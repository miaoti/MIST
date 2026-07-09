#!/usr/bin/env python3
"""Seed-capture driver (checklist §1.95.2; corpus plan §2.2 rev 2).

Thin, MIST-free transcript recorder: executes a capture-spec YAML against the
live SUT and writes a sidecar.json (format v1 — corpus-19505-sidecar-format.md).
Harness-level by design: keeps the MIST study-commit pin untouched; records
BEHAVIOR only (no verdicts). Auth tokens are redacted in the transcript.

usage: capture_driver.py <spec.yaml> <out-sidecar.json> [--mist-commit SHA]
"""
import json
import sys
import time
import urllib.request
from pathlib import Path

import yaml


def _http(method, url, payload=None, token=None, timeout=15):
    data = payload.encode("utf-8") if payload else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return r.status, r.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace")


def run(spec_path, out_path, mist_commit):
    spec = yaml.safe_load(Path(spec_path).read_text(encoding="utf-8"))
    base = spec["base_url"].rstrip("/")
    records = []
    t0 = time.monotonic()

    def rel():
        return int((time.monotonic() - t0) * 1000)

    token = None
    login = spec.get("login")
    if login:
        body = json.dumps({"username": login["username"], "password": str(login["password"])})
        t = rel()
        status, resp = _http("POST", base + login["path"], body)
        records.append({"t_rel_ms": t, "kind": "request", "method": "POST",
                        "path": login["path"], "payload": "{\"username\":\"<redacted>\",\"password\":\"<redacted>\"}"})
        records.append({"t_rel_ms": rel(), "kind": "response", "status": status,
                        "body": "<login response — token redacted>"})
        try:
            token = json.loads(resp)["data"]["token"]
        except Exception:
            raise SystemExit("login failed: HTTP %s %s" % (status, resp[:200]))

    for step in spec.get("steps", []):
        t = rel()
        status, resp = _http(step["method"], base + step["path"], step.get("payload"), token)
        rec = {"t_rel_ms": t, "kind": "request", "method": step["method"], "path": step["path"]}
        if step.get("payload"):
            rec["payload"] = step["payload"]
        records.append(rec)
        records.append({"t_rel_ms": rel(), "kind": "response", "status": status, "body": resp})

    for obs in spec.get("observations", []):
        time.sleep(obs.get("delay_ms", 0) / 1000.0)
        t = rel()
        status, resp = _http("GET", base + obs["path"], None, token)
        records.append({"t_rel_ms": t, "kind": "observation",
                        "probe": obs.get("probe", "GET " + obs["path"]),
                        "status": status, "body": resp})

    sidecar = {
        "sidecar_version": 1,
        "case_id": spec["case_id"],
        "producer": "seed-capture-driver",
        "mist_commit": mist_commit,
        "sut": spec.get("sut", {}),
        "records": records,
    }
    Path(out_path).parent.mkdir(parents=True, exist_ok=True)
    Path(out_path).write_text(json.dumps(sidecar, indent=1), encoding="utf-8", newline="\n")
    print("captured %d records -> %s" % (len(records), out_path))


if __name__ == "__main__":
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    commit = "7d69de9"
    for a in sys.argv[1:]:
        if a.startswith("--mist-commit="):
            commit = a.split("=", 1)[1]
    if len(args) < 2:
        print(__doc__)
        sys.exit(2)
    run(args[0], args[1], commit)
