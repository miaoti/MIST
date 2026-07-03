# G3 head-to-head artifacts — TrainTicket cancel→refund

Configuration for the depth head-to-head (MIST B2 vs the frozen blind comparator) on the
natural cancel→refund missing-compensation defect. Design + rationale:
`debug/a-main/prep/g3-headtohead-run-architecture.md`, `…/g3-tt-defect-survey.md`,
`…/g3-tt-headtohead-design.md`; oracle-code reviews: `…/research/REVIEW-DEPTH-*.md`.

## Files
- `target-triples-natural.yaml` — the cancel→refund triple, `supplied` isolation +
  `value-delta` read-back on `/inside_payment/account` (match userId → value balance).
  No `fault_flag`: the natural-stratum fault is the EnvoyFilter abort (below).
- `target-triples-constructed.yaml` — same triple with `fault_flag`
  (`mist.fault.drawback.fabricatedack.enabled` on `ts-inside-payment-service`), the
  fork's opt-in fabricated-ack drawback (commit f57102e6). Needs the **fork-built**
  ts-inside-payment image (upstream has no such flag).
- `drawback-abort-envoyfilter.yaml` — the natural-stratum fault: an inbound EnvoyFilter
  on ts-inside-payment-service aborting only `/…/inside_payment/drawback` with HTTP 418
  (chosen to sit outside the app+mesh status space). Applied/removed by the
  `IstioRouteFaultInjector`.

## Strata (expected results)
| stratum | fault | cancel response (fault leg) | comparator | MIST B2 |
|---------|-------|------------------------------|------------|---------|
| natural | EnvoyFilter abort /drawback | `{1,"error"}` | FLAG (msg gate) | FIRE → tie + MIST diagnosis |
| constructed | fabricated-ack flag | `{1,"Success."}` | PASS (misses) | FIRE → clean MIST win |

## RUNBOOK (pre-registered, from REVIEW-DEPTH-RECONCILIATION.md)
- Per-leg **fresh** buyer (isolation is by construction, not machine-enforced).
- The cancelled order is **PAID, price>0, far-future travelTime** (else the refund is
  "0"/"0.00" and the value-delta is invisible); no leg crosses the expiry boundary.
- Read-back key = **loginId** (the drawback credits loginId). The read-back is
  JWT-gated → the harness configures MstAuthHandler with a valid login.

## Prerequisite
ts-inside-payment-service must carry an Istio sidecar for the EnvoyFilter (the namespace
is deployed sidecar-free to dodge the infra startup race — inject into this one
Deployment). Live bring-up status: `debug/a-main/prep/g3-tt-deploy-progress.md`.
