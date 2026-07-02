package io.mist.cli.comparator;

import io.mist.cli.fault.FaultInjector;
import io.mist.cli.fault.TargetTripleRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * G2 comparator calibration runner (comparator-runner-design.md §3): for each
 * bound endpoint, clear the fault flag, run the CONTROL write and evaluate the
 * frozen contract (every evaluated check must PASS — a failing control clause
 * is a comparator-infra-failure, the bindings are suspect, and the fault leg
 * is skipped), then inject, run the FAULT write, evaluate, and clear in a
 * finally. The report is written BEFORE any clear-failure throw (the F2
 * evidence-sink lesson from the pairing executor).
 */
public final class ComparatorRunner {

    private static final Logger logger = LogManager.getLogger(ComparatorRunner.class);

    private static final Pattern TOKEN = Pattern.compile("\\$\\{(uuid|fresh):([A-Za-z0-9_]+)}");

    /** Per-endpoint calibration result. */
    public static final class EndpointResult {
        public final String endpoint;
        /** flag | no_flag | comparator-infra-failure | not-run */
        public final String verdict;
        public final String detail;
        public final ContractEvaluator.EndpointOutcome control;
        public final ContractEvaluator.EndpointOutcome fault;
        /** Injected fault manifest (review F6b): "deployment/-Dproperty" or null. */
        public String faultManifest;

        EndpointResult(String endpoint, String verdict, String detail,
                       ContractEvaluator.EndpointOutcome control,
                       ContractEvaluator.EndpointOutcome fault) {
            this.endpoint = endpoint;
            this.verdict = verdict;
            this.detail = detail;
            this.control = control;
            this.fault = fault;
        }
    }

    private final AssertionBindings.Bindings bindings;
    private final TargetTripleRegistry.Registry registry;
    private final FaultInjector injector;
    private final ContractEvaluator.SutClient client;

    public ComparatorRunner(AssertionBindings.Bindings bindings,
                            TargetTripleRegistry.Registry registry,
                            FaultInjector injector,
                            ContractEvaluator.SutClient client) {
        this.bindings = bindings;
        this.registry = registry;
        this.injector = injector;
        this.client = client;
    }

    /**
     * Runs the calibration over every bound endpoint and writes the report.
     * The report is ALWAYS persisted — before a clear-failure exception
     * propagates and even when an endpoint dies mid-loop (review F3/F4).
     */
    public List<EndpointResult> run(String runId, Path reportFile) throws IOException {
        List<EndpointResult> results = new ArrayList<>();
        List<FaultInjector.FaultTarget> clearFailures = new ArrayList<>();

        for (AssertionBindings.BoundEndpoint endpoint : bindings.endpoints) {
            if (!clearFailures.isEmpty()) {
                // Review F3: a failed clear means the SUT may still be
                // faulted — later verdicts would be contaminated. Stop
                // verdict work; surface the skip.
                results.add(new EndpointResult(endpoint.endpoint, "not-run",
                        "skipped: a prior fault-flag clear failed — SUT state suspect", null, null));
                continue;
            }
            try {
                results.add(runEndpoint(endpoint, clearFailures));
            } catch (RuntimeException e) {
                // Review F4: a mid-loop failure (binding error at run time,
                // inject rollout timeout, transport exception) must not
                // destroy the run's evidence — it is comparator-infra-failure
                // for THIS endpoint, and the loop continues.
                logger.error("Comparator[{}]: endpoint failed ({}) — comparator-infra-failure",
                        endpoint.endpoint, e.toString());
                results.add(new EndpointResult(endpoint.endpoint, "comparator-infra-failure",
                        "endpoint run failed: " + e, null, null));
            }
        }

        // F2 lesson: persist the evidence BEFORE the loud throw.
        writeReport(reportFile, results, runId, clearFailures);
        if (!clearFailures.isEmpty()) {
            throw new FaultInjector.FaultInjectionException(
                    "fault flag may still be active on: " + clearFailures
                            + " — verify/clear manually before any further run");
        }
        return results;
    }

    private EndpointResult runEndpoint(AssertionBindings.BoundEndpoint endpoint,
                                       List<FaultInjector.FaultTarget> clearFailures) {
        TargetTripleRegistry.Triple triple = tripleFor(endpoint.triple);
        if (triple.faultFlag == null) {
            return new EndpointResult(endpoint.endpoint, "comparator-infra-failure",
                    "triple '" + triple.name + "' has no fault_flag — cannot calibrate", null, null);
        }
        injector.clear(triple.faultFlag);

        String controlBody = substitute(endpoint.bodyTemplate);
        ContractEvaluator.Response controlResp = client.post(path(endpoint.endpoint), controlBody);
        ContractEvaluator.EndpointOutcome control = ContractEvaluator.evaluate(
                endpoint, "control", controlBody, controlResp, client);
        if (control.flagged) {
            // Design §3: a failing control clause means the bindings (or the
            // deploy) are suspect — never a detection; skip the fault leg so
            // no flag is left at risk for a worthless run.
            logger.error("Comparator[{}]: control run violated the contract — infra-failure",
                    endpoint.endpoint);
            return new EndpointResult(endpoint.endpoint, "comparator-infra-failure",
                    "control run violated the frozen contract — bindings/deploy suspect",
                    control, null);
        }

        ContractEvaluator.EndpointOutcome fault = null;
        try {
            injector.inject(triple.faultFlag);
            String faultBody = substitute(endpoint.bodyTemplate);
            ContractEvaluator.Response faultResp = client.post(path(endpoint.endpoint), faultBody);
            fault = ContractEvaluator.evaluate(endpoint, "fault", faultBody, faultResp, client);
        } finally {
            try {
                injector.clear(triple.faultFlag);
            } catch (RuntimeException e) {
                logger.error("Comparator: FAILED to clear {} — the SUT may still be faulted: {}",
                        triple.faultFlag, e.toString());
                clearFailures.add(triple.faultFlag);
            }
        }
        EndpointResult result;
        if (fault != null && fault.flagged && onlyTransportFailures(fault)) {
            // Review F2: an infra blip on the fault leg's state GET is not
            // bug evidence — mirror MIST's read-back-error category instead
            // of inflating the comparator's recall.
            result = new EndpointResult(endpoint.endpoint, "comparator-infra-failure",
                    "fault-leg failures are transport-only (state GET non-2xx) — not a detection",
                    control, fault);
        } else {
            result = new EndpointResult(endpoint.endpoint,
                    fault != null && fault.flagged ? "flag" : "no_flag",
                    fault != null && fault.flagged
                            ? "the frozen contract failed under the injected fault"
                            : "the frozen contract held under the injected fault",
                    control, fault);
        }
        result.faultManifest = triple.faultFlag.deployment + "/-D" + triple.faultFlag.property
                + "=true";
        return result;
    }

    private static boolean onlyTransportFailures(ContractEvaluator.EndpointOutcome outcome) {
        boolean sawFailure = false;
        for (ContractEvaluator.CheckOutcome co : outcome.outcomes) {
            if ("FAIL".equals(co.result)) {
                sawFailure = true;
                if (!co.transportFailure) {
                    return false;
                }
            }
        }
        return sawFailure;
    }

    private TargetTripleRegistry.Triple tripleFor(String name) {
        for (TargetTripleRegistry.Triple t : registry.triples) {
            if (t.name.equals(name)) {
                return t;
            }
        }
        throw new IllegalArgumentException("ComparatorRunner: bindings reference unknown triple '"
                + name + "' — the bindings and target-triples.yaml are out of sync");
    }

    /** "METHOD /path" → "/path" (POSTs only at calibration). */
    static String path(String endpoint) {
        if (!endpoint.startsWith("POST ")) {
            throw new IllegalArgumentException("ComparatorRunner: calibration supports POST endpoints, got: "
                    + endpoint);
        }
        return endpoint.substring(5).trim();
    }

    /** ${uuid:F} → fresh UUID; ${fresh:F} → fresh short string. */
    static String substitute(String template) {
        Matcher m = TOKEN.matcher(template);
        StringBuffer out = new StringBuffer();
        while (m.find()) {
            String value = "uuid".equals(m.group(1))
                    ? UUID.randomUUID().toString()
                    : "cmp-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            m.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** Machine-readable calibration report (per-clause outcomes + verdicts). */
    public static void writeReport(Path file, List<EndpointResult> results, String runId,
                                   List<FaultInjector.FaultTarget> clearFailures) throws IOException {
        JSONObject report = new JSONObject();
        report.put("runId", runId);
        report.put("comparator", "blind-authored per-endpoint assertion oracle (Filibuster-style); "
                + "frozen contracts evaluated verbatim via the committed bindings");
        report.put("generatedAtEpochMs", System.currentTimeMillis());
        if (!clearFailures.isEmpty()) {
            JSONArray failed = new JSONArray();
            for (FaultInjector.FaultTarget flag : clearFailures) {
                failed.put(String.valueOf(flag));
            }
            report.put("f2ClearFailure", true);
            report.put("f2FailedFlags", failed);
        }
        JSONArray endpoints = new JSONArray();
        for (EndpointResult r : results) {
            JSONObject ep = new JSONObject();
            ep.put("endpoint", r.endpoint);
            ep.put("verdict", r.verdict);
            ep.put("detail", r.detail);
            ep.put("faultManifest", r.faultManifest == null ? JSONObject.NULL : r.faultManifest);
            ep.put("control", outcomeJson(r.control));
            ep.put("fault", outcomeJson(r.fault));
            endpoints.put(ep);
        }
        report.put("endpoints", endpoints);
        Files.createDirectories(file.getParent());
        Files.write(file, report.toString(2).getBytes(StandardCharsets.UTF_8));
        logger.info("Comparator report written to {}", file);
    }

    private static Object outcomeJson(ContractEvaluator.EndpointOutcome outcome) {
        if (outcome == null) {
            return JSONObject.NULL;
        }
        JSONObject json = new JSONObject();
        json.put("runLabel", outcome.runLabel);
        json.put("flagged", outcome.flagged);
        json.put("writeHttpStatus", outcome.writeHttpStatus);
        json.put("submittedBody", truncate(outcome.submittedBody));
        json.put("writeBody", truncate(outcome.writeBody));
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

    private static Object truncate(String body) {
        if (body == null) {
            return JSONObject.NULL;
        }
        return body.length() <= 8_000 ? body : body.substring(0, 8_000) + "…[truncated]";
    }
}
