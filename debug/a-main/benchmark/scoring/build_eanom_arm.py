#!/usr/bin/env python3
"""Materialize E-ANOM (our unsupervised control-vs-fault structural trace differ) as a
FIRST-CLASS headline arm — the 2026-07-21 expand-review mandate (REVIEW-expand-{A,B,C}
unanimous): fold the honest trace competitor into the matched-recall table instead of
holding it in a detail file next to a not_evaluable stub.

Source of truth: verdicts/traceanomaly.detail.json (the measured run_anomaly_arm.py
output; RESULT-eanom.md is the result-of-record). Verdict rule = the measured one:
flag iff the differ emitted >=1 structural reason (missing edge / novel span); the
NOISE-quality catch (fabricatedack: identical edge structure, flagged on a background
NacosWatch op) COUNTS as a flag — quality graded in verdict_source, not laundered out.
Everything without a paired control leg + distributed traces is not_evaluable (absent
from `cases` -> scored not_evaluable by score_arms).
"""
import io, json
from pathlib import Path

HERE = Path(__file__).resolve().parent
DETAIL = HERE / "verdicts" / "traceanomaly.detail.json"
OUT = HERE / "verdicts" / "eanom_control_differ.json"

detail = json.loads(DETAIL.read_text(encoding="utf-8"))
cases = {cid: ("flag" if r.get("reasons") else "no_flag") for cid, r in detail.items()}

src = (
    "E-ANOM: OUR unsupervised control-vs-fault structural trace differ (learn-normal-from-"
    "control; scoring/run_anomaly_arm.py; result-of-record b4/RESULT-eanom.md). Requires BOTH "
    "a paired control leg AND distributed traces -> evaluable 7/27 (6 traced positives + the "
    "1 traced benign pair); everything else not_evaluable by the method's own input contract. "
    "Measured quality grading (RESULT-eanom): adminroute+adminbasic STRONG (span collapse 15->3 / "
    "13->2), oteldemo-lost+sockshop-swallowed WEAK (1-2 missing edges of 22/12 - a tuned "
    "detector's threshold may not fire), fabricatedack NOISE-only (edge structure IDENTICAL; "
    "flagged on a background NacosWatch lambda op - the call-happens-data-lost variety has no "
    "missing edge), createaccount MISS; bookinfo benign = FP (1/1 evaluable negatives). Folded "
    "into the headline as the honest trace competitor per the 2026-07-21 expand-review mandate."
)

OUT.write_text(json.dumps({"arm": "eanom_control_differ", "verdict_source": src,
                           "cases": cases}, indent=1) + "\n", encoding="utf-8")
tally = {}
for v in cases.values():
    tally[v] = tally.get(v, 0) + 1
print(f"wrote eanom_control_differ.json: {tally} over {len(cases)} evaluable (rest not_evaluable)")
