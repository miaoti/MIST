# RESULT — Wave M-YIELD-COMPLETION (Step 4 across the remaining 5 SUTs) — RESULT OF RECORD

**Status: EXECUTED 2026-07-15 → 2026-07-16; all 5 legs complete; post-hoc 3-cold review
FOLDED 2026-07-16 (A REVISE-1B · B REVISE-3B · C ACCEPT-WITH-FIXES-2B; all 6 blocking =
text/disclosure-layer, numbers unchanged, no re-run; recon
`REVIEW-MYC-RESULT-RECONCILIATION.md` local-only) — WAVE CLOSED. Corrections are marked
in place below.** Plan: `wave-myield-completion-plan.md` rev 2.1 (rev-1 3-cold = A REJECT
6B + B REVISE 3B + C REQ-CHANGES 6B, 15 folded; confirmation pass UNANIMOUS w/ residuals
folded). Frozen budgets: §3.2 (spec-rich 1 h × 10; thin 1 h × 3; LLM-off). Wall-clock:
**~12.5 h end-to-end** vs the plan's ~3-5 d estimate (the SS 10×1h-killed leg dominated;
faster-than-planned, disclosed per the plan's own calendar-honesty rider). MIST pin: the
`main_track` tree at leg starts (runner/config-only additions during the window; per-leg
HEAD stamped in driver logs). Evidence: `debug/a-main/benchmark/b4/ttomni/myc/`
(per-seed logs + preserved allure-results/test-data/fault-reports + `CLUSTERING-myc.json`).

## Per-leg outcomes (run/paused/total = N/0/N everywhere — no PAUSED seeds)

| SUT | tier | seeds | completion | executed tests | passed/failed | fail clusters |
|---|---|---|---|---|---|---|
| TeaStore | spec-rich | 10/10 | natural (~seconds each) | 10 (1/seed) | 0/10 | 1 |
| OTel-Demo | thin | 3/3 | natural (~10 s each) | 642 (~214/seed) | 108/534 | 7 |
| SockShop | spec-rich | 10/10 | ALL killed @1 h (full budget) | 3440 (~344/seed) | 0/3440 | 7 |
| Bookinfo | thin | 3/3 | natural (~7-8 s each) | 246 | 39/207 | 4 |
| Boutique | thin | 3/3 | natural (~15 min each) | 807 | 19/788 | 7 |
| **total (this wave)** | | **29/29** | | **5145** | **166/4979** | **26** |

The TT leg (TT-omnibus, `RESULT-tt-omnibus.md`) completes the 6-SUT Step-4 set: 10 seeds,
0 flagged events. **Interpretation rail: test-level failures are PIPELINE OUTCOMES under
the run condition (faulty.ratio=0.8 makes the corpus negative-heavy BY DESIGN; a "failed"
negative test is the generator probing the SUT), NOT defect labels — genuine-vs-benign
adjudication is RATER-GATED (Step 5); NO yield statistic is reported (self-concordance
rule).** SS's 0/3440 passed has a disclosed mechanical shape: **100% of its write steps
(POST /addresses, POST /cards) returned 500 `Invalid Id Hex`** under the type-naive
LLM-off tier (disclosure 3) — the measured condition of this tier, not a SUT-defect claim
(A post-review fold; load-bearing for the finding below). Clustering per the frozen
convention: 26 clusters in `CLUSTERING-myc.json` — 1 representative each (drawn from seed
20260714) + **10% audit samples drawn ACROSS seeds** (sampling seed 20260714; relabeled
post-review — the drafted "(seed 20260714)" misread as sample provenance; e.g. teastore's
lone audit sample is s20260715, sockshop's span all 10 seeds).

## The wave's cross-SUT FINDING (REWRITTEN post-review — the drafted mechanism was WRONG): observe arms ONLY at the final round, and the ARMED stretch is structurally STARVED under 1 h budgets

**Correction of record (A-blocking):** the drafted version of this section claimed SS
seeds were "killed at the 1 h cap before [the] final round" / that "budget-capped runs
never reach it." **That was FALSE.** Every one of the 10 SS seed logs carries the
`OBSERVE mode: session 'observe-final-round' armed for 2 triple(s)` line, which the source
emits ONLY inside the final round (`MistRunner.java` L1730: `isFinalRound = round ==
enhancerRounds`; L1757-58: `maybeBeginObserve` called only under `if (isFinalRound)`) —
**SS reached AND armed the final round in 10/10 seeds.** The corrected finding:

- **Arming is final-round-only by construction** (source above) — earlier enhancer rounds
  run the same writes with observe hooks as no-ops.
- **SS (this wave): armed 10/10, yet 0 `DataIntegrity[` records**, for three compounding
  reasons: (i) **no observable durable write existed** — 100% of the 3440 generated writes
  returned 500 `Invalid Id Hex` at the type-naive tier, so the armed session had nothing
  durable to record; (ii) the 1 h kill landed **MID-final-round, before `maybeEndObserve`**
  (no observe-summary line exists in any seed log); (iii) **independent tier cap:** with
  `jaeger.enabled=false` the defect tier `OBSERVED_COMPLETE_ABSENT` requires a Jaeger
  trace-complete and was unreachable BY CONSTRUCTION — the reachable ceiling was WARN-only
  `TIMEOUT_ABSENT` (B-blocking co-reason; the TT-omnibus leg-1 measured that tier WITH
  jaeger, where it fired 5/5).
- **TT (TT-omnibus): 4/10 highest-progress seeds reached + armed; 6/10 never reached the
  final round in-budget; on the 4 armed seeds the hooked steps never covered the 2
  registry triples** — a coverage gap on ARMED seeds, a different mechanism from
  never-arming (the two are no longer conflated; C-blocking fix).
- Natural-completing thin-SUT runs have no DI triples to arm (descoped/read-only).

**Corrected characterization: across all 20 DI-configured seeds (SS 10 + TT 10) the
observe oracle produced 0 DI records — NOT because the final round is unreachable (SS
reached it 10/10, TT 4/10), but because the armed final-round stretch is structurally
STARVED under 1 h budgets: it arrives too late/short for hooked-step coverage of the
registry triples (TT), and it has nothing durable to observe when the generator's writes
fail at the minimal-enablement tier (SS all-500) — with SS's defect tier additionally
capped by this wave's jaeger-off condition. A real tool-behavior datum for the paper's
M-yield section, NOT a defect claim.** The SS binding-smoke record is restated in
disclosure 4 as what it was: a stop-rule deviation, disclosed.

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
   webui PF, 200 via the persistence PF *(post-review hedge: that probe was an ad hoc curl
   during Phase-0 authoring, never piped to a file — artifact-thin; the scoping stands on
   architectural grounds [multi-host topology vs MIST's single `base.url`] + the smoke
   logs' probe pattern)*.
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
   tool code — five source-verified reasons); Bookinfo read-only; Boutique no committed
   triple. **SS DI = a DISCLOSED STOP-RULE DEVIATION (rewritten post-review, B-blocking;
   the drafted "armed-verified … closes as NO-DI-datum" framing is RETRACTED):** the
   binding smoke did NOT pass its hardened criterion — the oracle armed but NO hooked-step
   / `DataIntegrity[` record appeared in 5 min (the plan's "hooks ≥1 generated step, else
   STOP" + the A'-residual assertion) = the else-STOP branch; the pinned action was "run
   no-DI + disclose"; the leg instead ran all 10 seeds `dataintegrity.enabled=true` with
   the CLAIM withheld. Harmless in outcome (0 records materialized; the tier was
   jaeger-off-capped regardless — see the finding), but a deviation of record, not a clean
   closure. Corroborating probe datum (post-review add): the driver's per-seed collection
   probe logged **bytes=1132 IDENTICALLY across all 10 seeds** — no `readback_bound`
   saturation occurred (closes the C'-residual watch with data).
5. **Bookinfo namespace finding:** no `bookinfo` ns exists — the deployments live in
   `default` (the revival script's assumption corrected in-flight; scale-up + reviews-v3 VS
   verified `v3`).
6. **Boutique deploy:** the classifier correctly blocked a self-fetched-manifest shortcut;
   the deploy ran the PLAN-PINNED `deploy.sh` VERBATIM (istio-injection label + upstream
   manifest + waits), then `loadgenerator → 0` (the pre-checked uncontrolled-load rail);
   11 Running; smoke 200. **Checklist 2.4 folds ◐ max** (deploy+smoke DONE; the row's
   Istio gRPC abort-rider live check remains open, per the confirmation-pass C'-residual).
7. **Uniform override block** per seed (LLM-off, jaeger.enabled=false, base.url, seed,
   experiment.name). **CORRECTED post-review (B-blocking) — the drafted claim that "the
   shipped profiles' trace-shape oracle keys were verified INACTIVE (commented)" was
   FALSE:** `mst.oracle.shape.invariants.hidden_downstream_failure.enabled=true` is LIVE
   (uncommented) in the per-seed props (sockshop L152 / bookinfo L482 / boutique L157);
   the drafted claim had misread the OTHER five commented example keys (bookinfo
   L466-470). The override block disables NO oracle keys, so the plan-§2 "per-SUT
   trace-dependent oracle keys=off, enumerated at Phase 0" step was **NOT implemented — a
   plan-fidelity deviation, disclosed.** Effect on the run: none, SOURCE-VERIFIED — that
   key's only consumer is `TraceShapeOracle` (`MstConfig` L433 → `TraceShapeOracle` L49),
   which has NO production call site in the MistMain pipeline (tests + a trainticket
   evaluation live-check only; the same unwired status the TT-omnibus freeze row records
   as the mist_trace_shape DEFER), and jaeger was off besides — inert by construction,
   not by the drafted "commented" fiction.
8. **Evidence preservation:** 29 seed evidence dirs banked; the TT-leg Allure loss class
   is fixed (copy-out before each wipe). **Two caveats ADDED post-review — the drafted
   blanket "copy-out worked for EVERY seed" was inaccurate for TeaStore: (a) TeaStore
   contamination (C-blocking):** all 10 teastore seed dirs carry a stale, byte-identical
   smoke-3 result JSON at the TOP level (the crashed driver attempt 1 — see
   `driver-attempt1.log` — copied a stale `target/allure-results`; the fixed attempt 2's
   `Copy-Item -Recurse` then NESTED the real copy), so the GENUINE per-seed results live
   at `allure-results/allure-results/` — one level deeper than every other SUT.
   `CLUSTERING-myc.json` and the table above reference ONLY the genuine nested files
   (verified independently by two reviewers). Nothing deleted (standing rule);
   `README-EVIDENCE-NOTE.md` in the teastore evidence dir marks the stale artifact.
   **(b) mist.log NOT preserved (B-N1):** log4j's rollover rename fails on Windows
   (`Illegal char <:>` in the timestamped name) and the copy-out never included mist.log —
   the "0 `DataIntegrity[` records" claims rest on the PRESERVED record (per-seed
   stdout/stderr logs + allure attachments + driver logs), where independent reviewer
   greps found 0 hits; the mist.log stream itself is gone.
9. Ops: one Docker Desktop outage mid-window (engine restarted; kubectl symlink returned
   with it); PS 5.1 non-ASCII/here-string parse traps → the driver is ASCII+CRLF; per-seed
   props must live BESIDE the source profile (relative-path resolution). Post-review add:
   the driver's `EXIT rc=` capture is empty for natural exits (a Start-Process rc race) —
   natural completion is inferred from process exit + banked evidence; SS's KILLED
   entries are explicit.

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
