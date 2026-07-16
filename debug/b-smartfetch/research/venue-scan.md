# SmartFetch Venue Scan — CCF-B / CORE A-B testing venues

> Produced 2026-07-15 by a web-research agent (official CFPs via researchr.org / IEEE/ACM pages;
> CCF via two independent catalogue mirrors; CORE via portal.core.edu.au ICORE2026).
> Every deadline tagged VERIFIED/UNVERIFIED. Predatory WASET/conferenceindex listings excluded.
> Verbatim agent report (condensed formatting only).

## TL;DR

1. **CCF and CORE disagree sharply on this venue set.** ICST, EASE, ICPC are **CCF-C but CORE-A**.
   ICSME, SANER, ISSRE, ICWS, ESEM are the only **CCF-B AND CORE-A** double-qualifiers.
   APSEC, SEKE, QRS, SCAM, Internetware are CCF-C and CORE-C (or unranked) — fallbacks only.
2. **Near-exact predecessor at ICST**: *KAT: Dependency-aware Automated API Testing with LLMs*
   (Katalon, ICST 2024, 12 real services, GPT-driven dependency inference + test data). SmartFetch's
   differentiator (live-value harvesting from a running deployment + persistent cross-run registry
   vs KAT's spec+LLM synthetic values) is real but must be argued crisply against this paper.
3. **Only two 2027-cycle CFPs verified today**: **SANER 2027 (abstract Sept 21 / paper Sept 25,
   2026)** and **ICST 2027 (Nov 2, 2026)**. Everything else = historical-pattern estimates.

## Venue candidates (condensed)

| Venue | CCF | CORE | Fit | Next deadline (after Aug 2026) | Notes |
|---|---|---|---|---|---|
| **ICST 2027** | C | **A** | Direct (testing venue; KAT precedent; AIST workshop; Tools track) | **Nov 2, 2026 [VERIFIED]**; conf May 17–21, 2027, San Sebastián | 10pp+2; AE badges; NEW 3-way decision incl. Major Revision |
| **SANER 2027** | **B** | **A** | Indirect-moderate ("analysis" umbrella; tool+empirical welcomed) | **Abs Sept 21 / paper Sept 25, 2026 [VERIFIED]**; conf Mar 9–12, 2027, Richmond VA | ~23–26% acceptance (documented); zero LLM-REST-testing precedent found — work the "registry evolves across runs" angle |
| **ISSRE 2027** | **B** | **A** | Moderate (EvoMaster lineage home: 2021, 2025) | est. late Jun/early Jul 2027 [UNVERIFIED] | ~29–30% acceptance; 12pp incl refs; needs fault-detection result as a primary finding |
| ICWS 2027 | B | A | Moderate (services framing; 15pp LNCS) | est. ~Mar 2027 [UNVERIFIED] | one 2025 short-paper precedent (transformer-based microservice testing) |
| ICSME 2027 | B | A | Indirect (maintenance/evolution) | est. ~Mar 2027 [UNVERIFIED] | consider only if registry-evolution becomes the main hook |
| ESEM 2027 | B | A | Moderate (empirical rigor; open-by-default artifacts; 17+3pp) | est. ~May 2027 [UNVERIFIED] | 2026 edition already passed (May 18, 2026) |
| EASE 2027 | C | A | Moderate (evaluation-centric) | est. ~Jan 2027 [UNVERIFIED]; conf Jun 15–18, 2027, Hanoi | |
| QRS 2027 | C | C | Weak-moderate (EvoMaster-REST started at QRS 2017) | est. ~mid-Apr 2027 [UNVERIFIED] | stable 24–27% acceptance; below stated bar, fallback only |
| APSEC / SEKE / COMPSAC / SCAM / ICPC / Internetware | C | C/B/A mixed | Weak | various [UNVERIFIED] | APSEC+SEKE downgraded CORE B→C in 2023+; not primary targets |
| **AST 2027** (ICSE-colocated) | — | — | Very strong thematic ("AI for Automated Software Testing" named topic) | **Oct 30, 2026 [VERIFIED]**; Apr 26–27, 2027, Dublin | 10pp; workshop tier — companion, not primary |
| ICSE 2027 Tool Demos | (A parent) | | Companion 4pp+video | **Oct 23, 2026 [VERIFIED]** | AutoRestTest pattern: full paper elsewhere + ICSE demo |

## The evaluation bar (calibration papers)

| Paper | Venue | SUTs | Baselines | Ablations | Artifact |
|---|---|---|---|---|---|
| ARAT-RL | ASE 2023 (A, calibration) | 10 services | RESTler, EvoMaster, Morest | yes | GitHub |
| Morest | ICSE 2022 (A, calibration) | 6 projects | RESTler, EvoMaster | partial | yes |
| RESTGPT | ICSE-NIER 2024 | spec-only PoC | none | no | limited |
| LlamaRestTest | FSE 2025 (A, calibration) | 12 services | RESTGPT, RESTler, Morest, EvoMaster, ARAT-RL (the full quintet) | yes | yes |
| AutoRestTest | ICSE 2025 (A, calibration) | 12 services | prior graph/MARL | yes (4-agent) | yes + ICSE demo |
| **KAT** | **ICST 2024 (target tier)** | **12 services** | **1 SOTA tool** | **no** | not confirmed |
| EvoMaster lineage | QRS'17→ICST'18→ISSRE'21→ISSRE'25 | 10-19 → EMB 100+ | RESTler / prior selves | varies | mature OSS |

Implications for SmartFetch:
- **SUT count**: 4–6 microservice *systems* ≈ or > the 10–12-single-API norm in endpoint terms —
  state total operation/endpoint counts explicitly (reviewers pattern-match on that number).
- **Baselines**: ≥2–3 expected at ICST/ISSRE tier; at least RESTler + EvoMaster, plus one LLM-era
  tool (RESTGPT or ARAT-RL); "LLM vs LLM" comparison is now expected, not optional. (KAT got away
  with 1 baseline at ICST 2024, but the bar is rising.)
- **Metrics**: CCF-B/C tier is *more* tolerant of validity/cost metrics than the
  coverage-maximalist A-tier; LLM cost reporting is rare → genuine differentiation lever.
- **Human studies**: not expected in this subfield. Ablations: strengthen, not disqualifying if partial.
- **Artifacts**: ICST/ICSME have formal AE badge tracks; ESEM mandates data availability.

## Ranked recommendation (agent's)

1. **ICST 2027** (Nov 2, 2026 VERIFIED) — only venue with a near-exact predecessor (KAT); AE
   badges; Major-Revision mechanism; risk = CCF-C label + direct comparison against KAT/RESTGPT
   lineage.
2. **SANER 2027** (Sept 21/25, 2026 VERIFIED — earliest) — the clean CCF-B+CORE-A double
   qualifier; documented 23–26% acceptance; risk = zero venue precedent for LLM-REST-testing,
   PC gravity = comprehension/evolution → lead with the registry-evolution story.
3. **ISSRE 2027** (est. ~Jul 2027 UNVERIFIED) — clean double-qualifier, EvoMaster lineage home,
   ~29–30% acceptance; risk = needs fault-detection as a primary finding; deadline a year out.

Companion strategy: ICSE 2027 Tool Demos (Oct 23) or AST 2027 (Oct 30) 4–10pp companion — the
exact AutoRestTest pattern (full paper + demo).

## Sources

ICST 2027 / SANER 2027 / AST 2027 / ICSE 2027 Demos researchr.org tracks; ISSRE 2026
cyprusconferences.org; ICWS/COMPSAC/QRS/SCAM/ICPC/Internetware/EASE/APSEC official 2026 pages;
CCF via ccf.atom.im + GitHub gist mirror (agreeing); CORE via portal.core.edu.au (ICORE2026);
se-deadlines.github.io; KAT arXiv:2407.10227; LlamaRestTest arXiv:2501.08598; ARAT-RL
arXiv:2309.04583; RESTGPT arXiv:2312.00894; Morest arXiv:2204.12148; AutoRestTest
arXiv:2411.07098; EvoMaster publications.md.
