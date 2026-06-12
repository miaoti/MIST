# MIST test-tool flow — code-verified (2026-06-12; first verified 2026-06-01)

Two modes, drawn separately. Every box is anchored to `file:line` and was confirmed by a 4-agent
read-only trace of the code (generation+cascade / single-phase exec+enhancer / two-phase / oracle),
re-run on 2026-06-12 against MIST `main` (the standalone repo): **53/53 anchors re-verified, zero
behavioural mismatches**; line numbers re-anchored (the 06-01 numbers predated the June pipeline
fixes), one content update (fault-type count 8→9) and the oracle invariant table added. Δ-log at
the bottom.
File shorthands: **MR** = `mist-cli/.../MistRunner.java`, **MG** = `mist-core/.../generation/MistGenerator.java`,
**W** = `mist-cli/.../writer/MultiServiceRESTAssuredWriter.java`, **TRC** = `mist-core/.../enhancer/TestResultCapture.java`,
**SIF** = `mist-core/.../smart/SmartInputFetcher.java`, **TWE** = `mist-core/.../workflow/TraceWorkflowExtractor.java`,
**TSO** = `mist-core/.../oracle/shape/TraceShapeOracle.java`, **ZS** = `mist-core/.../generation/ZeroShotLLMGenerator.java`.

Four clarifications vs. the informal sketch: (a) in default `smart` mode only REGEX_MISMATCH +
SEMANTIC_MISMATCH faults use the LLM — the other **7** are hardcoded (ENUM_VIOLATION joined the
original 6); (b) fault detection is **marker-driven** (`data.injected==true` + registered
`faultName`), and the Trace Shape Oracle verdict is the per-step pass/fail authority — NOT the JUnit
assertion; (c) the Jaeger fetch + oracle run **inside the generated test** (`attachJaegerTrace`),
once per executed step; (d) configuration is **one `.properties` file** — when `mst.config.path`
is absent, MistMain uses the core file itself as the MST source [MistMain:100-103], which also
makes the file's `faulty.ratio` effective for the MST pipeline (the legacy split-overlay path
silently fell back to the 0.1 default [MstConfig:247]).

---

## MODE 1 — SINGLE-PHASE (default; positives + negatives generated together)

```
 TRACE INPUT  (Jaeger/OTel JSON: data[].spans[] + processes)
   │  TWE.extractScenarios()  [TWE:55]  — group by traceId, no-parent span = root
   ▼
 SCENARIOS (root APIs + induced call trees)
   │  multi-root merge (data-dep / session-window)        [TWE:824 / :523]
   │  dedup by ROOT API only                              [MR:649]
   ▼
 MistGenerator.generate()                                  [MG:513]
   │  stage chain: dedup → SharedPool → WCC-shatter(Union-Find) → dedup → decompose   [MG:540-545]
   │
   │  per root API → generateScenarioVariants:
   │     ├── POSITIVE   count = round(N·(1−faultyRatio))                 [MG:644]
   │     └── NEGATIVE   count = max(round(N·faultyRatio), 1·perFaultTarget floor)  [MG:645]
   │            Sniper = 1 fault / 1 target param / variant; all other params stay positive  [MG:757-765]
   │            9 fault types (boundary/overflow/null/empty/special-char/type/regex/semantic/enum)
   │              · smart mode (default): only REGEX + SEMANTIC via LLM; other 7 hardcoded  [ZS:231-266]
   │
   │  per parameter VALUE — cascade (first hit wins):
   │     ① shared pool  (preferVerifiedValues prefers SUT-verified subset)   [MG:1221 → :300]
   │     ② smart-fetch   (live producer HTTP call, e.g. GET /stations)        [MG:1245 / SIF]
   │     ③ LLM           (generate value)                                     [MG:1268]
   │     ④ synthetic placeholder (FALLBACK_/LLM_EMPTY/STEP1_ → SYNTHETIC_PLACEHOLDER)  [MG:1271,:1324,:2736]
   ▼
 writer.write()  → JUnit/REST-Assured test files                            [W]
   │  isNegativeTest = scenario.getFaulty() || hasSyntheticPlaceholder()     [W:1485]
   ▼
 EXECUTE   (only if executeTestCases)                                        [MR:450]
   │  3-way branch:  enhancerEnabled = cfg.enabled() && !forceDisableEnhancer [MR:456]
   │     ├─ enhancer ON  → executeWithEnhancement(rounds)                     [MR:463]
   │     ├─ Phase-A      → (two-phase only; see MODE 2)                       [MR:464]
   │     └─ enhancer OFF → executeGeneratedTestsWithJUnit  (NO capture)       [MR:476]
   ▼
 executeWithEnhancement — for round = 0..N:                                  [MR:1337]
   ┌──────────────────────────────────────────────────────────────────────┐
   │ executeTestsWithCollector  [MR:1360]  (enableCapture [MR:1826])        │
   │   run JUnit; each test step:                                            │
   │     send request / extract response  [W:2125]                           │
   │     assert actual-vs-expected status [W:2262]                           │
   │     ── per step, INSIDE the test ──                                     │
   │        attachJaegerTrace [W:301] → fetch trace [W:355]                  │
   │        → TSO.evaluate(model, rootApi, targetSvc, targetParam) [W:714]   │
   │          (six invariant families — see ORACLE box below)                │
   │        → verdict → LAST_VERDICT [W:715] → recordVerdict [W:781]         │
   │          (success path [W:2345] / fail path [W:~2510])                  │
   │     negative test: RESPONSE_ENVELOPE violation ⇒ FAIL→PASS  [W:2594]    │
   │     marker check: data.injected==true + faultName ⇒ recordDetectedFault │
   │                   [W:2324 / :2467]  (faultName must be pre-registered)  │
   │   collect FAILED tests  [MR:1545]                                       │
   │ round 0 only: status-code exploration (if SCE config on)  [MR:1370]     │
   │ if failures & rounds remain:                                            │
   │   enhanceBatch — LLM READS the SUT error (HTTP status/response/error)   │
   │                  to propose better values   [MR:1546 / TestCaseEnhancer:421,451] │
   │   regenerateTestFile — patch param values in place  [MR:1566]           │
   │   recompile → next round re-executes                                    │
   └──────────────────────────────────────────────────────────────────────┘
   ▼
 REPORT  — Trace Shape Oracle verdicts = pass/fail authority; marker-driven detected-faults;
           oracle anomalies tracked separately. (legacy TraceErrorAnalyzer + LLM validator
           [default OFF, MstConfig:230] = diagnosis/Allure only)

 ORACLE — TSO.evaluate runs the enabled invariant families per step [TSO:91-118]:
   │  SpanTreeShape          default ON   [MstConfig:405]
   │  StatusPropagation      default ON   [MstConfig:407]
   │  ResponseEnvelope       default ON   [MstConfig:409]  (first unseen 2xx value → 1 LLM call,
   │                                       classifier wired at test startup [W:306-311])
   │  TimingEnvelope         default OFF  [MstConfig:411]
   │  TargetAttribution      default ON   [MstConfig:413]  (needs non-null targetSvc)
   │  HiddenDownstreamFailure default OFF — opt-in          [MstConfig:415]
   │     (the paper's headline G2 invariant; enabled in the MST sections of the
   │      bookinfo + sockshop demo .properties; dedicated Allure attachment [W:751-770])
```

---

## MODE 2 — TWO-PHASE (opt-in `mst.two.phase.enabled=true`  [MR:326])

Positives FIRST (harvest values the SUT accepted) → negatives REUSE them, so a negative's rejection is
attributable to the injected fault, not a bad baseline param. This is the feature built in commits
c0f24632 (A1) + abfc474d (enhancer-rescue loop) of the development-history archive (miaoti/Rest).

```
 runTwoPhasePipeline  [MR:502]

 ╔═════════════ PHASE A — positive baseline (harvest VERIFIED_VALID) ═════════════╗
 ║  setFaultyRatio(0.0)  [MR:512]                                                  ║
 ║  setPhaseARescuePlaceholders(true)  [MR:516]                                    ║
 ║  runSinglePhasePipeline(forceDisableEnhancer=true)  [MR:520]                    ║
 ║     → executeWithEnhancement(rounds = max(1, configured), exploreStatusCodes=false) [MR:461→473] ║
 ║       (the "Phase-A" branch — capture + enhancer-rescue, SCE suppressed)        ║
 ║                                                                                 ║
 ║  generate positives. A param that can't ground → synthetic placeholder.        ║
 ║  BUT rescue-mode ⇒ isNegativeTest stays FALSE → it remains a POSITIVE (exp 2xx) ║
 ║     isNegativeTest = getFaulty() || (hasSyntheticPlaceholder() && !rescue)  [W:1485] ║
 ║                                                                                 ║
 ║  EXECUTE:                                                                       ║
 ║    placeholder positive → 400 → fails POSITIVE assertion  [W:2262]              ║
 ║       → enhancer rescue: read SUT 400 error → LLM regenerate valid value        ║
 ║                          → re-execute → 2xx  [MR:1337/1546/1566]                ║
 ║    on 2xx → recordParameterSuccess PER BODY FIELD  [W:2380-2384]                ║
 ║       gate: captureEnabled + 2xx + !isNegativeTest  [TRC:347-362]               ║
 ║       Pattern 7: rescued param's stepParams.put rewritten to ENHANCED value     ║
 ║                  [TestFileRegenerator:214]                                      ║
 ║                                                                                 ║
 ║  drainParameterObservationsToRegistry → markVerified → VERIFIED_VALID  [MR:1929/1979] ║
 ╚═════════════════════════════════════════════════════════════════════════════════╝
            │
            │  INTER-PHASE  [MR:523-534]
            │    resetForNewPhase() — reload verified pool from registry  [MR:533 → MG:485]
            │    FaultDetectionTracker.reset()  [MR:534]  (Phase A doesn't count in the report)
            ▼
 ╔═════════════ PHASE B — negatives (REUSE verified pool) ═════════════╗
 ║  setFaultyRatio(original)  [MR:553]                                  ║
 ║  setPhaseARescuePlaceholders(false)  [MR:556]                        ║
 ║  runSinglePhasePipeline (normal enhancer path)                       ║
 ║                                                                      ║
 ║  generate negatives (Sniper: 1 fault on the TARGET param).           ║
 ║  for each NON-target pooled param:                                   ║
 ║     preferVerifiedValues(rawPool, verb, route, name)  [MG:1221→:300] ║
 ║        = rawPool ∩ getVerifiedValues(endpoint, name)                 ║
 ║        → draw non-target values from SUT-ACCEPTED set only           ║
 ║          (verified live: startStation/endStation/id/loginId narrowed)║
 ║                                                                      ║
 ║  EXECUTE (+ enhancer + per-step trace oracle, same as MODE 1)        ║
 ║  RETURN Phase B test cases = the deliverable  [MR:557]               ║
 ╚═══════════════════════════════════════════════════════════════════════╝
```

**Cost / status:** opt-in, ~2× SUT execution (both phases run), and Phase A re-pollutes the SUT DB. Verified
live on train-ticket adminroute: PHASE A capture+enhancer-rescue → 34 regenerations → Phase B **869 narrowing
events**, 0 regenerated-test compile errors. See `debug/grounding/producer-ranking-and-two-phase.md` for the
full V1/V2 verification trail. The verified non-target baselines also feed the `TargetAttribution` invariant.

---

## Δ since the 2026-06-01 verification

Re-verified 2026-06-12 on MIST `main` (4 parallel read-only agents, 53 anchors). Behavioural
claims all held; the following changed or were added to the picture:

1. **9 built-in fault types, not 8** — ENUM_VIOLATION joined (hardcoded, schema-aware enum
   violations) [ZS:255, `mist/fault-types.default.yaml`]. Smart-mode LLM usage unchanged
   (REGEX + SEMANTIC only).
2. **Negative-count floor folded into the count expression** — the ≥1-per-FaultTarget floor now
   lives inside the count line [MG:645]; the old separate block at MG:712-714 is gone.
3. **ORACLE box added** — the six invariant families with their defaults were implicit before;
   HiddenDownstreamFailure (the paper's headline, opt-in) and TargetAttribution (default ON)
   were missing from the drawing entirely.
4. **Single-file configuration** (2026-06-11) — clarification (d): MistMain falls back to the
   core `.properties` as the MST source [MistMain:100-103]; this also ended the `faulty.ratio`
   split-brain (file value now effective; legacy overlay path used the 0.1 default).
5. **All line anchors re-based** to the MIST standalone repo (initial release `8e8f633`); the
   06-01 numbers predated the June pipeline fixes (18 logic bugs + four A-rank fixes, including
   the dedup leak) that are folded into the release snapshot. Scenario counts under the bundled
   seeded demo accordingly re-baselined 123 → 26 (see `debug/reproduce/README.md` §5).
