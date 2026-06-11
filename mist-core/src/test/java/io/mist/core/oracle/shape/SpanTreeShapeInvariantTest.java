package io.mist.core.oracle.shape;

import io.mist.core.oracle.shape.invariant.SpanTreeShapeInvariant;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SpanTreeShapeInvariantTest {

    @Test
    public void emptyCorpusProducesPermissiveInvariant() {
        SpanTreeShapeInvariant.Data data = SpanTreeShapeInvariant.learn(Collections.emptyList());
        assertTrue("observed edges should be empty", data.getObservedEdges().isEmpty());
        assertTrue("required edges should be empty", data.getRequiredEdges().isEmpty());

        SpanTreeShapeInvariant inv = new SpanTreeShapeInvariant("GET /a", data);
        TraceModel trace = buildTrace("t1", Arrays.asList(
                span("r", null, "gateway"),
                span("c", "r", "orders")));
        TraceShapeVerdict.InvariantOutcome out = inv.evaluate(trace);
        assertTrue("permissive empty invariant should pass any trace", out.passed);
    }

    @Test
    public void singleTraceCorpusLearnsAllItsEdges() {
        TraceModel seed = buildTrace("seed", Arrays.asList(
                span("a", null, "gateway"),
                span("b", "a", "orders"),
                span("c", "b", "db")));
        SpanTreeShapeInvariant.Data data = SpanTreeShapeInvariant.learn(Collections.singletonList(seed));
        assertTrue(data.getObservedEdges().contains("gateway->orders"));
        assertTrue(data.getObservedEdges().contains("orders->db"));
        // with one trace at K=0.8, every observed edge crosses the required threshold (ceil(0.8*1)=1)
        assertTrue(data.getRequiredEdges().contains("gateway->orders"));
        assertTrue(data.getRequiredEdges().contains("orders->db"));
    }

    @Test
    public void conformantTracePasses() {
        List<TraceModel> seeds = Arrays.asList(
                buildTrace("s1", Arrays.asList(span("a", null, "gateway"), span("b", "a", "orders"))),
                buildTrace("s2", Arrays.asList(span("a", null, "gateway"), span("b", "a", "orders"))));
        SpanTreeShapeInvariant inv = new SpanTreeShapeInvariant("GET /o", SpanTreeShapeInvariant.learn(seeds));

        TraceModel candidate = buildTrace("c", Arrays.asList(
                span("a", null, "gateway"), span("b", "a", "orders")));
        TraceShapeVerdict.InvariantOutcome out = inv.evaluate(candidate);
        assertTrue("conformant trace should pass: " + out.detail, out.passed);
    }

    @Test
    public void unexpectedEdgeFailsWithEvidence() {
        TraceModel seed = buildTrace("s1", Arrays.asList(
                span("a", null, "gateway"), span("b", "a", "orders")));
        SpanTreeShapeInvariant inv = new SpanTreeShapeInvariant("GET /o", SpanTreeShapeInvariant.learn(Collections.singletonList(seed)));

        TraceModel bad = buildTrace("bad", Arrays.asList(
                span("a", null, "gateway"),
                span("b", "a", "orders"),
                // gateway->payments is novel; should be flagged.
                span("c", "a", "payments")));
        TraceShapeVerdict.InvariantOutcome out = inv.evaluate(bad);
        assertFalse("unexpected edge should fail", out.passed);
        assertTrue("detail should mention unexpected", out.detail.toLowerCase().contains("unexpected"));
        assertTrue("detail should mention payments edge", out.detail.contains("gateway->payments"));
    }

    @Test
    public void missingRequiredEdgeFails() {
        TraceModel seed = buildTrace("s1", Arrays.asList(
                span("a", null, "gateway"), span("b", "a", "orders")));
        SpanTreeShapeInvariant inv = new SpanTreeShapeInvariant("GET /o", SpanTreeShapeInvariant.learn(Collections.singletonList(seed)));

        TraceModel bad = buildTrace("bad", Collections.singletonList(span("a", null, "gateway")));
        TraceShapeVerdict.InvariantOutcome out = inv.evaluate(bad);
        assertFalse(out.passed);
        assertTrue("missing edges flagged", out.detail.contains("missing"));
    }

    @Test
    public void thresholdBoundaryControlsRequiredEdges() {
        // 5 seed traces; only 4 carry the orders->db edge => 4/5 = 0.80 exactly.
        // At K=0.80 ceil(0.80*5)=4 so the edge crosses; at K=0.81 ceil(4.05)=5 so it doesn't.
        List<TraceModel> seeds = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            seeds.add(buildTrace("t" + i, Arrays.asList(
                    span("a", null, "gateway"),
                    span("b", "a", "orders"),
                    span("c", "b", "db"))));
        }
        seeds.add(buildTrace("t4", Arrays.asList(
                span("a", null, "gateway"),
                span("b", "a", "orders"))));

        SpanTreeShapeInvariant.Data atThreshold = SpanTreeShapeInvariant.learn(seeds, 0.80);
        assertTrue("orders->db should be required at threshold 0.80", atThreshold.getRequiredEdges().contains("orders->db"));

        SpanTreeShapeInvariant.Data justAbove = SpanTreeShapeInvariant.learn(seeds, 0.81);
        assertFalse("orders->db should NOT be required at threshold 0.81", justAbove.getRequiredEdges().contains("orders->db"));
    }

    private TraceModel buildTrace(String traceId, List<TraceModel.Span> spans) {
        return new TraceModel(traceId, spans);
    }

    private TraceModel.Span span(String spanId, String parent, String service) {
        return new TraceModel.Span(spanId, parent, service, "op", 200, "OK", 1000L, new HashMap<>());
    }
}
