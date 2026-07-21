#!/usr/bin/env python3
"""E-ANOM (plan rev 2, 3-cold ACCEPT-WITH-CHANGES): a REAL unsupervised trace-anomaly
detector, executed offline over the corpus's paired traces, to replace the
`traceanomaly` arm's argued `not_evaluable 33/33` with a MEASURED result.
[2026-07-21: corpus-of-record is now 27; the measured verdicts ship as the first-class
arm `eanom_control_differ` via build_eanom_arm.py — see RESULT-eanom.md's superseding note.]

Paradigm (the honest, defensible core of the trace-anomaly family): LEARN-NORMAL-FROM-
CONTROL, DETECT-DEVIATION. For each case that has BOTH a control-leg and a fault-leg
Jaeger trace, build a structural "normal profile" from the CONTROL leg and flag the FAULT
leg iff it structurally DEVIATES. This is genuinely distinct from `naive_span_error` (flags
any error span, no baseline) and `mist_trace_shape` (MIST's own fixed hidden-downstream
invariant) — it is the only arm that learns a per-endpoint normal and scores deviation.

Pre-registered flag rule (FIXED here; NOT tuned; the statistical latency/count rules from
the plan v1 are DROPPED as INERT — control legs carry n=1-2 traces so z-scores are
undefined, exactly the population defect that n_e'd the learned VAE traceanomaly; this is
disclosed, and the arm is presented as an a-fortiori STRUCTURAL LOWER BOUND on the
trace-anomaly family, not a replacement for a trained VAE):
  FLAG the fault leg iff ANY of:
   (a) a (service|operation) or a parent->child service EDGE present in the fault leg but
       ABSENT from the control profile   [structural novelty]
   (b) a control-profile EDGE that is MISSING from the fault leg                [structural drop]
   (c) an error-status span (http>=400 OR otel=ERROR) in the fault leg whose (service,op)
       is NOT already an error in the control profile          [novel error, deviation-scored]

Honest expected result (audited from the captures, per review-2): the masked-loss positives
leave the fault trace structurally ~identical to control -> 0 flags; the bookinfo benign
503-degradation deviates -> a FALSE POSITIVE (the same designed-degradation FP mist_trace_shape
hits). Cases without BOTH legs -> not_evaluable (the trace-uninstrumented + sidecar-only
captures, disclosed - same boundary as the learned traceanomaly).
"""
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent.parent.parent
CASES = HERE.parent / "cases"
OUT = HERE / "verdicts" / "traceanomaly.json"
DETAIL = HERE / "verdicts" / "traceanomaly.detail.json"


def load_spans(rel):
    p = ROOT / rel
    if not p.is_file():
        return None
    try:
        d = json.loads(p.read_text(encoding="utf-8"))
    except Exception:
        return None
    traces = d.get("data") if isinstance(d, dict) else None
    if traces is None:
        traces = [d] if isinstance(d, dict) and "spans" in d else []
    spans = []
    for tr in traces:
        procs = tr.get("processes", {}) if isinstance(tr, dict) else {}
        for s in tr.get("spans", []) or []:
            svc = ""
            pid = s.get("processID")
            if pid and pid in procs:
                svc = procs[pid].get("serviceName", "")
            tags = {t.get("key"): t.get("value") for t in s.get("tags", []) or []}
            http = tags.get("http.status_code")
            try:
                http = int(http)
            except (TypeError, ValueError):
                http = 0
            spans.append({
                "spanID": s.get("spanID"),
                "svc": svc or "unknown",
                "op": s.get("operationName", ""),
                "parent": next((r.get("spanID") for r in s.get("references", [])
                                if r.get("refType") == "CHILD_OF"), None),
                "http": http,
                "otel": str(tags.get("otel.status_code", "")),
            })
    return spans


def profile(spans):
    by_id = {s["spanID"]: s for s in spans}
    svcops, edges, errs = set(), set(), set()
    for s in spans:
        svcops.add((s["svc"], s["op"]))
        if s["http"] >= 400 or s["otel"].upper() == "ERROR":
            errs.add((s["svc"], s["op"]))
        p = by_id.get(s["parent"])
        if p:
            edges.add((p["svc"], s["svc"]))
    return svcops, edges, errs


def detect(control, fault):
    """Return (flag: bool, reasons: list). control/fault are span lists."""
    c_svcops, c_edges, c_errs = profile(control)
    f_svcops, f_edges, f_errs = profile(fault)
    reasons = []
    novel_ops = f_svcops - c_svcops
    if novel_ops:
        reasons.append(f"novel svc/op: {sorted(novel_ops)[:3]}")
    novel_edges = f_edges - c_edges
    if novel_edges:
        reasons.append(f"novel edge: {sorted(novel_edges)[:3]}")
    missing_edges = c_edges - f_edges
    if missing_edges:
        reasons.append(f"missing edge: {sorted(missing_edges)[:3]}")
    novel_errs = f_errs - c_errs
    if novel_errs:
        reasons.append(f"novel error span: {sorted(novel_errs)[:3]}")
    return (len(reasons) > 0), reasons


def main():
    verdicts, detail = {}, {}
    for f in sorted(CASES.glob("*.json")):
        c = json.loads(f.read_text(encoding="utf-8"))
        cid = c.get("case_id", f.stem)
        prov = c.get("provenance") or {}
        ctrl = load_spans(prov.get("control_trace")) if prov.get("control_trace") else None
        flt = load_spans(prov.get("fault_trace")) if prov.get("fault_trace") else None
        if not ctrl or not flt:
            verdicts[cid] = "not_evaluable"
            continue
        flag, reasons = detect(ctrl, flt)
        verdicts[cid] = "flag" if flag else "no_flag"
        detail[cid] = {"label": c["ground_truth"]["label"],
                       "control_spans": len(ctrl), "fault_spans": len(flt),
                       "reasons": reasons}
        print(f"{cid[:44]:44s} {c['ground_truth']['label']:8s} -> {verdicts[cid]}"
              + (f"  ({'; '.join(reasons)})" if reasons else ""))

    OUT.write_text(json.dumps({
        "arm": "traceanomaly",
        "verdict_source": ("E-ANOM (run_anomaly_arm.py): unsupervised learn-normal-from-control / "
                           "detect-deviation trace-anomaly detector, executed offline over the paired "
                           "control+fault Jaeger traces. STRUCTURAL a-fortiori LOWER BOUND on the "
                           "trace-anomaly family (latency/count z-rules dropped as inert at n=1-2 control "
                           "traces - the same population defect that made a learned VAE unrunnable here; "
                           "disclosed). flag iff structural novelty/drop/novel-error vs the control profile. "
                           "Distinct from naive_span_error (no baseline) and mist_trace_shape (fixed "
                           "invariant). REPLACES the prior all-not_evaluable stub with MEASURED verdicts."),
        "cases": verdicts}, indent=2) + "\n", encoding="utf-8")
    DETAIL.write_text(json.dumps(detail, indent=2) + "\n", encoding="utf-8")
    import collections
    print("tally:", dict(collections.Counter(verdicts.values())))
    pos_flag = [k for k, v in verdicts.items() if v == "flag" and detail.get(k, {}).get("label") == "positive"]
    neg_flag = [k for k, v in verdicts.items() if v == "flag" and detail.get(k, {}).get("label") != "positive"]
    ev_pos = [k for k, v in verdicts.items() if v in ("flag", "no_flag") and detail.get(k, {}).get("label") == "positive"]
    print(f"trace-evaluable positives flagged: {len(pos_flag)}/{len(ev_pos)}")
    print(f"negatives flagged (FP): {neg_flag}")


if __name__ == "__main__":
    main()
