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
| `mist-core/src/test/java/io/mist/core/config/MstConfigDataIntegrityTest.java` | Pins the two main-track P1 flags (mst.oracle.dataintegrity.enabled, mist.fault.injection.enabled): default OFF, opt-in propagation, strict-validator whitelist survival. |
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
| `mist-cli/src/main/java/io/mist/cli/MistRunner.java` | Core MST pipeline runner; builds the MistGenerator and writer, bootstraps the Trace Shape Oracle, then generates, writes, executes and reports on tests. Pairing block: single-threaded pairing/probe bracket (mst.test.parallelism=1), single pairing-report path shared by the C-P1-3fix clear-failure sink (writes f2ClearFailure + f2FailedFlags before the F2 throw) and the normal-path writes. |
| `mist-cli/src/main/java/io/mist/cli/SemanticRegistryDumper.java` | Standalone utility that rebuilds the SemanticDependencyRegistry from the TrainTicket spec, config and traces and dumps it to a JSON file. |
| `mist-cli/src/main/java/io/mist/cli/SemanticRegistryEvaluator.java` | Evaluates the SemanticDependencyRegistry against a ground-truth YAML reporting precision/recall/F1, plus an ablation table and golden-file diff. |
| `mist-cli/src/main/java/io/mist/cli/TraceErrorAnalysisMain.java` | Demo main that runs TraceErrorAnalyzer over a sample failed trace, printing root-cause analysis and the error report after loading LLM properties. |
| `mist-cli/src/main/java/io/mist/cli/TraceMain.java` | Tiny scratch main that extracts and prints WorkflowScenarios from a single hardcoded trace file path. |

### `mist-cli/src/main/java/io/mist/cli/auth/`

| Path | Description |
|------|-------------|
| `mist-cli/src/main/java/io/mist/cli/auth/MstAuthHandler.java` | Runtime auth helper for MST-generated tests with configurable login strategy (none/static/per-jvm/per-test/per_jvm_cookie), token caching and per-path skip patterns. per_jvm_cookie (G3 SUT-2 eng item iii, live-smoked vs Sock Shop): one register/login per JVM, SESSION COOKIES cached + attached by applyAuth (no Authorization header); ${unique} in auth.login.username/body resolves to a per-JVM suffix so register-as-login never collides; overrideToken + 401-refresh are header-centric and disabled in cookie mode (disclosed) |
| `mist-cli/src/test/java/io/mist/cli/auth/MstAuthHandlerCookieModeTest.java` | Pins the PER_JVM_COOKIE pure-config contracts: mode parsing, null bearer token, 401-refresh disabled, ${unique} resolution stable + substituted, plain usernames untouched |
| `mist-cli/src/main/java/io/mist/cli/auth/MstAuthRefreshFilter.java` | REST Assured filter for MST tests that on a 401/403 invalidates the cached JWT, re-logs in, swaps the header and retries the request once. |

### `mist-cli/src/main/java/io/mist/cli/fault/`

| Path | Description |
|------|-------------|
| `mist-cli/src/main/java/io/mist/cli/fault/DataIntegrityRuntime.java` | B2 runtime consulted by generated tests: baseline capture, isolation-key freshening (fresh-strings/station-pair), ack parse, quiescence-gated read-back poll with Jaeger absence upgrade, run records on the in-process holder. G3 depth adapter: beforeWriteSupplied(step,corrId,body,keyField,keyValue) for supplied-isolation triples (bodyless writes; body passed through untouched; wiring mismatches recorded as loud per-record errors) + presentX() mode dispatch — VALUE_DELTA X = extractProbeValue(match_field row → value_field) differs from the leg's own baseline (BigDecimal-aware, absent row = no value; fresh buyer appearing counts as movement); polling/gates/verdict machinery reused verbatim; read-back JWT already covered by MstAuthHandler. Hardening wave (+ review fix wave): read-back HTTP status recorded; non-2xx polls tolerated WITHOUT scanning but the DECISIVE read must be 2xx else error (H2 — error bodies are never evidence, transient blips don't burn records); baseline non-2xx = pass-through error; readback_bound completeness check; post-settle re-read before OBSERVED_COMPLETE_ABSENT (R4fix); orphaned-pending synthetic records keep the join aligned (H1); beginRun refuses mst.test.parallelism>1 (R7fix). G3 rider 1: RunRecord carries a generation-time correlationId; beforeWrite/afterWrite gain a correlationId overload (legacy overloads pass null) threaded via Pending onto every record shape. extractItems/parsesToCollection recognise a third collection shape — the HAL/HATEOAS `{_embedded:{<rel>:[..]}}` convention (Spring HATEOAS, Sock Shop SS-B), flattening every embedded relation — in addition to bare arrays and `{status,msg,data:[]}`; the HAL branch is inert on TrainTicket bodies (never `_embedded`), so the frozen comparator's verdicts are byte-identical. Http/HttpResponse seam + `installHttpOverride(Http)` widened to PUBLIC (logic unchanged) so an out-of-package g3 head-to-head can route the value-delta read-back at a non-SUT host — the Sock Shop shipping enqueue's durable sink is the RabbitMQ management API, not the SUT. |
| `mist-cli/src/main/java/io/mist/cli/fault/FaultInjector.java` | B1.1 backend-swappable fault-activation interface (inject/clear + FaultTarget + mist.fault.injection.enabled gate) for the flag-gated control/fault pairing executor. |
| `mist-cli/src/main/java/io/mist/cli/fault/PairedFaultExecutor.java` | B1.3 orchestrator (clear→control→inject-all→fault→clear-finally, crash/inject-failure safe) + B2.3 pure-differential verdicts + B2.4 benign FP probe (N flag-off runs, FP rates per stratum, FP-vs-timeout curve, ≤5% bar) and the JSON pairing report. Hardening wave (+ review fix wave): bar v2 (NOT_EVALUABLE when gateResolvedFraction<0.5 or timeoutGatedFraction>0.3 — pre-registered floors; the floor is derived/subsumed, disclosed), verdict-aware per-record join replacing pick() (triple FIREs iff ≥1 joined pair fires; fire/noFire/notEvaluable/unjoined tallies in the report), onClearFailure(BiConsumer) evidence sink persisting the report with f2ClearFailure:true + f2FailedFlags BEFORE the F2 throw (C-P1-3fix/H6), readbackHttpStatus in the report. G3 rider 1 (R3fix/H1/C13, + review fix wave): the join is CORRELATOR-based (joinRecords aligns by the writer's <class>.<method>#<stepIdx> when every record carries one, positional fallback for legacy suites) so an asymmetric skip leaves only that write unjoined instead of shifting every pair; zero aligned pairs with both legs non-empty → NOT_EVALUABLE (never a cross-paired FIRE, review A-F1); joinMode + correlatorUnique surfaced per pair (allUnique guard, review A-F2/C-MAJOR-2) — tallies feed G3 claims ONLY when joinMode=correlator ∧ correlatorUnique=true; correlationId/joinMode/correlatorUnique emitted in the report; evaluate() package-visible for the join unit tests. |
| `mist-cli/src/main/java/io/mist/cli/fault/IstioRouteFaultInjector.java` | G3 FaultInjector backend for route-scoped network faults on an UNMODIFIED SUT (the natural stratum): inject = kubectl apply of a reviewed VirtualService manifest (fault.abort on the inside-payment /drawback URI prefix, /account read-back stays live), clear = kubectl delete --ignore-not-found; convergence PROBED via a caller-supplied non-mutating URL (incomplete path under the aborted prefix: Envoy abort status when live vs app 404 when not) in BOTH directions with FaultInjectionException on timeout; probe I/O failure satisfies neither direction; shares SutFlagFaultInjector's Exec seam/runProcess; FaultTarget = logging identity, manifest = operative coordinate |
| `mist-cli/src/test/java/io/mist/cli/fault/IstioRouteFaultInjectorTest.java` | Pins the Istio route-fault backend: apply/delete argv assembly (context/namespace/ignore-not-found), probe-gated convergence both directions, never-converging inject/clear throw, probe-I/O neutrality, kubectl failure surfaces output, constructor validation |
| `mist-cli/src/main/java/io/mist/cli/fault/SutFlagFaultInjector.java` | Gate-1 FaultInjector backend: kubectl set env JAVA_TOOL_OPTIONS=-D<prop>=true + rollout status per toggle, explicit kubectl context, process-level timeouts, test seam. |
| `mist-cli/src/main/java/io/mist/cli/fault/HttpToggleFaultInjector.java` | FaultInjector backend that toggles a RUNTIME in-memory fault mode via an HTTP GET (inside-payment's /inside_payment/test/faultmode/{mode}) with NO pod restart — so the caller's pooled connection + Ribbon routing to the single stable instance stay valid (the G3 head-to-head's reliable mechanism; the SutFlag rollout and IstioRoute mesh abort both raced ts-cancel-service's client-side caching per leg, g3-headtohead-results.md). Mode = the FaultTarget property's mist.fault.drawback.<mode>.enabled segment; clear = "none"; optional bearer token for a gateway-guarded route; 2xx-or-FaultInjectionException |
| `mist-cli/src/main/java/io/mist/cli/fault/TargetTripleRegistry.java` | Strict loader for the per-SUT target-triples.yaml (write endpoint, persisting dependency, read-back GET, isolation key, optional fault_flag) consumed only by the flag-gated pairing executor. G3 depth adapter: +isolation_strategy `supplied` (key established by scenario setup, exactly one isolation_key field) and +readback_mode `value-delta` with value_probe{match_field,value_field} (X = probed value differs from the leg's own baseline; for aggregate-only observables like the TT /account balance), loud cross-validation. Hardening wave: readback_endpoint must start with "GET " at load (R7fix/C-P1-9) + optional readback_bound completeness field (R1fix). |

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
| `mist-cli/src/main/java/io/mist/cli/writer/MultiServiceRESTAssuredWriter.java` | Writer emitting JUnit/REST Assured suites that replay MultiServiceTestCases, with auth, Allure, query-param and Trace Shape Oracle emission. B2 data-integrity hooks (beforeWrite/afterWrite) emitted only for steps matching a registered triple (flag-off byte-identical); G3 rider 1 stamps the class-qualified generation-time correlator "<className>.<testMethodName>#<stepIdx>" onto both hooks so the pairing join aligns cross-leg by correlator, not position (class prefix keeps it unique across a run's many generated classes, review A-F2). |

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
| `mist-cli/src/main/resources/My-Example/trainticket-gate1-pairing.properties` | Gate-1 pairing run config: demo base + faulty.ratio=0, two-phase ON, enhancer OFF, data-integrity oracle + fault-injection flags ON, pre-registered quiescence knobs. |

### `mist-cli/src/main/resources/My-Example/trainticket/`

| Path | Description |
|------|-------------|
| `mist-cli/src/main/resources/My-Example/trainticket/flow.md` | Mermaid-diagram doc describing the MST end-to-end flow from MistMain through MistRunner generation, oracle bootstrap and execution. |
| `mist-cli/src/main/resources/My-Example/trainticket/input-fetch-registry.yaml` | Smart-input-fetch registry YAML mapping parameter names to producer endpoints, extract paths, priorities and success rates for TrainTicket. |
| `mist-cli/src/main/resources/My-Example/trainticket/merged_openapi_spec 1.yaml` | Merged OpenAPI 3.0.3 specification for the whole TrainTicket microservice system, used as the oas.path input. |
| `mist-cli/src/main/resources/My-Example/trainticket/mist-noun-map.yaml` | Empty per-SUT noun-map override for TrainTicket demonstrating the overlay mechanism over the bundled default noun map. |
| `mist-cli/src/main/resources/My-Example/trainticket/real-system-conf.yaml` | Multi-service test configuration YAML listing each TrainTicket service's operations, paths and parameter generators (the conf.path input). |
| `mist-cli/src/main/resources/My-Example/trainticket/root-api-registry.json` | Persisted Root API Registry JSON of unique TrainTicket root APIs and their recorded microservice call trees per source trace. |
| `mist-cli/src/main/resources/My-Example/trainticket/target-triples.yaml` | P2 target-triple registry: the two Gate-1 write/read-back triples (adminroute, adminbasic-contacts) with trace-matchable dependency and business-key isolation fields. |

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

### `mist-cli/src/test/java/io/mist/cli/fault/`

| Path | Description |
|------|-------------|
| `mist-cli/src/test/java/io/mist/cli/fault/DataIntegrityRuntimeTest.java` | Pins the B2 runtime against a scripted HTTP seam: passthrough when inactive, freshening per strategy, membership projection, and every quiescence-gate branch; hooks never throw. |
| `mist-cli/src/test/java/io/mist/cli/fault/PairedFaultExecutorTest.java` | Pins B1.3+B2.3 against a stateful fake SUT: masked fault run FIREs, injector clear/inject/clear ordering, crash-safe flag clearing, full verdict rule table, JSON report shape. |
| `mist-cli/src/test/java/io/mist/cli/fault/SutFlagFaultInjectorTest.java` | Pins the B1.1 injector against a recorded Exec seam: exact kubectl argv (-D form), set-env→rollout sequencing, context passing, failure propagation, enabled-gate default. |
| `mist-cli/src/test/java/io/mist/cli/fault/HttpToggleFaultInjectorTest.java` | Unit-covers HttpToggleFaultInjector.modeOf (drawback property → mode segment; rejects too-few segments); the HTTP call is exercised live by the G3 head-to-head run. |
| `mist-cli/src/test/java/io/mist/cli/fault/TargetTripleRegistryTest.java` | Pins the P2 registry: shipped TrainTicket file parses to the two Gate-1 triples (incl. fault_flag); strict parser rejects missing/unknown/duplicate/empty-key malformations. |

### `mist-cli/src/test/java/io/mist/cli/writer/`

| Path | Description |
|------|-------------|
| `mist-cli/src/test/java/io/mist/cli/writer/DataIntegrityEmissionTest.java` | Locks the B2 codegen layer: matching write steps get beforeWrite/afterWrite hook emissions (body rewrite before req.body, traceparent id into afterWrite); no triples ⇒ hook-free source. |
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
| `evaluation/suts/sockshop/target-triples.yaml` | SUT-2 β registry (benign-trap-only, NO fault_flag per branch β): SS-B addresses (isolation_key street+number) + cards (longNum), dependency user, fresh-strings, readback_bound 500 (R1fix — global seeded growing lists). SS-A cart deliberately ABSENT: the BFF renames id→itemId across the surface so containsKey (name+value) would false-absence; needs a reviewed membership alias first. Cookie auth via the run config |
| `evaluation/suts/sockshop/sockshop-g3-benign.properties` | G3 β benign FP probe run config (prereg C-pin 4): demo conf + faulty.ratio 0.0 + enhancer/exploration OFF + auth per_jvm_cookie (/register register-as-login, ${unique} username, /register+/login cookie-skipped) + dataintegrity oracle ON (poll 500ms/timeout 10s/settle 3s carried from G1) + fpprobe.runs=30 (bar v2: interval + histogram, NOT_EVALUABLE on degraded gate) |
| `evaluation/suts/sockshop/sockshop-demo.properties` | Single minimal MIST config profile for Sock Shop, relying on generalization defaults (auth.mode=none, smart-fetch OAS=oas.path, basePath auto). |

### `evaluation/suts/sockshop/deploy/`

| Path | Description |
|------|-------------|
| `evaluation/suts/sockshop/deploy/deploy.sh` | Adds WeaveWorks Sock Shop into the existing kind+Istio+Jaeger cluster, excludes its DBs from the mesh, routes it through the shared ingress; teardown removes it. |
| `evaluation/suts/sockshop/deploy/g3-write-path-enable.sh` | G3 write-path enablement overlay (idempotent, runs after deploy.sh): pins carts-db/orders-db to mongo:3.4 (complete-demo.yaml leaves them floating at latest=8.x which removed OP_QUERY → the 2017-era carts:0.4.8 driver 500s on every Mongo write) + restarts carts/orders; extends the ingress VirtualService with /register //login //card //address for the cookie-session write paths. Live-discovered fixes recorded in debug/a-main/prep/g3-sut2-deploy-verify.md. |

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
| `debug/a-main/README.md` | **Active A-conference plan v4** (reviewer-hardened; §0/§2/§3/§4/§9 updated post-cold-review): take MIST to a top venue via black-box generation-driven fault injection + a label-free differential data-integrity trace oracle; two-mode fire rule (§4); **primary A-path re-anchored to the empirical/benchmark leg (C2 benchmark + C3 prevalence — Cast-independent, ISSTA/ASE-A) with Gate-3 as upside**, not the Cast-capped mechanism; honest track-split verdict (empirical-A floor + mechanism-A via Gate-3), build list, decision gates |
| `debug/a-main/EXECUTION.md` | **Active execution plan** (decision: bet on Gate 3): ordered engineering sequence with code seams + acceptance criteria; focus on the Gate 1 sprint (B1 fault-injection mode, B2 differential oracle, validate on TrainTicket) toward the Gate 3 empirical bug hunt |
| `debug/a-main/TOOL-EXECUTION-PLAN.md` | **Step-by-step plan to TOUCH MIST tool code** (B1 opt-in fault-injection mode + B2 differential data-integrity oracle), v8 — SIX review rounds; **round-5 (on v6) AND round-6 (on v7) EACH returned 3/3 cold-reviewer OVERALL SATISFIED** across feasibility+prep / novelty+contribution / design-logic, zero BLOCKING on v7 (E verified Cast arXiv → deltas accurate + no overclaim, mechanism ceiling honestly disclosed & routed around; D/G verified all 7 seams + spec incl. the state-channel citation; F/I verified all fixes hold, no surviving contradiction). Design spine: writer=codegen (B2 = codegen + orchestration layers, state via a sibling in-process JUnitCore static holder); two-mode fire (pure-differential/S2 headline + gated/S1→G3); soundness protocol w/ measured stratified FP (≤5% non-timeout-gated sync bar, async→G3, quiescence-gate coverage); adminbasic = business-key collection membership, build-verified/smoke-pending; primary A-path = empirical/benchmark MAIN-track (C2 benchmark w/ costed ~100–140 scale plan + C3 defect-yield/prevalence, Cast-independent) + Gate-3 upside; Cast comparator pre-committed (blind-authored, matched recall). **Execution BLOCKED until the user says "yes"** |

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
| `debug/a-main/research/REVIEW-B1B2-COLD-A-fp-freedom.md` | Cold review A of the BUILT B1+B2 (2026-07-02, during run #3): FP-freedom of the FIRE verdict — isolation/non-circularity/races confirmed sound; CRITICAL-if-triggered finding 1 = collection truncation + control-before-fault row accumulation → systematic false FIRE in the high-confidence stratum (mechanism confirmed, TT trigger unverified); pick() positional join; FIRE emitted regardless of gate vs bar exclusion; ack-rule + membership-normalization preconditions |
| `debug/a-main/research/REVIEW-B1B2-COLD-B-fp-measurement.md` | Cold review B of the BUILT B1+B2 (2026-07-02): validity of the ≤5% bar — bar governs a LOWER BOUND (timeout-gated fires excluded from numerator, kept in denominator → anti-conservative under Jaeger degradation; vacuous/weak PASS possible); no post-settle re-read before OBSERVED_COMPLETE_ABSENT; trace-stability proxies request-completion not durable absence; pick() masks lost siblings (FN); denominator counts records not iterations; station-pair cross-run collision; soft cap; arithmetic/curve/floor confirmed correct |
| `debug/a-main/research/REVIEW-B1B2-COLD-C-impl-contribution.md` | Cold review C of the BUILT B1+B2 (2026-07-02): implementation + contribution — concurrency races latent-not-active (pairing forces parallelism=1; guard-by-convention), read-back HTTP status discarded (5xx ≈ absence), clear-failure discards the whole report (confirmed by run #2), batch inject/clear safe, rollout-confirmed ≠ fault-activation-confirmed; contribution NOT yet A-ready — novelty vs Cast/Filibuster/metamorphic-REST deferred to G2/G3, needs ≥2 SUTs + fair comparator + wild defect; "defensible island" = the combination |
| `debug/a-main/research/REVIEW-B1B2-RECONCILIATION.md` | Reconciliation of the 3 independent B1+B2 cold reviews (2026-07-02): consensus table R1–R8 (read-back completeness precondition; bar = Jaeger-dependent lower bound → report the FP interval; pick() join; OBSERVED_COMPLETE_ABSENT semantics; TT-coupled preconditions; station-pair caveats; parallelism-by-convention; novelty deferred) — verdict: NOTHING invalidates run #3; report-audit checklist for run #3's JSON; prioritized post-run hardening list (bar v2 NOT_EVALUABLE-on-gate-degradation, completeness assertion+teardown, persist-report-before-F2-throw, verdict-aware join, post-settle re-read); paper-wording obligations |
| `debug/a-main/research/REVIEW-PREREG-A-pc.md` | Prereg cold review A (2026-07-02, hostile-PC simulation on g2-novelty-comparator-prereg): 13 findings — CRITICAL F1 blindness-by-reveal-ordering unimplementable (fault list already public in target-triples.yaml + benchmark cases; fix = enumerated hash-frozen provisioning of upstream source only), paragraph overclaims F2/F3/F10 (every-OSS-cannot-meet; unscoped no-assertion-points vs our own hand-curated registry; metamorphic concession must live IN the paragraph), competence floor F4, freeze-the-brief F5, endpoint-leak F6, matched-recall undefined F7, one-directional adjudication F8, R2 under-implementation F9, injected≠PC-moving F11, citation slips F12; comparator approximates Filibuster NOT Cast; verdicts = paragraph NO as-is (rewrite offered), comparator right-intent-not-yet-non-strawman |
| `debug/a-main/research/REVIEW-PREREG-C-methods.md` | Prereg cold review C (2026-07-02, methodology audit of both preregs): F1 §8.5-3 half-fulfilled (sites named, opportunities never COUNTED; TT's own G3 saga site MISSING); F2 no SUT-2 sensitivity/constructed-positive story (Toxiproxy S1 masks-or-not unknown; branch unregistered); F3 paragraph contradicted by sibling prereg's javaagent attachment; F4 hardening promotions un-propagated + bar v2 not adopted for G3 (vacuous-PASS regime); F5-F11 adjudication/per-SUT-FP/blind-set-scheduling/elastic-triggers/SS-A-isolation/SS-C-depth-conditional/minors; 13 PIN-THESE-NOW items; verdicts = both docs FIT-AFTER-PINS |
| `debug/a-main/research/REVIEW-PREREG-B-tech.md` | Prereg cold review B (2026-07-02, technical accuracy of the G3 prereg — 28 claims verified against primary sources incl. service source code): MAJOR-1 the tracing mitigation cannot work as stated (W3C break is AT the Node front-end → javaagents on Java services yield DISCONNECTED traces; fix = OTel Node auto-instrumentation on front-end + javaagents, OTLP→jaeger 2.14 native, plus readOnlyRootFilesystem/heap/Java-8 realities); MAJOR-2 pagination misdiagnosed (BFF GET /orders is UNPAGINATED findByCustomerId; completeness mechanism must be BFF-compatible bounded/row-count; TeaStore genuinely windows); MEDIUM SS-B read-backs are GLOBAL seeded growing lists + /register //login not ingress-routed; INFO gold: shipping SWALLOWS enqueue failure (natural masked-failure candidate, black-box-invisible to read-back → masking-oracle/benchmark case; resolves the SS-C async QUESTION negative) + ?custId= dev-mode isolation lever |
| `debug/a-main/research/REVIEW-PREREG-RECONCILIATION.md` | Reconciliation of the 3 prereg cold reviews (2026-07-02): consensus actions — blindness re-based on enumerated hash-frozen provisioning; paragraph rewritten (impossibility sentence, assertion-point scoping, metamorphic clause inside, dev-confirmed, javaagent disclosure); comparator = Filibuster-approximating with Cast-pattern OUT; competence floor + failed-calibration branch (calibration set = the two public Gate-1 faults); operating points + detection unit + κ adjudication + R2-complete outputs; G3 corrections (Node front-end instrumentation load-bearing; BFF-compatible completeness; SS-B global lists; 4th engineering item; SS-C async QUESTION resolved NEGATIVE → depth credential reduced; TT cancel→refund saga site + counts; bar v2 + per-SUT FP protocol; crisp triggers); disposition ledger — G2 v2 + G3 v2 applied → both preregs FIT |
| `debug/a-main/research/REVIEW-HARDENING-A-soundness.md` | Hardening-wave cold review A (2026-07-02, soundness of e5af35b): verdict SOUND for G2/G3 — R1/R4 attack attempts failed, additivity verified; residuals ranked: F1 positional-join completeness (afterWrite not in a finally → mid-run record gap misaligns; equal-count double-drop silent), F2 report tallies thinner than spec, F3 gateResolvedFraction floor = dead code (derived), F4 sink covers only the runs-complete shape, F5 property-only parallelism guard (disclosed) |
| `debug/a-main/research/REVIEW-HARDENING-B-conformance.md` | Hardening-wave cold review B (2026-07-02, spec conformance + test adequacy of e5af35b): substantially conformant — item-by-item table (bar v2 + t1–t5 exact; 2(c) adapters DEFERRED SILENTLY; R3 join keyed on triple not stepKey + tallies-not-itemized deviations); 4 spec-enumerated tests missing (worst: nonzero unjoinedRecords); no-silent-re-scoring RESPECTED; depoison restore byte-exact but gate1-result §7 pointer stale; verdict fit-after-tests+disclosure |
| `debug/a-main/research/REVIEW-HARDENING-C-integration.md` | Hardening-wave cold review C (2026-07-02, integration/pipeline risk of e5af35b): SAFE TO SHIP — every new failure mode fails loud; top risk F2 = R1fix abort-on-first-non-2xx is attrition-prone on the 503-prone SUT (recommends poll-through + 2xx decisive read, pre-registered amendment); F1 join misalignment trigger proven live (run #3 71v70); F3 registry write-back re-dirties the shipped resource every run (runbook rule); no automated report consumers; byte-additivity confirmed |
| `debug/a-main/research/REVIEW-HARDENING-RECONCILIATION.md` | Reconciliation of the 3 hardening-wave reviews (2026-07-02): consensus table H1–H10 with dispositions — FIXED in the fix wave (H2 poll-through-transient decisive-read semantics; H1(i) orphaned-pending synthetic records; H3 six new tests; H6 f2FailedFlags; H10 path dedupe + doc pointers) and DISCLOSED (2(c) adapter deferral; dead floor; tallies-only report; sink shape; H9 runbook registry rule; per-pair tallies DESCRIPTIVE-ONLY until the writer-side correlator = G3 rider); post-fix status: G3 prerequisites MET with two disclosed riders |
| `debug/a-main/research/REVIEW-COMPARATOR-A-soundness.md` | Comparator cold review A (2026-07-02, soundness of 666c461): CRITICAL F1 = comparator path never logs in (ensureReady uncalled) → live calibration DOA; F2 fault-leg state-GET transport failure scored as a DETECTION (comparator-favoring vs MIST's read-back-error category); F3 clear-failure continues the loop; F4 mid-loop throws lose the report; F5 = the timing-fairness analysis (zero-wait FAIR for calibration via control gate + LOST_WRITE verdict-equivalence; G3 needs delay stratification + infra-failure-rate reporting); F6 report gaps (cite/manifest), matched-inputs wording |
| `debug/a-main/research/REVIEW-COMPARATOR-B-bindings.md` | Comparator cold review B (2026-07-02, bindings faithfulness c4b9a08 vs frozen set 15954a8): freeze hygiene verified + NO run has executed (all fixes = pre-run amendments); findings — adminroute's second frozen read path dropped silently, contacts fields narrowed 5→2, THE PATTERN (every narrowing = exactly MIST's registry keys, most attackable fact), design §4's pre-stated outcome FALSIFIED by G0 evidence (sloppy fabricated ack: msg case variant + data:null → response clauses flag too — injection-realism artifact), matched-inputs contradiction partly forced; net-bias statement for the study |
| `debug/a-main/research/REVIEW-COMPARATOR-C-integration.md` | Comparator cold review C (2026-07-02, operational readiness): auth-via-preflight-side-effect confirmed; report-loss paths; ~5-15%/endpoint transient control-abort from zero-wait state reads (recommends the 10s/500ms bounded retry as a disclosed amendment); mist.fault.injection.enabled silently ignored; root-api-registry dirtied pre-branch (H9 extension: target/ scratch path); assertions.path CWD-relative trap; client timeouts; PLUS the full calibration-run checklist (properties clone, cluster steps, expected wall time ~20-40min, post-run verification) |
| `debug/a-main/research/REVIEW-COMPARATOR-RECONCILIATION.md` | Reconciliation of the 3 comparator reviews (2026-07-02): consensus C1–C13 with dispositions — FIXED same day (ensureReady fail-fast; transport-only fault-leg reclassification; bindings amendment A2 completing the state clauses incl. ${field:id} per-entity GET + entity-matches mode + all-five contacts fields; A3 bounded presence retry at MIST's 10s/500ms budget; per-endpoint try/catch with report always written; clear-failure stops the loop; injection-flag fail-fast; report cite+faultManifest+transportFailure; client timeouts) and DISCLOSED (design §3/§4 corrections; ops preconditions; G3 riders: full-set binding round incl. failure contracts, infra-failure-rate reporting, delay-vs-loss stratification); net-bias statement; suites 35+331+93 green |
| `debug/a-main/research/REVIEW-RIDER1-A-soundness.md` | Cold review A of Rider 1 (`e640748`, join soundness): ACCEPT-WITH-FIXES; F1 (HIGH) the representative fallback cross-pairs controls.get(0)/faults.get(0) when the correlator aligns zero pairs but both legs are non-empty (disjoint correlators) → false FIRE with firePairs=0, breaking the "fires iff ≥1 aligned pair" invariant; F2 (MED) the correlator omits the class name so cross-class method-name collisions leave residual misalignment; F3/F4 LOW (silent positional revert; pre-existing dangling pending, fail-safe); verified the multimap FIFO pairing, both-side leftover counting, filter-by-tripleName-before-join, and all ten record shapes threading the id are correct |
| `debug/a-main/research/REVIEW-RIDER1-B-additivity.md` | Cold review B of Rider 1 (`e640748`, byte-additivity + backward-compat + generated-code integrity): ACCEPT-WITH-FIXES; all three headline claims verified TRUE (flag-off byte-identical — emissions strictly inside __diTriple guard; legacy null path byte-for-byte, proven live by the checked-in Flow_Scenario_107 legacy suite; generated Java compiles + binds the new overloads unambiguously); RunRecord 15→16→17 ctor chain appends correlationId with no field shift; report key additive; independently found the SAME representative cross-pair false-FIRE as A-F1 (rated MED) + the javadoc-still-positional LOW |
| `debug/a-main/research/REVIEW-RIDER1-RECONCILIATION.md` | Reconciliation of the 3 Rider-1 reviews (2026-07-02): all ACCEPT-WITH-FIXES; A+B independently converge on the representative cross-pair false-FIRE (R1), A+C on correlator uniqueness (R2); fix wave applied same day (suites 35+331+104, +8) — R1 empty-pairs-both-legs-non-empty → NOT_EVALUABLE; R2 className prefix + correlatorUnique/joinMode surfaced + claims gated on joinMode=correlator ∧ correlatorUnique=true; R3 the indispensable equal-count divergent-skip test (positional silently mispairs, unjoined=0); R4/R5/R6 afterWrite-arg/orphan/execute()-level tests; R7 joinMode surfaced; R8 javadoc; R9 disclosed; R10 the graduation claim reworded to what the code earns (misalignment error is a false POSITIVE → correlator = precision, not recall; evidence-gated) |
| `debug/a-main/research/REVIEW-RIDER1-C-tests.md` | Cold review C of Rider 1 (`e640748`, test adequacy + claim integrity): ACCEPT-WITH-FIXES; "graduate to feed G3 claims" NOT earned as written — MAJOR-1 the indispensable equal-count divergent-skip case (positional unjoined=0, silent mispair; the REVIEW-HARDENING-A F1 residual) is untested, the one tested win (middleSkip 2-vs-1) was already positionally flagged, and the recall/false-NO_FIRE recovery direction is asserted-not-tested; MAJOR-2 duplicate/colliding correlators silently degrade to positional-within-group while still labeled correlator-aligned (uniqueness untested/undisclosed/unguarded); MEDIUM afterWrite arg dead except one untested record + orphan/error-shape correlators + correlator-path integration all untested (execute()-level tests use legacy hooks); CORRECT-list credits the middleSkip/positionalFallback A/B and the by-construction cross-leg identity |

### `debug/a-main/prep/`

| Path | Description |
|------|-------------|
| `debug/a-main/prep/target-triples.md` | Main-track prep (no tool code): candidate (write endpoint, persisting dependency, read-back GET) triples in the TrainTicket spec for the Gate 1 differential data-integrity oracle; recommends adminroute + adminbasic/contacts |
| `debug/a-main/prep/sut-fault-injection-capability.md` | Main-track prep (no tool code): our SUT fork (train-ticket-injection@injection) already has an in-service fault injector; how to extend it SUT-side (LOST_WRITE_FAULT on a MIST-trainticket branch) to build the differential-oracle ground truth with zero MIST tool changes; §8/§9 record the implemented LOST_WRITE on adminroute (5c471dd8) and adminbasic/contacts (bbf3d6ae) |
| `debug/a-main/prep/gate1-environment-runbook.md` | Main-track prep (no tool code): WSL2/k8s runbook to deploy TrainTicket (MIST-trainticket branch) with tracing, enable the LOST_WRITE variant on ts-admin-route-service, and confirm the acknowledged-but-lost write by read-back (manual proof of the differential oracle's target before B2 is built) |
| `debug/a-main/prep/gate1-session-runbook.md` | MIST-side Gate-1 session script: WSL2 preconditions (minikube start, port-forwards, stale-flag hygiene), pairing run command + expected flow, PASS/FAIL reading per plan §4, known adminbasic-scenario gap, post-session cleanup |
| `debug/a-main/prep/gate1-smoke-result.md` | Main-track prep (no tool code): Gate-1 smoke RESULT — on live TrainTicket (minikube), control-vs-fault proof that the adminroute LOST_WRITE yields an acknowledged-but-lost write (HTTP 200/status:1, getAllRoutes unchanged) that status/schema/body oracles pass and only read-back catches; records the load-bearing JAVA_TOOL_OPTIONS `-D` flag finding (env relaxed-binding fails on TT) |
| `debug/a-main/prep/p3-async-path-resolution.md` | Main-track prep (no tool code): P3 broker-async write-path research for the B2.4 benign trap — full RabbitMQ inventory of TrainTicket (2 queues: `email` dead-code/test-only, `food_delivery` live food→delivery), proof that no existing 2xx write is ack-before-persist with a black-box read-back, and verdict NEW-INJECTOR-NEEDED with the recommended flag-gated ASYNC_WRITE trap in ts-food-service (skip sync save, persist via rabbit consumer, read back via GET /foodservice/orders/{orderId}) |
| `debug/a-main/prep/gate1-result.md` | Main-track Gate-1 validation RESULT — **PASS** (run #3 lean deploy, 2026-07-02, supersedes the runs-#1/#2 INCONCLUSIVE kept as history): FIRE on the constructed adminroute lost-write in the STRONG stratum (OBSERVED_COMPLETE_ABSENT, 19 polls, trace complete; control persisted 181ms) + sync FP 0.0 (0 fires / 2,127 acked benign records, interval [0.0,0.0], gate histogram OBSERVED_PRESENT:2127 = observation gate 100%, bar-v2 cross-reading agrees) + async disclaimer; FP-vs-timeout curve (500ms→12.98%, 1s→0.14%, ≥2s→0) justifies the 10s cap; 6-point recon-§2 audit outcomes; caveats (contacts triple NOT_EVALUABLE 0/0 records — unhooked-scenario gap; sync-only one-SUT soundness, zero novelty evidence); disclosures (lean deploy, LLM-key-absent fallbacks, -Xmx4g, ~7.8h wall); routes to G2 + hardening wave + G3 |
| `debug/a-main/prep/gate1-run3-report.json` | Committed evidence copy of run #3's machine-readable pairing report (canonical: logs/data-integrity-reports/pairing_trainticket_gate1_pairing_1782976771915.json): pairs[] (adminroute FIRE with full control/fault records incl. isolation keys, gates, poll counts, bodies; contacts NOT_EVALUABLE 0/0), fpProbe (2127 acked, 0 fires, gateHistogram, fpVsTimeoutCurve, syncFpBar PASS, async disclaimer, accept-then-drop disclosure) |
| `debug/a-main/prep/gate1-run3-input-fetch-registry.yaml` | Evidence snapshot of run #3's auto-learned input-fetch registry (moved OUT of the shipped resource: the learned successRate fields violate the ShippedRegistryDepoisonTest invariant — shipped registries must stay depoisoned; the classpath copy was restored to its pre-run state) |
| `debug/a-main/prep/gate1-infra-incident.md` | Main-track Gate-1 infra incident + recovery log (2026-07-01): run #1 wedged the WSL2 minikube via memory exhaustion (traced topology ~19–20 GiB on a 25 GiB box → swap 95% → cri-dockerd/apiserver down, 1046 connection-refused, run discarded); user-approved kill+recover+relaunch (minikube start, sweep 43 orphaned pods, restabilize 59/60, scale down prometheus+grafana); run #2 relaunched clean (0 connection-refused, memory stable, target services healthy ~0.3s, peripheral timeouts caveated); feeds the disclosures section of gate1-result.md |
| `debug/a-main/prep/gate1-preflight-audit.md` | Main-track Gate-1 pre-flight audit + live verification (2026-07-02, run #3): independent author re-audit of the 4 core B1+B2 files (PairedFaultExecutor pairing/verdict/FP-bar, DataIntegrityRuntime isolation/quiescence/normalization, SutFlagFaultInjector rollout-confirmed + tracing-preserving toggle, FaultInjector) — all sound; live checks — traceparent propagation confirmed via exact-id Jaeger lookup (strong OBSERVED_COMPLETE_ABSENT stratum works), config verified, station catalogue 87 (isolation can't degrade), lean-topology memory headroom; 3 independent cold reviewers dispatched for 验收; NOT the verdict (see gate1-result.md) |
| `debug/a-main/prep/g2-novelty-comparator-prereg.md` | G2 pre-registration v2 (2026-07-02, post-cold-review — implements A-F1..F13 + C-pins 5-8,12): the rewritten Cast-delta paragraph (two-ingredients-unavailable vs two-per-system-costs split; metamorphic concession + javaagent disclosure + declared-endpoint-pairs concession INSIDE the paragraph; dev-confirmed; frequency phrasing) + the fair-comparator protocol v2 (Filibuster-approximating, Cast-pattern OUT; blindness re-based on enumerated hash-frozen provisioning with the TT qualification; frozen brief text; endpoint superset; competence floor + failed-calibration branch with calibration = the two public Gate-1 faults; pinned operating points + detection unit + ≥10 seeds; symmetric κ adjudication + infra-evidence rule; injected = calibration only; R2-complete outputs; cluster lifecycle) |
| `debug/a-main/prep/hardening-wave-spec.md` | Post-Gate-1 hardening-wave SPEC (2026-07-02, test-first; implementation only after the Gate-1 verdict commits): the four G3 PREREQUISITES with pinned unit tests — bar v2 with PRE-REGISTERED numeric floors (gateResolvedFraction ≥0.5, timeoutGatedFraction ≤0.3, rationale + 5 pinned tests incl. reviewer-B's 54%-hidden-loss scenario); R1fix read-back completeness (record HTTP status, non-2xx→NOT_EVALUABLE, per-triple readback_bound, surface adapters); R3fix verdict-aware per-record join (kills pick()-masking); R4fix post-settle re-read; plus C-P1-3fix (persist report BEFORE the F2 throw) and R7fix guards; sequencing + the rule that run #3 is NOT silently re-scored under bar v2 |
| `debug/a-main/prep/g3-rider1-correlator.md` | G3 Rider 1 record (2026-07-02, BUILT + test-first, suites 35+331+98): the writer-side correlator join (H1/comparator-C13) — the writer stamps a generation-time <method>#<stepIdx> onto both data-integrity hooks; identical across the control/fault legs (same generated file runs twice), so PairedFaultExecutor.joinRecords aligns pairs by correlator instead of position, fixing the run-#3 71-vs-70 asymmetric-skip misalignment (a shifted positional pair could spuriously FIRE); flag-off byte-identical, legacy hooks fall back to positional; per-pair tallies graduate from DESCRIPTIVE-ONLY and may feed G3 claims; soundness argument (verdict-rule-neutral, fail-safe) + the test list |
| `mist-cli/src/main/java/io/mist/cli/g3/CancelRefundHeadToHead.java` | G3 depth head-to-head runner core (compile-verified, fault suites 110 green): per-stratum clear→control-leg→inject→fault-leg→clear(finally) orchestration feeding ONE shared cancel stimulus to BOTH oracles (DataIntegrityRuntime supplied hooks + PairedFaultExecutor.evaluate for MIST; ContractEvaluator on the frozen contract for the comparator — cancel state clauses NOT_CHECKABLE so only response-envelope checks bind); Stimulus interface = the live-TT boundary (createPaidOrder/cancel, implemented at deploy); BOTH strata via HttpToggleFaultInjector — a RUNTIME in-memory toggle of inside-payment's drawback fault mode (natural = fail → {1,"error"}; constructed = fabricatedack → {1,"Success."}), derived from each triple's fault_flag property; no pod restart so cancel-service's pooled connection + Ribbon routing stay valid (SutFlag rollout + EnvoyFilter mesh abort both raced that client-side caching — g3-headtohead-results.md); config via -Dg3.* properties; traceless-gate limitation documented (sidecar-free cancel → timeout-gated read-back). Prints a value-delta probe line (buyer /account baseline→final balance) as evidence the delta is ARITHMETIC not membership. RESULT (N=5 stable): natural FIRE+CAUGHT (tie+diagnosis), constructed FIRE+MISSED (clean win); pre-funded buyer so both legs baseline 50.00→control 130.00/fault 50.00 |
| `mist-cli/src/main/java/io/mist/cli/g3/TrainTicketStimulus.java` | Live-TT stimulus for the head-to-head: per leg registers a FRESH buyer, PRE-FUNDS it to a non-zero /account balance (addMoney baseFund=50.00) so MIST's value-delta is a real arithmetic delta not an appear-vs-absent membership signal (review A/B MAJOR), creates ONE PAID far-future order (fetches the real persisted id via order/query), cancels it. main() sets base.url + registers a per-JVM reader and ensureReady()s MstAuthHandler so the /account read-back carries a JWT |
| `mist-cli/src/main/java/io/mist/cli/g3/AccountCreateAgreement.java` | G3 AGREEMENT-anchor runner (fairness/anti-strawman): a body-carrying create (POST /inside_payment/account {userId,money}) with a runtime createAccount fabricated-ack fault; MEMBERSHIP read-back. Control persists the account, fault acks without persisting → MIST FIREs (membership) AND the comparator's STATE_GET binds on the submitted userId + catches → both AGREE. Reuses DataIntegrityRuntime + PairedFaultExecutor.evaluate + ContractEvaluator; toggles createfaultmode inline. RESULT: N=5 AGREEMENT (runs/agreement-*.log) |
| `mist-cli/src/main/java/io/mist/cli/g3/ShippingEnqueueHeadToHead.java` | G3 BREADTH head-to-head runner for the Sock Shop shipping enqueue defect (weaveworksdemos/shipping:0.4.8 — postShipping swallows any convertAndSend("shipping-task",…) exception, logs "Accepting anyway. Don't do this for real!", returns 201 unconditionally). Mirrors CancelRefundHeadToHead (reuses the reviewed oracle LOGIC: DataIntegrityRuntime supplied hooks + PairedFaultExecutor.evaluate for MIST; ContractEvaluator on the frozen contract for the comparator); the driver owns the legs + fault. Stimulus = POST /shipping {id,name}; a simple local Fault interface (inject/clear) instead of the SUT-flag FaultInjector (shipping faults are broker/mesh-side). Two strata: natural (IstioAmqpSeverInjector — /health also errs → diagnosis gap) + constructed (RabbitPolicyInjector reject-publish — /health green → clean win). Value-delta triple reads the RabbitMQ mgmt API GET /api/queues/%2f (match_field=name→shipping-task, value_field=messages) via the ShippingReadbackHttp override; queue-master→0 so depth is monotonic. Compile-verified + unit-tested (ShippingEnqueueHeadToHeadTest, 3 tests). NOT YET REVIEWED / NOT YET RUN LIVE. |
| `mist-cli/src/main/java/io/mist/cli/g3/ShippingReadbackHttp.java` | Read-back DataIntegrityRuntime.Http for the shipping head-to-head: getSut(path) → GET the RabbitMQ mgmt API (off-SUT host, basic auth mist:mist since 3.7+ restricts guest to loopback) so the reviewed value-delta oracle reads /api/queues/%2f unchanged. Installed via installHttpOverride; a transport failure reads as a non-2xx (never evidence), matching RestAssuredHttp. |
| `mist-cli/src/main/java/io/mist/cli/g3/RabbitPolicyInjector.java` | Constructed-stratum Fault: a max-length:1/overflow:reject-publish policy on shipping-task via the RabbitMQ mgmt API (PUT/DELETE /api/policies/%2f/ship-drop), with a convergence poll on the queue's applied policy so the fault is provably live before the fault leg. With depth≥1 a publish is rejected+lost while /health stays green (the clean win, live-verified on 3.8.34). reject-publish needs RabbitMQ 3.7+. |
| `mist-cli/src/main/java/io/mist/cli/g3/IstioAmqpSeverInjector.java` | Natural-stratum Fault: a surgical shipping↛rabbitmq:5672 sever (kubectl apply/delete a committed Istio manifest; 15672 read-back stays live). Mirrors IstioRouteFaultInjector but convergence is signalled by the /health BODY (getHealth live-probes the broker → shipping-rabbitmq flips err/OK) since the sever flips no HTTP status (acked-but-lost). Own Exec/HealthProbe seams (no fault-package internals); probe I/O never converges; if /health never flips, inject() throws rather than run a degenerate leg. Whether the L4 sever breaks shipping's cached broker connection is a live-tuned risk (fallback: bounce shipping). |
| `mist-cli/src/test/java/io/mist/cli/g3/ShippingEnqueueHeadToHeadTest.java` | Pins the shipping head-to-head orchestration against fakes (no live SUT/broker): a call-counter read-back models monotonic depth (control 1→2 present, fault 2→2 absent) → MIST FIRE while a response-only comparator misses both legs; a 200-expecting contract flags both legs (comparator not vacuously passing); the fault is cleared even when the fault leg throws. 3 tests, green. |
| `evaluation/suts/sockshop/g3/target-triple-shipping.yaml` | G3 shipping-enqueue value-delta triple (supplied isolation, readback GET /api/queues/%2f on the RabbitMQ mgmt API, match_field=name→shipping-task, value_field=messages; NO fault_flag — the harness owns fault injection per stratum). Parses through the reviewed loader (pinned by TargetTripleRegistryTest.shippedShippingTriple_parsesToTheEnqueueValueDelta). RUNBOOK: queue-master→0 so depth is monotonic; mgmt read-back via mist:mist. |
| `debug/a-main/g3-comparator-tt/runs/` | Persisted head-to-head run logs (prefunded-run*.log + prefunded-reps.txt for the two cancel cells with the value-delta probe balances; agreement-run*.log + agreement-reps.txt for the agreement anchor; the *-v105.log pair re-verifies all three cells on the uniform :1.0.5 image with the claim-eligibility line; the *-gated.log pair is the FOR-RECORD run with ALL round-2 machine gates live — claim-eligibility + pre-funded-baseline + addMoney-envelope) — machine-readable evidence, addressing review B's "persist run records" |
| `evaluation/suts/trainticket/g3/target-triples-natural.yaml` | G3 natural-stratum cancel→refund triple (supplied isolation + value-delta on /inside_payment/account, userId→balance; fault_flag mist.fault.drawback.fail.enabled on ts-inside-payment-service → cancelOrder's genuine catch acks {1,"error"}). Parses through the reviewed loader (pinned by TargetTripleRegistryTest.shippedG3Configs_parseToTheCancelRefundTriples) |
| `evaluation/suts/trainticket/g3/target-triples-constructed.yaml` | G3 constructed-stratum triple = natural + fault_flag (mist.fault.drawback.fabricatedack.enabled on ts-inside-payment-service); needs the fork-built inside-payment image |
| `evaluation/suts/trainticket/g3/target-triples-agreement.yaml` | G3 AGREEMENT-anchor triple: body-carrying createAccount write, MEMBERSHIP read-back on /inside_payment/account (submitted userId), createAccount fabricated-ack fault. Shows the comparator catches a lost body-carrying write (STATE_GET binds) — both oracles agree. Needs the fork-built 1.0.5 image |
| `evaluation/suts/trainticket/g3/drawback-abort-envoyfilter.yaml` | G3 natural-stratum fault manifest: inbound EnvoyFilter on ts-inside-payment-service, fault.abort HTTP 418 scoped to the /…/inside_payment/drawback URI prefix (leaves /account live); applied/removed by IstioRouteFaultInjector. EnvoyFilter (not VS) because TT's @LoadBalanced RestTemplate makes the authority a pod IP |
| `evaluation/suts/trainticket/g3/README.md` | G3 head-to-head artifacts index: the two strata (natural=tie+diagnosis, constructed=clean MIST win), the RUNBOOK (fresh buyer, PAID far-future nonzero-refund order, key=loginId, JWT read-back), and the sidecar prerequisite |
| `debug/a-main/prep/g3-tt-deploy-progress.md` | G3 TrainTicket live deploy record: target = kind "mist" cluster, namespace `trainticket` deployed SIDECAR-FREE (avoids Istio-on-infra startup race; sidecar added later ONLY to ts-inside-payment-service for the inbound EnvoyFilter); sock-shop scaled to 0 for RAM; `hack/deploy/deploy.sh trainticket` quick_start (upstream codewisdom/*:1.0.2, all-in-one mysql, no tracing), detached log /home/miaot/gate1-logs/tt-deploy.log. CRITICAL: constructed stratum needs a FORK-built ts-inside-payment image (f57102e6 fabricated-ack flag is in source, not upstream image) → build ONLY that service via hack/build-image.sh + kind load + repoint that one Deployment; natural stratum + agreement run on upstream. Order-from-here checklist |
| `debug/a-main/prep/g3-headtohead-run-architecture.md` | G3 run architecture decision: neither existing runner fits the bodyless-GET cancel (pairing executor drives generated JUnit + hardcodes SutFlagFaultInjector; comparator runner is POST-only) → DECISION: a dedicated focused harness reusing the reviewed oracle LOGIC (DataIntegrityRuntime supplied hooks + PairedFaultExecutor.evaluate for MIST; ContractEvaluator on the frozen contract for the comparator; IstioRouteFaultInjector/SutFlagFaultInjector for the two strata's faults), driving ONE shared stimulus (register→create→PAID-order→cancel). Expected-result table (natural=tie+diagnosis, constructed=clean MIST win, agreement anchor=both catch). Prereq order: TT-in-Istio-mesh (long pole) → EnvoyFilter manifest → two target-triples configs → harness → runs |
| `debug/a-main/prep/g3-tt-mesh-fault-note.md` | G3 natural-stratum mesh-fault note: TT k8s Services name ports `http` (Istio L7 available) BUT inter-service calls use a @LoadBalanced RestTemplate (discovery → pod IP + rewritten URI → authority = IP) → a plain VirtualService host-match will NOT catch cancel→inside-payment; disabling client LB not viable (portless URLs vs app-port Services). PRIMARY: EnvoyFilter on the inside-payment INBOUND listener, fault.abort 418 (per review C) scoped to the /drawback path prefix — authority-independent, /account stays live; VS = fallback; manifest authored at deploy time + live-verified; IstioRouteFaultInjector agnostic |
| `debug/a-main/prep/g3-tt-defect-survey.md` | G3 depth-site survey (user-requested research-before-decision, source-verified, AUTHORITATIVE over the two earlier docs it corrects): repo-wide failure-conversion map — the {1,"error"} catch-all is UNIQUE to ts-cancel-service, no service converts exceptions→envelopes, restTemplate throws on non-2xx → on the UNMODIFIED fork no network fault can produce a clean-success acked-but-lost anywhere (preserve's 3 swallow branches + pay()'s ignored setOrderStatus + rebook helpers all need downstream HTTP-200-{0}, unreachable; cancel's {0} branch is dead code). CORRECTION: under natural infra faults cancel returns {1,"error"} (acked per MIST's predicate, DataIntegrityRuntime:403) → frozen contract's msg gate flags it → detection TIE + MIST diagnosis gap. The comparator-blind clean miss needs the CONSTRUCTED fabricated-ack drawBack flag (Gate-1/G2 methodology, disclosed). Second site reframed: body-carrying CRUD + fabricated-ack = comparator catches too → AGREEMENT/fairness anchor; the discriminating axis is state-clause BINDABILITY (bodyless/delta/JWT). Design menu: Option 1 (recommended) = natural stratum (tie+diagnosis) + constructed stratum (clean win) + agreement site + Rider-2 binding-round fraction |
| `debug/a-main/research/REVIEW-DEPTH-A-oracle-soundness.md` | Cold review of the value-delta oracle (commits 3a94b88/7453142): ACCEPT-WITH-FIXES. Confirmed vanish→movement false-negative latch (A-F2), unstable-baseline poisoning (A-F1), zero-delta invisibility + expiry-boundary false-FIRE (A-F3), FRESH_STRINGS+value-delta gap (A-F5) |
| `debug/a-main/research/REVIEW-DEPTH-B-wiring.md` | Cold review of wiring/additivity: ACCEPT-WITH-FIXES. Additivity CONFIRMED clean by pre-image diff (membership path byte-identical, drainOrphan line-identical, shipped registry unchanged); state machine sound; F1 (2xx-gate not-acked read), F2 (constant probe / FRESH+value-delta rejects), F3 (message greppability) |
| `debug/a-main/research/REVIEW-DEPTH-C-injector.md` | Cold review of IstioRouteFaultInjector: ACCEPT-WITH-FIXES. Probe-gated convergence + -1 neutrality + conservative dead-gateway bias correct; substantive C-F2 = abortStatus must be outside app+Envoy 5xx space (418); minors (message sentinel, overflow, drainQuietly) |
| `debug/a-main/research/REVIEW-DEPTH-RECONCILIATION.md` | Reconciles depth reviews A/B/C: all ACCEPT-WITH-FIXES. Disposition table (every blocking/substantive finding FIXED across fix waves 1+2, rest disclosed as runbook/run-notes) + the value-delta RUNBOOK (per-leg fresh buyer, nonzero-refund PAID far-future order, key=loginId, baseline-stability double-read); records that per-leg isolation is a runbook rule not machine-enforced (baselineContainedX hardcoded false in value-delta by design) |
| `debug/a-main/research/REVIEW-BLIND-CANCEL-A-faithfulness.md` | Cold review A of the frozen blind cancel→refund contract: faithfulness to TT source + independence. Verdict PASS — every load-bearing claim VERIFIED with file+line evidence, no WRONG claims; INDEPENDENCE PASS (symmetric competent-engineer spec, not reverse-engineered). Confirms /money returns data:null |
| `debug/a-main/research/REVIEW-BLIND-CANCEL-B-bindability.md` | Cold review B: can the comparator's closed primitive set EXECUTE each contract clause? Verdict — response envelope EXECUTABLE (incl the {1,"error"} false-success trap via the msg gate), but ALL 3 state postconditions are NOT_CHECKABLE (no delta/snapshot/JWT; bodyless GET breaks ${field} body-resolution; runner is POST-only). So the comparator adjudicates only the lying response envelope and misses the lost refund |
| `debug/a-main/research/REVIEW-BLIND-CANCEL-C-strength.md` | Cold review C: is the contract a STRONG, fair, non-strawman baseline + schema-conformant? Verdict STRONG — asserts response + all 3 state effects incl the hard refund balance delta; requires non-expired order (avoids delta-0 trap); no material over-reach; key note: the miss is an EXECUTABILITY gap, not under-assertion. Flags a minor schema divergence vs the G2 frozen set |
| `debug/a-main/research/REVIEW-BLIND-CANCEL-RECONCILIATION.md` | Reconciliation of blind-cancel reviews A/B/C: contract VALIDATED + stays frozen (38e7aa6); comparator CONFIRMED to miss all state postconditions (expressivity gap on a STRONG contract = ideal head-to-head shape). LINCHPIN: queryAddMoney (/money) returns data:null even on success → refund observable ONLY via /account aggregate (balance=ΣMoney−ΣPayments = R−P control vs −P fault, delta R) → MIST's original /money-membership read-back INVALIDATED, must use an /account VALUE-DIFFERENTIAL (principled B2 generalization from membership to value-delta; balance trajectory needs live-verify). STRATEGIC FLAG: cancel→refund now needs 3 stacked MIST affordances (pre-established isolation + value-differential + JWT read-back) → over-fitting-optics decision (single depth site vs also a clean-membership defect) surfaced to user |
| `debug/a-main/g3-comparator-tt/blind-cancel-refund-contract.yaml` | FROZEN blind comparator contract for the TT cancel→refund head-to-head (authored 2026-07-03 by an independent cold agent, blind to MIST/the fault/the missing-compensation bug; 17 TT sources consulted). A STRONG baseline: response contract (success = status==1 AND msg=="Success.", catching the {1,"error"} false-success trap) + 3 state postconditions — (1) order status→CANCEL(4) via GET /orderservice/order/{id}; (2) REFUND = buyer's inside-payment balance +R (R=0.80*price) observable ONLY as a DELTA via GET /inside_payment/account (needs USER/ADMIN JWT, filter queryAccount by userId, pre-cancel snapshot); (3) seat release = sold-ticket counter −1 — plus 5 failure contracts + observability gaps (no discrete refund-record endpoint, /money returns data:null, no cancel email). The author DID assert the refund → the head-to-head crux is whether a stateless per-call assertion oracle can EXECUTE a balance-delta-with-snapshot check (vs MIST's differential capturing it by construction). Freeze-before-reveal record for the depth head-to-head |
| `debug/a-main/g3-comparator-tt/assertion-bindings-cancel-refund.yaml` | Executable bindings translating the FROZEN blind contract (38e7aa6) into the closed comparator primitive set: response envelope binds (HTTP_STATUS 200 + ENVELOPE_STATUS 1 + MSG_CONTAINS "Success.", catching {1,"error"}); all 3 state postconditions NOT_CHECKABLE (no snapshot/delta/arithmetic primitive, no per-request JWT, bodyless cancel GET carries no field to STATE_GET) — the executable form of the bindability the REVIEW-BLIND-CANCEL wave decided. Fed to ContractEvaluator by the head-to-head harness. PENDING ≥3-cold-review before its numbers feed claims |
| `debug/a-main/g3-comparator-tt/assertion-bindings-account-create.yaml` | Executable bindings for the AGREEMENT anchor (createAccount): response envelope (status 1 + "Create Account Success") + a POSITIVE STATE_GET (contains-submitted-fields userId on /account) that BINDS because the create carries a body — so the comparator CATCHES the lost create. The anti-strawman contrast to the bodyless cancel's NOT_CHECKABLE clauses |
| `debug/a-main/g3-comparator-tt/g3-headtohead-results.md` | RESULTS OF RECORD for the cancel→refund head-to-head (MIST B2 value-delta vs frozen response-assertion comparator), **REVIEWER-ACCEPTED (2 rounds × 3 cold reviewers)**: THREE cells N=5 stable — natural = FIRE+CAUGHT (tie + diagnostic edge), constructed = FIRE+MISSED (the clean win; PRE-FUNDED buyer → arithmetic 50→130 vs 50→50, membership can't catch), agreement (body-carrying create) = FIRE+CAUGHT (STATE_GET binds — no strawman). Carries the un-contestability argument, complementary-not-superset scope, natural-faithfulness framing, runtime-toggle mechanism + rejected restart/mesh mechanisms, machine-gate disclosures, image-tag provenance |
| `debug/a-main/g3-comparator-tt/g3-natural-faithfulness-source-check.md` | Authors' independent source re-derivation corroborating the two-stratum split: CancelController.java:45-51 catch → HTTP-200 {1,"error"} (natural provenance); CancelServiceImpl.java:92 returns {1,"Success."} even on drawbackMoney==false (the real bug) but drawBack's {0} is dead code (findByUserId → List, empty-not-null, line 305) so the clean miss needs the disclosed constructed fabricated-ack; notes a possible cancel-side "cleanfail" strengthening. Confirms the natural "fail" throw bypasses no response-shaping logic |
| `debug/a-main/g3-comparator-tt/rider2-bindability-survey.md` | Rider-2 §1 analysis product over the ENTIRE frozen TT blind set (81 entries @ 15954a8): per-endpoint state-clause bindability dispositions against the closed primitive set, generous (alias-allowed, doubt-against-MIST) + strict convention pair. **REVIEWER-ACCEPTED (3× ACCEPT-WITH-FIXES, all folded): G 69/80 = 86.25% bind / 11 structural NC; S 59/80 = 73.75%.** NC census: 3 KEY-SHAPE + 2 NESTED-ITEM-SHAPE (#76/#77 AdminTrip wrappers, review C) + 3 OBJECT-ABSENCE (#12/#23/#52 — absence on single-object reads vacuous, extractItems empty for non-array data, review A+B) + TRANSITION + RESPONSE-KEYED + BATCH. Collection-shape rule explicit (absence→list reads only; per-entity presence→entity-matches). Reading: residue = STRUCTURAL primitive-vocabulary gaps, object/aggregate categories = the cancel→refund shape; payment/compensation = 2 separately-scoped facts. Executable YAML at breadth-run build implements these dispositions exactly |
| `debug/a-main/g3-comparator-tt/REVIEW-SURVEY-RECONCILIATION.md` | Survey review reconciliation: 3× ACCEPT-WITH-FIXES; A+B converged on the OBJECT-ABSENCE BLOCKING, C additionally caught NESTED-ITEM-SHAPE (#76/#77); 11-finding disposition table all FIXED; every flip ran AGAINST the comparator (residue grew, reading strengthened). Carries the collection-shape rule + the entity-absent-primitive boundary note to the breadth-run build. STATUS: survey accepted, corrected fraction may feed the external-validity claim |
| `debug/a-main/g3-comparator-tt/breadth-executability-finding.md` | Plan-changing finding (2026-07-04): the "run 69 bindings via ComparatorRunner = mechanical" assumption was WRONG — ComparatorRunner.runEndpoint REQUIRES a per-endpoint fault_flag (full inject calibration cycle), so a full inject-based breadth needs ~69 fork lost-write flags across ~15 services = infeasible. But Rider-2 bindability is a CONTROL-LEG property (does each frozen STATE clause EVALUATE on live TT) needing NO injection: ContractEvaluator.evaluate(control) already returns PASS(BINDS)/NOT_CHECKABLE(residue)/FAIL(unbindable). CHOSEN: small control-only BindabilityRunner (no injector, no fault_flags) → representative-first then scale to 80 → empirical Rider-2 fraction. Bonus: partial inject-CATCH for adminbasic if its flag is service-wide. Authoring guide's schema/mapping still valid; only the runner changes |
| `debug/a-main/g3-comparator-tt/REVIEW-BINDABILITY-RUNNER-RECONCILIATION.md` | 3-cold-review of BindabilityRunner (commit 5b09a15) — REJECTED for the full-empirical-69/80 claim (A ACCEPT-W-FIXES / B REJECT / C ACCEPT-W-FIXES, unanimous on the decisive defects): POST-only (>60% of the census is non-POST → silently INFRA_FAILURE → biased fraction), absence unsound in control-only single-write (vacuous false BINDS on the OBJECT-ABSENCE NC class), circular/overclaim (NC residue re-encoded not measured → BINDS-side audit only), control-PASS sound only for presence+fresh-key, multi-observable ordering bug, denominator inflation. Establishes the executable breadth is LOW-ROI vs the accepted analytical survey (a sound full version = big multi-verb+setup-flow harness, still only a BINDS-side audit). Ends with the 4-option strategic fork put to the user |
| `debug/a-main/g3-comparator-tt/breadth-bindings-authoring-guide.md` | Authoring guide turning the accepted rider2 survey (69 BIND / 11 NC) into the executable `assertion-bindings-breadth.yaml` the ComparatorRunner loads: the AssertionBindings/ContractEvaluator schema + legal vocabulary (HTTP_STATUS/ENVELOPE_STATUS/ENVELOPE_DATA/MSG_CONTAINS; STATE_GET contains-submitted-fields=list membership / entity-matches-submitted-fields=per-entity echo / other-expect=single-shot absence; NOT_CHECKABLE+reason), the disposition→clause mapping table (BINDS→membership/echo, BINDS a→list absence, BINDS-P→catch clause + NOT_CHECKABLE part, NC-*→NOT_CHECKABLE verbatim reason), path source-of-truth (merged_openapi_spec), and the run protocol (mst.comparator.enabled + assertions.path; TT redeploy). Faithfulness discipline: 11 NC stay NC; author in strata + one ≥3-cold-review before any run |
| `debug/a-main/g3-comparator-tt/g3-value-delta-ground-truth.md` | Byte-level clean-win ground truth, PRE-FUNDED configuration of record (round-2 R2-1 recapture): both buyers PRESENT at baseline 50.00, byte-identical cancel acks, control final 130.00 (+80 refund) vs fault 50.00 — membership passes both legs, only the arithmetic delta discriminates; documents the machine gates. The superseded zero-baseline capture (round-1's membership-degenerate shape) retained as a marked appendix |
| `debug/a-main/g3-comparator-tt/REVIEW-HEADTOHEAD-RECONCILIATION.md` | BOTH review rounds' reconciliation. Round 1: 3× ACCEPT-WITH-FIXES, 12 findings all FIXED/DISCLOSED (membership-degeneracy MAJOR → pre-fund). Round 2: 3× ACCEPT(-WITH-FIXES) verifying the fixes are sound — 8 more findings (stale ground-truth doc MAJOR → recapture; machine gates; modeOf pin; stale comments) all LANDED. **STATUS: 3-cell result REVIEWER-ACCEPTED for paper claims**, subject to 2 standing framing rules (oracle-class scope adjacent to the clean-win claim; external-validity → Rider-2 bindability fraction) |
| `debug/a-main/g3-comparator-tt/runs/prefunded-reps.txt` | N=5 pre-funded reps (2–5) — every rep natural FIRE+CAUGHT, constructed FIRE+MISSED, probe baseline 50.00→control 130.00/fault 50.00 |
| `debug/a-main/g3-comparator-ss/blind-shipping-contract.yaml` | FROZEN blind executable comparator bindings for Sock Shop `shipping` `POST /shipping` (SUT-2 head-to-head), authored blind from primary sources (microservices-demo/shipping@master ShippingController/Shipment/HealthCheck) + live `weaveworksdemos/shipping:0.4.8`. Exactly ONE sound live check binds — `HTTP_STATUS 201`; three NOT_CHECKABLE with precise reasons: (2) id/name bare-object ECHO (envelope primitives read status/msg/data → ENVELOPE_STATUS/MSG_CONTAINS false-fail a correct success, ENVELOPE_DATA:null vacuous; no GET returns the shipment — `/shipping/{id}` is a text/plain stub), (3) the true enqueue effect (swallowed `convertAndSend` → acked-but-lost, no single-service HTTP observable), (4) service/broker liveness via `/health` (STATE_GET has no literal-value match + `extractItems` doesn't unwrap `{health:[..]}` + `/health` stays 200 when broker down). Loader-valid (mirrors the g3-comparator-tt bindings). Freeze-before-reveal for the SS comparator head-to-head |
| `debug/a-main/g3-comparator-ss/blind-shipping-contract-notes.md` | Authoring reasoning for `blind-shipping-contract.yaml`: every source (paths + URLs + live curl captures), `POST /shipping`'s real success (201 + bare `{id,name}` echo + SWALLOWED broker exception = built-in acked-but-lost), clause-by-clause bind/NOT_CHECKABLE justification (why ENVELOPE_* are unsound or vacuous here, why the `/shipping/{id}` stub cannot read back, why `/health` liveness is inexpressible), and TWO minimal primitive PROPOSALS — P1 `RESPONSE_BODY_CONTAINS` (match submitted fields against the WRITE body → binds the echo), P2 literal-match STATE_GET expect over a named collection entry + configurable `collection_key` (→ binds `/health` broker+app liveness). Verdict: service/broker liveness NOT expressible in the current closed primitive set |
| `debug/a-main/g3-comparator-ss/REVIEW-SHIPPING-HARNESS-RECONCILIATION.md` | 3-cold-review disposition for the shipping head-to-head harness (all ACCEPT-WITH-CHANGES; A oracle-reuse / B wiring / C injectors) + the fix wave: A-M1&C-1 (inject inside try — durable-fault leak), C-2 (runProcess waitFor-first — hang), C-3 (reject-publish consumers==0/depth guard), B-M1 (ShippingReadbackHttpTest), + MINORs; the standing framing rules (constructed win = observability not arithmetic value-delta; monotonic-depth runbook; self-documented-log needs live confirm); and the BLIND-AUTHOR FINDING + the P1/P2-primitive design fork (implement liveness primitive P2 for the maximally-fair comparator vs run HTTP_STATUS-only + disclose) |
| `mist-cli/src/test/java/io/mist/cli/g3/ShippingReadbackHttpTest.java` | Pins ShippingReadbackHttp against a JDK loopback HttpServer (review B-M1): the `%2f` default-vhost encoding reaches the request line UNDECODED, basic-auth header sent, 401/500 status passed through (never swallowed), transport failure → status 0. 4 tests |
| `debug/a-main/prep/g3-tt-headtohead-design.md` | G3 cancel→refund head-to-head engineering design analysis (pre-implementation): the B2-triple gap (cancel is a bodyless GET → the body-freshening isolation is a no-op → RECOMMENDATION: a bounded "pre-established isolation" strategy where the fresh userId/loginId is supplied by setup not freshened into a body); the fault must be ROUTE-SCOPED to the /drawback hop (cancel reaches inside-payment via hardcoded DNS http://ts-inside-payment-service/.../drawback/{userId}/{money}; a global sever would kill the queryAccount read-back too) → RECOMMENDATION: an Istio VirtualService fault.abort on the drawback URI prefix (source-agnostic, leaves /account live), injected via kubectl apply/delete (IstioRouteFaultInjector); the per-run setup harness (register→create→pay→cancel so a real refund exists to lose); HONEST framing of where MIST wins (response-blindness by construction + differential precision, NOT "needs no read-back knowledge" — the triple needs the same domain knowledge as the comparator's state assertion) + the disclosed possibility of a TIE if the blind author writes a correct refund state assertion; build order gated behind 3-cold-review per code change |
| `debug/a-main/prep/g3-tt-cancel-refund-defect.md` | G3 TrainTicket depth-site verification (prereg §0.5, source-grounded in the TT fork): the cancel→refund missing-compensation defect is NATURAL in CancelServiceImpl.cancelOrder (lines 64-92: drawbackMoney false → logs error but STILL returns {1,"Success."} → order cancelled, refund lost, acked success). Endpoints: write = GET /api/v1/cancelservice/cancel/{orderId}/{loginId}; lost compensation = GET /inside_pay_service/inside_payment/drawback/{userId}/{money} (saves Money{userId,money,type=D}); B2 read-back = GET /inside_pay_service/inside_payment/money (queryAddMoney, business key userId+money+type=D, global list → readback_bound). Fault = Toxiproxy sever inside-payment↔DB. Head-to-head: MIST B2 FIREs (refund absent despite acked) vs response-assertion comparator PASSES (misses). FREEZE-PROTOCOL constraint: ts-cancel-service + ts-inside-payment-service are in the frozen set's not_covered → need a blind-authored contract extension frozen-before-reveal; bodyless-GET write → fresh-order-per-run isolation |
| `debug/a-main/prep/g3-sut2-deploy-verify.md` | G3 SUT-2 (Sock Shop) deploy + live-verification record (2026-07-02): fresh kind cluster + Istio1.30 + Jaeger + Bookinfo + Sock Shop up; two live-discovered write-path fixes (Mongo image pin carts-db/orders-db → mongo:3.4 because latest=8.x removed OP_QUERY → all Mongo writes 500'd; auth+write ingress routes /register //login //card //address = eng item iv); SS-A cart triple VALIDATED end-to-end via cookie session (register→cookie→POST /cart 201→GET /cart shows submitted itemId + unitPrice, membership viable, bare-201 ack); prereg CORRECTION: ?custId= dev override NOT honored on POST on this build → SS-A isolation key = fresh registered user per run (disclosed amendment, triple unchanged); reproducible via g3-write-path-enable.sh; next = tracing (eng ii) + sensitivity probe (C-pin2) + blind-set authoring |
| `debug/a-main/g3-sut2-wildhunt-plan.md` | SUT-2 wild-hunt PLAN **v3 — EXECUTION-READY** (two review rounds, both 3/3 ACCEPT-WITH-CHANGES; A: "no further re-review needed"). v3 folded the confirming-round punch-list: S2 primary = verified-feasible queue-policy drop (`overflow: reject-publish` / `max-length`, exchange+permission-agnostic, /health-green) — invalid "unbind from default exchange" removed (shipping publishes via the default exchange), permission-revoke demoted to optional secondary (write-only, regex excludes ""); two S2 narratives pre-registered (broker-drop ≠ the shipped swallow); S1 sharpened (comparator detects the OUTAGE not the write-loss, NOT a "tie"); kill-switch reduces to S1-sufficiency; flaky-stats-DB robustness caveat + FP0 load-bearing. Target = the Sock Shop `shipping` enqueue-swallow: deployed `weaveworksdemos/shipping:0.4.8` bytecode DEFINITIVELY shows `postShipping` catches any `convertAndSend` exception, logs "Accepting anyway. Don't do this for real!", returns **HTTP 201** + the Shipment unconditionally = natural self-documented ASYNC acked-but-lost. **v1's "clean structural win" was REFUTED** — the service's own `GET /health` live-probes rabbitmq (→ err on the fault), so a health-aware comparator can catch the outage. v2 adopts a **TT-parity two-stratum** design: S1 natural sever (→ /health err → detection TIE, MIST wins on diagnosis/localization) + S2 a /health-GREEN publish loss (revoke-publish-perm / max-length-0 → comparator PASSes both legs incl /health → clean MIST win). Read-back reality (corrected): exporter dead (9419, module_up=0), /api/overview 500s, message_stats.publish absent → ONLY channel = queue depth via mgmt API :15672 (guest:guest, remote OK) + queue-master→0 (disclosed demo-only scaffold), ~5s stats lag → quiescence≫interval + rabbitmqctl ground-truth. Fault = Istio abort/RST on egress:5672 (read-back 15672 survives, verified). Comparator blind-authored incl /health clause. Honest disclosures R1-R6 (read-back doctoring, degeneracy, write-boundary-only structural miss, fault naturalness, async+read-lag, rider-not-centerpiece). Framing A; kill-switch C named. Success = MIST FIRE+FP0 both strata, per-stratum comparator outcome, N-stable, ≥3-review |
| `debug/a-main/g3-comparator-tt/REVIEW-WILDHUNT-PLAN-RECONCILIATION.md` | 3-cold-review reconciliation of wild-hunt plan v1 → drives v2. All three ACCEPT-WITH-CHANGES (worth executing, NOT kill-switch). Bytecode CONFIRMED verbatim by all 3; corrections: ack is 201 not 200; `GET /health` live-probes rabbitmq (v1's "no app-API signal" FALSE = the load-bearing finding, triply-confirmed → the /health confound); read-back far more fragile than v1 (exporter dead 9419, overview 500, message_stats.publish absent → only mgmt-API depth + queue-master→0). Disposition table maps 9 convergent findings to v2 fixes (two-stratum TT-parity design, single read-back channel + scaffold disclosure, R1 doctoring disclosure, R5 read-lag, R6 anti-pattern-class reframe, abort/RST fault, 201). Framing: Option A (two-stratum dominates single-clean-win-refuted and demo-only); kill-switch C named |
| `debug/a-main/prep/g3-sut2-wildhunt-exec-progress.md` | SUT-2 wild-hunt EXECUTION progress (plan v3). Step1+3 DE-RISK: MIST wiring feasible with NO oracle change — value-delta extraction fits the mgmt LIST endpoint `/api/queues/%2f` (array; match_field=name→shipping-task, value_field=messages), different-host+basic-auth handled by a focused g3-style harness overriding the Http seam (`defaultHttpOverride`). Step2 live: CONTROL verified (POST /shipping→201+depth 0→1+/health green); S1 natural stratum VERIFIED (rabbitmq→0 → POST 201 fabricated-ack + msg lost + /health err = diagnosis-gap); S2 BLOCKED on 3.6.8 (overflow:reject-publish is 3.7+, max-length=drop-head unsound, permission-revoke can't deny default-exchange publish w/o breaking startup, message-ttl won't drop without a consumer) → DECISION: upgrade broker to ≥3.7 for reject-publish (defect is broker-independent; + dedicated mist user since 3.7 restricts guest to loopback). Reverts recorded |
| `debug/a-main/prep/g3-rider2-comparator-protocol.md` | G3 Rider 2 — comparator binding-round + reporting protocol, PRE-REGISTERED 2026-07-02 (docs, no code): (1) the full-frozen-set binding round — at G3 bind the ENTIRE frozen blind set incl. FAILURE CONTRACTS (review-B finding 7 landmine: unbound failure contracts would score legitimate rejections as comparator flags = inflated comparator recall; binding them is pro-rigor/pro-MIST-adversarial); SUT-2/3 need NEW blind sets per A1 (deploy-gated); (2) per-SUT FP/cost protocol — MIST's ≤5% observed-gated bar (prereg §0) matched against the NEW rule that the comparator's infra-failure RATE is reported (it can't show a control FP by construction → that cost is otherwise hidden); (3) delay-vs-loss stratification satisfied by A3's bounded presence-retry at the matched 10s/500ms budget, results reported stratified by fault type for both oracles; pre-commits the protocol, not the outcome |
| `debug/a-main/prep/g3-sut2-fp-probe-result.md` | RESULT OF RECORD — SUT-2 (Sock Shop) benign FP probe (bar v2, prereg C-pin 4): MIST's B2 oracle run benignly N=30 on the 2nd SUT = **0 FP over 1200 acked benign writes, gate 100% resolved (all OBSERVED_PRESENT), syncFpBar PASS, interval [0,0]**, FP=0 across the whole timeout curve (500ms→10s). SS-B addresses+cards (600 each), cookie auth wired, HAL read-back (fix load-bearing + reviewer-accepted). pairs:[] correct for branch β (no constructed positives). Independent raw-record recount matches the report. Supports external-validity of FP behavior (2nd SUT, HAL encoding, cookie auth); does NOT claim SUT-2 detection/async/breadth. PENDING ≥3-cold-review of the result |
| `debug/a-main/prep/g3-sut2-fp-probe-report.json` | Committed machine-readable evidence copy of the SUT-2 benign FP probe report (canonical: .runtime/logs/data-integrity-reports/pairing_sockshop_g3_benign_1783149661165.json, gitignored): fpProbe {aggregate + perTriple ss-b-address/ss-b-card, each 600 records/0 fires/gateHistogram OBSERVED_PRESENT, fpVsTimeoutCurve all 0}, syncFpBar PASS (gateResolvedFraction 1.0, timeoutGatedFraction 0, value 0), asyncDisclaimer + acceptThenDropTrap disclosures, pairs:[] (branch β) |
| `debug/a-main/prep/REVIEW-SUT2-FP-RECONCILIATION.md` | 3-cold-review reconciliation of the SUT-2 FP result (30745cd): 3× ACCEPT-WITH-FIXES, all recounts match the report exactly, FP=0 confirmed GENUINE by all three (decisive: 1200 live pre-write baseline containsKey checks all ABSENT, invalidRuns=0 → real before/after delta, not always-present), bar-v2 PASS machine-computed + prereg-compliant. 7-finding disposition table: C caught the committed JSON's asyncDisclaimer being a reused TT constant FALSE about Sock Shop (has RabbitMQ) → doc provenance note; quiescence-not-exercised + pseudo-replication + readback_bound-not-exercised → threats-to-validity section; ms 9–38; jar-attribution moot. Outcome: RESULT ACCEPTED, scoped to HAL parsing + exact-match membership + cookie auth (NOT quiescence machinery = TT Gate-1) |
| `debug/a-main/prep/g3-sut2-fp-probe-records.log` | Committed raw evidence: the 1200 benign DataIntegrity records (600 address + 600 card) extracted from the gitignored .runtime/logs/mist.log — every line acked=true X-present=true gate=OBSERVED_PRESENT polls=1 ~12-19ms; independent recount = 1200 acked / 0 X-present=false / 1200 OBSERVED_PRESENT (backs g3-sut2-fp-probe-result.md) |
| `debug/a-main/prep/g3-sut2-hal-readback-finding.md` | SUT-2 live-discovered blocker + fix decision (2026-07-04): the benign FP probe failed because Sock Shop's SS-B read-backs (`GET /addresses`, `GET /cards`) are HAL/HATEOAS `{_embedded:{address\|card:[..]}}`, which `DataIntegrityRuntime.extractItems` (bare-array + `{data:[]}` only) parsed as EMPTY → a ~100% false-positive storm (parsing artifact, not a real miss); the "SS-B hooks with zero new code" premise was wrong (Spring HATEOAS is Sock Shop's native collection format). Decision: teach extractItems/parsesToCollection the HAL `_embedded` convention (flatten relations) — general (whole Sock Shop user/order surface), additive, provably inert on the frozen TrainTicket comparator (no TT body carries `_embedded`); flatten soundness rests on fresh-unique isolation keys. Includes the goal-driven verification plan (unit tests + ≥3-cold-review + trace capture + re-run). |
| `debug/a-main/prep/REVIEW-HAL-RECONCILIATION.md` | 3-cold-review reconciliation of the HAL `_embedded` extraction (commits 0a16255 + a8d7d32): A ACCEPT-WITH-FIXES, B ACCEPT-WITH-FIXES, C ACCEPT — all 3 load-bearing claims (freeze-inertness on the frozen TT comparator, flatten soundness, live-shape correctness) HOLD; every reviewer confirmed freeze-inertness via repo-wide `_embedded` grep + the monotonic add-only flatten structurally cannot manufacture a false FIRE. Disposition table (F1 empty-HAL asymmetry→doc; F2 data-first ordering pin→test; F3 cross-relation non-collision→test; F4 non-array/null robustness→test; F6 verbatim `_links` row→test; A4 non-numeric-freshening→VERIFIED MOOT by live curl; F5 keySet order→no-change). Verdict: ACCEPTED, HAL extraction cleared for the SS-B benign FP probe |
| `debug/a-main/prep/g3-sut2-triples-prereg.md` | G3 pre-specification v2 (2026-07-02, post-cold-review — implements B MAJOR-1/2 + MEDIUM-3/4 + C-pins 1-4,9-11,13): §0 R1fix as G3 PREREQUISITE with BFF-compatible bounded/row-count mechanism + bar v2 + per-SUT FP protocol (N=30, ≤5% observed-gated per SUT, NOT_EVALUABLE-SUT doesn't count toward ≥2); §0.5 TT depth site = cancel→refund compensation flow + opportunity COUNTS (TT≈6 saga flows, SS=3 sites/0 compensation, petclinic=3/0); SUT-2 Sock Shop with FOUR engineering items (completeness; two-part tracing = Node front-end auto-instr LOAD-BEARING + Java javaagents + k8s realities; ?custId= lever + cookie sessions; VirtualService /register //login routes) + pre-registered sensitivity branch (α mask / β honest-5xx → FP/breadth+wild-only); SS-A (scope,itemId) isolation pinned per-run AND per-iteration; SS-B corrected to global growing lists; SS-C async QUESTION RESOLVED NEGATIVE → sync fan-out breadth credential + shipping swallowed-enqueue recorded as natural masking-oracle/benchmark wild candidate; SUT-3 petclinic triple pre-spec complete; crisp time-boxed change triggers; live-verification checklist |

### `debug/a-main/g2-comparator/`

| Path | Description |
|------|-------------|
| `debug/a-main/g2-comparator/blind-assertions-trainticket.yaml` | FROZEN blind assertion set for the G2 Filibuster-style comparator: 79 mutating endpoints × 22 TrainTicket services (all admin + core business), authored by a fresh-context agent under the prereg blindness protocol (amendment A1: upstream-source-only provisioning, pinned at FudanSELab/train-ticket@313886e) — per endpoint: success response contract, state post-condition via a documented GET, documented failure contract, UNKNOWNs marked; global caveats include 201-unconditional creates, the travel/travel2 status-1-on-failure quirk, idempotent-success deletes, admin pass-through-at-200 |
| `debug/a-main/g2-comparator/transcript-retention-note.md` | Disclosure: the blind author's tool transcript was NOT retained (harness output file empty) — blindness audit evidence for the frozen set = the provisioning manifest (self-attested) + freeze-commit spot-checks; amendment A1's "transcript-audited" claim weakens to process-level attestation for THIS set; future authoring runs must verify transcript capture first |
| `debug/a-main/g2-comparator/comparator-runner-design.md` | G2 comparator-runner design SPEC (test-first, 2026-07-02; status header records BUILT+3-cold-reviewed+fix-wave with disclosed amendments A2/A3, §3 matched-inputs correction, §4 corrected calibration expectation per the sloppy-fabricated-ack artifact, and the operational preconditions): closed primitive set, flag-gated CLI path, clear→control(gate)→inject→fault→clear sequence, pre-stated falsifiable calibration outcome |
| `debug/a-main/g2-comparator/calibration-report.json` | Committed evidence copy of the G2 calibration run 1783032488954 (canonical logs/comparator-reports/): per-clause outcomes with cites + transportFailure + faultManifest — adminroute fault leg: HTTP/STATUS PASS (the masking), ENVELOPE_DATA+MSG FAIL (artifact), BOTH STATE clauses ABSENT at the 10s cap (10+21 polls); contacts fault leg: MSG FAIL (artifact), 5-field STATE ABSENT (19 polls); both control legs all-PASS (incl. live proof the fork honors client-supplied route ids) |
| `debug/a-main/g2-comparator/calibration-result.md` | G2 calibration RESULT — ACCEPTED (2026-07-02, run 1783032488954, ~6min): both endpoints flag via genuine STATE-clause failures (never transport) with all-clean controls → prereg §2 competence floor MET (failed-calibration branch not needed); artifact attribution disclosed (MSG both + adminroute ENVELOPE_DATA = sloppy fabricated ack; the load-bearing channel = STATE clauses, independently failing); MIST cross-reference (same faults: adminroute FIREd in run #3, contacts = G0); **G2 CLOSURE: both Gate-2 deliverables met** → NEXT = G3 with the carried riders; H9 + H9-ext honored (shipped registries stayed clean) |
| `debug/a-main/g2-comparator/assertion-bindings-trainticket-calibration.yaml` | FROZEN executable bindings for the 2 calibration endpoints (committed BEFORE any run, c4b9a08): per-clause mechanical translation citing the frozen blind contracts verbatim — adminroute (200 + status:1 + data non-null + 'Save and Modify success'; STATE_GET membership by client-supplied fresh UUID id, exploiting the frozen contract's own documented create-with-submitted-id behavior) + adminbasic contacts (200 + status:1 + data NULL + 'Create Success'; STATE_GET by accountId+documentNumber); failure clauses = NOT_CHECKABLE with reasons (valid-input calibration scope); ${uuid:}/${fresh:} harness substitution notes |
| `mist-cli/src/main/java/io/mist/cli/comparator/AssertionBindings.java` | Strict loader for the frozen executable bindings (G2 comparator): closed Primitive enum, per-primitive expect validation (HTTP_STATUS ints, ENVELOPE_STATUS int, ENVELOPE_DATA null/non-null, STATE_GET path+expect+fields, NOT_CHECKABLE reason), unknown keys/primitives fail loudly — a binding error must be comparator-infra-failure, never a quiet mis-evaluation |
| `mist-cli/src/main/java/io/mist/cli/comparator/ContractEvaluator.java` | G2 comparator oracle: evaluates the frozen bindings against one write execution (write response + state GETs via the SutClient seam); fixed per-endpoint contracts — no differential, no quiescence gate, no traces (the point of the comparison); STATE_GET membership reuses DataIntegrityRuntime.extractItems so both oracles parse collections identically; failing state GET = FAIL with transport detail (control gate maps it to infra-failure) |
| `mist-cli/src/main/java/io/mist/cli/comparator/ComparatorRunner.java` | G2 calibration runner: per endpoint clear→CONTROL write+evaluate (any control FAIL = comparator-infra-failure, fault leg skipped, never a detection)→inject→FAULT write+evaluate→clear (finally, collecting failures); report (per-clause outcomes + verdicts + truncated bodies) written BEFORE any F2 clear-failure throw with f2ClearFailure+f2FailedFlags; ${uuid:}/${fresh:} body substitution; verdict = flag iff ≥1 evaluated check fails under the fault |
| `mist-cli/src/main/java/io/mist/cli/comparator/BindabilityRunner.java` | Control-only BINDABILITY surveyor (Rider-2 breadth, breadth-executability-finding.md): NO injection, NO fault_flag — one benign CONTROL write per endpoint, then classify the frozen STATE clause via ContractEvaluator: BINDS (state clause PASSed) / BINDS_PARTIAL (catch binds, another observable NOT_CHECKABLE) / NOT_CHECKABLE (residue) / UNBINDABLE (expressible but submitted state ABSENT on a benign write → read-back shape doesn't surface it, analytical BINDS contradicted live) / INFRA_FAILURE (write response FAIL or state-GET transport) / NO_STATE_CLAUSE (response-only, excluded). Report = per-endpoint verdicts + bindabilityFraction (binds-incl-partial / residue / denominator excl. NO_STATE + INFRA). Reuses ComparatorRunner.substitute/path + ContractEvaluator verbatim. Turns the survey's analytical 69/80 into an empirical fraction. Test: BindabilityRunnerTest (8, fake SUT per verdict) |
| `mist-cli/src/main/java/io/mist/cli/comparator/RestAssuredSutClient.java` | Production SutClient: auth-applied JSON POST/GET via MstAuthHandler; sets RestAssured.baseURI from the base.url property (comparator mode is standalone — no generated test sets it) |

### `debug/a-main/benchmark/`

| Path | Description |
|------|-------------|
| `debug/a-main/benchmark/README.md` | Main-track prep (no tool code): structure + schema for contribution C2 — the first OPEN-SOURCE labeled benchmark of masked-downstream / data-integrity faults; 3 strata, oracle-verdict semantics (baseline cols deterministic, MIST cols are targets measured at Gate 1), how to add/validate a case |
| `debug/a-main/benchmark/schema/fault-case.schema.json` | JSON Schema (draft 2020-12) for one labeled fault case: target triple, injection mechanism, ground-truth label, and each oracle's expected verdict; validated with Python jsonschema (positive + negative tests pass) |
| `debug/a-main/benchmark/schema/rubric.md` | Pre-registered genuine-vs-benign labeling rubric (adjudication guide shipped with the benchmark): checkable predicates per fault class, quiescence protocol, stratum-3 κ adjudication, honesty rules |
| `debug/a-main/benchmark/cases/TT-adminroute-lostwrite-001.json` | Seed case — stratum 1 POSITIVE: adminroute acknowledged-but-lost write (LOST_WRITE_FAULT); all baseline + trace-shape oracles pass, only the read-back differential oracle flags it |
| `debug/a-main/benchmark/cases/TT-adminroute-control-001.json` | Seed case — stratum 1 NEGATIVE control: same input, fault off; the read-back oracle must not fire (per-case specificity check) |
| `debug/a-main/benchmark/cases/TT-adminbasic-contacts-lostwrite-001.json` | Seed case — stratum 1 POSITIVE: second lost write on a different service (adminbasic addContact → ts-contacts-service); fresh-UUID per-entity create = cleanest read-back FP-measurement target (triple B) |
| `debug/a-main/benchmark/cases/bookinfo-ratings-benign-001.json` | Seed case — stratum 2 NEGATIVE benign trap: Bookinfo reviews→ratings designed degradation; the naive span-error oracle false-positives, the MIST target is no_flag (the A1 precision/FP test) |
| `debug/a-main/benchmark/cases/sockshop-shipping-swallowed-enqueue-001.json` | Case 5 — stratum 1 NATURAL POSITIVE (source-grounded, schema-validated): Sock Shop shipping swallows a RabbitMQ enqueue failure and still acks ("Accepting anyway. Don't do this for real!") while the order is 2xx-acked with shipment set at creation → shipping task silently lost; found by prereg cold-reviewer B (INFO-1); status/schema/body no_flag, naive-span+trace-shape flag (REQUIRES the G3 two-part tracing mitigation), dataintegrity not_applicable BY DESIGN — documents the read-back oracle's applicability boundary (no black-box read-back reflects broker consumption) |
| `debug/a-main/benchmark/cases/TT-contacts-dedupe-benign-001.json` | Case 6 — stratum 2 BENIGN TRAP for the ack rule (schema-validated): duplicate POST /contacts soft-rejects with 2xx + body status:0 and by design persists nothing (accept-then-drop SURFACE, negative label; grounded in G0 evidence + the ACCEPT_THEN_DROP_DISCLOSURE); dataintegrity must NOT fire (ack rule excludes status:0 → vacuous base relation, true negative); body_marker_oracle FLAGS the status:0 marker and is WRONG (intended business rejection) — the discriminating gap; also documents that TT has no success-claiming accept-then-drop representative |

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
