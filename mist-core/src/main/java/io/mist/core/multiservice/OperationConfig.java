package io.mist.core.multiservice;

import java.util.List;
import java.util.Map;

/** Configuration of a single API operation (similar to default monolithic Operation config). */
public class OperationConfig {
    private String testPath;          // e.g. "/api/books/{book-id}"
    private String operationId;       // e.g. "findBookById"
    private String method;            // e.g. "get", "post", etc.
    private List<TestParameter> testParameters; // Parameter configurations for this operation
    private Integer expectedResponse; // Expected HTTP status code (e.g. 200)

    // Getters and setters...
    public String getTestPath() { return testPath; }
    public void setTestPath(String testPath) { this.testPath = testPath; }
    public String getOperationId() { return operationId; }
    public void setOperationId(String operationId) { this.operationId = operationId; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public List<TestParameter> getTestParameters() { return testParameters; }
    public void setTestParameters(List<TestParameter> testParameters) { this.testParameters = testParameters; }
    public Integer getExpectedResponse() { return expectedResponse; }
    public void setExpectedResponse(Integer expectedResponse) { this.expectedResponse = expectedResponse; }
}
