package io.mist.cli.g3;

import io.mist.cli.comparator.AssertionBindings;
import io.mist.cli.comparator.ContractEvaluator;
import io.mist.cli.fault.DataIntegrityRuntime;
import io.mist.cli.fault.PairedFaultExecutor;
import io.mist.cli.fault.TargetTripleRegistry;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Pins the {@link ShippingEnqueueHeadToHead} orchestration against fakes (no live SUT or
 * broker): the reviewed value-delta oracle + the frozen response comparator, driven over a
 * control leg (shipping-task depth moves 1&rarr;2, X present) and a faulted leg (depth stays
 * 2&rarr;2, acked-but-lost, X absent) &rarr; MIST FIREs while a response-only comparator
 * misses BOTH legs. The fake read-back models monotonic queue depth (queue-master scaffolded
 * to 0): within one {@code runStratum} the read order is control(baseline&times;2, poll) then
 * fault(baseline&times;2, poll&hellip;), so depth = 1 for the first two reads and 2 after —
 * exactly control 1&rarr;2 (present) and fault 2&rarr;2 (absent).
 */
public class ShippingEnqueueHeadToHeadTest {

    @Before
    public void fastTiming() {
        // Bound the faulted-leg absence wait so the test is quick (real defaults are seconds).
        System.setProperty(DataIntegrityRuntime.POLL_MS_PROPERTY, "5");
        System.setProperty(DataIntegrityRuntime.TIMEOUT_MS_PROPERTY, "60");
        System.setProperty(DataIntegrityRuntime.TRACE_SETTLE_MS_PROPERTY, "0");
        System.setProperty("mst.test.parallelism", "1");
    }

    @After
    public void cleanup() {
        DataIntegrityRuntime.installHttpOverride(null);
        System.clearProperty(DataIntegrityRuntime.POLL_MS_PROPERTY);
        System.clearProperty(DataIntegrityRuntime.TIMEOUT_MS_PROPERTY);
        System.clearProperty(DataIntegrityRuntime.TRACE_SETTLE_MS_PROPERTY);
    }

    // ---- fakes ------------------------------------------------------------------

    /** Bare /api/queues array; shipping-task depth = 1 for the first two reads, 2 after. */
    private static final class DepthHttp implements DataIntegrityRuntime.Http {
        final AtomicInteger calls = new AtomicInteger();

        @Override
        public DataIntegrityRuntime.HttpResponse getSut(String path) {
            int depth = calls.incrementAndGet() <= 2 ? 1 : 2;
            return new DataIntegrityRuntime.HttpResponse(200,
                    "[{\"name\":\"shipping-task\",\"messages\":" + depth + "}]");
        }

        @Override
        public DataIntegrityRuntime.HttpResponse getAbsolute(String url) {
            throw new AssertionError("getAbsolute must not be called (traceId is null)");
        }
    }

    private static final class FakeStimulus implements ShippingEnqueueHeadToHead.Stimulus {
        @Override
        public ShippingEnqueueHeadToHead.Shipment postShipping() {
            String id = java.util.UUID.randomUUID().toString();
            return new ShippingEnqueueHeadToHead.Shipment(id, "n-" + id, 201,
                    "{\"id\":\"" + id + "\",\"name\":\"n-" + id + "\"}");
        }
    }

    private static final class RecordingFault implements ShippingEnqueueHeadToHead.Fault {
        final List<String> events = new ArrayList<>();

        @Override
        public void inject() {
            events.add("inject");
        }

        @Override
        public void clear() {
            events.add("clear");
        }
    }

    /** These test contracts carry no STATE_GET clause, so the client must never be hit. */
    private static final class NoStateClient implements ContractEvaluator.SutClient {
        @Override
        public ContractEvaluator.Response post(String path, String jsonBody) {
            throw new AssertionError("SutClient.post must not be called");
        }

        @Override
        public ContractEvaluator.Response get(String path) {
            throw new AssertionError("SutClient.get must not be called");
        }
    }

    private static TargetTripleRegistry.Triple loadTriple() throws Exception {
        String yaml = "cluster:\n"
                + "  context: test\n"
                + "  namespace: sock-shop\n"
                + "  rollout_timeout_s: 60\n"
                + "triples:\n"
                + "  - name: shipping-enqueue\n"
                + "    write_endpoint: \"POST /shipping\"\n"
                + "    dependency: rabbitmq\n"
                + "    readback_endpoint: \"GET /api/queues/%2f\"\n"
                + "    isolation_key: [name]\n"
                + "    isolation_strategy: supplied\n"
                + "    readback_mode: value-delta\n"
                + "    value_probe:\n"
                + "      match_field: name\n"
                + "      value_field: messages\n";
        Path f = Files.createTempFile("shipping-triple", ".yaml");
        Files.write(f, yaml.getBytes(StandardCharsets.UTF_8));
        f.toFile().deleteOnExit();
        return TargetTripleRegistry.load(f).triples.get(0);
    }

    /** A minimal response contract: HTTP_STATUS + a NOT_CHECKABLE enqueue-landing clause. */
    private static AssertionBindings.BoundEndpoint contract(String expectStatus) throws Exception {
        String yaml = "sut: sockshop\n"
                + "frozen_set: \"test\"\n"
                + "endpoints:\n"
                + "  - endpoint: \"POST /shipping\"\n"
                + "    triple: shipping-enqueue\n"
                + "    body_template: '{\"id\":\"${uuid:id}\",\"name\":\"${uuid:name}\"}'\n"
                + "    clauses:\n"
                + "      - cite: \"201 CREATED acknowledgement\"\n"
                + "        checks:\n"
                + "          - primitive: HTTP_STATUS\n"
                + "            expect: \"" + expectStatus + "\"\n"
                + "      - cite: \"enqueue landing is not response-observable\"\n"
                + "        checks:\n"
                + "          - primitive: NOT_CHECKABLE\n"
                + "            reason: \"the shipping-task enqueue landing is invisible in the POST response\"\n";
        AssertionBindings.Bindings b = AssertionBindings.parse(
                new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)), "test-contract");
        return ShippingEnqueueHeadToHead.shippingEndpoint(b);
    }

    // ---- tests ------------------------------------------------------------------

    @Test
    public void mistFires_whileResponseComparatorMissesBothLegs() throws Exception {
        DataIntegrityRuntime.installHttpOverride(new DepthHttp());
        RecordingFault fault = new RecordingFault();
        ShippingEnqueueHeadToHead harness =
                new ShippingEnqueueHeadToHead(new FakeStimulus(), new NoStateClient());

        ShippingEnqueueHeadToHead.StratumResult r =
                harness.runStratum("constructed", loadTriple(), contract("201"), fault);

        // MIST: acknowledged-but-lost -> FIRE, via a unique correlator join (claim-eligible).
        assertEquals(1, r.mist.size());
        assertEquals(PairedFaultExecutor.PairVerdict.FIRE, r.mist.get(0).pureDifferential);
        assertEquals("correlator", r.mist.get(0).joinMode);
        assertTrue(r.mist.get(0).correlatorUnique);
        // Comparator: a response-only contract passes BOTH legs (the loss is invisible to it).
        assertFalse("control leg must pass the response contract", r.controlComparator.flagged);
        assertFalse("fault leg must ALSO pass (comparator misses the lost enqueue)",
                r.faultComparator.flagged);
        // Fault lifecycle: clear (hygiene) -> inject -> clear (restore).
        assertEquals(Arrays.asList("clear", "inject", "clear"), fault.events);
    }

    @Test
    public void comparatorFlagsWhenResponseViolatesContract() throws Exception {
        // Same run, but a contract expecting 200: the 201 ack FAILs HTTP_STATUS on BOTH legs,
        // proving the comparator wiring detects violations (it is not vacuously passing).
        DataIntegrityRuntime.installHttpOverride(new DepthHttp());
        ShippingEnqueueHeadToHead harness =
                new ShippingEnqueueHeadToHead(new FakeStimulus(), new NoStateClient());

        ShippingEnqueueHeadToHead.StratumResult r =
                harness.runStratum("constructed", loadTriple(), contract("200"), new RecordingFault());

        assertEquals(PairedFaultExecutor.PairVerdict.FIRE, r.mist.get(0).pureDifferential);
        assertTrue(r.controlComparator.flagged);
        assertTrue(r.faultComparator.flagged);
    }

    @Test
    public void faultClearedEvenWhenFaultLegThrows() throws Exception {
        DataIntegrityRuntime.installHttpOverride(new DepthHttp());
        RecordingFault fault = new RecordingFault();
        // A stimulus that throws on the SECOND call (the fault leg's POST).
        ShippingEnqueueHeadToHead.Stimulus flaky = new ShippingEnqueueHeadToHead.Stimulus() {
            private int n = 0;

            @Override
            public ShippingEnqueueHeadToHead.Shipment postShipping() throws Exception {
                if (++n == 2) {
                    throw new IllegalStateException("boom on fault leg");
                }
                return new FakeStimulus().postShipping();
            }
        };
        ShippingEnqueueHeadToHead harness = new ShippingEnqueueHeadToHead(flaky, new NoStateClient());
        try {
            harness.runStratum("constructed", loadTriple(), contract("201"), fault);
            fail("expected the fault leg's stimulus exception to propagate");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("boom"));
        }
        // The SUT must never be left faulted: clear ran after inject even on the exception.
        assertEquals(Arrays.asList("clear", "inject", "clear"), fault.events);
    }
}
