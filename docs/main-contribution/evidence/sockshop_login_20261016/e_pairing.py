"""Trace pairing check for a Sock Shop leg: for each test, the response body's verdict (failed downstream vs
success), whether the invariant fired, how its trace was found (marker match vs time window), and the gap
between the request start and the start of the trace it attached.
usage: e_pairing.py <leg dir>"""
import collections
import csv
import json
import re
import sys
from pathlib import Path

sys.stdout.reconfigure(encoding="utf-8", errors="replace")
leg = Path(sys.argv[1])
res = leg / "allure-results"
scan = {r["test"]: r for r in csv.DictReader(open(leg / "scan.csv", encoding="utf-8"))}
files = {p.name: p for p in res.iterdir()}
FAIL_BODY = ("Invalid Id Hex", "upstream connect error", "reset before headers", "no healthy upstream",
             "cannot unmarshal", '"status_code":500')


def attachments(j):
    out = []

    def walk(n):
        for a in n.get("attachments", []) or []:
            if a.get("source") in files:
                out.append((a.get("name", ""), files[a["source"]].read_text(encoding="utf-8", errors="replace")))
        for s in n.get("steps", []) or []:
            walk(s)
    walk(j)
    return out


rows, seen = [], set()
for f in res.glob("*-result.json"):
    j = json.loads(f.read_text(encoding="utf-8"))
    name = j["name"]
    if name in seen or name not in scan:
        continue
    seen.add(name)
    at = attachments(j)
    body = next((t for n, t in at if n.startswith("📥 Response")), "")
    dbg = next((t for n, t in at if "Query Debug Info" in n), "")
    raw = next((t for n, t in at if "Raw Trace Data" in n), "")
    req_start = re.search(r"Request Start \(\D+\): (\d+)", dbg)
    req_start = int(req_start.group(1)) if req_start else None
    marker = "Marker match" in dbg
    trace_start = None
    try:
        tr = json.loads(raw)
        if isinstance(tr, dict) and "data" in tr:
            tr = tr["data"][0]
        starts = [s.get("startTime") for s in tr.get("spans", []) if s.get("startTime")]
        trace_start = min(starts) if starts else None
    except Exception:
        pass
    gap = (trace_start - req_start) / 1e6 if (trace_start and req_start) else None
    failed_body = any(k in body for k in FAIL_BODY)
    rows.append((name, scan[name]["entry"], failed_body, scan[name]["fired"] == "yes", marker, gap))

print("tests:", len(rows), "| marker matches:", sum(r[4] for r in rows))
cells = collections.Counter((r[2], r[3]) for r in rows)
print("body failed / fired:", {f"{'fail' if a else 'ok'}-body/{'fired' if b else 'quiet'}": v for (a, b), v in sorted(cells.items())})
for label, sel in (("ok-body but fired", lambda r: not r[2] and r[3]), ("fail-body but quiet", lambda r: r[2] and not r[3]),
                   ("fail-body and fired", lambda r: r[2] and r[3]), ("ok-body and quiet", lambda r: not r[2] and not r[3])):
    gaps = [r[5] for r in rows if sel(r) and r[5] is not None]
    gaps.sort()
    if gaps:
        print(f"{label:22s} n={sum(1 for r in rows if sel(r)):4d}  trace start minus request start (s): "
              f"min {gaps[0]:+.2f} median {gaps[len(gaps)//2]:+.2f} max {gaps[-1]:+.2f}; within +-0.5 s: {sum(1 for g in gaps if abs(g) <= 0.5)}")
