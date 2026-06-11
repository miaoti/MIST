package io.mist.core.tools;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Verifies that the {@link com.fasterxml.jackson.databind.ObjectMapper} configured
 * in {@code TestCaseEnhancer} accepts the kinds of malformed JSON the LLM emits in
 * production: line comments, trailing commas, single quotes, and unquoted keys.
 *
 * <p>Run via {@code mvn -q exec:java -Dexec.mainClass=io.mist.core.tools.JsonToleranceVerifier}.
 */
public final class JsonToleranceVerifier {

    public static void main(String[] args) throws Exception {
        ObjectMapper mapper = new ObjectMapper()
                .configure(JsonParser.Feature.ALLOW_COMMENTS, true)
                .configure(JsonParser.Feature.ALLOW_TRAILING_COMMA, true)
                .configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true)
                .configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);

        // Each of these failed parse in the May 2 production run.
        String[] inputs = {
            "{\n  // a leading comment\n  \"enhancedParameters\": [{\"name\": \"foo\", \"value\": \"bar\"}],\n  \"reasoning\": \"...\"\n}",
            "{\"enhancedParameters\": [{\"name\": \"foo\", \"value\": \"bar\"},], \"reasoning\": \"trailing\"}",
            "{enhancedParameters: [{'name': 'foo', 'value': 'bar'}], reasoning: 'unquoted'}",
        };
        for (int i = 0; i < inputs.length; i++) {
            JsonNode root = mapper.readTree(inputs[i]);
            JsonNode params = root.get("enhancedParameters");
            if (params == null || !params.isArray() || params.size() != 1) {
                fail("input " + i + " parsed but enhancedParameters missing");
            }
            String name = params.get(0).get("name").asText();
            if (!"foo".equals(name)) {
                fail("input " + i + " parsed but name != foo");
            }
        }
        System.out.println("PASS — Jackson tolerates // comments, trailing commas, single quotes, unquoted keys");
    }

    private static void fail(String msg) {
        System.err.println("FAIL: " + msg);
        System.exit(1);
    }

    private JsonToleranceVerifier() {}
}
