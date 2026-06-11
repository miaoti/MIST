package io.mist.llm;

import okhttp3.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Client for communicating with Google Gemini 2.0 Flash API
 */
public class GeminiApiClient {
    
    private static final Logger logger = LogManager.getLogger(GeminiApiClient.class);
    
    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final OkHttpClient httpClient;
    private final int maxRetries;
    private final boolean rateLimitRetryEnabled;
    
    public GeminiApiClient(String apiKey, String model, String baseUrl) {
        this(apiKey, model, baseUrl, 3, true);
    }

    public GeminiApiClient(String apiKey, String model, String baseUrl, int maxRetries, boolean rateLimitRetryEnabled) {
        this.apiKey = apiKey;
        this.model = model;
        this.baseUrl = baseUrl;
        this.maxRetries = maxRetries;
        this.rateLimitRetryEnabled = rateLimitRetryEnabled;
        
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .readTimeout(90, TimeUnit.SECONDS)
                .build();
        
        logger.info("GeminiApiClient initialized with model: {}, maxRetries: {}, rateLimitRetryEnabled: {}",
                   model, maxRetries, rateLimitRetryEnabled);
    }
    
    /**
     * Generate content using Gemini API with automatic retry for rate limiting
     * @param systemPrompt System prompt for the model
     * @param userPrompt User prompt/question
     * @param maxTokens Maximum tokens to generate
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
     * @param systemPrompt System prompt for the model
     * @param userPrompt User prompt/question
     * @param maxTokens Maximum tokens to generate
     * @param temperature Temperature for generation (0.0 to 1.0)
     * @param maxRetries Maximum number of retries for rate limiting
     * @return Generated content or null if failed
     */
    private String generateContentWithRetry(String systemPrompt, String userPrompt, int maxTokens, double temperature, int maxRetries) {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                // Build the request URL
                String url = String.format("%s/%s:generateContent?key=%s", baseUrl, model, apiKey);

                // Build the request body according to Gemini API format
                JSONObject requestBody = new JSONObject();
            
            // Add generation config
            JSONObject generationConfig = new JSONObject();
            generationConfig.put("maxOutputTokens", maxTokens);
            generationConfig.put("temperature", temperature);
            // Gemini 2.0+ honours generationConfig.seed; older models ignore it,
            // which is fine — temperature=0 still drives toward greedy decoding.
            Long seed = configuredBaseSeed();
            if (seed != null) {
                generationConfig.put("seed", seed);
            }
            requestBody.put("generationConfig", generationConfig);
            
            // Add contents (messages)
            JSONArray contents = new JSONArray();
            
            // Add system instruction if provided
            if (systemPrompt != null && !systemPrompt.trim().isEmpty()) {
                requestBody.put("systemInstruction", new JSONObject()
                    .put("parts", new JSONArray()
                        .put(new JSONObject().put("text", systemPrompt))));
            }
            
            // Add user message
            JSONObject userContent = new JSONObject();
            userContent.put("role", "user");
            userContent.put("parts", new JSONArray()
                .put(new JSONObject().put("text", userPrompt)));
            contents.put(userContent);
            
            requestBody.put("contents", contents);
            
            // Never log the URL verbatim — it carries the API key in the query string.
            logger.debug("[Gemini API] Sending request to: {}/{}:generateContent", baseUrl, model);
            logger.debug("[Gemini API] Request body: {}", requestBody.toString());
            
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
                        logger.debug("[Gemini API] Response: {}", responseBody);

                        String result = parseGeminiResponse(responseBody);
                        if (attempt > 1) {
                            logger.info("✅ [Gemini API] Request succeeded on attempt {} after rate limiting", attempt);
                        }
                        return result;
                    } else if (response.code() == 429) {
                        // Rate limiting detected
                        String errorBody = response.body() != null ? response.body().string() : "No error body";

                        if (attempt < maxRetries) {
                            int waitTimeSeconds = calculateWaitTime(attempt, errorBody);
                            logger.warn("⏳ [Gemini API] Rate limit hit (429) on attempt {}/{}. Waiting {} seconds before retry...",
                                       attempt, maxRetries, waitTimeSeconds);
                            logger.warn("⏳ [Gemini API] This is normal for Gemini free tier - we'll automatically retry");
                            logger.info("💤 [Gemini API] Sleeping for {} seconds to respect rate limits (attempt {} will start at {})",
                                       waitTimeSeconds, attempt + 1, java.time.LocalTime.now().plusSeconds(waitTimeSeconds));

                            Thread.sleep(waitTimeSeconds * 1000L);

                            logger.info("🔄 [Gemini API] Retrying request (attempt {}/{}) after rate limit wait", attempt + 1, maxRetries);
                            continue; // Retry the request
                        } else {
                            logger.error("❌ [Gemini API] Rate limit exceeded after {} attempts. Giving up.", maxRetries);
                            logger.error("❌ [Gemini API] Error body: {}", errorBody);
                            return null;
                        }
                    } else {
                        // Other HTTP error
                        String errorBody = response.body() != null ? response.body().string() : "No error body";
                        logger.error("[Gemini API] Request failed with code {}: {}", response.code(), errorBody);
                        return null;
                    }
                }
            
            } catch (InterruptedException e) {
                logger.warn("[Gemini API] Thread interrupted during rate limit wait");
                Thread.currentThread().interrupt();
                return null;
            } catch (Exception e) {
                if (attempt < maxRetries) {
                    logger.warn("[Gemini API] Request failed on attempt {}/{}: {}. Retrying...", attempt, maxRetries, e.getMessage());
                    try {
                        Thread.sleep(1000 * attempt); // Simple backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                    continue;
                } else {
                    logger.error("[Gemini API] Error calling Gemini API after {} attempts: {}", maxRetries, e.getMessage(), e);
                    return null;
                }
            }
        }

        logger.error("[Gemini API] All {} attempts failed", maxRetries);
        return null;
    }

    /**
     * Calculate wait time for rate limiting with exponential backoff
     * @param attempt Current attempt number (1-based)
     * @param errorBody Error response body that might contain retry-after info
     * @return Wait time in seconds
     */
    private int calculateWaitTime(int attempt, String errorBody) {
        // Try to extract retry-after from error response
        try {
            if (errorBody != null && errorBody.contains("Retry after")) {
                // Parse retry-after if present in error message
                // This is a simple heuristic - Gemini might include this info
                String[] parts = errorBody.split("Retry after ");
                if (parts.length > 1) {
                    String timeStr = parts[1].split(" ")[0];
                    int retryAfter = Integer.parseInt(timeStr.replaceAll("[^0-9]", ""));
                    if (retryAfter > 0 && retryAfter <= 300) { // Max 5 minutes
                        logger.info("📋 [Gemini API] Found retry-after hint: {} seconds", retryAfter);
                        return retryAfter;
                    }
                }
            }
        } catch (Exception e) {
            // Ignore parsing errors, fall back to default
        }

        // Default exponential backoff for Gemini free tier
        // Gemini free tier typically has per-minute limits
        switch (attempt) {
            case 1:
                return 60; // Wait 1 minute for first retry
            case 2:
                return 90; // Wait 1.5 minutes for second retry
            case 3:
            default:
                return 120; // Wait 2 minutes for subsequent retries
        }
    }
    
    /**
     * Parse Gemini API response to extract generated content
     */
    private String parseGeminiResponse(String responseBody) {
        try {
            JSONObject jsonResponse = new JSONObject(responseBody);
            
            if (jsonResponse.has("candidates")) {
                JSONArray candidates = jsonResponse.getJSONArray("candidates");
                if (candidates.length() > 0) {
                    JSONObject firstCandidate = candidates.getJSONObject(0);
                    if (firstCandidate.has("content")) {
                        JSONObject content = firstCandidate.getJSONObject("content");
                        if (content.has("parts")) {
                            JSONArray parts = content.getJSONArray("parts");
                            if (parts.length() > 0) {
                                JSONObject firstPart = parts.getJSONObject(0);
                                if (firstPart.has("text")) {
                                    String text = firstPart.getString("text").trim();
                                    logger.debug("[Gemini API] Successfully extracted content: {}", text);
                                    return text;
                                }
                            }
                        }
                    }
                }
            }
            
            // Check for error in response
            if (jsonResponse.has("error")) {
                JSONObject error = jsonResponse.getJSONObject("error");
                String errorMessage = error.optString("message", "Unknown error");
                logger.error("[Gemini API] API returned error: {}", errorMessage);
            }
            
            logger.warn("[Gemini API] No valid content found in response");
            return null;
            
        } catch (Exception e) {
            logger.error("[Gemini API] Error parsing response: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Simple method for basic text generation (convenience method)
     */
    public String generateText(String prompt) {
        return generateContent(null, prompt, 200, 0.7);
    }
    
    /**
     * Generate text with system prompt (convenience method)
     */
    public String generateText(String systemPrompt, String userPrompt) {
        return generateContent(systemPrompt, userPrompt, 200, 0.7);
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
