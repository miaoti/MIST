# SUT-2 wild-hunt plan v3 — the Sock Shop shipping enqueue-swallow (natural async acked-but-lost)

Status: **v3 — EXECUTION-READY. Two review rounds done, both 3/3.** Round 1 (v1): all three
ACCEPT-WITH-CHANGES → v2. Round 2 (confirming, v2): all three ACCEPT-WITH-CHANGES with only MINOR/fix-on-paper
items, none design-breaking (A: "Execute after folding them in; no further re-review of the plan needed").
v3 folds the round-2 punch-list: S2 primary = verified-feasible queue-policy drop (`overflow: reject-publish`
/ `max-length`), invalid "unbind" removed, permission-revoke demoted to optional secondary; the two S2
narratives pre-registered (broker-drop ≠ the shipped swallow); S1 sharpened (comparator detects the OUTAGE,
not the write-loss — not a "tie"); kill-switch reduces to S1-sufficiency; flaky-stats-DB robustness caveat +
FP0 load-bearing. See `g3-comparator-tt/REVIEW-WILDHUNT-PLAN-RECONCILIATION.md`. v1's "clean structural win"
was refuted (the service's own `GET /health` reflects broker connectivity) → TT-parity **two-stratum** design.
Same loop as TOOL-PLAN vN. **Now executing the §10 step plan.**

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

## 4. Two strata (TT-parity: a natural anchor stratum and a clean-win stratum)
The self-documented swallow only fires on a real broker/connection failure — which is exactly what `/health`
detects. So one cell cannot be both "self-documented" and "invisible to a health-aware comparator." Mirror
the accepted TT design with two strata; the comparator is authored WITH a `/health` liveness clause (maximally
fair to the comparator / adversarial to MIST) and the outcome is reported honestly per stratum. (All three
reviewers verified the S2-decisive facts: shipping publishes via the **default exchange** as **guest/guest**,
the only broker user is `guest [administrator]` `.* .* .*`, and **no policies are set** — so a queue policy is
available and exchange/permission-agnostic.)

- **S1 — natural, self-documented (fault: sever shipping→rabbitmq AMQP 5672, fail-fast abort/RST).**
  `convertAndSend` throws → the swallow fires (confirm the "Accepting anyway…" log + low-latency 201) →
  message lost. `/health` flips to `err`. Expected: MIST FIREs a **per-write** acked-but-lost verdict on the
  specific POST /shipping write; a response-only comparator MISSES entirely; a `/health`-augmented comparator
  detects only the **service-global OUTAGE** (its `/health` clause would fire even with ZERO writes) and never
  asserts that an acked write lost its data. **This is NOT a detection tie** — the comparator detects the
  outage, MIST detects the write-loss. MIST's edge = a per-write durable-effect verdict vs a global liveness
  alarm (framed *qualitatively*: the focused harness runs one write type, so "localization" = kind-of-signal,
  not needle-in-haystack). Mirrors the accepted TT natural stratum (naturalness anchor; makes S2 credible by
  contrast). The write-up must SHOW the two outputs side by side (MIST: "write Wᵢ acked-but-lost" vs
  comparator: "shipping-rabbitmq down"), not merely assert "localization."
- **S2 — clean structural win (fault: a `/health`-GREEN publish loss; NO SUT code change).**
  **PRIMARY (high-confidence, verified feasible): a broker-side silent drop via a queue policy** — `overflow:
  reject-publish` (or `max-length` with the queue pre-filled; **verify `max-length: 0` semantics on 3.6.8 in
  step 2**, as 0 may be rejected / historically mean "unlimited"). Exchange/permission-agnostic (drops at the
  QUEUE regardless of the default-exchange routing), leaves the AMQP connection up → `/health` GREEN, does not
  touch `guest` → mgmt read-back intact, queue stays present → observable readable; the publish "succeeds"
  with no throw → 201, message never lands. This does NOT enter the app's catch — it demonstrates the
  fire-and-forget-**without-publisher-confirms** anti-pattern (the absent confirm is the real bug), **DISTINCT
  from S1's shipped-code swallow; keep the two separate in the write-up** (S2 is not "the self-documented
  bug"). **SECONDARY/OPTIONAL: revoke the `guest` write permission** — only `guest` exists (shared with MIST's
  reader), so revoke WRITE only, keep configure/read + the administrator tag so the mgmt read survives, and the
  write regex must EXCLUDE the empty default-exchange name `""` (e.g. `write=^amq`). This variant MAY throw →
  fire the self-documented swallow (carrying the S1 log flavor); it is fiddly and not required. **Removed:
  "unbind from the default exchange" is INVALID** (the default binding is immutable; the declared
  `shipping-task-exchange` is unused by the publish). Expected either way: 201 + `/health` GREEN + message lost
  → the comparator PASSes both legs **even with the `/health` clause** → **clean MIST win**. Disclose S2 as a
  constructed-but-realistic infra fault (queue overflow-drop / permission misconfig) against unmodified
  shipping — arguably cleaner than TT's constructed cell (no code change).

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
3. **Comparator, per stratum:** S1 → the comparator's `/health` clause flags the service-global OUTAGE (NOT
   the write-loss — it fires even with zero writes), while MIST returns a per-write acked-but-lost verdict;
   report the two OUTPUTS side by side (not "tie"). S2 → clean MIST win (comparator incl `/health` PASSes
   both legs).
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
  MIST's SOLE observable here rides a partially-unhealthy stats DB (`/api/overview` 500; ~5 s sampling) that
  could degrade DURING a run, not only before it → the **FP0 control leg is LOAD-BEARING** to rule out
  read-lag artifacts. Disclosed as a real robustness caveat of the SUT-2 async observable.
- **R6 sufficiency:** a rider generalizing the accepted head-to-head; value is contingent on S2 giving a real
  clean win (verified feasible via the queue-policy drop) and S1 giving an honest per-write-vs-global-liveness
  contrast — both established live, not asserted.

## 9. Framing decision + kill-switch
**Option A — two-stratum head-to-head** (S1 per-write-vs-global-liveness contrast, S2 clean win), blind
comparator incl `/health`, N-stable. Dominates the v1 "single clean win" (refuted) and the "demo-only" option
(wastes the real S2 win). **Kill-switch (Option C):** consolidate the three accepted results instead — nominal
trigger is BOTH (a) no `/health`-green publish loss (S2) is surgically injectable AND (b) the S1 reframe is
judged an insufficient rider. **In practice (a) is pre-satisfied** — all three reviewers verified the
queue-policy drop (`overflow: reject-publish` / `max-length`) is exchange- and permission-agnostic and keeps
`/health` green — so the kill-switch reduces to (b). Named so the hunt cannot open-endedly consume effort.

## 10. Step plan (each step → verify; executed only after this plan is reviewer-accepted)
1. **Read-back channel** — confirm mgmt-API depth read from the host/harness; scale `queue-master`→0; verify
   a manual `curl POST /shipping` raises the mgmt-API depth by exactly 1 AND matches `rabbitmqctl`; measure
   the stats lag; set quiescence ≫ it. Verify overview-500 doesn't affect the per-queue read.
2. **Faults** — S1: author the Envoy abort/RST on egress:5672; verify control(+1)/fault(201,+0), the
   "Accepting anyway…" log, and mgmt API still 200. S2 (PRIMARY): apply a queue policy `overflow:
   reject-publish` (or `max-length` pre-filled; first verify `max-length: 0` is accepted on 3.6.8, else use
   reject-publish); verify it keeps `/health` GREEN AND loses the message (201, depth +0). S2 optional
   secondary = write-only permission-revoke (regex excludes `""`). Ground-truth both via rabbitmqctl; record
   the exact revert for every policy/permission change.
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
