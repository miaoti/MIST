# Smart Input Fetch — Execution Summary (final)

Date: 2026-05-05
Plan: [`refinement-plan.md`](./refinement-plan.md)
Original audit: [`smart-fetch-bug-audit.md`](./smart-fetch-bug-audit.md)
Quality metrics: [`smart-fetch-quality-framework.md`](./smart-fetch-quality-framework.md)
Implementation map: [`dataflow-map.md`](./dataflow-map.md)
Migration tool: [`scripts/migrate_registry.py`](./scripts/migrate_registry.py)

This is the consolidated record of every change applied to the Smart Input Fetch subsystem during this audit cycle. Build is green (`mvn compile` → BUILD SUCCESS). Registry has been cleaned. Two independent verification passes plus a fresh deep code review have run; everything they flagged that was in scope and value-positive has been fixed.

## Compile status

✅ `mvn clean compile` (when GoogleNews resource copy succeeds) and `mvn compile -Dmaven.resources.skip=true` both → **BUILD SUCCESS**. No errors, only pre-existing warnings in unrelated files.

## Registry migration result

| Metric | Before | After | Δ |
|---|---|---|---|
| Total mappings (`endpoint:` rows) | 175 | 82 | −53 % |
| Mappings with `successRate: 0.0` | 130 | 38 | −71 % |
| `service: NO_GOOD_MATCH` rows | 2 | 0 | −100 % |
| Fabricated `*/query` endpoints | 56 | 0 | −100 % |
| Literal `{paramName}` placeholders | 27 | 0 | −100 % |
| Distinct service strings (case-folded) | 14 | 14 | (already canonical post-cleanup) |

Backup of pre-migration registry preserved at `src/main/resources/My-Example/trainticket/input-fetch-registry.20260505015858.bak.yaml`.

## Aggregate counts

### Original 40-finding audit (round 1)

| Severity | Total | ✅ Fixed | ⏸ Deferred |
|---|---|---|---|
| Critical | 4 | 4 | 0 |
| High | 12 | 12 | 0 |
| Medium | 18 | 17 | 1 (#19 doc cross-reference) |
| Low | 6 | 6 | 0 |
| **Total** | **40** | **39** | **1** |

Plus reviewer-mandated additions: 7 of 8 applied (C1, C2 via #15, C4, C6, C7, C19, C22 ✅; C5 ConcurrentHashMap hardening of registry data classes still deferred — only manifests under multi-fetcher concurrency that does not exist today).

### Fresh deep code review (round 2 — 30 new findings)

| Severity | Total | ✅ Fixed | ❌ Skipped (out-of-scope or opinionated) |
|---|---|---|---|
| High | 5 | 5 | 0 |
| Medium | 11 | 9 | 2 (F12 LLMService singleton — not smart-fetch's concern; F18 ServicePattern — needs YAML migration, deferred) |
| Low | 14 | 11 | 3 (F16 isUUIDValue strict — current behavior correct; F21 removeMapping — zero callers; F24 REGISTRY_CACHE — only one path used) |
| **Total** | **30** | **25** | **5** |

## What was fixed (consolidated)

The fixes group naturally into themes. File:line citations point to the current source; every change is annotated in code with either `Bug audit Finding #N` (round 1) or `Fresh-review Finding F<n>` (round 2).

### Theme 1 — Data hygiene (round 1: #2, #4, #15, #22, #33; round 2: F3)

- LLM-suggested service names are validated against `getAllAvailableServices()`; the `NO_GOOD_MATCH` sentinel is dropped at both single-token and array-element sites.
- Service names are lowercase-canonicalized before persistence.
- `ApiMapping` carries an optional `consumerApiKey` field; `addMapping(consumerApiKey, paramName, mapping)` scopes new entries; `getMappingsForParameter(consumerApiKey, paramName)` returns scoped-then-global. Legacy YAML entries with no scope load as global (zero migration loss).
- `inferEndpointForService` returns `null` instead of fabricating `/api/v1/<svc>/query`.
- `pickFirstReasonableEndpoint` is also gated; no real-but-irrelevant heuristic fallback.
- `SmartLLMParameterGenerator.createParameterInfo` now sets `apiName` so consumer-scoping works for that path too.

### Theme 2 — Path-parameter handling (round 1: #3, #23)

- `resolveFetchableEndpoint` strips trailing `/{...}` segments at fetch time; mid-path placeholders cause the candidate to be dropped.
- `addMapping` rejects non-GET methods at registration; `fetchFromApiMapping` honors `mapping.getMethod()` (warns + falls back to GET if non-GET ever sneaks in).

### Theme 3 — ID-semantics consistency (round 1: #5, #18; round 2: F4, F5, F6)

- All boundary-aware `paramName.contains("id")` sites swapped to `isIdLikeParamName(...)` (boundary-aware regex covering `Id`, `ID`, `UUID`, `Uuid`, `_id`, `_uuid`, bare `id`/`uuid`).
- `isValidLLMResponse` no longer false-positives on `paramName.contains("route")`, `contains("number")`.
- `isNonsensicalValue` now gates the numeric-check on `isNumericSchemaType(parameterInfo)` instead of `paramName.contains("distance/price/rate")`.

### Theme 4 — Hot-path performance + score math (round 1: #1, #6, #13, #14, #24, #27, #28, #30, #38; reviewer C1, C5, C6, C19, C22; round 2: F1, F9, F11, F17)

- `ApiMapping.calculateScore()` uses `ChronoUnit.DAYS.between(...)` divided by configurable `decayDays`.
- Registry is loaded **before** caches are constructed so `CacheConfig.maxEntries` from YAML is honored (previously the field always returned the literal 1000).
- Process-wide `static ConcurrentMap` registry cache (mtime-keyed) shared across `SmartLLMParameterGenerator` instances.
- Caches are bounded LRU (`Collections.synchronizedMap(LinkedHashMap with removeEldestEntry)`) sized from `CacheConfig.maxEntries`.
- `valueRotationIndex` rotation uses atomic `compute(...)` to close the read-modify-write race.
- `containsKey`-then-`get` patterns replaced with single-`get`+null-check (no TOCTOU NPE).
- One JVM shutdown hook total (class-level `LIVE_FETCHERS` set) instead of one per fetcher; no GC leak.
- Atomic write (`Files.move(... ATOMIC_MOVE)` with non-atomic fallback) in `InputFetchRegistry.saveToFile`.
- Discovery saves use `registryDirty = true` flag; flush at scenario boundary + JVM shutdown (`flushIfDirty`).
- Connect/read timeouts split (`smart.input.fetch.connect.timeout.ms` / `.read.timeout.ms`).
- EMA learning rate (`smart.input.fetch.ema.alpha`) and decay window (`smart.input.fetch.decay.days`) are config-driven; applied to all mappings via `applyMappingTuning(...)`.
- `getRequiredValueCount` reads `SmartInputFetchConfig.diverseTargetCount` instead of `System.getProperty` directly.
- LLM endpoint-select retry distinguishes deterministic `NO_GOOD_MATCH` (1 forced attempt, then stop) from transient call failure (retry up to 3 times).

### Theme 5 — Generic auth + JWT lifecycle (round 1: #11, #12, #26; reviewer C4; round 2: F13, F27)

- `SmartFetchAuthManager` is now generic: configurable login URL, body fields, token JSON path, validity minutes (defaults preserve TrainTicket behavior).
- `getValidToken` and `invalidateToken` are `synchronized`; cached fields are `volatile`.
- 401/403 from `fetchFromApiMapping` triggers `authManager.invalidateToken()`.
- JWT prefix debug log removed; login-failed response body redacted (length only).
- Hardcoded `auth.admin.password = "222222"` default replaced with empty default — operators must configure explicitly; missing config means `isConfigured()` returns false and smart-fetch proceeds without auth instead of using a guessed credential.
- Dead `auth.user.*` config and allowlist entries removed.

### Theme 6 — Validation & extraction (round 1: #7, #17, #21, #34, #35, #36, #37; round 2: F7, F8, F15, F28)

- `isValidApiResponse`: 20-char floor removed; envelope shapes broadened (`data`/`result`/`payload`/`items`); `null` payload now rejected symmetrically.
- `cleanBooleanValue`: strict (only `true`/`false`/`1`/`0`) — synonyms like `yes`/`on`/`enabled`/`active` no longer laundered.
- `formatAsArrayValue`: rejects `NO_GOOD_MATCH`/`NO_VALUES_FOUND`/`NO_MATCH`/`NO_VALUES_GENERATED` sentinels before wrapping.
- `cleanIntegerValue`/`cleanNumberValue`: clamp to schema `minimum`/`maximum` after parse; fallbacks honor schema bounds.
- `isValidValueForParameter`: enforces schema bounds for numeric parameters (rejects out-of-range values instead of silently caching them).
- `generateFallbackSemanticValues`: dropped `_<idx>` suffix that produced strings like `"42_2"` for an integer parameter.
- `cacheLlmFallbackValues` config flag (default `false`): only smart-fetched values from real upstreams populate the diverse cache by default; LLM-fallback diversity is opt-in via `smart.input.fetch.cache.llm.fallback=true`.
- `isValidLLMResponse`: dropped the heuristic 100-char "looks like explanation" cap (schema `maxLength` is the authoritative size bound).

### Theme 7 — Cleanup, dead-code, prompt budget (round 1: #8, #9, #10, #16, #20, #25, #31, #32, #39, #40; round 2: F19, F20, F22, F23)

- 12 dead JSONPath methods deleted (`extractValueFromResponse`, `selectValueWithFallbackLogic`, `selectValueWithLLM`, `buildValueSelectionPrompt`, `truncateDataForLLM`, `askLLMForValueSelection`, `callLLMForValueSelection`, `guessExtractPath`, `guessPathByParameterName`, `isValidJsonPath`, `getApiResponseSchema`, `buildDataExtractionPrompt`, `askLLMForExtractionPath`, `callLLMForExtractionPathDiscovery`).
- `OpenAPIEndpointDiscovery.findBestEndpoint` + `ScoredEndpoint` + `scoreEndpoint` removed.
- `handleSingleValueParameterFromLLM` removed.
- `discoverByPatterns` no-op stub removed.
- `JsonPath`/`PathNotFoundException` imports dropped.
- 9 sites of the `2044` prompt cap converted to `config.getMaxPromptChars()` (default 8000, configurable). 4 small-prompt caps left intentionally smaller (single-decision LLM calls).
- `parameterErrors` keyed via `decodeUrlForKey` on both write and read paths (round-trip symmetry).
- `addMapping` deduplicates against existing `(endpoint, method, service, extractPath, scope)` tuples.
- Unused imports removed (`okhttp3.*`, `TimeUnit`, `Pattern`, `OpenAPISpecification`, `LLMConfig`, etc.).
- `baseUrl` trailing-slash normalized in constructor (matches `SmartFetchAuthManager`).
- Algorithmic last-resort fallback no longer appends a fake unit-char suffix.
- `loadOpenAPISpec` escalates configured-but-missing path to ERROR instead of silently degrading.

### Theme 8 — LLM call reduction (round 2: F29, F30)

- `inferSchemaTypeFromParameterName` no longer issues a per-fetch LLM call when type is unknown — defaults to `"string"`.
- `isRelevantField` no longer fires LLM as last-resort fallback for every JSON field — deterministic match only (substring, ID-stem, numeric-name).

### Theme 9 — Trace cache semantics (round 2: F26)

- `resetValueRotation` clears `cache` and `diverseValueCache` at scenario boundary so trace-observed values do not leak across scenarios (matches `flow.md:825` "session-scoped" intent).

### Theme 10 — Configuration plumbing (round 2: F2)

- `loadLLMProperties` uses prefix-based filter (`llm.*`, `auth.admin.*`) instead of explicit allowlist that previously dropped `llm.local.api.key` (DeepSeek auth) and similar keys.

## Deferred (with rationale)

| # | Severity | Why still deferred |
|---|---|---|
| #19 | Medium | Documentation cross-reference. The substantive doc lives in `dataflow-map.md`; adding the same to `flow.md` is polish |
| Reviewer C5 | Major | `InputFetchRegistry` data classes hardening to `ConcurrentHashMap`/`CopyOnWriteArrayList`. Race only manifests under multi-fetcher concurrency that does not exist in the current pipeline; the static registry cache is process-wide, so future scenarios could expose it. Track for future work |
| F12 | Medium | `LLMService.getInstance` first-init-wins singleton. Out of smart-fetch scope (LLMService is a system-wide class; the issue affects every LLM caller, not just smart-fetch) |
| F16 | Low | `isUUIDValue` strict 36-char match. Current behavior is correct for canonical UUIDs; broader matching (braced, urn-prefixed, compact) is opinionated and might cause unexpected acceptances |
| F18 | Low | `ServicePattern` dead state in YAML schema. Removing it cleanly requires a YAML migration step (Jackson would error on existing `servicePatterns:` blocks unless `@JsonIgnoreProperties(ignoreUnknown=true)` is added). Tracked |
| F21 | Low | `removeMapping` doesn't honor `consumerApiKey` scope. Method has zero callers; not exercised. Leave or delete in future cleanup |
| F24 | Low | `REGISTRY_CACHE` (process-wide cache in `SmartLLMParameterGenerator`) is unbounded. Only one path is used in practice; theoretical leak |

All deferrals are tracked here; nothing is silently skipped.

## What changed in the smart-fetch's purpose

**Nothing.** The subsystem still does the same thing it set out to do: harvest realistic upstream values via discovery → registry mapping → HTTP fetch → LLM extraction → cache → fallback. Every fix above either makes that pipeline more correct (validators, scoping, persistence), more efficient (fewer redundant LLM calls, bounded caches), or more honest (no silent config drops, no fabricated endpoints, no laundered booleans). The priority chain (P0 trace → P1 registry → P2 discovery → P3 LLM) is unchanged.

## File-by-file change footprint

| File | Approximate net delta |
|---|---|
| `SmartInputFetcher.java` | rich annotations + behavior changes; ~600 lines net removal (dead JSONPath chain) |
| `InputFetchRegistry.java` | +consumer-scope; +URL-key symmetry; +dedup; +atomic save; +error-cap |
| `SmartInputFetchConfig.java` | +12 config keys (timeouts split, prompt cap, EMA, decay, diverse target, LLM-fallback cache, generic auth) |
| `SmartFetchAuthManager.java` | configurable login plumbing; concurrency; redacted logs; empty-default password |
| `SmartLLMParameterGenerator.java` | process-wide registry cache; consumer-scope plumbing |
| `OpenAPIEndpointDiscovery.java` | deleted dead `findBestEndpoint`/`ScoredEndpoint` |
| `ApiMapping.java` | +consumerApiKey; configurable EMA / decay window |
| `scripts/migrate_registry.py` | new tool; one-shot YAML cleanup |

## How to use this folder

- **Read first**: this `execution-summary.md` (you are here) for the headline status.
- **For root-cause context per finding**: [`smart-fetch-bug-audit.md`](./smart-fetch-bug-audit.md) (40 original findings) — the in-code citations point back here.
- **For metrics & KPIs**: [`smart-fetch-quality-framework.md`](./smart-fetch-quality-framework.md) (17 metrics, 47 citations).
- **For implementation reality (call-graph, mutable state, prompts)**: [`dataflow-map.md`](./dataflow-map.md).
- **For the original action plan**: [`refinement-plan.md`](./refinement-plan.md).
- **To re-clean a polluted registry**: `python3 scripts/migrate_registry.py [path]`.
