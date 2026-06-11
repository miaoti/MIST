package io.mist.core.smart;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Represents a mapping from a parameter to a source API that can provide realistic values
 */
public class ApiMapping {
    
    private String endpoint;
    private String method;
    private String service;
    private String extractPath;  // JSONPath expression to extract values
    private int priority;        // Higher values = higher priority
    private LocalDateTime lastUsed;
    private double successRate;  // 0.0 to 1.0
    private String description;
    /**
     * Consumer API key (e.g., {@code "POST /api/v1/orderservice/orders"}) that triggered
     * the discovery of this mapping. {@code null} means "global" — this mapping applies to
     * any consumer of the parameter. (Bug audit Finding #15 + Reviewer Comment 2.)
     *
     * <p>The persisted YAML keeps {@code parameterMappings} keyed by bare parameter name for
     * back-compat (preserving existing learning at registry-cleanup time), but each entry
     * now records its consumer scope. {@link InputFetchRegistry#getMappingsForParameter}
     * returns scoped mappings ahead of global ones, so two operations sharing parameter
     * name {@code id} no longer pollute each other's candidate list.</p>
     */
    private String consumerApiKey;
    
    // Default constructor
    public ApiMapping() {
        this.method = "GET";
        this.priority = 5; // Will be overridden by configuration
        this.successRate = 0.0;
        this.lastUsed = LocalDateTime.now();
    }
    
    // Constructor with configurable priority
    public ApiMapping(int defaultPriority) {
        this.method = "GET";
        this.priority = defaultPriority;
        this.successRate = 0.0;
        this.lastUsed = LocalDateTime.now();
    }
    
    // Constructor with essential fields
    public ApiMapping(String endpoint, String service, String extractPath) {
        this();
        this.endpoint = endpoint;
        this.service = service;
        this.extractPath = extractPath;
    }
    
    // Full constructor
    public ApiMapping(String endpoint, String method, String service, String extractPath, 
                     int priority, double successRate) {
        this.endpoint = endpoint;
        this.method = method;
        this.service = service;
        this.extractPath = extractPath;
        this.priority = priority;
        this.successRate = successRate;
        this.lastUsed = LocalDateTime.now();
    }
    
    /**
     * Default decay window in days for the recentness axis of {@link #calculateScore()}.
     * A mapping last used today scores 1.0 on recentness; one last used {@code DECAY_DAYS}+
     * days ago scores 0.0. Configurable per-instance via {@link #setDecayDays(int)}.
     */
    private static final int DEFAULT_DECAY_DAYS = 30;
    private int decayDays = DEFAULT_DECAY_DAYS;

    /**
     * Default EMA learning rate; bigger values let a fresh upstream recover faster
     * after a streak of failures (e.g. when the SUT was temporarily down).
     */
    private static final double DEFAULT_EMA_ALPHA = 0.1;
    private double emaAlpha = DEFAULT_EMA_ALPHA;

    /**
     * Update success rate based on fetch result.
     */
    public void updateSuccessRate(boolean success) {
        this.successRate = emaAlpha * (success ? 1.0 : 0.0) + (1 - emaAlpha) * this.successRate;
        this.lastUsed = LocalDateTime.now();
    }

    public int getDecayDays() { return decayDays; }
    public void setDecayDays(int decayDays) {
        if (decayDays > 0) this.decayDays = decayDays;
    }

    public double getEmaAlpha() { return emaAlpha; }
    public void setEmaAlpha(double emaAlpha) {
        if (emaAlpha > 0.0 && emaAlpha <= 1.0) this.emaAlpha = emaAlpha;
    }

    /**
     * Calculate overall score for ranking APIs.
     *
     * Recentness uses {@link ChronoUnit#DAYS} between {@code lastUsed} and now; the prior
     * implementation divided {@code LocalDateTime.compareTo} (which returns -1/0/+1) by 86400,
     * which made recentnessScore ≈ 1.0 for every mapping regardless of staleness — defeating
     * the whole {@code lastUsed} field. (Bug audit Finding #1.)
     */
    public double calculateScore() {
        double priorityScore = priority / 10.0;
        long daysSince = lastUsed != null ? ChronoUnit.DAYS.between(lastUsed, LocalDateTime.now()) : decayDays;
        // Grounding fix B: reward recent *successful use*, not recent *creation*. A mapping that has never
        // succeeded against the SUT (successRate==0, e.g. a brand-new LLM-discovered producer) gets no
        // recentness bonus, so a fresh wrong-producer guess cannot outrank a known producer on freshness alone.
        double recentnessScore = successRate > 0.0
                ? Math.max(0.0, 1.0 - (double) daysSince / decayDays)
                : 0.0;
        return (0.5 * priorityScore) + (0.3 * successRate) + (0.2 * recentnessScore);
    }
    
    // Getters and setters
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    
    public String getService() { return service; }
    public void setService(String service) { this.service = service; }
    
    public String getExtractPath() { return extractPath; }
    public void setExtractPath(String extractPath) { this.extractPath = extractPath; }
    
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
    
    public LocalDateTime getLastUsed() { return lastUsed; }
    public void setLastUsed(LocalDateTime lastUsed) { this.lastUsed = lastUsed; }
    
    public double getSuccessRate() { return successRate; }
    public void setSuccessRate(double successRate) { this.successRate = successRate; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getConsumerApiKey() { return consumerApiKey; }
    public void setConsumerApiKey(String consumerApiKey) { this.consumerApiKey = consumerApiKey; }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ApiMapping that = (ApiMapping) o;
        return Objects.equals(endpoint, that.endpoint) &&
               Objects.equals(method, that.method) &&
               Objects.equals(service, that.service) &&
               Objects.equals(extractPath, that.extractPath);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(endpoint, method, service, extractPath);
    }
    
    @Override
    public String toString() {
        return String.format(
            "ApiMapping{endpoint='%s', method='%s', service='%s', extractPath='%s', " +
            "priority=%d, successRate=%.2f, score=%.2f}",
            endpoint, method, service, extractPath, priority, successRate, calculateScore()
        );
    }
}