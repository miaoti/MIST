# Cold review A — C2+C3 execution plan @ 980164c, lens: A-bar sufficiency (hostile empirical-track PC)

**Verdict: ACCEPT-WITH-CHANGES.** The §1 adjudication is a FAITHFUL reading of §9 (all three quotes
verified verbatim; nothing quietly upgraded; the hedge preserved). The plan is unusually honest and
well-pre-registered, but carries one self-inflicted comparator hole (BLOCKING) and under-prices the
empty-wild-stratum branch (MAJOR).

## Direct answer to the user's question
**NO — failing the "Gate 3 yields wild bugs" trigger does not mean the contribution is insufficient
for an A-venue**, because the committed, six-round-reviewed A-path never rested on it: the wild
trigger was the entry fee of the mechanism-novelty track only; its failure forecloses the upside, not
the floor. The contribution IS sufficient for a **credible-to-clear empirical-track A if and only if
this plan executes at its pre-registered quality** (C2 ≥100 cases/6 SUTs clean provenance; C3 with
genuinely blind non-author raters + κ≥0.6; E2 with a non-crippled comparator set). **The single
biggest remaining threat to acceptance is C3's wild stratum coming back ~empty** — that degrades C3
to a scarcity finding and lands the paper in Weak-Accept/Borderline territory unless the
benign-dominance reframe is pre-registered now (M1).

Calibration data offered: Defects4J (ISSTA'14 main track, zero new bugs) and "No Time to Rest Yet"
(ISSTA'22, pure comparison) support the track's viability; **RCAEval landed at WWW'25 COMPANION, not
a main track** — so "clear" is overconfident as a prior; "credible-to-clear, decided by execution
quality" is the defensible calibration (the plan's §1 wording stays just inside it). What makes this
MORE than a dataset paper — must LEAD the framing — is **C3-as-first-measurement**: the literature
contains no genuine-vs-benign split of masked-2xx events (Uber gives prevalence, no benign split).

## §1 per-item scrutiny
- (a) Gate-4 "jointly discharged by E1+E2" = **partly accounting**: E1's generators never appear ON
  the E2 precision frontier; under the R4 fallback the frontier drops to 2 trace-aware comparators —
  below research/05 §1's "3+" deliverable shape and arguably below Gate-4 as written. Pre-register
  the accounting (M2).
- (b) "the §6 'only evidence' sentence EXISTS in constructed form" = **one notch generous**: what
  exists demonstrates the ORACLE-CLASS BOUNDARY (in fact a stronger form — the blind author's
  strongest contract structurally could not express the observable); what it does NOT demonstrate is
  PRACTICAL INCIDENCE. The §6 demand (tied to "§9 Gate 3" real defects) is **waived-for-the-track**,
  not discharged — say "waived"; C3's wild stratum is precisely the instrument that could still
  discharge it.
- (c) Morest bar optional = correct for a PURE empirical paper — but this paper ships C1 capability
  claims + a head-to-head, so reviewers partially apply the tool-paper lens (≥1 confirmed bug de-facto
  norm). Survives only with STUDY-FIRST framing (M3).
- Everything else checks out: all 7 deferred-ledger items addressed; §8.5 genuinely folded;
  executable-breadth rejection respected.

## Findings
### B1 (BLOCKING) — E2 omits the downstream-span-PRESENCE assertion arm
`g3-result.md` §7 names it verbatim as "the trace-class analogue of MIST's read-back [that] could
catch both constructed cells in an instrumented deployment." E2 lists only span-ERROR-class
comparators → re-opens the crippled-comparator/tautology charge WITH the project's own artifact
chain as ammunition. **Fix: pre-register a 4th E2 arm — "Tracetest + downstream-span-presence
assertions, hand-authored per endpoint" — reporting per-endpoint authoring cost + benign-trap FP
(the authoring cost IS the automation-gap datum; this arm strengthens the story).** If excluded, the
exclusion rationale must be pre-registered before the corpus freezes.

### MAJORs
- **M1 price the 0-wild branch:** pre-register the interpretation (benign-dominance = the
  load-bearing finding: "most masked-2xx under realistic demo workloads are lived-with ⇒ precision,
  not recall, is the binding constraint") which promotes the E2 precision frontier to headline in
  that branch; state in §1 that "clear" is two-sided (execution quality AND the composed story
  surviving the S3 outcome). If S3≈0: the paper = the README's own Plan-B description
  (Borderline-to-Weak-Accept / dataset-track redirect risk) unless reframed as above.
- **M2 Gate-4 accounting pre-registered:** which tools count toward "≥4 baselines"; the honestly
  narrowed claim if the frontier ends at 2 comparators; disclosed-amendment style.
- **M3 study-first framing + early harvest:** binding writing rule — title/abstract/contribution
  order lead with C2+C3 (the study); C1 = the instrument; head-to-head cells = boundary
  demonstrations. Move upstream bug-filing EARLIER (during steps 4–5); one developer confirmation
  before submission flips reject rationale #3.
- **M4 "first open-source" sweep:** add a pre-registered related-work sweep (2024–26: Cast-group
  artifacts, microservice fault/anomaly datasets, LLM-era corpora) to §2.4 step 1 BEFORE the claim
  freezes; wire into §8.5-6 claim hygiene. (Nezha/RCAEval ship open injected corpora — the claim
  survives only under its precise scoping.)

### MINORs
m1 OpenAPI fallback for thin-spec SUTs (Sock Shop/Boutique) — author specs or restrict E1 grid with
disclosure. m2 pre-register the headline formula "≥80 constructed/benign + wild as-found" (else ~85
under S3 shortfall silently breaks "≥100"). m3 carry research/05 §5's CI-half-width ≤5% sample-size
rule into §3.1 (κ alone doesn't size). m4 if the two-author-blind fallback triggers, the C3 claim is
downgraded in the ABSTRACT (not a footnote) — circularity lineage. m5 restate §5 budget as a wave
schedule with compute-weeks (E1 ≈300 SUT-hours on a 2-SUTs-at-a-time node).

## Composition + headline
ONE coherent paper achievable: **C2 = spine, C3 = headline study, E2 = central experiment, G3 cells =
~10 seed cases + boundary demonstration.** Seam to police = constructed cells reading as discovery
(already fenced by R-TT-1/R-SS-2/R-SS-6). Suggested one-sentence headline recorded in the review
transcript. Two-half-papers risk exists WITHOUT M3.

## Reject rationales + status
1. "Dataset-track material" — rebuttal EXISTS (C3 answers an unmeasured research question) but the
   framing commitment is missing (M3). 2. "Injected-dominated corpus on demo SUTs; prevalence
   generalizes to nothing" — mostly pre-rebutted (unit-of-measure defense, accepted practice, wild≠
   production disclosure); the 0-yield branch unpriced (M1). 3. "You automated an assertion;
   comparators crippled" — pre-rebutted ONLY IF B1 is fixed.
