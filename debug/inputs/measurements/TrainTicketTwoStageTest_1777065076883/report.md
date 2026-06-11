# Input Quality Metrics — TrainTicketTwoStageTest_1777065076883

_Generated 2026-04-27T18:53:01Z_

## Source artefacts

- Generated tests: `/home/tingshuo_miao2/github/Rest/src/test/java/trainticket_twostage_test/TrainTicketTwoStageTest_1777065076883`
- LLM log: `/home/tingshuo_miao2/github/Rest/logs/llm-communications/llm-communication-20260424-161112.log`
- OpenAPI spec: `/home/tingshuo_miao2/github/Rest/src/main/resources/My-Example/trainticket/merged_openapi_spec 1.yaml`

## Summary

| Metric | Score | Threshold | Status |
| --- | --- | --- | --- |
| D1 SCR (positive) | **90.61%** | ≥ 90% | ✅ Pass |
| D2 IPD-SR | N/A — no IDL declared | ≥ 85% | — |
| D3 LHR | **0.91%** | ≤ 20% | ✅ Pass |

## D1 — Schema Conformance Rate

- **SCR (positive variants): 90.61%** (1023 / 1129 validated)
- Rows total: 1129 (no-schema: 0, missing-op: 0, skipped negatives: 27781)
- Threshold: ≥ 90%  →  ✅ Pass

### Breakdown by test kind

| Kind | Total | Valid | SCR |
| --- | ---: | ---: | ---: |
| positive | 1129 | 1023 | 90.61% |

### Worst-performing parameters (≥ 3 rows)

| Operation | Parameter | Location | Total | Valid | SCR |
| --- | --- | --- | ---: | ---: | ---: |
| `POST /api/v1/adminorderservice/adminorder` | `differenceMoney` | body | 20 | 0 | 0.00% |
| `PUT /api/v1/consignservice/consigns` | `accountId` | body | 20 | 0 | 0.00% |
| `POST /api/v1/adminrouteservice/adminroute` | `distanceList` | body | 19 | 0 | 0.00% |
| `POST /api/v1/adminorderservice/adminorder` | `contactsDocumentNumber` | body | 20 | 4 | 20.00% |
| `POST /api/v1/adminorderservice/adminorder` | `price` | body | 20 | 5 | 25.00% |
| `POST /api/v1/adminorderservice/adminorder` | `seatClass` | body | 20 | 13 | 65.00% |
| `POST /api/v1/adminorderservice/adminorder` | `accountId` | body | 20 | 14 | 70.00% |
| `POST /api/v1/adminorderservice/adminorder` | `status` | body | 20 | 18 | 90.00% |
| `POST /api/v1/adminorderservice/adminorder` | `coachNumber` | body | 20 | 19 | 95.00% |
| `POST /api/v1/orderOtherService/orderOther/refresh` | `boughtDateEnd` | body | 18 | 18 | 100.00% |

### Example violations

- `POST /api/v1/adminorderservice/adminorder` / `differenceMoney` (body):
  - value `-2.00` → <root>: -2.0 is not of type 'string'
  - value `-15.00` → <root>: -15.0 is not of type 'string'
  - value `-2.00` → <root>: -2.0 is not of type 'string'
- `PUT /api/v1/consignservice/consigns` / `accountId` (body):
  - value `4445556667` → <root>: 4445556667 is not of type 'string'
  - value `1234567890` → <root>: 1234567890 is not of type 'string'
  - value `7778889990` → <root>: 7778889990 is not of type 'string'
- `POST /api/v1/adminrouteservice/adminroute` / `distanceList` (body):
  - value `12.5` → <root>: 12.5 is not of type 'string'
  - value `9.3` → <root>: 9.3 is not of type 'string'
  - value `12.5` → <root>: 12.5 is not of type 'string'
- `POST /api/v1/adminorderservice/adminorder` / `contactsDocumentNumber` (body):
  - value `666666666666` → <root>: 666666666666 is not of type 'string'
  - value `777777777777` → <root>: 777777777777 is not of type 'string'
  - value `777777777777` → <root>: 777777777777 is not of type 'string'
- `POST /api/v1/adminorderservice/adminorder` / `price` (body):
  - value `19.99` → <root>: 19.99 is not of type 'string'
  - value `25.45` → <root>: 25.45 is not of type 'string'
  - value `25.45` → <root>: 25.45 is not of type 'string'

## D2 — Inter-Parameter-Dependency Satisfaction Rate

- **Status: N/A — no IDL declared**
- TrainTicket's OAS does not declare x-dependencies. To enable D2, copy debug/inputs/scripts/curated_idl.example.yaml to curated_idl.yaml and add rules.

To enable: copy `debug/inputs/scripts/curated_idl.example.yaml` to `curated_idl.yaml` (without the `.example`) and add rules.

## D3 — LLM Hallucination Rate

- **LHR: 0.91%** (7 / 767 actionable values)
- Definition: `hallucinated / (scored - abstained)` — abstentions are not hallucinations
- Total LLM values seen: 48492 (scored: 776; valid: 760; abstained: 9)
- Scored categories: diverse_gen, positive_gen, simple_gen
- Abstain rate: 1.16% (LLM emitted `NO_GOOD_MATCH` / `NO_VALUES_GENERATED` instead of a value)
- Threshold: ≤ 20%  →  ✅ Pass

### Violations by constraint type

| Constraint | Violations |
| --- | ---: |
| type | 7 |

### Per-model breakdown

| Model | Total | Valid | Hallucinated | Abstained | LHR |
| --- | ---: | ---: | ---: | ---: | ---: |
| `qwen2.5-coder:14b` | 776 | 760 | 7 | 9 | 0.91% |

### Per prompt category

| Category | Total | Valid | Hallucinated | Abstained | LHR |
| --- | ---: | ---: | ---: | ---: | ---: |
| `bulk_extract` | 15 | 0 | 0 | 0 | 0.00% |
| `diverse_gen` | 48 | 34 | 7 | 7 | 17.07% |
| `extraction` | 12 | 0 | 0 | 0 | 0.00% |
| `negative_gen` | 489 | 0 | 0 | 0 | 0.00% |
| `other` | 47200 | 0 | 0 | 0 | 0.00% |
| `positive_gen` | 719 | 719 | 0 | 0 | 0.00% |
| `simple_gen` | 9 | 7 | 0 | 2 | 0.00% |

### Worst-performing parameters (≥ 3 actionable values)

| Parameter | Total | Hallucinated | Abstained | LHR |
| --- | ---: | ---: | ---: | ---: |
| `enableTravelDateQuery` | 28 | 7 | 0 | 25.00% |
| `boughtDateEnd` | 23 | 0 | 0 | 0.00% |
| `boughtDateStart` | 15 | 0 | 0 | 0.00% |
| `enableBoughtDateQuery` | 20 | 0 | 1 | 0.00% |
| `enableStateQuery` | 18 | 0 | 0 | 0.00% |
| `loginId` | 15 | 0 | 0 | 0.00% |
| `state` | 17 | 0 | 0 | 0.00% |
| `travelDateEnd` | 16 | 0 | 0 | 0.00% |
| `travelDateStart` | 22 | 0 | 0 | 0.00% |
| `orderId` | 58 | 0 | 0 | 0.00% |

### Example violations

- `enableTravelDateQuery`:
  - value `enabled` → type: not a boolean ('enabled')
  - value `disabled` → type: not a boolean ('disabled')
  - value `yes` → type: not a boolean ('yes')

---

_See `debug/inputs/input-quality-measurement-framework.md` for the full definitions and citations._
