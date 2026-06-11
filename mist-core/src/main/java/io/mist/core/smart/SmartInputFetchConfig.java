package io.mist.core.smart;

import java.util.Map;
import java.util.List;

/**
 * Configuration for the Smart Input Fetching System
 * Loads settings from properties and registry files
 */
public class SmartInputFetchConfig {
    
    private boolean enabled;
    private double smartFetchPercentage;
    private String registryPath;
    private String openApiSpecPath;
    private boolean llmDiscoveryEnabled;
    private boolean llmEndpointSelectionEnabled;
    private int maxCandidates;
    private boolean dependencyResolutionEnabled;
    private long discoveryTimeoutMs;
    private long connectTimeoutMs;
    private long readTimeoutMs;
    private boolean cacheEnabled;
    private int cacheTtlSeconds;
    private int defaultPriority;
    private int patternDiscoveryPriority;
    private int llmDiscoveryPriority;
    private String defaultContentType;
    private int successResponseCode;
    private long schemaDiscoveryTimeoutMs;
    /**
     * Bug audit Finding #10: maximum LLM prompt size in characters. The previous codebase
     * baked a literal {@code 2044} cap across 9 sites (a GPT4All limit). Production runs
     * use Ollama qwen2.5-coder:14b with a 32 K context, so 2044 was silently dropping
     * useful schema context. Default is now 8000; users on smaller-context models can lower
     * via {@code smart.input.fetch.max.prompt.chars}.
     */
    private int maxPromptChars;
    /**
     * Bug audit Finding #35: when true (default), LLM-fallback values are added to the
     * diverse-value cache to broaden test diversity. When false, only smart-fetched values
     * (from real upstream APIs) populate the cache, so it represents "what the SUT actually
     * produces" rather than "what we guessed". Toggle via
     * {@code smart.input.fetch.cache.llm.fallback}.
     */
    private boolean cacheLlmFallbackValues;
    /**
     * Bug audit Finding #27 follow-up: EMA learning rate for {@link ApiMapping#updateSuccessRate}.
     * Higher α (0.0..1.0) lets a fresh upstream recover from past failures faster; lower α
     * smooths over transient noise. Default 0.1 matches the original hardcoded value.
     */
    private double emaAlpha;
    /**
     * Decay window in days for the recentness axis of {@link ApiMapping#calculateScore}.
     * A mapping last used today scores 1.0; one last used {@code decayDays}+ days ago scores 0.0.
     */
    private int decayDays;
    /**
     * Fresh-review Finding F17: target count of diverse values to keep in the rotation pool
     * per parameter. Was previously read directly via {@code System.getProperty} bypassing
     * this config object; now plumbed here for consistency.
     */
    private int diverseTargetCount;

    // Authentication settings.
    // Bug audit Finding #12: the authUser* fields had no consumers in the codebase and were
    // removed. The single-tenant admin-only auth is documented in SmartFetchAuthManager;
    // a dual-profile setup would require explicit caller plumbing, not just a config field.
    private String authAdminUsername;
    private String authAdminPassword;
    
    // Default constructor
    public SmartInputFetchConfig() {
        // Default values
        this.enabled = false;
        // Grounding-first default: 1.0 makes the percentage gate always attempt a smart
        // fetch and fall back to the LLM only when it yields no usable value, so grounding
        // works generically without per-SUT tuning. Lower it per SUT to re-introduce
        // deliberate LLM diversity injection.
        this.smartFetchPercentage = 1.0;
        this.registryPath = "input-fetch-registry.yaml";
        this.openApiSpecPath = ""; // no SUT-specific default; fromProperties falls back to oas.path
        this.llmDiscoveryEnabled = true;
        this.llmEndpointSelectionEnabled = true;
        this.maxCandidates = 5;
        this.dependencyResolutionEnabled = true;
        this.discoveryTimeoutMs = 5000;
        this.connectTimeoutMs = 2000;
        this.readTimeoutMs = 8000;
        this.cacheEnabled = true;
        this.cacheTtlSeconds = 300;
        this.defaultPriority = 5;
        this.patternDiscoveryPriority = 5;
        this.llmDiscoveryPriority = 7;
        this.defaultContentType = "application/json";
        this.successResponseCode = 200;
        this.schemaDiscoveryTimeoutMs = 3000;
        this.maxPromptChars = 8000;
        // Bug audit Finding #35 follow-up: default flipped to false so the diverse cache
        // reflects only smart-fetched values from real upstreams; operators can opt back in
        // via {@code smart.input.fetch.cache.llm.fallback=true}.
        this.cacheLlmFallbackValues = false;
        this.emaAlpha = 0.1;
        this.decayDays = 30;
        this.diverseTargetCount = 10;
    }
    
    /**
     * Load configuration from properties
     */
    public static SmartInputFetchConfig fromProperties(Map<String, String> properties) {
        SmartInputFetchConfig config = new SmartInputFetchConfig();
        
        config.enabled = Boolean.parseBoolean(
            properties.getOrDefault("smart.input.fetch.enabled", "false"));
        
        config.smartFetchPercentage = Double.parseDouble(
            properties.getOrDefault("smart.input.fetch.percentage", "1.0"));
        
        config.registryPath = properties.getOrDefault(
            "smart.input.fetch.registry.path", "input-fetch-registry.yaml");
        
        // Default to the SUT's own OpenAPI spec (oas.path) rather than a hardcoded
        // train-ticket spec, so smart-fetch grounds against the SUT actually under test.
        config.openApiSpecPath = properties.getOrDefault(
            "smart.input.fetch.openapi.spec.path",
            properties.getOrDefault("oas.path", ""));
        
        config.llmDiscoveryEnabled = Boolean.parseBoolean(
            properties.getOrDefault("smart.input.fetch.llm.discovery.enabled", "true"));

        config.llmEndpointSelectionEnabled = Boolean.parseBoolean(
            properties.getOrDefault("smart.input.fetch.llm.endpoint.selection.enabled", "true"));

        config.maxCandidates = Integer.parseInt(
            properties.getOrDefault("smart.input.fetch.max.candidates", "5"));
        
        config.dependencyResolutionEnabled = Boolean.parseBoolean(
            properties.getOrDefault("smart.input.fetch.dependency.resolution.enabled", "true"));
        
        config.discoveryTimeoutMs = Long.parseLong(
            properties.getOrDefault("smart.input.fetch.discovery.timeout.ms", "5000"));

        // Bug audit Finding #28: split connect/read timeouts. If unset, both fall back to
        // discoveryTimeoutMs to preserve the previous behavior.
        config.connectTimeoutMs = Long.parseLong(
            properties.getOrDefault("smart.input.fetch.connect.timeout.ms",
                String.valueOf(config.discoveryTimeoutMs)));
        config.readTimeoutMs = Long.parseLong(
            properties.getOrDefault("smart.input.fetch.read.timeout.ms",
                String.valueOf(config.discoveryTimeoutMs)));

        config.cacheEnabled = Boolean.parseBoolean(
            properties.getOrDefault("smart.input.fetch.cache.enabled", "true"));
        
        config.cacheTtlSeconds = Integer.parseInt(
            properties.getOrDefault("smart.input.fetch.cache.ttl.seconds", "300"));
        
        config.defaultPriority = Integer.parseInt(
            properties.getOrDefault("smart.input.fetch.default.priority", "5"));
        
        config.patternDiscoveryPriority = Integer.parseInt(
            properties.getOrDefault("smart.input.fetch.pattern.discovery.priority", "5"));
        
        config.llmDiscoveryPriority = Integer.parseInt(
            properties.getOrDefault("smart.input.fetch.llm.discovery.priority", "7"));
        
        config.defaultContentType = properties.getOrDefault(
            "smart.input.fetch.http.content.type", "application/json");
        
        config.successResponseCode = Integer.parseInt(
            properties.getOrDefault("smart.input.fetch.http.success.code", "200"));
        
        config.schemaDiscoveryTimeoutMs = Long.parseLong(
            properties.getOrDefault("smart.input.fetch.schema.discovery.timeout.ms", "3000"));

        config.maxPromptChars = Integer.parseInt(
            properties.getOrDefault("smart.input.fetch.max.prompt.chars", "8000"));

        // Bug audit Finding #35 follow-up: default false; operators opt-in if they want
        // LLM-fallback values in the diverse cache for broader test diversity.
        config.cacheLlmFallbackValues = Boolean.parseBoolean(
            properties.getOrDefault("smart.input.fetch.cache.llm.fallback", "false"));

        try {
            config.emaAlpha = Double.parseDouble(
                    properties.getOrDefault("smart.input.fetch.ema.alpha", "0.1"));
            if (config.emaAlpha <= 0.0 || config.emaAlpha > 1.0) {
                config.emaAlpha = 0.1;
            }
        } catch (NumberFormatException ignored) {
            config.emaAlpha = 0.1;
        }
        try {
            config.decayDays = Integer.parseInt(
                    properties.getOrDefault("smart.input.fetch.decay.days", "30"));
            if (config.decayDays <= 0) config.decayDays = 30;
        } catch (NumberFormatException ignored) {
            config.decayDays = 30;
        }
        try {
            config.diverseTargetCount = Integer.parseInt(
                    properties.getOrDefault("smart.input.fetch.diverse.target.count", "10"));
            if (config.diverseTargetCount <= 0) config.diverseTargetCount = 10;
        } catch (NumberFormatException ignored) {
            config.diverseTargetCount = 10;
        }

        // Authentication settings
        // Fresh-review Finding F27: do not ship a default admin password. Operators must
        // set {@code auth.admin.password} (and username) via properties; missing values
        // make {@link SmartFetchAuthManager#isConfigured()} return false → smart-fetch
        // proceeds without auth instead of using a guessed credential.
        config.authAdminUsername = properties.getOrDefault("auth.admin.username", "");
        config.authAdminPassword = properties.getOrDefault("auth.admin.password", "");

        // Bug audit Finding #11: configurable login plumbing.
        config.authLoginPath = properties.getOrDefault("auth.login.path", "/api/v1/users/login");
        config.authLoginUsernameField = properties.getOrDefault("auth.login.username.field", "username");
        config.authLoginPasswordField = properties.getOrDefault("auth.login.password.field", "password");
        config.authTokenJsonPath = properties.getOrDefault("auth.token.json.path", "data.token");
        try {
            config.authTokenValidityMinutes = Integer.parseInt(
                    properties.getOrDefault("auth.token.validity.minutes", "30"));
        } catch (NumberFormatException ignored) {
            config.authTokenValidityMinutes = 30;
        }

        return config;
    }
    
    // Getters and setters
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    
    public double getSmartFetchPercentage() { return smartFetchPercentage; }
    public void setSmartFetchPercentage(double smartFetchPercentage) { 
        this.smartFetchPercentage = smartFetchPercentage; 
    }
    
    public String getRegistryPath() { return registryPath; }
    public void setRegistryPath(String registryPath) { this.registryPath = registryPath; }
    
    public String getOpenApiSpecPath() { return openApiSpecPath; }
    public void setOpenApiSpecPath(String openApiSpecPath) { this.openApiSpecPath = openApiSpecPath; }
    
    public boolean isLlmDiscoveryEnabled() { return llmDiscoveryEnabled; }
    public void setLlmDiscoveryEnabled(boolean llmDiscoveryEnabled) {
        this.llmDiscoveryEnabled = llmDiscoveryEnabled;
    }

    public boolean isLlmEndpointSelectionEnabled() { return llmEndpointSelectionEnabled; }
    public void setLlmEndpointSelectionEnabled(boolean llmEndpointSelectionEnabled) {
        this.llmEndpointSelectionEnabled = llmEndpointSelectionEnabled;
    }
    
    public int getMaxCandidates() { return maxCandidates; }
    public void setMaxCandidates(int maxCandidates) { this.maxCandidates = maxCandidates; }
    
    public boolean isDependencyResolutionEnabled() { return dependencyResolutionEnabled; }
    public void setDependencyResolutionEnabled(boolean dependencyResolutionEnabled) { 
        this.dependencyResolutionEnabled = dependencyResolutionEnabled; 
    }
    
    public long getDiscoveryTimeoutMs() { return discoveryTimeoutMs; }
    public void setDiscoveryTimeoutMs(long discoveryTimeoutMs) {
        this.discoveryTimeoutMs = discoveryTimeoutMs;
    }

    public long getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(long connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }

    public long getReadTimeoutMs() { return readTimeoutMs; }
    public void setReadTimeoutMs(long readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }
    
    public boolean isCacheEnabled() { return cacheEnabled; }
    public void setCacheEnabled(boolean cacheEnabled) { this.cacheEnabled = cacheEnabled; }
    
    public int getCacheTtlSeconds() { return cacheTtlSeconds; }
    public void setCacheTtlSeconds(int cacheTtlSeconds) { this.cacheTtlSeconds = cacheTtlSeconds; }
    
    public int getDefaultPriority() { return defaultPriority; }
    public void setDefaultPriority(int defaultPriority) { this.defaultPriority = defaultPriority; }
    
    public int getPatternDiscoveryPriority() { return patternDiscoveryPriority; }
    public void setPatternDiscoveryPriority(int patternDiscoveryPriority) { 
        this.patternDiscoveryPriority = patternDiscoveryPriority; 
    }
    
    public int getLlmDiscoveryPriority() { return llmDiscoveryPriority; }
    public void setLlmDiscoveryPriority(int llmDiscoveryPriority) { 
        this.llmDiscoveryPriority = llmDiscoveryPriority; 
    }
    
    public String getDefaultContentType() { return defaultContentType; }
    public void setDefaultContentType(String defaultContentType) { 
        this.defaultContentType = defaultContentType; 
    }
    
    public int getSuccessResponseCode() { return successResponseCode; }
    public void setSuccessResponseCode(int successResponseCode) { 
        this.successResponseCode = successResponseCode; 
    }
    
    public long getSchemaDiscoveryTimeoutMs() { return schemaDiscoveryTimeoutMs; }
    public void setSchemaDiscoveryTimeoutMs(long schemaDiscoveryTimeoutMs) {
        this.schemaDiscoveryTimeoutMs = schemaDiscoveryTimeoutMs;
    }

    public int getMaxPromptChars() { return maxPromptChars; }
    public void setMaxPromptChars(int maxPromptChars) { this.maxPromptChars = maxPromptChars; }

    public boolean isCacheLlmFallbackValues() { return cacheLlmFallbackValues; }
    public void setCacheLlmFallbackValues(boolean v) { this.cacheLlmFallbackValues = v; }

    public double getEmaAlpha() { return emaAlpha; }
    public void setEmaAlpha(double emaAlpha) {
        if (emaAlpha > 0.0 && emaAlpha <= 1.0) this.emaAlpha = emaAlpha;
    }

    public int getDecayDays() { return decayDays; }
    public void setDecayDays(int decayDays) {
        if (decayDays > 0) this.decayDays = decayDays;
    }

    public int getDiverseTargetCount() { return diverseTargetCount; }
    public void setDiverseTargetCount(int v) { if (v > 0) this.diverseTargetCount = v; }

    public String getAuthAdminUsername() { return authAdminUsername; }
    public void setAuthAdminUsername(String authAdminUsername) { this.authAdminUsername = authAdminUsername; }

    public String getAuthAdminPassword() { return authAdminPassword; }
    public void setAuthAdminPassword(String authAdminPassword) { this.authAdminPassword = authAdminPassword; }
    // getAuthUserUsername/getAuthUserPassword removed (Bug audit Finding #12).

    // Bug audit Finding #11: configurable login plumbing so {@code SmartFetchAuthManager}
    // is no longer hardcoded to TrainTicket. Defaults preserve current TrainTicket behavior.
    private String authLoginPath = "/api/v1/users/login";
    private String authLoginUsernameField = "username";
    private String authLoginPasswordField = "password";
    private String authTokenJsonPath = "data.token";
    private int authTokenValidityMinutes = 30;

    public String getAuthLoginPath() { return authLoginPath; }
    public void setAuthLoginPath(String v) { this.authLoginPath = v; }
    public String getAuthLoginUsernameField() { return authLoginUsernameField; }
    public void setAuthLoginUsernameField(String v) { this.authLoginUsernameField = v; }
    public String getAuthLoginPasswordField() { return authLoginPasswordField; }
    public void setAuthLoginPasswordField(String v) { this.authLoginPasswordField = v; }
    public String getAuthTokenJsonPath() { return authTokenJsonPath; }
    public void setAuthTokenJsonPath(String v) { this.authTokenJsonPath = v; }
    public int getAuthTokenValidityMinutes() { return authTokenValidityMinutes; }
    public void setAuthTokenValidityMinutes(int v) { this.authTokenValidityMinutes = v; }
    
    @Override
    public String toString() {
        return String.format(
            "SmartInputFetchConfig{enabled=%s, smartFetchPercentage=%.2f, registryPath='%s', " +
            "openApiSpecPath='%s', llmDiscoveryEnabled=%s, maxCandidates=%d, dependencyResolutionEnabled=%s, " +
            "discoveryTimeoutMs=%d, cacheEnabled=%s, cacheTtlSeconds=%d}",
            enabled, smartFetchPercentage, registryPath, openApiSpecPath, llmDiscoveryEnabled, maxCandidates,
            dependencyResolutionEnabled, discoveryTimeoutMs, cacheEnabled, cacheTtlSeconds
        );
    }
}