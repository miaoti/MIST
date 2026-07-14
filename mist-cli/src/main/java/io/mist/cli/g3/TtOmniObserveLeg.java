package io.mist.cli.g3;

import io.mist.cli.fault.DataIntegrityRuntime;
import io.mist.cli.fault.FaultInjector;
import io.mist.cli.fault.HttpToggleFaultInjector;
import io.mist.cli.fault.TargetTripleRegistry;
import io.restassured.RestAssured;

import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

/**
 * TT-OMNIBUS leg 1 — OBSERVE-mode live run on the traced cancel-refund pair
 * (wave-tt-omnibus-plan.md rev 2.1 §1-1; frozen protocol ttomni-phase0-protocol.md (a)).
 *
 * <p>The ONE wiring change vs the G3 paired harness: {@link CancelRefundHeadToHead#runLeg}
 * passes {@code afterWrite(..., null)} BY DESIGN (its read-back stays timeout-gated), so no
 * run of record has ever reached the trace-complete defect tier. This runner hands the
 * runtime the REAL client-injected W3C trace-id (already minted by
 * {@link TrainTicketStimulus#cancel}), so with {@code -Djaeger.base.url} set the observe
 * gate can evaluate {@code traceComplete()} and the {@code OBSERVED_COMPLETE_ABSENT} tier
 * becomes reachable for the first time. Whether it FIRES or ABSTAINS on the fabricated-ack
 * leg is the leg's headline measurement — reported EITHER WAY (stop rule: no tuning).
 *
 * <p>Config (-D): {@code g3.base.url} (gateway), {@code g3.triples.constructed},
 * {@code ttomni.leg=control|fault} (control first), {@code ttomni.n} (default 5),
 * {@code jaeger.base.url} (the trace gate; its absence is E5 config C2). The fabricatedack
 * fault mode is toggled at RUNTIME via {@link HttpToggleFaultInjector} (no pod restart);
 * leg=fault toggles it ON before its writes and clears it after.
 */
public final class TtOmniObserveLeg {

    private static String required(String key) {
        String v = System.getProperty(key);
        if (v == null || v.trim().isEmpty()) {
            throw new IllegalArgumentException("missing required -D" + key);
        }
        return v.trim();
    }

    public static void main(String[] args) throws Exception {
        String baseUrl = required("g3.base.url");
        String leg = System.getProperty("ttomni.leg", "control");
        int n = Integer.getInteger("ttomni.n", 5);
        RestAssured.baseURI = baseUrl;
        System.setProperty("base.url", baseUrl);
        System.setProperty(FaultInjector.ENABLED_PROPERTY, "true");
        System.setProperty("mst.test.parallelism", "1");
        System.out.println("[ttomni] leg=" + leg + " n=" + n
                + " jaeger.base.url=" + System.getProperty("jaeger.base.url", "(unset)"));

        // Reader auth for the /account value-delta read-back + the runtime toggle
        // (verbatim TrainTicketStimulus.main: fresh reader, per_jvm login, ensureReady).
        String reader = "ttor" + (System.currentTimeMillis() % 100000000L);
        String readerPw = "pw" + reader;
        io.restassured.RestAssured.given().contentType("application/json")
                .body(new org.json.JSONObject()
                        .put("userName", reader).put("password", readerPw)
                        .put("gender", 1).put("documentType", 1)
                        .put("documentNum", "TTO").put("email", reader + "@ttomni.test")
                        .toString())
                .post("/api/v1/userservice/users/register");
        System.setProperty("auth.mode", "per_jvm");
        System.setProperty("auth.login.url", "/api/v1/users/login");
        System.setProperty("auth.login.username", reader);
        System.setProperty("auth.login.password", readerPw);
        if (!io.mist.cli.auth.MstAuthHandler.ensureReady()) {
            throw new IllegalStateException("reader auth login failed for " + reader);
        }

        TargetTripleRegistry.Registry reg =
                TargetTripleRegistry.load(Paths.get(required("g3.triples.constructed")));
        TargetTripleRegistry.Triple triple = reg.triples.get(0);

        HttpToggleFaultInjector toggle = new HttpToggleFaultInjector(
                baseUrl, io.mist.cli.auth.MstAuthHandler.getDefaultToken());
        toggle.clear(triple.faultFlag); // hygiene: start clean (flushes any stale fault)
        if ("fault".equals(leg)) {
            toggle.inject(triple.faultFlag);
        }

        TrainTicketStimulus stim = new TrainTicketStimulus();
        List<DataIntegrityRuntime.RunRecord> records;
        DataIntegrityRuntime.beginObserveRun(Collections.singletonList(triple),
                "ttomni-leg1-observe-" + leg);
        try {
            for (int i = 1; i <= n; i++) {
                CancelRefundHeadToHead.Order order = stim.createPaidOrder();
                // one write per iteration -> per-write correlator (no cross-leg pairing in observe)
                String corr = triple.name + "#cancel-" + leg + "-" + i;
                DataIntegrityRuntime.beforeWriteSupplied(triple.writeEndpoint, corr, null,
                        "userId", order.loginId);
                CancelRefundHeadToHead.Resp cancel = stim.cancel(order);
                // THE re-wiring: pass the REAL client trace-id (G3 passes null by design).
                DataIntegrityRuntime.afterWrite(triple.writeEndpoint, corr, cancel.status,
                        cancel.body, cancel.traceId);
                System.out.println("[ttomni] run " + i + "/" + n + " leg=" + leg
                        + " ack=" + cancel.status + " traceId=" + cancel.traceId);
            }
        } finally {
            if ("fault".equals(leg)) {
                toggle.clear(triple.faultFlag);
            }
            records = DataIntegrityRuntime.endRun();
        }

        System.out.println("[ttomni] ===== observe RunRecords leg=" + leg + " (" + records.size() + ") =====");
        for (DataIntegrityRuntime.RunRecord r : records) {
            System.out.println("[ttomni] step=" + r.stepKey
                    + " acked=" + r.acked
                    + " ackHttp=" + r.ackHttpStatus + "/" + r.ackBodyStatus
                    + " baselineContainedX=" + r.baselineContainedX
                    + " readbackContainedX=" + r.readbackContainedX
                    + " gate=" + r.gate
                    + " polls=" + r.polls
                    + " elapsedMs=" + r.elapsedMs);
        }
    }
}
