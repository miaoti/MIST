#!/usr/bin/env python3
"""
D9 — Equivalence-Partition Coverage (EPC).

For each parameter, the schema implies a small set of canonical equivalence
classes. We map each generated *positive* value to one class and report
the fraction of classes that received at least one value.

Class taxonomy by schema type:

    enum        each declared enum literal is its own class
    boolean     {true, false}
    integer     [zero, positive_in_range, negative_in_range, at_min, at_max,
                 below_min (out-of-range), above_max (out-of-range)]
                Out-of-range classes only count as "covered" when they are
                actually achievable for the schema (i.e. the range is
                bounded). When unbounded we collapse to {zero, positive,
                negative}.
    number      same as integer, plus:
                  - includes a {fractional} class (non-integer numeric)
    string      [empty, format-conforming, format-violating,
                 short, medium, long, at_minLength, at_maxLength]
                Short/medium/long apply when no length constraints; the
                length-bound classes apply when they are.
    array       [empty, singleton, multi]
    object      single class (object presence — no canonical partition without
                a per-property treatment)

Out-of-range classes are *intentional* — covering them means the positive
generator emitted edge values, not that boundaries were violated. (Negative
variants are excluded by default; the test_kind=negative path is the place
that *should* be exercising boundary-violation.)

FALLBACK_* values are excluded from the denominator for the same reason as
in D1/D7/D8 — they are tool-side padding, not generator output.

Outputs:
    d9_per_param.csv    one row per (operation, parameter, location): total,
                        classes_total, classes_covered, epc, missing classes
    d9_summary.json     mean EPC, threshold pass, worst pools
"""

from __future__ import annotations

import argparse
import csv
import json
import math
import re
import sys
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path

from oas_helpers import (
    get_operation,
    load_oas,
    parameter_schema,
    request_body_schema,
)

FALLBACK_VALUE_RX = re.compile(r"^FALLBACK_[A-Za-z0-9_]+_\d+$")

GATE_MIN_ROWS = 3


def _coerce_number(value: str) -> float | None:
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def _coerce_integer(value: str) -> int | None:
    try:
        # Handle e.g. "5", "5.0" (still an integer mathematically), but reject
        # "5.7"
        if value is None:
            return None
        s = str(value).strip()
        if s == "":
            return None
        if "." in s:
            f = float(s)
            if not f.is_integer():
                return None
            return int(f)
        return int(s)
    except (TypeError, ValueError):
        return None


def _coerce_bool(value: str) -> bool | None:
    s = str(value).strip().lower()
    if s in ("true", "1"):
        return True
    if s in ("false", "0"):
        return False
    return None


def _try_load_json(value: str):
    try:
        return json.loads(value)
    except (TypeError, ValueError):
        return None


def _format_check(value: str, fmt: str) -> bool:
    """Best-effort format conformance for the common OpenAPI formats."""
    if not value:
        return False
    if fmt == "uuid":
        return bool(re.fullmatch(
            r"[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}",
            value))
    if fmt == "date":
        return bool(re.fullmatch(r"\d{4}-\d{2}-\d{2}", value))
    if fmt == "date-time":
        return bool(re.match(r"\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}", value))
    if fmt == "email":
        return bool(re.match(r"[^@\s]+@[^@\s]+\.[^@\s]+", value))
    if fmt == "ipv4":
        return bool(re.fullmatch(r"\d{1,3}(\.\d{1,3}){3}", value))
    if fmt == "uri" or fmt == "url":
        return bool(re.match(r"https?://", value))
    if fmt in ("int32", "int64"):
        return _coerce_integer(value) is not None
    if fmt == "float" or fmt == "double":
        return _coerce_number(value) is not None
    return False


def classes_for_schema(schema: dict | None) -> list[str]:
    """Return the canonical, *reachable* class list for a schema.

    Classes that the bounded-numeric branch of `classify_value` cannot
    produce (e.g. `zero` when minimum=1) are pruned, so EPC isn't capped
    artificially by classes that are mathematically unreachable.

    None / empty schema → ["any"].
    """
    if not schema:
        return ["any"]
    if "enum" in schema and isinstance(schema["enum"], list) and schema["enum"]:
        return [f"enum:{json.dumps(v, sort_keys=True)}" for v in schema["enum"]]
    types = schema.get("type")
    if isinstance(types, list):
        prim = next((t for t in types if t != "null"), None)
    else:
        prim = types

    if prim == "boolean":
        return ["true", "false"]

    if prim in ("integer", "number"):
        bounded = "minimum" in schema or "maximum" in schema
        if not bounded:
            base = ["zero", "positive", "negative"]
            if prim == "number":
                base.append("fractional")
            return base
        # Bounded: include only classes that are reachable given the bounds.
        mn = schema.get("minimum")
        mx = schema.get("maximum")
        out: list[str] = []
        if mn is not None:
            out.append("below_min")
            out.append("at_min")
        if mx is not None:
            out.append("at_max")
            out.append("above_max")
        # `in_range`: only when at least one strictly-interior value exists.
        if mn is not None and mx is not None and mx - mn > 1:
            out.append("in_range")
        elif mn is None or mx is None:
            out.append("in_range")
        # `zero` is reachable only when 0 is strictly interior to the range
        # (otherwise classify_value collapses it to at_min / at_max / below).
        zero_interior = (
            (mn is None or mn < 0) and (mx is None or mx > 0)
        )
        if zero_interior:
            out.append("zero")
        # `negative` only when negatives are strictly inside (not at_min/below_min).
        neg_interior = (mn is None or mn < -1) and (mx is None or mx > -1)
        # The strict check: there must be a negative value v such that
        # mn < v < 0 (otherwise it'd be at_min or zero).
        if mn is None:
            neg_reachable = True
        elif mn < 0:
            neg_reachable = True   # mn itself is negative; in_range carries the role
        else:
            neg_reachable = False
        if neg_reachable:
            out.append("negative")
        if prim == "number":
            # `fractional` reachable when at least one non-integer value lies strictly
            # in (mn, mx).
            frac_reachable = (mn is None or mx is None) or (mx - mn > 0)
            if frac_reachable:
                out.append("fractional")
        # Dedup while preserving order
        seen: set[str] = set()
        deduped: list[str] = []
        for c in out:
            if c not in seen:
                seen.add(c)
                deduped.append(c)
        return deduped

    if prim == "string":
        if "format" in schema and schema["format"]:
            return ["empty", "format_conforming", "format_violating"]
        if "minLength" in schema or "maxLength" in schema:
            mn = schema.get("minLength")
            mx = schema.get("maxLength")
            out = ["empty"]
            if mn is not None:
                out.append("below_minLength")
                out.append("at_minLength")
            if mx is not None:
                out.append("at_maxLength")
                out.append("above_maxLength")
            # in_length_range exists only if the range is non-degenerate
            if mn is None or mx is None or mx - mn > 1:
                out.append("in_length_range")
            return out
        return ["empty", "short", "medium", "long"]
    if prim == "array":
        return ["empty", "singleton", "multi"]
    if prim == "object":
        return ["object"]
    return ["any"]


def classify_value(value: str, schema: dict | None) -> str | None:
    """Map a value to one of the classes from `classes_for_schema(schema)`,
    or None when the value cannot be mapped (e.g. malformed JSON for an
    object schema)."""
    if not schema:
        return "any"
    if "enum" in schema and isinstance(schema["enum"], list) and schema["enum"]:
        # Compare canonicalised JSON form; treat the raw string as already
        # canonical when the enum members are strings.
        for ev in schema["enum"]:
            if str(value) == str(ev):
                return f"enum:{json.dumps(ev, sort_keys=True)}"
        return None  # value isn't a declared enum literal
    types = schema.get("type")
    if isinstance(types, list):
        prim = next((t for t in types if t != "null"), None)
    else:
        prim = types

    if prim == "boolean":
        b = _coerce_bool(value)
        return None if b is None else ("true" if b else "false")

    if prim in ("integer", "number"):
        if prim == "integer":
            n = _coerce_integer(value)
            if n is None:
                return None
            n_val = n
            is_fractional = False
        else:
            n = _coerce_number(value)
            if n is None:
                return None
            n_val = n
            is_fractional = (not float(n_val).is_integer())

        bounded = "minimum" in schema or "maximum" in schema
        if bounded:
            mn = schema.get("minimum")
            mx = schema.get("maximum")
            if mn is not None and n_val < mn:
                return "below_min"
            if mx is not None and n_val > mx:
                return "above_max"
            if mn is not None and n_val == mn:
                return "at_min"
            if mx is not None and n_val == mx:
                return "at_max"
            if n_val == 0:
                return "zero"
            if n_val < 0:
                return "negative"
            if prim == "number" and is_fractional:
                return "fractional"
            return "in_range"
        # Unbounded
        if n_val == 0:
            return "zero"
        if prim == "number" and is_fractional:
            return "fractional"
        return "positive" if n_val > 0 else "negative"

    if prim == "string":
        s = "" if value is None else str(value)
        if s == "":
            return "empty"
        fmt = schema.get("format") or ""
        if fmt:
            return "format_conforming" if _format_check(s, fmt) else "format_violating"
        mn = schema.get("minLength")
        mx = schema.get("maxLength")
        if mn is not None or mx is not None:
            L = len(s)
            if mn is not None and L < mn:
                return "below_minLength"
            if mx is not None and L > mx:
                return "above_maxLength"
            if mn is not None and L == mn:
                return "at_minLength"
            if mx is not None and L == mx:
                return "at_maxLength"
            return "in_length_range"
        # Length-class only
        L = len(s)
        if L < 10:
            return "short"
        if L <= 50:
            return "medium"
        return "long"

    if prim == "array":
        arr = _try_load_json(value)
        if not isinstance(arr, list):
            return None
        if not arr:
            return "empty"
        if len(arr) == 1:
            return "singleton"
        return "multi"

    if prim == "object":
        obj = _try_load_json(value)
        if not isinstance(obj, dict):
            return None
        return "object"

    return "any"


def lookup_schema(spec: dict, op_cache: dict, method: str, path: str,
                  parameter: str, location: str) -> dict | None:
    """Return the schema for a single (method, path, parameter, location)."""
    cache_key = (method.upper(), path)
    if cache_key not in op_cache:
        op_cache[cache_key] = get_operation(spec, method, path) or None
    op_info = op_cache[cache_key]
    if op_info is None:
        return None
    _, op = op_info
    loc = (location or "").lower()
    if loc == "body":
        body_schema = request_body_schema(spec, op)
        if not parameter or parameter.startswith("__") or parameter.startswith("requestBody"):
            return body_schema
        if isinstance(body_schema, dict):
            sub = (body_schema.get("properties") or {}).get(parameter)
            if sub is not None:
                return sub
        return body_schema
    return parameter_schema(spec, op, parameter, loc)


def main(argv: list[str]) -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--inputs", required=True, type=Path)
    p.add_argument("--oas", required=True, type=Path)
    p.add_argument("--out-dir", required=True, type=Path)
    p.add_argument("--include-negatives", action="store_true",
                   help="Include negative variants. Off by default — D9 measures the "
                        "positive-pool's coverage of canonical classes.")
    args = p.parse_args(argv)
    args.out_dir.mkdir(parents=True, exist_ok=True)

    spec = load_oas(args.oas)
    op_cache: dict = {}

    # Per (method, path, parameter, location): {class -> count, total, fallback}
    pools: dict[tuple[str, str, str, str], dict] = defaultdict(
        lambda: {"covered": defaultdict(int), "total": 0, "no_schema": 0, "unmapped": 0}
    )

    rows_total = 0
    rows_kept = 0
    fallback_skipped = 0

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
            parameter = row.get("parameter") or ""
            location = (row.get("location") or "").lower()
            key = (method, path, parameter, location)
            agg = pools[key]
            agg["total"] += 1
            rows_kept += 1
            schema = lookup_schema(spec, op_cache, method, path, parameter, location)
            if schema is None:
                agg["no_schema"] += 1
                continue
            cls = classify_value(value, schema)
            if cls is None:
                agg["unmapped"] += 1
                continue
            agg["covered"][cls] += 1

    per_param_path = args.out_dir / "d9_per_param.csv"
    per_param_rows = []
    with per_param_path.open("w", encoding="utf-8", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(["http_method", "path", "parameter", "location",
                    "total", "classes_total", "classes_covered", "epc",
                    "missing_classes"])
        for (m, path, param, loc), agg in sorted(pools.items()):
            schema = lookup_schema(spec, op_cache, m, path, param, loc)
            classes = classes_for_schema(schema)
            covered = sorted(agg["covered"].keys())
            missing = [c for c in classes if c not in covered]
            classes_total = len(classes)
            classes_covered = sum(1 for c in classes if c in covered)
            epc = (classes_covered / classes_total) if classes_total else None
            row_d = {
                "operation": f"{m} {path}",
                "parameter": param,
                "location": loc,
                "total": agg["total"],
                "classes_total": classes_total,
                "classes_covered": classes_covered,
                "epc": epc,
                "missing_classes": missing,
            }
            per_param_rows.append(row_d)
            w.writerow([m, path, param, loc, agg["total"],
                        classes_total, classes_covered,
                        f"{epc:.4f}" if epc is not None else "",
                        "|".join(missing)])

    gated = [r for r in per_param_rows if r["total"] >= GATE_MIN_ROWS and r["classes_total"] > 0]
    epc_values = [r["epc"] for r in gated if r["epc"] is not None]
    mean_epc = (sum(epc_values) / len(epc_values)) if epc_values else None

    worst = sorted(gated, key=lambda r: r["epc"] if r["epc"] is not None else 0.0)[:15]

    summary = {
        "metric": "D9 Equivalence-Partition Coverage",
        "include_negatives": bool(args.include_negatives),
        "rows_total": rows_total,
        "rows_kept": rows_kept,
        "rows_fallback_skipped": fallback_skipped,
        "pools_total": len(per_param_rows),
        "pools_gated": len(gated),
        "mean_epc": mean_epc,
        "epc_threshold_pass": (mean_epc is not None) and (mean_epc >= 0.50),
        "worst_pools": worst,
        "computed_at": datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z"),
    }
    (args.out_dir / "d9_summary.json").write_text(json.dumps(summary, indent=2))

    epc_str = f"{mean_epc:.4f}" if mean_epc is not None else "N/A"
    print(f"validate_d9: EPC(mean)={epc_str} "
          f"({len(gated)} pools ≥ {GATE_MIN_ROWS} rows; "
          f"{fallback_skipped} fallback-skipped) → {args.out_dir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
