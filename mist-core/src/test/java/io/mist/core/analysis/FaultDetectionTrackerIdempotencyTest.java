package io.mist.core.analysis;

import io.mist.core.oracle.attribution.AttributionVerdict;
import io.mist.core.oracle.shape.TraceModel;
import io.mist.core.oracle.shape.TraceShapeVerdict;
import io.mist.core.oracle.shape.invariant.TargetAttributionInvariant;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Per-execution idempotency contract: the writer's generated code calls
 * attachJaegerTrace twice for the same step when a positive variant throws on
 * an ERROR-severity verdict (success path records the verdict, the
 * AssertionError is caught, the catch path records the SAME verdict again with
 * the SAME marker traceId). hitCount and the attribution histogram must count
 * that step execution once. Re-executions (enhancer rounds) carry a fresh
 * marker UUID and must keep counting; callers without a traceId keep the
 * legacy count-every-call behaviour.
 */
public class FaultDetectionTrackerIdempotencyTest {

    @Before
    public void setUp() {
        FaultDetectionTracker.getInstance().reset();
        FaultDetectionTracker.getInstance().initializeWithNoFaults();
    }

    @After
    public void tearDown() {
        FaultDetectionTracker.getInstance().reset();
    }

    @Test
    public void sameExecutionRecordedTwice_countsOnce() {
        FaultDetectionTracker t = FaultDetectionTracker.getInstance();
        TraceShapeVerdict v = oneFailingVerdict();
        t.recordVerdict(v, "POST /x", "C", "m", "marker-1");
        t.recordVerdict(v, "POST /x", "C", "m", "marker-1");  // catch-path repeat

        assertEquals(1, t.getOracleAnomaliesForTest().size());
        FaultDetectionTracker.OracleAnomaly a =
                t.getOracleAnomaliesForTest().values().iterator().next();
        assertEquals("intra-execution double record must collapse", 1, a.hitCount);
    }

    @Test
    public void sameExecutionRecordedTwice_attributionCountsOnce() {
        FaultDetectionTracker t = FaultDetectionTracker.getInstance();
        TraceShapeVerdict v = verdictWithAttribution(
                traceWithLeafError("ts-order-service", "validateSeatNumber"),
                "ts-order-service", "seatNumber");
        t.recordVerdict(v, "POST /x", "C", "m", "marker-1");
        t.recordVerdict(v, "POST /x", "C", "m", "marker-1");

        FaultDetectionTracker.OracleAnomaly a =
                t.getOracleAnomaliesForTest().values().iterator().next();
        assertEquals(Integer.valueOf(1),
                a.attributionCounts.get(AttributionVerdict.TARGET_REJECTION));
    }

    @Test
    public void reExecutionWithFreshMarker_stillCounts() {
        FaultDetectionTracker t = FaultDetectionTracker.getInstance();
        TraceShapeVerdict v = oneFailingVerdict();
        t.recordVerdict(v, "POST /x", "C", "m", "marker-round0");
        t.recordVerdict(v, "POST /x", "C", "m", "marker-round1");

        FaultDetectionTracker.OracleAnomaly a =
                t.getOracleAnomaliesForTest().values().iterator().next();
        assertEquals("distinct executions (fresh markers) must both count", 2, a.hitCount);
    }

    @Test
    public void missingTraceId_keepsLegacyCountEveryCall() {
        FaultDetectionTracker t = FaultDetectionTracker.getInstance();
        assertTrue(t.recordOracleAnomaly("SPAN_TREE", "POST /x", "fp_1", "d", "ERROR",
                "C", "m", null));
        assertTrue(t.recordOracleAnomaly("SPAN_TREE", "POST /x", "fp_1", "d", "ERROR",
                "C", "m", null));
        FaultDetectionTracker.OracleAnomaly a =
                t.getOracleAnomaliesForTest().values().iterator().next();
        assertEquals(2, a.hitCount);
    }

    @Test
    public void recordOracleAnomaly_returnValueReflectsDedup() {
        FaultDetectionTracker t = FaultDetectionTracker.getInstance();
        assertTrue(t.recordOracleAnomaly("SPAN_TREE", "POST /x", "fp_1", "d", "ERROR",
                "C", "m", "marker-1"));
        assertFalse("repeat of the same (test, traceId, anomaly) must report deduped",
                t.recordOracleAnomaly("SPAN_TREE", "POST /x", "fp_1", "d", "ERROR",
                        "C", "m", "marker-1"));
    }

    @Test
    public void reset_clearsInstanceDedupState() {
        FaultDetectionTracker t = FaultDetectionTracker.getInstance();
        t.recordVerdict(oneFailingVerdict(), "POST /x", "C", "m", "marker-1");
        t.reset();
        t.recordVerdict(oneFailingVerdict(), "POST /x", "C", "m", "marker-1");
        FaultDetectionTracker.OracleAnomaly a =
                t.getOracleAnomaliesForTest().values().iterator().next();
        assertEquals("post-reset recording must start fresh", 1, a.hitCount);
    }

    // ── helpers (mirror FaultDetectionTrackerAttributionTest) ─────────────

    private static TraceShapeVerdict oneFailingVerdict() {
        return TraceShapeVerdict.builder()
                .add(TraceShapeVerdict.InvariantOutcome.fail(
                        "STATUS_PROPAGATION", "POST /x", TraceShapeVerdict.Severity.ERROR,
                        "depth=0 http=500 expected=[200, 201]", Collections.singletonList("s1")))
                .build();
    }

    private static TraceShapeVerdict verdictWithAttribution(TraceModel trace,
                                                            String targetService,
                                                            String targetParam) {
        TraceShapeVerdict.Builder b = TraceShapeVerdict.builder();
        for (TraceShapeVerdict.InvariantOutcome o : oneFailingVerdict().getOutcomes()) {
            b.add(o);
        }
        b.add(new TargetAttributionInvariant("POST /x", targetService, targetParam)
                .evaluate(trace));
        return b.build();
    }

    private static TraceModel traceWithLeafError(String leafService, String leafOp) {
        Map<String, String> tags = new HashMap<>();
        tags.put("http.status_code", "500");
        tags.put("otel.status_code", "ERROR");
        return new TraceModel("test-trace", Arrays.asList(
                new TraceModel.Span("root", null, "ts-gateway", "POST /x", 500, "ERROR", 0L, tags),
                new TraceModel.Span("leaf", "root", leafService, leafOp, 500, "ERROR", 0L, tags)));
    }
}
