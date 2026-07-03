# G3 cancel→refund head-to-head — live results

MIST's B2 differential value-delta oracle vs the FROZEN blind response-assertion
comparator (`blind-cancel-refund-contract.yaml` @ 38e7aa6 → executable
`assertion-bindings-cancel-refund.yaml`), both observing the SAME cancel→refund
stimulus under two fault strata. Runner: `io.mist.cli.g3.CancelRefundHeadToHead`
+ `TrainTicketStimulus`. Design: `prep/g3-headtohead-run-architecture.md`.

Status: **PRELIMINARY** — records runs as they land. NOT yet reviewer-accepted;
the standing ≥3-cold-review gates these numbers before they feed any claim.

## SUT / deployment

- kind cluster `mist`, ns `trainticket`, upstream `codewisdom/*:1.0.0` graph +
  the two measured services fork-built to `:1.0.2` (`ts-inside-payment-service`,
  `ts-cancel-service`) carrying the opt-in fabricated-ack drawback flag
  (`mist.fault.drawback.fabricatedack.enabled`, fork commit f57102e6).
- Reached from the host via `kubectl port-forward svc/ts-gateway-service 18888`.

### Operational caveat (reproducibility) — nacos gRPC 1.X-mode

A nacos StatefulSet rolling-restart earlier (memory-thrash recovery) left the
3-node cluster stuck in **"1.X mode, can't accept gRPC request"**: newly
(re)starting services intermittently failed to re-register (6+ crash-loops),
which made the SutFlag rollout time out and, when an upstream 1.0.0 replica kept
serving, silently masked the fault (a false NO_FIRE). Fix that restored reliable
registration: `kubectl rollout restart statefulset/nacos` (rolling — peers retain
the registry) → services re-register in ≤3 tries. **Any re-run must confirm
inside-payment restarts converge to a single ready pod before trusting a cell.**

## Cell: CONSTRUCTED stratum (fabricated-ack drawback) — the clean win

Fault = the fork flag toggled by `SutFlagFaultInjector` (JAVA_TOOL_OPTIONS `-D` +
rollout). `drawBack` returns the EXACT success envelope without persisting the
refund `Money` row → `ts-cancel-service` returns a perfectly clean
`{status:1,msg:"Success."}` while the refund is lost.

Run #1 (2026-07-03, log `tmp/g3-constructed5.log`):

```
=== stratum: constructed ===
  MIST B2 (differential value-delta): FIRE
      fault run acknowledged X (http 200, body status 1) but X is absent from its
      own read-back (20 poll(s), gate TIMEOUT_ABSENT); control's X persisted —
      acknowledged-but-lost write
  Comparator (frozen response contract): control flagged=false, fault flagged=false  -> MISSED
```

- **MIST FIRE** — the value-delta oracle: control leg's /account balance moved
  by the refund (X present), the fault leg's never did despite a 200/status-1 ack
  → acknowledged-but-lost write.
- **Comparator MISSED** — the clean `{1,"Success."}` passes HTTP_STATUS 200 +
  ENVELOPE_STATUS 1 + MSG_CONTAINS "Success."; the three refund/state
  postconditions are NOT_CHECKABLE (no snapshot/delta/JWT primitive), so nothing
  binds that could catch the lost refund. Neither leg flags.

Mechanism cross-checked by hand (flag ON → cancel `{1,"Success."}` + NO /account
row for the buyer; flag OFF → refund row appears at balance 80.00).

## Cell: NATURAL stratum (EnvoyFilter /drawback abort) — PENDING

## Cell: AGREEMENT anchor (body-carrying write, both catch) — PENDING
