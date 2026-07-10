# Wave-3a Item 3 — `kafkaQueueProblems` S2 (probe-gated): RESULT = STOP (C-m8)

**Date:** 2026-07-10 · **SUT:** OTel-Demo, chart 0.40.9 / app 2.2.0, ns `otel-demo` ·
**Flag mechanism:** flagd-ui API (the 1-P0 mechanism of record; `flagd-toggle.sh`), integer variant
`off:0 / on:100`, hot-reload <2 s, restore = flags-object semantic equality vs the frozen reference.

## Decision
Plan rev 2.1 §3 gave three outcomes — AUTHOR (delay ≥ 2× the ack→export-close margin, ~24 s),
NOT-AUTHORED (delay < horizon), or **STOP (C-m8): rows actually LOST**. The probe round fired the
**STOP** branch and the confirmatory + separation runs upgraded it from "permanent loss" to
"**stochastic permanent production loss with a wedge that persists past flag-off; 0 pending**".
**No S2 case authored** (the "delayed-not-lost" premise is refuted) and **no S1 case authored this
wave** (per C-m8 the S1-positive candidate is deferred to its own disciplined characterization).

## Measured (raw logs in this directory)
| Run | order id | ack | landed? | after restart | reading |
|---|---|---|---|---|---|
| probe (`item3-probe-N4.log`) | 68d30bc7 / 243d0229 / dfb71a2d / 9b2415a8 | 200 ~0.03 s | 4/4 TIMEOUT @300 s | still 0 | **lost at production** |
| probe post-flag canary | 791c41c3 | 200 | rows=1 | 1 | drained PAST the 4 ⇒ they were dropped, not queued |
| confirm (`item3-confirm-N2.log`) | 32030159 | 200 | rows=1 @≤30 s **under flag** | 1 | **stochastic fast success** |
| confirm | 320f390b | 200 | 0 @30 s | still 0 | lost |
| confirm post-flag canary | 69589ef0 | 200 | 0 | still 0 | lost (flag OFF) |
| settle (`item3-settle.log`) fresh flag-off canary | a1a4ce9a | 200 | 0 @60 s | still 0 | **wedge persists past toggle-off** |
| recovery health canary (`item3-recovery-separation.log`) | 2efce5ac | 200 | rows=1 @~10 s | — | pipeline restored |

**Net:** 7 of 8 in-window acked orders PERMANENTLY LOST, 1 stochastic fast success, **0 pending**.
Kafka pod **0 restarts** throughout (NOT the Phase-D broker-replacement wedge). The recovery
rollout-restart (accounting+checkout+fraud) restored LIVE traffic but recovered ZERO lost orders ⇒
no buffered/pending component — the flag drops at **production** (swallowed `sendToPostProcessor`
publish under overload) and leaves the rdkafka clients degraded past flag-off until a restart.

## Provenance / hygiene
- Anti-circularity: all IDs are server-issued `orderId`s from the ack body; labels never from tool output.
- Flag restored + frozen-equality verified after every run; boot-state ConfigMap never touched.
- Post-run cluster state: OTel-Demo UP and healthy (health canary landed); flag = off/frozen.

## Disposition (verbatim, plan §3 STOP / C-m8)
"rows actually LOST ⇒ dated survey correction + a decision point — that is an S1-positive candidate
(vendor-flag provenance), authored only under its own discipline in a later item/wave, never
silently subsumed."
- **Dated survey correction:** `c2c3/c2-depth-survey.md` §2 OTel item-3 block + the S2 path-(1) inline pointer.
- **Scoring record:** `trace_score.py` `oteldemo-kafkaqueue-pending-benign` row stays inert, STOP-annotated.
- **S1-positive candidate (deferred):** needs a many-trial loss-rate characterization separating
  production-drop from consumer-wedge, its own control leg, and vendor-flag provenance disclosure.
