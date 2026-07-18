#!/usr/bin/env python3
"""A-venue recheck fix (reviewer B item 2): materialize the TRIVIAL-BASELINE arms
(status-code / response-schema / body-marker) as first-class verdict files so the
matched-recall table stops hiding cheap-oracle ties (e.g. the TT-natural body-tell).

Source: each case file's oracle_expectation.{status_code_oracle,schema_oracle,
body_marker_oracle}. For CAPTURED cases these fields record what the recorded
ack/response contains (verified at capture; R2 rater-sidecar pass machine-read the
ack payloads), so they are measured-at-capture verdicts, not design guesses.
SPECIFIED cases -> not_evaluable (freeze rev-2.1 R2/R3: a specified case's
oracle_expectation is a design target, never tallied as a result).
not_applicable -> not_evaluable (own bucket, out of denominator).
"""
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
CASES = HERE.parent / "cases"

ARMS = {
    "status_code": ("status_code_oracle",
                    "trivial baseline: flag iff the ack status code is non-2xx/unexpected "
                    "(case-file oracle_expectation, measured-at-capture for captured cases)"),
    "schema_conformance": ("schema_oracle",
                    "trivial baseline: flag iff the ack body violates the response schema "
                    "(case-file oracle_expectation, measured-at-capture)"),
    "body_marker": ("body_marker_oracle",
                    "trivial baseline: flag iff the ack body carries an explicit failure/degradation "
                    "marker (e.g. status:0 body-tell) despite the 2xx (measured-at-capture)"),
}


def main():
    out = {a: {} for a in ARMS}
    for f in sorted(CASES.glob("*.json")):
        c = json.loads(f.read_text(encoding="utf-8"))
        cid = c.get("case_id", f.stem)
        captured = c.get("capture_status") == "captured"
        oe = c.get("oracle_expectation") or {}
        for arm, (field, _) in ARMS.items():
            v = oe.get(field)
            if not captured or v in (None, "not_applicable"):
                out[arm][cid] = "not_evaluable"
            else:
                out[arm][cid] = v  # "flag" | "no_flag"
    for arm, (field, src) in ARMS.items():
        p = HERE / "verdicts" / f"{arm}.json"
        p.write_text(json.dumps({"arm": arm, "verdict_source": src,
                                 "cases": out[arm]}, indent=2) + "\n", encoding="utf-8")
        tally = {}
        for v in out[arm].values():
            tally[v] = tally.get(v, 0) + 1
        print(f"wrote {p.name}: {tally}")


if __name__ == "__main__":
    main()
