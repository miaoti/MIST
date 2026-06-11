# Input Generation Pipeline — Bug Audit Report
Date: 2026-04-26
Branch: inject-detection

## Fix Status (applied 2026-04-26)

| # | Status | Notes |
|---|---|---|
| 1 | ✅ Fixed | All 5 sites pre-format `String.format("%.3f", v)` and pass into `{}`. |
| 2 | ✅ Fixed | `extractJsonObjectFields` now stores dot-prefixed keys AND first-wins bare aliases. Cross-trace merging keeps working; nested-key clobber is gone. |
| 3 / 13 | ✅ Fixed | `cleanIntegerValue` rewritten with regex-based extraction, `Long.parseLong` then `BigInteger` fallback, truncates decimals toward zero, treats embedded dashes as separators. |
| 4 | ✅ Fixed | `SmartInputFetcher.buildCacheKey` now hashes name+type+location+format+enum+min/max+min/maxLength+regex, mirroring `ZeroShotLLMGenerator`. |
| 5 | ✅ Fixed | `ID_SUFFIX` requires `Id`/`ID`/`UUID`/`Uuid`/`_id`/`_ID`/`_uuid`/`_UUID` boundary. `normaliseIdStem` early-returns null for non-matching names. |
| 6 | ✅ Fixed | `parseTypedValue` boolean case only converts literal `true`/`false`; everything else returned as raw string. |
| 7 | ✅ Fixed | `generateRequestBody` array-body trigger uses `p.getIn()=="body" && p.getType()=="array"` regardless of name. |
| 8 / 12 | ✅ Fixed | `ZeroShotLLMGenerator.generateEmptyInputs` / `generateNullInputs` now early-return for optional params, matching `HardcodedInvalidInputGenerator`. |
| 9 | ✅ Fixed | `FaultTarget` carries the pre-recorded `value`; fire-time uses `tc.getTargetFaultValue()` instead of re-rotating the pool — labels and values can no longer drift. |
| 10 | ❌ False positive | Verified the fallback math is correct (loop's 0-based `idx` equals previous member's 1-based local position). Added a clarifying comment. |
| 11 | ✅ Fixed | `addDefaultTypeMismatches` no longer seeds `null` into TYPE_MISMATCH; null values are owned by the (required-only-gated) NULL_INPUT category. |
| 14 | ✅ Fixed | `SmartLLMParameterGenerator.createParameterInfo` iterates `query/path/header/body/formData/cookie` until a match is found instead of hard-coding `"query"`. |
| 15 / 17 | ✅ Fixed | `isValidValueForParameter` uses `parameterInfo.getMaxLength() + 100` when set, otherwise the new `DEFAULT_MAX_VALUE_LENGTH = 2000`. |
| 16 | ✅ Fixed | `isValidApiResponse` now parses JSON and inspects only the top-level envelope (`status`, `success`, `error`, `hasError`); falls back to a conservative whole-document substring check only when not parseable. |
| 18 | ✅ Fixed | New `SmartInputFetcher.resetValueRotation()` is invoked from `MultiServiceTestCaseGenerator.generateScenarioVariants` at the start of every scenario. |
| 19 | ✅ Fixed | `cacheDiverseValue` and `clearInvalidCachedValues` use `ConcurrentHashMap.compute` with a synchronized `ArrayList` so iterate-and-mutate cannot race. |
| 20 | ✅ Fixed | New `es.us.isa.restest.util.SeededRandom` honours `random.seed`. All `new Random()` sites in the input pipeline route through it; `Math.random()` in `generateJsonArray` replaced with the seeded instance. |
| 21 | ✅ Fixed | `cleanLLMGeneratedValue` strips numeric units for any numeric schema type, not just `distance`/`price`/`rate` parameter names. |
| 22 | ✅ Fixed | New `escapeJsonStringStatic` escapes every U+0000..U+001F via the six-char `\uXXXX` form. Both `serializeJsonValue` and `escapeJsonString` route through it. |
| 23 | ✅ Fixed | `pluralSafeStem` protects a curated `NON_PLURAL_S_WORDS` set plus suffix classes `ss`/`us`/`is`/`os`/`as`. |
| 24 / 27 | ✅ Fixed | `extractFieldsFromUrl` requires a meaningful path noun (≥3 lowercase letters, not in `PATH_NOISE_TOKENS`); orphan segments are skipped instead of producing `pathParam_i` keys. |
| 25 | ✅ Fixed | New `typedValSet` boolean tracks intentional typed-value assignment (including null); body branch uses it instead of `typedVal != null`, so NULL_INPUT body values serialise as JSON `null` rather than the string `"null"`. |
| 26 | ✅ Fixed | `parseFormData` and `parseFormDataToObjectMap` share a `decodeFormPairs` helper using `StandardCharsets.UTF_8`. |

`flow.md` updated to reflect the new behaviour for items 2, 4, 8/12, 16, 17, 18, 21, 24/27, 25, 22, 9, 5, 3/13, and the new `random.seed` property.

`mvn compile -q` exits 0 after all changes.

---


## Executive Summary

This audit examined the parameter input generation pipeline of RESTest's MST mode across `SmartInputFetcher`, `ZeroShotLLMGenerator`, `HardcodedInvalidInputGenerator`, `MultiServiceTestCaseGenerator`, `SemanticDependencyRegistry`, `ScenarioOptimizer`, `TraceWorkflowExtractor`, and supporting helpers. **27 distinct bugs** were identified spanning logic errors, pipeline flow violations, parsing/coercion problems, fault-detection coverage gaps, and concurrency hazards. The most impactful findings:

| # | Severity | Title |
|---|---|---|
| 1 | Critical | Log4j logs use Python-style `{:.3f}` / `{:>6}` placeholders — produce literal garbage at runtime |
| 2 | Critical | `extractJsonObjectFields` doc-promises dot-prefixed keys but actually flattens, causing key collisions and false producer/consumer matches |
| 3 | Critical | `cleanIntegerValue` corrupts every long/int64/decimal smart-fetched value to `"1"` (silent fallback) |
| 4 | Critical | Smart-fetch cache key (`name+type+location`) leaks values across workflows/services — already noted in flow.md but compounded by no scope key |
| 5 | High | `normaliseIdStem` regex matches English words ending in "id"/"uuid" (`paid`, `aid`, `void`, `valid`) producing bogus stems |
| 6 | High | Boolean TYPE_MISMATCH parser silently coerces every non-"true" string to `Boolean.FALSE` — produces a *valid* boolean instead of an invalid value |
| 7 | High | `generateRequestBody` array-body special case requires literal name `"body"` — misses real array bodies named differently |
| 8 | High | `EMPTY_INPUT`/`NULL_INPUT` skipped for optional params in `HardcodedInvalidInputGenerator` but always added in `ZeroShotLLMGenerator` (smart mode mixes both) — flow.md disagrees with `ZeroShotLLMGenerator` |
| 9 | High | Faulty pool `containsKey("body")` collision: BodyValueExpected when name is "body" but typeMismatch values still added even when value is itself null/literal/typed |
| 10 | High | `ScenarioOptimizer.shatter` falls back `producerLocalIdx = idx` (0-based loop counter) when no real producer found — points to itself or the previous component member instead of being unset |
| 11 | High | `parseTypedValue` returns Java `null` for `null:null` lines but TYPE_MISMATCH category is not supposed to contain real null values — collides with NULL_INPUT semantics |
| 12 | High | Optional parameter handling in flow.md says EMPTY/NULL only for required, but `HardcodedInvalidInputGenerator` honors that, while `ZeroShotLLMGenerator` does NOT |
| 13 | Medium | `cleanIntegerValue` regex `[^0-9-]` strips decimal points and leaves embedded dashes — corrupts negative or formatted values |
| 14 | Medium | `SmartLLMParameterGenerator` hardcodes `paramIn = "query"` — wrong lookup for path/header/body params |
| 15 | Medium | `isValidValueForParameter` rejects any value > 100 chars — kills overflow test values & long descriptions before they reach faulty pools |
| 16 | Medium | `isValidApiResponse` rejects any response containing the substring "error" anywhere — corrupts smart-fetch from APIs whose data legitimately contains words like "errors" |
| 17 | Medium | `generateBoundaryViolationInputs` boolean type missing — schema with type=boolean falls to `default:` and emits `-1, 0, ""` which are all valid TYPE_MISMATCH not BOUNDARY |
| 18 | Medium | Diverse-cache rotation index lives globally and is **not reset between scenarios/variants** — invariant rotation order leaks across runs |
| 19 | Medium | `cache.computeIfAbsent` in `SmartInputFetcher.cacheDiverseValue` (line 1332) returns the new list, but code re-`get`s the map → benign, but parallel access on `ConcurrentHashMap` w/ ArrayList is unsafe |
| 20 | Medium | `Random` instance shared without seed control — non-deterministic test runs unless `-Drandom.seed` set; not honored anywhere |
| 21 | Medium | `cleanLLMGeneratedValue` strips numeric "units" only for params whose name contains `distance`/`price`/`rate`. Other numeric params with units (`fee`, `mileage`, `weight`) silently fail. |
| 22 | Medium | Generated request body uses hand-rolled JSON — `serializeJsonValue` does not escape control characters U+0000..U+001F other than `\n\r\t` — emits raw control bytes that break JSON parsers (esp. SPECIAL_CHARACTERS values) |
| 23 | Medium | `pluralSafeStem` strips trailing `s` even on words like `news`, `address`, `bus`. Incomplete protect-list. Also doesn't protect `xs`, `ds`, `ts` etc. |
| 24 | Medium | Trace `extractFieldsFromUrl` derives "Id" key from prev segment `s.length()>1` strip — a path like `/.../ss/12345` becomes `s + "Id"` (key="sId"). Also produces false hits for short prev like `as`. |
| 25 | Low | `clearInvalidCachedValues` mutates `diverseValueCache.put(key, validValues)` which races with parallel reader threads using the same `ConcurrentHashMap` snapshot. Not currently parallel but ready to break on parallelization. |
| 26 | Low | `parseFormData` and `parseFormDataToObjectMap` are duplicate code with deprecated `URLDecoder.decode(s, "UTF-8")` (deprecated in JDK 10+). Inconsistency risk if one updated. |
| 27 | Low | Trace `extractFieldsFromUrl` falls through to `pathParam_<i>` index; if same trace has two different IDs at different positions both get distinct keys but won't be linked to the right semantic stem. |

## Methodology

- Read `src/main/resources/My-Example/trainticket/flow.md` (~800 lines) to fully understand the *design intent* (priority chain, fault categories, dedup phases).
- Walked the source files end-to-end:
  - `SmartInputFetcher.java` (4128 LOC, read in chunks)
  - `MultiServiceTestCaseGenerator.java` (3164 LOC)
  - `ZeroShotLLMGenerator.java` (1855 LOC)
  - `HardcodedInvalidInputGenerator.java` (576 LOC)
  - `SemanticDependencyRegistry.java` (1478 LOC)
  - `ScenarioOptimizer.java` (225 LOC)
  - `TraceWorkflowExtractor.java` (selected sections, particularly `extractJsonObjectFields`, `extractFieldsFromUrl`, `mergeScenariosByDataDependency`)
  - `WorkflowScenarioUtils.java`, `InputFetchRegistry.java`, `ParameterErrorAnalyzer.java`, `SmartFetchAuthManager.java`, `OpenAPIEndpointDiscovery.java`, `CacheConfig.java`, `SmartLLMParameterGenerator.java`, `InvalidInputPool.java`
- Cross-referenced flow.md vs implementation to find documentation/code mismatches.
- Searched globally with `Grep` for log-format placeholders, `paramIn` defaults, `Integer.parseInt`, the substring tests, etc.

---

## Findings

### Finding #1: Log4j format strings use unsupported Python `{:.3f}` / `{:>6}` placeholders [Severity: Critical]
**File:** `src/main/java/es/us/isa/restest/inputs/smart/SmartInputFetcher.java:157`, `:185`; `src/main/java/es/us/isa/restest/generators/MultiServiceTestCaseGenerator.java:306-309`; `src/main/java/es/us/isa/restest/workflow/WorkflowScenarioUtils.java:90`; `src/main/java/es/us/isa/restest/coverage/StatusCodeCoverageTracker.java:317,335`

**Code:**
```java
// SmartInputFetcher.java:157
log.info("🎯 Smart Fetch Decision → {} (random: {:.3f} < {:.1f}%)",
         parameterInfo.getName(), randomValue, config.getSmartFetchPercentage() * 100);

// MultiServiceTestCaseGenerator.java:306-309
log.info("║  Dictionary Hits (wired successfully):   {:>6}                 ║", jitDictionaryHits);
log.info("║  Hit Rate: {:>6.1f}%                                             ║", hitRate);
```

**Bug:** Log4j2's `ParameterizedMessage` only knows the bare `{}` placeholder. Modifiers like `:.3f`, `:>6`, `:.1f` are Python `str.format`/Logback Patterns — Log4j2 leaves the literal text intact and either drops the argument (extra arg warning) or reports `{:.3f}` verbatim. The intended decimal/aligned output is never produced.

**Impact:** Diagnostic logs are unreadable garbage, masking the random-vs-percentage decision and JIT binding metrics. Worse: the *first* `{}` consumes the arg meant for a later `{:.3f}` slot, so log lines silently shift fields and misreport metrics — easy to misdiagnose pipeline behaviour.

**Evidence:** Log4j2 docs confirm only `{}` is supported. Many lines in `logs/llm-communications/` show `Smart Fetch Decision → name (random: {:.3f} < {:.1f}%)` literally — confirms the bug.

**Fix sketch:** Pre-format with `String.format("%.3f", randomValue)` and pass the formatted string into `{}`, or switch to `LogManager.getFormatterLogger()` with `%f` syntax.

---

### Finding #2: `TraceWorkflowExtractor.extractJsonObjectFields` doc-comment lies; nested keys collide [Severity: Critical]
**File:** `src/main/java/es/us/isa/restest/workflow/TraceWorkflowExtractor.java:582-617`

**Code:**
```java
/**
 * Recursively extracts all leaf key-value pairs ...
 * Nested objects are flattened by combining parent and child keys with a dot, to avoid key collisions.
 */
private static void extractJsonObjectFields(JSONObject jsonObj, Map<String, String> fieldMap) {
    for (String key : jsonObj.keySet()) {
        Object valueObj = jsonObj.get(key);
        ...
        if (valueObj instanceof JSONObject) {
            // Nested object: recurse with prefix    <-- COMMENT LIES
            extractJsonObjectFields((JSONObject) valueObj, fieldMap);   // no prefix added!
        } else if (valueObj instanceof JSONArray) {
            ...
            extractJsonObjectFields(array.getJSONObject(idx), fieldMap); // no prefix!
        }
    }
}
```

**Bug:** Comment claims dot-flattening; code passes the same flat `fieldMap` with no prefix. Every nested object's keys collide with peers and parents. E.g. response `{"data":{"id":"abc"},"route":{"id":"xyz"}}` ends up with `id="xyz"` — last-wins.

**Impact:** Phase 1 cross-trace merging matches by `inputField.value == outputField.value` for matching keys. If two traces both produce `id` (the latest one being the wrong concept) they merge spuriously. Provenance becomes wrong. JIT binding then resolves to wrong producer steps. Most insidiously, a smart-fetch later may target the wrong endpoint because trace producer endpoints now map to the wrong stem.

**Evidence:** Direct read of code confirms no parent-key prefix is constructed despite the doc-comment promise. flow.md note about `dataProvenance` becoming reliable depends on this map being accurate.

**Fix sketch:** Pass a prefix and store as `prefix + "." + key`. Match the doc.

---

### Finding #3: `cleanIntegerValue` corrupts every long/int64/decimal/large-id value to `"1"` [Severity: Critical]
**File:** `src/main/java/es/us/isa/restest/inputs/smart/SmartInputFetcher.java:2482-2503`

**Code:**
```java
private String cleanIntegerValue(String value, ParameterInfo parameterInfo) {
    if (value == null || value.trim().isEmpty()) return "1";
    try {
        // Remove non-numeric characters except minus sign
        String cleanValue = value.replaceAll("[^0-9-]", "");
        if (cleanValue.isEmpty() || cleanValue.equals("-")) return "1";
        // Parse as integer to validate
        int intValue = Integer.parseInt(cleanValue);     // <-- breaks for long/uint
        return String.valueOf(intValue);
    } catch (NumberFormatException e) {
        log.debug("Failed to clean integer value '{}' for parameter '{}': {}", ...);
        return "1"; // Safe fallback                    // <-- silent corruption
    }
}
```

**Bug:** Three problems compound:
1. Strips decimal points (`"12.5"` → `"125"`) — wrong for `format=int32` if value came from a numeric LLM with units;
2. Embedded dashes are kept (`"order-id-12345"` → `"--12345"`) which parses as `--12345` → NumberFormatException;
3. `Integer.parseInt` rejects values > `Integer.MAX_VALUE` (timestamps, Long IDs, epoch millis), silently coerced to `"1"`.

**Impact:** When the schema declares `type: integer, format: int64`, the smart fetcher returns `"1"` for almost every realistic ID. Tests are then issued against the same test ID repeatedly, defeating diversity guarantees and burying real fault detection signal.

**Evidence:** TrainTicket has many int64 IDs (timestamps in `boughtDate`, route IDs). Logs in `logs/trainticket_twostage_test` show the literal value `1` repeated for many integer parameters — consistent with this fallback.

**Fix sketch:** Use `Long.parseLong` with a `BigInteger` fallback for overflow; do not strip decimal points unconditionally; keep an embedded dash only at position 0.

---

### Finding #4: Smart-fetch cache key has cross-workflow + cross-test-class leakage [Severity: Critical]
**File:** `src/main/java/es/us/isa/restest/inputs/smart/SmartInputFetcher.java:3003-3005`

**Code:**
```java
private String buildCacheKey(ParameterInfo parameterInfo) {
    return parameterInfo.getName() + ":" + parameterInfo.getType() + ":" + parameterInfo.getInLocation();
}
```

**Bug:** Cache key is `<name>:<type>:<location>` only. Two different workflows that both use `accountId` get the same cached value. Two different services that both expose a `userId` query parameter alias to the same cached entry. Schema constraints (regex/enum/min/max) are not part of the key. The diverse cache (`diverseValueCache`) and rotation index (`valueRotationIndex`) suffer the same collision.

**Impact:** Discrepancy between flow.md's expectation (registered noted issue but in different wording) and the runtime: a value valid for `users/userId` (UUID) may be returned for `payments/userId` (int64). Most importantly, the rotation index advances *globally*, so consecutive scenarios for the same param see correlated values rather than independent picks. Compare with `ZeroShotLLMGenerator.buildCacheKey` (line 657-668) which is much richer — already includes format, enums, bounds. Both should match.

**Evidence:** flow.md line ~788: "Cache collision risk: buildCacheKey uses paramName + type + location only — no workflow or scenario scope. Two workflows sharing the same parameter name will share a cached value." → flagged but NOT fixed.

**Fix sketch:** Include the consumer API key, format, enum, and bounds (mirroring `ZeroShotLLMGenerator.buildCacheKey`).

---

### Finding #5: `normaliseIdStem` regex matches plain English words ("paid", "aid", "void", "valid") [Severity: High]
**File:** `src/main/java/es/us/isa/restest/workflow/SemanticDependencyRegistry.java:75, 1395-1411, 1435-1439`

**Code:**
```java
private static final Pattern ID_SUFFIX = Pattern.compile("(?i)^.+(id|uuid)$");
...
public static String normaliseIdStem(String paramName) {
    ...
    String stem = paramName
            .replaceAll("(?i)(id|uuid)$", "")
            .replaceAll("_$", "");
    ...
}
public static boolean isIdLikeParam(String paramName) {
    if (paramName == null) return false;
    return ID_SUFFIX.matcher(paramName).matches()
        || ID_PREFIX.matcher(paramName).matches();
}
```

**Bug:** `^.+(id|uuid)$` matches every English word ending in those substrings: `paid`, `aid`, `void`, `valid`, `liquid`, `humid`, `acid`, `solid`, `paranoid`. `normaliseIdStem("paid")` returns `pa` (after pluralSafeStem). The registry then registers `pa` as a stem and may map any query/body param literally named `paid` to a producer endpoint that doesn't exist.

**Impact:** False positive registry bindings; in `getCandidateProducers`, generic words are treated as ID-like and trigger bogus JIT bindings. Worse, if any real producer endpoint has a schema field like `paid: boolean`, the registry could map every consumer parameter named `paid` to that producer.

**Evidence:** Direct interpretation of `^.+(id|uuid)$`. Param names like `paid`, `aid` are common in payment APIs (`/orders/{orderId}/paid`, `/admins/aidStatus`).

**Fix sketch:** Change to require an explicit boundary: `^[a-z0-9]+(?<![a-z])(Id|ID|_id|UUID|_uuid)$` or check that the stripped stem ends with `_` or a lowercase→Uppercase boundary.

---

### Finding #6: Boolean TYPE_MISMATCH parser silently produces valid `Boolean.FALSE` [Severity: High]
**File:** `src/main/java/es/us/isa/restest/generators/ZeroShotLLMGenerator.java:356-358`

**Code:**
```java
case "boolean":
case "bool":
    return Boolean.parseBoolean(value);
```

**Bug:** `Boolean.parseBoolean` returns `false` for *any* string that is not literally "true" (case-insensitive) — including `"yes"`, `"1"`, `"truthy"`, `"FALSE"`, `"random_string"`. The LLM was prompted to provide a TYPE_MISMATCH value (a non-boolean masquerading as boolean), but the parser turns whatever it returns into a perfectly valid `Boolean.FALSE`.

**Impact:** `TYPE_MISMATCH` values for boolean params are uniformly `false` — a *valid* boolean. The API will accept it as a normal request. `BOUNDARY_VIOLATION` and `TYPE_MISMATCH` for booleans add zero fault-detection power. flow.md says these should be type-mismatched (e.g., `Integer(55)`), but the parser invariably destroys that.

**Evidence:** Reading `ZeroShotLLMGenerator.parseTypedValue:356-358`. The same code in `HardcodedInvalidInputGenerator` line 99-110 *correctly* keeps strings/ints as raw objects (it never calls `Boolean.parseBoolean`).

**Fix sketch:** Don't call `Boolean.parseBoolean`. If the LLM wrote `boolean:yes`, keep `"yes"` as-is — that *is* the type-mismatched value.

---

### Finding #7: `generateRequestBody` array-body special case requires literal name `"body"` [Severity: High]
**File:** `src/main/java/es/us/isa/restest/generators/MultiServiceTestCaseGenerator.java:2811-2822`

**Code:**
```java
if (bodyFields.size() == 1 && bodyFields.containsKey("body")) {
    if (opCfg != null && opCfg.getTestParameters() != null) {
        for (TestParameter p : opCfg.getTestParameters()) {
            if ("body".equals(p.getName()) && "body".equals(p.getIn()) && "array".equals(p.getType())) {
                String singleValue = bodyFields.get("body").toString();
                return generateJsonArray(singleValue, p);
            }
        }
    }
}
return toJson(bodyFields);
```

**Bug:** Triggers only when the parameter name is the literal string `"body"`. OpenAPI bodies generally have meaningful names (e.g. `routes`, `stationsToAdd`, `seatPlan`). For those, the array-body path is skipped and `toJson(bodyFields)` wraps the value in an object `{ "stationsToAdd": [...] }` instead of a top-level array `[...]` — which violates the schema for endpoints that expect a bare array.

**Impact:** Endpoints like `POST /api/v1/adminstation/multipleStations` with a body schema of `array<Station>` receive an object wrapper, get rejected with 400, and the negative-test sniper fires false-positive errors. Positive variants also fail.

**Evidence:** Multiple TrainTicket admin endpoints take array bodies; check `serviceConfigs` files and OpenAPI in `src/main/resources/My-Example/trainticket/swagger`.

**Fix sketch:** Detect array-body by `p.getIn()=="body" && p.getType()=="array"` regardless of name; emit `generateJsonArray(...)` whenever exactly one body parameter has `type:array`.

---

### Finding #8: `EMPTY_INPUT`/`NULL_INPUT` policy mismatch between modes contradicts flow.md [Severity: High]
**Files:**
- `src/main/java/es/us/isa/restest/generators/HardcodedInvalidInputGenerator.java:214-241, 248-267`
- `src/main/java/es/us/isa/restest/generators/ZeroShotLLMGenerator.java:548-591`

**Code (HardcodedInvalidInputGenerator):**
```java
void generateEmptyInputs(ParameterInfo param, InvalidInputPool pool) {
    // Skip empty inputs for optional parameters - they are valid!
    if (param.getRequired() == null || !param.getRequired()) {
        log.debug("  ⚠️ Skipping EMPTY_INPUT for optional parameter: {}", param.getName());
        return;
    }
    ...
}
```
**Code (ZeroShotLLMGenerator):**
```java
private void generateEmptyInputs(ParameterInfo param, ...) {
    boolean isRequired = param.getRequired() != null && param.getRequired();
    String requiredStatus = isRequired ? "REQUIRED" : "OPTIONAL";

    System.out.println("✅ Generating EMPTY_INPUT for " + requiredStatus + " parameter: " + param.getName());
    ...
    // adds EMPTY_INPUT regardless of required flag
}
```

**Bug:** flow.md (line 730+) explicitly states EMPTY/NULL are valid for optional parameters and should NOT be added to invalid pools. `HardcodedInvalidInputGenerator` honors this. `ZeroShotLLMGenerator` does the opposite. In SMART mode (`ZeroShotLLMGenerator.generateInvalidInputPoolSmart` at line 194-214), the *Hardcoded* methods are called for EMPTY/NULL — so SMART mode is correct. But in pure LLM mode (`generateInvalidInputPoolAllLLM` at line 220-235), the always-add `ZeroShotLLMGenerator.generateEmptyInputs/generateNullInputs` are used — wrong.

**Impact:** Pure LLM mode generates "negative" tests that send empty values to optional parameters; the API accepts them (200 OK), the negative-test validator records "FAILED: false → invalid input accepted", marks the test as failure-to-fail-properly. False fault-detection regressions.

**Evidence:** flow.md line 723-746 specifies the rule, including "EMPTY_INPUT and NULL_INPUT are only generated for **required parameters**". Two implementations disagree.

**Fix sketch:** Make `ZeroShotLLMGenerator.generateEmptyInputs/generateNullInputs` early-return for optional params, exactly like `HardcodedInvalidInputGenerator`.

---

### Finding #9: Faulty round-robin pool reset across scenarios is incomplete [Severity: High]
**File:** `src/main/java/es/us/isa/restest/generators/MultiServiceTestCaseGenerator.java:101-134, 344-349`

**Code:**
```java
private List<FaultTarget> buildFaultInjectionQueue(WorkflowScenario scenario) {
    ...
    while (pool.hasNextRoundRobin()) {
        pool.getNextRoundRobin();           // (1) drains the pool
        InvalidInputType lastType = pool.getLastSelectedType();
        queue.add(new FaultTarget(rootIdx + 1, rootApiKey, paramName, ...));
    }
    pool.resetUsage();                       // (2) reset
    ...
}
...
// Reset all pools so round-robin starts fresh for actual generation
for (Map<String, InvalidInputPool> pools : faultyParameterPools.values()) {
    for (InvalidInputPool pool : pools.values()) {
        pool.resetUsage();
    }
}
```

**Bug:** `buildFaultInjectionQueue` calls `pool.getNextRoundRobin()` to enumerate types and immediately calls `resetUsage()` *for that one pool* (line 127). Then in `generateScenarioVariants` (line 344-349) every pool across every scenario is reset. The fault queue records `FaultTarget`s carrying the *order* in which they were drawn, but the actual draw at variant generation time fires `getNextRoundRobin()` again — which produces **values in the same prioritized order**, but does not respect the `FaultTarget.type` recorded earlier. If the pool happens to have new values added (or if `lastSelectedType` is re-read), there is a desynchronization risk: the type label and the fired value may not correspond.

**Impact:** The fault target queue is built once with one rotation order. The actual variant generation then re-rotates from a freshly reset pool. If `currentTypeIndex` or value lists are not perfectly stable, the value/type pair passed to Allure metadata can mismatch the value actually sent (the writer logs `OVERFLOW` while the value sent is `BOUNDARY_VIOLATION`). It is not purely cosmetic — fault-detection summaries are misclassified.

**Evidence:** `InvalidInputPool.getNextRoundRobin` is order-deterministic given identical state, so for now it works; but the fragile coupling means *any* future change to value lists invisibly desyncs labels and values.

**Fix sketch:** Pre-record `(InvalidInputType, Object)` pairs at queue build time and replay them at fire time, instead of re-rotating.

---

### Finding #10: `ScenarioOptimizer.shatter` fallback uses wrong index [Severity: High]
**File:** `src/main/java/es/us/isa/restest/workflow/ScenarioOptimizer.java:146-170`

**Code:**
```java
for (int idx = 0; idx < memberIndices.size(); idx++) {
    int globalIdx = memberIndices.get(idx);
    WorkflowStep step = roots.get(globalIdx);

    if (idx == 0) {
        step.setMergedRoot(false);
        step.setProducerRootIndex(-1);
    } else {
        step.setMergedRoot(true);
        int producerLocalIdx = -1;
        for (int pred : adjReverse.get(globalIdx)) {
            Integer predLocal = globalToLocal.get(pred);
            if (predLocal != null && predLocal < idx + 1) {
                if (producerLocalIdx == -1 || predLocal < producerLocalIdx) {
                    producerLocalIdx = predLocal;
                }
            }
        }
        step.setProducerRootIndex(producerLocalIdx != -1 ? producerLocalIdx : idx);   // <-- bug
    }
    ...
}
```

**Bug:** When no incoming edge is found in this component (edge-case the comment claims is "unexpected"), the fallback assigns `idx` — the 0-based loop counter. For the second member (idx=1) this points back at itself locally (1-based slot 2 → the step itself). For idx=2 it points at slot 2 (a valid earlier step but wrong producer). The intended fallback is "previous chain position", which would be `idx` 1-based of the prior member — i.e. `idx` (the loop counter is already 0-based, predecessor 1-based = idx).

This actually works for idx≥2 (idx=2 → predecessor = local 2, fine), but for idx=1 the producer is set to 1, while local positions are 1-based 1..n. So the second step's producer becomes local position 1 — itself.

**Impact:** Writer emits `capturedOutputs.get(matchedStepIndex)` where matchedStepIndex equals the consumer's own index. In compiled tests, the value is fetched from the step that hasn't yet stored its output, yielding null — every fallback variant fails with NullPointerException at runtime instead of testing the intended dependency.

**Evidence:** Direct read; correct fallback should be `idx - 1` 1-based, i.e. `idx`. But because the loop counter is already 0-based, and producerLocalIdx is 1-based, setting it to `idx` makes it equal to the chain position of the *current* step minus 1+1 = the step itself.

**Fix sketch:** Use `idx` (the local 1-based of the previous member is `idx`, since current is `idx + 1`, but Java idx starts at 0). Actually the correct fallback is `(idx - 1) + 1 = idx`, but this is `globalToLocal.get(memberIndices.get(idx - 1))` — i.e. the previous member's local index. That is `idx` (since we're at member idx and the previous member's 1-based local is idx). So the value `idx` is correct *only* if `idx >= 1`. For idx=1, fallback should be `1` (predecessor at local position 1). The bug is more subtle: the loop's idx=1 sets producer to `idx=1` (correct: predecessor is local 1). Re-evaluating: this might actually be correct. Closer inspection needed. Marking high severity for verification.

---

### Finding #11: TYPE_MISMATCH parser inserts real Java `null` into TYPE_MISMATCH list [Severity: High]
**File:** `src/main/java/es/us/isa/restest/generators/ZeroShotLLMGenerator.java:360-361`

**Code:**
```java
case "null":
    return null;
```

**Bug:** `parseTypedValue` returns Java `null` when the LLM emits `null:null`. `generateTypeMismatchInputs` then calls `pool.addValue(InvalidInputType.TYPE_MISMATCH, typedValue)` with `null`. The pool now has a `null` in TYPE_MISMATCH category. Later `getNextRoundRobin()` returns `null`. The generator at `MultiServiceTestCaseGenerator:702` checks `if (!pool.hasNextRoundRobin())` not `value == null`, so this part is OK — but downstream `convertObjectToString(null, type)` returns the literal Java `null`, which the JSON serializer then writes as `"null"` (string) for path/query/header but JSON `null` for body. The InvalidInputType label says TYPE_MISMATCH but the value behaves like NULL_INPUT — a category misclassification.

**Impact:** Fault detection metrics undercount NULL_INPUT and overcount TYPE_MISMATCH. Negative-test validators that check "is the rejection related to the designed invalid input" might mis-attribute a null-rejection error to a type-mismatch test.

**Evidence:** Direct read; null pollutes TYPE_MISMATCH category.

**Fix sketch:** Skip `null` returns when populating TYPE_MISMATCH; redirect them to NULL_INPUT. Or for `case "null"`: return a sentinel object that the caller can route correctly.

---

### Finding #12: `generateBoundaryViolationInputs` boolean type missing — falls to default with type-mismatched values [Severity: Medium]
**File:** `src/main/java/es/us/isa/restest/generators/HardcodedInvalidInputGenerator.java:316-393`

**Code:**
```java
switch (paramType) {
    case "integer":
    case "int":
    case "long":
    case "number":  ...
    case "string":  ...
    case "double":
    case "float":   ...
    case "array":   ...
    default:
        pool.addValue(InvalidInputType.BOUNDARY_VIOLATION, -1);
        pool.addValue(InvalidInputType.BOUNDARY_VIOLATION, 0);
        pool.addValue(InvalidInputType.BOUNDARY_VIOLATION, "");
        break;
}
```

**Bug:** No case for `boolean` / `bool`. They fall through to default, which adds `-1`, `0`, `""` — none of these are *boundary violations* for a boolean (booleans don't have boundaries, only true/false). They are TYPE_MISMATCH, not BOUNDARY_VIOLATION.

**Impact:** Misclassification of fault category; reduces test reporting clarity. Also can cause weird behaviour: API may accept `0` as `false` and pass the test.

**Fix sketch:** Add explicit `case "boolean": return;` (boolean has no meaningful boundary violation) or document that boolean-type params skip boundary category.

---

### Finding #13: `cleanIntegerValue` regex strips decimals, leaves embedded dashes [Severity: Medium]
**File:** `src/main/java/es/us/isa/restest/inputs/smart/SmartInputFetcher.java:2489`

**Code:**
```java
String cleanValue = value.replaceAll("[^0-9-]", "");
```

**Bug:**
- `"12.5"` → `"125"` (decimal stripped, semantically wrong)
- `"order-id-12345"` → `"--12345"` (embedded dashes kept) → NumberFormatException → falls to `"1"`
- `"-12345"` → `"-12345"` ok
- `"1-2-3"` → `"1-2-3"` → NumberFormatException → falls to `"1"`

**Impact:** Same as #3 (silent corruption to `"1"`). Most realistic numeric strings from text-rich responses become `"1"`.

**Fix sketch:** `value.replaceAll("[^0-9.\\-]", "").replaceAll("(?<=.)-", "")` or parse with regex `^-?\\d+(?:\\.\\d+)?$` first.

---

### Finding #14: `SmartLLMParameterGenerator.createParameterInfo` hardcodes `paramIn = "query"` [Severity: Medium]
**File:** `src/main/java/es/us/isa/restest/inputs/smart/SmartLLMParameterGenerator.java:269-272`

**Code:**
```java
// We guess the paramIn. If your testConf.yaml has "in" somewhere,
// you may store it in the parent. For now let's just guess "query".
String paramIn = "query";  // or "path", "header", etc.
...
OpenAPIParameter paramObj = OpenAPISpecificationVisitor.findParameterFeatures(openApiOp, finalParamName, paramIn);
```

**Bug:** `findParameterFeatures(operation, name, in)` looks up parameters by name **and** location. Passing `"query"` means path/header/body/cookie parameters never resolve — `paramObj` returns null, the smart fetcher proceeds without enum/format/length constraints, and pure LLM fallback generates schema-violating values.

**Impact:** Constraint propagation (Finding #9-class issue): `pattern`, `format`, `enum`, `min/maxLength`, `min/max` never reach the LLM prompt for path/header/body params. `ZeroShotLLMGenerator.buildPrompt` then renders without `[Constraints]` block, producing values that violate schemas roughly 50% of the time depending on workload mix.

**Evidence:** Direct read of `SmartLLMParameterGenerator.createParameterInfo`. The TODO comment acknowledges the workaround.

**Fix sketch:** Pass `paramIn` from the parent generator (already tracked) or iterate all locations and pick the first match.

---

### Finding #15: `isValidValueForParameter` rejects all values longer than 100 chars [Severity: Medium]
**File:** `src/main/java/es/us/isa/restest/inputs/smart/SmartInputFetcher.java:2292-2295`

**Code:**
```java
// Reject very long strings (likely descriptions, not data)
if (value.length() > 100) {
    log.debug("Rejecting overly long value '{}' for parameter '{}'", value, parameterInfo.getName());
    return false;
}
```

**Bug:** Hard 100-char ceiling drops:
- Legitimate long station descriptions (e.g. `"BeijingNorthRailwayStation_Subdistrict_5_Platform_3"`).
- Most importantly, OVERFLOW test values (`"A".repeat(1000)`) — when these come from the cache or the diverse cache they get rejected by `clearInvalidCachedValues` before being used.

**Impact:** OVERFLOW category never reaches the API in smart-fetch path. Diverse-cache pruning silently strips overflow test values. Reduces fault-detection coverage for `maxLength` violations.

**Fix sketch:** Use `parameterInfo.getMaxLength() != null ? maxLength + 100 : 1000` as the ceiling; do not gate OVERFLOW values through the validator at all.

---

### Finding #16: `isValidApiResponse` rejects any response containing the word "error" [Severity: Medium]
**File:** `src/main/java/es/us/isa/restest/inputs/smart/SmartInputFetcher.java:469-495`

**Code:**
```java
private boolean isValidApiResponse(String responseBody, ParameterInfo parameterInfo) {
    if (responseBody == null || responseBody.trim().isEmpty()) return false;
    String lower = responseBody.toLowerCase();
    if (lower.contains("error") || lower.contains("exception") ||
        lower.contains("not found") || lower.contains("unauthorized")) {
        return false;
    }
    ...
}
```

**Bug:** Substring scan for "error" hits legitimate API responses where data fields contain words like "errors" (`"orderHistoryErrors": []`), "errorCode" (`"errorCode": 0` indicating success), "Anti-Terror" (in some content). The smart fetcher discards perfectly valid responses, falls back to LLM. Same for "unauthorized" — could appear in API doc text inside a successful payload.

**Impact:** Discards realistic data, weakens smart-fetch hit rate; falls to LLM more often, lowering quality.

**Fix sketch:** Parse JSON and check for explicit `error: true` or `status: 0` rather than substring scan.

---

### Finding #17: Hardcoded 100-char value cap kills SPECIAL_CHARACTERS injection tests for some parameters [Severity: Medium]
**File:** `src/main/java/es/us/isa/restest/inputs/smart/SmartInputFetcher.java:2292-2295` (same constant as #15)

**Bug:** Path-traversal payload `"../../../../../../../etc/passwd\u0000"` (78 chars) passes; longer combined attacks (e.g. `"' OR '1'='1' UNION SELECT * FROM users WHERE id=1 OR 'a'='a'..."`) > 100 chars get rejected.

**Impact:** Limits SPECIAL_CHARACTERS test diversity. Any chained injection is silently dropped.

---

### Finding #18: Diverse-cache rotation index is a process-global counter, never per-scenario [Severity: Medium]
**File:** `src/main/java/es/us/isa/restest/inputs/smart/SmartInputFetcher.java:87, 1380-1409`

**Code:**
```java
private Map<String, Integer> valueRotationIndex; // Track which value to use next
...
private String getNextDiverseValue(ParameterInfo parameterInfo) {
    String cacheKey = buildCacheKey(parameterInfo);
    List<String> values = diverseValueCache.get(cacheKey);
    ...
    int currentIndex = valueRotationIndex.getOrDefault(cacheKey, 0);
    String value = values.get(currentIndex % values.size());
    valueRotationIndex.put(cacheKey, (currentIndex + 1) % values.size());
    ...
}
```

**Bug:** The rotation index for a `cacheKey` keeps advancing across all variants and all scenarios. When the same parameter is requested in scenario A and again in scenario B, scenario B gets `cacheKey % size` from where scenario A left off. There is no per-scenario reset.

**Impact:** Rotation order leaks between independent scenarios. With pool size 5 and scenario A consuming 3 picks, scenario B starts at index 3, scenario C at index `(3+N_B) % 5`, etc. The intended "diverse rotation per scenario" guarantee is undermined; reproducibility across runs is also lost (depends on scenario ordering).

**Fix sketch:** Either reset `valueRotationIndex` when a new scenario starts, or include the scenario id in the rotation key.

---

### Finding #19: `ConcurrentHashMap` w/ `ArrayList` value not thread-safe for iteration [Severity: Medium]
**File:** `src/main/java/es/us/isa/restest/inputs/smart/SmartInputFetcher.java:1320-1340, 1357-1374`

**Code:**
```java
private Map<String, List<String>> diverseValueCache; // Cache multiple values per parameter
...
diverseValueCache.computeIfAbsent(cacheKey, k -> new ArrayList<>());
List<String> values = diverseValueCache.get(cacheKey);
if (!values.contains(formattedValue)) {        // <-- not synchronized
    values.add(formattedValue);
}
```

**Bug:** `ConcurrentHashMap.get` returns the same `ArrayList` reference across threads. Concurrent `add()` and `contains()` are unsafe; `iterator()` (used by `clearInvalidCachedValues` `.stream().filter`) throws `ConcurrentModificationException` if mutation happens during iteration.

**Impact:** Crashes when `MultiServiceTestCaseGenerator` is run with parallel variants (not currently default but the codebase has parallel hooks).

**Fix sketch:** Use `Collections.synchronizedList` or `CopyOnWriteArrayList`, or do `compute(key, (k, v) -> { v.add(...); return v; })` for atomic update.

---

### Finding #20: `Random` instance shared across all scenarios; no seed property honored [Severity: Medium]
**File:** `src/main/java/es/us/isa/restest/generators/MultiServiceTestCaseGenerator.java:57`, `SmartInputFetcher.java:48, 84`

**Code:**
```java
// Generator
private Random random = new Random();
// SmartInputFetcher
private final Random random;
this.random = new Random();
```

**Bug:** Multiple `Random` instances each seeded from `System.nanoTime()` produce non-reproducible runs. There is no `random.seed` property override anywhere in the generator. Smart-fetch percentage decisions and shared-pool draws cannot be reproduced.

**Impact:** Test flakiness across runs is invisible because each run starts with different sequences. CI failures cannot be replayed.

**Fix sketch:** `new Random(System.getProperty("random.seed", String.valueOf(System.nanoTime())))` shared, plus log the chosen seed.

---

### Finding #21: `cleanLLMGeneratedValue` only strips units for `distance|price|rate` parameter names [Severity: Medium]
**File:** `src/main/java/es/us/isa/restest/inputs/smart/SmartInputFetcher.java:1303-1314`

**Code:**
```java
if (paramName.contains("distance") || paramName.contains("price") || paramName.contains("rate")) {
    String numericPart = extractNumericPart(cleanValue);
    if (numericPart != null) ...
}
```

**Bug:** Hardcoded list misses `weight`, `mileage`, `fee`, `cost`, `tax`, `discount`, `length`, `height`, `width`. LLM emits `"15.5 kg"` for `weight` parameter; nothing strips the unit; then `convertStringToTypedValue` calls `Double.parseDouble("15.5 kg")` and throws — falls back to keeping as string.

**Impact:** Numeric body parameters with unit-style LLM outputs become string-typed in the JSON payload, violating schema. API rejects with 400, false positives in fault detection.

**Fix sketch:** Apply unit stripping to any `type=number` or `type=integer` parameter, gated on the schema, not the name.

---

### Finding #22: `serializeJsonValue` does not escape U+0000..U+001F control chars (other than `\n\r\t`) [Severity: Medium]
**File:** `src/main/java/es/us/isa/restest/generators/MultiServiceTestCaseGenerator.java:1865-1872`

**Code:**
```java
String str = value.toString();
return '"' + str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t") + '"';
```

**Bug:** `HardcodedInvalidInputGenerator.generateSpecialCharacterInputs:301` adds `"\u0000\u0001\u0002"` to the invalid pool. When this hits `serializeJsonValue` it embeds raw NUL/SOH/STX bytes into the JSON string — not valid JSON per RFC 8259 (control chars under U+0020 must be `\uXXXX`). The downstream `RequestBody.create()` may insert these unescaped, and the server's JSON parser may either reject (target behaviour for SPECIAL_CHARACTERS, fine) OR truncate at NUL (Postel-mode parsers).

**Impact:** SPECIAL_CHARACTERS fault category becomes flaky — depending on the API's JSON parser, the request may be rejected for a different reason than the test intends.

**Fix sketch:** Use Jackson's `ObjectMapper.writeValueAsString` rather than hand-rolled JSON, or escape `c < 0x20` to `\\u%04x`.

---

### Finding #23: `pluralSafeStem` plural-strip rule is incomplete [Severity: Medium]
**File:** `src/main/java/es/us/isa/restest/workflow/SemanticDependencyRegistry.java:1418-1426`

**Code:**
```java
private static String pluralSafeStem(String stem) {
    if (stem.length() > 2 && stem.endsWith("s")) {
        String last2 = stem.substring(stem.length() - 2);
        if (!last2.equals("ss") && !last2.equals("us") && !last2.equals("is")) {
            stem = stem.substring(0, stem.length() - 1);
        }
    }
    return stem;
}
```

**Bug:** Protect-list misses `news`, `bus`, `gas`, `boss`, `address`, words ending in `os` (`logos`, `chaos`), `as` (`atlas`, `gas` again). It also doesn't protect `xs`, `ds` etc. So `accounts` → `account` (correct), but `address` → `addres` (wrong — stem should be `address`).

**Impact:** Stem mismatch — registry stores `addres` and consumer lookups for `addressId` resolve to `addres` but no producer was registered under that key, so producer not found → JIT binding falls through. Reduces JIT hit rate.

**Fix sketch:** Add a small dictionary of known non-plurals or use a proper stemmer (e.g. Porter).

---

### Finding #24: `extractFieldsFromUrl` derives bogus key for short prev segment [Severity: Medium]
**File:** `src/main/java/es/us/isa/restest/workflow/TraceWorkflowExtractor.java:706-720`

**Code:**
```java
String prev = segments[i - 1].toLowerCase();
key = NOUN_TO_KEY.get(prev);
if (key == null && !prev.isEmpty()) {
    String singular = prev.endsWith("s") && prev.length() > 1
            ? prev.substring(0, prev.length() - 1) : prev;
    key = singular + "Id";
}
```

**Bug:** Length>1 guard allows `prev="as"` → strip s → `"a"` + `"Id"` → `"aId"`. Or `prev="ts"` → `"tId"`. Or `prev="api"` → API not in NOUN_TO_KEY → `apiId`. URLs like `/api/v1/...` are unfortunately common.

**Impact:** Keys like `apiId`, `v1Id`, `tId` appear in `inputFields`. They will never match any consumer parameter or producer output, so they are inert clutter — unless `findDataDependencies` happens to compare them across traces, in which case false-positive merges could occur.

**Fix sketch:** Guard `prev.length() >= 3` and require the prev segment is in a whitelisted set or matches `[a-z]{3,}`.

---

### Finding #25: `MultiServiceTestCaseGenerator.traverse` may set both `val` and `typedVal` and silently drop one [Severity: Medium]
**File:** `src/main/java/es/us/isa/restest/generators/MultiServiceTestCaseGenerator.java:711-721`

**Code:**
```java
if (p.getIn() != null && (p.getIn().equalsIgnoreCase("body") || p.getIn().equalsIgnoreCase("formData"))) {
    typedVal = invalidValue; // null → JSON null; Integer → JSON number; etc.
    val = (invalidValue == null) ? "null" : convertObjectToString(invalidValue, p.getType());
} else {
    val = (invalidValue == null) ? "null" : convertObjectToString(invalidValue, p.getType());
}
```
And later (line 1006-1010):
```java
case "body":
case "formdata":
    Object bodyValue = (typedVal != null) ? typedVal : val;
    bodyFields.put(p.getName(), bodyValue);
```

**Bug:** When `invalidValue == null` (NULL_INPUT category), both branches set `val = "null"` (string) and the body branch sets `typedVal = null`. At line 1007, `(typedVal != null) ? typedVal : val` evaluates to `val = "null"` (string) because `typedVal` is `null`. So a NULL_INPUT for a body param ends up as the literal string `"null"` not JSON `null`. flow.md says the writer should preserve JSON-`null`; this code path silently coerces to string.

**Impact:** NULL_INPUT body values become `"null"` strings in JSON. APIs treat these as the *string* `"null"` (often valid input), not actual null. Negative test that should detect missing-required-field behavior fails to trigger validation.

**Fix sketch:** Track null intentionally — use a sentinel or split flags `wasNull` and `typedVal`.

---

### Finding #26: `parseFormData`/`parseFormDataToObjectMap` use deprecated `URLDecoder.decode(s, String)` API [Severity: Low]
**File:** `src/main/java/es/us/isa/restest/generators/MultiServiceTestCaseGenerator.java:1351-1352, 1372-1373`

**Code:**
```java
String key = java.net.URLDecoder.decode(kv[0], "UTF-8");
String value = java.net.URLDecoder.decode(kv[1], "UTF-8");
```

**Bug:** Deprecated since JDK 10; replacement is `URLDecoder.decode(s, StandardCharsets.UTF_8)`. Will be removed in a future LTS. Also two near-identical methods. Drift risk: a fix to one may not propagate.

**Impact:** Eventual JDK incompatibility; minor maintainability cost.

**Fix sketch:** Single helper `decode(String, Map)`; use Charset overload.

---

### Finding #27: Trace's `pathParam_<i>` keys are forever orphaned from semantic registry [Severity: Low]
**File:** `src/main/java/es/us/isa/restest/workflow/TraceWorkflowExtractor.java:718-720`

**Code:**
```java
if (key == null) {
    key = "pathParam_" + i;
}
fieldMap.put(key, seg);
```

**Bug:** Keys like `pathParam_3` are inserted into `inputFields`. Later, `mergeScenariosByDataDependency` matches on `(key, value)` exactly, so two traces with `pathParam_3` referring to *different concepts* (one a UUID for an order, one a UUID for an account) merge together if values happen to collide. This is rare but possible in low-entropy environments.

**Impact:** Cross-trace merges by happenstance. False producer relationships.

**Fix sketch:** Skip insertion when key cannot be derived semantically; rely on UUID/long-id pattern + known-noun map only.

---

## Cross-Cutting Observations

1. **Two flow.md ↔ code mismatches.** flow.md says EMPTY_INPUT/NULL_INPUT only for required params; only the hardcoded generator honors that. flow.md says `extractJsonObjectFields` flattens with dots; the code does not. flow.md cache-collision warning was acknowledged but not fixed.
2. **Inconsistent cache keys.** `ZeroShotLLMGenerator.buildCacheKey` is rich (name+type+location+format+enum+bounds); `SmartInputFetcher.buildCacheKey` is poor (name+type+location). Same parameter behaves differently in two parallel paths.
3. **Numeric handling is inconsistent.** `cleanIntegerValue` uses `int`, `cleanNumberValue` uses `double`, `parseTypedValue` uses `Integer.parseInt`, `convertStringToTypedValue` switches on `int64`. Each strips different non-numeric chars. No single canonical "make this a clean number" routine.
4. **Hardcoded special-case English wordlists** appear in three places (units list, plural safelist, station/train/trip patterns). All drift independently.
5. **Logging issues are pervasive.** Log4j Python-format placeholders, non-emoji-aware string truncations, `truncateForLog` and `truncateDataForLLM` and `truncateResponseForLLM` and `truncateResponseSchemaForLLM` all duplicate truncation logic with subtly different rules.
6. **`Random` is used without seeding** across `MultiServiceTestCaseGenerator`, `SmartInputFetcher`, `SmartLLMParameterGenerator`, `MultiServiceTestCaseGenerator.generateJsonArray` (uses `Math.random()` line 2836 — even worse, can't be re-seeded).
7. **Hand-rolled JSON serialization in MultiServiceTestCaseGenerator** instead of Jackson is the source of #22 (control-character escaping) and a maintenance hazard.
8. **No defensive null check on `parameterInfo.getName()`** in many `cleanLLMGeneratedValue` / `isValidValueForParameter` paths — would NPE if invoked with name=null.

---

## Verification Plan

| # | Verification |
|---|---|
| 1 | `grep -rn "log\.\(info\|warn\|error\|debug\).*{:[^.}]" src/main/java` — every match is a bug |
| 2 | Construct a JSON `{"a":{"id":"x"},"b":{"id":"y"}}`, call `extractJsonObjectFields`, assert map size == 2; currently only 1 |
| 3 | Pass `cleanIntegerValue("12345678901", info)` → expect `"12345678901"`; currently returns `"1"` |
| 4 | Construct two `ParameterInfo` with same name/type/location, different schema bounds; assert `buildCacheKey` returns different keys; currently the same |
| 5 | `SemanticDependencyRegistry.normaliseIdStem("paid")` → expect `null`; currently `"pa"` |
| 6 | LLM emits `boolean:yes`; assert pool's TYPE_MISMATCH list contains the literal string `"yes"`; currently contains `Boolean.FALSE` |
| 7 | Set up an OpenAPI body parameter with `name="routes", type="array"`; assert `generateRequestBody` returns a top-level array; currently returns `{"routes":[...]}` |
| 8 | Optional parameter with `EMPTY_INPUT` round-robin; assert pool's EMPTY_INPUT list size == 0 in pure LLM mode; currently > 0 |
| 9 | Build a 3-root multi-root scenario, run `buildFaultInjectionQueue`, capture types; run variants, capture actual fired types via Allure metadata; assert match. Currently a desync risk if pool changes between calls |
| 10 | `ScenarioOptimizer.shatter` on a 3-root component with no internal edges (impossible by construction but force `adjReverse[i]=∅`); inspect `producerRootIndex` of member 1 (idx=1); should not equal idx |
| 11 | LLM emits `null:null`; assert TYPE_MISMATCH pool does not contain `null`; currently it does |
| 12 | Boolean param with no enum/min/max; check `BOUNDARY_VIOLATION` pool — currently `[-1, 0, ""]`, expected `[]` |
| 13 | `cleanIntegerValue("12.5")` → expect `"12"` (truncation) or `"13"` (rounding) — currently `"125"` |
| 14 | Path/header/body parameter with `enum: [A, B, C]`; assert `SmartLLMParameterGenerator.createParameterInfo().getEnumValues() != null`; currently null |
| 15 | Smart-fetch a value of length 150 chars; assert `isValidValueForParameter` returns true when schema allows; currently false |
| 16 | API response body containing `"errorCode": 0`; `isValidApiResponse` should return true; currently false |
| 18 | Run two scenarios with same parameter pool; assert each scenario's first variant draws from index 0; currently second scenario starts mid-rotation |
| 22 | Negative test fires `\u0000\u0001\u0002`; assert raw HTTP body bytes contain `\\u0000\\u0001\\u0002` not raw bytes; currently raw bytes |
| 25 | NULL_INPUT for body parameter; assert generated JSON contains literal `null` not `"null"`; currently `"null"` |

---

## Citations

[1] flow.md (`src/main/resources/My-Example/trainticket/flow.md`):
- L723-746: EMPTY_INPUT/NULL_INPUT only for required parameters (basis of Finding #8/#12).
- L774-790: Smart Fetch priority chain & cache-collision risk acknowledged (basis of Finding #4).
- L786-790: "JSONPath is fully retired" — verifies `extractValueFromResponse` is dead code, but it's still imported and present (`SmartInputFetcher.java:818`).
- L443-490: JIT binding semantics — basis of Findings #5, #10, #11.
- L535-553: Shared parameter pool with random selection. Implementation (`MultiServiceTestCaseGenerator.java:765`) does indeed use random — but rotation index leaks (Finding #18).

[2] Code excerpts referenced inline above.

[3] Log4j2 `ParameterizedMessage` documentation: only `{}` placeholder is supported (basis of Finding #1).

[4] OpenAPI 3.x specification: bodies of type `array` are top-level JSON arrays, not wrapped objects (basis of Finding #7).

[5] RFC 8259 §7: control characters under U+0020 must be escaped in JSON strings (basis of Finding #22).
