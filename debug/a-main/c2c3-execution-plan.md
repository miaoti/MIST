# C2+C3 execution plan v2 — the empirical-track floor (benchmark + prevalence study)

**Status:** v2, REVIEWED — 3 cold reviews (A sufficiency / B soundness / C feasibility), all
ACCEPT-WITH-CHANGES, every disposition folded per `REVIEW-C2C3-PLAN-RECONCILIATION.md` (v1 = git
980164c). This document is the executable pre-registration; deviations from it are disclosed
amendments. **Provenance:** user direction 2026-07-08 (option (b) post-G3): build C2 + C3 — the
deliverables `g3-result.md` names as the unbuilt condition of the empirical-track "clear Accept".

---

## §1 The contribution question, adjudicated (user question 2026-07-08; reviewer-verified)

**Question:** does failing Plan A's "Gate 3 yields wild bugs" trigger (and the other undischarged
items) mean the contribution is INSUFFICIENT for an A-venue?

**Answer (review A, charged to answer head-on, verdict verbatim): "NO — failing the 'Gate 3 yields
wild bugs' trigger does not mean the contribution is insufficient for an A-venue"** — the committed,
six-round-reviewed A-path never rested on it: the wild trigger was the mechanism-novelty track's
entry fee only; its failure forecloses the upside, not the floor. The contribution IS sufficient for
a **credible-to-clear empirical-track A if and only if this plan executes at its pre-registered
quality**. Honest calibration (review A): "clear" as a prior is overconfident — RCAEval landed at a
WWW'25 COMPANION track, while Defects4J (ISSTA'14, zero new bugs) and "No Time to Rest Yet"
(ISSTA'22, pure comparison) prove the track's main-track viability — so the defensible label is
**credible-to-clear, decided by execution quality**. What makes this MORE than a dataset paper, and
therefore MUST lead the framing: **C3 is the literature's first genuine-vs-benign measurement of
masked-2xx events** (Uber gives prevalence, no benign split).

**"Clear" is two-sided (review A M1):** conditional on execution quality AND on the composed story
surviving the S3 (wild-stratum) outcome. If S3 ≈ 0 genuine defects, the PRE-REGISTERED
interpretation (not a retrofit) is: **benign-dominance is itself the load-bearing finding** — "most
masked-2xx events under realistic demo workloads are lived-with ⇒ precision, not recall, is the
binding constraint on any oracle for this class" — which promotes the E2 precision frontier to the
headline experiment; venue fit in that branch is honestly Borderline-to-Weak-Accept at a main track
unless the reframe carries, and that is stated here, in advance.

**Binding writing rule (review A M3):** title, abstract, and contribution ordering lead with C2+C3
(the study); C1 is the measurement instrument; the G3 head-to-head cells are ~10 seed cases of the
benchmark plus a class-boundary demonstration — never discovery. Genuine wild finds from C3 are
filed upstream DURING execution (steps 4–5), not at the end: one developer confirmation before
submission flips the tool-lens reject rationale.

**Per-item adjudication of the undischarged ledger (g3-result.md §8):**

| item | does it gate the A-claim? |
|---|---|
| Gate-3 wild trigger + decisive-result pin (UNMET) | Mechanism-track upside only. The §6 "only evidence" demand is **waived-for-the-track**: constructed-form BOUNDARY evidence exists (two clean-win cells + the 11/80 structural residue — in fact the stronger expressibility form); PRACTICAL-INCIDENCE evidence would discharge it fully, and C3's wild stratum is exactly the instrument that could still do so (review A (b)) |
| **C2 at citable scale** | **YES — floor condition.** §2 |
| **C3 executed** | **YES — floor condition.** §3 |
| Gate-4 / E1–E2 breadth | Load-bearing for the evaluation chapter (anti-tautology pillars A+C). **Gate-4 accounting, pre-registered (review A M2):** "≥4 baselines" counts the E2 frontier comparators (naive span-error, Tracetest span-error, Tracetest span-PRESENCE, TraceAnomaly/TraceRCA) — E1's five generators calibrate strength but never sit ON the frontier. If the R4 spike leaves only the fallback pair + the presence arm (=3), the claim narrows to "3 trace-aware comparators" with the shortfall disclosed in the deviations ledger; below 3 = stop-and-replan |
| §8.5 commitments | Folded: -1 → §3.1 (rule text WRITTEN); -2 → step 2.5's measured coverage table; -3 → §2.4 step 3 (NORMATIVE for S1 quotas); -4 → argued-not-measured anchored in the paper-deliverables list (the "unless E3" escape clause STRUCK — no replay tool is runnable); -5 → the four soundness-threat disclosures are a §4 writing obligation; -6 → §2 claim hygiene |
| Trace-style comparator | Returns at eval scale inside E2 — now four arms incl. span-PRESENCE (reviews A-B1/B-M4) |
| Executable breadth (REJECTED) | Stays rejected |
| SUT-2 β extras | Secondary; off-path |

---

## §2 C2 — the open benchmark (claim string frozen below)

**Frozen claim string (reviews A-M4/B-B3):** *"the first open-source labeled benchmark built for
ORACLE EVALUATION on masked-downstream / acknowledged-but-lost data-integrity faults — pairing
positive strata with a benign-trap false-positive stratum and a blind-adjudicated wild stratum,
under a per-case provenance taxonomy."* Never "the first open fault corpus".

**Claim-defense table (pre-freeze; one row per known open competitor):**
| prior open artifact | which qualifier it fails |
|---|---|
| Filibuster application corpus (SoCC'21; open, Dockerized apps w/ fault-tolerance bugs) | developer-assertion/bug-report labels; no benign-trap FP stratum; no adjudication rubric; not masked-2xx-labeled |
| FudanSELab/train-ticket-fault-replicate (22 industry-replicated faults — OUR S1 input, credited) | single SUT; fault-replication corpus, not an oracle benchmark; no benign stratum; no wild stratum; no rubric |
| Nezha injected-fault dataset (FSE'23) | RCA root-cause labels at injection granularity; no benign-trap stratum; no masked-2xx orientation |
| RCAEval (WWW'25 companion; failure CASES w/ telemetry + root-cause labels — described accurately, review B m2) | RCA granularity + purpose; no oracle-evaluation labels; no benign/adjudication strata |
A pre-freeze related-work SWEEP (2024–26: Cast-group artifacts, microservice fault/anomaly datasets,
LLM-era corpora) is §2.4 step 1's first action; any new competitor adds a row or narrows the string
BEFORE the freeze.

### 2.1 Composition — three strata with label provenance (unchanged from v1 in substance)
S1 positives-by-construction (TT F-corpus swallowed-subset; our reviewed G1/G3 cases; OTel-Demo
vendor flags; controlled injections incl. the proven mesh-sever + broker-policy mechanisms) · S2
benign traps (catalogued designed degradation on all 6 SUTs + the two packaged FP corpora) · S3
adjudicated wild (shared with C3).

### 2.2 Per-case schema (freezable form — review B M5's seven fields added)
Original fields (SUT+version pins, deploy manifests, trigger + provenance class, workload, expected
observable, label + provenance + underspecified marker, raw artifacts, MIST/comparator verdicts,
machine-readable index row) PLUS: **negative control** (the no-fault twin run; G3 cells' control
legs; F-corpus cases get one) · **SUT health preconditions + data seeding** (pre-flight checklist,
Gate-1 style) · **oracle-config provenance** (properties, triples, timeout caps) + **one frozen MIST
commit for the whole study** (oracle drift invalidates comparability) · **label version-validity**
(label bound to image digest; by-docs labels cite the doc version) · **per-case
license/redistribution field** · **trace-visibility class** {error-span-visible /
span-presence-visible / trace-invisible} (feeds E2 per-class reporting).

### 2.3 Scale + floors (review B M3 / A m2)
**Headline formula: ≥80 constructed/benign cases + wild as-found** (S1 ≥ 45, S2 ≥ 35, S3 target 20
with the shortfall-is-a-finding rule; "≥100" is reported only if S3 ≥ 20 materializes). **Case units
defined:** S2 case = one designed-degradation path on one SUT; a packaged FP corpus = ONE case with
a record-set attachment (no padding). **Diversity minima:** S1 ≥ 4 distinct fault MECHANISMS per
write-path SUT (flag / mesh-sever / broker-policy / code-level, as applicable); ≥ 6 data-integrity
(acked-but-lost) cases ACROSS the write-path SUTs so the differentiated family cannot hide inside a
formally-met floor; the §8.5-3 opportunity-count table (step 3) is NORMATIVE — S1 per-SUT quotas
derive from it. F-corpus: floor ≥ 6 replications (F6/F8/F10/F20 + 2), target ≥ 10 (review C M4).
Citability defense: composition + openness + rubric (units-of-measure honesty vs RCAEval as above).

### 2.4 Steps + acceptance
1. Freeze wave: related-work sweep → claim string + defense table final → case schema + rubric doc
   + machine index format frozen; **license audit** (per-source disposition table:
   train-ticket-fault-replicate license verified BEFORE any F-code ships; upstream images =
   reference-by-digest + build-from-source recipes by default; manifests audited) (review B M6).
2. Promote the ~10 reviewed existing assets into cases (incl. their negative controls + visibility
   tags).
3. Deploy + enablement waves (per §5) with the per-SUT §8.5-3 depth survey (NORMATIVE output).
4. S1 population → S2 population → S3 from C3.
5. E6 packaging: repo layout, license file, hash manifest, **data management** (large raw
   traces/transcripts → Zenodo/OSF archive with size budget + hashes; git holds the index + configs)
   (review B m4).
**Acceptance:** floors + diversity minima met (or S3 shortfall reported); every case reproduces via
an **automated per-case replay script** on a clean cluster; label provenance complete; zero labels
from MIST's own predicate; the benchmark's ≥3-cold-review takes the **sampled-reproduction form**:
each reviewer re-runs k=5 random cases end-to-end + schema/label-audits m=15 more (review B m3).

---

## §3 C3 — defect-yield + bounded wild prevalence

### 3.1 Machinery (all pre-registered here)
- **B4 blind-label harness** (unchanged): genuine-vs-benign standard from API contract/docs/source,
  raters never see MIST signals.
- **THE §8.5-1 RULE, verbatim (review B M9):** labels are three-way {genuine, benign,
  underspecified}. "Underspecified" = the intended behavior for the observed degradation is not
  derivable from docs/spec/source. Underspecified cases are EXCLUDED from the primary precision
  denominator and their fraction is reported; precision is reported both including and excluding
  them. A disagreement about WHETHER a case is underspecified goes to the third rater like any other
  disagreement. Admissible evidence: docs, OpenAPI/spec, source code. Inadmissible: runtime
  behavior, traces, MIST output. **κ-gate iteration rule:** if κ < 0.6 after the calibration round,
  at most TWO rubric-iteration rounds, each using CALIBRATION CASES ONLY (no S3 peeking); after any
  iteration, ALL previously-labeled cases are relabeled under the final rubric (fresh raters if
  available).
- **Raters, quantified (reviews B M7 / C m4):** ≥2 MIST-blind raters with microservice literacy +
  a third adjudicator. Workload ≈ S3 (≤40) + calibration (~20) + the M-yield cluster audit sample,
  at 15–45 min/case ≈ **15–45 hours per rater ≈ 2–3 paid working days** — compensation sized to
  that, consent + compensation sentence in the study materials, independence mechanics (no
  discussion channel before submission). **Fallback (two-author-blind), with scars pre-committed:**
  triggers only if recruitment fails by the step-5 gate; then (i) the C3 precision claim is demoted
  one register IN THE ABSTRACT, (ii) all label evidence is released for community re-adjudication,
  (iii) author-pair κ reported; acknowledged as partially undoing the §6 central fix.
- **Statistics (reviews B M2 / A m3):** κ computed over pooled calibration+S3 (n ≥ 50), reported
  with its CI, raw agreement, and a prevalence-adjusted coefficient (PABAK or Gwet's AC1). Per-SUT
  intervals: n ≥ 10 → Wilson; n < 10 → counts + exact Clopper–Pearson only, no per-SUT CI-based
  claims (pooled-with-stratification secondary). **CI units are distinct defect/fault-sites, not
  flagged events** (the correlated-denominator lesson carried from the G3 pack). The companion's
  ≤5% CI-half-width target is **explicitly superseded** for S3 (wild scarcity; min(all,40) yields
  ±15–21% — reported as such); it stays the target for M-yield's pooled precision. Holm/Bonferroni
  inherited across any SUT × tool grid (review B m8).

### 3.2 The two measurements
- **M-yield (generation-driven):** MIST pipeline, budget **pinned at 1 h × 10 seeds** on the
  spec-rich tier (TT, TeaStore, SS) and **1 h × 3 seeds** on the thin tier (disclosed; review C
  ladder) — matching E1's budget for fairness; LLM condition pinned (LLM-off, as G1 ran, disclosed;
  review C m1). **Event→case clustering (review B M1):** equivalence class = endpoint ×
  fault-signature × SUT; adjudicate one representative per cluster + a 10% random audit sample;
  report cluster counts. Yield = genuine/(genuine+benign) per §3.1 statistics. Upstream filing of
  genuine finds happens HERE (review A M3).
- **M-prevalence (wild):** **instruments named (review B B1):** (i) the trace-shape masking oracle
  on step-2.5-instrumented SUTs; (ii) the single-leg read-back-absence check as a SEPARATE mode —
  both FP-CALIBRATED ON THE S2 STRATUM BEFORE S3 SAMPLING; their FP profiles are NOT inherited from
  the paired-mode zeros (those are scoped to paired/probe modes). Workload per SUT **pinned**:
  named source (OTel-Demo built-in generator, loadgen disabled→controlled; SS upstream load-gen; TT
  authored scenario script; TeaStore browse profile; Boutique/Bookinfo scripted browse+write where a
  write path exists), **N = 12 h per SUT OR a 500-write-carrying-request stopping rule, whichever
  first**; workload scripts versioned in the benchmark; the workload's write-path fraction reported
  (it bounds the prevalence ceiling). **Estimand stated (review B M8):** a DETECTOR-CONDITIONED
  LOWER BOUND on genuine masked-defect prevalence under that workload class (genuine defects the
  detectors never flag are invisible); detector recall on S1 reported as the qualifier. Two
  denominators (per-request, per-endpoint).
### 3.3 S3 sampling (unchanged rule + priced branch)
Sample = min(all flagged, 40), stratified by SUT × flag type; < 20 wild flags total ⇒ the scarcity
IS the finding, prevalence carries wide CIs honestly, and the §1 benign-dominance interpretation
applies (pre-registered — review A M1).
**Acceptance:** §3.1 statistics gates met; underspecified fraction reported; per-SUT tables;
upstream filings attempted for every genuine wild find.

---

## §4 The evaluation chapter (scope + fairness mechanics)

- **E1 (two-tier grid — reviews C B2 / A m1):** FULL tier (TT, TeaStore, SS): 5 tools × 10 seeds ×
  1 h. THIN tier (Bookinfo, Boutique, OTel-Demo): 3 seeds × 30 min with saturation disclosed
  (Bookinfo ≈ 4 GET paths). Work items: author TeaStore + OTel-Demo OpenAPI specs (released with
  the benchmark, pre-registered as authored — review B m5); per SUT × tool auth glue with an
  **evaluability smoke gate** ("tool reaches ≥1 authed endpoint" — else the cell is reported
  non-evaluable, not zero). Tool-crash/timeout accounting + machine spec + run exclusivity
  inherited from the "No Time to Rest Yet" protocol (cited). Substitution rule: Morest/AutoRestTest
  install failure → RestTestGen; floor = ≥4 runnable tools (review C m2). AutoRestTest's LLM key +
  model pinned.
- **E2 (four arms — reviews A B1 / B M4):** (1) naive span-error oracle; (2) Tracetest + generic
  span-ERROR assertion; (3) **Tracetest + downstream-span-PRESENCE assertions, hand-authored per
  endpoint — reporting the per-endpoint authoring cost (the automation-gap datum) and its
  benign-trap FP**; (4) TraceAnomaly/TraceRCA (stretch, gated on the step-1 spike clearing in ≤2
  days; fallback = arms 1–3). **Matched recall, operationally defined:** threshold sweep where the
  comparator is tunable (TraceAnomaly score); single (P,R) point where not (naive, Tracetest);
  matching evaluated ON THE TRACE-VISIBLE SUBSET, with every case's visibility class tagged at
  freeze and **comparator recall reported per visibility class** (trace-invisible positives can
  never be silent N-vs-0 — they are their own disclosed row). §8.5-5's four soundness-threat
  disclosures are a writing obligation of this chapter.
- **E3** trigger rate (mined from E1/M-yield logs, free). **E5** ablations at one-SUT-pair scope
  (TT ×5 seeds; disclosed). **E6** release per §2.4-5.
- **E-item × SUT applicability matrix (review B m7):** produced at step-1 freeze — which SUTs carry
  E1-full/E1-thin, which have a write path (data-integrity strata), which are S2-only contributors
  (e.g., Bookinfo: benign traps + E1-thin only); prevents "on the 6 SUTs" from implying uniformity.

## §5 Sequencing, budget (evidence-based — review C adopted), infrastructure
**Timeline (single-box; ∥ = parallel):** step 0 reconcile 2 d ✔ · **step 1 (1 wk):** freeze wave
(§2.4-1) + §8.5-3 depth surveys + **R4 comparator spike (moved here)** + rater outreach (2–6 wk
lead, ∥) + applicability matrix · **step 2 (2.5–3.5 wk):** deploy wave (TeaStore, OTel-Demo,
Boutique; Bookinfo live) + **step 2.5 instrumentation wave** (per-SUT OTel + measured trace-coverage
table = the §8.5-2 disclosure + TraceAnomaly normal-corpus capture) + **per-SUT MIST enablement
package** (registry + auth smoke + one end-to-end paired run as DoD; thin SUTs may be
oracle/prevalence-only, disclosed) + wipe scripts (state-reset policy: per-SUT DB-wipe preferred,
rollout-restart fallback — review C M5) + **wave-runner** (unattended: timeout, logs, reset,
dispatch; 2–4 d) · **step 3a (1–2 wk, ∥ nights):** S1/S2 population + F-corpus builds (≥6, off-peak)
· **step 3b (1.5–3 wk calendar):** E1 two-tier (~160 h driven) · **step 4 (1–1.5 wk):** M-yield ·
**step 5:** M-prevalence runs (~72 h ≈ 1 wk) + S3 adjudication (2–4 wk, rater-gated ∥) · **step 6
(1 wk):** E2 (+1 wk if TraceAnomaly cleared) · **step 7 (3–4 d):** E5 · **step 8 (1 wk):** E6 +
benchmark sampled-reproduction review. **Total ≈ 10–13 wk single-box; ≈ 8–10 wk with a cloud-burst
second node.**
**Infrastructure (review C B1):** RECOMMENDED ask, surfaced to the user — one cloud VM (32–64 GB,
kind; ~300–400 machine-hours ≈ $150–600 spot) to run small-SUT E1/M-yield waves in parallel with
local TT work (halves elapsed; removes the WSL-relay SPOF). Single-box fallback = the schedule
above. **Runbook constraints (review C M2):** restore `.wslconfig` to 26 GB for TT waves; tenancy
schedule (big SUTs solo: TT, OTel-Demo; small co-reside: Bookinfo+SS); never build images while a
graph is deployed; pin ONE TT topology for E1/M-yield (lean-traced G1 topology — the scaled-to-0
subgraph turns fuzzer calls into 503 walls); docker-exec recovery runbook at hand; disk pruning per
wave (review C m3).
**5.4 De-scope ladder (adopted verbatim, never below the C2 floor):** 1 two-tier E1 (already
default) → 2 TraceAnomaly→fallback arms → 3 E5 to one SUT → 4 F-corpus ≥10→≥6 → 5 M-yield thin-tier
seeds ×3 → 6 NEVER cut core-6 from C2 (cut E-depth before SUT count) → E3 stays regardless.
**Risks:** R1 wild scarcity (priced, §1/§3.3) · R2 raters (fallback w/ scars) · R3 async coverage
(step-2.5 table = containment) · R4 comparator operability (step-1 spike + fallback arms) · R5
scale slip (floors = stop-and-replan) · **R6 (new, review C): host wedging under load** — exclusive
runs, wave-runner timeouts, 26 GB envelope, off-peak builds.

## §6 Review protocol — DISCHARGED for the plan (3× ACCEPT-WITH-CHANGES, reconciled); the
benchmark artifact and the C3/E2 results each get their own ≥3-cold-review at their §2.4/§3
acceptance gates. §7 Manifest: FILE_INDEX rows + memory updated with v2; execution begins at §5
step 1.
