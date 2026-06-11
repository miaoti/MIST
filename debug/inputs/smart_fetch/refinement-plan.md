# Smart Input Fetch — Refinement Plan

Date: 2026-05-05
Scope: `src/main/java/es/us/isa/restest/inputs/smart/*` plus the persisted registry `src/main/resources/My-Example/trainticket/input-fetch-registry.yaml` and the calling sites in `MultiServiceTestCaseGenerator.java`.
Source of findings: [`smart-fetch-bug-audit.md`](./smart-fetch-bug-audit.md) (40 findings).

This document **distills the bug audit into a prioritized work plan**, grouped by themed work-streams rather than by individual finding. The goal is to refactor smart-fetch incrementally without breaking the rest of the MST pipeline.

Severity legend: **C** = Critical, **H** = High, **M** = Medium, **L** = Low. Effort estimates are person-days for someone with the codebase loaded.

---

## Stream 1 — Stop persisting bad data into the registry  (effort: 2 d)

**Why first:** Findings #2, #4, #15, #22, #33 work together to silently poison the persisted YAML. A registry that is corrupt at the source means every downstream improvement (better scoring, better caching, better learning) is amplified noise. Until we plug the inflow, every other stream rebuilds on sand.

| Finding | Concrete change | File:line |
|---|---|---|
| **#2 (C)** Sentinel `NO_GOOD_MATCH` persisted as service | In `askLLMForServices` drop tokens equal to `NO_GOOD_MATCH` (case-insensitive) before returning. In `discoverByLLM` reject any name that is not in `getAllAvailableServices()`. | `SmartInputFetcher.java:3573-3589` (askLLMForServices); `:393-411` (discoverByLLM); `InputFetchRegistry.java:228-231` (prompt — clarify "NO_GOOD_MATCH may only be used as a *whole* response, never an array element"). |
| **#22 (H)** No validation of LLM-returned service name | Whitelist enforcement: validate every suggested service is a key in `openAPIDiscovery.getAllServices()` *or* a registered `ServicePattern.services` entry. | `SmartInputFetcher.java:393-411`. |
| **#4 (H)** Capitalization variants (`ts-User-service`, `ts-Travel-Service`, …) | Normalize service strings to lowercase **before** lookup, persist, or validation. Migrate existing registry once: collapse capitalization variants and merge their `successRate` (max). | `SmartInputFetcher.java:393-411`, `:3939` (fallback endpoint composer); registry one-shot migration script. |
| **#15 (H)** `addMapping` keys by bare `parameterName` | Change the key to `<consumerApiKey>::<paramName>` so two operations sharing `id` don't share producer mappings. Migrate registry: split each entry into per-consumer copies. (Optional: keep a fallback global tier for params that genuinely are global.) | `InputFetchRegistry.java:79-89`; `SmartInputFetcher.java:246-247`, `:317-320`. |
| **#33 (H)** `inferEndpointForService` synthesizes `/api/v1/<svc>/query` when discovery fails | Return `null` instead of fabricating. Skip the candidate. The LLM can re-suggest a valid service later. | `SmartInputFetcher.java:3938-3942`. |

After this stream, write a one-shot Python migration that prunes existing `NO_GOOD_MATCH` rows and `*/query` fabrications from the registry YAML (the auditor counted **130 of 174** mappings stuck at `successRate: 0.0`, and **60+ fabricated `*/query` endpoints**). Save the migrated copy under `input-fetch-registry.YYYYMMDD.bak.yaml` before overwriting.

---

## Stream 2 — Path-parameter substitution & method honoring  (effort: 1.5 d)

**Why second:** Finding #3 alone explains a large share of the persistent `successRate: 0.0` entries. Endpoints like `/api/v1/orderservice/order/{orderId}` GET'd verbatim always return 404. They get marked failed and stay in the candidate list forever. Pair this with #23.

| Finding | Concrete change | File:line |
|---|---|---|
| **#3 (C)** Path-parameter endpoints baked literally | Before fetch, scan endpoint for `{...}` segments. If any remain unresolved, either (a) skip the candidate, or (b) attempt to substitute from `traceProducerEndpoints` / from the diverse cache of the corresponding `<noun>Id` parameter, or (c) call the collection endpoint by stripping the trailing `/{...}` segment. | `SmartInputFetcher.java:423-476` (`fetchFromApiMapping`). |
| **#23 (M)** Always GET regardless of `mapping.getMethod()` | Either (a) honor the persisted `method` field, or (b) document that only GET is supported and reject persistence of non-GET mappings. Currently the YAML has 100% GET so this is harmless today, but the inconsistency is a footgun. | `SmartInputFetcher.java:427`. |

Add a unit test: a registry containing `endpoint: "/api/v1/foo/{id}"` should not produce a 404 fetch on a clean run.

---

## Stream 3 — Identifier-name handling consistency  (effort: 1 d)

**Why:** Three different code sites use three different definitions of "is this an ID parameter": (a) the boundary-aware `isIdTypedParam` regex at `:2123-2138`, (b) the loose `paramName.contains("id")` at `:2515` and `:2535`, (c) `selectValueWithFallbackLogic` heuristics at `:912-963`. The boundary-aware definition is correct (already cited as the fix for "paid" / "valid" / "humid" in `flow.md` line 1095). The loose ones contradict it.

| Finding | Concrete change | File:line |
|---|---|---|
| **#5 (H)** `isValidValueForParameter` loose ID rule | Replace `paramName.contains("id")` with the existing `isIdTypedParam(parameterInfo)` helper. | `SmartInputFetcher.java:2515-2517`. |
| **#18 (M)** `selectValueWithFallbackLogic` keyword cascade | Replace string-matching heuristics with schema type / format checks. | `SmartInputFetcher.java:912-963`. |
| Same loose check inside `isNonsensicalValue` | Replace with `isIdTypedParam`. | `SmartInputFetcher.java:2535`. |

This stream has the highest test surface — every parameter ever validated by smart-fetch goes through `isValidValueForParameter`. Add tests with `paid`, `valid`, `humid`, `aid`, `void` as parameter names.

---

## Stream 4 — Cache & registry hot-path performance  (effort: 1.5 d)

**Why:** Finding #13 is a per-call 1.6 MB YAML parse. On a 100-parameter run the wall time hit is order-of-seconds. Finding #14 multiplies persistent IO by N for any run with N discoveries.

| Finding | Concrete change | File:line |
|---|---|---|
| **#13 (C)** `createParameterInfoWithErrorContext` reloads whole YAML per param | Cache a single `InputFetchRegistry` reference in `SmartLLMParameterGenerator` and reuse it. Optionally invalidate on file mtime change. | `SmartLLMParameterGenerator.java:204-237`. |
| **#14 (H)** Registry YAML rewritten after every discovery | Buffer `saveRegistry()` calls and persist once per scenario (`generateScenarioVariants` boundary), or use a coalescing timer. | `SmartInputFetcher.java:323`. |
| **#24 (M)** Cache eviction missing | Either honor `CacheConfig.maxEntries` (LRU evict) or document that the cache is bounded only by parameter cardinality. | `SmartInputFetcher.java:93-95` (`new ConcurrentHashMap<>()` for `cache` and `diverseValueCache`). |
| **#27 (M)** EMA learning rate α=0.1 makes recovery slow | Make α configurable; alternatively use a sliding-window count (last 10 attempts) so a fresh upstream can recover from past failures within ~10 hits. | `ApiMapping.java:59-64`. |
| **#28 (M)** `discoveryTimeoutMs` reused as both connect AND read | Split into `connect.timeout.ms` (default 2 s) and `read.timeout.ms` (default 8 s). | `SmartInputFetcher.java:442-443`. |
| **#30 (M)** `valueRotationIndex` and `diverseValueCache` racing | Guard updates inside the same compute block; or move both into a single record. | `SmartInputFetcher.java:1480-1509`. |
| **#38 (M)** Up to 3 LLM calls on transient failure | Distinguish "LLM said NO_GOOD_MATCH" (don't retry) from "LLM call failed" (do retry once). Cap forced-selection at 1 retry. | `SmartInputFetcher.java:3965-3997`. |

Acceptance: after this stream, a run that previously took N seconds in YAML parsing should drop by ≥ 90%.

---

## Stream 5 — Score & freshness math  (effort: 0.5 d)

**Why:** Finding #1 alone is a 1-character-class change but disables the entire `lastUsed` axis of mapping ranking. Pair with #27 if you bundle them as one PR.

| Finding | Concrete change | File:line |
|---|---|---|
| **#1 (C)** `LocalDateTime.compareTo / 86400` | Replace with `ChronoUnit.DAYS.between(lastUsed, LocalDateTime.now())` and divide by a configurable decay window (default 30 d). Add unit test asserting older mapping scores below newer. | `ApiMapping.java:69-76`. |
| **#27 (M)** EMA learning slow recovery | (handled in Stream 4) | |
| **#6 (L)** `Math.max(5, 10)` | Replace with literal `10`, or expose as `smart.input.fetch.diverse.target.count`. | `SmartInputFetcher.java:1999`. |

---

## Stream 6 — Generic auth & token lifecycle  (effort: 2 d)

**Why:** Findings #11, #12, #26 all stem from the assumption that the SUT is TrainTicket. The `SmartFetchAuthManager` has the URL `/api/v1/users/login` hardcoded, the JSON body keys `username`/`password` hardcoded, the token field path `data.token` hardcoded, and a 30-minute expiry hardcoded. None of this is generic, and our framework aspires to be SUT-agnostic. The audit also notes that `auth.user.*` credentials are wired into config but never used.

| Finding | Concrete change | File:line |
|---|---|---|
| **#11 (H)** Hardcoded TrainTicket login | Extract login URL, body keys, response token JSONPath, and `tokenValidityMinutes` into `SmartInputFetchConfig`. Default to TrainTicket values to preserve current behavior. | `SmartFetchAuthManager.java:84-140`. |
| **#12 (M)** Dead `auth.user.*` credentials | Either wire `auth.user.*` to a second auth profile (admin vs. user role) or remove the dead config. | `SmartInputFetchConfig.java:33-34, 116-119`; `SmartInputFetcher.java:99-103`. |
| **#26 (H)** `invalidateToken` never auto-invoked on 401/403 | In `fetchFromApiMapping` after a 401 or 403 response, call `authManager.invalidateToken()` and retry once. | `SmartInputFetcher.java:445-475`. |

After this stream, the auth manager is no longer TrainTicket-specific and can be reused by any future SUT.

---

## Stream 7 — Response validation & extraction quality  (effort: 1 d)

| Finding | Concrete change | File:line |
|---|---|---|
| **#7 (M)** `isValidApiResponse` rejects responses < 20 chars | Drop the 20-char floor; rely on the empty `{}`/`[]` and explicit-failure-envelope checks already present at `:493-494`. | `SmartInputFetcher.java:497-500`. |
| **#17 (H)** `cleanBooleanValue` coerces unknown strings to `false` | Only convert literal `true`/`false` (case-insensitive). Pass everything else through to the validator, which can then reject. | `SmartInputFetcher.java:2779-2786`; `:3022-3030` (duplicate `formatAsBooleanValue`). |
| **#29 (M)** `selectValueWithFallbackLogic` skips array element 0 for `list`/`array` params | Drop the "element 1 is more representative" hack. Use first non-null element. | `SmartInputFetcher.java:923-929`. |
| **#34 (M)** `formatAsArrayValue` wraps `NO_GOOD_MATCH`/error strings as a 1-element array | Validate the inner value before wrapping. | `SmartInputFetcher.java:3143-3201`. |
| **#35 (M)** LLM-generated values added to `diverseValueCache` even when smart-fetch ultimately returns null | Only cache when smart-fetch returns a non-null value upstream. | `SmartInputFetcher.java:1188-1191`. |
| **#36 (M)** `cleanIntegerValue`/`cleanNumberValue` ignore schema bounds when synthesizing "1" | If `parameterInfo.getMinimum()` exists, use it as the fallback floor. | `SmartInputFetcher.java:2708-2766`. |
| **#37 (L)** Empty-`data` rejection misses `result`/`payload` envelope shapes | Inspect a configurable list of envelope keys (`data`, `result`, `payload`, `items`). | `SmartInputFetcher.java:512-520`. |

---

## Stream 8 — Dead code, prompt budget, and code-hygiene cleanup  (effort: 1.5 d)

**Why last:** None of these change behavior on a successful run, but they substantially reduce maintenance cost and prevent future bugs from hiding in dead branches. Bundle as a single PR after the behavior-changing streams have shipped.

| Finding | Concrete change | File:line |
|---|---|---|
| **#9 (M)** Dead JSONPath helpers | Remove `extractValueFromResponse`, `guessExtractPath`, `guessPathByParameterName`, `isValidJsonPath`, `getApiResponseSchema`, `buildDataExtractionPrompt`, `truncateResponseSchemaForLLM` (the older copy), `askLLMForExtractionPath`, `callLLMForExtractionPathDiscovery`. Document the deletion in `flow.md` ("retired in 2026-05"). | `SmartInputFetcher.java:885-907, 3272-3320, 3325-3384, 3389-3418, 3420-3448, 3450-3467, 3506-3511, 3670-3675`. |
| **#16 (L)** `OpenAPIEndpointDiscovery.findBestEndpoint` is dead | Delete it (and the helper `ScoredEndpoint` if no other callers). | `OpenAPIEndpointDiscovery.java:140-151, 277-289`. |
| **#25 (L)** `handleSingleValueParameterFromLLM` is dead | Delete. | `SmartInputFetcher.java:1260-1280`. |
| **#32 (M)** `discoverByPatterns` is dead-stub | Delete the method and its caller invocation; do not leave warning logs in the success path. | `SmartInputFetcher.java:303-305, 339-356`. |
| **#10 (H)** 2044-char prompt cap baked everywhere | Replace with a single config-driven `llm.prompt.max.chars` (default 8000 for Ollama qwen2.5-coder:14b, configurable down to 2044 for GPT4All). Each call site reads the same value. | `SmartInputFetcher.java:574, 859, 1593, 2080, 2920, 3281, 3725, 4007`. |
| **#8 (L)** Exception-as-control-flow | Replace `throw new Exception("No smart sources …")` at `:289` with `return null`; caller already handles null. | `SmartInputFetcher.java:289-290, 180-201`. |
| **#19 (M)** Trace-producer endpoints not persisted | Document explicitly in `flow.md` that they are session-scoped (this is intentional per design, but currently undocumented). No code change. | flow.md update only. |
| **#20 (M)** `generateAlgorithmicMinimalValue` ignores `minimum`/`maximum` | Clamp to bounds when known. | `SmartInputFetcher.java:1850-1869`. |
| **#21 (H)** `generateFallbackSemanticValues` appends `_<idx>` to numeric/array values | Use a different uniqueness mechanism — e.g., add 1 to the integer value, or vary the format. | `SmartInputFetcher.java:2293`. |
| **#39 (H)** Per-field LLM call in `isRelevantField` | Replace with a deterministic field-name-vs-param-name check (`equalsIgnoreCase`, or token-overlap > 0.5). LLM call only when no deterministic match. | `SmartInputFetcher.java:2397-2431`. |
| **#40 (M)** `parameterErrors` keyed by encoded URL | Decode `%xx` sequences before keying so that `/foo/%25` and `/foo/%` collapse to the same bucket. | `InputFetchRegistry.java:109-111`. |
| **#31 (L)** `JsonPath.read` without filter — implicit list semantics | (Removed by Stream 8 dead-code cleanup.) | `SmartInputFetcher.java:887`. |

---

## Cross-cutting tracking

### Dependency map

```
Stream 1 (data hygiene) ──▶ Stream 2 (path subst) ──▶ Stream 4 (perf) ─┐
                       └──▶ Stream 5 (score math) ──▶ Stream 4         │
                       └──▶ Stream 6 (auth) ──────────────────────────┐│
                                                                       ▼▼
Stream 3 (ID semantics) ───────────────────────────────▶ Stream 7 (validation) ──▶ Stream 8 (cleanup)
```

Streams 1, 3, 5, 6 are independent and can be parallelized. Stream 2 builds on Stream 1's whitelist. Stream 4 wants Stream 5's score fix (otherwise the registry persistence cost dominates). Stream 7 should run after Stream 3 (validators share the boundary-aware ID definition). Stream 8 is last.

### KPIs to validate the plan

| KPI | Source | Threshold (after fix) |
|---|---|---|
| Fraction of registry mappings with `successRate: 0.0` | registry YAML | drop from 75% to < 30% |
| Fraction of services with capitalization variants | registry YAML | drop from ≥ 30% to 0% |
| Number of `*/query` fabricated endpoints | registry YAML | drop from 60+ to 0 |
| Number of literal `{paramName}` endpoints fetched | registry YAML | drop from 17 to 0 |
| Wall-time spent in `InputFetchRegistry.loadFromFile` per scenario | new metric (S5.2) | drop ≥ 90% |
| `yield_smart` (S6 fraction served by smart-fetch, not LLM fallback) | new metric (S6) | climb ≥ 0.5 → ≥ 0.7 |

The full quality framework (`smart-fetch-quality-framework.md`) defines all 17 metrics and how to compute them.

### Effort summary

| Stream | Severity span | Effort (d) | Parallelizable? |
|---|---|---|---|
| 1 — Data hygiene | C+H | 2.0 | yes |
| 2 — Path subst | C+M | 1.5 | after 1 |
| 3 — ID semantics | H+M | 1.0 | yes |
| 4 — Hot path | C+H+M | 1.5 | after 1, 5 |
| 5 — Score math | C+M+L | 0.5 | yes |
| 6 — Auth | H+M | 2.0 | yes |
| 7 — Validation | H+M+L | 1.0 | after 3 |
| 8 — Cleanup | H+M+L | 1.5 | last |
| **Total** | | **11.0 d** | |

A two-engineer team can ship Streams 1+5+6+3 in week 1 (parallel), then Streams 2+4+7+8 in week 2 (with dependencies). One engineer would take ~3 sprint weeks at half capacity.

### Things I am explicitly NOT proposing to change

- The structure of the priority chain (P0 trace → P1 registry → P2 LLM discovery → fallback). The audit found the chain itself is correct; only individual nodes are buggy.
- The decision to call `extractValueDirectlyFromResponse` instead of JSONPath. This is the right call (per `flow.md` line 824); we are only proposing to **delete** the dead JSONPath siblings, not revive them.
- The closed-domain short-circuit for booleans/enums (`SmartInputFetcher.java:2046-2057`) and the ID-typed short-circuit (`:2070-2075`). These are good and were recently added; do not regress them.
- The trace-producer-endpoint Priority 0 path (Findings note that it works but is undocumented in flow.md re: session-scoping).
- The registry YAML schema. We propose data migration, not a schema change.

### Validation acceptance criteria

For each Stream, the corresponding PR should add or update at least one test under `src/test/java/es/us/isa/restest/inputs/smart/`. After all streams ship, a clean run on TrainTicket should produce a registry that:

1. Has no `service: "NO_GOOD_MATCH"` entries.
2. Has no fabricated `*/query` endpoints.
3. Has no literal `{paramName}` placeholders in `endpoint`.
4. Has only canonical (lowercase) service names.
5. Has < 30% of mappings stuck at `successRate: 0.0`.
6. Has `lastUsed` evidence that the freshness signal is now live (older mappings score lower in the candidate list).

If any of these are not met after a clean run, the corresponding stream did not land correctly.
