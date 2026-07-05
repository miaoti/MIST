# G3 SUT-2 head-to-head — Sock Shop `shipping` acked-but-lost enqueue

MIST's differential value-delta STATE oracle vs a FROZEN blind response-assertion comparator, on a
SECOND SUT (Sock Shop) and a DIFFERENT integrity hazard class (a lost message-queue enqueue) — the
external-validity complement to the TrainTicket cancel→refund head-to-head.

## The defect (natural, self-documented)
`weaveworksdemos/shipping:0.4.8` `ShippingController.postShipping` wraps `convertAndSend("shipping-task", …)`
in a try/catch that logs *"Accepting anyway. Don't do this for real!"* and returns **HTTP 201 unconditionally**.
When the broker enqueue fails, the client is told the shipment was accepted while the task is silently
dropped — a textbook acked-but-lost write, present in the UNMODIFIED image (no fork flag).

## Two strata (× two comparator forms = dual-form, review B-MAJOR-2)
- **natural** — a surgical shipping↛broker sever: an Istio L4 AuthorizationPolicy denies shipping→rabbitmq:5672
  **plus** a `rabbitmqctl close_all_connections` so the cached connection reconnects INTO the block (verified
  live: `/health` flips `shipping-rabbitmq` to "err" in ~1s, HTTP stays 200). POST still 201 + the enqueue is
  lost. Because `/health` degrades, a liveness-aware comparator CATCHES the outage → a **diagnosis gap** (both
  detect an anomaly; the comparator sees a service-wide outage, MIST localizes the specific lost enqueue).
- **constructed** — a `max-length:1` / `overflow:reject-publish` queue policy: the broker rejects the publish
  (lost) while the connection stays live, so `/health` reads **GREEN**. POST still 201. Response + liveness
  checking is blind to it → the **clean MIST win**, robust to "just add a health check".
- **comparator forms:** (i) **as-frozen** = the blind author's sound output (HTTP_STATUS 201 only; the id/name
  echo, the enqueue effect, and `/health` liveness were all NOT_CHECKABLE in the closed primitive set); (ii)
  **P2-amended** = the `/health` clause bound via the user-chosen `contains-literal-fields` liveness primitive
  (disclosed, pre-run). Reporting both proves the amendment only made MIST's win HARDER, never manufactured it.

## Oracles + config
- **MIST**: value-delta on the `shipping-task` queue depth (RabbitMQ mgmt `GET /api/queues/%2f`, match_field=name,
  value_field=messages), read-back routed off-SUT via `ShippingReadbackHttp` (mist:mist). FIRE = fault leg acks X
  (201) yet its own read-back never shows the +1 while control's does.
- **comparator**: the frozen contract via `ContractEvaluator` (HTTP_STATUS + P2 liveness), `/health` over the SUT.
- queue-master scaled to 0 so depth is monotonic; oracle timeout 20 s (≫ the ~5 s mgmt stats lag); comparator
  retry cap 3 s. kind "mist" cluster; rabbitmq 3.8.34.

## Results — pilot (N=1, run b8cyz1t9m, exit 0); N=5 stability reps in flight

| stratum | comparator form | MIST | comparator | reading |
|---|---|---|---|---|
| natural | P2-amended | **FIRE** | **CAUGHT** (control=F, fault=T) | diagnosis gap — both detect, MIST localizes |
| constructed | P2-amended | **FIRE** | **MISSED** (F, F) | **clean MIST win** — /health green under reject-publish |
| natural | as-frozen | **FIRE** | **MISSED** (F, F) | response-only baseline blind to the outage |
| constructed | as-frozen | **FIRE** | **MISSED** (F, F) | response-only baseline blind to the loss |

Per-cell (depth = shipping-task `messages`, own-baseline value-delta; all four MIST cells FIRE via a unique
correlator join, `body status null`, `TIMEOUT_ABSENT` after ~40 polls):

```
P2  natural     : comparator control=F fault=T -> CAUGHT   depth control 4->5 (present) | fault 5->5 (lost)
P2  constructed : comparator control=F fault=F -> MISSED   depth control 5->6 (present) | fault 1->1 (lost)
frz natural     : comparator control=F fault=F -> MISSED   depth control 1->2 (present) | fault 2->2 (lost)
frz constructed : comparator control=F fault=F -> MISSED   depth control 2->3 (present) | fault 1->1 (lost)
```

## Reading the matrix
- **MIST FIREs in all four** — the value-delta oracle is contract-independent, so it catches the acked-but-lost
  enqueue in BOTH fault mechanisms regardless of which comparator form it is put beside.
- **The as-frozen (sound blind) comparator MISSES BOTH strata** — `HTTP_STATUS 201` passes on every leg because
  the swallow returns 201 unconditionally; a purely response-assertion contract cannot see either loss.
- **The P2-strengthened comparator closes ONLY the natural cell** (to a CAUGHT diagnosis-gap via `/health`
  liveness) and STILL MISSES the constructed cell — so the clean MIST win is **robust to in-class strengthening**
  (adding the strongest liveness check a contract-checker affords does not close it; only out-of-class broker/
  queue-state observation does = MIST's contribution).
- **Dual-form (B-MAJOR-2) discharged:** adding P2 moved the natural cell from MISSED→CAUGHT — i.e. it made MIST's
  comparative story HARDER (turned a would-be clean win into an honest diagnosis-gap), never manufactured a win.

## Framing (reviewer-mandated, applied above)
- **Natural = a diagnosis gap, not a MIST detection win and not a plain tie**: both oracles flag the fault leg,
  but the comparator flags a service-wide `/health` outage while MIST localizes the *specific* lost enqueue (the
  fault-leg queue-depth delta). The comparator flag is a genuine in-body liveness FAIL (`/health` HTTP 200,
  `shipping-rabbitmq=err`), NOT a transport reclassification.
- **Class-scope the constructed win**: "the strongest fair single-endpoint response+liveness contract-checker
  (Pact/Dredd/synthetic-monitoring shape) misses it" — never "no diligent engineer could." The rebuttal "add
  queue-depth monitoring" concedes the thesis (that catching this needs out-of-class broker/queue state = MIST).
- **Protocol, not personhood**: the anti-gaming guarantee is freeze-before-reveal (primary-sources-only, no
  MIST internals, git-frozen before the reveal, P2 written into the frozen notes), not an org-separate human.

## Standing live-confirms (satisfied this run)
- Fault-leg `/health` is HTTP 200 + `shipping-rabbitmq=err` on the natural sever (real detection, not transport).
- Control legs never flag (control-clean by construction; `/health` recovers green before each control leg).
- **CONSTRUCTED /health stays GREEN under reject-publish** (comparator control=F fault=F) — the make-or-break,
  confirmed: shipping's health probe checks connection liveness, not a test-publish.

<!-- N=5 stability summary to be appended from the reps run, then ≥3-cold-review -->
