# MIST test-tool flow — code-verified (2026-06-01)

Two modes, drawn separately. Every box is anchored to `file:line` and was confirmed by a 4-agent
read-only trace of the code (generation+cascade / single-phase exec+enhancer / two-phase / oracle).
File shorthands: **MR** = `mist-cli/.../MistRunner.java`, **MG** = `mist-core/.../generation/MistGenerator.java`,
**W** = `mist-cli/.../writer/MultiServiceRESTAssuredWriter.java`, **TRC** = `mist-core/.../enhancer/TestResultCapture.java`,
**SIF** = `mist-core/.../smart/SmartInputFetcher.java`, **TWE** = `mist-core/.../workflow/TraceWorkflowExtractor.java`,
**TSO** = `mist-core/.../oracle/shape/TraceShapeOracle.java`.

Three clarifications vs. the informal sketch: (a) in default `smart` mode only REGEX_MISMATCH + SEMANTIC_MISMATCH
faults use the LLM — the other 6 are hardcoded; (b) fault detection is **marker-driven** (`data.injected==true` +
registered `faultName`), and the Trace Shape Oracle verdict is the per-step pass/fail authority — NOT the JUnit
assertion; (c) the Jaeger fetch + oracle run **inside the generated test** (`attachJaegerTrace`), once per executed step.

---

## MODE 1 — SINGLE-PHASE (default; positives + negatives generated together)

```
 TRACE INPUT  (Jaeger/OTel JSON: data[].spans[] + processes)
   │  TWE.extractScenarios()  [TWE:55]  — group by traceId, no-parent span = root
   ▼
 SCENARIOS (root APIs + induced call trees)
   │  multi-root merge (data-dep / session-window)        [TWE:824 / :523]
   │  dedup by ROOT API only                              [MR:639]
   ▼
 MistGenerator.generate()                                  [MG:505]
   │  stage chain: dedup → SharedPool → WCC-shatter(Union-Find) → dedup → decompose   [MG:532-537]
   │
   │  per root API → generateScenarioVariants:
   │     ├── POSITIVE   count = round(N·(1−faultyRatio))                 [MG:627]
   │     └── NEGATIVE   count = round(N·faultyRatio), but ≥1 per FaultTarget (floor)  [MG:628,:712-714]
   │            Sniper = 1 fault / 1 target param / variant; all other params stay positive  [MG:757-760]
   │            8 fault types (boundary/overflow/null/empty/special-char/type/regex/semantic)
   │              · smart mode (default): only REGEX + SEMANTIC via LLM; other 6 hardcoded  [ZeroShotLLMGenerator:205-263]
   │
   │  per parameter VALUE — cascade (first hit wins):
   │     ① shared pool  (preferVerifiedValues prefers SUT-verified subset)   [MG:1198 → :300]
   │     ② smart-fetch   (live producer HTTP call, e.g. GET /stations)        [MG:1222 / SIF]
   │     ③ LLM           (generate value)                                     [MG:1243]
   │     ④ synthetic placeholder (FALLBACK_/LLM_EMPTY/STEP1_ → SYNTHETIC_PLACEHOLDER)  [MG:1248,:1301,:2654]
   ▼
 writer.write()  → JUnit/REST-Assured test files                            [W]
   │  isNegativeTest = scenario.getFaulty() || hasSyntheticPlaceholder()     [W:1439]
   ▼
 EXECUTE   (only if executeTestCases)                                        [MR:440]
   │  3-way branch:  enhancerEnabled = cfg.enabled() && !forceDisableEnhancer [MR:446]
   │     ├─ enhancer ON  → executeWithEnhancement(rounds)                     [MR:453]
   │     ├─ Phase-A      → (two-phase only; see MODE 2)                       [MR:454]
   │     └─ enhancer OFF → executeGeneratedTestsWithJUnit  (NO capture)       [MR:465]
   ▼
 executeWithEnhancement — for round = 0..N:                                  [MR:1327]
   ┌──────────────────────────────────────────────────────────────────────┐
   │ executeTestsWithCollector  [MR:1339]  (enableCapture [MR:1805])        │
   │   run JUnit; each test step:                                            │
   │     send request → assert actual-vs-expected status  [W:2182 / :2168]   │
   │     ── per step, INSIDE the test ──                                     │
   │        attachJaegerTrace [W:296] → fetch trace [W:355]                  │
   │        → TSO.evaluate(model, rootApi, targetSvc, targetParam) [W:700]   │
   │        → record verdict [W:757]   (success path [W:2328] / fail [W:2498])│
   │     negative test: RESPONSE_ENVELOPE violation ⇒ FAIL→PASS  [W:2504]    │
   │     marker check: data.injected==true + faultName ⇒ recordDetectedFault │
   │                   [W:2244 / :2386]  (faultName must be pre-registered)  │
   │   collect FAILED tests  [MR:1524]                                       │
   │ round 0 only: status-code exploration (if SCE config on)  [MR:1349]     │
   │ if failures & rounds remain:                                            │
   │   enhanceBatch — LLM READS the SUT error (HTTP status/response/error)   │
   │                  to propose better values   [MR:1525 / TestCaseEnhancer:421,451] │
   │   regenerateTestFile — patch param values in place  [MR:1546]           │
   │   recompile → next round re-executes                                    │
   └──────────────────────────────────────────────────────────────────────┘
   ▼
 REPORT  — Trace Shape Oracle verdicts = pass/fail authority; marker-driven detected-faults;
           oracle anomalies tracked separately. (legacy TraceErrorAnalyzer + LLM validator
           [default OFF, MstConfig:230] = diagnosis/Allure only)
```

---

## MODE 2 — TWO-PHASE (opt-in `mst.two.phase.enabled=true`  [MR:326])

Positives FIRST (harvest values the SUT accepted) → negatives REUSE them, so a negative's rejection is
attributable to the injected fault, not a bad baseline param. This is the feature built in commits
c0f24632 (A1) + abfc474d (enhancer-rescue loop).

```
 runTwoPhasePipeline  [MR:502]

 ╔═════════════ PHASE A — positive baseline (harvest VERIFIED_VALID) ═════════════╗
 ║  setFaultyRatio(0.0)  [MR:502]                                                  ║
 ║  setPhaseARescuePlaceholders(true)  [MR:506]                                    ║
 ║  runSinglePhasePipeline(forceDisableEnhancer=true)  [MR:510]                    ║
 ║     → executeWithEnhancement(rounds = max(1, configured), exploreStatusCodes=false) [MR:461]  ║
 ║       (the "Phase-A" branch — capture + enhancer-rescue, SCE suppressed)        ║
 ║                                                                                 ║
 ║  generate positives. A param that can't ground → synthetic placeholder.        ║
 ║  BUT rescue-mode ⇒ isNegativeTest stays FALSE → it remains a POSITIVE (exp 2xx) ║
 ║     isNegativeTest = getFaulty() || (hasSyntheticPlaceholder() && !rescue)  [W:1439] ║
 ║                                                                                 ║
 ║  EXECUTE:                                                                       ║
 ║    placeholder positive → 400 → fails POSITIVE assertion  [W:2182]              ║
 ║       → enhancer rescue: read SUT 400 error → LLM regenerate valid value        ║
 ║                          → re-execute → 2xx  [MR:1327/1525/1546]                ║
 ║    on 2xx → recordParameterSuccess PER BODY FIELD  [W:2028-2033 → :2301]        ║
 ║       gate: captureEnabled + 2xx + !isNegativeTest  [TRC:347-362]               ║
 ║       Pattern 7: rescued param's stepParams.put rewritten to ENHANCED value     ║
 ║                  [TestFileRegenerator:214]                                      ║
 ║                                                                                 ║
 ║  drainParameterObservationsToRegistry → markVerified → VERIFIED_VALID  [MR:1908/1958] ║
 ╚═════════════════════════════════════════════════════════════════════════════════╝
            │
            │  INTER-PHASE  [MR:523-524]
            │    resetForNewPhase() — reload verified pool from registry  [MG:485]
            │    FaultDetectionTracker.reset()  (Phase A doesn't count in the report)
            ▼
 ╔═════════════ PHASE B — negatives (REUSE verified pool) ═════════════╗
 ║  setFaultyRatio(original)  [MR:543]                                  ║
 ║  setPhaseARescuePlaceholders(false)  [MR:546]                        ║
 ║  runSinglePhasePipeline (normal enhancer path)                       ║
 ║                                                                      ║
 ║  generate negatives (Sniper: 1 fault on the TARGET param).           ║
 ║  for each NON-target pooled param:                                   ║
 ║     preferVerifiedValues(rawPool, verb, route, name)  [MG:1198→:300] ║
 ║        = rawPool ∩ getVerifiedValues(endpoint, name)                 ║
 ║        → draw non-target values from SUT-ACCEPTED set only           ║
 ║          (verified live: startStation/endStation/id/loginId narrowed)║
 ║                                                                      ║
 ║  EXECUTE (+ enhancer + per-step trace oracle, same as MODE 1)        ║
 ║  RETURN Phase B test cases = the deliverable  [MR:528]               ║
 ╚═══════════════════════════════════════════════════════════════════════╝
```

**Cost / status:** opt-in, ~2× SUT execution (both phases run), and Phase A re-pollutes the SUT DB. Verified
live on train-ticket adminroute: PHASE A capture+enhancer-rescue → 34 regenerations → Phase B **869 narrowing
events**, 0 regenerated-test compile errors. See `debug/grounding/producer-ranking-and-two-phase.md` for the
full V1/V2 verification trail. The verified non-target baselines also feed the `TargetAttribution` invariant.
