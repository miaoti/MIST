package io.mist.core.enhancer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * JUnit RunListener that collects failed test information for enhancement.
 * Works in conjunction with TestResultCapture to gather complete test context.
 */
public class FailedTestCollector extends RunListener {
    
    private static final Logger log = LogManager.getLogger(FailedTestCollector.class);
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    
    private final List<FailedTestResult> failedTests = new ArrayList<>();
    // Tracks which test keys (class.method) have already been drained into
    // failedTests, so calling drainFromTestResultCapture multiple times (once
    // per parallel JUnitCore's testRunFinished) doesn't duplicate entries.
    // Keyed by testClassName + "." + testMethodName.
    private final Set<String> drainedKeys = new HashSet<>();
    private final int currentRound;
    private final boolean skip5xx;
    private final String outputDir;
    
    public FailedTestCollector(int currentRound, boolean skip5xx, String outputDir) {
        this.currentRound = currentRound;
        this.skip5xx = skip5xx;
        this.outputDir = outputDir;
    }
    
    @Override
    public void testRunStarted(Description description) throws Exception {
        log.info("🔍 FailedTestCollector: Starting test run (Round {})", currentRound);
        TestResultCapture.enableCapture();
    }
    
    @Override
    public void testStarted(Description description) throws Exception {
        String className = description.getClassName();
        String methodName = description.getMethodName();
        TestResultCapture.setCurrentTest(className, methodName);
    }
    
    @Override
    public void testFailure(Failure failure) throws Exception {
        Description description = failure.getDescription();
        String testKey = description.getClassName() + "." + description.getMethodName();
        
        // Determine failure type
        String failureType = "ASSERTION";
        Throwable exception = failure.getException();
        if (exception != null) {
            if (exception instanceof AssertionError) {
                failureType = "ASSERTION";
            } else if (exception instanceof java.net.ConnectException) {
                failureType = "CONNECTION";
            } else if (exception instanceof java.net.SocketTimeoutException) {
                failureType = "TIMEOUT";
            } else {
                failureType = "EXCEPTION";
            }
        }
        
        // Mark the test as failed in TestResultCapture
        TestResultCapture.markTestFailed(
                failure.getMessage() != null ? failure.getMessage() : "No message",
                failureType
        );
        
        log.debug("🔴 Test failed: {} (type: {})", testKey, failureType);
    }
    
    @Override
    public void testFinished(Description description) throws Exception {
        TestResultCapture.clearCurrentTest();
    }
    
    @Override
    public void testRunFinished(Result result) throws Exception {
        log.info("🔍 FailedTestCollector: Test run finished. Failures: {}", result.getFailureCount());
        drainFromTestResultCapture();
    }

    /**
     * Drain {@link TestResultCapture}'s in-memory captured results into this
     * collector's {@link #failedTests} list and persist them to disk.
     *
     * <p>This is the body of {@link #testRunFinished(Result)} extracted so the
     * parallel-execution path in MistRunner can run multiple JUnitCores (each
     * without its own collector listener), then drain once at the end.  Running
     * the drain per-JUnitCore would race on
     * {@link TestResultCapture#enableCapture()} / {@code disable…} and lose
     * results.
     */
    public synchronized void drainFromTestResultCapture() {
        // Use snapshot — NOT disable — because in parallel mode this method is
        // called once per per-task JUnitCore's testRunFinished. Disabling on
        // the first call would cause subsequent tasks' markTestFailed to drop
        // results. MistRunner explicitly disables capture once after all
        // parallel tasks finish (or it stays enabled until the next round
        // re-enables it idempotently — both are fine).
        Map<String, FailedTestResult> capturedResults = TestResultCapture.getResultsSnapshot();

        int addedThisDrain = 0;
        int skippedThisDrain = 0;
        for (FailedTestResult failed : capturedResults.values()) {
            String testKey = failed.getTestClassName() + "." + failed.getTestMethodName();
            // Idempotency: this captured result was already drained earlier
            // (e.g. by an earlier JUnitCore's testRunFinished). Skip without
            // re-adding so failedTests holds at most one entry per test.
            if (!drainedKeys.add(testKey)) continue;

            // Skip 5xx errors if configured
            if (skip5xx && failed.getActualStatusCode() >= 500) {
                log.debug("⏭️  Skipping 5xx error: {} (status: {})",
                        failed.getTestMethodName(), failed.getActualStatusCode());
                skippedThisDrain++;
                continue;
            }

            // Skip bypass-triggered failures: the step ran with a synthetic fallback
            // value because its upstream producer already failed. The root cause is
            // upstream; enhancing this step's parameters cannot fix the flow.
            if (failed.isBypassTriggered()) {
                log.debug("⏭️  Skipping enhancement: {} (step ran in ⚡ Bypass Mode — upstream failure caused fallback)",
                        failed.getTestMethodName());
                skippedThisDrain++;
                continue;
            }

            failed.setEnhancementRound(currentRound);
            failedTests.add(failed);
            addedThisDrain++;
        }

        log.info("📊 Collected {} enhanceable failed tests (added {} skipped {} this drain; cumulative skipped {})",
                failedTests.size(), addedThisDrain, skippedThisDrain,
                capturedResults.size() - failedTests.size());

        // Save to file
        saveFailedTestsToFile();
    }
    
    /**
     * Get all collected failed tests that are eligible for enhancement.
     */
    public List<FailedTestResult> getFailedTests() {
        return new ArrayList<>(failedTests);
    }
    
    /**
     * Get count of failed tests.
     */
    public int getFailedTestCount() {
        return failedTests.size();
    }
    
    /**
     * Save failed tests to JSON file for debugging/inspection.
     */
    private void saveFailedTestsToFile() {
        if (failedTests.isEmpty()) {
            log.info("No failed tests to save");
            return;
        }
        
        try {
            File dir = new File(outputDir, "round-" + currentRound);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            
            File outputFile = new File(dir, "failed-tests.json");
            objectMapper.writeValue(outputFile, failedTests);
            log.info("💾 Saved {} failed tests to: {}", failedTests.size(), outputFile.getAbsolutePath());
            
        } catch (IOException e) {
            log.error("Failed to save failed tests to file: {}", e.getMessage());
        }
    }
    
    /**
     * Load failed tests from a previous round's file.
     */
    public static List<FailedTestResult> loadFromFile(String outputDir, int round) {
        try {
            File inputFile = new File(outputDir, "round-" + round + "/failed-tests.json");
            if (!inputFile.exists()) {
                log.warn("No failed tests file found for round {}", round);
                return new ArrayList<>();
            }
            
            FailedTestResult[] results = objectMapper.readValue(inputFile, FailedTestResult[].class);
            log.info("📂 Loaded {} failed tests from round {}", results.length, round);
            return new ArrayList<>(List.of(results));
            
        } catch (IOException e) {
            log.error("Failed to load failed tests from file: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
    
    /**
     * Create a summary report of failed tests.
     */
    public String createSummaryReport() {
        StringBuilder report = new StringBuilder();
        report.append("╔══════════════════════════════════════════════════════════════════════════════╗\n");
        report.append("║                     FAILED TEST COLLECTION REPORT                           ║\n");
        report.append("║                            Round ").append(currentRound).append("                                           ║\n");
        report.append("╠══════════════════════════════════════════════════════════════════════════════╣\n");
        
        if (failedTests.isEmpty()) {
            report.append("║  🎉 No failed tests to enhance!                                             ║\n");
        } else {
            report.append(String.format("║  📊 Total Failed Tests: %-52d ║%n", failedTests.size()));
            report.append("╠══════════════════════════════════════════════════════════════════════════════╣\n");
            
            int count = 0;
            for (FailedTestResult failed : failedTests) {
                count++;
                report.append(String.format("║  %d. %-72s ║%n", count, 
                        truncate(failed.getTestMethodName(), 70)));
                report.append(String.format("║     Endpoint: %-62s ║%n", 
                        truncate(failed.getEndpoint(), 60)));
                report.append(String.format("║     Status: %-3d | Type: %-8s | Params: %-24d ║%n", 
                        failed.getActualStatusCode(),
                        failed.isNegativeTest() ? "NEGATIVE" : "POSITIVE",
                        failed.getParameters().size()));
                if (count < failedTests.size()) {
                    report.append("║  ──────────────────────────────────────────────────────────────────────────  ║\n");
                }
            }
        }
        
        report.append("╚══════════════════════════════════════════════════════════════════════════════╝\n");
        return report.toString();
    }
    
    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen - 3) + "..." : s;
    }
}




