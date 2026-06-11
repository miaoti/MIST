#!/usr/bin/env python3
"""
Mine RESTest's LLM communication log.

Walks one or more LLM log files and emits one row per (LLM request, emitted value)
pair, with the constraints stated in the prompt attached so D3 can validate
the LLM's output against what the prompt told it.

Output CSV columns:
    log_file, request_id, timestamp, model, prompt_category,
    parameter, type, format, location, required,
    enum, minimum, maximum, min_length, max_length, pattern,
    response_index, value

prompt_category:
    positive_gen : the canonical "expert API tester" positive-value prompt
                   (full Type / Format / Enum / Min / Max / Pattern block)
    diverse_gen  : "Generate N additional values that are semantically similar..."
                   (constraint set is just Type)
    extraction   : smart-fetch direct extraction ("API Response: ...")
                   (constraint set is just Type)
    negative_gen : asks the LLM to violate constraints (excluded from D3 LHR)
    other        : everything else (validation, classification, etc.)

Usage:
    python3 mine_llm_log.py --log <PATH> --out llm_pairs.csv
    python3 mine_llm_log.py --log-dir <DIR> --out llm_pairs.csv  # all *.log files
"""

from __future__ import annotations

import argparse
import csv
import json
import re
import sys
from pathlib import Path

# ---------------------------------------------------------------------------
# Block extraction
# ---------------------------------------------------------------------------

REQ_HEADER = "🚀 LLM REQUEST #"
RESP_HEADER = "🎯 LLM RESPONSE #"
USER_PROMPT_HEADER = "👤 USER PROMPT:"
LLM_RESP_HEADER = "🤖 LLM RESPONSE:"
SEP = "--------------------------------------------------------------------------------"

RE_REQ_ID = re.compile(r"Request ID:\s*(\d+)")
RE_TIMESTAMP = re.compile(r"Timestamp:\s*([\d\-: \.]+)")
RE_MODEL = re.compile(r"Model Name:\s*(\S+)")


def split_into_blocks(text: str) -> list[str]:
    """Split a log file into per-request blocks."""
    parts = text.split(REQ_HEADER)
    return [REQ_HEADER + p for p in parts[1:]]


def extract_section(block: str, start_marker: str) -> str | None:
    """Return everything between `start_marker` and the next `---...---` separator."""
    idx = block.find(start_marker)
    if idx < 0:
        return None
    after = block[idx + len(start_marker):].lstrip("\n")
    sep_idx = after.find(SEP)
    if sep_idx < 0:
        return after.rstrip()
    return after[:sep_idx].rstrip()


# ---------------------------------------------------------------------------
# Prompt category detection
# ---------------------------------------------------------------------------

POSITIVE_GEN_HALLMARK = "expert API tester. Generate"
DIVERSE_GEN_HALLMARK = "Generate "
DIVERSE_GEN_HALLMARK_2 = "additional values that are semantically similar"
EXTRACTION_HALLMARK = "API Response:"
NEGATIVE_GEN_HALLMARKS = (
    "Generate INVALID values",
    "TYPE MISMATCH",
    "REGEX MISMATCH",
    "SEMANTIC MISMATCH",
    "BOUNDARY VIOLATION",
    "OVERFLOW",
    "Generate values with malicious",
    "SPECIAL CHARACTERS",
    "Generate 5-8 values with SPECIAL CHARACTERS",
)


SIMPLE_GEN_HALLMARK = "Generate a realistic test value for the following parameter"
BULK_EXTRACT_HALLMARK = "Extract ALL possible values from this JSON response"


def classify_prompt(user_prompt: str) -> str:
    if not user_prompt:
        return "other"
    for h in NEGATIVE_GEN_HALLMARKS:
        if h in user_prompt:
            return "negative_gen"
    if POSITIVE_GEN_HALLMARK in user_prompt:
        return "positive_gen"
    if DIVERSE_GEN_HALLMARK_2 in user_prompt:
        return "diverse_gen"
    if SIMPLE_GEN_HALLMARK in user_prompt:
        return "simple_gen"
    if BULK_EXTRACT_HALLMARK in user_prompt and "parameter '" in user_prompt:
        return "bulk_extract"
    if EXTRACTION_HALLMARK in user_prompt and "Target Parameter:" in user_prompt:
        return "extraction"
    return "other"


# ---------------------------------------------------------------------------
# Constraint parsing
# ---------------------------------------------------------------------------

# Field labels we look for in the prompt's [Parameter Details] / [Constraints]
# blocks. Each maps to a regex that pulls the value off the rest of the line.
# `Parameter Type:` (used by `simple_gen`) is recognised in addition to `Type:`
# (used by `positive_gen`).
FIELD_PATTERNS = {
    "parameter":  re.compile(r"^\s*Parameter Name\s*:\s*(.+?)\s*$",          re.MULTILINE),
    "location":   re.compile(r"^\s*Location\s*:\s*(.+?)\s*$",                re.MULTILINE),
    "type":       re.compile(r"^\s*(?:Parameter\s+)?Type\s*:\s*(.+?)\s*$",   re.MULTILINE),
    "format":     re.compile(r"^\s*Format\s*:\s*(.+?)\s*$",                  re.MULTILINE),
    "required":   re.compile(r"^\s*Required\s*:\s*(.+?)\s*$",                re.MULTILINE),
    "enum":       re.compile(r"^\s*Enum\s*:\s*\[?(.+?)\]?\s*$",              re.MULTILINE),
    "minimum":    re.compile(r"^\s*Min(?:imum)?\s*:\s*(.+?)\s*$",            re.MULTILINE),
    "maximum":    re.compile(r"^\s*Max(?:imum)?\s*:\s*(.+?)\s*$",            re.MULTILINE),
    "min_length": re.compile(r"^\s*MinLength\s*:\s*(.+?)\s*$",               re.MULTILINE),
    "max_length": re.compile(r"^\s*MaxLength\s*:\s*(.+?)\s*$",               re.MULTILINE),
    "pattern":    re.compile(r"^\s*Pattern\s*:\s*(.+?)\s*$",                 re.MULTILINE),
}

# Fallback patterns for diverse_gen / extraction prompts.
RE_DIVERSE_PARAM = re.compile(
    r"semantically similar to the existing values for parameter\s+'?([^'(\s]+)'?\s*\(type:\s*([^)]+)\)"
)
RE_TARGET_PARAM = re.compile(
    r"Target Parameter:\s*([^\s(]+)\s*\(type:\s*([^)]+)\)"
)
RE_BULK_EXTRACT_PARAM = re.compile(
    r"values from this JSON response that could be used for parameter\s+'?([^'(\s]+)'?\s*\(type:\s*([^)]+)\)"
)


def _split_type_and_format(raw_type: str) -> tuple[str, str]:
    """Decompose `'integer (int32)'` → `('integer', 'int32')`.
    Several RESTest LLM prompts include the OAS `format` as a parenthetical
    after the type (e.g. `Type: integer (int32)`, `string (date)`,
    `number (double)`). The constraint validator handles them separately so
    `integer (int32)` would otherwise pass through type validation
    unconditionally — an LLM emitting `'2024-01-01'` for an int32 param would
    not be flagged. Split them so D3 LHR validates both axes."""
    if not raw_type:
        return "", ""
    m = re.match(r"^\s*([A-Za-z][A-Za-z0-9]*)\s*\(([^)]+)\)\s*$", raw_type)
    if m:
        return m.group(1).strip().lower(), m.group(2).strip().lower()
    return raw_type.strip().lower(), ""


def parse_constraints(user_prompt: str, category: str) -> dict:
    """Return a dict of constraint fields. Missing fields are absent from the dict."""
    out: dict = {}
    if category in ("positive_gen", "simple_gen"):
        for key, pattern in FIELD_PATTERNS.items():
            m = pattern.search(user_prompt)
            if m:
                out[key] = m.group(1).strip()
        # Extract a parenthetical format from the type field if present and
        # promote it to a top-level format constraint for D3 validation.
        if out.get("type"):
            t_norm, fmt = _split_type_and_format(out["type"])
            out["type"] = t_norm
            if fmt and not out.get("format"):
                out["format"] = fmt
    elif category == "diverse_gen":
        m = RE_DIVERSE_PARAM.search(user_prompt)
        if m:
            out["parameter"] = m.group(1).strip()
            t_norm, fmt = _split_type_and_format(m.group(2).strip())
            out["type"] = t_norm
            if fmt:
                out["format"] = fmt
    elif category == "bulk_extract":
        m = RE_BULK_EXTRACT_PARAM.search(user_prompt)
        if m:
            out["parameter"] = m.group(1).strip()
            t_norm, fmt = _split_type_and_format(m.group(2).strip())
            out["type"] = t_norm
            if fmt:
                out["format"] = fmt
    elif category == "extraction":
        m = RE_TARGET_PARAM.search(user_prompt)
        if m:
            out["parameter"] = m.group(1).strip()
            t_norm, fmt = _split_type_and_format(m.group(2).strip())
            out["type"] = t_norm
            if fmt:
                out["format"] = fmt
    return out


# ---------------------------------------------------------------------------
# Response parsing
# ---------------------------------------------------------------------------

# Sentinel responses the LLM is told to emit when it cannot produce a value.
# These are NOT hallucinations — they're a legitimate "I can't" signal — and we
# strip them rather than feeding them into the constraint validator.
LLM_ABSTAIN_SENTINELS = {
    "NO_GOOD_MATCH",
    "NO_VALUES_FOUND",
    "NO_VALUES_GENERATED",
    "NO MATCH",
    "NONE",
}

# Strict bullet/number-prefix stripper. Only matches:
#   - "1. ", "42) "  (digits then '.' or ')' then whitespace)
#   - "- ", "* ", "• " (single bullet glyph then whitespace)
# IMPORTANT: it must NOT match value strings that begin with digits (e.g. dates
# like '2026-04-26' or numeric IDs). The previous version used [-*\d]+ which
# greedily consumed those values and produced spurious empty rows.
LIST_PREFIX_RX = re.compile(r"^(?:\d+[.)]\s+|[-*•]\s+)")


def parse_response_values(response_text: str, category: str) -> list[str]:
    """Split the LLM response into the discrete values it emitted.
    For positive_gen / diverse_gen / simple_gen / bulk_extract the canonical
    format is one value per line. For extraction it's a single value (often a
    sentinel like NO_GOOD_MATCH).

    Markdown fences are stripped, surrounding quotes removed, list prefixes
    (`1. `, `- `) stripped, and abstention sentinels filtered out.
    """
    if response_text is None:
        return []
    text = response_text.strip()
    if not text:
        return []
    # Strip code fences
    text = re.sub(r"^```(?:\w+)?\s*", "", text)
    text = re.sub(r"\s*```$", "", text)
    # Try JSON array first (common for array-typed params)
    if text.startswith("["):
        try:
            arr = json.loads(text)
            if isinstance(arr, list):
                return [str(x) for x in arr if str(x).strip() and str(x).strip() not in LLM_ABSTAIN_SENTINELS]
        except json.JSONDecodeError:
            pass
    # One value per line
    values: list[str] = []
    for ln in text.split("\n"):
        s = ln.strip()
        if not s:
            continue
        s = LIST_PREFIX_RX.sub("", s, count=1).strip()
        if not s:
            continue
        # strip surrounding quotes
        if (s.startswith('"') and s.endswith('"')) or (s.startswith("'") and s.endswith("'")):
            s = s[1:-1]
        if s in LLM_ABSTAIN_SENTINELS:
            continue
        values.append(s)
    if category in ("extraction", "simple_gen"):
        return values[:1]
    return values


# ---------------------------------------------------------------------------
# Driver
# ---------------------------------------------------------------------------

def process_log(log_path: Path, writer: csv.DictWriter) -> tuple[int, int]:
    text = log_path.read_text(errors="replace")
    blocks = split_into_blocks(text)
    rows = 0
    requests = 0
    for block in blocks:
        requests += 1
        req_id_m = RE_REQ_ID.search(block)
        ts_m = RE_TIMESTAMP.search(block)
        model_m = RE_MODEL.search(block)

        user_prompt = extract_section(block, USER_PROMPT_HEADER) or ""
        response = extract_section(block, LLM_RESP_HEADER) or ""

        category = classify_prompt(user_prompt)
        constraints = parse_constraints(user_prompt, category)
        values = parse_response_values(response, category)

        base = {
            "log_file": log_path.name,
            "request_id": req_id_m.group(1) if req_id_m else "",
            "timestamp": ts_m.group(1) if ts_m else "",
            "model": model_m.group(1) if model_m else "",
            "prompt_category": category,
            "parameter": constraints.get("parameter", ""),
            "type": constraints.get("type", ""),
            "format": constraints.get("format", ""),
            "location": constraints.get("location", ""),
            "required": constraints.get("required", ""),
            "enum": constraints.get("enum", ""),
            "minimum": constraints.get("minimum", ""),
            "maximum": constraints.get("maximum", ""),
            "min_length": constraints.get("min_length", ""),
            "max_length": constraints.get("max_length", ""),
            "pattern": constraints.get("pattern", ""),
        }
        if not values:
            # Still emit one row so we can count empty responses
            writer.writerow({**base, "response_index": 0, "value": ""})
            rows += 1
            continue
        for idx, v in enumerate(values):
            writer.writerow({**base, "response_index": idx, "value": v})
            rows += 1
    return requests, rows


def main(argv: list[str]) -> int:
    p = argparse.ArgumentParser(description=__doc__)
    g = p.add_mutually_exclusive_group(required=True)
    g.add_argument("--log", type=Path, help="Single LLM log file.")
    g.add_argument("--log-dir", type=Path, help="Directory of LLM log files (mines all *.log).")
    p.add_argument("--out", required=True, type=Path, help="Output CSV path.")
    args = p.parse_args(argv)

    if args.log:
        log_files = [args.log]
    else:
        log_files = sorted(args.log_dir.glob("*.log"))

    if not log_files:
        print("error: no log files found", file=sys.stderr)
        return 2

    cols = [
        "log_file", "request_id", "timestamp", "model", "prompt_category",
        "parameter", "type", "format", "location", "required",
        "enum", "minimum", "maximum", "min_length", "max_length", "pattern",
        "response_index", "value",
    ]
    args.out.parent.mkdir(parents=True, exist_ok=True)
    total_requests = 0
    total_rows = 0
    with args.out.open("w", encoding="utf-8", newline="") as fh:
        w = csv.DictWriter(fh, fieldnames=cols, quoting=csv.QUOTE_MINIMAL)
        w.writeheader()
        for log in log_files:
            r, n = process_log(log, w)
            total_requests += r
            total_rows += n
            print(f"  {log.name}: {r} requests, {n} value rows")
    print(f"mine_llm_log: {len(log_files)} log(s), {total_requests} requests, {total_rows} rows → {args.out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
