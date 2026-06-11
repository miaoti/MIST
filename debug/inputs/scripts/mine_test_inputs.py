#!/usr/bin/env python3
"""
Mine generated test files for parameter inputs.

Walks every Flow_Scenario_*.java under a run directory and extracts:
  - HTTP operations (method + path) from `.when().<method>("/path")` calls
  - Body parameters from `String requestBodyN = "..."` declarations
  - Query parameters from `.queryParam("name", "value")` calls
  - Header parameters from `.header("name", "value")` calls (Authorization skipped)

Each test method (`test_positive_flow_*` / `test_negative_flow_*`) is processed
independently so we know which inputs belong to which variant.

Output CSV columns:
    run_id, scenario, test_method, test_kind (positive|negative), step_idx,
    http_method, path, parameter, location, value

Usage:
    python3 mine_test_inputs.py --run-dir <PATH> --out inputs.csv
"""

from __future__ import annotations

import argparse
import csv
import json
import re
import sys
from pathlib import Path
from typing import Iterable

# ---------------------------------------------------------------------------
# Regexes
# ---------------------------------------------------------------------------

# Match the start of a @Test method declaration so we can split a class file
# into per-method scopes. We capture only the method name; the body span is
# resolved by counting braces below.
RE_TEST_METHOD = re.compile(
    r"@Test\s*(?:\([^)]*\))?\s*public\s+void\s+(\w+)\s*\(\s*\)\s*throws\s+\w+\s*\{",
    re.MULTILINE,
)

# Body declarations look like: String requestBody3 = "{\"k\":\"v\"}";
# We use a non-greedy capture but allow embedded escape sequences (\" \\ \n).
RE_REQUEST_BODY = re.compile(
    r'String\s+(requestBody\d+)\s*=\s*"((?:\\.|[^"\\])*)"\s*;',
    re.DOTALL,
)

# Operation invocations: .when().post("/api/v1/...") and friends. Path is a
# string literal — concatenated paths (e.g. "/users/" + id) only capture the
# leading literal, which is sufficient for matching against OAS path templates.
RE_OPERATION = re.compile(
    r"\.when\s*\(\s*\)\s*\.\s*(get|post|put|delete|patch|head|options)"
    r'\s*\(\s*"([^"]+)"',
    re.IGNORECASE,
)

# Query / header / form-param invocations. Two-string-literal form only.
RE_QUERY_PARAM = re.compile(r'\.queryParam\s*\(\s*"([^"]+)"\s*,\s*"((?:\\.|[^"\\])*)"\s*\)')
RE_HEADER_PARAM = re.compile(r'\.header\s*\(\s*"([^"]+)"\s*,\s*"((?:\\.|[^"\\])*)"\s*\)')
RE_FORM_PARAM = re.compile(r'\.formParam\s*\(\s*"([^"]+)"\s*,\s*"((?:\\.|[^"\\])*)"\s*\)')

# Path-parameter literal: .pathParam("name", "value")
RE_PATH_PARAM = re.compile(r'\.pathParam\s*\(\s*"([^"]+)"\s*,\s*"((?:\\.|[^"\\])*)"\s*\)')


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def unescape_java_string(s: str) -> str:
    """Decode the escape sequences a Java string literal uses.
    The set Java uses (\\n \\t \\r \\\" \\\\ \\u####) is a subset of what JSON
    accepts inside a string, so we wrap the candidate in quotes and ask
    json.loads to do the work. Falls back to a manual replace on failure
    (e.g. for legitimately-malformed strings like "...\\xZZ...").
    """
    try:
        return json.loads(f'"{s}"')
    except json.JSONDecodeError:
        out = (
            s.replace("\\\\", "")  # protect literal backslash
             .replace('\\"', '"')
             .replace("\\n", "\n")
             .replace("\\r", "\r")
             .replace("\\t", "\t")
             .replace("", "\\")
        )
        return out


def find_method_spans(source: str) -> list[tuple[str, int, int]]:
    """Return [(method_name, body_start_inclusive, body_end_exclusive), ...].
    body_start is the index just after the opening '{' of the method body.
    body_end is the index of the matching '}' (exclusive).
    """
    spans: list[tuple[str, int, int]] = []
    for match in RE_TEST_METHOD.finditer(source):
        method_name = match.group(1)
        # The opening brace is the last char of the match.
        body_start = match.end()
        depth = 1
        i = body_start
        n = len(source)
        in_string = False
        in_char = False
        in_line_comment = False
        in_block_comment = False
        while i < n and depth > 0:
            c = source[i]
            nxt = source[i + 1] if i + 1 < n else ""
            if in_line_comment:
                if c == "\n":
                    in_line_comment = False
            elif in_block_comment:
                if c == "*" and nxt == "/":
                    in_block_comment = False
                    i += 1
            elif in_string:
                if c == "\\":
                    i += 1  # skip escape
                elif c == '"':
                    in_string = False
            elif in_char:
                if c == "\\":
                    i += 1
                elif c == "'":
                    in_char = False
            elif c == "/" and nxt == "/":
                in_line_comment = True
                i += 1
            elif c == "/" and nxt == "*":
                in_block_comment = True
                i += 1
            elif c == '"':
                in_string = True
            elif c == "'":
                in_char = True
            elif c == "{":
                depth += 1
            elif c == "}":
                depth -= 1
                if depth == 0:
                    spans.append((method_name, body_start, i))
                    break
            i += 1
    return spans


def classify_test_method(name: str) -> str:
    if "_negative_" in name:
        return "negative"
    if "_positive_" in name:
        return "positive"
    return "unknown"


def extract_step_inputs(method_body: str) -> list[dict]:
    """For a single test-method body, walk the source and emit one record per
    HTTP operation found. Each record contains the operation, the body that
    immediately preceded it, and the query/header/path/form params declared
    between the previous operation and this one.
    """
    # Pre-scan all anchors with their start indices, then merge.
    anchors: list[tuple[int, str, tuple]] = []  # (pos, kind, payload)
    for m in RE_REQUEST_BODY.finditer(method_body):
        anchors.append((m.start(), "body", (m.group(1), m.group(2))))
    for m in RE_OPERATION.finditer(method_body):
        anchors.append((m.start(), "op", (m.group(1).lower(), m.group(2))))
    for m in RE_QUERY_PARAM.finditer(method_body):
        anchors.append((m.start(), "query", (m.group(1), m.group(2))))
    for m in RE_HEADER_PARAM.finditer(method_body):
        if m.group(1).lower() == "authorization":
            continue
        anchors.append((m.start(), "header", (m.group(1), m.group(2))))
    for m in RE_FORM_PARAM.finditer(method_body):
        anchors.append((m.start(), "form", (m.group(1), m.group(2))))
    for m in RE_PATH_PARAM.finditer(method_body):
        anchors.append((m.start(), "path", (m.group(1), m.group(2))))
    anchors.sort(key=lambda x: x[0])

    steps: list[dict] = []
    pending_body: tuple[str, str] | None = None
    pending_query: list[tuple[str, str]] = []
    pending_header: list[tuple[str, str]] = []
    pending_form: list[tuple[str, str]] = []
    pending_path: list[tuple[str, str]] = []

    for _pos, kind, payload in anchors:
        if kind == "body":
            pending_body = payload  # last body before the operation wins
        elif kind == "query":
            pending_query.append(payload)
        elif kind == "header":
            pending_header.append(payload)
        elif kind == "form":
            pending_form.append(payload)
        elif kind == "path":
            pending_path.append(payload)
        elif kind == "op":
            steps.append(
                {
                    "method": payload[0].upper(),
                    "path": payload[1],
                    "body": pending_body,
                    "query": pending_query,
                    "header": pending_header,
                    "form": pending_form,
                    "path_params": pending_path,
                }
            )
            pending_body = None
            pending_query, pending_header, pending_form, pending_path = [], [], [], []
    return steps


def expand_body_to_rows(body_var: str, body_raw: str) -> Iterable[tuple[str, str]]:
    """Decode the Java string literal and try to parse as JSON. If JSON, yield
    one (parameter, value) pair per top-level key. If not JSON, yield a single
    record under the synthetic name in `body_var`.
    """
    body_str = unescape_java_string(body_raw)
    try:
        parsed = json.loads(body_str)
    except json.JSONDecodeError:
        yield (body_var, body_str)
        return
    if isinstance(parsed, dict):
        for k, v in parsed.items():
            yield (k, json.dumps(v) if not isinstance(v, str) else v)
    elif isinstance(parsed, list):
        # array body — emit a single row under a synthetic key so the
        # downstream validator can validate the whole array against the schema.
        yield ("__array_body__", json.dumps(parsed))
    else:
        yield ("__primitive_body__", json.dumps(parsed))


# ---------------------------------------------------------------------------
# Driver
# ---------------------------------------------------------------------------

def process_file(java_path: Path, run_id: str, scenario: str, writer: csv.DictWriter) -> int:
    source = java_path.read_text(errors="replace")
    methods = find_method_spans(source)
    rows = 0
    for method_name, start, end in methods:
        body = source[start:end]
        kind = classify_test_method(method_name)
        steps = extract_step_inputs(body)
        for step_idx, step in enumerate(steps, start=1):
            base = {
                "run_id": run_id,
                "scenario": scenario,
                "test_method": method_name,
                "test_kind": kind,
                "step_idx": step_idx,
                "http_method": step["method"],
                "path": step["path"],
            }
            # Body
            if step["body"]:
                _var, raw = step["body"]
                for param, value in expand_body_to_rows(_var, raw):
                    writer.writerow({**base, "parameter": param, "location": "body", "value": value})
                    rows += 1
            # Query / header / form / path
            for name, value in step["query"]:
                writer.writerow({**base, "parameter": name, "location": "query", "value": unescape_java_string(value)})
                rows += 1
            for name, value in step["header"]:
                writer.writerow({**base, "parameter": name, "location": "header", "value": unescape_java_string(value)})
                rows += 1
            for name, value in step["form"]:
                writer.writerow({**base, "parameter": name, "location": "formData", "value": unescape_java_string(value)})
                rows += 1
            for name, value in step["path_params"]:
                writer.writerow({**base, "parameter": name, "location": "path", "value": unescape_java_string(value)})
                rows += 1
    return rows


def main(argv: list[str]) -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--run-dir", required=True, type=Path,
                   help="Directory containing Flow_Scenario_*.java files.")
    p.add_argument("--out", required=True, type=Path, help="Output CSV path.")
    args = p.parse_args(argv)

    if not args.run_dir.is_dir():
        print(f"error: --run-dir is not a directory: {args.run_dir}", file=sys.stderr)
        return 2

    run_id = args.run_dir.name
    args.out.parent.mkdir(parents=True, exist_ok=True)

    cols = [
        "run_id", "scenario", "test_method", "test_kind",
        "step_idx", "http_method", "path",
        "parameter", "location", "value",
    ]
    total_rows = 0
    files_seen = 0
    with args.out.open("w", encoding="utf-8", newline="") as fh:
        w = csv.DictWriter(fh, fieldnames=cols, quoting=csv.QUOTE_MINIMAL)
        w.writeheader()
        for java_path in sorted(args.run_dir.glob("Flow_Scenario_*.java")):
            scenario = java_path.stem  # "Flow_Scenario_1"
            n = process_file(java_path, run_id, scenario, w)
            total_rows += n
            files_seen += 1
    print(f"mine_test_inputs: {files_seen} files, {total_rows} input rows → {args.out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
