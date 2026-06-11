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
 * Span-tree shape invariant (Phase 2.B). Learns, per root API, the set of
 * {@code (parent.service, child.service)} edges seen in at least K% of
 * known-good traces and the maximum observed direct fan-out at each tree
 * level. At evaluation time flags edges absent from the learned set,
 * required edges that are missing, and spans whose fan-out exceeds 3x the
 * learned maximum at the same level.
 */
public final class SpanTreeShapeInvariant implements ShapeInvariant<SpanTreeShapeInvariant.Data> {

    public static final String KIND = "SPAN_TREE_SHAPE";
    public static final double DEFAULT_FREQUENCY_THRESHOLD = 0.8;
    public static final double FAN_OUT_MULTIPLIER = 3.0;

    private final String rootApiKey;
    private final Data data;

    public SpanTreeShapeInvariant(String rootApiKey, Data data) {
        this.rootApiKey = rootApiKey;
        this.data = data == null ? Data.empty() : data;
    }

    @Override public String kind() { return KIND; }
    @Override public String rootApiKey() { return rootApiKey; }

    @Override
    public TraceShapeVerdict.InvariantOutcome evaluate(TraceModel trace) {
        // Empty model: no required edges to enforce and no fan-out limit -- treat as pass.
        if (data.requiredEdges.isEmpty() && data.observedEdges.isEmpty() && data.fanOutByLevel.isEmpty()) {
            return TraceShapeVerdict.InvariantOutcome.pass(KIND, rootApiKey, TraceShapeVerdict.Severity.ERROR);
        }

        Map<String, TraceModel.Span> idx = trace.spanIndex();
        Set<String> observedTraceEdges = new HashSet<>();
        Map<String, List<TraceModel.Span>> childrenByParent = new HashMap<>();
        for (TraceModel.Span s : trace.getSpans()) {
            if (s.parentSpanId == null) continue;
            TraceModel.Span parent = idx.get(s.parentSpanId);
            if (parent == null) continue;
            observedTraceEdges.add(edgeKey(parent.service, s.service));
            childrenByParent.computeIfAbsent(parent.spanId, k -> new ArrayList<>()).add(s);
        }

        List<String> unexpectedEdges = new ArrayList<>();
        for (String e : observedTraceEdges) {
            if (!data.observedEdges.contains(e)) unexpectedEdges.add(e);
        }
        Collections.sort(unexpectedEdges);

        List<String> missingEdges = new ArrayList<>();
        for (String e : data.requiredEdges) {
            if (!observedTraceEdges.contains(e)) missingEdges.add(e);
        }
        Collections.sort(missingEdges);

        // Walk from each root, comparing each span's children-count against the
        // learned ceiling for its depth. Evidence span IDs accumulate so the
        // verdict reader can jump straight to the offender.
        List<String> fanOutEvidence = new ArrayList<>();
        List<String> fanOutDetails = new ArrayList<>();
        for (TraceModel.Span root : trace.roots()) {
            walkFanOut(root, 0, childrenByParent, fanOutEvidence, fanOutDetails);
        }

        boolean passed = unexpectedEdges.isEmpty() && missingEdges.isEmpty() && fanOutEvidence.isEmpty();
        if (passed) {
            return TraceShapeVerdict.InvariantOutcome.pass(KIND, rootApiKey, TraceShapeVerdict.Severity.ERROR);
        }
        StringBuilder detail = new StringBuilder();
        if (!unexpectedEdges.isEmpty()) {
            detail.append("unexpected edges=").append(unexpectedEdges);
        }
        if (!missingEdges.isEmpty()) {
            if (detail.length() > 0) detail.append("; ");
            detail.append("missing required edges=").append(missingEdges);
        }
        if (!fanOutDetails.isEmpty()) {
            if (detail.length() > 0) detail.append("; ");
            detail.append("fan-out outliers=").append(fanOutDetails);
        }
        return TraceShapeVerdict.InvariantOutcome.fail(
                KIND, rootApiKey, TraceShapeVerdict.Severity.ERROR, detail.toString(), fanOutEvidence);
    }

    private void walkFanOut(TraceModel.Span span, int depth,
                            Map<String, List<TraceModel.Span>> childrenByParent,
                            List<String> evidence, List<String> details) {
        List<TraceModel.Span> kids = childrenByParent.getOrDefault(span.spanId, Collections.emptyList());
        Integer learnedMax = data.fanOutByLevel.get(depth);
        if (learnedMax != null && kids.size() > Math.max(1, (int) Math.ceil(learnedMax * FAN_OUT_MULTIPLIER))) {
            evidence.add(span.spanId);
            details.add("depth=" + depth + " kids=" + kids.size() + " learnedMax=" + learnedMax);
        }
        for (TraceModel.Span k : kids) {
            walkFanOut(k, depth + 1, childrenByParent, evidence, details);
        }
    }

    private static String edgeKey(String parentService, String childService) {
        return parentService + "->" + childService;
    }

    /**
     * Learn from a corpus of known-good traces. Edges seen in ≥ {@code threshold}
     * fraction of traces become "required"; edges seen in any trace become
     * "observed" (allowed). Fan-out maxima are recorded per depth.
     */
    public static Data learn(List<TraceModel> goodTraces, double threshold) {
        if (goodTraces == null || goodTraces.isEmpty()) return Data.empty();

        Map<String, Integer> edgeOccurrences = new HashMap<>();
        Set<String> observedEdges = new TreeSet<>();
        Map<Integer, Integer> fanOutMaxByLevel = new TreeMap<>();
        int totalTraces = goodTraces.size();

        for (TraceModel tm : goodTraces) {
            Set<String> traceEdges = new HashSet<>();
            Map<String, TraceModel.Span> idx = tm.spanIndex();
            Map<String, List<TraceModel.Span>> kids = new HashMap<>();
            for (TraceModel.Span s : tm.getSpans()) {
                if (s.parentSpanId == null) continue;
                TraceModel.Span parent = idx.get(s.parentSpanId);
                if (parent == null) continue;
                traceEdges.add(edgeKey(parent.service, s.service));
                kids.computeIfAbsent(parent.spanId, k -> new ArrayList<>()).add(s);
            }
            for (String e : traceEdges) {
                observedEdges.add(e);
                edgeOccurrences.merge(e, 1, Integer::sum);
            }
            for (TraceModel.Span root : tm.roots()) {
                accumulateFanOut(root, 0, kids, fanOutMaxByLevel);
            }
        }

        Set<String> requiredEdges = new TreeSet<>();
        int requiredCount = (int) Math.ceil(threshold * totalTraces);
        for (Map.Entry<String, Integer> e : edgeOccurrences.entrySet()) {
            if (e.getValue() >= requiredCount) requiredEdges.add(e.getKey());
        }

        return new Data(observedEdges, requiredEdges, fanOutMaxByLevel);
    }

    public static Data learn(List<TraceModel> goodTraces) {
        return learn(goodTraces, DEFAULT_FREQUENCY_THRESHOLD);
    }

    private static void accumulateFanOut(TraceModel.Span span, int depth,
                                         Map<String, List<TraceModel.Span>> childrenByParent,
                                         Map<Integer, Integer> maxByLevel) {
        List<TraceModel.Span> kids = childrenByParent.getOrDefault(span.spanId, Collections.emptyList());
        maxByLevel.merge(depth, kids.size(), Math::max);
        for (TraceModel.Span k : kids) {
            accumulateFanOut(k, depth + 1, childrenByParent, maxByLevel);
        }
    }

    public Data getData() { return data; }

    public static String storeKey(String rootApiKey) { return KIND + "::" + rootApiKey; }

    public void persist(ShapeInvariantStore store) {
        store.put(storeKey(rootApiKey), data.toJson());
    }

    public static SpanTreeShapeInvariant load(String rootApiKey, ShapeInvariantStore store) {
        JsonObject json = store.get(storeKey(rootApiKey));
        return new SpanTreeShapeInvariant(rootApiKey, json == null ? Data.empty() : Data.fromJson(json));
    }

    public static final class Data {
        final Set<String> observedEdges;
        final Set<String> requiredEdges;
        final Map<Integer, Integer> fanOutByLevel;

        Data(Set<String> observedEdges, Set<String> requiredEdges, Map<Integer, Integer> fanOutByLevel) {
            this.observedEdges = Collections.unmodifiableSet(new TreeSet<>(observedEdges));
            this.requiredEdges = Collections.unmodifiableSet(new TreeSet<>(requiredEdges));
            this.fanOutByLevel = Collections.unmodifiableMap(new TreeMap<>(fanOutByLevel));
        }

        public static Data empty() {
            return new Data(Collections.emptySet(), Collections.emptySet(), Collections.emptyMap());
        }

        public Set<String> getObservedEdges() { return observedEdges; }
        public Set<String> getRequiredEdges() { return requiredEdges; }
        public Map<Integer, Integer> getFanOutByLevel() { return fanOutByLevel; }

        public JsonObject toJson() {
            JsonObject obj = new JsonObject();
            JsonArray oe = new JsonArray();
            for (String e : observedEdges) oe.add(e);
            obj.add("observedEdges", oe);
            JsonArray re = new JsonArray();
            for (String e : requiredEdges) re.add(e);
            obj.add("requiredEdges", re);
            JsonObject fo = new JsonObject();
            for (Map.Entry<Integer, Integer> e : fanOutByLevel.entrySet()) {
                fo.addProperty(String.valueOf(e.getKey()), e.getValue());
            }
            obj.add("fanOutByLevel", fo);
            return obj;
        }

        public static Data fromJson(JsonObject obj) {
            if (obj == null) return empty();
            Set<String> obs = new TreeSet<>();
            if (obj.has("observedEdges")) {
                for (JsonElement el : obj.getAsJsonArray("observedEdges")) obs.add(el.getAsString());
            }
            Set<String> req = new TreeSet<>();
            if (obj.has("requiredEdges")) {
                for (JsonElement el : obj.getAsJsonArray("requiredEdges")) req.add(el.getAsString());
            }
            Map<Integer, Integer> fo = new TreeMap<>();
            if (obj.has("fanOutByLevel")) {
                JsonObject foObj = obj.getAsJsonObject("fanOutByLevel");
                for (Map.Entry<String, JsonElement> e : foObj.entrySet()) {
                    fo.put(Integer.parseInt(e.getKey()), e.getValue().getAsInt());
                }
            }
            return new Data(obs, req, fo);
        }
    }
}
