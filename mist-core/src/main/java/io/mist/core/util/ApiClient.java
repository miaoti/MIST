package io.mist.core.util;

import okhttp3.*;
import okhttp3.internal.Util;
import org.json.JSONObject;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ApiClient {

    private static final MediaType JSON
            = MediaType.parse("application/json; charset=utf-8");

    /** ------------ CONFIG SECTION ------------- */
    private final Map<String,String> serviceUrlMap = new HashMap<>();

    /** client is thread‑safe; share for all requests */
    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30,   TimeUnit.SECONDS)
            .build();

    public ApiClient() {
        // ⬇️  **Replace with real base URLs**
        serviceUrlMap.put("order-service",   "http://localhost:8081");
        serviceUrlMap.put("payment-service", "http://localhost:8082");
        serviceUrlMap.put("inventory-service","http://localhost:8083");
    }

    /* -----------------------------------------------------------
       Top‑level entry point used by WorkflowTestCase.execute()
       ----------------------------------------------------------- */
    public ApiResponse call(String service,
                            String operationName,
                            Map<String,String> inputs,
                            Map<String,String> headers) throws IOException {

        // --- 1. parse "METHOD /path" -----------------------------
        String  method;
        String  rawPath;
        String[] parts = operationName.split("\\s+", 2);
        if (parts.length == 2) {
            method  = parts[0].toUpperCase(Locale.ROOT);
            rawPath = parts[1];
        } else {
            // default GET if method missing
            method  = "GET";
            rawPath = operationName.trim();
        }

        // --- 2. build URL, substitute {vars} --------------------
        String base = serviceUrlMap.getOrDefault(service, "");
        if (base.isEmpty()) {
            throw new IllegalStateException("No baseUrl configured for service '" + service + "'");
        }

        // replace {placeholders} in path
        Pattern p = Pattern.compile("\\{([^/}]+)}");
        Matcher m = p.matcher(rawPath);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String key = m.group(1);
            String val = inputs.getOrDefault(key, "");          // empty if not provided
            m.appendReplacement(sb, val);
            inputs.remove(key);                                 // don’t treat as query/body again
        }
        m.appendTail(sb);
        String path = sb.toString();

        // split queryable & body params
        HttpUrl.Builder urlBuilder = HttpUrl.parse(base + path).newBuilder();
        RequestBody     requestBody = null;

        if ("GET".equals(method) || "DELETE".equals(method)) {
            // attach remaining inputs as query parameters
            for (var e : inputs.entrySet()) {
                urlBuilder.addQueryParameter(e.getKey(), e.getValue());
            }
        } else {
            // send JSON body
            JSONObject json = new JSONObject(inputs);
            requestBody = RequestBody.create(json.toString(), JSON);
        }

        // --- 3. final Request -----------------------------------
        Request.Builder reqB = new Request.Builder()
                .url(urlBuilder.build());

        // Propagate a trace ID so we can later retrieve the spans
        String traceParent = headers.getOrDefault(
                "traceparent",
                TraceIdGenerator.newTraceParent());
        reqB.header("traceparent", traceParent);

        // extra caller‑supplied headers
        headers.forEach(reqB::header);

        switch (method) {
            case "POST":   reqB.post   (requestBody); break;
            case "PUT":    reqB.put    (requestBody); break;
            case "PATCH":  reqB.patch  (requestBody); break;
            case "DELETE": reqB.delete(requestBody == null
                    ? Util.EMPTY_REQUEST
                    : requestBody);          break;
            default:       reqB.get();                           break;
        }

        try (Response resp = http.newCall(reqB.build()).execute()) {
            int code = resp.code();
            String body = resp.body() != null ? resp.body().string() : "";
            return new ApiResponse(code, body, traceParent);
        }
    }

    /* -----------------------------------------------------------
       Generate a W3C traceparent (00‑<trace‑id>‑<span‑id>‑01)
       ----------------------------------------------------------- */
    private static final class TraceIdGenerator {
        private static final Random RAND = new Random();
        static String newTraceParent() {
            String traceId = randomHex(32);
            String spanId  = randomHex(16);
            return "00-" + traceId + "-" + spanId + "-01";
        }
        private static String randomHex(int len) {
            StringBuilder sb = new StringBuilder(len);
            for (int i = 0; i < len; i++)
                sb.append("0123456789abcdef".charAt(RAND.nextInt(16)));
            return sb.toString();
        }
    }
}
