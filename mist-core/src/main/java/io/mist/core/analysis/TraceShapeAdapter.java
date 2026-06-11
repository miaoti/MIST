package io.mist.core.analysis;

import io.mist.core.oracle.shape.TraceModel;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adapter that converts the writer-side Jaeger trace JSON into a
 * {@link io.mist.core.oracle.shape.TraceModel} so the Trace Shape Oracle
 * can evaluate it. The mist-core library is module-pure (org.json POJO
 * only), so this glue lives in mist-restest-adapter where the writer
 * also lives.
 *
 * <p>The Jaeger payload shape this adapter consumes is the wrapper object
 * returned by {@code /api/traces/{id}} or one element of the {@code data}
 * array from {@code /api/traces?...}: a JSON object with {@code spans} and
 * (optionally) {@code processes} keys. This is the same shape the writer
 * stores as {@code globalBestTrace}.
 */
public final class TraceShapeAdapter {

    private TraceShapeAdapter() { }

    // FIXES.md F4: per-traceId cache. Jaeger traces are immutable once a
    // span tree is finalized, so the conversion is a pure function of the
    // traceId for our usage. Multi-step tests where the same parent
    // traceId appears in every step's pulled JSON now reuse the same
    // TraceModel instance instead of rebuilding it N times.
    //
    // Scope is JVM-wide. MistRunner.run() calls clearCache() at the start
    // of each test run so leftover entries from a prior run don't return
    // stale data — guards against JVMs that the harness reuses.
    private static final ConcurrentHashMap<String, TraceModel> CACHE = new ConcurrentHashMap<>();

    /**
     * Drop all cached {@link TraceModel} instances. Call at the start of a
     * test run; benign no-op otherwise.
     */
    public static void clearCache() {
        CACHE.clear();
    }

    /**
     * Test-only: count of currently-cached models.
     */
    static int cacheSize() {
        return CACHE.size();
    }

    /**
     * Convert one Jaeger trace JSON object into a {@link TraceModel}.
     *
     * <p>Returns a cached instance when one is already present for the same
     * {@code traceID}. The cache is keyed by traceId only; {@code rootApiKey}
     * does not participate (the conversion result is independent of it —
     * the parameter is retained on the signature for caller symmetry).
     *
     * @param jaegerTrace one trace wrapper ({@code spans}, optional
     *                    {@code processes}); never null
     * @param rootApiKey  unused by the conversion itself but retained on
     *                    the signature so callers in the generated test
     *                    code can pass the key through symmetrically (the
     *                    oracle takes the same string)
     */
    public static TraceModel toModel(JSONObject jaegerTrace, String rootApiKey) {
        if (jaegerTrace == null) {
            return new TraceModel("", new ArrayList<>());
        }
        String traceId = jaegerTrace.optString("traceID", jaegerTrace.optString("traceId", ""));
        if (!traceId.isEmpty()) {
            TraceModel cached = CACHE.get(traceId);
            if (cached != null) return cached;
        }
        TraceModel built = buildModel(jaegerTrace, traceId);
        if (!traceId.isEmpty()) {
            CACHE.put(traceId, built);
        }
        return built;
    }

    private static TraceModel buildModel(JSONObject jaegerTrace, String traceId) {
        JSONArray spans = jaegerTrace.optJSONArray("spans");
        if (spans == null) {
            return new TraceModel(traceId, new ArrayList<>());
        }
        JSONObject processes = jaegerTrace.optJSONObject("processes");

        List<TraceModel.Span> out = new ArrayList<>(spans.length());
        for (int i = 0; i < spans.length(); i++) {
            JSONObject spanObj = spans.optJSONObject(i);
            if (spanObj == null) continue;
            TraceModel.Span span = toSpan(spanObj, processes);
            if (span != null) out.add(span);
        }
        return new TraceModel(traceId, out);
    }

    private static TraceModel.Span toSpan(JSONObject obj, JSONObject processes) {
        String spanId = obj.optString("spanID", obj.optString("spanId", ""));
        if (spanId.isEmpty()) return null;

        String parentSpanId = obj.optString("parentSpanId", null);
        if (parentSpanId == null || parentSpanId.isEmpty()) {
            JSONArray refs = obj.optJSONArray("references");
            if (refs != null) {
                for (int k = 0; k < refs.length(); k++) {
                    JSONObject ref = refs.optJSONObject(k);
                    if (ref == null) continue;
                    if ("CHILD_OF".equalsIgnoreCase(ref.optString("refType"))) {
                        parentSpanId = ref.optString("spanID", null);
                        break;
                    }
                }
            }
        }
        if (parentSpanId != null && parentSpanId.isEmpty()) parentSpanId = null;

        // Service name: prefer span-local serviceName, otherwise look up via processID.
        String service = obj.optString("serviceName", "");
        if (service.isEmpty() && processes != null) {
            String procId = obj.optString("processID", null);
            if (procId != null && processes.has(procId)) {
                service = processes.getJSONObject(procId).optString("serviceName", "");
            }
        }
        if (service.isEmpty()) service = "unknown";

        String operation = obj.optString("operationName", "");
        long duration = obj.optLong("duration", -1L);

        Map<String, String> tags = new HashMap<>();
        int httpStatus = 0;
        String otelStatus = null;
        JSONArray tagsArr = obj.optJSONArray("tags");
        if (tagsArr != null) {
            for (int t = 0; t < tagsArr.length(); t++) {
                JSONObject tag = tagsArr.optJSONObject(t);
                if (tag == null) continue;
                String key = tag.optString("key", null);
                if (key == null) continue;
                Object value = tag.opt("value");
                String stringValue = value == null ? "" : value.toString();
                tags.put(key, stringValue);
                if ("http.status_code".equals(key)) {
                    try {
                        httpStatus = Integer.parseInt(stringValue);
                    } catch (NumberFormatException ignored) {
                        if (value instanceof Number) httpStatus = ((Number) value).intValue();
                    }
                } else if ("otel.status_code".equals(key)) {
                    otelStatus = stringValue;
                }
            }
        }
        // startTime is a top-level field on Jaeger spans, but TimingEnvelopeInvariant
        // reads it from the tags map. Mirror it across so duration percentiles work.
        long startTime = obj.optLong("startTime", -1L);
        if (startTime >= 0L) {
            tags.putIfAbsent("startTime", Long.toString(startTime));
        }

        return new TraceModel.Span(spanId, parentSpanId, service, operation,
                httpStatus, otelStatus, duration, tags);
    }
}
