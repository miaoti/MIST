# UX wave demo DoD — RESULT: PASSED (2026-07-09)

**The DoD (checklist §1.9.3):** a normal `java -jar mist.jar <properties>` run shows the
data-integrity section in the Allure report. **Verdict: MET — live-verified end-to-end.**

## The run
- Command: `java -jar mist-cli/target/mist.jar mist-cli/src/main/resources/My-Example/trainticket-demo-dod.properties`
  (detached Windows process; the DoD profile = the bundled demo properties with two bounds:
  `testsperoperation=10`, `llm.enabled=false` — committed beside the demo as the reproducible DoD
  profile). Registry = `target-triples-demo.yaml` via the NEW `mst.oracle.dataintegrity.registry`
  key (fault_flag-free product registry).
- SUT: TrainTicket quick_start on kind "mist", namespace `trainticket`, images `codewisdom/*:1.0.0`,
  UI via WSL port-forward `localhost:32677`; admin login live-verified pre-run. TT is UNTRACED under
  quick_start (no Jaeger) — disclosed; on a healthy SUT the ✅ tier needs no trace backend.
- Scale: **2,483 test cases generated + executed**, exit code 0; run ≈ 67 min wall
  (00:41 registry load → 01:48 complete) incl. generation/probing; single final execution round.

## The evidence chain (all live)
1. **Registry key**: `MistRunner:359 — data-integrity oracle ON: 2 target triple(s) from
   …\target-triples-demo.yaml` (the new property + beside-conf resolution worked).
2. **Observe arming (W0)**: `MistRunner:612 — data-integrity OBSERVE mode: session
   'observe-final-round' armed for 2 triple(s); test parallelism forced to 1` +
   `DataIntegrityRuntime:384 — OBSERVE run active … poll=500ms timeout=10000ms settle=3000ms`
   — the ENHANCER-final-round arming path fired (the demo ships enhancer rounds=1).
3. **Hook + check in generated code (W1)**: generated corpus = 26 classes; the class covering the
   adminroute write (`Flow_Scenario_262.java`) carries `beforeWrite`/`afterWrite` +
   `DataIntegrityObserveCheck.afterStep` emissions (verified in source).
4. **THE VERDICT IN THE REPORT (W2)**: rendered Allure test case
   **`test_positive_flow_S262_v219` — passed — step "✅ durable write confirmed
   [adminroute-create] — read-back shows it (1 poll(s), 69 ms)"** — present in
   `target/allure-report` (HTML generated via `allure generate`). 1/1 hooked positive execution
   rendered its verdict (the bounded run produced exactly one positive variant of the hooked flow).
5. **categories.json (W2)**: written into `target/allure-results/` at run end by `maybeEndObserve`
   (💧 acked-but-lost + hidden-downstream groups; the Categories tab shows them only when a matching
   FAILURE exists — a healthy TT produced none, which is the correct quiet state).
6. **Suites**: full reactor BUILD SUCCESS (mist-cli 199 tests incl. the wave's 19); paired/eval
   pinned suites unchanged = the U5 byte-equal proof.

## Honest caveats (disclosed)
- One hooked write observed in this bounded run (the demo trace covers adminroute; the contacts
  triple has 0 references in the demo corpus — pre-checked and disclosed). The verdict tiers
  (💧/⏳/⚠️/ℹ️) are pinned by the 7 DataIntegrityObserveTest unit tests, not by this live run — a
  healthy SUT can only demonstrate ✅.
- The 1,562 red tests in the run are the demo's ordinary status/schema/negative-test failures
  (LLM off; TT quirks) — none carry a data-integrity failure (0 ACKED-BUT-LOST, correct on a
  healthy SUT).
- The terminal observe summary went to the raw console (printRaw) and missed the redirected log —
  fixed post-DoD (summary now mirrored through the logger); categories.json (same code path)
  proves `maybeEndObserve` ran.
- Deploy-side potholes (image tag 1.0.2→1.0.0, scaled-to-0 helm infra, nacos needs BOTH members +
  `doubleWriteEnabled=false` after a PVC-restart into 1.X mode) are recorded in the checklist §2.6
  runbook.

## 1.9.5 — MIST study-commit pin
The pin criteria (value-delta + supplied hooks + injectors + W0–W6 in one buildable commit;
QuiescenceGate→verdict mapping frozen) are met by the commit containing this record + the
summary-logging fix. **The pin = the commit of this document** (recorded in `c2-freeze.md` §6
amendment table at population time; all case verdicts from step 3a on are recorded at it; the
promoted G1/G3 seeds are re-recorded at it). Fork-side (train-ticket-injection@MIST-trainticket)
pins separately at its HEAD `a1767ab3`.
