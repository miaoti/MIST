#!/usr/bin/env python3
"""A5 (completion-set wave; benchmark-of-record 26 cases as of 2026-07-21): build the MIST read-back-column census.

Reads every case file under benchmark/cases/ and emits
benchmark/mist-column-census.json: per case -> label, mist_readback_oracle,
oracle_mode, mist_commit, capture_status, readback_shape, rateability-relevant
fields, and an adjudication slot (structural vs bindable-pending-eval) filled for every
not_applicable case (10 as of the A3 F-corpus). Deterministic (sorted by case id).
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
CASES = ROOT / "cases"
OUT = ROOT / "mist-column-census.json"
OUT_VIS = ROOT / "e2-visibility-census.json"


# A5 adjudications of the 8 not_applicable cases (completion-set wave, 2026-07-16;
# evidence = each case file's readback/notes fields, extracted + reviewed at execution).
ADJUDICATIONS = {
    "bookinfo-ratings-benign-001": {
        "class": "structural",
        "reason": ("read-path degradation case: no write is intended and no durable "
                   "read-back exists (readback.modality=none-durable; the discriminator "
                   "is the trace) - the read-back oracle has no target by construction"),
    },
    "sockshop-shipping-control-001": {
        "class": "structural",
        "reason": ("read-back surface non-discriminating by design: order.shipment is "
                   "written at order creation regardless of enqueue outcome and "
                   "queue-master persists nothing (G3-prereg async question resolved "
                   "NEGATIVE) - the documented applicability boundary of the oracle"),
    },
    "sockshop-shipping-swallowed-enqueue-001": {
        "class": "structural",
        "reason": ("same site as its control twin: the only durable field is written "
                   "regardless of the swallowed enqueue; trace-only discriminator "
                   "(rateability: trace-required-not-blind-rateable per MANIFEST-r2)"),
    },
    # teastore-order-depdown-specified-001 RETIRED 2026-07-21 to cases/excluded-out-of-mask/
    # (live MIST oracle NO_FIRE 0/4 -> LOUD-500 loss, out of the 2xx-gated masked-2xx class;
    # b4/RESULT-depdown-live.md). No longer in the cases/*.json glob, so no adjudication entry.
    "TT-order-contact-corrupt-f10-001": {
        "class": "structural",
        "reason": ("CORRUPTED-present (order persists rotated contact documentNumber, "
                   "not lost) - out-of-scope-by-design (lost-not-corrupted rail); "
                   "A3 F-corpus, live B-m6 PASS via the real booking flow"),
    },
    "TT-cancel-status-recheck-corrupt-f11-001": {
        "class": "structural",
        "reason": ("CORRUPTED-present INTERMITTENT (cancel persists CHANGE instead of "
                   "CANCEL on skipped-recheck invocations) - out-of-scope-by-design; "
                   "A3 F-corpus, live B-m6 PASS"),
    },
    "TT-basic-price-corrupt-f14-001": {
        "class": "structural",
        "reason": ("CORRUPTED-present (order persists first-class-rate price for a "
                   "second-class booking; search surface truthful) - out-of-scope-"
                   "by-design; A3 F-corpus NEW-SITE #2, live B-m6 PASS"),
    },
    "TT-order-statusskew-corrupt-f20-001": {
        "class": "structural",
        "reason": ("CORRUPTED-present (modifyOrder persists (status+1) mod 7, a valid-"
                   "but-wrong code) - out-of-scope-by-design; A3 F-corpus, live "
                   "B-m6 PASS"),
    },
    "TT-user-selection-corrupt-f8-001": {
        "class": "structural",
        "reason": ("CORRUPTED-present (documentType persisted wrong, not lost) - "
                   "out-of-scope-by-design: MIST detects lost/absent not "
                   "present-but-wrong (lost-not-corrupted rail); the benchmark is "
                   "broader than the tool (A3 F-corpus, live B-m6 PASS)"),
    },
    "oteldemo-checkout-kafkaqueue-lost-001": {
        "class": "barred-by-stop-rule",
        "reason": ("a MIST leg requires another flag-on window, barred by the "
                   "no-third-attempt stop rule this wave; bindable-in-principle "
                   "via the 2.75-A OtelCheckoutHeadToHead site binding - a future "
                   "window = USER decision (disclosed, not silent)"),
    },
}
# A5(iii) RESOLVED 2026-07-16 (the Phase-C TeaStore window): the 4 former
# bindable-pending-eval cases now carry MEASURED verdicts (disclosed cell change;
# labels/ground-truth untouched) - see the PROVENANCE entries below.

# Verdict provenance pointers (which run of record carries each verdict).
# Cases not listed carry the generic pointer (case-file capture-of-record notes
# + the corpus-wide mist_commit pin).
PROVENANCE = {
    "teastore-order-maintenance-masked-001": "2.75-A TeaStore leg (freeze 2.75-A row: paired FIRE 5/5 ground-truth-verified)",
    "teastore-order-control-001": "2.75-A TeaStore control leg (no_flag)",
    "oteldemo-checkout-lost-001": "2.75-A OTel leg (paired FIRE 5/5; the flagship async loss, oracle_mode=paired)",
    "oteldemo-checkout-control-001": "2.75-A OTel control leg (no_flag)",
    "oteldemo-checkout-eventual-benign-001": "S3 w120 natural observation + R1d-B source-verified observe abstention (TIMEOUT_ABSENT=WARN-only => no_flag)",
    "oteldemo-checkout-eventual-benign-002": "R1d induced capture (observe abstention => no_flag; DataIntegrityObserveCheck L58-73)",
    "oteldemo-checkout-eventual-benign-003": "R1d induced capture (observe abstention => no_flag)",
    "TT-cancel-refund-fabricatedack-001": "G3 head-to-head cells + TT-omnibus leg-1/paired rerun (paired FIRE 5/5 live provenance; observe OBSERVED_COMPLETE_ABSENT 5/5)",
    "TT-cancel-refund-clean-001": "G3 h2h control + TT-omnibus control (OBSERVED_PRESENT 5/5 => no_flag)",
    "TT-cancel-refund-natural-001": "G3 TT natural cancel-refund defect record (flag)",
    "TT-adminroute-lostwrite-001": "G1 run-3 (prep/gate1-run3-report.json: FIRE, strong stratum)",
    "TT-adminroute-control-001": "G1 run-3 benign record (sync FP 0/2127 corpus => no_flag)",
    "TT-adminbasic-contacts-lostwrite-001": "manual-G0 evidence (G1 run-3 contacts triple NOT_EVALUABLE - unhooked-scenario gap; the flag rests on the manual G0 record, disclosed)",
    "TT-adminbasic-contacts-control-001": "manual-G0 control record (no_flag; same disclosure as its twin)",
    "TT-contacts-dedupe-benign-001": "R1 T2 re-capture (legacy trap re-captured under the T2 cadence; no_flag)",
    "TT-contacts-noop-modify-benign-001": "R1 T2 re-capture (no_flag)",
    "TT-cancel-refund-asyncrefund-f1-001": "A3 F-corpus live B-m6 PASS 2026-07-17 (control refund 50->130 lands, fault 50->50 lost, both ack {1,Success}; MIST value-delta FIRE; upstream FudanSELab F1 async-refund-sequencing, cancel-refund site mechanism-variant)",
    "teastore-order-meshsever-masked-001": "completion-set Phase-C A5(iii) window (2026-07-16): TeaStoreMeshseverHeadToHead paired FIRE 4/4 under the mesh-sever VS producer (b4/cset/teastore-order-meshsever-run.report.json; ground truth = direct reads, fault order rows ABSENT)",
    "teastore-order-meshsever-control-001": "same window, control leg (4/4 present, no false fire)",
    "teastore-orderitems-meshsever-masked-001": "completion-set Phase-C A5(iii) window: chained child-collection read-back FIRE 4/4 (b4/cset/teastore-orderitems-meshsever-run.report.json; ground truth: fault parents PRESENT w/ items=0)",
    "teastore-orderitems-meshsever-control-001": "same window, control leg (4/4 present w/ items>=1, no false fire)",
    # RECLASSIFIED capture-concordant -> live-run 2026-07-21 (user-directed; the depdown-lesson de-risk
    # of the LAST concordant flag). The MIST read-back oracle was ALREADY run live on this cell in the
    # G3 head-to-head (AccountCreateAgreement: real PairedFaultExecutor+DataIntegrityRuntime); it was
    # only RELOCATED to g3 to avoid double-counting the agreement/comparator claim - the read-back FIRE
    # itself is a live paired-oracle run of this cell's oracle (same membership binding: userId in /account).
    "TT-createaccount-agreement-001": ("G3 head-to-head AGREEMENT anchor - AccountCreateAgreement real MIST "
        "PairedFaultExecutor+DataIntegrityRuntime paired FIRE 5/5 live (verbatim: 'fault run acknowledged X "
        "http 200 body status 1 but X absent from its own read-back 20-poll TIMEOUT_ABSENT; control's X "
        "persisted -> acknowledged-but-lost write', correlatorUnique=true; g3-comparator-tt/runs/"
        "agreement-run2-v105.log + agreement-reps.txt reps 2-5) + 2026-07-20 MySQL business-key re-capture "
        "(b4/cset/RESULT-tt-recapture.md: control present / fault absent). Same membership binding (userId "
        "in GET /account) as this cell. RECLASSIFIED capture-concordant->live-run 2026-07-21 (user-directed; "
        "run originally relocated to g3, verified against primary logs before reclassifying)"),
}


def get(d, *keys, default=None):
    for k in keys:
        if isinstance(d, dict) and k in d:
            d = d[k]
        else:
            return default
    return d


def main():
    rows = []
    for f in sorted(CASES.glob("*.json")):
        c = json.loads(f.read_text(encoding="utf-8"))
        rows.append({
            "case_id": get(c, "case_id", default=f.stem),
            "file": f.name,
            "sut": get(c, "sut", default=get(c, "system", default=None)),
            "label": get(c, "label", default=get(c, "ground_truth", "label")),
            "fault_class": get(c, "fault", "fault_class",
                               default=get(c, "fault_class")),
            "mist_readback_oracle": get(c, "mist_readback_oracle",
                                        default=get(c, "oracle", "mist_readback_oracle")),
            "oracle_mode": get(c, "oracle_mode",
                               default=get(c, "oracle", "oracle_mode")),
            "mist_commit": get(c, "mist_commit",
                               default=get(c, "provenance", "mist_commit")),
            "capture_status": get(c, "capture_status"),
            "readback_shape": get(c, "readback_shape"),
            "trace_visibility": None,
            "ack_content_visibility": None,
            "naive_span_error_oracle": None,
            "mist_trace_shape_oracle": None,
            "adjudication": None,
            "provenance_run": None,
        })
    # search nested if top-level miss (schema variations)
    def deep_find(obj, key):
        if isinstance(obj, dict):
            if key in obj:
                return obj[key]
            for v in obj.values():
                r = deep_find(v, key)
                if r is not None:
                    return r
        elif isinstance(obj, list):
            for v in obj:
                r = deep_find(v, key)
                if r is not None:
                    return r
        return None

    for row in rows:
        c = json.loads((CASES / row["file"]).read_text(encoding="utf-8"))
        for k in ("mist_readback_oracle", "oracle_mode", "mist_commit",
                  "capture_status", "readback_shape", "label", "fault_class",
                  "trace_visibility", "ack_content_visibility",
                  "naive_span_error_oracle", "mist_trace_shape_oracle"):
            if row.get(k) is None:
                row[k] = deep_find(c, k)

    generic = ("case-file capture-of-record notes + the corpus-wide mist_commit pin "
               "(no dedicated oracle run beyond the capture wave)")
    for r in rows:
        cid = r["case_id"]
        if r["mist_readback_oracle"] == "not_applicable":
            r["adjudication"] = ADJUDICATIONS.get(
                cid, {"class": "UNADJUDICATED", "reason": "MISSING - must not ship"})
        else:
            r["provenance_run"] = PROVENANCE.get(cid, generic)

    tally = {}
    adj_tally = {}
    for r in rows:
        v = r["mist_readback_oracle"] or "MISSING"
        tally[v] = tally.get(v, 0) + 1
        if r["adjudication"]:
            k = r["adjudication"]["class"]
            adj_tally[k] = adj_tally.get(k, 0) + 1
    if adj_tally.get("UNADJUDICATED"):
        print("FATAL: unadjudicated not_applicable cases remain", file=sys.stderr)
        return 1
    census = {
        "schema": "mist-column-census/1",
        "generated_by": "build_mist_column_census.py (completion-set wave A5)",
        "note": ("Verdict provenance + every not_applicable adjudication are "
                 "filled by the wave; no silent pending may survive (plan 3.5)."),
        "tally": tally,
        "adjudication_tally": adj_tally,
        "cases": rows,
    }
    OUT.write_text(json.dumps(census, indent=1, ensure_ascii=False) + "\n",
                   encoding="utf-8")
    print(f"wrote {OUT.name}: {len(rows)} cases; tally={tally}; adj={adj_tally}")

    # A7: the E2 trace-visibility census — aggregated FROM the per-case
    # trace_visibility stamps authored at capture time (derivation upstream of
    # any comparator run: no circularity). trace-invisible-by-construction =
    # the E2 table's N-vs-0 row; trace-uninstrumented = NOT_EVALUABLE for
    # trace-consuming arms.
    vis_tally = {}
    missing = []
    for r in rows:
        v = r["trace_visibility"] or "MISSING"
        vis_tally[v] = vis_tally.get(v, 0) + 1
        if v == "MISSING":
            missing.append(r["case_id"])
    if missing:
        print(f"FATAL: cases without trace_visibility: {missing}", file=sys.stderr)
        return 1
    vis = {
        "schema": "e2-visibility-census/1",
        "generated_by": "build_mist_column_census.py (completion-set wave A7)",
        "derivation": ("per-case trace_visibility stamps authored at capture time "
                       "from capture evidence + spec (upstream of every comparator "
                       "run - no circularity); this census only aggregates them"),
        "class_semantics": {
            "error-span-visible": "fault leg carries an error span - the naive span-error class CAN see it",
            "span-presence-visible": "no error span, but a span present/absent delta exists - presence-class arms CAN see it",
            "trace-invisible-by-construction": "fault and control traces are shape-identical - NO trace-consuming arm can see it (the E2 N-vs-0 row)",
            "trace-uninstrumented": ("the capture has no usable trace - NOT_EVALUABLE for "
                                     "trace-consuming arms. TWO SENSES (B-NB-3): by SUT "
                                     "tier (Kieker-only TeaStore / untraced TT legs) or "
                                     "BY CAPTURE on an instrumented SUT (the kafka S1 "
                                     "case: the stochastic protocol exported no per-trial "
                                     "traces, disclosed in-case)"),
        },
        "tally": vis_tally,
        "cases": [{"case_id": r["case_id"], "label": r["label"],
                   "trace_visibility": r["trace_visibility"],
                   "naive_span_error_oracle": r["naive_span_error_oracle"],
                   "mist_trace_shape_oracle": r["mist_trace_shape_oracle"],
                   "mist_readback_oracle": r["mist_readback_oracle"]}
                  for r in rows],
    }
    OUT_VIS.write_text(json.dumps(vis, indent=1, ensure_ascii=False) + "\n",
                       encoding="utf-8")
    print(f"wrote {OUT_VIS.name}: vis_tally={vis_tally}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
