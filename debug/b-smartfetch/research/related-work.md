# SmartFetch Related-Work / Novelty Scan

> Produced 2026-07-15 by a web-research agent (45+ searches/fetches across arXiv, ACM DL,
> IEEE Xplore, GitHub, vendor docs) for the b-smartfetch paper track. Verbatim agent report.
> Spot-verify load-bearing citations before any submission.

*Scope: REST API test-input generation for microservices. Research conducted July 2026 via arXiv, ACM DL, IEEE Xplore, GitHub, and vendor documentation. All claims below are sourced from fetched abstracts/full text or multiple corroborating search snippets; anything I could not directly verify is flagged as such.*

## 1. Closest prior art

### 1.1 Classical dependency-graph fuzzers (pre-LLM lineage)

**RESTler** (Atlidakis, Godefroid, Polishchuk et al., *ICSE 2019*, Microsoft Research) — Infers producer-consumer dependencies by matching response-body field *names/types* to request parameter names/types declared in the OpenAPI spec (e.g., a response `id` feeds a later path parameter named `id`), then uses dynamic feedback (which request sequences got 2xx vs error) to prune infeasible edges during a single fuzzing run. Values otherwise come from a small, static, manually-curated/default "fuzzing dictionary" (`dict.json`) compiled once from the spec.
*Delta*: No LLM (2019, pre-LLM), no semantic reasoning — dependency inference is syntactic name/type matching, not "what microservice/endpoint would plausibly own `accountId`." Values are static-dictionary or same-run string-matched, never freshly fetched from a chosen live GET and never persisted across separate future runs.
[Microsoft Research](https://www.microsoft.com/en-us/research/publication/restler-stateful-rest-api-fuzzing/) | [ICSE paper PDF](https://patricegodefroid.github.io/public_psfiles/icse2019.pdf) | [GitHub](https://github.com/microsoft/restler-fuzzer)

**Morest** (Liu et al., *ICSE 2022*) — Builds a "RESTful-service Property Graph" (RPG) encoding producer-consumer edges *and* property-equivalence relations, dynamically updated during a run from execution feedback (edges added/removed based on observed responses).
*Delta*: Same-run dynamic graph via schema/property-name equivalence, not LLM semantic mapping; no cross-run persistence; no deliberate "go fetch from this specific read endpoint" step — it reuses whatever call sequence the graph walk produces.
[arXiv:2204.12148](https://arxiv.org/abs/2204.12148) | [ACM](https://dl.acm.org/doi/10.1145/3510003.3510133)

**RESTest** (Martín-López, Segura, Ruiz-Cortés et al.) + **IDLReasoner** — Black-box constraint-based testing; models an Operation Dependency Graph from output/input field-name matching, and separately lets API providers *formally document* inter-parameter constraints via the IDL4OAS OpenAPI extension, resolved by a constraint-satisfaction reasoner (IDLReasoner). Has contributed to 200+ bugs found in GitHub, Spotify, YouTube APIs.
*Delta*: Constraints are either declared by humans (IDL4OAS) or inferred via name/type matching — no LLM, no live-fetched values, no cross-run learned registry.
[GitHub](https://github.com/isa-group/RESTest) | [Springer](https://link.springer.com/chapter/10.1007/978-3-030-65310-1_33) | [IDL tool suite](https://www.sciencedirect.com/science/article/pii/S2352711024003686)

### 1.2 EvoMaster — the closest "harvest real data" precedent, but at a different layer

EvoMaster (Arcuri et al.; tool report in *Automated Software Engineering* 2024, [springer link](https://link.springer.com/article/10.1007/s10515-024-00478-1); original *ACM TOSEM 2019*) has **three distinct mechanisms** for sourcing "real" data, verified precisely against its own docs:

| Mechanism | What it actually does | How it differs from SmartFetch |
|---|---|---|
| `probOfHarvestingResponsesFromActualExternalServices`, `probOfMutatingResponsesBasedOnActualResponse`, `probOfPrioritizingSuccessfulHarvestedActualResponses` | Harvests real responses from **external third-party services that the SUT itself calls out to** (e.g., the SUT calls a payment gateway; EvoMaster lets that outbound call go through in parallel to a WireMock stub, to build a realistic mock of *that dependency*) | Opposite direction: this fakes the environment *around* the SUT using real data from the SUT's outbound calls. SmartFetch fetches from the SUT's own *inbound-facing* read API. No LLM; no semantic parameter→endpoint mapping — it's keyed by which external host/path was actually called. |
| `probOfSelectFromDatabase`, `minRowOfTable` | Issues raw **SQL `SELECT`** against the SUT's backing database to reuse existing rows as resource state, instead of `INSERT`-ing new rows | Bypasses the HTTP API entirely (white-box DB access, needs DB credentials/driver) — never a GET call, no LLM, no JSON extraction. |
| Dynamic resource-ID binding | Creates dependent resources via chained POST/PUT calls within the same run, capturing IDs from response bodies (RESTler/Morest-style) | Same-run only, schema-field-name binding, no LLM, no cross-run persistence. |
| LLM support (`llm=true`) | Test *naming* and vulnerability-class classification | Not value harvesting at all. |

*Verdict on EvoMaster*: it already normalizes the idea "probabilistically pull in real data instead of always synthesizing," which is conceptually the closest precedent to SmartFetch's percentage-gate design — but every one of its three real-data channels operates at a different layer (external dependency mocks, raw SQL, or same-run POST-response capture) than "LLM picks a live GET on the SUT's own read surface and extracts a value from JSON." **This is flagged as the single highest-risk naming collision** — a reviewer skimming EvoMaster's options doc could initially mistake `probOfHarvestingResponsesFromActualExternalServices` for prior art; the paper must explicitly distinguish "harvesting from the SUT's outbound external dependencies" (EvoMaster) from "harvesting from the SUT's own inbound read endpoints" (SmartFetch).
[EvoMaster options.md](https://github.com/WebFuzzing/EvoMaster/blob/master/docs/options.md) | [GitHub](https://github.com/webfuzzing/evomaster) | [blackbox.md](https://github.com/WebFuzzing/EvoMaster/blob/master/docs/blackbox.md)

### 1.3 RL-based testers

**ARAT-RL** (Kim, Sinha, Orso — *ASE 2023*) — RL-prioritized exploration of operations/parameters; critically, it "dynamically analyzes request and response data to inform dependent parameters," constructing key-value pairs from **its own prior responses within the same run** (any operation's response, opportunistically, not a deliberately chosen read-only producer). Q-tables reset per run — no cross-run persistence. No LLM anywhere. Reported 9.2x/2.5x/2.4x more bugs than RESTler/EvoMaster/Morest respectively.
*Delta*: Closest kin on "mine real response data dynamically," but opportunistic/same-run, no LLM, no deliberate endpoint routing, no persistence.
[arXiv:2309.04583](https://arxiv.org/abs/2309.04583) | [GitHub](https://github.com/codingsoo/ARAT-RL)

**DeepREST** (Corradini et al. — *ASE 2024*) — Deep RL with curiosity-driven exploration ordering; its "Experience-Driven Input Generator" treats *value selection* as a multi-armed bandit per parameter, rewarded by 2xx vs 4xx/5xx from the current run.
*Delta*: Learns a value-selection *policy* over a small candidate set via reward shaping, not semantic retrieval of a specific live value; no LLM; no evidence of cross-run persistence.
[arXiv:2408.08594](https://arxiv.org/abs/2408.08594)

### 1.4 LLM-era REST testers (2023–2026) — the sharpest comparison set

**AutoRestTest** (Stennett, Kim, Sinha, Orso — *ICSE 2025*; ranked #1 in all three categories at the *SBFT 2026* REST League tool competition) — Combines a Semantic Property Dependency Graph (SPDG, built statically from OpenAPI text via embedding similarity) with 5-agent Multi-Agent RL (operation/parameter/value/dependency/header agents). **The Value Agent generates parameter values purely from the LLM's own parametric knowledge** ("domain-aware inputs"), retrying and regenerating using the 4xx *error message* as feedback context when a value is rejected — it never issues a GET to fetch a real value. Reports LLM cost: ~9.6M input / 2.2M output tokens, ~$1.83 total across a competition run, ≈$0.02/run/service — a directly reusable cost benchmark.
*Delta*: This is the sharpest "LLM generates plausible values" baseline. It never retrieves an actually-existing value from the running system; it *guesses* a plausible one and iterates on rejection. **This is the paper most likely to be demanded as an experimental baseline.**
[arXiv:2501.08600](https://arxiv.org/abs/2501.08600) | [GitHub](https://github.com/selab-gatech/AutoRestTest) | [SBFT 2026 competition report, arXiv:2607.01063](https://arxiv.org/abs/2607.01063)

**RESTGPT** (Kim et al., *ICSE-NIER 2024*) — LLM (GPT-3.5) extracts machine-readable constraints and example values from the **natural-language prose in the OpenAPI spec's description fields** (97% rule-extraction precision, 73% value-generation accuracy). Purely a static spec-enrichment preprocessing pass.
*Delta*: Mines the spec's *text*, not the running system's *state*.
[arXiv:2312.00894](https://arxiv.org/abs/2312.00894) | [GitHub](https://github.com/selab-gatech/RESTGPT)

**NLP2REST** (Kim et al., *ISSTA 2023*) — Predecessor to RESTGPT; same category (NL description → formal OpenAPI rule mining).
[GitHub](https://github.com/codingsoo/nlp2rest)

**LlamaRestTest** (Kim et al., *FSE/PACMSE 2025*) — Two Llama3-8B models fine-tuned/quantized: LlamaREST-EX generates values from a dataset **mined once from 4,000+ specs (~1.8M static example parameters)**; LlamaREST-IPD detects inter-parameter dependencies by reading **error-message text** (not response body values). Full text fetched: no live HTTP requests for value sourcing, no persistent mapping registry — "persistence" here is baked-in model weights, static after fine-tuning.
*Delta*: Values are compressed-into-weights static knowledge, frozen at training time — can never reflect *this specific deployment's* actual current data (e.g., the real `accountId`s that exist in today's target instance). This freshness/instance-specificity gap is the cleanest one-line pitch for SmartFetch against this baseline.
[arXiv:2501.08598](https://arxiv.org/abs/2501.08598) | [ACM PACMSE](https://dl.acm.org/doi/10.1145/3715737)

**KAT** (Le et al., Katalon — *ICST 2024*) — GPT + Operation Dependency Graph built from the OpenAPI spec; generates test scripts/data considering operation and inter-parameter dependencies. Reports +15.7% status-code coverage over RestTestGen. No confirmed live-GET-value-harvesting mechanism; no confirmed public artifact (commercial).
[arXiv:2407.10227](https://arxiv.org/abs/2407.10227) | [ICST 2024](https://conf.researchr.org/details/icst-2024/icst-2024-papers/20/KAT-Dependency-aware-Automated-API-Testing-with-Large-Language-Models)

**RESTSpecIT** ("You Can REST Now," Decrop et al. — *ASE 2023*) — LLM (GPT-3.5) infers an entire OpenAPI spec from scratch via in-context masked prompting against a black-box API, given only its name. It does issue many live requests, but to discover routes/schema, not to mine realistic *values* for other requests.
*Delta*: solves spec-inference, not value-sourcing; different problem despite the "live LLM-guided HTTP calls" surface similarity.
[arXiv:2402.05102](https://arxiv.org/abs/2402.05102) | [GitHub](https://github.com/alixdecr/RESTSpecIT)

**RAFT** (Saha et al., reported 2025 — **CAUTION**: no primary source accessed; reconstructed from citations in a secondary MDPI 2025 paper, treat venue/details as lower-confidence) — Infers producer-consumer relationships via **noun-matching between path segments** (e.g., `/accounts/{id}` ↔ response field `account_id`), not LLM semantic reasoning. Reports higher parameter coverage/operation-success than Morest/EvoMaster/RestTestGen.
*Naming collision warning*: **do not confuse with Microsoft's unrelated "RAFT" = "REST API Fuzz Testing"**, a self-hosted CI/CD orchestration service that wraps RESTler ([microsoft/rest-api-fuzz-testing](https://github.com/microsoft/rest-api-fuzz-testing)) — same acronym, completely different artifact, much older. Use the full paper title, not the acronym, when citing.
[Citing paper, MDPI 2025](https://www.mdpi.com/2673-4591/120/1/42)

**ASTRA** ("Utilizing API Response for Test Refinement," Sondhi, Sharma, Saha — IBM Research/IIIT Delhi, *arXiv Jan 2025*) — **The conceptually closest single paper found.** Uses an LLM (Mistral Large-2) to classify natural-language *error-message text* into 14 constraint categories, identify which input parameters are "identifiers," and infer ProducerConsumer relationships between operations in the *same* spec; when a 4xx has no useful message body (22% of cases in their benchmark), it falls back to response-code heuristics. Refines an in-memory specification model that accumulates learned constraints — **session-scoped, not persisted across runs.**
*Delta*: The LLM's job is interpreting *error text* to classify constraints/dependencies, not semantically routing a bare parameter name to a producer *endpoint chosen from a multi-service registry* and then *extracting a value from a JSON body*. Same-spec only (not cross-microservice), no persistence, no x-service-name/merged-registry concept. **No public code artifact found** — flag as likely non-runnable baseline.
[arXiv:2501.18145](https://arxiv.org/abs/2501.18145)

Other 2025–2026 LLM-agent REST testers surveyed for completeness, none of which fetch live values via LLM-routed semantic mapping: **LogiAgent** (multi-agent business-logic oracle testing, arXiv:2503.15079), **RESTLess** (LLM-mined static value dataset "RTSet" + parameter-rendering-order optimization for cloud fuzzing, IEEE 2024), **MASTEST** (LLM+programmed multi-agent tool chain, arXiv:2511.18038), **RESTifAI** (workflow-based happy-path/negative test synthesis, ICSE 2026 demo, arXiv:2512.08706), **BOSQTGEN** (combinatorial value stratification via LLM, arXiv:2510.19777).

### 1.5 Microservice-specific and service-mesh-aware testing

**TrainTicket benchmark work** ("Benchmarking Component and Integration Testing in Microservices," *IEEE 2026*) — Establishes 1,365 component + 210 integration tests over TrainTicket's 41 Java microservices; cites **uTest** (black-box functional-scenario coverage) and **MACROHIVE** (grey-box, service-mesh insight). Methodological detail on their value-sourcing unavailable from abstracts — lower-confidence coverage; read primaries before citing specifics.
[IEEE](https://ieeexplore.ieee.org/document/11126132/)

**MicroFuzz** (Ant Group, *ICSE-SEIP 2024*) — Industrial-scale fuzzing framework (261 apps, 74.6M LOC) using mocking-assisted seed execution, distributed tracing, and seed refresh for determinism across services. Value realism comes from *mocking for reproducibility*, not live-fetch value mining; no LLM semantic parameter mapping evidenced.
[arXiv:2401.05529](https://arxiv.org/abs/2401.05529)

**"Fuzzing Microservices in Face of Intrinsic Uncertainties"** (Zhang, Yue, Arcuri — 2026, EvoMaster lineage) — Extends white/black-box fuzzing to handle uncertainty from cross-service state/timing; statistical robustness, not LLM-guided value discovery.
[arXiv:2603.02551](https://arxiv.org/abs/2603.02551)

**SAINT** (*ICSE 2026*, IBM Research) — White-box, static-analysis + LLM agents for enterprise Java service-level testing **without OpenAPI specs** (reads source); different framing entirely.
[arXiv:2511.13305](https://arxiv.org/abs/2511.13305)

**MIRAGE** (2026) — The clearest *inverse* of SmartFetch: an LLM **simulates/fabricates** a dependency's response on demand at runtime (reading the dependency's source code + production traces to hallucinate a plausible response), explicitly to beat record-replay's poor fidelity (0%/12%) on error/edge scenarios real captured traffic never contains. Manufacturing plausible fake data, not retrieving real data — a sharp contrast for the motivation section ("where MIRAGE fabricates a response when reality is unavailable, SmartFetch instead goes and gets the real one when it is available").
[arXiv:2604.04806](https://arxiv.org/abs/2604.04806)

**Kashef** (Almutawa, Ghabrah, Canini — KAUST workshop paper) — Multi-agent LLM driving **Selenium/DOM-level browser testing** of MSA web frontends; not REST-parameter harvesting. Their related work states: *"the majority of work is focused on unit testing and fuzzing... with limited work focusing on using LLMs for end-to-end system testing"* of microservice architectures — corroborates that the MSA+LLM intersection is thin. (Full text fetched.)
[GitHub](https://github.com/Kashef-KAUST/Kashef)

**"LLM-Based Robustness Testing of Microservice Applications"** (Tigulla & Vieira, 2026) — Full text fetched: no live GET fetching, no merged/cross-service spec, no persistent registry — a prompt-strategy study (7 styles × 3 open-weight LLMs) against one service's spec at a time.
[arXiv:2605.14202](https://arxiv.org/abs/2605.14202)

### 1.6 Schema fuzzers and documentation-conformance tools

**Schemathesis** — Property-based fuzzing (Hypothesis) purely from OpenAPI/GraphQL schema constraints; no LLM, no live-fetch, dependencies limited to declared OpenAPI Links. Industrial-strength.
[schemathesis.io](https://schemathesis.io/) | [GitHub](https://github.com/schemathesis/schemathesis)

**Dredd** — Documentation-conformance: replays the **literal example values written in the spec** and checks responses. Not generative.
[dredd.org](https://dredd.org/)

### 1.7 Retrieval-augmented framing ("RAG for test generation")

**"Retrieval-Augmented Test Generation: How Far Are We?"** (Shin et al., arXiv:2409.12682) — RAG for **unit-test generation for Python ML libraries**, retrieving from API docs/GitHub issues/StackOverflow (text corpora), not live JSON responses; different domain, different retrieval target.

**PrediQL** ("first retrieval-augmented, LLM-guided fuzzer," arXiv:2510.10407) — For **GraphQL**, not REST. Retrieves its **own accumulated execution traces, schema fragments, and prior errors within a session** (self-referential in-session memory, multi-armed-bandit strategy selection) — not a live fetch from a semantically-chosen producer endpoint across a service registry; no cross-run persistence.
*Delta*: Closest thing to "retrieval-augmented API test generation" in name, but retrieval target and mechanism both differ (self-history vs live-system-state; GraphQL vs REST).
[arXiv:2510.10407](https://arxiv.org/abs/2510.10407)

### 1.8 Industrial traffic-capture/replay tools (adjacent, different paradigm)

All solve a **backward-looking** problem (mine previously observed traffic) rather than SmartFetch's **forward-looking / just-in-time** problem (issue a *new* live call, chosen by LLM reasoning, at the moment a value is needed):

- **Keploy** — eBPF-captures real request/response/DB/queue traffic, emits YAML test+mock files reproducing what was recorded. Values frozen at capture time; the "mapping" is whatever was observed. [GitHub](https://github.com/keploy/keploy)
- **Speedscale / Proxymock** — record-replay at infrastructure level. [speedscale.com](https://speedscale.com/proxymock/)
- **Levo.ai** — eBPF capture mapped to users/roles for security fuzzing; observation-based. [levo.ai](https://www.levo.ai/use-case/api-security-testing)
- **Postman Agent Mode** — assertions from a response the user is looking at; human-in-the-loop, no autonomous discovery/registry. [Postman blog](https://blog.postman.com/testing-apis-with-postman-agent-mode-a-practical-guide/)

The backward- vs forward-looking distinction should be stated explicitly and early ("why not just use Keploy" is a predictable reviewer question).

---

## 2. The specific novelty question — direct verdict

**Question**: Is there published work where test input values are harvested *at generation time* from the live SUT's *own read APIs*, guided by an *LLM mapping parameters to producer endpoints*, with a *persistent cross-run learned registry*?

**Answer: No single paper or tool combining all of these elements was found**, across 45+ targeted searches spanning academic (arXiv/ACM/IEEE), the RAG-for-testing angle, the microservice-specific angle, and industrial traffic-capture tools. The individual ingredients are each independently well precedented (§1), but no paper combines:

1. LLM semantic routing of a bare parameter name/description to a specific producer **microservice + endpoint**, chosen from a set spanning multiple independently-specified services (the "x-service-name across a merged registry" framing appears only as industrial spec-merging *tooling* — `openapi-merge`, APIMatic — never combined with LLM-driven parameter routing in a testing context);
2. A live, authenticated GET issued *at test-generation time* specifically to mine a currently-real value (vs same-run opportunistic reuse: RESTler/Morest/ARAT-RL/ASTRA/EvoMaster resource-binding);
3. Direct LLM extraction of the value from the JSON body (tolerating semantic mismatch between field name and parameter name) rather than schema/name/noun matching or JSONPath;
4. A **persistent, cross-run** registry — nothing surveyed (2019–2026) persists a learned parameter→endpoint mapping to disk and reuses/amortizes it across *separate future runs*, let alone ranks it by EMA success rate. Closest analogues differ qualitatively: RESTler's dictionary is static/hand-curated; LlamaRestTest's fine-tuned weights are "persisted knowledge" frozen at training time, not per-deployment updated;
5. The diverse-value cache with rotation — no named precedent in REST API testing;
6. The percentage-gate live-fetch/LLM-fallback hybrid — EvoMaster's `probOf*` knobs are the closest **design-pattern** precedent (tunable probability mixing real-data vs synthetic channels), but on entirely different channels (SQL/external-mocks vs live-GET+LLM-extraction).

**Framing implication**: pitch SmartFetch as a **novel system-level combination**, not a single novel algorithm — the norm in this literature (AutoRestTest = SPDG+MARL+LLM; LlamaRestTest = two fine-tuned models; ASTRA = error-NLP+producer-consumer). Reviewers in this subfield routinely accept combination-novelty papers provided each ingredient's lineage is honestly cited.

---

## 3. Metrics and benchmarks used in this literature

**Metric categories** (per Golmohammadi/Zhang/Arcuri, *"Testing RESTful APIs: A Survey," ACM TOSEM 2023*, [arXiv:2212.14604](https://arxiv.org/abs/2212.14604)):

- **Fault detection** — most-applied; overwhelmingly proxied by **5xx** ("unique server errors"). Newer work (LogiAgent, MASTOR) pushes business-logic oracles.
- **Coverage** — schema-based (operation, parameter, status-code-class, content-type coverage); JVM SUTs add **line/branch via JaCoCo** (EvoMaster ecosystem/EMB).
- **Performance** — rarely investigated.
- **Valid-request / 2xx rate** — "successful operation count" (AutoRestTest), "operation success rate" (RAFT/ARAT-RL) — the field's proxy for "are my values realistic enough to be accepted"; precisely SmartFetch's axis.
- **LLM token/dollar cost** — emerging; AutoRestTest reports ~9.6M in / 2.2M out tokens, ~$1.83 total, ≈$0.02/run/service — directly reusable comparison point.

**Standard SUT benchmarks**:

| Benchmark | Scale | Used by |
|---|---|---|
| **EMB** | 1,655 classes, ~20k coverage targets, 440 endpoints, JVM-instrumentable | EvoMaster ecosystem, white-box comparisons |
| "12 real-world services" panel (Spotify, LanguageTool, Ohsome, …) | black-box | The Kim/Sinha/Orso lineage (RESTGPT, AutoRestTest, LlamaRestTest, ARAT-RL) — enables cross-paper comparison |
| **TrainTicket** | 41 Java microservices; 1,365 component + 210 integration tests (IEEE 2026) | microservice/integration papers |
| **PRAB** (MSR 2025) | 60 public APIs with OpenAPI + Postman docs | emerging standardization |
| **PetStore** | toy | smoke tests only |
| RESTler's original targets | Azure/GitLab-style | security fuzzing |

[Survey](https://arxiv.org/abs/2212.14604) | [PRAB / MSR 2025](https://2025.msrconf.org/details/msr-2025-technical-papers/28/A-Public-Benchmark-of-REST-APIs) | [EMB](https://researchonline.gcu.ac.uk/ws/portalfiles/portal/101887187/101884810.pdf)

---

## 4. Frank verdict

### What SmartFetch CAN plausibly claim as novel

Not any single ingredient — every piece has *some* precedent (§1). The defensible claim is the **specific combination**, within which three sub-claims look genuinely unclaimed:

1. **Cross-microservice LLM-routed retrieval**: choosing *which service and which read endpoint* (from a merged, `x-service-name`-annotated, multi-service registry) should supply a given parameter, via open-ended LLM semantic reasoning rather than schema/name/noun matching within a single spec. Everything in §1.1–1.4 infers dependencies *within one API's own operation graph*; none route across an explicit multi-service registry via LLM judgment.
2. **Freshness/instance-specificity via live fetch at generation time**, as distinct from (a) values frozen in fine-tuned weights (LlamaRestTest), (b) values hallucinated from parametric knowledge (AutoRestTest/RESTGPT/KAT), (c) values replayed from a possibly-stale traffic corpus (Keploy/Speedscale/Levo), (d) values pulled by bypassing the API via SQL (EvoMaster). SmartFetch's value is guaranteed to exist in *this* running deployment *right now* — no other surveyed tool makes that guarantee via that mechanism.
3. **Persistent, cross-run, EMA-ranked learned registry** amortizing LLM/discovery cost over time. No precedent found for this exact mechanism.

### What SmartFetch CANNOT claim as novel

- LLM-generated test values for REST APIs — established (RESTGPT, LlamaRestTest, AutoRestTest, KAT, MASTEST, RESTLess).
- Producer-consumer / inter-operation dependency inference — since RESTler (2019); refined by Morest, RESTest/IDLReasoner, RAFT, ASTRA, KAT, EvoMaster.
- Reusing response data from earlier calls *within a session* — ARAT-RL, ASTRA, RESTler, Morest, EvoMaster.
- "Seed tests with real backend data instead of synthetic" as a general idea — EvoMaster does this via SQL SELECT and external-service harvesting (different layer, same spirit — **distinguish explicitly, never ignore**).
- Probabilistic real-vs-synthetic gating — EvoMaster `probOf*` (same pattern, different channel).
- Production-realistic test data as an industry category — Keploy, Speedscale, Levo, Postman (replay, not live fetch).

### Papers that come dangerously close (rank-ordered by risk)

1. **EvoMaster's harvesting options** — dangerously close *in name* even though the mechanism differs (outbound external dependencies + raw SQL vs own inbound GET surface). **Highest risk of a reviewer's first reflexive objection**; preempt explicitly and early with precise option semantics quoted.
2. **AutoRestTest** (ICSE 2025 / SBFT 2026 winner) — dangerously close *in framing* (LLM value agent) but confirmed (full-text) to generate from parametric knowledge, not live-fetch. Natural reviewer question: "how much does live-fetching buy over LLM-guess-and-retry-on-4xx?" — answer with a head-to-head, not a citation.
3. **ASTRA** (2025, IBM) — dangerously close *conceptually* (LLM + producer-consumer + response-driven refinement) but same-spec-only, error-text-focused, session-scoped, no public artifact.

### The baselines a reviewer will demand

| Tool | Why demanded | Artifact | SUT input format |
|---|---|---|---|
| **EvoMaster** | Default comparator in the field; also the "harvesting" naming collision to preempt | Open source, maintained | BB: OpenAPI + running instance. WB (incl. SQL seeding): JVM driver class — heavy for polyglot stacks |
| **AutoRestTest** | Sharpest "LLM generates the value" baseline; current SOTA reference | Open source | OpenAPI + running target + LLM key; straightforward on our SUTs |
| **LlamaRestTest** or **RESTGPT** | Poses "why fetch live instead of recall/guess" cleanly | RESTGPT open source; LlamaRestTest needs fine-tuned Llama3-8B hosting (check release status) | OpenAPI; LlamaRestTest adds GPU/hosted inference |
| **ASTRA** | Conceptual comparison demanded by IBM-line-aware reviewers | **No public artifact** — state the limitation explicitly | N/A |
| (Context) **Keploy** | Preempts "why not record/replay" | Open source | Different framing — a paragraph, not necessarily a run |

### Bottom line

No paper found combines all of SmartFetch's ingredients; the closest triangulation is **EvoMaster (harvesting spirit) + AutoRestTest (LLM value generation) + ASTRA (LLM + producer-consumer + response-driven)**, and none of the three overlaps on live-fetch-at-generation-time-from-the-SUT's-own-read-surface-with-persistent-cross-run-registry. The novelty claim is real but must be pitched precisely as a combination/system contribution with each ingredient's lineage honestly attributed — overclaiming any single ingredient (especially "harvesting real data" or "LLM-guided producer-consumer inference") would not survive review.
