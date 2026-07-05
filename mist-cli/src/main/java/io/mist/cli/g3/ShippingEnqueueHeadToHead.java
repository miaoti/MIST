package io.mist.cli.g3;

import io.mist.cli.comparator.AssertionBindings;
import io.mist.cli.comparator.ContractEvaluator;
import io.mist.cli.comparator.RestAssuredSutClient;
import io.mist.cli.fault.DataIntegrityRuntime;
import io.mist.cli.fault.PairedFaultExecutor;
import io.mist.cli.fault.TargetTripleRegistry;
import io.restassured.RestAssured;
import org.json.JSONObject;

import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

/**
 * G3 breadth head-to-head runner for the Sock Shop shipping enqueue defect: MIST B2's
 * differential data-integrity oracle vs the frozen blind response-assertion comparator,
 * observing the SAME {@code POST /shipping} scenario under two fault strata. Mirrors
 * {@link CancelRefundHeadToHead} — it reuses the reviewed oracle LOGIC (NOT
 * PairedFaultExecutor.execute, which is coupled to SUT-flag injection): the driver owns
 * the control/fault legs and the fault mechanism, and calls the pure verdict entry points
 * {@link PairedFaultExecutor#evaluate} (MIST) and {@link ContractEvaluator#evaluate}
 * (comparator). Design + verified feasibility: debug/a-main/g3-sut2-wildhunt-plan.md (v3)
 * and debug/a-main/prep/g3-sut2-wildhunt-exec-progress.md.
 *
 * <p>The defect is natural + self-documented in {@code weaveworksdemos/shipping:0.4.8}:
 * {@code ShippingController.postShipping} catches ANY {@code convertAndSend("shipping-task",…)}
 * exception, logs "Accepting anyway. Don't do this for real!", and returns HTTP 201
 * unconditionally — a fire-and-forget enqueue whose lost landing a write-boundary contract
 * oracle structurally cannot localize.
 *
 * <p>Two strata, one stimulus (each stratum's fault is supplied as a {@link Fault}):
 * <ul>
 *   <li><b>natural</b> — a surgical shipping&#8617;broker sever: the swallow fires, POST returns
 *       201 + the message is lost, but {@code /health} also reads "err" (getHealth live-probes
 *       the broker) → the comparator flags the OUTAGE via a liveness clause but only MIST
 *       localizes the specific lost POST (diagnosis gap, mirrors the accepted TT natural
 *       stratum — NOT a tie).</li>
 *   <li><b>constructed</b> — a {@code max-length:1 / overflow:reject-publish} queue policy:
 *       the publish is rejected + lost, POST returns 201, but {@code /health} stays GREEN (the
 *       connection is live) → the comparator passes both legs even WITH a {@code /health}
 *       clause → the clean MIST win (mirrors the accepted TT constructed stratum).</li>
 * </ul>
 *
 * <p>The durable-effect read-back is the RabbitMQ management API (a different host + basic
 * auth), installed via {@link ShippingReadbackHttp}. The value-delta triple reads
 * {@code GET /api/queues/%2f} (match_field=name → the {@code shipping-task} queue,
 * value_field=messages): control moves depth baseline→baseline+1 (X present), the faulted
 * leg leaves depth at its own baseline (X absent to the cap → FIRE). The queue-master
 * consumer is scaffolded to 0 so depth is monotonic during a run.
 */
public final class ShippingEnqueueHeadToHead {

    /** The SUT-specific boundary — implemented against the live shipping service. */
    public interface Stimulus {
        /** POST /shipping with a fresh {id,name}; return the submitted fields + HTTP status/body. */
        Shipment postShipping() throws Exception;
    }

    /**
     * A fault mechanism for one stratum. {@link #inject} makes the enqueue loss live and
     * {@link #clear} restores the SUT; both must return only once the SUT has converged so
     * the next request observes the new state. Simpler than the SUT-flag
     * {@link io.mist.cli.fault.FaultInjector} because the shipping faults are broker/mesh
     * side, not a SUT {@code -D} flag; the harness owns the timing between legs.
     */
    public interface Fault {
        void inject() throws Exception;

        void clear() throws Exception;
    }

    /** One posted shipment: the submitted id/name (for the comparator echo) + the response. */
    public static final class Shipment {
        public final String id;
        public final String name;
        public final int status;
        public final String body;

        public Shipment(String id, String name, int status, String body) {
            this.id = id;
            this.name = name;
            this.status = status;
            this.body = body;
        }
    }

    /** One stratum's verdicts, returned for assertion (also printed). */
    public static final class StratumResult {
        public final List<PairedFaultExecutor.PairResult> mist;
        public final ContractEvaluator.EndpointOutcome controlComparator;
        public final ContractEvaluator.EndpointOutcome faultComparator;

        StratumResult(List<PairedFaultExecutor.PairResult> mist,
                      ContractEvaluator.EndpointOutcome controlComparator,
                      ContractEvaluator.EndpointOutcome faultComparator) {
            this.mist = mist;
            this.controlComparator = controlComparator;
            this.faultComparator = faultComparator;
        }
    }

    /** The queue whose depth is the write's durable-effect sink (the value-delta match key). */
    static final String QUEUE_NAME = "shipping-task";

    private final Stimulus stimulus;
    private final ContractEvaluator.SutClient sutClient;

    public ShippingEnqueueHeadToHead(Stimulus stimulus, ContractEvaluator.SutClient sutClient) {
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

    /** One leg: run the shared stimulus once and feed BOTH oracles the same POST result. */
    private LegOutcome runLeg(TargetTripleRegistry.Triple triple,
                              AssertionBindings.BoundEndpoint contract, String leg) throws Exception {
        DataIntegrityRuntime.beginRun(Collections.singletonList(triple), leg);
        ContractEvaluator.EndpointOutcome comparator;
        List<DataIntegrityRuntime.RunRecord> records;
        try {
            // Correlator identifies the WRITE, not the leg — IDENTICAL across control and fault
            // legs so PairedFaultExecutor.joinRecords pairs them (one POST per leg, so a
            // per-triple constant is unique within each leg).
            String corr = triple.name + "#enqueue";
            // MIST: capture the pre-POST shipping-task queue-depth baseline for this leg.
            DataIntegrityRuntime.beforeWriteSupplied(triple.writeEndpoint, corr, null,
                    "name", QUEUE_NAME);
            Shipment shipment = stimulus.postShipping();
            // MIST: acknowledge + poll the queue-depth read-back for the enqueue (value-delta).
            DataIntegrityRuntime.afterWrite(triple.writeEndpoint, corr, shipment.status,
                    shipment.body, null);
            // Comparator: the SAME POST response against the frozen contract. submittedBody
            // supplies the id/name the response-echo clauses reference; any /health liveness
            // clause is evaluated live via the sutClient.
            String submitted = new JSONObject()
                    .put("id", shipment.id)
                    .put("name", shipment.name)
                    .toString();
            comparator = ContractEvaluator.evaluate(contract, leg, submitted,
                    new ContractEvaluator.Response(shipment.status, shipment.body), sutClient);
        } finally {
            records = DataIntegrityRuntime.endRun();
        }
        return new LegOutcome(records, comparator);
    }

    /** One stratum: clean control leg, then the faulted leg, then both oracles' verdicts. */
    public StratumResult runStratum(String stratum, TargetTripleRegistry.Triple triple,
                                    AssertionBindings.BoundEndpoint contract, Fault fault)
            throws Exception {
        fault.clear(); // hygiene: start clean (flushes any stale fault)
        LegOutcome control = runLeg(triple, contract, stratum + "-control");
        LegOutcome faulted;
        try {
            // inject() INSIDE the try (review A-M1 / C-MAJOR-1): the injectors mutate a
            // DURABLE external fault (an Istio manifest / a reject-publish policy) BEFORE
            // their convergence probe, and that probe can throw (a mesh sever may not flip
            // /health within budget). Outside the try, such a throw would leak the fault and
            // leave the cluster partitioned. clear() is idempotent (delete --ignore-not-found
            // / DELETE-404), so covering inject() with the finally is safe.
            fault.inject();
            faulted = runLeg(triple, contract, stratum + "-fault");
        } finally {
            fault.clear(); // the SUT must never be left faulted
        }
        List<PairedFaultExecutor.PairResult> mist = PairedFaultExecutor.evaluate(
                Collections.singletonList(triple), control.mistRecords, faulted.mistRecords);
        requireClaimEligible(mist);
        StratumResult result = new StratumResult(mist, control.comparator, faulted.comparator);
        printCell(stratum, result);
        printProbeValues(control.mistRecords, faulted.mistRecords);
        return result;
    }

    /**
     * Rider-1 claim-eligibility guard (shared with CancelRefundHeadToHead): a MIST tally may
     * feed a G3 claim only when the pair was joined by CORRELATOR with a UNIQUE correlator in
     * each leg. That holds by construction here (one POST per leg, a leg-shared per-triple
     * correlator) — asserted defensively so a silent regression to a positional join can never
     * feed a claim.
     */
    static void requireClaimEligible(List<PairedFaultExecutor.PairResult> mist) {
        if (mist.isEmpty()) {
            return; // NO_RESULT prints as such; nothing feeds a claim
        }
        PairedFaultExecutor.PairResult r = mist.get(0);
        if (!"correlator".equals(r.joinMode) || !r.correlatorUnique) {
            throw new IllegalStateException("claim-eligibility violated: joinMode=" + r.joinMode
                    + ", correlatorUnique=" + r.correlatorUnique
                    + " (G3 claims require a correlator join with a unique correlator)");
        }
    }

    /**
     * Review C-MAJOR-1 (mirrors {@link io.mist.cli.comparator.ComparatorRunner}): a fault-leg flag
     * whose FAILs are ALL transport-level (a state GET non-2xx decisive read) is an infra blip, not
     * a detection — scoring it CAUGHT would inflate the comparator's recall and misattribute the
     * natural cell's headline. /health is served by shipping itself (not through the severed AMQP
     * path) so the primary path stays 2xx; this guards the disclosed pod-bounce fallback + hiccups.
     */
    static boolean onlyTransportFailures(ContractEvaluator.EndpointOutcome outcome) {
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

    /** Prints one stratum's cell: MIST verdict + the comparator's control/fault flags. */
    private static void printCell(String stratum, StratumResult r) {
        String mistVerdict = r.mist.isEmpty() ? "NO_RESULT" : r.mist.get(0).pureDifferential.name();
        String mistReason = r.mist.isEmpty() ? "" : r.mist.get(0).reason;
        System.out.println("\n=== stratum: " + stratum + " ===");
        System.out.println("  MIST B2 (differential value-delta): " + mistVerdict);
        System.out.println("      " + mistReason);
        boolean faultInfraOnly = r.faultComparator.flagged && onlyTransportFailures(r.faultComparator);
        String comparatorOutcome;
        if (faultInfraOnly) {
            comparatorOutcome = "comparator-infra-failure (fault-leg state GET non-2xx — NOT a detection)";
        } else if (r.faultComparator.flagged) {
            comparatorOutcome = "CAUGHT (response/liveness-level, NOT write-localized — MIST additionally"
                    + " localizes the specific lost enqueue)";
        } else {
            comparatorOutcome = "MISSED";
        }
        System.out.println("  Comparator (frozen response contract): control flagged="
                + r.controlComparator.flagged + ", fault flagged=" + r.faultComparator.flagged
                + "  -> " + comparatorOutcome
                + (r.controlComparator.flagged ? " (control also flagged — systemic, verify)" : ""));
        if (!r.mist.isEmpty()) {
            System.out.println("  claim-eligibility: joinMode=" + r.mist.get(0).joinMode
                    + ", correlatorUnique=" + r.mist.get(0).correlatorUnique);
        }
    }

    /**
     * Evidence-only (does not affect any verdict): prints each leg's shipping-task queue depth
     * at baseline vs final read-back, so the log shows the value-delta is a real depth delta —
     * baseline N present in both legs, moving +1 only on control.
     */
    private static void printProbeValues(List<DataIntegrityRuntime.RunRecord> control,
                                         List<DataIntegrityRuntime.RunRecord> fault) {
        System.out.println("  value-delta probe (shipping-task queue depth, evidence only):");
        System.out.println("      control " + probeLine(control));
        System.out.println("      fault   " + probeLine(fault));
    }

    private static String probeLine(List<DataIntegrityRuntime.RunRecord> records) {
        if (records == null || records.isEmpty()) {
            return "<no record>";
        }
        DataIntegrityRuntime.RunRecord r = records.get(records.size() - 1);
        return "baseline=" + depthOf(r.baselineBody) + " -> final=" + depthOf(r.lastReadbackBody);
    }

    /**
     * Pulls the shipping-task queue's {@code messages} out of a bare /api/queues array body
     * (evidence only — does not affect any verdict). Uses a REAL JSON parse so the live body's
     * nested queue sub-objects (arguments{}, backing_queue_status{}, message_stats{}, … — 43
     * fields) do not defeat it; the old brace-bounded regex matched only flat objects and would
     * misreport &lt;ABSENT&gt; on a correct FIRE with real depth movement (review A-m5).
     */
    private static String depthOf(String queuesBody) {
        if (queuesBody == null) {
            return "<no-read>";
        }
        try {
            org.json.JSONArray arr = new org.json.JSONArray(queuesBody.trim());
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject q = arr.optJSONObject(i);
                if (q != null && QUEUE_NAME.equals(q.optString("name", null)) && q.has("messages")) {
                    return String.valueOf(q.get("messages"));
                }
            }
            return "<ABSENT>";
        } catch (RuntimeException e) {
            return "<unparseable>";
        }
    }

    /** Picks the single POST /shipping endpoint from the frozen contract (it binds exactly one). */
    static AssertionBindings.BoundEndpoint shippingEndpoint(AssertionBindings.Bindings bindings) {
        if (bindings.endpoints.size() == 1) {
            return bindings.endpoints.get(0);
        }
        for (AssertionBindings.BoundEndpoint e : bindings.endpoints) {
            if (e.endpoint.toLowerCase().contains("shipping")) {
                return e;
            }
        }
        throw new IllegalStateException("no shipping endpoint in the contract ("
                + bindings.endpoints.size() + " endpoints) — check the frozen bindings file");
    }

    private static String required(String key) {
        String v = System.getProperty(key);
        if (v == null || v.trim().isEmpty()) {
            throw new IllegalArgumentException("missing required -D" + key);
        }
        return v.trim();
    }

    /**
     * Wiring. Config via -D properties (all live/SUT-specific): g3.ship.base (shipping gateway),
     * g3.ship.contract.path, g3.ship.triple (the value-delta triple), g3.ship.rmq.base (broker
     * mgmt, port-forwarded), g3.ship.rmq.user/pass, g3.ship.strata (default "natural,constructed").
     * The natural fault is the surgical sever ({@link IstioAmqpSeverInjector}); the constructed
     * fault is the reject-publish policy ({@link RabbitPolicyInjector}). The {@link Stimulus} is
     * supplied by the live launcher.
     */
    public static void run(Stimulus stimulus) throws Exception {
        String baseUrl = required("g3.ship.base");
        RestAssured.baseURI = baseUrl;            // comparator SutClient + any Stimulus RestAssured use
        System.setProperty("base.url", baseUrl);  // comparator SutClient + preflight
        System.setProperty("mst.test.parallelism", "1"); // the hooks require single-threaded
        // Review A-m4 / C read-lag: the mgmt `messages` datum is stats-DB-sampled (~5s), so the
        // read-back must wait well beyond that or the control leg reads absence too early (a false
        // NO_RESULT). Floor the oracle timeout >> the stats interval unless the launcher set one.
        if (System.getProperty(DataIntegrityRuntime.TIMEOUT_MS_PROPERTY) == null) {
            System.setProperty(DataIntegrityRuntime.TIMEOUT_MS_PROPERTY, "20000");
        }

        String rmqBase = required("g3.ship.rmq.base");
        String rmqUser = required("g3.ship.rmq.user");
        String rmqPass = required("g3.ship.rmq.pass");
        try {
            // Route the value-delta read-back at the broker mgmt API (off-SUT host + basic auth).
            // Installed INSIDE the try (review B-m4) so it is cleared in the finally on any throw.
            DataIntegrityRuntime.installHttpOverride(
                    new ShippingReadbackHttp(rmqBase, rmqUser, rmqPass, 5000));
            TargetTripleRegistry.Registry reg =
                    TargetTripleRegistry.load(Paths.get(required("g3.ship.triple")));
            TargetTripleRegistry.Triple triple = reg.triples.get(0);
            AssertionBindings.BoundEndpoint contract =
                    shippingEndpoint(AssertionBindings.load(Paths.get(required("g3.ship.contract.path"))));
            ContractEvaluator.SutClient sutClient = new RestAssuredSutClient();

            ShippingEnqueueHeadToHead harness = new ShippingEnqueueHeadToHead(stimulus, sutClient);

            java.util.Set<String> strata = new java.util.HashSet<>(java.util.Arrays.asList(
                    System.getProperty("g3.ship.strata", "natural,constructed").toLowerCase().split(",")));

            if (strata.contains("natural")) {
                harness.runStratum("natural", triple, contract, new IstioAmqpSeverInjector(
                        System.getProperty("g3.ship.kubectl", "kubectl"),
                        System.getProperty("g3.ship.kubeconfig"),
                        System.getProperty("g3.ship.namespace", "sock-shop"),
                        Paths.get(required("g3.ship.sever.manifest")),
                        baseUrl.replaceAll("/+$", "") + "/health",
                        Long.getLong("g3.ship.converge.seconds", 60L),
                        System.getProperty("g3.ship.broker.workload", "deploy/rabbitmq")));
            }
            if (strata.contains("constructed")) {
                harness.runStratum("constructed", triple, contract,
                        new RabbitPolicyInjector(rmqBase, rmqUser, rmqPass, QUEUE_NAME));
            }
        } finally {
            DataIntegrityRuntime.installHttpOverride(null); // never leak the override into a later run
        }
    }
}
