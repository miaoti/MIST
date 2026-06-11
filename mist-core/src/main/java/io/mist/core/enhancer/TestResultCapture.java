package io.mist.core.enhancer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe utility for capturing test execution results.
 * Generated tests call these static methods to store response data
 * that can later be collected by the Test Case Enhancer.
 * 
 * Uses ThreadLocal storage to ensure thread-safety in parallel test execution.
 */
public class TestResultCapture {
    
    private static final Logger log = LogManager.getLogger(TestResultCapture.class);
    
    // ThreadLocal storage for current test context
    private static final ThreadLocal<TestContext> currentContext = new ThreadLocal<>();
    
    // Global storage for all captured results (thread-safe)
    private static final Map<String, FailedTestResult> capturedResults = new ConcurrentHashMap<>();

    // Phase 1: per-value observations from positive (non-faulty) test steps.
    // Keyed by "<endpoint>\u0001<paramName>" (\u0001 delimiter so paths
    // containing "::" don't collide). Values are concurrent sets so writer
    // emissions from parallel JUnitCores don't race.
    private static final Map<String, java.util.Set<String>> parameterSuccessObservations
            = new ConcurrentHashMap<>();
    private static final Map<String, java.util.Set<String>> parameterRejectObservations
            = new ConcurrentHashMap<>();
    
    // Flag to enable/disable capture
    private static volatile boolean captureEnabled = false;
    
    /**
     * Enable result capture (called before test execution starts).
     *
     * <p>Idempotent: only clears {@link #capturedResults} on the FIRST call
     * when capture is currently disabled. Subsequent calls while capture is
     * already enabled are no-ops. This lets parallel JUnitCores each register
     * a {@code FailedTestCollector} listener without racing to clear the
     * shared results map — each listener's {@code testRunStarted} fires
     * {@code enableCapture()}, but only the first one wins the clear.
     *
     * <p>Before this idempotency guard, commit e3d7e4b6 sidestepped the race
     * by NOT registering the listener on per-task JUnitCores at all, but that
     * meant {@code testFailure} never fired and {@code markTestFailed} was
     * never called — so every parallel run reported 0 enhanceable failures.
     */
    public static synchronized void enableCapture() {
        if (!captureEnabled) {
            capturedResults.clear();
            captureEnabled = true;
            log.info("Test result capture ENABLED (map cleared)");
        } else {
            log.debug("Test result capture already enabled — preserving {} captured results",
                    capturedResults.size());
        }
    }
    
    /**
     * Disable result capture and return all captured results.
     * NOTE: Results are NOT cleared - call clearResults() when done with exploration.
     */
    public static Map<String, FailedTestResult> disableCaptureAndGetResults() {
        captureEnabled = false;
        Map<String, FailedTestResult> results = new ConcurrentHashMap<>(capturedResults);
        // Don't clear here - results needed for status code exploration
        log.info("Test result capture DISABLED. Captured {} results", results.size());
        return results;
    }
    
    /**
     * Get a snapshot of captured results without modifying state.
     * Use this for status code exploration after disableCaptureAndGetResults().
     */
    public static Map<String, FailedTestResult> getResultsSnapshot() {
        return new ConcurrentHashMap<>(capturedResults);
    }
    
    /**
     * Explicitly clear all captured results.
     * Call this after status code exploration is complete.
     */
    public static void clearResults() {
        int count = capturedResults.size();
        capturedResults.clear();
        log.debug("Cleared {} captured results", count);
    }
    
    /**
     * Check if capture is enabled.
     */
    public static boolean isCaptureEnabled() {
        return captureEnabled;
    }
    
    /**
     * Set the current test being executed.
     * Called at the beginning of each test method.
     */
    public static void setCurrentTest(String testClassName, String testMethodName) {
        if (!captureEnabled) return;
        
        TestContext context = new TestContext();
        context.testClassName = testClassName;
        context.testMethodName = testMethodName;
        context.startTime = System.currentTimeMillis();
        currentContext.set(context);
        
        log.debug("Started capturing test: {}.{}", testClassName, testMethodName);
    }

    /**
     * Record that a specific step executed in bypass mode during this test.
     * Called from the generated {@code else} branch of every fallback block.
     *
     * @param stepIndex the 1-based step index that activated bypass
     */
    public static void recordBypassTriggered(int stepIndex) {
        if (!captureEnabled) return;
        TestContext context = currentContext.get();
        if (context != null) {
            context.bypassedSteps.add(stepIndex);
        }
    }
    
    /**
     * Set test metadata (legacy single-step form, delegates to step 1).
     */
    public static void setTestMetadata(String endpoint, String httpMethod, 
                                       String serviceName, boolean isNegativeTest) {
        setStepMetadata(1, endpoint, httpMethod, serviceName, isNegativeTest);
    }
    
    /**
     * Set metadata for a specific step within a multi-root sequence test.
     * Can be called multiple times, once per step.
     */
    public static void setStepMetadata(int stepIndex, String endpoint, String httpMethod,
                                       String serviceName, boolean isNegativeTest) {
        if (!captureEnabled) return;
        
        TestContext context = currentContext.get();
        if (context != null) {
            StepContext stepCtx = new StepContext();
            stepCtx.stepIndex = stepIndex;
            stepCtx.endpoint = endpoint;
            stepCtx.httpMethod = httpMethod;
            stepCtx.serviceName = serviceName;
            stepCtx.isNegativeTest = isNegativeTest;
            context.stepContexts.put(stepIndex, stepCtx);
            // Keep the latest step as the "current" for backward-compat
            context.endpoint = endpoint;
            context.httpMethod = httpMethod;
            context.serviceName = serviceName;
            context.isNegativeTest = isNegativeTest;
            context.currentStepIndex = stepIndex;
        }
    }
    
    /**
     * Add a parameter snapshot for the current test (legacy form, delegates to step-aware version).
     */
    public static void addParameter(String name, String value, String type, 
                                   String location, String description, String example) {
        addParameter(name, value, type, location, description, example, false);
    }
    
    /**
     * Add a parameter snapshot with required flag (legacy form, uses current step index).
     */
    public static void addParameter(String name, String value, String type, 
                                   String location, String description, String example, boolean required) {
        if (!captureEnabled) return;
        TestContext context = currentContext.get();
        int step = (context != null) ? context.currentStepIndex : 1;
        addParameter(name, value, type, location, description, example, required, step, false);
    }
    
    /**
     * Add a parameter snapshot with full step-awareness and data-injection flag.
     */
    public static void addParameter(String name, String value, String type, 
                                   String location, String description, String example,
                                   boolean required, int stepIndex, boolean dataInjected) {
        if (!captureEnabled) return;
        
        TestContext context = currentContext.get();
        if (context != null) {
            ParameterSnapshot param = ParameterSnapshot.builder()
                    .name(name)
                    .value(value)
                    .type(type)
                    .location(location)
                    .description(description)
                    .example(example)
                    .required(required)
                    .stepIndex(stepIndex)
                    .dataInjected(dataInjected)
                    .build();
            context.parameters.add(param);
        }
    }
    
    /**
     * Capture the response from a step execution (legacy form, uses current step index).
     */
    public static void captureResponse(int statusCode, String responseBody) {
        if (!captureEnabled) return;
        TestContext context = currentContext.get();
        int step = (context != null) ? context.currentStepIndex : 1;
        captureStepResponse(step, statusCode, responseBody);
    }
    
    /**
     * Capture the response from a specific step execution, recording which step produced it.
     */
    public static void captureStepResponse(int stepIndex, int statusCode, String responseBody) {
        if (!captureEnabled) return;
        
        TestContext context = currentContext.get();
        if (context != null) {
            context.lastStatusCode = statusCode;
            context.lastResponseBody = responseBody;
            context.lastFailedStepIndex = stepIndex;
            StepContext stepCtx = context.stepContexts.get(stepIndex);
            if (stepCtx != null) {
                stepCtx.statusCode = statusCode;
                stepCtx.responseBody = responseBody;
            }
        }
    }
    
    /**
     * Mark the current test as failed and store the result.
     * Resolves the failed step from the last captured response's step index.
     */
    public static void markTestFailed(String errorMessage, String failureType) {
        if (!captureEnabled) return;
        
        TestContext context = currentContext.get();
        if (context == null) {
            log.warn("No test context found when marking test as failed");
            return;
        }
        
        String testKey = context.testClassName + "." + context.testMethodName;
        
        // Resolve the failed step: use the step that last called captureResponse
        int failedStep = context.lastFailedStepIndex;
        StepContext failedStepCtx = context.stepContexts.get(failedStep);
        
        // Use per-step metadata when available, fall back to global context
        String endpoint = (failedStepCtx != null) ? failedStepCtx.endpoint : context.endpoint;
        String httpMethod = (failedStepCtx != null) ? failedStepCtx.httpMethod : context.httpMethod;
        String serviceName = (failedStepCtx != null) ? failedStepCtx.serviceName : context.serviceName;
        
        // A step failed in bypass mode when it ran with a synthetic fallback value
        // because an upstream producer step had already failed.
        boolean bypassTriggered = context.bypassedSteps.contains(failedStep);

        FailedTestResult result = FailedTestResult.builder()
                .testClassName(context.testClassName)
                .testMethodName(context.testMethodName)
                .endpoint(endpoint)
                .httpMethod(httpMethod)
                .serviceName(serviceName)
                .negativeTest(context.isNegativeTest)
                .invalidParameters(new ArrayList<>(context.invalidParameters))
                .actualStatusCode(context.lastStatusCode)
                .responseBody(context.lastResponseBody)
                .errorMessage(errorMessage)
                .failureType(failureType)
                .parameters(new ArrayList<>(context.parameters))
                .executionTimestamp(context.startTime)
                .failedStepIndex(failedStep)
                .lockedDependencyParams(context.lockedParamsByStep.get(failedStep))
                .bypassTriggered(bypassTriggered)
                .build();
        
        capturedResults.put(testKey, result);
        log.debug("Captured failed test: {} (status: {}, failedStep: {}, enhanceable: {})", 
                testKey, context.lastStatusCode, failedStep, result.isEnhanceable());
    }
    
    /**
     * Clear the current test context (called at end of test method).
     */
    public static void clearCurrentTest() {
        currentContext.remove();
    }
    
    /**
     * Get all captured failed test results.
     */
    public static List<FailedTestResult> getAllCapturedResults() {
        return new ArrayList<>(capturedResults.values());
    }
    
    /**
     * Get only enhanceable failures (non-5xx errors).
     */
    public static List<FailedTestResult> getEnhanceableFailures() {
        List<FailedTestResult> enhanceable = new ArrayList<>();
        for (FailedTestResult result : capturedResults.values()) {
            if (result.isEnhanceable()) {
                enhanceable.add(result);
            }
        }
        return enhanceable;
    }
    
    /**
     * Register the set of structurally locked dependency parameters for a step.
     * These are the param names from StepCall.getParamDependencies().keySet()
     * that are JIT-wired to capturedOutputs at runtime and must never be modified.
     *
     * @param stepIndex the 1-based step index
     * @param lockedParamNames parameter names wired to runtime dependencies
     */
    public static void setLockedDependencyParams(int stepIndex, Set<String> lockedParamNames) {
        if (!captureEnabled) return;
        TestContext context = currentContext.get();
        if (context != null && lockedParamNames != null && !lockedParamNames.isEmpty()) {
            context.lockedParamsByStep.put(stepIndex, new HashSet<>(lockedParamNames));
        }
    }
    
    /**
     * Phase 1: record one parameter-value observation on a 2xx step of a
     * positive (non-faulty) test. The MistRunner drains these into
     * {@link io.mist.core.smart.InputFetchRegistry#markVerified} between
     * Phase A and Phase B. Recording is gated on {@link #captureEnabled} so
     * idle generations don't accumulate noise. Idempotent for the same
     * (endpoint, paramName, value) triple within a phase.
     */
    public static void recordParameterSuccess(String endpoint, String paramName,
                                              String value, int statusCode) {
        if (!captureEnabled) return;
        if (endpoint == null || paramName == null || value == null) return;
        if (statusCode < 200 || statusCode >= 300) return;
        // Only record for positive (non-faulty) tests. Negative variants
        // deliberately inject invalid values for the target parameter; their
        // non-target params come from the same pool but the 2xx success path
        // here is never reached on a negative variant. Belt-and-braces guard
        // in case a fault doesn't actually trigger and a negative test passes.
        TestContext context = currentContext.get();
        if (context != null && context.isNegativeTest) return;
        String key = endpoint + "\u0001" + paramName;
        parameterSuccessObservations
                .computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet())
                .add(value);
    }

    /**
     * Phase 1: record one parameter-value observation on a 4xx/5xx step of a
     * positive (non-faulty) test — the SUT rejected the value despite our
     * intent to succeed, so the value is suspect for future positive runs.
     * Not yet wired by the writer (success path is enough for Phase 1's
     * Sniper filter); kept here so a future writer hook can emit it.
     */
    public static void recordParameterReject(String endpoint, String paramName,
                                             String value, int statusCode) {
        if (!captureEnabled) return;
        if (endpoint == null || paramName == null || value == null) return;
        if (statusCode < 400) return;
        String key = endpoint + "\u0001" + paramName;
        parameterRejectObservations
                .computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet())
                .add(value);
    }

    /**
     * Snapshot of all observed (endpoint, paramName) → values that returned
     * 2xx during the current capture window. Defensive deep copy: callers
     * can iterate without races against ongoing writer emissions.
     */
    public static Map<String, java.util.Set<String>> getParameterSuccessSnapshot() {
        Map<String, java.util.Set<String>> snapshot = new java.util.HashMap<>();
        for (Map.Entry<String, java.util.Set<String>> e : parameterSuccessObservations.entrySet()) {
            snapshot.put(e.getKey(), new java.util.HashSet<>(e.getValue()));
        }
        return snapshot;
    }

    /**
     * Snapshot of all observed (endpoint, paramName) → values that returned
     * 4xx/5xx during the current capture window.
     */
    public static Map<String, java.util.Set<String>> getParameterRejectSnapshot() {
        Map<String, java.util.Set<String>> snapshot = new java.util.HashMap<>();
        for (Map.Entry<String, java.util.Set<String>> e : parameterRejectObservations.entrySet()) {
            snapshot.put(e.getKey(), new java.util.HashSet<>(e.getValue()));
        }
        return snapshot;
    }

    /**
     * Clear the parameter-observation maps between phases. Mirrors the
     * idempotent enableCapture clear-on-transition semantics: caller is
     * expected to drain first (via getParameterSuccessSnapshot) then clear.
     */
    public static void clearParameterObservations() {
        parameterSuccessObservations.clear();
        parameterRejectObservations.clear();
    }

    /**
     * Add an invalid parameter (for negative tests).
     * These parameters are intentionally invalid and should NOT be changed during enhancement.
     */
    public static void addInvalidParameter(String paramNameAndValue) {
        if (!captureEnabled) return;
        
        TestContext context = currentContext.get();
        if (context != null) {
            context.invalidParameters.add(paramNameAndValue);
        }
    }
    
    /**
     * Internal context for a single test execution.
     */
    private static class TestContext {
        String testClassName;
        String testMethodName;
        String endpoint;
        String httpMethod;
        String serviceName;
        boolean isNegativeTest;
        int lastStatusCode;
        String lastResponseBody;
        long startTime;
        List<ParameterSnapshot> parameters = new ArrayList<>();
        List<String> invalidParameters = new ArrayList<>();
        int currentStepIndex = 1;
        int lastFailedStepIndex = 1;
        java.util.Map<Integer, StepContext> stepContexts = new java.util.HashMap<>();
        // Per-step locked dependency param names (from StepCall.getParamDependencies().keySet())
        java.util.Map<Integer, Set<String>> lockedParamsByStep = new java.util.HashMap<>();
        // Step indices that activated the resilient bypass (upstream producer failed)
        Set<Integer> bypassedSteps = new HashSet<>();
    }
    
    /**
     * Per-step context within a multi-root sequence test.
     */
    private static class StepContext {
        int stepIndex;
        String endpoint;
        String httpMethod;
        String serviceName;
        boolean isNegativeTest;
        int statusCode;
        String responseBody;
    }
}

