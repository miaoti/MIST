#!/usr/bin/env python3
"""
D8 — Pool Shannon Entropy + Simpson Diversity Index.

For each parameter, treat the multiset of *positive* generated values as a
discrete distribution and compute:

    H(P) = -sum_v f(v) * log2 f(v)              Shannon entropy (bits)
    S(P) = 1 - sum_v f(v)^2                     Simpson 1-D diversity

where f(v) = count(v) / |V_pos(P)|.

We also report H_norm = H / log2(|V_pos(P)|) when |V_pos(P)| ≥ 2 — the
fraction of the maximum-entropy ceiling. Per the framework spec (§D8,
"Reasonable thresholds"), the ceiling is `log_2 n` where `n` is the *total*
pool size: a 100-row pool that collapses to 2 values used 50/50 should
score H_norm ≈ 1/log2(100) ≈ 0.15, not 1.0. Normalising by distinct count
hides pool collapse and would let any binary-uniform pool pass the gate.

Pools with <3 generated rows are tabulated but excluded from the threshold
gate (entropy of a 1- or 2-element distribution is uninformative).

FALLBACK_* tool-side padding values are excluded — they are not generator
output and would misleadingly inflate apparent diversity.

Outputs:
    d8_per_param.csv    one row per parameter with the entropy + Simpson stats
    d8_summary.json     overall mean H_norm + mean Simpson + worst pools
"""

from __future__ import annotations

import argparse
import csv
import json
import math
import re
import sys
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path

FALLBACK_VALUE_RX = re.compile(r"^FALLBACK_[A-Za-z0-9_]+_\d+$")

# Minimum total pool size for normalised entropy; below this H_norm is None.
H_NORM_MIN_TOTAL = 2

# Pools with fewer than this many rows are excluded from the threshold gate.
GATE_MIN_ROWS = 3


def shannon_entropy(counts: list[int]) -> float:
    total = sum(counts)
    if total == 0:
        return 0.0
    h = 0.0
    for c in counts:
        if c <= 0:
            continue
        p = c / total
        h -= p * math.log2(p)
    return h


def simpson_diversity(counts: list[int]) -> float:
    total = sum(counts)
    if total == 0:
        return 0.0
    return 1.0 - sum((c / total) ** 2 for c in counts)


def main(argv: list[str]) -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--inputs", required=True, type=Path, help="inputs.csv from mine_test_inputs.py")
    p.add_argument("--out-dir", required=True, type=Path)
    p.add_argument("--include-negatives", action="store_true",
                   help="Include negative variants. Off by default — they are intentionally varied "
                        "and would inflate diversity.")
    args = p.parse_args(argv)
    args.out_dir.mkdir(parents=True, exist_ok=True)

    # Per (operation, parameter, location) value distribution.
    # We use the operation tuple to avoid collapsing distinct schemas that
    # happen to share a parameter name (e.g. `id` across services).
    pools: dict[tuple[str, str, str, str], Counter] = defaultdict(Counter)

    fallback_skipped = 0
    rows_total = 0
    rows_kept = 0

    with args.inputs.open("r", encoding="utf-8", newline="") as fh:
        reader = csv.DictReader(fh)
        for row in reader:
            rows_total += 1
            kind = (row.get("test_kind") or "").lower()
            if kind == "negative" and not args.include_negatives:
                continue
            value = (row.get("value") or "").strip()
            if FALLBACK_VALUE_RX.match(value):
                fallback_skipped += 1
                continue
            method = (row.get("http_method") or "").upper()
            path = row.get("path") or ""
            param = row.get("parameter") or ""
            loc = row.get("location") or ""
            pools[(method, path, param, loc)][value] += 1
            rows_kept += 1

    per_param_path = args.out_dir / "d8_per_param.csv"
    per_param_rows = []
    with per_param_path.open("w", encoding="utf-8", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(["http_method", "path", "parameter", "location",
                    "total_values", "distinct_values",
                    "shannon_bits", "shannon_normalised",
                    "simpson_diversity"])
        for (m, path, param, loc), counter in sorted(pools.items()):
            total = sum(counter.values())
            distinct = len(counter)
            counts_list = list(counter.values())
            h = shannon_entropy(counts_list)
            s = simpson_diversity(counts_list)
            h_norm = (h / math.log2(total)) if total >= H_NORM_MIN_TOTAL else None
            row_d = {
                "operation": f"{m} {path}",
                "parameter": param,
                "location": loc,
                "total": total,
                "distinct": distinct,
                "shannon": h,
                "shannon_normalised": h_norm,
                "simpson": s,
            }
            per_param_rows.append(row_d)
            w.writerow([m, path, param, loc, total, distinct,
                        f"{h:.4f}",
                        f"{h_norm:.4f}" if h_norm is not None else "",
                        f"{s:.4f}"])

    # Summary aggregates over pools that meet the gate-minimum row count.
    gated = [r for r in per_param_rows if r["total"] >= GATE_MIN_ROWS]
    norm_values = [r["shannon_normalised"] for r in gated if r["shannon_normalised"] is not None]
    simpson_values = [r["simpson"] for r in gated]

    mean_h_norm = (sum(norm_values) / len(norm_values)) if norm_values else None
    mean_simpson = (sum(simpson_values) / len(simpson_values)) if simpson_values else None

    # Worst pools: gated, lowest normalised entropy (fall back to simpson when undefined)
    worst = sorted(
        gated,
        key=lambda r: (
            r["shannon_normalised"] if r["shannon_normalised"] is not None else r["simpson"]
        ),
    )[:15]

    summary = {
        "metric": "D8 Pool Shannon Entropy + Simpson Index",
        "include_negatives": bool(args.include_negatives),
        "rows_total": rows_total,
        "rows_kept": rows_kept,
        "rows_fallback_skipped": fallback_skipped,
        "pools_total": len(per_param_rows),
        "pools_gated": len(gated),
        "mean_shannon_normalised": mean_h_norm,
        "mean_simpson": mean_simpson,
        "shannon_threshold_pass": (mean_h_norm is not None) and (mean_h_norm >= 0.50),
        "simpson_threshold_pass": (mean_simpson is not None) and (mean_simpson >= 0.50),
        "worst_pools": worst,
        "computed_at": datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z"),
    }
    (args.out_dir / "d8_summary.json").write_text(json.dumps(summary, indent=2))

    h_str = f"{mean_h_norm:.4f}" if mean_h_norm is not None else "N/A"
    s_str = f"{mean_simpson:.4f}" if mean_simpson is not None else "N/A"
    print(f"validate_d8: H_norm(mean)={h_str} Simpson(mean)={s_str} "
          f"({len(gated)} pools ≥ {GATE_MIN_ROWS} rows; "
          f"{fallback_skipped} fallback-skipped) → {args.out_dir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
