package io.mist.core.workflow.pipeline.stages;

import io.mist.core.spec.Operation;
import io.mist.core.spec.TestConfigurationObject;
import io.mist.core.spec.TestParameter;
import io.mist.core.generation.AiDrivenLLMGenerator;
import io.mist.core.fault.InvalidInputPool;
import io.mist.core.fault.PoolKey;
import io.mist.core.llm.ParameterInfo;
import io.mist.core.smart.SmartInputFetchConfig;
import io.mist.core.smart.SmartInputFetcher;
import io.mist.core.util.ConsoleProgressBar;
import io.mist.core.workflow.WorkflowScenario;
import io.mist.core.workflow.WorkflowStep;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

/**
 * Package-private helpers backing {@link SharedPoolGenerationStage}.
 *
 * <p>The methods below contain the verbatim bodies of the
 * {@code groupScenariosByRootApi}, {@code generateSharedParameterPools},
 * {@code generateParameterPoolForRootApi}, {@code generateFaultyParameterPools},
 * and {@code generateFaultyPoolForSingleRoot} methods that previously lived
 * on {@code MultiServiceTestCaseGenerator}. Pre-existing semantics (logging,
 * dynamic pool sizing, fault-pool location breakdown, etc.) are preserved
 * byte-for-byte.
 */
public final class SharedPoolSupport {
    private static final Logger log = LogManager.getLogger(SharedPoolSupport.class);

    private SharedPoolSupport() {}

    /** Group scenarios by their root API (method + path) to enable parameter sharing. */
    static Map<String, List<WorkflowScenario>> groupScenariosByRootApi(
            List<WorkflowScenario> scenarios) {
        Map<String, List<WorkflowScenario>> groups = new LinkedHashMap<>();

        for (WorkflowScenario sc : scenarios) {
            String rootApiKey = StageSupport.getRootApiKey(sc);
            if (rootApiKey != null) {
                groups.computeIfAbsent(rootApiKey, k -> new ArrayList<>()).add(sc);
                log.info("Grouped scenario {} under root API: {}", sc.getSourceFileName(), rootApiKey);
            } else {
                // Fallback: use scenario-specific key for scenarios without clear root API
                String fallbackKey = "scenario_" + sc.getSourceFileName();
                groups.computeIfAbsent(fallbackKey, k -> new ArrayList<>()).add(sc);
                log.warn("Using fallback key for scenario {}: {}", sc.getSourceFileName(), fallbackKey);
            }
        }

        log.info("=== GROUPED {} scenarios into {} root API groups ===", scenarios.size(), groups.size());
        for (Map.Entry<String, List<WorkflowScenario>> entry : groups.entrySet()) {
            log.info("Root API '{}' has {} scenarios", entry.getKey(), entry.getValue().size());
        }

        return groups;
    }

    /** Generate shared parameter pools for each root API group. */
    static void generateSharedParameterPools(
            Map<String, List<WorkflowScenario>> groupedScenarios,
            Map<String, TestConfigurationObject> serviceConfigs,
            boolean useLLM,
            AiDrivenLLMGenerator llmGen,
            SmartInputFetcher smartFetcher,
            SmartInputFetchConfig smartFetchConfig,
            Map<String, Map<String, List<String>>> sharedParameterPools,
            Map<String, Map<PoolKey, InvalidInputPool>> faultyParameterPools) {
        log.info("=== GENERATING SHARED PARAMETER POOLS ===");

        ConsoleProgressBar.begin("Pool Gen", groupedScenarios.size());
        for (Map.Entry<String, List<WorkflowScenario>> entry : groupedScenarios.entrySet()) {
            String rootApiKey = entry.getKey();
            List<WorkflowScenario> scenariosInGroup = entry.getValue();

            log.info("Generating shared parameters for root API: {} (scenarios: {})",
                    rootApiKey, scenariosInGroup.size());

            // Use the first scenario in the group to extract parameter structure
            WorkflowScenario representativeScenario = scenariosInGroup.get(0);
            Map<String, List<String>> parameterPool = generateParameterPoolForRootApi(
                    representativeScenario, rootApiKey,
                    serviceConfigs, useLLM, llmGen, smartFetcher, smartFetchConfig);

            sharedParameterPools.put(rootApiKey, parameterPool);
            ConsoleProgressBar.update(rootApiKey);

            log.info("Generated parameter pool for '{}' with {} parameters",
                    rootApiKey, parameterPool.size());
        }
        ConsoleProgressBar.complete();

        log.info("=== COMPLETED: {} shared parameter pools generated ===", sharedParameterPools.size());

        // Generate faulty parameter pools
        generateFaultyParameterPools(groupedScenarios, serviceConfigs, useLLM, llmGen, faultyParameterPools);
    }

    /** Generate a parameter pool for a specific root API using the first scenario as reference. */
    private static Map<String, List<String>> generateParameterPoolForRootApi(
            WorkflowScenario scenario, String rootApiKey,
            Map<String, TestConfigurationObject> serviceConfigs,
            boolean useLLM,
            AiDrivenLLMGenerator llmGen,
            SmartInputFetcher smartFetcher,
            SmartInputFetchConfig smartFetchConfig) {
        Map<String, List<String>> parameterPool = new HashMap<>();

        // Find the first business API step to extract its parameters
        WorkflowStep firstBusinessStep = StageSupport.findFirstBusinessStep(scenario);
        if (firstBusinessStep == null) {
            log.warn("No business step found for root API: {}", rootApiKey);
            return parameterPool;
        }

        // Extract HTTP operation info
        String service = firstBusinessStep.getServiceName();
        String opName = firstBusinessStep.getOperationName();

        String verb = null, route = null;
        Matcher httpMatcher = StageSupport.HTTP_OPERATION_PATTERN.matcher(opName);
        if (httpMatcher.matches()) {
            verb = httpMatcher.group(1).toLowerCase();
            route = httpMatcher.group(2);
        } else {
            // Try extracting from trace data
            Map<String, String> outputs = firstBusinessStep.getOutputFields();
            String httpMethod = outputs.get("http.method");
            String httpTarget = outputs.get("http.target");

            if (httpMethod != null && httpTarget != null) {
                verb = httpMethod.toLowerCase();
                route = httpTarget;
            }
        }

        if (verb == null || route == null) {
            // Fallback: the rootApiKey itself encodes method + path (e.g. "POST__cart_checkout"
            // -> POST /cart/checkout, "GET__" -> GET /). Istio/wildcard span operation names
            // don't match HTTP_OPERATION_PATTERN and the trace may omit http.method/http.target,
            // so without this the pool stays empty and no tests are produced for the root.
            String[] mp = methodPathFromRootKey(rootApiKey);
            if (mp != null) { verb = mp[0]; route = mp[1]; }
        }
        if (verb == null || route == null) {
            log.warn("Could not extract HTTP method/path for root API: {}", rootApiKey);
            return parameterPool;
        }

        // Resolve the operation: service-name match first, then a template-aware
        // endpoint fallback so a service-name mismatch (e.g. Bookinfo trace
        // "productpage.default" vs conf "productpage") still finds the op and
        // builds a NON-empty pool. firstBusinessStep is the root by construction.
        StageSupport.ResolvedOperation resolved =
                StageSupport.resolveOperation(serviceConfigs, service, verb, route);
        if (resolved == null) {
            log.warn("No operation config for {} {} (service '{}') for root API: {}", verb, route, service, rootApiKey);
            return parameterPool;
        }
        Operation opCfg = resolved.op;

        // Generate parameter values for all parameters in this operation
        if (opCfg.getTestParameters() != null && useLLM) {
            // Collect all parameter names for context
            List<String> allParamNames = new ArrayList<>();
            for (TestParameter tp : opCfg.getTestParameters()) {
                allParamNames.add(tp.getName());
            }

            int numParams = allParamNames.size();
            int variantCount = StageSupport.getVariantCountFromProperties();
            int targetPoolSize = StageSupport.computeTargetPoolSize(numParams, variantCount);

            log.info("Dynamic Pool Scaling → API '{}': {} params, {} variants → targetPoolSize={}",
                    rootApiKey, numParams, variantCount, targetPoolSize);

            // Per-location enrolment breakdown so operators reviewing the run
            // log can confirm path / header / cookie params got enrolled — the
            // previous symptom was a silently empty pool for those locations.
            Map<String, Integer> sharedLocationBreakdown = new LinkedHashMap<>();
            sharedLocationBreakdown.put("path", 0);
            sharedLocationBreakdown.put("query", 0);
            sharedLocationBreakdown.put("header", 0);
            sharedLocationBreakdown.put("cookie", 0);
            sharedLocationBreakdown.put("body", 0);
            sharedLocationBreakdown.put("other", 0);

            // Build API name for context
            String apiName = verb.toUpperCase() + " " + route;

            ConsoleProgressBar.begin("params", opCfg.getTestParameters().size());
            for (TestParameter p : opCfg.getTestParameters()) {
                String sharedNormalisedIn = StageSupport.normaliseParamLocation(p.getIn());
                sharedLocationBreakdown.merge(sharedNormalisedIn, 1, Integer::sum);
                ParameterInfo info = StageSupport.createParameterInfoWithContext(p, apiName, service, allParamNames);
                Set<String> uniqueValues = new LinkedHashSet<>();

                // Phase 1: Smart Input Fetching. Grounding fix B: track which values are
                // GROUNDED (fetched live/cached from the SUT) vs LLM-invented, via provenance.
                Set<String> groundedValues = new LinkedHashSet<>();
                if (smartFetcher != null && smartFetchConfig != null && smartFetchConfig.isEnabled()) {
                    try {
                        for (int i = 0; i < targetPoolSize && uniqueValues.size() < targetPoolSize; i++) {
                            java.util.Optional<io.mist.core.value.ResolvedValue> rv =
                                    smartFetcher.fetchSmartInputWithProvenance(info);
                            if (rv.isEmpty()) continue;
                            String smartValue = rv.get().value();
                            if (smartValue == null || smartValue.trim().isEmpty()) continue;
                            uniqueValues.add(smartValue);
                            io.mist.core.value.ValueProvenance prov = rv.get().provenance();
                            if (prov == io.mist.core.value.ValueProvenance.RESOLVED_LIVE
                                    || prov == io.mist.core.value.ValueProvenance.RESOLVED_CACHE) {
                                groundedValues.add(smartValue);
                            }
                        }
                        if (!uniqueValues.isEmpty()) {
                            log.info("Smart Fetch Pool → parameter '{}': {} unique values ({} grounded from SUT)",
                                    p.getName(), uniqueValues.size(), groundedValues.size());
                        }
                    } catch (Exception e) {
                        log.debug("Smart fetching failed for shared pool parameter '{}': {}",
                                 p.getName(), e.getMessage());
                    }
                }

                // Grounding fix B: if the SUT actually grounded this parameter (a producer
                // endpoint exists and returned real values), make the pool GROUNDED-DOMINANT
                // rather than diluting it with LLM-invented padding to reach targetPoolSize.
                // Variants then draw real, SUT-accepted values. The LLM top-up below runs ONLY
                // for parameters the SUT could not ground (no producer source) — that residual is
                // the inherent value-generation problem, not a tool defect.
                if (!groundedValues.isEmpty()) {
                    parameterPool.put(p.getName(), new ArrayList<>(groundedValues));
                    ConsoleProgressBar.update(p.getName());
                    log.info("Generated GROUNDED shared pool for '{}' (in={}): {} live values",
                            p.getName(), sharedNormalisedIn, groundedValues.size());
                    continue;
                }

                // Phase 2: LLM top-up with dynamic howMany (only when grounding produced nothing).
                if (uniqueValues.size() < targetPoolSize) {
                    int needed = targetPoolSize - uniqueValues.size();
                    log.info("Smart fetch provided {} values for '{}', requesting {} more from LLM",
                            uniqueValues.size(), p.getName(), needed);

                    List<String> llmValues = llmGen.generateParameterValues(info, Math.min(needed, 50));
                    if (!llmValues.isEmpty()) {
                        uniqueValues.addAll(llmValues);
                        log.info("LLM Pool → parameter '{}': {} values added (total unique: {})",
                                p.getName(), llmValues.size(), uniqueValues.size());
                    } else {
                        log.warn("LLM returned no values for parameter '{}', using fallback", p.getName());
                    }
                }

                // Phase 3: Fallback padding to guarantee minimum pool size. Type-aware
                // so numeric / boolean / array slots don't get string-shaped padding.
                // Closed-domain types (boolean, enum) are capped so the loop terminates
                // once padding stops making progress.
                int fallbackIdx = 0;
                int safetyCeiling = Math.max(targetPoolSize * 2, 64);
                int prevSize = -1;
                while (uniqueValues.size() < targetPoolSize && fallbackIdx < safetyCeiling) {
                    int sizeBefore = uniqueValues.size();
                    uniqueValues.add(StageSupport.typeAwareFallbackValue(p, fallbackIdx++));
                    if (uniqueValues.size() == sizeBefore && uniqueValues.size() == prevSize) {
                        log.debug("Pool padding for '{}' reached closed-domain ceiling at "
                                + "{} unique values (target was {})",
                                p.getName(), uniqueValues.size(), targetPoolSize);
                        break;
                    }
                    prevSize = sizeBefore;
                }

                parameterPool.put(p.getName(), new ArrayList<>(uniqueValues));
                ConsoleProgressBar.update(p.getName());
                log.info("Generated shared pool for parameter '{}' (in={}): {} unique values (target was {})",
                        p.getName(), sharedNormalisedIn,
                        parameterPool.get(p.getName()).size(), targetPoolSize);
            }
            ConsoleProgressBar.complete();

            log.info("Shared pool enrolment by location for '{}': path={} query={} header={} cookie={} body={} other={}",
                    rootApiKey,
                    sharedLocationBreakdown.get("path"),
                    sharedLocationBreakdown.get("query"),
                    sharedLocationBreakdown.get("header"),
                    sharedLocationBreakdown.get("cookie"),
                    sharedLocationBreakdown.get("body"),
                    sharedLocationBreakdown.get("other"));
        }

        return parameterPool;
    }

    /**
     * Generate faulty parameter pools for every root API across all scenarios.
     * For multi-root sequences (A-&gt;B merged by data dependency), this builds
     * separate pools keyed by each root's own verb_path, enabling negative
     * injection into any root in the sequence.
     */
    private static void generateFaultyParameterPools(
            Map<String, List<WorkflowScenario>> groupedScenarios,
            Map<String, TestConfigurationObject> serviceConfigs,
            boolean useLLM,
            AiDrivenLLMGenerator llmGen,
            Map<String, Map<PoolKey, InvalidInputPool>> faultyParameterPools) {
        log.info("=== GENERATING FAULTY PARAMETER POOLS (ALL ROOTS) ===");
        log.info("Number of scenario groups: {}", groupedScenarios.size());

        // Pre-count total roots for progress tracking
        int totalRoots = 0;
        for (List<WorkflowScenario> group : groupedScenarios.values()) {
            totalRoots += group.get(0).getRootSteps().size();
        }
        int rootProgress = 0;
        ConsoleProgressBar.begin("Faulty Pools", totalRoots);

        // Iterate ALL distinct rootApiKeys across ALL scenarios (deduped).
        // Previously only representative.getRootSteps() was scanned, which
        // dropped roots that appeared only in non-representative scenarios —
        // most notably DELETE /admintravel/{tripId}, whose pool was never
        // generated, so no negative variants (and no INVALID_TRIP_ID_FORMAT
        // detection) ever fired.
        java.util.LinkedHashMap<String, WorkflowStep> uniqueRoots = new java.util.LinkedHashMap<>();
        for (java.util.Map.Entry<String, java.util.List<WorkflowScenario>> entry : groupedScenarios.entrySet()) {
            for (WorkflowScenario scenario : entry.getValue()) {
                for (WorkflowStep rootStep : scenario.getRootSteps()) {
                    String rootApiKey = StageSupport.extractRootApiFromStep(rootStep);
                    if (rootApiKey != null) uniqueRoots.putIfAbsent(rootApiKey, rootStep);
                }
            }
        }
        log.info("Unique root API keys across all scenarios: {}", uniqueRoots.size());

        for (java.util.Map.Entry<String, WorkflowStep> rootEntry : uniqueRoots.entrySet()) {
            String rootApiKey = rootEntry.getKey();
            WorkflowStep rootStep = rootEntry.getValue();
            if (faultyParameterPools.containsKey(rootApiKey)) {
                rootProgress++;
                ConsoleProgressBar.update("reuse " + rootApiKey);
                continue;
            }
            log.info("Processing root with key '{}'", rootApiKey);

            Map<PoolKey, InvalidInputPool> faultyPool =
                    generateFaultyPoolForSingleRoot(rootStep, rootApiKey,
                            serviceConfigs, useLLM, llmGen);

            faultyParameterPools.put(rootApiKey, faultyPool);
            rootProgress++;
            ConsoleProgressBar.update(rootApiKey);
            log.info("Generated faulty pool for '{}' with {} parameters: {}",
                    rootApiKey, faultyPool.size(), faultyPool.keySet());
        }
        ConsoleProgressBar.complete();

        log.info("=== COMPLETED: {} faulty parameter pools generated ===", faultyParameterPools.size());
        log.info("All faulty pool keys: {}", faultyParameterPools.keySet());
    }

    /**
     * Build an InvalidInputPool for a single root step (not just the first business step).
     * Reuses the same LLM-driven invalid-input generation as before but scoped to
     * exactly one root's Operation config.
     */
    static Map<PoolKey, InvalidInputPool> generateFaultyPoolForSingleRoot(
            WorkflowStep rootStep, String rootApiKey,
            Map<String, TestConfigurationObject> serviceConfigs,
            boolean useLLM,
            AiDrivenLLMGenerator llmGen) {

        Map<PoolKey, InvalidInputPool> faultyPool = new HashMap<>();

        WorkflowStep businessStep = StageSupport.findFirstBusinessStepRecursive(rootStep);
        if (businessStep == null) {
            log.warn("No business step found under root for key '{}'", rootApiKey);
            return faultyPool;
        }

        String service = businessStep.getServiceName();
        String opName  = businessStep.getOperationName();
        String verb = null, route = null;

        Matcher httpMatcher = StageSupport.HTTP_OPERATION_PATTERN.matcher(opName);
        if (httpMatcher.matches()) {
            verb  = httpMatcher.group(1).toLowerCase();
            route = httpMatcher.group(2);
        } else {
            Map<String, String> outputs = businessStep.getOutputFields();
            String httpMethod = outputs.get("http.method");
            String httpTarget = outputs.get("http.target");
            if (httpMethod != null && httpTarget != null) {
                verb  = httpMethod.toLowerCase();
                route = httpTarget;
            }
        }
        if (verb == null || route == null) {
            // Fallback: parse method + path from the rootApiKey (see the sibling above).
            // Without this, Istio/wildcard SUTs (e.g. Online Boutique) produce an EMPTY
            // faulty pool -> ZERO negative tests. A gRPC-internal root (no matching conf
            // operation) still yields an empty pool via resolveOperation below, so this
            // only enables faulty pools for real, resolvable HTTP endpoints.
            String[] mp = methodPathFromRootKey(rootApiKey);
            if (mp != null) { verb = mp[0]; route = mp[1]; }
        }
        if (verb == null || route == null) {
            log.warn("Cannot extract HTTP info for root key '{}'", rootApiKey);
            return faultyPool;
        }

        // Service-name match first, then template-aware endpoint fallback, so a
        // service-name mismatch still yields a NON-empty faulty pool (no faulty
        // pool → no negative tests). businessStep is the root by construction.
        StageSupport.ResolvedOperation resolved =
                StageSupport.resolveOperation(serviceConfigs, service, verb, route);
        if (resolved == null) { return faultyPool; }
        Operation opCfg = resolved.op;

        if (opCfg.getTestParameters() != null && useLLM) {
            List<String> allParamNames = new ArrayList<>();
            for (TestParameter tp : opCfg.getTestParameters()) {
                allParamNames.add(tp.getName());
            }
            String apiName = verb.toUpperCase() + " " + route;

            // Cover every parameter location. The previous queue silently
            // dropped path / header / cookie params, breaking the
            // "8-fault coverage per parameter" guarantee for endpoints that
            // use them. Location normalisation folds OpenAPI 2 'formData'
            // into 'body' and defaults null/empty to 'body' to match the
            // writer's body-path conventions.
            int preCount = faultyPool.size();
            Map<String, Integer> locationBreakdown = new LinkedHashMap<>();
            locationBreakdown.put("path", 0);
            locationBreakdown.put("query", 0);
            locationBreakdown.put("header", 0);
            locationBreakdown.put("cookie", 0);
            locationBreakdown.put("body", 0);
            locationBreakdown.put("other", 0);
            log.info("Fault enrolment (root='{}'): pre={} parameters in pool",
                    rootApiKey, preCount);

            ConsoleProgressBar.begin("params", opCfg.getTestParameters().size());
            for (TestParameter p : opCfg.getTestParameters()) {
                String normalisedIn = StageSupport.normaliseParamLocation(p.getIn());
                locationBreakdown.merge(normalisedIn, 1, Integer::sum);

                ParameterInfo info = StageSupport.createParameterInfoWithContext(p, apiName, service, allParamNames);
                InvalidInputPool pool = llmGen.generateInvalidInputPool(info);
                faultyPool.put(new PoolKey(p.getName(), normalisedIn), pool);
                ConsoleProgressBar.update(p.getName());
                log.debug("  Invalid pool for '{}' (in={}): {}",
                        p.getName(), normalisedIn, pool.getTotalCount());
            }
            ConsoleProgressBar.complete();

            int postCount = faultyPool.size();
            log.info("Fault enrolment (root='{}'): post={} parameters in pool (delta={})",
                    rootApiKey, postCount, postCount - preCount);
            log.info("Fault enrolment by location for '{}': path={} query={} header={} cookie={} body={} other={}",
                    rootApiKey,
                    locationBreakdown.get("path"),
                    locationBreakdown.get("query"),
                    locationBreakdown.get("header"),
                    locationBreakdown.get("cookie"),
                    locationBreakdown.get("body"),
                    locationBreakdown.get("other"));
        }

        int totalInvalidValues = faultyPool.values().stream()
                .mapToInt(InvalidInputPool::getTotalCount).sum();
        log.info("Faulty pool for '{}': {} params, {} total invalid values",
                rootApiKey, faultyPool.size(), totalInvalidValues);
        return faultyPool;
    }

    /**
     * Parse a "METHOD__path" root key (the registry/trace encoding where '_' stands in for
     * '/') into {method, route}: "POST__cart_checkout" -> {"post","/cart/checkout"},
     * "GET__" -> {"get","/"}. Used as a fallback when the span operation name is a wildcard
     * (Istio) and the trace omits http.method/http.target, so faulty pools — and hence
     * negative tests — still get generated for resolvable HTTP roots. Returns null if the
     * key has no "__" separator.
     */
    static String[] methodPathFromRootKey(String rootApiKey) {
        if (rootApiKey == null) return null;
        int us = rootApiKey.indexOf("__");
        if (us <= 0) return null;
        String verb = rootApiKey.substring(0, us).toLowerCase();
        String route = "/" + rootApiKey.substring(us + 2).replace("_", "/");
        return new String[]{ verb, route };
    }
}
