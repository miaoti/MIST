# MIST — A-Conference Evaluation & Benchmark Design

> Scope: design the evaluation that lets MIST (black-box REST test generator for microservices; OTel/Jaeger traces as generation input + assertion target; label-free `HiddenDownstreamFailure` trace oracle) clear the ICSE/FSE/ASE/ISSTA bar, and frankly rate whether the inputs that evaluation needs are obtainable.
> Date: 2026-06-30. Every nontrivial claim is cited; refs marked **[P]**rimary / **[S]**econdary. Nothing fabricated; uncertainties flagged.

## 0. Corrections to the briefing (read first)

Three load-bearing facts in the briefing are wrong or unverifiable as stated. Fix them before they reach a reviewer.

1. **"~42% benign" is a misread of the Uber paper.** The Uber *Tale of Errors* (SIGMETRICS'25) reports ~**29.35%** of successful (2xx) requests carry ≥1 non-fatal downstream error [P: Lee et al. 2025, DOI 10.1145/3700436]. The "42%" is the **"Entity Not Found" error *category* share (42.46%)**, not a benign rate [S: Tiwari blog summarizing the paper]. The paper publishes **no clean benign-vs-harmful split** and argues most non-fatal errors are *not* truly benign (requests with them do 1.9× compute, 2.9× P99 latency) [S: Tiwari; P: DOI 10.1145/3700436]. **Implication:** the benign rate of swallowed-downstream errors is *unmeasured in the literature*. This strengthens MIST's case (you must construct labels) but the "42% benign" claim must be deleted.
2. **AutoRestTest is ICSE 2025, not ISSTA 2025.** Research-track paper "A Multi-Agent Approach for REST API Testing with Semantic Graphs and LLM-Driven Inputs," ICSE 2025 [P: Kim/Stennett/Sinha/Orso, arXiv 2411.07098]; tool demo at ICSE 2025 [P: arXiv 2501.08600]; artifact `selab-gatech/AutoRestTest`.
3. **RCAEval is WWW 2025 (Web Conf companion), not ICSE.** [P: Pham et al., DOI 10.1145/3701716.3715290 / arXiv 2412.17015].

Anchors that check out: EmRest ISSTA'25 — 16 real-world APIs, 226 unique bugs others miss [P: DOI 10.1145/3728964]. Morest ICSE'22 — 6 projects, 44 bugs (13 unique), 2 developer-confirmed at Bitbucket [P: DOI 10.1145/3510003.3510133; arXiv 2204.12148]. LlamaRestTest FSE'25 — 12 services [P: DOI 10.1145/3715737]. LogiAgent — arXiv preprint, venue unconfirmed [P: arXiv 2503.15079].

---

## 1. Recommended evaluation protocol (concrete)

A reviewer at this bar expects: multiple SUTs, a non-trivial baseline that is **non-zero on the target bug class** (not "0 by construction"), real/confirmed or injected-but-grounded bugs, ablations isolating each contribution, and honest precision/recall/FP with statistics. Concretely:

**E1 — Generation effectiveness (the "test generator" claim).** On ≥6 SUTs, run MIST and the black-box REST baselines (EvoMaster, RESTler, Schemathesis, Morest, AutoRestTest) under a **fixed budget** (e.g., 1h × 10 seeds each, identical OpenAPI inputs). Report operation/endpoint coverage and **all** faults each tool finds *by its own oracle* (5xx, crashes, spec/schema violations). This calibrates baselines as strong (Morest finds 44 bugs; EvoMaster routinely finds 500s) and defuses the strawman charge — see §3.

**E2 — Oracle effectiveness (the headline `HiddenDownstreamFailure` claim).** On a **labeled trace corpus** (§4), measure precision/recall/FP of MIST's oracle against (a) response-only baselines (0 by construction — reported only to frame the gap, never as the sole comparison) and (b) **non-zero trace-aware comparators**: a naive "any error span under a 2xx entry" detector, Tracetest with a generic span-error assertion, and an unsupervised trace-anomaly detector (TraceAnomaly/TraceRCA). MIST must win on **precision/FP at matched recall** — see §3.

**E3 — Trigger effectiveness (the "cross-service negative input" claim).** Measure how often each generator drives a SUT into a genuine hidden-downstream state (trigger rate per endpoint). Response-only generators trigger some by luck (non-zero); MIST should trigger more by trace-guided construction.

**E4 — Prevalence study (external validity).** On realistic workloads, self-measure the rate of "2xx-at-entry hiding a downstream server-error," per-request and per-endpoint, with CIs; compare to Uber's 29.35% [P: DOI 10.1145/3700436] as an external-validity anchor — explicitly noting Uber gives *no* benign split, so MIST's adjudicated benign rate is a **new** measurement, not a reproduction.

**E5 — Ablations** (§5): remove the benign-filter (→ naive oracle), remove cross-service negative generation, swap trace-input for spec-only input. Each isolates one contribution.

**E6 — Artifact**: release the labeled swallowed-downstream trace corpus + adjudication rubric. This is itself a contribution (no such benchmark exists; §4) and the strongest hedge against the ground-truth reject.

Deliverable shape that matches the bar: **6–8 SUTs, 5+ runnable REST baselines, 3+ trace-aware comparators, a precision/recall/FP frontier on a released labeled corpus, prevalence with CIs, 3 ablations, 10 seeds/config with effect sizes.**

---

## 2. SUT catalogue & recommended set

Selection axes for MIST specifically: (i) a **client-facing REST/HTTP entry** rich enough to *generate* against (ideally OpenAPI); (ii) **OTel/Jaeger trace-context** across services so deeper spans are visible; (iii) a real **gateway→downstream topology** so a downstream failure *can* be swallowed; (iv) **maintenance**. A recurring tension: deep-topology demos (DeathStarBench, Online Boutique) communicate internally over gRPC/Thrift with only a thin HTTP frontend — strong for the *oracle*, weak for *generation*. Disclose this split.

| SUT | #services | Lang | REST entry (generation) | OTel/Jaeger | Gateway→downstream | Maint. | Role / notes | Source |
|---|---|---|---|---|---|---|---|---|
| **TrainTicket** (Fudan) | 41+ | Java/Spring (poly) | Rich REST, many params | Yes (Jaeger/OTel instrumentable) | nginx/ts-gateway → deep chains | Active | **Primary.** Best on both halves; has F1–F22 fault corpus | [P] DOI 10.1109/TSE.2018.2887384; github.com/FudanSELab/train-ticket |
| **TeaStore** (Descartes) | 5 (+registry) | Java | REST (WebUI) | Instrumentable | WebUI → Auth/Persistence/Recommender/Image | Active | Core gen+oracle; clean tiers | [P] DOI 10.1109/MASCOTS.2018.00018; github.com/DescartesResearch/TeaStore |
| **Sock Shop** (Weaveworks) | ~7 | Go/Java/Node (poly) | REST front-end | Zipkin native; OTel-able | front-end → catalogue/carts/orders/payment/shipping | Weaveworks defunct (2024); forks live | Polyglot REST; **maint. caveat** | [S] InfoQ; github.com/microservices-demo/microservices-demo; fork ocp-power-demos/sock-shop-demo |
| **OpenTelemetry Demo** (Astronomy Shop) | ~15+ | 12 langs (poly) | HTTP frontend + gRPC downstream | **Native OTel** | frontend → product/cart/checkout/payment… | Active (OTel project) | **Oracle-strong**; built-in fault flags (`productCatalogFailure`) = ready swallowed-downstream scenarios | [P] github.com/open-telemetry/opentelemetry-demo |
| **Online Boutique** (GCP) | 11 | poly | HTTP frontend only → gRPC | Yes (OTel/OpenCensus) | frontend → 10 gRPC services | Active | Oracle-strong, **thin REST surface** for generation | [P] github.com/GoogleCloudPlatform/microservices-demo |
| **Istio Bookinfo** | 4 | poly | HTTP productpage | Jaeger via Istio | productpage → details/reviews → ratings | Active (Istio) | Minimal but clean; **benign-degradation reference** (ratings-down handled) | [P] istio.io/latest/docs/examples/bookinfo |
| *DeathStarBench* hotelReservation/socialNetwork | 6 / ~30 | Go,gRPC / C++,Thrift | thin HTTP (nginx+wrk2) | **Native Jaeger/OpenTracing** | deep RPC chains | Active | *Stretch:* credibility + deep topology; **thin REST**, oracle-only | [P] DOI 10.1145/3297858.3304013; github.com/delimitrou/DeathStarBench |
| *spring-petclinic-microservices* | ~5 | Java/Spring Cloud | REST + gateway | Sleuth/OTel | gateway/Eureka → customers/vets/visits | Active | *Stretch:* extra Spring-Cloud generation SUT | [P] github.com/spring-petclinic/spring-petclinic-microservices |

**Recommended defensible set (6 core):** TrainTicket, TeaStore, Sock Shop, OpenTelemetry Demo, Online Boutique, Bookinfo. **Rationale:** TrainTicket + TeaStore + Sock Shop carry the *generation* story (rich polyglot REST, maintained); OTel Demo + Online Boutique + Bookinfo carry the *oracle/topology* story and bring native instrumentation and a built-in benign-degradation case (Bookinfo) plus vendor fault-injection (OTel Demo). **Add DeathStarBench (hotelReservation) and spring-petclinic-microservices as stretch SUTs (7–8)** to approach EmRest-scale breadth. This set keeps MIST's current four (TrainTicket, Bookinfo, Sock Shop, Online Boutique) so prior results carry over, and adds TeaStore + OTel Demo, which directly upgrade trace quality and supply *labeled* faults.

DeathStarBench is the ASPLOS'19 reference suite with native Jaeger [P: Gan et al., DOI 10.1145/3297858.3304013]; include it for credibility but state plainly its REST surface is too thin to exercise MIST's generator — use it as an oracle/prevalence SUT only.

---

## 3. Baselines + anti-tautology design

### 3a. Runnable baseline inventory

| Baseline | Type | Runnable head-to-head? | Notes | Source |
|---|---|---|---|---|
| **EvoMaster** (black-box mode) | SBST REST | **HIGH** | De-facto standard; consumes OpenAPI; finds 500s | [P] DOI 10.1007/s10515-024-00478-1 |
| **RESTler** | Stateful fuzzer | **HIGH** | Found 28 bugs in GitLab; OpenAPI-driven | [P] ICSE'19, patricegodefroid.github.io/.../icse2019.pdf |
| **Schemathesis** | Property-based | **HIGH** | OpenAPI/GraphQL; easy to run | [P] schemathesis.io; PyPI |
| **RestTestGen** | Model-based black-box | MED | ICST'20; artifact older | [P] DOI 10.1109/ICST46399.2020.00023 |
| **Morest** | Model-based (RPG) | MED | Artifact exists; older deps | [P] arXiv 2204.12148 |
| **AutoRestTest** | MARL + SPDG + LLM | MED | `selab-gatech/AutoRestTest`; LLM cost | [P] arXiv 2411.07098 (**ICSE'25**) |
| **LlamaRestTest** | Fine-tuned SLM | MED | Needs fine-tuned Llama3-8b weights | [P] DOI 10.1145/3715737 |
| **EmRest** | Error-message analysis | MED | Artifact zenodo.org/records/15202098; different SUT set | [P] DOI 10.1145/3728964 |
| **LogiAgent** | Multi-agent logical oracle | LOW | Preprint; artifact unconfirmed | [P] arXiv 2503.15079 |
| **Tracetest** | Trace-based testing tool | **HIGH** | Asserts on spans but **assertions hand-written** | [P] github.com/kubeshop/tracetest |
| **TraceAnomaly** | Unsupervised trace anomaly | MED | ISSRE'20; research code | [P] github.com/NetManAIOps/TraceAnomaly |
| **TraceRCA** | Trace spectrum RCA | MED | IWQoS'21; localization not detection | [P] TraceRCA, IWQoS 2021 |
| **Nezha** | Multimodal RCA | MED | FSE'23; injected-fault ground truth | [P] DOI 10.1145/3611643.3616249 |
| **RCAEval** | RCA benchmark + 15 baselines | **HIGH** | 735 cases, 11 fault types, packaged baselines | [P] arXiv 2412.17015 (**WWW'25**) |
| **AGORA** | Invariant test oracles | MED | Oracle baseline for *response* invariants (81.2% prec.) | [P] ISSTA'23, DOI 10.1145/3597926.3598114 |

### 3b. Anti-tautology design (the central reviewer risk)

**The reject:** "MIST finds N hidden-downstream bugs, baseline finds 0 — but the baseline finds 0 *by construction* because it never reads traces. Tautology." Defend on three pillars:

- **Pillar A — Competence calibration.** Run EvoMaster/RESTler/Schemathesis/Morest/AutoRestTest on the *same* SUTs and report *all* faults they find by their *own* oracles (500s, crashes, spec violations). They are non-zero and strong (E1). This proves they are not strawmen.
- **Pillar B — Shared-detectable overlap.** On loud entry-level 5xx, show MIST detects a superset/comparable set — MIST is not a one-trick oracle that *only* sees the exotic class.
- **Pillar C — Non-zero trace-aware comparator on the target class (the crux).** Never let the *only* comparison on hidden-downstream be 0-vs-N. Introduce comparators that are **non-zero on the target class** and beat them on **precision/FP**:
  - **Naive span-error oracle** — "flag any 2xx-entry trace containing a span with error/status≥500." Trace-aware, high recall, but **cannot separate genuine swallowed failures from benign graceful-degradation** (optional-dependency, designed fallback, retry-then-succeed). It floods with false positives. MIST's label-free discrimination is exactly what raises precision at matched recall. **Headline becomes "same recall, MIST 3–5× precision," not "N vs 0."**
  - **Tracetest + generic span-error assertion** — a *real, runnable* trace-aware tool, but assertions are **hand-authored per endpoint** [P: kubeshop/tracetest]. Use it to show MIST *automates* what Tracetest needs a human to write, and scales where Tracetest does not.
  - **TraceAnomaly / TraceRCA** — unsupervised trace detectors, non-zero on the class but tuned for latency/structural anomaly and root-cause, with high FP on benign degradation.

**Net framing:** the contribution is a **precision/recall/FP frontier on the same bug class against trace-aware baselines**, plus a higher trigger rate (E3) than response-only generators that occasionally hit the class by luck. This is non-tautological and is the single most important design decision in the whole evaluation.

---

## 4. Ground-truth labeling protocol

**The central methodological problem:** distinguish a **genuine swallowed defect** from **benign graceful-degradation**. Two hard facts bound the design:
- **No wild corpus exists.** Prior MIST probing (archive-2026-06-01/probe-wildbugs.md) found **0 reproducible developer-confirmed wild swallowed-downstream bugs**: such fixes hide inside generic "fix error handling" commits with no mineable signal, and the bugs are systematically under-reported because the reporting client sees only a 2xx. Corroborated structurally by Yuan et al. OSDI'14: error-handling defects (empty/over-broad catch, swallow) are pervasive and ~92% of catastrophic failures stem from incorrect handling of *non-fatal* errors [P: Yuan et al., usenix.org/.../osdi14-paper-yuan.pdf] — but that catalogue is failure cases in data systems, not replayable request/response traces.
- **No measured benign rate in the literature** (see §0): Uber gives 29.35% prevalence but no benign split [P: DOI 10.1145/3700436].

Therefore labels must be **constructed**, in three strata, and **released as an artifact**:

**Stratum 1 — Positive ground truth (genuine swallowed failures, true by construction).**
- **TrainTicket fault corpus F1–F22** [P: DOI 10.1109/TSE.2018.2887384; github.com/FudanSELab/train-ticket-fault-replicate] — select faults that manifest as swallowed-downstream (per prior probe: F6 retry-timeout, F8 dropped-token, F10 mis-call, F20 enum-mismatch). These are *industry-replicated*, which is the strongest grounding available short of wild bugs.
- **OTel Demo built-in fault flags** (`productCatalogFailure`, `recommendationServiceCacheFailure`, …) [P: open-telemetry/opentelemetry-demo] — *vendor-authored* downstream faults with a known failing service.
- **Controlled fault injection** (code-level exception injection, dependency 500s, scale-to-zero) mirroring RCAEval's 11 fault types [P: arXiv 2412.17015] and Nezha's injection points [P: DOI 10.1145/3611643.3616249]. Each injected fault has a **known root-cause span → known label**.

**Stratum 2 — Benign ground truth (the false-positive traps).** Catalogue **designed-degradation** paths from SUT code/docs and label them benign-by-design:
- Bookinfo `reviews→ratings`: ratings-down is *handled*, productpage returns 2xx with "ratings unavailable" [P: istio.io/.../bookinfo] — the canonical benign case.
- Online Boutique `adservice`/`recommendationservice` optional dependencies; OTel Demo recommendation-cache degradation (degrades, not fails).
- Generic patterns: retry-then-succeed, circuit-breaker default, optional-feature fallback. These are exactly the cases a naive span-error oracle mislabels — the precision test of §3.

**Stratum 3 — Adjudication for realistic traffic (the prevalence/precision sample).** For E4/E2 on non-seeded workloads, take a **stratified random sample** of MIST-flagged traces; ≥2 independent raters label genuine-vs-benign with a **pre-registered rubric**; report **inter-rater agreement (Cohen's κ)**; a third rater adjudicates disagreements.

**Operational definition (the rubric anchor).** *Genuine swallowed defect* = a downstream span server-errored on a **required** dependency, AND the entry response neither reflects the failure (still 2xx with nominal payload) NOR is the failure recovered (no successful retry, no designed fallback). *Benign* = optional dependency, designed fallback, or recovered-by-retry. This operationalizes the Uber "non-fatal but hidden" notion [P: DOI 10.1145/3700436] into a checkable predicate.

**Defensibility & honest disclosure.**
- Pre-register the rubric; **release the labeled trace corpus + adjudication guide** (becomes the *first* labeled swallowed-downstream trace benchmark — a contribution in itself).
- Injected/replicated ground truth is **accepted practice** in this subfield (RCAEval, Nezha both use injected faults) [P: arXiv 2412.17015; DOI 10.1145/3611643.3616249] — cite this to preempt the "not wild" objection.
- **Do not** claim a wild developer-confirmed swallowed-downstream count rivaling EmRest's 226 or AGORA's 32 [P: DOI 10.1145/3728964; DOI 10.1145/3597926.3598114]; those counts come from response-visible bugs on live/black-box APIs, a path closed to a trace-only class (you must own the deployment to see downstream spans). State this limitation explicitly. The defensible bug story = injected/replicated positives + adjudicated self-measured prevalence + the released corpus, **plus** any incidental developer-confirmed *loud* bugs MIST finds along the way (target ≥2, matching Morest's bar [P: DOI 10.1145/3510003.3510133]).

---

## 5. Metrics & statistics

**Oracle quality (E2).** Precision = genuine / flagged; Recall = flagged-genuine / all-genuine (computable on Strata 1–2 where labels are known); FP rate on Stratum 2 (benign traps). Report a **precision-recall frontier** vs the trace-aware comparators (§3c). Headline metric: **precision (and FP) at matched recall**.

**Prevalence (E4).** Two denominators, reported separately: **per-request** (fraction of 2xx-entry requests hiding a downstream server-error) and **per-endpoint** (fraction of endpoints exhibiting ≥1 such request). Report **Wilson score / Clopper–Pearson 95% CIs** for every proportion. Compare per-request rate to Uber's 29.35% as external validity [P: DOI 10.1145/3700436].

**Generation/trigger (E1, E3).** Operation & endpoint coverage; faults-by-own-oracle (calibration); trigger rate per endpoint. Follow the comparative-study template of Kim et al., "No Time to Rest Yet," ISSTA'22 [P: arXiv 2204.08348] for fixed-budget, multi-tool REST comparison.

**Ablations (E5), each isolating one contribution:**
- **A1 (benign-filter):** full oracle vs naive "any error span" → isolates the **label-free discrimination** (expect large precision/FP gain, recall ~flat).
- **A2 (cross-service negative generation):** with vs without → isolates **trigger rate / recall** of the generator.
- **A3 (trace-input vs spec-only input):** isolates the value of **traces as generation input**.

**Statistical rigor.** Randomized algorithm → ≥**10 seeds per config**; report mean ± CI; compare with **Mann–Whitney U** and **Vargha–Delaney Â₁₂** effect sizes per Arcuri & Briand [P: DOI 10.1002/stvr.1486]. Apply **Holm/Bonferroni** correction across the multi-SUT × multi-baseline grid. For the adjudication sample, report **Cohen's κ** and sample size justification (target CI half-width on precision ≤ 5%). Pre-register thresholds and the rubric.

---

## 6. Obtainability risk

| Required input | Obtain. | Justification | Fallback if LOW/MED |
|---|---|---|---|
| **SUTs (deployable, traced)** | **HIGH** | All 6 core open-source & maintained; OTel Demo native, TrainTicket/TeaStore instrumentable | Use native-OTel SUTs (OTel Demo, DeathStarBench, Online Boutique) to cap instrumentation cost |
| **Uniform OTel + gateway across all SUTs** | **MED** | Polyglot SUTs need consistent context propagation + a real gateway; Sock Shop is Zipkin-native, demos are gRPC | Standardize on OTel Collector; accept per-SUT adapters; lean on natively-instrumented SUTs |
| **Runnable REST baselines** | **MED–HIGH** | EvoMaster/RESTler/Schemathesis HIGH; Morest/RestTestGen/EmRest MED (older/diff SUT set); LlamaRestTest/AutoRestTest MED (LLM/weights cost); LogiAgent LOW | Drop LogiAgent; keep ≥4 strong runnable tools; reuse EmRest/RCAEval artifacts where possible |
| **Baselines running on MIST's SUTs** | **MED** | Baselines consume OpenAPI; Sock Shop/Online Boutique lack rich specs → must author/generate specs | Author OpenAPI for thin-spec SUTs; or restrict head-to-head generation to spec-rich SUTs (TrainTicket, TeaStore, petclinic) |
| **Trace-aware comparators** | **MED–HIGH** | Tracetest HIGH; naive span-oracle trivial to build; TraceAnomaly/TraceRCA/Nezha MED (research code); RCAEval HIGH (packaged) | Naive span-error oracle + Tracetest suffice for the anti-tautology argument; RCAEval baselines as bonus |
| **Ground-truth labels (genuine vs benign)** | **LOW** ⚠ | **Biggest risk.** No measured benign rate in literature (§0); benign/genuine boundary needs human adjudication; scales poorly on real traffic | Construct labels (Strata 1–3); release corpus + rubric; report κ; injected ground truth is accepted (RCAEval/Nezha) |
| **Developer-confirmed *wild* swallowed-downstream bugs** | **LOW** ⚠ | Probe verdict: **0 reproducible**; structurally under-reported + unmineable fixes | **Do not claim.** Substitute injected/replicated positives + self-measured prevalence; report incidental confirmed *loud* bugs (≥2) for the Morest-style bar |
| **Realistic workload for prevalence** | **MED** | SUT load generators exist (wrk2, locust) but may not exercise error paths | Combine load-gen traffic with MIST-generated negative inputs to surface error paths |

**Two LOW items dominate**, and they are the *same* methodological fault line: the **genuine-vs-benign label** and the **wild developer-confirmed bug**. The first is mitigable (construct + release labels; this is a contribution). The second is **structurally unobtainable** for a trace-only class and must be *designed around*, not claimed.

---

## 7. References

Primary [P] = the paper/artifact/repo itself; Secondary [S] = summaries/blogs/wikis. Accessed 2026-06-30.

**REST API testing**
- [P] Atlidakis, Godefroid, Polishchuk. *RESTler: Stateful REST API Fuzzing.* ICSE 2019. https://patricegodefroid.github.io/public_psfiles/icse2019.pdf
- [P] Viglianisi, Dallago, Ceccato. *RestTestGen: Automated Black-Box Testing of RESTful APIs.* ICST 2020. DOI 10.1109/ICST46399.2020.00023
- [P] Liu, Li, Deng, Liu et al. *Morest: Model-based RESTful API Testing with Execution Feedback.* ICSE 2022. DOI 10.1145/3510003.3510133 / arXiv 2204.12148
- [P] Arcuri et al. *EvoMaster: black- and white-box search-based fuzzing for REST/GraphQL/RPC APIs.* Autom. Softw. Eng. 2024. DOI 10.1007/s10515-024-00478-1
- [P] Alonso, Segura, Ruiz-Cortés. *AGORA: Automated Generation of Test Oracles for REST APIs.* ISSTA 2023. DOI 10.1145/3597926.3598114
- [P] *EmRest: Effective REST APIs Testing with Error Message Analysis.* ISSTA 2025 (PACMSE). DOI 10.1145/3728964. Artifact: zenodo.org/records/15202098
- [P] *LlamaRestTest: Effective REST API Testing with Small Language Models.* FSE 2025. DOI 10.1145/3715737 / arXiv 2501.08598
- [P] Kim, Stennett, Sinha, Orso. *A Multi-Agent Approach for REST API Testing (AutoRestTest).* **ICSE 2025.** arXiv 2411.07098; tool demo arXiv 2501.08600; repo selab-gatech/AutoRestTest
- [P] *LogiAgent: Automated Logical Testing for REST Systems with LLM-Based Multi-Agents.* arXiv 2503.15079 (preprint; venue unconfirmed)
- [P] Kim, Sinha, Orso. *Automated Test Generation for REST APIs: No Time to Rest Yet.* ISSTA 2022. arXiv 2204.08348
- [P] Schemathesis. schemathesis.io ; pypi.org/project/schemathesis

**Microservice benchmarks / SUTs**
- [P] Zhou, Peng, Xie et al. *Fault Analysis and Debugging of Microservice Systems (TrainTicket).* TSE 2018 (Best Paper). DOI 10.1109/TSE.2018.2887384. Repos: github.com/FudanSELab/train-ticket , /train-ticket-fault-replicate
- [P] Gan et al. *An Open-Source Benchmark Suite for Cloud Microservices (DeathStarBench).* ASPLOS 2019. DOI 10.1145/3297858.3304013. Repo: github.com/delimitrou/DeathStarBench
- [P] von Kistowski et al. *TeaStore: A Micro-Service Reference Application.* MASCOTS 2018. DOI 10.1109/MASCOTS.2018.00018. Repo: github.com/DescartesResearch/TeaStore
- [P] OpenTelemetry Demo (Astronomy Shop). github.com/open-telemetry/opentelemetry-demo
- [P] Online Boutique (microservices-demo). github.com/GoogleCloudPlatform/microservices-demo
- [P] Sock Shop. github.com/microservices-demo/microservices-demo ; [S] InfoQ, *Introducing Sock Shop.* infoq.com/articles/sock-shop
- [P] Istio Bookinfo. istio.io/latest/docs/examples/bookinfo
- [P] spring-petclinic-microservices. github.com/spring-petclinic/spring-petclinic-microservices

**Trace oracle / RCA / anomaly**
- [P] Lee, Zhang, Parwal, Chabbi. *The Tale of Errors in Microservices.* SIGMETRICS 2025. **DOI 10.1145/3700436** (ext. abstract 10.1145/3726854.3727320). Artifact: zenodo.org/records/13947828. [S] Tiwari, *The Hidden Cost of Success*, abhishek-tiwari.com (corroborates 29.35%; Entity-Not-Found 42.46% category; **no benign split**)
- [P] Yuan et al. *Simple Testing Can Prevent Most Critical Failures.* OSDI 2014. usenix.org/system/files/conference/osdi14/osdi14-paper-yuan.pdf
- [P] Liu et al. *TraceAnomaly: Unsupervised Detection of Microservice Trace Anomalies.* ISSRE 2020. github.com/NetManAIOps/TraceAnomaly
- [P] Li et al. *TraceRCA: Practical Root Cause Localization via Trace Analysis.* IWQoS 2021
- [P] Yu et al. *Nezha: Interpretable Fine-Grained RCA on Multi-modal Observability Data.* ESEC/FSE 2023. DOI 10.1145/3611643.3616249. Repo: github.com/IntelligentDDS/Nezha
- [P] Pham et al. *RCAEval: A Benchmark for RCA of Microservice Systems with Telemetry Data.* **WWW 2025 (companion).** DOI 10.1145/3701716.3715290 / arXiv 2412.17015
- [P] Tracetest. github.com/kubeshop/tracetest

**Microservice bug/issue datasets**
- [P] Waseem et al. *Understanding the Issues, Their Causes and Solutions in Microservices Systems: An Empirical Study.* arXiv 2302.01894 (2,641 issues, 15 systems; exception-handling & service-communication top categories)

**Statistics**
- [P] Arcuri, Briand. *A Hitchhiker's Guide to Statistical Tests for Assessing Randomized Algorithms in SE.* STVR 2014. DOI 10.1002/stvr.1486

**Internal (this repo)**
- [P] archive-2026-06-01/probe-wildbugs.md — verdict: 0 reproducible wild swallowed-downstream bugs; prevalence-by-citation + injected faults is the only defensible study leg
- [P] archive-2026-06-01/probe-attribution.md — param-level attribution is not a load-bearing novelty; service-level attribution is the honest ceiling
