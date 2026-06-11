# Input Generation — End-to-End Data Flow Map
Date: 2026-04-26
Branch: inject-detection

This document is a **call-graph and state-flow** for parameter input generation, with file:line citations into the actual source. It is the companion to `pipeline-bug-audit.md` (bugs) and `input-quality-measurement-framework.md` (metrics). The narrative summary in `flow.md` is the design intent; this document is the implementation reality.

---

## A. Positive Input Generation

### A.1 First-Step Parameters (HIGH relevance)

**Entry point:** `MultiServiceTestCaseGenerator.java:2168-2195`

1. **Shared Pool generation** (initialised once at `MultiServiceTestCaseGenerator.java:258`).
   - `generateSharedParameterPools(groupedScenarios)` populates:
     `sharedParameterPools : Map<String, Map<String, List<String>>>` (declared at line 242).
   - Outer key: `rootApiKey = verb.toUpperCase() + "_" + route.replaceAll("[^a-zA-Z0-9_]", "_")` (e.g., `GET_api_v1_order`).
2. **`targetPoolSize` formula** (`MultiServiceTestCaseGenerator.java:2350-2359`):
   - 1 param  → `variantCount + 10`
   - 2 params → `max(25, sqrt(variantCount) * 2)`
   - 3-5 params → `max(20, ceil(variantCount^(1/numParams)) + 5)`
   - 6+ params → 15
3. **Three-phase population** (`MultiServiceTestCaseGenerator.java:2272-2311`):
   - **Phase 1 (Smart Fetch loop):** line 2276 — `smartFetcher.fetchSmartInput(info)` until pool reaches target.
   - **Phase 2 (LLM fallback):** line 2297 — `llmGen.generateParameterValues(info, needed)` with `howMany = min(needed, 50)`.
   - **Phase 3 (Padding):** lines 2309-2311 — `FALLBACK_<paramName>_<index>` strings to guarantee pool size.
4. **Variant-time read** (`MultiServiceTestCaseGenerator.java:761-778`): random draw from `sharedParameterPools.get(rootApiKey).get(paramName)` via `random.nextInt(pool.size())`. Falls through to Smart Fetch / LLM if pool absent.

### A.2 Non-First-Step Parameters (HIGH relevance)

**Entry point:** `MultiServiceTestCaseGenerator.java:660-968` (`traverse()`).

Priority chain (line numbers approximate; matches flow.md priority 1-7):

| Priority | Source | Line | Method |
|---|---|---|---|
| 1 | Negative-test target check (skip rest) | 889-891 | inline |
| 2-PROV | `dataProvenance` (cross-trace merge value) | 863-868 | `span.getDataProvenance().get(paramName)` |
| 2-CTX | Context map (output→input dependency) | 871-877 | `context.get(paramName)` |
| 2-REUSE | Context map (input reuse) | 880-886 | same map, different lookup pattern |
| 3-JIT | `SemanticDependencyRegistry` JIT binding | 1102-1187 | `dependencyRegistry.findProducer()` → `call.addParamDependency()` |
| 4 | Trace replay value | 892-898 | `getTraceParameterValue(span, paramName)` |
| 5 | Shared Parameter Pool | 829 (negative-fallback) / 761 | `sharedParameterPools.get(...).get(...)` |
| 6 | Smart Fetch (independent) | 912-935 | `smartFetcher.fetchSmartInput(info)` |
| 7 | LLM generation | 938-956 | `llmGen.generateParameterValues(info, 1)` |
| 8 | Placeholder `VAL_<name>` | 967 | hard-coded last resort |

> **Discrepancy with flow.md:** flow.md lists trace replay as priority 4 and shared pool as priority 5, but in the actual code the shared pool is consulted from the negative-fallback path at line 829 and from a different branch on the first step at 761. The non-first-step traverse path goes through Smart Fetch/LLM directly and reaches the shared pool only on the negative-fallback branch.

### A.3 SmartInputFetcher Call Chain (MEDIUM relevance)

**Entry:** `SmartInputFetcher.java:147` — `fetchSmartInput(ParameterInfo param)`.

1. **Decision gate** (`SmartInputFetcher.java:154-158`):
   `random.nextDouble() < config.getSmartFetchPercentage()`. Default 90 % smart, 10 % LLM.
2. **`fetchFromSmartSource`** (`SmartInputFetcher.java:197-278`):
   - **Priority 0** — trace-observed producer endpoints (`SmartInputFetcher.java:211-230`).
   - **Priority 1** — registry mappings (`SmartInputFetcher.java:234`).
   - **Priority 2** — LLM-driven discovery (`SmartInputFetcher.java:238-241`) when registry empty AND discovery enabled.
3. **Direct extraction** (`SmartInputFetcher.java:451, 500`):
   - Build `directExtractionPrompt` (line 505).
   - LLM call `askLLMForDirectValueExtraction(prompt)` (line 515).
   - Guard against LLM returning JSONPath (lines 526-532).
   - Format value per OpenAPI schema (line 545).
4. **Fallback** (`SmartInputFetcher.java:177-181`): null result or any exception → `fallbackToLLM(parameterInfo)`.

### A.4 ZeroShotLLMGenerator Prompt + Parse (MEDIUM relevance)

**Entry:** `ZeroShotLLMGenerator.java:75` — `generateParameterValues(ParameterInfo, int howMany)`.

- **Prompt build** (`ZeroShotLLMGenerator.java:681-750`): persona, API context, parameter details, output-format directive.
- **LLM call** (`ZeroShotLLMGenerator.java:806`): delegates to `LLMService.callLLM(prompt)`.
- **Parse** (`ZeroShotLLMGenerator.java:92-113`):
  - Array type → `parseJsonArray(rawOutput)`.
  - Other types → `parseLines(rawOutput)`.
  - Regex filter (`102-104`).
  - Empty fallback (`107-109`) returns `Collections.singletonList("")` (note: this is itself a quality bug — see audit Finding #15).
- **Cache** (`ZeroShotLLMGenerator.java:112`): `cache.put(buildCacheKey(param), values)`. Cache key uses `paramName+type+location` only (collision risk — see audit Finding #4).

### A.5 Shared Parameter Pool Lifecycle (LOW relevance)

| Aspect | Value |
|---|---|
| Type | `Map<String, Map<String, List<String>>>` |
| Created | `MultiServiceTestCaseGenerator.java:242, 2183` |
| Read | `:761` (first-step), `:829` (negative fallback) |
| Thread-safe? | NO (`HashMap`) — but mutation completes before parallel variant generation |
| Fallback when missing | Smart Fetch / LLM at the appropriate priority |

---

## B. Negative (Faulty) Input Generation

### B.1 Variant Decision Logic (HIGH relevance)

**File:** `MultiServiceTestCaseGenerator.java:101-121, 318-397`.

1. **Exhaustive fault queue** (`:101-121, :328`):
   - For each `(rootIdx, rootApiKey, paramName, InvalidInputType)` create one `FaultTarget`.
   - `FaultTarget` struct at lines 72-88.
2. **Dynamic variant sizing** (`:331-342`):
   - `positiveBase = max(1, round(configuredVariantCount × (1 − faultyRatio)))`.
   - `requiredNegative = max(round(configuredVariantCount × faultyRatio), totalNegativeSlots)`.
   - `variantCount = positiveBase + requiredNegative`.
3. **Variant classification** (`:368`):
   `boolean isFaultyVariant = (v >= positiveBase) && (faultQueueCursor < faultQueue.size());`
4. **Sniper strategy** (`:414-429`): only the targeted root receives the fault; siblings get positive inputs.

### B.2 InvalidInputPool Structure (HIGH relevance)

**File:** `InvalidInputPool.java:1-200`.

| Field | Line | Purpose |
|---|---|---|
| `valuesByType : Map<InvalidInputType, List<Object>>` | 14 | All values per fault type |
| `usedIndicesByType : Map<InvalidInputType, Set<Integer>>` | 15 | Round-robin consumption tracking |
| `currentTypeIndex : int` | 21 | Rotation cursor across types |
| `typeRotation : List<InvalidInputType>` | 30-39 | Priority order |

**Type-rotation priority** (lines 30-39):
1. BOUNDARY_VIOLATION
2. OVERFLOW
3. NULL_INPUT
4. EMPTY_INPUT
5. SPECIAL_CHARACTERS
6. TYPE_MISMATCH
7. REGEX_MISMATCH
8. SEMANTIC_MISMATCH

Lifecycle:
- Created at `MultiServiceTestCaseGenerator.java:56`, populated by `generateFaultyParameterPools()` at `:2368-2418`.
- Reset at `:347` before each variant generation (`pool.resetUsage()`).
- Read at `:687` (round-robin) / `:728` (random).

### B.3 Round-Robin Mode (HIGH relevance)

**Code:** `MultiServiceTestCaseGenerator.java:694-725`, `InvalidInputPool.java:72-105`.

1. Exhaustion check `pool.hasNextRoundRobin()` (`:698`).
2. Get next value `pool.getNextRoundRobin()` (`:702`):
   - Rotates types via `currentTypeIndex` (`InvalidInputPool.java:77-91`).
   - For each type, picks first unused index, marks used (`:84`), advances cursor (`:91`).
   - Records `lastSelectedType` for telemetry (`:88`).
3. Type coercion:
   - Body / formData → keep typed (Integer, Boolean, null) → `typedVal`.
   - Path / query / header → string `val`.
4. Tracking: `tc.addFaultyParameter(p.getName(), val)` (`:719`).

> Note: `getNextRoundRobin()` may legitimately return Java `null` (NULL_INPUT). The exhaustion signal is `hasNextRoundRobin()` returning false (`:124-130`), not a null return.

### B.4 Random Mode (MEDIUM relevance)

`InvalidInputPool.getRandomValue(random)` at `:135-154` picks a random non-empty type then a random value from it. **Repetition allowed.**

### B.5 HardcodedInvalidInputGenerator (HIGH relevance)

**File:** `HardcodedInvalidInputGenerator.java:1-576`.

| Fault type | Method line | Notes |
|---|---|---|
| TYPE_MISMATCH | 67-148 | Returns typed objects (Integer, Boolean, List, Map) — keep typed for body |
| OVERFLOW | 154-207 | Strings up to 10 000 chars; `Long.MAX_VALUE`; 1 000-elem list |
| EMPTY_INPUT | 214-241 | **Required-only gated** at line 216 |
| NULL_INPUT | 248-267 | **Required-only gated** at line 250; both Java null and string `"null"`/`"NULL"` |
| SPECIAL_CHARACTERS | 273-303 | SQL/XSS/path-traversal/cmd-injection |
| BOUNDARY_VIOLATION | 316-393 | Schema-driven `min-1`, `max+1`; canonical fallbacks |
| REGEX_MISMATCH | 398-450 | Email / phone / date / UUID hand-curated |
| SEMANTIC_MISMATCH | 451-500+ | Age / price / quantity / country etc. |

### B.6 LLM-Mode Invalid Pool (MEDIUM relevance)

`ZeroShotLLMGenerator.java:160-235`. Mode selector at `:161` reads `negative.input.generation.mode` (`smart` default, also `llm`, `hardcode`).

- **smart mode** dispatch at `:194-214`:
  - Hardcoded for 6 universal types (TYPE/OVERFLOW/EMPTY/NULL/SPECIAL/BOUNDARY).
  - LLM for 2 context-aware types (REGEX_MISMATCH, SEMANTIC_MISMATCH).

> **Discrepancy with flow.md:** flow.md says EMPTY/NULL are required-only. `HardcodedInvalidInputGenerator` honors that (lines 216, 250). `ZeroShotLLMGenerator` (`:194-214`) does NOT gate them on `required` — see audit Finding #8 / #12.

---

## C. Shared Mutable State Inventory

| Structure | Type | Created | Mutated | Read | Thread-safe? | Scope |
|---|---|---|---|---|---|---|
| `sharedParameterPools` | `Map<String, Map<String, List<String>>>` | `MultiServiceTestCaseGenerator.java:242, 2183` | `:2183` only | `:761, :829` | **No** (HashMap) | Across all variants |
| `faultyParameterPools` | `Map<String, Map<String, InvalidInputPool>>` | `:56, :2407` | `:2407` (put), `:347` (reset) | `:110, :687` | **No** | Across all variants; per-pool reset per variant |
| `usedIndicesByType` (in pool) | `Map<InvalidInputType, Set<Integer>>` | `InvalidInputPool.java:45` | `:84` | `:129` | **No** | Per parameter, per round |
| `currentTypeIndex` (in pool) | int | `InvalidInputPool.java:21` | `:91, :98` | `:77` | **No** | Per pool |
| `context` | `Map<String, String>` | `MultiServiceTestCaseGenerator.java:411` | `:872, :881` | `:872, :881` | **No** | Per variant |
| `SmartInputFetcher.cache` | `ConcurrentHashMap<String, CachedValue>` | `SmartInputFetcher.java:85` | `:259` | `:204` | **Yes** | Per fetcher instance — collision risk: cache key omits scenario/workflow scope (audit Finding #4) |
| `ZeroShotLLMGenerator.cache` | `ConcurrentHashMap<String, List<String>>` | `ZeroShotLLMGenerator.java:29` | `:112` | `:80` | **Yes** | Per generator |
| `SemanticDependencyRegistry.consumerIndex` | `Map<String, Map<String, ProducerBinding>>` | `:96` | Built once | `:1102` | **No**, but immutable after build | Whole run |
| `dataProvenance` (per WorkflowStep) | `Map<String, String>` | trace extractor | not mutated | `:863, :1084` | n/a | Per step (read-only) |
| `traceProducerEndpoints` (per ParameterInfo) | `List<String>` | `:909` | not mutated | `SmartInputFetcher.java:211` | depends on impl | Per parameter info |

---

## D. Boundaries and Error Paths

### D.1 Silent / Logged Catches

| Line | Source | Exception | Handling | Impact |
|---|---|---|---|---|
| `MultiServiceTestCaseGenerator.java:798-801` | Smart fetch (step 1) | Exception | log.warn, continue to LLM | transparent |
| `MultiServiceTestCaseGenerator.java:930-934` | Smart fetch (independent) | Exception | log.debug, val = null, fall through | transparent |
| `MultiServiceTestCaseGenerator.java:1178` | dyn path finder JSON | Exception | `catch (Exception ignored)` | **silent swallow** |
| `MultiServiceTestCaseGenerator.java:2285-2288` | smart fetch during pool build | Exception | log.debug, continue | transparent |
| `SmartInputFetcher.java:226-227` | trace endpoint fetch | Exception | log.debug, try next | transparent |

### D.2 Null / Empty Returns

- Smart fetch returns null → fall through to LLM at `:805` (step 1) or `:944` (independent).
- LLM returns empty → placeholder `LLM_EMPTY_<name>` at `:809` and `:942`.
- Pool exhaustion (round-robin) → demote variant to positive at `:459-467`.

### D.3 Missing Configuration

| Line | Condition | Response |
|---|---|---|
| `:238-240` | service config null | log.warn, propagate to children |
| `:244-246` | operation config null | log.warn, skip parameter generation |
| `:2245` | opCfg null during faulty pool gen | log.warn, skip root |
| `:2431-2433` | businessStep null during faulty pool gen | log.warn, return empty pool |

---

## E. Invariants and Their Enforcement

| Invariant | Enforcement | Location | Can Break? | Silent? |
|---|---|---|---|---|
| Shared pool always non-empty before variants | Padding fallback | `:2292-2311` | If padding also fails (unlikely) | No (logged) |
| Invalid pool has ≥1 value per fault type | Hardcoded defaults | `HardcodedInvalidInputGenerator.java:40-48, :283-284` | LLM-mode REGEX/SEMANTIC can be empty; no fallback there | **Yes** (silent) |
| Required-only EMPTY/NULL | null-check in HardcodedInvalidInputGenerator | `:216, :250` | NOT gated for TYPE/OVERFLOW/BOUNDARY/SPECIAL; not gated at all in ZeroShotLLMGenerator | **Partial** |
| ParamDependency matches trace reality | backward scan + registry fallback | `:1117-1137` | Trace response unavailable → uses default jsonPath; no validation that extracted value is non-empty | **Yes** (silent) |
| Faulty value is actually invalid | Validation block | `:447-456` | Placeholder strings re-detected → variant demoted to positive | **Logged** at `:459` |
| Global payload dedup succeeds | Fingerprint + seenPayloads + 5 retries | `:479-507` | After 5 retries duplicate → variant skipped | **Logged** at `:506` |

---

## F. Critical Call Paths (Cheatsheet)

```
POSITIVE FIRST STEP
generate()
└─ generateSharedParameterPools()
   └─ generateParameterPoolForRootApi()
      ├─ Phase 1: smartFetcher.fetchSmartInput() loop
      ├─ Phase 2: llmGen.generateParameterValues()
      └─ Phase 3: FALLBACK_<name>_<i>

generateScenarioVariants()
└─ traverse()  [line 760-778]
   └─ random draw from sharedParameterPools.get(rootApiKey).get(paramName)
      └─ on miss → smart fetch → LLM → "VAL_" placeholder

POSITIVE LATER STEP
traverse()  [line 860-968]
├─ provenance check (priority 2-PROV)
├─ context map check
├─ trace data
├─ JIT binding (SemanticDependencyRegistry)
├─ smart fetch (independent)
├─ LLM
└─ "VAL_" placeholder

NEGATIVE
generateScenarioVariants()
└─ buildFaultInjectionQueue()  [line 101-121]
   └─ for each FaultTarget:
      ├─ resolved pool = faultyParameterPools.get(rootApiKey).get(paramName)
      ├─ round-robin: pool.getNextRoundRobin()  OR  random: pool.getRandomValue(random)
      ├─ type coerce (typed for body, string for path/query/header)
      └─ tc.addFaultyParameter()
```

---

## G. Documentation vs Code Discrepancies

| flow.md statement | Code reality | File |
|---|---|---|
| EMPTY/NULL only for required parameters | `HardcodedInvalidInputGenerator` honors it; `ZeroShotLLMGenerator` does not | flow.md `1006-1013`, `HardcodedInvalidInputGenerator.java:216, 250` vs `ZeroShotLLMGenerator.java:194-214` |
| `extractJsonObjectFields` flattens nested keys with dot prefix | Code passes flat map without prefix → key collisions | `TraceWorkflowExtractor.java:582-617` |
| Priority chain has explicit ordering 1-7 | First-step path (line 760-778) and negative-fallback path (line 829) both reach the shared pool, but the non-first-step `traverse` reaches Smart Fetch/LLM directly without consulting the pool unless the dependency tier fails | `MultiServiceTestCaseGenerator.java:660-968` |
| Smart Fetch cache lookup uses scenario-aware key | Cache key is `name+type+location` only — already noted as a limitation in flow.md | `SmartInputFetcher.java:` (buildCacheKey) |

---

## H. Sources

- `src/main/resources/My-Example/trainticket/flow.md` (1598 lines)
- `src/main/java/es/us/isa/restest/generators/MultiServiceTestCaseGenerator.java`
- `src/main/java/es/us/isa/restest/generators/ZeroShotLLMGenerator.java`
- `src/main/java/es/us/isa/restest/generators/HardcodedInvalidInputGenerator.java`
- `src/main/java/es/us/isa/restest/generators/InvalidInputPool.java`
- `src/main/java/es/us/isa/restest/inputs/smart/SmartInputFetcher.java`
- `src/main/java/es/us/isa/restest/workflow/SemanticDependencyRegistry.java`
- `src/main/java/es/us/isa/restest/workflow/ScenarioOptimizer.java`
- `src/main/java/es/us/isa/restest/workflow/TraceWorkflowExtractor.java`
