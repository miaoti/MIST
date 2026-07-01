package io.mist.cli.fault;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * B1.3 control/fault pairing executor: runs the generated pairing tests once
 * clean (control) and once with every registered SUT fault flag active
 * (fault, batched so N triples cost one extra rollout round-trip, not N),
 * then applies the B2.3 two-mode fire rule per triple.
 *
 * <p>Sequence: clear-all (hygiene — also flushes any stale flag a manual
 * smoke left on the deployment) → control run → inject-all → fault run →
 * clear-all (always, via finally). Control and fault runs execute
 * back-to-back with no interleaved mutation, per the smoke evidence-hygiene
 * note. Each run is bracketed by {@link DataIntegrityRuntime#beginRun}/
 * {@link DataIntegrityRuntime#endRun}, the same-JVM holder channel.
 *
 * <p><b>Fire rule (headline, pure-differential):</b> FIRE when the fault run
 * acknowledged X with 2xx/success AND X is absent from the fault run's own
 * read-back AND the control run's X persisted. The control run rules out
 * systemic/environmental false positives only — input validity is carried by
 * the verified pool plus isolation freshness (B2.3 rescoping). The gated
 * mode (base relation AND an observed D-span error) is deliberately
 * <b>NOT_EVALUATED</b> here: it needs a real downstream failure signal
 * (Toxiproxy backend), which lands at G3; its verdict slot is reported so
 * the two strata are never pooled.
 */
public final class PairedFaultExecutor {

    private static final Logger logger = LogManager.getLogger(PairedFaultExecutor.class);

    /** Gate-1 placeholder for the gated/S1 stratum (validated at G3). */
    static final String GATED_MODE_STATUS =
            "NOT_EVALUATED (needs an observed D-span error; Toxiproxy backend lands at G3)";

    /** One filtered execution of the generated pairing tests (seam for tests). */
    public interface FilteredRun {
        void run() throws Exception;
    }

    public enum PairVerdict { FIRE, NO_FIRE, NOT_EVALUABLE }

    /** Joined control+fault observation and verdict for one triple. */
    public static final class PairResult {
        public final String tripleName;
        public final DataIntegrityRuntime.RunRecord control;
        public final DataIntegrityRuntime.RunRecord fault;
        public final PairVerdict pureDifferential;
        public final String reason;

        PairResult(String tripleName, DataIntegrityRuntime.RunRecord control,
                   DataIntegrityRuntime.RunRecord fault, PairVerdict pureDifferential, String reason) {
            this.tripleName = tripleName;
            this.control = control;
            this.fault = fault;
            this.pureDifferential = pureDifferential;
            this.reason = reason;
        }
    }

    private final List<TargetTripleRegistry.Triple> triples;
    private final FaultInjector injector;
    private final FilteredRun run;

    public PairedFaultExecutor(List<TargetTripleRegistry.Triple> triples, FaultInjector injector,
                               FilteredRun run) {
        this.triples = triples;
        this.injector = injector;
        this.run = run;
    }

    public List<PairResult> execute() throws Exception {
        List<TargetTripleRegistry.Triple> injectable = new ArrayList<>();
        for (TargetTripleRegistry.Triple t : triples) {
            if (t.faultFlag != null) {
                injectable.add(t);
            }
        }
        logger.info("Pairing: {} triple(s), {} with a SUT fault flag", triples.size(), injectable.size());

        // Hygiene: start from a clean SUT (flushes stale smoke flags too).
        for (TargetTripleRegistry.Triple t : injectable) {
            injector.clear(t.faultFlag);
        }

        List<DataIntegrityRuntime.RunRecord> controlRecords;
        DataIntegrityRuntime.beginRun(triples, "control");
        try {
            run.run();
        } finally {
            controlRecords = DataIntegrityRuntime.endRun();
        }

        List<DataIntegrityRuntime.RunRecord> faultRecords;
        for (TargetTripleRegistry.Triple t : injectable) {
            injector.inject(t.faultFlag);
        }
        try {
            DataIntegrityRuntime.beginRun(triples, "fault");
            try {
                run.run();
            } finally {
                faultRecords = DataIntegrityRuntime.endRun();
            }
        } finally {
            // The SUT must never be left with a fault flag on.
            for (TargetTripleRegistry.Triple t : injectable) {
                injector.clear(t.faultFlag);
            }
        }

        List<PairResult> results = new ArrayList<>();
        for (TargetTripleRegistry.Triple t : injectable) {
            results.add(verdict(t.name,
                    first(controlRecords, t.name),
                    first(faultRecords, t.name)));
        }
        return results;
    }

    private static DataIntegrityRuntime.RunRecord first(List<DataIntegrityRuntime.RunRecord> records,
                                                        String tripleName) {
        for (DataIntegrityRuntime.RunRecord r : records) {
            if (r.tripleName.equals(tripleName)) {
                return r;
            }
        }
        return null;
    }

    /**
     * B2.3 pure-differential verdict for one control/fault record pair. The
     * NOT_EVALUABLE outcomes are environment/protocol failures — they are
     * reported as broken pairs, never as NO_FIRE evidence.
     */
    static PairResult verdict(String tripleName, DataIntegrityRuntime.RunRecord control,
                              DataIntegrityRuntime.RunRecord fault) {
        if (control == null || fault == null) {
            return new PairResult(tripleName, control, fault, PairVerdict.NOT_EVALUABLE,
                    "missing " + (control == null ? "control" : "fault") + " record"
                            + " (test skipped or hook never reached)");
        }
        if (control.error != null) {
            return new PairResult(tripleName, control, fault, PairVerdict.NOT_EVALUABLE,
                    "control run error: " + control.error);
        }
        if (fault.error != null) {
            return new PairResult(tripleName, control, fault, PairVerdict.NOT_EVALUABLE,
                    "fault run error: " + fault.error);
        }
        if (control.baselineContainedX || fault.baselineContainedX) {
            return new PairResult(tripleName, control, fault, PairVerdict.NOT_EVALUABLE,
                    "isolation violated: freshened key already present in a baseline read-back");
        }
        if (!control.acked) {
            return new PairResult(tripleName, control, fault, PairVerdict.NOT_EVALUABLE,
                    "control run not acknowledged (http " + control.ackHttpStatus
                            + ", body status " + control.ackBodyStatus + ") — input/systemic guard failed");
        }
        if (!control.readbackContainedX) {
            return new PairResult(tripleName, control, fault, PairVerdict.NOT_EVALUABLE,
                    "control write never appeared on its read-back (gate " + control.gate
                            + ") — systemic guard failed, pair carries no evidence");
        }
        if (!fault.acked) {
            return new PairResult(tripleName, control, fault, PairVerdict.NO_FIRE,
                    "fault run not acknowledged (http " + fault.ackHttpStatus
                            + ", body status " + fault.ackBodyStatus + ") — base relation vacuous");
        }
        if (fault.readbackContainedX) {
            return new PairResult(tripleName, control, fault, PairVerdict.NO_FIRE,
                    "fault run's write persisted (did the injected flag take effect?)");
        }
        return new PairResult(tripleName, control, fault, PairVerdict.FIRE,
                "fault run acknowledged X (http " + fault.ackHttpStatus + ", body status "
                        + fault.ackBodyStatus + ") but X is absent from its own read-back ("
                        + fault.polls + " poll(s), gate " + fault.gate
                        + "); control's X persisted — acknowledged-but-lost write");
    }

    /** Writes the machine-readable pairing report (evidence + strata). */
    public static void writeReport(Path file, List<PairResult> results, String runId) throws IOException {
        JSONObject report = new JSONObject();
        report.put("runId", runId);
        report.put("generatedAtEpochMs", System.currentTimeMillis());
        report.put("fireRule", "pure-differential (headline): fault 2xx-acks-X AND X absent on fault"
                + " read-back AND control X present; control = systemic FP-guard only");
        report.put("gatedMode", GATED_MODE_STATUS);
        JSONArray pairs = new JSONArray();
        for (PairResult r : results) {
            JSONObject pair = new JSONObject();
            pair.put("triple", r.tripleName);
            pair.put("pureDifferential", r.pureDifferential.name());
            pair.put("reason", r.reason);
            pair.put("control", toJson(r.control));
            pair.put("fault", toJson(r.fault));
            pairs.put(pair);
        }
        report.put("pairs", pairs);
        Files.createDirectories(file.getParent());
        Files.write(file, report.toString(2).getBytes(StandardCharsets.UTF_8));
        logger.info("Pairing report written to {}", file);
    }

    private static Object toJson(DataIntegrityRuntime.RunRecord record) {
        if (record == null) {
            return JSONObject.NULL;
        }
        JSONObject json = new JSONObject();
        json.put("runLabel", record.runLabel);
        json.put("stepKey", record.stepKey);
        JSONObject key = new JSONObject();
        for (Map.Entry<String, String> e : record.isolationKey.entrySet()) {
            key.put(e.getKey(), e.getValue());
        }
        json.put("isolationKey", key);
        json.put("ackHttpStatus", record.ackHttpStatus);
        json.put("ackBodyStatus", record.ackBodyStatus == null ? JSONObject.NULL : record.ackBodyStatus);
        json.put("acked", record.acked);
        json.put("baselineContainedX", record.baselineContainedX);
        json.put("readbackContainedX", record.readbackContainedX);
        json.put("quiescenceGate", record.gate.name());
        json.put("polls", record.polls);
        json.put("elapsedMs", record.elapsedMs);
        json.put("baselineBody", truncate(record.baselineBody));
        json.put("lastReadbackBody", truncate(record.lastReadbackBody));
        json.put("error", record.error == null ? JSONObject.NULL : record.error);
        return json;
    }

    private static Object truncate(String body) {
        if (body == null) {
            return JSONObject.NULL;
        }
        return body.length() <= 8_000 ? body : body.substring(0, 8_000) + "…[truncated]";
    }

    /** Human-readable one-block summary for the console + run log. */
    public static String summarize(List<PairResult> results) {
        StringBuilder out = new StringBuilder();
        out.append("\n==================================================================\n");
        out.append("  Differential data-integrity pairing verdicts (pure-differential)\n");
        out.append("  gated/S1 stratum: ").append(GATED_MODE_STATUS).append("\n");
        out.append("  ------------------------------------------------------------\n");
        for (PairResult r : results) {
            out.append("  ").append(r.pureDifferential).append("  ").append(r.tripleName);
            if (r.fault != null && r.fault.gate != DataIntegrityRuntime.QuiescenceGate.NOT_APPLICABLE) {
                out.append("  [fault gate: ").append(r.fault.gate).append("]");
            }
            out.append("\n      ").append(r.reason).append("\n");
        }
        out.append("==================================================================\n");
        return out.toString();
    }
}
