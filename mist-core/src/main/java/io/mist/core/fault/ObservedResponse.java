package io.mist.core.fault;

/**
 * Minimal POJO representing one observed 4xx/5xx response the
 * {@link FaultMiner} mines for SUT-specific fault categories. Sourced from
 * {@code target/failed-tests.json} once the miner is wired in.
 */
public final class ObservedResponse {

    private final String apiKey;
    private final int statusCode;
    private final String responseBody;

    public ObservedResponse(String apiKey, int statusCode, String responseBody) {
        this.apiKey = apiKey;
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public String apiKey() {
        return apiKey;
    }

    public int statusCode() {
        return statusCode;
    }

    public String responseBody() {
        return responseBody;
    }
}
