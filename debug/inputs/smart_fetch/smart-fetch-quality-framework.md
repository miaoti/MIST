# Smart-Fetch Quality Measurement Framework for RESTest

Date: 2026-05-05
Companion documents:
- `debug/inputs/input-quality-measurement-framework.md` — parent framework, D1–D10 (Validity, Microservice Grounding, Realism, Diversity, Negative-Adversariness)
- `debug/inputs/microservice-input-quality-research.md` — bibliography survey
- `debug/inputs/dataflow-map.md` — provenance map (priorities 0–7) inside RESTest

This document is **smart-fetch-specific**. It measures how well RESTest's `SmartInputFetcher` subsystem performs the *internal* job of mapping a parameter to an upstream producer service, calling that service, and extracting a value — independent of whether the resulting value happens to be schema-valid (D1) or trace-resolvable (D5). The parent framework's D1–D10 already cover end-state input quality. The seven smart-fetch metrics here (S1–S7) cover the *process* by which Smart-Fetch produces those inputs in the first place.

---

## 1. Scope and definitions

### 1.1 Unit of analysis: a "smart-fetch attempt"

A *smart-fetch attempt* is one invocation of `SmartInputFetcher.fetchSmartInput(ParameterInfo)` for a single parameter that survives the up-front coin-flip against `smart.input.fetch.percentage` and is therefore actually routed through the smart pipeline (rather than dropped to LLM at line 199 of `SmartInputFetcher.java`). Each attempt is annotated with the following ground-truth labels at runtime:

| Field | Source | Notes |
|---|---|---|
| `attempt_id` | UUID | Unique per fetch call. |
| `param_name`, `param_type` | `ParameterInfo` | From the OpenAPI spec for the consumer operation. |
| `priority_chain_used` | `fetchFromSmartSource()` | One of `TRACE` (P0), `REGISTRY_HIT`, `LLM_DISCOVERY`, `PATTERN_DISCOVERY`. |
| `services_attempted`, `endpoints_attempted` | `discoverByLLM`, `discoverByPatterns` | List of `(service, endpoint)` pairs queried. |
| `http_status` per attempt | `fetchFromApiMapping` line 446 | Producer call result. |
| `extraction_method` | `extractValueDirectlyFromResponse` vs. `extractValueWithSimpleFallback` | Whether LLM or fallback semantics produced the value. |
| `value_returned` | the return value of `fetchSmartInput` | After format-for-schema. |
| `fallback_to_llm` | true iff `fallbackToLLM()` was reached | Smart-fetch failure. |
| `cache_hit_path` | `cache` vs. `diverseValueCache` vs. `null` | Which cache, if any, served the value. |
| `time_to_value_ms` | wall-clock | Discovery + HTTP + extraction. |

This is a strict superset of the data captured by the parent framework's `InputQualityLogger`. We will instrument it as an additional CSV (`smart-fetch-trace.csv`) in §3.

### 1.2 Gold truth

For *Service Discovery Quality* and *Endpoint Selection Quality*, the gold truth is the union of two sources:

1. **OpenAPI extension `x-service-name`.** RESTest's `OpenAPIEndpointDiscovery` already parses this tag (see `OpenAPIEndpointDiscovery.java:79-101`). For each parameter consumed by an operation in the merged spec, we walk the spec for operations whose response schema contains a field whose name or `$ref` semantically matches the parameter (`accountId` → `Account.id`, `stationName` → `Station.name`). The set of `(service, endpoint)` pairs producing those fields is `Gold_Producers(P)`.

2. **Jaeger trace ground truth.** Replay the same merged-spec operation in a baseline run with full Jaeger export. For each parameter `P` *consumed* by an operation, walk *backwards* through the call tree to the closest ancestor span whose response payload contained the value. The owning service and operation form `Gold_TraceProducers(P)`. This is what RESTest's `MultiServiceTestCaseGenerator` already populates onto `ParameterInfo.traceProducerEndpoints` at `MultiServiceTestCaseGenerator.java:943`. It is the closest available analogue to the producer-consumer DAG defined by RESTler [1] and Morest [6].

The empirical gold set for a parameter is `Gold(P) = Gold_Producers(P) ∪ Gold_TraceProducers(P)`. When the two disagree, we keep both, because OpenAPI captures *declared* producers and Jaeger captures *observed* ones; a healthy pipeline should hit both.

### 1.3 Explicit non-scope (already covered elsewhere)

To avoid duplicating the parent framework, the seven metrics here **deliberately exclude**:

| Out of scope | Belongs in |
|---|---|
| Whether the returned value satisfies the schema | D1 SCR (parent) |
| Whether the value appears in any Jaeger output | D5 IDR (parent) |
| Whether the test sequence chain resolves all IDs | D6 CRR (parent) |
| LLM hallucination on prompt-stated constraints | D3 LHR (parent) |
| Realism (DBpedia/Wikidata membership) | D7 (parent) |
| Negative-input fault-type purity | D10 (parent) |
| End-to-end fault detection / coverage | tool-effectiveness framework (TBD) |

The smart-fetch family answers *the orthogonal question*: when Smart-Fetch decides to act, **does it act well as an information-retrieval and value-harvesting subsystem?**

---

## 2. The seven smart-fetch metrics (S1–S7)

| # | Metric | Family |
|---|---|---|
| **S1.1** | Service Discovery Precision (SDP) | Service Discovery Quality |
| **S1.2** | Service Discovery Recall (SDR) | Service Discovery Quality |
| **S2** | Endpoint Selection Hit Rate (ESHR) — conditional on a correct service | Endpoint Selection Quality |
| **S3.1** | Direct-Extraction Hit Rate (DEHR) — LLM-extracted value is non-null and conforms to schema type | Value Extraction Quality |
| **S3.2** | Semantic-Field-Match F1 (SFM-F1) — extracted field name matches the gold field name | Value Extraction Quality |
| **S4.1** | Cache Hit Rate (CHR) | Cache Effectiveness |
| **S4.2** | Cache Value Diversity (CVD) — Shannon entropy on `diverseValueCache` per parameter | Cache Effectiveness |
| **S4.3** | TTL Staleness Risk (TSR) — fraction of hits served beyond P50 of validity-window | Cache Effectiveness |
| **S5.1** | Registry EMA Convergence (REC) — variance of `successRate` across last K invocations | Learning Convergence |
| **S5.2** | Mapping Stability (MS) — fraction of mappings present in run R also present in run R+1 | Learning Convergence |
| **S6** | End-to-End Smart-Fetch Yield (E2E-Yield) — pair (smart-served fraction, SUT-accepted fraction) | End-to-End Yield |
| **S7** | Trace-Priority Beat (TPB) — when a P0 trace endpoint exists, does it outperform the registry? | Provenance / Trace-Awareness |

Each metric is operationally defined below with formula, citations, and a "How to compute in RESTest" pointing at the source method.

---

## Family 1 — Service Discovery Quality (S1)

### S1.1 Service Discovery Precision (SDP)

**Definition.** When Smart-Fetch's discovery step (LLM or pattern) emits a list of candidate services for parameter `P`, the fraction of those candidates that overlap with the gold producer set `Gold(P)`.

**Formula.**
```
SDP(P)  = | Discovered(P) ∩ Gold_services(P) | / | Discovered(P) |
SDP     = mean over P ∈ params of SDP(P)        (macro-average)
```
where `Discovered(P)` is the set of services returned by `discoverByLLM` (`SmartInputFetcher.java:378-418`) and `discoverByPatterns` (`:339-356`, currently disabled), and `Gold_services(P)` is the set of distinct `service` strings in `Gold(P)`.

**Why it matters.** Producer-consumer fuzzers since RESTler [1] depend on accurately identifying the producer of each consumed value; ARAT-RL [11] explicitly assigns a Q-value bonus to inputs sourced from the right *response-history* lineage, and Morest [6] makes the producer-consumer DAG the *primary data structure*. AutoRestTest [30] uses a Semantic Property Dependency Graph (SPDG) with similarity scores — the multi-agent reinforcement learning component's whole purpose is to *raise this very precision*. Service discovery precision is therefore the foundational metric — if SDP is low, every downstream metric suffers a constant-factor degradation regardless of RL or LLM cleverness.

**Citations.** [1, 6, 11, 14, 16, 30] (recent: [11, 14, 16, 30]).

**Why it is smart-fetch-specific.** SDP is computed at the moment of *discovery*, before any HTTP fetch. It does not depend on whether the fetch succeeded (S2), whether extraction succeeded (S3), or whether the resulting value was schema-valid (D1).

**How to compute in RESTest.**
- Hook `discoverByLLM` (`SmartInputFetcher.java:378-418`) to log every entry of the returned `mappings` list as `(parameter, suggested_service, suggested_endpoint, llm_rank)`.
- For the gold set, run a one-time offline script that walks `merged_openapi_spec*.yaml` for `x-service-name` (parser: `OpenAPIEndpointDiscovery.parseOpenAPISpec()` line 55) plus a Jaeger export from a baseline run.
- Compute the intersection per parameter; average across parameters for the run-level number.

**Reasonable thresholds (KPI).** SDP ≥ 0.50 (top-3 LLM picks). RESTGPT [14] reports a parameter-value validity rate of 73% on the same kind of LLM-against-spec task; for the *coarser* "is this service even right" question, 50% precision-at-3 is a fair target.

---

### S1.2 Service Discovery Recall (SDR)

**Definition.** Of the gold producers, the fraction Smart-Fetch's discovery actually surfaces.

**Formula.**
```
SDR(P) = | Discovered(P) ∩ Gold_services(P) | / | Gold_services(P) |
SDR    = mean over P ∈ params of SDR(P)
```

**Why this matters separately from SDP.** A discovery step that returns a single very-likely-correct service (SDP = 1.0) but misses three other valid producers also has SDR = 0.25. In multi-step microservice workflows like Train Ticket [9, 33], a parameter (e.g., `accountId`) typically has *multiple* producers (auth-service, contacts-service, user-service); discovering only one means cache values come from a single distribution, hurting downstream Shannon entropy (D8) and the Smart-Fetch system's resilience against any single producer being unavailable.

**Citations.** Standard IR retrieval evaluation [38]; RESTler's producer-consumer model [1] is a special case where each consumer ID has exactly one producer; ARTE [2, 20] reports realism on a *multiset* of candidates — the same recall-vs-precision tension applies.

**How to compute.** Same instrumentation as S1.1. Threshold KPI: SDR ≥ 0.40 at top-3.

**Why it is smart-fetch-specific.** Recall is over a *static* parameter→producer mapping defined before any test runs. It does not measure runtime behavior of the SUT.

---

## Family 2 — Endpoint Selection Quality (S2)

### S2. Endpoint Selection Hit Rate (ESHR)

**Definition.** Conditional on Smart-Fetch having discovered a *correct service*, the fraction of attempts where the chosen endpoint within that service is also a member of `Gold(P)` *and* returns HTTP 2xx.

**Formula.**
```
ESHR(P) = | { attempt a in Attempts(P) :
                attempt_service(a) ∈ Gold_services(P)
                AND attempt_endpoint(a) ∈ Gold(P)
                AND http_status(a) == 200 } |
          /
          | { attempt a in Attempts(P) : attempt_service(a) ∈ Gold_services(P) } |
```

**Why this matters.** RESTest's smart-fetch picks an endpoint via `OpenAPIEndpointDiscovery.findBestEndpoint` (`OpenAPIEndpointDiscovery.java:140-151`) — a hand-coded, weighted-keyword scorer. ARAT-RL [11] showed that *learned* endpoint selection beats hand-coded scoring; AutoRestTest [30] uses an LLM-driven parameter agent to make this selection. Measuring ESHR tells us whether RESTest's hand-coded scorer or LLM-rank-based fallback (`SmartInputFetcher.java:401-404`) is competitive.

**Citations.** [6, 11, 14, 30] (recent: [11, 14, 30]); Schemathesis [22] uses OpenAPI `links` for endpoint chaining — analogous declarative endpoint targeting.

**How to compute in RESTest.**
- The CSV row for each attempt already carries `attempt_endpoint` and `http_status` (from `fetchFromApiMapping` line 446–449).
- Filter to attempts where `attempt_service ∈ Gold_services(P)`.
- The numerator additionally requires `attempt_endpoint ∈ Gold(P)` AND `http_status == 200`.

**Reasonable threshold.** ESHR ≥ 0.70. The parent framework reports the related D5 IDR threshold at 0.40; ESHR should be *higher* than IDR because it conditions on the easier prerequisite (a correct service) and ignores schema validity.

**Why it is smart-fetch-specific.** ESHR is conditional on *internal* correctness of the discovery and selection layers. A failed test downstream does not change ESHR; an upstream LLM that mis-ranked `ts-station-service` before `ts-train-service` would.

---

## Family 3 — Value Extraction Quality (S3)

### S3.1 Direct-Extraction Hit Rate (DEHR)

**Definition.** Given an HTTP 200 response from a discovered, scored endpoint, the fraction of attempts where `extractValueDirectlyFromResponse` (the LLM-driven extractor at `SmartInputFetcher.java:567-629`) returns a non-null, non-`NO_GOOD_MATCH` value that survives the `isValidValueForParameter` filter (called at line 268).

**Formula.**
```
DEHR = | { attempt a : http_status(a) == 200
                       AND extracted_value(a) != null
                       AND not NO_GOOD_MATCH
                       AND isValidValueForParameter(extracted_value(a), P) } |
       /
       | { attempt a : http_status(a) == 200 } |
```

**Why it matters.** This is a hallucination metric in disguise: the LLM is asked at line 233 of `directValueExtraction` prompt to "use ONLY values that appear in the response — do not generate new values." When it returns a value that is not in the response (a JSONPath expression like `$.data[*].route.endStation`, see line 594 — RESTest explicitly logs this as a "CRITICAL BUG"), or returns a value that fails schema validation, that is the LLM hallucinating against a stated grounding constraint. HalluLens [15] and FaithBench [42] formalize the same kind of measurement. The structured-outputs literature [40, 41] argues that schema-aware extraction (JSONPath, XPath, regex) outperforms free-form LLM extraction on simple tasks but the LLM does better on *semantically ambiguous* fields — DEHR quantifies which regime RESTest is in.

**Citations.** [14, 15, 16, 40, 42] (recent: all five — [14] ICSE-NIER 2024, [15] ACL 2025, [16] FSE 2025, [40] arXiv 2025, [42] FaithBench 2025).

**How to compute in RESTest.**
- `extractValueDirectlyFromResponse` already logs `LLM extracted ACTUAL VALUE` on success (`SmartInputFetcher.java:609`) and several distinct failure logs (`NO_GOOD_MATCH`, `JSONPath` mistake, validation failure). Mine these for the run.
- The denominator is the count of `200` responses logged at `:447`.

**Why it is smart-fetch-specific.** DEHR conditions on a successful HTTP fetch. Schema validity (which D1 SCR also checks) is included only as the *gate* that says "this attempt produced a usable value at all." A smart-fetch attempt that fetched HTTP 200 but where the LLM hallucinated a JSONPath instead of a value contributes to DEHR's denominator and not its numerator — that is the *Smart-Fetch* failure, not the *value*'s failure.

**Reasonable threshold.** DEHR ≥ 0.65 with current 8B–14B local LLMs. RESTGPT reports 73% valid-input rate [14]; LlamaRestTest reports 72.44% [16]; for a value-extraction (rather than value-generation) task, the same range is realistic because the LLM has only to copy from the response.

---

### S3.2 Semantic-Field-Match F1 (SFM-F1)

**Definition.** For each successful direct-extraction, the *field* the LLM picked (e.g., `endStation`) is compared to the gold field (the field name in `Gold(P)`'s response schema that the parameter is supposed to be sourced from). F1 score over the field-name micro-confusion-matrix.

**Formula.** Standard IR F1 [38]:
```
Precision  = | LLM_picked ∩ Gold_field | / | LLM_picked |
Recall     = | LLM_picked ∩ Gold_field | / | Gold_field |
SFM-F1     = 2 * Precision * Recall / (Precision + Recall)
```

**Why it matters.** RESTest's `findSemanticMatch` (`SmartInputFetcher.java:782-880`) explicitly calls an LLM and asks it for a field name (line 818 onwards: "Find the most semantically relevant field"). The example pairs in the prompt (line 851: `startStation → from`, `userId → accountId`, `distance → price NOT trainId`) are *exactly* the kind of confusion pairs SFM-F1 measures. ARTE [2, 20] does the analogous mapping in the realism-corpus direction (parameter name → DBpedia entity). RBCTest [32] mines API response constraints from natural language; the constraint side is dual to the field-name side.

**Citations.** [2, 14, 20, 32, 39] (recent: [14, 32, 39]).

**How to compute in RESTest.**
- Hook `findSemanticMatch` (line 782) and `extractValueDirectlyFromResponse` (line 567) to log the *field* the LLM picked.
- Build the gold field set per parameter from a hand-curated micro-corpus: for the 30 most-frequent params in TrainTicket, list the OpenAPI response fields that should source them. (~30 minutes of manual labelling — paid by D5 IDR's gold-truth dataset which already has the same sourcing.)

**Reasonable threshold.** F1 ≥ 0.60. Lower than DEHR because field-name mapping is a finer-grained task; the LLM can extract a *correct value* from a *wrong field* (e.g., picking `name` instead of `id` and getting a string in both cases) — DEHR rewards that, F1 punishes it.

**Why it is smart-fetch-specific.** Field-name matching is the LLM's internal reasoning trace. It is observed only inside Smart-Fetch and contributes nothing to D1–D10 directly.

---

## Family 4 — Cache Effectiveness (S4)

### S4.1 Cache Hit Rate (CHR)

**Definition.** Fraction of smart-fetch attempts served from the in-memory `cache` or `diverseValueCache` rather than triggering a fresh HTTP fetch.

**Formula.**
```
CHR_main    = | { a : cache_hit_path(a) == "cache" } | / | Attempts |
CHR_diverse = | { a : cache_hit_path(a) == "diverseValueCache" } | / | Attempts |
CHR         = CHR_main + CHR_diverse
```
where `cache_hit_path(a)` is recorded at the early-return paths in `fetchFromSmartSource` (`SmartInputFetcher.java:213-220`) and `fetchSmartInput` (`:174-178`).

**Why it matters.** Caching is the *only* mechanism by which Smart-Fetch reduces LLM token spend and HTTP load on the SUT. Recent token-cost analyses [43] put output-token cost at 4–5x input-token cost; even one cached call per parameter saves a structured-extraction round-trip with O(2 KB) of input tokens. Distributed caching surveys [37] target hit ratios of 85–95% for read-heavy workloads. RESTest's TTL is configurable at `smart.input.fetch.cache.ttl.seconds` with a default of 300 s (`SmartInputFetchConfig.java:49`).

**Citations.** [37, 43] (recent: both).

**How to compute.** Every code path that returns from the cache logs `Using cached value` at line 217 of `fetchFromSmartSource`, or `Using diverse cached value` at line 176 of `fetchSmartInput`. Count those vs. total attempts.

**Reasonable threshold.** CHR ≥ 0.50 in steady state. Below 0.30 means the cache is effectively cold or TTL is mis-tuned.

**Why it is smart-fetch-specific.** Cache behavior is internal to Smart-Fetch and invisible to the parent framework's D1–D10 (those metrics see only the *value*, not whether it came from a cache or a fresh fetch).

---

### S4.2 Cache Value Diversity (CVD)

**Definition.** Per-parameter Shannon entropy [25] over the multiset of distinct values stored in `diverseValueCache` (`SmartInputFetcher.java:63-64`, `:1397-1423`). The diverse cache exists explicitly to break the "same first hit forever" anti-pattern, and CVD measures whether it succeeds.

**Formula.**
```
H(P) = - sum over distinct v in diverseValueCache[P] of  f(v) * log_2 f(v)
       where f(v) = count(v) / |diverseValueCache[P]|
```
plus the rotation-cursor evenness:
```
RotationEvenness(P) = 1 - max_k(rotated_count(k)) / |diverseValueCache[P]|
```
where `rotated_count(k)` is the number of times index `k` was returned by `getNextDiverseValue` (`SmartInputFetcher.java:1480-1509`). Perfect rotation gives `RotationEvenness = (n-1)/n`.

**Why it matters.** Information-theoretic fuzzing analyses by Böhme [36, 44] show that diversity (more distinct inputs) drives bug discovery once coverage saturates. The parent framework's D8 measures Shannon on the *test pool*; CVD measures it on the *cache that feeds the test pool*. If the cache is concentrated (low H), D8 will inherit the concentration. The audit found a real rotation-cursor leak (audit finding #18) where `valueRotationIndex` was not reset between scenarios, biasing rotation; CVD's `RotationEvenness` would catch exactly that.

**Citations.** [25, 36, 44] (recent: [44]).

**How to compute.** Periodically dump `diverseValueCache.entrySet()` and `valueRotationIndex.entrySet()` (e.g., once per scenario completion). Compute H per parameter; aggregate to a run-level mean, weighted by each parameter's draw count.

**Reasonable threshold.** For an n-element diverse-cache, the entropy ceiling is `log_2 n`. Cached pools should reach `H ≥ 0.8 * log_2 n`. If the rotation cursor leak from audit #18 reappears, `RotationEvenness < 0.5` will flag it.

**Why it is smart-fetch-specific.** This is the *cache*, not the test pool. D8 is about test pool diversity; CVD is about whether the cache that *populates* the pool has diverse content to begin with.

---

### S4.3 TTL Staleness Risk (TSR)

**Definition.** When a value is served from cache (`CachedValue` at `SmartInputFetcher.java:68-80`), how close to the TTL deadline is it? TSR measures the fraction of cache hits served in the latter half of the TTL window — i.e., values whose remaining lifetime is short enough that the underlying SUT may have changed since the cache write.

**Formula.**
```
TSR = | { a : cache_hit(a) == TRUE
              AND age_at_hit(a) > 0.5 * config.cacheTtlSeconds } |
      /
      | { a : cache_hit(a) == TRUE } |
```

**Why it matters.** Cache freshness in distributed systems is a fundamental tradeoff [37]: long TTLs raise CHR but raise the *probability* of returning a value that no longer exists in the SUT (the user was deleted, the order was cancelled). The parent framework's D5 IDR captures the *consequence* of staleness — values that no longer appear in trace outputs — but TSR captures the *risk* before it manifests, computable purely from cache metadata. The OneUptime [37] guidance is that high-mutability domain objects (orders, sessions) want TTL ≤ 60 s; low-mutability ones (stations, route plans) tolerate 600 s+. RESTest currently uses one global TTL of 300 s.

**Citations.** [37].

**How to compute.** Every cache hit, log `(cache_hit_age_ms, ttl_ms)`. The relevant code is `CachedValue.isExpired` (`SmartInputFetcher.java:77-79`).

**Reasonable threshold.** TSR ≤ 0.30. Above this means TTL is too long for the workload, or the workload has higher SUT mutation rate than 300 s assumes.

**Why it is smart-fetch-specific.** TSR is a metadata property of the cache's age distribution; it has no cross-parent dependency.

---

## Family 5 — Learning Convergence / Stability (S5)

### S5.1 Registry EMA Convergence (REC)

**Definition.** RESTest's `ApiMapping` tracks a per-mapping success rate using an exponential moving average [45] with `alpha = 0.1` (`ApiMapping.java:62`). REC measures whether the EMA *converges* — i.e., approaches a stable value across invocations — or oscillates / drifts.

**Formula.** For each mapping `m` with K invocations in a run:
```
REC(m) = 1 - var(successRate_history(m)[last K/2]) / var(successRate_history(m)[first K/2])
```
where `var()` is sample variance. REC ≈ 1 means the EMA's variance shrinks over time (good convergence); REC ≈ 0 means it stays the same (no convergence); REC < 0 means the EMA is drifting (the SUT is changing or the mapping is unstable).

**Why it matters.** Online learning theory [46] predicts that an EMA with `alpha = 0.1` reaches 95% of the steady-state value within roughly 30 samples and remains within ±2σ of it thereafter. If REC is consistently negative for many mappings, either (a) the mappings are flaky (the SUT response distribution is non-stationary), (b) `alpha` is mis-tuned for the workload, or (c) the registry persists wrong mappings that should be evicted.

**Citations.** [45, 46] (the EMA-in-deep-learning paper [45] from arXiv 2024 is recent).

**How to compute.** Add a hook to `ApiMapping.updateSuccessRate` (`ApiMapping.java:59-64`) to append a row to `mapping-ema-history.csv` per invocation: `(timestamp, mapping_id, was_success, post_update_rate)`. Compute REC offline by binning by mapping_id and applying the formula.

**Reasonable threshold.** REC ≥ 0.5 averaged across mappings invoked ≥ 30 times. Long-tail mappings (≤ 5 invocations) should be excluded.

**Why it is smart-fetch-specific.** REC is purely a property of the `ApiMapping` data structure inside Smart-Fetch. The parent framework's metrics never look at the EMA.

---

### S5.2 Mapping Stability (MS)

**Definition.** Across two consecutive runs `R` and `R+1` (same SUT, same TrainTicket benchmark, same OpenAPI spec), the fraction of mappings present in `R`'s persisted `input-fetch-registry.yaml` that are still present in `R+1`'s registry.

**Formula.**
```
MS = | M(R) ∩ M(R+1) | / | M(R) |
```
where `M(R)` is the set of `(parameter, service, endpoint)` triples in run R's registry. `R+1`'s registry inherits from `R`'s on disk (`SmartInputFetcher.loadRegistry()` at `:1288-1302`, `saveRegistry()` at `:1330-1339`).

**Why it matters.** A central design promise of persisted-registry approaches (RESTler's stateful learning [1], ARAT-RL's Q-table persistence [11], Morest's RPG updates [6]) is that costly LLM-discovery work is *amortised* across runs. If every run rediscovers the same mappings, the LLM token cost grows linearly in the number of runs and the EMA's success-rate signal never accumulates enough samples to converge (S5.1 fails). MS = 1.0 means full reuse; MS < 0.7 means the registry is being *churned* — discovery is rerunning each time.

The audit found that `discoverApiMappings` *unconditionally* runs LLM discovery if the parameter's mapping list is empty (`SmartInputFetcher.java:250-253`); a real-world bug where the registry's serialization roundtrips lossily — losing some mappings on YAML reload — would cause MS to silently degrade. MS is the *test* for that bug class.

**Citations.** [1, 6, 11, 14] (recent: [11, 14]).

**How to compute.** Compare two consecutive `input-fetch-registry.yaml` files. Trivial set-cardinality computation; one `diff` plus parsing.

**Reasonable threshold.** MS ≥ 0.85 across consecutive identical-config runs.

**Why it is smart-fetch-specific.** The persisted YAML registry is a smart-fetch internal artefact; D1–D10 do not consume it.

---

## Family 6 — End-to-End Smart-Fetch Yield (S6)

### S6. End-to-End Smart-Fetch Yield (E2E-Yield)

**Definition.** A *pair* of fractions: `(yield_smart, yield_accepted)`.

- `yield_smart` = fraction of the parameter-resolution decisions where Smart-Fetch returned a value (any value), as opposed to falling back to LLM (`fallbackToLLM` at `SmartInputFetcher.java:1150-1221`).
- `yield_accepted` = of the values that Smart-Fetch returned, the fraction that the SUT accepted with HTTP 2xx in the actual test execution.

**Formula.**
```
yield_smart    = | { a : fallback_to_llm(a) == FALSE } | / | Attempts |
yield_accepted = | { a : fallback_to_llm(a) == FALSE
                         AND consumer_response_2xx(a) == TRUE } |
                 /
                 | { a : fallback_to_llm(a) == FALSE } |
```
where `consumer_response_2xx(a)` is the HTTP status of the *consumer* operation (the one for which the parameter was being resolved), not the producer fetch.

**Why it matters.** This is the only smart-fetch metric that touches the SUT *consumer* response, but it does so only as the binary "accepted vs. rejected" — it explicitly avoids the question of *whether the test was a meaningful test* (that is the tool-effectiveness framework's job). E2E-Yield directly answers the operational question: when Smart-Fetch is enabled, does my pipeline actually use it, and does the SUT actually take the values?

LlamaRestTest [16] reports a 72.44% valid-input-generation rate for a comparable LLM-driven generator; ARTE [2] reports 57.3% accepted-call rate. RESTGPT [14] reports 73% parameter-validity rate and a meaningfully higher coverage as a result. We expect Smart-Fetch's `yield_accepted` to *exceed* a pure LLM baseline because the values come from real upstream responses — that is the *whole point* of the design.

**Citations.** [2, 14, 16, 20] (recent: [14, 16]).

**How to compute.** `yield_smart` is computable from the smart-fetch CSV alone. `yield_accepted` joins on the existing per-test HTTP status logs that `MultiServiceTestCaseGenerator` emits. The join key is `(scenario_id, root_idx, step_idx, parameter)`.

**Reasonable thresholds.** `yield_smart ≥ 0.80`; `yield_accepted ≥ 0.60`. Below `0.80` smart-served fraction means the cascade of S1–S3 is failing too often; below `0.60` accepted fraction means smart-fetched values are themselves being rejected by the SUT (probably stale — connect to S4.3 TSR — or wrong-format — connect to D1 SCR in the parent).

**Why it is smart-fetch-specific (and not D5/D6).** `yield_smart` is an internal Smart-Fetch decision metric; `yield_accepted` is a 2xx-binary on the consumer call which is a much weaker signal than D5 (trace-resolvability) or D6 (chain-coherence). Two scenarios with identical `yield_accepted` can have very different D5/D6 — so reporting all three is non-redundant.

---

## Family 7 — Provenance and Trace-Awareness (S7)

### S7. Trace-Priority Beat (TPB)

**Definition.** RESTest implements a Priority-0 fast path for trace-observed producer endpoints (`SmartInputFetcher.java:222-243`): when the calling code has populated `ParameterInfo.traceProducerEndpoints`, those endpoints are tried *before* the registry. TPB measures whether this fast path *actually beats* the registry-driven path:

- `TPB_yield` = of attempts where `traceProducerEndpoints` was non-empty, the fraction where the trace fast path returned a usable value (line 232).
- `TPB_uplift` = `(yield_accepted | trace_used) - (yield_accepted | registry_used)`, paired by parameter.

**Formula.**
```
TPB_yield    = | { a : traceEndpointsAvailable(a)
                       AND trace_path_returned_value(a) == TRUE } |
               /
               | { a : traceEndpointsAvailable(a) } |

TPB_uplift   = mean over P of [
                 yield_accepted(P | path == TRACE)
                 - yield_accepted(P | path == REGISTRY)
               ]
```

**Why it matters.** OpenTelemetry's trace-based-testing community [10, 47] argues — and the ICSE-2025 microservice-testing track confirms [21, 30] — that distributed traces are the gold-standard ground truth for cross-service data flow. Priority-0 trace-aware fetch is a direct implementation of that thesis. TPB tells us whether the implementation is paying off in practice or whether the registry actually beats the trace path (which would suggest the trace export is missing fields). The audit's finding #11 (`MultiServiceTestCaseGenerator.collectProducerEndpoints` walks a hand-rolled span tree) means the trace fast path is only as good as that span-collection code.

**Citations.** [9, 10, 17, 23, 29, 33, 47] (recent: [29, 47]).

**How to compute.** The smart-fetch CSV row already carries `priority_chain_used` ∈ {`TRACE`, `REGISTRY`, …}. Bucket attempts by that field and compare yields per parameter.

**Reasonable threshold.** `TPB_uplift ≥ +0.10` (i.e., the trace path is 10 percentage points better than the registry). If `TPB_uplift ≤ 0`, either traces are stale-by-design (the trace was captured in an earlier baseline run and the SUT now disagrees) or the trace ingest pipeline is dropping the high-precision endpoints.

**Why it is smart-fetch-specific.** The Priority-0 chain is internal to `SmartInputFetcher.fetchFromSmartSource`. Whether the parent framework's D5 IDR succeeds is not the same question as whether the Priority-0 path beat the registry — D5 fires for any value that resolves in traces regardless of which Smart-Fetch path produced it.

---

## 3. Operational protocol (six steps)

A single run-cycle protocol that produces one row per smart-fetch attempt and one summary table per metric.

### Step 1 — Instrument log lines

Add one helper, `SmartFetchEventLogger`, that emits a CSV row per attempt to `logs/<run_id>/smart-fetch-trace.csv`. Required columns:

```
attempt_id, ts_ms, run_id, scenario_id, root_idx, step_idx,
parameter, parameter_type,
priority_chain_used, services_attempted, endpoints_attempted,
http_statuses,            # comma-separated, in attempt order
extraction_method,        # LLM_DIRECT | LLM_FALLBACK_FIELD | LLM_GENERATED | NULL
llm_picked_field,         # for SFM-F1
value_returned,
fallback_to_llm,
cache_hit_path,           # main | diverse | none
cache_hit_age_ms,
ttl_ms,                   # snapshot of config at this attempt
time_to_value_ms,
mapping_priority, mapping_success_rate_pre, mapping_success_rate_post
```

The hook points are:
- **Service discovery (S1):** wrap `discoverByLLM` (`:378`) and `discoverByPatterns` (`:339`). Log every entry of the returned `mappings` list.
- **Endpoint selection (S2):** wrap `OpenAPIEndpointDiscovery.findBestEndpoint` (`OpenAPIEndpointDiscovery.java:140`). Log `(service, chosen_endpoint, score)`.
- **HTTP fetch (S2):** wrap `fetchFromApiMapping` (`SmartInputFetcher.java:423`). Log `(url, http_status, response_size_bytes)`.
- **Direct extraction (S3.1, S3.2):** wrap `extractValueDirectlyFromResponse` (`:567`) and `findSemanticMatch` (`:782`). Log `(extraction_method, llm_picked_field, value_returned)`.
- **Cache (S4):** wrap the cache hit early-returns at `:213-220` and `:174-178`. Log `(cache_hit_path, cache_hit_age_ms)`.
- **EMA (S5.1):** wrap `ApiMapping.updateSuccessRate` (`ApiMapping.java:59`). Log `(mapping_id, was_success, pre_rate, post_rate)`.
- **Trace path (S7):** wrap the Priority-0 block (`:222-243`). Log `(trace_endpoints_available, trace_path_returned_value)`.

Estimated diff size: ~150 lines of code, mostly call-site wrapping. No SUT change.

### Step 2 — Build the gold-truth artefact (one-time, refreshed quarterly)

For TrainTicket (or any new SUT):
1. Run `OpenAPIEndpointDiscovery` over the merged spec to enumerate `(operation, x-service-name, response_schema_fields)`.
2. For each parameter consumed by an operation, record the schema-field whose name (case-insensitive, with the audit's normalize-for-comparison rules at `InputFetchRegistry.java:548-559`) matches the parameter — that pair forms `Gold_Producers(P)`.
3. Run a baseline test cycle with full Jaeger export. Walk each consumer span backwards; for each ID-typed input, find the closest ancestor span containing that value. Record `(parameter, ancestor_service, ancestor_operation)` as `Gold_TraceProducers(P)`.
4. Persist as `gold-producers.csv`. Refresh when the SUT or spec changes.

### Step 3 — Per-run metric calculator

A small Python notebook (`debug/inputs/scripts/compute_smart_fetch_metrics.py`) consumes:
- `logs/<run_id>/smart-fetch-trace.csv` (Step 1)
- `gold-producers.csv` (Step 2)
- `mapping-ema-history.csv` (Step 1)
- `input-fetch-registry.yaml` from runs `R-1` and `R` (for S5.2 MS)

It produces:
- `metrics-per-parameter.csv` — SDP, SDR, ESHR, DEHR, SFM-F1, CHR, CVD, TSR, TPB per parameter.
- `metrics-per-mapping.csv` — REC per mapping.
- `metrics-summary.json` — run-level scalars: `yield_smart`, `yield_accepted`, MS, all macro-averages.

### Step 4 — Tabulate

Generate a single Markdown report `smart-fetch-quality-report-<run_id>.md` with:
- One section per family (1–7).
- A table of per-parameter metrics, sorted by `1 - SDP`.
- A `pre/post` comparison if a previous run is available.

### Step 5 — Compare against KPI thresholds (§4)

The Python script flags any metric below the amber threshold in red and any below green in amber. The summary block is the gating signal for CI.

### Step 6 — Report and ticket

- Auto-attach the report to the run's CI job.
- File a JIRA ticket per RED metric with the smart-fetch source-line links from §2 prefilled.

---

## 4. KPI table

| Metric | Family | Green | Amber | Red |
|---|---|---|---|---|
| **SDP** | Service Discovery | ≥ 0.50 | 0.30 – 0.50 | < 0.30 |
| **SDR** | Service Discovery | ≥ 0.40 | 0.25 – 0.40 | < 0.25 |
| **ESHR** | Endpoint Selection | ≥ 0.70 | 0.50 – 0.70 | < 0.50 |
| **DEHR** | Value Extraction | ≥ 0.65 | 0.45 – 0.65 | < 0.45 |
| **SFM-F1** | Value Extraction | ≥ 0.60 | 0.40 – 0.60 | < 0.40 |
| **CHR** | Cache | ≥ 0.50 | 0.30 – 0.50 | < 0.30 |
| **CVD** (H / log_2 n ratio) | Cache | ≥ 0.80 | 0.60 – 0.80 | < 0.60 |
| **TSR** | Cache | ≤ 0.30 | 0.30 – 0.50 | > 0.50 |
| **REC** | Convergence | ≥ 0.50 | 0.20 – 0.50 | < 0.20 |
| **MS** | Convergence | ≥ 0.85 | 0.65 – 0.85 | < 0.65 |
| **yield_smart** | E2E Yield | ≥ 0.80 | 0.60 – 0.80 | < 0.60 |
| **yield_accepted** | E2E Yield | ≥ 0.60 | 0.40 – 0.60 | < 0.40 |
| **TPB_uplift** | Trace-Awareness | ≥ +0.10 | 0 – +0.10 | < 0 |

These thresholds are the result of cross-checking three published baselines: RESTGPT [14] for parameter validity (73 %), LlamaRestTest [16] for valid-input generation (72.44 %), and ARTE [2] for accepted-call rate (57.3 %). The Smart-Fetch thresholds are set roughly midway between best-published and "minimum useful." All thresholds are pilot-runnable: the audit's existing measurement folders (`debug/inputs/measurements/TrainTicketTwoStageTest_*`) contain enough trace data to validate them on day one.

---

## 5. Implementability gaps (where the source code falls short)

A faithful framework lists the metrics it cannot compute *yet*. Three gaps:

| Metric | Gap | Required source change |
|---|---|---|
| **S1.2 SDR** | The denominator (`Gold_services(P)`) requires an offline gold artefact built from the merged OpenAPI spec + a Jaeger baseline. Nothing in `SmartInputFetcher.java` produces that artefact. | Add a `tools/build-gold-producers.py` ETL. Already in scope of `debug/inputs/scripts/` per §3 Step 2. |
| **S3.2 SFM-F1** | The LLM's *picked field* is logged informally in `findSemanticMatch` line 870 but not in `extractValueDirectlyFromResponse` (which returns the *value*, not the *field name*, line 612). | Add a `(field, value)` pair to the `extractValueDirectlyFromResponse` log line. ~3 LOC. |
| **S5.1 REC** | `ApiMapping.successRate` is updated in place; no history is kept (`ApiMapping.java:59-64`). | Add a circular buffer of the last 100 updates per mapping. ~10 LOC. |

The other metrics (S1.1 SDP, S2 ESHR, S3.1 DEHR, S4.1–S4.3, S5.2 MS, S6, S7) are computable from existing log content plus the new `SmartFetchEventLogger` row. The audit confirmed that `loadRegistry`/`saveRegistry` (lines 1288–1339), the cache early-return paths, the priority-chain branches in `fetchFromSmartSource` (lines 213–290), and the EMA call site (`ApiMapping.java:59`) all already log enough information to derive the metric values, modulo CSV emission.

---

## 6. Why this scope is correct (and not duplicative of D1–D10)

Two reasons.

1. **D1–D10 measure end-state input quality**, treating Smart-Fetch as a black box that emits a value with some `provenance` tag. They cannot answer questions like "did the LLM service-suggester return a service that *exists* in the spec," "was the chosen endpoint the right one *for that service*," or "did the cache rotation cursor skip values." Those are properties of the discovery/selection/extraction *chain*, not of the value itself. The seven smart-fetch metrics inhabit exactly that chain.

2. **The chain has its own failure modes that do not bubble up cleanly to D1–D10.** The audit's pipeline-bug-audit found that many Smart-Fetch bugs (e.g., the JSONPath-as-value confusion at line 594, the rotation-cursor leak in audit finding #18, the YAML registry serialization issues) manifest as *correlated, low-rate* degradations of D1 SCR or D5 IDR — i.e., they look like normal noise in the parent metrics. The smart-fetch metrics are designed to *isolate* those failure modes early, before they corrupt downstream metrics.

A useful rubric: **D1–D10 say "the inputs are bad"; S1–S7 say where they got bad inside Smart-Fetch.**

---

## 7. References

[1] V. Atlidakis, P. Godefroid, and M. Polishchuk, "RESTler: Stateful REST API Fuzzing," in *Proc. 41st Int. Conf. Software Engineering (ICSE)*, 2019, pp. 748–758. https://doi.org/10.1109/ICSE.2019.00083

[2] J. C. Alonso, A. Martin-Lopez, S. Segura, J. M. García, and A. Ruiz-Cortés, "ARTE: Automated Generation of Realistic Test Inputs for Web APIs," *IEEE Trans. Software Engineering*, vol. 49, no. 1, pp. 348–363, Jan. 2023. https://doi.org/10.1109/TSE.2022.3150618

[3] A. Arcuri, "Automated Black- and White-Box Testing of RESTful APIs With EvoMaster," *IEEE Software*, vol. 38, no. 3, pp. 72–78, 2021. https://doi.org/10.1109/MS.2020.3013820

[4] D. Corradini, A. Zampieri, M. Pasqua, and M. Ceccato, "Restats: A Test Coverage Tool for RESTful APIs," in *Proc. ICSME 2021*, 2021. arXiv:2108.08209. https://arxiv.org/abs/2108.08209

[5] **[recent]** A. Arcuri, M. Zhang, and A. Golmohammadi, "Tool report: EvoMaster — black and white box search-based fuzzing for REST, GraphQL and RPC APIs," *Automated Software Engineering*, vol. 31, no. 1, 2024. https://doi.org/10.1007/s10515-024-00478-1

[6] Y. Liu, Y. Li, G. Deng, Y. Liu, R. Wan, R. Wu, D. Ji, S. Xu, and M. Bao, "Morest: Model-based RESTful API Testing with Execution Feedback," in *Proc. 44th Int. Conf. Software Engineering (ICSE)*, 2022, pp. 1406–1417. https://doi.org/10.1145/3510003.3510133

[7] M. Kim, Q. Xin, S. Sinha, and A. Orso, "Automated Test Generation for REST APIs: No Time to Rest Yet," in *Proc. ISSTA 2022*, pp. 289–301. https://doi.org/10.1145/3533767.3534401

[8] **[recent]** A. Golmohammadi, M. Zhang, and A. Arcuri, "Testing RESTful APIs: A Survey," *ACM TOSEM*, vol. 33, no. 1, art. 27, Jan. 2024. https://doi.org/10.1145/3617175

[9] X. Zhou, X. Peng, T. Xie, J. Sun, C. Ji, D. Liu, Q. Xiang, and C. He, "Latent Error Prediction and Fault Localization for Microservice Applications by Learning from System Trace Logs," in *Proc. ESEC/FSE 2019*, pp. 683–694. https://doi.org/10.1145/3338906.3338961

[10] OpenTelemetry Authors, "Trace-based Testing the OpenTelemetry Demo," 2023. https://opentelemetry.io/blog/2023/testing-otel-demo/

[11] **[recent]** M. Kim, S. Sinha, and A. Orso, "Adaptive REST API Testing with Reinforcement Learning," in *Proc. ASE 2023*, 2023. arXiv:2309.04583. https://arxiv.org/abs/2309.04583

[12] **[recent]** D. Corradini, Z. Montolli, M. Pasqua, and M. Ceccato, "DeepREST: Automated Test Case Generation for REST APIs Exploiting Deep Reinforcement Learning," in *Proc. 39th IEEE/ACM Int. Conf. Automated Software Engineering (ASE)*, 2024, pp. 1383–1394. https://doi.org/10.1145/3691620.3695511

[13] D. R. Kuhn, R. N. Kacker, and Y. Lei, *Practical Combinatorial Testing*, NIST SP 800-142, U.S. National Institute of Standards and Technology, 2010. https://nvlpubs.nist.gov/nistpubs/legacy/sp/nistspecialpublication800-142.pdf

[14] **[recent]** M. Kim, T. Stennett, D. Shah, S. Sinha, and A. Orso, "Leveraging Large Language Models to Improve REST API Testing," in *Proc. ICSE-NIER 2024*, pp. 37–41. (RESTGPT — arXiv:2312.00894.) https://doi.org/10.1145/3639476.3639769

[15] **[recent]** Y. Bang et al., "HalluLens: LLM Hallucination Benchmark," in *Proc. ACL 2025 (Long Papers)*, 2025. https://aclanthology.org/2025.acl-long.1176/

[16] **[recent]** M. Kim, S. Sinha, and A. Orso, "LlamaRestTest: Effective REST API Testing with Small Language Models," *Proc. ACM Software Engineering*, vol. 2, no. FSE, 2025. arXiv:2501.08598. https://doi.org/10.1145/3715737

[17] M. Waseem, P. Liang, M. Shahin, A. Di Salle, and G. Márquez, "Design, monitoring, and testing of microservices systems: The practitioners' perspective," *Journal of Systems and Software*, vol. 182, art. 111061, 2021. https://doi.org/10.1016/j.jss.2021.111061

[18] Vectara, "Hallucination Leaderboard," 2024–2025. https://github.com/vectara/hallucination-leaderboard

[19] V. J. M. Manès, H. Han, C. Han, S. K. Cha, M. Egele, E. J. Schwartz, and M. Woo, "The Art, Science, and Engineering of Fuzzing: A Survey," *IEEE Trans. Software Engineering*, vol. 47, no. 11, pp. 2312–2331, 2021. https://doi.org/10.1109/TSE.2019.2946563

[20] J. C. Alonso, "ARTE: Automated Generation of Realistic Test Inputs for Web APIs," *ESEC/FSE 2022 Journal-First*. (Companion presentation of [2] at FSE 2022; both retained for cross-reference.) https://2022.esec-fse.org/details/fse-2022-journal-first/15/

[21] **[recent]** AGORA+: J. C. Alonso, S. Segura, and A. Ruiz-Cortés, "Test Oracle Generation for REST APIs," *ACM TOSEM*, 2025. https://doi.org/10.1145/3726524

[22] D. Hatch and Schemathesis Authors, *Schemathesis: Property-based Testing for OpenAPI and GraphQL*, 2019–2025. https://schemathesis.io/

[23] Jaeger Authors, "Jaeger: Open-Source Distributed Tracing Platform." https://www.jaegertracing.io/

[24] A. Martin-Lopez, S. Segura, and A. Ruiz-Cortés, "Specification and Automated Analysis of Inter-Parameter Dependencies in Web APIs," *IEEE Trans. Services Computing*, vol. 15, no. 4, pp. 2342–2355, 2022. https://doi.org/10.1109/TSC.2021.3050610

[25] C. E. Shannon, "A Mathematical Theory of Communication," *Bell System Technical Journal*, vol. 27, pp. 379–423, 623–656, 1948.

[26] OWASP Foundation, *OWASP API Security Top 10*, 2023. https://owasp.org/API-Security/

[27] T. Le, A. Le, T. Mai, X.-B. D. Le, and N. Tran, "KAT: Dependency-aware Automated API Testing with Large Language Models," in *Proc. ICST 2024*, IEEE, 2024.

[28] A. Martin-Lopez, S. Segura, and A. Ruiz-Cortés, "RESTest: Automated Black-Box Testing of RESTful Web APIs," in *Proc. ISSTA 2021 (Tool Demos)*, pp. 682–685. https://doi.org/10.1145/3460319.3469082

[29] **[recent]** A. S. Abdelfattah, T. Cerny, J. Yero, E. Song, and D. Taibi, "Test Coverage in Microservice Systems: An Automated Approach to E2E and API Test Coverage Metrics," *Electronics*, vol. 13, no. 10, art. 1913, May 2024. https://doi.org/10.3390/electronics13101913

[30] **[recent]** M. Kim, S. Sinha, and A. Orso, "A Multi-Agent Approach for REST API Testing with Semantic Graphs and LLM-Driven Inputs," in *Proc. ICSE 2025 (Research Track)*, 2025. arXiv:2411.07098. https://arxiv.org/abs/2411.07098

[31] **[recent]** M. Kim et al., "AutoRestTest: A Tool for Automated REST API Testing Using LLMs and MARL," in *Proc. ICSE 2025 (Demonstrations)*, 2025. arXiv:2501.08600. https://doi.org/10.1109/ICSE-Companion66252.2025.00015

[32] **[recent]** T.-H. Hoang, V. Le, T. Le, M. Nguyen-Truong, and N. Tran, "RBCTest: Combining Static and Dynamic Approaches for Mining and Testing Constraints for RESTful API Testing," 2025. arXiv:2504.17287. https://arxiv.org/abs/2504.17287

[33] X. Zhou, X. Peng, T. Xie, J. Sun, C. Xu, C. Ji, and W. Zhao, "Benchmarking Microservice Systems for Software Engineering Research," in *Proc. 40th Int. Conf. Software Engineering (ICSE Companion)*, 2018, pp. 323–324. https://github.com/FudanSELab/train-ticket

[34] V. Atlidakis, R. Geambasu, P. Godefroid, M. Polishchuk, and B. Ray, "Pythia: Grammar-Based Fuzzing of REST APIs with Coverage-guided Feedback and Learning-based Mutations," in *Proc. ISSTA 2020*, pp. 145–156. https://doi.org/10.1145/3395363.3397382

[35] M. Böhme, V.-T. Pham, M.-D. Nguyen, and A. Roychoudhury, "Directed Greybox Fuzzing," in *Proc. ACM CCS 2017*, pp. 2329–2344.

[36] M. Böhme, "STADS: Software Testing as Species Discovery," *ACM TOSEM*, vol. 27, no. 2, art. 7, 2018. https://doi.org/10.1145/3210309

[37] OneUptime Authors, "How to Implement Time-Based Cache Invalidation," 2026. https://oneuptime.com/blog/post/2026-01-30-time-based-invalidation/view

[38] C. D. Manning, P. Raghavan, and H. Schütze, *Introduction to Information Retrieval*. Cambridge University Press, 2008. (Chapter 8: Evaluation in information retrieval — Precision, Recall, F1.)

[39] **[recent]** Y. Bai et al., "PARSE: LLM Driven Schema Optimization for Reliable Entity Extraction," 2025. arXiv:2510.08623. https://arxiv.org/abs/2510.08623

[40] **[recent]** OpenAI, "Structured Outputs in the OpenAI API," 2024–2025. https://openai.com/index/introducing-structured-outputs-in-the-api/

[41] **[recent]** L. Lei et al., "DeepJSONEval: Benchmarking Complex Nested JSON Data Mining for Large Language Models," 2025. arXiv:2509.25922. https://arxiv.org/abs/2509.25922

[42] **[recent]** F. Bao et al., "FaithBench / HalluLens: Benchmarking LLM Faithfulness in RAG with Evolving Leaderboards," in *Proc. EMNLP 2025 Industry*, 2025. arXiv:2505.04847. https://aclanthology.org/2025.emnlp-industry.54.pdf

[43] **[recent]** Silicon Data, "Understanding LLM Cost Per Token: A 2026 Practical Guide," 2026. https://www.silicondata.com/blog/llm-cost-per-token

[44] M. Böhme, V. J. M. Manès, and S. K. Cha, "Boosting Fuzzer Efficiency: An Information Theoretic Perspective," in *Proc. ESEC/FSE 2020*, pp. 678–689. https://doi.org/10.1145/3368089.3409748

[45] **[recent]** D. Morwani, M. Pagliardini, M. Andriushchenko, T. Pethick, and N. Doikov, "Exponential Moving Average of Weights in Deep Learning: Dynamics and Benefits," 2024. arXiv:2411.18704. https://arxiv.org/abs/2411.18704

[46] J. S. Hunter, "The Exponentially Weighted Moving Average," *Journal of Quality Technology*, vol. 18, no. 4, pp. 203–210, 1986.

[47] **[recent]** Tracetest Authors, "Trace-Based Integration Tests for Microservices with OpenTelemetry," 2024–2025. https://tracetest.io/

---

## 8. Top-5 most-recent (2024–2026) load-bearing citations

| # | Citation | Why load-bearing |
|---|---|---|
| 1 | **[30] AutoRestTest (ICSE 2025)** | This is the closest peer to RESTest's smart-fetch design — multi-agent LLM with a semantic property dependency graph to map parameters to producer endpoints. It establishes the precedent that *separate* agents for API, dependency, parameter, and value selection are measurable independently — which is exactly the S1/S2/S3 split. |
| 2 | **[16] LlamaRestTest (FSE 2025)** | Provides the only published *small-LLM* baseline (LlamaREST-EX) directly comparable to RESTest's local-Ollama setup. Reports 72.44% valid-input-generation rate — the empirical floor we use to set the DEHR amber/green threshold (S3.1). |
| 3 | **[12] DeepREST (ASE 2024)** | DeepREST uses curiosity-driven RL to learn implicit constraints; its key thesis — that successful API interactions feed back into input data generation — is the direct analogue of RESTest's success-rate EMA on `ApiMapping`. The paper's empirical results justify the S5.1 REC threshold. |
| 4 | **[14] RESTGPT (ICSE-NIER 2024)** | Reports the canonical 73 % valid-parameter rate that anchors the green-threshold band in our KPI table for DEHR, ESHR, and yield_accepted. Also the only paper that measures *value generation from natural-language* in the way Smart-Fetch's `directValueExtraction` prompt does — direct analogue. |
| 5 | **[32] RBCTest (arXiv 2025)** | Combines static + dynamic constraint mining with explicit Observation-Confirmation prompting that achieves 85.1–93.6 % constraint-mining precision. Shapes our SDP threshold (≥ 0.50 at top-3 is achievable; ≥ 0.85 at top-1 is the published-state-of-art ceiling) and the SFM-F1 threshold. |

These five papers together (a) establish the metric formulae are computable, (b) provide empirical baselines for the green/amber/red bands, and (c) confirm the smart-fetch design pattern (LLM-driven service discovery + cached value pool + EMA-weighted mappings) is a recognised research direction with measurable success criteria.

---

## 9. Summary of source-of-each-metric file:line references

For convenience, all citations into the RESTest source code, in metric order:

| Metric | File | Lines | Method |
|---|---|---|---|
| S1.1 SDP | `inputs/smart/SmartInputFetcher.java` | 378–418 | `discoverByLLM` |
| S1.2 SDR | `inputs/smart/SmartInputFetcher.java` | 339–356 | `discoverByPatterns` (currently disabled) |
| S2 ESHR | `inputs/smart/OpenAPIEndpointDiscovery.java` | 140–151 | `findBestEndpoint` |
| S2 ESHR (HTTP) | `inputs/smart/SmartInputFetcher.java` | 423–476 | `fetchFromApiMapping` |
| S3.1 DEHR | `inputs/smart/SmartInputFetcher.java` | 567–629 | `extractValueDirectlyFromResponse` |
| S3.2 SFM-F1 | `inputs/smart/SmartInputFetcher.java` | 782–880 | `findSemanticMatch`, `askLLMForSemanticFieldMatching` |
| S4.1 CHR (main) | `inputs/smart/SmartInputFetcher.java` | 213–220 | cache check in `fetchFromSmartSource` |
| S4.1 CHR (diverse) | `inputs/smart/SmartInputFetcher.java` | 174–178 | `getNextDiverseValue` early-return |
| S4.2 CVD | `inputs/smart/SmartInputFetcher.java` | 1397–1423, 1480–1509 | `cacheDiverseValue`, `getNextDiverseValue` |
| S4.3 TSR | `inputs/smart/SmartInputFetcher.java` | 68–80 | `CachedValue.isExpired`, `cacheTtlSeconds` config |
| S5.1 REC | `inputs/smart/ApiMapping.java` | 59–64 | `updateSuccessRate` (EMA, alpha=0.1) |
| S5.2 MS | `inputs/smart/SmartInputFetcher.java` | 1288–1339 | `loadRegistry`, `saveRegistry` |
| S5.2 MS (YAML) | `inputs/smart/InputFetchRegistry.java` | 51–74 | `loadFromFile`, `saveToFile` |
| S6 yield_smart | `inputs/smart/SmartInputFetcher.java` | 1150–1221 | `fallbackToLLM` (negation of) |
| S7 TPB | `inputs/smart/SmartInputFetcher.java` | 222–243 | Priority-0 trace block |
| S7 TPB (population) | `generators/MultiServiceTestCaseGenerator.java` | 943 | `setTraceProducerEndpoints` |
| S7 TPB (consumption) | `inputs/llm/ParameterInfo.java` | 71–72 | `getTraceProducerEndpoints` |

---

*End of framework.*
