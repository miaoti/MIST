# PATH_B_POSITIONING — Defending MIST at ICSE/FSE 2027

> **Audience.** ICSE/FSE 2027 PC reviewer who knows AutoRestTest (ICSE
> 2025) deeply, knows LogiAgent (2025 preprint) and MACROHIVE (QRS'22,
> JSS'23) by name, and will reject the paper on overlap if not given
> a sharp story.
>
> **Purpose.** Phase 0 deliverable per `PATH_B_REBUILD_PLAN.md` §4 —
> internal positioning document. Defends two named contributions
> (Trace Shape Oracle, Adaptive Fault Taxonomy) sitting on top of a
> trace-driven generation engine (Root API Mode + Multi-Root + Sniper).
>
> **Status.** Draft v1. Code citations are concrete `file:line` references
> verified by grep on `mist-2.x/path-b` HEAD. Citations against
> external papers are marked `[unverified]` where the PDF could not be
> retrieved from this environment and the claim derives from
> search-snippet content.

---

## Table of contents

- [§ 1. AutoRestTest (ICSE 2025) deep dive](#1-autoresttest-icse-2025-deep-dive)
- [§ 2. LogiAgent (2025 preprint) deep dive](#2-logiagent-2025-preprint-deep-dive)
- [§ 3. MACROHIVE (QRS'22 / JSS'23) deep dive](#3-macrohive-qrs22--jss23-deep-dive)
- [§ 4. Head-to-head ablation table layout](#4-head-to-head-ablation-table-layout)
- [§ 5. One-paragraph paper pitch](#5-one-paragraph-paper-pitch)
- [§ 6. MIST capability inventory (cross-reference)](#6-mist-capability-inventory-cross-reference)

---

## 1. AutoRestTest (ICSE 2025) deep dive

**Citation.** Stennett, Kim, Sinha, Orso. *AutoRestTest: A Tool for
Automated REST API Testing Using LLMs and MARL.* ICSE-Companion 2025
(tool demonstration track), DOI `10.1109/ICSE-Companion66252.2025.00015`.
Companion research paper: Kim et al., *A Multi-Agent Approach for REST
API Testing with Semantic Graphs and LLM-Driven Inputs*, arXiv
`2411.07098` (2024). Repository:
`github.com/selab-gatech/AutoRestTest`.

**Architecture summary.** Five reinforcement-learning agents (Operation,
Parameter, Value, Dependency, Header) jointly explore an API surface.
The Semantic Property Dependency Graph (SPDG) is constructed offline
from the OpenAPI spec by GloVe-embedding parameter/body/response names
and connecting pairs above the 0.7 similarity threshold. The LLM (Value
Agent) supplies domain-realistic parameter values; the Q-tables are
updated online as the agents observe responses.

### 1.1 Ten things MIST does that AutoRestTest does not

| # | Capability                                                                                                       | MIST `file:line` citation                                                                                                                                                                                                                                                                                                                                                                                                                          | Why AutoRestTest cannot/does not                                                                                                                                                          |
| - | ---------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1 | Distributed-trace ingestion as the test-generation source (Jaeger/OTel JSON/JSONL → workflow scenarios)          | `workflow/TraceWorkflowExtractor.java:40` (class), `:52` (`extractScenarios`), `:199-210` (group by `traceId`)                                                                                                                                                                                                                                                                                                                                     | AutoRestTest's only input is the OpenAPI spec; SPDG is built from spec text via GloVe (0.7). No OTel/Jaeger ingestion module in the repo.                                                 |
| 2 | Weakly-Connected-Components shattering of multi-root scenarios (Union-Find over per-step producer/consumer edges) | `workflow/ScenarioOptimizer.java:23` (class), `:76-124` (`shatter()`); pipeline gate `workflow/pipeline/stages/Phase3ShatteringStage.java:25`                                                                                                                                                                                                                                                                                                      | AutoRestTest emits per-operation request sequences. There is no scenario-level partitioning because no scenario object exists.                                                            |
| 3 | Root API Mode: persistent `RootApiRegistry` of root entry points discovered from traces, with hierarchical IDs   | `registry/RootApiRegistry.java:53` (class), `:74-126` (`registerRootApisFromScenarios`); `testcases/MultiServiceTestCase.java:52` ("hierarchical root ID targeted by the sniper")                                                                                                                                                                                                                                                                  | AutoRestTest treats every operation as a peer node; no notion of "root entry point" vs. "internal call". Cross-service request chains are not modelled.                                   |
| 4 | Sniper Strategy: exactly one fault per variant, `FaultTarget` queue enumerating `(rootIdx, paramName, paramLocation, InvalidInputType, value)` | `generators/MultiServiceTestCaseGenerator.java:133-157` (`FaultTarget` record), `:169-208` (`buildFaultInjectionQueue`), `:500-565` ("Sniper Strategy" + per-variant resolution); writer gate `writers/restassured/MultiServiceRESTAssuredWriter.java:1580-1596` ("Sniper invariant: at most ONE entry is replaced") | AutoRestTest's agents simultaneously mutate operation/parameter/value choices; faults are not isolated per variant, so blame attribution cannot be one-fault-per-test.                    |
| 5 | Soft-error oracle for silent 2xx failures (rule cache learns `(success_set, failure_set, field_checks)` per API and short-circuits subsequent LLM calls) | `validation/SoftErrorRuleCache.java:17-30` (Javadoc), `:60-103` (`SoftErrorRule`, `FieldCheck`), `:154-292` (`evaluateFieldChecks`)                                                                                                                                                                                                                                                                                                                | AutoRestTest's reward function counts 500-class responses as faults. 2xx-with-bad-body silent errors are invisible to it.                                                                 |
| 6 | LLM-driven Root-Cause Analysis over failed-span trees (deepest-failed-span heuristic feeds an LLM RCA prompt)    | `analysis/TraceErrorAnalyzer.java:18` (class), `:331-369` (`identifyRootCauses`), `:463-619` (`generateIntelligentAnalysis` → LLM at `:619`)                                                                                                                                                                                                                                                                                                       | AutoRestTest's LLM role is restricted to value generation. It collects no causal data — no traces, no parent-child error propagation graph.                                               |
| 7 | Cross-service chained-call orchestration in emitted RestAssured tests (`capturedOutputs` map + jsonPath extraction across step boundaries) | `writers/restassured/MultiServiceRESTAssuredWriter.java:1311` (capture map), `:1644-1659` (jsonPath extraction); `testcases/MultiServiceTestCase.java:281` (`getCaptureOutputKeys`)                                                                                                                                                                                                                                                                | AutoRestTest emits independent HTTP requests; the harness is OpenAPI-spec-only and evaluation focuses on single-service benchmarks (Spotify, LanguageTool, …).                            |
| 8 | Determinism-preserving on-disk LLM call cache keyed by `SHA-256(model, backend, prompt, temperature, max_tokens)` | `llm/LLMCallCache.java:42` (class), `:124-149` (`get`/`put`), `:160-211` (atomic flush)                                                                                                                                                                                                                                                                                                                                                            | AutoRestTest caches the SPDG + Q-tables (per README) but does not cache LLM responses by prompt hash. Reruns are non-deterministic w.r.t. the Value Agent. [unverified for current HEAD] |
| 9 | Authentication-fault taxonomy beyond malformed inputs (REMOVE_AUTH, INVALID_TOKEN, EXPIRED_TOKEN, WRONG_USER, INSUFFICIENT_SCOPE, …) | `auth/AuthManipulationStrategy.java:19` (class), `:26-50` (`ManipulationType` enum); per-test use `writers/restassured/MultiServiceRESTAssuredWriter.java:1236-1262`                                                                                                                                                                                                                                                                              | AutoRestTest's Header Agent (acknowledged as a v2 addition) varies basic-auth headers only. Token-class faults are absent.                                                                |
| 10 | Schema-aware fault-type applicability filter rejecting nonsensical pairings (e.g. OVERFLOW on boolean)           | `inputs/InvalidInputType.java:6` (enum), `:81-95` (`appliesTo(oasType)`), `:66-80` (Javadoc rationale: D10 NIFP label-vs-value mismatches)                                                                                                                                                                                                                                                                                                         | AutoRestTest's Value Agent samples values via LLM + Q-learning with no typed-fault taxonomy. No applicability filter exists.                                                              |

### 1.2 Five things AutoRestTest does that MIST does not

| # | Capability                                                                                                                                                              | Why MIST does not / threat                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          |
| - | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1 | Online reward-driven adaptive test sequence generation via 5-agent Q-learning; Q-tables update after every response and re-prioritise operation orderings.             | MIST's pipeline (`Phase25Dedup → SharedPool → Phase3Shatter → Phase35Dedup → Phase4Decompose`) is an *offline batch* generator. No online learner re-prioritises operations during execution. Phase 3 of the rebuild plan (Adaptive Fault Taxonomy + FaultMiner) partially closes the gap.                                                                                                                                                                                                                       |
| 2 | GloVe-embedding-based SPDG over parameter/body/response *names* with similarity threshold 0.7; bridges endpoints in different OpenAPI groups that share a `customerId`. | MIST's `SemanticDependencyRegistry` uses noun-stem heuristics + ID-suffix regexes + actual trace-observed payloads. Semantically-similar-but-lexically-distant pairs (e.g. `clientReference` ↔ `userKey`) bridge only when observed in a trace.                                                                                                                                                                                                                                                                  |
| 3 | Coverage-driven exploration where Q-tables direct agents toward unvisited (op, param) combinations; AutoRestTest reportedly triggered an internal error on Spotify.    | MIST is trace-bounded by design: operations present in the spec but never in the trace corpus never appear in a scenario. This is intentional ("trace-as-oracle") but is a coverage limitation a reviewer will name.                                                                                                                                                                                                                                                                                            |
| 4 | Black-box deployment with zero observability instrumentation; only needs HTTP + OpenAPI.                                                                                | MIST requires OTel/Jaeger trace export. On uninstrumented SUTs MIST cannot run. Deployment-cost asymmetry must be defended as a vendor-neutrality stance, not a regression.                                                                                                                                                                                                                                                                                                                                     |
| 5 | Demonstrated empirical superiority over RESTler/EvoMaster/Morest/ARAT-RL on a 10-API benchmark suite (LanguageTool, REST Countries, Genome Nexus, OhSome, Spotify, …). | MIST is currently evaluated only on the bundled TrainTicket. Without an apples-to-apples ablation against AutoRestTest on the same SUTs, the "trace-driven > pure-spec" claim is unfalsified. This is the single most decisive PC-reviewer threat; the Phase 5 future task addresses it.                                                                                                                                                                                                                       |

### 1.3 Take-away for positioning

AutoRestTest sits on the *spec-only, online-RL* axis. MIST sits on the
*trace-grounded, offline-batch with checked invariants* axis. The
contributions do not overlap on the differentiator dimension (oracle
strength + cross-service workflow assembly), but they overlap on the
"LLM-assisted REST testing" superset. The paper must lead with the
oracle story, not the LLM story.

---

## 2. LogiAgent (2025 preprint) deep dive

**Citation.** Zhang K., Zhang C., Wang C., Zhang C., Wu Y., Xing Z.,
Liu Y., Li Q., Peng X. *LogiAgent: Automated Logical Testing for REST
Systems with LLM-Based Multi-Agents.* arXiv `2503.15079`, March 2025.

**Architecture summary.** Three LLM agents — Test Scenario Generator,
API Request Executor, API Response Validator — plus a Scenario
Scheduler and Execution Memory. GPT-4o-mini at default hyperparameters
for all agents. Inputs are per-API OpenAPI specs rendered as Markdown.
Oracle is the Validator agent's free-text natural-language analysis of
the response body against the API description. Budget: 1,000 API
requests per system. Evaluation: 12 SUTs (named: Petstore, UK
Parliament Bills API, Genome Nexus). Reported: 234 logical issues at
66.19 % accuracy, 49 5xx crashes, branch/line/method coverage
39.98/71.78/73.06 %.

### 2.1 Ten things MIST does that LogiAgent does not

| # | Capability                                                                                                                                       | MIST `file:line`                                                                                                                                                                                            | Why LogiAgent cannot/does not                                                                                                                                                                                                            |
| - | ------------------------------------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1 | OTel/Jaeger trace ingestion (JSON, JSONL, Jaeger `data[]` wrapper; multi-trace input directory)                                                  | `workflow/TraceWorkflowExtractor.java:40-92, 199-210, 281, 304, 316`                                                                                                                                          | LogiAgent's only inputs are Markdown-rendered OpenAPI specs + its own Execution Memory of prior request/response pairs. No trace ingestion, no span parser.                                                                            |
| 2 | Parent/child span hierarchy + deepest-failed-span root cause                                                                                     | `analysis/TraceErrorAnalyzer.java:136-153, 178-202, 207-241, 331-370`                                                                                                                                         | LogiAgent's oracle is a single-response LLM judgement; failure propagation across services is invisible to it because spans are never observed.                                                                                        |
| 3 | Multi-root sequence assembly via cross-trace data-dependency merging (output field of trace A reused as input of trace B; session merging)        | `workflow/TraceWorkflowExtractor.java:33-37, 500-507`; `workflow/ScenarioOptimizer.java:44-105`                                                                                                                | LogiAgent's ARG is undirected and built from spec text. It captures co-occurrence, not data lineage.                                                                                                                                  |
| 4 | WCC shattering of fat multi-root scenarios with `approvedInDedupPass` tag                                                                        | `workflow/ScenarioOptimizer.java:73-105, 125-140`; `workflow/WorkflowScenario.java:48`; `workflow/pipeline/stages/Phase3ShatteringStage.java`                                                                  | No graph-partitioning step exists; the Scenario Scheduler is a flat sequential executor.                                                                                                                                              |
| 5 | Pool-keyed sniper fault injection: `PoolKey(name, location)` keeps same-named params at different locations disjoint                              | `generators/MultiServiceTestCaseGenerator.java:64-115, 133-205, 442-466, 530-565`; `testcases/MultiServiceTestCase.java:51-68, 125-134`                                                                       | No explicit fault enumeration in LogiAgent; the LLM picks values opportunistically; no `(name, location)` keying.                                                                                                                     |
| 6 | Closed 8-category fault taxonomy + schema-aware applicability + prioritised round-robin                                                          | `inputs/InvalidInputType.java:6-115`; `inputs/InvalidInputPool.java:30-38, 77-110`                                                                                                                            | LogiAgent's Validator classifies *outcomes*, not *causes*. No `BOUNDARY_VIOLATION` vs. `OVERFLOW` distinction.                                                                                                                        |
| 7 | Deterministic seeded mode (`-Drandom.seed=42` → temperature 0 + SHA-256 LLM cache)                                                                | `llm/LLMConfig.java:541-549` (`applySeedGate`); `llm/LLMService.java:185, 200`; `llm/LLMCallCache.java`                                                                                                       | LogiAgent uses GPT-4o-mini with default hyperparameters; no seed mechanism, no response cache. Inconsistent with ICSE/FSE 30-rep protocol.                                                                                            |
| 8 | File-backed soft-error rule cache amortising validation cost to ~1 LLM call per API key                                                          | `validation/SoftErrorRuleCache.java:31-285, 365-412`; `generators/ZeroShotLLMGenerator.java:1596-1810`                                                                                                        | LogiAgent fires the Validator agent on every request; cost grows linearly with budget. MIST's cache makes the cost asymptotically free after the first observation per API.                                                            |
| 9 | OTel-driven `SemanticDependencyRegistry` with concrete output→input bindings (`provenanceBindings`)                                              | `testcases/MultiServiceTestCase.java:243-247, 308-316`; `workflow/SemanticDependencyRegistry.java`; `workflow/NounKeyMap.java`                                                                                | LogiAgent's ARG is undirected and spec-derived; it cannot record that "`tripId` produced by `POST /trips` was consumed by `POST /preserve`".                                                                                          |
| 10 | Deterministic batch tool with structured per-test fault metadata persisted (`targetFaultRootId`, `faultTypeCategory`, `targetFaultParamLocation`, `targetFaultValue`) for Allure + statistical aggregation | `testcases/MultiServiceTestCase.java:51-134`; `configuration/MstConfig.java:36-71, 265-272`                                                                                                                 | LogiAgent is an interactive multi-agent loop; per-test fault metadata is implicit in NL Validator verdicts. The 234-issue count is manually triaged.                                                                                 |

### 2.2 Five things LogiAgent does that MIST does not

| # | Capability                                                                                                                                              | Threat                                                                                                                                                                                                                                                                                          |
| - | ------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1 | Multi-agent LLM coordination (three role-prompted agents + Scheduler + Execution Memory); a genuinely *agentic* architecture                            | **Medium.** MIST's `LLMService` is a single dispatch point. Defence: multi-agent is a *generation*-side concern; MIST's contribution is the trace-grounded *oracle invariants* — orthogonal.                                                                                              |
| 2 | Business-logic verification by free-text NL inference over the response body (Validator with multi-perspective prompt)                                  | **High.** Direct overlap with MIST's `SoftErrorRuleCache` + `ZeroShotLLMGenerator.validateResponse…` path. Per plan § 3.3: subsume soft-error layer into `ResponseEnvelopeInvariant`; frame it as one invariant kind under the Trace Shape Oracle umbrella, not the headline contribution. |
| 3 | API Relationship Graph (ARG) auto-linking isolated APIs into the spec-derived graph                                                                     | **Low.** MIST's `SemanticDependencyRegistry` is strictly stronger (directed, trace-grounded, includes provenance). Position MIST's registry as the trace-grounded successor to LogiAgent's spec-text ARG.                                                                                  |
| 4 | Feedback-driven test exploration: Validator verdicts redirect the Scenario Generator toward "more complex, realistic scenarios"                         | **Medium.** MIST's `TestCaseEnhancer` + `StatusCodeExplorationEnhancer` are open-loop. Phase 3 of the rebuild plan (Adaptive Fault Taxonomy + FaultMiner) is the natural answer but is not yet implemented at the time of the positioning doc.                                            |
| 5 | Empirical breadth: 12 real-world SUTs (Petstore, UK Bills API, Genome Nexus, …) with public-online subset; 4-baseline head-to-head at matched budgets   | **High for paper.** MIST currently ships with TrainTicket only. The future-task § 5.1 onboards 10-15 SUTs. Until then, LogiAgent out-flanks MIST quantitatively even where MIST out-flanks it architecturally.                                                                          |

### 2.3 Take-away

LogiAgent overlaps with MIST's *soft-error layer alone*, not with the
broader Trace Shape Oracle (Phase 2). The plan positions the soft-error
layer as one component of TSO, not as the contribution — exactly the
out-flank strategy.

---

## 3. MACROHIVE (QRS'22 / JSS'23) deep dive

**Citations.** Giamattei L., Guerriero A., Pietrantuono R., Russo S.
*Automated Grey-Box Testing of Microservice Architectures.* IEEE QRS
2022, pp. 640-650, DOI `10.1109/QRS57517.2022.00070`. Same authors,
*Automated functional and robustness testing of microservice
architectures*, J. Syst. Softw. 207 (2024) 111857, DOI
`10.1016/j.jss.2023.111857`. Open-access QRS22 PDF available at
`iris.unina.it`.

**Architecture summary.** Three components forming a grey-box pipeline:
**uTest** (combinatorial / pairwise generation of valid + invalid
inputs from microservice specs; runs the suite), **uSauron** (monitor
aggregating inter-service call records), **uProxy** (in-mesh proxy
deployed alongside every microservice — the proxy fleet is the
observability fabric). JSS23 adds a causal-inference engine over
recorded call chains. Benchmark: TrainTicket. No LLM, no OTel/Jaeger
ingestion, no reproducibility seed surfaced. MACROHIVE is the closest
related work in spirit (microservice grey-box, TrainTicket benchmark).

### 3.1 Ten things MIST does that MACROHIVE does not

| # | Capability                                                                                                                                                             | MACROHIVE                                                                                                                              | MIST evidence                                                                                                                                                                          |
| - | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1 | OTel/Jaeger trace ingestion as the observability source                                                                                                                | No. Recordings come from `uProxy` sidecars per microservice.                                                                          | `workflow/TraceWorkflowExtractor.java:40-92` + `configuration/MstConfig.java:344-352` (Jaeger config record, `jaeger.base.url=http://localhost:16686`).                              |
| 2 | Vendor-neutral observability (no mesh deployment)                                                                                                                      | No. Per-service proxy deployment + custom inter-proxy protocol.                                                                       | Zero `Istio`/`Envoy`/`sidecar`/`service.mesh` symbols across `src/main/java/` (grep). MIST is mesh-agnostic.                                                                          |
| 3 | Trace hierarchy as oracle (root-cause failure attribution via deepest-failed-span heuristic)                                                                            | Partial. JSS23 adds causal inference *post-hoc*, but the oracle itself is HTTP-status-at-edge.                                        | `analysis/TraceErrorAnalyzer.java:18-153`: `FailedSpan.isRootCause`, `identifyRootCauses(allFailedSpans, childrenMap)`.                                                              |
| 4 | LLM-driven fault taxonomy + value synthesis                                                                                                                            | No. Combinatorial/pairwise only.                                                                                                       | `inputs/InvalidInputType.java:6-115` (8-enum taxonomy with applicability matrix); `generators/ZeroShotLLMGenerator.java:1-30`; `generators/AiDrivenLLMGenerator.java`; `llm/LLMService.java`. |
| 5 | Schema-aware fault applicability filter (OVERFLOW × boolean suppressed, …)                                                                                              | No. Pairwise tables encode no such constraint.                                                                                         | `inputs/InvalidInputType.java:81-115`: per-enum switch over `boolean/string/array/integer/...`.                                                                                     |
| 6 | Semantic dependency registry mined from OAS + traces, with five passes (config producer/consumer discovery, Swagger safety net, schema- and trace-driven JSON-path refinement) | No. Mesh proxies see calls but do not parametrise a producer/consumer graph at the parameter level.                                  | `workflow/SemanticDependencyRegistry.java:1-120, 1508 LOC total`; ID regexes at `:82-99`.                                                                                            |
| 7 | Multi-root sequence assembly via session merging + WCC shattering                                                                                                       | No. Test-suite organisation is per-edge / per-API; cross-API sequences not built from the dependency graph.                          | `workflow/ScenarioOptimizer.java:23-105`; `workflow/pipeline/stages/Phase3ShatteringStage.java`; `workflow/TraceWorkflowExtractor.java:502-510` (session merge).                      |
| 8 | Per-fault, per-parameter-location targeting (path/query/header/cookie/body) with `PoolKey(name, location)`                                                              | No. Inputs are at the API boundary; fault location inside the request envelope is not first-class.                                    | `generators/MultiServiceTestCaseGenerator.java:130-200, 533-567`.                                                                                                                    |
| 9 | Reproducibility via single deterministic seed (LLM temperature gate + SHA-256-keyed call cache)                                                                         | No. Not surfaced in QRS22 or JSS23.                                                                                                   | `util/SeededRandom.java`; `llm/LLMConfig.applySeedGate`; `llm/LLMCallCache.java`.                                                                                                    |
| 10 | Operates on an unchanged SUT (attaches at the existing tracer's export endpoint; no re-deploy of microservices)                                                         | No. Each microservice must be wrapped in a `uProxy` instance + inter-proxy protocol plumbed in.                                       | `MstConfig.Jaeger.baseUrl` defaults to `http://localhost:16686` (standard Jaeger UI/Query). `TraceWorkflowExtractor` reads from `fileOrDirPath` rather than instrumenting the SUT.   |

### 3.2 Five things MACROHIVE does that MIST does not

| # | Capability                                                                                                                                                       | MIST                                                                                                                                                                              | Implication                                                                                                                                                                              |
| - | ---------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| 1 | Live grey-box probing during execution (uProxy intercepts every internal call synchronously)                                                                     | Post-hoc trace consumption.                                                                                                                                                       | Position MIST's offline trace consumption as a *deployment-cost advantage*, not a capability regression.                                                                                |
| 2 | Per-microservice runtime monitoring data plane (mesh proxies see calls that the application logs may not surface)                                                | Sees exactly what the application emits as spans; un-instrumented services are invisible.                                                                                         | Defensible as a vendor-neutrality / portability trade-off. Note explicitly that OTel coverage is the user's responsibility.                                                              |
| 3 | Active fault injection / robustness at the transport layer (latency, drops, 5xx into specific edges)                                                             | Injects faults at the **input** layer of edge requests, not into the transport between services.                                                                                  | Future work bullet, not a defence. MIST's threat model is "caller sends bad input"; MACROHIVE's robustness arm also covers "wire misbehaves".                                          |
| 4 | Causal-inference engine over failure chains (JSS23 differentiator)                                                                                                | Tree-based root-cause attribution (deepest failed span) — a *structural* heuristic, not causal inference.                                                                         | State this distinction explicitly in related work so reviewers do not equate the two.                                                                                                  |
| 5 | In-mesh state inspection (full request/response payloads on every internal hop)                                                                                   | Sees whatever OTel chose to capture in `http.response.body` tags; falls back to schema-derived JSON paths when bodies are absent.                                                | Concede that mesh-proxy capture is more complete by construction; argue MIST's `SemanticDependencyRegistry` Pass 2a closes the worst of the gap without the deployment cost.            |

### 3.3 Take-away

MACROHIVE is the most architecturally similar competitor, and it
shipped first (QRS'22). MIST's defence is **vendor-neutral
observability + reproducibility seed + LLM-driven fault taxonomy** —
three axes MACROHIVE does not cover. The paper should explicitly cite
MACROHIVE in the introduction as the closest prior work and frame
MIST as the next-generation, mesh-free successor.

---

## 4. Head-to-head ablation table layout

The future task (§ 5.2 of the rebuild plan) runs the matrix below.
Phase 0 commits to the **structure** of the table and the
**ablation configurations**; all cells are TODO at Phase-0 close.

### 4.1 SUT columns

| Column      | SUT                                | Status                                                                                          |
| ----------- | ---------------------------------- | ----------------------------------------------------------------------------------------------- |
| C1          | TrainTicket                        | Bundled. Seed traces ready. Phase-2 invariants learned from this.                              |
| C2          | Sock Shop (Weaveworks)             | Onboard in Phase 4. Public, well-instrumented.                                                  |
| C3          | Online Boutique (Google Cloud)     | Onboard in Phase 4. OTel-native, ~10 microservices.                                            |
| C4          | DeathStarBench Social Network      | Onboard in Phase 4 if compute budget allows. Larger than the others.                            |
| C5          | EvoMaster benchmark (Features-Service) | Onboard in Phase 4. Mandatory for direct EvoMaster head-to-head.                              |
| C6          | Spotify (live API)                 | Optional; subject to API-key availability.                                                       |
| C7          | LanguageTool                       | Optional; mirrors AutoRestTest's evaluation surface.                                             |

### 4.2 Configuration rows

| Row | Configuration                            | Definition                                                                                                            | Decision-relevant claim                                                                                                                                                       |
| --- | ---------------------------------------- | --------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| R1  | MIST-full                                | All MIST phases on (Phase 1 generator + Phase 2 TSO + Phase 3 adaptive faults).                                       | The "everything on" reference column.                                                                                                                                       |
| R2  | MIST −trace-shape-oracle                 | Phase 2 invariants disabled. Soft-error cache + LLM RCA still active.                                                  | Quantifies what TSO buys above today's diagnostic trace pipeline.                                                                                                          |
| R3  | MIST −adaptive-fault                     | `mist.fault.mining.enabled=false`; fault taxonomy frozen at the 8 default categories.                                  | Quantifies what mined faults buy above the static taxonomy.                                                                                                                |
| R4  | MIST −trace-shape −adaptive              | Both ablations on. Generation still uses Root API Mode + Multi-Root + Sniper but oracle is degraded to status-code-only. | **Expected ≈ AutoRestTest-class performance.** This is the row that proves the plan's defence: the two new contributions are what MIST is, not the generation engine alone. |
| R5  | AutoRestTest                             | Vendor binary at the public commit.                                                                                    | Primary external baseline.                                                                                                                                                  |
| R6  | EvoMaster (black-box)                    | Vendor binary, black-box mode for parity.                                                                              | Mandatory baseline.                                                                                                                                                          |
| R7  | RESTler                                  | Vendor binary at upstream release.                                                                                     | Mandatory baseline.                                                                                                                                                          |
| R8  | Morest                                   | Vendor binary at upstream release.                                                                                     | Mandatory baseline.                                                                                                                                                          |
| R9  | LogiAgent                                | If artefact released by submission time, run; otherwise omit and explain.                                              | Direct soft-error overlap; necessary to prove MIST's TSO out-flanks LogiAgent's single-response Validator.                                                                  |

### 4.3 Cell metrics (per (row, column) cell)

Each cell holds the **trio**: (1) operation coverage %, (2) faults
detected (5xx + soft-error verdicts, separately reported), (3) time-to-
first-fault (median over 30 reps). Vargha-Delaney A₁₂ + Mann-Whitney
U with Bonferroni correction (m = number of pairwise comparisons in
the table) is computed pairwise against R1.

Cells are explicitly **TODO** at Phase-0 close. No premature numbers.

### 4.4 Acceptance condition

The ablation passes its evidentiary bar if **R4 ≈ R5 within statistical
noise across ≥ 3 SUTs** while **R1 dominates R5 on at least one of
(coverage, faults, time-to-first-fault) with A₁₂ ≥ 0.71 across the
same SUTs**. If R1 ≠ R4 by a smaller margin, the contribution story
is weaker than claimed; if R1 > R4 ≫ R5 holds, the story is exactly
the one the paper wants.

---

## 5. One-paragraph paper pitch

> **MIST: trace-grounded REST testing with checkable trace invariants
> and adaptive fault mining.**
> Existing REST test generators are spec-bound and oracle-light: they
> sample inputs from the OpenAPI surface and stop at the HTTP boundary
> (RESTler, EvoMaster, Morest, ARAT-RL, AutoRestTest), or they ask an
> LLM to read the response body (LogiAgent). We argue that the
> distributed trace produced by every modern microservice deployment
> is a stronger oracle than either. MIST learns per-API *trace shape
> invariants* — expected span tree, expected status propagation,
> expected timing envelope, expected response-envelope schema — from
> a small seed corpus of known-good runs, and checks subsequent runs
> against them at first-class assertion strength. A second
> contribution, the *Adaptive Fault Taxonomy*, replaces the legacy
> fixed fault-category enum with a registry that the LLM can extend
> with SUT-specific categories mined from observed 4xx/5xx responses
> and OpenAPI description text. Both contributions ride on a
> trace-driven generation engine — Root API Mode + Multi-Root
> Sequence assembly via WCC shattering + Sniper Strategy — that
> already differentiates MIST from spec-only baselines. We evaluate
> on 5-7 microservice SUTs against five baselines and show that
> stripping the two new contributions degrades MIST to AutoRestTest-
> class performance, quantifying exactly what the trace layers buy.

**Constraints honoured.** The pitch does not name LLMs as a
contribution. It names exactly two contributions (Trace Shape Oracle,
Adaptive Fault Taxonomy). Root API Mode + Sniper is supporting work.

---

## 6. MIST capability inventory (cross-reference)

This is the inventory the comparison tables above cite. Each row was
verified via grep on `mist-2.x/path-b` HEAD.

| # | Capability                                       | Primary entry point                                                                                                                                                | One-line description                                                                                                                                                  |
| - | ------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1 | Trace ingestion from Jaeger/OTel                 | `workflow/TraceWorkflowExtractor.java:52` (`extractScenarios`)                                                                                                     | Parses OTel/Jaeger JSON trace spans into `WorkflowScenario` workflow models.                                                                                          |
| 2 | Multi-Root Sequence via WCC shattering           | `workflow/pipeline/stages/Phase3ShatteringStage.java:25`                                                                                                            | Pipeline stage that shatters multi-root traces into per-WCC single-root scenarios.                                                                                   |
| 3 | Phase pipeline orchestrator                      | `workflow/pipeline/WorkflowPipeline.java:14`                                                                                                                        | Sequential runner of the 5 stages (Phase2.5 Dedup → SharedPool → Phase3 Shatter → Phase3.5 Dedup → Phase4 Decompose).                                              |
| 4 | Semantic Dependency Registry                     | `workflow/SemanticDependencyRegistry.java:1`                                                                                                                        | Cross-API producer/consumer registry built via 5-pass OAS + trace analysis.                                                                                          |
| 5 | NounKeyMap (S-2)                                 | `workflow/NounKeyMap.java:32`                                                                                                                                       | YAML-driven noun→parameter-name map for path-segment semantic extraction.                                                                                            |
| 6 | MstConfig POJO + validator (A-6)                 | `configuration/MstConfig.java:28`                                                                                                                                   | Immutable typed config that materialises ~30 system properties into grouped inner classes; `MstConfigValidator` at line 30 enforces strict mode.                    |
| 7 | LLM input synthesis (AI-driven)                  | `generators/AiDrivenLLMGenerator.java:12`                                                                                                                            | Delegates to `ZeroShotLLMGenerator` for parameter value generation via LLM.                                                                                          |
| 8 | LLM zero-shot generator                          | `generators/ZeroShotLLMGenerator.java:30`                                                                                                                            | Zero-shot LLM querier producing realistic sample values for any parameter without category enumeration.                                                              |
| 9 | LLM call cache (S-4)                             | `llm/LLMCallCache.java:42`                                                                                                                                            | SHA-256-keyed file-backed JSON cache mapping `(model, backend, prompt, settings)` to LLM response; default `.mist/llm-call-cache.json`.                              |
| 10 | Seed-gated LLM temperature                       | `llm/LLMConfig.java:548` (`applySeedGate`)                                                                                                                            | Drops temperature to 0.0 when `-Drandom.seed` is set; greedy decoding for reproducibility.                                                                            |
| 11 | Sniper Strategy                                  | `generators/MultiServiceTestCaseGenerator.java:500`                                                                                                                  | One-fault-per-variant negative test policy; `FaultTarget` queue at line 169.                                                                                          |
| 12 | Pool-keyed faults (A-5b)                         | `generators/MultiServiceTestCaseGenerator.java:97` (`PoolKey`)                                                                                                       | `(paramName, paramLocation)` composite type disambiguating same-name params at different locations.                                                                  |
| 13 | Header/cookie fault emission                     | `writers/restassured/MultiServiceRESTAssuredWriter.java:1609`                                                                                                        | Writer code generation emitting `req.header(...)` and `req.cookie(...)` for negative variants.                                                                       |
| 14 | Soft-error rule cache                            | `validation/SoftErrorRuleCache.java`; `configuration/MstConfig.java:265` (`SoftErrorCache`)                                                                          | `.mist/soft-error-rule-cache.json` per root API: success-set / failure-set / field-checks. Subsumed by Phase 2's `ResponseEnvelopeInvariant`.                       |
| 15 | Trace Error Analyzer                             | `analysis/TraceErrorAnalyzer.java:18`                                                                                                                                | Runtime Jaeger trace analyser; `analyzeTrace()` line 136; `generateIntelligentAnalysis()` line 463.                                                                  |
| 16 | Parameter Error Analysis cache (A-7)             | `configuration/MstConfig.java:285` (`ParameterErrorCache`)                                                                                                            | `.mist/parameter-error-analysis-cache.json` for LLM verdict signatures.                                                                                              |
| 17 | MultiService test case carrier                   | `testcases/MultiServiceTestCase.java:13`                                                                                                                              | POJO with `faultTypeCategory` (line 56) + `targetFaultParamLocation` (line 68).                                                                                       |
| 18 | MultiServiceRESTAssuredWriter                    | `writers/restassured/MultiServiceRESTAssuredWriter.java:35`                                                                                                            | JUnit + REST-Assured code emitter; trace integration via `TraceErrorAnalyzer` imported at line 27.                                                                  |
| 19 | TestCaseEnhancer (bolt-on)                       | `enhancer/TestCaseEnhancer.java:22`                                                                                                                                   | LLM-powered post-generation enhancer.                                                                                                                                |
| 20 | StatusCodeExplorationEnhancer (bolt-on)          | `enhancer/StatusCodeExplorationEnhancer.java:37`                                                                                                                       | Status-code coverage enhancer post-execution.                                                                                                                        |
| 21 | InvalidInputType 8-category enum                 | `inputs/InvalidInputType.java:6`                                                                                                                                       | TYPE_MISMATCH, REGEX_MISMATCH, SEMANTIC_MISMATCH, OVERFLOW, EMPTY_INPUT, NULL_INPUT, SPECIAL_CHARACTERS, BOUNDARY_VIOLATION. **Retired by Phase 3.**                |
| 22 | InvalidInputPool                                 | `inputs/InvalidInputPool.java:9`                                                                                                                                       | Pool data structure managing per-type round-robin rotation.                                                                                                            |
| 23 | CLI entry-point                                  | `main/TestGenerationAndExecution.java` (2,423 lines pre-lift; MST branches at 155, 181, 199, 374, 431, 565, 597, 639, 656, 659, 688, 690)                            | The current entry. Phase 1.A lifts the MST branch into `MistRunner`.                                                                                                  |
| 24 | WorkflowPipeline + 5 stages                      | `workflow/pipeline/stages/{Phase25DedupStage:19, SharedPoolGenerationStage:25, Phase3ShatteringStage:25, Phase35DedupStage:25, Phase4DecompositionStage:21}`           | The five pipeline stages owning lifted phase bodies.                                                                                                                 |
| 25 | S-1b lift helpers                                | `workflow/pipeline/stages/{SharedPoolSupport:39, DedupSupport:27, DecompositionSupport:23, StageSupport:30}`                                                          | Helpers lifted from the generator by fix S-1b.                                                                                                                        |

---

## Acceptance check (Phase 0 § 4.5 of plan)

- [x] § 1. AutoRestTest deep dive (10 differentiators + 5 threats + code citations).
- [x] § 2. LogiAgent deep dive (10 differentiators + 5 threats).
- [x] § 3. MACROHIVE deep dive (10 differentiators + 5 threats).
- [x] § 4. Head-to-head ablation table layout (rows, columns, cells = TODO).
- [x] § 5. One-paragraph paper pitch — two named contributions, no LLM-as-contribution.
- [ ] User review and sign-off on the two contributions to defend.
- [ ] User confirmation that "MIST −trace-shape −adaptive" ≈ AutoRestTest is the right ablation framing.

The two unticked items belong to the user; the executing agent cannot
tick them.

---

*End of positioning document, v1.*
