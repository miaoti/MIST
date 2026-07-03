package io.mist.cli.fault;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
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

    /**
     * Minimum acked benign runs for the bar to be evaluable — a bar computed
     * over a collapsed denominator (auth outage, pair exhaustion) must not
     * auto-PASS (soundness review F1).
     */
    static final int MIN_ACKED_FOR_BAR = 20;

    /**
     * Bar v2 (hardening-wave R2fix; floors pre-registered in
     * prep/hardening-wave-spec.md §1): a degraded observation gate makes the
     * bar NOT_EVALUABLE, never PASS — otherwise timeout-gated benign losses
     * are excluded from the numerator while staying in the denominator, so
     * gate degradation (Jaeger latency/outage) lowers the governed rate
     * without lowering the true FP rate (cold-review B-1/B-2). Gate-1 run #3
     * stays scored under the pre-registered v1 bar (no silent re-scoring);
     * these guards bind every later run.
     */
    static final double GATE_RESOLVED_FLOOR = 0.5;
    static final double TIMEOUT_GATED_CAP = 0.3;

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
        /**
         * Hardening-wave R3fix: per-record join tallies. The triple verdict
         * is FIRE iff >=1 joined pair fires against its OWN control sibling —
         * a persisted sibling can no longer mask a lost one (cold-review
         * A-2/B-5/C-P1-6). Unjoined records (count mismatch between the two
         * runs) are surfaced, never silently dropped.
         */
        public int firePairs = 0;
        public int noFirePairs = 0;
        public int notEvaluablePairs = 0;
        public int unjoinedRecords = 0;
        /**
         * G3 rider 1 (review A-F2 / C-MAJOR-2): how the two legs were aligned —
         * {@code "correlator"} (by the writer's {@code <class>.<method>#<stepIdx>})
         * or {@code "positional"} (legacy fallback) — and, for correlator mode,
         * whether every record's correlator was UNIQUE within its leg. Tallies
         * are claim-eligible for G3 only when joinMode=correlator AND
         * correlatorUnique=true; a duplicate correlator (a method run more than
         * once) degrades to positional-within-group and is surfaced here, never
         * silently counted as aligned.
         */
        public String joinMode = "positional";
        public boolean correlatorUnique = false;

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
    /**
     * Hardening-wave C-P1-3fix: invoked with the computed verdicts AND the
     * flags whose clear failed (review H6), BEFORE the F2 clear-failure
     * throw, so a failed flag-clear no longer discards the run's evidence
     * (run #2 lost its entire report this way). Optional.
     */
    private java.util.function.BiConsumer<List<PairResult>, List<FaultInjector.FaultTarget>>
            clearFailureSink;

    public PairedFaultExecutor(List<TargetTripleRegistry.Triple> triples, FaultInjector injector,
                               FilteredRun run) {
        this.triples = triples;
        this.injector = injector;
        this.run = run;
    }

    /** Registers the C-P1-3fix evidence sink (see {@link #clearFailureSink}). */
    public void onClearFailure(
            java.util.function.BiConsumer<List<PairResult>, List<FaultInjector.FaultTarget>> sink) {
        this.clearFailureSink = sink;
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
            // C-P1-3fix: the SUT-may-be-faulted throw stays loud, but the
            // already-collected evidence is persisted first (best-effort — a
            // sink failure must not mask the F2 signal).
            if (clearFailureSink != null) {
                try {
                    clearFailureSink.accept(evaluate(injectable, controlRecords, faultRecords),
                            clearFailures);
                } catch (RuntimeException e) {
                    logger.error("clear-failure evidence sink failed ({}); the F2 throw proceeds",
                            e.toString());
                }
            }
            throw new FaultInjector.FaultInjectionException(
                    "fault flag may still be active on: " + clearFailures
                            + " — verify/clear manually before any further run");
        }

        return evaluate(injectable, controlRecords, faultRecords);
    }

    /**
     * Hardening-wave R3fix + G3 rider 1: verdict-aware per-record join. Records
     * are aligned by the writer's generation-time correlator when every record
     * carries one (joinMode=correlator; see {@link #joinRecords}), else by
     * position (legacy fallback). Each joined pair gets its own verdict and the
     * triple FIREs iff at least one CORRECTLY-ALIGNED pair fires; records with
     * no sibling are surfaced as {@code unjoinedRecords}. When the correlator
     * aligns zero pairs but both legs are non-empty, the triple is
     * NOT_EVALUABLE — never a cross-paired verdict (review A-F1/B).
     *
     * <p>Public so the G3 head-to-head runner ({@code io.mist.cli.g3}) can reuse this
     * exact, reviewed pure-differential verdict over legs it drives itself (its fault is
     * a route-scoped EnvoyFilter, not a SUT flag, so it bypasses {@link #execute()} but
     * must not fork the verdict logic). Behaviour is unchanged — visibility only.
     */
    public static List<PairResult> evaluate(List<TargetTripleRegistry.Triple> injectable,
                                     List<DataIntegrityRuntime.RunRecord> controlRecords,
                                     List<DataIntegrityRuntime.RunRecord> faultRecords) {
        List<PairResult> results = new ArrayList<>();
        for (TargetTripleRegistry.Triple t : injectable) {
            List<DataIntegrityRuntime.RunRecord> controls = recordsFor(controlRecords, t.name);
            List<DataIntegrityRuntime.RunRecord> faults = recordsFor(faultRecords, t.name);
            Join join = joinRecords(controls, faults);

            PairResult firstFire = null;
            PairResult firstNoFire = null;
            PairResult firstAny = null;
            int fires = 0;
            int noFires = 0;
            int notEvaluable = 0;
            for (DataIntegrityRuntime.RunRecord[] p : join.pairs) {
                PairResult pair = verdict(t.name, p[0], p[1]);
                if (firstAny == null) {
                    firstAny = pair;
                }
                switch (pair.pureDifferential) {
                    case FIRE:
                        fires++;
                        if (firstFire == null) {
                            firstFire = pair;
                        }
                        break;
                    case NO_FIRE:
                        noFires++;
                        if (firstNoFire == null) {
                            firstNoFire = pair;
                        }
                        break;
                    default:
                        notEvaluable++;
                }
            }
            PairResult representative;
            if (firstFire != null) {
                representative = firstFire;
            } else if (firstNoFire != null) {
                representative = firstNoFire;
            } else if (firstAny != null) {
                representative = firstAny;
            } else if (controls.isEmpty() || faults.isEmpty()) {
                // A leg is entirely absent — the null-sibling verdict is
                // NOT_EVALUABLE and preserves the present leg's evidence.
                representative = verdict(t.name,
                        controls.isEmpty() ? null : controls.get(0),
                        faults.isEmpty() ? null : faults.get(0));
            } else {
                // Review A-F1 / B: both legs produced records but the correlator
                // aligned NONE of them (opposite asymmetric skips / disjoint
                // scenarios). Every record is unjoined; there is no like-for-like
                // sibling to verdict against, so the triple is NOT_EVALUABLE —
                // NEVER a cross-paired FIRE (the exact misalignment the rider
                // removes; positionally this branch was unreachable with both
                // legs non-empty).
                representative = new PairResult(t.name, null, null, PairVerdict.NOT_EVALUABLE,
                        "no records aligned by correlator (" + controls.size() + " control, "
                                + faults.size() + " fault record(s) — all unjoined)");
            }
            representative.controlRecordCount = controls.size();
            representative.faultRecordCount = faults.size();
            representative.firePairs = fires;
            representative.noFirePairs = noFires;
            representative.notEvaluablePairs = notEvaluable;
            representative.unjoinedRecords = join.unjoined;
            representative.joinMode = join.mode;
            representative.correlatorUnique = join.correlatorUnique;
            results.add(representative);
        }
        return results;
    }

    /** A join result: the aligned (control, fault) pairs, the unpaired count, the mode, uniqueness. */
    private static final class Join {
        final List<DataIntegrityRuntime.RunRecord[]> pairs = new ArrayList<>();
        int unjoined = 0;
        String mode = "positional";
        boolean correlatorUnique = false;
    }

    /**
     * G3 rider (H1 / comparator-C13): align the two legs' records by the
     * generation-time correlator when EVERY record on both sides carries one,
     * so an asymmetric skip in one leg (run #3's 71-vs-70) leaves only that
     * write unjoined instead of shifting every subsequent positional pair.
     * Falls back to the original positional join whenever any record lacks a
     * correlator (legacy suites), preserving prior behaviour byte-for-byte.
     * The correlator is unique per write ONLY if a method runs at most once
     * (review A-F2/C-MAJOR-2); duplicates degrade to FIFO-within-group, so the
     * join reports {@code correlatorUnique} rather than silently claiming full
     * alignment.
     */
    private static Join joinRecords(List<DataIntegrityRuntime.RunRecord> controls,
                                    List<DataIntegrityRuntime.RunRecord> faults) {
        Join j = new Join();
        if (canCorrelate(controls) && canCorrelate(faults)) {
            j.mode = "correlator";
            j.correlatorUnique = allUnique(controls) && allUnique(faults);
            Map<String, Deque<DataIntegrityRuntime.RunRecord>> faultBy = new LinkedHashMap<>();
            for (DataIntegrityRuntime.RunRecord f : faults) {
                faultBy.computeIfAbsent(f.correlationId, k -> new ArrayDeque<>()).add(f);
            }
            for (DataIntegrityRuntime.RunRecord c : controls) {
                Deque<DataIntegrityRuntime.RunRecord> q = faultBy.get(c.correlationId);
                if (q != null && !q.isEmpty()) {
                    j.pairs.add(new DataIntegrityRuntime.RunRecord[]{c, q.poll()});
                } else {
                    j.unjoined++;                       // control write with no fault sibling
                }
            }
            for (Deque<DataIntegrityRuntime.RunRecord> q : faultBy.values()) {
                j.unjoined += q.size();                 // fault writes with no control sibling
            }
            return j;
        }
        int joined = Math.min(controls.size(), faults.size());
        for (int i = 0; i < joined; i++) {
            j.pairs.add(new DataIntegrityRuntime.RunRecord[]{controls.get(i), faults.get(i)});
        }
        j.unjoined = Math.abs(controls.size() - faults.size());
        return j;
    }

    private static boolean allUnique(List<DataIntegrityRuntime.RunRecord> records) {
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (DataIntegrityRuntime.RunRecord r : records) {
            if (!seen.add(r.correlationId)) {
                return false;
            }
        }
        return true;
    }

    private static boolean canCorrelate(List<DataIntegrityRuntime.RunRecord> records) {
        if (records.isEmpty()) {
            return false;
        }
        for (DataIntegrityRuntime.RunRecord r : records) {
            if (r.correlationId == null) {
                return false;
            }
        }
        return true;
    }

    private static List<DataIntegrityRuntime.RunRecord> recordsFor(
            List<DataIntegrityRuntime.RunRecord> records, String tripleName) {
        List<DataIntegrityRuntime.RunRecord> out = new ArrayList<>();
        for (DataIntegrityRuntime.RunRecord r : records) {
            if (r.tripleName.equals(tripleName)) {
                out.add(r);
            }
        }
        return out;
    }

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
            perTriple.put(e.getKey(), fpStats(e.getValue(), cutoffs, timeoutMs));
        }
        out.put("perTriple", perTriple);
        JSONObject aggregate = fpStats(records, cutoffs, timeoutMs);
        out.put("aggregate", aggregate);

        int acked = aggregate.getInt("ackedBenignRuns");
        int fires = aggregate.getInt("fpFires");
        int observedGated = aggregate.getInt("observedGatedFpFires");
        int timeoutGated = aggregate.getInt("timeoutGatedFpFires");
        JSONObject bar = new JSONObject();
        bar.put("preRegistered", "bar v2: non-timeout-gated sync FP <= " + SYNC_FP_BAR + " over >= "
                + MIN_ACKED_FOR_BAR + " acked benign runs (plan section 4), evaluable only while the "
                + "observation gate held (gateResolvedFraction >= " + GATE_RESOLVED_FLOOR
                + " AND timeoutGatedFraction <= " + TIMEOUT_GATED_CAP
                + " — prep/hardening-wave-spec.md section 1)");
        if (acked < MIN_ACKED_FOR_BAR) {
            // A collapsed denominator must not auto-PASS the pre-registered bar.
            bar.put("value", JSONObject.NULL);
            bar.put("verdict", "NOT_EVALUABLE");
            bar.put("reason", "only " + acked + " acked benign run(s) — below the " + MIN_ACKED_FOR_BAR
                    + "-run minimum; diagnose invalid runs before reading the bar");
            out.put("syncFpBar", bar);
            return out;
        }
        // Acked records resolve to exactly one of the three gates, so the
        // timeout-gated fires ARE the timeout-gated acked records and the
        // resolved fraction is their complement. Both guards are kept (not
        // just the tighter cap) as defense in depth for future gate kinds.
        double timeoutGatedFraction = (double) timeoutGated / acked;
        double gateResolvedFraction = 1.0 - timeoutGatedFraction;
        bar.put("gateResolvedFraction", gateResolvedFraction);
        bar.put("timeoutGatedFraction", timeoutGatedFraction);
        if (timeoutGatedFraction > TIMEOUT_GATED_CAP || gateResolvedFraction < GATE_RESOLVED_FLOOR) {
            // Observation gate degraded: the numerator excludes exactly the
            // stratum that ballooned, so a PASS label would be uninformative.
            bar.put("value", JSONObject.NULL);
            bar.put("verdict", "NOT_EVALUABLE");
            bar.put("reason", (timeoutGatedFraction > TIMEOUT_GATED_CAP
                    ? "timeout-gated fraction " + timeoutGatedFraction + " exceeds the "
                            + TIMEOUT_GATED_CAP + " cap"
                    : "gate-resolved fraction " + gateResolvedFraction + " below the "
                            + GATE_RESOLVED_FLOOR + " floor")
                    + " — observation gate degraded (verify Jaeger health); the excluded timeout-gated "
                    + "stratum could hide benign losses, so no PASS/FAIL is claimed");
            out.put("syncFpBar", bar);
            return out;
        }
        double nonTimeoutFpRate = (double) observedGated / acked;
        bar.put("value", nonTimeoutFpRate);
        bar.put("verdict", nonTimeoutFpRate <= SYNC_FP_BAR ? "PASS" : "FAIL");
        if (fires > 0 && observedGated == 0) {
            bar.put("caveat", "all " + fires + " fire(s) are timeout-gated — the observation gate "
                    + "may have been unavailable (verify Jaeger reachability); the bar's numerator "
                    + "is then structurally zero and this PASS is weak evidence");
        }
        out.put("syncFpBar", bar);
        return out;
    }

    private static JSONObject fpStats(List<DataIntegrityRuntime.RunRecord> records, long[] cutoffs,
                                      long timeoutMs) {
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
        stats.put("nonTimeoutGatedFpRate",
                acked == 0 ? JSONObject.NULL : (double) observedGatedFires / acked);
        stats.put("gateHistogram", new JSONObject(gateHistogram));
        JSONArray curve = new JSONArray();
        for (long cutoff : cutoffs) {
            int firesAtCutoff = fires;
            // A presence observed after the cutoff would have been judged
            // absent under that (shorter) cutoff. At the cap itself the poll
            // loop is the authority: a presence seen on the final poll past
            // the cap did NOT fire, so it is not re-counted (review F4).
            if (cutoff < timeoutMs) {
                for (long presence : presenceTimesMs) {
                    if (presence > cutoff) {
                        firesAtCutoff++;
                    }
                }
            }
            JSONObject point = new JSONObject();
            point.put("timeoutMs", cutoff);
            point.put("fpRate", acked == 0 ? JSONObject.NULL : (double) firesAtCutoff / acked);
            curve.put(point);
        }
        stats.put("fpVsTimeoutCurve", curve);
        stats.put("curveNote", "points are observable only up to the pre-registered cap (" + timeoutMs
                + " ms); absence beyond the cap is censored, so no larger cutoffs are reported");
        return stats;
    }

    /** Cutoffs at or below the pre-registered cap, with the cap itself last. */
    private static long[] curveCutoffs(long timeoutMs) {
        List<Long> cutoffs = new ArrayList<>();
        for (long cutoff : CURVE_CUTOFFS_MS) {
            if (cutoff < timeoutMs) {
                cutoffs.add(cutoff);
            }
        }
        cutoffs.add(timeoutMs);
        long[] out = new long[cutoffs.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = cutoffs.get(i);
        }
        return out;
    }

    /** Writes the machine-readable pairing report (evidence + strata). */
    public static void writeReport(Path file, List<PairResult> results, String runId) throws IOException {
        writeReport(file, results, null, 0, runId, false, null);
    }

    /** Report incl. the B2.4 benign-probe section when probe records exist. */
    public static void writeReport(Path file, List<PairResult> results,
                                   List<DataIntegrityRuntime.RunRecord> probeRecords, long timeoutMs,
                                   String runId) throws IOException {
        writeReport(file, results, probeRecords, timeoutMs, runId, false, null);
    }

    /**
     * Full report writer. {@code f2ClearFailure} marks a report persisted on
     * the C-P1-3fix path: the flag-clear could not be confirmed and the F2
     * exception followed — the evidence is valid, the SUT state is suspect.
     * {@code f2FailedFlags} names the flags whose clear failed (review H6).
     */
    public static void writeReport(Path file, List<PairResult> results,
                                   List<DataIntegrityRuntime.RunRecord> probeRecords, long timeoutMs,
                                   String runId, boolean f2ClearFailure,
                                   List<FaultInjector.FaultTarget> f2FailedFlags) throws IOException {
        JSONObject report = new JSONObject();
        report.put("runId", runId);
        if (f2ClearFailure) {
            report.put("f2ClearFailure", true);
            if (f2FailedFlags != null && !f2FailedFlags.isEmpty()) {
                JSONArray failed = new JSONArray();
                for (FaultInjector.FaultTarget flag : f2FailedFlags) {
                    failed.put(String.valueOf(flag));
                }
                report.put("f2FailedFlags", failed);
            }
        }
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
            pair.put("firePairs", r.firePairs);
            pair.put("noFirePairs", r.noFirePairs);
            pair.put("notEvaluablePairs", r.notEvaluablePairs);
            pair.put("unjoinedRecords", r.unjoinedRecords);
            pair.put("joinMode", r.joinMode);
            pair.put("correlatorUnique", r.correlatorUnique);
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
        json.put("correlationId", record.correlationId == null ? JSONObject.NULL : record.correlationId);
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
        json.put("readbackHttpStatus",
                record.readbackHttpStatus == null ? JSONObject.NULL : record.readbackHttpStatus);
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
                .append(", non-timeout-gated FP rate ")
                .append(bar.isNull("value") ? "n/a" : String.valueOf(bar.getDouble("value")))
                .append(" -> bar ").append(bar.getString("verdict")).append("\n");
        if (bar.has("reason")) {
            out.append("  bar reason: ").append(bar.getString("reason")).append("\n");
        }
        out.append("  gate coverage: ").append(aggregate.getJSONObject("gateHistogram")).append("\n");
        out.append("  async: descriptive-only at Gate-1 (see report asyncDisclaimer)\n");
        return out.toString();
    }
}
