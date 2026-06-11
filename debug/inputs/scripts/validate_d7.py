#!/usr/bin/env python3
"""
D7 — Realism Score (ARTE-style, offline by default).

For natural-language-typed parameters (city, station, place, country,
contactsName, ...) check whether the generated value matches a known
real-world entity in the curated offline list (or Wikidata when --online).

Output:
    d7_per_row.csv      one row per NLP-typed input with realistic=true|false
    d7_per_param.csv    aggregated per parameter
    d7_summary.json     overall realism + breakdown
"""

from __future__ import annotations

import argparse
import csv
import json
import re
import sys
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path

from realism_oracle import RealismOracle


# Heuristic: a parameter is "natural-language-typed" when its name suggests
# a real-world entity (place, station, country, contactsName, ...). This is the
# same heuristic ARTE uses to pick parameters for DBpedia lookup.
#
# Note: bare `name` is excluded — in TrainTicket-style domains it is a label
# (e.g. train name "Express Train"), not a real-world entity. Only qualified
# Name parameters (contactsName, cityName, ...) are treated as NLP-typed.
NLP_PATTERNS = (
    re.compile(r"station", re.IGNORECASE),
    re.compile(r"city",    re.IGNORECASE),
    re.compile(r"country", re.IGNORECASE),
    re.compile(r"^place$|place$", re.IGNORECASE),
    re.compile(r"[a-z]Name$", re.IGNORECASE),   # contactsName, firstName — must have a qualifier prefix
    re.compile(r"terminal", re.IGNORECASE),
    re.compile(r"junction", re.IGNORECASE),
)

# Skip parameters that look like NLP entities by name pattern but are not in
# practice (label/identifier fields, generic `name`, internal API metadata).
ANTI_PATTERNS = (
    re.compile(r"id$|Id$|ID$|UUID$", re.IGNORECASE),
    re.compile(r"^operationName$"),
    re.compile(r"parameter", re.IGNORECASE),
    re.compile(r"^name$", re.IGNORECASE),   # bare `name` is a label, not a real-world entity
)

# Tool-side padding values produced by typeAwareFallbackValue. They are not
# LLM/test output and would unfairly drag the realism denominator.
FALLBACK_VALUE_RX = re.compile(r"^FALLBACK_[A-Za-z0-9_]+_\d+$")


def is_nlp_param(param_name: str) -> bool:
    if not param_name:
        return False
    if any(rx.search(param_name) for rx in ANTI_PATTERNS):
        return False
    return any(rx.search(param_name) for rx in NLP_PATTERNS)


def main(argv: list[str]) -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--inputs", required=True, type=Path, help="inputs.csv")
    p.add_argument("--out-dir", required=True, type=Path)
    p.add_argument("--online", action="store_true",
                   help="Fall back to Wikidata search for values missing from the offline list. "
                        "Slower; cached.")
    p.add_argument("--cache", type=Path, default=None,
                   help="Optional JSON cache file for online lookups.")
    p.add_argument("--entities-file", type=Path, default=None,
                   help="Override the bundled curated entity file.")
    args = p.parse_args(argv)

    args.out_dir.mkdir(parents=True, exist_ok=True)

    if args.entities_file:
        from realism_oracle import load_offline_entities
        entities = load_offline_entities(args.entities_file)
        oracle = RealismOracle(entities=entities, cache_path=args.cache, online=args.online)
    else:
        oracle = RealismOracle(cache_path=args.cache, online=args.online)

    counts = {
        "total_inputs": 0,
        "nlp_typed": 0,
        "realistic": 0,
        "unrealistic": 0,
        "empty": 0,
        "fallback_skipped": 0,
        "by_source": defaultdict(int),
    }
    per_param: dict[str, dict] = defaultdict(
        lambda: {"total": 0, "realistic": 0, "unrealistic_examples": []}
    )

    per_row_path = args.out_dir / "d7_per_row.csv"
    cols = [
        "scenario", "test_method", "step_idx", "http_method", "path",
        "parameter", "value", "realistic", "source",
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
            param = row.get("parameter") or ""
            if not is_nlp_param(param):
                continue
            value = row.get("value") or ""
            # Skip tool-side padding values (typeAwareFallbackValue). These
            # are not LLM/test outputs and shouldn't be measured as realism.
            if FALLBACK_VALUE_RX.match(value.strip()):
                counts["fallback_skipped"] += 1
                continue
            counts["nlp_typed"] += 1
            if not value.strip():
                counts["empty"] += 1
                writer.writerow({
                    **{k: row.get(k, "") for k in
                       ("scenario", "test_method", "step_idx",
                        "http_method", "path", "parameter", "value")},
                    "realistic": "false",
                    "source": "empty",
                })
                continue

            ok, src = oracle.is_real(value)
            counts["by_source"][src] += 1
            if ok:
                counts["realistic"] += 1
            else:
                counts["unrealistic"] += 1

            agg = per_param[param]
            agg["total"] += 1
            if ok:
                agg["realistic"] += 1
            elif len(agg["unrealistic_examples"]) < 3:
                agg["unrealistic_examples"].append(value)

            writer.writerow({
                **{k: row.get(k, "") for k in
                   ("scenario", "test_method", "step_idx",
                    "http_method", "path", "parameter", "value")},
                "realistic": "true" if ok else "false",
                "source": src,
            })

    # Persist online cache if used
    oracle.save_cache()

    per_param_path = args.out_dir / "d7_per_param.csv"
    with per_param_path.open("w", encoding="utf-8", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(["parameter", "total", "realistic", "realism"])
        for param, v in sorted(per_param.items()):
            r = (v["realistic"] / v["total"]) if v["total"] else 0.0
            w.writerow([param, v["total"], v["realistic"], f"{r:.4f}"])

    nlp_total = counts["nlp_typed"]
    realism = (counts["realistic"] / nlp_total) if nlp_total else None

    summary = {
        "metric": "D7 Realism Score",
        "definition": "fraction of NLP-typed positive-variant values that match a known entity in the realism oracle",
        "oracle_mode": "online" if args.online else "offline",
        "entities_loaded": len(oracle.entities),
        "online_calls": oracle.online_calls,
        "online_hits": oracle.online_hits,
        "total_inputs_seen": counts["total_inputs"],
        "nlp_typed_inputs": nlp_total,
        "realistic": counts["realistic"],
        "unrealistic": counts["unrealistic"],
        "empty": counts["empty"],
        "fallback_skipped": counts["fallback_skipped"],
        "by_lookup_source": dict(counts["by_source"]),
        "realism": realism,
        "realism_threshold_pass": (realism is not None) and (realism >= 0.50),
        "worst_parameters": sorted(
            (
                {
                    "parameter": k,
                    "total": v["total"],
                    "realistic": v["realistic"],
                    "realism": (v["realistic"] / v["total"]) if v["total"] else 0.0,
                    "unrealistic_examples": v["unrealistic_examples"],
                }
                for k, v in per_param.items()
                if v["total"] >= 3
            ),
            key=lambda x: x["realism"],
        )[:15],
        "computed_at": datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z"),
    }
    (args.out_dir / "d7_summary.json").write_text(json.dumps(summary, indent=2))
    real_str = f"{realism:.4f}" if realism is not None else "N/A"
    print(f"validate_d7: Realism={real_str} "
          f"({counts['realistic']}/{nlp_total} match curated entities; "
          f"{counts['empty']} empty; "
          f"{counts['fallback_skipped']} fallback-skipped; "
          f"online_calls={oracle.online_calls}) → {args.out_dir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
