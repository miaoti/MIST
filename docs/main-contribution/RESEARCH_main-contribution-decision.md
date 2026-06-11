# Main-Contribution Decision — research verdict #2 (deep-research, 2026-05-29)

Frank, cited determination of the ONE main line that is **both** (i) genuinely first-of-kind
**and** (ii) clears the real A-conference (ICSE/FSE/ASE/ISSTA) bar. Method: 5-angle fan-out,
22 primary sources, 108 claims, 25 adversarially verified (3-vote) → 22 confirmed / 3 killed.
Raw report: `research-raw-main-line-decision.json`. Supersedes nothing in
`RESEARCH_a-conference-viability.md` (#1) — it sharpens it.

## VERDICT (frank)
The single defensible main contribution is **one integrated SYSTEM**:

> **LLM-guided NEGATIVE-input test generation over multi-step CROSS-SERVICE microservice
> workflows + a LABEL-FREE distributed-trace (OTel/Jaeger) oracle that detects HIDDEN-DOWNSTREAM
> failures (gateway returns 2xx while a downstream span 5xx-errors, response body clean).**

- **Pillar A (primary novelty):** the label-free distributed-trace oracle for hidden-downstream.
- **Pillar B (enabling setting):** cross-service-workflow negative generation.
- **Neither half clears the bar alone.** Cross-service-negative alone = incremental scope change;
  trace-oracle alone = conceptually shadowed by Microusity + TraceAnomaly + Tracetest.
- **The combination is first-of-kind AND A-bar-viable** — *contingent* on the empirical evaluation
  actually delivering multiple SUTs + a non-trivial baseline + a handful of confirmed bugs.

**Key structural fact: both pillars already exist in MIST.** The contribution is the *combination
+ the evaluation*, not new machinery. What still needs design is the oracle's label-free
genuine-fault decision (see Risks). The evaluation is the real work.

## Novelty map (first-of-kind? — cited, verified)
| Candidate | First-of-kind? | Closest prior art / why it doesn't pre-empt |
|---|---|---|
| **Cross-service workflow negative testing** | **Yes (HIGH)** — but a *scope/setting* novelty, attackable as incremental alone | RESTler, Morest, MISH, Lobrest, LlamaRestTest are all **single-service-scoped**; "stateful"/"business-aware" is strictly *within* one service; none inject negative inputs across a cross-service business workflow. |
| **Hidden-downstream-failure via label-free trace oracle** | **Yes (HIGH)** — single strongest pillar | Every verified SOTA oracle is **response/status-level** and structurally blind to a clean 2xx masking a downstream 5xx: RESTler/Morest/Lobrest/LlamaRestTest fire on 5xx status; MISH uses app **logs** not spans; EmRest (ISSTA'25 SOTA) analyzes 400/500 **messages**, no tracing. |
| (threat) **Microusity** (ICPC'23 demo) | Conceptual overlap (MEDIUM) — **reviewer's most likely counter-citation** | Pinpoints which backend caused a 500 behind a BFF = the *exact* hidden-downstream scenario. BUT uses **network port-mapping (Zeek), not distributed traces**; no LLM/cross-service negative gen; **no real-bug eval** (5-page demo + 8-person usability study). |
| (threat) **Tracetest** | Not a competitor to *label-free* | Trace-based testing exists, but assertions are **human-authored** (selector+check). → MIST's gap is doing it **automatically/label-free**, NOT "using traces". |
| (threat) **TraceAnomaly / AIOps** | Not a test oracle | **Operational/unsupervised**; detects timing + invocation-path statistical outliers (mean±3σ / Bayesian); **status/body-agnostic**, no concept of an injected negative input. |

**→ Phrase the claim precisely:** *not* "first to detect hidden-downstream failures" (Microusity
dents that), **but** "first **label-free distributed-trace** oracle for hidden-downstream failures,
fired under **LLM-generated cross-service negative inputs**, with a **real-bug** evaluation."

## The A-bar (concrete, recent anchors)
- **EmRest** (ISSTA'25): **16 SUTs**, 226 unique bugs not found by competitors (higher recent anchor).
- **Morest** (ICSE'22): **6 SUTs**, 44 bugs over a **non-trivial baseline** (EvoMaster-bb/RestTestGen/
  RESTler — all non-zero), **only 2 developer-confirmed-and-fixed**.
- **→ The bar does NOT require a large confirmed-bug count.** Multiple SUTs + a non-trivial baseline +
  a *modest* number of developer-confirmed bugs that response-level SOTA structurally misses clears it.
  (Do **not** present 44/6 as a hard threshold — that was refuted; it's a flexible existence proof.)
- Caveat: "unique bugs" in these papers usually means competitor-differential 500-triggering
  operations, **not** developer-triaged defects — MIST's claim is materially stronger if its bugs are
  genuinely **developer-confirmed**.

## What must be built / evaluated (the path)
1. **≥2–3 microservice SUTs** with real gateway+downstream topology (train-ticket **plus at least one
   more**) — to beat the single-SUT objection. *(Currently the steepest gap — see Risks.)*
2. **A non-trivial baseline:** run EmRest / LlamaRestTest / RESTler / Morest on the **same** SUTs;
   show their response-level oracle returns the hidden-downstream bug as a **clean 2xx** (baseline = 0
   on hidden-downstream *specifically*), while the baseline is **non-zero on other bugs** so the
   comparison is not "0-by-construction".
3. **A modest set of developer-CONFIRMED hidden-downstream bugs** (a handful suffices; Morest cleared
   ICSE with 2).
4. **An ablation** isolating the trace oracle's contribution vs the response-level / soft-error check
   MIST already ships.

## Single strongest reviewer counterargument + rebuttal to build
> *"Your hidden-downstream oracle is just trace-anomaly detection / Microusity applied to testing,
> and a downstream 5xx is a bug regardless of trace — a developer would catch it in logs. What is
> fundamentally new beyond plumbing?"*

Rebuttal we must be able to demonstrate:
- **(a) Label-free** = no human-authored span assertions (vs Tracetest) and no trained normal-pattern
  baseline (vs TraceAnomaly).
- **(b) Closed loop** = the oracle fires under MIST's own **LLM-generated negative cross-service
  inputs** — a generation+oracle pairing no prior tool has.
- **(c) Real developer-CONFIRMED bugs** where gateway=2xx but a downstream span 5xx-errored, which
  RESTler/Morest/EmRest/LlamaRestTest provably cannot flag.

## Risks / contingency (frank)
- **Novelty findings = HIGH confidence, primary-sourced. A-bar viability = MEDIUM, CONTINGENT** on an
  evaluation that does not yet exist.
- **#1 risk — second SUT.** Per project memory, train-ticket is the *single* live SUT and param-level
  attribution is currently TARGET_REJECTION=0. The "≥2–3 SUTs" element is real and **currently unmet**.
- **#2 risk — confirmed bugs.** train-ticket is a research benchmark, not an actively-maintained
  product, so *developer* confirmation is harder than for Morest's Atlassian targets. Likely need a
  maintained microservice system for the confirmed-bug existence proof, plus injected mutants for the
  detection-rate study.
- **#3 risk — the mechanism.** *How* does the label-free oracle decide a downstream 5xx is a genuine
  fault vs an expected/handled error, with no human assertion and no trained baseline? This is the
  core of rebuttal (a) and its design was **not** in research scope — it is the next tool-design task.
- **Microusity (2-1, single-source)** is the load-bearing novelty risk; treat it as the reviewer's
  most likely counter-citation, not a settled non-issue. Re-sweep for 2026 trace-oracle/RESTifAI
  span-level work right before submission.

## Key sources (all primary unless noted)
RESTler (MSR) microsoft.com/en-us/research/publication/restler-stateful-rest-api-fuzzing ·
Morest (ICSE'22) arxiv.org/pdf/2204.12148 · MISH arxiv.org/html/2412.03420v1 ·
Lobrest arxiv.org/pdf/2604.08007 · LlamaRestTest (FSE'25) conf.researchr.org/details/fse-2025/…/51 ·
EmRest (ISSTA'25) dl.acm.org/doi/10.1145/3728964 · Microusity (ICPC'23) arxiv.org/abs/2302.11150 ·
Tracetest docs.tracetest.io/concepts/what-is-trace-based-testing ·
TraceAnomaly (ISSRE'20) nkcs.iops.ai/…/paper-ISSRE20-TraceAnomaly.pdf + github.com/NetManAIOps/TraceAnomaly
