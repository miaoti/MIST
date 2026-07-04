package io.mist.cli.g3;

import io.mist.cli.comparator.AssertionBindings;
import io.mist.cli.comparator.ContractEvaluator;
import io.mist.cli.comparator.RestAssuredSutClient;
import io.mist.cli.fault.DataIntegrityRuntime;
import io.mist.cli.fault.PairedFaultExecutor;
import io.mist.cli.fault.TargetTripleRegistry;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.json.JSONObject;

import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static io.restassured.RestAssured.given;

/**
 * G3 head-to-head — AGREEMENT anchor (fairness / anti-strawman). Same SUT + service as the
 * cancel→refund cells (ts-inside-payment-service), but the write is a BODY-CARRYING create
 * (POST /inside_payment/account {userId, money}). Because the body carries userId, the frozen
 * comparator's STATE_GET binds (submitted-field membership on /account) and CATCHES a lost create —
 * so BOTH oracles flag (agreement). This demonstrates the comparator is not defined to lose on the
 * head-to-head: it misses the cancel→refund clean win specifically because that write is bodyless
 * and the refund is a numeric delta, not because its STATE machinery cannot catch a lost write.
 *
 * <p>Fault = the fork's createAccount fabricated-ack (createAccountFaultMode), toggled at RUNTIME via
 * {@code …/inside_payment/test/createfaultmode/{none|fabricatedack}}. MEMBERSHIP read-back: the
 * submitted userId appears in /account iff the create persisted. Per leg a FRESH buyer (absent at
 * baseline, present iff persisted). Config via -Dg3.* (base.url, contract.path, triple).
 */
public final class AccountCreateAgreement {

    private static final AtomicLong SEQ = new AtomicLong();
    private static final String MONEY = "50";

    private final ContractEvaluator.SutClient sutClient = new RestAssuredSutClient();
    private final AssertionBindings.BoundEndpoint contract;
    private final TargetTripleRegistry.Triple triple;
    private final String toggleBase; // gateway base for the createfaultmode toggle
    private final String readerToken;

    private AccountCreateAgreement(AssertionBindings.BoundEndpoint contract,
                                   TargetTripleRegistry.Triple triple, String toggleBase, String readerToken) {
        this.contract = contract;
        this.triple = triple;
        this.toggleBase = toggleBase;
        this.readerToken = readerToken;
    }

    public static void main(String[] args) throws Exception {
        String baseUrl = System.getProperty("g3.base.url");
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("missing required -Dg3.base.url (the TT gateway)");
        }
        RestAssured.baseURI = baseUrl.trim();

        // A per-JVM reader for the /account read-back auth (same recipe as TrainTicketStimulus).
        String reader = "g3ard" + (System.currentTimeMillis() % 100000000L);
        String readerPw = "pw" + reader;
        given().contentType("application/json")
                .body(new JSONObject().put("userName", reader).put("password", readerPw)
                        .put("gender", 1).put("documentType", 1)
                        .put("documentNum", "G3A").put("email", reader + "@g3.test").toString())
                .post("/api/v1/userservice/users/register");
        System.setProperty("base.url", baseUrl.trim());
        System.setProperty("auth.mode", "per_jvm");
        System.setProperty("auth.login.url", "/api/v1/users/login");
        System.setProperty("auth.login.username", reader);
        System.setProperty("auth.login.password", readerPw);
        if (!io.mist.cli.auth.MstAuthHandler.ensureReady()) {
            throw new IllegalStateException("read-back auth login failed for reader " + reader);
        }

        AssertionBindings.Bindings bindings =
                AssertionBindings.load(Paths.get(required("g3.contract.path")));
        AssertionBindings.BoundEndpoint contract = bindings.endpoints.get(0);
        TargetTripleRegistry.Registry cfg =
                TargetTripleRegistry.load(Paths.get(required("g3.triples.agreement")));
        TargetTripleRegistry.Triple triple = cfg.triples.get(0);

        new AccountCreateAgreement(contract, triple, baseUrl.trim(),
                io.mist.cli.auth.MstAuthHandler.getDefaultToken()).run();
    }

    private void run() throws Exception {
        toggleCreateFault("none");
        LegOutcome control = runLeg("agreement-control");
        toggleCreateFault("fabricatedack");
        LegOutcome fault;
        try {
            fault = runLeg("agreement-fault");
        } finally {
            toggleCreateFault("none"); // never leave the SUT faulted
        }
        List<PairedFaultExecutor.PairResult> mist = PairedFaultExecutor.evaluate(
                Collections.singletonList(triple), control.records, fault.records);
        CancelRefundHeadToHead.requireClaimEligible(mist); // rider-1 guard (round-2 review C)

        String mistVerdict = mist.isEmpty() ? "NO_RESULT" : mist.get(0).pureDifferential.name();
        String mistReason = mist.isEmpty() ? "" : mist.get(0).reason;
        System.out.println("\n=== stratum: agreement (body-carrying create) ===");
        System.out.println("  MIST B2 (membership): " + mistVerdict);
        System.out.println("      " + mistReason);
        System.out.println("  Comparator (STATE_GET binds): control flagged=" + control.comparator.flagged
                + ", fault flagged=" + fault.comparator.flagged
                + "  -> " + (fault.comparator.flagged ? "CAUGHT" : "MISSED")
                + (control.comparator.flagged ? " (control also flagged — verify)" : ""));
        if (!mist.isEmpty()) {
            System.out.println("  claim-eligibility: joinMode=" + mist.get(0).joinMode
                    + ", correlatorUnique=" + mist.get(0).correlatorUnique);
        }
        boolean agreement = "FIRE".equals(mistVerdict) && fault.comparator.flagged
                && !control.comparator.flagged;
        System.out.println("  => " + (agreement ? "AGREEMENT (both catch — comparator is no strawman)"
                : "NOT the expected agreement — investigate"));
    }

    /** One leg: register a fresh buyer, create the account, feed BOTH oracles the same result. */
    private LegOutcome runLeg(String leg) {
        DataIntegrityRuntime.beginRun(Collections.singletonList(triple), leg);
        ContractEvaluator.EndpointOutcome comparator;
        List<DataIntegrityRuntime.RunRecord> records;
        try {
            String[] buyer = registerBuyer(); // [userId, token]
            String userId = buyer[0];
            String token = buyer[1];
            String corr = triple.name + "#create";
            DataIntegrityRuntime.beforeWriteSupplied(triple.writeEndpoint, corr, null, "userId", userId);
            Response create = given().contentType("application/json").header("Authorization", "Bearer " + token)
                    .body(new JSONObject().put("userId", userId).put("money", MONEY).toString())
                    .post("/api/v1/inside_pay_service/inside_payment/account");
            DataIntegrityRuntime.afterWrite(triple.writeEndpoint, corr, create.statusCode(),
                    create.asString(), null);
            String submitted = new JSONObject().put("userId", userId).put("money", MONEY).toString();
            comparator = ContractEvaluator.evaluate(contract, leg, submitted,
                    new ContractEvaluator.Response(create.statusCode(), create.asString()), sutClient);
        } finally {
            records = DataIntegrityRuntime.endRun();
        }
        return new LegOutcome(records, comparator);
    }

    private String[] registerBuyer() {
        long n = SEQ.incrementAndGet();
        String suffix = Long.toString(System.currentTimeMillis() % 100000000L) + n;
        String userName = "g3a" + suffix;
        String password = "pw" + suffix;
        given().contentType("application/json")
                .body(new JSONObject().put("userName", userName).put("password", password)
                        .put("gender", 1).put("documentType", 1)
                        .put("documentNum", "G3" + suffix).put("email", userName + "@g3.test").toString())
                .post("/api/v1/userservice/users/register");
        Response login = given().contentType("application/json")
                .body(new JSONObject().put("username", userName).put("password", password).toString())
                .post("/api/v1/users/login");
        String token = login.jsonPath().getString("data.token");
        String userId = login.jsonPath().getString("data.userId");
        if (token == null || userId == null) {
            throw new IllegalStateException("login gave no token/userId for " + userName + ": " + login.asString());
        }
        return new String[]{userId, token};
    }

    /** Runtime toggle of the createAccount fabricated-ack fault (no pod restart). */
    private void toggleCreateFault(String mode) {
        Response r = given().header("Authorization", "Bearer " + readerToken)
                .get(toggleBase + "/api/v1/inside_pay_service/inside_payment/test/createfaultmode/" + mode);
        if (r.statusCode() / 100 != 2) {
            throw new IllegalStateException("createfaultmode toggle to '" + mode + "' failed: HTTP "
                    + r.statusCode() + " " + r.asString());
        }
    }

    private static String required(String key) {
        String v = System.getProperty(key);
        if (v == null || v.trim().isEmpty()) {
            throw new IllegalArgumentException("missing required -D" + key);
        }
        return v.trim();
    }

    private static final class LegOutcome {
        final List<DataIntegrityRuntime.RunRecord> records;
        final ContractEvaluator.EndpointOutcome comparator;
        LegOutcome(List<DataIntegrityRuntime.RunRecord> records, ContractEvaluator.EndpointOutcome comparator) {
            this.records = records;
            this.comparator = comparator;
        }
    }
}
