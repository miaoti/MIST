# The rater case corpus ("待评案例库") — composition, dependencies, build order

**Why (user directive 2026-07-09):** the grad-student raters can only start once a complete,
normalized case corpus exists. This document pins WHAT the corpus is, what each part DEPENDS on,
what can be built NOW vs only after step 2, and the resulting order — so the checklist's scattered
items (2.75 seed migration · step-4 M-yield audit · step-5 B4 harness + calibration + S3) get one
authoritative view. → ≥3-cold-review with the README-UX docs pass, then execute.

## §1 What the raters actually receive (from `c3-rater-materials.md` §0/§6 + plan §3.1–3.2)
ONE normalized, interleaved set (B4 harness output; opaque ids; clean-run column stripped; no tool
verdicts/traces), composed of three strata the rater cannot distinguish:

| part | size | ground truth | raw-material source |
|---|---|---|---|
| **Calibration** | ~30 | known (S1 genuine / S2 benign) | existing reviewed assets + step-3a population |
| **S3 wild** | min(all flagged, 40) | UNKNOWN — the measurement | step-5 M-prevalence runs (wild detectors on instrumented SUTs, pinned workloads) |
| **M-yield audit** | 1 rep/cluster + 10% sample | UNKNOWN — the measurement | step-4 M-yield runs |
Plus, OUTSIDE the rated set: the §9 eligibility screen (2 unambiguous cases, disjoint from
everything — m7).

## §2 Dependency truth (the user's question answered)
**The corpus CANNOT be completed before step 2** — its measurement strata are outputs of execution:
- S3 wild needs: step 2 (deploys) + step 2.5 (instrumentation — the trace-shape wild detector needs
  spans) + step 2.75 (MIST enablement) + the pinned 12h/500-write workloads (step 5). No deploy → no
  wild flags → no S3.
- M-yield audit needs: step 4 (generation runs on the enabled SUTs).
- Even calibration wants breadth: ~30 balanced cases across SUTs need step-3a's new S1/S2 (TT-only
  calibration would let a rater pattern-match the SUT mix — the §0 distributional-tell audit).

**But three corpus components have NO step-2 dependency and start NOW (the parallel track):**
1. **B4 blind-label harness (the corpus factory).** Deterministic transform: case file (rev-2
   schema) + raw run artifacts → rater-facing case (SUT+version, request sequence, responses,
   observed durable state, docs/spec/source pointers; opaque id; clean-run column STRIPPED; no
   tool output) + a ballot skeleton (§4 of the rater materials). Develop + test against existing
   assets as fixtures. Checklist had it at step 5 — TOO LATE; it moves to the parallel track now.
2. **The seed calibration subset.** The ~10 reviewed existing assets (G1 adminroute/contacts, G3
   cancel + agreement cells, SS shipping/carts, the 6 v0.1.0 seed cases) → rev-2 migration
   (benchmark/README §9) → B4-normalized → ~8–12 calibration cases + the 2 eligibility-screen
   cases. Proves the harness end-to-end and de-risks the step-5 assembly.
3. **Rater-materials ≥3-cold-review + IRB filing** (1.9.6 remainder; channel = user-decided
   in-group MIST-blind grads). Runs while the corpus grows.

## §3 The order (decision, with the evidence)
```
NOW (parallel track):  B4 harness build → seed migration → seed calibration subset
                        ∥ rater-materials 3-review → IRB filing → eligibility screen ready
STEP 2/2.5/2.75:       deploys + instrumentation + enablement   ← the corpus's raw-material gate
STEP 3a:               S1/S2 population → calibration completed to ~30 (balanced mix)
STEP 4:                M-yield → audit sample cases through B4
STEP 5:                M-prevalence → S3 flags → B4 → THE COMPLETE CORPUS → raters start
```
**Answer to "库先还是 step 2 先": step 2 first for the corpus BODY; the corpus FACTORY + seeds now,
in parallel.** Raters start at step 5 by construction — which is also when the recruitment lead
(2–6 wk, started at 1.9.6) naturally lands. No idle waiting on either side.

## §4 Checklist correctness verdict (the user asked)
The checklist's items are CORRECT but scattered and one is mis-timed:
- ✔ correct: seed migration at 2.75; M-yield audit at step 4; calibration sizing (~30, pooled ≥50
  free) at step 5; S3 sampling rule min(all,40).
- ✘ mis-timed: **B4 harness build sat inside step 5** — it has zero deploy dependencies and is the
  long-lead factory everything flows through. Moved to the NOW-track (checklist §1.95 added).
- ➕ missing: an explicit "corpus assembly" gate before rating (all three strata through the SAME
  harness version; §0 tell-audit run on the final mix; freeze the corpus manifest + hashes before
  the first rater sees it). Added as step 5's entry gate.

## §5 B4 harness — build spec (the NOW-track deliverable)
- Input: a rev-2 case file + its raw artifacts (ack response, read-back polls / probe outputs,
  request sequence). Output: `rater-cases/<opaque-id>/case.md` (the §0-compliant view) +
  `ballot.yaml` skeleton; a `manifest.json` (opaque-id ↔ true-id mapping, SEALED — not shipped to
  raters; hash-frozen at assembly).
- Invariants (tested): no MIST/tool strings in any rater-facing byte; no clean-run column; opaque
  ids carry no stratum signal (random, not prefixed); per-case docs/spec/source pointers resolve;
  deterministic output (same input → same bytes, for the corpus hash).
- Fixtures: the seed calibration subset (§2.2).
- Non-goals: no scoring, no adjudication tooling (κ math lives in the analysis scripts at step 5).

## §6 What this changes in the checklist
- NEW §1.95 (parallel track, starts now): B4 harness + seed migration→calibration subset +
  eligibility cases; rater-materials review + IRB stay 1.9.6.
- Step 5 gains the corpus-assembly ENTRY GATE (same-harness-version, tell-audit, sealed manifest,
  corpus hash freeze) before any rater contact with cases.
- Everything else stands as reviewed.
