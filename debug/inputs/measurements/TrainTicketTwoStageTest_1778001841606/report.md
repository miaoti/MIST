# Input Quality Metrics — TrainTicketTwoStageTest_1778001841606

_Generated 2026-05-06T03:28:10Z_

## Source artefacts

- Generated tests: `src/test/java/trainticket_twostage_test/TrainTicketTwoStageTest_1778001841606`
- LLM log: `logs/llm-communications/llm-communication-20260505-122355.log`
- OpenAPI spec: `/home/tingshuo_miao2/github/Rest/src/main/resources/My-Example/trainticket/merged_openapi_spec 1.yaml`

## Summary

| Metric | Score | Threshold | Status |
| --- | --- | --- | --- |
| D1 SCR (positive) | **99.25%** | ≥ 90% | ✅ Pass |
| D2 IPD-SR | N/A — no IDL declared | ≥ 85% | — |
| D3 LHR | **N/A** | ≤ 20% | ❌ Fail |
| D4 SFHR (conservative) | **58.43%** (upper 100.00%) | ≥ 60% | ❌ Fail |
| D5 IDR | **0.00%** | ≥ 40% | ❌ Fail |
| D6 CRR | **N/A** | ≥ 50% | ❌ Fail |
| D7 Realism | **0.00%** | ≥ 50% | ❌ Fail |
| D8 Pool Diversity (H_norm / Simpson) | **0.3538 / 0.4393** | H_norm ≥ 50% | ❌ Fail |
| D9 Equivalence-Partition Coverage | **39.97%** | ≥ 50% | ❌ Fail |
| D10 NIFP (overall) | **83.50%** | per-type ≥ 95% | ❌ Fail |

## D1 — Schema Conformance Rate

- **SCR (positive variants): 99.25%** (659 / 664 validated)
- Rows total: 664 (no-schema: 0, missing-op: 0, skipped negatives: 17858, skipped fallback: 439)
- Threshold: ≥ 90%  →  ✅ Pass

### Breakdown by test kind

| Kind | Total | Valid | SCR |
| --- | ---: | ---: | ---: |
| positive | 664 | 659 | 99.25% |

### Worst-performing parameters (≥ 3 rows)

| Operation | Parameter | Location | Total | Valid | SCR |
| --- | --- | --- | ---: | ---: | ---: |
| `POST /api/v1/adminorderservice/adminorder` | `price` | body | 5 | 4 | 80.00% |
| `POST /api/v1/orderOtherService/orderOther/refresh` | `state` | body | 20 | 19 | 95.00% |
| `POST /api/v1/adminbasicservice/adminbasic/trains` | `confortClass` | body | 20 | 19 | 95.00% |
| `POST /api/v1/adminbasicservice/adminbasic/trains` | `economyClass` | body | 20 | 19 | 95.00% |
| `POST /api/v1/adminorderservice/adminorder` | `seatClass` | body | 20 | 19 | 95.00% |
| `POST /api/v1/orderOtherService/orderOther/refresh` | `enableBoughtDateQuery` | body | 20 | 20 | 100.00% |
| `POST /api/v1/orderOtherService/orderOther/refresh` | `enableStateQuery` | body | 20 | 20 | 100.00% |
| `POST /api/v1/orderOtherService/orderOther/refresh` | `enableTravelDateQuery` | body | 20 | 20 | 100.00% |
| `POST /api/v1/orderOtherService/orderOther/refresh` | `travelDateEnd` | body | 6 | 6 | 100.00% |
| `POST /api/v1/orderOtherService/orderOther/refresh` | `travelDateStart` | body | 3 | 3 | 100.00% |

### Example violations

- `POST /api/v1/adminorderservice/adminorder` / `price` (body):
  - value `0.38` → <root>: 0.38 is not of type 'string'
- `POST /api/v1/orderOtherService/orderOther/refresh` / `state` (body):
  - value `test976` → <root>: 'test976' is not of type 'integer'
- `POST /api/v1/adminbasicservice/adminbasic/trains` / `confortClass` (body):
  - value `test166` → <root>: 'test166' is not of type 'integer'
- `POST /api/v1/adminbasicservice/adminbasic/trains` / `economyClass` (body):
  - value `test261` → <root>: 'test261' is not of type 'integer'
- `POST /api/v1/adminorderservice/adminorder` / `seatClass` (body):
  - value `test763` → <root>: 'test763' is not of type 'integer'

## D2 — Inter-Parameter-Dependency Satisfaction Rate

- **Status: N/A — no IDL declared**
- TrainTicket's OAS does not declare x-dependencies. To enable D2, copy debug/inputs/scripts/curated_idl.example.yaml to curated_idl.yaml and add rules.

To enable: copy `debug/inputs/scripts/curated_idl.example.yaml` to `curated_idl.yaml` (without the `.example`) and add rules.

## D3 — LLM Hallucination Rate

- **LHR: N/A** (0 / 0 actionable values)
- Definition: `hallucinated / (scored - abstained)` — abstentions are not hallucinations
- Total LLM values seen: 13033 (scored: 159; valid: 0; abstained: 159)
- Scored categories: diverse_gen, positive_gen, simple_gen
- Abstain rate: 100.00% (LLM emitted `NO_GOOD_MATCH` / `NO_VALUES_GENERATED` instead of a value)
- Threshold: ≤ 20%  →  ❌ Fail

### Per-model breakdown

| Model | Total | Valid | Hallucinated | Abstained | LHR |
| --- | ---: | ---: | ---: | ---: | ---: |
| `deepseek-chat` | 159 | 0 | 0 | 159 | N/A |

### Per prompt category

| Category | Total | Valid | Hallucinated | Abstained | LHR |
| --- | ---: | ---: | ---: | ---: | ---: |
| `bulk_extract` | 54 | 0 | 0 | 0 | 0.00% |
| `diverse_gen` | 39 | 0 | 0 | 39 | N/A |
| `extraction` | 54 | 0 | 0 | 0 | 0.00% |
| `negative_gen` | 102 | 0 | 0 | 0 | 0.00% |
| `other` | 12664 | 0 | 0 | 0 | 0.00% |
| `positive_gen` | 83 | 0 | 0 | 83 | N/A |
| `simple_gen` | 37 | 0 | 0 | 37 | N/A |

## D4 — Smart-Fetch Hit Rate

- **SFHR (conservative): 58.43%** (104 explicit smart-fetch / 178 ID-typed inputs)
- SFHR (upper bound, including unattributed pool draws): 100.00%
- ID-typed positive inputs: 178
- Threshold: ≥ 60%  →  ❌ Fail

**Coverage caveat.** RESTest's shared-pool-fill path does not log each pooled value's originating source. Values appearing only in 'Shared Pool (Step 1)' lines have ambiguous provenance. We report a conservative SFHR (SMART_FETCH-only) and an upper bound (SMART_FETCH + SHARED_POOL_DRAW).

### Classification of ID-typed inputs

| Classification | Count |
| --- | ---: |
| smart_fetch | 104 |
| shared_pool_draw | 74 |
| llm | 0 |
| negative | 0 |
| unknown | 0 |

### Worst-performing parameters (≥ 3 ID-typed rows)

| Parameter | Total | SmartFetch | SFHR (cons.) | SFHR (upper) |
| --- | ---: | ---: | ---: | ---: |
| `loginId` | 35 | 16 | 45.71% | 100.00% |
| `accountId` | 40 | 22 | 55.00% | 100.00% |
| `id` | 83 | 46 | 55.42% | 100.00% |
| `orderId` | 20 | 20 | 100.00% | 100.00% |

## D5 — ID-Resolvability Rate

- **IDR: 0.00%** (0 / 178 ID-typed values appeared as outputs in the Jaeger trace export)
- Distinct values observed in spans: 24
- Threshold: ≥ 40%  →  ❌ Fail

### Worst-performing parameters (≥ 3 ID-typed rows)

| Parameter | Total | Resolvable | IDR |
| --- | ---: | ---: | ---: |
| `loginId` | 35 | 0 | 0.00% |
| `id` | 83 | 0 | 0.00% |
| `accountId` | 40 | 0 | 0.00% |
| `orderId` | 20 | 0 | 0.00% |

### Example unresolvable values

- `loginId`: `FALLBACK_loginId_11`, `FALLBACK_loginId_7`, `FALLBACK_loginId_3`
- `id`: `test576`, `0b23bd3e-876a-4af3-b920-c50a90c90b04`, `FALLBACK_id_5`
- `accountId`: `acc9976`, `test710`, `test710`
- `orderId`: `test947`, `test947`, `test947`

## D6 — Chain Resolution Rate

- **CRR: N/A** (0 / 0 multi-step sequences fully resolved)
- ID-input resolution rate (across all multi-step sequences): N/A (0 / 0)
- Single-step sequences excluded: 150
- Threshold: ≥ 50%  →  ❌ Fail

## D7 — Realism Score

- **Realism: 0.00%** (0 / 74 NLP-typed values matched a known entity)
- Oracle: `offline` (732 curated entries; 0 online lookups)
- Empty values: 0
- Threshold: ≥ 50%  →  ❌ Fail

### Lookup source breakdown

| Source | Count |
| --- | ---: |
| unknown | 74 |

### Worst-performing parameters (≥ 3 NLP-typed rows)

| Parameter | Total | Realistic | Realism |
| --- | ---: | ---: | ---: |
| `endPlace` | 10 | 0 | 0.00% |
| `startPlace` | 11 | 0 | 0.00% |
| `endStation` | 15 | 0 | 0.00% |
| `startStation` | 15 | 0 | 0.00% |
| `stationList` | 15 | 0 | 0.00% |
| `contactsName` | 8 | 0 | 0.00% |

### Example unrealistic values

- `endPlace`: `en2396`, `test935`, `test935`
- `startPlace`: `sta4603`, `test838`, `sta4603`
- `endStation`: `test94`, `test94`, `end1015`
- `startStation`: `test409`, `star2606`, `test409`
- `stationList`: `test739`, `test739`, `test739`

## D8 — Pool Shannon Entropy + Simpson Index

- **Mean H_norm: 0.3538** (Shannon entropy as fraction of log₂(pool size); see framework spec §D8)
- **Mean Simpson 1-D: 0.4393**
- Pools gated (≥ 3 rows): 49 / 67
- Rows kept: 664 (fallback-skipped: 439)
- Threshold: H_norm ≥ 50%  →  ❌ Fail

### Lowest-diversity pools (≥ 3 rows)

| Operation | Parameter | Total | Distinct | H_norm | Simpson |
| --- | --- | ---: | ---: | ---: | ---: |
| `POST /api/v1/adminorderservice/adminorder` | `from` | 3 | 1 | 0.0000 | 0.0000 |
| `POST /api/v1/adminorderservice/adminorder` | `travelTime` | 5 | 1 | 0.0000 | 0.0000 |
| `POST /api/v1/adminrouteservice/adminroute` | `distanceList` | 15 | 1 | 0.0000 | 0.0000 |
| `POST /api/v1/adminrouteservice/adminroute` | `loginId` | 15 | 1 | 0.0000 | 0.0000 |
| `POST /api/v1/orderOtherService/orderOther/refresh` | `travelDateStart` | 3 | 1 | 0.0000 | 0.0000 |
| `PUT /api/v1/adminbasicservice/adminbasic/trains` | `confortClass` | 4 | 1 | 0.0000 | 0.0000 |
| `PUT /api/v1/adminbasicservice/adminbasic/trains` | `economyClass` | 4 | 1 | 0.0000 | 0.0000 |
| `PUT /api/v1/adminbasicservice/adminbasic/trains` | `name` | 4 | 1 | 0.0000 | 0.0000 |
| `PUT /api/v1/consignservice/consigns` | `orderId` | 20 | 1 | 0.0000 | 0.0000 |
| `PUT /api/v1/consignservice/consigns` | `phone` | 20 | 1 | 0.0000 | 0.0000 |

## D9 — Equivalence-Partition Coverage

- **Mean EPC: 39.97%**
- Pools gated (≥ 3 rows): 49 / 67
- Rows kept: 664 (fallback-skipped: 439)
- Threshold: ≥ 50%  →  ❌ Fail

### Worst-covered pools (≥ 3 rows)

| Operation | Parameter | Total | Covered/Total | EPC | Missing classes |
| --- | --- | ---: | ---: | ---: | --- |
| `POST /api/v1/adminbasicservice/adminbasic/trains` | `id` | 4 | 1/4 | 25.00% | `empty`, `medium`, `long` |
| `POST /api/v1/adminorderservice/adminorder` | `boughtDate` | 5 | 1/4 | 25.00% | `empty`, `medium`, `long` |
| `POST /api/v1/adminorderservice/adminorder` | `contactsName` | 8 | 1/4 | 25.00% | `empty`, `medium`, `long` |
| `POST /api/v1/adminorderservice/adminorder` | `from` | 3 | 1/4 | 25.00% | `empty`, `short`, `long` |
| `POST /api/v1/adminorderservice/adminorder` | `price` | 5 | 1/4 | 25.00% | `empty`, `medium`, `long` |
| `POST /api/v1/adminorderservice/adminorder` | `to` | 5 | 1/4 | 25.00% | `empty`, `medium`, `long` |
| `POST /api/v1/adminorderservice/adminorder` | `trainNumber` | 6 | 1/4 | 25.00% | `empty`, `medium`, `long` |
| `POST /api/v1/adminorderservice/adminorder` | `travelTime` | 5 | 1/4 | 25.00% | `empty`, `medium`, `long` |
| `POST /api/v1/adminrouteservice/adminroute` | `distanceList` | 15 | 1/4 | 25.00% | `empty`, `medium`, `long` |
| `POST /api/v1/adminrouteservice/adminroute` | `endStation` | 15 | 1/4 | 25.00% | `empty`, `medium`, `long` |

## D10 — Negative-Input Fault-Type Purity

- **Overall NIFP: 83.50%** (1645 / 1970)
- Definition: `fraction of negative values that actually exhibit the labelled fault as a property of the value`
- Threshold: per-type ≥ 95%  →  ❌ Fail

### Per fault-type purity

| Fault label | Total | Pure | Schema-unbounded | NIFP | Pass (≥ 95%) |
| --- | ---: | ---: | ---: | ---: | --- |
| `EMPTY_INPUT` | 12 | 12 | 0 | 100.00% | ✅ |
| `NULL_INPUT` | 21 | 21 | 0 | 100.00% | ✅ |
| `OVERFLOW` | 246 | 226 | 0 | 91.87% | ❌ |
| `SEMANTIC_MISMATCH` | 354 | 294 | 0 | 83.05% | ❌ |
| `SPECIAL_CHARACTERS` | 882 | 686 | 0 | 77.78% | ❌ |
| `TYPE_MISMATCH` | 455 | 406 | 0 | 89.23% | ❌ |

### Example impure values

- `OVERFLOW`:
  - `state` = `2147483647` (POST /api/v1/orderOtherService/orderOther/refresh)
  - `state` = `-2147483648` (POST /api/v1/orderOtherService/orderOther/refresh)
  - `documentType` = `2147483647` (POST /api/v1/adminorderservice/adminorder)
- `SEMANTIC_MISMATCH`:
  - `state` = `test259` (POST /api/v1/orderOtherService/orderOther/refresh)
  - `state` = `x` (POST /api/v1/orderOtherService/orderOther/refresh)
  - `state` = `1` (POST /api/v1/orderOtherService/orderOther/refresh)
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
