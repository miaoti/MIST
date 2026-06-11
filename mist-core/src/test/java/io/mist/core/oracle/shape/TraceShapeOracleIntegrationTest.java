package io.mist.core.oracle.shape;

import com.google.gson.JsonObject;
import io.mist.core.config.MstConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * End-to-end tests that exercise the full learn -> persist -> load -> evaluate
 * pipeline. Synthetic traces are written to disk and read back via
 * {@link TraceModel#fromJaegerJson(Path)} so the wiring under test matches
 * production usage.
 *
 * <p>The class-level setup explicitly enables the TimingEnvelope toggle so
 * these tests continue to exercise the legacy four-invariant verdict shape;
 * the production default for {@code mst.oracle.shape.invariants.timing.enabled}
 * is {@code false} since the paper revision dropped TimingEnvelope from the
 * named contribution.
 */
public class TraceShapeOracleIntegrationTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private String previousTimingEnabled;

    @Before
    public void enableTimingInvariant() {
        previousTimingEnabled = System.getProperty("mst.oracle.shape.invariants.timing.enabled");
        System.setProperty("mst.oracle.shape.invariants.timing.enabled", "true");
        MstConfig.resetForTesting();
    }

    @After
    public void restoreTimingInvariant() {
        if (previousTimingEnabled == null) {
            System.clearProperty("mst.oracle.shape.invariants.timing.enabled");
        } else {
            System.setProperty("mst.oracle.shape.invariants.timing.enabled", previousTimingEnabled);
        }
        MstConfig.resetForTesting();
    }

    @Test
    public void oracleProducesPassVerdictOnConformantTrace() throws IOException {
        ShapeInvariantStore store = freshStore();
        Path corpus = writeCorpus(2, /*addBadSpan*/false);
        Path labels = writeLabels(Collections.emptyMap());

        TraceShapeLearner.learn(corpus, labels, store);

        TraceShapeOracle oracle = new TraceShapeOracle(store);
        TraceModel candidate = TraceModel.fromJaegerJson(corpus.resolve("trace-0.json")).get(0);
        TraceShapeVerdict verdict = oracle.evaluate(candidate, "GET /api/v1/orders");
        assertTrue("conformant trace should pass: " + verdict.summary(), verdict.isPassed());
        assertEquals(4, verdict.getOutcomes().size());
    }

    @Test
    public void unexpectedServiceCallFlippedToFail() throws IOException {
        ShapeInvariantStore store = freshStore();
        Path corpus = writeCorpus(3, /*addBadSpan*/false);
        Path labels = writeLabels(Collections.emptyMap());
        TraceShapeLearner.learn(corpus, labels, store);

        // Craft a trace with a service call that never appeared during learning.
        TraceModel candidate = buildTrace("bad",
                rootSpan("r", "gateway", 200),
                child("c1", "r", "orders", 200),
                // 'malware' is novel both as a service and as an edge destination.
                child("c2", "r", "malware", 200));

        TraceShapeVerdict verdict = new TraceShapeOracle(store).evaluate(candidate, "GET /api/v1/orders");
        assertFalse("trace with novel service must fail: " + verdict.summary(), verdict.isPassed());
    }

    @Test
    public void knownBadTracesExcludedFromLearning() throws IOException {
        ShapeInvariantStore store = freshStore();
        Path corpus = writeCorpus(2, /*addBadSpan*/false);
        // Add a "bad" trace and label it accordingly.
        TraceModel bad = buildTrace("bad",
                rootSpan("r", "gateway", 200),
                child("c1", "r", "noxious", 200));
        writeTraceToFile(corpus.resolve("trace-bad.json"), bad);
        Map<String, String> labels = new HashMap<>();
        labels.put("trace-bad.json", "known-bad");
        Path labelsFile = writeLabels(labels);

        TraceShapeLearner.learn(corpus, labelsFile, store);

        // The "noxious" service should not appear in the learned observedEdges.
        JsonObject st = store.get("SPAN_TREE_SHAPE::GET /api/v1/orders");
        assertTrue("known-bad trace edges should not leak in", st != null && !st.getAsJsonArray("observedEdges").toString().contains("noxious"));
    }

    @Test
    public void persistAndReloadStorePreservesState() throws IOException {
        Path storePath = tmp.newFile("store.json").toPath();
        ShapeInvariantStore store1 = new ShapeInvariantStore(storePath);
        Path corpus = writeCorpus(2, /*addBadSpan*/false);
        TraceShapeLearner.learn(corpus, writeLabels(Collections.emptyMap()), store1);

        // Reload from disk in a fresh instance and check we still see learned state.
        ShapeInvariantStore store2 = new ShapeInvariantStore(storePath);
        assertTrue("store should have learned data after reload", store2.size() > 0);
        assertTrue(store2.has("SPAN_TREE_SHAPE::GET /api/v1/orders"));
    }

    @Test
    public void verdictSummaryNamesEachInvariant() throws IOException {
        ShapeInvariantStore store = freshStore();
        Path corpus = writeCorpus(2, /*addBadSpan*/false);
        TraceShapeLearner.learn(corpus, writeLabels(Collections.emptyMap()), store);

        TraceModel candidate = TraceModel.fromJaegerJson(corpus.resolve("trace-0.json")).get(0);
        TraceShapeVerdict verdict = new TraceShapeOracle(store).evaluate(candidate, "GET /api/v1/orders");
        String summary = verdict.summary();
        assertTrue(summary.contains("SPAN_TREE_SHAPE"));
        assertTrue(summary.contains("STATUS_PROPAGATION"));
        assertTrue(summary.contains("TIMING_ENVELOPE"));
        assertTrue(summary.contains("RESPONSE_ENVELOPE"));
    }

    @Test
    public void envelopeClassifierWiredThroughOracle_failsOnSoftError() throws IOException {
        ShapeInvariantStore store = freshStore();
        // No learned data needed: the wired classifier drives the verdict. ResponseEnvelope
        // is enabled by default; a 2xx body whose status is classified a failure must fail.
        Map<String, String> tags = new HashMap<>();
        tags.put("http.method", "GET");
        tags.put("http.target", "/api/v1/orders");
        tags.put("http.response.body", "{\"status\":0,\"msg\":\"not found\",\"data\":null}");
        TraceModel.Span root = new TraceModel.Span("r", null, "gateway", "GET /api/v1/orders", 200, "OK", 1500L, tags);
        TraceModel soft = new TraceModel("soft", Collections.singletonList(root));

        TraceShapeOracle oracle = new TraceShapeOracle(store)
                .setEnvelopeClassifier((api, field, val, body) -> "0".equals(val) ? Boolean.TRUE : Boolean.FALSE);
        TraceShapeVerdict verdict = oracle.evaluate(soft, "GET /api/v1/orders");

        assertFalse("soft error must fail the oracle via the wired classifier: " + verdict.summary(),
                verdict.isPassed());
        boolean responseEnvelopeFailed = false;
        for (TraceShapeVerdict.InvariantOutcome o : verdict.getOutcomes()) {
            if ("RESPONSE_ENVELOPE".equals(o.kind) && !o.passed
                    && o.severity == TraceShapeVerdict.Severity.ERROR) {
                responseEnvelopeFailed = true;
            }
        }
        assertTrue("RESPONSE_ENVELOPE should be a failing ERROR outcome", responseEnvelopeFailed);
    }

    // ----- helpers ----------------------------------------------------

    private ShapeInvariantStore freshStore() throws IOException {
        return new ShapeInvariantStore(tmp.newFile("store-" + System.nanoTime() + ".json").toPath());
    }

    private Path writeCorpus(int n, boolean addBadSpan) throws IOException {
        Path dir = tmp.newFolder("corpus-" + System.nanoTime()).toPath();
        for (int i = 0; i < n; i++) {
            TraceModel tm = buildTrace("seed-" + i,
                    rootSpan("r" + i, "gateway", 200),
                    child("c" + i, "r" + i, "orders", 200));
            writeTraceToFile(dir.resolve("trace-" + i + ".json"), tm);
        }
        return dir;
    }

    private Path writeLabels(Map<String, String> labels) throws IOException {
        Path f = tmp.newFile("labels-" + System.nanoTime() + ".json").toPath();
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> e : labels.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(e.getKey()).append("\":\"").append(e.getValue()).append("\"");
            first = false;
        }
        sb.append("}");
        Files.write(f, sb.toString().getBytes(StandardCharsets.UTF_8));
        return f;
    }

    /** Materialise a TraceModel as a Jaeger-data-wrapper JSON file. */
    private void writeTraceToFile(Path file, TraceModel tm) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"data\":[{\"traceID\":\"").append(tm.getTraceId()).append("\",\"spans\":[");
        boolean first = true;
        for (TraceModel.Span s : tm.getSpans()) {
            if (!first) sb.append(",");
            sb.append("{\"traceID\":\"").append(tm.getTraceId()).append("\"");
            sb.append(",\"spanID\":\"").append(s.spanId).append("\"");
            sb.append(",\"operationName\":\"").append(s.operation).append("\"");
            sb.append(",\"serviceName\":\"").append(s.service).append("\"");
            sb.append(",\"duration\":").append(s.durationMicros);
            if (s.parentSpanId != null) {
                sb.append(",\"references\":[{\"refType\":\"CHILD_OF\",\"spanID\":\"")
                  .append(s.parentSpanId).append("\"}]");
            }
            sb.append(",\"tags\":[");
            sb.append("{\"key\":\"http.method\",\"type\":\"string\",\"value\":\"GET\"}");
            sb.append(",{\"key\":\"http.target\",\"type\":\"string\",\"value\":\"/api/v1/orders\"}");
            sb.append(",{\"key\":\"http.status_code\",\"type\":\"int64\",\"value\":").append(s.httpStatus).append("}");
            if (s.otelStatus != null) {
                sb.append(",{\"key\":\"otel.status_code\",\"type\":\"string\",\"value\":\"")
                  .append(s.otelStatus).append("\"}");
            }
            for (Map.Entry<String, String> e : s.tags.entrySet()) {
                sb.append(",{\"key\":\"").append(e.getKey()).append("\",\"type\":\"string\",\"value\":\"")
                  .append(escape(e.getValue())).append("\"}");
            }
            sb.append("]}");
            first = false;
        }
        sb.append("]}]}");
        Files.write(file, sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private TraceModel buildTrace(String tid, TraceModel.Span... spans) {
        return new TraceModel(tid, Arrays.asList(spans));
    }

    private TraceModel.Span rootSpan(String id, String service, int httpStatus) {
        Map<String, String> tags = new HashMap<>();
        tags.put("http.method", "GET");
        tags.put("http.target", "/api/v1/orders");
        return new TraceModel.Span(id, null, service, "GET /api/v1/orders", httpStatus, "OK", 1500L, tags);
    }

    private TraceModel.Span child(String id, String parent, String service, int httpStatus) {
        Map<String, String> tags = new HashMap<>();
        return new TraceModel.Span(id, parent, service, "op", httpStatus, "OK", 500L, tags);
    }
}
