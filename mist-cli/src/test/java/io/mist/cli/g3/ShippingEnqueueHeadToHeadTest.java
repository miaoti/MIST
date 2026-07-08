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
        // Bound the comparator's presence-retry so a broker-err liveness FAIL resolves fast.
        System.setProperty("mst.comparator.state.retry.cap.ms", "50");
        System.setProperty("mst.comparator.state.poll.ms", "5");
    }

    @After
    public void cleanup() {
        DataIntegrityRuntime.installHttpOverride(null);
        System.clearProperty(DataIntegrityRuntime.POLL_MS_PROPERTY);
        System.clearProperty(DataIntegrityRuntime.TIMEOUT_MS_PROPERTY);
        System.clearProperty(DataIntegrityRuntime.TRACE_SETTLE_MS_PROPERTY);
        System.clearProperty("mst.comparator.state.retry.cap.ms");
        System.clearProperty("mst.comparator.state.poll.ms");
    }

    private static final String HEALTHY = "{\"health\":["
            + "{\"service\":\"shipping-rabbitmq\",\"status\":\"OK\"},"
            + "{\"service\":\"shipping\",\"status\":\"OK\"}]}";
    private static final String BROKER_ERR = "{\"health\":["
            + "{\"service\":\"shipping-rabbitmq\",\"status\":\"err\"},"
            + "{\"service\":\"shipping\",\"status\":\"OK\"}]}";

    // ---- fakes ------------------------------------------------------------------

    /**
     * Bare /api/queues array; shipping-task depth = 1 for the first two reads, 2 after. This is
     * coupled to value-delta doing exactly two baseline reads per leg (review B-m1), but the
     * coupling is SELF-GUARDING: if the oracle ever changed that count, the control leg's
     * post-write read would no longer differ from its own baseline, so the FIRE assertion below
     * would FAIL rather than pass for the wrong reason. {@code calls} is asserted for good measure.
     */
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

    /**
     * BOTH legs land: shipping-task depth increments on each leg's post-write poll (control 1→2,
     * "fault" 2→3), so the value-delta sees X present on both → NO_FIRE. Models the benign control
     * where nothing is lost. Coupled to value-delta's 2 baseline reads per leg (calls 1-2 baseline,
     * 3 poll = control; 4-5 baseline, 6+ poll = second leg).
     */
    private static final class BenignDepthHttp implements DataIntegrityRuntime.Http {
        final AtomicInteger calls = new AtomicInteger();

        @Override
        public DataIntegrityRuntime.HttpResponse getSut(String path) {
            int c = calls.incrementAndGet();
            int depth = c <= 2 ? 1 : c <= 5 ? 2 : 3;
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

    /** Shared sever flag: the fault flips it, the /health client reads it. */
    private static final class SeverState {
        volatile boolean severed = false;
    }

    private static final class SeverFault implements ShippingEnqueueHeadToHead.Fault {
        final SeverState state;

        SeverFault(SeverState state) {
            this.state = state;
        }

        @Override
        public void inject() {
            state.severed = true;
        }

        @Override
        public void clear() {
            state.severed = false;
        }
    }

    /**
     * Models shipping's {@code GET /health}: green until severed, then the broker entry flips to
     * "err" (HTTP stays 200 = a genuine in-body detection). {@code transportOnFault} instead
     * returns a non-2xx /health on the severed leg, to exercise the transport reclassification.
     */
    private static final class HealthClient implements ContractEvaluator.SutClient {
        final SeverState state;
        final boolean transportOnFault;

        HealthClient(SeverState state, boolean transportOnFault) {
            this.state = state;
            this.transportOnFault = transportOnFault;
        }

        @Override
        public ContractEvaluator.Response post(String path, String jsonBody) {
            throw new AssertionError("SutClient.post must not be called");
        }

        @Override
        public ContractEvaluator.Response get(String path) {
            assertEquals("/health", path);
            if (!state.severed) {
                return new ContractEvaluator.Response(200, HEALTHY);
            }
            return transportOnFault
                    ? new ContractEvaluator.Response(503, "shipping unreachable")
                    : new ContractEvaluator.Response(200, BROKER_ERR);
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

    /** HTTP_STATUS 201 + a bound P2 liveness clause (the amended shipping contract's shape). */
    private static AssertionBindings.BoundEndpoint livenessContract() throws Exception {
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
                + "            expect: \"201\"\n"
                + "      - cite: \"broker+app liveness\"\n"
                + "        checks:\n"
                + "          - primitive: STATE_GET\n"
                + "            path: \"/health\"\n"
                + "            expect: \"contains-literal-fields\"\n"
                + "            collection_key: \"health\"\n"
                + "            fields: \"service=shipping-rabbitmq,status=OK\"\n";
        AssertionBindings.Bindings b = AssertionBindings.parse(
                new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)), "liveness-contract");
        return ShippingEnqueueHeadToHead.shippingEndpoint(b);
    }

    // ---- tests ------------------------------------------------------------------

    @Test
    public void mistFires_whileResponseComparatorMissesBothLegs() throws Exception {
        DepthHttp http = new DepthHttp();
        DataIntegrityRuntime.installHttpOverride(http);
        RecordingFault fault = new RecordingFault();
        ShippingEnqueueHeadToHead harness =
                new ShippingEnqueueHeadToHead(new FakeStimulus(), new NoStateClient());

        ShippingEnqueueHeadToHead.StratumResult r =
                harness.runStratum("constructed", loadTriple(), contract("201"), fault);

        // Sanity on the read sequence (review B-m1): control 2 baseline + >=1 poll, fault 2 baseline
        // + >=1 poll -> >=5 getSut calls; a drift in the oracle's baseline count would trip this.
        assertTrue("expected the value-delta baseline+poll read sequence", http.calls.get() >= 5);

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

    @Test
    public void liveness_caughtOnFaultLeg_cleanOnControl() throws Exception {
        // Natural stratum: /health green on control, broker-err on fault (HTTP 200 both). The bound
        // P2 liveness clause PASSes control and FAILs fault -> comparator CAUGHT (a diagnosis-gap
        // catch), while MIST FIREs on the specific lost enqueue.
        DataIntegrityRuntime.installHttpOverride(new DepthHttp());
        SeverState state = new SeverState();
        ShippingEnqueueHeadToHead harness =
                new ShippingEnqueueHeadToHead(new FakeStimulus(), new HealthClient(state, false));

        ShippingEnqueueHeadToHead.StratumResult r =
                harness.runStratum("natural", loadTriple(), livenessContract(), new SeverFault(state));

        assertEquals(PairedFaultExecutor.PairVerdict.FIRE, r.mist.get(0).pureDifferential);
        assertFalse("control /health green -> liveness PASSes", r.controlComparator.flagged);
        assertTrue("fault /health broker-err -> liveness FAILs -> CAUGHT", r.faultComparator.flagged);
        // ...and it is a GENUINE in-body detection (HTTP 200), NOT a transport reclassification.
        assertFalse("the fault liveness FAIL must be a real detection, not transport-only",
                ShippingEnqueueHeadToHead.onlyTransportFailures(r.faultComparator));
    }

    @Test
    public void benignControl_mistDoesNotFire_comparatorPasses() throws Exception {
        // The specificity control (result review C-M3): NO fault -> both legs land (X present on
        // both) -> MIST NO_FIRE, and the response+liveness contract holds on both legs. Proves the
        // shipping queue-depth oracle does not cry wolf when the enqueue is not lost.
        DataIntegrityRuntime.installHttpOverride(new BenignDepthHttp());
        ShippingEnqueueHeadToHead harness =
                new ShippingEnqueueHeadToHead(new FakeStimulus(), new HealthClient(new SeverState(), false));

        ShippingEnqueueHeadToHead.StratumResult r = harness.runStratum(
                "benign", loadTriple(), livenessContract(), new ShippingEnqueueHeadToHead.NoOpFault());

        assertEquals("both legs land -> no acked-but-lost -> NO_FIRE",
                PairedFaultExecutor.PairVerdict.NO_FIRE, r.mist.get(0).pureDifferential);
        assertFalse("control leg passes the contract", r.controlComparator.flagged);
        assertFalse("the second (no-fault) leg also passes -> comparator does not flag",
                r.faultComparator.flagged);
    }

    @Test
    public void transportOnlyFaultFlag_isReclassified_notCaught() throws Exception {
        // Review C-MAJOR-1: if the fault leg's /health decisive read is non-2xx, the STATE_GET FAILs
        // as a TRANSPORT failure. The leg is flagged, but the harness must NOT score it CAUGHT
        // (that would inflate the comparator's recall with an infra blip).
        DataIntegrityRuntime.installHttpOverride(new DepthHttp());
        SeverState state = new SeverState();
        ShippingEnqueueHeadToHead harness =
                new ShippingEnqueueHeadToHead(new FakeStimulus(), new HealthClient(state, true));

        ShippingEnqueueHeadToHead.StratumResult r =
                harness.runStratum("natural", loadTriple(), livenessContract(), new SeverFault(state));

        assertTrue("a non-2xx /health FAILs the clause", r.faultComparator.flagged);
        assertTrue("but it is transport-only -> reclassified to comparator-infra-failure, not CAUGHT",
                ShippingEnqueueHeadToHead.onlyTransportFailures(r.faultComparator));
    }
}
