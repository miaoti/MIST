#!/usr/bin/env python3
"""
Mine provenance for every (parameter, value) emitted by the input pipeline.

Walks the run's execution log (logs/.../trainticket_test_execution.log) and
records, for each (parameter, value) it sees, the FIRST source that produced
that value. This is the canonical "where did this value come from?" map used
by D4 (SFHR).

Recognised provenance categories — these are exhaustive over the log line
shapes RESTest 's MultiServiceTestCaseGenerator and SmartInputFetcher emit
(verified against logs/trainticket_twostage_test/trainticket_test_execution.log):

  SMART_FETCH       — value came from a real upstream service response
                      (lines: 'Smart Fetch → … = <v> ✅', 'Smart Fetch (Step 1) → …',
                       'Smart Fetch (Independent) → …')
  LLM               — value came from the LLM ('LLM (Step 1 Fallback) → …',
                       'LLM (Independent Fallback) → …', 'LLM (Fallback, Rotated) → …')
  SHARED_POOL_DRAW  — value drawn from the pre-built shared pool by a variant
                      ('Shared Pool (Step 1) → …'). The originating source is
                      whatever filled the pool entry; we record this only when
                      no earlier provenance for (param, value) exists.
  NEGATIVE_FAULT    — invalid value injected by the negative-test path
                      ('✅ Negative Test (Round-Robin) → … [InvalidType: T]')

The output CSV is keyed by (parameter, value, source); duplicate observations
are collapsed to the first occurrence so D4 sees the originating provenance.

Usage:
    python3 mine_provenance.py --exec-log <PATH> --out provenance.csv
"""

from __future__ import annotations

import argparse
import csv
import re
import sys
from pathlib import Path

# ---------------------------------------------------------------------------
# Provenance line patterns
# Each pattern captures (param, value); whichever pattern matches first
# determines the provenance category. Order matters — more specific first.
# ---------------------------------------------------------------------------

# Common stop-tokens that signal the end of the value field. Each line shape
# below uses these (alone or in combination) so the captured value never
# leaks the trailing decoration like ' (type: String)' or ' ✅'.
#
# Notes:
# - We require a leading space before '(type:' so that a value containing a
#   parenthesised remark (rare but possible) is not truncated mid-value.
# - The negative lookahead at end-of-line keeps lines without a decoration
#   from over-capturing whitespace.

VALUE_STOP = (
    r"(?: \(type:| \(pool size:| \(from | ✅| ⚠| ❌| - LOCKED| \(step | \[InvalidType:|$)"
)
# `' (from N options)'` is appended by `LLM (Fallback, Rotated)` lines and was missing
# from the original stop-token list — it caused 46/46 LLM-classified rows in real runs
# to carry the polluted suffix, breaking the (param, value) join with inputs.csv.

# 'Smart Fetch (Step 1) → ts-contacts-service accountId = <value> ✅'
RE_SMART_STEP1 = re.compile(
    r"Smart Fetch \(Step 1\) → \S+ (?P<param>\S+) = (?P<value>.*?)" + VALUE_STOP
)

# 'Smart Fetch (Independent) → ts-foo-service param = <value> ✅ (step …)'
RE_SMART_INDEP = re.compile(
    r"Smart Fetch \(Independent\) → \S+ (?P<param>\S+) = (?P<value>.*?)" + VALUE_STOP
)

# 'Smart Fetch → param = <value> ✅' (during pool fill; SmartInputFetcher line 173/183)
RE_SMART_BARE = re.compile(
    r"Smart Fetch → (?P<param>\S+) = (?P<value>.*?)" + VALUE_STOP
)

# 'LLM (Step 1 Fallback) → ts-foo-service param = <value> (type: …)'
# 'LLM (Independent Fallback) → ts-foo-service param = <value> (type: …) (step …)'
# 'LLM (Fallback, Rotated) → param = <value>'
RE_LLM_FALLBACK = re.compile(
    r"LLM \([^)]+\) → (?:\S+ )?(?P<param>\S+) = (?P<value>.*?)" + VALUE_STOP
)

# 'Shared Pool (Step 1) → ts-foo-service param = <value> (type: …, pool size: N) ✅'
RE_SHARED_POOL = re.compile(
    r"Shared Pool \(Step 1\) → \S+ (?P<param>\S+) = (?P<value>.*?)" + VALUE_STOP
)

# '✅ Negative Test (Round-Robin) → param = <value> [InvalidType: T] (javaType: …) - LOCKED'
RE_NEGATIVE = re.compile(
    r"Negative Test \(Round-Robin\) → (?P<param>\S+) = (?P<value>.*?)\s*\[InvalidType: (?P<fault>[^\]]+)\]"
)

# Markers that indicate the smart fetch FAILED — these do NOT count as a hit.
SMART_FETCH_FAILURE_MARKERS = ("ERROR (", "falling back to LLM", "FAILED (", "(Failed)")

# Sentinel values that the smart fetcher returns when the upstream JSON had no
# matching field. They appear with '✅' but the value is unusable; we do NOT
# record them as smart-fetch hits because the variant value will be a different
# (LLM-generated) string.
SMART_FETCH_SENTINELS = {"NO_GOOD_MATCH", "NO_VALUES_FOUND", "NO_VALUES_GENERATED"}


# Provenance category constants
P_SMART_FETCH = "SMART_FETCH"
P_LLM = "LLM"
P_SHARED_POOL = "SHARED_POOL_DRAW"
P_NEGATIVE = "NEGATIVE_FAULT"


def classify_line(line: str) -> tuple[str, str, str] | None:
    """Return (provenance, parameter, value) or None when nothing matches.

    The same line can in theory match multiple patterns; we test specific
    smart-fetch sub-patterns first and fall back to the bare smart-fetch
    pattern, then LLM, shared-pool, and negative-test.
    """
    # Negative test must come BEFORE the smart-fetch patterns because some
    # invalid values contain '=' or '→'-like characters that would otherwise
    # be parsed by the bare smart-fetch regex.
    m = RE_NEGATIVE.search(line)
    if m:
        param = m.group("param").strip()
        value = m.group("value").strip()
        # Truncated overflow values end in '...' in the log; that ellipsis is
        # part of the truncation, not the actual fault value.
        if value.endswith("..."):
            value = value[:-3].rstrip()
        return P_NEGATIVE, param, value

    # 'Smart Fetch (...)' specific shapes first
    m = RE_SMART_STEP1.search(line) or RE_SMART_INDEP.search(line) or RE_SMART_BARE.search(line)
    if m:
        # Reject failure-marker lines (the value field then contains an error
        # message, not a real smart-fetch hit).
        if any(marker in line for marker in SMART_FETCH_FAILURE_MARKERS):
            return None
        param = m.group("param").strip()
        value = m.group("value").strip()
        if value in SMART_FETCH_SENTINELS:
            return None  # not a real hit
        return P_SMART_FETCH, param, value

    m = RE_LLM_FALLBACK.search(line)
    if m:
        param = m.group("param").strip()
        value = m.group("value").strip()
        return P_LLM, param, value

    m = RE_SHARED_POOL.search(line)
    if m:
        param = m.group("param").strip()
        value = m.group("value").strip()
        return P_SHARED_POOL, param, value

    return None


def main(argv: list[str]) -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--exec-log", required=True, type=Path,
                   help="Path to trainticket_test_execution.log")
    p.add_argument("--out", required=True, type=Path, help="Output provenance CSV")
    args = p.parse_args(argv)

    if not args.exec_log.is_file():
        print(f"error: --exec-log is not a file: {args.exec_log}", file=sys.stderr)
        return 2

    # First-occurrence wins per (param, value) so we record the originating
    # source rather than a later draw from a pre-filled pool.
    first_seen: dict[tuple[str, str], str] = {}
    counts: dict[str, int] = {}

    with args.exec_log.open("r", encoding="utf-8", errors="replace") as fh:
        for line in fh:
            classification = classify_line(line)
            if classification is None:
                continue
            prov, param, value = classification
            counts[prov] = counts.get(prov, 0) + 1
            key = (param, value)
            if key not in first_seen:
                first_seen[key] = prov
            elif first_seen[key] == P_SHARED_POOL and prov in (P_SMART_FETCH, P_LLM):
                # If we initially recorded a pool draw and later see the
                # actual originating event (smart-fetch or LLM), prefer the
                # originator. Do NOT promote to NEGATIVE_FAULT — the same
                # (param, value) can be drawn positively in one variant and
                # injected as a fault in another; downgrading to NEGATIVE
                # would mis-classify the legitimate positive draw.
                first_seen[key] = prov

    args.out.parent.mkdir(parents=True, exist_ok=True)
    with args.out.open("w", encoding="utf-8", newline="") as out_fh:
        w = csv.writer(out_fh, quoting=csv.QUOTE_MINIMAL)
        w.writerow(["parameter", "value", "provenance"])
        for (param, value), prov in sorted(first_seen.items()):
            w.writerow([param, value, prov])

    print(f"mine_provenance: {sum(counts.values())} provenance lines "
          f"(unique (param, value) pairs: {len(first_seen)}); "
          f"by category: {dict(sorted(counts.items()))} → {args.out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
