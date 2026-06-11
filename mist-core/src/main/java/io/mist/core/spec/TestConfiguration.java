package io.mist.core.spec;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TestConfiguration {

    /**
     * key   = service name (user-service, order-service, …)
     * value = list of operation‐config maps
     */
    private Map<String,List<Map<String,Object>>> services;

    /** if you still need operations, keep this */
    private List<Operation> operations = null;

    public Map<String,List<Map<String,Object>>> getServices() {
        return services;
    }

    public void setServices(Map<String,List<Map<String,Object>>> services) {
        this.services = services;
    }

    public List<Operation> getOperations() {
        return operations;
    }

    public void setOperations(List<Operation> operations) {
        this.operations = operations;
    }
}
