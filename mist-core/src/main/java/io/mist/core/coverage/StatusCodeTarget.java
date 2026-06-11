package io.mist.core.coverage;

import org.json.JSONObject;
import org.json.JSONArray;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Represents an LLM-discovered status code target with its triggering strategy.
 * This class is NOT an enum - it's fully dynamic based on LLM discovery.
 * 
 * The LLM analyzes an API operation and returns possible status codes along with
 * strategies to trigger them. This enables smart, adaptive status code coverage
 * without hardcoding specific status codes.
 */
public class StatusCodeTarget {
    
    private final int statusCode;
    private final String category;           // e.g., "Success", "Client Error", "Server Error"
    private final String description;        // LLM-generated description of when this code is returned
    private final String triggerStrategy;    // LLM-generated strategy to trigger this status code
    private final boolean requiresAuthManipulation;
    private final Map<String, String> suggestedInputs;  // LLM-suggested parameter values
    
    private StatusCodeTarget(Builder builder) {
        this.statusCode = builder.statusCode;
        this.category = builder.category;
        this.description = builder.description;
        this.triggerStrategy = builder.triggerStrategy;
        this.requiresAuthManipulation = builder.requiresAuthManipulation;
        this.suggestedInputs = builder.suggestedInputs != null ? 
            new HashMap<>(builder.suggestedInputs) : new HashMap<>();
    }
    
    // Getters
    public int getStatusCode() {
        return statusCode;
    }
    
    public String getCategory() {
        return category;
    }
    
    public String getDescription() {
        return description;
    }
    
    public String getTriggerStrategy() {
        return triggerStrategy;
    }
    
    public boolean isRequiresAuthManipulation() {
        return requiresAuthManipulation;
    }
    
    public Map<String, String> getSuggestedInputs() {
        return new HashMap<>(suggestedInputs);
    }
    
    /**
     * Get the HTTP status code category based on the code number.
     */
    public String getHttpCategory() {
        if (statusCode >= 100 && statusCode < 200) return "1xx Informational";
        if (statusCode >= 200 && statusCode < 300) return "2xx Success";
        if (statusCode >= 300 && statusCode < 400) return "3xx Redirection";
        if (statusCode >= 400 && statusCode < 500) return "4xx Client Error";
        if (statusCode >= 500 && statusCode < 600) return "5xx Server Error";
        return "Unknown";
    }
    
    /**
     * Check if this is a success status code (2xx).
     */
    public boolean isSuccess() {
        return statusCode >= 200 && statusCode < 300;
    }
    
    /**
     * Check if this is a client error status code (4xx).
     */
    public boolean isClientError() {
        return statusCode >= 400 && statusCode < 500;
    }
    
    /**
     * Check if this is a server error status code (5xx).
     */
    public boolean isServerError() {
        return statusCode >= 500 && statusCode < 600;
    }
    
    /**
     * Parse a StatusCodeTarget from an LLM JSON response.
     * 
     * Expected JSON format:
     * {
     *   "statusCode": 404,
     *   "category": "Client Error",
     *   "description": "Resource not found",
     *   "triggerStrategy": "Use a non-existent resource ID",
     *   "requiresAuthManipulation": false,
     *   "suggestedInputs": {"resourceId": "non-existent-id-12345"}
     * }
     */
    public static StatusCodeTarget fromLLMResponse(JSONObject json) {
        Builder builder = new Builder(json.getInt("statusCode"));
        
        if (json.has("category")) {
            builder.category(json.getString("category"));
        }
        if (json.has("description")) {
            builder.description(json.getString("description"));
        }
        if (json.has("triggerStrategy")) {
            builder.triggerStrategy(json.getString("triggerStrategy"));
        }
        if (json.has("requiresAuthManipulation")) {
            builder.requiresAuthManipulation(json.getBoolean("requiresAuthManipulation"));
        }
        if (json.has("suggestedInputs")) {
            JSONObject inputs = json.getJSONObject("suggestedInputs");
            Map<String, String> inputMap = new HashMap<>();
            for (String key : inputs.keySet()) {
                inputMap.put(key, inputs.optString(key, ""));
            }
            builder.suggestedInputs(inputMap);
        }
        
        return builder.build();
    }
    
    /**
     * Convert this StatusCodeTarget to JSON for serialization/logging.
     */
    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        json.put("statusCode", statusCode);
        json.put("category", category);
        json.put("description", description);
        json.put("triggerStrategy", triggerStrategy);
        json.put("requiresAuthManipulation", requiresAuthManipulation);
        json.put("suggestedInputs", new JSONObject(suggestedInputs));
        return json;
    }
    
    @Override
    public String toString() {
        return String.format("StatusCodeTarget{%d %s: %s}", 
            statusCode, category, description);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StatusCodeTarget that = (StatusCodeTarget) o;
        return statusCode == that.statusCode;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(statusCode);
    }
    
    /**
     * Builder for StatusCodeTarget.
     */
    public static class Builder {
        private final int statusCode;
        private String category = "";
        private String description = "";
        private String triggerStrategy = "";
        private boolean requiresAuthManipulation = false;
        private Map<String, String> suggestedInputs = new HashMap<>();
        
        public Builder(int statusCode) {
            this.statusCode = statusCode;
        }
        
        public Builder category(String category) {
            this.category = category != null ? category : "";
            return this;
        }
        
        public Builder description(String description) {
            this.description = description != null ? description : "";
            return this;
        }
        
        public Builder triggerStrategy(String triggerStrategy) {
            this.triggerStrategy = triggerStrategy != null ? triggerStrategy : "";
            return this;
        }
        
        public Builder requiresAuthManipulation(boolean requiresAuthManipulation) {
            this.requiresAuthManipulation = requiresAuthManipulation;
            return this;
        }
        
        public Builder suggestedInputs(Map<String, String> suggestedInputs) {
            this.suggestedInputs = suggestedInputs != null ? new HashMap<>(suggestedInputs) : new HashMap<>();
            return this;
        }
        
        public Builder addSuggestedInput(String paramName, String value) {
            this.suggestedInputs.put(paramName, value);
            return this;
        }
        
        public StatusCodeTarget build() {
            return new StatusCodeTarget(this);
        }
    }
}
