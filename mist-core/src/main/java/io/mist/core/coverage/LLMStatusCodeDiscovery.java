package io.mist.core.coverage;

import io.mist.llm.LLMConfig;
import io.mist.llm.LLMService;
import io.mist.core.config.CacheToggle;
import io.mist.core.llm.ParameterInfo;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Uses LLM to discover ALL possible HTTP status codes for an API operation.
 * This class runs AFTER first execution to use actual results for informed discovery.
 * 
 * The discovery is fully dynamic - the LLM analyzes the API semantics and identifies
 * all possible status codes (not just common ones), along with strategies to trigger each.
 */
public class LLMStatusCodeDiscovery {
    
    private static final Logger log = LogManager.getLogger(LLMStatusCodeDiscovery.class);

    /** Heuristic: a "stable" path segment starts with a lowercase letter, is at
     * least 2 characters long, and consists only of lowercase letters/digits/
     * {._-}. Curly-brace template literals like "{id}" are also stable.
     *
     * <p>Min-length 2 collapses single-character path values ({@code /account/x},
     * {@code /trip/a}) while preserving common short resource names with digits
     * like {@code v1}, {@code v2}. Anything else (URL-encoded values, FALLBACK_*,
     * mixed case, leading digit, special chars, single letters) is treated as a
     * parameter value and replaced with {@code {id}} in {@link #normalizePath}.
     */
    private static final Pattern STABLE_PATH_SEGMENT =
            Pattern.compile("^([a-z][a-z0-9_.\\-]{1,29}|\\{[^}]+\\})$");

    /** Default disk path for the persistent cache. */
    private static final String DEFAULT_CACHE_PATH = ".mist/llm-status-code-discovery-cache.json";

    /** Property: cache file path (default {@value #DEFAULT_CACHE_PATH}).
     *  Read/write enable comes from the shared {@link CacheToggle} — there is
     *  NO per-cache read/write toggle, intentionally, to keep operator UX to a
     *  single {@code mst.cache.read} / {@code mst.cache.write} pair. */
    public static final String PROP_PATH = "mst.status.code.discovery.cache.path";

    private final LLMService llmService;

    // Signature-keyed cache: same endpoint signature → same status-code list
    // regardless of which concrete URL/value the caller passed. Persisted to
    // disk so identical signatures across runs reuse the LLM result and the
    // run is reproducible for paper/A会 numbers. Key shape:
    //    "<METHOD> <normalizedPath>|<paramSchemaFingerprint>"
    private final Map<String, List<StatusCodeTarget>> discoveryCache = new HashMap<>();
    private final Path persistPath;
    private final Object diskLock = new Object();

    public LLMStatusCodeDiscovery(LLMService llmService) {
        this(llmService, Paths.get(System.getProperty(PROP_PATH, DEFAULT_CACHE_PATH)));
    }

    /** Package-visible constructor for tests: lets the test pin the cache file
     *  regardless of System properties. Read/write decisions still come from
     *  {@link CacheToggle} at lookup/save time so tests can flip
     *  {@code mst.cache.read} / {@code mst.cache.write} in the same way as
     *  production. */
    LLMStatusCodeDiscovery(LLMService llmService, Path persistPath) {
        this.llmService = llmService;
        this.persistPath = persistPath;
        // Always load on construction — read-only playback (cache.read=true,
        // cache.write=false) still needs entries in memory; refresh mode
        // (read=false, write=true) loads so new puts merge with existing
        // rather than clobber the on-disk file.
        loadFromDisk();
        if (!CacheToggle.canRead() || !CacheToggle.canWrite()) {
            log.info("LLMStatusCodeDiscovery cache: master read={} write={} (loaded {} entries)",
                    CacheToggle.canRead(), CacheToggle.canWrite(), discoveryCache.size());
        }
    }
    
    /**
     * Create instance from properties.
     */
    public static LLMStatusCodeDiscovery fromProperties(Map<String, String> properties) {
        LLMConfig config = LLMConfig.fromProperties(properties);
        LLMService service = LLMService.getInstance(config);
        return new LLMStatusCodeDiscovery(service);
    }
    
    /**
     * Discover all possible status codes for an API operation.
     * Uses execution results to inform the discovery.
     * 
     * @param serviceName The microservice name
     * @param httpMethod The HTTP method (GET, POST, etc.)
     * @param path The API path
     * @param parameters List of parameters for this operation
     * @param observedStatusCodes Status codes already observed in execution
     * @param sampleResponses Sample responses from execution (for context)
     * @return List of StatusCodeTarget objects representing all possible status codes
     */
    public List<StatusCodeTarget> discoverStatusCodes(
            String serviceName,
            String httpMethod,
            String path,
            List<ParameterInfo> parameters,
            Set<Integer> observedStatusCodes,
            List<String> sampleResponses) {
        
        // Build the signature-based cache key. Same (method, normalized-path,
        // sorted-parameter-schema) returns the same status-code list regardless
        // of which concrete URL the caller passed. Observed status codes and
        // sample responses are NOT in the key — they're hints to the LLM on
        // first call, not part of the endpoint identity.
        String cacheKey = buildSignatureCacheKey(httpMethod, path, parameters);

        // Check cache first. Read is gated by the master {@link CacheToggle}:
        // when {@code mst.cache.read=false} the lookup is skipped so every
        // call hits the LLM (useful for refresh mode with write=true).
        if (CacheToggle.canRead() && discoveryCache.containsKey(cacheKey)) {
            log.debug("Using cached status code discovery for {}", cacheKey);
            return new ArrayList<>(discoveryCache.get(cacheKey));
        }

        log.info("Discovering status codes for {} {} (service: {})", httpMethod, path, serviceName);

        String prompt = buildDiscoveryPrompt(serviceName, httpMethod, path, parameters,
                                             observedStatusCodes, sampleResponses);

        String systemPrompt = buildSystemPrompt();
        
        try {
            String llmResponse = llmService.generateText(systemPrompt, prompt, 2000, 0.3);
            
            if (llmResponse == null || llmResponse.trim().isEmpty()) {
                log.warn("LLM returned empty response for status code discovery");
                return createDefaultTargets(observedStatusCodes);
            }
            
            List<StatusCodeTarget> targets = parseDiscoveryResponse(llmResponse);
            
            if (targets.isEmpty()) {
                log.warn("Failed to parse LLM response, using defaults");
                return createDefaultTargets(observedStatusCodes);
            }
            
            // Cache the result + persist to disk so a future run with the same
            // endpoint signature hits the cache instead of re-asking the LLM.
            // Skipped when master {@code mst.cache.write=false} (read-only playback).
            if (CacheToggle.canWrite()) {
                discoveryCache.put(cacheKey, new ArrayList<>(targets));
                saveToDisk();
            }

            log.info("Discovered {} possible status codes for {}: {}",
                targets.size(), cacheKey,
                targets.stream().map(t -> String.valueOf(t.getStatusCode())).reduce((a, b) -> a + ", " + b).orElse(""));

            return targets;

        } catch (Exception e) {
            log.error("Error during status code discovery for {}: {}", cacheKey, e.getMessage(), e);
            return createDefaultTargets(observedStatusCodes);
        }
    }
    
    /**
     * Simplified discovery without execution context (for initial setup).
     */
    public List<StatusCodeTarget> discoverStatusCodes(
            String serviceName,
            String httpMethod,
            String path,
            List<ParameterInfo> parameters) {
        return discoverStatusCodes(serviceName, httpMethod, path, parameters, 
                                   Collections.emptySet(), Collections.emptyList());
    }
    
    private String buildSystemPrompt() {
        return "You are an API testing expert specializing in HTTP status codes and REST API behavior.\n" +
               "Your task is to analyze API operations and identify ALL possible HTTP status codes they could return.\n\n" +
               "Be comprehensive - consider all standard HTTP status codes (1xx, 2xx, 3xx, 4xx, 5xx).\n" +
               "For each status code, provide a clear strategy to trigger it and suggested parameter values.\n\n" +
               "Always respond with valid JSON only. No markdown, no explanations outside the JSON.";
    }
    
    private String buildDiscoveryPrompt(
            String serviceName,
            String httpMethod,
            String path,
            List<ParameterInfo> parameters,
            Set<Integer> observedStatusCodes,
            List<String> sampleResponses) {
        
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("Analyze this REST API operation and identify ALL HTTP status codes it could possibly return.\n\n");
        
        prompt.append("=== API OPERATION ===\n");
        prompt.append("Service: ").append(serviceName).append("\n");
        prompt.append("Method: ").append(httpMethod).append("\n");
        prompt.append("Path: ").append(path).append("\n\n");
        
        if (parameters != null && !parameters.isEmpty()) {
            prompt.append("=== PARAMETERS ===\n");
            for (ParameterInfo param : parameters) {
                prompt.append("- ").append(param.getName())
                      .append(" (").append(param.getType()).append(")")
                      .append(Boolean.TRUE.equals(param.getRequired()) ? " [REQUIRED]" : " [optional]");
                if (param.getDescription() != null && !param.getDescription().isEmpty()) {
                    prompt.append(": ").append(param.getDescription());
                }
                prompt.append("\n");
            }
            prompt.append("\n");
        }
        
        if (!observedStatusCodes.isEmpty()) {
            prompt.append("=== ALREADY OBSERVED STATUS CODES ===\n");
            prompt.append("From first execution: ").append(observedStatusCodes).append("\n\n");
        }
        
        if (sampleResponses != null && !sampleResponses.isEmpty()) {
            prompt.append("=== SAMPLE RESPONSES ===\n");
            for (int i = 0; i < Math.min(3, sampleResponses.size()); i++) {
                String response = sampleResponses.get(i);
                if (response.length() > 500) {
                    response = response.substring(0, 500) + "...";
                }
                prompt.append("Response ").append(i + 1).append(": ").append(response).append("\n");
            }
            prompt.append("\n");
        }
        
        prompt.append("=== TASK ===\n");
        prompt.append("Identify ALL possible HTTP status codes this API could return.\n");
        prompt.append("Consider these categories:\n");
        prompt.append("- 2xx Success: 200, 201, 202, 204, 206...\n");
        prompt.append("- 4xx Client Errors: 400, 401, 403, 404, 405, 409, 422, 429...\n");
        prompt.append("- 5xx Server Errors: 500, 502, 503, 504...\n");
        prompt.append("- 3xx Redirects if applicable: 301, 302, 304...\n\n");
        
        prompt.append("For EACH status code, provide:\n");
        prompt.append("1. statusCode: The HTTP status code number\n");
        prompt.append("2. category: Category name (Success, Client Error, Server Error, Redirect)\n");
        prompt.append("3. description: When/why this status code would be returned\n");
        prompt.append("4. triggerStrategy: How to trigger this status code\n");
        prompt.append("5. requiresAuthManipulation: true/false - does triggering require auth changes?\n");
        prompt.append("6. suggestedInputs: Parameter values to trigger this code (JSON object)\n\n");
        
        prompt.append("=== RESPONSE FORMAT ===\n");
        prompt.append("Respond with a JSON array ONLY (no markdown, no explanation):\n");
        prompt.append("[\n");
        prompt.append("  {\n");
        prompt.append("    \"statusCode\": 200,\n");
        prompt.append("    \"category\": \"Success\",\n");
        prompt.append("    \"description\": \"Successful operation\",\n");
        prompt.append("    \"triggerStrategy\": \"Provide valid inputs for all parameters\",\n");
        prompt.append("    \"requiresAuthManipulation\": false,\n");
        prompt.append("    \"suggestedInputs\": {\"param1\": \"validValue\"}\n");
        prompt.append("  },\n");
        prompt.append("  ...\n");
        prompt.append("]\n");
        
        return prompt.toString();
    }
    
    /**
     * Parse the LLM response into StatusCodeTarget objects.
     */
    List<StatusCodeTarget> parseDiscoveryResponse(String llmResponse) {
        List<StatusCodeTarget> targets = new ArrayList<>();
        
        try {
            // Try to extract JSON array from response
            String jsonStr = extractJsonArray(llmResponse);
            
            if (jsonStr == null || jsonStr.isEmpty()) {
                log.warn("Could not extract JSON array from LLM response");
                return targets;
            }
            
            JSONArray jsonArray = new JSONArray(jsonStr);
            
            for (int i = 0; i < jsonArray.length(); i++) {
                try {
                    JSONObject entry = jsonArray.getJSONObject(i);
                    StatusCodeTarget target = StatusCodeTarget.fromLLMResponse(entry);
                    targets.add(target);
                    log.debug("Parsed status code target: {}", target);
                } catch (Exception e) {
                    log.warn("Failed to parse status code entry {}: {}", i, e.getMessage());
                }
            }
            
        } catch (Exception e) {
            log.error("Failed to parse LLM discovery response: {}", e.getMessage());
        }
        
        return targets;
    }
    
    /**
     * Extract JSON array from LLM response (handles markdown code blocks, etc.)
     */
    private String extractJsonArray(String response) {
        if (response == null) return null;
        
        String cleaned = response.trim();
        
        // Remove markdown code blocks if present
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
        
        // Find the JSON array
        int arrayStart = cleaned.indexOf('[');
        int arrayEnd = cleaned.lastIndexOf(']');
        
        if (arrayStart >= 0 && arrayEnd > arrayStart) {
            return cleaned.substring(arrayStart, arrayEnd + 1);
        }
        
        return null;
    }
    
    /**
     * Create default status code targets when LLM fails.
     * Based on common REST API patterns.
     */
    private List<StatusCodeTarget> createDefaultTargets(Set<Integer> observedCodes) {
        List<StatusCodeTarget> defaults = new ArrayList<>();
        
        // Always include common status codes
        if (!observedCodes.contains(200)) {
            defaults.add(new StatusCodeTarget.Builder(200)
                .category("Success")
                .description("Successful operation")
                .triggerStrategy("Provide valid inputs")
                .build());
        }
        
        if (!observedCodes.contains(400)) {
            defaults.add(new StatusCodeTarget.Builder(400)
                .category("Client Error")
                .description("Bad request - invalid input")
                .triggerStrategy("Provide malformed or invalid parameters")
                .build());
        }
        
        if (!observedCodes.contains(401)) {
            defaults.add(new StatusCodeTarget.Builder(401)
                .category("Client Error")
                .description("Unauthorized - missing or invalid auth")
                .triggerStrategy("Remove or invalidate authentication")
                .requiresAuthManipulation(true)
                .build());
        }
        
        if (!observedCodes.contains(403)) {
            defaults.add(new StatusCodeTarget.Builder(403)
                .category("Client Error")
                .description("Forbidden - insufficient permissions")
                .triggerStrategy("Use credentials with insufficient permissions")
                .requiresAuthManipulation(true)
                .build());
        }
        
        if (!observedCodes.contains(404)) {
            defaults.add(new StatusCodeTarget.Builder(404)
                .category("Client Error")
                .description("Not found - resource does not exist")
                .triggerStrategy("Use non-existent resource ID")
                .addSuggestedInput("id", "NONEXISTENT_ID_99999")
                .build());
        }
        
        if (!observedCodes.contains(500)) {
            defaults.add(new StatusCodeTarget.Builder(500)
                .category("Server Error")
                .description("Internal server error")
                .triggerStrategy("Trigger edge case or malformed data")
                .build());
        }
        
        return defaults;
    }
    
    /**
     * Get a unique key for an API operation. PUBLIC because external code (e.g.
     * {@code StatusCodeCoverageTracker}) keys by this same string.
     */
    public static String getApiKey(String httpMethod, String path) {
        return httpMethod.toUpperCase() + " " + normalizePath(path);
    }

    /**
     * Normalize a concrete request path to a stable endpoint signature.
     *
     * <p>Path parameter values come from the test generator's invalid-input pools
     * — values like {@code FALLBACK_orderId_3}, {@code %00%01%02},
     * {@code ../../../etc/passwd}, {@code XXXX…overflow}, or random UUIDs —
     * which would make each invocation produce a different cache key and
     * defeat reuse. We replace every "value-like" path segment with the
     * literal {@code {id}} so the normalized path matches what the OpenAPI
     * path template would look like.
     *
     * <p>A segment is "stable" (kept verbatim) if it starts with a lowercase
     * letter and consists only of lowercase letters, digits, {@code _}, {@code .},
     * or {@code -}; or if it is already a {@code {…}} template literal.
     * Everything else is normalized.
     */
    public static String normalizePath(String path) {
        if (path == null || path.isEmpty()) return path == null ? "" : path;
        String[] parts = path.split("/", -1);
        StringBuilder sb = new StringBuilder(path.length());
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append('/');
            String seg = parts[i];
            if (seg.isEmpty()) continue;
            if (STABLE_PATH_SEGMENT.matcher(seg).matches()) {
                sb.append(seg);
            } else {
                sb.append("{id}");
            }
        }
        return sb.toString();
    }

    /**
     * Build the signature-based cache key from method + normalized path +
     * parameter schema. Schema fingerprint sorts param tuples
     * {@code name|type|location|required} alphabetically so map-iteration
     * order is irrelevant and the key is deterministic.
     */
    static String buildSignatureCacheKey(String httpMethod, String path,
                                         List<ParameterInfo> parameters) {
        List<String> rows = new ArrayList<>();
        if (parameters != null) {
            for (ParameterInfo p : parameters) {
                String name = p.getName() == null ? "" : p.getName();
                String type = p.getType() == null ? "" : p.getType();
                String loc = p.getInLocation() == null ? "" : p.getInLocation();
                boolean req = Boolean.TRUE.equals(p.getRequired());
                rows.add(name + "|" + type + "|" + loc + "|" + req);
            }
        }
        Collections.sort(rows);
        return getApiKey(httpMethod, path) + "|" + String.join(",", rows);
    }

    /**
     * Load cache entries from disk on startup. Missing file is a cold start
     * (info-logged, not an error). A malformed file logs a warning and the
     * cache stays empty — we never throw at construction because callers may
     * not have control over the persistent file's state.
     */
    private void loadFromDisk() {
        if (persistPath == null) return;
        if (!Files.exists(persistPath)) {
            log.info("LLMStatusCodeDiscovery cache: cold start, no file at {}", persistPath);
            return;
        }
        try {
            String content = new String(Files.readAllBytes(persistPath), StandardCharsets.UTF_8);
            if (content.trim().isEmpty()) return;
            JSONObject obj = new JSONObject(content);
            int loaded = 0;
            for (String key : obj.keySet()) {
                try {
                    JSONArray arr = obj.getJSONArray(key);
                    List<StatusCodeTarget> targets = parseDiscoveryResponse(arr.toString());
                    if (!targets.isEmpty()) {
                        discoveryCache.put(key, targets);
                        loaded++;
                    }
                } catch (Exception entryEx) {
                    log.warn("LLMStatusCodeDiscovery cache: skipping malformed entry {}: {}",
                            key, entryEx.getMessage());
                }
            }
            log.info("LLMStatusCodeDiscovery cache: loaded {} entries from {}", loaded, persistPath);
        } catch (Exception e) {
            log.warn("LLMStatusCodeDiscovery cache: failed to load {}: {}", persistPath, e.getMessage());
        }
    }

    /**
     * Persist the in-memory cache to disk via atomic temp+rename so partial
     * writes don't corrupt the file on JVM crash.
     */
    private void saveToDisk() {
        if (persistPath == null) return;
        synchronized (diskLock) {
            try {
                JSONObject obj = new JSONObject();
                for (Map.Entry<String, List<StatusCodeTarget>> e : discoveryCache.entrySet()) {
                    JSONArray arr = new JSONArray();
                    for (StatusCodeTarget t : e.getValue()) {
                        arr.put(t.toJSON());
                    }
                    obj.put(e.getKey(), arr);
                }
                Path parent = persistPath.getParent();
                if (parent != null) Files.createDirectories(parent);
                Path tmp = persistPath.resolveSibling(persistPath.getFileName().toString() + ".tmp");
                Files.write(tmp, obj.toString(2).getBytes(StandardCharsets.UTF_8));
                try {
                    Files.move(tmp, persistPath,
                            StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (java.nio.file.AtomicMoveNotSupportedException atomicEx) {
                    Files.move(tmp, persistPath, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException ioe) {
                log.warn("LLMStatusCodeDiscovery cache: save failed: {}", ioe.getMessage());
            }
        }
    }

    /**
     * Clear the discovery cache.
     */
    public void clearCache() {
        discoveryCache.clear();
    }
    
    /**
     * Check if an API has been discovered.
     */
    public boolean hasDiscovery(String httpMethod, String path) {
        return discoveryCache.containsKey(getApiKey(httpMethod, path));
    }
    
    /**
     * Get cached discovery for an API.
     */
    public List<StatusCodeTarget> getCachedDiscovery(String httpMethod, String path) {
        String apiKey = getApiKey(httpMethod, path);
        return discoveryCache.containsKey(apiKey) ? 
            new ArrayList<>(discoveryCache.get(apiKey)) : Collections.emptyList();
    }
}
