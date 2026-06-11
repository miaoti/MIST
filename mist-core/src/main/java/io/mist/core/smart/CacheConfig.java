package io.mist.core.smart;

/**
 * Configuration for caching fetched input values
 */
public class CacheConfig {
    
    private boolean enabled;
    private int maxEntries;
    private int ttlSeconds;
    
    // Default constructor with sensible defaults
    public CacheConfig() {
        this.enabled = true;
        this.maxEntries = 1000;
        this.ttlSeconds = 300; // 5 minutes
    }
    
    // Full constructor
    public CacheConfig(boolean enabled, int maxEntries, int ttlSeconds) {
        this.enabled = enabled;
        this.maxEntries = maxEntries;
        this.ttlSeconds = ttlSeconds;
    }
    
    // Getters and setters
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    
    public int getMaxEntries() { return maxEntries; }
    public void setMaxEntries(int maxEntries) { this.maxEntries = maxEntries; }
    
    public int getTtlSeconds() { return ttlSeconds; }
    public void setTtlSeconds(int ttlSeconds) { this.ttlSeconds = ttlSeconds; }
    
    @Override
    public String toString() {
        return String.format(
            "CacheConfig{enabled=%s, maxEntries=%d, ttlSeconds=%d}",
            enabled, maxEntries, ttlSeconds
        );
    }
} 