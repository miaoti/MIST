# Input Quality Metrics — TrainTicketTwoStageTest_1778039778981

_Generated 2026-05-07T19:06:27Z_

## Source artefacts


## Summary

| Metric | Score | Threshold | Status |
| --- | --- | --- | --- |
| D1 SCR (positive) | **100.00%** | ≥ 90% | ✅ Pass |
| D2 IPD-SR | N/A — no IDL declared | ≥ 85% | — |
| D3 LHR | **0.00%** | ≤ 20% | ✅ Pass |
| D4 SFHR (conservative) | **47.03%** (upper 65.68%) | ≥ 60% | ❌ Fail |
| D5 IDR | **0.00%** | ≥ 40% | ❌ Fail |
| D6 CRR | **N/A** | ≥ 50% | ❌ Fail |
| D7 Realism | **92.72%** | ≥ 50% | ✅ Pass |
| D8 Pool Diversity (H_norm / Simpson) | **0.5572 / 0.7223** | H_norm ≥ 50% | ✅ Pass |
| D9 Equivalence-Partition Coverage | **44.36%** | ≥ 50% | ❌ Fail |
| D10 NIFP (overall) | **83.52%** | per-type ≥ 95% | ❌ Fail |

## D1 — Schema Conformance Rate

- **SCR (positive variants): 100.00%** (1991 / 1991 validated)
- Rows total: 1991 (no-schema: 0, missing-op: 0, skipped negatives: 21160, skipped fallback: 38)
- Threshold: ≥ 90%  →  ✅ Pass

### Breakdown by test kind

| Kind | Total | Valid | SCR |
| --- | ---: | ---: | ---: |
| positive | 1991 | 1991 | 100.00% |

### Worst-performing parameters (≥ 3 rows)

| Operation | Parameter | Location | Total | Valid | SCR |
| --- | --- | --- | ---: | ---: | ---: |
| `POST /api/v1/orderOtherService/orderOther/refresh` | `boughtDateEnd` | body | 20 | 20 | 100.00% |
| `POST /api/v1/orderOtherService/orderOther/refresh` | `boughtDateStart` | body | 18 | 18 | 100.00% |
| `POST /api/v1/orderOtherService/orderOther/refresh` | `enableBoughtDateQuery` | body | 20 | 20 | 100.00% |
| `POST /api/v1/orderOtherService/orderOther/refresh` | `enableStateQuery` | body | 20 | 20 | 100.00% |
| `POST /api/v1/orderOtherService/orderOther/refresh` | `enableTravelDateQuery` | body | 20 | 20 | 100.00% |
| `POST /api/v1/orderOtherService/orderOther/refresh` | `loginId` | body | 20 | 20 | 100.00% |
| `POST /api/v1/orderOtherService/orderOther/refresh` | `state` | body | 20 | 20 | 100.00% |
| `POST /api/v1/orderOtherService/orderOther/refresh` | `travelDateEnd` | body | 18 | 18 | 100.00% |
| `POST /api/v1/orderOtherService/orderOther/refresh` | `travelDateStart` | body | 19 | 19 | 100.00% |
| `PUT /api/v1/adminbasicservice/adminbasic/stations` | `id` | body | 49 | 49 | 100.00% |

### Example violations


## D2 — Inter-Parameter-Dependency Satisfaction Rate

- **Status: N/A — no IDL declared**
- TrainTicket's OAS does not declare x-dependencies. To enable D2, copy debug/inputs/scripts/curated_idl.example.yaml to curated_idl.yaml and add rules.

To enable: copy `debug/inputs/scripts/curated_idl.example.yaml` to `curated_idl.yaml` (without the `.example`) and add rules.

## D3 — LLM Hallucination Rate

- **LHR: 0.00%** (0 / 989 actionable values)
- Definition: `hallucinated / (scored - abstained)` — abstentions are not hallucinations
- Total LLM values seen: 41093 (scored: 989; valid: 989; abstained: 0)
- Scored categories: diverse_gen, positive_gen, simple_gen
- Abstain rate: 0.00% (LLM emitted `NO_GOOD_MATCH` / `NO_VALUES_GENERATED` instead of a value)
- Threshold: ≤ 20%  →  ✅ Pass

### Per-model breakdown

| Model | Total | Valid | Hallucinated | Abstained | LHR |
| --- | ---: | ---: | ---: | ---: | ---: |
| `deepseek-chat` | 989 | 989 | 0 | 0 | 0.00% |

### Per prompt category

| Category | Total | Valid | Hallucinated | Abstained | LHR |
| --- | ---: | ---: | ---: | ---: | ---: |
| `bulk_extract` | 349 | 0 | 0 | 0 | 0.00% |
| `diverse_gen` | 280 | 280 | 0 | 0 | 0.00% |
| `extraction` | 54 | 0 | 0 | 0 | 0.00% |
| `negative_gen` | 670 | 0 | 0 | 0 | 0.00% |
| `other` | 39031 | 0 | 0 | 0 | 0.00% |
| `positive_gen` | 689 | 689 | 0 | 0 | 0.00% |
| `simple_gen` | 20 | 20 | 0 | 0 | 0.00% |

### Worst-performing parameters (≥ 3 actionable values)

| Parameter | Total | Hallucinated | Abstained | LHR |
| --- | ---: | ---: | ---: | ---: |
| `boughtDateEnd` | 22 | 0 | 0 | 0.00% |
| `boughtDateStart` | 22 | 0 | 0 | 0.00% |
| `enableBoughtDateQuery` | 19 | 0 | 0 | 0.00% |
| `enableStateQuery` | 19 | 0 | 0 | 0.00% |
| `enableTravelDateQuery` | 19 | 0 | 0 | 0.00% |
| `loginId` | 19 | 0 | 0 | 0.00% |
| `state` | 13 | 0 | 0 | 0.00% |
| `travelDateEnd` | 21 | 0 | 0 | 0.00% |
| `travelDateStart` | 23 | 0 | 0 | 0.00% |
| `orderId` | 35 | 0 | 0 | 0.00% |

### Example violations


## D4 — Smart-Fetch Hit Rate

- **SFHR (conservative): 47.03%** (174 explicit smart-fetch / 370 ID-typed inputs)
- SFHR (upper bound, including unattributed pool draws): 65.68%
- ID-typed positive inputs: 370
- Threshold: ≥ 60%  →  ❌ Fail

**Coverage caveat.** RESTest's shared-pool-fill path does not log each pooled value's originating source. Values appearing only in 'Shared Pool (Step 1)' lines have ambiguous provenance. We report a conservative SFHR (SMART_FETCH-only) and an upper bound (SMART_FETCH + SHARED_POOL_DRAW).

### Classification of ID-typed inputs

| Classification | Count |
| --- | ---: |
| smart_fetch | 174 |
| shared_pool_draw | 69 |
| llm | 114 |
| negative | 10 |
| unknown | 3 |

### Worst-performing parameters (≥ 3 ID-typed rows)

| Parameter | Total | SmartFetch | SFHR (cons.) | SFHR (upper) |
| --- | ---: | ---: | ---: | ---: |
| `id` | 259 | 99 | 38.22% | 50.97% |
| `loginId` | 40 | 22 | 55.00% | 100.00% |
| `accountId` | 51 | 33 | 64.71% | 100.00% |
| `orderId` | 20 | 20 | 100.00% | 100.00% |

## D5 — ID-Resolvability Rate

- **IDR: 0.00%** (0 / 370 ID-typed values appeared as outputs in the Jaeger trace export)
- Distinct values observed in spans: 24
- Threshold: ≥ 40%  →  ❌ Fail

### Worst-performing parameters (≥ 3 ID-typed rows)

| Parameter | Total | Resolvable | IDR |
| --- | ---: | ---: | ---: |
| `loginId` | 40 | 0 | 0.00% |
| `id` | 259 | 0 | 0.00% |
| `accountId` | 51 | 0 | 0.00% |
| `orderId` | 20 | 0 | 0.00% |

### Example unresolvable values

- `loginId`: `support_agent`, `testuser01`, `guest`
- `id`: `8`, `a1b2c3d4-e5f6-7890-abcd-ef1234567890`, `existing-station-id-123`
- `accountId`: `user_001_abc123def456`, `7jh90mqol6DgaiEL`, `user_001_abc123def456`
- `orderId`: `ORD-20260505-001`, `ORD-20260505-001`, `ORD-20260505-001`

## D6 — Chain Resolution Rate

- **CRR: N/A** (0 / 0 multi-step sequences fully resolved)
- ID-input resolution rate (across all multi-step sequences): N/A (0 / 0)
- Single-step sequences excluded: 339
- Threshold: ≥ 50%  →  ❌ Fail

## D7 — Realism Score

- **Realism: 92.72%** (191 / 206 NLP-typed values matched a known entity)
- Oracle: `offline` (732 curated entries; 0 online lookups)
- Empty values: 0
- Threshold: ≥ 50%  →  ✅ Pass

### Lookup source breakdown

| Source | Count |
| --- | ---: |
| offline | 127 |
| token | 64 |
| unknown | 15 |

### Worst-performing parameters (≥ 3 NLP-typed rows)

| Parameter | Total | Realistic | Realism |
| --- | ---: | ---: | ---: |
| `contactsName` | 31 | 17 | 54.84% |
| `startStation` | 20 | 19 | 95.00% |
| `endPlace` | 57 | 57 | 100.00% |
| `startPlace` | 58 | 58 | 100.00% |
| `endStation` | 20 | 20 | 100.00% |
| `stationList` | 20 | 20 | 100.00% |

### Example unrealistic values

- `contactsName`: `M`, `M`, `T2sR4aOnMQsnhIFxu`
- `startStation`: `12345678901`

## D8 — Pool Shannon Entropy + Simpson Index

- **Mean H_norm: 0.5572** (Shannon entropy as fraction of log₂(pool size); see framework spec §D8)
- **Mean Simpson 1-D: 0.7223**
- Pools gated (≥ 3 rows): 68 / 68
- Rows kept: 1991 (fallback-skipped: 38)
- Threshold: H_norm ≥ 50%  →  ✅ Pass

### Lowest-diversity pools (≥ 3 rows)

| Operation | Parameter | Total | Distinct | H_norm | Simpson |
| --- | --- | ---: | ---: | ---: | ---: |
| `POST /api/v1/adminrouteservice/adminroute` | `loginId` | 20 | 1 | 0.0000 | 0.0000 |
| `PUT /api/v1/consignservice/consigns` | `orderId` | 20 | 1 | 0.0000 | 0.0000 |
| `PUT /api/v1/consignservice/consigns` | `phone` | 20 | 1 | 0.0000 | 0.0000 |
| `POST /api/v1/adminrouteservice/adminroute` | `distanceList` | 20 | 2 | 0.0663 | 0.0950 |
| `PUT /api/v1/consignservice/consigns` | `within` | 20 | 2 | 0.1877 | 0.3750 |
| `PUT /api/v1/consignservice/consigns` | `weight` | 20 | 4 | 0.1961 | 0.2700 |
| `PUT /api/v1/consignservice/consigns` | `to` | 20 | 2 | 0.2039 | 0.4200 |
| `POST /api/v1/orderOtherService/orderOther/refresh` | `enableStateQuery` | 20 | 2 | 0.2247 | 0.4800 |
| `POST /api/v1/orderOtherService/orderOther/refresh` | `enableBoughtDateQuery` | 20 | 2 | 0.2314 | 0.5000 |
| `POST /api/v1/orderOtherService/orderOther/refresh` | `enableTravelDateQuery` | 20 | 2 | 0.2314 | 0.5000 |

## D9 — Equivalence-Partition Coverage

- **Mean EPC: 44.36%**
- Pools gated (≥ 3 rows): 68 / 68
- Rows kept: 1991 (fallback-skipped: 38)
- Threshold: ≥ 50%  →  ❌ Fail

### Worst-covered pools (≥ 3 rows)

| Operation | Parameter | Total | Covered/Total | EPC | Missing classes |
| --- | --- | ---: | ---: | ---: | --- |
| `POST /api/v1/adminorderservice/adminorder` | `boughtDate` | 30 | 1/4 | 25.00% | `empty`, `short`, `long` |
| `POST /api/v1/adminorderservice/adminorder` | `contactsDocumentNumber` | 26 | 1/4 | 25.00% | `empty`, `short`, `long` |
| `POST /api/v1/adminorderservice/adminorder` | `differenceMoney` | 29 | 1/4 | 25.00% | `empty`, `medium`, `long` |
| `POST /api/v1/adminorderservice/adminorder` | `from` | 23 | 1/4 | 25.00% | `empty`, `medium`, `long` |
| `POST /api/v1/adminorderservice/adminorder` | `price` | 30 | 1/4 | 25.00% | `empty`, `medium`, `long` |
| `POST /api/v1/adminorderservice/adminorder` | `seatNumber` | 31 | 1/4 | 25.00% | `empty`, `medium`, `long` |
| `POST /api/v1/adminorderservice/adminorder` | `travelDate` | 30 | 1/4 | 25.00% | `empty`, `short`, `long` |
| `POST /api/v1/adminorderservice/adminorder` | `travelTime` | 31 | 1/4 | 25.00% | `empty`, `medium`, `long` |
| `POST /api/v1/adminrouteservice/adminroute` | `loginId` | 20 | 1/4 | 25.00% | `empty`, `short`, `long` |
| `POST /api/v1/orderOtherService/orderOther/refresh` | `boughtDateEnd` | 20 | 1/4 | 25.00% | `empty`, `short`, `long` |

## D10 — Negative-Input Fault-Type Purity

- **Overall NIFP: 83.52%** (1936 / 2318)
- Definition: `fraction of negative values that actually exhibit the labelled fault as a property of the value`
- Threshold: per-type ≥ 95%  →  ❌ Fail

### Per fault-type purity

| Fault label | Total | Pure | Schema-unbounded | NIFP | Pass (≥ 95%) |
| --- | ---: | ---: | ---: | ---: | --- |
| `EMPTY_INPUT` | 12 | 12 | 0 | 100.00% | ✅ |
| `NULL_INPUT` | 21 | 21 | 0 | 100.00% | ✅ |
| `OVERFLOW` | 246 | 226 | 0 | 91.87% | ❌ |
| `SEMANTIC_MISMATCH` | 702 | 585 | 0 | 83.33% | ❌ |
| `SPECIAL_CHARACTERS` | 882 | 686 | 0 | 77.78% | ❌ |
| `TYPE_MISMATCH` | 455 | 406 | 0 | 89.23% | ❌ |

### Example impure values

- `OVERFLOW`:
  - `state` = `2147483647` (POST /api/v1/orderOtherService/orderOther/refresh)
  - `state` = `-2147483648` (POST /api/v1/orderOtherService/orderOther/refresh)
  - `documentType` = `2147483647` (POST /api/v1/adminorderservice/adminorder)
- `SEMANTIC_MISMATCH`:
  - `state` = `-1` (POST /api/v1/orderOtherService/orderOther/refresh)
  - `state` = `0` (POST /api/v1/orderOtherService/orderOther/refresh)
  - `state` = `999999` (POST /api/v1/orderOtherService/orderOther/refresh)
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
