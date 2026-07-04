# SUT-2 wild-hunt plan v2 — the Sock Shop shipping enqueue-swallow (natural async acked-but-lost)

Status: **v2, reconciled from 3-cold-review of v1** (all three ACCEPT-WITH-CHANGES; see
`g3-comparator-tt/REVIEW-WILDHUNT-PLAN-RECONCILIATION.md`). v1's "clean structural head-to-head win" framing
was refuted (the service's own `GET /health` reflects broker connectivity) and is replaced by a TT-parity
**two-stratum** design. Pending a confirming review round before execution. Same loop as TOOL-PLAN vN.

## 0. Thesis (honest scope — a rider that generalizes the accepted TT head-to-head)
MIST detects a **natural, self-documented async acked-but-lost write** in a standard benchmark microservice
(Sock Shop `shipping`), on a **second SUT** and in a **second write class** — *asynchronous fire-and-forget
enqueue without landing confirmation*. This is a **rider**, not a centerpiece: it generalizes the accepted
TrainTicket result along three axes — sync→async, SUT1→SUT2, constructed→**natural-self-documented**. It is
NOT claimed as "discovering a previously-unknown real bug" (the developer flagged it in a log line; it is an
intentional demo shortcut). The claim is that a real-world **anti-pattern class** — fire-and-forget enqueue
whose success ack is decoupled from whether the write landed — is caught by MIST's durable-effect read-back
and, at the write boundary, is structurally invisible to a response/contract oracle. Ack is **HTTP 201**.

## 1. The defect — DEFINITIVE (deployed `weaveworksdemos/shipping:0.4.8`, decompiled bytecode; 3 reviewers reproduced)
`works.weave.socks.shipping.controllers.ShippingController.postShipping` (`@ResponseStatus(HttpStatus.CREATED)`):
```java
public Shipment postShipping(Shipment shipment) {           // -> HTTP 201
    System.out.println("Adding shipment to queue...");
    try {
        rabbitTemplate.convertAndSend("shipping-task", shipment);   // bc 8–15
    } catch (Exception e) {                                          // exc table 8→18: catch java/lang/Exception
        System.out.println("Unable to add to queue (the queue is probably down). "
            + "Accepting anyway. Don't do this for real!");         // bc 22–27
    }
    return shipment;   // bc 30–31: UNCONDITIONAL (both success goto and catch fall-through)
}
```
`getShipping`/`getShippingById` are pure STUBS (return a literal string; no repository/entity classes exist
in the jar). **BUT** `getHealth()` is NOT a stub: it live-probes rabbitmq (`rabbitTemplate.execute →
getConnection().getServerProperties()`, catches `AmqpException` → `setStatus("err")`) — so `GET /health`
reflects broker connectivity. The write's only durable sink is the queued message.

Impact amplifier (OPTIONAL, UNVERIFIED — not load-bearing): `orders` may persist the returned (fabricated-
ack) `Shipment`, so `GET /orders/{id}` could forever claim shipped. Verify before citing; do not rely on it.

## 2. Environment (verified 2026-07-04 by 3 reviewers; corrections vs v1 folded in)
kind `mist`, ns `sock-shop`, 14/14 Running. `shipping` (image above, port 80, istio-proxy sidecar).
`rabbitmq:3.6.8-management` (mgmt plugin enabled; ports 15672 mgmt + 5672 AMQP; istio-proxy sidecar; a
`rabbitmq-exporter` container). `queue-master` (the consumer; near-no-op; draining live, `consumers:1`).
Queue `shipping-task` exists (`rabbitmqctl list_queues` → `shipping-task 0 0`, non-durable). TT ns empty.
**Read-back reality (corrected):**
- **Working:** `GET http://<rabbitmq>:15672/api/queues/%2f/shipping-task` (guest:guest; `loopback_users=[]`
  so remote reads are permitted) → `messages` (depth). This is the ONLY working HTTP observable.
- **Dead/unusable:** the exporter is on **9419 not 9090** and reports `rabbitmq_module_up=0` for every module
  (no queue metrics). `/api/overview` returns a persistent **HTTP 500** (stats DB partially unhealthy).
  `message_stats.publish` is **absent** from the queue JSON. → DROP exporter + publish-counter as channels.
- **Caveat:** mgmt `messages` is stats-DB-sampled (~5 s in 3.6.8) → lags a just-completed publish.

## 3. Read-back observable (single channel + disclosed scaffold)
- **Channel:** queue depth via the mgmt API (§2). Because a healthy monotonic publish counter is unavailable
  here, use **depth with `queue-master` scaled to 0** so a landed message accumulates (else the consumer
  drains it to 0 on both legs → no signal). Value-delta probe (MIST's existing value_probe mode): control
  POST → depth +1; fault POST → 201 + depth +0.
- **Ground-truth:** cross-check EVERY mgmt-API reading against `rabbitmqctl list_queues` (authoritative, not
  stats-sampled) at read time.
- **Timing:** set MIST's quiescence/poll window ≫ the ~5 s stats-emit interval so a control read never
  observes a not-yet-emitted publish as depth 0 (would be a false FIRE). Validate depth freshness before the
  run (overview-500 is a live risk to the stats subsystem).
- **Disclosures (R1):** (a) depth+consumer-off is a **demo-only measurement scaffold** for the unavailable
  publish counter — a test-harness intervention, not a production path; (b) the queue name / creds / path are
  **out-of-band topology knowledge** configured into the probe, not auto-discovered; (c) the no-op consumer
  is a **degenerate instance** — the queue is here the write's *transport*; in a consumer-with-effect system
  the durable-effect sink MIST reads would be the consumer's state. General capability claimed = "bind the
  read-back to the write's durable-effect sink," with this SUT a degenerate (transport-only) instance.

## 4. Two strata (TT-parity: a natural tie+diagnosis stratum and a clean-win stratum)
The self-documented swallow only fires on a real broker/connection failure — which is exactly what `/health`
detects. So one cell cannot be both "self-documented" and "invisible to a health-aware comparator." Mirror
the accepted TT design with two strata; the comparator is authored WITH a `/health` liveness clause (maximally
fair to the comparator / adversarial to MIST) and the outcome is reported honestly per stratum:

- **S1 — natural, self-documented (fault: sever shipping→rabbitmq AMQP 5672, fail-fast abort/RST).**
  `convertAndSend` throws → the swallow fires (confirm the "Accepting anyway…" log + low-latency 201) →
  message lost. `/health` flips to `err`. Expected: MIST FIREs (acked-but-lost, localized to the specific
  POST /shipping write); a response-only comparator MISSES; a `/health`-augmented comparator DETECTS the
  outage but CANNOT localize the lost write → **detection tie, MIST wins on diagnosis/localization** (mirrors
  the accepted TT natural stratum).
- **S2 — clean structural win (fault: a `/health`-GREEN publish loss).** Candidate injections, selected
  empirically in step 2 (whichever keeps `/health` green AND loses the message): (a) revoke the shipping
  broker user's publish permission (connection stays up → `/health` green; publish denied → lost — test
  whether this throws-then-swallows, carrying the self-documented log, or is a silent channel-close); (b)
  queue policy `max-length=0` or unbind `shipping-task` from the default exchange (publish silently dropped,
  no throw, `/health` green). Expected: 201 + `/health` green + message lost → the comparator PASSes both legs
  **even with the `/health` clause** → **clean MIST win on detection** (mirrors the accepted TT constructed
  stratum). Disclose S2's fault as constructed-but-realistic (broker permission/policy misconfig).

## 5. Fault mechanics + controls
- **S1 fault:** Istio EnvoyFilter/VirtualService **abort/RST** (fail-fast, not black-hole — a silent TCP
  drop can hang the POST instead of returning 201) on shipping's sidecar egress to rabbitmq:5672. Fallback:
  NetworkPolicy egress-deny to 5672 only.
- **Control that the fault does NOT break the read-back (verified feasible):** MIST reads 15672; the fault
  severs 5672 — distinct listeners; confirm the mgmt API answers 200 throughout. Report this explicitly.
- **Fault-leg confirmations:** S1 — the "Accepting anyway…" line in `shipping` logs + POST latency low + 201.
  Both strata — `rabbitmqctl` shows depth unchanged after the acked POST.
- **Negative control (honesty about the scaffold):** one run with `queue-master` RUNNING to document that
  detection depends on the consumer-off scaffold (depth drains to 0 on both legs → no signal) — disclosed,
  not hidden.

## 6. Comparator (blind, TT-parity)
Blind-author (independent author, freeze-before-reveal, exactly as the TT frozen set) a `shipping` contract:
per-write response/contract clauses (HTTP 201; body is a `Shipment` echoing submitted id/name) **plus** a
`/health` liveness clause (`shipping-rabbitmq == OK`). Run both legs of both strata. Expected: S1 — response
clauses PASS both legs (miss); the `/health` clause FAILS the fault leg (detects the outage) but yields no
write-level localization. S2 — all clauses (incl `/health`) PASS both legs (clean miss). Report per stratum;
do not state an unqualified "comparator PASSes both legs."

## 7. Success criteria (verifiable; N-stable like the accepted head-to-head cells)
1. **Defect reproduced end-to-end**, both strata: control POST → depth +1 (ground-truthed via rabbitmqctl);
   fault POST → HTTP 201 + depth +0; S1 additionally shows the "Accepting anyway…" log; S2 keeps `/health`
   green.
2. **MIST FIREs** on both fault legs (acked-but-lost) and is **clean on control** (FP 0 over N, with
   quiescence ≫ the stats interval; no false FIRE from read lag).
3. **Comparator, per stratum:** S1 → detection tie (`/health` flags the outage), MIST wins on
   diagnosis/localization; S2 → clean MIST win (comparator incl `/health` PASSes both legs).
4. N-stable across runs; the consumer-off scaffold + read-lag timing disclosed.
5. **≥3-cold-reviewer ACCEPT** of the plan (this round) and of the result (after the run).

## 8. Honest risks / disclosures (carried into the write-up, not hidden)
- **R1 read-back = SUT-doctoring proxy** (consumer-off; out-of-band topology; no-op-consumer degeneracy) — §3.
- **R2 degeneracy:** the queue is transport, not a business-state destination; scopes the claim to the
  publish/enqueue boundary; does not generalize to consumer-side/end-to-end async.
- **R3 comparator miss is structural only for write-boundary clauses**, not the full API — hence the
  two-stratum design and the explicit `/health` handling (§4/§6).
- **R4 fault naturalness:** S1's trigger (broker/connection down) is exactly the scenario the buggy catch
  names; the fabrication is already in the shipped image (stronger than TT's added flag). S2's fault is
  constructed-but-realistic (disclosed).
- **R5 async + read-lag:** publish is sync-to-broker (disclaimer doesn't fatally bite), but the HTTP depth
  read lags ~5 s → false-FIRE risk on control → mitigated by quiescence ≫ interval + rabbitmqctl ground-truth.
- **R6 sufficiency:** a rider generalizing the accepted head-to-head; value is contingent on S2 giving a real
  clean win and S1 giving an honest diagnosis-gap tie — both established live, not asserted.

## 9. Framing decision + kill-switch
**Option A — two-stratum head-to-head** (S1 tie+diagnosis, S2 clean win), blind comparator incl `/health`,
N-stable. Dominates the v1 "single clean win" (refuted) and the "demo-only" option (wastes the real S2 win).
**Kill-switch (Option C):** consolidate the three accepted results instead — trigger ONLY if BOTH (a) no
`/health`-green publish loss (S2) is surgically injectable on this deployment AND (b) the S1 diagnosis-gap
reframe is judged an insufficient rider. Named so the hunt cannot open-endedly consume effort.

## 10. Step plan (each step → verify; executed only after this plan is reviewer-accepted)
1. **Read-back channel** — confirm mgmt-API depth read from the host/harness; scale `queue-master`→0; verify
   a manual `curl POST /shipping` raises the mgmt-API depth by exactly 1 AND matches `rabbitmqctl`; measure
   the stats lag; set quiescence ≫ it. Verify overview-500 doesn't affect the per-queue read.
2. **Faults** — S1: author the Envoy abort/RST on egress:5672; verify control(+1)/fault(201,+0), the
   "Accepting anyway…" log, and mgmt API still 200. S2: try permission-revoke and/or `max-length=0`; select
   the injection that keeps `/health` green AND loses the message; verify control(+1)/fault(201,+0,/health
   green). Ground-truth both via rabbitmqctl.
3. **MIST wiring** — trace POST /shipping; bind value-delta read-back to the depth observable; quiescence
   gate; paired control/fault per stratum. Verify FP0 control + FIRE fault; N-stability.
4. **Comparator** — blind-author the shipping contract (incl `/health` clause), freeze, run both strata,
   report per-stratum outcomes (§6/§7.3).
5. **(optional) verify + run the `orders` amplifier cell.**
6. **Record + ≥3-cold-review** the result; update FILE_INDEX + memory.

## 11. Non-goals / guardrails
- No MIST tool-code changes unless a wiring gap forces one (then: main_track branch, test, ≥3-review). Reuse
  the reviewed value-delta/quiescence machinery verbatim.
- No file deletion. `queue-master`→0, Istio/NetworkPolicy manifests, and any broker permission/policy change
  are reversible; record + revert. minikube stays stopped; never `wsl --shutdown`.
- Claude is not a commit contributor. Artifacts in English; converse in 中文.
