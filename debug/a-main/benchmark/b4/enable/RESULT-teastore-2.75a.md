# RESULT — Wave 2.75-A, TeaStore MIST read-back ENABLED + RUN

**Date:** 2026-07-10
**Plan:** `debug/a-main/c2c3/wave-275a-plan.md` rev 2.1 (unanimous 3-cold-accept + confirmation pass)
**Freeze amendment:** `c2-freeze.md` §6, 2026-07-10 "WAVE 2.75-A EXECUTED — TeaStore" row
**Cases:** `teastore-order-maintenance-masked-001` (positive) + `teastore-order-control-001` (clean control)

## What ran

MIST's differential data-integrity oracle (MEMBERSHIP mode, SUPPLIED isolation) was bound, for the
first time, to TeaStore's durable order store and run as a **paired** control/fault head-to-head
against the live SUT (kind cluster `mist`, TeaStore v1.4.2, webui PF :8082 / persistence PF :8083).

- **Write boundary:** the webui order flow — login (fresh per-order session) → add product 42 →
  `POST /cartAction confirm` with a **globally-unique per-probe marker** (`mist-<leg>-p<i>-<uuid>`)
  planted in the `address1` field (verified to land verbatim in the persistence order row).
- **Read-back (bound this wave):** `JsonDurableReadback` GETs the **full** persistence
  `/rest/orders` JSON collection; the oracle checks MEMBERSHIP of the marker. The full-collection
  read makes absence of a globally-unique marker sound.
- **Fault (owned inside the stimulus, B-R3):** on the fault leg, the persistence **maintenance flag**
  is toggled ON around the confirm write and restored OFF *before* the read-back — so the order is
  masked-lost (persistence CREATE fabricates `201/-1`, webui renders ORDERCONFIRMED) yet the
  read-back that follows sees a healthy, served `/rest/orders`.
- **N = 5 probe-pairs** per run (`ts.probes=5`), correlator-joined across legs.

Harness/transport (committed): `mist-cli/src/main/java/io/mist/cli/enable/{TeaStoreOrderHeadToHead,
TeaStoreHttpStimulus,JsonDurableReadback}.java`; triple `b4/enable/teastore-order-triple.yaml`;
transports first committed `655fa0b`.

## Result

**MIST paired FIRE 5/5 probe-pairs** (control-present / maintenance-masked-absent), all
correlator-joined, `correlatorUnique=true`, 0 unjoined.

| leg | ack | on read-back | verdict |
|---|---|---|---|
| control (maintenance OFF) | HTTP 200 (ORDERCONFIRMED) | marker **PRESENT** (`readbackContainedX=true`) | no fire (correct) |
| fault (maintenance ON around confirm) | HTTP 200 (ORDERCONFIRMED, success-shaped-clean) | marker **ABSENT** (`readbackContainedX=false`, gate `TIMEOUT_ABSENT`) | **FIRE** — acknowledged-but-lost |

Machine evidence: `b4/enable/teastore-order-run.report.json` (verdict FIRE; `firePairs` 5/5; both
legs `ackHttpStatus` 200).

Gate disclosure: the fault-leg absence is established by `TIMEOUT_ABSENT` (15 s / 31 polls), NOT
`OBSERVED_COMPLETE_ABSENT`. This is expected and sound for a **synchronous** SUT — there is no
async-completion signal to observe, and a synchronous write that is still absent after the full
polling window is conclusively lost.

## Anti-circularity firewall (independent ground truth)

Two distinct guards (not one — cold-review A-3/C-F5): (1) the ground-truth *label* is SUT-native (the
order did/didn't durably land), read directly from `/rest/orders`, never from MIST's verdict; this
direct read wraps the same collection MIST's transport reads, so it is a store re-read distinct from
MIST's transport, not an orthogonal oracle. (2) The read-mechanism validator is the paired CONTROL
leg: a systemically-broken read would null the control marker too → `control.readbackContainedX=false`
→ NOT_EVALUABLE, never FIRE (the report shows `control.readbackContainedX=true`). This also carries
the truncation soundness — the equally-fresh control marker found in the SAME full `/rest/orders`
read proves the read window includes just-written orders (no `readback_bound` is set; `/rest/orders`
serves newest-first, unbounded).

Committed evidence: `b4/enable/ground-truth-teastore.txt` lists every landed control marker (9 across
this wave's two TeaStore runs — run1 N=4 + run2 N=5-of-record) and confirms **0 fault markers** —
per-probe auditable. For the recorded run alone (run2, `teastore-order-run.report.json`): **5/5
control persisted, 0/5 fault**.

The verdict-valued MIST cell (`mist_readback_oracle=flag`) is therefore earned by a measured run
whose ground truth was verified out-of-band. Audit property preserved: verdict-valued MIST cells
appear only where MIST ran.

## Honest framing (C-B1)

This is a **sole-oracle** datum: TeaStore is trace-uninstrumented as-deployed (Kieker-only, no
OTel/Jaeger), so no trace comparator exists on this SUT and MIST's read-back is the *only* oracle
that fires. **This is NOT a discrimination win over a trace comparator** — "beats trace-only" would
be vacuous where no trace oracle is even bindable. The datum's value is: (1) a genuine
acknowledged-but-lost write that every deployed *ack-side* column misses (status 200, schema
unchanged, body success-shaped with no sentinel — the `-1` never reaches the client), caught by
MIST's read-back; (2) the first case to earn a verdict-valued `mist_readback_oracle` cell, moving
it from the T9 applicability-boundary row into the MIST recall denominator.

The MIST-only *discrimination* win over a trace comparator remains the TrainTicket fabricated-ack
case (a traced SUT where the trace looks clean).

## Cell changes (atomic with the freeze row)

- `teastore-order-maintenance-masked-001`: `mist_bindable` false→**true**; `mist_readback_oracle`
  not_applicable→**flag**; read-back locator HTML-profile→JSON `/rest/orders` (modality enum
  unchanged, stays `api-get`).
- `teastore-order-control-001`: `mist_bindable` false→**true**; `mist_readback_oracle`
  not_applicable→**no_flag** (clean-control TN); same locator move.

Corpus counts are unchanged (no case added/removed; two existing cases gain measured MIST cells).
