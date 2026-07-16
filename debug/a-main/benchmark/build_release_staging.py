#!/usr/bin/env python3
"""A4 (completion-set wave): E6 release STAGING + the reproduction census.
Assembles the benchmark-release recipe under benchmark/release-staging/ —
index.generated.json (member list w/ hashes) + MANIFEST.sha256 + the per-case
executable-reproduction census. STAGING ONLY: nothing is published; the
fork-publication decision and any push are USER-side (Step 8 / E6).
"""
import hashlib
import json
import sys
from pathlib import Path

BENCH = Path(__file__).resolve().parent
STAGE = BENCH / "release-staging"
REPO = BENCH.parents[2]


def sha(p):
    return hashlib.sha256(p.read_bytes()).hexdigest()


def main():
    STAGE.mkdir(exist_ok=True)
    members = []
    groups = {
        "cases": sorted((BENCH / "cases").glob("*.json")),
        "schema": sorted((BENCH / "schema").glob("*.json")),
        "censuses": [BENCH / "mist-column-census.json",
                     BENCH / "e2-visibility-census.json",
                     BENCH / "case-trace-arm-map.json"],
        "scoring": sorted((BENCH / "scoring").glob("*.py"))
        + [BENCH / "scoring" / "README.md",
           BENCH / "scoring" / "matched-recall-table.json"],
        "specs": sorted((REPO / "evaluation" / "suts").glob("*/openapi/*.yaml")),
    }
    for group, paths in groups.items():
        for p in paths:
            if not p.exists():
                print(f"FATAL: missing member {p}", file=sys.stderr)
                return 1
            members.append({
                "group": group,
                "path": str(p.relative_to(REPO)).replace("\\", "/"),
                "sha256": sha(p),
            })

    # reproduction census: per case, is the fault executable-reproducible from
    # the committed record, or capture-only?
    census = []
    for f in sorted((BENCH / "cases").glob("*.json")):
        c = json.loads(f.read_text(encoding="utf-8"))
        sut = c.get("sut", {})
        fault = c.get("fault", {})
        stim = c.get("stimulus", {})
        replay = sut.get("replay_script") or stim.get("script")
        replay_exists = bool(replay) and (REPO / replay).exists()
        method = fault.get("injection_method")
        cap = c.get("capture_status")
        if cap == "specified":
            status = "specified-design-target (no capture to reproduce)"
        elif replay_exists and method in ("sut_injector", "mesh_abort", "vendor_flag",
                                          "dependency_scale_zero"):
            status = "executable-reproduction (replay script committed + mechanized injection)"
        elif replay_exists and method in ("none", "natural"):
            status = "executable-reproduction (no injection; stimulus replay)"
        elif replay_exists:
            status = f"replayable-stimulus (injection '{method}' unmechanized)"
        else:
            status = "capture-only (no committed replay script)"
        census.append({
            "case_id": c.get("case_id", f.stem),
            "capture_status": cap,
            "injection_method": method,
            "replay_script": replay,
            "replay_script_exists": replay_exists,
            "reproduction_status": status,
        })
    tally = {}
    for r in census:
        tally[r["reproduction_status"]] = tally.get(r["reproduction_status"], 0) + 1

    index = {
        "schema": "release-staging-index/1",
        "generated_by": "build_release_staging.py (completion-set wave A4)",
        "note": ("STAGING RECIPE - the release repo is assembled FROM these committed "
                 "members at the USER-gated E6 step (fork-publication decision = USER). "
                 "Rater-sidecars enter only via the Step-5 seal decisions. Large "
                 "artifacts (captures/evidence trees) ship by hash via Zenodo/OSF "
                 "per Step 8."),
        "licenses": {
            "case_code": "Apache-2.0", "case_data": "CC-BY-4.0",
            "scoring_harness": "Apache-2.0",
            "specs": "per c2-license-audit.md (heterogeneous provenance DISCLOSED: "
                     "authored-by-us sockshop/teastore/oteldemo; stock-upstream bookinfo; "
                     "machine-generated trainticket; boutique no-in-file-license)",
            "mist": "by reference (LGPL), never vendored",
        },
        "members": members,
        "reproduction_census_tally": tally,
    }
    (STAGE / "index.generated.json").write_text(
        json.dumps(index, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    (STAGE / "reproduction-census.json").write_text(
        json.dumps({"schema": "reproduction-census/1", "tally": tally,
                    "cases": census}, indent=1, ensure_ascii=False) + "\n",
        encoding="utf-8")
    with (STAGE / "MANIFEST.sha256").open("w", encoding="utf-8", newline="\n") as fh:
        fh.write("# E6 release-staging manifest (completion-set wave A4; staging only)\n")
        for mrow in members:
            fh.write(f"{mrow['sha256']}  {mrow['path']}\n")
    print(f"staged {len(members)} members; reproduction tally={tally}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
