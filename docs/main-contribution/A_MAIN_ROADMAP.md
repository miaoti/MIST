# A-main roadmap — label-free distributed-trace oracle for swallowed cross-service failures

> Deep-research-backed (2026-06-01, 104-agent run, 21/25 claims confirmed on primary sources).
> Verdict: a HYBRID method+study is the strongest viable A-main path — **borderline-but-achievable, NOT safe.**

## 0. The one-line contribution
**The FIRST label-free *distributed-trace* oracle for (i) swallowed-downstream-failure (gateway returns 2xx while a downstream span has a swallowed 5xx/gRPC-error) and (ii) trace-only silent-acceptance — failures invisible in the client-facing response — plus a measurement study of how prevalent they are and what fraction the oracle catches.**

## 1. Precise novelty — what to claim and what NOT to
- **DO NOT claim soft-error detection as novel.** A 2xx body that signals a domain failure is already detected at the response level by **LogiAgent** (arXiv 2503.15079), **RBCTest** (ICSE'26, arXiv 2504.17287), and **RESTifAI** (ICSE'26 demo, arXiv 2512.08706). Claiming it = instant reject.
- **DO claim the TRACE-ONLY / cross-service class.** RESTifAI, LogiAgent, RBCTest, AGORA+ (TOSEM'25) **all operate at the single-response / single-operation level and none consume distributed traces or downstream spans** (3-0 verified across all four). The swallowed-downstream-span detection + intent-conditioned attribution is genuinely **unclaimed**.

## 2. The killer motivation (cite it everywhere)
**Uber, "The Tale of Errors in Microservices," SIGMETRICS'25 / POMACS 8(3), DOI 10.1145/3700436**: **~29.35% of successful (2xx) client requests contain ≥1 non-fatal error swallowed deep in the call chain** (11B RPCs, ~1,900 endpoints, 6,000+ services; 84% of endpoints saw ≥1). Their mechanism is a 1:1 match: *"a fatal error somewhere deep in the call chain … one of the services squashed the error, resulting in a 2xx response."*
- **Honest caveat:** ~42% are benign speculative Entity-Not-Found lookups → this evidences **prevalence**, not that 29% are bugs. **Separating genuine swallowed defects from benign graceful-degradation IS the research question** (and a defensible one).

## 3. Real-bug ground truth (obtainable, but qualified)
**FudanSELab TrainTicket TSE'18 corpus** (`github.com/FudanSELab/train-ticket-fault-replicate`): 22 faults from an industry survey of 16 engineers / 12 companies; ~10/22 are cross-service "Interaction" faults; **F6, F7, F10, F12 are real swallowed/unhandled-downstream cases** (F12 "does not consider an unexpected output of a microservice in its call chain"; F6 "endless recursive requests caused by SQL errors of another dependent microservice").
- **We already run TrainTicket → directly reproducible.**
- **CAVEAT (use precise language):** these are real-world-**derived** patterns **re-injected** via code branches, **NOT wild commit-history bugs** (the research refuted 3 attempts to call them "wild bugs", and refuted F8/F18/F20 as "silent"). Stronger than mutants, weaker than wild bugs. Reviewers may still discount → add some genuinely-mined OSS wild bugs if at all possible.

## 4. The A-main bar (non-negotiable additions — we don't clear it yet)
| Bar | Set by | We have | Gap |
|---|---|---|---|
| **Scale** ~12 SUTs (de-facto) | LogiAgent (12 systems) | 4 self-deployed | expand SUTs **or** a large-scale trace study carries the weight |
| **Precision/Recall + real baseline** | RBCTest (P/R + head-to-head vs AGORA+) | none | report P/R on swallowed-failure task vs status-code + response-level-LLM oracle |
| **Confirmed REAL bugs** | AGORA+ (32 in live public APIs) | injected/outage only | TrainTicket F6/F7/F10/F12 + ideally mined OSS wild bugs |

## 5. Milestones
- **M1 Reposition** the whole narrative onto the trace mechanism + intent-attribution; Uber-29% as motivation; LogiAgent/RBCTest/RESTifAI/AGORA+ as **response-scoped non-baselines**.
- **M2 Prevalence/detection study at real/realistic trace scale**: what fraction of 2xx hide a swallowed failure, how many the oracle flags vs a response-level baseline, **and the false-positive rate** (benign degradation vs genuine defect — must be measured, not asserted).
- **M3 Ground truth**: reproduce TrainTicket F6/F7/F10/F12 (and more) on our live TT; mine OSS microservice repos / incident postmortems for wild swallowed-error bugs.
- **M4 Evaluation**: precision/recall + head-to-head vs a response-level oracle + an **ablation** isolating the trace mechanism AND the intent-conditioned attribution.

## 6. Skeptical-PC counterarguments to pre-empt
1. **"Mechanism is trivial — a 5xx-behind-2xx check is engineering, not research."** → lean on intent-conditioned attribution (the genuinely-novel idea) + the prevalence study. **BIGGEST RISK:** attribution currently "works weakly" (memory `project_phase2_attribution_gap`: TARGET_REJECTION=0 on TT). If it can't be strengthened, framing-A collapses to a tool-demo and the paper must lean entirely on the study arm (B).
2. **"It just flags normal graceful degradation."** (Uber: ~42% benign) → must measure precision on real traces.
3. **"TrainTicket faults are re-injected, not wild."** → add genuinely-mined wild bugs.
4. **"4 SUTs < 12."** → expand or let the large-scale trace study carry it.

## 7. Load-bearing unknowns (decide these before committing)
1. Can **intent-conditioned attribution** be moved from "works weakly" to a quantified, ablation-supported contribution? (currently a hard limitation)
2. Does a **mineable corpus of WILD swallowed-downstream bugs** exist beyond TrainTicket? (NOT verified by the research — aspirational)
3. What is the **false-positive rate** on real production-like traces (defect vs benign degradation)?
4. Is a **large-scale trace study** runnable (public trace dataset or expanded SUTs)?

## 8. Time-sensitivity (act with urgency)
The "gap is open" conclusion is the **most perishable** claim — RESTifAI (ICSE'26), RBCTest (ICSE'26), AGORA+ (TOSEM'25), LogiAgent (Mar'25), Uber (SIGMETRICS'25) are all 2025/pre-publication. A competing main-track trace oracle could appear during the review cycle.

## 9. Bottom line (frank)
Achievable but **borderline**. It becomes credible **only if** (M2) the prevalence/detection study runs at real scale **AND** (M3) some non-injected wild bugs are obtained. Without both → a strong tool-demo (CAIN/industry/short-paper grade), not an A-main contribution.

Sources: arXiv 2512.08706 (RESTifAI), 2503.15079 (LogiAgent), 2504.17287 (RBCTest), AGORA+ TOSEM'25 PDF, ACM 10.1145/3700436 (Uber), IEEE TSE.2018.2887384 + FudanSELab/train-ticket-fault-replicate (TrainTicket faults).
