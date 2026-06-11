package io.mist.core.analysis;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Pins the end-of-run console findings summary ({@link FaultDetectionTracker#summarizeAnomalies()}
 * + {@link FaultDetectionTracker.AnomalySummary#render}). Added after a review found the summary
 * was being printed to a log4j-filtered stream (never reaching the terminal) AND that no test
 * exercised the rendered content. These tests cover the data/format; the channel fix lives in
 * MistRunner (ConsoleProgressBar.printRaw, not System.out).
 *
 * <p>Touches singleton {@link FaultDetectionTracker} state, so each test resets it.
 */
public class FaultDetectionTrackerSummaryTest {

    @Before public void setUp()    { FaultDetectionTracker.getInstance().reset(); }
    @After  public void tearDown() { FaultDetectionTracker.getInstance().reset(); }

    private int recSeq = 0;

    private void rec(String kind, String endpoint, String sig, String detail, String sev) {
        // Fresh marker per observation, mirroring production where every step
        // execution carries its own UUID. A constant marker would now be
        // collapsed by the per-execution dedup in recordOracleAnomaly.
        FaultDetectionTracker.getInstance().recordOracleAnomaly(
                kind, endpoint, sig, detail, sev, "C", "m", "trace-" + (recSeq++));
    }

    @Test
    public void emptyRun_saysNoAnomalies() {
        FaultDetectionTracker.AnomalySummary s = FaultDetectionTracker.getInstance().summarizeAnomalies();
        assertEquals(0, s.total());
        String out = s.render("logs/x", false);
        assertTrue(out, out.contains("no oracle anomalies"));
    }

    @Test
    public void counts_bucketBySeverityAndKind_distinctNotHits() {
        FaultDetectionTracker.getInstance().recordTestCase("C", "m1");
        FaultDetectionTracker.getInstance().recordTestCase("C", "m2");
        FaultDetectionTracker.getInstance().recordTestCase("C", "m3");
        rec("HIDDEN_DOWNSTREAM_FAILURE", "GET /a", "sigA", "a->b http=503 otel=ERROR", "ERROR");
        rec("HIDDEN_DOWNSTREAM_FAILURE", "GET /b", "sigB", "b->c otel=ERROR", "WARN");
        rec("RESPONSE_ENVELOPE", "POST /login", "sigC", "200 body status=0", "ERROR");
        // same key as sigA twice more -> dedup to 1 distinct, hitCount grows
        rec("HIDDEN_DOWNSTREAM_FAILURE", "GET /a", "sigA", "a->b http=503 otel=ERROR", "ERROR");
        rec("HIDDEN_DOWNSTREAM_FAILURE", "GET /a", "sigA", "a->b http=503 otel=ERROR", "ERROR");

        FaultDetectionTracker.AnomalySummary s = FaultDetectionTracker.getInstance().summarizeAnomalies();
        assertEquals("3 distinct anomalies", 3, s.total());
        assertEquals(2, s.errorCount);
        assertEquals(1, s.warnCount);
        assertEquals("5 raw hits across 3 distinct", 5, s.totalHits);
        assertEquals(3, s.testCaseCount);
    }

    @Test
    public void render_isActionable_namesKindEndpointAndCounts() {
        FaultDetectionTracker.getInstance().recordTestCase("C", "m1");
        rec("HIDDEN_DOWNSTREAM_FAILURE", "GET /api/v1/products/0/reviews",
                "sig1", "reviews->ratings:9080 http=503 otel=ERROR (swallowed)", "ERROR");
        String out = FaultDetectionTracker.getInstance().summarizeAnomalies().render("logs/fdr", false);
        assertTrue(out, out.contains("Hidden downstream failure"));            // kind label
        assertTrue(out, out.contains("GET /api/v1/products/0/reviews"));       // B2: which endpoint
        assertTrue(out, out.contains("ratings:9080 http=503"));                // B2: what was swallowed
        assertTrue(out, out.contains("1 ERROR"));                              // count line
        assertTrue(out, out.contains("ERROR findings fail the test"));         // B3: gating explainer
        assertTrue(out, out.contains("logs/fdr"));                             // detail pointer
    }

    @Test
    public void asciiMode_hasNoEmoji() {
        rec("HIDDEN_DOWNSTREAM_FAILURE", "GET /a", "sigA", "swallowed", "ERROR");
        String ascii = FaultDetectionTracker.getInstance().summarizeAnomalies().render("logs/x", true);
        assertTrue(ascii, ascii.contains("[HIDDEN]"));
        assertFalse("no emoji in ascii mode", ascii.contains("🕳")); // 🕳️
        assertFalse("no unicode bullet in ascii mode", ascii.contains("▸")); // ▸
    }
}
