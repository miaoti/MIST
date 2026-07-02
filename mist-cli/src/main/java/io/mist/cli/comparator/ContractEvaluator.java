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

        CheckOutcome(AssertionBindings.Check check, String result, String detail) {
            this.check = check;
            this.result = result;
            this.detail = detail;
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
                Response readback = client.get(check.path);
                if (readback.status / 100 != 2) {
                    // A failing state GET cannot verify the contract either
                    // way; recorded as FAIL with the transport detail — the
                    // control-run all-pass gate turns this into
                    // comparator-infra-failure (design §3), never a detection.
                    return new CheckOutcome(check, "FAIL",
                            "state GET " + check.path + " returned HTTP " + readback.status);
                }
                boolean present = containsSubmittedFields(readback.body, submitted, check.fields);
                if ("contains-submitted-fields".equals(check.expect)) {
                    return present
                            ? new CheckOutcome(check, "PASS", "submitted key present on " + check.path)
                            : new CheckOutcome(check, "FAIL", "submitted key ABSENT from " + check.path
                                    + " (fields " + check.fields + ")");
                }
                return present
                        ? new CheckOutcome(check, "FAIL", "submitted key still present on " + check.path)
                        : new CheckOutcome(check, "PASS", "submitted key absent from " + check.path);
            }
            default:
                throw new IllegalStateException("unhandled primitive " + check.primitive);
        }
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
