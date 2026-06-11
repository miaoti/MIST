package io.mist.core.oracle.shape.invariant;

import io.mist.core.oracle.shape.TraceModel;
import io.mist.core.oracle.shape.TraceShapeVerdict;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Pins HiddenDownstreamFailureInvariant: fires when the client-facing entry
 * returned 2xx but a non-entry span server-errored (swallowed). ERROR when the
 * swallowed span is an HTTP >=500 (synchronous call masked); WARN when it is
 * otel=ERROR only (softer signal). Silent on healthy traces, loud failures, and
 * benign downstream 4xx. Also pins the partial-view (co-root) fix.
 */
public class HiddenDownstreamFailureInvariantTest {

    // rootApiKey "POST /api/v1/x" does NOT match the test spans' "POST /x"
    // operation, so these cases exercise the all-roots fallback path.
    private final HiddenDownstreamFailureInvariant inv =
            new HiddenDownstreamFailureInvariant("POST /api/v1/x");

    // rootApiKey that DOES match the entry span operation, exercising the
    // rootApiKey-keyed entry path (the C7a partial-view fix).
    private final HiddenDownstreamFailureInvariant invMatch =
            new HiddenDownstreamFailureInvariant("POST /x");

    private static TraceModel.Span span(String id, String parent, String svc, String op, int http, String otel) {
        return new TraceModel.Span(id, parent, svc, op, http, otel, 0L, new HashMap<>());
    }

    @Test
    public void fires_whenRoot2xxButDescendant500() {
        TraceModel t = new TraceModel("t", Arrays.asList(
                span("root", null, "ts-gateway", "POST /x", 200, null),
                span("child", "root", "ts-order", "OrderController.create", 500, "ERROR")));
        TraceShapeVerdict.InvariantOutcome o = inv.evaluate(t);
        assertFalse("hidden failure must fire", o.passed);
        assertEquals("HIDDEN_DOWNSTREAM_FAILURE", o.kind);
        assertEquals("http>=500 swallowed → ERROR", TraceShapeVerdict.Severity.ERROR, o.severity);
    }

    @Test
    public void fires_whenRoot2xxButDescendantOtelErrorNoHttp() {
        // The real train-ticket pattern: leaf error span has otel=ERROR, http=0.
        TraceModel t = new TraceModel("t", Arrays.asList(
                span("root", null, "ts-gateway", "POST /x", 200, null),
                span("child", "root", "ts-route", "RouteController.createAndModifyRoute", 0, "ERROR")));
        assertFalse(inv.evaluate(t).passed);
    }

    @Test
    public void passes_whenAllHealthy2xx() {
        TraceModel t = new TraceModel("t", Arrays.asList(
                span("root", null, "ts-gateway", "POST /x", 200, null),
                span("child", "root", "ts-order", "OrderController.create", 200, null)));
        assertTrue(inv.evaluate(t).passed);
    }

    @Test
    public void passes_whenRootItselfErrored_loudNotHidden() {
        TraceModel t = new TraceModel("t", Arrays.asList(
                span("root", null, "ts-gateway", "POST /x", 500, "ERROR"),
                span("child", "root", "ts-order", "OrderController.create", 500, "ERROR")));
        assertTrue("root surfaced the error → loud, not hidden", inv.evaluate(t).passed);
    }

    @Test
    public void passes_whenDescendant4xx_belowServerErrorThreshold() {
        // reviewer B3: downstream 4xx is benign control-flow, must NOT fire.
        TraceModel t = new TraceModel("t", Arrays.asList(
                span("root", null, "ts-gateway", "POST /x", 200, null),
                span("child", "root", "ts-order", "OrderController.lookup", 404, null)));
        assertTrue(inv.evaluate(t).passed);
    }

    @Test
    public void passes_whenEmptyOrNull() {
        assertTrue(inv.evaluate(null).passed);
        assertTrue(inv.evaluate(new TraceModel("t", Collections.emptyList())).passed);
    }

    // ---- C7a: partial-view fix — a downstream 5xx appearing as a co-root (its
    // parent absent from the fetched trace) must NOT suppress the finding. The
    // pre-fix code passed() as soon as any parentless span was a server error.
    @Test
    public void fires_whenEntryMatchesAndCoRootDownstream500() {
        TraceModel t = new TraceModel("t", Arrays.asList(
                span("entry", null, "ts-gateway", "POST /x", 200, null),           // matched entry, clean 2xx
                span("orphan", null, "ts-route", "RouteController.create", 500, "ERROR"))); // co-root downstream 5xx
        TraceShapeVerdict.InvariantOutcome o = invMatch.evaluate(t);
        assertFalse("co-root downstream 500 must be counted, not suppressed", o.passed);
        assertEquals(TraceShapeVerdict.Severity.ERROR, o.severity);
        assertTrue("the orphan span is the evidence", o.evidenceSpanIds.contains("orphan"));
    }

    // ---- C7b: confidence guard.
    @Test
    public void warns_whenSwallowedErrorIsOtelOnly() {
        TraceModel t = new TraceModel("t", Arrays.asList(
                span("entry", null, "ts-gateway", "POST /x", 200, null),
                span("c", "entry", "ts-route", "RouteController.create", 0, "ERROR"))); // otel-only, no http 5xx
        TraceShapeVerdict.InvariantOutcome o = invMatch.evaluate(t);
        assertFalse("still a finding", o.passed);
        assertEquals("otel-only → softer WARN, non-blocking", TraceShapeVerdict.Severity.WARN, o.severity);
    }

    @Test
    public void errors_whenSwallowedErrorIsHttp5xx() {
        TraceModel t = new TraceModel("t", Arrays.asList(
                span("entry", null, "ts-gateway", "POST /x", 200, null),
                span("c", "entry", "ts-route", "RouteController.create", 503, null)));
        assertEquals(TraceShapeVerdict.Severity.ERROR, invMatch.evaluate(t).severity);
    }
}
