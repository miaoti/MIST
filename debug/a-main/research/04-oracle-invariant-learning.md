# 04 — Can "Automatically Learning Label-Free Cross-Service Trace-Shape Invariants as Test Oracles" Headline an A-Conf Paper?

> Analyst note (2026-06-30). Scope: MIST's "Trace Shape Oracle" and its invariant families
> (SpanTreeShape, StatusPropagation, ResponseEnvelope, HiddenDownstreamFailure), assessed as a
> *learning* contribution against (a) Daikon/AGORA-style response invariant mining, (b) unsupervised
> trace-anomaly/RCA, (c) manual trace assertions (Tracetest). Frank and skeptical by request.
> Every nontrivial claim carries a citation; DOIs/URLs verified this session are marked ✓, inferred
> ones are flagged. Primary vs secondary is labelled in §6.

---

## 1. VERDICT

**Supporting pillar — NOT headline-capable as a *learning* contribution.**

The act of *automatically mining invariants from a small known-good corpus and refining them to bound
false positives* is, in 2026, a **standard and recently re-published recipe**, not a novel one:

- **AGORA / AGORA+** already mine likely invariants from request/response corpora and ship them as
  executable REST test oracles, with FP control by falsification on observed runs, 106 invariant
  types, and 32 real bugs found [A1, A2].
- **MINES (ICSE'26)** already does *label-free* Web-API **invariant inference** trained *only on
  normal (known-good) logs*, with an explicit candidate-generate-then-refine loop that discards
  invariants causing false positives on the good corpus — the exact FP-control mechanism MIST would
  claim [N1].
- **TraceAnomaly / DeepTraLog / TraceVAE** already *learn the normal shape of cross-service traces*
  far more powerfully than MIST's "simple known-good baseline," reaching P/R ≈ 0.93–0.97 [B1, B2, B3].

MIST's learner is therefore **dominated on both flanks**: weaker than (b) as a learner of trace
normality, and offering nothing mechanically new over (a)/MINES as an invariant miner. A skeptical PC
will read "learn invariants from a good corpus, threshold the FPs" as **Daikon-on-spans plus an
anomaly model in disguise**, and that reading is essentially correct for the mechanism.

**What MIST *can* legitimately own** is not "learning" but **framing + a fault class**: the *first
label-free, explainable, cross-service **structural/causal** trace invariants used as **test oracles
inside a black-box REST generator**, targeting the **silent / hidden-downstream-failure** class that
value-level oracles (no response breadcrumb) and operational anomaly detectors (not input-bound, not
explainable, threshold-based) both miss.* That is a **detection + framing + empirical** headline, with
*learned invariants as the engine* — exactly the "supporting pillar" role. This is consistent with the
project's own 2026-06-01 verdict that novelty must rest on study/framing, not method novelty
(`debug/a-main/archive-2026-06-01/VERDICT-2026-06-01.md`).

**Could it *ever* headline?** Only if the *learning itself* becomes the novelty — e.g. a new
cross-service **causal/temporal/value-flow invariant language** with a **formal sample-complexity /
PAC-style bound** on the corpus size needed to guarantee FP-rate ≤ α, evaluated to beat *both*
"AGORA+-on-span-attributes" *and* "anomaly-detector-as-oracle" on the same SUTs. That is a real but
**tall** research program, and (critically) the one genuinely under-claimed invariant class
(cross-span *value* invariants) is the **least feasible** black-box/trace-only (see §5). So: in
principle promotable, in MIST's current trace-only/small-corpus reality, a pillar.

---

## 2. PRIOR-ART CHARACTERIZATION

### 2.1 Dynamic invariant detection & value-level oracle mining (group **a**)

- **Daikon** [D1] — generate-and-check dynamic detection of *likely* invariants over observed values
  at program points (constants, ranges, linear/ordering relations). Foundational; the template +
  falsification + confidence machinery every successor reuses. *Per-point, value-level, white-box
  instrumentation.* PRIMARY/foundational.
- **AGORA** (ISSTA'23, Distinguished Artifact) [A1] — lifts Daikon to **REST responses**: a `Beet`
  front-end turns an OpenAPI spec + request/response logs into Daikon point/trace files; new
  invariant types for API outputs; emits oracles. *Per-operation, response/value-level, black-box.*
- **AGORA+ / "Test Oracle Generation for REST APIs"** (TOSEM'25) [A2] — extension: **106 invariant
  types**, `PostmanAssertify` to executable JS assertions, **80% precision** on 25 ops / 20 industrial
  APIs, detects **48%** of seeded output errors, **32 real bugs** (Amadeus, GitHub, YouTube, …). RCR
  report [A2b]. *Still single-response; no call-tree / causal / cross-service reasoning.* PRIMARY —
  **the work to beat for category (a).**
- **RBCTest** (arXiv'25, updated Dec'25) [A3] — LLM mines **response-body** constraints *statically
  from the spec* (Observation-Confirmation prompting), 85–93% precision. *Body/value-level, single
  endpoint.* PRIMARY (preprint).

### 2.2 Unsupervised trace-anomaly / RCA (group **b**) — operational, not oracles

- **TraceAnomaly** (ISSRE'20) [B1] — service-level **deep Bayesian network with posterior flows**
  learns normal trace patterns offline; online anomaly score per trace; P/R > 0.97 in production on
  18 services. *Unsupervised, opaque score, operational monitoring — not bound to a test input, not a
  spec, not explainable.*
- **DeepTraLog** (ICSE'22) [B2] — unified **trace+log graph**, GGNN + deep SVDD one-class model;
  P 0.93 / R 0.97 on a microservice benchmark. *Combined trace/log normality model; operational.*
- **TraceVAE** (WWW'23) [B3] — dual-variable **graph VAE** for unsupervised trace anomaly detection.
  *Stronger structural learner than MIST; operational.*
- **Nezha** (ESEC/FSE'23) [B4] — multimodal (log/metric/trace) RCA via event-pattern *frequent-item*
  mining of pre/post-anomaly differences; interpretable *root cause*, still **RCA not oracle**.
- **TraceRCA** (IWQoS'21) [B5] — anomalous-trace detection + fp-growth frequent-itemset blame
  ranking. *RCA; secondary, not independently re-verified this session.*

Shared trait of (b): they **learn normality and emit an anomaly/root-cause signal for operations**.
None is a *test oracle*: no per-input PASS/FAIL bound to a generated request, no human-readable
invariant, no FP *budget* the tester sets, no integration into test generation.

### 2.3 Manual trace assertions (group **c**)

- **Tracetest** (Kubeshop, OSS) [C1] — trigger a request, wait for the OTel trace, run **human-authored**
  span-selector + assertion checks (attribute/status/timing/ordering, wildcard span checks). *No
  learning; the engineer writes every selector and check.* SECONDARY (tool/docs, not peer-reviewed).
- Adjacent OSS/blog practice: Malabi, OTel-Demo trace-based testing [C2]. SECONDARY.

### 2.4 Spec / temporal-property mining & oracle theory (context)

- **Texada** (ASE'15) [S1] — general **LTL** specification mining from traces against user LTL
  *templates*; arbitrary-length temporal properties. *Temporal spec mining over event logs; not REST,
  not an oracle, requires templates.*
- **The Oracle Problem in Software Testing: A Survey** (TSE'15) [S2] — the canonical taxonomy
  (specified / derived / implicit / no oracle); frames where "learned invariants as oracles" sits
  (derived oracle). SECONDARY/survey. Newer landscape: *Assertions in Software Testing* survey
  (STTT'25) [S3]. SECONDARY.
- **Verification of Microservices Using Metamorphic Testing** (~2019–20) [S4] — attacks the
  microservice oracle problem with **metamorphic relations** (no learned invariants). SECONDARY;
  venue uncertain (Macquarie/Deakin record).

### 2.5 The single closest near-prior-art

- **MINES — "Explainable Anomaly Detection through Web API Invariant Inference"** (ICSE'26) [N1].
  Infers **explainable executable invariants** (FK / not-null / equality / check constraints) from DB
  schema + API signatures; **trains only on normal (known-good) logs**; LLM generates candidates then
  **iteratively refines against the good corpus, discarding any invariant that fires on it** (claims
  ~100% precision); single web app + DB binary logs. **But: not distributed traces, not cross-service
  trace-shape, and explicitly *operational anomaly detection*, not a test oracle.** This is the paper
  that most threatens any "label-free invariant inference + FP-by-known-good-refinement" *learning*
  claim — yet leaves the **cross-service trace-shape + test-oracle** cell open. PRIMARY (accepted/
  preprint).

---

## 3. NOVELTY-DELTA TABLE

Legend: ✅ MIST genuinely differs / unclaimed by them · ⚠️ partial / contestable · ❌ they already
own it (no delta).

| Dimension | vs (a) AGORA+ / Daikon-on-spans / RBCTest | vs (b) TraceAnomaly / DeepTraLog / TraceVAE / Nezha | vs (c) Tracetest |
|---|---|---|---|
| **Artifact = cross-service call TREE** (span topology, depth, hidden downstream spans) | ✅ they reason over a *single response*, never the call tree | ❌ they model the trace graph too (often better) | ⚠️ Tracetest *can* select cross-service spans, but only if a human writes the selector |
| **Invariant family: structural span-tree shape** | ✅ unclaimed | ❌ graph normality is their core | ⚠️ expressible manually, not learned |
| **Invariant family: per-depth status propagation** | ✅ unclaimed | ⚠️ subsumed by anomaly score, but not as an explicit rule | ⚠️ manual |
| **Invariant family: cross-span VALUE invariants** (value from svc A constrained in svc B's span) | ✅ **the one genuinely open cell** — AGORA+ is intra-response only | ✅ anomaly models don't emit human-readable cross-service value rules | ✅ manual & rarely written | 
| **Mining MECHANISM** (generate templates from known-good corpus, falsify) | ❌ identical recipe to AGORA+/Daikon | ⚠️ different (rules vs deep model) but not *better* | n/a (no learning) |
| **FP control = refine/threshold on known-good** | ❌ AGORA+ falsifies; **MINES does exactly this** [N1] | ⚠️ they threshold an anomaly score; MIST thresholds per-family | n/a |
| **Label-free (no fault labels)** | ❌ AGORA+ & MINES also label-free | ❌ unsupervised = label-free too | ✅ vs human labels-of-effort |
| **Used as a TEST ORACLE** (per-input PASS/FAIL, bound to a *generated* request) | ❌ AGORA+ is exactly this | ✅ **anomaly detector ≠ oracle** (not input-bound, threshold, opaque) | ❌ Tracetest is an oracle (manual) |
| **Inside a black-box REST GENERATOR** (gen + execute + assert loop) | ✅ AGORA+ assumes you bring the requests | ✅ they run on production traffic | ✅ Tracetest assumes you bring the test |
| **Explainable / human-readable invariant** | ❌ AGORA+ & MINES also explainable | ✅ vs opaque embeddings/scores | ❌ already explainable |
| **Target fault class: silent / hidden-downstream / masked error** | ✅ value oracle blind when body has no breadcrumb | ⚠️ they'd flag it operationally, not as a test failure | ⚠️ only if a human anticipated it |

**Reading the table.** Almost every ✅ is in the **artifact / framing / use / fault-class** rows.
**Every "learning-mechanism" row is ❌ or ⚠️.** The only learning-substantive ✅ — *cross-span value
invariants* — is the cell that is hardest to realize black-box/trace-only (§5). Net: **the
learning is not the delta; the trace-shape oracle *framing* and the *fault class* are.**

---

## 4. REQUIRED EVALUATION (to convince a skeptical PC the learner ≠ Daikon-on-spans ≠ anomaly detector)

Even to make this a *credible pillar*, MIST must run the comparisons it currently lacks:

1. **Per-family precision/recall.**
   - *Specificity / FP rate*: fire each invariant family on **held-out known-good** runs; report the
     false-positive rate and show it honors a **set FP budget α** (this is the "controlled FP" claim —
     must be a curve, not an anecdote).
   - *Sensitivity*: detection rate on a **fault benchmark** (seeded + real-outage faults across ≥3–4
     real SUTs; MIST has Bookinfo HTTP + Online Boutique gRPC; add DeathStarBench / TeaStore /
     Sock-Shop / TrainTicket).
2. **Mandatory baselines** (the skeptic's checklist):
   - **B-AGORA**: AGORA+/Daikon **lifted onto span attributes** (mine response-level invariants per
     span). If this catches the same faults, MIST's "trace-shape" adds nothing. *Must show a fault
     class B-AGORA structurally cannot express.* [A2, D1]
   - **B-Anomaly**: TraceAnomaly / TraceVAE **thresholded as an oracle** on the same traces. Show MIST
     wins on **explainability + FP control + input-binding**, and ideally on precision at matched
     recall. [B1, B3]
   - **B-Status**: status-code-only oracle (the trivial baseline) — quantify *how many faults
     status-only misses* that trace-shape catches (this is the existence proof from the project's own
     Online Boutique gRPC case).
   - **B-Manual**: Tracetest with human-written assertions — contrast **effort & generalization**
     (MIST learns N invariants per root API automatically vs hand-authored selectors). [C1]
   - **B-MINES-style**: a known-good-refined invariant set without trace structure — isolates whether
     *structure*, not *refinement*, is doing the work. [N1]
3. **Ablations:** (i) **corpus-size → FP-rate** curve (substantiate "small corpus"); (ii) leave-one-
   family-out detection contribution; (iii) with/without the cross-span value family (does the one
   novel family pull its weight?).
4. **The decisive experiment:** a clearly-delimited fault class — *silent acceptance / hidden
   downstream / masked 5xx behind a 2xx root* — where **structural trace invariants detect and ALL of
   B-AGORA, B-Status, and a body-reading oracle MISS**, quantified across SUTs. This is the only result
   that turns "trace-shape" from a framing claim into evidence.

Absent baselines B-AGORA and B-Anomaly, the contribution is **un-evaluable as a learning claim** — a
PC will assume the simplest explanation (Daikon-on-spans) and reject.

---

## 5. FEASIBILITY FOR MIST + KILLER OBJECTION

**Feasible, but feasible *because* trivial.** The *structural* families (span-tree shape,
status-propagation, hidden-downstream) are cheap, deterministic, explainable, and already implemented —
black-box trace-only with a small corpus is no obstacle. That is exactly why they read as "trivial
mechanism" (the project's own verdict: a ~40-line structural check). The **novelty and the feasibility
are inversely correlated**: the one genuinely under-claimed family — **cross-span value invariants** —
is the **least feasible black-box/trace-only**, because OTel/Jaeger spans seldom carry the response
*values* needed to relate svc A's output to svc B's input. (MIST's own attribution probe found
`TARGET_REJECTION=0`: value breadcrumbs are missing from traces;
`debug/a-main/archive-2026-06-01/probe-attribution.md`.) So MIST can cheaply build the *non-novel*
invariants and struggles to build the *novel* one.

Two further integrity constraints from the codebase: **ResponseEnvelope is effectively a no-op**
(empty failure set; LLM path is a TODO) — it cannot be cited as a *learned soft-error* invariant; and
**TimingEnvelope is default-off** — temporal invariants are not actually exercised. A learning headline
that leans on these would be misrepresenting the artifact.

**Strongest reviewer objection (the killer):**
> "Strip the framing: your contribution is *learn likely properties from a known-good corpus and
> discard the ones that fire on it.* AGORA+ (TOSEM'25) and **MINES (ICSE'26)** already published that
> exact loop — MINES even on label-free Web-API invariants with known-good refinement — and
> TraceAnomaly/DeepTraLog learn cross-service trace normality *better* than your baseline. Your
> structural invariants are hand-designed rules with a corpus-derived threshold, i.e. Daikon-on-spans
> for discovery and an anomaly model for normality, recombined. **Show me one thing your *learner*
> does that AGORA+-on-span-attributes and a thresholded trace-anomaly detector cannot — on the same
> SUTs, with precision/recall — or this is engineering, not a learning contribution.**"

MIST cannot answer this today: its learner *is* a "simple known-good-corpus baseline," weaker than the
neighbors on the learning axis. It can only answer the *adjacent* question — "what fault class do
structural trace oracles catch that value oracles and status-only miss?" — which is a **detection /
empirical** answer, confirming the pillar (not headline) placement.

---

## 6. REFERENCES

Primary = method/tool paper making the contribution; Secondary = survey/tool-doc/RCA-context.
✓ = URL/DOI seen in this session's search; (inf) = DOI inferred from venue scheme, not re-verified.

**(a) Invariant / response-oracle mining — PRIMARY**
- [D1] M. D. Ernst, J. H. Perkins, P. J. Guo, S. McCamant, C. Pacheco, M. S. Tschantz, C. Xiao,
  "The Daikon system for dynamic detection of likely invariants," *Science of Computer Programming*
  69(1–3):35–45, 2007. DOI 10.1016/j.scico.2007.01.015 ✓ — https://dl.acm.org/doi/10.1016/j.scico.2007.01.015
- [A1] J. C. Alonso, S. Segura, A. Ruiz-Cortés, "AGORA: Automated Generation of Test Oracles for REST
  APIs," *ISSTA 2023*. DOI 10.1145/3597926.3598114 ✓ — https://dl.acm.org/doi/10.1145/3597926.3598114
  (Distinguished Artifact Award; Beet instrumenter, https://github.com/isa-group/Beet)
- [A2] J. C. Alonso, S. Segura, A. Ruiz-Cortés et al., "Test Oracle Generation for REST APIs"
  (AGORA+), *ACM TOSEM*, 2025. DOI 10.1145/3726524 ✓ — https://dl.acm.org/doi/10.1145/3726524
  (PDF: https://personales.us.es/sergiosegura/files/papers/alonso25-tosem.pdf). 106 invariant types,
  80% precision, 32 real bugs.
- [A2b] RCR report for [A2], *ACM TOSEM*, DOI 10.1145/3771281 ✓ — https://dl.acm.org/doi/10.1145/3771281
- [A3] (Authors per arXiv) "RBCTest: Leveraging LLMs to Mine and Verify Oracles of API Response Bodies
  for RESTful API Testing," arXiv:2504.17287, 2025 (rev. Dec 2025) ✓ — https://arxiv.org/abs/2504.17287

**(b) Trace anomaly / RCA — PRIMARY (operational, not oracles)**
- [B1] P. Liu, H. Xu, et al., "Unsupervised Detection of Microservice Trace Anomalies through
  Service-Level Deep Bayesian Networks," *ISSRE 2020* ✓ — https://ieeexplore.ieee.org/document/9251058
  (DOI 10.1109/ISSRE5003.2020.00014 (inf); code https://github.com/NetManAIOps/TraceAnomaly)
- [B2] C. Zhang, X. Peng, et al., "DeepTraLog: Trace-Log Combined Microservice Anomaly Detection
  through Graph-based Deep Learning," *ICSE 2022* ✓ — https://ieeexplore.ieee.org/document/9793918
  (DOI 10.1145/3510003.3510180 (inf); PDF https://cspengxin.github.io/publications/icse22-DeepTraLog.pdf)
- [B3] Z. Xie et al., "Unsupervised Anomaly Detection on Microservice Traces through Graph VAE"
  (TraceVAE), *WWW 2023*. DOI 10.1145/3543507.3583215 ✓ — https://dl.acm.org/doi/10.1145/3543507.3583215
- [B4] G. Yu, P. Chen, et al., "Nezha: Interpretable Fine-Grained Root Causes Analysis for
  Microservices on Multi-modal Observability Data," *ESEC/FSE 2023*. DOI 10.1145/3611643.3616249 ✓ —
  https://dl.acm.org/doi/10.1145/3611643.3616249 (code https://github.com/IntelligentDDS/Nezha)
- [B5] Z. Li et al., "Practical Root Cause Localization for Microservice Systems via Trace Analysis"
  (TraceRCA), *IEEE/ACM IWQoS 2021*. SECONDARY — cited from prior knowledge, not re-verified this
  session; treat venue/DOI as approximate.

**(c) Manual trace assertions — SECONDARY (tools)**
- [C1] Tracetest (Kubeshop), trace-based testing with OpenTelemetry — https://github.com/kubeshop/tracetest ✓ ,
  docs https://docs.tracetest.io/ ✓ (span-selector language + manual assertions). Not peer-reviewed.
- [C2] OpenTelemetry Demo trace-based testing / Malabi — https://opentelemetry.io/blog/2023/testing-otel-demo/ ✓
  SECONDARY (blog/OSS).

**Closest near-prior-art — PRIMARY**
- [N1] W. Zhang, Y. Lin, K. C. F. Amos, X. Teoh, X. Xie, F. Liauw, H. Zhang, J. S. Dong,
  "MINES: Explainable Anomaly Detection through Web API Invariant Inference," *ICSE 2026*,
  arXiv:2512.06906 ✓ — https://arxiv.org/html/2512.06906 (label-free invariant inference, known-good
  refinement FP control; DB-constraint invariants; single app + DB logs; **not traces, not an oracle**).

**Spec mining / oracle theory — context**
- [S1] C. Lemieux, D. Park, I. Beschastnikh, "General LTL Specification Mining," *ASE 2015*
  ✓ — https://www.cs.ubc.ca/~bestchai/papers/texada-ase15_final.pdf (DOI 10.1109/ASE.2015.71 (inf);
  Texada, https://github.com/ModelInference/texada). PRIMARY.
- [S2] E. T. Barr, M. Harman, P. McMinn, M. Shahbaz, S. Yoo, "The Oracle Problem in Software Testing:
  A Survey," *IEEE TSE* 41(5):507–525, 2015. DOI 10.1109/TSE.2014.2372785 ✓. SECONDARY/survey.
- [S3] "Assertions in software testing: survey, landscape, and trends," *STTT*, 2025 ✓ —
  https://link.springer.com/article/10.1007/s10009-025-00794-1 . SECONDARY/survey.
- [S4] "Verification of Microservices Using Metamorphic Testing," ~2019–2020 (record:
  https://dro.deakin.edu.au/view/DU:30135913 ✓ ; ResearchGate 338743728). SECONDARY; venue uncertain.

**Internal (MIST) cross-checks**
- `debug/a-main/archive-2026-06-01/VERDICT-2026-06-01.md` — prior conclusion: novelty must rest on
  study/framing, not method; ResponseEnvelope is a no-op; TimingEnvelope default-off.
- `debug/a-main/archive-2026-06-01/probe-attribution.md` — `TARGET_REJECTION=0`; trace value
  breadcrumbs missing (bears directly on §5 feasibility of cross-span value invariants).

---

### One-line bottom line
The *learning* of trace-shape invariants is a recombination of published recipes (Daikon/AGORA+ for
discovery, MINES for label-free known-good FP control, TraceAnomaly for trace normality) and **cannot
headline**; it is a sound **supporting pillar** under a *generation/elicitation* or *empirical
prevalence/detectability* headline — where the ownable claim is the **explainable cross-service
structural trace oracle and the silent/hidden-downstream fault class**, not the learner.
