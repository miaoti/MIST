package io.mist.core.multiservice;

import io.mist.core.spec.Operation;
import io.mist.core.spec.OpenAPISpecification;
import io.mist.core.spec.TestConfigurationObject;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.parameters.RequestBody;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Stage (i) reads a request body sent only as an HTML form (a Swagger 2.0 formData operation) into
 * named parameters and records the form media type, so the generator and writer send a form; a
 * JSON body is read as before and records no media type.
 */
public class MicroserviceTestConfigurationGeneratorFormTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static final String SPEC = String.join("\n",
            "swagger: '2.0'",
            "info: {title: shop, version: '1'}",
            "host: frontend",
            "consumes: [application/x-www-form-urlencoded]",
            "paths:",
            "  /cart:",
            "    post:",
            "      operationId: addToCart",
            "      parameters:",
            "        - {name: product_id, in: formData, required: true, type: string}",
            "        - {name: quantity, in: formData, required: true, type: integer}",
            "      responses: {'200': {description: ok}}",
            "  /orders:",
            "    post:",
            "      operationId: createOrder",
            "      consumes: [application/json]",
            "      parameters:",
            "        - name: body",
            "          in: body",
            "          required: true",
            "          schema: {type: object, properties: {item: {type: string}}}",
            "      responses: {'200': {description: ok}}",
            "");

    @Test
    public void formOperationGetsItsFieldsAndMediaType() throws Exception {
        Map<String, OperationConfig> ops = generate();
        OperationConfig cart = ops.get("addToCart");
        assertEquals("application/x-www-form-urlencoded", cart.getRequestContentType());
        assertEquals(List.of("product_id", "quantity"),
                cart.getTestParameters().stream().map(TestParameter::getName).collect(Collectors.toList()));
    }

    @Test
    public void jsonOperationRecordsNoMediaType() throws Exception {
        OperationConfig orders = generate().get("createOrder");
        assertNull(orders.getRequestContentType());
        assertEquals("item", orders.getTestParameters().get(0).getName());
    }

    @Test
    public void writtenConfigurationLoadsTheMediaType() throws Exception {
        File out = tmp.newFile("conf.yaml");
        new MicroserviceTestConfigurationGenerator(new OpenAPISpecification(specFile().getPath()))
                .generateTestConfiguration(out.getPath());
        Map<String, TestConfigurationObject> loaded;
        try (InputStream in = new FileInputStream(out)) {
            loaded = MicroserviceTestConfigurationIO.loadMultiServiceConfiguration(in);
        }
        Map<String, Operation> byId = loaded.values().stream()
                .flatMap(c -> c.getTestConfiguration().getOperations().stream())
                .collect(Collectors.toMap(Operation::getOperationId, o -> o));
        assertTrue(byId.get("addToCart").isFormRequestBody());
        assertFalse(byId.get("createOrder").isFormRequestBody());
    }

    @Test
    public void bodyMediaTypePrefersJsonThenForm() {
        assertEquals("application/json", MicroserviceTestConfigurationGenerator.bodyMediaType(
                body("application/x-www-form-urlencoded", "application/json")));
        assertEquals("application/x-www-form-urlencoded; charset=UTF-8",
                MicroserviceTestConfigurationGenerator.bodyMediaType(body("application/x-www-form-urlencoded; charset=UTF-8")));
        assertNull(MicroserviceTestConfigurationGenerator.bodyMediaType(body("multipart/form-data")));
        assertNull(MicroserviceTestConfigurationGenerator.bodyMediaType(null));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private File specFile() throws Exception {
        File f = new File(tmp.getRoot(), "spec.yaml");
        if (!f.exists()) {
            Files.write(f.toPath(), SPEC.getBytes(StandardCharsets.UTF_8));
        }
        return f;
    }

    private Map<String, OperationConfig> generate() throws Exception {
        MultiServiceTestConfiguration conf = new MicroserviceTestConfigurationGenerator(
                new OpenAPISpecification(specFile().getPath()))
                .generateTestConfiguration(new File(tmp.getRoot(), "gen.yaml").getPath());
        return conf.getServices().values().stream().flatMap(List::stream)
                .collect(Collectors.toMap(OperationConfig::getOperationId, o -> o));
    }

    private static RequestBody body(String... mediaTypes) {
        Content c = new Content();
        for (String m : mediaTypes) {
            c.addMediaType(m, new MediaType());
        }
        return new RequestBody().content(c);
    }
}
