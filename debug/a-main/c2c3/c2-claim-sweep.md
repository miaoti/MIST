# Related-work SWEEP for the C2 claim freeze (plan §2.4-1) — 2026-07-08

**Claim under test (frozen string):** *"the first open-source labeled benchmark built for ORACLE
EVALUATION on masked-downstream / acknowledged-but-lost data-integrity faults — pairing positive
strata with a benign-trap false-positive stratum and a blind-adjudicated wild stratum, under a
per-case provenance taxonomy."*

Qualifiers: **Q1** open-source · **Q2** labeled benchmark (per-case fault labels) · **Q3** built for
oracle evaluation · **Q4** fault class = masked-downstream / acked-but-lost data-integrity · **Q5**
benign-trap FP stratum · **Q6** blind-adjudicated wild stratum · **Q7** per-case provenance taxonomy.

## 1. VERDICT — claim string SAFE AS FROZEN; no narrowing forced
32 candidates examined (2014–2026, emphasis 2023–26). **No artifact satisfies the conjunction; every
candidate fails ≥2 qualifiers, most fail 4+.** Three findings accompany the freeze:
1. **Cast released nothing open.** arXiv:2602.00972 (ICSE-SEIP'26, Huawei Cloud + Sun Yat-sen): no
   availability statement; SUTs anonymized proprietary ("Service 1–4"); the 137-found/89-confirmed
   bug list NOT released; the lead academic author's homepage lists code for other papers, none for
   Cast. Fails Q1 outright. (Bonus: Cast's own text confirms the fault class is real and industrial
   — "HTTP 200 OK … despite the internal failure" on unchecked async Kafka publishes — usable as
   MOTIVATION, not competition.)
2. **Two proactive-citation obligations (pattern-match risk, not claim risk):** CloudAnoBench
   (benign-stratum axis) and the Uber "Tale of Errors" Zenodo artifact (fault-class axis) — §3.
   Cite and differentiate BOTH before a reviewer discovers them.
3. **Two 2025–26 RCA benchmarks exclude the masked class BY CONSTRUCTION** — OpenRCA 2.0 drops
   injections with no observable SLO impact; the Fault-Propagation-Aware TT benchmark discards
   84.4% of its 9,152 injections as "No Anomaly" under an entry-point-SLI filter. **Affirmative
   positioning evidence: the prevailing benchmark methodology systematically filters out exactly the
   stratum our benchmark labels.** Add this sentence + citations to the C2 framing.

**Wording obligation:** define "per-case provenance taxonomy" on first use as LABEL-provenance
classes (by-construction / by-docs / adjudicated …) — several RCA benchmarks advertise "fault
taxonomies"/"multi-expert label pipelines"; without the definition a reviewer may wrongly equate.

**Watch-list rider (camera-ready):** OpenRCA 2.0 and the FP-aware TT benchmark promise release
"upon acceptance" — re-check their released form before submission (neither, as described, adds a
benign-trap/wild stratum or oracle-eval labels).

**Rev-2 review annotations (2026-07-08, step-1 review C-A1 + m1):**
- **Single frozen claim string:** the authoritative hardened string ("per-case **label-provenance**
  taxonomy") + the Filibuster and Cast differentiation live in `c2-freeze.md` §1 — cite THAT to avoid
  the multi-copy drift (this doc + the plan + benchmark/README carried slightly different wordings, m1).
- **Filibuster (SoCC'21) — first-class differentiation (C-A1):** a resilience-TESTING framework with a
  fault-tolerance-bug application corpus; developer-assertion/bug-report labels, NO benign-trap FP
  stratum, NO adjudication rubric, not masked-2xx-labeled for oracle eval. It is the most dangerous
  SE-venue competitor and now has an explicit defense row (freeze §1), not just a plan mention.
- **Cast framing strengthened (C-A1):** do NOT rest the differentiation on openness alone. Cast
  (closed) validates the class is industrially real (motivation); our scientific delta = the paired
  benign-trap FP stratum + per-case oracle-evaluation labels + the blind-adjudicated wild stratum,
  none of which Cast provides.
- **The paper LEADS with the study, never "first" (plan §1 writing rule):** the claim string is
  recorded for priority-defense only.

## 2. Defense-table rows to ADD
(Existing rows — Filibuster corpus, train-ticket-fault-replicate, Nezha, RCAEval — re-verified
accurate; update RCAEval's venue tag to "ASE'24/WWW'25 companion/FSE'26, 735 cases / 9 datasets".)

| prior open artifact | source | what it is | fails |
|---|---|---|---|
| Cast (ICSE-SEIP'26) | arxiv.org/abs/2602.00972 | in-production resilience testing at Huawei Cloud; 137/89 bugs incl. silent-2xx | **Q1 — no open artifact** (hence Q2/Q5/Q6/Q7 moot) |
| Uber "Tale of Errors" artifact (SIGMETRICS'25) | zenodo.org/records/13947828 + DOI 10.1145/3700436 | ~1.4M sanitized production traces w/ error tags; 29% of 2xx carry hidden non-fatal errors | Q2 (raw, no per-case labels), Q3, Q5, Q6 (wild but UNadjudicated), Q7 |
| CloudAnoBench (arXiv 2508.01844) | jayzou3773.github.io/cloudanobench-agent | 44 scenarios (28 anomalous + 16 "deceptive normal" benign), 1,252 cases of LLM-SYNTHESIZED metric/log lines for AD classification | Q3 (AD classification, not request-level oracle eval), Q4 (no data-integrity faults; no executed SUTs), Q6, Q7 |
| AGORA / AGORA+ (ISSTA'23 Dist. Artifact; TOSEM'25) | zenodo.org/records/7970822 | invariant-based REST oracle generation; labeled invariants, seeded output errors, 11 real API bugs | Q4 (single-service, seeded output mutations), Q5 (FP over invariants, not benign system stratum), Q6, Q7 |
| AIOpsLab (Microsoft 2025) | github.com/microsoft/AIOpsLab | agent-eval env; 48 problems, ChaosMesh symptomatic faults | Q3, Q4, Q5, Q6, Q7 |
| ITBench (IBM 2025) | github.com/itbench-hub/ITBench | 102 IT-automation scenarios w/ YAML ground truth | Q3, Q4, Q5, Q6, Q7 |
| OpenRCA (ICLR'25) | github.com/microsoft/OpenRCA | 335 enterprise failures + 68 GB telemetry; LLM RCA | Q3, Q4, Q5, Q6, Q7 |
| OpenRCA 2.0 (arXiv 2606.27154) | arxiv.org/html/2606.27154v2 | 500 instances, causal-process supervision; DROPS injections lacking SLO impact | Q1 (release upon acceptance), Q3, Q4 (**masked class excluded by construction**), Q5, Q6 |
| FP-aware TT benchmark (arXiv 2510.04711) | arxiv.org/html/2510.04711v2 | 1,430 validated TT failure cases from 9,152 injections; **84.4% "No Anomaly" discarded** | Q3, Q4 (masked filtered out), Q5, Q6, Q7; Q1 pending |
| AgenticOpsEval: AIOps2025+RCA100 (arXiv 2606.29193) | aiops.cn gitlab | ~500 expert-adjudicated failure cases for LLM-agent diagnosis | Q3, Q4, Q5, Q6 (root-cause labels of injected faults, not wild flags), Q7 |
| Injected-telemetry RCA/AD family: GAIA-MicroSS · AnoMod · LEMMA-RCA · PetShop · Murphy · TraceBench'14 · FIRM | (family row; repos verified) | open telemetry corpora w/ injected/curated anomalies + root-cause labels (perf/resource/network) | all fail Q3–Q7 identically |
| LO2 (arXiv 2504.12067) | zenodo.org/records/14257989 | light-oauth2 log/metric dataset; runs labeled by EXPLICIT negative-test API error type | Q4 (explicit 4xx/5xx — the antithesis of masked-2xx), Q3, Q5, Q6, Q7 |
| OathKeeper 109-case silent-semantic-violation study (OSDI'22) | github.com/OrderLab/OathKeeper | 109 real silent semantic failures in 9 distributed systems + rule inference | Q2/Q3 (study+tool), Q4 (ZooKeeper-class infra, not microservice masked-downstream writes), Q5, Q6, Q7 |
| Rainmaker (NSDI'23) | github.com/xlab-uiuc/rainmaker | REST-layer transient-fault injection for cloud-backed apps; 73 bugs | Q2 (repro list, not labeled benchmark), Q4, Q5, Q6, Q7 |
| EMB→WFD + PRAB (MSR'25) | github.com/WebFuzzing/Dataset · github.com/alixdecr/PRAB | SUT/spec corpora for REST fuzzing | **Q2 — NO fault labels at all**; Q3–Q7 moot |
| MASTOR (arXiv 2606.10465) + TOGBench/OE25dev (AIware'26) | arxiv/github | LLM-era oracle-generation benches (mutation-kill REST semantics; unit-level oracles w/ FP analysis) | Q4 (seeded mutants/unit-level), Q5, Q6, Q7 |
| Online Marketplace (SIGMOD'25) | arxiv.org/abs/2403.12605 | workload benchmark w/ data-management correctness criteria for DATA PLATFORMS | Q2, Q3, Q5, Q6, Q7 |
| TUM TT test-suite artifact | mediatum.ub.tum.de/doc/1796223 | TT-derived test suites; 130 tests labeled FAILING | Q3, Q4, Q5, Q6, Q7 |

## 3. Closest call — CloudAnoBench (two runners-up); none contests
CloudAnoBench is the only artifact pairing labeled positives with a DESIGNED benign-FP stratum (16
"deceptive normal" scenarios) — the same IDEA as our S2 — but it is metric/log AD classification
over LLM-synthesized telemetry: no executed SUTs, no request-level oracle verdicts, no
data-integrity faults, no wild stratum, no provenance taxonomy (fails Q3+Q4 conjunctively). Cite
where S2 is motivated; differentiate on exactly those axes. Runner-up (fault-class axis): the Uber
Zenodo artifact (raw, unlabeled, unadjudicated — cite the artifact, not just the paper). Runner-up
(purpose axis): AGORA+ (nearest "labeled dataset for REST oracle eval" — single-service, seeded
output mutations, no strata).

## 4. Search coverage (auditability)
16 WebSearch query families + 13 primary-source WebFetches (arXiv, Zenodo, GitHub, Illinois Data
Bank, author homepage). Families: Cast+artifact (3 forms incl. author homepage); {masked, silent,
swallowed, partial-failure, data-integrity, lost-write, acked} × {microservice, benchmark, dataset,
corpus, labeled}; LLM-era ops benches; REST-oracle line (AGORA/EMB/PRAB/RESTestBench/MASTOR/
TOGBench); RCA/AD families; systems/chaos line (OathKeeper, Rainmaker, Filibuster extensions,
ChaosETH/ChaosMachine, MicroRes, MicroFI, FastFI, Blueprint-adjacent); saga/compensation searches;
benign-FP-stratum + blind-adjudication searches; "HTTP-200-but-lost" phrasing sweep. Resolved-to-
nothing: OmniTune (no such dataset), TraceBench (only 2014 HDFS), Murphy (perf traces), MicroFI/
FastFI (tools), Blueprint (framework), DeathStarBench fault datasets (3rd-party telemetry only),
swallowed-exception literature (code-pattern studies), SDC (hardware, out of class). Counts: 32
examined; 18 new rows (one a 7-artifact family row); 4 existing rows re-verified.
