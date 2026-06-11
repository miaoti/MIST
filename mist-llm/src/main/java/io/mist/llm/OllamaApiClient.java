package io.mist.llm;

import okhttp3.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

/**
 * Client for communicating with Ollama API
 * Supports local Ollama installation with models like gemma2:2b
 */
public class OllamaApiClient {
    
    private static final Logger logger = LogManager.getLogger(OllamaApiClient.class);
    
    private final String baseUrl;
    private final String model;
    private final OkHttpClient httpClient;
    private final int maxRetries;
    private final boolean rateLimitRetryEnabled;
    
    public OllamaApiClient(String baseUrl, String model) {
        this(baseUrl, model, 3, true);
    }
    
    public OllamaApiClient(String baseUrl, String model, int maxRetries, boolean rateLimitRetryEnabled) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.model = model;
        this.maxRetries = maxRetries;
        this.rateLimitRetryEnabled = rateLimitRetryEnabled;
        
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        
        logger.info("OllamaApiClient initialized with model: {}, baseUrl: {}, maxRetries: {}, rateLimitRetryEnabled: {}", 
                   model, baseUrl, maxRetries, rateLimitRetryEnabled);
    }
    
    /**
     * Generate content using Ollama API with automatic retry for rate limiting
     * @param systemPrompt System prompt for the model
     * @param userPrompt User prompt/question
     * @param maxTokens Maximum tokens to generate (ignored for Ollama, but kept for compatibility)
     * @param temperature Temperature for generation (0.0 to 1.0)
     * @return Generated content or null if failed
     */
    public String generateContent(String systemPrompt, String userPrompt, int maxTokens, double temperature) {
        if (!rateLimitRetryEnabled) {
            return generateContentWithRetry(systemPrompt, userPrompt, maxTokens, temperature, 1);
        }
        return generateContentWithRetry(systemPrompt, userPrompt, maxTokens, temperature, maxRetries);
    }
    
    /**
     * Generate content with retry mechanism for rate limiting
     */
    private String generateContentWithRetry(String systemPrompt, String userPrompt, int maxTokens, double temperature, int maxRetries) {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                // Build the request URL for Ollama chat API
                String url = baseUrl + "/api/chat";
                
                // Build the request body according to Ollama API format
                JSONObject requestBody = new JSONObject();
                requestBody.put("model", model);
                requestBody.put("stream", false);
                
                // Build messages array with system and user messages
                JSONArray messages = new JSONArray();
                
                // Add system message if provided
                if (systemPrompt != null && !systemPrompt.trim().isEmpty()) {
                    JSONObject systemMessage = new JSONObject();
                    systemMessage.put("role", "system");
                    systemMessage.put("content", systemPrompt);
                    messages.put(systemMessage);
                }
                
                // Add user message
                JSONObject userMessage = new JSONObject();
                userMessage.put("role", "user");
                userMessage.put("content", userPrompt);
                messages.put(userMessage);
                
                requestBody.put("messages", messages);
                
                // Add options for temperature (NOT num_predict - it causes empty content with qwen3 models)
                // 🔥 FIX: Removed num_predict option because it causes message.content to be empty
                // with qwen3:8b and similar models that use thinking mode
                JSONObject options = new JSONObject();
                options.put("temperature", temperature);
                // NOT adding num_predict - it breaks content generation
                Long seed = configuredBaseSeed();
                if (seed != null) {
                    options.put("seed", seed);
                }
                requestBody.put("options", options);
                
                logger.debug("[Ollama API] Request URL: {}", url);
                logger.debug("[Ollama API] Request body: {}", requestBody.toString());
                
                // Create HTTP request
                RequestBody body = RequestBody.create(
                    requestBody.toString(),
                    MediaType.parse("application/json")
                );
                
                Request request = new Request.Builder()
                        .url(url)
                        .post(body)
                        .addHeader("Content-Type", "application/json")
                        .build();
                
                // Execute request
                try (Response response = httpClient.newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        String responseBody = response.body().string();
                        logger.debug("[Ollama API] Response: {}", responseBody);
                        
                        String result = parseOllamaResponse(responseBody);
                        if (attempt > 1) {
                            logger.info("✅ [Ollama API] Request succeeded on attempt {} after rate limiting", attempt);
                        }
                        return result;
                    } else if (response.code() == 429) {
                        // Rate limiting detected (though less common with local Ollama)
                        String errorBody = response.body() != null ? response.body().string() : "No error body";
                        
                        if (attempt < maxRetries) {
                            int waitTimeSeconds = calculateWaitTime(attempt);
                            logger.warn("⏳ [Ollama API] Rate limit hit (429) on attempt {}/{}. Waiting {} seconds before retry...", 
                                       attempt, maxRetries, waitTimeSeconds);
                            logger.info("💤 [Ollama API] Sleeping for {} seconds to respect rate limits...", waitTimeSeconds);
                            
                            Thread.sleep(waitTimeSeconds * 1000L);
                            
                            logger.info("🔄 [Ollama API] Retrying request (attempt {}/{}) after rate limit wait", attempt + 1, maxRetries);
                            continue; // Retry the request
                        } else {
                            logger.error("❌ [Ollama API] Rate limit exceeded after {} attempts. Giving up.", maxRetries);
                            logger.error("❌ [Ollama API] Error body: {}", errorBody);
                            return null;
                        }
                    } else {
                        // Other HTTP error
                        String errorBody = response.body() != null ? response.body().string() : "No error body";
                        logger.error("[Ollama API] Request failed with code {}: {}", response.code(), errorBody);
                        return null;
                    }
                }
                
            } catch (InterruptedException e) {
                logger.warn("[Ollama API] Thread interrupted during rate limit wait");
                Thread.currentThread().interrupt();
                return null;
            } catch (Exception e) {
                if (attempt < maxRetries) {
                    logger.warn("[Ollama API] Request failed on attempt {}/{}: {}. Retrying...", attempt, maxRetries, e.getMessage());
                    try {
                        Thread.sleep(1000 * attempt); // Simple backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                    continue;
                } else {
                    logger.error("[Ollama API] Error calling Ollama API after {} attempts: {}", maxRetries, e.getMessage(), e);
                    return null;
                }
            }
        }
        
        logger.error("[Ollama API] All {} attempts failed", maxRetries);
        return null;
    }
    
    /**
     * Parse Ollama API response to extract the generated content
     */
    private String parseOllamaResponse(String responseBody) {
        try {
            JSONObject response = new JSONObject(responseBody);
            
            // Check if response contains message
            if (response.has("message")) {
                JSONObject message = response.getJSONObject("message");
                if (message.has("content")) {
                    String content = message.getString("content");
                    if (content != null && !content.trim().isEmpty()) {
                        return content.trim();
                    }
                }
            }
            
            // Fallback: check for direct content field
            if (response.has("content")) {
                String content = response.getString("content");
                if (content != null && !content.trim().isEmpty()) {
                    return content.trim();
                }
            }
            
            logger.warn("[Ollama API] No content found in response: {}", responseBody);
            return null;
            
        } catch (Exception e) {
            logger.error("[Ollama API] Failed to parse response: {}", e.getMessage());
            logger.debug("[Ollama API] Response body: {}", responseBody);
            return null;
        }
    }
    
    /**
     * Calculate wait time for rate limiting with simple backoff
     */
    private int calculateWaitTime(int attempt) {
        // Simple backoff for local Ollama (usually not needed)
        switch (attempt) {
            case 1:
                return 5; // Wait 5 seconds for first retry
            case 2:
                return 10; // Wait 10 seconds for second retry
            case 3:
            default:
                return 15; // Wait 15 seconds for subsequent retries
        }
    }

    /**
     * Resolve the configured base random seed, or {@code null} when
     * {@code -Drandom.seed} is unset or unparseable. Reads the system
     * property directly so the mist-llm module carries no compile-time edge
     * to the adapter's {@code SeededRandom}.
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
}
