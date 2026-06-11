package io.mist.cli;

import io.mist.core.multiservice.MicroserviceTestConfigurationIO;
import io.mist.core.spec.TestConfigurationObject;
import io.mist.core.spec.OpenAPISpecification;
import io.mist.core.registry.SemanticDependencyRegistry;
import io.mist.core.workflow.TraceWorkflowExtractor;
import io.mist.core.workflow.WorkflowScenario;

import java.io.FileInputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Standalone utility: rebuild the {@link SemanticDependencyRegistry} from the
 * same inputs the MST pipeline uses (OpenAPI spec + multi-service YAML config +
 * recorded trace directory) and dump it to {@code target/semantic-registry-dump.json}.
 *
 * <p>Exists so we can validate registry-build changes in seconds without paying
 * for the full generation + test-execution pipeline.  No LLM calls, no HTTP,
 * no test writing — just the registry.
 *
 * <p>Usage (from the repo root):
 * <pre>
 *   mvn -q compile
 *   mvn -q exec:java -Dexec.mainClass=io.mist.cli.SemanticRegistryDumper
 * </pre>
 */
public class SemanticRegistryDumper {

    private static final String OAS_PATH    = "src/main/resources/My-Example/trainticket/merged_openapi_spec 1.yaml";
    private static final String CONF_PATH   = "src/main/resources/My-Example/trainticket/real-system-conf.yaml";
    private static final String TRACE_PATH  = "src/main/resources/My-Example/trainticket/test-trace";
    private static final String OUTPUT_PATH = "target/semantic-registry-dump.json";

    public static void main(String[] args) throws Exception {
        System.out.println("=== Semantic Registry rebuild ===");
        System.out.println("OAS:    " + OAS_PATH);
        System.out.println("Conf:   " + CONF_PATH);
        System.out.println("Traces: " + TRACE_PATH);

        OpenAPISpecification spec = new OpenAPISpecification(OAS_PATH);

        Map<String, TestConfigurationObject> serviceConfigs;
        try (FileInputStream in = new FileInputStream(CONF_PATH)) {
            serviceConfigs = MicroserviceTestConfigurationIO.loadMultiServiceConfiguration(in);
        }
        System.out.println("Loaded " + serviceConfigs.size() + " services from config.");

        Map<String, OpenAPISpecification> serviceSpecs = new LinkedHashMap<>();
        for (String svc : serviceConfigs.keySet()) {
            serviceSpecs.put(svc, spec);
        }

        List<WorkflowScenario> scenarios = null;
        try {
            scenarios = TraceWorkflowExtractor.extractScenarios(TRACE_PATH);
            System.out.println("Extracted " + scenarios.size() + " trace scenarios.");
        } catch (Exception e) {
            System.out.println("Trace extraction failed (" + e.getMessage() + ") — rebuilding registry without trace-driven refinement.");
        }

        SemanticDependencyRegistry reg = SemanticDependencyRegistry.build(
                serviceConfigs,
                io.mist.cli.spi.PojoConverter.toOpenApiMap(serviceSpecs),
                scenarios);
        reg.dumpRegistryToFile(OUTPUT_PATH);

        System.out.println("Registry dumped to " + OUTPUT_PATH);
    }
}
