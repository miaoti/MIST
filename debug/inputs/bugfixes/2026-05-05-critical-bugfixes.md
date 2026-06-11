# Critical Bug Fixes — week of 2026-04-26 → 2026-05-05

Eleven high-impact fixes from this week. Six (C1–C6) came out of the input-quality framework (D1–D10); five (C7–C11) came out of a deep audit of the Smart Input Fetch subsystem.
Smaller hygiene fixes (FALLBACK exclusion, entity-list expansion, BOUNDARY accounting, log diagnostics, dead-code cleanup) are tracked in git history, not here.

## C1 · Negative-pool fault types fired at incompatible schemas

- **Where** — `inputs/InvalidInputType.java`, `generators/HardcodedInvalidInputGenerator.java`, `generators/ZeroShotLLMGenerator.java`
- **Before** — every fault type fired at every parameter regardless of schema. A `boolean` param received `OVERFLOW = "A".repeat(5000)`, `BOUNDARY = -1`, `SPECIAL_CHARACTERS = '<script>...'` — values that are really TYPE_MISMATCHes wearing the wrong label. D10 NIFP surfaced this as 248/248 schema-unbounded BOUNDARY, 28-impure boolean OVERFLOW, etc.
- **After** — new `InvalidInputType.appliesTo(oasType)` matrix. Boolean params now generate only TYPE_MISMATCH + NULL_INPUT; numeric params don't get SPECIAL/REGEX; strings without bounds skip BOUNDARY.
- **Impact** — D10 NIFP overall 74.76 % → 83.50 %; BOUNDARY schema-unbounded count 248 → 0.

## C2 · LLM API-key never reached the LLMService singleton

- **Where** — `generators/ZeroShotLLMGenerator.java` (and 3 sibling allowlists)
- **Before** — `loadLLMProperties()` had a hard-coded property allowlist that omitted `llm.local.api.key`. The first caller of `LLMService.getInstance()` (singleton, cached for the run) was therefore built with no key. **Result: 13 033 silent 401s over a 10-hour DeepSeek run; zero usable LLM output.**
- **After** — `llm.local.api.key` added to the allowlists in `ZeroShotLLMGenerator`, `TraceErrorAnalysisMain`, `TraceErrorAnalyzer`, and `TestGenerationAndExecution.smartProperties[]`.
- **Impact** — DeepSeek runs now actually authenticate. Confirmed in the post-fix run: 124+ successful responses, 0 failures.

## C3 · Indefinite HTTP/2 stream hang on hosted LLM endpoints

- **Where** — `llm/LLMService.java`
- **Before** — the `OkHttpClient` was built with `connectTimeout = readTimeout = callTimeout = 0`. Reasonable for an on-host Ollama daemon; catastrophic for a hosted API. A stalled DeepSeek HTTP/2 stream parked the JVM main thread in `Http2Stream.waitForIo()` for 3–4 + minutes per stuck request. OkHttp 4.10 also has a known bug where per-call timeout overrides don't apply to streams reused from a parent zero-timeout connection pool.
- **After** — separate `hostedHttpClient` with its **own** ConnectionPool (no inheritance), 30 s connect / 120 s read / 180 s call. A daemon `WATCHDOG` `ScheduledExecutor` invokes `call.cancel()` at 185 s as belt-and-braces. Local-URL clients keep the original zero-timeout behaviour.
- **Impact** — a stalled hosted call now aborts cleanly and triggers the per-call cascade (DeepSeek → Ollama → caller fallback) instead of blocking the run forever.

## C4 · D7 NLP detector matched bare `name` parameters

- **Where** — `scripts/validate_d7.py`
- **Before** — regex `^name$|Name$` (case-insensitive) matched the bare `name` parameter, so train labels like "Express Train", "Black Hawk", "Blue Comet" were scored as failed city/person entities. **D7 = 22.42 %** in the Ollama baseline, dragged down by 197 false-negative `name` rows (0 / 197 realistic).
- **After** — `[a-z]Name$` requires a qualifier prefix; bare `^name$` moved to ANTI_PATTERNS. Only `contactsName`, `firstName`, `cityName`, etc. are scored as NLP-typed.
- **Impact** — D7 = **96.88 %** on the same data. Real-cities miss list shrinks from dozens to 6.

## C5 · D8 entropy normalised by distinct-count instead of pool size

- **Where** — `scripts/validate_d8.py`
- **Before** — `H_norm = H / log₂(distinct)`. A 100-row pool collapsed to two values used 50/50 scored `H_norm = 1.0` — looks perfect, hides the collapse. Spec §D8 explicitly says the ceiling is `log₂ n` where `n` is **pool size**.
- **After** — `H_norm = H / log₂(total)`. Same collapsed pool now scores `1 / log₂(100) ≈ 0.15` — correctly flagged as low-diversity. Locked in by `test_h_norm_uses_pool_size_not_distinct`.
- **Impact** — the diversity gate now actually catches pool collapse instead of rubber-stamping every binary-uniform pool.

## C6 · D10 TYPE_MISMATCH check unsound for string schemas

- **Where** — `scripts/validate_d10.py`
- **Before** — for `type: string`, the check called `json.loads(value)` and flagged anything that parsed as a non-string. The execution log strips outer quotes (`"12345"` and `12345` look identical), so legitimate string values were marked impure. The result distorted TYPE_MISMATCH and (via the cross-check) SEMANTIC_MISMATCH purity rates.
- **After** — uses the `(javaType: T)` annotation that the negative pool already emits. `javaType: Integer` against a `type: string` schema is authoritatively a TYPE_MISMATCH; `javaType: String` is not. Falls back to value-shape inference only when no annotation is present.
- **Impact** — TYPE_MISMATCH purity 86 % → 89 %; SEMANTIC_MISMATCH purity 63 % → 78 % (cross-check no longer dispatches false-positive type mismatches).

---

## C7 · `ApiMapping.calculateScore()` — recentness math always returned ≈ 1.0

- **Where** — `inputs/smart/ApiMapping.java:69-76`
- **Before** — `recentnessScore = Math.max(0, 1 - (LocalDateTime.now().compareTo(lastUsed) / (24.0 * 60 * 60)))`. `LocalDateTime.compareTo` returns `-1` / `0` / `+1` (sign of ordering), not a duration. Dividing by 86 400 always yields ≈ 0, so `recentnessScore` was effectively `1.0` for every mapping regardless of staleness. The 0.2-weight freshness axis was dead — a mapping last used in November scored identical to one used yesterday. The persisted `lastUsed` field was decorative.
- **After** — `long days = ChronoUnit.DAYS.between(lastUsed, now()); recentnessScore = max(0, 1 - days / decayDays)` with configurable `decayDays` (default 30) via `smart.input.fetch.decay.days`.
- **Impact** — freshness signal restored; stale upstreams now lose ranking weight at one-per-day. Verified by re-grep: `grep -n "compareTo(lastUsed)"` returns 0.

## C8 · `NO_GOOD_MATCH` sentinel + fabricated `*/query` endpoints persisted into the registry

- **Where** — `inputs/smart/SmartInputFetcher.java` (`discoverByLLM`, `askLLMForServices`, `inferEndpointForService`)
- **Before** — when the LLM emitted the `NO_GOOD_MATCH` sentinel as a service-name array element, `askLLMForServices` returned it as a literal service. `inferEndpointForService` then synthesised `/api/v1/<svc>/query` (line 3939, pre-fix) and persisted the mapping. Hard YAML evidence: `grep -c 'service: "NO_GOOD_MATCH"' input-fetch-registry.yaml` = **2**; `grep -cE '/query"$'` = **56** fabricated endpoints. The sentinel even leaked into request payloads — Jackson `int`-deserialization of `"NO_GOOD_MATCH"` was logged in the registry's `parameterErrors` section.
- **After** — sentinel filter at the array-element level (`SmartInputFetcher.java:496-500`); LLM-suggested service names whitelisted against `getAllAvailableServices()` (`:484-507`); `inferEndpointForService` returns `null` instead of fabricating `/query` (`:3856-3882`); `pickFirstReasonableEndpoint` heuristic gated behind the same null-on-fail rule (Reviewer C7).
- **Impact** — registry post-migration: `NO_GOOD_MATCH` rows **2 → 0**, `*/query` fabrications **56 → 0**, total mappings **175 → 82**.

## C9 · Path-templated endpoints (`/order/{orderId}`) GET'd literally — guaranteed 404

- **Where** — `inputs/smart/SmartInputFetcher.java` `fetchFromApiMapping`
- **Before** — registry rows like `endpoint: "/api/v1/orderservice/order/{orderId}"` were GET'd with the literal `{orderId}` segment in the URL. Every request returned 404; `mapping.updateSuccessRate(false)` ran on each retry; the mapping never recovered. `grep -cE 'endpoint:.*\{' input-fetch-registry.yaml` = **27** such rows in the pre-migration registry.
- **After** — new helper `resolveFetchableEndpoint` strips trailing `/{...}` segments greedily (so `/order/{orderId}` → `/order`); mid-path placeholders return `null` and the candidate is dropped (`:609-628`).
- **Impact** — registry post-migration: literal `{paramName}` placeholders **27 → 0**. The collection-style endpoints continue to harvest IDs from the JSON response.

## C10 · `SmartLLMParameterGenerator.createParameterInfoWithErrorContext` re-parsed the 1.6 MB registry per call

- **Where** — `inputs/smart/SmartLLMParameterGenerator.java:213` (pre-fix)
- **Before** — every parameter generation ran `InputFetchRegistry.loadFromFile(registryFile)`. The TrainTicket registry is **51 232 lines / 1.34 MB**; on a 100-parameter scenario this is 100 × YAML parse + Jackson hydrate + DTO conversion. Wall time spent in `InputFetchRegistry.loadFromFile` dominated the param-info construction phase.
- **After** — process-wide `static ConcurrentMap<String, CachedRegistry>` keyed by registry path, mtime-invalidated (`SmartLLMParameterGenerator.java:211-241`). Loaded once, reused across all `SmartLLMParameterGenerator` and `SmartInputFetcher` instances. Reviewer-mandated process-wide scope (Comment 19) so two consumers don't drift.
- **Impact** — per-parameter YAML parse cost amortised to one parse per registry mtime change. The registry-load wall-time line item disappears from the profile of a multi-scenario run.

## C11 · Registry mapping keyed by bare parameter name — cross-service collision on shared names like `id`

- **Where** — `inputs/smart/InputFetchRegistry.java` (`addMapping` / `getMappingsForParameter`)
- **Before** — `parameterMappings: Map<String, List<ApiMapping>>` keyed by bare `parameterName`. Two operations that both have a parameter named `id` (or `name`, `userId`, `routeId`) shared one candidate list — a discovery for `POST /orderservice/orders` would pollute `GET /userservice/users`'s lookup. The plan's reviewer flagged this as the registry's structural defect; without consumer scope, the registry "learning" was indiscriminate.
- **After** — new `consumerApiKey` field on `ApiMapping` (`ApiMapping.java:17`); `addMapping(consumerApiKey, paramName, mapping)` stamps the consumer (`InputFetchRegistry.java:156-186`); `getMappingsForParameter(consumerApiKey, paramName)` returns scoped-then-global (`:116-139`). Legacy entries with no recorded scope load as global, so existing learning is preserved (zero migration loss). `SmartLLMParameterGenerator.createParameterInfo` now sets `apiName` so the consumer key is available at lookup time.
- **Impact** — new discoveries are scoped to the consumer that triggered them; future cross-service pollution is structurally prevented while the global tier keeps the existing 82 mappings reachable.

---

**Tests** — 16 / 16 Java unit tests + 148 / 148 Python pipeline tests pass; `mvn -o compile` → BUILD SUCCESS for the C7–C11 changes.
**Status** — all eleven fixes shipped to `inject-detection` branch on 2026-05-05.
