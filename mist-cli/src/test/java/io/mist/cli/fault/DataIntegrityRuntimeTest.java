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
}
