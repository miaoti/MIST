package io.mist.core.fault;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal POJO the {@link FaultMiner} reads from. Holds the OpenAPI surface
 * the LLM needs to propose SUT-specific fault types — currently just parameter
 * descriptions; we extend with examples / enums once the miner is wired in.
 */
public final class SpecRef {

    private final String apiKey;
    private final Map<String, String> parameterDescriptions;

    public SpecRef(String apiKey, Map<String, String> parameterDescriptions) {
        this.apiKey = apiKey;
        this.parameterDescriptions = parameterDescriptions == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(parameterDescriptions));
    }

    public String apiKey() {
        return apiKey;
    }

    public Map<String, String> parameterDescriptions() {
        return parameterDescriptions;
    }
}
