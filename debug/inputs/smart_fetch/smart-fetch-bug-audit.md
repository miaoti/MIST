# Smart Input Fetch Subsystem — Bug Audit

**Scope:** `src/main/java/es/us/isa/restest/inputs/smart/*` (SmartInputFetcher, InputFetchRegistry, SmartLLMParameterGenerator, ParameterErrorAnalyzer, SmartFetchAuthManager, OpenAPIEndpointDiscovery, SmartInputFetchConfig, CacheConfig, ServicePattern, ApiMapping, ParameterError) plus the learned registry `src/main/resources/My-Example/trainticket/input-fetch-registry.yaml` and the calling site in `MultiServiceTestCaseGenerator.java`.

Audit date: 2026-05-05.

## Fix Status

| #  | Finding                                                                                          | Severity | Status        |
|----|--------------------------------------------------------------------------------------------------|----------|---------------|
| 1  | `ApiMapping.calculateScore()` recentness math broken (compareTo / 86400)                          | Critical | Not fixed |
| 2  | LLM `NO_GOOD_MATCH` token persisted as a service name & endpoint                                  | Critical | Not fixed |
| 3  | Path-parameter endpoints baked literally into registry / fetched without substitution            | Critical | Not fixed |
| 4  | LLM-returned service names persisted with random capitalization variants                          | High     | Not fixed |
| 5  | `isValidValueForParameter` uses loose `paramName.contains("id")` for ID rules                     | High     | Not fixed |
| 6  | `getRequiredValueCount` is `Math.max(5, 10) = 10` — useless ternary                               | Low      | Not fixed |
| 7  | `isValidApiResponse` rejects responses < 20 chars                                                 | Medium   | Not fixed |
| 8  | "No smart sources available" thrown then immediately caught — wasteful exception flow             | Low      | Not fixed |
| 9  | Dead JSONPath helpers still wired (`extractValueFromResponse`, `guessExtractPath`, …)             | Medium   | Not fixed |
| 10 | 2044-character LLM prompt limit hardcoded in 8+ places                                            | High     | Not fixed |
| 11 | `SmartFetchAuthManager` is TrainTicket-specific (URL/keys/token-path/expiry hardcoded)            | High     | Not fixed |
| 12 | `auth.user.*` credentials wired into config but never used anywhere                               | Medium   | Not fixed |
| 13 | `SmartLLMParameterGenerator.createParameterInfoWithErrorContext` reloads 51K-line YAML per param  | Critical | Not fixed |
| 14 | Registry YAML rewritten after every discovery batch                                               | High     | Not fixed |
| 15 | `registry.addMapping(parameterName, …)` keys by bare name — cross-service collision               | High     | Not fixed |
| 16 | `OpenAPIEndpointDiscovery.findBestEndpoint` is dead code                                          | Low      | Not fixed |
| 17 | `cleanBooleanValue` coerces unknown values to `false` (silent type-mismatch laundry)              | High     | Not fixed |
| 18 | `selectValueWithFallbackLogic` uses `paramName.contains("id"|"name"|"title"|"list"|"array")`       | Medium   | Not fixed |
| 19 | `traceProducerEndpoints` not persisted across runs (session-scoped only)                          | Medium   | Not fixed |
| 20 | `generateAlgorithmicMinimalValue` ignores schema `minimum`/`maximum` bounds                       | Medium   | Not fixed |
| 21 | `generateFallbackSemanticValues` appends `_<idx>` suffix to numeric/array minimal values          | High     | Not fixed |
| 22 | LLM-generated `serviceName` not validated before persistence                                      | High     | Not fixed |
| 23 | `fetchFromApiMapping` ignores `mapping.getMethod()` — always GET                                  | Medium   | Not fixed |
| 24 | Cache eviction missing — `CacheConfig.maxEntries` is never enforced                               | Medium   | Not fixed |
| 25 | `handleSingleValueParameterFromLLM` is dead                                                       | Low      | Not fixed |
| 26 | `invalidateToken` never auto-invoked on 401/403                                                   | High     | Not fixed |
| 27 | EMA learning rate α=0.1 makes mappings recover slowly — many entries stuck at 0.0                 | Medium   | Not fixed |
| 28 | `discoveryTimeoutMs` (default 5 s) reused as both connect AND read timeout                        | Medium   | Not fixed |
| 29 | `selectValueWithFallbackLogic`/`extractAdditionalDiverseValues` skip element 0 of array           | Medium   | Not fixed |
| 30 | `valueRotationIndex` and `diverseValueCache` accessed without joint locking                       | Medium   | Not fixed |
| 31 | `extractValueFromResponse` calls `JsonPath.read` without filter — implicit list semantics         | Low      | Not fixed |
| 32 | `discoverByPatterns` is intentionally dead — pattern discovery silently disabled                  | Medium   | Not fixed |
| 33 | `inferEndpointForService` synthesizes `/api/v1/<svc>/query` when discovery fails                  | High     | Not fixed |
| 34 | `formatAsArrayValue` happily wraps NO_GOOD_MATCH/error strings as a one-element array             | Medium   | Not fixed |
| 35 | LLM-generated values added to `diverseValueCache` when smart-fetch returns null                   | Medium   | Not fixed |
| 36 | `cleanIntegerValue`/`cleanNumberValue` ignore `minimum`/`maximum` when synthesizing "1"           | Medium   | Not fixed |
| 37 | `isValidApiResponse` empty-`data` rejection misses other envelope shapes (`result`, `payload`)    | Low      | Not fixed |
| 38 | `selectEndpointWithLLMRetry` retries up to 3× even on transient failure → up to 3 LLM calls       | Medium   | Not fixed |
| 39 | `extractValueWithSimpleFallback`/`isRelevantField` issue an LLM call per JSON field               | High     | Not fixed |
| 40 | `parameterErrors` map keyed by encoded URL — `%25` payloads fragment the error registry           | Medium   | Not fixed |

## Severity rollup

- **Critical:** 4
- **High:** 12
- **Medium:** 18
- **Low:** 6

---

## Findings

### Finding 1: `ApiMapping.calculateScore()` recentness math is broken

**Severity:** Critical

**Location:** `src/main/java/es/us/isa/restest/inputs/smart/ApiMapping.java:69-76`

**Code excerpt:**
```java
public double calculateScore() {
    double priorityScore = priority / 10.0;
    double recentnessScore = Math.max(0, 1.0 -
        (LocalDateTime.now().compareTo(lastUsed) / (24.0 * 60 * 60))); // Days ago
    return (0.5 * priorityScore) + (0.3 * successRate) + (0.2 * recentnessScore);
}
```

**Bug:** `LocalDateTime.compareTo(...)` returns -1, 0, or +1 (per the JDK contract, it is a sign-of-ordering not a duration). Dividing that by `24*60*60 = 86400` yields ~0.0000116, so `recentnessScore` is effectively `1.0` for every mapping that exists, regardless of staleness. The intended `Duration.between(lastUsed, LocalDateTime.now()).toDays()` is missing.

**Impact:** The "freshness" axis of the score is dead — a mapping last used in November 2025 outranks a never-used mapping by exactly the same amount as one used yesterday. The composite score collapses to `0.5*priority/10 + 0.3*successRate + 0.2`, removing the freshness signal that the comment promises. Stale, rotted upstreams win ranking ties forever, defeating the whole `lastUsed` field that the registry persists per mapping.

**Evidence:** `grep -n "compareTo(lastUsed)" src/main/java/es/us/isa/restest/inputs/smart/ApiMapping.java:73`.
JDK contract: `java.time.LocalDateTime#compareTo(ChronoLocalDateTime)` returns `-1/0/+1`.

**Fix sketch:** Replace with `long days = ChronoUnit.DAYS.between(lastUsed, LocalDateTime.now()); double recentnessScore = Math.max(0.0, 1.0 - days/30.0);` (or whatever decay window is desired). Add a unit test that asserts a 30-day-old mapping scores below a 1-day-old one.

---

### Finding 2: LLM `NO_GOOD_MATCH` token is persisted as a service name and endpoint

**Severity:** Critical

**Location:** `src/main/java/es/us/isa/restest/inputs/smart/SmartInputFetcher.java:378-418` (`discoverByLLM`) + `:3939` (`inferEndpointForService` final fallback)

**Code excerpt (registry YAML evidence):**
```yaml
# input-fetch-registry.yaml:89-91
  - endpoint: "/api/v1/no_good_match/query"
    method: "GET"
    service: "NO_GOOD_MATCH"
    extractPath: "DIRECT_EXTRACTION"
    priority: 9
```

**Bug:** `discoverByLLM` does not validate the strings the LLM returns from `askLLMForServices(prompt)`. When the LLM emits the structured token `NO_GOOD_MATCH` *as one element of a JSON array* (the service-discovery prompt at `InputFetchRegistry.java:228-231` describes it as a top-level reply, not an element), `askLLMForServices` happily parses the array, calls `inferEndpointForService("NO_GOOD_MATCH", …)`, which falls through to line 3939 (`"/api/v1/" + service.toLowerCase().replace("ts-", "").replace("-service", "") + "/query"`) and produces `/api/v1/no_good_match/query`. The mapping is then persisted (line 318) and later refetched on every cache miss.

**Impact:** The registry YAML contains live mappings whose service is the literal sentinel string and whose endpoint is a fabricated path that does not exist in the SUT. `fetchFromApiMapping` will dial `http://<host>/api/v1/no_good_match/query`, get a 404, mark the mapping as failed, and on the next discovery attempt the persisted bad mapping pollutes the candidate list (it is in `getMappingsForParameter`). Because `discoverByLLM` is gated on `mappings.isEmpty()` (line 250), the system never re-discovers — so once a parameter has acquired a `NO_GOOD_MATCH` mapping it is stuck with it forever. We have hard YAML evidence that this happened twice (lines 91 & 275).

**Evidence:** `grep -n NO_GOOD_MATCH input-fetch-registry.yaml` → lines 91, 275 (service strings) and 89, 273 (synthesized endpoints). Errors logged in the registry's `parameterErrors` section confirm the SUT later returned "Cannot deserialize value of type `int` from String \"NO_GOOD_MATCH\"" (lines 32643, 47271) — i.e., the sentinel even leaked into request parameter values.

**Fix sketch:** In `askLLMForServices`, drop any returned token equal to `NO_GOOD_MATCH` (case-insensitive) before persisting. In `discoverByLLM`, validate that each suggested service name actually exists in `getAllAvailableServices()` and reject anything else; never call `inferEndpointForService` for strings that are not a real service.

---

### Finding 3: Path-parameter endpoints are persisted literally and fetched without substitution

**Severity:** Critical

**Location:** Mappings created in `SmartInputFetcher.java:399` (`new ApiMapping(endpoint, service, "DIRECT_EXTRACTION")`); fetched at `:423-431` (`fetchFromApiMapping`).

**Code excerpt (caller):**
```java
// SmartInputFetcher.java:423-432
private String fetchFromApiMapping(ApiMapping mapping, ParameterInfo parameterInfo) throws Exception {
    String url = baseUrl + mapping.getEndpoint();
    String httpMethod = "GET";
    log.info("🌐 API Call: {} {} for parameter '{}'", httpMethod, url, parameterInfo.getName());
    HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
```

**Code excerpt (registry):**
```yaml
# input-fetch-registry.yaml:105
  - endpoint: "/api/v1/orderservice/order/{orderId}"
# input-fetch-registry.yaml:1285
  - endpoint: "/api/v1/orderservice/order/security/{checkDate}/{accountId}"
```

**Bug:** The OpenAPI-derived endpoint paths contain `{orderId}`, `{tripId}`, `{stationName}`, etc. `inferEndpointForService` (line 3903) returns these unaltered, `discoverByLLM` persists them (line 399), and `fetchFromApiMapping` concatenates `baseUrl + mapping.getEndpoint()` and calls `new URL(...)` directly without substituting placeholders. There is no `path-template → real value` resolution anywhere.

**Impact:** Every fetch against a path-templated endpoint goes out as `GET http://host/api/v1/orderservice/order/{orderId}` with the literal curly braces in the URL. The HTTP server typically returns a 404 (or 400 if it tries to URL-decode `{orderId}`), `mapping.updateSuccessRate(false)` is called, and the mapping accumulates failures while never producing a value. There are 17 distinct path-templated endpoints in the registry (`grep -nE 'endpoint: ".*\{.*\}.*"' input-fetch-registry.yaml | wc -l = 17`), each one a wasted RTT plus an LLM call to re-decide on the next pass.

**Evidence:** `grep -n endpoint: input-fetch-registry.yaml | grep '{'` → 17 hits including `/api/v1/orderservice/order/{orderId}`, `/api/v1/travelservice/trips/{tripId}`, `/api/v1/orderservice/order/{travelDate}/{trainNumber}`, `/api/v1/orderservice/order/security/{checkDate}/{accountId}`.

**Fix sketch:** Either (a) filter path-templated endpoints out at discovery time (only persist endpoints whose `pathItem` has no path-level parameters); or (b) at fetch time, walk the path template and substitute placeholders from the diverse cache / context. Option (a) is far simpler and matches the existing intent of "fetch list endpoints to harvest IDs".

---

### Finding 4: LLM-returned service names persisted with random capitalization

**Severity:** High

**Location:** `SmartInputFetcher.java:378-418` (`discoverByLLM`); `:399` persists the LLM's raw service string into the YAML.

**Evidence (registry YAML):**
```
22  ts-travel-service
17  ts-route-service
16  ts-Travel-Service
11  ts-order-Service
 7  ts-route-Service
 7  ts-travel-Service
 4  ts-User-service
 2  ts-Travel-service
 1  ts-admin-User-service
```

**Bug:** The LLM's response is `TextNode.asText().trim()` (line 3575) and stored verbatim. There is no normalization step. Across runs, qwen2.5-coder:14b sometimes returns `ts-Travel-Service`, sometimes `ts-travel-service`, sometimes `ts-Travel-service`. They are written to the registry as different services even though they refer to the same SUT.

**Impact:** (1) `getAllAvailableServices()` returns duplicates that confuse the next LLM discovery prompt. (2) `fetchFromApiMapping` doesn't care about the service field, but `inferEndpointForService` looks up endpoints by `openAPIDiscovery.getEndpointsForService(service)` which is **case-sensitive** (`OpenAPIEndpointDiscovery.java:134`). So `ts-Travel-Service` returns an empty endpoint list, then falls through to the synthesized `/api/v1/Travel/query` (line 3939) — bug 33. (3) The registry size doubles or triples for no semantic reason.

**Evidence:** `awk -F'"' '/^    service:/ {print $2}' input-fetch-registry.yaml | sort | uniq -c | sort -rn` shows 28 distinct strings collapsing to ~14 real services.

**Fix sketch:** In `askLLMForServices`, normalize each returned service to its canonical form. The simplest reliable approach: look up the raw LLM string in `getAllAvailableServices()` case-insensitively and substitute the canonical capitalization, otherwise drop it (this also closes Finding 22).

---

### Finding 5: `isValidValueForParameter` uses loose `paramName.contains("id")` for ID rules

**Severity:** High

**Location:** `SmartInputFetcher.java:2515-2517`

**Code excerpt:**
```java
// For ID parameters, be more strict
if (paramName.contains("id")) {
    return isValidIdValue(value, parameterInfo);
}
```

**Bug:** This is the same loose substring check that was explicitly removed from `isIdTypedParam` at line 2130 (which uses the boundary-aware regex `^.+?(Id|ID|UUID|_id|_uuid)$`). English words ending or containing the substring "id" — `paid`, `valid`, `rapid`, `humid`, `solid`, `hidden`, `bid`, `decide` — all incorrectly trigger ID-validation rules. Any parameter whose name contains the letters "id" anywhere (e.g., `validUntil`, `humidity`, `rapidName`) gets routed through `isValidIdValue`, which rejects values containing spaces (`isValidIdValue:2649`). A station named `Hai Bridge`, fetched for a parameter named `validStation`, would be rejected.

**Impact:** Realistic values are spuriously rejected. The diverse-value pool starves and the fetcher falls back to `generateMinimalFallbackValue`, which is itself buggy (Finding 21). This contradicts the careful boundary-aware rule the codebase already implements 380 lines later.

**Evidence:** Grep `grep -n 'paramName.contains("id")' SmartInputFetcher.java` → lines 2515, 2535, 931, 1661, 1676, 1725, 2206, 3347 — eight loose checks. Compare to the boundary-aware rule at line 2123-2138.

**Fix sketch:** Replace the `paramName.contains("id")` checks with `isIdTypedParam(parameterInfo)` (the static helper already exists). This single change fixes Findings 5, 18 and most of the `paramName.contains` family in one pass.

---

### Finding 6: `getRequiredValueCount` is `Math.max(5, 10)` — useless ternary that always returns 10

**Severity:** Low

**Location:** `SmartInputFetcher.java:1996-2000`

**Code excerpt:**
```java
private int getRequiredValueCount(ParameterInfo parameterInfo) {
    // Estimate based on typical test generation needs
    // This could be made configurable or dynamic based on test suite size
    return Math.max(5, 10); // At least 5, preferably 10 diverse values
}
```

**Bug:** `Math.max(5, 10)` is a constant `10`. The comment claims "at least 5, preferably 10" but no variable expression participates. The whole method is morally a magic-number return.

**Impact:** Cosmetic — it always returns 10 — but the function is called from `extractAdditionalDiverseValues` (line 1571) to decide how many diverse values to harvest, and it ignores the parameter type, the OpenAPI `enum` size, the test-suite size, and the configurable `maxCandidates`. Boolean parameters get topped up to 10 LLM-generated booleans (and the LLM goes off-distribution into "yes/on/enabled" — see Finding 17).

**Evidence:** `grep -n "Math.max(5, 10)" SmartInputFetcher.java` → 1999.

**Fix sketch:** Replace with `parameterInfo.hasEnum() ? parameterInfo.getEnumValues().size() : config.getMaxCandidates()`, or wire it to the variant count from `MultiServiceTestCaseGenerator.computeTargetPoolSize`.

---

### Finding 7: `isValidApiResponse` rejects responses < 20 chars

**Severity:** Medium

**Location:** `SmartInputFetcher.java:497-500`

**Code excerpt:**
```java
if (trimmed.length() < 20) {
    log.debug("API response too short: {} chars", trimmed.length());
    return false;
}
```

**Bug:** A perfectly valid JSON response shorter than 20 characters — e.g., `{"data":[1,2,3]}` (16 chars), `{"id":"abc","ok":1}` (19 chars), `{"value":42}` (12 chars), `[1,2,3,4]` (9 chars) — is rejected before we even look at the structured envelope. The 20-char threshold is a magic number with no relationship to the JSON envelope semantics handled in the next 30 lines.

**Impact:** Endpoints that legitimately return small payloads (lookup-by-key, count endpoints, single-id results) cause `fetchFromApiMapping` to return null and the corresponding mapping to be marked `success=false` even though the upstream returned a valid value.

**Evidence:** `sed -n '497,500p' SmartInputFetcher.java`. The check fires *before* the structured envelope inspection at line 503-525.

**Fix sketch:** Delete the length check — the structured-envelope and substring checks below already reject empty/error responses. Or move it after the structured check and lower the threshold to 2 (just to filter `{}`/`[]`, which are already handled at line 493).

---

### Finding 8: `fetchFromSmartSource` throws `Exception("No smart sources available")` to signal a fall-through

**Severity:** Low

**Location:** `SmartInputFetcher.java:289` (throw) and `:189-192` (catch)

**Code excerpt:**
```java
// :289
throw new Exception("No smart sources available for parameter: " + paramName);
// :189-192 (caller)
} catch (Exception e) {
    log.info("Smart Fetch → {} = ERROR ({}), falling back to LLM", parameterInfo.getName(), e.getMessage());
    return fallbackToLLM(parameterInfo);
}
```

**Bug:** Throwing a generic `Exception` for an expected control-flow event ("we tried everything and got nothing") is wasteful — JVM stack-trace creation, log-spam, and indistinguishable from a real failure (network error, parsing error). The caller then logs every benign exhaustion as `ERROR (...)`.

**Impact:** Operational logs show `Smart Fetch → X = ERROR (No smart sources available)` for every parameter that simply failed to produce a value, mixed in with real errors. Performance is also non-trivial — `Exception` capture is hot in this loop.

**Evidence:** `grep -n "No smart sources available" SmartInputFetcher.java` → 289.

**Fix sketch:** Make `fetchFromSmartSource` return `null` on exhaustion; have `fetchSmartInput` test for null and call `fallbackToLLM`. Reserve thrown exceptions for genuine errors (HTTP, IO, JSON-parse).

---

### Finding 9: Dead JSONPath helpers still wired up

**Severity:** Medium

**Location:** Multiple — `SmartInputFetcher.java:885` (`extractValueFromResponse`), `:3272` (`guessExtractPath`), `:3325` (`guessPathByParameterName`), `:3389` (`isValidJsonPath`), `:3420` (`getApiResponseSchema`), `:3450` (`buildDataExtractionPrompt`), `:3506` (`askLLMForExtractionPath`), `:3670` (`callLLMForExtractionPathDiscovery`)

**Code excerpt (one example):**
```java
// :3506
private String askLLMForExtractionPath(String prompt) {
    log.warn("❌ DEPRECATED: askLLMForExtractionPath called - this should not happen!");
    log.warn("❌ The system should use direct value extraction instead of JSONPath discovery");
    log.warn("❌ Returning null to force fallback to direct extraction");
    return null;
}
```

**Bug:** A grep for callers of these methods within SmartInputFetcher.java shows: `extractValueFromResponse` declared at 885, never called. `guessExtractPath` declared at 3272, never called. `guessPathByParameterName` only called by `guessExtractPath` (line 3317) → dead transitively. Same for `isValidJsonPath`, `getApiResponseSchema`, `buildDataExtractionPrompt`, `truncateResponseSchemaForLLM` (used only by `buildDataExtractionPrompt` and `extractValueDirectlyFromResponse` — wait, the latter does call it; so `truncateResponseSchemaForLLM` is *not* dead; but its callers reach via `buildDataExtractionPrompt` which is dead). `askLLMForExtractionPath` and `callLLMForExtractionPathDiscovery` only print "DEPRECATED" and return null.

**Impact:** ~400 lines of unreachable code, plus the maintenance hazard that someone reads `extractValueFromResponse` (which calls `selectValueWithFallbackLogic`, which uses the buggy `paramName.contains("id")` rules of Finding 18) and assumes it is the production path. The JSONPath template registered at `InputFetchRegistry.llmPrompts.dataExtraction` (referenced at `:3451`) is also dead — and the registry YAML doesn't even contain a `dataExtraction` key any more, so `template` is null, which would NPE if anyone called `buildDataExtractionPrompt`.

**Evidence:** `grep -nE "extractValueFromResponse\(" SmartInputFetcher.java` → only the definition at 885; no call site. Same for `guessExtractPath`, `buildDataExtractionPrompt`, `getApiResponseSchema`. `parameterErrors`-section dump of registry YAML shows no `dataExtraction` template.

**Fix sketch:** Delete `extractValueFromResponse`, `selectValueWithFallbackLogic`, `selectValueWithLLM` (only called by the dead `extractValueFromResponse`), `guessExtractPath`, `guessPathByParameterName`, `isValidJsonPath`, `getApiResponseSchema`, `buildDataExtractionPrompt`, `askLLMForExtractionPath`, `callLLMForExtractionPathDiscovery`. Drop the `dataExtraction` key from `InputFetchRegistry.initializeDefaults` (it isn't there anyway, but the dead code references it).

---

### Finding 10: Hardcoded 2044-char LLM prompt limit, in 8 places

**Severity:** High

**Location:** `SmartInputFetcher.java:574, 999, 1022, 1593, 2080, 3281, 3725, 4007, 4154` — total 9 occurrences (plus a 2000 in `:4296` and 1944 in `:3529`).

**Code excerpt (sample):**
```java
// :574
if (prompt.length() > 2044) {
    log.warn("Direct extraction prompt too long ({} chars), using fallback", prompt.length());
    return extractValueWithSimpleFallback(responseBody, parameterInfo);
}
// :3529
int maxServicesLength = 1944 - tempPrompt.length();
```

**Bug:** 2044 is the historical message-length cap for GPT4All (the original local LLM). It is hardcoded into 8 separate prompt builders. The system also supports Gemini (1M tokens), Ollama (qwen2.5-coder:14b @ 32k tokens), and OpenAI — all with vastly larger limits. There is no `config.maxPromptLength` field in `SmartInputFetchConfig` or `LLMConfig`; the cap is just sprinkled inline.

**Impact:** When direct extraction prompt is, say, 3000 chars (typical for a real OAS schema slice), the system silently drops to `extractValueWithSimpleFallback` even though the actual model has 16-32× more capacity. We are operating at ~6% of the qwen2.5-coder context window because of a constant left over from a discontinued model. Multiple findings (`buildLLMDiscoveryPrompt` truncating service lists at 1944 chars, `truncateResponseSchemaForLLM` capping schema at 1500 chars) are downstream of this.

**Evidence:** `grep -n 2044 SmartInputFetcher.java` → 9 hits. None of them references `LLMConfig.getMaxPromptChars()` (no such method exists).

**Fix sketch:** Add `int maxPromptChars` to `LLMConfig` (default by model: 2044 for gpt4all-old, 16000 for ollama, 100000 for gemini). Replace every `> 2044` with `> llmService.getConfig().getMaxPromptChars()`. Same for the 1844, 1944, 1500 buffers (compute relative to the configurable cap).

---

### Finding 11: `SmartFetchAuthManager.performLogin` is TrainTicket-specific

**Severity:** High

**Location:** `src/main/java/es/us/isa/restest/inputs/smart/SmartFetchAuthManager.java:84-140`

**Code excerpt:**
```java
String loginUrl = baseUrl + "/api/v1/users/login";          // hardcoded path
loginPayload.put("username", adminUsername);                // hardcoded body keys
loginPayload.put("password", adminPassword);
jwtToken = response.getJSONObject("data").getString("token"); // hardcoded response shape
tokenExpiry = LocalDateTime.now().plus(TOKEN_VALIDITY_MINUTES, ChronoUnit.MINUTES); // 30 min
```

**Bug:** The login URL path, request body keys, response token JSON path, and token validity are all baked into the class. The class JavaDoc even admits it: "Authentication manager for Smart Input Fetching — Handles login and JWT token management for TrainTicket system" (line 18). There is no `auth.login.url`, `auth.login.body.user.key`, `auth.token.path`, `auth.token.ttl.minutes` configuration.

**Impact:** Smart-fetch is unusable on any SUT that does not happen to have a `POST /api/v1/users/login` endpoint accepting `{"username","password"}` and returning `{"data":{"token":...}}`. The system is misnamed "smart" because it cannot adapt to a different SUT's auth scheme without a code change. The 30-minute fixed expiry also has no relation to the actual token expiry — if the SUT issues a 10-minute token, requests fail at minute 11; if the SUT issues a 24-hour token, the manager re-logs in unnecessarily every 25 minutes.

**Evidence:** `grep -nE 'login|token|expiry' SmartFetchAuthManager.java`. The only configurable fields are `baseUrl`, `adminUsername`, `adminPassword`.

**Fix sketch:** Inject `loginPath`, `usernameField`, `passwordField`, `tokenJsonPath`, `tokenTtlMinutes` from `SmartInputFetchConfig`. Even better, decode the JWT's `exp` claim and use it as the actual expiry instead of guessing.

---

### Finding 12: `auth.user.*` credentials are loaded but never wired to the auth manager

**Severity:** Medium

**Location:** `SmartInputFetchConfig.java:118-119` (loaded); `SmartInputFetcher.java:99-103` (only admin credentials are passed to the manager).

**Code excerpt:**
```java
// SmartInputFetchConfig.java:118-119
config.authUserUsername = properties.getOrDefault("auth.user.username", "fdse_microservice");
config.authUserPassword = properties.getOrDefault("auth.user.password", "111111");
```
```java
// SmartInputFetcher.java:99-103
this.authManager = new SmartFetchAuthManager(
    baseUrl,
    config.getAuthAdminUsername(),
    config.getAuthAdminPassword()
);
```

**Bug:** The config exposes *two* credential pairs (`auth.admin.*` and `auth.user.*`) but `SmartInputFetcher` only forwards `auth.admin.*` to the auth manager. `getAuthUserUsername`/`getAuthUserPassword` are never called by any production path (`grep -rn "getAuthUserUsername\|getAuthUserPassword" src/` returns only the getters and the loader). The user-tier credentials are dead.

**Impact:** Admin requests are used for everything, including endpoints that should be exercised under user-tier permissions. This pollutes the response data — admin-tier endpoints often return additional fields that are then learned as "valid values" but cause 403 when a regular user later tries to use them.

**Evidence:** `grep -rn "getAuthUserUsername\|getAuthUserPassword" src/main/java/` → only definitions, no consumers.

**Fix sketch:** Either delete the user-tier credential fields if they are not needed, or instantiate a second `SmartFetchAuthManager` and pick admin/user based on the endpoint's required role.

---

### Finding 13: `SmartLLMParameterGenerator.createParameterInfoWithErrorContext` reloads the 51K-line YAML on every parameter

**Severity:** Critical

**Location:** `src/main/java/es/us/isa/restest/inputs/smart/SmartLLMParameterGenerator.java:204-237`, especially **line 213**.

**Code excerpt:**
```java
private ParameterInfo createParameterInfoWithErrorContext() {
    ParameterInfo pinfo = createParameterInfo();
    try {
        String registryPath = System.getProperty("smart.input.fetch.registry.path");
        if (registryPath != null && !registryPath.isEmpty()) {
            java.io.File registryFile = new java.io.File(registryPath);
            if (registryFile.exists()) {
                InputFetchRegistry registry = InputFetchRegistry.loadFromFile(registryFile);  // ← line 213
                ...
```

**Bug:** Every call to `nextValue()` falls through to `generateWithErrorContext` → `createParameterInfoWithErrorContext`, which reads the YAML file from disk and parses it via Jackson into the entire `RegistryData` graph. With a registry of 51,232 lines (`wc -l input-fetch-registry.yaml`), each call is a multi-megabyte parse. There is no caching, no `volatile InputFetchRegistry instance`, no file-mtime check.

**Impact:** Tested empirically: a 51K-line YAML parses in ~150-300 ms on a warm JVM. With 14 parameters per scenario × 80 scenarios per service × 41 services = ~46,000 fetches per run, that is ~2-4 hours of pure YAML-parse work — repeatedly producing the same object graph. This is the dominant runtime cost of the smart-fetch path. Memory pressure is also enormous (constant garbage churn, since each parse allocates the full DTO tree).

**Evidence:** `grep -n "InputFetchRegistry.loadFromFile" SmartLLMParameterGenerator.java` → 213. The `SmartInputFetcher` itself caches the registry in `this.registry` (line 61); only this one helper bypasses the cache.

**Fix sketch:** Cache the `InputFetchRegistry` at the `SmartLLMParameterGenerator` field level, with a periodic refresh keyed on file mtime, OR reuse the existing `smartFetcher.getRegistry()` accessor (add one if needed). Even a simple `static volatile InputFetchRegistry cached` keyed by path is a 100-1000× speedup.

---

### Finding 14: Registry YAML is rewritten after every discovery batch

**Severity:** High

**Location:** `SmartInputFetcher.java:316-324` and `:1330-1339`

**Code excerpt:**
```java
// :316-324
for (ApiMapping mapping : discoveries) {
    registry.addMapping(parameterInfo.getName(), mapping);
    log.info("💾 Saved mapping for '{}': {} -> {}", paramName, mapping.getService(), mapping.getEndpoint());
}
if (!discoveries.isEmpty()) {
    saveRegistry(); // Persist learned mappings
}
```
```java
// :1330-1339
private void saveRegistry() {
    try {
        File registryFile = new File(config.getRegistryPath());
        registryFile.getParentFile().mkdirs();
        registry.saveToFile(registryFile);  // serialises whole 51K-line YAML
```

**Bug:** Every successful LLM discovery (typically 3 mappings) triggers a full re-serialization of the 51K-line YAML. There is no batching, no debouncing, no lock — multiple concurrent fetches racing on `discoverApiMappings` will write the file in interleaved order. Worse: `addMapping` mutates the in-memory map, but `saveToFile` serializes the full graph including unrelated parameters' mappings, so a write started while another thread is still appending to a different list can produce a corrupt YAML mid-write.

**Impact:** I/O storm + race condition. With 40+ parameters under discovery, that is 40 sequential 51K-line writes per scenario startup. The on-disk file mtime in the test logs would show a timestamp change every few seconds throughout the run — verifiable with `ls -la --time=full input-fetch-registry.yaml` over a run.

**Evidence:** No locking around `saveToFile`. `InputFetchRegistry.saveToFile` writes through `yamlMapper.writeValue(file, data)` (`InputFetchRegistry.java:68`) directly, no temp-file-and-rename.

**Fix sketch:** (1) Write to `registry.yaml.tmp` then atomic rename. (2) Batch by debouncing — only save after N seconds of no discovery, or on shutdown. (3) Wrap the save in a `synchronized(this)` or a global `ReentrantLock`. (4) Even better: use a periodic flush thread; mutators only mark dirty.

---

### Finding 15: `registry.addMapping(parameterName, …)` keys by bare parameter name only

**Severity:** High

**Location:** `InputFetchRegistry.java:86-89` (`addMapping`); `SmartInputFetcher.java:318` (caller)

**Code excerpt:**
```java
// InputFetchRegistry.java:86
public void addMapping(String parameterName, ApiMapping mapping) {
    parameterMappings.computeIfAbsent(parameterName, k -> new ArrayList<>()).add(mapping);
    log.debug("Added mapping for parameter '{}': {}", parameterName, mapping);
}
```

**Bug:** The map key is just the parameter's bare name. Two completely different operations that both have a parameter called `id`, `name`, `userId`, or `accountId` share the same mapping list. There is no scope by service, operation path, location-in-request, or schema-type.

**Impact:** Operation `/api/v1/contactservice/contacts/{id}` (a contact GUID) and `/api/v1/userservice/users/{id}` (a user UUID) both look up `id` in the registry and find each other's mappings. The fetcher then attempts to use a contact-service producer endpoint to satisfy a user-service `id` parameter — guaranteed mismatch. The registry YAML clearly shows ~30 distinct top-level keys like `id`, `name`, `accountId`, `loginId` — but a real SUT has dozens of operations using each of those names with different semantics.

**Evidence:** `grep -E "^[a-zA-Z]+:$" input-fetch-registry.yaml | head -50` shows 30+ bare parameter-name keys, and `head -51000 input-fetch-registry.yaml | grep -E "endpoint:.*[a-z]" | wc -l = 174` mappings sharing those 30 keys. Compare to the cache key in `SmartInputFetcher.buildCacheKey` (`:3244-3258`), which correctly composes name + type + location + format + enums + bounds + lengths + regex — but the *registry* key throws all of that away.

**Fix sketch:** Compose the registry key the same way `buildCacheKey` composes the cache key. At minimum add the operation path (`<method> <path>`) so that two unrelated operations cannot collide.

---

### Finding 16: `OpenAPIEndpointDiscovery.findBestEndpoint` is dead code

**Severity:** Low

**Location:** `OpenAPIEndpointDiscovery.java:140-151`

**Bug:** `findBestEndpoint` is declared `public Optional<EndpointInfo> findBestEndpoint(...)` but `grep -rn findBestEndpoint src/main/java/` shows the only hit is the definition itself; no caller anywhere in the project. The whole `ScoredEndpoint` private inner class (`:278-289`) is also dead.

**Impact:** ~50 lines of unreachable scoring logic. Note that `inferEndpointForService` (in `SmartInputFetcher.java:3903`) re-implements scoring via LLM and never calls this helper — the two are competing implementations.

**Evidence:** `grep -rn findBestEndpoint src/main/java/` → only `OpenAPIEndpointDiscovery.java:140` (the declaration).

**Fix sketch:** Delete `findBestEndpoint`, `scoreEndpoint`, and the `ScoredEndpoint` inner class.

---

### Finding 17: `cleanBooleanValue` coerces unknown values to `false`, masking type errors

**Severity:** High

**Location:** `SmartInputFetcher.java:2771-2787` (also duplicated at `:3015-3031` as `formatAsBooleanValue`).

**Code excerpt:**
```java
private String cleanBooleanValue(String value, ParameterInfo parameterInfo) {
    if (value == null || value.trim().isEmpty()) return "false";
    String cleanValue = value.trim().toLowerCase();
    if (cleanValue.equals("true") || cleanValue.equals("1") ||
        cleanValue.equals("yes") || cleanValue.equals("on") ||
        cleanValue.equals("enabled") || cleanValue.equals("active")) {
        return "true";
    }
    // False values (default)
    return "false";
}
```

**Bug:** Two issues. (1) `yes`/`on`/`enabled`/`active` are mapped to literal `"true"` even though no real boolean parameter accepts those tokens — Spring's deserializer expects exactly `"true"`/`"false"`/`"1"`/`"0"`. The closed-domain shortcut at line 2046-2056 specifically warns the LLM not to emit those synonyms; this method then *re-introduces* them silently. (2) Anything not in the whitelist becomes `"false"`. So if the LLM emits an integer (`"42"`), a typo (`"truu"`), an explanation (`"This is a delivery flag"`), or even just `"false"` itself, the result is `"false"` — a *valid-looking* boolean. Type mismatches and hallucinations are laundered into syntactically-valid but semantically-wrong values.

**Impact:** Boolean negative-test variants are guaranteed to be the same as the positive variant ("false") because the fault-injection layer cannot generate a syntactic mismatch — `cleanBooleanValue` always returns `true` or `false`. The whole boundary-condition concept is broken for booleans.

**Evidence:** `grep -nE "yes|on|enabled|active" SmartInputFetcher.java | grep -v "//"` shows the synonyms at 2779-2781 and 3024-3026.

**Fix sketch:** Whitelist exactly `"true"`, `"false"`, `"1"`, `"0"`. Anything else returns the input unchanged so downstream type-checking can mark the test as faulty.

---

### Finding 18: `selectValueWithFallbackLogic` uses heuristic `paramName.contains("id"|"name"|"title"|"list"|"array")`

**Severity:** Medium

**Location:** `SmartInputFetcher.java:912-966` (the whole method).

**Code excerpt:**
```java
if (paramName.contains("list") || paramName.contains("array")) { … }
if (paramName.contains("id") || paramName.contains("identifier")) { … }
if (paramName.contains("name") || paramName.contains("title")) { … }
```

**Bug:** Same loose substring matching as Finding 5, applied to value-selection from a JSON list. `paramName.contains("list")` matches `customerList`, `playlistOrder`, `listenerId`, `whitelist`, `realistic`. `contains("name")` matches `firstname`, `nameless`, `legalNameField`, `username`, `pathname`, `dirname` — and also misfires when an ID-typed param happens to contain "name" because the order of the if-blocks below `contains("id")` is wrong (id tested first, but `firstname` does not contain "id" — actually no, but `nameId` would hit both). Furthermore, this method is reached only via the dead `extractValueFromResponse` (Finding 9), so it might be unreachable today, but a `grep` shows future devs could call it from `selectValueWithLLM` (line 891) if they re-enable the JSONPath path.

**Impact:** Latent. If JSONPath extraction is ever re-enabled, the fallback selects values inconsistently with parameter semantics and contradicts the boundary-aware ID detection elsewhere.

**Evidence:** `grep -nE 'paramName.contains' SmartInputFetcher.java | grep selectValueWithFallbackLogic` … or simply read 912-966.

**Fix sketch:** Delete this method entirely (it is unreachable per Finding 9). If it must be kept, replace `paramName.contains("id")` with `isIdTypedParam`, and `paramName.contains("list")|contains("array")` with `getOpenAPISchemaType(...) .equals("array")`.

---

### Finding 19: Trace-observed producer endpoints are session-scoped only

**Severity:** Medium

**Location:** `SmartInputFetcher.java:222-243`; `MultiServiceTestCaseGenerator.java:943` (`info.setTraceProducerEndpoints(...)`).

**Code excerpt:**
```java
// SmartInputFetcher.java:222-243
List<String> traceEndpoints = parameterInfo.getTraceProducerEndpoints();
if (traceEndpoints != null && !traceEndpoints.isEmpty()) {
    log.info("Trace-aware fetch: trying {} producer endpoints observed in workflow for '{}'", traceEndpoints.size(), paramName);
    for (String endpoint : traceEndpoints) {
        ApiMapping traceMapping = new ApiMapping(endpoint, "trace-observed", "DIRECT_EXTRACTION");
        ...
```

**Bug:** Trace-observed producer endpoints are passed in via `ParameterInfo.traceProducerEndpoints` (a transient field), tried first, but then *not* persisted to the registry on success. Each subsequent run starts with no knowledge of the trace-observed producers and has to re-run the trace-mining to recover them. The `flow.md` documentation (lines 813-823) admits this is the design intent ("Trace endpoints are session-scoped, intentionally not persisted because traces from different runs may have stale URLs"), but the result is that the smart-fetch system fundamentally cannot learn from successful trace-mining.

**Impact:** The most reliable signal — "this exact endpoint was observed producing this exact parameter value in a real trace" — is discarded after one run. The persistent registry instead retains the LLM-guessed (often wrong) endpoints. So the registry monotonically degrades in quality.

**Evidence:** `traceMapping` at line 228 is local to the loop, never added to `registry.addMapping`. `grep -n traceProducerEndpoints SmartInputFetcher.java` confirms only the read path.

**Fix sketch:** When a trace-observed mapping yields a successful fetch, also add it to the registry (`registry.addMapping(paramName, traceMapping)`). The "stale URL" concern can be addressed by stamping `lastUsed` and decaying via Finding 1's fixed score.

---

### Finding 20: `generateAlgorithmicMinimalValue` ignores `minimum`/`maximum`

**Severity:** Medium

**Location:** `SmartInputFetcher.java:1847-1869`

**Code excerpt:**
```java
if ("integer".equals(schemaType)) {
    return String.valueOf(Math.abs(paramName.hashCode() % 1000) + 1);
} else if ("number".equals(schemaType)) {
    double value = (Math.abs(paramName.hashCode() % 10000) + 1) / 10.0;
    return String.valueOf(value);
} else if ("boolean".equals(schemaType)) {
    return String.valueOf(paramName.hashCode() % 2 == 0);
}
```

**Bug:** Schema bounds (`minimum`, `maximum`, `exclusiveMinimum`, `multipleOf`) are completely ignored. A parameter declared `integer, minimum: 100, maximum: 200` will be assigned `abs(hashCode%1000)+1` which is in `[1, 1000]` — frequently out of range. A `number, minimum: 0.0, maximum: 1.0` (a probability) gets values in `[0.1, 1000.0]`.

**Impact:** Smart-fetch's last-resort fallback violates the schema. The value is then rejected by the validation layer (`isValidValueForParameter`'s slack of +100 length, but no bounds check), or worse, sent to the SUT and rejected with HTTP 400 — which the system then learns as a `parameterErrors` entry, polluting the error context for future LLM calls.

**Evidence:** `grep -n "generateAlgorithmicMinimalValue" SmartInputFetcher.java` → declarations and definitions at 1784, 1847; nowhere is `parameterInfo.getMinimum()` or `getMaximum()` referenced.

**Fix sketch:** Read `parameterInfo.getMinimum()`/`getMaximum()`. For integer: `min + (hash % (max - min + 1))`. For number: `min + (hash%10000)/10000.0 * (max - min)`.

---

### Finding 21: `generateFallbackSemanticValues` appends `"_<idx>"` to a "minimal" value

**Severity:** High

**Location:** `SmartInputFetcher.java:2283-2311`

**Code excerpt:**
```java
while (generatedValues.size() < count) {
    String variation = generateValueVariationWithLLM(parameterInfo, generatedValues);
    if (variation != null && ...) { generatedValues.add(variation); }
    else {
        // If LLM fails completely, generate minimal schema-compliant values
        String minimalValue = generateMinimalFallbackValue(parameterInfo);
        if (!generatedValues.contains(minimalValue) && !existingValues.contains(minimalValue)) {
            generatedValues.add(minimalValue + "_" + generatedValues.size());   // ← line 2293
        }
    }
    ...
}
// :2308-2311 (last-resort branch — same pattern)
String minimalValue = generateMinimalFallbackValue(parameterInfo);
generatedValues.add(minimalValue + "_" + i);
```

**Bug:** When the LLM fails to invent a variation, the code appends `"_" + size` to a value generated by `generateMinimalFallbackValue`, which is type-aware. So an integer parameter for which the minimal value is `42` ends up with `"42_2"`, `"42_3"` in the diverse cache — *strings* that fail integer parsing on every consumer. Worse, the array fallback at `:1864` returns `[\"foo\", \"bar\"]` and the suffix becomes `[\"foo\", \"bar\"]_2` — invalid JSON.

**Impact:** The diverse cache is systematically polluted with type-violating values. Each draw from it triggers an `isValidValueForParameter` rejection downstream, which falls back to LLM regeneration, which often re-fails — wasting LLM calls and producing test cases stamped with `LLM_EMPTY_<param>`-style sentinels.

**Evidence:** `grep -n "minimalValue +" SmartInputFetcher.java` → 2293, 2310.

**Fix sketch:** Drop the suffix. If `generateMinimalFallbackValue` cannot produce N distinct values, return what it has and let the consumer pad through other means (closedDomainCandidates, schema bounds, etc.). Never concatenate a numeric suffix to a typed value.

---

### Finding 22: `discoverByLLM` does not validate the LLM-returned `serviceName` before persisting

**Severity:** High

**Location:** `SmartInputFetcher.java:387-411` and `:3555-3604` (`askLLMForServices`)

**Code excerpt:**
```java
// :387-411
List<String> suggestedServices = askLLMForServices(prompt);
...
for (int i = 0; i < suggestedServices.size(); i++) {
    String service = suggestedServices.get(i);
    String endpoint = inferEndpointForService(service, parameterInfo);
    if (endpoint != null) {
        ApiMapping mapping = new ApiMapping(endpoint, service, "DIRECT_EXTRACTION");
        ...
```

**Bug:** `service` is whatever the LLM emits as a JSON string. There is no check that it appears in `getAllAvailableServices()` (which is the very list passed *into* the prompt). When the LLM hallucinates `Travel-Service`, `ts-magic-service`, or just `NO_GOOD_MATCH`, the string is passed straight into `inferEndpointForService` and then persisted at `:399`. This is the root cause of Findings 2 and 4.

**Impact:** Registry pollution. The `getAllAvailableServices()` set monotonically grows with phantom services because they get re-included in the next discovery prompt (line 365 `registry.getAllServices()`).

**Evidence:** `askLLMForServices` (`:3555-3604`) parses the JSON array but does no membership check. Lines 91 & 275 of registry YAML are the proof.

**Fix sketch:** After parsing the JSON array, intersect with `getAllAvailableServices()` (case-insensitive, with capitalization normalized — Finding 4). Drop services not in the set.

---

### Finding 23: `fetchFromApiMapping` ignores `mapping.getMethod()`

**Severity:** Medium

**Location:** `SmartInputFetcher.java:423-432`

**Code excerpt:**
```java
private String fetchFromApiMapping(ApiMapping mapping, ParameterInfo parameterInfo) throws Exception {
    String url = baseUrl + mapping.getEndpoint();
    // Always use GET for data fetching
    String httpMethod = "GET";
    log.info("🌐 API Call: {} {} for parameter '{}'", httpMethod, url, parameterInfo.getName());
    HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
    conn.setRequestMethod(httpMethod);
```

**Bug:** Even though `ApiMapping.method` is a persisted field (default `"GET"`, settable), the fetch always uses `GET`. If a registry mapping was created with `method: "POST"` (e.g., from an enrichment script), it is silently changed to GET at fetch time. The mapping stays out-of-sync with the wire behavior.

**Impact:** Registry YAML scan shows all ~174 mappings are `method: "GET"` today, so the bug is latent. But: (1) The test_endpoint discovery layer at `:4198-4228` actively filters to GET endpoints only; if a future change permits POST endpoints into the candidate pool, every fetch will silently become GET. (2) The persisted `method` field is dead; that is a subtle data-modeling smell that suggests stale code intent.

**Evidence:** `grep -nE 'method: "(POST|PUT|DELETE)"' input-fetch-registry.yaml` returns 0 hits — but `grep -n "Always use GET" SmartInputFetcher.java` → 426.

**Fix sketch:** Either delete the `method` field on `ApiMapping` (since it is forced to GET), or read `mapping.getMethod()` and reject mappings with non-GET methods at registration time.

---

### Finding 24: Cache eviction is missing — `CacheConfig.maxEntries` is never enforced

**Severity:** Medium

**Location:** `SmartInputFetcher.java:62-93`; `CacheConfig.java:9-14` (configured); never read.

**Code excerpt:**
```java
// SmartInputFetcher.java:62-63
private Map<String, CachedValue> cache;
private Map<String, List<String>> diverseValueCache;
// :93-95
this.cache = new ConcurrentHashMap<>();
this.diverseValueCache = new ConcurrentHashMap<>();
```

**Bug:** Both caches are unbounded `ConcurrentHashMap`s. `CacheConfig.maxEntries` (default 1000) is exposed via `setMaxEntries(...)` and persisted in the YAML, but no code in the project reads it back to enforce the bound. `grep -rn "maxEntries\|getMaxEntries" src/main/java/` returns only the getter/setter.

**Impact:** Long runs leak memory. Each unique `buildCacheKey(...)` (a 6-tuple of string fields) creates a new entry, and the diverse cache holds Lists of values. Across thousands of parameters and runs, the heap grows monotonically. A 24-hour soak test would OOM.

**Evidence:** `grep -rn "maxEntries\|getMaxEntries" src/main/java/` → only definition sites, no consumer.

**Fix sketch:** Wrap the caches with a bounded cache (e.g., Caffeine's `Caffeine.newBuilder().maximumSize(config.getMaxEntries()).expireAfterWrite(config.getTtlSeconds(), SECONDS).build()`). Or at minimum, in `cacheValue`, evict the oldest entry when `cache.size() > config.getMaxEntries()`.

---

### Finding 25: `handleSingleValueParameterFromLLM` is dead

**Severity:** Low

**Location:** `SmartInputFetcher.java:1260-1280`

**Bug:** Declared `private`, never called. `grep -n handleSingleValueParameterFromLLM SmartInputFetcher.java` shows only the definition.

**Impact:** Maintenance hazard — the method calls `cleanLLMGeneratedValue` and `isValidValueForParameter` (both still alive), so removing it is safe. ~20 lines of unreachable code.

**Evidence:** `grep -n handleSingleValueParameterFromLLM SmartInputFetcher.java` → 1260 only.

**Fix sketch:** Delete the method.

---

### Finding 26: `SmartFetchAuthManager.invalidateToken` is never auto-invoked on 401/403

**Severity:** High

**Location:** `SmartFetchAuthManager.java:167-171` (manual API); never called from `fetchFromApiMapping` (`SmartInputFetcher.java:445-450`).

**Code excerpt:**
```java
// SmartInputFetcher.java:446-450
int responseCode = conn.getResponseCode();
if (responseCode != config.getSuccessResponseCode()) {
    log.warn("❌ API call failed with HTTP {}: {}", responseCode, url);
    throw new Exception("HTTP " + responseCode + " from " + url);
}
```

**Bug:** When the SUT returns 401 (token expired, invalidated) or 403 (token rejected), the fetcher just throws and moves on. The token is not refreshed. Subsequent fetches reuse the same expired/invalid token because `isTokenValid()` (`SmartFetchAuthManager.java:69-79`) only checks the *internal* expiry timestamp, not the SUT's verdict. Combined with Finding 11 (the 30-min internal expiry is a guess), this means a single mid-run JWT revocation kills smart-fetch for the rest of the run.

**Impact:** Cascade failure on token expiry. The internal expiry is conservative (30 min), but if the SUT rotates the JWT signing key at minute 5, every fetch from minute 5 to minute 30 fails with 403, until the manager finally re-logs in.

**Evidence:** `grep -n invalidateToken src/main/java/` → only the definition at `SmartFetchAuthManager.java:167`.

**Fix sketch:** In `fetchFromApiMapping`, when `responseCode == 401 || responseCode == 403`, call `authManager.invalidateToken()` and retry once.

---

### Finding 27: EMA learning rate α=0.1 prevents fast recovery — many registry entries stuck at 0.0

**Severity:** Medium

**Location:** `ApiMapping.java:59-64`

**Code excerpt:**
```java
public void updateSuccessRate(boolean success) {
    double alpha = 0.1; // Learning rate
    this.successRate = alpha * (success ? 1.0 : 0.0) + (1 - alpha) * this.successRate;
    this.lastUsed = LocalDateTime.now();
}
```

**Bug:** Symmetric EMA with α=0.1. Starting from `successRate=0.0`, after 10 successes the rate is `0.65` (not 1.0). After one failure followed by 10 successes the rate is even lower. The decay is also slow on the way down. Combined with Finding 1, this means a mapping that starts with a small misfire is heavily penalized but has no path back.

**Impact:** Inspecting the registry: `grep "successRate: 0.0" input-fetch-registry.yaml | wc -l = 130`. Out of 174 mappings, 75% have successRate=0.0 (never succeeded once or recovered). The registry is largely composed of dead entries that will not reach the priority threshold thanks to Finding 1's broken score function.

**Evidence:** `awk '/^    successRate:/' input-fetch-registry.yaml | sort | uniq -c`:
```
130     successRate: 0.0
  9     successRate: 0.19
  8     successRate: 0.1
  6     successRate: 0.34
  6     successRate: 0.27
  3     successRate: 0.15
  ...
```

**Fix sketch:** Use a Bayesian beta-binomial estimate (`successRate = (successes + 1) / (attempts + 2)`), or asymmetric α (e.g., 0.3 for successes, 0.05 for failures). And include "attempts" in the score so a 0.0 rate after only 2 attempts ranks above a 0.0 rate after 50 attempts.

---

### Finding 28: `discoveryTimeoutMs` is reused as both connect AND read timeout

**Severity:** Medium

**Location:** `SmartInputFetcher.java:442-443`

**Code excerpt:**
```java
conn.setConnectTimeout((int) config.getDiscoveryTimeoutMs());
conn.setReadTimeout((int) config.getDiscoveryTimeoutMs());
```

**Bug:** A single value (`smart.input.fetch.discovery.timeout.ms`, default 5000) is used for both connect and read. The two phases have different latency profiles — connection should be < 1 s on a healthy SUT, but read can legitimately take much longer for large list endpoints. The default 5 s is too tight for read but too loose for connect, and there is no separate configuration knob.

**Impact:** Either real responses get truncated mid-read (if the SUT is slow under load), or hung connections idle for 5 s before failing. Neither is good. The same applies to `getApiResponseSchema` (`:3427-3428`).

**Evidence:** `grep -n setConnectTimeout SmartInputFetcher.java` → 442; `setReadTimeout` → 443.

**Fix sketch:** Add `smart.input.fetch.connect.timeout.ms` (default 1500) and `smart.input.fetch.read.timeout.ms` (default 8000) as separate keys.

---

### Finding 29: `selectValueWithFallbackLogic` skips list element 0 for "list" parameters

**Severity:** Medium

**Location:** `SmartInputFetcher.java:923-929`

**Code excerpt:**
```java
if (paramName.contains("list") || paramName.contains("array")) {
    if (list.size() > 1) {
        Object value = list.get(1); // Second element often more representative
        return value != null ? value.toString() : null;
    }
}
```

**Bug:** The comment "Second element often more representative" is a folk superstition; arrays of length 1 fall through to the next branch (id/name), but arrays of length 2+ always discard element 0. Worse, when the array represents a paginated result with the most recent record at index 0, this rule deliberately avoids the freshest data.

**Impact:** Stations like `[Beijing, Shanghai, Guangzhou]` always return `Shanghai`, never `Beijing`. The diverse-value cache is biased away from index 0 forever. (Note: this method is dead per Finding 9 today, but if the JSONPath path is reactivated, the bias re-appears.)

**Evidence:** `sed -n '923,929p' SmartInputFetcher.java`. The skip-zero is also implicit in `extractAdditionalDiverseValues` (`:1549-1582`), which uses `extractedValues.add(firstValue)` — but `firstValue` is the LLM-extracted "first" value, not necessarily index 0.

**Fix sketch:** Pick a random element with the seeded `Random` (`this.random.nextInt(list.size())`) or simply `list.get(0)`. Document the choice.

---

### Finding 30: `valueRotationIndex` and `diverseValueCache` are accessed without joint locking

**Severity:** Medium

**Location:** `SmartInputFetcher.java:1480-1498` (`getNextDiverseValue`); `:1411-1422` (`cacheDiverseValue` uses `compute`+synchronized list).

**Code excerpt:**
```java
private String getNextDiverseValue(ParameterInfo parameterInfo) {
    String cacheKey = buildCacheKey(parameterInfo);
    List<String> values = diverseValueCache.get(cacheKey);
    if (values == null || values.isEmpty()) return null;
    int currentIndex = valueRotationIndex.getOrDefault(cacheKey, 0);
    String value = values.get(currentIndex % values.size());            // ← no sync on values
    valueRotationIndex.put(cacheKey, (currentIndex + 1) % values.size());// ← read-modify-write
    ...
```

**Bug:** Two race windows: (1) Between the `get(...)` and `values.get(...)`, another thread could call `clearInvalidCachedValues` (`:1428-1463`) and shrink/null the list; `values.get(currentIndex % values.size())` then throws IOOB or `values` is stale. (2) `valueRotationIndex.put(...)` is not atomic relative to `getOrDefault(...)`; two concurrent rotations can both increment from index N to N+1, skipping N+1 to N+2 on the next caller.

**Impact:** Sporadic `IndexOutOfBoundsException` and rotation drift under concurrent fetches (the test generator is single-threaded today, but the class is shared by `SmartLLMParameterGenerator` instances and exposed via `setSmartFetcher`).

**Evidence:** `getNextDiverseValue` does not use the `diverseValueCache.compute(...)` pattern that `cacheDiverseValue` uses (`:1411-1422`).

**Fix sketch:** Wrap the rotation in a single `compute` step on `valueRotationIndex` that reads the current list under a `synchronized(values)` block.

---

### Finding 31: `extractValueFromResponse` uses `JsonPath.read` without filter — implicit list semantics

**Severity:** Low

**Location:** `SmartInputFetcher.java:885-907`

**Bug:** `JsonPath.read(responseBody, extractPath)` returns a `List` for any path with a wildcard (`[*]`), an `Object` for a definite path, and a primitive for a leaf. `selectValueWithFallbackLogic` checks `instanceof List`, but it does *not* handle the `null`-vs-`PathNotFoundException` distinction reliably (the catch at line 899-901 catches `PathNotFoundException`, but other `IllegalArgumentException`-class errors fall through to the generic catch at line 902 with only a debug log). Also, `extractPath` is read from registry; nothing guards against a malformed `$..` expression.

**Impact:** Latent — the method is unreachable per Finding 9. But if reactivated, malformed registry data causes silent null returns that look like "not found" instead of "broken path".

**Evidence:** `sed -n '885,907p' SmartInputFetcher.java`.

**Fix sketch:** Distinguish `PathNotFoundException` (legitimate miss) from `InvalidPathException` (registry corruption); log the latter at WARN with the registry-row coordinates.

---

### Finding 32: `discoverByPatterns` is intentionally dead — pattern discovery silently disabled

**Severity:** Medium

**Location:** `SmartInputFetcher.java:339-356`

**Code excerpt:**
```java
private List<ApiMapping> discoverByPatterns(ParameterInfo parameterInfo) {
    List<ApiMapping> mappings = new ArrayList<>();
    log.info("🔍 Pattern discovery for '{}' checking {} patterns", paramName, registry.getServicePatterns().size());
    log.warn("❌ DEPRECATED: Pattern discovery creates JSONPath mappings - we want direct extraction only");
    log.warn("❌ Skipping pattern discovery to avoid JSONPath expressions like '$.data[*].route.endStation'");
    ...
    return mappings; // Return empty list to force LLM-based discovery
}
```

**Bug:** The function logs three lines per call but always returns an empty list, so the LLM is the only discovery path. `InputFetchRegistry.servicePatterns` is still populated (`InputFetchRegistry.java:200-218`) and serialized to YAML; line 343 even calls `getServicePatterns().size()` for the log. This is dead state in the registry that survives YAML saves and confuses anyone reading the file.

**Impact:** (1) The `servicePatterns` list in the YAML is misleading — readers assume it is consulted, but it is not. (2) Three log lines per parameter discovery (× 14 params × 80 scenarios = ~3,400 log lines per run) are pure noise. (3) The fall-back path described in `flow.md` (lines 791-793) "If pattern discovery yields no mappings, the system invokes the LLM" is gone — it is always LLM, even for parameters where the pattern-match would be trivially correct.

**Evidence:** `sed -n '339,356p' SmartInputFetcher.java`. Compare with `InputFetchRegistry.java:200-218` which still defines five patterns.

**Fix sketch:** Either re-enable pattern discovery (with `extractPath = "DIRECT_EXTRACTION"` instead of JSONPath) — patterns like `.*[Ss]tation.*` → `ts-station-service` are deterministic and faster than an LLM call. Or delete `servicePatterns` from `InputFetchRegistry` to remove dead state.

---

### Finding 33: `inferEndpointForService` synthesizes `/api/v1/<svc>/query` when discovery fails

**Severity:** High

**Location:** `SmartInputFetcher.java:3938-3942`

**Code excerpt:**
```java
// Final fallback: create a reasonable endpoint based on service name
String fallbackEndpoint = "/api/v1/" + service.toLowerCase().replace("ts-", "").replace("-service", "") + "/query";
log.info("🔧 Using generated endpoint '{}' for service '{}' and parameter '{}'",
        fallbackEndpoint, service, parameterInfo.getName());
return fallbackEndpoint;
```

**Bug:** When the OpenAPI spec doesn't contain endpoints for a service, OR when the LLM's selection fails after retries, the code fabricates a path of the form `/api/v1/<lowercased-trimmed-svc>/query`. There is no evidence such a path exists. The registry YAML clearly shows the result: `endpoint: "/api/v1/travel/query"` (24 instances), `endpoint: "/api/v1/order/query"` (11), `endpoint: "/api/v1/route/query"` (7), `endpoint: "/api/v1/admin-user/query"` etc. — many of these `*/query` paths are not real TrainTicket endpoints. This is also the path that produced `/api/v1/no_good_match/query` in Finding 2.

**Impact:** Fabricated endpoints are persisted, fetched (returning 404), failed, and then re-fetched on subsequent runs because `successRate=0.0` on a single mapping does not delete it (Finding 27). The registry monotonically grows in dead mappings.

**Evidence:** `awk -F'"' '/^  - endpoint:/ {print $2}' input-fetch-registry.yaml | sort | uniq -c | sort -rn`:
```
24  /api/v1/travel/query
19  /api/v1/travelservice/trips
15  /api/v1/orderservice/order
12  /api/v1/routeservice/routes
11  /api/v1/order/query
 7  /api/v1/route/query
 6  /api/v1/user/query
 ...
```
The `*/query` paths total ~60 entries, none of which appear in the OpenAPI spec.

**Fix sketch:** Return null when no valid endpoint can be inferred. The caller (`discoverByLLM`) already handles `endpoint == null` by skipping the mapping (`:397`). Do not invent paths.

---

### Finding 34: `formatAsArrayValue` happily wraps NO_GOOD_MATCH/error strings into a single-element array

**Severity:** Medium

**Location:** `SmartInputFetcher.java:3143-3201`, especially the fallback at `:3199`.

**Code excerpt:**
```java
} catch (Exception e) {
    log.debug("Failed to format '{}' as array for parameter '{}': {}",
             value, parameterInfo.getName(), e.getMessage());
    return "[\"" + value + "\"]"; // Fallback: single-element array
}
```

**Bug:** When `value` is a sentinel (`NO_GOOD_MATCH`), an LLM explanation, or any unexpected string, the fallback wraps it in quotes and brackets and returns `["NO_GOOD_MATCH"]` — a syntactically valid JSON array, semantically a guaranteed-bad value. There is no `isValidValueForParameter` gate before this wrap.

**Impact:** Array parameters' diverse cache contains sentinel strings as one-element arrays. The first time we draw `["NO_GOOD_MATCH"]` and feed it to the SUT, the SUT returns 400 with "Cannot deserialize value of type `int` from String \"NO_GOOD_MATCH\"" — exactly the error captured at `parameterErrors` line 32643 of the registry.

**Evidence:** `parameterErrors` section in registry shows multiple "JSON parse error: Cannot deserialize value of type `int` from String \"NO_GOOD_MATCH\"" entries (lines 32643, 47271).

**Fix sketch:** Validate `value` against the array's `items.type`/`items.format` schema before wrapping. Reject NO_GOOD_MATCH explicitly.

---

### Finding 35: LLM-generated values are added to `diverseValueCache` even when smart-fetch returned null

**Severity:** Medium

**Location:** `SmartInputFetcher.java:1186-1201` (in `fallbackToLLM`)

**Code excerpt:**
```java
String processed = cleanLLMGeneratedValue(cleaned, parameterInfo);
if (processed != null && isValidValueForParameter(processed, parameterInfo)) {
    validCleanedValues.add(processed);
    cacheDiverseValue(parameterInfo, processed);   // ← cached for *future* smart-fetch calls
}
```

**Bug:** `fallbackToLLM` is the path taken when smart-fetch produced nothing. Yet the LLM's output is stored in `diverseValueCache` (line 1190). Next time `fetchSmartInput` runs, `getNextDiverseValue` (`:174`) returns the cached LLM value as if it were a smart-fetched value, triggering the `🔄 Using diverse cached value` log line (`:176`). The "Smart fetched X" success metric is therefore inflated by ghost successes that are actually pure-LLM outputs.

**Impact:** Telemetry is wrong (smart-fetch hit rate is overstated). More importantly, an LLM hallucination from one call propagates to every subsequent call until the cache TTL expires (which it does not — Finding 24).

**Evidence:** `grep -n "cacheDiverseValue" SmartInputFetcher.java | head` shows it called from `fallbackToLLM` at 1190.

**Fix sketch:** Only cache values that came from a real upstream fetch. Move `cacheDiverseValue` calls out of the LLM-fallback path, or tag the cached values with a `source` field and exclude LLM-sourced entries from the smart-fetch return path.

---

### Finding 36: `cleanIntegerValue`/`cleanNumberValue` ignore `minimum`/`maximum` when synthesizing fallback "1"

**Severity:** Medium

**Location:** `SmartInputFetcher.java:2708-2766`

**Code excerpt:**
```java
private String cleanIntegerValue(String value, ParameterInfo parameterInfo) {
    if (value == null || value.trim().isEmpty()) return "1";
    ...
    log.debug("Failed to clean integer value '{}' for parameter '{}'", value, parameterInfo.getName());
    return "1";
}

private String cleanNumberValue(String value, ParameterInfo parameterInfo) {
    if (value == null || value.trim().isEmpty()) return "1.0";
    ...
    return "1.0";
}
```

**Bug:** When parsing fails, the methods return literal `"1"` / `"1.0"`. Schema constraints are ignored — a `minimum: 100` parameter gets `"1"`, an enum-constrained `[A,B,C]` gets `"1"`, a `format: int64`-with-`minimum: 1000000000000` gets `"1"`. The "safe fallback" comment is a misnomer; the fallback is *unsafe* with respect to schema.

**Impact:** Smart-fetch silently produces schema-invalid values that the SUT rejects with 400. The error is then learned in `parameterErrors` and re-fed into the LLM context, polluting future runs.

**Evidence:** `sed -n '2708,2766p' SmartInputFetcher.java`. No reference to `parameterInfo.getMinimum()`/`getMaximum()`.

**Fix sketch:** Read `parameterInfo.getMinimum()` and return that (or `1` if unset). Same for number with `getMinimum()`. For enums, return the first enum value.

---

### Finding 37: `isValidApiResponse` empty-`data` rejection misses other envelope shapes

**Severity:** Low

**Location:** `SmartInputFetcher.java:512-520`

**Code excerpt:**
```java
Object data = obj.opt("data");
if (data instanceof org.json.JSONArray && ((org.json.JSONArray) data).isEmpty()) {
    log.debug("API response has empty data array");
    return false;
}
if (data instanceof org.json.JSONObject && ((org.json.JSONObject) data).isEmpty()) {
    log.debug("API response has empty data object");
    return false;
}
```

**Bug:** Only the field literally named `"data"` is checked. Common alternatives `"result"`, `"payload"`, `"items"`, `"records"`, `"content"`, `"results"` are not. A response like `{"status":1, "result":[]}` (Spring's alt envelope) passes `isValidApiResponse` with the empty result, then `extractValueDirectlyFromResponse` returns nothing, and the mapping is marked `success=false` — but for the wrong reason.

**Impact:** Mappings against "alternate envelope" services accumulate false negatives. The success-rate field becomes meaningless for those services.

**Evidence:** `sed -n '503,524p' SmartInputFetcher.java`.

**Fix sketch:** Iterate over a known list of envelope keys (`data`, `result`, `payload`, `items`, `records`, `content`, `results`); for each, check empty-array/object.

---

### Finding 38: `selectEndpointWithLLMRetry` retries up to 3× even on transient failures

**Severity:** Medium

**Location:** `SmartInputFetcher.java:3965-3997`

**Code excerpt:**
```java
private String selectEndpointWithLLMRetry(...) {
    int maxRetries = 3;
    for (int attempt = 1; attempt <= maxRetries; attempt++) {
        ...
        String result = selectEndpointWithLLM(endpoints, parameterInfo, serviceName);
        if (result != null && !result.equals("NO_GOOD_MATCH")) return result;
        if (result != null && result.equals("NO_GOOD_MATCH")) {
            String forcedResult = forceEndpointSelectionWithLLM(...); // ← extra LLM call
            if (forcedResult != null) return forcedResult;
        }
    }
    return null;
}
```

**Bug:** Each retry is a full LLM call. An LLM that returns `NO_GOOD_MATCH` once will likely return it again with the same prompt. Worse, on each `NO_GOOD_MATCH`, the code calls `forceEndpointSelectionWithLLM` (another LLM call). Total max LLM calls per parameter discovery: 3 normal + 3 forced = 6 LLM calls. Multiply by 14 params × 80 scenarios = up to 6,720 LLM calls per run for endpoint selection alone.

**Impact:** Discovery cost. Smart-fetch's whole-run LLM bill is dominated by retries on hopeless prompts.

**Evidence:** `sed -n '3965,3997p' SmartInputFetcher.java`. The retries are unconditional even when the LLM made a deterministic choice.

**Fix sketch:** If the LLM returns the same `NO_GOOD_MATCH` twice with the same prompt, give up (no third retry). Cache the prompt → response mapping for the duration of the run.

---

### Finding 39: `extractValueWithSimpleFallback`/`isRelevantField` fire one LLM call per JSON field

**Severity:** High

**Location:** `SmartInputFetcher.java:2397-2473`

**Code excerpt:**
```java
private boolean isRelevantField(String fieldName, String paramName, String paramType) {
    return askLLMForFieldRelevance(fieldName, paramName, paramType);   // ← LLM call
}
```

**Bug:** `extractValuesFromJsonNode` (`:2357`) recursively walks the JSON tree and for **every field name** invokes `isRelevantField`, which always asks the LLM. A response with 30 fields nested 4 deep yields ~120 LLM calls just to decide which fields are relevant — for one parameter. The local cache `field-name → relevance` is not implemented; each call is from scratch.

**Impact:** A single `extractValueWithSimpleFallback` run on a moderately complex response (50 fields) makes ~50 LLM calls. The "fallback" is therefore far more expensive than the main path it falls back from. With Finding 13's per-call YAML reload, each LLM call is also a 51K-line YAML parse.

**Evidence:** `grep -n "askLLMForFieldRelevance" SmartInputFetcher.java` → 2399 (only invocation site, called from `isRelevantField`).

**Fix sketch:** (1) Cache `(fieldName, paramName) → boolean` for the duration of a fetch. (2) Use a cheap heuristic gate first (substring or stem match); only escalate to LLM for ambiguous cases. (3) Or batch all field decisions into a single LLM call: "Which of these N fields are relevant for parameter P?".

---

### Finding 40: `parameterErrors` map keyed by the encoded URL — `%25`-style payloads fragment the error registry

**Severity:** Medium

**Location:** `InputFetchRegistry.java:30, 109-112`; YAML evidence at lines 2747, 32643.

**Code excerpt (registry YAML):**
```yaml
# input-fetch-registry.yaml:2747
  /api/v1/contactservice/contacts/account/%2527%253B%2520DROP%2520TABLE--:
    accountId:
    - errorType: "FORMAT_ERROR"
      errorReason: "The request was rejected because the URL contained a potentially\
        \ malicious String \"%25\""
```

**Bug:** `parameterErrors` is keyed by the API endpoint *as-recorded* — and the recorded form is the *encoded* URL with embedded test payloads (e.g., `%2527%253B%2520DROP%2520TABLE--` for a SQL-injection fault variant). Each unique fault payload becomes a separate map key. The error registry fragments rather than aggregates.

**Impact:** The "known error context" feature (`getErrorContextForParameter` at `InputFetchRegistry.java:153-172`) fails to match a future test against a past error because the URL paths differ by the encoded-payload suffix. The semantic deduplication at `InputFetchRegistry.findSemanticallyEquivalentError` is per-key, so the dedup never sees the cross-payload similarity.

**Evidence:** `grep -E "%[0-9A-F]{2}" input-fetch-registry.yaml | wc -l` returns hits across hundreds of fragmented endpoint keys. Real endpoints (e.g., `/api/v1/contactservice/contacts/account/{id}`) end up with as many error sub-trees as there are fault payloads.

**Fix sketch:** Normalize the endpoint key to the OpenAPI path template (`/api/v1/contactservice/contacts/account/{id}`) before lookup. The encoded URL can be stored in the `additionalInfo` field of `ParameterError` for forensics.
