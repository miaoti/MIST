// src/main/java/es/us/isa/restest/specification/OpenAPIOperation.java
package io.mist.core.spec;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.servers.Server;
import java.util.*;

public class OpenAPIOperation {
    private final String path;
    private final String method;
    private final Operation operation;

    public OpenAPIOperation(String path, String method, Operation operation) {
        this.path = path;
        this.method = method;
        this.operation = operation;
    }

    public String getPath() { return path; }
    public String getMethod() { return method; }
    public String getOperationId() { return operation.getOperationId(); }
    public Object getExtension(String name) {
        return operation.getExtensions() != null
                ? operation.getExtensions().get(name)
                : null;
    }
    public List<String> getTags() { return operation.getTags(); }
    public List<Server> getServers() { return operation.getServers(); }
    /**
     * Returns the underlying swagger-core {@link Parameter} list. Callers
     * that want the RESTest-side {@code OpenAPIParameter} wrapper can wrap
     * each entry themselves; keeping this method swagger-typed lets
     * mist-core stay free of RESTest's spec-wrapper hierarchy.
     */
    public List<Parameter> getParameters() {
        return operation.getParameters() != null
                ? operation.getParameters()
                : Collections.emptyList();
    }
    public List<String> getResponseCodes() {
        return operation.getResponses() != null
                ? new ArrayList<>(operation.getResponses().keySet())
                : Collections.emptyList();
    }

    /** ← new: expose the requestBody so callers can inspect JSON schemas */
    public RequestBody getRequestBody() {
        return operation.getRequestBody();
    }
}
