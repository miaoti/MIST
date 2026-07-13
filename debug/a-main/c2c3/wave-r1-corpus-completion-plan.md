# Wave R1 — corpus-completion wave (step 3a: S1+S2 population to floors) — rev 2 (EXECUTION GO, phase-gated)

**Date:** 2026-07-13 · Owner: main_track · Status: **rev 2 — 3-cold-reviewed (A opus / B fable /
C sonnet, ALL ACCEPT-WITH-FIXES; `REVIEW-R1-PLAN-RECONCILIATION.md` = the authoritative fold map).
Phase A = GO. Phase B = BLOCKED until the §4-B0 gates close (C's split).** Rev-1 is superseded; the
rev-2 deltas are the reconciliation's X1–X6 + B-F4/C-F6/C-F7/C-F8.
**Provenance:** user direction 2026-07-13 — complete ALL experiments before paper drafting; C3
raters FOUND, materials missing; rater-materials critical path first. User decisions: S2 in one
wave; F-corpus in R1; E1 stays full-scope (wave M1).
**Authority chain:** plan v2 §2.3/§5 step 3a · `c2-freeze.md` rev 2.1 §5 (floors, C-A4 distinct-site
rule, two denominators, F-corpus in-class rule) · `c2-depth-survey.md` (NORMATIVE quotas + 2026-07-10
corrections) · `step2-execution-checklist.md` step 3a · `c2-license-audit.md` · S3
`RESULT-p5-assembly.md` §1–§3 · `b4/s3/s3-p0-pins.md` §2 (cadence).

---

## §0 Goal — what this wave EARNS (recounted, honest)

1. **S1 distinct-site growth 7 → 12–19 projected** (counting convention §1.0; ≥20 floor AT RISK —
   the §4-B0 survey computes the real projection BEFORE the expensive Phase B; a <20 projection
   surfaces the freeze §5 stop-and-replan decision instead of discovering it after the build loop).
2. **S2 trap growth 4 → 19–24 projected.** The ≥35 floor is **structurally unreachable on this SUT
   set** (all-candidate ceiling ≈30) — the freeze §5 disclosed-shortfall branch carries it, declared
   only after the §6 exhaustion bar is EARNED. C3 consequence owned now: rateable calibration
   benigns ≈15–20 < floor 30 (computed need ≈42–43) → the shortfall + power consequence + the
   pooled-κ(n≥50)-basis loss (until M-yield merges) are disclosed in RESULT-r1, not discovered at
   assembly.
3. **Diversity floors:** ≥4 mechanisms per write-path SUT (TeaStore broker-less min 3; OTel thins
   to 3-distinct on the checkout site post-cartFailure-refutation — noted, survey-blessed); the
   BINDING ≥6 acked-but-lost across write-path SUTs on distinct sites (met, widened); R8 tell-free
   floor TALLIED.
4. **Riders:** (a) `kafkaQueueProblems` stochastic-S1 characterization (wave-3a STOP disposition
   executed, §5-R1 conventions); (b) TeaStore observe-mode recall leg (S3 F2b remedy, §5-R2).
5. **C3 shape supply** per §3 (the redesigned polarity floors + cadence conformance + legacy
   re-captures) so the R2 assembly tell-audit can pass.

**Not in R1 (→R2/M1):** M-yield · OpenAPI authoring · wave-runner · assembly entry gate/seal · E1/E2/
E5/E3/E6 · any MIST tool-code change (capture-only at the existing pin).

## §1 Quota arithmetic (recounted per X1/X2 — the honest baseline)

### §1.0 Counting conventions (BINDING for RESULT-r1)
- **Distinct defect site** = the durable write target that gets lost (C-A4): TeaStore's 3 existing
  cases (maintenance/mesh-sever/depdown on the order-row via the same entry) = **1 site**; a
  same-entry case whose LOST ARTIFACT differs (order-items vs order-row) = a distinct site;
  mechanism variants on one site count toward mechanism diversity ONLY. Both denominators
  (distinct-site AND case-run) reported per freeze §5.
- **S2 floor denominator = stratum-2 benign-trap cases ONLY.** S1 clean controls are stratum 1 and
  NEVER count toward the ≥35 floor or the C3 benign supply (they are clean-present — banned from
  the rating mix by C-B3/A-F10). Label-negatives (traps + controls) are reported as a separate
  descriptive figure, never against a floor.
- **Packaged FP corpora** = ≤2 case UNITS for C2 (plan-v2 §2.3), authored with a dated freeze §6
  exemption (by-construction benign ground truth + pre-pin disclosure, S3-precedent form — C-F8),
  and **EXCLUDED from the C3 rateable supply** (record-sets, not 15–45-min rateable units; clean-
  present-shaped).

### §1.1 S1 — current honest count = **7 distinct sites**
TT 4 (cancel-refund [cancel/inside-payment/order], createaccount, adminroute, adminbasic-contacts) ·
TeaStore 1 (order-row) · OTel 1 (checkout→kafka→accounting) · SS 1 (shipping-enqueue).

| SUT | R1 additions | site yield | notes |
|---|---|---|---|
| TeaStore | order-items × mesh-abort (child-collection loss: order acked+present, items lost) + optional capped internal-CRUD 201/`-1` (sentinel-in-body, **EXCLUDED from the discriminating floor**, never a backfill) | **+1** | survey §1; DB-down stays specified-only (UNSOUND on this deploy) |
| OTel-Demo | kafka-loss × mesh-sever (same site, 2nd mechanism) · emptycart × method-scoped-sever (**VERIFY-FIRST**, refutation branch live) · kafkaQueueProblems rider (same site, 3rd mechanism — §5-R1) | **+0–1** | all kafka-path items = mechanism credit on the existing site |
| Boutique | checkout→EmptyCart × method-scoped abort (S1-minor, DISCLOSED; **VERIFY-FIRST**) | **+0–1** | needs 2.4 deploy |
| SS | carts-adjacent masked-site verify (addresses/cards paths); honest-loud ⇒ freeze recount corrected DOWN, disclosed | **+0–1** | prior = honest-loud on 3 SUTs |
| TT F-corpus | ≥6 target 10 replications from F1–F22 descriptions — **gated by §4-B0**: masked-2xx eligibility by description + **occupied-site cross-check (C-F2: upstream F10 targets Contacts = OCCUPIED ×3; F1 targets Cancel/Inside-Payment = the flagship site — occupied candidates earn floor-6/mechanism credit ONLY, never a new site)**; each counts only after live in-class verification (B-m6) | **+4–8 (B0-determined)** | floor candidates re-selected at B0 from the UNOCCUPIED-service subset |

**Projected S1 distinct sites: 7 + 1 + (0–1) + (0–1) + (0–1) + (4–8) = 12–19.** ≥20 is AT SERIOUS
RISK; the B0 survey computes the real projection and a <20 projection surfaces the stop-and-replan
decision (options: extend the F-corpus target on unoccupied services · accept the disclosed <20
finding · widen elsewhere) BEFORE Phase B spends its budget. Case-run denominator reported alongside.

### §1.2 S2 — current honest count = **4 traps**
(tt-contacts-dedupe, tt-noop-modify, bookinfo-ratings-benign, oteldemo-eventual-benign-w120.)

| source | R1 additions (each benign-by-design, by-docs provenance + citation unless noted) |
|---|---|
| TT | +2–4: admin-basic duplicate-key family on station/config/price (same `{status:0}` class) · login/auth honest-failure trap — live-verified shapes |
| SS | +1–2: queue warm-up first-POST transient · rabbit-user re-create window |
| TeaStore | +2: maintenance-503 read window (benign side of the S1 toggle) · DB-regen wipe (documented destructive maintenance; LAST on tenant) |
| OTel-Demo | +4–6 from the 13 as-deployed flags: duplicate-delivery dedupe (forced duplicate) · imageSlowLoad(10sec) · ad-family (verify per D3b; refutation = recorded anti-finding) · emailMemoryLeak graded · failedReadinessProbe |
| Bookinfo | +2: productpage→reviews degraded · productpage→details degraded (documented Istio-task outcomes) |
| Boutique | +2: adservice-failure 200-no-ads · recommendation-failure 200-no-recs (FRESH captures, rev-2 sidecars) |
| packaged | +2 units (per §1.0 conventions — C2 only, C3-excluded) |

**Projected S2 traps: 4 + 15–20 = 19–24** (floor ≥35 unreachable, disclosed via the §6 exhaustion
bar). Rateable C3 benigns ≈15–20 (traps, minus packaged units, minus ~2 worked-example consumption).

### §1.3 R8 tell-free tally (obligation)
Count natural × `success-shaped-clean` × `trace-invisible-by-construction` positives post-R1;
smallness is the honest, positioning-strengthening finding.

## §2 Per-case obligations (unchanged from rev 1 + one addition)
(1) negative control per S1 (B-M3 note on controls); (2) health-precondition + probe-first (N≥4);
(3) replay script + rater-sidecar (1.95.05 format); (4) typed readback + `ack_content_visibility` +
`trace_visibility` (capture_status-keyed) + `write_shape` + `oracle_mode` + `mist_authoring` +
license fields + digests; (5) validator PASS per case + corpus-wide at wave end; (6) A-M8
contract-grounding/construction-bar disclosure for best-effort writes (OTel); (7) license conduct at
point of use (F-corpus per §4-B0 clean-room; change notices; never re-push images); (8) per-case
`mist_commit` at the R1 pin + a dated freeze §6 pin-lineage row; (9) T2 capture-of-record on any
re-touch; **(10) per-SUT pre-batch render gate (B-F4): before a tenant's capture batch closes, one
authored bundle round-trips `b4_harness.render` with 0 BANNED_STRINGS.**

## §3 C3 shape supply (redesigned per X3/B-F2 — the load-bearing fix)

- **Three-way shape taxonomy (BINDING stamp per case):** `write-acked-absent` (soft-rejects,
  by-design drops — dedupe family, duplicate-dedupe, DB-regen) · `write-acked-eventual-present`
  (bounded eventual consistency — w120 class) · `no-write-degradation` (read-path benigns — ad
  family, reviews/details degraded, imageSlowLoad).
- **Floors (achievable, decode-resistant):** **write-acked-absent benigns ≥8** (≥ the genuine count
  in the rated mix — B's arithmetic: at 8, a pure "absent⇒genuine" decoder falls to
  P(genuine|absent)=0.50) · **write-acked-eventual-present ≥2** (w120 + ≥1 deliberate
  bounded-backlog capture: accounting scale-0 → acked orders buffer → scale-up → drain-verified;
  provenance disposition decided at authoring — by-construction benign with a dated freeze §6
  exemption if needed, S3-precedent form).
- **Structural decode directions DISCLOSED, not "fixed":** PRESENT⇒benign and no-write⇒benign are
  structural (every genuine is ABSENT); the pre-registered detector for a presence-decoding rater is
  the known-label bias audit (a pure decoder mislabels every known-benign-absent — unmissable in the
  per-rater confusion matrix). RESULT-r1 states this framing verbatim; no "presence does not decode
  the label" overclaim.
- **Cadence conformance (B-F4):** the s3-p0-pins §2 cadence governs rating-destined captures; a
  dated pin EXTENDS it to SS/Bookinfo/Boutique (sync default: 10 s cap / 0.5 s poll / 300 s
  re-probe) BEFORE Phase A; **legacy trap re-captures** (tt-contacts-dedupe, tt-noop-modify,
  bookinfo-ratings — all pre-cadence) are scheduled inside their tenants' windows under T2.

## §4 Phases + tenancy (26 GB WSL; runbook rules verbatim)

**Phase A — small tenants (GO):**
A0. Cadence-extension pin committed → `free` gate → revive OTel-Demo + TeaStore (PF re-establish;
    kafka-wedge runbook at hand).
A1. OTel: kafka×mesh-sever S1 pair → emptycart×method-sever VERIFY→capture-or-refute → S2 batch
    (dedupe-forced-duplicate, imageSlowLoad, ad-family verify, emailMemoryLeak, failedReadinessProbe)
    → bounded-backlog eventual-present capture (§3) → **kafka rider LAST** (§5-R1; recovery restart +
    health canary after). **Teardown-verification gate between EVERY capture (C-F7): zero
    fault-related VS/EnvoyFilter/env objects + a clean healthy-probe round.**
A2. TeaStore: order-items×mesh-abort S1 pair → maintenance-503 benign S2 → **observe-recall rider**
    (§5-R2) → DB-regen wipe S2 LAST (regenerate + re-seed verify after). Same teardown gates.
A3. Bookinfo (tiny): 2 S2 + the legacy bookinfo-ratings re-capture. Boutique (light): deploy (2.4) →
    S1-minor verify→capture-or-refute → 2 S2 → scale to 0.
A4. SS window (swap vs TeaStore if RAM-tight): revive (rabbit-user + warm-up runbook) →
    carts-adjacent verify → S2 captures → scale to 0.
**Phase A exit:** quotas captured-or-refuted · per-SUT render gates passed · artifacts committed ·
tenants to 0.

**Phase B0 — F-corpus gates (BLOCKS Phase B; C's split; can run DURING Phase A):**
1. **Description-only eligibility survey:** transcribe the F1–F22 prose (README table / the survey
   paper's fault table — NEVER the fault-code branches) into a fresh spec file
   (`f-corpus-spec.md`); per fault: masked-2xx-by-description call + **occupied-site cross-check**
   (occupied = cancel/inside-payment/order, createaccount, adminroute/route, adminbasic/contacts);
   select ≥6 eligible candidates weighted to UNOCCUPIED services; if the resulting §1.1 projection
   is <20 sites → surface the stop-and-replan decision BEFORE any build.
2. **License/lineage closure (C-F1):** GitHub license-API check on `AsifShaafi/train-ticket-injection`;
   resolve the `codewisdom` Docker Hub namespace (faithful notice-preserving redistribution of the
   Apache-2.0 base?); a correction plan for the DEAD `repo: github.com/miaoti/train-ticket-injection`
   citation across the existing TT stratum (fix field or dated disclosure — applied in Phase C);
   **fork-publication decision (pre-E6 release obligation) FLAGGED TO USER — not decided here.**
3. **Survey-paper citation resolved + pinned** into `c2-license-audit.md` (C-F4; verify against the
   upstream README's own reference — expected: the Fudan TSE fault-analysis study).
4. **Two-actor clean-room protocol (C-F3):** the implementer is an ISOLATED subagent whose ONLY
   input is `f-corpus-spec.md` (+ the clean upstream FudanSELab base source), explicitly instructed
   never to fetch `train-ticket-fault-replicate`; per fault, the input artifact (paper row / README
   paragraph) is recorded in the spec file; the orchestrator never pastes upstream diff/code into
   the implementer's context.

**Phase B — TT window (solo; starts only when B0 is CLOSED):**
B1. All graphs at 0 → build the B0-selected fork images off-peak (change notices in diffs) → revive
    TT (runbook §2.6 + nacos doubleWrite + Xenon force-recreate + WSL-flap discipline).
B2. Replication loop per fault: deploy → probe-first → **in-class verification gate** (masked-2xx
    acked-but-lost live, B-m6; fail ⇒ swap from the B0-eligible pool, disclosed) → fault+control
    capture pair → restore base → teardown-verify → next. Floor 6 / target 10 / **stop rule: <6
    in-class-verified after 4 working days ⇒ SHIP achieved + freeze shortfall disclosure**.
B3. TT S2 enumeration (+2–4) + the legacy TT trap re-captures (dedupe, noop-modify).
B4. Restore posture + key-path smoke.

**Phase C — close:** corpus-wide validator · R8 tally · shape-polarity census (§3 stamps) · dead-
citation correction applied · README/freeze §6 (pin-lineage row + packaged-corpora exemption + any
shape-provenance exemption) /FILE_INDEX/memory sync · `RESULT-r1.md` (recounted tables
achieved-vs-projected, refutation records, exhaustion-bar evidence, both S1 denominators) ·
**≥3-cold-review of the RESULT.**

## §5 Riders

- **R1 `kafkaQueueProblems` stochastic S1 (conventions per X4 — BINDING):** control-leg FIRST (N≥10
  flag-off trials on a healthy pipeline), THEN N≥20 flag-on in-window acked orders; per-order psql
  verdict at the pinned cadence + T+5 min re-probe; loss-rate + Wilson CI; past-toggle poisoning
  window measured (flag-off canaries until first success → recovery restart → health canary).
  Case fields: `oracle_expectation.*` enum verdicts stand (`mist_readback_oracle: not_applicable`
  in R1 — C-F9); measured rate + CI live in `fault.config` + provenance notes; **scoring convention
  pinned in-file: per-lost-trial; replay = N trials, reproduce = ≥1 acked-lost**; sidecar = ONE
  representative lost-trial transcript, the N-trial characterization attached as raw evidence;
  `injection_method: vendor_flag`, `ground_truth.source: vendor` (NOT by-docs);
  **calibration-genuine-INELIGIBLE (async-no-bound) stamp — applied equally to the existing
  checkout-lost flagship and the new mesh-sever variant** (assembly must know which genuines are
  rubric-reachable); wedge-restart budget between trial batches priced (wave-3a: ~8 orders wedged
  the clients).
- **R2 TeaStore observe-mode recall leg:** one fresh observe-mode MIST run vs the maintenance-masked
  fault at the R1 pin → a measured observe-mode CONFIRMED (or the honest failure); freeze §6 note
  amends the S3 F2b disclosure to "remedied on TeaStore".

## §6 Budget, stop rules, risks

- Phase A 3–5 d · B0 ≤1 d (parallel with A) · Phase B 4–7 d (stop rule §4-B2) · Phase C 1–2 d.
- **S2 exhaustion bar (C-F6 — shortfall may be DECLARED only when):** every §1.2-named candidate is
  captured or refuted-with-datum · the flagd-13 sweep is complete (each flag adjudicated
  S2/S1/refuted) · the Bookinfo degradation-path list is exhausted · the TT/SS enumeration lists are
  exhausted. Until then the shortfall is a projection, not a result.
- **Combined cart-refutation contingency (X6):** if OTel AND Boutique emptycart both refute → re-run
  the §1.1 projection immediately; backfill = F-corpus target extension on unoccupied services
  (never the sentinel tier); if still <20 → the stop-and-replan surface.
- **Refutation discipline:** refuted candidates are recorded anti-findings (survey-correction
  style); never silently replaced beyond the named alternates.
- **Destructive-op ordering:** DB-regen wipe + kafka rider LAST on their tenants; teardown-verify
  gates between all captures (C-F7); never `GET /rest/generatedb` as a probe.
- **RAM:** `free` gate per revival; small-batch restarts; WSL-flap = wait; wedge >½ day ⇒ snapshot +
  continue + return.
- Stochastic-rider CI width at N=20 reported as measured (honest CI beats deterministic overclaim).

## §7 Acceptance (DoD — recounted numbers)

1. S1: both denominators reported; distinct-site projection vs achieved table; **≥20 evaluated
   post-B0 — achieved ≥20 OR the stop-and-replan decision surfaced with options**; R8 tally.
2. S2: trap-only achieved band vs 19–24 projection; **≥35 shortfall declared ONLY via the §6
   exhaustion bar**; rateable-benign count + floor-30 shortfall + power consequence + pooled-κ basis
   loss stated in RESULT-r1.
3. Shape floors: write-acked-absent benigns ≥8 · eventual-present ≥2 · taxonomy stamp on every S2 ·
   cadence conformance incl. the 3 legacy re-captures · per-SUT render gates passed.
4. Riders executed-or-honestly-failed with artifacts; kafka conventions honored in-file.
5. Every case: §2 obligations; corpus-wide validator green; replay scripts committed.
6. B0 artifacts: `f-corpus-spec.md` (eligibility + site cross-check + per-fault input provenance) ·
   license/lineage closure record · citation pinned · clean-room transcripts referenced.
7. Syncs (README/freeze/FILE_INDEX/memory) · tenants at declared end-state · `RESULT-r1.md` +
   ≥3-cold-review PASSED with fixes folded.

## §8 Out of scope
M-yield / OpenAPI / wave-runner / assembly gate + seal (R2) · E1/E2/E5/E3/E6 (M1) · MIST tool-code
changes · TT lean-traced convergence · S3-class wild hunting (closed) · the fork-publication
decision (USER, flagged at B0).
