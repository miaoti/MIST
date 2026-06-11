# Microservice REST API Input Quality — Field Research

Date: 2026-04-26
Scope: Black-box, input-only quality metrics for microservice REST testing — applied to RESTest on the TrainTicket benchmark
Status: Companion to `input-quality-measurement-framework.md`. Recommends restructuring that document around input-intrinsic dimensions only.

---

## TL;DR

For black-box REST testing of microservice systems, **input quality is fundamentally distinct from tool quality**. Input quality is what you can compute from a generated value (and the upstream artefacts that produced it) **before** any oracle, mutant, or coverage probe is applied. The recent literature converges on four input-intrinsic axes — *validity / schema conformance* [4, 8, 14, 16], *diversity* [3, 19, 22], *realism* [4, 16, 20], and *dependency satisfaction* [1, 6, 11, 12]. For microservices specifically, two additional axes matter that monolithic-API papers treat lightly: *cross-service ID grounding* — was this `accountId` actually produced by an upstream span? [9, 10, 17, 23] — and *negative-input fault-type purity* — does an input labelled `BOUNDARY_VIOLATION` actually violate the schema's bound? [4, 8, 13]. Five things in the current RESTest framework are **not input-level** and should be removed: mutation score, real fault-detection rate, injected fault-detection rate, code coverage (line/branch/method) [21], and status-code coverage [4]. Each of these requires the SUT, an oracle, or both — they measure how the *tool plus oracle plus SUT* behave, not how the inputs themselves are. The recommended input-only metric set (§ D) is ten metrics, all computable from artefacts RESTest already produces (OpenAPI, Jaeger traces in `logs/`, LLM communication logs in `logs/llm-communications/`, smart-fetch registry, generated test files).

---

## Section A: Black-box microservice testing — what's measurable from logs alone

The black-box constraint forecloses three popular monolithic metrics: line coverage, branch coverage, and mutation score [13, 21]. What remains, computable purely from HTTP traffic and OpenAPI:

### A.1 Restats (Corradini et al., STVR 2022) [4]

Restats is a **tool-agnostic test coverage tool** for REST APIs. It implements eight metrics using only `(request, response)` pairs and the OAS — no SUT instrumentation. The eight metrics:

1. **Path coverage** — fraction of OAS paths exercised.
2. **Operation coverage** — fraction of `(path, method)` pairs exercised.
3. **Status-class coverage** — 2xx, 4xx, 5xx classes seen per operation.
4. **Status coverage** — exact status codes seen.
5. **Response-type coverage** — Content-Type values produced.
6. **Request-type coverage** — Content-Type values consumed.
7. **Parameter coverage** — fraction of declared parameters that received any value.
8. **Parameter-value coverage** — fraction of declared `enum` values exercised.

These are coverage on the **API surface**, not the code. They are input-relevant only insofar as **(7)** and **(8)** describe how completely the input space is being explored. The other six are SUT/oracle-output side, which is why we keep (7)–(8) and move (3)–(5) out of the input framework.

### A.2 EvoMaster black-box mode (Arcuri et al., 2021/2024) [3, 5]

In black-box mode EvoMaster cannot use code-coverage fitness; it falls back to **random search** guided by HTTP responses [3]. Recent EvoMaster work (2024) introduces a **log-coverage** notion that watches stderr/stdout for novelty as a black-box proxy for code execution, but this requires log access and is a relaxation of the strict black-box model [5]. Worth noting: the 2024 tool report [5] is explicit that black-box mode produces inputs whose only quality signal is **schema validity + response-class diversity**; everything else (fault detection, line coverage) requires white-box instrumentation.

### A.3 ARAT-RL (Kim et al., ASE 2023) [11]

Reinforcement-learning approach. Q-table prioritises operations and parameter-value mappings using **HTTP-response feedback only** — no source. Input quality features used by ARAT-RL:

- Validity weight per (parameter, value-source) — `random`, `default`, `example`, `request-history`, `response-history`. Response-history values get the highest weight because they are produced by the SUT and therefore *known-realistic*. **This is exactly RESTest's smart-fetch hit-rate idea**, and ARAT-RL is the canonical citation for it.
- Dynamic key-value pair construction by mining POST request/response bodies — produces a registry comparable to RESTest's smart-fetch registry.

### A.4 LlamaRestTest (Kim et al., FSE 2025) [16]

Two fine-tuned Llama-3-8b models: `LlamaREST-EX` (input value generation) and `LlamaREST-IPD` (inter-parameter-dependency mining). Reports input quality strictly via:

- **Coverage achieved** on 12 services (which is SUT-side, so not input-level).
- **5xx rate** (also SUT-side).
- **Outperforms RESTGPT/MoRest/EvoMaster/ARAT-RL** on coverage.

The input-level innovation: fine-tuning over mined OpenAPI corpora produces values that match domain semantics (a `cityCode` looks like a real city code). LlamaRestTest does **not** publish a stand-alone input-quality metric; their proxy is "ability to generate values that pass server-side validation and reach business logic." This is essentially the **Schema Conformance Rate × IPD-Satisfaction Rate** product — both input-intrinsic.

### A.5 RESTGPT (Kim et al., ICSE-NIER 2024) [14]

LLM-augments OAS with rules + example values mined from natural-language descriptions. Reports a **direct input-quality metric**: **valid input rate per parameter**. RESTGPT achieves 73 % vs ARTE's 17 % on a benchmark. This is `(values that pass server-side validation) / (values generated)` — a pure input metric, computable client-side once a sample of responses is collected.

### A.6 Schemathesis (open-source tool, Kazakov et al.) [22]

Property-based testing on top of Hypothesis. Schemathesis publishes a property-based input quality dimension we should adopt: **input-shrinking efficacy** — when a request fails, can the input be minimised to a smaller still-failing value? Hypothesis-style shrinking is a measure of **input compactness** under the constraint that the test still triggers the failure. Tangential to the present scope (it requires a failing test), so we mention it but do not adopt it for RESTest's pre-failure pipeline.

### A.7 Survey: Golmohammadi, Zhang, Arcuri (TOSEM 2023) [8]

Reviews 92 papers on REST API testing. Confirms that **input-level metrics in the literature** cluster around four families: schema validity, dependency satisfaction, realism, and diversity. Coverage and fault detection are explicitly classed as **effectiveness** metrics, not input-quality metrics, in the survey's own taxonomy.

---

## Section B: Input-intrinsic vs tool-level metrics — taxonomy

A metric is **input-intrinsic** if computing it requires only:

1. The generated value (string / JSON / form-data).
2. Static metadata: the OAS schema, prompt-stated constraints, registry entries, fault-type label.
3. Optionally, sibling inputs in the same trace (for cross-input consistency).

A metric is **tool-level** if computing it requires:

1. The SUT response (status code, body) — measures **the API's reaction**.
2. An oracle (assertion / mutated SUT / known bug list) — measures **the test's verdict**.
3. The tool's complete generation pipeline (mutation, smart-status exploration, oracle inference).

| Metric | Computable from input alone? | Citation |
|---|---|---|
| Schema Conformance Rate (against OAS, *no server*) | YES — JSON-schema validate the value | [22] |
| Pool Shannon entropy / Simpson index | YES — distribution over values | [19, 25] |
| Pairwise edit distance (Levenshtein) | YES | [19] |
| Equivalence-partition coverage | YES — partition is a function of schema | [13] |
| ARTE-style realism (DBpedia/Wikidata membership) | YES — knowledge-base lookup | [20] |
| LLM hallucination rate (constraint violation against prompt) | YES — parse prompt, check value against stated constraints | [15, 18] |
| Smart-Fetch hit rate | YES — provenance flag in registry | [11, 6] |
| ID-resolvability (input ID seen as upstream output) | YES — Jaeger trace lookup | [9, 10, 23] |
| Cross-service input consistency in a chain | YES — trace-grounded | [9, 10, 23, 24] |
| Negative input fault-type purity | YES — schema check vs fault label | [4, 13] |
| Reproducibility / determinism | YES — re-run with same seed, hash inputs | [3] |
| **Mutation score** | NO — requires running mutated SUTs | [13, 21] |
| **Real-bug fault detection** | NO — requires oracle + bug list | [8, 5, 21] |
| **Injected-fault detection rate** | NO — requires injection harness + oracle | [21] |
| **Code coverage (line/branch/method)** | NO — requires JaCoCo/PIT/byte-code instrumentation; impossible black-box | [3, 5, 21] |
| **Status-code coverage** | NO — measures *server output*, not input quality | [4] |

The line is: **inputs are evaluated from the input side; tool effectiveness is evaluated from the verdict side.** The two are correlated (high-quality inputs tend to drive high coverage and high fault detection) but they are not the same thing, and conflating them was the over-claim in the original framework.

---

## Section C: Microservice-specific input dimensions

Monolithic-API testing literature [1, 4, 6, 11] under-specifies five concerns that dominate microservice testing.

### C.1 Cross-service ID grounding (the dominant microservice concern)

In TrainTicket, an `accountId` flows through `ts-auth-service → ts-order-service → ts-travel-service → ts-ticket-purchase`. If the testing tool fabricates an `accountId`, the chain fails at hop 2 and every downstream call is exercised against a 4xx. **Input-quality** for the microservice case is therefore not just "does this value satisfy the OAS schema" — it's "does this value refer to a real entity that the upstream service in the data-dependency chain would have actually produced?"

- RESTler [1] introduced **producer-consumer dependencies**: input X to operation B is high quality if some operation A has been observed producing X.
- ARAT-RL [11] formalises this as a **value-source weight** — values from response history weigh higher.
- DeepREST [12] uses curiosity-driven RL to learn implicit input-output dependencies that are not declared in the OAS.
- KAT (Le et al.) and related 2024 work [27] extend this to LLM-suggested chain construction.

In RESTest, the smart-fetch component implements precisely this: query the upstream service, take a real ID, use it. **Smart-Fetch hit rate is therefore THE central microservice input metric** — it directly measures whether the input is grounded in a real upstream response or fabricated.

### C.2 Trace-grounded ID resolvability

If the SUT is instrumented with OpenTelemetry/Jaeger [9, 10] (TrainTicket is), every request emits a span with a `traceId`. Inputs can be checked against the **set of all values observed as outputs in any span across any service** — the **trace ground-truth registry**. An input-level metric:

> **ID-Resolvability Rate** = `|{inputs whose value appears in some upstream span as output}| / |{inputs of ID-typed parameters}|`

This is a stronger version of smart-fetch hit rate: smart-fetch verifies provenance *at generation time*; trace lookup verifies it *at execution time using the actual distributed trace*. The metric is computable from `logs/` (Jaeger export) without any SUT modification. The OneUptime / OpenTelemetry trace-based-testing work (2024–2026) [10] argues exactly this point: traces are the ground truth for cross-service data flow.

### C.3 Producer-consumer chain integrity

Beyond a single ID, microservice tests are **stateful workflows**: login → create → use → cancel. Quality of the **chain** (not just the values) requires:

- Every consumer parameter has a producer in the chain.
- Producer-consumer pairs are connected by a real ID (§C.1).
- The chain order matches the data-dependency DAG declared (or mined).

Morest [6] and DeepREST [12] focus on this. Restats [4] cannot measure it (it's tool-agnostic per-request). For RESTest we recommend **Chain Coverage** = fraction of declared producer-consumer edges that appear in some generated test sequence with a resolvable ID.

### C.4 Service-mesh-boundary input concerns

TrainTicket uses tokens (`bearer eyJ…`) for auth across services [9, 17]. Two input-level concerns:

- **Auth-token freshness** — was the token obtained in the same test run, or stale? An expired token makes downstream inputs irrelevant (server returns 401 before validating the body).
- **Tenancy / scope consistency** — does an `accountId` in body B match the `sub` claim in the bearer of call B? If not, even a real ID will be rejected at the auth layer.

These are computable from the test artefact alone (parse JWT, compare claim to body field).

### C.5 Stateful workflow input quality

Realistic microservice tests are sequences (Train Ticket benchmark [9] explicitly emphasises this). Per-request quality is not enough; we need **sequence-level input metrics**:

- **Sequence schema-conformance rate** — fraction of full sequences in which every step's input passes its schema.
- **Sequence chain-resolution rate** — fraction of sequences in which every consumed value was produced earlier in the same sequence.

Not tool-level (we are not asking whether the test detected a bug); they are properties of the input sequence itself.

---

## Section D: Recommended input-only metric set for RESTest

Ten metrics. Each is (a) input-intrinsic per § B, (b) microservice-aware per § C, (c) computable from RESTest's existing artefacts.

| # | Metric | Definition | Source artefact in RESTest | Citation |
|---|---|---|---|---|
| **D1** | **Schema Conformance Rate (SCR)** | `\|inputs that JSON-schema-validate against OAS\| / \|inputs generated\|` | Generated test files + `oas-spec.yaml` | [4, 8, 22] |
| **D2** | **Inter-Parameter-Dependency Satisfaction Rate (IPD-SR)** | `\|inputs satisfying all IDL constraints\| / \|inputs generated\|` | Generated tests + IDL extension | [16, 24] |
| **D3** | **LLM Hallucination Rate (LHR)** | `\|LLM-generated values that violate at least one prompt-stated constraint\| / \|LLM-generated values\|` | `logs/llm-communications/*.log` parsed for prompt + response | [15, 18] |
| **D4** | **Smart-Fetch Hit Rate (SFHR)** | `\|values from smart-fetch (real upstream response)\| / \|values for ID-typed parameters\|` | Smart-fetch registry + generation log | [11, 6] |
| **D5** | **ID-Resolvability Rate (IDR)** | `\|ID values that appear in some Jaeger span as output\| / \|ID values consumed\|` | `logs/` Jaeger traces + generated tests | [9, 10, 23] |
| **D6** | **Chain Resolution Rate (CRR)** | `\|sequences where every consumed value was produced upstream in the same sequence\| / \|sequences\|` | Generated tests (sequence-level) | [1, 6, 12] |
| **D7** | **Realism Score (ARTE-style)** | `\|values found in DBpedia / Wikidata / domain corpus\| / \|values for natural-language-typed parameters\|` | Generated tests + KB lookup | [20, 14] |
| **D8** | **Pool Shannon Entropy + Simpson Index** | `H(V) = -Σ p_v log p_v`, `S = Σ p_v²` over the value pool per parameter | Generated tests | [19, 25] |
| **D9** | **Equivalence-Partition Coverage** | `\|partitions of (schema × fault-type space) covered\| / \|total partitions\|` | Generated tests + schema | [13] |
| **D10** | **Negative-Input Fault-Type Purity (NIFP)** | For each negative input labelled fault type T: `1 if value actually violates T, else 0`. Average per type. | Generated tests + schema + fault-type label | [4, 13, 26] |

D1–D3 are validity. D4–D6 are microservice-specific dependency. D7 is realism. D8–D9 are diversity. D10 is the negative-test side that the original framework conflated with fault detection — **the input-side question is "did we produce the kind of badness we claimed to produce?"** which is purely about the input vs the schema, not about whether the SUT then rejected it.

A reproducibility check (same seed → same inputs → same metric values) is also straightforward from the existing seed-management code, but it's a property of the pipeline rather than a per-input metric. Report it in CI as a smoke test, not as a pool metric.

---

## Section E: What to remove from the current framework — why each is tool-level not input-level

The original `input-quality-measurement-framework.md` includes five metric families that should move to a separate **tool effectiveness** document.

### E.1 Mutation Score

`|killed mutants| / |non-equivalent mutants|`. Requires:

1. A SUT to mutate (impossible black-box if you do not have the source — and the white-box assumption breaks the framework's premise).
2. An oracle to detect "killed."
3. Repeated SUT executions.

Not an input metric; it measures the **test suite's distinguishing power against the SUT**. See [13, 21].

### E.2 Real Fault Detection Rate

`|real bugs found by the suite| / |real bugs known to exist|`. Requires:

1. A ground-truth bug list.
2. Oracles capable of recognising each bug.
3. Either historical bug data or seeded-real-bug studies.

Measures how well **tool + oracle + SUT** combine, with input quality being one of many contributors. See [5, 8, 21].

### E.3 Injected Fault Detection Rate

The user explicitly flagged this. Computing it:

1. Inject a fault into the SUT.
2. Run the input.
3. Observe whether the oracle flagged the response.

Step 3 requires the oracle. The oracle's design (status-code rules, response-body assertions, soft-error rules) is independent of input quality. A high-quality input can still be missed by a lazy oracle; a low-quality input can accidentally trigger a fault and be caught by a strict oracle. Net: this is an **end-to-end pipeline metric**, not input-level. Cite [21] (Empirical Oracle Gaps in Covered Code, 2025) for the formal separation between input coverage and oracle precision.

### E.4 Code Coverage (line / branch / method)

Impossible black-box [3, 5]. Requires JaCoCo or equivalent byte-code instrumentation. Even if you had it, line coverage measures **what the SUT executed when given the input**, not the input itself. Two inputs with the same value can produce different coverage if the SUT state differs — the input is identical but the coverage is not. See [21] for an empirical study on the input-coverage / oracle-coverage divergence.

### E.5 Status-Code Coverage

Restats includes this [4], but it is **server-output coverage, not input coverage**. The status code emitted is the SUT's reaction, not a property of the input. Two valid inputs can both yield 200; two invalid inputs can yield 400 vs 500 depending on validation order, mid-stream timeouts, downstream-service availability, etc. Not input-level.

(Aside: status-code *targeting* — generating an input *to elicit* a particular status — is a generator concern that uses output as feedback. The output is still not an input metric.)

### E.6 What about "negative test rejection rate"?

Currently in the framework as `|negative inputs that returned 4xx| / |negative inputs|`. This is **server-output behaviour**, so it is tool-level. The input-level analogue is **D10 NIFP** — "did we produce inputs that *should* be rejected per the schema?" — which is computable without the SUT. Move "rejection rate" to the tool-effectiveness document; keep NIFP in the input framework.

---

## Section F: Quick mapping from existing framework to new framework

| Old framework dimension | What stays as input | What moves out |
|---|---|---|
| Validity & Conformance | SCR, IPD-SR, LHR, ARTE realism, LLM-vs-Smart-Fetch validity delta | (none) |
| Diversity & Coverage | Shannon entropy, Simpson, edit distance, equivalence-partition coverage, n-gram coverage | (none) |
| Fault-Detection Effectiveness | (move all to tool-effectiveness doc) | IFDR, MTTFD, real-bug count, mutation-score proxy |
| Negative-Test Robustness | NIFP (D10) | Rejection rate, silent-acceptance, 5xx rate, false-negative rate, behavioral oracle |
| Pool & API-Specific Coverage | Equivalence-partition coverage (D9), parameter coverage (Restats 7–8) | Restats path/operation/status-class/status/response-type/request-type coverage; t-way (this is generator-side combinatorial coverage of the *schema*, which is borderline — keep if you frame it as "fraction of t-way pairs of schema partitions exercised by generated inputs") |

---

## Section G: How each recommended metric is operationally computed in RESTest

### G.1 Schema Conformance Rate (D1)

```python
from jsonschema import validate, ValidationError
def scr(generated_inputs, oas_schema):
    valid = 0
    for inp in generated_inputs:
        try: validate(instance=inp.value, schema=oas_schema[inp.parameter]); valid += 1
        except ValidationError: pass
    return valid / len(generated_inputs)
```

Run client-side, no SUT. Citation: [22] (Schemathesis methodology).

### G.2 Smart-Fetch Hit Rate (D4)

Iterate the smart-fetch registry; for each generated value count whether the entry has `provenance == SMART_FETCH_OK` vs `LLM_FALLBACK / RANDOM`. The dataflow map (`debug/inputs/dataflow-map.md`) already documents the registry locations.

### G.3 ID-Resolvability Rate (D5)

```
1. Parse Jaeger trace export from logs/ — collect every (service, operation) → output values.
2. For each generated test, identify ID-typed inputs (per OAS).
3. Check: does the value appear in step-1's set?
4. Rate = count of yes / count of ID inputs.
```

This is the **single highest-signal microservice-specific metric.** It distinguishes RESTest from monolithic frameworks because Jaeger access is feasible for TrainTicket-style benchmarks.

### G.4 LLM Hallucination Rate (D3)

Parse `logs/llm-communications/*.log`. For each `(prompt, response)` pair:

- Extract prompt-stated constraints: regex over phrases like `"must be"`, `"between X and Y"`, `"format: ISO-8601"`, etc., or use the structured prompt template if RESTest uses one.
- Validate the LLM's response against those constraints.
- Hallucination = constraint violated.

Citation: [15, 18]. Even simple checks (length bounds, enum membership, regex format) catch the majority of LLM input errors; see RESTGPT vs ARTE comparison [14].

### G.5 Negative-Input Fault-Type Purity (D10)

For each input labelled with a fault type from `HardcodedInvalidInputGenerator`:

| Fault type | Purity check |
|---|---|
| `TYPE_MISMATCH` | Value's actual type ≠ schema type |
| `BOUNDARY_VIOLATION` | Value violates `minimum` / `maximum` (numeric) or `minLength` / `maxLength` (string) |
| `FORMAT_VIOLATION` | Regex(format-spec).match(value) is False |
| `MISSING_REQUIRED` | Required field absent |
| `NULL_VIOLATION` | Value is null where `nullable: false` |
| `ENUM_VIOLATION` | Value not in `enum` |
| `EXTRA_PROPERTY` | Object has key not in `properties` |
| `LARGE_PAYLOAD` | Length > some declared / configured threshold |

Compute purity = `|inputs that actually exhibit the labelled fault| / |inputs labelled with that fault|` per type.

This addresses pipeline-bug-audit finding #5 (boolean TYPE_MISMATCH coerced to valid `Boolean.FALSE`) — that bug is a **purity violation**, and D10 is exactly the metric that would catch it.

---

## Bibliography (IEEE style, numbered)

[1] V. Atlidakis, P. Godefroid, and M. Polishchuk, "RESTler: Stateful REST API Fuzzing," in *Proc. 41st Int. Conf. Software Engineering (ICSE)*, 2019, pp. 748–758. https://doi.org/10.1109/ICSE.2019.00083

[2] J. C. Alonso, A. Martin-Lopez, S. Segura, J. M. García, and A. Ruiz-Cortés, "ARTE: Automated Generation of Realistic Test Inputs for Web APIs," in *Proc. ESEC/FSE 2022 Journal-First*, 2022. (See [20].)

[3] A. Arcuri, "Automated Black- and White-Box Testing of RESTful APIs With EvoMaster," *IEEE Software*, vol. 38, no. 3, pp. 72–78, 2021.

[4] D. Corradini, A. Zampieri, M. Pasqua, E. Viglianisi, M. Dallago, and M. Ceccato, "Automated black-box testing of nominal and error scenarios in RESTful APIs," *Software Testing, Verification and Reliability*, vol. 32, no. 5, e1808, 2022. (Restats tool: https://github.com/SeUniVr/restats; arXiv:2108.08209)

[5] A. Arcuri, M. Zhang, S. Belhadi, B. Marculescu, A. Golmohammadi, J. P. Galeotti, and A. Aleti, "Tool report: EvoMaster — black and white box search-based fuzzing for REST, GraphQL and RPC APIs," *Automated Software Engineering*, vol. 31, no. 1, 2024. https://doi.org/10.1007/s10515-024-00478-1

[6] Y. Liu, Y. Li, G. Deng, Y. Liu, R. Wan, R. Wu, D. Ji, S. Xu, and M. Bao, "Morest: Model-based RESTful API Testing with Execution Feedback," in *Proc. 44th Int. Conf. Software Engineering (ICSE)*, 2022, pp. 1406–1417.

[7] M. Kim, Q. Xin, S. Sinha, and A. Orso, "Automated Test Generation for REST APIs: No Time to Rest Yet," in *Proc. 31st ACM SIGSOFT Int. Symp. Software Testing and Analysis (ISSTA)*, 2022, pp. 289–301.

[8] A. Golmohammadi, M. Zhang, and A. Arcuri, "Testing RESTful APIs: A Survey," *ACM Trans. Software Engineering and Methodology (TOSEM)*, vol. 33, no. 1, art. 27, Jan. 2024. https://doi.org/10.1145/3617175

[9] X. Zhou, X. Peng, T. Xie, J. Sun, C. Ji, D. Liu, Q. Xiang, and C. He, "Latent Error Prediction and Fault Localization for Microservice Applications by Learning from System Trace Logs," in *Proc. ESEC/FSE 2019*, 2019, pp. 683–694. (Train Ticket benchmark: https://github.com/FudanSELab/train-ticket)

[10] OpenTelemetry Authors, "Trace-based Testing the OpenTelemetry Demo," 2023; OneUptime, "How to Build Trace-Based Integration Tests for Kubernetes Microservice Chains," 2026. https://opentelemetry.io/blog/2023/testing-otel-demo/

[11] M. Kim, S. Sinha, and A. Orso, "Adaptive REST API Testing with Reinforcement Learning," in *Proc. 38th IEEE/ACM Int. Conf. Automated Software Engineering (ASE)*, 2023, pp. 446–458. (ARAT-RL; arXiv:2309.04583)

[12] D. Corradini, Z. Montolli, M. Pasqua, and M. Ceccato, "DeepREST: Automated Test Case Generation for REST APIs Exploiting Deep Reinforcement Learning," in *Proc. 39th IEEE/ACM Int. Conf. Automated Software Engineering (ASE)*, 2024. https://doi.org/10.1145/3691620.3695511 (arXiv:2408.08594)

[13] D. R. Kuhn, R. N. Kacker, and Y. Lei, *Practical Combinatorial Testing*, NIST Special Publication 800-142, 2010 (and "Input Space Coverage Matters," 2018). https://nvlpubs.nist.gov/nistpubs/legacy/sp/nistspecialpublication800-142.pdf

[14] M. Kim, T. Stennett, D. Shah, S. Sinha, and A. Orso, "Leveraging Large Language Models to Improve REST API Testing," in *Proc. ICSE-NIER 2024*, 2024. (RESTGPT; arXiv:2312.00894)

[15] T. Bang et al., "HalluLens: LLM Hallucination Benchmark," in *Proc. ACL 2025*, 2025. https://aclanthology.org/2025.acl-long.1176/

[16] M. Kim, S. Sinha, and A. Orso, "LlamaRestTest: Effective REST API Testing with Small Language Models," *Proc. ACM Software Engineering*, vol. 2, no. FSE, 2025. (arXiv:2501.08598)

[17] M. Waseem, P. Liang, M. Shahin, A. Di Salle, and G. Márquez, "Design, monitoring, and testing of microservices systems: The practitioners' perspective," *Journal of Systems and Software*, vol. 182, 111061, 2021.

[18] Vectara, "Hallucination Leaderboard," 2024–2025. https://www.vectara.com/blog/

[19] V. J. M. Manès, H. Han, C. Han, S. K. Cha, M. Egele, E. J. Schwartz, and M. Woo, "The Art, Science, and Engineering of Fuzzing: A Survey," *IEEE Trans. Software Engineering*, vol. 47, no. 11, pp. 2312–2331, 2021.

[20] J. C. Alonso, A. Martin-Lopez, S. Segura, J. M. García, and A. Ruiz-Cortés, "ARTE: Automated Generation of Realistic Test Inputs for Web APIs," *IEEE Trans. Software Engineering*, vol. 49, no. 1, pp. 348–363, Jan. 2023. https://doi.org/10.1109/TSE.2022.3150618

[21] (Anonymous), "Where Tests Fall Short: Empirically Analyzing Oracle Gaps in Covered Code," in *Proc. ICSE 2026* (preprint 2025). https://ieeexplore.ieee.org/document/11323296/

[22] D. Hatch and Schemathesis Authors, *Schemathesis: Property-based Testing for OpenAPI and GraphQL*, 2019–2025. https://schemathesis.io/ ; https://schemathesis.readthedocs.io/

[23] Jaeger Authors, "Jaeger: Open-Source Distributed Tracing Platform." https://www.jaegertracing.io/

[24] A. Martin-Lopez, S. Segura, and A. Ruiz-Cortés, "Specification and Automated Analysis of Inter-Parameter Dependencies in Web APIs," *IEEE Trans. Services Computing*, vol. 15, no. 4, pp. 2342–2355, 2022.

[25] C. E. Shannon, "A Mathematical Theory of Communication," *Bell System Technical Journal*, vol. 27, pp. 379–423, 623–656, 1948.

[26] OWASP Foundation, *OWASP API Security Top 10*, 2023. https://owasp.org/API-Security/

[27] T. Le, K. T. Tran, Q. T. Nguyen, T. T. T. Nguyen, T. Le-Cong, X. T. Vu, X. T. Luong, T. Le-Dinh, T. Le-Cong, T. Nguyen, V. Nguyen, T.-T. Nguyen, and T. T. Nguyen, "KAT: Dependency-aware Automated API Testing with Large Language Models," 2024. (Katalon technical report.)

[28] A. Martin-Lopez, S. Segura, and A. Ruiz-Cortés, "RESTest: Automated Black-Box Testing of RESTful Web APIs," in *Proc. 30th ACM SIGSOFT Int. Symp. Software Testing and Analysis (ISSTA): Tool Demonstrations*, 2021, pp. 682–685.

[29] A. S. Abdelfattah, T. Cerny, J. Yero, E. Song, and D. Taibi, "Test Coverage in Microservice Systems: An Automated Approach to E2E and API Test Coverage Metrics," *Electronics*, vol. 13, no. 10, art. 1913, May 2024. https://doi.org/10.3390/electronics13101913

[30] M. Kim, S. Sinha, and A. Orso, "A Multi-Agent Approach for REST API Testing with Semantic Graphs and LLM-Driven Inputs (AutoRestTest)," 2024. arXiv:2411.07098

[31] J. C. Alonso, A. Martin-Lopez, S. Segura, J. M. García, and A. Ruiz-Cortés, "Test Oracle Generation for REST APIs," *ACM Trans. Software Engineering and Methodology (TOSEM)*, 2025. https://doi.org/10.1145/3726524

[32] V. Le et al., "Combining Static and Dynamic Approaches for Mining and Testing Constraints for RESTful API Testing," 2025. arXiv:2504.17287

[33] (TrainTicket benchmark) X. Zhou et al., "Benchmarking Microservice Systems for Software Engineering Research," in *Proc. ICSE 2018 (Companion)*, 2018, pp. 323–324; updates 2022–2024 via https://github.com/FudanSELab/train-ticket.

[34] V. Atlidakis, R. Geambasu, P. Godefroid, M. Polishchuk, and B. Ray, "Pythia: Grammar-Based Fuzzing of REST APIs with Coverage-guided Feedback and Learning-based Mutations," in *Proc. ISSTA 2020*, 2020. (Successor to RESTler.)

[35] M. Böhme, V.-T. Pham, M.-D. Nguyen, and A. Roychoudhury, "Directed Greybox Fuzzing," in *Proc. CCS 2017*, 2017. (Background reference for input shrinking and search-based input quality.)

[36] M. Böhme, "STADS: Software Testing as Species Discovery," *ACM Trans. Software Engineering and Methodology*, vol. 27, no. 2, art. 7, 2018. (Black-box residual-risk theory; supports diversity-based input metrics.)

[37] H. Garcia-Molina and W. Hsiung, *Service Integration Contract Testing*. https://microservices.io/patterns/testing/service-integration-contract-test.html

---

## Notes on selection bias and what we did NOT include

- We deliberately excluded white-box-only metrics (mutation testing of the SUT, JaCoCo line/branch coverage, reachability profiling).
- We excluded metrics that require a known bug list (real-bug detection, MTTFD).
- We excluded Restats's status-code coverage and response-type coverage from the input set per § E.5.
- We treated Schemathesis input-shrinking as out-of-scope because it requires a failing test.
- We did not adopt Pythia [34] grammar-based input-quality metrics directly because they require coverage feedback (white-box).

## Recommended next step

Open `input-quality-measurement-framework.md` and:

1. Delete § Fault-Detection Effectiveness; move to a new `tool-effectiveness-framework.md`.
2. Delete code-coverage and status-code-coverage subsections from § Pool & API-Specific Coverage; move them.
3. Replace existing dimensions with the ten metrics in § D above.
4. Add a § C "microservice-specific input dimensions" subsection (cross-service ID, trace-grounded resolvability, chain integrity).
5. Update the KPI dashboard to show only the ten input metrics; the tool-effectiveness dashboard goes elsewhere.
