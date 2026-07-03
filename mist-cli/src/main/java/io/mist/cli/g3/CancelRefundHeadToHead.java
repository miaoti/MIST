package io.mist.cli.g3;

import io.mist.cli.comparator.AssertionBindings;
import io.mist.cli.comparator.ContractEvaluator;
import io.mist.cli.comparator.RestAssuredSutClient;
import io.mist.cli.fault.DataIntegrityRuntime;
import io.mist.cli.fault.FaultInjector;
import io.mist.cli.fault.IstioRouteFaultInjector;
import io.mist.cli.fault.PairedFaultExecutor;
import io.mist.cli.fault.SutFlagFaultInjector;
import io.mist.cli.fault.TargetTripleRegistry;
import io.restassured.RestAssured;
import org.json.JSONObject;

import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

/**
 * G3 depth head-to-head runner: MIST B2's differential data-integrity oracle vs the
 * frozen blind response-assertion comparator, observing the SAME cancel→refund scenario
 * under two fault strata. Reuses the already-reviewed oracle LOGIC (it does NOT go through
 * PairedFaultExecutor.execute, which is coupled to SUT-flag injection) — the driver owns
 * the control/fault legs and the fault mechanism, and calls the pure verdict entry points:
 * {@link PairedFaultExecutor#evaluate} for MIST and {@link ContractEvaluator#evaluate} for
 * the comparator. Design + the verified-signature blueprint:
 * debug/a-main/prep/g3-headtohead-run-architecture.md.
 *
 * <p>Two strata, one stimulus:
 * <ul>
 *   <li><b>natural</b> — unmodified SUT, fault = the inbound EnvoyFilter abort on
 *       /drawback ({@link IstioRouteFaultInjector}); cancel returns {@code {1,"error"}} →
 *       both oracles flag (detection tie; MIST additionally localizes the lost refund).</li>
 *   <li><b>constructed</b> — the fork's fabricated-ack drawback flag
 *       ({@link SutFlagFaultInjector}); cancel returns a clean {@code {1,"Success."}} →
 *       the comparator misses, MIST fires (the clean win).</li>
 * </ul>
 *
 * <p>The one SUT-specific piece is {@link Stimulus}: create a fresh PAID order and issue
 * the bodyless cancel. Everything else is SUT-agnostic and compile-checkable without the
 * live SUT; the stimulus is implemented + tuned against the deployed TrainTicket.
 *
 * <p>Read-back gate note: in the minimal subgraph ts-cancel-service is sidecar-free, so
 * the cancel write yields no Istio/Jaeger trace → {@code traceId} is null → MIST's
 * read-back is timeout-gated, not trace-gated. The differential balance-delta verdict
 * still holds (control: balance moves +refund, X present fast; fault: balance never moves,
 * X absent to the cap → FIRE). Recorded as a known weaker-gate limitation.
 */
public final class CancelRefundHeadToHead {

    /** The SUT-specific boundary — implemented against the live TrainTicket. */
    public interface Stimulus {
        /** Register a fresh buyer and create ONE PAID order (price&gt;0, far-future travel). */
        Order createPaidOrder() throws Exception;

        /** Issue the bodyless cancel GET for the order; return its HTTP status + body. */
        Resp cancel(Order order) throws Exception;
    }

    /** A created order: the id to cancel and the buyer login (== the value-delta userId key). */
    public static final class Order {
        public final String orderId;
        public final String loginId;

        public Order(String orderId, String loginId) {
            this.orderId = orderId;
            this.loginId = loginId;
        }
    }

    /** A minimal HTTP response (status + raw body). */
    public static final class Resp {
        public final int status;
        public final String body;

        public Resp(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }

    private final Stimulus stimulus;
    private final ContractEvaluator.SutClient sutClient;

    public CancelRefundHeadToHead(Stimulus stimulus, ContractEvaluator.SutClient sutClient) {
        this.stimulus = stimulus;
        this.sutClient = sutClient;
    }

    /** Both oracles' observations of one leg (control or fault) of one stratum. */
    private static final class LegOutcome {
        final List<DataIntegrityRuntime.RunRecord> mistRecords;
        final ContractEvaluator.EndpointOutcome comparator;

        LegOutcome(List<DataIntegrityRuntime.RunRecord> mistRecords,
                   ContractEvaluator.EndpointOutcome comparator) {
            this.mistRecords = mistRecords;
            this.comparator = comparator;
        }
    }

    /** One leg: run the shared stimulus once and feed BOTH oracles the same cancel result. */
    private LegOutcome runLeg(TargetTripleRegistry.Triple triple,
                              AssertionBindings.BoundEndpoint contract, String leg) throws Exception {
        DataIntegrityRuntime.beginRun(Collections.singletonList(triple), leg);
        ContractEvaluator.EndpointOutcome comparator;
        List<DataIntegrityRuntime.RunRecord> records;
        try {
            Order order = stimulus.createPaidOrder();
            String corr = triple.name + "#" + leg;
            // MIST: capture the pre-cancel balance baseline for this fresh buyer.
            DataIntegrityRuntime.beforeWriteSupplied(triple.writeEndpoint, corr, null,
                    "userId", order.loginId);
            Resp cancel = stimulus.cancel(order);
            // MIST: acknowledge + poll the /account read-back for the refund (value-delta).
            DataIntegrityRuntime.afterWrite(triple.writeEndpoint, corr, cancel.status,
                    cancel.body, null);
            // Comparator: the SAME cancel response against the frozen contract. The cancel's
            // state clauses are NOT_CHECKABLE (review B), so only the response-envelope
            // checks bind; submittedBody supplies the fields those clauses reference.
            String submitted = new JSONObject()
                    .put("orderId", order.orderId)
                    .put("loginId", order.loginId)
                    .toString();
            comparator = ContractEvaluator.evaluate(contract, leg, submitted,
                    new ContractEvaluator.Response(cancel.status, cancel.body), sutClient);
        } finally {
            records = DataIntegrityRuntime.endRun();
        }
        return new LegOutcome(records, comparator);
    }

    /** One stratum: clean control leg, then the faulted leg, then both oracles' verdicts. */
    public void runStratum(String stratum, TargetTripleRegistry.Triple triple,
                           AssertionBindings.BoundEndpoint contract, FaultInjector injector,
                           FaultInjector.FaultTarget target) throws Exception {
        injector.clear(target); // hygiene: start clean (flushes any stale fault)
        LegOutcome control = runLeg(triple, contract, stratum + "-control");
        injector.inject(target);
        LegOutcome fault;
        try {
            fault = runLeg(triple, contract, stratum + "-fault");
        } finally {
            injector.clear(target); // the SUT must never be left faulted
        }
        List<PairedFaultExecutor.PairResult> mist = PairedFaultExecutor.evaluate(
                Collections.singletonList(triple), control.mistRecords, fault.mistRecords);
        printCell(stratum, mist, control.comparator, fault.comparator);
    }

    /** Prints one stratum's cell: MIST verdict + the comparator's control/fault flags. */
    private static void printCell(String stratum, List<PairedFaultExecutor.PairResult> mist,
                                  ContractEvaluator.EndpointOutcome control,
                                  ContractEvaluator.EndpointOutcome fault) {
        String mistVerdict = mist.isEmpty() ? "NO_RESULT" : mist.get(0).pureDifferential.name();
        String mistReason = mist.isEmpty() ? "" : mist.get(0).reason;
        System.out.println("\n=== stratum: " + stratum + " ===");
        System.out.println("  MIST B2 (differential value-delta): " + mistVerdict);
        System.out.println("      " + mistReason);
        System.out.println("  Comparator (frozen response contract): control flagged="
                + control.flagged + ", fault flagged=" + fault.flagged
                + "  -> " + (fault.flagged ? "CAUGHT" : "MISSED")
                + (control.flagged ? " (control also flagged — systemic, verify)" : ""));
    }

    /** Picks the single cancel endpoint from the frozen contract (it binds exactly one). */
    static AssertionBindings.BoundEndpoint cancelEndpoint(AssertionBindings.Bindings bindings) {
        if (bindings.endpoints.size() == 1) {
            return bindings.endpoints.get(0);
        }
        for (AssertionBindings.BoundEndpoint e : bindings.endpoints) {
            if (e.endpoint.toLowerCase().contains("cancel")) {
                return e;
            }
        }
        throw new IllegalStateException("no cancel endpoint in the contract (" + bindings.endpoints.size()
                + " endpoints) — check the frozen bindings file");
    }

    private static String required(String key) {
        String v = System.getProperty(key);
        if (v == null || v.trim().isEmpty()) {
            throw new IllegalArgumentException("missing required -D" + key);
        }
        return v.trim();
    }

    /**
     * Wiring. Config via -D properties (all live/SUT-specific): g3.base.url (gateway),
     * g3.kube.context, g3.namespace, g3.drawback.probe.url (a non-mutating incomplete
     * /drawback path via an inside-payment port-forward, answers 418 when the abort is
     * live), g3.envoyfilter.manifest, g3.contract.path, g3.triples.natural,
     * g3.triples.constructed. The {@link Stimulus} impl is supplied by the live launcher.
     */
    public static void run(Stimulus stimulus) throws Exception {
        String baseUrl = required("g3.base.url");
        RestAssured.baseURI = baseUrl;               // MIST read-back (RestAssuredHttp)
        System.setProperty("base.url", baseUrl);     // comparator SutClient + preflight
        System.setProperty(FaultInjector.ENABLED_PROPERTY, "true");
        System.setProperty("mst.test.parallelism", "1"); // the hooks require single-threaded

        TargetTripleRegistry.Registry natural =
                TargetTripleRegistry.load(Paths.get(required("g3.triples.natural")));
        TargetTripleRegistry.Registry constructed =
                TargetTripleRegistry.load(Paths.get(required("g3.triples.constructed")));
        AssertionBindings.BoundEndpoint contract =
                cancelEndpoint(AssertionBindings.load(Paths.get(required("g3.contract.path"))));
        ContractEvaluator.SutClient sutClient = new RestAssuredSutClient();

        CancelRefundHeadToHead harness = new CancelRefundHeadToHead(stimulus, sutClient);

        // natural stratum — route-scoped EnvoyFilter abort on /drawback (unmodified SUT).
        String context = natural.cluster.context;
        String namespace = natural.cluster.namespace;
        IstioRouteFaultInjector istio = new IstioRouteFaultInjector(context, namespace,
                Paths.get(required("g3.envoyfilter.manifest")), required("g3.drawback.probe.url"),
                418, 120);
        harness.runStratum("natural", natural.triples.get(0), contract, istio,
                new FaultInjector.FaultTarget("ts-inside-payment-service", "istio.drawback.abort"));

        // constructed stratum — the fork's fabricated-ack drawback SUT flag.
        TargetTripleRegistry.Triple constructedTriple = constructed.triples.get(0);
        SutFlagFaultInjector sutFlag = new SutFlagFaultInjector(constructed.cluster.context,
                constructed.cluster.namespace, constructed.cluster.rolloutTimeoutSeconds, 15);
        harness.runStratum("constructed", constructedTriple, contract, sutFlag,
                constructedTriple.faultFlag);

        // agreement anchor (both catch — comparator is no strawman): a body-carrying create
        // + fabricated-ack where the contract's STATE clause binds. Authored once the two
        // core cells run live (needs a second triple + contract endpoint).
    }
}
