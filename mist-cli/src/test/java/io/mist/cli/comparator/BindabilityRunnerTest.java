package io.mist.cli.comparator;

import org.json.JSONObject;
import org.junit.After;
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
import static org.junit.Assert.assertTrue;

/**
 * Pins the control-only bindability classification against a stateful fake SUT:
 * a benign write always persists (no injector), so a correctly-bound STATE clause
 * PASSes (BINDS); a wrapper read-back shape that hides the submitted fields FAILs
 * structurally (UNBINDABLE); a NOT_CHECKABLE-only clause is residue; a
 * response-only endpoint is excluded; a bad write envelope or a non-2xx state GET
 * is INFRA_FAILURE. Also pins the report's bindability-fraction arithmetic.
 */
public class BindabilityRunnerTest {

    private static final String EP = "POST /api/v1/x/create";
    private static final String READBACK = "/api/v1/x/list";

    /** Benign writes always persist (no fault); get returns the collection. */
    private static final class FakeSut implements ContractEvaluator.SutClient {
        final List<Map<String, String>> rows = new ArrayList<>();
        int writeStatus = 200;
        String writeEnvelope = "{\"status\":1,\"msg\":\"Create Success\",\"data\":null}";
        int getStatus = 200;
        /** "collection" = {status,data:[rows]}; "wrapper" = rows nested (no top-level fields); "empty". */
        String getShape = "collection";

        @Override
        public ContractEvaluator.Response post(String path, String jsonBody) {
            JSONObject b = new JSONObject(jsonBody);
            Map<String, String> row = new LinkedHashMap<>();
            for (String k : b.keySet()) {
                row.put(k, String.valueOf(b.get(k)));
            }
            rows.add(row);
            return new ContractEvaluator.Response(writeStatus, writeEnvelope);
        }

        @Override
        public ContractEvaluator.Response get(String path) {
            if (getStatus / 100 != 2) {
                return new ContractEvaluator.Response(getStatus, "{\"error\":\"down\"}");
            }
            if ("empty".equals(getShape)) {
                return new ContractEvaluator.Response(200, "{\"status\":1,\"data\":[]}");
            }
            StringBuilder data = new StringBuilder("[");
            for (int i = 0; i < rows.size(); i++) {
                if (i > 0) {
                    data.append(',');
                }
                if ("wrapper".equals(getShape)) {
                    data.append("{\"wrap\":").append(new JSONObject(rows.get(i))).append('}');
                } else {
                    data.append(new JSONObject(rows.get(i)));
                }
            }
            data.append(']');
            return new ContractEvaluator.Response(200, "{\"status\":1,\"data\":" + data + "}");
        }
    }

    private FakeSut sut;
    private String prevCap;
    private String prevPoll;

    @Before
    public void setUp() {
        prevCap = System.getProperty("mst.comparator.state.retry.cap.ms");
        prevPoll = System.getProperty("mst.comparator.state.poll.ms");
        // Keep the absence path fast (default cap is 10s).
        System.setProperty("mst.comparator.state.retry.cap.ms", "30");
        System.setProperty("mst.comparator.state.poll.ms", "1");
        sut = new FakeSut();
    }

    @After
    public void tearDown() {
        restore("mst.comparator.state.retry.cap.ms", prevCap);
        restore("mst.comparator.state.poll.ms", prevPoll);
    }

    private static void restore(String k, String v) {
        if (v == null) {
            System.clearProperty(k);
        } else {
            System.setProperty(k, v);
        }
    }

    private static AssertionBindings.Bindings parse(String yaml) {
        return AssertionBindings.parse(
                new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)), "inline");
    }

    private static String header() {
        return "sut: test\nfrozen_set: \"t @ x\"\nendpoints:\n";
    }

    private static String membershipEndpoint() {
        return "  - endpoint: \"" + EP + "\"\n"
                + "    triple: t\n"
                + "    body_template: '{\"name\":\"${fresh:name}\"}'\n"
                + "    clauses:\n"
                + "      - cite: \"envelope\"\n"
                + "        checks:\n"
                + "          - primitive: HTTP_STATUS\n            expect: \"200\"\n"
                + "          - primitive: ENVELOPE_STATUS\n            expect: \"1\"\n"
                + "      - cite: \"membership\"\n"
                + "        checks:\n"
                + "          - primitive: STATE_GET\n"
                + "            path: \"" + READBACK + "\"\n"
                + "            expect: \"contains-submitted-fields\"\n"
                + "            fields: \"name\"\n";
    }

    private BindabilityRunner.EndpointResult only(AssertionBindings.Bindings b) {
        return new BindabilityRunner(b, sut).classify(b.endpoints.get(0));
    }

    @Test
    public void membershipPresentOnBenignWrite_binds() {
        BindabilityRunner.EndpointResult r = only(parse(header() + membershipEndpoint()));
        assertEquals(BindabilityRunner.Verdict.BINDS, r.verdict);
    }

    @Test
    public void readbackWrapperHidesSubmittedFields_unbindable() {
        sut.getShape = "wrapper"; // rows nested under {wrap:{...}} → no top-level 'name'
        BindabilityRunner.EndpointResult r = only(parse(header() + membershipEndpoint()));
        assertEquals(BindabilityRunner.Verdict.UNBINDABLE, r.verdict);
    }

    @Test
    public void notCheckableOnlyStateClause_isResidue() {
        String yaml = header()
                + "  - endpoint: \"" + EP + "\"\n"
                + "    triple: t\n"
                + "    body_template: '{\"name\":\"${fresh:name}\"}'\n"
                + "    clauses:\n"
                + "      - cite: \"envelope\"\n"
                + "        checks:\n"
                + "          - primitive: HTTP_STATUS\n            expect: \"200\"\n"
                + "      - cite: \"state\"\n"
                + "        checks:\n"
                + "          - primitive: NOT_CHECKABLE\n"
                + "            reason: \"single-object read; absence vacuous\"\n";
        assertEquals(BindabilityRunner.Verdict.NOT_CHECKABLE, only(parse(yaml)).verdict);
    }

    @Test
    public void responseOnlyEndpoint_isExcluded() {
        String yaml = header()
                + "  - endpoint: \"" + EP + "\"\n"
                + "    triple: t\n"
                + "    body_template: '{\"name\":\"n\"}'\n"
                + "    clauses:\n"
                + "      - cite: \"envelope only (token issuance)\"\n"
                + "        checks:\n"
                + "          - primitive: HTTP_STATUS\n            expect: \"200\"\n"
                + "          - primitive: ENVELOPE_STATUS\n            expect: \"1\"\n";
        assertEquals(BindabilityRunner.Verdict.NO_STATE_CLAUSE, only(parse(yaml)).verdict);
    }

    @Test
    public void badWriteEnvelope_isInfraFailure() {
        sut.writeEnvelope = "{\"status\":0,\"msg\":\"boom\",\"data\":null}"; // ENVELOPE_STATUS 1 fails
        BindabilityRunner.EndpointResult r = only(parse(header() + membershipEndpoint()));
        assertEquals(BindabilityRunner.Verdict.INFRA_FAILURE, r.verdict);
        assertTrue(r.detail.contains("response contract"));
    }

    @Test
    public void stateReadbackTransportFailure_isInfraFailure() {
        sut.getStatus = 503; // the membership read-back is non-2xx
        BindabilityRunner.EndpointResult r = only(parse(header() + membershipEndpoint()));
        assertEquals(BindabilityRunner.Verdict.INFRA_FAILURE, r.verdict);
        assertTrue(r.detail.contains("transport"));
    }

    @Test
    public void partialBinds_whenMembershipPassesAndAnotherClauseIsNotCheckable() {
        String yaml = header()
                + "  - endpoint: \"" + EP + "\"\n"
                + "    triple: t\n"
                + "    body_template: '{\"name\":\"${fresh:name}\"}'\n"
                + "    clauses:\n"
                + "      - cite: \"envelope\"\n"
                + "        checks:\n"
                + "          - primitive: HTTP_STATUS\n            expect: \"200\"\n"
                + "      - cite: \"membership binds\"\n"
                + "        checks:\n"
                + "          - primitive: STATE_GET\n"
                + "            path: \"" + READBACK + "\"\n"
                + "            expect: \"contains-submitted-fields\"\n"
                + "            fields: \"name\"\n"
                + "      - cite: \"derived value inexpressible\"\n"
                + "        checks:\n"
                + "          - primitive: NOT_CHECKABLE\n"
                + "            reason: \"derived price\"\n";
        assertEquals(BindabilityRunner.Verdict.BINDS_PARTIAL, only(parse(yaml)).verdict);
    }

    @Test
    public void reportFraction_countsBindsOverResidueExcludingNoState() throws Exception {
        // one BINDS (membership) + one NOT_CHECKABLE-only endpoint → 1/2 bind.
        String yaml = header() + membershipEndpoint()
                + "  - endpoint: \"POST /api/v1/y/create\"\n"
                + "    triple: t\n"
                + "    body_template: '{\"name\":\"${fresh:name}\"}'\n"
                + "    clauses:\n"
                + "      - cite: \"envelope\"\n"
                + "        checks:\n"
                + "          - primitive: HTTP_STATUS\n            expect: \"200\"\n"
                + "      - cite: \"state\"\n"
                + "        checks:\n"
                + "          - primitive: NOT_CHECKABLE\n"
                + "            reason: \"nested key shape\"\n";
        Path report = Files.createTempDirectory("bind").resolve("report.json");
        List<BindabilityRunner.EndpointResult> results =
                new BindabilityRunner(parse(yaml), sut).run("run-bind", report);
        assertEquals(2, results.size());
        assertTrue(Files.isRegularFile(report));
        JSONObject parsed = new JSONObject(new String(Files.readAllBytes(report), StandardCharsets.UTF_8));
        JSONObject frac = parsed.getJSONObject("bindabilityFraction");
        assertEquals(1, frac.getInt("bindsInclPartial"));
        assertEquals(1, frac.getInt("residue"));
        assertEquals(2, frac.getInt("denominator"));
        assertEquals("run-bind", parsed.getString("runId"));
    }
}
