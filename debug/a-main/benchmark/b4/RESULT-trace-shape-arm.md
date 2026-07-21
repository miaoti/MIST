# RESULT — MIST Trace Shape Oracle (structural sub-invariant): wired + MEASURED over the corpus

> **[2026-07-21 CURRENT-STATE POINTER]** Corpus figures below are the 33-era snapshot; the
> benchmark-of-record is now **27** (F-corpus retired, depdown captured) and the table was
> re-scored (`bd362d0`): this arm reads 0/6 evaluable positives + 1 FP over the 27. See the
> freeze 2026-07-21 row.

> **Post-review rev 2 (tsarm A/B/C fold, same day).** This arm runs ONLY MIST's structural
> `HIDDEN_DOWNSTREAM_FAILURE` sub-invariant (1 of the oracle's 6 invariants — the four learned
> invariants need a learned store the offline captures do not carry, and target-attribution needs
> a negative-test target): it is **MIST's structural sub-check, never "the full trace-shape
> oracle"**. Arm-count lineage: the canonical Gate-4 frozen table = 6 arms; + 3 trivial baselines
> (sufficiency fold) = 9; + this arm = **10**. The old "TraceShapeOracle unwired/inert" MYC
> disclosure is superseded by this wave (offline arm + flag-gated DI reporting wiring).

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
Per visibility class (the harness's per-class cells; the "0/6" below is the cross-class UNION
tally of evaluable positives, stated as a union, never as a pooled recall):
| outcome | cases |
|---|---|
| **0/6 evaluable (traced) masked-loss positives flagged** — union across classes; per-class cells in `matched-recall-table.json` | TT fabricatedack, adminroute, adminbasic, createaccount-agreement, oteldemo-lost, sockshop-swallowed → all `no_flag` |
| WARN-tier abstention (no flag) | sockshop-shipping-control: queue-master otel=ERROR w/o http-5xx → WARN → non-blocking — **the severity tiering abstains where naive_span_error false-positives on the same trace** |
| 1 ERROR-tier flag | bookinfo-ratings-benign: reviews→ratings http=503 under the 200 page → **an honest FP on the designed-degradation negative** (the invariant's documented over-claim risk, now measured) |

## Why this HELPS the paper (the honest reading — review-B calibrated)
- **An INTERNAL CONTROL, not independent evidence:** the 0/6 re-states, through MIST's own
  oracle code, the same underlying fact `naive_span_error` already shows on the same traces —
  the masked-loss faults leave NO error span anywhere. Its value is confirmation-via-the-tool's-
  own-second-oracle (killing the "you built a second oracle that also wins" suspicion and any
  "your own trace oracle would have caught it" objection), NEVER an independent detection datum.
  The read-back differential remains the only oracle in the 10-arm study that catches these.
- The severity-tiering vs naive-span contrast (abstain-on-WARN vs FP on sockshop-control) is a
  measured design datum; the bookinfo ERROR flag is the honest counter-datum (below).
- The C1×C2 integration is now real for BOTH MIST oracles: read-back (10/10 + 0/13) and the
  structural sub-invariant (0/6 + 1 FP + 1 WARN-abstain), through the same single scoring path.

## The bookinfo reconciliation (review B's required disclosure)
`bookinfo-ratings-benign-001` is the corpus's designed-degradation NEGATIVE: the page 200s while
reviews→ratings returns 503 — the exact shape the corpus pre-registers as requiring SEMANTIC
judgment (designed graceful degradation ≠ defect). MIST's FULL oracle carries that semantics in
invariants this configuration does not run (response-envelope classification, learned baselines);
the structural-only sub-invariant sees "5xx swallowed under 2xx" and flags → **the arm's single
corpus flag is a FALSE POSITIVE on the benign, a property of the structural-only configuration,
not of MIST's full oracle** — and simultaneously the measured form of the invariant's own
documented over-claim risk. The frozen `oracle_expectation.mist_trace_shape_oracle` stamps are
`not_applicable` on all 33 cases (design-era: the oracle was unwired then); this measured arm
supersedes them for the 13 traced cases — the bookinfo stamp-vs-measured divergence is exactly
this configuration gap, disclosed, and the stamps are NOT retro-edited (measured-vs-stamped
reconciliation: 12/13 abstain-consistent, 1/13 the disclosed bookinfo divergence).

## Rails (incl. review B's required table-note, verbatim for the draft)
Never present `mist_trace_shape` as a detector of the masked-loss class; never pool it with the
read-back column; always name it the STRUCTURAL SUB-INVARIANT. The table-note of record:
"`mist_trace_shape` runs ONLY MIST's structural HIDDEN_DOWNSTREAM_FAILURE sub-invariant (four
learned invariants disabled — offline captures carry no learned store); on the 13 trace-bearing
cases it flags 0/6 evaluable masked-loss positives and its sole corpus flag is a false positive
on bookinfo (a 503-under-2xx the corpus pre-registered as MIST's semantic no_flag case, which
this structural-only configuration does not exercise) — reported as an internal control on the
same traces `naive_span_error` consumes, never as MIST's full trace-shape oracle and never
pooled with the read-back column."

## Review-A code fixes folded (same day)
Note computed AFTER the decisive R4fix re-read (never adds latency ahead of it); the runtime
note filtered to the hidden-downstream invariant only (parity with the offline arm — other
invariants keep their generated-test reporting channel); "byte-identical" scoped to the VERDICT
FLOW (serialized records additively gain a null field); `OfflineTraceShapeEvaluator.evaluate`
extracted + 4 unit tests (ERROR→flag / WARN→no_flag / multi-trace / single-wrapper); all suites
green (4+4+34+33).
