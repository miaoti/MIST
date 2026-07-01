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

    /** Gate-1 placeholder for the gated/S1 stratum (built + validated at G3). */
    static final String GATED_MODE_STATUS =
            "NOT_EVALUATED (needs an observed D-span error; the D-span locator + Toxiproxy backend land at G3)";

    /** B2.4: benign-probe iteration count (0 = no probe). Pre-registered per run config. */
    public static final String FP_PROBE_RUNS_PROPERTY = "mst.oracle.dataintegrity.fpprobe.runs";

    /** §4: the pre-registered numeric bar on non-timeout-gated sync FP. */
    static final double SYNC_FP_BAR = 0.05;

    /** §4: async-FP carries no soundness claim at Gate-1 (P3 verdict: no clean broker path). */
    static final String ASYNC_DISCLAIMER =
            "Gate-1 measures the SYNC stratum only. No broker-mediated async write path exists on this SUT "
                    + "(prep/p3-async-path-resolution.md: verdict NEW-INJECTOR-NEEDED), so no async-FP claim is "
                    + "made; async soundness is deferred to G3 with the Option A injector + P2-completeness "
                    + "validation. Any async fraction reported before that is descriptive-only.";

    /** B2.4 accept-then-drop sub-class: disclosure that TrainTicket has no representative. */
    static final String ACCEPT_THEN_DROP_DISCLOSURE =
            "The 2xx-accepted-but-by-design-never-persists trap class has NO representative on TrainTicket: "
                    + "the only by-design drop (contacts dedupe) soft-rejects with body status:0, which the ack "
                    + "rule already excludes (a true negative, not a trap). The residual FP class is therefore "
                    + "covered only by the eventually-consistent-then-correct benign runs measured here.";

    /** FP-vs-timeout curve cutoffs (ms); the run's real timeout is appended if larger. */
    static final long[] CURVE_CUTOFFS_MS = {500, 1_000, 2_000, 3_000, 5_000, 7_500, 10_000};

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
        /** How many records each run produced for this triple (join visibility). */
        public int controlRecordCount = 1;
        public int faultRecordCount = 1;

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
        List<FaultInjector.FaultTarget> clearFailures = new ArrayList<>();
        try {
            // Inject INSIDE the try: a failed inject (e.g. rollout timeout on
            // the second triple) must still reach the clear-all below, or the
            // first triple's flag would stay live on the SUT.
            for (TargetTripleRegistry.Triple t : injectable) {
                injector.inject(t.faultFlag);
            }
            DataIntegrityRuntime.beginRun(triples, "fault");
            try {
                run.run();
            } finally {
                faultRecords = DataIntegrityRuntime.endRun();
            }
        } finally {
            // The SUT must never be left with a fault flag on. Best-effort
            // over every target: one failed clear must not skip the rest.
            for (TargetTripleRegistry.Triple t : injectable) {
                try {
                    injector.clear(t.faultFlag);
                } catch (RuntimeException e) {
                    logger.error("FAILED to clear fault flag on {} — the SUT may still be faulted: {}",
                            t.faultFlag, e.toString());
                    clearFailures.add(t.faultFlag);
                }
            }
        }
        if (!clearFailures.isEmpty()) {
            throw new FaultInjector.FaultInjectionException(
                    "fault flag may still be active on: " + clearFailures
                            + " — verify/clear manually before any further run");
        }

        List<PairResult> results = new ArrayList<>();
        for (TargetTripleRegistry.Triple t : injectable) {
            PairResult verdict = verdict(t.name,
                    pick(controlRecords, t.name),
                    pick(faultRecords, t.name));
            verdict.controlRecordCount = count(controlRecords, t.name);
            verdict.faultRecordCount = count(faultRecords, t.name);
            results.add(verdict);
        }
        return results;
    }

    /**
     * Picks the triple's record for the verdict join. Several hooked methods
     * can hit one triple; an evaluable (error-free, acknowledged) record is
     * preferred over noise so the join is deterministic even when parallel
     * execution reorders the list. The per-run record counts are surfaced on
     * the report so multi-record joins are visible.
     */
    /**
     * B2.4 benign probe: N flag-off iterations of the same generated pairing
     * tests. Every acknowledged benign write must appear on its own read-back;
     * a benign run that fires the per-run relation (acked ∧ absent at the
     * pre-registered cap) is a measured FALSE POSITIVE. Presence times
     * ({@code elapsedMs}) feed the FP-vs-timeout curve.
     */
    public List<DataIntegrityRuntime.RunRecord> benignProbe(int runs) throws Exception {
        // Hygiene: the probe measures the un-faulted SUT.
        for (TargetTripleRegistry.Triple t : triples) {
            if (t.faultFlag != null) {
                injector.clear(t.faultFlag);
            }
        }
        List<DataIntegrityRuntime.RunRecord> all = new ArrayList<>();
        for (int i = 1; i <= runs; i++) {
            DataIntegrityRuntime.beginRun(triples, "benign-" + i);
            try {
                run.run();
            } finally {
                all.addAll(DataIntegrityRuntime.endRun());
            }
            logger.info("Benign FP probe: iteration {}/{} complete ({} records so far)", i, runs, all.size());
        }
        return all;
    }

    private static DataIntegrityRuntime.RunRecord pick(List<DataIntegrityRuntime.RunRecord> records,
                                                       String tripleName) {
        DataIntegrityRuntime.RunRecord fallback = null;
        for (DataIntegrityRuntime.RunRecord r : records) {
            if (!r.tripleName.equals(tripleName)) {
                continue;
            }
            if (r.error == null && r.acked) {
                return r;
            }
            if (fallback == null) {
                fallback = r;
            }
        }
        return fallback;
    }

    private static int count(List<DataIntegrityRuntime.RunRecord> records, String tripleName) {
        int n = 0;
        for (DataIntegrityRuntime.RunRecord r : records) {
            if (r.tripleName.equals(tripleName)) {
                n++;
            }
        }
        return n;
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

    /**
     * B2.4 aggregation over benign-probe records: per-triple + aggregate FP
     * rates, quiescence-gate coverage, the FP-vs-timeout curve (derived from
     * time-to-presence), and the pre-registered ≤5% non-timeout-gated sync-FP
     * bar. Timeout-gated fires are the lower-confidence stratum and are never
     * pooled with observation-gated ones.
     */
    public static JSONObject fpProbeJson(List<DataIntegrityRuntime.RunRecord> records, long timeoutMs) {
        JSONObject out = new JSONObject();
        out.put("stratum", "sync");
        out.put("asyncDisclaimer", ASYNC_DISCLAIMER);
        out.put("acceptThenDropTrap", ACCEPT_THEN_DROP_DISCLOSURE);
        out.put("fpRule", "per-run: acknowledged (2xx/success) AND key absent from own read-back at the"
                + " pre-registered cap = false positive on a benign write");

        long[] cutoffs = curveCutoffs(timeoutMs);
        Map<String, List<DataIntegrityRuntime.RunRecord>> byTriple = new java.util.LinkedHashMap<>();
        for (DataIntegrityRuntime.RunRecord r : records) {
            byTriple.computeIfAbsent(r.tripleName, k -> new ArrayList<>()).add(r);
        }
        JSONObject perTriple = new JSONObject();
        for (Map.Entry<String, List<DataIntegrityRuntime.RunRecord>> e : byTriple.entrySet()) {
            perTriple.put(e.getKey(), fpStats(e.getValue(), cutoffs));
        }
        out.put("perTriple", perTriple);
        JSONObject aggregate = fpStats(records, cutoffs);
        out.put("aggregate", aggregate);

        double nonTimeoutFpRate = aggregate.getDouble("nonTimeoutGatedFpRate");
        JSONObject bar = new JSONObject();
        bar.put("preRegistered", "non-timeout-gated sync FP <= " + SYNC_FP_BAR + " (plan section 4)");
        bar.put("value", nonTimeoutFpRate);
        bar.put("verdict", nonTimeoutFpRate <= SYNC_FP_BAR ? "PASS" : "FAIL");
        out.put("syncFpBar", bar);
        return out;
    }

    private static JSONObject fpStats(List<DataIntegrityRuntime.RunRecord> records, long[] cutoffs) {
        int invalid = 0;
        int acked = 0;
        int fires = 0;
        int observedGatedFires = 0;
        int timeoutGatedFires = 0;
        List<Long> presenceTimesMs = new ArrayList<>();
        Map<String, Integer> gateHistogram = new java.util.LinkedHashMap<>();
        for (DataIntegrityRuntime.RunRecord r : records) {
            gateHistogram.merge(r.gate.name(), 1, Integer::sum);
            if (r.error != null || r.baselineContainedX || !r.acked) {
                invalid++;
                continue;
            }
            acked++;
            if (r.readbackContainedX) {
                presenceTimesMs.add(r.elapsedMs);
            } else {
                fires++;
                if (r.gate == DataIntegrityRuntime.QuiescenceGate.OBSERVED_COMPLETE_ABSENT) {
                    observedGatedFires++;
                } else {
                    timeoutGatedFires++;
                }
            }
        }
        JSONObject stats = new JSONObject();
        stats.put("records", records.size());
        stats.put("ackedBenignRuns", acked);
        stats.put("invalidRuns", invalid);
        stats.put("fpFires", fires);
        stats.put("fpRate", acked == 0 ? JSONObject.NULL : (double) fires / acked);
        stats.put("observedGatedFpFires", observedGatedFires);
        stats.put("timeoutGatedFpFires", timeoutGatedFires);
        stats.put("nonTimeoutGatedFpRate", acked == 0 ? 0.0 : (double) observedGatedFires / acked);
        stats.put("gateHistogram", new JSONObject(gateHistogram));
        JSONArray curve = new JSONArray();
        for (long cutoff : cutoffs) {
            int firesAtCutoff = fires;
            for (long presence : presenceTimesMs) {
                if (presence > cutoff) {
                    firesAtCutoff++;
                }
            }
            JSONObject point = new JSONObject();
            point.put("timeoutMs", cutoff);
            point.put("fpRate", acked == 0 ? JSONObject.NULL : (double) firesAtCutoff / acked);
            curve.put(point);
        }
        stats.put("fpVsTimeoutCurve", curve);
        return stats;
    }

    private static long[] curveCutoffs(long timeoutMs) {
        for (long cutoff : CURVE_CUTOFFS_MS) {
            if (cutoff == timeoutMs) {
                return CURVE_CUTOFFS_MS;
            }
        }
        long[] extended = java.util.Arrays.copyOf(CURVE_CUTOFFS_MS, CURVE_CUTOFFS_MS.length + 1);
        extended[extended.length - 1] = timeoutMs;
        java.util.Arrays.sort(extended);
        return extended;
    }

    /** Writes the machine-readable pairing report (evidence + strata). */
    public static void writeReport(Path file, List<PairResult> results, String runId) throws IOException {
        writeReport(file, results, null, 0, runId);
    }

    /** Report incl. the B2.4 benign-probe section when probe records exist. */
    public static void writeReport(Path file, List<PairResult> results,
                                   List<DataIntegrityRuntime.RunRecord> probeRecords, long timeoutMs,
                                   String runId) throws IOException {
        JSONObject report = new JSONObject();
        report.put("runId", runId);
        report.put("generatedAtEpochMs", System.currentTimeMillis());
        report.put("fireRule", "pure-differential (headline): fault 2xx-acks-X AND X absent on fault"
                + " read-back AND control X present; control = systemic FP-guard only");
        report.put("gatedMode", GATED_MODE_STATUS);
        if (probeRecords != null) {
            report.put("fpProbe", fpProbeJson(probeRecords, timeoutMs));
        }
        JSONArray pairs = new JSONArray();
        for (PairResult r : results) {
            JSONObject pair = new JSONObject();
            pair.put("triple", r.tripleName);
            pair.put("pureDifferential", r.pureDifferential.name());
            pair.put("reason", r.reason);
            pair.put("controlRecordCount", r.controlRecordCount);
            pair.put("faultRecordCount", r.faultRecordCount);
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

    /** Console block for the B2.4 benign-probe result. */
    public static String summarizeProbe(JSONObject fpProbe) {
        JSONObject aggregate = fpProbe.getJSONObject("aggregate");
        JSONObject bar = fpProbe.getJSONObject("syncFpBar");
        StringBuilder out = new StringBuilder();
        out.append("  Benign FP probe (sync stratum): ")
                .append(aggregate.getInt("fpFires")).append(" fire(s) / ")
                .append(aggregate.getInt("ackedBenignRuns")).append(" acked benign run(s)")
                .append(", non-timeout-gated FP rate ").append(bar.getDouble("value"))
                .append(" -> bar ").append(bar.getString("verdict")).append("\n");
        out.append("  gate coverage: ").append(aggregate.getJSONObject("gateHistogram")).append("\n");
        out.append("  async: descriptive-only at Gate-1 (see report asyncDisclaimer)\n");
        return out.toString();
    }
}
