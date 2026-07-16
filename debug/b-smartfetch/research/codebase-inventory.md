# Smart Fetch — Evidence Inventory for Paper Planning

> Produced 2026-07-15 by a codebase-exploration agent (every numeric claim computed directly
> from files via grep/wc or full reads). Verbatim agent report (HTML entities decoded).
> This is the §2 evidence base for PAPER-PLAN.md.

All paths relative to repo root `C:\Users\miaot\Github\MIST`.

**Critical dating caveat (read this first).** `git log` shows that everything under `mist-core/src/main/java/io/mist/core/smart/` and `docs/Smart-Fetch-Process.md` and all of `debug/inputs/smart_fetch/*.md` has exactly **one** commit in this repo's history: `8e8f633 "MIST 1.6 — initial standalone release"` (2026-06-11), i.e. these are a frozen import from a prior fork (file paths inside them read `/home/tingshuo_miao2/github/Rest/...`). That prior fork used package `es.us.isa.restest.inputs.smart` and class name `MultiServiceTestCaseGenerator`; the current tree uses `io.mist.core.smart` and **`MistGenerator`**. A sample of the audit's internal `SmartInputFetcher.java`/`ApiMapping.java` line citations was independently re-verified against the current file — they still match almost exactly, so **internal line numbers in the audit docs are reliable**, but **cross-references to sibling classes are stale**: `TestGenerationAndExecution` → now `MistRunner` (`mist-cli/src/main/java/io/mist/cli/MistRunner.java`); `SmartLLMParameterGenerator` and `TestDataGeneratorFactory` **no longer exist as classes** — zero hits in `*.java` (stale references survive only in frozen docs/markdown; Round-1 reviewer C counted 62 such doc mentions). Only `docs/PROMPT_INVENTORY.md` has been actively maintained post-rename (git date 2026-07-13) and its `io.mist.core.smart` line numbers are exact (spot-checked).

---

## 1. Code inventory

### 1.1 Classes in `mist-core/src/main/java/io/mist/core/smart/` (11 files, 7,636 LOC total)

| Class | LOC | Responsibility | Key public methods |
|---|---:|---|---|
| `SmartInputFetcher.java` | 4576 | Orchestrates the whole smart-fetch pipeline: cache lookup → trace-endpoint priority → registry-mapping priority → LLM discovery → HTTP GET → LLM value extraction → LLM-generation fallback; owns all prompt builders. | `fetchSmartInput(ParameterInfo)` (:283), `fetchSmartInputWithProvenance(ParameterInfo)` → `Optional<ResolvedValue>` (:306), `resetValueRotation()` (:1735), `flushIfDirty()` (:224), static `rankingScore(ApiMapping, String, boolean)` (:702), static `nameAffinity(...)` |
| `InputFetchRegistry.java` | 1006 | Persisted YAML registry: parameter→`ApiMapping` list, service patterns, LLM-prompt templates, parameter-error history, per-value pool status; YAML (de)serialization. | `loadFromFile` (:70), `saveToFile` (:87), `getMappingsForParameter(String)` / `(consumerApiKey, param)` (:117/:135), `addMapping` (:163/:175), `addParameterError` (:252), `markVerified`/`markRejected`/`getPoolEntryStatus` (:367/:381/:412), `getAllServices()` (:431) |
| `ApiMapping.java` | 173 | POJO: one candidate producer (endpoint/method/service/extractPath/priority/lastUsed/successRate/consumerApiKey) plus scoring/learning math. | `updateSuccessRate(boolean)` — EMA (:87), `calculateScore()` (:110) |
| `OpenAPIEndpointDiscovery.java` | 251 | Parses an OpenAPI spec, indexes GET endpoints by `x-service-name` for discovery-time lookup. | `loadFromFile` (:34), `getAllServices()` (:156), `getEndpointsForService` (:163), `isLoaded()` (:181) |
| `ParameterError.java` | 79 | POJO: one recorded execution-time error tied to `(apiEndpoint, parameterName)`. | getters/setters, equals/hashCode |
| `ParameterErrorAnalysisCache.java` | 235 | Process-wide JSON-persisted cache of `ParameterErrorAnalyzer` LLM verdicts, keyed by service/operation/status/exception. | `getInstance` (:58), `get` (:103), `peekByEndpoint` (:128), `put` (:153) |
| `ParameterErrorAnalyzer.java` | 583 | Given a failed trace, determines whether a specific input parameter caused the failure (deterministic ref-chain matching, LLM tiebreak). Invoked from the **test writer**, not from the fetcher pipeline. | static `analyzeParameterErrors` (:70) |
| `ServicePattern.java` | 68 | POJO: regex-over-parameter-name → candidate services/endpoints (pattern discovery currently disabled). | `matches(String)` (:30) |
| `SmartFetchAuthManager.java` | 259 | JWT login + cached-token lifecycle; login URL/field names/token-path/validity are constructor args with TrainTicket-shaped defaults. | `getValidToken()` (:71, synchronized), `getAuthorizationHeader()` (:83), `addAuthHeaders` (:184), `isConfigured()` (:197), `invalidateToken()` (:206, synchronized) |
| `SmartInputFetchConfig.java` | 364 | Property-bag → typed config. | `fromProperties(Map)` (:111), ~35 getter/setter pairs (§8) |
| `CacheConfig.java` | 42 | Tiny POJO (`enabled`, `maxEntries`, `ttlSeconds`); `maxEntries` not read by `SmartInputFetcher` (reads `SmartInputFetchConfig` bounds instead — audit Theme 4). | getters/setters |

Related, outside `smart/`: `io.mist.core.value.ValueProvenance` — 5-value enum `RESOLVED_LIVE`, `RESOLVED_CACHE`, `LLM_GENERATED`, `SYNTHETIC_PLACEHOLDER`, `MUTATED_FROM_RESOLVED`, with `isLiveGrounded()` (`ValueProvenance.java:19-44`) — and `io.mist.core.value.ResolvedValue`. **Provenance is a first-class API concept** (`fetchSmartInputWithProvenance`), not just a log string.

### 1.2 Integration points outside `smart/`

`grep -rn "fetchSmartInput"` across the whole tree returns **exactly two callers outside the smart package** (plus the fetcher's own tests):

| Caller | File:line | What it does |
|---|---|---|
| `MistGenerator` (formerly `MultiServiceTestCaseGenerator`) | `MistGenerator.java:72` (field), `:424` (construction), `:538` (into pipeline context), `:599-600` (`resetValueRotation()` per scenario) | Calls `fetchSmartInput` at **three** sites: `:1245` (step-1 fallback), `:1403` (later-step fallback), `:3219` (array-element top-up) |
| `SharedPoolSupport` | `SharedPoolSupport.java:212` (gate), `:216` (`fetchSmartInputWithProvenance` in a loop up to `targetPoolSize`), `:221-224` (buckets `RESOLVED_LIVE`/`RESOLVED_CACHE` into `groundedValues`) | Phase-1 shared-parameter-pool population — the "Smart Fetch Pool" log line |
| `PipelineContext` | `:51,53,83-84,95` | Threads `smartFetcher`/`smartFetchConfig` through the workflow pipeline |
| `SharedPoolGenerationStage` | `:53` | Thin stage wrapper passing `ctx.smartFetcher` into `SharedPoolSupport` |
| `MistRunner` (formerly `TestGenerationAndExecution`) | `MistRunner.java:1056` (call site), `:1197-1241+` (`passSmartInputFetchingProperties()` — typed `MstConfig.SmartFetch` accessor + settings summary) | Bridges CLI config into system properties before `MistGenerator` construction |
| `TTEndStationLiveCheck` (test) | `:77,85` | Live-gated grounding check |

**Classes that no longer exist** (verified): `MultiServiceTestCaseGenerator` (→ `MistGenerator`), `SmartLLMParameterGenerator`, `TestDataGeneratorFactory`. The classic non-MST `LLMParameterGenerator` SPI path documented in `docs/Smart-Fetch-Process.md` §6 was dropped in the MIST extraction — only the MST/`MistGenerator` path survives.

---

## 2. LLM prompts inventory

`dataflow-map.md` §5 says "fourteen" in prose (line 341) but its table has 15 rows and ends "Distinct prompts: 15." (line 361) — cite carefully. `ParameterErrorAnalyzer` adds one more (invoked from the test writer, not the fetcher), so subsystem-wide: **15 (fetcher) + 1 (analyzer) = 16 LLM call sites.**

Current line numbers verified against today's 4576-line `SmartInputFetcher.java` (dataflow-map cited 4383 lines pre-rename; back-half numbers drifted ~190 lines):

| # | Prompt | Purpose | Current site | Registry-overridable? |
|---|---|---|---|---|
| 1 | apiDiscovery | Which SUT service(s) could supply this parameter (top-3 JSON array or NO_GOOD_MATCH) | def `:3723`, call `:600` | **Yes** — `InputFetchRegistry.java:467` |
| 2 | directValueExtraction | Extract a value using ONLY values present in the fetched JSON response | def `:1037`, call `:975` | **Yes** — `:479` |
| 3 | valueSelection | Select best value from arbitrary data | registry template only | Registered (`:499`) but **dead** — nothing calls it (`docs/PROMPT_INVENTORY.md:5`) |
| 4 | semanticFieldMatching | Map parameter name to most relevant response field, or NO_MATCH | def `:1221`, call `:1188` | No (inline) |
| 5 | multipleValueExtraction | Pull several distinct values to seed the diverse cache | call `:1877` | No |
| 6 | semanticSimilarity | Generate semantically-similar additional values (type-specific guard rails) | call `:2377` | No |
| 7 | fieldRelevance | YES/NO — is this JSON field relevant (made a last resort by audit F30) | call `:2751` | No |
| 8 | schemaTypeInference | One-word OpenAPI type guess (made dead-in-hot-path by audit F29; defaults "string") | call `:3309` | No |
| 9 | arrayTypeDecision | YES/NO array-type (flagged dead-code-reachable-only in dataflow-map) | call `:3448` | No |
| 10 | endpointSelection | Pick one endpoint from a service's GET list | call `:4198` | No |
| 11 | forcedEndpointSelection | Same, forced pick after NO_GOOD_MATCH | call `:4345` | No |
| 12 | valueGeneration | Generate a plain/array value as fallback within the smart path | call `:3928` | No |
| 13 | minimalFallback (named) | Named/typed minimal fallback | def `:1982`, 6 call sites (`:1357,1421,1428,1462,2602,2622`) | No |
| 14 | minimalFallbackLLM (raw) | Raw minimal value, one level below #13 | def `:2076`, call `:2065` | No |
| 15 | valueVariation | One varied value when rotating an existing set | call `:2589` | No |
| 16 (analyzer) | Parameter-error classification | Was the failure caused by this parameter; category | `ParameterErrorAnalyzer.java:165` | No |

Two registry keys referenced only in dead code and never installed by `initializeDefaults()` (`InputFetchRegistry.java:198-262` installs only the 3 above): `dataExtraction`, `endpointDiscovery` — latent NPEs behind unreachable callers.

Verbatim prompt texts for #1, #2, #4, #16: `docs/PROMPT_INVENTORY.md:236-354` (current as of 2026-07-13).

---

## 3. Registry state per SUT (exhaustive search)

| File | SUT | `- endpoint:` rows | Distinct params | successRate | parameterErrors | poolEntryStatus |
|---|---|---:|---:|---|---:|---|
| `mist-cli/src/main/resources/My-Example/trainticket/input-fetch-registry.yaml` | TrainTicket (demo) | **103** | **57** | **103/103 exactly 0.0** | 3,913 entries / 1,138 endpoint keys | present (~150 lines) |
| `evaluation/suts/trainticket/input-fetch-registry.yaml` | TrainTicket (eval) | **155** | **57** | **155/155 exactly 0.0** | 4,087 / 1,254 | present (~232 lines) |
| `evaluation/suts/sockshop/input-fetch-registry.yaml` | SockShop | **0** (`parameterMappings: {}`) | 0 | n/a | 0 | **92 VERIFIED_VALID values** across 2 ops / 8 fields (`POST /cards`: expires×12, longNum×13, ccv×14; `POST /addresses`: country×10, number×12, city×9, street×12, postcode×10) |
| — | TeaStore / OTelDemo / Bookinfo / Boutique | file does not exist | — | — | — | — |

Service distribution: demo TT registry spans **18** `ts-*` services (ts-travel-service×20, ts-order-service×14, ts-route-service×13); eval TT registry **23** services.

**On "maturity":** the 100%-zero successRate is intentional — `ShippedRegistryDepoisonTest.java:14-27` documents the deliberate **de-poison (2026-06-10)**: until A2 (producer-keyed feedback) lands, no code path can legitimately raise a producer's successRate; non-zero rates also kept the cold-start gate (max successRate < 1e-9) permanently false, bypassing the Fix-B name-affinity prior (endStation→trains at 0.9721 was the live failure). **TrainTicket's maturity is structural/curated (57 params × which services/GET endpoints), not learned.** Ranking today = `0.5·(priority/10)` + cold-start `nameAffinity` prior (`SmartInputFetcher.rankingScore`/`nameAffinity` `:702-704`, `ProducerRankingTest`).

**SockShop is NOT "mature" in the mapping sense** — zero ApiMappings means the discovery→persistence half has never produced anything there; what exists is the separate poolEntryStatus verified-pool mechanism (`InputFetchRegistryPoolStatusTest.java:14-18`). Corroborated by `paper/tool-demo/REVIEW_ISSTA_2026.md:215`: a SockShop draft claim flagged "THIN — log shows dedup-exhaustion + smart-fetch 'no mappings'."

**TeaStore/OTel "LLM-off recently":** their `.properties` contain **no** `smart.input.fetch.*` keys at all (coded default `enabled=false`); the `negative.input.generation.mode=smart` lines are a **different subsystem**. Latest dated session props (`teastore/myc-props-s20260723.properties:45`, `oteldemo/myc-props-s20260716.properties:42`) set `llm.enabled=false`. Bookinfo sets `smart.input.fetch.enabled=false` (`bookinfo-demo.properties:374`). Boutique sets `enabled=true`/`percentage=1.0` (`boutique-demo.properties:127-128`) but has no committed registry (would cold-start).

---

## 4. Existing measurements

### 4.1 D4 "Smart-Fetch Hit Rate" — all four d4_summary.json

D4 (`debug/inputs/input-quality-measurement-framework.md:112-137`): fraction of ID-typed positive-variant inputs whose value came from a real upstream response; conservative = SMART_FETCH only; upper = + SHARED_POOL_DRAW. Threshold ≥0.60. (A 5th, earliest run dir predates D4 instrumentation — no d4_summary.json.)

| Run | computed_at | total inputs | id-typed | smart_fetch | pool_draw | llm | neg | unk | SFHR cons. | SFHR upper | Pass? |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| 1777348134277 | 2026-05-01 | 26,467 | 123 | 56 | 15 | 51 | 0 | 1 | **45.53%** | 57.72% | ❌ |
| 1777780533352 | 2026-05-05 | 32,198 | 357 | 271 | 49 | 37 | 0 | 0 | **75.91%** | 89.64% | ✅ |
| 1778001841606 | 2026-05-06 | 18,961 | 178 | 104 | 74 | 0 | 0 | 0 | **58.43%** | 100.00% | ❌ |
| 1778039778981 | 2026-05-07 | 23,189 | 370 | 174 | 69 | 114 | 10 | 3 | **47.03%** | 65.68% | ❌ |

**Only 1 of 4 runs passes**; mean conservative **56.7%** (spread 30.4pp); mean upper 78.3%. Run-to-run variance is itself a finding — single-number SFHR claims are misleading without spread.

**Data-quality flags visible in the JSONs**: run 1778001841606's worst-parameter examples are literally `FALLBACK_loginId_11` etc. (SYNTHETIC_PLACEHOLDER-style strings) labeled SHARED_POOL_DRAW and counted into the **upper** bound (hence exactly 100.00%); run 1777348134277's orderId examples repeat literal `"ORD123456789"` ×3 (LLM template, not grounded diversity); run 1778039778981 has an accountId example valued a literal triple-backtick (markdown-fence leak) counted as valid input.

### 4.2 report.md headlines (D-framework context)

- 1777065076883 (04-27): D1 90.61% ✅, D3 0.91% ✅ — no D4/D5 yet.
- 1777348134277 (05-01): D1 97.42% ✅, D4 45.53/57.72 ❌, **D5 IDR 0.00% ❌**, D7 realism 26.47% ❌.
- 1777780533352 (05-05): D1 98.26% ✅, D4 75.91/89.64 ✅, **D5 0.00% ❌**, D7 96.88% ✅, D8 0.5525 ✅, D9 43.14% ❌, D10 74.76% ❌.
- 1778001841606 (05-06): D1 99.25% ✅, D4 58.43/100 ❌, **D5 0.00% ❌**, D7 0.00% ❌, D8 0.3538 ❌.
- 1778039778981 (05-07): D1 100% ✅, D4 47.03/65.68 ❌, **D5 0.00% ❌**, D7 92.72% ✅, D8 0.5572 ✅.

**Cross-metric tension:** D5 "ID-Resolvability Rate" (value appears as an output in the Jaeger trace export) is **0.00% in every run**, including the D4-peak run. Fetch-time provenance and execution-time trace confirmation disagree completely on every measured run — a paper citing SFHR must not imply trace corroboration.

### 4.3 Methodological caveats on the D4 pipeline itself

`debug/inputs/scripts/REVIEW.md` (2026-04-30, 30 findings against `validate_d4.py`/`mine_provenance.py`):
- **#1 Critical** (`mine_provenance.py:57,77-79`): regex fails to strip `" (from N options)"` → LLM-classified rows fall through to UNKNOWN ("46/46 LLM entries corrupted" in the exercised run) — plausible root cause of run 1778001841606's suspicious `llm: 0`.
- **#2 High** (`:179-185`): non-injective (parameter, value) join can mislabel positive pool draws as NEGATIVE_FAULT.
- **#3 High** (`id_helpers.py`): `is_id_like('id')` originally False → ~30% of body-parameter rows dropped from denominators (appears fixed by the 05-01 run, but the denominator was unstable across the period).

**Net:** the SFHR numbers are the only quantitative smart-fetch-effectiveness evidence in the repo, but from a pipeline with documented, partially-resolved bugs, on a small variable sample. **Indicative, not benchmark-grade.**

### 4.4 Other quantitative evidence

- D4 definition/threshold: `debug/inputs/input-quality-measurement-framework.md:40,112-137,180,323,334`.
- `debug/inputs/microservice-input-quality-research.md` calls SFHR "THE central microservice input metric."
- **Unrelated** "JIT Binding Hit Rate" (88.75% example) in `docs/SemanticDependencyRegistry-Architecture.md:946-967` — a *different* subsystem; smart-fetch is the **fallback** it activates on dictionary miss (`:362,847,884`). **Do not conflate.**
- **Zero** SFHR/hit-rate numbers anywhere under `paper/` (grep-confirmed).

---

## 5. Quality framework (`smart-fetch-quality-framework.md`, 669 lines, 2026-05-05)

Measures the *internal process* (discovery → endpoint → extraction → cache → learning), distinct from the parent D1-D10 framework. **7 families, 12 named metric IDs verifiable in the body** (the docs' own "17 metrics" headline is never reconciled — cite "12 named IDs, 7 families"):

S1.1 Service Discovery Precision (KPI ≥0.50) · S1.2 Service Discovery Recall (≥0.40) ·
S2 Endpoint Selection Hit Rate (≥0.70) · S3.1 Direct-Extraction Hit Rate (≥0.65) ·
S3.2 Semantic-Field-Match F1 (≥0.60) · S4.1 Cache Hit Rate (≥0.50) · S4.2 Cache Value Diversity
(H ≥0.8·log₂n) · S4.3 TTL Staleness Risk (≤0.30) · S5.1 Registry EMA Convergence (≥0.5 @ ≥30
invocations) · S5.2 Mapping Stability (≥0.85 across consecutive runs) · S6 End-to-End Yield
(yield_smart ≥0.80 / yield_accepted ≥0.60) · S7 Trace-Priority Beat (uplift ≥+0.10).

3 explicit implementability gaps (`:507-515`): S1.2 needs a gold-producer artifact (unbuilt); S3.2 needs one extra log field (~3 LOC); S5.1 needs a per-mapping history buffer. **None of S1-S7 has ever been computed** (no trace CSV, no gold CSV, no calculator script exists). The framework is designed-but-unrun — a ready-made instrument a new paper could actually execute.

---

## 6. The audit as paper material

### 6.1 Headline numbers

Round 1: 40 findings (4 Critical / 12 High / 18 Medium / 6 Low), 39 fixed. Round 2 (fresh review): 30 findings (5 High / 11 Medium / 14 Low), 25 fixed. **Total 70 findings, 64 fixed, 6 disclosed deferrals.** Registry migration: endpoint rows 175→82; NO_GOOD_MATCH 2→0; fabricated `*/query` 56→0; `{paramName}` placeholders 27→0; successRate-0.0 share 75%→46% on that pass (the <30% target was NOT met then; later fully zeroed by the deliberate 2026-06-10 de-poison).

**Caution:** `smart-fetch-bug-audit.md`'s own "Fix Status" column reads "Not fixed" for all 40 (frozen original); actual status lives in `execution-summary.md` + `verification-final.md` (38/40 verified fixed with file:line evidence). Do not cite the audit table's status column.

### 6.2 Five findings revealing design tensions

1. **Registry pollution is a one-way ratchet**: discovery re-runs only when `mappings.isEmpty()`, so one bad discovery (literal `NO_GOOD_MATCH` rows; fabricated `/api/v1/<svc>/query` endpoints) becomes permanent — a "learning" registry with no forgetting (external migration script was the only self-heal). [Post-audit: discovery-fallback-on-all-failing + quarantine mitigate this.]
2. **"JSONPath is retired" was only half-true** — 12 dead JSONPath-era methods + 2 orphan registry keys survived until the audit's Theme-7 cleanup deleted them.
3. **A one-character-class bug defeated the freshness axis**: `recentnessScore` computed from `compareTo` (±1) not a duration → ≈1.0 for every mapping; the fix also had to gate recency on `successRate > 0` so fresh unvalidated discoveries can't win on freshness alone.
4. **Learning was persisted asymmetrically**: `saveRegistry()` originally only fired from discovery, so Priority-1 EMA updates were silently lost on JVM exit for exactly the well-populated registries where discovery never re-triggers. Patched (registryDirty + flushIfDirty at scenario boundary/shutdown).
5. **Shipped "mature" registry and EMA-learning are currently decoupled**: 100% zero successRate by design; ranking = priority + name-affinity prior. A paper claiming the registry "learns" must be precise about which half is currently true (structural coverage yes; learned scores await A2).

---

## 7. How the current MIST papers describe smart fetch

`paper/full-paper/main.tex`: abstract names SmartInputFetcher (no numbers); §4.5 dedicated subsection (`\label{ssec:smartfetch}`, lines 425-441) — **stale on two points**: says registry maps parameters to endpoints "with JSONPath-based extractors" (current path is LLM DIRECT_EXTRACTION) and asserts a "50% probability" split (lines 292, 696) that matches no current config (`SmartInputFetchConfig.java:80` defaults 1.0 "grounding-first"; SockShop/Boutique props set 1.0; only the frozen process doc's worked example uses 0.3). Evaluation is TrainTicket-only, 5 traces × 10 variants, qualitative; exactly one smart-fetch figure (`fig:smartfetch_body`: station-names body → 200). **Zero quantitative SFHR/hit-rate content anywhere in paper/** — a new paper adds the first quantitative smart-fetch evaluation, duplicating nothing. Sibling `Nostep_version.tex` differs slightly in §smartfetch wording. `poster.tex:109` and `paper/tool-demo/main.tex:126` mention it once each (tool-demo: one of three "positive sources" — shared pool, smart fetch, JIT bind — in the Sniper negative-test algorithm).

---

## 8. Config surface (`SmartInputFetchConfig.java`, defaults from no-arg ctor `:73-106`)

| Property | Default | Notes |
|---|---|---|
| smart.input.fetch.enabled | false | master switch |
| smart.input.fetch.percentage | **1.0** | "Grounding-first default" (comment `:76-80`) |
| smart.input.fetch.registry.path | input-fetch-registry.yaml | |
| smart.input.fetch.openapi.spec.path | "" → falls back to `oas.path` | no hardcoded SUT spec |
| smart.input.fetch.llm.discovery.enabled | true | |
| smart.input.fetch.llm.endpoint.selection.enabled | true | |
| smart.input.fetch.max.candidates | 5 | ranked mappings tried per parameter |
| smart.input.fetch.dependency.resolution.enabled | true | largely superseded by MST context/trace handling |
| smart.input.fetch.discovery.timeout.ms | 5000 | legacy single timeout |
| smart.input.fetch.connect.timeout.ms / read.timeout.ms | fall back to discovery timeout | split per audit #28 |
| smart.input.fetch.cache.enabled / cache.ttl.seconds | true / 300 | |
| smart.input.fetch.default.priority / pattern.discovery.priority / llm.discovery.priority | 5 / 5 / 7 | |
| smart.input.fetch.http.content.type / http.success.code | application/json / 200 | |
| smart.input.fetch.schema.discovery.timeout.ms | 3000 | |
| smart.input.fetch.max.prompt.chars | 8000 | was hardcoded 2044 ×9 pre-audit; default sized for qwen2.5-coder:14b 32K context |
| smart.input.fetch.cache.llm.fallback | false | only real-upstream values fill the diverse cache by default |
| smart.input.fetch.ema.alpha | 0.1 | clamped (0,1] |
| smart.input.fetch.decay.days | 30 | |
| smart.input.fetch.diverse.target.count | 10 | |
| auth.admin.username / auth.admin.password | "" / "" | no baked-in credential |
| auth.login.path | /api/v1/users/login | TT-shaped default, overridable |
| auth.login.username.field / password.field | username / password | |
| auth.token.json.path | data.token | |
| auth.token.validity.minutes | 30 | |

---

## 9. Other paper-relevant material

**Tests** (all under `mist-core/src/test/java/io/mist/core/smart/`): `InputFetchRegistryPoolStatusTest` (123 LOC — verified-pool semantics, YAML round-trip), `ProducerRankingTest` (61 — cold-start nameAffinity/rankingScore, token-boundary correctness, prior off once feedback exists), `ShippedRegistryDepoisonTest` (85 — data lint: all-zero rates; skips if registry files missing), `TTEndStationLiveCheck` (108 — live-gated e2e grounding vs the poisoned-producer failure mode). **No integration test drives MistGenerator/SharedPoolSupport smart-fetch call sites.**

**Auth limitation:** single-arg `SmartFetchAuthManager` ctor still defaults to the TrainTicket shape (5-arg ctor fully configurable); class Javadoc still says "for TrainTicket system."

**Percentage gate:** `fetchSmartInputWithProvenance` `:306-346` — disabled → fallbackToLLM; else roll `nextDouble() < percentage`; false branch logged "🤖 LLM Decision", straight to LLM. Per-call, not a global quota.

**Diverse-cache rotation:** `getNextDiverseValue` `:1761-1795`, atomic `valueRotationIndex.compute` under `synchronized(values)` (audit #30 fix); `resetValueRotation()` (`:1735`, called per scenario from `MistGenerator.java:600`) clears cursor AND cache contents (F26), so trace-observed values don't leak across scenarios.

**EMA scoring:** `updateSuccessRate`: `successRate = α·(success?1:0) + (1-α)·successRate`, α default 0.1. `calculateScore()`: `0.5·(priority/10) + 0.3·successRate + 0.2·recentness`, recentness gated to 0 unless successRate > 0. Currently exercised against all-zero registries → ranking reduces to priority + nameAffinity prior; state this precisely.

**One more artifact:** `debug/inputs/bugfixes/2026-05-05-critical-bugfixes.md` — eleven critical fixes (C1-C11) from the D1-D10 + smart-fetch audits (fix-level granularity if needed).
