# RESULT — Wave M-YIELD-COMPLETION (Step 4 across the remaining 5 SUTs) — RESULT OF RECORD

**Status: EXECUTED 2026-07-15 → 2026-07-16; all 5 legs complete; DoD gate = post-hoc 3-cold
review (pending).** Plan: `wave-myield-completion-plan.md` rev 2.1 (rev-1 3-cold = A REJECT
6B + B REVISE 3B + C REQ-CHANGES 6B, 15 folded; confirmation pass UNANIMOUS w/ residuals
folded). Frozen budgets: §3.2 (spec-rich 1 h × 10; thin 1 h × 3; LLM-off). MIST pin: the
`main_track` tree at leg starts (runner/config-only additions during the window; per-leg
HEAD stamped in driver logs). Evidence: `debug/a-main/benchmark/b4/ttomni/myc/`
(per-seed logs + preserved allure-results/test-data/fault-reports + `CLUSTERING-myc.json`).

## Per-leg outcomes (run/paused/total = N/0/N everywhere — no PAUSED seeds)

| SUT | tier | seeds | completion | executed tests | passed/failed | fail clusters |
|---|---|---|---|---|---|---|
| TeaStore | spec-rich | 10/10 | natural (~seconds each) | 10 (1/seed) | 0/10 | 1 |
| OTel-Demo | thin | 3/3 | natural (~10 s each) | 642 (~214/seed) | 108/534 | 7 |
| SockShop | spec-rich | 10/10 | ALL killed @1 h (full budget) | 3440 (~344/seed) | 0/3440 | 7 |
| Bookinfo | thin | 3/3 | natural (fast) | 246 | 39/207 | 4 |
| Boutique | thin | 3/3 | natural (fast) | 807 | 19/788 | 7 |
| **total (this wave)** | | **29/29** | | **5145** | **166/4979** | **26** |

The TT leg (TT-omnibus, `RESULT-tt-omnibus.md`) completes the 6-SUT Step-4 set: 10 seeds,
0 flagged events. **Interpretation rail: test-level failures are PIPELINE OUTCOMES under
the run condition (faulty.ratio=0.8 makes the corpus negative-heavy BY DESIGN; a "failed"
negative test is the generator probing the SUT), NOT defect labels — genuine-vs-benign
adjudication is RATER-GATED (Step 5); NO yield statistic is reported (self-concordance
rule).** Clustering per the frozen convention: 26 clusters, 1 representative each + 10%
audit samples (seed 20260714) in `CLUSTERING-myc.json`.

## The wave's cross-SUT FINDING: observe arms at the FINAL ROUND; budget-capped runs never reach it

The pipeline's data-integrity OBSERVE stretch is armed in the enhancer FINAL ROUND. Across
BOTH DI-armed legs: SS (this wave) — oracle armed (2 fresh-strings triples) on every seed,
**0 `DataIntegrity[` records in 10/10 seeds** because every seed was killed at the 1 h cap
before its final round; TT (TT-omnibus) — armed on the 4 highest-progress seeds, hooked
steps never covered its 2 registry triples in-budget. Natural-completing runs on the thin
SUTs have no DI triples to arm (descoped/read-only). **Characterization: under M-yield's
pinned 1 h budgets, the pipeline's observe oracle is structurally unlikely to produce DI
records on spec-rich SUTs (the final round lies beyond the budget) — a real
tool-behavior datum for the paper's M-yield section, NOT a defect claim.** The SS
binding-smoke record: armed-verified; coverage undetermined at 5 min (same final-round
reason); the DI claim was evidence-gated and closes as NO-DI-datum, disclosed.

## Enablement record (`mist_authoring`; the B'-R4 obligation)

| SUT | authored | tier | minutes |
|---|---|---|---|
| TeaStore | `real-system-conf.yaml` (via `MistConfGenMain` from the E1 spec) + `teastore-myc.properties` + 1 synthetic seed trace | generated-from-spec + authored-input | ~25 (incl. the two-host re-scope + smokes) |
| OTel-Demo | conf (same generator) + `oteldemo-myc.properties` + 4 REAL seed traces captured from its own jaeger | generated-from-spec + captured-input | ~15 |
| SockShop | `sockshop-myc.properties` (= shipped profile + DI flag) | shipped | ~5 |
| Bookinfo | none (shipped profile) | shipped | 0 |
| Boutique | none (shipped profile; deploy via the committed `deploy.sh` verbatim) | shipped | 0 |

## Disclosures (all pinned in-flight, dated in the plan/commits)

1. **TeaStore two-host finding:** no gateway; MIST's conf carries ONE global `base.url` →
   the leg ran the PERSISTENCE service surface only (PF 8083; 6 ops incl. the POST
   /rest/orders write); the 3 webui form ops EXCLUDED (their masked-write coverage lives in
   the corpus 2.75-A harness bindings). Verified empirically: the same path = 404 via the
   webui PF, 200 via the persistence PF.
2. **Seed-trace requirement (dated plan amendment):** MST generation REQUIRES Jaeger-JSON
   seeds (no spec-only branch). OTel = 4 REAL traces captured at leg start (6-15 spans);
   TeaStore (Kieker-only) = 1 AUTHORED synthetic workflow seed (a generation INPUT, never
   evidence) — its tiny scenario pool explains the seconds-scale natural completions and
   the 1-test-per-seed outcome.
3. **Minimal-enablement tier:** TeaStore/OTel run WITHOUT smart-fetch registries (TT/SS
   ship mature learned registries); under LLM-off the fallback generates type-naive values
   ("test919" for int params) → positive-flow 4xx on TeaStore = the measured condition of
   this tier, disclosed (NOT SUT defects).
4. **TeaStore/OTel DI descoped** (plan rev 2: pipeline triples dead-by-construction without
   tool code — five source-verified reasons); SS DI evidence-gated → closed NO-datum (the
   finding above); Bookinfo read-only; Boutique no committed triple.
5. **Bookinfo namespace finding:** no `bookinfo` ns exists — the deployments live in
   `default` (the revival script's assumption corrected in-flight; scale-up + reviews-v3 VS
   verified `v3`).
6. **Boutique deploy:** the classifier correctly blocked a self-fetched-manifest shortcut;
   the deploy ran the PLAN-PINNED `deploy.sh` VERBATIM (istio-injection label + upstream
   manifest + waits), then `loadgenerator → 0` (the pre-checked uncontrolled-load rail);
   11 Running; smoke 200. **Checklist 2.4 folds ◐ max** (deploy+smoke DONE; the row's
   Istio gRPC abort-rider live check remains open, per the confirmation-pass C'-residual).
7. **Uniform override block** per seed (LLM-off, jaeger.enabled=false, base.url, seed,
   experiment.name); the shipped profiles' trace-shape oracle keys were verified INACTIVE
   (commented) — no oracle was disabled that had been active.
8. **Evidence preservation:** per-seed copy-out worked for EVERY seed (the TT-leg Allure
   loss class is fixed); 29 seed evidence dirs banked.
9. Ops: one Docker Desktop outage mid-window (engine restarted; kubectl symlink returned
   with it); PS 5.1 non-ASCII/here-string parse traps → the driver is ASCII+CRLF; per-seed
   props must live BESIDE the source profile (relative-path resolution).

## Branch-determined folds (pinned pre-run)

- **Step 4 → ✔**: all 5 legs ran at the pinned budgets with outcomes recorded; 0 PAUSED
  seeds; the TT leg completes the 6-SUT set. (✔ = "ran at pinned budgets", per the
  A'-confirmed honest reading — DI validation was never the criterion.)
- **2.4 → ◐** (deploy+smoke done; abort-rider open).
- E3 note: trigger-rate mining from these logs (checklist Step 8) is now UNBLOCKED.

## End state

All tenants at 0 (boutique scaled-0, ns preserved; the lone otel-collector-agent DaemonSet
pod is the known negligible residual). Cluster idle. The M-yield experiment surface —
the LAST substantive pre-draft experiment — is COMPLETE.
