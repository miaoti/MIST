#!/usr/bin/env python3
"""
D5 — ID-Resolvability Rate (IDR).

Stronger, trace-grounded version of D4. For each ID-typed parameter value
generated for a positive variant, look up whether that value appears as an
output anywhere in the Jaeger trace export (`mine_jaeger.py` output).

Output:
    d5_per_row.csv      one row per ID input with resolvable=true|false
    d5_per_param.csv    aggregated per parameter
    d5_summary.json     overall IDR + breakdown
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


def load_observed_values(path: Path) -> set[str]:
    """Load every observed-as-output value from the Jaeger flat table.
    Returns a set for O(1) membership."""
    out: set[str] = set()
    if not path.is_file():
        return out
    with path.open("r", encoding="utf-8", newline="") as fh:
        for row in csv.DictReader(fh):
            v = (row.get("value") or "").strip()
            if v:
                out.add(v)
    return out


def main(argv: list[str]) -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--inputs", required=True, type=Path, help="inputs.csv")
    p.add_argument("--jaeger", required=True, type=Path,
                   help="Jaeger flat-output CSV (from mine_jaeger.py)")
    p.add_argument("--out-dir", required=True, type=Path)
    args = p.parse_args(argv)

    args.out_dir.mkdir(parents=True, exist_ok=True)
    observed = load_observed_values(args.jaeger)

    counts = {
        "total_inputs": 0,
        "id_typed": 0,
        "resolvable": 0,
        "unresolved": 0,
    }
    per_param: dict[str, dict] = defaultdict(
        lambda: {"total": 0, "resolvable": 0, "examples": []}
    )

    per_row_path = args.out_dir / "d5_per_row.csv"
    cols = [
        "scenario", "test_method", "step_idx", "http_method", "path",
        "parameter", "stem", "value", "resolvable",
    ]
    with args.inputs.open("r", encoding="utf-8", newline="") as in_fh, \
         per_row_path.open("w", encoding="utf-8", newline="") as out_fh:
        reader = csv.DictReader(in_fh)
        writer = csv.DictWriter(out_fh, fieldnames=cols, quoting=csv.QUOTE_MINIMAL)
        writer.writeheader()

        for row in reader:
            counts["total_inputs"] += 1
            if (row.get("test_kind") or "").lower() == "negative":
                continue
            param = row["parameter"]
            if not is_id_like(param):
                continue
            counts["id_typed"] += 1

            value = (row.get("value") or "").strip()
            resolvable = bool(value) and (value in observed)
            if resolvable:
                counts["resolvable"] += 1
            else:
                counts["unresolved"] += 1

            stem = normalise_id_stem(param) or ""
            agg = per_param[param]
            agg["total"] += 1
            if resolvable:
                agg["resolvable"] += 1
            elif len(agg["examples"]) < 3:
                agg["examples"].append(value)

            writer.writerow({
                **{k: row.get(k, "") for k in
                   ("scenario", "test_method", "step_idx",
                    "http_method", "path", "parameter", "value")},
                "stem": stem,
                "resolvable": "true" if resolvable else "false",
            })

    per_param_path = args.out_dir / "d5_per_param.csv"
    with per_param_path.open("w", encoding="utf-8", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(["parameter", "total", "resolvable", "idr"])
        for param, v in sorted(per_param.items()):
            idr = (v["resolvable"] / v["total"]) if v["total"] else 0.0
            w.writerow([param, v["total"], v["resolvable"], f"{idr:.4f}"])

    id_total = counts["id_typed"]
    idr = (counts["resolvable"] / id_total) if id_total else None
    summary = {
        "metric": "D5 ID-Resolvability Rate",
        "definition": "fraction of ID-typed positive-variant values that appear as outputs in the Jaeger trace export",
        "total_inputs_seen": counts["total_inputs"],
        "id_typed_inputs": id_total,
        "resolvable": counts["resolvable"],
        "unresolved": counts["unresolved"],
        "observed_values_in_traces": len(observed),
        "idr": idr,
        "idr_threshold_pass": (idr is not None) and (idr >= 0.40),
        "worst_parameters": sorted(
            (
                {
                    "parameter": k,
                    "total": v["total"],
                    "resolvable": v["resolvable"],
                    "idr": (v["resolvable"] / v["total"]) if v["total"] else 0.0,
                    "unresolvable_examples": v["examples"],
                }
                for k, v in per_param.items()
                if v["total"] >= 3
            ),
            key=lambda x: x["idr"],
        )[:15],
        "computed_at": datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z"),
    }
    (args.out_dir / "d5_summary.json").write_text(json.dumps(summary, indent=2))
    idr_str = f"{idr:.4f}" if idr is not None else "N/A"
    print(f"validate_d5: IDR={idr_str} "
          f"({counts['resolvable']}/{id_total} resolvable; "
          f"{len(observed)} distinct observed values in trace export) → {args.out_dir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
