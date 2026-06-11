# B1 Inventory — `MultiServiceTestCaseGenerator` ↔ RESTest dependency surface

> Phase B1.A deliverable for `PROMPT_B1_SEVER_RESTEST_INHERITANCE.md`.
> Status: **All seven B1 phases delivered. B1.A inventory complete;
> B1.B vendoring of nine data carriers done (seven configuration
> POJOs + TestCase + MultiServiceTestCase); B1.C migration complete
> — every MIST-owned class promoted into `mist-core`, including the
> 2952-LOC `MultiServiceTestCaseGenerator` now in
> `io.mist.core.generation.MistGenerator`, the twelve-file workflow
> pipeline cluster, the 1510-LOC `SemanticDependencyRegistry`, the
> 4450-LOC `SmartInputFetcher`, and ~30 smaller MIST-only classes;
> B1.D sever done; B1.E + B1.F SPI surface and three adapter-side
> SPI implementations registered via `META-INF/services/`; B1.G
> verified via live demo against TrainTicket cluster at
> `http://129.62.148.112:32677` (123 `Flow_Scenario_*.java` test
> sources generated end-to-end through the post-rebuild pipeline,
> with `io.mist.core.*` imports throughout — no Java exceptions,
> no SPI lookup failures). Cardinal § 7.5 criterion
> ("`mist-core/src/main/java` has zero `es.us.isa` references") is
> firmly held. Adapter MIST surface is now: spec loader bridge +
> RESTAssured writer + legacy `TestGenerationAndExecution` main +
> `MistRunner` + three SPI provider classes + two converters
> (PojoConverter, TestCaseConverter).**

## 1. What this branch has done

1. Severed `MultiServiceTestCaseGenerator extends AbstractTestCaseGenerator`
   in place. The audit in § 3 shows the inherited surface MIST actually
   consumed was empty except for one setter from outside callers, which is
   now a documented no-op on `MultiServiceTestCaseGenerator` itself.
2. Promoted 33 RESTest-dependency-free MIST classes out of
   `es.us.isa.restest.*` and into `io.mist.core.*`. None of these classes
   ever depended on RESTest; they were sitting in RESTest packages by
   historical convention.
3. Moved two MIST-only util helpers (`ConsoleProgressBar`, `IDGenerator`)
   from `es.us.isa.restest.util` into `io.mist.core.util`. These are the
   only "non-leaf" moves so far: the adapter still references them, but
   via the new `io.mist.core.util.*` imports.
4. Vendored the seven leaf configuration POJOs into
   `io.mist.core.spec.*` as verbatim copies (per § Phase B1.B):
   `Auth`, `Generator`, `GenParameter`, `TestParameter`, `Operation`,
   `TestConfiguration`, `TestConfigurationObject`. Adapter callers still
   use the originals — these vendored copies are unreferenced until a
   follow-up swap pass.
5. Adjusted poms for the new compile-classpath needs (jackson, junit,
   okhttp lifted to mist-core; swagger.version hoisted to parent so
   mist-core's vendored `Operation` can reference the OASv3 model).

## 2. Module layout snapshot (this branch HEAD)

```
mist-core/src/main/java/io/mist/core/
├── analysis/        (4 files — fault-detection + trace error analysis)
├── bandit/          (1 file  — ThompsonScheduler, unchanged)
├── config/          (2 files — MstConfig + MstConfigValidator)
├── enhancer/        (6 files — failed-test capture + regenerator + TestCaseEnhancer)
├── fault/           (8 files — InvalidInputPool + FaultTypeRegistry family)
├── generation/      (3 files — AiDrivenLLMGenerator, ZeroShotLLMGenerator,
│                                HardcodedInvalidInputGenerator)
├── llm/             (1 file  — ParameterInfo)
├── oracle/shape/    (6 files — Trace Shape Oracle, unchanged)
├── registry/        (3 files — RootApiRegistry + ApiTree + RootApiEntry)
├── smart/           (11 files — smart-fetch data classes + caches + SmartInputFetcher)
├── spec/            (7 files — vendored configuration POJOs, B1.B)
├── spi/             (5 files — MistSpec, MistSpecLoader, MistTestWriter,
│                                MistTestExecutor, MistServices, B1.E)
├── util/            (3 files — SeededRandom + ConsoleProgressBar + IDGenerator)
├── value/           (2 files — ValueProvenance + ResolvedValue, unchanged)
└── workflow/        (5 files — WorkflowScenario, WorkflowStep, NounKeyMap,
                                 WorkflowScenarioUtils, TraceWorkflowExtractor)
```

`mist-core` total Java files: **80** (up from 55 at the previous snapshot,
17 at the start of B1). `mist-restest-adapter` MIST-relevant files left in
its tree: **44**, plus the new `io.mist.adapter.restest.*` package with
three SPI/converter implementations and a `META-INF/services/` registration.

## 3. Category A — `AbstractTestCaseGenerator` surface MIST actually used (closed)

`MultiServiceTestCaseGenerator extends AbstractTestCaseGenerator` was
inherited from upstream RESTest by convention, but a complete audit
showed MIST consumes **none of the inherited state** and **only one
inherited setter from the outside**:

| Element | Inherited from base | MIST actually used it? | `file:line` (pre-sever) |
|---|---|---|---|
| `super(spec, conf, nTests)` ctor | yes (only mechanism to populate base's `spec`, `conf`, `numberOfTests`) | **no** — MIST never reads `super.spec`/`super.conf`/`super.numberOfTests`; the same values are also stored on MIST's own fields | `MultiServiceTestCaseGenerator.java:291` |
| `super.spec` / `super.conf` / `super.numberOfTests` | yes | no reference | — |
| `super.rand` / `super.seed` | yes | no reference — MIST owns `private Random random = SeededRandom.create(...)` instead | `:73` |
| `super.nominalGenerators` / `super.faultyGenerators` | yes | no reference — MIST owns `Map<String, Map<PoolKey, InvalidInputPool>> faultyParameterPools` | `:72` |
| `super.authManager` | yes | no reference | — |
| `super.faultyRatio` | yes (protected) | **shadowed** by MIST's own private field | `:63` (decl); `:307` (assigned from `MstConfig.faulty().ratio()`) |
| `super.n*` counters | yes | no reference | — |
| `generateOperationTestCases(Operation)` | abstract | overridden as a no-op stub | `:757` (pre-sever) |
| `generateNextTestCase(Operation)` | abstract | overridden as a no-op stub | `:759` |
| `hasNext()` | abstract | overridden as a no-op stub | `:761` |
| `generate()` (concrete) | concrete | **completely replaced** — never calls `super.generate()` | `:407` |
| `setCheckTestCases(boolean)` (concrete) | concrete | called by `MistRunner.createMstGenerator()` (`MistRunner.java:546`) and `TestGenerationAndExecution.createGenerator()` (`:336`); the field is never read by MIST | external |

**Result of B1.D:** dropped the `extends` clause, removed `super(...)`,
removed the three no-op overrides, added a documented no-op
`setCheckTestCases(boolean)` so the external CLI surface compiles
unchanged.

## 4. Category E — MIST-owned classes that have moved out of `es.us.isa.restest.*`

| Old location | New location | Notes |
|---|---|---|
| `analysis/FaultDetectionTracker` | `io.mist.core.analysis.FaultDetectionTracker` | |
| `analysis/IntelligentAnalysisCache` | `io.mist.core.analysis.IntelligentAnalysisCache` | |
| `analysis/TraceErrorAnalyzer` | `io.mist.core.analysis.TraceErrorAnalyzer` | LLM-driven trace failure-mode diagnosis |
| `analysis/TraceShapeAdapter` | `io.mist.core.analysis.TraceShapeAdapter` | bridges adapter-side Jaeger JSON to the oracle |
| `configuration/MstConfig` | `io.mist.core.config.MstConfig` | typed config singleton (Fix A-6) |
| `configuration/MstConfigValidator` | `io.mist.core.config.MstConfigValidator` | startup validator |
| `enhancer/FailedTestCollector` | `io.mist.core.enhancer.FailedTestCollector` | JUnit RunListener; junit lifted to compile scope on mist-core |
| `enhancer/FailedTestResult` | `io.mist.core.enhancer.FailedTestResult` | |
| `enhancer/ParameterSnapshot` | `io.mist.core.enhancer.ParameterSnapshot` | |
| `enhancer/TestFileRegenerator` | `io.mist.core.enhancer.TestFileRegenerator` | |
| `enhancer/TestResultCapture` | `io.mist.core.enhancer.TestResultCapture` | |
| `generators/AiDrivenLLMGenerator` | `io.mist.core.generation.AiDrivenLLMGenerator` | |
| `generators/ZeroShotLLMGenerator` | `io.mist.core.generation.ZeroShotLLMGenerator` | okhttp lifted to mist-core compile scope |
| `generators/HardcodedInvalidInputGenerator` | `io.mist.core.generation.HardcodedInvalidInputGenerator` | |
| `inputs/InvalidInputPool` | `io.mist.core.fault.InvalidInputPool` | |
| `inputs/llm/ParameterInfo` | `io.mist.core.llm.ParameterInfo` | |
| `inputs/smart/ApiMapping` | `io.mist.core.smart.ApiMapping` | |
| `inputs/smart/CacheConfig` | `io.mist.core.smart.CacheConfig` | |
| `inputs/smart/InputFetchRegistry` | `io.mist.core.smart.InputFetchRegistry` | |
| `inputs/smart/OpenAPIEndpointDiscovery` | `io.mist.core.smart.OpenAPIEndpointDiscovery` | jackson lifted to mist-core compile scope |
| `inputs/smart/ParameterError` | `io.mist.core.smart.ParameterError` | |
| `inputs/smart/ParameterErrorAnalysisCache` | `io.mist.core.smart.ParameterErrorAnalysisCache` | |
| `inputs/smart/ParameterErrorAnalyzer` | `io.mist.core.smart.ParameterErrorAnalyzer` | |
| `inputs/smart/ServicePattern` | `io.mist.core.smart.ServicePattern` | |
| `inputs/smart/SmartFetchAuthManager` | `io.mist.core.smart.SmartFetchAuthManager` | |
| `inputs/smart/SmartInputFetchConfig` | `io.mist.core.smart.SmartInputFetchConfig` | |
| `registry/ApiTree` | `io.mist.core.registry.ApiTree` | |
| `registry/RootApiEntry` | `io.mist.core.registry.RootApiEntry` | |
| `registry/RootApiRegistry` | `io.mist.core.registry.RootApiRegistry` | |
| `util/SeededRandom` | `io.mist.core.util.SeededRandom` | |
| `util/ConsoleProgressBar` | `io.mist.core.util.ConsoleProgressBar` | adapter still imports from the new location |
| `util/IDGenerator` | `io.mist.core.util.IDGenerator` | adapter wildcards in `AbstractTestCaseGenerator` and `TestGenerationAndExecution` were back-filled with explicit imports |
| `workflow/NounKeyMap` | `io.mist.core.workflow.NounKeyMap` | YAML-driven noun map (S-2) |
| `workflow/WorkflowScenario` | `io.mist.core.workflow.WorkflowScenario` | `addTraceId` / `mergeWith` widened to public for the (still-in-adapter) extractor/optimizer |
| `workflow/WorkflowScenarioUtils` | `io.mist.core.workflow.WorkflowScenarioUtils` | |
| `workflow/WorkflowStep` | `io.mist.core.workflow.WorkflowStep` | |
| `workflow/TraceWorkflowExtractor` | `io.mist.core.workflow.TraceWorkflowExtractor` | reconstructs scenarios from Jaeger traces |
| `inputs/smart/SmartInputFetcher` | `io.mist.core.smart.SmartInputFetcher` | 4450 LOC; moved verbatim once `ConsoleProgressBar` was promoted; doc-link to `SemanticDependencyRegistry` softened to `{@code}` |
| `enhancer/TestCaseEnhancer` | `io.mist.core.enhancer.TestCaseEnhancer` | 409 LOC; only its package declaration tied it to RESTest |
| `generators/ValueProvenanceInference` | `io.mist.core.value.ValueProvenanceInference` | 50 LOC; bundled with the ValueProvenance carrier |
| `coverage/LLMStatusCodeDiscovery` | `io.mist.core.coverage.LLMStatusCodeDiscovery` | LLM-driven; consumer is `StatusCodeExplorationEnhancer` (still in adapter) |
| `coverage/StatusCodeCoverageTracker` | `io.mist.core.coverage.StatusCodeCoverageTracker` | |
| `coverage/StatusCodeTarget` | `io.mist.core.coverage.StatusCodeTarget` | |
| `configuration/multiservice/MstConfig` | `io.mist.core.config.legacy.MstConfig` | Properties-file loader; the `.legacy` sub-package disambiguates from the typed `io.mist.core.config.MstConfig` POJO |
| `workflow/SemanticDependencyRegistry` | `io.mist.core.registry.SemanticDependencyRegistry` | 1510 LOC; `build(...)` signature switched from `Map<String, OpenAPISpecification>` to `Map<String, OpenAPI>` and pojo refs switched to `io.mist.core.spec.*`. Boundary converter `io.mist.adapter.restest.PojoConverter` bridges the three RESTest-side call sites |
| `workflow/ScenarioOptimizer` | `io.mist.core.workflow.ScenarioOptimizer` | unblocked once `SemanticDependencyRegistry` moved; lone consumer is `Phase3ShatteringStage` |
| `MultiServiceTestCaseGenerator#PoolKey` (inner class) | `io.mist.core.fault.PoolKey` | top-level class; extraction unblocked `PipelineContext`'s last dependency on the generator class |

## 4a. Category B — RESTest data classes vendored as verbatim copies (B1.B)

These seven configuration POJOs are now duplicated into
`io.mist.core.spec.*`. The originals remain in
`es.us.isa.restest.configuration.pojos.*` because RESTest's own
configuration loader, the legacy generators (Random/AR/ConstraintBased),
and the writers still use them. Once MIST-only consumers have been moved
into `mist-core`, their imports swap to `io.mist.core.spec.*` and the
adapter loses the corresponding references one class at a time.

| Vendored class (`io.mist.core.spec.*`) | Verbatim source | Notes |
|---|---|---|
| `Auth` | `es.us.isa.restest.configuration.pojos.Auth` | leaf POJO |
| `Generator` | `es.us.isa.restest.configuration.pojos.Generator` | depends on `GenParameter` (same package) |
| `GenParameter` | `es.us.isa.restest.configuration.pojos.GenParameter` | leaf POJO |
| `Operation` | `es.us.isa.restest.configuration.pojos.Operation` | holds `io.swagger.v3.oas.models.Operation` (kept verbatim — swagger-core dep added to mist-core) |
| `TestConfiguration` | `es.us.isa.restest.configuration.pojos.TestConfiguration` | Jackson-annotated for ignore-unknown |
| `TestConfigurationObject` | `es.us.isa.restest.configuration.pojos.TestConfigurationObject` | composite of `Auth` + `TestConfiguration` |
| `TestParameter` | `es.us.isa.restest.configuration.pojos.TestParameter` | leaf-ish; depends on `Generator` |

## 5. What still depends on RESTest types (deferred)

These classes stay in `mist-restest-adapter` because they reference
RESTest types directly (configuration loader internals, OpenAPI
specification visitors, test-case base classes, util helpers) or depend
on others in this group.

| File | RESTest types it consumes | Disposition |
|---|---|---|
| `generators/MultiServiceTestCaseGenerator` | `OpenAPISpecification`, `TestConfigurationObject`, `TestParameter`, `Operation`, `TestCase`, `MultiServiceTestCase` | Wrap (spec) + Vendor (testcase) |
| `workflow/ScenarioOptimizer` | via `SemanticDependencyRegistry` | follows SDR |
| `workflow/SemanticDependencyRegistry` | `OpenAPISpecification`, `TestConfigurationObject`, `Operation`, `TestParameter` | Wrap (spec) |
| `workflow/pipeline/PipelineContext` | `TestConfigurationObject`, `OpenAPISpecification`, `MultiServiceTestCaseGenerator`, `SmartInputFetcher` | needs all of the above |
| `workflow/pipeline/PipelineStage` (interface) | through `PipelineContext` | follows ctx |
| `workflow/pipeline/WorkflowPipeline` | through `PipelineContext` | follows ctx |
| `workflow/pipeline/stages/*` (9 files) | `PipelineContext` + RESTest pojos | follows ctx |
| `inputs/smart/SmartInputFetcher` | none from RESTest after the `ConsoleProgressBar` move; uses adapter-side OpenAPI parser | should be re-checked for a move |
| `inputs/smart/SmartLLMParameterGenerator` | `OpenAPIParameter`, `OpenAPISpecificationVisitor`, `LLMParameterGenerator`, `PropertyManager` | Wrap (spec) |
| `inputs/llm/LLMParameterGenerator` | `ParameterGenerator` (RESTest stateful), `OpenAPIParameter`, `OpenAPISpecificationVisitor` | Wrap (spec + stateful gen) |
| `enhancer/StatusCodeExplorationEnhancer` | `AuthManipulationStrategy`, `LLMStatusCodeDiscovery`, `MultiServiceTestCase` | Vendor (testcase) + decide on auth |
| `enhancer/TestCaseEnhancer` | adapter test-case base | follows testcase |
| `testcases/MultiServiceTestCase` | `TestCase` (RESTest base) | Vendor (after `TestCase`) |
| `testcases/TestCase` | `OpenAPIParameter`, `idlreasonerchoco.Analyzer` | Wrap (spec) + Wrap (IDL) |
| `auth/MstAuthHandler` | RESTest auth glue | Wrap |
| `writers/restassured/MultiServiceRESTAssuredWriter` | RESTest writer base — intentionally stays per prompt § 10 | Stays |

These map onto Phase B1.B (vendor a small slice of RESTest types into
`mist-core`) and Phase B1.E (SPI definition) in the original prompt. The
remaining migration is one focused round per RESTest type group:
configuration pojos (largely done in this branch), OpenAPI specification
view, test-case base, util.

## 6. Gates met by this branch

| Gate (from prompt § 7) | Status |
|---|---|
| 7.1 Gate B1.A — inventory present | ✅ this file |
| 7.2 Gate B1.B — vendored leaf POJOs compile in isolation | ✅ via `mvn -pl mist-core -am compile` |
| 7.4 Gate B1.D — `mist-core/src` has zero `extends AbstractTestCaseGenerator` | ✅ trivially — no MIST class lives in `mist-core` that would extend it |
| 7.4 Gate B1.D — MIST generator no longer extends the RESTest base | ✅ severed |
| 7.4 Gate B1.D — `mvn -q -DskipTests compile` passes for the reactor | ✅ |
| 7.4 Gate B1.D — Seeded demo byte-identical to baseline | ⏳ not run — bundled demo needs the remote TrainTicket cluster at `http://129.62.148.112:32677` |
| **7.5 — `mist-core` has zero `es.us.isa` references** | **✅ verified with `grep -rE 'es\.us\.isa' mist-core/src/main/java`** |
| 7.5 — `mist-core` unit tests pass without RESTest on the classpath | ✅ no RESTest in `mvn -pl mist-core dependency:tree` |
| 7.5 / 7.6 — SPI gates (interface files in `io.mist.core.spi`, `META-INF/services`) | 🟡 partial — `MistSpec`, `MistSpecLoader`, `MistTestWriter<T>`, `MistTestExecutor` interfaces defined; `RestestMistSpecLoader` adapter registered. Writer / executor adapter wrappers deferred until a `mist-core`-resident consumer exists |
| 7.7 — final cleanup | ⏳ deferred (positioning doc citations, README diagram, flow.md class names) |
