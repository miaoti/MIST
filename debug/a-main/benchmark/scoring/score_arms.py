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
                           "G3 h2h", "G3 TT natural")


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
            mist_prov[cid] = ("live-run" if any(m in prov for m in LIVE_PROVENANCE_MARKERS)
                              else "capture-concordant")
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
    out = {
        "schema": "matched-recall-table/1",
        "generated_by": "scoring/score_arms.py (completion-set wave A6)",
        "rails": [
            "per-visibility-class cells only - NO pooled headline recall is emitted",
            "NOT_EVALUABLE leaves the denominator and lands in its own bucket",
            f"the {inv} row is the N-vs-0 row: {len(inv_pos)} positives no trace-consuming arm can see by construction",
            "MIST cells carry provenance_class (live-run vs capture-concordant); capture-concordant cells must never be pooled into a headline (self-concordance rule)",
            "matched-recall framing only - never 'discrimination' (the natural-discriminator question was S3's, closed 0/1514)",
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
