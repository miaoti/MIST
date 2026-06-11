package io.mist.core.oracle.shape;

import io.mist.core.oracle.shape.invariant.TimingEnvelopeInvariant;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TimingEnvelopeInvariantTest {

    @Test
    public void emptyCorpusIsPermissive() {
        TimingEnvelopeInvariant.Data data = TimingEnvelopeInvariant.learn(Collections.emptyList());
        assertEquals(0L, data.totalP99);
        TimingEnvelopeInvariant inv = new TimingEnvelopeInvariant("GET /x", data);
        TraceModel trace = buildTrace("t", Collections.singletonList(span("a", null, 5_000_000L)));
        assertTrue(inv.evaluate(trace).passed);
    }

    @Test
    public void singleTraceLearnsPercentiles() {
        TraceModel seed = buildTrace("s", Arrays.asList(
                span("a", null, 1000L),
                span("b", "a", 500L)));
        TimingEnvelopeInvariant.Data data = TimingEnvelopeInvariant.learn(Collections.singletonList(seed));
        // root duration is 1000; only root is a "total"; percentile of single-element list is that value
        assertEquals(1000L, data.totalP99);
        assertEquals(1000L, data.spanP99);
    }

    @Test
    public void conformantTracePasses() {
        List<TraceModel> seeds = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            seeds.add(buildTrace("s" + i, Arrays.asList(
                    span("a", null, 1000L + i * 10L),
                    span("b", "a", 500L))));
        }
        TimingEnvelopeInvariant inv = new TimingEnvelopeInvariant("GET /x", TimingEnvelopeInvariant.learn(seeds));
        TraceModel candidate = buildTrace("c", Arrays.asList(
                span("a", null, 1050L),
                span("b", "a", 500L)));
        assertTrue(inv.evaluate(candidate).passed);
    }

    @Test
    public void slowTraceFailsTotalEnvelope() {
        List<TraceModel> seeds = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            seeds.add(buildTrace("s" + i, Collections.singletonList(span("a", null, 1000L))));
        }
        TimingEnvelopeInvariant inv = new TimingEnvelopeInvariant("GET /x", TimingEnvelopeInvariant.learn(seeds));
        TraceModel bad = buildTrace("bad", Collections.singletonList(span("a", null, 1_000_000L)));
        TraceShapeVerdict.InvariantOutcome out = inv.evaluate(bad);
        assertFalse(out.passed);
        assertEquals("WARN severity by default", TraceShapeVerdict.Severity.WARN, out.severity);
    }

    @Test
    public void spanOutlierFlaggedWithEvidence() {
        // Seeds: all spans 1000µs.  Per-span p99 = 1000.  Span outlier threshold = 2000.
        List<TraceModel> seeds = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            seeds.add(buildTrace("s" + i, Arrays.asList(
                    span("a", null, 1000L),
                    span("b", "a", 1000L))));
        }
        TimingEnvelopeInvariant inv = new TimingEnvelopeInvariant("GET /x", TimingEnvelopeInvariant.learn(seeds));
        TraceModel bad = buildTrace("bad", Arrays.asList(
                span("a", null, 1500L),
                span("b", "a", 5000L)));
        TraceShapeVerdict.InvariantOutcome out = inv.evaluate(bad);
        assertFalse(out.passed);
        assertTrue("evidence should name span b", out.evidenceSpanIds.contains("b"));
    }

    @Test
    public void boundaryDurationEqualToP99Passes() {
        // total duration = p99 should NOT fail (strict greater-than).
        List<TraceModel> seeds = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            seeds.add(buildTrace("s" + i, Collections.singletonList(span("a", null, 2000L))));
        }
        TimingEnvelopeInvariant inv = new TimingEnvelopeInvariant("GET /x", TimingEnvelopeInvariant.learn(seeds));
        TraceModel candidate = buildTrace("c", Collections.singletonList(span("a", null, 2000L)));
        TraceShapeVerdict.InvariantOutcome out = inv.evaluate(candidate);
        assertTrue("duration exactly at p99 should pass", out.passed);
    }

    private TraceModel buildTrace(String tid, List<TraceModel.Span> spans) {
        return new TraceModel(tid, spans);
    }

    private TraceModel.Span span(String id, String parent, long durationMicros) {
        return new TraceModel.Span(id, parent, "svc", "op", 200, "OK", durationMicros, new HashMap<>());
    }
}
