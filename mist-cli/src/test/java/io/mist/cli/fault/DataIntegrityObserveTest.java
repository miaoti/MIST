package io.mist.cli.fault;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * UX W0/W1 (REVIEW-UX-RECONCILIATION U1/U4/U5/U8): pins the product
 * observe-mode session — the observe accessors are inert for paired legs
 * (U5), the generated check's failure semantics default to WARN (U4), only a
 * VALIDATED read-back may fail the test (U8 quarantine), and TIMEOUT_ABSENT
 * never fails regardless of configuration.
 */
public class DataIntegrityObserveTest {

    private static final String CONTACT_STEP = "POST /api/v1/adminbasicservice/adminbasic/contacts";

    /** Minimal scripted seam (same idiom as DataIntegrityRuntimeTest). */
    private static final class ScriptedHttp implements DataIntegrityRuntime.Http {
        final Map<String, Deque<DataIntegrityRuntime.HttpResponse>> byPath = new HashMap<>();
        final Map<String, DataIntegrityRuntime.HttpResponse> lastServed = new HashMap<>();
        final Map<String, Deque<DataIntegrityRuntime.HttpResponse>> byUrl = new HashMap<>();

        void script(String path, String... bodies) {
            Deque<DataIntegrityRuntime.HttpResponse> q = byPath.computeIfAbsent(path, k -> new ArrayDeque<>());
            for (String b : bodies) {
                q.add(new DataIntegrityRuntime.HttpResponse(200, b));
            }
        }

        void scriptAbsolute(String url, String... bodies) {
            Deque<DataIntegrityRuntime.HttpResponse> q = byUrl.computeIfAbsent(url, k -> new ArrayDeque<>());
            for (String b : bodies) {
                q.add(new DataIntegrityRuntime.HttpResponse(200, b));
            }
        }

        @Override
        public DataIntegrityRuntime.HttpResponse getSut(String path) {
            Deque<DataIntegrityRuntime.HttpResponse> q = byPath.get(path);
            if (q != null && !q.isEmpty()) {
                DataIntegrityRuntime.HttpResponse next = q.poll();
                lastServed.put(path, next);
                return next;
            }
            DataIntegrityRuntime.HttpResponse last = lastServed.get(path);
            return last != null ? last
                    : new DataIntegrityRuntime.HttpResponse(200, "{\"status\":1,\"data\":[]}");
        }

        @Override
        public DataIntegrityRuntime.HttpResponse getAbsolute(String url) {
            Deque<DataIntegrityRuntime.HttpResponse> q = byUrl.get(url);
            if (q == null || q.isEmpty()) {
                return new DataIntegrityRuntime.HttpResponse(404, "");
            }
            return q.size() == 1 ? q.peek() : q.poll();
        }
    }

    private ScriptedHttp http;
    private String previousJaegerBase;
    private String previousFailOnLost;

    private static TargetTripleRegistry.Triple contactTriple() {
        TargetTripleRegistry.Registry registry = TargetTripleRegistry.parse(
                DataIntegrityObserveTest.class.getResourceAsStream(
                        "/My-Example/trainticket/target-triples.yaml"), "shipped");
        for (TargetTripleRegistry.Triple t : registry.triples) {
            if (t.name.equals("adminbasic-contacts-create")) {
                return t;
            }
        }
        throw new IllegalStateException("no shipped contacts triple");
    }

    private void beginObserve(TargetTripleRegistry.Triple... triples) {
        DataIntegrityRuntime.beginRun(Arrays.asList(triples), "observe-test", http, 1, 30, 1,
                /*observe=*/true);
    }

    private void beginPaired(TargetTripleRegistry.Triple... triples) {
        DataIntegrityRuntime.beginRun(Arrays.asList(triples), "control", http, 1, 30, 1);
    }

    @Before
    public void setUp() {
        http = new ScriptedHttp();
        previousJaegerBase = System.getProperty("jaeger.base.url");
        System.clearProperty("jaeger.base.url");
        previousFailOnLost = System.getProperty(DataIntegrityObserveCheck.FAIL_ON_LOST_PROPERTY);
        System.clearProperty(DataIntegrityObserveCheck.FAIL_ON_LOST_PROPERTY);
    }

    @After
    public void tearDown() {
        DataIntegrityRuntime.endRun();
        restore("jaeger.base.url", previousJaegerBase);
        restore(DataIntegrityObserveCheck.FAIL_ON_LOST_PROPERTY, previousFailOnLost);
    }

    private static void restore(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    /**
     * Drives one hooked write. beforeWrite FRESHENS the isolation key, so a
     * landing write must be scripted with the freshened values; {@code lands}
     * controls whether the read-back ever shows this write's key.
     */
    private void write(String correlationId, String traceId, boolean lands) {
        String freshened = DataIntegrityRuntime.beforeWrite(CONTACT_STEP, correlationId,
                "{\"accountId\":\"p\",\"documentNumber\":\"p\",\"name\":\"n\"}");
        assertNotNull(freshened);
        if (lands) {
            org.json.JSONObject key = new org.json.JSONObject(freshened);
            String present = "{\"status\":1,\"data\":[{\"accountId\":\"" + key.getString("accountId")
                    + "\",\"documentNumber\":\"" + key.getString("documentNumber") + "\",\"id\":\"srv-1\"}]}";
            http.script(tripleReadback(), "{\"status\":1,\"data\":[]}", present);
        }
        DataIntegrityRuntime.afterWrite(CONTACT_STEP, correlationId, 200, "{\"status\":1}", traceId);
    }

    // ── U5: paired-session inertness ──

    @Test
    public void pairedSession_observeAccessorsAreInert() {
        beginPaired(contactTriple());
        write("C.m#0", "t1", true);
        assertNull("observeRecordFor must be null for a paired leg",
                DataIntegrityRuntime.observeRecordFor("C.m#0"));
        assertFalse(DataIntegrityRuntime.observeTripleHasObservedPresent("adminbasic-contacts-create"));
        // and the generated check is a no-op even with failOnLost set
        System.setProperty(DataIntegrityObserveCheck.FAIL_ON_LOST_PROPERTY, "true");
        DataIntegrityObserveCheck.afterStep("C.m#0"); // must not throw
        List<DataIntegrityRuntime.RunRecord> records = DataIntegrityRuntime.endRun();
        assertEquals("paired record stream unchanged", 1, records.size());
    }

    @Test
    public void noSession_checkIsNoop() {
        System.setProperty(DataIntegrityObserveCheck.FAIL_ON_LOST_PROPERTY, "true");
        DataIntegrityObserveCheck.afterStep("C.m#0"); // must not throw
    }

    // ── W0 accessors on an observe session ──

    @Test
    public void observeSession_recordFoundByCorrelator_andPresentValidatesTriple() {
        // membership read-back: first the baseline (absent), then present
        beginObserve(contactTriple());
        write("C.m#0", "t1", true);
        DataIntegrityRuntime.RunRecord r = DataIntegrityRuntime.observeRecordFor("C.m#0");
        assertNotNull("observe session must expose the record", r);
        assertEquals(DataIntegrityRuntime.QuiescenceGate.OBSERVED_PRESENT, r.gate);
        assertTrue(DataIntegrityRuntime.observeTripleHasObservedPresent("adminbasic-contacts-create"));
        DataIntegrityObserveCheck.afterStep("C.m#0"); // ✅ path, never throws
    }

    // ── U4/U8: failure semantics ──

    @Test
    public void validatedDefect_failOnLost_throwsWithMarker() {
        System.setProperty("jaeger.base.url", "http://jaeger.test:16686/api");
        http.scriptAbsolute("http://jaeger.test:16686/api/traces/t2",
                "{\"data\":[{\"spans\":[{},{}]}]}");
        // write #1 lands (validates the read-back), write #2 never appears
        beginObserve(contactTriple());
        write("C.m#0", "t1", true);  // OBSERVED_PRESENT → triple validated
        write("C.m#1", "t2", false); // stable absent + trace complete → defect
        DataIntegrityRuntime.RunRecord r2 = DataIntegrityRuntime.observeRecordFor("C.m#1");
        assertNotNull(r2);
        assertEquals(DataIntegrityRuntime.QuiescenceGate.OBSERVED_COMPLETE_ABSENT, r2.gate);
        System.setProperty(DataIntegrityObserveCheck.FAIL_ON_LOST_PROPERTY, "true");
        try {
            DataIntegrityObserveCheck.afterStep("C.m#1");
            fail("validated defect with failOnLost=true must throw");
        } catch (AssertionError e) {
            assertTrue("message carries the category marker: " + e.getMessage(),
                    e.getMessage().contains(DataIntegrityObserveCheck.LOST_MARKER));
        }
    }

    @Test
    public void validatedDefect_defaultConfig_warnsOnly() {
        System.setProperty("jaeger.base.url", "http://jaeger.test:16686/api");
        http.scriptAbsolute("http://jaeger.test:16686/api/traces/t2",
                "{\"data\":[{\"spans\":[{},{}]}]}");
        beginObserve(contactTriple());
        write("C.m#0", "t1", true);
        write("C.m#1", "t2", false);
        // U4: shipped default is WARN — no property set, no throw
        DataIntegrityObserveCheck.afterStep("C.m#1");
    }

    @Test
    public void unvalidatedDefect_isQuarantined_neverThrows() {
        System.setProperty("jaeger.base.url", "http://jaeger.test:16686/api");
        http.scriptAbsolute("http://jaeger.test:16686/api/traces/t1",
                "{\"data\":[{\"spans\":[{},{}]}]}");
        // read-back NEVER shows anything landing (mis-bound read-back shape)
        beginObserve(contactTriple());
        write("C.m#0", "t1", false);
        DataIntegrityRuntime.RunRecord r = DataIntegrityRuntime.observeRecordFor("C.m#0");
        assertNotNull(r);
        assertEquals(DataIntegrityRuntime.QuiescenceGate.OBSERVED_COMPLETE_ABSENT, r.gate);
        assertFalse(DataIntegrityRuntime.observeTripleHasObservedPresent("adminbasic-contacts-create"));
        System.setProperty(DataIntegrityObserveCheck.FAIL_ON_LOST_PROPERTY, "true");
        DataIntegrityObserveCheck.afterStep("C.m#0"); // quarantined → warn, not fail
    }

    @Test
    public void timeoutAbsent_neverThrows_evenWithFailOnLost() {
        // no jaeger.base.url → absence stays timeout-gated (U3 disclosure)
        beginObserve(contactTriple());
        write("C.m#0", "t1", false);
        DataIntegrityRuntime.RunRecord r = DataIntegrityRuntime.observeRecordFor("C.m#0");
        assertNotNull(r);
        assertEquals(DataIntegrityRuntime.QuiescenceGate.TIMEOUT_ABSENT, r.gate);
        System.setProperty(DataIntegrityObserveCheck.FAIL_ON_LOST_PROPERTY, "true");
        DataIntegrityObserveCheck.afterStep("C.m#0"); // unconfirmed → warn, not fail
    }

    /** The contacts triple's read-back path (GET stripped by the loader). */
    private static String tripleReadback() {
        TargetTripleRegistry.Triple t = contactTriple();
        String rb = t.readbackEndpoint;
        return rb.startsWith("GET ") ? rb.substring(4) : rb;
    }
}
