# Input Quality Metrics — TrainTicketTwoStageTest_1777780533352

_Generated 2026-05-05T05:06:12Z_

## Source artefacts

- Generated tests: `src/test/java/trainticket_twostage_test/TrainTicketTwoStageTest_1777780533352`
- LLM log: `/home/tingshuo_miao2/github/Rest/logs/llm-communications/llm-communication-20260502-225524.log`
- OpenAPI spec: `/home/tingshuo_miao2/github/Rest/src/main/resources/My-Example/trainticket/merged_openapi_spec 1.yaml`

## Summary

| Metric | Score | Threshold | Status |
| --- | --- | --- | --- |
| D1 SCR (positive) | **98.26%** | ≥ 90% | ✅ Pass |
| D2 IPD-SR | N/A — no IDL declared | ≥ 85% | — |
| D3 LHR | **0.00%** | ≤ 20% | ✅ Pass |
| D4 SFHR (conservative) | **75.91%** (upper 89.64%) | ≥ 60% | ✅ Pass |
| D5 IDR | **0.00%** | ≥ 40% | ❌ Fail |
| D6 CRR | **N/A** | ≥ 50% | ❌ Fail |
| D7 Realism | **96.88%** | ≥ 50% | ✅ Pass |
| D8 Pool Diversity (H_norm / Simpson) | **0.5525 / 0.7058** | H_norm ≥ 50% | ✅ Pass |
| D9 Equivalence-Partition Coverage | **43.14%** | ≥ 50% | ❌ Fail |
| D10 NIFP (overall) | **74.76%** | per-type ≥ 95% | ❌ Fail |

## D1 — Schema Conformance Rate

- **SCR (positive variants): 98.26%** (1807 / 1839 validated)
- Rows total: 1839 (no-schema: 0, missing-op: 0, skipped negatives: 30337, skipped fallback: 22)
- Threshold: ≥ 90%  →  ✅ Pass

### Breakdown by test kind

| Kind | Total | Valid | SCR |
| --- | ---: | ---: | ---: |
| positive | 1839 | 1807 | 98.26% |

### Worst-performing parameters (≥ 3 rows)

| Operation | Parameter | Location | Total | Valid | SCR |
| --- | --- | --- | ---: | ---: | ---: |
| `POST /api/v1/adminorderservice/adminorder` | `contactsDocumentNumber` | body | 20 | 3 | 15.00% |
| `POST /api/v1/adminorderservice/adminorder` | `differenceMoney` | body | 20 | 12 | 60.00% |
| `POST /api/v1/adminorderservice/adminorder` | `accountId` | body | 20 | 17 | 85.00% |
| `POST /api/v1/adminorderservice/adminorder` | `price` | body | 20 | 17 | 85.00% |
| `POST /api/v1/adminrouteservice/adminroute` | `startStation` | body | 20 | 19 | 95.00% |
| `POST /api/v1/orderOtherService/orderOther/refresh` | `boughtDateEnd` | body | 20 | 20 | 100.00% |
| `POST /api/v1/orderOtherService/orderOther/refresh` | `boughtDateStart` | body | 20 | 20 | 100.00% |
| `POST /api/v1/orderOtherService/orderOther/refresh` | `enableBoughtDateQuery` | body | 20 | 20 | 100.00% |
| `POST /api/v1/orderOtherService/orderOther/refresh` | `enableStateQuery` | body | 20 | 20 | 100.00% |
| `POST /api/v1/orderOtherService/orderOther/refresh` | `enableTravelDateQuery` | body | 20 | 20 | 100.00% |

### Example violations

- `POST /api/v1/adminorderservice/adminorder` / `contactsDocumentNumber` (body):
  - value `8901234567890123` → <root>: 8901234567890123 is not of type 'string'
  - value `4567891230` → <root>: 4567891230 is not of type 'string'
  - value `4321098765432109` → <root>: 4321098765432109 is not of type 'string'
- `POST /api/v1/adminorderservice/adminorder` / `differenceMoney` (body):
  - value `5.75` → <root>: 5.75 is not of type 'string'
  - value `-89.30` → <root>: -89.3 is not of type 'string'
  - value `-89.30` → <root>: -89.3 is not of type 'string'
- `POST /api/v1/adminorderservice/adminorder` / `accountId` (body):
  - value `1234567890` → <root>: 1234567890 is not of type 'string'
  - value `1234567890` → <root>: 1234567890 is not of type 'string'
  - value `1234567890` → <root>: 1234567890 is not of type 'string'
- `POST /api/v1/adminorderservice/adminorder` / `price` (body):
  - value `789.00` → <root>: 789.0 is not of type 'string'
  - value `321.09` → <root>: 321.09 is not of type 'string'
  - value `456.78` → <root>: 456.78 is not of type 'string'
- `POST /api/v1/adminrouteservice/adminroute` / `startStation` (body):
  - value `12345678901` → <root>: 12345678901 is not of type 'string'

## D2 — Inter-Parameter-Dependency Satisfaction Rate

- **Status: N/A — no IDL declared**
- TrainTicket's OAS does not declare x-dependencies. To enable D2, copy debug/inputs/scripts/curated_idl.example.yaml to curated_idl.yaml and add rules.

To enable: copy `debug/inputs/scripts/curated_idl.example.yaml` to `curated_idl.yaml` (without the `.example`) and add rules.

## D3 — LLM Hallucination Rate

- **LHR: 0.00%** (0 / 760 actionable values)
- Definition: `hallucinated / (scored - abstained)` — abstentions are not hallucinations
- Total LLM values seen: 80413 (scored: 767; valid: 760; abstained: 7)
- Scored categories: diverse_gen, positive_gen, simple_gen
- Abstain rate: 0.91% (LLM emitted `NO_GOOD_MATCH` / `NO_VALUES_GENERATED` instead of a value)
- Threshold: ≤ 20%  →  ✅ Pass

### Per-model breakdown

| Model | Total | Valid | Hallucinated | Abstained | LHR |
| --- | ---: | ---: | ---: | ---: | ---: |
| `qwen2.5-coder:14b` | 767 | 760 | 0 | 7 | 0.00% |

### Per prompt category

| Category | Total | Valid | Hallucinated | Abstained | LHR |
| --- | ---: | ---: | ---: | ---: | ---: |
| `bulk_extract` | 26 | 0 | 0 | 0 | 0.00% |
| `diverse_gen` | 56 | 50 | 0 | 6 | 0.00% |
| `extraction` | 18 | 0 | 0 | 0 | 0.00% |
| `negative_gen` | 468 | 0 | 0 | 0 | 0.00% |
| `other` | 79134 | 0 | 0 | 0 | 0.00% |
| `positive_gen` | 702 | 702 | 0 | 0 | 0.00% |
| `simple_gen` | 9 | 8 | 0 | 1 | 0.00% |

### Worst-performing parameters (≥ 3 actionable values)

| Parameter | Total | Hallucinated | Abstained | LHR |
| --- | ---: | ---: | ---: | ---: |
| `boughtDateEnd` | 16 | 0 | 0 | 0.00% |
| `boughtDateStart` | 15 | 0 | 0 | 0.00% |
| `enableBoughtDateQuery` | 18 | 0 | 0 | 0.00% |
| `enableStateQuery` | 18 | 0 | 0 | 0.00% |
| `enableTravelDateQuery` | 19 | 0 | 0 | 0.00% |
| `loginId` | 18 | 0 | 0 | 0.00% |
| `state` | 15 | 0 | 0 | 0.00% |
| `travelDateEnd` | 20 | 0 | 0 | 0.00% |
| `travelDateStart` | 18 | 0 | 1 | 0.00% |
| `orderId` | 49 | 0 | 0 | 0.00% |

### Example violations


## D4 — Smart-Fetch Hit Rate

- **SFHR (conservative): 75.91%** (271 explicit smart-fetch / 357 ID-typed inputs)
- SFHR (upper bound, including unattributed pool draws): 89.64%
- ID-typed positive inputs: 357
- Threshold: ≥ 60%  →  ✅ Pass

**Coverage caveat.** RESTest's shared-pool-fill path does not log each pooled value's originating source. Values appearing only in 'Shared Pool (Step 1)' lines have ambiguous provenance. We report a conservative SFHR (SMART_FETCH-only) and an upper bound (SMART_FETCH + SHARED_POOL_DRAW).

### Classification of ID-typed inputs

| Classification | Count |
| --- | ---: |
| smart_fetch | 271 |
| shared_pool_draw | 49 |
| llm | 37 |
| negative | 0 |
| unknown | 0 |

### Worst-performing parameters (≥ 3 ID-typed rows)

| Parameter | Total | SmartFetch | SFHR (cons.) | SFHR (upper) |
| --- | ---: | ---: | ---: | ---: |
| `loginId` | 40 | 8 | 20.00% | 57.50% |
| `accountId` | 40 | 32 | 80.00% | 100.00% |
| `id` | 257 | 212 | 82.49% | 92.61% |
| `orderId` | 20 | 19 | 95.00% | 95.00% |

## D5 — ID-Resolvability Rate

- **IDR: 0.00%** (0 / 357 ID-typed values appeared as outputs in the Jaeger trace export)
- Distinct values observed in spans: 24
- Threshold: ≥ 40%  →  ❌ Fail

### Worst-performing parameters (≥ 3 ID-typed rows)

| Parameter | Total | Resolvable | IDR |
| --- | ---: | ---: | ---: |
| `loginId` | 40 | 0 | 0.00% |
| `accountId` | 40 | 0 | 0.00% |
| `id` | 257 | 0 | 0.00% |
| `orderId` | 20 | 0 | 0.00% |

### Example unresolvable values

- `loginId`: `customer_1`, `guest123`, `user123`
- `accountId`: `customer_001`, `98779e1f-8cce-4435-9ff4-81411a9d9bd5`, `order_2026`
- `id`: `````, `train004`, `train010`
- `orderId`: `03e27662-7874-433f-81be-9d5d840d4955`, `be5032f0-335d-4bcb-8c8e-c2f3d9346bcb`, `ORD123456`

## D6 — Chain Resolution Rate

- **CRR: N/A** (0 / 0 multi-step sequences fully resolved)
- ID-input resolution rate (across all multi-step sequences): N/A (0 / 0)
- Single-step sequences excluded: 337
- Threshold: ≥ 50%  →  ❌ Fail

## D7 — Realism Score

- **Realism: 96.88%** (186 / 192 NLP-typed values matched a known entity)
- Oracle: `offline` (732 curated entries; 0 online lookups)
- Empty values: 0
- Threshold: ≥ 50%  →  ✅ Pass

### Lookup source breakdown

| Source | Count |
| --- | ---: |
| offline | 110 |
| token | 75 |
| unknown | 6 |
| suffix_stripped | 1 |

### Worst-performing parameters (≥ 3 NLP-typed rows)

| Parameter | Total | Realistic | Realism |
| --- | ---: | ---: | ---: |
| `startStation` | 20 | 17 | 85.00% |
| `endPlace` | 58 | 56 | 96.55% |
| `startPlace` | 56 | 55 | 98.21% |
| `contactsName` | 18 | 18 | 100.00% |
| `endStation` | 20 | 20 | 100.00% |
| `stationList` | 20 | 20 | 100.00% |

### Example unrealistic values

- `startStation`: `empirestatebuilding`, `12345678901`, `empirestatebuilding`
- `endPlace`: `New York City`, `New York City`
- `startPlace`: `New York City`

## D8 — Pool Shannon Entropy + Simpson Index

- **Mean H_norm: 0.5525** (Shannon entropy as fraction of log₂(pool size); see framework spec §D8)
- **Mean Simpson 1-D: 0.7058**
- Pools gated (≥ 3 rows): 68 / 68
- Rows kept: 1839 (fallback-skipped: 22)
- Threshold: H_norm ≥ 50%  →  ✅ Pass

### Lowest-diversity pools (≥ 3 rows)

| Operation | Parameter | Total | Distinct | H_norm | Simpson |
| --- | --- | ---: | ---: | ---: | ---: |
| `POST /api/v1/adminrouteservice/adminroute` | `stationList` | 20 | 3 | 0.1317 | 0.1850 |
| `PUT /api/v1/consignservice/consigns` | `within` | 20 | 2 | 0.1670 | 0.3200 |
| `PUT /api/v1/consignservice/consigns` | `id` | 20 | 4 | 0.1961 | 0.2700 |
| `POST /api/v1/orderOtherService/orderOther/refresh` | `enableStateQuery` | 20 | 2 | 0.2247 | 0.4800 |
| `POST /api/v1/orderOtherService/orderOther/refresh` | `enableBoughtDateQuery` | 20 | 2 | 0.2297 | 0.4950 |
| `POST /api/v1/orderOtherService/orderOther/refresh` | `enableTravelDateQuery` | 20 | 2 | 0.2297 | 0.4950 |
| `PUT /api/v1/adminbasicservice/adminbasic/trains` | `name` | 95 | 5 | 0.2558 | 0.5664 |
| `PUT /api/v1/adminbasicservice/adminbasic/trains` | `averageSpeed` | 95 | 5 | 0.2563 | 0.5669 |
| `PUT /api/v1/consignservice/consigns` | `handleDate` | 20 | 5 | 0.2596 | 0.3500 |
| `PUT /api/v1/consignservice/consigns` | `to` | 20 | 5 | 0.2596 | 0.3500 |

## D9 — Equivalence-Partition Coverage

- **Mean EPC: 43.14%**
- Pools gated (≥ 3 rows): 68 / 68
- Rows kept: 1839 (fallback-skipped: 22)
- Threshold: ≥ 50%  →  ❌ Fail

### Worst-covered pools (≥ 3 rows)

| Operation | Parameter | Total | Covered/Total | EPC | Missing classes |
| --- | --- | ---: | ---: | ---: | --- |
| `POST /api/v1/adminbasicservice/adminbasic/stations` | `name` | 19 | 1/4 | 25.00% | `empty`, `short`, `long` |
| `POST /api/v1/adminbasicservice/adminbasic/trains` | `name` | 19 | 1/4 | 25.00% | `empty`, `short`, `long` |
| `POST /api/v1/adminorderservice/adminorder` | `boughtDate` | 20 | 1/4 | 25.00% | `empty`, `short`, `long` |
| `POST /api/v1/adminorderservice/adminorder` | `differenceMoney` | 20 | 1/4 | 25.00% | `empty`, `medium`, `long` |
| `POST /api/v1/adminorderservice/adminorder` | `price` | 20 | 1/4 | 25.00% | `empty`, `medium`, `long` |
| `POST /api/v1/adminorderservice/adminorder` | `to` | 18 | 1/4 | 25.00% | `empty`, `short`, `long` |
| `POST /api/v1/adminorderservice/adminorder` | `trainNumber` | 20 | 1/4 | 25.00% | `empty`, `medium`, `long` |
| `POST /api/v1/adminorderservice/adminorder` | `travelDate` | 14 | 1/4 | 25.00% | `empty`, `short`, `long` |
| `POST /api/v1/adminrouteservice/adminroute` | `distanceList` | 20 | 1/4 | 25.00% | `empty`, `short`, `long` |
| `POST /api/v1/adminrouteservice/adminroute` | `endStation` | 20 | 1/4 | 25.00% | `empty`, `medium`, `long` |

## D10 — Negative-Input Fault-Type Purity

- **Overall NIFP: 74.76%** (2503 / 3348)
- Definition: `fraction of negative values that actually exhibit the labelled fault as a property of the value`
- Threshold: per-type ≥ 95%  →  ❌ Fail

### Per fault-type purity

| Fault label | Total | Pure | Schema-unbounded | NIFP | Pass (≥ 95%) |
| --- | ---: | ---: | ---: | ---: | --- |
| `BOUNDARY_VIOLATION` | 248 | 0 | 248 | 0.00% | ❌ |
| `EMPTY_INPUT` | 12 | 12 | 0 | 100.00% | ✅ |
| `NULL_INPUT` | 21 | 21 | 0 | 100.00% | ✅ |
| `OVERFLOW` | 254 | 226 | 0 | 88.98% | ❌ |
| `SEMANTIC_MISMATCH` | 1224 | 956 | 0 | 78.10% | ❌ |
| `SPECIAL_CHARACTERS` | 1134 | 882 | 0 | 77.78% | ❌ |
| `TYPE_MISMATCH` | 455 | 406 | 0 | 89.23% | ❌ |

_Note: 248 `BOUNDARY_VIOLATION`-labelled values target parameters whose OAS schema declares no `minimum`/`maximum`/`minLength`/`maxLength`. These cannot violate any boundary because none is declared — the impurity here is a **schema gap**, not a value-purity miss._

### Example impure values

- `OVERFLOW`:
  - `enableBoughtDateQuery` = `AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA` (POST /api/v1/orderOtherService/orderOther/refresh)
  - `enableBoughtDateQuery` = `2147483647` (POST /api/v1/orderOtherService/orderOther/refresh)
  - `enableStateQuery` = `AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA` (POST /api/v1/orderOtherService/orderOther/refresh)
- `SEMANTIC_MISMATCH`:
  - `enableBoughtDateQuery` = `truefalse` (POST /api/v1/orderOtherService/orderOther/refresh)
  - `enableBoughtDateQuery` = `01` (POST /api/v1/orderOtherService/orderOther/refresh)
  - `enableBoughtDateQuery` = `null` (POST /api/v1/orderOtherService/orderOther/refresh)
- `SPECIAL_CHARACTERS`:
  - `boughtDateStart` = `1; DELETE FROM users WHERE 1=1` (POST /api/v1/orderOtherService/orderOther/refresh)
  - `boughtDateStart` = `..\..\..\windows\system32\config\sam` (POST /api/v1/orderOtherService/orderOther/refresh)
  - `boughtDateStart` = `; ls -la` (POST /api/v1/orderOtherService/orderOther/refresh)
- `TYPE_MISMATCH`:
  - `boughtDateStart` = `{key=value}` (POST /api/v1/orderOtherService/orderOther/refresh)
  - `loginId` = `{key=value}` (POST /api/v1/orderOtherService/orderOther/refresh)
  - `boughtDateEnd` = `{key=value}` (POST /api/v1/orderOtherService/orderOther/refresh)

---

_See `debug/inputs/input-quality-measurement-framework.md` for the full definitions and citations._
