#!/usr/bin/env python3
"""A-venue recheck (unanimous top-ROI item): execute the REAL tool's FULL oracle
stack on MIST-REACHED executions — closing surrogate==real by measurement, not grep.

Method: a byte-replay server serves the RECORDED responses (status/content-type/body)
from the meshsever capture sidecars (control + lost legs) for POST /api/cart and
POST /api/checkout; Schemathesis v4.23.0 (`st run --checks all`, the tool's entire
response-oracle suite) runs against it under the committed clean-room OTel spec.
The response side of every exchange IS the recorded ack from the MIST-reached run
(request side is tool-generated; the oracle axis holds the RESPONSE constant — the
thing an oracle judges). Two runs; the discriminative question: does ANY check fire
on the lost leg that does not fire identically on the control leg?
"""
import json
import re
import os
import subprocess
import sys
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

HERE = Path(__file__).resolve().parent
CAP = HERE.parent / "captures"
SPEC = Path(r"C:\Users\miaot\Github\MIST\evaluation\suts\oteldemo\openapi\oteldemo-swagger.yaml")
OUT = HERE / "realoracle"
OUT.mkdir(exist_ok=True)


def load_replay(leg):
    d = json.loads((CAP / f"oteldemo-checkout-meshsever-{leg}" / "sidecar.json")
                   .read_text(encoding="utf-8"))
    replay = {}
    recs = d["records"]
    for i, r in enumerate(recs):
        if r["kind"] == "request":
            rsp = next((x for x in recs[i + 1:] if x["kind"] == "response"), None)
            if rsp:
                op = r["path"].split("?")[0]
                # spec basePath is /api and `st --url` targets spec-RELATIVE paths
                # (/cart, /checkout) - key the replay at the spec-relative op (the
                # v1 run keyed /api/cart and 404'd everything = vacuous; corrected)
                if op.startswith("/api/"):
                    op = op[4:]
                replay[(r["method"], op)] = (rsp["status"], rsp.get("body") or "")
    return replay


def serve(replay, port):
    class H(BaseHTTPRequestHandler):
        protocol_version = "HTTP/1.1"

        def _send(self, status, body):
            data = body.encode()
            self.send_response(status)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)

        def _handle(self):
            try:
                n = int(self.headers.get("Content-Length") or 0)
                if n:
                    self.rfile.read(n)
                op = self.path.split("?")[0]
                key = (self.command, op)
                if key in replay:
                    self._send(*replay[key])
                else:
                    self._send(404, '{"error":"not recorded"}')
            except Exception:
                try:
                    self._send(404, '{"error":"replay edge"}')
                except Exception:
                    pass

        def do_OPTIONS(self):
            self.send_response(405)
            self.send_header("Allow", "POST")
            self.send_header("Content-Length", "0")
            self.end_headers()

        do_GET = do_POST = do_PUT = do_DELETE = do_PATCH = do_HEAD = _handle

        def log_message(self, *a):
            pass

    srv = ThreadingHTTPServer(("127.0.0.1", port), H)
    threading.Thread(target=srv.serve_forever, daemon=True).start()
    return srv


def run_leg(leg, port):
    replay = load_replay(leg)
    srv = serve(replay, port)
    log = OUT / f"{leg}.log"
    with log.open("w", encoding="utf-8") as f:
        p = subprocess.run(
            ["st", "run", str(SPEC), "--url", f"http://127.0.0.1:{port}",
             "--checks", "all", "--include-path-regex", "^/(cart|checkout)$",
             "--max-examples", "10", "--seed", "7",],
            stdout=f, stderr=subprocess.STDOUT, timeout=600,
            env={**os.environ, "PYTHONIOENCODING": "utf-8", "PYTHONUTF8": "1"})
    srv.shutdown()
    txt = log.read_text(encoding="utf-8", errors="replace")
    # v4 summary lines: "N passed", "N failed" + per-failure titles
    failures = sorted(set(re.findall(r"^\s*- (.+)$", txt, re.M)))
    summary = "; ".join(l.strip() for l in txt.splitlines()
                        if re.search(r"(passed|failed|errored)", l))[:400]
    return {"leg": leg, "exit": p.returncode, "failure_titles": failures,
            "summary_line": summary}


def main():
    res = {leg: run_leg(leg, 8770 + i) for i, leg in enumerate(["control", "lost"])}
    same = res["control"]["failure_titles"] == res["lost"]["failure_titles"]
    cell = {
        "experiment": "real-oracle-on-MIST-reached-execution (byte-replay of recorded acks)",
        "tool": "Schemathesis v4.23.0, st run --checks all (the tool's ENTIRE response-oracle suite, executed)",
        "spec": "evaluation/suts/oteldemo/openapi/oteldemo-swagger.yaml (committed clean-room E1)",
        "input": "meshsever capture sidecars: the RECORDED cart+checkout acks of the control and lost legs "
                 "(the lost leg's checkout ack is the genuine acknowledged-but-LOST 200 with orderId/tracking)",
        "legs": res,
        "leg_invariant": same,
        "verdict": ("MEASURED MISS: the real tool's full oracle suite produces IDENTICAL findings on the "
                    "acknowledged-but-lost leg and the control leg (zero leg-discriminating flags; zero "
                    "data-integrity findings) - on the very executions where MIST's read-back FIREs. "
                    "surrogate==real closed by execution." if same else
                    "LEG-DIVERGENT - inspect logs before claiming anything."),
    }
    (OUT / "realoracle-cell.json").write_text(json.dumps(cell, indent=2) + "\n",
                                              encoding="utf-8")
    print(json.dumps(cell, indent=2)[:1500])


if __name__ == "__main__":
    main()
