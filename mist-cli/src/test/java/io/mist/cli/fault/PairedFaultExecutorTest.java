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
        /** R1fix seam: read-back status after the first (baseline) GET. */
        int readbackStatusAfterBaseline = 200;
        /** R1fix seam: status of the very first (baseline) GET. */
        int baselineStatus = 200;
        /** H2 seam: calls #2..#(1+N) return 500, then recover to 200. */
        int failReadbackPollsUpTo = 0;
        int getSutCalls = 0;
        /** R4fix seam: hide rows until the trace-completeness check ran twice. */
        boolean hideRowsUntilTraceStable = false;
        boolean traceStable = false;
        int getAbsoluteCalls = 0;

        void persist(Map<String, String> row) {
            rows.add(new LinkedHashMap<>(row));
        }

        @Override
        public DataIntegrityRuntime.HttpResponse getSut(String path) {
            if (!CONTACT_READBACK.equals(path)) {
                return new DataIntegrityRuntime.HttpResponse(404, "");
            }
            getSutCalls++;
            if (getSutCalls == 1 && baselineStatus / 100 != 2) {
                return new DataIntegrityRuntime.HttpResponse(baselineStatus,
                        "{\"error\":\"baseline down\"}");
            }
            if (getSutCalls > 1 && readbackStatusAfterBaseline / 100 != 2) {
                return new DataIntegrityRuntime.HttpResponse(readbackStatusAfterBaseline,
                        "{\"error\":\"boom\"}");
            }
            if (getSutCalls > 1 && getSutCalls <= 1 + failReadbackPollsUpTo) {
                return new DataIntegrityRuntime.HttpResponse(500, "{\"error\":\"transient\"}");
            }
            boolean hidden = hideRowsUntilTraceStable && !traceStable;
            StringBuilder data = new StringBuilder("[");
            if (!hidden) {
                for (int i = 0; i < rows.size(); i++) {
                    if (i > 0) {
                        data.append(',');
                    }
                    data.append(new JSONObject(rows.get(i)));
                }
            }
            data.append(']');
            return new DataIntegrityRuntime.HttpResponse(200,
                    "{\"status\":1,\"data\":" + data + "}");
        }

        @Override
        public DataIntegrityRuntime.HttpResponse getAbsolute(String url) {
            getAbsoluteCalls++;
            if (getAbsoluteCalls >= 2) {
                traceStable = true;
            }
            return new DataIntegrityRuntime.HttpResponse(200,
                    "{\"data\":[{\"spans\":[{},{}]}]}");
        }
    }

    private static final class RecordingInjector implements FaultInjector {
        final List<String> ops = new ArrayList<>();
        boolean active = false;
        boolean failOnInject = false;
        /** C-P1-3fix seam: the final clear (after an inject) fails. */
        boolean failOnFinalClear = false;

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
            if (failOnFinalClear && ops.stream().anyMatch(op -> op.startsWith("inject:"))) {
                throw new FaultInjectionException("clear rollout timed out");
            }
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
        assertEquals("a collapsed denominator must not auto-PASS the bar (review F1)",
                "NOT_EVALUABLE", probe.getJSONObject("syncFpBar").getString("verdict"));
        assertEquals("benign runs are flag-off (one hygiene clear only)",
                java.util.Collections.singletonList("clear:ts-admin-basic-info-service"), injector.ops);
        assertEquals("sync", probe.getString("stratum"));
        assertNotNull(probe.getString("asyncDisclaimer"));
        assertNotNull(probe.getString("acceptThenDropTrap"));
    }

    @Test
    public void fpProbeJson_ratesStrataAndCurve() {
        List<DataIntegrityRuntime.RunRecord> records = new java.util.ArrayList<>();
        // 20 fast-present benign runs (present on read-back within 100 ms)
        for (int i = 0; i < 20; i++) {
            records.add(probeRecord(true, true, 100,
                    DataIntegrityRuntime.QuiescenceGate.OBSERVED_PRESENT, null));
        }
        // 1 slow-present run (eventually consistent: present at 6000 ms)
        records.add(probeRecord(true, true, 6_000,
                DataIntegrityRuntime.QuiescenceGate.OBSERVED_PRESENT, null));
        // 1 presence observed on the final poll PAST the cap — the poll loop
        // is the authority at the cap, so it must not re-count as a fire there.
        records.add(probeRecord(true, true, 11_000,
                DataIntegrityRuntime.QuiescenceGate.OBSERVED_PRESENT, null));
        // 2 observation-gated fires (the high-confidence FP stratum)
        records.add(probeRecord(true, false, 10_000,
                DataIntegrityRuntime.QuiescenceGate.OBSERVED_COMPLETE_ABSENT, null));
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
        assertEquals(25, aggregate.getInt("ackedBenignRuns"));
        assertEquals(1, aggregate.getInt("invalidRuns"));
        assertEquals(3, aggregate.getInt("fpFires"));
        assertEquals(3.0 / 25, aggregate.getDouble("fpRate"), 1e-9);
        assertEquals(2, aggregate.getInt("observedGatedFpFires"));
        assertEquals(1, aggregate.getInt("timeoutGatedFpFires"));
        assertEquals(2.0 / 25, aggregate.getDouble("nonTimeoutGatedFpRate"), 1e-9);
        assertEquals("2/25 = 8% > 5% bar", "FAIL",
                probe.getJSONObject("syncFpBar").getString("verdict"));

        org.json.JSONArray curve = aggregate.getJSONArray("fpVsTimeoutCurve");
        double fpAt5000 = -1;
        double fpAt10000 = -1;
        long maxCutoff = -1;
        for (int i = 0; i < curve.length(); i++) {
            JSONObject point = curve.getJSONObject(i);
            maxCutoff = Math.max(maxCutoff, point.getLong("timeoutMs"));
            if (point.getLong("timeoutMs") == 5_000) {
                fpAt5000 = point.getDouble("fpRate");
            }
            if (point.getLong("timeoutMs") == 10_000) {
                fpAt10000 = point.getDouble("fpRate");
            }
        }
        // Below the cap: both slow presences would have been judged absent.
        assertEquals(5.0 / 25, fpAt5000, 1e-9);
        // At the cap: exactly the observed fires — the poll loop is authoritative.
        assertEquals(3.0 / 25, fpAt10000, 1e-9);
        // No censored points beyond the cap.
        assertEquals(10_000, maxCutoff);
    }

    // ── bar v2 (hardening-wave R2fix): observation-gate degradation guards ──
    // Floors pre-registered in prep/hardening-wave-spec.md §1:
    // gateResolvedFraction >= 0.5 AND timeoutGatedFraction <= 0.3, else the
    // bar is NOT_EVALUABLE (never PASS) with the tripped guard named.

    private static void addProbeRecords(List<DataIntegrityRuntime.RunRecord> records, int n,
                                        boolean present, DataIntegrityRuntime.QuiescenceGate gate) {
        for (int i = 0; i < n; i++) {
            records.add(probeRecord(true, present, 5_000, gate, null));
        }
    }

    @Test
    public void barV2_t1_healthyGate_overBar_fails() {
        List<DataIntegrityRuntime.RunRecord> records = new ArrayList<>();
        addProbeRecords(records, 25, true, DataIntegrityRuntime.QuiescenceGate.OBSERVED_PRESENT);
        addProbeRecords(records, 10, false, DataIntegrityRuntime.QuiescenceGate.TIMEOUT_ABSENT);
        addProbeRecords(records, 5, false, DataIntegrityRuntime.QuiescenceGate.OBSERVED_COMPLETE_ABSENT);
        JSONObject bar = PairedFaultExecutor.fpProbeJson(records, 10_000).getJSONObject("syncFpBar");
        // resolved 30/40 = 0.75, timeout-gated 10/40 = 0.25 -> evaluable; 5/40 = 0.125 > 0.05
        assertEquals("FAIL", bar.getString("verdict"));
    }

    @Test
    public void barV2_t2_timeoutFractionOverCap_notEvaluable() {
        List<DataIntegrityRuntime.RunRecord> records = new ArrayList<>();
        addProbeRecords(records, 10, true, DataIntegrityRuntime.QuiescenceGate.OBSERVED_PRESENT);
        addProbeRecords(records, 25, false, DataIntegrityRuntime.QuiescenceGate.TIMEOUT_ABSENT);
        addProbeRecords(records, 5, false, DataIntegrityRuntime.QuiescenceGate.OBSERVED_COMPLETE_ABSENT);
        JSONObject bar = PairedFaultExecutor.fpProbeJson(records, 10_000).getJSONObject("syncFpBar");
        // timeout-gated 25/40 = 0.625 > 0.3 cap
        assertEquals("NOT_EVALUABLE", bar.getString("verdict"));
        assertTrue("reason must name the tripped guard",
                bar.getString("reason").contains("timeout-gated fraction"));
    }

    @Test
    public void barV2_t3_reviewerScenario_hiddenLossCannotPass() {
        // Reviewer B-1: acked=100, observedGated=4 (4% would 'PASS'),
        // timeoutGated=50 -> the cap makes it NOT_EVALUABLE, not PASS.
        List<DataIntegrityRuntime.RunRecord> records = new ArrayList<>();
        addProbeRecords(records, 46, true, DataIntegrityRuntime.QuiescenceGate.OBSERVED_PRESENT);
        addProbeRecords(records, 50, false, DataIntegrityRuntime.QuiescenceGate.TIMEOUT_ABSENT);
        addProbeRecords(records, 4, false, DataIntegrityRuntime.QuiescenceGate.OBSERVED_COMPLETE_ABSENT);
        JSONObject bar = PairedFaultExecutor.fpProbeJson(records, 10_000).getJSONObject("syncFpBar");
        assertEquals("NOT_EVALUABLE", bar.getString("verdict"));
    }

    @Test
    public void barV2_t4_jaegerDownAllTimeoutGated_noVacuousPass() {
        List<DataIntegrityRuntime.RunRecord> records = new ArrayList<>();
        addProbeRecords(records, 10, true, DataIntegrityRuntime.QuiescenceGate.OBSERVED_PRESENT);
        addProbeRecords(records, 20, false, DataIntegrityRuntime.QuiescenceGate.TIMEOUT_ABSENT);
        JSONObject bar = PairedFaultExecutor.fpProbeJson(records, 10_000).getJSONObject("syncFpBar");
        // observedGated == 0 would read 0.0 <= 0.05 under v1; v2 blocks the PASS label.
        assertEquals("NOT_EVALUABLE", bar.getString("verdict"));
    }

    @Test
    public void barV2_t5_cleanRun_passes() {
        List<DataIntegrityRuntime.RunRecord> records = new ArrayList<>();
        addProbeRecords(records, 29, true, DataIntegrityRuntime.QuiescenceGate.OBSERVED_PRESENT);
        addProbeRecords(records, 1, false, DataIntegrityRuntime.QuiescenceGate.OBSERVED_COMPLETE_ABSENT);
        JSONObject bar = PairedFaultExecutor.fpProbeJson(records, 10_000).getJSONObject("syncFpBar");
        assertEquals("PASS", bar.getString("verdict"));
        assertEquals(1.0 / 30, bar.getDouble("value"), 1e-9);
    }

    @Test
    public void fpProbeJson_lowTimeout_trimsCurveToCap() {
        List<DataIntegrityRuntime.RunRecord> records = new java.util.ArrayList<>();
        for (int i = 0; i < 20; i++) {
            records.add(probeRecord(true, true, 100,
                    DataIntegrityRuntime.QuiescenceGate.OBSERVED_PRESENT, null));
        }
        JSONObject aggregate = PairedFaultExecutor.fpProbeJson(records, 3_000)
                .getJSONObject("aggregate");
        org.json.JSONArray curve = aggregate.getJSONArray("fpVsTimeoutCurve");
        assertEquals("cutoffs 500/1000/2000 + the cap", 4, curve.length());
        assertEquals(3_000, curve.getJSONObject(curve.length() - 1).getLong("timeoutMs"));
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

    // ── hardening wave: R3fix / C-P1-3fix / R1fix / R4fix / R7fix ──────────

    @Test
    public void r3fix_persistedSiblingCannotMaskALostOne() throws Exception {
        // Two hooked writes per run: variant #1 persists in BOTH runs (immune
        // to the fault), variant #2 honors the flag. The old first-acked
        // representative join returned NO_FIRE here; the per-record join must
        // surface variant #2's genuine acknowledged-but-lost write.
        PairedFaultExecutor.FilteredRun twoWrites = () -> {
            for (int variant = 0; variant < 2; variant++) {
                String freshened = DataIntegrityRuntime.beforeWrite(CONTACT_STEP,
                        "{\"accountId\":\"pool\",\"documentNumber\":\"pool\"}");
                JSONObject body = new JSONObject(freshened);
                boolean immuneVariant = variant == 0;
                if (immuneVariant || !injector.active) {
                    Map<String, String> row = new LinkedHashMap<>();
                    row.put("accountId", body.getString("accountId"));
                    row.put("documentNumber", body.getString("documentNumber"));
                    sut.persist(row);
                }
                DataIntegrityRuntime.afterWrite(CONTACT_STEP, 200, "{\"status\":1}", "trace-x");
            }
        };
        List<PairedFaultExecutor.PairResult> results = new PairedFaultExecutor(
                Collections.singletonList(contacts), injector, twoWrites).execute();
        assertEquals(1, results.size());
        PairedFaultExecutor.PairResult pair = results.get(0);
        assertEquals(PairedFaultExecutor.PairVerdict.FIRE, pair.pureDifferential);
        assertEquals(2, pair.controlRecordCount);
        assertEquals(2, pair.faultRecordCount);
        assertEquals(1, pair.firePairs);
        assertEquals(1, pair.noFirePairs);
        assertEquals(0, pair.unjoinedRecords);
    }

    @Test
    public void cP13fix_clearFailure_persistsEvidenceBeforeThrow() {
        injector.failOnFinalClear = true;
        PairedFaultExecutor executor = new PairedFaultExecutor(
                Collections.singletonList(contacts), injector, fakeGeneratedRun());
        List<List<PairedFaultExecutor.PairResult>> sunk = new ArrayList<>();
        List<FaultInjector.FaultTarget> sunkFlags = new ArrayList<>();
        executor.onClearFailure((evidence, flags) -> {
            sunk.add(evidence);
            sunkFlags.addAll(flags);
        });
        try {
            executor.execute();
            fail("expected the F2 clear-failure to propagate");
        } catch (FaultInjector.FaultInjectionException expected) {
            // expected — the SUT-may-be-faulted signal must stay loud
        } catch (Exception e) {
            fail("expected FaultInjectionException, got " + e);
        }
        assertEquals("evidence must be sunk exactly once before the throw", 1, sunk.size());
        assertEquals(PairedFaultExecutor.PairVerdict.FIRE,
                sunk.get(0).get(0).pureDifferential);
        assertEquals("the sink must receive the failed flags (review H6)", 1, sunkFlags.size());
    }

    @Test
    public void h2_transientNon2xxPolls_recoverToPresent() throws Exception {
        // Two polls 500, then recovery — the old abort-on-first would have
        // burned the record; poll-through must converge to OBSERVED_PRESENT.
        sut.failReadbackPollsUpTo = 2;
        DataIntegrityRuntime.beginRun(Collections.singletonList(contacts), "control");
        String freshened = DataIntegrityRuntime.beforeWrite(CONTACT_STEP,
                "{\"accountId\":\"pool\",\"documentNumber\":\"pool\"}");
        JSONObject body = new JSONObject(freshened);
        Map<String, String> row = new LinkedHashMap<>();
        row.put("accountId", body.getString("accountId"));
        row.put("documentNumber", body.getString("documentNumber"));
        sut.persist(row);
        DataIntegrityRuntime.afterWrite(CONTACT_STEP, 200, "{\"status\":1}", "trace-x");
        List<DataIntegrityRuntime.RunRecord> records = DataIntegrityRuntime.endRun();
        assertEquals(1, records.size());
        assertEquals(DataIntegrityRuntime.QuiescenceGate.OBSERVED_PRESENT, records.get(0).gate);
        assertTrue(records.get(0).readbackContainedX);
        assertEquals("transient blips must not error the record", null, records.get(0).error);
    }

    @Test
    public void r1fix_baselineNon2xx_isErrorNotWeakenedIsolation() throws Exception {
        sut.baselineStatus = 503;
        DataIntegrityRuntime.beginRun(Collections.singletonList(contacts), "control");
        String passedThrough = DataIntegrityRuntime.beforeWrite(CONTACT_STEP,
                "{\"accountId\":\"pool\",\"documentNumber\":\"pool\"}");
        assertTrue("body must pass through unfreshened on a baseline failure",
                passedThrough.contains("pool"));
        DataIntegrityRuntime.afterWrite(CONTACT_STEP, 200, "{\"status\":1}", "trace-x");
        List<DataIntegrityRuntime.RunRecord> records = DataIntegrityRuntime.endRun();
        assertEquals(1, records.size());
        assertNotNull(records.get(0).error);
        assertTrue(records.get(0).error, records.get(0).error.contains("baseline read-back HTTP 503"));
    }

    @Test
    public void r3fix_countMismatch_surfacesNonzeroUnjoined() throws Exception {
        // The fault run executes only ONE of the two writes (the second step
        // never runs at all — the C-F1 shape): counts 2 vs 1 must surface as
        // unjoinedRecords=1, never silently.
        PairedFaultExecutor.FilteredRun skipSecondUnderFault = () -> {
            int writes = injector.active ? 1 : 2;
            for (int i = 0; i < writes; i++) {
                fakeGeneratedRun().run();
            }
        };
        List<PairedFaultExecutor.PairResult> results = new PairedFaultExecutor(
                Collections.singletonList(contacts), injector, skipSecondUnderFault).execute();
        PairedFaultExecutor.PairResult pair = results.get(0);
        assertEquals(2, pair.controlRecordCount);
        assertEquals(1, pair.faultRecordCount);
        assertEquals(1, pair.unjoinedRecords);
        assertEquals("the joined pair still verdicts", PairedFaultExecutor.PairVerdict.FIRE,
                pair.pureDifferential);
    }

    @Test
    public void r4fix_absentOnPostSettleReread_staysCompleteAbsent() throws Exception {
        String prevJaeger = System.getProperty("jaeger.base.url");
        String prevSettle = System.getProperty(DataIntegrityRuntime.TRACE_SETTLE_MS_PROPERTY);
        System.setProperty("jaeger.base.url", "http://fake/api");
        System.setProperty(DataIntegrityRuntime.TRACE_SETTLE_MS_PROPERTY, "1");
        try {
            // Nothing is ever persisted: the re-read is also absent, so the
            // high-confidence absence stands.
            DataIntegrityRuntime.beginRun(Collections.singletonList(contacts), "fault");
            String freshened = DataIntegrityRuntime.beforeWrite(CONTACT_STEP,
                    "{\"accountId\":\"pool\",\"documentNumber\":\"pool\"}");
            assertNotNull(freshened);
            DataIntegrityRuntime.afterWrite(CONTACT_STEP, 200, "{\"status\":1}", "abc123");
            List<DataIntegrityRuntime.RunRecord> records = DataIntegrityRuntime.endRun();
            assertEquals(1, records.size());
            assertEquals(DataIntegrityRuntime.QuiescenceGate.OBSERVED_COMPLETE_ABSENT,
                    records.get(0).gate);
            assertTrue(!records.get(0).readbackContainedX);
        } finally {
            restore("jaeger.base.url", prevJaeger);
            restore(DataIntegrityRuntime.TRACE_SETTLE_MS_PROPERTY, prevSettle);
        }
    }

    @Test
    public void h1fix_orphanedBeforeWrite_getsSyntheticErrorRecord() throws Exception {
        DataIntegrityRuntime.beginRun(Collections.singletonList(contacts), "control");
        // First write dies between its hooks: beforeWrite runs, afterWrite never fires.
        DataIntegrityRuntime.beforeWrite(CONTACT_STEP,
                "{\"accountId\":\"pool\",\"documentNumber\":\"pool\"}");
        // Second write proceeds normally; its beforeWrite must detect the orphan.
        String freshened = DataIntegrityRuntime.beforeWrite(CONTACT_STEP,
                "{\"accountId\":\"pool\",\"documentNumber\":\"pool\"}");
        JSONObject body = new JSONObject(freshened);
        Map<String, String> row = new LinkedHashMap<>();
        row.put("accountId", body.getString("accountId"));
        row.put("documentNumber", body.getString("documentNumber"));
        sut.persist(row);
        DataIntegrityRuntime.afterWrite(CONTACT_STEP, 200, "{\"status\":1}", "trace-x");
        List<DataIntegrityRuntime.RunRecord> records = DataIntegrityRuntime.endRun();
        assertEquals("synthetic orphan record + the normal record", 2, records.size());
        assertNotNull(records.get(0).error);
        assertTrue(records.get(0).error, records.get(0).error.contains("orphaned"));
        assertEquals(DataIntegrityRuntime.QuiescenceGate.OBSERVED_PRESENT, records.get(1).gate);
    }

    @Test
    public void f2Report_carriesMarkerAndFlags_cleanReportDoesNot() throws Exception {
        PairedFaultExecutor.PairResult fire = PairedFaultExecutor.verdict("t",
                record("control", true, false, true,
                        DataIntegrityRuntime.QuiescenceGate.OBSERVED_PRESENT, null),
                record("fault", true, false, false,
                        DataIntegrityRuntime.QuiescenceGate.TIMEOUT_ABSENT, null));
        java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("di-f2");

        java.nio.file.Path f2File = dir.resolve("f2.json");
        PairedFaultExecutor.writeReport(f2File, Collections.singletonList(fire), null, 0, "run-f2",
                true, Collections.singletonList(
                        new FaultInjector.FaultTarget("ts-x", "mist.fault.x.enabled")));
        JSONObject f2 = new JSONObject(new String(java.nio.file.Files.readAllBytes(f2File),
                java.nio.charset.StandardCharsets.UTF_8));
        assertTrue(f2.getBoolean("f2ClearFailure"));
        assertEquals(1, f2.getJSONArray("f2FailedFlags").length());

        java.nio.file.Path cleanFile = dir.resolve("clean.json");
        PairedFaultExecutor.writeReport(cleanFile, Collections.singletonList(fire), "run-clean");
        JSONObject clean = new JSONObject(new String(java.nio.file.Files.readAllBytes(cleanFile),
                java.nio.charset.StandardCharsets.UTF_8));
        assertTrue("clean reports must not carry the f2 marker", !clean.has("f2ClearFailure"));
        assertTrue(!clean.has("f2FailedFlags"));
    }

    @Test
    public void r1fix_failingReadback_isErrorNotAbsence() throws Exception {
        sut.readbackStatusAfterBaseline = 500;
        DataIntegrityRuntime.beginRun(Collections.singletonList(contacts), "control");
        String freshened = DataIntegrityRuntime.beforeWrite(CONTACT_STEP,
                "{\"accountId\":\"pool\",\"documentNumber\":\"pool\"}");
        assertNotNull(freshened);
        DataIntegrityRuntime.afterWrite(CONTACT_STEP, 200, "{\"status\":1}", "trace-x");
        List<DataIntegrityRuntime.RunRecord> records = DataIntegrityRuntime.endRun();
        assertEquals(1, records.size());
        DataIntegrityRuntime.RunRecord record = records.get(0);
        assertNotNull("a 5xx read-back must be an error, never an absence", record.error);
        assertTrue(record.error, record.error.contains("read-back HTTP 500"));
        assertTrue(!record.readbackContainedX);
        assertEquals(Integer.valueOf(500), record.readbackHttpStatus);
        assertEquals("errored records are NOT_EVALUABLE, not NO_FIRE",
                PairedFaultExecutor.PairVerdict.NOT_EVALUABLE,
                PairedFaultExecutor.verdict("t",
                        record("control", true, false, true,
                                DataIntegrityRuntime.QuiescenceGate.OBSERVED_PRESENT, null),
                        record).pureDifferential);
    }

    @Test
    public void r4fix_writeLandingDuringSettle_isPresentNotCompleteAbsent() throws Exception {
        String prevJaeger = System.getProperty("jaeger.base.url");
        String prevSettle = System.getProperty(DataIntegrityRuntime.TRACE_SETTLE_MS_PROPERTY);
        System.setProperty("jaeger.base.url", "http://fake/api");
        System.setProperty(DataIntegrityRuntime.TRACE_SETTLE_MS_PROPERTY, "1");
        try {
            sut.hideRowsUntilTraceStable = true;
            DataIntegrityRuntime.beginRun(Collections.singletonList(contacts), "benign-1");
            String freshened = DataIntegrityRuntime.beforeWrite(CONTACT_STEP,
                    "{\"accountId\":\"pool\",\"documentNumber\":\"pool\"}");
            JSONObject body = new JSONObject(freshened);
            Map<String, String> row = new LinkedHashMap<>();
            row.put("accountId", body.getString("accountId"));
            row.put("documentNumber", body.getString("documentNumber"));
            sut.persist(row);
            DataIntegrityRuntime.afterWrite(CONTACT_STEP, 200, "{\"status\":1}", "abc123");
            List<DataIntegrityRuntime.RunRecord> records = DataIntegrityRuntime.endRun();
            assertEquals(1, records.size());
            assertEquals("a write visible on the post-settle re-read is PRESENT, "
                            + "not a high-confidence loss",
                    DataIntegrityRuntime.QuiescenceGate.OBSERVED_PRESENT, records.get(0).gate);
            assertTrue(records.get(0).readbackContainedX);
        } finally {
            restore("jaeger.base.url", prevJaeger);
            restore(DataIntegrityRuntime.TRACE_SETTLE_MS_PROPERTY, prevSettle);
        }
    }

    @Test
    public void r7fix_beginRunRefusesParallelExecution() {
        String prev = System.getProperty("mst.test.parallelism");
        System.setProperty("mst.test.parallelism", "4");
        try {
            DataIntegrityRuntime.beginRun(Collections.singletonList(contacts), "control");
            DataIntegrityRuntime.endRun();
            fail("expected beginRun to refuse mst.test.parallelism=4");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("parallelism"));
        } finally {
            restore("mst.test.parallelism", prev);
        }
    }

    @Test
    public void r1fix_readbackAtBound_isErrorNotAbsence() throws Exception {
        TargetTripleRegistry.Triple bounded = TargetTripleRegistry.parse(
                new java.io.ByteArrayInputStream(("triples:\n"
                        + "  - name: contacts-bounded\n"
                        + "    write_endpoint: \"" + CONTACT_STEP + "\"\n"
                        + "    dependency: ts-contacts\n"
                        + "    readback_endpoint: \"GET " + CONTACT_READBACK + "\"\n"
                        + "    isolation_key: [accountId, documentNumber]\n"
                        + "    readback_bound: 2\n").getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                "inline").triples.get(0);
        Map<String, String> filler = new LinkedHashMap<>();
        filler.put("accountId", "other-1");
        filler.put("documentNumber", "other-1");
        sut.persist(filler);
        Map<String, String> filler2 = new LinkedHashMap<>();
        filler2.put("accountId", "other-2");
        filler2.put("documentNumber", "other-2");
        sut.persist(filler2);

        DataIntegrityRuntime.beginRun(Collections.singletonList(bounded), "benign-1");
        String freshened = DataIntegrityRuntime.beforeWrite(CONTACT_STEP,
                "{\"accountId\":\"pool\",\"documentNumber\":\"pool\"}");
        assertNotNull(freshened);
        // The fresh write is silently dropped; the collection sits AT the bound.
        DataIntegrityRuntime.afterWrite(CONTACT_STEP, 200, "{\"status\":1}", "trace-x");
        List<DataIntegrityRuntime.RunRecord> records = DataIntegrityRuntime.endRun();
        assertEquals(1, records.size());
        assertNotNull("absence at the completeness bound must be an error", records.get(0).error);
        assertTrue(records.get(0).error, records.get(0).error.contains("bound"));
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
