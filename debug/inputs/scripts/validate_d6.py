#!/usr/bin/env python3
"""
D6 — Chain Resolution Rate (CRR).

Group inputs.csv by (run_id, scenario, test_method). For each multi-step
sequence (≥2 steps), check that every ID-typed input parameter has a
matching producer earlier in the same sequence — i.e. some earlier step's
path produces a resource whose stem matches the consumer's parameter stem.

A sequence is "fully resolved" when every ID consumer is satisfied.

Output:
    d6_per_sequence.csv   one row per test_method with stats
    d6_summary.json       overall CRR + breakdown
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
from path_helpers import producer_stems


def main(argv: list[str]) -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--inputs", required=True, type=Path, help="inputs.csv")
    p.add_argument("--out-dir", required=True, type=Path)
    p.add_argument("--min-steps", type=int, default=2,
                   help="Minimum steps in a sequence to count it for CRR (default: 2). "
                        "Single-step sequences are trivially resolved.")
    args = p.parse_args(argv)

    args.out_dir.mkdir(parents=True, exist_ok=True)

    # First pass: load every row, group by sequence key.
    sequences: dict[tuple[str, str, str], list[dict]] = defaultdict(list)
    with args.inputs.open("r", encoding="utf-8", newline="") as fh:
        for row in csv.DictReader(fh):
            if (row.get("test_kind") or "").lower() == "negative":
                continue
            key = (
                row.get("run_id", ""),
                row.get("scenario", ""),
                row.get("test_method", ""),
            )
            sequences[key].append(row)

    counts = {"total": 0, "skipped_single_step": 0, "fully_resolved": 0,
              "partial": 0, "violations": 0, "id_inputs_total": 0,
              "id_inputs_resolved": 0}
    per_seq_rows = []

    for (run_id, scenario, method), rows in sorted(sequences.items()):
        # Group rows by step_idx and find each step's (method, path).
        by_step: dict[int, list[dict]] = defaultdict(list)
        for r in rows:
            try:
                idx = int(r.get("step_idx") or 0)
            except (TypeError, ValueError):
                idx = 0
            by_step[idx].append(r)

        step_indices = sorted(by_step.keys())
        if len(step_indices) < args.min_steps:
            counts["skipped_single_step"] += 1
            continue

        counts["total"] += 1
        # Walk steps in order, accumulating producer stems.
        producers_so_far: set[str] = set()
        seq_id_total = 0
        seq_id_resolved = 0
        first_violation: dict | None = None

        for idx in step_indices:
            step_rows = by_step[idx]
            # All rows in a single step share the same (method, path);
            # take from the first one.
            method_http = step_rows[0].get("http_method", "")
            path = step_rows[0].get("path", "")

            # Check ID consumers BEFORE adding this step's producers — a
            # step cannot be its own producer (the value isn't known until
            # the call returns).
            for r in step_rows:
                pname = r.get("parameter") or ""
                if not is_id_like(pname):
                    continue
                stem = normalise_id_stem(pname)
                if stem is None:
                    continue
                seq_id_total += 1
                # Bare 'id' / 'uuid' (stem == '') has no resource constraint
                # of its own — its semantics are "the id of whatever resource
                # the URL is pointing at". D6 resolves it whenever ANY earlier
                # step contributed a producer, since that earlier resource is
                # the contextual referent.
                if stem == "":
                    resolved = bool(producers_so_far)
                else:
                    resolved = stem in producers_so_far
                if resolved:
                    seq_id_resolved += 1
                else:
                    if first_violation is None:
                        first_violation = {
                            "step": idx,
                            "parameter": pname,
                            "stem": stem,
                            "available_producers": sorted(producers_so_far),
                        }

            producers_so_far.update(producer_stems(method_http, path))

        counts["id_inputs_total"] += seq_id_total
        counts["id_inputs_resolved"] += seq_id_resolved
        if seq_id_total == 0:
            # No ID consumers in the sequence — nothing to violate.
            classification = "fully_resolved"
            counts["fully_resolved"] += 1
        elif seq_id_resolved == seq_id_total:
            classification = "fully_resolved"
            counts["fully_resolved"] += 1
        else:
            classification = "partial"
            counts["partial"] += 1
            counts["violations"] += (seq_id_total - seq_id_resolved)

        per_seq_rows.append({
            "run_id": run_id,
            "scenario": scenario,
            "test_method": method,
            "steps": len(step_indices),
            "id_inputs": seq_id_total,
            "resolved": seq_id_resolved,
            "classification": classification,
            "first_violation": json.dumps(first_violation) if first_violation else "",
        })

    per_seq_path = args.out_dir / "d6_per_sequence.csv"
    with per_seq_path.open("w", encoding="utf-8", newline="") as fh:
        w = csv.DictWriter(fh, fieldnames=[
            "run_id", "scenario", "test_method", "steps", "id_inputs",
            "resolved", "classification", "first_violation",
        ], quoting=csv.QUOTE_MINIMAL)
        w.writeheader()
        for row in per_seq_rows:
            w.writerow(row)

    crr = (counts["fully_resolved"] / counts["total"]) if counts["total"] else None
    id_resolution_rate = (
        counts["id_inputs_resolved"] / counts["id_inputs_total"]
        if counts["id_inputs_total"] else None
    )

    summary = {
        "metric": "D6 Chain Resolution Rate",
        "definition": "fraction of multi-step sequences in which every ID consumer was produced by an earlier step of the same sequence",
        "min_steps_for_inclusion": args.min_steps,
        "sequences_considered": counts["total"],
        "sequences_skipped_single_step": counts["skipped_single_step"],
        "sequences_fully_resolved": counts["fully_resolved"],
        "sequences_partial": counts["partial"],
        "id_inputs_total": counts["id_inputs_total"],
        "id_inputs_resolved": counts["id_inputs_resolved"],
        "violations": counts["violations"],
        "crr": crr,
        "id_input_resolution_rate": id_resolution_rate,
        "crr_threshold_pass": (crr is not None) and (crr >= 0.50),
        "computed_at": datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z"),
    }
    (args.out_dir / "d6_summary.json").write_text(json.dumps(summary, indent=2))
    crr_str = f"{crr:.4f}" if crr is not None else "N/A"
    print(f"validate_d6: CRR={crr_str} "
          f"({counts['fully_resolved']}/{counts['total']} sequences fully resolved; "
          f"{counts['skipped_single_step']} single-step skipped; "
          f"{counts['id_inputs_resolved']}/{counts['id_inputs_total']} ID inputs resolved) "
          f"→ {args.out_dir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
