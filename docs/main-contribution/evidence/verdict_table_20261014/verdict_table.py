"""Per-test verdict table of one MIST run, from its Allure results, with a consistency check of every
HiddenDownstreamFailure flag against the test's own request.

usage: python verdict_table.py <allure-results dir> <run id substring> <out.csv> [expect.json]

For every test of the run it records: test name, scenario, kind (positive/negative), entry (method and
path), the client status MIST recorded, whether the invariant fired, the failed spans MIST listed
(service, url, http status, otel status) and a verdict:
  CONFIRMED  the client status is 2xx, every failed span is a 5xx or otel ERROR, and (when expect.json
             gives the call path of the entry) every failed span's url lies on that path;
  OFF-PATH   a failed span belongs to a service the entry never calls (trace lookup picked up another
             request: an artifact);
  NO-2XX     the recorded client status is not 2xx (the invariant should not have fired);
  NONE       the invariant did not fire.
expect.json maps a path regex to a list of url substrings that are legitimate downstream calls, e.g.
{"^/api/v1/products/[^/]+/reviews$": ["reviews", "ratings"], "^/api/v1/products/[^/]+$": ["details"]}.
"""
import csv
import json
import os
import re
import sys

results, run_id, out = sys.argv[1], sys.argv[2], sys.argv[3]
expect = json.load(open(sys.argv[4], encoding="utf-8")) if len(sys.argv) > 4 else {}
expect = [(re.compile(k), v) for k, v in expect.items()]

rows = []
for f in sorted(os.listdir(results)):
    if not f.endswith("-result.json"):
        continue
    try:
        d = json.load(open(os.path.join(results, f), encoding="utf-8"))
    except Exception:  # noqa: BLE001
        continue
    full = d.get("fullName", "")
    if run_id not in full:
        continue
    name = d.get("name", "")
    sc = re.search(r"Flow_Scenario_(\d+)", full)
    params = {p.get("name", ""): str(p.get("value", "")) for p in d.get("parameters", [])}
    entry = next((v for k, v in params.items() if k.endswith("Endpoint") and "Failed" not in k), "")
    method = next((v for k, v in params.items() if "HTTP Method" in k), "")
    hidden = next((v for k, v in params.items() if "Hidden downstream" in k), "")
    results_vals = [v for k, v in params.items() if k.endswith("Result") and "Scenario" not in k]
    status = ""
    for v in results_vals:
        m = re.search(r"status: (\d{3})|\((\d{3}) in", v)
        if m:
            status = m.group(1) or m.group(2)
    kind = "positive" if "positive" in name else "negative"
    fired = bool(hidden)
    spans = re.findall(r"([\w.-]+) ──▶ ([\w.:/*-]+) \(http=(\d+|\?) otel=(\w*)\)", hidden)
    verdict = "NONE"
    if fired:
        verdict = "CONFIRMED"
        if status and not status.startswith("2"):
            verdict = "NO-2XX"
        for caller, url, http, otel in spans:
            ok5 = (http.isdigit() and int(http) >= 500) or otel == "ERROR"
            if not ok5:
                verdict = "NOT-5XX"
            path_ok = True
            for rx, allowed in expect:
                if rx.search(entry):
                    path_ok = any(a in url for a in allowed)
                    break
            if not path_ok and verdict == "CONFIRMED":
                verdict = "OFF-PATH"
    rows.append([name, sc.group(1) if sc else "", kind, method, entry, status, "yes" if fired else "no", verdict,
                 "; ".join(f"{c}->{u} http={h} otel={o}" for c, u, h, o in spans)[:300]])

with open(out, "w", newline="", encoding="utf-8") as fh:
    w = csv.writer(fh)
    w.writerow(["test", "scenario", "kind", "method", "entry", "client_status", "fired", "verdict", "failed_spans"])
    w.writerows(sorted(rows, key=lambda r: (r[4], r[0])))

from collections import Counter  # noqa: E402

print("tests:", len(rows))
per = {}
for r in rows:
    per.setdefault(r[4], Counter())[r[7]] += 1
for entry, c in sorted(per.items()):
    print(f"{entry:<45} total {sum(c.values()):>4}  " + "  ".join(f"{k} {v}" for k, v in sorted(c.items())))
print("overall:", dict(Counter(r[7] for r in rows)))
