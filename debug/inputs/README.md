# debug/inputs — RESTest Parameter Input Generation Audit

Date: 2026-04-26 (rev. 2 — quality framework rescoped to black-box microservice testing)
Branch: inject-detection

This folder contains a deep audit of the **parameter input generation pipeline** in RESTest's MST mode (Smart Fetch + LLM positive paths, plus the eight-fault-type negative path). It was produced in response to two questions:

1. **How do we measure the quality of generated test inputs in a microservice, black-box context?**
2. **What logical / pipeline bugs exist in the current implementation?**

All findings are evidence-backed: every claim cites a specific `file:line` in the source tree, a specific log artefact, or a peer-reviewed academic source.

## Contents

| Document | What it answers |
|---|---|
| [`input-quality-measurement-framework.md`](./input-quality-measurement-framework.md) | Q1 — Ten input-only metrics across five families (Validity, Microservice Grounding, Realism, Diversity, Negative-Adversariness), 37 citations, six-step measurement protocol, KPI thresholds. **Strict input-level scope** — tool-effectiveness metrics (fault detection, mutation score, code coverage, status-code coverage) are explicitly out of scope and called out. |
| [`microservice-input-quality-research.md`](./microservice-input-quality-research.md) | Companion field survey behind Q1. Reviews Restats / EvoMaster black-box / ARAT-RL / LlamaRestTest / RESTGPT / Schemathesis / Train Ticket / OpenTelemetry trace-based testing. Establishes the input-vs-tool taxonomy and the microservice-specific dimensions. |
| [`pipeline-bug-audit.md`](./pipeline-bug-audit.md) | Q2 — 27 evidence-backed bug findings (with a Fix Status table at the top showing 26 fixed + 1 verified false positive). Each finding has file:line, code excerpt, impact, fix sketch, and verification plan. |
| [`dataflow-map.md`](./dataflow-map.md) | Implementation-reality call-graph (Positive first-step / non-first-step / Smart Fetch / LLM / Negative round-robin / Negative random). Tables of shared mutable state and invariant-enforcement points. Discrepancies with `flow.md`. |
| [`scripts/`](./scripts/) | Self-contained Python pipeline that computes **D1 / D2 / D3** from existing artefacts (no re-run of the tool). `./scripts/run_metrics.sh` mines generated tests + LLM logs, validates against the OpenAPI schema and prompt-stated constraints, then writes a markdown report under `measurements/<RUN_ID>/report.md`. See `scripts/README.md`. |
| [`measurements/`](./measurements/) | Per-run output (gitignored) — regenerated each time you run `./scripts/run_metrics.sh`. |

## How to use this folder

- **Reading order for understanding the system:** `dataflow-map.md` → `flow.md` (in `src/main/resources/My-Example/trainticket/`) → `pipeline-bug-audit.md`.
- **Reading order for fixing bugs:** start at `pipeline-bug-audit.md`. Each finding has a Severity tag, a fix sketch, and a verification plan.
- **Reading order for measuring quality:** start at `microservice-input-quality-research.md` § A–E for the conceptual basis, then `input-quality-measurement-framework.md` § "Operational Protocol" and the KPI table.

## Top 5 highest-impact bug findings (from the audit)

1. **Critical — Log4j format-string bug** in 9 sites: `{:.3f}`, `{:>6}`, `{:.1f}` Python-style placeholders are not recognised by Log4j2; metrics/decisions log as literal garbage and field arguments shift, misreporting smart-fetch and JIT binding metrics. ✅ Fixed.
2. **Critical — `extractJsonObjectFields` doc-promises dot-prefixing** but the code passes the flat map without prefix; nested JSON keys collide last-wins, corrupting `inputFields` / `outputFields` and producing false cross-trace producer matches in `mergeScenariosByDataDependency`. ✅ Fixed (dot prefix + first-wins bare alias).
3. **Critical — `cleanIntegerValue` silently corrupts** every long/decimal value to `"1"`. Uses `Integer.parseInt` (rejects int64), strips decimal points, leaves embedded dashes. ✅ Fixed (regex extraction + Long → BigInteger fallback).
4. **High — `normaliseIdStem` regex matches English words** like `paid`, `aid`, `void`, `valid`, `humid`; bogus stems pollute the registry and trigger false JIT bindings. ✅ Fixed (boundary-aware regex).
5. **High — Boolean TYPE_MISMATCH always becomes valid `Boolean.FALSE`**: `parseTypedValue` calls `Boolean.parseBoolean` on whatever the LLM returns, destroying the type-mismatch. ✅ Fixed (only literal `true`/`false` is converted; everything else preserved as raw string).

For the remaining 22 findings, see `pipeline-bug-audit.md`. **All 26 of 27 are fixed in code; the 27th (#10 ScenarioOptimizer.shatter fallback) was verified to be a false positive.**

## The five families of the input-quality framework (rev. 2)

| Family | Metrics | Why it qualifies |
|---|---|---|
| **Validity** | D1 Schema Conformance Rate, D2 IPD-Satisfaction Rate, D3 LLM Hallucination Rate | Computable from value + schema + prompt — no SUT needed |
| **Microservice Grounding** *(centerpiece)* | D4 Smart-Fetch Hit Rate, D5 ID-Resolvability Rate, D6 Chain Resolution Rate | Cross-service ID grounding via smart-fetch provenance + Jaeger trace lookup + sequence-internal producer-consumer chains |
| **Realism** | D7 ARTE-style Realism Score | Static lookup against DBpedia/Wikidata |
| **Diversity** | D8 Shannon entropy + Simpson, D9 Equivalence-Partition Coverage | Distribution and partition coverage on the input multiset |
| **Negative-Input Adversariness** | D10 Negative-Input Fault-Type Purity | Property of value vs. schema vs. fault label — no SUT |

## What is intentionally **out of scope** for the input-quality framework

The previous revision conflated input quality with whole-tool effectiveness. The user explicitly flagged this. The following are now **out of scope**, with pointers to where they should live:

| Removed metric | Why it is not input-level | Where it belongs |
|---|---|---|
| Mutation score | Requires running mutated SUTs | Future `tool-effectiveness-framework.md` |
| Real fault detection rate | Requires oracle + bug list | Future tool doc |
| **Injected-fault detection rate** | Requires injection harness + oracle. **The injected-faults system measures whole-tool performance, not input quality.** | Future tool doc |
| Code coverage (line/branch/method) | Black-box impossible; requires JaCoCo/PIT | Future tool doc (white-box only) |
| Status-code coverage | Server output, not input property | Future tool doc |
| Negative-test rejection rate (4xx fraction) | Server response | Future tool doc — input-side equivalent is D10 NIFP |
| 5xx rate / silent-acceptance rate | Server response | Future tool doc |

## Reproducibility

All findings can be re-verified:

- Bug audit: each finding lists the exact `file:line(s)`. Use `Read` or open in your editor. The Fix Status table at the top shows what is now in code.
- Quality framework: every metric has a citation and an "How to compute in RESTest" subsection that points at the relevant log directory or source method. The `mvn compile -q` already succeeds after the bug fixes; the framework metrics are computable today by adding the `InputQualityLogger` described in § "Operational Protocol".
- Data-flow map: every step is cited to source — diff against the code to confirm.
- Field survey: 37 numbered citations, 16 from 2023–2026.

If any reader believes a finding or metric is wrong, please grep for the cited line, fetch the cited paper, or submit a counter-example. The audit was deliberately ruthless — false positives are possible and welcome.
