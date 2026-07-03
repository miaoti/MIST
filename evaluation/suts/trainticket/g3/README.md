# G3 head-to-head artifacts — TrainTicket cancel→refund

Configuration for the depth head-to-head (MIST B2 value-delta vs the frozen blind response-
assertion comparator) on the cancel→refund missing-compensation defect. Design + rationale:
`debug/a-main/prep/g3-headtohead-run-architecture.md`, `…/g3-tt-defect-survey.md`; results +
faithfulness: `debug/a-main/g3-comparator-tt/{g3-headtohead-results.md, g3-natural-faithfulness-
source-check.md, g3-value-delta-ground-truth.md}`; oracle-code reviews: `…/research/REVIEW-DEPTH-*.md`.

## Fault mechanism of record: runtime HTTP toggle

Both strata are driven by `HttpToggleFaultInjector` — a GET to a fork test endpoint
(`…/inside_payment/test/faultmode/{none|fail|fabricatedack}`) that flips an in-memory `volatile`
mode on ts-inside-payment-service. **No pod restart**, so ts-cancel-service's pooled connection +
Ribbon routing to the single stable inside-payment instance stay valid and the per-leg toggle is
reliable. Mode is derived from each triple's `fault_flag.property`
(`mist.fault.drawback.<mode>.enabled` → `<mode>`).

Two restart/mesh mechanisms were tried first and **rejected** (both raced ts-cancel-service's
client-side caching, not MIST — see the results doc's "mechanism" section): an EnvoyFilter mesh
abort (`drawback-abort-envoyfilter.yaml`, kept only for that documented finding) and a
JAVA_TOOL_OPTIONS `-D` flag rollout (`SutFlagFaultInjector`).

## Files
- `target-triples-natural.yaml` — the cancel→refund triple, `supplied` isolation + `value-delta`
  read-back on `/inside_payment/account` (match userId → value balance). `fault_flag`
  `mist.fault.drawback.fail.enabled`: drawBack throws → 500 → ts-cancel-service's RestTemplate
  throws → `CancelController`'s genuine catch acks `{1,"error"}` with the refund lost.
- `target-triples-constructed.yaml` — same triple with `fault_flag`
  `mist.fault.drawback.fabricatedack.enabled`: drawBack returns the exact success envelope without
  persisting → clean `{1,"Success."}`, refund lost. Needs the **fork-built** inside-payment image.
- `drawback-abort-envoyfilter.yaml` — the rejected EnvoyFilter mesh abort, retained for the
  connection-pool-race finding only (NOT used by the harness).

## Strata (expected results — N=5 stable, runs/prefunded-*.log)
| stratum | fault → cancel (fault leg) | comparator | MIST B2 |
|---------|---------------------------|------------|---------|
| natural | drawback throws → `{1,"error"}` | FLAG (msg gate) → **CAUGHT** | FIRE → detection tie + MIST diagnostic edge |
| constructed | fabricated-ack → `{1,"Success."}` | PASS → **MISSED** | FIRE → clean MIST win |

Control leg never flags. The constructed miss is un-contestable: with the pre-funded buyer the
value-delta is a real arithmetic delta (`50.00 → 130.00` control vs `50.00` fault), so membership
(buyer present in both legs) cannot catch it — only the +refund delta does.

## RUNBOOK (pre-registered + head-to-head-review disclosures)
- Per-leg **fresh** buyer (isolation is by construction, not machine-enforced — VALUE_DELTA
  hardcodes baselineHasX=false, so the executor isolation tripwire cannot fire).
- Each buyer is **PRE-FUNDED** to a non-zero `/account` balance (addMoney baseFund=50.00) before
  the cancel, so the discriminator is a genuine arithmetic +R delta and not an appear-vs-absent
  membership signal (review A/B).
- The cancelled order is **PAID, price>0, far-future travelTime** (else calculateRefund is
  "0"/"0.00" and the value-delta is invisible); no leg crosses the expiry boundary. Both legs use
  the same nonzero R (stimulus-enforced identical orders).
- Read-back key = **loginId** (= the buyer's userId; the drawback credits loginId). The read-back
  is JWT-gated → the harness readies a valid MstAuthHandler login.
- inside-payment is **replicas=1** (the runtime toggle sets a volatile on one pod; >1 could miss on
  a fault leg → NO_FIRE, never a false FIRE).
- The cancel is sidecar-free hence **traceless** → the fault-leg absence is TIMEOUT_ABSENT, a
  confidence-label (not soundness) limitation: the control leg proves normal timing lands in-window
  and the injected loss is permanent, so the 10 s cap cannot hide a slow-but-real refund.
