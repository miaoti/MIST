# Input Quality Metrics — Pre-Fix Baseline

**Run:** `TrainTicketTwoStageTest_1777780533352`
**Date:** 2026-05-02
**Generator:** RESTest, qwen2.5-coder:14b
**OAS:** `merged_openapi_spec 1.yaml`

## Headline

| # | Metric | Score | Threshold | Status |
|---|---|---|---|---|
| D1 | Schema Conformance | **98.26%** | ≥ 90% | ✅ |
| D2 | IPD Satisfaction | N/A | ≥ 85% | — (no IDL declared) |
| D3 | LLM Hallucination | **0.00%** | ≤ 20% | ✅ |
| D4 | Smart-Fetch Hit | **75.91%** (upper 89.64%) | ≥ 60% | ✅ |
| D5 | ID Resolvability | **0.00%** | ≥ 40% | ❌ |
| D6 | Chain Resolution | N/A | ≥ 50% | — (no multi-step seqs) |
| D7 | Realism | **96.88%** | ≥ 50% | ✅ |
| D8 | Pool Diversity (H_norm / Simpson) | **0.5525 / 0.7058** | H_norm ≥ 50% | ✅ |
| D9 | Equivalence-Partition Coverage | **43.14%** | ≥ 50% | ❌ |
| D10 | NIFP (overall) | **74.76%** | per-type ≥ 95% | ❌ |

## Per-metric detail

### D1 — Schema Conformance Rate

- 1807 / 1839 positive values valid (98.26%).
- 30337 negatives skipped, 22 FALLBACK_* values skipped.
- Worst: `contactsDocumentNumber` 15%, `differenceMoney` 60%, `accountId`/`price` 85%.

### D3 — LLM Hallucination Rate

- 80 413 LLM values seen; 760 actionable; 0 hallucinated.
- 7 abstentions (`NO_GOOD_MATCH` / `NO_VALUES_GENERATED`).

### D4 — Smart-Fetch Hit Rate

- 357 ID-typed positive inputs.
- 271 explicit smart-fetch (conservative 75.91%).
- 49 shared-pool draws (upper bound 89.64%).
- 37 LLM fallback.
- Worst: `loginId` 20% (cons.) / 57.5% (upper).

### D5 — ID Resolvability

- 0 / 357 ID-typed values appeared in the Jaeger trace export.
- 24 distinct values observed in traces.
- **Cause:** trace export came from input population, not post-execution. Re-run with `--post-exec-jaeger` once SUT is hit.

### D6 — Chain Resolution Rate

- 0 / 0 multi-step sequences fully resolved; 337 single-step sequences excluded.
- **Cause:** all generated sequences are single-step in this run.

### D7 — Realism Score

- 186 / 192 NLP-typed values matched a curated entity (96.88%).
- Oracle: 732 offline entries, 0 online lookups.
- 8 FALLBACK_* skipped.
- Lookup sources: 110 offline · 75 token · 1 suffix-stripped · 6 unknown.
- Remaining unrealistic: `empirestatebuilding` (×2), `12345678901`, `New York City` (×3).

### D8 — Pool Shannon Entropy + Simpson

- 68 pools gated (≥ 3 rows).
- Mean H_norm = 0.5525 (Shannon / log₂(pool size)).
- Mean Simpson = 0.7058.
- Lowest H_norm pool: `stationList` for `POST /api/v1/adminrouteservice/adminroute` (3 distinct / 20, H_norm = 0.13).

### D9 — Equivalence-Partition Coverage

- 68 pools gated, mean EPC 43.14%.
- Dominant miss pattern: `(empty, short, long)` — values cluster in `medium` (10–50 chars).
- `empty` is structurally unreachable for positive variants — accounts for ~25 percentage points of the gap.

### D10 — Negative-Input Fault-Type Purity

| Fault label | Total | Pure | Schema-unbounded | NIFP | Pass |
|---|---:|---:|---:|---:|---|
| `BOUNDARY_VIOLATION` | 248 | 0 | 248 | 0.00% | ❌ (schema gap) |
| `EMPTY_INPUT` | 12 | 12 | 0 | 100.00% | ✅ |
| `NULL_INPUT` | 21 | 21 | 0 | 100.00% | ✅ |
| `OVERFLOW` | 254 | 226 | 0 | 88.98% | ❌ |
| `SEMANTIC_MISMATCH` | 1 224 | 956 | 0 | 78.10% | ❌ |
| `SPECIAL_CHARACTERS` | 1 134 | 882 | 0 | 77.78% | ❌ |
| `TYPE_MISMATCH` | 455 | 406 | 0 | 89.23% | ❌ |

Diagnoses:

- **BOUNDARY_VIOLATION 0%** — 248 / 248 target params with no `min`/`max`/`minLength`/`maxLength` declared in OAS. Schema-completeness gap, not a value-purity miss.
- **OVERFLOW 89%** — 28 impure cases all fired at *boolean* params (`enableBoughtDateQuery = 2147483647` etc.). Generator was emitting overflow values for booleans, where overflow is undefined.
- **SEMANTIC_MISMATCH 78%** — values like `truefalse`, `01`, `null`, `undefined`, `NaN` against booleans were labelled SEMANTIC but are actually TYPE_MISMATCH.
- **SPECIAL_CHARACTERS 78%** — partly metric (OWASP regex too narrow: misses `DELETE FROM`, raw `\x00`), partly real injection payloads fired at non-string types.
- **TYPE_MISMATCH 89%** — 49 cases are Java `Map.toString()` literal `{key=value}` for string-typed params; not valid JSON, classifier fell through.

## Generator fixes applied (post-baseline)

These ship in the next run:

- `InvalidInputType.appliesTo(oasType)` — applicability matrix.
- `HardcodedInvalidInputGenerator` + `ZeroShotLLMGenerator` gate every fault category by it.
- `BOUNDARY_VIOLATION` is skipped silently when the schema declares no bound.
- 9 JUnit tests in `InvalidInputTypeApplicabilityTest`.

Expected post-fix changes: BOUNDARY_VIOLATION 0% → ~0 cases; OVERFLOW ↑ ~95%+; SEMANTIC_MISMATCH ↑ ~90%+; SPECIAL_CHARACTERS ↑.

## Source artefacts

- Generated tests: `src/test/java/trainticket_twostage_test/TrainTicketTwoStageTest_1777780533352`
- LLM log: `logs/llm-communications/llm-communication-20260502-225524.log`
- Execution log: `logs/trainticket_twostage_test/trainticket_test_execution.log`
- OpenAPI spec: `src/main/resources/My-Example/trainticket/merged_openapi_spec 1.yaml`

Detail tables and per-row CSVs are in `report.md` and the `d{1..10}_*.csv` files in this directory.
