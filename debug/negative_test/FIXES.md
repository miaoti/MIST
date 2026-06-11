# Phase 0/1/2 Audit Fix-List (Local)

Verified against current `origin/inject-detection` HEAD `19c0bf3c` after a
manual grep-and-read pass over the three reviewer agents' findings. Some
severities downgraded from the initial reports after direct evidence
check (notes inline).

---

## Summary

| # | Phase | Severity | Title | Effort |
|---|---|---|---|---|
| F1 | 2 | **HIGH** | `TargetAttributionInvariant` not in the ShapeInvariant family | 1.5–2 h |
| F2 | 2 | **HIGH** | `mst.oracle.shape.invariants.target_attribution.enabled` not in validator allow-list | 5 min |
| F3 | 2 | **HIGH** | `TraceAttribution.attribute()` runs unconditionally — no kill switch | 30 min |
| F4 | 2 | MEDIUM | No per-run TraceModel cache; same trace rebuilt per step | 1 h |
| F5 | 2 | MEDIUM | Synchronous in-test trace pull; not batched or async | 2–4 h (defer) |
| F6 | 2 | LOW | Missing unit test: parallel leaf-error spans in sibling branches | 20 min |
| F7 | 1 | LOW | `ConsoleProgressBar` state not explicitly reset between phases | 15 min |
| F8 | 0 | NIT | 4 invariants don't call `recordOracleAnomaly` directly — go through `recordVerdict` | 0 (design refinement, leave) |

**Critical-path bundle (recommended to ship Phase 2 cleanly)**: F1 + F2 + F3.
Combined effort: ~2.5 hours. One single commit, scoped to the
`oracle.attribution` + `oracle.shape.invariant` package + the validator
file.

**Defer**: F4, F5 (perf, not correctness). F6 (low-probability edge case).
F7 (cosmetic). F8 (already justified by commit message).

---

## F1 — `TargetAttributionInvariant` not in the ShapeInvariant family

**Severity**: HIGH (downgraded from BLOCKER after grep verification)

**Why HIGH not BLOCKER**: the attribution algorithm IS being invoked.
`FaultDetectionTracker.recordVerdict(verdict, model, …, targetService,
targetParam)` calls `TraceAttribution.attribute(trace, targetService,
targetParam)` internally at runtime. The writer-emitted test code
(line 717 of `MultiServiceRESTAssuredWriter.java`) passes the right
arguments. The 1234 attribution histograms in Run 22's report happened
through that path. **Functionally it works**.

What's missing is integration with the existing ablation framework. The
four existing shape invariants (SpanTreeShapeInvariant,
StatusPropagationInvariant, ResponseEnvelopeInvariant,
TimingEnvelopeInvariant) all implement the same `ShapeInvariant`
interface and are toggled per-feature via `AblationProfile`. Attribution
is a fifth invariant family conceptually — it should join.

**Evidence**:
- `find mist-core/src/main/java/io/mist/core/oracle/shape/invariant/` —
  only the four existing classes; no `TargetAttributionInvariant.java`.
- `grep "attribution" mist-core/src/main/java/io/mist/core/config/AblationProfile.java` — empty.
- Commit `7d92298c` message line 7-8 explicitly says: "ShapeInvariant
  integration is part 2, deferred".

**Fix steps**:

1. Create
   `mist-core/src/main/java/io/mist/core/oracle/shape/invariant/TargetAttributionInvariant.java`
   implementing the existing `ShapeInvariant` interface. The class is a
   thin wrapper:
   - Takes `TraceModel`, `targetService`, `targetParam` from the
     verdict-build context (same fields already on
     `TraceShapeVerdict.Builder`).
   - Calls `TraceAttribution.attribute(trace, targetService, targetParam)`.
   - Returns an `InvariantOutcome` whose `failure` flag is true iff the
     verdict is `UPSTREAM_REJECTION` or `WRONG_PARAM_REJECTION` (those
     are the "tool found a real-but-not-on-target rejection" cases),
     false on `TARGET_REJECTION` (test landed) and `NO_ATTRIBUTION`
     (insufficient evidence).
   - Surfaces the attribution verdict in `InvariantOutcome.detail` so
     the existing report-rendering path collapses it like other
     invariants.

2. Register it in `ShapeInvariantStore` (or wherever
   `SpanTreeShapeInvariant` etc. are enumerated). Match the
   `AblationProfile` field-name pattern, e.g.
   `targetAttributionEnabled`.

3. Move the `TraceAttribution.attribute(...)` call from
   `FaultDetectionTracker.recordVerdict` INTO the new invariant. Keep
   `recordVerdict`'s API surface (`AttributionVerdict attribution`
   parameter, anomaly bucket increment) but have `recordVerdict` read
   the pre-computed verdict from the `TraceShapeVerdict` instead of
   computing it.

4. Update `MultiServiceRESTAssuredWriter` writer code: the verdict
   builder now includes attribution as one of the invariant outcomes.
   No change to the recordVerdict signature beyond removing the
   `targetService`/`targetParam` parameters (those are now on the
   verdict).

**Acceptance criteria**:
- New class `TargetAttributionInvariant` exists and implements
  `ShapeInvariant`.
- Disabling `mst.oracle.shape.invariants.target_attribution.enabled`
  via system property: the attribution histograms in the report drop
  to zero AND there is no perf cost (no `TraceAttribution.attribute`
  call happens).
- Existing test `FaultDetectionTrackerAttributionTest` still passes.
- All 246 mist-core + 4 mist-cli + 6 mist-llm tests stay green.
- Run 23 with attribution on: anomaly attribution histograms match
  Run 22 byte-for-byte (modulo timestamps).

**Estimated effort**: 1.5–2 hours (one new class ~80 LOC, two small
edits to `FaultDetectionTracker` and `MultiServiceRESTAssuredWriter`,
one ablation profile field).

---

## F2 — `mst.oracle.shape.invariants.target_attribution.enabled` not in validator allow-list

**Severity**: HIGH (verified)

**Why HIGH**: any user setting this flag (or its `=false` ablation)
triggers a `MstConfig: unknown property '...' (typo?)` warning on every
run. Under `mst.config.strict=true` it aborts the run. Same shape as
the validator-allow-list bug we fixed three times before (commits
`67ca4a97`, `9d8dc7fe`, the validator section of `a5353498`).

**Evidence**: `grep "target_attribution"
mist-core/src/main/java/io/mist/core/config/MstConfigValidator.java`
returns nothing.

**Fix steps**: Add one line to `MstConfigValidator.KNOWN_KEYS` next to
the four existing invariant flags (around line 133-136):

```java
"mst.oracle.shape.invariants.timing.enabled",
"mst.oracle.shape.invariants.target_attribution.enabled",  // NEW (F2)
```

**Acceptance criteria**:
- `grep "target_attribution"
  mist-core/src/main/java/io/mist/core/config/MstConfigValidator.java`
  finds one line.
- A run with
  `-Dmst.oracle.shape.invariants.target_attribution.enabled=false`
  shows no `unknown property` warning.

**Estimated effort**: 5 minutes.

---

## F3 — `TraceAttribution.attribute()` runs unconditionally — no kill switch

**Severity**: HIGH (recalibrated from MEDIUM after seeing F1 path)

**Why HIGH**: today the only way to suppress attribution is to set
`mist.report.oracle.anomalies.enabled=false`, which ALSO hides the Phase
0 anomaly section. There is no per-feature switch. If F1 is done
correctly, this resolves automatically (the new
`TargetAttributionInvariant` is enabled/disabled by its own flag).
Listing it separately so it doesn't get lost if F1 is partially
implemented.

**Evidence**:
`FaultDetectionTracker.recordVerdict` (line ~225) computes
`TraceAttribution.attribute(...)` whenever trace+targetService are
non-null, with no config check.

**Fix steps**: implemented as part of F1 — the new
`TargetAttributionInvariant`'s `enabled` flag becomes the kill switch.
After F1: `recordVerdict` no longer calls `TraceAttribution.attribute()`
itself; it just consumes the pre-computed verdict that the (possibly
disabled) invariant provided.

**Acceptance criteria**: same as F1's acceptance criteria.

**Estimated effort**: 30 minutes (subsumed by F1 if done together).

---

## F4 — No per-run TraceModel cache

**Severity**: MEDIUM (verified)

**Why MEDIUM, not HIGH**: correctness is fine; cost is wasted CPU and
Jaeger query bandwidth. Design doc called for an in-memory
`Map<String, TraceModel>` per run-id. Current code rebuilds
`TraceShapeAdapter.toModel(globalBestTrace, rootApiKey)` at
`MultiServiceRESTAssuredWriter:680` per step. For a multi-step test
with N steps that share a trace, this is N× the work.

**Evidence**:
- `grep -rn "Map<.*TraceModel\|cache.*Trace"
  mist-core/src/main/java/io/mist/core/oracle/` returns only the
  internal `childrenByParent` map inside LeafErrorSpanFinder — not a
  cross-call cache.
- Design doc `phase_2_trace_attribution.md` line 160 mandates one.

**Fix steps**:

1. Add a static `ConcurrentHashMap<String, TraceModel>` to
   `TraceShapeAdapter` keyed by `(testRunId, traceId)`. JVM-local;
   cleared at run end.
2. `toModel()` checks the cache first; returns the cached entry if
   present.
3. At the end of the run, the cache is cleared by `MistRunner.run()`
   to avoid leaking across runs.

**Acceptance criteria**:
- Run 23 trace-build counter (add a `log.debug` counter) shows
  ~1 build per unique trace, not 1 build per step.
- Wall-time impact: < 1% (cache lookups are O(1)).
- All existing tests stay green.

**Estimated effort**: 1 hour.

---

## F5 — Synchronous in-test trace pull

**Severity**: MEDIUM (verified, but recommend deferring)

**Why MEDIUM**: not a correctness issue; design doc says "trivial,
~1-2 min wall-time per run" — confirmed by Run 22's actual numbers (no
visible attribution-related wall-time penalty until the DeepSeek/Ollama
fallback dominated everything). Async is a nice-to-have, not a need.

**Recommendation**: defer to a perf-only follow-up commit. Do not bundle
with F1-F3.

**Evidence**:
`grep "CompletableFuture\|@Async\|ExecutorService.*trace"
mist-core/src/main/java/io/mist/core/oracle/attribution
mist-cli/src/main/java/io/mist/cli/writer/MultiServiceRESTAssuredWriter.java`
returns nothing.

**Fix steps (when ready)**: wrap the trace pull inside `attachJaegerTrace`
in a `CompletableFuture`, batch on test-class end via the existing
`testRunFinished` listener. Requires shared executor scoped to the test
run.

**Estimated effort**: 2-4 hours.

---

## F6 — Missing unit test: parallel leaf-error spans in sibling branches

**Severity**: LOW (verified)

**Why LOW**: the algorithm is greedy DFS and returns the deepest leaf
across all error chains. With two parallel error branches at equal
depth, the algorithm picks one arbitrarily. Worst case the chosen leaf
is in a sibling service → `UPSTREAM_REJECTION` (still classified as
"not our target"). Functionally correct, but the test suite doesn't
exercise this code path.

**Evidence**:
`TraceAttributionTest.java findLeafError_descendsErrorChain` tests a
linear chain only. No `findLeafError_picksDeepestAmongParallelErrors`
test exists.

**Fix steps**: add one test case that constructs a trace with two
sibling spans both having `status=ERROR`, asserts that one of them is
returned (whichever) and that the verdict is sensible.

**Acceptance criteria**: new test passes; existing test suite stays
green.

**Estimated effort**: 20 minutes.

---

## F7 — `ConsoleProgressBar` state not explicitly reset between phases

**Severity**: LOW (downgraded from MEDIUM)

**Why LOW**: re-verified via grep — `ConsoleProgressBar` exposes only
`banner / begin / update / complete`. Each `begin()` starts a fresh
bar. No global counter or state that leaks between phase A and phase B.
The reviewer's MEDIUM rating was conservative-by-default; in practice
there's no observable bug.

**Fix steps (optional)**: add `ConsoleProgressBar.complete()` at the
end of `runPhaseA` to be explicit, even though the next `begin()` call
in `runPhaseB` would do the right thing anyway.

**Acceptance criteria**: terminal output during a `mst.two.phase.enabled=true`
run shows two cleanly separated bar lifecycles ("Phase A" complete,
then "Phase B" begin).

**Estimated effort**: 15 minutes.

---

## F8 — 4 invariants don't call `recordOracleAnomaly` directly

**Severity**: NIT (design refinement, not a bug)

The design doc (phase_0_oracle_credit.md, the "Recording path" section)
showed each invariant directly calling `recordOracleAnomaly`. The
implementation instead routes through `recordVerdict(verdict, …)` which
iterates the verdict's invariant outcomes and calls
`recordOracleAnomaly` per failing outcome. Commit `e8146c08`'s message
justifies this: invariants don't know their test-identity context, only
the verdict-consumption site does.

**Action**: none. Leave as-is and update the design doc to match the
implementation. Update at:
`debug/negative_test/phase_0_oracle_credit.md` "Recording path" section
— say "via `TraceShapeVerdict` outcomes which `FaultDetectionTracker.
recordVerdict` then routes to `recordOracleAnomaly`" instead of the
direct-call pattern.

**Estimated effort**: 5 minutes (doc tweak only).

---

## Recommended order

1. **F1 + F2 + F3 in one commit** (~2.5 h, single PR).
   Title: `feat(attribution): TargetAttributionInvariant + ablation flag (Phase 2 part 2)`.
2. **F8 doc update** in same commit (free, the doc already lives
   in-repo).
3. F4 in a follow-up perf commit (~1 h).
4. F6 in the same perf commit or a separate test-only commit (~20 min).
5. F7 only if cosmetic complaints surface.
6. F5 deferred to its own perf commit when LLM/Jaeger latency budget
   actually matters (currently not the bottleneck).

After F1-F3 land, all three phase audits become full ACCEPT.
