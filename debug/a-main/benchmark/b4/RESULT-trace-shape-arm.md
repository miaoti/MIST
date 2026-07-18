# RESULT — MIST Trace Shape Oracle: wired + MEASURED over the corpus (the sufficiency round's #1 lever)

**Date:** 2026-07-18 · USER-authorized MIST tool code (the standing no-tool-code gate lifted for this).
Evidence: `scoring/verdicts/mist_trace_shape.json` + `.detail.json`; harness
`mist-cli/.../tools/OfflineTraceShapeEvaluator.java` + `scoring/run_trace_shape_arm.py`;
DI wiring `DataIntegrityRuntime.java` + `PairedFaultExecutor.java` + `DataIntegrityTraceShapeNoteTest` (4/4).

## What was wired
1. **Offline arm (the corpus integration):** `OfflineTraceShapeEvaluator` runs the SAME
   `TraceShapeOracle` code the generated tests wire (via `TraceShapeAdapter`) over each case's
   captured trace of record. Configuration disclosed: ONLY the structural intent-agnostic
   `HIDDEN_DOWNSTREAM_FAILURE` invariant enabled (the 4 learned invariants need a learned store
   the captures don't carry). flag iff ERROR-severity failure — WARN is non-blocking, the
   runtime's own rule. 13 traced cases evaluated; 20 without a captured trace = not_evaluable.
2. **DI runtime wiring (kills the 'inert flag' disclosure):** at the trace-complete OBSERVED_*
   gate the runtime now evaluates the shape oracle over that same completed trace and carries a
   REPORTING-ONLY `traceShapeNote` on the RunRecord (serialized by the pairing executor).
   Flag-gated (`mst.oracle.shape.invariants.hidden_downstream_failure.enabled`, default false):
   legacy records byte-identical; the gate/verdict tiers NEVER move. Pinned by 4 unit tests
   (flag-off null / swallowed-5xx detail / clean pass / fetch-failure null); the existing
   DI+pairing suites (34+33+7) stay green.

## The measured arm (10-arm table row `mist_trace_shape`)
| outcome | cases |
|---|---|
| **0/6 traced masked-loss positives flagged** | TT fabricatedack, adminroute, adminbasic, createaccount-agreement, oteldemo-lost, sockshop-swallowed → all `no_flag` |
| WARN-tier abstention (no flag) | sockshop-shipping-control: queue-master otel=ERROR w/o http-5xx → WARN → non-blocking — **the severity tiering abstains where naive_span_error false-positives on the same trace** |
| 1 ERROR-tier flag | bookinfo-ratings-benign: reviews→ratings http=503 under the 200 page → **an honest FP on the designed-degradation negative** (the invariant's documented over-claim risk, now measured) |

## Why this HELPS the paper (the honest reading)
- **The central claim gains its strongest internal control:** even MIST's OWN trace-structural
  detector sees NONE of the masked-loss positives — the faults leave no error span anywhere
  (measured, not asserted). The read-back differential is the ONLY oracle in the 10-arm study
  that catches them. This kills the "you built a second oracle that also wins" suspicion and
  pins the contribution precisely on the read-back mechanism.
- The severity-tiering vs naive-span contrast (abstain-on-WARN vs FP) is a measured design
  datum; the bookinfo ERROR FP is the honest counter-datum, reported.
- The C1×C2 integration is now real for BOTH MIST oracles: read-back (10/10 + 0/13) and
  trace-shape (0/6 + 1 FP + 1 WARN-abstain), through the same single scoring path.

## Rails
Never present `mist_trace_shape` as a detector of the masked-loss class (it measures the
class's trace-invisibility); never pool it with the read-back column; the learned-invariant
gates were OFF (disclosed) — enabling them requires a learned store no corpus capture carries.
