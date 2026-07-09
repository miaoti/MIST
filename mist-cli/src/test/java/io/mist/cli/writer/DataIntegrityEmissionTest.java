package io.mist.cli.writer;

import io.mist.cli.fault.TargetTripleRegistry;
import io.mist.core.spec.Operation;
import io.mist.core.testcase.MultiServiceTestCase;
import io.mist.core.testcase.TestCase;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Locks the B2 codegen layer: a write step matching a registered target
 * triple gets the DataIntegrityRuntime.beforeWrite (body freshening, emitted
 * between the body literal and req.body) and afterWrite (ack + read-back,
 * fed the step's own traceparent id) calls; without registered triples the
 * generated source carries no trace of the feature (flag-off byte-identity).
 */
public class DataIntegrityEmissionTest {

    private static final String ROUTE_PATH = "/api/v1/adminrouteservice/adminroute";

    private static List<TargetTripleRegistry.Triple> shippedTriples() {
        return TargetTripleRegistry.parse(
                DataIntegrityEmissionTest.class.getResourceAsStream(
                        "/My-Example/trainticket/target-triples.yaml"), "shipped").triples;
    }

    private static MultiServiceTestCase routePostCase(String body) {
        MultiServiceTestCase tc = new MultiServiceTestCase("RouteDemo");
        tc.setScenarioName("RouteScenario");
        tc.setFaulty(false);
        Operation op = new Operation();
        op.setMethod("post");
        op.setTestPath(ROUTE_PATH);
        MultiServiceTestCase.StepCall step = new MultiServiceTestCase.StepCall(
                "ts-admin-route-service", op, ROUTE_PATH, null, null, null, body, 200, null);
        step.setTopLevelRoot(true);
        tc.addStepCall(step);
        return tc;
    }

    private static final class Written {
        final String src;
        final MultiServiceRESTAssuredWriter writer;

        Written(String src, MultiServiceRESTAssuredWriter writer) {
            this.src = src;
            this.writer = writer;
        }
    }

    private static Written writeCase(List<TargetTripleRegistry.Triple> triples, MultiServiceTestCase tc)
            throws IOException {
        Path out = Files.createTempDirectory("di-emission-test");
        MultiServiceRESTAssuredWriter writer = new MultiServiceRESTAssuredWriter(
                null, null, out.toString(), "RouteDemo", "io.mist.generated", "http://localhost", false);
        writer.setAllureReport(true);
        if (triples != null) {
            writer.setDataIntegrityTriples(triples);
        }
        writer.write(Collections.<TestCase>singletonList(tc));
        try (Stream<Path> files = Files.walk(out)) {
            String src = files.filter(p -> p.toString().endsWith(".java"))
                    .map(p -> {
                        try {
                            return Files.readString(p);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .collect(Collectors.joining("\n"));
            return new Written(src, writer);
        }
    }

    @Test
    public void matchingStep_getsBothHooksAndIsRecorded() throws IOException {
        Written written = writeCase(shippedTriples(),
                routePostCase("{\"startStation\":\"a\",\"endStation\":\"b\"}"));
        MultiServiceRESTAssuredWriter writer = written.writer;
        String src = written.src;

        // The writer numbers steps itself, so assertions are step-index-agnostic.
        // G3 rider: both hooks now carry the generation-time correlator
        // "<class>.<method>#<stepIdx>" whose index must match the step's own
        // number; the class-qualified form (a dot before the #) is pinned so a
        // regression to the bare "<method>#<idx>" (review A-F2) is caught.
        assertTrue("beforeWrite must rewrite the body literal and carry the class-qualified correlator",
                src.matches("(?s).*requestBody(\\d+) = io\\.mist\\.cli\\.fault\\.DataIntegrityRuntime"
                        + "\\.beforeWrite\\(\"POST " + ROUTE_PATH + "\", \"[^\"]*\\.[^\"]*#\\1\", requestBody\\1\\);.*"));
        assertTrue("beforeWrite must run before req.body picks the body up",
                src.indexOf("DataIntegrityRuntime.beforeWrite") < src.indexOf("req = req.body(requestBody"));
        assertTrue("afterWrite must receive the class-qualified correlator, status, body and the traceparent id",
                src.matches("(?s).*io\\.mist\\.cli\\.fault\\.DataIntegrityRuntime\\.afterWrite\\(\"POST "
                        + ROUTE_PATH + "\", \"[^\"]*\\.[^\"]*#(\\d+)\", actualStatusCode\\1, "
                        + "stepResponse\\1\\.getBody\\(\\)\\.asString\\(\\), __mstTraceId\\1\\);.*"));
        assertEquals("the hooked method must be recorded for the pairing filter",
                1, writer.getDataIntegrityMethods().size());

        // UX W1 (REVIEW-UX-RECONCILIATION U1/U5): a positive hooked step also
        // carries the observe-mode verdict check, emitted AFTER afterWrite so
        // the record exists when it runs. Inert at runtime in paired legs.
        assertTrue("positive hooked step must emit the observe check with the correlator shape",
                src.matches("(?s).*io\\.mist\\.cli\\.fault\\.DataIntegrityObserveCheck"
                        + "\\.afterStep\\(\"[^\"]*\\.[^\"]*#\\d+\"\\);.*"));
        assertTrue("the check must run after afterWrite",
                src.indexOf("DataIntegrityRuntime.afterWrite") < src.indexOf("DataIntegrityObserveCheck.afterStep"));
    }

    @Test
    public void matchingStep_faultyVariant_getsHooksButNoObserveCheck() throws IOException {
        // Negative variants keep their hooks (paired record stream unchanged)
        // but never run the observe check — a designed-invalid write carries
        // no durable-write claim (UX W1, review-A negative-variant finding).
        MultiServiceTestCase tc = routePostCase("{\"startStation\":\"a\",\"endStation\":\"b\"}");
        tc.setFaulty(true);
        Written written = writeCase(shippedTriples(), tc);
        assertTrue("hooks stay on the faulty variant",
                written.src.contains("DataIntegrityRuntime.afterWrite"));
        assertFalse("no observe check on a faulty variant",
                written.src.contains("DataIntegrityObserveCheck"));
    }

    @Test
    public void bodylessSuppliedStep_withGenerationTimePathKey_getsSuppliedHookAndCheck() throws IOException {
        // UX W6 (REVIEW-UX-RECONCILIATION U6): a bodyless write matching a
        // supplied-isolation triple whose single key resolves to a concrete
        // path segment at generation time IS hooked — beforeWriteSupplied +
        // afterWrite + the observe check.
        String yaml = "triples:\n"
                + "  - name: cancel-order\n"
                + "    write_endpoint: \"POST /api/v1/cancelservice/cancel/{orderId}\"\n"
                + "    dependency: ts-inside-payment-service\n"
                + "    readback_endpoint: \"GET /api/v1/orderservice/order\"\n"
                + "    isolation_key: [orderId]\n"
                + "    isolation_strategy: supplied\n";
        List<TargetTripleRegistry.Triple> triples = TargetTripleRegistry.parse(
                new java.io.ByteArrayInputStream(yaml.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                "inline").triples;

        MultiServiceTestCase tc = new MultiServiceTestCase("CancelDemo");
        tc.setScenarioName("CancelScenario");
        tc.setFaulty(false);
        Operation op = new Operation();
        op.setMethod("post");
        op.setTestPath("/api/v1/cancelservice/cancel/{orderId}");
        MultiServiceTestCase.StepCall step = new MultiServiceTestCase.StepCall(
                "ts-cancel-service", op, "/api/v1/cancelservice/cancel/ord-123",
                null, null, null, null, 200, null);
        step.setTopLevelRoot(true);
        tc.addStepCall(step);

        Written written = writeCase(triples, tc);
        assertTrue("bodyless supplied step must emit beforeWriteSupplied with the concrete key",
                written.src.contains("DataIntegrityRuntime.beforeWriteSupplied(\"POST /api/v1/cancelservice/cancel/ord-123\"")
                        && written.src.contains("\"orderId\", \"ord-123\""));
        assertTrue("afterWrite must bind on the supplied step",
                written.src.contains("DataIntegrityRuntime.afterWrite(\"POST /api/v1/cancelservice/cancel/ord-123\""));
        assertTrue("the observe check must bind on the supplied step",
                written.src.contains("DataIntegrityObserveCheck.afterStep("));
        assertEquals(1, written.writer.getDataIntegrityMethods().size());
    }

    @Test
    public void nonMatchingStep_emitsNothing() throws IOException {
        MultiServiceTestCase tc = new MultiServiceTestCase("OtherDemo");
        tc.setScenarioName("OtherScenario");
        tc.setFaulty(false);
        Operation op = new Operation();
        op.setMethod("post");
        op.setTestPath("/api/v1/other");
        MultiServiceTestCase.StepCall step = new MultiServiceTestCase.StepCall(
                "ts-other", op, "/api/v1/other", null, null, null, "{\"a\":1}", 200, null);
        step.setTopLevelRoot(true);
        tc.addStepCall(step);

        Written written = writeCase(shippedTriples(), tc);
        assertFalse(written.src.contains("DataIntegrityRuntime"));
        assertTrue(written.writer.getDataIntegrityMethods().isEmpty());
    }

    @Test
    public void noTriplesRegistered_sourceIsHookFree() throws IOException {
        Written written = writeCase(null, routePostCase("{\"startStation\":\"a\"}"));
        assertFalse("flag-off output must carry no trace of the feature",
                written.src.contains("DataIntegrityRuntime"));
        assertTrue(written.writer.getDataIntegrityMethods().isEmpty());
    }

    @Test
    public void bodylessMatchingStep_isLeftUnhooked() throws IOException {
        Written written = writeCase(shippedTriples(), routePostCase(null));
        assertFalse("a body-less write cannot carry an isolation key",
                written.src.contains("DataIntegrityRuntime"));
        assertTrue(written.writer.getDataIntegrityMethods().isEmpty());
    }
}
