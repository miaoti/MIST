# Masked-Write / Data-Integrity Fault Benchmark — RELEASE STAGING (skeleton)

**STAGING ONLY.** This directory is the assembly RECIPE for the benchmark release repo
(E6). Nothing here is published; the fork-publication decision and any push are USER-side
(Step 8). Assembled FROM the committed members listed in `index.generated.json`
(hash-pinned; `MANIFEST.sha256`).

## What the release will contain (the E6 assembly, per Step 8)

- `cases/` — the 33 schema-validated fault cases (18 positive / 15 negative; 5 SUTs) +
  `fault-case.schema.json` + the adjudication rubric.
- `censuses/` — the MIST-column census (per-case verdicts + provenance + principled
  not-applicable adjudications), the trace-visibility census, the case↔bundle↔arm map.
- `scoring/` — the scoring harness (`score_arms.py`, Apache-2.0): benchmark labels ×
  per-tool verdict files → the per-visibility matched-recall table (NO pooled recall).
- `specs/` — the 6 OpenAPI specs (provenance HETEROGENEOUS, disclosed per
  `c2-license-audit.md`: authored-by-us sockshop/teastore/oteldemo; stock-upstream
  bookinfo; machine-generated trainticket; boutique carries no in-file license).
- `reproduction-census.json` — per-case executable-reproduction status
  (32/33 executable: mechanized injection 20 + stimulus-replay 12 + specified 1); the
  Step-8 sampled-reproduction review (k=5 re-runs + m=15 audits) runs against this.
- Rater-facing sidecars enter ONLY via the gated Step-5 seal decisions (the staged
  branches live in `../b4/rater-sidecars-staging/`).
- Large evidence (capture bundles, per-seed trees) ships BY HASH via Zenodo/OSF.

## Licenses

Case code Apache-2.0 · case data CC-BY-4.0 · scoring harness Apache-2.0 · MIST by
reference (LGPL), never vendored · specs per the license audit (above).

## Regenerating this staging

`python ../build_release_staging.py` (deterministic; existence-verified members;
fails loud on any missing member).
