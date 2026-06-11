package io.mist.core.multiservice;

import io.mist.core.spec.OpenAPIOperation;
import io.mist.core.spec.OpenAPIParameter;
import io.mist.core.spec.OpenAPISpecification;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Generates a multi-service ("per-service") test configuration from an OpenAPI
 * specification. Each operation in the spec is mapped to a service (derived from
 * the {@code x-service-name} extension, tags, or server URL), and the resulting
 * {@link MultiServiceTestConfiguration} is serialized to a YAML file consumable
 * by the MIST runner.
 *
 * <p>This is a RESTest-free reimplementation: it relies solely on
 * {@code io.mist.core.*}, {@code io.swagger.v3.*}, the JDK and SnakeYAML. Because
 * {@link io.mist.core.spec.OpenAPISpecification} parses with
 * {@code resolveFully=true}, {@code $ref}s are already inlined; the only residual
 * dereferencing handled here is the defensive lookup of a still-present
 * {@code $ref} against the components map.
 */
public class MicroserviceTestConfigurationGenerator {

    private final OpenAPISpecification openApiSpec;

    public MicroserviceTestConfigurationGenerator(OpenAPISpecification openApiSpec) {
        this.openApiSpec = openApiSpec;
    }

    /**
     * Generates a multi-service test configuration from the OpenAPI spec.
     *
     * @param outputYamlPath File path to write the configuration YAML.
     * @return the MultiServiceTestConfiguration object representing the config.
     * @throws IOException if writing to file fails.
     */
    public MultiServiceTestConfiguration generateTestConfiguration(String outputYamlPath) throws IOException {
        MultiServiceTestConfiguration multiConfig = new MultiServiceTestConfiguration();
        Map<String, List<OperationConfig>> opsByService = new LinkedHashMap<>();

        for (OpenAPIOperation apiOp : openApiSpec.getAllOperations()) {
            String serviceName = determineServiceName(apiOp);
            opsByService.computeIfAbsent(serviceName, k -> new ArrayList<>());

            OperationConfig opConfig = new OperationConfig();
            opConfig.setTestPath(applyBasePath(apiOp.getPath()));
            opConfig.setMethod(apiOp.getMethod().toLowerCase());
            String opId = apiOp.getOperationId();
            if (opId == null || opId.isEmpty()) {
                opId = (apiOp.getMethod() + " " + apiOp.getPath()).trim();
            }
            opConfig.setOperationId(opId);

            List<TestParameter> paramConfigs = new ArrayList<>();

            // 1) Existing path/query/header parameters. apiOp.getParameters()
            //    returns swagger's raw Parameter list; wrap each at this consumer
            //    boundary so toTestParam()'s signature stays unchanged.
            for (io.swagger.v3.oas.models.parameters.Parameter swParam : apiOp.getParameters()) {
                paramConfigs.add(toTestParam(new OpenAPIParameter(swParam)));
            }

            // 2) requestBody -> application/json schema (already $ref-resolved by
            //    the fully-resolving parser; defensive deref only as a fallback).
            RequestBody rb = apiOp.getRequestBody();
            if (rb != null) {
                RequestBody resolvedRb = rb;
                if (rb.get$ref() != null) {
                    String refName = rb.get$ref().replace("#/components/requestBodies/", "");
                    Set<String> availableRefs = (openApiSpec.getSpecification().getComponents() != null
                            && openApiSpec.getSpecification().getComponents().getRequestBodies() != null)
                            ? openApiSpec.getSpecification().getComponents().getRequestBodies().keySet()
                            : Collections.<String>emptySet();

                    // Handle service-specific prefix mapping
                    // (e.g., api_PriceInfo -> ts-admin-basic-info-service_PriceInfo)
                    String actualRefName = resolveServiceSpecificReference(refName, serviceName, availableRefs);

                    if (openApiSpec.getSpecification().getComponents() != null
                            && openApiSpec.getSpecification().getComponents().getRequestBodies() != null
                            && openApiSpec.getSpecification().getComponents().getRequestBodies().containsKey(actualRefName)) {
                        resolvedRb = openApiSpec.getSpecification().getComponents().getRequestBodies().get(actualRefName);
                    }
                }

                if (resolvedRb.getContent() != null && resolvedRb.getContent().containsKey("application/json")) {
                    MediaType mt = resolvedRb.getContent().get("application/json");
                    Schema<?> schema = mt.getSchema();

                    if (schema != null) {
                        Schema<?> resolvedSchema = resolveSchemaWithServiceMapping(
                                schema, serviceName, openApiSpec.getSpecification());

                        if (resolvedSchema != null) {
                            // Handle OBJECT schemas with properties.
                            if (resolvedSchema.getProperties() != null) {
                                List<String> requiredProps = resolvedSchema.getRequired() != null
                                        ? resolvedSchema.getRequired() : Collections.<String>emptyList();

                                @SuppressWarnings("unchecked")
                                Map<String, Schema<?>> props =
                                        (Map<String, Schema<?>>) (Map) resolvedSchema.getProperties();

                                for (Map.Entry<String, Schema<?>> pe : props.entrySet()) {
                                    String propName = pe.getKey();
                                    Schema<?> propSchema = pe.getValue();
                                    OpenAPIParameter bodyParam = new OpenAPIParameter(propName, propSchema,
                                            requiredProps.contains(propName));
                                    paramConfigs.add(toTestParam(bodyParam));
                                }
                            }
                            // Handle NON-OBJECT schemas (arrays, primitives, etc.).
                            else {
                                String bodyParamName = "body";
                                String description = resolvedRb.getDescription();
                                if (description == null || description.isEmpty()) {
                                    description = "Request body parameter";
                                }
                                boolean isRequired = resolvedRb.getRequired() != null && resolvedRb.getRequired();

                                OpenAPIParameter bodyParam = new OpenAPIParameter(bodyParamName, resolvedSchema, isRequired);
                                bodyParam.setDescription(description);
                                bodyParam.setIn("body");

                                paramConfigs.add(toTestParam(bodyParam));
                            }
                        }
                    }
                }
            }

            opConfig.setTestParameters(paramConfigs.isEmpty() ? null : paramConfigs);
            opConfig.setExpectedResponse(deriveExpectedResponse(apiOp));
            opsByService.get(serviceName).add(opConfig);
        }

        // Sort each service's operations by path.
        for (List<OperationConfig> list : opsByService.values()) {
            list.sort(Comparator.comparing(OperationConfig::getTestPath));
        }

        // Assemble and write YAML.
        for (Map.Entry<String, List<OperationConfig>> e : opsByService.entrySet()) {
            multiConfig.addServiceConfig(e.getKey(), e.getValue());
        }
        writeYamlFile(multiConfig, outputYamlPath);
        return multiConfig;
    }

    /**
     * Converts an {@link OpenAPIParameter} into a {@link TestParameter}, carrying
     * over description, type, format, pattern, enum, min/max, example, etc.
     */
    private TestParameter toTestParam(OpenAPIParameter apiParam) {
        TestParameter tp = new TestParameter();
        tp.setName(apiParam.getName());
        tp.setIn(apiParam.getIn());
        tp.setWeight(null);
        tp.setValid(true);
        tp.setDescription(apiParam.getDescription());
        tp.setType(apiParam.getType());
        tp.setFormat(apiParam.getFormat());
        tp.setPattern(apiParam.getPattern());
        tp.setEnumValues(apiParam.getEnumValues());
        tp.setMin(apiParam.getMin());
        tp.setMax(apiParam.getMax());
        tp.setMinLength(apiParam.getMinLength());
        tp.setMaxLength(apiParam.getMaxLength());
        tp.setExample(apiParam.getExample());
        tp.setRequired(apiParam.getRequired());

        List<ValueGenerator> gens = new ArrayList<>();
        gens.add(createDefaultGenerator(apiParam));
        tp.setGenerators(gens);
        return tp;
    }

    /**
     * Determines the service name for a given operation using OpenAPI metadata
     * (x-service-name extension, tags, or server URL).
     */
    private String determineServiceName(OpenAPIOperation apiOp) {
        // 1. Custom extension (e.g., x-service-name).
        Object serviceExt = apiOp.getExtension("x-service-name");
        if (serviceExt instanceof String && !((String) serviceExt).isEmpty()) {
            return ((String) serviceExt).trim();
        }

        // 2. Tags: prefer one ending with "service", else the first tag.
        List<String> tags = apiOp.getTags();
        if (tags != null && !tags.isEmpty()) {
            for (String tag : tags) {
                if (tag != null && tag.toLowerCase().endsWith("service")) {
                    return tag.trim();
                }
            }
            return tags.get(0).trim();
        }

        // 3. Server URL: derive from first path segment or host.
        if (apiOp.getServers() != null && !apiOp.getServers().isEmpty()) {
            String url = apiOp.getServers().get(0).getUrl();
            try {
                URL serverUrl = new URL(url);
                String host = serverUrl.getHost();
                String path = serverUrl.getPath();
                if (path != null && path.length() > 1) {
                    String[] segments = path.split("/");
                    if (segments.length > 1 && !segments[1].isEmpty()) {
                        return segments[1];
                    }
                }
                if (host != null && !host.isEmpty()) {
                    String[] hostParts = host.split("\\.");
                    if (hostParts.length > 0 && hostParts[0].length() > 0) {
                        return hostParts[0];
                    }
                }
            } catch (Exception e) {
                // URL parsing failed; fall through to default.
            }
        }

        // 4. Fallback.
        return "DefaultService";
    }

    /**
     * Prepend the spec's server base path (swagger 2.0 {@code basePath}, or an
     * OpenAPI 3.0 {@code servers[].url} path component) to an operation path, so the
     * generated {@code testPath} matches the URL a client/trace actually uses.
     * Without this, a SUT whose swagger declares a base path (e.g. Bookinfo
     * {@code basePath: /api/v1}, paths {@code /products}) produced testPaths like
     * {@code /products} that never matched the {@code /api/v1/products} in its traces
     * (the per-SUT "conf basePath" hand-fix). Idempotent (never double-prefixes); a
     * SUT whose paths already embed the base path (e.g. train-ticket) is unchanged.
     */
    private String applyBasePath(String path) {
        if (path == null) return null;
        String base = serverBasePath();
        if (base.isEmpty() || path.equals(base) || path.startsWith(base + "/")) {
            return path;
        }
        return base + path;
    }

    /**
     * The usable server base path, or "" when the spec has none. A relative server
     * URL ({@code /api/v1}) IS the base path; an absolute URL contributes its path
     * component. Junk/doc server URLs (e.g. train-ticket's Apache-license URL,
     * whose path ends in {@code .html}) are skipped so they are never mistaken for a
     * base path.
     */
    private String serverBasePath() {
        try {
            java.util.List<io.swagger.v3.oas.models.servers.Server> servers =
                    openApiSpec.getSpecification().getServers();
            if (servers == null) return "";
            for (io.swagger.v3.oas.models.servers.Server s : servers) {
                String url = (s == null) ? null : s.getUrl();
                if (url == null || url.trim().isEmpty()) continue;
                String p;
                if (url.startsWith("/")) {
                    p = url;                              // relative server URL == base path
                } else {
                    try { p = new URL(url).getPath(); }   // absolute: take the path component
                    catch (Exception e) { continue; }
                }
                if (p == null) continue;
                p = p.replaceAll("/+$", "");              // strip trailing slash(es)
                if (p.isEmpty() || !p.startsWith("/")) continue;
                String last = p.substring(p.lastIndexOf('/') + 1).toLowerCase();
                if (last.matches(".*\\.(html?|json|ya?ml|xml|txt|pdf)$")) continue; // license/doc URL
                return p;
            }
        } catch (Exception e) {
            // no usable base path
        }
        return "";
    }

    /**
     * Creates a default value generator for a parameter based on its schema
     * type/format.
     */
    private ValueGenerator createDefaultGenerator(OpenAPIParameter apiParam) {
        String type = apiParam.getType();
        ValueGenerator generator = new ValueGenerator();
        List<GenParam> genParams = new ArrayList<>();

        if (type == null) {
            type = "string";
        }
        switch (type.toLowerCase()) {
            case "integer":
            case "number":
                generator.setType("LLMParameterGenerator");
                GenParam typeParam = new GenParam();
                typeParam.setName("type");
                typeParam.setValues(Collections.singletonList("integer"));
                GenParam minParam = new GenParam();
                minParam.setName("min");
                minParam.setValues(Collections.singletonList("1"));
                GenParam maxParam = new GenParam();
                maxParam.setName("max");
                maxParam.setValues(Collections.singletonList("100"));
                genParams.add(typeParam);
                genParams.add(minParam);
                genParams.add(maxParam);
                break;
            case "boolean":
                generator.setType("LLMParameterGenerator");
                GenParam boolValues = new GenParam();
                boolValues.setName("values");
                boolValues.setValues(Arrays.asList("true", "false"));
                genParams.add(boolValues);
                break;
            default:
                generator.setType("LLMParameterGenerator");
                GenParam lengthParam = new GenParam();
                lengthParam.setName("length");
                lengthParam.setValues(Collections.singletonList("10"));
                genParams.add(lengthParam);
                generator.setDescription(apiParam.getDescription());
                break;
        }
        generator.setGenParameters(genParams);
        return generator;
    }

    /**
     * Derives a default expected response code for an operation (first 2xx, else
     * 200).
     */
    private Integer deriveExpectedResponse(OpenAPIOperation apiOp) {
        List<String> responseCodes = new ArrayList<>(apiOp.getResponseCodes());
        Collections.sort(responseCodes);
        for (String code : responseCodes) {
            if (code.startsWith("2")) {
                try {
                    return Integer.parseInt(code);
                } catch (NumberFormatException ignore) {
                    // not a numeric code; keep scanning.
                }
            }
        }
        return 200;
    }

    /**
     * Writes the {@link MultiServiceTestConfiguration} to a YAML file in block
     * style.
     */
    private void writeYamlFile(MultiServiceTestConfiguration config, String outputPath) throws IOException {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);
        Yaml yaml = new Yaml(options);

        Map<String, Object> top = new LinkedHashMap<>();
        Map<String, Object> servicesMap = new LinkedHashMap<>();

        for (Map.Entry<String, List<OperationConfig>> svcEntry : config.getServices().entrySet()) {
            String svcName = svcEntry.getKey();
            List<Map<String, Object>> opsList = new ArrayList<>();

            for (OperationConfig oc : svcEntry.getValue()) {
                Map<String, Object> opMap = new LinkedHashMap<>();
                opMap.put("expectedResponse", oc.getExpectedResponse());
                opMap.put("method", oc.getMethod());
                opMap.put("operationId", oc.getOperationId());
                opMap.put("testPath", oc.getTestPath());

                if (oc.getTestParameters() != null) {
                    List<Map<String, Object>> params = new ArrayList<>();
                    for (TestParameter tp : oc.getTestParameters()) {
                        Map<String, Object> p = new LinkedHashMap<>();
                        p.put("name", tp.getName());
                        p.put("in", tp.getIn());
                        p.put("weight", tp.getWeight());
                        p.put("description", tp.getDescription());
                        if (tp.getType() != null) p.put("type", tp.getType());
                        if (tp.getFormat() != null) p.put("format", tp.getFormat());
                        if (tp.getPattern() != null) p.put("pattern", tp.getPattern());
                        if (tp.getEnumValues() != null) p.put("enumValues", tp.getEnumValues());
                        if (tp.getMin() != null) p.put("minimum", tp.getMin());
                        if (tp.getMax() != null) p.put("maximum", tp.getMax());
                        if (tp.getMinLength() != null) p.put("minLength", tp.getMinLength());
                        if (tp.getMaxLength() != null) p.put("maxLength", tp.getMaxLength());
                        if (tp.getRequired() != null) p.put("required", tp.getRequired());

                        // Turn any Jackson ArrayNode/ObjectNode into plain Java.
                        Object example = tp.getExample();
                        if (example instanceof com.fasterxml.jackson.databind.JsonNode) {
                            example = convertJsonNode((com.fasterxml.jackson.databind.JsonNode) example);
                        }
                        if (example != null) {
                            p.put("example", example);
                        }

                        List<Map<String, Object>> gens = new ArrayList<>();
                        for (ValueGenerator vg : tp.getGenerators()) {
                            Map<String, Object> gm = new LinkedHashMap<>();
                            gm.put("type", vg.getType());
                            List<Map<String, Object>> gpList = new ArrayList<>();
                            for (GenParam gp : vg.getGenParameters()) {
                                Map<String, Object> single = new LinkedHashMap<>();
                                single.put("name", gp.getName());
                                single.put("values", gp.getValues());
                                gpList.add(single);
                            }
                            gm.put("genParameters", gpList);
                            gens.add(gm);
                        }
                        p.put("generators", gens);

                        params.add(p);
                    }
                    opMap.put("testParameters", params);
                } else {
                    opMap.put("testParameters", null);
                }

                opsList.add(opMap);
            }

            servicesMap.put(svcName, opsList);
        }

        top.put("testConfiguration", Collections.singletonMap("services", servicesMap));

        try (FileWriter writer = new FileWriter(outputPath)) {
            yaml.dump(top, writer);
        }
    }

    /** Recursively converts a Jackson JsonNode into plain Java types. */
    private Object convertJsonNode(com.fasterxml.jackson.databind.JsonNode node) {
        if (node.isObject()) {
            Map<String, Object> map = new LinkedHashMap<>();
            node.fieldNames().forEachRemaining(field ->
                    map.put(field, convertJsonNode(node.get(field)))
            );
            return map;
        }
        if (node.isArray()) {
            List<Object> list = new ArrayList<>();
            node.forEach(elem -> list.add(convertJsonNode(elem)));
            return list;
        }
        if (node.isTextual()) return node.asText();
        if (node.isNumber()) return node.numberValue();
        if (node.isBoolean()) return node.asBoolean();
        return node.toString();
    }

    /**
     * Resolves service-specific reference names. Maps generic references like
     * {@code api_PriceInfo} to actual service-specific names like
     * {@code ts-admin-basic-info-service_PriceInfo}.
     */
    private String resolveServiceSpecificReference(String genericRefName, String serviceName, Set<String> availableRefs) {
        if (availableRefs.contains(genericRefName)) {
            return genericRefName;
        }

        String serviceSpecificRef = serviceName + "_" + genericRefName.replace("api_", "");
        if (availableRefs.contains(serviceSpecificRef)) {
            return serviceSpecificRef;
        }

        String withoutApiPrefix = genericRefName.replace("api_", "");
        for (String ref : availableRefs) {
            if (ref.endsWith("_" + withoutApiPrefix)) {
                return ref;
            }
        }

        return genericRefName;
    }

    /**
     * Resolves a request-body schema, applying service-specific reference mapping
     * when a residual {@code $ref} is present.
     *
     * <p>The fully-resolving parser normally inlines {@code $ref}s, so the common
     * path simply returns the schema as-is. When a {@code $ref} survives, it is
     * dereferenced against the components/schemas map (with service-prefix
     * mapping) rather than via the (removed) RESTest {@code SchemaManager}.
     */
    private Schema<?> resolveSchemaWithServiceMapping(Schema<?> schema, String serviceName, OpenAPI spec) {
        if (schema.get$ref() == null) {
            return schema;
        }

        if (spec.getComponents() == null || spec.getComponents().getSchemas() == null) {
            return schema;
        }

        String refName = schema.get$ref().replace("#/components/schemas/", "");
        String actualRefName = resolveServiceSpecificReference(
                refName, serviceName, spec.getComponents().getSchemas().keySet());

        Schema<?> resolved = spec.getComponents().getSchemas().get(actualRefName);
        return resolved != null ? resolved : schema;
    }
}
