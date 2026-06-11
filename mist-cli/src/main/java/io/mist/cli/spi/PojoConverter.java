package io.mist.cli.spi;

import io.mist.core.spec.OpenAPISpecification;
import io.swagger.v3.oas.models.OpenAPI;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Single surviving boundary helper after the RESTest sever:
 * unwrap a {@code Map<String, OpenAPISpecification>} (the MIST
 * parser wrapper) into a {@code Map<String, OpenAPI>} (the
 * swagger-core model) so {@code SemanticDependencyRegistry.build}
 * and {@code MistGenerator} can consume the raw swagger model.
 *
 * <p>All the RESTest-pojo conversion helpers this class used to
 * carry were removed alongside the RESTest source tree.
 */
public final class PojoConverter {

    private PojoConverter() {}

    public static Map<String, OpenAPI> toOpenApiMap(
            Map<String, OpenAPISpecification> src) {
        if (src == null) return null;
        Map<String, OpenAPI> dst = new LinkedHashMap<>(src.size());
        for (Map.Entry<String, OpenAPISpecification> e : src.entrySet()) {
            OpenAPISpecification spec = e.getValue();
            dst.put(e.getKey(), spec == null ? null : spec.getSpecification());
        }
        return dst;
    }
}
