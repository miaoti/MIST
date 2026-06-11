package io.mist.core.oracle.shape.invariant;

import com.google.gson.JsonObject;
import io.mist.core.oracle.shape.ShapeInvariant;
import io.mist.core.oracle.shape.ShapeInvariantStore;
import io.mist.core.oracle.shape.TraceModel;
import io.mist.core.oracle.shape.TraceShapeVerdict;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Timing-envelope invariant (Phase 2.D). Learns per-root-API total-duration
 * percentiles (p50/p95/p99) and a global per-span p99. Flags traces whose
 * total duration exceeds the learned p99 or whose any single span exceeds
 * 2x the per-span p99. Severity defaults to WARN because span duration is
 * a noisy signal in real microservice traffic.
 */
public final class TimingEnvelopeInvariant implements ShapeInvariant<TimingEnvelopeInvariant.Data> {

    public static final String KIND = "TIMING_ENVELOPE";
    public static final double SPAN_OUTLIER_MULTIPLIER = 2.0;

    private final String rootApiKey;
    private final Data data;
    private final TraceShapeVerdict.Severity severity;

    public TimingEnvelopeInvariant(String rootApiKey, Data data) {
        this(rootApiKey, data, TraceShapeVerdict.Severity.WARN);
    }

    public TimingEnvelopeInvariant(String rootApiKey, Data data, TraceShapeVerdict.Severity severity) {
        this.rootApiKey = rootApiKey;
        this.data = data == null ? Data.empty() : data;
        this.severity = severity;
    }

    @Override public String kind() { return KIND; }
    @Override public String rootApiKey() { return rootApiKey; }

    @Override
    public TraceShapeVerdict.InvariantOutcome evaluate(TraceModel trace) {
        if (data.totalP99 <= 0 && data.spanP99 <= 0) {
            return TraceShapeVerdict.InvariantOutcome.pass(KIND, rootApiKey, severity);
        }

        long totalDuration = 0L;
        long maxStart = Long.MIN_VALUE;
        long minStart = Long.MAX_VALUE;
        long maxEnd = Long.MIN_VALUE;
        for (TraceModel.Span s : trace.getSpans()) {
            if (s.durationMicros > 0) {
                String start = s.tags.get("startTime");
                if (start != null) {
                    try {
                        long st = Long.parseLong(start);
                        if (st < minStart) minStart = st;
                        if (st + s.durationMicros > maxEnd) maxEnd = st + s.durationMicros;
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        if (minStart != Long.MAX_VALUE && maxEnd != Long.MIN_VALUE) {
            totalDuration = maxEnd - minStart;
        } else {
            // Fallback when spans carry no startTime tag: longest root span duration.
            for (TraceModel.Span root : trace.roots()) {
                if (root.durationMicros > totalDuration) totalDuration = root.durationMicros;
            }
        }

        List<String> evidence = new ArrayList<>();
        List<String> details = new ArrayList<>();
        if (data.totalP99 > 0 && totalDuration > data.totalP99) {
            details.add("total=" + totalDuration + "µs > p99=" + data.totalP99);
        }

        if (data.spanP99 > 0) {
            long ceiling = (long) (data.spanP99 * SPAN_OUTLIER_MULTIPLIER);
            for (TraceModel.Span s : trace.getSpans()) {
                if (s.durationMicros > ceiling) {
                    evidence.add(s.spanId);
                    details.add("span " + s.spanId + " duration=" + s.durationMicros + "µs > 2*p99=" + ceiling);
                }
            }
        }

        if (details.isEmpty()) {
            return TraceShapeVerdict.InvariantOutcome.pass(KIND, rootApiKey, severity);
        }
        return TraceShapeVerdict.InvariantOutcome.fail(
                KIND, rootApiKey, severity, String.join("; ", details), evidence);
    }

    public static Data learn(List<TraceModel> goodTraces) {
        if (goodTraces == null || goodTraces.isEmpty()) return Data.empty();
        List<Long> totals = new ArrayList<>();
        List<Long> spans = new ArrayList<>();
        for (TraceModel tm : goodTraces) {
            long maxDuration = 0L;
            for (TraceModel.Span s : tm.getSpans()) {
                if (s.durationMicros > 0) {
                    spans.add(s.durationMicros);
                    if (s.parentSpanId == null && s.durationMicros > maxDuration) {
                        maxDuration = s.durationMicros;
                    }
                }
            }
            // Fallback: total duration ≈ longest span in trace when no clear root.
            if (maxDuration == 0L) {
                for (TraceModel.Span s : tm.getSpans()) {
                    if (s.durationMicros > maxDuration) maxDuration = s.durationMicros;
                }
            }
            if (maxDuration > 0L) totals.add(maxDuration);
        }
        Collections.sort(totals);
        Collections.sort(spans);
        return new Data(
                percentile(totals, 0.50),
                percentile(totals, 0.95),
                percentile(totals, 0.99),
                percentile(spans, 0.50),
                percentile(spans, 0.95),
                percentile(spans, 0.99));
    }

    private static long percentile(List<Long> sortedAsc, double p) {
        if (sortedAsc.isEmpty()) return 0L;
        int idx = (int) Math.ceil(p * sortedAsc.size()) - 1;
        if (idx < 0) idx = 0;
        if (idx >= sortedAsc.size()) idx = sortedAsc.size() - 1;
        return sortedAsc.get(idx);
    }

    public Data getData() { return data; }

    public static String storeKey(String rootApiKey) { return KIND + "::" + rootApiKey; }

    public void persist(ShapeInvariantStore store) {
        store.put(storeKey(rootApiKey), data.toJson());
    }

    public static TimingEnvelopeInvariant load(String rootApiKey, ShapeInvariantStore store) {
        JsonObject json = store.get(storeKey(rootApiKey));
        return new TimingEnvelopeInvariant(rootApiKey, json == null ? Data.empty() : Data.fromJson(json));
    }

    public static final class Data {
        public final long totalP50;
        public final long totalP95;
        public final long totalP99;
        public final long spanP50;
        public final long spanP95;
        public final long spanP99;

        public Data(long totalP50, long totalP95, long totalP99, long spanP50, long spanP95, long spanP99) {
            this.totalP50 = totalP50;
            this.totalP95 = totalP95;
            this.totalP99 = totalP99;
            this.spanP50 = spanP50;
            this.spanP95 = spanP95;
            this.spanP99 = spanP99;
        }

        public static Data empty() { return new Data(0, 0, 0, 0, 0, 0); }

        public JsonObject toJson() {
            JsonObject obj = new JsonObject();
            obj.addProperty("totalP50", totalP50);
            obj.addProperty("totalP95", totalP95);
            obj.addProperty("totalP99", totalP99);
            obj.addProperty("spanP50", spanP50);
            obj.addProperty("spanP95", spanP95);
            obj.addProperty("spanP99", spanP99);
            return obj;
        }

        public static Data fromJson(JsonObject obj) {
            if (obj == null) return empty();
            return new Data(
                    obj.has("totalP50") ? obj.get("totalP50").getAsLong() : 0,
                    obj.has("totalP95") ? obj.get("totalP95").getAsLong() : 0,
                    obj.has("totalP99") ? obj.get("totalP99").getAsLong() : 0,
                    obj.has("spanP50") ? obj.get("spanP50").getAsLong() : 0,
                    obj.has("spanP95") ? obj.get("spanP95").getAsLong() : 0,
                    obj.has("spanP99") ? obj.get("spanP99").getAsLong() : 0);
        }
    }
}
