# RESULT — Completion-set wave PHASE C (the live windows) — RESULT OF RECORD

**Date:** 2026-07-16 · Status: EXECUTED (two single-tenant windows, sequential; all tenants
back to 0). Plan: `wave-completion-set-plan.md` rev 2 (CONFIRMED 3/3). DoD gate = the
wave's batched 3-cold review (with `RESULT-e2-frontier.md`).

## Leg 1 — the A5(iii) TeaStore mesh-sever window (the 4 owed MIST-column cells)

- **Order pair: paired FIRE 4/4** (`TeaStoreMeshseverHeadToHead`, the 2.75-A MEMBERSHIP
  binding under the mesh-sever VS producer; the Stimulus decorator applies the committed
  wave-3a VS around each confirm and ALWAYS deletes it before the oracle polls — VS
  leftovers at teardown: 0).
- **Orderitems pair: paired FIRE 4/4** via the NEW `ChainedOrderItemsReadback`
  (child-collection membership = parent-exists AND items ≥ 1; sanctioned
  read-back-binding category).
- **Ground truth (direct persistence reads, never MIST; banked
  `ground-truth-meshsever.txt`):** 8 control markers landed (order run items=1 ×4;
  orderitems run ×4); orderitems fault PARENTS present w/ items=0 ×4 (the
  partial-aggregate loss); order-run fault rows ABSENT entirely (the order-row masking).
- **DISCLOSED CELL CHANGE:** the 4 cases' `mist_readback_oracle` filled
  (not_applicable → flag/no_flag ×2 pairs); labels + ground truth untouched. The MIST
  column now: **flag 9 / no_flag 13 / structural-n_a 4(+1 below) — ZERO silent pending;
  9/9 evaluable positives FIRE, 0/15 negatives flagged.**
- Window ops: sidecars restored via ns label + rollout restart of auth/webui ONLY
  (reverted at teardown — deployment shape back to the snapshot's); tenant to 0.

## Leg 2 — kafka S1, the SECOND ATTEMPT (X4 protocol; the first attempt STOPPED at probe)

- **Pre-flag canary PASS** (T2 baseline) → **CONTROL leg (flag OFF, run FIRST): 10/10
  landed.**
- **Fault leg (kafkaQueueProblems=100 via the flagd-ui API mechanism of record): 20/20
  acked HTTP 200 + orderId; T+5 min BINDING re-probe → 19/20 LOST** (1 fast success
  under-flag — the first attempt's stochastic-mix pattern at N=20).
  **Measured rate 0.95; Wilson 95% CI [0.764, 0.991].**
- **The wedge record REPRODUCED:** post-flag-off canary FAIL (the flag-on condition wedges
  rdkafka past toggle-off) → the pinned recovery-restart (checkout+accounting+fraud) →
  canary PASS → **post-recovery permanence re-probe: the 19 remain ABSENT** (the healthy
  pipeline drained past them = dropped at production, not buffered). NO third attempt
  (the stop rule held; none was needed).
- Restore verify: runtime flags == the frozen reference (semantic equality); ConfigMap
  never touched.
- **NEW S1 CASE (ADDITIVE — corpus 26 → 27, validator-green 27/27):**
  `oteldemo-checkout-kafkaqueue-lost-001` (stratum 1, `vendor_flag`,
  `ground_truth.source=vendor` per X4; measured rate + CI + the wedge/permanence record in
  `fault.config`; NOT counted toward S2 floors; async-no-bound ⇒
  calibration-genuine-INELIGIBLE; `trace_visibility=trace-uninstrumented` BY CAPTURE —
  the stochastic protocol exported no per-trial traces, disclosed).
  `mist_readback_oracle=not_applicable` OF RECORD, adjudication class
  **barred-by-stop-rule** (a MIST leg needs another flag-on window; bindable-in-principle
  via the 2.75-A site binding; future window = USER decision — disclosed, not silent).
- Evidence: `b4/cset/kafka-s1/` (control.log, fault.log w/ both re-probe passes,
  restore.log); runner `b4/runners/cset/kafka-s1.sh` (committed).

## Leg 3 — the Boutique 2.4 gRPC-abort rider: DEFERRED-WITH-REASON (§0(d) branch)

The rider is a deployment-capability check (Istio gRPC abort on Boutique), not a paper
experiment; nothing in the claim map consumes it; closing it costs a further tenant window
in an already-long day. **2.4 stays ◐ with the rider OPEN, deferred explicitly** (the
close-or-defer-with-reason branch; a future Boutique window can take it opportunistically).

## Integration chain after Phase C (regenerated end-to-end)

27-case corpus validator-green; censuses/map/verdicts/table/release-staging all
regenerated (40 members; reproduction census **26/27 executable-reproducible** — the new
case is mechanized `vendor_flag` + committed script). The matched-recall table's MIST
column: 9/9 evaluable positives, 0 FP, per-visibility cells only.

## Incidents

Two background wsl waiters were externally killed mid-sweep (the known
detached-wsl-client class); no evidence loss — the trials were all placed and the BINDING
measurement is the T+5 min re-probe, which ran in bounded foreground calls afterward.
(The kill landed mid-initial-sweep, so `fault.log`'s initial landed-sweep section lacks
trial 20's line — cosmetic; both re-probe passes cover all 20. The log is evidence and
was not edited.)

## Category + claim nuance (B-NB-4 fold)

The two new `enable/` transports (`TeaStoreMeshseverHeadToHead`, `ChainedOrderItemsReadback`)
are A5(iii)-SANCTIONED READ-BACK BINDINGS (the 2026-07-10 gate amendment's
"read-back modality bindings" category) — they bind MIST's EXISTING membership oracle to
sites/producers; no oracle semantics were touched (§4-barred territory). The chained
read-back is a CONSTRUCTED membership view (orders joined to their child collections),
not a native durable surface — any paper claim citing the orderitems cells carries that
nuance.
