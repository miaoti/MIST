package io.mist.core.enhancer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.mist.core.enhancer.FailedTestResult;
import io.mist.core.enhancer.ParameterSnapshot;
import io.mist.llm.LLMService;
import io.mist.core.util.ConsoleProgressBar;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

/**
 * Test Case Enhancer that uses LLM to improve failed test inputs.
 * 
 * This class analyzes failed test cases, sends their context to the LLM,
 * and receives improved parameter values that might help the test pass.
 */
public class TestCaseEnhancer {
    
    private static final Logger log = LogManager.getLogger(TestCaseEnhancer.class);
    // The LLM commonly emits JSON decorated with // comments, trailing commas, and
    // single-quoted keys/values. We turn on the corresponding Jackson tolerances so a
    // helpful-but-non-strict response does not abort an entire enhancement round
    // (12 such failures observed in a single run when these were strict).
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .configure(JsonParser.Feature.ALLOW_COMMENTS, true)
            .configure(JsonParser.Feature.ALLOW_TRAILING_COMMA, true)
            .configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true)
            .configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
    
    private final LLMService llmService;
    private final int maxTokens;
    private final double temperature;
    
    // Cache of enhanced parameters by test key. Concurrent because enhanceBatch
    // now distributes results from a parallel executor.
    private final Map<String, Map<String, String>> enhancedParametersCache = new ConcurrentHashMap<>();
    
    public TestCaseEnhancer(LLMService llmService) {
        this(llmService, 500, 0.7);
    }
    
    public TestCaseEnhancer(LLMService llmService, int maxTokens, double temperature) {
        this.llmService = llmService;
        this.maxTokens = maxTokens;
        this.temperature = temperature;
    }
    
    /**
     * Enhance a single failed test case.
     * 
     * @param failedTest The failed test result with full context
     * @return EnhancementResult containing new parameter values and reasoning
     */
    public EnhancementResult enhance(FailedTestResult failedTest) {
        log.info("🔧 Enhancing test: {} (status: {}, params: {})", 
                failedTest.getTestMethodName(), 
                failedTest.getActualStatusCode(),
                failedTest.getParameters().size());
        
        String systemPrompt = buildSystemPrompt();
        String userPrompt = buildUserPrompt(failedTest);
        
        log.debug("📝 LLM Enhancement Prompt:\n{}", userPrompt);
        
        String llmResponse = llmService.generateText(systemPrompt, userPrompt, maxTokens, temperature);
        
        if (llmResponse == null || llmResponse.trim().isEmpty()) {
            log.warn("⚠️  LLM returned empty response for test: {}", failedTest.getTestMethodName());
            return EnhancementResult.failed("LLM returned empty response");
        }
        
        log.debug("🤖 LLM Enhancement Response:\n{}", llmResponse);
        
        return parseEnhancementResponse(llmResponse, failedTest);
    }
    
    /**
     * Enhance multiple failed tests in batch.
     *
     * <p>The implementation has three stages:
     * <ol>
     *   <li><b>Group</b> by canonical scenario+fault+error fingerprint
     *       (negative tests only, when {@code mst.enhancer.dedup.negative=true});
     *       positive tests stay unique per test method name so their per-variant
     *       diversity is preserved.</li>
     *   <li><b>Resolve</b> each group via the persistent {@link EnhancementCache}
     *       (when {@code mst.enhancer.cache.enabled=true}); cache misses queue
     *       for a real LLM call, dispatched through a bounded thread pool
     *       (size = {@code mst.enhancer.parallelism}, default 8) with a
     *       {@link Semaphore} guard so concurrent calls don't exceed the
     *       provider's rate limit window.</li>
     *   <li><b>Distribute</b> the group result back to every member, rehydrating
     *       per-test identity (testClassName, testMethodName) so downstream
     *       {@code TestFileRegenerator} sees the original test in each result.
     *       The variant's distinctive INVALID-parameter value remains in place
     *       because the LLM never produces a replacement for it (its name is
     *       absent from {@code enhancedParameters}).</li>
     * </ol>
     */
    public List<EnhancementResult> enhanceBatch(List<FailedTestResult> failedTests) {
        if (failedTests == null || failedTests.isEmpty()) {
            log.info("🔧 Enhancing 0 failed tests — nothing to do");
            return new ArrayList<>();
        }

        int parallelism = sysIntProp("mst.enhancer.parallelism", 8);
        boolean dedupNegative = sysBoolProp("mst.enhancer.dedup.negative", true);
        boolean cacheEnabled = sysBoolProp("mst.enhancer.cache.enabled", true);

        log.info("🔧 Enhancing {} failed tests (parallelism={}, dedup.negative={}, cache.enabled={})",
                failedTests.size(), parallelism, dedupNegative, cacheEnabled);

        // ── Phase 1: group by canonical key (or unique identity for positives) ──
        String[] keys = new String[failedTests.size()];
        Map<String, List<Integer>> groupToIndices = new LinkedHashMap<>();
        int negCount = 0;
        int posCount = 0;
        for (int i = 0; i < failedTests.size(); i++) {
            FailedTestResult ft = failedTests.get(i);
            if (dedupNegative && ft.isNegativeTest()) {
                keys[i] = canonicalKey(ft);
                negCount++;
            } else {
                // Positives (P1) and dedup-disabled: each gets a unique key so
                // the LLM is invoked independently per test.
                keys[i] = "unique:" + ft.getTestClassName() + "." + ft.getTestMethodName();
                posCount++;
            }
            groupToIndices.computeIfAbsent(keys[i], k -> new ArrayList<>()).add(i);
        }
        int uniqueGroups = groupToIndices.size();
        int reductionFactor = Math.max(1, failedTests.size() / Math.max(1, uniqueGroups));
        log.info("📊 Grouping: {} negative + {} positive → {} unique LLM calls needed ({}x reduction)",
                negCount, posCount, uniqueGroups, reductionFactor);

        // ── Phase 2: cache lookup ──
        EnhancementCache cache = cacheEnabled ? EnhancementCache.getInstance() : null;
        Map<String, EnhancementResult> resolvedByKey = new ConcurrentHashMap<>();
        List<String> toCall = new ArrayList<>();
        for (String key : groupToIndices.keySet()) {
            if (cache != null) {
                Optional<EnhancementCache.CachedEnhancement> hit = cache.get(key);
                if (hit.isPresent()) {
                    EnhancementCache.CachedEnhancement ce = hit.get();
                    // testClass/Method fields are filled per-test in Phase 4 — we
                    // store the shared template here with placeholder identifiers.
                    resolvedByKey.put(key, EnhancementResult.success(
                            "", "",
                            new LinkedHashMap<>(ce.enhancedParameters),
                            ce.reasoning));
                    continue;
                }
            }
            toCall.add(key);
        }
        int cacheHits = uniqueGroups - toCall.size();
        log.info("📁 Cache: {} hits, {} require LLM calls", cacheHits, toCall.size());

        // ── Phase 3: parallel LLM execution with bounded concurrency ──
        if (!toCall.isEmpty()) {
            int effectiveParallelism = Math.max(1, Math.min(parallelism, toCall.size()));
            ExecutorService exec = Executors.newFixedThreadPool(effectiveParallelism, r -> {
                Thread t = new Thread(r, "enhance-llm");
                t.setDaemon(true);
                return t;
            });
            Semaphore gate = new Semaphore(effectiveParallelism);
            ConsoleProgressBar.begin("Enhancing", toCall.size());
            List<CompletableFuture<Void>> futures = new ArrayList<>(toCall.size());
            for (String key : toCall) {
                int representativeIdx = groupToIndices.get(key).get(0);
                FailedTestResult representative = failedTests.get(representativeIdx);
                futures.add(CompletableFuture.runAsync(() -> {
                    try {
                        gate.acquire();
                        try {
                            EnhancementResult r = enhance(representative);
                            resolvedByKey.put(key, r);
                            if (cache != null && r.isSuccess()) {
                                cache.put(key, new EnhancementCache.CachedEnhancement(
                                        r.getEnhancedParameters(), r.getReasoning()));
                            }
                        } finally {
                            gate.release();
                            ConsoleProgressBar.update(representative.getTestMethodName());
                        }
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        resolvedByKey.put(key, EnhancementResult.failed("Interrupted"));
                    } catch (Throwable t) {
                        log.error("Enhancement task failed for key={}: {}",
                                shortKey(key), t.toString());
                        resolvedByKey.put(key,
                                EnhancementResult.failed("Exception: " + t.getMessage()));
                    }
                }, exec));
            }
            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            } finally {
                exec.shutdown();
                ConsoleProgressBar.complete();
            }
        }

        // ── Phase 4: distribute results back to per-test slots ──
        List<EnhancementResult> results = new ArrayList<>(
                Collections.nCopies(failedTests.size(), (EnhancementResult) null));
        int enhanced = 0;
        int failed = 0;
        for (Map.Entry<String, List<Integer>> e : groupToIndices.entrySet()) {
            EnhancementResult shared = resolvedByKey.get(e.getKey());
            for (int idx : e.getValue()) {
                FailedTestResult ft = failedTests.get(idx);
                EnhancementResult perTest;
                if (shared != null && shared.isSuccess()) {
                    // Defensive copy: each test's params map is independent, so
                    // any later mutation by the regenerator stays test-local.
                    perTest = EnhancementResult.success(
                            ft.getTestClassName(),
                            ft.getTestMethodName(),
                            new LinkedHashMap<>(shared.getEnhancedParameters()),
                            shared.getReasoning());
                    enhancedParametersCache.put(
                            ft.getTestClassName() + "." + ft.getTestMethodName(),
                            perTest.getEnhancedParameters());
                    enhanced++;
                } else if (shared != null) {
                    // Share the failed result; no per-test identity needed.
                    perTest = shared;
                    failed++;
                } else {
                    perTest = EnhancementResult.failed("No result for canonical group");
                    failed++;
                }
                results.set(idx, perTest);
            }
        }

        int llmCallsMade = toCall.size();
        int savedByDedupOrCache = failedTests.size() - llmCallsMade - cacheHits;
        log.info("✅ Enhancement complete: {} enhanced, {} failed (LLM calls: {}, cache hits: {}, saved: {})",
                enhanced, failed, llmCallsMade, cacheHits, savedByDedupOrCache);
        return results;
    }

    /**
     * Compute the canonical-key fingerprint for {@code ft}. Used as the
     * deduplication / cache key for negative tests. The fields included are
     * exactly those that affect the LLM's output:
     * <ul>
     *   <li>HTTP method + endpoint (in prompt)</li>
     *   <li>failedStepIndex (regenerator applies step-scoped edits)</li>
     *   <li>actualStatusCode (different status → different LLM advice)</li>
     *   <li>response body fingerprint (truncated + normalized; 200 chars matches
     *       the prompt's own truncation)</li>
     *   <li>parameter SCHEMA fingerprint — names+types+locations+required, sorted
     *       (concrete values are excluded by design; variants vary by value)</li>
     *   <li>invalid-parameter NAMES (sorted; values stripped — distinguishes
     *       _SPECIAL_CHARACTERS vs _OVERFLOW fault types)</li>
     *   <li>locked-dependency NAMES (sorted)</li>
     * </ul>
     * Excluded: testMethodName/testClassName/scenarioName, parameter values,
     * errorMessage/failureType (derivable from status + body).
     */
    String canonicalKey(FailedTestResult ft) {
        JSONObject k = new JSONObject();
        k.put("m", nullSafe(ft.getHttpMethod()));
        k.put("e", nullSafe(ft.getEndpoint()));
        k.put("s", ft.getFailedStepIndex());
        k.put("c", ft.getActualStatusCode());
        k.put("b", sha256Hex(canonicalizeResponse(ft.getResponseBody(), 200)));
        k.put("p", paramSchemaFingerprint(ft));
        k.put("i", sortedJsonArray(stripValueSuffixes(ft.getInvalidParameters())));
        k.put("l", sortedJsonArray(ft.getLockedDependencyParams()));
        return sha256Hex(k.toString());
    }

    private static String paramSchemaFingerprint(FailedTestResult ft) {
        Set<String> locked = ft.getLockedDependencyParams();
        if (locked == null) locked = Collections.emptySet();
        int targetStep = ft.getFailedStepIndex();
        List<String> rows = new ArrayList<>();
        for (ParameterSnapshot p : ft.getParameters()) {
            // Match TestCaseEnhancer.buildUserPrompt:208-217: when failedStepIndex
            // is positive, only that step's params are shown; else all of them.
            if (targetStep > 0 && p.getStepIndex() != targetStep) continue;
            if (locked.contains(p.getName())) continue;
            rows.add(nullSafe(p.getName()) + "|"
                    + nullSafe(p.getType()) + "|"
                    + nullSafe(p.getLocation()) + "|"
                    + p.isRequired());
        }
        Collections.sort(rows);
        return sha256Hex(String.join("\n", rows));
    }

    /**
     * Convert {@code "accountId=BAD_CHARS_8"} entries into bare names
     * {@code "accountId"}. Names alone determine LLM advice (the prompt only
     * lists names — see buildUserPrompt:195-196).
     */
    private static List<String> stripValueSuffixes(List<String> namesWithValues) {
        if (namesWithValues == null) return Collections.emptyList();
        List<String> out = new ArrayList<>(namesWithValues.size());
        for (String raw : namesWithValues) {
            if (raw == null) continue;
            int eq = raw.indexOf('=');
            out.add(eq >= 0 ? raw.substring(0, eq) : raw);
        }
        return out;
    }

    private static JSONArray sortedJsonArray(Collection<String> values) {
        List<String> copy = values == null ? Collections.emptyList() : new ArrayList<>(values);
        Collections.sort(copy);
        return new JSONArray(copy);
    }

    /**
     * Lowercase + collapse-whitespace + truncate. Matches the prompt's
     * 500-char truncation rule loosely but at 200 chars for tighter key
     * locality (the leading 200 chars of an error body almost always
     * determine the LLM's response, and longer bodies vary in noise).
     */
    private static String canonicalizeResponse(String body, int maxChars) {
        if (body == null) return "";
        String norm = body.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
        return norm.length() > maxChars ? norm.substring(0, maxChars) : norm;
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException nsae) {
            throw new IllegalStateException("SHA-256 not available", nsae);
        }
    }

    private static String shortKey(String key) {
        return key == null ? "null" : (key.length() > 12 ? key.substring(0, 12) + "..." : key);
    }

    private static int sysIntProp(String name, int defaultValue) {
        String v = System.getProperty(name);
        if (v == null || v.trim().isEmpty()) return defaultValue;
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException ignored) {
            log.warn("Invalid integer for system property {}={}, falling back to {}", name, v, defaultValue);
            return defaultValue;
        }
    }

    private static boolean sysBoolProp(String name, boolean defaultValue) {
        String v = System.getProperty(name);
        if (v == null || v.trim().isEmpty()) return defaultValue;
        return Boolean.parseBoolean(v.trim());
    }
    
    /**
     * Get cached enhanced parameters for a test.
     */
    public Map<String, String> getEnhancedParameters(String testClassName, String testMethodName) {
        String testKey = testClassName + "." + testMethodName;
        return enhancedParametersCache.get(testKey);
    }
    
    /**
     * Save enhancement results to file.
     */
    public void saveEnhancementResults(List<EnhancementResult> results, String outputDir, int round) {
        try {
            File dir = new File(outputDir, "round-" + round);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            
            File outputFile = new File(dir, "enhancement-results.json");
            objectMapper.writeValue(outputFile, results);
            log.info("💾 Saved {} enhancement results to: {}", results.size(), outputFile.getAbsolutePath());
            
        } catch (IOException e) {
            log.error("Failed to save enhancement results: {}", e.getMessage());
        }
    }
    
    private String buildSystemPrompt() {
        return "You are an expert API test case analyzer and enhancer.\n\n" +
               "Your task is to analyze a failed API test case and suggest improved parameter values " +
               "that are more likely to make the test pass.\n\n" +
               "IMPORTANT RULES:\n" +
               "1. Suggest realistic, valid values that match the API's expectations.\n" +
            //    "2. For NEGATIVE tests (testing invalid inputs), suggest values that are still invalid but might trigger different error responses.\n" +
               "2. Analyze the error response message carefully to understand WHY the test failed.\n" +
               "3. Consider the parameter descriptions, types, and examples when suggesting new values.\n" +
               "4. Return your response in valid JSON format ONLY.\n\n" +
               "RESPONSE FORMAT:\n" +
               "{\n" +
               "  \"enhancedParameters\": [\n" +
               "    {\"name\": \"paramName\", \"value\": \"newValue\"}\n" +
               "  ],\n" +
               "  \"reasoning\": \"Brief explanation of why these values were chosen\"\n" +
               "}";
    }
    
    private String buildUserPrompt(FailedTestResult failedTest) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("ANALYZE THIS FAILED TEST AND SUGGEST IMPROVED PARAMETER VALUES:\n\n");
        
        int failedStep = failedTest.getFailedStepIndex();
        
        prompt.append("TEST INFORMATION:\n");
        prompt.append("- Test Name: ").append(failedTest.getTestMethodName()).append("\n");
        prompt.append("- Endpoint: ").append(failedTest.getHttpMethod()).append(" ")
              .append(failedTest.getEndpoint()).append("\n");
        prompt.append("- Test Type: ").append(failedTest.isNegativeTest() ? "NEGATIVE (invalid inputs)" : "POSITIVE (valid inputs)").append("\n");
        prompt.append("- Service: ").append(failedTest.getServiceName()).append("\n");
        if (failedStep > 0) {
            prompt.append("- Failed Step Index: ").append(failedStep).append("\n");
        }
        prompt.append("\n");
        
        prompt.append("EXECUTION RESULT:\n");
        prompt.append("- HTTP Status: ").append(failedTest.getActualStatusCode()).append("\n");
        prompt.append("- Response: ").append(truncateResponse(failedTest.getResponseBody())).append("\n");
        prompt.append("- Error: ").append(failedTest.getErrorMessage()).append("\n\n");
        
        // For negative tests, show which parameters are intentionally invalid
        if (failedTest.isNegativeTest() && failedTest.getInvalidParameters() != null 
                && !failedTest.getInvalidParameters().isEmpty()) {
            prompt.append("⚠️ INTENTIONALLY INVALID PARAMETERS (DO NOT CHANGE THESE):\n");
            for (String invalidParam : failedTest.getInvalidParameters()) {
                prompt.append("- ").append(invalidParam).append("\n");
            }
            prompt.append("\n");
            prompt.append("NOTE: This is a NEGATIVE test. The parameters above are INTENTIONALLY INVALID.\n");
            prompt.append("You should ONLY suggest changes to OTHER parameters to make the test trigger a different error response.\n");
            prompt.append("DO NOT change the intentionally invalid parameters listed above.\n\n");
        }
        
        // Filter parameters to only those belonging to the failed step
        List<ParameterSnapshot> allParams = failedTest.getParameters();
        List<ParameterSnapshot> targetParams;
        
        if (failedStep > 0) {
            targetParams = new ArrayList<>();
            for (ParameterSnapshot p : allParams) {
                if (p.getStepIndex() == failedStep) {
                    targetParams.add(p);
                }
            }
        } else {
            targetParams = new ArrayList<>(allParams);
        }
        
        // Structural lock: list param names wired to capturedOutputs via StepCall.getParamDependencies()
        Set<String> locked = failedTest.getLockedDependencyParams();
        if (locked != null && !locked.isEmpty()) {
            prompt.append("STRUCTURALLY LOCKED PARAMETERS (DO NOT MODIFY):\n");
            prompt.append("These parameters are wired to runtime variables (capturedOutputs from a previous step).\n");
            prompt.append("They maintain cross-step data flow and MUST remain unchanged.\n");
            for (String lockedName : locked) {
                prompt.append("- ").append(lockedName).append("\n");
            }
            prompt.append("\n");
            
            // Remove locked params from the list shown to the LLM to reduce hallucination risk
            targetParams.removeIf(p -> locked.contains(p.getName()));
        }
        
        prompt.append("CRITICAL RULE: Some parameters in the source code are structurally wired to ")
              .append("runtime variables (e.g., 'capturedOutputs.get(...)'). These are locked dependencies ")
              .append("to maintain business logic. You MUST NOT modify, hardcode, or replace the values ")
              .append("of these parameters. Only generate fixes for independent parameters.\n\n");
        
        prompt.append("PARAMETERS USED (Step ").append(failedStep > 0 ? failedStep : "all").append("):\n");
        prompt.append("```json\n");
        prompt.append(formatParametersForPrompt(targetParams));
        prompt.append("\n```\n\n");
        
        prompt.append("Based on the error response, suggest improved values for the parameters.\n");
        if (failedTest.isNegativeTest()) {
            prompt.append("Remember: DO NOT change the intentionally invalid parameters. Only adjust other parameters.\n");
        }
        if (locked != null && !locked.isEmpty()) {
            prompt.append("CRITICAL: DO NOT suggest changes for structurally locked parameters listed above.\n");
        }
        prompt.append("Return ONLY a valid JSON response in the specified format.");
        
        return prompt.toString();
    }
    
    private String formatParametersForPrompt(List<ParameterSnapshot> parameters) {
        try {
            List<Map<String, Object>> paramList = new ArrayList<>();
            for (ParameterSnapshot param : parameters) {
                Map<String, Object> paramMap = new LinkedHashMap<>();
                paramMap.put("name", param.getName());
                paramMap.put("value", param.getValue());
                paramMap.put("type", param.getType());
                paramMap.put("location", param.getLocation());
                // Always include required flag
                paramMap.put("required", param.isRequired());
                // Include description and example if available
                if (param.getDescription() != null && !param.getDescription().isEmpty()) {
                    paramMap.put("description", param.getDescription());
                }
                if (param.getExample() != null && !param.getExample().isEmpty()) {
                    paramMap.put("example", param.getExample());
                }
                paramList.add(paramMap);
            }
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(paramList);
        } catch (Exception e) {
            return "[]";
        }
    }
    
    private String truncateResponse(String response) {
        if (response == null) return "null";
        if (response.length() > 500) {
            return response.substring(0, 500) + "... (truncated)";
        }
        return response;
    }
    
    private EnhancementResult parseEnhancementResponse(String llmResponse, FailedTestResult originalTest) {
        try {
            // Extract JSON from response (LLM might add explanatory text)
            String jsonStr = extractJson(llmResponse);
            if (jsonStr == null) {
                return EnhancementResult.failed("Could not extract JSON from LLM response");
            }
            
            JsonNode root = objectMapper.readTree(jsonStr);
            
            Map<String, String> enhancedParams = new HashMap<>();
            String reasoning = "";
            
            // Parse enhanced parameters
            if (root.has("enhancedParameters") && root.get("enhancedParameters").isArray()) {
                for (JsonNode paramNode : root.get("enhancedParameters")) {
                    String name = paramNode.has("name") ? paramNode.get("name").asText() : null;
                    // Guard JSON null: paramNode.get("value").asText() on a NullNode
                    // returns the literal string "null", which would be spliced into
                    // the request as the value "null" rather than skipped.
                    JsonNode valueNode = paramNode.get("value");
                    String value = (valueNode != null && !valueNode.isNull()) ? valueNode.asText() : null;
                    if (name != null && value != null) {
                        enhancedParams.put(name, value);
                    }
                }
            }
            
            // Parse reasoning
            if (root.has("reasoning")) {
                reasoning = root.get("reasoning").asText();
            }
            
            // Layer A: Strip structurally locked dependency parameters
            Set<String> locked = originalTest.getLockedDependencyParams();
            if (locked != null && !locked.isEmpty()) {
                Iterator<String> it = enhancedParams.keySet().iterator();
                while (it.hasNext()) {
                    String paramName = it.next();
                    if (locked.contains(paramName)) {
                        log.warn("LLM suggested modifying structurally locked dependency '{}' — stripped", paramName);
                        it.remove();
                    }
                }
            }

            // Layer A2: Strip the sniper TARGET parameters of a negative test. These
            // hold the intentionally-injected fault value; the prompt asks the LLM not
            // to touch them, but that is only advisory. If the LLM proposes a value for
            // a target anyway, applying it would replace the fault value and silently
            // turn the negative into a positive — corrupting detection metrics. Enforce
            // it in code, mirroring the locked-dependency strip.
            if (originalTest.isNegativeTest() && originalTest.getInvalidParameters() != null
                    && !originalTest.getInvalidParameters().isEmpty()) {
                Set<String> targets = new HashSet<>(stripValueSuffixes(originalTest.getInvalidParameters()));
                Iterator<String> it = enhancedParams.keySet().iterator();
                while (it.hasNext()) {
                    String paramName = it.next();
                    if (targets.contains(paramName)) {
                        log.warn("LLM suggested modifying intentionally-invalid target '{}' of a negative test — stripped", paramName);
                        it.remove();
                    }
                }
            }

            if (enhancedParams.isEmpty()) {
                return EnhancementResult.failed("No enhanced parameters found in LLM response (all were locked dependencies)");
            }
            
            return EnhancementResult.success(
                    originalTest.getTestClassName(),
                    originalTest.getTestMethodName(),
                    enhancedParams,
                    reasoning
            );
            
        } catch (Exception e) {
            log.error("Failed to parse LLM enhancement response: {}", e.getMessage());
            return EnhancementResult.failed("Parse error: " + e.getMessage());
        }
    }
    
    private String extractJson(String response) {
        // Try to find JSON object in the response
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        
        if (start >= 0 && end > start) {
            return response.substring(start, end + 1);
        }
        
        return null;
    }
    
    /**
     * Result of an enhancement attempt.
     */
    public static class EnhancementResult {
        private boolean success;
        private String testClassName;
        private String testMethodName;
        private Map<String, String> enhancedParameters;
        private String reasoning;
        private String errorMessage;
        
        public static EnhancementResult success(String testClassName, String testMethodName,
                                                Map<String, String> enhancedParameters, String reasoning) {
            EnhancementResult result = new EnhancementResult();
            result.success = true;
            result.testClassName = testClassName;
            result.testMethodName = testMethodName;
            result.enhancedParameters = enhancedParameters;
            result.reasoning = reasoning;
            return result;
        }
        
        public static EnhancementResult failed(String errorMessage) {
            EnhancementResult result = new EnhancementResult();
            result.success = false;
            result.errorMessage = errorMessage;
            return result;
        }
        
        // Getters
        public boolean isSuccess() { return success; }
        public String getTestClassName() { return testClassName; }
        public String getTestMethodName() { return testMethodName; }
        public Map<String, String> getEnhancedParameters() { return enhancedParameters; }
        public String getReasoning() { return reasoning; }
        public String getErrorMessage() { return errorMessage; }
        
        @Override
        public String toString() {
            if (success) {
                return "EnhancementResult{SUCCESS, test=" + testMethodName + 
                       ", params=" + enhancedParameters.size() + "}";
            } else {
                return "EnhancementResult{FAILED, error=" + errorMessage + "}";
            }
        }
    }
}

