# Wave R1b — S1 distinct-site widening (reach the ≥20 floor) — rev 1 (FOR 3-COLD REVIEW)

**Date:** 2026-07-13 · Owner: main_track · Status: **DRAFT — requires ≥3 independent cold reviewers
UNANIMOUS-ACCEPT before any fork engineering (standing /goal rule; plan-v2 §8 + R1 §4-B0-1 route
option 3 "widen … out of R1's reviewed scope; requires a new plan + full review").**
**Provenance:** user decision 2026-07-13 (AskUserQuestion) — after B0 surfaced that the ≥20
distinct-site floor is unreachable with natural/existing sites (ceiling 13; masked-2xx sites are
scarce = the S3 finding), the user chose **"widen to ≥20"** over accept-and-disclose, aligning with
the standing "do all experiments as completely as possible" directive.
**Relationship to R1:** R1b is a scoped ADDENDUM to `wave-r1-corpus-completion-plan.md` rev 2; it
adds constructed S1 distinct sites on UNOCCUPIED TrainTicket write services. It folds into R1's
Phase B (TT-solo window). R1's S2 population, F-corpus, riders, and honesty rules are unchanged.

---

## §0 Goal + the honesty problem this plan must solve

**Goal:** author enough NEW distinct S1 defect sites to bring the corpus's distinct-site count from
the B0-projected ~13 to **≥20**, cleanly crossing the freeze §5 floor instead of disclosing a
shortfall.

**The honesty problem (stated up front, because it IS the design constraint):** the cheapest way to
+7 sites is 7 identical `lostwrite`-flag injections on 7 services — which is exactly the **padding**
the distinct-site metric exists to prevent (C-A4). Ten uniform-mechanism injections would inflate
the site count while adding zero mechanism diversity, and a hostile PC would (correctly) call it
padding. **This plan is therefore constrained to make the new sites GENUINELY DIVERSE** — different
fault mechanisms, different read-back modalities, different business domains — so the widening is
real benchmark breadth, not a number game. The three standing honesty rails are preserved and
re-stated as acceptance gates (§5): two-denominator reporting, the tell-free-natural floor (R8) kept
separate and honestly small, and the by-injection provenance labeled on every new case.

**What R1b does NOT claim:** that these are natural. They are CONSTRUCTED positives, the same class
as the existing `adminroute`/`adminbasic` lost-writes — the scarcity of NATURAL masked sites (S3) is
unchanged and remains the honest finding that JUSTIFIES constructed positives. R1b raises the corpus
SCALE + site diversity; it does not touch the natural-vs-constructed story.

## §1 The write surface (surveyed, TT at 0, from the G2 contract + target-triples)

TT exposes **21 services with POST-create write endpoints** (G2 `blind-assertions-trainticket.yaml`,
23-service coverage). OCCUPIED (existing corpus sites, C-F2): ts-route/ts-admin-route (adminroute),
ts-contacts/ts-admin-basic-info (adminbasic-contacts), ts-inside-payment (cancel + createaccount),
ts-order (cancel constellation). **UNOCCUPIED write-capable services (~15):** ts-station, ts-train,
ts-config, ts-price, ts-travel, ts-travel2, ts-food, ts-consign, ts-consign-price, ts-order-other,
ts-security, ts-user, ts-auth, ts-admin-order, ts-admin-travel, ts-admin-user. **Ample surface for
+10 sites with in-class-verify attrition margin.**

## §2 Target set — 10 NEW sites, mechanism- and modality-DIVERSE (the anti-padding design)

Each row = one distinct defect site (distinct service + write target + read-back). The MECHANISM and
READ-BACK MODALITY columns are deliberately varied; the target is ≥4 mechanism classes and ≥3
read-back modalities across the 10, so the widening adds real diversity, not 10 clones. Final
per-site mechanism is confirmed at authoring against what the service's code actually admits
(disclosed if a planned mechanism proves infeasible → swap within the surviving surface).

| # | service (site) | write endpoint | mechanism (planned) | read-back modality | rationale for the mechanism |
|---|---|---|---|---|---|
| 1 | ts-station-service | POST /stationservice/stations | skipped-persist flag | membership (GET /stations) | simplest create/list; the baseline flag site |
| 2 | ts-train-service | POST /trainservice/trains | fabricated-ack (return crafted success, no persist) | membership (GET /trains) | fabricated-ack diversity (the E2 class, natural site) |
| 3 | ts-config-service | POST /configservice/configs | skipped-persist flag | membership (GET /configs) | admin-config domain |
| 4 | ts-price-service | POST /priceservice/prices | value-corrupt (persist a WRONG value, ack clean) | value-delta (GET price, compare) | value-delta modality + a corruption (not just absence) mechanism |
| 5 | ts-travel-service | POST /travelservice/trips | skipped-persist flag | membership (GET /trips) | travel domain |
| 6 | ts-food-service | POST /foodservice/foods (order food) | partial-aggregate (parent food-order lands, items lost) | count-delta (items) | the partial-aggregate class (TeaStore-orderitems analog on TT) |
| 7 | ts-consign-service | POST /consignservice/consigns | skipped-persist flag | membership (GET /consigns/account/{id}) | consign domain |
| 8 | ts-order-other-service | POST /orderOtherService/orderOther | fabricated-ack | membership (GET /orderOther by acct) | second order type; fabricated-ack |
| 9 | ts-security-service | POST /securityservice/securityConfigs | skipped-persist flag | membership (GET /securityConfigs) | security-config domain |
| 10 | ts-contacts-service | POST /contactservice/contacts (USER path, distinct from the OCCUPIED admin path) | dependency-down (a real downstream dep severed) where one exists, else skipped-persist | membership (GET /contacts/account/{id}) | a NON-admin contacts write = a distinct SITE from adminbasic-contacts; try a dependency-down mechanism for diversity |

**Diversity tally (target):** mechanisms = {skipped-persist ×5, fabricated-ack ×2, value-corrupt ×1,
partial-aggregate ×1, dependency-down ×1} = **5 mechanism classes**; modalities = {membership,
value-delta, count-delta} = **3**. If a planned mechanism proves infeasible on a service, swap the
mechanism (not the site) from the surviving set and disclose — never silently homogenize to
all-skipped-persist (that would re-introduce the padding problem, so it is a REVIEW-GATED deviation:
if >2 sites collapse to skipped-persist, STOP and re-surface).

**Site accounting:** current ~8 captured + F-corpus unoccupied 2 + these 10 = **~20**, with margin
for in-class-verify attrition. If attrition drops below 20, the freeze §5 disclosed-shortfall branch
carries the remainder (we do NOT inject beyond this diverse set just to hit the number).

## §3 Engineering (replicate the proven adminroute/adminbasic fork pattern — CLEAN-ROOM + license)

- **Mechanism scaffold = OUR OWN pattern** (already in the fork on adminroute/adminbasic): an opt-in
  guard `if (mist.fault.<mech>.enabled) { <mechanism>; }` gating the persist call, keyed by a JVM
  system property set via `kubectl set env JAVA_TOOL_OPTIONS`. This is authored by US, not copied
  from any third-party fault repo — **the R1 §4-B0 clean-room constraint does NOT apply here** (that
  constraint is specific to the FudanSELab fault-replicate F-corpus; R1b's injections are our own
  construction on the Apache-2.0 base). Apache-2.0 §4 change notices on every modified service file;
  fork diffs on the MIST-trainticket lineage; never re-push upstream images.
- **Per site:** edit the service's create controller with the guarded mechanism → build the fork
  image (off-peak, never while a graph is deployed) → deploy → **probe-first** (N≥4 vs
  ribbon/registry round-robin) → **in-class verification gate (B-m6): masked-2xx acked-but-lost
  demonstrated live** (clean 2xx/success-envelope ack ∧ durable read-back absent/wrong) → fault +
  control capture pair (control-first) → restore base image → teardown-verify → next.
- **Batching:** build all ~10 fork images in ONE off-peak batch (all graphs at 0), then deploy TT
  once and iterate the sites within the single TT window (avoid repeated TT revivals — each is
  expensive per the runbook). RAM: the lean-traced G1 topology + only the involved services scaled
  up; the rest at 0.
- **mist_commit / pin:** a new R1b fork pin recorded; a dated freeze §6 row (pin lineage). The
  study-commit-pin discipline continues via disclosed pin history (2.75-A/S3 precedent).

## §4 Phase placement + budget + stop rules

- **Folds into R1 Phase B** (TT-solo). Order within Phase B: F-corpus B0-eligible captures (R1) →
  R1b new-site captures → TT S2 + legacy re-captures (R1). All in the one TT window.
- **Budget:** fork edits + build batch 1–2 d · deploy + 10-site iterate 3–5 d · = **~1 wk added to
  Phase B**.
- **Stop rules:** (a) in-class-verify attrition — a site that cannot be made masked-2xx after a
  reasonable attempt is dropped + disclosed (swap within the surface); (b) **mechanism-homogeneity
  guard** — if >2 sites collapse to skipped-persist (diversity lost), STOP + re-surface (do not ship
  a padded set); (c) if total distinct sites still <20 after the diverse set is exhausted, the
  freeze §5 disclosed-shortfall carries it (we do NOT add uniform injections to force 20); (d) TT
  stability per the runbook (nacos/Xenon/WSL-flap disciplines).

## §5 Acceptance (DoD) — the honesty gates are FIRST-CLASS

1. **≥10 new distinct sites captured-or-attrition-disclosed**, each in-class-verified masked-2xx,
   each with a negative control, replay script, sidecar, typed read-back, digests, license change
   notice, `provenance_class=by-injection`, `ground_truth.source=by_construction`.
2. **Diversity delivered:** ≥4 mechanism classes AND ≥3 read-back modalities across the new sites
   (else the mechanism-homogeneity stop fired + is disclosed).
3. **Two-denominator honesty:** RESULT reports distinct-site count (target ≥20) AND case-run count,
   with the widening's constructed-site contribution explicitly labeled; NO claim that these are
   natural.
4. **Tell-free-natural floor (R8) reported SEPARATELY and honestly** — the widening does NOT inflate
   it (constructed sites are excluded from the natural-exhibit tally); the natural-exhibit count
   stays small = the disclosed scarcity finding.
5. **Corpus-wide validator green;** every new case schema-valid; the mechanism scaffold's
   opt-in/off-by-default nature disclosed (the SUT ships clean; the flag is lab scaffolding, the
   §2.7-A "B1 is not a contribution" framing carried).
6. **README/freeze §6/FILE_INDEX/memory synced; RESULT-r1b (or a merged RESULT-r1) + ≥3-cold-review
   PASSED.**

## §6 Risks + honest framing carried

- **"This is padding" (the central PC objection):** answered by §2's enforced mechanism/modality
  diversity + the mechanism-homogeneity stop + the two-denominator + tell-free-natural separation.
  The RESULT states plainly: the widening raises corpus SCALE and site diversity via CONSTRUCTED
  positives (honestly labeled); natural masked sites remain scarce (S3); the paper leads with the
  study, and the site count is corpus scale, not a headline.
- **"Uniform mechanism after all":** the homogeneity stop (>2 skipped-persist collapse ⇒ STOP)
  prevents shipping a de-facto-uniform set under a diverse label.
- **Fork engineering fragility / TT stability:** the proven adminroute/adminbasic pattern + the
  runbook disciplines; build-batch-then-single-window to minimize TT revivals.
- **Value question (disclosed for the reviewers):** a reviewer MAY argue accept-and-disclose (~13)
  was the higher-integrity move; this plan executes the user's explicit choice to cross the floor,
  and makes crossing it HONEST via diversity rather than declining it — the reviewers should judge
  whether §2's diversity design actually defuses the padding objection, and REJECT if it does not.

## §7 Out of scope
R1's S2 population + F-corpus + riders (unchanged) · M-yield/OpenAPI/wave-runner/assembly (R2) ·
E1/E2/E5/E6 (M1) · any NATURAL-discrimination claim · new non-TT SUTs (the surface here suffices) ·
the fork-publication decision (USER, flagged at B0 — still owed for E6 reproducibility).
