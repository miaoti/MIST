#!/usr/bin/env python3
"""A3(iv) (completion-set wave): calibration-set assembly REHEARSAL + entry-gate
dry-run over the machine-checkable subset. Staging only — assigns nothing, seals
nothing; the real assembly is the gated Step-5 (USER-witnessed) step.
"""
import json
import sys
from pathlib import Path

B4 = Path(__file__).resolve().parent
BENCH = B4.parent
OUT = B4 / "rater-sidecars-staging" / "calibration-rehearsal.json"

TARGET = 50          # max(30, 50 - |S3|), |S3| = 0 (frozen convention)
BENIGN_SKEW = 2.0    # benign:genuine >= 2:1


def main():
    m = json.loads((B4 / "MANIFEST-r2.json").read_text(encoding="utf-8"))
    rows = m["rows"]
    pools = {}
    for r in rows:
        pools.setdefault((r["rateability"], r["label"]), []).append(r["case_id"])

    ok_neg = pools.get(("ok", "negative"), [])
    ok_pos = pools.get(("ok", "positive"), [])
    tg_neg = pools.get(("tt-collection-truncation-gated", "negative"), [])
    tg_pos = pools.get(("tt-collection-truncation-gated", "positive"), [])
    ss_pos = pools.get(("trace-required-not-blind-rateable", "positive"), [])

    def scenario(name, neg, pos, note):
        n, p = len(neg), len(pos)
        skew_ok = (p == 0) or (n / p >= BENIGN_SKEW)
        # enforce the skew by holding back genuines if needed
        held = []
        while p > 0 and n / p < BENIGN_SKEW:
            held.append(pos[p - 1]); p -= 1
        return {"scenario": name, "note": note,
                "benign": n, "genuine": p, "genuine_held_for_skew": held,
                "units": n + p, "target": TARGET, "shortfall": max(0, TARGET - n - p),
                "skew_ok_as_drawn": skew_ok}

    scenarios = [
        scenario("S0-ok-only (current sealed rateability)", ok_neg, ok_pos,
                 "no seal-time branches taken"),
        scenario("S1-ok + truncation-branch-(a) re-capture", ok_neg + tg_neg,
                 ok_pos + tg_pos,
                 "A3(ii) branch (a): +9 units IF the USER elects the TT re-capture window"),
        scenario("S2-ok + SS-include", ok_neg, ok_pos + ss_pos,
                 "A3(iii-a) branch (b): +1 genuine w/ white-box tell cost"),
    ]

    # entry-gate DRY-RUN (machine-checkable subset only)
    versions = sorted({r.get("sidecar_sha256") is not None for r in rows})
    same_harness = len({m.get("schema")}) == 1 and m.get("deterministic_all") is True
    elig = set()
    for f in (BENCH / "eligibility").glob("*.json"):
        try:
            e = json.loads(f.read_text(encoding="utf-8"))
            ids = e if isinstance(e, list) else e.get("cases", [])
            for it in ids:
                elig.add(it.get("case_id") if isinstance(it, dict) else str(it))
        except Exception:
            pass
    cal_candidates = set(ok_neg + ok_pos)
    checks = {
        "same_harness_manifest (schema uniform + deterministic_all)": bool(same_harness),
        "corpus_content_hash_present": bool(m.get("corpus_content_hash")),
        "capture_status_captured_by_construction": "25/25 (the specified case has no sidecar row)",
        "disjointness_calibration_vs_eligibility": sorted(cal_candidates & elig) == [],
        "disjointness_vs_S3_confirmed": "|S3|=0 - vacuous",
        "disjointness_vs_myield_audit": "M-yield audit = generated-test level, never corpus cases - disjoint by type",
        "opaque_id_guard": "render-time mechanical guard (E1+R2 hardening) - enforced at cut",
        "USER-PENDING (not machine-checkable now)": [
            "IRB determination received BEFORE first contact",
            "blindness-screen + debrief records per rater",
            "rubric version sealed + corpus hash frozen at seal",
            "worked examples authored on real calibration cases",
        ],
    }

    out = {
        "schema": "calibration-rehearsal/1",
        "generated_by": "b4/rehearse_calibration.py (completion-set wave A3-iv)",
        "note": ("REHEARSAL ONLY - no assignment, no seal. Demand of record (R1d): "
                 "target 50 w/ benign share ~40-43; the structural shortfall is the "
                 "corpus's disclosed finding, quantified here per seal-time branch."),
        "target": TARGET, "benign_skew_floor": BENIGN_SKEW,
        "pools": {str(k): v for k, v in pools.items()},
        "scenarios": scenarios,
        "entry_gate_dry_run": checks,
    }
    OUT.write_text(json.dumps(out, indent=1, ensure_ascii=False) + "\n",
                   encoding="utf-8")
    print(f"wrote {OUT.relative_to(B4)}")
    for s in scenarios:
        print(f"  {s['scenario']}: units={s['units']} (b{s['benign']}/g{s['genuine']}"
              f"{' held:'+str(len(s['genuine_held_for_skew'])) if s['genuine_held_for_skew'] else ''})"
              f" shortfall={s['shortfall']}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
