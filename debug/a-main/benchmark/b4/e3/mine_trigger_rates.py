#!/usr/bin/env python3
"""A1 (completion-set wave): E3 trigger-rate mining over the EXISTING committed
evidence (the M-yield 6-SUT trees + the TT-omnibus leg logs) - checklist Step 8.

"Trigger" (rev-2 pinned definition): an oracle-check emission above INFO in the
preserved per-seed stdout logs, counted per ORACLE FAMILY per SUT. Purely
DESCRIPTIVE pipeline telemetry: no yield/defect language (adjudication is
rater-gated), no cross-SUT pooling into any headline.
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]   # .../benchmark
MYC = ROOT / "b4" / "ttomni" / "myc"
LEG_LOGS = [ROOT / "b4" / "ttomni" / "leg1",
            ROOT / "b4" / "ttomni" / "leg3"]
OUT = Path(__file__).resolve().parent / "e3-trigger-rates.json"

# Oracle-family markers (WARN+ emissions in MistMain stdout).
FAMILIES = {
    "data_integrity_armed": re.compile(r"OBSERVE mode: session '[^']+' armed"),
    "data_integrity_record": re.compile(r"DataIntegrity\["),
    "data_integrity_timeout_absent": re.compile(r"TIMEOUT_ABSENT"),
    "data_integrity_observed_absent": re.compile(r"OBSERVED_COMPLETE_ABSENT"),
    "data_integrity_observed_present": re.compile(r"OBSERVED_PRESENT"),
    "readback_bound_threshold": re.compile(r"readback_bound", re.IGNORECASE),
    "trace_shape_verdict": re.compile(r"TraceShape|hidden_downstream", re.IGNORECASE),
    "oracle_on_banner": re.compile(r"data-integrity oracle ON"),
}


def scan(fp):
    counts = {k: 0 for k in FAMILIES}
    try:
        text = fp.read_text(encoding="utf-8", errors="replace")
    except OSError:
        return counts, 0
    lines = text.replace("\r", "\n").split("\n")
    for ln in lines:
        for fam, rx in FAMILIES.items():
            if rx.search(ln):
                counts[fam] += 1
    return counts, len(lines)


def main():
    per_sut = {}
    for sut_dir in sorted(p for p in MYC.iterdir() if p.is_dir()):
        agg = {k: 0 for k in FAMILIES}
        logs = sorted(sut_dir.glob("myc-s*.log"))
        for lg in logs:
            c, _ = scan(lg)
            for k, v in c.items():
                agg[k] += v
        if logs:
            per_sut[f"myc/{sut_dir.name}"] = {"seed_logs": len(logs), **agg}
    for leg in LEG_LOGS:
        if not leg.is_dir():
            continue
        agg = {k: 0 for k in FAMILIES}
        logs = sorted(leg.glob("*.log"))
        for lg in logs:
            c, _ = scan(lg)
            for k, v in c.items():
                agg[k] += v
        if logs:
            per_sut[f"ttomni/{leg.name}"] = {"seed_logs": len(logs), **agg}

    # executed-test denominators from the committed clustering (descriptive
    # rate context only - never a yield claim).
    clustering = MYC / "CLUSTERING-myc.json"
    denom = {}
    if clustering.exists():
        cj = json.loads(clustering.read_text(encoding="utf-8"))
        for sut, blob in cj.get("suts", {}).items():
            denom[sut] = blob.get("flagged_total")

    out = {
        "schema": "e3-trigger-rates/1",
        "generated_by": "b4/e3/mine_trigger_rates.py (completion-set wave A1)",
        "trigger_definition": ("an oracle-check emission above INFO in the "
                               "preserved per-seed stdout logs, per oracle "
                               "family per SUT (rev-2 pinned)"),
        "rails": ["descriptive pipeline telemetry only",
                  "no yield/defect language (rater-gated)",
                  "no cross-SUT pooling",
                  "mist.log was NOT preserved (MYC disclosure 8b) - stdout is "
                  "the mined record; families that print only to mist.log are "
                  "undercounted by construction, disclosed"],
        "failed_test_context_from_clustering": denom,
        "per_source": per_sut,
    }
    OUT.write_text(json.dumps(out, indent=1, ensure_ascii=False) + "\n",
                   encoding="utf-8")
    print(f"wrote {OUT.name}")
    for src, row in per_sut.items():
        nz = {k: v for k, v in row.items() if k != "seed_logs" and v}
        print(f"  {src}: logs={row['seed_logs']} {nz}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
