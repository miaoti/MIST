package io.mist.core.oracle.shape.invariant;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import io.mist.core.oracle.shape.ShapeInvariant;
import io.mist.core.oracle.shape.ShapeInvariantStore;
import io.mist.core.oracle.shape.TraceModel;
import io.mist.core.oracle.shape.TraceShapeVerdict;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Response-envelope invariant (Phase 2.E). Per root API the learner records the
 * set of {@code primaryField} values observed alongside a 2xx HTTP status in
 * the seed corpus (the {@code successSet}). At runtime, a 2xx span whose root
 * response body carries a {@code primaryField} value outside the success set
 * is flagged when the value is present in the {@code failureSet}.
 *
 * <p><b>Learn-only-from-observation (Phase 4.x).</b> When an {@link EnvelopeClassifier}
 * is wired in (via {@link #withClassifier}), an otherwise-unknown value triggers a
 * <em>single</em> classification call: the result is recorded into the live
 * success/failure set and (when a {@link ShapeInvariantStore} is supplied) persisted,
 * so every later occurrence of that value resolves from the cache with no further
 * call. This restores the runtime "first-2xx → classify → cache" behaviour of the
 * deleted {@code SoftErrorRuleCache}. With no classifier wired the invariant stays
 * permissive on unknown values (legacy behaviour preserved byte-for-byte).
 *
 * <p>Defaults: the primary field is {@code status} (override via
 * {@link #setPrimaryField(String)}). The root response body is read from the
 * span tag {@code http.response.body} on the root span.
 */
public final class ResponseEnvelopeInvariant implements ShapeInvariant<ResponseEnvelopeInvariant.Data> {

    public static final String KIND = "RESPONSE_ENVELOPE";
    public static final String DEFAULT_PRIMARY_FIELD = "status";

    /**
     * One-shot classifier for a previously-unseen {@code primaryField} value on a 2xx
     * response. Implemented in the LLM layer and injected at runtime so {@code mist-core}
     * stays free of a hard LLM dependency.
     */
    @FunctionalInterface
    public interface EnvelopeClassifier {
        /**
         * @return {@code Boolean.TRUE} if {@code observedValue} denotes a soft-error/failure,
         *         {@code Boolean.FALSE} if it denotes success, or {@code null} if the value
         *         could not be classified (the invariant then stays permissive).
         */
        Boolean classifyFailure(String rootApiKey, String primaryField, String observedValue, String fullBody);
    }

    private final String rootApiKey;
    private final Data data;
    private String primaryField;

    /** Mutable working sets seeded from {@link #data}; grown by on-the-fly classification. */
    private final Set<String> liveSuccess;
    private final Set<String> liveFailure;

    /** Optional runtime wiring (null => legacy permissive behaviour). */
    private EnvelopeClassifier classifier;
    private ShapeInvariantStore cacheStore;

    public ResponseEnvelopeInvariant(String rootApiKey, Data data) {
        this.rootApiKey = rootApiKey;
        this.data = data == null ? Data.empty() : data;
        this.primaryField = data == null || data.primaryField == null || data.primaryField.isEmpty()
                ? DEFAULT_PRIMARY_FIELD : data.primaryField;
        this.liveSuccess = new TreeSet<>(this.data.successSet);
        this.liveFailure = new TreeSet<>(this.data.failureSet);
    }

    /**
     * Wire a runtime classifier (and optional cache store). When set, an unknown 2xx
     * {@code primaryField} value is classified once and recorded; failures then fail the
     * invariant. Returns {@code this} for chaining.
     */
    public ResponseEnvelopeInvariant withClassifier(EnvelopeClassifier classifier, ShapeInvariantStore cacheStore) {
        this.classifier = classifier;
        this.cacheStore = cacheStore;
        return this;
    }

    public void setPrimaryField(String primaryField) {
        this.primaryField = primaryField == null || primaryField.isEmpty() ? DEFAULT_PRIMARY_FIELD : primaryField;
    }

    public String getPrimaryField() { return primaryField; }

    @Override public String kind() { return KIND; }
    @Override public String rootApiKey() { return rootApiKey; }

    @Override
    public TraceShapeVerdict.InvariantOutcome evaluate(TraceModel trace) {
        // With no learned data AND no classifier there is nothing to check; stay silent.
        if (liveSuccess.isEmpty() && liveFailure.isEmpty() && classifier == null) {
            return TraceShapeVerdict.InvariantOutcome.pass(KIND, rootApiKey, TraceShapeVerdict.Severity.ERROR);
        }
        List<String> evidence = new ArrayList<>();
        List<String> details = new ArrayList<>();
        boolean anyFailureSet = false;
        for (TraceModel.Span root : trace.roots()) {
            if (root.httpStatus < 200 || root.httpStatus >= 300) continue;
            String body = root.tags.get("http.response.body");
            if (body == null || body.isEmpty()) continue;
            String observed = extractPrimaryValue(body, primaryField);
            if (observed == null) continue;
            if (liveSuccess.contains(observed)) continue;
            if (liveFailure.contains(observed)) {
                evidence.add(root.spanId);
                details.add(primaryField + "=" + observed + " is in learned failureSet");
                anyFailureSet = true;
            } else if (classifier != null) {
                // First sighting of this value: classify once, record, and cache.
                Boolean isFailure;
                try {
                    isFailure = classifier.classifyFailure(rootApiKey, primaryField, observed, body);
                } catch (RuntimeException ex) {
                    isFailure = null; // a flaky classifier must not crash the oracle
                }
                if (isFailure == null) {
                    evidence.add(root.spanId);
                    details.add(primaryField + "=" + observed + " could not be classified");
                } else if (isFailure) {
                    liveFailure.add(observed);
                    persistLive();
                    evidence.add(root.spanId);
                    details.add(primaryField + "=" + observed + " classified as failure (LLM, cached)");
                    anyFailureSet = true;
                } else {
                    liveSuccess.add(observed);
                    persistLive();
                }
            } else {
                // Legacy permissive path: no classifier wired. Record the unknown value
                // as INFO so the next learner pass can pick it up, but do not fail.
                evidence.add(root.spanId);
                details.add(primaryField + "=" + observed + " is unknown (needs LLM classification)");
            }
        }
        if (evidence.isEmpty()) {
            return TraceShapeVerdict.InvariantOutcome.pass(KIND, rootApiKey, TraceShapeVerdict.Severity.ERROR);
        }
        if (anyFailureSet) {
            return TraceShapeVerdict.InvariantOutcome.fail(
                    KIND, rootApiKey, TraceShapeVerdict.Severity.ERROR,
                    String.join("; ", details), evidence);
        }
        return new TraceShapeVerdict.InvariantOutcome(
                KIND, rootApiKey, true, TraceShapeVerdict.Severity.INFO,
                String.join("; ", details), evidence);
    }

    /** Persist the current live sets back to the cache store, if one is wired. */
    private void persistLive() {
        if (cacheStore == null) return;
        Data updated = new Data(primaryField, liveSuccess, liveFailure);
        cacheStore.put(storeKey(rootApiKey), updated.toJson());
        cacheStore.flush();
    }

    /** Parse {@code body} and return the string form of {@code field}, or null. */
    public static String extractPrimaryValue(String body, String field) {
        if (body == null || body.isEmpty() || field == null || field.isEmpty()) return null;
        try {
            JsonElement el = new JsonParser().parse(body);
            if (!el.isJsonObject()) return null;
            JsonObject obj = el.getAsJsonObject();
            if (!obj.has(field)) return null;
            JsonElement v = obj.get(field);
            if (v.isJsonNull()) return "null";
            if (v.isJsonPrimitive()) return v.getAsString();
            return v.toString();
        } catch (JsonSyntaxException ex) {
            return null;
        }
    }

    public static Data learn(List<TraceModel> goodTraces, String primaryField) {
        if (goodTraces == null || goodTraces.isEmpty()) {
            return new Data(primaryField, Collections.emptySet(), Collections.emptySet());
        }
        Set<String> success = new TreeSet<>();
        for (TraceModel tm : goodTraces) {
            for (TraceModel.Span root : tm.roots()) {
                if (root.httpStatus < 200 || root.httpStatus >= 300) continue;
                String body = root.tags.get("http.response.body");
                if (body == null || body.isEmpty()) continue;
                String v = extractPrimaryValue(body, primaryField);
                if (v != null) success.add(v);
            }
        }
        return new Data(primaryField, success, Collections.emptySet());
    }

    public static Data learn(List<TraceModel> goodTraces) {
        return learn(goodTraces, DEFAULT_PRIMARY_FIELD);
    }

    public Data getData() { return data; }

    /** Live success set (seeded from learned data, grown by runtime classification). */
    public Set<String> getLiveSuccessSet() { return Collections.unmodifiableSet(liveSuccess); }

    /** Live failure set (seeded from learned data, grown by runtime classification). */
    public Set<String> getLiveFailureSet() { return Collections.unmodifiableSet(liveFailure); }

    public static String storeKey(String rootApiKey) { return KIND + "::" + rootApiKey; }

    public void persist(ShapeInvariantStore store) {
        store.put(storeKey(rootApiKey), data.toJson());
    }

    public static ResponseEnvelopeInvariant load(String rootApiKey, ShapeInvariantStore store) {
        JsonObject json = store.get(storeKey(rootApiKey));
        return new ResponseEnvelopeInvariant(rootApiKey, json == null ? Data.empty() : Data.fromJson(json));
    }

    public static final class Data {
        final String primaryField;
        final Set<String> successSet;
        final Set<String> failureSet;

        public Data(String primaryField, Set<String> successSet, Set<String> failureSet) {
            this.primaryField = primaryField == null || primaryField.isEmpty()
                    ? DEFAULT_PRIMARY_FIELD : primaryField;
            this.successSet = Collections.unmodifiableSet(new TreeSet<>(successSet == null ? new HashSet<>() : successSet));
            this.failureSet = Collections.unmodifiableSet(new TreeSet<>(failureSet == null ? new HashSet<>() : failureSet));
        }

        public static Data empty() {
            return new Data(DEFAULT_PRIMARY_FIELD, Collections.emptySet(), Collections.emptySet());
        }

        public String getPrimaryField() { return primaryField; }
        public Set<String> getSuccessSet() { return successSet; }
        public Set<String> getFailureSet() { return failureSet; }

        public JsonObject toJson() {
            JsonObject obj = new JsonObject();
            obj.addProperty("primaryField", primaryField);
            JsonArray succ = new JsonArray();
            for (String v : successSet) succ.add(v);
            obj.add("successSet", succ);
            JsonArray fail = new JsonArray();
            for (String v : failureSet) fail.add(v);
            obj.add("failureSet", fail);
            return obj;
        }

        public static Data fromJson(JsonObject obj) {
            if (obj == null) return empty();
            String field = obj.has("primaryField") ? obj.get("primaryField").getAsString() : DEFAULT_PRIMARY_FIELD;
            Set<String> succ = new TreeSet<>();
            if (obj.has("successSet")) {
                for (JsonElement el : obj.getAsJsonArray("successSet")) succ.add(el.getAsString());
            }
            Set<String> fail = new TreeSet<>();
            if (obj.has("failureSet")) {
                for (JsonElement el : obj.getAsJsonArray("failureSet")) fail.add(el.getAsString());
            }
            return new Data(field, succ, fail);
        }
    }
}
