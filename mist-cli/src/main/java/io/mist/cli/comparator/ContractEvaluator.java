package io.mist.cli.comparator;

import io.mist.cli.fault.DataIntegrityRuntime;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Evaluates the FROZEN executable bindings ({@link AssertionBindings}) against
 * one write execution: the write's HTTP response plus follow-up state GETs
 * through the {@link SutClient} seam. This is the G2 comparator's oracle —
 * fixed per-endpoint contracts, no differential, no isolation freshening
 * beyond the harness-level unique key, no quiescence gate, no traces (that is
 * the point of the comparison; comparator-runner-design.md §1).
 */
public final class ContractEvaluator {

    /** Minimal HTTP seam (mirrors the fault package's Http seam pattern). */
    public interface SutClient {
        Response post(String path, String jsonBody);

        Response get(String path);
    }

    public static final class Response {
        public final int status;
        public final String body;

        public Response(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }

    /** Outcome of one bound check: PASS / FAIL / NOT_CHECKABLE. */
    public static final class CheckOutcome {
        public final AssertionBindings.Check check;
        public final String result;
        public final String detail;
        /** The frozen clause this check binds (review F6b: adjudication traceability). */
        public String cite;
        /**
         * Review F2: true when a FAIL is a TRANSPORT failure (non-2xx state
         * GET), not a contract violation — the runner reclassifies fault-leg
         * verdicts whose only failures are transport as
         * comparator-infra-failure, mirroring MIST's read-back-error
         * category so an infra blip is never scored as a detection.
         */
        public final boolean transportFailure;

        CheckOutcome(AssertionBindings.Check check, String result, String detail) {
            this(check, result, detail, false);
        }

        CheckOutcome(AssertionBindings.Check check, String result, String detail,
                     boolean transportFailure) {
            this.check = check;
            this.result = result;
            this.detail = detail;
            this.transportFailure = transportFailure;
        }
    }

    /** Outcome of one write evaluation over every bound clause. */
    public static final class EndpointOutcome {
        public final String endpoint;
        public final String runLabel;
        public final String submittedBody;
        public final int writeHttpStatus;
        public final String writeBody;
        public final List<CheckOutcome> outcomes;
        /** true iff >=1 evaluated (non-NOT_CHECKABLE) check FAILed. */
        public final boolean flagged;

        EndpointOutcome(String endpoint, String runLabel, String submittedBody, int writeHttpStatus,
                        String writeBody, List<CheckOutcome> outcomes, boolean flagged) {
            this.endpoint = endpoint;
            this.runLabel = runLabel;
            this.submittedBody = submittedBody;
            this.writeHttpStatus = writeHttpStatus;
            this.writeBody = writeBody;
            this.outcomes = Collections.unmodifiableList(new ArrayList<>(outcomes));
            this.flagged = flagged;
        }
    }

    private ContractEvaluator() {
        // static evaluation only
    }

    /**
     * Evaluates every bound clause of {@code endpoint} for one executed write.
     * The submitted body supplies the field values STATE_GET membership checks
     * compare against.
     */
    public static EndpointOutcome evaluate(AssertionBindings.BoundEndpoint endpoint, String runLabel,
                                           String submittedBody, Response writeResponse,
                                           SutClient client) {
        JSONObject submitted = new JSONObject(submittedBody);
        List<CheckOutcome> outcomes = new ArrayList<>();
        boolean flagged = false;
        for (AssertionBindings.Clause clause : endpoint.clauses) {
            for (AssertionBindings.Check check : clause.checks) {
                CheckOutcome outcome = evaluateCheck(check, submitted, writeResponse, client);
                outcome.cite = clause.cite;
                outcomes.add(outcome);
                if ("FAIL".equals(outcome.result)) {
                    flagged = true;
                }
            }
        }
        return new EndpointOutcome(endpoint.endpoint, runLabel, submittedBody,
                writeResponse.status, writeResponse.body, outcomes, flagged);
    }

    private static CheckOutcome evaluateCheck(AssertionBindings.Check check, JSONObject submitted,
                                              Response write, SutClient client) {
        switch (check.primitive) {
            case NOT_CHECKABLE:
                return new CheckOutcome(check, "NOT_CHECKABLE", check.reason);
            case HTTP_STATUS: {
                for (String s : check.expect.split(",")) {
                    if (write.status == Integer.parseInt(s.trim())) {
                        return new CheckOutcome(check, "PASS", "http " + write.status);
                    }
                }
                return new CheckOutcome(check, "FAIL",
                        "http " + write.status + " not in [" + check.expect + "]");
            }
            case ENVELOPE_STATUS: {
                Integer status = envelopeInt(write.body, "status");
                int expected = Integer.parseInt(check.expect.trim());
                return status != null && status == expected
                        ? new CheckOutcome(check, "PASS", "envelope status " + status)
                        : new CheckOutcome(check, "FAIL", "envelope status " + status
                                + " != " + expected);
            }
            case ENVELOPE_DATA: {
                boolean dataNull = envelopeDataIsNull(write.body);
                boolean expectNull = "null".equals(check.expect);
                return dataNull == expectNull
                        ? new CheckOutcome(check, "PASS", "data " + (dataNull ? "null" : "non-null"))
                        : new CheckOutcome(check, "FAIL", "data " + (dataNull ? "null" : "non-null")
                                + ", expected " + check.expect);
            }
            case MSG_CONTAINS: {
                String msg = envelopeString(write.body, "msg");
                return msg != null && msg.contains(check.expect)
                        ? new CheckOutcome(check, "PASS", "msg contains expected text")
                        : new CheckOutcome(check, "FAIL", "msg '" + msg + "' lacks '"
                                + check.expect + "'");
            }
            case STATE_GET: {
                String path = resolvePath(check.path, submitted);
                boolean presenceExpect = "contains-submitted-fields".equals(check.expect)
                        || "entity-matches-submitted-fields".equals(check.expect)
                        || "contains-literal-fields".equals(check.expect);
                // Design amendment A3 (review wave): presence checks retry up
                // to the SAME pre-registered cap MIST's read-back uses, so a
                // benign convergence delay (~1-2s on this SUT) neither aborts
                // the control leg nor lets a delay-type fault masquerade as a
                // loss. Harness-level read timing only — still no trace gate,
                // no differential. Absence-expect stays single-shot.
                long capMs = presenceExpect
                        ? Long.getLong("mst.comparator.state.retry.cap.ms", 10_000) : 0;
                long pollMs = Math.max(1, Long.getLong("mst.comparator.state.poll.ms", 500));
                long start = System.nanoTime();
                Response readback;
                boolean ok;
                boolean satisfied = false;
                int polls = 0;
                while (true) {
                    readback = client.get(path);
                    polls++;
                    ok = readback.status / 100 == 2;
                    if (ok && presenceExpect) {
                        satisfied = presenceSatisfied(check, readback.body, submitted);
                        if (satisfied) {
                            break;
                        }
                    }
                    long elapsedMs = (System.nanoTime() - start) / 1_000_000;
                    if (elapsedMs >= capMs) {
                        break;
                    }
                    try {
                        Thread.sleep(pollMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("interrupted during state-GET retry", e);
                    }
                }
                if (!ok) {
                    // The DECISIVE read failed at the transport level: FAIL
                    // marked as a TRANSPORT failure. The control gate maps it
                    // to comparator-infra-failure; on the fault leg the runner
                    // reclassifies transport-only failures the same way
                    // (review F2) — never a detection.
                    return new CheckOutcome(check, "FAIL",
                            "state GET " + path + " returned HTTP " + readback.status
                                    + " on the decisive read (" + polls + " poll(s))", true);
                }
                if (presenceExpect) {
                    // Name the evidence accurately: a contains-literal-fields clause checks LITERAL
                    // name=value constraints (e.g. liveness), not the submitted body — the FAIL
                    // detail is the human-facing evidence for a diagnosis-gap CAUGHT.
                    String kind = "contains-literal-fields".equals(check.expect)
                            ? "literal fields" : "submitted state";
                    return satisfied
                            ? new CheckOutcome(check, "PASS", kind + " present on " + path
                                    + " (" + polls + " poll(s))")
                            : new CheckOutcome(check, "FAIL", kind + " ABSENT from " + path
                                    + " at the cap (fields " + check.fields + ", " + polls
                                    + " poll(s))");
                }
                boolean present = containsSubmittedFields(readback.body, submitted, check.fields);
                return present
                        ? new CheckOutcome(check, "FAIL", "submitted key still present on " + path)
                        : new CheckOutcome(check, "PASS", "submitted key absent from " + path);
            }
            default:
                throw new IllegalStateException("unhandled primitive " + check.primitive);
        }
    }

    /**
     * ${field:NAME} tokens in a STATE_GET path resolve to the submitted
     * body's field value (e.g. the per-entity read
     * {@code /routes/${field:id}} — bindings amendment A2).
     */
    static String resolvePath(String path, JSONObject submitted) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\\$\\{field:([A-Za-z0-9_]+)}").matcher(path);
        StringBuffer out = new StringBuffer();
        while (m.find()) {
            String field = m.group(1);
            if (!submitted.has(field)) {
                throw new IllegalArgumentException("ContractEvaluator: STATE_GET path references "
                        + "field '" + field + "' absent from the submitted body — binding error");
            }
            m.appendReplacement(out,
                    java.util.regex.Matcher.quoteReplacement(String.valueOf(submitted.get(field))));
        }
        m.appendTail(out);
        return out.toString();
    }

    /**
     * Per-entity read (amendment A2): the envelope's {@code data} is a single
     * OBJECT whose fields must all match the submitted values.
     */
    static boolean entityMatchesSubmittedFields(String body, JSONObject submitted,
                                                List<String> fields) {
        JSONObject entity;
        try {
            JSONObject envelope = new JSONObject(body.trim());
            if (!envelope.has("data") || envelope.isNull("data")) {
                return false;
            }
            entity = envelope.getJSONObject("data");
        } catch (RuntimeException e) {
            return false;
        }
        for (String field : fields) {
            if (!submitted.has(field)) {
                throw new IllegalArgumentException("ContractEvaluator: STATE_GET field '" + field
                        + "' is not present in the submitted body — binding error");
            }
            if (!entity.has(field) || !String.valueOf(entity.get(field))
                    .equals(String.valueOf(submitted.get(field)))) {
                return false;
            }
        }
        return true;
    }

    /** Membership by the submitted fields' values over the collection body. */
    static boolean containsSubmittedFields(String collectionBody, JSONObject submitted,
                                           List<String> fields) {
        Map<String, String> key = new LinkedHashMap<>();
        for (String field : fields) {
            if (!submitted.has(field)) {
                throw new IllegalArgumentException("ContractEvaluator: STATE_GET field '" + field
                        + "' is not present in the submitted body — binding error");
            }
            key.put(field, String.valueOf(submitted.get(field)));
        }
        for (Object item : DataIntegrityRuntime.extractItems(collectionBody)) {
            if (!(item instanceof JSONObject)) {
                continue;
            }
            JSONObject obj = (JSONObject) item;
            boolean all = true;
            for (Map.Entry<String, String> e : key.entrySet()) {
                if (!obj.has(e.getKey())
                        || !String.valueOf(obj.get(e.getKey())).equals(e.getValue())) {
                    all = false;
                    break;
                }
            }
            if (all) {
                return true;
            }
        }
        return false;
    }

    /** Dispatches a presence-expect STATE_GET to its matcher. */
    private static boolean presenceSatisfied(AssertionBindings.Check check, String body,
                                             JSONObject submitted) {
        switch (check.expect) {
            case "entity-matches-submitted-fields":
                return entityMatchesSubmittedFields(body, submitted, check.fields);
            case "contains-literal-fields":
                return containsLiteralFields(body, check.fields, check.collectionKey);
            default:
                return containsSubmittedFields(body, submitted, check.fields);
        }
    }

    /**
     * P2 (user-chosen design fork): membership by LITERAL {@code name=value} constraints over a
     * collection, unwrapping a custom {@code collectionKey} (e.g. {@code health} for a
     * {@code {health:[..]}} body the default array / {@code {data}} / {@code _embedded} shapes do
     * not cover). PASS iff some item matches ALL constraints — so a broker-down {@code /health},
     * whose shipping-rabbitmq entry flips to status {@code "err"}, has no item matching
     * {@code service=shipping-rabbitmq,status=OK} and the liveness clause FAILs.
     */
    static boolean containsLiteralFields(String collectionBody, List<String> constraints,
                                         String collectionKey) {
        // Defense-in-depth: an empty constraint set would match the first item VACUOUSLY (a silent
        // false-PASS). The loader already rejects a contains-literal-fields clause with zero parsed
        // constraints, so this is a belt-and-braces guard, not the primary check.
        if (constraints.isEmpty()) {
            return false;
        }
        for (Object item : literalItems(collectionBody, collectionKey)) {
            if (!(item instanceof JSONObject)) {
                continue;
            }
            JSONObject obj = (JSONObject) item;
            boolean all = true;
            for (String c : constraints) {
                int eq = c.indexOf('=');
                String name = c.substring(0, eq).trim();
                String value = c.substring(eq + 1).trim();
                if (!obj.has(name) || !String.valueOf(obj.get(name)).equals(value)) {
                    all = false;
                    break;
                }
            }
            if (all) {
                return true;
            }
        }
        return false;
    }

    /** Items for a literal-fields check: {@code body.<collectionKey>} when set, else the default shapes. */
    private static List<Object> literalItems(String body, String collectionKey) {
        if (collectionKey == null || collectionKey.trim().isEmpty()) {
            return DataIntegrityRuntime.extractItems(body);
        }
        List<Object> items = new ArrayList<>();
        try {
            org.json.JSONArray arr = new JSONObject(body.trim()).optJSONArray(collectionKey.trim());
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    items.add(arr.get(i));
                }
            }
        } catch (RuntimeException e) {
            // unparseable / absent collection -> no items -> not satisfied (a malformed or
            // broker-down /health reads as "liveness not shown", the correct direction).
        }
        return items;
    }

    private static Integer envelopeInt(String body, String field) {
        try {
            JSONObject obj = new JSONObject(body.trim());
            return obj.has(field) ? obj.getInt(field) : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String envelopeString(String body, String field) {
        try {
            JSONObject obj = new JSONObject(body.trim());
            return obj.has(field) ? obj.getString(field) : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean envelopeDataIsNull(String body) {
        try {
            JSONObject obj = new JSONObject(body.trim());
            return !obj.has("data") || obj.isNull("data");
        } catch (RuntimeException e) {
            return true;
        }
    }
}
