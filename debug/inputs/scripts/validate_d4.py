#!/usr/bin/env python3
"""
D4 — Smart-Fetch Hit Rate (SFHR).

Joins inputs.csv (mined per-variant inputs) with provenance.csv (mined from the
execution log) on (parameter, value). For every ID-typed parameter occurrence
in a positive variant, we check whether the value's recorded provenance is
SMART_FETCH (or any other "real upstream response" tier). Values whose origin
cannot be determined from the log are classified UNKNOWN — see the
"Coverage caveat" below.

Output:
    d4_per_row.csv      one row per (test_method, step, parameter, value)
                        with provenance + classification
    d4_per_param.csv    aggregated per parameter
    d4_summary.json     overall SFHR + breakdown

Coverage caveat
---------------
RESTest's pool-fill path ('generateSharedParameterPool') logs an aggregate
'LLM Pool → parameter X: N values added' line but does NOT log each pooled
value individually. Variants that draw from the pool produce 'Shared Pool
(Step 1) → … = <value>' lines, but the originating source of that pooled
value is not reachable from the log alone. Therefore:

  * SMART_FETCH       — explicit `Smart Fetch (...) → … = <value> ✅` line.
                        High confidence the value was fetched from a real
                        upstream service response.
  * SHARED_POOL_DRAW  — value drawn from the pool, but originating source
                        not traceable in this log (could be smart-fetch,
                        LLM, or FALLBACK padding).
  * LLM               — explicit LLM-fallback line.
  * UNKNOWN           — value produced by a code path the log does not mark
                        explicitly (most commonly LLM-pool-fill or padding).

For SFHR we report a conservative number (SMART_FETCH only) and a generous
upper-bound number (SMART_FETCH + SHARED_POOL_DRAW). The truth lies between.
To collapse the band, instrument 'generateSharedParameterPool' to log each
pooled value with its source.
"""

from __future__ import annotations

import argparse
import csv
import json
import sys
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path

from id_helpers import is_id_like, normalise_id_stem


SMART_FETCH_TIERS = {"SMART_FETCH", "TRACE_REPLAY", "JIT_BINDING", "DATA_PROVENANCE"}


def load_provenance(path: Path) -> dict[tuple[str, str], str]:
    out: dict[tuple[str, str], str] = {}
    with path.open("r", encoding="utf-8", newline="") as fh:
        for row in csv.DictReader(fh):
            out[(row["parameter"], row["value"])] = row["provenance"]
    return out


def load_llm_value_set(path: Path) -> tuple[set[tuple[str, str]], list[str]]:
    """Build (a) a `(parameter, value)` set and (b) a flat corpus of LLM
    response strings from llm_pairs.csv.

    The pair set credits clean per-parameter LLM responses (`positive_gen`,
    `simple_gen`). The corpus catches structured envelope responses
    emitted by TestCaseEnhancer-style flows where mine_llm_log.py records
    a JSON literal like `{"name": "id", "value": "existing-station-id"}`
    in a single row with empty `parameter` — the per-parameter join fails
    but the value is still verifiably an LLM emission. validate_d4 then
    falls back to substring containment.
    """
    pairs: set[tuple[str, str]] = set()
    corpus: list[str] = []
    if not path or not path.is_file():
        return pairs, corpus
    try:
        with path.open("r", encoding="utf-8", newline="") as fh:
            for row in csv.DictReader(fh):
                p = (row.get("parameter") or "").strip()
                v = (row.get("value") or "").strip()
                if not v:
                    continue
                if p:
                    pairs.add((p, v))
                corpus.append(v)
    except (OSError, csv.Error):
        pass
    return pairs, corpus


def main(argv: list[str]) -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--inputs", required=True, type=Path, help="inputs.csv")
    p.add_argument("--provenance", required=True, type=Path, help="provenance.csv")
    p.add_argument("--llm-pairs", type=Path, default=None,
                   help="Optional llm_pairs.csv from mine_llm_log.py; lets D4 credit "
                        "LLM-direct values that bypass SmartInputFetcher's logging.")
    p.add_argument("--out-dir", required=True, type=Path)
    p.add_argument("--include-negatives", action="store_true",
                   help="Also score ID-typed parameters in negative variants "
                        "(off by default — negative variants intentionally use "
                        "fault values, not smart-fetched IDs).")
    args = p.parse_args(argv)

    args.out_dir.mkdir(parents=True, exist_ok=True)
    prov = load_provenance(args.provenance)
    llm_pairs, llm_corpus = (
        load_llm_value_set(args.llm_pairs) if args.llm_pairs else (set(), [])
    )
    # Joined string for fast substring tests against envelope-wrapped values.
    # We add a sentinel between entries so a value can't span two log rows.
    llm_corpus_blob = "\n␞\n".join(llm_corpus) if llm_corpus else ""

    # Counters
    counts = {
        "total_inputs": 0,
        "id_typed": 0,
        "smart_fetch": 0,
        "shared_pool_draw": 0,
        "llm": 0,
        "negative": 0,
        "unknown": 0,
    }
    per_param: dict[str, dict] = defaultdict(
        lambda: {"total": 0, "smart_fetch": 0, "pool_or_smart": 0, "examples": []}
    )

    per_row_path = args.out_dir / "d4_per_row.csv"
    cols = [
        "scenario", "test_method", "test_kind", "step_idx",
        "http_method", "path", "parameter", "stem", "value",
        "provenance", "classification",
    ]
    with args.inputs.open("r", encoding="utf-8", newline="") as in_fh, \
         per_row_path.open("w", encoding="utf-8", newline="") as out_fh:
        reader = csv.DictReader(in_fh)
        writer = csv.DictWriter(out_fh, fieldnames=cols, quoting=csv.QUOTE_MINIMAL)
        writer.writeheader()

        for row in reader:
            counts["total_inputs"] += 1
            kind = (row.get("test_kind") or "").lower()
            if kind == "negative" and not args.include_negatives:
                continue
            param = row["parameter"]
            if not is_id_like(param):
                continue
            counts["id_typed"] += 1

            value = row["value"]
            provenance = prov.get((param, value), "UNKNOWN")
            # If exec-log provenance miss but the value appears in the LLM
            # communication log — either as a clean (param, value) pair or
            # embedded in an envelope-wrapped response — credit it as LLM.
            if provenance == "UNKNOWN":
                if (param, value) in llm_pairs:
                    provenance = "LLM"
                elif (
                    llm_corpus_blob
                    and len(value) >= 4  # avoid trivial single-char matches
                    and value in llm_corpus_blob
                ):
                    provenance = "LLM"
            if provenance in SMART_FETCH_TIERS:
                cls = "smart_fetch"
                counts["smart_fetch"] += 1
            elif provenance == "SHARED_POOL_DRAW":
                cls = "shared_pool_draw"
                counts["shared_pool_draw"] += 1
            elif provenance == "LLM":
                cls = "llm"
                counts["llm"] += 1
            elif provenance == "NEGATIVE_FAULT":
                cls = "negative"
                counts["negative"] += 1
            else:
                cls = "unknown"
                counts["unknown"] += 1

            stem = normalise_id_stem(param) or ""
            agg = per_param[param]
            agg["total"] += 1
            if cls == "smart_fetch":
                agg["smart_fetch"] += 1
            if cls in ("smart_fetch", "shared_pool_draw"):
                agg["pool_or_smart"] += 1
            if len(agg["examples"]) < 3 and cls != "smart_fetch":
                agg["examples"].append({"value": value, "provenance": provenance})

            writer.writerow({
                **{k: row.get(k, "") for k in
                   ("scenario", "test_method", "test_kind", "step_idx",
                    "http_method", "path", "parameter", "value")},
                "stem": stem,
                "provenance": provenance,
                "classification": cls,
            })

    # ----- Aggregations -----
    per_param_path = args.out_dir / "d4_per_param.csv"
    with per_param_path.open("w", encoding="utf-8", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(["parameter", "total", "smart_fetch", "pool_or_smart",
                    "sfhr_conservative", "sfhr_upper"])
        for param, v in sorted(per_param.items()):
            sfhr_low = (v["smart_fetch"] / v["total"]) if v["total"] else 0.0
            sfhr_up = (v["pool_or_smart"] / v["total"]) if v["total"] else 0.0
            w.writerow([param, v["total"], v["smart_fetch"], v["pool_or_smart"],
                        f"{sfhr_low:.4f}", f"{sfhr_up:.4f}"])

    id_total = counts["id_typed"]
    sfhr_conservative = (counts["smart_fetch"] / id_total) if id_total else None
    # Upper bound: assume every shared-pool draw originated from smart fetch.
    sfhr_upper = ((counts["smart_fetch"] + counts["shared_pool_draw"]) / id_total) if id_total else None

    summary = {
        "metric": "D4 Smart-Fetch Hit Rate",
        "definition": "fraction of ID-typed positive-variant inputs whose value came from a real upstream response",
        "total_inputs_seen": counts["total_inputs"],
        "id_typed_inputs": id_total,
        "by_classification": {
            "smart_fetch": counts["smart_fetch"],
            "shared_pool_draw": counts["shared_pool_draw"],
            "llm": counts["llm"],
            "negative": counts["negative"],
            "unknown": counts["unknown"],
        },
        "sfhr_conservative": sfhr_conservative,
        "sfhr_upper": sfhr_upper,
        "sfhr_threshold_pass": (sfhr_conservative is not None) and (sfhr_conservative >= 0.60),
        "coverage_caveat": (
            "RESTest's shared-pool-fill path does not log each pooled value's "
            "originating source. Values appearing only in 'Shared Pool (Step 1)' "
            "lines have ambiguous provenance. We report a conservative SFHR "
            "(SMART_FETCH-only) and an upper bound (SMART_FETCH + SHARED_POOL_DRAW)."
        ),
        "worst_parameters": sorted(
            (
                {
                    "parameter": k,
                    "total": v["total"],
                    "smart_fetch": v["smart_fetch"],
                    "sfhr_conservative": (v["smart_fetch"] / v["total"]) if v["total"] else 0.0,
                    "sfhr_upper": (v["pool_or_smart"] / v["total"]) if v["total"] else 0.0,
                    "examples": v["examples"],
                }
                for k, v in per_param.items()
                if v["total"] >= 3
            ),
            key=lambda x: x["sfhr_conservative"],
        )[:15],
        "computed_at": datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z"),
    }
    (args.out_dir / "d4_summary.json").write_text(json.dumps(summary, indent=2))
    low = f"{sfhr_conservative:.4f}" if sfhr_conservative is not None else "N/A"
    up = f"{sfhr_upper:.4f}" if sfhr_upper is not None else "N/A"
    print(f"validate_d4: SFHR conservative={low}, upper={up} "
          f"({counts['smart_fetch']}/{id_total} explicit smart-fetch; "
          f"+{counts['shared_pool_draw']} pool-draw, "
          f"{counts['unknown']} unknown) → {args.out_dir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
