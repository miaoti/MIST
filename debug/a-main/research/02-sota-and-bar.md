# SOTA & The Bar: Automated REST API / Microservice Testing (2023–2026)

**Scope:** Map the current research frontier of automated REST API / microservice testing at top venues
(ICSE, FSE/ESEC-FSE, ASE, ISSTA, TSE/TOSEM) and define the concrete evaluation "bar" a new contribution must clear.
**Positioning target:** MIST — black-box REST test generator for microservices that uses the distributed
OpenTelemetry/Jaeger **trace** as both a generation input and an assertion target; headline is a label-free
**"Trace Shape Oracle"** whose `HiddenDownstreamFailure` invariant fires when a client-facing entry returns HTTP 2xx
while a **deeper span server-errored** (a swallowed downstream failure); plus cross-service negative-input generation.

**Compiled:** 2026-06-30. **Method:** web search + primary-source retrieval (arXiv full text for Microusity and the
TOSEM survey were extracted and read directly). Citations marked **[P]** primary (the paper/tool itself) or
**[S]** secondary (survey, blog, third-party summary). Items I could not verify are explicitly flagged.

> **Skeptical bottom line up front.** The field's de-facto oracle is "edge returns 500 / response violates schema."
> MIST's trace-shape oracle is genuinely under-occupied territory — BUT it is **not empty**: **Microusity (ICPC'23)**
> already fuzzes a Backend-for-Frontend edge and reports backend sub-request 5xx / exception leakage that is *not*
> visible at the edge; **Tracetest (CNCF)** already asserts on OpenTelemetry spans; **AGORA+/SATORI** already learn
> automated oracles beyond 500; **MISH (2024)** already learns microservice behavioral automata from logs. MIST must
> be positioned *precisely* against these. Details in §4.

---

## 1) Frontier Table

Oracle level legend: **SC** = status-code (mostly 5xx) · **SCH** = response-schema conformance · **BODY** =
response-body value/constraint · **SEC** = security rule · **LOGIC** = business-logic/metamorphic · **TRACE** =
cross-service span/trace structure. "Bugs" column notes whether **dev-confirmed**, **500-triggering** (unique
internal errors, not confirmed), or **injected/seeded**.

### 1a. REST API test *generators* (black-box unless noted)

| System | Venue/Year | One-line contribution | Oracle | #SUTs | Baselines | Bugs (type) |
|---|---|---|---|---|---|---|
| **RESTler** [P] | ICSE'19 | First *stateful* REST fuzzer; infers producer-consumer deps from OpenAPI | SC + SEC rules | GitLab + Azure/O365 | — (first) | 28 in GitLab (dev-confirmed) + several cloud |
| **RestTestGen** [P] | ICST'20; STVR'22 | Operation Dependency Graph; nominal + error scenarios | SC + SCH | ~14 (orig) | RESTler-style | tens (500-triggering) |
| **RESTest** [P] | ISSTA'21 (tool) | Constraint-based; IDL inter-parameter dependency language + solver | SC (5xx/4xx-on-valid) | several | — | "30% of 90K tests" failures (500-triggering) |
| **Morest** [P] | ICSE'22 | RESTful-service Property Graph (RPG) refined with execution feedback | SC + SCH | 6 real projects | EvoMaster, RESTler, RestTestGen | 44 (13 unique) — 500-triggering |
| **EvoMaster** [P] | IEEE SW'21; ASE-J'24 | Search-based; **only tool doing both white- & black-box**; MIO algorithm | SC + SCH (+ WB heuristics) | EMB corpus (~14–19 REST) | de-facto baseline for all | 80 real bugs (WB, dev-confirmed) |
| **Schemathesis** [P] | ICSE'22 (SEIP) | Property-based, semantics-aware fuzzing from OpenAPI/GraphQL | SC + SCH | 16 real services | other fuzzers | 1.4×–4.5× more defects than prior (500/schema) |
| **NLPtoREST** [P] | ISSTA'23 | NLP mines rules from human-readable OpenAPI text → enhanced spec | (enhances input, oracle=SC) | EMB-style | ARTE | +103% coverage (enabler, not bug-finder) |
| **RESTGPT** [P] | ICSE'24 NIER | LLM extracts param constraints/values to enhance specs | (enhances input) | — | ARTE (17%→73% valid) | enabler |
| **ARAT-RL** [P] | (Kim et al.'23) | Reinforcement-learning adaptive REST testing (common LLM-era baseline) | SC | ~ | EvoMaster/Morest/RESTler | 500-triggering |
| **AutoRestTest** [P] | ICSE'25 | MARL (4–5 agents) + Semantic Property Dependency Graph + LLM values | SC (500) + coverage | 12 real-world | 4 leading BB tools incl. RESTGPT-enhanced | only tool to trigger 500 in Spotify (500-triggering) |
| **LlamaRestTest** [P] | FSE'25 (PACMSE) | 2 fine-tuned/quantized Llama3-8B (inter-param dep + example values) | SC (500) + coverage | 12 real-world | RESTGPT + SOTA tools | more 500s + coverage than larger-model tools |
| **EmRest** [P] | ISSTA'25 | Error-message analysis: 4xx msgs → prune input space; 5xx msgs → focus | SC (500) + 4xx-msg mining | 16 real-world | SOTA BB tools | **226 unique bugs** others miss (500-triggering) |
| **LogiAgent** [P] | arXiv'25 (2503.15079) | LLM multi-agent with **logical/business-logic oracle** (beyond crashes) | **LOGIC** | 12 REST systems | (LLM tools) | 234 issues = 139 bugs + 95 enhancements; 66% acc |
| **RESTifAI** [P] | ICSE'26 Demo (2512.08706) | LLM happy-path + derived negatives; reusable Postman/Newman suites | SC + SCH (2xx/4xx) | (on par w/ AutoRestTest, LogiAgent) | AutoRestTest, LogiAgent | server errors highlighted (500-triggering) |
| **MASTEST** [P] | arXiv'25 (2511.18038) | LLM multi-agent runnable-script generation | SC/SCH | — | — | ~80% runnable (≤3 tries) |
| **APITestGenie** [P] | arXiv'24 (2409.03838) | Generate executable API tests from NL requirements + spec via LLM | SC/SCH | — | — | ~80% runnable scripts |
| **MISH** [P] | arXiv'24 (2412.03420) | **White-box**: real-time *automaton learning from microservice log streams* as EvoMaster search heuristic | (fitness, not oracle) — SC for faults | 6 microservice benchmarks | MOSA | up to +42% coverage, +76% 500-detection vs MOSA |
| **MioHint** [P] | arXiv'25 (2504.05738) | LLM-assisted request mutation for white-box REST testing | SC + WB | EMB | EvoMaster MIO | coverage gains |

### 1b. REST API *oracle* generation / verification (the "richer oracle" line)

| System | Venue/Year | Contribution | Oracle level | #SUTs | Eval headline |
|---|---|---|---|---|---|
| **AGORA** [P] | ISSTA'23 | Daikon-based dynamic **invariant** detection over responses; Beet front-end; 105 invariant types | BODY/SCH invariants | 7 industrial (11 ops) | 81.2% precision; 6/10 seeded errors; 11 real bugs |
| **AGORA+ / "Test Oracle Generation for REST APIs"** [P] | **TOSEM'25** (Dec 11 2025) | Extended Daikon (106 invariant types) + PostmanAssertify; authors incl. **M. D. Ernst** | BODY/SCH invariants | 20 industrial (25 ops) | 80% precision; **32 bugs** dev-confirmed/fixed |
| **SATORI** [P] | **ASE'25** (2508.16318) | **Static** LLM oracle inference from OpenAPI response-field descriptions | BODY/SCH | 12 industrial (17 ops) | F1 74.3% > AGORA+ 69.3%; 18 bugs → doc fixes |
| **RBCTest** [P] | arXiv'25 (2504.17287) | LLM mines response-**body** constraints from spec; Observation-Confirmation prompting | BODY vs spec | 19 real APIs | 85–94% precision; 46 spec/response mismatches |
| **Multi-Agent LLM Metamorphic Testing** [P] | arXiv'26 (2605.28321) | LLM-built **metamorphic relations** as oracle (incl. RBAC user-mgmt microservice) | LOGIC/metamorphic | (case studies) | very recent; not trace-based |

### 1c. Microservice testing tools that use the *distributed execution* (closest neighbors to MIST)

| System | Venue/Year | Contribution | Data used | Oracle | Evaluation |
|---|---|---|---|---|---|
| **Microusity** [P] ⚠ | **ICPC'23** tool-demo (2302.11150) | Test **BFF** edge with RESTler; map main→sub requests; localize backend that errored | **Port mapping via Zeek** network logs (NOT OTel traces) | backend/edge **5xx + exception-string leakage**; 4 report categories incl. "**only sub-responses leak**" | **User study, 8 practitioners** (Likert 4.1–4.5); **no bug counts, no baselines, no SUT-scale eval** |
| **Tracetest** [P/industrial] ⚠ | CNCF/Kubeshop (ongoing) | Trace-based testing: assert on any span in an OTel distributed trace | **OpenTelemetry traces (Jaeger/Tempo/…)** | **TRACE** — but **manually written** per-span assertions + manual trigger | open-source product; not an academic eval |
| **Filibuster** [P] | SoCC'21 | Service-level fault **injection** testing (SFIT); static analysis + concolic + dynamic reduction | instrumented service boundaries | resilience: does test suite tolerate injected dependency failures | corpus of 4 industry-reproduced + 8 example apps |
| **FastFI** [P] | arXiv'26 (2601.14800) | API call-site robustness via fault injection in microservices | call-site instrumentation | error-handling robustness | recent preprint |
| **Fuzzing Microservices in Face of Intrinsic Uncertainties** [P] | arXiv'26 (2603.02551) | **Vision/architecture** for uncertainty-driven *system-level* microservice fuzzing (EvoMaster team: Zhang, Yue, Arcuri) | service virtualization + uncertainty sim | (not specified; no trace oracle) | position paper, e-commerce example |

### 1d. Trace/observability analytics (adjacent — *diagnose*, do not generate tests or serve as test oracles)

| System | Venue/Year | Contribution | Note for MIST |
|---|---|---|---|
| **TraceAnomaly** [P] | ISSRE'20 (Xplore 9251058) | Unsupervised deep Bayesian net + posterior flow on Service-Trace-Vectors | learns "normal trace" shape, but for *production anomaly detection*, not testing |
| **TraceVAE / TraceGra** [P] | WWW'23 (10.1145/3543507.3583215); ComCom'23 | Graph-VAE / graph-DL trace anomaly detection | same: detection, not a test oracle |
| **TraceRCA** [P] | IWQoS'21 (10.1109/IWQOS52092.2021.9521340) | "More abnormal traces through a service ⇒ more likely root cause"; trace anomaly + pattern mining | RCA on injected faults; not generation/oracle |
| **Nezha** [P] | ESEC/FSE'23 (10.1145/3611643.3616249) | Multi-modal (trace+log+metric) interpretable RCA at code-region granularity | RCA; assumes failure exists |
| **RCAEval** [P] | WWW'25 (2412.17015) | RCA **benchmark**: 735 cases, 11 fault types, 15 baselines; RE3 diagnoses code-level faults via **"response code in traces"** | benchmark; note RE3 uses trace status codes as a fault signal — conceptually adjacent to MIST's invariant |
| **DeathStarBench** [P] | ASPLOS'19 (10.1145/3297858.3304013) | Open microservices benchmark (SocialNetwork, HotelReservation, Media, …) | a SUT MIST can/should use |
| **TrainTicket** [S] | (FSE'18 fault-analysis study; widely reused) | 40+ service train-booking microservice benchmark | a SUT MIST can/should use |

### 1e. Security-flavored REST/microservice testing (overlapping "silent failure" framing)

| System | Venue/Year | Contribution | Relevance |
|---|---|---|---|
| **BACFuzz** [P] | arXiv'25 (2507.15984) | Gray-box fuzzing for **Broken Access Control** (BOLA/BFLA); frames BAC as **"silent"** (no crash/error) | uses **SQL-query oracle** + LLM param selection; PHP web apps — different domain, but shares the "silent/non-crash failure needs a non-status oracle" thesis |
| **BACScan** [P] | CCS'25 (10.1145/3719027.3744825) | Black-box BAC detection | security, not downstream-error masking |
| **Mass-assignment testing (Corradini)** [P] | (2023, arXiv 2301.01261) | Black-box mass-assignment vulnerability testing for REST | security oracle, single-API |

---

## 2) The Bar (de-facto evaluation expectations, with cited anchors)

What a 2025–2026 ICSE/FSE/ASE/ISSTA REST-testing paper is expected to deliver:

**(a) Number of SUTs: ~10–20 real services.** The black-box LLM era has converged on **~12 real-world services**
(AutoRestTest: 12 [P, ICSE'25, arXiv:2411.07098]; LlamaRestTest: 12 [P, FSE'25, 10.1145/3715737]; LogiAgent: 12
[P, arXiv:2503.15079]). Error-message work pushed to **16** (EmRest [P, ISSTA'25, 10.1145/3728964]). White-box work
uses the **EMB corpus (~14–19 REST APIs**, grown from 5 in 2017) [S, github.com/aster-test-generation/EMB; "EMB: A
Curated Corpus…"]. Oracle papers use **7–20 industrial APIs / 11–25 operations** (AGORA 7/11; AGORA+ 20/25; SATORI
12/17). **Microservice-specific SUTs** expected: TrainTicket and/or DeathStarBench (and EMB's enterprise apps).
The standardization push that set this bar is **"No Time to Rest Yet"** [P, ISSTA'22, 10.1145/3533767.3534401] (10
tools on a common benchmark), now reinforced by **WFC/WFD** [P, arXiv:2509.01612] and the **public REST benchmark**
(Decrop et al., 2025).

**(b) Baselines: EvoMaster is mandatory; ≥3 SOTA tools total.** Every serious paper compares against **EvoMaster**
(black-box and/or white-box) [P, IEEE SW'21; ASE-J'24, 10.1007/s10515-024-00478-1] plus a selection of
{**RESTler, Morest, RestTestGen, Schemathesis, RESTest, ARAT-RL**}. Since 2024 it is also expected to beat the
**LLM-enhanced** baselines: **RESTGPT-enhanced** variants (AutoRestTest, LlamaRestTest both do this), and for
2025+ papers the **LLM tools themselves** (RESTifAI compares to AutoRestTest *and* LogiAgent). A microservice
trace tool will additionally be expected to position against **Microusity** and **Tracetest** (see §4).

**(c) Bug-count expectations: two acceptable currencies.**
 - *Volume of unique 500-triggering faults:* EmRest **226** unique bugs others miss [P]; Morest **44 (13 unique)**
 [P]; AutoRestTest highlights uniquely triggering a 500 in **Spotify** [P]. Tens-to-low-hundreds is normal.
 - *Developer-confirmed / fixed bugs in real public APIs:* AGORA **11** [P]; **AGORA+ 32** [P, TOSEM'25]; SATORI
 **18 → doc fixes** [P]; RESTler **28 in GitLab** [P]. Confirmed bugs are smaller-N but weigh more.
 A strong submission ideally reports **both** a coverage/operation metric **and** a fault metric, with at least a
 handful of **developer-confirmed** issues (GitHub issue links / fixes), because reviewers now discount raw 500
 counts as duplicates/known-flaky.

**(d) Ablations are expected.** Each component must be shown to contribute: AutoRestTest ablates the MARL agent set
[P]; LlamaRestTest ablates fine-tuning vs base model [P]; EmRest ablates the 4xx-mining vs 5xx-focusing components
[P]. For MIST this means: ablate (i) trace as *generation* signal vs none, (ii) the trace-shape oracle vs a plain
500 oracle, (iii) cross-service negative-input generation vs generic fuzzing — and report the *incremental* bugs
the trace oracle catches that a 500-only oracle misses (this is the single most important number for MIST).

**(e) Coverage when source is available:** line/branch + **operation coverage** (JaCoCo-style) is standard for
white-box / instrumentable SUTs; black-box-only SUTs report operation/parameter coverage and fault counts.

---

## 3) Open Gaps (ranked by defensibility for a trace-driven microservice tester)

Sources: the **TOSEM'23 survey** [P, 10.1145/3617175, arXiv:2212.14604] §6.1.2 and §8 (RQ11/RQ12, Tables 10–11,
read directly from full text); the **JSS'24 microservice-testing mapping study** [S, 10.1016/j.jss.2024.112232];
the **RCA survey** [S, CSUR, 10.1145/3736755]; and recent papers' future-work/threats sections.

> **Survey ground-truth quotes (TOSEM'23):**
> - §6.1.2 Fault Detection: *"The status code 5xx has been applied to identify potential faults in REST API testing"*
>   — confirming 5xx is **the** dominant fault signal.
> - §8.1 Oracle problem (addressed by only 5/92 papers): *"Fuzzers can identify faults based on 500 HTTP status code,
>   and mismatches of the responses with the given API schema. Research has been carried out to define further
>   automated oracles to be able to detect more faults."*
> - §8.2 **Open** challenges (Table 11): **Automated oracles (4 papers)** — *"specific properties … could be used as
>   Automated oracles to be able to find more faults"*; **Classifying test results (4)** — *"automatically check if
>   the obtained responses represent actual faults, and classify their importance/criticality"*; **Handling external
>   services (4)** — a REST API relying on other REST APIs makes testing hard/flaky; **Instance identification (2)** —
>   microservice testers *"might not know which concrete instance of service is being invoked."*
> - **Notably absent from the entire survey:** any "trace-based," "span-level," or "downstream-failure" oracle. The
>   distributed *execution* of a microservice call graph is **not** treated as a testing signal anywhere in the 92 papers.

**GAP #1 (MOST defensible) — The oracle is blind to *masked / swallowed downstream failures*.**
Mainstream oracles look only at the *client-facing* response (5xx or schema). A request that returns **2xx at the
edge while a deeper span 5xx'd** is, by construction, invisible to every status/schema oracle. The survey lists
"Automated oracles" and "Classifying test results" as explicit open problems; none of the 92 papers asserts on the
cross-service trace. **MIST owns this** *iff* it (a) formalizes it as a label-free invariant over **general OTel
traces** (arbitrary depth), and (b) measures the **incremental** faults caught vs a 500-only oracle.
**Caveat (must clear):** Microusity already surfaces "only sub-responses leak/err" for BFFs — so the claim must be
"first *label-free trace-shape oracle over general distributed traces, rigorously evaluated for fault-finding*," not
"first to ever notice masked backend errors." (See §4, risk #1.)

**GAP #2 (defensible) — REST testing treats the API as a single black box; *system-level microservice* testing is
under-served.** The survey's only microservice-specific items are "Instance identification" and "Handling external
services." The EvoMaster team's **own 2026 vision paper** [P, arXiv:2603.02551] argues existing approaches
"insufficiently address" microservice-level/uncertainty concerns and calls for *system-level* fuzzing — i.e., the
leaders concede this is open. Using the **trace as a generation signal** (which deep paths were exercised / which
services were reached) and as the assertion target is a coherent, under-occupied niche. **Caveat:** MISH [P,
arXiv:2412.03420] already learns microservice behavioral automata from logs as a *generation heuristic*; MIST's
differentiator is the *oracle*, not merely "use system behavior to guide search."

**GAP #3 (defensible) — Input-driven provocation of *downstream* error-handling/resilience bugs.** Resilience today
is tested by **fault injection** (Filibuster SoCC'21 [P]; FastFI arXiv'26 [P]) — injecting dependency failures, which
requires harness control and tests the *caller's* tolerance. Negative-input REST testing (RestTestGen, EmRest,
RESTifAI) sends malformed inputs but observes only the *edge* response. The gap: drive *one well-formed-but-adversarial
input per variant* through the front door and catch the failure **wherever in the call graph it manifests** — bridging
black-box input generation with resilience observation, **without** fault-injection instrumentation. **Caveat:**
overlaps conceptually with metamorphic/logical work (LogiAgent, 2605.28321) at the oracle end.

*Honorable-mention gaps (less defensible to "own"):* security oracles beyond 500 (10 papers want this, but it's a
crowded lane: BACFuzz, BACScan, mass-assignment); flakiness/uncertainty handling in microservice fuzzing (arXiv
2603.28452, 2603.02551 — actively being claimed by the EvoMaster group); authentication-gated coverage.

**Recommended framing:** lead with **#1** (the oracle), support with **#2** (system-level signal) and **#3** (input-
driven provocation) as the mechanism. The defensible thesis sentence: *"Across 92 surveyed papers and the 2024–2026
SOTA, the REST-testing oracle is the edge status code; no tool asserts on the distributed trace to catch failures the
edge hides — and the one adjacent tool (Microusity) is BFF-only, port-mapping-based, and evaluated only by a user study."*

---

## 4) Novelty-Risk Watchlist (threats that could pre-empt or narrow MIST)

Severity = how much it narrows MIST's novelty claim.

### 🔴 RISK #1 — Microusity (ICPC'23). Severity: HIGH. **This is the counter-citation MIST must defeat head-on.**
*What it does (from full text, arXiv:2302.11150):* fuzzes a **BFF** edge with **RESTler**, uses **Zeek** to capture
network traffic, and via **port mapping (Algorithm 1)** correlates the main request to the BFF with the sub-requests
the BFF issues to backends. Its **error report Category 3** is *"the sequence that **only the sub-responses from
back-end microservices contain exception leakage**"* — i.e., it **does** flag backend errors not reflected at the
edge. Oracle = **HTTP 5xx + exception-string leakage** in bodies, framed as a **security** (info-leak) concern.
*Evaluation:* **qualitative user study, 8 practitioners**, usability Likert 4.1–4.5 — **no SUT-scale run, no bug
counts, no baseline comparison, no fault-detection measurement.**
*Why it narrows MIST:* MIST **cannot** claim to be the first to surface backend errors hidden behind an aggregating
edge. *Defensible differentiators (state all of them):* (1) **general OpenTelemetry/Jaeger distributed traces** with
real span-context propagation vs **BFF-only port-mapping over a chronological network log** (Microusity breaks on
arbitrary depth, async, fan-out, and non-BFF topologies); (2) a **formal, label-free trace-shape invariant**
(`HiddenDownstreamFailure`: edge-2xx ∧ deep-span-5xx) vs an **ad-hoc 5xx+regex-exception-leakage** heuristic; (3)
**arbitrary call-graph depth** ("a *deeper* span") vs **one-hop** BFF→backend; (4) **cross-service negative-input
generation** vs generic RESTler fuzzing at the edge; (5) a **rigorous fault-finding evaluation** (bugs, baselines)
vs a usability interview. **Action:** cite Microusity prominently, reproduce/compare if feasible, and reframe the
headline as above.

### 🟠 RISK #2 — Tracetest (CNCF/Kubeshop). Severity: MEDIUM-HIGH. **Prior art for "trace-level oracle for API testing."**
Trace-based testing on **OpenTelemetry** spans is an established *industrial* paradigm: you trigger a request and
write **assertions against any span** (e.g., a DB span < 100ms, "service X emitted a span"). So "assert on spans" is
**not novel per se**. *Differentiators MIST must assert:* Tracetest requires **manually authored per-span assertions
and a manual trigger** — it is neither a **test generator** nor a **label-free/automated** oracle; there is no
invariant that fires *without* a human writing the assertion. MIST = automated generation + **label-free** invariant
(no per-span spec). **Action:** cite Tracetest (and the OpenTelemetry-demo trace-testing blog) as the manual-assertion
baseline that MIST automates.

### 🟠 RISK #3 — Automated oracle *learning/inference* beyond 500: AGORA+ (TOSEM'25), SATORI (ASE'25), LogiAgent ('25), RBCTest ('25). Severity: MEDIUM.
The "richer oracle" lane is **active and credentialed**. **AGORA+** [P, TOSEM'25, 10.1145/3726524] (with **Michael D.
Ernst**, Daikon's author) learns **106 invariant types** and found **32 dev-confirmed bugs**; **SATORI** [P, ASE'25]
infers oracles statically from spec via LLM; **LogiAgent** [P] uses an LLM **business-logic** oracle (139 bugs);
**RBCTest** [P] mines response-body constraints. *Why it's only MEDIUM:* **all operate on single-API request/response
fields or business logic — none on the cross-service trace/span structure.** MIST's "trace shape" is **orthogonal** to
"response invariant." *Risk to watch:* a follow-up that points Daikon/AGORA-style invariant mining **at span
attributes across a trace** would directly collide with MIST — this is the most likely near-future pre-emptor.

### 🟡 RISK #4 — MISH (arXiv'24, 2412.03420). Severity: LOW-MEDIUM. **Learns microservice behavior from telemetry — but as a fitness function, not an oracle.**
Real-time **automaton learning from microservice log streams** (Drain3 + FlexFringe) used as an EvoMaster **search
heuristic**; +42% coverage / +76% 500-detection vs MOSA on 6 microservice benchmarks. It is **generation**, not
**assertion** — it has **no trace-shape oracle** and still detects faults via 500s. *Differentiator:* MIST's
contribution is the **oracle**; if MIST also uses traces to *guide generation*, MISH is the closest prior on that
sub-claim (use logs/automata, MIST uses traces) — so scope the generation claim carefully.

### 🟡 RISK #5 — "Silent-failure" security fuzzers: BACFuzz (arXiv'25), BACScan (CCS'25). Severity: LOW.
BACFuzz explicitly frames **Broken Access Control as "silent"** (no crash/error → needs a non-status oracle) and
solves it with a **SQL-query oracle** on **PHP** apps. Shares MIST's *thesis* ("important failures don't show up as
500"), but a **different domain (access-control security), different oracle (SQL taint), different target (monolithic
PHP)**. Cite as independent corroboration that "status-code oracles miss real bugs," not as a competitor.

### 🟡 RISK #6 — System-level microservice fuzzing is being actively claimed (EvoMaster group). Severity: WATCH.
arXiv:2603.02551 (Zhang, Yue, Arcuri, Mar 2026) is a **vision** for uncertainty-driven *system-level* microservice
fuzzing; arXiv:2603.28452 (flakiness in REST fuzzing) and 2604.07073 (log-coverage for REST test strategies) show the
leaders moving toward microservice/observability signals. None yet ships a trace-shape oracle, but the **window is
closing** — MIST should publish/preprint promptly and cite these as concurrent work.

*Lower-priority watch items:* RCAEval RE3 [P] uses "response code in traces" as a fault label (diagnosis, not
testing); "Trace-Discovered Resilience Models on the OTel Demo" (arXiv:2512.12314) discovers resilience models from
traces (modeling, not test generation/oracle).

---

## 5) Reference List (DOI / arXiv-id / URL; [P] primary, [S] secondary)

**Surveys & benchmarks**
1. [P] A. Golmohammadi, M. Zhang, A. Arcuri. "Testing RESTful APIs: A Survey." **TOSEM 33(1):27, 2023.** DOI 10.1145/3617175 · arXiv:2212.14604. *(Read full text; §6.1.2, §8 Tables 10–11.)*
2. [P] M. Kim, Q. Xin, S. Sinha, A. Orso. "Automated Test Generation for REST APIs: No Time to Rest Yet." **ISSTA'22.** DOI 10.1145/3533767.3534401 · arXiv:2204.08348.
3. [S] "Unveiling the microservices testing methods, challenges, solutions, and solution gaps: A systematic mapping study." **JSS 220, 2024.** DOI 10.1016/j.jss.2024.112232.
4. [S] "Intelligent Root Cause Localization in Microservice Systems: A Survey." **ACM Computing Surveys, 2024/25.** DOI 10.1145/3736755.
5. [S] EMB — EvoMaster Benchmark corpus. github.com/aster-test-generation/EMB · "EMB: A Curated Corpus of Web/Enterprise Applications…" (2023).
6. [P] WFC/WFD: "Web Fuzzing Commons…" arXiv:2509.01612 (2025). · [P] A. Decrop et al. "A Public Benchmark of REST APIs" (2025), xdevroey.be/publication/decrop-2025.
7. [S] "Open Problems in Fuzzing RESTful APIs: A Comparison of Tools." arXiv:2205.05325. · [S] "Empirical Comparison of Black-box Test Case Generation Tools for RESTful APIs." arXiv:2108.08196.

**REST test generators**
8. [P] V. Atlidakis, P. Godefroid, M. Polishchuk. "RESTler: Stateful REST API Fuzzing." **ICSE'19**, pp. 748–758. DOI 10.1109/ICSE.2019.00083.
9. [P] E. Viglianisi, M. Dallago, M. Ceccato. "RestTestGen: Automated Black-Box Testing of RESTful APIs." **ICST'20**, pp. 142–152. *(DOI not independently verified here.)* · [P] D. Corradini et al. "Automated black-box testing of nominal and error scenarios in RESTful APIs." **STVR 32(5), 2022.** DOI 10.1002/stvr.1808.
10. [P] A. Martin-Lopez, S. Segura, A. Ruiz-Cortés. "RESTest: Automated Black-Box Testing of RESTful Web APIs." **ISSTA'21** (tool). DOI 10.1145/3460319.3469082. · (short) ICSOC'20, DOI 10.1007/978-3-030-65310-1_33.
11. [P] Y. Liu et al. "Morest: Model-based RESTful API Testing with Execution Feedback." **ICSE'22.** DOI 10.1145/3510003.3510133 · arXiv:2204.12148.
12. [P] A. Arcuri. "Automated Black- and White-Box Testing of RESTful APIs With EvoMaster." **IEEE Software 38(3):72–78, 2021.** (DOI 10.1109/MS.2020.3013820 — verify.) · [P] A. Arcuri et al. "Tool report: EvoMaster…" **Autom. Softw. Eng., 2024.** DOI 10.1007/s10515-024-00478-1.
13. [P] "Deriving Semantics-Aware Fuzzers from Web API Schemas" (Schemathesis). **ICSE'22 (SEIP).** arXiv:2112.10328. · schemathesis.io · github.com/schemathesis/schemathesis.
14. [P] M. Kim, D. Corradini, S. Sinha, A. Orso, M. Pasqua, R. Tzoref-Brill, M. Ceccato. "Enhancing REST API Testing with NLP Techniques (NLPtoREST)." **ISSTA'23.** DOI 10.1145/3597926.3598131.
15. [P] M. Kim et al. "Leveraging LLMs to Improve REST API Testing (RESTGPT)." **ICSE'24 NIER.** DOI 10.1145/3639476.3639769.
16. [P] T. Stennett, M. Kim, et al. "A Multi-Agent Approach for REST API Testing with Semantic Graphs and LLM-Driven Inputs (AutoRestTest)." **ICSE'25.** arXiv:2411.07098. · demo: arXiv:2501.08600. · github.com/selab-gatech/AutoRestTest.
17. [P] M. Kim, S. Sinha, A. Orso. "LlamaRestTest: Effective REST API Testing with Small Language Models." **FSE'25 (PACMSE).** DOI 10.1145/3715737 · arXiv:2501.08598.
18. [P] "Effective REST APIs Testing with Error Message Analysis (EmRest)." **ISSTA'25 (PACMSE).** DOI 10.1145/3728964 · github.com/GIST-NJU/EmRest · Zenodo 15202098.
19. [P] "LogiAgent: Automated Logical Testing for REST Systems with LLM-Based Multi-Agents." arXiv:2503.15079 (2025).
20. [P] L. Kogler et al. "RESTifAI: LLM-Based Workflow for Reusable REST API Testing." **ICSE'26 Demo.** arXiv:2512.08706 · github.com/casablancahotelsoftware/RESTifAI.
21. [P] "MASTEST: A LLM-Based Multi-Agent System For RESTful API Tests." arXiv:2511.18038 (2025).
22. [P] A. Pereira et al. "APITestGenie: Automated API Test Generation through Generative AI." arXiv:2409.03838 (2024).
23. [P] J. Cao et al. "Automated Test-Case Generation for REST APIs Using Model Inference Search Heuristic (MISH)." arXiv:2412.03420 (2024).
24. [P] J. Li et al. "MioHint: LLM-Assisted Request Mutation for Whitebox REST API Testing." arXiv:2504.05738 (2025).
25. [P] "RESTestBench: …LLM-Generated REST API Test Cases from NL Requirements." **EASE'26.** arXiv:2604.25862.
26. [P] "Multi-Agent LLM-based Metamorphic Testing for REST APIs." arXiv:2605.28321 (2026).

**REST oracle generation**
27. [P] J. C. Alonso, S. Segura, et al. "AGORA: Automated Generation of Test Oracles for REST APIs." **ISSTA'23.** DOI 10.1145/3597926.3598114.
28. [P] J. C. Alonso, M. D. Ernst, S. Segura, A. Ruiz-Cortés. "Test Oracle Generation for REST APIs (AGORA+)." **TOSEM, 2025** (Dec 11, 2025). DOI 10.1145/3726524 · RCR report DOI 10.1145/3771281.
29. [P] J. C. Alonso et al. "SATORI: Static Test Oracle Generation for REST APIs." **ASE'25.** arXiv:2508.16318 · IEEE Xplore 11334625.
30. [P] "RBCTest: Leveraging LLMs to Mine and Verify Oracles of API Response Bodies." arXiv:2504.17287 (2025).

**Microservice testing using the distributed execution (MIST's neighbors)**
31. [P] ⚠ P. Rattanukul, C. Makaranond, P. Watanakulcharus, C. Ragkhitwetsagul, T. Nearunchorn, V. Visoottiviseth, M. Choetkiertikul, T. Sunetnanta. "Microusity: A testing tool for Backends for Frontends (BFF) Microservice Systems." **ICPC'23 tool-demo**, pp. 74–78. arXiv:2302.11150 · github.com/MUICT-SERU/MICROUSITY. *(Full text read; uses RESTler+Zeek port-mapping; 5xx+exception-leakage oracle; 8-practitioner user study.)*
32. [P/S] ⚠ Tracetest (Kubeshop/CNCF). tracetest.io · github.com/kubeshop/tracetest · "Trace-based Testing the OpenTelemetry Demo," opentelemetry.io/blog/2023/testing-otel-demo (2023).
33. [P] C. Meiklejohn, A. Estrada, Y. Song, H. Miller, R. Padhye. "Service-Level Fault Injection Testing (Filibuster)." **SoCC'21.** DOI 10.1145/3472883.3487005.
34. [P] "FastFI: Enhancing API Call-Site Robustness in Microservice-Based Systems with Fault Injection." arXiv:2601.14800 (2026).
35. [P] M. Zhang, T. Yue, A. Arcuri. "Fuzzing Microservices in Face of Intrinsic Uncertainties" (vision). arXiv:2603.02551 (2026).
36. [P] "Assessing REST API Test Generation Strategies with Log Coverage." arXiv:2604.07073 (2026). · [P] "Detecting and Mitigating Flakiness in REST API Fuzzing." arXiv:2603.28452 (2026).

**Trace/observability analytics (adjacent)**
37. [P] P. Liu et al. "Unsupervised Detection of Microservice Trace Anomalies through Service-Level Deep Bayesian Networks (TraceAnomaly)." **ISSRE'20.** IEEE Xplore 9251058 · github.com/NetManAIOps/TraceAnomaly.
38. [P] "Unsupervised Anomaly Detection on Microservice Traces through Graph VAE (TraceVAE)." **WWW'23.** DOI 10.1145/3543507.3583215. · [P] "TraceGra: trace-based anomaly detection … graph deep learning." **Computer Communications, 2023**, ScienceDirect S0140366423001135.
39. [P] Z. Li et al. "Practical Root Cause Localization for Microservice Systems via Trace Analysis (TraceRCA)." **IWQoS'21.** DOI 10.1109/IWQOS52092.2021.9521340 · github.com/NetManAIOps/TraceRCA.
40. [P] G. Yu, P. Chen, et al. "Nezha: Interpretable Fine-Grained Root Cause Analysis … Multi-modal Observability." **ESEC/FSE'23.** DOI 10.1145/3611643.3616249 · github.com/IntelligentDDS/Nezha.
41. [P] "RCAEval: A Benchmark for Root Cause Analysis of Microservice Systems with Telemetry Data." **WWW'25.** arXiv:2412.17015 · Zenodo 14590730.
42. [P] Y. Gan et al. "An Open-Source Benchmark Suite for Microservices … (DeathStarBench)." **ASPLOS'19.** DOI 10.1145/3297858.3304013 · github.com/delimitrou/DeathStarBench.
43. [P] "Evaluating Asynchronous Semantics in Trace-Discovered Resilience Models: OTel Demo." arXiv:2512.12314 (2025).

**Security-flavored / silent-failure**
44. [P] "BACFuzz: Exposing the Silence on Broken Access Control Vulnerabilities in Web Applications." arXiv:2507.15984 (2025).
45. [P] "BACScan: Automatic Black-Box Detection of Broken-Access-Control Vulnerabilities." **CCS'25.** DOI 10.1145/3719027.3744825.
46. [P] D. Corradini et al. "Automated Black-box Testing of Mass Assignment Vulnerabilities in RESTful APIs." (2023) arXiv:2301.01261.

**Could NOT verify / flagged**
- **"Lobrest"** — no such REST-testing tool found under this spelling (searches returned only the unrelated "Lob" mailing API). Possibly a typo for LlamaRestTest/EmRest, or an obscure/unindexed tool. **Treat as unverified; please confirm the intended name.**
- Exact peer-reviewed venue for **LogiAgent** and **RBCTest** beyond arXiv was not confirmed (cited as arXiv preprints).
- Exact DOIs for **RestTestGen ICST'20**, **EvoMaster IEEE Software'21**, **TraceAnomaly ISSRE'20**, **RCAEval WWW'25** not independently confirmed (venue/pages or Xplore/arXiv ids given instead).
