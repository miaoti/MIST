#!/usr/bin/env python3
"""Seed-capture driver (checklist §1.95.2; corpus plan §2.2 rev 2).

Thin, MIST-free transcript recorder: executes a capture-spec YAML against the
live SUT and writes a sidecar.json (format v1 — corpus-19505-sidecar-format.md).
Harness-level by design: keeps the MIST study-commit pin untouched; records
BEHAVIOR only (no verdicts). Auth tokens/credentials are redacted in the transcript
(the driver records NO auth headers, so which identity issued a step is invisible).

Two spec shapes are supported:
  (A) simple (adminroute):  login: {...} + steps: [...] + observations: [...]
  (B) multi-auth / variable-flow (cancel trio):  admin_login + gen_user + steps
      whose entries may carry `auth` (session|admin|none), `capture` (jsonpath ->
      var), `set_session` (make a captured var the session bearer), `redact`, and
      `{var}` substitution in path/payload; observations may carry `auth`.

usage: capture_driver.py <spec.yaml> <out-sidecar.json> [--mist-commit=SHA] [--case-id=ID]
"""
import json
import re
import sys
import time
import urllib.request
import urllib.error
from pathlib import Path

import yaml

_VAR = re.compile(r"\{(\w+)\}")


def _http(method, url, payload=None, token=None, timeout=25):
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


def _subst(s, variables):
    if s is None:
        return None
    return _VAR.sub(lambda m: str(variables.get(m.group(1), m.group(0))), s)


def _jget(body, path):
    """Resolve a dotted json path with optional [N] indices, e.g. data[0].id."""
    obj = json.loads(body)
    for part in re.findall(r"[^.\[\]]+", path):
        obj = obj[int(part)] if isinstance(obj, list) else obj[part]
    return obj


def _login_token(base, login):
    body = json.dumps({"username": login["username"], "password": str(login["password"])})
    status, resp = _http("POST", base + login["path"], body)
    try:
        return json.loads(resp)["data"]["token"]
    except Exception:
        raise SystemExit("login failed for %s: HTTP %s %s" % (login["username"], status, resp[:200]))


def run(spec_path, out_path, mist_commit, case_id_override=None):
    spec = yaml.safe_load(Path(spec_path).read_text(encoding="utf-8"))
    base = spec["base_url"].rstrip("/")
    records = []
    variables = {}
    if spec.get("gen_user"):
        tag = str(int(time.time())) + str(int(time.monotonic() * 1000) % 1000)
        variables["user"] = "cb" + tag          # <=20 chars, within the SUT's login bounds
        variables["pw"] = "pw" + tag
        variables["docnum"] = "CB" + tag

    admin_token = None
    if spec.get("admin_login"):
        al = spec["admin_login"]
        admin_token = _login_token(base, {"path": al.get("path", "/api/v1/users/login"),
                                          "username": al["username"], "password": al["password"]})

    t0 = time.monotonic()

    def rel():
        return int((time.monotonic() - t0) * 1000)

    session_token = None

    # Shape (A): the special login phase (adminroute). Sets the session bearer.
    login = spec.get("login")
    if login:
        t = rel()
        body = json.dumps({"username": login["username"], "password": str(login["password"])})
        status, resp = _http("POST", base + login["path"], body)
        records.append({"t_rel_ms": t, "kind": "request", "method": "POST", "path": login["path"],
                        "payload": "{\"username\":\"<redacted>\",\"password\":\"<redacted>\"}"})
        records.append({"t_rel_ms": rel(), "kind": "response", "status": status,
                        "body": "<login response — token redacted>"})
        try:
            session_token = json.loads(resp)["data"]["token"]
        except Exception:
            raise SystemExit("login failed: HTTP %s %s" % (status, resp[:200]))

    def token_for(auth):
        if auth == "admin":
            return admin_token
        if auth == "none":
            return None
        return session_token  # default

    for step in spec.get("steps", []):
        path = _subst(step["path"], variables)
        payload = _subst(step.get("payload"), variables)
        t = rel()
        status, resp = _http(step["method"], base + path, payload, token_for(step.get("auth", "session")))
        # capture BEFORE redaction (reads the real response body)
        for var, jp in (step.get("capture") or {}).items():
            try:
                variables[var] = str(_jget(resp, jp))
            except Exception:
                raise SystemExit("capture '%s' via '%s' failed (%s step %s): HTTP %s %s"
                                 % (var, jp, step["method"], path, status, resp[:200]))
        if step.get("set_session"):
            session_token = variables[step["set_session"]]
        redact = step.get("redact")
        rec = {"t_rel_ms": t, "kind": "request", "method": step["method"], "path": path}
        if payload is not None:
            rec["payload"] = "<credentials redacted>" if redact else payload
        records.append(rec)
        records.append({"t_rel_ms": rel(), "kind": "response", "status": status,
                        "body": "<response redacted (carries a session token)>" if redact else resp})

    for obs in spec.get("observations", []):
        time.sleep(obs.get("delay_ms", 0) / 1000.0)
        path = _subst(obs["path"], variables)
        t = rel()
        status, resp = _http("GET", base + path, None, token_for(obs.get("auth", "session")))
        records.append({"t_rel_ms": t, "kind": "observation",
                        "probe": obs.get("probe", "GET " + path), "status": status, "body": resp})

    sidecar = {
        "sidecar_version": 1,
        "case_id": case_id_override or spec["case_id"],
        "producer": "seed-capture-driver",
        "mist_commit": mist_commit,
        "sut": spec.get("sut", {}),
        "records": records,
    }
    Path(out_path).parent.mkdir(parents=True, exist_ok=True)
    Path(out_path).write_text(json.dumps(sidecar, indent=1), encoding="utf-8", newline="\n")
    print("captured %d records -> %s" % (len(records), out_path))


if __name__ == "__main__":
    pos = [a for a in sys.argv[1:] if not a.startswith("--")]
    commit = "7d69de9"
    case_id = None
    for a in sys.argv[1:]:
        if a.startswith("--mist-commit="):
            commit = a.split("=", 1)[1]
        elif a.startswith("--case-id="):
            case_id = a.split("=", 1)[1]
    if len(pos) < 2:
        print(__doc__)
        sys.exit(2)
    run(pos[0], pos[1], commit, case_id)
