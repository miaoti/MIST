"""Per-leg breakdown of a Sock Shop leg: for every test, kind, Sniper category, client status, the response
body class, whether the invariant fired, and the failed spans in its attached trace (from scan.csv).
usage: e_breakdown.py <leg dir>"""
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


def body_of(j):
    out = []

    def walk(n):
        for a in n.get("attachments", []) or []:
            if a.get("name", "").startswith("📥 Response") and a.get("source") in files:
                out.append(files[a["source"]].read_text(encoding="utf-8", errors="replace"))
        for s in n.get("steps", []) or []:
            walk(s)
    walk(j)
    return out[0] if out else ""


def body_class(b):
    b = b.strip()
    if "Invalid Id Hex" in b:
        return "invalid-id-hex"
    if "upstream connect error" in b or "reset before headers" in b:
        return "envoy-reset"
    if "no healthy upstream" in b:
        return "no-healthy-upstream"
    if b.startswith('{"id"'):
        return "stored-id"
    if '"error"' in b or "status_code" in b:
        m = re.search(r'"error":"([^"]{0,40})', b)
        return "error:" + (m.group(1) if m else "?")
    if b.startswith("[") or b.startswith('{"size"') or b.startswith("{"):
        return "json-ok"
    return "other:" + b[:30]


seen = set()
rows = []
for f in res.glob("*-result.json"):
    j = json.loads(f.read_text(encoding="utf-8"))
    name = j["name"]
    if name in seen or name not in scan:
        continue
    seen.add(name)
    s = scan[name]
    m = re.search(r"_fault_Root\d+_([A-Z_]+)$", name)
    cat = m.group(1) if m else "-"
    b = body_of(j)
    spans = s.get("failed_spans_in_trace", "")
    st = sorted(set(re.findall(r"http=(\d+)", spans)))
    rows.append((s["entry"], s["kind"], cat, s["client_status"], body_class(b), s["fired"], ",".join(st), j.get("status")))

print("tests:", len(rows))
by = collections.Counter((r[0] if not r[0].startswith("/catalogue/") or r[0] == "/catalogue/size" else "/catalogue/{id}", r[1], r[4], r[5]) for r in rows)
for k, v in sorted(by.items()):
    print(f"  {k[0]:18s} {k[1]:8s} body={k[2]:24s} fired={k[3]:3s} n={v}")
print("fired tests by category and failed-span statuses:")
fc = collections.Counter((r[0] if not r[0].startswith("/catalogue/") or r[0] == "/catalogue/size" else "/catalogue/{id}", r[2], r[4], r[6]) for r in rows if r[5] == "yes")
for k, v in sorted(fc.items()):
    print(f"  {k[0]:18s} {k[1]:20s} body={k[2]:24s} spans={k[3]:10s} n={v}")
print("positive tests:")
for r in sorted(r for r in rows if r[1] == "positive"):
    print("  ", r)
