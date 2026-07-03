package io.mist.cli.g3;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.atomic.AtomicLong;

import static io.restassured.RestAssured.given;

/**
 * Live TrainTicket implementation of {@link CancelRefundHeadToHead.Stimulus}, verified by
 * hand against the deployed SUT (debug/a-main/prep/g3-tt-deploy-progress.md, FIDELITY
 * GATE). Per leg it registers a FRESH buyer (RUNBOOK isolation — each leg's value-delta
 * is against its own /account baseline), PRE-FUNDS the buyer to a non-zero balance (so the
 * value-delta is a real arithmetic delta, not an appear-vs-absent membership signal — see
 * {@link #baseFund}), creates ONE PAID far-future order, and cancels it.
 *
 * <p>Two SUT quirks the recipe pins:
 * <ul>
 *   <li>the order create RESPONSE returns a DIFFERENT id than the persisted one, so the
 *       real id is fetched via {@code POST /order/query} by loginId (a fresh buyer has
 *       exactly one order);</li>
 *   <li>login enforces username 4-20 chars / password &ge;6 (a fork-injected check), so
 *       the generated credentials stay inside those bounds.</li>
 * </ul>
 *
 * <p>{@code RestAssured.baseURI} (the gateway) is set by the runner. The buyer's JWT from
 * login is carried on the returned {@link CancelRefundHeadToHead.Order} for the cancel
 * (and is what the runner also feeds MIST's /account read-back auth).
 */
public final class TrainTicketStimulus implements CancelRefundHeadToHead.Stimulus {

    private static final AtomicLong SEQ = new AtomicLong();

    /**
     * TrainTicket launcher: sets the gateway base URL, registers a fresh per-JVM reader
     * and points MstAuthHandler at it (so MIST's /account read-back carries a JWT), then
     * runs the SUT-agnostic head-to-head engine. All other config is via -Dg3.* (see
     * {@link CancelRefundHeadToHead#run}).
     */
    public static void main(String[] args) throws Exception {
        String baseUrl = System.getProperty("g3.base.url");
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("missing required -Dg3.base.url (the TT gateway)");
        }
        RestAssured.baseURI = baseUrl.trim();

        // A dedicated reader for the value-delta read-back (/account returns ALL balances,
        // so any authenticated identity suffices); per_jvm caches its token across legs.
        String reader = "g3rdr" + (System.currentTimeMillis() % 100000000L);
        String readerPw = "pw" + reader;
        given().contentType("application/json")
                .body(new JSONObject()
                        .put("userName", reader).put("password", readerPw)
                        .put("gender", 1).put("documentType", 1)
                        .put("documentNum", "G3R").put("email", reader + "@g3.test")
                        .toString())
                .post("/api/v1/userservice/users/register");
        System.setProperty("base.url", baseUrl.trim()); // MstAuthHandler composes the login URL from this
        System.setProperty("auth.mode", "per_jvm");
        System.setProperty("auth.login.url", "/api/v1/users/login");
        System.setProperty("auth.login.username", reader);
        System.setProperty("auth.login.password", readerPw);
        // token.json.path (data.token), body template, header/prefix defaults already match TT.
        // Trigger the per_jvm login NOW so the read-back's applyAuth finds a cached token
        // (applyAuth only reads getDefaultToken(); it does not log in on its own).
        if (!io.mist.cli.auth.MstAuthHandler.ensureReady()) {
            throw new IllegalStateException("read-back auth login failed for reader " + reader
                    + " — /account read-back would be unauthenticated");
        }

        CancelRefundHeadToHead.run(new TrainTicketStimulus());
    }

    /** Far-future so calculateRefund never hits the ticket-expired (refund 0) branch. */
    private final String travelDate = "2027-12-01";
    private final String travelTime = "2027-12-01 10:00:00";
    private final String price = "100.0";

    /**
     * Pre-fund each buyer's /account to this NON-ZERO balance before the cancel, so MIST's
     * value-delta read-back discriminates on a real arithmetic delta ({@code baseFund ->
     * baseFund+refund} on the control leg vs an unchanged {@code baseFund} on the fault leg)
     * rather than on the buyer merely APPEARING in /account. A zero-balance fresh buyer is
     * ABSENT from /account at baseline (queryAccount only emits userIds with a Money row), which
     * degenerates the value-delta into an appear-vs-absent MEMBERSHIP signal that a
     * response-assertion STATE_GET could also express — making the constructed "clean win"
     * contestable (head-to-head cold-review A/B). With a non-zero baseline the buyer is PRESENT
     * in both legs, so only the +refund delta — which the closed comparator primitive set cannot
     * compute — distinguishes them.
     */
    private final String baseFund = "50.00";

    @Override
    public CancelRefundHeadToHead.Order createPaidOrder() {
        long n = SEQ.incrementAndGet();
        String suffix = Long.toString(System.currentTimeMillis() % 100000000L) + n; // <=20 chars
        String userName = "g3b" + suffix;
        String password = "pw" + suffix;

        // 1) register a fresh buyer
        Response reg = given().contentType("application/json")
                .body(new JSONObject()
                        .put("userName", userName).put("password", password)
                        .put("gender", 1).put("documentType", 1)
                        .put("documentNum", "G3" + suffix).put("email", userName + "@g3.test")
                        .toString())
                .post("/api/v1/userservice/users/register");
        require(reg, "register", userName);

        // 2) login -> JWT + userId
        Response login = given().contentType("application/json")
                .body(new JSONObject().put("username", userName).put("password", password).toString())
                .post("/api/v1/users/login");
        require(login, "login", userName);
        String token = login.jsonPath().getString("data.token");
        String userId = login.jsonPath().getString("data.userId");
        if (token == null || userId == null) {
            throw new IllegalStateException("login gave no token/userId for " + userName
                    + ": " + login.asString());
        }
        String bearer = "Bearer " + token;

        // 2.5) pre-fund the buyer's /account to a non-zero balance (addMoney, a type-A Money row)
        // BEFORE the cancel, so the value-delta is a genuine arithmetic delta and not a mere
        // appear-vs-absent membership signal (see baseFund). This makes the constructed clean win
        // un-contestable: the buyer is PRESENT in both legs, so only the +refund delta discriminates.
        Response fund = given().header("Authorization", bearer)
                .get("/api/v1/inside_pay_service/inside_payment/" + userId + "/" + baseFund);
        require(fund, "pre-fund addMoney", userName);

        // 3) create a PAID order (status 1); the response id is unreliable (see class doc)
        Response create = given().contentType("application/json").header("Authorization", bearer)
                .body(new JSONObject()
                        .put("accountId", userId).put("status", 1).put("price", price)
                        .put("boughtDate", "2026-01-01 10:00:00")
                        .put("travelDate", travelDate).put("travelTime", travelTime)
                        .put("from", "Shang Hai").put("to", "Su Zhou")
                        .put("trainNumber", "G1234").put("coachNumber", 5)
                        .put("seatClass", 2).put("seatNumber", "5A")
                        .put("contactsName", "G3 Buyer").put("documentType", 1)
                        .put("contactsDocumentNumber", "G3" + suffix)
                        .toString())
                .post("/api/v1/orderservice/order");
        require(create, "order create", userName);

        // 4) fetch the REAL persisted order id (fresh buyer => exactly one order)
        Response query = given().contentType("application/json").header("Authorization", bearer)
                .body(new JSONObject()
                        .put("loginId", userId)
                        .put("enableStateQuery", false).put("enableTravelDateQuery", false)
                        .put("enableBoughtDateQuery", false)
                        .toString())
                .post("/api/v1/orderservice/order/query");
        require(query, "order query", userName);
        String orderId = firstOrderId(query.asString());
        if (orderId == null) {
            throw new IllegalStateException("no order found for fresh buyer " + userId
                    + " after create: " + query.asString());
        }
        return new CancelRefundHeadToHead.Order(orderId, userId, token);
    }

    @Override
    public CancelRefundHeadToHead.Resp cancel(CancelRefundHeadToHead.Order order) {
        Response r = given().header("Authorization", "Bearer " + order.token)
                .get("/api/v1/cancelservice/cancel/" + order.orderId + "/" + order.loginId);
        return new CancelRefundHeadToHead.Resp(r.statusCode(), r.asString());
    }

    private static String firstOrderId(String queryBody) {
        JSONObject env = new JSONObject(queryBody);
        if (!env.has("data") || env.isNull("data")) {
            return null;
        }
        JSONArray data = env.getJSONArray("data");
        return data.isEmpty() ? null : data.getJSONObject(0).getString("id");
    }

    /** A stimulus step must reach the SUT with a 2xx; anything else aborts the leg loudly. */
    private static void require(Response r, String step, String who) {
        if (r.statusCode() / 100 != 2) {
            throw new IllegalStateException("stimulus step '" + step + "' failed for " + who
                    + ": HTTP " + r.statusCode() + " " + r.asString());
        }
    }
}
