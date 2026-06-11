package io.mist.core.smart;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Represents an error that occurred when using a specific parameter value
 * during test execution. This helps track common failure patterns for
 * parameter values to improve future test generation.
 */
public class ParameterError {
    
    private String errorType;
    private String errorReason;
    private String apiEndpoint;
    private String parameterName;
    private LocalDateTime timestamp;
    private String additionalInfo;
    
    public ParameterError() {
        this.timestamp = LocalDateTime.now();
    }
    
    public ParameterError(String errorType, String errorReason, String apiEndpoint, 
                         String parameterName, LocalDateTime timestamp, String additionalInfo) {
        this.errorType = errorType;
        this.errorReason = errorReason;
        this.apiEndpoint = apiEndpoint;
        this.parameterName = parameterName;
        this.timestamp = timestamp != null ? timestamp : LocalDateTime.now();
        this.additionalInfo = additionalInfo;
    }
    
    public ParameterError(String errorType, String errorReason, String apiEndpoint, String parameterName) {
        this(errorType, errorReason, apiEndpoint, parameterName, LocalDateTime.now(), null);
    }
    
    // Getters and setters
    public String getErrorType() { return errorType; }
    public void setErrorType(String errorType) { this.errorType = errorType; }
    
    public String getErrorReason() { return errorReason; }
    public void setErrorReason(String errorReason) { this.errorReason = errorReason; }
    
    public String getApiEndpoint() { return apiEndpoint; }
    public void setApiEndpoint(String apiEndpoint) { this.apiEndpoint = apiEndpoint; }
    
    public String getParameterName() { return parameterName; }
    public void setParameterName(String parameterName) { this.parameterName = parameterName; }
    
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    
    public String getAdditionalInfo() { return additionalInfo; }
    public void setAdditionalInfo(String additionalInfo) { this.additionalInfo = additionalInfo; }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ParameterError that = (ParameterError) o;
        return Objects.equals(errorType, that.errorType) &&
               Objects.equals(errorReason, that.errorReason) &&
               Objects.equals(apiEndpoint, that.apiEndpoint) &&
               Objects.equals(parameterName, that.parameterName);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(errorType, errorReason, apiEndpoint, parameterName);
    }
    
    @Override
    public String toString() {
        return String.format("ParameterError{errorType='%s', errorReason='%s', apiEndpoint='%s', parameterName='%s', timestamp=%s}", 
                           errorType, errorReason, apiEndpoint, parameterName, timestamp);
    }
}

