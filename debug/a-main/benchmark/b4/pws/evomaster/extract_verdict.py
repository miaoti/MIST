#!/usr/bin/env python3
"""PWS L1: extract the per-site EvoMaster reachability + masked-fault verdict from a
control/fault run pair. Reachability = did generated tests exercise the site's WRITE
endpoint AND establish an acked-2xx baseline (control). Verdict rule (rev-2 pinned):
detect iff a generated test FAILS implicating the masked write; a miss WITHOUT reachability
= NOT_INTERPRETABLE. Descriptive-only; NEVER merged into the matched-recall table.

usage: extract_verdict.py <site> <control_dir> <fault_dir> <write_endpoint_fragment>
"""
import json
import re
import sys
from pathlib import Path


def load(run_dir):
    rj = Path(run_dir) / "report.json"
    if not rj.exists():
        return None
    return json.loads(rj.read_text(encoding="utf-8"))


def endpoints_2xx(report):
    # EvoMaster report.json carries problemDetails; the "Successfully executed 2xx N of M"
    # line is in the log — here we approximate reachability from the test files instead.
    return None


def write_endpoint_hit(run_dir, frag):
    """Did any generated test target the write endpoint fragment?"""
    hits = 0
    for f in Path(run_dir).glob("*_faults.py"):
        hits += f.read_text(encoding="utf-8", errors="replace").count(frag)
    for f in Path(run_dir).glob("*_successes.py"):
        hits += f.read_text(encoding="utf-8", errors="replace").count(frag)
    return hits


def main():
    site, ctrl, fault, frag = sys.argv[1:5]
    cr, fr = load(ctrl), load(fault)
    out = {
        "site": site,
        "tool": "EvoMaster v6.1.1 (sha 7aa06eb6…, LGPL-3.0, black-box, seed 42, 60m)",
        "control_run": {
            "total_tests": cr.get("totalTests") if cr else None,
            "potential_faults": (cr.get("faults") or {}).get("total")
            if cr and isinstance(cr.get("faults"), dict) else cr.get("faults") if cr else None,
            "write_endpoint_tests": write_endpoint_hit(ctrl, frag),
        },
        "fault_run": {
            "total_tests": fr.get("totalTests") if fr else None,
            "potential_faults": (fr.get("faults") or {}).get("total")
            if fr and isinstance(fr.get("faults"), dict) else fr.get("faults") if fr else None,
            "write_endpoint_tests": write_endpoint_hit(fault, frag),
        },
    }
    # reachability: the write endpoint was exercised in BOTH runs
    reached = out["control_run"]["write_endpoint_tests"] > 0 and \
        out["fault_run"]["write_endpoint_tests"] > 0
    out["write_endpoint_reached"] = reached
    # NB: the acked-2xx-baseline check is read from the log's "Successfully executed 2xx"
    # line and pasted into the RESULT (report.json does not carry per-endpoint 2xx cleanly);
    # this extractor records the reachability-of-target datum + fault counts. The masked-loss
    # verdict adjudication (did any test fail IMPLICATING the masked write) is done by hand
    # against the *_faults.py contents + the RESULT template, because EvoMaster's fault
    # taxonomy (500/schema/timeout) never includes "acked-2xx-but-durably-lost" by
    # construction — so the expected cell is a MEASURED miss WITH reachability, or
    # NOT_INTERPRETABLE if the acked-2xx baseline was never reached.
    print(json.dumps(out, indent=1, ensure_ascii=False))


if __name__ == "__main__":
    main()
