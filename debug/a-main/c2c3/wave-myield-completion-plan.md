# Wave M-YIELD-COMPLETION — finish Step 4 across the remaining 5 SUTs — rev 2 (post-3-cold-review fold; CONFIRMATION PASS pending)

**Date:** 2026-07-15 · Owner: main_track · Status: **rev 2 — rev-1 3-cold review = A REJECT (6
BLOCKING) · B REVISE (3 BLOCKING) · C REQUEST-CHANGES (6 BLOCKING); ALL 15 folded per
`REVIEW-MYC-PLAN-RECONCILIATION.md`; execution gated on a CONFIRMATION PASS (unanimous).**
User direction (2026-07-15): finish ALL experiments before drafting.

**rev-2 headline changes:** (1) **NO pipeline DI triples for TeaStore/OTel — dead-by-construction
without tool code** (A, five source-verified reasons — the sixth blocking item was the
missing smoke, a remedy, folded below: the writer emits `beforeWriteSupplied` only
for path-segment keys so form/body-keyed supplied triples hard-error in `beforeWrite`; OTel's
`{userId,items[]}` read-back shape is outside `extractItems`; TeaStore's form-urlencoded write
breaks `freshen()`'s JSON assumption; a query-string `write_endpoint` never matches
`step.getPath()`; cookie/form auth is NOT config-only — `postLogin` hardcodes JSON). They run
in the **same condition as Bookinfo** (pipeline generation + status/response oracles; DI = none),
DISCLOSED: their masked-write read-back coverage lives in the corpus-level 2.75-A harness
bindings (banked; `b4/enable/*HeadToHead`), which do NOT transfer to the observe pipeline.
The rev-1 "the pipeline triple binds the cart write" clause is DELETED. (2) **Single-tenant
sequential legs** (B: co-fit was proven only IDLE; under-load co-residency is the wedge regime)
— each SUT runs ALONE. (3) **Mandatory pre-run smokes**: per-SUT 5-min pipeline smoke +
for SS a BINDING smoke proving the shipping triple hooks ≥1 generated step (the
armed-but-uncovered lesson; hardened with a mist.log `DataIntegrity[` record assertion — A'-residual). (4) **Uniform oracle-condition override block** across all 5
SUTs (C: the G-era per-SUT profiles carry hidden-downstream/jaeger oracles that would
error with no jaeger deployed): LLM-off + `jaeger.enabled=false` + trace-dependent oracles
OFF + base.url + seed + experiment.name — the SAME disclosed condition as the TT leg.
(5) Calendar honesty: **~3-5 days** end-to-end. (6) Branch-determined folds + PAUSED-row
accounting + the generatedb conf grep-gate + a committed Window-B revival script + the
tracked-config pre-run gate + ONE wave-wide MIST pin.

## §1 Scope — the five legs (frozen §3.2 budgets, unchanged)

| SUT | tier | budget | DI condition | enablement work |
|---|---|---|---|---|
| TeaStore | spec-rich | 1 h × 10 | **none (descoped — A)**; disclosed | AUTHOR `real-system-conf.yaml` only (no auth glue: unauthenticated coverage — webui browse + persistence REST GETs execute; authed actions 302/fail = recorded outcomes, DISCLOSED) |
| SockShop | spec-rich | 1 h × 10 | shipping triple (EXISTS, tracked) + **binding smoke** | commit the 4 currently-UNTRACKED SS registry/trace files first (§4-3 gate) |
| OTel-Demo | thin | 1 h × 3 | **none (descoped — A)**; disclosed | AUTHOR `real-system-conf.yaml` only (no auth needed) |
| Bookinfo | thin | 1 h × 3 | none (read-only SUT) | conf EXISTS; revival = scale-up + reviews-v3 VS state check |
| Boutique | thin | 1 h × 3 | none (no committed triple) | DEPLOY (2.4; `deploy.sh` pre-checked against the CURRENT cluster shape — istio-era assumptions verified/adjusted BEFORE run) |

Pipeline spec inputs for the 2 authored confs = the E1 specs (their first pipeline
consumption; TeaStore's spec structurally excludes `/rest/generatedb`). `mist_authoring`
cost (tier/minutes) recorded for the 2 conf authorings. LLM-off; seeds `20260714+i`;
**ONE wave-wide MIST pin** stamped per-run (B).

**Note (B):** tracked `evaluation/suts/{teastore,oteldemo}` triples files DO exist (swept
into `bafc894`) — they are HARNESS-era artifacts (2.75-A custom-runner bindings), NOT
pipeline-usable (per A's five reasons); the enablement table above reflects that reality.

## §2 Uniform run condition (the override block; C)

Every seed of every SUT = the SUT's shipped demo profile + this APPENDED override block
(Properties last-key-wins; the TT-leg discipline):
`llm.enabled=false · base.url=<PF> · random.seed=<seed> · experiment.name=myc_<sut>_s<seed>
· jaeger.enabled=false · <trace-dependent oracle keys>=off (per-SUT enumerated at Phase 0
from each profile — hidden-downstream/trace-shape oracles CANNOT run with no jaeger
deployed; disabling = environment-matching, DISCLOSED as the uniform condition; NOTE the TT leg's
seed profiles kept jaeger.enabled=true — the comparison is NOT claimed; this disposition
stands on its own: no jaeger deployed ⇒ trace-dependent oracles off — C'-residual)`. The exact per-SUT key list is frozen
at Phase 0 and committed with the driver.

## §3 Phases (single-tenant sequential; ~3-5 days)

- **Phase 0 (no-tenant prep):** (a) author the 2 confs (+ record authoring minutes);
  (b) freeze the per-SUT override-block key lists; (c) commit the 4 untracked SS files +
  any other load-bearing untracked config (`git status` gate over `evaluation/suts/**` —
  the tracked-config lesson); (d) the **generatedb grep-gate** (committed script: grep the
  TeaStore conf + input-fetch + spec for `generatedb` — must be 0 hits) wired into the
  driver before any TeaStore seed; (e) extend the myield driver: per-seed evidence copy-out
  (allure-results + fault report + test-data → `b4/ttomni/myc/<sut>/s<seed>/` BEFORE the
  next seed), health probe between seeds (home/login 200 **+ a RAM check — available < 3 Gi ⇒ PAUSE**,
  the B'-residual: the TT-leg wedge was single-tenant sustained-load, so single-tenancy
  alone does not remove the exhaustion mechanism; on failure PAUSE the SUT's
  remaining seeds — **paused seeds appear in the outcome table as PAUSED rows with the
  probe evidence; denominators report run/paused/total** (B)); (f) the Window-B revival
  script (SS: scale-up + re-create the `mist:mist` RabbitMQ user + warm-up POST +
  catalogue/catalogue-db startup-race handling; Bookinfo: scale-up + reviews-v3 VS check;
  committed like revive-stage.sh) (C).
- **Phase 1-5 (one leg per phase, SUT up ALONE, sequential):**
  TeaStore (smoke → 10 seeds ≈ 10 h) → OTel (smoke → 3 seeds) → SS (revive → **binding
  smoke: the shipping triple hooks ≥1 generated step in a 5-min bounded run, else STOP —
  the SS leg's DI claim depends on it** → 10 seeds) → Bookinfo (revive → smoke → 3 seeds)
  → Boutique (deploy 2.4 + home-200 smoke → 3 seeds). Each phase: tenant up → RAM
  checkpoint → smoke → seeds (detached Windows driver) → evidence verify → tenant to 0.
  Swap cost (B'-residual): 5 up/down cycles ≈ 10-20 min each on these light SUTs (4-21
  pods; nothing TT-scale) — ~1.5 h total, inside the 3-5 d calendar.
- **Phase 6 (close-out):** clustering over ALL flagged events across 6 SUTs (frozen
  convention; the TT leg's 0 included); per-SUT outcome tables (run/paused/total);
  `RESULT-myield-completion.md` + freeze EXECUTED row + **branch-determined folds (C):**
  Step 4 → **✔ iff all 5 legs ran at pinned budgets with outcomes recorded (paused seeds
  disclosed); else ◐ with the blocker named**; 2.4 → **◐ max from this wave** (deploy+smoke earns
  deploy-DONE; the row's Istio gRPC abort-rider live check is NOT performed here and
  stays open — C'-residual; ☐ with the failure recorded if deploy fails); FILE_INDEX/memory; post-hoc 3-cold review.

## §4 Known risks, pinned dispositions

- **SS `readback_bound: 500` vs a 10 h cumulative global collection (C):** the membership
  read-back may saturate the bound late in the campaign — DISCLOSED per-seed (Phase 0(e) BUILDS a driver probe — a bounded authenticated GET of
  the orders read-back collection, counting items, logged before each seed; the runtime's
  own readback_bound check only logs the threshold reactively — C'-residual); no bound-raising (that would be
  oracle-condition tuning); if saturation occurs, affected seeds are annotated (the DI
  no-op is an environment artifact, not a SUT verdict).
- **TeaStore unauthenticated coverage** limits the write-path fraction — DISCLOSED (the
  write-path-fraction reporting obligation already exists in §3.2's M-prevalence framing;
  M-yield reports what executed).
- Boutique deploy risk to other tenants: none co-resident (single-tenant phases); deploy
  pre-check pins istio-era assumptions before running.
- The wedge rails (unchanged): RAM checkpoints, lean race-scale on wedge, never
  `wsl --shutdown` on a healthy cluster, no probe-hammering, per-seed evidence out of
  wipe-able dirs.

## §5 DoD + stop rules

1. 5 legs at pinned budgets; per-seed evidence preserved for EVERY seed; outcome tables
   with run/paused/total; smokes + binding-smoke logs committed.
2. Clustering + representatives + audit sample (vacuous if 0 flags); NO yield statistic
   (rater-gated); upstream filings for genuine finds.
3. Branch-determined folds (§3 Phase 6); RESULT + freeze row + 3-cold review;
   FILE_INDEX/memory.
- **Stop rules:** ANY tool-code need for enablement ⇒ the SUT runs in the descoped
  (no-DI) condition + disclose (never code under this wave); SS binding smoke fails ⇒
  STOP the SS DI claim (run no-DI + disclose); per-leg budget >1.5× ⇒ pause+disclose;
  wedge beyond runbook ⇒ STOP+surface; generatedb gate ≠ 0 hits ⇒ STOP.

## §6 NOT in scope (unchanged)

kafka S1; the contract-invariant spike/run; E1 grid; TraceAnomaly; the rater SEAL + IRB;
S3-BENIGN-01 re-cut; E6 (fork-pub = USER); the paper draft (NEXT after this window).

## §7 Confirmation pass (the execution gate)

3 cold re-reads of THIS rev 2; execution only on unanimous confirm. Brief: verify the 15
blocking dispositions landed (A1-6 = the descope + smokes + no-code honesty; B1-3 =
single-tenant + calendar + PAUSED accounting; C1-6 = branch-folds + SS bound + revival
script + override discipline + generatedb gate), and that no new over-claim was introduced.
