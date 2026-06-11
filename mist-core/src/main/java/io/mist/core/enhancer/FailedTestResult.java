package io.mist.core.enhancer;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Captures the complete context of a failed test case for enhancement.
 * This data is sent to the LLM to suggest improved parameter values.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FailedTestResult {
    
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    
    // Test identification
    private String testClassName;
    private String testMethodName;
    private String scenarioName;
    
    // API information
    private String endpoint;
    private String httpMethod;
    private String serviceName;
    
    // Test type
    private boolean negativeTest;
    
    // For negative tests: which parameters are intentionally invalid (DO NOT CHANGE)
    private List<String> invalidParameters = new ArrayList<>();
    
    // Execution result
    private int actualStatusCode;
    private String responseBody;
    private String errorMessage;
    private String failureType;  // ASSERTION, EXCEPTION, TIMEOUT, etc.
    
    // Parameters used in the test
    private List<ParameterSnapshot> parameters = new ArrayList<>();
    
    // Step-level failure tracking for multi-root sequences
    private int failedStepIndex = -1;
    
    // Structurally locked parameters — names from StepCall.getParamDependencies().keySet()
    // These are JIT-wired to runtime capturedOutputs and must NEVER be modified by the enhancer.
    private Set<String> lockedDependencyParams = new HashSet<>();

    // True when the failing step executed in resilient bypass mode: it ran with a synthetic
    // fallback value because its upstream producer step had already failed.  Enhancing such
    // a test is meaningless — the root cause is upstream, not in this step's parameters.
    private boolean bypassTriggered = false;
    
    // Metadata
    private long executionTimestamp;
    private int enhancementRound;
    private String originalTestFile;
    
    // Default constructor for JSON deserialization
    public FailedTestResult() {
        this.executionTimestamp = System.currentTimeMillis();
    }
    
    /**
     * Check if this test failure is enhanceable.
     * <ul>
     *   <li>5xx errors are server bugs; changing inputs cannot fix them.</li>
     *   <li>Bypass-triggered failures ran with a synthetic fallback value because
     *       an upstream step had failed. The root cause is upstream; enhancing
     *       this step's parameters is pointless.</li>
     * </ul>
     */
    public boolean isEnhanceable() {
        return actualStatusCode < 500 && !bypassTriggered;
    }
    
    /**
     * Convert to JSON for LLM prompt.
     */
    public String toJson() {
        try {
            return objectMapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
    
    /**
     * Create a compact JSON suitable for LLM consumption.
     */
    public String toLLMPromptJson() {
        try {
            LLMPromptFormat prompt = new LLMPromptFormat();
            prompt.testName = testMethodName;
            prompt.endpoint = endpoint;
            prompt.method = httpMethod;
            prompt.isNegativeTest = negativeTest;
            prompt.actualStatus = actualStatusCode;
            prompt.responseMessage = responseBody;
            prompt.parameters = parameters;
            return objectMapper.writeValueAsString(prompt);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
    
    // Inner class for LLM-friendly format
    private static class LLMPromptFormat {
        public String testName;
        public String endpoint;
        public String method;
        public boolean isNegativeTest;
        public int actualStatus;
        public String responseMessage;
        public List<ParameterSnapshot> parameters;
    }
    
    // Builder pattern
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private final FailedTestResult result = new FailedTestResult();
        
        public Builder testClassName(String testClassName) {
            result.testClassName = testClassName;
            return this;
        }
        
        public Builder testMethodName(String testMethodName) {
            result.testMethodName = testMethodName;
            return this;
        }
        
        public Builder scenarioName(String scenarioName) {
            result.scenarioName = scenarioName;
            return this;
        }
        
        public Builder endpoint(String endpoint) {
            result.endpoint = endpoint;
            return this;
        }
        
        public Builder httpMethod(String httpMethod) {
            result.httpMethod = httpMethod;
            return this;
        }
        
        public Builder serviceName(String serviceName) {
            result.serviceName = serviceName;
            return this;
        }
        
        public Builder negativeTest(boolean negativeTest) {
            result.negativeTest = negativeTest;
            return this;
        }
        
        public Builder invalidParameters(List<String> invalidParameters) {
            result.invalidParameters = invalidParameters;
            return this;
        }
        
        public Builder addInvalidParameter(String invalidParam) {
            result.invalidParameters.add(invalidParam);
            return this;
        }
        
        public Builder actualStatusCode(int actualStatusCode) {
            result.actualStatusCode = actualStatusCode;
            return this;
        }
        
        public Builder responseBody(String responseBody) {
            result.responseBody = responseBody;
            return this;
        }
        
        public Builder errorMessage(String errorMessage) {
            result.errorMessage = errorMessage;
            return this;
        }
        
        public Builder failureType(String failureType) {
            result.failureType = failureType;
            return this;
        }
        
        public Builder parameters(List<ParameterSnapshot> parameters) {
            result.parameters = parameters;
            return this;
        }
        
        public Builder addParameter(ParameterSnapshot param) {
            result.parameters.add(param);
            return this;
        }
        
        public Builder executionTimestamp(long executionTimestamp) {
            result.executionTimestamp = executionTimestamp;
            return this;
        }
        
        public Builder enhancementRound(int enhancementRound) {
            result.enhancementRound = enhancementRound;
            return this;
        }
        
        public Builder originalTestFile(String originalTestFile) {
            result.originalTestFile = originalTestFile;
            return this;
        }
        
        public Builder failedStepIndex(int failedStepIndex) {
            result.failedStepIndex = failedStepIndex;
            return this;
        }
        
        public Builder lockedDependencyParams(Set<String> lockedDependencyParams) {
            result.lockedDependencyParams = lockedDependencyParams != null
                    ? new HashSet<>(lockedDependencyParams) : new HashSet<>();
            return this;
        }

        public Builder bypassTriggered(boolean bypassTriggered) {
            result.bypassTriggered = bypassTriggered;
            return this;
        }

        public FailedTestResult build() {
            return result;
        }
    }
    
    // Getters and Setters
    public String getTestClassName() { return testClassName; }
    public void setTestClassName(String testClassName) { this.testClassName = testClassName; }
    
    public String getTestMethodName() { return testMethodName; }
    public void setTestMethodName(String testMethodName) { this.testMethodName = testMethodName; }
    
    public String getScenarioName() { return scenarioName; }
    public void setScenarioName(String scenarioName) { this.scenarioName = scenarioName; }
    
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    
    public String getHttpMethod() { return httpMethod; }
    public void setHttpMethod(String httpMethod) { this.httpMethod = httpMethod; }
    
    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }
    
    public boolean isNegativeTest() { return negativeTest; }
    public void setNegativeTest(boolean negativeTest) { this.negativeTest = negativeTest; }
    
    public List<String> getInvalidParameters() { return invalidParameters; }
    public void setInvalidParameters(List<String> invalidParameters) { this.invalidParameters = invalidParameters; }
    
    public int getActualStatusCode() { return actualStatusCode; }
    public void setActualStatusCode(int actualStatusCode) { this.actualStatusCode = actualStatusCode; }
    
    public String getResponseBody() { return responseBody; }
    public void setResponseBody(String responseBody) { this.responseBody = responseBody; }
    
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    
    public String getFailureType() { return failureType; }
    public void setFailureType(String failureType) { this.failureType = failureType; }
    
    public List<ParameterSnapshot> getParameters() { return parameters; }
    public void setParameters(List<ParameterSnapshot> parameters) { this.parameters = parameters; }
    
    public long getExecutionTimestamp() { return executionTimestamp; }
    public void setExecutionTimestamp(long executionTimestamp) { this.executionTimestamp = executionTimestamp; }
    
    public int getEnhancementRound() { return enhancementRound; }
    public void setEnhancementRound(int enhancementRound) { this.enhancementRound = enhancementRound; }
    
    public String getOriginalTestFile() { return originalTestFile; }
    public void setOriginalTestFile(String originalTestFile) { this.originalTestFile = originalTestFile; }
    
    public int getFailedStepIndex() { return failedStepIndex; }
    public void setFailedStepIndex(int failedStepIndex) { this.failedStepIndex = failedStepIndex; }
    
    public Set<String> getLockedDependencyParams() { return lockedDependencyParams; }
    public void setLockedDependencyParams(Set<String> lockedDependencyParams) {
        this.lockedDependencyParams = lockedDependencyParams != null ? lockedDependencyParams : new HashSet<>();
    }

    public boolean isBypassTriggered() { return bypassTriggered; }
    public void setBypassTriggered(boolean bypassTriggered) { this.bypassTriggered = bypassTriggered; }
    
    @Override
    public String toString() {
        return "FailedTestResult{" +
                "testMethodName='" + testMethodName + '\'' +
                ", endpoint='" + endpoint + '\'' +
                ", actualStatusCode=" + actualStatusCode +
                ", negativeTest=" + negativeTest +
                ", parameters=" + parameters.size() +
                '}';
    }
}

