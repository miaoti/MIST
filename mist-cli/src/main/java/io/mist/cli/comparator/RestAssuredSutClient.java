package io.mist.cli.comparator;

import io.mist.cli.auth.MstAuthHandler;
import io.restassured.RestAssured;

/**
 * Production {@link ContractEvaluator.SutClient}: auth-applied JSON calls
 * against the SUT. The comparator mode runs standalone (no generated test has
 * set {@code RestAssured.baseURI}), so the base URI comes from the
 * {@code base.url} property MistMain pushes to System properties.
 */
public final class RestAssuredSutClient implements ContractEvaluator.SutClient {

    public RestAssuredSutClient() {
        String baseUrl = System.getProperty("base.url");
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            throw new IllegalStateException("comparator mode needs base.url (set in the .properties file)");
        }
        RestAssured.baseURI = baseUrl.trim();
        // Review F6 (integration): bound the client — a black-holed gateway
        // request must not hang the calibration run.
        RestAssured.config = RestAssured.config().httpClient(
                io.restassured.config.HttpClientConfig.httpClientConfig()
                        .setParam("http.connection.timeout", 10_000)
                        .setParam("http.socket.timeout", 20_000));
    }

    @Override
    public ContractEvaluator.Response post(String path, String jsonBody) {
        io.restassured.response.Response response = MstAuthHandler
                .applyAuth(RestAssured.given().urlEncodingEnabled(false), path)
                .contentType("application/json")
                .body(jsonBody)
                .when().post(path);
        return new ContractEvaluator.Response(response.getStatusCode(), response.getBody().asString());
    }

    @Override
    public ContractEvaluator.Response get(String path) {
        io.restassured.response.Response response = MstAuthHandler
                .applyAuth(RestAssured.given().urlEncodingEnabled(false), path)
                .when().get(path);
        return new ContractEvaluator.Response(response.getStatusCode(), response.getBody().asString());
    }
}
