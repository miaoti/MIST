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
    static final long DEFAULT_POLL_MS = 500;
    static final long DEFAULT_TIMEOUT_MS = 10_000;

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

        RunRecord(String runLabel, String tripleName, String stepKey, Map<String, String> isolationKey,
                  int ackHttpStatus, Integer ackBodyStatus, boolean acked,
                  boolean baselineContainedX, boolean readbackContainedX,
                  QuiescenceGate gate, int polls, long elapsedMs,
                  String baselineBody, String lastReadbackBody, String error) {
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
        }
    }

    /** Test seam for the two HTTP shapes the runtime needs. */
    interface Http {
        /** Auth-applied GET against the SUT (path relative to RestAssured base URI). */
        HttpResponse getSut(String path);

        /** Raw absolute GET (Jaeger trace lookup). */
        HttpResponse getAbsolute(String url);
    }

    static final class HttpResponse {
        final int status;
        final String body;

        HttpResponse(int status, String body) {
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

        Pending(TargetTripleRegistry.Triple triple, Map<String, String> isolationKey,
                boolean baselineContainedX, String baselineBody, String error) {
            this.triple = triple;
            this.isolationKey = isolationKey;
            this.baselineContainedX = baselineContainedX;
            this.baselineBody = baselineBody;
            this.error = error;
        }
    }

    private static final class Session {
        final Map<String, TargetTripleRegistry.Triple> byStepKey;
        final String runLabel;
        final Http http;
        final long pollMs;
        final long timeoutMs;
        final List<RunRecord> records = Collections.synchronizedList(new ArrayList<>());
        final ThreadLocal<Pending> pending = new ThreadLocal<>();

        Session(List<TargetTripleRegistry.Triple> triples, String runLabel, Http http,
                long pollMs, long timeoutMs) {
            Map<String, TargetTripleRegistry.Triple> keyed = new HashMap<>();
            for (TargetTripleRegistry.Triple t : triples) {
                keyed.put(t.writeEndpoint, t);
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
        }
    }

    private static volatile Session session;

    // Test seam: lets the pairing-executor tests drive the PUBLIC beginRun
    // (the one PairedFaultExecutor calls) against a fake SUT. Never set in
    // production.
    static volatile Http defaultHttpOverride = null;

    private DataIntegrityRuntime() {
        // static hooks only
    }

    /** Activates recording for one control or fault run (B1.3 calls this). */
    public static void beginRun(List<TargetTripleRegistry.Triple> triples, String runLabel) {
        Http http = defaultHttpOverride != null ? defaultHttpOverride : new RestAssuredHttp();
        beginRun(triples, runLabel, http,
                Long.getLong(POLL_MS_PROPERTY, DEFAULT_POLL_MS),
                Long.getLong(TIMEOUT_MS_PROPERTY, DEFAULT_TIMEOUT_MS));
    }

    static void beginRun(List<TargetTripleRegistry.Triple> triples, String runLabel, Http http,
                         long pollMs, long timeoutMs) {
        if (session != null) {
            throw new IllegalStateException("DataIntegrityRuntime: a run is already active");
        }
        session = new Session(triples, runLabel, http, pollMs, timeoutMs);
        logger.info("DataIntegrity: run '{}' active for {} triple(s), poll={}ms timeout={}ms",
                runLabel, triples.size(), pollMs, timeoutMs);
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
        Session s = session;
        if (s == null) {
            return requestBody;
        }
        TargetTripleRegistry.Triple triple = s.byStepKey.get(stepKey);
        if (triple == null) {
            return requestBody;
        }
        try {
            HttpResponse baseline = s.http.getSut(readbackPath(triple));
            Map<String, String> freshKey = new LinkedHashMap<>();
            String freshened = freshen(triple, requestBody, baseline.body, freshKey, s.http);
            boolean baselineHasX = containsKey(baseline.body, freshKey);
            s.pending.set(new Pending(triple, freshKey, baselineHasX, baseline.body, null));
            logger.info("DataIntegrity[{}][{}]: baseline captured, fresh key {}",
                    s.runLabel, triple.name, freshKey);
            return freshened;
        } catch (RuntimeException e) {
            logger.warn("DataIntegrity[{}][{}]: beforeWrite failed ({}); passing body through",
                    s.runLabel, triple.name, e.toString());
            s.pending.set(new Pending(triple, new LinkedHashMap<>(), false, null,
                    "beforeWrite: " + e));
            return requestBody;
        }
    }

    /**
     * Emitted into generated tests just after the step response is captured:
     * evaluates the acknowledgement, runs the quiescence-gated read-back wait,
     * and records the run observation. Never throws.
     */
    public static void afterWrite(String stepKey, int httpStatus, String responseBody, String traceId) {
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
                    null, null, "afterWrite without matching beforeWrite"));
            return;
        }
        try {
            Integer bodyStatus = bodyStatus(responseBody);
            boolean acked = httpStatus / 100 == 2 && (bodyStatus == null || bodyStatus == 1);
            if (pending.error != null || pending.isolationKey.isEmpty()) {
                s.records.add(new RunRecord(s.runLabel, triple.name, stepKey, pending.isolationKey,
                        httpStatus, bodyStatus, acked, pending.baselineContainedX, false,
                        QuiescenceGate.NOT_APPLICABLE, 0, 0, pending.baselineBody, null,
                        pending.error != null ? pending.error : "no isolation key established"));
                return;
            }
            if (!acked) {
                // Base relation is vacuous without an acknowledgement; one
                // immediate read-back is kept as evidence.
                HttpResponse now = s.http.getSut(readbackPath(triple));
                s.records.add(new RunRecord(s.runLabel, triple.name, stepKey, pending.isolationKey,
                        httpStatus, bodyStatus, false, pending.baselineContainedX,
                        containsKey(now.body, pending.isolationKey),
                        QuiescenceGate.NOT_APPLICABLE, 1, 0, pending.baselineBody, now.body, null));
                return;
            }

            long start = System.nanoTime();
            int polls = 0;
            boolean present = false;
            String last = null;
            QuiescenceGate gate;
            while (true) {
                HttpResponse readback = s.http.getSut(readbackPath(triple));
                last = readback.body;
                polls++;
                if (containsKey(last, pending.isolationKey)) {
                    present = true;
                    gate = QuiescenceGate.OBSERVED_PRESENT;
                    break;
                }
                long elapsedMs = (System.nanoTime() - start) / 1_000_000;
                if (elapsedMs >= s.timeoutMs) {
                    gate = traceComplete(s.http, traceId, s.pollMs)
                            ? QuiescenceGate.OBSERVED_COMPLETE_ABSENT
                            : QuiescenceGate.TIMEOUT_ABSENT;
                    break;
                }
                sleep(s.pollMs);
            }
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            s.records.add(new RunRecord(s.runLabel, triple.name, stepKey, pending.isolationKey,
                    httpStatus, bodyStatus, true, pending.baselineContainedX, present,
                    gate, polls, elapsedMs, pending.baselineBody, last, null));
            logger.info("DataIntegrity[{}][{}]: acked={} X-present={} gate={} polls={} in {}ms",
                    s.runLabel, triple.name, true, present, gate, polls, elapsedMs);
        } catch (RuntimeException e) {
            logger.warn("DataIntegrity[{}][{}]: afterWrite failed ({})", s.runLabel, triple.name, e.toString());
            s.records.add(new RunRecord(s.runLabel, triple.name, stepKey, pending.isolationKey,
                    httpStatus, null, false, pending.baselineContainedX, false,
                    QuiescenceGate.NOT_APPLICABLE, 0, 0, pending.baselineBody, null,
                    "afterWrite: " + e));
        }
    }

    // ── internals ──────────────────────────────────────────────────────────

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
                          Map<String, String> outKey, Http http) {
        JSONObject body = requestBody == null || requestBody.trim().isEmpty()
                ? new JSONObject()
                : new JSONObject(requestBody);
        // Server-assigned identity: a >=32-char generator value would flip the
        // create into an update on adminroute (RouteServiceImpl keeps long ids).
        body.remove("id");
        switch (triple.isolationStrategy) {
            case FRESH_STRINGS:
                for (String field : triple.isolationKey) {
                    String value = "mist-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
                    body.put(field, value);
                    outKey.put(field, value);
                }
                break;
            case STATION_PAIR:
                freshStationPair(body, baselineBody, outKey, http);
                break;
            default:
                throw new IllegalStateException("unhandled strategy " + triple.isolationStrategy);
        }
        return body.toString();
    }

    /**
     * TrainTicket adminroute adapter: the SUT rejects routes whose stations do
     * not exist, so freshness comes from an ordered pair of EXISTING stations
     * that no baseline route already uses.
     */
    private static void freshStationPair(JSONObject body, String baselineBody,
                                         Map<String, String> outKey, Http http) {
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

    /** TrainTicket convention: collections arrive as {@code {status,msg,data:[..]}} or a bare array. */
    static List<Object> extractItems(String body) {
        List<Object> items = new ArrayList<>();
        if (body == null || body.trim().isEmpty()) {
            return items;
        }
        String trimmed = body.trim();
        try {
            JSONArray array;
            if (trimmed.startsWith("[")) {
                array = new JSONArray(trimmed);
            } else {
                JSONObject obj = new JSONObject(trimmed);
                Object data = obj.opt("data");
                if (!(data instanceof JSONArray)) {
                    return items;
                }
                array = (JSONArray) data;
            }
            for (int i = 0; i < array.length(); i++) {
                items.add(array.get(i));
            }
        } catch (RuntimeException e) {
            logger.debug("DataIntegrity: unparseable collection body ({}) — treating as empty", e.toString());
        }
        return items;
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
