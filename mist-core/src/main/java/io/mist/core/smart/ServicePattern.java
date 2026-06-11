package io.mist.core.smart;

import java.util.List;
import java.util.Objects;

/**
 * Represents a pattern for discovering APIs based on parameter names
 * Used for pattern-based matching of parameters to potential service endpoints
 */
public class ServicePattern {
    
    private String pattern;        // Regex pattern to match parameter names
    private List<String> services; // Services that might provide data for matching parameters
    private List<String> endpoints; // Endpoints within those services
    
    // Default constructor
    public ServicePattern() {
    }
    
    // Full constructor
    public ServicePattern(String pattern, List<String> services, List<String> endpoints) {
        this.pattern = pattern;
        this.services = services;
        this.endpoints = endpoints;
    }
    
    /**
     * Check if this pattern matches a parameter name
     */
    public boolean matches(String parameterName) {
        if (pattern == null || parameterName == null) {
            return false;
        }
        return parameterName.matches(pattern);
    }
    
    // Getters and setters
    public String getPattern() { return pattern; }
    public void setPattern(String pattern) { this.pattern = pattern; }
    
    public List<String> getServices() { return services; }
    public void setServices(List<String> services) { this.services = services; }
    
    public List<String> getEndpoints() { return endpoints; }
    public void setEndpoints(List<String> endpoints) { this.endpoints = endpoints; }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ServicePattern that = (ServicePattern) o;
        return Objects.equals(pattern, that.pattern) &&
               Objects.equals(services, that.services) &&
               Objects.equals(endpoints, that.endpoints);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(pattern, services, endpoints);
    }
    
    @Override
    public String toString() {
        return String.format(
            "ServicePattern{pattern='%s', services=%s, endpoints=%s}",
            pattern, services, endpoints
        );
    }
} 