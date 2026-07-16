# SPIKE — the contract-invariant comparator arm (completion-set wave, Phase A item A2)

**Date:** 2026-07-16 · Status: EXECUTED (offline; no tenant) · **Recommendation: GO**
(run the arm in Phase B through the A6 harness).

## What the arm is

Single-response CONTRACT invariants derived from the committed OpenAPI specs (all 6 exist:
the E1 clean-room teastore/oteldemo pair + trainticket merged 3.0.3 + shipped
sockshop/bookinfo/boutique): validate each case's ACK evidence (status + body) against the
spec's success-response schema for the entry endpoint. AGORA-class positioning
(single-response invariants — cite ISSTA'23 vs TOSEM'25 precisely); NO cross-call/lifecycle
invariants (those require read-back semantics, i.e. the compared mechanism itself).

## Feasibility findings (verified against the corpus, not argued)

1. **The corpus already stamps the expectation per case:** `oracle_expectation` carries a
   comparator battery per case; aggregated over all 26 cases:
   `schema_oracle: no_flag 26/26` and `status_code_oracle: no_flag 26/26` — every
   POSITIVE (11/11) is contract-conforming BY DEFINITION of the masked-2xx class (the ack
   is success-shaped; e.g. the fabricated-ack's live-verified `HTTP 200 {1,"Success."}`).
2. **Blind-authoring holds by construction:** the invariant spec = the OpenAPI
   success-response schemas, authored upstream of every outcome (E1 clean-room discipline;
   shipped specs predate the corpus) — request/spec side only, zero outcome contamination.
3. **Inputs exist:** ack transcripts live in the committed capture sidecars (A8-mapped);
   specs 6/6 on disk.
4. **Authoring cost ≈ 0 per endpoint** (the invariants ARE the specs; the runner is a
   small validation script) — record THIS as the arm's `authoring-cost` cell: the
   automation-gap datum cuts BOTH ways (cheap to author, blind to the class).

## Why run it at all (the value of a by-construction MISS column)

The arm's expected cells = 0 flags on all 26 (recall 0 in every visibility class,
including on the 11 positives). Running it MEASURES what is today only a design stamp —
the paper's "contract/schema oracles cannot see acknowledged-but-lost writes" argument
gets a mechanical cell (Gate-4 wording: "3 frontier trace comparators + contract-invariant
arm"), and any deviation from 0 (a spec strict enough to reject a real ack) would be a
finding, not an embarrassment. Rails: matched-recall framing; the cells flow through the
A6 harness like every arm; the arm is labeled `contract-invariant (single-response,
spec-derived)` — never sold as a lifecycle oracle.

## GO conditions for Phase B (pinned)

- Verdict source: offline validation script over A8's ack-evidence pointers + the 6 specs;
  committed under `b4/e2/`; verdict file `scoring/verdicts/contract_invariant.json`.
- NOT_EVALUABLE: cases whose ack transcript is absent from committed evidence (enumerate
  at run time; disclosed) — never guessed.
- The `oracle_expectation.schema_oracle` stamps are NOT consumed by the runner (they are
  the prediction; the run is the measurement — self-reference broken).
