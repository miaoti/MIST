package io.mist.llm;

import okhttp3.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.concurrent.TimeUnit;

/**
 * Unified LLM service that routes requests to an OpenAI-compatible HTTP
 * endpoint (DeepSeek, OpenAI, OpenRouter, Together, ..., or a self-hosted
 * OpenAI shim like gpt4all), Google Gemini, or Ollama, based on configuration.
 */
public class LLMService implements LLMClient {
    
    private static final Logger logger = LogManager.getLogger(LLMService.class);
    
    private final LLMConfig config;
    private final GeminiApiClient geminiClient;
    private final OllamaApiClient ollamaClient;
    private final OkHttpClient httpClient;
    private final OkHttpClient hostedHttpClient;
    private final LLMCommunicationSink communicationSink;

    // Watchdog scheduler for hosted-endpoint deadlines — defense in depth on
    // top of OkHttp's own timeouts (see hostedHttpClient construction note).
    private static final java.util.concurrent.ScheduledExecutorService WATCHDOG =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "LLMService-watchdog");
                t.setDaemon(true);
                return t;
            });

    // Singleton instance
    private static LLMService instance;
    
    private LLMService(LLMConfig config) {
        this(config, new Properties());
    }

    private LLMService(LLMConfig config, Properties properties) {
        this.config = config;

        // Initialize communication logger with provided properties
        Properties loggerProps = new Properties();
        // Copy LLM communication logging AND resource monitoring properties
        for (String key : properties.stringPropertyNames()) {
            if (key.startsWith("llm.communication.logging.") || key.startsWith("llm.resource.monitoring.")) {
                loggerProps.setProperty(key, properties.getProperty(key));
            }
        }
        // Set default if not specified
        if (!loggerProps.containsKey("llm.communication.logging.enabled")) {
            loggerProps.setProperty("llm.communication.logging.enabled", "true");
        }
        this.communicationSink = loadCommunicationSink();
        this.communicationSink.init(loggerProps);
        
        // Initialize Gemini client if needed
        if (config.getModelType() == LLMConfig.ModelType.GEMINI && config.isGeminiEnabled()) {
            this.geminiClient = new GeminiApiClient(
                config.getGeminiApiKey(),
                config.getGeminiModel(),
                config.getGeminiApiUrl(),
                config.getMaxRetries(),
                config.isRateLimitRetryEnabled()
            );
        } else {
            this.geminiClient = null;
        }

        // Initialize Ollama client whenever URL+model are configured, even when
        // the active modelType is OPENAI_COMPATIBLE or GEMINI. This makes Ollama
        // available as a per-call fallback when the primary backend (e.g. a
        // flaky DeepSeek HTTP/2 stream) times out or returns null.
        // enforceModelTypeConsistency sets ollamaEnabled=false for non-Ollama
        // primaries; that flag governs routing, not whether the client exists.
        boolean ollamaConfigured = config.getOllamaUrl() != null
                && !config.getOllamaUrl().trim().isEmpty()
                && config.getOllamaModel() != null
                && !config.getOllamaModel().trim().isEmpty();
        if (ollamaConfigured) {
            this.ollamaClient = new OllamaApiClient(
                config.getOllamaUrl(),
                config.getOllamaModel(),
                config.getMaxRetries(),
                config.isRateLimitRetryEnabled()
            );
        } else {
            this.ollamaClient = null;
        }
        
        // Initialize HTTP client for local model
        // Disable timeouts for long-running local generations per user requirement
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(0, TimeUnit.MILLISECONDS)
                .writeTimeout(0, TimeUnit.MILLISECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(true)
                .pingInterval(30, TimeUnit.SECONDS)
                .callTimeout(0, TimeUnit.MILLISECONDS)
                .build();

        // Separate, fully-independent HTTP client for hosted endpoints
        // (DeepSeek, OpenAI, ...). This MUST NOT share its ConnectionPool with
        // httpClient: OkHttp 4.10's HTTP/2 stream timeouts inherit from the
        // pooled connection's parent client. If we derive this client via
        // httpClient.newBuilder(), reused connections keep the zero readTimeout
        // and a stalled stream blocks forever (observed: 3+ minutes on a
        // DeepSeek HTTP/2 stream stall in 22:56 run).
        this.hostedHttpClient = new OkHttpClient.Builder()
                .connectionPool(new okhttp3.ConnectionPool(5, 5, TimeUnit.MINUTES))
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .callTimeout(180, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .pingInterval(30, TimeUnit.SECONDS)
                .build();
        
        logger.info("LLMService initialized with model type: {}", config.getModelType());
    }
    
    /**
     * Get or create singleton instance
     */
    public static synchronized LLMService getInstance(LLMConfig config) {
        if (instance == null) {
            instance = new LLMService(config);
        }
        return instance;
    }

    /**
     * Get or create singleton instance with communication logging properties
     */
    public static synchronized LLMService getInstance(LLMConfig config, Properties properties) {
        if (instance == null) {
            instance = new LLMService(config, properties);
        }
        return instance;
    }

    /**
     * Get instance from properties map
     */
    public static synchronized LLMService getInstance(Map<String, String> properties) {
        LLMConfig config = LLMConfig.fromProperties(properties);

        // Convert Map to Properties for communication logger
        Properties props = new Properties();
        props.putAll(properties);

        return getInstance(config, props);
    }
    
    /**
     * Generate text using the configured LLM
     * @param systemPrompt System prompt for the model
     * @param userPrompt User prompt/question
     * @param maxTokens Maximum tokens to generate
     * @param temperature Temperature for generation (0.0 to 1.0)
     * @return Generated content or null if failed
     */
    public String generateText(String systemPrompt, String userPrompt, int maxTokens, double temperature) {
        if (!config.isEnabled()) {
            logger.warn("LLM is disabled in configuration");
            return null;
        }

        if (!config.isValid()) {
            logger.error("LLM configuration is invalid: {}", config);
            return null;
        }

        // Reproducibility: when a seed is configured the LLM is forced into
        // greedy decoding and prior cached responses short-circuit the backend
        // entirely. Unseeded runs still write through so future seeded runs
        // can replay them.
        double effectiveTemperature = LLMConfig.applySeedGate(temperature);

        String modelType = config.getModelType().toString();
        String modelName = getModelName();
        String endpoint = getEndpoint();
        Object metadata = createMetadata(maxTokens, effectiveTemperature);

        String backendName = config.getModelType().name();
        String cacheKey = LLMCallCache.key(modelType, modelName, backendName,
                systemPrompt, userPrompt, effectiveTemperature, maxTokens);
        LLMCallCache cache = LLMCallCache.getInstance();

        LLMCommunicationSink.RequestHandle context = communicationSink.logRequest(
            modelType, modelName, systemPrompt, userPrompt, endpoint, metadata);

        // Cache READ gate. Three configurable layers, in priority order:
        //   1. mist.llm.cache.read=true | false    explicit override
        //   2. -Drandom.seed=<n>                   seeded run ⇒ read
        //   3. (otherwise)                         never read (fresh LLM calls)
        // The MST .properties file's MstConfig.applyToSystemProperties copies
        // any mist.llm.cache.* key into System properties so this knob is
        // settable from the properties file as well as from -D.
        if (cacheReadEnabled()) {
            String hit = cache.get(cacheKey);
            if (hit != null) {
                logger.info("LLMCallCache: read hit for key {} (backend {})",
                        abbreviateKey(cacheKey), backendName);
                communicationSink.logResponse(context, hit, true, null);
                return hit;
            }
        }

        long startTime = System.currentTimeMillis();
        String result = null;
        boolean success = false;
        String errorMessage = null;
        // True when the result was produced by the Ollama per-call fallback rather
        // than the configured primary backend. Such a result must NOT be written to
        // the cache under the primary's key, or a later seeded reproduction would
        // replay the fallback model's answer as if it were the primary's.
        boolean servedByFallback = false;

        try {
            switch (config.getModelType()) {
                case GEMINI:
                    result = generateWithGemini(systemPrompt, userPrompt, maxTokens, effectiveTemperature);
                    break;
                case OPENAI_COMPATIBLE:
                    result = generateWithOpenAICompatible(systemPrompt, userPrompt, maxTokens, effectiveTemperature);
                    break;
                case OLLAMA:
                    result = generateWithOllama(systemPrompt, userPrompt, maxTokens, effectiveTemperature);
                    break;
                default:
                    errorMessage = "Unknown model type: " + config.getModelType();
                    logger.error(errorMessage);
                    break;
            }

            // Per-call fallback to a local Ollama model when the hosted primary
            // (OPENAI_COMPATIBLE → DeepSeek/OpenAI/etc., or GEMINI) returns null
            // because of a timeout, 401, parse failure, etc. The fallback is
            // *per call only* — the next call routes to the primary again so a
            // transient hosted-API stall doesn't permanently demote the run.
            // If Ollama also fails, return null and let the upstream caller
            // (SmartInputFetcher / ZeroShotLLMGenerator) hit its placeholder
            // fallback as before.
            if (result == null
                    && ollamaClient != null
                    && config.getModelType() != LLMConfig.ModelType.OLLAMA) {
                logger.warn("[LLM] Primary {} returned null — falling back to Ollama for this call", config.getModelType());
                // effectiveTemperature keeps the seed gate active across the cascade.
                String fallback = generateWithOllama(systemPrompt, userPrompt, maxTokens, effectiveTemperature);
                if (fallback != null) {
                    result = fallback;
                    servedByFallback = true;
                    logger.info("[LLM] Ollama fallback succeeded ({} chars)", fallback.length());
                } else {
                    logger.warn("[LLM] Ollama fallback also returned null — caller will use its own fallback");
                }
            }

            success = (result != null);

        } catch (Exception e) {
            errorMessage = e.getMessage();
            logger.error("Error during LLM generation: {}", errorMessage, e);
            // Same per-call cascade on hard exceptions, e.g. SocketTimeoutException
            // bubbled out of OkHttp before generateWithOpenAICompatible could swallow it.
            if (result == null
                    && ollamaClient != null
                    && config.getModelType() != LLMConfig.ModelType.OLLAMA) {
                try {
                    logger.warn("[LLM] Primary threw '{}' — falling back to Ollama for this call", errorMessage);
                    result = generateWithOllama(systemPrompt, userPrompt, maxTokens, effectiveTemperature);
                    if (result != null) {
                        servedByFallback = true;
                        logger.info("[LLM] Ollama fallback succeeded after exception ({} chars)", result.length());
                    }
                } catch (Exception fbErr) {
                    logger.error("[LLM] Ollama fallback also threw '{}'", fbErr.getMessage());
                }
            }
        } finally {
            // Log the response
            communicationSink.logResponse(context, result, success, errorMessage);
        }

        // Cache WRITE gate. Default ON so unseeded runs feed the cache and a
        // future seeded run can replay them. Set mist.llm.cache.write=false
        // (in the MST .properties file or via -D) to suppress writes — useful
        // when you want a one-off "no-cache" benchmark without polluting the
        // shared cache file. Empty strings (content-filter refusals, etc.)
        // are skipped — caching those would prevent retries from ever
        // recovering.
        if (result != null && !result.isEmpty() && !servedByFallback && cacheWriteEnabled()) {
            cache.put(cacheKey, result);
            logger.debug("LLMCallCache: write hit for key {} (backend {})",
                    abbreviateKey(cacheKey), backendName);
        } else if (servedByFallback) {
            logger.debug("LLMCallCache: skipping write for key {} — result served by Ollama fallback, "
                    + "not the configured {} backend", abbreviateKey(cacheKey), backendName);
        }

        return result;
    }

    /**
     * Whether to read from {@link LLMCallCache} on this call. Reads
     * {@code mist.llm.cache.read} first (true|false|auto), then falls back
     * to the legacy "seeded run ⇒ read" gate keyed off
     * {@code -Drandom.seed}. The property can come from the MST
     * .properties file (every key is mirrored into System properties by
     * MstConfig.applyToSystemProperties) or from a {@code -D} flag.
     */
    static boolean cacheReadEnabled() {
        String explicit = System.getProperty("mist.llm.cache.read");
        if (explicit != null) {
            String v = explicit.trim().toLowerCase();
            if ("true".equals(v) || "on".equals(v) || "yes".equals(v)) return true;
            if ("false".equals(v) || "off".equals(v) || "no".equals(v)) return false;
            // "auto" or anything else falls through to the legacy behaviour
        }
        return System.getProperty("random.seed") != null;
    }

    /**
     * Whether to write the LLM response back to {@link LLMCallCache}.
     * Default ON. Set {@code mist.llm.cache.write=false} (in the MST
     * .properties file or via -D) to skip cache writes — useful for a
     * one-off no-cache benchmark that mustn't pollute the shared cache.
     */
    static boolean cacheWriteEnabled() {
        String prop = System.getProperty("mist.llm.cache.write");
        if (prop == null) return true;
        String v = prop.trim().toLowerCase();
        return !("false".equals(v) || "off".equals(v) || "no".equals(v));
    }

    private static final int LOG_KEY_PREFIX_LEN = 16;
    private static String abbreviateKey(String key) {
        return key.length() > LOG_KEY_PREFIX_LEN ? key.substring(0, LOG_KEY_PREFIX_LEN) : key;
    }

    /**
     * Convenience method with default parameters
     */
    public String generateText(String systemPrompt, String userPrompt) {
        return generateText(systemPrompt, userPrompt, 200, 0.7);
    }
    
    /**
     * Generate text using Gemini API
     */
    private String generateWithGemini(String systemPrompt, String userPrompt, int maxTokens, double temperature) {
        if (geminiClient == null) {
            logger.error("Gemini client not initialized");
            return null;
        }
        
        logger.debug("[LLMService] Using Gemini API for generation");
        return geminiClient.generateContent(systemPrompt, userPrompt, maxTokens, temperature);
    }
    
    /**
     * Generate text against an OpenAI-compatible chat-completions endpoint
     * (DeepSeek, OpenAI, OpenRouter, Together, ..., or a self-hosted OpenAI
     * shim like gpt4all or llama.cpp --api).
     */
    private String generateWithOpenAICompatible(String systemPrompt, String userPrompt, int maxTokens, double temperature) {
        logger.debug("[LLMService] Using OpenAI-compatible LLM endpoint for generation");
        
        try {
            // Build the request body compatible with OpenAI API format (which gpt4all supports)
            JSONArray messages = new JSONArray();
            
            if (systemPrompt != null && !systemPrompt.trim().isEmpty()) {
                messages.put(new JSONObject().put("role", "system").put("content", systemPrompt));
            }
            
            messages.put(new JSONObject().put("role", "user").put("content", userPrompt));
            
            JSONObject requestBody = new JSONObject()
                    .put("model", config.getOpenaiCompatibleModel())
                    .put("messages", messages)
                    .put("max_tokens", maxTokens)
                    .put("temperature", temperature);

            // Providers that honour `seed` (OpenAI gpt-4o, DeepSeek, OpenRouter,
            // Together, Groq, vLLM) combine it with temperature=0 for byte-stable
            // output; others silently drop the field.
            Long seed = configuredBaseSeed();
            if (seed != null) {
                requestBody.put("seed", seed);
            }

            logger.debug("[OpenAI-compatible LLM] Sending request to: {}", config.getOpenaiCompatibleUrl());
            logger.debug("[OpenAI-compatible LLM] Request body: {}", requestBody.toString());
            
            RequestBody body = RequestBody.create(
                    requestBody.toString(),
                    MediaType.parse("application/json")
            );
            
            Request.Builder reqBuilder = new Request.Builder()
                    .url(config.getOpenaiCompatibleUrl())
                    .post(body)
                    .addHeader("Content-Type", "application/json");
            // Hosted OpenAI-compatible endpoints (DeepSeek, OpenAI, OpenRouter,
            // ...) need an Authorization header. Self-hosted OpenAI shims like
            // gpt4all leave the key empty and skip the header.
            String apiKey = config.getOpenaiCompatibleApiKey();
            if (apiKey != null && !apiKey.isEmpty()) {
                reqBuilder.addHeader("Authorization", "Bearer " + apiKey);
            } else if (looksLikeHostedEndpoint(config.getOpenaiCompatibleUrl())) {
                // Loud warning the first time we hit this — better one ugly log line
                // up front than thousands of silent 401s downstream.
                warnMissingKeyOnce(config.getOpenaiCompatibleUrl());
            }
            Request request = reqBuilder.build();

            // Pick a client whose timeouts and ConnectionPool match the endpoint.
            // hostedHttpClient has a fresh pool and finite timeouts; httpClient is
            // the zero-timeout local one. We deliberately do NOT derive one from
            // the other via newBuilder(), because OkHttp 4.10 stream-level
            // timeouts inherit from the pooled connection's parent client and a
            // shared pool would re-import the zero timeout (observed in 22:56 run:
            // readTimeout(120) ignored, JVM blocked 3+ min in Http2Stream.waitForIo).
            boolean hosted = looksLikeHostedEndpoint(config.getOpenaiCompatibleUrl());
            OkHttpClient perCallClient = hosted ? hostedHttpClient : httpClient;

            // Belt-and-braces watchdog: schedule call.cancel() at deadline+5s in
            // case OkHttp's internal callTimeout still fails to fire on a
            // stalled HTTP/2 stream. cancel() throws IOException("Canceled") in
            // the executing thread, which our existing catch turns into null and
            // the cascade then routes to Ollama.
            okhttp3.Call call = perCallClient.newCall(request);
            java.util.concurrent.ScheduledFuture<?> watchdog = null;
            if (hosted) {
                watchdog = WATCHDOG.schedule(() -> {
                    if (!call.isExecuted() || !call.isCanceled()) {
                        logger.warn("[OpenAI-compatible LLM] Watchdog firing — cancelling stalled call to {}",
                                config.getOpenaiCompatibleUrl());
                        call.cancel();
                    }
                }, 185, TimeUnit.SECONDS);
            }

            try (Response response = call.execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String responseBody = response.body().string();
                    logger.debug("[OpenAI-compatible LLM] Response: {}", responseBody);

                    return parseOpenAICompatibleResponse(responseBody);
                } else {
                    String errorBody = "";
                    try {
                        errorBody = response.body() != null ? response.body().string() : "No error body";
                    } catch (Exception ignore) { }
                    logger.error("[OpenAI-compatible LLM] Request failed with code {}: {}", response.code(), errorBody);
                    return null;
                }
            } finally {
                if (watchdog != null) {
                    watchdog.cancel(false);
                }
            }

        } catch (Exception e) {
            logger.error("[OpenAI-compatible LLM] Error calling OpenAI-compatible API: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Parse an OpenAI-compatible chat-completions response (DeepSeek, OpenAI,
     * OpenRouter, etc. all share this response shape).
     */
    private String parseOpenAICompatibleResponse(String responseBody) {
        try {
            JSONObject jsonResponse = new JSONObject(responseBody);
            
            if (jsonResponse.has("choices")) {
                JSONArray choices = jsonResponse.getJSONArray("choices");
                if (choices.length() > 0) {
                    JSONObject choice = choices.getJSONObject(0);
                    JSONObject message = choice.getJSONObject("message");
                    String content = message.getString("content").trim();
                    
                    logger.debug("[OpenAI-compatible LLM] Successfully extracted content: {}", content);
                    return content;
                }
            }
            
            logger.warn("[OpenAI-compatible LLM] No choices in response");
            return null;
            
        } catch (Exception e) {
            logger.error("[OpenAI-compatible LLM] Error parsing response: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Generate text using Ollama API
     */
    private String generateWithOllama(String systemPrompt, String userPrompt, int maxTokens, double temperature) {
        if (ollamaClient == null) {
            logger.error("Ollama client is not initialized. Check configuration.");
            return null;
        }

        logger.debug("[LLMService] Using Ollama API for generation");
        return ollamaClient.generateContent(systemPrompt, userPrompt, maxTokens, temperature);
    }

    /**
     * Get current configuration
     */
    public LLMConfig getConfig() {
        return config;
    }
    
    /**
     * Check if service is properly configured and ready
     */
    public boolean isReady() {
        return config.isEnabled() && config.isValid();
    }

    /**
     * Get model name for logging
     */
    private String getModelName() {
        switch (config.getModelType()) {
            case GEMINI:
                return config.getGeminiModel();
            case OPENAI_COMPATIBLE:
                return config.getOpenaiCompatibleModel();
            case OLLAMA:
                return config.getOllamaModel();
            default:
                return "Unknown";
        }
    }

    /**
     * Get endpoint for logging
     */
    private String getEndpoint() {
        switch (config.getModelType()) {
            case GEMINI:
                return config.getGeminiApiUrl();
            case OPENAI_COMPATIBLE:
                return config.getOpenaiCompatibleUrl();
            case OLLAMA:
                return config.getOllamaUrl();
            default:
                return "Unknown";
        }
    }

    /**
     * Create metadata object for logging
     */
    private Object createMetadata(int maxTokens, double temperature) {
        return String.format("maxTokens=%d, temperature=%.2f", maxTokens, temperature);
    }

    /**
     * Close communication logger
     */
    public void close() {
        if (communicationSink != null) {
            communicationSink.close();
        }
    }

    /**
     * {@link LLMClient} entry point. Wraps the existing
     * {@link #generateText(String, String)} convenience method so callers
     * coding against the SPI see the same prompt-cache + per-call cascade
     * behaviour as the legacy direct callers.
     */
    @Override
    public String prompt(String systemPrompt, String userPrompt) {
        return generateText(systemPrompt, userPrompt);
    }

    /**
     * Resolve the configured base random seed, or {@code null} when
     * {@code -Drandom.seed} is unset or unparseable. Equivalent to the
     * adapter's {@code SeededRandom.getBaseSeed()} but uses only system
     * properties so the LLM module stays free of {@code restest-core}
     * dependencies.
     */
    private static Long configuredBaseSeed() {
        String prop = System.getProperty("random.seed");
        if (prop == null || prop.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(prop);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * Discover the {@link LLMCommunicationSink} binding via
     * {@link ServiceLoader}. When no binding is registered (e.g. running the
     * mist-llm jar standalone in a test) we fall back to
     * {@link NoOpLLMCommunicationSink} so callers never have to null-check.
     * The adapter ships its own binding in
     * {@code mist-restest-adapter/src/main/resources/META-INF/services/}.
     */
    private static LLMCommunicationSink loadCommunicationSink() {
        try {
            ServiceLoader<LLMCommunicationSink> loader = ServiceLoader.load(LLMCommunicationSink.class);
            Iterator<LLMCommunicationSink> it = loader.iterator();
            if (it.hasNext()) {
                LLMCommunicationSink sink = it.next();
                logger.debug("LLMService: bound communication sink {}", sink.getClass().getName());
                return sink;
            }
        } catch (Throwable t) {
            // Swallow and fall through to the no-op so a broken sink binding
            // never takes the LLM pipeline down with it.
            logger.warn("LLMService: failed to load LLMCommunicationSink via ServiceLoader ({}); using no-op", t.getMessage());
        }
        return new NoOpLLMCommunicationSink();
    }

    private static final java.util.concurrent.atomic.AtomicBoolean MISSING_KEY_WARNED =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    private static boolean looksLikeHostedEndpoint(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("api.deepseek.com")
                || lower.contains("api.openai.com")
                || lower.contains("api.anthropic.com")
                || lower.contains("openrouter.ai")
                || lower.contains("api.together")
                || lower.contains("api.mistral");
    }

    private static void warnMissingKeyOnce(String url) {
        if (MISSING_KEY_WARNED.compareAndSet(false, true)) {
            logger.error("[OpenAI-compatible LLM] Hosted endpoint detected ({}) but llm.openai_compatible.api.key "
                    + "(or legacy llm.local.api.key) resolved to an empty value. Every request will return 401. "
                    + "Either set DEEPSEEK_API_KEY (or your provider's env var) before launching, or write the key "
                    + "to .api_keys/<VAR_NAME> in the project root. Suppressing further warnings.", url);
        }
    }
}
