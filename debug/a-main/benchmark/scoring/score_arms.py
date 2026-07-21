#!/usr/bin/env python3
"""A6 (completion-set wave): the single mechanical scoring path for the
benchmark (contribution 1) x tool (contribution 2) integration — Step-8 B-m4.

Inputs:
  - ../cases/*.json                 ground-truth labels (the benchmark)
  - ../e2-visibility-census.json   per-case trace-visibility class (A7)
  - ../mist-column-census.json     the MIST column + provenance (A5)
  - verdicts/<arm>.json            per-arm verdict files:
      {"arm": str, "verdict_source": str,
       "cases": {case_id: "flag"|"no_flag"|"not_evaluable"}}

Output: matched-recall-table.json — per arm x visibility class: flagged/
positives (recall numerators/denominators), negative-side flag counts,
NOT_EVALUABLE buckets, and the trace-invisible-by-construction N-vs-0 row.

Rails (enforced here, not in prose): NOT_EVALUABLE cases leave the
denominator and land in their own bucket; the invisible-by-construction row
is reported as N-vs-0, never folded into a pooled recall; NO pooled headline
recall is emitted at all — per-class cells only (the self-concordance rule:
read-back cells whose verdict provenance is the labeling capture itself must
never be pooled into a headline; MIST cells carry their provenance class so
the RESULT can render them honestly).
"""
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
BENCH = HERE.parent
VERDICT_DIR = HERE / "verdicts"
OUT = HERE / "matched-recall-table.json"

LIVE_PROVENANCE_MARKERS = ("2.75-A", "G1 run-3", "TT-omnibus", "G3 head-to-head",
                           "G3 h2h", "G3 TT natural", "Phase-C A5(iii)",
                           "completion-set Phase-C", "A3 F-corpus live")


def wilson95(k, n):
    # Wilson score interval, z=1.96; None when the denominator is empty.
    if n <= 0:
        return None
    z = 1.96
    ph = k / n
    den = 1 + z * z / n
    ctr = ph + z * z / (2 * n)
    rad = z * ((ph * (1 - ph) / n + z * z / (4 * n * n)) ** 0.5)
    return [round((ctr - rad) / den, 4), round((ctr + rad) / den, 4)]


def load_cases():
    cases = {}
    for f in sorted((BENCH / "cases").glob("*.json")):
        c = json.loads(f.read_text(encoding="utf-8"))
        cid = c.get("case_id", f.stem)
        label = c.get("label")
        if label is None:  # nested schemas
            def deep(o, k):
                if isinstance(o, dict):
                    if k in o:
                        return o[k]
                    for v in o.values():
                        r = deep(v, k)
                        if r is not None:
                            return r
                elif isinstance(o, list):
                    for v in o:
                        r = deep(v, k)
                        if r is not None:
                            return r
                return None
            label = deep(c, "label")
        cases[cid] = label
    return cases


def main():
    labels = load_cases()
    vis = json.loads((BENCH / "e2-visibility-census.json").read_text(encoding="utf-8"))
    visclass = {r["case_id"]: r["trace_visibility"] for r in vis["cases"]}
    mist = json.loads((BENCH / "mist-column-census.json").read_text(encoding="utf-8"))

    # The MIST column is derived from the A5 census, through the same path
    # as every comparator arm.
    mist_cases = {}
    mist_prov = {}
    for r in mist["cases"]:
        v = r["mist_readback_oracle"]
        cid = r["case_id"]
        if v in ("flag", "no_flag"):
            mist_cases[cid] = v
            prov = r.get("provenance_run") or ""
            # A manual/G0 record whose prose NAMES a live run only to disclose it was
            # NOT_EVALUABLE (TT-adminbasic: "G1 run-3 ... NOT_EVALUABLE; the flag rests
            # on the manual G0 record") must NOT be auto-swept into live-run by that
            # substring. Manual records are their own class (review final3-2 fix).
            if "manual-G0" in prov or "manual G0" in prov:
                mist_prov[cid] = "manual-record"
            elif any(m in prov for m in LIVE_PROVENANCE_MARKERS):
                mist_prov[cid] = "live-run"
            else:
                mist_prov[cid] = "capture-concordant"
        else:
            mist_cases[cid] = "not_evaluable"
            mist_prov[cid] = (r.get("adjudication") or {}).get("class", "not_applicable")
    arms = {"mist_readback": {
        "arm": "mist_readback",
        "verdict_source": "A5 mist-column-census (per-case provenance pointers)",
        "cases": mist_cases,
        "provenance_class": mist_prov,
    }}

    if VERDICT_DIR.is_dir():
        for f in sorted(VERDICT_DIR.glob("*.json")):
            a = json.loads(f.read_text(encoding="utf-8"))
            if "arm" not in a:
                continue  # side artifacts (run detail etc.) are not verdict files
            arms[a["arm"]] = a

    classes = sorted(set(visclass.values()))
    table = {}
    for name, a in arms.items():
        cells = {}
        for cls in classes:
            cls_ids = [cid for cid in labels if visclass.get(cid) == cls]
            pos = [c for c in cls_ids if labels[c] == "positive"]
            neg = [c for c in cls_ids if labels[c] != "positive"]
            av = a["cases"]
            ne = [c for c in cls_ids if av.get(c, "not_evaluable") == "not_evaluable"]
            flagged_pos = [c for c in pos if av.get(c) == "flag"]
            flagged_neg = [c for c in neg if av.get(c) == "flag"]
            eval_pos = [c for c in pos if av.get(c, "not_evaluable") != "not_evaluable"]
            cells[cls] = {
                "positives": len(pos),
                "evaluable_positives": len(eval_pos),
                "flagged_positives": len(flagged_pos),
                "negatives": len(neg),
                "flagged_negatives": len(flagged_neg),
                "not_evaluable": len(ne),
                "recall_wilson95": wilson95(len(flagged_pos), len(eval_pos)),
                "fp_rate_wilson95": wilson95(len(flagged_neg),
                                             len([c for c in neg if av.get(c, "not_evaluable") != "not_evaluable"])),
                "flagged_positive_ids": flagged_pos,
                "flagged_negative_ids": flagged_neg,
                "not_evaluable_ids": ne,
            }
        table[name] = {
            "verdict_source": a.get("verdict_source", "UNSPECIFIED"),
            "cells": cells,
        }
        if "provenance_class" in a:
            table[name]["provenance_class"] = a["provenance_class"]

    inv = "trace-invisible-by-construction"
    inv_pos = [c for c in labels if visclass.get(c) == inv and labels[c] == "positive"]
    # provenance split COMPUTED from the census classification (the static "live-run (8)"
    # prose went stale when the corpus moved 33->27; expand-review C caught it)
    split = {}
    for cid, v in mist_cases.items():
        if v == "flag":
            split[mist_prov[cid]] = split.get(mist_prov[cid], 0) + 1
    split_txt = " + ".join(f"{split.get(k, 0)} {k}" for k in
                           ("live-run", "manual-record", "capture-concordant"))
    # positives scope COMPUTED: foreground the principled not-evaluable positives so the
    # headline recall always reads "of the evaluable", never as a rigged perfect score
    ne_pos = sorted(c for c in labels if labels[c] == "positive"
                    and mist_cases.get(c, "not_evaluable") == "not_evaluable")
    npos = sum(1 for c in labels if labels[c] == "positive")
    out = {
        "schema": "matched-recall-table/1",
        "generated_by": "scoring/score_arms.py (completion-set wave A6)",
        "rails": [
            "per-visibility-class cells only - NO pooled headline recall is emitted",
            "NOT_EVALUABLE leaves the denominator and lands in its own bucket",
            f"POSITIVES SCOPE (foregrounded): {npos} positives total; MIST evaluable = {npos - len(ne_pos)}; the {len(ne_pos)} principled not-evaluable positives are {ne_pos} - any 'x/x' MIST recall means x of the EVALUABLE positives and MUST be stated with this scope",
            f"the {inv} row is the N-vs-0 row: {len(inv_pos)} positives no trace-consuming arm can see by construction",
            f"MIST flag cells carry provenance_class - {split_txt} (manual-record = TT-adminbasic: the live G1 run-3 was NOT_EVALUABLE, the flag rests on a disclosed manual-G0 record; capture-concordant flags rest on the capture-of-record, not a separate live MIST oracle run); neither the manual nor the concordant cells may be pooled into a live headline (self-concordance rule)",
            "Wilson 95% intervals accompany every cell (recall over evaluable positives; FP rate over evaluable negatives) - tiny denominators stay visibly tiny",
            "FP denominator convention: the headline FP figure is 0/13 on the MEASURED no_flag denominator; the corpus-level statement is 0 false flags among all 15 negatives (2 negatives are principled-n_a and cannot flag) - state both, pool neither",
            "matched-recall framing only - never 'discrimination' (the natural-discriminator question was S3's, closed 0/1514)",
            "OPERATING-POINT framing (2026-07-21 expand-review mandate): eanom_control_differ is a first-class arm - never claim a trace differ 'cannot see' masked loss (E-ANOM catches 5/6 traced positives); the defended claim is the operating point (black-box, no instrumentation, no paired control leg, single-execution, durable-state not trace-proxy) plus the 0-FP profile, NOT unique detection",
        ],
        "n_vs_0_row": {"class": inv, "positives": len(inv_pos), "positive_ids": inv_pos},
        "visibility_tally": vis["tally"],
        "arms": table,
    }
    OUT.write_text(json.dumps(out, indent=1, ensure_ascii=False) + "\n",
                   encoding="utf-8")
    print(f"wrote {OUT.name}: arms={sorted(table)}; classes={classes}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
