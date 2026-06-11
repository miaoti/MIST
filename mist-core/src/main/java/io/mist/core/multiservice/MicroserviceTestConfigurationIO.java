package io.mist.core.multiservice;

import io.mist.core.spec.Auth;
import io.mist.core.spec.TestConfiguration;
import io.mist.core.spec.TestConfigurationObject;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.*;

public class MicroserviceTestConfigurationIO {

    /**
     * Loads a multi-service test configuration YAML and returns a map of service name to TestConfigurationObject.
     * Each TestConfigurationObject contains the operations for that service (and shared auth if applicable).
     * @param yamlStream Input stream of the YAML file (could also use a File path variant).
     * @return Map from service name to TestConfigurationObject for that service.
     */
    public static Map<String, TestConfigurationObject> loadMultiServiceConfiguration(InputStream yamlStream) {
        Yaml yaml = new Yaml();
        @SuppressWarnings("unchecked")
        Map<String,Object> root = yaml.load(yamlStream);
        Map<String, TestConfigurationObject> serviceConfigMap = new LinkedHashMap<>();
        if (root == null) return serviceConfigMap;

        Object tcNode = root.get("testConfiguration");
        if (!(tcNode instanceof Map)) return serviceConfigMap;
        @SuppressWarnings("unchecked")
        Map<String,Object> tcMap = (Map<String,Object>)tcNode;

        // global auth if present
        Auth globalAuth = null;
        if (root.get("auth") instanceof Map) {
            globalAuth = yaml.loadAs(yaml.dump(root.get("auth")), Auth.class);
        }

        Object servicesNode = tcMap.get("services");
        if (!(servicesNode instanceof Map)) return serviceConfigMap;
        @SuppressWarnings("unchecked")
        Map<String,Object> servicesMap = (Map<String,Object>)servicesNode;

        for (Map.Entry<String,Object> entry : servicesMap.entrySet()) {
            String serviceName = entry.getKey();
            Object serviceVal  = entry.getValue();
            List<?> opsList = null;

            // --- option A: serviceVal is directly the List of operations
            if (serviceVal instanceof List) {
                opsList = (List<?>) serviceVal;
            }
            // --- option B: serviceVal is a Map with an "operations" key
            else if (serviceVal instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String,Object> svcMap = (Map<String,Object>) serviceVal;
                Object maybeOps = svcMap.get("operations");
                if (maybeOps instanceof List) {
                    opsList = (List<?>) maybeOps;
                }
            }

            if (opsList == null) {
                // neither style matched, skip
                continue;
            }

            // Convert each operation‐entry (a Map) into your POJO.Operation
            TestConfiguration tc = new TestConfiguration();
            List<io.mist.core.spec.Operation> pojoOps = new ArrayList<>();

            for (Object opObj : opsList) {
                String singleYaml = yaml.dump(opObj);
                io.mist.core.spec.Operation pojoOp =
                        yaml.loadAs(singleYaml, io.mist.core.spec.Operation.class);
                pojoOps.add(pojoOp);
            }
            tc.setOperations(pojoOps);

            TestConfigurationObject tco = new TestConfigurationObject();
            tco.setTestConfiguration(tc);
            if (globalAuth != null) {
                tco.setAuth(globalAuth);
            }
            serviceConfigMap.put(serviceName, tco);
        }

        return serviceConfigMap;
    }
}
