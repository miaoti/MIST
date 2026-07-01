package io.mist.cli.fault;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Pins the B1.3 orchestration and the B2.3 pure-differential fire rule.
 * execute() runs against a stateful in-memory fake SUT: the fake test run
 * performs the freshen/ack/read-back hook sequence a generated test would,
 * persisting on the control run and masking (2xx-ack, no persist) on the
 * fault run — the executor must FIRE, toggle the injector in
 * clear→inject→clear order, and never leave a flag active even on a crash.
 */
public class PairedFaultExecutorTest {

    private static final String CONTACT_STEP = "POST /api/v1/adminbasicservice/adminbasic/contacts";
    private static final String CONTACT_READBACK = "/api/v1/adminbasicservice/adminbasic/contacts";

    /** In-memory contacts table served through the runtime's Http seam. */
    private static final class FakeSut implements DataIntegrityRuntime.Http {
        final List<Map<String, String>> rows = new ArrayList<>();

        void persist(Map<String, String> row) {
            rows.add(new LinkedHashMap<>(row));
        }

        @Override
        public DataIntegrityRuntime.HttpResponse getSut(String path) {
            if (!CONTACT_READBACK.equals(path)) {
                return new DataIntegrityRuntime.HttpResponse(404, "");
            }
            StringBuilder data = new StringBuilder("[");
            for (int i = 0; i < rows.size(); i++) {
                if (i > 0) {
                    data.append(',');
                }
                data.append(new JSONObject(rows.get(i)));
            }
            data.append(']');
            return new DataIntegrityRuntime.HttpResponse(200,
                    "{\"status\":1,\"data\":" + data + "}");
        }

        @Override
        public DataIntegrityRuntime.HttpResponse getAbsolute(String url) {
            return new DataIntegrityRuntime.HttpResponse(404, "");
        }
    }

    private static final class RecordingInjector implements FaultInjector {
        final List<String> ops = new ArrayList<>();
        boolean active = false;
        boolean failOnInject = false;

        @Override
        public void inject(FaultTarget target) {
            ops.add("inject:" + target.deployment);
            if (failOnInject) {
                throw new FaultInjectionException("rollout timed out");
            }
            active = true;
        }

        @Override
        public void clear(FaultTarget target) {
            ops.add("clear:" + target.deployment);
            active = false;
        }
    }

    private FakeSut sut;
    private RecordingInjector injector;
    private TargetTripleRegistry.Triple contacts;
    private String prevPoll;
    private String prevTimeout;

    @Before
    public void setUp() {
        sut = new FakeSut();
        injector = new RecordingInjector();
        contacts = TargetTripleRegistry.parse(
                        PairedFaultExecutorTest.class.getResourceAsStream(
                                "/My-Example/trainticket/target-triples.yaml"), "shipped")
                .triples.get(1);
        DataIntegrityRuntime.defaultHttpOverride = sut;
        prevPoll = System.getProperty(DataIntegrityRuntime.POLL_MS_PROPERTY);
        prevTimeout = System.getProperty(DataIntegrityRuntime.TIMEOUT_MS_PROPERTY);
        System.setProperty(DataIntegrityRuntime.POLL_MS_PROPERTY, "1");
        System.setProperty(DataIntegrityRuntime.TIMEOUT_MS_PROPERTY, "20");
    }

    @After
    public void tearDown() {
        DataIntegrityRuntime.defaultHttpOverride = null;
        DataIntegrityRuntime.endRun();
        restore(DataIntegrityRuntime.POLL_MS_PROPERTY, prevPoll);
        restore(DataIntegrityRuntime.TIMEOUT_MS_PROPERTY, prevTimeout);
    }

    private static void restore(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    /**
     * Plays the role of the generated pairing test: freshen the body, then
     * either persist (healthy SUT) or silently drop (LOST_WRITE) before
     * acknowledging success.
     */
    private PairedFaultExecutor.FilteredRun fakeGeneratedRun() {
        return () -> {
            String freshened = DataIntegrityRuntime.beforeWrite(CONTACT_STEP,
                    "{\"accountId\":\"pool\",\"documentNumber\":\"pool\",\"name\":\"n\"}");
            JSONObject body = new JSONObject(freshened);
            if (!injector.active) {
                Map<String, String> row = new LinkedHashMap<>();
                row.put("accountId", body.getString("accountId"));
                row.put("documentNumber", body.getString("documentNumber"));
                row.put("id", "srv-" + sut.rows.size());
                sut.persist(row);
            }
            DataIntegrityRuntime.afterWrite(CONTACT_STEP, 200, "{\"status\":1}", "trace-x");
        };
    }

    @Test
    public void maskedFaultRun_fires_andInjectorSequenceIsClean() throws Exception {
        PairedFaultExecutor executor = new PairedFaultExecutor(
                Collections.singletonList(contacts), injector, fakeGeneratedRun());
        List<PairedFaultExecutor.PairResult> results = executor.execute();

        assertEquals(1, results.size());
        PairedFaultExecutor.PairResult pair = results.get(0);
        assertEquals(PairedFaultExecutor.PairVerdict.FIRE, pair.pureDifferential);
        assertTrue(pair.control.acked && pair.control.readbackContainedX);
        assertTrue(pair.fault.acked);
        assertTrue(!pair.fault.readbackContainedX);
        assertEquals(DataIntegrityRuntime.QuiescenceGate.OBSERVED_PRESENT, pair.control.gate);
        assertEquals(DataIntegrityRuntime.QuiescenceGate.TIMEOUT_ABSENT, pair.fault.gate);
        assertEquals(java.util.Arrays.asList(
                "clear:ts-admin-basic-info-service",
                "inject:ts-admin-basic-info-service",
                "clear:ts-admin-basic-info-service"), injector.ops);
    }

    @Test
    public void healthySutBothRunsPersist_noFire() throws Exception {
        PairedFaultExecutor.FilteredRun alwaysPersist = () -> {
            String freshened = DataIntegrityRuntime.beforeWrite(CONTACT_STEP,
                    "{\"accountId\":\"pool\",\"documentNumber\":\"pool\"}");
            JSONObject body = new JSONObject(freshened);
            Map<String, String> row = new LinkedHashMap<>();
            row.put("accountId", body.getString("accountId"));
            row.put("documentNumber", body.getString("documentNumber"));
            sut.persist(row);
            DataIntegrityRuntime.afterWrite(CONTACT_STEP, 200, "{\"status\":1}", "trace-x");
        };
        List<PairedFaultExecutor.PairResult> results = new PairedFaultExecutor(
                Collections.singletonList(contacts), injector, alwaysPersist).execute();
        assertEquals(PairedFaultExecutor.PairVerdict.NO_FIRE, results.get(0).pureDifferential);
        assertTrue(results.get(0).reason.contains("persisted"));
    }

    @Test
    public void failedInject_stillRunsClearAll() {
        injector.failOnInject = true;
        try {
            new PairedFaultExecutor(Collections.singletonList(contacts), injector, fakeGeneratedRun())
                    .execute();
            fail("expected the inject failure to propagate");
        } catch (Exception expected) {
            // expected
        }
        // hygiene clear, control run, failing inject — then the finally must
        // still clear so no flag can stay live on the SUT.
        assertEquals(java.util.Arrays.asList(
                "clear:ts-admin-basic-info-service",
                "inject:ts-admin-basic-info-service",
                "clear:ts-admin-basic-info-service"), injector.ops);
    }

    @Test
    public void crashingFaultRun_stillClearsTheFlag() {
        final int[] calls = {0};
        PairedFaultExecutor.FilteredRun crashOnSecond = () -> {
            if (++calls[0] == 2) {
                throw new IllegalStateException("fault run crashed");
            }
            fakeGeneratedRun().run();
        };
        try {
            new PairedFaultExecutor(Collections.singletonList(contacts), injector, crashOnSecond)
                    .execute();
            fail("expected the crash to propagate");
        } catch (Exception expected) {
            // expected
        }
        assertEquals("flag must be cleared even on a crash",
                "clear:ts-admin-basic-info-service", injector.ops.get(injector.ops.size() - 1));
        assertTrue(!injector.active);
    }

    // ── verdict rule table (records built directly) ────────────────────────

    private static DataIntegrityRuntime.RunRecord record(String label, boolean acked,
                                                         boolean baselineHasX, boolean readbackHasX,
                                                         DataIntegrityRuntime.QuiescenceGate gate,
                                                         String error) {
        Map<String, String> key = new LinkedHashMap<>();
        key.put("accountId", "mist-1");
        return new DataIntegrityRuntime.RunRecord(label, "t", "POST /x", key, 200,
                acked ? Integer.valueOf(1) : Integer.valueOf(0), acked, baselineHasX, readbackHasX,
                gate, 1, 5, "{}", "{}", error);
    }

    @Test
    public void verdictTable() {
        DataIntegrityRuntime.RunRecord goodControl = record("control", true, false, true,
                DataIntegrityRuntime.QuiescenceGate.OBSERVED_PRESENT, null);

        assertEquals(PairedFaultExecutor.PairVerdict.NOT_EVALUABLE,
                PairedFaultExecutor.verdict("t", null,
                        record("fault", true, false, false,
                                DataIntegrityRuntime.QuiescenceGate.TIMEOUT_ABSENT, null)).pureDifferential);

        assertEquals("control error → NOT_EVALUABLE", PairedFaultExecutor.PairVerdict.NOT_EVALUABLE,
                PairedFaultExecutor.verdict("t",
                        record("control", true, false, true,
                                DataIntegrityRuntime.QuiescenceGate.OBSERVED_PRESENT, "boom"),
                        record("fault", true, false, false,
                                DataIntegrityRuntime.QuiescenceGate.TIMEOUT_ABSENT, null)).pureDifferential);

        assertEquals("baseline collision → NOT_EVALUABLE", PairedFaultExecutor.PairVerdict.NOT_EVALUABLE,
                PairedFaultExecutor.verdict("t", goodControl,
                        record("fault", true, true, false,
                                DataIntegrityRuntime.QuiescenceGate.TIMEOUT_ABSENT, null)).pureDifferential);

        assertEquals("control not persisted → NOT_EVALUABLE", PairedFaultExecutor.PairVerdict.NOT_EVALUABLE,
                PairedFaultExecutor.verdict("t",
                        record("control", true, false, false,
                                DataIntegrityRuntime.QuiescenceGate.TIMEOUT_ABSENT, null),
                        record("fault", true, false, false,
                                DataIntegrityRuntime.QuiescenceGate.TIMEOUT_ABSENT, null)).pureDifferential);

        assertEquals("fault soft-rejected → NO_FIRE", PairedFaultExecutor.PairVerdict.NO_FIRE,
                PairedFaultExecutor.verdict("t", goodControl,
                        record("fault", false, false, false,
                                DataIntegrityRuntime.QuiescenceGate.NOT_APPLICABLE, null)).pureDifferential);

        PairedFaultExecutor.PairResult fire = PairedFaultExecutor.verdict("t", goodControl,
                record("fault", true, false, false,
                        DataIntegrityRuntime.QuiescenceGate.OBSERVED_COMPLETE_ABSENT, null));
        assertEquals(PairedFaultExecutor.PairVerdict.FIRE, fire.pureDifferential);
        assertTrue("fire reason must carry the gate stratum",
                fire.reason.contains("OBSERVED_COMPLETE_ABSENT"));
    }

    @Test
    public void benignProbe_healthySut_zeroFires() throws Exception {
        PairedFaultExecutor.FilteredRun alwaysPersist = () -> {
            String freshened = DataIntegrityRuntime.beforeWrite(CONTACT_STEP,
                    "{\"accountId\":\"pool\",\"documentNumber\":\"pool\"}");
            JSONObject body = new JSONObject(freshened);
            Map<String, String> row = new LinkedHashMap<>();
            row.put("accountId", body.getString("accountId"));
            row.put("documentNumber", body.getString("documentNumber"));
            sut.persist(row);
            DataIntegrityRuntime.afterWrite(CONTACT_STEP, 200, "{\"status\":1}", "trace-x");
        };
        List<DataIntegrityRuntime.RunRecord> records = new PairedFaultExecutor(
                Collections.singletonList(contacts), injector, alwaysPersist).benignProbe(3);
        assertEquals(3, records.size());
        JSONObject probe = PairedFaultExecutor.fpProbeJson(records, 20);
        JSONObject aggregate = probe.getJSONObject("aggregate");
        assertEquals(3, aggregate.getInt("ackedBenignRuns"));
        assertEquals(0, aggregate.getInt("fpFires"));
        assertEquals("PASS", probe.getJSONObject("syncFpBar").getString("verdict"));
        assertEquals("benign runs are flag-off (one hygiene clear only)",
                java.util.Collections.singletonList("clear:ts-admin-basic-info-service"), injector.ops);
        assertEquals("sync", probe.getString("stratum"));
        assertNotNull(probe.getString("asyncDisclaimer"));
        assertNotNull(probe.getString("acceptThenDropTrap"));
    }

    @Test
    public void fpProbeJson_ratesStrataAndCurve() {
        List<DataIntegrityRuntime.RunRecord> records = new java.util.ArrayList<>();
        // 8 fast-present benign runs (present on read-back within 100 ms)
        for (int i = 0; i < 8; i++) {
            records.add(probeRecord(true, true, 100,
                    DataIntegrityRuntime.QuiescenceGate.OBSERVED_PRESENT, null));
        }
        // 1 slow-present run (eventually consistent: present at 6000 ms)
        records.add(probeRecord(true, true, 6_000,
                DataIntegrityRuntime.QuiescenceGate.OBSERVED_PRESENT, null));
        // 1 observation-gated fire (the high-confidence FP stratum)
        records.add(probeRecord(true, false, 10_000,
                DataIntegrityRuntime.QuiescenceGate.OBSERVED_COMPLETE_ABSENT, null));
        // 1 timeout-gated fire (lower-confidence stratum)
        records.add(probeRecord(true, false, 10_000,
                DataIntegrityRuntime.QuiescenceGate.TIMEOUT_ABSENT, null));
        // 1 invalid run (hook error) — excluded from every denominator
        records.add(probeRecord(false, false, 0,
                DataIntegrityRuntime.QuiescenceGate.NOT_APPLICABLE, "boom"));

        JSONObject probe = PairedFaultExecutor.fpProbeJson(records, 10_000);
        JSONObject aggregate = probe.getJSONObject("aggregate");
        assertEquals(11, aggregate.getInt("ackedBenignRuns"));
        assertEquals(1, aggregate.getInt("invalidRuns"));
        assertEquals(2, aggregate.getInt("fpFires"));
        assertEquals(2.0 / 11, aggregate.getDouble("fpRate"), 1e-9);
        assertEquals(1, aggregate.getInt("observedGatedFpFires"));
        assertEquals(1, aggregate.getInt("timeoutGatedFpFires"));
        assertEquals(1.0 / 11, aggregate.getDouble("nonTimeoutGatedFpRate"), 1e-9);
        assertEquals("1/11 = 9.1% > 5% bar", "FAIL",
                probe.getJSONObject("syncFpBar").getString("verdict"));

        // Curve: at a 5000 ms cutoff the slow-present run would also have fired.
        org.json.JSONArray curve = aggregate.getJSONArray("fpVsTimeoutCurve");
        double fpAt5000 = -1;
        double fpAt10000 = -1;
        for (int i = 0; i < curve.length(); i++) {
            JSONObject point = curve.getJSONObject(i);
            if (point.getLong("timeoutMs") == 5_000) {
                fpAt5000 = point.getDouble("fpRate");
            }
            if (point.getLong("timeoutMs") == 10_000) {
                fpAt10000 = point.getDouble("fpRate");
            }
        }
        assertEquals(3.0 / 11, fpAt5000, 1e-9);
        assertEquals(2.0 / 11, fpAt10000, 1e-9);
    }

    private static DataIntegrityRuntime.RunRecord probeRecord(boolean acked, boolean present,
                                                              long elapsedMs,
                                                              DataIntegrityRuntime.QuiescenceGate gate,
                                                              String error) {
        Map<String, String> key = new LinkedHashMap<>();
        key.put("accountId", "mist-x");
        return new DataIntegrityRuntime.RunRecord("benign-1", "t", "POST /x", key, 200,
                acked ? Integer.valueOf(1) : null, acked, false, present, gate, 1, elapsedMs,
                "{}", "{}", error);
    }

    @Test
    public void report_isWrittenWithVerdictAndStrata() throws Exception {
        PairedFaultExecutor.PairResult fire = PairedFaultExecutor.verdict("t",
                record("control", true, false, true,
                        DataIntegrityRuntime.QuiescenceGate.OBSERVED_PRESENT, null),
                record("fault", true, false, false,
                        DataIntegrityRuntime.QuiescenceGate.TIMEOUT_ABSENT, null));
        java.nio.file.Path file = java.nio.file.Files.createTempDirectory("di-report")
                .resolve("pairing_test.json");
        PairedFaultExecutor.writeReport(file, Collections.singletonList(fire), "run-1");
        String json = new String(java.nio.file.Files.readAllBytes(file),
                java.nio.charset.StandardCharsets.UTF_8);
        JSONObject parsed = new JSONObject(json);
        assertEquals("run-1", parsed.getString("runId"));
        assertNotNull(parsed.getString("gatedMode"));
        assertEquals("FIRE", parsed.getJSONArray("pairs").getJSONObject(0)
                .getString("pureDifferential"));
        assertEquals("TIMEOUT_ABSENT", parsed.getJSONArray("pairs").getJSONObject(0)
                .getJSONObject("fault").getString("quiescenceGate"));
    }
}
