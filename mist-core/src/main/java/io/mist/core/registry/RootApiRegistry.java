package io.mist.core.registry;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.json.JSONObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.mist.core.workflow.WorkflowStep;
import io.mist.core.workflow.WorkflowScenario;

/**
 * Registry for storing and managing unique Root API endpoints with their complete
 * microservice interaction trees. This registry ensures no duplicate root APIs are stored
 * and maintains the tree structure for each root API showing all downstream service calls.
 * 
 * The registry is designed for microservice testing where one root API can trigger
 * multiple different interaction patterns (trees) depending on the specific execution context.
 * 
 * Storage format: JSON file with the following structure:
 * {
 *   "registry_metadata": {
 *     "version": "1.0",
 *     "last_updated": "2025-09-16T10:30:00Z",
 *     "total_root_apis": 5,
 *     "total_trees": 12
 *   },
 *   "root_apis": {
 *     "GET /api/v1/tickets": {
 *       "method": "GET",
 *       "path": "/api/v1/tickets",
 *       "service": "ts-ticket-service",
 *       "trees": [
 *         {
 *           "tree_id": "tree_1",
 *           "source_trace": "admin_add_route_success",
 *           "root": {
 *             "service": "ts-ticket-service",
 *             "operation": "GET /api/v1/tickets",
 *             "children": [...]
 *           }
 *         }
 *       ]
 *     }
 *   }
 * }
 */
public class RootApiRegistry {
    private static final Logger log = LogManager.getLogger(RootApiRegistry.class);
    
    private final Map<String, RootApiEntry> registry = new ConcurrentHashMap<>();
    private final String registryFilePath;
    
    /**
     * Creates a new RootApiRegistry with the specified file path.
     * @param registryFilePath path where the registry JSON file will be stored
     */
    public RootApiRegistry(String registryFilePath) {
        this.registryFilePath = registryFilePath;
        loadRegistryFromFile();
    }
    
    /**
     * Registers root APIs from workflow scenarios, avoiding duplicates.
     * Preserves existing registry content and only adds new unique trees.
     * @param scenarios list of workflow scenarios extracted from traces
     * @return number of new root APIs registered (excluding duplicates)
     */
    public int registerRootApisFromScenarios(List<WorkflowScenario> scenarios) {
        int initialRootApis = registry.size();
        int initialTrees = registry.values().stream()
            .mapToInt(entry -> entry.getTrees().size()).sum();
        
        int newRootApis = 0;
        int newTrees = 0;
        
        log.info("Starting registration with {} existing root APIs and {} existing trees", 
                initialRootApis, initialTrees);
        
        for (WorkflowScenario scenario : scenarios) {
            String sourceFileName = scenario.getSourceFileName();
            
            for (WorkflowStep rootStep : scenario.getRootSteps()) {
                String rootApiKey = generateRootApiKey(rootStep);
                
                boolean isNewApi = !registry.containsKey(rootApiKey);
                
                // Get or create the root API entry
                RootApiEntry entry = registry.computeIfAbsent(rootApiKey, k -> {
                    log.info("✓ Registering NEW root API: {}", rootApiKey);
                    return new RootApiEntry(rootStep);
                });
                
                if (isNewApi) {
                    newRootApis++;
                }
                
                // Add the tree structure for this execution pattern
                String treeId = generateTreeId(entry, sourceFileName);
                int treesBefore = entry.getTrees().size();
                boolean added = entry.addTree(treeId, sourceFileName, rootStep);
                int treesAfter = entry.getTrees().size();
                
                if (added) {
                    if (treesAfter == treesBefore) {
                        // Tree count didn't change - must have replaced a subset
                        log.info("  ↻ REPLACED subset tree for '{}' with more complete tree from trace: {}", 
                                rootApiKey, sourceFileName);
                    } else {
                        // Tree count increased - new tree added
                        newTrees++;
                        log.info("  ✓ Added NEW tree for '{}' from trace: {}", rootApiKey, sourceFileName);
                    }
                } else {
                    log.debug("  ⊗ Skipped tree for '{}' from trace: {} (duplicate or subset of existing tree)", 
                             rootApiKey, sourceFileName);
                }
            }
        }
        
        int totalRootApis = registry.size();
        int totalTrees = registry.values().stream()
            .mapToInt(entry -> entry.getTrees().size()).sum();
        
        log.info("=== Registration Summary ===");
        log.info("Root APIs: {} existing + {} new = {} total", 
                initialRootApis, newRootApis, totalRootApis);
        log.info("Trees: {} existing + {} new = {} total", 
                initialTrees, newTrees, totalTrees);
        
        return newRootApis;
    }
    
    /**
     * Saves the current registry state to the JSON file.
     */
    public void saveRegistry() {
        try {
            JSONObject registryJson = new JSONObject();
            
            // Add metadata
            JSONObject metadata = new JSONObject();
            metadata.put("version", "1.0");
            metadata.put("last_updated", new Date().toString());
            metadata.put("total_root_apis", registry.size());
            metadata.put("total_trees", registry.values().stream()
                .mapToInt(entry -> entry.getTrees().size()).sum());
            registryJson.put("registry_metadata", metadata);
            
            // Add root APIs
            JSONObject rootApis = new JSONObject();
            for (Map.Entry<String, RootApiEntry> entry : registry.entrySet()) {
                rootApis.put(entry.getKey(), entry.getValue().toJson());
            }
            registryJson.put("root_apis", rootApis);
            
            // Ensure parent directory exists
            File file = new File(registryFilePath);
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            
            // Write to file with pretty formatting
            try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
                writer.write(registryJson.toString(2)); // 2 spaces indentation
            }
            
            log.info("Registry saved to: {}", registryFilePath);
            
        } catch (IOException e) {
            log.error("Failed to save registry to file: {}", registryFilePath, e);
        }
    }
    
    /**
     * Loads registry from the JSON file if it exists.
     * This preserves all existing content - new registrations will be added to it.
     */
    private void loadRegistryFromFile() {
        File file = new File(registryFilePath);
        if (!file.exists()) {
            log.info("Registry file does not exist, starting with empty registry: {}", registryFilePath);
            log.info("New registry will be created at: {}", registryFilePath);
            return;
        }
        
        try {
            String content = new String(Files.readAllBytes(Paths.get(registryFilePath)), StandardCharsets.UTF_8);
            JSONObject registryJson = new JSONObject(content);
            
            if (registryJson.has("root_apis")) {
                JSONObject rootApis = registryJson.getJSONObject("root_apis");
                
                int loadedApis = 0;
                int loadedTrees = 0;
                
                for (String apiKey : rootApis.keySet()) {
                    JSONObject apiData = rootApis.getJSONObject(apiKey);
                    RootApiEntry entry = RootApiEntry.fromJson(apiData);
                    registry.put(apiKey, entry);
                    loadedApis++;
                    loadedTrees += entry.getTrees().size();
                }
                
                log.info("✓ Loaded EXISTING registry: {} root APIs, {} trees from: {}", 
                        loadedApis, loadedTrees, registryFilePath);
                log.info("  New content will be merged with existing content");
            }
            
        } catch (Exception e) {
            log.error("Failed to load registry from file: {}, starting with empty registry", registryFilePath, e);
            registry.clear();
        }
    }
    
    /**
     * Generates a unique key for a root API based on HTTP method and path.
     */
    private String generateRootApiKey(WorkflowStep rootStep) {
        String method = rootStep.getInputFields().get("http.method");
        String path = extractApiPath(rootStep);
        
        if (method == null || method.isEmpty()) {
            method = "UNKNOWN";
        }
        
        if (path == null || path.isEmpty()) {
            path = rootStep.getOperationName();
        }
        
        return method + " " + path;
    }
    
    /**
     * Extracts the API path from a workflow step, handling both http.url and http.target.
     */
    private String extractApiPath(WorkflowStep step) {
        String target = step.getInputFields().get("http.target");
        if (target != null && !target.isEmpty()) {
            // Remove query parameters from target
            int queryIndex = target.indexOf('?');
            return queryIndex > 0 ? target.substring(0, queryIndex) : target;
        }
        
        String url = step.getInputFields().get("http.url");
        if (url != null && !url.isEmpty()) {
            try {
                // Extract path from full URL
                java.net.URI parsedUri = java.net.URI.create(url);
                String path = parsedUri.getPath();
                return path != null && !path.isEmpty() ? path : "/";
            } catch (Exception e) {
                log.warn("Failed to parse URL: {}", url);
            }
        }
        
        return step.getOperationName();
    }
    
    /**
     * Generates a unique tree ID for a specific execution pattern.
     */
    private String generateTreeId(RootApiEntry entry, String sourceFileName) {
        return "tree_" + (entry.getTrees().size() + 1) + "_" + sourceFileName;
    }
    
    /**
     * Returns the current registry statistics.
     */
    public RegistryStats getStats() {
        int totalTrees = registry.values().stream()
            .mapToInt(entry -> entry.getTrees().size()).sum();
        return new RegistryStats(registry.size(), totalTrees);
    }
    
    /**
     * Returns all registered root API keys.
     */
    public Set<String> getAllRootApiKeys() {
        return new HashSet<>(registry.keySet());
    }
    
    /**
     * Returns the RootApiEntry for a specific API key.
     */
    public RootApiEntry getRootApiEntry(String apiKey) {
        return registry.get(apiKey);
    }
    
    /**
     * Statistics about the registry.
     */
    public static class RegistryStats {
        private final int totalRootApis;
        private final int totalTrees;
        
        public RegistryStats(int totalRootApis, int totalTrees) {
            this.totalRootApis = totalRootApis;
            this.totalTrees = totalTrees;
        }
        
        public int getTotalRootApis() { return totalRootApis; }
        public int getTotalTrees() { return totalTrees; }
        
        @Override
        public String toString() {
            return String.format("RegistryStats{rootApis=%d, trees=%d}", totalRootApis, totalTrees);
        }
    }
}
