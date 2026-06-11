#!/usr/bin/env python3
"""
D2 — Inter-Parameter-Dependency Satisfaction Rate.

Two sources of IDL constraints, in priority order:
  1. The OAS itself: any operation containing an `x-dependencies` extension.
  2. A curated `curated_idl.yaml` next to this script (or via --curated).

The TrainTicket spec does not declare IDL, so the default report shows
status='N/A' unless a curated IDL file exists. The script never blocks the
pipeline — it always emits d2_summary.json (with status='N/A' when empty).

Each rule has a `rule` type:
    requires        — antecedent (key=value pairs) implies consequent (params present)
    only_one        — exactly one of the listed params present
    all_or_none     — either all of the listed params present, or none
    or              — at least one of the listed params present
    zero_or_one     — at most one of the listed params present
"""

from __future__ import annotations

import argparse
import csv
import json
import sys
from collections import defaultdict
from pathlib import Path

import yaml

from oas_helpers import load_oas


# ---------------------------------------------------------------------------
# IDL loading
# ---------------------------------------------------------------------------

def load_oas_dependencies(spec: dict) -> list[dict]:
    """Extract IDL rules from the OAS via the `x-dependencies` extension.
    The recognised shape is:
        x-dependencies:
          - rule: requires
            antecedent: {...}
            consequent: [...]
    placed under any operation object.
    """
    out: list[dict] = []
    for path, item in (spec.get("paths") or {}).items():
        if not isinstance(item, dict):
            continue
        for method in ("get", "post", "put", "delete", "patch", "head", "options"):
            op = item.get(method)
            if not isinstance(op, dict):
                continue
            dependencies = op.get("x-dependencies") or []
            for d in dependencies:
                rule = dict(d)
                rule["operation"] = f"{method.upper()} {path}"
                out.append(rule)
    return out


def load_curated_rules(curated_path: Path) -> list[dict]:
    if not curated_path.is_file():
        return []
    try:
        doc = yaml.safe_load(curated_path.read_text(encoding="utf-8")) or {}
    except yaml.YAMLError:
        return []
    rules = doc.get("rules") or []
    return [dict(r) for r in rules]


# ---------------------------------------------------------------------------
# Rule evaluation
# ---------------------------------------------------------------------------

def is_present(payload: dict, name: str) -> bool:
    if name not in payload:
        return False
    v = payload[name]
    if v is None:
        return False
    if isinstance(v, str) and not v.strip():
        return False
    if isinstance(v, (list, dict)) and not v:
        return False
    return True


def coerce_value(s: object) -> object:
    if isinstance(s, str):
        if s in ("true", "True"):
            return True
        if s in ("false", "False"):
            return False
        try:
            return int(s)
        except ValueError:
            try:
                return float(s)
            except ValueError:
                return s
    return s


def evaluate_rule(rule: dict, payload: dict) -> tuple[bool, str | None]:
    """Return (satisfied, violation_reason or None)."""
    rtype = (rule.get("rule") or "").lower()
    if rtype == "requires":
        antecedent = rule.get("antecedent") or {}
        # Antecedent fires only when ALL antecedent keys are present and equal.
        for k, expected in antecedent.items():
            if not is_present(payload, k):
                return True, None  # antecedent unmet → trivially satisfied
            if coerce_value(payload[k]) != coerce_value(expected):
                return True, None
        # Antecedent satisfied — every consequent param must be present.
        consequent = rule.get("consequent") or []
        missing = [p for p in consequent if not is_present(payload, p)]
        if missing:
            return False, f"requires {antecedent} → {consequent}, missing: {missing}"
        return True, None
    if rtype == "only_one":
        params = rule.get("parameters") or []
        present = [p for p in params if is_present(payload, p)]
        if len(present) != 1:
            return False, f"only_one of {params}, got {present}"
        return True, None
    if rtype == "all_or_none":
        params = rule.get("parameters") or []
        present = [p for p in params if is_present(payload, p)]
        if 0 < len(present) < len(params):
            return False, f"all_or_none of {params}, got {present}"
        return True, None
    if rtype == "or":
        params = rule.get("parameters") or []
        present = [p for p in params if is_present(payload, p)]
        if not present:
            return False, f"or {params}, none present"
        return True, None
    if rtype == "zero_or_one":
        params = rule.get("parameters") or []
        present = [p for p in params if is_present(payload, p)]
        if len(present) > 1:
            return False, f"zero_or_one of {params}, got {present}"
        return True, None
    return True, None  # unknown rule type → don't penalize


# ---------------------------------------------------------------------------
# Driver
# ---------------------------------------------------------------------------

def collect_payloads(inputs_csv: Path) -> dict[tuple[str, str, str, str], dict]:
    """Group input rows into payloads keyed by
        (scenario, test_method, step_idx, operation)
    where operation is "<METHOD> <path>". Each payload is { parameter: value }.
    Only body parameters are aggregated for IDL evaluation (which is the
    typical scope of IPD constraints).
    """
    payloads: dict[tuple[str, str, str, str], dict] = defaultdict(dict)
    with inputs_csv.open("r", encoding="utf-8", newline="") as fh:
        reader = csv.DictReader(fh)
        for row in reader:
            kind = row.get("test_kind", "").lower()
            if kind == "negative":
                continue
            if row.get("location") != "body":
                continue
            key = (
                row.get("scenario", ""),
                row.get("test_method", ""),
                row.get("step_idx", ""),
                f"{row.get('http_method','').upper()} {row.get('path','')}",
            )
            payloads[key][row["parameter"]] = row["value"]
    return payloads


def main(argv: list[str]) -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--inputs", required=True, type=Path, help="inputs.csv from mine_test_inputs.py")
    p.add_argument("--oas", required=True, type=Path, help="OpenAPI spec file")
    p.add_argument("--curated", type=Path, help="Optional curated IDL YAML (overrides default lookup)")
    p.add_argument("--out-dir", required=True, type=Path, help="Output directory")
    args = p.parse_args(argv)

    args.out_dir.mkdir(parents=True, exist_ok=True)

    spec = load_oas(args.oas)
    rules = load_oas_dependencies(spec)
    curated_path = args.curated or (Path(__file__).resolve().parent / "curated_idl.yaml")
    rules += load_curated_rules(curated_path)

    if not rules:
        summary = {
            "metric": "D2 IPD-SR",
            "status": "N/A — no IDL declared",
            "ipd_sr": None,
            "ipd_sr_threshold_pass": None,
            "rules_loaded": 0,
            "note": (
                "TrainTicket's OAS does not declare x-dependencies. To enable D2, copy "
                "debug/inputs/scripts/curated_idl.example.yaml to curated_idl.yaml and add rules."
            ),
        }
        (args.out_dir / "d2_summary.json").write_text(json.dumps(summary, indent=2))
        print("validate_d2: N/A (no IDL declared)")
        return 0

    payloads = collect_payloads(args.inputs)

    # Index rules by operation for fast lookup
    rules_by_op: dict[str, list[dict]] = defaultdict(list)
    for r in rules:
        rules_by_op[r.get("operation", "")].append(r)

    counts = {"payloads": 0, "fully_satisfied": 0, "evaluated": 0}
    per_op = defaultdict(lambda: {"total": 0, "satisfied": 0, "violations": []})
    per_rule_violations: dict[str, int] = defaultdict(int)

    per_row_path = args.out_dir / "d2_per_payload.csv"
    with per_row_path.open("w", encoding="utf-8", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(["scenario", "test_method", "step_idx", "operation",
                    "rules_evaluated", "rules_satisfied", "violations"])
        for (scen, method_name, step_idx, op), payload in payloads.items():
            counts["payloads"] += 1
            applicable = rules_by_op.get(op, [])
            if not applicable:
                continue  # no rules for this operation → not counted in IPD-SR
            counts["evaluated"] += 1
            satisfied = 0
            violations: list[str] = []
            # Coerce string values to typed (int/bool/float) for comparison.
            typed_payload = {k: coerce_value(v) for k, v in payload.items()}
            for r in applicable:
                ok, reason = evaluate_rule(r, typed_payload)
                if ok:
                    satisfied += 1
                else:
                    violations.append(reason or "unspecified")
                    rule_label = r.get("rule", "?") + ":" + json.dumps(
                        {k: r[k] for k in r if k not in ("operation",)}, sort_keys=True
                    )[:80]
                    per_rule_violations[rule_label] += 1
            all_ok = (satisfied == len(applicable))
            if all_ok:
                counts["fully_satisfied"] += 1
            per_op[op]["total"] += 1
            if all_ok:
                per_op[op]["satisfied"] += 1
            else:
                if len(per_op[op]["violations"]) < 3:
                    per_op[op]["violations"].append({"payload": payload, "violations": violations})
            w.writerow([scen, method_name, step_idx, op,
                        len(applicable), satisfied, " | ".join(violations)])

    ipd_sr = (counts["fully_satisfied"] / counts["evaluated"]) if counts["evaluated"] else None
    summary = {
        "metric": "D2 IPD-SR",
        "status": "computed" if rules else "N/A — no IDL declared",
        "rules_loaded": len(rules),
        "rules_from_oas": len([r for r in rules if "operation" in r and r.get("_source") != "curated"]),
        "rules_from_curated": curated_path.is_file(),
        "curated_path": str(curated_path),
        "payloads_total": counts["payloads"],
        "payloads_evaluated": counts["evaluated"],
        "payloads_fully_satisfied": counts["fully_satisfied"],
        "ipd_sr": ipd_sr,
        "ipd_sr_threshold_pass": (ipd_sr is not None) and (ipd_sr >= 0.85),
        "by_operation": {
            op: {
                "total": v["total"],
                "satisfied": v["satisfied"],
                "ipd_sr": (v["satisfied"] / v["total"]) if v["total"] else None,
                "violation_examples": v["violations"],
            } for op, v in per_op.items()
        },
        "rule_violation_counts": dict(sorted(per_rule_violations.items(), key=lambda x: -x[1])),
    }
    (args.out_dir / "d2_summary.json").write_text(json.dumps(summary, indent=2))
    ipd_str = f"{ipd_sr:.4f}" if ipd_sr is not None else "N/A"
    print(f"validate_d2: IPD-SR={ipd_str} ({counts['fully_satisfied']}/{counts['evaluated']}) "
          f"using {len(rules)} rule(s) → {args.out_dir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
