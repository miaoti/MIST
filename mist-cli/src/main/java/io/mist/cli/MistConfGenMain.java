package io.mist.cli;

import io.mist.core.multiservice.MicroserviceTestConfigurationGenerator;
import io.mist.core.multiservice.MicroserviceTestConfigurationIO;
import io.mist.core.multiservice.MultiServiceTestConfiguration;
import io.mist.core.spec.OpenAPISpecification;
import io.mist.core.spec.TestConfigurationObject;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * MIST per-service ("multi-service") configuration generator. Reads an OpenAPI
 * specification and emits a per-service test configuration YAML — the file that
 * the MIST runner's {@code conf.path} property points at.
 *
 * <p>This is a one-shot tool: run it once per OpenAPI spec change and save the
 * output where your {@code .properties} expects it.
 *
 * <p>Usage:
 * <pre>
 *   java -cp mist-cli/target/mist.jar io.mist.cli.MistConfGenMain \
 *        &lt;path/to/your-openapi.yaml&gt; &lt;path/to/output-conf.yaml&gt;
 * </pre>
 */
public final class MistConfGenMain {

    private MistConfGenMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            printUsage();
            System.exit(2);
            return;
        }

        Path oasPath = Paths.get(args[0]).toAbsolutePath().normalize();
        Path outPath = Paths.get(args[1]).toAbsolutePath().normalize();

        if (!Files.exists(oasPath)) {
            System.err.println("ERROR: OpenAPI spec not found: " + oasPath);
            System.err.println();
            printUsage();
            System.exit(2);
            return;
        }

        Path outDir = outPath.getParent();
        if (outDir != null && !Files.exists(outDir)) {
            Files.createDirectories(outDir);
        }

        System.out.println("MIST conf-gen: reading  " + oasPath);
        System.out.println("MIST conf-gen: writing  " + outPath);

        OpenAPISpecification spec = new OpenAPISpecification(oasPath.toString());
        MicroserviceTestConfigurationGenerator gen =
                new MicroserviceTestConfigurationGenerator(spec);
        MultiServiceTestConfiguration multiConfig =
                gen.generateTestConfiguration(outPath.toString());

        try (InputStream in = new FileInputStream(outPath.toString())) {
            Map<String, TestConfigurationObject> serviceConfigs =
                    MicroserviceTestConfigurationIO.loadMultiServiceConfiguration(in);
            int total = 0;
            for (TestConfigurationObject co : serviceConfigs.values()) {
                total += co.getTestConfiguration().getOperations().size();
            }
            System.out.println("MIST conf-gen: " + serviceConfigs.size()
                    + " service(s), " + total + " operation(s) written.");
            for (Map.Entry<String, TestConfigurationObject> e : serviceConfigs.entrySet()) {
                System.out.println("  - " + e.getKey()
                        + "  (" + e.getValue().getTestConfiguration().getOperations().size()
                        + " operations)");
            }
        } catch (NoSuchFileException nsfe) {
            System.err.println("WARN: configuration was written but could not be read back: "
                    + nsfe.getMessage());
        }
    }

    private static void printUsage() {
        System.err.println("Usage:");
        System.err.println("  java -cp mist-cli/target/mist.jar io.mist.cli.MistConfGenMain \\");
        System.err.println("       <input-openapi.yaml> <output-conf.yaml>");
    }
}
