package io.mist.cli.comparator;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Control-only BINDABILITY surveyor for the Rider-2 breadth demonstration
 * (breadth-executability-finding.md). Unlike {@link ComparatorRunner} — which
 * injects a per-endpoint {@code fault_flag} to prove the frozen contract CATCHES
 * a lost write — this runner performs NO injection and needs no fault flag. It
 * runs one benign CONTROL write per endpoint and answers a single question:
 * does the frozen STATE postcondition clause EVALUATE on the live SUT?
 *
 * <ul>
 *   <li>{@code BINDS} — the state clause bound and PASSed on the benign write.</li>
 *   <li>{@code BINDS_PARTIAL} — a catching state observable binds; a further
 *       observable on the same endpoint is NOT_CHECKABLE.</li>
 *   <li>{@code NOT_CHECKABLE} — the only state clause is marked NOT_CHECKABLE
 *       (the survey's structural residue).</li>
 *   <li>{@code UNBINDABLE} — the clause is expressible but the submitted state is
 *       ABSENT on a healthy benign write (the read-back shape does not surface it)
 *       — a live contradiction of an analytical BINDS, surfaced loudly.</li>
 *   <li>{@code INFRA_FAILURE} — the control write violated the response contract,
 *       or the state read-back failed at the transport level: a deploy/binding
 *       problem, not a bindability verdict.</li>
 *   <li>{@code NO_STATE_CLAUSE} — response-only endpoint (excluded from the
 *       denominator, mirroring the survey's exclusion of auth login).</li>
 * </ul>
 *
 * This turns the survey's ANALYTICAL bindability fraction into an EMPIRICAL one.
 * It reuses the reviewed {@link ContractEvaluator} verbatim (no differential, no
 * isolation beyond the harness-level unique key) and {@link ComparatorRunner}'s
 * token substitution and path parsing.
 */
public final class BindabilityRunner {

    private static final Logger logger = LogManager.getLogger(BindabilityRunner.class);

    /** Per-endpoint bindability verdict. */
    public enum Verdict { BINDS, BINDS_PARTIAL, NOT_CHECKABLE, UNBINDABLE, INFRA_FAILURE, NO_STATE_CLAUSE }

    public static final class EndpointResult {
        public final String endpoint;
        public final Verdict verdict;
        public final String detail;
        public final ContractEvaluator.EndpointOutcome control;

        EndpointResult(String endpoint, Verdict verdict, String detail,
                       ContractEvaluator.EndpointOutcome control) {
            this.endpoint = endpoint;
            this.verdict = verdict;
            this.detail = detail;
            this.control = control;
        }
    }

    private final AssertionBindings.Bindings bindings;
    private final ContractEvaluator.SutClient client;

    public BindabilityRunner(AssertionBindings.Bindings bindings, ContractEvaluator.SutClient client) {
        this.bindings = bindings;
        this.client = client;
    }

    /**
     * Classifies every bound endpoint and writes the report. The report is
     * always persisted; a per-endpoint runtime failure is recorded as
     * INFRA_FAILURE and the loop continues (mirrors ComparatorRunner F4).
     */
    public List<EndpointResult> run(String runId, Path reportFile) throws IOException {
        List<EndpointResult> results = new ArrayList<>();
        for (AssertionBindings.BoundEndpoint endpoint : bindings.endpoints) {
            try {
                results.add(classify(endpoint));
            } catch (RuntimeException e) {
                logger.error("Bindability[{}]: endpoint failed ({}) — infra-failure",
                        endpoint.endpoint, e.toString());
                results.add(new EndpointResult(endpoint.endpoint, Verdict.INFRA_FAILURE,
                        "endpoint run failed: " + e, null));
            }
        }
        writeReport(reportFile, results, runId);
        return results;
    }

    EndpointResult classify(AssertionBindings.BoundEndpoint endpoint) {
        String body = ComparatorRunner.substitute(endpoint.bodyTemplate);
        ContractEvaluator.Response resp = client.post(ComparatorRunner.path(endpoint.endpoint), body);
        ContractEvaluator.EndpointOutcome control =
                ContractEvaluator.evaluate(endpoint, "bindability", body, resp, client);

        // The write itself must succeed (its response clauses PASS) before a
        // STATE verdict is meaningful — a failed control write is an infra/deploy
        // problem for this endpoint, not a bindability fact (cf. ComparatorRunner
        // §3 "a failing control clause is comparator-infra-failure").
        boolean sawResponseCheck = false;
        boolean responseFailed = false;
        int stateGetPass = 0;
        int stateGetFailStructural = 0;
        int stateGetFailTransport = 0;
        int stateGetTotal = 0;
        int notCheckable = 0;
        for (ContractEvaluator.CheckOutcome co : control.outcomes) {
            switch (co.check.primitive) {
                case HTTP_STATUS:
                case ENVELOPE_STATUS:
                case ENVELOPE_DATA:
                case MSG_CONTAINS:
                    sawResponseCheck = true;
                    if ("FAIL".equals(co.result)) {
                        responseFailed = true;
                    }
                    break;
                case STATE_GET:
                    stateGetTotal++;
                    if ("PASS".equals(co.result)) {
                        stateGetPass++;
                    } else if ("FAIL".equals(co.result)) {
                        if (co.transportFailure) {
                            stateGetFailTransport++;
                        } else {
                            stateGetFailStructural++;
                        }
                    }
                    break;
                case NOT_CHECKABLE:
                    notCheckable++;
                    break;
                default:
                    throw new IllegalStateException("unhandled primitive " + co.check.primitive);
            }
        }

        if (sawResponseCheck && responseFailed) {
            return new EndpointResult(endpoint.endpoint, Verdict.INFRA_FAILURE,
                    "control write violated the response contract — deploy/binding suspect", control);
        }
        if (stateGetTotal == 0 && notCheckable == 0) {
            return new EndpointResult(endpoint.endpoint, Verdict.NO_STATE_CLAUSE,
                    "no STATE postcondition clause (response-only) — excluded from the denominator", control);
        }
        if (stateGetTotal == 0) {
            return new EndpointResult(endpoint.endpoint, Verdict.NOT_CHECKABLE,
                    "only NOT_CHECKABLE state clause(s) — structural residue", control);
        }
        if (stateGetPass == 0 && stateGetFailTransport > 0 && stateGetFailStructural == 0) {
            return new EndpointResult(endpoint.endpoint, Verdict.INFRA_FAILURE,
                    "state read-back transport failure (non-2xx) — not a bindability verdict", control);
        }
        if (stateGetFailStructural > 0) {
            return new EndpointResult(endpoint.endpoint, Verdict.UNBINDABLE,
                    "state clause expressible but submitted state ABSENT on a benign write — "
                            + "read-back shape does not surface it (analytical BINDS contradicted live)", control);
        }
        if (stateGetPass > 0 && notCheckable > 0) {
            return new EndpointResult(endpoint.endpoint, Verdict.BINDS_PARTIAL,
                    "catching state observable binds; a further observable is NOT_CHECKABLE", control);
        }
        return new EndpointResult(endpoint.endpoint, Verdict.BINDS,
                "the frozen state postcondition binds and PASSed on a benign write", control);
    }

    /** Machine-readable bindability report (per-endpoint verdicts + the fraction). */
    public static void writeReport(Path file, List<EndpointResult> results, String runId) throws IOException {
        Map<Verdict, Integer> tally = new EnumMap<>(Verdict.class);
        for (Verdict v : Verdict.values()) {
            tally.put(v, 0);
        }
        for (EndpointResult r : results) {
            tally.put(r.verdict, tally.get(r.verdict) + 1);
        }
        int binds = tally.get(Verdict.BINDS) + tally.get(Verdict.BINDS_PARTIAL);
        int residue = tally.get(Verdict.NOT_CHECKABLE) + tally.get(Verdict.UNBINDABLE);
        int denominator = binds + residue; // excludes NO_STATE_CLAUSE and INFRA_FAILURE

        JSONObject report = new JSONObject();
        report.put("runId", runId);
        report.put("runner", "control-only bindability surveyor (no injection, no fault_flag) — "
                + "does each frozen STATE clause EVALUATE on a benign write? (breadth-executability-finding.md)");
        report.put("generatedAtEpochMs", System.currentTimeMillis());

        JSONObject tallyJson = new JSONObject();
        for (Verdict v : Verdict.values()) {
            tallyJson.put(v.name(), tally.get(v));
        }
        report.put("tally", tallyJson);

        JSONObject fraction = new JSONObject();
        fraction.put("bindsInclPartial", binds);
        fraction.put("residue", residue);
        fraction.put("denominator", denominator);
        fraction.put("note", "denominator excludes NO_STATE_CLAUSE (no state postcondition) and "
                + "INFRA_FAILURE (deploy/transport, not a bindability fact); residue = NOT_CHECKABLE + UNBINDABLE");
        report.put("bindabilityFraction", fraction);

        JSONArray endpoints = new JSONArray();
        for (EndpointResult r : results) {
            JSONObject ep = new JSONObject();
            ep.put("endpoint", r.endpoint);
            ep.put("verdict", r.verdict.name());
            ep.put("detail", r.detail);
            ep.put("control", outcomeJson(r.control));
            endpoints.put(ep);
        }
        report.put("endpoints", endpoints);

        Files.createDirectories(file.getParent());
        Files.write(file, report.toString(2).getBytes(StandardCharsets.UTF_8));
        logger.info("Bindability report written to {} — {}/{} bind (incl. partial), {} residue",
                file, binds, denominator, residue);
    }

    private static Object outcomeJson(ContractEvaluator.EndpointOutcome outcome) {
        if (outcome == null) {
            return JSONObject.NULL;
        }
        JSONObject json = new JSONObject();
        json.put("writeHttpStatus", outcome.writeHttpStatus);
        JSONArray checks = new JSONArray();
        for (ContractEvaluator.CheckOutcome co : outcome.outcomes) {
            JSONObject check = new JSONObject();
            check.put("primitive", co.check.primitive.name());
            check.put("expect", co.check.expect == null ? JSONObject.NULL : co.check.expect);
            check.put("path", co.check.path == null ? JSONObject.NULL : co.check.path);
            check.put("cite", co.cite == null ? JSONObject.NULL : co.cite);
            check.put("result", co.result);
            check.put("detail", co.detail);
            check.put("transportFailure", co.transportFailure);
            checks.put(check);
        }
        json.put("checks", checks);
        return json;
    }
}
