package io.mist.core.smart;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Discovers API endpoints from OpenAPI specification files
 * Provides service-to-endpoint mapping for smart input fetching
 */
public class OpenAPIEndpointDiscovery {
    
    private static final Logger log = LogManager.getLogger(OpenAPIEndpointDiscovery.class);
    
    private final ObjectMapper yamlMapper;
    private Map<String, List<EndpointInfo>> serviceEndpoints;
    private boolean loaded = false;
    
    public OpenAPIEndpointDiscovery() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        this.serviceEndpoints = new HashMap<>();
    }
    
    /**
     * Load and parse OpenAPI specification from file
     */
    public void loadFromFile(String filePath) throws IOException {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new IOException("OpenAPI specification file not found: " + filePath);
        }
        
        try {
            JsonNode root = yamlMapper.readTree(file);
            parseOpenAPISpec(root);
            loaded = true;
            log.info("Loaded OpenAPI specification from {}, found {} services with {} total endpoints", 
                    filePath, serviceEndpoints.size(), getTotalEndpointCount());
        } catch (IOException e) {
            log.error("Failed to parse OpenAPI specification from {}: {}", filePath, e.getMessage());
            throw e;
        }
    }
    
    /**
     * Parse the OpenAPI specification and extract service-endpoint mappings
     */
    private void parseOpenAPISpec(JsonNode root) {
        serviceEndpoints.clear();
        
        JsonNode paths = root.get("paths");
        if (paths == null || !paths.isObject()) {
            log.warn("No paths found in OpenAPI specification");
            return;
        }
        
        paths.fields().forEachRemaining(pathEntry -> {
            String path = pathEntry.getKey();
            JsonNode pathNode = pathEntry.getValue();
            
            // Process each HTTP method for this path
            pathNode.fields().forEachRemaining(methodEntry -> {
                String method = methodEntry.getKey().toLowerCase();
                JsonNode operationNode = methodEntry.getValue();
                
                // Skip non-HTTP methods (like parameters, summary, etc.)
                if (!isHttpMethod(method)) {
                    return;
                }
                
                // Derive the service name: x-service-name extension first, then
                // OpenAPI tags (an off-the-shelf swagger groups by tags, not by the
                // train-ticket-specific x-service-name extension).
                String serviceName = deriveServiceName(operationNode);
                if (serviceName != null && !serviceName.isEmpty()) {
                    // Extract operation details
                    String operationId = getTextValue(operationNode, "operationId");
                    String summary = getTextValue(operationNode, "summary");
                    String description = getTextValue(operationNode, "description");

                    // Create endpoint info
                    EndpointInfo endpoint = new EndpointInfo(
                        path,
                        method.toUpperCase(),
                        serviceName,
                        operationId,
                        summary,
                        description
                    );

                    // Add to service mapping
                    serviceEndpoints.computeIfAbsent(serviceName, k -> new ArrayList<>()).add(endpoint);

                    log.debug("Found endpoint: {} {} for service {}", method.toUpperCase(), path, serviceName);
                }
            });
        });
    }
    
    /**
     * Derive a service name for an operation: the {@code x-service-name} extension
     * first, then OpenAPI {@code tags} (preferring a tag ending in "service", else
     * the first tag). Returns null when neither is present. Previously only
     * {@code x-service-name} was honored, so an off-the-shelf swagger that groups by
     * tags (e.g. Bookinfo: product/review/rating, no x-service-name) registered ZERO
     * services and smart-fetch had no candidate list to ground LLM discovery against
     * (it then hallucinated train-ticket service names). This mirrors the conf
     * generator's {@code determineServiceName} cascade.
     */
    private String deriveServiceName(JsonNode operationNode) {
        JsonNode ext = operationNode.get("x-service-name");
        if (ext != null && ext.isTextual() && !ext.asText().trim().isEmpty()) {
            return ext.asText().trim();
        }
        JsonNode tags = operationNode.get("tags");
        if (tags != null && tags.isArray() && tags.size() > 0) {
            String first = null;
            for (JsonNode t : tags) {
                if (t == null || !t.isTextual()) continue;
                String tag = t.asText().trim();
                if (tag.isEmpty()) continue;
                if (first == null) first = tag;
                if (tag.toLowerCase().endsWith("service")) return tag;
            }
            if (first != null) return first;
        }
        return null;
    }

    /**
     * Check if the given string is an HTTP method
     */
    private boolean isHttpMethod(String method) {
        return Arrays.asList("get", "post", "put", "delete", "patch", "head", "options", "trace")
                .contains(method.toLowerCase());
    }
    
    /**
     * Safely extract text value from JSON node
     */
    private String getTextValue(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        return field != null && field.isTextual() ? field.asText() : null;
    }
    
    /**
     * Get all available service names
     */
    public Set<String> getAllServices() {
        return new HashSet<>(serviceEndpoints.keySet());
    }
    
    /**
     * Get endpoints for a specific service
     */
    public List<EndpointInfo> getEndpointsForService(String serviceName) {
        return serviceEndpoints.getOrDefault(serviceName, new ArrayList<>());
    }
    
    // findBestEndpoint and scoreEndpoint removed — dead code (Bug audit Finding #16).
    // Endpoint selection is performed by SmartInputFetcher.selectEndpointWithLLMRetry which
    // is a strict superset of this scoring heuristic.
    
    /**
     * Get total number of endpoints across all services
     */
    private int getTotalEndpointCount() {
        return serviceEndpoints.values().stream().mapToInt(List::size).sum();
    }
    
    /**
     * Check if OpenAPI spec has been loaded
     */
    public boolean isLoaded() {
        return loaded;
    }
    
    /**
     * Get statistics about loaded endpoints
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("loaded", loaded);
        stats.put("serviceCount", serviceEndpoints.size());
        stats.put("totalEndpoints", getTotalEndpointCount());
        stats.put("servicesWithEndpoints", serviceEndpoints.entrySet().stream()
                .collect(Collectors.toMap(
                    Map.Entry::getKey, 
                    entry -> entry.getValue().size()
                )));
        return stats;
    }
    
    /**
     * Represents an API endpoint with metadata
     */
    public static class EndpointInfo {
        private final String path;
        private final String method;
        private final String serviceName;
        private final String operationId;
        private final String summary;
        private final String description;
        
        public EndpointInfo(String path, String method, String serviceName, 
                           String operationId, String summary, String description) {
            this.path = path;
            this.method = method;
            this.serviceName = serviceName;
            this.operationId = operationId;
            this.summary = summary;
            this.description = description;
        }
        
        // Getters
        public String getPath() { return path; }
        public String getMethod() { return method; }
        public String getServiceName() { return serviceName; }
        public String getOperationId() { return operationId; }
        public String getSummary() { return summary; }
        public String getDescription() { return description; }
        
        @Override
        public String toString() {
            return String.format("%s %s (%s)", method, path, serviceName);
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            EndpointInfo that = (EndpointInfo) o;
            return Objects.equals(path, that.path) && 
                   Objects.equals(method, that.method) && 
                   Objects.equals(serviceName, that.serviceName);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(path, method, serviceName);
        }
    }
    
    // ScoredEndpoint removed alongside findBestEndpoint (Bug audit Finding #16).
}