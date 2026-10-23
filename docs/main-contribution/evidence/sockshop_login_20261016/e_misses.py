"""Show the tests of a leg whose response body shows a downstream failure but whose invariant stayed quiet:
name, entry, body, client status, and every span of the attached trace with its status.
usage: e_misses.py <leg dir>"""
import csv
import json
import sys
from pathlib import Path

sys.stdout.reconfigure(encoding="utf-8", errors="replace")
leg = Path(sys.argv[1])
res = leg / "allure-results"
scan = {r["test"]: r for r in csv.DictReader(open(leg / "scan.csv", encoding="utf-8"))}
files = {p.name: p for p in res.iterdir()}
FAIL = ("Invalid Id Hex", "upstream connect error", "reset before headers", "no healthy upstream",
        "cannot unmarshal", '"status_code":500')
seen = set()
for f in res.glob("*-result.json"):
    j = json.loads(f.read_text(encoding="utf-8"))
    n = j["name"]
    if n in seen or n not in scan:
        continue
    seen.add(n)
    at = []

    def walk(x):
        for a in x.get("attachments", []) or []:
            if a.get("source") in files:
                at.append((a.get("name", ""), files[a["source"]].read_text(encoding="utf-8", errors="replace")))
        for s in x.get("steps", []) or []:
            walk(s)
    walk(j)
    body = next((t for k, t in at if k.startswith("📥 Response")), "")
    if not any(k in body for k in FAIL) or scan[n]["fired"] == "yes":
        continue
    print("==", n, scan[n]["entry"], "client", scan[n]["client_status"])
    print("   body:", body[:160].replace("\n", " "))
    raw = next((t for k, t in at if "Raw Trace Data" in k), "")
    try:
        tr = json.loads(raw)
        if "data" in tr:
            tr = tr["data"][0]
        procs = tr.get("processes", {})
        for s in sorted(tr.get("spans", []), key=lambda s: s.get("startTime", 0)):
            tags = {t["key"]: t.get("value") for t in s.get("tags", [])}
            svc = procs.get(s.get("processID"), {}).get("serviceName", "?")
            print(f"   span {svc:28s} {s.get('operationName','')[:48]:48s} http={tags.get('http.status_code')} "
                  f"otel={tags.get('otel.status_code')} flags={tags.get('response_flags')} kind={tags.get('span.kind')}")
    except Exception as e:
        print("   (no raw trace:", e, ")")
