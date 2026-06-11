# MIST — Critical Architecture Fixes (S-tier + A-tier issues 1-6)

> **Audience.** This document is a self-contained execution brief for an
> agent (Claude Code or equivalent) that will land seven surgical fixes on
> the MIST tool. The agent has read access to the whole repo and write
> access via `Read`/`Edit`/`Write`/`Bash`. **Do not deviate from this brief.**

---

## 0. Rules of engagement (read this first)

### 0.1 Branding
- User-facing name is **MIST** (Microservice Integration & Scenario Tester).
- Code-internal name stays **MST** (class names like `MstAuthHandler`,
  `MultiServiceTestCaseGenerator`; property keys like `mst.config.path`;
  generator enum value `MST`). The two-layer brand strategy is locked
  in by commit `51266a4b` — do **not** rename anything in code, config,
  or property files.
- Documentation files you create may use `MIST` (the brand).

### 0.2 Branch and base
- **Base your work on `origin/inject-detection`** (the active development
  branch). The auth + loop-streak fixes from commit `67337db9` are in
  this branch and **must not be reverted**.
- Create one fix-specific branch per fix:
  `claude/fix-mst-<NN>-<short-slug>` (e.g. `claude/fix-mst-01-phase-pipeline`).
- Land each fix as its own PR-shaped branch. Do **not** combine fixes.
- Sequence is fixed (see § 0.6). Skipping ahead breaks downstream fixes.

### 0.3 Scope boundaries
You may modify code under:
- `src/main/java/es/us/isa/restest/workflow/**`
- `src/main/java/es/us/isa/restest/generators/MultiServiceTestCaseGenerator.java`
- `src/main/java/es/us/isa/restest/generators/AiDrivenLLMGenerator.java`
- `src/main/java/es/us/isa/restest/generators/ZeroShotLLMGenerator.java`
- `src/main/java/es/us/isa/restest/inputs/InvalidInputPool.java`
- `src/main/java/es/us/isa/restest/inputs/smart/**`
- `src/main/java/es/us/isa/restest/llm/**`
- `src/main/java/es/us/isa/restest/configuration/MstConfig*.java` (new)
- `src/main/resources/My-Example/trainticket/**` (config files only)
- `src/test/java/es/us/isa/restest/{workflow,generators,inputs,llm}/**` (new tests)

You **may not** modify:
- Any class **not** prefixed `Mst`, **not** under `workflow/`, **not**
  under `inputs/smart/`, and **not** in the explicit allow-list above.
  These are upstream RESTest classes; touching them is out of scope.
- Anything under `src/test/java/trainticket_twostage_test/**`
  (auto-generated test cases).
- Any `*.tex`, `*.bib` file (paper layer).
- `README.md` (brand layer).

### 0.4 Tooling
- Use `Read` / `Edit` / `Write` over `cat`/`sed`/`awk`.
- For every fix, run `mvn -q -DskipTests compile` after edits to verify
  the codebase still builds. If `compile` fails, **stop and fix the
  compile error before continuing**.
- After all seven fixes land, run `mvn -q test` once. New tests added by
  this brief must pass; pre-existing failures are documented in
  § 9 and may be tolerated only if they predate your branch.
- Invoke the `simplify` skill on every fix before committing it.
  This is the user's standing instruction. Use the skill to double-check
  that the change is minimal, that no unused code was added, and that
  no dead error-handling sneaked in.

### 0.5 Forbidden patterns ("don't")
1. **No new features.** Every fix below is a refactor or a correctness
   patch. If you find yourself adding a property, a class, or a method
   that is not explicitly enumerated here, stop and check the brief.
2. **No backwards-compatibility shims.** Don't keep deprecated
   `applySingleRootDedup` after Fix S-3; delete it. Don't re-export
   removed symbols.
3. **No comments that restate code.** Per the user's coding standard,
   only add comments when the *why* is non-obvious.
4. **No `_v2` / `_old` / `_legacy` duplicate classes.** Delete or replace.
5. **No new system properties** unless explicitly added by Fix A-6
   (which *consolidates*, not expands, the property surface).
6. **No LLM-based generation in the verification step.** Tests must be
   deterministic.
7. **No ablation infrastructure.** Ablation is deferred to Path B
   (see `PATH_B_REBUILD_PLAN.md`). Fix S-1 produces clean
   pipeline boundaries; it does **not** add `--disable-phase-N` flags.
8. **No evaluation extensions.** Don't add new SUTs, new baselines, new
   metrics. That work belongs to Path B's future tasks.

### 0.6 Required execution order

```
Fix S-4 (LLM determinism)    ─┐
                              ├─> Fix S-2 (semantic registry config)
Fix A-6 (MstConfig POJO)     ─┘                                    │
                                                                   ▼
                                                          Fix S-3 (dedup correctness)
                                                                   │
                                                                   ▼
                                                          Fix S-1 (phase pipeline)
                                                                   │
                                                                   ▼
                                                          Fix A-5 (Sniper coverage)
                                                                   │
                                                                   ▼
                                                          Fix A-7 (cache .mist/ migration)
```

**Rationale.** S-4 and A-6 are isolated infrastructure (LLM cache, config
POJO) with zero dependencies — land them first to stabilise the surface.
S-2 reads the new config (A-6 first). S-3 (dedup correctness) must
precede S-1 (phase extraction) because S-1 will lift the dedup code
into its own phase class, which is only safe once the dedup itself is
correct. A-5 (Sniper coverage) edits the new phase boundary, so it must
come after S-1. A-7 (cache `.mist/` migration) is independent of every
other fix on the code level, but lands last because (a) it needs the
`/.mist/` gitignore entry added by S-4, and (b) it adjusts default
values inside `MstConfig.Llm` and related sub-records that A-6
introduces.

### 0.7 Per-fix commit policy
- Each fix lands as exactly one commit (plus its test commit if a test
  is added separately).
- Commit message body lists the symbols touched and the acceptance
  criteria that pass.
- Do **not** push to `inject-detection`. Push the fix branch and stop;
  the user will review and merge.

---

## Fix S-4 — LLM determinism (LAND FIRST)

### Problem
`AiDrivenLLMGenerator` (and the `LLMService` it wraps) call out to
Ollama / Gemini / OpenAI-compatible endpoints with no on-disk caching
and no fixed sampling seed. Two consequences:
- Re-running the test generator on the same trace + same OpenAPI spec
  produces different test bodies, so reported numbers are not
  reproducible.
- A reviewer asked to reproduce results will see drift even on the
  same hardware.

Note: a `random.seed` system property is already honoured by
`SeededRandom.create(scope)` for Java-side randomness (see flow.md
§ "Reproducibility"). The gap is the LLM side.

### Why the existing `LLMCommunicationLogger` does NOT solve this

The codebase already has
`src/main/java/es/us/isa/restest/util/LLMCommunicationLogger.java`,
which writes every request + response to a per-session text file under
`logs/llm-communications/llm-communication-{timestamp}.log`. **This is
a forensic log, not a cache.** Five concrete reasons it cannot be
repurposed:

1. **Output format is human-readable, not machine-replayable.**
   Records are delimited by emoji-decorated banner lines (`🚀 LLM
   REQUEST #N` / `🎯 LLM RESPONSE #N` / `---...---`). Prompts and
   responses are free-form text; the moment a prompt contains a `---`
   in its body (common in OpenAPI descriptions), a parser breaks.
2. **Content is truncated.** `maxContentLength` defaults to 10 000
   chars (line 68 of the logger). Any prompt longer than that gets a
   `... [TRUNCATED]` marker, and the hash of the truncated prompt is
   not the hash of the original — a cache built on this would
   miss on every truncated entry.
3. **No read path exists.** The logger only writes (`logWriter.println`,
   `logWriter.flush`). Nothing in the codebase reads these files. To
   turn them into a cache, you would have to add a parser + an
   in-memory index + a lookup path — i.e. write the cache anyway, just
   with a worse on-disk format.
4. **Per-session files, no cross-run sharing.** Each JVM start creates
   a new timestamped file (line 277-278 of the logger). Run 1 records
   its 200 LLM calls into file A; Run 2 starts file B empty — even if
   you wanted to replay, the data is in the wrong file.
5. **Even with a perfect log, you still cannot replay.** The logger
   records "what happened" but does not intercept "what will happen
   next". Two runs with the same `random.seed` but different LLM
   non-determinism still diverge — the log just documents the
   divergence; it does not prevent it.

**The logger stays as-is.** S-4 adds `LLMCallCache` as a parallel
mechanism with a different purpose. Logger = forensics; cache =
determinism. They write to different files; they do not interact.

### Why hosted APIs cannot be made strictly deterministic on the client side

Three of MIST's four backend options have different determinism
guarantees:

| Backend | `temperature=0` honoured? | `seed` parameter honoured? | Server-side variance |
|---|---|---|---|
| **Ollama** (local) | ✅ yes | ✅ yes (`options.seed`) | None — you own the weights |
| **OpenAI-compatible** (DeepSeek, OpenAI, OpenRouter, ...) | ✅ yes | ⚠️ OpenAI's `seed` is documented as "best-effort, not strictly deterministic"; DeepSeek accepts the field with the same caveat; pure-proxy services (OpenRouter) may strip it | Model versioning, request routing, KV-cache batching — all can produce different tokens even with `temperature=0` |
| **Gemini** | ✅ yes | ⚠️ `generationConfig.seed` added mid-2024 with the same "may produce different outputs across runs" disclaimer | Same as above |

In short: **on hosted APIs you can tell the server `temperature=0,
seed=42`, but you cannot make the server obey strictly.** This is the
provider's choice, not a bug in your code, and it cannot be fixed at
the client layer.

**`LLMCallCache` exists precisely to sidestep this.** Once a prompt's
response is in the cache, the cache is the source of truth — the
backend is not called again, so the backend's non-determinism is
irrelevant for cached entries.

### Design: the "reproducibility switch"

The master toggle is the JVM system property `-Drandom.seed=<long>`.
It is **off by default**, because day-to-day development does not need
byte-determinism, and forcing every dev run through the cache would
prevent exploring fresh LLM responses.

When the switch is **off** (`random.seed` unset):
- `LLMService.generateText` calls the backend exactly as today.
- Every (model, backend, prompt, temperature, max_tokens) → response
  pair is **written** to `LLMCallCache` opportunistically, so that
  the next person who turns the switch on benefits from this run's
  work.
- The cache is never read. Dev runs are non-deterministic by design.

When the switch is **on** (`-Drandom.seed=42` or similar):
- Before every backend call, `LLMService.generateText` consults
  `LLMCallCache` using the same key.
- **Cache hit** → return the cached response. Backend is **not**
  called. This is what makes the second run byte-identical to the
  first.
- **Cache miss** → call the backend with `temperature` forced to `0.0`
  (regardless of what `LLMConfig.getTemperature()` would otherwise
  return), `seed` passed if the backend supports it, then store the
  response and return it.
- `SeededRandom.create(scope)` (already in the codebase) covers the
  Java-side RNG. Combined with the cache, this closes the
  determinism loop.

The switch must be a **single point of control**. No separate
`mist.llm.cache.enabled` property, no `--reproducible-mode` flag.
The presence of `random.seed` is the toggle. Adding a second toggle
creates four states; one toggle creates two.

### Operating modes (the 2×2 matrix the executing agent must implement)

|   | Cache file does NOT exist on disk | Cache file exists on disk |
|---|---|---|
| **`random.seed` unset** (dev) | Backend is called for every prompt. Each response is **written** to a freshly-created cache file. Run output may differ between consecutive runs (LLM non-determinism). | Backend is called for every prompt. Each response **overwrites** the cache entry if the key matches; new keys are appended. Run output may differ between consecutive runs. |
| **`random.seed` set** (reproducible mode) | First seeded run = warm-up. Backend is called for every prompt; responses populate the cache. Run output is whatever the backend returned this time. | **Cache hits short-circuit backend calls.** Every key already in the cache returns its stored response. New keys (e.g. because you changed the OpenAPI spec) hit the backend, get cached, and on the next seeded run also short-circuit. Run output is byte-identical to whatever produced the cache entries. |

The bottom-right quadrant is what makes artifact reproducibility
possible: ship the cache file with the artifact, set `random.seed`,
run.

### Workflow for artifact / paper reproducibility

This is the workflow the executing agent must enable but not
*execute* — execution is a future user-driven step at publication
time:

1. **Blessed machine, blessed run** (you, once). On the machine you
   intend to declare as the canonical environment in the paper, run
   the demo with `-Drandom.seed=42`. The cache populates from
   scratch into `.mist/llm-call-cache.json` at the repo root.
   Inspect `target/test-cases/` and confirm the generated tests are
   sensible.
2. **Un-gitignore `.mist/`.** The fix's `.gitignore` patch adds
   `/.mist/` so day-to-day development does not accidentally commit
   the cache. For artifact submission, **remove that line** (or use
   a targeted negation like `!/.mist/llm-call-cache.json`) so the
   cache file becomes trackable:
   ```
   # before:    /.mist/
   # after:     # (deleted, or:)
   #            !/.mist/llm-call-cache.json
   ```
   **No `cp` is needed.** The cache is already in the repo root; you
   are only changing whether git tracks it.
3. **Commit the cache file.** `git add .mist/llm-call-cache.json &&
   git commit -m "data: bundle blessed LLM cache for artifact"`.
   This is data, not code. Treat it like the bundled traces or the
   injected-faults JSON — it is part of the reproduction package.
4. **Reviewer reproduction.** A reviewer pulls the repo, sets the
   same `-Drandom.seed=42`, runs the demo. Their LLM calls all hit
   the cache, so they get byte-identical generated tests — even if
   their LLM backend is offline, mis-configured, or returns different
   tokens than yours did.
5. **Cache refresh on intentional change.** If you change a prompt
   (e.g. update Smart Fetch's discovery prompt), the SHA-256 key
   changes, so the old cache entry is a miss. The next seeded run
   re-fills it. To force a full rebuild: `rm
   .mist/llm-call-cache.json`.

**Why not `target/`?** The first draft of this fix put the cache in
`.mist/llm-call-cache.json`. That was wrong on two counts: (a) the
project's `.gitignore` already excludes `/target`, so the file would
not have been committable anyway, and (b) the documented Quick Start
command is `mvn clean install -DskipTests`, which would wipe the
cache before every fresh build. `.mist/` is outside Maven's clean
target and is a conventional dotdir for tool state.

### Files
- `src/main/java/es/us/isa/restest/llm/LLMService.java` (edit)
- `src/main/java/es/us/isa/restest/llm/LLMCallCache.java` *(new)*
- `src/main/java/es/us/isa/restest/llm/LLMConfig.java` (edit:
  `getTemperature()` becomes seed-gated)
- `.gitignore` (edit: add `/.mist/` so the dev-mode cache is not
  accidentally committed; users will remove this line manually when
  preparing an artifact bundle)
- The pre-existing
  `src/main/java/es/us/isa/restest/util/LLMCommunicationLogger.java`
  is **not** modified by this fix.

### Surgical change
1. **Create `LLMCallCache`** (~150 LOC, single file).
   - File-backed JSON, one object keyed by SHA-256 of
     `(modelType || modelName || backend || systemPrompt ||
     userPrompt || temperature || maxTokens)`. All eight fields go
     into the hash; the cache key collides only when all eight are
     identical.
   - Value is the model's raw text response string.
   - On startup, read the cache file once into a
     `ConcurrentHashMap<String, String>`. Loading errors (file
     corrupt, JSON malformed) **must** throw a fatal exception at
     startup — silent fallback to an empty cache would let a
     corrupted artifact go undetected.
   - Writes go to a `.tmp` sibling first, then atomic rename, to
     avoid leaving a half-written file on a JVM crash.
   - Writes are debounced: batch multiple `put` calls into one flush
     per N seconds (default 5) to avoid hammering the filesystem
     when 1000+ LLM calls fire in a generation pass.
   - Thread-safe (it sits behind a `synchronized` write path; reads
     are lock-free against the `ConcurrentHashMap`).
2. **In `LLMService.generateText`**, wrap the existing backend
   dispatch (`GeminiApiClient` / OpenAI-compatible HTTP /
   `OllamaApiClient`):
   ```java
   String seed = System.getProperty("random.seed");
   String key = LLMCallCache.key(modelType, modelName, backend,
                                 systemPrompt, userPrompt,
                                 temperature, maxTokens);
   if (seed != null) {
       String hit = cache.get(key);
       if (hit != null) return hit;       // short-circuit, no backend call
   }
   String response = dispatchToBackend(...);  // existing code
   cache.put(key, response);                  // always write
   return response;
   ```
3. **In `LLMConfig.getTemperature()`**, gate by `random.seed`:
   ```java
   public double getTemperature() {
       if (System.getProperty("random.seed") != null) return 0.0;
       return this.configuredTemperature;
   }
   ```
   Implement the gating **in `LLMConfig`**, not at the call site.
   Every place that reads temperature already calls `getTemperature()`;
   no edits to call sites.
4. **Cache file location** is the value of
   `System.getProperty("mist.llm.cache.path",
   ".mist/llm-call-cache.json")`. This is the only new system
   property introduced by this fix. Fix A-6 will absorb it into
   `MstConfig.Llm.cachePath()`; until then, the call site reads the
   property directly. The default location is **outside** `target/`
   on purpose — `mvn clean` (which the bundled README's Quick Start
   recommends) wipes `target/`, so a cache there would be deleted
   before every fresh build. `.mist/` survives `mvn clean` and
   follows the dotdir convention. The fix also patches `.gitignore`
   to add `/.mist/` so the cache is not accidentally committed
   during dev; the artifact workflow above documents how to opt-in
   to committing it.
5. **The cache MUST also honour Gemini's and OpenAI-compatible's
   `seed` parameter when `random.seed` is set.** Even though cache
   hits short-circuit, the cache cold-fill path still calls the
   backend, and we want that one call to be as deterministic as the
   backend allows. Pass the same numeric `random.seed` value into
   `GeminiApiClient.request.generationConfig.seed` and into the
   OpenAI-compatible payload's `seed` field. Ollama already accepts
   it via `options.seed`. This is best-effort; the cache is the
   actual guarantee.

### Acceptance criteria
- [ ] Two consecutive runs with `-Drandom.seed=42` produce
      byte-identical generated test classes for the same scenario set.
      Verify:
      ```
      mvn -q clean package -DskipTests
      java -Drandom.seed=42 -jar target/restest.jar \
           src/main/resources/My-Example/trainticket-demo.properties \
           > run1.log 2>&1
      find target/test-cases -name 'Flow_Scenario_*.java' \
           -exec sha256sum {} \; | sort > run1.sums

      mvn -q clean package -DskipTests
      java -Drandom.seed=42 -jar target/restest.jar \
           src/main/resources/My-Example/trainticket-demo.properties \
           > run2.log 2>&1
      find target/test-cases -name 'Flow_Scenario_*.java' \
           -exec sha256sum {} \; | sort > run2.sums

      diff run1.sums run2.sums   # must be empty
      ```
- [ ] Run 1 above logs `LLMCallCache: write hit` lines (cold fill).
- [ ] Run 2 above logs `LLMCallCache: read hit` lines and zero
      `LLMService: dispatching to <backend>` lines for any prompt
      that already appeared in Run 1.
- [ ] When `random.seed` is unset, behaviour matches
      `inject-detection` HEAD before this fix: backend is called for
      every prompt, no reads from cache. Verify by removing the seed
      and confirming the per-step Allure attachments differ between
      two consecutive runs.
- [ ] `.mist/llm-call-cache.json` exists after a run, parses as
      valid JSON, and the keys are 64-hex-char strings.
- [ ] A deliberately corrupted cache file (`echo '{bad json' >
      .mist/llm-call-cache.json`) causes the next JVM start with
      `random.seed` set to **exit non-zero** with a clear error
      message. The fix is to delete or fix the file, not to silently
      ignore it.
- [ ] `LLMConfig.getTemperature()` returns `0.0` when `random.seed`
      is set, regardless of the configured value. Verify via
      `LLMConfigSeedGateTest`.
- [ ] `LLMCommunicationLogger` continues to write logs as before,
      unaffected by the new cache. Verify by inspecting
      `logs/llm-communications/` after both seeded and unseeded runs.
- [ ] `mvn -q -DskipTests compile` succeeds.

### Tests to add
- `src/test/java/es/us/isa/restest/llm/LLMCallCacheTest.java`:
  unit tests for
  - `put` then `get` round-trip on the same key
  - different prompts produce different keys (no collision)
  - same prompt + different temperature produce different keys
  - same prompt + different backend produce different keys
  - atomic rename: kill the JVM after `tmp` is written but before
    rename, verify the canonical cache file is unchanged
  - corrupt cache file at startup throws fatal exception (no silent
    fallback)
- `src/test/java/es/us/isa/restest/llm/LLMConfigSeedGateTest.java`:
  unit tests for
  - `getTemperature()` returns `0.0` when `random.seed` is set
  - `getTemperature()` returns the configured value when
    `random.seed` is unset
  - thread-safety: setting/unsetting `random.seed` concurrently does
    not corrupt the gating (this is a `System.getProperty` read, but
    we want a regression test).

### Rollback
- Delete `.mist/llm-call-cache.json` (or the path the user pointed
  `mist.llm.cache.path` at).
- Revert the `.gitignore` `/.mist/` addition.
- Revert the branch.
- The `LLMCommunicationLogger` is untouched and continues to function.

### Explicit non-goals (do not extend this fix)
- Do **not** add a separate `mist.llm.cache.enabled` property. The
  `random.seed` property is the only switch.
- Do **not** add cache TTL / eviction. The cache is meant to be
  artifact-stable; entries should not silently expire.
- Do **not** add a "clear cache" CLI command. The user runs `rm
  .mist/llm-call-cache.json` (or the path their override points at).
  One file, no ceremony.
- Do **not** migrate the other in-`target/` caches
  (`target/soft-error-rule-cache.json`,
  `target/parameter-error-analysis-cache.json`,
  `target/intelligent-analysis-cache.json`) in this fix. They have the
  same `mvn clean` vulnerability and are handled by **Fix A-7**, which
  lands last in the DAG. S-4 is scoped to the LLM call cache only;
  do not bleed into A-7's surface.
- Do **not** merge the cache with `LLMCommunicationLogger`. They have
  incompatible formats and incompatible update semantics.
- Do **not** make the cache lookup async / non-blocking. The
  short-circuit must be synchronous so that the cached response is
  returned in the same JVM frame the LLM call would have been made.

---

## Fix A-6 — MstConfig POJO (LAND SECOND)


### Problem
30+ MST-related system properties are read with `System.getProperty(...)`
scattered across the code. Two specific harms:
- Property precedence is implicit. `mst.generate.only.first.step=true`
  and `scenario.shattering.enabled=false` interact, but the interaction
  is not documented. Reviewers cannot reconstruct a run.
- A typo (`smart.input.fetch.percetage` vs `…percentage`) silently
  produces the default value with no warning.

### Files
- `src/main/java/es/us/isa/restest/configuration/MstConfig.java` *(new)*
- `src/main/java/es/us/isa/restest/configuration/MstConfigValidator.java` *(new)*
- Every existing call site of
  `System.getProperty("mst.*" | "smart.input.fetch.*" |
  "scenario.shattering.*" | "faulty.*" | "trace.merge.*" |
  "test.enhancer.*" | "status.code.exploration.*" |
  "soft.error.cache.*" | "jaeger.*" | "negative.input.generation.*" |
  "llm.response.validation.*")` (must be migrated)

### Surgical change
1. Create `MstConfig` as an immutable POJO. Fields are typed and named
   exactly after the existing property keys (use `kebab-case → camelCase`
   transformation; `mst.generate.only.first.step` ⇒ `generateOnlyFirstStep`).
   Group the fields into sub-records by area:
   - `core` (MST primary toggles)
   - `smartFetch`
   - `llm` (LLM backend + cache; **must include `cachePath` —
     absorbs the `mist.llm.cache.path` property that Fix S-4
     introduced**, default `.mist/llm-call-cache.json`)
   - `faulty` (negative test config)
   - `scenarioMerge` (Phase 1/2 thresholds)
   - `scenarioShattering`
   - `softErrorCache`
   - `statusCodeExploration`
   - `enhancer`
   - `jaeger`
2. Single entry point: `MstConfig.fromSystemProperties()`. It reads
   every key once, applies defaults from the existing call sites,
   builds the POJO, runs `MstConfigValidator.validate(cfg)`, and
   returns the instance.
3. `MstConfigValidator` checks:
   - **Unknown keys.** If `System.getProperty(key)` returns a value
     for a key that starts with one of the MST namespaces above but is
     **not** in the known-keys whitelist, log `WARN` and exit non-zero
     if `mst.config.strict=true` (this is the only new property; it
     defaults to `false`).
   - **Conflicting keys.** Hard-code the documented conflicts: e.g.
     when `mst.generate.only.first.step=false`, `scenario.shattering.enabled`
     is ignored — emit `INFO` if user set it explicitly.
   - **Range checks.** `faulty.ratio` ∈ [0,1]; `trace.merge.max.session.gap.micros`
     ≥ 0; etc.
4. Make `MstConfig` available as a singleton via
   `MstConfig.instance()` (lazy). Wire every existing call site to
   read from this singleton. The call site change is mechanical:
   replace `System.getProperty("mst.generate.only.first.step", "true")`
   with `MstConfig.instance().core().generateOnlyFirstStep()`.
5. Update **only the documented properties** in
   `flow.md § "Conditions, Flags, and Inputs"` to add a precedence note
   per group. Do **not** rewrite the section.

### Acceptance criteria
- [ ] Zero `System.getProperty(...)` calls remain in MST-mode code for
  keys in the namespaces listed above. Verify:
  ```
  grep -rE "System\.getProperty\(\"(mst|smart\.input\.fetch|scenario\.shattering|faulty|trace\.merge|test\.enhancer|status\.code\.exploration|soft\.error\.cache|jaeger|negative\.input\.generation|llm\.response\.validation)\." \
       src/main/java/es/us/isa/restest
  ```
  returns nothing.
- [ ] With no properties set, defaults match `inject-detection` HEAD
  (run the demo with `mvn -q exec:java …` and confirm test count is
  unchanged ± noise).
- [ ] With a deliberately bogus key
  `-Dmst.config.strict=true -Dmst.generrate.only.first.step=true`
  (note typo), the run exits non-zero.
- [ ] With the same bogus key without `strict=true`, the run continues
  with a `WARN` log.
- [ ] `MstConfigValidatorTest` covers: unknown key, out-of-range
  `faulty.ratio`, conflicting Root-API-Mode + shattering.

### Tests to add
- `src/test/java/es/us/isa/restest/configuration/MstConfigTest.java`
- `src/test/java/es/us/isa/restest/configuration/MstConfigValidatorTest.java`

### Rollback
Revert the branch. No state side effects (the POJO is in-memory).

---

## Fix S-2 — Semantic Dependency Registry: externalise the noun map

### Problem
`TraceWorkflowExtractor.NOUN_TO_KEY` is a hard-coded `HashMap`
(currently at line 634 of the file) listing TrainTicket-domain plurals
like `orders → orderId`, `trips → tripId`, `trains → trainNumber`.
Side effects:
1. `isMeaningfulPathNoun()` only accepts lowercase, ≥3-char, no-hyphen
   segments. AWS-style `/order-items/{order-item-id}` is silently
   dropped. So is `/customers/{cid}/orders/{oid}` (the second ID).
2. To support a new SUT (Sock Shop, Online Boutique, Spotify), users
   must edit Java source and recompile. The tool is not portable.

The paper's P/R/F₁ = 1.000 claim is true *only* on TrainTicket and
*only* because the map was hand-tuned for TrainTicket.

### Files
- `src/main/java/es/us/isa/restest/workflow/TraceWorkflowExtractor.java`
- `src/main/resources/mist/noun-map.default.yaml` *(new)*
- `src/main/resources/My-Example/trainticket/mist-noun-map.yaml` *(new)*
- `src/main/java/es/us/isa/restest/workflow/NounKeyMap.java` *(new)*

### Surgical change
1. Create `NounKeyMap` (~80 LOC). It is a thin wrapper over a
   `Map<String, String>` loaded from YAML at startup. Two construction
   paths:
   - `NounKeyMap.fromDefault()` loads
     `src/main/resources/mist/noun-map.default.yaml` (TrainTicket
     entries verbatim, so behaviour is unchanged on the demo).
   - `NounKeyMap.fromPath(Path)` overrides the default with a
     per-SUT file; missing keys fall back to the default map.
2. Allow hyphenated and underscored keys. Update
   `isMeaningfulPathNoun()` to accept `[a-z]+([-_][a-z]+)*` of total
   length ≥ 3, and to **not** silently drop unrecognised nouns. Instead:
   - If the segment is in the map ⇒ map directly.
   - If the segment is not in the map but matches the meaningful-noun
     regex ⇒ derive a stem by stripping the trailing `s` (plural-safe
     list still applies) and append `Id`. Log at `DEBUG` so users can
     audit.
   - If the segment is not meaningful (`api`, `v1`, length < 3, etc.) ⇒
     skip and log at `TRACE`.
3. Read the map path from `MstConfig.instance().core().nounMapPath()`
   (add this single field to `MstConfig.Core`; this is the **one**
   exception to the "no new properties" rule, because it directly
   substitutes for hard-coded constants. Default value:
   `mist/noun-map.default.yaml` on classpath. **Document it in flow.md.**
4. Make `NOUN_TO_KEY` private and final, populated from the loaded
   `NounKeyMap`. Delete the static initialiser block.

### Acceptance criteria
- [ ] On the bundled TrainTicket demo, scenario extraction produces
  the same merged-scenario count as `inject-detection` HEAD ± 0
  (the default YAML must reproduce TrainTicket behaviour exactly).
- [ ] A test that constructs a URL like
  `/api/v1/order-items/123e4567-e89b-12d3-a456-426614174000` and runs
  `extractFieldsFromUrl()` produces a key (e.g. `orderItemId`) instead
  of silently dropping the segment.
- [ ] A test that constructs a nested URL
  `/api/v1/customers/AAA-BBB/orders/CCC-DDD` produces both
  `customerId=AAA-BBB` and `orderId=CCC-DDD` in `inputFields`.
- [ ] Loading a malformed YAML raises a startup-time exception with a
  clear message (don't fall back silently).
- [ ] `mvn -q -DskipTests compile` succeeds.

### Tests to add
- `src/test/java/es/us/isa/restest/workflow/NounKeyMapTest.java`
- `src/test/java/es/us/isa/restest/workflow/TraceWorkflowExtractorUrlExtractionTest.java`
  with at least: TrainTicket regression, hyphenated, nested, all-caps
  acronym, unknown-meaningful-noun fallback.

### Rollback
Restore the static initialiser block in `TraceWorkflowExtractor` and
delete the new files. Property `mist.noun.map.path` becomes inert.

---

## Fix S-3 — Phase 2.5 dedup correctness

### Problem
`MultiServiceTestCaseGenerator` (`inject-detection` HEAD) maintains two
side-by-side dedup structures:

```java
private final Set<String> seenSingleRootApis = new LinkedHashSet<>();
private final Set<WorkflowScenario> dedupApprovedScenarios =
        Collections.newSetFromMap(new IdentityHashMap<>());
```

`applySingleRootDedup` (around line 3107) uses **identity comparison**
(`dedupApprovedScenarios.contains(sc)`) as a pass-through guard so that
scenarios already kept in Phase 2.5 are not re-evaluated in Phase 3.5.

Failure mode: Phase 3 (`ScenarioOptimizer.optimizeScenarios`) returns
**newly constructed** `WorkflowScenario` instances after shattering.
Those new instances are not in `dedupApprovedScenarios`, so Phase 3.5
re-evaluates them against `seenSingleRootApis` — which by that point
already contains the API keys from Phase 2.5. A shattered single-root
component can be erroneously dropped, breaking the "Phase 3 preserves
coverage" invariant.

### Files
- `src/main/java/es/us/isa/restest/generators/MultiServiceTestCaseGenerator.java`
  (touch only the dedup region: ~line 60–80, ~line 3088–3220)

### Surgical change
1. **Replace identity tracking with phase tagging.** Add a single
   `boolean approvedInDedupPass` field on `WorkflowScenario` (and a
   setter). Phase 2.5 sets `approvedInDedupPass=true` on every scenario
   it keeps. Phase 3 (shattering) propagates the tag from the parent
   scenario to every child component. Phase 3.5 / Phase 4 honour the
   tag instead of consulting `dedupApprovedScenarios`.
2. **Delete `dedupApprovedScenarios`.** It is no longer needed.
   Identity tracking was the bug.
3. **Make `seenSingleRootApis` reset-safe.** Add a `Set<String>
   approvedApiKeys` that records the keys of approved (kept) scenarios
   only. Phase 3.5 / Phase 4 dedup against `approvedApiKeys`, not
   `seenSingleRootApis`. The semantic difference: `seenSingleRootApis`
   was "every key we've ever seen", which over-suppresses; the new set
   is "every key that survived dedup".
4. **Consolidate the two methods.** Delete `deduplicateSingleRootScenarios`
   (line 3092) and `applySingleRootDedup` (line 3107) and replace with
   a single private method `runSingleRootDedupPass(String label,
   List<WorkflowScenario> scenarios, Set<String> approvedKeys)` that
   takes the input list, the approved-key tracker, and the log label.
   Phase 2.5 calls it with an empty `approvedKeys`; Phase 3.5 calls it
   with the populated set from Phase 2.5.
5. **Update `decomposeMultiRootScenarios()`** (referenced in the same
   class around line 3210) to read from `approvedApiKeys`, not
   `seenSingleRootApis`.

### Acceptance criteria
- [ ] On the bundled TrainTicket demo, the number of generated
  `Flow_Scenario_N.java` files is **unchanged** from
  `inject-detection` HEAD.
- [ ] A targeted unit test constructs three scenarios:
  - scenario A: single root `GET /stations` (kept in Phase 2.5)
  - scenario B: multi-root `[POST /trip, GET /stations]` (kept in
    Phase 2.5, shattered in Phase 3 into component A' = `GET /stations`,
    component B' = `POST /trip`)
  - Phase 3.5 must keep both shattered components. Pre-fix behaviour:
    A' is dropped because `/stations` was already in `seenSingleRootApis`.
    Post-fix: A' is dropped only if scenario A's `GET /stations` is
    semantically identical to A' (same canonical key); otherwise both
    survive.
- [ ] `dedupApprovedScenarios` no longer appears in the source.
- [ ] `applySingleRootDedup` and `deduplicateSingleRootScenarios` are
  replaced by exactly one method `runSingleRootDedupPass`.

### Tests to add
- `src/test/java/es/us/isa/restest/generators/PhaseTwoFiveDedupTest.java`
  with the three-scenario regression and a fan-out shatter case.

### Rollback
Revert the branch. The `approvedInDedupPass` field is additive on
`WorkflowScenario` and has no persisted side effects.

---

## Fix S-1 — Extract phase pipeline from the god class

### Problem
`MultiServiceTestCaseGenerator.generate()` (line 286 in
`inject-detection` HEAD, ~52 lines) sequentially invokes Phase 2.5,
shared-pool generation, Phase 3, Phase 4, fault-queue construction,
variant generation. The class is **3,332 lines** (now ~3,422 after
67337db9) and concentrates six responsibilities in one method body. Two
concrete harms:
- Adding or reordering a phase requires editing this one method.
- Phase logic cannot be unit-tested in isolation because the surrounding
  state (`scenarios`, `serviceSpecs`, `serviceConfigs`,
  `dependencyRegistry`, `sharedParameterPools`, `seenSingleRootApis`)
  is private mutable state.

### Files
- `src/main/java/es/us/isa/restest/generators/MultiServiceTestCaseGenerator.java`
- `src/main/java/es/us/isa/restest/workflow/pipeline/WorkflowPipeline.java` *(new)*
- `src/main/java/es/us/isa/restest/workflow/pipeline/PipelineStage.java` *(new)*
- `src/main/java/es/us/isa/restest/workflow/pipeline/PipelineContext.java` *(new)*
- `src/main/java/es/us/isa/restest/workflow/pipeline/stages/*.java` *(new — five files)*

### Surgical change

This is a refactor, **not** a rewrite. The logic of each phase moves
verbatim; only the surrounding plumbing changes. **Do not** simplify or
improve any phase's algorithm in this fix. Behavioural drift = bug.

1. Create the pipeline interfaces:

   ```java
   public interface PipelineStage {
       String name();
       void run(PipelineContext ctx);
   }

   public final class PipelineContext {
       public List<WorkflowScenario> scenarios; // mutated in place
       public final Map<String, OpenAPISpecification> serviceSpecs;
       public final Map<String, MultiServiceConfig> serviceConfigs;
       public final SemanticDependencyRegistry dependencyRegistry;
       public final Set<String> approvedApiKeys; // from S-3
       public final MstConfig config; // from A-6
       // … only the state that is actually read or written by stages
   }

   public final class WorkflowPipeline {
       private final List<PipelineStage> stages;
       public WorkflowPipeline(List<PipelineStage> stages) { /* … */ }
       public void execute(PipelineContext ctx) {
           for (PipelineStage s : stages) {
               log.info("[Pipeline] running stage: {}", s.name());
               s.run(ctx);
           }
       }
   }
   ```

2. Create five stage classes under `workflow/pipeline/stages/`:
   - `Phase25DedupStage`         (calls the new `runSingleRootDedupPass`)
   - `SharedPoolGenerationStage` (lifts `generateSharedParameterPools()`)
   - `Phase3ShatteringStage`     (wraps `ScenarioOptimizer.optimizeScenarios`)
   - `Phase35DedupStage`         (second call to `runSingleRootDedupPass`)
   - `Phase4DecompositionStage`  (lifts `decomposeMultiRootScenarios()`)

3. In `MultiServiceTestCaseGenerator.generate()`, the first ~30 lines
   are replaced by:

   ```java
   PipelineContext ctx = new PipelineContext(
           scenarios, serviceSpecs, serviceConfigs,
           dependencyRegistry, approvedApiKeys, mstConfig);
   List<PipelineStage> stages = List.of(
           new Phase25DedupStage(),
           new SharedPoolGenerationStage(),
           new Phase3ShatteringStage(),
           new Phase35DedupStage(),
           new Phase4DecompositionStage());
   new WorkflowPipeline(stages).execute(ctx);
   ```

   The rest of `generate()` (fault-queue construction and variant loop)
   stays inline — it is the variant generator, not a phase. This fix
   does **not** extract that.

4. **Verbatim lift.** Move the body of each phase method into the
   `run()` method of its stage class. Replace `this.<field>` reads with
   `ctx.<field>`. Resolve any private-method dependencies by either
   making the helper `package-private` on `MultiServiceTestCaseGenerator`
   or duplicating it into the stage class (prefer the former; the
   stages and the generator live in adjacent packages — adjust the
   visibility minimally).

### Acceptance criteria
- [ ] On the bundled TrainTicket demo, the **byte-identical** set of
  `Flow_Scenario_N.java` files is produced compared with the pre-fix
  branch (run with the same `-Drandom.seed=42` after S-4 lands, and
  diff `target/test-cases/` recursively).
- [ ] `MultiServiceTestCaseGenerator.java` line count drops by ≥ 200
  lines (the phase bodies move out).
- [ ] Each stage class has a unit test that constructs a minimal
  `PipelineContext` with hand-rolled scenarios and asserts the stage's
  output on the context. Five tests total.
- [ ] No phase logic is altered. (Verify by inspection: each stage's
  `run()` body matches the pre-fix method body line-for-line modulo
  `this.` → `ctx.` substitution.)

### Tests to add
- `src/test/java/es/us/isa/restest/workflow/pipeline/Phase25DedupStageTest.java`
- `src/test/java/es/us/isa/restest/workflow/pipeline/SharedPoolGenerationStageTest.java`
- `src/test/java/es/us/isa/restest/workflow/pipeline/Phase3ShatteringStageTest.java`
- `src/test/java/es/us/isa/restest/workflow/pipeline/Phase35DedupStageTest.java`
- `src/test/java/es/us/isa/restest/workflow/pipeline/Phase4DecompositionStageTest.java`

### Rollback
Revert the branch. The new files are standalone; the only mutated file
is `MultiServiceTestCaseGenerator.java`.

### Explicit non-goals (don't do these in this fix)
- Don't add per-stage `--enable / --disable` flags. (Ablation is Path B.)
- Don't make the pipeline parallel. Stages stay sequential.
- Don't change the call site of the pipeline from `AbstractTestCaseGenerator`
  upstream code paths.

---

## Fix A-5 — Sniper FaultTarget queue: cover path / header / cookie parameters

### Problem
`generateSharedParameterPools()` (referenced from `MultiServiceTestCaseGenerator`
line ~298 and now moved into `SharedPoolGenerationStage`) builds
`InvalidInputPool` instances keyed on `(rootApiKey, parameter)`. Inspection
reveals it iterates only over **body** and **query** parameters. Path
parameters (e.g. `{orderId}` in `/orders/{orderId}`), header parameters
(`Authorization`, `X-API-Key`, custom headers), and cookie parameters are
not enrolled in the pool. Consequence: `BuildFaultInjectionQueue` never
emits a `FaultTarget` for those parameter locations, so the paper's
"exhaustive 8-fault coverage per parameter" claim is false on every
endpoint with a path or header parameter (most REST endpoints).

### Files
- `src/main/java/es/us/isa/restest/workflow/pipeline/stages/SharedPoolGenerationStage.java`
  (post-S-1) — the loop body that walks an operation's parameters
- `src/main/java/es/us/isa/restest/inputs/InvalidInputPool.java`
  (only if `ParameterLocation` is not already first-class in the pool key)

### Surgical change
1. Audit `SharedPoolGenerationStage.run(ctx)` to confirm which parameter
   locations are iterated. The current iteration likely calls
   `operation.getTestParameters()` and filters by `in == "body" || in == "query"`.
   Remove the filter. Iterate **all** locations: `path`, `query`, `header`,
   `cookie`, `body`. `formData` is rare in modern REST; treat it as `body`.
2. Verify the pool key includes the parameter's location, so a `path`
   `id` and a `query` `id` (same name, different location) do not
   collide. If not, extend the pool key to `(rootApiKey, paramName,
   paramLocation)`.
3. For `header` parameters, apply the schema-aware applicability matrix
   already implemented (flow.md § "Schema-aware fault applicability").
   Most headers are `string`-typed, so most fault categories already
   apply. **Do not** invent new fault categories.
4. For `path` parameters, all eight categories apply per the matrix;
   no extra work needed beyond enrolment.
5. Confirm `MultiServiceRESTAssuredWriter` already emits the request
   builder for `path` / `header` / `cookie` invalid values (it should,
   since positive tests use these locations). If it doesn't, this fix
   is out of scope and the user must be told (see § "Out of scope below").

### Out of scope here
- Authentication-specific exploration (invalid token, expired JWT,
  wrong-role user) is already covered by `AuthManipulationStrategy`
  under Status Code Exploration. **Do not** duplicate it here.
- Do **not** add new fault categories. The eight in
  `InvalidInputType` are the contract.

### Acceptance criteria
- [ ] On the bundled TrainTicket demo, the count of `FaultTarget`
  tuples emitted by `BuildFaultInjectionQueue` increases by ≥ 30 %
  (most TrainTicket admin endpoints use a path `id` that was
  previously skipped). Log the count before/after at `INFO` level for
  the user to read.
- [ ] A generated negative variant for `GET
  /api/v1/orderservice/order/{orderId}` exists with a path-parameter
  fault (e.g. `OVERFLOW: orderId = AAAAA…` of length 5000).
- [ ] A generated negative variant for an endpoint with a header
  parameter exercises that header with a fault.
- [ ] No upstream classes outside the allow-list in § 0.3 are
  modified. (`MultiServiceRESTAssuredWriter` is on the allow-list
  in spirit — confirm with the user before touching it; if a writer
  change is needed and the user agrees, add it to the allow-list and
  proceed.)

### Tests to add
- `src/test/java/es/us/isa/restest/workflow/pipeline/SharedPoolGenerationStagePathParamTest.java`
- `src/test/java/es/us/isa/restest/workflow/pipeline/SharedPoolGenerationStageHeaderParamTest.java`

### Rollback
Revert the branch. The pool-key extension is the only schema-level
change; if the change is keyed on `(rootApiKey, paramName,
paramLocation)`, the new tuple shape is compatible with the existing
`Map<String, InvalidInputPool>` value type.

---

## Fix A-7 — Migrate persistent caches from `target/` to `.mist/`

### Problem
Fix S-4 fixed the `mvn clean` vulnerability for the new LLM cache by
defaulting it to `.mist/llm-call-cache.json`. **Three other persistent
caches in the codebase have the exact same vulnerability** and were
deliberately left out of S-4's scope:

| File | Default in code | Property |
|---|---|---|
| `target/soft-error-rule-cache.json` | `MultiServiceRESTAssuredWriter.java:206` (embedded in the generated test code template) AND `src/main/resources/My-Example/trainticket-mst.properties:194` (explicit demo override) | `soft.error.cache.path` |
| `target/parameter-error-analysis-cache.json` | `ParameterErrorAnalyzer.java:135` | `parameter.error.analysis.cache.path` |
| `target/intelligent-analysis-cache.json` | `TraceErrorAnalyzer.java:471` | `intelligent.analysis.cache.path` |

All three are persistent caches whose entire purpose is to **avoid
re-asking the LLM the same question across runs** (soft-error
classification, parameter-error attribution, intelligent failure
analysis). Each `mvn clean install` wipes them, forcing a cold relearn
on the next run. Just like the LLM call cache (S-4), they:
- are gitignored (because `/target` is gitignored), so cannot be shipped
  in an artifact bundle, and
- are deleted by the documented Quick Start command, so the "increment-
  not-overwrite" semantics that S-4's operating-modes table guarantees
  do not hold here either.

A-7 mechanically applies the S-4 pattern to all three.

### What is **not** migrated (and why)
Inspection of the codebase finds the following `target/`-located files
that look like state but should **stay** in `target/`:

- `target/enhancer/{testId}/round-N/...` (TestCaseEnhancer): per-run
  forensics, scoped to a single execution. Wiping on `mvn clean` is
  correct behaviour.
- `target/test-cases/Flow_Scenario_*.java`: generated source code,
  always re-emitted each run.
- `target/test-classes/`, `target/dependency/`, `target/allure-results/`,
  `target/allure-report/`: standard Maven / Allure build outputs.
- `target/semantic-registry-dump.json`: a debug/evaluation snapshot
  written by `SemanticRegistryDumper`; consumed by
  `SemanticRegistryEvaluator` in the same JVM run, then discarded.
  Not a cross-run cache.

The fix touches **only the three caches in the problem table**.

### Files
- `src/main/java/es/us/isa/restest/validation/SoftErrorRuleCache.java`
  (edit: add one-shot migration in `getInstance(String)`)
- `src/main/java/es/us/isa/restest/inputs/smart/ParameterErrorAnalyzer.java`
  (edit: change default at line ~135; add migration)
- `src/main/java/es/us/isa/restest/analysis/TraceErrorAnalyzer.java`
  (edit: change default at line ~471; add migration)
- `src/main/java/es/us/isa/restest/writers/restassured/MultiServiceRESTAssuredWriter.java`
  (edit: change the embedded fallback string at line ~206 so newly
  generated test code uses the new default)
- `src/main/resources/My-Example/trainticket-mst.properties`
  (edit: delete the `soft.error.cache.path=target/...` line at
  ~line 194 so the new Java default takes effect — do **not** replace
  with a `.mist/...` override, the Java default already points there)
- `src/main/java/es/us/isa/restest/configuration/MstConfig.java`
  (edit: A-6 has already absorbed these properties into sub-records
  `softErrorCache`, `enhancer`, and `jaeger` — update their default
  values to `.mist/...`)
- The `.gitignore` `/.mist/` entry from S-4 already covers all three
  new locations. **No `.gitignore` edit in this fix.**

### Surgical change

This fix follows the same pattern for each of the three caches.

#### Step 1 — Change the default in the Java owner

For each of the three classes, change the fallback string in the
`System.getProperty("X.cache.path", "target/Y.json")` call from
`target/Y.json` to `.mist/Y.json`. Verbatim list of changes:

| File | Line | Old default | New default |
|---|---|---|---|
| `MultiServiceRESTAssuredWriter.java` | ~206 | `target/soft-error-rule-cache.json` | `.mist/soft-error-rule-cache.json` |
| `ParameterErrorAnalyzer.java` | ~135 | `target/parameter-error-analysis-cache.json` | `.mist/parameter-error-analysis-cache.json` |
| `TraceErrorAnalyzer.java` | ~471 | `target/intelligent-analysis-cache.json` | `.mist/intelligent-analysis-cache.json` |

The writer change is special: line 206 emits a `pw.println(...)` that
writes the fallback string **into the generated test code**. The fix
edits the string literal inside the `pw.println(...)`. Tests generated
**before** this fix landed still embed the old fallback; they are
regenerated on the next `java -jar restest.jar`, after which the new
fallback takes effect.

#### Step 2 — Add one-shot migration

In each cache owner (`SoftErrorRuleCache.getInstance`,
`ParameterErrorAnalyzer.<load method>`,
`TraceErrorAnalyzer.<load method>`), insert at the top of the load
path:

```java
Path resolved = Path.of(cachePath);              // the new .mist/... path
Path legacy   = Path.of("target/" + resolved.getFileName());
if (!Files.exists(resolved) && Files.exists(legacy)) {
    Files.createDirectories(resolved.getParent());
    Files.move(legacy, resolved, StandardCopyOption.REPLACE_EXISTING);
    log.info("Migrated cache: {} -> {}", legacy, resolved);
}
```

The migration is **one-shot, opportunistic, and silent on the happy
path**. It runs every JVM start but its body fires at most once per
cache, the first time a user upgrades. After that the legacy `target/`
copy no longer exists, so the `Files.exists(legacy)` guard skips it.

The migration **must not** fire when the user has explicitly overridden
the path via property (e.g. an artifact submission that points
`soft.error.cache.path` at a committable resource location). Use the
literal `target/<filename>` check above, not a generic "scan for any
legacy location" — the migration is targeted at the specific old
default, nothing else.

#### Step 3 — Update demo property file

In `src/main/resources/My-Example/trainticket-mst.properties`, **delete**
the line:
```
soft.error.cache.path=target/soft-error-rule-cache.json
```
The Java default in `MultiServiceRESTAssuredWriter` (after Step 1) now
points at `.mist/...`, so removing the explicit override lets the new
default take effect. **Do not** replace it with a `.mist/...` override
— that would defeat A-6's MstConfig consolidation.

If grep finds other property files (search
`src/main/resources/**/*.properties`) that explicitly set any of the
three paths to `target/...`, delete those lines too. Per-SUT overrides
that point at `src/main/resources/.../X.json` (a committable location
for artifact bundles) are **kept**.

#### Step 4 — Update MstConfig defaults

A-6 has already absorbed these three properties into
`MstConfig.SoftErrorCache.cachePath()` and analogous accessors. Find
those default values and change them from `target/...` to
`.mist/...`. This is one-line edits inside `MstConfig.java`.

If A-6's POJO does not yet contain accessors for
`parameter.error.analysis.cache.path` or
`intelligent.analysis.cache.path` (because A-6 grouped them under a
broader sub-record), add them in the analogous sub-record. This is the
**one** place where the executing agent may extend A-6's sub-record
list — A-7 is the legitimate consumer of these properties.

### Acceptance criteria
- [ ] After `mvn clean install -DskipTests` on a fresh checkout, the
      three caches at `.mist/{soft-error-rule-cache, parameter-error-
      analysis-cache, intelligent-analysis-cache}.json` are **not**
      deleted (they don't exist on a fresh checkout, but the
      `target/` versions also don't exist — the check is that
      `mvn clean` doesn't touch `.mist/`).
- [ ] On a user who has pre-existing `target/soft-error-rule-cache.json`
      (e.g. from a run before this fix), the next JVM start with the
      fix applied logs `Migrated cache: target/... -> .mist/...` and
      the cache content survives. Verify by hand:
      ```
      mkdir -p target
      echo '{"GET /api/test": {"fieldChecks":[]}}' \
          > target/soft-error-rule-cache.json
      java -jar target/restest.jar trainticket-demo.properties
      ls .mist/soft-error-rule-cache.json   # exists
      ls target/soft-error-rule-cache.json  # gone (Files.move)
      ```
- [ ] After running the bundled demo, `.mist/` contains
      `llm-call-cache.json` (from S-4) plus any of the three new
      caches that were populated during the run.
- [ ] `grep -rnE 'target/(soft-error-rule|parameter-error-analysis|intelligent-analysis)' src/main/java`
      returns nothing (the migration code uses `Path.of` arithmetic,
      not literal `target/...` strings).
- [ ] `trainticket-mst.properties` no longer mentions
      `target/soft-error-rule-cache.json`.
- [ ] `MstConfig.SoftErrorCache.cachePath()` returns
      `.mist/soft-error-rule-cache.json` by default; analogous
      accessors return their `.mist/...` defaults.
- [ ] `mvn -q -DskipTests compile` succeeds.
- [ ] All tests added by previous fixes still pass.

### Tests to add
- `src/test/java/es/us/isa/restest/validation/SoftErrorRuleCacheMigrationTest.java`:
  - Pre-create a `target/soft-error-rule-cache.json`, call
    `SoftErrorRuleCache.getInstance(".mist/soft-error-rule-cache.json")`,
    verify the file moved.
  - Pre-create both `target/...` and `.mist/...` files; verify the
    `.mist/...` content wins and `target/...` is unchanged (no
    accidental overwrite).
  - User-overridden path (e.g. `src/main/resources/foo.json`): verify
    no migration happens (the legacy guard uses the literal
    `target/<filename>` shape).
- `src/test/java/es/us/isa/restest/inputs/smart/ParameterErrorAnalyzerMigrationTest.java`:
  - Same matrix.
- `src/test/java/es/us/isa/restest/analysis/TraceErrorAnalyzerMigrationTest.java`:
  - Same matrix.

(Three small test classes, each one isolated — they share the same
test logic, but separate test classes keep failure attribution clean
when one cache's migration breaks.)

### Rollback
- Revert the branch.
- Users who already migrated will have files only in `.mist/`. They can
  manually copy them back to `target/` if they want pre-A-7 behaviour,
  but most likely they will not — the new location is strictly better.

### Explicit non-goals
- Do **not** unify the three caches behind a common abstraction
  ("`.mist/` cache manager"). Each cache has its own loader, its own
  schema, its own update semantics. A shared abstraction is premature.
- Do **not** add file locking, multi-process coordination, or
  network-shared cache support. The caches are single-JVM single-host.
- Do **not** migrate `target/semantic-registry-dump.json`,
  `target/enhancer/...`, or any per-run artifact. Their `target/`
  location is correct.
- Do **not** rewrite the migration check after this fix. If a future
  fix introduces a new cache file, it lands at `.mist/...` from day
  one with no `target/...` precursor — no migration needed.

---

## 7. Global verification (run after all seven fixes land)

```bash
# 1. Build
mvn -q -DskipTests compile

# 2. Compile-time tests (the new ones added by this brief)
mvn -q test -Dtest='LLMCallCacheTest,MstConfigTest,MstConfigValidatorTest,NounKeyMapTest,TraceWorkflowExtractorUrlExtractionTest,PhaseTwoFiveDedupTest,Phase25DedupStageTest,SharedPoolGenerationStageTest,Phase3ShatteringStageTest,Phase35DedupStageTest,Phase4DecompositionStageTest,SharedPoolGenerationStagePathParamTest,SharedPoolGenerationStageHeaderParamTest'

# 3. End-to-end smoke on the bundled demo, twice, with seed
java -Drandom.seed=42 -jar target/restest.jar \
     src/main/resources/My-Example/trainticket-demo.properties \
     > run1.log 2>&1
sha256sum target/test-cases/Flow_Scenario_*.java > run1.sums

# Reset target/test-cases so the second run regenerates cleanly
mvn -q clean -DskipTests

java -Drandom.seed=42 -jar target/restest.jar \
     src/main/resources/My-Example/trainticket-demo.properties \
     > run2.log 2>&1
sha256sum target/test-cases/Flow_Scenario_*.java > run2.sums

diff run1.sums run2.sums   # must be empty
```

The diff being empty proves S-4 (determinism) AND S-1 (no behavioural
drift in the pipeline refactor) AND S-3 (dedup correctness) all hold
together.

---

## 8. Master execution checklist

Tick each box only after the acceptance criteria for that fix pass.

- [ ] Branch `claude/fix-mst-04-llm-determinism` created from
      `origin/inject-detection`
  - [ ] `LLMCallCache.java` added (file-backed JSON, atomic rename,
        debounced writes, fatal exception on corrupt-file startup)
  - [ ] Cache default location is `.mist/llm-call-cache.json` at
        the repo root — **not** in `target/` (would be wiped by
        `mvn clean`)
  - [ ] `.gitignore` patched to add `/.mist/` so dev runs do not
        accidentally commit the cache
  - [ ] `LLMService.generateText` wraps backend calls: cache-read on
        seed-set, cache-write always
  - [ ] `LLMConfig.getTemperature()` returns `0.0` when
        `random.seed` is set
  - [ ] Backend `seed` parameter forwarded to Ollama/Gemini/OpenAI-
        compatible when `random.seed` is set (best-effort)
  - [ ] `mist.llm.cache.path` property read (default
        `.mist/llm-call-cache.json`); to be absorbed by A-6
  - [ ] `LLMCommunicationLogger` untouched and still writes per-
        session log files
  - [ ] `LLMCallCacheTest` and `LLMConfigSeedGateTest` pass
  - [ ] After `mvn clean install -DskipTests`, the cache file at
        `.mist/llm-call-cache.json` still exists (smoke test that
        Maven clean does NOT touch it)
  - [ ] Two seeded runs produce byte-identical
        `Flow_Scenario_*.java` files (`diff run1.sums run2.sums` empty)
  - [ ] Run 2 logs `LLMCallCache: read hit` and zero backend
        dispatches for previously-seen prompts
  - [ ] Corrupted cache file → next seeded JVM start exits non-zero
        with a clear error
  - [ ] `simplify` skill invoked, no unused additions
  - [ ] Single commit pushed; PR not opened (user merges)

- [ ] Branch `claude/fix-mst-06-mst-config` created from S-4 tip
  - [ ] `MstConfig` POJO + sub-records
  - [ ] `MstConfig.Llm.cachePath()` absorbs the `mist.llm.cache.path`
        property introduced by S-4; S-4's direct `System.getProperty`
        read is replaced by `cfg.llm().cachePath()`
  - [ ] `MstConfigValidator` checks unknown keys, ranges, conflicts
  - [ ] All `System.getProperty("mst.*"|"smart.input.fetch.*"|…)`
        replaced; grep returns empty (including `mist.llm.cache.path`)
  - [ ] `mst.config.strict=true` exits non-zero on typo
  - [ ] `MstConfigValidatorTest` passes
  - [ ] `simplify` skill invoked
  - [ ] Single commit pushed

- [ ] Branch `claude/fix-mst-02-noun-map-yaml` created from A-6 tip
  - [ ] `NounKeyMap.java` added; loads YAML
  - [ ] `mist/noun-map.default.yaml` shipped with TrainTicket entries
  - [ ] `isMeaningfulPathNoun` accepts hyphen/underscore
  - [ ] `MstConfig.Core.nounMapPath()` added (the single new property)
  - [ ] Hyphenated-URL test passes; nested-URL test passes
  - [ ] Bundled demo produces unchanged scenario count
  - [ ] `simplify` skill invoked
  - [ ] Single commit pushed

- [ ] Branch `claude/fix-mst-03-dedup-correctness` created from S-2 tip
  - [ ] `dedupApprovedScenarios` deleted
  - [ ] `WorkflowScenario.approvedInDedupPass` field added
  - [ ] `approvedApiKeys` set introduced; `seenSingleRootApis` renamed
        or its semantics narrowed
  - [ ] `runSingleRootDedupPass` replaces the two old methods
  - [ ] `decomposeMultiRootScenarios()` updated
  - [ ] `PhaseTwoFiveDedupTest` passes
  - [ ] Bundled demo scenario count unchanged
  - [ ] `simplify` skill invoked
  - [ ] Single commit pushed

- [ ] Branch `claude/fix-mst-01-phase-pipeline` created from S-3 tip
  - [ ] `WorkflowPipeline`, `PipelineStage`, `PipelineContext` added
  - [ ] Five stage classes added under
        `workflow/pipeline/stages/`
  - [ ] `MultiServiceTestCaseGenerator.generate()` rewires to pipeline
  - [ ] Verbatim lift: stage bodies match pre-fix phase methods
  - [ ] Generator class line count drops by ≥ 200 lines
  - [ ] Five stage unit tests pass
  - [ ] Seeded run output is byte-identical with pre-fix output
        (modulo S-2 and S-3 behaviour changes that the user has
        already approved)
  - [ ] `simplify` skill invoked
  - [ ] Single commit pushed

- [ ] Branch `claude/fix-mst-05-sniper-locations` created from S-1 tip
  - [ ] `SharedPoolGenerationStage` enrolls path / header / cookie
        parameters in `InvalidInputPool`
  - [ ] Pool key extended to `(rootApi, name, location)` if needed
  - [ ] `FaultTarget` count up ≥ 30 % on bundled demo (logged)
  - [ ] Path-parameter negative variant exists
  - [ ] Header-parameter negative variant exists
  - [ ] Two new tests pass
  - [ ] `simplify` skill invoked
  - [ ] Single commit pushed

- [ ] Branch `claude/fix-mst-07-cache-mist-dir` created from A-5 tip
  - [ ] `SoftErrorRuleCache`, `ParameterErrorAnalyzer`,
        `TraceErrorAnalyzer` default paths changed from
        `target/*.json` to `.mist/*.json`
  - [ ] `MultiServiceRESTAssuredWriter.java:206` template emits
        `.mist/soft-error-rule-cache.json` (not `target/...`)
  - [ ] `trainticket-mst.properties` line
        `soft.error.cache.path=target/...` deleted; no replacement
        line added (Java default takes effect)
  - [ ] One-shot migration in each cache's load path:
        `target/X.json` -> `.mist/X.json` if old exists and new
        doesn't
  - [ ] `MstConfig` sub-record defaults updated to `.mist/...`
  - [ ] `grep -rnE 'target/(soft-error-rule|parameter-error-analysis|intelligent-analysis)' src/main/java`
        returns empty
  - [ ] After `mvn clean install -DskipTests`, `.mist/` directory
        contents preserved
  - [ ] Three migration test classes pass
  - [ ] `simplify` skill invoked
  - [ ] Single commit pushed

- [ ] Global verification (§ 7) executed; `run1.sums` == `run2.sums`
- [ ] All branches pushed to `origin`; user notified

---

## 9. Known pre-existing failures (tolerated)

Document here any pre-existing failing tests in `src/test/java/es/...`
that predate `claude/fix-mst-04-llm-determinism`. If a test fails on
`origin/inject-detection` at the moment of branching, **do not** try
to fix it. List it here and move on.

(Empty at time of writing. The executing agent fills this in after the
first `mvn -q test` run on `inject-detection`.)

---

## 10. Out-of-scope follow-ups (for Path B, not now)

These are observations from the audit that are **not** part of this
brief. Do not act on them. They are recorded for the user's reference
and will be picked up under `PATH_B_REBUILD_PLAN.md`.

- The `Test Case Enhancer`, `Status Code Exploration`, `Parameter
  Error Analysis cache`, and `Soft Error Rule Cache` subsystems feel
  bolted on rather than architected. Path B will decide whether to
  promote them to first-class contributions or relegate them to
  appendix material.
- The `MultiServiceTestCaseGenerator` variant loop (post-S-1, the part
  *not* extracted) is still ~2,800 lines and concentrates
  fault-injection + parameter resolution + LLM dispatch. Path B will
  split this further.
- The `SemanticDependencyRegistry` uses purely lexical patterns to
  match IDs (`Id`, `ID`, `uuid` suffixes). Path B contemplates a
  learned name-embedding approach for non-canonical REST naming.
- Trace-as-Oracle is currently rule-based + LLM. Path B turns it into
  a first-class "trace shape oracle" that learns invariants over
  per-API span trees.

---

*End of brief.*
