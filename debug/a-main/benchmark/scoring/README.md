# Benchmark scoring harness (A6; Step-8 B-m4)

The SINGLE mechanical path from benchmark labels x per-tool verdict files to the E2
matched-recall table. Every arm — including the MIST column — is scored here; ad hoc
per-arm scoring is forbidden (completion-set plan, Phase B).

- `score_arms.py` — the scorer. Reads `../cases/*.json` (labels),
  `../e2-visibility-census.json` (A7), `../mist-column-census.json` (A5, the MIST
  column), and `verdicts/<arm>.json` for comparator arms. Emits
  `matched-recall-table.json` (per-arm x per-visibility-class cells; NOT_EVALUABLE
  buckets; the trace-invisible-by-construction N-vs-0 row; NO pooled headline recall).
- `verdicts/` — one JSON per comparator arm:
  `{"arm": str, "verdict_source": str, "cases": {case_id: "flag"|"no_flag"|"not_evaluable"}}`.
  The `verdict_source` must name the run/script of record (surrogate arms must say so).

**License:** Apache-2.0 (matches the benchmark's component map; Step 8).
**Rails:** matched-recall framing only; capture-concordant MIST cells never pool into a
headline (`provenance_class` marks them); surrogate arms are labeled as surrogates.
