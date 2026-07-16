#!/usr/bin/env python3
"""A8 (completion-set wave): the case <-> trace-bundle <-> arm mapping.

For every benchmark case: which COMMITTED trace bundles exist (kind + paths,
existence-verified on disk), and which Phase-B arm can consume what. Gaps are
explicit: a case with no usable bundle for an arm = that arm's NOT_EVALUABLE
row (disclosed), never a silent skip. Emits benchmark/case-trace-arm-map.json.
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
B4 = ROOT / "b4"
OUT = ROOT / "case-trace-arm-map.json"

# case_id -> list of (kind, repo-relative glob/dir) trace sources.
# kinds: leg1-cogen (TT-omnibus leg-1 exports, co-generated with the MIST
# column - the B-B1 pinned surrogate input), e2-run (the E2 wave's frozen
# run-pair traces), traced-capture (per-case capture dirs w/ trace-fault.json),
# none-by-construction (trace-uninstrumented tier or specified-uncaptured).
BUNDLES = {
    "TT-cancel-refund-fabricatedack-001": [
        ("leg1-cogen", "b4/ttomni/leg1/trace-fault-*.json"),
        ("e2-run", "b4/e2/e2-run*-fault-trace.json"),
        ("traced-capture", "b4/captures/tt-s1-cancel-fabricatedack-traced"),
    ],
    "TT-cancel-refund-clean-001": [
        ("leg1-cogen", "b4/ttomni/leg1/trace-control-*.json"),
        ("e2-run", "b4/e2/e2-run*-control-trace.json"),
    ],
    "TT-adminroute-lostwrite-001": [
        ("traced-capture", "b4/captures/tt-s1-adminroute-lostwrite-traced"),
    ],
    "TT-adminroute-control-001": [
        ("traced-capture", "b4/captures/tt-ctl-adminroute-create-traced"),
    ],
    "TT-adminbasic-contacts-lostwrite-001": [
        ("traced-capture", "b4/captures/tt-s1-adminbasic-contacts-lostwrite-traced"),
    ],
    "TT-adminbasic-contacts-control-001": [],  # no -traced control twin committed
    "oteldemo-checkout-lost-001": [
        ("traced-capture", "b4/captures/oteldemo-checkout-lost"),
    ],
    "oteldemo-checkout-control-001": [
        ("traced-capture", "b4/captures/oteldemo-checkout-control"),
    ],
    "oteldemo-checkout-eventual-benign-001": [],  # w120 natural observation
    "oteldemo-checkout-eventual-benign-002": [
        ("traced-capture", "b4/captures/oteldemo-checkout-eventual-induced"),
    ],
    "oteldemo-checkout-eventual-benign-003": [
        ("traced-capture", "b4/captures/oteldemo-checkout-eventual-induced"),
    ],
    "bookinfo-ratings-benign-001": [
        ("traced-capture", "b4/captures/bookinfo-ratings-benign-traced"),
    ],
    "sockshop-shipping-swallowed-enqueue-001": [
        ("traced-capture", "b4/captures/sockshop-shipping-swallowed-traced"),
    ],
    "sockshop-shipping-control-001": [
        ("traced-capture", "b4/captures/sockshop-shipping-control-traced"),
    ],
    "TT-createaccount-agreement-001": [],   # invisible-by-construction: N-vs-0
    "TT-createaccount-clean-001": [],
}
UNINSTRUMENTED = [
    "teastore-order-maintenance-masked-001", "teastore-order-control-001",
    "teastore-order-meshsever-masked-001", "teastore-order-meshsever-control-001",
    "teastore-orderitems-meshsever-masked-001",
    "teastore-orderitems-meshsever-control-001",
    "teastore-order-depdown-specified-001",
    "TT-cancel-refund-natural-001", "TT-contacts-dedupe-benign-001",
    "TT-contacts-noop-modify-benign-001",
]

ARMS = {
    "tracetest_semantics_surrogate": {
        "consumes": "leg1-cogen (flagship pair; the B-B1 pin) + traced-capture trace-fault.json elsewhere",
        "note": ("offline surrogate; cells labeled 'span-assertion-semantics "
                 "(surrogate; the live tool was NOT run)'"),
    },
    "traceanomaly_conditional": {
        "consumes": "traced-capture / leg1-cogen (input contract resolved at its clearance check)",
        "note": "runs ONLY if the clearance check passes; else construction-blindness demo",
    },
    "contract_invariant": {
        "consumes": "OpenAPI specs + request/response evidence (NOT traces); spike-gated",
        "note": "authored BLIND to outcomes (spec + request side only)",
    },
}


def main():
    vis = json.loads((ROOT / "e2-visibility-census.json").read_text(encoding="utf-8"))
    visclass = {r["case_id"]: r["trace_visibility"] for r in vis["cases"]}
    rows = []
    missing = []
    for cid, cls in sorted(visclass.items()):
        if cid in UNINSTRUMENTED:
            entries = [("none-by-construction",
                        "trace-uninstrumented tier / specified-uncaptured")]
        else:
            entries = BUNDLES.get(cid, [])
            if not entries:
                entries = [("none-committed",
                            "no committed trace bundle - NOT_EVALUABLE for "
                            "trace-consuming arms, disclosed")]
        verified = []
        for kind, loc in entries:
            if kind in ("none-by-construction", "none-committed"):
                verified.append({"kind": kind, "detail": loc})
                continue
            if "*" in loc:
                hits = sorted(str(p.relative_to(ROOT)).replace("\\", "/")
                              for p in ROOT.glob(loc))
            else:
                p = ROOT / loc
                hits = [loc] if p.exists() else []
            if not hits:
                missing.append((cid, loc))
            verified.append({"kind": kind, "path": loc, "verified_files": len(hits)})
        rows.append({"case_id": cid, "trace_visibility": cls, "bundles": verified})
    if missing:
        print(f"FATAL: mapped bundles missing on disk: {missing}", file=sys.stderr)
        return 1
    out = {
        "schema": "case-trace-arm-map/1",
        "generated_by": "build_case_trace_arm_map.py (completion-set wave A8)",
        "note": ("every mapped path existence-verified at build time; "
                 "none-committed / none-by-construction rows are the disclosed "
                 "NOT_EVALUABLE inputs for trace-consuming arms"),
        "arms": ARMS,
        "cases": rows,
    }
    OUT.write_text(json.dumps(out, indent=1, ensure_ascii=False) + "\n",
                   encoding="utf-8")
    tally = {}
    for r in rows:
        for b in r["bundles"]:
            tally[b["kind"]] = tally.get(b["kind"], 0) + 1
    print(f"wrote {OUT.name}: {len(rows)} cases; bundle-kind tally={tally}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
