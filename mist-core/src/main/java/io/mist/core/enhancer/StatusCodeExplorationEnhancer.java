package io.mist.core.enhancer;

import io.mist.core.auth.AuthManipulationStrategy;
import io.mist.core.config.CacheToggle;
import io.mist.core.coverage.LLMStatusCodeDiscovery;
import io.mist.core.coverage.StatusCodeCoverageTracker;
import io.mist.core.coverage.StatusCodeTarget;
import io.mist.core.llm.ParameterInfo;
import io.mist.llm.LLMService;
import io.mist.core.testcase.MultiServiceTestCase;
import io.mist.core.util.ConsoleProgressBar;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Enhancer that creates new test cases to explore untriggered HTTP status codes.
 * 
 * This enhancer works AFTER the first execution round:
 * 1. Runs LLM Discovery to identify all possible status codes per API
 * 2. Tracks which status codes have been triggered
 * 3. For each test case, asks LLM if it's a good candidate for exploration
 * 4. Creates NEW test cases to target untriggered status codes
 * 
 * Key design principles:
 * - Creates NEW tests, doesn't modify originals
 * - Uses round-robin to avoid targeting same code twice per round
 * - Preserves original test enhancement logic (works alongside TestCaseEnhancer)
 */
public class StatusCodeExplorationEnhancer {
    
    private static final Logger log = LogManager.getLogger(StatusCodeExplorationEnhancer.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    private final LLMService llmService;
    private final LLMStatusCodeDiscovery discovery;
    private final StatusCodeCoverageTracker tracker;
    private final AuthManipulationStrategy authStrategy;
    
    private final int maxTokens;
    private final double temperature;
    
    // Configuration
    private int maxExplorationTestsPerOriginal = 3;
    private int maxExplorationTestsPerRound = 20;
    private int maxRetryPerStatusCode = 2;  // Max retry attempts for each status code
    private boolean enabled = true;
    
    // Track failed inputs per (apiKey, statusCode) to avoid repeating them
    private final Map<String, Set<String>> failedInputsPerTarget = new HashMap<>();

    // ─── Signature-based persistent cache for exploration suggestions ────
    /** Cache file path property. Default {@code .mist/llm-exploration-suggest-cache.json}.
     *  Read/write is governed by the master {@link CacheToggle} pair so operators
     *  set caching policy in ONE place ({@code mst.cache.read} / {@code mst.cache.write}). */
    public static final String PROP_CACHE_PATH = "mst.exploration.suggest.cache.path";

    private static final String DEFAULT_CACHE_PATH = ".mist/llm-exploration-suggest-cache.json";

    /** Cache entries: signature → raw LLM response JSON string. Re-parsed on hit. */
    private final Map<String, String> evaluationCache = new HashMap<>();
    private final Path cachePersistPath;
    private final Object cacheDiskLock = new Object();
    
    /**
     * Callback interface for executing a single exploration test.
     * Used to enable the feedback loop where we execute tests immediately and check results.
     */
    public interface ExplorationTestExecutor {
        /**
         * Execute a single exploration test and return the actual status code.
         * @param test The exploration test to execute
         * @return The actual HTTP status code returned, or -1 if execution failed
         */
        int executeAndGetStatusCode(MultiServiceTestCase test);
    }
    
    public StatusCodeExplorationEnhancer(LLMService llmService) {
        this(llmService, new StatusCodeCoverageTracker());
    }
    
    public StatusCodeExplorationEnhancer(LLMService llmService, StatusCodeCoverageTracker tracker) {
        this.llmService = llmService;
        this.discovery = new LLMStatusCodeDiscovery(llmService);
        this.tracker = tracker;
        this.authStrategy = new AuthManipulationStrategy();
        this.maxTokens = 1000;
        this.temperature = 0.3;
        this.cachePersistPath = Paths.get(System.getProperty(PROP_CACHE_PATH, DEFAULT_CACHE_PATH));
        loadCacheFromDisk();
        if (!CacheToggle.canRead() || !CacheToggle.canWrite()) {
            log.info("ExplorationEnhancer LLM-suggest cache: master read={} write={} (loaded {} entries)",
                    CacheToggle.canRead(), CacheToggle.canWrite(), evaluationCache.size());
        }
    }
    
    /**
     * Main entry point: Process executed tests and create exploration tests.
     * 
     * @param executedTests Tests that were executed
     * @param executionResults Map of test name to execution result (status code, response, etc.)
     * @return ExplorationResult containing new exploration tests and coverage summary
     */
    public ExplorationResult explore(
            List<MultiServiceTestCase> executedTests,
            Map<String, TestExecutionResult> executionResults) {
        
        if (!enabled) {
            log.info("Status code exploration is disabled");
            return new ExplorationResult(Collections.emptyList(), tracker.getOverallCoverageSummary());
        }
        
        log.info("=== STATUS CODE EXPLORATION PHASE ===");
        log.info("Processing {} executed tests for exploration", executedTests.size());
        
        // STEP 1: Run LLM Discovery for each unique API
        runDiscoveryForApis(executedTests, executionResults);
        
        // STEP 2: Update triggered status codes from execution results
        updateTriggeredStatusCodes(executionResults);
        
        // Log current coverage
        tracker.logCoverageReport();
        
        // STEP 3: Create exploration tests
        List<MultiServiceTestCase> explorationTests = new ArrayList<>();
        int totalCreated = 0;
        
        for (MultiServiceTestCase test : executedTests) {
            if (totalCreated >= maxExplorationTestsPerRound) {
                log.info("Reached max exploration tests per round ({})", maxExplorationTestsPerRound);
                break;
            }
            
            // Skip tests that are already exploration tests
            if (test.isStatusCodeExplorationTest()) {
                log.debug("Skipping exploration test: {}", test.getOperationId());
                continue;
            }
            
            TestExecutionResult result = executionResults.get(test.getOperationId());
            if (result == null) {
                log.warn("No execution result for test: {}", test.getOperationId());
                continue;
            }
            
            String apiKey = getApiKey(test);
            
            // Get untriggered codes for this API
            List<StatusCodeTarget> untriggeredCodes = tracker.getUntriggeredCodes(apiKey);
            if (untriggeredCodes.isEmpty()) {
                log.debug("All status codes already triggered/targeted for {}", apiKey);
                continue;
            }
            
            // Ask LLM if this test is a good exploration candidate
            List<ExplorationSuggestion> suggestions = evaluateExplorationCandidate(
                test, result, untriggeredCodes);
            
            // Build a map of status code -> StatusCodeTarget for quick lookup
            Map<Integer, StatusCodeTarget> targetsByCode = new HashMap<>();
            for (StatusCodeTarget target : untriggeredCodes) {
                targetsByCode.put(target.getStatusCode(), target);
            }
            
            // Create exploration tests from suggestions
            int createdForThisTest = 0;
            for (ExplorationSuggestion suggestion : suggestions) {
                if (totalCreated >= maxExplorationTestsPerRound) break;
                if (createdForThisTest >= maxExplorationTestsPerOriginal) break;
                
                int targetCode = suggestion.getTargetStatusCode();
                
                // Skip if already targeted this round (round-robin)
                if (tracker.isTargeted(apiKey, targetCode)) {
                    log.debug("Status {} already targeted for {} this round", targetCode, apiKey);
                    continue;
                }
                
                // Get the StatusCodeTarget for this status code (has suggestedInputs from discovery)
                StatusCodeTarget statusCodeTarget = targetsByCode.get(targetCode);
                
                // Create new exploration test, passing the StatusCodeTarget for fallback inputs
                MultiServiceTestCase explorationTest = createExplorationTest(test, suggestion, statusCodeTarget);
                if (explorationTest != null) {
                    explorationTests.add(explorationTest);
                    tracker.markTargeted(apiKey, targetCode);
                    totalCreated++;
                    createdForThisTest++;
                    
                    log.info("Created exploration test {} targeting status {}", 
                        explorationTest.getOperationId(), targetCode);
                }
            }
        }
        
        log.info("=== EXPLORATION COMPLETE: Created {} new tests ===", explorationTests.size());
        
        return new ExplorationResult(explorationTests, tracker.getOverallCoverageSummary());
    }
    
    /**
     * Efficient exploration: ONE LLM call per test case, batch execute all exploration tests.
     * 
     * Correct flow:
     * 1. For each test case, ask LLM ONCE: "Should we explore? Give ALL suggestions with parameters"
     * 2. LLM returns ALL suggestions at once (e.g., [{status:400, params:{...}}, {status:422, params:{...}}])
     * 3. Generate ALL exploration tests at once from LLM suggestions
     * 4. Return all exploration tests - caller will execute them in batch
     * 5. After execution, call recordExplorationResults() to update round-robin
     * 
     * @param executedTests Tests that were executed
     * @param executionResults Results from execution
     * @return ExplorationResult with all generated exploration tests (caller executes them)
     */
    public ExplorationResult exploreEfficient(
            List<MultiServiceTestCase> executedTests,
            Map<String, TestExecutionResult> executionResults) {
        
        if (!enabled || executedTests.isEmpty()) {
            return new ExplorationResult(Collections.emptyList(), tracker.getOverallCoverageSummary());
        }
        
        log.info("═══════════════════════════════════════════════════════════════════════════");
        log.info("🔬 STATUS CODE EXPLORATION (Efficient Mode)");
        log.info("═══════════════════════════════════════════════════════════════════════════");
        log.info("Processing {} executed tests for exploration", executedTests.size());
        
        // STEP 1: Run LLM Discovery for each unique API (ONCE per API)
        runDiscoveryForApis(executedTests, executionResults);
        
        // STEP 2: Update triggered status codes from execution results
        updateTriggeredStatusCodes(executionResults);
        
        // Log current coverage
        tracker.logCoverageReport();
        
        // STEP 3: For each test × each step, get exploration suggestions with reachability gating
        List<MultiServiceTestCase> allExplorationTests = new ArrayList<>();
        int totalCreated = 0;

        ConsoleProgressBar.begin("Explore Eval", executedTests.size());
        try {
        for (MultiServiceTestCase test : executedTests) {
            if (totalCreated >= maxExplorationTestsPerRound) {
                log.info("Reached max exploration tests per round ({})", maxExplorationTestsPerRound);
                break;
            }

            if (test.isStatusCodeExplorationTest()) {
                ConsoleProgressBar.update("skip explor-test");
                continue;
            }

            TestExecutionResult result = executionResults.get(test.getOperationId());
            if (result == null) {
                log.warn("No execution result for test: {}", test.getOperationId());
                ConsoleProgressBar.update("no result " + test.getOperationId());
                continue;
            }
            
            // Iterate every step in this test for per-step exploration
            for (int stepIdx = 0; stepIdx < test.getSteps().size(); stepIdx++) {
                if (totalCreated >= maxExplorationTestsPerRound) break;
                
                String apiKey = getApiKey(test, stepIdx);
                
                List<StatusCodeTarget> untriggeredCodes = tracker.getUntriggeredCodes(apiKey);
                if (untriggeredCodes.isEmpty()) {
                    log.debug("All status codes already triggered/targeted for {} (step {})", apiKey, stepIdx);
                    continue;
                }
                
                // Reachability gate: verify Steps 0..stepIdx-1 all returned 2xx
                if (stepIdx > 0 && !isStepReachable(result, stepIdx)) {
                    log.info("   ⏭ Step {} ({}) is NOT reachable in base test {} - skipping",
                        stepIdx, apiKey, test.getOperationId());
                    continue;
                }
                
                log.info("───────────────────────────────────────────────────────────────────────────");
                log.info("📋 Test: {} | Step {} (API: {})", test.getOperationId(), stepIdx, apiKey);
                log.info("   Available status codes to explore: {}", untriggeredCodes.stream()
                    .map(t -> String.valueOf(t.getStatusCode())).collect(Collectors.joining(", ")));
                
                List<ExplorationSuggestion> suggestions = evaluateExplorationCandidate(
                    test, result, untriggeredCodes, stepIdx);
                
                if (suggestions.isEmpty()) {
                    log.info("   LLM: No exploration suggested for step {}", stepIdx);
                    continue;
                }
                
                // Stamp targetStepIndex onto each suggestion
                for (ExplorationSuggestion s : suggestions) {
                    s.setTargetStepIndex(stepIdx);
                }
                
                log.info("   LLM suggested {} exploration(s): {}", suggestions.size(),
                    suggestions.stream().map(s -> String.valueOf(s.getTargetStatusCode())).collect(Collectors.joining(", ")));
                
                Map<Integer, StatusCodeTarget> targetsByCode = new HashMap<>();
                for (StatusCodeTarget target : untriggeredCodes) {
                    targetsByCode.put(target.getStatusCode(), target);
                }
                
                int createdForThisStep = 0;
                for (ExplorationSuggestion suggestion : suggestions) {
                    if (totalCreated >= maxExplorationTestsPerRound) break;
                    if (createdForThisStep >= maxExplorationTestsPerOriginal) break;
                    
                    int targetCode = suggestion.getTargetStatusCode();
                    
                    if (tracker.isTriggered(apiKey, targetCode)) {
                        continue;
                    }
                    
                    StatusCodeTarget statusCodeTarget = targetsByCode.get(targetCode);
                    
                    MultiServiceTestCase explorationTest = createExplorationTest(test, suggestion, statusCodeTarget);
                    if (explorationTest != null) {
                        allExplorationTests.add(explorationTest);
                        tracker.markTargeted(apiKey, targetCode);
                        totalCreated++;
                        createdForThisStep++;
                        
                        log.info("   ✅ Created: {} targeting status {} at step {}",
                            explorationTest.getOperationId(), targetCode, stepIdx);
                        log.info("      Parameters: {}", suggestion.parameterChanges);
                    }
                }
            }
            ConsoleProgressBar.update(test.getOperationId());
        }
        } finally {
            ConsoleProgressBar.complete();
        }

        log.info("═══════════════════════════════════════════════════════════════════════════");
        log.info("🔬 EXPLORATION GENERATION COMPLETE: Created {} exploration tests", allExplorationTests.size());
        log.info("   Next: Caller will execute these tests and call recordExplorationResults()");
        log.info("═══════════════════════════════════════════════════════════════════════════");
        
        return new ExplorationResult(allExplorationTests, tracker.getOverallCoverageSummary());
    }
    
    /**
     * Record exploration test results and update round-robin.
     * Call this AFTER executing exploration tests to update coverage.
     * 
     * @param explorationResults Map of exploration test operationId -> actual status code received
     */
    public void recordExplorationResults(Map<String, Integer> explorationResults) {
        log.info("📊 Recording {} exploration test results", explorationResults.size());
        
        for (Map.Entry<String, Integer> entry : explorationResults.entrySet()) {
            String testId = entry.getKey();
            int actualStatusCode = entry.getValue();
            
            // Extract API key and target status from test ID
            // Format: test_POST_1_1_explore_400 or similar
            String apiKey = extractApiKeyFromExplorationTestId(testId);
            int targetStatus = extractTargetStatusFromExplorationTestId(testId);
            
            if (apiKey != null && targetStatus > 0) {
                if (actualStatusCode == targetStatus) {
                    // SUCCESS: Remove from round-robin (mark as triggered)
                    tracker.markTriggered(apiKey, targetStatus);
                    log.info("   ✅ {} triggered target status {} - REMOVED from round-robin", testId, targetStatus);
                } else {
                    // FAILED: Move to end of round-robin for future retry
                    tracker.moveToEndOfRoundRobin(apiKey, targetStatus);
                    log.info("   ❌ {} got {} instead of {} - MOVED to end of round-robin", 
                        testId, actualStatusCode, targetStatus);
                }
                
                // Also record the actual status we got (might discover new codes)
                if (actualStatusCode > 0 && actualStatusCode != targetStatus) {
                    tracker.markTriggered(apiKey, actualStatusCode);
                    log.debug("   📝 Incidentally triggered status {} for {}", actualStatusCode, apiKey);
                }
            }
        }
        
        log.info("📊 Updated coverage: {}", tracker.getOverallCoverageSummary());
    }
    
    /**
     * Extract API key from exploration test ID.
     * Test ID format: test_POST_1_1_explore_400
     */
    private String extractApiKeyFromExplorationTestId(String testId) {
        // This needs to be implemented based on your test ID format
        // For now, return a placeholder - will be populated from test metadata
        return null; // Will be set by caller with actual API key
    }
    
    /**
     * Extract target status code from exploration test ID.
     * Test ID format: test_POST_1_1_explore_400
     */
    private int extractTargetStatusFromExplorationTestId(String testId) {
        try {
            // Look for _explore_XXX pattern
            int idx = testId.lastIndexOf("_explore_");
            if (idx > 0) {
                String suffix = testId.substring(idx + 9); // after "_explore_"
                // Handle retry suffix: _explore_400_retry1 -> 400
                int underscoreIdx = suffix.indexOf('_');
                if (underscoreIdx > 0) {
                    suffix = suffix.substring(0, underscoreIdx);
                }
                return Integer.parseInt(suffix);
            }
        } catch (NumberFormatException e) {
            log.debug("Could not extract target status from test ID: {}", testId);
        }
        return -1;
    }
    
    /**
     * Run LLM Discovery for each unique API across ALL steps of executed tests.
     */
    private void runDiscoveryForApis(
            List<MultiServiceTestCase> executedTests,
            Map<String, TestExecutionResult> executionResults) {
        
        // (apiKey -> stepIndex that first introduced it, sampleTest)
        Map<String, int[]> apiToStepSample = new LinkedHashMap<>();
        Map<String, MultiServiceTestCase> apiToSampleTest = new HashMap<>();
        Map<String, Set<Integer>> observedCodesPerApi = new HashMap<>();
        Map<String, List<String>> sampleResponsesPerApi = new HashMap<>();
        
        for (MultiServiceTestCase test : executedTests) {
            TestExecutionResult result = executionResults.get(test.getOperationId());
            for (int s = 0; s < test.getSteps().size(); s++) {
                String apiKey = getApiKey(test, s);
                apiToStepSample.putIfAbsent(apiKey, new int[]{s});
                apiToSampleTest.putIfAbsent(apiKey, test);
                
                if (result != null) {
                    observedCodesPerApi.computeIfAbsent(apiKey, k -> new HashSet<>());
                    sampleResponsesPerApi.computeIfAbsent(apiKey, k -> new ArrayList<>());
                    
                    // Use per-step results if available, otherwise fall back to top-level
                    if (!result.getStepResults().isEmpty() && s < result.getStepResults().size()) {
                        StepExecutionResult sr = result.getStepResults().get(s);
                        observedCodesPerApi.get(apiKey).add(sr.getStatusCode());
                        if (sr.getResponseBody() != null && sampleResponsesPerApi.get(apiKey).size() < 3) {
                            sampleResponsesPerApi.get(apiKey).add(sr.getResponseBody());
                        }
                    } else if (s == 0) {
                        observedCodesPerApi.get(apiKey).add(result.getActualStatusCode());
                        if (result.getResponseBody() != null && sampleResponsesPerApi.get(apiKey).size() < 3) {
                            sampleResponsesPerApi.get(apiKey).add(result.getResponseBody());
                        }
                    }
                }
            }
        }
        
        log.info("Running LLM Discovery for {} unique APIs (across all steps)", apiToStepSample.size());

        ConsoleProgressBar.begin("Status Discovery", apiToStepSample.size());
        try {
            for (Map.Entry<String, int[]> entry : apiToStepSample.entrySet()) {
                String apiKey = entry.getKey();
                int stepIdx = entry.getValue()[0];

                if (tracker.hasApi(apiKey)) {
                    log.debug("API {} already discovered", apiKey);
                    ConsoleProgressBar.update("cached " + apiKey);
                    continue;
                }

                MultiServiceTestCase sampleTest = apiToSampleTest.get(apiKey);
                Set<Integer> observedCodes = observedCodesPerApi.getOrDefault(apiKey, Collections.emptySet());
                List<String> sampleResponses = sampleResponsesPerApi.getOrDefault(apiKey, Collections.emptyList());

                String[] apiParts = apiKey.split(" ", 2);
                String httpMethod = apiParts[0];
                String path = apiParts.length > 1 ? apiParts[1] : "";
                String serviceName = getServiceName(sampleTest, stepIdx);

                List<StatusCodeTarget> discoveredCodes = discovery.discoverStatusCodes(
                    serviceName, httpMethod, path,
                    getParameterInfos(sampleTest, stepIdx),
                    observedCodes, sampleResponses);

                tracker.registerDiscoveredCodes(apiKey, discoveredCodes);
                ConsoleProgressBar.update(apiKey);
            }
        } finally {
            ConsoleProgressBar.complete();
        }
    }
    
    /**
     * Update triggered status codes from execution results (including per-step granularity).
     */
    private void updateTriggeredStatusCodes(Map<String, TestExecutionResult> executionResults) {
        for (TestExecutionResult result : executionResults.values()) {
            // Top-level (backward compat)
            String apiKey = result.getApiKey();
            if (apiKey != null && tracker.hasApi(apiKey)) {
                tracker.markTriggered(apiKey, result.getActualStatusCode());
            }
            // Per-step granularity
            for (StepExecutionResult sr : result.getStepResults()) {
                String stepApiKey = sr.getApiKey();
                if (stepApiKey != null && tracker.hasApi(stepApiKey) && sr.getStatusCode() > 0) {
                    tracker.markTriggered(stepApiKey, sr.getStatusCode());
                }
            }
        }
    }
    
    /**
     * Check whether all steps before targetStepIndex returned 2xx in the base test execution.
     */
    private boolean isStepReachable(TestExecutionResult result, int targetStepIndex) {
        List<StepExecutionResult> stepResults = result.getStepResults();
        if (stepResults.isEmpty()) {
            // No per-step data available; fall back to top-level result for step 0 only
            return targetStepIndex == 0 ||
                   (result.getActualStatusCode() >= 200 && result.getActualStatusCode() < 300);
        }
        for (int i = 0; i < targetStepIndex && i < stepResults.size(); i++) {
            int code = stepResults.get(i).getStatusCode();
            if (code < 200 || code >= 300) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Legacy overload for backward compatibility (targets step 0).
     */
    private List<ExplorationSuggestion> evaluateExplorationCandidate(
            MultiServiceTestCase test,
            TestExecutionResult result,
            List<StatusCodeTarget> untriggeredCodes) {
        return evaluateExplorationCandidate(test, result, untriggeredCodes, 0);
    }
    
    /**
     * Ask LLM if this test is a good candidate for status code exploration on the given step.
     *
     * <p>Signature-keyed by (per-step method+normalized-path, target step idx,
     * sorted untriggered status codes, sorted parameter schema). On a hit the
     * cached raw LLM response is re-parsed — concrete invalid values vary per
     * variant but the suggestion strategy is endpoint-property not invocation-
     * property, so caching is safe.
     */
    private List<ExplorationSuggestion> evaluateExplorationCandidate(
            MultiServiceTestCase test,
            TestExecutionResult result,
            List<StatusCodeTarget> untriggeredCodes,
            int targetStepIndex) {

        String cacheKey = buildExplorationCacheKey(test, untriggeredCodes, targetStepIndex);

        if (CacheToggle.canRead()) {
            String cached = evaluationCache.get(cacheKey);
            if (cached != null) {
                log.debug("Exploration suggest cache HIT: {} step {}", test.getOperationId(), targetStepIndex);
                return parseExplorationResponse(cached);
            }
        }

        String systemPrompt = buildExplorationSystemPrompt();
        String userPrompt = buildExplorationUserPrompt(test, result, untriggeredCodes, targetStepIndex);

        log.debug("Evaluating exploration candidate: {} step {}", test.getOperationId(), targetStepIndex);

        String llmResponse = llmService.generateText(systemPrompt, userPrompt, maxTokens, temperature);

        if (llmResponse == null || llmResponse.trim().isEmpty()) {
            log.warn("LLM returned empty response for exploration evaluation");
            return Collections.emptyList();
        }

        if (CacheToggle.canWrite()) {
            evaluationCache.put(cacheKey, llmResponse);
            saveCacheToDisk();
        }

        return parseExplorationResponse(llmResponse);
    }

    /**
     * Build the signature-based cache key. Includes only fields that determine
     * the LLM's strategy:
     * <ul>
     *   <li>Method + normalized path for every step up to and including the
     *       target (workflow context dictates which params can be modified).</li>
     *   <li>Target step index (the LLM's job is "modify THIS step's params").</li>
     *   <li>Sorted untriggered status code set (the goal of the suggestion).</li>
     *   <li>Sorted parameter schema (name|type|location|required) for the
     *       target step. Concrete parameter VALUES are excluded — they vary
     *       per variant but don't change the suggestion strategy.</li>
     * </ul>
     */
    private String buildExplorationCacheKey(MultiServiceTestCase test,
                                            List<StatusCodeTarget> untriggeredCodes,
                                            int targetStepIndex) {
        StringBuilder sb = new StringBuilder();
        // workflow steps up to target
        int upper = Math.min(targetStepIndex + 1, test.getSteps().size());
        for (int s = 0; s < upper; s++) {
            MultiServiceTestCase.StepCall step = test.getSteps().get(s);
            String m = step.getMethod() != null ? step.getMethod().getMethod().toUpperCase() : "GET";
            sb.append(m).append(' ')
              .append(LLMStatusCodeDiscovery.normalizePath(step.getPath()))
              .append('|');
        }
        sb.append("target=").append(targetStepIndex).append('|');
        // sorted untriggered status codes
        List<Integer> codes = new ArrayList<>();
        if (untriggeredCodes != null) {
            for (StatusCodeTarget t : untriggeredCodes) codes.add(t.getStatusCode());
        }
        Collections.sort(codes);
        sb.append("untrig=").append(codes).append('|');
        // sorted param-schema of target step
        if (targetStepIndex < test.getSteps().size()) {
            MultiServiceTestCase.StepCall targetStep = test.getSteps().get(targetStepIndex);
            List<String> rows = new ArrayList<>();
            for (String name : targetStep.getPathParams().keySet())
                rows.add(name + "|string|path|false");
            for (String name : targetStep.getQueryParams().keySet())
                rows.add(name + "|string|query|false");
            for (String name : targetStep.getBodyFields().keySet())
                rows.add(name + "|string|body|false");
            Collections.sort(rows);
            sb.append("params=").append(String.join(",", rows));
        }
        return sb.toString();
    }

    /** Load the persistent suggest cache file. Cold start / corrupt file logs warn. */
    private void loadCacheFromDisk() {
        if (cachePersistPath == null) return;
        if (!Files.exists(cachePersistPath)) {
            log.info("ExplorationEnhancer suggest cache: cold start (no file at {})", cachePersistPath);
            return;
        }
        try {
            String content = new String(Files.readAllBytes(cachePersistPath), StandardCharsets.UTF_8);
            if (content.trim().isEmpty()) return;
            JSONObject obj = new JSONObject(content);
            for (String key : obj.keySet()) {
                evaluationCache.put(key, obj.optString(key, ""));
            }
            log.info("ExplorationEnhancer suggest cache: loaded {} entries from {}",
                    evaluationCache.size(), cachePersistPath);
        } catch (Exception e) {
            log.warn("ExplorationEnhancer suggest cache: failed to load {}: {}",
                    cachePersistPath, e.getMessage());
        }
    }

    /** Atomic temp+rename save. Falls back to plain replace on filesystems
     *  (e.g. tmpfs overlays) that don't support ATOMIC_MOVE. */
    private void saveCacheToDisk() {
        if (cachePersistPath == null) return;
        synchronized (cacheDiskLock) {
            try {
                JSONObject obj = new JSONObject();
                for (Map.Entry<String, String> e : evaluationCache.entrySet()) {
                    obj.put(e.getKey(), e.getValue());
                }
                Path parent = cachePersistPath.getParent();
                if (parent != null) Files.createDirectories(parent);
                Path tmp = cachePersistPath.resolveSibling(cachePersistPath.getFileName().toString() + ".tmp");
                Files.write(tmp, obj.toString(2).getBytes(StandardCharsets.UTF_8));
                try {
                    Files.move(tmp, cachePersistPath,
                            StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (java.nio.file.AtomicMoveNotSupportedException atomicEx) {
                    Files.move(tmp, cachePersistPath, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException ioe) {
                log.warn("ExplorationEnhancer suggest cache: save failed: {}", ioe.getMessage());
            }
        }
    }
    
    private String buildExplorationSystemPrompt() {
        return "You are an API testing expert specializing in HTTP status code coverage.\n" +
               "Your task is to generate exploration test cases that trigger specific HTTP status codes.\n\n" +
               "You will receive:\n" +
               "1. API operation details (method, path, service)\n" +
               "2. Current test parameters and execution result\n" +
               "3. A prioritized list of UNTRIGGERED status codes with suggested inputs\n\n" +
               "Your job:\n" +
               "- Select which status codes can realistically be triggered by modifying parameters\n" +
               "- Provide EXACT parameter values to trigger each selected status code\n" +
               "- Use the suggested inputs from discovery as a starting point\n" +
               "- Be practical - only suggest codes achievable via parameter changes\n\n" +
               "IMPORTANT: Respond with valid JSON only. No markdown, no explanations outside JSON.";
    }
    
    /**
     * Legacy overload for backward compatibility (targets step 0).
     */
    private String buildExplorationUserPrompt(
            MultiServiceTestCase test,
            TestExecutionResult result,
            List<StatusCodeTarget> untriggeredCodes) {
        return buildExplorationUserPrompt(test, result, untriggeredCodes, 0);
    }
    
    private String buildExplorationUserPrompt(
            MultiServiceTestCase test,
            TestExecutionResult result,
            List<StatusCodeTarget> untriggeredCodes,
            int targetStepIndex) {
        
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("GENERATE EXPLORATION TEST CASES FOR STATUS CODE COVERAGE\n\n");
        
        // Multi-step sequence context: show ALL steps up to and including target
        boolean isMultiStep = test.getSteps().size() > 1;
        
        if (isMultiStep) {
            prompt.append("=== WORKFLOW SEQUENCE CONTEXT ===\n");
            for (int s = 0; s <= targetStepIndex && s < test.getSteps().size(); s++) {
                MultiServiceTestCase.StepCall step = test.getSteps().get(s);
                String tag = (s == targetStepIndex) ? " ← TARGET" : " (locked)";
                prompt.append("Step ").append(s).append(": ")
                      .append(step.getMethod()).append(" ").append(step.getPath())
                      .append(tag).append("\n");
            }
            prompt.append("\n");
            
            prompt.append("⚠️ THIS IS A MULTI-STEP WORKFLOW. You are targeting Step ")
                  .append(targetStepIndex).append(". Do NOT modify parameters for Steps 0 to ")
                  .append(targetStepIndex - 1)
                  .append(", as they must remain valid to reach the target.\n\n");
        }
        
        // Target step API information
        prompt.append("=== TARGET API OPERATION (Step ").append(targetStepIndex).append(") ===\n");
        if (targetStepIndex < test.getSteps().size()) {
            MultiServiceTestCase.StepCall targetStep = test.getSteps().get(targetStepIndex);
            prompt.append("Service: ").append(targetStep.getServiceName()).append("\n");
            prompt.append("Method: ").append(targetStep.getMethod()).append("\n");
            prompt.append("Path: ").append(targetStep.getPath()).append("\n");
        } else {
            prompt.append("API: ").append(getApiKey(test, targetStepIndex)).append("\n");
        }
        prompt.append("\n");
        
        // Target step parameters
        prompt.append("=== CURRENT TEST PARAMETERS (Step ").append(targetStepIndex).append(") ===\n");
        prompt.append("Test ID: ").append(test.getOperationId()).append("\n");
        prompt.append("Test Type: ").append(test.getFaulty() ? "NEGATIVE (invalid inputs)" : "POSITIVE (valid inputs)").append("\n");
        if (targetStepIndex < test.getSteps().size()) {
            MultiServiceTestCase.StepCall targetStep = test.getSteps().get(targetStepIndex);
            if (!targetStep.getPathParams().isEmpty()) {
                prompt.append("Path Parameters: ").append(targetStep.getPathParams()).append("\n");
            }
            if (!targetStep.getQueryParams().isEmpty()) {
                prompt.append("Query Parameters: ").append(targetStep.getQueryParams()).append("\n");
            }
            if (targetStep.getBody() != null && !targetStep.getBody().isEmpty()) {
                prompt.append("Request Body: ").append(truncate(targetStep.getBody(), 500)).append("\n");
            }
            if (!targetStep.getBodyFields().isEmpty()) {
                prompt.append("Body Fields: ").append(targetStep.getBodyFields()).append("\n");
            }
            
            // Dependency protection: list dynamically injected parameters
            if (!targetStep.getParamDependencies().isEmpty()) {
                prompt.append("\n🚫 DYNAMICALLY INJECTED PARAMETERS (DO NOT MODIFY THESE):\n");
                for (String depParam : targetStep.getParamDependencies().keySet()) {
                    MultiServiceTestCase.Dependency dep = targetStep.getParamDependencies().get(depParam);
                    prompt.append("  - ").append(depParam)
                          .append(" (injected from Step ").append(dep.sourceStepIndex)
                          .append(", field: ").append(dep.sourceOutputKey).append(")\n");
                }
            }
        }
        prompt.append("\n");
        
        // Execution result for the target step
        prompt.append("=== LAST EXECUTION RESULT ===\n");
        if (!result.getStepResults().isEmpty() && targetStepIndex < result.getStepResults().size()) {
            StepExecutionResult sr = result.getStepResults().get(targetStepIndex);
            prompt.append("Actual Status Code (Step ").append(targetStepIndex).append("): ").append(sr.getStatusCode()).append("\n");
            if (sr.getResponseBody() != null && !sr.getResponseBody().isEmpty()) {
                prompt.append("Response: ").append(truncate(sr.getResponseBody(), 400)).append("\n");
            }
        } else {
            prompt.append("Actual Status Code: ").append(result.getActualStatusCode()).append("\n");
            if (result.getResponseBody() != null && !result.getResponseBody().isEmpty()) {
                prompt.append("Response: ").append(truncate(result.getResponseBody(), 400)).append("\n");
            }
        }
        prompt.append("\n");
        
        // Round-robin list with priorities and suggested inputs
        prompt.append("=== AVAILABLE STATUS CODES TO EXPLORE (Priority Order) ===\n");
        prompt.append("These status codes have NOT been triggered yet. They are listed in priority order.\n");
        prompt.append("You can generate exploration tests for ANY of these (suggest multiple if possible).\n\n");
        
        int priority = 1;
        for (StatusCodeTarget target : untriggeredCodes) {
            prompt.append(priority++).append(". Status ").append(target.getStatusCode())
                  .append(" - ").append(target.getCategory()).append("\n");
            prompt.append("   Description: ").append(target.getDescription()).append("\n");
            prompt.append("   Trigger Strategy: ").append(target.getTriggerStrategy()).append("\n");
            if (target.isRequiresAuthManipulation()) {
                prompt.append("   ⚠️ Requires Auth Manipulation: YES\n");
            }
            if (!target.getSuggestedInputs().isEmpty()) {
                prompt.append("   📝 Suggested Inputs: ").append(target.getSuggestedInputs()).append("\n");
            }
            prompt.append("\n");
        }
        
        // Task
        prompt.append("=== YOUR TASK ===\n");
        prompt.append("1. Review the available status codes above\n");
        prompt.append("2. For EACH status code you think can be triggered, provide:\n");
        prompt.append("   - The target status code\n");
        prompt.append("   - Your strategy to trigger it\n");
        prompt.append("   - The EXACT parameter changes for Step ").append(targetStepIndex)
              .append(" ONLY (use the suggested inputs as starting point)\n");
        prompt.append("3. You can suggest MULTIPLE status codes (recommended: 2-5 per test)\n");
        prompt.append("4. Only suggest codes that are REALISTICALLY achievable\n");
        if (isMultiStep) {
            prompt.append("5. Do NOT suggest changes to dynamically injected parameters listed above\n");
        }
        prompt.append("\n");
        
        // Response format
        prompt.append("=== RESPONSE FORMAT (JSON ONLY) ===\n");
        prompt.append("{\n");
        prompt.append("  \"isGoodCandidate\": true,\n");
        prompt.append("  \"reason\": \"Brief explanation of why this test can trigger these codes\",\n");
        prompt.append("  \"explorations\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"targetStatusCode\": 400,\n");
        prompt.append("      \"strategy\": \"Send malformed request body\",\n");
        prompt.append("      \"parameterChanges\": {\"<bodyField>\": \"\", \"<otherField>\": \"invalid\"},\n");
        prompt.append("      \"requiresAuthManipulation\": false\n");
        prompt.append("    },\n");
        prompt.append("    {\n");
        prompt.append("      \"targetStatusCode\": 404,\n");
        prompt.append("      \"strategy\": \"Request non-existent resource\",\n");
        prompt.append("      \"parameterChanges\": {\"<pathParam>\": \"NONEXISTENT_999\"},\n");
        prompt.append("      \"requiresAuthManipulation\": false\n");
        prompt.append("    }\n");
        prompt.append("  ]\n");
        prompt.append("}\n\n");
        
        prompt.append("If this test is NOT a good candidate, respond:\n");
        prompt.append("{\"isGoodCandidate\": false, \"reason\": \"explanation\", \"explorations\": []}\n");
        
        return prompt.toString();
    }
    
    /**
     * Parse the LLM exploration response into suggestions.
     */
    private List<ExplorationSuggestion> parseExplorationResponse(String llmResponse) {
        List<ExplorationSuggestion> suggestions = new ArrayList<>();
        
        try {
            // Extract JSON from response
            String jsonStr = extractJson(llmResponse);
            if (jsonStr == null) {
                log.warn("Could not extract JSON from LLM exploration response");
                return suggestions;
            }
            
            JSONObject json = new JSONObject(jsonStr);
            
            boolean isGoodCandidate = json.optBoolean("isGoodCandidate", false);
            String reason = json.optString("reason", "");
            
            if (!isGoodCandidate) {
                log.debug("LLM says test is not a good candidate: {}", reason);
                return suggestions;
            }
            
            log.debug("LLM says test IS a good candidate: {}", reason);
            
            JSONArray explorations = json.optJSONArray("explorations");
            if (explorations == null) {
                return suggestions;
            }
            
            for (int i = 0; i < explorations.length(); i++) {
                try {
                    JSONObject exp = explorations.getJSONObject(i);
                    ExplorationSuggestion suggestion = new ExplorationSuggestion();
                    
                    suggestion.targetStatusCode = exp.getInt("targetStatusCode");
                    suggestion.strategy = exp.optString("strategy", "");
                    suggestion.requiresAuthManipulation = exp.optBoolean("requiresAuthManipulation", false);
                    
                    JSONObject params = exp.optJSONObject("parameterChanges");
                    if (params != null) {
                        for (String key : params.keySet()) {
                            suggestion.parameterChanges.put(key, params.optString(key, ""));
                        }
                    }
                    
                    suggestions.add(suggestion);
                    log.debug("Parsed exploration suggestion: {}", suggestion);
                    
                } catch (Exception e) {
                    log.warn("Failed to parse exploration suggestion {}: {}", i, e.getMessage());
                }
            }
            
        } catch (Exception e) {
            log.error("Failed to parse LLM exploration response: {}", e.getMessage());
        }
        
        return suggestions;
    }
    
    /**
     * Create a new exploration test from the original test and suggestion.
     * @param original The original test case to clone
     * @param suggestion The exploration suggestion from LLM
     * @param statusCodeTarget The StatusCodeTarget from discovery (contains suggestedInputs as fallback)
     */
    private MultiServiceTestCase createExplorationTest(
            MultiServiceTestCase original,
            ExplorationSuggestion suggestion,
            StatusCodeTarget statusCodeTarget) {
        
        try {
            // Clone the original test
            MultiServiceTestCase exploration = cloneTestCase(original);
            
            // Update name
            String newName = original.getOperationId() + "_explore_" + suggestion.targetStatusCode;
            exploration.setOperationId(newName);
            
            // Mark as exploration test
            exploration.setStatusCodeExplorationTest(true);
            exploration.setTargetStatusCode(suggestion.targetStatusCode);
            
            // Determine which parameter changes to use:
            // 1. First preference: parameterChanges from LLM exploration response
            // 2. Fallback: suggestedInputs from StatusCodeTarget (from discovery phase)
            Map<String, String> paramChanges = new HashMap<>();
            if (!suggestion.parameterChanges.isEmpty()) {
                paramChanges.putAll(suggestion.parameterChanges);
                log.debug("Using {} parameter changes from LLM exploration response", paramChanges.size());
            } else if (statusCodeTarget != null && !statusCodeTarget.getSuggestedInputs().isEmpty()) {
                paramChanges.putAll(statusCodeTarget.getSuggestedInputs());
                log.info("Using {} suggested inputs from discovery phase for status {}", 
                    paramChanges.size(), suggestion.targetStatusCode);
            }
            
            // Apply parameter changes to the TARGET step (not hardcoded step 0)
            int targetIdx = suggestion.getTargetStepIndex();
            if (targetIdx < exploration.getSteps().size() && !paramChanges.isEmpty()) {
                MultiServiceTestCase.StepCall originalStep = exploration.getSteps().get(targetIdx);
                
                log.info("Applying parameter changes for status {} exploration at step {}: {}", 
                    suggestion.targetStatusCode, targetIdx, paramChanges);
                
                // Collect dependency-protected parameter names for safety filter
                Set<String> protectedParams = originalStep.getParamDependencies() != null
                        ? originalStep.getParamDependencies().keySet()
                        : Collections.emptySet();
                
                Map<String, String> newPathParams = new LinkedHashMap<>(originalStep.getPathParams());
                Map<String, String> newQueryParams = new LinkedHashMap<>(originalStep.getQueryParams());
                Map<String, String> newBodyFields = new LinkedHashMap<>(originalStep.getBodyFields());
                
                for (Map.Entry<String, String> change : paramChanges.entrySet()) {
                    String paramName = change.getKey();
                    String paramValue = change.getValue();
                    
                    // Safety filter: never overwrite a dynamically injected parameter
                    if (protectedParams.contains(paramName)) {
                        log.info("   🛡️ Skipping protected parameter '{}' (data dependency)", paramName);
                        continue;
                    }
                    
                    boolean applied = false;
                    
                    if (newPathParams.containsKey(paramName)) {
                        newPathParams.put(paramName, paramValue);
                        log.debug("Applied {} = {} to path params", paramName, paramValue);
                        applied = true;
                    }
                    
                    if (newQueryParams.containsKey(paramName)) {
                        newQueryParams.put(paramName, paramValue);
                        log.debug("Applied {} = {} to query params", paramName, paramValue);
                        applied = true;
                    }
                    
                    if (newBodyFields.containsKey(paramName)) {
                        newBodyFields.put(paramName, paramValue);
                        log.debug("Applied {} = {} to body fields", paramName, paramValue);
                        applied = true;
                    }
                    
                    if (!applied) {
                        String method = originalStep.getMethod().toString().toUpperCase();
                        if (method.equals("POST") || method.equals("PUT") || method.equals("PATCH")) {
                            newBodyFields.put(paramName, paramValue);
                            log.debug("Added {} = {} to body fields (new parameter for {})", 
                                paramName, paramValue, method);
                        } else {
                            newQueryParams.put(paramName, paramValue);
                            log.debug("Added {} = {} to query params (new parameter)", paramName, paramValue);
                        }
                    }
                }
                
                String newBody = originalStep.getBody();
                if (!newBodyFields.isEmpty()) {
                    JSONObject bodyJson = new JSONObject(newBodyFields);
                    newBody = bodyJson.toString();
                    log.info("Rebuilt request body for exploration: {}", newBody);
                }
                
                MultiServiceTestCase.StepCall modifiedStep = new MultiServiceTestCase.StepCall(
                    originalStep.getServiceName(),
                    originalStep.getMethod(),
                    originalStep.getPath(),
                    newPathParams,
                    newQueryParams,
                    new LinkedHashMap<>(originalStep.getHeaders()),
                    newBody,
                    originalStep.getExpectedStatus(),
                    newBodyFields
                );
                
                for (String key : originalStep.getCaptureOutputKeys()) {
                    modifiedStep.addCaptureOutputKey(key);
                }
                
                // Retain flow metadata on the modified step as well
                for (Map.Entry<String, MultiServiceTestCase.Dependency> dep : originalStep.getParamDependencies().entrySet()) {
                    modifiedStep.addParamDependency(dep.getKey(), dep.getValue().sourceStepIndex, dep.getValue().sourceOutputKey);
                }
                for (Integer wdep : originalStep.getWorkflowDependencies()) {
                    modifiedStep.addWorkflowDependency(wdep);
                }
                modifiedStep.setDependencyType(originalStep.getDependencyType());
                modifiedStep.setHierarchicalId(originalStep.getHierarchicalId());
                modifiedStep.setTopLevelRoot(originalStep.isTopLevelRoot());
                modifiedStep.setMergedRootStep(originalStep.isMergedRootStep());
                modifiedStep.setProducerRootIndex(originalStep.getProducerRootIndex());
                for (Map.Entry<String, String> prov : originalStep.getProvenanceBindings().entrySet()) {
                    modifiedStep.addProvenanceBinding(prov.getKey(), prov.getValue());
                }
                
                exploration.getSteps().set(targetIdx, modifiedStep);
                
                log.info("Created modified step {} with new body: {}", targetIdx, newBody);
            } else if (paramChanges.isEmpty()) {
                log.warn("No parameter changes available for status {} exploration at step {} - test may not trigger expected status", 
                    suggestion.targetStatusCode, targetIdx);
            }
            
            // Handle auth manipulation if needed
            if (suggestion.requiresAuthManipulation) {
                StatusCodeTarget target = new StatusCodeTarget.Builder(suggestion.targetStatusCode)
                    .triggerStrategy(suggestion.strategy)
                    .requiresAuthManipulation(true)
                    .build();
                
                AuthManipulationStrategy.AuthConfig authConfig = 
                    authStrategy.getManipulatedAuth(target, null);
                exploration.setAuthManipulation(authConfig);
            }
            
            return exploration;
            
        } catch (Exception e) {
            log.error("Failed to create exploration test: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Clone a MultiServiceTestCase for modification, preserving all flow metadata.
     */
    private MultiServiceTestCase cloneTestCase(MultiServiceTestCase original) {
        MultiServiceTestCase clone = new MultiServiceTestCase(original.getOperationId());
        clone.setScenarioName(original.getScenarioName());
        clone.setFaulty(original.getFaulty());
        
        for (MultiServiceTestCase.StepCall originalStep : original.getSteps()) {
            MultiServiceTestCase.StepCall clonedStep = new MultiServiceTestCase.StepCall(
                originalStep.getServiceName(),
                originalStep.getMethod(),
                originalStep.getPath(),
                new LinkedHashMap<>(originalStep.getPathParams()),
                new LinkedHashMap<>(originalStep.getQueryParams()),
                new LinkedHashMap<>(originalStep.getHeaders()),
                originalStep.getBody(),
                originalStep.getExpectedStatus(),
                new LinkedHashMap<>(originalStep.getBodyFields())
            );
            
            for (String key : originalStep.getCaptureOutputKeys()) {
                clonedStep.addCaptureOutputKey(key);
            }
            
            // Retain all 7 flow metadata fields dropped by the StepCall constructor
            for (Map.Entry<String, MultiServiceTestCase.Dependency> dep : originalStep.getParamDependencies().entrySet()) {
                clonedStep.addParamDependency(dep.getKey(), dep.getValue().sourceStepIndex, dep.getValue().sourceOutputKey);
            }
            for (Integer wdep : originalStep.getWorkflowDependencies()) {
                clonedStep.addWorkflowDependency(wdep);
            }
            clonedStep.setDependencyType(originalStep.getDependencyType());
            clonedStep.setHierarchicalId(originalStep.getHierarchicalId());
            clonedStep.setTopLevelRoot(originalStep.isTopLevelRoot());
            clonedStep.setMergedRootStep(originalStep.isMergedRootStep());
            clonedStep.setProducerRootIndex(originalStep.getProducerRootIndex());
            for (Map.Entry<String, String> prov : originalStep.getProvenanceBindings().entrySet()) {
                clonedStep.addProvenanceBinding(prov.getKey(), prov.getValue());
            }
            
            clone.addStepCall(clonedStep);
        }
        
        for (String faultyParam : original.getFaultyParameters()) {
            String[] parts = faultyParam.split("=", 2);
            if (parts.length == 2) {
                clone.addFaultyParameter(parts[0], parts[1]);
            }
        }
        
        return clone;
    }
    
    // Utility methods
    
    private String getApiKey(MultiServiceTestCase test) {
        return getApiKey(test, 0);
    }
    
    private String getApiKey(MultiServiceTestCase test, int stepIndex) {
        if (stepIndex >= test.getSteps().size()) return "UNKNOWN";
        MultiServiceTestCase.StepCall step = test.getSteps().get(stepIndex);
        String method = step.getMethod() != null ? step.getMethod().getMethod().toUpperCase() : "GET";
        return method + " " + step.getPath();
    }
    
    private String getServiceName(MultiServiceTestCase test, int stepIndex) {
        if (stepIndex >= test.getSteps().size()) return "unknown";
        return test.getSteps().get(stepIndex).getServiceName();
    }
    
    private List<ParameterInfo> getParameterInfos(MultiServiceTestCase test, int stepIndex) {
        List<ParameterInfo> params = new ArrayList<>();
        if (stepIndex >= test.getSteps().size()) return params;
        
        MultiServiceTestCase.StepCall step = test.getSteps().get(stepIndex);
        
        for (String key : step.getPathParams().keySet()) {
            ParameterInfo info = new ParameterInfo();
            info.setName(key);
            info.setInLocation("path");
            info.setType("string");
            params.add(info);
        }
        
        for (String key : step.getQueryParams().keySet()) {
            ParameterInfo info = new ParameterInfo();
            info.setName(key);
            info.setInLocation("query");
            info.setType("string");
            params.add(info);
        }
        
        return params;
    }
    
    private String extractJson(String response) {
        if (response == null) return null;
        
        // Remove markdown code blocks
        String cleaned = response.trim();
        if (cleaned.contains("```json")) {
            int start = cleaned.indexOf("```json") + 7;
            int end = cleaned.indexOf("```", start);
            if (end > start) {
                cleaned = cleaned.substring(start, end).trim();
            }
        } else if (cleaned.contains("```")) {
            int start = cleaned.indexOf("```") + 3;
            int end = cleaned.indexOf("```", start);
            if (end > start) {
                cleaned = cleaned.substring(start, end).trim();
            }
        }
        
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        
        if (start >= 0 && end > start) {
            return cleaned.substring(start, end + 1);
        }
        
        return null;
    }
    
    private String truncate(String str, int maxLen) {
        if (str == null) return "null";
        if (str.length() <= maxLen) return str;
        return str.substring(0, maxLen) + "...";
    }
    
    // Configuration setters
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public void setMaxExplorationTestsPerOriginal(int max) {
        this.maxExplorationTestsPerOriginal = max;
    }
    
    public void setMaxExplorationTestsPerRound(int max) {
        this.maxExplorationTestsPerRound = max;
    }
    
    public StatusCodeCoverageTracker getTracker() {
        return tracker;
    }
    
    /**
     * Start a new round (called between enhancer rounds).
     */
    public void startNewRound() {
        tracker.startNewRound();
    }
    
    // Inner classes
    
    /**
     * Suggestion for exploring a specific status code on a specific step.
     */
    public static class ExplorationSuggestion {
        private int targetStatusCode;
        private String strategy = "";
        private Map<String, String> parameterChanges = new HashMap<>();
        private boolean requiresAuthManipulation = false;
        private int targetStepIndex = 0;
        
        public int getTargetStatusCode() { return targetStatusCode; }
        public String getStrategy() { return strategy; }
        public Map<String, String> getParameterChanges() { return parameterChanges; }
        public boolean isRequiresAuthManipulation() { return requiresAuthManipulation; }
        public int getTargetStepIndex() { return targetStepIndex; }
        public void setTargetStepIndex(int targetStepIndex) { this.targetStepIndex = targetStepIndex; }
        
        @Override
        public String toString() {
            return String.format("ExplorationSuggestion{status=%d, step=%d, params=%s, authManip=%s}",
                targetStatusCode, targetStepIndex, parameterChanges, requiresAuthManipulation);
        }
    }
    
    /**
     * Result of exploration for a single test execution round.
     */
    public static class ExplorationResult {
        private final List<MultiServiceTestCase> explorationTests;
        private final StatusCodeCoverageTracker.OverallCoverageSummary coverageSummary;
        
        public ExplorationResult(List<MultiServiceTestCase> explorationTests,
                                 StatusCodeCoverageTracker.OverallCoverageSummary coverageSummary) {
            this.explorationTests = explorationTests;
            this.coverageSummary = coverageSummary;
        }
        
        public List<MultiServiceTestCase> getExplorationTests() { return explorationTests; }
        public StatusCodeCoverageTracker.OverallCoverageSummary getCoverageSummary() { return coverageSummary; }
        
        @Override
        public String toString() {
            return String.format("ExplorationResult{tests=%d, %s}", 
                explorationTests.size(), coverageSummary);
        }
    }
    
    /**
     * Execution result for a test case, with optional per-step granularity.
     */
    public static class TestExecutionResult {
        private String testName;
        private String apiKey;
        private int actualStatusCode;
        private String responseBody;
        private boolean passed;
        private String errorMessage;
        private boolean softErrorDetected;
        private List<StepExecutionResult> stepResults = new ArrayList<>();
        
        public TestExecutionResult() {}
        
        public TestExecutionResult(String testName, String apiKey, int statusCode, 
                                   String responseBody, boolean passed) {
            this.testName = testName;
            this.apiKey = apiKey;
            this.actualStatusCode = statusCode;
            this.responseBody = responseBody;
            this.passed = passed;
        }
        
        public String getTestName() { return testName; }
        public void setTestName(String testName) { this.testName = testName; }
        
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        
        public int getActualStatusCode() { return actualStatusCode; }
        public void setActualStatusCode(int actualStatusCode) { this.actualStatusCode = actualStatusCode; }
        
        public String getResponseBody() { return responseBody; }
        public void setResponseBody(String responseBody) { this.responseBody = responseBody; }
        
        public boolean isPassed() { return passed; }
        public void setPassed(boolean passed) { this.passed = passed; }
        
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        
        public boolean isSoftErrorDetected() { return softErrorDetected; }
        public void setSoftErrorDetected(boolean softErrorDetected) { this.softErrorDetected = softErrorDetected; }
        
        public List<StepExecutionResult> getStepResults() { return stepResults; }
        public void setStepResults(List<StepExecutionResult> stepResults) { this.stepResults = stepResults; }
        public void addStepResult(StepExecutionResult sr) { this.stepResults.add(sr); }
    }
    
    /**
     * Per-step execution result within a multi-root test case.
     */
    public static class StepExecutionResult {
        private int stepIndex;
        private String apiKey;
        private int statusCode;
        private String responseBody;
        private boolean success;
        
        public StepExecutionResult() {}
        
        public StepExecutionResult(int stepIndex, String apiKey, int statusCode,
                                   String responseBody, boolean success) {
            this.stepIndex = stepIndex;
            this.apiKey = apiKey;
            this.statusCode = statusCode;
            this.responseBody = responseBody;
            this.success = success;
        }
        
        public int getStepIndex() { return stepIndex; }
        public void setStepIndex(int stepIndex) { this.stepIndex = stepIndex; }
        
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        
        public int getStatusCode() { return statusCode; }
        public void setStatusCode(int statusCode) { this.statusCode = statusCode; }
        
        public String getResponseBody() { return responseBody; }
        public void setResponseBody(String responseBody) { this.responseBody = responseBody; }
        
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
    }
}
