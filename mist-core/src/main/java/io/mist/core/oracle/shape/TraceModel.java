package io.mist.core.oracle.shape;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal POJO view of an OpenTelemetry/Jaeger trace used by the Trace Shape
 * Oracle. The factory {@link #fromJaegerJson(Path)} handles the same three
 * file shapes accepted by {@code TraceWorkflowExtractor}: a top-level JSON
 * array of spans, a Jaeger wrapper object with {@code data[*].spans[]}, or
 * JSONL (one span per line). Only the fields the oracle uses are materialised.
 */
public final class TraceModel {

    private final String traceId;
    private final List<Span> spans;

    public TraceModel(String traceId, List<Span> spans) {
        this.traceId = traceId;
        this.spans = Collections.unmodifiableList(new ArrayList<>(spans));
    }

    public String getTraceId() { return traceId; }
    public List<Span> getSpans() { return spans; }

    /** Lookup helper: spanId -> Span. */
    public Map<String, Span> spanIndex() {
        Map<String, Span> idx = new HashMap<>();
        for (Span s : spans) {
            idx.put(s.spanId, s);
        }
        return idx;
    }

    /** Returns the roots (spans whose parent is absent from this trace). */
    public List<Span> roots() {
        Map<String, Span> idx = spanIndex();
        List<Span> roots = new ArrayList<>();
        for (Span s : spans) {
            if (s.parentSpanId == null || !idx.containsKey(s.parentSpanId)) {
                roots.add(s);
            }
        }
        return roots;
    }

    /**
     * Parse a single Jaeger/JSONL file into one or more {@link TraceModel}s.
     * A wrapper file with multiple traces yields one model per traceID.
     */
    public static List<TraceModel> fromJaegerJson(Path file) throws IOException {
        List<JSONObject> spanObjects = readSpans(file);
        Map<String, List<JSONObject>> byTrace = new HashMap<>();
        for (JSONObject obj : spanObjects) {
            String tid = obj.optString("traceId", obj.optString("traceID", null));
            if (tid == null || tid.isEmpty()) continue;
            byTrace.computeIfAbsent(tid, k -> new ArrayList<>()).add(obj);
        }
        List<TraceModel> models = new ArrayList<>();
        for (Map.Entry<String, List<JSONObject>> e : byTrace.entrySet()) {
            List<Span> spans = new ArrayList<>();
            for (JSONObject obj : e.getValue()) {
                Span s = toSpan(obj);
                if (s != null) spans.add(s);
            }
            models.add(new TraceModel(e.getKey(), spans));
        }
        return models;
    }

    private static List<JSONObject> readSpans(Path file) throws IOException {
        List<JSONObject> spanObjects = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file.toFile()))) {
            String firstLine;
            do {
                firstLine = reader.readLine();
            } while (firstLine != null && firstLine.trim().isEmpty());
            if (firstLine == null) return spanObjects;

            String trimmed = firstLine.trim();
            if (trimmed.startsWith("[")) {
                StringBuilder sb = new StringBuilder(trimmed);
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                JSONArray arr = new JSONArray(sb.toString());
                for (int i = 0; i < arr.length(); i++) {
                    if (arr.get(i) instanceof JSONObject) spanObjects.add(arr.getJSONObject(i));
                }
            } else if (trimmed.startsWith("{")) {
                StringBuilder sb = new StringBuilder(trimmed);
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                JSONObject root = new JSONObject(sb.toString());
                if (root.has("data")) {
                    JSONArray dataArr = root.getJSONArray("data");
                    for (int i = 0; i < dataArr.length(); i++) {
                        JSONObject bucket = dataArr.getJSONObject(i);
                        JSONObject processes = bucket.optJSONObject("processes");
                        JSONArray spans = bucket.optJSONArray("spans");
                        if (spans == null) continue;
                        for (int j = 0; j < spans.length(); j++) {
                            JSONObject spanObj = spans.getJSONObject(j);
                            if (processes != null) {
                                String procId = spanObj.optString("processID", null);
                                if (procId != null && processes.has(procId)) {
                                    String svc = processes.getJSONObject(procId)
                                            .optString("serviceName", null);
                                    if (svc != null && !svc.isEmpty()) {
                                        spanObj.put("serviceName", svc);
                                    }
                                }
                            }
                            spanObjects.add(spanObj);
                        }
                    }
                } else if (root.has("spans")) {
                    JSONArray spans = root.getJSONArray("spans");
                    for (int j = 0; j < spans.length(); j++) {
                        spanObjects.add(spans.getJSONObject(j));
                    }
                }
            } else {
                // JSONL
                try { spanObjects.add(new JSONObject(firstLine)); } catch (JSONException ignored) {}
                String line;
                while ((line = reader.readLine()) != null) {
                    String t = line.trim();
                    if (t.isEmpty()) continue;
                    try { spanObjects.add(new JSONObject(t)); } catch (JSONException ignored) {}
                }
            }
        }
        return spanObjects;
    }

    private static Span toSpan(JSONObject obj) {
        String spanId = obj.optString("spanId", obj.optString("spanID", null));
        if (spanId == null || spanId.isEmpty()) return null;
        String parentSpanId = obj.has("parentSpanId") ? obj.optString("parentSpanId", null) : null;
        if ((parentSpanId == null || parentSpanId.isEmpty()) && obj.has("references")) {
            JSONArray refs = obj.optJSONArray("references");
            if (refs != null) {
                for (int k = 0; k < refs.length(); k++) {
                    JSONObject ref = refs.optJSONObject(k);
                    if (ref != null && "CHILD_OF".equals(ref.optString("refType"))) {
                        parentSpanId = ref.optString("spanID", null);
                        break;
                    }
                }
            }
        }
        if (parentSpanId != null && parentSpanId.isEmpty()) parentSpanId = null;

        String service = obj.optString("serviceName", "");
        if (service.isEmpty()) service = "unknown";
        String operation = obj.optString("operationName", "");
        long duration = obj.optLong("duration", -1L);

        Map<String, String> tags = new HashMap<>();
        int httpStatus = 0;
        String otelStatus = null;

        JSONArray tagsArr = obj.optJSONArray("tags");
        if (tagsArr != null) {
            for (int i = 0; i < tagsArr.length(); i++) {
                JSONObject t = tagsArr.optJSONObject(i);
                if (t == null) continue;
                String key = t.optString("key", null);
                if (key == null) continue;
                String type = t.optString("type", "");
                if ("http.status_code".equals(key)) {
                    if ("int64".equals(type)) {
                        httpStatus = t.optInt("value", 0);
                    } else {
                        try { httpStatus = Integer.parseInt(t.optString("value", "0")); }
                        catch (NumberFormatException ignored) { }
                    }
                } else if ("otel.status_code".equals(key)) {
                    otelStatus = t.optString("value", null);
                }
                tags.put(key, t.opt("value") == null ? "" : t.opt("value").toString());
            }
        }

        JSONObject attrs = obj.optJSONObject("attributes");
        if (attrs != null) {
            for (String k : attrs.keySet()) {
                Object v = attrs.opt(k);
                if (v != null) tags.putIfAbsent(k, v.toString());
            }
            if (httpStatus == 0) {
                Object s = attrs.opt("http.status_code");
                if (s != null) {
                    try { httpStatus = Integer.parseInt(s.toString()); }
                    catch (NumberFormatException ignored) { }
                }
            }
            if (otelStatus == null) otelStatus = attrs.optString("otel.status_code", null);
        }

        return new Span(spanId, parentSpanId, service, operation, httpStatus, otelStatus, duration, tags);
    }

    public static final class Span {
        public final String spanId;
        public final String parentSpanId;
        public final String service;
        public final String operation;
        public final int httpStatus;
        public final String otelStatus;
        public final long durationMicros;
        public final Map<String, String> tags;

        public Span(String spanId, String parentSpanId, String service, String operation,
                    int httpStatus, String otelStatus, long durationMicros,
                    Map<String, String> tags) {
            this.spanId = spanId;
            this.parentSpanId = parentSpanId;
            this.service = service == null ? "unknown" : service;
            this.operation = operation == null ? "" : operation;
            this.httpStatus = httpStatus;
            this.otelStatus = otelStatus;
            this.durationMicros = durationMicros;
            // Mutable on purpose: the per-test model is ephemeral and the writer injects
            // the live client response body into the root span's tags before evaluation
            // (Jaeger spans omit the body) so ResponseEnvelopeInvariant can read it. An
            // unmodifiable map here made that injection throw UnsupportedOperationException,
            // which aborted the whole oracle.evaluate (no verdict -> no findings surfaced).
            this.tags = tags == null ? new HashMap<>() : new HashMap<>(tags);
        }
    }
}
