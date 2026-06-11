import io.mist.core.oracle.shape.TraceShapeOracle;
import io.mist.core.oracle.shape.ShapeInvariantStore;
import io.mist.core.oracle.shape.TraceModel;
import io.mist.core.oracle.shape.TraceShapeVerdict;
import io.mist.core.generation.ZeroShotLLMGenerator;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Live evidence that MIST's shipped Trace Shape Oracle, with the real LLM-backed
 * ResponseEnvelope classifier, flips a soft-error response (HTTP 200 with a
 * failure-valued body) to RESPONSE_ENVELOPE=FAIL where a status-class oracle passes.
 *
 * It builds the exact runtime path the generated test uses: a 2xx root span carrying
 * the client response body, the oracle wired with an EnvelopeClassifier that delegates
 * to ZeroShotLLMGenerator.validateResponse (one real LLM call, then cached).
 *
 * Run (DeepSeek):
 *   java -cp mist-cli/target/mist.jar \
 *     -Dllm.openai_compatible.enabled=true \
 *     -Dllm.openai_compatible.url=https://api.deepseek.com/v1/chat/completions \
 *     -Dllm.openai_compatible.model=deepseek-chat \
 *     -Dllm.openai_compatible.api.key=$(cat .api_keys/DEEPSEEK_API_KEY) \
 *     evaluation/ResponseEnvelopeLiveCheck.java
 *
 * Default body is the TrainTicket adminroute soft error of the motivating example.
 */
public class ResponseEnvelopeLiveCheck {
    public static void main(String[] args) {
        String body = args.length > 0 ? args[0]
            : "{\"status\":0,\"msg\":\"start or end station not include in stationList.\",\"data\":null}";
        String rootApiKey = args.length > 1 ? args[1] : "POST /api/v1/adminrouteservice/adminroute";

        Map<String, String> tags = new HashMap<>();
        tags.put("http.method", "POST");
        tags.put("http.target", "/api/v1/adminrouteservice/adminroute");
        tags.put("http.status_code", "200");
        tags.put("http.response.body", body);
        TraceModel.Span root = new TraceModel.Span(
            "root", null, "ts-admin-route-service", rootApiKey, 200, null, 1000L, tags);
        TraceModel trace = new TraceModel("live-soft-error", Collections.singletonList(root));

        ZeroShotLLMGenerator llm = new ZeroShotLLMGenerator();
        // Fresh empty store each run so the value is classified by a live LLM call,
        // not resolved from a previously-cached failureSet.
        ShapeInvariantStore store;
        try {
            java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("re-live");
            store = new ShapeInvariantStore(dir.resolve("store.json"));
        } catch (java.io.IOException e) {
            store = new ShapeInvariantStore();
        }
        TraceShapeOracle oracle = new TraceShapeOracle(store)
            .setEnvelopeClassifier((api, field, value, b) -> {
                int sp = api == null ? -1 : api.indexOf(' ');
                String m = sp > 0 ? api.substring(0, sp) : "GET";
                String p = sp > 0 ? api.substring(sp + 1) : (api == null ? "" : api);
                ZeroShotLLMGenerator.ValidationResult vr = llm.validateResponse(200, b, "", m, p);
                return vr == null ? null : Boolean.valueOf(vr.isFailed());
            });

        System.out.println("Soft-error response (HTTP 200): " + body);
        System.out.println("Root API: " + rootApiKey);
        System.out.println("Status-class oracle: PASS (HTTP is 200)");
        TraceShapeVerdict verdict = oracle.evaluate(trace, rootApiKey, null, null);
        System.out.println("Trace Shape Oracle overall passed: " + verdict.isPassed());
        for (TraceShapeVerdict.InvariantOutcome o : verdict.getOutcomes()) {
            System.out.println("  " + o.kind + ": " + (o.passed ? "pass" : "FAIL")
                + "  severity=" + o.severity
                + (o.detail == null || o.detail.isEmpty() ? "" : "  detail=" + o.detail));
        }
    }
}
