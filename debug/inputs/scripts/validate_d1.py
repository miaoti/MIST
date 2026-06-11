#!/usr/bin/env python3
"""
D1 — Schema Conformance Rate.

For each row in inputs.csv (produced by mine_test_inputs.py):
  - look up the matching OpenAPI operation by (HTTP method, path)
  - if location == 'body', validate the *whole* JSON payload of the body
    against the request-body schema
  - otherwise validate the value against the parameter's schema

We coerce values before validation so that a query-string `5` validates as
integer (it would always be a string in the test source). Coercion is one-way:
if the schema says integer, parse, else leave as string.

Outputs:
    d1_per_row.csv      one row per (operation, parameter, value) with valid=true|false
    d1_per_param.csv    aggregated by (operation, parameter, location)
    d1_summary.json     overall SCR + breakdown by test_kind
"""

from __future__ import annotations

import argparse
import csv
import json
import re
import sys
from collections import defaultdict
from pathlib import Path

from jsonschema import Draft7Validator
from jsonschema.exceptions import ValidationError

# Tool-side padding values produced by typeAwareFallbackValue. They are not
# LLM/test outputs and shouldn't be measured as schema conformance — they
# always serialize as plain strings regardless of the parameter's declared
# type.
FALLBACK_VALUE_RX = re.compile(r"^FALLBACK_[A-Za-z0-9_]+_\d+$")

from oas_helpers import (
    get_operation,
    load_oas,
    parameter_schema,
    request_body_schema,
)


def coerce_query_value(value: str, schema: dict | None) -> object:
    """Best-effort string-to-typed coercion so that query/path/header values
    can be validated against numeric / boolean schemas.
    """
    if schema is None:
        return value
    types = schema.get("type")
    if isinstance(types, list):
        prim_types = [t for t in types if t != "null"]
        prim_type = prim_types[0] if prim_types else None
    else:
        prim_type = types
    if prim_type in ("integer",):
        try:
            return int(value)
        except (TypeError, ValueError):
            return value
    if prim_type in ("number",):
        try:
            return float(value)
        except (TypeError, ValueError):
            return value
    if prim_type in ("boolean",):
        if value in ("true", "True", "TRUE"):
            return True
        if value in ("false", "False", "FALSE"):
            return False
        return value
    if prim_type == "array":
        # The Java test files JSON-encode arrays. Try that first.
        try:
            return json.loads(value)
        except json.JSONDecodeError:
            return value
    if prim_type == "object":
        try:
            return json.loads(value)
        except json.JSONDecodeError:
            return value
    return value


def parse_body(value: str) -> object:
    try:
        return json.loads(value)
    except (json.JSONDecodeError, TypeError):
        return value


def validate_value(coerced: object, schema: dict | None) -> tuple[bool, str]:
    if schema is None:
        return True, "no_schema"
    try:
        Draft7Validator(schema).validate(coerced)
        return True, ""
    except ValidationError as e:
        path = ".".join(str(p) for p in e.absolute_path) or "<root>"
        return False, f"{path}: {e.message}"
    except Exception as e:  # pragma: no cover — defensive
        return False, f"validator_error: {e}"


def main(argv: list[str]) -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--inputs", required=True, type=Path, help="inputs.csv from mine_test_inputs.py")
    p.add_argument("--oas", required=True, type=Path, help="OpenAPI spec YAML/JSON")
    p.add_argument("--out-dir", required=True, type=Path, help="Output directory")
    p.add_argument("--include-negatives", action="store_true",
                   help="Also score negative variants (off by default — they are expected to be invalid).")
    args = p.parse_args(argv)

    args.out_dir.mkdir(parents=True, exist_ok=True)

    spec = load_oas(args.oas)

    # Cache: (method, path) → (template, operation)
    op_cache: dict[tuple[str, str], object] = {}

    per_row_path = args.out_dir / "d1_per_row.csv"
    cols = [
        "scenario", "test_method", "test_kind", "step_idx",
        "http_method", "path", "matched_template", "parameter",
        "location", "value", "valid", "schema_present", "error",
    ]

    counts = {
        "total": 0,
        "valid": 0,
        "invalid": 0,
        "no_schema": 0,
        "missing_op": 0,
        "skipped_negative": 0,
        "skipped_fallback": 0,
    }
    per_param_counts: dict[tuple[str, str, str, str], dict] = defaultdict(
        lambda: {"total": 0, "valid": 0, "invalid_examples": []}
    )
    per_kind_counts: dict[str, dict] = defaultdict(lambda: {"total": 0, "valid": 0})

    with args.inputs.open("r", encoding="utf-8", newline="") as in_fh, \
         per_row_path.open("w", encoding="utf-8", newline="") as out_fh:
        reader = csv.DictReader(in_fh)
        writer = csv.DictWriter(out_fh, fieldnames=cols, quoting=csv.QUOTE_MINIMAL)
        writer.writeheader()
        for row in reader:
            kind = (row.get("test_kind") or "").lower()
            if kind == "negative" and not args.include_negatives:
                counts["skipped_negative"] += 1
                continue
            value_for_filter = (row.get("value") or "").strip()
            if FALLBACK_VALUE_RX.match(value_for_filter):
                counts["skipped_fallback"] += 1
                continue
            counts["total"] += 1

            method = row["http_method"]
            path = row["path"]
            cache_key = (method.upper(), path)
            if cache_key not in op_cache:
                op_cache[cache_key] = get_operation(spec, method, path) or None
            op_info = op_cache[cache_key]

            if op_info is None:
                counts["missing_op"] += 1
                writer.writerow({
                    **{k: row.get(k, "") for k in
                       ("scenario", "test_method", "test_kind", "step_idx",
                        "http_method", "path", "parameter", "location", "value")},
                    "matched_template": "",
                    "valid": "",
                    "schema_present": "false",
                    "error": "no_matching_operation",
                })
                continue

            tmpl, op = op_info
            location = row["location"]
            value = row["value"]
            parameter = row["parameter"]

            if location == "body":
                # mine_test_inputs.py emits one row per top-level body property,
                # so look up the sub-schema for that property under
                # `requestBody.content[*].schema.properties[name]`. Synthetic
                # rows (parameter == '__array_body__' / '__primitive_body__'
                # / 'requestBodyN') are validated against the whole body schema.
                body_schema = request_body_schema(spec, op)
                if parameter and not parameter.startswith("__") and not parameter.startswith("requestBody"):
                    sub = None
                    if isinstance(body_schema, dict):
                        sub = (body_schema.get("properties") or {}).get(parameter)
                    if sub is not None:
                        schema = sub
                        # Use type-aware coercion: mine_test_inputs.py already
                        # unwrapped the JSON value (`"price":"1.0"` → "1.0"
                        # string in CSV). Calling json.loads again would re-
                        # parse "1.0" as a float and break string-typed schemas
                        # whose values happen to look numeric. Match the same
                        # per-type rules as query/path/header parameters.
                        coerced = coerce_query_value(value, schema)
                    elif body_schema is None:
                        schema = None
                        coerced = value
                    else:
                        # Property absent from the declared body schema —
                        # `additionalProperties` may forbid this. Validate the
                        # whole body so the misuse surfaces as one violation.
                        schema = body_schema
                        coerced = parse_body(value)
                else:
                    schema = body_schema
                    coerced = parse_body(value)
            else:
                schema = parameter_schema(spec, op, parameter, location)
                coerced = coerce_query_value(value, schema)

            if schema is None:
                counts["no_schema"] += 1
                valid_flag = ""
                err = "no_schema"
            else:
                ok, err = validate_value(coerced, schema)
                if ok:
                    counts["valid"] += 1
                    valid_flag = "true"
                else:
                    counts["invalid"] += 1
                    valid_flag = "false"

            writer.writerow({
                **{k: row.get(k, "") for k in
                   ("scenario", "test_method", "test_kind", "step_idx",
                    "http_method", "path", "parameter", "location", "value")},
                "matched_template": tmpl,
                "valid": valid_flag,
                "schema_present": "true" if schema is not None else "false",
                "error": err if valid_flag != "true" else "",
            })
            agg_key = (method.upper(), tmpl, parameter, location)
            agg = per_param_counts[agg_key]
            agg["total"] += 1
            if valid_flag == "true":
                agg["valid"] += 1
            elif valid_flag == "false" and len(agg["invalid_examples"]) < 3:
                agg["invalid_examples"].append({"value": value, "error": err})
            kind_agg = per_kind_counts[kind or "unknown"]
            kind_agg["total"] += 1
            if valid_flag == "true":
                kind_agg["valid"] += 1

    # ----- Aggregations -----
    per_param_path = args.out_dir / "d1_per_param.csv"
    with per_param_path.open("w", encoding="utf-8", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(["http_method", "path_template", "parameter", "location",
                    "total", "valid", "invalid", "scr"])
        for (m, tmpl, param, loc), v in sorted(per_param_counts.items()):
            inv = v["total"] - v["valid"]
            scr = (v["valid"] / v["total"]) if v["total"] else 0.0
            w.writerow([m, tmpl, param, loc, v["total"], v["valid"], inv, f"{scr:.4f}"])

    schema_present_total = counts["valid"] + counts["invalid"]
    overall_scr = (counts["valid"] / schema_present_total) if schema_present_total else None

    summary = {
        "metric": "D1 Schema Conformance Rate",
        "include_negatives": bool(args.include_negatives),
        "rows_total": counts["total"],
        "rows_validated": schema_present_total,
        "rows_valid": counts["valid"],
        "rows_invalid": counts["invalid"],
        "rows_no_schema": counts["no_schema"],
        "rows_missing_operation": counts["missing_op"],
        "rows_skipped_negative": counts["skipped_negative"],
        "rows_skipped_fallback": counts["skipped_fallback"],
        "scr": overall_scr,
        "scr_threshold_pass": (overall_scr is not None) and (overall_scr >= 0.90),
        "by_test_kind": {
            k: {
                "total": v["total"],
                "valid": v["valid"],
                "scr": (v["valid"] / v["total"]) if v["total"] else None,
            } for k, v in per_kind_counts.items()
        },
        "worst_parameters": sorted(
            (
                {
                    "operation": f"{m} {tmpl}",
                    "parameter": param,
                    "location": loc,
                    "total": v["total"],
                    "valid": v["valid"],
                    "scr": (v["valid"] / v["total"]) if v["total"] else 0.0,
                    "invalid_examples": v["invalid_examples"],
                }
                for (m, tmpl, param, loc), v in per_param_counts.items()
                if v["total"] >= 3
            ),
            key=lambda x: x["scr"],
        )[:15],
    }
    (args.out_dir / "d1_summary.json").write_text(json.dumps(summary, indent=2))
    scr_str = f"{overall_scr:.4f}" if overall_scr is not None else "N/A"
    print(f"validate_d1: SCR={scr_str} "
          f"({counts['valid']}/{schema_present_total}) "
          f"+ {counts['no_schema']} no-schema, "
          f"{counts['missing_op']} missing-op, "
          f"{counts['skipped_negative']} skipped-negatives, "
          f"{counts['skipped_fallback']} skipped-fallback → {args.out_dir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
