# PAPER DRAFT PLAN (a-main, A-venue empirical/benchmark track) — rev 3.1 (START-READY)

**Date:** 2026-07-16 · Owner: main_track · Status: **rev 3.1 — START-READY: the rev-2
3-cold (A 2B / B 2B / C 5B, zero REJECT) folded per `REVIEW-DRAFTPLAN-RECONCILIATION.md`;
confirmation pass A' CONFIRM · B' CONFIRM · C' FAIL→C'' CONFIRM on the one-line S1-pair fix
(c174259). DRAFTING STARTS ONLY ON THE USER'S EXPLICIT GO (§4 Step 0). **USER GATE 2026-07-16(b),
SUPERSEDING the sequencing: NO WRITE-UP OF ANY KIND until ALL experiments INCLUDING the
rater study (Step 5) are complete — the earliest consent point is post-rater-completion;
START-READY is parked until then.** Rev-1 history: deferred by the user pending the
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

> **[2026-07-21 AMENDMENT — corpus-of-record 27 + E-ANOM fold + OPERATING-POINT headline (user-
> approved reframe; mandate = the unanimous 3-cold expand-review `REVIEW-expand-{A,B,C}`, all
> EXPANSION-NOT-THE-LEVER).** Supersedes the stale figures inside P1/P6 (in-cell markers added):
> **(1)** benchmark-of-record = **26 (11 pos / 15 neg)** — the 6 F-corpus retired to
> `cases/excluded-fcorpus/` (5 `acknowledged_corrupted_write` = disclosed out-of-scope boundary
> appendix; F1 same-site-covered), AND **depdown RETIRED 2026-07-21** to `cases/excluded-out-of-mask/`
> (user-directed after the live MIST oracle REFUTED its 2026-07-20 curl "capture": NO_FIRE — the
> db-scale-0 producer 500s the followed confirm journey, a LOUD-500 loss NOT masked-2xx; 3-cold
> reviewed ACCEPT; `b4/RESULT-depdown-live.md` §2). depdown is a genuine lost-write but OUT of the
> 2xx-ack-gated masked scope; it becomes a boundary appendix (MIST correctly ABSTAINS on loud
> failures). **MIST read-back is UNCHANGED at 9/9 evaluable + 0 FP** (depdown was already n_a, never
> one of the 9 flags) — retiring it moves no headline number. **(2)** the
> E2 table is **11 arms**: E-ANOM ships as the first-class arm `eanom_control_differ` (5/6 traced
> positives [2 STRONG/2 WEAK/1 NOISE] + createaccount MISS + bookinfo FP = 1/1 evaluable
> negatives) — never claim a trace differ "cannot see" masked loss. **(3)** MIST provenance split
> = **7 live-run + 1 manual-record + 1 capture-concordant** (9 flags; was 7/1/2=10 before the
> 2026-07-21 depdown refutation removed it from the flagged set; the 8/1/1 split below is 33-era).
> **(4) HEADLINE DISCIPLINE (binds every drafted sentence): the claim is the OPERATING POINT**
> (black-box · no instrumentation · no paired control leg · single-execution · durable-state
> read-back, not a trace proxy) **+ the 0-FP profile** (the one cell no comparator matches:
> naive +2 FP, trace_shape +1, tracetest-surrogate +1, eanom 1/1) — **never "uniquely detects" /
> perfect-recall-led framing**; always state recall with the POSITIVES-SCOPE rail (**9 evaluable
> of 11**, MIST 9/9 + 0 FP [depdown retired 2026-07-21, so it is no longer among the positives at
> all — not an n_e]; the **2** principled n_e foregrounded: kafka barred-by-stop-rule + sockshop
> trace-only).
> Provenance: freeze 2026-07-21 row; `matched-recall-table.json` rails; commits 6242257/e010a05/
> bd362d0 + the eanom-fold commit.]**

| # | Paper claim (honest form) | Provenance |
|---|---|---|
| P1 | **[SUPERSEDED 2026-07-21 → see the §1 amendment block: benchmark-of-record = 27 (12 pos / 15 neg), F-corpus retired, depdown captured]** Corpus: **33 cases (18 pos / 15 neg) [post-A3; the 27/12-pos figure was the pre-A3 snapshot]**, schema-validated, **5 SUTs** (bookinfo/oteldemo/sockshop/teastore/trainticket; Boutique appears in the M-yield experiment set only), provenance classes + `readback_shape` census (9 present-landed / 3 eventual-present / 2 reject-no-delta / 1 degraded-present; positives omit the field) — the 27th = the additive kafka S1 stochastic case; 28-33 = the A3 F-corpus (F1 lost + 5 acknowledged_corrupted_write) | freeze §5 + R1d row + the Phase-C row (2026-07-16); `benchmark/cases/**` validator-green 27/27 |
| P2 | 10 distinct positive SITES post-A3 (7 pre-A3 site families + F8 ts-user + F14 ts-basic + the kafka-checkout site; per-family enumeration in REVIEW-verify-numbers item 5) — the disclosed positive-side widening CLOSE after 3 rejected attempts | freeze §5; R1b/R1c rows |
| P3 | STRUCTURAL SCARCITY datum 1: wild-hunt 0 CONFIRMED / N=1514 / K=5 (≤0.20% one-sided; pre-registered) — scarcity IS the finding; NO natural discriminator exists in this regime | S3 rows (2026-07-12/13); `RESULT-s3.md`; per-window commits 10eb19e/5802fa8/0fbe00f, classifier byte-identical |
| P4 | STRUCTURAL SCARCITY datum 2: write-acked-absent presence-defuser floor 0/≥8 — a clean-ack empty read-back IS a masked loss (structural, not effort) | R1d row; `RESULT-r1d.md` + `r1d-phase0-findings.md` |
| P5 | MIST read-back enablement: paired FIRE 5/5 ground-truth-verified on BOTH corpus SUT legs (2.75-A) | freeze 2.75-A row; commits + capture logs |
| P6 | **[SUPERSEDED 2026-07-21 → see the §1 amendment block: 11 arms (eanom_control_differ folded in), MIST 9/9 evaluable-of-12 + 0 FP, split 7 live + 1 manual + 1 concordant (depdown REFUTED off the flagged set 2026-07-21), OPERATING-POINT + 0-FP headline]** **The COMPLETED E2 matched-recall table through the single scoring harness — arm lineage: 6 canonical Gate-4 arms + 3 trivial baselines + the mist_trace_shape structural sub-invariant = 10 arms × 4 visibility classes** — MIST **read-back** 10/10 evaluable positives (**8 live-run + 1 manual-record (TT-adminbasic, disclosed) + 1 capture-concordant (TT-createaccount-agreement)** [review final3-2 correction] [`TT-createaccount-agreement-001`] — the self-concordance rail forbids pooling the concordant cell into any live headline); negatives 0 flags among all 15 (**13 evaluable**, 2 structurally n_e); naive 0 positives + 2 FP; Tracetest-presence SURROGATE (labeled; live tool never run) 4 positives + 1 FP + invisible-MISS; db-locality 1/1 invisible CATCH (specification-locality measured); contract-invariant live flagship cells (by-construction MISS measured ×5); TraceAnomaly construction-blindness (not-cleared by actual check); measured-vs-stamped 0 mismatches (read-back; the trace-shape arm adds the 1 disclosed bookinfo stamp-vs-measured divergence, see P20); matched-recall framing ONLY (never "discrimination"); no pooled recall exists in the artifact | `benchmark/scoring/matched-recall-table.json` + `RESULT-e2-frontier.md` (post-Phase-C refresh) + TT-omnibus row + E2 row (5942bab) |
| P7 | Trace-gated tier: the first CONTROLLED pre-registered either-way measurement in the c2c3 record — observe fault 5/5 OBSERVED_COMPLETE_ABSENT + control clean; NEVER "first in any run of record" (G1 2026-07-02 fired it 126×, disclosed) | TT-omnibus row; leg-1 report |
| P8 | E5 ablation: exact-4 OAT × 5 reps uniform; A2 (trace gate) = the ONLY verdict-tier-moving axis (C1 vs C2 contrast) | TT-omnibus row leg-3; `b4/ttomni/leg3/` |
| P9 | M-yield: 6-SUT set, 29+10 seeds, 5145+~2700 tests, 26 clusters + reps + cross-seed 10% audit; NO yield statistic (rater-gated); flags on TT complete seed = 0 (S3 prior held) | MYC row (2026-07-16) + `RESULT-myield-completion.md` |
| P10 | The observe-starvation finding (CORRECTED mechanism — the paper text uses ONLY this form): observe arms ONLY at the enhancer final round; budget-capped runs DO reach it (SS 10/10 armed) but the armed stretch is STARVED (SS all-3440-writes-500 at the type-naive tier + mid-round kill + jaeger-off tier cap; TT 4/10 armed w/ triple-coverage miss) | MYC row; RESULT finding section; MistRunner L1730/1757-58 |
| P11 | Oracle-FP measurement: sync FP 0/2127 acked (interval [0,0], gate histogram 100% observed) + FP-vs-timeout curve (500ms→12.98%, 1s→0.14%, ≥2s→0.0) + packaged SS FP corpus 0/1200 | G1 row (gate1-result.md, run-3 report JSON); SS FP corpus = `prep/g3-sut2-fp-probe-result.md` + `g3-result.md` P5 (packaged-FP exemption: freeze §6 R1 row) |
| P12 | Authoring-cost symmetry: `mist_authoring` table (TeaStore ~25 min / OTel ~15 / SS ~5 / BI+Boutique 0) vs comparator per-endpoint authoring cost (E2 arm-3 obligation) | MYC row enablement table; E2 prereg |
| P13 | Scope/Limitations of record: detects acknowledged-but-LOST (absence), NOT acknowledged-but-CORRUPTED; observe-mode CORRECTLY ABSTAINS on eventual-consistency (TIMEOUT_ABSENT = WARN-only; the naive at-cap comparator is what FPs); single-box; induced-vs-natural mix; TT-only exception text | R1c-A + R1d-B rows (source-verified, memory-pinned) |
| P14 | Rater-study DESIGN + its disclosed shortfalls (calibration ≪50, binding=benign; S2 floor ≥35 unmet w/ earned-exhaustion documentation; rateability census 14 ok / 9 truncation-gated / 1 trace-only / 1 async-ineligible) | R1d + E1+R2 rows; `MANIFEST-r2.json` |
| P15 | Benchmark release engineering: neutralized rater-sidecars rev 3 (0-leak, hardened BANNED_STRINGS + opaque-id guard), clean-room OpenAPI specs + coverage gate, license audit incl. OpenAPI-provenance heterogeneity | E1+R2 rows (7404873/d4a6c96 + corrections) |
| P16 | **The C1×C2 integration layer (the benchmark-consumes-the-tool machinery):** the 33-case MIST **read-back**-column census (flag 10 / no_flag 13 / principled-n_a 10, ZERO silent pending, per-cell provenance incl. live-run vs capture-concordant [self-concordance rule]); the scoring harness as THE single mechanical path; the visibility census (two uninstrumented senses); the existence-verified bundle map; release staging w/ the reproduction census (**32/33 executable-reproducible**) | completion-set Phase A+C rows (96cbbaa→086cf68); `benchmark/{mist-column-census,e2-visibility-census,case-trace-arm-map}.json` + `scoring/` + `release-staging/` |
| P17 | **The kafka stochastic S1 measurement:** vendor-flag permanent production loss — control 10/10 landed vs fault 19/20 LOST at the T+5min binding re-probe (rate 0.95, Wilson95 [0.764, 0.991]); wedge-past-flag-off reproduced + healed by the pinned recovery-restart; post-recovery permanence re-probe; SECOND attempt of record (the first STOPPED at probe). Frame as a measured vendor-fault characterization + corpus case, never a SUT-defect-rate claim | Phase-C row (34b2f8d); `b4/cset/kafka-s1/` + the case file |
| P20 | **The `mist_trace_shape` structural sub-invariant arm (INTERNAL CONTROL, 2026-07-18):** MIST's HIDDEN_DOWNSTREAM_FAILURE sub-invariant executed offline (real oracle code) over the 13 traced captures — 0/6 evaluable masked-loss positives flagged (trace-invisibility confirmed via MIST's own second oracle; the SAME no-error-span fact naive_span_error shows, framed as confirmation never independent evidence); WARN-tier abstains on sockshop-control where naive-span FPs; the sole flag = the bookinfo structural-only FP (the pre-registered semantic no_flag case; the full oracle's semantic invariants were not run — disclosed w/ the 12/13-consistent + 1/13-divergent measured-vs-stamped reconciliation); + the flag-gated reporting-only DI-runtime wiring (default-off; verdict flow byte-identical; 8 unit tests). NEVER pooled with the read-back column; always "structural sub-invariant" | `b4/RESULT-trace-shape-arm.md` + `scoring/verdicts/mist_trace_shape.json` + commit 77376ad + the tsarm fold |
| P19 | G3 banked supporting evidence — the SS-shipping head-to-head (4972d3b) + the Rider-2 survey (86.25%): SUPPORTING-ONLY disposition (own records, cited where sections 6/8 need them; NEVER headline claims; the survey's executable-breadth variant stays rejected-LOW-ROI) | [[g3-headtohead-result]]; `g3-result.md` |
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
4. **The benchmark (C2)** — P1/P2/P4/P14/P15/P16 (the integration layer = the benchmark's tool-consumption machinery)/P17 (the stochastic S1 case as corpus material): schema, provenance classes, shape census,
   neutralization pipeline, license audit, packaged-FP corpora, the 8-site disclosure,
   counting conventions (distinct-site definition, two-denominator S1).
5. **Study design (C3)** — rater protocol + calibration/floor shortfalls AS DISCLOSED
   findings about the fault class's supply (P4/P14) + the seal-decision surface P18
   (reported as design/disclosure, never as executed rating); IRB = pending user-side (stated).
6. **Experiments (executed record)** — E2 matched-recall (P6, fed by P16's harness);
   read-back enablement (P5); trace-tier controlled measurement (P7); E5 (P8); M-yield
   (P9/P10/P12); FP measurement (P11); S3 (P3); the kafka stochastic S1 measurement (P17);
   G3 supporting evidence where needed (P19).
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

0. **USER CONSENT (HARD GATE, operative here not just in the header): NO drafting wave
   starts without the user's explicit go — an ALL-CONFIRM on this plan parks it at
   START-READY and nothing more. This gate binds every future session including /goal
   auto-execution.**
1. THIS PLAN → 3-cold review → reconcile → confirmation pass (heavy revision, R1d
   precedent) → ALL-CONFIRM = START-READY (see step 0).
2. Drafting in 3 waves, each ending in a wave review: W1 = skeleton + Intro + Background +
   Mechanism (sections 1-3); W2 = Benchmark + Study design + Experiments (4-6); W3 = Results
   + Threats + Related + Abstract polish (7-10 + abstract).
3. Full-draft 3-cold review at the end (the DoD gate) → fold → the draft-of-record.
4. Every wave commits on `main_track`; reviews local-only; FILE_INDEX/memory sync per wave.
5. **SUBMISSION GATE (distinct from start-ready): a draft carrying `[RATER-GATED]`
   placeholders is submittable-SHAPED, NOT submittable — SUBMISSION additionally requires
   the Step-5 rater cells filled, a pre-submission 3-cold re-review of the FILLED draft,
   and the user's venue election.**

## §5 Hard rails (memory-pinned; a reviewer finding ANY of these violated = BLOCKING)
0. **Two MIST oracles exist — always disambiguate:** "MIST read-back column" (the detection
   contribution, 9/9 + 0/13; was 10/10 pre-2026-07-21-depdown-retirement) vs "the mist_trace_shape structural sub-invariant arm" (an
   internal control, 0/6 + 1 disclosed structural-only FP). Unqualified "MIST column"/"MIST
   oracle" is FORBIDDEN in the draft; the two are never pooled.

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
- **The checklist-footer paper honesty riders, VERBATIM (C-B3): lead with the study;
  two-denominator S1 (distinct-site AND case-run — the Step-3a S1 pair; the
  per-request/per-endpoint pair belongs to the Step-5 M-prevalence obligation, a separate
  rider); tell-free floor; MIST vs arm-3
  authoring-cost symmetry; Gate-4 wording "3 frontier trace comparators +
  contract-invariant arm".**
- NO scoped-"first" phrasing in the DRAFT VOICE — internal-provenance firsts (P7) live in
  provenance notes only, never in claims the paper makes for itself.

## §6 NOT in scope

New experiments or captures (kafka S1 = CLOSED-CAPTURED at completion-set Phase C — no
new kafka work; the contract-invariant arm = MEASURED at Phase B); any MIST tool/oracle
code; rater contact; IRB; **the seal decisions themselves (USER-side: the S3-BENIGN-01
re-cut swap [CASE-Q47 staged], the TT re-capture-vs-keep-excluded branch, the SS
keep-vs-exclude, the ack-text check on any re-captured renders)**; E6 packaging
execution / fork-publication (USER); LaTeX/venue formatting (venue = USER); SmartFetch
(parallel track; no-double-claim).
