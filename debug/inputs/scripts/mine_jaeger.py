#!/usr/bin/env python3
"""
Walk Jaeger trace JSON files and emit every value observed in a span as a
candidate "output" for D5 ID-Resolvability lookup.

A Jaeger trace export has the shape:

    {
        "data": [
            {
                "traceID": "...",
                "spans": [
                    {"spanID": "...", "operationName": "...",
                     "tags": [{"key": "...", "value": "..."}, ...],
                     "logs": [...], "process": ...},
                    ...
                ],
                "processes": {...}
            },
            ...
        ]
    }

For each span we record every UUID-looking and long-numeric tag value, plus
any leaf string from a parsed `http.response.body` / `db.statement` / `peer.*`
tag. Other tag keys are skipped because their values are not the kind of
identifier D5 cares about.

Output CSV columns:
    trace_id, span_id, service, operation, source_field, value

The downstream validator (validate_d5.py) reads this file and turns it into
a set of "values that ever appeared as outputs anywhere in any span".
"""

from __future__ import annotations

import argparse
import csv
import json
import re
import sys
from pathlib import Path
from typing import Iterable

UUID_RX = re.compile(
    r"\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\b"
)
LONG_NUM_RX = re.compile(r"\b\d{5,}\b")

# Tag keys whose values are unlikely to be IDs — skip to keep the output set
# focused.
NOISE_TAG_PREFIXES = (
    "internal.", "otel.", "span.", "tracestate", "sampler.",
    "thread.", "process.", "host.", "telemetry.",
    "db.connection_string", "peer.",
    # 'net.*.port' keys (net.peer.port, net.host.port, net.sock.peer.port,
    # net.sock.host.port) are pure socket metadata and dominated the
    # observed-values set with port numbers in the 11k-19k range — those
    # would false-positive against any 5-digit numeric ID input.
    "net.",
)


def iter_traces(path: Path) -> Iterable[dict]:
    """Yield each trace dict from a Jaeger export file."""
    with path.open("r", encoding="utf-8", errors="replace") as fh:
        try:
            doc = json.load(fh)
        except json.JSONDecodeError:
            return
    if isinstance(doc, dict) and isinstance(doc.get("data"), list):
        yield from doc["data"]
    elif isinstance(doc, list):
        yield from doc


def extract_id_candidates(value: str) -> set[str]:
    """Return all UUID and long-int substrings inside `value`."""
    out: set[str] = set()
    if value is None:
        return out
    for m in UUID_RX.finditer(value):
        out.add(m.group(0))
    for m in LONG_NUM_RX.finditer(value):
        out.add(m.group(0))
    return out


def walk_json_leaves(node, prefix: str = "", out: list[tuple[str, str]] | None = None) -> list[tuple[str, str]]:
    """Yield (key_path, leaf_value) pairs from a parsed JSON object/array."""
    if out is None:
        out = []
    if isinstance(node, dict):
        for k, v in node.items():
            walk_json_leaves(v, f"{prefix}.{k}" if prefix else k, out)
    elif isinstance(node, list):
        for i, v in enumerate(node):
            walk_json_leaves(v, f"{prefix}[{i}]" if prefix else f"[{i}]", out)
    elif node is not None:
        out.append((prefix, str(node)))
    return out


def process_span(span: dict, processes: dict, writer: csv.writer) -> int:
    rows = 0
    span_id = span.get("spanID") or span.get("spanId") or ""
    op = span.get("operationName") or ""
    proc_id = span.get("processID") or ""
    service = ""
    if proc_id and isinstance(processes, dict):
        proc = processes.get(proc_id)
        if isinstance(proc, dict):
            service = proc.get("serviceName") or ""
    if not service and isinstance(span.get("process"), dict):
        service = span["process"].get("serviceName") or ""
    trace_id = span.get("traceID") or span.get("traceId") or ""

    for tag in span.get("tags") or []:
        if not isinstance(tag, dict):
            continue
        key = tag.get("key") or ""
        if any(key.startswith(p) for p in NOISE_TAG_PREFIXES):
            continue
        value = tag.get("value")
        if value is None:
            continue
        # Only string-shaped tag values can carry IDs. Cast numerics to str so
        # they pass through the long-number regex too.
        sval = str(value)

        # Tag keys that may carry a JSON document — try to parse and extract leaves.
        if key in ("http.response.body", "http.request.body", "db.statement"):
            try:
                doc = json.loads(sval)
                for path, leaf in walk_json_leaves(doc):
                    for cand in extract_id_candidates(leaf):
                        writer.writerow([trace_id, span_id, service, op, f"{key}#{path}", cand])
                        rows += 1
            except (json.JSONDecodeError, TypeError):
                # Plain SQL or non-JSON; still scan for ID substrings.
                for cand in extract_id_candidates(sval):
                    writer.writerow([trace_id, span_id, service, op, key, cand])
                    rows += 1
            continue

        # Generic tag — extract IDs by pattern.
        for cand in extract_id_candidates(sval):
            writer.writerow([trace_id, span_id, service, op, key, cand])
            rows += 1

        # Special case: tags whose key suggests they ARE an ID and whose
        # value is short, single-token, alphanumeric — record verbatim so
        # short/non-UUID/non-long-int IDs are still picked up.
        klow = key.lower()
        if (klow.endswith("id") or klow.endswith("uuid")) and re.fullmatch(r"[A-Za-z0-9_\-]{2,64}", sval):
            writer.writerow([trace_id, span_id, service, op, key, sval])
            rows += 1

    return rows


def main(argv: list[str]) -> int:
    p = argparse.ArgumentParser(description=__doc__)
    g = p.add_mutually_exclusive_group(required=True)
    g.add_argument("--trace-file", type=Path, help="Single Jaeger trace JSON file.")
    g.add_argument("--trace-dir", type=Path, help="Directory of Jaeger trace JSON files.")
    p.add_argument("--out", required=True, type=Path, help="Output CSV path.")
    args = p.parse_args(argv)

    if args.trace_file:
        files = [args.trace_file]
    else:
        files = sorted(args.trace_dir.glob("*.json"))

    if not files:
        print("error: no trace files found", file=sys.stderr)
        return 2

    args.out.parent.mkdir(parents=True, exist_ok=True)
    rows = 0
    spans_seen = 0
    with args.out.open("w", encoding="utf-8", newline="") as out_fh:
        w = csv.writer(out_fh, quoting=csv.QUOTE_MINIMAL)
        w.writerow(["trace_id", "span_id", "service", "operation", "source_field", "value"])
        for f in files:
            file_rows = 0
            file_spans = 0
            for trace in iter_traces(f):
                processes = trace.get("processes") if isinstance(trace, dict) else {}
                for span in trace.get("spans") or []:
                    file_spans += 1
                    file_rows += process_span(span, processes, w)
            print(f"  {f.name}: {file_spans} spans, {file_rows} extracted values")
            rows += file_rows
            spans_seen += file_spans
    print(f"mine_jaeger: {len(files)} file(s), {spans_seen} spans, {rows} extracted values → {args.out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
