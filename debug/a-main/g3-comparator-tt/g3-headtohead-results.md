# G3 cancel→refund head-to-head — live results

MIST's B2 differential value-delta oracle vs the FROZEN blind response-assertion
comparator (`blind-cancel-refund-contract.yaml` @ 38e7aa6 → executable
`assertion-bindings-cancel-refund.yaml`), both observing the SAME cancel→refund
stimulus under two fault strata. Runner: `io.mist.cli.g3.CancelRefundHeadToHead`
+ `TrainTicketStimulus`. Design: `prep/g3-headtohead-run-architecture.md`.

Status: **PRELIMINARY** — records runs as they land. NOT yet reviewer-accepted;
the standing ≥3-cold-review gates these numbers before they feed any claim.

## Both cells (run #1, 2026-07-03, log `tmp/g3-toggle1.log`)

| stratum | fault → cancel response | MIST B2 (value-delta) | Comparator (frozen contract) |
|---|---|---|---|
| **natural** | drawBack throws → `{1,"error"}` | **FIRE** | **CAUGHT** (fault flagged) |
| **constructed** | fabricated-ack → `{1,"Success."}` | **FIRE** | **MISSED** |

```
=== stratum: natural ===
  MIST B2 (differential value-delta): FIRE
      fault run acknowledged X (http 200, body status 1) but X is absent from its own
      read-back (20 poll(s), gate TIMEOUT_ABSENT); control's X persisted — acknowledged-but-lost write
  Comparator (frozen response contract): control flagged=false, fault flagged=true  -> CAUGHT
=== stratum: constructed ===
  MIST B2 (differential value-delta): FIRE
      fault run acknowledged X ... X absent ... control's X persisted — acknowledged-but-lost write
  Comparator (frozen response contract): control flagged=false, fault flagged=false  -> MISSED
```

- **constructed = the clean win.** The fabricated `{1,"Success."}` passes every bindable
  comparator check (HTTP_STATUS 200 + ENVELOPE_STATUS 1 + MSG_CONTAINS "Success."); the three
  refund/state postconditions are NOT_CHECKABLE (no snapshot/delta/JWT primitive) → the
  comparator cannot catch the lost refund. MIST's differential value-delta catches it (control
  balance moves +refund, fault balance never moves despite the ack).
- **natural = detection tie + MIST diagnosis, and the comparator is NO STRAWMAN.** The
  `{1,"error"}` fails the MSG_CONTAINS "Success." gate → the comparator flags the fault leg
  (CAUGHT). MIST also FIREs; its edge here is diagnostic, not detection: it identifies the
  specific acked-but-lost write (the cancel) and the missing observable (the refund
  balance-delta on /account), whereas the comparator only reports that the response message is
  wrong. (MIST is black-box on cancel + /account — it does NOT attribute the fault to the
  inside-payment hop; the internal cause is out of its view.)
- In both, `control flagged=false` (the clean control leg passes) — no systemic false alarm.

**Stability: N=5 (run #1 + reps 2–5, `tmp/g3-reps.txt`), 100 % consistent** — every run:
natural = FIRE + CAUGHT, constructed = FIRE + MISSED, control never flagged. Runs are ~24 s
each (no restarts/settles), so the verdict is deterministic, not a routing coin-flip.

## Fault mechanism — runtime in-memory toggle (and why two earlier mechanisms failed)

Final mechanism (`HttpToggleFaultInjector`): a fork endpoint
`GET /api/v1/inside_pay_service/inside_payment/test/faultmode/{none|fail|fabricatedack}` flips
an in-memory `volatile` mode on inside-payment; `drawBack` reads it. **No pod restart**, so
ts-cancel-service's pooled connection + Ribbon routing to the single stable inside-payment
instance stay valid, and the per-leg toggle is reliable + instant. `fail` = throw → HTTP 500 →
cancel-service's RestTemplate throws → `cancelOrder`'s genuine compensation-failure catch acks
`{1,"error"}`; `fabricatedack` = the exact success envelope without the persist → clean
`{1,"Success."}`, refund lost. The route is gateway-guarded, so the toggle carries the reader JWT.

This is the endpoint that finally worked; **two restart/mesh-based mechanisms were tried and
found unreliable** — a real methodological finding worth keeping in the writeup, because both
failure modes are about the SUT caller's client-side caching, not about MIST:

1. **EnvoyFilter mesh abort** (route-scoped inbound fault on an inside-payment sidecar). A
   stably-applied filter aborts `/drawback` correctly, but the *per-leg toggle* races
   ts-cancel-service's **pooled** Apache-HttpClient connection: the harness's convergence probe
   hits the gateway on a FRESH connection and sees the new Envoy config before cancel-service's
   REUSED connection does. Proxy-log ground truth: a control-leg drawback returned 418 (abort
   live) while the probe 0.36 s earlier returned 403 (abort gone). Both directions lag → the leg
   observes the wrong filter state → false NO_FIRE / NOT_EVALUABLE.
2. **JAVA_TOOL_OPTIONS `-D` flag rollout** (`SutFlagFaultInjector`). The rollout RESTARTS
   inside-payment; ts-cancel-service's stale connection pool / Ribbon instance cache then races.
   With a short settle the old pod is still up → the caller round-robins and observes the wrong
   flag ~50 % of the time; with a 60 s settle the old pod is gone but its dead IP lingers in the
   caller's cache → the fault-leg cancel connects to it and **read-timeout-hangs** (the run
   crashed here). Also gated on the nacos ipDeleteTimeout (~30 s) + Ribbon refresh (~30 s).

## SUT / deployment

- kind cluster `mist`, ns `trainticket`, upstream `codewisdom/*:1.0.0` graph; the measured
  service `ts-inside-payment-service` fork-built to `:1.0.4` (branch MIST-trainticket) carrying
  the runtime fault-mode toggle. Sidecar-free (the EnvoyFilter attempt's sidecar was removed).
- Reached from the host via `kubectl port-forward svc/ts-gateway-service 18888`.

### Reproducibility caveats

- **nacos gRPC 1.X-mode.** An earlier nacos StatefulSet rolling-restart (memory-thrash recovery)
  left the 3-node cluster stuck in "1.X mode, can't accept gRPC request" → restarting services
  intermittently failed to re-register. Fixed with `kubectl rollout restart statefulset/nacos`.
  The runtime toggle no longer restarts inside-payment, so this no longer affects a run, but a
  fresh deploy should confirm nacos is in 2.0 mode.
- The toggle route is gateway-guarded (403 without a JWT); the injector sends a registered
  USER JWT (same as the /account read-back).

## Cell: AGREEMENT anchor (body-carrying write, both catch) — PENDING
