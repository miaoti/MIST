package io.mist.cli.s3;

import io.mist.cli.auth.MstAuthHandler;
import io.mist.cli.fault.DataIntegrityRuntime;
import io.mist.cli.fault.TargetTripleRegistry;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.json.JSONObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static io.restassured.RestAssured.given;

/**
 * S3 wild-hunt window runner — TrainTicket (plan rev 2.1 §6 P3, MANDATORY: the only nonzero-prior +
 * sync-acked surface). Binds the 2-3 ADDITIONAL admin-basic write endpoints (station / config /
 * price creates — {@code trainticket-adminbasic-triples.yaml}) as SUPPLIED + MEMBERSHIP triples over
 * the EXISTING {@code installHttpOverride}/RestAssured mechanics — NO new detector code:
 *
 * <ul>
 *   <li><b>Write</b>: RestAssured POST with an admin bearer (from {@link MstAuthHandler}), the neutral
 *       marker supplied in the triple's keyed create field.</li>
 *   <li><b>Read-back</b>: the built-in {@code RestAssuredHttp} (override left null) GETs the triple's
 *       collection and — via {@code MstAuthHandler.applyAuth} — carries the SAME admin bearer, so the
 *       reviewed MEMBERSHIP oracle checks {@code {<key>: <marker>}} membership. Both the at-cap poll
 *       and the >=T+5min re-probe use this transport (the runner passes a null re-probe transport,
 *       which {@link DataIntegrityRuntime#reProbe} resolves to {@code RestAssuredHttp} exactly as the
 *       observe run does).</li>
 * </ul>
 *
 * <p>An acked create absent from its collection at cap is the RAW candidate; a still-absent re-probe
 * is CONFIRMED. Journeys round-robin across ALL bound endpoints (plan §6). Paths, admin creds, and
 * the create-body schemas are the pre-registered candidates VERIFIED at the P3 preflight (this file
 * is the frozen skeleton; the preflight resolves the price/trainType fallback if needed). Modes:
 * {@code s3.mode=calibration|window}.
 */
public final class TrainTicketWildHunt {

    public static void main(String[] args) throws Exception {
        String base = req("g3.base.url");          // TT gateway, e.g. http://localhost:8080
        String adminUser = System.getProperty("s3.admin.user", "admin");
        String adminPass = System.getProperty("s3.admin.pass", "222222"); // P3-verified
        String mode = System.getProperty("s3.mode", "calibration");
        int journeys = Integer.getInteger("s3.journeys", "calibration".equals(mode) ? 20 : 100);
        Path outDir = Paths.get(System.getProperty("s3.out",
                "debug/a-main/benchmark/b4/s3/" + mode + "-trainticket"));
        long reProbeMs = Long.getLong("s3.reprobe.ms", 300_000L);
        // Freeze-row knobs (pin 2/§2): TT/TeaStore timeout 10 s, poll 500 ms.
        System.setProperty(DataIntegrityRuntime.TIMEOUT_MS_PROPERTY, "10000");
        System.setProperty(DataIntegrityRuntime.POLL_MS_PROPERTY, "500");
        System.setProperty("mst.test.parallelism", "1");

        RestAssured.baseURI = base;
        // Admin bearer for the create writes AND (via MstAuthHandler) the built-in read-back transport.
        System.setProperty("base.url", base);
        System.setProperty("auth.mode", "per_jvm");
        System.setProperty("auth.login.url", "/api/v1/users/login");
        System.setProperty("auth.login.username", adminUser);
        System.setProperty("auth.login.password", adminPass);
        if (!MstAuthHandler.ensureReady()) {
            throw new IllegalStateException("admin read-back auth login failed for " + adminUser
                    + " — the admin-basic read-back would be unauthenticated");
        }
        String bearer = "Bearer " + MstAuthHandler.getDefaultToken();

        TargetTripleRegistry.Registry reg = TargetTripleRegistry.load(Paths.get(System.getProperty(
                "s3.triples", "evaluation/suts/trainticket/triples/trainticket-adminbasic-triples.yaml")));

        // TT admin-basic writes are UNIQUE-KEYED (station.name/id, config.name, price.trainType), so a
        // FIXED marker seed re-generates a prior run's markers and the store rejects the duplicate
        // (HTTP 200 {status:0} → not-acked). Salt the pinned base seed with a per-run nonce so every
        // run's markers are globally unique against the persistent store. The ban-free grammar
        // `corpus-w<seq>-<12hex>` is unchanged (unit-tested); marker values are scientifically opaque
        // (the assembler rebases/opaquifies). Disclosed in s3-p0-pins.md §1/§3. (OTel/TeaStore writes
        // are NOT unique-keyed — re-runs simply add rows — so only TT needs the salt.)
        long baseSeed = Long.getLong("s3.marker.seed", 20260713L);
        long effSeed = baseSeed ^ System.nanoTime();
        System.out.println("marker seed: base=" + baseSeed + " effective=" + effSeed + " (per-run salted)");
        WildHuntEngine engine = new WildHuntEngine("trainticket", mode + "-trainticket", outDir,
                reProbeMs, effSeed, null);
        // TT's gateway (Spring Cloud Gateway + Sentinel) rate-limits bursts (429s corrupt read-backs).
        // Pace the workload to stay under it — pure inter-journey pacing, NOT an observation-cadence
        // knob (poll/re-probe are unchanged), so cross-strata cadence uniformity holds. Default 800 ms.
        engine.setJourneyDelayMs(Long.getLong("s3.journey.delay.ms", 800L));
        for (TargetTripleRegistry.Triple t : reg.triples) {
            engine.setProbe(t.name, t.readbackEndpoint); // per-triple neutral read-back descriptor
        }
        Files.createDirectories(outDir);

        List<WildHuntEngine.Journey> js = new ArrayList<>();
        for (int i = 0; i < journeys; i++) {
            final TargetTripleRegistry.Triple t = reg.triples.get(i % reg.triples.size());
            js.add(ctx -> journey(ctx, t, bearer));
        }
        try {
            DataIntegrityRuntime.installHttpOverride(null); // built-in RestAssuredHttp (admin-authed)
            engine.runWindow(reg.triples, js, null, null);  // null => RestAssuredHttp for re-probes too
        } finally {
            DataIntegrityRuntime.installHttpOverride(null);
        }
        engine.emit(System.getProperty("s3.mist.commit", "unpinned"),
                "train-ticket @ MIST-trainticket branch (k8s/minikube deploy)",
                new JSONObject().put("gateway", base).put("bound_endpoints", reg.triples.size())
                        .put("admin_auth", "MstAuthHandler per_jvm")
                        .put("marker_seed_base", baseSeed).put("marker_seed_effective", effSeed));
        System.out.println("window done: see " + outDir.resolve("window-log.json"));
    }

    /** One journey: GET the collection (read step) -> admin create with the marker (the bound write). */
    private static void journey(WildHuntEngine.JourneyContext ctx, TargetTripleRegistry.Triple t,
                                String bearer) throws Exception {
        String getPath = t.readbackEndpoint.replaceFirst("^GET ", "");
        ctx.recorder.request("GET", getPath, null);
        Response before = given().header("Authorization", bearer).get(getPath);
        ctx.recorder.response(before.statusCode(), "<collection elided>");
        ctx.countRead();

        String marker = ctx.nextMarker();
        String keyField = t.isolationKey.get(0);
        String postPath = t.writeEndpoint.replaceFirst("^POST ", "");
        String body = createBody(t.name, keyField, marker);
        ctx.recorder.request("POST", postPath, body);
        ctx.write(t, keyField, marker, () -> {
            Response r = given().contentType("application/json").header("Authorization", bearer)
                    .body(body).post(postPath);
            ctx.recorder.response(r.statusCode(), r.asString());
            return new WildHuntEngine.Ack(r.statusCode(), r.asString());
        });
    }

    /**
     * Per-site create body with the marker in the keyed field (standard TT admin-basic schemas;
     * P3-verified). Non-marker fields are neutral placeholders (never tell-bearing vocabulary — the
     * only project string is the marker itself, which is the pre-registered ban-free grammar).
     */
    private static String createBody(String tripleName, String keyField, String marker) {
        JSONObject b = new JSONObject();
        if (tripleName.endsWith("station")) {
            b.put("id", marker).put("name", marker).put("stayTime", 10);
        } else if (tripleName.endsWith("config")) {
            b.put("name", marker).put("value", "1").put("description", "config");
        } else if (tripleName.endsWith("price")) {
            b.put("id", marker).put("trainType", marker).put("routeId", "route")
                    .put("basicPriceRate", 1.0).put("firstClassPriceRate", 1.0);
        } else {
            b.put(keyField, marker); // generic fallback (P3 adds the site's required fields)
        }
        return b.toString();
    }

    private static String req(String k) {
        String v = System.getProperty(k);
        if (v == null || v.trim().isEmpty()) {
            throw new IllegalArgumentException("missing -D" + k);
        }
        return v.trim();
    }
}
