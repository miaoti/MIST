"""Per-test scan of one MIST run from its Allure results: the invariant's flag against the raw trace
the test itself attached, so that every quiet test is checked too.

usage: python verdict_scan.py <allure-results dir> <run id substring or ""> <out.csv> [expect.json] [rule.json]

Per test it records the entry, kind, client status, whether HiddenDownstreamFailure fired, the spans the
invariant listed, the number of spans in the test's own raw trace, the failed spans found in that trace
(http >= 500, otel.status_code ERROR, error=true, or a server-side gRPC code 2/4/8/12/13/14/15), whether a
rule says the test should have fired (rule.json: path regex -> list of methods, empty = any), and a category:
  TP               fired, client 2xx, and the raw trace holds a failed span
  FIRED-NO-FAIL    fired, but the attached raw trace holds no failed span
  FIRED-NO-2XX     fired on a test whose client status is not 2xx
  MISS-IN-TRACE    quiet, client 2xx, and the raw trace holds a failed span (the oracle missed it)
  MISS-TRACE-SHORT quiet, client 2xx, rule says it should fire, and the raw trace holds no failed span
                   (the trace fetched by the test lacked the failed hop)
  QUIET            quiet, nothing failed in the trace, no rule says it should fire
  NO-2XX           quiet, client status not 2xx
expect.json (as for verdict_table.py) marks a listed or found failed span OFF-PATH when its url or service
is one the entry never calls; such tests are counted separately.
"""
import csv
import json
import os
import re
import sys
from collections import Counter

sys.stdout.reconfigure(encoding="utf-8", errors="replace")
results, run_id, out = sys.argv[1], sys.argv[2], sys.argv[3]
expect = json.load(open(sys.argv[4], encoding="utf-8")) if len(sys.argv) > 4 and sys.argv[4] else {}
expect = [(re.compile(k), v) for k, v in expect.items()]
rules = json.load(open(sys.argv[5], encoding="utf-8")) if len(sys.argv) > 5 else {}
rules = [(re.compile(k), v) for k, v in rules.items()]
GRPC_SERVER_FAIL = {"2", "4", "8", "12", "13", "14", "15"}


def tagmap(span):
    return {t.get("key"): str(t.get("value")) for t in span.get("tags", [])}


def failed_spans(trace):
    procs = trace.get("processes", {}) or {}
    found = []
    for sp in trace.get("spans", []):
        t = tagmap(sp)
        http = t.get("http.status_code") or t.get("http.response.status_code") or ""
        otel = t.get("otel.status_code", "")
        grpc = t.get("rpc.grpc.status_code") or t.get("grpc.status_code") or ""
        err = t.get("error", "")
        bad = (http.isdigit() and int(http) >= 500) or otel == "ERROR" or err == "true" or grpc in GRPC_SERVER_FAIL
        if bad:
            svc = (procs.get(sp.get("processID"), {}) or {}).get("serviceName", "") or sp.get("operationName", "").split(".")[0]
            found.append((svc, sp.get("operationName", ""), t.get("http.url", ""), http, otel, grpc))
    return len(trace.get("spans", [])), found


def on_path(entry, svc, url, op):
    for rx, allowed in expect:
        if rx.search(entry):
            return any(a in url or a in svc or a in op for a in allowed)
    return True


rows = []
run_ids = Counter()
for f in sorted(os.listdir(results)):
    if not f.endswith("-result.json"):
        continue
    try:
        d = json.load(open(os.path.join(results, f), encoding="utf-8"))
    except Exception:  # noqa: BLE001
        continue
    full = d.get("fullName", "")
    if run_id and run_id not in full:
        continue
    m = re.search(r"Test_(\d{13})", full)
    run_ids[m.group(1) if m else "?"] += 1
    name = d.get("name", "")
    sc = re.search(r"Flow_Scenario_(\d+)", full)
    params = {p.get("name", ""): str(p.get("value", "")) for p in d.get("parameters", [])}
    entry = next((v for k, v in params.items() if k.endswith("Endpoint") and "Failed" not in k), "")
    method = next((v for k, v in params.items() if "HTTP Method" in k), "")
    hidden = next((v for k, v in params.items() if "Hidden downstream" in k), "")
    status = ""
    for k, v in params.items():
        if k.endswith("Result") and "Scenario" not in k:
            mm = re.search(r"status: (\d{3})|\((\d{3}) in", v)
            if mm:
                status = mm.group(1) or mm.group(2)
    kind = "positive" if "positive" in name else "negative"
    fired = bool(hidden)
    listed = re.findall(r"([\w.-]+) ──▶ ([\w.:/*-]+) \(http=(\d+|\?) otel=(\w*)\)", hidden)
    nspans, found = 0, []
    for st in d.get("steps", []):
        for a in st.get("attachments", []):
            if (a.get("name") or "").endswith("Raw Trace Data"):
                try:
                    tr = json.load(open(os.path.join(results, a["source"]), encoding="utf-8"))
                except Exception:  # noqa: BLE001
                    continue
                n, fs = failed_spans(tr)
                nspans += n
                found += fs
    two = status.startswith("2")
    rule = any(rx.search(entry) and (not ms or method in ms) for rx, ms in rules)
    if fired and not two:
        cat = "FIRED-NO-2XX"
    elif fired and found:
        cat = "TP"
    elif fired:
        cat = "FIRED-NO-FAIL"
    elif not two:
        cat = "NO-2XX"
    elif found:
        cat = "MISS-IN-TRACE"
    elif rule:
        cat = "MISS-TRACE-SHORT"
    else:
        cat = "QUIET"
    off = [x for x in found if not on_path(entry, x[0], x[2], x[1])] + [l for l in listed if not on_path(entry, l[0], l[1], "")]
    rows.append([name, sc.group(1) if sc else "", kind, method, entry, status, "yes" if fired else "no",
                 "; ".join(f"{c}->{u} http={h} otel={o}" for c, u, h, o in listed)[:300], nspans, len(found),
                 "; ".join(f"{s}:{op} http={h} otel={o} grpc={g}" for s, op, u, h, o, g in found)[:300],
                 "yes" if rule else "no", cat, "yes" if off else "no"])

with open(out, "w", newline="", encoding="utf-8") as fh:
    w = csv.writer(fh)
    w.writerow(["test", "scenario", "kind", "method", "entry", "client_status", "fired", "listed_spans", "trace_spans",
                "failed_in_trace", "failed_spans_in_trace", "rule_should_fire", "category", "off_path"])
    w.writerows(sorted(rows, key=lambda r: (r[4], r[0])))

print("run ids:", dict(run_ids), " tests:", len(rows))
print("categories:", dict(sorted(Counter(r[12] for r in rows).items())))
print("off-path among fired:", sum(1 for r in rows if r[13] == "yes" and r[6] == "yes"), " rule-should-fire:", sum(1 for r in rows if r[11] == "yes"),
      " fired:", sum(1 for r in rows if r[6] == "yes"))
per = {}
for r in rows:
    per.setdefault((r[3], r[4]), Counter())[r[12]] += 1
for (mth, entry), c in sorted(per.items()):
    print(f"{mth:>6} {entry:<42} n={sum(c.values()):>4}  " + "  ".join(f"{k} {v}" for k, v in sorted(c.items())))
