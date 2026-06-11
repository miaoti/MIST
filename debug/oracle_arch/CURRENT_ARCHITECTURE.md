# MIST Oracle — Current Architecture (as-is map)

Read-only map of the existing code, file:line evidenced (verified 2026-05-28 on
`inject-detection` @ `a59044e9`). This is the substrate the [target design](TARGET_ARCHITECTURE.md)
refactors. Nothing here is a proposal — it is what the code does today.

---

## 1. Components & dataflow

The negative-test → oracle path spans three modules:

```
GENERATION (mist-core)                WRITER-EMITTED TEST (mist-cli writer → JUnit)        TRACKING (mist-core)
─────────────────────                ────────────────────────────────────────────        ───────────────────
MistGenerator                         attachJaegerTrace(...)  [emitted, runs per step]     FaultDetectionTracker
 ├ buildFaultInjectionQueue            ├ fetch Jaeger trace by marker id                    ├ recordVerdict(...)
 │   → FaultTarget {root,param,        ├ TraceShapeAdapter.toModel(trace)                   └ recordOracleAnomaly(...)
 │     location,type,value}            ├ oracle.evaluate(model, rootApiKey,                      → ShapeInvariantStore
 │   (MistGenerator.java:107-131)      │                   targetService, targetParam)
 ├ generateScenarioVariants            │     TraceShapeOracle.java:72-94
 │   → de-normalizes FaultTarget       │       SpanTree / StatusProp / Timing / RespEnv
 │     into MultiServiceTestCase       │       + TargetAttribution (iff targetService!=null)
 │     scalar fields; FaultTarget      └ recordVerdict(verdict, ...)   writer:719-721
 │     DISCARDED (MistGenerator.java:728)
 └ fault types = data (8) in
   fault-types.default.yaml
```

### Generation (intent origin)
- `MistGenerator.FaultTarget` (`MistGenerator.java:107-131`) is the **cohesive intent object**: `rootApiKey, rootIndex, paramName, paramLocation, type, value`. Built per scenario in `buildFaultInjectionQueue` (`:143-190`).
- It is **dereferenced into scattered scalar fields** on `MultiServiceTestCase` in the variant loop (`:717-729`): `setTargetFaultRootId("Root "+i)` (`:718`), `setFaultTypeCategory` (`:719`), `setTargetFaultRootApiPath` (`:720`), `setTargetFaultParamLocation` (`:725`), `setTargetFaultValue` (`:728`); invalid value applied via `addFaultyParameter(name,val)` (`:1100`/`:1124`). **`FaultTarget` itself is garbage after `:728`.**
- **Sniper invariant**: exactly one param of one root is faulted; `targetFaultyParams` holds one name (`:690,695`); non-target roots get an empty target list (`:752-754`); non-target params skip the fault pool (`:1142,1172,1198`, logged `- LOCKED` `:1104,1126`).
- **8 fault types** are pure data (`mist-core/src/main/resources/mist/fault-types.default.yaml`), loaded via `FaultTypeRegistry` → `FaultType` (`FaultType.java:19-73`). Each is an **invalid-value strategy only** — `applicableTo`/`applicableLocations`, no expected-response semantics.

### Writer emission (the per-step oracle call)
- The oracle runs **inside the emitted `attachJaegerTrace(...)`** (`MultiServiceRESTAssuredWriter.java:287` signature; `:685` the `oracle.evaluate(model, rootApiKey, targetService, targetParam)` call).
- `__targetService` / `__targetParam` are **reconstructed at write-time from the de-normalized fields** (`:1336-1369`): `getTargetFaultRootId()` → normalize `"Root N"→"RN"` → scan `getSteps()` for matching `hierarchicalId` → `getServiceName()`; `__targetParam` = name-half of `getFaultyParameters().get(0)` split on `'='`. Fragile string round-trips, `"null"` when unresolved.
- Trace fetch: **marker-first** exact `GET /traces/<markerTraceId>` (5×200 ms, `:349-379`) using injected W3C `traceparent` (`:1979`), then **time-window heuristic** fallback (3 retries × 8 query strategies, `:381-615`).
- Verdict → `FaultDetectionTracker.recordVerdict(verdict, rootApiKey=method+" "+path, testClass, testMethod, traceId=markerTraceId)` (`:719-721`). `LAST_VERDICT` ThreadLocal (`:252`) read back for status flips (`:2275-2284` positive, `:2442-2452` negative).

### Oracle (judging)
- `TraceShapeOracle.evaluate(trace, rootApiKey, targetService, targetParam)` (`TraceShapeOracle.java:72-94`) assembles per-invariant outcomes; verdict `passed` = AND of **ERROR-severity** outcomes only (`TraceShapeVerdict.java:64-73`).
- 5 invariants (all `mist-core/.../oracle/shape/invariant/`):

| Invariant | KIND | Severity | Learns | Intent-aware? |
|---|---|---|---|---|
| SpanTreeShape | `SPAN_TREE_SHAPE` | ERROR | observed/required service edges + fan-out, from `known-good` traces | no |
| StatusPropagation | `STATUS_PROPAGATION` | ERROR | per-depth allowed http/otel status set | no |
| ResponseEnvelope | `RESPONSE_ENVELOPE` | ERROR/INFO | `successSet` of a body field on 2xx roots; **`failureSet` always empty**, LLM path is `TODO` → ~never ERROR-fails | no |
| TimingEnvelope | `TIMING_ENVELOPE` | **WARN** (never flips verdict) | duration p50/p95/p99 | no |
| TargetAttribution | `TARGET_ATTRIBUTION` | **INFO** (never flips verdict) | nothing (`T=Void`) | **yes** (only one) |

- Learning: **cold-start only**, from `known-good` (positive) traces only (`TraceShapeLearner.java:86-117`; trigger `MistRunner.java:809,815-823`). Store = JSON, `.mist/trace-shape-invariants.json` (`ShapeInvariantStore.java:32-33`).

---

## 2. The three architectural gaps (what blocks the intent-aware-oracle thesis)

### G1 — Intent is generated, then discarded
`FaultTarget` (`MistGenerator.java:107-131`) is a complete intent object but is **dropped at `:728`**; the writer re-derives a *lossy* subset (`targetService`, `targetParam` only) by fragile string round-trips (`:1336-1369`). The injected **value** and **fault type** never reach the oracle (`attachJaegerTrace` signature `:287` has no such params).

### G2 — Expected outcome is never represented as data
`StepCall.expectedStatus` is **always the success status** (default 200, `MistGenerator.java:1453-1484`); there is **no** branch flipping it to 4xx for a faulty variant. "Should be rejected" exists only as a **hardcoded runtime predicate** in emitted code (`non-2xx == pass`, writer `:2017,:2102`) and the INFO-only `TargetAttributionInvariant`. No `ExpectedOutcome` object exists.

### G3 — Observed outcome is hidden from the oracle
Both `actualStatusCode<N>` (observed) and `expectedStatusCode<N>` exist in the **same method scope** (`:1986-2017`), but **neither is passed to `attachJaegerTrace`/`oracle.evaluate`** (`:287,:685`). The oracle's only view of status is **scraped from Jaeger span tags** (`TraceShapeAdapter.java:150`) — a second, lag-prone observation, not the authoritative REST-assured response.

---

## 3. Capability gaps that follow (verified, not inferred)

- **Silent acceptance** (negative test that SHOULD be rejected but SUT returns 2xx): **no invariant can detect it.** Intent-agnostic invariants treat a 2xx/success trace as the normal shape (pass); `TargetAttributionInvariant` on a clean trace → `findLeafError` null (`LeafErrorSpanFinder.java:48-49,88-92`) → `NO_ATTRIBUTION` → `passed=true` (`TargetAttributionInvariant.java:72-73`).
- **Hidden downstream failure** (root/gateway 2xx but a deep span errored): only `StatusPropagationInvariant` *incidentally* (per-depth status whitelist), never by root↔descendant correlation; misses status-less spans and depths deeper than trained (`StatusPropagationInvariant.java:73-83`). The attribution path can't see it (needs an error-tagged **root** to start descending, `LeafErrorSpanFinder.java:48-49`).
- **Param-level attribution** (`TARGET_REJECTION`): structurally 0 on train-ticket (see the Stage-2a cert finding). `MethodToParamMapper` tier-1/3 unimplemented (`MethodToParamMapper.java:12-27`).
