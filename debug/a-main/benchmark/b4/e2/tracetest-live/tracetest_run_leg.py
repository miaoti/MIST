#!/usr/bin/env python3
"""Tracetest LIVE leg runner (REST-API-driven; replaces the quoting-fragile bash version).
Usage: tracetest_run_leg.py <legname>
Upserts the 3 adminroute tests (fresh admin bearer inlined into the trigger), runs each,
polls to a terminal state, saves run-<leg>-<test>.json beside this script, prints a summary.
"""
import json
import sys
import time
import urllib.request
from pathlib import Path

API = "http://localhost:11633/api"
GW_LOCAL = "http://localhost:8080"          # reachable from THIS process
GW_IN = "http://host.docker.internal:8080"  # reachable from the tracetest container
HERE = Path(__file__).resolve().parent

ROUTE_BODY = json.dumps({"id": "", "startStation": "shanghai", "endStation": "taiyuan",
                         "stationList": "shanghai,taiyuan", "distanceList": "0,1350"})


def http(method, url, payload=None):
    data = json.dumps(payload).encode() if payload is not None else None
    req = urllib.request.Request(url, data=data, method=method,
                                 headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            body = r.read().decode()
            return r.status, (json.loads(body) if body.strip() else {})
    except urllib.error.HTTPError as e:
        return e.code, {"error": e.read().decode()[:300]}


def main():
    leg = sys.argv[1]
    _, login = http("POST", GW_LOCAL + "/api/v1/users/login",
                    {"username": "admin", "password": "222222"})
    tok = login["data"]["token"]

    def test(tid, name, selector, assertions):
        return {"type": "Test", "spec": {
            "id": tid, "name": name,
            "trigger": {"type": "http", "httpRequest": {
                "method": "POST",
                "url": GW_IN + "/api/v1/adminrouteservice/adminroute",
                "headers": [{"key": "Content-Type", "value": "application/json"},
                            {"key": "Authorization", "value": "Bearer " + tok}],
                "body": ROUTE_BODY}},
            "specs": [{"selector": selector, "name": name, "assertions": assertions}]}}

    tests = [
        test("adminroute-presence", "downstream persist client span present",
             'span[kind="client"]', ["attr:tracetest.selected_spans.count >= 1"]),
        test("adminroute-acked2xx", "entry acked 2xx (masked)",
             'span[kind="server"]', ["attr:http.status_code = 200"]),
        test("adminroute-noerror", "no 5xx span (naive)",
             'span[tracetest.span.type="http"]', ["attr:http.status_code < 500"]),
    ]
    for t in tests:
        tid = t["spec"]["id"]
        st, _ = http("PUT", f"{API}/tests/{tid}", t)
        if st >= 400:
            st, _ = http("POST", f"{API}/tests", t)
        print(f"upsert {tid}: HTTP {st}")

    for t in tests:
        tid = t["spec"]["id"]
        st, run = http("POST", f"{API}/tests/{tid}/run", {})
        rid = run.get("id") or (run.get("run") or {}).get("id")
        if rid is None:
            print(f"RUN-LAUNCH-FAILED {tid}: HTTP {st} {str(run)[:200]}")
            continue
        state = "?"
        for _ in range(40):
            _, d = http("GET", f"{API}/tests/{tid}/run/{rid}")
            state = d.get("state", "?")
            if state in ("FINISHED", "FAILED", "TRIGGER_FAILED", "TRACE_FAILED",
                         "ASSERTION_FAILED", "ANALYZING_ERROR"):
                break
            time.sleep(4)
        (HERE / f"run-{leg}-{tid}.json").write_text(json.dumps(d, indent=2),
                                                    encoding="utf-8")
        res = d.get("result") or {}
        rows = []
        for sr in res.get("results") or []:
            for a in sr.get("results") or []:
                spans = a.get("spanResults") or []
                rows.append(f"{a.get('assertion')} allPassed={a.get('allPassed')} spans={len(spans)}")
        print(f"leg={leg} test={tid} state={state} allPassed={res.get('allPassed')} :: "
              + " | ".join(rows))


if __name__ == "__main__":
    main()
