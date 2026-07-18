package io.mist.cli.tools;

import io.mist.core.analysis.TraceShapeAdapter;
import io.mist.core.oracle.shape.ShapeInvariantStore;
import io.mist.core.oracle.shape.TraceModel;
import io.mist.core.oracle.shape.TraceShapeOracle;
import io.mist.core.oracle.shape.TraceShapeVerdict;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * Offline Trace Shape Oracle evaluation over a CAPTURED Jaeger trace file — the
 * benchmark-integration path for the trace-shape arm (A-venue wave, 2026-07-18):
 * the corpus's captured traces are replayed through the SAME oracle code the
 * generated tests wire ({@link TraceShapeOracle} via {@link TraceShapeAdapter}),
 * so the per-case verdicts are measurements of MIST's oracle, not a surrogate.
 *
 * <p>Input: a Jaeger API response file ({@code {"data":[trace...]}}) or a single
 * trace wrapper ({@code {"spans":[...],"processes":{...}}}). Every trace in the
 * file is evaluated; the aggregate FLAGS iff any trace carries an ERROR-severity
 * failed outcome (WARN-only stays no_flag — mirrors the runtime's blocking rule,
 * {@link TraceShapeVerdict#passed}).
 *
 * <p>Invariant gating comes from the standard {@code mst.oracle.shape.*} system
 * properties (see {@link io.mist.core.config.MstConfig.Oracle}); the corpus run
 * enables ONLY the structural hidden-downstream-failure detector — the four
 * learned invariants need a learned {@link ShapeInvariantStore}, which captured
 * corpus traces do not carry, and are disabled explicitly rather than passed
 * vacuously.
 *
 * <p>Usage: {@code OfflineTraceShapeEvaluator <trace.json> <rootApiKey>}
 * — prints one JSON object on stdout.
 */
public final class OfflineTraceShapeEvaluator {

    private OfflineTraceShapeEvaluator() { }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("usage: OfflineTraceShapeEvaluator <trace.json> <rootApiKey>");
            System.exit(2);
        }
        String raw = new String(Files.readAllBytes(Paths.get(args[0])), StandardCharsets.UTF_8);
        JSONObject out = evaluate(new JSONObject(raw), args[1]);
        out.put("file", args[0]);
        System.out.println(out.toString(2));
    }

    /** The measurement path, extracted for direct unit testing (review A). */
    static JSONObject evaluate(JSONObject parsed, String rootApiKey) {
        JSONArray traces = parsed.optJSONArray("data");
        if (traces == null) {
            traces = new JSONArray();
            traces.put(parsed);
        }

        TraceShapeOracle oracle = new TraceShapeOracle(new ShapeInvariantStore());
        JSONArray outcomes = new JSONArray();
        boolean anyErrorFail = false;
        int evaluated = 0;
        for (int i = 0; i < traces.length(); i++) {
            JSONObject traceObj = traces.optJSONObject(i);
            if (traceObj == null) continue;
            TraceModel model = TraceShapeAdapter.toModel(traceObj, rootApiKey);
            TraceShapeVerdict verdict = oracle.evaluate(model, rootApiKey);
            evaluated++;
            List<TraceShapeVerdict.InvariantOutcome> list = verdict.getOutcomes();
            for (TraceShapeVerdict.InvariantOutcome o : list) {
                JSONObject row = new JSONObject();
                row.put("trace_index", i);
                row.put("trace_id", model.getTraceId());
                row.put("kind", o.kind);
                row.put("passed", o.passed);
                row.put("severity", o.severity == null ? JSONObject.NULL : o.severity.name());
                row.put("message", o.detail);
                outcomes.put(row);
                if (!o.passed && o.severity == TraceShapeVerdict.Severity.ERROR) {
                    anyErrorFail = true;
                }
            }
        }

        JSONObject out = new JSONObject();
        out.put("root_api_key", rootApiKey);
        out.put("traces_evaluated", evaluated);
        out.put("verdict", anyErrorFail ? "flag" : "no_flag");
        out.put("outcomes", outcomes);
        return out;
    }
}
