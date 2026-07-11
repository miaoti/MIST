# RESULT — E2 read-back capability + provenance-closure run (TrainTicket fabricated-ack, traced)

**Date:** 2026-07-11
**Plan:** `debug/a-main/c2c3/e2-discrimination-plan.md` rev 2.1 (re-review UNANIMOUS, all 3 cleared execution)
**Case:** `TT-cancel-refund-fabricatedack-001` (TrainTicket fork, `drawbackFaultMode=fabricatedack`)
**What this IS (not the paper headline):** (1) PROVENANCE CLOSURE — the flagship discrimination cell
was a manual-curl PRE-REGISTRATION; it is now HARNESS-RUN-BACKED on a traced deploy; (2) a bounded
SPECIFICATION-LOCALITY capability datum. The real discrimination headline (a NATURAL trace-miss case)
remains the S3 wild-hunt, deferred/rater-gated — this run de-risks it, does not substitute.

## What ran (one coherent traced deploy)

- **Deploy:** TrainTicket revived; the cancel path (`ts-cancel-service`, `ts-inside-payment-service`,
  `ts-order-service`) RE-INSTRUMENTED with the OTel javaagent 1.33.6 (`OTEL_TRACES_SAMPLER=always_on`,
  exporter → `jaeger-collector.istio-system:4318`, DEFAULT instrumentation). Canary verified: a cancel
  with an injected traceparent is resolvable in Jaeger by that id, carrying the entry + drawback + the
  `INSERT ts.inside_money` DB-client span.
- **Harness:** the existing `io.mist.cli.g3.CancelRefundHeadToHead` constructed stratum + `TrainTicketStimulus`
  (MIST value-delta read-back on the buyer `/account` balance; `requirePreFundedBaselines` +
  `requireClaimEligible`), with the disclosed **C1** change (stimulus injects a client W3C traceparent,
  the harness emits each leg's cancel trace-id for out-of-band scoring; read-back stays TIMEOUT-gated).
  **N = 5 fresh JVM invocations, constructed-only.**
- **Trace comparator:** the frozen `trace_score.py` with the pre-registered **C2** 3-config selectors
  (naive error-span / service-map-granularity presence / **DB-span-granularity presence**), scored on
  each leg's id-selected trace.

## Result (N=5, perfectly consistent)

| oracle | control leg | fault leg |
|---|---|---|
| **MIST read-back** (value-delta, harness) | no fire | **FIRE 5/5** |
| response-contract comparator (harness) | no_flag | **MISSED 5/5** |
| naive error-span trace oracle | no_flag | **no_flag — MISS 5/5** |
| service-map-granularity presence trace oracle | no_flag | **no_flag — MISS 5/5** |
| **DB-span-granularity presence trace oracle** | no_flag (INSERT present) | **flag — CATCH 5/5** (INSERT absent) |
| ground truth — `/account` value-delta | 50.00 → **130.00** (refund landed) | 50.00 → **50.00** (refund lost) |
| ground truth — direct `inside_money` DB (P4) | `A 50` + **`D 80.00`** (drawback present) | `A 50` only (**no drawback**) |

Evidence (all 5 runs independently auditable — reviewer A-1/B-1/C-F2): `e2-run.sh` (the runner of
record + exact `-D` matrix + deploy pins), `e2-run-stdout.txt` (all 5 runs' harness stdout: FIRE,
gate `TIMEOUT_ABSENT` @ ~20 polls, MISSED, correlator-unique, value-delta), `e2-run{1..5}-{control,fault}-trace.json`
(all 10 per-leg traces, re-scorable with the frozen `trace_score.py`), `e2-run-summary.txt`,
`e2-trace-scores.txt`, `e2-p4-db-groundtruth.txt`.

**N-scope (reviewer C-F3):** N=5 replicates a DETERMINISTIC by-construction skip (a flakiness/determinism
check), not five independent samples; no rate/recall is claimed.
**Gate (reviewer B-2):** the read-back used the DEFAULT timeout (10 s; `mst.oracle.dataintegrity.timeout.ms`
not overridden) — the fault-leg absence at ~20 polls is sound because the fabricated-ack skip is permanent
and control landed well within the cap on all 5.

## The claim — specification-locality (A-MAJOR wording, NOT "out-of-the-box")

On this acknowledged-but-lost write (clean `{status:1,"Success."}` ack, clean trace), the naive and
**service-map-granularity** trace oracles MISS; a **DB-span-granularity** trace assertion CATCHES; and
MIST's durable-value read-back CATCHES. The honest distinction is **granularity + implementation-coupling,
NOT zero-authoring**: MIST's read-back is specified ONCE per SUT (a triple: readback endpoint + isolation
key + value probe — costed ~1–2 h/SUT in 2.75-A) at the **durable business-outcome** granularity (the
refunded balance), whereas the DB-span assertion is coupled to the **exact internal write span**
(`INSERT ts.inside_money`) the author must know to assert on. **We ARGUE BY INSPECTION — we do NOT
measure it (reviewer C-F1/A-3) — that the read-back's durable-outcome granularity is robust to the drop
mechanism and to a persistence refactor:** a read of the durable balance is indifferent to *how* the
balance failed to move (a skipped INSERT here; a rolled-back tx or a wrong-account write would present
the same balance-unchanged), while a span assertion names one specific span. **THIS RUN measures exactly
ONE drop mechanism (the skipped INSERT);** the cross-mechanism robustness is a design-inference from the
specification granularity, not a measured generality. Same detection here; the read-back's specification
is coarser-grained and implementation-decoupled.

**NOT claimed:** "beats trace" (a DB-span assertion catches it); "assertion-free" (read-back needs a
per-SUT binding); prevalence/recall (N=5, one SYNTHETIC fork fault shaped to be trace-clean).

## Anti-circularity (two guards, plus a genuinely orthogonal one)

1. The read-mechanism validator is the paired **control leg** — its value-delta shows the +refund present
   (50→130), so a broken read would null it → NOT_EVALUABLE, never FIRE.
2. The `/account` re-read wraps the same endpoint MIST reads (a store re-read, not an orthogonal oracle —
   the 2.75-A caveat).
3. **P4 orthogonal ground truth:** the direct `inside_money` DB read (mysql, distinct from the `/account`
   REST transport) confirms fault = no drawback row / control = drawback `D 80.00` present. **DISCLOSURE
   (reviewer A-2/B-minor-2):** P4 is a SEPARATE manual probe on its OWN fault+control buyer pair (not the
   5 harness-run buyers), so it is a *mechanism-class* orthogonality cross-check, not per-run orthogonal
   ground truth. The PER-RUN durable evidence for each of the 5 is the fault trace's DB-span absence
   (the `INSERT ts.inside_money` span present-on-control / absent-on-fault, from jaeger — a channel
   orthogonal to the `/account` read-back).

## Honest framing (carried verbatim; the reviewers' standing constraints)

- **Synthetic worst-case.** The fault is a fork flag *defined* as trace-clean + durable-absent; the
  capability datum is an existence/bounding result, a component/motivating example, NOT the headline.
- **The corpus does NOT rescue it as a natural discrimination.** Wave-2.75-A read-back cases are
  SOLE-oracle (TeaStore) or PRESENCE-CONCORDANT (OTel) — ZERO are natural "trace-runs-and-misses" instances.
  The discrimination-over-trace rests solely on this synthetic fork; the corpus gives read-back
  *applicability breadth*, not natural discrimination.
- **The owed headline stays owed:** a NATURAL fault where an in-practice trace oracle misses and read-back
  catches = the S3 wild-hunt (rater-gated, deferred). This run de-risks it (the traced read-back path is
  proven end-to-end), never substitutes.

## Cell change (atomic with the freeze row)

`TT-cancel-refund-fabricatedack-001.mist_readback_oracle` stays `flag` but is upgraded from
PRE-REGISTERED (manual `/account` curl) to **HARNESS-RUN-BACKED** on the traced deploy where the 3-config
trace comparator is measured; the DB-span-granularity comparator result is added to the case notes.

## Environment disclosure (the run was expensive)

Reviving a full TrainTicket for E2 over-committed the 26 GB WSL (OTel-Demo + TeaStore were up) → a RAM
wedge. Recovery (all user-authorized): OTel-Demo + TeaStore scaled to 0 (`e2-ram-teardown-note.md`; the
2.75-A "tenants UP" end-state is superseded, measurements unaffected); a `wsl --shutdown` + a forced
Docker Desktop restart; and the TT Xenon MySQL HA cluster's cold-start deadlock was broken by
force-recreating `tsdb-mysql-0`. TT was then brought up as the cancel-refund subgraph (non-path services
scaled to 0 for RAM). Runbook addition: **a force-delete of the stuck mysql-0 pod re-forms the Xenon
quorum after a hard restart.**

## Reproducibility posture (reviewer C-F4)

This is an **auditable** result, not reproducible-from-scratch: the committed per-leg traces + the frozen
`trace_score.py` re-score to the same verdicts (all three cold reviewers independently re-ran the scorer
and reproduced them), and the runner + `-D` matrix + deploy/agent pins are recorded. But the live
deployment is NOT turnkey — it required the disclosed manual environment recovery (RAM wedge →
`wsl --shutdown` → Docker Desktop restart → `tsdb-mysql-0` force-recreate → cancel-subgraph-only). The
measurement stands on the committed evidence regardless of the environment being gone.

## Post-run (3-cold review — DONE)

3-cold-reviewed (`REVIEW-E2-RESULT-RECONCILIATION.md`): A (oracle-soundness), B (engineering), C
(hostile-PC) all **ACCEPT-WITH-FIXES, 0 blocking**; all three independently reproduced the verdicts from
the committed traces. Fixes folded: the mechanism-robustness claim relabeled argument-by-inspection
(C-F1/A-3); all 5 runs' evidence + runner committed (B-1/B-2); P4-is-a-mechanism-cross-check, N-is-
determinism, auditable-not-turnkey, and the stale-Javadoc/C1-test-guard all addressed.
