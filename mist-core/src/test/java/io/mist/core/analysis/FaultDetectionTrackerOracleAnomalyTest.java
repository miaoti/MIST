package io.mist.core.analysis;

import io.mist.core.oracle.shape.TraceShapeVerdict;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * Pins the Phase 0 oracle-anomaly path: dedup across same-key calls,
 * first-seen sample preservation, verdict integration, and the
 * report-toggle backward-compatibility contract.
 *
 * <p>Touches singleton {@link FaultDetectionTracker} state so each test
 * resets the tracker in {@link #setUp}.
 */
public class FaultDetectionTrackerOracleAnomalyTest {

    private static final String FAKE_FAULTS_JSON =
            "{\"injected_faults\":[{\"faultName\":\"F1\",\"service\":\"svc\",\"api\":\"GET /x\"}]}";

    private Path tempFaultsFile;
    private String prevReportFlag;

    @Before
    public void setUp() throws IOException {
        tempFaultsFile = Files.createTempFile("injected-faults-", ".json");
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
    public void sameKey100Calls_collapseTo1EntryWithHitCount100() {
        FaultDetectionTracker t = FaultDetectionTracker.getInstance();
        for (int i = 0; i < 100; i++) {
            t.recordOracleAnomaly(
                    "SPAN_TREE",
                    "POST /api/v1/orders",
                    "fp_aaaaaaaa",
                    "missing edge order->payment at depth 2",
                    "ERROR",
                    "TestClass" + i,                  // varies per call
                    "test_method_" + i,               // varies per call
                    "trace_" + i);                    // varies per call
        }
        assertEquals(1, t.getOracleAnomaliesForTest().size());
        FaultDetectionTracker.OracleAnomaly only = t.getOracleAnomaliesForTest().values().iterator().next();
        assertEquals(100, only.hitCount);
        // first-seen sample wins
        assertEquals("TestClass0", only.sampleTestClass);
        assertEquals("test_method_0", only.sampleTestMethod);
        assertEquals("trace_0", only.sampleTraceId);
    }

    @Test
    public void differentKeys_produceDistinctEntries() {
        FaultDetectionTracker t = FaultDetectionTracker.getInstance();
        t.recordOracleAnomaly("SPAN_TREE", "POST /a", "sig_1", "d1", "ERROR", "C", "m", "t");
        t.recordOracleAnomaly("SPAN_TREE", "POST /b", "sig_1", "d1", "ERROR", "C", "m", "t"); // different endpoint
        t.recordOracleAnomaly("STATUS_PROPAGATION", "POST /a", "sig_1", "d1", "ERROR", "C", "m", "t"); // different oracle
        t.recordOracleAnomaly("SPAN_TREE", "POST /a", "sig_2", "d1", "ERROR", "C", "m", "t"); // different sig
        assertEquals(4, t.getOracleAnomaliesForTest().size());
    }

    @Test
    public void fingerprint_normalizesDurationsAndIds() {
        // Real Jaeger span IDs are 16 hex chars; use that length so the
        // {8,}-length regex catches them. Common English-hex words like
        // "facade" (6 chars) should NOT be normalized.
        String fp1 = FaultDetectionTracker.fingerprintViolation("TIMING",
                "span 0123abcdef012345 duration=4123µs > 2*p99=2000µs");
        String fp2 = FaultDetectionTracker.fingerprintViolation("TIMING",
                "span ffffff0123456789 duration=9912µs > 2*p99=2000µs");
        assertEquals("fingerprint should ignore concrete µs durations + span hex ids",
                fp1, fp2);
        // different kind → different fp
        assertNotEquals(fp1,
                FaultDetectionTracker.fingerprintViolation("SPAN_TREE",
                        "span 0123abcdef012345 duration=4123µs > 2*p99=2000µs"));
        // English-hex word safety: "facade" (6 chars) preserved
        String fpA = FaultDetectionTracker.fingerprintViolation("X", "facade controller");
        String fpB = FaultDetectionTracker.fingerprintViolation("X", "decade controller");
        assertNotEquals("short all-hex words must remain distinguishable", fpA, fpB);
    }

    @Test
    public void recordVerdict_skipsPassedOutcomes_recordsFailures() {
        FaultDetectionTracker t = FaultDetectionTracker.getInstance();
        TraceShapeVerdict v = TraceShapeVerdict.builder()
                .add(TraceShapeVerdict.InvariantOutcome.pass(
                        "SPAN_TREE_SHAPE", "GET /x", TraceShapeVerdict.Severity.ERROR))
                .add(TraceShapeVerdict.InvariantOutcome.fail(
                        "STATUS_PROPAGATION", "GET /x", TraceShapeVerdict.Severity.ERROR,
                        "depth=0 http=500 expected=[200, 201]", Collections.singletonList("span1")))
                .add(TraceShapeVerdict.InvariantOutcome.fail(
                        "RESPONSE_ENVELOPE", "GET /x", TraceShapeVerdict.Severity.WARN,
                        "status=err is in learned failureSet", Arrays.asList("root")))
                .build();

        t.recordVerdict(v, "GET /x", "io.mist.fake.Test", "test_method", "abc123");
        assertEquals("only failing outcomes recorded", 2, t.getOracleAnomaliesForTest().size());
    }

    @Test
    public void reportWriter_emitsOracleAnomaliesSection() throws IOException {
        FaultDetectionTracker t = FaultDetectionTracker.getInstance();
        t.recordOracleAnomaly("SPAN_TREE", "POST /api/v1/orders", "fp1",
                "missing edge order->payment at depth 2", "ERROR",
                "FlowScenario12", "test_negative_v1", "trace-abc");
        t.recordOracleAnomaly("SPAN_TREE", "POST /api/v1/orders", "fp1",
                "missing edge order->payment at depth 2", "ERROR",
                "FlowScenario12", "test_negative_v2", "trace-def");

        Path reportDir = Files.createTempDirectory("fdt-report-");
        try {
            t.generateReport(reportDir.toString(), "phase0-anomaly-test");
            File[] files = reportDir.toFile().listFiles();
            assertTrue(files != null && files.length > 0);
            String text = new String(Files.readAllBytes(files[0].toPath()));
            assertTrue("section header present", text.contains("ORACLE ANOMALIES"));
            assertTrue("oracle name in body", text.contains("SPAN_TREE"));
            assertTrue("endpoint in body", text.contains("POST /api/v1/orders"));
            assertTrue("hit count rendered", text.contains("2 time(s)"));
            assertTrue("disclaimer text present",
                    text.contains("NOT confirmed bugs"));
            assertTrue("first-seen sample test name preserved",
                    text.contains("test_negative_v1"));
        } finally {
            deleteRecursive(reportDir.toFile());
        }
    }

    @Test
    public void reportWriter_omitsSection_whenFlagFalse() throws IOException {
        System.setProperty("mist.report.oracle.anomalies.enabled", "false");
        try {
            FaultDetectionTracker t = FaultDetectionTracker.getInstance();
            t.recordOracleAnomaly("SPAN_TREE", "POST /x", "fp", "d", "ERROR", "C", "m", "t");

            Path reportDir = Files.createTempDirectory("fdt-report-noflag-");
            try {
                t.generateReport(reportDir.toString(), "phase0-flag-off");
                File[] files = reportDir.toFile().listFiles();
                String text = new String(Files.readAllBytes(files[0].toPath()));
                assertFalse("section absent when flag=false",
                        text.contains("ORACLE ANOMALIES"));
            } finally {
                deleteRecursive(reportDir.toFile());
            }
        } finally {
            System.clearProperty("mist.report.oracle.anomalies.enabled");
        }
    }

    @Test
    public void recordOracleAnomaly_rejectsEmptyKey() {
        FaultDetectionTracker t = FaultDetectionTracker.getInstance();
        t.recordOracleAnomaly("", "ep", "sig", "d", "E", "C", "m", "t");
        t.recordOracleAnomaly("SPAN_TREE", "ep", "", "d", "E", "C", "m", "t");
        assertEquals(0, t.getOracleAnomaliesForTest().size());
    }

    @Test
    public void resetClearsOracleAnomalies() {
        FaultDetectionTracker t = FaultDetectionTracker.getInstance();
        t.recordOracleAnomaly("SPAN_TREE", "ep", "sig", "d", "E", "C", "m", "t");
        assertEquals(1, t.getOracleAnomaliesForTest().size());
        t.reset();
        assertEquals(0, t.getOracleAnomaliesForTest().size());
    }

    private static void deleteRecursive(File f) {
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteRecursive(k);
        }
        f.delete();
    }
}
