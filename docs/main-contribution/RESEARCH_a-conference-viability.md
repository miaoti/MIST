# A-Conference Viability — research verdict (deep-research, 2026-05-29)

Rigorous, cited assessment of whether the intent-conditioned distributed-trace oracle is a
viable **main contribution** for ICSE/FSE/ASE/ISSTA. Method: 5-angle fan-out, 22 sources,
98 claims, 25 adversarially verified (3-vote) → 19 confirmed / 6 killed. Raw report:
`research-raw-a-conf-viability.json`.

## Verdict (frank)
**Credible niche contribution, but NOT a confident accept-grade MAIN contribution *as
framed*. It needs more.** A skeptical PC would currently rate it borderline/weak
("narrow + thin evaluation"). It is reachable to accept-grade with specific changes (below).

## The three prongs
- **Problem significance — ✓ SOLID.** The test-oracle problem for REST/microservice APIs is
  a recognized, named, still-open challenge (TSE'17 "oracle problem"; TOSEM'23 92-paper
  survey ranks "Oracle problem" 3rd-most-addressed and lists "Automated oracles" as open
  future work). [TSE'17, TOSEM'23 survey, AGORA]
- **Novelty — REAL but narrower than hoped.** No published system combines **label-free +
  intent-conditioned + distributed-trace** for negative API testing — the closest oracle
  work (AGORA/ISSTA'23, AGORA+/TOSEM'25, EvoMaster, metamorphic-testing/TSE'17, RESTifAI)
  is all **response-level** (status/schema/invariant, no traces); the closest trace work
  (Tracetest = manual user-authored span assertions; TraceGra/TraceAnomaly = unsupervised
  *operational* anomaly detection, not a test oracle). **But two blunting facts:**
  1. **Silent-acceptance is ALREADY published.** RESTifAI (ICSE'26 demo, Dec 2025) does
     LLM negative testing and already found **real HTTP-200-instead-of-400** silent-accept
     bugs at the response level — *no traces needed*. → silent-acceptance is **not** our
     novelty; the genuinely-novel target collapses to **hidden-downstream-failure**
     (gateway 2xx over a downstream 5xx), which response-level oracles structurally cannot see.
  2. **Trace primitives are prior art.** Tracetest can already assert downstream-span status
     (manually); label-free trace analysis exists (TraceGra). → novelty rests **entirely on
     the automated INTENT-CONDITIONING mechanism**, which must be the crisp, defended,
     load-bearing core — not the bug taxonomy.
- **Evaluation — WEAKEST part.** "Baseline detects 0 by construction" is near-tautological;
  single SUT (train-ticket) + injected-only mutants = external-validity + mutant-realism
  threats. The accepted bar pairs mutation studies with **real developer-confirmed bugs +
  multiple SUTs** (MT'17: 302/317 mutants killed AND 11 real issues on 6 APIs, 10 confirmed;
  RESTifAI: real bugs on a real industrial SUT; AGORA+/SATORI report honest precision/recall
  on non-trivial baselines).

## To make it accept-grade (exactly what's required)
1. **Reframe the headline** to the **automated intent-conditioned trace oracle MECHANISM** +
   **hidden-downstream-failure** as the novel detection target. Drop silent-acceptance as the
   headline (RESTifAI owns it); keep it only as a secondary "we also catch it, via traces".
2. **Multiple SUTs** (2-3+ real microservice systems, not just train-ticket).
3. **Real-bug evidence** (developer-confirmed hidden-downstream-failures in production-grade
   microservices), not only injected mutants.
4. **A non-trivial baseline** (not 0-by-construction): compare on the SAME traces against an
   operational trace-anomaly detector (TraceGra/TraceAnomaly) and/or Tracetest with
   expert-authored downstream-span assertions; report honest precision / recall / FP rate.
5. **Cite + explicitly distinguish RESTifAI** (the single biggest novelty threat) and
   re-check for 2026 trace-oracle work immediately before submission.
6. **Ablate "learned trace-shape invariants"** to prove they're distinct from (a) AGORA-style
   Daikon invariants lifted to span attributes and (b) unsupervised trace-anomaly models —
   make that distinction the load-bearing, evaluated novelty.

## Open questions (must answer before betting the paper on it)
- Does any 2026 paper already pair automated negative testing with a distributed-trace
  oracle / extend RESTifAI to span-level assertions? (first-of-kind may already be at risk)
- Can we show REAL hidden-downstream-failure bugs in production-grade microservices?
- What is the fair non-trivial baseline, and how does our precision/recall compare on the same traces?
- Is "learned trace-shape invariants" genuinely distinct from Daikon-on-spans / trace-anomaly models, and can we ablate it?

## Key sources
AGORA (ISSTA'23) personales.us.es/sergiosegura/files/papers/alonso23-issta.pdf · AGORA+ (TOSEM'25)
dl.acm.org/doi/10.1145/3726524 · EvoMaster (ASE'24) link.springer.com/article/10.1007/s10515-024-00478-1 ·
MT-of-REST (TSE'17) javiertroyauma.github.io/publications/TSE2017_REST_prePrint.pdf · RESTifAI (ICSE'26 demo)
arxiv.org/html/2512.08706v1 · Tracetest github.com/kubeshop/tracetest · TraceGra
sciencedirect.com/science/article/abs/pii/S0140366423001135 · REST-testing survey (TOSEM'23) dl.acm.org/doi/10.1145/3617175
