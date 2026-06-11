# Input Quality Metric Scripts

Offline scripts that compute **D1–D10** of the input-quality framework from artefacts RESTest
already produces. **No re-run of the tool is required** — everything is mined from generated
test files, the execution log, LLM communication logs, and (optionally) Jaeger trace exports.

See `debug/inputs/input-quality-measurement-framework.md` for the full definitions.

## Layout

| File | Stage | Purpose |
|---|---|---|
| `mine_test_inputs.py` | 1a | Walks generated test files, extracts every (operation, parameter, value) triple. → `inputs.csv` |
| `mine_llm_log.py`     | 1b | Walks LLM communication logs, extracts (prompt-constraints, emitted value) pairs. → `llm_pairs.csv` |
| `mine_provenance.py`  | 1c | Walks the execution log to label each (param, value) by SMART_FETCH / LLM / SHARED_POOL / NEGATIVE. → `provenance.csv` |
| `mine_jaeger.py`      | 1d | Flattens a Jaeger trace export to (span, service, output_field, output_value). → `jaeger_outputs.csv` |
| `export_jaeger_traces.py` | 1d′ | Optional: pulls post-execution traces from a Jaeger UI directly. |
| `validate_d1.py`      | 2  | D1 — schema conformance against OpenAPI. |
| `validate_d2.py`      | 2  | D2 — IDL inter-parameter satisfaction (best-effort; N/A when no IDL declared). |
| `validate_d3.py`      | 2  | D3 — LLM hallucination rate against in-prompt constraints. |
| `validate_d4.py`      | 2  | D4 — Smart-Fetch hit rate (conservative + upper-bound). |
| `validate_d5.py`      | 2  | D5 — ID-resolvability rate against the Jaeger trace export. |
| `validate_d6.py`      | 2  | D6 — Chain resolution rate across multi-step sequences. |
| `validate_d7.py`      | 2  | D7 — Realism score against a curated entity oracle (offline; opt-in Wikidata online fallback). |
| `validate_d8.py`      | 2  | D8 — Pool Shannon entropy + Simpson diversity per parameter. |
| `validate_d9.py`      | 2  | D9 — Equivalence-partition coverage from OAS-derived classes. |
| `validate_d10.py`     | 2  | D10 — Negative-input fault-type purity (per fault label). |
| `generate_report.py`  | 3  | Aggregates D1–D10 into a markdown report. |
| `run_metrics.sh`      | -  | One-shot orchestrator. |
| `curated_idl.yaml`    | -  | Optional hand-written IDL for D2 (template included). |
| `realism_entities.txt`| -  | Curated D7 entity oracle (~700 entries). |
| `realism_oracle.py`   | -  | D7 entity-matching engine (exact + token + suffix-stripped + cache + online). |

## Quick start

```bash
# 1. Install deps (one-off)
pip install -r debug/inputs/scripts/requirements.txt

# 2. Run on the latest TrainTicket two-stage run
./debug/inputs/scripts/run_metrics.sh

# 3. Open the report
xdg-open debug/inputs/measurements/<RUN_ID>/report.md
```

`run_metrics.sh` defaults to:
- the most recent run dir under `src/test/java/trainticket_twostage_test/`
- the most recent log under `logs/llm-communications/`
- the OpenAPI spec at `src/main/resources/My-Example/trainticket/merged_openapi_spec 1.yaml`
- output directory `debug/inputs/measurements/<RUN_ID>/`

Override any of these via flags or env vars (see `run_metrics.sh --help`).

## Output

For run `<RUN_ID>` you get:

```
debug/inputs/measurements/<RUN_ID>/
├── inputs.csv               # one row per (operation, parameter, value) from generated tests
├── llm_pairs.csv            # one row per (constraints, response_value) from LLM logs
├── d1_per_row.csv           # SCR result per generated value
├── d1_per_param.csv         # SCR aggregated by parameter
├── d1_summary.json          # overall SCR scalar + breakdown
├── d3_per_row.csv           # LHR result per LLM value
├── d3_per_param.csv         # LHR aggregated by parameter
├── d3_summary.json          # overall LHR scalar + breakdown
├── d2_summary.json          # IDL satisfaction (or N/A)
└── report.md                # human-readable summary
```

## What's measured

- **D1 SCR** = `valid_inputs / total_inputs`, validated against OpenAPI schemas (type, format,
  enum, min/max, minLength/maxLength, pattern, required). Body parameters are validated as
  whole JSON payloads against the `requestBody.content[*].schema`. Query/path/header are
  validated against their `parameter.schema`. Filtered to *positive-only* variants by default
  (test methods named `test_positive_*`). See `validate_d1.py --help` for `--include-negatives`.
- **D2 IPD-SR** = `payloads_satisfying_all_idl / total_payloads`, evaluated against IDL
  declared in the OAS (`x-dependencies`) or in the optional `curated_idl.yaml`. Reports
  `N/A` when no IDL is available — the TrainTicket spec does not declare IDL, so this is the
  default unless you author `curated_idl.yaml`.
- **D3 LHR** = `values_violating_at_least_one_stated_constraint / total_LLM_values`, where
  *stated constraint* means a constraint written verbatim in the LLM prompt (`Type:`,
  `Format:`, `Enum:`, `Min:`, `Max:`, `MinLength:`, `MaxLength:`, `Pattern:`). Only
  positive-generation prompts are scored; negative-generation prompts (where the LLM is
  *asked* to violate constraints) are reported separately and excluded from LHR.

Thresholds (per `input-quality-measurement-framework.md`):

| Metric | Pass threshold |
|---|---|
| D1 SCR (positive) | ≥ 0.90 |
| D2 IPD-SR | ≥ 0.85 |
| D3 LHR | ≤ 0.20 |

## Re-running on a different run

```bash
./debug/inputs/scripts/run_metrics.sh \
    --run-dir src/test/java/trainticket_twostage_test/TrainTicketTwoStageTest_<TS> \
    --llm-log logs/llm-communications/llm-communication-<TS>.log \
    --oas      "src/main/resources/My-Example/trainticket/merged_openapi_spec 1.yaml" \
    --out      debug/inputs/measurements/<RUN_ID>
```

## What the report looks like (sample run)

A pilot run of `TrainTicketTwoStageTest_1777065076883` (28,910 mined input rows from 20
test classes; 5,969 LLM requests yielding 48,492 emitted values) produced:

| Metric | Score | Threshold | Status |
| --- | --- | --- | --- |
| D1 SCR (positive) | 90.61 % | ≥ 90 % | ✅ Pass |
| D2 IPD-SR | N/A — no IDL declared | ≥ 85 % | — |
| D3 LHR | 29.20 % | ≤ 20 % | ❌ Fail |

The D3 failure is dominated by 214 empty LLM responses across 767 scored values; only
10 emitted values violated a stated `type` constraint. The D1 result surfaces three
parameters where the OAS schema disagrees with what the generator produces
(`differenceMoney`, `accountId`, `distanceList` declared as `string` but observed as
numbers) — these are likely spec inconsistencies, exactly the kind of finding the
metric is designed to expose.

## Limitations (v1)

- **Path parameters** are skipped when the value is computed from a runtime variable
  (`/api/v1/orders/" + orderId + "/...`). Body, query, and header parameters cover the
  large majority of TrainTicket inputs.
- **D2** requires explicit IDL. TrainTicket's spec does not declare IDL extensions, so
  the default report shows N/A. Author `curated_idl.yaml` (template provided) for the
  operations whose IPD constraints you know.
- **OpenAPI 3.0 quirks** (`nullable`, `oneOf`, `allOf`) are handled minimally — the
  validator preprocesses `nullable: true` into `type: ["X", "null"]` and resolves `$ref`,
  but exotic schema combinators may misreport. Inspect `d1_per_row.csv` `error` column
  when in doubt.
- **TrainTicket spec quirk**: many `$ref: '#/components/requestBodies/api_X'` entries
  point at non-existent components — the actual key is `<service>_X`. The validator
  recovers via suffix-matching, biased toward the operation's `x-service-name`, but
  in rare cases a wrong-service body schema may be picked. Watch `d1_per_param.csv`
  for surprising 0% scores.

## File outputs

For run `<RUN_ID>`:

```
debug/inputs/measurements/<RUN_ID>/
├── inputs.csv          # mined parameter values (one row per generated input)
├── llm_pairs.csv       # mined LLM (constraints, value) pairs
├── d1_per_row.csv      # D1 result per generated input (with valid + error)
├── d1_per_param.csv    # D1 aggregated per parameter
├── d1_summary.json     # D1 scalar + breakdown
├── d2_per_payload.csv  # D2 per-payload IDL satisfaction (only when IDL is loaded)
├── d2_summary.json     # D2 scalar + breakdown (or N/A)
├── d3_per_row.csv      # D3 result per LLM value
├── d3_per_param.csv    # D3 aggregated per parameter
├── d3_summary.json     # D3 scalar + breakdown
└── report.md           # human-readable rollup
```

`measurements/` is gitignored — every run regenerates everything deterministically
from the source artefacts, so the outputs do not need to live in source control.
