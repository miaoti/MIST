# debug/inputs/smart_fetch — RESTest Smart Input Fetch Audit

Date: 2026-05-05
Branch: `inject-detection`

This folder is the persistent, evidence-backed record of an audit + refinement cycle on RESTest's **Smart Input Fetch** subsystem (`src/main/java/es/us/isa/restest/inputs/smart/`) and its persisted state (`src/main/resources/My-Example/trainticket/input-fetch-registry.yaml`).

The cycle ran in three phases:

1. **Phase 1 — Audit & plan.** A bug audit found 40 findings (4 Critical, 12 High, 18 Medium, 6 Low). A quality framework defined 17 metrics across 7 families with 47 citations. A dataflow-map cataloged 15 LLM prompts, 24 mutable shared-state fields, and 10 design-doc-vs-code discrepancies. A refinement plan grouped the findings into 8 themed work-streams. Independent peer review surfaced 24 comments; per-comment disposition was applied.
2. **Phase 2 — Implementation.** The 8 streams shipped (39/40 fixed; 7/8 reviewer-mandated additions applied). A migration script cleaned the polluted YAML in place (175→82 mappings; 0 sentinels, 0 fabrications, 0 templated placeholders). Two independent verification rounds confirmed the fixes via grep-cited evidence.
3. **Phase 3 — Fresh deep code review.** A clean-context reviewer found 30 additional findings; 25 were fixed (5 Skipped: out-of-scope, opinionated, or migration-heavy).

Build is green, registry is canonical, smart-fetch's purpose is preserved (no behavioral pivot — every fix reinforces "harvest realistic upstream values via discovery → registry → fetch → extract → cache → fallback").

## Contents

| Document | What it has |
|---|---|
| [`execution-summary.md`](./execution-summary.md) | **Start here.** Headline status, registry KPIs, themed list of every fix with file:line citations, deferrals with rationale. |
| [`smart-fetch-bug-audit.md`](./smart-fetch-bug-audit.md) | The original 40-finding audit with code excerpts, impact, evidence, and fix sketches. Historical reference; the in-code annotations point back here by Finding number. |
| [`smart-fetch-quality-framework.md`](./smart-fetch-quality-framework.md) | 7 metric families, 17 metrics, 47 citations (19 from 2024–2026), KPI thresholds, six-step measurement protocol. |
| [`dataflow-map.md`](./dataflow-map.md) | Implementation-reality call graph (15 LLM prompts, 24 mutable fields, 10 discrepancies vs. `flow.md`). |
| [`refinement-plan.md`](./refinement-plan.md) | Original 8-stream plan (now executed; kept for the rationale and dependency-map structure). |
| [`scripts/migrate_registry.py`](./scripts/migrate_registry.py) | Re-runnable YAML cleanup tool: drops `NO_GOOD_MATCH` rows, fabricated `*/query` endpoints, literal `{paramName}` placeholders, stale-zero-success entries; lowercases services; caps error history. |

## Top-line numbers

| | Round 1 (audit) | Round 2 (fresh review) | Total |
|---|---|---|---|
| Findings | 40 | 30 | 70 |
| ✅ Fixed | 39 | 25 | 64 |
| ⏸ Deferred / skipped | 1 | 5 | 6 |
| Reviewer additions | 8 (round 1 peer review) | — | 7 of 8 applied |

| Registry KPI | Before | After |
|---|---|---|
| `endpoint:` rows | 175 | 82 |
| `successRate: 0.0` | 130 (75 %) | 38 (46 %) |
| `service: NO_GOOD_MATCH` | 2 | 0 |
| `*/query` fabrications | 56 | 0 |
| Literal `{paramName}` placeholders | 27 | 0 |
| Distinct case-folded services | 14 | 14 (canonical lowercase) |

## What's intentionally not here

This folder used to also contain `reviewer-feedback.md`, `reviewer-disposition.md`, `verification-report.md`, `verification-report-2.md`, and `fresh-code-review.md` — all intermediate workflow artifacts. Their substantive findings have been integrated into `execution-summary.md` (theme-organized) and the in-code annotations. Removing them removed redundancy without losing evidence.

## Reproducibility

- Every finding is grep-verifiable. `smart-fetch-bug-audit.md` cites exact `file:line` for the original buggy form; the current code carries `Bug audit Finding #N` or `Fresh-review Finding F<n>` annotations near every fix.
- Every quality metric is computable from existing log lines or via a short additional logger described in `smart-fetch-quality-framework.md` § Operational Protocol.
- Every dataflow assertion in `dataflow-map.md` cites a file:line.
- The migration script can be re-run on any future polluted registry; the original is always backed up to a timestamped `.bak.yaml`.
