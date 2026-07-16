# PAPER DRAFT PLAN (a-main, A-venue empirical/benchmark track) — rev 2

**Date:** 2026-07-16 · Owner: main_track · Status: **rev 2 — the COMPLETION SET CLOSED
(086cf68), so THIS PLAN'S REVIEW RE-ENTERS per its own rev-1 hold terms; the claim map is
refreshed to the post-wave evidence state. DRAFTING ITSELF REMAINS BEHIND THE EXPLICIT
USER-CONSENT GATE (user 2026-07-16) — an ALL-ACCEPT review of this plan makes the draft
START-READY, it does NOT start it.** Rev-1 history: deferred by the user pending the
completion set; the "experiment surface fully complete" overstatement corrected (it was
Step-4-scoped).

## §0 What this paper is (from the reviewer-cleared direction, `debug/a-main/README.md` v7)

**Primary A-path (Cast-independent):** C2 (the first OPEN-SOURCE labeled benchmark of
masked/data-integrity faults) + C3 (defect-yield / oracle-eval study design + what the corpus
supports today) + C1 (the accessible black-box capability with a MEASURED oracle-FP).
The reframed one-line claim (README §0, verbatim base — no stacked-"first"):
> MIST makes silently-masked cross-service failures and — on write-path services with a
> black-box read-back — acknowledged-but-lost writes testable without production traffic and
> without AOP / per-service assertions, using only the OTel a system already runs plus a
> label-free read-back differential oracle; together with an open-source labeled benchmark of
> such faults and a measurement of how often they are genuine defects.

Cast concession UP FRONT (README §2 table verbatim); Microusity/Filibuster/AGORA/MINES
positioned with the verified citations only. SmartFetch is a SEPARATE paper (b-smartfetch) —
zero claim overlap permitted (no-double-claim policy; SmartFetch appears here at most as a
named generator component, never as a contribution).

## §1 The draft's evidence spine — claim → provenance map (the load-bearing table)

Every drafted claim carries one of these provenance anchors (freeze `c2-freeze.md` §5/§6 +
RESULT files). NO claim without a row here; reviewers should treat a claim outside this map
as BLOCKING.

| # | Paper claim (honest form) | Provenance |
|---|---|---|
| P1 | Corpus: **27 cases (12 pos / 15 neg)**, schema-validated, 6 SUTs, provenance classes + `readback_shape` census (9 present-landed / 3 eventual-present / 2 reject-no-delta / 1 degraded-present; positives omit the field) — the 27th = the additive kafka S1 stochastic case | freeze §5 + R1d row + the Phase-C row (2026-07-16); `benchmark/cases/**` validator-green 27/27 |
| P2 | 8 distinct positive SITES (TT4/TS2/OTel1/SS1; ~13 w/ F-corpus) — the disclosed positive-side widening CLOSE after 3 rejected attempts | freeze §5; R1b/R1c rows |
| P3 | STRUCTURAL SCARCITY datum 1: wild-hunt 0 CONFIRMED / N=1514 / K=5 (≤0.20% one-sided; pre-registered) — scarcity IS the finding; NO natural discriminator exists in this regime | S3 rows (2026-07-12/13); `RESULT-s3.md`; per-window commits 10eb19e/5802fa8/0fbe00f, classifier byte-identical |
| P4 | STRUCTURAL SCARCITY datum 2: write-acked-absent presence-defuser floor 0/≥8 — a clean-ack empty read-back IS a masked loss (structural, not effort) | R1d row; `RESULT-r1d.md` + `r1d-phase0-findings.md` |
| P5 | MIST read-back enablement: paired FIRE 5/5 ground-truth-verified on BOTH corpus SUT legs (2.75-A) | freeze 2.75-A row; commits + capture logs |
| P6 | **The COMPLETED E2 matched-recall table: 6 arms × 4 visibility classes through the single scoring harness** — MIST 9/9 evaluable positives + 0/15 FP; naive 0 positives + 2 FP; Tracetest-presence SURROGATE (labeled; live tool never run) 4 positives + 1 FP + invisible-MISS; db-locality 1/1 invisible CATCH (specification-locality measured); contract-invariant live flagship cells (by-construction MISS measured ×5); TraceAnomaly construction-blindness (not-cleared by actual check); measured-vs-stamped 0 mismatches; matched-recall framing ONLY (never "discrimination"); no pooled recall exists in the artifact | `benchmark/scoring/matched-recall-table.json` + `RESULT-e2-frontier.md` (post-Phase-C refresh) + TT-omnibus row + E2 row (5942bab) |
| P7 | Trace-gated tier: the first CONTROLLED pre-registered either-way measurement in the c2c3 record — observe fault 5/5 OBSERVED_COMPLETE_ABSENT + control clean; NEVER "first in any run of record" (G1 2026-07-02 fired it 126×, disclosed) | TT-omnibus row; leg-1 report |
| P8 | E5 ablation: exact-4 OAT × 5 reps uniform; A2 (trace gate) = the ONLY verdict-tier-moving axis (C1 vs C2 contrast) | TT-omnibus row leg-3; `b4/ttomni/leg3/` |
| P9 | M-yield: 6-SUT set, 29+10 seeds, 5145+~2700 tests, 26 clusters + reps + cross-seed 10% audit; NO yield statistic (rater-gated); flags on TT complete seed = 0 (S3 prior held) | MYC row (2026-07-16) + `RESULT-myield-completion.md` |
| P10 | The observe-starvation finding (CORRECTED mechanism — the paper text uses ONLY this form): observe arms ONLY at the enhancer final round; budget-capped runs DO reach it (SS 10/10 armed) but the armed stretch is STARVED (SS all-3440-writes-500 at the type-naive tier + mid-round kill + jaeger-off tier cap; TT 4/10 armed w/ triple-coverage miss) | MYC row; RESULT finding section; MistRunner L1730/1757-58 |
| P11 | Oracle-FP measurement: sync FP 0/2127 acked (interval [0,0], gate histogram 100% observed) + FP-vs-timeout curve (500ms→12.98%, 1s→0.14%, ≥2s→0.0) + packaged SS FP corpus 0/1200 | G1 row (gate1-result.md, run-3 report JSON); freeze §5 packaged-FP |
| P12 | Authoring-cost symmetry: `mist_authoring` table (TeaStore ~25 min / OTel ~15 / SS ~5 / BI+Boutique 0) vs comparator per-endpoint authoring cost (E2 arm-3 obligation) | MYC row enablement table; E2 prereg |
| P13 | Scope/Limitations of record: detects acknowledged-but-LOST (absence), NOT acknowledged-but-CORRUPTED; observe-mode CORRECTLY ABSTAINS on eventual-consistency (TIMEOUT_ABSENT = WARN-only; the naive at-cap comparator is what FPs); single-box; induced-vs-natural mix; TT-only exception text | R1c-A + R1d-B rows (source-verified, memory-pinned) |
| P14 | Rater-study DESIGN + its disclosed shortfalls (calibration ≪50, binding=benign; S2 floor ≥35 unmet w/ earned-exhaustion documentation; rateability census 14 ok / 9 truncation-gated / 1 trace-only / 1 async-ineligible) | R1d + E1+R2 rows; `MANIFEST-r2.json` |
| P15 | Benchmark release engineering: neutralized rater-sidecars rev 3 (0-leak, hardened BANNED_STRINGS + opaque-id guard), clean-room OpenAPI specs + coverage gate, license audit incl. OpenAPI-provenance heterogeneity | E1+R2 rows (7404873/d4a6c96 + corrections) |
| P16 | **The C1×C2 integration layer (the benchmark-consumes-the-tool machinery):** the 27-case MIST-column census (flag 9 / no_flag 13 / principled-n_a 5, ZERO silent pending, per-cell provenance incl. live-run vs capture-concordant [self-concordance rule]); the scoring harness as THE single mechanical path; the visibility census (two uninstrumented senses); the existence-verified bundle map; release staging w/ the reproduction census (**26/27 executable-reproducible**) | completion-set Phase A+C rows (96cbbaa→086cf68); `benchmark/{mist-column-census,e2-visibility-census,case-trace-arm-map}.json` + `scoring/` + `release-staging/` |
| P17 | **The kafka stochastic S1 measurement:** vendor-flag permanent production loss — control 10/10 landed vs fault 19/20 LOST at the T+5min binding re-probe (rate 0.95, Wilson95 [0.764, 0.991]); wedge-past-flag-off reproduced + healed by the pinned recovery-restart; post-recovery permanence re-probe; SECOND attempt of record (the first STOPPED at probe). Frame as a measured vendor-fault characterization + corpus case, never a SUT-defect-rate claim | Phase-C row (34b2f8d); `b4/cset/kafka-s1/` + the case file |
| P18 | Seal-prep decision surface (rater-study §): the calibration rehearsal quantifies the structural shortfall in EVERY branch (S0=14/36, S1[+TT-re-capture]=22/28, S2=15/35); the TT per-endpoint rendering EVIDENCE-BLOCKED finding; the ack-text tell VACUOUS for the current packet (measured: zero rendered ack payloads in the rateable-ok 14) — all USER-decision inputs, reported as design/disclosure, never as executed rating | A3 staging (d089062 + the wave-close addendum); `b4/rater-sidecars-staging/` |

**Rater-gated numbers (κ, genuine-vs-benign yield, M-prevalence, calibration outcomes) appear
ONLY as STRUCTURED PLACEHOLDERS** — a table shell + the pre-registered estimand wording, each
cell stamped `[RATER-GATED: Step 5]`. The draft must read coherently WITH the placeholders
(the S3-scarcity + benchmark + capability spine carries the paper even before rating).

## §2 Paper skeleton (sections + which P-rows feed them)

1. **Introduction** — lead with the STUDY (honesty rider), the one-line claim, contributions
   C2/C3/C1, the two scarcity data as first-class findings (P3, P4). Explicitly NOT-first
   (Cast concession forward-referenced).
2. **Background + the fault class** — masked-2xx / acknowledged-but-lost; Cast/Microusity
   occupy detection primacy (README §2 table); the TOSEM'23 open-oracle gap as framing anchor.
3. **MIST's oracle semantics (the mechanism section)** — paired (differential value-delta;
   trace-gate-independent) vs observe (trace-gated defect tier; WARN-only abstention);
   lost-not-corrupted scope (P13); the metamorphic read-back formulation.
4. **The benchmark (C2)** — P1/P2/P4/P14/P15: schema, provenance classes, shape census,
   neutralization pipeline, license audit, packaged-FP corpora, the 8-site disclosure,
   counting conventions (distinct-site definition, two-denominator S1).
5. **Study design (C3)** — rater protocol + calibration/floor shortfalls AS DISCLOSED
   findings about the fault class's supply (P4/P14); IRB = pending user-side (stated).
6. **Experiments (executed record)** — E2 matched-recall (P6); trace-tier controlled
   measurement (P7); E5 (P8); M-yield (P9/P10/P12); FP measurement (P11); S3 (P3).
7. **Results + placeholders** — the executed cells + `[RATER-GATED]` shells.
8. **Threats / Scope / Limitations** — P13 + single-box + induced-provenance + the
   self-concordance rule (never pool self-concordant read-back cells into headline recall)
   + tell-free floor + authoring-cost symmetry (P12).
9. **Related work** — README §2 verbatim positioning, verified citations only (AGORA vs
   AGORA+ kept precise; Filibuster-DB claim = abstract-supported only).
10. **Artifact/availability** — benchmark repo plan (E6 = USER-gated fork-pub decision;
    the draft states the release WITHOUT executing it).

## §3 Deliverables + tree

- `debug/a-main/paper-draft/` — one md file per section (`00-abstract.md` … `10-artifact.md`)
  + `CLAIM-MAP.md` (the §1 table, kept in lockstep with drafted text; every drafted claim
  cites its P-row inline as an HTML comment).
- Venue-neutral markdown FIRST (venue election = USER; ISSTA/FSE/ICSE all fit README §3);
  LaTeX conversion is a later, separate step. NOT under `paper/` (that tree = the ISSTA'26
  tool-demo, a different artifact).
- FILE_INDEX + freeze: a DRAFT-OPENED row when drafting starts; section files indexed.

## §4 Process (the /goal review discipline)

1. THIS PLAN → 3-cold review (independent axes: PC-realism/claim-honesty; evidence-fidelity
   of the claim map; structure/feasibility) → reconcile → ALL-ACCEPT gate (confirmation pass
   if heavily revised — R1d precedent).
2. Drafting in 3 waves, each ending in a wave review: W1 = skeleton + Intro + Background +
   Mechanism (sections 1-3); W2 = Benchmark + Study design + Experiments (4-6); W3 = Results
   + Threats + Related + Abstract polish (7-10 + abstract).
3. Full-draft 3-cold review at the end (the DoD gate) → fold → the draft-of-record.
4. Every wave commits on `main_track`; reviews local-only; FILE_INDEX/memory sync per wave.

## §5 Hard rails (memory-pinned; a reviewer finding ANY of these violated = BLOCKING)

- NEVER "first in any run of record" for the trace tier (P7 wording only).
- NEVER "discrimination" for E2 — matched-recall vs comparators; the natural-discriminator
  question was S3's and CLOSED (0/1514).
- NEVER pool self-concordant read-back cells into a headline recall.
- NO yield statistic anywhere (rater-gated); test-level failures are pipeline outcomes.
- The observe finding uses ONLY the corrected starvation mechanism (P10), never
  "never reaches the final round".
- Lost-not-corrupted + abstention-correctness stated in Scope (P13) — do not re-invert.
- Uber's 29.35% is swallowed non-fatal errors, NOT a defect rate (README §6 rail).
- The masking oracle (HiddenDownstreamFailure) is the NON-NOVEL part — Cast/Microusity own
  detection primacy; the claim is the combination (accessibility + benchmark + measured FP).
- No SmartFetch contribution claims (separate paper; no-double-claim).
- Rater/IRB/E6/venue = USER-side; the draft references them as pending, never executes.

## §6 NOT in scope

New experiments or captures; any MIST tool code; rater contact; kafka S1; the
contract-invariant arm run; E6 packaging execution; LaTeX/venue formatting; SmartFetch.
