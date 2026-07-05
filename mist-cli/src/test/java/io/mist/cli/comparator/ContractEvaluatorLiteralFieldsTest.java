package io.mist.cli.comparator;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Pins the P2 liveness primitive (STATE_GET expect {@code contains-literal-fields}): membership
 * by LITERAL name=value constraints over a custom-keyed collection. This is what lets the
 * comparator express broker+app liveness ({@code /health} entry service=shipping-rabbitmq has
 * status=OK) — the clause that makes the shipping natural stratum a diagnosis gap (comparator
 * CATCHES the outage) while the constructed stratum (green /health) stays a clean MIST win.
 */
public class ContractEvaluatorLiteralFieldsTest {

    private static final String HEALTHY = "{\"health\":["
            + "{\"service\":\"shipping-rabbitmq\",\"status\":\"OK\",\"date\":\"d\"},"
            + "{\"service\":\"shipping\",\"status\":\"OK\",\"date\":\"d\"}]}";
    private static final String BROKER_ERR = "{\"health\":["
            + "{\"service\":\"shipping-rabbitmq\",\"status\":\"err\",\"date\":\"d\"},"
            + "{\"service\":\"shipping\",\"status\":\"OK\",\"date\":\"d\"}]}";

    @Before
    public void fastRetry() {
        // Bound the presence-expect retry so the FAIL (broker-err) case resolves quickly.
        System.setProperty("mst.comparator.state.retry.cap.ms", "50");
        System.setProperty("mst.comparator.state.poll.ms", "5");
    }

    @After
    public void cleanup() {
        System.clearProperty("mst.comparator.state.retry.cap.ms");
        System.clearProperty("mst.comparator.state.poll.ms");
    }

    // ---- the matcher directly ----

    @Test
    public void literalFields_pass_whenEntryMatches() {
        assertTrue(ContractEvaluator.containsLiteralFields(HEALTHY,
                Arrays.asList("service=shipping-rabbitmq", "status=OK"), "health"));
    }

    @Test
    public void literalFields_fail_whenBrokerErr() {
        assertFalse(ContractEvaluator.containsLiteralFields(BROKER_ERR,
                Arrays.asList("service=shipping-rabbitmq", "status=OK"), "health"));
    }

    @Test
    public void literalFields_fail_whenCollectionAbsentOrUnparseable() {
        assertFalse(ContractEvaluator.containsLiteralFields("{\"other\":[]}",
                Arrays.asList("service=shipping-rabbitmq", "status=OK"), "health"));
        assertFalse(ContractEvaluator.containsLiteralFields("not json",
                Arrays.asList("status=OK"), "health"));
    }

    @Test
    public void literalFields_bareArray_whenNoCollectionKey() {
        assertTrue(ContractEvaluator.containsLiteralFields(
                "[{\"service\":\"x\",\"status\":\"OK\"}]", Arrays.asList("status=OK"), null));
    }

    // ---- the full STATE_GET flow via evaluate ----

    @Test
    public void evaluate_livenessClause_passesHealthy_failsBrokerErr() {
        AssertionBindings.BoundEndpoint ep = contractWithLiveness();
        String submitted = "{\"id\":\"i\",\"name\":\"n\"}";
        ContractEvaluator.Response ack = new ContractEvaluator.Response(201, submitted);

        ContractEvaluator.EndpointOutcome healthy =
                ContractEvaluator.evaluate(ep, "control", submitted, ack, healthClient(200, HEALTHY));
        assertFalse("green /health -> liveness PASSes -> comparator does not flag", healthy.flagged);

        ContractEvaluator.EndpointOutcome err =
                ContractEvaluator.evaluate(ep, "fault", submitted, ack, healthClient(200, BROKER_ERR));
        assertTrue("broker-err /health -> liveness FAILs -> comparator flags (CATCHES the outage)",
                err.flagged);
    }

    @Test
    public void evaluate_liveness_transportFail_whenHealthUnreachable() {
        AssertionBindings.BoundEndpoint ep = contractWithLiveness();
        String submitted = "{\"id\":\"i\",\"name\":\"n\"}";
        ContractEvaluator.EndpointOutcome o = ContractEvaluator.evaluate(ep, "fault", submitted,
                new ContractEvaluator.Response(201, submitted), healthClient(503, "down"));
        assertTrue(o.flagged);
        // a non-2xx /health FAILs as a TRANSPORT failure (never scored as a real detection).
        assertTrue("non-2xx /health must be marked transportFailure",
                o.outcomes.stream().anyMatch(c -> c.transportFailure));
    }

    private static AssertionBindings.BoundEndpoint contractWithLiveness() {
        String doc = "sut: sockshop\nfrozen_set: \"t\"\nendpoints:\n"
                + "  - endpoint: \"POST /shipping\"\n"
                + "    triple: shipping-enqueue\n"
                + "    body_template: '{\"id\":\"${uuid:id}\",\"name\":\"${uuid:name}\"}'\n"
                + "    clauses:\n"
                + "      - cite: \"201\"\n"
                + "        checks:\n"
                + "          - primitive: HTTP_STATUS\n"
                + "            expect: \"201\"\n"
                + "      - cite: \"broker+app liveness\"\n"
                + "        checks:\n"
                + "          - primitive: STATE_GET\n"
                + "            path: \"/health\"\n"
                + "            expect: \"contains-literal-fields\"\n"
                + "            collection_key: \"health\"\n"
                + "            fields: \"service=shipping-rabbitmq,status=OK\"\n";
        return AssertionBindings.parse(
                new ByteArrayInputStream(doc.getBytes(StandardCharsets.UTF_8)), "t").endpoints.get(0);
    }

    private static ContractEvaluator.SutClient healthClient(int status, String body) {
        return new ContractEvaluator.SutClient() {
            @Override
            public ContractEvaluator.Response post(String path, String jsonBody) {
                throw new AssertionError("SutClient.post must not be called");
            }

            @Override
            public ContractEvaluator.Response get(String path) {
                assertEquals("/health", path);
                return new ContractEvaluator.Response(status, body);
            }
        };
    }
}
