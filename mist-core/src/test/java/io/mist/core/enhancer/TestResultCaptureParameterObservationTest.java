package io.mist.core.enhancer;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Phase 1 contract: parameter observation accumulation, snapshot semantics,
 * isNegativeTest gate, and clear-on-drain idempotency.
 */
public class TestResultCaptureParameterObservationTest {

    @Before
    public void setUp() {
        // Wipe ThreadLocal context from a previous test in this JVM so its
        // isNegativeTest doesn't bleed into the next test's recording gate.
        TestResultCapture.clearCurrentTest();
        TestResultCapture.disableCaptureAndGetResults();  // force re-enable below
        TestResultCapture.enableCapture();
        TestResultCapture.clearParameterObservations();
    }

    @After
    public void tearDown() {
        TestResultCapture.clearParameterObservations();
        TestResultCapture.clearResults();
        TestResultCapture.clearCurrentTest();
        TestResultCapture.disableCaptureAndGetResults();
    }

    @Test
    public void successOn2xx_recordsValue() {
        TestResultCapture.recordParameterSuccess("POST /api/v1/orders", "seatClass", "FirstClass", 200);
        Map<String, Set<String>> snap = TestResultCapture.getParameterSuccessSnapshot();
        assertEquals(1, snap.size());
        String key = snap.keySet().iterator().next();
        assertTrue("key contains endpoint", key.contains("POST /api/v1/orders"));
        assertTrue("value recorded", snap.get(key).contains("FirstClass"));
    }

    @Test
    public void non2xx_skipped() {
        TestResultCapture.recordParameterSuccess("e", "p", "v", 400);
        TestResultCapture.recordParameterSuccess("e", "p", "v", 500);
        TestResultCapture.recordParameterSuccess("e", "p", "v", 100);
        assertTrue(TestResultCapture.getParameterSuccessSnapshot().isEmpty());
    }

    @Test
    public void disabledCapture_skipped() {
        TestResultCapture.disableCaptureAndGetResults();
        TestResultCapture.recordParameterSuccess("e", "p", "v", 200);
        assertTrue(TestResultCapture.getParameterSuccessSnapshot().isEmpty());
    }

    @Test
    public void nullArgs_skipped() {
        TestResultCapture.recordParameterSuccess(null, "p", "v", 200);
        TestResultCapture.recordParameterSuccess("e", null, "v", 200);
        TestResultCapture.recordParameterSuccess("e", "p", null, 200);
        assertTrue(TestResultCapture.getParameterSuccessSnapshot().isEmpty());
    }

    @Test
    public void negativeTest_skipped() {
        TestResultCapture.setCurrentTest("TestClass", "test_method");
        TestResultCapture.setStepMetadata(1, "POST /x", "POST", "svc", true /* isNegativeTest */);
        TestResultCapture.recordParameterSuccess("POST /x", "p", "v", 200);
        assertTrue("negative tests never record success",
                TestResultCapture.getParameterSuccessSnapshot().isEmpty());
    }

    @Test
    public void positiveTest_recorded() {
        TestResultCapture.setCurrentTest("TestClass", "test_positive");
        TestResultCapture.setStepMetadata(1, "POST /x", "POST", "svc", false /* isNegativeTest */);
        TestResultCapture.recordParameterSuccess("POST /x", "p", "v", 200);
        Map<String, Set<String>> snap = TestResultCapture.getParameterSuccessSnapshot();
        assertEquals(1, snap.size());
        assertTrue(snap.values().iterator().next().contains("v"));
    }

    @Test
    public void multipleObservations_accumulateSet() {
        TestResultCapture.recordParameterSuccess("e", "p", "v1", 200);
        TestResultCapture.recordParameterSuccess("e", "p", "v2", 200);
        TestResultCapture.recordParameterSuccess("e", "p", "v1", 201); // dupe
        Map<String, Set<String>> snap = TestResultCapture.getParameterSuccessSnapshot();
        assertEquals(1, snap.size());
        assertEquals(2, snap.values().iterator().next().size());
    }

    @Test
    public void differentEndpointsOrParams_keysDistinct() {
        TestResultCapture.recordParameterSuccess("POST /a", "p", "v", 200);
        TestResultCapture.recordParameterSuccess("POST /b", "p", "v", 200);
        TestResultCapture.recordParameterSuccess("POST /a", "q", "v", 200);
        assertEquals(3, TestResultCapture.getParameterSuccessSnapshot().size());
    }

    @Test
    public void rejectOn4xx_recorded_butNotInSuccessSnapshot() {
        TestResultCapture.recordParameterReject("e", "p", "v", 422);
        assertEquals(1, TestResultCapture.getParameterRejectSnapshot().size());
        assertTrue(TestResultCapture.getParameterSuccessSnapshot().isEmpty());
    }

    @Test
    public void rejectOn2xx_skipped() {
        TestResultCapture.recordParameterReject("e", "p", "v", 200);
        assertTrue(TestResultCapture.getParameterRejectSnapshot().isEmpty());
    }

    @Test
    public void clearObservations_dropsBothMaps() {
        TestResultCapture.recordParameterSuccess("e", "p", "v", 200);
        TestResultCapture.recordParameterReject("e", "p", "v", 422);
        TestResultCapture.clearParameterObservations();
        assertTrue(TestResultCapture.getParameterSuccessSnapshot().isEmpty());
        assertTrue(TestResultCapture.getParameterRejectSnapshot().isEmpty());
    }

    @Test
    public void snapshotIsDefensiveCopy() {
        TestResultCapture.recordParameterSuccess("e", "p", "v1", 200);
        Map<String, Set<String>> snap1 = TestResultCapture.getParameterSuccessSnapshot();
        // Mutate snap1 — should not affect later snapshot
        snap1.clear();
        TestResultCapture.recordParameterSuccess("e", "p", "v2", 200);
        Map<String, Set<String>> snap2 = TestResultCapture.getParameterSuccessSnapshot();
        assertEquals(1, snap2.size());
        assertEquals(2, snap2.values().iterator().next().size());
    }
}
