package io.mist.cli.comparator;

import io.mist.cli.fault.FaultInjector;
import io.mist.cli.fault.TargetTripleRegistry;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Pins the calibration sequence, the control-run all-pass gate, the evaluator
 * primitives against a stateful fake SUT, the report shape, and the
 * report-before-F2-throw behavior (design tests t2–t5 + the F2 lesson).
 */
public class ComparatorRunnerTest {

    private static final String ENDPOINT = "POST /api/v1/adminbasicservice/adminbasic/contacts";
    private static final String READBACK = "/api/v1/adminbasicservice/adminbasic/contacts";

    /** Contacts-shaped fake SUT: 200 + {1,'Create Success',null}, drops when faulted. */
    private static final class FakeSut implements ContractEvaluator.SutClient {
        final List<Map<String, String>> rows = new ArrayList<>();
        final RecordingInjector injector;
        boolean failStateGet = false;

        FakeSut(RecordingInjector injector) {
            this.injector = injector;
        }

        @Override
        public ContractEvaluator.Response post(String path, String jsonBody) {
            JSONObject body = new JSONObject(jsonBody);
            if (!injector.active) {
                Map<String, String> row = new LinkedHashMap<>();
                row.put("accountId", body.getString("accountId"));
                row.put("documentNumber", body.getString("documentNumber"));
                rows.add(row);
            }
            // The SUT lies under the fault: identical success envelope either way.
            return new ContractEvaluator.Response(200,
                    "{\"status\":1,\"msg\":\"Create Success\",\"data\":null}");
        }

        @Override
        public ContractEvaluator.Response get(String path) {
            if (failStateGet) {
                return new ContractEvaluator.Response(503, "{\"error\":\"down\"}");
            }
            if (!READBACK.equals(path)) {
                return new ContractEvaluator.Response(404, "");
            }
            StringBuilder data = new StringBuilder("[");
            for (int i = 0; i < rows.size(); i++) {
                if (i > 0) {
                    data.append(',');
                }
                data.append(new JSONObject(rows.get(i)));
            }
            data.append(']');
            return new ContractEvaluator.Response(200, "{\"status\":1,\"data\":" + data + "}");
        }
    }

    private static final class RecordingInjector implements FaultInjector {
        final List<String> ops = new ArrayList<>();
        boolean active = false;
        boolean failOnFinalClear = false;

        @Override
        public void inject(FaultTarget target) {
            ops.add("inject:" + target.deployment);
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

    private RecordingInjector injector;
    private FakeSut sut;
    private AssertionBindings.Bindings bindings;
    private TargetTripleRegistry.Registry registry;

    @Before
    public void setUp() {
        injector = new RecordingInjector();
        sut = new FakeSut(injector);
        String bindingsYaml = "sut: test\n"
                + "frozen_set: \"test @ deadbeef\"\n"
                + "endpoints:\n"
                + "  - endpoint: \"" + ENDPOINT + "\"\n"
                + "    triple: adminbasic-contacts-create\n"
                + "    body_template: '{\"name\":\"n\",\"accountId\":\"${uuid:accountId}\",\"documentType\":1,\"documentNumber\":\"${fresh:documentNumber}\",\"phoneNumber\":\"1\"}'\n"
                + "    clauses:\n"
                + "      - cite: \"success envelope\"\n"
                + "        checks:\n"
                + "          - primitive: HTTP_STATUS\n"
                + "            expect: \"200\"\n"
                + "          - primitive: ENVELOPE_STATUS\n"
                + "            expect: \"1\"\n"
                + "          - primitive: ENVELOPE_DATA\n"
                + "            expect: \"null\"\n"
                + "          - primitive: MSG_CONTAINS\n"
                + "            expect: \"Create Success\"\n"
                + "      - cite: \"contact in list with submitted fields\"\n"
                + "        checks:\n"
                + "          - primitive: STATE_GET\n"
                + "            path: \"" + READBACK + "\"\n"
                + "            expect: \"contains-submitted-fields\"\n"
                + "            fields: \"accountId,documentNumber\"\n"
                + "      - cite: \"duplicate rejection\"\n"
                + "        checks:\n"
                + "          - primitive: NOT_CHECKABLE\n"
                + "            reason: \"out of calibration scope\"\n";
        bindings = AssertionBindings.parse(
                new ByteArrayInputStream(bindingsYaml.getBytes(StandardCharsets.UTF_8)), "inline");
        registry = TargetTripleRegistry.parse(
                ComparatorRunnerTest.class.getResourceAsStream(
                        "/My-Example/trainticket/target-triples.yaml"), "shipped");
    }

    @Test
    public void calibrationHappyPath_responseClausesPassStateClauseFlags() throws Exception {
        Path report = Files.createTempDirectory("cmp").resolve("report.json");
        List<ComparatorRunner.EndpointResult> results = new ComparatorRunner(
                bindings, registry, injector, sut).run("run-1", report);

        assertEquals(1, results.size());
        ComparatorRunner.EndpointResult r = results.get(0);
        assertEquals("flag", r.verdict);
        assertTrue("control must pass every clause", !r.control.flagged);
        assertTrue("fault run must be flagged", r.fault.flagged);
        // Pre-stated calibration outcome (design §4): the masked response
        // clauses PASS under the fault; ONLY the state clause fails.
        for (ContractEvaluator.CheckOutcome co : r.fault.outcomes) {
            if (co.check.primitive == AssertionBindings.Primitive.STATE_GET) {
                assertEquals("FAIL", co.result);
            } else if (co.check.primitive == AssertionBindings.Primitive.NOT_CHECKABLE) {
                assertEquals("NOT_CHECKABLE", co.result);
            } else {
                assertEquals(co.check.primitive + " must PASS under the masking fault",
                        "PASS", co.result);
            }
        }
        assertEquals(java.util.Arrays.asList(
                "clear:ts-admin-basic-info-service",
                "inject:ts-admin-basic-info-service",
                "clear:ts-admin-basic-info-service"), injector.ops);
        assertTrue(Files.isRegularFile(report));
    }

    @Test
    public void controlViolation_isInfraFailure_skipsFaultLeg() throws Exception {
        sut.failStateGet = true;
        Path report = Files.createTempDirectory("cmp").resolve("report.json");
        List<ComparatorRunner.EndpointResult> results = new ComparatorRunner(
                bindings, registry, injector, sut).run("run-2", report);

        assertEquals(1, results.size());
        assertEquals("comparator-infra-failure", results.get(0).verdict);
        assertTrue("fault leg must be skipped", results.get(0).fault == null);
        assertTrue("no inject may happen on an infra-failed endpoint",
                injector.ops.stream().noneMatch(op -> op.startsWith("inject:")));
    }

    @Test
    public void clearFailure_reportWrittenBeforeThrow() throws Exception {
        injector.failOnFinalClear = true;
        Path report = Files.createTempDirectory("cmp").resolve("report.json");
        try {
            new ComparatorRunner(bindings, registry, injector, sut).run("run-3", report);
            fail("expected the clear failure to propagate");
        } catch (FaultInjector.FaultInjectionException expected) {
            // expected — loud F2 signal
        }
        assertTrue("report must be persisted BEFORE the throw", Files.isRegularFile(report));
        JSONObject parsed = new JSONObject(new String(Files.readAllBytes(report),
                StandardCharsets.UTF_8));
        assertTrue(parsed.getBoolean("f2ClearFailure"));
        assertEquals(1, parsed.getJSONArray("f2FailedFlags").length());
        assertEquals("flag", parsed.getJSONArray("endpoints").getJSONObject(0)
                .getString("verdict"));
    }

    @Test
    public void reportShape_carriesPerClauseOutcomes() throws Exception {
        Path report = Files.createTempDirectory("cmp").resolve("report.json");
        new ComparatorRunner(bindings, registry, injector, sut).run("run-4", report);
        JSONObject parsed = new JSONObject(new String(Files.readAllBytes(report),
                StandardCharsets.UTF_8));
        assertEquals("run-4", parsed.getString("runId"));
        JSONObject ep = parsed.getJSONArray("endpoints").getJSONObject(0);
        assertEquals(ENDPOINT, ep.getString("endpoint"));
        JSONArray controlChecks = ep.getJSONObject("control").getJSONArray("checks");
        assertEquals(6, controlChecks.length());
        assertNotNull(controlChecks.getJSONObject(0).getString("result"));
        assertTrue("clean report must not carry the f2 marker", !parsed.has("f2ClearFailure"));
    }

    @Test
    public void substitution_freshPerCall() {
        String template = "{\"a\":\"${uuid:a}\",\"b\":\"${fresh:b}\"}";
        JSONObject first = new JSONObject(ComparatorRunner.substitute(template));
        JSONObject second = new JSONObject(ComparatorRunner.substitute(template));
        assertTrue(!first.getString("a").equals(second.getString("a")));
        assertTrue(!first.getString("b").equals(second.getString("b")));
        assertTrue(first.getString("b").startsWith("cmp-"));
    }

    @Test
    public void unknownTripleReference_failsLoudly() throws Exception {
        String bad = "sut: test\nfrozen_set: \"x @ y\"\nendpoints:\n"
                + "  - endpoint: \"POST /x\"\n"
                + "    triple: no-such-triple\n"
                + "    body_template: '{}'\n"
                + "    clauses:\n"
                + "      - cite: \"c\"\n"
                + "        checks:\n"
                + "          - primitive: HTTP_STATUS\n"
                + "            expect: \"200\"\n";
        AssertionBindings.Bindings b = AssertionBindings.parse(
                new ByteArrayInputStream(bad.getBytes(StandardCharsets.UTF_8)), "inline");
        try {
            new ComparatorRunner(b, registry, injector, sut)
                    .run("run-5", Files.createTempDirectory("cmp").resolve("r.json"));
            fail("expected unknown triple reference to fail");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("no-such-triple"));
        }
    }
}
