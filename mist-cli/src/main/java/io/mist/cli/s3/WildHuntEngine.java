package io.mist.cli.s3;

import io.mist.cli.fault.DataIntegrityRuntime;
import io.mist.cli.fault.TargetTripleRegistry;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * S3 wild-hunt observe-runner engine (plan {@code s3-wildhunt-plan.md} rev 2.1 §2; pre-registered in
 * the freeze §6 "Step-5-as-amended" row, 2026-07-13). Drives pinned fault-free journeys through
 * MIST's observe mode and classifies every acked write at WINDOW END:
 *
 * <pre>
 *   RAW       = acked ∧ error==null ∧ absent-at-cap (gate ∈ {TIMEOUT_ABSENT, OBSERVED_COMPLETE_ABSENT})
 *               ∧ the W3 quarantine gate open for the triple (≥1 OBSERVED_PRESENT in-session,
 *               evaluated at window end)
 *   CONFIRMED = RAW ∧ the ≥T+5min re-probe is ABSENT (same transport + the SAME runtime predicate
 *               via {@link DataIntegrityRuntime#reProbe}; a non-2xx/VANISHED/bound-hit re-probe is
 *               an ERROR record, never CONFIRMED)
 * </pre>
 *
 * Re-probes are scheduled W3-INDEPENDENTLY for every acked-absent-at-cap write (review B-r2) and
 * executed between journeys (single-threaded). Quarantined triples (no OBSERVED_PRESENT by window
 * end) are reported, never S3-eligible. The mid-window circuit breaker (review B-M5) trips on ≥5
 * consecutive RAW candidates or a trailing-50 candidate rate &gt;20% and throws — exclusion of
 * breaker-window flags is LIST-driven at triage, never decided here (review A-R2/C-1).
 *
 * <p>The engine emits OUR-SIDE raw artifacts (absolute times allowed here; the Python assembler
 * rebases to relative times and produces the rater-facing sidecar): a per-window ledger, a window
 * log with the pinned denominators, and one flag bundle per acked-absent write. Markers use the
 * neutral {@code corpus-w<seq>-<12hex>} grammar (review B-B2 — never a tool string; unit-tested
 * against the B4 banned list).
 */
public final class WildHuntEngine {

    /** Ack of one bound write (status + body, the success envelope judged by the runtime). */
    public static final class Ack {
        public final int status;
        public final String body;

        public Ack(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }

    /** One bound write's HTTP call, performed by the journey through the recorder. */
    public interface WriteCall {
        Ack call() throws Exception;
    }

    /** A pinned journey: performs its steps via the context; returns when done. */
    public interface Journey {
        void run(JourneyContext ctx) throws Exception;
    }

    /** Optional per-SUT hook (OTel trace snapshots at RAW time + stability re-fetch at CONFIRM). */
    public interface FlagHook {
        default void onAckedAbsent(WriteEntry e) {}
        default void onReProbeDone(WriteEntry e) {}
    }

    /** Per-write ledger entry (our-side; absolute times allowed — the assembler rebases). */
    public static final class WriteEntry {
        public final int index;
        public final TargetTripleRegistry.Triple triple;
        public final String corr;
        public final Map<String, String> isolationKey;
        public final String marker;
        public final List<RawRecorder.Rec> journeyRecords; // snapshot of the journey up to + incl. this write
        public Ack ack;
        public DataIntegrityRuntime.RunRecord record;       // observe-session record (post afterWrite)
        public long reProbeDueAtMs;                          // 0 = none scheduled
        public DataIntegrityRuntime.ReProbeResult reProbe;   // null until executed
        public long reProbeAtMs;
        public String traceId;                               // optional (OTel journeys set it)
        public String classification = "pending";            // window-end: raw-confirmed / raw-delayed /
                                                             // raw-error / quarantined / present / error

        WriteEntry(int index, TargetTripleRegistry.Triple triple, String corr,
                   Map<String, String> isolationKey, String marker, List<RawRecorder.Rec> snapshot) {
            this.index = index;
            this.triple = triple;
            this.corr = corr;
            this.isolationKey = isolationKey;
            this.marker = marker;
            this.journeyRecords = snapshot;
        }
    }

    /** Thrown when the mid-window breaker trips; the orchestrator repairs + resumes per runbook. */
    public static final class BreakerTripped extends RuntimeException {
        public BreakerTripped(String msg) {
            super(msg);
        }
    }

    private final String sutName;
    private final String windowId;
    private final Path outDir;
    private final long reProbeDelayMs;
    private final Random markerRng;
    private final FlagHook hook;
    /** Human-neutral read-back descriptor for the bundle (review A-m3 — never a Java class name). */
    public volatile String probeDescriptor = "GET <read-back>";
    /** Per-triple probe descriptor override (multi-triple windows, e.g. TT's 3 admin-basic sites). */
    private final Map<String, String> tripleProbes = new LinkedHashMap<>();

    /** Sets a per-triple read-back descriptor; unset triples fall back to {@link #probeDescriptor}. */
    public void setProbe(String tripleName, String descriptor) {
        tripleProbes.put(tripleName, descriptor);
    }
    private int seq = 0;
    private final List<WriteEntry> ledger = new ArrayList<>();
    private final List<String> breakerEvents = new ArrayList<>();
    private int consecutiveCandidates = 0;
    private final java.util.Deque<Boolean> trailing = new java.util.ArrayDeque<>();
    private long readSteps = 0;
    private long writeSteps = 0;
    private int failedJourneys = 0;

    public WildHuntEngine(String sutName, String windowId, Path outDir, long reProbeDelayMs,
                          long markerSeed, FlagHook hook) {
        this.sutName = sutName;
        this.windowId = windowId;
        this.outDir = outDir;
        this.reProbeDelayMs = reProbeDelayMs;
        this.markerRng = new Random(markerSeed);
        this.hook = hook == null ? new FlagHook() {} : hook;
    }

    /** The neutral marker grammar (freeze row pin 11): {@code corpus-w<seq>-<12hex>}. */
    public String nextMarker() {
        byte[] b = new byte[6];
        markerRng.nextBytes(b);
        StringBuilder hex = new StringBuilder();
        for (byte x : b) {
            hex.append(String.format("%02x", x));
        }
        return "corpus-w" + (++seq) + "-" + hex;
    }

    /** Journey-facing context: raw recording + the bound-write hook path. */
    public final class JourneyContext {
        public final RawRecorder recorder = new RawRecorder();

        public String nextMarker() {
            return WildHuntEngine.this.nextMarker();
        }

        public void countRead() {
            readSteps++;
        }

        /**
         * One bound write: baseline via {@code beforeWriteSupplied}, the journey's HTTP call, the
         * observe poll via {@code afterWrite}, the ledger entry + W3-independent re-probe schedule.
         */
        public WriteEntry write(TargetTripleRegistry.Triple triple, String keyField, String marker,
                                WriteCall call) throws Exception {
            writeSteps++;
            int idx = ledger.size();
            String corr = triple.name + "#w" + idx;
            DataIntegrityRuntime.beforeWriteSupplied(triple.writeEndpoint, corr, null, keyField, marker);
            Ack ack = call.call();
            DataIntegrityRuntime.afterWrite(triple.writeEndpoint, corr, ack.status, ack.body, null);
            Map<String, String> key = new LinkedHashMap<>();
            key.put(keyField, marker);
            WriteEntry e = new WriteEntry(idx, triple, corr, key, marker,
                    new ArrayList<>(recorder.records()));
            e.ack = ack;
            e.record = DataIntegrityRuntime.observeRecordFor(corr);
            ledger.add(e);
            boolean ackedAbsent = e.record != null && e.record.acked
                    && !e.record.readbackContainedX && e.record.error == null;
            if (ackedAbsent) {
                e.reProbeDueAtMs = System.currentTimeMillis() + reProbeDelayMs;
                hook.onAckedAbsent(e);
            }
            feedBreaker(ackedAbsent);
            return e;
        }
    }

    private void feedBreaker(boolean candidate) {
        consecutiveCandidates = candidate ? consecutiveCandidates + 1 : 0;
        trailing.addLast(candidate);
        if (trailing.size() > 50) {
            trailing.removeFirst();
        }
        long hits = trailing.stream().filter(x -> x).count();
        boolean rateTrip = trailing.size() >= 50 && hits > 10; // >20% of trailing 50
        if (consecutiveCandidates >= 5 || rateTrip) {
            String ev = "breaker@write" + (ledger.size() - 1) + " consecutive=" + consecutiveCandidates
                    + " trailing50=" + hits;
            breakerEvents.add(ev);
            throw new BreakerTripped(ev + " — pause + runbook health check (plan §2); resume appends"
                    + " to the same window id");
        }
    }

    /** Executes all DUE re-probes (call between journeys; single-threaded). */
    public void pumpReProbes(DataIntegrityRuntime.Http transportOrNull, ReProbeMarkerSwap swap) {
        long now = System.currentTimeMillis();
        for (WriteEntry e : ledger) {
            if (e.reProbeDueAtMs > 0 && e.reProbe == null && now >= e.reProbeDueAtMs) {
                Runnable restore = swap == null ? null : swap.point(e.marker);
                try {
                    e.reProbe = DataIntegrityRuntime.reProbe(e.triple, transportOrNull,
                            e.isolationKey, e.record == null ? null : e.record.baselineBody);
                    e.reProbeAtMs = System.currentTimeMillis();
                } finally {
                    if (restore != null) {
                        restore.run();
                    }
                }
                hook.onReProbeDone(e);
            }
        }
    }

    /** OTel's marker-scoped SQL read-back needs the supplier re-pointed for the re-probe (§2). */
    public interface ReProbeMarkerSwap {
        /** Points the transport at {@code marker}; returns the restore action. */
        Runnable point(String marker);
    }

    /** True while any scheduled re-probe is still pending (window drain loop). */
    public boolean reProbesPending() {
        return ledger.stream().anyMatch(e -> e.reProbeDueAtMs > 0 && e.reProbe == null);
    }

    /**
     * WINDOW-END classification (W3 + RAW/CONFIRMED applied here, not at write time — review B-r2):
     * quarantined (no OBSERVED_PRESENT for the triple in-session) · raw-confirmed · raw-delayed
     * (present at re-probe) · raw-error (re-probe unusable) · present · error.
     */
    public void classify() {
        for (WriteEntry e : ledger) {
            if (e.record == null || e.record.error != null) {
                e.classification = "error";
                continue;
            }
            if (!e.record.acked) {
                e.classification = "not-acked";
                continue;
            }
            if (e.record.readbackContainedX) {
                e.classification = "present";
                continue;
            }
            boolean gateOpen = DataIntegrityRuntime.observeTripleHasObservedPresent(e.triple.name);
            if (!gateOpen) {
                e.classification = "quarantined";
                continue;
            }
            if (e.reProbe == null) {
                e.classification = "raw-unprobed"; // should not happen if the drain loop ran
            } else if (e.reProbe.outcome == DataIntegrityRuntime.ReProbeOutcome.ABSENT) {
                e.classification = "raw-confirmed";
            } else if (e.reProbe.outcome == DataIntegrityRuntime.ReProbeOutcome.PRESENT) {
                e.classification = "raw-delayed"; // present-at-re-probe (delayed-beyond-cap)
            } else {
                e.classification = "raw-error";   // unusable evidence, never CONFIRMED
            }
        }
    }

    /** Writes the ledger + window log + one raw flag-bundle per acked-absent write. */
    public void emit(String mistCommit, String sutVersionRef, JSONObject environmentGuard)
            throws Exception {
        Files.createDirectories(outDir.resolve("flags"));
        JSONArray ledgerJson = new JSONArray();
        Map<String, int[]> perEndpoint = new LinkedHashMap<>(); // endpoint -> [writes, confirmed]
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (WriteEntry e : ledger) {
            counts.merge(e.classification, 1, Integer::sum);
            int[] pe = perEndpoint.computeIfAbsent(e.triple.writeEndpoint, k -> new int[2]);
            pe[0]++;
            if ("raw-confirmed".equals(e.classification)) {
                pe[1]++;
            }
            JSONObject j = new JSONObject();
            j.put("index", e.index).put("endpoint", e.triple.writeEndpoint)
                    .put("marker", e.marker).put("classification", e.classification)
                    .put("ackStatus", e.ack == null ? JSONObject.NULL : e.ack.status)
                    .put("gate", e.record == null ? JSONObject.NULL : e.record.gate.name())
                    .put("recordError", e.record == null || e.record.error == null
                            ? JSONObject.NULL : e.record.error)
                    .put("reProbe", e.reProbe == null ? JSONObject.NULL : e.reProbe.outcome.name())
                    .put("traceId", e.traceId == null ? JSONObject.NULL : e.traceId);
            ledgerJson.put(j);
            if (e.reProbeDueAtMs > 0) {
                writeFlagBundle(e, mistCommit, sutVersionRef);
            }
        }
        JSONObject log = new JSONObject();
        log.put("window", windowId).put("sut", sutName)
                .put("writes_total", ledger.size())
                .put("read_steps", readSteps).put("write_steps", writeSteps)
                .put("failed_journeys", failedJourneys)
                .put("write_path_fraction",
                        writeSteps + readSteps == 0 ? 0 : (double) writeSteps / (writeSteps + readSteps))
                .put("classification_counts", new JSONObject(counts))
                .put("breaker_events", new JSONArray(breakerEvents))
                .put("environment_guard", environmentGuard == null ? new JSONObject() : environmentGuard);
        JSONObject perEp = new JSONObject();
        for (Map.Entry<String, int[]> en : perEndpoint.entrySet()) {
            perEp.put(en.getKey(), new JSONObject()
                    .put("acked_writes", en.getValue()[0]).put("raw_confirmed", en.getValue()[1]));
        }
        log.put("per_endpoint", perEp);
        Files.write(outDir.resolve("window-log.json"),
                log.toString(1).getBytes(StandardCharsets.UTF_8));
        Files.write(outDir.resolve("ledger.json"),
                ledgerJson.toString(1).getBytes(StandardCharsets.UTF_8));
    }

    private void writeFlagBundle(WriteEntry e, String mistCommit, String sutVersionRef)
            throws Exception {
        JSONObject b = new JSONObject();
        b.put("bundle_version", 1).put("producer", "wildflag-bundle").put("mist_commit", mistCommit);
        b.put("window", windowId);
        b.put("sut", new JSONObject().put("name", sutName).put("version_ref", sutVersionRef));
        b.put("write_index", e.index).put("endpoint", e.triple.writeEndpoint)
                .put("marker", e.marker).put("classification", e.classification)
                .put("probe_descriptor", tripleProbes.getOrDefault(e.triple.name, probeDescriptor));
        JSONArray recs = new JSONArray();
        for (RawRecorder.Rec r : e.journeyRecords) {
            recs.put(r.toJson());
        }
        b.put("journey_records", recs);
        JSONObject obs = new JSONObject();
        if (e.record != null) {
            obs.put("baseline_body", e.record.baselineBody == null ? JSONObject.NULL : e.record.baselineBody);
            obs.put("at_cap_body", e.record.lastReadbackBody == null ? JSONObject.NULL : e.record.lastReadbackBody);
            obs.put("at_cap_polls", e.record.polls).put("at_cap_elapsed_ms", e.record.elapsedMs);
            // At-cap read-back HTTP status (SqlDurableReadback maps its psql exec onto 200/rows; a
            // non-2xx read-back is an ERROR, never a candidate — so for any EMITTED bundle this is a
            // 2xx by the RAW invariant). Carried so the assembler renders the observation faithfully
            // rather than assuming, and can assert the invariant.
            obs.put("at_cap_status", e.record.readbackHttpStatus == null
                    ? JSONObject.NULL : e.record.readbackHttpStatus);
        }
        if (e.reProbe != null) {
            obs.put("re_probe_outcome", e.reProbe.outcome.name())
                    .put("re_probe_status", e.reProbe.httpStatus)
                    .put("re_probe_body", e.reProbe.body == null ? JSONObject.NULL : e.reProbe.body)
                    .put("re_probe_abs_ms", e.reProbeAtMs);
        }
        b.put("observations", obs);
        Files.write(outDir.resolve("flags").resolve("flag-w" + e.index + ".json"),
                b.toString(1).getBytes(StandardCharsets.UTF_8));
    }

    /** Runs one observe session over the whole window (plan §2: ONE session per SUT window). */
    public void runWindow(List<TargetTripleRegistry.Triple> triples, List<Journey> journeySequence,
                          DataIntegrityRuntime.Http reProbeTransportOrNull, ReProbeMarkerSwap swap)
            throws Exception {
        DataIntegrityRuntime.beginObserveRun(triples, "s3-" + windowId);
        try {
            int done = 0;
            int total = journeySequence.size();
            for (Journey j : journeySequence) {
                // A journey that throws is an OPERATIONAL failure (transient port-forward drop /
                // connection reset over a long window), NOT a SUT behavior: skip + count + continue
                // so one blip never aborts a multi-hundred-write session (the write is simply not
                // recorded, so it is excluded from the denominator like a breaker-window flag).
                try {
                    j.run(new JourneyContext());
                } catch (Exception ex) {
                    failedJourneys++;
                    System.err.println("journey " + done + " failed (operational, skipped): " + ex);
                }
                pumpReProbes(reProbeTransportOrNull, swap);
                if (++done % 25 == 0) {
                    System.out.println("progress " + done + "/" + total + " writes=" + ledger.size()
                            + " failed=" + failedJourneys);
                }
            }
            // Drain: wait out the remaining re-probe delays (single-threaded, between no journeys).
            while (reProbesPending()) {
                Thread.sleep(2000);
                pumpReProbes(reProbeTransportOrNull, swap);
            }
            // Classification MUST run inside the observe session: the W3 accessor
            // (observeTripleHasObservedPresent) reads the CURRENT session, which endRun clears.
            classify();
        } finally {
            DataIntegrityRuntime.endRun();
        }
    }

    public List<WriteEntry> ledger() {
        return Collections.unmodifiableList(ledger);
    }
}
