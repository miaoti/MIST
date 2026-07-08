# C2+C3 execution plan — the empirical-track floor (benchmark + prevalence study)

**Status:** PLAN (pre-execution). Workflow per the standing rule: plan → ≥3-cold-review → reconcile →
manifest → execute. **Provenance:** user direction 2026-07-08 (option (b) of the post-G3 fork): build
C2 (open labeled benchmark at citable scale) + C3 (adjudicated defect-yield/prevalence study) — the
two deliverables the Gate-3 verdict (`g3-result.md`) names as the unbuilt condition of the
empirical-track "clear Accept". Companion inputs: `README.md` (plan v4+, §3/§6/§8/§8.5/§9),
`research/05-evaluation-and-benchmarks.md` (the E-series + corpus strata + anti-tautology design),
`g3-result.md` + `g3-evidence-pack.md` (what already exists).

---

## §1 The contribution question, adjudicated (user question 2026-07-08)

**Question:** does failing to discharge Plan A's "Gate 3 yields wild bugs" trigger (and the other
undischarged items) mean the contribution is INSUFFICIENT for an A-venue?

**Answer (grounded in the 6-round-reviewed plan, not my opinion): NO for the committed path — with one
hard condition, which is exactly this plan.** The reviewed plan's §9 verdict is TRACK-SPLIT:

- **Mechanism-novelty research track:** floor without Gate-3 wild bugs = **Weak Reject** (Cast caps the
  mechanism). The wild-bug trigger was ALWAYS this track's entry fee, and the plan explicitly did NOT
  rest A-worthiness on it ("§3 commits the primary A-path to the empirical/benchmark leg, with Gate 3
  as upside — not the reverse"). Not discharging it forecloses the UPSIDE, not the goal.
- **Empirical/benchmark main track (ISSTA/ASE/FSE):** guaranteed floor = **clear Accept** — but
  **conditional** (cold-review H, quoted in §9): *"'clear' conditional on C2 released at the benchmark
  scale + C3 executed"*; until then *credible, not yet clear*. C2+C3 are Cast-independent (Cast's
  benchmark is closed; Cast ships no prevalence study).

**Per-item adjudication of everything the G3 verdict left undischarged:**

| undischarged item (g3-result.md §8) | does it gate the A-claim? |
|---|---|
| Gate-3 "wild bugs" trigger + the decisive-result pin (UNMET) | Gates ONLY the mechanism-track upside. The §6 sentence "show the specific defects MIST catches that the assertion-based oracle misses because no human authored that assertion — the ONLY evidence that moves a reviewer off 'you automated an assertion'; if it cannot be produced, the novelty claim fails" — that evidence EXISTS in constructed/disclosed form (the two clean-win cells + the 11/80 structural residue). What is missing is its WILD form. The empirical track does not require it; the paper's novelty section must stay class-scoped exactly as the G3 framing rules already mandate |
| **C2 at citable scale** | **YES — the floor condition.** Built by §2 below |
| **C3 executed** | **YES — the floor condition.** Built by §3 below |
| Gate-4 / E1–E2 breadth (≥4 baselines × ≥6 SUTs; precision frontier) | Not named in §9's "clear" conditional, BUT load-bearing for the paper's evaluation chapter via the anti-tautology design (research/05 §3b pillars A+C): without E1 baseline calibration and a NON-ZERO trace-aware comparator at matched recall, the headline invites the "0-vs-N tautology" reject regardless of C2/C3. **In scope here (§4)** — E1+E2 jointly discharge Gate-4 |
| §8.5 binding commitments (underspecified-case protocol; per-SUT FP + async coverage; depth-not-count; replay-coverage argued-not-measured) | Execution/writing obligations, not new research — folded into §2/§3 design below as pre-registered rules |
| Trace-style comparator (never executed at G3) | RETURNS at eval scale inside E2 (Tracetest + generic span-error assertion; naive span-error oracle; TraceAnomaly/TraceRCA) — a stronger, pre-planned answer than the G3-time gap |
| Executable breadth run (REJECTED as low-ROI) | Stays rejected; the analytical survey stands. Not resurrected |
| SUT-2 β extras | Secondary; not on the A-path |

**Honest bottom line for the user:** the current gap is a DELIVERABLE gap, not a novelty-ceiling gap.
The novelty ceiling (Cast) was priced in six review rounds ago and routed around via the empirical
track. But two honesty notes bind: (1) "clear Accept" is the plan's own reviewed self-assessment of
that track — a strong, thrice-cold-reviewed prior, not a guarantee; the arbiter is execution quality
(scale, rubric blindness, statistics) — which is why this plan's reviewers are explicitly charged with
attacking sufficiency (§6). (2) The Morest bar ("any incidental developer-confirmed bugs, target ≥2")
remains a cheap, real upside worth harvesting during C3's wild sampling — it is NOT required for the
floor, and no wild-count claim is ever made (README §6 bug-story rule).

---

## §2 C2 — the first open-source labeled benchmark of masked-downstream / data-integrity faults

**Claim shape (fixed by §3/C2 + claim-hygiene §8.5-6):** "the FIRST OPEN-SOURCE labeled benchmark" of
masked-downstream/data-integrity faults across N OSS microservice systems, with an adjudication rubric
— scoped open + OSS (Cast's 48-bug benchmark is closed); single comparative-first, no stacked
adjectives.

### 2.1 Composition — three strata (research/05 §4), each with label PROVENANCE
- **S1 positives-by-construction** (label true by injection, never by MIST):
  (a) the TrainTicket F1–F22 industry-replicated corpus [DOI 10.1109/TSE.2018.2887384] — the
  swallowed-downstream subset (prior probe: F6 retry-timeout, F8 dropped-token, F10 mis-call, F20
  enum-mismatch) replicated on our pinned deploy; (b) OUR reviewed injected sites promoted into cases:
  the G1 adminroute lost-write, the TT cancel→refund cells (natural + constructed + agreement), the SS
  shipping cells (natural sever + reject-publish) — each already has manifests, triggers, raw logs, and
  review records; (c) OTel-Demo vendor-authored fault flags (productCatalogFailure,
  recommendationServiceCacheFailure, …); (d) controlled injections mirroring RCAEval's 11 fault types /
  Nezha's injection points (code-level exception, dependency 500s, scale-to-zero, mesh sever, broker
  policy — the last two proven at G3).
- **S2 benign traps** (the FP stratum; label benign-by-design from docs/source): Bookinfo
  reviews→ratings (the canonical handled degradation), Online Boutique adservice/recommendation
  optional deps, OTel-Demo recommendation-cache degradation, retry-then-succeed, circuit-breaker
  defaults, eventually-consistent writes; PLUS our two labeled benign corpora as packaged runs (TT
  2,127-record + SS 1,200-record, with their gate histograms and the FP-vs-timeout curve).
- **S3 adjudicated wild sample** (shared with C3 §3): naturally-occurring masked-2xx under a realistic
  UN-FAULTED workload on the OSS SUTs; every case carries its blind adjudication record (rubric verdict,
  rater pair, κ round). Wild ≠ production traffic (these demos have none) — stated per cold-review H.

### 2.2 Per-case content (the unit a downstream user consumes)
SUT + version pins + deploy manifests (kind-reproducible, as our G1/G3 cases already are); the fault
trigger as config/manifest/flag with its provenance class (`injected-flag` / `operational-policy` /
`vendor-flag` / `industry-replicated` / `wild`); the workload (request script or generator seed); the
expected observable (read-back endpoint + expected delta/membership); the LABEL + provenance
(by-injection / by-docs / by-adjudication) + the §8.5-1 underspecified marker where applicable; raw
artifacts (traces where instrumented, HTTP transcripts, oracle records); MIST's verdict AND the
comparator verdicts where run (the G3 cells ship theirs). Machine-readable index (one YAML/JSON row per
case) + a README rubric document.

### 2.3 Scale — pre-registered target (the reviewers' first attack surface)
**Target: ≥100 labeled cases total across ≥6 SUTs** (core 6: TrainTicket, TeaStore, Sock Shop,
OTel-Demo, Online Boutique, Bookinfo; oracle write-path subset on TT/TeaStore/SS(+petclinic stretch)),
with pre-registered strata floors: **S1 ≥ 45** (≥6 per SUT where the write path exists; incl. ≥10
TT-F-corpus replications), **S2 ≥ 35** (every catalogued designed-degradation path on the 6 SUTs +
both benign corpora), **S3 ≥ 20 adjudicated wild cases** (sampling rule in §3.3; if the un-faulted
wild yield is lower, the SHORTFALL IS ITSELF A FINDING — reported, never padded). Citability defense:
RCAEval's 735 counts fault-injection RUNS at RCA granularity; our unit is a LABELED, provenance-tagged,
rubric-adjudicated CASE with per-case artifacts — composition + openness + rubric is the citable asset
(R1: "a citable benchmark regardless of mechanism simplicity"). Honest disclosure: we do NOT match 735
and never claim to.

### 2.4 Steps + acceptance
1. Case schema + rubric doc + machine index format → **frozen before population** (prereg discipline).
2. Promote the existing reviewed assets into cases (G1, TT×3, SS×2 + benign corpora) — ~10 cases, the
   seed, already reviewer-accepted.
3. Deploy wave: TeaStore, OTel-Demo, Online Boutique, Bookinfo on the kind cluster (TT/SS proven);
   per-SUT write-path + designed-degradation survey (the §8.5-3 depth-not-count pre-specification:
   name each saga/dual-write/compensation site and COUNT its genuine acked-but-lost opportunities).
4. S1 population (F-corpus replication + per-SUT injections) → S2 population (catalogue + label from
   docs/source with citations) → S3 from C3's sampling (§3).
5. E6 release packaging (repo layout, license, hashes, README).
**Acceptance:** strata floors met (or shortfall-reported for S3); every case reproduces from its
manifest on a clean cluster; label provenance complete; zero labels derived from MIST's own predicate
(the §6 circularity rule); ≥3-cold-review of the benchmark artifact itself before release.

---

## §3 C3 — defect-yield + bounded prevalence (the adjudicated study)

**Claim shape (§3/C3):** (a) **defect-yield / oracle precision**: of the masked-2xx events MIST
surfaces on generated workloads, how many hide a GENUINE defect vs a lived-with non-fatal error — NOT
ecosystem prevalence (most masked-2xx on generated workloads are MIST-induced; cold-review E); (b)
**bounded population-prevalence** claimed ONLY from the stratum-3 wild sample (un-faulted realistic
workload), with CIs (E4). Uber's 29.35% cited only as phenomenon-pervasiveness, never as a defect rate.

### 3.1 Machinery (all pre-registered before any labeling)
- **B4 independent-label harness** (deferred at Gate-1 to the eval stage — now due): the
  genuine-vs-benign standard derived BLIND from API contract / SUT docs / source (required-vs-optional
  dependency + designed-degradation), by raters who never see MIST's predicate, output, or traces'
  MIST-annotations. The rubric must not reuse MIST signals (§6 central fix).
- **≥2 independent blind raters + Cohen's κ + third-rater adjudication** on every S3 case and on a
  stratified sample of S1/S2 as rater-calibration (κ reported for both).
- **§8.5-1 underspecified-case protocol:** pre-registered resolution rule for "intended behavior
  unknown"; report the underspecified fraction; report precision BOTH including and excluding
  underspecified cases; raters may not resolve silence from runtime/trace behavior.
- **§8.5-2:** per-SUT (never pooled) oracle FP/FN + per-SUT async-write trace-coverage disclosure.
- **Rater sourcing (disclosed constraint):** raters must be MIST-blind humans with microservice
  literacy; recruit ≥2 CS-graduate-level raters outside the project + the third adjudicator; their
  training set = S1/S2 calibration cases; compensation/logistics budgeted. If recruitment fails, the
  fallback is a disclosed two-author-blind protocol with its validity threat stated — a known
  weakening, pre-registered as the fallback, never silently substituted.

### 3.2 The two measurements
- **M-yield:** run MIST's full generation-driven pipeline (budgeted, seeded ×10) on the ≥6 SUTs; every
  MIST-flagged masked-2xx event → blind adjudication → defect-yield = genuine/(genuine+benign) with
  per-SUT E4 CIs (Wilson intervals; ≥10 seeds; MWU/Â₁₂ where tools are compared).
- **M-prevalence:** the un-faulted realistic workload (recorded demo scenarios / seeded generators
  WITHOUT fault injection) × N hours × 6 SUTs; stratified random sample of naturally-flagged events →
  the S3 adjudicated set → bounded prevalence of genuine masked defects under that workload class,
  with the workload's representativeness threat stated (demo SUTs, no production traffic).
### 3.3 S3 sampling rule (pre-registered): sample size = min(all flagged, 40); stratified by SUT and
by flag type; if total wild flags < 20 across 6 SUTs, report the scarcity as a finding (consistent
with "wild trace-only swallowed-bug corpora are structurally unobtainable") and let prevalence carry
wide CIs honestly.
**Acceptance:** κ ≥ 0.6 (else rubric iteration round, disclosed); underspecified fraction reported;
every number carries its CI; per-SUT tables; developer-confirmation attempts filed upstream for every
genuine wild defect found (the ≥2 Morest-bar upside — pursued, not promised).

---

## §4 The evaluation chapter around C2/C3 (what else the paper needs — scope decisions)

- **E1 (IN — anti-tautology pillar A):** EvoMaster, RESTler, Schemathesis, Morest, AutoRestTest under
  a fixed budget (1 h × 10 seeds, identical OpenAPI) on the 6 SUTs; report coverage + all faults by
  their OWN oracles. Calibrates baselines as strong (non-strawman).
- **E2 (IN — pillar C, the crux):** on the C2 labeled corpus, precision/recall/FP of MIST vs NON-ZERO
  trace-aware comparators — naive span-error oracle, Tracetest + generic span-error assertion,
  TraceAnomaly/TraceRCA — **headline = precision/FP at matched recall on the hard async-benign cases**,
  never N-vs-0. This also discharges the G3-deferred trace-style-comparator item at eval scale, and
  E1+E2 jointly discharge Gate-4 (≥4 baselines × ≥6 SUTs).
- **E3 trigger rate (IN, cheap):** MIST's trace-guided construction vs response-only generators' lucky
  hits, per endpoint.
- **E5 ablations (IN, bounded):** A1 remove benign-filter; A2 remove generation (replay-only); A3
  trace-input vs spec-only. One SUT-pair scope if budget forces (disclosed).
- **E6 release (IN):** the C2 artifact.
- **Replay-coverage delta (§8.5-4):** framed argued-not-measured unless E3 yields a cheap head-to-head.
- **OUT (unchanged):** executable breadth (rejected); Cast-pattern comparator (reviewed out at G2);
  invariant mining (blocked-by-data); DeathStarBench beyond stretch.

## §5 Sequencing, budget, risks
Order: (0) this plan's review + reconciliation → (1) §2.4-1 schema/rubric freeze + §8.5-3 per-SUT
depth survey (doc work, no cluster) → (2) deploy wave (4 new SUTs; TT/SS runbooks exist; expect
per-SUT deploy debugging — the known cost) → (3) S1/S2 population + E1 runs (parallelizable per SUT)
→ (4) MIST pipeline + M-yield runs → (5) wild-workload M-prevalence + S3 adjudication (rater
recruitment starts at step 1, longest lead) → (6) E2 comparator runs on the frozen corpus → (7) E5 →
(8) E6 packaging + benchmark review → (9) paper integration with `g3-evidence-pack.md`.
Rough budget: steps 1–2 ≈ days; 3–4 ≈ 1–2 weeks compute-dominated; 5 gated on raters (weeks,
parallel); 6–8 ≈ 1 week. Cluster: one kind node is proven for 2 SUTs at a time; schedule SUTs in
waves, pin images, reuse the G3 hygiene runbooks.
**Risks:** (R1) wild-flag scarcity on demo SUTs → pre-registered shortfall-is-a-finding rule (§3.3);
(R2) rater recruitment → disclosed fallback (§3.1); (R3) async trace coverage per SUT varies →
§8.5-2 per-SUT disclosure is the containment, plus the oracle's non-trace read-back mode (proven at
G3) keeps write-path cases evaluable; (R4) Tracetest/TraceAnomaly operability on our stacks → spike
early in step 3, fall back to the naive span-error + Tracetest-only pair (≥2 non-zero comparators
minimum, disclosed); (R5) scale slip → strata floors are the pre-registered minimum; slipping below
them is a stop-and-replan, not a silent shrink.

## §6 Review protocol (this plan) — the reviewers' charges
≥3 independent cold reviewers, no shared context, each explicitly answering the user's questions:
1. **Sufficiency/strength:** is §1's adjudication sound — does C2+C3(+E1/E2) genuinely reach the
   "clear Accept" empirical-track bar, or is something still missing for an A? Attack the §9
   "conditional clear" reading itself.
2. **Completeness:** what has this plan NOT considered (design, statistics, threats, logistics,
   related-work exposure — e.g., is the benchmark's "first open-source" claim actually safe)?
3. **Composition:** do G3's pack (capability + comparator boundary + FP) + C2 + C3 + E1/E2 compose
   into one coherent A-paper, or do seams show (two-track confusion, claim inflation risks)?
4. **Feasibility:** scale/budget/sequencing realism; the riskiest step; what to de-scope FIRST if
   forced, without dropping below the floor.
Reconcile → fold → manifest → execute per the standing workflow.
