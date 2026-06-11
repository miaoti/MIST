package io.mist.core.coverage;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Tracks HTTP status code coverage across test execution rounds.
 * Ensures no redundant exploration attempts and maintains round-robin fairness.
 * 
 * This tracker is central to the smart status code exploration system:
 * - Stores LLM-discovered status codes per API
 * - Tracks which codes have been triggered (actually returned)
 * - Tracks which codes have been targeted (attempted to trigger)
 * - Provides untriggered codes for exploration
 * - Manages round transitions for round-robin fairness
 */
public class StatusCodeCoverageTracker {
    
    private static final Logger log = LogManager.getLogger(StatusCodeCoverageTracker.class);
    
    // API key = "METHOD /path" (e.g., "POST /api/v1/orderservice/order")
    private final Map<String, ApiStatusCodeInfo> apiCoverage = new HashMap<>();
    
    private int currentRound = 0;
    
    /**
     * Information about status code coverage for a single API operation.
     */
    public static class ApiStatusCodeInfo {
        private final String apiKey;
        private final List<StatusCodeTarget> discoveredCodes;  // From LLM Discovery
        private final Set<Integer> triggeredCodes;             // Actually returned in tests
        private final Set<Integer> targetedCodes;              // Attempted to trigger (may not succeed)
        private final Map<Integer, StatusCodeTarget> codeDetails; // Quick lookup by code
        
        public ApiStatusCodeInfo(String apiKey, List<StatusCodeTarget> discoveredCodes) {
            this.apiKey = apiKey;
            this.discoveredCodes = new ArrayList<>(discoveredCodes);
            this.triggeredCodes = new HashSet<>();
            this.targetedCodes = new HashSet<>();
            this.codeDetails = new HashMap<>();
            
            for (StatusCodeTarget target : discoveredCodes) {
                codeDetails.put(target.getStatusCode(), target);
            }
        }
        
        public String getApiKey() { return apiKey; }
        public List<StatusCodeTarget> getDiscoveredCodes() { return new ArrayList<>(discoveredCodes); }
        public Set<Integer> getTriggeredCodes() { return new HashSet<>(triggeredCodes); }
        public Set<Integer> getTargetedCodes() { return new HashSet<>(targetedCodes); }
        
        public StatusCodeTarget getTargetDetails(int statusCode) {
            return codeDetails.get(statusCode);
        }
        
        public int getDiscoveredCount() { return discoveredCodes.size(); }
        public int getTriggeredCount() { return triggeredCodes.size(); }
        public int getUntriggeredCount() { return discoveredCodes.size() - triggeredCodes.size(); }
        
        public double getCoveragePercentage() {
            if (discoveredCodes.isEmpty()) return 100.0;
            return (triggeredCodes.size() * 100.0) / discoveredCodes.size();
        }
    }
    
    /**
     * Summary of coverage for a single API.
     */
    public static class CoverageSummary {
        public final String apiKey;
        public final int discoveredCount;
        public final int triggeredCount;
        public final int untriggeredCount;
        public final double coveragePercentage;
        public final List<Integer> triggeredCodes;
        public final List<Integer> untriggeredCodes;
        
        public CoverageSummary(ApiStatusCodeInfo info) {
            this.apiKey = info.getApiKey();
            this.discoveredCount = info.getDiscoveredCount();
            this.triggeredCount = info.getTriggeredCount();
            this.untriggeredCount = info.getUntriggeredCount();
            this.coveragePercentage = info.getCoveragePercentage();
            this.triggeredCodes = new ArrayList<>(info.getTriggeredCodes());
            this.untriggeredCodes = info.getDiscoveredCodes().stream()
                .map(StatusCodeTarget::getStatusCode)
                .filter(code -> !info.getTriggeredCodes().contains(code))
                .collect(Collectors.toList());
        }
        
        @Override
        public String toString() {
            return String.format("%s: %d/%d (%.1f%%) - triggered: %s, untriggered: %s",
                apiKey, triggeredCount, discoveredCount, coveragePercentage,
                triggeredCodes, untriggeredCodes);
        }
    }
    
    /**
     * Register discovered status codes for an API.
     * Called after LLM discovery completes.
     */
    public void registerDiscoveredCodes(String apiKey, List<StatusCodeTarget> discoveredCodes) {
        if (apiCoverage.containsKey(apiKey)) {
            log.debug("API {} already registered, updating discovered codes", apiKey);
        }
        
        apiCoverage.put(apiKey, new ApiStatusCodeInfo(apiKey, discoveredCodes));
        
        log.info("Registered {} discovered status codes for {}: {}", 
            discoveredCodes.size(), apiKey,
            discoveredCodes.stream()
                .map(t -> String.valueOf(t.getStatusCode()))
                .collect(Collectors.joining(", ")));
    }
    
    /**
     * Record that a status code was actually triggered (from execution results).
     */
    public void markTriggered(String apiKey, int statusCode) {
        ApiStatusCodeInfo info = apiCoverage.get(apiKey);
        if (info != null) {
            if (info.triggeredCodes.add(statusCode)) {
                log.info("Status code {} triggered for {} (coverage: {}/{})", 
                    statusCode, apiKey, info.triggeredCodes.size(), info.discoveredCodes.size());
            }
        } else {
            log.warn("Cannot mark triggered: API {} not registered", apiKey);
        }
    }
    
    /**
     * Record that we're attempting to trigger a status code (before execution).
     * Used for round-robin to prevent targeting the same code twice in one round.
     */
    public void markTargeted(String apiKey, int statusCode) {
        ApiStatusCodeInfo info = apiCoverage.get(apiKey);
        if (info != null) {
            info.targetedCodes.add(statusCode);
            log.debug("Status code {} targeted for {} (will attempt to trigger)", statusCode, apiKey);
        } else {
            log.warn("Cannot mark targeted: API {} not registered", apiKey);
        }
    }
    
    /**
     * Check if a status code has been triggered.
     */
    public boolean isTriggered(String apiKey, int statusCode) {
        ApiStatusCodeInfo info = apiCoverage.get(apiKey);
        return info != null && info.triggeredCodes.contains(statusCode);
    }
    
    /**
     * Check if a status code has been targeted this round.
     */
    public boolean isTargeted(String apiKey, int statusCode) {
        ApiStatusCodeInfo info = apiCoverage.get(apiKey);
        return info != null && info.targetedCodes.contains(statusCode);
    }
    
    /**
     * Check if a status code is available for exploration (not triggered, not targeted).
     */
    public boolean isAvailableForExploration(String apiKey, int statusCode) {
        return !isTriggered(apiKey, statusCode) && !isTargeted(apiKey, statusCode);
    }
    
    /**
     * Get status codes that haven't been triggered or targeted yet.
     */
    public List<StatusCodeTarget> getUntriggeredCodes(String apiKey) {
        ApiStatusCodeInfo info = apiCoverage.get(apiKey);
        if (info == null) {
            return Collections.emptyList();
        }
        
        return info.discoveredCodes.stream()
            .filter(t -> !info.triggeredCodes.contains(t.getStatusCode()))
            .filter(t -> !info.targetedCodes.contains(t.getStatusCode()))
            .collect(Collectors.toList());
    }
    
    /**
     * Get all discovered status code targets for an API.
     */
    public List<StatusCodeTarget> getDiscoveredCodes(String apiKey) {
        ApiStatusCodeInfo info = apiCoverage.get(apiKey);
        return info != null ? new ArrayList<>(info.discoveredCodes) : Collections.emptyList();
    }
    
    /**
     * Move a status code to the end of the round-robin list (for failed exploration attempts).
     * This allows it to be retried later, but gives priority to other codes first.
     */
    public void moveToEndOfRoundRobin(String apiKey, int statusCode) {
        ApiStatusCodeInfo info = apiCoverage.get(apiKey);
        if (info != null) {
            // Find the target in the list
            StatusCodeTarget targetToMove = null;
            int indexToRemove = -1;
            
            for (int i = 0; i < info.discoveredCodes.size(); i++) {
                if (info.discoveredCodes.get(i).getStatusCode() == statusCode) {
                    targetToMove = info.discoveredCodes.get(i);
                    indexToRemove = i;
                    break;
                }
            }
            
            if (targetToMove != null && indexToRemove >= 0) {
                // Remove from current position
                info.discoveredCodes.remove(indexToRemove);
                // Add to end of list
                info.discoveredCodes.add(targetToMove);
                // Remove from targeted set so it can be targeted again in future rounds
                info.targetedCodes.remove(statusCode);
                
                log.debug("Moved status {} to end of round-robin for {} (failed attempt)", statusCode, apiKey);
            }
        } else {
            log.warn("Cannot move to end: API {} not registered", apiKey);
        }
    }
    
    /**
     * Get details for a specific status code target.
     */
    public StatusCodeTarget getTargetDetails(String apiKey, int statusCode) {
        ApiStatusCodeInfo info = apiCoverage.get(apiKey);
        return info != null ? info.getTargetDetails(statusCode) : null;
    }
    
    /**
     * Start a new round. Resets targeted codes to allow retry of failed targets.
     * Triggered codes are preserved (they're actual successes).
     */
    public void startNewRound() {
        currentRound++;
        log.info("=== Starting Round {} ===", currentRound);
        
        for (ApiStatusCodeInfo info : apiCoverage.values()) {
            int previousTargeted = info.targetedCodes.size();
            info.targetedCodes.clear();
            
            if (previousTargeted > 0) {
                log.debug("Cleared {} targeted codes for {} (triggered codes preserved: {})",
                    previousTargeted, info.apiKey, info.triggeredCodes.size());
            }
        }
    }
    
    /**
     * Get the current round number.
     */
    public int getCurrentRound() {
        return currentRound;
    }
    
    /**
     * Check if an API has been registered.
     */
    public boolean hasApi(String apiKey) {
        return apiCoverage.containsKey(apiKey);
    }
    
    /**
     * Get all registered API keys.
     */
    public Set<String> getRegisteredApis() {
        return new HashSet<>(apiCoverage.keySet());
    }
    
    /**
     * Get coverage summary for all APIs.
     */
    public Map<String, CoverageSummary> getCoverageSummary() {
        Map<String, CoverageSummary> summary = new HashMap<>();
        for (ApiStatusCodeInfo info : apiCoverage.values()) {
            summary.put(info.getApiKey(), new CoverageSummary(info));
        }
        return summary;
    }
    
    /**
     * Get coverage summary for a specific API.
     */
    public CoverageSummary getCoverageSummary(String apiKey) {
        ApiStatusCodeInfo info = apiCoverage.get(apiKey);
        return info != null ? new CoverageSummary(info) : null;
    }
    
    /**
     * Get overall coverage statistics across all APIs.
     */
    public OverallCoverageSummary getOverallCoverageSummary() {
        return new OverallCoverageSummary(this);
    }
    
    /**
     * Print a coverage report to the log.
     */
    public void logCoverageReport() {
        log.info("=== STATUS CODE COVERAGE REPORT (Round {}) ===", currentRound);
        
        int totalDiscovered = 0;
        int totalTriggered = 0;
        
        for (ApiStatusCodeInfo info : apiCoverage.values()) {
            totalDiscovered += info.getDiscoveredCount();
            totalTriggered += info.getTriggeredCount();
            
            log.info("  {} : {}/{} ({}%)",
                info.getApiKey(),
                info.getTriggeredCount(),
                info.getDiscoveredCount(),
                String.format("%.1f", info.getCoveragePercentage()));
            
            if (info.getUntriggeredCount() > 0) {
                List<Integer> untriggered = info.getDiscoveredCodes().stream()
                    .map(StatusCodeTarget::getStatusCode)
                    .filter(code -> !info.getTriggeredCodes().contains(code))
                    .collect(Collectors.toList());
                log.info("    Untriggered: {}", untriggered);
            }
        }
        
        double overallPercentage = totalDiscovered > 0 ? 
            (totalTriggered * 100.0) / totalDiscovered : 100.0;
        
        log.info("  TOTAL: {}/{} ({}%)", totalTriggered, totalDiscovered, String.format("%.1f", overallPercentage));
        log.info("==========================================");
    }
    
    /**
     * Overall coverage statistics across all APIs.
     */
    public static class OverallCoverageSummary {
        public final int totalApis;
        public final int totalDiscovered;
        public final int totalTriggered;
        public final int totalUntriggered;
        public final double overallPercentage;
        public final int currentRound;
        
        public OverallCoverageSummary(StatusCodeCoverageTracker tracker) {
            this.totalApis = tracker.apiCoverage.size();
            this.currentRound = tracker.currentRound;
            
            int discovered = 0;
            int triggered = 0;
            
            for (ApiStatusCodeInfo info : tracker.apiCoverage.values()) {
                discovered += info.getDiscoveredCount();
                triggered += info.getTriggeredCount();
            }
            
            this.totalDiscovered = discovered;
            this.totalTriggered = triggered;
            this.totalUntriggered = discovered - triggered;
            this.overallPercentage = discovered > 0 ? (triggered * 100.0) / discovered : 100.0;
        }
        
        @Override
        public String toString() {
            return String.format("Round %d: %d APIs, %d/%d status codes triggered (%.1f%%)",
                currentRound, totalApis, totalTriggered, totalDiscovered, overallPercentage);
        }
    }
}
