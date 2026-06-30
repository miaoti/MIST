# A-main grounding synthesis (anchor for the new plan)

> Author: orchestrator (pre-research grounding, 2026-06-29). This is the STABLE ANCHOR for the
> synthesis + review phases. It records (1) what MIST is, (2) the binding prior findings the new
> plan may NOT contradict, (3) the candidate directions, and (4) the open questions the 5 research
> agents must answer. The cited, authoritative content lives in `01..05-*.md` (agent outputs);
> this file is the neutral frame. Update the "decision" section only after research returns.

## 1. What MIST is (code-verified; see debug/flow/MIST_FLOW.md)
Black-box REST API test generator for **microservices** that treats the OTel/Jaeger **distributed
trace** as both a generation input and an assertion target. Three shipped features:
- **Root API Mode** — drive only externally-reachable entry endpoints; observe internals via trace.
- **Sniper Strategy** — exactly one injected fault per negative variant (keyed by param name+location).
- **Trace Shape Oracle** — invariant families over each captured trace. Headline =
  **HiddenDownstreamFailure** (label-free, LLM-free): entry 2xx but a deeper span 5xx/otel=ERROR.
Tool-demo paper submitted to ISSTA'26 Tool Demonstrations (`paper/tool-demo/main_issta.tex`).
4 SUTs: TrainTicket, Bookinfo, Sock Shop, Online Boutique.

## 2. Binding prior findings (load-bearing; new plan must respect)
From `archive-2026-06-01/probe-*.md`, `VERDICT-2026-06-01.md`, and `docs/main-contribution/*`:
- **F1 — Mechanism is trivial.** HiddenDownstreamFailure is a ~40-line structural check. A skeptical
  PC will call it "engineering, not research." Novelty CANNOT rest on the detector. (deep-research #1/#2)
- **F2 — Param-level attribution is information-limited.** Spans carry service/controller identity,
  not which parameter was rejected; TrainTicket TARGET_REJECTION=0. Attribution cannot be the
  load-bearing novelty. Ceiling = service-level. (probe-attribution.md)
- **F3 — No wild swallowed-downstream bug corpus.** Mineable count ≈ 0; structurally hard to mine.
  Must rely on injected/replicated faults + citation-level prevalence. (probe-wildbugs.md)
- **F4 — All masked-failure evidence is OUTAGE-DRIVEN, not INPUT-DRIVEN.** A downstream service was
  scaled to zero; MIST *observed* the masking. This makes MIST look like observability/RCA, not test
  generation. Closing this is the crux. (VERDICT, candidate-suts rec #3)
- **F5 — Prevalence is real and citable.** Uber "Tale of Errors" SIGMETRICS'25 (DOI 10.1145/3700436):
  ~29.35% of 2xx requests hide a swallowed downstream error. **CORRECTION (agent 05):** the "~42%
  benign" figure carried from the old deep-research is WRONG — 42.46% is Uber's "Entity Not Found"
  error-*category* share, NOT a benign rate; Uber publishes NO benign-vs-harmful split. So the benign
  rate is UNMEASURED in the literature → constructing genuine-vs-benign labels is itself a contribution,
  and "42% benign" must never be stated. Yuan OSDI'14: 92% of catastrophic failures from mishandled
  non-fatal errors. Also corrected: AutoRestTest = ICSE'25 (not ISSTA'25); RCAEval = WWW'25 (not ICSE).
- **F6 — Novelty threats already in the field.** Soft-error/silent-acceptance at response level is
  taken (LogiAgent, RBCTest, RESTifAI). Microusity (ICPC'23) pinpoints which backend caused a 500
  behind a BFF — the reviewer's most likely counter-citation. Tracetest = manual trace assertions;
  TraceAnomaly/Nezha/TraceGra = unsupervised operational RCA (not test oracles).
- **F7 — The A-bar (anchors).** EmRest (ISSTA'25, ~16 SUTs, 226 bugs); Morest (ICSE'22, 6 SUTs, 44
  bugs, 2 dev-confirmed); LlamaRestTest (FSE'25); AutoRestTest (ISSTA'25); LogiAgent (~12 systems).
  Needs: multiple SUTs + non-trivial baseline (not 0-by-construction) + some real/confirmed bugs +
  ablations + honest P/R/FP.
- **F8 — Natural masking exists (non-circular path).** Istio Bookinfo `reviews` catches a failed
  `ratings` call and returns 200 (developer-intended, PR istio/istio#15489); Online Boutique
  `frontend` log-and-continues on adservice/recommendation errors. So evidence need not be mutants.
  Conceptual analogue: NSDI'24 Legolas (gray/partial failure: subsystem broken while health checks pass).

## 3. Why the OLD plan is insufficient (what the user is reacting to)
The 2026-06-01 plan defaulted to "the study/measurement leg carries the paper" (a prevalence paper
with framing). The user's directive ("smart fetch, soft error, sniper are clearly not enough")
rejects settling for a measurement+framing paper. The new plan must aim for a genuine **technical
contribution** that answers F1 (non-trivial mechanism) and F4 (turn observation into generation).

## 4. Candidate directions the research is testing (NOT yet decided)
- **D1 — Active elicitation of masked failures** (agent 03). Generate inputs and/or inject controlled
  downstream faults that *provoke* hidden-downstream failures, oracle detects. Turns observation →
  generation (answers F4), and the *generation/search strategy* becomes the non-trivial mechanism
  (answers F1). Must beat Filibuster (service-level fault-injection testing) + Microusity + chaos eng.
- **D2 — Automated label-free trace-invariant learning as the contribution** (agent 04). Must be
  distinct from Daikon-on-spans (AGORA/AGORA+), trace-anomaly (TraceAnomaly/Nezha), Tracetest.
  Likely a supporting pillar, not a headline — to be confirmed.
- **D3 — The serious empirical study** (agent 05) underpins whichever mechanism headlines.
- Feasibility of all of the above is bounded by agent 01 (codebase) and the trace signal availability.

## 5. Open questions the research MUST answer (decision gates)
1. Is D1 genuinely novel vs Filibuster/Microusity/chaos-testing, and buildable on MIST's black-box
   trace-driven architecture? (agents 03 + 01)
2. What is the most defensible OPEN gap MIST can own at an A-venue right now? (agent 02)
3. Can D2 headline, or only support? (agent 04)
4. Is an A-bar-clearing evaluation's input set actually obtainable (SUTs, baselines, ground truth,
   confirmed bugs)? Which input is the binding constraint? (agent 05)
5. Net: is there a credible, achievable A-conf contribution, or is the honest answer "strong
   tool-demo / short paper, not A-main"? The plan must state this frankly either way.

## 6. Decision (after research + 3-reviewer round + primary-source verification, 2026-06-30)
- **5 research agents** → spine = active fault-injection + label-free **differential data-integrity oracle**
  (read-back state diff), with cross-service trace-shape oracle as engine; invariant-learning is supporting
  only; input-only elicitation is near-0 yield.
- **3 cold A-conf reviewers** (novelty/eval/soundness) → **Weak Reject ×3, convergent**: (a) novelty too thin
  vs **Cast** (ICSE-SEIP'26); (b) circular/confounded ground truth (read-back diff is "a race not an
  invariant"; labels reuse MIST's signals); (c) conditional contribution with a below-A floor; all three say
  "more SUTs/baselines/stats won't help — the *contribution* is the problem."
- **Primary-source verification (Cast arXiv:2602.00972, full text):** Cast's oracle = phase-based
  metric-threshold criteria from historical traces + granular assertion points; it DOES detect masked-2xx
  and silent dual-write inconsistency (89 production-confirmed). Cast needs production-traffic replay
  (admits missing paths), Java AOP instrumentation (Java-only), Huawei-only eval. → MIST's HONEST deltas:
  generation-based (no prod traffic), black-box/language-agnostic, read-back data-correctness oracle, open
  benchmark. These are SETTING/ACCESSIBILITY + a modest metamorphic-oracle delta, NOT first-to-detect.
- **Filibuster-DB "named it open" quote: UNVERIFIABLE → dropped.** Use only the abstract-supported framing
  (DB fault injection + IDE visualization, not an automated label-free oracle).
- **Verdict:** borderline-A; acceptance is EXECUTION-CONTINGENT (real data-integrity bugs assertion-tools
  miss). A plan cannot "pass all reviewers" on paper because the objection is to the idea's novelty ceiling.
  → README v4 states this honestly + presents the strategic decision (bet-on-execution / different-venue /
  pivot-idea) for the user. See README.md §0/§2/§9 and `research/REVIEW-R{1,2,3}-*.md`.
- **Round-2 re-review of v4 (3 fresh cold reviewers) → ALL THREE moved Weak Reject → BORDERLINE**
  (`research/REVIEW2-R{1,2,3}-*.md`). Evaluation reviewer rates the *methodology axis at Accept*; all three
  independently confirm the §9 self-diagnosis is correctly calibrated. The two methodological FATALs
  (circular GT, unsound read-back) are resolved in design; the cap is the verified Cast novelty overlap; the
  ONLY path to a clear Accept is executing **Gate 3** (real lost-write bugs assertion-tools miss) — which a
  plan cannot promise. Residual non-fatal commitments folded into README §8.5. **Terminal planning state:
  reviewer-hardened, no FATALs, Borderline ceiling on paper; the go/no-go on execution is the team's call.**
