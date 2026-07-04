# SUT-2 wild-hunt plan v1 — the Sock Shop shipping enqueue-swallow (natural async acked-but-lost)

Status: **DRAFT for ≥3-cold-review** (same loop as TOOL-PLAN vN). User directive (2026-07-04): the
executable-breadth path was reviewer-rejected as low-ROI → pivot to the wild-hunt, but FIRST settle a
plan, review it, and track it in the manifest (this doc + FILE_INDEX) before any execution.

## 0. One-paragraph thesis (why this is an A-conference-worthy strengthening)
MIST detects a **natural, self-documented, previously-unflagged acked-but-lost-write** in a widely-used
benchmark microservice (Sock Shop `shipping`), on a **second SUT** and in a **second, independent defect
class** — *asynchronous fire-and-forget enqueue-swallow* — complementing the centerpiece TrainTicket
*synchronous* refund-loss. A response/contract oracle sees `200 OK` + a valid `Shipment` echo and PASSes;
MIST, checking the write's actual durable effect, FIREs. This generalizes the head-to-head result across
(SUT, sync/async write pattern, constructed-vs-natural fault) and is the strongest kind of "finds real
bugs" evidence: the fault is not constructed — the shipped code already contains it and **admits it in a
log line**.

## 1. The defect — DEFINITIVE (deployed image `weaveworksdemos/shipping:0.4.8`, decompiled bytecode)
`works.weave.socks.shipping.controllers.ShippingController.postShipping`:
```java
public Shipment postShipping(Shipment shipment) {
    System.out.println("Adding shipment to queue...");
    try {
        rabbitTemplate.convertAndSend("shipping-task", shipment);   // bc 8–15
    } catch (Exception e) {                                          // exc table 8→18: catch java/lang/Exception
        System.out.println("Unable to add to queue (the queue is probably down). "
            + "Accepting anyway. Don't do this for real!");         // bc 22–27
    }
    return shipment;   // bc 30–31: aload_1 areturn — UNCONDITIONAL
}
```
Any exception from the enqueue (broker down, channel/connection error, unroutable) is swallowed; the method
still returns the `Shipment` → HTTP 200 + valid body. The client/caller believes the shipment is accepted;
it was never enqueued → **acked-but-lost**. The `getShippingById` "read-back" endpoint is a STUB (returns
the literal string `"GET Shipping Resource with id: <id>"`), so the service exposes NO app-level state that
reflects the enqueue — the write's only durable effect is the message in the RabbitMQ `shipping-task` queue.

Impact amplifier (to verify, not required for the core result): the `orders` service calls `POST /shipping`
during checkout and persists the returned `Shipment` in `orders-db`. Because it stores the *fabricated-ack*
shipment, `GET /orders/{id}` will forever claim the order shipped — a persistent downstream lie. Note this
makes `orders` state NON-discriminating (it shows "shipped" whether or not the enqueue landed), which is
exactly why the queue is the only discriminating observable (see §3).

## 2. Environment (verified 2026-07-04)
kind `mist`, ns `sock-shop`, 14/14 Running. `shipping` (image above, port 80, Istio sidecar), `rabbitmq`
(container ports 15672 management + 5672 AMQP; sidecar; plus a `rabbitmq-exporter` Prometheus container on
9090), `queue-master` (the consumer; a near-no-op that logs + sleeps). Queue `shipping-task` exists
(`rabbitmqctl list_queues` → `shipping-task 0 0`). TrainTicket ns empty (scaled to 0). Host kubectl +
KUBECONFIG per memory; MSYS_NO_PATHCONV=1 required for `pod:/path` args on Git-Bash.

## 3. The design crux — the read-back observable (this is what review must pressure-test)
MIST's B2 oracle needs a read-back that reflects the write's durable effect. Candidates:
- **(chosen) RabbitMQ `shipping-task` queue depth/publish-count**, read over HTTP via the management API
  (`:15672/api/queues/%2f/shipping-task` → `messages` / `message_stats.publish`) or the Prometheus exporter
  (`:9090/metrics` → `rabbitmq_queue_messages*`). To make depth a monotonic record of successful publishes,
  **scale `queue-master` to 0** (stop the consumer; reversible) so a landed message accumulates instead of
  being drained. Value-delta read-back (MIST's existing value_probe mode): control POST → depth/publishes
  +1; fault POST → 200 but delta 0 → FIRE.
- `orders` state — REJECTED: non-discriminating (stores the fabricated ack; see §1).
- consumer (queue-master) durable effect — NONE in this demo (it only logs); this degeneracy is a
  disclosed limitation (see §6-R2).

**Fault design:** sever `shipping → rabbitmq` on the AMQP port (5672) while keeping the broker AND its
read-back API (15672/9090) UP — so `convertAndSend` throws (triggering the buggy catch) but the observable
stays readable. Both pods have sidecars → primary = an Istio EnvoyFilter/VirtualService that aborts/denies
the 5672 route from shipping's sidecar; fallback = a NetworkPolicy blocking egress to 5672 only. Verify the
fault is surgical: control `POST /shipping` → queue depth +1; fault `POST /shipping` → HTTP 200 **and**
depth unchanged; management/exporter API still 200 throughout.

## 4. The comparison (MIST vs the response/contract comparator)
- **MIST:** trace the `POST /shipping` write; bind a value-delta read-back to the queue observable;
  quiescence-gate; paired control/fault via the existing executor. Expected: control clean (FP 0), fault
  FIRE (acked-but-lost).
- **Comparator (frozen response/contract oracle):** author a shipping contract the way a reasonable
  operator would from the API — response clauses (HTTP 200, body is a `Shipment` echoing the submitted
  id/name). It has no queue clause (the queue is not in the service's API). Expected: **PASS on both legs**
  (the 200 + echo is valid) → misses the loss. This is the head-to-head win, and it is *structural*: the
  comparator checks the declared contract; MIST checks the write's real effect, which for a fire-and-forget
  write lives outside the declared API.

## 5. Success criteria (verifiable; N-stable like the head-to-head cells)
1. **Defect reproduced end-to-end:** control POST → queue delta +1; fault POST → HTTP 200 + queue delta 0
   (ground-truthed independently via `rabbitmqctl`, not only via MIST's read-back).
2. **MIST FIREs** on the fault leg (acked-but-lost) and is **clean on control** (FP 0 over N runs).
3. **Comparator PASSes** on both legs (misses) — a clean, structural MIST win.
4. Optional impact cell: `orders` checkout under the fault → `GET /orders/{id}` claims shipped while the
   queue never received it (persistent-lie amplifier).
5. **≥3-cold-reviewer ACCEPT** of both the plan (now) and the result (after the run).

## 6. Honest risks / threats the review must adjudicate
- **R1 read-back fairness (make-or-break).** The read-back is the broker's queue, not the app's own HTTP
  surface. Defense: MIST binds a read-back to wherever the write's durable effect lands; for a fire-and-
  forget write that sink IS the queue; the binding mechanism is the same value_probe used on TT. The
  comparator can't do this because its contract is authored from the service API, which by construction
  excludes the broker — that's the point, not a cheat. **But**: is configuring a queue read-back something a
  real MIST deployment does, or am I hand-building a detector? Must be argued, not hand-waved.
- **R2 observable degeneracy.** Sock Shop's consumer is a no-op, so the queue is the *only* discriminating
  observable. In a realistic system the consumer would have a durable effect and MIST would read *that*.
  Disclose as a limitation; do not imply the queue read-back is the general case.
- **R3 comparator-contract fairness.** I author the comparator's shipping contract. A skeptic will ask
  whether a diligent author *could* have written a state clause that catches this. Argument: no clause over
  the service's own API can (getShippingById is a stub; the response is a valid echo) — so the miss is
  structural, not a strawman. The contract must be authored blind/faithfully (independent author, freeze
  before reveal) exactly as the TT frozen set was, or the win is not credible.
- **R4 fault naturalness.** I inject the AMQP-sever fault. Defense: the code's own catch comment ("the queue
  is probably down") names this exact scenario — the fault is the *natural trigger the buggy code
  anticipates*, and unlike TT (where I added a fabricated-ack flag) the fabrication is ALREADY in the
  shipped image. This is a *stronger* naturalness story than TT's constructed cell.
- **R5 async + MIST's async disclaimer.** This write is async; MIST's prior async handling carried a
  disclaimer. Must show the quiescence-gated value-delta read-back handles it soundly (the publish is
  synchronous-to-the-broker even though delivery is async; the observable is the publish, which is
  well-defined at ack time) — or scope the claim accordingly.
- **R6 contribution sufficiency.** One more defect on one more SUT is a STRENGTHENING rider, not a new
  centerpiece. It must be framed as generalizing the accepted head-to-head (sync→async, SUT1→SUT2,
  constructed→natural-self-documented), alongside the accepted survey + FP=0. Reviewers should judge whether
  that materially raises the contribution or is marginal.

## 7. Framing options (pick one; review + user to confirm)
- **Option A — full head-to-head (recommended):** 3 cells (control FP0 / fault MIST-FIRE+comparator-miss /
  optional orders-impact), blind-authored comparator contract, N-stable. Mirrors the accepted TT head-to-
  head → slots directly into the paper as the async/2nd-SUT generalization. Highest value; most work (blind
  contract + comparator run).
- **Option B — MIST-detects-natural-async-loss demonstration (lighter):** show MIST FIRE + FP0 + the
  self-documented defect, with the comparator argued analytically (not run). Lower cost; weaker (no live
  comparator leg).
- **Option C — abandon if a blocker survives review** (e.g., no surgical fault keeps the read-back
  readable, or R1 fairness fails): fall back to consolidating the three accepted results. The plan names
  its own kill-switch so the wild-hunt cannot open-endedly consume effort.

## 8. Methodology / step plan (each step → verify; executed only after the plan is reviewer-accepted)
1. **Read-back channel** → confirm management API (`:15672`) creds/plugin OR exporter (`:9090`) exposes
   `shipping-task` depth+publishes over HTTP; scale `queue-master`→0; verify depth is monotonic under manual
   POSTs. Verify: a curl POST /shipping raises the HTTP-read depth by exactly 1.
2. **Surgical fault** → author the Istio AMQP-sever (or NetworkPolicy); verify control(+1)/fault(200,+0) and
   read-back API stays 200. Ground-truth with `rabbitmqctl` in parallel.
3. **MIST wiring** → trace POST /shipping; bind value-delta read-back to the queue observable; quiescence
   gate; paired control/fault run. Verify FP0 control + FIRE fault; N-stability.
4. **Comparator** → blind-author the shipping response contract (independent author, freeze-before-reveal),
   run both legs, confirm PASS/PASS (miss). Verify the miss is structural (no over-the-API clause catches).
5. **(optional) orders-impact** cell.
6. **Record + ≥3-cold-review** the result; update FILE_INDEX + memory.

## 9. Non-goals / guardrails
- No MIST tool-code changes unless a wiring gap forces one (then: main_track branch, test, ≥3-review — the
  standing rules). Prefer reusing the reviewed value-delta/quiescence machinery verbatim.
- No file deletion. queue-master scale-to-0 and Istio/NetworkPolicy manifests are reversible; record the
  revert. minikube stays stopped; never `wsl --shutdown`.
- Claude is not a commit contributor. Artifacts in English; converse in 中文.
