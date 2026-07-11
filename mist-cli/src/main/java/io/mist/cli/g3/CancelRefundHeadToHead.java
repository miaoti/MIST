package io.mist.cli.g3;

import io.mist.cli.comparator.AssertionBindings;
import io.mist.cli.comparator.ContractEvaluator;
import io.mist.cli.comparator.RestAssuredSutClient;
import io.mist.cli.fault.DataIntegrityRuntime;
import io.mist.cli.fault.FaultInjector;
import io.mist.cli.fault.HttpToggleFaultInjector;
import io.mist.cli.fault.PairedFaultExecutor;
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
 *   <li><b>natural</b> — the fork's drawback fault mode "fail": drawBack throws →
 *       cancelOrder's genuine compensation-failure catch returns {@code {1,"error"}} → both
 *       oracles flag (detection tie; MIST additionally localizes the lost refund).</li>
 *   <li><b>constructed</b> — the fork's drawback fault mode "fabricatedack": cancel returns a
 *       clean {@code {1,"Success."}} → the comparator misses, MIST fires (the clean win).</li>
 * </ul>
 *
 * <p>Both modes are toggled by {@link HttpToggleFaultInjector} — a RUNTIME in-memory switch on
 * the un-restarted inside-payment pod. Earlier restart-based mechanisms (a JAVA_TOOL_OPTIONS flag
 * rollout, an EnvoyFilter mesh abort) both raced ts-cancel-service's client-side connection pool /
 * instance cache and were unreliable per leg (g3-headtohead-results.md).
 *
 * <p>The one SUT-specific piece is {@link Stimulus}: create a fresh PAID order and issue
 * the bodyless cancel. Everything else is SUT-agnostic and compile-checkable without the
 * live SUT; the stimulus is implemented + tuned against the deployed TrainTicket.
 *
 * <p>Read-back gate note: in the minimal subgraph ts-cancel-service is sidecar-free, so
 * the cancel write yields no Istio/Jaeger trace → {@code traceId} is null → MIST's
 * read-back is timeout-gated, not trace-gated. The differential balance-delta verdict
 * still holds (control: balance moves +refund, X present fast; fault: balance never moves,
 * X absent to the cap → FIRE). E2/C1 UPDATE (reviewer B-minor-1): the cancel now DOES yield a trace (the
 * stimulus injects a client traceparent the SUT adopts), but the read-back stays timeout-gated BY DESIGN
 * — {@link #runLeg} passes {@code afterWrite(..., null)}, using the captured trace-id ONLY for out-of-band
 * trace selection, never to gate the verdict (no Jaeger coupling, zero verdict difference). This null is
 * a LOAD-BEARING invariant: trace-gating the read-back here would couple the verdict to export latency —
 * do not pass the trace-id to afterWrite.
 */
public final class CancelRefundHeadToHead {

    /** The SUT-specific boundary — implemented against the live TrainTicket. */
    public interface Stimulus {
        /** Register a fresh buyer and create ONE PAID order (price&gt;0, far-future travel). */
        Order createPaidOrder() throws Exception;

        /** Issue the bodyless cancel GET for the order; return its HTTP status + body. */
        Resp cancel(Order order) throws Exception;
    }

    /**
     * A created order: the id to cancel, the buyer login (== the value-delta userId key),
     * and the buyer's JWT (used for the cancel and for MIST's /account read-back auth).
     */
    public static final class Order {
        public final String orderId;
        public final String loginId;
        public final String token;

        public Order(String orderId, String loginId, String token) {
            this.orderId = orderId;
            this.loginId = loginId;
            this.token = token;
        }
    }

    /** A minimal HTTP response (status + raw body + the client trace-id, if one was injected). */
    public static final class Resp {
        public final int status;
        public final String body;
        /**
         * E2 (plan rev 2.1, C1): the client-generated W3C trace-id the stimulus put on this write's
         * {@code traceparent}, used ONLY to select exactly this operation's trace for out-of-band
         * scoring. It does NOT gate the read-back (that stays timeout-gated — B-minor-1), so it never
         * touches the verdict; {@code null} when no traceparent was injected (the legacy G3 path).
         */
        public final String traceId;

        public Resp(int status, String body) {
            this(status, body, null);
        }

        public Resp(int status, String body, String traceId) {
            this.status = status;
            this.body = body;
            this.traceId = traceId;
        }
    }

    /** A client-generated W3C trace context: the 32-hex trace-id + the full {@code traceparent} header. */
    public static final class ClientTrace {
        public final String traceId;
        public final String traceparent;

        public ClientTrace(String traceId, String traceparent) {
            this.traceId = traceId;
            this.traceparent = traceparent;
        }
    }

    /**
     * E2/C1: mint a valid W3C {@code traceparent} (version 00, sampled) with a fresh non-zero 32-hex
     * trace-id + 16-hex parent-span-id, so the SUT (always-on sampler) roots the cancel's server span on
     * OUR trace-id and the exact operation's trace is fetchable by id. Extracted + deterministic-format
     * so it is unit-testable without the live SUT.
     */
    public static ClientTrace newClientTrace(java.util.Random rnd) {
        String traceId = String.format("%016x%016x", rnd.nextLong(), rnd.nextLong() | 1L); // non-zero
        String spanId = String.format("%016x", rnd.nextLong() | 1L);                        // non-zero
        return new ClientTrace(traceId, "00-" + traceId + "-" + spanId + "-01");
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
        /** E2/C1: the cancel's client trace-id for out-of-band trace export selection; may be null. */
        final String traceId;

        LegOutcome(List<DataIntegrityRuntime.RunRecord> mistRecords,
                   ContractEvaluator.EndpointOutcome comparator, String traceId) {
            this.mistRecords = mistRecords;
            this.comparator = comparator;
            this.traceId = traceId;
        }
    }

    /** One leg: run the shared stimulus once and feed BOTH oracles the same cancel result. */
    private LegOutcome runLeg(TargetTripleRegistry.Triple triple,
                              AssertionBindings.BoundEndpoint contract, String leg) throws Exception {
        DataIntegrityRuntime.beginRun(Collections.singletonList(triple), leg);
        ContractEvaluator.EndpointOutcome comparator;
        List<DataIntegrityRuntime.RunRecord> records;
        String traceId = null;
        try {
            Order order = stimulus.createPaidOrder();
            // Correlator identifies the WRITE, not the leg — it must be IDENTICAL across the
            // control and fault legs so PairedFaultExecutor.joinRecords pairs them (one cancel
            // write per leg, so a per-triple constant is unique within each leg).
            String corr = triple.name + "#cancel";
            // MIST: capture the pre-cancel balance baseline for this fresh buyer.
            DataIntegrityRuntime.beforeWriteSupplied(triple.writeEndpoint, corr, null,
                    "userId", order.loginId);
            Resp cancel = stimulus.cancel(order);
            traceId = cancel.traceId; // E2/C1: for out-of-band trace export selection (NOT the gate)
            // MIST: acknowledge + poll the /account read-back for the refund (value-delta). traceId
            // stays null here on purpose — the read-back is TIMEOUT-gated (B-minor-1), not trace-gated.
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
        return new LegOutcome(records, comparator, traceId);
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
        requireClaimEligible(mist);
        requirePreFundedBaselines(control.mistRecords, fault.mistRecords);
        printCell(stratum, mist, control.comparator, fault.comparator);
        printProbeValues(control.mistRecords, fault.mistRecords);
        // E2/C1: emit each leg's cancel trace-id so the out-of-band scorer fetches EXACTLY that
        // operation's trace (exactly-one-trace guard satisfied by id). Read-back stays timeout-gated.
        System.out.println("  cancel trace-ids (out-of-band trace-score selection): control="
                + control.traceId + " fault=" + fault.traceId);
    }

    /**
     * Round-2 review B gate: the clean win's un-contestability REQUIRES a non-zero pre-funded
     * baseline PRESENT in both legs (a silently soft-failed pre-fund would regress the value-delta
     * to the contestable appear-vs-absent membership shape with no verdict-level signal). Both
     * legs' probe baselines must parse to the SAME POSITIVE number, or the cell aborts instead of
     * printing a mislabeled verdict. (The agreement runner does NOT use this gate — its membership
     * cell intentionally starts from an absent baseline.)
     */
    private static void requirePreFundedBaselines(List<DataIntegrityRuntime.RunRecord> control,
                                                  List<DataIntegrityRuntime.RunRecord> fault) {
        java.math.BigDecimal c = parsedBaseline(control);
        java.math.BigDecimal f = parsedBaseline(fault);
        if (c == null || f == null || c.signum() <= 0 || c.compareTo(f) != 0) {
            throw new IllegalStateException("pre-funded-baseline gate violated: control baseline="
                    + c + ", fault baseline=" + f + " (need the SAME POSITIVE baseline in both legs;"
                    + " see TrainTicketStimulus#baseFund)");
        }
    }

    private static java.math.BigDecimal parsedBaseline(List<DataIntegrityRuntime.RunRecord> records) {
        if (records == null || records.isEmpty()) {
            return null;
        }
        DataIntegrityRuntime.RunRecord r = records.get(records.size() - 1);
        String userId = r.isolationKey == null ? null : r.isolationKey.get("userId");
        String balance = balanceOf(r.baselineBody, userId);
        try {
            return new java.math.BigDecimal(balance.trim());
        } catch (NumberFormatException e) {
            return null; // <ABSENT>/<no-read> markers land here → the gate throws
        }
    }

    /**
     * Rider-1 claim-eligibility guard (round-2 review C): a MIST tally may feed a G3 claim only
     * when the pair was joined by CORRELATOR with a UNIQUE correlator in each leg. That holds by
     * construction here (one cancel per leg, a leg-shared per-triple correlator) — asserted
     * defensively so a silent regression to a positional join can never feed a claim.
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
     * Evidence-only (does not affect any verdict): prints each leg's buyer /account balance at
     * baseline vs final read-back, so the log shows the value-delta is a real ARITHMETIC delta —
     * a non-zero baseline PRESENT in both legs, moving by the refund only on control — and not an
     * appear-vs-absent MEMBERSHIP signal a response-assertion STATE_GET could also express
     * (head-to-head cold-review A/B; the pre-fund lives in TrainTicketStimulus#baseFund).
     */
    private static void printProbeValues(List<DataIntegrityRuntime.RunRecord> control,
                                         List<DataIntegrityRuntime.RunRecord> fault) {
        System.out.println("  value-delta probe (buyer /account balance, evidence only):");
        System.out.println("      control " + probeLine(control));
        System.out.println("      fault   " + probeLine(fault));
    }

    private static String probeLine(List<DataIntegrityRuntime.RunRecord> records) {
        if (records == null || records.isEmpty()) {
            return "<no record>";
        }
        DataIntegrityRuntime.RunRecord r = records.get(records.size() - 1);
        String userId = r.isolationKey == null ? null : r.isolationKey.get("userId");
        return "baseline=" + balanceOf(r.baselineBody, userId)
                + " -> final=" + balanceOf(r.lastReadbackBody, userId);
    }

    /** Pulls {@code userId}'s balance out of an /account list body, or a marker if absent/unread. */
    private static String balanceOf(String accountBody, String userId) {
        if (accountBody == null) {
            return "<no-read>";
        }
        if (userId == null) {
            return "<no-userId>";
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "\"userId\"\\s*:\\s*\"" + java.util.regex.Pattern.quote(userId)
                        + "\"\\s*,\\s*\"balance\"\\s*:\\s*\"([^\"]*)\"").matcher(accountBody);
        return m.find() ? m.group(1) : "<ABSENT>";
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
        if (!mist.isEmpty()) {
            System.out.println("  claim-eligibility: joinMode=" + mist.get(0).joinMode
                    + ", correlatorUnique=" + mist.get(0).correlatorUnique);
        }
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
     * g3.contract.path, g3.triples.natural, g3.triples.constructed, g3.strata (default
     * "natural,constructed"). Both strata flip inside-payment's drawback fault mode at
     * RUNTIME via {@link HttpToggleFaultInjector} (mode derived from each triple's
     * fault_flag property; no pod restart). The {@link Stimulus} impl is supplied by the
     * live launcher.
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

        // Which strata to run (default both).
        java.util.Set<String> strata = new java.util.HashSet<>(java.util.Arrays.asList(
                System.getProperty("g3.strata", "natural,constructed").toLowerCase().split(",")));

        // ONE injector for both strata: a RUNTIME in-memory toggle of inside-payment's drawback
        // fault mode (natural="fail" → {1,"error"}; constructed="fabricatedack" → clean
        // {1,"Success."}), derived from each triple's fault_flag property. inside-payment is never
        // restarted, so ts-cancel-service's pooled connection + Ribbon routing stay valid — the
        // SutFlag rollout and the EnvoyFilter mesh abort both raced that client-side caching and
        // were unreliable per leg (g3-headtohead-results.md). Reader JWT in case the route is guarded.
        HttpToggleFaultInjector toggle = new HttpToggleFaultInjector(
                baseUrl, io.mist.cli.auth.MstAuthHandler.getDefaultToken());

        if (strata.contains("natural")) {
            TargetTripleRegistry.Triple naturalTriple = natural.triples.get(0);
            harness.runStratum("natural", naturalTriple, contract, toggle, naturalTriple.faultFlag);
        }

        if (strata.contains("constructed")) {
            TargetTripleRegistry.Triple constructedTriple = constructed.triples.get(0);
            harness.runStratum("constructed", constructedTriple, contract, toggle,
                    constructedTriple.faultFlag);
        }

        // The agreement anchor (body-carrying create, both catch — comparator is no strawman)
        // is a separate focused runner: io.mist.cli.g3.AccountCreateAgreement.
    }
}
