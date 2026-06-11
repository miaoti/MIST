package io.mist.core.multiservice;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Multi-service test configuration: maps service names to their list of operations. */
public class MultiServiceTestConfiguration {
    private Map<String, List<OperationConfig>> services = new LinkedHashMap<>();

    public Map<String, List<OperationConfig>> getServices() { return services; }
    public void setServices(Map<String, List<OperationConfig>> services) { this.services = services; }

    public void addServiceConfig(String serviceName, List<OperationConfig> operations) {
        this.services.put(serviceName, operations);
    }
}
