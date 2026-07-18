# RESULT — the END-TO-END Allure demonstration (MIST generates → discovers → reports) — RESULT OF RECORD

**Date:** 2026-07-18 · Answers the USER's direct question ("有从头到尾验证过 MIST 和 benchmark 的
flow 吗?就真使用 MIST 去生成 test case 然后去发现然后报告在 allure report 里面?") — the honest
audit found the Allure surface had NEVER been verified/preserved; this wave closed it with a live
run of record. Artifacts: `final-run.log` + `allure-results/` + `allure-report/` (open
`allure-report/index.html`) + the runner/scripts under `runners/fcorpus/e2e-demo-*`.

## What was demonstrated, end to end, in ONE observe session
| phase | action | MIST's live verdict |
|---|---|---|
| 0 | fault OFF (agent kept), TT revived, adminroute image REBUILT from the fork (see finding F1) | — |
| 1 | **MIST-GENERATED** scenario (`Flow_Scenario_15`, trace-scenario stimulus generation, gate1-pairing profile) executes: POST /api/v1/adminrouteservice/adminroute | write LANDS: `acked=true HTTP 200, gate=OBSERVED_PRESENT, readbackContainedX=true` — the read-back binding SELF-VALIDATES (U8) |
| 2 | mid-session fault flip ON (`mist.fault.lostwrite.enabled=true`, rollout) — the intermittent-production-bug shape | — |
| 3 | the SAME generated scenario executes again: POST acked **HTTP 200** | **`ACKED-BUT-LOST WRITE [adminroute-create] … client got HTTP 200 (acked) but the durable write is ABSENT from the read-back (observation-gated: the step's own trace is complete)`** — `gate=OBSERVED_COMPLETE_ABSENT` (Jaeger trace-complete confirmed), `failonlost=true` FAILS the generated test |
| 4 | `allure generate` | the failed test renders in the **Allure HTML report** with the 💧 ACKED-BUT-LOST attachment + per-step evidence (categories: the default Product-defects bucket carries it; the custom 💧 category regex keys on the outer message and does not match — cosmetic, disclosed below) |

`tests run=2, failures=1` — the ONLY failure is the data-integrity defect. Full sequence log:
`final-run.log`.

## The oracle-quality moments the demo itself surfaced (all designed behavior, now witnessed live)
- **U8 precision-first quarantine, live:** an earlier all-faulted attempt produced
  `OBSERVED_COMPLETE_ABSENT` on every write and the check QUARANTINED instead of flagging
  ("this triple's read-back has not shown any write landing this run") — a mis-bound read-back
  is indistinguishable from total loss, so MIST refuses the defect verdict until the read-back
  self-validates. The final run's land-then-lose shape is exactly what the guard demands.
- **The trace gate, live:** with a wrong Jaeger base URL the same loss came out `TIMEOUT_ABSENT`
  (WARN-only, no failure); fixing the URL upgraded it to `OBSERVED_COMPLETE_ABSENT` (defect
  tier) — the R1d-documented confidence ladder, exercised end to end.

## Findings (disclosed)
- **F1 — STALE-IMAGE finding (reproduction-relevant):** the cluster's deployed
  `codewisdom/ts-admin-route-service:1.0.0` PREDATES the fork's lost-write fault commit
  (verified in-pod: `lostWriteFaultEnabled` absent from the class constant pool; the persist
  call demonstrably executed with the flag set). G1 run-3's FIRE ran on an earlier deployment
  that carried it. The demo rebuilt the image from the fork (`:mistfault` tag, kind-loaded,
  now verified to carry the fault) — the corpus's `sut.image_digest` pins exist precisely for
  this class of drift; the TT-adminroute case replays need the rebuilt image on this cluster.
- **F2 — full-suite starvation (MYC mechanism, reproduced):** the naive full-pipeline demo
  (MistMain, whole generated suite, 40-min wall) burned the budget on slow unrelated scenarios
  and produced 0 DI records — the same observe-starvation MYC documented. The demo's
  hand-picked-scenario runner (`TinyObserveRunner`) is the honest bounded-window workaround and
  is now a committed tool.
- **F3 — category regex cosmetic gap:** the scenario wrapper re-throws a generic failure
  message, so the custom 💧 Allure category (regex on ACKED-BUT-LOST) does not bucket the test;
  the marker + full evidence live in the step text and the 💧 attachment on the test page.
  Optional MIST polish: propagate the marker into the wrapper's message.
- **Paired-mode surface (already banked, not re-run):** the cross-run paired differential
  verdict renders in the pairing report JSON, not Allure (one Allure report = one run by
  construction); its live FIREs are the banked G1 run-3 + TT-omnibus records.

## Ops rails learned
`st`-era 30005 path is the UI SPA (fake 200s) — the query API on the istio `tracing` PF is
`/jaeger/api`; ProcessBuilder("bash") on this box resolves to the WSL launcher (in-WSL commands,
no `wsl` prefix); post-rollout writes need ~90s (ribbon+nacos re-registration) or they read-time-out.

## End state
TT scaled to 0; adminroute JTO env removed (image stays `:mistfault` — the CORRECT fork build);
sealed sets untouched. The demo artifacts are committed under `b4/e2e-demo/`.
