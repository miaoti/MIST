package io.mist.core.oracle.shape;

import io.mist.core.oracle.shape.invariant.ResponseEnvelopeInvariant;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ResponseEnvelopeInvariantTest {

    @Test
    public void emptyCorpusIsPermissive() {
        ResponseEnvelopeInvariant.Data data = ResponseEnvelopeInvariant.learn(Collections.emptyList());
        assertTrue(data.getSuccessSet().isEmpty());
        ResponseEnvelopeInvariant inv = new ResponseEnvelopeInvariant("POST /x", data);
        TraceModel trace = buildTrace("t",
                rootSpan("a", 200, "{\"status\":1,\"msg\":\"ok\"}"));
        assertTrue(inv.evaluate(trace).passed);
    }

    @Test
    public void singleTraceLearnsSuccessValue() {
        TraceModel seed = buildTrace("s", rootSpan("a", 200, "{\"status\":1}"));
        ResponseEnvelopeInvariant.Data data = ResponseEnvelopeInvariant.learn(Collections.singletonList(seed));
        assertTrue("success set should contain 1", data.getSuccessSet().contains("1"));
    }

    @Test
    public void conformantTracePasses() {
        TraceModel seed = buildTrace("s", rootSpan("a", 200, "{\"status\":1}"));
        ResponseEnvelopeInvariant inv = new ResponseEnvelopeInvariant("POST /x",
                ResponseEnvelopeInvariant.learn(Collections.singletonList(seed)));
        TraceModel candidate = buildTrace("c", rootSpan("b", 200, "{\"status\":1}"));
        assertTrue(inv.evaluate(candidate).passed);
    }

    @Test
    public void valueInFailureSetFails() {
        Set<String> success = new TreeSet<>(Collections.singletonList("1"));
        Set<String> failure = new TreeSet<>(Collections.singletonList("0"));
        ResponseEnvelopeInvariant.Data data = new ResponseEnvelopeInvariant.Data("status", success, failure);
        ResponseEnvelopeInvariant inv = new ResponseEnvelopeInvariant("POST /x", data);

        TraceModel bad = buildTrace("bad", rootSpan("a", 200, "{\"status\":0,\"msg\":\"oops\"}"));
        TraceShapeVerdict.InvariantOutcome out = inv.evaluate(bad);
        assertFalse("status=0 in failureSet should fail", out.passed);
        assertEquals(TraceShapeVerdict.Severity.ERROR, out.severity);
    }

    @Test
    public void unknownValueDefersAsInfo() {
        Set<String> success = new TreeSet<>(Collections.singletonList("1"));
        Set<String> failure = new TreeSet<>();
        ResponseEnvelopeInvariant.Data data = new ResponseEnvelopeInvariant.Data("status", success, failure);
        ResponseEnvelopeInvariant inv = new ResponseEnvelopeInvariant("POST /x", data);

        // status=42 is neither success nor failure; verdict should be a passing INFO (deferred to LLM).
        TraceModel candidate = buildTrace("c", rootSpan("a", 200, "{\"status\":42}"));
        TraceShapeVerdict.InvariantOutcome out = inv.evaluate(candidate);
        assertTrue("unknown value should not be an ERROR-level failure", out.passed);
        assertEquals(TraceShapeVerdict.Severity.INFO, out.severity);
        assertTrue(out.detail.contains("unknown") || out.detail.contains("LLM"));
    }

    @Test
    public void nonSuccessHttpStatusIsIgnored() {
        // Invariant scopes to 2xx responses only; a 500 with status=0 should not trigger evaluation.
        Set<String> success = new TreeSet<>(Collections.singletonList("1"));
        Set<String> failure = new TreeSet<>(Collections.singletonList("0"));
        ResponseEnvelopeInvariant.Data data = new ResponseEnvelopeInvariant.Data("status", success, failure);
        ResponseEnvelopeInvariant inv = new ResponseEnvelopeInvariant("POST /x", data);

        TraceModel candidate = buildTrace("c", rootSpan("a", 500, "{\"status\":0}"));
        assertTrue("non-2xx must not be evaluated by this invariant", inv.evaluate(candidate).passed);
    }

    // --- Phase 4.x: runtime classifier wiring (learn-only-from-observation) ---

    @Test
    public void classifierFlagsUnknownAsFailure_failsAndCaches() {
        // Empty learned data + a classifier that calls status=0 a failure.
        ResponseEnvelopeInvariant inv = new ResponseEnvelopeInvariant("POST /x", ResponseEnvelopeInvariant.Data.empty())
                .withClassifier((api, field, value, body) -> "0".equals(value) ? Boolean.TRUE : Boolean.FALSE, null);
        TraceModel bad = buildTrace("bad", rootSpan("a", 200, "{\"status\":0,\"msg\":\"not found\"}"));
        TraceShapeVerdict.InvariantOutcome out = inv.evaluate(bad);
        assertFalse("classifier-flagged soft error must fail", out.passed);
        assertEquals(TraceShapeVerdict.Severity.ERROR, out.severity);
        assertTrue("value cached into live failure set", inv.getLiveFailureSet().contains("0"));
    }

    @Test
    public void classifierFlagsUnknownAsSuccess_passesAndCaches() {
        ResponseEnvelopeInvariant inv = new ResponseEnvelopeInvariant("POST /x", ResponseEnvelopeInvariant.Data.empty())
                .withClassifier((api, field, value, body) -> Boolean.FALSE, null);
        TraceModel ok = buildTrace("ok", rootSpan("a", 200, "{\"status\":7}"));
        assertTrue("classifier-confirmed success passes", inv.evaluate(ok).passed);
        assertTrue("value cached into live success set", inv.getLiveSuccessSet().contains("7"));
    }

    @Test
    public void classifierCalledOncePerValue_thenCached() {
        int[] calls = {0};
        ResponseEnvelopeInvariant inv = new ResponseEnvelopeInvariant("POST /x", ResponseEnvelopeInvariant.Data.empty())
                .withClassifier((api, field, value, body) -> { calls[0]++; return Boolean.TRUE; }, null);
        TraceModel bad = buildTrace("bad", rootSpan("a", 200, "{\"status\":0}"));
        inv.evaluate(bad);
        inv.evaluate(bad); // same value again resolves from the cached failure set
        assertEquals("second sighting must not re-invoke the classifier", 1, calls[0]);
    }

    @Test
    public void classifierReturningNullStaysPermissive() {
        ResponseEnvelopeInvariant inv = new ResponseEnvelopeInvariant("POST /x", ResponseEnvelopeInvariant.Data.empty())
                .withClassifier((api, field, value, body) -> null, null);
        TraceModel candidate = buildTrace("c", rootSpan("a", 200, "{\"status\":9}"));
        TraceShapeVerdict.InvariantOutcome out = inv.evaluate(candidate);
        assertTrue("unclassifiable value must not fail", out.passed);
        assertFalse("nothing cached when classifier abstains", inv.getLiveFailureSet().contains("9"));
    }

    private TraceModel buildTrace(String tid, TraceModel.Span span) {
        return new TraceModel(tid, Collections.singletonList(span));
    }

    private TraceModel.Span rootSpan(String id, int httpStatus, String responseBody) {
        Map<String, String> tags = new HashMap<>();
        tags.put("http.response.body", responseBody);
        return new TraceModel.Span(id, null, "svc", "op", httpStatus, "OK", 1000L, tags);
    }
}
