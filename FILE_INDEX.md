# MIST — File Index

**Single source of truth for what every file in this repository is and where it lives.**

This index lists **all 643 tracked files** with their full repo-relative path and a one-line description, grouped by directory. It exists so you can locate things and understand the codebase *without* crawling the tree.

> ⚠️ **Maintenance contract — read this.**
> This file must stay in sync with the repository. Whenever you **create, delete, move/rename, or materially change the purpose of** a file, update its row here *in the same change*:
> - **Create** → add a row (full path + one-line description) under the correct `###` directory header.
> - **Delete** → remove its row.
> - **Move/rename** → update the path and move the row to the right section.
> - **Repurpose** → update the description.
>
> There must be **exactly one** index file in the project — this one. Never fork a second. (See the project memory entries `file-index-consult-first` and `file-index-maintenance`.)

## How to use this index

- **Find a file / its purpose:** grep or Ctrl-F the filename or a keyword. Every row is self-contained — the full path and its description are on the same line, so a single match tells you both *where* it is and *what* it does.
- **Browse an area:** use the Table of Contents below, then the `###` directory sub-headers within each section.
- **Paths** are repo-relative from the repository root.

_Generated 2026-06-30. Tracked files: 643._

## Contents

| Section | Files |
|---------|-------|
| [Repository root & CI / config](#repository-root--ci--config) | 11 |
| [mist-core — engine, oracles, generation, faults](#mist-core--engine-oracles-generation-faults) | 182 |
| [mist-cli & mist-llm](#mist-cli--mist-llm) | 55 |
| [evaluation — SUTs & harness](#evaluation--suts--harness) | 58 |
| [debug — notes, inputs & measurements](#debug--notes-inputs--measurements) | 248 |
| [docs — design & evidence](#docs--design--evidence) | 66 |
| [paper — LaTeX sources](#paper--latex-sources) | 23 |
| **Total** | **643** |

## Repository root & CI / config

Top-level entrypoint docs, build (`pom.xml`), license, env templates, CI, and IDE run configurations.

### Repository root

| Path | Description |
|------|-------------|
| `.env.example` | Template for the `.env` file holding OPENAI_API_KEY and GEMINI_API_KEY; copy and fill in real keys. |
| `.gitattributes` | Forces LF line endings on *.sh and *.bash so SUT deploy-script shebangs work on Linux and macOS. |
| `.gitignore` | Git ignore rules covering IDE state, Maven target/logs, .mist runtime state, generated artifacts, traces, and API-key files. |
| `LICENSE` | Full text of the GNU Lesser General Public License v3.0. |
| `README.md` | Main project README: MIST overview, three-module architecture, inputs, quick-start paths, LLM backends, outputs, and layout. |
| `REPRODUCE.md` | Artifact and reproduction guide for reviewers: SUT requirements, offline oracle smoke run, and claim-to-evidence map. |
| `deepseek-config.properties` | Template LLM config for the DeepSeek OpenAI-compatible chat endpoint with retry and communication-logging settings. |
| `pom.xml` | Maven reactor parent POM (es.us.isa:mist-parent) declaring the mist-core, mist-llm, mist-cli modules and shared dependency versions. |

### `.circleci/`

| Path | Description |
|------|-------------|
| `.circleci/config.yml` | CircleCI 2.1 pipeline building on cimg/openjdk:11 that runs `mvn verify` plus a SonarQube scan with a Maven cache. |

### `.idea/runConfigurations/`

| Path | Description |
|------|-------------|
| `.idea/runConfigurations/MIST__Demo__Bundled_TrainTicket_.xml` | IntelliJ run config launching io.mist.cli.MistMain against the bundled TrainTicket demo properties (seed 42, CWD repo root). |
| `.idea/runConfigurations/MIST__Demo__no_exec_smoke_.xml` | IntelliJ run config for a generation-only no-exec smoke run using the trainticket-demo-noexec profile (no SUT/LLM needed). |

## mist-core — engine, oracles, generation, faults

The core Maven module: OpenAPI spec model, test/input generation, oracle (shape/attribution/invariant) checks, fault taxonomy, smart input fetching, workflow pipeline, plus utilities, registries, config, default resources, and unit tests.

### `mist-core/`

| Path | Description |
|------|-------------|
| `mist-core/pom.xml` | Maven POM for the mist-core module: the standalone trace-shape-oracle and adaptive-fault-taxonomy library plus its gson/jackson/swagger/okhttp deps. |

### `mist-core/src/main/java/io/mist/core/analysis/`

| Path | Description |
|------|-------------|
| `mist-core/src/main/java/io/mist/core/analysis/FaultDetectionTracker.java` | Thread-safe singleton tracking injected-fault detection and TraceShapeOracle anomalies during execution; generates coverage/anomaly reports and console summaries. |
| `mist-core/src/main/java/io/mist/core/analysis/IntelligentAnalysisCache.java` | Per-failure-signature JSON-persisted cache of TraceErrorAnalyzer LLM diagnoses so traces failing the same way reuse one diagnosis instead of re-calling the LLM. |
| `mist-core/src/main/java/io/mist/core/analysis/TraceErrorAnalyzer.java` | Analyzes Jaeger traces to find failed spans and root causes, builds error reports, and produces LLM-backed (with fallback) intelligent diagnoses. |
| `mist-core/src/main/java/io/mist/core/analysis/TraceShapeAdapter.java` | Converts writer-side Jaeger trace JSON into a TraceModel for the Trace Shape Oracle, with a per-traceId cache. |

### `mist-core/src/main/java/io/mist/core/auth/`

| Path | Description |
|------|-------------|
| `mist-core/src/main/java/io/mist/core/auth/AuthManipulationStrategy.java` | Strategies to manipulate auth (remove/invalid/expired token, wrong/guest user, scope) to trigger 401/403 status codes for coverage exploration. |

### `mist-core/src/main/java/io/mist/core/bandit/`

| Path | Description |
|------|-------------|
| `mist-core/src/main/java/io/mist/core/bandit/ThompsonScheduler.java` | Thompson-sampling scheduler over per-key Beta(alpha,beta) posteriors; ranks fault-target queues to favour high-value arms while preserving exploration. |

### `mist-core/src/main/java/io/mist/core/config/`

| Path | Description |
|------|-------------|
| `mist-core/src/main/java/io/mist/core/config/AblationProfile.java` | Snapshot of the seven ablation toggles (oracle, four invariants, bandit, fault-mining) from MstConfig; renders a grep-friendly run-banner summary. |
| `mist-core/src/main/java/io/mist/core/config/CacheToggle.java` | Master read/write switches (mst.cache.read / mst.cache.write) shared by MIST's signature-based LLM caches. |
| `mist-core/src/main/java/io/mist/core/config/MstConfig.java` | Immutable typed POJO materializing the ~30 MIST/MST system-property keys into documented sub-groups; lazily-built JVM singleton with validation. |
| `mist-core/src/main/java/io/mist/core/config/MstConfigValidator.java` | Validation companion for MstConfig: unknown-key typo scan, numeric range checks, and documented-conflict warnings; strict mode aborts on unknown keys. |

### `mist-core/src/main/java/io/mist/core/config/legacy/`

| Path | Description |
|------|-------------|
| `mist-core/src/main/java/io/mist/core/config/legacy/MstConfig.java` | Loader for the MST-only java.util.Properties file; pushes loaded keys into System properties so downstream getProperty consumers keep working. |

### `mist-core/src/main/java/io/mist/core/coverage/`

| Path | Description |
|------|-------------|
| `mist-core/src/main/java/io/mist/core/coverage/LLMStatusCodeDiscovery.java` | Uses an LLM to discover all possible HTTP status codes for an API operation plus trigger strategies; signature-keyed, disk-persisted cache. |
| `mist-core/src/main/java/io/mist/core/coverage/StatusCodeCoverageTracker.java` | Tracks per-API status-code coverage across rounds: discovered vs triggered vs targeted codes, round-robin fairness, and coverage summaries. |
| `mist-core/src/main/java/io/mist/core/coverage/StatusCodeTarget.java` | Dynamic (non-enum) carrier for an LLM-discovered status-code target with category, trigger strategy, auth-manipulation flag, and suggested inputs; Builder plus JSON. |

### `mist-core/src/main/java/io/mist/core/enhancer/`

| Path | Description |
|------|-------------|
| `mist-core/src/main/java/io/mist/core/enhancer/EnhancementCache.java` | On-disk JSON singleton cache of TestCaseEnhancer LLM results keyed by scenario-fault fingerprint, surviving JVM restarts with debounced atomic flush. |
| `mist-core/src/main/java/io/mist/core/enhancer/FailedTestCollector.java` | JUnit RunListener that drains TestResultCapture into a list of enhanceable FailedTestResults, skipping 5xx and bypass-triggered failures, and persists them per round. |
| `mist-core/src/main/java/io/mist/core/enhancer/FailedTestResult.java` | Builder-style data model capturing full context of a failed test (endpoint, params, status, locked deps) for sending to the LLM enhancer. |
| `mist-core/src/main/java/io/mist/core/enhancer/ParameterSnapshot.java` | Builder-style POJO snapshotting one parameter's state (name, value, type, location, schema hints) at test-execution time for enhancer context. |
| `mist-core/src/main/java/io/mist/core/enhancer/StatusCodeExplorationEnhancer.java` | Enhancer that creates new test cases targeting untriggered HTTP status codes via LLM discovery and coverage tracking, running after the first round. |
| `mist-core/src/main/java/io/mist/core/enhancer/TestCaseEnhancer.java` | Core enhancer that sends failed-test context to the LLM and parses suggested improved parameter values, with lenient JSON parsing and a concurrent result cache. |
| `mist-core/src/main/java/io/mist/core/enhancer/TestFileRegenerator.java` | Parses generated Java test files and rewrites parameter assignments with LLM-suggested values, scoping replacements to the failed step's code block. |
| `mist-core/src/main/java/io/mist/core/enhancer/TestResultCapture.java` | Thread-safe static store using ThreadLocal that generated tests call to record response data and per-value success/reject observations for the enhancer. |

### `mist-core/src/main/java/io/mist/core/fault/`

| Path | Description |
|------|-------------|
| `mist-core/src/main/java/io/mist/core/fault/ApplicabilityMatrix.java` | Read-side wrapper turning the registry's applicableFor logic into an applies(FaultType, oasType, location) predicate, preferring registry overrides. |
| `mist-core/src/main/java/io/mist/core/fault/FaultMiner.java` | LLM-assisted miner (gated by mist.fault.mining.enabled) proposing SUT-specific fault categories from OpenAPI params and observed 4xx/5xx, appended to a YAML overlay. |
| `mist-core/src/main/java/io/mist/core/fault/FaultType.java` | Identity-driven value object for a fault category (id, displayName, applicableTo/Locations, DEFAULT/MINED source); replaces the retired InvalidInputType enum. |
| `mist-core/src/main/java/io/mist/core/fault/FaultTypeRegistry.java` | Registry of FaultType entries from the bundled default YAML with optional per-SUT overlay; applicability queries and OAS-type normalization. |
| `mist-core/src/main/java/io/mist/core/fault/InvalidInputPool.java` | Per-parameter pool of invalid values keyed by fault-type id, with priority-ordered round-robin and random selection for negative testing. |
| `mist-core/src/main/java/io/mist/core/fault/MistInvalidInputPool.java` | Forward-compatible parallel invalid-input pool keyed by FaultType.id with caller-supplied rotation order; scaffolding not yet wired into the generator. |
| `mist-core/src/main/java/io/mist/core/fault/ObservedResponse.java` | Minimal POJO for one observed 4xx/5xx response (apiKey, statusCode, body) that FaultMiner mines for SUT-specific fault categories. |
| `mist-core/src/main/java/io/mist/core/fault/PoolKey.java` | Composite (paramName, normalised paramLocation) key so same-named parameters at different locations get distinct fault-pool entries. |
| `mist-core/src/main/java/io/mist/core/fault/SpecRef.java` | Minimal POJO holding an apiKey plus OpenAPI parameter descriptions that FaultMiner reads to propose SUT-specific fault types. |

### `mist-core/src/main/java/io/mist/core/generation/`

| Path | Description |
|------|-------------|
| `mist-core/src/main/java/io/mist/core/generation/AiDrivenLLMGenerator.java` | Thin facade delegating parameter-value and invalid-input-pool generation to ZeroShotLLMGenerator. |
| `mist-core/src/main/java/io/mist/core/generation/HardcodedInvalidInputGenerator.java` | Deterministic no-LLM generator of invalid-input pools across the nine fault types, schema-aware (bounds/enum/CSV) per parameter. |
| `mist-core/src/main/java/io/mist/core/generation/MistGenerator.java` | Core MIST test-case generator: builds positive/negative scenario variants with Sniper fault injection, Thompson-ranked fault queue, dedup, and per-endpoint policy. |
| `mist-core/src/main/java/io/mist/core/generation/ZeroShotLLMGenerator.java` | Zero-shot LLM generator of realistic parameter values and invalid-input pools, with okhttp LLM calls, signature-keyed validation cache, and seeded fallback. |

### `mist-core/src/main/java/io/mist/core/health/`

| Path | Description |
|------|-------------|
| `mist-core/src/main/java/io/mist/core/health/SutHealthCheck.java` | Black-box preflight probing each Root API (optionally auth-aware) before scenario discovery so a broken SUT shows at second 0 instead of hours in. |

### `mist-core/src/main/java/io/mist/core/llm/`

| Path | Description |
|------|-------------|
| `mist-core/src/main/java/io/mist/core/llm/ParameterInfo.java` | Mutable POJO describing one API parameter (name, type, location, format, regex, enum, bounds, length, plus API/service context) for input generators. |

### `mist-core/src/main/java/io/mist/core/multiservice/`

| Path | Description |
|------|-------------|
| `mist-core/src/main/java/io/mist/core/multiservice/GenParam.java` | POJO for a single value-generator parameter (name plus one or more string values). |
| `mist-core/src/main/java/io/mist/core/multiservice/MicroserviceTestConfigurationGenerator.java` | Generates a per-service multi-service test configuration YAML from an OpenAPI spec, mapping each operation to a service (RESTest-free reimplementation). |
| `mist-core/src/main/java/io/mist/core/multiservice/MicroserviceTestConfigurationIO.java` | Loads a multi-service configuration YAML into a map of service name to TestConfigurationObject, including global auth. |
| `mist-core/src/main/java/io/mist/core/multiservice/MultiServiceTestConfiguration.java` | POJO mapping service names to their lists of OperationConfig entries. |
| `mist-core/src/main/java/io/mist/core/multiservice/OperationConfig.java` | POJO configuring one API operation (path, operationId, method, test parameters, expected response). |
| `mist-core/src/main/java/io/mist/core/multiservice/TestParameter.java` | Parameter config POJO for multi-service operations carrying schema metadata (type, format, enum, min/max, generators). |
| `mist-core/src/main/java/io/mist/core/multiservice/ValueGenerator.java` | POJO describing a test-data generator type and its GenParam configuration. |

### `mist-core/src/main/java/io/mist/core/oracle/attribution/`

| Path | Description |
|------|-------------|
| `mist-core/src/main/java/io/mist/core/oracle/attribution/AttributionVerdict.java` | Enum of Phase 2 trace-attribution outcomes (NO_ATTRIBUTION, UPSTREAM_REJECTION, WRONG_PARAM_REJECTION, TARGET_REJECTION) in increasing confidence. |
| `mist-core/src/main/java/io/mist/core/oracle/attribution/LeafErrorSpanFinder.java` | Walks a TraceModel to find the deepest error span on an unbroken error chain from any root (Jha CLOUD'22 simplified algorithm). |
| `mist-core/src/main/java/io/mist/core/oracle/attribution/MethodToParamMapper.java` | Maps a leaf-error span's operation name to candidate parameter names via token-overlap naming heuristic to decide param responsibility. |
| `mist-core/src/main/java/io/mist/core/oracle/attribution/TraceAttribution.java` | Phase 2 entry point deciding whether a negative test's SUT rejection landed on the target service/parameter or off-target, returning an AttributionVerdict. |

### `mist-core/src/main/java/io/mist/core/oracle/shape/`

| Path | Description |
|------|-------------|
| `mist-core/src/main/java/io/mist/core/oracle/shape/ShapeInvariant.java` | Interface for a learned trace-shape invariant with a learner side producing data record T and a runtime evaluate against a TraceModel. |
| `mist-core/src/main/java/io/mist/core/oracle/shape/ShapeInvariantStore.java` | File-backed JSON store of learned invariant data keyed by (kind, root API), with atomic temp-file rename on write. |
| `mist-core/src/main/java/io/mist/core/oracle/shape/TraceModel.java` | Minimal POJO view of a Jaeger/OpenTelemetry trace (spans, roots, span index) parsed from array, wrapper-object, or JSONL formats. |
| `mist-core/src/main/java/io/mist/core/oracle/shape/TraceShapeLearner.java` | Learner that walks a seed corpus of known-good traces, partitions by root-API key, runs each invariant's learn side, and persists records to the store. |
| `mist-core/src/main/java/io/mist/core/oracle/shape/TraceShapeOracle.java` | Top-level runtime evaluator running the enabled shape invariants for a root API against a trace, gated per-invariant by MstConfig.Oracle flags. |
| `mist-core/src/main/java/io/mist/core/oracle/shape/TraceShapeVerdict.java` | Aggregate verdict for one trace combining per-invariant outcomes; passed is the AND of ERROR-severity boolean outcomes. |

### `mist-core/src/main/java/io/mist/core/oracle/shape/invariant/`

| Path | Description |
|------|-------------|
| `mist-core/src/main/java/io/mist/core/oracle/shape/invariant/HiddenDownstreamFailureInvariant.java` | Label-free runtime invariant flagging a 2xx client response that swallowed a deeper server-error span, observable only in the trace. |
| `mist-core/src/main/java/io/mist/core/oracle/shape/invariant/ResponseEnvelopeInvariant.java` | Learns per-root-API success-envelope primaryField values from 2xx traces and flags 2xx responses carrying a failure value, with optional classifier-on-unknown. |
| `mist-core/src/main/java/io/mist/core/oracle/shape/invariant/SpanTreeShapeInvariant.java` | Learns per-root-API frequent parent-child service edges and max fan-out, flagging unknown edges, missing required edges, and excessive fan-out. |
| `mist-core/src/main/java/io/mist/core/oracle/shape/invariant/StatusPropagationInvariant.java` | Learns per-tree-depth sets of known-good HTTP and OTEL status codes and flags spans carrying a code outside the known set for their depth. |
| `mist-core/src/main/java/io/mist/core/oracle/shape/invariant/TargetAttributionInvariant.java` | Adapts TraceAttribution into the ShapeInvariant family (no learned data) so negative-test attribution verdicts join the gated oracle report at INFO severity. |
| `mist-core/src/main/java/io/mist/core/oracle/shape/invariant/TimingEnvelopeInvariant.java` | Learns per-root-API duration percentiles and a per-span p99, flagging traces or spans exceeding the learned envelope at WARN severity. |

### `mist-core/src/main/java/io/mist/core/policy/`

| Path | Description |
|------|-------------|
| `mist-core/src/main/java/io/mist/core/policy/EndpointPolicy.java` | Per-endpoint testing strategy: dedup mode, dedup/zero-step thresholds, and variant budget; LEGACY constant reproduces historical fixed values. |
| `mist-core/src/main/java/io/mist/core/policy/EndpointPolicyResolver.java` | Resolves an EndpointPolicy from HTTP-method semantics plus OpenAPI x-mist-* hints (dedup mode, thresholds, POST/PATCH variant budget). |

### `mist-core/src/main/java/io/mist/core/registry/`

| Path | Description |
|------|-------------|
| `mist-core/src/main/java/io/mist/core/registry/ApiTree.java` | Structural tree of a root API's microservice call pattern (TreeNode method/path hierarchy) built from a WorkflowStep trace; JSON (de)serialization. |
| `mist-core/src/main/java/io/mist/core/registry/RootApiEntry.java` | One root-API registry entry (method, path, service) holding multiple execution-pattern ApiTrees with subset/duplicate detection on add. |
| `mist-core/src/main/java/io/mist/core/registry/RootApiRegistry.java` | Registry of unique Root API endpoints and their interaction trees, persisted to JSON; merges scenarios without duplicating root APIs. |
| `mist-core/src/main/java/io/mist/core/registry/SemanticDependencyRegistry.java` | Builds an immutable dictionary of semantic parameter dependencies (producer/consumer ID bindings) across APIs from configs, OpenAPI specs, and traces. |

### `mist-core/src/main/java/io/mist/core/smart/`

| Path | Description |
|------|-------------|
| `mist-core/src/main/java/io/mist/core/smart/ApiMapping.java` | POJO mapping a parameter to a source API (endpoint, extract path, priority, consumer scope) that can supply realistic values. |
| `mist-core/src/main/java/io/mist/core/smart/CacheConfig.java` | Config POJO for the fetched-value cache (enabled, max entries, TTL seconds). |
| `mist-core/src/main/java/io/mist/core/smart/InputFetchRegistry.java` | Registry persisting parameter-to-API mappings, service patterns, parameter errors, and per-value pool status, with YAML load/save and scoped lookup. |
| `mist-core/src/main/java/io/mist/core/smart/OpenAPIEndpointDiscovery.java` | Parses OpenAPI spec files to build a service-to-endpoint map used for smart input fetching. |
| `mist-core/src/main/java/io/mist/core/smart/ParameterError.java` | POJO recording an error tied to a specific parameter value during execution (type, reason, endpoint, parameter, timestamp). |
| `mist-core/src/main/java/io/mist/core/smart/ParameterErrorAnalysisCache.java` | Per-signature JSON-persisted cache of ParameterErrorAnalyzer LLM verdicts (both positive and negative) keyed by service/operation/status/exception. |
| `mist-core/src/main/java/io/mist/core/smart/ParameterErrorAnalyzer.java` | Analyzes trace errors to identify which parameter caused a failure and why, using deterministic ref-chain matching plus LLM assistance. |
| `mist-core/src/main/java/io/mist/core/smart/ServicePattern.java` | POJO defining a regex pattern over parameter names mapped to candidate services and endpoints for pattern-based API discovery. |
| `mist-core/src/main/java/io/mist/core/smart/SmartFetchAuthManager.java` | Manages login and JWT token lifecycle (configurable login path, fields, token JSON path, expiry) for authenticated smart-fetch calls. |
| `mist-core/src/main/java/io/mist/core/smart/SmartInputFetchConfig.java` | Config POJO holding all smart-input-fetch settings (percentages, registry/spec paths, timeouts, LLM toggles, prompt char cap). |
| `mist-core/src/main/java/io/mist/core/smart/SmartInputFetcher.java` | Smart input fetching service that pulls realistic test data from existing APIs (via HttpURLConnection and LLM extraction) instead of random values. |

### `mist-core/src/main/java/io/mist/core/spec/`

| Path | Description |
|------|-------------|
| `mist-core/src/main/java/io/mist/core/spec/Auth.java` | POJO holding auth config (required flag, query/header params, and file paths for API keys, headers, OAuth). |
| `mist-core/src/main/java/io/mist/core/spec/GenParameter.java` | POJO for a generator parameter (name with string values and object values) in the test-config spec model. |
| `mist-core/src/main/java/io/mist/core/spec/Generator.java` | POJO describing a test-data generator (type, gen parameters, valid flag) in the test-config spec model. |
| `mist-core/src/main/java/io/mist/core/spec/OpenAPIOperation.java` | Lightweight wrapper over a swagger-core Operation exposing path, method, operationId, tags, servers, parameters, and extensions. |
| `mist-core/src/main/java/io/mist/core/spec/OpenAPIParameter.java` | Wrapper representing an OAS parameter with extracted schema features (type, format, pattern, enum, min/max, length, example). |
| `mist-core/src/main/java/io/mist/core/spec/OpenAPISpecification.java` | Reads an OpenAPI v3 spec file (JSON/YAML) with full ref resolution via swagger parser and exposes the parsed OpenAPI model and operations. |
| `mist-core/src/main/java/io/mist/core/spec/OpenAPISpecificationVisitor.java` | Static utility for inspecting a swagger Operation: finding parameter features and resolving body/media-type schemas. |
| `mist-core/src/main/java/io/mist/core/spec/Operation.java` | Test-config POJO for one operation (test path, operationId, method, test parameters, expected response) plus the linked swagger Operation. |
| `mist-core/src/main/java/io/mist/core/spec/TestConfiguration.java` | Test-config POJO holding a services map of operation-config maps and an optional flat operations list. |
| `mist-core/src/main/java/io/mist/core/spec/TestConfigurationObject.java` | Root test-config POJO pairing an Auth block with a TestConfiguration. |
| `mist-core/src/main/java/io/mist/core/spec/TestParameter.java` | Test-config parameter POJO (name, in, weight, generators) with schema metadata fields (type, format, pattern, enum, min/max, length, example). |

### `mist-core/src/main/java/io/mist/core/spi/`

| Path | Description |
|------|-------------|
| `mist-core/src/main/java/io/mist/core/spi/MistServices.java` | Lazily-resolved ServiceLoader locator for the MIST SPIs (spec loader, test writer, test executor); throws a clear error when an impl is missing. |
| `mist-core/src/main/java/io/mist/core/spi/MistSpec.java` | Read-only interface view of a parsed OpenAPI v3 spec (underlying model, source location, title) consumed by the MIST generation pipeline. |
| `mist-core/src/main/java/io/mist/core/spi/MistSpecLoader.java` | SPI factory interface that parses an OpenAPI v3 spec at a location into a MistSpec; resolved via ServiceLoader. |
| `mist-core/src/main/java/io/mist/core/spi/MistTestExecutor.java` | SPI interface for executing previously-written MIST test sources under a directory, returning the failed-assertion count. |
| `mist-core/src/main/java/io/mist/core/spi/MistTestWriter.java` | SPI interface for serialising MIST-generated test cases into a target framework's source files under an output directory. |

### `mist-core/src/main/java/io/mist/core/testcase/`

| Path | Description |
|------|-------------|
| `mist-core/src/main/java/io/mist/core/testcase/MultiServiceTestCase.java` | Multi-service workflow test-case carrier extending TestCase with per-step (StepCall) structure, scenario name, and fault-injection metadata. |
| `mist-core/src/main/java/io/mist/core/testcase/TestCase.java` | Domain-independent HTTP test-case data carrier (id, faulty flag, method, path, params, expected response); RESTest-free vendored model. |

### `mist-core/src/main/java/io/mist/core/tools/`

| Path | Description |
|------|-------------|
| `mist-core/src/main/java/io/mist/core/tools/JsonToleranceVerifier.java` | Standalone main verifying the enhancer's Jackson mapper tolerates LLM-emitted malformed JSON (comments, trailing commas, single quotes, unquoted keys). |
| `mist-core/src/main/java/io/mist/core/tools/RegenEscapeVerifier.java` | Standalone main verifying TestFileRegenerator emits correct single-escape JSON-in-Java strings (not double-escaped) and that output compiles via javac. |

### `mist-core/src/main/java/io/mist/core/util/`

| Path | Description |
|------|-------------|
| `mist-core/src/main/java/io/mist/core/util/AdapterLLMCommunicationSink.java` | ServiceLoader-registered LLMCommunicationSink bridging mist-llm events to the legacy LLMCommunicationLogger singleton. |
| `mist-core/src/main/java/io/mist/core/util/ApiClient.java` | OkHttp-based client mapping service names to base URLs and issuing HTTP calls, returning ApiResponse; used by workflow test-case execution. |
| `mist-core/src/main/java/io/mist/core/util/ApiResponse.java` | Immutable holder for an HTTP response: status code, body, and trace-propagation id. |
| `mist-core/src/main/java/io/mist/core/util/ConsoleDedupFilter.java` | Log4j2 filter suppressing byte-identical console messages within a TTL so retry/validator spam is deduped while the file appender keeps all events. |
| `mist-core/src/main/java/io/mist/core/util/ConsoleProgressBar.java` | Single-line in-place console progress bar for the MIST pipeline with a nested phase stack; writes via a raw FileDescriptor stream. |
| `mist-core/src/main/java/io/mist/core/util/FileManager.java` | Minimal file utility helpers (exists check, recursive create/delete directory) lifted from the deleted RESTest adapter util. |
| `mist-core/src/main/java/io/mist/core/util/IDGenerator.java` | Static ID helpers: short random id, and a time/seed-based id honouring -Drandom.seed for reproducible generated test names. |
| `mist-core/src/main/java/io/mist/core/util/InjectedFaultConverter.java` | Parses injected faults (fault name plus API endpoint) from an INJECTED_FAULTS.md markdown file and converts them to JSON. |
| `mist-core/src/main/java/io/mist/core/util/LLMCommunicationLogger.java` | Singleton logging all LLM requests/responses with timestamps, response times, and optional resource monitoring to timestamped log files. |
| `mist-core/src/main/java/io/mist/core/util/LoggerStream.java` | OutputStream adapter piping writes to a log4j Logger and optionally mirroring to a backing stream; mirror-off avoids double console output. |
| `mist-core/src/main/java/io/mist/core/util/PropertyManager.java` | Loads global (classpath/CWD config.properties) and per-user properties and exposes typed property lookups; vendored RESTest util. |
| `mist-core/src/main/java/io/mist/core/util/RESTestException.java` | Checked Exception subclass used as MIST's general-purpose error type (vendored from RESTest). |
| `mist-core/src/main/java/io/mist/core/util/SeededRandom.java` | Factory for Random instances honouring -Drandom.seed, with per-scope stream separation for reproducible pipeline runs. |
| `mist-core/src/main/java/io/mist/core/util/SystemResourceMonitor.java` | Singleton monitoring CPU/memory of external local-LLM processes (GPT4All/Ollama) during inference, with average/peak statistics. |
| `mist-core/src/main/java/io/mist/core/util/Timer.java` | Static named-counter stopwatch accumulating start/stop durations per TestStep for timing instrumentation. |
| `mist-core/src/main/java/io/mist/core/util/TraceValidator.java` | Stub trace validator checking expected service::operation steps appear in a recorded trace; placeholder for a real Jaeger/Zipkin query. |

### `mist-core/src/main/java/io/mist/core/value/`

| Path | Description |
|------|-------------|
| `mist-core/src/main/java/io/mist/core/value/ResolvedValue.java` | Immutable carrier coupling a parameter's string value with its ValueProvenance; factory methods per provenance category. |
| `mist-core/src/main/java/io/mist/core/value/ValueProvenance.java` | Enum of how a value was obtained (resolved live/cache, LLM-generated, synthetic placeholder, mutated) driving positive/negative test classification. |
| `mist-core/src/main/java/io/mist/core/value/ValueProvenanceInference.java` | Heuristic inferring SYNTHETIC_PLACEHOLDER provenance from known fallback value prefixes (FALLBACK_, LLM_EMPTY, STEP1_, VAL_). |

### `mist-core/src/main/java/io/mist/core/workflow/`

| Path | Description |
|------|-------------|
| `mist-core/src/main/java/io/mist/core/workflow/NounKeyMap.java` | Immutable externalised noun-to-key map (default bundled YAML plus per-SUT override) turning URL path nouns like orders into field names like orderId. |
| `mist-core/src/main/java/io/mist/core/workflow/ScenarioOptimizer.java` | Post-merge optimizer that shatters fat multi-root scenarios into cohesive partitions via weakly-connected-component analysis of the dependency graph. |
| `mist-core/src/main/java/io/mist/core/workflow/TraceWorkflowExtractor.java` | Reads OpenTelemetry trace JSON/JSONL and reconstructs hierarchical WorkflowScenario instances, inferring cross-trace data dependencies. |
| `mist-core/src/main/java/io/mist/core/workflow/WorkflowScenario.java` | Model of a multi-step workflow scenario holding trace IDs and root steps, supporting cross-trace merge and decomposition tagging. |
| `mist-core/src/main/java/io/mist/core/workflow/WorkflowScenarioUtils.java` | Utility methods for scenarios, notably deduplicating by root API signature while registering all patterns for learning. |
| `mist-core/src/main/java/io/mist/core/workflow/WorkflowStep.java` | Model of one workflow step (a span) holding service/operation, timing, input/output fields, parent-child links, and merge provenance. |
| `mist-core/src/main/java/io/mist/core/workflow/WorkflowTestCase.java` | Executes a WorkflowScenario step-by-step, injecting captured outputs into later requests and validating via ApiClient and TraceValidator. |

### `mist-core/src/main/java/io/mist/core/workflow/pipeline/`

| Path | Description |
|------|-------------|
| `mist-core/src/main/java/io/mist/core/workflow/pipeline/PipelineContext.java` | Mutable struct-style state container threaded through pipeline stages (scenarios, specs, configs, dependency registry, shared/faulty pools, config). |
| `mist-core/src/main/java/io/mist/core/workflow/pipeline/PipelineStage.java` | Interface for one pipeline step with a name and a run against the shared PipelineContext. |
| `mist-core/src/main/java/io/mist/core/workflow/pipeline/WorkflowPipeline.java` | Sequentially executes a list of PipelineStages against a shared context, halting if a stage throws. |

### `mist-core/src/main/java/io/mist/core/workflow/pipeline/stages/`

| Path | Description |
|------|-------------|
| `mist-core/src/main/java/io/mist/core/workflow/pipeline/stages/DecompositionSupport.java` | Package-private helper backing Phase 4 that decomposes multi-root scenarios into per-root 1-Root baseline scenarios with fingerprint dedup. |
| `mist-core/src/main/java/io/mist/core/workflow/pipeline/stages/DedupSupport.java` | Package-private single-root dedup helper shared by Phase 2.5 and 3.5 that removes 1-root scenarios whose root API key is already approved. |
| `mist-core/src/main/java/io/mist/core/workflow/pipeline/stages/Phase25DedupStage.java` | Pipeline stage running the initial single-root scenario deduplication pass before downstream processing. |
| `mist-core/src/main/java/io/mist/core/workflow/pipeline/stages/Phase35DedupStage.java` | Pipeline stage re-running single-root dedup after shattering to drop new duplicate 1-root partitions, gated by the shattering flag. |
| `mist-core/src/main/java/io/mist/core/workflow/pipeline/stages/Phase3ShatteringStage.java` | Pipeline stage that partitions fat multi-root scenarios via ScenarioOptimizer, gated by mst.scenarioShattering.enabled. |
| `mist-core/src/main/java/io/mist/core/workflow/pipeline/stages/Phase4DecompositionStage.java` | Pipeline stage extracting 1-Root baseline scenarios from multi-root workflows via DecompositionSupport to guarantee per-API coverage. |
| `mist-core/src/main/java/io/mist/core/workflow/pipeline/stages/SharedPoolGenerationStage.java` | Pre-processing stage grouping scenarios by root API and generating the shared and faulty parameter pools consumed by the variant loop. |
| `mist-core/src/main/java/io/mist/core/workflow/pipeline/stages/SharedPoolSupport.java` | Package-private helpers backing SharedPoolGenerationStage holding the verbatim grouping, shared-pool, and faulty-pool generation logic. |
| `mist-core/src/main/java/io/mist/core/workflow/pipeline/stages/StageSupport.java` | Package-private pure-function helpers shared across stages (root API key extraction, operation-name parsing) lifted from the generator. |

### `mist-core/src/main/resources/mist/`

| Path | Description |
|------|-------------|
| `mist-core/src/main/resources/mist/fault-types.default.yaml` | Bundled default fault-taxonomy YAML: nine fault categories with applicableTo types and applicableLocations defining the registry rotation order. |
| `mist-core/src/main/resources/mist/noun-map.default.yaml` | Default noun-to-ID-key map (orders to orderId, trips to tripId, ...) for TrainTicket, naming path-segment fields; per-SUT overridable. |
| `mist-core/src/main/resources/mist/seed-trace-labels.json` | Phase 2.A label registry mapping seed-trace filenames to known-good or known-bad verdicts for the TraceShapeLearner invariant corpus. |

### `mist-core/src/test/java/io/mist/core/analysis/`

| Path | Description |
|------|-------------|
| `mist-core/src/test/java/io/mist/core/analysis/FaultDetectionTrackerAttributionTest.java` | Verifies the OracleAnomaly attribution histogram is populated only when a verdict carries a TARGET_ATTRIBUTION outcome, with report rendering and reset. |
| `mist-core/src/test/java/io/mist/core/analysis/FaultDetectionTrackerIdempotencyTest.java` | Checks per-execution dedup: same traceId marker collapses double records to one hit, fresh markers re-count, null traceId keeps legacy count-every-call. |
| `mist-core/src/test/java/io/mist/core/analysis/FaultDetectionTrackerOracleAnomalyTest.java` | Pins oracle-anomaly recording: same-key dedup with hitCount, first-seen sample, fingerprint normalization, verdict integration, report toggle. |
| `mist-core/src/test/java/io/mist/core/analysis/FaultDetectionTrackerSummaryTest.java` | Pins the end-of-run console anomaly summary: severity/kind buckets, distinct vs hit counts, actionable rendering, ASCII no-emoji mode. |
| `mist-core/src/test/java/io/mist/core/analysis/TraceShapeAdapterCacheTest.java` | Verifies TraceShapeAdapter.toModel caches by traceId (same reuses instance, different fresh), clearCache drops all, null/empty traceId does not poison. |

### `mist-core/src/test/java/io/mist/core/bandit/`

| Path | Description |
|------|-------------|
| `mist-core/src/test/java/io/mist/core/bandit/ThompsonSchedulerTest.java` | Pins ThompsonScheduler: samples in (0,1), success/failure shift posterior mean, rank favours high-alpha keys, snapshot/seed round-trip, prior validation. |

### `mist-core/src/test/java/io/mist/core/config/`

| Path | Description |
|------|-------------|
| `mist-core/src/test/java/io/mist/core/config/AblationProfileTest.java` | Pins AblationProfile.from(MstConfig) reads and single-line summary format across all-on, all-off, default, and mid ablation configurations. |
| `mist-core/src/test/java/io/mist/core/config/CacheToggleTest.java` | Pins the four read/write states of the master cache toggle (CacheToggle.canRead/canWrite) with defaults read-on write-on. |
| `mist-core/src/test/java/io/mist/core/config/MstConfigAdaptiveTest.java` | Pins MstConfig.Adaptive defaults (off, K=10/3, 5s token age) and system-property overrides so a disabled run stays byte-identical to legacy. |
| `mist-core/src/test/java/io/mist/core/config/MstConfigOracleAndSchedulerTest.java` | Pins the six ablation toggles for shape oracle, four invariants (timing off by default), and bandit; confirms each accessor reflects its property. |

### `mist-core/src/test/java/io/mist/core/coverage/`

| Path | Description |
|------|-------------|
| `mist-core/src/test/java/io/mist/core/coverage/LLMStatusCodeDiscoveryCacheTest.java` | Pins LLMStatusCodeDiscovery signature cache: normalizePath collapses value segments to {id}, cache key is method/schema-sensitive and param-order-stable. |

### `mist-core/src/test/java/io/mist/core/enhancer/`

| Path | Description |
|------|-------------|
| `mist-core/src/test/java/io/mist/core/enhancer/CanonicalKeyDryRunIT.java` | Read-only dry-run IT that feeds a real failed-tests.json through TestCaseEnhancer.canonicalKey and reports dedup groups; self-skips without -Dfailed.tests.json. |
| `mist-core/src/test/java/io/mist/core/enhancer/TestCaseEnhancerDedupTest.java` | Tests TestCaseEnhancer.canonicalKey grouping (collapses variants, splits on method/endpoint/status/schema) and EnhancementCache disk round-trip. |
| `mist-core/src/test/java/io/mist/core/enhancer/TestResultCaptureParameterObservationTest.java` | Tests TestResultCapture parameter-success/reject observation: 2xx-only success, negative-test gate, null/disabled skips, set accumulation, defensive snapshots. |

### `mist-core/src/test/java/io/mist/core/fault/`

| Path | Description |
|------|-------------|
| `mist-core/src/test/java/io/mist/core/fault/ApplicabilityMatrixTest.java` | Verifies ApplicabilityMatrix matches the legacy enum applicability table per oasType/category, honours location filter, normalizes aliases, rejects nulls. |
| `mist-core/src/test/java/io/mist/core/fault/FaultMinerTest.java` | Verifies FaultMiner gating (disabled returns empty, no LLM call) and enabled LLM mining: accepts candidates, persists YAML idempotently, rejects default collisions. |
| `mist-core/src/test/java/io/mist/core/fault/FaultTypeRegistryTest.java` | Locks the default-YAML FaultTypeRegistry: nine default ids, legacy applicability per oasType, int/long aliasing, mined overlay merge, malformed-YAML throws. |
| `mist-core/src/test/java/io/mist/core/fault/MistInvalidInputPoolTest.java` | Tests MistInvalidInputPool enqueue/dequeue and round-robin rotation across fault types, null-value distinction, reset replay, count, unknown-id rejection. |

### `mist-core/src/test/java/io/mist/core/generation/`

| Path | Description |
|------|-------------|
| `mist-core/src/test/java/io/mist/core/generation/EnumViolationTest.java` | Verifies generateEnumViolationInputs emits correctly-typed non-member values for string/integer enums, nothing without an enum, gated by registry per type. |
| `mist-core/src/test/java/io/mist/core/generation/MistGeneratorAdaptiveTest.java` | Verifies decideEndpointPolicy returns EndpointPolicy.LEGACY when adaptive off (byte-identical), and diverges per verb when adaptive on. |
| `mist-core/src/test/java/io/mist/core/generation/MistGeneratorBanditGateTest.java` | Verifies MistGenerator.applyBanditGate: disabled returns queue unchanged, enabled invokes ranker, short-circuits on empty/singleton/null. |
| `mist-core/src/test/java/io/mist/core/generation/MistGeneratorTwoPhaseTest.java` | Verifies MistGenerator two-phase hooks: setFaultyRatio/getFaultyRatio round-trip and resetForNewPhase is idempotent and preserves the faulty ratio. |
| `mist-core/src/test/java/io/mist/core/generation/SecretlyValidNegativeTest.java` | Guards against secretly-valid negatives: OVERFLOW never in-range, boolean TYPE_MISMATCH not binder-coercible, NULL_INPUT on strings emits only real null. |

### `mist-core/src/test/java/io/mist/core/health/`

| Path | Description |
|------|-------------|
| `mist-core/src/test/java/io/mist/core/health/SutHealthCheckTest.java` | Tests SutHealthCheck with stub probes: 200/404/403 healthy, 5xx/transport unhealthy, report aggregation/order, header threading, auth-aware 500 preflight. |

### `mist-core/src/test/java/io/mist/core/oracle/attribution/`

| Path | Description |
|------|-------------|
| `mist-core/src/test/java/io/mist/core/oracle/attribution/TraceAttributionTest.java` | Tests trace attribution: token mapping, service matching, leaf-error DFS walk, and TARGET/UPSTREAM/WRONG_PARAM/NO_ATTRIBUTION verdicts. |

### `mist-core/src/test/java/io/mist/core/oracle/shape/`

| Path | Description |
|------|-------------|
| `mist-core/src/test/java/io/mist/core/oracle/shape/ResponseEnvelopeInvariantTest.java` | Tests ResponseEnvelopeInvariant: learns success status values, fails on failure-set values, defers unknowns as INFO, 2xx-only scope, classifier wiring/caching. |
| `mist-core/src/test/java/io/mist/core/oracle/shape/SpanTreeShapeInvariantTest.java` | Tests SpanTreeShapeInvariant: empty corpus permissive, learns edges, flags unexpected and missing-required edges, K-threshold controls required-edge set. |
| `mist-core/src/test/java/io/mist/core/oracle/shape/StatusPropagationInvariantTest.java` | Tests StatusPropagationInvariant: learns http/otel status by depth, fails non-conformant status with span evidence, unknown depth permissive. |
| `mist-core/src/test/java/io/mist/core/oracle/shape/TimingEnvelopeInvariantTest.java` | Tests TimingEnvelopeInvariant: learns total/span p99, passes conformant, fails slow total (WARN) and span outliers, boundary at p99 passes. |
| `mist-core/src/test/java/io/mist/core/oracle/shape/TraceShapeOracleIntegrationTest.java` | End-to-end learn/persist/reload/evaluate of TraceShapeOracle over on-disk Jaeger traces, known-bad exclusion, classifier-driven soft-error failure. |
| `mist-core/src/test/java/io/mist/core/oracle/shape/TraceShapeOracleIntentTogglesTest.java` | Pins HiddenDownstreamFailureInvariant wiring into the 4-arg evaluate: fires only when its opt-in flag is on (default off). |
| `mist-core/src/test/java/io/mist/core/oracle/shape/TraceShapeOracleTogglesTest.java` | Tests TraceShapeOracle ablation toggles: disabled oracle returns empty verdict, default loads three invariants (timing off), per-invariant disable skips one. |

### `mist-core/src/test/java/io/mist/core/oracle/shape/invariant/`

| Path | Description |
|------|-------------|
| `mist-core/src/test/java/io/mist/core/oracle/shape/invariant/HiddenDownstreamFailureInvariantTest.java` | Tests HiddenDownstreamFailureInvariant: 2xx entry with swallowed downstream 5xx fails ERROR, otel-only WARN, silent on healthy/loud/4xx, co-root fix. |
| `mist-core/src/test/java/io/mist/core/oracle/shape/invariant/TargetAttributionInvariantTest.java` | Tests TargetAttributionInvariant outcome semantics (TARGET/NO pass, UPSTREAM/WRONG_PARAM fail) and the oracle 4-arg evaluate kill switch and overloads. |

### `mist-core/src/test/java/io/mist/core/policy/`

| Path | Description |
|------|-------------|
| `mist-core/src/test/java/io/mist/core/policy/EndpointPolicyResolverTest.java` | Tests EndpointPolicyResolver: RFC-7231 method defaults (GET payload K=25, POST off budget 50), x-mist-* hint overrides, garbage-hint safety, LEGACY sentinel. |

### `mist-core/src/test/java/io/mist/core/smart/`

| Path | Description |
|------|-------------|
| `mist-core/src/test/java/io/mist/core/smart/InputFetchRegistryPoolStatusTest.java` | Tests InputFetchRegistry pool-entry status: verified/rejected marking, no demotion of verified, promotion, default unverified, null no-op, YAML round-trip. |
| `mist-core/src/test/java/io/mist/core/smart/ProducerRankingTest.java` | Tests SmartInputFetcher cold-start name-affinity prior: endStation prefers station over train service, 'end' not matching 'vendor', off when feedback exists. |
| `mist-core/src/test/java/io/mist/core/smart/ShippedRegistryDepoisonTest.java` | Data-lint over shipped TrainTicket registries asserting all successRates are zero (de-poison) and endStation cold-start ranks stations over trains; skips if absent. |
| `mist-core/src/test/java/io/mist/core/smart/TTEndStationLiveCheck.java` | Live gated check that SmartInputFetcher grounds endStation from the station service against a running TrainTicket; skips without tt.live.base.url and DEEPSEEK_API_KEY. |

### `mist-core/src/test/java/io/mist/core/spi/`

| Path | Description |
|------|-------------|
| `mist-core/src/test/java/io/mist/core/spi/MistServicesTest.java` | Smoke test for the MistServices SPI locator: isPresent returns false when no impl is registered and the locator class loads without exception. |

### `mist-core/src/test/java/io/mist/core/value/`

| Path | Description |
|------|-------------|
| `mist-core/src/test/java/io/mist/core/value/ValueProvenanceTest.java` | Pins ValueProvenance.isLiveGrounded classification (live/cache/llm grounded; placeholder/mutated not) and ResolvedValue factory, equality, null rejection. |

### `mist-core/src/test/java/io/mist/core/workflow/pipeline/stages/`

| Path | Description |
|------|-------------|
| `mist-core/src/test/java/io/mist/core/workflow/pipeline/stages/DedupFlagLeakRegressionTest.java` | Locks the approvedInDedupPass lifecycle: optimizer drops the tag on shattered partitions, second dedup pass drops/keeps correctly, reset clears stale tags. |
| `mist-core/src/test/java/io/mist/core/workflow/pipeline/stages/StageSupportPathMatchTest.java` | Tests StageSupport path-template matching and service-name-first resolveOperation with endpoint fallback, prefix disambiguation, and exact-beats-template. |

## mist-cli & mist-llm

`mist-cli`: command-line entrypoint, SPI implementations, test writer, example configs and resources. `mist-llm`: LLM client/integration layer and its tests.

### `mist-cli/`

| Path | Description |
|------|-------------|
| `mist-cli/pom.xml` | Maven build for the mist-cli module; shades the standalone mist.jar (Main-Class io.mist.cli.MistMain), pinning REST Assured, Groovy and Allure deps. |

### `mist-cli/src/main/java/io/mist/cli/`

| Path | Description |
|------|-------------|
| `mist-cli/src/main/java/io/mist/cli/InjectedFaultConverterMain.java` | Main that converts an INJECTED_FAULTS.md markdown file into injected-faults JSON (fault names + their APIs) via InjectedFaultConverter. |
| `mist-cli/src/main/java/io/mist/cli/MistConfGenMain.java` | One-shot CLI that reads an OpenAPI spec and emits the per-service multi-service test configuration YAML referenced by conf.path. |
| `mist-cli/src/main/java/io/mist/cli/MistMain.java` | Standalone mist.jar entry point; loads a .properties file, resolves input paths, builds MstConfig and Inputs, then runs MistRunner. |
| `mist-cli/src/main/java/io/mist/cli/MistPathResolver.java` | Utility that rewrites relative INPUT path keys in a MIST .properties bag to absolute paths against the file's directory so CWD does not matter. |
| `mist-cli/src/main/java/io/mist/cli/MistRunResult.java` | Typed outcome of a MistRunner.run(): exit code, test-case count, allure report dir and run id, with a summarise() printer and builder. |
| `mist-cli/src/main/java/io/mist/cli/MistRunner.java` | Core MST pipeline runner; builds the MistGenerator and writer, bootstraps the Trace Shape Oracle, then generates, writes, executes and reports on tests. |
| `mist-cli/src/main/java/io/mist/cli/SemanticRegistryDumper.java` | Standalone utility that rebuilds the SemanticDependencyRegistry from the TrainTicket spec, config and traces and dumps it to a JSON file. |
| `mist-cli/src/main/java/io/mist/cli/SemanticRegistryEvaluator.java` | Evaluates the SemanticDependencyRegistry against a ground-truth YAML reporting precision/recall/F1, plus an ablation table and golden-file diff. |
| `mist-cli/src/main/java/io/mist/cli/TraceErrorAnalysisMain.java` | Demo main that runs TraceErrorAnalyzer over a sample failed trace, printing root-cause analysis and the error report after loading LLM properties. |
| `mist-cli/src/main/java/io/mist/cli/TraceMain.java` | Tiny scratch main that extracts and prints WorkflowScenarios from a single hardcoded trace file path. |

### `mist-cli/src/main/java/io/mist/cli/auth/`

| Path | Description |
|------|-------------|
| `mist-cli/src/main/java/io/mist/cli/auth/MstAuthHandler.java` | Runtime auth helper for MST-generated tests with configurable login strategy (none/static/per-jvm/per-test), token caching and per-path skip patterns. |
| `mist-cli/src/main/java/io/mist/cli/auth/MstAuthRefreshFilter.java` | REST Assured filter for MST tests that on a 401/403 invalidates the cached JWT, re-logs in, swaps the header and retries the request once. |

### `mist-cli/src/main/java/io/mist/cli/spi/`

| Path | Description |
|------|-------------|
| `mist-cli/src/main/java/io/mist/cli/spi/DefaultMistSpec.java` | MistSpec SPI adapter wrapping an OpenAPISpecification to expose the swagger OpenAPI model, location and title to mist-core. |
| `mist-cli/src/main/java/io/mist/cli/spi/DefaultMistSpecLoader.java` | MistSpecLoader SPI implementation (registered via META-INF/services) that loads a spec through OpenAPISpecification's path constructor. |
| `mist-cli/src/main/java/io/mist/cli/spi/MavenSurefireMistTestExecutor.java` | MistTestExecutor SPI implementation that shells out to mvn test against the written test directory, using its exit code as the pass/fail signal. |
| `mist-cli/src/main/java/io/mist/cli/spi/PojoConverter.java` | Boundary helper that unwraps a map of OpenAPISpecification wrappers into raw swagger OpenAPI models for the registry builder and generator. |
| `mist-cli/src/main/java/io/mist/cli/spi/RestAssuredMistTestWriter.java` | MistTestWriter SPI implementation that delegates test-source emission to a configured MultiServiceRESTAssuredWriter. |

### `mist-cli/src/main/java/io/mist/cli/writer/`

| Path | Description |
|------|-------------|
| `mist-cli/src/main/java/io/mist/cli/writer/MultiServiceRESTAssuredWriter.java` | Writer emitting JUnit/REST Assured suites that replay MultiServiceTestCases, with auth, Allure, query-param and Trace Shape Oracle emission. |

### `mist-cli/src/main/resources/`

| Path | Description |
|------|-------------|
| `mist-cli/src/main/resources/log4j2-logToFile.properties` | Log4j2 config for the logToFile profile: console at WARN+, rolling file at INFO with timestamp pattern under ${logFilename}.log. |
| `mist-cli/src/main/resources/log4j2.properties` | Default Log4j2 config: WARN+ console with ConsoleDedupFilter, INFO rolling file at logs/mist.log, swagger logger capped at WARN. |

### `mist-cli/src/main/resources/META-INF/services/`

| Path | Description |
|------|-------------|
| `mist-cli/src/main/resources/META-INF/services/io.mist.core.spi.MistSpecLoader` | ServiceLoader registration naming io.mist.cli.spi.DefaultMistSpecLoader as the MistSpecLoader implementation. |
| `mist-cli/src/main/resources/META-INF/services/io.mist.core.spi.MistTestExecutor` | ServiceLoader registration naming io.mist.cli.spi.MavenSurefireMistTestExecutor as the MistTestExecutor implementation. |
| `mist-cli/src/main/resources/META-INF/services/io.mist.core.spi.MistTestWriter` | ServiceLoader registration naming io.mist.cli.spi.RestAssuredMistTestWriter as the MistTestWriter implementation. |

### `mist-cli/src/main/resources/My-Example/`

| Path | Description |
|------|-------------|
| `mist-cli/src/main/resources/My-Example/trainticket-demo-noexec.properties` | Bundled TrainTicket demo config (generation-only: execute/allure off, LLM disabled, hardcode negatives) merging MIST-core and MST keys in one file. |
| `mist-cli/src/main/resources/My-Example/trainticket-demo.properties` | Bundled TrainTicket demo config (full run: execute/allure on, LLM and smart-fetch enabled) merging MIST-core and MST keys in one file. |

### `mist-cli/src/main/resources/My-Example/trainticket/`

| Path | Description |
|------|-------------|
| `mist-cli/src/main/resources/My-Example/trainticket/flow.md` | Mermaid-diagram doc describing the MST end-to-end flow from MistMain through MistRunner generation, oracle bootstrap and execution. |
| `mist-cli/src/main/resources/My-Example/trainticket/input-fetch-registry.yaml` | Smart-input-fetch registry YAML mapping parameter names to producer endpoints, extract paths, priorities and success rates for TrainTicket. |
| `mist-cli/src/main/resources/My-Example/trainticket/merged_openapi_spec 1.yaml` | Merged OpenAPI 3.0.3 specification for the whole TrainTicket microservice system, used as the oas.path input. |
| `mist-cli/src/main/resources/My-Example/trainticket/mist-noun-map.yaml` | Empty per-SUT noun-map override for TrainTicket demonstrating the overlay mechanism over the bundled default noun map. |
| `mist-cli/src/main/resources/My-Example/trainticket/real-system-conf.yaml` | Multi-service test configuration YAML listing each TrainTicket service's operations, paths and parameter generators (the conf.path input). |
| `mist-cli/src/main/resources/My-Example/trainticket/root-api-registry.json` | Persisted Root API Registry JSON of unique TrainTicket root APIs and their recorded microservice call trees per source trace. |

### `mist-cli/src/main/resources/My-Example/trainticket/injectedFaults/`

| Path | Description |
|------|-------------|
| `mist-cli/src/main/resources/My-Example/trainticket/injectedFaults/injected-faults.json` | JSON registry of the 10 named faults injected into TrainTicket APIs (service, API path, faultName) consumed by FaultDetectionTracker. |

### `mist-cli/src/main/resources/My-Example/trainticket/test-trace/`

| Path | Description |
|------|-------------|
| `mist-cli/src/main/resources/My-Example/trainticket/test-trace/traces-1772605095842.json` | Bundled Jaeger/OpenTelemetry trace dump (data/spans) of TrainTicket request traces; the seed trace corpus for MST scenario extraction. |

### `mist-cli/src/test/java/io/mist/cli/`

| Path | Description |
|------|-------------|
| `mist-cli/src/test/java/io/mist/cli/SpiDiscoveryIntegrationTest.java` | JUnit test verifying the MIST SpecLoader, TestWriter and TestExecutor SPIs are discoverable via ServiceLoader on the cli classpath. |

### `mist-cli/src/test/java/io/mist/cli/writer/`

| Path | Description |
|------|-------------|
| `mist-cli/src/test/java/io/mist/cli/writer/QueryParamEmissionTest.java` | JUnit test locking the writer's query-param emission: form-encoded values, fault-target replacement, and no double-emission of dependency-wired params. |

### `mist-llm/`

| Path | Description |
|------|-------------|
| `mist-llm/pom.xml` | Maven build for the mist-llm module (LLM dispatch + cache SPI and backends); depends on okhttp/gson/org.json with no mist-core or restest edges. |

### `mist-llm/src/main/java/io/mist/llm/`

| Path | Description |
|------|-------------|
| `mist-llm/src/main/java/io/mist/llm/GeminiApiClient.java` | OkHttp client for Google Gemini generateContent with 429 rate-limit retry/backoff, response parsing, and optional seed for deterministic decoding. |
| `mist-llm/src/main/java/io/mist/llm/LLMBackendKind.java` | Enum discriminating the LLM backend (OLLAMA, GEMINI, OPENAI_COMPATIBLE) kept in lock-step with LLMConfig.ModelType. |
| `mist-llm/src/main/java/io/mist/llm/LLMCallCache.java` | On-disk SHA-256-keyed JSON cache mapping LLM call descriptors to responses for seed-deterministic replay; debounced atomic flush, corrupt-file fail-fast. |
| `mist-llm/src/main/java/io/mist/llm/LLMClient.java` | Tiny LLM SPI interface (prompt plus envelope promptWith) letting any module call an LLM backend without pulling in adapter dependencies. |
| `mist-llm/src/main/java/io/mist/llm/LLMCommunicationSink.java` | ServiceLoader SPI for handing LLM request/response metadata to a downstream communication logger; falls back to a no-op when none is registered. |
| `mist-llm/src/main/java/io/mist/llm/LLMConfig.java` | LLM config loader from properties for openai-compatible/Gemini/Ollama backends, with env-placeholder resolution, memoization and the seed gate. |
| `mist-llm/src/main/java/io/mist/llm/LLMRequest.java` | Immutable request envelope (system/user prompt, maxTokens, temperature, backend hint) for the LLMClient SPI. |
| `mist-llm/src/main/java/io/mist/llm/LLMResponse.java` | Immutable response envelope (content, success flag, error message) for the LLMClient SPI with ok()/failure() factories. |
| `mist-llm/src/main/java/io/mist/llm/LLMService.java` | Singleton LLMClient implementation routing prompts to openai-compatible/Gemini/Ollama backends with cache, watchdog timeouts and comm-sink logging. |
| `mist-llm/src/main/java/io/mist/llm/MistLLMException.java` | Unchecked exception for unrecoverable LLM SPI failures (config/wire-format); recoverable failures instead return a null result. |
| `mist-llm/src/main/java/io/mist/llm/NoOpLLMCommunicationSink.java` | Default no-op LLMCommunicationSink binding that silently drops events when no downstream logger is registered. |
| `mist-llm/src/main/java/io/mist/llm/OllamaApiClient.java` | OkHttp client for a local or remote Ollama API with configurable model, timeouts and rate-limit retry. |
| `mist-llm/src/main/java/io/mist/llm/package-info.java` | Package doc for io.mist.llm describing the LLM dispatch/cache layer, its SPI types, and the no-mist-core dependency rule. |

### `mist-llm/src/test/java/io/mist/llm/`

| Path | Description |
|------|-------------|
| `mist-llm/src/test/java/io/mist/llm/LLMCallCacheTest.java` | JUnit test for LLMCallCache: key stability across input dimensions, get/put round-trip, and loud failure on a corrupt on-disk cache file. |
| `mist-llm/src/test/java/io/mist/llm/LLMClientContractTest.java` | JUnit test locking LLMClient SPI relationships: LLMService implements LLMClient, cache honours random.seed, and response success flags. |
| `mist-llm/src/test/java/io/mist/llm/LLMConfigEnvResolverTest.java` | JUnit test for LLMConfig.resolveEnvPlaceholder: literal pass-through, empty/null handling, and ${VAR} env/system/default resolution. |
| `mist-llm/src/test/java/io/mist/llm/LLMConfigSeedGateTest.java` | JUnit test for LLMConfig.applySeedGate: collapses temperature to 0.0 when -Drandom.seed is set, saving/restoring the property hermetically. |
| `mist-llm/src/test/java/io/mist/llm/LLMServiceCacheGateTest.java` | JUnit test for LLMService cacheReadEnabled/cacheWriteEnabled gates driven by mist.llm.cache.read/write and the random.seed fallback. |

## evaluation — SUTs & harness

Evaluation harness and systems-under-test (bookinfo, boutique, sockshop, trainticket): OpenAPI specs, deploy/workload scripts, injected faults, captured traces, oracle configs.

### `evaluation/`

| Path | Description |
|------|-------------|
| `evaluation/.gitignore` | Ignores per-SUT runtime caches/logs/outputs (suts/*/.runtime/) plus trainticket's runtime-regenerated registry dir; all regenerable, never committed. |
| `evaluation/run-offline-oracle.sh` | Offline repro: builds mist.jar then runs OracleCheck on committed Bookinfo (HTTP) and Online Boutique (gRPC) outage traces; no live SUT or LLM. |

### `evaluation/suts/bookinfo/`

| Path | Description |
|------|-------------|
| `evaluation/suts/bookinfo/MANIFEST.json` | Bookinfo trace-corpus manifest: documents masked/healthy /productpage oracle A/B traces and the gen_api_* healthy generation-input traces with capture method. |
| `evaluation/suts/bookinfo/OracleCheck.java` | Harness running MIST's HiddenDownstreamFailureInvariant on a Bookinfo Jaeger trace, contrasting trace-oracle FIRES vs a response-level oracle that PASSES on 2xx. |
| `evaluation/suts/bookinfo/README.md` | Bookinfo SUT guide: the hidden-downstream-failure phenomenon SUT (reviews swallows a failed ratings call, returns 200); deploy/workload/oracle quick-start. |
| `evaluation/suts/bookinfo/bookinfo-demo.properties` | Single MIST config profile for Bookinfo (MST generator + MST section): hidden-downstream oracle on, auth.mode=none, smart-fetch off, DeepSeek LLM. |
| `evaluation/suts/bookinfo/real-system-conf.yaml` | MIST multi-service test config for Bookinfo (generated from swagger): 4 GET ops over product/review/rating services with id path-param generators. |
| `evaluation/suts/bookinfo/root-api-registry.json` | Bookinfo root-API registry: unique root APIs (/productpage, /api/v1/products...) and their microservice interaction trees, regenerated from captured traces. |
| `evaluation/suts/bookinfo/run-oracle-e2e.sh` | Bookinfo end-to-end demo: compiles MIST-generated JUnit tests, toggles a real ratings outage, runs 4 cases where /reviews FIRES HIDDEN_DOWNSTREAM_FAILURE under 200. |

### `evaluation/suts/bookinfo/deploy/`

| Path | Description |
|------|-------------|
| `evaluation/suts/bookinfo/deploy/deploy.sh` | Stands up Istio Bookinfo on kind (demo profile + Jaeger, 100% sampling), pins reviews->v3; idempotent; teardown deletes the kind cluster. |

### `evaluation/suts/bookinfo/injectedFaults/`

| Path | Description |
|------|-------------|
| `evaluation/suts/bookinfo/injectedFaults/injected-faults.json` | Empty injected-faults registry for Bookinfo (0 faults): its failure is an oracle anomaly HIDDEN_DOWNSTREAM_FAILURE, not a SUT-reported named fault. |

### `evaluation/suts/bookinfo/openapi/`

| Path | Description |
|------|-------------|
| `evaluation/suts/bookinfo/openapi/bookinfo-swagger.yaml` | Bookinfo OpenAPI 2.0 spec: 4 GET endpoints under /api/v1 (products, product, reviews, ratings) with Product/Review/Rating schemas. |

### `evaluation/suts/bookinfo/traces/`

| Path | Description |
|------|-------------|
| `evaluation/suts/bookinfo/traces/gen_api_product_id.json` | Healthy Bookinfo Jaeger trace for GET /api/v1/products/{id} (productpage->details, all 200); MIST generation-input seed. |
| `evaluation/suts/bookinfo/traces/gen_api_products.json` | Healthy Bookinfo Jaeger trace for GET /api/v1/products (ingress->productpage, all 200, no ratings dependency); MIST generation-input seed. |
| `evaluation/suts/bookinfo/traces/gen_api_ratings.json` | Healthy Bookinfo Jaeger trace for GET /api/v1/products/{id}/ratings (productpage->ratings, all 200); generation seed; fails loud 503 under outage. |
| `evaluation/suts/bookinfo/traces/gen_api_reviews.json` | Healthy Bookinfo Jaeger trace for GET /api/v1/products/{id}/reviews (productpage->reviews->ratings, all 200); the endpoint that masks under a ratings outage. |
| `evaluation/suts/bookinfo/traces/healthy_3c613a26a5388abff9cb10726dbd8b47.json` | Healthy /productpage Bookinfo trace (9 spans, all 2xx, ratings restored); the oracle-silent control for the hidden-downstream A/B. |
| `evaluation/suts/bookinfo/traces/masked_ratings_outage_c80623f59e318b99bc556c41f818e5b2.json` | Masked /productpage Bookinfo outage trace (reviews->ratings 503/ERROR swallowed behind a 200); second instance where HIDDEN_DOWNSTREAM_FAILURE fires. |
| `evaluation/suts/bookinfo/traces/masked_ratings_outage_f13dbc33d5858c49da37f039a0243c3a.json` | Masked /productpage Bookinfo outage trace (8 spans; reviews->ratings 503/ERROR/UH swallowed behind a 200); HIDDEN_DOWNSTREAM_FAILURE fires at ERROR. |

### `evaluation/suts/bookinfo/workload/`

| Path | Description |
|------|-------------|
| `evaluation/suts/bookinfo/workload/inject-ratings-outage.sh` | Toggles Bookinfo's hidden-downstream fault by scaling ratings-v1 to 0 (on) or 1 (off) — a real availability outage, not a code mutant. |
| `evaluation/suts/bookinfo/workload/traffic.sh` | Drives N nominal GET /productpage requests through the Istio ingress gateway (default 50) and prints the returned HTTP codes. |

### `evaluation/suts/boutique/`

| Path | Description |
|------|-------------|
| `evaluation/suts/boutique/OracleCheck.java` | Harness (identical to Bookinfo's) running MIST's HiddenDownstreamFailureInvariant on an Online Boutique Jaeger trace vs the response-level oracle. |
| `evaluation/suts/boutique/README.md` | Online Boutique SUT guide: 4th SUT, 2nd hidden-downstream demo over gRPC (adservice outage swallowed by frontend, home 200); deploys into Bookinfo's cluster. |
| `evaluation/suts/boutique/boutique-demo.properties` | Single MIST config profile for Online Boutique (frontend HTTP service, base.url :8081); hidden-downstream invariant on for the adservice gRPC outage demo. |
| `evaluation/suts/boutique/real-system-conf.yaml` | MIST test config for Online Boutique (generated from swagger): single frontend service with home/cart/product/checkout HTTP routes. |

### `evaluation/suts/boutique/deploy/`

| Path | Description |
|------|-------------|
| `evaluation/suts/boutique/deploy/deploy.sh` | Adds Online Boutique (microservices-demo) into the existing kind+Istio+Jaeger cluster with sidecars so frontend->gRPC calls are traced; teardown removes the namespace. |

### `evaluation/suts/boutique/openapi/`

| Path | Description |
|------|-------------|
| `evaluation/suts/boutique/openapi/boutique-swagger.yaml` | Online Boutique OpenAPI 2.0 spec describing the single HTTP frontend's routes (home/product/cart/checkout); the other 10 services are internal gRPC. |

### `evaluation/suts/boutique/traces/`

| Path | Description |
|------|-------------|
| `evaluation/suts/boutique/traces/boutique_adservice_outage.json` | Online Boutique frontend traces with adservice scaled to 0: home 200 while the swallowed frontend->adservice gRPC span carries otel=ERROR; oracle evidence. |
| `evaluation/suts/boutique/traces/boutique_home.json` | Healthy Online Boutique frontend Jaeger traces (adservice up, no ERROR spans); MIST generation seed and the oracle-silent control. |

### `evaluation/suts/boutique/workload/`

| Path | Description |
|------|-------------|
| `evaluation/suts/boutique/workload/capture-traces-controlled.sh` | Re-captures Boutique healthy vs adservice-outage windows (loadgenerator-driven deep traces) and verifies MIST's oracle fires only under outage. |
| `evaluation/suts/boutique/workload/capture-traces.sh` | Drives the Boutique frontend, pulls healthy traces, then scales adservice to 0 to capture swallowed-gRPC-error traces and restores adservice. |

### `evaluation/suts/sockshop/`

| Path | Description |
|------|-------------|
| `evaluation/suts/sockshop/README.md` | Sock Shop SUT guide: the generalization-validation SUT (different services/tags/basePath); proves MIST runs on a new SUT from its own inputs with no code edits. |
| `evaluation/suts/sockshop/real-system-conf.yaml` | MIST test config for Sock Shop (generated from swagger): catalogue/cart/order/user services derived from OpenAPI tags, paths at root. |
| `evaluation/suts/sockshop/sockshop-demo.properties` | Single minimal MIST config profile for Sock Shop, relying on generalization defaults (auth.mode=none, smart-fetch OAS=oas.path, basePath auto). |

### `evaluation/suts/sockshop/deploy/`

| Path | Description |
|------|-------------|
| `evaluation/suts/sockshop/deploy/deploy.sh` | Adds WeaveWorks Sock Shop into the existing kind+Istio+Jaeger cluster, excludes its DBs from the mesh, routes it through the shared ingress; teardown removes it. |

### `evaluation/suts/sockshop/openapi/`

| Path | Description |
|------|-------------|
| `evaluation/suts/sockshop/openapi/sockshop-swagger.yaml` | Sock Shop front-end OpenAPI 2.0 spec: 26 ops (incl. 12 writes) across catalogue/cart/order/user, each tagged with its owning microservice. |

### `evaluation/suts/sockshop/traces/`

| Path | Description |
|------|-------------|
| `evaluation/suts/sockshop/traces/sockshop_catalogue.json` | Sock Shop ingress->front-end Jaeger trace for GET /catalogue (traceparent ...0001); generation input (shallow: front-end does not propagate W3C). |
| `evaluation/suts/sockshop/traces/sockshop_catalogue_id.json` | Sock Shop ingress->front-end Jaeger trace for GET /catalogue/{id} (traceparent ...0004); MIST generation input. |
| `evaluation/suts/sockshop/traces/sockshop_catalogue_size.json` | Sock Shop ingress->front-end Jaeger trace for GET /catalogue/size (traceparent ...0002); MIST generation input. |
| `evaluation/suts/sockshop/traces/sockshop_tags.json` | Sock Shop ingress->front-end Jaeger trace for GET /tags (traceparent ...0003); MIST generation input. |

### `evaluation/suts/sockshop/workload/`

| Path | Description |
|------|-------------|
| `evaluation/suts/sockshop/workload/capture-traces.sh` | Drives Sock Shop catalogue endpoints through the Istio ingress with W3C traceparent markers and pulls each trace by id from Jaeger into traces/. |

### `evaluation/suts/trainticket/`

| Path | Description |
|------|-------------|
| `evaluation/suts/trainticket/MANIFEST.json` | TrainTicket trace/fault manifest: documents the loud-failure adminroute trace, the nominal workload corpus, and the 10 SUT-reported injected faults. |
| `evaluation/suts/trainticket/OracleCheck.java` | Harness (identical to Bookinfo's) running MIST's HiddenDownstreamFailureInvariant on a TrainTicket Jaeger trace vs the response-level oracle. |
| `evaluation/suts/trainticket/README.md` | TrainTicket SUT guide: the fault-injection SUT (7 services carry 10 code-level faults detected via faultName markers); deploys via k8s/minikube make deploy. |
| `evaluation/suts/trainticket/ResponseEnvelopeLiveCheck.java` | Live harness: runs MIST's LLM-backed ResponseEnvelope invariant on a TrainTicket soft-error body (200, status:0), flipping it to RESPONSE_ENVELOPE=FAIL. |
| `evaluation/suts/trainticket/input-fetch-registry.yaml` | TrainTicket smart-input-fetch registry: maps API parameters (e.g. boughtDateStart) to endpoints/services for dependency-resolved value fetching. |
| `evaluation/suts/trainticket/mist-noun-map.yaml` | Empty per-SUT noun-map override for TrainTicket (default map already covers its vocabulary); exists to demonstrate the override mechanism. |
| `evaluation/suts/trainticket/real-system-conf.yaml` | MIST test config for TrainTicket (generated from the 265-op spec): ~40 ts-* services with actuator/health and business endpoints. |
| `evaluation/suts/trainticket/root-api-registry.json` | TrainTicket root-API registry: unique root APIs and their multi-service call trees, regenerated from the captured nominal trace corpus. |
| `evaluation/suts/trainticket/run-oracle-e2e.sh` | TrainTicket end-to-end detection run against a local deploy: runs MIST (generate->compile->execute->oracle) and prints the fault-detection summary. |
| `evaluation/suts/trainticket/trainticket-demo-noexec.properties` | Offline MIST profile for TrainTicket (experiment.execute=false): zero-infra generation check with no live SUT. |
| `evaluation/suts/trainticket/trainticket-demo.properties` | Full MIST profile for TrainTicket (experiment.execute=true): generate->compile->execute->detect against the local fault-injection deploy. |

### `evaluation/suts/trainticket/deploy/`

| Path | Description |
|------|-------------|
| `evaluation/suts/trainticket/deploy/deploy.sh` | Deploys the fault-injection TrainTicket via minikube + make deploy: builds ~40 services from injection-branch source, exposes gateway on :32677; teardown via make reset-deploy. |

### `evaluation/suts/trainticket/injectedFaults/`

| Path | Description |
|------|-------------|
| `evaluation/suts/trainticket/injectedFaults/injected-faults.json` | TrainTicket injected-faults registry: 10 named faults (INVALID_*, INSUFFICIENT_STATIONS) across 5 admin/travel services, matched against SUT-reported faultName markers. |

### `evaluation/suts/trainticket/openapi/`

| Path | Description |
|------|-------------|
| `evaluation/suts/trainticket/openapi/merged_openapi_spec.yaml` | TrainTicket merged OpenAPI 3.0.3 spec (265 operations across the ~40 ts-* services); MIST input #1. |

### `evaluation/suts/trainticket/traces/`

| Path | Description |
|------|-------------|
| `evaluation/suts/trainticket/traces/admin_add_route_failed.json` | TrainTicket loud-failure Jaeger trace: POST /adminroute NumberFormatException propagates http=500 x5 + otel=ERROR x7 to the root (fails loud, no masking). |
| `evaluation/suts/trainticket/traces/traces-1772605095842.json` | TrainTicket nominal-traffic Jaeger trace corpus; part of the seed the workflow extractor consumes for test generation. |

### `evaluation/suts/trainticket/workload/`

| Path | Description |
|------|-------------|
| `evaluation/suts/trainticket/workload/login-and-drive.sh` | Logs into the local TrainTicket gateway with admin/222222 and drives N GET /adminbasic/stations requests as nominal traffic. |

## debug — notes, inputs & measurements

Developer design notes / investigation logs (excluding inputs), plus the input-quality measurement framework: Python scripts, smart-fetch experiments, and timestamped measurement runs.

### `debug/Conference-refinement/`

| Path | Description |
|------|-------------|
| `debug/Conference-refinement/B1_BYTE_IDENTICAL_REPORT.md` | B1.G verification report; byte-identity fails against the live TrainTicket cluster due to non-deterministic SUT inputs, aggregate metrics match |
| `debug/Conference-refinement/B1_BYTE_IDENTICAL_after.sums` | SHA-256 list of 123 generated Flow_Scenario_*.java files from the after run (commit 4e6964f8) for the byte-identical check |
| `debug/Conference-refinement/B1_BYTE_IDENTICAL_baseline.sums` | SHA-256 list of 123 generated Flow_Scenario_*.java files from the baseline run (commit 9a6d2d94) for the byte-identical check |
| `debug/Conference-refinement/B1_FOLLOWUPS.md` | Tracker of deferred B1 items (remaining vendoring, class moves, SPI scaffolding) still ahead of the RESTest-sever rebuild |
| `debug/Conference-refinement/B1_INVENTORY.md` | Inventory of the MultiServiceTestCaseGenerator-to-RESTest dependency surface and the per-class disposition for the B1 sever |
| `debug/Conference-refinement/CRITICAL_FIXES_S_A_1_6.md` | Execution brief for seven surgical S/A-tier fixes (LLM determinism, MstConfig POJO, noun map, dedup, phase pipeline, cache migration) |
| `debug/Conference-refinement/H2_FOLLOWUPS.md` | Out-of-scope follow-ups from the H2 ablation work, including the resolved seed-42 non-determinism bandit-seeding fix |
| `debug/Conference-refinement/H2_INVENTORY.md` | Inventory of every code surface the H2 ablation-toggle work touches, with verified file:line references |
| `debug/Conference-refinement/PATH_B_REBUILD_PLAN.md` | Long-horizon Path B rebuild plan for ICSE/FSE 2027: decouple from RESTest, Trace Shape Oracle, adaptive fault taxonomy |
| `debug/Conference-refinement/PROMPT_B1_SEVER_RESTEST_INHERITANCE.md` | Execution brief for B1: sever RESTest inheritance and vendor the minimal generation core into mist-core |
| `debug/Conference-refinement/PROMPT_H2_ABLATION_INFRASTRUCTURE.md` | Execution brief for H2: add per-contribution ablation toggles via single mst. properties for the evaluation phase |
| `debug/Conference-refinement/PROMPT_VERIFY_FIXES.md` | Brief for independently verifying that the four landed fix tracks do what their commit messages claim; report-only, no code edits |
| `debug/Conference-refinement/Path B — End-to-End Summary` | Narrative end-to-end summary of the Path B rebuild commits that split RESTest into a 4-module MIST reactor with production wiring |
| `debug/Conference-refinement/README.md` | Empty placeholder file (single blank line) |

### `debug/a-main/`

| Path | Description |
|------|-------------|
| `debug/a-main/README.md` | **Active A-conference plan v4** (reviewer-hardened): take MIST to a top venue via black-box generation-driven fault injection + a label-free differential data-integrity trace oracle; honest Borderline verdict, build list, decision gates, strategic options |
| `debug/a-main/EXECUTION.md` | **Active execution plan** (decision: bet on Gate 3): ordered engineering sequence with code seams + acceptance criteria; focus on the Gate 1 sprint (B1 fault-injection mode, B2 differential oracle, validate on TrainTicket) toward the Gate 3 empirical bug hunt |

### `debug/a-main/research/`

| Path | Description |
|------|-------------|
| `debug/a-main/research/00-grounding-synthesis.md` | Neutral grounding anchor for the A-main plan: what MIST is, the 8 binding prior findings, candidate directions, open questions, and the final decision log |
| `debug/a-main/research/01-feasibility-codebase.md` | Codebase feasibility audit (file:line): MIST is observe-only; signal floor (status+topology cross-SUT); corpus floor; build list and hard constraints |
| `debug/a-main/research/02-sota-and-bar.md` | SOTA & A-conf bar survey for REST/microservice testing: frontier table, evaluation bar, ranked open gaps, novelty-risk watchlist (cited) |
| `debug/a-main/research/03-active-elicitation.md` | Novel-direction research: active provocation of masked failures; crowded vs Filibuster/Cast; recommends a label-free differential data-integrity oracle (cited) |
| `debug/a-main/research/04-oracle-invariant-learning.md` | Novel-direction research: automated trace-invariant learning is a supporting pillar not a headline; novelty delta vs AGORA+/TraceAnomaly/Tracetest/MINES (cited) |
| `debug/a-main/research/05-evaluation-and-benchmarks.md` | Evaluation design: 6+2 SUTs, anti-tautology baselines, 3-strata ground-truth protocol, metrics/stats, obtainability risks (cited) |
| `debug/a-main/research/REVIEW-R1-novelty.md` | Round-1 A-conf reviewer (novelty) on plan v3: Weak Reject; Cast pre-emption FATAL |
| `debug/a-main/research/REVIEW-R2-evaluation.md` | Round-1 A-conf reviewer (evaluation) on plan v3: Weak Reject; circular ground-truth FATAL |
| `debug/a-main/research/REVIEW-R3-soundness.md` | Round-1 A-conf reviewer (soundness) on plan v3: Weak Reject; differential oracle is "a race, not an invariant" FATAL |
| `debug/a-main/research/REVIEW2-R1-novelty.md` | Round-2 reviewer (novelty) on plan v4: Borderline; Cast claims verified verbatim; thin-novelty cap remains |
| `debug/a-main/research/REVIEW2-R2-evaluation.md` | Round-2 reviewer (evaluation) on plan v4: Borderline (methodology axis at Accept); circular GT resolved in design |
| `debug/a-main/research/REVIEW2-R3-soundness.md` | Round-2 reviewer (soundness) on plan v4: Borderline; race reframed as measured, gated bounded risk |

### `debug/a-main/prep/`

| Path | Description |
|------|-------------|
| `debug/a-main/prep/target-triples.md` | Main-track prep (no tool code): candidate (write endpoint, persisting dependency, read-back GET) triples in the TrainTicket spec for the Gate 1 differential data-integrity oracle; recommends adminroute + adminbasic/contacts |
| `debug/a-main/prep/sut-fault-injection-capability.md` | Main-track prep (no tool code): our SUT fork (train-ticket-injection@injection) already has an in-service fault injector; how to extend it SUT-side (LOST_WRITE_FAULT on a MIST-trainticket branch) to build the differential-oracle ground truth with zero MIST tool changes; §8 records the implemented adminroute LOST_WRITE (commit 5c471dd8) |
| `debug/a-main/prep/gate1-environment-runbook.md` | Main-track prep (no tool code): WSL2/k8s runbook to deploy TrainTicket (MIST-trainticket branch) with tracing, enable the LOST_WRITE variant on ts-admin-route-service, and confirm the acknowledged-but-lost write by read-back (manual proof of the differential oracle's target before B2 is built) |

### `debug/a-main/benchmark/`

| Path | Description |
|------|-------------|
| `debug/a-main/benchmark/README.md` | Main-track prep (no tool code): structure + schema for contribution C2 — the first OPEN-SOURCE labeled benchmark of masked-downstream / data-integrity faults; 3 strata, oracle-verdict semantics (baseline cols deterministic, MIST cols are targets measured at Gate 1), how to add/validate a case |
| `debug/a-main/benchmark/schema/fault-case.schema.json` | JSON Schema (draft 2020-12) for one labeled fault case: target triple, injection mechanism, ground-truth label, and each oracle's expected verdict; validated with Python jsonschema (positive + negative tests pass) |
| `debug/a-main/benchmark/schema/rubric.md` | Pre-registered genuine-vs-benign labeling rubric (adjudication guide shipped with the benchmark): checkable predicates per fault class, quiescence protocol, stratum-3 κ adjudication, honesty rules |
| `debug/a-main/benchmark/cases/TT-adminroute-lostwrite-001.json` | Seed case — stratum 1 POSITIVE: adminroute acknowledged-but-lost write (LOST_WRITE_FAULT); all baseline + trace-shape oracles pass, only the read-back differential oracle flags it |
| `debug/a-main/benchmark/cases/TT-adminroute-control-001.json` | Seed case — stratum 1 NEGATIVE control: same input, fault off; the read-back oracle must not fire (per-case specificity check) |
| `debug/a-main/benchmark/cases/bookinfo-ratings-benign-001.json` | Seed case — stratum 2 NEGATIVE benign trap: Bookinfo reviews→ratings designed degradation; the naive span-error oracle false-positives, the MIST target is no_flag (the A1 precision/FP test) |

### `debug/a-main/archive-2026-06-01/`

| Path | Description |
|------|-------------|
| `debug/a-main/archive-2026-06-01/DISPOSITION-2026-06-01.md` | Reviewer per-comment accept/partial/reject dispositions on ROADMAP-EXEC with grep evidence (Chinese); flags ResponseEnvelope misdescription |
| `debug/a-main/archive-2026-06-01/PLAN.md` | A-main executable action plan (Chinese): two load-bearing probes plus five grounding fixes and dated status updates |
| `debug/a-main/archive-2026-06-01/ROADMAP-EXEC.md` | A-main execution roadmap v2 (Chinese): four parallel tracks toward ISSTA tool-demo and the main-track A-conference paper |
| `debug/a-main/archive-2026-06-01/VERDICT-2026-06-01.md` | Feasibility verdict (Chinese): HiddenDownstreamFailure detector is real with live evidence; paper must rest on the study/framing leg |
| `debug/a-main/archive-2026-06-01/probe-attribution.md` | Probe of the intent-conditioned attribution mechanism (Chinese): TARGET_REJECTION=0 is a code limit; attribution cannot be load-bearing |
| `debug/a-main/archive-2026-06-01/probe-wildbugs.md` | Probe (Chinese) for a wild commit-history corpus of swallowed-downstream bugs: none reproducible, fall back to real-outage plus cited prevalence |

### `debug/a-rank-fixes/`

| Path | Description |
|------|-------------|
| `debug/a-rank-fixes/VALIDATION-2026-06-10.md` | Live and offline validation of four deferred A-rank fixes (query params, Phase 3.5 dedup, etc.) on Sock Shop; mist-core suite 328 green |

### `debug/flow/`

| Path | Description |
|------|-------------|
| `debug/flow/MIST_FLOW.md` | Code-verified end-to-end flow map of the MIST test tool (single-phase, two-phase, oracle), anchored to file:line across both modes |

### `debug/generalization/`

| Path | Description |
|------|-------------|
| `debug/generalization/PLAN.md` | Plan to make MIST run on an arbitrary SUT with no hand-fixing; promotes Bookinfo hand-fixes into code (6 blockers plus best-effort) |

### `debug/generation_generalization/`

| Path | Description |
|------|-------------|
| `debug/generation_generalization/PLAN.md` | Draft plan to decouple trace-to-scenario-to-variant generation from train-ticket structure via endpoint-based fallback matching |

### `debug/grounding/`

| Path | Description |
|------|-------------|
| `debug/grounding/producer-ranking-and-two-phase.md` | Plan to fix producer-ranking validity (wrong producer rewarded) and complete the two-phase pool-validation flow |

### `debug/hidden_downstream/`

| Path | Description |
|------|-------------|
| `debug/hidden_downstream/PLAN.md` | Code-modification plan to make HiddenDownstreamFailure a defensible label-free intent-conditioned oracle, plus a live TrainTicket mutant demo |

### `debug/inputs/`

| Path | Description |
|------|-------------|
| `debug/inputs/.gitignore` | Git ignore rule excluding the scripts/__pycache__ bytecode directory. |
| `debug/inputs/README.md` | Top-level guide to the RESTest parameter input-generation audit (quality framework, bug audit, scripts, measurements). |
| `debug/inputs/dataflow-map.md` | End-to-end call-graph and state-flow map of input generation with file:line citations into the source. |
| `debug/inputs/input-quality-measurement-framework.md` | Defines the ten input-only metrics D1-D10 across five families with 37 citations, protocol, and KPI thresholds. |
| `debug/inputs/microservice-input-quality-research.md` | Field survey behind the framework establishing the input-vs-tool taxonomy and microservice-specific quality dimensions. |
| `debug/inputs/pipeline-bug-audit.md` | 27-finding evidence-backed bug audit of the input-generation pipeline with a fix-status table and file:line citations. |

### `debug/inputs/bugfixes/`

| Path | Description |
|------|-------------|
| `debug/inputs/bugfixes/2026-05-05-critical-bugfixes.md` | Writeup of eleven critical fixes (C1-C11) from the input-quality D1-D10 and Smart Input Fetch audits. |

### `debug/inputs/measurements/`

| Path | Description |
|------|-------------|
| `debug/inputs/measurements/.gitignore` | Commented-out gitignore template for the per-run measurement output directory. |

### `debug/inputs/measurements/TrainTicketTwoStageTest_1777065076883/`

| Path | Description |
|------|-------------|
| `debug/inputs/measurements/TrainTicketTwoStageTest_1777065076883/d1_per_param.csv` | D1 schema-conformance aggregated per (operation, parameter) with total, valid, invalid, and SCR. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1777065076883/d1_per_row.csv` | D1 per-row schema-conformance results, one row per (operation, parameter, value) with valid flag and error. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1777065076883/d1_summary.json` | D1 Schema Conformance Rate summary with overall SCR, threshold pass, and breakdown by test_kind. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1777065076883/d2_summary.json` | D2 IPD-Satisfaction Rate summary; N/A here since TrainTicket OAS declares no IDL rules. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1777065076883/d3_per_param.csv` | D3 LLM hallucination aggregated per parameter with total, hallucinated, abstained, and LHR. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1777065076883/d3_per_row.csv` | D3 per-row LLM hallucination results, one row per LLM (prompt-constraint, emitted value) with status and violations. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1777065076883/d3_summary.json` | D3 LLM Hallucination Rate summary with scored categories, counts, and overall LHR. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1777065076883/inputs.csv` | Mined (operation, parameter, value) triples from this run's generated test files (mine_test_inputs output). |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1777065076883/llm_pairs.csv` | Mined LLM (prompt-constraint, emitted value) pairs from the LLM communication log (mine_llm_log output). |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1777065076883/report.md` | Aggregated markdown report for this run; earliest run, covering only D1-D3. |

### `debug/inputs/measurements/TrainTicketTwoStageTest_1777348134277/`

| Path | Description |
|------|-------------|
| `debug/inputs/measurements/TrainTicketTwoStageTest_1777348134277/d1_per_param.csv` | D1 schema-conformance aggregated per (operation, parameter) with total, valid, invalid, and SCR. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1777348134277/d1_per_row.csv` | D1 per-row schema-conformance results, one row per (operation, parameter, value) with valid flag and error. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1777348134277/d1_summary.json` | D1 Schema Conformance Rate summary with overall SCR, threshold pass, and breakdown by test_kind. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1777348134277/d2_summary.json` | D2 IPD-Satisfaction Rate summary; N/A here since TrainTicket OAS declares no IDL rules. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1777348134277/d3_per_param.csv` | D3 LLM hallucination aggregated per parameter with total, hallucinated, abstained, and LHR. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1777348134277/d3_per_row.csv` | D3 per-row LLM hallucination results, one row per LLM (prompt-constraint, emitted value) with status and violations. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1777348134277/d3_summary.json` | D3 LLM Hallucination Rate summary with scored categories, counts, and overall LHR. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1777348134277/d4_per_param.csv` | D4 Smart-Fetch Hit Rate per parameter with smart_fetch, pool_or_smart, conservative, and upper-bound SFHR. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1777348134277/d4_per_row.csv` | D4 per-row smart-fetch classification per (test_method, step, parameter, value) with provenance. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1777348134277/d4_summary.json` | D4 Smart-Fetch Hit Rate summary with counts by classification and conservative/upper bounds. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1777348134277/d5_per_param.csv` | D5 ID-Resolvability per parameter with total, resolvable, and IDR. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1777348134277/d5_per_row.csv` | D5 per-row ID-resolvability, one row per ID value with resolvable flag against the Jaeger trace export. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1777348134277/d5_summary.json` | D5 ID-Resolvability Rate summary with resolvable counts, IDR, and worst parameters. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1777348134277/d6_per_sequence.csv` | D6 Chain Resolution per multi-step sequence with steps, id_inputs, resolved, and classification. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1777348134277/d6_summary.json` | D6 Chain Resolution Rate summary with sequences considered, fully/partially resolved counts, and CRR. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1777348134277/d7_per_param.csv` | D7 Realism per parameter with total, realistic, and realism score. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1777348134277/d7_per_row.csv` | D7 per-row realism, one row per NLP-typed value with realistic flag and oracle source. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1777348134277/d7_summary.json` | D7 Realism Score summary with oracle mode, entities loaded, and realistic counts. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1777348134277/inputs.csv` | Mined (operation, parameter, value) triples from this run's generated test files (mine_test_inputs output). |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1777348134277/jaeger_outputs.csv` | Flattened Jaeger trace outputs (trace, span, service, field, value) for D5 lookup (mine_jaeger output). |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1777348134277/llm_pairs.csv` | Mined LLM (prompt-constraint, emitted value) pairs from the LLM communication log (mine_llm_log output). |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1777348134277/provenance.csv` | Per (parameter, value) provenance label SMART_FETCH, LLM, SHARED_POOL, or NEGATIVE (mine_provenance output). |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1777348134277/realism_cache.json` | D7 online realism-oracle cache (empty in offline-only mode). |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1777348134277/report.md` | Aggregated markdown report for this run, covering D1-D7. |

### `debug/inputs/measurements/TrainTicketTwoStageTest_1777780533352/`

| Path | Description |
|------|-------------|
| `debug/inputs/measurements/TrainTicketTwoStageTest_1777780533352/RESULTS.md` | Pre-fix baseline results writeup for this run (dated 2026-05-02) with the D1-D10 headline table. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1777780533352/d10_per_type.csv` | D10 negative-input fault-type purity per fault label with total, pure, schema_unbounded, and purity. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1777780533352/d10_per_value.csv` | D10 per-value fault purity, one row per (operation, parameter, value) with fault_label and pure flag. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1777780533352/d10_summary.json` | D10 Negative-Input Fault-Type Purity summary with overall NIFP and per-fault breakdown. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1777780533352/d1_per_param.csv` | D1 schema-conformance aggregated per (operation, parameter) with total, valid, invalid, and SCR. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1777780533352/d1_per_row.csv` | D1 per-row schema-conformance results, one row per (operation, parameter, value) with valid flag and error. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1777780533352/d1_summary.json` | D1 Schema Conformance Rate summary with overall SCR, threshold pass, and breakdown by test_kind. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1777780533352/d2_summary.json` | D2 IPD-Satisfaction Rate summary; N/A here since TrainTicket OAS declares no IDL rules. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1777780533352/d3_per_param.csv` | D3 LLM hallucination aggregated per parameter with total, hallucinated, abstained, and LHR. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1777780533352/d3_per_row.csv` | D3 per-row LLM hallucination results, one row per LLM (prompt-constraint, emitted value) with status and violations. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1777780533352/d3_summary.json` | D3 LLM Hallucination Rate summary with scored categories, counts, and overall LHR. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1777780533352/d4_per_param.csv` | D4 Smart-Fetch Hit Rate per parameter with smart_fetch, pool_or_smart, conservative, and upper-bound SFHR. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1777780533352/d4_per_row.csv` | D4 per-row smart-fetch classification per (test_method, step, parameter, value) with provenance. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1777780533352/d4_summary.json` | D4 Smart-Fetch Hit Rate summary with counts by classification and conservative/upper bounds. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1777780533352/d5_per_param.csv` | D5 ID-Resolvability per parameter with total, resolvable, and IDR. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1777780533352/d5_per_row.csv` | D5 per-row ID-resolvability, one row per ID value with resolvable flag against the Jaeger trace export. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1777780533352/d5_summary.json` | D5 ID-Resolvability Rate summary with resolvable counts, IDR, and worst parameters. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1777780533352/d6_per_sequence.csv` | D6 Chain Resolution per multi-step sequence with steps, id_inputs, resolved, and classification. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1777780533352/d6_summary.json` | D6 Chain Resolution Rate summary with sequences considered, fully/partially resolved counts, and CRR. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1777780533352/d7_per_param.csv` | D7 Realism per parameter with total, realistic, and realism score. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1777780533352/d7_per_row.csv` | D7 per-row realism, one row per NLP-typed value with realistic flag and oracle source. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1777780533352/d7_summary.json` | D7 Realism Score summary with oracle mode, entities loaded, and realistic counts. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1777780533352/d8_per_param.csv` | D8 per-parameter Shannon entropy (raw and normalised) and Simpson diversity over the value multiset. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1777780533352/d8_summary.json` | D8 Pool Diversity summary with mean normalised Shannon entropy, mean Simpson, and threshold pass. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1777780533352/d9_per_param.csv` | D9 Equivalence-Partition Coverage per parameter with classes total, covered, EPC, and missing classes. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1777780533352/d9_summary.json` | D9 Equivalence-Partition Coverage summary with mean EPC, threshold pass, and worst pools. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1777780533352/inputs.csv` | Mined (operation, parameter, value) triples from this run's generated test files (mine_test_inputs output). |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1777780533352/jaeger_outputs.csv` | Flattened Jaeger trace outputs (trace, span, service, field, value) for D5 lookup (mine_jaeger output). |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1777780533352/llm_pairs.csv` | Mined LLM (prompt-constraint, emitted value) pairs from the LLM communication log (mine_llm_log output). |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1777780533352/provenance.csv` | Per (parameter, value) provenance label SMART_FETCH, LLM, SHARED_POOL, or NEGATIVE (mine_provenance output). |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1777780533352/realism_cache.json` | D7 online realism-oracle cache (empty in offline-only mode). |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1777780533352/report.md` | Aggregated markdown report for this full D1-D10 run (generated 2026-05-05). |

### `debug/inputs/measurements/TrainTicketTwoStageTest_1778001841606/`

| Path | Description |
|------|-------------|
| `debug/inputs/measurements/TrainTicketTwoStageTest_1778001841606/d10_per_type.csv` | D10 negative-input fault-type purity per fault label with total, pure, schema_unbounded, and purity. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1778001841606/d10_per_value.csv` | D10 per-value fault purity, one row per (operation, parameter, value) with fault_label and pure flag. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1778001841606/d10_summary.json` | D10 Negative-Input Fault-Type Purity summary with overall NIFP and per-fault breakdown. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1778001841606/d1_per_param.csv` | D1 schema-conformance aggregated per (operation, parameter) with total, valid, invalid, and SCR. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1778001841606/d1_per_row.csv` | D1 per-row schema-conformance results, one row per (operation, parameter, value) with valid flag and error. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1778001841606/d1_summary.json` | D1 Schema Conformance Rate summary with overall SCR, threshold pass, and breakdown by test_kind. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1778001841606/d2_summary.json` | D2 IPD-Satisfaction Rate summary; N/A here since TrainTicket OAS declares no IDL rules. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1778001841606/d3_per_param.csv` | D3 LLM hallucination aggregated per parameter with total, hallucinated, abstained, and LHR. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1778001841606/d3_per_row.csv` | D3 per-row LLM hallucination results, one row per LLM (prompt-constraint, emitted value) with status and violations. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1778001841606/d3_summary.json` | D3 LLM Hallucination Rate summary with scored categories, counts, and overall LHR. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1778001841606/d4_per_param.csv` | D4 Smart-Fetch Hit Rate per parameter with smart_fetch, pool_or_smart, conservative, and upper-bound SFHR. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1778001841606/d4_per_row.csv` | D4 per-row smart-fetch classification per (test_method, step, parameter, value) with provenance. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1778001841606/d4_summary.json` | D4 Smart-Fetch Hit Rate summary with counts by classification and conservative/upper bounds. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1778001841606/d5_per_param.csv` | D5 ID-Resolvability per parameter with total, resolvable, and IDR. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1778001841606/d5_per_row.csv` | D5 per-row ID-resolvability, one row per ID value with resolvable flag against the Jaeger trace export. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1778001841606/d5_summary.json` | D5 ID-Resolvability Rate summary with resolvable counts, IDR, and worst parameters. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1778001841606/d6_per_sequence.csv` | D6 Chain Resolution per multi-step sequence with steps, id_inputs, resolved, and classification. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1778001841606/d6_summary.json` | D6 Chain Resolution Rate summary with sequences considered, fully/partially resolved counts, and CRR. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1778001841606/d7_per_param.csv` | D7 Realism per parameter with total, realistic, and realism score. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1778001841606/d7_per_row.csv` | D7 per-row realism, one row per NLP-typed value with realistic flag and oracle source. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1778001841606/d7_summary.json` | D7 Realism Score summary with oracle mode, entities loaded, and realistic counts. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1778001841606/d8_per_param.csv` | D8 per-parameter Shannon entropy (raw and normalised) and Simpson diversity over the value multiset. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1778001841606/d8_summary.json` | D8 Pool Diversity summary with mean normalised Shannon entropy, mean Simpson, and threshold pass. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1778001841606/d9_per_param.csv` | D9 Equivalence-Partition Coverage per parameter with classes total, covered, EPC, and missing classes. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1778001841606/d9_summary.json` | D9 Equivalence-Partition Coverage summary with mean EPC, threshold pass, and worst pools. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1778001841606/inputs.csv` | Mined (operation, parameter, value) triples from this run's generated test files (mine_test_inputs output). |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1778001841606/jaeger_outputs.csv` | Flattened Jaeger trace outputs (trace, span, service, field, value) for D5 lookup (mine_jaeger output). |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1778001841606/llm_pairs.csv` | Mined LLM (prompt-constraint, emitted value) pairs from the LLM communication log (mine_llm_log output). |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1778001841606/provenance.csv` | Per (parameter, value) provenance label SMART_FETCH, LLM, SHARED_POOL, or NEGATIVE (mine_provenance output). |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1778001841606/realism_cache.json` | D7 online realism-oracle cache (empty in offline-only mode). |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1778001841606/report.md` | Aggregated markdown report for this full D1-D10 run. |

### `debug/inputs/measurements/TrainTicketTwoStageTest_1778039778981/`

| Path | Description |
|------|-------------|
| `debug/inputs/measurements/TrainTicketTwoStageTest_1778039778981/d10_per_type.csv` | D10 negative-input fault-type purity per fault label with total, pure, schema_unbounded, and purity. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1778039778981/d10_per_value.csv` | D10 per-value fault purity, one row per (operation, parameter, value) with fault_label and pure flag. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1778039778981/d10_summary.json` | D10 Negative-Input Fault-Type Purity summary with overall NIFP and per-fault breakdown. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1778039778981/d1_per_param.csv` | D1 schema-conformance aggregated per (operation, parameter) with total, valid, invalid, and SCR. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1778039778981/d1_per_row.csv` | D1 per-row schema-conformance results, one row per (operation, parameter, value) with valid flag and error. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1778039778981/d1_summary.json` | D1 Schema Conformance Rate summary with overall SCR, threshold pass, and breakdown by test_kind. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1778039778981/d2_summary.json` | D2 IPD-Satisfaction Rate summary; N/A here since TrainTicket OAS declares no IDL rules. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1778039778981/d3_per_param.csv` | D3 LLM hallucination aggregated per parameter with total, hallucinated, abstained, and LHR. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1778039778981/d3_per_row.csv` | D3 per-row LLM hallucination results, one row per LLM (prompt-constraint, emitted value) with status and violations. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1778039778981/d3_summary.json` | D3 LLM Hallucination Rate summary with scored categories, counts, and overall LHR. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1778039778981/d4_per_param.csv` | D4 Smart-Fetch Hit Rate per parameter with smart_fetch, pool_or_smart, conservative, and upper-bound SFHR. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1778039778981/d4_per_row.csv` | D4 per-row smart-fetch classification per (test_method, step, parameter, value) with provenance. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1778039778981/d4_summary.json` | D4 Smart-Fetch Hit Rate summary with counts by classification and conservative/upper bounds. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1778039778981/d5_per_param.csv` | D5 ID-Resolvability per parameter with total, resolvable, and IDR. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1778039778981/d5_per_row.csv` | D5 per-row ID-resolvability, one row per ID value with resolvable flag against the Jaeger trace export. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1778039778981/d5_summary.json` | D5 ID-Resolvability Rate summary with resolvable counts, IDR, and worst parameters. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1778039778981/d6_per_sequence.csv` | D6 Chain Resolution per multi-step sequence with steps, id_inputs, resolved, and classification. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1778039778981/d6_summary.json` | D6 Chain Resolution Rate summary with sequences considered, fully/partially resolved counts, and CRR. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1778039778981/d7_per_param.csv` | D7 Realism per parameter with total, realistic, and realism score. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1778039778981/d7_per_row.csv` | D7 per-row realism, one row per NLP-typed value with realistic flag and oracle source. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1778039778981/d7_summary.json` | D7 Realism Score summary with oracle mode, entities loaded, and realistic counts. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1778039778981/d8_per_param.csv` | D8 per-parameter Shannon entropy (raw and normalised) and Simpson diversity over the value multiset. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1778039778981/d8_summary.json` | D8 Pool Diversity summary with mean normalised Shannon entropy, mean Simpson, and threshold pass. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1778039778981/d9_per_param.csv` | D9 Equivalence-Partition Coverage per parameter with classes total, covered, EPC, and missing classes. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1778039778981/d9_summary.json` | D9 Equivalence-Partition Coverage summary with mean EPC, threshold pass, and worst pools. |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1778039778981/inputs.csv` | Mined (operation, parameter, value) triples from this run's generated test files (mine_test_inputs output). |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1778039778981/jaeger_outputs.csv` | Flattened Jaeger trace outputs (trace, span, service, field, value) for D5 lookup (mine_jaeger output). |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1778039778981/llm_pairs.csv` | Mined LLM (prompt-constraint, emitted value) pairs from the LLM communication log (mine_llm_log output). |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1778039778981/provenance.csv` | Per (parameter, value) provenance label SMART_FETCH, LLM, SHARED_POOL, or NEGATIVE (mine_provenance output). |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1778039778981/realism_cache.json` | D7 online realism-oracle cache (empty in offline-only mode). |
| `debug/inputs/measurements/TrainTicketTwoStageTest_1778039778981/report.md` | Aggregated markdown report for this full D1-D10 run. |

### `debug/inputs/scripts/`

| Path | Description |
|------|-------------|
| `debug/inputs/scripts/README.md` | Index and quick-start for the offline D1-D10 metric scripts (mine, validate, report stages). |
| `debug/inputs/scripts/REVIEW.md` | Code review (2026-04-30) of the D4-D7 metric scripts listing material counting/encoding bugs. |
| `debug/inputs/scripts/REVIEW_EXPORTER.md` | Code review (2026-04-26) of the Jaeger trace exporter for D5, flagging 4xx-retry and malformed-input defects. |
| `debug/inputs/scripts/curated_idl.example.yaml` | Template of optional hand-written inter-parameter-dependency rules to enable D2 (copy to curated_idl.yaml). |
| `debug/inputs/scripts/export_jaeger_traces.py` | Optional exporter pulling post-execution Jaeger traces via HTTP into a JSON file mine_jaeger can read (D5). |
| `debug/inputs/scripts/generate_report.py` | Stage 3 aggregator rolling the per-metric JSON summaries up into a single markdown report against thresholds. |
| `debug/inputs/scripts/id_helpers.py` | ID-detection and stem-extraction helpers (boundary-aware regex) shared by D4, D5, and D6. |
| `debug/inputs/scripts/mine_jaeger.py` | Stage 1d miner flattening a Jaeger trace export into (span, service, field, value) outputs for D5. |
| `debug/inputs/scripts/mine_llm_log.py` | Stage 1b miner extracting LLM (prompt-constraint, emitted value) pairs from the LLM communication log for D3. |
| `debug/inputs/scripts/mine_provenance.py` | Stage 1c miner labelling each (parameter, value) by its first source (SMART_FETCH, LLM, SHARED_POOL, NEGATIVE) for D4. |
| `debug/inputs/scripts/mine_test_inputs.py` | Stage 1a miner walking generated Flow_Scenario test files to extract every (operation, parameter, value) triple. |
| `debug/inputs/scripts/oas_helpers.py` | Lightweight OpenAPI 3.0 schema lookup and JSON-Schema validation helpers used by D1. |
| `debug/inputs/scripts/path_helpers.py` | Path-segment producer-stem extraction used by D6 Chain Resolution Rate. |
| `debug/inputs/scripts/realism_entities.txt` | Curated offline entity oracle (~732 entries) of cities, train hubs, and names for D7. |
| `debug/inputs/scripts/realism_oracle.py` | D7 entity-matching engine (offline curated list plus optional cached Wikidata online fallback). |
| `debug/inputs/scripts/requirements.txt` | Python dependency pins for the metric scripts (jsonschema, PyYAML). |
| `debug/inputs/scripts/run_metrics.sh` | One-shot bash orchestrator running the full D1-D10 mine/validate/report pipeline on a run. |
| `debug/inputs/scripts/run_tests.py` | Stdlib-only test runner discovering and executing tests/test_*.py for the metric scripts. |
| `debug/inputs/scripts/validate_d1.py` | D1 validator computing Schema Conformance Rate of inputs against the OpenAPI spec. |
| `debug/inputs/scripts/validate_d10.py` | D10 validator computing Negative-Input Fault-Type Purity per fault label. |
| `debug/inputs/scripts/validate_d2.py` | D2 validator computing IDL inter-parameter-dependency satisfaction (N/A when no IDL declared). |
| `debug/inputs/scripts/validate_d3.py` | D3 validator computing LLM Hallucination Rate against in-prompt constraints. |
| `debug/inputs/scripts/validate_d4.py` | D4 validator computing Smart-Fetch Hit Rate by joining inputs with provenance. |
| `debug/inputs/scripts/validate_d5.py` | D5 validator computing ID-Resolvability Rate against the Jaeger trace export. |
| `debug/inputs/scripts/validate_d6.py` | D6 validator computing Chain Resolution Rate across multi-step sequences. |
| `debug/inputs/scripts/validate_d7.py` | D7 validator computing Realism Score against the entity oracle (offline by default). |
| `debug/inputs/scripts/validate_d8.py` | D8 validator computing per-parameter Shannon entropy and Simpson diversity. |
| `debug/inputs/scripts/validate_d9.py` | D9 validator computing Equivalence-Partition Coverage from OAS-derived classes. |

### `debug/inputs/scripts/tests/`

| Path | Description |
|------|-------------|
| `debug/inputs/scripts/tests/__init__.py` | Empty package marker for the metric-scripts test package. |
| `debug/inputs/scripts/tests/test_d1_d7_fallback.py` | Tests for the FALLBACK_* exclusion in D1/D7 and the tightened D7 NLP-parameter detector. |
| `debug/inputs/scripts/tests/test_d8_d9_d10.py` | Sanity tests for the D8 entropy, D9 EPC, and D10 NIFP helper functions. |
| `debug/inputs/scripts/tests/test_export_jaeger.py` | Tests for export_jaeger_traces, the Jaeger HTTP-API exporter for D5. |
| `debug/inputs/scripts/tests/test_id_helpers.py` | Tests for id_helpers ID-detection and stem-extraction functions. |
| `debug/inputs/scripts/tests/test_path_helpers.py` | Tests for path_helpers producer-stem extraction used by D6. |
| `debug/inputs/scripts/tests/test_provenance.py` | Tests for mine_provenance.classify_line exec-log line classification. |
| `debug/inputs/scripts/tests/test_realism.py` | Tests for realism_oracle D7 entity matching (offline, online, and cache paths). |
| `debug/inputs/scripts/tests/test_validators_e2e.py` | End-to-end smoke tests running validators over tiny synthetic fixtures and asserting summary outputs. |

### `debug/inputs/scripts/tests/__pycache__/`

| Path | Description |
|------|-------------|
| `debug/inputs/scripts/tests/__pycache__/__init__.cpython-312.pyc` | Compiled Python 3.12 bytecode cache of tests/__init__.py. |
| `debug/inputs/scripts/tests/__pycache__/test_d1_d7_fallback.cpython-312.pyc` | Compiled Python 3.12 bytecode cache of test_d1_d7_fallback.py. |
| `debug/inputs/scripts/tests/__pycache__/test_d8_d9_d10.cpython-312.pyc` | Compiled Python 3.12 bytecode cache of test_d8_d9_d10.py. |
| `debug/inputs/scripts/tests/__pycache__/test_export_jaeger.cpython-312.pyc` | Compiled Python 3.12 bytecode cache of test_export_jaeger.py. |
| `debug/inputs/scripts/tests/__pycache__/test_id_helpers.cpython-312.pyc` | Compiled Python 3.12 bytecode cache of test_id_helpers.py. |
| `debug/inputs/scripts/tests/__pycache__/test_path_helpers.cpython-312.pyc` | Compiled Python 3.12 bytecode cache of test_path_helpers.py. |
| `debug/inputs/scripts/tests/__pycache__/test_provenance.cpython-312.pyc` | Compiled Python 3.12 bytecode cache of test_provenance.py. |
| `debug/inputs/scripts/tests/__pycache__/test_realism.cpython-312.pyc` | Compiled Python 3.12 bytecode cache of test_realism.py. |
| `debug/inputs/scripts/tests/__pycache__/test_validators_e2e.cpython-312.pyc` | Compiled Python 3.12 bytecode cache of test_validators_e2e.py. |

### `debug/inputs/smart_fetch/`

| Path | Description |
|------|-------------|
| `debug/inputs/smart_fetch/README.md` | Index of the Smart Input Fetch audit folder summarizing the three-phase audit, fixes, and registry KPIs. |
| `debug/inputs/smart_fetch/dataflow-map.md` | Implementation-reality call-graph for the smart-fetch subsystem with file:line citations and discrepancies. |
| `debug/inputs/smart_fetch/execution-summary.md` | Consolidated record of every change applied to smart-fetch during the audit cycle with compile status. |
| `debug/inputs/smart_fetch/refinement-plan.md` | Prioritized 8-stream work plan distilled from the smart-fetch bug audit. |
| `debug/inputs/smart_fetch/smart-fetch-bug-audit.md` | Original 40-finding Smart Input Fetch bug audit with code excerpts, impact, evidence, and fix status. |
| `debug/inputs/smart_fetch/smart-fetch-quality-framework.md` | Smart-fetch-specific quality framework (S1-S7 metrics across 7 families, 47 citations, KPI thresholds). |
| `debug/inputs/smart_fetch/verification-final.md` | Independent final verification confirming each smart-fetch plan item is implemented with file:line evidence. |

### `debug/inputs/smart_fetch/scripts/`

| Path | Description |
|------|-------------|
| `debug/inputs/smart_fetch/scripts/migrate_registry.py` | Re-runnable smart-fetch YAML registry cleanup tool dropping sentinels, fabricated endpoints, placeholders, and stale entries. |

### `debug/negative-gen/`

| Path | Description |
|------|-------------|
| `debug/negative-gen/PLAN.md` | Negative-generation strengthening plan: schema-aware negative values (Gap 3) plus IPD-violation negatives (Gap 1); Gap 3 implemented and verified |

### `debug/negative_test/`

| Path | Description |
|------|-------------|
| `debug/negative_test/FIXES.md` | Phase 0/1/2 audit fix-list against inject-detection HEAD; eight findings on attribution invariant, kill-switch, and caching |
| `debug/negative_test/README.md` | Negative-test capability phase plan; Run 22 closed the 7-to-10 fault-detection gap, outlines remaining A-conference concerns |
| `debug/negative_test/VERIFICATION_PROMPT.md` | Adversarial independent-verification brief for the negative-test work line (7/10 to 10/10 claim plus three follow-up phases) |
| `debug/negative_test/phase_0_oracle_credit.md` | Phase 0: surface the thousands of existing TraceShapeOracle violations as credited anomalies in the fault-detection report |
| `debug/negative_test/phase_1_two_phase_flow.md` | Phase 1: two-phase flow with pool validation (positive baseline, mark verified-valid entries, then negatives use only valid values) |
| `debug/negative_test/phase_2_trace_attribution.md` | Phase 2: SUT-agnostic leaf-error-span trace attribution deciding if a rejection was caused by the targeted invalid parameter |

### `debug/negative_test/runs/`

| Path | Description |
|------|-------------|
| `debug/negative_test/runs/run21b-fault-detection-7of10.txt` | Fault-detection summary report from run 21b showing 7 of 10 injected faults detected (70%) |
| `debug/negative_test/runs/run22-fault-detection-10of10.txt` | Fault-detection summary report from run 22 showing 10 of 10 injected faults detected (100%) |

### `debug/oracle_arch/`

| Path | Description |
|------|-------------|
| `debug/oracle_arch/CURRENT_ARCHITECTURE.md` | Read-only as-is map of the existing negative-test to oracle code path, file:line evidenced on inject-detection |
| `debug/oracle_arch/DISPOSITIONS.md` | Reviewer dispositions for the oracle redesign; reframes thesis to trace-backed disambiguation, notes train-ticket has zero hidden-failure instances |
| `debug/oracle_arch/README.md` | Oracle architecture redesign toward a label-free, intent-aware trace oracle for an A-conference contribution |
| `debug/oracle_arch/TARGET_ARCHITECTURE.md` | Target oracle design: an intent-aware, outcome-aware trace oracle that judges whether the SUT handled bad input correctly |

### `debug/reproduce/`

| Path | Description |
|------|-------------|
| `debug/reproduce/README.md` | Reproducibility audit of the ISSTA 2026 tool-demo artifact; re-runs each README/REPRODUCE claim from a fresh clone with a verification matrix |

### `debug/reproduce/evidence/`

| Path | Description |
|------|-------------|
| `debug/reproduce/evidence/bookinfo-deploy-fail-kubeadm.log` | Log of a failed kubeadm control-plane init (wait-control-plane phase error stack trace) |
| `debug/reproduce/evidence/bookinfo-deploy-fail-kubeconfig.log` | Log of a kubectl failure connecting to localhost:8080 (connection refused) during a Bookinfo deploy attempt |
| `debug/reproduce/evidence/bookinfo-deploy-fresh-rerun.log` | Log of a fresh kind cluster (kind-mist-repro) Bookinfo deploy rerun |
| `debug/reproduce/evidence/bookinfo-e2e-matrix.log` | Live 4-case oracle matrix run on Bookinfo; the /reviews outage fires HIDDEN_DOWNSTREAM_FAILURE on a 200 response |
| `debug/reproduce/evidence/noexec-llm-cache-misses.log` | Log tail of network-unreachable warnings hitting TrainTicket endpoints during an offline noexec run |
| `debug/reproduce/evidence/noexec-run1-tail.log` | Progress-bar tail of a noexec generation run writing 123 scenario files |
| `debug/reproduce/evidence/offline-oracle.log` | Offline OracleCheck output: Bookinfo HIDDEN_DOWNSTREAM_FAILURE fires at ERROR while the response-level oracle passes; Online Boutique gRPC traces |
| `debug/reproduce/evidence/run1.sums` | SHA-256 list of generated Flow_Scenario_*.java files from seed-42 run 1 (byte-identical generation check) |
| `debug/reproduce/evidence/run2.sums` | SHA-256 list of generated Flow_Scenario_*.java files from seed-42 run 2; identical to run1 (123/123) |
| `debug/reproduce/evidence/tt-local-deploy-nonconvergence.log` | Log of a local one-command TrainTicket compose deploy that did not converge on an 8-core host (container-state counts) |

### `debug/reviewer-remediation/`

| Path | Description |
|------|-------------|
| `debug/reviewer-remediation/EXPERIMENTS.md` | Real-run reviewer-remediation experiments reproducing the paper's Boutique HiddenDownstreamFailure fire counts (7/12, 24/40, healthy 0) |
| `debug/reviewer-remediation/PLAN.md` | Reviewer-remediation campaign for the ISSTA 2026 tool demo: per-finding dispositions, plan, and evidence from three cold reviews |

### `debug/subproject/`

| Path | Description |
|------|-------------|
| `debug/subproject/README.md` | Index of three master-student sub-project briefs (SP1-SP3) that close the paper's named evaluation gaps |
| `debug/subproject/SP1_MULTI_SUT_DEPLOYMENT.md` | Sub-project brief SP1: deploy at least three microservice SUTs and capture MIST-ingestible Jaeger trace corpora |
| `debug/subproject/SP2_BASELINE_HARNESS.md` | Sub-project brief SP2: containerise at least three competing REST testers behind one uniform driver for baseline reproducibility |
| `debug/subproject/SP3_REAL_BUG_BENCHMARK.md` | Sub-project brief SP3: curate 20-30 real historical bugs into a one-command replay benchmark for fault realism |

### `debug/tool-demo/`

| Path | Description |
|------|-------------|
| `debug/tool-demo/ISSTA-SUBMISSION-READINESS-2026-06-01.md` | ISSTA tool-demo submission-readiness review (Chinese): near-ready, lists reproducibility overclaim and integrity must-fixes before submission |

### `debug/uiux/`

| Path | Description |
|------|-------------|
| `debug/uiux/findings-discoverability.md` | UI/UX assessment of whether MIST's hidden-downstream/soft-error findings are discoverable; WARN-severity findings are under-surfaced |

## docs — design & evidence

Architecture/design markdown, implementation summaries, decision records, plans, screencast notes, Allure reports, images, and main-contribution evidence traces.

### `docs/`

| Path | Description |
|------|-------------|
| `docs/Allure.png` | Screenshot of an Allure test report rendered for MIST/RESTest runs |
| `docs/Approach8.png` | Approach overview diagram of the RESTest test-generation pipeline |
| `docs/Enhancer-Diagnostic-Report.md` | Diagnostic report: the Test Case Enhancer works architecturally but wrongly tries to fix negative tests exposing validation bugs |
| `docs/FAULT_DETECTION_IMPLEMENTATION.md` | Implementation notes for fault-detection tracking in MST mode (FaultDetectionTracker, generated-test instrumentation, coverage reports) |
| `docs/MAIN_CONTRIBUTION.md` | Author-facing navigation doc (Chinese) re-positioning MIST's headline label-free HiddenDownstreamFailure trace oracle |
| `docs/RESTest_architecture_v4.png` | Architecture diagram (v4) of the RESTest test-generation pipeline |
| `docs/RESTest_v3.png` | Architecture/pipeline diagram (v3) of RESTest |
| `docs/SMART_INPUT_FETCHING_IMPLEMENTATION_SUMMARY.md` | Summary of the implemented Smart Input Fetching system components, configuration, and integration into RESTest |
| `docs/SMART_INPUT_FETCHING_README.md` | Overview README of Smart Input Fetching, which fetches realistic test values from live APIs instead of random data |
| `docs/SemanticDependencyRegistry-Architecture.md` | Architecture doc for SemanticDependencyRegistry, a compile-time OpenAPI schema-inference engine mapping consumer params to producer APIs |
| `docs/Smart-Fetch-Process.md` | End-to-end process documentation of Smart Fetch: live-value fetching, registry learning, LLM fallback, and MST integration |
| `docs/adaptive-strategy-decision-phase2.md` | Decision record deferring the Layer-4 live IdempotencyProbe of the adaptive per-endpoint strategy indefinitely |
| `docs/adaptive-strategy-research.md` | Research/design proposal for an adaptive per-endpoint strategy (dedup, K_DEDUP_EXHAUSTED) replacing rigid global knobs |
| `docs/img.png` | Embedded documentation image (generic figure) |
| `docs/play_video.png` | Play-button thumbnail image linking to a demo video |
| `docs/restest-video.png` | Thumbnail image for the RESTest demo video |
| `docs/showcase_v2.png` | Showcase figure (v2) of the project/repository data |
| `docs/sut-blocker-2026-05-22.md` | Record of a train-ticket SUT blocker: two endpoints return deterministic HTTP 500 to authenticated requests (not a MIST defect) |
| `docs/trace-fetch-validation.md` | Validation note for W3C traceparent-based per-step trace fetching from Jaeger by exact minted trace ID |
| `docs/youtube-dep.png` | Thumbnail/dependency image linking to a YouTube demo video |

### `docs/Allure Reports/`

| Path | Description |
|------|-------------|
| `docs/Allure Reports/Behaviors.png` | Screenshot of the Allure report Behaviors tab |
| `docs/Allure Reports/Categories.png` | Screenshot of the Allure report Categories (defect categories) tab |
| `docs/Allure Reports/Graphs.png` | Screenshot of the Allure report Graphs tab (status and severity charts) |
| `docs/Allure Reports/Overview.png` | Screenshot of the Allure report Overview dashboard |
| `docs/Allure Reports/Packages.png` | Screenshot of the Allure report Packages tab |
| `docs/Allure Reports/Suites.png` | Screenshot of the Allure report Suites tab |
| `docs/Allure Reports/Timeline.png` | Screenshot of the Allure report Timeline tab |

### `docs/main-contribution/`

| Path | Description |
|------|-------------|
| `docs/main-contribution/A_MAIN_ROADMAP.md` | Roadmap for the A-main contribution: a label-free distributed-trace oracle for swallowed cross-service failures, plus evidence plan |
| `docs/main-contribution/RESEARCH_a-conference-viability.md` | Deep-research verdict assessing whether the intent-conditioned trace oracle is an A-conference-viable main contribution |
| `docs/main-contribution/RESEARCH_candidate-suts.md` | Research verdict identifying SUTs (Bookinfo, Online Boutique) that naturally exhibit hidden-downstream failures for non-circular evidence |
| `docs/main-contribution/RESEARCH_main-contribution-decision.md` | Deep-research decision picking MIST's single main line: LLM negative-input generation plus a label-free hidden-downstream trace oracle |
| `docs/main-contribution/research-raw-a-conf-viability.json` | Raw deep-research harness log (104 agents) backing the A-conference-viability verdict |
| `docs/main-contribution/research-raw-candidate-suts.json` | Raw deep-research harness log (108 agents) backing the candidate-SUTs verdict |
| `docs/main-contribution/research-raw-main-line-decision.json` | Raw deep-research harness log (104 agents) backing the main-contribution decision verdict |

### `docs/main-contribution/evidence/`

| Path | Description |
|------|-------------|
| `docs/main-contribution/evidence/bookinfo_e2e_pipeline.md` | Evidence: full MIST pipeline generates, executes, and trace-oracle-detects a hidden downstream failure on live Istio Bookinfo |
| `docs/main-contribution/evidence/bookinfo_hidden_downstream.md` | Evidence: MIST's trace oracle catches a swallowed ratings-outage downstream failure on third-party Istio Bookinfo (non-circular) |
| `docs/main-contribution/evidence/boutique_e2e_pipeline.md` | Evidence: 2nd SUT Online Boutique (gRPC) hidden downstream failure via a swallowed adservice UNAVAILABLE seen only on the trace |
| `docs/main-contribution/evidence/boutique_run_fault-detection-summary.txt` | Fault-detection summary report from a Boutique hidden-downstream run (38 test cases) |
| `docs/main-contribution/evidence/fault-detection-summary-silent_accept_demo.txt` | Fault-detection summary report from the silent-acceptance demo run (277 test cases) |
| `docs/main-contribution/evidence/mutant-INSUFFICIENT_STATIONS.diff` | Git diff of the INSUFFICIENT_STATIONS silent-acceptance mutant on train-ticket adminroute for the trace-oracle demo |
| `docs/main-contribution/evidence/responseenvelope_live_3case.txt` | Transcript of 3 live DeepSeek ResponseEnvelope classifier calls (soft error, success, false-positive on ambiguous body) |
| `docs/main-contribution/evidence/responseenvelope_live_softerror.txt` | Live evidence transcript: the LLM-backed ResponseEnvelope flips a train-ticket soft-error 200 to FAIL |
| `docs/main-contribution/evidence/silent_acceptance_demo.md` | Historical/superseded evidence: intent-aware trace oracle detects silent acceptance via a controlled train-ticket mutant |
| `docs/main-contribution/evidence/trainticket_10fault_fault-detection-10of10.txt` | Fault-detection summary showing 10 of 10 injected train-ticket faults detected (15036 test cases) |

### `docs/main-contribution/evidence/bookinfo_e2e_traces/`

| Path | Description |
|------|-------------|
| `docs/main-contribution/evidence/bookinfo_e2e_traces/healthy_reviews_control.json` | Captured Jaeger trace of a healthy Bookinfo reviews request (all spans HTTP 200) as control |
| `docs/main-contribution/evidence/bookinfo_e2e_traces/masked_reviews_ratings_outage.json` | Captured Jaeger trace where the ratings span returns 503/otel ERROR but is swallowed and productpage returns 200 |

### `docs/main-contribution/evidence/bookinfo_inprocess_e2e/`

| Path | Description |
|------|-------------|
| `docs/main-contribution/evidence/bookinfo_inprocess_e2e/README.md` | Notes for the in-process Bookinfo run where MIST generated+executed 166 tests and HiddenDownstreamFailure fired at ERROR on /reviews |
| `docs/main-contribution/evidence/bookinfo_inprocess_e2e/bookinfo_inprocess_fault-detection-summary.txt` | Fault-detection summary report from the in-process Bookinfo run (166 test cases) |
| `docs/main-contribution/evidence/bookinfo_inprocess_e2e/sample_hidden_downstream_finding.txt` | Sample HiddenDownstreamFailure finding (ERROR) for the swallowed reviews-to-ratings 503 on Bookinfo |

### `docs/main-contribution/evidence/boutique_e2e_traces/`

| Path | Description |
|------|-------------|
| `docs/main-contribution/evidence/boutique_e2e_traces/boutique_adservice_outage.json` | Captured Jaeger trace of the Online Boutique frontend under an adservice outage (swallowed gRPC error) |
| `docs/main-contribution/evidence/boutique_e2e_traces/boutique_adservice_outage_recapture.json` | Re-captured Jaeger trace of the Boutique frontend adservice-outage scenario |
| `docs/main-contribution/evidence/boutique_e2e_traces/boutique_frontend_healthy.json` | Captured Jaeger trace of a healthy Online Boutique frontend request (control) |
| `docs/main-contribution/evidence/boutique_e2e_traces/boutique_frontend_healthy_recapture.json` | Re-captured Jaeger trace of the healthy Boutique frontend scenario |

### `docs/main-contribution/evidence/boutique_inprocess_e2e/`

| Path | Description |
|------|-------------|
| `docs/main-contribution/evidence/boutique_inprocess_e2e/README.md` | Notes for the in-process Boutique run where MIST generated+executed 579 tests and HiddenDownstreamFailure fired 7x at WARN (gRPC) |
| `docs/main-contribution/evidence/boutique_inprocess_e2e/boutique_inprocess_fault-detection-summary.txt` | Fault-detection summary report from the in-process Boutique run (579 test cases) |
| `docs/main-contribution/evidence/boutique_inprocess_e2e/sample_hidden_downstream_finding.txt` | Sample HiddenDownstreamFailure finding (WARN) for the swallowed frontend-to-adservice gRPC error on Boutique |

### `docs/main-contribution/evidence/sockshop_softerror/`

| Path | Description |
|------|-------------|
| `docs/main-contribution/evidence/sockshop_softerror/README.md` | Evidence: Sock Shop soft-error (G1) under a real catalogue-db outage, a client 200 whose body carries a server-side failure |
| `docs/main-contribution/evidence/sockshop_softerror/sockshop_catalogue_healthy.json` | Captured healthy Sock Shop GET /catalogue response body (sock product list) |
| `docs/main-contribution/evidence/sockshop_softerror/sockshop_catalogue_outage.json` | Captured Sock Shop /catalogue response body under a DB outage (database connection error) |

### `docs/mst-plans/`

| Path | Description |
|------|-------------|
| `docs/mst-plans/PATH_B_POSITIONING.md` | Internal positioning doc defending MIST's Trace Shape Oracle and Adaptive Fault Taxonomy vs AutoRestTest/LogiAgent for ICSE/FSE 2027 |
| `docs/mst-plans/PATH_B_PRIOR_ART.bib` | BibTeX entries of prior art (AutoRestTest, etc.) collected for the ICSE/FSE 2027 submission positioning |
| `docs/mst-plans/STAGE_1D_VERIFICATION.md` | Stage 1.D verification that mist.jar and restest.jar produce byte-identical scenario files under one seed, with residual gaps |
| `docs/mst-plans/phase-1a-mst-branch-map.txt` | Phase 1.A enumeration of every MST-specific block in TestGenerationAndExecution.java for extraction into MistRunner |
| `docs/mst-plans/phase-1c-mist-inventory.txt` | Phase 1.C inventory of MIST classes to move from es.us.isa.restest.* into io.mist.core.* |
| `docs/mst-plans/phase-1c-mist-to-restest-imports.txt` | Phase 1.C scan of which RESTest classes MIST classes import, defining the adapter SPI surface |

### `docs/screencast/`

| Path | Description |
|------|-------------|
| `docs/screencast/SCRIPT.md` | Production screencast script for the SPLASH/ISSTA 2026 tool demonstration, with measured commands, outputs, and feasibility ledger |

## paper — LaTeX sources

LaTeX sources for the tool-demo and full-paper writeups: main `.tex`, sections, bibliography, and figures.

### `paper/full-paper/`

| Path | Description |
|------|-------------|
| `paper/full-paper/Nostep_version.tex` | IEEEtran full conference paper "MIST: Trace-Driven REST API Testing with a Trace-Shape Oracle and Adaptive Fault Taxonomy for Microservices". |
| `paper/full-paper/adminroute_exec.png` | Screenshot of a three-step ts-admin-route workflow where step one returns 400 and fails the whole scenario. |
| `paper/full-paper/case_exec.png` | Screenshot of one generated case's end-to-end execution showing a 500 on a prices call rendered in Allure. |
| `paper/full-paper/get_adminorder_matrix.png` | Per-call result matrix for GET adminorder where every run returns 200 with a single latency spike. |
| `paper/full-paper/main.tex` | IEEEtran full conference paper "AI-Enhanced Trace-Driven API Testing with Comprehensive Microservice Validation". |
| `paper/full-paper/post_prices_matrix.png` | Per-call result matrix for POST adminbasic prices showing two passes and several Status Code Mismatch failures. |
| `paper/full-paper/poster.tex` | tikzposter conference poster (48x36in, Baylor green/gold theme) summarizing the MIST work. |
| `paper/full-paper/references.bib` | BibTeX bibliography for the full paper (RESTest, microservice testing and tracing surveys, and related work). |
| `paper/full-paper/smartfetch_body.png` | Screenshot of Smart Fetch filling a POST stations/idlist body with existing station names, returning 200. |

### `paper/full-paper/figs/`

| Path | Description |
|------|-------------|
| `paper/full-paper/figs/Res_flow.png` | Data-flow diagram of the MIST/RESTest pipeline (fig:dataflow), used in the poster and Nostep paper. |
| `paper/full-paper/figs/bu_logo.png` | Baylor University logo used on the poster. |
| `paper/full-paper/figs/mst-architecture.png` | End-to-end data-flow diagram across the extended RESTest multi-service testing pipeline. |

### `paper/full-paper/figs/experiment/`

| Path | Description |
|------|-------------|
| `paper/full-paper/figs/experiment/INTELLIGENT_ANALYSIS.png` | Screenshot of AI-generated root-cause analysis naming the failing service, method, and line plus a proposed fix. |
| `paper/full-paper/figs/experiment/Step_output.png` | Screenshot of the Allure report for a single test step combining HTTP request/response data with trace-driven analysis. |
| `paper/full-paper/figs/experiment/api_info.png` | Screenshot of the API call hierarchy and stats showing a POST 500 at the root followed by successful downstream GET calls. |

### `paper/tool-demo/`

| Path | Description |
|------|-------------|
| `paper/tool-demo/NOTES_TO_AUTHOR.md` | Author working-notes ledger mapping paper-side component names to repository classes and modules for the tool-demo paper. |
| `paper/tool-demo/README_ISSTA.md` | Build instructions for the tool-demo paper variants (acmart vs IEEEtran) and their venue targeting. |
| `paper/tool-demo/REVIEW_ISSTA_2026.md` | Internal skeptical review and change plan for the ISSTA 2026 tool-demo submission, grounding each quantitative claim to evidence. |
| `paper/tool-demo/main.tex` | IEEEtran (ICSE 2027 fallback) tool-demo paper "MIST: A Trace-Driven Tool for Multi-Service REST API Test Generation with Trace-Shape Oracles". |
| `paper/tool-demo/main_issta.tex` | acmart sigconf active ISSTA 2026 tool-demo paper "MIST: A Trace-Driven Tool for Multi-Service REST API Test Generation with Trace-Shape Oracles". |
| `paper/tool-demo/refs.bib` | BibTeX bibliography for the tool-demo paper (EvoMaster, RESTest, ARAT-RL, and others), verified against ACM DL and IEEE Xplore. |

### `paper/tool-demo/figures/`

| Path | Description |
|------|-------------|
| `paper/tool-demo/figures/architecture.tex` | TikZ source for Figure 1, the three-module MIST architecture diagram (mist-core, mist-cli, mist-llm). |
| `paper/tool-demo/figures/trace_oracle.tex` | TikZ source for Figure 2, a Gantt-style Jaeger trace showing a swallowed downstream failure on Istio Bookinfo. |
