package io.mist.core.oracle.shape;

import io.mist.core.oracle.shape.invariant.StatusPropagationInvariant;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StatusPropagationInvariantTest {

    @Test
    public void emptyCorpusIsPermissive() {
        StatusPropagationInvariant.Data data = StatusPropagationInvariant.learn(Collections.emptyList());
        assertTrue(data.getHttpStatusByDepth().isEmpty());
        assertTrue(data.getOtelStatusByDepth().isEmpty());

        StatusPropagationInvariant inv = new StatusPropagationInvariant("GET /x", data);
        TraceModel trace = buildTrace("t", Arrays.asList(spanHttp("a", null, "svc", 500)));
        assertTrue("empty invariant passes any trace", inv.evaluate(trace).passed);
    }

    @Test
    public void singleTraceLearnsItsStatuses() {
        TraceModel seed = buildTrace("s", Arrays.asList(
                spanHttp("a", null, "gateway", 200),
                spanHttp("b", "a", "orders", 200)));
        StatusPropagationInvariant.Data data = StatusPropagationInvariant.learn(Collections.singletonList(seed));
        assertTrue(data.getHttpStatusByDepth().get(0).contains(200));
        assertTrue(data.getHttpStatusByDepth().get(1).contains(200));
    }

    @Test
    public void conformantTracePasses() {
        TraceModel seed = buildTrace("s", Arrays.asList(
                spanHttp("a", null, "g", 200),
                spanHttp("b", "a", "o", 200)));
        StatusPropagationInvariant inv = new StatusPropagationInvariant("GET /x",
                StatusPropagationInvariant.learn(Collections.singletonList(seed)));

        TraceModel candidate = buildTrace("c", Arrays.asList(
                spanHttp("a", null, "g", 200),
                spanHttp("b", "a", "o", 200)));
        assertTrue(inv.evaluate(candidate).passed);
    }

    @Test
    public void nonConformantStatusFailsWithEvidence() {
        TraceModel seed = buildTrace("s", Arrays.asList(
                spanHttp("a", null, "g", 200),
                spanHttp("b", "a", "o", 200)));
        StatusPropagationInvariant inv = new StatusPropagationInvariant("GET /x",
                StatusPropagationInvariant.learn(Collections.singletonList(seed)));

        TraceModel bad = buildTrace("c", Arrays.asList(
                spanHttp("a", null, "g", 200),
                spanHttp("b", "a", "o", 500)));
        TraceShapeVerdict.InvariantOutcome out = inv.evaluate(bad);
        assertFalse(out.passed);
        assertTrue("evidence should name span b", out.evidenceSpanIds.contains("b"));
        assertTrue("detail mentions 500", out.detail.contains("500"));
    }

    @Test
    public void otelStatusViolationFlagged() {
        Map<String, String> seedTags = new HashMap<>();
        TraceModel seed = buildTrace("s", Collections.singletonList(
                new TraceModel.Span("a", null, "svc", "op", 200, "OK", 1000L, seedTags)));
        StatusPropagationInvariant inv = new StatusPropagationInvariant("GET /x",
                StatusPropagationInvariant.learn(Collections.singletonList(seed)));

        TraceModel bad = buildTrace("c", Collections.singletonList(
                new TraceModel.Span("a", null, "svc", "op", 200, "ERROR", 1000L, new HashMap<>())));
        TraceShapeVerdict.InvariantOutcome out = inv.evaluate(bad);
        assertFalse(out.passed);
        assertTrue(out.detail.toLowerCase().contains("error"));
    }

    @Test
    public void unknownDepthIsTreatedAsPermissive() {
        TraceModel seed = buildTrace("s", Collections.singletonList(spanHttp("a", null, "g", 200)));
        StatusPropagationInvariant inv = new StatusPropagationInvariant("GET /x",
                StatusPropagationInvariant.learn(Collections.singletonList(seed)));

        // The candidate has more depth than the seed; deeper levels were never trained, so they pass.
        TraceModel candidate = buildTrace("c", Arrays.asList(
                spanHttp("a", null, "g", 200),
                spanHttp("b", "a", "o", 418)));
        assertTrue("unknown depth should not fail", inv.evaluate(candidate).passed);
    }

    private TraceModel buildTrace(String tid, List<TraceModel.Span> spans) {
        return new TraceModel(tid, spans);
    }

    private TraceModel.Span spanHttp(String id, String parent, String service, int httpStatus) {
        return new TraceModel.Span(id, parent, service, "op", httpStatus, null, 1000L, new HashMap<>());
    }
}
