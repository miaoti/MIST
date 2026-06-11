package io.mist.core.oracle.shape;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.mist.core.config.MstConfig;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for the H2 ablation toggles on {@link TraceShapeOracle}.
 * Confirms gate 7.3 of PROMPT_H2_ABLATION_INFRASTRUCTURE.md:
 * <ul>
 *   <li>Disabled oracle returns an empty verdict.</li>
 *   <li>Each per-invariant disable skips its invariant only.</li>
 * </ul>
 *
 * <p>Each test builds an {@link MstConfig.Oracle} from a controlled set
 * of system properties and passes it explicitly to the
 * {@link TraceShapeOracle} constructor, so the singleton's state does
 * not leak across cases.
 */
public class TraceShapeOracleTogglesTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static final List<String> KEYS = Arrays.asList(
            "mst.oracle.shape.enabled",
            "mst.oracle.shape.invariants.span_tree.enabled",
            "mst.oracle.shape.invariants.status_propagation.enabled",
            "mst.oracle.shape.invariants.response_envelope.enabled",
            "mst.oracle.shape.invariants.timing.enabled"
    );

    private final Map<String, String> previous = new HashMap<>();

    @Before
    public void setUp() {
        previous.clear();
        for (String k : KEYS) {
            previous.put(k, System.getProperty(k));
            System.clearProperty(k);
        }
        MstConfig.resetForTesting();
    }

    @After
    public void tearDown() {
        for (Map.Entry<String, String> e : previous.entrySet()) {
            if (e.getValue() == null) {
                System.clearProperty(e.getKey());
            } else {
                System.setProperty(e.getKey(), e.getValue());
            }
        }
        MstConfig.resetForTesting();
    }

    @Test
    public void wholeOracleDisabled_returnsEmptyVerdict() throws IOException {
        System.setProperty("mst.oracle.shape.enabled", "false");
        ShapeInvariantStore store = trainedStore();
        TraceModel candidate = conformantCandidate();

        TraceShapeOracle oracle = new TraceShapeOracle(store,
                new MstConfig.Oracle());
        TraceShapeVerdict verdict = oracle.evaluate(candidate, "GET /api/v1/orders");

        assertTrue("disabled oracle's verdict still passes", verdict.isPassed());
        assertEquals("disabled oracle emits zero outcomes",
                0, verdict.getOutcomes().size());
    }

    @Test
    public void defaults_threeInvariantsLoadedNotTiming() throws IOException {
        ShapeInvariantStore store = trainedStore();
        TraceModel candidate = conformantCandidate();

        TraceShapeOracle oracle = new TraceShapeOracle(store,
                new MstConfig.Oracle());
        TraceShapeVerdict verdict = oracle.evaluate(candidate, "GET /api/v1/orders");

        assertEquals("default loads three of four invariants (timing off)",
                3, verdict.getOutcomes().size());
        String summary = verdict.summary();
        assertTrue(summary.contains("SPAN_TREE_SHAPE"));
        assertTrue(summary.contains("STATUS_PROPAGATION"));
        assertTrue(summary.contains("RESPONSE_ENVELOPE"));
        assertFalse("timing skipped under default",
                summary.contains("TIMING_ENVELOPE"));
    }

    @Test
    public void spanTreeDisabled_skipsOnlySpanTree() throws IOException {
        System.setProperty("mst.oracle.shape.invariants.span_tree.enabled", "false");
        // Keep the other three at their defaults to focus the assertion.
        ShapeInvariantStore store = trainedStore();
        TraceModel candidate = conformantCandidate();

        TraceShapeOracle oracle = new TraceShapeOracle(store,
                new MstConfig.Oracle());
        TraceShapeVerdict verdict = oracle.evaluate(candidate, "GET /api/v1/orders");

        String summary = verdict.summary();
        assertFalse("span_tree skipped", summary.contains("SPAN_TREE_SHAPE"));
        assertTrue("status_propagation runs", summary.contains("STATUS_PROPAGATION"));
        assertTrue("response_envelope runs", summary.contains("RESPONSE_ENVELOPE"));
        // 2 invariants (span_tree off, timing off, others on).
        assertEquals(2, verdict.getOutcomes().size());
    }

    @Test
    public void statusPropagationDisabled_skipsOnlyStatusPropagation() throws IOException {
        System.setProperty("mst.oracle.shape.invariants.status_propagation.enabled", "false");
        ShapeInvariantStore store = trainedStore();
        TraceModel candidate = conformantCandidate();

        TraceShapeOracle oracle = new TraceShapeOracle(store, new MstConfig.Oracle());
        String summary = oracle.evaluate(candidate, "GET /api/v1/orders").summary();
        assertFalse(summary.contains("STATUS_PROPAGATION"));
        assertTrue(summary.contains("SPAN_TREE_SHAPE"));
        assertTrue(summary.contains("RESPONSE_ENVELOPE"));
    }

    @Test
    public void responseEnvelopeDisabled_skipsOnlyResponseEnvelope() throws IOException {
        System.setProperty("mst.oracle.shape.invariants.response_envelope.enabled", "false");
        ShapeInvariantStore store = trainedStore();
        TraceModel candidate = conformantCandidate();

        TraceShapeOracle oracle = new TraceShapeOracle(store, new MstConfig.Oracle());
        String summary = oracle.evaluate(candidate, "GET /api/v1/orders").summary();
        assertFalse(summary.contains("RESPONSE_ENVELOPE"));
        assertTrue(summary.contains("SPAN_TREE_SHAPE"));
        assertTrue(summary.contains("STATUS_PROPAGATION"));
    }

    @Test
    public void timingEnabled_loadsAllFour() throws IOException {
        System.setProperty("mst.oracle.shape.invariants.timing.enabled", "true");
        ShapeInvariantStore store = trainedStore();
        TraceModel candidate = conformantCandidate();

        TraceShapeOracle oracle = new TraceShapeOracle(store, new MstConfig.Oracle());
        TraceShapeVerdict verdict = oracle.evaluate(candidate, "GET /api/v1/orders");

        assertEquals("opt-in timing: four invariants",
                4, verdict.getOutcomes().size());
        assertTrue(verdict.summary().contains("TIMING_ENVELOPE"));
    }

    @Test
    public void delegateConstructor_picksUpSingletonOracleConfig() throws IOException {
        // The single-arg constructor reads the singleton's MstConfig.Oracle.
        // Set timing off explicitly (matches default) and verify behaviour.
        ShapeInvariantStore store = trainedStore();
        TraceModel candidate = conformantCandidate();

        TraceShapeOracle delegate = new TraceShapeOracle(store);
        TraceShapeVerdict verdict = delegate.evaluate(candidate, "GET /api/v1/orders");
        assertEquals(3, verdict.getOutcomes().size());
        assertFalse(verdict.summary().contains("TIMING_ENVELOPE"));
    }

    @Test
    public void disabledOracle_emptyVerdictHasNullSafeContract() throws IOException {
        System.setProperty("mst.oracle.shape.enabled", "false");
        ShapeInvariantStore store = trainedStore();

        TraceShapeOracle oracle = new TraceShapeOracle(store, new MstConfig.Oracle());
        TraceShapeVerdict verdict = oracle.evaluate(conformantCandidate(), "GET /api/v1/orders");

        // Empty verdict still exposes a non-null outcomes list and a sane summary.
        assertEquals(0, verdict.getOutcomes().size());
        String summary = verdict.summary();
        assertTrue("summary survives empty outcomes", summary.startsWith("PASS"));
    }

    // ----- helpers ----------------------------------------------------

    private ShapeInvariantStore trainedStore() throws IOException {
        ShapeInvariantStore store = new ShapeInvariantStore(
                tmp.newFile("store-" + System.nanoTime() + ".json").toPath());
        Path corpus = writeCorpus(2);
        Path labels = writeLabels(Collections.emptyMap());
        TraceShapeLearner.learn(corpus, labels, store);
        return store;
    }

    private TraceModel conformantCandidate() throws IOException {
        return buildTrace("conformant",
                rootSpan("r", "gateway", 200),
                child("c", "r", "orders", 200));
    }

    private Path writeCorpus(int n) throws IOException {
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
            sb.append("]}");
            first = false;
        }
        sb.append("]}]}");
        Files.write(file, sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private TraceModel buildTrace(String tid, TraceModel.Span... spans) {
        return new TraceModel(tid, Arrays.asList(spans));
    }

    private TraceModel.Span rootSpan(String id, String service, int httpStatus) {
        Map<String, String> tags = new HashMap<>();
        tags.put("http.method", "GET");
        tags.put("http.target", "/api/v1/orders");
        return new TraceModel.Span(id, null, service, "GET /api/v1/orders",
                httpStatus, "OK", 1500L, tags);
    }

    private TraceModel.Span child(String id, String parent, String service, int httpStatus) {
        Map<String, String> tags = new HashMap<>();
        return new TraceModel.Span(id, parent, service, "op", httpStatus,
                "OK", 500L, tags);
    }
}
