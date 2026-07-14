#!/usr/bin/env python3
"""R2 pre-seal manifest builder (committed per post-review B-8/C-4 so the
manifest is reproducible from tree). Renders every rater-sidecar through
b4_harness with a BLIND opaque id (case-NNN by sorted order), checks
render-determinism (two renders, byte-identical case.md), and writes
../b4/MANIFEST-r2.json — OUTSIDE rater-sidecars/ so a naive
glob('rater-sidecars/*.json') can never pick it up (it is ADMIN-side: it
carries true case_ids, labels and the label-perfect readback_shape).

corpus_content_hash formula: sha256 of the concatenation of the SORTED
per-sidecar sha256 hex digests (ascii).

rateability (review B-5 / freeze conventions) — a pre-seal RECORD, final call
at the gated Step-5 seal:
  ok                                  rendered evidence carries the decision signal
  tt-collection-truncation-gated      TT global-collection read-back: the acting
                                      record falls beyond the rendered window;
                                      per-endpoint acting-record rendering is
                                      gated Step-5 rater-cut work (AUDIT finding 4)
  trace-required-not-blind-rateable   the only discriminator is a trace span the
                                      harness never renders (sockshop swallowed-
                                      enqueue); default = exclude from the blind
                                      set unless the seal ships disclosed
                                      white-box evidence
  async-no-bound-calibration-ineligible  the standing freeze stamp on every OTel
                                      async positive (row 309(5))"""
import hashlib, json, os, tempfile

import b4_harness as b4

_HERE = os.path.dirname(os.path.abspath(__file__))
RS = os.path.join(_HERE, "rater-sidecars")
CASES = os.path.normpath(os.path.join(_HERE, "..", "cases"))
OUT = os.path.join(_HERE, "MANIFEST-r2.json")

RATEABILITY = {
    "TT-adminbasic-contacts-control-001": "tt-collection-truncation-gated",
    "TT-adminbasic-contacts-lostwrite-001": "tt-collection-truncation-gated",
    "TT-adminroute-control-001": "tt-collection-truncation-gated",
    "TT-adminroute-lostwrite-001": "tt-collection-truncation-gated",
    "TT-cancel-refund-clean-001": "tt-collection-truncation-gated",
    "TT-cancel-refund-fabricatedack-001": "tt-collection-truncation-gated",
    "TT-cancel-refund-natural-001": "tt-collection-truncation-gated",
    "TT-createaccount-agreement-001": "tt-collection-truncation-gated",
    "TT-createaccount-clean-001": "tt-collection-truncation-gated",
    "sockshop-shipping-swallowed-enqueue-001": "trace-required-not-blind-rateable",
    "oteldemo-checkout-lost-001": "async-no-bound-calibration-ineligible",
}


def caseinfo(cid):
    d = json.load(open(os.path.join(CASES, cid + ".json"), encoding="utf-8"))
    gt = d.get("ground_truth") or {}
    return dict(stratum=d.get("stratum"), label=gt.get("label"),
                shape=d.get("readback_shape"), sut=(d.get("sut") or {}).get("name"))


def main():
    rows = []
    sidecars = sorted(f for f in os.listdir(RS) if f.endswith(".json"))
    for i, name in enumerate(sidecars):
        cid = name[:-5]
        sc = os.path.join(RS, name)
        txt = open(sc, encoding="utf-8").read()
        doc = json.loads(txt)
        nobs = sum(1 for r in doc["records"] if r.get("kind") == "observation")
        with tempfile.TemporaryDirectory() as t1, tempfile.TemporaryDirectory() as t2:
            r1 = b4.render(os.path.join(CASES, cid + ".json"), sc, "case-%03d" % i, t1)
            r2 = b4.render(os.path.join(CASES, cid + ".json"), sc, "case-%03d" % i, t2)
            det = r1["case_md_sha256"] == r2["case_md_sha256"]
        ci = caseinfo(cid)
        rows.append(dict(case_id=cid, sut=ci["sut"], stratum=ci["stratum"],
                         label=ci["label"], shape=ci["shape"],
                         records=len(doc["records"]), observations=nobs,
                         rateability=RATEABILITY.get(cid, "ok"),
                         sidecar_sha256=hashlib.sha256(txt.encode()).hexdigest(),
                         case_md_sha256=r1["case_md_sha256"], deterministic=det))
    manifest = dict(
        schema="r2-preseal-manifest/2",
        note=("ADMIN-side pre-seal manifest (carries labels; never ship next to "
              "renders). corpus_content_hash = sha256(concat(sorted per-sidecar "
              "sha256 hex digests)). rateability = pre-seal record, final at the "
              "gated Step-5 seal."),
        n=len(rows),
        corpus_content_hash=hashlib.sha256(
            "".join(sorted(r["sidecar_sha256"] for r in rows)).encode()).hexdigest(),
        deterministic_all=all(r["deterministic"] for r in rows),
        rows=rows)
    with open(OUT, "w", encoding="utf-8", newline="\n") as f:
        json.dump(manifest, f, indent=1)
        f.write("\n")
    print("wrote %s  n=%d deterministic_all=%s" % (OUT, len(rows), manifest["deterministic_all"]))
    print("corpus_content_hash =", manifest["corpus_content_hash"])
    from collections import Counter
    print("rateability:", dict(Counter(r["rateability"] for r in rows)))
    return manifest


if __name__ == "__main__":
    main()
