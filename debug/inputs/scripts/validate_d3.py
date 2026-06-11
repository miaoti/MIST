#!/usr/bin/env python3
"""
D3 — LLM Hallucination Rate.

For each row in llm_pairs.csv (produced by mine_llm_log.py):
  - take the constraints stated in the prompt (type / format / enum / min /
    max / minLength / maxLength / pattern)
  - check the emitted value against each stated constraint
  - if at least one stated constraint is violated, the value is hallucinated

Only rows with prompt_category == 'positive_gen' or 'diverse_gen' are scored
by default (these are the prompts where positive constraints apply).
Negative-generation prompts are reported separately.

Outputs:
    d3_per_row.csv     one row per LLM value with violations and hallucinated flag
    d3_per_param.csv   aggregated per parameter
    d3_summary.json    overall LHR + breakdown by constraint type / category / model
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

# ---------------------------------------------------------------------------
# Type & format checks
# ---------------------------------------------------------------------------

def _parse_int(s: str) -> int | None:
    try:
        return int(s)
    except (TypeError, ValueError):
        return None


def _parse_float(s: str) -> float | None:
    try:
        return float(s)
    except (TypeError, ValueError):
        return None


def check_type(value: str, type_decl: str) -> str | None:
    """Return None if value satisfies the declared type, else a short reason."""
    if not type_decl:
        return None
    t = type_decl.lower()
    if t in ("string", "str"):
        return None  # any non-empty string trivially satisfies
    if t in ("integer", "int", "int32", "int64", "long"):
        return None if _parse_int(value) is not None else f"not an integer ({value!r})"
    if t in ("number", "double", "float"):
        return None if _parse_float(value) is not None else f"not a number ({value!r})"
    if t in ("boolean", "bool"):
        return None if value in ("true", "false", "True", "False") else f"not a boolean ({value!r})"
    if t == "array":
        try:
            v = json.loads(value)
            return None if isinstance(v, list) else f"not an array ({value!r})"
        except (json.JSONDecodeError, TypeError):
            return f"not an array ({value!r})"
    if t == "object":
        try:
            v = json.loads(value)
            return None if isinstance(v, dict) else f"not an object ({value!r})"
        except (json.JSONDecodeError, TypeError):
            return f"not an object ({value!r})"
    return None  # unknown declared type — don't penalize


# OpenAPI 3.0 standard "format" values that we can verify deterministically.
RE_DATE = re.compile(r"^\d{4}-\d{2}-\d{2}$")
RE_DATE_TIME = re.compile(
    r"^\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}:\d{2}(\.\d+)?(Z|[+-]\d{2}:?\d{2})?$"
)
RE_TIME = re.compile(r"^\d{2}:\d{2}:\d{2}(\.\d+)?$")
RE_EMAIL = re.compile(r"^[^@\s]+@[^@\s]+\.[^@\s]+$")
RE_UUID = re.compile(
    r"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
)
RE_HOSTNAME = re.compile(r"^[a-zA-Z0-9.-]+$")
RE_IPV4 = re.compile(r"^(?:\d{1,3}\.){3}\d{1,3}$")
RE_IPV6 = re.compile(r"^[0-9a-fA-F:]+$")


def check_format(value: str, fmt: str) -> str | None:
    if not fmt:
        return None
    f = fmt.lower()
    if f == "date":
        return None if RE_DATE.match(value) else f"not date format (yyyy-mm-dd): {value!r}"
    if f in ("date-time", "datetime"):
        return None if RE_DATE_TIME.match(value) else f"not date-time format: {value!r}"
    if f == "time":
        return None if RE_TIME.match(value) else f"not time format: {value!r}"
    if f == "email":
        return None if RE_EMAIL.match(value) else f"not email format: {value!r}"
    if f == "uuid":
        return None if RE_UUID.match(value) else f"not uuid format: {value!r}"
    if f == "hostname":
        return None if RE_HOSTNAME.match(value) else f"not hostname format: {value!r}"
    if f == "ipv4":
        return None if RE_IPV4.match(value) else f"not ipv4 format: {value!r}"
    if f == "ipv6":
        return None if RE_IPV6.match(value) else f"not ipv6 format: {value!r}"
    if f in ("int32", "int64"):
        return check_type(value, "integer")
    if f in ("float", "double"):
        return check_type(value, "number")
    return None  # unknown / unsupported format — don't penalize


def check_enum(value: str, enum_csv: str) -> str | None:
    if not enum_csv:
        return None
    enum = [e.strip().strip("'\"") for e in enum_csv.split(",") if e.strip()]
    if not enum:
        return None
    return None if value in enum else f"not in enum {enum}: {value!r}"


def check_numeric_range(value: str, min_str: str, max_str: str) -> str | None:
    n = _parse_float(value)
    if n is None:
        return None  # type check already covers this
    if min_str:
        try:
            lo = float(min_str)
            if n < lo:
                return f"value {n} below minimum {lo}"
        except ValueError:
            pass
    if max_str:
        try:
            hi = float(max_str)
            if n > hi:
                return f"value {n} above maximum {hi}"
        except ValueError:
            pass
    return None


def check_length(value: str, min_str: str, max_str: str) -> str | None:
    L = len(value)
    if min_str:
        try:
            lo = int(min_str)
            if L < lo:
                return f"length {L} below minLength {lo}"
        except ValueError:
            pass
    if max_str:
        try:
            hi = int(max_str)
            if L > hi:
                return f"length {L} above maxLength {hi}"
        except ValueError:
            pass
    return None


def check_pattern(value: str, pattern: str) -> str | None:
    if not pattern:
        return None
    try:
        # OpenAPI patterns are anchored implicitly only when the schema asks
        # — JSON Schema matches anywhere by default. We honour that.
        if re.search(pattern, value):
            return None
        return f"value does not match pattern {pattern!r}: {value!r}"
    except re.error:
        return None


# Shape-only sanity check: catches the LLM "I'll fabricate a UUID-shaped
# string by pattern-matching" failure mode that the prompt's stated
# constraints (just `type: string`) cannot detect. Flagged when the value
# matches the structural shape of a UUID/email/datetime but fails the
# format-validity test for that shape.

UUID_SHAPE_RX = re.compile(r"^[A-Za-z0-9]{8}-[A-Za-z0-9]{4,12}-[A-Za-z0-9]{4,12}-[A-Za-z0-9]{4,12}-[A-Za-z0-9]{4,16}$")
UUID_VALID_RX = re.compile(r"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
EMAIL_SHAPE_RX = re.compile(r"^\S+@\S+$")


def check_shape_sanity(value: str) -> str | None:
    """Catch LLM-fabricated values that copy a structural pattern but break
    the format invariants. Specifically:
      - UUID-shaped strings whose hex digits include non-hex letters
        (`a3b2c1d4-ijkl-1234-5678-abcdef9015`).
      - Email-shaped strings missing a domain dot.
    Returns a violation reason or None.
    """
    if not value:
        return None
    # Fake-UUID detector.
    if UUID_SHAPE_RX.match(value) and not UUID_VALID_RX.match(value):
        return f"value has UUID-like shape but is not a valid UUID: {value!r}"
    # Email-shape sanity.
    if EMAIL_SHAPE_RX.match(value) and "." not in value.split("@", 1)[1]:
        return f"value has email-like shape but no domain: {value!r}"
    return None


# ---------------------------------------------------------------------------
# Driver
# ---------------------------------------------------------------------------

def evaluate_row(row: dict) -> tuple[str, list[str]]:
    """Return (status, [violations]) where status is one of:
        'valid'       — passes every stated constraint
        'hallucinated' — violates at least one stated constraint
        'abstained'   — LLM emitted an abstention sentinel (NO_GOOD_MATCH /
                        NO_VALUES_FOUND / NO_VALUES_GENERATED) — these are
                        legitimate "I cannot" signals and are NOT counted as
                        hallucinations (we track them separately as a different
                        quality concern: how often the LLM gives up).
        'no_constraint' — the prompt did not declare any verifiable constraint.
    """
    value = row["value"]

    has_constraint = any(row.get(k) for k in (
        "type", "format", "enum", "minimum", "maximum", "min_length", "max_length", "pattern",
    ))
    if not has_constraint:
        return "no_constraint", []

    # An empty string here means the parser stripped the response down to
    # nothing — either the LLM emitted only whitespace or it returned an
    # abstention sentinel that mine_llm_log.py filtered out. Treat as abstain.
    if value is None or not str(value).strip():
        return "abstained", ["abstained: LLM emitted no value"]

    violations: list[str] = []
    for label, fn in (
        ("type", lambda: check_type(value, row.get("type", ""))),
        ("format", lambda: check_format(value, row.get("format", ""))),
        ("enum", lambda: check_enum(value, row.get("enum", ""))),
        ("range", lambda: check_numeric_range(value, row.get("minimum", ""), row.get("maximum", ""))),
        ("length", lambda: check_length(value, row.get("min_length", ""), row.get("max_length", ""))),
        ("pattern", lambda: check_pattern(value, row.get("pattern", ""))),
        ("shape", lambda: check_shape_sanity(value)),
    ):
        msg = fn()
        if msg:
            violations.append(f"{label}: {msg}")
    return ("hallucinated" if violations else "valid"), violations


def main(argv: list[str]) -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--pairs", required=True, type=Path, help="llm_pairs.csv from mine_llm_log.py")
    p.add_argument("--out-dir", required=True, type=Path, help="Output directory")
    p.add_argument("--include-categories", default="positive_gen,diverse_gen,simple_gen",
                   help="Comma-separated prompt_category values to score "
                        "(default: positive_gen,diverse_gen,simple_gen — the prompt categories that "
                        "ask the LLM to *generate* a value against stated constraints). "
                        "bulk_extract / extraction are excluded because they ask the LLM to *find* "
                        "values in a real API response; their abstention sentinels (NO_GOOD_MATCH, "
                        "NO_VALUES_FOUND) are not hallucinations.")
    args = p.parse_args(argv)

    args.out_dir.mkdir(parents=True, exist_ok=True)
    keep_categories = {c.strip() for c in args.include_categories.split(",") if c.strip()}

    per_row_path = args.out_dir / "d3_per_row.csv"
    cols = [
        "log_file", "request_id", "model", "prompt_category", "parameter",
        "type", "format", "value", "status", "violations",
    ]

    counts = {"total": 0, "scored": 0, "valid": 0, "hallucinated": 0, "abstained": 0, "no_constraint": 0}
    by_violation: dict[str, int] = defaultdict(int)
    by_category: dict[str, dict] = defaultdict(
        lambda: {"total": 0, "valid": 0, "hallucinated": 0, "abstained": 0}
    )
    by_model: dict[str, dict] = defaultdict(
        lambda: {"total": 0, "valid": 0, "hallucinated": 0, "abstained": 0}
    )
    by_param: dict[str, dict] = defaultdict(
        lambda: {"total": 0, "hallucinated": 0, "abstained": 0, "examples": []}
    )

    with args.pairs.open("r", encoding="utf-8", newline="") as in_fh, \
         per_row_path.open("w", encoding="utf-8", newline="") as out_fh:
        reader = csv.DictReader(in_fh)
        writer = csv.DictWriter(out_fh, fieldnames=cols, quoting=csv.QUOTE_MINIMAL)
        writer.writeheader()
        for row in reader:
            counts["total"] += 1
            cat = row.get("prompt_category", "")
            by_category[cat]["total"] += 1
            if cat not in keep_categories:
                continue
            counts["scored"] += 1

            status, violations = evaluate_row(row)
            counts[status] = counts.get(status, 0) + 1
            by_category[cat][status] = by_category[cat].get(status, 0) + 1

            for v in violations:
                if status != "hallucinated":
                    continue
                kind = v.split(":", 1)[0]
                by_violation[kind] += 1

            model = row.get("model", "") or ""
            by_model[model]["total"] += 1
            by_model[model][status] = by_model[model].get(status, 0) + 1

            param = row.get("parameter", "") or "<unknown>"
            pp = by_param[param]
            pp["total"] += 1
            if status == "hallucinated":
                pp["hallucinated"] += 1
                if len(pp["examples"]) < 3:
                    pp["examples"].append({
                        "value": row["value"],
                        "violations": violations[:3],
                    })
            elif status == "abstained":
                pp["abstained"] += 1

            writer.writerow({
                "log_file": row.get("log_file", ""),
                "request_id": row.get("request_id", ""),
                "model": model,
                "prompt_category": cat,
                "parameter": param,
                "type": row.get("type", ""),
                "format": row.get("format", ""),
                "value": row["value"],
                "status": status,
                "violations": " | ".join(violations),
            })

    # ----- Aggregations -----
    per_param_path = args.out_dir / "d3_per_param.csv"
    with per_param_path.open("w", encoding="utf-8", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(["parameter", "total", "hallucinated", "abstained", "lhr"])
        for param, v in sorted(by_param.items()):
            actionable = v["total"] - v["abstained"]
            lhr = (v["hallucinated"] / actionable) if actionable else 0.0
            w.writerow([param, v["total"], v["hallucinated"], v["abstained"], f"{lhr:.4f}"])

    # LHR's denominator is *actionable* rows: scored - abstained. Abstentions
    # ('NO_GOOD_MATCH' / 'NO_VALUES_GENERATED') are a legitimate response to
    # "I cannot generate" instructions, not a hallucination, so excluding them
    # from the denominator gives the truthful constraint-violation rate.
    actionable = counts["scored"] - counts.get("abstained", 0)
    overall_lhr = (counts.get("hallucinated", 0) / actionable) if actionable else None
    abstain_rate = (counts.get("abstained", 0) / counts["scored"]) if counts["scored"] else None
    summary = {
        "metric": "D3 LLM Hallucination Rate",
        "scored_categories": sorted(keep_categories),
        "rows_total": counts["total"],
        "rows_scored": counts["scored"],
        "rows_actionable": actionable,
        "rows_valid": counts.get("valid", 0),
        "rows_hallucinated": counts.get("hallucinated", 0),
        "rows_abstained": counts.get("abstained", 0),
        "rows_no_constraint": counts.get("no_constraint", 0),
        "lhr": overall_lhr,
        "lhr_definition": "hallucinated / (scored - abstained)",
        "lhr_threshold_pass": (overall_lhr is not None) and (overall_lhr <= 0.20),
        "abstain_rate": abstain_rate,
        "by_constraint_violation": dict(sorted(by_violation.items(), key=lambda x: -x[1])),
        "by_prompt_category": {
            k: {
                "total": v["total"],
                "valid": v.get("valid", 0),
                "hallucinated": v.get("hallucinated", 0),
                "abstained": v.get("abstained", 0),
                "lhr": (
                    v.get("hallucinated", 0) / (v["total"] - v.get("abstained", 0))
                    if (v["total"] - v.get("abstained", 0)) else None
                ),
            } for k, v in by_category.items()
        },
        "by_model": {
            k: {
                "total": v["total"],
                "valid": v.get("valid", 0),
                "hallucinated": v.get("hallucinated", 0),
                "abstained": v.get("abstained", 0),
                "lhr": (
                    v.get("hallucinated", 0) / (v["total"] - v.get("abstained", 0))
                    if (v["total"] - v.get("abstained", 0)) else None
                ),
            } for k, v in by_model.items()
        },
        "worst_parameters": sorted(
            (
                {
                    "parameter": param,
                    "total": v["total"],
                    "hallucinated": v["hallucinated"],
                    "abstained": v["abstained"],
                    "lhr": (
                        v["hallucinated"] / (v["total"] - v["abstained"])
                        if (v["total"] - v["abstained"]) else 0.0
                    ),
                    "examples": v["examples"],
                }
                for param, v in by_param.items()
                if (v["total"] - v["abstained"]) >= 3
            ),
            key=lambda x: -x["lhr"],
        )[:15],
        "computed_at": datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z"),
    }
    (args.out_dir / "d3_summary.json").write_text(json.dumps(summary, indent=2))
    lhr_str = f"{overall_lhr:.4f}" if overall_lhr is not None else "N/A"
    abstain_str = f"{abstain_rate:.4f}" if abstain_rate is not None else "N/A"
    print(f"validate_d3: LHR={lhr_str} "
          f"({counts.get('hallucinated', 0)}/{actionable} actionable values violated a stated constraint; "
          f"abstain_rate={abstain_str}, {counts.get('abstained', 0)} legitimate 'cannot generate' responses) "
          f"→ {args.out_dir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
