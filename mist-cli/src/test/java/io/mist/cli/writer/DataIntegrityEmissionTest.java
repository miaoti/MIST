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
        // "<method>#<stepIdx>" whose index must match the step's own number.
        assertTrue("beforeWrite must rewrite the body literal and carry the correlator",
                src.matches("(?s).*requestBody(\\d+) = io\\.mist\\.cli\\.fault\\.DataIntegrityRuntime"
                        + "\\.beforeWrite\\(\"POST " + ROUTE_PATH + "\", \"[^\"]*#\\1\", requestBody\\1\\);.*"));
        assertTrue("beforeWrite must run before req.body picks the body up",
                src.indexOf("DataIntegrityRuntime.beforeWrite") < src.indexOf("req = req.body(requestBody"));
        assertTrue("afterWrite must receive the correlator, status, body and the step's traceparent id",
                src.matches("(?s).*io\\.mist\\.cli\\.fault\\.DataIntegrityRuntime\\.afterWrite\\(\"POST "
                        + ROUTE_PATH + "\", \"[^\"]*#(\\d+)\", actualStatusCode\\1, "
                        + "stepResponse\\1\\.getBody\\(\\)\\.asString\\(\\), __mstTraceId\\1\\);.*"));
        assertEquals("the hooked method must be recorded for the pairing filter",
                1, writer.getDataIntegrityMethods().size());
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
