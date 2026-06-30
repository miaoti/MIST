# 03 — Active Elicitation: can MIST move from passive observation to active provocation of swallowed downstream failures?

> Analyst note (2026-06-30). Scope: assess whether an ACTIVE technique (input-driven elicitation and/or fault-injection-driven provocation) gives MIST a citable, novel, achievable METHOD contribution for ICSE/FSE/ASE/ISSTA, on top of its trace-shape oracle `HiddenDownstreamFailure`. Frank and skeptical. Every nontrivial claim is cited; primary vs secondary marked; reconstructed-from-memory DOIs flagged "DOI unverified".
> Binding prior findings respected (do not contradict): the detector is a ~40-line structural check (trivial mechanism); all current masked-failure evidence is OUTAGE-driven, none input-driven; param-level attribution from passive traces is information-limited. See `../archive-2026-06-01/VERDICT-2026-06-01.md`.

---

## 1. Bottom-line recommendation

**The "active provocation + trace oracle for masked failures" method idea is largely PRECLUDED as of mid-2026.** The window that existed when Filibuster (SoCC'21) launched has closed: a 2024–2026 cluster — Filibuster's database extension (ICSE'24), MicroFI (TDSC'24), FastFI (arXiv Jan'26), and especially **Cast (ICSE-SEIP'26)** — already does "inject controlled downstream faults + replay realistic inputs + use distributed traces with assertion points that catch *silent* failures in inner spans." That is framing (ii) almost verbatim. A top-conference reviewer will see active fault-injection resilience testing of microservices as a crowded, mature area.

**The only still-open, defensible sliver is a sharpened oracle, not the provocation mechanism.** Specifically: a **label-free, specification-free, black-box differential oracle for silent STATE/DATA inconsistency** (acknowledged-but-not-persisted writes / missing compensation / lost updates) under tolerated downstream faults — exactly the "Byzantine silent corruption / no test oracle that specifies behavior under failure" gap that the CMU/Filibuster team itself named as open in ICSE'24 [Assad et al. 2024, PRIMARY]. MIST can target this gap without developer assertions (Filibuster/Cast both need them) and without code instrumentation (black-box OTel).

**Recommended framing = combination, with a discovered third option as the load-bearing hook:**
- Use **framing (ii)** (controlled downstream fault injection during replayed/generated requests) only as the cheap, reliable PROVOCATION ENGINE.
- Re-aim the oracle from "2xx-over-5xx span" (trivial, and now Cast-adjacent) to a **differential data-integrity invariant** (third option, §4): a state-mutating request returns 2xx to the client while the trace shows the persisting span errored AND a control/read-back proves state diverged ⇒ silent data loss / missing compensation.
- Present **framing (i)** (input-only elicitation) as a genuinely novel but largely-unachievable open problem and a *secondary* qualitative result, not a pillar.

**Honest verdict on ambition:** even the sharpened oracle is an *increment* over Filibuster/Cast (label-free + black-box + REST + data-integrity-specific), not a leap. It is unlikely to carry an A-conf paper *as a method contribution alone*. It is, however, a credible METHOD HOOK that — combined with the empirical prevalence study the prior VERDICT already recommends — could lift the paper from "engineering" to "new oracle + measurement." One-sentence claim it supports:

> *"MIST is the first black-box, specification-free REST test generator that actively provokes downstream faults and uses the distributed trace as a differential oracle to expose silently-masked cross-service failures — including acknowledged-but-lost writes — that status-code and response-body oracles cannot see."*

If the empirical study cannot produce real (non-synthetic, non-pure-outage) data-integrity findings on real OSS SUTs, **neither framing is strong enough** and the project should fall back to the measurement-leg paper from the prior VERDICT.

---

## 2. Framing (i): INPUT-DRIVEN ELICITATION

### 2.1 Technique sketch
Generate request inputs (no infrastructure fault injected) that drive an UPSTREAM service into the catch/fallback/graceful-degradation branch that swallows a DOWNSTREAM domain/validation/dependency failure; the trace oracle then flags the masked inner error under a 2xx root.

Concrete black-box loop MIST could build:
1. Seed from OpenAPI; generate inputs (boundary/constraint-aware, like RESTest's inter-parameter constraints [Martin-Lopez et al. 2020/2021, PRIMARY]).
2. Execute; pull the OTel/Jaeger trace.
3. Feedback signal = "did any non-root span error (http>=500 / otel=ERROR) while the root stayed 2xx?" Use this as a coverage-guided fitness to evolve inputs toward masking paths (search-based, EvoMaster-style fitness [Arcuri 2019, PRIMARY], but with a *trace-shape* fitness instead of a code-coverage/500-at-boundary fitness).
4. Finding = an input that *reproducibly* yields 2xx-root + errored-inner-span with no infrastructure fault present.

### 2.2 Novelty delta
- vs **EvoMaster/RESTest** [Arcuri 2019; Martin-Lopez et al. 2020/2021, PRIMARY]: they evolve inputs to maximize coverage and flag faults *at the entry boundary* (HTTP 500 returned to the client, schema mismatch). They do NOT reason about *cross-service masking* — a 2xx entry hiding an inner 5xx is, to them, a pass. MIST's trace-shape fitness is new.
- vs **Microusity** [Phupattanasilp et al., ICPC'23 tool demo, PRIMARY]: closest "trace-correlate front request to backend sub-requests to attribute internal 500s" tool, BUT (a) BFF-pattern-specific, port-mapping based (not general OTel topology), (b) it *surfaces/attributes* 500s that reach the BFF; from the available description it does not target the *masked* case where the front returns 2xx while a backend 500 is swallowed, and (c) it is a tool demo with a comprehension/RCA goal, not active input generation. MIST's delta = general topology + the masking case as the explicit target + active elicitation. (Caveat: I could not fully confirm whether Microusity handles the 2xx-masks-500 case; verify against the paper before claiming.)
- vs **error-handling-code testing** [Yuan et al., OSDI'14; LFI, Marinescu & Candea, DSN'09; Xu et al., TDSC'23 — all PRIMARY]: these reach error handlers by INJECTING faults (library/syscall/exception), not by crafting INPUTS that organically trip a cross-service masking branch. No prior art (academic) found that uses *inputs alone* to provoke a *masked cross-service* failure detected by a trace oracle — a targeted search returned only practitioner blog material, no papers. **This is the genuinely novel territory.**

### 2.3 Feasibility (black-box, trace-driven)
**Low, and this is the crux.** To mask a downstream failure via inputs alone, the input must cause a *downstream* service to error in a way the *upstream* catches and converts to 2xx. Three problems:
- **Yield.** Bad inputs are usually rejected by the FIRST service that validates them, which surfaces a 4xx/5xx directly — not masked. To reach a *deep* error that an *intermediate* service swallows, the input must pass upstream validation yet break a downstream invariant. Such inputs exist but are rare and SUT-specific; a black-box generator has no signal for where they are.
- **Controllability.** The masking code path typically lives in the upstream's catch/fallback. A black-box tool cannot target it directly (that would need white-box reachability, violating the premise).
- **Attribution limit (binding finding).** Even on a hit, spans carry service/controller identity, not "which parameter the downstream rejected," so MIST can localize to a service, not a param. This is an information limit, not just a code limit (`probe-attribution.md`).

Net: feasible to *build* the loop; the expected *hit rate is near zero* without infrastructure faults — which is precisely why the project has zero input-driven evidence today. This matches reality, it is not pessimism.

### 2.4 Strongest reviewer objection
*"You have no evidence inputs alone can provoke masking, and information-theoretically a black-box trace can't tell you which input did it. This is a fishing expedition dressed as test generation."* **Largely unanswerable** at the method level; answerable only by demoting it to a secondary qualitative finding ("we found N input-driven cases on real SUTs") if any exist.

---

## 3. Framing (ii): FAULT-INJECTION-DRIVEN

### 3.1 Technique sketch
Replay realistic inputs while systematically injecting controlled DOWNSTREAM faults (latency, HTTP 5xx, abort/timeout, connection reset, resource exhaustion) at service or DB-client boundaries; use the trace oracle to find cases where masking is INCORRECT (wrong 2xx, data loss, silent inconsistency, missing compensation). Decision rule (naive version) = "injected fault tolerated at root (2xx) but inner span shows the fault as an unrecovered error" ⇒ candidate masked failure.

### 3.2 Novelty delta — this is where the area is CROWDED
- **Filibuster / Service-level Fault Injection Testing** [Meiklejohn, Estrada, Song, Miller, Padhye, SoCC'21, DOI 10.1145/3472883.3487005, PRIMARY]. Static analysis + concolic-style execution + dynamic-reduction to enumerate combinatorial fault scenarios; *extends existing functional tests*; **developer asserts correct behavior under each fault**. MIST's only deltas: (a) no developer assertions (trace-shape oracle is label-free), (b) generates inputs instead of needing a functional test suite, (c) standard black-box OTel rather than per-service instrumentation. Real but incremental.
- **Filibuster DB extension** [Assad, Meiklejohn, Miller, Krusche, ICSE-Companion'24, DOI 10.1145/3639478.3640021, PRIMARY]. *Directly targets the data-integrity angle*: injects DB-client faults across Redis/Cassandra/CockroachDB/Postgres/DynamoDB and explicitly raises "**data corruption … a Byzantine fault where the client returns corrupted data**" and "**the lack of a test oracle that specifies behavior under failure**" as open. This both (i) validates the data-integrity direction and (ii) shows CMU already occupies it — but via developer specs + IDE visualization, NOT a label-free oracle. **This paper is simultaneously MIST's best citation and its biggest "you're an increment" risk.**
- **Cast** [Z. Chen, Deng, Zhang, Liu, Cui, Zhong, Zheng, ICSE-SEIP'26, arXiv:2602.00972, PRIMARY]. The most threatening. Injects application-level faults (DB timeouts, serialization errors, inter-service comm errors) + **production-traffic replay** + **distributed-trace reconstruction** + a "multi-faceted oracle" with **granular assertion points at internal endpoints that catch silent failures in asynchronous operations** + phase-based success thresholds. Deployed 8 months in production; 137 vulns, 89 confirmed. This is framing (ii) including the "inner-span silent-failure" idea. MIST deltas vs Cast: (a) Cast needs configured assertion points + phase thresholds (not label-free), (b) Cast uses Java AOP agents + production traffic (not black-box / not generation-based), (c) Cast is SEIP/industry-track. Differentiable, but the conceptual overlap is large and recent.
- **Gremlin** [Heorhiadi, Rajagopalan, Jamjoom, Reiter, Sekar, ICDCS'16, pp.57–66, IEEE Xplore 7536505, DOI unverified, PRIMARY]: manipulates inter-service messages at the network layer; operator writes *resilience assertions* (recipes). Needs specs; not trace-oracle-free.
- **LDFI / Molly** [Alvaro, Rosen, Hellerstein, SIGMOD'15, DOI 10.1145/2723372.2723711, PRIMARY] + **Automating Failure Testing at Internet Scale** [Alvaro et al., SoCC'16, PRIMARY]: reasons *backward from a known-correct outcome* to the minimal fault sets that could break it. Principled fault selection — but needs a notion of "correct outcome." MIST's label-free trace oracle is a *different* (weaker, cheaper) substitute for that oracle.
- **MicroFI** [H. Chen, P. Chen, Yu, Li, He, IEEE TDSC'24, DOI 10.1109/TDSC.2024.3363902, PRIMARY] and **FastFI** [Tan et al., arXiv:2601.14800, Jan'26, PRIMARY]: both optimize *which* faults to inject (PageRank-prioritized request-level injection; DFS combinatorial reduction, 76% time saving). They contribute injection *efficiency*; they assume an external failure signal. MIST contributes the *oracle*, not the injection strategy — complementary, but it means "systematic downstream injection" is solved and unavailable as a novelty claim.
- **ChaosMachine** [L. Zhang, Morin, Haller, Baudry, Monperrus, IEEE TSE'21, arXiv:1805.05246, DOI unverified, PRIMARY]: injects exceptions at try-catch granularity in the JVM and falsifies error-handling hypotheses *in production*. Same spirit (provoke + observe error-handling), white-box JVM, single-process, not REST/cross-service/black-box.
- Infra/secondary: **3MileBeach** (message-level tracer+FI) [NSF PAR 10322128, venue unverified, secondary]; **Box of Pain** (co-evolving tracing & FI) [arXiv:1903.12226, secondary].

**What is NOT yet done (MIST's residual room in framing ii):** a *fully label-free, specification-free, black-box* oracle — no developer assertions (Filibuster/Gremlin), no phase thresholds/assertion points (Cast), no "correct outcome" model (LDFI) — that flags *incorrect masking* purely from trace shape + a self-checking differential. Thin, but real.

### 3.3 Feasibility
**High.** Injecting downstream faults black-box is well-supported: service-mesh fault injection (Istio/Envoy abort+delay), Chaos Mesh/Toxiproxy, or sidecar/proxy interception — no SUT source needed. Replaying realistic inputs MIST can already generate. The trace oracle already exists. The only new build is the differential/read-back checker (§4). This is the *achievable* framing.

### 3.4 Strongest reviewer objection
*"This is Filibuster/Cast/Gremlin with a weaker, label-free oracle. Removing the developer assertion makes you cheaper but also less precise; injecting downstream faults + reading traces for silent failures is exactly Cast (ICSE-SEIP'26). What is the research contribution beyond 'the same thing without specs'?"* **Partially answerable** only if the oracle is sharpened to something those tools demonstrably do NOT do label-free (data-integrity differential, §4) AND backed by an empirical study showing it finds real bugs the assertion-based tools miss in practice (because nobody wrote the assertion). Without that, the objection stands.

---

## 4. Discovered third option (RECOMMENDED hook): label-free DIFFERENTIAL DATA-INTEGRITY oracle

### 4.1 Idea
Keep framing (ii)'s injection engine, but replace the trivial "2xx-over-5xx span" rule with a **metamorphic/differential invariant specialized to state-mutating operations**, which is self-checking and needs no spec:

For a mutating request R (POST/PUT/PATCH/DELETE) on resource X:
1. **Control run** (no fault): execute R, then read-back X (GET) → state S_control; capture trace T_control.
2. **Fault run**: execute R while injecting one controlled downstream fault on the persisting dependency (DB write, downstream write API); read-back X → state S_fault; capture trace T_fault.
3. **Oracle fires** when: client response of the fault run is 2xx/"success" AND T_fault shows the persisting span errored/aborted AND `S_fault != S_control` (or the entity is absent/stale) — i.e., the system *acknowledged* a write it did not durably perform, or skipped a compensation. Symmetric checks catch silent partial writes and missing rollbacks.

Decision is fully label-free: the read-back differential is the ground truth; no developer assertion, no phase threshold, no "correct outcome" model.

### 4.2 Why this is the best novelty × feasibility
- **Novelty vs Filibuster/Cast:** both detect "bad behavior under fault" only via *developer-supplied* assertions (Filibuster) or *configured* assertion points/thresholds (Cast). The ICSE'24 DB paper [Assad et al., PRIMARY] explicitly flags silent data corruption and "no oracle that specifies behavior under failure" as OPEN. A label-free read-back differential is a concrete answer they did not give.
- **Novelty vs EvoMaster/RESTest:** they have no fault injection and no cross-service/state-differential oracle.
- **Feasibility:** entirely black-box. Read-back is a normal GET; injection is mesh/proxy-level; the diff is mechanical. Idempotency/nondeterminism handled by normalizing volatile fields (Cast already shows state-dependent replay is tractable [PRIMARY]).
- **Bug class is recognized and prevalent:** maps to Yuan et al.'s finding that **92%** of catastrophic distributed-system failures stem from incorrect handling of non-fatal errors and **35%** from outright wrong error handlers [OSDI'14, PRIMARY] — strong motivation citation for "masked errors that cause silent inconsistency matter."

### 4.3 Residual objection (be honest)
*"A read-back differential under injected DB faults is close to what a Filibuster-DB test with a state assertion would check; you've automated the assertion, not invented a new analysis."* True. The defense is empirical, not conceptual: show, on multiple real OSS SUTs, real acknowledged-but-lost-write / missing-compensation bugs that the assertion-based tools miss *in practice because no human wrote that assertion*, plus a prevalence number. If that evidence does not materialize, downgrade to the measurement-leg paper.

---

## 5. Reference list

Primary = the actual research artifact; Secondary = survey/blog/figure/derivative. "DOI unverified" = reconstructed from memory; cite via the stable URL given.

### Microservice fault-injection / resilience testing (closest prior art)
1. **[PRIMARY]** C. Meiklejohn, A. Estrada, Y. Song, H. Miller, R. Padhye. "Service-Level Fault Injection Testing" (Filibuster). ACM SoCC 2021. DOI 10.1145/3472883.3487005. https://dl.acm.org/doi/10.1145/3472883.3487005 · PDF https://christophermeiklejohn.com/publications/filibuster-socc-2021.pdf
2. **[PRIMARY]** M. Assad, C. Meiklejohn, H. Miller, S. Krusche. "Can My Microservice Tolerate an Unreliable Database? Resilience Testing with Fault Injection and Visualization." ICSE-Companion 2024. DOI 10.1145/3639478.3640021. https://dl.acm.org/doi/10.1145/3639478.3640021 · arXiv:2404.01886
3. **[PRIMARY]** Z. Chen, Z. Deng, K. Zhang, Y. Liu, C. Cui, J. Zhong, Z. Zheng. "Cast: Automated Resilience Testing for Production Cloud Service Systems." ICSE-SEIP 2026. arXiv:2602.00972. https://arxiv.org/abs/2602.00972
4. **[PRIMARY]** V. Heorhiadi, S. Rajagopalan, H. Jamjoom, M. K. Reiter, V. Sekar. "Gremlin: Systematic Resilience Testing of Microservices." IEEE ICDCS 2016, pp. 57–66. IEEE Xplore doc 7536505 (DOI unverified, likely 10.1109/ICDCS.2016.11). https://ieeexplore.ieee.org/document/7536505/
5. **[PRIMARY]** P. Alvaro, J. Rosen, J. M. Hellerstein. "Lineage-driven Fault Injection" (Molly). ACM SIGMOD 2015. DOI 10.1145/2723372.2723711. https://dl.acm.org/doi/10.1145/2723372.2723711
6. **[PRIMARY/secondary]** P. Alvaro, K. Andrus, C. Sanden, C. Rosenthal, A. Basiri, L. Hochstein. "Automating Failure Testing Research at Internet Scale." ACM SoCC 2016. DOI 10.1145/2987550.2987555 (DOI unverified). Netflix LDFI deployment.
7. **[PRIMARY]** H. Chen, P. Chen, G. Yu, X. Li, Z. He. "MicroFI: Non-Intrusive and Prioritized Request-Level Fault Injection for Microservice Applications." IEEE TDSC 2024. DOI 10.1109/TDSC.2024.3363902. https://ieeexplore.ieee.org/document/10428037/
8. **[PRIMARY, preprint]** Y. Tan, J. Wang, S. Xie, B. Li, Y. Yong, N. Zhang, S. Tan. "FastFI: Enhancing API Call-Site Robustness in Microservice-Based Systems with Fault Injection." arXiv:2601.14800, Jan 2026. https://arxiv.org/abs/2601.14800
9. **[SECONDARY/primary]** "3MileBeach: A Tracer with Teeth" (message-level tracing + fault injection). NSF PAR 10322128 (venue/year unverified). https://par.nsf.gov/biblio/10322128
10. **[SECONDARY]** "Co-evolving Tracing and Fault Injection with Box of Pain." arXiv:1903.12226. https://arxiv.org/pdf/1903.12226

### Error-handling / recovery-code testing (general)
11. **[PRIMARY]** D. Yuan, Y. Luo, X. Zhuang, G. Rodrigues, X. Zhao, Y. Zhang, P. U. Jain, M. Stumm. "Simple Testing Can Prevent Most Critical Failures…" (Aspirator). USENIX OSDI 2014. https://www.usenix.org/conference/osdi14/technical-sessions/presentation/yuan — key stats: 92% of catastrophic failures = mishandled non-fatal errors; 58% preventable by simple testing; 35% = incorrect error handlers.
12. **[PRIMARY]** P. D. Marinescu, G. Candea. "LFI: A Practical and General Library-Level Fault Injector." IEEE/IFIP DSN 2009 (DOI unverified). https://dslab.epfl.ch/pubs/lfi.pdf · follow-up "Efficient Testing of Recovery Code Using Fault Injection," ACM TOCS / ICSE 2011.
13. **[PRIMARY]** (Xu et al.) "Testing Error Handling Code With Software Fault Injection and Error-Coverage-Guided Fuzzing." IEEE TDSC 2023. DOI 10.1109/TDSC.2023.3288876. https://dl.acm.org/doi/10.1109/TDSC.2023.3288876
14. **[PRIMARY]** L. Zhang, B. Morin, P. Haller, B. Baudry, M. Monperrus. "A Chaos Engineering System for Live Analysis and Falsification of Exception-handling in the JVM" (ChaosMachine). IEEE TSE 2021 (DOI unverified). arXiv:1805.05246. https://arxiv.org/abs/1805.05246

### REST API test generation (input side)
15. **[PRIMARY]** A. Arcuri. "RESTful API Automated Test Case Generation with EvoMaster." ACM TOSEM 2019. DOI 10.1145/3293455. https://dl.acm.org/doi/10.1145/3293455
16. **[PRIMARY]** A. Martin-Lopez, S. Segura, A. Ruiz-Cortés. "RESTest: Black-Box Constraint-Based Testing of RESTful Web APIs" (ICSOC 2020) + tool demo "RESTest: Automated Black-Box Testing of RESTful Web APIs" (ISSTA 2021, DOI 10.1145/3460319.3469082). https://dl.acm.org/doi/10.1145/3460319.3469082
17. **[PRIMARY]** S. Segura, J. A. Parejo, J. Troya, A. Ruiz-Cortés. "Metamorphic Testing of RESTful Web APIs." IEEE TSE 2018 (DOI unverified). https://javiertroyauma.github.io/publications/TSE2017_REST_prePrint.pdf
18. **[PRIMARY]** (Phupattanasilp et al.) "Microusity: A Testing Tool for Backends-for-Frontends (BFF) Microservice Systems." IEEE/ACM ICPC 2023, Tool Demo. IEEE Xplore doc 10174084 (DOI unverified). https://ieeexplore.ieee.org/document/10174084/

### Checked and NOT threats (recent, ruled out)
19. **[PRIMARY]** A. C. Ribeiro. "Invariant-Driven Automated Testing." arXiv:2602.23922, Mar 2026 — single-system REST API invariant testing from OAS; no traces, no fault injection, no cross-service masking. Not competing.
20. **[PRIMARY]** A. A. Krasnovsky. "Evaluating Asynchronous Semantics in Trace-Discovered Resilience Models: A Case Study on the OpenTelemetry Demo." arXiv:2512.12314, Dec 2025 — trace-derived model *fidelity* analysis; no test generation, no fault injection. Not competing.

### Secondary / context
21. **[SECONDARY]** A. Golmohammadi, M. Zhang, A. Arcuri. "Testing RESTful APIs: A Survey." arXiv:2212.14604 (TOSEM 2023).
22. **[SECONDARY]** "Feedback-based, Automated Failure Testing of Microservice-based Applications." arXiv:1908.06466 (PDF text not extractable; relevance unconfirmed).
23. **[SECONDARY]** C. Meiklejohn. "Resilient Microservice Applications, by Design, and without the Chaos" (PhD work, 2024). https://christophermeiklejohn.com/publications/cmeiklej_phd_s3d_2024.pdf — the umbrella research program MIST competes against.

---

### Appendix — one-paragraph honest framing for the paper
Position MIST as: *given* that systematic downstream fault injection is solved (Filibuster/MicroFI/FastFI/Cast), MIST's contribution is a **specification-free, black-box differential trace oracle** that turns those injections into automatic detectors of *silently-masked cross-service failures and acknowledged-but-lost state changes* — the open "no oracle for behavior under failure / Byzantine silent corruption" problem named by the Filibuster team themselves — and an empirical study of how prevalent such masking is in real OSS microservices and how much status-code/body oracles miss. The provocation is the vehicle; the label-free differential oracle + the prevalence measurement are the contributions. Be explicit that input-only elicitation (framing i) remains an open, largely-unsolved problem.
