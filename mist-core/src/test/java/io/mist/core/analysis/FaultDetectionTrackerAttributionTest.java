package io.mist.core.analysis;

import io.mist.core.oracle.attribution.AttributionVerdict;
import io.mist.core.oracle.shape.TraceModel;
import io.mist.core.oracle.shape.TraceShapeVerdict;
import io.mist.core.oracle.shape.invariant.TargetAttributionInvariant;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Phase 2 part 3 + FIXES.md F3 cleanup contract: the attribution
 * histogram on {@link FaultDetectionTracker.OracleAnomaly} is populated
 * ONLY when the verdict carries a TARGET_ATTRIBUTION outcome (set by
 * {@link TargetAttributionInvariant} inside TraceShapeOracle). There is
 * no inline fallback; the per-invariant flag is the single kill switch.
 */
public class FaultDetectionTrackerAttributionTest {

    private static final String FAKE_FAULTS_JSON =
            "{\"injected_faults\":[{\"faultName\":\"F1\",\"service\":\"svc\",\"api\":\"GET /x\"}]}";

    private Path tempFaultsFile;
    private String prevReportFlag;

    @Before
    public void setUp() throws IOException {
        tempFaultsFile = Files.createTempFile("injected-faults-attr-", ".json");
        Files.write(tempFaultsFile, FAKE_FAULTS_JSON.getBytes());
        FaultDetectionTracker.getInstance().reset();
        FaultDetectionTracker.getInstance().loadInjectedFaults(tempFaultsFile.toString());
        prevReportFlag = System.getProperty("mist.report.oracle.anomalies.enabled");
        System.clearProperty("mist.report.oracle.anomalies.enabled");
    }

    @After
    public void tearDown() throws IOException {
        Files.deleteIfExists(tempFaultsFile);
        FaultDetectionTracker.getInstance().reset();
        if (prevReportFlag == null) {
            System.clearProperty("mist.report.oracle.anomalies.enabled");
        } else {
            System.setProperty("mist.report.oracle.anomalies.enabled", prevReportFlag);
        }
    }

    @Test
    public void recordVerdict_noAttributionOutcome_emptyHistogram() {
        TraceShapeVerdict v = oneFailingVerdict();
        FaultDetectionTracker t = FaultDetectionTracker.getInstance();
        t.recordVerdict(v, "POST /x", "C", "m", "trace-1");
        assertEquals(1, t.getOracleAnomaliesForTest().size());
        FaultDetectionTracker.OracleAnomaly a = t.getOracleAnomaliesForTest().values().iterator().next();
        assertTrue("no TARGET_ATTRIBUTION outcome → no histogram entry",
                a.attributionCounts.isEmpty());
    }

    @Test
    public void recordVerdict_targetRejectionOutcome_incrementsBucket() {
        TraceShapeVerdict v = verdictWithAttribution(
                traceWithLeafError("ts-order-service", "validateSeatNumber"),
                "ts-order-service", "seatNumber");
        FaultDetectionTracker.getInstance().recordVerdict(v, "POST /x", "C", "m", "trace-1");
        FaultDetectionTracker.OracleAnomaly a = FaultDetectionTracker.getInstance()
                .getOracleAnomaliesForTest().values().iterator().next();
        assertEquals(Integer.valueOf(1),
                a.attributionCounts.get(AttributionVerdict.TARGET_REJECTION));
    }

    @Test
    public void recordVerdict_upstreamRejectionOutcome_incrementsBucket() {
        TraceShapeVerdict v = verdictWithAttribution(
                traceWithLeafError("ts-payment-service", "charge"),
                "ts-order-service", "seatNumber");
        FaultDetectionTracker.getInstance().recordVerdict(v, "POST /x", "C", "m", "trace-1");
        FaultDetectionTracker.OracleAnomaly a = FaultDetectionTracker.getInstance()
                .getOracleAnomaliesForTest().values().iterator().next();
        assertEquals(Integer.valueOf(1),
                a.attributionCounts.get(AttributionVerdict.UPSTREAM_REJECTION));
    }

    @Test
    public void recordVerdict_wrongParamOutcome_incrementsBucket() {
        TraceShapeVerdict v = verdictWithAttribution(
                traceWithLeafError("ts-order-service", "validateContactsName"),
                "ts-order-service", "seatNumber");
        FaultDetectionTracker.getInstance().recordVerdict(v, "POST /x", "C", "m", "trace-1");
        FaultDetectionTracker.OracleAnomaly a = FaultDetectionTracker.getInstance()
                .getOracleAnomaliesForTest().values().iterator().next();
        assertEquals(Integer.valueOf(1),
                a.attributionCounts.get(AttributionVerdict.WRONG_PARAM_REJECTION));
    }

    @Test
    public void multipleVerdicts_accumulateInBuckets() {
        FaultDetectionTracker t = FaultDetectionTracker.getInstance();
        TraceModel target = traceWithLeafError("ts-order-service", "validateSeatNumber");
        TraceModel upstream = traceWithLeafError("ts-payment-service", "charge");
        for (int i = 0; i < 3; i++) {
            t.recordVerdict(
                    verdictWithAttribution(target, "ts-order-service", "seatNumber"),
                    "POST /x", "C", "m" + i, "tr" + i);
        }
        for (int i = 0; i < 5; i++) {
            t.recordVerdict(
                    verdictWithAttribution(upstream, "ts-order-service", "seatNumber"),
                    "POST /x", "C", "u" + i, "tu" + i);
        }
        FaultDetectionTracker.OracleAnomaly a = t.getOracleAnomaliesForTest().values().iterator().next();
        assertEquals(8, a.hitCount);
        assertEquals(Integer.valueOf(3), a.attributionCounts.get(AttributionVerdict.TARGET_REJECTION));
        assertEquals(Integer.valueOf(5), a.attributionCounts.get(AttributionVerdict.UPSTREAM_REJECTION));
    }

    @Test
    public void report_emitsAttributionLine_perAnomaly() throws IOException {
        FaultDetectionTracker t = FaultDetectionTracker.getInstance();
        TraceModel target = traceWithLeafError("ts-order-service", "validateSeatNumber");
        t.recordVerdict(
                verdictWithAttribution(target, "ts-order-service", "seatNumber"),
                "POST /x", "C", "m", "tr");

        Path reportDir = Files.createTempDirectory("fdt-attr-");
        try {
            t.generateReport(reportDir.toString(), "phase2-attr");
            File[] files = reportDir.toFile().listFiles();
            assertNotNull(files);
            assertTrue(files.length > 0);
            String text = new String(Files.readAllBytes(files[0].toPath()));
            assertTrue("anomaly section present", text.contains("ORACLE ANOMALIES"));
            assertTrue("attribution line on entry", text.contains("Attribution:"));
            assertTrue("rollup label present", text.contains("Attribution roll-up"));
            assertTrue("target verdict surfaced", text.contains("TARGET_REJECTION"));
        } finally {
            deleteRecursive(reportDir.toFile());
        }
    }

    @Test
    public void noAttributionOutcomeForUnknownTrace_emptyHistogram() {
        // Verdict has only a STATUS_PROPAGATION failure; no TARGET_ATTRIBUTION
        // outcome was appended (the kill switch was off, or the target was
        // unknown). Histogram stays empty regardless of whether the
        // FaultDetectionTracker would otherwise compute attribution.
        TraceShapeVerdict v = oneFailingVerdict();
        FaultDetectionTracker.getInstance().recordVerdict(v, "POST /x", "C", "m", "tr");
        FaultDetectionTracker.OracleAnomaly a = FaultDetectionTracker.getInstance()
                .getOracleAnomaliesForTest().values().iterator().next();
        assertTrue("no attribution outcome → no histogram", a.attributionCounts.isEmpty());
    }

    @Test
    public void noAttributionVerdict_outcomePresentButValueNo_ATTRIBUTION() {
        // Verdict carries a TARGET_ATTRIBUTION outcome whose detail is
        // NO_ATTRIBUTION. The histogram should still bump that bucket for
        // each anomaly, so the report shows the algorithm WAS run but
        // couldn't reach a conclusion.
        TraceShapeVerdict v = verdictWithAttribution(
                traceNoError() /* no leaf → NO_ATTRIBUTION */,
                "ts-x", "p");
        FaultDetectionTracker.getInstance().recordVerdict(v, "POST /x", "C", "m", "tr");
        FaultDetectionTracker.OracleAnomaly a = FaultDetectionTracker.getInstance()
                .getOracleAnomaliesForTest().values().iterator().next();
        assertEquals(Integer.valueOf(1),
                a.attributionCounts.get(AttributionVerdict.NO_ATTRIBUTION));
    }

    @Test
    public void resetClearsAttribution() {
        FaultDetectionTracker t = FaultDetectionTracker.getInstance();
        TraceModel target = traceWithLeafError("ts-order-service", "validateSeatNumber");
        t.recordVerdict(
                verdictWithAttribution(target, "ts-order-service", "seatNumber"),
                "POST /x", "C", "m", "tr");
        assertEquals(1, t.getOracleAnomaliesForTest().size());
        t.reset();
        assertEquals(0, t.getOracleAnomaliesForTest().size());
    }

    // ---------------------------------------------------------------------

    /**
     * Build a verdict with one STATUS_PROPAGATION failure AND a
     * TARGET_ATTRIBUTION outcome computed by the real invariant. Mirrors
     * the production path (TraceShapeOracle.evaluate(4-arg) builds the
     * same outcome chain) so attribute computation goes through the same
     * code in tests as in prod.
     */
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

    private static TraceShapeVerdict oneFailingVerdict() {
        return TraceShapeVerdict.builder()
                .add(TraceShapeVerdict.InvariantOutcome.fail(
                        "STATUS_PROPAGATION", "POST /x", TraceShapeVerdict.Severity.ERROR,
                        "depth=0 http=500 expected=[200, 201]", Collections.singletonList("s1")))
                .build();
    }

    private static TraceModel traceWithLeafError(String leafService, String leafOp) {
        Map<String, String> tags = new HashMap<>();
        tags.put("http.status_code", "500");
        tags.put("otel.status_code", "ERROR");
        return new TraceModel("test-trace", Arrays.asList(
                new TraceModel.Span("root", null, "ts-gateway", "POST /x", 500, "ERROR", 0L, tags),
                new TraceModel.Span("leaf", "root", leafService, leafOp, 500, "ERROR", 0L, tags)));
    }

    private static TraceModel traceNoError() {
        Map<String, String> tags = new HashMap<>();
        tags.put("http.status_code", "200");
        tags.put("otel.status_code", "OK");
        return new TraceModel("test-trace", Collections.singletonList(
                new TraceModel.Span("root", null, "ts-gateway", "POST /x", 200, "OK", 0L, tags)));
    }

    private static void deleteRecursive(File f) {
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteRecursive(k);
        }
        f.delete();
    }
}
