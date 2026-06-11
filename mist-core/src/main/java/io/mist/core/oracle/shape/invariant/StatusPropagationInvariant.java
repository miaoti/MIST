package io.mist.core.oracle.shape.invariant;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.mist.core.oracle.shape.ShapeInvariant;
import io.mist.core.oracle.shape.ShapeInvariantStore;
import io.mist.core.oracle.shape.TraceModel;
import io.mist.core.oracle.shape.TraceShapeVerdict;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Status-propagation invariant (Phase 2.C). For each tree depth the learner
 * records the set of known-good {@code http.status_code} values and
 * {@code otel.status_code} strings. At runtime any span carrying a code
 * outside the known set for its depth is flagged.
 */
public final class StatusPropagationInvariant implements ShapeInvariant<StatusPropagationInvariant.Data> {

    public static final String KIND = "STATUS_PROPAGATION";

    private final String rootApiKey;
    private final Data data;

    public StatusPropagationInvariant(String rootApiKey, Data data) {
        this.rootApiKey = rootApiKey;
        this.data = data == null ? Data.empty() : data;
    }

    @Override public String kind() { return KIND; }
    @Override public String rootApiKey() { return rootApiKey; }

    @Override
    public TraceShapeVerdict.InvariantOutcome evaluate(TraceModel trace) {
        if (data.httpStatusByDepth.isEmpty() && data.otelStatusByDepth.isEmpty()) {
            return TraceShapeVerdict.InvariantOutcome.pass(KIND, rootApiKey, TraceShapeVerdict.Severity.ERROR);
        }

        Map<String, TraceModel.Span> idx = trace.spanIndex();
        Map<String, List<TraceModel.Span>> kids = new HashMap<>();
        for (TraceModel.Span s : trace.getSpans()) {
            if (s.parentSpanId == null) continue;
            TraceModel.Span p = idx.get(s.parentSpanId);
            if (p == null) continue;
            kids.computeIfAbsent(p.spanId, k -> new ArrayList<>()).add(s);
        }

        List<String> evidence = new ArrayList<>();
        List<String> details = new ArrayList<>();
        for (TraceModel.Span root : trace.roots()) {
            walk(root, 0, kids, evidence, details);
        }
        if (evidence.isEmpty()) {
            return TraceShapeVerdict.InvariantOutcome.pass(KIND, rootApiKey, TraceShapeVerdict.Severity.ERROR);
        }
        return TraceShapeVerdict.InvariantOutcome.fail(
                KIND, rootApiKey, TraceShapeVerdict.Severity.ERROR, String.join("; ", details), evidence);
    }

    private void walk(TraceModel.Span span, int depth,
                      Map<String, List<TraceModel.Span>> kids,
                      List<String> evidence, List<String> details) {
        Set<Integer> allowedHttp = data.httpStatusByDepth.get(depth);
        // Depth-0 always has at least one learned status, so an unknown depth here = a deeper-than-trained tree.
        // We skip such depths (don't flag) so the invariant remains permissive on legitimate variation.
        if (allowedHttp != null && span.httpStatus > 0 && !allowedHttp.contains(span.httpStatus)) {
            evidence.add(span.spanId);
            details.add("depth=" + depth + " http=" + span.httpStatus + " expected=" + allowedHttp);
        }
        Set<String> allowedOtel = data.otelStatusByDepth.get(depth);
        if (allowedOtel != null && span.otelStatus != null && !allowedOtel.contains(span.otelStatus)) {
            evidence.add(span.spanId);
            details.add("depth=" + depth + " otel=" + span.otelStatus + " expected=" + allowedOtel);
        }
        List<TraceModel.Span> children = kids.getOrDefault(span.spanId, Collections.emptyList());
        for (TraceModel.Span k : children) {
            walk(k, depth + 1, kids, evidence, details);
        }
    }

    public static Data learn(List<TraceModel> goodTraces) {
        if (goodTraces == null || goodTraces.isEmpty()) return Data.empty();
        Map<Integer, Set<Integer>> http = new TreeMap<>();
        Map<Integer, Set<String>> otel = new TreeMap<>();
        for (TraceModel tm : goodTraces) {
            Map<String, TraceModel.Span> idx = tm.spanIndex();
            Map<String, List<TraceModel.Span>> kids = new HashMap<>();
            for (TraceModel.Span s : tm.getSpans()) {
                if (s.parentSpanId == null) continue;
                TraceModel.Span p = idx.get(s.parentSpanId);
                if (p == null) continue;
                kids.computeIfAbsent(p.spanId, k -> new ArrayList<>()).add(s);
            }
            for (TraceModel.Span root : tm.roots()) {
                accumulate(root, 0, kids, http, otel);
            }
        }
        return new Data(http, otel);
    }

    private static void accumulate(TraceModel.Span span, int depth,
                                   Map<String, List<TraceModel.Span>> kids,
                                   Map<Integer, Set<Integer>> http,
                                   Map<Integer, Set<String>> otel) {
        if (span.httpStatus > 0) {
            http.computeIfAbsent(depth, k -> new TreeSet<>()).add(span.httpStatus);
        }
        if (span.otelStatus != null && !span.otelStatus.isEmpty()) {
            otel.computeIfAbsent(depth, k -> new TreeSet<>()).add(span.otelStatus);
        }
        for (TraceModel.Span k : kids.getOrDefault(span.spanId, Collections.emptyList())) {
            accumulate(k, depth + 1, kids, http, otel);
        }
    }

    public Data getData() { return data; }

    public static String storeKey(String rootApiKey) { return KIND + "::" + rootApiKey; }

    public void persist(ShapeInvariantStore store) {
        store.put(storeKey(rootApiKey), data.toJson());
    }

    public static StatusPropagationInvariant load(String rootApiKey, ShapeInvariantStore store) {
        JsonObject json = store.get(storeKey(rootApiKey));
        return new StatusPropagationInvariant(rootApiKey, json == null ? Data.empty() : Data.fromJson(json));
    }

    public static final class Data {
        final Map<Integer, Set<Integer>> httpStatusByDepth;
        final Map<Integer, Set<String>> otelStatusByDepth;

        Data(Map<Integer, Set<Integer>> http, Map<Integer, Set<String>> otel) {
            this.httpStatusByDepth = deepCopyHttp(http);
            this.otelStatusByDepth = deepCopyOtel(otel);
        }

        public static Data empty() {
            return new Data(Collections.emptyMap(), Collections.emptyMap());
        }

        public Map<Integer, Set<Integer>> getHttpStatusByDepth() { return httpStatusByDepth; }
        public Map<Integer, Set<String>> getOtelStatusByDepth() { return otelStatusByDepth; }

        private static Map<Integer, Set<Integer>> deepCopyHttp(Map<Integer, Set<Integer>> src) {
            Map<Integer, Set<Integer>> out = new TreeMap<>();
            for (Map.Entry<Integer, Set<Integer>> e : src.entrySet()) {
                out.put(e.getKey(), Collections.unmodifiableSet(new TreeSet<>(e.getValue())));
            }
            return Collections.unmodifiableMap(out);
        }

        private static Map<Integer, Set<String>> deepCopyOtel(Map<Integer, Set<String>> src) {
            Map<Integer, Set<String>> out = new TreeMap<>();
            for (Map.Entry<Integer, Set<String>> e : src.entrySet()) {
                out.put(e.getKey(), Collections.unmodifiableSet(new TreeSet<>(e.getValue())));
            }
            return Collections.unmodifiableMap(out);
        }

        public JsonObject toJson() {
            JsonObject obj = new JsonObject();
            JsonObject http = new JsonObject();
            for (Map.Entry<Integer, Set<Integer>> e : httpStatusByDepth.entrySet()) {
                JsonArray arr = new JsonArray();
                for (Integer v : e.getValue()) arr.add(v);
                http.add(String.valueOf(e.getKey()), arr);
            }
            obj.add("httpStatusByDepth", http);
            JsonObject otel = new JsonObject();
            for (Map.Entry<Integer, Set<String>> e : otelStatusByDepth.entrySet()) {
                JsonArray arr = new JsonArray();
                for (String v : e.getValue()) arr.add(v);
                otel.add(String.valueOf(e.getKey()), arr);
            }
            obj.add("otelStatusByDepth", otel);
            return obj;
        }

        public static Data fromJson(JsonObject obj) {
            if (obj == null) return empty();
            Map<Integer, Set<Integer>> http = new TreeMap<>();
            if (obj.has("httpStatusByDepth")) {
                JsonObject inner = obj.getAsJsonObject("httpStatusByDepth");
                for (Map.Entry<String, JsonElement> e : inner.entrySet()) {
                    Set<Integer> set = new TreeSet<>();
                    for (JsonElement v : e.getValue().getAsJsonArray()) set.add(v.getAsInt());
                    http.put(Integer.parseInt(e.getKey()), set);
                }
            }
            Map<Integer, Set<String>> otel = new TreeMap<>();
            if (obj.has("otelStatusByDepth")) {
                JsonObject inner = obj.getAsJsonObject("otelStatusByDepth");
                for (Map.Entry<String, JsonElement> e : inner.entrySet()) {
                    Set<String> set = new HashSet<>();
                    for (JsonElement v : e.getValue().getAsJsonArray()) set.add(v.getAsString());
                    otel.put(Integer.parseInt(e.getKey()), set);
                }
            }
            return new Data(http, otel);
        }
    }
}
