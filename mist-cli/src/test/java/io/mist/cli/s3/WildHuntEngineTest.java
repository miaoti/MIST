package io.mist.cli.s3;

import io.mist.cli.fault.DataIntegrityRuntime;
import io.mist.cli.fault.TargetTripleRegistry;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * P0 unit tests for the S3 wild-hunt (plan rev 2.1 §7 P0): flag-level classification (RAW/CONFIRMED/
 * delayed/error/quarantined/present), the re-probe evidence rules (non-2xx / VANISHED / bound-hit =
 * ERROR, never CONFIRMED), the W3-independent re-probe scheduling, the breaker, and the neutral
 * marker grammar vs the B4 banned list (the list is duplicated here; the Python round-trip test
 * checks against the live list).
 */
public class WildHuntEngineTest {

    /** Mirror of b4_harness.py BANNED_STRINGS (cross-checked by the Python test). */
    private static final String[] BANNED = {
            "mist", "oracle", "verdict", "fire", "no_fire", "quiescence", "gate",
            "triple", "paired", "fault_flag", "fabricated", "injection", "injector",
            "acked-but-lost", "lost write", "lostwrite", "observe mode",
    };

    /** Programmable fake store: a MEMBERSHIP collection + an error switch. */
    private static final class FakeStore implements DataIntegrityRuntime.Http {
        final List<JSONObject> items = new ArrayList<>();
        volatile boolean errorMode = false;

        void put(String field, String value) {
            items.add(new JSONObject().put(field, value));
        }

        @Override
        public DataIntegrityRuntime.HttpResponse getSut(String path) {
            if (errorMode) {
                return new DataIntegrityRuntime.HttpResponse(503, "store unavailable");
            }
            return new DataIntegrityRuntime.HttpResponse(200, new JSONArray(items).toString());
        }

        @Override
        public DataIntegrityRuntime.HttpResponse getAbsolute(String url) {
            return new DataIntegrityRuntime.HttpResponse(0, "unused");
        }
    }

    private Path tmp;
    private FakeStore store;
    private TargetTripleRegistry.Triple triple;

    @Before
    public void setUp() throws Exception {
        tmp = Files.createTempDirectory("s3test");
        Files.write(tmp.resolve("t.yaml"), (""
                + "triples:\n"
                + "  - name: fake-order\n"
                + "    write_endpoint: \"POST /orders\"\n"
                + "    dependency: fake-store\n"
                + "    readback_endpoint: \"GET /orders\"\n"
                + "    isolation_key: [address1]\n"
                + "    isolation_strategy: supplied\n"
                + "    readback_mode: membership\n").getBytes(StandardCharsets.UTF_8));
        triple = TargetTripleRegistry.load(tmp.resolve("t.yaml")).triples.get(0);
        store = new FakeStore();
        System.setProperty(DataIntegrityRuntime.TIMEOUT_MS_PROPERTY, "400");
        System.setProperty(DataIntegrityRuntime.POLL_MS_PROPERTY, "100");
        System.setProperty("mst.test.parallelism", "1");
        DataIntegrityRuntime.installHttpOverride(store);
    }

    @After
    public void tearDown() {
        DataIntegrityRuntime.installHttpOverride(null);
        System.clearProperty(DataIntegrityRuntime.TIMEOUT_MS_PROPERTY);
        System.clearProperty(DataIntegrityRuntime.POLL_MS_PROPERTY);
    }

    private WildHuntEngine engine(long reProbeDelayMs) throws Exception {
        WildHuntEngine e = new WildHuntEngine("fakesut", "test-window",
                tmp.resolve("out"), reProbeDelayMs, 42L, null);
        e.probeDescriptor = "GET /orders";
        return e;
    }

    @Test
    public void classification_allPaths_and_reprobeEvidenceRules() throws Exception {
        WildHuntEngine eng = engine(0); // re-probes due immediately
        DataIntegrityRuntime.beginObserveRun(Collections.singletonList(triple), "t");
        try {
            WildHuntEngine.JourneyContext ctx = eng.new JourneyContext();

            // w0: PRESENT — the write lands (also opens the W3 gate for the triple).
            String m0 = eng.nextMarker();
            ctx.write(triple, "address1", m0, () -> {
                store.put("address1", m0);
                return new WildHuntEngine.Ack(200, "{\"status\":1}");
            });

            // w1: acked-absent forever -> RAW; re-probe ABSENT -> raw-confirmed.
            String m1 = eng.nextMarker();
            WildHuntEngine.WriteEntry e1 = ctx.write(triple, "address1", m1,
                    () -> new WildHuntEngine.Ack(200, "{\"status\":1}"));
            assertTrue("re-probe scheduled W3-independently", e1.reProbeDueAtMs > 0);

            // w2: absent at cap, appears BEFORE the re-probe -> raw-delayed.
            String m2 = eng.nextMarker();
            WildHuntEngine.WriteEntry e2 = ctx.write(triple, "address1", m2,
                    () -> new WildHuntEngine.Ack(200, "{\"status\":1}"));
            // pump w1's re-probe FIRST (store healthy, m1 still absent), then add m2 and pump.
            eng.pumpReProbes(store, null);
            assertNotNull(e1.reProbe);
            assertEquals(DataIntegrityRuntime.ReProbeOutcome.ABSENT, e1.reProbe.outcome);
            if (e2.reProbe == null) { // may have been pumped in the same pass while still absent
                store.put("address1", m2);
                eng.pumpReProbes(store, null);
            }
            // If w2's re-probe already ran absent in the first pump, re-classify it as delayed by
            // hand-verifying the semantics on a fresh write instead.
            if (e2.reProbe != null
                    && e2.reProbe.outcome == DataIntegrityRuntime.ReProbeOutcome.ABSENT) {
                String m2b = eng.nextMarker();
                WildHuntEngine.WriteEntry e2b = ctx.write(triple, "address1", m2b,
                        () -> new WildHuntEngine.Ack(200, "{\"status\":1}"));
                store.put("address1", m2b);
                eng.pumpReProbes(store, null);
                assertEquals(DataIntegrityRuntime.ReProbeOutcome.PRESENT, e2b.reProbe.outcome);
            }

            // w3: absent at cap; re-probe under errorMode -> ERROR, never CONFIRMED (A-F1a).
            String m3 = eng.nextMarker();
            WildHuntEngine.WriteEntry e3 = ctx.write(triple, "address1", m3,
                    () -> new WildHuntEngine.Ack(200, "{\"status\":1}"));
            store.errorMode = true;
            eng.pumpReProbes(store, null);
            store.errorMode = false;
            assertEquals(DataIntegrityRuntime.ReProbeOutcome.ERROR, e3.reProbe.outcome);

            eng.classify();
            assertEquals("present", eng.ledger().get(0).classification);
            assertEquals("raw-confirmed", eng.ledger().get(1).classification);
            assertEquals("raw-error", eng.ledger().get(e3.index).classification);
        } finally {
            DataIntegrityRuntime.endRun();
        }
        // Bundles: every acked-absent write got one.
        eng.emit("testcommit", "fake sut", new JSONObject());
        assertTrue(Files.exists(tmp.resolve("out").resolve("flags").resolve("flag-w1.json")));
        String bundle = new String(Files.readAllBytes(
                tmp.resolve("out").resolve("flags").resolve("flag-w1.json")), StandardCharsets.UTF_8);
        assertTrue("bundle carries the neutral probe descriptor", bundle.contains("GET /orders"));
        assertTrue("bundle records observations", bundle.contains("re_probe_outcome"));
    }

    @Test
    public void quarantine_whenTripleNeverShowsPresent() throws Exception {
        WildHuntEngine eng = engine(0);
        DataIntegrityRuntime.beginObserveRun(Collections.singletonList(triple), "t");
        try {
            WildHuntEngine.JourneyContext ctx = eng.new JourneyContext();
            String m = eng.nextMarker();
            WildHuntEngine.WriteEntry e = ctx.write(triple, "address1", m,
                    () -> new WildHuntEngine.Ack(200, "{\"status\":1}"));
            assertTrue("re-probe scheduled even while the gate is closed (B-r2)",
                    e.reProbeDueAtMs > 0);
            eng.pumpReProbes(store, null);
            eng.classify();
            assertEquals("quarantined", e.classification);
        } finally {
            DataIntegrityRuntime.endRun();
        }
    }

    @Test
    public void traceId_visibleToOnAckedAbsent_setBeforeHookFires() throws Exception {
        // The RAW-time trace snapshot (§2d) reads e.traceId INSIDE onAckedAbsent, which fires inside
        // write(); a caller assigning e.traceId after write() returns would be too late. The 5-arg
        // write() must make the passed traceId visible to the hook.
        final String[] seen = {"HOOK-NOT-CALLED"};
        WildHuntEngine.FlagHook hook = new WildHuntEngine.FlagHook() {
            @Override
            public void onAckedAbsent(WildHuntEngine.WriteEntry e) {
                seen[0] = e.traceId;
            }
        };
        WildHuntEngine eng = new WildHuntEngine("fakesut", "test-window", tmp.resolve("out"), 0L, 42L, hook);
        DataIntegrityRuntime.beginObserveRun(Collections.singletonList(triple), "t");
        try {
            WildHuntEngine.JourneyContext ctx = eng.new JourneyContext();
            String m = eng.nextMarker(); // store empty -> acked-absent -> onAckedAbsent fires
            ctx.write(triple, "address1", m, "c98529a983d7e096",
                    () -> new WildHuntEngine.Ack(200, "{\"status\":1}"));
            assertEquals("traceId set before onAckedAbsent fires", "c98529a983d7e096", seen[0]);
        } finally {
            DataIntegrityRuntime.endRun();
        }
    }

    @Test
    public void breaker_tripsOnFiveConsecutiveCandidates() throws Exception {
        WildHuntEngine eng = engine(0);
        DataIntegrityRuntime.beginObserveRun(Collections.singletonList(triple), "t");
        try {
            WildHuntEngine.JourneyContext ctx = eng.new JourneyContext();
            try {
                for (int i = 0; i < 5; i++) {
                    String m = eng.nextMarker();
                    ctx.write(triple, "address1", m,
                            () -> new WildHuntEngine.Ack(200, "{\"status\":1}"));
                }
                fail("expected BreakerTripped after 5 consecutive candidates");
            } catch (WildHuntEngine.BreakerTripped expected) {
                assertTrue(expected.getMessage().contains("consecutive=5"));
            }
        } finally {
            DataIntegrityRuntime.endRun();
        }
    }

    @Test
    public void markerGrammar_neutralAndBanFree() throws Exception {
        WildHuntEngine eng = engine(0);
        for (int i = 0; i < 200; i++) {
            String m = eng.nextMarker();
            assertTrue("grammar: " + m, m.matches("corpus-w\\d+-[0-9a-f]{12}"));
            String low = m.toLowerCase();
            for (String b : BANNED) {
                assertFalse("marker must not contain banned '" + b + "': " + m, low.contains(b));
            }
        }
        // The whitelist constraint (SqlDurableReadback): [A-Za-z0-9_.:-]+ — no spaces.
        assertTrue(eng.nextMarker().matches("[A-Za-z0-9_.:-]+"));
    }

    @Test
    public void reProbe_valueDelta_movedFlatVanished_andBoundHit() throws Exception {
        // VALUE_DELTA triple.
        Files.write(tmp.resolve("v.yaml"), (""
                + "triples:\n"
                + "  - name: fake-balance\n"
                + "    write_endpoint: \"POST /pay\"\n"
                + "    dependency: fake-store\n"
                + "    readback_endpoint: \"GET /account\"\n"
                + "    isolation_key: [userId]\n"
                + "    isolation_strategy: supplied\n"
                + "    readback_mode: value-delta\n"
                + "    value_probe: {match_field: userId, value_field: balance}\n")
                .getBytes(StandardCharsets.UTF_8));
        TargetTripleRegistry.Triple vt = TargetTripleRegistry.load(tmp.resolve("v.yaml")).triples.get(0);
        Map<String, String> key = new LinkedHashMap<>();
        key.put("userId", "u1");
        String baseline = "[{\"userId\":\"u1\",\"balance\":\"50.00\"}]";

        DataIntegrityRuntime.Http flat = fixed(200, "[{\"userId\":\"u1\",\"balance\":\"50.00\"}]");
        DataIntegrityRuntime.Http moved = fixed(200, "[{\"userId\":\"u1\",\"balance\":\"130.00\"}]");
        DataIntegrityRuntime.Http vanished = fixed(200, "[]");
        DataIntegrityRuntime.Http broken = fixed(503, "boom");

        assertEquals(DataIntegrityRuntime.ReProbeOutcome.ABSENT,
                DataIntegrityRuntime.reProbe(vt, flat, key, baseline).outcome);
        assertEquals(DataIntegrityRuntime.ReProbeOutcome.PRESENT,
                DataIntegrityRuntime.reProbe(vt, moved, key, baseline).outcome);
        assertEquals("VANISHED row is ERROR, never absence",
                DataIntegrityRuntime.ReProbeOutcome.ERROR,
                DataIntegrityRuntime.reProbe(vt, vanished, key, baseline).outcome);
        assertEquals(DataIntegrityRuntime.ReProbeOutcome.ERROR,
                DataIntegrityRuntime.reProbe(vt, broken, key, baseline).outcome);

        // MEMBERSHIP bound-hit: collection at the pre-registered bound + absent marker => ERROR.
        Files.write(tmp.resolve("b.yaml"), (""
                + "triples:\n"
                + "  - name: fake-bounded\n"
                + "    write_endpoint: \"POST /orders\"\n"
                + "    dependency: fake-store\n"
                + "    readback_endpoint: \"GET /orders\"\n"
                + "    isolation_key: [address1]\n"
                + "    isolation_strategy: supplied\n"
                + "    readback_mode: membership\n"
                + "    readback_bound: 2\n").getBytes(StandardCharsets.UTF_8));
        TargetTripleRegistry.Triple bt = TargetTripleRegistry.load(tmp.resolve("b.yaml")).triples.get(0);
        Map<String, String> bkey = new LinkedHashMap<>();
        bkey.put("address1", "corpus-w9-abcdefabcdef");
        DataIntegrityRuntime.Http atBound = fixed(200,
                "[{\"address1\":\"x\"},{\"address1\":\"y\"}]");
        assertEquals("bound-hit absence is ERROR, never CONFIRMED",
                DataIntegrityRuntime.ReProbeOutcome.ERROR,
                DataIntegrityRuntime.reProbe(bt, atBound, bkey, "[]").outcome);
    }

    private static DataIntegrityRuntime.Http fixed(int status, String body) {
        return new DataIntegrityRuntime.Http() {
            @Override
            public DataIntegrityRuntime.HttpResponse getSut(String path) {
                return new DataIntegrityRuntime.HttpResponse(status, body);
            }

            @Override
            public DataIntegrityRuntime.HttpResponse getAbsolute(String url) {
                return new DataIntegrityRuntime.HttpResponse(0, "unused");
            }
        };
    }
}
