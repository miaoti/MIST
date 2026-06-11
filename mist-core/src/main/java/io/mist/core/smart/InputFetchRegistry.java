package io.mist.core.smart;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Registry for managing input fetch mappings and service patterns
 * Handles loading, saving, and querying of parameter-to-API mappings
 */
public class InputFetchRegistry {
    
    private static final Logger log = LogManager.getLogger(InputFetchRegistry.class);
    
    private String version;
    private LocalDateTime lastUpdated;
    private Map<String, List<ApiMapping>> parameterMappings;
    private List<ServicePattern> servicePatterns;
    private Map<String, String> llmPrompts;
    private CacheConfig cacheConfig;
    private Map<String, Map<String, List<ParameterError>>> parameterErrors; // API endpoint -> parameter -> errors
    // Phase 1: per-value SUT-verified status for non-target parameter values.
    // Nesting mirrors parameterErrors: endpoint -> paramName -> value -> status.
    // Populated by the writer-side recordParameterSuccess drain after a 2xx
    // positive step; consumed by MistGenerator's Sniper non-target picker.
    private Map<String, Map<String, Map<String, PoolEntryStatus>>> poolEntryStatus;
    
    // Jackson mapper for YAML serialization
    private static final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory())
            .registerModule(new JavaTimeModule());
    
    public InputFetchRegistry() {
        this.version = "1.0";
        this.lastUpdated = LocalDateTime.now();
        this.parameterMappings = new HashMap<>();
        this.servicePatterns = new ArrayList<>();
        this.llmPrompts = new HashMap<>();
        this.cacheConfig = new CacheConfig();
        this.parameterErrors = new HashMap<>();
        this.poolEntryStatus = new HashMap<>();

        initializeDefaults();
    }

    /**
     * Phase 1: classification of a pool value's known validity per the SUT.
     * UNVERIFIED is the default for values that have never been observed in
     * a positive 2xx response. VERIFIED_VALID is set after the value flows
     * through a successful positive test step. REJECTED_BY_SUT is set when
     * the same value triggered a 4xx/5xx in a positive (non-faulty) step.
     */
    public enum PoolEntryStatus {
        UNVERIFIED,
        VERIFIED_VALID,
        REJECTED_BY_SUT
    }
    
    /**
     * Load registry from YAML file
     */
    public static InputFetchRegistry loadFromFile(File file) throws IOException {
        try {
            RegistryData data = yamlMapper.readValue(file, RegistryData.class);
            return data.toRegistry();
        } catch (IOException e) {
            log.error("Failed to load registry from {}: {}", file.getPath(), e.getMessage());
            throw e;
        }
    }
    
    /**
     * Save registry to YAML file using an atomic write (temp file + rename) so that a JVM
     * crash mid-write cannot leave the canonical registry in a half-written state. The prior
     * implementation called {@code yamlMapper.writeValue(file, ...)} directly — for a 1.6 MB
     * file an interruption between byte 0 and EOF could corrupt the YAML and brick the next
     * load (Reviewer Comment 6).
     */
    public synchronized void saveToFile(File file) throws IOException {
        this.lastUpdated = LocalDateTime.now();
        RegistryData data = RegistryData.fromRegistry(this);
        File parent = file.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        File tmp = File.createTempFile(file.getName() + ".", ".tmp", parent);
        try {
            yamlMapper.writeValue(tmp, data);
            try {
                java.nio.file.Files.move(tmp.toPath(), file.toPath(),
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException atomicNotSupported) {
                // Some filesystems (e.g. cross-device tmpdirs) cannot do ATOMIC_MOVE; fall
                // back to non-atomic replace, still safer than direct overwrite of the canonical
                // file because any failure of the writeValue happens against the temp file.
                java.nio.file.Files.move(tmp.toPath(), file.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            log.debug("Saved registry to {} via atomic temp-rename", file.getPath());
        } catch (IOException e) {
            log.error("Failed to save registry to {}: {}", file.getPath(), e.getMessage());
            try { java.nio.file.Files.deleteIfExists(tmp.toPath()); } catch (IOException ignored) { }
            throw e;
        }
    }
    
    /**
     * Get mappings for a specific parameter (global tier only — back-compat).
     */
    public List<ApiMapping> getMappingsForParameter(String parameterName) {
        return getMappingsForParameter(null, parameterName);
    }

    /**
     * Get mappings for a parameter scoped to a specific consumer API. Returns scoped
     * mappings (whose {@code consumerApiKey} equals {@code consumerApiKey}) first, then
     * global mappings (whose {@code consumerApiKey} is null/empty) for fallback.
     *
     * <p>Bug audit Finding #15 + Reviewer Comment 2: this lets two operations that both
     * have a parameter named {@code id} use distinct producer mappings without zeroing
     * out existing learning (legacy entries with no recorded scope are treated as global).</p>
     *
     * @param consumerApiKey the consumer's normalized API key (e.g.
     *                       {@code "POST /api/v1/orderservice/orders"}), or {@code null} to
     *                       return all mappings (global tier only).
     * @param parameterName  the bare parameter name.
     */
    public List<ApiMapping> getMappingsForParameter(String consumerApiKey, String parameterName) {
        List<ApiMapping> all = parameterMappings.getOrDefault(parameterName, new ArrayList<>());
        if (consumerApiKey == null || consumerApiKey.isEmpty()) {
            // Pre-Finding-#15 callers (anyone passing the bare paramName form) get the
            // global tier only — same as before.
            List<ApiMapping> globals = new ArrayList<>();
            for (ApiMapping m : all) {
                if (m.getConsumerApiKey() == null || m.getConsumerApiKey().isEmpty()) {
                    globals.add(m);
                }
            }
            return globals.isEmpty() ? all : globals;
        }
        List<ApiMapping> scoped = new ArrayList<>();
        List<ApiMapping> globals = new ArrayList<>();
        for (ApiMapping m : all) {
            String mk = m.getConsumerApiKey();
            if (consumerApiKey.equals(mk)) scoped.add(m);
            else if (mk == null || mk.isEmpty()) globals.add(m);
        }
        List<ApiMapping> result = new ArrayList<>(scoped);
        result.addAll(globals);
        return result;
    }

    /**
     * Add a new mapping for a parameter (global / unscoped — back-compat).
     */
    public void addMapping(String parameterName, ApiMapping mapping) {
        addMapping(null, parameterName, mapping);
    }

    /**
     * Add a new mapping for a parameter, scoped to a specific consumer API. The mapping's
     * {@link ApiMapping#setConsumerApiKey} is set so future reads can scope-filter
     * (Bug audit Finding #15).
     *
     * @param consumerApiKey may be {@code null} for a global mapping; otherwise normalized
     *                       method+path key like {@code "POST /api/v1/orderservice/orders"}.
     */
    public void addMapping(String consumerApiKey, String parameterName, ApiMapping mapping) {
        if (mapping == null) return;
        // Bug audit Finding #23 + Reviewer note: smart-fetch only knows how to GET data
        // upstream. Reject mappings that record a non-GET method so we never persist a
        // POST/PUT/DELETE that would produce side effects when "fetched".
        String method = mapping.getMethod();
        if (method != null && !"GET".equalsIgnoreCase(method)) {
            log.warn("Refusing to register non-GET mapping for parameter '{}' ({} {}); smart-fetch only supports GET.",
                    parameterName, method, mapping.getEndpoint());
            return;
        }
        if (consumerApiKey != null && !consumerApiKey.isEmpty()) {
            mapping.setConsumerApiKey(consumerApiKey);
        }
        // Fresh-review Finding F20: dedup by (endpoint, method, service, extractPath, scope).
        // ApiMapping.equals already covers the first four; we additionally require scope
        // equality so a global entry and a scoped entry with otherwise-identical fields
        // coexist.
        List<ApiMapping> bucket = parameterMappings.computeIfAbsent(parameterName, k -> new ArrayList<>());
        for (ApiMapping existing : bucket) {
            if (existing.equals(mapping)
                    && java.util.Objects.equals(existing.getConsumerApiKey(), mapping.getConsumerApiKey())) {
                log.debug("Skipping duplicate mapping for parameter '{}' (scope='{}'): {}",
                        parameterName, consumerApiKey != null ? consumerApiKey : "<global>", mapping);
                return;
            }
        }
        bucket.add(mapping);
        log.debug("Added mapping for parameter '{}' (scope='{}'): {}",
                parameterName, consumerApiKey != null ? consumerApiKey : "<global>", mapping);
    }
    
    /**
     * Remove a mapping for a parameter
     */
    public boolean removeMapping(String parameterName, ApiMapping mapping) {
        List<ApiMapping> mappings = parameterMappings.get(parameterName);
        if (mappings != null) {
            boolean removed = mappings.remove(mapping);
            if (mappings.isEmpty()) {
                parameterMappings.remove(parameterName);
            }
            return removed;
        }
        return false;
    }
    
    /**
     * Reviewer Comment 22: cap the per-endpoint+param error history to bound registry
     * memory and YAML size. A single OVERFLOW probe with a 49 KB Lorem-ipsum payload was
     * inflating the live registry by ~25 % (dataflow-map § 6.5). FIFO-evicting the oldest
     * entry beyond the cap keeps recent failure context for LLM-aware regeneration without
     * unbounded growth.
     */
    private static final int MAX_ERRORS_PER_PARAM = 50;
    private static final int MAX_ERROR_REASON_CHARS = 1024;

    /**
     * In-place truncate {@code error.errorReason} to {@link #MAX_ERROR_REASON_CHARS}.
     * Shared by {@link #addParameterError} (which then runs the similarity check) and
     * {@link #isAlreadyRegistered} (which gates the YAML save on that same check) —
     * they must see the same normalized form or the dedup decision diverges.
     */
    private static void truncateReasonIfNeeded(ParameterError error) {
        if (error != null && error.getErrorReason() != null
                && error.getErrorReason().length() > MAX_ERROR_REASON_CHARS) {
            error.setErrorReason(error.getErrorReason().substring(0, MAX_ERROR_REASON_CHARS) + "...");
        }
    }

    /**
     * Add a parameter error for specific API endpoint and parameter.
     * Decodes percent-encoded URL segments before keying so {@code /foo/%25} and
     * {@code /foo/%} collapse into one bucket (Bug audit Finding #40). Caps total entries
     * per (endpoint, param) at {@value #MAX_ERRORS_PER_PARAM} and individual reasons at
     * {@value #MAX_ERROR_REASON_CHARS} chars (Reviewer Comment 22).
     */
    public void addParameterError(String apiEndpoint, String parameterName, ParameterError error) {
        String key = decodeUrlForKey(apiEndpoint);
        truncateReasonIfNeeded(error);
        Map<String, List<ParameterError>> endpointErrors = parameterErrors.computeIfAbsent(key, k -> new HashMap<>());
        List<ParameterError> paramErrors = endpointErrors.computeIfAbsent(parameterName, k -> new ArrayList<>());

        // Check for semantic similarity to avoid redundant information
        ParameterError existingSimilar = findSemanticallyEquivalentError(paramErrors, error);

        if (existingSimilar == null) {
            // No similar error found, add the new one
            paramErrors.add(error);
            // FIFO eviction once the cap is exceeded.
            while (paramErrors.size() > MAX_ERRORS_PER_PARAM) {
                paramErrors.remove(0);
            }
            log.debug("Added new parameter error for {}.{}: {} - {}",
                     key, parameterName, error.getErrorType(),
                     truncateForLog(error.getErrorReason(), 50));
        } else {
            // Similar error exists, merge information if the new one is more detailed
            if (shouldUpdateExistingError(existingSimilar, error)) {
                mergeErrorInformation(existingSimilar, error);
                log.debug("Updated existing parameter error for {}.{}: {} with more details", 
                         apiEndpoint, parameterName, error.getErrorType());
            } else {
                log.debug("Skipped semantically similar parameter error for {}.{}: {}", 
                         apiEndpoint, parameterName, error.getErrorType());
            }
        }
    }
    
    /**
     * Get parameter errors for specific API endpoint and parameter.
     *
     * <p>Fresh-review Finding F10: applies the same {@code decodeUrlForKey} normalization
     * as {@link #addParameterError}. Without this symmetry, callers asking for
     * {@code /foo/%25} fail to find errors stored under decoded {@code /foo/%}, undermining
     * Bug audit Finding #40.</p>
     */
    public List<ParameterError> getParameterErrors(String apiEndpoint, String parameterName) {
        String key = decodeUrlForKey(apiEndpoint);
        return parameterErrors.getOrDefault(key, new HashMap<>())
                .getOrDefault(parameterName, new ArrayList<>());
    }

    /**
     * Returns {@code true} when calling {@link #addParameterError} with {@code candidate}
     * would not mutate the registry. Callers gate the YAML flush on this so we don't
     * write to disk when nothing on-disk would change.
     *
     * <p>Mirrors the same predicate {@link #addParameterError} uses internally:
     * {@link #findSemanticallyEquivalentError} + {@link #shouldUpdateExistingError}. A
     * bit-exact-only check would miss the semantic-similar-but-no-update case and still
     * trigger a redundant save.</p>
     */
    public boolean isAlreadyRegistered(String apiEndpoint, String parameterName, ParameterError candidate) {
        if (candidate == null) return false;
        // Symmetry: addParameterError truncates the reason in-place before the similarity
        // check, so we must compare against the same truncated form. Without this, a long
        // reason can look "not similar" here but be a no-op inside addParameterError, which
        // would still flip registryChanged and force a wasted YAML write.
        truncateReasonIfNeeded(candidate);
        List<ParameterError> existing = getParameterErrors(apiEndpoint, parameterName);
        ParameterError similar = findSemanticallyEquivalentError(existing, candidate);
        // No similar error → addParameterError would add a new entry → caller must save.
        if (similar == null) return false;
        // Similar exists → addParameterError would mutate iff shouldUpdateExistingError;
        // if it would NOT update, the registry is effectively unchanged.
        return !shouldUpdateExistingError(similar, candidate);
    }

    /**
     * Get all parameter errors for an API endpoint (decoded-key, see {@link #getParameterErrors}).
     */
    public Map<String, List<ParameterError>> getParameterErrorsForEndpoint(String apiEndpoint) {
        String key = decodeUrlForKey(apiEndpoint);
        return parameterErrors.getOrDefault(key, new HashMap<>());
    }
    
    /**
     * Get error context for LLM parameter generation
     */
    public String getErrorContextForParameter(String apiEndpoint, String parameterName) {
        List<ParameterError> errors = getParameterErrors(apiEndpoint, parameterName);
        if (errors.isEmpty()) {
            return "";
        }
        
        StringBuilder context = new StringBuilder();
        context.append("⚠️ KNOWN ERROR PATTERNS for parameter '").append(parameterName).append("':\n");
        
        Set<String> uniqueErrorTypes = new HashSet<>();
        for (ParameterError error : errors) {
            String errorInfo = String.format("%s: %s", error.getErrorType(), error.getErrorReason());
            if (uniqueErrorTypes.add(errorInfo)) {
                context.append("- ").append(errorInfo).append("\n");
            }
        }
        
        context.append("Please generate values that avoid these known error patterns.\n");
        return context.toString();
    }
    
    // ---------------------------------------------------------------------
    // Phase 1: SUT-verified pool status (non-target parameter values)
    // ---------------------------------------------------------------------

    /**
     * Mark {@code value} as VERIFIED_VALID for the given endpoint+param. Called
     * by the test-result drain after a positive (non-faulty) test step
     * returned 2xx with this value in scope. Idempotent; promotes
     * REJECTED_BY_SUT → VERIFIED_VALID when the SUT eventually accepts a
     * previously-rejected value (e.g. data fixture became available).
     */
    public synchronized void markVerified(String endpoint, String paramName, String value) {
        if (endpoint == null || paramName == null || value == null) return;
        poolEntryStatus
                .computeIfAbsent(endpoint, k -> new HashMap<>())
                .computeIfAbsent(paramName, k -> new HashMap<>())
                .put(value, PoolEntryStatus.VERIFIED_VALID);
    }

    /**
     * Mark {@code value} as REJECTED_BY_SUT for the given endpoint+param.
     * Called when a positive (non-faulty) step returns 4xx/5xx with this
     * value in scope. Never overwrites a VERIFIED_VALID entry (a single
     * verified observation outweighs intermittent rejections).
     */
    public synchronized void markRejected(String endpoint, String paramName, String value) {
        if (endpoint == null || paramName == null || value == null) return;
        Map<String, PoolEntryStatus> perValue = poolEntryStatus
                .computeIfAbsent(endpoint, k -> new HashMap<>())
                .computeIfAbsent(paramName, k -> new HashMap<>());
        if (perValue.get(value) != PoolEntryStatus.VERIFIED_VALID) {
            perValue.put(value, PoolEntryStatus.REJECTED_BY_SUT);
        }
    }

    /**
     * Return the list of values currently classified VERIFIED_VALID for the
     * given endpoint+param. Returns an empty list if none are verified —
     * caller falls back to the raw shared pool with a warning.
     */
    public synchronized List<String> getVerifiedValues(String endpoint, String paramName) {
        Map<String, Map<String, PoolEntryStatus>> byParam = poolEntryStatus.get(endpoint);
        if (byParam == null) return Collections.emptyList();
        Map<String, PoolEntryStatus> byValue = byParam.get(paramName);
        if (byValue == null) return Collections.emptyList();
        List<String> verified = new ArrayList<>();
        for (Map.Entry<String, PoolEntryStatus> e : byValue.entrySet()) {
            if (e.getValue() == PoolEntryStatus.VERIFIED_VALID) verified.add(e.getKey());
        }
        return verified;
    }

    /**
     * Status query — returns {@link PoolEntryStatus#UNVERIFIED} when the
     * value has never been observed. Test-only and diagnostic use.
     */
    public synchronized PoolEntryStatus getPoolEntryStatus(String endpoint, String paramName, String value) {
        Map<String, Map<String, PoolEntryStatus>> byParam = poolEntryStatus.get(endpoint);
        if (byParam == null) return PoolEntryStatus.UNVERIFIED;
        Map<String, PoolEntryStatus> byValue = byParam.get(paramName);
        if (byValue == null) return PoolEntryStatus.UNVERIFIED;
        PoolEntryStatus s = byValue.get(value);
        return s == null ? PoolEntryStatus.UNVERIFIED : s;
    }

    /**
     * Test-only read-only view of the nested status map for assertions.
     */
    Map<String, Map<String, Map<String, PoolEntryStatus>>> getPoolEntryStatusMapForTest() {
        return Collections.unmodifiableMap(poolEntryStatus);
    }

    /**
     * Get all service names from mappings and patterns
     */
    public List<String> getAllServices() {
        Set<String> services = new HashSet<>();
        
        // From parameter mappings
        parameterMappings.values().stream()
                .flatMap(List::stream)
                .map(ApiMapping::getService)
                .filter(Objects::nonNull)
                .forEach(services::add);
        
        // From service patterns
        servicePatterns.stream()
                .flatMap(pattern -> pattern.getServices().stream())
                .forEach(services::add);
        
        return new ArrayList<>(services);
    }
    
    /**
     * Initialize default patterns and prompts
     */
    private void initializeDefaults() {
        // NOTE: no hardcoded service patterns are seeded here. These used to inject
        // train-ticket service names (ts-station-service, ts-order-service, ...) and
        // train-ticket endpoint paths UNCONDITIONALLY into every InputFetchRegistry.
        // On a SUT whose own input-fetch-registry.yaml is absent (e.g. Bookinfo), that
        // leaked ts-* names into the smart-fetch LLM discovery candidate list, so the
        // LLM "discovered" ts-travel-service / ts-order-service for unrelated params
        // (a generation-generalization bug). Service patterns are SUT-specific and now
        // live ONLY in each SUT's input-fetch-registry.yaml — train-ticket's file
        // carries them under `servicePatterns:` and toRegistry() loads them (replacing
        // this empty default). A SUT with no registry file gets an EMPTY pattern set,
        // which is correct: smart-fetch finds no candidates and falls back cleanly,
        // instead of hallucinating train-ticket services.

        // Default LLM prompts (SUT-agnostic prompt templates — these stay)
        llmPrompts.put("apiDiscovery", 
                "Parameter: {parameterName} (type: {parameterType}, location: {parameterLocation})\n" +
                "Description: {parameterDescription}\n\n" +
                "Services: {availableServices}\n\n" +
                "Task: Select the TOP 3 services most likely to provide realistic data for this parameter.\n" +
                "Consider semantic meaning and naming patterns.\n\n" +
                "If you find good matches, respond with a JSON array of 1-3 service names in priority order:\n" +
                "[\"service1\", \"service2\", \"service3\"]\n\n" +
                "If NO services seem suitable for this parameter, respond with:\n" +
                "NO_GOOD_MATCH\n\n" +
                "Respond ONLY with the JSON array OR 'NO_GOOD_MATCH', no explanations.");
        
        llmPrompts.put("directValueExtraction",
                "API Response: {responseSchema}\n\n" +
                "Target Parameter: {parameterName} (type: {parameterType})\n" +
                "Description: {parameterDescription}\n\n" +
                "Task: Extract or derive a suitable value for this parameter from the API response above.\n" +
                "You must use ONLY values that appear in the response - do not generate new values.\n\n" +
                "Guidelines:\n" +
                "- Look for exact field matches first\n" +
                "- Consider semantically related fields\n" +
                "- Use any reasonable value from the response\n" +
                "- For list parameters: you can combine multiple values with commas\n" +
                "- Ensure the returned value matches the parameter type\n\n" +
                "Examples:\n" +
                "- For a location/name parameter: use values from semantically related fields (e.g. 'from', 'to', 'name')\n" +
                "- For 'price': use values from 'price' or cost-related fields\n" +
                "- For 'id': use any ID field from the response\n" +
                "- For a list parameter: use numeric values or names that match the element type\n\n" +
                "Respond with ONLY the extracted value (e.g., '<a value from the response>' or '100.0')\n" +
                "If no suitable value exists in the response: NO_GOOD_MATCH");
        
        llmPrompts.put("valueSelection",
                "Data: {extractedData}\n" +
                "Parameter: {parameterName} (type: {parameterType})\n" +
                "Description: {parameterDescription}\n\n" +
                "Task: Select the most suitable value for this parameter based on its name, type, and description.\n\n" +
                "If you find a suitable value, respond with just the value:\n" +
                "selectedValue\n\n" +
                "If NO value seems appropriate for this parameter, respond with:\n" +
                "NO_GOOD_MATCH");
    }
    
    // Getters and setters
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    
    public LocalDateTime getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(LocalDateTime lastUpdated) { this.lastUpdated = lastUpdated; }
    
    public Map<String, List<ApiMapping>> getParameterMappings() { return parameterMappings; }
    public void setParameterMappings(Map<String, List<ApiMapping>> parameterMappings) { 
        this.parameterMappings = parameterMappings; 
    }
    
    public List<ServicePattern> getServicePatterns() { return servicePatterns; }
    public void setServicePatterns(List<ServicePattern> servicePatterns) { 
        this.servicePatterns = servicePatterns; 
    }
    
    public Map<String, String> getLlmPrompts() { return llmPrompts; }
    public void setLlmPrompts(Map<String, String> llmPrompts) { this.llmPrompts = llmPrompts; }
    
    public CacheConfig getCacheConfig() { return cacheConfig; }
    public void setCacheConfig(CacheConfig cacheConfig) { this.cacheConfig = cacheConfig; }
    
    public Map<String, Map<String, List<ParameterError>>> getParameterErrors() { return parameterErrors; }
    public void setParameterErrors(Map<String, Map<String, List<ParameterError>>> parameterErrors) { 
        this.parameterErrors = parameterErrors; 
    }
    
    /**
     * Data transfer object for YAML serialization
     */
    private static class RegistryData {
        public String version;
        public LocalDateTime lastUpdated;
        public Map<String, List<ApiMappingData>> parameterMappings;
        public List<ServicePatternData> servicePatterns;
        public Map<String, String> llmPrompts;
        public CacheConfigData cache;
        public Map<String, Map<String, List<ParameterErrorData>>> parameterErrors;
        // Phase 1: per-value SUT-verified status; nested as endpoint -> param
        // -> value -> status. Optional in YAML; legacy files load with this
        // field null and we treat all values as UNVERIFIED on first run.
        public Map<String, Map<String, Map<String, PoolEntryStatus>>> poolEntryStatus;
        
        public static RegistryData fromRegistry(InputFetchRegistry registry) {
            RegistryData data = new RegistryData();
            data.version = registry.version;
            data.lastUpdated = registry.lastUpdated;
            data.llmPrompts = registry.llmPrompts;
            
            // Convert parameter mappings
            data.parameterMappings = new HashMap<>();
            for (Map.Entry<String, List<ApiMapping>> entry : registry.parameterMappings.entrySet()) {
                List<ApiMappingData> mappingDataList = entry.getValue().stream()
                        .map(ApiMappingData::fromApiMapping)
                        .collect(Collectors.toList());
                data.parameterMappings.put(entry.getKey(), mappingDataList);
            }
            
            // Convert service patterns
            data.servicePatterns = registry.servicePatterns.stream()
                    .map(ServicePatternData::fromServicePattern)
                    .collect(Collectors.toList());
            
            // Convert cache config
            data.cache = CacheConfigData.fromCacheConfig(registry.cacheConfig);
            
            // Convert parameter errors
            data.parameterErrors = new HashMap<>();
            for (Map.Entry<String, Map<String, List<ParameterError>>> endpointEntry : registry.parameterErrors.entrySet()) {
                Map<String, List<ParameterErrorData>> parameterErrorsData = new HashMap<>();
                for (Map.Entry<String, List<ParameterError>> paramEntry : endpointEntry.getValue().entrySet()) {
                    List<ParameterErrorData> errorDataList = paramEntry.getValue().stream()
                            .map(ParameterErrorData::fromParameterError)
                            .collect(Collectors.toList());
                    parameterErrorsData.put(paramEntry.getKey(), errorDataList);
                }
                data.parameterErrors.put(endpointEntry.getKey(), parameterErrorsData);
            }

            // Phase 1: serialize the per-value verified/rejected status map.
            // Deep copy so YAML serde doesn't share references with the live registry.
            if (registry.poolEntryStatus != null && !registry.poolEntryStatus.isEmpty()) {
                data.poolEntryStatus = new HashMap<>();
                for (Map.Entry<String, Map<String, Map<String, PoolEntryStatus>>> e1
                        : registry.poolEntryStatus.entrySet()) {
                    Map<String, Map<String, PoolEntryStatus>> byParam = new HashMap<>();
                    for (Map.Entry<String, Map<String, PoolEntryStatus>> e2 : e1.getValue().entrySet()) {
                        byParam.put(e2.getKey(), new HashMap<>(e2.getValue()));
                    }
                    data.poolEntryStatus.put(e1.getKey(), byParam);
                }
            }

            return data;
        }

        public InputFetchRegistry toRegistry() {
            InputFetchRegistry registry = new InputFetchRegistry();
            registry.version = this.version;
            registry.lastUpdated = this.lastUpdated;
            registry.llmPrompts = this.llmPrompts != null ? this.llmPrompts : new HashMap<>();
            
            // Convert parameter mappings
            registry.parameterMappings = new HashMap<>();
            if (this.parameterMappings != null) {
                for (Map.Entry<String, List<ApiMappingData>> entry : this.parameterMappings.entrySet()) {
                    List<ApiMapping> mappings = entry.getValue().stream()
                            .map(ApiMappingData::toApiMapping)
                            .collect(Collectors.toList());
                    registry.parameterMappings.put(entry.getKey(), mappings);
                }
            }
            
            // Convert service patterns
            if (this.servicePatterns != null) {
                registry.servicePatterns = this.servicePatterns.stream()
                        .map(ServicePatternData::toServicePattern)
                        .collect(Collectors.toList());
            }
            
            // Convert cache config
            if (this.cache != null) {
                registry.cacheConfig = this.cache.toCacheConfig();
            }
            
            // Convert parameter errors
            registry.parameterErrors = new HashMap<>();
            if (this.parameterErrors != null) {
                for (Map.Entry<String, Map<String, List<ParameterErrorData>>> endpointEntry : this.parameterErrors.entrySet()) {
                    Map<String, List<ParameterError>> parameterErrors = new HashMap<>();
                    for (Map.Entry<String, List<ParameterErrorData>> paramEntry : endpointEntry.getValue().entrySet()) {
                        List<ParameterError> errors = paramEntry.getValue().stream()
                                .map(data -> data.toParameterError())
                                .collect(Collectors.toList());
                        parameterErrors.put(paramEntry.getKey(), errors);
                    }
                    registry.parameterErrors.put(endpointEntry.getKey(), parameterErrors);
                }
            }

            // Phase 1: load per-value verified/rejected status. Legacy YAMLs
            // without the field load with poolEntryStatus already initialized
            // empty by the InputFetchRegistry constructor.
            if (this.poolEntryStatus != null) {
                for (Map.Entry<String, Map<String, Map<String, PoolEntryStatus>>> e1
                        : this.poolEntryStatus.entrySet()) {
                    Map<String, Map<String, PoolEntryStatus>> byParam = new HashMap<>();
                    for (Map.Entry<String, Map<String, PoolEntryStatus>> e2 : e1.getValue().entrySet()) {
                        byParam.put(e2.getKey(), new HashMap<>(e2.getValue()));
                    }
                    registry.poolEntryStatus.put(e1.getKey(), byParam);
                }
            }

            return registry;
        }
    }
    
    // Data transfer classes for YAML serialization
    private static class ApiMappingData {
        public String endpoint;
        public String method;
        public String service;
        public String extractPath;
        public int priority;
        public LocalDateTime lastUsed;
        public double successRate;
        public String description;
        // Bug audit Finding #15: persist the consumer scope so reads can filter by it.
        // Optional/back-compat — older YAMLs without this field are interpreted as global.
        public String consumerApiKey;

        public static ApiMappingData fromApiMapping(ApiMapping mapping) {
            ApiMappingData data = new ApiMappingData();
            data.endpoint = mapping.getEndpoint();
            data.method = mapping.getMethod();
            data.service = mapping.getService();
            data.extractPath = mapping.getExtractPath();
            data.priority = mapping.getPriority();
            data.lastUsed = mapping.getLastUsed();
            data.successRate = mapping.getSuccessRate();
            data.description = mapping.getDescription();
            data.consumerApiKey = mapping.getConsumerApiKey();
            return data;
        }

        public ApiMapping toApiMapping() {
            ApiMapping mapping = new ApiMapping();
            mapping.setEndpoint(this.endpoint);
            mapping.setMethod(this.method);
            mapping.setService(this.service);
            mapping.setExtractPath(this.extractPath);
            mapping.setPriority(this.priority);
            mapping.setLastUsed(this.lastUsed);
            mapping.setSuccessRate(this.successRate);
            mapping.setDescription(this.description);
            mapping.setConsumerApiKey(this.consumerApiKey);
            return mapping;
        }
    }
    
    private static class ServicePatternData {
        public String pattern;
        public List<String> services;
        public List<String> endpoints;
        
        public static ServicePatternData fromServicePattern(ServicePattern pattern) {
            ServicePatternData data = new ServicePatternData();
            data.pattern = pattern.getPattern();
            data.services = pattern.getServices();
            data.endpoints = pattern.getEndpoints();
            return data;
        }
        
        public ServicePattern toServicePattern() {
            return new ServicePattern(this.pattern, this.services, this.endpoints);
        }
    }
    
    private static class CacheConfigData {
        public boolean enabled;
        public int maxEntries;
        public int ttlSeconds;
        
        public static CacheConfigData fromCacheConfig(CacheConfig config) {
            CacheConfigData data = new CacheConfigData();
            data.enabled = config.isEnabled();
            data.maxEntries = config.getMaxEntries();
            data.ttlSeconds = config.getTtlSeconds();
            return data;
        }
        
        public CacheConfig toCacheConfig() {
            return new CacheConfig(this.enabled, this.maxEntries, this.ttlSeconds);
        }
    }
    
    private static class ParameterErrorData {
        public String errorType;
        public String errorReason;
        public String apiEndpoint;
        public String parameterName;
        public LocalDateTime timestamp;
        public String additionalInfo;
        
        public static ParameterErrorData fromParameterError(ParameterError error) {
            ParameterErrorData data = new ParameterErrorData();
            data.errorType = error.getErrorType();
            data.errorReason = error.getErrorReason();
            data.apiEndpoint = error.getApiEndpoint();
            data.parameterName = error.getParameterName();
            data.timestamp = error.getTimestamp();
            data.additionalInfo = error.getAdditionalInfo();
            return data;
        }
        
        public ParameterError toParameterError() {
            return new ParameterError(this.errorType, this.errorReason, this.apiEndpoint, 
                                    this.parameterName, this.timestamp, this.additionalInfo);
        }
    }
    
    /**
     * Finds semantically equivalent error using text similarity algorithms
     */
    private ParameterError findSemanticallyEquivalentError(List<ParameterError> existingErrors, ParameterError newError) {
        if (existingErrors.isEmpty()) {
            return null;
        }
        
        // First check for exact matches (fast path)
        for (ParameterError existing : existingErrors) {
            if (existing.getErrorType().equals(newError.getErrorType()) && 
                existing.getErrorReason().equals(newError.getErrorReason())) {
                return existing;
            }
        }
        
        // Then check for semantic similarity (only if error types match)
        for (ParameterError existing : existingErrors) {
            if (existing.getErrorType().equals(newError.getErrorType()) && 
                areErrorReasonsSemanticallyEquivalent(existing.getErrorReason(), newError.getErrorReason())) {
                return existing;
            }
        }
        
        return null;
    }
    
    /**
     * Determines if two error reasons are semantically equivalent using multiple similarity metrics
     */
    private boolean areErrorReasonsSemanticallyEquivalent(String reason1, String reason2) {
        if (reason1 == null || reason2 == null) {
            return reason1 == reason2;
        }
        
        // Normalize both reasons for comparison
        String normalized1 = normalizeForComparison(reason1);
        String normalized2 = normalizeForComparison(reason2);
        
        // If normalized versions are identical, they're equivalent
        if (normalized1.equals(normalized2)) {
            return true;
        }
        
        // Calculate multiple similarity scores
        double jaccardSimilarity = calculateJaccardSimilarity(normalized1, normalized2);
        double levenshteinSimilarity = calculateLevenshteinSimilarity(normalized1, normalized2);
        double keywordOverlap = calculateKeywordOverlap(normalized1, normalized2);
        
        // Consider errors semantically equivalent if they meet similarity thresholds
        return jaccardSimilarity >= 0.75 || 
               levenshteinSimilarity >= 0.80 || 
               keywordOverlap >= 0.85;
    }
    
    /**
     * Normalizes error reason text for semantic comparison
     */
    private String normalizeForComparison(String text) {
        return text.toLowerCase()
                   .replaceAll("[^a-z0-9\\s]", " ")  // Remove punctuation
                   .replaceAll("\\b(the|a|an|is|are|was|were|this|that|these|those|it|its|caused?|resulting?|due|because)\\b", " ")  // Remove common words
                   .replaceAll("\\bjackson\\b", "parser")  // Normalize technical terms
                   .replaceAll("\\bdeseriali[sz]ation?\\b", "parsing")
                   .replaceAll("\\bmismatchedinputexception\\b", "type error")
                   .replaceAll("\\bapi\\s+expects?\\b", "expected")
                   .replaceAll("\\bprovided\\s+value\\b", "value")
                   .replaceAll("\\s+", " ")  // Normalize whitespace
                   .trim();
    }
    
    /**
     * Calculates Jaccard similarity between two text strings
     */
    private double calculateJaccardSimilarity(String text1, String text2) {
        Set<String> words1 = new HashSet<>(Arrays.asList(text1.split("\\s+")));
        Set<String> words2 = new HashSet<>(Arrays.asList(text2.split("\\s+")));
        
        Set<String> intersection = new HashSet<>(words1);
        intersection.retainAll(words2);
        
        Set<String> union = new HashSet<>(words1);
        union.addAll(words2);
        
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }
    
    /**
     * Calculates Levenshtein similarity (normalized edit distance)
     */
    private double calculateLevenshteinSimilarity(String text1, String text2) {
        int distance = calculateLevenshteinDistance(text1, text2);
        int maxLength = Math.max(text1.length(), text2.length());
        return maxLength == 0 ? 1.0 : 1.0 - (double) distance / maxLength;
    }
    
    /**
     * Calculates Levenshtein distance between two strings
     */
    private int calculateLevenshteinDistance(String a, String b) {
        if (a.length() == 0) return b.length();
        if (b.length() == 0) return a.length();
        
        int[][] matrix = new int[a.length() + 1][b.length() + 1];
        
        for (int i = 0; i <= a.length(); i++) {
            matrix[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            matrix[0][j] = j;
        }
        
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
                matrix[i][j] = Math.min(Math.min(
                    matrix[i - 1][j] + 1,      // deletion
                    matrix[i][j - 1] + 1),     // insertion
                    matrix[i - 1][j - 1] + cost // substitution
                );
            }
        }
        
        return matrix[a.length()][b.length()];
    }
    
    /**
     * Calculates keyword overlap similarity focusing on technical terms
     */
    private double calculateKeywordOverlap(String text1, String text2) {
        Set<String> keywords1 = extractKeywords(text1);
        Set<String> keywords2 = extractKeywords(text2);
        
        if (keywords1.isEmpty() && keywords2.isEmpty()) {
            return 1.0;
        }
        
        Set<String> intersection = new HashSet<>(keywords1);
        intersection.retainAll(keywords2);
        
        Set<String> union = new HashSet<>(keywords1);
        union.addAll(keywords2);
        
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }
    
    /**
     * Extracts important keywords from error text
     */
    private Set<String> extractKeywords(String text) {
        Set<String> keywords = new HashSet<>();
        String[] words = text.split("\\s+");
        
        for (String word : words) {
            if (word.length() >= 3 && isImportantKeyword(word)) {
                keywords.add(word);
            }
        }
        
        return keywords;
    }
    
    /**
     * Determines if a word is an important keyword for error comparison
     */
    private boolean isImportantKeyword(String word) {
        return word.matches("(?i).*(array|list|string|number|numeric|integer|json|type|error|format|parsing|expected|value|parameter|field|mismatch).*");
    }
    
    /**
     * Determines if existing error should be updated with new error information
     */
    private boolean shouldUpdateExistingError(ParameterError existing, ParameterError newError) {
        // Update if new error has more detailed reason
        if (newError.getErrorReason().length() > existing.getErrorReason().length() + 20) {
            return true;
        }
        
        // Update if new error has additional info and existing doesn't
        if (newError.getAdditionalInfo() != null && existing.getAdditionalInfo() == null) {
            return true;
        }
        
        // Update if new error is more recent (within reason)
        if (newError.getTimestamp().isAfter(existing.getTimestamp().plusMinutes(5))) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Merges information from new error into existing error
     */
    private void mergeErrorInformation(ParameterError existing, ParameterError newError) {
        // Update with more detailed reason if applicable
        if (newError.getErrorReason().length() > existing.getErrorReason().length()) {
            existing.setErrorReason(newError.getErrorReason());
        }
        
        // Update additional info if new error has it and existing doesn't
        if (newError.getAdditionalInfo() != null && existing.getAdditionalInfo() == null) {
            existing.setAdditionalInfo(newError.getAdditionalInfo());
        }
        
        // Update timestamp to latest
        if (newError.getTimestamp().isAfter(existing.getTimestamp())) {
            existing.setTimestamp(newError.getTimestamp());
        }
    }
    
    /**
     * Decode percent-encoded segments in a URL path so the same logical endpoint with
     * different encodings ({@code /foo/%25} vs {@code /foo/%}) shares one error bucket.
     * Falls back to the raw input on decode failure (Bug audit Finding #40).
     */
    private static String decodeUrlForKey(String apiEndpoint) {
        if (apiEndpoint == null || apiEndpoint.indexOf('%') < 0) return apiEndpoint;
        try {
            return java.net.URLDecoder.decode(apiEndpoint, java.nio.charset.StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return apiEndpoint;
        }
    }

    /**
     * Truncates text for logging purposes only (doesn't affect stored data)
     */
    private String truncateForLog(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }
} 