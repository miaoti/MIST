"""Distinct requests among the tests of one run, from the raw Allure results: a test's request
sequence is, per executed step, its HTTP method and endpoint (the '📡 HTTP Method' and
'🔗 Endpoint' parameters, in order) plus the text of each '📤 Request Body' attachment.
Optionally the recorded injected payload is added ('payload' argument).

usage: python distinct_requests.py <allure-results dir> [verdicts.csv] [payload]
"""
import collections
import csv
import glob
import json
import os
import sys

sys.stdout.reconfigure(encoding="utf-8")
res = sys.argv[1]
vfile = sys.argv[2] if len(sys.argv) > 2 and sys.argv[2].endswith(".csv") else None
with_payload = "payload" in sys.argv[2:]
V = {r["test"]: r for r in csv.DictReader(open(vfile, encoding="utf-8"))} if vfile else None
files = {os.path.basename(f): f for f in glob.glob(os.path.join(res, "**", "*"), recursive=True)}

sig, kind = {}, {}
for rf in glob.glob(os.path.join(res, "**", "*-result.json"), recursive=True):
    j = json.load(open(rf, encoding="utf-8"))
    name = j.get("name")
    if V is not None and name not in V:
        continue
    steps, method = [], None
    payload = []
    for p in j.get("parameters", []) or []:
        n, v = p.get("name", ""), p.get("value", "")
        if n.endswith("HTTP Method"):
            method = v
        elif n.endswith("Endpoint") and "Failed" not in n:
            steps.append((method, v))
        elif n.endswith("Injected Payload"):
            payload.append(v)
    bodies = []
    def walk(node):
        for a in node.get("attachments", []) or []:
            if a.get("name", "").endswith("Request Body") and a.get("source") in files:
                bodies.append(open(files[a["source"]], encoding="utf-8", errors="ignore").read().strip())
        for s in node.get("steps", []) or []:
            walk(s)
    walk(j)
    key = (tuple(steps), tuple(bodies)) + ((tuple(payload),) if with_payload else ())
    if name in sig:
        name = name + "#dup"
    sig[name] = key
    kind[name] = "negative" if "_negative_" in name else "positive"

counts = collections.Counter(sig.values())
print(f"tests {len(sig)} -> distinct request sequences {len(counts)}; largest repeat {max(counts.values()) if counts else 0}")
for k in ("positive", "negative"):
    names = [n for n in sig if kind[n] == k]
    print(f"  {k}: {len(names)} -> distinct {len(set(sig[n] for n in names))}")
if V is not None:
    fired = [n for n in sig if V.get(n, {}).get("fired") == "yes"]
    print(f"  flagged: {len(fired)} -> distinct {len(set(sig[n] for n in fired))}")
for key, c in counts.most_common(5):
    if c > 1:
        print(f"  x{c}: {str(key)[:220]}")
