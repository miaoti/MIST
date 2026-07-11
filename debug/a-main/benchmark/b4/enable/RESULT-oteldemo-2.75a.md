# RESULT — Wave 2.75-A, OTel-Demo MIST read-back ENABLED + RUN (async flagship)

**Date:** 2026-07-10
**Plan:** `debug/a-main/c2c3/wave-275a-plan.md` rev 2.1 (unanimous 3-cold-accept + confirmation pass)
**Freeze amendment:** `c2-freeze.md` §6, 2026-07-10 "WAVE 2.75-A EXECUTED — OTel-Demo" row
**Cases:** `oteldemo-checkout-lost-001` (positive) + `oteldemo-checkout-control-001` (clean control)

## What ran

MIST's differential data-integrity oracle (MEMBERSHIP, SUPPLIED isolation) was bound to
OpenTelemetry-Demo's durable order store and run as a **paired** control/fault head-to-head against
the live SUT (kind cluster `mist`, chart 0.40.9 / app 2.2.0, frontend-proxy PF :8085).

- **Write boundary:** the frontend checkout flow — `POST /api/cart` → `POST /api/checkout` — with a
  **globally-unique per-probe marker** (`mist-<leg>-p<i>-<uuid>`) planted in
  `address.streetAddress`. Live-verified: the marker lands verbatim in
  `accounting.shipping.street_address`.
- **Read-back (bound this wave):** `SqlDurableReadback` runs a marker-filtered
  `kubectl exec … psql` — `SELECT street_address FROM accounting.shipping WHERE street_address='<m>'`
  — and synthesizes the MEMBERSHIP collection (`[{street_address:<m>}]` present / `[]` absent). The
  server-side `WHERE` keeps the growing shipping table from inflating the read (B-R1).
- **Key soundness (C-R2):** the correlation key is the **request-derived** `street_address`, NOT the
  server-assigned `order_id` (which the oracle forbids). Accounting persists Order+Shipping+OrderItem
  in ONE `SaveChanges()` transaction, so a `street_address`-keyed absence is equivalence-preserving
  vs the capture's `order_id`-keyed one.
- **Fault (leg-level, single toggle — A-F8/B-F7):** the harness runs the control leg (kafka up),
  then a SINGLE `kubectl scale kafka --replicas=0`, then the fault leg, then restores. Broker-down
  makes checkout's async produce fail; checkout swallows-and-logs and acks 200 anyway.
- **Async floor:** the read-back timeout is 25 s (≥ 20 s). The measured control landing this run was
  sub-second (p0 present on the first poll at `elapsedMs=224`), so the floor is a conservative
  over-provision — over-margin only ever risks a MISSED fire, never a false one; a control-leg late
  landing can never be mis-read as absence. (The 25 s value was pre-registered against a
  capture-time worst case of a few seconds; the live landing was faster.)
- **N = 5 probe-pairs.**

Harness/transport (committed): `mist-cli/src/main/java/io/mist/cli/enable/{OtelCheckoutHeadToHead,
OtelCheckoutStimulus,KafkaClusterOps,SqlDurableReadback}.java`; triple
`b4/enable/oteldemo-checkout-triple.yaml`.

## Result

**MIST paired FIRE 5/5 probe-pairs** (control-present / broker-down-absent), all correlator-joined,
`correlatorUnique=true`, 0 unjoined.

| leg | ack | on read-back | verdict |
|---|---|---|---|
| control (kafka up) | HTTP 200 order confirmation | marker **PRESENT** (`readbackContainedX=true`) | no fire (correct) |
| fault (kafka scaled 0) | HTTP 200, success-shaped, ~0.02 s (fully-async produce) | marker **ABSENT** (`readbackContainedX=false`, gate `TIMEOUT_ABSENT`, 34 polls) | **FIRE** — acknowledged-but-lost |

Machine evidence: `b4/enable/oteldemo-checkout-run.report.json`.

## Anti-circularity firewall + permanence

Two distinct guards (not one — cold-review A-3/C-F5): (1) the ground-truth *label* is SUT-native, read
directly from the store, never taken from MIST's verdict; this direct `psql` read wraps the same
query MIST's transport wraps, so it is a store re-read distinct from MIST's transport, not an
orthogonal oracle. (2) The read-mechanism validator that forecloses a shared-mode read failure is the
paired CONTROL leg: a silent wrong-schema/db read would null the control marker too →
`control.readbackContainedX=false` → NOT_EVALUABLE, never FIRE (the report shows
`control.readbackContainedX=true`). Committed evidence: `b4/enable/ground-truth-oteldemo.txt` (all 5
control markers listed present, 0 fault). Confirmed:

- **5/5 control markers persisted; 0/5 fault markers persisted** (each of the 5 control markers is
  listed in the ground-truth artifact — per-probe auditable).
- **Fault markers STILL 0** after the pipeline was fully recovered and a heal canary drained. The
  loss is **permanent**, not pending: the fault-leg produce never entered the topic (kafka was
  down), so there is nothing to drain — distinct from a post-recovery heal canary, which landed only
  after the accounting consumer-group rebalance drained it (the pending-vs-missing distinction,
  re-observed live).

## Honest framing (C-B1) — PRESENCE-CONCORDANT, not a discrimination win

This SUT is natively traced, and the case's `tracetest_presence_oracle` **already FLAGs** (the
accounting `receive orders` CONSUMER span is absent on the fault leg). MIST's read-back is therefore
**CONCORDANT** with the presence oracle — it *independently confirms*, at the durable
system-of-record, the loss that presence catches. It is **NOT** a "beats trace-only" discrimination
win; claiming one here would be false. The datum's value is: (1) MIST's read-back binds an **async
SQL** system-of-record and agrees with ground truth on a genuinely-async, fast-clean-ack loss; (2)
it independently corroborates the presence signal via a different mechanism (durable state vs span
topology), strengthening the case's evidence; (3) it earns the case a verdict-valued
`mist_readback_oracle` cell.

The MIST-only *discrimination* win over a trace comparator remains the TrainTicket fabricated-ack
case.

## Cell changes (atomic with the freeze row)

- `oteldemo-checkout-lost-001`: `mist_bindable` false→**true**; `mist_readback_oracle`
  not_applicable→**flag**; locator `order_id`→`street_address` (C-R2; modality enum stays
  `sql-probe`).
- `oteldemo-checkout-control-001`: `mist_bindable` false→**true**; `mist_readback_oracle`
  not_applicable→**no_flag** (clean-control TN); same locator change.

## Cluster hygiene

The kafka pod replacement wedges the rdkafka clients (measured runbook). Recovery — rollout-restart
checkout+accounting+fraud-detection — was executed and **verified healthy**: frontend-proxy 200, all
four deployments available, both post-recovery heal canaries landed. OTel-Demo stays UP (plan §1
default). No case added/removed.
