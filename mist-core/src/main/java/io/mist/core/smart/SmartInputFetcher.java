package io.mist.core.smart;

import io.mist.core.generation.AiDrivenLLMGenerator;
import io.mist.core.llm.ParameterInfo;
import io.mist.core.smart.ApiMapping;
import io.mist.core.smart.CacheConfig;
import io.mist.core.smart.InputFetchRegistry;
import io.mist.core.smart.OpenAPIEndpointDiscovery;
import io.mist.core.smart.SmartFetchAuthManager;
import io.mist.core.smart.SmartInputFetchConfig;
import io.mist.core.value.ResolvedValue;
import io.mist.llm.LLMService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
// JsonPath / PathNotFoundException imports removed — JSONPath is fully retired in favor
// of direct LLM extraction (Bug audit Findings #9, #31; Reviewer Comment 14).

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
// Fresh-review Finding F22: removed unused imports (OpenAPISpecification, LLMConfig,
// ConcurrentHashMap, TimeUnit, java.util.regex.Pattern, all okhttp3.* — okhttp3 was a
// planned migration that never happened; the file uses HttpURLConnection throughout).
// org.json.JSONArray / JSONObject are referenced fully-qualified in this file already.

/**
 * Smart Input Fetching Service
 * Intelligently fetches realistic test data from existing APIs instead of generating random values
 */
public class SmartInputFetcher {

    private static final Logger log = LogManager.getLogger(SmartInputFetcher.class);

    /**
     * Safety ceiling on value length when the parameter schema does not declare a
     * {@code maxLength}. Generous enough to cover realistic descriptions, station names,
     * and serialized JSON snippets, while still rejecting clearly truncated paragraphs
     * pasted by an LLM.
     */
    private static final int DEFAULT_MAX_VALUE_LENGTH = 2000;

    private final SmartInputFetchConfig config;
    private final AiDrivenLLMGenerator llmGenerator;
    private final LLMService llmService;
    private final ObjectMapper objectMapper;
    private final Random random;
    private final OpenAPIEndpointDiscovery openAPIDiscovery;
    private final SmartFetchAuthManager authManager;

    // Runtime data
    private InputFetchRegistry registry;
    /**
     * Bug audit Finding #24: caches are bounded by {@link CacheConfig#getMaxEntries()}.
     * The previous implementation used unbounded {@link ConcurrentHashMap}, which leaks
     * in long-running soak tests and never honored the {@code maxEntries} config field.
     * We now use a synchronized LinkedHashMap that evicts least-recently-accessed entries
     * once the cap is exceeded.
     */
    private Map<String, CachedValue> cache;
    private Map<String, List<String>> diverseValueCache;
    private Map<String, Integer> valueRotationIndex;
    /**
     * Bug fix (success-then-reject loop): track consecutive validation-failure counts per
     * (cacheKey, mappingEndpoint) pair. When a mapping repeatedly returns values that pass
     * the API-response check but fail {@link #isValidValueForParameter} (e.g. orderId UUID
     * {@code 03e27662-...} 122 times in one run), we quarantine that mapping for the rest
     * of the JVM lifetime so subsequent fetches skip it instead of looping. In-memory only;
     * no persistent registry change. Bounded LRU so a long-running soak test cannot leak.
     */
    private Map<String, Integer> mappingFailureCounts;
    /**
     * Quarantine threshold: after this many consecutive validation failures from the same
     * mapping for the same parameter cache-key, the mapping is skipped for the rest of the
     * run. Tuned conservatively — a healthy upstream that briefly emits a stale value will
     * recover within 3 attempts before being benched.
     */
    private static final int MAPPING_QUARANTINE_THRESHOLD = 3;
    private String baseUrl;

    /**
     * Construct a bounded LRU map with the given capacity. Access-order ensures that
     * frequently-used entries survive eviction. Wrapped in {@code synchronizedMap} so
     * the existing {@code ConcurrentHashMap}-style synchronization assumptions hold for
     * single-key operations; the caller is responsible for compound atomicity (which the
     * existing code already manages via {@code synchronized (list) { ... }} blocks).
     */
    private static <K, V> Map<K, V> boundedLruMap(int capacity) {
        if (capacity <= 0) {
            return java.util.Collections.synchronizedMap(new java.util.LinkedHashMap<>(16, 0.75f, true));
        }
        final int max = capacity;
        return java.util.Collections.synchronizedMap(new java.util.LinkedHashMap<K, V>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > max;
            }
        });
    }

    // Cache for fetched values
    private static class CachedValue {
        final String value;
        final LocalDateTime timestamp;

        CachedValue(String value) {
            this.value = value;
            this.timestamp = LocalDateTime.now();
        }

        boolean isExpired(int ttlSeconds) {
            return LocalDateTime.now().isAfter(timestamp.plusSeconds(ttlSeconds));
        }
    }

    /**
     * Reviewer Comment 1: track in-memory mutations to {@code registry} that originate
     * from Priority-1 (registry-mapping) success/failure updates so we can flush them on
     * scenario boundary or JVM shutdown — previously {@code saveRegistry()} only ran
     * after discovery, so {@code ApiMapping.successRate}/{@code lastUsed} updates from
     * subsequent fetches were lost on next run.
     */
    private volatile boolean registryDirty = false;

    /**
     * Fresh-review Finding F11: track live fetchers in a class-level set so we can register
     * exactly ONE JVM shutdown hook regardless of how many fetchers are constructed. The
     * previous per-instance hook leaked the fetcher reference (preventing GC) and caused
     * last-writer-wins data loss in multi-fetcher test farms.
     */
    private static final java.util.Set<SmartInputFetcher> LIVE_FETCHERS =
            java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
    private static final java.util.concurrent.atomic.AtomicBoolean SHUTDOWN_HOOK_REGISTERED =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    private static void registerSharedShutdownHook() {
        if (SHUTDOWN_HOOK_REGISTERED.compareAndSet(false, true)) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                for (SmartInputFetcher f : LIVE_FETCHERS) {
                    try { f.flushIfDirty(); }
                    catch (Throwable ignored) { /* shutdown is best-effort */ }
                }
            }, "smart-fetch-registry-flush"));
        }
    }

    public SmartInputFetcher(SmartInputFetchConfig config, String baseUrl) {
        this.config = config;
        // Fresh-review Finding F23: normalize trailing slash so concatenation with the
        // mapping endpoint (which always starts with '/') doesn't produce a `//path` URL.
        // Matches the same hygiene already applied in SmartFetchAuthManager.
        this.baseUrl = baseUrl != null && baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.llmGenerator = new AiDrivenLLMGenerator();

        // Initialize LLM service with properties from system
        Map<String, String> llmProperties = loadLLMProperties();
        this.llmService = LLMService.getInstance(llmProperties);

        this.objectMapper = new ObjectMapper();
        this.random = io.mist.core.util.SeededRandom.create("SmartInputFetcher");
        this.openAPIDiscovery = new OpenAPIEndpointDiscovery();

        // Initialize authentication manager (Bug audit Finding #11: now accepts a generic
        // login URL/body/token-path/expiry config so it is no longer TrainTicket-specific).
        this.authManager = new SmartFetchAuthManager(
            baseUrl,
            config.getAuthAdminUsername(),
            config.getAuthAdminPassword(),
            config.getAuthLoginPath(),
            config.getAuthLoginUsernameField(),
            config.getAuthLoginPasswordField(),
            config.getAuthTokenJsonPath(),
            config.getAuthTokenValidityMinutes()
        );

        // Fresh-review Finding F1: load the registry before constructing the caches so
        // CacheConfig.maxEntries from the persisted YAML is honored. Previously the caches
        // were constructed first, registryCacheCapacity() saw {@code registry==null} and
        // always returned the literal 1000, silently dropping the operator's tuning knob.
        loadRegistry();
        loadOpenAPISpec();

        int cacheCap = config.isCacheEnabled()
                ? Math.max(1, registryCacheCapacity()) : 1;
        this.cache = boundedLruMap(cacheCap);
        this.diverseValueCache = boundedLruMap(cacheCap);
        this.valueRotationIndex = boundedLruMap(cacheCap);
        // Failure counters reuse the same LRU bound so they cannot outgrow the value caches.
        this.mappingFailureCounts = boundedLruMap(cacheCap);

        // Reviewer Comment 1 + Fresh-review F11: persist Priority-1 mutations on JVM exit
        // via a SINGLE class-level shutdown hook (was previously one per fetcher → leak).
        LIVE_FETCHERS.add(this);
        registerSharedShutdownHook();

        log.info("SmartInputFetcher initialized with config: {}", config);
        log.info("Authentication manager configured: {}", authManager.isConfigured());
    }

    /**
     * Persist the in-memory registry to disk if any Priority-1 mutation has occurred since
     * the last save. Safe to call multiple times; no-op when clean. Used by the JVM shutdown
     * hook and may be called by callers at scenario-boundary for tighter durability
     * (Reviewer Comment 1).
     */
    /**
     * Resolve the LRU bound for the in-memory caches from {@link CacheConfig#getMaxEntries()}.
     * Defaults to 1000 if the registry has not been loaded yet (the cache is constructed
     * before {@code loadRegistry()} runs).
     */
    private int registryCacheCapacity() {
        return registry != null && registry.getCacheConfig() != null
                ? registry.getCacheConfig().getMaxEntries()
                : 1000;
    }

    public void flushIfDirty() {
        if (registryDirty) {
            try {
                saveRegistry();
                registryDirty = false;
            } catch (Exception e) {
                log.warn("Failed to flush dirty registry: {}", e.getMessage());
            }
        }
    }

    /**
     * Load LLM properties from system properties.
     *
     * <p>Fresh-review Finding F2: replaced the explicit allowlist (which silently dropped
     * {@code llm.openai_compatible.api.key} / legacy {@code llm.local.api.key} — critical
     * for DeepSeek/OpenAI-compatible auth per {@code flow.md:851-862}) with a prefix-based
     * filter that forwards every system property starting with {@code llm.} or
     * {@code auth.admin.}. New LLM properties are automatically picked up without touching
     * this method.</p>
     */
    private Map<String, String> loadLLMProperties() {
        Map<String, String> properties = new HashMap<>();
        for (Object keyObj : System.getProperties().keySet()) {
            String key = String.valueOf(keyObj);
            if (key.startsWith("llm.") || key.startsWith("auth.admin.")) {
                String value = System.getProperty(key);
                if (value != null) {
                    properties.put(key, value);
                }
            }
        }

        // If model type is set to ollama but the OpenAI-compatible backend is
        // enabled, prefer OPENAI_COMPATIBLE to match the user's intent.
        // Read the canonical llm.openai_compatible.enabled, then fall back to
        // the legacy llm.local.enabled, so both old and new configs work.
        String modelType = properties.getOrDefault("llm.model.type", "openai_compatible").toLowerCase();
        String openaiCompatibleEnabled = properties.getOrDefault(
                "llm.openai_compatible.enabled",
                properties.getOrDefault("llm.local.enabled", "false"));
        String ollamaEnabled = properties.getOrDefault("llm.ollama.enabled", "false");
        if ("ollama".equals(modelType)
                && Boolean.parseBoolean(openaiCompatibleEnabled)
                && !Boolean.parseBoolean(ollamaEnabled)) {
            properties.put("llm.model.type", "openai_compatible");
            log.warn("LLM config: Overriding llm.model.type=ollama -> openai_compatible "
                    + "because the OpenAI-compatible backend is enabled and Ollama is not");
        }

        return properties;
    }

    /**
     * Fetch a smart input value for a parameter. Convenience wrapper that
     * discards provenance; new code should prefer
     * {@link #fetchSmartInputWithProvenance(ParameterInfo)} so the caller
     * can record how the value was obtained.
     */
    public String fetchSmartInput(ParameterInfo parameterInfo) {
        return fetchSmartInputWithProvenance(parameterInfo)
                .map(ResolvedValue::value)
                .orElse(null);
    }

    /**
     * Same fetch pipeline as {@link #fetchSmartInput(ParameterInfo)}, but each
     * returned value is tagged with the {@link io.mist.core.value.ValueProvenance}
     * describing how it was obtained:
     * <ul>
     *   <li>{@link io.mist.core.value.ValueProvenance#RESOLVED_CACHE} — a
     *       diverse value rotated from the smart-fetch cache;</li>
     *   <li>{@link io.mist.core.value.ValueProvenance#RESOLVED_LIVE} — a value
     *       returned by the smart-source pipeline (trace producer, registry
     *       mapping, or LLM-discovered endpoint);</li>
     *   <li>{@link io.mist.core.value.ValueProvenance#LLM_GENERATED} — the
     *       smart pipeline declined / failed and the LLM produced a realistic
     *       value (still a positive value, not a placeholder).</li>
     * </ul>
     * Returns {@link Optional#empty()} only when even the LLM fallback yields
     * a null or empty string.
     */
    public Optional<ResolvedValue> fetchSmartInputWithProvenance(ParameterInfo parameterInfo) {
        if (!config.isEnabled()) {
            log.debug("Smart fetching disabled, using LLM for parameter '{}'", parameterInfo.getName());
            return wrapLlm(fallbackToLLM(parameterInfo));
        }

        double randomValue = random.nextDouble();
        if (randomValue < config.getSmartFetchPercentage()) {
            log.info("🎯 Smart Fetch Decision → {} (random: {} < {}%)",
                     parameterInfo.getName(),
                     String.format("%.3f", randomValue),
                     String.format("%.1f", config.getSmartFetchPercentage() * 100));

            clearInvalidCachedValues(parameterInfo);

            String diverseValue = getNextDiverseValue(parameterInfo);
            if (diverseValue != null) {
                log.info("🔄 Using diverse cached value → {} = {} ✅", parameterInfo.getName(), diverseValue);
                return Optional.of(ResolvedValue.cache(diverseValue));
            }

            try {
                String result = fetchFromSmartSource(parameterInfo);
                if (result != null) {
                    log.info("Smart Fetch → {} = {} ✅", parameterInfo.getName(), result);
                    return Optional.of(ResolvedValue.live(result));
                }
                log.info("Smart Fetch → {} = FAILED (no good matches found), falling back to LLM", parameterInfo.getName());
                return wrapLlm(fallbackToLLM(parameterInfo));
            } catch (Exception e) {
                log.info("Smart Fetch → {} = ERROR ({}), falling back to LLM", parameterInfo.getName(), e.getMessage());
                return wrapLlm(fallbackToLLM(parameterInfo));
            }
        }

        log.info("🤖 LLM Decision → {} (random: {} >= {}%)",
                 parameterInfo.getName(),
                 String.format("%.3f", randomValue),
                 String.format("%.1f", config.getSmartFetchPercentage() * 100));
        return wrapLlm(fallbackToLLM(parameterInfo));
    }

    private static Optional<ResolvedValue> wrapLlm(String s) {
        return (s == null || s.trim().isEmpty()) ? Optional.empty() : Optional.of(ResolvedValue.llm(s));
    }

    /**
     * Fetch input from smart sources (existing APIs).
     * When trace-observed producer endpoints are available on the ParameterInfo,
     * those endpoints are tried first (highest priority) before falling back
     * to registry mappings and LLM discovery.
     */
    private String fetchFromSmartSource(ParameterInfo parameterInfo) throws Exception {
        String paramName = parameterInfo.getName();

        // Check cache first
        if (config.isCacheEnabled()) {
            String cacheKey = buildCacheKey(parameterInfo);
            CachedValue cached = cache.get(cacheKey);
            if (cached != null && !cached.isExpired(config.getCacheTtlSeconds())) {
                log.debug("Using cached value for parameter '{}'", paramName);
                return cached.value;
            }
        }

        // Priority 0: Try trace-observed producer endpoints first
        List<String> traceEndpoints = parameterInfo.getTraceProducerEndpoints();
        if (traceEndpoints != null && !traceEndpoints.isEmpty()) {
            log.info("Trace-aware fetch: trying {} producer endpoints observed in workflow for '{}'",
                    traceEndpoints.size(), paramName);
            for (String endpoint : traceEndpoints) {
                ApiMapping traceMapping = new ApiMapping(endpoint, "trace-observed", "DIRECT_EXTRACTION");
                traceMapping.setPriority(10);
                applyMappingTuning(traceMapping);
                // Bug fix (success-then-reject loop): skip mappings that have already failed
                // validation N times in a row for this parameter in this run.
                if (isMappingQuarantined(parameterInfo, traceMapping)) {
                    log.debug("Skipping quarantined trace-observed endpoint {} for '{}'", endpoint, paramName);
                    continue;
                }
                try {
                    String value = fetchFromApiMapping(traceMapping, parameterInfo);
                    if (value != null && !value.trim().isEmpty() && isValidValueForParameter(value, parameterInfo)) {
                        resetMappingFailure(parameterInfo, traceMapping);
                        cacheValue(parameterInfo, value);
                        log.info("Trace-Aware Fetch → {} = '{}' (from observed producer endpoint {})",
                                paramName, value, endpoint);
                        return value;
                    } else if (value != null && !value.trim().isEmpty()) {
                        // Value was fetched but failed validation — record for quarantine.
                        recordMappingFailure(parameterInfo, traceMapping);
                    }
                } catch (Exception e) {
                    log.debug("Trace-observed endpoint {} failed for '{}': {}", endpoint, paramName, e.getMessage());
                }
            }
            log.info("All trace-observed endpoints exhausted for '{}', falling back to registry", paramName);
        }

        // Bug audit Finding #15: scope mapping lookup by consumer API so two operations that
        // both have a parameter named e.g. {@code id} do not pollute each other's candidate
        // list. Falls back to global-tier mappings (legacy entries with no recorded scope).
        String consumerApiKey = parameterInfo.getApiName();
        List<ApiMapping> mappings = registry.getMappingsForParameter(consumerApiKey, paramName);
        log.info("Parameter '{}' (consumer='{}') has {} existing registry mappings (scoped+global)",
                paramName, consumerApiKey != null ? consumerApiKey : "<global>", mappings.size());

        // If no mappings found, try to discover new ones
        if (mappings.isEmpty() && config.isLlmDiscoveryEnabled()) {
            log.info("No existing mappings for '{}', attempting discovery...", paramName);
            mappings = discoverApiMappings(parameterInfo);
            log.info("Discovery for '{}' found {} new mappings", paramName, mappings.size());
        } else if (mappings.isEmpty()) {
            log.warn("No mappings for '{}' and discovery is disabled", paramName);
        }

        // Rank by score. Grounding fix B: a generic param↔producer name-affinity prior breaks COLD-START
        // ties (e.g. endStation -> stationservice, not a fresh high-priority trains discovery). It is applied
        // ONLY when no candidate has earned SUT feedback yet (all successRate≈0); once any producer has a real
        // successRate, calculateScore alone decides, so the prior can never override accumulated SUT truth.
        // Keys are precomputed once (not re-evaluated per comparison).
        final String affinityParam = parameterInfo.getName();
        final boolean coldStart = mappings.stream().mapToDouble(ApiMapping::getSuccessRate).max().orElse(0.0) < 1e-9;
        final java.util.Map<ApiMapping, Double> rankKey = new java.util.IdentityHashMap<>();
        for (ApiMapping m : mappings) {
            rankKey.put(m, rankingScore(m, affinityParam, coldStart));
        }
        for (ApiMapping mapping : mappings.stream()
                .sorted((a, b) -> Double.compare(rankKey.get(b), rankKey.get(a)))
                .limit(config.getMaxCandidates())
                .collect(Collectors.toList())) {

            // Bug fix (success-then-reject loop): skip mappings that have already failed
            // validation N times in a row for this parameter. Prevents the 122x re-fetch of
            // the same UUID seen in the trainticket run.
            if (isMappingQuarantined(parameterInfo, mapping)) {
                log.debug("Skipping quarantined mapping '{}' for parameter '{}'",
                        mapping.getEndpoint(), paramName);
                continue;
            }

            try {
                String value = fetchFromApiMapping(mapping, parameterInfo);
                if (value != null && !value.trim().isEmpty()) {
                    // Validate value before caching and returning
                    if (isValidValueForParameter(value, parameterInfo)) {
                        // Grounding fix B (de-poison): do NOT raise successRate on a local format-check pass.
                        // Format-validity is not producer-correctness — a train id passes the check but the SUT
                        // rejects it 400 as a station, and rewarding it here cemented the wrong producer at
                        // successRate≈0.99. successRate is therefore demote-only on this path (the failure
                        // branches below still lower it); raising it on a real SUT 2xx is future work (fix A,
                        // producer-keyed feedback — see debug/grounding/). Until then the cold-start name-affinity
                        // prior carries producer selection. Here we only cache + return the format-valid value.
                        registryDirty = true;
                        resetMappingFailure(parameterInfo, mapping);
                        cacheValue(parameterInfo, value);

                        log.debug("Successfully fetched valid value '{}' for parameter '{}' from {}",
                                 value, paramName, mapping.getEndpoint());
                        return value;
                    } else {
                        log.warn("Rejecting invalid value '{}' for parameter '{}' from {}",
                                value, paramName, mapping.getEndpoint());
                        mapping.updateSuccessRate(false);
                        registryDirty = true;
                        recordMappingFailure(parameterInfo, mapping);
                    }
                }
            } catch (Exception e) {
                log.debug("Failed to fetch from mapping {}: {}", mapping, e.getMessage());
                mapping.updateSuccessRate(false);
                registryDirty = true;
            }
        }

        // Grounding fix E: a parameter that already had an existing (possibly poisoned) registry
        // mapping never reached the discovery branch above — that only fires when the mapping list
        // is EMPTY. So e.g. a station param whose only stored mapping is a bad route-lookup endpoint
        // ({start}/{end} it cannot fill) would fall straight to the LLM and invent a fake station.
        // If every existing mapping just failed, try discovery as a last resort so the real producer
        // (e.g. /stations) can still be found and the value gets grounded to live SUT data.
        if (!mappings.isEmpty() && config.isLlmDiscoveryEnabled()) {
            log.info("All {} existing mappings failed for '{}'; attempting discovery fallback...",
                    mappings.size(), paramName);
            for (ApiMapping mapping : discoverApiMappings(parameterInfo).stream()
                    .sorted((a, b) -> Double.compare(b.calculateScore(), a.calculateScore()))
                    .limit(config.getMaxCandidates())
                    .collect(Collectors.toList())) {
                if (isMappingQuarantined(parameterInfo, mapping)) continue;
                try {
                    String value = fetchFromApiMapping(mapping, parameterInfo);
                    if (value != null && !value.trim().isEmpty()
                            && isValidValueForParameter(value, parameterInfo)) {
                        mapping.updateSuccessRate(true);
                        registryDirty = true;
                        resetMappingFailure(parameterInfo, mapping);
                        cacheValue(parameterInfo, value);
                        log.info("Discovery-fallback grounded '{}' = {} via {}", paramName, value, mapping.getEndpoint());
                        return value;
                    }
                } catch (Exception e) {
                    log.debug("Discovery-fallback fetch failed for {}: {}", mapping, e.getMessage());
                }
            }
        }

        // If all smart sources failed, return null. The caller already handles a null result
        // by falling back to LLM and logging "FAILED (no good matches found)". Using an
        // exception for ordinary control flow is wasteful (Bug audit Finding #8).
        return null;
    }

    /**
     * Discover new API mappings using LLM and pattern matching
     */
    private List<ApiMapping> discoverApiMappings(ParameterInfo parameterInfo) {
        List<ApiMapping> discoveries = new ArrayList<>();
        String paramName = parameterInfo.getName();

        try {
            log.info("🔍 Starting discovery for parameter '{}'", paramName);

            // Pattern discovery was a no-op stub that always returned an empty list and
            // emitted misleading warning logs (Bug audit Finding #32 + Reviewer C12). It is
            // now removed; LLM-based discovery is the only supported discovery path.
            if (config.isLlmDiscoveryEnabled()) {
                List<ApiMapping> llmMappings = discoverByLLM(parameterInfo);
                discoveries.addAll(llmMappings);
                log.info("🤖 LLM discovery for '{}' found {} mappings", paramName, llmMappings.size());
            } else {
                log.info("🚫 LLM discovery disabled for '{}'", paramName);
            }

            // 3. Save discovered mappings to registry, scoped by consumer (Bug audit Finding #15).
            String consumerApiKey = parameterInfo.getApiName();
            for (ApiMapping mapping : discoveries) {
                registry.addMapping(consumerApiKey, parameterInfo.getName(), mapping);
                log.info("💾 Saved mapping for '{}' (consumer='{}'): {} -> {}",
                        paramName, consumerApiKey != null ? consumerApiKey : "<global>",
                        mapping.getService(), mapping.getEndpoint());
            }

            if (!discoveries.isEmpty()) {
                // Bug audit Finding #14 finish: mark dirty rather than write immediately;
                // flushIfDirty handles persistence on scenario boundary / shutdown.
                registryDirty = true;
                log.info("✅ Registry updated with {} new mappings for '{}' (deferred save)",
                        discoveries.size(), paramName);
            } else {
                log.warn("❌ No mappings discovered for parameter '{}'", paramName);
            }

        } catch (Exception e) {
            log.warn("💥 Discovery failed for parameter '{}': {}", parameterInfo.getName(), e.getMessage(), e);
        }

        return discoveries;
    }

    /**
     * Get all available services from both registry and OpenAPI specification
     */
    private List<String> getAllAvailableServices() {
        Set<String> allServices = new HashSet<>();

        // Add services from registry
        allServices.addAll(registry.getAllServices());

        // Add services from OpenAPI specification
        if (openAPIDiscovery != null && openAPIDiscovery.isLoaded()) {
            allServices.addAll(openAPIDiscovery.getAllServices());
        }

        return new ArrayList<>(allServices);
    }

    /**
     * Discover APIs using LLM analysis.
     *
     * <p>Validates each LLM-suggested service against the known service set before persisting
     * (Bug audit Findings #2, #4, #22): the literal sentinel {@code NO_GOOD_MATCH} is
     * stripped, names are lowercase-normalized for canonical lookup, and any name not in
     * the known service set is dropped. This prevents the registry from accumulating
     * entries like {@code service: "NO_GOOD_MATCH"} or capitalization variants
     * ({@code ts-Travel-Service} vs {@code ts-travel-service}).</p>
     */
    private List<ApiMapping> discoverByLLM(ParameterInfo parameterInfo) {
        List<ApiMapping> mappings = new ArrayList<>();

        try {
            // Get available services from both registry and OpenAPI spec
            List<String> availableServices = getAllAvailableServices();

            // Ask LLM which services might provide data for this parameter
            String prompt = buildLLMDiscoveryPrompt(parameterInfo, availableServices);
            List<String> suggestedServices = askLLMForServices(prompt);

            if (suggestedServices.isEmpty()) {
                log.info("LLM indicated no good service matches for parameter '{}', skipping LLM discovery",
                        parameterInfo.getName());
                return mappings;
            }

            // Build a lowercase lookup of known services for whitelist validation.
            java.util.Map<String, String> knownByLower = new java.util.HashMap<>();
            for (String s : availableServices) {
                if (s != null && !s.trim().isEmpty()) {
                    knownByLower.putIfAbsent(s.toLowerCase(java.util.Locale.ROOT), s);
                }
            }

            int rank = 0;
            for (String rawService : suggestedServices) {
                if (rawService == null) continue;
                String trimmed = rawService.trim();
                // Bug audit Finding #2: drop the LLM sentinel "NO_GOOD_MATCH" before persisting.
                if (trimmed.isEmpty() || trimmed.equalsIgnoreCase("NO_GOOD_MATCH")) {
                    log.debug("Skipping LLM-suggested sentinel/empty service: '{}'", rawService);
                    continue;
                }
                // Bug audit Findings #4 + #22: whitelist + lowercase canonicalization.
                // Grounding fix C: accept stem/fuzzy matches too — LLMs emit descriptive names like
                // "stationservice" that never exact-match terse known names like "ts-station-service".
                // Still rejects genuinely-unknown services (the stem must overlap a real known one),
                // so the cross-SUT-hallucination guard the whitelist was added for is preserved.
                String low = trimmed.toLowerCase(java.util.Locale.ROOT);
                String canonical = knownByLower.get(low);
                if (canonical == null) {
                    canonical = fuzzyMatchService(low, knownByLower);
                }
                if (canonical == null) {
                    log.warn("LLM suggested unknown service '{}' for parameter '{}'; dropping (whitelist enforcement)",
                            trimmed, parameterInfo.getName());
                    continue;
                }
                String endpoint = inferEndpointForService(canonical, parameterInfo);
                // Bug audit Finding #33 + reviewer C7: when the picker fails, return null
                // and skip the service rather than fabricating "/api/v1/<svc>/query".
                if (endpoint == null) {
                    log.debug("No reasonable endpoint for service '{}'; skipping mapping for '{}'",
                            canonical, parameterInfo.getName());
                    continue;
                }
                ApiMapping mapping = new ApiMapping(endpoint, canonical, "DIRECT_EXTRACTION");
                int priority = config.getLlmDiscoveryPriority() + rank;
                mapping.setPriority(priority);
                applyMappingTuning(mapping);
                mappings.add(mapping);

                log.info("LLM-discovered mapping #{}: {} -> {} {} (priority: {}) [DIRECT]",
                        rank + 1, parameterInfo.getName(), canonical, endpoint, priority);
                rank++;
            }

        } catch (Exception e) {
            log.warn("LLM discovery failed: {}", e.getMessage());
        }

        return mappings;
    }

    /**
     * Grounding fix C: match an LLM-suggested service name to a known one by stem
     * (drop {@code ts-} prefix, {@code service/svc/srv} suffix, and hyphens), so
     * {@code "stationservice"} matches {@code "ts-station-service"}. Returns the canonical
     * known service name, or null if no real stem overlap (keeps the hallucination guard).
     */
    private static String fuzzyMatchService(String suggestedLower, java.util.Map<String, String> knownByLower) {
        String sStem = stemService(suggestedLower);
        if (sStem.length() < 3) return null;
        for (java.util.Map.Entry<String, String> e : knownByLower.entrySet()) {
            String kStem = stemService(e.getKey());
            if (kStem.length() < 3) continue;
            if (kStem.equals(sStem) || kStem.contains(sStem) || sStem.contains(kStem)) {
                return e.getValue();
            }
        }
        return null;
    }

    private static String stemService(String lower) {
        return lower.replace("ts-", "").replace("-service", "").replace("service", "")
                    .replace("svc", "").replace("srv", "").replace("-", "").trim();
    }

    /** Weight of the param↔producer name-affinity prior in cold-start producer ranking (grounding fix B). */
    static final double NAME_AFFINITY_WEIGHT = 0.3;

    /** Path/version noise tokens that must not count as a name match. */
    private static final java.util.Set<String> AFFINITY_NOISE = java.util.Set.of("api", "www", "rest");

    /**
     * Producer-ranking key: base {@link ApiMapping#calculateScore()} plus, ONLY at cold-start (no candidate
     * has earned SUT feedback), a generic name-affinity prior. Gating to cold-start guarantees the prior breaks
     * ties but never overrides an accumulated successRate signal once execution feedback exists (fix A).
     */
    static double rankingScore(ApiMapping mapping, String paramName, boolean coldStart) {
        double base = mapping.calculateScore();
        return coldStart ? base + NAME_AFFINITY_WEIGHT * nameAffinity(paramName, mapping) : base;
    }

    /**
     * Generic param↔producer name affinity in [0,1]: the fraction of the parameter's normalised name tokens
     * that also appear (by token EQUALITY, not substring) in the candidate producer's service+endpoint tokens.
     * Both sides are normalised identically (see {@link #normTokens}), so "endStation" -> {end, station} matches
     * a "stationservice"/"stations" producer but not "trains" or "vendors". No SUT-specific hardcoding — the
     * heuristic a human uses ("a station parameter is served by the station service"). Cold-start tie-breaker only.
     */
    static double nameAffinity(String paramName, ApiMapping mapping) {
        if (paramName == null || mapping == null) return 0.0;
        java.util.Set<String> paramTokens = normTokens(paramName);
        if (paramTokens.isEmpty()) return 0.0;
        String svc = mapping.getService() == null ? "" : mapping.getService();
        String ep = mapping.getEndpoint() == null ? "" : mapping.getEndpoint();
        java.util.Set<String> producerTokens = normTokens(svc + " " + ep);
        int matched = 0;
        for (String t : paramTokens) {
            if (producerTokens.contains(t)) matched++;
        }
        return (double) matched / paramTokens.size();
    }

    /**
     * Split a camelCase / snake / kebab / path string into normalised lowercase tokens: strip the
     * {@code service}/{@code svc}/{@code srv} suffix and a trailing plural 's', drop path/version noise and
     * tokens shorter than 3 chars. Applied symmetrically to the parameter name and the producer service+endpoint
     * so equality matching works across "endStation"/"stationservice"/"stations" (grounding fix B).
     */
    static java.util.Set<String> normTokens(String s) {
        java.util.Set<String> out = new java.util.LinkedHashSet<>();
        if (s == null) return out;
        String spaced = s.replaceAll("([a-z0-9])([A-Z])", "$1 $2").replaceAll("[^A-Za-z0-9]+", " ");
        for (String part : spaced.trim().split("\\s+")) {
            String low = part.toLowerCase().replace("service", "").replace("svc", "").replace("srv", "");
            if (low.endsWith("s") && low.length() > 3) low = low.substring(0, low.length() - 1);
            if (low.length() >= 3 && !AFFINITY_NOISE.contains(low)) out.add(low);
        }
        return out;
    }

    /**
     * Fetch data from a specific API mapping. The endpoint is resolved via
     * {@link #resolveFetchableEndpoint} which strips trailing {@code /{...}} segments so
     * that path-templated registry rows like {@code /api/v1/orderservice/order/{orderId}}
     * fall back to the collection endpoint {@code /api/v1/orderservice/order} (Bug audit
     * Finding #3). If no fetchable endpoint can be resolved (e.g. {@code /{paramName}} mid-path)
     * the call returns {@code null} and the caller drops this candidate.
     */
    private String fetchFromApiMapping(ApiMapping mapping, ParameterInfo parameterInfo) throws Exception {
        String resolved = resolveFetchableEndpoint(mapping.getEndpoint());
        if (resolved == null) {
            log.warn("Skipping mapping with unresolvable path placeholders: '{}' (param='{}')",
                    mapping.getEndpoint(), parameterInfo.getName());
            return null;
        }
        String url = baseUrl + resolved;

        // Bug audit Finding #23 follow-up: honor the mapping's recorded method instead of
        // hardcoding "GET". The write-side {@code InputFetchRegistry.addMapping} already
        // rejects non-GET registrations, so in practice every persisted mapping is GET.
        // This makes the read-path consistent with the write-path contract and surfaces
        // any future inconsistency through {@code conn.setRequestMethod} directly.
        String httpMethod = mapping.getMethod() != null && !mapping.getMethod().isEmpty()
                ? mapping.getMethod().toUpperCase(java.util.Locale.ROOT) : "GET";
        if (!"GET".equals(httpMethod)) {
            log.warn("Mapping for parameter '{}' has non-GET method '{}'; smart-fetch enforces GET only",
                    parameterInfo.getName(), httpMethod);
            httpMethod = "GET";
        }
        log.info("🌐 API Call: {} {} for parameter '{}'", httpMethod, url, parameterInfo.getName());

        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod(httpMethod);
        conn.setRequestProperty("Content-Type", config.getDefaultContentType());

        // Add authentication headers
        if (authManager.isConfigured()) {
            authManager.addAuthHeaders(conn);
            log.debug("🔐 Added authentication headers to API request");
        } else {
            log.warn("⚠️ Authentication not configured, API call may fail with 403");
        }
        conn.setConnectTimeout((int) config.getConnectTimeoutMs());
        conn.setReadTimeout((int) config.getReadTimeoutMs());

        try {
            int responseCode = conn.getResponseCode();
            if (responseCode != config.getSuccessResponseCode()) {
                // Bug audit Finding #26: auto-invalidate the cached JWT on 401/403 so the
                // next attempt re-logs in instead of reusing a stale token.
                if ((responseCode == 401 || responseCode == 403) && authManager.isConfigured()) {
                    log.warn("Authentication response {} from {} → invalidating JWT for next attempt",
                            responseCode, url);
                    authManager.invalidateToken();
                }
                log.warn("❌ API call failed with HTTP {}: {}", responseCode, url);
                throw new Exception("HTTP " + responseCode + " from " + url);
            }

            // Read response
            String responseBody = readResponse(conn);
            log.debug("API response preview: {}", responseBody.substring(0, Math.min(200, responseBody.length())));

            // Validate response quality
            if (!isValidApiResponse(responseBody, parameterInfo)) {
                log.warn("❌ Invalid API response from {}", url);
                return null;
            }

            // Use direct value extraction instead of JSONPath
            String result = extractValueDirectlyFromResponse(responseBody, parameterInfo);
            if (result != null && !result.trim().isEmpty()) {
                // Bug fix (success-then-reject loop): this is a CANDIDATE value pre-validation.
                // The caller (fetchFromSmartSource / trace-aware loop) still runs
                // {@link #isValidValueForParameter} and may reject it. Logging
                // "Smart Fetch Success" here was misleading and produced 122 rejected-after-
                // success entries in a single run. The true success log lives on the caller.
                log.info("🔍 Smart Fetch Candidate: {} = '{}' (from {}) — awaiting validation",
                        parameterInfo.getName(), result, mapping.getService());
                return result;
            } else {
                log.warn("❌ No valid value extracted for parameter '{}'", parameterInfo.getName());
                return null;
            }

        } finally {
            conn.disconnect();
        }
    }

    /**
     * Resolve a registry-stored endpoint into a fetchable URL path. Endpoints persisted with
     * trailing path placeholders (e.g. {@code /api/v1/orderservice/order/{orderId}}) cannot
     * be GET'd as-is; we strip the trailing {@code /{...}} segments to recover the collection
     * endpoint. Mid-path placeholders like {@code /api/v1/foo/{x}/bar} cannot be safely
     * resolved without value substitution; for those we return {@code null} so the caller
     * drops the candidate (Bug audit Finding #3 + reviewer Comment 9).
     *
     * @param endpoint the persisted endpoint string; may contain {@code {placeholder}} segments
     * @return a placeholder-free path suitable for {@code baseUrl + path}, or {@code null}
     *         if the placeholders cannot be safely stripped
     */
    static String resolveFetchableEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isEmpty()) return endpoint;
        if (endpoint.indexOf('{') < 0) return endpoint;
        // Strip trailing /{...} segments greedily, but only at the end of the path.
        String stripped = endpoint;
        while (true) {
            int lastSlash = stripped.lastIndexOf('/');
            if (lastSlash < 0) break;
            String tail = stripped.substring(lastSlash + 1);
            if (tail.startsWith("{") && tail.endsWith("}")) {
                stripped = stripped.substring(0, lastSlash);
            } else {
                break;
            }
        }
        if (stripped.indexOf('{') >= 0) {
            return null;
        }
        return stripped.isEmpty() ? null : stripped;
    }

    /**
     * Validate that an API response contains useful data.
     * Uses a structured JSON check first (looks for explicit failure flags such as
     * {@code "status": 0}, {@code "success": false}, {@code "error": true}, or a non-empty
     * top-level {@code "error"} message). Falls back to a conservative whole-document substring
     * scan only when the body is not parseable as JSON. The previous unconditional substring
     * scan for {@code "error"}/{@code "exception"} rejected legitimate responses whose data
     * fields contained those words (e.g. {@code "errorCode": 0}).
     */
    private boolean isValidApiResponse(String responseBody, ParameterInfo parameterInfo) {
        if (responseBody == null || responseBody.trim().isEmpty()) {
            return false;
        }

        String trimmed = responseBody.trim();
        if (trimmed.equals("{}") || trimmed.equals("[]")) {
            log.debug("API response is an empty document");
            return false;
        }
        // Removed the legacy {@code length() < 20} floor (Bug audit Finding #7) — short
        // payloads like {@code {"data":[1,2]}} are legitimate and the structured-failure
        // checks below already catch real error envelopes.

        // Structured JSON inspection — only checks the top-level envelope for failure flags.
        if (trimmed.startsWith("{")) {
            try {
                org.json.JSONObject obj = new org.json.JSONObject(trimmed);
                if (isExplicitFailureEnvelope(obj)) {
                    log.debug("API response is an explicit failure envelope: {}",
                            trimmed.length() > 200 ? trimmed.substring(0, 200) + "..." : trimmed);
                    return false;
                }
                // Common envelope shapes — { "status": 1, "data": [] } /
                // { "result": [] } / { "payload": null } / { "items": [] } — empty payload
                // is a soft miss (Bug audit Finding #37). Fresh-review Finding F7 also
                // rejects {@code "data": null} (was previously accepted, leaving extraction
                // to fail downstream with no signal).
                for (String envelopeKey : new String[] {"data", "result", "payload", "items"}) {
                    if (!obj.has(envelopeKey)) continue;
                    Object payload = obj.opt(envelopeKey);
                    if (payload == null || payload == org.json.JSONObject.NULL) {
                        log.debug("API response has null {} payload", envelopeKey);
                        return false;
                    }
                    if (payload instanceof org.json.JSONArray
                            && ((org.json.JSONArray) payload).isEmpty()) {
                        log.debug("API response has empty {} array", envelopeKey);
                        return false;
                    }
                    if (payload instanceof org.json.JSONObject
                            && ((org.json.JSONObject) payload).isEmpty()) {
                        log.debug("API response has empty {} object", envelopeKey);
                        return false;
                    }
                }
                return true;
            } catch (org.json.JSONException ignored) {
                // Fall through to conservative substring check below.
            }
        }

        // Non-JSON or unparseable: be conservative and only reject documents that look like
        // error pages / status messages, not arbitrary payloads.
        String lower = trimmed.toLowerCase(java.util.Locale.ROOT);
        if (lower.startsWith("error") || lower.startsWith("exception")
                || lower.contains("internal server error") || lower.contains("not found")
                || lower.contains("unauthorized") || lower.contains("forbidden")) {
            return false;
        }

        return true;
    }

    /**
     * True when a JSON object explicitly declares a failure outcome at the top level
     * (the only level where these keys carry envelope semantics).
     */
    private boolean isExplicitFailureEnvelope(org.json.JSONObject obj) {
        if (obj.has("success") && !obj.optBoolean("success", true)) return true;
        if (obj.has("error")) {
            Object err = obj.opt("error");
            if (err instanceof Boolean && (Boolean) err) return true;
            if (err instanceof String && !((String) err).isEmpty()) return true;
        }
        if (obj.has("hasError") && obj.optBoolean("hasError", false)) return true;
        // status==0 / status=="error" / status=="false" are common Spring-style envelopes.
        if (obj.has("status")) {
            Object s = obj.opt("status");
            if (s instanceof Number && ((Number) s).intValue() == 0) return true;
            if (s instanceof Boolean && !((Boolean) s)) return true;
            if (s instanceof String) {
                String sv = ((String) s).toLowerCase(java.util.Locale.ROOT);
                if (sv.equals("error") || sv.equals("false") || sv.equals("0")) return true;
            }
        }
        return false;
    }

    /**
     * Extract value directly from API response using LLM
     */
    private String extractValueDirectlyFromResponse(String responseBody, ParameterInfo parameterInfo) {
        try {
            log.info("🔄 Starting DIRECT VALUE EXTRACTION for parameter '{}'", parameterInfo.getName());

            // Build direct extraction prompt
            String prompt = buildDirectExtractionPrompt(responseBody, parameterInfo);

            if (prompt.length() > config.getMaxPromptChars()) {
                log.warn("Direct extraction prompt too long ({} chars), using fallback", prompt.length());
                return extractValueWithSimpleFallback(responseBody, parameterInfo);
            }

            log.info("🧠 Calling LLM for DIRECT VALUE EXTRACTION (not JSONPath) for parameter '{}'", parameterInfo.getName());

            // Ask LLM to extract value directly
            String llmResponse = askLLMForDirectValueExtraction(prompt);

            if (llmResponse != null && !llmResponse.trim().isEmpty()) {
                String cleanResponse = llmResponse.trim();

                // Check for NO_GOOD_MATCH response
                if (cleanResponse.equals("NO_GOOD_MATCH")) {
                    log.info("LLM indicated no good value match for parameter '{}'", parameterInfo.getName());
                    return extractValueWithSimpleFallback(responseBody, parameterInfo);
                }

                // Check if LLM incorrectly returned a JSONPath expression
                if (cleanResponse.startsWith("$.") || cleanResponse.contains("$[") || cleanResponse.contains("data[")) {
                    log.error("❌ CRITICAL BUG: LLM returned JSONPath expression '{}' instead of actual value for parameter '{}'",
                             cleanResponse, parameterInfo.getName());
                    log.error("❌ This should NEVER happen with direct value extraction!");
                    log.error("❌ Using fallback extraction instead");
                    return extractValueWithSimpleFallback(responseBody, parameterInfo);
                }

                // Validate LLM response quality before using it
                if (!isValidLLMResponse(cleanResponse, parameterInfo)) {
                    log.warn("❌ LLM response '{}' is invalid for parameter '{}', using fallback",
                            cleanResponse, parameterInfo.getName());
                    return extractValueWithSimpleFallback(responseBody, parameterInfo);
                }

                log.info("✅ LLM extracted ACTUAL VALUE '{}' for parameter '{}' (not JSONPath)", cleanResponse, parameterInfo.getName());

                // Format the value according to OpenAPI schema
                String formattedValue = formatValueForSchema(cleanResponse, parameterInfo);
                log.info("🔧 Formatted value '{}' → '{}' for parameter '{}'", cleanResponse, formattedValue, parameterInfo.getName());

                // Try to extract additional diverse values from the same response
                extractAdditionalDiverseValues(responseBody, parameterInfo, cleanResponse);

                return formattedValue;
            }

            // Fallback if LLM fails
            return extractValueWithSimpleFallback(responseBody, parameterInfo);

        } catch (Exception e) {
            log.debug("Direct value extraction failed for parameter '{}': {}",
                     parameterInfo.getName(), e.getMessage());
            return extractValueWithSimpleFallback(responseBody, parameterInfo);
        }
    }

    /**
     * Build prompt for direct value extraction
     */
    private String buildDirectExtractionPrompt(String responseBody, ParameterInfo parameterInfo) {
        String template = registry.getLlmPrompts().get("directValueExtraction");

        // Handle message length limit for response body
        String truncatedResponse = truncateResponseSchemaForLLM(responseBody, template, parameterInfo);

        String paramName = parameterInfo.getName() != null ? parameterInfo.getName() : "";
        String paramType = parameterInfo.getType() != null ? parameterInfo.getType() : "";
        String paramDesc = parameterInfo.getDescription() != null ? parameterInfo.getDescription() : "";

        return template
                .replace("{responseSchema}", truncatedResponse != null ? truncatedResponse : "")
                .replace("{parameterName}", paramName)
                .replace("{parameterType}", paramType)
                .replace("{parameterDescription}", paramDesc)
                // Handle multiple parameter name references
                .replaceAll("\\{parameterName\\}", paramName);
    }

    /**
     * Ask LLM for direct value extraction
     */
    private String askLLMForDirectValueExtraction(String prompt) {
        try {
            // Call LLM for direct value extraction (NOT JSONPath generation)
            String rawResponse = callLLMForDirectValueExtractionFromResponse(prompt);

            if (rawResponse != null && !rawResponse.trim().isEmpty()) {
                String cleaned = cleanJsonFromMarkdown(rawResponse);

                // Check for NO_GOOD_MATCH response first
                if (cleaned.trim().equals("NO_GOOD_MATCH")) {
                    return "NO_GOOD_MATCH";
                }

                // Remove quotes if the LLM wrapped the value in quotes
                if (cleaned.startsWith("\"") && cleaned.endsWith("\"")) {
                    cleaned = cleaned.substring(1, cleaned.length() - 1);
                }

                return cleaned.trim();
            }

            return null;
        } catch (Exception e) {
            log.debug("LLM direct value extraction failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Simple fallback value extraction when LLM fails
     */
    private String extractValueWithSimpleFallback(String responseBody, ParameterInfo parameterInfo) {
        try {
            // Parse JSON and try to find any reasonable value
            Object parsed = objectMapper.readValue(responseBody, Object.class);

            if (parsed instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) parsed;

                // Look for data array
                if (map.containsKey("data") && map.get("data") instanceof List) {
                    List<?> dataList = (List<?>) map.get("data");
                    if (!dataList.isEmpty() && dataList.get(0) instanceof Map) {
                        Map<?, ?> firstItem = (Map<?, ?>) dataList.get(0);

                        // Try to find a reasonable field based on parameter name
                        String paramName = parameterInfo.getName() != null ? parameterInfo.getName().toLowerCase() : "";

                        // Direct field name match
                        for (Map.Entry<?, ?> entry : firstItem.entrySet()) {
                            String fieldName = entry.getKey().toString().toLowerCase();
                            if (fieldName.contains(paramName) || paramName.contains(fieldName)) {
                                Object value = entry.getValue();
                                if (value != null) {
                                    log.info("📋 Fallback extracted '{}' from field '{}' for parameter '{}'",
                                            value, entry.getKey(), parameterInfo.getName());
                                    // Populate diverse cache from this same response to avoid repeats
                                    try {
                                        extractAdditionalDiverseValues(responseBody, parameterInfo, value.toString());
                                    } catch (Exception ignore) { }
                                    return value.toString();
                                }
                            }
                        }

                        // Semantic matching fallback with validation
                        String fallbackValue = findSemanticMatch(firstItem, paramName);
                        if (fallbackValue != null) {
                            // ✅ CRITICAL FIX: Validate the fallback value before accepting it
                            if (isValidValueForParameter(fallbackValue, parameterInfo)) {
                                log.info("📋 Fallback found valid semantic match '{}' for parameter '{}'",
                                        fallbackValue, parameterInfo.getName());
                                // Populate diverse cache from the same response so subsequent tests rotate values
                                try {
                                    extractAdditionalDiverseValues(responseBody, parameterInfo, fallbackValue);
                                } catch (Exception ignore) { }
                                return fallbackValue;
                            } else {
                                log.warn("❌ Fallback found invalid semantic match '{}' for parameter '{}' - rejecting",
                                        fallbackValue, parameterInfo.getName());
                                // Continue to LLM generation instead of using invalid value
                            }
                        }

                        // Last resort: Ask LLM to generate a meaningful value instead of using random strings
                        log.info("📋 No suitable values found in API response, asking LLM to generate meaningful value for parameter '{}'", parameterInfo.getName());
                        String llmGeneratedValue = generateValueWithLLM(parameterInfo);
                        if (llmGeneratedValue != null && !llmGeneratedValue.trim().isEmpty()) {
                            log.info("✅ LLM generated meaningful value '{}' for parameter '{}'", llmGeneratedValue, parameterInfo.getName());
                            // Also try to enrich cache from response for future diversity
                            try {
                                extractAdditionalDiverseValues(responseBody, parameterInfo, llmGeneratedValue);
                            } catch (Exception ignore) { }
                            return llmGeneratedValue;
                        }

                        // Final fallback: return first valid string value only if LLM fails
                        for (Map.Entry<?, ?> entry : firstItem.entrySet()) {
                            Object value = entry.getValue();
                            if (value != null && value instanceof String && !value.toString().trim().isEmpty()) {
                                String stringValue = value.toString();
                                // ✅ CRITICAL FIX: Validate even the last resort value
                                if (isValidValueForParameter(stringValue, parameterInfo)) {
                                    log.warn("📋 LLM generation failed, using first valid string value '{}' for parameter '{}' as last resort",
                                            stringValue, parameterInfo.getName());
                                    return stringValue;
                                } else {
                                    log.debug("❌ Rejecting invalid last resort value '{}' for parameter '{}'",
                                             stringValue, parameterInfo.getName());
                                }
                            }
                        }
                    }
                }
            }

        } catch (Exception e) {
            log.debug("Fallback extraction failed: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Find semantic matches using LLM-based analysis instead of hardcoded rules
     */
    private String findSemanticMatch(Map<?, ?> data, String paramName) {
        try {
            // Use LLM to find semantically relevant fields in the data
            String semanticMatch = askLLMForSemanticFieldMatching(data, paramName);

            if (semanticMatch != null && !semanticMatch.trim().isEmpty()) {
                // Try to extract the suggested field from data
                Object value = data.get(semanticMatch.trim());
                if (value != null) {
                    log.debug("LLM found semantic match: field '{}' → value '{}' for parameter '{}'",
                             semanticMatch, value, paramName);
                    return value.toString();
                }

                // Try case-insensitive matching
                for (Map.Entry<?, ?> entry : data.entrySet()) {
                    if (entry.getKey() != null &&
                        entry.getKey().toString().equalsIgnoreCase(semanticMatch.trim())) {
                        log.debug("LLM found semantic match (case-insensitive): field '{}' → value '{}' for parameter '{}'",
                                 entry.getKey(), entry.getValue(), paramName);
                        return entry.getValue().toString();
                    }
                }
            }

            return null;

        } catch (Exception e) {
            log.debug("LLM semantic matching failed for parameter '{}': {}", paramName, e.getMessage());
            return null;
        }
    }

    /**
     * Ask LLM to find semantically relevant fields in data for a parameter
     */
    private String askLLMForSemanticFieldMatching(Map<?, ?> data, String paramName) {
        try {
            StringBuilder prompt = new StringBuilder();

            prompt.append("Find the most semantically relevant field in this data for the parameter '").append(paramName).append("':\n\n");

            prompt.append("Available fields and their values:\n");
            for (Map.Entry<?, ?> entry : data.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    String fieldName = entry.getKey().toString();
                    String fieldValue = entry.getValue().toString();

                    // Truncate long values for readability
                    if (fieldValue.length() > 50) {
                        fieldValue = fieldValue.substring(0, 47) + "...";
                    }

                    prompt.append("- ").append(fieldName).append(": ").append(fieldValue).append("\n");
                }
            }

            prompt.append("\nParameter: ").append(paramName).append("\n\n");

            prompt.append("Instructions:\n");
            prompt.append("1. Find the field that is most semantically related to the parameter\n");
            prompt.append("2. Consider meaning, context, and domain relevance\n");
            prompt.append("3. Consider the VALUE TYPE - don't match UUIDs to distance/numeric parameters\n");
            prompt.append("4. For distance/numeric parameters, only match numeric fields\n");
            prompt.append("5. For ID parameters, prefer UUID or numeric ID fields\n");
            prompt.append("6. Return ONLY the field name, nothing else\n");
            prompt.append("7. If no relevant field exists, respond with: NO_MATCH\n\n");

            prompt.append("Examples:\n");
            prompt.append("Parameter 'origin' → field 'from' (if from contains matching values)\n");
            prompt.append("Parameter 'destination' → field 'to' (if to contains matching values)\n");
            prompt.append("Parameter 'userId' → field 'accountId' (if accountId contains IDs)\n");
            prompt.append("Parameter 'distance' → field 'price' (if price contains numbers, not UUIDs)\n");
            prompt.append("Parameter 'itemId' → field 'itemNumber' (if itemNumber contains matching IDs)\n\n");

            prompt.append("Which field is most relevant for parameter '").append(paramName).append("'?");

            // Bug fix (stale 1200-char cap): raised from 1200 to 30000. The static template
            // alone runs ~1100-1500 chars, so the previous threshold rejected nearly every
            // realistic prompt (12 silent skips observed at 1241–1467 chars). 30000 chars
            // (~7.5k tokens) is well within any modern model's context window while still
            // catching pathologically large payloads.
            if (prompt.length() > 30000) {
                log.warn("Semantic matching prompt too long ({} chars), skipping LLM", prompt.length());
                return null;
            }

            String systemContent = "You are a semantic field matching expert. Find the most relevant field in the data for the given parameter. " +
                                  "Consider both semantic meaning AND value type compatibility. Never match UUID values to numeric parameters.";

            String result = llmService.generateText(systemContent, prompt.toString(), 10, 0.2);

            if (result != null && !result.trim().isEmpty() && !result.trim().equals("NO_MATCH")) {
                log.debug("[Semantic Matching LLM] Found field: {}", result.trim());
                return result.trim();
            }

            return null;

        } catch (Exception e) {
            log.debug("LLM semantic field matching failed: {}", e.getMessage());
            return null;
        }
    }

    // extractValueFromResponse, selectValueWithFallbackLogic, selectValueWithLLM,
    // buildValueSelectionPrompt, truncateDataForLLM, askLLMForValueSelection,
    // callLLMForValueSelection removed — JSONPath is fully retired in favor of direct LLM
    // extraction (extractValueDirectlyFromResponse). truncateJsonIntelligently below is
    // kept because truncateResponseSchemaForLLM still calls it.
    // (Bug audit Findings #9, #18, #29; Reviewer Comment 14.)

    /**
     * Intelligently truncate JSON to keep complete objects/arrays when possible.
     * Used by {@code truncateResponseSchemaForLLM} when an API response exceeds the
     * configured prompt budget.
     */
    private String truncateJsonIntelligently(String jsonData, int maxLength) {
        if (jsonData.length() <= maxLength) return jsonData;

        try {
            // Parse JSON to understand structure
            Object parsed = objectMapper.readValue(jsonData, Object.class);

            if (parsed instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) parsed;
                // For response objects, try to keep the data array with fewer items
                if (map.containsKey("data") && map.get("data") instanceof List) {
                    List<?> dataList = (List<?>) map.get("data");

                    // Keep only first 1-2 items from data array to show structure
                    List<Object> truncatedData = new ArrayList<>();
                    for (int i = 0; i < Math.min(2, dataList.size()); i++) {
                        truncatedData.add(dataList.get(i));
                    }

                    // Rebuild response with truncated data
                    Map<String, Object> truncatedResponse = new HashMap<>();
                    truncatedResponse.put("status", map.get("status"));
                    truncatedResponse.put("msg", "truncated");
                    truncatedResponse.put("data", truncatedData);

                    String result = objectMapper.writeValueAsString(truncatedResponse);
                    if (result.length() <= maxLength) {
                        return result;
                    }

                    // If still too long, keep only 1 item
                    truncatedData = truncatedData.subList(0, Math.min(1, truncatedData.size()));
                    truncatedResponse.put("data", truncatedData);
                    return objectMapper.writeValueAsString(truncatedResponse);
                }
            }

            // Fallback to simple truncation
            return jsonData.substring(0, Math.min(maxLength - 3, jsonData.length())) + "...";

        } catch (Exception e) {
            // Fallback to simple truncation
            return jsonData.substring(0, Math.min(maxLength - 3, jsonData.length())) + "...";
        }
    }

    /**
     * Fallback to traditional LLM generation with validation
     */
    private String fallbackToLLM(ParameterInfo parameterInfo) {
        try {
            List<String> values = llmGenerator.generateParameterValues(parameterInfo);

            if (values.isEmpty()) {
                log.warn("LLM returned empty values list for parameter '{}'", parameterInfo.getName());
                String appropriateFallback = generateMinimalFallbackValue(parameterInfo);
                log.info("Empty Values Fallback → {} = {}", parameterInfo.getName(), appropriateFallback);
                return appropriateFallback;
            }

            // Check if this is an array parameter
            String schemaType = getOpenAPISchemaType(parameterInfo);
            boolean isArrayParameter = "array".equals(schemaType);

            if (isArrayParameter) {
                // For array parameters, process all values and return as array
                return handleArrayParameterFromLLM(parameterInfo, values);
            } else {
                // For single-value parameters:
                // 1) Clean and validate all returned values
                // 2) Cache them as diverse options
                // 3) Return a rotated value to avoid repeating the same choice across tests
                // Fresh-review Finding F9: single get + null check instead of containsKey-then-get
                // (the latter is a TOCTOU race on a synchronized map: another thread could
                // remove() the entry between containsKey and get, causing a NPE on size()).
                int cachedCountBefore = 0;
                String cacheKey = buildCacheKey(parameterInfo);
                List<String> existingDiverse = diverseValueCache.get(cacheKey);
                if (existingDiverse != null) {
                    synchronized (existingDiverse) { cachedCountBefore = existingDiverse.size(); }
                }

                List<String> validCleanedValues = new ArrayList<>();
                for (String value : values) {
                    if (value == null) continue;
                    String cleaned = cleanJsonFromMarkdown(value);
                    if (cleaned == null || cleaned.trim().isEmpty() || cleaned.equals("json") || cleaned.startsWith("```")) {
                        continue;
                    }
                    String processed = cleanLLMGeneratedValue(cleaned, parameterInfo);
                    if (processed != null && isValidValueForParameter(processed, parameterInfo)) {
                        validCleanedValues.add(processed);
                        // Bug audit Finding #35: gate the diverse-cache contribution behind
                        // {@code smart.input.fetch.cache.llm.fallback}. When false, the cache
                        // contains only smart-fetched values from real upstreams (i.e., it
                        // models "what the SUT actually produces") instead of being polluted
                        // with LLM guesses that happen to pass the validator.
                        if (config.isCacheLlmFallbackValues()) {
                            cacheDiverseValue(parameterInfo, processed);
                        }
                    }
                }

                if (!validCleanedValues.isEmpty()) {
                    // If we added new options to the cache, rotate to the next one
                    int cachedCountAfter = diverseValueCache.getOrDefault(cacheKey, Collections.emptyList()).size();
                    if (cachedCountAfter > cachedCountBefore) {
                        String rotated = getNextDiverseValue(parameterInfo);
                        if (rotated != null && !rotated.trim().isEmpty()) {
                            log.info("LLM (Fallback, Rotated) → {} = {} (from {} options)",
                                    parameterInfo.getName(), rotated, cachedCountAfter);
                            return rotated;
                        }
                    }
                    // Fallback to first valid cleaned value if rotation not available
                    return validCleanedValues.get(0);
                }

                // If nothing valid came from LLM values, fall back to minimal value
                String appropriateFallback = generateMinimalFallbackValue(parameterInfo);
                log.info("Single Value Fallback → {} = {}", parameterInfo.getName(), appropriateFallback);
                return appropriateFallback;
            }

        } catch (Exception e) {
            log.warn("LLM fallback failed for '{}': {}", parameterInfo.getName(), e.getMessage());
            String appropriateFallback = generateMinimalFallbackValue(parameterInfo);
            log.info("Exception Fallback → {} = {}", parameterInfo.getName(), appropriateFallback);
            return appropriateFallback;
        }
    }

    /**
     * Handle array parameter from LLM values
     */
    private String handleArrayParameterFromLLM(ParameterInfo parameterInfo, List<String> values) {
        List<String> validValues = new ArrayList<>();

        for (String value : values) {
            if (value != null) {
                String cleanedValue = cleanJsonFromMarkdown(value);
                if (!cleanedValue.trim().isEmpty() && !cleanedValue.equals("json") && !cleanedValue.startsWith("```")) {
                    String processedValue = cleanLLMGeneratedValue(cleanedValue, parameterInfo);
                    if (processedValue != null && isValidValueForParameter(processedValue, parameterInfo)) {
                        validValues.add(processedValue);
                    }
                }
            }
        }

        if (!validValues.isEmpty()) {
            // Return as JSON array string for array parameters
            String arrayResult = "[" + validValues.stream()
                    .map(v -> "\"" + v + "\"")
                    .collect(Collectors.joining(", ")) + "]";
            log.info("LLM (Array Fallback) → {} = {} (from {} values)",
                    parameterInfo.getName(), arrayResult, validValues.size());
            return arrayResult;
        } else {
            log.warn("❌ No valid values found in LLM array response for parameter '{}'", parameterInfo.getName());
            String appropriateFallback = generateMinimalFallbackValue(parameterInfo);
            log.info("Array Fallback → {} = {}", parameterInfo.getName(), appropriateFallback);
            return appropriateFallback;
        }
    }

    // handleSingleValueParameterFromLLM removed — was dead code with no callers
    // (Bug audit Finding #25).



    /**
     * Helper methods
     */

    private void loadRegistry() {
        try {
            File registryFile = new File(config.getRegistryPath());
            if (registryFile.exists()) {
                registry = InputFetchRegistry.loadFromFile(registryFile);
                log.info("Loaded input fetch registry from: {}", config.getRegistryPath());
            } else {
                registry = new InputFetchRegistry();
                log.info("Created new input fetch registry");
            }
        } catch (Exception e) {
            log.warn("Failed to load registry, creating new one: {}", e.getMessage());
            registry = new InputFetchRegistry();
        }
        // Bug audit Finding #27 follow-up: apply per-config EMA alpha + decay-days tuning to
        // every ApiMapping in the loaded registry. Default 0.1 / 30 days preserves prior
        // behavior; operators can tune via {@code smart.input.fetch.ema.alpha} and
        // {@code smart.input.fetch.decay.days}.
        applyMappingTuningToRegistry();
    }

    /**
     * Stamp the configured EMA alpha + decay-days onto every {@link ApiMapping} held by the
     * registry. Called once after {@link #loadRegistry} and again whenever a new mapping is
     * registered via discovery, so the tuning is uniform across loaded and learned entries.
     */
    private void applyMappingTuningToRegistry() {
        if (registry == null || registry.getParameterMappings() == null) return;
        for (List<ApiMapping> mappings : registry.getParameterMappings().values()) {
            if (mappings == null) continue;
            for (ApiMapping m : mappings) {
                applyMappingTuning(m);
            }
        }
    }

    private void applyMappingTuning(ApiMapping mapping) {
        if (mapping == null) return;
        mapping.setEmaAlpha(config.getEmaAlpha());
        mapping.setDecayDays(config.getDecayDays());
    }

    /**
     * Load OpenAPI specification for endpoint discovery
     */
    private void loadOpenAPISpec() {
        try {
            String openApiPath = config.getOpenApiSpecPath();
            if (openApiPath == null || openApiPath.trim().isEmpty()) {
                log.info("No OpenAPI specification path configured; smart-fetch endpoint discovery disabled.");
                return;
            }
            File openApiFile = new File(openApiPath);
            if (openApiFile.exists()) {
                openAPIDiscovery.loadFromFile(openApiPath);
                log.info("Loaded OpenAPI specification from: {}", openApiPath);
                log.info("Available services: {}", openAPIDiscovery.getAllServices().size());
            } else {
                // Fresh-review Finding F25: a configured-but-missing path is operator
                // misconfiguration — escalate to ERROR with an actionable message instead
                // of silently degrading to LLM-only mode.
                log.error("Configured OpenAPI specification file not found: {} — endpoint "
                        + "discovery will be disabled (LLM-only mode). Fix the "
                        + "{@code smart.input.fetch.openapi.spec.path} property to enable "
                        + "OAS-driven discovery.", openApiPath);
            }
        } catch (Exception e) {
            log.error("Failed to load OpenAPI specification ({}); endpoint discovery disabled",
                    e.getMessage(), e);
        }
    }

    private void saveRegistry() {
        try {
            File registryFile = new File(config.getRegistryPath());
            registryFile.getParentFile().mkdirs();
            registry.saveToFile(registryFile);
            log.debug("Saved input fetch registry");
        } catch (Exception e) {
            log.warn("Failed to save registry: {}", e.getMessage());
        }
    }

    private void cacheValue(ParameterInfo parameterInfo, String value) {
        if (config.isCacheEnabled()) {
            // Format value according to OpenAPI schema before caching
            String formattedValue = formatValueForSchema(value, parameterInfo);

            String cacheKey = buildCacheKey(parameterInfo);
            cache.put(cacheKey, new CachedValue(formattedValue));

            // Also add to diverse value cache
            cacheDiverseValue(parameterInfo, formattedValue);
        }
    }

    /**
     * Clean LLM-generated values (remove units, clean formatting, etc.)
     */
    private String cleanLLMGeneratedValue(String value, ParameterInfo parameterInfo) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        String cleanValue = value.trim();
        String paramName = parameterInfo.getName().toLowerCase();

        // Remove quotes if present
        if (cleanValue.startsWith("\"") && cleanValue.endsWith("\"") && cleanValue.length() > 1) {
            cleanValue = cleanValue.substring(1, cleanValue.length() - 1);
        }

        // Strip unit suffixes for any numeric-typed parameter (driven by the schema, not by
        // hard-coded name fragments). The LLM commonly emits values like "15.5 kg" or "120 km"
        // which would otherwise fail downstream Double/Long parsing.
        if (isNumericSchemaType(parameterInfo)) {
            String numericPart = extractNumericPart(cleanValue);
            if (numericPart != null && !numericPart.equals(cleanValue)) {
                log.debug("Extracted numeric part '{}' from LLM value '{}' for parameter '{}' (type={})",
                         numericPart, value, parameterInfo.getName(), parameterInfo.getType());
                return numericPart;
            }
        }

        // For other parameters, return cleaned value
        return cleanValue;
    }

    private static boolean isNumericSchemaType(ParameterInfo parameterInfo) {
        String type = parameterInfo == null ? null : parameterInfo.getType();
        if (type == null) return false;
        String t = type.toLowerCase(java.util.Locale.ROOT);
        return t.equals("integer") || t.equals("int") || t.equals("long")
                || t.equals("number") || t.equals("double") || t.equals("float");
    }

    /**
     * Cache multiple diverse values for a parameter - only cache valid values
     */
    private void cacheDiverseValue(ParameterInfo parameterInfo, String value) {
        // Validate value before caching
        if (!isValidValueForParameter(value, parameterInfo)) {
            log.debug("❌ Skipping caching of invalid diverse value '{}' for parameter '{}'",
                     value, parameterInfo.getName());
            return;
        }

        // Format value according to OpenAPI schema before caching
        String formattedValue = formatValueForSchema(value, parameterInfo);

        String cacheKey = buildCacheKey(parameterInfo);
        // Atomic update: combine the get-or-create and the conditional add into a single
        // compute step so iteration / mutation cannot race when callers share the fetcher.
        diverseValueCache.compute(cacheKey, (key, existing) -> {
            List<String> list = (existing != null) ? existing
                    : java.util.Collections.synchronizedList(new ArrayList<>());
            synchronized (list) {
                if (!list.contains(formattedValue)) {
                    list.add(formattedValue);
                    log.debug("📋 Cached diverse value '{}' for parameter '{}' (total: {})",
                             formattedValue, parameterInfo.getName(), list.size());
                }
            }
            return list;
        });
    }

    /**
     * Compute the quarantine key for a (parameter, mapping) pair. Combines the parameter
     * cache key (which already encodes name/type/format/bounds/regex) with the mapping's
     * endpoint so two mappings serving the same parameter quarantine independently.
     */
    private String mappingQuarantineKey(ParameterInfo parameterInfo, ApiMapping mapping) {
        String endpoint = (mapping != null && mapping.getEndpoint() != null) ? mapping.getEndpoint() : "";
        return buildCacheKey(parameterInfo) + "::" + endpoint;
    }

    /**
     * Bug fix (success-then-reject loop): returns true when a mapping has produced
     * {@value #MAPPING_QUARANTINE_THRESHOLD} consecutive invalid values for this parameter.
     * Quarantined mappings are skipped for the rest of the run to break the fetch-reject loop.
     */
    private boolean isMappingQuarantined(ParameterInfo parameterInfo, ApiMapping mapping) {
        Integer count = mappingFailureCounts.get(mappingQuarantineKey(parameterInfo, mapping));
        return count != null && count >= MAPPING_QUARANTINE_THRESHOLD;
    }

    /**
     * Increment the consecutive-failure counter for a (parameter, mapping) pair and log
     * when the threshold is crossed for the first time.
     */
    private void recordMappingFailure(ParameterInfo parameterInfo, ApiMapping mapping) {
        String key = mappingQuarantineKey(parameterInfo, mapping);
        Integer updated = mappingFailureCounts.compute(key, (k, v) -> v == null ? 1 : v + 1);
        if (updated != null && updated == MAPPING_QUARANTINE_THRESHOLD) {
            log.warn("🚫 Quarantining mapping '{}' for parameter '{}' after {} consecutive validation failures; will skip for rest of run",
                    mapping != null ? mapping.getEndpoint() : "<null>",
                    parameterInfo.getName(),
                    MAPPING_QUARANTINE_THRESHOLD);
        }
    }

    /**
     * Reset the consecutive-failure counter on a successful validation so transient upstream
     * blips don't permanently bench a mapping.
     */
    private void resetMappingFailure(ParameterInfo parameterInfo, ApiMapping mapping) {
        mappingFailureCounts.remove(mappingQuarantineKey(parameterInfo, mapping));
    }

    /**
     * Clear invalid cached values for a parameter
     */
    private void clearInvalidCachedValues(ParameterInfo parameterInfo) {
        String cacheKey = buildCacheKey(parameterInfo);

        // Fresh-review Finding F9: single get + null check; no TOCTOU race with concurrent
        // remove() / LRU eviction.
        CachedValue cachedValue = cache.get(cacheKey);
        if (cachedValue != null && !isValidValueForParameter(cachedValue.value, parameterInfo)) {
            cache.remove(cacheKey);
            log.info("🧹 Cleared invalid cached value for parameter '{}'", parameterInfo.getName());
        }

        // Clear diverse value cache
        if (diverseValueCache.containsKey(cacheKey)) {
            // Atomic compute keeps the iterate-and-replace step safe against concurrent writers.
            diverseValueCache.compute(cacheKey, (key, values) -> {
                if (values == null) return null;
                List<String> validValues;
                synchronized (values) {
                    validValues = values.stream()
                            .filter(value -> isValidValueForParameter(value, parameterInfo))
                            .collect(Collectors.toList());
                }
                if (validValues.size() == values.size()) {
                    return values;
                }
                if (validValues.isEmpty()) {
                    log.info("🧹 Cleared all invalid diverse cached values for parameter '{}'", parameterInfo.getName());
                    return null; // remove entry
                }
                log.info("🧹 Removed {} invalid diverse cached values for parameter '{}', kept {} valid values",
                        values.size() - validValues.size(), parameterInfo.getName(), validValues.size());
                return java.util.Collections.synchronizedList(new ArrayList<>(validValues));
            });
        }
    }

    /**
     * Get next diverse value using rotation
     */
    /**
     * Reset the per-parameter diverse-value rotation cursor. Should be invoked at the start of
     * every new scenario so that scenario A's draws do not advance the cursor that scenario B
     * starts from. Without this reset, the same parameter pool yields correlated picks across
     * independent scenarios and reproducibility across runs depends on scenario ordering.
     */
    public void resetValueRotation() {
        if (valueRotationIndex != null) {
            valueRotationIndex.clear();
        }
        // Fresh-review Finding F26: also clear the diverse-value cache contents at scenario
        // boundary. Trace-observed values flow through this cache (cacheValue is called for
        // them), and {@code flow.md:825} declares trace endpoints session-scoped — so values
        // harvested in scenario A must not leak into scenario B's rotation pool.
        if (diverseValueCache != null) {
            diverseValueCache.clear();
        }
        if (cache != null) {
            cache.clear();
        }
        // Bug fix (success-then-reject loop): clear per-mapping failure counts at scenario
        // boundary too, mirroring the value-cache reset. A scenario quarantine should not
        // survive across independent scenarios since the underlying upstream may recover.
        if (mappingFailureCounts != null) {
            mappingFailureCounts.clear();
        }
        // Reviewer Comment 1: persist Priority-1 mutations at scenario boundary too — the
        // shutdown hook is the safety net but a per-scenario flush keeps recent learning
        // durable even if the JVM is killed mid-run.
        flushIfDirty();
    }

    private String getNextDiverseValue(ParameterInfo parameterInfo) {
        String cacheKey = buildCacheKey(parameterInfo);
        List<String> values = diverseValueCache.get(cacheKey);

        if (values == null) {
            return null;
        }
        // Snapshot the list under its monitor so iteration / size are consistent even if
        // another thread mutates it concurrently.
        int snapshotSize;
        String value;
        synchronized (values) {
            snapshotSize = values.size();
            if (snapshotSize == 0) return null;
            // Bug audit Finding #30: read-and-update the rotation cursor atomically per
            // (cacheKey). Previously this was a get-then-put pair that two threads could
            // interleave, producing duplicate or skipped picks.
            int[] picked = new int[1];
            valueRotationIndex.compute(cacheKey, (k, current) -> {
                int idx = (current == null ? 0 : current) % snapshotSize;
                picked[0] = idx;
                return (idx + 1) % snapshotSize;
            });
            value = values.get(picked[0]);
            log.debug("🔄 Rotating to diverse value '{}' for parameter '{}' (index: {}/{})",
                    value, parameterInfo.getName(), picked[0], snapshotSize);
        }

        String formattedValue = formatCachedValueForSchema(value, parameterInfo);
        if (!formattedValue.equals(value)) {
            log.debug("🔧 Formatted cached value '{}' → '{}' for parameter '{}'",
                    value, formattedValue, parameterInfo.getName());
        }
        return formattedValue;
    }

    /**
     * Format cached value according to parameter schema (especially for arrays)
     */
    private String formatCachedValueForSchema(String cachedValue, ParameterInfo parameterInfo) {
        if (cachedValue == null || parameterInfo == null) {
            return cachedValue;
        }

        try {
            String schemaType = getOpenAPISchemaType(parameterInfo);

            // For array parameters, ensure the cached value is formatted as an array
            if ("array".equals(schemaType)) {
                // If cached value is already a JSON array, return as-is
                if (cachedValue.startsWith("[") && cachedValue.endsWith("]")) {
                    return cachedValue;
                }

                // Convert single cached value to array format
                String arrayValue = "[\"" + cachedValue + "\"]";
                log.debug("Converted cached single value '{}' to array '{}' for array parameter '{}'",
                         cachedValue, arrayValue, parameterInfo.getName());
                return arrayValue;
            }

            // For non-array parameters, return cached value as-is
            return cachedValue;

        } catch (Exception e) {
            log.debug("Failed to format cached value '{}' for parameter '{}': {}",
                     cachedValue, parameterInfo.getName(), e.getMessage());
            return cachedValue;
        }
    }

    /**
     * Extract additional diverse values from API response using LLM-based analysis
     */
    private void extractAdditionalDiverseValues(String responseBody, ParameterInfo parameterInfo, String firstValue) {
        try {
            log.debug("🔍 Extracting additional diverse values for parameter '{}'", parameterInfo.getName());

            // Use LLM to extract all possible values from the response
            Set<String> extractedValues = extractAllValuesWithLLM(responseBody, parameterInfo);
            extractedValues.add(firstValue); // Include the first value

            // Cache all extracted diverse values
            for (String value : extractedValues) {
                if (!value.equals(firstValue)) { // Don't re-cache the first value
                    cacheDiverseValue(parameterInfo, value);
                }
            }

            if (extractedValues.size() > 1) {
                log.info("📋 Extracted {} diverse values for parameter '{}': {}",
                        extractedValues.size(), parameterInfo.getName(),
                        extractedValues.stream().limit(5).collect(Collectors.toList()));
            }

            // If we don't have enough values, generate more using semantic similarity
            int requiredValues = getRequiredValueCount(parameterInfo);
            if (extractedValues.size() < requiredValues) {
                log.info("🔍 Need {} values but only have {}, generating additional values using semantic similarity",
                        requiredValues, extractedValues.size());
                generateAdditionalValuesWithSemanticSimilarity(parameterInfo, extractedValues, requiredValues);
            }

        } catch (Exception e) {
            log.debug("Failed to extract additional diverse values for parameter '{}': {}",
                     parameterInfo.getName(), e.getMessage());
        }
    }

    /**
     * Extract all possible values from API response using LLM
     */
    private Set<String> extractAllValuesWithLLM(String responseBody, ParameterInfo parameterInfo) {
        Set<String> extractedValues = new HashSet<>();

        try {
            String prompt = buildMultipleValueExtractionPrompt(responseBody, parameterInfo);

            if (prompt.length() > config.getMaxPromptChars()) {
                log.warn("Multiple value extraction prompt too long ({} chars), using fallback", prompt.length());
                return extractValuesWithFallback(responseBody, parameterInfo);
            }

            String llmResponse = askLLMForMultipleValueExtraction(prompt);

            if (llmResponse != null && !llmResponse.trim().isEmpty()) {
                // Parse LLM response to extract multiple values
                String[] values = llmResponse.split("[,\\n\\r]+");
                for (String value : values) {
                    String cleanValue = value.trim().replaceAll("^[\"']|[\"']$", ""); // Remove quotes
                    if (!cleanValue.isEmpty() && !cleanValue.equals("NO_VALUES_FOUND") &&
                        isValidValueForParameter(cleanValue, parameterInfo)) {
                        extractedValues.add(cleanValue);
                    }
                }

                log.debug("LLM extracted {} values from API response for parameter '{}'",
                         extractedValues.size(), parameterInfo.getName());
            }

        } catch (Exception e) {
            log.debug("LLM multiple value extraction failed for parameter '{}': {}",
                     parameterInfo.getName(), e.getMessage());
        }

        return extractedValues;
    }

    /**
     * Validate LLM response quality to reject explanations and invalid values
     */
    private boolean isValidLLMResponse(String response, ParameterInfo parameterInfo) {
        if (response == null || response.trim().isEmpty()) {
            return false;
        }

        String cleanResponse = response.trim();
        String paramName = parameterInfo.getName().toLowerCase();

        // Reject responses that look like explanations or descriptions. Fresh-review F8:
        // dropped the magic {@code length() > 100} cap — schema-level {@code maxLength}
        // is the authoritative size bound (enforced by {@link #isValidValueForParameter}).
        // The remaining substring checks are signature phrases the LLM emits when it
        // returns prose instead of a value.
        if (cleanResponse.contains("The format appears to be") ||
            cleanResponse.contains("delivery route)") ||
            cleanResponse.contains("This is a") ||
            cleanResponse.contains("appears to be") ||
            cleanResponse.contains("should be") ||
            cleanResponse.contains("typically") ||
            cleanResponse.contains("usually") ||
            cleanResponse.contains("format:") ||
            cleanResponse.contains("example:")) {
            log.warn("❌ LLM response looks like explanation, not value: '{}'", cleanResponse);
            return false;
        }

        // Reject generic placeholder values
        if (cleanResponse.equals("objects") ||
            cleanResponse.equals("values") ||
            cleanResponse.equals("data") ||
            cleanResponse.equals("items") ||
            cleanResponse.equals("elements") ||
            cleanResponse.equals("content")) {
            log.warn("❌ LLM response is generic placeholder: '{}'", cleanResponse);
            return false;
        }

        // Validate parameter-specific formats. Fresh-review Finding F4: dropped the
        // {@code || paramName.contains("route")} clause — it false-positived on names like
        // {@code routeDescription}, {@code traceRouteUrl}. The boundary-aware
        // {@link #isIdLikeParamName} is the single source of truth for "this is an ID".
        if (isIdLikeParamName(paramName)) {
            if (cleanResponse.length() < 3 || cleanResponse.length() > 50) {
                log.warn("❌ ID parameter '{}' has invalid length: '{}'", paramName, cleanResponse);
                return false;
            }
            if (cleanResponse.contains(" ") && cleanResponse.split(" ").length > 3) {
                log.warn("❌ ID parameter '{}' contains too many words: '{}'", paramName, cleanResponse);
                return false;
            }
        }

        // Fresh-review Finding F5: gate the digit-presence check on the schema type, not on
        // substring matches against the parameter name. {@code phoneNumber} (string) and
        // {@code priceListDescription} (string) used to falsely require digits.
        if (isNumericSchemaType(parameterInfo)) {
            if (!cleanResponse.matches(".*\\d.*")) {
                log.warn("❌ Numeric parameter '{}' contains no digits: '{}'", paramName, cleanResponse);
                return false;
            }
        }

        return true; // Response looks valid
    }

    // Duplicate method removed - using the original extractValueWithSimpleFallback method above

    /**
     * Generate minimal fallback value based on schema type
     */
    private String generateMinimalFallbackValue(ParameterInfo parameterInfo) {
        try {
            String paramName = parameterInfo.getName().toLowerCase();
            String schemaType = getOpenAPISchemaType(parameterInfo);
            String schemaFormat = getOpenAPISchemaFormat(parameterInfo);

            // Use LLM to generate appropriate value based on parameter semantics
            StringBuilder prompt = new StringBuilder();
            boolean isArrayType = "array".equals(schemaType);

            if (isArrayType) {
                prompt.append("Generate a JSON array with 2-3 realistic values for this parameter:\n\n");
            } else {
                prompt.append("Generate a single realistic value for this parameter:\n\n");
            }

            prompt.append("Parameter name: ").append(parameterInfo.getName()).append("\n");
            prompt.append("Schema type: ").append(schemaType != null ? schemaType : "string").append("\n");
            if (schemaFormat != null) {
                prompt.append("Schema format: ").append(schemaFormat).append("\n");
            }

            // Add semantic hints based on parameter name
            if (paramName.contains("distance")) {
                prompt.append("Semantic hint: This represents a distance measurement\n");
                if (isArrayType) {
                    prompt.append("Example: [\"10 miles\", \"25 km\", \"150 meters\"]\n");
                }
            } else if (isIdLikeParamName(paramName)) {
                prompt.append("Semantic hint: This represents an identifier\n");
            } else if (paramName.contains("price") || paramName.contains("rate")) {
                prompt.append("Semantic hint: This represents a price or rate value\n");
            } else if (paramName.contains("date") || paramName.contains("time")) {
                prompt.append("Semantic hint: This represents a date or time value\n");
            }

            prompt.append("\nInstructions:\n");
            if (isArrayType) {
                prompt.append("1. Generate a JSON array with 2-3 realistic values\n");
                prompt.append("2. For distance arrays, include values with units (like \"10 miles\", \"25 km\")\n");
                prompt.append("3. Return ONLY the JSON array, no explanation\n");
                prompt.append("4. Format: [\"value1\", \"value2\", \"value3\"]\n");
            } else {
                prompt.append("1. Generate ONE realistic value that matches the parameter semantics\n");
                prompt.append("2. For distance values, include units (like \"10 miles\", \"25 km\")\n");
                prompt.append("3. For string types, return a realistic string value\n");
                prompt.append("4. For boolean types, return 'true' or 'false'\n");
                prompt.append("5. Return ONLY the value, no explanation\n");
            }

            String systemContent = "You are a test data generator. Generate realistic values based on parameter semantics and schema types.";

            String result = llmService.generateText(systemContent, prompt.toString(), 20, 0.3);

            if (result != null && !result.trim().isEmpty()) {
                String cleanResult = cleanLLMGeneratedValue(result, parameterInfo);

                // Validate the cleaned value
                if (cleanResult != null && isValidValueForParameter(cleanResult, parameterInfo)) {
                    log.debug("LLM minimal fallback generated valid value '{}' (cleaned from '{}') for parameter '{}'",
                             cleanResult, result, parameterInfo.getName());
                    return cleanResult;
                } else {
                    log.debug("LLM minimal fallback generated invalid value '{}' for parameter '{}'",
                             result, parameterInfo.getName());
                }
            }

            // If LLM fails, use schema-based minimal values
            return generateSchemaBasedMinimalValue(parameterInfo, schemaType);

        } catch (Exception e) {
            log.debug("Failed to generate minimal fallback value for '{}': {}", parameterInfo.getName(), e.getMessage());
            return generateSchemaBasedMinimalValue(parameterInfo, getOpenAPISchemaType(parameterInfo));
        }
    }

    /**
     * Generate minimal value based on schema type only (last resort) - NO HARDCODING!
     */
    private String generateSchemaBasedMinimalValue(ParameterInfo parameterInfo, String schemaType) {
        try {
            // Use LLM to generate even the most basic fallback values
            return generateLLMBasedMinimalValue(parameterInfo, schemaType);
        } catch (Exception e) {
            log.debug("LLM-based minimal value generation failed for '{}': {}", parameterInfo.getName(), e.getMessage());
            // Only as absolute last resort, use algorithmic generation based on parameter analysis
            return generateAlgorithmicMinimalValue(parameterInfo, schemaType);
        }
    }

    /**
     * Generate minimal value using LLM (no hardcoding)
     */
    private String generateLLMBasedMinimalValue(ParameterInfo parameterInfo, String schemaType) {
        String paramName = parameterInfo.getName();

        StringBuilder prompt = new StringBuilder();
        prompt.append("Generate a minimal realistic value for this parameter:\n\n");
        prompt.append("Parameter name: ").append(paramName).append("\n");
        prompt.append("Schema type: ").append(schemaType != null ? schemaType : "string").append("\n");

        // Add context based on schema type
        if ("array".equals(schemaType)) {
            prompt.append("Required format: JSON array with 1-2 values\n");
            prompt.append("Example format: [\"value1\", \"value2\"]\n");
        } else if ("integer".equals(schemaType)) {
            prompt.append("Required format: Integer number\n");
            prompt.append("Example format: 42\n");
        } else if ("number".equals(schemaType)) {
            prompt.append("Required format: Decimal number\n");
            prompt.append("Example format: 42.5\n");
        } else if ("boolean".equals(schemaType)) {
            prompt.append("Required format: Boolean value\n");
            prompt.append("Example format: true or false\n");
        } else {
            prompt.append("Required format: String value\n");
            prompt.append("Example format: \"text\"\n");
        }

        prompt.append("\nInstructions:\n");
        prompt.append("1. Generate a realistic value based on the parameter name\n");
        prompt.append("2. Consider what this parameter might represent in a real system\n");
        prompt.append("3. Return ONLY the value in the correct format, no explanation\n");
        prompt.append("4. For arrays, return a JSON array\n");
        prompt.append("5. For strings, do not include quotes (they will be added automatically)\n");

        String systemContent = "You are a minimal test data generator. Generate the simplest realistic value for the given parameter.";

        String result = llmService.generateText(systemContent, prompt.toString(), 30, 0.1);

        if (result != null && !result.trim().isEmpty()) {
            String cleanResult = result.trim();

            // Remove quotes if present for non-array values
            if (!"array".equals(schemaType) && cleanResult.startsWith("\"") && cleanResult.endsWith("\"") && cleanResult.length() > 1) {
                cleanResult = cleanResult.substring(1, cleanResult.length() - 1);
            }

            log.debug("LLM minimal value generated '{}' for parameter '{}' (type: {})",
                     cleanResult, paramName, schemaType);
            return cleanResult;
        }

        throw new RuntimeException("LLM failed to generate minimal value");
    }

    /**
     * Generate minimal value using algorithmic approach (absolute last resort, no hardcoding)
     */
    private String generateAlgorithmicMinimalValue(ParameterInfo parameterInfo, String schemaType) {
        String paramName = parameterInfo.getName();

        if ("integer".equals(schemaType)) {
            // Bug audit Finding #20: respect schema minimum/maximum when synthesizing the
            // algorithmic fallback. Previously this returned (hash % 1000) + 1 unconditionally,
            // ignoring any declared bounds and producing schema-invalid integers.
            long base = Math.abs((long) paramName.hashCode() % 1000) + 1;
            return String.valueOf(clampToIntegerBounds(base, parameterInfo));
        } else if ("number".equals(schemaType)) {
            double value = (Math.abs(paramName.hashCode() % 10000) + 1) / 10.0;
            return String.valueOf(clampToNumberBounds(value, parameterInfo));
        } else if ("boolean".equals(schemaType)) {
            return String.valueOf(paramName.hashCode() % 2 == 0);
        } else if ("array".equals(schemaType)) {
            String baseValue = generateAlgorithmicStringValue(paramName);
            String secondValue = generateAlgorithmicStringValue(paramName + "_2");
            return "[\"" + baseValue + "\", \"" + secondValue + "\"]";
        } else {
            return generateAlgorithmicStringValue(paramName);
        }
    }

    /** Clamp an integer fallback to the parameter's declared {@code minimum}/{@code maximum}. */
    private static long clampToIntegerBounds(long candidate, ParameterInfo parameterInfo) {
        Number min = parameterInfo.getMinimum();
        Number max = parameterInfo.getMaximum();
        if (min != null && candidate < min.longValue()) candidate = min.longValue();
        if (max != null && candidate > max.longValue()) candidate = max.longValue();
        return candidate;
    }

    /** Clamp a numeric fallback to the parameter's declared {@code minimum}/{@code maximum}. */
    private static double clampToNumberBounds(double candidate, ParameterInfo parameterInfo) {
        Number min = parameterInfo.getMinimum();
        Number max = parameterInfo.getMaximum();
        if (min != null && candidate < min.doubleValue()) candidate = min.doubleValue();
        if (max != null && candidate > max.doubleValue()) candidate = max.doubleValue();
        return candidate;
    }

    /**
     * Generate string value algorithmically based ONLY on parameter name characteristics - NO HARDCODING
     */
    private String generateAlgorithmicStringValue(String paramName) {
        // Use ONLY the parameter name to generate values - no hardcoded strings or arrays

        // Clean parameter name for base generation
        String cleanName = paramName.replaceAll("[^a-zA-Z0-9]", "");

        // Generate hash-based numeric component
        int hashValue = Math.abs(paramName.hashCode());

        // Create value using parameter name characteristics only
        StringBuilder result = new StringBuilder();

        // Use first few characters of parameter name as base
        if (cleanName.length() > 0) {
            // Take first 1-3 characters based on name length
            int prefixLength = Math.min(cleanName.length(), Math.max(1, cleanName.length() / 3));
            result.append(cleanName.substring(0, prefixLength).toLowerCase());
        }

        // Add hash-derived numeric component
        result.append(hashValue % 10000);

        // Fresh-review Finding F19: dropped the "unit-char suffix" for distance/length/size
        // — appending a single random Latin letter does not produce a unit (`d1234x` is not
        // "1234 km"). This is the absolute-last-resort fallback; cleaner output is better
        // than fake-unit gibberish.
        return result.toString();
    }

    // All hardcoded generation methods removed - now using LLM-based generation only

    /**
     * Build prompt for LLM multiple value extraction
     */
    private String buildMultipleValueExtractionPrompt(String responseBody, ParameterInfo parameterInfo) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("Extract ALL possible values from this JSON response that could be used for parameter '");
        prompt.append(parameterInfo.getName()).append("' (type: ").append(parameterInfo.getType()).append(").\n\n");

        if (parameterInfo.getDescription() != null && !parameterInfo.getDescription().trim().isEmpty()
            && !parameterInfo.getDescription().equals("null")) {
            prompt.append("Parameter description: ").append(parameterInfo.getDescription()).append("\n\n");
        }

        // Truncate response if too long
        String truncatedResponse = truncateResponseSchemaForLLM(responseBody, "", parameterInfo);
        prompt.append("JSON Response:\n").append(truncatedResponse).append("\n\n");

        prompt.append("Instructions:\n");
        prompt.append("1. Find ALL values in the JSON that are semantically relevant for this parameter\n");
        prompt.append("2. Look for values in arrays, nested objects, and all fields\n");
        prompt.append("3. Consider field names, data types, and semantic meaning\n");
        prompt.append("4. Extract ONLY actual data values, NOT field names, paths, or descriptions\n");
        prompt.append("5. For array-type parameters (stations, distances), extract individual elements\n");
        prompt.append("6. For numeric parameters, extract only numeric values\n");
        prompt.append("7. For ID parameters, extract only actual IDs (UUIDs, numbers), not descriptions\n");
        prompt.append("8. Do NOT generate explanatory text or descriptions\n");
        prompt.append("9. Return each value on a separate line\n");
        prompt.append("10. If no relevant values found, respond with: NO_VALUES_FOUND\n\n");

        prompt.append("Example for a name-like parameter:\n");
        prompt.append("<first value from the response>\n");
        prompt.append("<second value from the response>\n");
        prompt.append("<third value from the response>\n");

        return prompt.toString();
    }

    /**
     * Ask LLM to extract multiple values from API response
     */
    private String askLLMForMultipleValueExtraction(String prompt) {
        try {
            String systemContent = "You are a data extraction expert. Extract ALL relevant values from JSON responses for test parameter generation. " +
                                  "Focus on finding diverse, meaningful values that match the parameter semantically. " +
                                  "Return actual values, not JSONPath expressions or field names.";

            String result = llmService.generateText(systemContent, prompt, 200, 0.3);

            if (result != null && !result.trim().isEmpty()) {
                log.debug("[Multiple Value Extraction LLM] Successfully extracted values: {}", result);
                return result;
            } else {
                log.warn("[Multiple Value Extraction LLM] LLM service returned null or empty result");
                return null;
            }

        } catch (Exception e) {
            log.warn("[Multiple Value Extraction LLM] Failed to call LLM service: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Fallback value extraction when LLM fails
     */
    private Set<String> extractValuesWithFallback(String responseBody, ParameterInfo parameterInfo) {
        Set<String> extractedValues = new HashSet<>();

        try {
            // Simple JSON parsing fallback
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(responseBody);

            extractValuesFromJsonNode(rootNode, parameterInfo, extractedValues);

        } catch (Exception e) {
            log.debug("Fallback value extraction failed for parameter '{}': {}",
                     parameterInfo.getName(), e.getMessage());
        }

        return extractedValues;
    }

    /**
     * Required number of diverse values per parameter pool.
     * Sourced from {@link SmartInputFetchConfig#getDiverseTargetCount()} (default 10);
     * formerly read directly via {@code System.getProperty} in violation of the config
     * pipeline (Fresh-review Finding F17, Bug audit Finding #6).
     */
    private int getRequiredValueCount(ParameterInfo parameterInfo) {
        return config.getDiverseTargetCount();
    }

    /**
     * Generate additional values using semantic similarity when API values are insufficient
     */
    private void generateAdditionalValuesWithSemanticSimilarity(ParameterInfo parameterInfo, Set<String> existingValues, int requiredCount) {
        try {
            int additionalNeeded = requiredCount - existingValues.size();
            if (additionalNeeded <= 0) {
                return;
            }

            log.info("🧠 Generating {} additional values for parameter '{}' using semantic similarity",
                    additionalNeeded, parameterInfo.getName());

            // Use LLM to generate semantically similar values
            Set<String> generatedValues = generateSemanticallySimilarValues(parameterInfo, existingValues, additionalNeeded);

            // Cache the generated values
            for (String value : generatedValues) {
                cacheDiverseValue(parameterInfo, value);
            }

            if (!generatedValues.isEmpty()) {
                log.info("✅ Generated {} additional semantic values for parameter '{}': {}",
                        generatedValues.size(), parameterInfo.getName(),
                        generatedValues.stream().limit(3).collect(Collectors.toList()));
            }

        } catch (Exception e) {
            log.warn("Failed to generate additional semantic values for parameter '{}': {}",
                    parameterInfo.getName(), e.getMessage());
        }
    }

    /**
     * Generate semantically similar values using LLM-based semantic understanding
     */
    private Set<String> generateSemanticallySimilarValues(ParameterInfo parameterInfo, Set<String> existingValues, int count) {
        Set<String> generatedValues = new HashSet<>();

        // Closed-domain short-circuit: when the parameter has a finite, fully-known value
        // space (boolean, or enum-constrained), do not call the LLM. The "semantic
        // similarity" framing tempts the model to emit synonyms ('yes', 'on', 'enabled'
        // for a boolean, or near-misses for enums) that are *not* schema-valid. We can
        // produce the correct candidates deterministically.
        Set<String> closedDomain = closedDomainCandidates(parameterInfo);
        if (closedDomain != null) {
            for (String v : closedDomain) {
                if (!existingValues.contains(v)) {
                    generatedValues.add(v);
                    if (generatedValues.size() >= count) break;
                }
            }
            log.debug("Closed-domain short-circuit for '{}' (type={}): produced {} value(s) without LLM",
                    parameterInfo.getName(), parameterInfo.getType(), generatedValues.size());
            return generatedValues;
        }

        // ID-typed short-circuit: a parameter whose name encodes the ID
        // convention (`orderId`, `accountId`, `trip_uuid`, bare `id`, `uuid`...)
        // refers to a real entity in the SUT. Asking an LLM to generate
        // "semantically similar UUIDs" produces UUID-shaped strings whose
        // hex digits include letters like `g..z` (e.g. `a3b2c1d4-ijkl-...`)
        // — they look like UUIDs but are not. They later pollute the diverse
        // cache and surface as smart-fetched values that no real upstream
        // would ever produce, breaking D5 ID-resolvability and producing
        // 4xx responses against the SUT. Skip the LLM for these params; the
        // pool will be filled only by real smart-fetched values plus any
        // existing seed value.
        if (isIdTypedParam(parameterInfo)) {
            log.debug("ID-typed short-circuit for '{}': declining to LLM-diversify a real-entity ID — "
                    + "smart-fetch values from upstream services are the only realistic source.",
                    parameterInfo.getName());
            return generatedValues;
        }

        try {
            String prompt = buildSemanticSimilarityPrompt(parameterInfo, existingValues, count);

            if (prompt.length() > config.getMaxPromptChars()) {
                log.warn("Semantic similarity prompt too long ({} chars), using fallback", prompt.length());
                return generateFallbackSemanticValues(parameterInfo, existingValues, count);
            }

            String llmResponse = askLLMForSemanticSimilarValues(prompt);

            if (llmResponse != null && !llmResponse.trim().isEmpty()) {
                // Parse LLM response to extract generated values
                String[] values = llmResponse.split("[,\\n\\r]+");
                for (String value : values) {
                    String cleanValue = value.trim().replaceAll("^[\"']|[\"']$", ""); // Remove quotes
                    if (!cleanValue.isEmpty() && !cleanValue.equals("NO_VALUES_GENERATED") &&
                        !existingValues.contains(cleanValue) &&
                        isValidValueForParameter(cleanValue, parameterInfo)) {
                        generatedValues.add(cleanValue);

                        if (generatedValues.size() >= count) {
                            break; // Got enough values
                        }
                    }
                }

                log.debug("LLM generated {} semantic values for parameter '{}'",
                         generatedValues.size(), parameterInfo.getName());
            }

        } catch (Exception e) {
            log.debug("LLM semantic value generation failed for parameter '{}': {}",
                     parameterInfo.getName(), e.getMessage());
        }

        return generatedValues;
    }

    /**
     * True when the parameter name follows an ID convention — same boundary-aware
     * rule used by the workflow-side {@code SemanticDependencyRegistry#isIdLikeParam}.
     * Boundary required: camelCase {@code Id}/{@code ID}/{@code UUID}/{@code Uuid},
     * snake-case {@code _id}/{@code _uuid}, or the bare names {@code id}/{@code uuid}.
     * English words ending in lowercase 'id' ({@code paid}, {@code valid}, {@code humid})
     * are NOT matched — pipeline-bug-audit finding #5.
     */
    private static final java.util.regex.Pattern ID_PARAM_SUFFIX_RX =
            java.util.regex.Pattern.compile("^.+?(Id|ID|UUID|Uuid|_id|_ID|_uuid|_UUID)$");
    private static final java.util.regex.Pattern ID_PARAM_PREFIX_RX =
            java.util.regex.Pattern.compile("^(?:id|Id|ID|uuid|Uuid|UUID)(?:_(\\w+)|([A-Z]\\w*))$");
    private static final java.util.Set<String> BARE_ID_NAMES = new java.util.HashSet<>(
            java.util.Arrays.asList("id", "ID", "Id", "iD", "uuid", "UUID", "Uuid"));

    private static boolean isIdTypedParam(ParameterInfo parameterInfo) {
        if (parameterInfo == null) return false;
        return isIdLikeParamName(parameterInfo.getName());
    }

    /**
     * String form of {@link #isIdTypedParam} so call sites that only have the param name
     * (validators, prompt builders, simple-fallback heuristics) can use the same boundary-aware
     * rule instead of the loose {@code paramName.contains("id")} that falsely matches
     * {@code paid}, {@code valid}, {@code humid}, {@code aid}, {@code void}, etc.
     * (Bug audit Findings #5, #18).
     */
    static boolean isIdLikeParamName(String name) {
        if (name == null || name.isEmpty()) return false;
        if (BARE_ID_NAMES.contains(name)) return true;
        if (ID_PARAM_SUFFIX_RX.matcher(name).matches()) return true;
        if (ID_PARAM_PREFIX_RX.matcher(name).matches()) return true;
        return false;
    }

    /**
     * Returns the full set of valid values when the parameter has a closed value domain
     * (boolean type or enum-constrained), or {@code null} for open domains.
     */
    private static Set<String> closedDomainCandidates(ParameterInfo parameterInfo) {
        if (parameterInfo == null) return null;
        String type = parameterInfo.getType();
        if (type != null) {
            String t = type.toLowerCase(java.util.Locale.ROOT);
            if (t.equals("boolean") || t.equals("bool")) {
                Set<String> out = new java.util.LinkedHashSet<>();
                out.add("true");
                out.add("false");
                return out;
            }
        }
        if (parameterInfo.hasEnum() && parameterInfo.getEnumValues() != null) {
            Set<String> out = new java.util.LinkedHashSet<>();
            for (String v : parameterInfo.getEnumValues()) {
                if (v != null) out.add(v);
            }
            return out;
        }
        return null;
    }

    /**
     * Build prompt for semantic similarity-based value generation
     */
    private String buildSemanticSimilarityPrompt(ParameterInfo parameterInfo, Set<String> existingValues, int count) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("Generate ").append(count).append(" additional values that are semantically similar to the existing values ");
        prompt.append("for parameter '").append(parameterInfo.getName()).append("' (type: ").append(parameterInfo.getType()).append(").\n\n");

        if (parameterInfo.getDescription() != null && !parameterInfo.getDescription().trim().isEmpty()
            && !parameterInfo.getDescription().equals("null")) {
            prompt.append("Parameter description: ").append(parameterInfo.getDescription()).append("\n\n");
        }

        prompt.append("Existing values:\n");
        for (String value : existingValues.stream().limit(5).collect(Collectors.toList())) {
            prompt.append("- ").append(value).append("\n");
        }

        prompt.append("\nInstructions:\n");
        prompt.append("1. Generate values that are semantically similar to the existing ones\n");
        prompt.append("2. Consider the same domain, category, or type as existing values\n");
        prompt.append("3. Use similar naming patterns, formats, or structures\n");
        prompt.append("4. Generate realistic, meaningful values (not random strings or descriptions)\n");

        // Add type/parameter-specific instructions. The type guard comes first so the LLM
        // does not "diversify" booleans into synonyms like 'yes'/'on'/'enabled' (a real
        // hallucination observed on the qwen2.5-coder:14b model — see D3 measurement).
        String paramType = parameterInfo.getType() != null
                ? parameterInfo.getType().toLowerCase(java.util.Locale.ROOT) : "";
        String paramName = parameterInfo.getName().toLowerCase();
        if (paramType.equals("boolean") || paramType.equals("bool")) {
            prompt.append("5. CRITICAL: This is a boolean parameter — every value MUST be the literal token 'true' or 'false'. ");
            prompt.append("Do NOT emit synonyms like 'yes', 'no', 'on', 'off', 'enabled', 'disabled', 'active'.\n");
        } else if (paramType.equals("integer") || paramType.equals("int")
                || paramType.equals("long") || paramType.equals("number")
                || paramType.equals("double") || paramType.equals("float")) {
            prompt.append("5. CRITICAL: This is a numeric parameter — every value MUST be a parseable number with no units, currency symbols, or letters.\n");
        } else if (isIdLikeParamName(paramName) && !paramName.contains("station")) {
            prompt.append("5. For ID parameters: generate actual UUID-like strings or meaningful IDs\n");
        } else if (paramName.contains("distance")) {
            prompt.append("5. For distance parameters: generate numeric values with appropriate units\n");
        } else {
            prompt.append("5. Generate values appropriate to the parameter type and domain\n");
        }

        prompt.append("6. For names: generate actual names, not generic terms like 'objects' or 'service'\n");
        prompt.append("7. Each value should be different from existing ones\n");
        prompt.append("8. Return ONLY the actual values, no explanatory text\n");
        prompt.append("9. Return each value on a separate line\n");
        prompt.append("10. If unable to generate similar values, respond with: NO_VALUES_GENERATED\n\n");

        prompt.append("Examples:\n");
        if (paramType.equals("boolean") || paramType.equals("bool")) {
            prompt.append("If existing values are [true] → generate: false\n");
            prompt.append("If existing values are [false] → generate: true\n");
            prompt.append("(Only the two literal tokens 'true' and 'false' are valid.)\n");
        } else if (paramName.contains("distance")) {
            prompt.append("If existing values are [100, 250] → generate: 150, 300, 75\n");
            prompt.append("If existing values are [10 miles, 50 km] → generate: 25 miles, 75 km, 100 meters\n");
        } else {
            prompt.append("If existing values are [item-1, item-2] → generate: item-3, item-4, item-5\n");
            prompt.append("If existing values are [SKU-1234, SKU-2468] → generate: SKU-5678, SKU-9012, SKU-3456\n");
            prompt.append("If existing values are [admin, user123] → generate: manager, guest456, operator\n");
        }

        return prompt.toString();
    }

    /**
     * Ask LLM to generate semantically similar values
     */
    private String askLLMForSemanticSimilarValues(String prompt) {
        try {
            String systemContent = "You are an expert in semantic similarity and test data generation. " +
                                  "Generate diverse but semantically similar values based on existing examples. " +
                                  "Focus on maintaining the same domain, format, and meaning while ensuring diversity. " +
                                  "Use your knowledge of real-world entities, naming patterns, and data structures.";

            String result = llmService.generateText(systemContent, prompt, 150, 0.7); // Higher temperature for creativity

            if (result != null && !result.trim().isEmpty()) {
                log.debug("[Semantic Similarity LLM] Successfully generated similar values: {}", result);
                return result;
            } else {
                log.warn("[Semantic Similarity LLM] LLM service returned null or empty result");
                return null;
            }

        } catch (Exception e) {
            log.warn("[Semantic Similarity LLM] Failed to call LLM service: {}", e.getMessage());
            return null;
        }
    }

    /**
     * LLM-based fallback semantic value generation when primary LLM fails
     */
    private Set<String> generateFallbackSemanticValues(ParameterInfo parameterInfo, Set<String> existingValues, int count) {
        Set<String> generatedValues = new HashSet<>();

        try {
            // Try a simpler LLM prompt for fallback generation
            String fallbackValue = generateValueWithLLM(parameterInfo);

            if (fallbackValue != null && !fallbackValue.trim().isEmpty() &&
                !existingValues.contains(fallbackValue) &&
                isValidValueForParameter(fallbackValue, parameterInfo)) {
                generatedValues.add(fallbackValue);
            }

            // If we need more values, generate variations
            int safetyCounter = 0;
            while (generatedValues.size() < count) {
                String variation = generateValueVariationWithLLM(parameterInfo, generatedValues);
                if (variation != null && !variation.trim().isEmpty() &&
                    !generatedValues.contains(variation) && !existingValues.contains(variation) &&
                    isValidValueForParameter(variation, parameterInfo)) {
                    generatedValues.add(variation);
                } else {
                    // Bug audit Finding #21: previously this appended {@code "_<idx>"} to a
                    // "minimal schema-compliant" value, producing strings like {@code "42_2"}
                    // for an integer parameter — which then fail downstream
                    // {@code isValidValueForParameter}, triggering wasted LLM regeneration.
                    // We now request a fresh minimal value per loop iteration. If the
                    // generator yields a duplicate, we just stop adding rather than
                    // synthesizing a corrupt suffix.
                    String minimalValue = generateMinimalFallbackValue(parameterInfo);
                    if (minimalValue != null && !generatedValues.contains(minimalValue)
                            && !existingValues.contains(minimalValue)
                            && isValidValueForParameter(minimalValue, parameterInfo)) {
                        generatedValues.add(minimalValue);
                    }
                }

                // Prevent infinite loop
                if (generatedValues.size() >= count || ++safetyCounter >= count * 3 + 10) {
                    break;
                }
            }

        } catch (Exception e) {
            log.debug("Fallback semantic value generation failed for parameter '{}': {}",
                     parameterInfo.getName(), e.getMessage());

            // Last resort: only schema-valid minimal values, no synthetic suffixes.
            for (int i = 0; i < count && generatedValues.size() < count; i++) {
                String minimalValue = generateMinimalFallbackValue(parameterInfo);
                if (minimalValue != null && isValidValueForParameter(minimalValue, parameterInfo)
                        && !generatedValues.contains(minimalValue)
                        && !existingValues.contains(minimalValue)) {
                    generatedValues.add(minimalValue);
                }
            }
        }

        return generatedValues;
    }

    /**
     * Generate value variation using LLM
     */
    private String generateValueVariationWithLLM(ParameterInfo parameterInfo, Set<String> existingValues) {
        try {
            StringBuilder prompt = new StringBuilder();

            prompt.append("Generate a new value similar to these existing values for parameter '")
                  .append(parameterInfo.getName()).append("':\n\n");

            prompt.append("Existing values:\n");
            for (String value : existingValues) {
                prompt.append("- ").append(value).append("\n");
            }

            prompt.append("\nGenerate ONE new value that is:\n");
            prompt.append("1. Similar in style and format to existing values\n");
            prompt.append("2. Different from all existing values\n");
            prompt.append("3. Appropriate for parameter '").append(parameterInfo.getName()).append("'\n");
            prompt.append("4. Return ONLY the value, no explanations\n");

            String systemContent = "You are a value generation expert. Generate realistic, diverse values that match the pattern of existing values.";

            String result = llmService.generateText(systemContent, prompt.toString(), 10, 0.7); // Higher temperature for diversity

            if (result != null && !result.trim().isEmpty()) {
                return result.trim();
            }

            return null;

        } catch (Exception e) {
            log.debug("LLM value variation generation failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Recursively extract values from JSON node based on parameter characteristics
     */
    private void extractValuesFromJsonNode(JsonNode node, ParameterInfo parameterInfo, Set<String> extractedValues) {
        if (node == null || extractedValues.size() >= 10) { // Limit to 10 diverse values
            return;
        }

        String paramName = parameterInfo.getName().toLowerCase();
        String paramType = parameterInfo.getType();

        if (node.isArray()) {
            // Extract from array elements
            for (JsonNode arrayElement : node) {
                extractValuesFromJsonNode(arrayElement, parameterInfo, extractedValues);
            }
        } else if (node.isObject()) {
            // Extract from object fields
            node.fields().forEachRemaining(entry -> {
                String fieldName = entry.getKey().toLowerCase();
                JsonNode fieldValue = entry.getValue();

                // Check if field name matches parameter name or type
                if (isRelevantField(fieldName, paramName, paramType)) {
                    if (fieldValue.isTextual()) {
                        String value = fieldValue.asText().trim();
                        if (!value.isEmpty() && isValidValueForParameter(value, parameterInfo)) {
                            extractedValues.add(value);
                        }
                    } else if (fieldValue.isNumber()) {
                        extractedValues.add(fieldValue.asText());
                    }
                }

                // Recursively search in nested objects/arrays
                extractValuesFromJsonNode(fieldValue, parameterInfo, extractedValues);
            });
        }
    }

    /**
     * Check if a field is relevant for the parameter.
     *
     * <p>Bug audit Finding #39: previously this issued a fresh LLM call per JSON field —
     * for a typical 20-field response, that meant 20 LLM round-trips per fetch. We now
     * accept deterministic matches (substring, ID-stem, normalized equality) and fall back
     * to the LLM only when no deterministic signal is present.</p>
     */
    private boolean isRelevantField(String fieldName, String paramName, String paramType) {
        if (fieldName == null || paramName == null) return false;
        String fnLower = fieldName.toLowerCase(java.util.Locale.ROOT);
        String pnLower = paramName.toLowerCase(java.util.Locale.ROOT);
        if (fnLower.equals(pnLower)) return true;
        if (fnLower.contains(pnLower) || pnLower.contains(fnLower)) return true;
        // ID-stem heuristic: orderId ↔ id, accountId ↔ accountId / account
        if (isIdLikeParamName(paramName) && (fnLower.equals("id") || fnLower.endsWith("id"))) {
            return true;
        }
        // Numeric field for numeric parameter — let extraction try it.
        if (paramType != null) {
            String t = paramType.toLowerCase(java.util.Locale.ROOT);
            if ((t.contains("int") || t.contains("number") || t.contains("double")
                    || t.contains("float")) && (fnLower.contains("amount") || fnLower.contains("price")
                    || fnLower.contains("rate") || fnLower.contains("count")
                    || fnLower.contains("size") || fnLower.contains("number"))) {
                return true;
            }
        }
        // Fresh-review Finding F30: when no deterministic check matches, return false
        // rather than firing a per-field LLM call. A 30-field response would otherwise
        // trigger up to 30 LLM round-trips per fetch — the dominant performance cost on
        // trace-rich responses, with rate-limited APIs (Gemini, OpenAI) throttling.
        return false;
    }

    /**
     * Ask LLM to determine if a field is relevant for a parameter
     */
    private boolean askLLMForFieldRelevance(String fieldName, String paramName, String paramType) {
        try {
            String prompt = buildFieldRelevancePrompt(fieldName, paramName, paramType);

            if (prompt.length() > 500) { // Keep prompt short for efficiency
                // Fallback to simple name matching for very long prompts
                return fieldName.toLowerCase().contains(paramName.toLowerCase()) ||
                       paramName.toLowerCase().contains(fieldName.toLowerCase());
            }

            String llmResponse = askLLMForFieldRelevanceDecision(prompt);

            if (llmResponse != null && !llmResponse.trim().isEmpty()) {
                String cleanResponse = llmResponse.trim().toLowerCase();
                return cleanResponse.equals("yes") || cleanResponse.equals("true") || cleanResponse.equals("relevant");
            }

            // Fallback to simple matching if LLM fails
            return fieldName.toLowerCase().contains(paramName.toLowerCase());

        } catch (Exception e) {
            log.debug("LLM field relevance check failed for field '{}' and parameter '{}': {}",
                     fieldName, paramName, e.getMessage());
            // Fallback to simple matching
            return fieldName.toLowerCase().contains(paramName.toLowerCase());
        }
    }

    /**
     * Build prompt for LLM field relevance analysis
     */
    private String buildFieldRelevancePrompt(String fieldName, String paramName, String paramType) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("Determine if the JSON field '").append(fieldName).append("' is relevant ");
        prompt.append("for extracting values for parameter '").append(paramName).append("' ");
        prompt.append("(type: ").append(paramType).append(").\n\n");

        prompt.append("Consider semantic similarity, naming patterns, and data types.\n");
        prompt.append("Examples of relevant matches:\n");
        prompt.append("- Field 'itemName' is relevant for parameter 'productName'\n");
        prompt.append("- Field 'itemId' is relevant for parameter 'itemNumber'\n");
        prompt.append("- Field 'categoryType' is relevant for parameter 'categoryClass'\n");
        prompt.append("- Field 'userId' is relevant for parameter 'loginId'\n\n");

        prompt.append("Respond with 'YES' if relevant, 'NO' if not relevant.");

        return prompt.toString();
    }

    /**
     * Ask LLM for field relevance decision
     */
    private String askLLMForFieldRelevanceDecision(String prompt) {
        try {
            String systemContent = "You are a data extraction expert. Determine if JSON fields are semantically relevant for parameter extraction. Be precise and consider naming patterns, semantic similarity, and data types.";

            String result = llmService.generateText(systemContent, prompt, 10, 0.1); // Low temperature for consistency

            if (result != null && !result.trim().isEmpty()) {
                return result.trim();
            }

            return null;

        } catch (Exception e) {
            log.debug("LLM field relevance decision failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Validate if a value is suitable for the parameter and format it according to OpenAPI schema
     */
     private boolean isValidValueForParameter(String value, ParameterInfo parameterInfo) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }

        String cleanValue = value.trim().toLowerCase();
        String paramName = parameterInfo.getName().toLowerCase();

        // Reject nonsensical LLM hallucinations
        if (isNonsensicalValue(cleanValue, paramName, parameterInfo)) {
            log.debug("Rejecting nonsensical value '{}' for parameter '{}'", value, parameterInfo.getName());
            return false;
        }

        // Reject values that exceed the schema's declared maxLength (with a small slack to avoid
        // off-by-one rejections), or — when no schema bound is declared — a generous safety
        // ceiling. The previous hard-coded 100-char cap was rejecting legitimate long station
        // names / descriptions and any OVERFLOW/SPECIAL_CHARACTERS payload that legitimately
        // needs to exceed 100 chars.
        Integer maxLen = parameterInfo.getMaxLength();
        int allowed;
        if (maxLen != null && maxLen > 0) {
            // Allow the schema max plus a small buffer for boundary-violation values that
            // intentionally exceed the cap. The fault-pool callers don't go through this
            // validator, so values that reach here are either positive or LLM-generated.
            allowed = maxLen + 100;
        } else {
            allowed = DEFAULT_MAX_VALUE_LENGTH;
        }
        if (value.length() > allowed) {
            log.debug("Rejecting overly long value (len={}, allowed={}) for parameter '{}'",
                    value.length(), allowed, parameterInfo.getName());
            return false;
        }

        // For ID parameters, be more strict (boundary-aware: paid/valid/humid are NOT IDs)
        if (isIdLikeParamName(paramName)) {
            return isValidIdValue(value, parameterInfo);
        }

        // Fresh-review Finding F28: enforce schema {@code minimum}/{@code maximum} on numeric
        // parameters. Without this, an LLM-emitted value like {@code "-99999"} for a parameter
        // declared with {@code minimum: 0} passed validation, was cached, and surfaced as a
        // 400 from the SUT.
        if (isNumericSchemaType(parameterInfo)
                && (parameterInfo.getMinimum() != null || parameterInfo.getMaximum() != null)) {
            try {
                double parsed = Double.parseDouble(value.trim());
                if (parameterInfo.getMinimum() != null
                        && parsed < parameterInfo.getMinimum().doubleValue()) {
                    log.debug("Rejecting value '{}' below schema minimum {} for parameter '{}'",
                            value, parameterInfo.getMinimum(), parameterInfo.getName());
                    return false;
                }
                if (parameterInfo.getMaximum() != null
                        && parsed > parameterInfo.getMaximum().doubleValue()) {
                    log.debug("Rejecting value '{}' above schema maximum {} for parameter '{}'",
                            value, parameterInfo.getMaximum(), parameterInfo.getName());
                    return false;
                }
            } catch (NumberFormatException ignored) {
                // Non-parseable — let other validators handle it; bounds-check doesn't apply.
            }
        }

        // Accept reasonable values
        return true;
    }

    /**
     * Check if a value is nonsensical LLM hallucination
     */
    private boolean isNonsensicalValue(String cleanValue, String paramName, ParameterInfo parameterInfo) {
        // Generic/meaningless terms
        if (cleanValue.equals("objects") || cleanValue.equals("service") || cleanValue.equals("data") ||
            cleanValue.equals("response") || cleanValue.equals("result") || cleanValue.equals("value") ||
            cleanValue.equals("item") || cleanValue.equals("element") || cleanValue.equals("field")) {
            return true;
        }

        // UUID values for non-ID parameters (major issue!)
        if (isUUIDValue(cleanValue) && !isIdLikeParamName(paramName)) {
            log.debug("Rejecting UUID value '{}' for non-ID parameter '{}'", cleanValue, paramName);
            return true;
        }

        // Fresh-review Finding F6: numeric parameters should not have non-numeric values
        // (still allow values with units). Gate on the schema type, not on parameter-name
        // substrings — {@code distanceUnit}, {@code priceListDescription} are strings whose
        // value-class is determined by the OAS, not by the substring "distance" / "price".
        if (parameterInfo != null && isNumericSchemaType(parameterInfo)) {
            String numericPart = extractNumericPart(cleanValue);
            if (numericPart == null || numericPart.trim().isEmpty()) {
                log.debug("Rejecting non-numeric value '{}' for numeric parameter '{}'", cleanValue, paramName);
                return true;
            }
        }

        // LLM explanatory text patterns
        if (cleanValue.contains("the format appears to be") ||
            cleanValue.contains("delivery route") ||
            cleanValue.contains("this is a") ||
            cleanValue.contains("represents a") ||
            cleanValue.contains("should be a") ||
            cleanValue.contains("example of") ||
            cleanValue.contains("type of")) {
            return true;
        }

        // Partial sentences or fragments
        if (cleanValue.contains("). the") || cleanValue.contains(", which") ||
            cleanValue.contains("such as") || cleanValue.contains("for example")) {
            return true;
        }

        return false;
    }

    /**
     * Check if a value is a UUID
     */
    private boolean isUUIDValue(String value) {
        if (value == null || value.length() != 36) {
            return false;
        }
        return value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    }

    /**
     * Check if a value is numeric (including values with units like "10 miles")
     */
    private boolean isNumericValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }

        String cleanValue = value.trim();

        // Try direct parsing first
        try {
            Double.parseDouble(cleanValue);
            return true;
        } catch (NumberFormatException e) {
            // If direct parsing fails, try extracting numeric part
            return extractNumericPart(cleanValue) != null;
        }
    }

    /**
     * Extract numeric part from values like "10 miles", "150.5 km", etc.
     */
    private String extractNumericPart(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        String cleanValue = value.trim();

        // Pattern to match numbers at the beginning (with optional decimal)
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("^([0-9]+\\.?[0-9]*)");
        java.util.regex.Matcher matcher = pattern.matcher(cleanValue);

        if (matcher.find()) {
            String numericPart = matcher.group(1);
            try {
                // Validate it's a valid number
                Double.parseDouble(numericPart);
                return numericPart;
            } catch (NumberFormatException e) {
                return null;
            }
        }

        return null;
    }

    /**
     * Validate ID values more strictly
     */
    private boolean isValidIdValue(String value, ParameterInfo parameterInfo) {
        String cleanValue = value.trim();

        // Accept UUIDs
        if (cleanValue.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
            return true;
        }

        // Accept numeric IDs
        if (cleanValue.matches("\\d+")) {
            return true;
        }

        // Accept opaque alphanumeric IDs (any SUT's id scheme): no whitespace, a generous
        // length cap, and the common id punctuation including ':' (e.g. composite/URN-style
        // keys). Avoids over-restricting to UUID / {1,20}-char tokens, which rejected valid
        // non-train-ticket identifiers.
        if (cleanValue.matches("[a-zA-Z0-9._:-]{1,64}")) {
            return true;
        }

        // Reject descriptive text for ID fields
        if (cleanValue.length() > 50 || cleanValue.contains(" ")) {
            return false;
        }

        return true;
    }

    /**
     * Format value according to OpenAPI schema type - but return raw values for Smart Fetch
     * Let the test generation system handle final formatting based on schema
     */
    private String formatValueForSchema(String value, ParameterInfo parameterInfo) {
        if (value == null || parameterInfo == null) {
            return value;
        }

        try {
            // Get the actual OpenAPI schema type for this parameter
            String schemaType = getOpenAPISchemaType(parameterInfo);
            String schemaFormat = getOpenAPISchemaFormat(parameterInfo);

            log.debug("Smart Fetch returning raw value '{}' for parameter '{}' with schema type: {}, format: {}",
                     value, parameterInfo.getName(), schemaType, schemaFormat);

            // For Smart Fetch, return raw values and let test generation handle formatting
            // Only do basic validation and cleanup
            if ("integer".equals(schemaType)) {
                return cleanIntegerValue(value, parameterInfo);
            } else if ("number".equals(schemaType)) {
                return cleanNumberValue(value, parameterInfo);
            } else if ("boolean".equals(schemaType)) {
                return cleanBooleanValue(value, parameterInfo);
            } else if ("array".equals(schemaType)) {
                // For array parameters, format as JSON array
                return formatAsArrayValue(value, parameterInfo);
            } else {
                // For string parameters, return raw value
                return cleanStringValue(value, parameterInfo);
            }

        } catch (Exception e) {
            log.debug("Failed to format value '{}' for parameter '{}': {}",
                     value, parameterInfo.getName(), e.getMessage());
            return value; // Return original value if formatting fails
        }
    }

    /**
     * Clean an integer-typed value extracted from an API response or LLM output.
     * Preserves int64 magnitudes (uses {@link Long#parseLong} with a {@link java.math.BigInteger}
     * fallback) and truncates fractional digits toward zero. Leading sign is preserved only when
     * it appears at the start of the string; embedded dashes (e.g., {@code "order-id-12345"}) are
     * treated as separators and the trailing digit run is used.
     */
    private static final java.util.regex.Pattern INTEGER_LEADING_PATTERN =
            java.util.regex.Pattern.compile("^-?\\d+");
    private static final java.util.regex.Pattern INTEGER_ANY_DIGIT_RUN =
            java.util.regex.Pattern.compile("\\d+");

    private String cleanIntegerValue(String value, ParameterInfo parameterInfo) {
        if (value == null || value.trim().isEmpty()) {
            // Bug audit Finding #36: respect schema bounds when synthesizing the fallback.
            return integerFallbackForSchema(parameterInfo);
        }
        String trimmed = value.trim();

        java.util.regex.Matcher leading = INTEGER_LEADING_PATTERN.matcher(trimmed);
        if (leading.find()) {
            String parsed = parseIntegerLiteral(leading.group());
            if (parsed != null) return clampIntegerString(parsed, parameterInfo);
        }
        java.util.regex.Matcher anyRun = INTEGER_ANY_DIGIT_RUN.matcher(trimmed);
        if (anyRun.find()) {
            String parsed = parseIntegerLiteral(anyRun.group());
            if (parsed != null) return clampIntegerString(parsed, parameterInfo);
        }
        log.debug("Failed to clean integer value '{}' for parameter '{}'", value, parameterInfo.getName());
        return integerFallbackForSchema(parameterInfo);
    }

    /**
     * Fresh-review Finding F15: clamp the parsed value to the parameter's declared
     * {@code minimum}/{@code maximum}. Companion {@code clampToIntegerBounds} was already
     * defined for the algorithmic fallback path; this brings the cleaner into parity.
     */
    private static String clampIntegerString(String parsed, ParameterInfo parameterInfo) {
        try {
            long parsedLong = Long.parseLong(parsed);
            return String.valueOf(clampToIntegerBounds(parsedLong, parameterInfo));
        } catch (NumberFormatException ignored) {
            // BigInteger overflow path — leave the parsed value as-is; the validator will
            // reject it if it exceeds bounds (Finding F28).
            return parsed;
        }
    }

    private static String integerFallbackForSchema(ParameterInfo parameterInfo) {
        Number min = parameterInfo.getMinimum();
        Number max = parameterInfo.getMaximum();
        if (min != null) return String.valueOf(min.longValue());
        if (max != null && max.longValue() < 1L) return String.valueOf(max.longValue());
        return "1";
    }

    private static String numberFallbackForSchema(ParameterInfo parameterInfo) {
        Number min = parameterInfo.getMinimum();
        Number max = parameterInfo.getMaximum();
        if (min != null) return String.valueOf(min.doubleValue());
        if (max != null && max.doubleValue() < 1.0) return String.valueOf(max.doubleValue());
        return "1.0";
    }

    private static String parseIntegerLiteral(String candidate) {
        if (candidate == null || candidate.isEmpty() || candidate.equals("-")) return null;
        try {
            return String.valueOf(Long.parseLong(candidate));
        } catch (NumberFormatException ignored) {
            try {
                return new java.math.BigInteger(candidate).toString();
            } catch (NumberFormatException ignored2) {
                return null;
            }
        }
    }

    /**
     * Clean number value without full formatting
     */
    private String cleanNumberValue(String value, ParameterInfo parameterInfo) {
        if (value == null || value.trim().isEmpty()) {
            return numberFallbackForSchema(parameterInfo);
        }

        try {
            // Remove non-numeric characters except minus sign and decimal point
            String cleanValue = value.replaceAll("[^0-9.-]", "");

            if (cleanValue.isEmpty() || cleanValue.equals("-") || cleanValue.equals(".")) {
                return numberFallbackForSchema(parameterInfo);
            }

            // Parse as double to validate, then clamp to the parameter's declared
            // {@code minimum}/{@code maximum} (Fresh-review Finding F15).
            double doubleValue = Double.parseDouble(cleanValue);
            return String.valueOf(clampToNumberBounds(doubleValue, parameterInfo));

        } catch (NumberFormatException e) {
            log.debug("Failed to clean number value '{}' for parameter '{}': {}",
                     value, parameterInfo.getName(), e.getMessage());
            return numberFallbackForSchema(parameterInfo);
        }
    }

    /**
     * Clean a boolean value extracted by smart-fetch. Only accepts the strict literal
     * tokens {@code "true"} / {@code "false"} (case-insensitive) plus {@code "1"}/{@code "0"}.
     * Synonyms like {@code yes/on/enabled/active} are rejected — they laundered LLM
     * type-mismatches as {@code true} and hid genuine boolean fault types
     * (Bug audit Finding #17). When the value cannot be cleanly resolved, return the raw
     * trimmed value so the downstream validator can reject it instead of silently coercing.
     */
    private String cleanBooleanValue(String value, ParameterInfo parameterInfo) {
        if (value == null || value.trim().isEmpty()) {
            return "false";
        }
        String cleanValue = value.trim().toLowerCase(java.util.Locale.ROOT);
        if (cleanValue.equals("true") || cleanValue.equals("1")) return "true";
        if (cleanValue.equals("false") || cleanValue.equals("0")) return "false";
        return value.trim();
    }

    

    /**
     * Clean string value without full formatting
     */
    private String cleanStringValue(String value, ParameterInfo parameterInfo) {
        if (value == null) {
            return "";
        }

        String cleanValue = value.trim();

        // Remove quotes if present (they'll be added by JSON serialization)
        if (cleanValue.startsWith("\"") && cleanValue.endsWith("\"") && cleanValue.length() > 1) {
            cleanValue = cleanValue.substring(1, cleanValue.length() - 1);
        }

        // Remove array brackets if present - let test generation handle arrays
        if (cleanValue.startsWith("[") && cleanValue.endsWith("]")) {
            // Extract first element from array string
            cleanValue = cleanValue.substring(1, cleanValue.length() - 1);
            if (cleanValue.contains(",")) {
                cleanValue = cleanValue.split(",")[0].trim();
            }
            // Remove quotes from extracted element
            if (cleanValue.startsWith("\"") && cleanValue.endsWith("\"") && cleanValue.length() > 1) {
                cleanValue = cleanValue.substring(1, cleanValue.length() - 1);
            }
        }

        return cleanValue;
    }

    /**
     * Get OpenAPI schema type for parameter by reading the actual schema
     */
    private String getOpenAPISchemaType(ParameterInfo parameterInfo) {
        try {
            // 1) Prefer exact schema type coming from OpenAPI if available
            String declaredSchemaType = parameterInfo.getSchemaType();
            if (declaredSchemaType != null && !declaredSchemaType.trim().isEmpty() && !"null".equals(declaredSchemaType)) {
                String lowerSchemaType = declaredSchemaType.toLowerCase().trim();
                if (lowerSchemaType.equals("integer") || lowerSchemaType.equals("number") ||
                    lowerSchemaType.equals("string") || lowerSchemaType.equals("boolean") ||
                    lowerSchemaType.equals("array")) {
                    return lowerSchemaType;
                }
            }

            // 2) Fall back to generic type hints if provided
            String paramType = parameterInfo.getType();
            if (paramType != null && !paramType.trim().isEmpty() && !paramType.equals("null")) {
                String lowerType = paramType.toLowerCase().trim();

                // Direct type mapping
                if (lowerType.contains("integer") || lowerType.contains("int")) {
                    return "integer";
                } else if (lowerType.contains("number") || lowerType.contains("double") || lowerType.contains("float")) {
                    return "number";
                } else if (lowerType.contains("boolean") || lowerType.contains("bool")) {
                    return "boolean";
                } else if (lowerType.contains("array")) {
                    return "array";
                } else if (lowerType.contains("string")) {
                    return "string";
                }
            }

            // Fallback: use parameter name-based heuristics with OpenAPI knowledge
            return inferSchemaTypeFromParameterName(parameterInfo);

        } catch (Exception e) {
            log.debug("Failed to get OpenAPI schema type for parameter '{}': {}",
                     parameterInfo.getName(), e.getMessage());
            return "string"; // Safe default
        }
    }

    /**
     * Get OpenAPI schema format for parameter
     */
    private String getOpenAPISchemaFormat(ParameterInfo parameterInfo) {
        try {
            String paramType = parameterInfo.getType();
            if (paramType != null && paramType.toLowerCase().contains("int32")) {
                return "int32";
            } else if (paramType != null && paramType.toLowerCase().contains("int64")) {
                return "int64";
            }
            return null; // No specific format
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Infer schema type from parameter name when no OpenAPI declaration is available.
     *
     * <p>Fresh-review Finding F29: previously this fired an LLM call per fetch when
     * {@code parameterInfo.getSchemaType()/getType()} were null/empty — extra latency for
     * a one-word answer the LLM rarely improves on. We now default to {@code "string"}
     * (the safest unconstrained type) without consulting the LLM. The 99 % case
     * already-typed parameters never reach this branch; the 1 % missing-type case spends
     * its budget on the actual fetch instead.</p>
     */
    private String inferSchemaTypeFromParameterName(ParameterInfo parameterInfo) {
        return "string";
    }

    /**
     * Ask LLM to infer OpenAPI schema type from parameter characteristics
     */
    private String askLLMForSchemaTypeInference(ParameterInfo parameterInfo) {
        try {
            String prompt = buildSchemaTypeInferencePrompt(parameterInfo);

            if (prompt.length() > 800) {
                log.warn("Schema type inference prompt too long ({} chars), using fallback", prompt.length());
                return "string";
            }

            String systemContent = "You are an OpenAPI schema expert. Analyze parameter characteristics to determine the most likely OpenAPI schema type. " +
                                  "Consider parameter names, descriptions, and common API patterns. " +
                                  "Respond with exactly one word: integer, number, string, boolean, or array.";

            String result = llmService.generateText(systemContent, prompt, 10, 0.1); // Low temperature for consistency

            if (result != null && !result.trim().isEmpty()) {
                log.debug("[Schema Type Inference LLM] Inferred type: {}", result.trim());
                return result.trim();
            }

            return null;

        } catch (Exception e) {
            log.debug("LLM schema type inference failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Build prompt for LLM schema type inference
     */
    private String buildSchemaTypeInferencePrompt(ParameterInfo parameterInfo) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("Determine the most likely OpenAPI schema type for this parameter:\n\n");
        prompt.append("Parameter name: ").append(parameterInfo.getName()).append("\n");

        if (parameterInfo.getType() != null && !parameterInfo.getType().trim().isEmpty() &&
            !parameterInfo.getType().equals("null")) {
            prompt.append("Declared type: ").append(parameterInfo.getType()).append("\n");
        }

        if (parameterInfo.getDescription() != null && !parameterInfo.getDescription().trim().isEmpty() &&
            !parameterInfo.getDescription().equals("null")) {
            prompt.append("Description: ").append(parameterInfo.getDescription()).append("\n");
        }

        prompt.append("\nOpenAPI schema types:\n");
        prompt.append("- integer: whole numbers (seatClass, coachNumber, status)\n");
        prompt.append("- number: decimal numbers (price, rate, distance)\n");
        prompt.append("- string: text values (name, id, description)\n");
        prompt.append("- boolean: true/false values (enabled, active, valid)\n");
        prompt.append("- array: collections of values (stations, distances, items)\n\n");

        prompt.append("Based on the parameter name and characteristics, what is the most likely schema type?\n");
        prompt.append("Respond with exactly one word: integer, number, string, boolean, or array");

        return prompt.toString();
    }

    /**
     * Format value as integer
     */
    private String formatAsIntegerValue(String value, ParameterInfo parameterInfo, String format) {
        if (value == null || value.trim().isEmpty()) {
            return "0";
        }

        try {
            // Remove non-numeric characters except minus sign
            String cleanValue = value.replaceAll("[^0-9-]", "");

            if (cleanValue.isEmpty() || cleanValue.equals("-")) {
                return "0";
            }

            // Parse as integer to validate
            int intValue = Integer.parseInt(cleanValue);

            // Apply format constraints
            if ("int32".equals(format)) {
                // Ensure within int32 range
                if (intValue < Integer.MIN_VALUE || intValue > Integer.MAX_VALUE) {
                    return "0";
                }
            }

            return String.valueOf(intValue);

        } catch (NumberFormatException e) {
            log.debug("Failed to format '{}' as integer for parameter '{}': {}",
                     value, parameterInfo.getName(), e.getMessage());
            return "0"; // Safe fallback
        }
    }

    /**
     * Format value as boolean — strict (only literal {@code true}/{@code false}/{@code 1}/{@code 0}).
     * Companion to {@link #cleanBooleanValue}; same rationale (Bug audit Finding #17).
     */
    private String formatAsBooleanValue(String value, ParameterInfo parameterInfo) {
        return cleanBooleanValue(value, parameterInfo);
    }

    /**
     * Format value as string (with validation)
     */
    private String formatAsStringValue(String value, ParameterInfo parameterInfo) {
        if (value == null) {
            return "";
        }

        String cleanValue = value.trim();

        // Remove quotes if present (they'll be added by JSON serialization)
        if (cleanValue.startsWith("\"") && cleanValue.endsWith("\"") && cleanValue.length() > 1) {
            cleanValue = cleanValue.substring(1, cleanValue.length() - 1);
        }

        return cleanValue;
    }

    /**
     * Check if parameter should be array type based on OpenAPI schema
     */
    private boolean shouldBeArrayType(ParameterInfo parameterInfo) {
        // Use the new schema type detection
        String schemaType = getOpenAPISchemaType(parameterInfo);
        boolean isArray = "array".equals(schemaType);

        log.debug("Parameter '{}' schema type: '{}', isArray: {}",
                 parameterInfo.getName(), schemaType, isArray);

        return isArray;
    }

    /**
     * Ask LLM to determine if parameter should be array type
     */
    private boolean askLLMForArrayTypeDecision(ParameterInfo parameterInfo) {
        try {
            String prompt = buildArrayTypePrompt(parameterInfo);

            if (prompt.length() > 500) {
                // Fallback to name-based heuristics
                String paramName = parameterInfo.getName().toLowerCase();
                return paramName.contains("list") || paramName.contains("stations") ||
                       paramName.contains("distances") || paramName.endsWith("s");
            }

            String llmResponse = askLLMForArrayTypeDecisionCall(prompt);

            if (llmResponse != null && !llmResponse.trim().isEmpty()) {
                String cleanResponse = llmResponse.trim().toLowerCase();
                return cleanResponse.equals("yes") || cleanResponse.equals("true") || cleanResponse.equals("array");
            }

            return false;

        } catch (Exception e) {
            log.debug("LLM array type decision failed for parameter '{}': {}",
                     parameterInfo.getName(), e.getMessage());
            return false;
        }
    }

    /**
     * Build prompt for array type decision
     */
    private String buildArrayTypePrompt(ParameterInfo parameterInfo) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("Determine if parameter '").append(parameterInfo.getName()).append("' ");
        prompt.append("(type: ").append(parameterInfo.getType()).append(") should be an array type ");
        prompt.append("based on OpenAPI schema conventions.\n\n");

        if (parameterInfo.getDescription() != null && !parameterInfo.getDescription().trim().isEmpty()) {
            prompt.append("Description: ").append(parameterInfo.getDescription()).append("\n\n");
        }

        prompt.append("Array type indicators:\n");
        prompt.append("- Parameter names ending with 's' (stations, distances)\n");
        prompt.append("- Parameter names containing 'list' (stationList, distanceList)\n");
        prompt.append("- Parameters that represent collections or multiple values\n\n");

        prompt.append("Respond with 'YES' if should be array, 'NO' if should be string/primitive.");

        return prompt.toString();
    }

    /**
     * Ask LLM for array type decision
     */
    private String askLLMForArrayTypeDecisionCall(String prompt) {
        try {
            String systemContent = "You are an OpenAPI schema expert. Determine if parameters should be array types based on naming conventions and semantic meaning.";

            String result = llmService.generateText(systemContent, prompt, 10, 0.1);

            if (result != null && !result.trim().isEmpty()) {
                return result.trim();
            }

            return null;

        } catch (Exception e) {
            log.debug("LLM array type decision call failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Format value as array - FIXED to avoid string-wrapped JSON.
     *
     * <p>Bug audit Finding #34: rejects sentinel/error tokens like {@code NO_GOOD_MATCH},
     * {@code NO_VALUES_FOUND}, {@code NO_MATCH} so the array does not end up containing
     * them as data. The Stream 1 whitelist already prevents these from reaching the value
     * pipeline, but this is a belt-and-braces guard.</p>
     */
    private String formatAsArrayValue(String value, ParameterInfo parameterInfo) {
        if (value == null || value.trim().isEmpty()) {
            return "[]";
        }

        try {
            String trimmedValue = value.trim();
            // Bug audit Finding #34: drop LLM sentinel tokens before wrapping as array.
            String upper = trimmedValue.toUpperCase(java.util.Locale.ROOT);
            if (upper.equals("NO_GOOD_MATCH") || upper.equals("NO_VALUES_FOUND")
                    || upper.equals("NO_MATCH") || upper.equals("NO_VALUES_GENERATED")) {
                log.debug("Refusing to wrap LLM sentinel '{}' as array element for parameter '{}'",
                        trimmedValue, parameterInfo.getName());
                return "[]";
            }

            // CRITICAL FIX: If value is already a JSON array string, parse and return properly
            if (trimmedValue.startsWith("[") && trimmedValue.endsWith("]")) {
                // Try to parse as JSON to validate it's a proper array
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode arrayNode = mapper.readTree(trimmedValue);
                    if (arrayNode.isArray()) {
                        return trimmedValue; // It's already a valid JSON array
                    }
                } catch (Exception e) {
                    log.debug("Value looks like array but isn't valid JSON: '{}'", trimmedValue);
                }
            }

            // CRITICAL FIX: Don't wrap strings that are already JSON in quotes
            // This was causing "seatClass": "[\"1\"]" instead of "seatClass": ["1"]
            if (trimmedValue.startsWith("\"[") && trimmedValue.endsWith("]\"")) {
                // Remove outer quotes from JSON array string
                return trimmedValue.substring(1, trimmedValue.length() - 1);
            }

            // Split comma-separated values into array
            String[] parts;
            if (trimmedValue.contains(",")) {
                parts = trimmedValue.split(",");
            } else {
                parts = new String[]{trimmedValue};
            }

            // Build JSON array
            StringBuilder arrayBuilder = new StringBuilder("[");
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) arrayBuilder.append(", ");
                String part = parts[i].trim();
                // Remove quotes if already present
                if (part.startsWith("\"") && part.endsWith("\"")) {
                    arrayBuilder.append(part);
                } else {
                    arrayBuilder.append("\"").append(part).append("\"");
                }
            }
            arrayBuilder.append("]");

            return arrayBuilder.toString();

        } catch (Exception e) {
            log.debug("Failed to format '{}' as array for parameter '{}': {}",
                     value, parameterInfo.getName(), e.getMessage());
            return "[\"" + value + "\"]"; // Fallback: single-element array
        }
    }

    /**
     * Check if parameter should be number type (deprecated - use getOpenAPISchemaType instead)
     */
    private boolean shouldBeNumberType(ParameterInfo parameterInfo) {
        String schemaType = getOpenAPISchemaType(parameterInfo);
        return "number".equals(schemaType) || "integer".equals(schemaType);
    }

    /**
     * Format value as number
     */
    private String formatAsNumberValue(String value, ParameterInfo parameterInfo) {
        if (value == null || value.trim().isEmpty()) {
            return "0";
        }

        try {
            // Remove non-numeric characters except decimal point and minus
            String cleanValue = value.replaceAll("[^0-9.-]", "");

            if (cleanValue.isEmpty()) {
                return "0";
            }

            // Try to parse as number to validate
            Double.parseDouble(cleanValue);
            return cleanValue;

        } catch (NumberFormatException e) {
            log.debug("Failed to format '{}' as number for parameter '{}': {}",
                     value, parameterInfo.getName(), e.getMessage());
            return "0"; // Fallback
        }
    }

    /**
     * Build a cache key that distinguishes parameters not just by name, type, and location, but
     * also by their schema-level constraints. Two parameters that share a name but differ in
     * format/enum/bounds/length/regex must not share a cached value (the SmartInputFetcher used
     * to leak values across services and operations because the key was too coarse).
     */
    private String buildCacheKey(ParameterInfo parameterInfo) {
        String name     = parameterInfo.getName()       != null ? parameterInfo.getName()       : "unknown";
        String type     = parameterInfo.getType()       != null ? parameterInfo.getType()       : "unknown";
        String location = parameterInfo.getInLocation() != null ? parameterInfo.getInLocation() : "unknown";
        String format   = parameterInfo.getFormat()     != null ? parameterInfo.getFormat()     : "";
        String enums    = parameterInfo.hasEnum() && parameterInfo.getEnumValues() != null
                ? String.join(",", parameterInfo.getEnumValues())
                : "";
        String bounds   = (parameterInfo.getMinimum()   != null ? parameterInfo.getMinimum()   : "")
                + ".." + (parameterInfo.getMaximum()   != null ? parameterInfo.getMaximum()   : "");
        String lengths  = (parameterInfo.getMinLength() != null ? parameterInfo.getMinLength() : "")
                + ".." + (parameterInfo.getMaxLength() != null ? parameterInfo.getMaxLength() : "");
        String regex    = parameterInfo.getRegex()      != null ? parameterInfo.getRegex()      : "";
        return name + ":" + type + ":" + location + ":" + format + ":" + enums
                + ":" + bounds + ":" + lengths + ":" + regex;
    }

    /**
     * Read the response body as UTF-8 (Fresh-review Finding F14: was using the platform
     * default charset, which mangled non-ASCII payloads on non-UTF-8 hosts). Falls back to
     * the connection's error stream when the input stream is not available — mirrors the
     * pattern used by {@code SmartFetchAuthManager.readResponse}.
     */
    private String readResponse(HttpURLConnection conn) throws IOException {
        java.io.InputStream stream;
        try {
            stream = conn.getInputStream();
        } catch (IOException e) {
            stream = conn.getErrorStream();
            if (stream == null) throw e;
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            char[] buf = new char[4096];
            int n;
            while ((n = reader.read(buf)) >= 0) {
                response.append(buf, 0, n);
            }
            return response.toString();
        }
    }






    /**
     * Truncate response schema to fit within LLM message length limits
     */
    private String truncateResponseSchemaForLLM(String responseSchema, String template, ParameterInfo parameterInfo) {
        if (responseSchema == null) return "";

        // Calculate space available for schema (leave 100 chars buffer)
        String tempPrompt = template
                .replace("{responseSchema}", "")
                .replace("{parameterName}", parameterInfo.getName() != null ? parameterInfo.getName() : "")
                .replace("{parameterType}", parameterInfo.getType() != null ? parameterInfo.getType() : "")
                .replace("{parameterDescription}", parameterInfo.getDescription() != null ? parameterInfo.getDescription() : "");

        // Bug audit Finding #10 finish: derive the schema budget from the configured prompt
        // cap rather than the legacy 1500 hardcode (which was tuned for a 2044-char total).
        int maxSchemaLength = Math.max(256, config.getMaxPromptChars() - 500 - tempPrompt.length());

        if (responseSchema.length() <= maxSchemaLength) {
            return responseSchema;
        }

        log.info("Response schema too long ({} chars), truncating to {} chars for parameter '{}'",
                responseSchema.length(), maxSchemaLength, parameterInfo.getName());

        // Try to truncate intelligently
        try {
            // If it's JSON, try to keep complete objects/arrays
            if (responseSchema.trim().startsWith("{") || responseSchema.trim().startsWith("[")) {
                return truncateJsonIntelligently(responseSchema, maxSchemaLength);
            } else {
                // Simple truncation for non-JSON data
                return responseSchema.substring(0, maxSchemaLength) + "...";
            }
        } catch (Exception e) {
            // Fallback to simple truncation
            return responseSchema.substring(0, Math.min(maxSchemaLength, responseSchema.length())) + "...";
        }
    }


    private String buildLLMDiscoveryPrompt(ParameterInfo parameterInfo, List<String> availableServices) {
        String template = registry.getLlmPrompts().get("apiDiscovery");

        // Limit services list to prevent message length issues (respect maxPromptChars)
        String servicesString = String.join(", ", availableServices);

        // First replace all parameters except availableServices
        String basePrompt = template
                .replace("{parameterName}", parameterInfo.getName() != null ? parameterInfo.getName() : "")
                .replace("{parameterType}", parameterInfo.getType() != null ? parameterInfo.getType() : "")
                .replace("{parameterDescription}", parameterInfo.getDescription() != null ? parameterInfo.getDescription() : "")
                .replace("{parameterLocation}", parameterInfo.getInLocation() != null ? parameterInfo.getInLocation() : "");

        // Bug audit Finding #10 finish: services list budget derives from configured prompt
        // cap minus a 100-char buffer for the rest of the template, instead of the legacy
        // hardcoded 1944 (= 2044 - 100 buffer).
        String tempPrompt = basePrompt.replace("{availableServices}", "");
        int maxServicesLength = Math.max(128, config.getMaxPromptChars() - 100 - tempPrompt.length());

        if (servicesString.length() > maxServicesLength) {
            // Truncate services list to fit within limit
            List<String> truncatedServices = new ArrayList<>();
            int currentLength = 0;

            for (String service : availableServices) {
                int serviceLength = service.length() + 2; // +2 for ", "
                if (currentLength + serviceLength > maxServicesLength) {
                    break;
                }
                truncatedServices.add(service);
                currentLength += serviceLength;
            }

            servicesString = String.join(", ", truncatedServices);
            if (truncatedServices.size() < availableServices.size()) {
                servicesString += " (and " + (availableServices.size() - truncatedServices.size()) + " more)";
            }
        }

        // Finally replace the availableServices placeholder
        return basePrompt.replace("{availableServices}", servicesString);
    }

    private List<String> askLLMForServices(String prompt) {
        try {
            // Call LLM directly for service discovery with appropriate system prompt
            String rawResponse = callLLMForServiceDiscovery(prompt);

            if (rawResponse != null && !rawResponse.trim().isEmpty()) {
                try {
                    // FIXED: Clean markdown code blocks from LLM response
                    String cleanedResponse = cleanJsonFromMarkdown(rawResponse);

                    JsonNode jsonResponse = objectMapper.readTree(cleanedResponse);
                    // Check for NO_GOOD_MATCH response
                    if (cleanedResponse.trim().equalsIgnoreCase("NO_GOOD_MATCH")) {
                        log.info("LLM indicated no good service matches for this parameter");
                        return new ArrayList<>();
                    }

                    if (jsonResponse.isArray()) {
                        List<String> services = new ArrayList<>();
                        jsonResponse.forEach(node -> {
                            String serviceName = node.asText().trim();
                            // Bug audit Finding #2: also drop the sentinel when the LLM
                            // emits it as a single element of a JSON array (a real failure
                            // mode that polluted the registry).
                            if (!serviceName.isEmpty()
                                    && !serviceName.equalsIgnoreCase("NO_GOOD_MATCH")) {
                                services.add(serviceName);
                            }
                        });

                        // Validate that we got reasonable results
                        if (services.size() > 0 && services.size() <= 5) {
                            log.info("LLM suggested {} services: {}", services.size(), services);
                            return services;
                        } else {
                            log.warn("LLM returned {} services (expected 1-5): {}", services.size(), services);
                            // Return first 3 if too many, or all if too few
                            return services.subList(0, Math.min(3, services.size()));
                        }
                    } else {
                        log.debug("LLM response is not a JSON array: {}", cleanedResponse);
                    }
                } catch (Exception e) {
                    log.debug("Failed to parse LLM response as JSON: {} (raw: '{}')", e.getMessage(), rawResponse);
                }
            }

            return new ArrayList<>();

        } catch (Exception e) {
            log.warn("Failed to ask LLM for services: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Call LLM directly for service discovery with appropriate system prompt
     */
    private String callLLMForServiceDiscovery(String prompt) {
        String systemContent =
                "You are an API testing assistant that helps identify which microservices " +
                "would most likely provide realistic data for given parameters. " +
                "Respond with a JSON array of service names in priority order. " +
                "Do NOT generate test values. Only return service names as a JSON array.";

        log.debug("[Service Discovery LLM] Using LLM service with model type: {}", llmService.getConfig().getModelType());
        log.debug("[Service Discovery LLM] User prompt: {}", prompt);

        try {
            String result = llmService.generateText(systemContent, prompt, 200, 0.7);

            if (result != null && !result.trim().isEmpty()) {
                log.debug("[Service Discovery LLM] Successfully generated content: {}", result);
                return result;
            } else {
                log.warn("[Service Discovery LLM] LLM service returned null or empty result");
                return null;
            }

        } catch (Exception e) {
            log.warn("[Service Discovery LLM] Failed to call LLM service: {}", e.getMessage());
            return null;
        }
    }

    /**
      * Call LLM directly for endpoint discovery with appropriate system prompt
      */
     private String callLLMForEndpointDiscovery(String prompt) {
         String systemContent =
                 "You are an API testing assistant that helps identify REST API endpoints " +
                 "within microservices that would provide data for given parameters. " +
                 "Respond with the most likely endpoint path (e.g., /api/v1/service/resource). " +
                 "Do NOT generate test values. Only return the endpoint path.";

         log.debug("[Endpoint Discovery LLM] Using LLM service with model type: {}", llmService.getConfig().getModelType());
         log.debug("[Endpoint Discovery LLM] User prompt: {}", prompt);

         try {
             String result = llmService.generateText(systemContent, prompt, 100, 0.3);

             if (result != null && !result.trim().isEmpty()) {
                 log.debug("[Endpoint Discovery LLM] Successfully generated content: {}", result);
                 return result;
             } else {
                 log.warn("[Endpoint Discovery LLM] LLM service returned null or empty result");
                 return null;
             }

         } catch (Exception e) {
             log.warn("[Endpoint Discovery LLM] Failed to call LLM service: {}", e.getMessage());
             return null;
         }
     }


      /**
       * Call LLM for direct value extraction from API response
       */
      private String callLLMForDirectValueExtractionFromResponse(String prompt) {
          String systemContent =
                  "You are an API testing assistant that extracts specific parameter values from JSON API responses. " +
                  "Given a JSON response and a parameter description, extract the most appropriate actual value from the response. " +
                  "CRITICAL RULES:\n" +
                  "1. Return ONLY the extracted value, not explanations or descriptions\n" +
                  "2. Do NOT return JSONPath expressions like $.data[*].name\n" +
                  "3. Do NOT return explanatory text like 'The format appears to be...'\n" +
                  "4. Do NOT return generic words like 'objects', 'data', 'items'\n" +
                  "5. For IDs: return actual ID values like 'route123' or 'abc-def-123'\n" +
                  "6. For names: return actual names like 'Acme Corp' or 'item-42'\n" +
                  "7. For numbers: return actual numbers like '100' or '25.5'\n" +
                  "8. If no suitable value exists, return 'NO_GOOD_MATCH'\n" +
                  "Examples: 'Acme Corp', 'route123', '25.5', 'SKU-1234' - NOT 'delivery route)', 'objects', 'The format appears to be UUID'";

          log.debug("[Direct Value Extraction LLM] Using LLM service with model type: {}", llmService.getConfig().getModelType());
          log.debug("[Direct Value Extraction LLM] User prompt: {}", prompt);

          try {
              String result = llmService.generateText(systemContent, prompt, 100, 0.3);

              if (result != null && !result.trim().isEmpty()) {
                  log.debug("[Direct Value Extraction LLM] Successfully generated content: {}", result);
                  return result;
              } else {
                  log.warn("[Direct Value Extraction LLM] LLM service returned null or empty result");
                  return null;
              }

          } catch (Exception e) {
              log.warn("[Direct Value Extraction LLM] Failed to call LLM service: {}", e.getMessage());
              return null;
          }
      }

      /**
       * Generate a meaningful value using LLM when API extraction fails
       */
      private String generateValueWithLLM(ParameterInfo parameterInfo) {
          try {
              log.info("🧠 Asking LLM to generate meaningful value for parameter '{}'", parameterInfo.getName());

              // Build prompt for value generation
              String prompt = buildValueGenerationPrompt(parameterInfo);

              if (prompt.length() > config.getMaxPromptChars()) {
                  log.warn("Value generation prompt too long ({} chars), using simple generation", prompt.length());
                  return generateSimpleValue(parameterInfo);
              }

              // Ask LLM to generate a meaningful value
              String llmResponse = askLLMForValueGeneration(prompt);

              if (llmResponse != null && !llmResponse.trim().isEmpty()) {
                  String cleanResponse = llmResponse.trim();

                  // Check if this is an array parameter
                  String schemaType = getOpenAPISchemaType(parameterInfo);
                  boolean isArrayParameter = "array".equals(schemaType);

                  if (isArrayParameter) {
                      // For array parameters, expect JSON array format
                      if (cleanResponse.startsWith("[") && cleanResponse.endsWith("]")) {
                          log.info("✅ LLM generated meaningful array value '{}' for parameter '{}'", cleanResponse, parameterInfo.getName());
                          return cleanResponse;
                      } else {
                          log.warn("❌ LLM returned non-array value '{}' for array parameter '{}', generating fallback array",
                                   cleanResponse, parameterInfo.getName());
                          return generateSimpleValue(parameterInfo);
                      }
                  } else {
                      // For single-value parameters, clean up quotes
                      if (cleanResponse.startsWith("\"") && cleanResponse.endsWith("\"")) {
                          cleanResponse = cleanResponse.substring(1, cleanResponse.length() - 1);
                      }

                      // Validate that it's not a JSONPath expression
                      if (cleanResponse.startsWith("$.") || cleanResponse.contains("$[") || cleanResponse.contains("data[")) {
                          log.warn("❌ LLM returned JSONPath expression '{}' instead of actual value for parameter '{}'",
                                   cleanResponse, parameterInfo.getName());
                          return generateSimpleValue(parameterInfo);
                      }

                      log.info("✅ LLM generated meaningful value '{}' for parameter '{}'", cleanResponse, parameterInfo.getName());
                      return cleanResponse;
                  }
              }

              log.warn("LLM value generation returned null/empty for parameter '{}'", parameterInfo.getName());
              return generateSimpleValue(parameterInfo);

          } catch (Exception e) {
              log.warn("LLM value generation failed for parameter '{}': {}", parameterInfo.getName(), e.getMessage());
              return generateSimpleValue(parameterInfo);
          }
      }

      /**
       * Build prompt for LLM value generation - ARRAY AWARE
       */
      private String buildValueGenerationPrompt(ParameterInfo parameterInfo) {
          StringBuilder prompt = new StringBuilder();

          // Check if this is an array parameter
          String schemaType = getOpenAPISchemaType(parameterInfo);
          boolean isArrayParameter = "array".equals(schemaType);

          if (isArrayParameter) {
              prompt.append("Generate a realistic JSON array for the following array parameter:\n\n");
          } else {
              prompt.append("Generate a realistic test value for the following parameter:\n\n");
          }

          prompt.append("Parameter Name: ").append(parameterInfo.getName()).append("\n");
          prompt.append("Parameter Type: ").append(parameterInfo.getType()).append("\n");
          prompt.append("Schema Type: ").append(schemaType != null ? schemaType : "string").append("\n");

          if (parameterInfo.getDescription() != null && !parameterInfo.getDescription().trim().isEmpty()
              && !parameterInfo.getDescription().equals("null")) {
              prompt.append("Description: ").append(parameterInfo.getDescription()).append("\n");
          }

          prompt.append("\nBased on the parameter name and type, generate a realistic test value.\n");

          if (isArrayParameter) {
              prompt.append("Array Examples:\n");
              prompt.append("- For 'distances' (array): [\"10 miles\", \"50 km\", \"100 meters\"]\n");
              prompt.append("- For 'names' (array): [\"Acme Corp\", \"Globex\", \"Initech\"]\n");
              prompt.append("- For 'userIds' (array): [\"user123\", \"john.doe\", \"admin\"]\n");
              prompt.append("- For 'itemIds' (array): [\"item-42\", \"SKU-1234\", \"SKU-2468\"]\n");
              prompt.append("- For 'prices' (array): [\"150.50\", \"89.99\", \"200.00\"]\n");
              prompt.append("- For 'dates' (array): [\"2024-12-25\", \"2024-01-15\", \"2024-03-10\"]\n");

              prompt.append("\nRespond with ONLY a JSON array (e.g., [\"Acme Corp\", \"Globex\"] or [\"10 miles\", \"50 km\"]).\n");
              prompt.append("Generate 2-3 realistic values in the array.\n");
              prompt.append("Do NOT include explanations or extra text.\n");
          } else {
              prompt.append("Single Value Examples:\n");
              prompt.append("- For a destination name (string): 'New York' or 'London' or 'Paris'\n");
              prompt.append("- For an origin name (string): 'Tokyo' or 'Berlin' or 'Madrid'\n");
              prompt.append("- For 'userId' (string): 'user123' or 'john.doe'\n");
              prompt.append("- For 'itemId' (string): 'item-42' or 'SKU-1234'\n");
              prompt.append("- For 'price' (number): '150.50' or '89.99'\n");
              prompt.append("- For 'distance' (number): '350' or '1200'\n");
              prompt.append("- For 'date' (string): '2024-12-25' or '2024-01-15'\n");
              prompt.append("- For 'time' (string): '14:30' or '09:15'\n");

              prompt.append("\nRespond with ONLY the generated value (e.g., 'Acme Corp' or '150.50' or 'item-42').\n");
              prompt.append("Do NOT include quotes, explanations, or JSONPath expressions.\n");
          }

          prompt.append("If you cannot generate a suitable value: NO_GOOD_MATCH");

          return prompt.toString();
      }

      /**
       * Ask LLM to generate a meaningful value
       */
      private String askLLMForValueGeneration(String prompt) {
          try {
              // Use a simple system prompt for value generation
              String systemContent = "You are a test data generator. Generate realistic test values based on parameter information. " +
                                    "Return only the actual value, never JSONPath expressions or explanations.";

              String result = llmService.generateText(systemContent, prompt, 50, 0.7);

              if (result != null && !result.trim().isEmpty()) {
                  log.debug("[Value Generation LLM] Successfully generated value: {}", result);
                  return result;
              } else {
                  log.warn("[Value Generation LLM] LLM service returned null or empty result");
                  return null;
              }

          } catch (Exception e) {
              log.warn("[Value Generation LLM] Failed to call LLM service: {}", e.getMessage());
              return null;
          }
      }

      /**
       * Generate simple value based on parameter type and name when LLM fails - NO HARDCODING, ARRAY AWARE
       */
      private String generateSimpleValue(ParameterInfo parameterInfo) {
          // Use the same no-hardcoding approach as generateMinimalFallbackValue
          try {
              return generateMinimalFallbackValue(parameterInfo);
          } catch (Exception e) {
              log.debug("Failed to generate simple value for '{}': {}", parameterInfo.getName(), e.getMessage());
              // Absolute last resort: use algorithmic generation
              String schemaType = getOpenAPISchemaType(parameterInfo);
              return generateAlgorithmicMinimalValue(parameterInfo, schemaType);
          }
      }

      /**
       * Clean JSON response from markdown code blocks
       * Handles responses like: ```json\n["service1", "service2"]\n```
     */
    private String cleanJsonFromMarkdown(String response) {
        if (response == null) {
            return "";
        }

        // Remove markdown code blocks
        String cleaned = response.trim();

        // Remove ```json at the beginning
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7).trim();
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3).trim();
        }

        // Remove ``` at the end
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3).trim();
        }

        return cleaned;
    }

    /**
     * Pick a real, parameter-relevant GET endpoint for the given service. Returns {@code null}
     * if no real endpoint is found — the caller (LLM discovery) then drops this service
     * candidate altogether. The previous implementation fabricated
     * {@code /api/v1/<svc>/query} when the OAS lookup failed, which produced 56 dead
     * endpoints in the persisted registry (Bug audit Finding #33).
     *
     * <p>Reviewer Comment 7: the {@code pickFirstReasonableEndpoint} heuristic is also
     * gated — when the LLM picker fails after retries, returning a "first non-utility"
     * endpoint regardless of relevance still pollutes the registry. We now return
     * {@code null} in that case too.</p>
     */
    private String inferEndpointForService(String service, ParameterInfo parameterInfo) {
        if (openAPIDiscovery == null || !openAPIDiscovery.isLoaded()) {
            log.debug("OpenAPI discovery unavailable; cannot infer endpoint for service '{}'", service);
            return null;
        }
        List<OpenAPIEndpointDiscovery.EndpointInfo> allEndpoints =
                openAPIDiscovery.getEndpointsForService(service);
        List<OpenAPIEndpointDiscovery.EndpointInfo> getEndpoints = allEndpoints.stream()
                .filter(endpoint -> "GET".equalsIgnoreCase(endpoint.getMethod()))
                .collect(Collectors.toList());
        if (getEndpoints.isEmpty()) {
            log.warn("⚠️ No GET endpoints found for service '{}' (found {} non-GET endpoints); dropping candidate",
                    service, allEndpoints.size());
            return null;
        }
        log.info("🔍 Found {} GET endpoints for service '{}' (filtered from {} total)",
                getEndpoints.size(), service, allEndpoints.size());
        String selectedEndpoint = selectEndpointWithLLMRetry(getEndpoints, parameterInfo, service);
        if (selectedEndpoint != null) {
            log.info("🧠 LLM selected GET endpoint '{}' for parameter '{}' in service '{}'",
                    selectedEndpoint, parameterInfo.getName(), service);
            return selectedEndpoint;
        }
        log.warn("LLM endpoint selection failed for parameter '{}' in service '{}'; dropping candidate (no fabrication)",
                parameterInfo.getName(), service);
        return null;
    }

     /**
      * Use LLM to select the best endpoint for a parameter.
      *
      * <p>Bug audit Finding #38: previously this looped {@code maxRetries=3} regardless of
      * outcome and on every {@code NO_GOOD_MATCH} also fired the forced-selection prompt,
      * leading to a worst case of 6 LLM calls per discovery. We now distinguish two
      * failure modes:
      * <ul>
      *   <li><b>{@code NO_GOOD_MATCH}</b> — the LLM gave a deterministic "no match" answer.
      *       Repeating the same prompt will give the same answer, so we try
      *       {@code forceEndpointSelectionWithLLM} once and then stop.</li>
      *   <li><b>{@code null}</b> (LLM call failure: timeout, parse error, etc.) — we retry
      *       up to {@code maxRetries} times because the next call may succeed.</li>
      * </ul>
      * Worst case is now 1 normal + 1 forced = 2 LLM calls on a deterministic miss, or
      * up to {@code maxRetries} on transient failures.</p>
      */
     private String selectEndpointWithLLMRetry(List<OpenAPIEndpointDiscovery.EndpointInfo> endpoints, ParameterInfo parameterInfo, String serviceName) {
         int maxRetries = 3;
         for (int attempt = 1; attempt <= maxRetries; attempt++) {
             log.debug("🔄 LLM endpoint selection attempt {} of {} for parameter '{}'",
                      attempt, maxRetries, parameterInfo.getName());
             String result = selectEndpointWithLLM(endpoints, parameterInfo, serviceName);
             if (result != null && !result.equals("NO_GOOD_MATCH")) {
                 log.info("✅ LLM endpoint selection succeeded on attempt {} for parameter '{}'",
                         attempt, parameterInfo.getName());
                 return result;
             }
             if (result != null && result.equals("NO_GOOD_MATCH")) {
                 // Deterministic miss — try forced once, then stop. Retrying the same prompt
                 // would just produce the same NO_GOOD_MATCH.
                 log.info("🤔 LLM said NO_GOOD_MATCH for parameter '{}' - trying forced selection (no retry)",
                         parameterInfo.getName());
                 return forceEndpointSelectionWithLLM(endpoints, parameterInfo, serviceName);
             }
             // result == null → transient LLM call failure → retry.
             log.warn("⚠️ LLM endpoint selection attempt {} failed (transient) for parameter '{}', retrying...",
                     attempt, parameterInfo.getName());
         }
         log.error("❌ All {} LLM endpoint selection attempts failed for parameter '{}'",
                  maxRetries, parameterInfo.getName());
         return null;
     }

     /**
      * Use LLM to select the best endpoint for a parameter (single attempt)
      */
     private String selectEndpointWithLLM(List<OpenAPIEndpointDiscovery.EndpointInfo> endpoints, ParameterInfo parameterInfo, String serviceName) {
         try {
             // Build endpoint selection prompt
             String prompt = buildEndpointSelectionPrompt(endpoints, parameterInfo, serviceName);

             if (prompt.length() > config.getMaxPromptChars()) {
                 log.warn("Endpoint selection prompt too long ({} chars), falling back to scoring", prompt.length());
                 return null;
             }

             // Ask LLM to select best endpoint
             String llmResponse = askLLMForEndpointSelection(prompt);

             if (llmResponse != null && !llmResponse.trim().isEmpty()) {
                 String cleanResponse = llmResponse.trim();
                 log.info("🧠 LLM raw response for parameter '{}': '{}'", parameterInfo.getName(), cleanResponse);

                 // Check for NO_GOOD_MATCH
                 if (cleanResponse.equals("NO_GOOD_MATCH")) {
                     log.info("LLM indicated no good endpoint match for parameter '{}'", parameterInfo.getName());
                     return "NO_GOOD_MATCH";
                 }

                 // Clean up the response (remove quotes, extra whitespace)
                 if (cleanResponse.startsWith("\"") && cleanResponse.endsWith("\"")) {
                     cleanResponse = cleanResponse.substring(1, cleanResponse.length() - 1);
                 }

                 // Validate that the selected endpoint exists (flexible matching)
                 String selectedEndpoint = validateAndNormalizeEndpoint(cleanResponse, endpoints, parameterInfo.getName());
                 if (selectedEndpoint != null) {
                     log.info("✅ LLM endpoint validation successful: '{}' for parameter '{}'", selectedEndpoint, parameterInfo.getName());
                     return selectedEndpoint;
                 }

                 log.warn("❌ LLM selected invalid endpoint '{}' for parameter '{}', available endpoints: {}",
                         cleanResponse, parameterInfo.getName(),
                         endpoints.stream().map(e -> e.getPath()).collect(Collectors.toList()));
             }

         } catch (Exception e) {
             log.debug("LLM endpoint selection failed: {}", e.getMessage());
         }

         return null; // Fallback to scoring
     }

     /**
      * Validate and normalize the LLM's endpoint selection with flexible matching
      */
     private String validateAndNormalizeEndpoint(String llmResponse, List<OpenAPIEndpointDiscovery.EndpointInfo> endpoints, String parameterName) {
         if (llmResponse == null || llmResponse.trim().isEmpty()) {
             return null;
         }

         String cleanResponse = llmResponse.trim();
         log.debug("🔍 Validating LLM endpoint selection '{}' against {} available endpoints", cleanResponse, endpoints.size());

         // 1. Exact match (most common case) - but only for GET endpoints
         for (OpenAPIEndpointDiscovery.EndpointInfo endpoint : endpoints) {
             if (endpoint.getPath().equals(cleanResponse)) {
                 if ("GET".equalsIgnoreCase(endpoint.getMethod())) {
                     log.debug("✅ Exact GET match found: '{}'", cleanResponse);
                     return cleanResponse;
                 } else {
                     log.warn("❌ LLM suggested '{}' but it's {} method, not GET. Rejecting.", cleanResponse, endpoint.getMethod());
                     return null; // Reject non-GET endpoints immediately
                 }
             }
         }

         // 2. Case-insensitive match
         for (OpenAPIEndpointDiscovery.EndpointInfo endpoint : endpoints) {
             if (endpoint.getPath().equalsIgnoreCase(cleanResponse)) {
                 log.debug("✅ Case-insensitive match found: '{}' -> '{}'", cleanResponse, endpoint.getPath());
                 return endpoint.getPath();
             }
         }

         // 3. Partial match - LLM might have returned just the endpoint name without full path
         for (OpenAPIEndpointDiscovery.EndpointInfo endpoint : endpoints) {
             String fullPath = endpoint.getPath();
             // Check if the LLM response is contained in the full path
             if (fullPath.contains(cleanResponse)) {
                 log.debug("✅ Partial match found: '{}' contained in '{}'", cleanResponse, fullPath);
                 return fullPath;
             }
             // Check if the full path ends with the LLM response
             if (fullPath.endsWith(cleanResponse)) {
                 log.debug("✅ Suffix match found: '{}' is suffix of '{}'", cleanResponse, fullPath);
                 return fullPath;
             }
         }

         // 4. Reverse partial match - full path might be contained in LLM response
         for (OpenAPIEndpointDiscovery.EndpointInfo endpoint : endpoints) {
             String fullPath = endpoint.getPath();
             if (cleanResponse.contains(fullPath)) {
                 log.debug("✅ Reverse partial match found: '{}' contains '{}'", cleanResponse, fullPath);
                 return fullPath;
             }
         }

         // 5. Fuzzy matching - remove common prefixes/suffixes and try again
         String normalizedResponse = normalizeEndpointPath(cleanResponse);
         for (OpenAPIEndpointDiscovery.EndpointInfo endpoint : endpoints) {
             String normalizedEndpoint = normalizeEndpointPath(endpoint.getPath());
             if (normalizedEndpoint.equals(normalizedResponse)) {
                 log.debug("✅ Fuzzy match found: '{}' -> '{}' (normalized: '{}' = '{}')",
                          cleanResponse, endpoint.getPath(), normalizedResponse, normalizedEndpoint);
                 return endpoint.getPath();
             }
         }

         log.debug("❌ No match found for '{}' in available endpoints: {}",
                  cleanResponse, endpoints.stream().map(e -> e.getPath()).collect(Collectors.toList()));
         return null;
     }

     /**
      * Normalize endpoint path for fuzzy matching
      */
     private String normalizeEndpointPath(String path) {
         if (path == null) return "";

         String normalized = path.toLowerCase().trim();

         // Remove common prefixes
         if (normalized.startsWith("/api/v1/")) {
             normalized = normalized.substring(8);
         } else if (normalized.startsWith("/api/")) {
             normalized = normalized.substring(5);
         } else if (normalized.startsWith("/")) {
             normalized = normalized.substring(1);
         }

         // Remove common suffixes
         if (normalized.endsWith("/")) {
             normalized = normalized.substring(0, normalized.length() - 1);
         }

         return normalized;
     }

     /**
      * Force LLM to select an endpoint when it initially says NO_GOOD_MATCH
      */
     private String forceEndpointSelectionWithLLM(List<OpenAPIEndpointDiscovery.EndpointInfo> endpoints, ParameterInfo parameterInfo, String serviceName) {
         try {
             // Build more aggressive prompt that forces selection
             String prompt = buildForcedEndpointSelectionPrompt(endpoints, parameterInfo, serviceName);

             if (prompt.length() > config.getMaxPromptChars()) {
                 log.warn("Forced endpoint selection prompt too long ({} chars), skipping", prompt.length());
                 return null;
             }

             // Ask LLM with forced selection prompt
             String llmResponse = askLLMForEndpointSelection(prompt);

             if (llmResponse != null && !llmResponse.trim().isEmpty()) {
                 String cleanResponse = llmResponse.trim();

                 // Don't accept NO_GOOD_MATCH this time
                 if (cleanResponse.equals("NO_GOOD_MATCH")) {
                     log.warn("LLM still said NO_GOOD_MATCH even with forced prompt for parameter '{}'", parameterInfo.getName());
                     return null;
                 }

                 // Clean up the response (remove quotes, extra whitespace)
                 if (cleanResponse.startsWith("\"") && cleanResponse.endsWith("\"")) {
                     cleanResponse = cleanResponse.substring(1, cleanResponse.length() - 1);
                 }

                 // Validate that the selected endpoint exists (flexible matching)
                 String selectedEndpoint = validateAndNormalizeEndpoint(cleanResponse, endpoints, parameterInfo.getName());
                 if (selectedEndpoint != null) {
                     log.info("🎯 LLM forced selection successful: '{}' for parameter '{}'", selectedEndpoint, parameterInfo.getName());
                     return selectedEndpoint;
                 }

                 log.warn("❌ LLM forced selection '{}' is invalid for parameter '{}', available endpoints: {}",
                         cleanResponse, parameterInfo.getName(),
                         endpoints.stream().map(e -> e.getPath()).collect(Collectors.toList()));
             }

         } catch (Exception e) {
             log.debug("Forced LLM endpoint selection failed: {}", e.getMessage());
         }

         return null;
     }

     /**
      * Build prompt for LLM endpoint selection
      */
     private String buildEndpointSelectionPrompt(List<OpenAPIEndpointDiscovery.EndpointInfo> endpoints, ParameterInfo parameterInfo, String serviceName) {
         StringBuilder prompt = new StringBuilder();
         prompt.append("Service: ").append(serviceName).append("\n");
         prompt.append("Parameter: ").append(parameterInfo.getName()).append(" (type: ").append(parameterInfo.getType()).append(")\n");
         prompt.append("Description: ").append(parameterInfo.getDescription() != null ? parameterInfo.getDescription() : "").append("\n\n");

         prompt.append("Available GET endpoints (ONLY GET methods for data fetching - DO NOT suggest POST/PUT/DELETE):\n");
         int shownCount = 0;
         for (int i = 0; i < endpoints.size() && shownCount < 10; i++) {
             OpenAPIEndpointDiscovery.EndpointInfo endpoint = endpoints.get(i);
             // Only show GET endpoints (should already be filtered, but double-check)
             if ("GET".equalsIgnoreCase(endpoint.getMethod())) {
                 shownCount++;
                 prompt.append("- GET ").append(endpoint.getPath()).append("\n");
             }
         }

         prompt.append("\nTask: Select the BEST GET endpoint to fetch data for this parameter.\n");
         prompt.append("IMPORTANT: You MUST choose from the GET endpoints listed above ONLY.\n");
         prompt.append("DO NOT suggest endpoints that are not in the list above.\n");
         prompt.append("DO NOT suggest POST, PUT, DELETE, or PATCH endpoints.\n\n");
         prompt.append("Guidelines:\n");
         prompt.append("- For 'list' parameters: prefer GET endpoints returning collections (no path params)\n");
         prompt.append("- For 'id' parameters: prefer GET endpoints that return lists (can extract IDs)\n");
         prompt.append("- For 'name' parameters: prefer GET endpoints returning entity details\n");
         prompt.append("- Avoid utility endpoints (welcome, health, status)\n\n");
         prompt.append("Respond with ONLY the endpoint path from the list above (e.g., /api/v1/service/resource)\n");
         prompt.append("If NO GET endpoint from the list above is suitable, respond with: NO_GOOD_MATCH");

         return prompt.toString();
     }






     /**
      * Build forced prompt that doesn't allow NO_GOOD_MATCH
      */
     private String buildForcedEndpointSelectionPrompt(List<OpenAPIEndpointDiscovery.EndpointInfo> endpoints, ParameterInfo parameterInfo, String serviceName) {
         StringBuilder prompt = new StringBuilder();
         prompt.append("Service: ").append(serviceName).append("\n");
         prompt.append("Parameter: ").append(parameterInfo.getName()).append(" (type: ").append(parameterInfo.getType()).append(")\n");
         prompt.append("Description: ").append(parameterInfo.getDescription() != null ? parameterInfo.getDescription() : "").append("\n\n");

         prompt.append("Available GET endpoints (for data fetching):\n");
         for (int i = 0; i < Math.min(10, endpoints.size()); i++) {
             OpenAPIEndpointDiscovery.EndpointInfo endpoint = endpoints.get(i);
             // Only show GET endpoints (should already be filtered, but double-check)
             if ("GET".equalsIgnoreCase(endpoint.getMethod())) {
                 prompt.append("- GET ").append(endpoint.getPath()).append("\n");
             }
         }

         prompt.append("\nTask: You MUST select one of the GET endpoints above for this parameter.\n");
         prompt.append("Even if none seem perfect, choose the most reasonable one.\n");
         prompt.append("We need to fetch some data for test generation.\n\n");
         prompt.append("Guidelines:\n");
         prompt.append("- For 'list' parameters: prefer endpoints returning collections (no path params)\n");
         prompt.append("- For 'id' parameters: prefer endpoints that return lists (can extract IDs)\n");
         prompt.append("- For 'name' parameters: prefer endpoints returning entity details\n");
         prompt.append("- If unsure, pick the first non-utility endpoint\n\n");
         prompt.append("Respond with ONLY the endpoint path (e.g., /api/v1/service/resource)\n");
         prompt.append("DO NOT respond with NO_GOOD_MATCH - you must pick one endpoint.");

         return prompt.toString();
     }
     private String buildEndpointDiscoveryPrompt(String serviceName, ParameterInfo parameterInfo) {
         return buildEndpointDiscoveryPrompt(serviceName, parameterInfo, new ArrayList<>());
     }

     private String buildEndpointDiscoveryPrompt(String serviceName, ParameterInfo parameterInfo, List<String> availableEndpoints) {
         String template = registry.getLlmPrompts().get("endpointDiscovery");

         // Truncate description if too long to prevent message length issues
         String description = parameterInfo.getDescription() != null ? parameterInfo.getDescription() : "";
         if (description.length() > 300) {
             description = description.substring(0, 297) + "...";
         }

         // Build available endpoints context
         String endpointsContext = "";
         if (!availableEndpoints.isEmpty()) {
             endpointsContext = "\n\nAvailable endpoints in " + serviceName + ":\n" +
                               String.join("\n", availableEndpoints.subList(0, Math.min(5, availableEndpoints.size())));
             if (availableEndpoints.size() > 5) {
                 endpointsContext += "\n... and " + (availableEndpoints.size() - 5) + " more";
             }
         }

         String prompt = template
                 .replace("{serviceName}", serviceName != null ? serviceName : "")
                 .replace("{parameterName}", parameterInfo.getName() != null ? parameterInfo.getName() : "")
                 .replace("{parameterType}", parameterInfo.getType() != null ? parameterInfo.getType() : "")
                 .replace("{parameterDescription}", description) + endpointsContext;

         // Bug audit Finding #10 finish: respect the configured prompt cap.
         int promptCap = config.getMaxPromptChars();
         if (prompt.length() > promptCap) {
             log.warn("Endpoint discovery prompt too long ({} > {}), truncating", prompt.length(), promptCap);
             prompt = prompt.substring(0, Math.max(0, promptCap - 3)) + "...";
         }

         return prompt;
     }

     private String askLLMForEndpoint(String prompt) {
         try {
             // Call LLM directly for endpoint discovery with appropriate system prompt
             String rawResponse = callLLMForEndpointDiscovery(prompt);

             if (rawResponse != null && !rawResponse.trim().isEmpty()) {
                 String cleaned = cleanJsonFromMarkdown(rawResponse);

                 // Check for NO_GOOD_MATCH response
                 if (cleaned.trim().equals("NO_GOOD_MATCH")) {
                     return "NO_GOOD_MATCH";
                 }

                 return cleaned;
             }

             return null;

         } catch (Exception e) {
             log.warn("Failed to ask LLM for endpoint: {}", e.getMessage());
             return null;
         }
     }

     private String parseEndpointFromLLMResponse(String llmResponse) {
         // Check for NO_GOOD_MATCH response first
         if (llmResponse != null && llmResponse.trim().equals("NO_GOOD_MATCH")) {
             log.info("LLM indicated no good endpoint match");
             return null;
         }

         try {
             JsonNode jsonResponse = objectMapper.readTree(llmResponse);
             if (jsonResponse.has("endpoint")) {
                 String endpoint = jsonResponse.get("endpoint").asText();
                 log.debug("LLM discovered endpoint: {}", endpoint);
                 return endpoint;
             } else {
                 log.debug("LLM response missing 'endpoint' field: {}", llmResponse);
             }
         } catch (Exception e) {
             log.debug("Failed to parse endpoint from LLM response: {} (raw: '{}')", e.getMessage(), llmResponse);
         }
         return null;
     }

     /**
      * Ask LLM to select the best endpoint from available options
      */
     private String askLLMForEndpointSelection(String prompt) {
         try {
             // Call LLM with endpoint selection prompt
             String rawResponse = callLLMForEndpointDiscovery(prompt);

             if (rawResponse != null && !rawResponse.trim().isEmpty()) {
                 String cleaned = cleanJsonFromMarkdown(rawResponse);

                 // Check for NO_GOOD_MATCH response first
                 if (cleaned.trim().equals("NO_GOOD_MATCH")) {
                     return "NO_GOOD_MATCH";
                 }

                 // Extract endpoint path from response
                 String endpointPath = cleaned.trim();

                 // Remove quotes if present
                 if (endpointPath.startsWith("\"") && endpointPath.endsWith("\"")) {
                     endpointPath = endpointPath.substring(1, endpointPath.length() - 1);
                 }

                 return endpointPath;
             }

             return null;
         } catch (Exception e) {
             log.warn("Failed to call LLM for endpoint selection: {}", e.getMessage());
             return null;
         }
     }
 }