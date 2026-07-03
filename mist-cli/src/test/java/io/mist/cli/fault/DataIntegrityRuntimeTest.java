package io.mist.cli.fault;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Pins the B2.1/B2.2 runtime against a scripted HTTP seam: passthrough when
 * inactive, isolation-key freshening per strategy (id stripped, non-key
 * fields preserved), business-key membership, and every quiescence-gate
 * branch (OBSERVED_PRESENT / OBSERVED_COMPLETE_ABSENT / TIMEOUT_ABSENT /
 * NOT_APPLICABLE). Hooks must never throw into the generated test.
 */
public class DataIntegrityRuntimeTest {

    private static final String ROUTE_STEP = "POST /api/v1/adminrouteservice/adminroute";
    private static final String ROUTE_READBACK = "/api/v1/adminrouteservice/adminroute";
    private static final String CONTACT_STEP = "POST /api/v1/adminbasicservice/adminbasic/contacts";
    private static final String CONTACT_READBACK = "/api/v1/adminbasicservice/adminbasic/contacts";

    private static final class ScriptedHttp implements DataIntegrityRuntime.Http {
        final Map<String, Deque<DataIntegrityRuntime.HttpResponse>> byPath = new HashMap<>();
        final Map<String, DataIntegrityRuntime.HttpResponse> lastServed = new HashMap<>();
        final List<String> requests = new ArrayList<>();
        final Map<String, Deque<DataIntegrityRuntime.HttpResponse>> byUrl = new HashMap<>();

        void script(String path, String... bodies) {
            Deque<DataIntegrityRuntime.HttpResponse> queue =
                    byPath.computeIfAbsent(path, k -> new ArrayDeque<>());
            for (String body : bodies) {
                queue.add(new DataIntegrityRuntime.HttpResponse(200, body));
            }
        }

        void scriptAbsolute(String url, String... bodies) {
            Deque<DataIntegrityRuntime.HttpResponse> queue =
                    byUrl.computeIfAbsent(url, k -> new ArrayDeque<>());
            for (String body : bodies) {
                queue.add(new DataIntegrityRuntime.HttpResponse(200, body));
            }
        }

        @Override
        public DataIntegrityRuntime.HttpResponse getSut(String path) {
            requests.add(path);
            Deque<DataIntegrityRuntime.HttpResponse> queue = byPath.get(path);
            if (queue != null && !queue.isEmpty()) {
                DataIntegrityRuntime.HttpResponse next = queue.poll();
                lastServed.put(path, next);
                return next;
            }
            // Queue exhausted: the last scripted response repeats (a stable
            // collection), or an empty collection when never scripted.
            DataIntegrityRuntime.HttpResponse last = lastServed.get(path);
            return last != null ? last
                    : new DataIntegrityRuntime.HttpResponse(200, "{\"status\":1,\"data\":[]}");
        }

        @Override
        public DataIntegrityRuntime.HttpResponse getAbsolute(String url) {
            requests.add(url);
            Deque<DataIntegrityRuntime.HttpResponse> queue = byUrl.get(url);
            if (queue == null || queue.isEmpty()) {
                return new DataIntegrityRuntime.HttpResponse(404, "");
            }
            return queue.size() == 1 ? queue.peek() : queue.poll();
        }
    }

    private ScriptedHttp http;
    private String previousJaegerBase;

    private static TargetTripleRegistry.Triple routeTriple() {
        return shipped("adminroute-create");
    }

    private static TargetTripleRegistry.Triple contactTriple() {
        return shipped("adminbasic-contacts-create");
    }

    private static TargetTripleRegistry.Triple shipped(String name) {
        TargetTripleRegistry.Registry registry = TargetTripleRegistry.parse(
                DataIntegrityRuntimeTest.class.getResourceAsStream(
                        "/My-Example/trainticket/target-triples.yaml"), "shipped");
        for (TargetTripleRegistry.Triple t : registry.triples) {
            if (t.name.equals(name)) {
                return t;
            }
        }
        throw new IllegalStateException("no shipped triple named " + name);
    }

    private void begin(String runLabel, TargetTripleRegistry.Triple... triples) {
        DataIntegrityRuntime.beginRun(Arrays.asList(triples), runLabel, http, 1, 30, 1);
    }

    @Before
    public void setUp() {
        http = new ScriptedHttp();
        previousJaegerBase = System.getProperty("jaeger.base.url");
        System.clearProperty("jaeger.base.url");
    }

    @After
    public void tearDown() {
        DataIntegrityRuntime.endRun();
        if (previousJaegerBase == null) {
            System.clearProperty("jaeger.base.url");
        } else {
            System.setProperty("jaeger.base.url", previousJaegerBase);
        }
    }

    @Test
    public void inactive_hooksArePassthroughNoops() {
        String body = "{\"accountId\":\"x\"}";
        assertEquals(body, DataIntegrityRuntime.beforeWrite(CONTACT_STEP, body));
        DataIntegrityRuntime.afterWrite(CONTACT_STEP, 200, "{\"status\":1}", "abc");
        assertTrue(DataIntegrityRuntime.endRun().isEmpty());
    }

    @Test
    public void unmatchedStep_isPassthrough() {
        begin("control", contactTriple());
        String body = "{\"whatever\":true}";
        assertEquals(body, DataIntegrityRuntime.beforeWrite("POST /api/v1/other", body));
        assertTrue(DataIntegrityRuntime.endRun().isEmpty());
    }

    @Test
    public void freshStrings_rewritesKeysStripsIdKeepsPoolFields() {
        begin("control", contactTriple());
        // accountId is UUID-typed on the SUT (java.util.UUID) — the pool value
        // is UUID-shaped and the fresh value must stay UUID-shaped or Jackson
        // 400s the request.
        String poolUuid = "123e4567-e89b-42d3-a456-426614174000";
        String body = "{\"accountId\":\"" + poolUuid + "\",\"documentNumber\":\"123\",\"documentType\":1,"
                + "\"name\":\"pool-name\",\"phoneNumber\":\"555\",\"id\":\"a-very-long-generator-id-0123456789\"}";
        String freshened = DataIntegrityRuntime.beforeWrite(CONTACT_STEP, body);
        JSONObject out = new JSONObject(freshened);
        assertTrue("UUID-shaped field stays UUID-shaped",
                out.getString("accountId").matches(
                        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
        assertNotEquals(poolUuid, out.getString("accountId"));
        assertTrue("free-form field gets the mist prefix",
                out.getString("documentNumber").startsWith("mist-"));
        assertEquals("pool-name", out.getString("name"));
        assertEquals(1, out.getInt("documentType"));
        assertEquals("555", out.getString("phoneNumber"));
        assertFalse("server-assigned id must be stripped", out.has("id"));
    }

    @Test
    public void stationPair_picksExistingUnusedPairAndRewritesLists() {
        http.script("/api/v1/stationservice/stations",
                "{\"status\":1,\"data\":[{\"name\":\"suzhou\"},{\"name\":\"wuxi\"},{\"name\":\"nanjing\"}]}");
        http.script(ROUTE_READBACK,
                "{\"status\":1,\"data\":[{\"startStation\":\"suzhou\",\"endStation\":\"wuxi\"}]}");
        begin("control", routeTriple());
        String freshened = DataIntegrityRuntime.beforeWrite(ROUTE_STEP,
                "{\"startStation\":\"old\",\"endStation\":\"old2\",\"stationList\":\"old,old2\","
                        + "\"distanceList\":\"0,1\",\"loginId\":\"admin\"}");
        JSONObject out = new JSONObject(freshened);
        String start = out.getString("startStation");
        String end = out.getString("endStation");
        assertTrue(Arrays.asList("suzhou", "wuxi", "nanjing").contains(start));
        assertTrue(Arrays.asList("suzhou", "wuxi", "nanjing").contains(end));
        assertNotEquals(start, end);
        assertFalse("baseline pair must not be reused", start.equals("suzhou") && end.equals("wuxi"));
        assertEquals(start + "," + end, out.getString("stationList"));
        assertTrue(out.getString("distanceList").startsWith("0,"));
        assertEquals("pool fields survive", "admin", out.getString("loginId"));
    }

    @Test
    public void stationPair_exhaustedPairs_recordsErrorAndPassesThrough() {
        http.script("/api/v1/stationservice/stations",
                "{\"status\":1,\"data\":[{\"name\":\"a1\"},{\"name\":\"b1\"}]}");
        http.script(ROUTE_READBACK,
                "{\"status\":1,\"data\":[{\"startStation\":\"a1\",\"endStation\":\"b1\"},"
                        + "{\"startStation\":\"b1\",\"endStation\":\"a1\"}]}");
        begin("control", routeTriple());
        String original = "{\"startStation\":\"x\",\"endStation\":\"y\"}";
        assertEquals("body must pass through on freshen failure",
                original, DataIntegrityRuntime.beforeWrite(ROUTE_STEP, original));
        DataIntegrityRuntime.afterWrite(ROUTE_STEP, 200, "{\"status\":1}", "t1");
        List<DataIntegrityRuntime.RunRecord> records = DataIntegrityRuntime.endRun();
        assertEquals(1, records.size());
        assertNotNull(records.get(0).error);
        assertEquals(DataIntegrityRuntime.QuiescenceGate.NOT_APPLICABLE, records.get(0).gate);
    }

    @Test
    public void ackedWritePresentOnSecondPoll_gatesObservedPresent() {
        http.script(CONTACT_READBACK, "{\"status\":1,\"data\":[]}"); // baseline
        begin("fault", contactTriple());
        String freshened = DataIntegrityRuntime.beforeWrite(CONTACT_STEP,
                "{\"accountId\":\"p\",\"documentNumber\":\"p\",\"name\":\"n\"}");
        JSONObject key = new JSONObject(freshened);
        String present = "{\"status\":1,\"data\":[{\"accountId\":\"" + key.getString("accountId")
                + "\",\"documentNumber\":\"" + key.getString("documentNumber") + "\",\"id\":\"srv-1\"}]}";
        http.script(CONTACT_READBACK, "{\"status\":1,\"data\":[]}", present);
        DataIntegrityRuntime.afterWrite(CONTACT_STEP, 200, "{\"status\":1,\"msg\":\"ok\"}", "t1");
        List<DataIntegrityRuntime.RunRecord> records = DataIntegrityRuntime.endRun();
        assertEquals(1, records.size());
        DataIntegrityRuntime.RunRecord r = records.get(0);
        assertTrue(r.acked);
        assertEquals(Integer.valueOf(1), r.ackBodyStatus);
        assertFalse(r.baselineContainedX);
        assertTrue(r.readbackContainedX);
        assertEquals(DataIntegrityRuntime.QuiescenceGate.OBSERVED_PRESENT, r.gate);
        assertEquals(2, r.polls);
        assertNull(r.error);
    }

    @Test
    public void ackedButNeverPresent_withStableTrace_gatesObservedCompleteAbsent() {
        // Base URL includes /api like the real config (.../jaeger/ui/api).
        System.setProperty("jaeger.base.url", "http://jaeger.test:16686/api");
        http.scriptAbsolute("http://jaeger.test:16686/api/traces/trace77",
                "{\"data\":[{\"spans\":[{},{},{}]}]}");
        begin("fault", contactTriple());
        DataIntegrityRuntime.beforeWrite(CONTACT_STEP, "{\"name\":\"n\"}");
        DataIntegrityRuntime.afterWrite(CONTACT_STEP, 200, "{\"status\":1}", "trace77");
        List<DataIntegrityRuntime.RunRecord> records = DataIntegrityRuntime.endRun();
        assertEquals(1, records.size());
        DataIntegrityRuntime.RunRecord r = records.get(0);
        assertTrue(r.acked);
        assertFalse(r.readbackContainedX);
        assertEquals(DataIntegrityRuntime.QuiescenceGate.OBSERVED_COMPLETE_ABSENT, r.gate);
        assertTrue("must have polled past the timeout", r.polls >= 1);
    }

    @Test
    public void ackedButNeverPresent_withoutJaeger_gatesTimeoutAbsent() {
        begin("fault", contactTriple());
        DataIntegrityRuntime.beforeWrite(CONTACT_STEP, "{\"name\":\"n\"}");
        DataIntegrityRuntime.afterWrite(CONTACT_STEP, 200, "{\"status\":1}", "trace77");
        List<DataIntegrityRuntime.RunRecord> records = DataIntegrityRuntime.endRun();
        assertEquals(DataIntegrityRuntime.QuiescenceGate.TIMEOUT_ABSENT, records.get(0).gate);
    }

    @Test
    public void g3Correlator_flowsFromHooksOntoRecord() {
        // The writer's <method>#<stepIdx> label passes through both hooks onto
        // the RunRecord so the pairing join can align by it. Legacy 2-arg /
        // 4-arg callers leave it null (positional fallback).
        begin("control", contactTriple());
        DataIntegrityRuntime.beforeWrite(CONTACT_STEP, "testCreateContact_0#3", "{\"name\":\"n\"}");
        DataIntegrityRuntime.afterWrite(CONTACT_STEP, "testCreateContact_0#3", 200,
                "{\"status\":1}", "t1");
        List<DataIntegrityRuntime.RunRecord> records = DataIntegrityRuntime.endRun();
        assertEquals(1, records.size());
        assertEquals("testCreateContact_0#3", records.get(0).correlationId);
    }

    @Test
    public void g3Correlator_legacyHooksLeaveItNull() {
        begin("control", contactTriple());
        DataIntegrityRuntime.beforeWrite(CONTACT_STEP, "{\"name\":\"n\"}");
        DataIntegrityRuntime.afterWrite(CONTACT_STEP, 200, "{\"status\":1}", "t1");
        List<DataIntegrityRuntime.RunRecord> records = DataIntegrityRuntime.endRun();
        assertNull(records.get(0).correlationId);
    }

    @Test
    public void g3Correlator_afterWriteWithoutBefore_usesAfterWriteCorrelator() {
        // The synthetic "afterWrite without matching beforeWrite" record must
        // carry the afterWrite hook's OWN correlator — proves the afterWrite arg
        // is live, not dead code (review C-MEDIUM-3).
        begin("control", contactTriple());
        DataIntegrityRuntime.afterWrite(CONTACT_STEP, "afterOnly#7", 200, "{\"status\":1}", "t1");
        List<DataIntegrityRuntime.RunRecord> records = DataIntegrityRuntime.endRun();
        assertEquals(1, records.size());
        assertNotNull(records.get(0).error);
        assertEquals("afterOnly#7", records.get(0).correlationId);
    }

    @Test
    public void g3Correlator_orphanRecordCarriesPriorWritesCorrelator() {
        // A second beforeWrite with no afterWrite between orphans the first; the
        // synthetic orphan record must carry the FIRST write's correlator so the
        // join stays aligned (review C-MEDIUM-4 — the orphan id is load-bearing).
        begin("control", contactTriple());
        DataIntegrityRuntime.beforeWrite(CONTACT_STEP, "first#0", "{\"name\":\"n\"}");
        DataIntegrityRuntime.beforeWrite(CONTACT_STEP, "second#1", "{\"name\":\"n\"}");
        DataIntegrityRuntime.afterWrite(CONTACT_STEP, "second#1", 200, "{\"status\":1}", "t1");
        List<DataIntegrityRuntime.RunRecord> records = DataIntegrityRuntime.endRun();
        assertEquals(2, records.size());
        assertEquals("first#0", records.get(0).correlationId);
        assertNotNull(records.get(0).error);
        assertTrue(records.get(0).error.contains("orphaned"));
        assertEquals("second#1", records.get(1).correlationId);
    }

    @Test
    public void softRejectedWrite_isNotAcked_noQuiescenceWait() {
        begin("control", contactTriple());
        DataIntegrityRuntime.beforeWrite(CONTACT_STEP, "{\"name\":\"n\"}");
        DataIntegrityRuntime.afterWrite(CONTACT_STEP, 200,
                "{\"status\":0,\"msg\":\"Already Exists\"}", "t1");
        List<DataIntegrityRuntime.RunRecord> records = DataIntegrityRuntime.endRun();
        assertEquals(1, records.size());
        DataIntegrityRuntime.RunRecord r = records.get(0);
        assertFalse(r.acked);
        assertEquals(Integer.valueOf(0), r.ackBodyStatus);
        assertEquals(DataIntegrityRuntime.QuiescenceGate.NOT_APPLICABLE, r.gate);
    }

    @Test
    public void non2xx_isNotAcked() {
        begin("control", contactTriple());
        DataIntegrityRuntime.beforeWrite(CONTACT_STEP, "{\"name\":\"n\"}");
        DataIntegrityRuntime.afterWrite(CONTACT_STEP, 500, "boom", "t1");
        assertFalse(DataIntegrityRuntime.endRun().get(0).acked);
    }

    @Test
    public void containsKey_comparesNumbersAsStrings() {
        Map<String, String> key = new HashMap<>();
        key.put("documentNumber", "123");
        assertTrue(DataIntegrityRuntime.containsKey(
                "{\"status\":1,\"data\":[{\"documentNumber\":123}]}", key));
        assertFalse(DataIntegrityRuntime.containsKey(
                "{\"status\":1,\"data\":[{\"documentNumber\":124}]}", key));
        assertFalse(DataIntegrityRuntime.containsKey("not json", key));
        assertFalse(DataIntegrityRuntime.containsKey(
                "{\"status\":1,\"data\":[{}]}", Collections.emptyMap()));
    }

    @Test
    public void beginRunTwice_failsFast() {
        begin("control", contactTriple());
        try {
            begin("fault", contactTriple());
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
            // expected
        }
    }

    // ── G3 depth adapter: supplied isolation + value-delta read-back ──

    private static final String CANCEL_STEP = "GET /api/v1/cancelservice/cancel/{orderId}/{loginId}";
    private static final String ACCOUNT_READBACK = "/api/v1/inside_pay_service/inside_payment/account";

    private static TargetTripleRegistry.Triple cancelTriple() {
        String yaml = "triples:\n"
                + "  - name: tt-cancel-refund\n"
                + "    write_endpoint: \"" + CANCEL_STEP + "\"\n"
                + "    dependency: ts-inside-payment-service\n"
                + "    readback_endpoint: \"GET " + ACCOUNT_READBACK + "\"\n"
                + "    isolation_key: [userId]\n"
                + "    isolation_strategy: supplied\n"
                + "    readback_mode: value-delta\n"
                + "    value_probe:\n"
                + "      match_field: userId\n"
                + "      value_field: balance\n";
        return TargetTripleRegistry.parse(
                new java.io.ByteArrayInputStream(yaml.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                "inline").triples.get(0);
    }

    private static String account(String... userBalancePairs) {
        StringBuilder sb = new StringBuilder("{\"status\":1,\"msg\":\"Success\",\"data\":[");
        for (int i = 0; i + 1 < userBalancePairs.length; i += 2) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"userId\":\"").append(userBalancePairs[i])
                    .append("\",\"balance\":\"").append(userBalancePairs[i + 1]).append("\"}");
        }
        return sb.append("]}").toString();
    }

    @Test
    public void suppliedValueDelta_controlLeg_refundMovesBalance_present() {
        // Baseline: buyer u-1 at 100.00. The cancel's refund lands on the
        // second post-write poll (180.00) — X-present, OBSERVED_PRESENT.
        http.script(ACCOUNT_READBACK, account("u-1", "100.00", "other", "5"));
        begin("control", cancelTriple());
        assertNull("bodyless write passes its (null) body through",
                DataIntegrityRuntime.beforeWriteSupplied(CANCEL_STEP, "cancel#0", null,
                        "userId", "u-1"));
        http.script(ACCOUNT_READBACK, account("u-1", "100.00", "other", "5"),
                account("u-1", "180.00", "other", "5"));
        DataIntegrityRuntime.afterWrite(CANCEL_STEP, "cancel#0", 200,
                "{\"status\":1,\"msg\":\"Success.\",\"data\":\"test not null\"}", null);
        List<DataIntegrityRuntime.RunRecord> records = DataIntegrityRuntime.endRun();
        assertEquals(1, records.size());
        DataIntegrityRuntime.RunRecord r = records.get(0);
        assertTrue(r.acked);
        assertFalse("value-delta baseline never contains X", r.baselineContainedX);
        assertTrue("balance moved — refund landed", r.readbackContainedX);
        assertEquals(DataIntegrityRuntime.QuiescenceGate.OBSERVED_PRESENT, r.gate);
        assertEquals("cancel#0", r.correlationId);
        assertNull(r.error);
        assertEquals("u-1", r.isolationKey.get("userId"));
    }

    @Test
    public void suppliedValueDelta_faultLeg_ackedErrorEnvelope_balanceNeverMoves_absent() {
        // The REAL fault-leg shape on the TT fork: the drawback hop dies, the
        // cancel controller's catch-all returns {1,"error"} — acked under the
        // runtime's predicate — and the buyer's balance never moves.
        http.script(ACCOUNT_READBACK, account("u-1", "100.00"));
        begin("fault", cancelTriple());
        DataIntegrityRuntime.beforeWriteSupplied(CANCEL_STEP, "cancel#0", null, "userId", "u-1");
        DataIntegrityRuntime.afterWrite(CANCEL_STEP, "cancel#0", 200,
                "{\"status\":1,\"msg\":\"error\",\"data\":null}", null);
        List<DataIntegrityRuntime.RunRecord> records = DataIntegrityRuntime.endRun();
        assertEquals(1, records.size());
        DataIntegrityRuntime.RunRecord r = records.get(0);
        assertTrue("{1,\"error\"} is an acknowledged envelope", r.acked);
        assertFalse("balance never moved — the refund is lost", r.readbackContainedX);
        assertEquals(DataIntegrityRuntime.QuiescenceGate.TIMEOUT_ABSENT, r.gate);
        assertNull(r.error);
    }

    @Test
    public void suppliedValueDelta_buyerAbsentFromBaseline_appearing_isPresent() {
        // A fresh buyer with no Money rows is absent from /account entirely;
        // the refund row makes them appear — that IS the value moving.
        http.script(ACCOUNT_READBACK, account("other", "5"));
        begin("control", cancelTriple());
        DataIntegrityRuntime.beforeWriteSupplied(CANCEL_STEP, "cancel#0", null, "userId", "u-1");
        http.script(ACCOUNT_READBACK, account("other", "5", "u-1", "80.00"));
        DataIntegrityRuntime.afterWrite(CANCEL_STEP, "cancel#0", 200, "{\"status\":1}", null);
        assertTrue(DataIntegrityRuntime.endRun().get(0).readbackContainedX);
    }

    @Test
    public void suppliedValueDelta_numericallyEqualStrings_notAChange() {
        // "100.0" vs "100.00" is the same balance — BigDecimal, not string, math.
        http.script(ACCOUNT_READBACK, account("u-1", "100.0"));
        begin("fault", cancelTriple());
        DataIntegrityRuntime.beforeWriteSupplied(CANCEL_STEP, "cancel#0", null, "userId", "u-1");
        http.script(ACCOUNT_READBACK, account("u-1", "100.00"));
        DataIntegrityRuntime.afterWrite(CANCEL_STEP, "cancel#0", 200, "{\"status\":1}", null);
        DataIntegrityRuntime.RunRecord r = DataIntegrityRuntime.endRun().get(0);
        assertFalse("numerically equal balances are no movement", r.readbackContainedX);
        assertEquals(DataIntegrityRuntime.QuiescenceGate.TIMEOUT_ABSENT, r.gate);
    }

    @Test
    public void legacyFresheningHook_onSuppliedTriple_recordsWiringError() {
        begin("control", cancelTriple());
        assertEquals("body passes through untouched", "{}",
                DataIntegrityRuntime.beforeWrite(CANCEL_STEP, "{}"));
        DataIntegrityRuntime.afterWrite(CANCEL_STEP, 200, "{\"status\":1}", null);
        List<DataIntegrityRuntime.RunRecord> records = DataIntegrityRuntime.endRun();
        assertEquals(1, records.size());
        assertNotNull(records.get(0).error);
        assertTrue(records.get(0).error, records.get(0).error.contains("beforeWriteSupplied"));
    }

    @Test
    public void suppliedHook_onFresheningTriple_recordsWiringError() {
        begin("control", contactTriple());
        DataIntegrityRuntime.beforeWriteSupplied(CONTACT_STEP, "c#0", "{\"name\":\"n\"}",
                "name", "n");
        DataIntegrityRuntime.afterWrite(CONTACT_STEP, "c#0", 200, "{\"status\":1}", null);
        List<DataIntegrityRuntime.RunRecord> records = DataIntegrityRuntime.endRun();
        assertEquals(1, records.size());
        assertNotNull(records.get(0).error);
        assertTrue(records.get(0).error, records.get(0).error.contains("non-supplied"));
    }

    @Test
    public void suppliedHook_keyFieldOutsideRegistry_recordsError() {
        http.script(ACCOUNT_READBACK, account("u-1", "100.00"));
        begin("control", cancelTriple());
        DataIntegrityRuntime.beforeWriteSupplied(CANCEL_STEP, "cancel#0", null,
                "accountId", "u-1");
        DataIntegrityRuntime.afterWrite(CANCEL_STEP, "cancel#0", 200, "{\"status\":1}", null);
        DataIntegrityRuntime.RunRecord r = DataIntegrityRuntime.endRun().get(0);
        assertNotNull(r.error);
        assertTrue(r.error, r.error.contains("unusable supplied key"));
    }

    @Test
    public void suppliedHook_baselineReadFails_recordsError() {
        http.byPath.computeIfAbsent(ACCOUNT_READBACK, k -> new ArrayDeque<>())
                .add(new DataIntegrityRuntime.HttpResponse(503, "down"));
        begin("control", cancelTriple());
        DataIntegrityRuntime.beforeWriteSupplied(CANCEL_STEP, "cancel#0", null, "userId", "u-1");
        DataIntegrityRuntime.afterWrite(CANCEL_STEP, "cancel#0", 200, "{\"status\":1}", null);
        DataIntegrityRuntime.RunRecord r = DataIntegrityRuntime.endRun().get(0);
        assertNotNull(r.error);
        assertTrue(r.error, r.error.contains("baseline read-back HTTP 503"));
    }

    @Test
    public void extractProbeValue_missingRowOrField_isNull() {
        TargetTripleRegistry.Triple t = cancelTriple();
        Map<String, String> key = new HashMap<>();
        key.put("userId", "u-1");
        assertEquals("100.00",
                DataIntegrityRuntime.extractProbeValue(t, account("u-1", "100.00"), key));
        assertNull("no row for the buyer",
                DataIntegrityRuntime.extractProbeValue(t, account("other", "5"), key));
        assertNull("unparseable body",
                DataIntegrityRuntime.extractProbeValue(t, "not json", key));
        assertNull("value field absent",
                DataIntegrityRuntime.extractProbeValue(t,
                        "{\"status\":1,\"data\":[{\"userId\":\"u-1\"}]}", key));
    }

    @Test
    public void valueDiffers_nullAndNumericSemantics() {
        assertFalse(DataIntegrityRuntime.valueDiffers(null, null));
        assertTrue(DataIntegrityRuntime.valueDiffers(null, "80.00"));
        assertTrue(DataIntegrityRuntime.valueDiffers("80.00", null));
        assertFalse(DataIntegrityRuntime.valueDiffers("100.0", "100.00"));
        assertFalse("surrounding whitespace is not a change",
                DataIntegrityRuntime.valueDiffers(" 100.00 ", "100.00"));
        assertTrue(DataIntegrityRuntime.valueDiffers("100.00", "180.00"));
        assertTrue("non-numeric falls back to string equality",
                DataIntegrityRuntime.valueDiffers("abc", "abd"));
        assertFalse(DataIntegrityRuntime.valueDiffers("abc", "abc"));
    }

    // ── depth-review fix wave: value-delta soundness (A-F1/A-F2/B-F1) ──

    @Test
    public void suppliedValueDelta_probeRowVanishesFromA2xxRead_isError() {
        // Review A-F2 (BLOCKING): a row present at baseline that disappears from
        // a 2xx read is a truncated/garbage surface, NOT movement — recording it
        // as X-present would latch a fault-leg false negative. It must be error.
        http.script(ACCOUNT_READBACK, account("u-1", "100.00")); // baseline: buyer present
        begin("fault", cancelTriple());
        DataIntegrityRuntime.beforeWriteSupplied(CANCEL_STEP, "cancel#0", null, "userId", "u-1");
        http.script(ACCOUNT_READBACK, account("other", "5")); // buyer row GONE
        DataIntegrityRuntime.afterWrite(CANCEL_STEP, "cancel#0", 200, "{\"status\":1}", null);
        DataIntegrityRuntime.RunRecord r = DataIntegrityRuntime.endRun().get(0);
        assertNotNull(r.error);
        assertTrue(r.error, r.error.contains("vanished"));
        assertFalse("a vanish is never X-present", r.readbackContainedX);
        assertEquals(DataIntegrityRuntime.QuiescenceGate.NOT_APPLICABLE, r.gate);
    }

    @Test
    public void suppliedValueDelta_unstableBaseline_isError() {
        // Review A-F1: two pre-write reads disagree — an earlier step (register /
        // pay) has not settled — so no trustworthy baseline; record an error.
        http.script(ACCOUNT_READBACK, account("u-1", "100.00"), account("u-1", "140.00"));
        begin("control", cancelTriple());
        DataIntegrityRuntime.beforeWriteSupplied(CANCEL_STEP, "cancel#0", null, "userId", "u-1");
        DataIntegrityRuntime.afterWrite(CANCEL_STEP, "cancel#0", 200, "{\"status\":1}", null);
        DataIntegrityRuntime.RunRecord r = DataIntegrityRuntime.endRun().get(0);
        assertNotNull(r.error);
        assertTrue(r.error, r.error.contains("not stable"));
        assertEquals(DataIntegrityRuntime.QuiescenceGate.NOT_APPLICABLE, r.gate);
    }

    @Test
    public void suppliedValueDelta_nonCollectionBaseline_isError() {
        // Review A-F2: a 2xx body that is not a collection would read as an
        // all-null probe surface; reject it at the hook.
        http.byPath.computeIfAbsent(ACCOUNT_READBACK, k -> new ArrayDeque<>())
                .add(new DataIntegrityRuntime.HttpResponse(200, "not a collection at all"));
        begin("control", cancelTriple());
        DataIntegrityRuntime.beforeWriteSupplied(CANCEL_STEP, "cancel#0", null, "userId", "u-1");
        DataIntegrityRuntime.afterWrite(CANCEL_STEP, "cancel#0", 200, "{\"status\":1}", null);
        DataIntegrityRuntime.RunRecord r = DataIntegrityRuntime.endRun().get(0);
        assertNotNull(r.error);
        assertTrue(r.error, r.error.contains("parseable collection"));
    }

    @Test
    public void suppliedValueDelta_faultLeg_traceComplete_gatesObservedCompleteAbsent() {
        // The gate the depth fault leg actually publishes: acked, balance never
        // moves, trace complete → OBSERVED_COMPLETE_ABSENT (a high-confidence
        // lost refund). Previously untested for value-delta (review test gap).
        System.setProperty("jaeger.base.url", "http://jaeger.test:16686/api");
        http.scriptAbsolute("http://jaeger.test:16686/api/traces/tcancel",
                "{\"data\":[{\"spans\":[{},{}]}]}");
        http.script(ACCOUNT_READBACK, account("u-1", "100.00")); // baseline, never moves
        begin("fault", cancelTriple());
        DataIntegrityRuntime.beforeWriteSupplied(CANCEL_STEP, "cancel#0", null, "userId", "u-1");
        DataIntegrityRuntime.afterWrite(CANCEL_STEP, "cancel#0", 200,
                "{\"status\":1,\"msg\":\"error\"}", "tcancel");
        DataIntegrityRuntime.RunRecord r = DataIntegrityRuntime.endRun().get(0);
        assertTrue(r.acked);
        assertFalse(r.readbackContainedX);
        assertEquals(DataIntegrityRuntime.QuiescenceGate.OBSERVED_COMPLETE_ABSENT, r.gate);
        assertNull(r.error);
    }

    @Test
    public void parsesToCollection_distinguishesGarbageFromEmpty() {
        assertTrue(DataIntegrityRuntime.parsesToCollection("{\"status\":1,\"data\":[]}"));
        assertTrue(DataIntegrityRuntime.parsesToCollection("[]"));
        assertFalse("empty list is a collection but null data is not",
                DataIntegrityRuntime.parsesToCollection("{\"status\":1,\"data\":null}"));
        assertFalse(DataIntegrityRuntime.parsesToCollection("not json"));
        assertFalse(DataIntegrityRuntime.parsesToCollection(""));
        assertFalse(DataIntegrityRuntime.parsesToCollection(null));
    }
}
