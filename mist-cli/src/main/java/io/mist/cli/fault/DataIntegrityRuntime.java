package io.mist.cli.fault;

import io.mist.cli.auth.MstAuthHandler;
import io.restassured.RestAssured;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Runtime half of the differential data-integrity oracle (B2.1/B2.2): the
 * writer emits {@link #beforeWrite}/{@link #afterWrite} calls into generated
 * tests for steps that match a registered target triple, and the pairing
 * executor (B1.3) brackets each JUnit run with {@link #beginRun}/{@link #endRun}
 * and reads the collected {@link RunRecord}s — the same in-process
 * static-holder channel the writer already uses for {@code LAST_VERDICT}.
 *
 * <p>With no active session every hook is a passthrough/no-op, so generated
 * tests that carry the emitted calls behave identically outside pairing runs.
 * Hooks never throw: any internal failure is recorded on the run record and
 * the underlying test proceeds untouched.
 *
 * <p>Soundness protocol pieces implemented here (TOOL-EXECUTION-PLAN B2.2):
 * <ul>
 *   <li><b>Isolation:</b> {@link #beforeWrite} rewrites the request's
 *   isolation-key fields to values fresh relative to the run's own baseline
 *   read-back (strategy per triple; the server-assigned {@code id} is
 *   stripped so a long generator value cannot turn the create into an
 *   update). X is request-derived — never read from the response.</li>
 *   <li><b>Quiescence:</b> {@link #afterWrite} polls the read-back until X
 *   appears (gate {@code OBSERVED_PRESENT}) or the pre-registered timeout
 *   elapses; an absent verdict is then upgraded to
 *   {@code OBSERVED_COMPLETE_ABSENT} only when the step's own Jaeger trace
 *   (exact W3C traceparent id) is present with a stable span set —
 *   otherwise it stays {@code TIMEOUT_ABSENT}, the lower-confidence stratum
 *   reported separately (R3 #1).</li>
 *   <li><b>Normalization:</b> membership is a projection onto the
 *   business-key fields, so volatile server fields (ids, timestamps) never
 *   enter the diff.</li>
 * </ul>
 */
public final class DataIntegrityRuntime {

    private static final Logger logger = LogManager.getLogger(DataIntegrityRuntime.class);

    /** Pre-registered quiescence knobs (B2.4: independent of any trap). */
    public static final String POLL_MS_PROPERTY = "mst.oracle.dataintegrity.poll.ms";
    public static final String TIMEOUT_MS_PROPERTY = "mst.oracle.dataintegrity.timeout.ms";
    /**
     * Settle window for the trace-completeness check (absence upgrade). Kept
     * well above typical OTel batch-exporter flush intervals so a
     * still-arriving trace is not mistaken for a complete one; the residual
     * weakness (spans dropped by the exporter yield a stable-but-partial
     * trace) is disclosed in the plan and bounded to the sync targets at
     * Gate-1.
     */
    public static final String TRACE_SETTLE_MS_PROPERTY = "mst.oracle.dataintegrity.trace.settle.ms";
    public static final long DEFAULT_POLL_MS = 500;
    public static final long DEFAULT_TIMEOUT_MS = 10_000;
    public static final long DEFAULT_TRACE_SETTLE_MS = 3_000;

    /** TrainTicket station catalogue consulted by the STATION_PAIR adapter. */
    static final String STATIONS_PATH = "/api/v1/stationservice/stations";

    /** How each run's read-back wait concluded (quiescence-gate stratum). */
    public enum QuiescenceGate {
        /** X appeared on the read-back — converged observation. */
        OBSERVED_PRESENT,
        /** X absent AND the write's own trace is complete/stable — high-confidence absence. */
        OBSERVED_COMPLETE_ABSENT,
        /** X absent at the wall-clock cap with no trace confirmation — lower-confidence stratum. */
        TIMEOUT_ABSENT,
        /** No wait ran (write not acknowledged, or infrastructure error). */
        NOT_APPLICABLE
    }

    /** One control- or fault-run observation for one triple. */
    public static final class RunRecord {
        public final String runLabel;
        public final String tripleName;
        public final String stepKey;
        public final Map<String, String> isolationKey;
        public final int ackHttpStatus;
        public final Integer ackBodyStatus;
        public final boolean acked;
        public final boolean baselineContainedX;
        public final boolean readbackContainedX;
        public final QuiescenceGate gate;
        public final int polls;
        public final long elapsedMs;
        public final String baselineBody;
        public final String lastReadbackBody;
        public final String error;
        /**
         * Hardening-wave R1fix: HTTP status of the last read-back GET (null
         * when no read-back ran). A non-2xx read-back is recorded as an
         * error, never scored as absence.
         */
        public final Integer readbackHttpStatus;
        /**
         * G3 rider (H1 / comparator-C13): a generation-time correlator —
         * {@code <testMethodName>#<stepIdx>} — stamped by the writer so the
         * per-record pairing join survives an asymmetric skip in one leg
         * (run #3's 71-vs-70). Identical across the control and fault legs
         * (the same generated file runs twice); {@code null} for legacy
         * records, which fall back to the positional join.
         */
        public final String correlationId;

        RunRecord(String runLabel, String tripleName, String stepKey, Map<String, String> isolationKey,
                  int ackHttpStatus, Integer ackBodyStatus, boolean acked,
                  boolean baselineContainedX, boolean readbackContainedX,
                  QuiescenceGate gate, int polls, long elapsedMs,
                  String baselineBody, String lastReadbackBody, String error) {
            this(runLabel, tripleName, stepKey, isolationKey, ackHttpStatus, ackBodyStatus, acked,
                    baselineContainedX, readbackContainedX, gate, polls, elapsedMs,
                    baselineBody, lastReadbackBody, error, null);
        }

        RunRecord(String runLabel, String tripleName, String stepKey, Map<String, String> isolationKey,
                  int ackHttpStatus, Integer ackBodyStatus, boolean acked,
                  boolean baselineContainedX, boolean readbackContainedX,
                  QuiescenceGate gate, int polls, long elapsedMs,
                  String baselineBody, String lastReadbackBody, String error,
                  Integer readbackHttpStatus) {
            this(runLabel, tripleName, stepKey, isolationKey, ackHttpStatus, ackBodyStatus, acked,
                    baselineContainedX, readbackContainedX, gate, polls, elapsedMs,
                    baselineBody, lastReadbackBody, error, readbackHttpStatus, null);
        }

        RunRecord(String runLabel, String tripleName, String stepKey, Map<String, String> isolationKey,
                  int ackHttpStatus, Integer ackBodyStatus, boolean acked,
                  boolean baselineContainedX, boolean readbackContainedX,
                  QuiescenceGate gate, int polls, long elapsedMs,
                  String baselineBody, String lastReadbackBody, String error,
                  Integer readbackHttpStatus, String correlationId) {
            this.runLabel = runLabel;
            this.tripleName = tripleName;
            this.stepKey = stepKey;
            this.isolationKey = Collections.unmodifiableMap(new LinkedHashMap<>(isolationKey));
            this.ackHttpStatus = ackHttpStatus;
            this.ackBodyStatus = ackBodyStatus;
            this.acked = acked;
            this.baselineContainedX = baselineContainedX;
            this.readbackContainedX = readbackContainedX;
            this.gate = gate;
            this.polls = polls;
            this.elapsedMs = elapsedMs;
            this.baselineBody = baselineBody;
            this.lastReadbackBody = lastReadbackBody;
            this.error = error;
            this.readbackHttpStatus = readbackHttpStatus;
            this.correlationId = correlationId;
        }
    }

    /**
     * Seam for the two HTTP shapes the runtime needs. PUBLIC so an out-of-package
     * g3 head-to-head harness can install a read-back Http that points at a
     * different host (e.g. the Sock Shop shipping enqueue lands in a RabbitMQ
     * queue, so the durable-effect read-back is the broker mgmt API, not the SUT).
     * Widening visibility only — the runtime's logic is unchanged.
     */
    public interface Http {
        /** Auth-applied GET against the SUT (path relative to RestAssured base URI). */
        HttpResponse getSut(String path);

        /** Raw absolute GET (Jaeger trace lookup). */
        HttpResponse getAbsolute(String url);
    }

    public static final class HttpResponse {
        public final int status;
        public final String body;

        public HttpResponse(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }

    private static final class Pending {
        final TargetTripleRegistry.Triple triple;
        final Map<String, String> isolationKey;
        final boolean baselineContainedX;
        final String baselineBody;
        final String error;
        final String correlationId;

        Pending(TargetTripleRegistry.Triple triple, Map<String, String> isolationKey,
                boolean baselineContainedX, String baselineBody, String error, String correlationId) {
            this.triple = triple;
            this.isolationKey = isolationKey;
            this.baselineContainedX = baselineContainedX;
            this.baselineBody = baselineBody;
            this.error = error;
            this.correlationId = correlationId;
        }
    }

    private static final class Session {
        final Map<String, TargetTripleRegistry.Triple> byStepKey;
        final String runLabel;
        final Http http;
        final long pollMs;
        final long timeoutMs;
        final long traceSettleMs;
        final List<RunRecord> records = Collections.synchronizedList(new ArrayList<>());
        final ThreadLocal<Pending> pending = new ThreadLocal<>();
        // Pairs claimed by ANY thread this run — two concurrent hooked methods
        // must never freshen onto the same (start,end) pair, or one thread's
        // persisted row satisfies the other's membership check (review F2).
        final Set<String> claimedPairs = java.util.concurrent.ConcurrentHashMap.newKeySet();
        // UX W0 (REVIEW-UX-RECONCILIATION U1/U5): true only for product
        // observe-mode sessions armed by MistRunner. The observe* accessors
        // below return nothing for paired sessions, so the generated
        // end-of-write check is structurally inert in every eval-harness run.
        final boolean observe;

        Session(List<TargetTripleRegistry.Triple> triples, String runLabel, Http http,
                long pollMs, long timeoutMs, long traceSettleMs) {
            this(triples, runLabel, http, pollMs, timeoutMs, traceSettleMs, false);
        }

        Session(List<TargetTripleRegistry.Triple> triples, String runLabel, Http http,
                long pollMs, long timeoutMs, long traceSettleMs, boolean observe) {
            this.observe = observe;
            Map<String, TargetTripleRegistry.Triple> keyed = new HashMap<>();
            for (TargetTripleRegistry.Triple t : triples) {
                if (keyed.put(t.writeEndpoint, t) != null) {
                    throw new IllegalArgumentException("duplicate write_endpoint '" + t.writeEndpoint
                            + "' across triples — records would be misattributed (review F5)");
                }
                if (t.isolationStrategy == TargetTripleRegistry.IsolationStrategy.STATION_PAIR
                        && !t.isolationKey.equals(java.util.Arrays.asList("startStation", "endStation"))) {
                    throw new IllegalArgumentException("station-pair strategy expects isolation_key"
                            + " [startStation, endStation]; triple '" + t.name + "' declares " + t.isolationKey);
                }
            }
            this.byStepKey = keyed;
            this.runLabel = runLabel;
            this.http = http;
            this.pollMs = pollMs;
            this.timeoutMs = timeoutMs;
            this.traceSettleMs = traceSettleMs;
        }
    }

    private static volatile Session session;

    // Test seam: lets the pairing-executor tests drive the PUBLIC beginRun
    // (the one PairedFaultExecutor calls) against a fake SUT. Never set in
    // production. Also used by a g3 head-to-head harness (via the public
    // installer below) to route the read-back GET at a non-SUT host.
    static volatile Http defaultHttpOverride = null;

    /**
     * Installs a read-back {@link Http} for a g3 head-to-head run whose durable
     * effect lives off the SUT (pass {@code null} to clear). The harness must
     * clear it in a finally so the override never leaks into a later run.
     */
    public static void installHttpOverride(Http http) {
        defaultHttpOverride = http;
    }

    private DataIntegrityRuntime() {
        // static hooks only
    }

    /** Activates recording for one control or fault run (B1.3 calls this). */
    public static void beginRun(List<TargetTripleRegistry.Triple> triples, String runLabel) {
        Http http = defaultHttpOverride != null ? defaultHttpOverride : new RestAssuredHttp();
        beginRun(triples, runLabel, http,
                Long.getLong(POLL_MS_PROPERTY, DEFAULT_POLL_MS),
                Long.getLong(TIMEOUT_MS_PROPERTY, DEFAULT_TIMEOUT_MS),
                Long.getLong(TRACE_SETTLE_MS_PROPERTY, DEFAULT_TRACE_SETTLE_MS));
    }

    /**
     * UX W0 (REVIEW-UX-RECONCILIATION U1): activates recording for a product
     * OBSERVE run — the single-leg, no-injection mode MistRunner arms around
     * normal test execution. Identical machinery to a paired leg except the
     * session is marked observe, which is what unlocks the
     * {@link #observeRecordFor}/{@link #observeTripleHasObservedPresent}
     * accessors consumed by the generated end-of-write check. Paired legs
     * (PairedFaultExecutor / g3 harnesses) never set the flag, so the check
     * is inert there by construction (U5).
     */
    public static void beginObserveRun(List<TargetTripleRegistry.Triple> triples, String runLabel) {
        Http http = defaultHttpOverride != null ? defaultHttpOverride : new RestAssuredHttp();
        beginRun(triples, runLabel, http,
                Long.getLong(POLL_MS_PROPERTY, DEFAULT_POLL_MS),
                Long.getLong(TIMEOUT_MS_PROPERTY, DEFAULT_TIMEOUT_MS),
                Long.getLong(TRACE_SETTLE_MS_PROPERTY, DEFAULT_TRACE_SETTLE_MS),
                /*observe=*/true);
    }

    /**
     * The record a completed hooked write produced in the CURRENT observe
     * session, or {@code null} when there is no session, the session is a
     * paired leg (U5 inertness), or no record carries the id. afterWrite is
     * synchronous, so by the time the generated check runs the record exists.
     */
    public static RunRecord observeRecordFor(String correlationId) {
        Session s = session;
        if (s == null || !s.observe || correlationId == null) {
            return null;
        }
        synchronized (s.records) {
            for (int i = s.records.size() - 1; i >= 0; i--) {
                RunRecord r = s.records.get(i);
                if (correlationId.equals(r.correlationId)) {
                    return r;
                }
            }
        }
        return null;
    }

    /**
     * UX W3 quarantine rule (REVIEW-UX-RECONCILIATION U8): a defect verdict is
     * trusted only for triples whose read-back has demonstrably worked — at
     * least one OBSERVED_PRESENT in the SAME observe session. A mis-bound
     * read-back (wrong endpoint / wrong shape) yields absence for every write;
     * this gate turns that failure mode into a quarantined warning instead of
     * a stream of false ACKED-BUT-LOST failures. Conservative by design
     * (precision-first): a session whose only write to a triple is genuinely
     * lost reports quarantined, not failed — disclosed limitation.
     */
    public static boolean observeTripleHasObservedPresent(String tripleName) {
        Session s = session;
        if (s == null || !s.observe || tripleName == null) {
            return false;
        }
        synchronized (s.records) {
            for (RunRecord r : s.records) {
                if (tripleName.equals(r.tripleName) && r.gate == QuiescenceGate.OBSERVED_PRESENT) {
                    return true;
                }
            }
        }
        return false;
    }

    static void beginRun(List<TargetTripleRegistry.Triple> triples, String runLabel, Http http,
                         long pollMs, long timeoutMs, long traceSettleMs) {
        beginRun(triples, runLabel, http, pollMs, timeoutMs, traceSettleMs, /*observe=*/false);
    }

    static void beginRun(List<TargetTripleRegistry.Triple> triples, String runLabel, Http http,
                         long pollMs, long timeoutMs, long traceSettleMs, boolean observe) {
        if (session != null) {
            throw new IllegalStateException("DataIntegrityRuntime: a run is already active");
        }
        // R7fix/C-P1-1: single-threaded execution was previously guaranteed
        // only by the caller setting mst.test.parallelism=1 — an invariant by
        // convention. Refuse to arm when the property explicitly says
        // otherwise (the ThreadLocal pending handoff and the verdict join
        // assume one JUnit thread).
        String parallelism = System.getProperty("mst.test.parallelism");
        if (parallelism != null) {
            try {
                if (Integer.parseInt(parallelism.trim()) > 1) {
                    throw new IllegalStateException("DataIntegrityRuntime: refusing to arm with "
                            + "mst.test.parallelism=" + parallelism.trim()
                            + " — the pairing hooks require single-threaded execution");
                }
            } catch (NumberFormatException ignored) {
                // unparseable value: leave it to the runner's own resolution
            }
        }
        session = new Session(triples, runLabel, http, pollMs, timeoutMs, traceSettleMs, observe);
        logger.info("DataIntegrity: {}run '{}' active for {} triple(s), poll={}ms timeout={}ms settle={}ms",
                observe ? "OBSERVE " : "", runLabel, triples.size(), pollMs, timeoutMs, traceSettleMs);
    }

    /** Deactivates the session and returns its records (B1.3 calls this). */
    public static List<RunRecord> endRun() {
        Session s = session;
        session = null;
        if (s == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(s.records);
    }

    /**
     * Emitted into generated tests just after the request-body literal: when a
     * pairing run is active and the step matches a registered triple, captures
     * the baseline read-back and returns the body with freshened isolation
     * keys; otherwise returns the body unchanged.
     */
    public static String beforeWrite(String stepKey, String requestBody) {
        return beforeWrite(stepKey, null, requestBody);
    }

    /**
     * G3-rider overload: {@code correlationId} = the writer's generation-time
     * {@code <method>#<stepIdx>} label, carried onto the RunRecord so the
     * pairing join can align by correlator instead of position.
     */
    public static String beforeWrite(String stepKey, String correlationId, String requestBody) {
        Session s = session;
        if (s == null) {
            return requestBody;
        }
        TargetTripleRegistry.Triple triple = s.byStepKey.get(stepKey);
        if (triple == null) {
            return requestBody;
        }
        drainOrphan(s);
        // A supplied-isolation triple has no body fields to freshen — the key
        // must come through beforeWriteSupplied. Reaching this hook instead is
        // a wiring bug; record it loudly rather than proceeding keyless.
        if (triple.isolationStrategy == TargetTripleRegistry.IsolationStrategy.SUPPLIED) {
            logger.warn("DataIntegrity[{}][{}]: supplied-isolation triple hit the freshening hook — "
                    + "use beforeWriteSupplied", s.runLabel, triple.name);
            s.pending.set(new Pending(triple, new LinkedHashMap<>(), false, null,
                    "supplied-isolation triple requires beforeWriteSupplied", correlationId));
            return requestBody;
        }
        try {
            HttpResponse baseline = s.http.getSut(readbackPath(triple));
            // R1fix: a failed baseline read must not silently weaken the
            // isolation guard (empty usedPairs, false baselineContainedX).
            if (baseline.status / 100 != 2) {
                logger.warn("DataIntegrity[{}][{}]: baseline read-back HTTP {} — passing body through",
                        s.runLabel, triple.name, baseline.status);
                s.pending.set(new Pending(triple, new LinkedHashMap<>(), false, baseline.body,
                        "baseline read-back HTTP " + baseline.status, correlationId));
                return requestBody;
            }
            Map<String, String> freshKey = new LinkedHashMap<>();
            String freshened = freshen(triple, requestBody, baseline.body, freshKey, s.http,
                    s.claimedPairs);
            boolean baselineHasX = containsKey(baseline.body, freshKey);
            s.pending.set(new Pending(triple, freshKey, baselineHasX, baseline.body, null, correlationId));
            logger.info("DataIntegrity[{}][{}]: baseline captured, fresh key {}",
                    s.runLabel, triple.name, freshKey);
            return freshened;
        } catch (RuntimeException e) {
            logger.warn("DataIntegrity[{}][{}]: beforeWrite failed ({}); passing body through",
                    s.runLabel, triple.name, e.toString());
            s.pending.set(new Pending(triple, new LinkedHashMap<>(), false, null,
                    "beforeWrite: " + e, correlationId));
            return requestBody;
        }
    }

    /**
     * G3 depth hook for supplied-isolation triples (bodyless writes such as the
     * TT cancel GET): the scenario's own setup established the key (e.g. the
     * freshly registered user) and hands it in explicitly; nothing is freshened
     * and {@code requestBody} (usually null) is returned untouched. Captures
     * the same baseline read-back as the freshening hook so VALUE_DELTA legs
     * compare against their own pre-write observation.
     */
    public static String beforeWriteSupplied(String stepKey, String correlationId, String requestBody,
                                             String keyField, String keyValue) {
        Session s = session;
        if (s == null) {
            return requestBody;
        }
        TargetTripleRegistry.Triple triple = s.byStepKey.get(stepKey);
        if (triple == null) {
            return requestBody;
        }
        drainOrphan(s);
        if (triple.isolationStrategy != TargetTripleRegistry.IsolationStrategy.SUPPLIED) {
            logger.warn("DataIntegrity[{}][{}]: beforeWriteSupplied on a {} triple — use beforeWrite",
                    s.runLabel, triple.name, triple.isolationStrategy);
            s.pending.set(new Pending(triple, new LinkedHashMap<>(), false, null,
                    "beforeWriteSupplied on a non-supplied triple (" + triple.isolationStrategy + ")",
                    correlationId));
            return requestBody;
        }
        if (keyField == null || !triple.isolationKey.contains(keyField)
                || keyValue == null || keyValue.trim().isEmpty()) {
            logger.warn("DataIntegrity[{}][{}]: unusable supplied key {}={}",
                    s.runLabel, triple.name, keyField, keyValue);
            s.pending.set(new Pending(triple, new LinkedHashMap<>(), false, null,
                    "unusable supplied key " + keyField + "=" + keyValue, correlationId));
            return requestBody;
        }
        Map<String, String> key = new LinkedHashMap<>();
        key.put(keyField, keyValue);
        try {
            HttpResponse baseline = s.http.getSut(readbackPath(triple));
            // B-F3: message names the supplied hook so its origin is greppable.
            if (baseline.status / 100 != 2) {
                logger.warn("DataIntegrity[{}][{}]: supplied baseline read-back HTTP {} — passing body through",
                        s.runLabel, triple.name, baseline.status);
                s.pending.set(new Pending(triple, new LinkedHashMap<>(), false, baseline.body,
                        "supplied baseline read-back HTTP " + baseline.status, correlationId));
                return requestBody;
            }
            // Review DEPTH-A F2: a 2xx body that is not a parseable collection
            // would read as an all-null probe surface (every value "absent") —
            // reject it here rather than mis-attribute movement later.
            if (!parsesToCollection(baseline.body)) {
                logger.warn("DataIntegrity[{}][{}]: supplied baseline is not a parseable collection — error",
                        s.runLabel, triple.name);
                s.pending.set(new Pending(triple, new LinkedHashMap<>(), false, baseline.body,
                        "supplied baseline read-back is not a parseable collection", correlationId));
                return requestBody;
            }
            // Review DEPTH-A F1: for value-delta the baseline is a numeric datum,
            // not a membership fact — an earlier scenario step (register / pay)
            // still settling would poison it. Require a stable pre-write read
            // (two identical probe values) or record an error, never a guess.
            if (triple.readbackMode == TargetTripleRegistry.ReadbackMode.VALUE_DELTA) {
                HttpResponse baseline2 = s.http.getSut(readbackPath(triple));
                if (baseline2.status / 100 != 2 || !parsesToCollection(baseline2.body)) {
                    s.pending.set(new Pending(triple, new LinkedHashMap<>(), false, baseline2.body,
                            "supplied baseline re-read unusable (HTTP " + baseline2.status + ")",
                            correlationId));
                    return requestBody;
                }
                if (valueDiffers(extractProbeValue(triple, baseline.body, key),
                        extractProbeValue(triple, baseline2.body, key))) {
                    logger.warn("DataIntegrity[{}][{}]: pre-write baseline not stable for {} — error",
                            s.runLabel, triple.name, key);
                    s.pending.set(new Pending(triple, new LinkedHashMap<>(), false, baseline2.body,
                            "pre-write baseline not stable (probe moved before the write)", correlationId));
                    return requestBody;
                }
                baseline = baseline2; // the settled read is the baseline of record
            }
            // VALUE_DELTA X is defined against this same baseline, so the
            // baseline trivially contains no X; membership keeps its meaning.
            boolean baselineHasX = triple.readbackMode == TargetTripleRegistry.ReadbackMode.MEMBERSHIP
                    && containsKey(baseline.body, key);
            s.pending.set(new Pending(triple, key, baselineHasX, baseline.body, null, correlationId));
            logger.info("DataIntegrity[{}][{}]: baseline captured, supplied key {}",
                    s.runLabel, triple.name, key);
            return requestBody;
        } catch (RuntimeException e) {
            logger.warn("DataIntegrity[{}][{}]: beforeWriteSupplied failed ({}); passing body through",
                    s.runLabel, triple.name, e.toString());
            s.pending.set(new Pending(triple, new LinkedHashMap<>(), false, null,
                    "beforeWriteSupplied: " + e, correlationId));
            return requestBody;
        }
    }

    /**
     * Review H1(i): an unconsumed pending means the PREVIOUS hooked write died
     * between its hooks (transport failure before afterWrite). Emit a synthetic
     * error record in its slot so the per-record join stays aligned instead of
     * silently shifting.
     */
    private static void drainOrphan(Session s) {
        Pending orphaned = s.pending.get();
        if (orphaned == null) {
            return;
        }
        s.pending.remove();
        logger.warn("DataIntegrity[{}][{}]: previous write orphaned (no afterWrite — transport "
                + "failure?) — synthetic error record keeps the join aligned",
                s.runLabel, orphaned.triple.name);
        s.records.add(new RunRecord(s.runLabel, orphaned.triple.name,
                orphaned.triple.writeEndpoint, orphaned.isolationKey, -1, null, false,
                orphaned.baselineContainedX, false, QuiescenceGate.NOT_APPLICABLE, 0, 0,
                orphaned.baselineBody, null,
                "hook orphaned: beforeWrite ran but afterWrite never fired", null,
                orphaned.correlationId));
    }

    /**
     * Emitted into generated tests just after the step response is captured:
     * evaluates the acknowledgement, runs the quiescence-gated read-back wait,
     * and records the run observation. Never throws.
     */
    public static void afterWrite(String stepKey, int httpStatus, String responseBody, String traceId) {
        afterWrite(stepKey, null, httpStatus, responseBody, traceId);
    }

    /**
     * G3-rider overload: {@code correlationId} = the writer's generation-time
     * {@code <method>#<stepIdx>} label; every record this call emits carries it
     * so the pairing join can align by correlator instead of position.
     */
    public static void afterWrite(String stepKey, String correlationId, int httpStatus,
                                  String responseBody, String traceId) {
        Session s = session;
        if (s == null) {
            return;
        }
        TargetTripleRegistry.Triple triple = s.byStepKey.get(stepKey);
        if (triple == null) {
            return;
        }
        Pending pending = s.pending.get();
        s.pending.remove();
        if (pending == null || pending.triple != triple) {
            s.records.add(new RunRecord(s.runLabel, triple.name, stepKey, new LinkedHashMap<>(),
                    httpStatus, null, false, false, false, QuiescenceGate.NOT_APPLICABLE, 0, 0,
                    null, null, "afterWrite without matching beforeWrite", null, correlationId));
            return;
        }
        try {
            Integer bodyStatus = bodyStatus(responseBody);
            boolean acked = httpStatus / 100 == 2 && (bodyStatus == null || bodyStatus == 1);
            if (pending.error != null || pending.isolationKey.isEmpty()) {
                s.records.add(new RunRecord(s.runLabel, triple.name, stepKey, pending.isolationKey,
                        httpStatus, bodyStatus, acked, pending.baselineContainedX, false,
                        QuiescenceGate.NOT_APPLICABLE, 0, 0, pending.baselineBody, null,
                        pending.error != null ? pending.error : "no isolation key established", null,
                        pending.correlationId));
                return;
            }
            if (!acked) {
                // Base relation is vacuous without an acknowledgement; one
                // immediate read-back is kept as evidence. Review DEPTH-B F1:
                // an error body is never evidence — only a 2xx read is scanned.
                HttpResponse now = s.http.getSut(readbackPath(triple));
                boolean x = now.status / 100 == 2
                        && probeVerdict(triple, now.body, pending) == XVerdict.PRESENT;
                s.records.add(new RunRecord(s.runLabel, triple.name, stepKey, pending.isolationKey,
                        httpStatus, bodyStatus, false, pending.baselineContainedX, x,
                        QuiescenceGate.NOT_APPLICABLE, 1, 0, pending.baselineBody, now.body, null,
                        now.status, pending.correlationId));
                return;
            }

            long start = System.nanoTime();
            int polls = 0;
            int nonOkPolls = 0;
            boolean present = false;
            String last = null;
            int lastStatus = -1;
            QuiescenceGate gate;
            while (true) {
                HttpResponse readback = s.http.getSut(readbackPath(triple));
                polls++;
                lastStatus = readback.status;
                boolean ok = readback.status / 100 == 2;
                // R1fix v2 (review H2): error bodies are never evidence — a
                // non-2xx poll is not scanned — but a transient blip must not
                // burn the record either (the old abort-on-first made runs on
                // a 503-prone SUT attrition-prone). Keep polling; only the
                // DECISIVE read (timeout-hit poll / post-settle re-read) must
                // be 2xx for an absence verdict.
                if (ok) {
                    last = readback.body;
                    XVerdict v = probeVerdict(triple, last, pending);
                    if (v == XVerdict.VANISHED) {
                        recordReadbackError(s, triple, stepKey, pending, httpStatus, bodyStatus,
                                polls, (System.nanoTime() - start) / 1_000_000, last, lastStatus,
                                "value-delta probe row vanished from a 2xx read-back — "
                                        + "unreliable/truncated surface, not a value change");
                        return;
                    }
                    if (v == XVerdict.PRESENT) {
                        present = true;
                        gate = QuiescenceGate.OBSERVED_PRESENT;
                        break;
                    }
                } else {
                    nonOkPolls++;
                }
                long elapsedMs = (System.nanoTime() - start) / 1_000_000;
                if (elapsedMs >= s.timeoutMs) {
                    if (!ok) {
                        recordReadbackError(s, triple, stepKey, pending, httpStatus, bodyStatus,
                                polls, elapsedMs, readback.body, lastStatus,
                                "read-back HTTP " + readback.status + " on the decisive read ("
                                        + nonOkPolls + " of " + polls + " polls non-2xx)");
                        return;
                    }
                    if (traceComplete(s.http, traceId, s.traceSettleMs)) {
                        // R4fix: absence was sampled BEFORE the settle window;
                        // re-read once so a write landing during the settle is
                        // not labeled a high-confidence loss.
                        HttpResponse recheck = s.http.getSut(readbackPath(triple));
                        polls++;
                        last = recheck.body;
                        lastStatus = recheck.status;
                        if (recheck.status / 100 != 2) {
                            recordReadbackError(s, triple, stepKey, pending, httpStatus, bodyStatus,
                                    polls, (System.nanoTime() - start) / 1_000_000, last, lastStatus,
                                    "read-back HTTP " + recheck.status + " on the post-settle re-read");
                            return;
                        }
                        XVerdict v = probeVerdict(triple, last, pending);
                        if (v == XVerdict.VANISHED) {
                            recordReadbackError(s, triple, stepKey, pending, httpStatus, bodyStatus,
                                    polls, (System.nanoTime() - start) / 1_000_000, last, lastStatus,
                                    "value-delta probe row vanished on the post-settle re-read — "
                                            + "unreliable/truncated surface, not a value change");
                            return;
                        }
                        if (v == XVerdict.PRESENT) {
                            present = true;
                            gate = QuiescenceGate.OBSERVED_PRESENT;
                            break;
                        }
                        gate = QuiescenceGate.OBSERVED_COMPLETE_ABSENT;
                    } else {
                        gate = QuiescenceGate.TIMEOUT_ABSENT;
                    }
                    // R1fix: at the pre-registered collection bound an absence
                    // is unverifiable (the surface may window/truncate).
                    if (triple.readbackBound > 0
                            && extractItems(last).size() >= triple.readbackBound) {
                        recordReadbackError(s, triple, stepKey, pending, httpStatus, bodyStatus,
                                polls, (System.nanoTime() - start) / 1_000_000, last, lastStatus,
                                "read-back collection at the pre-registered bound ("
                                        + triple.readbackBound + ") — absence unverifiable");
                        return;
                    }
                    break;
                }
                sleep(s.pollMs);
            }
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            s.records.add(new RunRecord(s.runLabel, triple.name, stepKey, pending.isolationKey,
                    httpStatus, bodyStatus, true, pending.baselineContainedX, present,
                    gate, polls, elapsedMs, pending.baselineBody, last, null, lastStatus,
                    pending.correlationId));
            logger.info("DataIntegrity[{}][{}]: acked={} X-present={} gate={} polls={} in {}ms",
                    s.runLabel, triple.name, true, present, gate, polls, elapsedMs);
        } catch (RuntimeException e) {
            logger.warn("DataIntegrity[{}][{}]: afterWrite failed ({})", s.runLabel, triple.name, e.toString());
            s.records.add(new RunRecord(s.runLabel, triple.name, stepKey, pending.isolationKey,
                    httpStatus, null, false, pending.baselineContainedX, false,
                    QuiescenceGate.NOT_APPLICABLE, 0, 0, pending.baselineBody, null,
                    "afterWrite: " + e, null, pending.correlationId));
        }
    }

    // ── internals ──────────────────────────────────────────────────────────

    /** R1fix: one shape for every "the read-back itself failed" record. */
    private static void recordReadbackError(Session s, TargetTripleRegistry.Triple triple,
                                            String stepKey, Pending pending, int httpStatus,
                                            Integer bodyStatus, int polls, long elapsedMs,
                                            String lastBody, int lastStatus, String error) {
        logger.warn("DataIntegrity[{}][{}]: {} — record is an error, not an absence",
                s.runLabel, triple.name, error);
        s.records.add(new RunRecord(s.runLabel, triple.name, stepKey, pending.isolationKey,
                httpStatus, bodyStatus, true, pending.baselineContainedX, false,
                QuiescenceGate.NOT_APPLICABLE, polls, elapsedMs, pending.baselineBody, lastBody,
                error, lastStatus, pending.correlationId));
    }

    static String readbackPath(TargetTripleRegistry.Triple triple) {
        String endpoint = triple.readbackEndpoint;
        if (!endpoint.startsWith("GET ")) {
            throw new IllegalArgumentException("readback_endpoint must be a GET, got: " + endpoint);
        }
        return endpoint.substring(4).trim();
    }

    /**
     * Rewrites the isolation-key fields of {@code requestBody} to values fresh
     * relative to {@code baselineBody}, per the triple's strategy. All other
     * fields keep their generator/pool-provided values (B1.2).
     */
    static String freshen(TargetTripleRegistry.Triple triple, String requestBody, String baselineBody,
                          Map<String, String> outKey, Http http, Set<String> claimedPairs) {
        JSONObject body = requestBody == null || requestBody.trim().isEmpty()
                ? new JSONObject()
                : new JSONObject(requestBody);
        // Server-assigned identity: a >=32-char generator value would flip the
        // create into an update on adminroute (RouteServiceImpl keeps long ids).
        body.remove("id");
        switch (triple.isolationStrategy) {
            case FRESH_STRINGS:
                for (String field : triple.isolationKey) {
                    // Form-preserving freshness: SUT entities may type a JSON
                    // string field as java.util.UUID (TrainTicket Contacts
                    // accountId), where an arbitrary string 400s at Jackson —
                    // so a UUID-shaped pool value gets a fresh UUID and only
                    // free-form strings get the mist- prefix.
                    String previous = body.has(field) ? String.valueOf(body.opt(field)) : null;
                    String value = freshValueLike(previous);
                    body.put(field, value);
                    outKey.put(field, value);
                }
                break;
            case STATION_PAIR:
                freshStationPair(body, baselineBody, outKey, http, claimedPairs);
                break;
            default:
                throw new IllegalStateException("unhandled strategy " + triple.isolationStrategy);
        }
        return body.toString();
    }

    private static final java.util.regex.Pattern UUID_SHAPE = java.util.regex.Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    static String freshValueLike(String previous) {
        if (previous != null && UUID_SHAPE.matcher(previous).matches()) {
            return UUID.randomUUID().toString();
        }
        return "mist-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    /**
     * TrainTicket adminroute adapter: the SUT rejects routes whose stations do
     * not exist, so freshness comes from an ordered pair of EXISTING stations
     * that no baseline route already uses.
     */
    private static void freshStationPair(JSONObject body, String baselineBody,
                                         Map<String, String> outKey, Http http,
                                         Set<String> claimedPairs) {
        HttpResponse stationsResp = http.getSut(STATIONS_PATH);
        List<String> stations = new ArrayList<>();
        for (Object item : extractItems(stationsResp.body)) {
            if (item instanceof JSONObject && ((JSONObject) item).has("name")) {
                stations.add(String.valueOf(((JSONObject) item).get("name")));
            }
        }
        if (stations.size() < 2) {
            throw new IllegalStateException("station catalogue has fewer than 2 stations; cannot build a fresh pair");
        }
        Set<String> usedPairs = new HashSet<>();
        for (Object item : extractItems(baselineBody)) {
            if (item instanceof JSONObject) {
                JSONObject route = (JSONObject) item;
                if (route.has("startStation") && route.has("endStation")) {
                    usedPairs.add(route.get("startStation") + "|" + route.get("endStation"));
                }
            }
        }
        List<String> shuffled = new ArrayList<>(stations);
        Collections.shuffle(shuffled, ThreadLocalRandom.current());
        for (String start : shuffled) {
            for (String end : shuffled) {
                if (start.equals(end) || usedPairs.contains(start + "|" + end)) {
                    continue;
                }
                if (!claimedPairs.add(start + "|" + end)) {
                    // Another thread already freshened onto this pair this run.
                    continue;
                }
                body.put("startStation", start);
                body.put("endStation", end);
                body.put("stationList", start + "," + end);
                body.put("distanceList", "0," + (100 + ThreadLocalRandom.current().nextInt(900)));
                outKey.put("startStation", start);
                outKey.put("endStation", end);
                return;
            }
        }
        throw new IllegalStateException("no unused (start,end) station pair left among "
                + stations.size() + " stations and " + usedPairs.size() + " baseline routes");
    }

    /** X-presence with an explicit VANISHED failure state (value-delta only). */
    private enum XVerdict { ABSENT, PRESENT, VANISHED }

    /**
     * X-presence dispatch: MEMBERSHIP asks whether the key appears as a
     * collection item; VALUE_DELTA asks whether the probed value moved away
     * from this leg's own baseline (the refund landing on the /account
     * aggregate). A row that was present at baseline but is gone from a 2xx
     * read is VANISHED — a truncated/unreliable surface, NOT a value change
     * (review DEPTH-A F2): reporting it as movement would latch a fault-leg
     * false negative, so the caller turns it into an error record. Same
     * polling, gating, and verdict machinery otherwise.
     */
    private static XVerdict probeVerdict(TargetTripleRegistry.Triple triple, String body,
                                         Pending pending) {
        if (triple.readbackMode == TargetTripleRegistry.ReadbackMode.VALUE_DELTA) {
            String base = extractProbeValue(triple, pending.baselineBody, pending.isolationKey);
            String cur = extractProbeValue(triple, body, pending.isolationKey);
            if (base != null && cur == null) {
                return XVerdict.VANISHED;
            }
            return valueDiffers(base, cur) ? XVerdict.PRESENT : XVerdict.ABSENT;
        }
        return containsKey(body, pending.isolationKey) ? XVerdict.PRESENT : XVerdict.ABSENT;
    }

    /**
     * VALUE_DELTA probe: the {@code valueField} of the collection item whose
     * {@code matchField} equals the supplied key value; null when the item or
     * either field is absent (an absent account row reads as "no value yet").
     */
    static String extractProbeValue(TargetTripleRegistry.Triple triple, String body,
                                    Map<String, String> key) {
        TargetTripleRegistry.ValueProbe probe = triple.valueProbe;
        String expected = probe == null ? null : key.get(probe.matchField);
        if (expected == null) {
            return null;
        }
        for (Object item : extractItems(body)) {
            if (!(item instanceof JSONObject)) {
                continue;
            }
            JSONObject obj = (JSONObject) item;
            if (obj.has(probe.matchField)
                    && String.valueOf(obj.get(probe.matchField)).equals(expected)
                    && obj.has(probe.valueField)) {
                return String.valueOf(obj.get(probe.valueField));
            }
        }
        return null;
    }

    /**
     * Numeric-aware inequality: "100.0" and "100.00" are the same balance.
     * Both-absent is "no movement"; a value appearing or vanishing is one.
     */
    static boolean valueDiffers(String baseline, String current) {
        if (baseline == null && current == null) {
            return false;
        }
        if (baseline == null || current == null) {
            return true;
        }
        // Review DEPTH-A F4: two reads of the same balance can differ only in
        // surrounding whitespace; trim before comparing (BigDecimal already
        // absorbs scale drift like "100.0" vs "100.00").
        String b = baseline.trim();
        String c = current.trim();
        try {
            return new java.math.BigDecimal(b).compareTo(new java.math.BigDecimal(c)) != 0;
        } catch (NumberFormatException e) {
            return !b.equals(c);
        }
    }

    /**
     * Membership as a business-key projection: true when any collection item
     * matches every isolation-key field (volatile fields never compared).
     */
    static boolean containsKey(String collectionBody, Map<String, String> key) {
        if (key.isEmpty()) {
            return false;
        }
        for (Object item : extractItems(collectionBody)) {
            if (!(item instanceof JSONObject)) {
                continue;
            }
            JSONObject obj = (JSONObject) item;
            boolean all = true;
            for (Map.Entry<String, String> e : key.entrySet()) {
                if (!obj.has(e.getKey()) || !String.valueOf(obj.get(e.getKey())).equals(e.getValue())) {
                    all = false;
                    break;
                }
            }
            if (all) {
                return true;
            }
        }
        return false;
    }

    /**
     * Collections arrive in one of three shapes: a bare array {@code [..]}, the
     * TrainTicket envelope {@code {status,msg,data:[..]}}, or the HAL/HATEOAS
     * {@code {_embedded:{<rel>:[..]}}} convention (Spring HATEOAS — Sock Shop).
     * Public: the G2 comparator reuses this envelope parsing so both oracles read
     * collections identically (a JSON utility, not oracle logic). The HAL branch is
     * inert on TrainTicket bodies (they never carry {@code _embedded}).
     */
    public static List<Object> extractItems(String body) {
        List<Object> items = new ArrayList<>();
        if (body == null || body.trim().isEmpty()) {
            return items;
        }
        String trimmed = body.trim();
        try {
            if (trimmed.startsWith("[")) {
                addAll(items, new JSONArray(trimmed));
                return items;
            }
            JSONObject obj = new JSONObject(trimmed);
            Object data = obj.opt("data");
            if (data instanceof JSONArray) {
                addAll(items, (JSONArray) data);
                return items;
            }
            // HAL/HATEOAS (Spring HATEOAS, IETF draft-kelly-json-hal): a collection
            // resource nests its rows under _embedded.<relation>. A read-back GET
            // returns one relation in practice, but flatten every relation for
            // generality — membership stays sound because isolation keys are fresh
            // unique strings, so a match cannot cross relations. INERT on TrainTicket:
            // its bodies are bare arrays or {status,msg,data:[]}, never _embedded.
            Object embedded = obj.opt("_embedded");
            if (embedded instanceof JSONObject) {
                JSONObject emb = (JSONObject) embedded;
                for (String rel : emb.keySet()) {
                    Object v = emb.opt(rel);
                    if (v instanceof JSONArray) {
                        addAll(items, (JSONArray) v);
                    } else if (v instanceof JSONObject) {
                        items.add(v);
                    }
                }
            }
        } catch (RuntimeException e) {
            logger.debug("DataIntegrity: unparseable collection body ({}) — treating as empty", e.toString());
        }
        return items;
    }

    private static void addAll(List<Object> items, JSONArray array) {
        for (int i = 0; i < array.length(); i++) {
            items.add(array.get(i));
        }
    }

    /**
     * True when {@code body} is a JSON collection surface — a bare array or a
     * {@code {..,data:[..]}} envelope. Distinguishes "parsed, row absent"
     * (a legitimate no-value-yet) from "garbage/non-collection" (an error),
     * which {@link #extractItems} alone cannot (it returns empty for both).
     */
    static boolean parsesToCollection(String body) {
        if (body == null || body.trim().isEmpty()) {
            return false;
        }
        String trimmed = body.trim();
        try {
            if (trimmed.startsWith("[")) {
                new JSONArray(trimmed);
                return true;
            }
            JSONObject obj = new JSONObject(trimmed);
            if (obj.opt("data") instanceof JSONArray) {
                return true;
            }
            // HAL: a collection resource with at least one embedded relation
            // (array or single object). See extractItems. KNOWN LIMITATION: an
            // EMPTY HAL collection ({_embedded:{}}, or Spring's omitted-_embedded
            // {_links:..}) reads false here — asymmetric with empty {data:[]} which
            // is true. Only matters for a future value-delta-on-HAL SUPPLIED triple
            // (an empty baseline would record an error, not a clean empty); dead for
            // SS-B membership. See g3-sut2-hal-readback-finding.md runbook.
            Object embedded = obj.opt("_embedded");
            if (embedded instanceof JSONObject) {
                JSONObject emb = (JSONObject) embedded;
                for (String rel : emb.keySet()) {
                    Object v = emb.opt(rel);
                    if (v instanceof JSONArray || v instanceof JSONObject) {
                        return true;
                    }
                }
            }
            return false;
        } catch (RuntimeException e) {
            return false;
        }
    }

    static Integer bodyStatus(String body) {
        if (body == null || body.trim().isEmpty()) {
            return null;
        }
        try {
            JSONObject obj = new JSONObject(body.trim());
            return obj.has("status") ? obj.getInt("status") : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Absence upgrade check: the step's own trace (exact traceparent id) is
     * present in Jaeger with a stable, non-empty span set across two looks.
     * Conservative on any failure — absence stays timeout-gated.
     */
    static boolean traceComplete(Http http, String traceId, long settleMs) {
        String base = System.getProperty("jaeger.base.url");
        if (base == null || base.trim().isEmpty() || traceId == null || traceId.trim().isEmpty()) {
            return false;
        }
        try {
            // Same convention as the writer-emitted lookup: jaeger.base.url
            // already ends in /api (e.g. .../jaeger/ui/api), so append only
            // /traces/<id>.
            String url = base.replaceAll("/$", "") + "/traces/" + traceId.trim();
            int first = spanCount(http.getAbsolute(url));
            if (first <= 0) {
                return false;
            }
            sleep(settleMs);
            return spanCount(http.getAbsolute(url)) == first;
        } catch (RuntimeException e) {
            logger.debug("DataIntegrity: Jaeger completeness check failed ({}) — staying timeout-gated",
                    e.toString());
            return false;
        }
    }

    private static int spanCount(HttpResponse response) {
        if (response.status != 200 || response.body == null) {
            return -1;
        }
        try {
            JSONArray data = new JSONObject(response.body).optJSONArray("data");
            if (data == null || data.length() == 0) {
                return -1;
            }
            JSONArray spans = data.getJSONObject(0).optJSONArray("spans");
            return spans == null ? -1 : spans.length();
        } catch (RuntimeException e) {
            return -1;
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted during read-back wait", e);
        }
    }

    /** Production HTTP: auth-applied SUT GETs + raw Jaeger GETs. */
    private static final class RestAssuredHttp implements Http {
        @Override
        public HttpResponse getSut(String path) {
            io.restassured.response.Response response = MstAuthHandler
                    .applyAuth(RestAssured.given().urlEncodingEnabled(false), path)
                    .when().get(path);
            return new HttpResponse(response.getStatusCode(), response.getBody().asString());
        }

        @Override
        public HttpResponse getAbsolute(String url) {
            io.restassured.response.Response response = RestAssured.given().when().get(url);
            return new HttpResponse(response.getStatusCode(), response.getBody().asString());
        }
    }
}
