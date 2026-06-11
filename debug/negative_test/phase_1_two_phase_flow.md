# Phase 1 — Two-Phase Flow with Pool Validation

## Goal

Replace the current "all variants generated and executed in one pass" flow
with two sequential phases:

1. **Phase A — Positive baseline.** Generate only positive variants, run
   them, and record which input values the SUT accepted (HTTP 2xx).
2. **Pool validation step.** Mark each `InputFetchRegistry` entry as
   `VERIFIED_VALID`, `REJECTED_BY_SUT`, or `UNVERIFIED` based on Phase A
   outcomes.
3. **Phase B — Negative tests.** Generate negative variants using only
   `VERIFIED_VALID` entries for non-target parameters. Sniper now really
   only wounds its target.

## Why this is Phase 1 (post-Run-22 reranking)

Run 22 already hit 10/10 by routing failed tests through the LLM
enhancer, which produced new parameter values that happened to be valid
enough to make Sniper land its shot. That is good for the benchmark but
bad for repeatability and cost:

| | Phase 1 (this doc) | Run 22 (status quo) |
|---|---|---|
| Cost per run | One extra positive-only sweep (~minutes; positives are simple) | LLM-enhance step (Run 22: 28 min, 2,847 LLM calls, ~$ depends on provider) |
| Determinism | SUT response is the source of truth; same SUT → same verified pool → same negatives | LLM responses at temp=0.3 vary between runs even with our LLM-validation cache (cache reuses byte-identical prompts; LLM-enhanced prompts vary) |
| Failure recovery | When LLM is down (e.g. Run 22 hit `Insufficient Balance` at 22:33) the enhancer can't help — but Phase B still has verified pool | Run 22 stalled into Ollama fallback (4× slower) for the last 2 hours |
| Generalizes to non-LLM-enhanced workflows | Yes | No — requires the LLM path |

In short: Run 22 proved the listener fix lets the LLM enhancer rescue a
broken pool. Phase 1 makes the enhancer optional rather than mandatory.

## Design

### Data model

Add to `InputFetchRegistry`:

```java
public enum PoolEntryStatus { UNVERIFIED, VERIFIED_VALID, REJECTED_BY_SUT }

// keyed by (endpoint, paramName, concreteValue)
private final Map<PoolKey, Map<String, PoolEntryStatus>> entryStatus = new HashMap<>();

public void markVerified(PoolKey key, String value);    // Phase A success
public void markRejected(PoolKey key, String value);    // Phase A 4xx/5xx
public List<String> getVerifiedValues(PoolKey key);     // Phase B reader
```

The status map persists alongside `parameterMappings` in the existing
YAML — append-only field with `UNVERIFIED` default, so an old YAML loads
without migration.

### TestResultCapture extension

Mirror the existing `markTestFailed` for the success path:

```java
public static void recordParameterSuccess(
    String endpoint, String paramName, String value, int statusCode);
```

The generated test code already extracts `actualStatusCode<stepIdx>` and
the body parameters. Add one call per non-target parameter after a 2xx
step success, only when `!isNegativeTest`.

### MistRunner two-phase loop

Gated on `mist.two.phase.enabled=true` (default false → byte-identical
legacy behaviour):

```java
if (twoPhaseEnabled) {
    runPhaseA();                                  // positive-only, faulty.ratio=0
    InputFetchRegistry.getInstance().drain();     // Phase A success drain
    runPhaseB();                                  // negatives with verified pool
} else {
    runSinglePhase();                             // unchanged
}
```

Each phase is its own full pipeline pass: `generate()` →
`writer.write()` → `compileTestClasses()` →
`executeTestsWithCollector()`. Phase A skips the enhancer; Phase B uses
the full enhance loop just like today.

### Generator change

Single chokepoint at `MistGenerator:1050-1074` (where Sniper pulls
non-target values). Add one filter:

```java
if (twoPhaseEnabled && phase == B) {
    List<String> poolVals = registry.getVerifiedValues(poolKey);
    if (poolVals.isEmpty()) {
        // Fallback: stale or never-tried value. Log and use legacy pool.
        log.warn("Phase B: no verified pool for {} — falling back to raw pool", poolKey);
        poolVals = sharedParameterPools.get(rootApiKey).get(paramName);
    }
    // pick one
    val = poolVals.get(random.nextInt(poolVals.size()));
}
```

## Implementation Checklist

- [ ] **Data model**
  - [ ] `PoolEntryStatus` enum
  - [ ] `Map<PoolKey, Map<String, PoolEntryStatus>> entryStatus` on `InputFetchRegistry`
  - [ ] `markVerified` / `markRejected` / `getVerifiedValues` methods
  - [ ] YAML serialization includes the status map (legacy YAMLs load with `UNVERIFIED`)
- [ ] **Capture path**
  - [ ] `TestResultCapture.recordParameterSuccess(endpoint, param, value, status)`
  - [ ] `MultiServiceRESTAssuredWriter` emits one `recordParameterSuccess` call per non-target parameter on a 2xx step (positive tests only)
  - [ ] At Phase A end, drain `TestResultCapture` successes into `InputFetchRegistry.markVerified`
- [ ] **MistRunner orchestration**
  - [ ] `mist.two.phase.enabled` flag read at run start
  - [ ] `runPhaseA()` — `System.setProperty("faulty.ratio","0.0")`, separate `className` suffix `_phaseA`, no enhancer loop
  - [ ] Pool drain between phases
  - [ ] `runPhaseB()` — original `faulty.ratio` restored, `className` suffix `_phaseB`, enhancer loop optional
  - [ ] Singleton state reset between phases (Timer, ConsoleProgressBar, FaultDetectionTracker counters scoped per phase)
- [ ] **Generator filter**
  - [ ] Phase B reads `getVerifiedValues` for non-target params at `MistGenerator:1050-1074`
  - [ ] Fallback policy when no verified values exist (log + raw pool)
  - [ ] Phase A path unchanged (uses raw pool — there's nothing to verify yet)
- [ ] **Config + validator**
  - [ ] `mist.two.phase.enabled` in `MstConfigValidator.KNOWN_KEYS`
- [ ] **Tests**
  - [ ] `InputFetchRegistryPoolStatusTest` — mark, query, YAML roundtrip with mixed statuses
  - [ ] `TestResultCaptureSuccessDrainTest` — recordParameterSuccess accumulates correctly across parallel JUnitCores
  - [ ] `MistRunnerTwoPhaseTest` — flag off = old behaviour byte-identical; flag on = two-phase log lines present
  - [ ] Smoke run on train-ticket with `two.phase=true` — verify 10/10 still detected
- [ ] **Empirical validation**
  - [ ] Run 23 with `mist.two.phase.enabled=true` and the LLM enhancer DISABLED — does it still hit 10/10?
  - [ ] If yes: Phase 1 has replaced the LLM path's role. Document.
  - [ ] If no: Phase 1 is complementary, not a replacement. Document gap.

## Risks (from Agent D audit, recalibrated)

| Risk | Severity | Mitigation |
|---|---|---|
| Doubled wall-clock time | Medium | Phase A is positive-only (~1/3 of variants per scenario, no LLM validation needed); estimated +30-50% wall time |
| Singleton state leakage between phases (Timer, FaultDetectionTracker, ConsoleProgressBar) | High | Explicit reset hooks at phase boundary; named tests pin behaviour |
| `generate()` non-idempotence (bandit ranking + `seenPayloads` reset) | High | Add `MistGenerator.resetForNewPhase()` clearing per-phase dedup state |
| Pool emptiness if SUT rejects everything (real upstream bug at some endpoint) | Medium | Fallback to raw pool + WARN log; Phase B still produces variants, just less reliably for that endpoint |
| YAML schema break for shared/checked-in registry files | Low | Status field defaults to UNVERIFIED on legacy YAML load |
| `TestResultCapture` static singleton gets cleared mid-phase by the Phase B start | Medium | Already addressed by the idempotency fix in commit 72521685; verify the same guard holds across phase boundary |
| Sniper still pulls invalid values for the target's *faulty* pool (separate from shared pool) | Low | Out of scope; Phase 1 fixes non-target params only |

## Success Criteria

- Run 23 with `two.phase=true`, LLM enhancer disabled, achieves ≥ 7/10 on
  the train-ticket benchmark. (Goal: 10/10. Floor: matches current single
  phase result, proving Phase 1 doesn't regress.)
- Two consecutive runs with same seed produce byte-identical
  fault-detection reports (proves determinism gain).
- Total wall time on a full train-ticket run < 1.5× Run-22-equivalent
  with LLM enhancer.
- For 100 random pool entries inspected: `successRate > 0.0` correlates
  with `VERIFIED_VALID`; `successRate ≈ 0.0` correlates with
  `REJECTED_BY_SUT`.
- `mist.two.phase.enabled=false` produces byte-identical legacy output.

## Out of Scope

- Trace-level oracle changes (Phase 2).
- Cross-run pool persistence (each run starts from the YAML state on
  disk; verified status accumulates within a run only — could persist
  later via a status-on-disk field).
- Smart "verified pool subset" optimisations (LRU, age-aware,
  diversity-aware). Phase 1 is "use any verified value, log if none".
