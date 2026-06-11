# Independent Verification Brief — MIST negative-test work line

> Hand this whole file to a fresh session. It has **no context** from the
> work it is checking, which is the point: verify the claims below from
> the source and artifacts alone. **Do not trust this brief's claims — try
> to falsify them.** A claim is only ACCEPTED when you have reproduced the
> evidence yourself (a passing test, a grep hit at a cited line, a report
> you read). "The commit message says so" is not evidence.

---

## Your mission

A multi-commit work line on branch `inject-detection` claims to have taken
the MIST microservice API-testing tool from **7/10 to 10/10 fault
detection** on the train-ticket benchmark, plus shipped three follow-up
"phases" (oracle-anomaly reporting, two-phase pool validation, Jaeger
trace attribution). Decide, with evidence, whether each headline claim
holds and whether anything is broken, overstated, or regressed.

Be adversarial. Where a claim depends on a test, also read the test and
ask whether it actually exercises the claim or just asserts something
trivial. Where a claim is "backward compatible behind a flag", actually
flip the flag and check.

Output a disposition table (format at the end). Keep prose tight.

---

## Environment

- Repo root: `/home/tingshuo_miao2/github/Rest` (a git worktree of the same
  repo also exists under `.claude/worktrees/`; either is fine — work from
  a clean checkout of branch `inject-detection`).
- Branch under test: **`inject-detection`**, HEAD should be
  `a59044e9` (`refactor(attribution): delete 8-arg recordVerdict + inline
  fallback (F3 kill-switch hardening)`). Confirm with `git log -1`.
- Modules: `mist-core`, `mist-cli`, `mist-llm` (Maven reactor).
- Build: `mvn -B -pl mist-core test` for the unit suite (fast). Full
  reactor: `mvn -B test`. JDK 21.
- You do NOT need a running SUT or LLM keys for any claim below — every
  check is static (read/grep) or a unit test. The end-to-end run artifacts
  are already captured in `debug/negative_test/runs/`.

### The work-line commits (base = `457b2faa`)

```
a59044e9 refactor(attribution): delete 8-arg recordVerdict + inline fallback (F3)
e6803225 perf(attribution): TraceModel cache + parallel-error test + phase-bar (F4+F6+F7)
b8e9ca4c feat(attribution): TargetAttributionInvariant + ablation flag (Phase 2 pt2; F1+F2+F3+F8)
18be1694 docs: FIXES.md (audit fix-list)
19c0bf3c docs: attach Run 21.b + Run 22 fault detection reports
c6b7290f feat(runner): strict two-phase positive/negative flow (Phase 1 pt2)
4ae62525 feat(report): trace-attribution buckets in ORACLE ANOMALIES (Phase 2 pt3)
4d573b55 fix(pool): scope recordParameterSuccess to per-step params
7d92298c feat(attribution): leaf-error-span trace attribution (Phase 2 pt1)
e24b26d3 feat(pool): SUT-verified pool status (Phase 1 pt1)
e8146c08 feat(report): credit trace-shape oracle violations (Phase 0)
63d0126e docs: phase 0/1/2 plan
72521685 fix(runner): restore failure capture in parallel test execution
a5353498 feat(cache): signature-keyed reproducibility caches + master CacheToggle
e1c3dc2f fix(adaptive): enforce variantBudget cap (POST/PATCH=50)
```

(`b9d8f2e8`, `3a2a2178`, `7eba0744` interleaved in the log are unrelated
subproject/README docs — ignore.)

---

## Claims to verify

### C1 — Build is green; the whole unit suite passes
- Run `mvn -B -pl mist-core test` (and `-pl mist-cli`, `-pl mist-llm` if
  time permits). Report exact `Tests run: N, Failures: F, Errors: E`.
- ACCEPT only if F=0 and E=0. Note any flaky/skipped.

### C2 — Run 22 report genuinely shows 10/10
- Read `debug/negative_test/runs/run22-fault-detection-10of10.txt`.
- Confirm header says `Detected Faults: 10 (100.0%)`,
  `Undetected Faults: 0`.
- Confirm all ten named faults appear in the DETECTED section and the
  UNDETECTED section is empty.
- Cross-check the three that the 7/10 baseline missed are now present:
  `INVALID_CONTACTS_NAME`, `INVALID_STATION_NAME`,
  `INVALID_STATION_NAME_LENGTH`. Compare against
  `debug/negative_test/runs/run21b-fault-detection-7of10.txt` (which
  should list those three under UNDETECTED).
- Adversarial: do the detection counts look plausible (each fault ≥ 1
  detection event with a concrete test-method + timestamp), or are any
  faults "detected" with zero evidence rows?

### C3 — The parallel-listener fix is correct (this is what unblocked 7→10)
Commit `72521685`. The prior state (commit `e3d7e4b6`, before this work
line) left `Enhanceable Failures: 0` because the failure-capture listener
was not attached to per-task JUnitCores. Verify the three-part fix:
- `mist-core/.../enhancer/TestResultCapture.java` — `enableCapture()` is
  `synchronized` and idempotent (clears the map ONLY on the
  disabled→enabled transition). Read it.
- `mist-cli/.../cli/MistRunner.java` — in the **parallel** branch of
  `executeTestsWithCollector`, the shared `FailedTestCollector` is added
  as a listener to **each** per-task `JUnitCore` (`local.addListener(...)`).
- `mist-core/.../enhancer/FailedTestCollector.java` —
  `drainFromTestResultCapture()` is idempotent via a **per-instance**
  `Set<String> drainedKeys` and uses `getResultsSnapshot()` (NOT
  `disableCaptureAndGetResults()`), so calling it once per parallel
  JUnitCore does not (a) drop later tasks' results by disabling capture
  early, nor (b) double-count a test.
- **Adversarial checks**:
  - Is `drainedKeys` an instance field (correct) or `static` (would leak
    across rounds and silently drop round-1 failures)?
  - With the idempotent `enableCapture`, who finally disables capture, and
    can a round-2 `enableCapture` wrongly preserve round-1 results?
  - Trace one failing test end-to-end: AssertionError →
    `testFailure` → `markTestFailed` → captured → drained → enhanceable.
    Is there any gap?

### C4 — Canonical-key dedup is sound (not over-collapsing)
Commit `967e5a2e` + test `TestCaseEnhancerDedupTest`.
- Run the test. Read it: does it actually pin that 100 variants of the
  same scenario collapse to ONE key, AND that distinct method / endpoint /
  status / invalid-param-names / response-body / schema each change the
  key?
- Adversarial: construct (on paper) two negative tests targeting
  **different** parameters that would hash to the **same** canonical key.
  If you can, that's a REJECT (dedup would drop a real distinct test).
- The "2.0× reduction / 4,560 LLM calls saved" figure is from Run 22 logs,
  not reproducible here — mark it "reported, not re-verified" unless you
  find the figure in a committed artifact.

### C5 — variantBudget cap (the fix that stopped the 32 MB-file javac OOM)
Commits `e1c3dc2f`. Test `EndpointPolicyResolverTest`.
- POST and PATCH resolve to `variantBudget == 50`; GET/HEAD/OPTIONS/
  PUT/DELETE and `EndpointPolicy.LEGACY` resolve to `-1`.
- `MistGenerator` actually enforces the cap (find the
  `policy.variantBudget()` consumption site; confirm `variantCount` is
  clamped).
- Adversarial: when `mst.adaptive.enabled=false`, the policy is LEGACY
  (budget −1) so NO cap applies — confirm the cap can't silently change
  non-adaptive (legacy-byte-identical) runs.

### C6 — Master CacheToggle is the single trigger for the 3 new caches
Commit `a5353498`. Test `CacheToggleTest`.
- `mst.cache.read` / `mst.cache.write` default true; four-state matrix
  (read×write) behaves as documented.
- Adversarial — the important one: do the THREE caches actually consult
  `CacheToggle`, or do they just claim to? grep each for `CacheToggle`:
  - `LLMStatusCodeDiscovery` (status-code discovery cache)
  - `StatusCodeExplorationEnhancer` (exploration-suggest cache)
  - `ZeroShotLLMGenerator` (LLM-validation cache)
  Confirm each guards both its read path and its write/persist path with
  `CacheToggle.canRead()` / `canWrite()`.
- Confirm cache **keys** are signature-based (normalized path + schema /
  response-body fingerprint), NOT raw prompt hash — that's the whole point
  (reproducibility). Read `LLMStatusCodeDiscovery.normalizePath` and its
  key builder; sanity-check it collapses `…/account/x`,
  `…/account/%00%01%02`, `…/account/FALLBACK_id_3` to the same key.

### C7 — Phase 0: oracle anomalies surfaced in the report
Commit `e8146c08`. Test `FaultDetectionTrackerOracleAnomalyTest`.
- `recordOracleAnomaly` / `OracleAnomaly` exist; anomalies dedup by
  `(oracle, endpoint, violation-fingerprint)` with a hit counter and a
  first-seen sample that is never overwritten.
- Report renders an ORACLE ANOMALIES section; gated by
  `mist.report.oracle.anomalies.enabled` (default true).
- Note (already known, confirm not re-broken): the four invariants do NOT
  call `recordOracleAnomaly` directly — recording is routed through
  `FaultDetectionTracker.recordVerdict(...)` over the verdict's failing
  outcomes. Confirm that path records exactly the failing outcomes (and
  skips the `TARGET_ATTRIBUTION` diagnostic outcome — see C9).
- Adversarial: with `mist.report.oracle.anomalies.enabled=false`, does the
  report omit the section (and is the rest byte-identical to legacy)?

### C8 — Phase 1: two-phase flow + SUT-verified pool
Commits `e24b26d3` + `4d573b55` + `c6b7290f`. Tests
`InputFetchRegistryPoolStatusTest`, `MistGeneratorTwoPhaseTest`.
- `PoolEntryStatus { UNVERIFIED, VERIFIED_VALID, REJECTED_BY_SUT }` and a
  state machine where **VERIFIED is never demoted** by a later reject, but
  a REJECTED entry is promoted when the SUT later accepts. Confirm via the
  test and by reading `InputFetchRegistry`.
- YAML round-trips the status; a legacy YAML with no status field loads as
  `UNVERIFIED`.
- `4d573b55` is a correctness fix: `recordParameterSuccess` is scoped to
  the **current step's** params (`stepParams<N>`), not the cumulative
  `allStepParameters`. Confirm the writer emits per-step maps — otherwise
  step-2 params get wrongly credited to step-1's endpoint.
- `MistRunner` gates two-phase on `mst.two.phase.enabled`; the original
  single-phase body is preserved as `runSinglePhasePipeline` for the
  flag=false path. `MistGenerator.resetForNewPhase()` clears per-phase
  dedup state (bandit, seenPayloads) so the second `generate()` is not
  poisoned by the first.
- Adversarial: with `mst.two.phase.enabled=false` (default), is the run
  path identical to pre-Phase-1? Does anything in `recordParameterSuccess`
  / pool-status execute on the legacy path and change behavior?

### C9 — Phase 2: Jaeger leaf-error attribution + the F3 kill-switch
Commits `7d92298c` (algo) + `4ae62525` (report buckets) + `b8e9ca4c`
(invariant + flag) + `a59044e9` (F3 hardening). Tests
`TraceAttributionTest`, `TargetAttributionInvariantTest`,
`FaultDetectionTrackerAttributionTest`.
- `LeafErrorSpanFinder` implements the Jha CLOUD'22 idea: walk only into
  error-tagged children, return the **deepest** error span. Read it; check
  the parallel-error tests added by `e6803225`
  (`findLeafError_picksDeepestAmongParallelErrors_FIXES_F6`,
  `…_parallelErrorsAtEqualDepth_picksOne_FIXES_F6`).
- `TraceAttribution.attribute` returns one of NO_ATTRIBUTION /
  UPSTREAM_REJECTION / WRONG_PARAM_REJECTION / TARGET_REJECTION per the
  service/param match.
- `TargetAttributionInvariant` implements the same `ShapeInvariant`
  interface as the other four and is gated by
  `mst.oracle.shape.invariants.target_attribution.enabled`.
- **The F3 kill-switch — verify the hardening actually closed it.**
  `a59044e9` claims to have DELETED the 8-arg `recordVerdict` overload and
  the inline `TraceAttribution.attribute(...)` fallback inside
  `FaultDetectionTracker`. Confirm by grep:
  - `grep -n "TraceAttribution.attribute" mist-core/.../analysis/FaultDetectionTracker.java`
    should return **zero** hits (attribution now only computed inside the
    invariant).
  - The only remaining `TraceAttribution.attribute` call site should be
    `TargetAttributionInvariant`.
  - With `…target_attribution.enabled=false`: the invariant must not run,
    so NO attribution is computed anywhere and the report's attribution
    buckets stay empty. Confirm there is no second path that recomputes it.

### C10 — Validator allow-list is complete (no startup typo-warnings)
Every new system property must be in
`mist-core/.../config/MstConfigValidator.java` `KNOWN_KEYS`, or it warns
(and aborts under `mst.config.strict=true`). grep-confirm each is present:
- `mst.enhancer.parallelism`, `.dedup.negative`, `.cache.enabled`, `.cache.path`
- `mst.cache.read`, `mst.cache.write`
- `mst.status.code.discovery.cache.path`,
  `mst.exploration.suggest.cache.path`, `mst.llm.validation.cache.path`
- `mist.report.oracle.anomalies.enabled`
- `mst.two.phase.enabled`
- `mst.oracle.shape.invariants.target_attribution.enabled`

---

## Known state — confirm, don't "discover"

These are already understood. Confirm they are as described; flag only if
they're WORSE than stated.

1. **F5 deferred**: trace pull is synchronous in-test, not batched/async.
   `FIXES.md` deliberately defers this (perf, not correctness). Expect no
   async code.
2. **Runtime-only workarounds (gitignored, not in the tree)**: a full
   end-to-end run needs (a) ~51 Jaeger trace files under
   `mist-cli/src/main/resources/My-Example/trainticket/test-trace/` and
   (b) `MultiServiceTestCase*.class` copied into `mist-cli/target/classes/
   io/mist/core/testcase/` to satisfy a fast-compile spot-check in
   `MistRunner` (a known P3: the spot-check looks in mist-cli's target,
   but the class lives in mist-core). Neither is needed for static review
   or unit tests. If you attempt a live run and it falls back to a slow
   all-at-once Maven compile, that spot-check is why.
3. **Run 22 wall-time (~6.5 h)**: the last ~2 h ran on the Ollama LLM
   fallback after the DeepSeek balance hit HTTP 402. This is a cost/time
   artifact, not a correctness issue; the 10/10 was already reached before
   the fallback. The signature caches (`.mist/*.json`) mean a re-run
   should be far faster.
4. **`docs(...)` commits** in the log are planning artifacts in
   `debug/negative_test/` — not code to verify.

---

## What would make you REJECT or downgrade

- Any unit test fails or is silently `@Ignore`d.
- C3: `drainedKeys` is static, or there is a path where `markTestFailed`
  is dropped under parallelism → the 7→10 result would be non-reproducible.
- C4: you find two genuinely-distinct negative tests that collapse to one
  canonical key.
- C6: a "new cache" doesn't actually consult `CacheToggle`, or its key is
  still a raw prompt hash (would defeat reproducibility).
- C9: `grep` finds `TraceAttribution.attribute` still in
  `FaultDetectionTracker` (F3 not actually closed), or attribution runs
  when its flag is false.
- C8: flag=false path is NOT byte-identical to pre-Phase-1 behavior.
- C10: any listed key missing from `KNOWN_KEYS`.
- C2: a fault is counted "detected" with no concrete detection rows.

---

## Output format

For each claim C1–C10:

```
Cx — <ACCEPT | PARTIAL | REJECT>
  Evidence: <command run / file:line read / test result>
  Notes:    <only if PARTIAL/REJECT, or a real new finding>
```

Then a one-paragraph overall verdict: is the 7→10 result trustworthy and
reproducible, are Phases 0/1/2 sound, and is anything overstated?

Add an "UNEXPECTED FINDINGS" section for anything outside C1–C10 you trip
over (new bug, dead code, security issue, misleading log/report text).
Cite file:line for each. Do not fix anything — report only.
