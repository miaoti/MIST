# Input Quality Metrics — TrainTicketTwoStageTest_1777348134277

_Generated 2026-05-01T01:20:48Z_

## Source artefacts

- Generated tests: `/home/tingshuo_miao2/github/Rest/src/test/java/trainticket_twostage_test/TrainTicketTwoStageTest_1777348134277`
- LLM log: `/home/tingshuo_miao2/github/Rest/logs/llm-communications/llm-communication-20260427-223414.log`
- OpenAPI spec: `/home/tingshuo_miao2/github/Rest/src/main/resources/My-Example/trainticket/merged_openapi_spec 1.yaml`

## Summary

| Metric | Score | Threshold | Status |
| --- | --- | --- | --- |
| D1 SCR (positive) | **97.42%** | ≥ 90% | ✅ Pass |
| D2 IPD-SR | N/A — no IDL declared | ≥ 85% | — |
| D3 LHR | **1.42%** | ≤ 20% | ✅ Pass |
| D4 SFHR (conservative) | **45.53%** (upper 57.72%) | ≥ 60% | ❌ Fail |
| D5 IDR | **0.00%** | ≥ 40% | ❌ Fail |
| D6 CRR | **N/A** | ≥ 50% | ❌ Fail |
| D7 Realism | **26.47%** | ≥ 50% | ❌ Fail |

## D1 — Schema Conformance Rate

- **SCR (positive variants): 97.42%** (680 / 698 validated)
- Rows total: 698 (no-schema: 0, missing-op: 0, skipped negatives: 25769)
- Threshold: ≥ 90%  →  ✅ Pass

### Breakdown by test kind

| Kind | Total | Valid | SCR |
| --- | ---: | ---: | ---: |
| positive | 698 | 680 | 97.42% |

### Worst-performing parameters (≥ 3 rows)

| Operation | Parameter | Location | Total | Valid | SCR |
| --- | --- | --- | ---: | ---: | ---: |
| `PUT /api/v1/consignservice/consigns` | `accountId` | body | 20 | 7 | 35.00% |
| `POST /api/v1/orderOtherService/orderOther/refresh` | `state` | body | 17 | 12 | 70.59% |
| `POST /api/v1/orderOtherService/orderOther/refresh` | `boughtDateEnd` | body | 17 | 17 | 100.00% |
| `POST /api/v1/orderOtherService/orderOther/refresh` | `boughtDateStart` | body | 17 | 17 | 100.00% |
| `POST /api/v1/orderOtherService/orderOther/refresh` | `enableBoughtDateQuery` | body | 17 | 17 | 100.00% |
| `POST /api/v1/orderOtherService/orderOther/refresh` | `enableStateQuery` | body | 17 | 17 | 100.00% |
| `POST /api/v1/orderOtherService/orderOther/refresh` | `enableTravelDateQuery` | body | 17 | 17 | 100.00% |
| `POST /api/v1/orderOtherService/orderOther/refresh` | `loginId` | body | 17 | 17 | 100.00% |
| `POST /api/v1/orderOtherService/orderOther/refresh` | `travelDateEnd` | body | 17 | 17 | 100.00% |
| `POST /api/v1/orderOtherService/orderOther/refresh` | `travelDateStart` | body | 17 | 17 | 100.00% |

### Example violations

- `PUT /api/v1/consignservice/consigns` / `accountId` (body):
  - value `1234567890` → <root>: 1234567890 is not of type 'string'
  - value `1234567890` → <root>: 1234567890 is not of type 'string'
  - value `1234567890` → <root>: 1234567890 is not of type 'string'
- `POST /api/v1/orderOtherService/orderOther/refresh` / `state` (body):
  - value `FALLBACK_state_2` → <root>: 'FALLBACK_state_2' is not of type 'integer'
  - value `FALLBACK_state_3` → <root>: 'FALLBACK_state_3' is not of type 'integer'
  - value `FALLBACK_state_2` → <root>: 'FALLBACK_state_2' is not of type 'integer'

## D2 — Inter-Parameter-Dependency Satisfaction Rate

- **Status: N/A — no IDL declared**
- TrainTicket's OAS does not declare x-dependencies. To enable D2, copy debug/inputs/scripts/curated_idl.example.yaml to curated_idl.yaml and add rules.

To enable: copy `debug/inputs/scripts/curated_idl.example.yaml` to `curated_idl.yaml` (without the `.example`) and add rules.

## D3 — LLM Hallucination Rate

- **LHR: 1.42%** (11 / 777 actionable values)
- Definition: `hallucinated / (scored - abstained)` — abstentions are not hallucinations
- Total LLM values seen: 72499 (scored: 787; valid: 766; abstained: 10)
- Scored categories: diverse_gen, positive_gen, simple_gen
- Abstain rate: 1.27% (LLM emitted `NO_GOOD_MATCH` / `NO_VALUES_GENERATED` instead of a value)
- Threshold: ≤ 20%  →  ✅ Pass

### Violations by constraint type

| Constraint | Violations |
| --- | ---: |
| shape | 11 |

### Per-model breakdown

| Model | Total | Valid | Hallucinated | Abstained | LHR |
| --- | ---: | ---: | ---: | ---: | ---: |
| `qwen2.5-coder:14b` | 787 | 766 | 11 | 10 | 1.42% |

### Per prompt category

| Category | Total | Valid | Hallucinated | Abstained | LHR |
| --- | ---: | ---: | ---: | ---: | ---: |
| `bulk_extract` | 26 | 0 | 0 | 0 | 0.00% |
| `diverse_gen` | 64 | 45 | 11 | 8 | 19.64% |
| `extraction` | 16 | 0 | 0 | 0 | 0.00% |
| `negative_gen` | 610 | 0 | 0 | 0 | 0.00% |
| `other` | 71060 | 0 | 0 | 0 | 0.00% |
| `positive_gen` | 717 | 717 | 0 | 0 | 0.00% |
| `simple_gen` | 6 | 4 | 0 | 2 | 0.00% |

### Worst-performing parameters (≥ 3 actionable values)

| Parameter | Total | Hallucinated | Abstained | LHR |
| --- | ---: | ---: | ---: | ---: |
| `orderId` | 56 | 7 | 0 | 12.50% |
| `id` | 84 | 4 | 0 | 4.76% |
| `boughtDateEnd` | 15 | 0 | 0 | 0.00% |
| `boughtDateStart` | 15 | 0 | 0 | 0.00% |
| `enableBoughtDateQuery` | 18 | 0 | 0 | 0.00% |
| `enableStateQuery` | 18 | 0 | 0 | 0.00% |
| `enableTravelDateQuery` | 18 | 0 | 0 | 0.00% |
| `loginId` | 17 | 0 | 0 | 0.00% |
| `state` | 17 | 0 | 0 | 0.00% |
| `travelDateEnd` | 16 | 0 | 0 | 0.00% |

### Example violations

- `orderId`:
  - value `1a3b5c7d-8e9f-0ab1-cdef-2ghij3klmno` → shape: value has UUID-like shape but is not a valid UUID: '1a3b5c7d-8e9f-0ab1-cdef-2ghij3klmno'
  - value `b23c4d5e-f67g-h8i9-j0ka-lmnopqrstu` → shape: value has UUID-like shape but is not a valid UUID: 'b23c4d5e-f67g-h8i9-j0ka-lmnopqrstu'
  - value `c34d5e6f-g7h8-i9ja-k0lb-mnopqrstuv` → shape: value has UUID-like shape but is not a valid UUID: 'c34d5e6f-g7h8-i9ja-k0lb-mnopqrstuv'
- `id`:
  - value `3e4f5a6b-c7d8-e9f0-a1b2-c3d4e5f6g7h8` → shape: value has UUID-like shape but is not a valid UUID: '3e4f5a6b-c7d8-e9f0-a1b2-c3d4e5f6g7h8'
  - value `1a2b3c4d-5e6f-7a8b-9c0d-e1f2g3h4i5j6` → shape: value has UUID-like shape but is not a valid UUID: '1a2b3c4d-5e6f-7a8b-9c0d-e1f2g3h4i5j6'
  - value `2f4a6b8c-e9d0-c1b2-a3d4-efghijklmnop` → shape: value has UUID-like shape but is not a valid UUID: '2f4a6b8c-e9d0-c1b2-a3d4-efghijklmnop'

## D4 — Smart-Fetch Hit Rate

- **SFHR (conservative): 45.53%** (56 explicit smart-fetch / 123 ID-typed inputs)
- SFHR (upper bound, including unattributed pool draws): 57.72%
- ID-typed positive inputs: 123
- Threshold: ≥ 60%  →  ❌ Fail

**Coverage caveat.** RESTest's shared-pool-fill path does not log each pooled value's originating source. Values appearing only in 'Shared Pool (Step 1)' lines have ambiguous provenance. We report a conservative SFHR (SMART_FETCH-only) and an upper bound (SMART_FETCH + SHARED_POOL_DRAW).

### Classification of ID-typed inputs

| Classification | Count |
| --- | ---: |
| smart_fetch | 56 |
| shared_pool_draw | 15 |
| llm | 51 |
| negative | 0 |
| unknown | 1 |

### Worst-performing parameters (≥ 3 ID-typed rows)

| Parameter | Total | SmartFetch | SFHR (cons.) | SFHR (upper) |
| --- | ---: | ---: | ---: | ---: |
| `loginId` | 17 | 1 | 5.88% | 94.12% |
| `orderId` | 20 | 8 | 40.00% | 40.00% |
| `id` | 66 | 28 | 42.42% | 42.42% |
| `accountId` | 20 | 19 | 95.00% | 95.00% |

## D5 — ID-Resolvability Rate

- **IDR: 0.00%** (0 / 123 ID-typed values appeared as outputs in the Jaeger trace export)
- Distinct values observed in spans: 24
- Threshold: ≥ 40%  →  ❌ Fail

### Worst-performing parameters (≥ 3 ID-typed rows)

| Parameter | Total | Resolvable | IDR |
| --- | ---: | ---: | ---: |
| `loginId` | 17 | 0 | 0.00% |
| `id` | 66 | 0 | 0.00% |
| `accountId` | 20 | 0 | 0.00% |
| `orderId` | 20 | 0 | 0.00% |

### Example unresolvable values

- `loginId`: `bob_brown101`, `user123`, `grace_purple606`
- `id`: `train-fghij`, `train-12345`, `train-12345`
- `accountId`: `1234567890`, `````, `1234567890`
- `orderId`: `ORD123456789`, `ORD987654321`, `ORD123456789`

## D6 — Chain Resolution Rate

- **CRR: N/A** (0 / 0 multi-step sequences fully resolved)
- ID-input resolution rate (across all multi-step sequences): N/A (0 / 0)
- Single-step sequences excluded: 128
- Threshold: ≥ 50%  →  ❌ Fail

## D7 — Realism Score

- **Realism: 26.47%** (36 / 136 NLP-typed values matched a known entity)
- Oracle: `offline` (542 curated entries; 0 online lookups)
- Empty values: 0
- Threshold: ≥ 50%  →  ❌ Fail

### Lookup source breakdown

| Source | Count |
| --- | ---: |
| unknown | 100 |
| offline | 36 |

### Worst-performing parameters (≥ 3 NLP-typed rows)

| Parameter | Total | Realistic | Realism |
| --- | ---: | ---: | ---: |
| `name` | 46 | 0 | 0.00% |
| `startPlace` | 45 | 7 | 15.56% |
| `endPlace` | 45 | 29 | 64.44% |

### Example unrealistic values

- `name`: `Greenway Express`, `Silver Bullet Express`, `Express Train`
- `startPlace`: `Dallas/Fort Worth International Airport`, `Hartsfield-Jackson Atlanta International Airport`, `Minneapolis–Saint Paul International Airport`
- `endPlace`: `Los Angeles, CA`, `New York City`, `Houston, TX`

---

_See `debug/inputs/input-quality-measurement-framework.md` for the full definitions and citations._
