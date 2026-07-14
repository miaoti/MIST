#!/usr/bin/env python3
"""B4 blind-label harness (corpus plan §5 rev 2, checklist §1.95.1).

Deterministic transform: case.yaml + sidecar.json -> rater-facing case.md +
ballot.yaml, plus a sealed manifest row. Blindness invariants enforced here:

- STRIP-LIST: the transform NEVER READS label/fault/oracle-expectation fields
  (it renders only from an explicit allow-list) and the rendered output is
  scanned against BANNED_STRINGS — any hit fails loudly (leak, not warning).
- Relative times only: a sidecar containing absolute-time keys is rejected.
- No own-clock: nothing in the output derives from time.time()/now().
- Deterministic: same inputs -> same bytes (stable ordering, no randomness;
  the opaque id is an INPUT assigned at assembly, never minted here).
"""
import hashlib
import json
import re
import sys
from pathlib import Path

import yaml

# Vocabulary that must never reach a rater-facing byte (case-insensitive).
# Two classes: (1) tool/label vocabulary; (2) INJECTION-MECHANISM narration —
# added E1+R2 2026-07-14 after the narrative-leak audit found capture sidecars
# narrating the mechanism ("scaled 0", "maintenance toggle", "meshsever",
# "post-VS-teardown", "read-back cap") in probe/body prose that render() emits
# verbatim. This list is a STRICT-ONLY tightening of the leak gate (it never
# loosens behavior); it is necessary-NOT-sufficient (the mandatory manual
# read-through §2.3 is the backstop for prose the enumerated list can't catch).
BANNED_STRINGS = [
    # (1) tool / label vocabulary
    "mist", "oracle", "verdict", "fire", "no_fire", "quiescence", "gate",
    "triple", "paired", "fault_flag", "fabricated", "injection", "injector",
    "acked-but-lost", "lost write", "lostwrite", "observe mode",
    # (2) injection-mechanism narration
    "meshsever", "mesh-sever", "severed", "teardown", "post-vs", "post-drain",
    "envoyfilter", "virtualservice", "scaled", "scale to", "toggle", "drain",
    "maintenance", "read-back cap", "readback cap", "epoch_ms",
]
# Sidecar keys that would smuggle absolute time.
ABSOLUTE_TIME_KEYS = re.compile(r"epoch|timestamp|generatedat|walltime|date", re.I)

ALLOWED_SUT_DOC_BUNDLES = "docs-bundles"  # per-SUT pinned bundle dir name (assembly provides)


def _fail(msg):
    raise SystemExit("B4 LEAK/INVARIANT FAILURE: " + msg)


def _check_sidecar(sidecar):
    if sidecar.get("sidecar_version") != 1:
        _fail("unsupported sidecar_version")
    blob = json.dumps(sidecar)
    for key in re.findall(r'"(\w+)"\s*:', blob):
        if ABSOLUTE_TIME_KEYS.search(key):
            _fail("absolute-time key in sidecar: " + key)
    recs = sidecar.get("records") or []
    if not recs:
        _fail("empty transcript")
    last = -1
    for r in recs:
        t = r.get("t_rel_ms")
        if not isinstance(t, (int, float)) or t < last:
            _fail("t_rel_ms missing or non-monotonic")
        last = t
    return recs


def _scan_banned(text, context):
    low = text.lower()
    for b in BANNED_STRINGS:
        if b in low:
            _fail("banned string '%s' in %s" % (b, context))


def render(case_path, sidecar_path, opaque_id, out_dir, sut_doc_note=None):
    """Render one rater-facing case. Returns the manifest row dict."""
    case = yaml.safe_load(Path(case_path).read_text(encoding="utf-8"))
    sidecar = json.loads(Path(sidecar_path).read_text(encoding="utf-8"))
    recs = _check_sidecar(sidecar)

    # ALLOW-LIST reads only (never touch label/fault/oracle_eval/title/etc.).
    sut = (case.get("sut") or {}).get("name") or (sidecar.get("sut") or {}).get("name")
    version_ref = (sidecar.get("sut") or {}).get("version_ref", "pinned")
    if not sut:
        _fail("sut name missing")

    lines = []
    lines.append("# Case %s" % opaque_id)
    lines.append("")
    lines.append("**System:** %s  (version set: %s)" % (sut, version_ref))
    lines.append("")
    doc_note = sut_doc_note or ("Use ONLY the provided documentation bundle: `%s/%s/`"
                                % (ALLOWED_SUT_DOC_BUNDLES, sut))
    lines.append("**Reference material:** " + doc_note)
    lines.append("")
    lines.append("## What was done (in order)")
    lines.append("")
    for r in recs:
        t = r["t_rel_ms"]
        if r.get("kind") == "request":
            payload = r.get("payload")
            lines.append("- `t+%dms`  **%s %s**" % (t, r.get("method", "?"), r.get("path", "?")))
            if payload:
                lines.append("  - request body: `%s`" % payload)
        elif r.get("kind") == "response":
            lines.append("  - response: HTTP %s — body: `%s`" % (r.get("status"), r.get("body")))
        elif r.get("kind") == "observation":
            lines.append("- `t+%dms`  observed state via `%s`: HTTP %s — `%s`"
                         % (t, r.get("probe", "?"), r.get("status"), r.get("body")))
        else:
            _fail("unknown record kind: %r" % r.get("kind"))
    lines.append("")
    lines.append("## Your task")
    lines.append("")
    lines.append("Using ONLY the reference material above, judge whether the observed outcome is a")
    lines.append("**genuine defect**, **by-design / benign**, or **underspecified** (the intended")
    lines.append("behavior is not derivable from the reference material). Record your answer in")
    lines.append("`ballot.yaml`.")
    case_md = "\n".join(lines) + "\n"

    ballot = (
        "case_id: %s\n"
        "label:            # genuine | benign | underspecified\n"
        "grounding:\n"
        "  citation:       # doc/spec/source location inside the provided bundle\n"
        "  quote_or_ref:   # the clause that grounds the label\n"
        "confidence:       # high | medium | low\n"
        "rationale: |\n"
        "  # 2-4 sentences: what was promised, what was observed, why the label follows\n"
        "time_minutes:     # integer\n" % opaque_id
    )

    _scan_banned(case_md, "case.md")
    _scan_banned(ballot, "ballot.yaml")

    out = Path(out_dir) / opaque_id
    out.mkdir(parents=True, exist_ok=True)
    (out / "case.md").write_text(case_md, encoding="utf-8", newline="\n")
    (out / "ballot.yaml").write_text(ballot, encoding="utf-8", newline="\n")

    return {
        "opaque_id": opaque_id,
        "true_id": case.get("id") or case.get("case_id") or Path(case_path).stem,
        "case_md_sha256": hashlib.sha256(case_md.encode("utf-8")).hexdigest(),
        "producer": sidecar.get("producer"),
        "mist_commit": sidecar.get("mist_commit"),
    }


def main(argv):
    if len(argv) < 4:
        print("usage: b4_harness.py <case.yaml> <sidecar.json> <opaque-id> [out-dir]")
        return 2
    row = render(argv[1], argv[2], argv[3], argv[4] if len(argv) > 4 else "rater-cases")
    print(json.dumps(row, indent=1, sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
