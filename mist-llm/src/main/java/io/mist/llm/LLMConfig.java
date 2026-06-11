package io.mist.llm;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Configuration class for LLM settings supporting OpenAI-compatible HTTP
 * endpoints (DeepSeek, OpenAI, OpenRouter, Together, Groq, ...), Google Gemini,
 * and Ollama.
 *
 * <p>The {@code OPENAI_COMPATIBLE} backend covers any provider that implements
 * OpenAI's {@code /v1/chat/completions} request/response shape — it is NOT
 * limited to OpenAI itself. The default test target in this repo is DeepSeek;
 * the same code path also drives self-hosted OpenAI shims like {@code gpt4all}
 * or {@code llama.cpp --api}. Authentication is by optional bearer token
 * ({@code llm.openai_compatible.api.key}), which may be empty for
 * unauthenticated self-hosted servers.
 *
 * <p>Historical note: prior versions called this backend {@code LOCAL} /
 * {@code llm.local.*} — a misnomer, because the typical deployment is a
 * REMOTE hosted API authenticated with a bearer token. Legacy
 * {@code llm.local.*} keys and {@code llm.model.type=local} are still accepted
 * as deprecated aliases (see {@link #fromProperties}) so existing configs keep
 * working; a one-line warning is logged on first use.
 *
 * <p><b>Treat instances returned by {@link #fromProperties} as immutable.</b> They
 * are shared across callers via {@link #CONFIG_CACHE}, so mutating one (via the
 * package-private setters retained for reflective/builder use) would pollute every
 * subsequent caller in the same JVM. The setters exist for legacy reasons; new
 * call sites should not invoke them.</p>
 */
public class LLMConfig {

    private static final Logger logger = LogManager.getLogger(LLMConfig.class);

    /**
     * Cache of previously-loaded configurations keyed by a snapshot of the
     * LLM-relevant entries from the supplied properties map. {@link #fromProperties}
     * is called once per scenario during MST runs (~526 times per run), and each
     * invocation re-reads identical properties, re-resolves the same {@code ${ENV_VAR}}
     * placeholders, and re-emits the same 3 INFO log lines. Memoizing here keeps
     * the first-load logs (so operators can still verify configuration on startup)
     * while making subsequent identical loads silent and effectively free.
     *
     * <p>Keyed on a content-snapshot string rather than {@code Map} identity because
     * callers (e.g. {@code LLMService.getInstance}, {@code ZeroShotLLMGenerator})
     * construct a fresh {@link java.util.HashMap} from {@code System.getProperty(...)}
     * on every call, so identity-based caching would never hit.
     */
    private static final ConcurrentHashMap<String, LLMConfig> CONFIG_CACHE = new ConcurrentHashMap<>();

    public enum ModelType {
        /**
         * Any OpenAI-compatible chat-completions HTTP endpoint: DeepSeek,
         * OpenAI, OpenRouter, Together, Groq, gpt4all, llama.cpp, ...
         */
        OPENAI_COMPATIBLE,
        GEMINI,
        OLLAMA
    }

    // General LLM settings
    private boolean enabled;
    private ModelType modelType;

    // OpenAI-compatible HTTP endpoint settings.
    // The {@code url} is the full chat-completions endpoint; the bearer token
    // ({@code apiKey}) is optional (empty for unauthenticated self-hosted
    // servers).
    private boolean openaiCompatibleEnabled;
    private String openaiCompatibleUrl;
    private String openaiCompatibleModel;
    /** Bearer token sent as {@code Authorization: Bearer <key>} when non-empty.
     *  Resolved from {@code ${ENV_VAR}} syntax in properties. */
    private String openaiCompatibleApiKey;

    // Gemini API settings
    private boolean geminiEnabled;
    private String geminiApiKey;
    private String geminiModel;
    private String geminiApiUrl;

    // Ollama API settings
    private boolean ollamaEnabled;
    private String ollamaUrl;
    private String ollamaModel;

    // Rate limiting settings
    private int maxRetries;
    private boolean rateLimitRetryEnabled;

    // Default constructor with sensible defaults
    public LLMConfig() {
        this.enabled = true;
        this.modelType = ModelType.OPENAI_COMPATIBLE;
        this.openaiCompatibleEnabled = true;
        this.openaiCompatibleUrl = "http://localhost:4891/v1/chat/completions";
        this.openaiCompatibleModel = "llama-3-8b-instruct";
        this.openaiCompatibleApiKey = "";
        this.geminiEnabled = false;
        this.geminiApiKey = "";
        this.geminiModel = "gemini-2.0-flash-exp";
        this.geminiApiUrl = "https://generativelanguage.googleapis.com/v1beta/models";
        this.ollamaEnabled = false;
        this.ollamaUrl = "http://localhost:11434";
        this.ollamaModel = "gemma3:4b";
        this.maxRetries = 3;
        this.rateLimitRetryEnabled = true;
    }

    /**
     * Create LLMConfig from properties map.
     *
     * <p>Accepts the canonical {@code llm.openai_compatible.*} keys and the
     * legacy {@code llm.local.*} keys (deprecated alias). When the canonical
     * key is missing, the legacy key is read as a fallback and a one-time
     * deprecation warning is logged.
     *
     * <p>Memoized: the first call with a given set of LLM-relevant property values
     * runs the full load (resolving {@code ${ENV_VAR}} placeholders, emitting INFO
     * logs about key resolution and final model selection). Subsequent calls with
     * the same property values return the cached instance silently — this is what
     * keeps a 526-scenario run from producing 1,578 redundant INFO log lines.
     */
    public static LLMConfig fromProperties(Map<String, String> properties) {
        String cacheKey = buildCacheKey(properties);
        LLMConfig cached = CONFIG_CACHE.get(cacheKey);
        if (cached != null) {
            logger.debug("LLMConfig cache hit (key hash={})", cacheKey.hashCode());
            return cached;
        }
        // computeIfAbsent ensures only one thread runs the load + logs for a given
        // key even under contention; later callers wait briefly and then reuse the
        // same instance.
        return CONFIG_CACHE.computeIfAbsent(cacheKey, k -> loadFromProperties(properties));
    }

    /**
     * Build a stable cache key from only the LLM-relevant properties. Listing the
     * keys explicitly (rather than hashing the whole map) means unrelated entries
     * like {@code base.url} or per-scenario settings don't cause false cache misses,
     * and that callers using a {@link java.util.HashMap} (unordered iteration) still
     * hit the cache.
     *
     * <p><b>Maintenance:</b> if you add a new {@code llm.*} property that
     * {@link #loadFromProperties} reads, add it here too — otherwise the cache will
     * silently mask its effect. The key uses the raw property text (including any
     * {@code ${VAR}} placeholders); env-var resolution is captured into the cached
     * instance once and assumed stable for the JVM's lifetime.</p>
     */
    private static String buildCacheKey(Map<String, String> properties) {
        if (properties == null) {
            return "<null>";
        }
        String[] keys = {
            "llm.enabled", "llm.model.type",
            "llm.local.enabled", "llm.local.url", "llm.local.model", "llm.local.api.key",
            "llm.gemini.enabled", "llm.gemini.api.key", "llm.gemini.model", "llm.gemini.api.url",
            "llm.ollama.enabled", "llm.ollama.url", "llm.ollama.model",
            "llm.rate.limit.max.retries", "llm.rate.limit.retry.enabled"
        };
        StringBuilder sb = new StringBuilder(256);
        for (String key : keys) {
            sb.append(key).append('=').append(properties.getOrDefault(key, "")).append('|');
        }
        return sb.toString();
    }

    /**
     * Actual load logic. Pulled out of {@link #fromProperties} so that the cache
     * can sit in front of it. Keeps all original INFO log lines so first-load
     * diagnostics are unchanged.
     */
    private static LLMConfig loadFromProperties(Map<String, String> properties) {
        LLMConfig config = new LLMConfig();

        config.enabled = Boolean.parseBoolean(
            properties.getOrDefault("llm.enabled", "true"));

        String modelTypeStr = properties.getOrDefault(
                "llm.model.type", "openai_compatible").toLowerCase();
        switch (modelTypeStr) {
            case "gemini":
                config.modelType = ModelType.GEMINI;
                break;
            case "ollama":
                config.modelType = ModelType.OLLAMA;
                break;
            case "local":
                logger.warn("llm.model.type=local is a deprecated alias for "
                        + "'openai_compatible'; please update your *.properties "
                        + "files. The old name was misleading because this "
                        + "backend is typically a REMOTE hosted API "
                        + "(DeepSeek/OpenAI/OpenRouter/...), not a local model.");
                config.modelType = ModelType.OPENAI_COMPATIBLE;
                break;
            case "openai":
                // Short alias accepted for ergonomics, but not the canonical name —
                // we deliberately avoid 'openai' alone because this backend works
                // with DeepSeek, OpenRouter, Together, and any other provider that
                // speaks the OpenAI chat-completions HTTP shape, not just OpenAI.
                config.modelType = ModelType.OPENAI_COMPATIBLE;
                break;
            case "openai_compatible":
            case "openai-compatible":
            default:
                config.modelType = ModelType.OPENAI_COMPATIBLE;
                break;
        }

        // OpenAI-compatible endpoint settings.
        // Read the canonical llm.openai_compatible.* keys first, fall back to
        // legacy llm.local.* when the canonical key is absent so existing
        // configs keep working.
        config.openaiCompatibleEnabled = Boolean.parseBoolean(
            firstDefined(properties, "llm.openai_compatible.enabled", "llm.local.enabled", "true"));
        config.openaiCompatibleUrl = firstDefined(properties,
            "llm.openai_compatible.url", "llm.local.url", "http://localhost:4891/v1/chat/completions");
        config.openaiCompatibleModel = firstDefined(properties,
            "llm.openai_compatible.model", "llm.local.model", "llama-3-8b-instruct");
        // Resolve ${ENV_VAR} in api.key so the secret never has to be committed
        // to the .properties file. Falls back to the literal value when no
        // ${...} placeholder is used.
        String rawApiKey = firstDefined(properties,
            "llm.openai_compatible.api.key", "llm.local.api.key", "");
        config.openaiCompatibleApiKey = resolveEnvPlaceholder(rawApiKey);
        warnIfLegacyOpenaiCompatibleKeysUsed(properties);
        // Diagnostic: surface key-resolution health (length only, never the value)
        // so production failures are debuggable without leaking the secret.
        if (rawApiKey != null && !rawApiKey.isEmpty()) {
            boolean isPlaceholder = rawApiKey.trim().startsWith("${")
                                    && rawApiKey.trim().endsWith("}");
            int resolvedLen = config.openaiCompatibleApiKey == null ? 0 : config.openaiCompatibleApiKey.length();
            String source;
            if (!isPlaceholder) {
                source = "literal";
            } else if (resolvedLen == 0) {
                source = "PLACEHOLDER UNRESOLVED — env/sys/file all empty";
            } else {
                source = "resolved";
            }
            logger.info("LLM openai_compatible api.key: raw='{}' (placeholder={}), resolvedLength={}, source={}",
                    isPlaceholder ? rawApiKey.trim() : "<literal>", isPlaceholder, resolvedLen, source);
        }

        // Gemini API settings
        config.geminiEnabled = Boolean.parseBoolean(
            properties.getOrDefault("llm.gemini.enabled", "false"));
        // Same ${VAR} resolution as the openai_compatible key, so gemini
        // secrets can stay out of the .properties file too.
        config.geminiApiKey = resolveEnvPlaceholder(
            properties.getOrDefault("llm.gemini.api.key", ""));
        config.geminiModel = properties.getOrDefault(
            "llm.gemini.model", "gemini-2.0-flash-exp");
        config.geminiApiUrl = properties.getOrDefault(
            "llm.gemini.api.url", "https://generativelanguage.googleapis.com/v1beta/models");

        // Ollama API settings
        config.ollamaEnabled = Boolean.parseBoolean(
            properties.getOrDefault("llm.ollama.enabled", "false"));
        config.ollamaUrl = properties.getOrDefault(
            "llm.ollama.url", "http://localhost:11434");
        config.ollamaModel = properties.getOrDefault(
            "llm.ollama.model", "gemma3:4b");

        // Rate limiting settings
        config.maxRetries = Integer.parseInt(
            properties.getOrDefault("llm.rate.limit.max.retries", "3"));
        config.rateLimitRetryEnabled = Boolean.parseBoolean(
            properties.getOrDefault("llm.rate.limit.retry.enabled", "true"));

        // Auto-correct modelType if selected backend is disabled but another is enabled
        // This makes configuration resilient when a launcher overrides llm.model.type inconsistently
        ModelType originalType = config.modelType;
        if (!config.isValid()) {
            // Prefer OPENAI_COMPATIBLE if enabled
            if (config.openaiCompatibleEnabled && config.openaiCompatibleUrl != null && !config.openaiCompatibleUrl.trim().isEmpty()) {
                config.modelType = ModelType.OPENAI_COMPATIBLE;
            } else if (config.ollamaEnabled && config.ollamaUrl != null && !config.ollamaUrl.trim().isEmpty()) {
                config.modelType = ModelType.OLLAMA;
            } else if (config.geminiEnabled && config.geminiApiKey != null && !config.geminiApiKey.trim().isEmpty()) {
                config.modelType = ModelType.GEMINI;
            }
        }

        if (originalType != config.modelType) {
            logger.warn("LLMConfig: Overriding modelType {} -> {} based on enabled providers to ensure a valid configuration", originalType, config.modelType);
        }

        logger.info("LLMConfig initialized: enabled={}, modelType={}, openaiCompatibleEnabled={}, geminiEnabled={}, ollamaEnabled={}, maxRetries={}, rateLimitRetryEnabled={}",
                   config.enabled, config.modelType, config.openaiCompatibleEnabled, config.geminiEnabled, config.ollamaEnabled, config.maxRetries, config.rateLimitRetryEnabled);

        // Automatically ensure only the selected model type is enabled
        config.enforceModelTypeConsistency();

        return config;
    }

    /**
     * Ensure only the selected model type is enabled, disable others
     */
    private void enforceModelTypeConsistency() {
        switch (this.modelType) {
            case OPENAI_COMPATIBLE:
                this.openaiCompatibleEnabled = true;
                this.geminiEnabled = false;
                this.ollamaEnabled = false;
                logger.info("Enforcing OPENAI_COMPATIBLE model configuration - disabled Gemini and Ollama");
                break;
            case GEMINI:
                this.openaiCompatibleEnabled = false;
                this.geminiEnabled = true;
                this.ollamaEnabled = false;
                logger.info("Enforcing GEMINI model configuration - disabled OpenAI-compatible and Ollama");
                break;
            case OLLAMA:
                this.openaiCompatibleEnabled = false;
                this.geminiEnabled = false;
                this.ollamaEnabled = true;
                logger.info("Enforcing OLLAMA model configuration - disabled OpenAI-compatible and Gemini");
                break;
        }
    }

    /**
     * Validate the configuration
     */
    public boolean isValid() {
        if (!enabled) {
            return true; // Valid to be disabled
        }

        switch (modelType) {
            case OPENAI_COMPATIBLE:
                return openaiCompatibleEnabled && openaiCompatibleUrl != null && !openaiCompatibleUrl.trim().isEmpty()
                       && openaiCompatibleModel != null && !openaiCompatibleModel.trim().isEmpty();
            case GEMINI:
                return geminiEnabled && geminiApiKey != null && !geminiApiKey.trim().isEmpty()
                       && geminiModel != null && !geminiModel.trim().isEmpty()
                       && geminiApiUrl != null && !geminiApiUrl.trim().isEmpty();
            case OLLAMA:
                return ollamaEnabled && ollamaUrl != null && !ollamaUrl.trim().isEmpty()
                       && ollamaModel != null && !ollamaModel.trim().isEmpty();
            default:
                return false;
        }
    }

    // Getters and setters
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public ModelType getModelType() { return modelType; }
    public void setModelType(ModelType modelType) { this.modelType = modelType; }

    public boolean isOpenaiCompatibleEnabled() { return openaiCompatibleEnabled; }
    public void setOpenaiCompatibleEnabled(boolean openaiCompatibleEnabled) { this.openaiCompatibleEnabled = openaiCompatibleEnabled; }

    public String getOpenaiCompatibleUrl() { return openaiCompatibleUrl; }
    public void setOpenaiCompatibleUrl(String openaiCompatibleUrl) { this.openaiCompatibleUrl = openaiCompatibleUrl; }

    public String getOpenaiCompatibleModel() { return openaiCompatibleModel; }
    public void setOpenaiCompatibleModel(String openaiCompatibleModel) { this.openaiCompatibleModel = openaiCompatibleModel; }

    public String getOpenaiCompatibleApiKey() { return openaiCompatibleApiKey; }
    public void setOpenaiCompatibleApiKey(String openaiCompatibleApiKey) { this.openaiCompatibleApiKey = openaiCompatibleApiKey; }

    /**
     * Return the first non-null/non-empty value for {@code newKey} or
     * {@code legacyKey}, falling back to {@code defaultValue}. Used so that
     * legacy {@code llm.local.*} keys keep working after the rename to
     * {@code llm.openai_compatible.*}.
     */
    private static String firstDefined(Map<String, String> properties,
                                       String newKey, String legacyKey,
                                       String defaultValue) {
        String fromNew = properties.get(newKey);
        if (fromNew != null && !fromNew.isEmpty()) {
            return fromNew;
        }
        String fromLegacy = properties.get(legacyKey);
        if (fromLegacy != null && !fromLegacy.isEmpty()) {
            return fromLegacy;
        }
        return defaultValue;
    }

    /**
     * Log a single deprecation warning if any legacy {@code llm.local.*} key
     * is present and the canonical {@code llm.openai_compatible.*} equivalent
     * is not.
     */
    private static void warnIfLegacyOpenaiCompatibleKeysUsed(Map<String, String> properties) {
        String[][] pairs = new String[][] {
            {"llm.openai_compatible.enabled", "llm.local.enabled"},
            {"llm.openai_compatible.url",     "llm.local.url"},
            {"llm.openai_compatible.model",   "llm.local.model"},
            {"llm.openai_compatible.api.key", "llm.local.api.key"},
        };
        for (String[] pair : pairs) {
            String newVal = properties.get(pair[0]);
            String legacyVal = properties.get(pair[1]);
            if ((newVal == null || newVal.isEmpty())
                    && legacyVal != null && !legacyVal.isEmpty()) {
                logger.warn("Legacy LLM property keys detected (llm.local.*). "
                        + "Please rename to llm.openai_compatible.* in your "
                        + "*.properties files. The new prefix reflects that this "
                        + "backend is an OpenAI-compatible HTTP endpoint "
                        + "(DeepSeek, OpenAI, OpenRouter, Together, ...), not a "
                        + "local model.");
                return;
            }
        }
    }

    /**
     * Resolve a property value that may be a literal, an environment-variable
     * reference of the form {@code ${VAR}}, or {@code ${VAR:default}}.
     *
     * <p>Resolution order for placeholders:
     * <ol>
     *   <li>{@code System.getenv(VAR)}</li>
     *   <li>{@code System.getProperty(VAR)} (handy for IDE run configs that
     *       inject {@code -DVAR=...})</li>
     *   <li>File at {@code .api_keys/VAR} relative to the current working
     *       directory (gitignored — survives IDE env-var injection issues)</li>
     *   <li>Literal default after {@code :} in {@code ${VAR:default}}</li>
     * </ol>
     *
     * <p>An empty or {@code null} input returns {@code ""}. A reference with
     * no matching value anywhere returns {@code ""} so that a missing key
     * causes the auth header to be omitted instead of literally sending the
     * placeholder text.
     */
    static String resolveEnvPlaceholder(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String trimmed = value.trim();
        if (!(trimmed.startsWith("${") && trimmed.endsWith("}"))) {
            return trimmed;
        }
        String inside = trimmed.substring(2, trimmed.length() - 1);
        String varName = inside;
        String defaultVal = "";
        int colon = inside.indexOf(':');
        if (colon >= 0) {
            varName = inside.substring(0, colon);
            defaultVal = inside.substring(colon + 1);
        }
        String fromEnv = System.getenv(varName);
        if (fromEnv != null && !fromEnv.isEmpty()) {
            return fromEnv;
        }
        String fromSys = System.getProperty(varName);
        if (fromSys != null && !fromSys.isEmpty()) {
            return fromSys;
        }
        String fromFile = readApiKeyFile(varName);
        if (fromFile != null && !fromFile.isEmpty()) {
            return fromFile;
        }
        return defaultVal;
    }

    /**
     * Read a single secret value from {@code .api_keys/<varName>}.
     * <p>Tries, in order:
     * <ol>
     *   <li>Current working directory ({@code .api_keys/<VAR>}) — the IntelliJ
     *       default CWD for Application run configs.</li>
     *   <li>{@code System.getProperty("user.dir")} — same path, resolved via
     *       the user-dir property in case the JVM CWD differs.</li>
     *   <li>{@code System.getProperty("user.home")/.restest/api_keys/<VAR>} —
     *       a user-wide location for users who want one key shared across
     *       multiple checkouts.</li>
     * </ol>
     * The file should contain only the secret (trailing whitespace/newlines
     * are stripped). Returns {@code null} if no candidate file exists. The
     * {@code .api_keys/} directory is gitignored.
     */
    private static String readApiKeyFile(String varName) {
        if (varName == null || varName.isEmpty()) {
            return null;
        }
        java.nio.file.Path[] candidates = new java.nio.file.Path[] {
            java.nio.file.Paths.get(".api_keys", varName),
            java.nio.file.Paths.get(
                    System.getProperty("user.dir", "."), ".api_keys", varName),
            java.nio.file.Paths.get(
                    System.getProperty("user.home", "."), ".restest", "api_keys", varName),
        };
        for (java.nio.file.Path p : candidates) {
            try {
                if (java.nio.file.Files.isRegularFile(p)) {
                    String content = new String(java.nio.file.Files.readAllBytes(p),
                            java.nio.charset.StandardCharsets.UTF_8);
                    String trimmed = content.trim();
                    if (!trimmed.isEmpty()) {
                        return trimmed;
                    }
                }
            } catch (java.io.IOException | SecurityException e) {
                // Try the next candidate.
            }
        }
        return null;
    }

    public boolean isGeminiEnabled() { return geminiEnabled; }
    public void setGeminiEnabled(boolean geminiEnabled) { this.geminiEnabled = geminiEnabled; }

    public String getGeminiApiKey() { return geminiApiKey; }
    public void setGeminiApiKey(String geminiApiKey) { this.geminiApiKey = geminiApiKey; }

    public String getGeminiModel() { return geminiModel; }
    public void setGeminiModel(String geminiModel) { this.geminiModel = geminiModel; }

    public String getGeminiApiUrl() { return geminiApiUrl; }
    public void setGeminiApiUrl(String geminiApiUrl) { this.geminiApiUrl = geminiApiUrl; }

    public boolean isOllamaEnabled() { return ollamaEnabled; }
    public void setOllamaEnabled(boolean ollamaEnabled) { this.ollamaEnabled = ollamaEnabled; }

    public String getOllamaUrl() { return ollamaUrl; }
    public void setOllamaUrl(String ollamaUrl) { this.ollamaUrl = ollamaUrl; }

    public String getOllamaModel() { return ollamaModel; }
    public void setOllamaModel(String ollamaModel) { this.ollamaModel = ollamaModel; }

    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }

    public boolean isRateLimitRetryEnabled() { return rateLimitRetryEnabled; }
    public void setRateLimitRetryEnabled(boolean rateLimitRetryEnabled) { this.rateLimitRetryEnabled = rateLimitRetryEnabled; }

    /**
     * Seed-gated temperature override. When {@code -Drandom.seed} is set
     * (regardless of value), returns {@code 0.0} to force greedy decoding so
     * cached responses replay byte-deterministically. The gate intentionally
     * fires on "set" rather than "parseable" — a non-numeric seed is still a
     * deliberate determinism request. Numeric forwarding to backends is
     * handled separately by {@code io.mist.core.util.SeededRandom} (in
     * mist-restest-adapter) for adapter callers, and by
     * {@link LLMService}'s internal {@code configuredBaseSeed()} helper for
     * mist-llm-direct callers.
     */
    public static double applySeedGate(double configuredTemperature) {
        return System.getProperty("random.seed") != null ? 0.0 : configuredTemperature;
    }

    @Override
    public String toString() {
        return String.format(
            "LLMConfig{enabled=%s, modelType=%s, openaiCompatibleEnabled=%s, openaiCompatibleUrl='%s', openaiCompatibleModel='%s', " +
            "geminiEnabled=%s, geminiModel='%s', geminiApiUrl='%s', ollamaEnabled=%s, ollamaUrl='%s', ollamaModel='%s', " +
            "maxRetries=%d, rateLimitRetryEnabled=%s}",
            enabled, modelType, openaiCompatibleEnabled, openaiCompatibleUrl, openaiCompatibleModel,
            geminiEnabled, geminiModel, geminiApiUrl, ollamaEnabled, ollamaUrl, ollamaModel,
            maxRetries, rateLimitRetryEnabled
        );
    }
}
