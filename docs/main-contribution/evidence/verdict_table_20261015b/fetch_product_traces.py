"""Fetch from Jaeger, for each given Online Boutique product id, the most complete trace of a
GET /product/<id> that the shop's own load generator sent through the frontend sidecar, and save
them in the Jaeger API shape ({"data": [trace, ...]}) as one file.

usage: python fetch_product_traces.py <jaeger api url> <out file> <product id> [<product id> ...]
"""
import json
import sys
import urllib.parse
import urllib.request

api, out, ids = sys.argv[1].rstrip("/"), sys.argv[2], sys.argv[3:]


def get(path, **q):
    url = f"{api}/{path}?" + urllib.parse.urlencode(q)
    with urllib.request.urlopen(url, timeout=60) as r:
        return json.load(r)


services = get("services")["data"]
front = [s for s in services if s.startswith("frontend")]
print("frontend services in Jaeger:", front)
picked = []
for pid in ids:
    best = None
    for svc in front:
        tags = json.dumps({"http.url": f"http://frontend/product/{pid}"})
        data = get("traces", service=svc, tags=tags, limit=20, lookback="1h").get("data", [])
        for t in data:
            # the trace must hold the frontend's inbound span for this page and the ad call
            ops = [s.get("operationName", "") for s in t["spans"]]
            has_ad = any("adservice" in o for o in ops)
            score = (has_ad, len(t["spans"]))
            if best is None or score > best[0]:
                best = (score, t)
    if best is None:
        print(f"{pid}: no trace found")
        continue
    (has_ad, n), t = best
    print(f"{pid}: trace {t['traceID']} with {n} spans, ad call present: {has_ad}")
    picked.append(t)
json.dump({"data": picked}, open(out, "w", encoding="utf-8"))
print(f"saved {len(picked)} traces to {out}")
