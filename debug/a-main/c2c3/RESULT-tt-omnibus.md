# RESULT — Wave TT-OMNIBUS (traced MIST live-run + M-yield TT leg + E5 ablations) — RESULT OF RECORD

**Status: EXECUTED 2026-07-14 → 2026-07-15; all three legs banked; Phase-3 close-out done;
DoD gate = post-hoc 3-cold review (below, pending).** Plan: `wave-tt-omnibus-plan.md` rev 2.1
(rev-1 3-cold review 11-BLOCKING folded; confirmation pass UNANIMOUS). Protocols:
`ttomni-phase0-protocol.md`. MIST pin: the `main_track` tree at `1c9e4df` (runner-only
additions during the window; runtime/oracle untouched — verifiable from the window's diffs).
Evidence commits: `be7bdb9` `1c9e4df` (leg 1) · `bafc894` (leg 2) · `347edd1` (leg 3 +
recovery) · `128adc0` `04834a8` (drivers).

## Headline (leg 1) — the first CONTROLLED, pre-registered observe-mode measurement of the trace-gated tier on the cancel-refund masked-loss site (c2c3 benchmark record)

**[Scope corrected post-review, A-BLOCKING:]** the tier itself is NOT new behavior — the
trace-gated `OBSERVED_COMPLETE_ABSENT` branch has existed since commit `696a2fe`
(2026-07-01) and fired 126× in the G1-era `adminroute-create` pairing run of 2026-07-02
(mist.log). What IS new: no run in the **c2c3 benchmark record** had measured it, and no
prior run anywhere was a CONTROLLED either-way pre-registered measurement on the
cancel-refund masked-loss site with a clean paired control — that is this leg's
contribution (the rev-1 "first time in any run of record" wording was a false universal;
the confirmation pass had scoped its precedent check to the c2c3 waves and missed the
G-gate era).

On the revived full-graph TT (53 pods) with the cancel path instrumented (OTel javaagent
1.33.6, the traced-capture recipe verbatim) and the fork's fabricated-ack toggled at runtime:

- **Observe mode + `jaeger.base.url` + client-traceparent** (`TtOmniObserveLeg` — the ONE
  wiring change: the REAL trace-id goes to `afterWrite`, where the G3 harness passes null BY
  DESIGN):
  | leg | acked | read-back | gate |
  |---|---|---|---|
  | control ×5 | 5/5 200/{1} | refund PRESENT (value-delta moved) | `OBSERVED_PRESENT` 5/5 @1 poll |
  | fault ×5 | 5/5 200/{1,"Success."} | ABSENT at the 20-21-poll cap | **`OBSERVED_COMPLETE_ABSENT` 5/5** |
  The pre-registered either-way measurement came out **FIRES**; the control stayed clean.
  Trace-shape datum: control traces 31 spans vs fault 24 (the missing spans = the
  never-executed persistence work).
- **Paired rerun (e2-run.sh matrix verbatim):** MIST differential value-delta **FIRE 5/5**
  vs the frozen response-contract comparator **MISS 5/5** — the E2 table's MIST column now
  carries live-tool provenance on the traced deploy. (Paired verdicts are
  trace-gate-independent BY SOURCE; the mist.log cross-check shows exactly that: the paired
  fault legs record `TIMEOUT_ABSENT` while FIRING on the differential.)

Evidence: `debug/a-main/benchmark/b4/ttomni/leg1/` (per-run Jaeger exports under the
immediate-export rule — exported BEFORE the overnight events wiped Jaeger's emptyDir badger;
paired logs; `LEG1-REPORT.md`).

## The claim-language CORRECTION (deliverable — freeze/memory wording)

The standing "MIST's DISCRIMINATION claim remains PRE-REGISTERED + UNMEASURED … OWED at
2.5/E2" was an OVER-CLAIMED framing (plan-review A-F2), corrected to:
1. The **natural-discriminator** question was S3's and CLOSED (0/1514 scarcity); no
   synthetic-fork site can produce a *natural* discrimination headline, and none is claimed.
2. E2's real obligation = **matched-recall MIST-vs-comparators on the same cases**:
   comparator cells banked at the traced-capture wave (`cd275c9`); THIS wave adds the MIST
   column with live-run provenance (both modes).
3. The wave's NEW datum = the **observe-mode trace-gated tier reachability measurement**
   (fires on the fabricated-ack loss; control clean) — upgrading the R1d characterization:
   observe mode is WARN-only *when trace evidence is absent*; with trace completeness
   established it escalates to the defect tier as designed. E5's A2 axis (below) shows the
   same contrast 5/5.
4. `mist_trace_shape_oracle` cells remain **traced-but-not-run (Branch-B, DEFERRED)** — a
   feasibility choice (the learned `TraceShapeOracle` needs training/wiring); the 2026-07-10
   gate amendment would permit the work.

## Leg 2 — M-yield TT (Step 4, ◐ PARTIAL by design)

Fork TORN DOWN first (image 1.0.5 → 1.0.0; fork-absence evidence: the faultmode route
flipped 200 → 403). Detached driver: 10 seeds {20260714..20260723} × 1 h wall budget, FULL
MIST pipeline (`MistMain`, demo profile + appended overrides: **LLM-off**, base.url =
self-healing PF 8080, **canonical `target-triples.yaml` registry** — 2 shipped triples,
confirmed armed), per-seed `experiment.name`.

**Per-seed outcomes (from the banked logs; `bafc894`):**

| seed | outcome | last execution progress |
|---|---|---|
| 20260714 | killed @1 h | 1440/2550 |
| 20260715 | killed @1 h | 19/26 (early batch) |
| 20260716 | killed @1 h | 18/26 (early batch) |
| 20260717 | killed @1 h | 1702/2551 |
| 20260718 | killed @1 h | 1265/2550 |
| 20260719 | killed @1 h | 7/26 (early batch) |
| 20260720 | **completed naturally (49 min)** | **2707/2707** |
| 20260721 | killed @1 h | 2/3 (degraded SUT) |
| 20260722 | killed @1 h | 2/3 (degraded SUT) |
| 20260723 | killed @1 h | 2/3 (degraded SUT) |

**Flagged events:** data-integrity observe = **0** across all seeds (the pipeline armed
observe in the enhancer final-round stretch on 4 seeds — `observe-final-round` sessions in
mist.log — but the hooked steps never covered the 2 registry triples' endpoints within the
budget); injected-fault detection (the completed seed's report) = **0/10** with 2707 tests
(consistent with the known G1-era state of that mutation corpus); test-level per-case
outcomes for killed seeds were LOST to per-seed Allure wipes (`deletepreviousresults=true` —
an evidence-preservation defect of the driver profile, disclosed; the per-seed stdout logs +
the one complete fault report are the durable record). **Clustering:** with 0 flagged events
there are no clusters to represent and no audit sample to draw (the convention was frozen
pre-run; vacuously satisfied). **NO yield statistic is reported** (rater-gated, Step 5).
The zero is CONSISTENT WITH the S3 0/1514 prior AND is coverage-limited (the hooked registry triples saw no in-budget coverage) — reported as both, not as a confirmation [B].

**Seeds 8-10 degradation — ATTRIBUTED, not fully root-caused [re-hedged post-review,
B-BLOCKING]:** SUT health FLUCTUATED across the window (seeds 16 and 20 also hit 0/39
preflight and recovered; seed-21 was ALREADY degraded — 12/39 preflight — at its 00:23
start). Both nacos pods were OBSERVED restarted the next morning (live kubectl at ~08:40
showed restarts ~01:17 / ~05:16 — a live observation NOT corroborated by any on-disk log,
so it is an attribution, not a proof), and post-restart 1.X-doubleWrite registration
refusals ARE consistent with the E5 rep-1 all-503 state at 03:30; but the 01:17 restart
cannot explain seed-21's degradation which PRECEDED it. Honest summary: environment-side
degradation of uncertain onset (on-list operator-hosting class, freeze row 306(10)),
disclosed rather than excluded since M-yield reports run outcomes, not flag rates; the
0-flagged-events headline is unaffected (degraded seeds contributed 0 flags regardless).

**Step 4 folds ◐ PARTIAL**: the named M-YIELD-COMPLETION follow-up window owns TeaStore/OTel
2.75 enablement (+ `mist_authoring` cost recording) + the SS/BI/Boutique thin legs.

## Leg 3 — E5 ablations (exact-4 OAT × 5 reps) — COMPLETE, uniform

Fork re-applied (set-image 1.0.5). Runner: `e5-rep.sh` (attached per-rep after the detached
driver wedged pre-reboot). **All 5 reps identical:**

| config | axis | control | fault | delta vs C0 |
|---|---|---|---|---|
| C0 paired/default | — | (differential) | **FIRE 5/5** | baseline |
| C3 paired/timeout.ms=20000 | A3 (2× cap) | (differential) | **FIRE 5/5** | none — the loss is permanent; cap size irrelevant |
| C1 observe + jaeger | A1+A2 | `OBSERVED_PRESENT` 5/5 | **`OBSERVED_COMPLETE_ABSENT` 5/5** | detect preserved; tier = defect |
| C2 observe − jaeger | A2 off | `OBSERVED_PRESENT` 5/5 | **`TIMEOUT_ABSENT` 5/5** | the ONLY verdict-tier delta: same loss drops to WARN-only |

**A2 (the trace gate) is the single axis that moves the verdict tier** — the cleanest
ablation contrast, 5/5 consistent, directly quantifying what trace-completeness evidence
buys the observe oracle. Evidence: `b4/ttomni/leg3/` (30 rep run logs + the pre-reboot failed rep-1 attempt logs +
the wedged driver's log). Terminology: the protocol's "5 seeds" realizes as 5 REPETITIONS
(the g3 runners take no seed input) [C]. Note: the E5 C1/C2 mist.log sessions reuse the
`ttomni-leg1-observe-*` label (the runner's fixed prefix) — disambiguate by timestamp [A].

## Operational incidents (recorded; all recovered — no MEASUREMENT-evidence loss; the
killed seeds' per-test Allure outcomes were lost as disclosed in Leg 2)

1. **Overnight double nacos restart** (01:17/05:16) → 1.X doubleWrite re-enabled →
   registration refusals → M-yield seeds 8-10 degraded + E5 rep-1 all-503. Fix: joint
   restart + doubleWrite=false.
2. **Host RAM exhaustion** (25/25 Gi; 46 idle-but-inflated JVMs after 10 h of load + the
   double nacos boot) → K8s API dead → WSL flap (0x8007274c) persisted = beyond the
   runbook's transient-window remedy → **host reboot resolved it** (the standing
   "never `wsl --shutdown`" rail protects a HEALTHY cluster; the wedge state was terminal).
   Post-reboot: `post-reboot-lean.sh` **race-scaled 32 non-E5 services to 0 before the java
   boot stampede** (RAM held at 8.4 Gi), doubleWrite=false applied **via `kubectl exec`**
   (the PF channel to nacos was broken while in-pod readiness was 200 — a kind portforward
   netns fault), crash-loop sweep, login 200.
3. **The g3/ triples files MOVED, not deleted [narrative corrected post-review,
   C-BLOCKING]:** `target-triples-{constructed,natural}.yaml` had been TRACKED at
   `evaluation/suts/trainticket/g3/` since Jul-3. At ~23:37 Jul-14 they were MOVED on disk
   to `evaluation/suts/trainticket/triples/` — git records the move as a clean `R100`
   rename inside commit `bafc894` (that commit swept the then-pending index state; the
   mover is UNKNOWN — the rev-1 "untracked duplicates deleted, suspect
   `deletepreviousresults`" story is RETRACTED on both counts: they were tracked, and no
   plausible pipeline code path targets that directory). The practical breakage was that
   `e5-rep.sh`/`e2-run.sh` hardcode the g3/ path, which the move vacated. Copies were
   restored (content verified identical) and committed at g3/, so BOTH paths are now
   tracked; the path duplication is noted for a future tidy.
4. Jaeger's badger emptyDir wiped by the reboot — harmless: leg-1 exports were taken under
   the immediate-export rule and committed before the wedge.

## Phase 3 — close-out (DONE)

Instrumentation restored on all 4 services (`instrument-leg1.sh restore`; JTO-carrier scan =
0 residual). Teardown: replica snapshot → all ts-* to 0 → infra to 0 (`KEEP_INFRA=0`; PVCs +
helm releases persist; the resting deployment spec keeps inside-payment at image 1.0.5 —
same as the pre-wave resting snapshot). End-state: trainticket 0 pods; otel-demo/teastore
already 0 (Phase 1); cluster idle.

## Budgets vs actuals (§5; the >1.5× rule) — actuals are wall-clock approximations
reconstructed from logs/commit timestamps [C]

| leg | baseline | actual | verdict |
|---|---|---|---|
| Phase 0 prep | ≤1 d | ~2 h | ✅ |
| Phase 1 close-out | ≤0.5 d | ~25 min | ✅ |
| Phase 2(i) revival | ≤0.5 d | ~70 min | ✅ |
| Phase 2(ii) leg 1 | ≤1 d | ~2.5 h | ✅ |
| Phase 2(iii) M-yield | 10 h driven + ≤0.5 d clustering | 10 h + ~1 h analysis | ✅ |
| Phase 2(iv) E5 | ≤4 d | ~50 min of runs (+ the overnight-wedge recovery ~2 h) | ✅ |
| Phase 3 close-out | ≤1 d | ~40 min ops (+ RESULT/review) | ✅ |

## Checklist folds

Step 4 (M-yield) → **◐ PARTIAL** (TT leg banked; the M-YIELD-COMPLETION window named).
2.5.1 (TT javaagents) → stays **◐** (re-exercised end-to-end this wave; instrumentation torn
down at close-out per the pilot framing; the runbook + runner are committed). 2.5.4 → no
row change needed (the deploy matched the traced-capture recipe; no new coverage class).
Step 7 (E5) → **✔ at the pinned one-SUT-pair scope**. The stale Standing-constraints footer
("TT up (53 pods)") → refreshed this wave. Step 6 (E2) → the MIST column carries live-run
provenance; comparator arms unchanged (Tracetest/TraceAnomaly/contract-invariant runs remain
out of scope as planned).

## Operational field lessons (runbook-grade)

1. Detached-inside-WSL dies with its wsl.exe client → staged FOREGROUND-attached calls or
   detached WINDOWS-side drivers. 2. `svc/nacos` PF fed a dead target while pods answered
   readiness → pod-level PF; post-reboot even pod-level PF broke while `kubectl exec` worked
   → exec is the robust nacos-switch channel. 3. The 0x8007274c flap self-recovers for
   transient JVM-boot spikes but NOT for sustained RAM exhaustion — the lean-profile
   race-scale (`post-reboot-lean.sh`) is the reusable fix. 4. g3 runners need the
   TrainTicketStimulus.main reader-auth block, else the toggle 403s. 5. Long batches must
   redirect per-seed evidence OUT of wipe-able dirs (`deletepreviousresults` cost the killed
   seeds' Allure outcomes) — and never leave load-bearing config as UNTRACKED worktree files.
6. nacos restarts are not just a revival concern: they were OBSERVED (live, next-morning
   kubectl) to have occurred spontaneously overnight — any long unattended TT window should
   schedule a doubleWrite re-check regardless of the exact restart cause.
