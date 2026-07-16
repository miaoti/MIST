#!/usr/bin/env python3
"""Phase B leg 1 (completion-set wave): run the FROZEN trace scorer
(b4/trace_score.py, reviewer-reproduced) over every A8-mapped committed bundle
and emit per-arm verdict files for the A6 harness.

Rails (rev-2 pins): offline only; selectors = the FROZEN table (cases without a
frozen selector = not_evaluable, reason recorded - post-hoc selector authoring
is barred); the tracetest_presence arm is labeled a SURROGATE verbatim; leg1
co-generated traces (5/leg) must be verdict-UNIFORM else STOP; the flagship
cells are cross-checked against the banked e2-trace-scores.txt (mismatch=STOP).
"""
import io
import json
import sys
from contextlib import redirect_stdout
from pathlib import Path

B4 = Path(__file__).resolve().parents[1]
BENCH = B4.parent
sys.path.insert(0, str(B4))
import trace_score  # the frozen scorer

OUTDIR = BENCH / "scoring" / "verdicts"

# case -> (which leg this case IS, fault-or-control trace source list)
# built from case-trace-arm-map.json kinds; leg choice: a case scores ITS OWN
# leg's trace (fault cases -> fault traces; control/clean cases -> control).
RUNS = {
    "TT-cancel-refund-fabricatedack-001": ["b4/ttomni/leg1/trace-fault-*.json"],
    "TT-cancel-refund-clean-001":         ["b4/ttomni/leg1/trace-control-*.json"],
    "TT-adminroute-lostwrite-001":  ["b4/captures/tt-s1-adminroute-lostwrite-traced/trace-fault.json"],
    "TT-adminroute-control-001":    ["b4/captures/tt-ctl-adminroute-create-traced/trace-control.json"],
    "TT-adminbasic-contacts-lostwrite-001": ["b4/captures/tt-s1-adminbasic-contacts-lostwrite-traced/trace-fault.json"],
    "TT-adminbasic-contacts-control-001": ["b4/captures/tt-s2-adminbasic-contacts-control-traced/trace-control.json"],
    "bookinfo-ratings-benign-001":  ["b4/captures/bookinfo-ratings-benign-traced/trace-fault.json"],
    "sockshop-shipping-swallowed-enqueue-001": ["b4/captures/sockshop-shipping-swallowed-traced/trace-fault.json"],
    "sockshop-shipping-control-001": ["b4/captures/sockshop-shipping-control-traced/trace-control.json"],
    "oteldemo-checkout-lost-001":    ["b4/captures/oteldemo-checkout-lost/trace-fault.json"],
    "oteldemo-checkout-control-001": ["b4/captures/oteldemo-checkout-control/trace-control.json"],
}
NOT_EVALUABLE = {
    "oteldemo-checkout-eventual-benign-001": "no committed bundle (w120 natural observation) + no frozen selector",
    "oteldemo-checkout-eventual-benign-002": "no frozen selector (post-hoc authoring barred)",
    "oteldemo-checkout-eventual-benign-003": "no frozen selector (post-hoc authoring barred)",
    "TT-createaccount-agreement-001": "trace-invisible-by-construction (N-vs-0 row); no bundle",
    "TT-createaccount-clean-001": "trace-invisible-by-construction; no bundle",
}


def score_one(case_id, trace_path):
    buf = io.StringIO()
    with redirect_stdout(buf):
        trace_score.score(case_id, str(trace_path))
    return json.loads(buf.getvalue())


def main():
    OUTDIR.mkdir(parents=True, exist_ok=True)
    arms = {
        "naive_span_error": {}, "tracetest_presence_surrogate": {},
        "db_span_presence": {},
    }
    detail = {}
    for cid, globs in RUNS.items():
        files = []
        for g in globs:
            files.extend(sorted(BENCH.glob(g)) if "*" in g else [BENCH / g])
        results = [score_one(cid, f) for f in files]
        def col(key):
            vals = {r[key] for r in results}
            if len(vals) != 1:
                raise SystemExit(f"STOP: non-uniform {key} across {len(files)} "
                                 f"traces for {cid}: {vals}")
            return vals.pop()
        arms["naive_span_error"][cid] = col("naive_span_error_oracle")
        arms["tracetest_presence_surrogate"][cid] = col(
            "tracetest_presence_oracle_verdict_on_this_leg")
        arms["db_span_presence"][cid] = col(
            "db_span_presence_oracle_verdict_on_this_leg")
        detail[cid] = {"n_traces": len(files),
                       "error_spans": results[0]["error_spans"],
                       "db_report": results[0]["db_client_spans_per_service (T6 disclosure)"]}
        print(f"{cid}: n={len(files)} naive={arms['naive_span_error'][cid]} "
              f"presence={arms['tracetest_presence_surrogate'][cid]} "
              f"db={arms['db_span_presence'][cid]}")

    for cid, reason in NOT_EVALUABLE.items():
        for a in arms.values():
            a[cid] = "not_evaluable"

    meta = {
        "naive_span_error": ("frozen trace_score.py naive_span_error (any error span "
                             "in the per-case scope; mechanical, no exclusions)"),
        "tracetest_presence_surrogate": (
            "span-assertion-semantics (surrogate; the live tool was NOT run) - the "
            "frozen EXISTENCE-ONLY presence assertion at cross-service span "
            "granularity, per-case frozen selectors (trace_score.py T6/T8)"),
        "db_span_presence": ("frozen DB-CLIENT-granularity existence assertion "
                             "(specification-locality arm; only cases with a frozen "
                             "db_presence selector emit a verdict - others "
                             "not_applicable, recorded as not_evaluable here)"),
    }
    # db arm: cases lacking a db_presence selector returned "not_applicable"
    for cid, v in list(arms["db_span_presence"].items()):
        if v == "not_applicable":
            arms["db_span_presence"][cid] = "not_evaluable"
    # presence arm: selector None -> not_applicable -> not_evaluable
    for cid, v in list(arms["tracetest_presence_surrogate"].items()):
        if v == "not_applicable":
            arms["tracetest_presence_surrogate"][cid] = "not_evaluable"

    for arm, cases in arms.items():
        out = {"arm": arm, "verdict_source": meta[arm],
               "not_evaluable_reasons": NOT_EVALUABLE, "cases": cases}
        (OUTDIR / f"{arm}.json").write_text(
            json.dumps(out, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    (Path(__file__).resolve().parent / "frontier-run-detail.json").write_text(
        json.dumps(detail, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"wrote {len(arms)} verdict files -> {OUTDIR}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
