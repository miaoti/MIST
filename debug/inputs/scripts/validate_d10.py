#!/usr/bin/env python3
"""
D10 — Negative-Input Fault-Type Purity (NIFP).

For every value RESTest emits as a `Negative Test (Round-Robin)` with a
`[InvalidType: T]` label, check whether the value actually exhibits the
fault type T as a property of the value plus the parameter's schema.

NIFP(T) = |{ v labelled T : value-exhibits-T(v, schema(P)) }| / |{ v labelled T }|

Per-type purity check (purely input-side — no SUT response):

    TYPE_MISMATCH        actualType(v) != schemaType(P)
                         (boolean: 'true'/'false' do NOT count as type mismatch
                          when schema is boolean — this catches the May-2 bug)
    BOUNDARY_VIOLATION   numeric: v < minimum OR v > maximum
                         string:  len(v) < minLength OR len(v) > maxLength
    REGEX_MISMATCH       schema.pattern is set AND not regex(P).fullmatch(v)
    OVERFLOW             numeric: |v| > 2^31  (or > schema.maximum * 1000)
                         string:  len(v) > 1000  (or > schema.maxLength * 10)
    EMPTY_INPUT          v in {"", " ", "\t", "\n", "[]", "{}", "null"} considered empty
                         AND parameter is required
    NULL_INPUT           v in {None, "null", "NULL"} AND parameter is required
                         (or the value is the JSON null literal)
    SPECIAL_CHARACTERS   regex match against an OWASP-style injection catalogue
    SEMANTIC_MISMATCH    best-effort domain check; no schema-derivable test, so
                         we count anything labelled SEMANTIC_MISMATCH that
                         passes a basic type/format conformance check (i.e.
                         the value is otherwise schema-valid) as pure.
                         Anything that fails the schema is impure (it's a
                         different fault type masquerading).

Source: `logs/.../trainticket_test_execution.log`. Parses lines of the
shape:

    ✅ Negative Test (Round-Robin) → param = value [InvalidType: T] (javaType: …) - LOCKED

Outputs:
    d10_per_value.csv   one row per (operation, parameter, value, fault_label, pure)
    d10_per_type.csv    aggregate per fault_label
    d10_summary.json    overall purity, per-type purity, threshold pass
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

from oas_helpers import (
    get_operation,
    load_oas,
    parameter_schema,
    request_body_schema,
)

# ---------------------------------------------------------------------------
# Log-line scanner — matches the negative-variant emission in
# MultiServiceTestCaseGenerator.fireNegativeFault and the
# negative-pool round-robin path.
# ---------------------------------------------------------------------------

# Captures (param, value, fault, javatype). The Java-type annotation
# tells us the *generator's* declared type for the value, which is the
# authoritative type signal for TYPE_MISMATCH purity (the value field
# alone loses its quoting through the log).
RE_NEGATIVE = re.compile(
    r"Negative Test \(Round-Robin\) → "
    r"(?:(?P<service>\S+) )?"
    r"(?P<param>\S+) = (?P<value>.*?)\s*\[InvalidType: (?P<fault>[^\]]+)\]"
    r"(?:\s+\(javaType:\s+(?P<javatype>\w+)\))?"
)

# Operation context appears in lines like:
#   '>> Step R1: ts-order-other-service POST /api/v1/orderOtherService/orderOther/refresh body=...'
# We track the most recent (method, path) so we can resolve the schema for the
# next negative-fault entry.
RE_STEP_CONTEXT = re.compile(
    r"(?P<method>GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS)\s+(?P<path>/[^\s]+)"
)

# Parameter declaration:
#   '📋 Parameter: boughtDateStart (type: string, in: formData, description: ...)'
# We track type+location alongside the operation context to give D10 a fallback
# schema view when the OAS lookup misses (e.g. body sub-property nesting).
RE_INLINE_PARAM = re.compile(
    r"📋\s+Parameter:\s+(?P<name>\S+)\s+\(type:\s+(?P<type>\w+),\s+in:\s+(?P<loc>\w+)"
)

# OWASP-style special-character injection patterns. A value is "pure" for
# SPECIAL_CHARACTERS if it matches at least one. The catch-all class drops
# `(){}` to avoid over-matching ordinary JSON-shaped values; only the OWASP
# meta-chars that appear in real injection payloads are kept.
SPECIAL_CHAR_PATTERNS = (
    re.compile(r"<script\b", re.IGNORECASE),                  # XSS
    re.compile(r"';|--|/\*|\*/|\bUNION\b|\bSELECT\b", re.IGNORECASE),  # SQLi
    re.compile(r"\$\(|\bexec\b|\bcat /etc", re.IGNORECASE),   # cmd injection
    re.compile(r"\.\./|\\\\\.\."),                              # path traversal
    re.compile(r"\${jndi:", re.IGNORECASE),                    # log4shell
    re.compile(r"[<>&\"'`%]"),                                  # OWASP meta-chars (excl. parens/braces)
    re.compile(r"\\x[0-9a-fA-F]{2}|\\u[0-9a-fA-F]{4}"),         # encoded chars
)

# Cross-language null indicators commonly emitted by negative-pool generators.
NULL_LITERALS = ("null", "NULL", "Null", "nil", "Nil", "NIL",
                 "None", "none", "NONE", "undefined", "Undefined", "UNDEFINED")
EMPTY_LITERALS = ("", " ", "\t", "\n", "[]", "{}")

OVERFLOW_NUMERIC_THRESHOLD = 2 ** 31
OVERFLOW_STRING_LEN = 1000

# Min rows per fault type for the threshold gate.
GATE_MIN_PER_TYPE = 3


def _coerce_number(value: str) -> float | None:
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def _coerce_integer(value: str) -> int | None:
    try:
        s = str(value).strip()
        if "." in s:
            f = float(s)
            return int(f) if f.is_integer() else None
        return int(s)
    except (TypeError, ValueError):
        return None


def _schema_type(schema: dict | None) -> str | None:
    if not schema:
        return None
    t = schema.get("type")
    if isinstance(t, list):
        return next((x for x in t if x != "null"), None)
    return t


def _is_required(schema: dict | None, param_name: str, op: dict | None,
                 location: str | None,
                 body_schema: dict | None = None) -> bool:
    """Best-effort required check.

    For body sub-properties consult the body schema's `required` list.
    For non-body params consult the parameter object's `required` flag.
    Unknown → False (do not over-credit EMPTY_INPUT/NULL_INPUT purity).
    """
    if (location or "").lower() == "body" and isinstance(body_schema, dict):
        req = body_schema.get("required") or []
        return param_name in req
    if op is not None:
        for p in op.get("parameters") or []:
            if p.get("name") == param_name:
                return bool(p.get("required"))
    return False


JAVATYPE_TO_OAS = {
    "Integer": "integer", "Long": "integer", "Short": "integer", "Byte": "integer",
    "Float": "number", "Double": "number", "BigDecimal": "number",
    "Boolean": "boolean",
    "String": "string", "Character": "string",
    "List": "array", "Set": "array", "Object": "object", "Map": "object",
}


def check_type_mismatch(value: str, schema: dict | None,
                        javatype: str | None = None) -> bool:
    """True iff the generator's declared Java type for the value doesn't
    match the schema's declared primitive type.

    The execution log loses the value's surrounding quoting (so `"12345"`
    and `12345` look the same in the value field). We therefore prefer
    the `(javaType: T)` annotation on the same line, which IS authoritative
    for the generator's intent, and only fall back to value-shape inference
    when the annotation is missing.
    """
    if not schema:
        return False
    t = _schema_type(schema)
    if t is None:
        return False

    if javatype:
        mapped = JAVATYPE_TO_OAS.get(javatype)
        if mapped:
            return mapped != t

    # No javaType — fall back to value-shape inference.
    if t == "integer":
        return _coerce_integer(value) is None
    if t == "number":
        return _coerce_number(value) is None
    if t == "boolean":
        s = value.strip().lower()
        return s not in ("true", "false", "1", "0")
    if t == "string":
        # Without quoting we can't disambiguate `12345` (string) from `12345`
        # (int). Only flag clear non-string JSON literals.
        try:
            parsed = json.loads(value)
        except (TypeError, ValueError):
            return False
        return isinstance(parsed, (dict, list, bool)) or parsed is None
    if t == "array":
        try:
            return not isinstance(json.loads(value), list)
        except (TypeError, ValueError):
            return True
    if t == "object":
        try:
            return not isinstance(json.loads(value), dict)
        except (TypeError, ValueError):
            return True
    return False


def schema_has_bounds(schema: dict | None) -> bool:
    """True iff the schema declares a min/max constraint that BOUNDARY_VIOLATION
    can violate. When false, the negative-pool's boundary-attack values cannot
    actually violate any boundary because none is declared — the result is a
    *schema gap*, not a value-purity miss. We surface this in the summary as
    `boundary_unbounded` so it's distinguishable from a true purity failure.
    """
    if not schema:
        return False
    t = _schema_type(schema)
    if t in ("integer", "number"):
        return ("minimum" in schema) or ("maximum" in schema)
    if t == "string":
        return ("minLength" in schema) or ("maxLength" in schema)
    return False


def check_boundary_violation(value: str, schema: dict | None) -> bool:
    if not schema:
        return False
    t = _schema_type(schema)
    if t in ("integer", "number"):
        n = _coerce_number(value)
        if n is None:
            return False
        if "minimum" in schema and n < schema["minimum"]:
            return True
        if "maximum" in schema and n > schema["maximum"]:
            return True
        return False
    if t == "string":
        L = len(value)
        if "minLength" in schema and L < schema["minLength"]:
            return True
        if "maxLength" in schema and L > schema["maxLength"]:
            return True
        return False
    return False


def check_regex_mismatch(value: str, schema: dict | None) -> bool:
    if not schema:
        return False
    pat = schema.get("pattern")
    if not pat:
        return False
    try:
        return re.fullmatch(pat, value) is None
    except re.error:
        return False


def check_overflow(value: str, schema: dict | None,
                   truncated: bool = False) -> bool:
    t = _schema_type(schema) if schema else None
    if t in ("integer", "number"):
        n = _coerce_number(value)
        if n is None:
            return False
        threshold = OVERFLOW_NUMERIC_THRESHOLD
        if schema and "maximum" in schema:
            try:
                threshold = max(threshold, abs(schema["maximum"]) * 1000)
            except (TypeError, ValueError):
                pass
        return abs(n) > threshold
    if t == "string":
        # The execution log truncates long values with a trailing '...';
        # presence of truncation is a positive signal that the original
        # value was long enough to count as an overflow.
        if truncated:
            return True
        # Repeated single-character ramp ("AAAA…", "XXXX…") of any
        # observable length is a classic overflow probe.
        if len(value) >= 30 and len(set(value)) == 1:
            return True
        threshold = OVERFLOW_STRING_LEN
        if schema and "maxLength" in schema:
            try:
                threshold = max(threshold, schema["maxLength"] * 10)
            except (TypeError, ValueError):
                pass
        return len(value) > threshold
    # Unknown schema type — be conservative and don't credit overflow.
    return False


def check_empty_input(value: str, schema: dict | None,
                     required: bool) -> bool:
    if not required:
        return False
    return value.strip() in EMPTY_LITERALS or value == ""


def check_null_input(value: str, schema: dict | None,
                    required: bool) -> bool:
    if not required:
        return False
    return value.strip() in NULL_LITERALS or value.strip().lower() == "null"


def check_special_characters(value: str, schema: dict | None) -> bool:
    return any(p.search(value) for p in SPECIAL_CHAR_PATTERNS)


def check_semantic_mismatch(value: str, schema: dict | None,
                            required: bool = False,
                            truncated: bool = False,
                            javatype: str | None = None) -> bool:
    """SEMANTIC_MISMATCH is a domain-level negative with no schema-derivable
    test. A value is *pure* for this label only when it doesn't already
    trigger a more specific fault check — otherwise the label is wrong:
    the value's actual fault is type/boundary/etc., not semantic.

    The cross-checks must use the same `truncated`/`required`/`javatype`
    flags the labelled check would receive, otherwise SEMANTIC values
    bypass the more specific tests by accident.
    """
    if check_type_mismatch(value, schema, javatype=javatype):
        return False
    if check_boundary_violation(value, schema):
        return False
    if check_regex_mismatch(value, schema):
        return False
    if check_overflow(value, schema, truncated=truncated):
        return False
    if check_empty_input(value, schema, required=required):
        return False
    if check_null_input(value, schema, required=required):
        return False
    if check_special_characters(value, schema):
        return False
    return True


PURITY_CHECKS = {
    "TYPE_MISMATCH": lambda v, s, r, t, j: check_type_mismatch(v, s, javatype=j),
    "BOUNDARY_VIOLATION": lambda v, s, r, t, j: check_boundary_violation(v, s),
    "REGEX_MISMATCH": lambda v, s, r, t, j: check_regex_mismatch(v, s),
    "OVERFLOW": lambda v, s, r, t, j: check_overflow(v, s, truncated=t),
    "EMPTY_INPUT": lambda v, s, r, t, j: check_empty_input(v, s, r),
    "NULL_INPUT": lambda v, s, r, t, j: check_null_input(v, s, r),
    "SPECIAL_CHARACTERS": lambda v, s, r, t, j: check_special_characters(v, s),
    "SEMANTIC_MISMATCH": lambda v, s, r, t, j: check_semantic_mismatch(
        v, s, required=r, truncated=t, javatype=j),
}


def lookup_schema_and_op(spec: dict, op_cache: dict, method: str | None,
                          path: str | None, parameter: str,
                          location: str = "") -> tuple[dict | None, dict | None, dict | None, str]:
    """Return (param_schema, op, body_schema, resolved_location).

    `body_schema` is returned alongside so callers can consult its
    `required` array for body sub-properties. `resolved_location` is one
    of {"body", "query", "path", "header", "formData", "cookie", ""} and
    reflects where the parameter was actually found.
    """
    if not method or not path:
        return None, None, None, ""
    cache_key = (method.upper(), path)
    if cache_key not in op_cache:
        op_cache[cache_key] = get_operation(spec, method, path) or None
    op_info = op_cache[cache_key]
    if op_info is None:
        return None, None, None, ""
    _, op = op_info

    body_schema = request_body_schema(spec, op)
    if isinstance(body_schema, dict):
        sub = (body_schema.get("properties") or {}).get(parameter)
        if sub is not None:
            return sub, op, body_schema, "body"

    for loc in ("query", "path", "header", "formData", "cookie"):
        sch = parameter_schema(spec, op, parameter, loc)
        if sch is not None:
            return sch, op, body_schema, loc

    return body_schema, op, body_schema, "body" if body_schema else ""


def main(argv: list[str]) -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--exec-log", required=True, type=Path,
                   help="trainticket_test_execution.log")
    p.add_argument("--oas", required=True, type=Path)
    p.add_argument("--out-dir", required=True, type=Path)
    args = p.parse_args(argv)
    args.out_dir.mkdir(parents=True, exist_ok=True)

    spec = load_oas(args.oas)
    op_cache: dict = {}

    # Per-fault counts and per-row records.
    # `unbounded` counts BOUNDARY_VIOLATION values whose target schema has no
    # min/max — those are *schema gaps*, not value-purity misses.
    per_type: dict[str, dict] = defaultdict(
        lambda: {"total": 0, "pure": 0, "unbounded": 0, "impure_examples": []}
    )

    rows = []
    last_method: str | None = None
    last_path: str | None = None
    inline_params: dict[str, dict] = {}  # param name → {"type": ..., "loc": ...}

    if not args.exec_log.is_file():
        print(f"error: --exec-log not a file: {args.exec_log}", file=sys.stderr)
        return 2

    with args.exec_log.open("r", encoding="utf-8", errors="replace") as fh:
        for line in fh:
            ctx = RE_STEP_CONTEXT.search(line)
            if ctx:
                last_method = ctx.group("method")
                last_path = ctx.group("path")

            ip = RE_INLINE_PARAM.search(line)
            if ip:
                inline_params[ip.group("name")] = {
                    "type": ip.group("type"),
                    "loc": ip.group("loc"),
                }

            m = RE_NEGATIVE.search(line)
            if not m:
                continue
            param = m.group("param").strip()
            value = m.group("value").strip()
            fault = m.group("fault").strip()
            javatype = (m.group("javatype") or "").strip() or None

            # Truncated overflow values end with '...' in the log
            truncated = value.endswith("...")
            if truncated:
                value = value[:-3].rstrip()

            schema, op, body_schema, resolved_loc = lookup_schema_and_op(
                spec, op_cache, last_method, last_path, param)
            # Fallback: when OAS lookup yields no schema, build a synthetic
            # schema from the inline 'type: T' annotation in the log. This
            # is enough to score TYPE_MISMATCH and OVERFLOW correctly even
            # when the OAS spec doesn't expose the body sub-schema.
            if schema is None and param in inline_params:
                schema = {"type": inline_params[param]["type"]}
                if not resolved_loc:
                    resolved_loc = inline_params[param]["loc"]
            required = _is_required(schema, param, op,
                                    location=resolved_loc,
                                    body_schema=body_schema)

            check = PURITY_CHECKS.get(fault)
            if check is None:
                # Unknown fault label — record as impure with note
                pure = False
            else:
                try:
                    pure = bool(check(value, schema, required, truncated, javatype))
                except Exception:  # pragma: no cover — defensive
                    pure = False

            agg = per_type[fault]
            agg["total"] += 1
            if pure:
                agg["pure"] += 1
            elif fault == "BOUNDARY_VIOLATION" and not schema_has_bounds(schema):
                agg["unbounded"] += 1
            elif len(agg["impure_examples"]) < 3:
                agg["impure_examples"].append({
                    "parameter": param,
                    "value": value,
                    "operation": f"{last_method} {last_path}" if last_method else "",
                })
            rows.append({
                "operation": f"{last_method} {last_path}" if last_method else "",
                "parameter": param,
                "value": value,
                "fault_label": fault,
                "pure": pure,
                "schema_present": schema is not None,
            })

    per_value_path = args.out_dir / "d10_per_value.csv"
    with per_value_path.open("w", encoding="utf-8", newline="") as fh:
        w = csv.writer(fh, quoting=csv.QUOTE_MINIMAL)
        w.writerow(["operation", "parameter", "value", "fault_label",
                    "pure", "schema_present"])
        for r in rows:
            w.writerow([r["operation"], r["parameter"], r["value"],
                        r["fault_label"], "true" if r["pure"] else "false",
                        "true" if r["schema_present"] else "false"])

    per_type_path = args.out_dir / "d10_per_type.csv"
    with per_type_path.open("w", encoding="utf-8", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(["fault_label", "total", "pure", "schema_unbounded", "purity"])
        for fault, agg in sorted(per_type.items()):
            purity = (agg["pure"] / agg["total"]) if agg["total"] else None
            w.writerow([fault, agg["total"], agg["pure"], agg["unbounded"],
                        f"{purity:.4f}" if purity is not None else ""])

    total_all = sum(a["total"] for a in per_type.values())
    pure_all = sum(a["pure"] for a in per_type.values())
    overall_nifp = (pure_all / total_all) if total_all else None

    # Per-type threshold pass: NIFP(T) >= 0.95 for every gated type
    per_type_pass = {}
    for fault, agg in per_type.items():
        if agg["total"] >= GATE_MIN_PER_TYPE:
            purity = agg["pure"] / agg["total"]
            per_type_pass[fault] = purity >= 0.95
    threshold_pass = bool(per_type_pass) and all(per_type_pass.values())

    summary = {
        "metric": "D10 Negative-Input Fault-Type Purity",
        "definition": "fraction of negative values that actually exhibit the labelled fault as a property of the value",
        "rows_total": total_all,
        "rows_pure": pure_all,
        "overall_nifp": overall_nifp,
        "by_fault": {
            fault: {
                "total": agg["total"],
                "pure": agg["pure"],
                "schema_unbounded": agg["unbounded"],
                "purity": (agg["pure"] / agg["total"]) if agg["total"] else None,
                "impure_examples": agg["impure_examples"],
            } for fault, agg in sorted(per_type.items())
        },
        "per_type_pass": per_type_pass,
        "nifp_threshold_pass": threshold_pass,
        "computed_at": datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z"),
    }
    (args.out_dir / "d10_summary.json").write_text(json.dumps(summary, indent=2))

    nifp_str = f"{overall_nifp:.4f}" if overall_nifp is not None else "N/A"
    by = ", ".join(
        f"{f}={(a['pure']/a['total']):.0%}" for f, a in sorted(per_type.items()) if a["total"] >= 1
    )
    print(f"validate_d10: NIFP(overall)={nifp_str} ({pure_all}/{total_all}) "
          f"[{by}] → {args.out_dir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
