# The rater case corpus ("待评案例库") — composition, dependencies, build order

**Why (user directive 2026-07-09):** the grad-student raters can only start once a complete,
normalized case corpus exists. This document pins WHAT the corpus is, what each part DEPENDS on,
what can be built NOW vs only after step 2, and the resulting order — so the checklist's scattered
items (2.75 seed migration · step-4 M-yield audit · step-5 B4 harness + calibration + S3) get one
authoritative view. **REV 2** — review B's findings folded (`REVIEW-CORPUS-B-dependencies.md`: B1 + M1–M6); review A covered the README pass separately.

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
2. **The seed calibration subset — REV 2 (B1: the raw material does NOT exist yet).** All 6 v0.1.0
   seeds are `capture_status: specified` (provenance null); the G1/G3/SS artifacts are verdict-level
   summaries (no request payloads, no full ack bodies — RunRecord never captured them). So the seed
   subset = rev-2 migration **+ SHORT CAPTURE RUNS at the pinned MIST commit** against the
   already-deployed TT (SS needs a re-warm window — big-SUTs-solo tenancy: schedule the SS capture
   when TT is scaled down or accept disclosed co-residence), with **harness-level transcript
   capture** (keeps the pin intact) writing the §5 sidecar format; cases flip
   `specified → captured`; only then are they B4 fixtures / calibration cases. Pre-pin July artifacts
   are freeze-invalid for cases anyway (mist_commit-IDENTICAL + seeds-re-recorded-at-pin).
3. **Rater-materials ≥3-cold-review + IRB filing** (1.9.6 remainder; channel = user-decided
   in-group MIST-blind grads). Runs while the corpus grows.

## §3 The order (decision, with the evidence)
```
NOW (parallel track):  sidecar format → B4 harness build → seed migration + CAPTURE RUNS @pin → seed calibration subset
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
- ✔ correct: M-yield audit at step 4; calibration sizing (~30, pooled ≥50 free) at step 5; S3
  sampling rule min(all,40). **(REV 2/M2: seed migration at 2.75 was ALSO mis-timed — the governing
  plan §2.4-2 puts asset promotion BEFORE the deploy waves; the NOW-track move RESTORES the plan's
  ordering. benchmark/README §9's "step-2 task" sentence is equally stale. Checklist items are
  single-homed at §1.95 with pointer stubs at 2.75/step-5.)**
- ✘ mis-timed: **B4 harness build sat inside step 5** — it has zero deploy dependencies and is the
  long-lead factory everything flows through. Moved to the NOW-track (checklist §1.95 added).
- ➕ missing: an explicit "corpus assembly" gate before rating (all three strata through the SAME
  harness version; §0 tell-audit run on the final mix; freeze the corpus manifest + hashes before
  the first rater sees it). Added as step 5's entry gate.

## §5 B4 harness — build spec (the NOW-track deliverable; REV 2 per B1/M3/M4)
- **Deliverable 0 (FIRST): the rater-artifact SIDECAR FORMAT** — per case: ordered request records
  (method/path/payload), response records (status + full body), durable-state observations
  (read-back/probe bodies) with RELATIVE times only, producer + mist_commit stamped. Every producer
  (seed capture runs, step-4 M-yield, step-5 wild-flag capture bundles) emits THIS format;
  `artifacts.raw_logs` points at it (freeze §6 amendment row for hygiene, no frozen-key change).
- Input: a rev-2 case file + its sidecar. Output: `rater-cases/<opaque-id>/case.md` (the
  §0-compliant view) + `ballot.yaml` skeleton; a `manifest.json` (opaque-id ↔ true-id mapping +
  RUBRIC VERSION, SEALED — not shipped to raters; hash-frozen at assembly).
- Invariants (tested, incl. a leak-fixture case): **explicit STRIP-LIST — label.*, fault.*
  (mechanism/injection!), oracle_eval.readback.expect_*, oracle_expectation.*, negative_control,
  title, trace attachments, and every tool string** never reach a rater-facing byte (the transform
  never READS label fields; S3 inputs arrive label-less); no clean-run column; opaque ids carry no
  stratum signal; **absolute timestamps normalized to relative offsets (relative durations KEPT —
  judgment-relevant) and B4 never stamps its own clock**; artifact richness/format uniform across
  strata (different producers must not yield distinguishable case shapes); per-case pointers resolve
  into the **version-pinned per-SUT docs/spec/source BUNDLE** (vendored snapshot or pinned-commit
  URLs — the ONLY pointers raters get; rater rule: provided bundle only, no web search — M6);
  deterministic output (same input → same bytes).
- Fixtures: the seed calibration subset (§2.2).
- Non-goals: no scoring, no adjudication tooling (κ math lives in the analysis scripts at step 5).
- **Wild-capture ownership decision (B1-fix-3, decide BEFORE step 5):** detector-(ii) single-leg
  flags need the request/response + durable-probe captured at flag time; writer/test-level capture =
  MIST tool code → a disclosed pin amendment; harness/proxy-level capture keeps the pin. Detector-(i)
  trace-shape flags perform no read-back at all → the step-5 pipeline MUST run the capture bundle
  (request sequence, response, durable-state probe, relative times — the sidecar format) at flag
  time, else S3 cases cannot be rendered rater-facing (M1).
- **Blindness × upstream-filing rule (M6):** for any case in the rated set, upstream filings are
  deferred to study close OR de-identified (behavior-only, no tool name) until close.

## §6 What this changes in the checklist (REV 2)
- NEW §1.95 (parallel track, starts now): sidecar format → B4 harness → seed migration + capture
  runs @pin → calibration subset + eligibility cases; rater-materials review + IRB stay 1.9.6.
- **Single-homing (M2):** seed migration + B4 build live ONLY at §1.95; the 2.75 and step-5 entries
  become verify-pointers ("moved to §1.95 — VERIFY done, same harness version").
- Step-5 corpus-assembly ENTRY GATE (REV 2/M5, 8 checks): same-harness-version · tell-audit (incl.
  timestamp + cross-strata shape uniformity) · sealed manifest (incl. RUBRIC VERSION) · corpus hash
  freeze · machine disjointness (calibration ∩ S3 ∩ M-yield-audit ∩ eligibility = ∅ by true id) ·
  every rated case `capture_status == captured` · IRB determination RECEIVED · wild-flag capture
  bundles present for every S3 case (M1).
- benchmark/README §9's "migration is a step-2 task" sentence is stale → §1.95 (noted there).
