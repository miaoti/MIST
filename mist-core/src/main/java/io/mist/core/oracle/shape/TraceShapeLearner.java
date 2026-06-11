package io.mist.core.oracle.shape;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.mist.core.oracle.shape.invariant.ResponseEnvelopeInvariant;
import io.mist.core.oracle.shape.invariant.SpanTreeShapeInvariant;
import io.mist.core.oracle.shape.invariant.StatusPropagationInvariant;
import io.mist.core.oracle.shape.invariant.TimingEnvelopeInvariant;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Learner that walks a seed corpus directory of trace files, filters to the
 * traces labelled {@code known-good}, partitions them by their root-API key
 * (HTTP method + path of the outermost span), and runs each invariant's
 * learn side. The resulting data records are persisted via the shared
 * {@link ShapeInvariantStore}.
 */
public final class TraceShapeLearner {

    private static final Logger logger = LogManager.getLogger(TraceShapeLearner.class);

    private TraceShapeLearner() { }

    public static LearnResult learn(Path seedCorpusDir, Path labelsFile, ShapeInvariantStore store) throws IOException {
        Map<String, String> labels = readLabels(labelsFile);
        Map<String, List<TraceModel>> byRoot = groupGoodTraces(seedCorpusDir, labels);

        LearnResult result = new LearnResult();
        for (Map.Entry<String, List<TraceModel>> e : byRoot.entrySet()) {
            String root = e.getKey();
            List<TraceModel> traces = e.getValue();

            SpanTreeShapeInvariant.Data st = SpanTreeShapeInvariant.learn(traces);
            new SpanTreeShapeInvariant(root, st).persist(store);

            StatusPropagationInvariant.Data sp = StatusPropagationInvariant.learn(traces);
            new StatusPropagationInvariant(root, sp).persist(store);

            TimingEnvelopeInvariant.Data te = TimingEnvelopeInvariant.learn(traces);
            new TimingEnvelopeInvariant(root, te).persist(store);

            ResponseEnvelopeInvariant.Data re = ResponseEnvelopeInvariant.learn(traces);
            new ResponseEnvelopeInvariant(root, re).persist(store);

            result.rootApisLearned.add(root);
            result.traceCountsByRoot.put(root, traces.size());
        }
        store.flush();
        logger.info("TraceShapeLearner: learned invariants for {} root APIs", result.rootApisLearned.size());
        return result;
    }

    static Map<String, String> readLabels(Path labelsFile) throws IOException {
        Map<String, String> labels = new HashMap<>();
        if (labelsFile == null) return labels;
        String content;
        try {
            content = new String(Files.readAllBytes(labelsFile), StandardCharsets.UTF_8);
        } catch (java.nio.file.NoSuchFileException nsfe) {
            return labels;
        }
        if (content.trim().isEmpty()) return labels;
        JsonElement parsed = new JsonParser().parse(content);
        if (!parsed.isJsonObject()) return labels;
        JsonObject obj = parsed.getAsJsonObject();
        for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
            if (e.getValue().isJsonPrimitive()) {
                labels.put(e.getKey(), e.getValue().getAsString());
            }
        }
        return labels;
    }

    static Map<String, List<TraceModel>> groupGoodTraces(Path seedCorpusDir, Map<String, String> labels) throws IOException {
        Map<String, List<TraceModel>> byRoot = new HashMap<>();
        if (seedCorpusDir == null || !Files.isDirectory(seedCorpusDir)) return byRoot;

        try (java.util.stream.Stream<Path> stream = Files.list(seedCorpusDir)) {
            List<Path> files = new ArrayList<>();
            stream.filter(Files::isRegularFile)
                  .filter(p -> {
                      String n = p.getFileName().toString().toLowerCase();
                      return n.endsWith(".json") || n.endsWith(".jsonl");
                  })
                  .forEach(files::add);
            Collections.sort(files);
            for (Path file : files) {
                String label = labels.getOrDefault(file.getFileName().toString(), "known-good");
                if (!"known-good".equals(label)) continue;
                List<TraceModel> models;
                try {
                    models = TraceModel.fromJaegerJson(file);
                } catch (Exception ex) {
                    logger.warn("Skipping unparseable trace file {}: {}", file, ex.getMessage());
                    continue;
                }
                for (TraceModel tm : models) {
                    String root = deriveRootApiKey(tm);
                    if (root == null) continue;
                    byRoot.computeIfAbsent(root, k -> new ArrayList<>()).add(tm);
                }
            }
        }
        return byRoot;
    }

    /**
     * Derive a stable root-API key (e.g. {@code "POST /api/v1/orders"}) from
     * the trace's root span. Returns null when no root span carries enough
     * HTTP metadata to identify it.
     */
    public static String deriveRootApiKey(TraceModel trace) {
        for (TraceModel.Span root : trace.roots()) {
            String method = root.tags.getOrDefault("http.method", "");
            String target = root.tags.getOrDefault("http.target", root.tags.getOrDefault("http.url", ""));
            if (!method.isEmpty() && !target.isEmpty()) {
                String path = target;
                int q = path.indexOf('?');
                if (q >= 0) path = path.substring(0, q);
                return method + " " + path;
            }
            if (!root.operation.isEmpty()) return root.operation;
        }
        return null;
    }

    public static final class LearnResult {
        public final List<String> rootApisLearned = new ArrayList<>();
        public final Map<String, Integer> traceCountsByRoot = new HashMap<>();
    }
}
