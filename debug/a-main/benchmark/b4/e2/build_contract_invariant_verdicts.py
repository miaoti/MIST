#!/usr/bin/env python3
"""Phase B leg 2 (completion-set wave): the contract-invariant arm's verdict
file, MACHINE-DERIVED from the banked live runs.

Mechanical basis on THIS corpus: the FROZEN response-contract comparator (the
E2 run of record) IS a live contract-conformance verdict on the ack - its
banked lines ("Comparator (frozen response contract): control flagged=false,
fault flagged=false") are parsed here for the flagship pair (5 runs, must be
uniform). Every other case = not_evaluable: the committed capture sidecars
persist response payloads as NULL (surveyed 2026-07-16), so no machine ack
exists to validate against the specs. SPIKE CORRECTION (disclosed): the A2
spike's "ack transcripts live in the committed capture sidecars" input claim
was WRONG - corrected by this run's survey; the spike's GO stands on the
flagship pair's live cells + the enumerated NOT_EVALUABLE discipline.
"""
import json
import re
import sys
from pathlib import Path

B4 = Path(__file__).resolve().parents[1]
BENCH = B4.parent
OUT = BENCH / "scoring" / "verdicts" / "contract_invariant.json"
LOG = B4 / "e2" / "e2-run-stdout.txt"

COMP = re.compile(r"Comparator \(frozen response contract\): control flagged=(\w+), "
                  r"fault flagged=(\w+)")


def main():
    text = LOG.read_text(encoding="utf-8", errors="replace")
    hits = COMP.findall(text)
    if len(hits) != 5 or any(h != ("false", "false") for h in hits):
        print(f"STOP: expected 5 uniform (false,false) comparator lines, got {hits}",
              file=sys.stderr)
        return 1
    cases = {}
    for f in sorted((BENCH / "cases").glob("*.json")):
        cid = json.loads(f.read_text(encoding="utf-8")).get("case_id", f.stem)
        cases[cid] = "not_evaluable"
    cases["TT-cancel-refund-fabricatedack-001"] = "no_flag"  # ack conformed x5 (live)
    cases["TT-cancel-refund-clean-001"] = "no_flag"          # control conformed x5 (live)
    out = {
        "arm": "contract_invariant",
        "verdict_source": (
            "LIVE cells (flagship pair): the frozen response-contract comparator's "
            "banked E2 run-of-record lines (b4/e2/e2-run-stdout.txt, 5/5 uniform "
            "control=false fault=false; ack facts logged: http 200, body status 1) - "
            "a contract-conformance verdict measured on the running SUT. All other "
            "cases: not_evaluable - the committed capture sidecars persist response "
            "payloads as NULL (2026-07-16 survey; no machine ack to validate). "
            "Single-response semantics only (AGORA-class); never a lifecycle oracle."),
        "spike_correction": (
            "A2 spike input claim 'ack transcripts live in the committed capture "
            "sidecars' was WRONG (all response payloads null) - corrected here."),
        "cases": cases,
    }
    OUT.write_text(json.dumps(out, indent=1, ensure_ascii=False) + "\n",
                   encoding="utf-8")
    ne = sum(1 for v in cases.values() if v == "not_evaluable")
    print(f"wrote {OUT.name}: no_flag 2 (live) / not_evaluable {ne}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
