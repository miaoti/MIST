"""Table I row of one re-run leg: runs the evidence folder's verdict_table.py and verdict_scan.py on the
leg's Allure results and counts Tests, Hidden, Fired, Confirmed, Missed as the evidence README defines
them (Hidden: client 2xx and either the leg's rule says the entry calls the failed service or the test's
own trace holds a failed span).

usage: python analyze_leg.py <leg dir> <expect.json> [rule.json]
"""
import collections
import csv
import json
import re
import subprocess
import sys
from pathlib import Path

leg = Path(sys.argv[1])
expect = sys.argv[2]
rule = sys.argv[3] if len(sys.argv) > 3 else None
ev = Path("C:/Users/miaot/Github/MIST/docs/main-contribution/evidence/verdict_table_20261014")
res = leg / "allure-results"

# run id: the numeric suffix of the generated test class package (e.g. BookinfoTest_1792...)
ids = collections.Counter()
for f in res.glob("*-result.json"):
    j = json.loads(f.read_text(encoding="utf-8"))
    m = re.search(r"Test_(\d{10,})", j.get("fullName", ""))
    if m:
        ids[m.group(1)] += 1
run_id = ids.most_common(1)[0][0] if ids else ""
print("run id:", run_id, dict(ids))

py = sys.executable
subprocess.check_call([py, "-I", str(ev / "verdict_table.py"), str(res), run_id, str(leg / "verdicts.csv"), expect])
scan_args = [py, "-I", str(ev / "verdict_scan.py"), str(res), run_id, str(leg / "scan.csv"), expect]
if rule:
    scan_args.append(rule)
subprocess.check_call(scan_args)

V = list(csv.DictReader(open(leg / "verdicts.csv", encoding="utf-8")))
S = {r["test"]: r for r in csv.DictReader(open(leg / "scan.csv", encoding="utf-8"))}
rules = [re.compile(k) for k in json.load(open(rule, encoding="utf-8"))] if rule else []

def is2xx(s):
    return s.isdigit() and 200 <= int(s) < 300

tests = len(V)
hidden = fired = confirmed = missed = 0
for r in V:
    s = S.get(r["test"], {})
    entry = r["entry"].split("?")[0]
    on_rule = any(rx.search(entry) for rx in rules)
    failed_in_trace = s.get("failed_in_trace", "0") not in ("", "0")
    h = is2xx(r["client_status"]) and (on_rule or failed_in_trace)
    f = r["fired"] == "yes"
    hidden += h
    fired += f
    confirmed += r["verdict"] == "CONFIRMED"
    missed += h and not f
cats = collections.Counter(s["category"] for s in S.values())
offp = sum(1 for s in S.values() if s.get("off_path") == "yes" and s.get("fired") == "yes")
kinds = collections.Counter(r["kind"] for r in V)
print(f"ROW tests={tests} hidden={hidden} fired={fired} confirmed={confirmed} missed={missed} off_path={offp}")
pos_hidden = sum(1 for r in V if r["kind"] == "positive" and r["fired"] == "yes")
json.dump({"run_id": run_id, "tests": tests, "hidden": hidden, "fired": fired, "confirmed": confirmed,
           "missed": missed, "off_path": offp, "positive_fired": pos_hidden,
           "kinds": dict(collections.Counter(r["kind"] for r in V)),
           "scan": dict(collections.Counter(s["category"] for s in S.values()))},
          open(leg / "row.json", "w", encoding="utf-8"), indent=1)
print("kinds:", dict(kinds), "| scan:", dict(cats))
neg = [r for r in V if r["kind"] == "negative"]
print("negative entries (distinct):", len(set(r["entry"].split("?")[0] for r in neg)),
      "| negative client statuses:", dict(collections.Counter(r["client_status"] for r in neg)))
