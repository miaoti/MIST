# Wave R1 — corpus-completion wave (step 3a: S1+S2 population to floors) — rev 1 (FOR 3-COLD REVIEW)

**Date:** 2026-07-13 · Owner: main_track · Status: **DRAFT — requires ≥3 independent cold reviewers
UNANIMOUS-ACCEPT before execution (standing /goal rule).**
**Provenance:** user direction 2026-07-13 — *complete ALL experiments before any paper drafting; the
C3 raters are FOUND and waiting on MATERIALS; the rater-materials critical path runs first.* User
decisions folded: (1) S2 pushed to ≥35 in ONE wave; (2) the TT F-corpus (≥6) is IN this wave; (3) E1
stays full-scope (later wave M1 — "do all experiments as completely as possible").
**Authority chain:** plan v2 `c2c3-execution-plan.md` §2.3/§5 (step 3a) · `c2-freeze.md` rev 2.1 §5
(floors, two denominators, F-corpus in-class rule) · `c2-depth-survey.md` (NORMATIVE per-SUT quotas +
live-verification corrections) · `step2-execution-checklist.md` step 3a (per-case obligations) ·
`c2-license-audit.md` (replicate-by-description, zero code copied) · S3 `RESULT-p5-assembly.md` §2–§3
(the C3 benign-shape + presence↮label obligations this wave discharges).

---

## §0 Goal — what this wave EARNS (in freeze terms)

R1 is **step 3a executed to its pre-registered floors**: populate S1 (positives) and S2 (benign traps)
so that:

1. **S1 distinct-site floor:** ≥20 distinct defect sites across the write-path SUTs (freeze §5 —
   below 20 = disclosed finding + stop-and-replan). Current honest count ≈ 9–10 captured sites; the
   survey's consolidated quota table + the F-corpus close the gap.
2. **S2 floor:** ≥35 benign cases **or the pre-registered disclosed shortfall** (freeze §5). Current
   = 12. This is simultaneously the C3 calibration benign supply (computed need ≈42–43; floor 30) and
   the fix for the S3 P5 §3 presence↮label tell — so S2 population here is **shape-constrained**
   (§3 below), not just count-constrained.
3. **Diversity floors:** ≥4 mechanisms per write-path SUT (TeaStore broker-less minimum 3); the
   BINDING ≥6 acked-but-lost across write-path SUTs on distinct sites (already met, widened here);
   the R8 tell-free floor TALLIED (natural × success-shaped-clean × trace-invisible-by-construction).
4. **Two riders that fold in at near-zero marginal cost:** (a) the deferred `kafkaQueueProblems`
   S1-positive candidate (wave-3a STOP disposition: "authored only under its own discipline in a
   later wave") gets its many-trial characterization + case; (b) the S3 F2b disclosed deviation
   (observe-mode measured-recall leg not freshly run) is remedied on TeaStore while it is up.

**What R1 does NOT do (→ R2/M1):** M-yield · OpenAPI authoring · wave-runner · corpus-assembly
9-check entry gate / sealing · E1/E2/E5/E6. R1's output is the RAW MATERIAL those consume.

**Honest prior:** most target mechanisms are already live-verified (survey corrections 2026-07-10) or
source-verified; the genuinely uncertain items carry pre-registered refutation branches (§2 notes +
§6). A refuted mechanism is a documented datum + quota shortfall disclosure, never a silent swap
beyond the named alternates.

---

## §1 Quota arithmetic (current → target; NORMATIVE sources cited)

### S1 (positives) — by SUT

| SUT | captured now (sites) | R1 additions (each = fault case + negative control unless noted) | source |
|---|---|---|---|
| **TeaStore** | 2 (maintenance-masked, mesh-sever) + 1 specified (depdown, never tallied) | **+1** order-items × mesh-abort (the partial-write / child-collection case: order row acked+present, items lost) · **+1 optional capped** internal-CRUD 201/`-1` tier (breadth, `sentinel-in-body`, segregated from the discriminating denominator) | survey §1 (4 natural pairs; DB-down = UNSOUND-for-capture on this deploy, stays specified) |
| **OTel-Demo** | 1 (checkout→Kafka→accounting × broker-down) | **+1** kafka-loss × **mesh-sever** (sever checkout→kafka at the mesh; same defect site, 2nd mechanism — counts toward mechanism diversity, NOT a new site) · **+1 site** emptycart × method-scoped-sever (**VERIFY-FIRST**: item-1 refuted the *flag* producer loudly; whether an Istio abort scoped to `/oteldemo.CartService/EmptyCart` alone reproduces the main-branch swallow on 2.2.0 is UNVERIFIED — probe before capture; refutation branch pre-registered) · **+1 site** `kafkaQueueProblems` S1 rider (§5-R1) | survey §2 + wave-3a items 1/3 |
| **Boutique** | 0 | **+1** (S1-minor, DISCLOSED — below the write-path floor by design): checkout→EmptyCart × method-scoped mesh abort (`/hipstershop.CartService/EmptyCart`, HTTP/2 path match — the 2.4 rider). Verify-first; same refutation caveat as OTel's emptycart | survey §3 (quota 1, optional) |
| **SS** | 1 (shipping-swallowed-enqueue) | **+0–1**: verify whether a carts-adjacent MASKED site exists (addresses/cards add paths); the G3 β datum says cart-store failure is honest-loud — if no masked site, the freeze §5 "SS ~2–3 sites" recount is corrected DOWN with a dated disclosure | freeze §5 recount + item-1 cross-SUT datum |
| **TT** | 4 (cancel-refund, createaccount, adminroute, adminbasic) | **+≥6 F-corpus replications** (floor 6, target 10; candidate pool = F1–F22 descriptions; named floor candidates F6/F8/F10/F20 + 2 chosen at description-survey time; **each counts ONLY after in-class verification** — masked-2xx acked-but-lost on the replicated deploy, B-m6) | plan v2 §2.3 + freeze §5 + license audit |

**Projected S1 distinct sites after R1:** 9–10 current + TeaStore 1 + OTel 1–2 + Boutique 1 + TT 6–10
≈ **18–24**. The ≥20 floor is REACHABLE but not guaranteed (F-corpus in-class attrition is the risk);
< 20 ⇒ the freeze's disclosed-finding + stop-and-replan branch — pre-registered here, not a surprise.

### S2 (benign traps) — to ≥35 or disclosed shortfall

| source | cases now | R1 additions (each benign-by-design with doc/source citation — by-docs provenance) |
|---|---|---|
| TT | 3 (dedupe ×2, noop-modify) | **+2–4** from live enumeration (checklist B-B2 demands ENUMERATED, not hand-waved): candidates = admin-basic duplicate-key family on the OTHER two endpoints (station/config/price soft-rejects, same `{status:0}` class as contacts-dedupe) · login/auth honest-failure trap · verify-at-capture |
| SS | 0 | **+1–2**: queue warm-up first-POST transient (runbook-documented) · rabbit-user re-create window — verify benign-shape live |
| TeaStore | 0 | **+2**: maintenance-503 read window (dual-use of the S1 toggle — the BENIGN side: reads stay 200, writes soft-fail visibly) · DB-regeneration wipe (`GET /rest/generatedb` — documented destructive maintenance; capture LAST on this tenant, it wipes state) |
| OTel-Demo | 2 (checkout-control*, w120 eventual-benign) | **+4–6** from the 13 as-deployed flags with benign doc labels: duplicate-delivery dedupe (accounting skips unique-violations — capture a forced duplicate) · imageSlowLoad (10sec variant — degraded-but-delivered) · adFailure / adManualGc / adHighCpu (ad panel best-effort; D3b showed the SSR page stays 200 while `/api/data` 500s — capture at the XHR surface where the degradation IS visible-but-benign, or refute per D3b and disclose) · emailMemoryLeak graded · failedReadinessProbe (pod-level, service continuity via replicas) — EACH verified live for benign shape before authoring |
| Bookinfo | 1 (ratings-benign) | **+2**: productpage→reviews degraded ("product reviews are currently unavailable", 200) · productpage→details degraded — both documented Istio-task expected outcomes |
| Boutique | 0 | **+2**: adservice failure → 200 page without ads · recommendation failure → 200 without recs (survey §3, in-tree) — FRESH captures with rev-2 sidecars (the old committed traces are not sidecar-compatible) |
| packaged FP corpora | 0 | **+2** (≤2 cases each = ≤2 case UNITS with record-set attachments, no padding — §2.3): the G1 TT benign-probe corpus (0/2127) + the SS FP-probe corpus (0/1200) |

*(the `-control` twins of S1 cases are negative controls, counted per the R1↔schema reconciliation
convention already in the corpus — the S2 count here uses the same counting rule as the current
"11 neg" tally so the arithmetic is apples-to-apples.)*

**Projected S2 after R1:** 12 + (TT 2–4) + (SS 1–2) + (TeaStore 2) + (OTel 4–6) + (Bookinfo 2) +
(Boutique 2) + (packaged 2) ≈ **27–32 → 29–34 with controls-convention**. Honest projection: **≥35 is
AT RISK — landing 30–34 is the likely band.** The freeze §5 shortfall branch ("target ≥35 or
disclosed shortfall") is pre-invoked as the handling; the C3-calibration consequence is bounded
because the calibration benign floor is 30 (met in-band) even when the computed ≈42–43 is not.
Every refuted candidate (e.g., more D3b-style refutations) is a documented anti-finding, which the
survey shows are themselves usable data.

### R8 tell-free floor tally (obligation, not a quota)
After R1, count and record: natural × `success-shaped-clean` × `trace-invisible-by-construction`
positives. Expected small (TeaStore order-confirm + SS swallowed-enqueue + any F-corpus member that
qualifies); smallness is the honest, positioning-strengthening finding — never padded.

---

## §2 Per-case obligations (checklist 3a, verbatim discipline — every case, no exceptions)

1. **Negative control** for every S1 (same-deploy twin; controls carry the pair's visibility regime
   note per B-M3).
2. **Health-precondition checklist + probe-first** after every rollout (N≥4 consecutive probes vs
   ribbon/registry round-robin; first-attempt-discard rule).
3. **Replay script** per case (automated, clean-cluster runnable) + **rater-sidecar** (1.95.05 format:
   ordered request/response records, relative times, producer + mist_commit stamp).
4. **Typed readback** (`readback{}` membership/value-delta/sql-probe) + `ack_content_visibility` +
   `trace_visibility` (capture_status-keyed: as-deployed uninstrumented ⇒ `not_applicable`, R2) +
   `write_shape` + `oracle_mode` + `mist_authoring` + license fields + image digests.
5. **Validator PASS** (`schema/validate_cases.py`) at authoring time, re-run corpus-wide at wave end.
6. **A-M8 disclosure** where the durable write is best-effort-plausible (OTel accounting especially):
   attach contract-grounding evidence or disclose the construction-bar basis in-file.
7. **License conduct at point of use:** F-corpus = replicate-by-description, ZERO lines copied, cite
   repo + survey paper, document independent re-implementation per fault; never re-push upstream
   images; change notices on fork diffs/modified manifests.
8. **mist_commit per case** at the R1 capture pin; a dated freeze §6 row records the R1 pin lineage
   (the single-study-pin rule continues via disclosed pin history, as established by the 2.75-A/S3
   §6 rows).
9. **Capture-of-record rule (T2):** where R1 re-touches an existing case's deploy, any re-capture is
   a complete new sidecar; divergence on old cells → new measurement stands + disclosed.

## §3 The C3-shape constraint on S2 (the load-bearing design point of this wave)

The S2 additions feed the C3 calibration mix, so their SHAPES are constrained (S3 P5 §2–§3, carried
here as normative):

- **Degradation-shaped only** for rating-mix membership — documented async-delay / by-design
  soft-reject / bounded eventual consistency / visible-but-benign degradation. Clean-present
  journeys never enter the rating mix (C-B3/A-F10 — stratum decodable).
- **BOTH re-probe polarities must exist in the merged benign set:** re-probe-PRESENT benigns
  (eventual-consistency class — w120 exists, add ≥1 more) AND re-probe-ABSENT benigns (soft-reject /
  by-design-drop class — TT dedupe family, TeaStore maintenance writes, OTel duplicate-dedupe) so
  presence does NOT decode the label. R1's S2 mix is selected to guarantee ≥5 of EACH polarity.
- **Cadence conformance:** rating-destined sidecars use the pinned observation cadence (baseline /
  at-cap / re-probe where applicable) so no timing tell separates strata.
- The full tell-audit runs at assembly (R2); R1's obligation is to SUPPLY the shapes and stamp each
  case's polarity in its notes.

## §4 Phases + tenancy (single box, 26 GB WSL; RAM gates verbatim from runbooks)

**Phase A — small-tenant captures (OTel-Demo + TeaStore up; Bookinfo/Boutique batched):**
A0. Pre-flight: `free` check; revive OTel-Demo + TeaStore from pinned manifests (PFs are dead
    post-reboot — re-establish; kafka-wedge runbook at hand: rollout-restart checkout+accounting+fraud
    after any kafka pod replacement).
A1. OTel captures: kafka×mesh-sever S1 pair → emptycart×method-sever VERIFY→capture-or-refute →
    S2 batch (dedupe, imageSlowLoad, ad-family verify, emailMemoryLeak, failedReadinessProbe) →
    **kafka-S1 rider** (§5-R1; LAST on this tenant — it degrades the pipeline; recovery restart after).
A2. TeaStore captures: order-items×mesh-abort S1 pair → maintenance-503 benign window S2 →
    **observe-recall rider** (§5-R2) → DB-regen wipe S2 LAST (destructive; regenerate after).
A3. Bookinfo (tiny, co-reside): 2 S2 captures; Boutique (light, co-reside or swap with TeaStore if
    RAM-tight): deploy (2.4) → S1-minor verify→capture → 2 S2 → scale to 0.
A4. SS window (swap against TeaStore if RAM requires): revive (rabbit-user + warm-up runbook) →
    carts-adjacent masked-site verify → S2 enumeration captures → scale to 0.
**Phase A exit gate:** all small-SUT quotas captured-or-refuted; artifacts committed; tenants to 0.

**Phase B — TT window (solo big tenant):**
B0. ALL graphs at 0 → **build F-corpus fork images off-peak** (never while a graph is deployed;
    change notices in fork diffs; per-fault branch on the MIST-trainticket fork lineage).
B1. Revive TT (runbook §2.6: deploy.yaml 1.0.2→1.0.0 sed; helm re-scale not re-install; nacos
    doubleWrite rule; Xenon `tsdb-mysql-0` force-recreate on quorum wedge; WSL flap = wait, small
    batches, never `wsl --shutdown`).
B2. F-corpus replication loop, per fault: deploy fork image → probe-first → **in-class verification
    gate** (masked-2xx acked-but-lost reproduced, B-m6 — fail ⇒ candidate swapped from the F1–F22
    pool, swap disclosed) → fault+control capture pair → restore base image → next. Floor 6, target
    10, budget-capped (§6).
B3. TT S2 enumeration captures (+2–4 per §1).
B4. Restore TT to the snapshot posture (scale per tenancy default); post-restore key-path smoke.

**Phase C — wave close:** corpus-wide validator run · R8 tally · README/freeze §6/FILE_INDEX/memory
sync · `RESULT-r1.md` (quota table achieved-vs-target, per-refutation record, shape-polarity census,
two-denominator S1 count) · **≥3-cold-review of the RESULT** (the wave's own gate; the benchmark
artifact's sampled-reproduction review stays at step 8).

## §5 Riders (folded, each under its own discipline)

- **R1-rider: `kafkaQueueProblems` S1 characterization** (wave-3a disposition executed): N≥20 trial
  in-window acked orders under `kafkaQueueProblems=100` + N≥10 control trials (flag off) on a healthy
  pipeline; per-order durable verdict via the psql probe at the pinned cadence + a T+5 min re-probe;
  loss-rate with Wilson CI; the past-toggle poisoning window measured (canaries at flag-off until
  first success, then recovery restart + health canary). Case authored as S1-positive,
  `fault.provenance_class=by-docs` (vendor flag), **stochastic-loss disclosed in-file** (expectation
  fields carry the measured rate, not a deterministic claim); control leg = the flag-off trials.
  This is the corpus's first stochastic positive — the in-file honesty IS the point.
- **R2-rider: TeaStore observe-mode measured-recall leg** (S3 F2b remedy): one fresh observe-mode
  MIST run against the maintenance-masked fault at the R1 pin → a measured observe-mode CONFIRMED
  (or the honest failure). Closes the S3 disclosed deviation with a measured cell; recorded in
  RESULT-r1 + a freeze §6 note amending the S3 F2b disclosure to "remedied on TeaStore".

## §6 Budget, stop rules, risks

- **Budget:** Phase A 3–5 days · Phase B 4–7 days (F-corpus verification loop dominates) · Phase C
  1–2 days. **Stop rule (F-corpus):** if after 4 working days of Phase B the in-class-verified count
  is < 6, SHIP the achieved count with the freeze shortfall disclosure rather than grinding (floor-6
  is a target with a disclosed-shortfall branch, NOT a do-not-return gate — the distinct-site floor
  ≥20 is the binding one; if THAT lands short, stop-and-replan per freeze §5).
- **Refutation branches pre-registered** (survey-correction discipline, wave-3a style): emptycart
  method-sever (OTel + Boutique) may be loud → refutation datum + quota shortfall disclosed; SS carts
  masked site may not exist → freeze recount corrected DOWN, disclosed; OTel ad-family S2 may refute
  per D3b → anti-finding recorded. **A refuted candidate is NEVER silently replaced beyond the named
  alternates in §1.**
- **Destructive-op ordering:** TeaStore DB-regen wipe and the kafka rider run LAST on their tenants;
  never `GET /rest/generatedb` as a probe; kafka rider followed by the recovery restart + health
  canary before the tenant is declared clean.
- **RAM:** `free` gate before each revival; small-batch pod restarts (WSL flap window expected,
  never `wsl --shutdown`); if a wedge costs > half a day, snapshot state and continue with the next
  phase item, returning after.
- **Risk: stochastic rider under-powers** (kafka loss-rate CI too wide at N=20) → report the CI as
  measured; the case ships with the width disclosed (a stochastic positive with an honest CI beats
  a deterministic overclaim).

## §7 Acceptance (DoD)

1. S1: quota table achieved-or-refuted per §1; **distinct-site count ≥20 OR the disclosed
   stop-and-replan branch invoked**; two denominators reported; R8 tally recorded.
2. S2: **≥35 OR the freeze §5 disclosed shortfall** with the achieved band; ≥5 re-probe-PRESENT and
   ≥5 re-probe-ABSENT benign shapes stamped.
3. Both riders executed-or-honestly-failed with artifacts.
4. Every new case: §2 obligations complete; corpus-wide validator green; replay scripts committed.
5. README/freeze §6/FILE_INDEX/memory synced; tenants restored to the declared end-state (all 0 or
   the standing default); `RESULT-r1.md` + ≥3-cold-review PASSED (all fixes folded).

## §8 Out of scope
M-yield (R2) · OpenAPI authoring + wave-runner (R2 prerequisites for M-yield/E1) · corpus-assembly
entry gate + sealing + rater hand-off (R2) · E1/E2/E5/E3/E6 (M1) · any MIST tool-code change (the
scoped gate stays as-is; R1 is capture-only against the existing pinned tool) · TT lean-traced
convergence (2.15(b), separate decision) · S3-class wild hunting (closed).
