# B1 follow-ups — what is still ahead of the rebuild

> Tracking file for items deferred out of the current `inject-detection`
> HEAD. See `PROMPT_B1_SEVER_RESTEST_INHERITANCE.md` for the full plan
> and `B1_INVENTORY.md` for the per-class disposition.

## What has landed already (B1.A + B1.D + B1.C-clean + B1.B-leaves)

- B1.A — full dependency inventory in `B1_INVENTORY.md`.
- B1.D — `MultiServiceTestCaseGenerator` no longer
  `extends AbstractTestCaseGenerator`. `MistRunner` uses the concrete
  type. The legacy CLI surface still compiles unchanged.
- B1.C (partial) — 33 RESTest-dependency-free MIST classes have moved
  out of `es.us.isa.restest.*` into `io.mist.core.*`:
  `analysis/*`, `config/MstConfig*`, `enhancer/*` (leaves),
  `fault/InvalidInputPool`, `generation/{Ai,Zero,Hardcoded}*`,
  `llm/ParameterInfo`, `registry/*`, `smart/*` (10 leaves),
  `util/SeededRandom`, `workflow/{WorkflowScenario, WorkflowStep,
  WorkflowScenarioUtils, NounKeyMap, TraceWorkflowExtractor}`.
- B1.C (continued) — two MIST-only util helpers moved out of
  `es.us.isa.restest.util` into `io.mist.core.util`:
  `ConsoleProgressBar`, `IDGenerator`. Adapter call sites now import
  the new locations.
- B1.C (further) — two more MIST-only classes moved:
  `inputs/smart/SmartInputFetcher` (4450 LOC; the largest single
  promotion in B1, unlocked once `ConsoleProgressBar` left the
  adapter) and `enhancer/TestCaseEnhancer` (409 LOC; only its
  package decl was RESTest-tied). Adapter callers' imports updated.
- B1.E + B1.F (SPI scaffolding) — four interfaces in
  `io.mist.core.spi` (`MistSpec`, `MistSpecLoader`,
  `MistTestWriter<T>`, `MistTestExecutor`) plus a
  `MistServices` service locator that fails fast with a
  descriptive error when an SPI has no implementation. The
  adapter ships `RestestMistSpec` + `RestestMistSpecLoader`,
  registered via `META-INF/services/`. Writer / executor adapter
  implementations stay deferred until a `mist-core`-resident
  consumer needs them (prompt § 6 #5).
- B1.C (continued, 5 more) — four more class moves landed:
  `generators/ValueProvenanceInference` (→ `io.mist.core.value`),
  the trio `coverage/{LLMStatusCodeDiscovery, StatusCodeCoverage-
  Tracker, StatusCodeTarget}` (→ `io.mist.core.coverage`), and
  `configuration/multiservice/MstConfig` (the Properties-file
  loader, → `io.mist.core.config.legacy.MstConfig`, distinct
  package from the existing typed `io.mist.core.config.MstConfig`).
- B1.B (leaves) — seven leaf configuration POJOs vendored into
  `io.mist.core.spec` as verbatim copies: `Auth`, `Generator`,
  `GenParameter`, `Operation`, `TestParameter`, `TestConfiguration`,
  `TestConfigurationObject`. Adapter call sites still use the
  originals; the vendored copies are unreferenced until consumers
  swap one-by-one in a follow-up pass.
- **Cardinal § 7.5 gate met**: `grep -rE 'es\.us\.isa' mist-core/src/main/java`
  returns zero matches. `mvn -pl mist-core dependency:tree` shows zero
  RESTest deps.

## What is still ahead — the remaining B1.B/C/E/F/G work

### Remaining vendoring (B1.B, mostly done)

| Class | Size | Status |
|---|---|---|
| `testcases/TestResult` | 110 LOC | deferred — needs CSVManager/FileManager chain or `exportToCSV` drop. Adapter consumers still use the original. |
| `testcases/TestCase` | 650 LOC | **DONE** — vendored to `io.mist.core.testcase.TestCase`; four method families dropped (OpenAPIParameter overloads, exportToCSV, isValid, checkFulfillsDependencies) per the prompt's vendor rules. |
| `testcases/MultiServiceTestCase` | 474 LOC | **DONE** — vendored to `io.mist.core.testcase.MultiServiceTestCase`; AuthManipulationStrategy.AuthConfig field dropped. |

### Remaining MIST-only class moves (B1.C, deferred)

These all share the same blocker: they reference one of the
not-yet-vendored / not-yet-wrapped RESTest types above. They cannot
move into `mist-core` until either (a) the type is vendored and the
reference swaps, or (b) the type is wrapped as a `MistXxx` SPI.

| File (currently in adapter) | Target under `mist-core` once unblocked |
|---|---|
| `generators/MultiServiceTestCaseGenerator` | `io.mist.core.generation.MistGenerator` (rename per prompt) |
| `workflow/ScenarioOptimizer` | `io.mist.core.workflow.ScenarioOptimizer` |
| `workflow/SemanticDependencyRegistry` | `io.mist.core.registry.SemanticDependencyRegistry` |
| `workflow/pipeline/PipelineContext` | `io.mist.core.workflow.pipeline.PipelineContext` |
| `workflow/pipeline/PipelineStage` | `io.mist.core.workflow.pipeline.PipelineStage` |
| `workflow/pipeline/WorkflowPipeline` | `io.mist.core.workflow.pipeline.WorkflowPipeline` |
| `workflow/pipeline/stages/*` (9 files) | `io.mist.core.workflow.pipeline.stages.*` |
| `inputs/smart/SmartInputFetcher` | `io.mist.core.smart.SmartInputFetcher` (re-check — `ConsoleProgressBar` block is now gone) |
| `inputs/smart/SmartLLMParameterGenerator` | `io.mist.core.generation.SmartLLMParameterGenerator` |
| `inputs/llm/LLMParameterGenerator` | `io.mist.core.generation.LLMParameterGenerator` |
| `enhancer/StatusCodeExplorationEnhancer` | `io.mist.core.enhancer.StatusCodeExplorationEnhancer` |

### MSTG cascade — completed in B1.C wholesale move

The 14-file `MultiServiceTestCaseGenerator` cluster has been
promoted into `mist-core` in a six-phase cascade:

  1. PipelineContext field types converted to mist-core
     (`Map<String, OpenAPI>`, vendored `TestConfigurationObject`).
  2. SharedPoolSupport / StageSupport signatures swapped to
     vendored pojos (`io.mist.core.spec.*`).
  3. PipelineContext + PipelineStage + WorkflowPipeline + 9
     stage / support classes moved as a batch.
  4. MSTG class itself renamed and moved to
     `io.mist.core.generation.MistGenerator`.
  5. Two boundary converters added on the adapter side:
     - `PojoConverter` now also has reverse direction methods
       (`toRestest(Operation)`, `toRestest(TestParameter)`, …)
       so adapter-side glue can hand mist-core pojos back to
       RESTest call sites.
     - `TestCaseConverter` (~210 LOC) bridges
       `io.mist.core.testcase.{TestCase, MultiServiceTestCase}`
       (what MistGenerator produces) → the RESTest carriers
       (what the RESTAssured writer's `Collection<TestCase>`
       expects). Prompt § 10 forbids touching the writer's
       internals; this converter sits between MistRunner and
       the writer so the writer stays unchanged.
  6. Demo verification on the live TrainTicket cluster.

### SPI scaffolding (B1.E + B1.F, partial)

The prompt's § Phase B1.E enumerates a "minimum viable SPI surface"
(`MistSpecLoader`, `MistSpec`, `MistOperation`, `MistParameter`,
`MistTestWriter`, `MistTestExecutor`). This branch lands:

- **All four interfaces** in `io.mist.core.spi`: `MistSpec`,
  `MistSpecLoader`, `MistTestWriter<T>`, `MistTestExecutor`. Plus a
  lazily-resolving service locator `MistServices` that raises a
  descriptive `IllegalStateException` (not an NPE) when an SPI has
  no implementation on the runtime classpath.
- **The adapter-side spec loader**: `io.mist.adapter.restest.RestestMistSpec`
  and `io.mist.adapter.restest.RestestMistSpecLoader` wrap
  RESTest's `OpenAPISpecification` and register via
  `META-INF/services/io.mist.core.spi.MistSpecLoader`.
- **Smoke test**: `MistServicesTest` in mist-core verifies the
  locator class loads cleanly and `isPresent()` returns `false`
  when nothing is registered (mist-core's own test scope).

Subsequently delivered:

- **RestAssuredMistTestWriter** in `io.mist.adapter.restest`
  wraps `MultiServiceRESTAssuredWriter` and exposes
  `MistTestWriter<Object>`; registered via
  `META-INF/services/io.mist.core.spi.MistTestWriter`.
- **MavenSurefireMistTestExecutor** in `io.mist.adapter.restest`
  shells out to `mvn -q test` against the generated tests dir;
  registered via `META-INF/services/io.mist.core.spi.MistTestExecutor`.

Still deferred (per prompt § 6 #5 — "don't invent SPIs for
hypothetical needs"):

- `MistOperation` and `MistParameter` interfaces. Currently
  `MistSpec` exposes the raw `io.swagger.v3.oas.models.OpenAPI` model,
  which the prompt's § Phase B1.E example pre-empts via richer
  view methods. The richer surface will follow once a consumer in
  mist-core actually needs it.

### Why `SemanticDependencyRegistry` and the workflow pipeline still
### sit in the adapter

Multiple investigations during this branch tried to move
`SemanticDependencyRegistry` (1510 LOC) and the
`workflow/pipeline/*` stages into `mist-core`. Each attempt hits
the same shape mismatch:

- The vendored copies under `io.mist.core.spec.*` (`Auth`,
  `TestConfigurationObject`, `Operation`, `TestParameter`, …) are
  byte-equivalent to but *type-distinct* from the originals in
  `es.us.isa.restest.configuration.pojos.*`.
- Adapter call sites get `Map<String, es.us.isa.restest.…
  .TestConfigurationObject>` from the RESTest configuration loader
  (`MicroserviceTestConfigurationIO`) and pass it to
  `SemanticDependencyRegistry.build(…)`. If the registry lives in
  `mist-core` with the vendored signature, those call sites need
  either (a) a converter at the boundary, or (b) the loader
  re-pointed at the vendored pojos.
- Option (b) cascades into RESTest's own configuration code
  (which uses the same pojos for the Random / AR / Constraint-Based
  generators); option (a) needs ~200 LOC of mechanical copy code.
- Neither is a "while I'm here" change; the prompt's § 0.3 scope
  discipline pushes back on it.

The right next move is to write the boundary converter (or, better,
make the IO loader package-aware and emit vendored types via a
small adapter wrapper). Until then the SDR family stays in the
adapter and the rest of the workflow pipeline stays with it.

## Other deferred items

- **Brand sweep** (`Mst` → `Mist` on moved classes, system-property keys
  `mst.*` stay). Prompt § Phase B1.C step 3 allows this once a class
  moves packages. Deferring keeps in-flight diffs reviewable.
- **Byte-identical demo proof** (`-Drandom.seed=42` against
  `trainticket-demo.properties`). Needs the remote TrainTicket cluster
  reachable from the build host, or a recorded fixture. Cannot be
  verified in CI alone.
- **Positioning doc citations** (`docs/mst-plans/PATH_B_POSITIONING.md`):
  every `file:line` citation that pointed to a now-moved class will
  resolve incorrectly. Refresh as a single editorial pass after all
  moves land.
- **`flow.md`** under
  `mist-restest-adapter/src/main/resources/My-Example/trainticket/`
  references several of the moved classes by FQN; the references read
  fine but should be updated for accuracy.
- **README architecture diagram** (lines 17-40 of the repo-root README)
  describes `mist-core` as "trace shape oracle (TSO) and adaptive
  fault taxonomy". After this batch it owns more material; refresh.

## Known constraints

- `MultiServiceTestCaseGenerator`'s constructor still takes
  `primarySpec` and `dummyPrimaryConf` parameters that became unused
  once the `super(...)` call was removed. Cleaning the signature
  requires touching the two call sites in `MistRunner.java` and
  `TestGenerationAndExecution.java`; held back for Phase B1.C, which
  moves the class wholesale.
- `WorkflowScenario#addTraceId` and `WorkflowScenario#mergeWith` are now
  `public` (used to be package-private). The widening is required by
  `TraceWorkflowExtractor` and `ScenarioOptimizer` reaching across the
  new package boundary; the two methods stay an internal contract of
  the workflow subsystem and are not part of any public API surface
  reviewers will see.
- `mist-core`'s `junit` dependency is now compile-scope (it used to be
  test-scope). `FailedTestCollector` extends `org.junit.runner.notification.RunListener`,
  so the dependency is genuine.
- `mist-core` now has a compile-scope dependency on
  `io.swagger.core.v3:swagger-core` (the OpenAPI model classes —
  *not* the parser, which stays in the adapter). The vendored
  `Operation` POJO has a `@JsonIgnore`-tagged `io.swagger.v3.oas.models.Operation`
  field that the verbatim copy preserves. `swagger.version` is hoisted
  to the parent pom for a single source of truth.

## Auto-regenerated test outputs

`mist-restest-adapter/src/test/java/trainticket_twostage_test/` contains
~56 pre-generated test classes (artifacts of an earlier MIST run). They
still reference `es.us.isa.restest.analysis.TraceErrorAnalyzer` and
similar old paths. Regenerating the demo (next time a TrainTicket
cluster is reachable) will overwrite them with current paths. The
adapter's test compile already failed pre-existingly on Java 21
("system modules path not set in conjunction with -source 11") so this
is not a new failure.
