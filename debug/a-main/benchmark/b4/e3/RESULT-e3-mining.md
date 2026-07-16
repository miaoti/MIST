# RESULT — E3 trigger-rate mining (completion-set wave, Phase A item A1)

**Date:** 2026-07-16 · Status: EXECUTED (offline; no tenant). Checklist Step-8 "E3 trigger
rate mined from E1/M-yield logs" — **re-scoped at plan rev 2 (A-B2): the "E1-era logs" do
not exist** (the Step-3b baseline grid was never run; superseded-by-MYC, disclosed); the
mined record = the EXISTING committed evidence (M-yield 6-SUT trees + TT-omnibus leg logs).

**Trigger (rev-2 pinned):** an oracle-check emission above INFO in the preserved per-seed
stdout logs, per oracle family per SUT. Miner: `mine_trigger_rates.py` (committed beside
this RESULT); artifact: `e3-trigger-rates.json`.

## Table (WARN+ oracle-family emissions; DESCRIPTIVE ONLY)

| source | logs | emissions |
|---|---|---|
| myc/sockshop | 10 | `data_integrity_armed` 10 · `oracle_on_banner` 10 · records 0 |
| myc/teastore, oteldemo, bookinfo, boutique | 10/3/3/3 | none (DI descoped / read-only / no committed triple — the pinned MYC condition) |
| ttomni/leg1 (paired-run logs) | 5 | `TIMEOUT_ABSENT` 5 — the paired legs' at-cap absences (the trace-gate-independence datum: paired FIRED while at TIMEOUT_ABSENT) |
| ttomni/leg3 (E5, all configs) | 31 | `TIMEOUT_ABSENT` 15 · `OBSERVED_COMPLETE_ABSENT` 5 (C1 observe+jaeger faults) · `OBSERVED_PRESENT` 10 (C1 controls et al.) — matches the E5 exact-4 OAT record |

## Rails + disclosures

1. **Descriptive pipeline telemetry only** — no yield/defect language (adjudication is
   rater-gated, Step 5); no cross-SUT pooling.
2. **mist.log was NOT preserved** (MYC disclosure 8b: Windows rollover rename fails) — the
   mined record is stdout; families that print only to mist.log are undercounted BY
   CONSTRUCTION, disclosed. The `trace_shape_verdict` family shows 0 everywhere, consistent
   with TraceShapeOracle being unwired in the pipeline (MYC disclosure 7).
3. leg1's scan covers its PAIRED-run logs (the dir's committed *.log set); the leg-1
   OBSERVE measurements (OBSERVED_COMPLETE_ABSENT 5/5) live in the leg-1 report + the E5
   C1 cells (mined above under leg3).
4. Consistency checks against the records of record: SS armed 10/10 + 0 `DataIntegrity[`
   records = the MYC corrected finding exactly; leg3's 5/10/15 split = the E5 uniform
   verdict table. No new claims — this table is provenance for the paper's
   oracle-behavior telemetry only.
