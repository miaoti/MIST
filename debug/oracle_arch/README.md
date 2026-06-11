# MIST Oracle Architecture Redesign — intent-aware, label-free trace oracle

Architecture design for repositioning MIST toward an A-conference contribution, grounded
in the existing `inject-detection` @ `a59044e9` code. Produced 2026-05-28.

## Why
Stage-2a certification showed the current "param-level trace attribution without fault
injection" claim is unsupported on train-ticket (`TARGET_REJECTION=0`, controller-level
spans, 500-crashes). A literature scan showed: LLM/RL **test generation** is a red-ocean
(AutoRestTest ICSE'25, DeepREST ASE'24, LlamaRestTest FSE'25), and the field openly
states **oracles are the bottleneck** ("limited to crashes, regressions, spec violations").
The strongest **invariant-oracle** competitor, AGORA+ (TOSEM'25), works only on
**single-API request/response pairs** — not distributed traces, not cross-service.

→ The defensible white space: **an automatic, label-free, intent-aware test oracle over
cross-service distributed traces for microservice negative testing.**

## The thesis (one sentence) — *narrowed per review (DISPOSITIONS.md B1/F1)*
Lift invariant-based REST oracles from single-API responses to cross-service traces, using
the negative test's own **intent** as a label-free per-test spec, to **disambiguate how the
SUT handled bad input** — correct-rejection vs *silent-accept-that-mutated-downstream* vs
*benign 2xx (input ignored)* vs *hidden downstream failure* — distinctions a status-code
assertion (which MIST already has, coarsely) and a single-API response oracle (AGORA+)
**cannot** make. The trace is what enables the disambiguation.

## The pivotal as-is finding
MIST **already produces** intent (`FaultTarget`), expected outcome (implicit in
`getFaulty()`), and observed outcome (`actualStatusCode<N>`, body) — but **disconnects all
three before the oracle**. The redesign is mostly *re-connection*, not new machinery — which
is why it is feasible on the existing code.

## Documents
- [`CURRENT_ARCHITECTURE.md`](CURRENT_ARCHITECTURE.md) — as-is map (components, dataflow, the 3 gaps G1–G3), file:line evidenced.
- [`TARGET_ARCHITECTURE.md`](TARGET_ARCHITECTURE.md) — the redesign: 5 moves, component diagram, delta table, flags, risks.

## The 5 moves (summary)
1. Promote intent to a first-class exported artifact (`NegativeTestIntent`) — kills G1.
2. Make the expectation explicit data (`ExpectedOutcome`) — kills G2.
3. Outcome-aware oracle interface (thread observed status+body in) — kills G3.
4. Add intent-aware detectors: **SilentAcceptance** + **HiddenDownstreamFailure** (ERROR); elevate **TargetAttribution** to a real verdict. ← the contribution.
5. Generate negatives that exercise the new detectors (semantically-invalid-but-syntactically-valid baits).

## Scope note
This is **architecture phase only** — no evaluation design (SUT count, baselines) here.
Param-level attribution is explicitly **not** the headline (SUT-dependent); service-level +
the two new bug classes do not depend on it.

## Status
Design drafted + reviewed; [`DISPOSITIONS.md`](DISPOSITIONS.md) records the per-comment
disposition (0 rejects — reviewer correct throughout). No code changed; SUT-derived OpenAPI
spec untouched.

## ⚠️ Decisive caveat — demonstrability on train-ticket (DISPOSITIONS.md C2)
The architecture is sound and feasible, but **train-ticket cannot demonstrate its headline.**
An empirical scan (836 traces; corroborated by the Stage-2a recall data) found **zero**
instances of the trace-only bug classes: every error propagates a 500 to the root (0 hidden
downstream failures, 0 observed silent mishandling; param-level `TARGET_REJECTION` was also 0
in the cert). train-ticket **fails loudly**, so the trace-only value cannot be shown here.
→ Need a SUT that **mishandles silently**, or deliberately injected **SUT-side mutants**
(swallow downstream errors / accept-and-mutate invalid input) as ground truth. This is an
architecture/methodology requirement, independent of evaluation scale.
