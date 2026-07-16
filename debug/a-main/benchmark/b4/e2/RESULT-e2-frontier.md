# RESULT — E2 comparator-frontier COMPLETION (completion-set wave, Phase B) — Step 6 fold

**Date:** 2026-07-16 · Status: EXECUTED (fully OFFLINE — no tenant, no MIST run; the
frozen scorer + banked live evidence only). Plan: `wave-completion-set-plan.md` rev 2
(CONFIRMED 3/3). DoD gate = post-hoc 3-cold review at the wave close.

## What ran

The FROZEN `b4/trace_score.py` (reviewer-reproduced, pre-registered selectors) swept over
every A8-mapped committed bundle — 11 case-legs (the flagship pair on the TT-omnibus
leg1 CO-GENERATED traces ×5/leg, verdict-UNIFORM; 9 traced-captures) — through
`run_frontier_arms.py`, emitting per-arm verdict files scored ONLY by the A6 harness
(`scoring/score_arms.py` → `matched-recall-table.json`).

## The completed table (per-visibility-class; NO pooled recall — the rails hold)

| arm | error-span-visible (1p/2n) | span-presence-visible (3p/6n) | invisible-by-construction (2p/2n) | uninstrumented (5p/5n) |
|---|---|---|---|---|
| **mist_readback** (A5 column) | 0-evaluable (structural n_e 3) | **3/3 flag**, 0 FP | **2/2 flag**, 0 FP | 2/2-evaluable flag, 0 FP (n_e 5) |
| naive_span_error | 0/1, **2 FP** (bookinfo benign + SS control noise) | 0/3, 0 FP | 0/1-evaluable, 0 FP | n_e 10 |
| tracetest_presence **(SURROGATE: span-assertion-semantics; the live tool was NOT run)** | **1/1 flag** (swallowed consumer-span) + 1 FP (bookinfo benign trap) | **3/3 flag**, 0 FP | 0/1-evaluable (fabricated-ack MISS) | n_e 10 |
| db_span_presence (specification-locality) | n_e | n_e (no frozen db selector) | **1/1-evaluable flag = the fabricated-ack CATCH** | n_e 10 |
| contract_invariant (single-response, live cells) | n_e | n_e | 0/1-evaluable (conforming ⇒ no_flag — the by-construction MISS, measured live ×5) | n_e |
| traceanomaly | not_evaluable 26/26 — the pre-registered CONSTRUCTION-BLINDNESS branch (below) | | | |

**The N-vs-0 row (2 invisible positives: fabricated-ack + createaccount-agreement):**
MIST 2/2 · shape-level trace arms 0 · db-locality 1/1-evaluable (it exists ONLY because a
human pre-specified the exact skipped INSERT — the specification-locality argument, now a
measured cell) · contract 0/1-evaluable.

## Verification spine

- **Measured-vs-stamped: 0 mismatches** — all 11 scored case-legs match their capture-time
  `oracle_expectation` stamps exactly (incl. the subtle ones: SS-control naive FP noise;
  the swallow emitting NO error span; bookinfo's real-error-span benign trap).
- **Flagship import cross-check EXACT vs `e2-trace-scores.txt`** (naive MISS /
  service-map-presence MISS / DB CATCH ×5) — and reproduced on the OTHER trace generation
  (banked cells = e2-run traces; this sweep = leg1 co-generated traces): a
  cross-generation consistency datum.
- The MIST column flows from the A5 census through the SAME harness (provenance_class
  live-run vs capture-concordant carried per cell; self-concordance rule enforced
  structurally — no pooled headline exists in the artifact).

## Arms of record + honest labels

1. **naive_span_error** — frozen scorer, mechanical, no exclusions.
2. **tracetest_presence SURROGATE** — the frozen EXISTENCE-ONLY assertion; cells labeled
   verbatim "span-assertion-semantics (surrogate; the live tool was NOT run)". The REAL
   Tracetest specs were AUTHORED offline (`tracetest-specs-authored.yaml`,
   authored-never-executed) for the authoring-cost cell: a mechanical transcription of
   the frozen selector table (banked selector-authoring cost ~12 min / 4-case table);
   the costly part is KNOWING the span to assert — symmetric with MIST's per-case
   `mist_authoring` minutes (e.g. cancel-refund triples = 15 min, stamped in-case).
   Live 2.5.5 install = declared out of scope (a TT-omnibus-scale window), disclosed.
3. **db_span_presence** — the frozen DB-granularity existence assertion (E2/C2).
4. **contract_invariant** — LIVE flagship cells (the banked frozen response-contract
   comparator lines, 5/5 conforming) + 24 not_evaluable: **the committed capture sidecars
   persist response payloads as NULL** (run-time survey) — the A2 spike's "ack transcripts
   live in the sidecars" input claim was WRONG and is corrected in the spike file.
5. **traceanomaly — NOT CLEARED (actual check, not armchair):** repo shows NO visible
   LICENSE (all-rights-reserved default), Python==3.6 unmaintained stack, and an input
   contract of a NORMAL-TRACE TRAINING CORPUS (vectorized train.zip) that the benchmark's
   per-case volumes (1-5 traces/leg) structurally cannot supply ⇒ the pre-registered
   construction-blindness branch: cells not_evaluable BY THE METHOD'S OWN INPUT CONTRACT
   (it answers production anomaly detection over trace populations, not per-case test
   oracles — research/02 positioning).

## NOT_EVALUABLE disclosures (nothing silent)

- eventual-benign-002/-003: NO FROZEN SELECTOR (post-hoc selector authoring barred by the
  pre-registration discipline); -001 additionally has no committed bundle (w120 natural).
- createaccount pair: invisible-by-construction + no bundle (in-process persistence, T7).
- trace-uninstrumented tier: 10 cases (Kieker-only TeaStore + untraced TT legs).
- A8 v1 CORRECTION folded: the adminbasic CONTROL traced bundle EXISTS
  (`tt-s2-adminbasic-contacts-control-traced/`) — none-committed 4→3; scored this sweep.

## Folds

- **Checklist Step 6 → ✔ AT THE OFFLINE SCOPE** (all non-conditional arms produced cells
  at the pinned scoring; conditional arms resolved per their pre-registered branches:
  TraceAnomaly = construction-blindness, contract-invariant = live-cells+enumerated-n_e;
  live-tool cells declared out-of-scope-disclosed). The E2 matched-recall obligation is
  now carried by ONE mechanical artifact (`scoring/matched-recall-table.json`).
- Freeze row lands with this RESULT; 3-cold review at wave close (with Phase C).
