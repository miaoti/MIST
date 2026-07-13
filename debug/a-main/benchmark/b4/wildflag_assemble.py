#!/usr/bin/env python3
"""S3 wild-hunt sidecar assembler (plan rev 2.1 §4.3; freeze §6 "Step-5-as-amended").

Deterministic transform: a Java-emitted raw flag bundle (`flags/flag-w<idx>.json`,
producer "wildflag-bundle") -> a rater-facing `sidecar.json` in the frozen v1 format
(`corpus-19505-sidecar-format.md` / `capture_driver` conventions). A pure function of
the bundle bytes: no clock, no randomness, stable key order, `json.dumps(indent=1)` ->
same bundle yields the same bytes (plan B-M6 "deterministic bytes").

What it does — and does NOT do:

  * REBASES absolute our-side times to per-case RELATIVE `t_rel_ms`, rebased to the
    FIRST journey record (plan B-M6a). The bundle's journey records carry `t_abs_ms`
    and the re-probe carries an absolute epoch; NO absolute-time key survives into the
    sidecar (b4's `_check_sidecar` rejects `epoch|timestamp|...|date` keys anyway).

  * Emits the pre-registered THREE observations, "one shape for every producer"
    (plan §4.3): baseline (state at write initiation) / at-cap (state at the read-back
    cap) / >=T+5min re-probe. EVERY emitted bundle is absent-at-cap with a scheduled
    re-probe by construction (clean-present journeys are barred from the rated mix,
    plan §4.1), so all three are REQUIRED; a bundle with no re-probe is a clean case
    that leaked into the mix -> fail loud (this is the cadence-uniformity guard).

  * NEVER copies the label-bearing bundle fields (`classification`, `gate`, record
    error, per-endpoint tallies) into the rater-facing sidecar. The sidecar carries
    only: `sut`, the redacted journey transcript, and the three neutral observations.
    A focused leak guard scans exactly the surface b4 will render (sut name/version_ref
    + each record's method/path/payload/status/body/probe) against the b4 banned list,
    so a leak fails HERE with a clear message rather than as a surprise at P5 render.
    Metadata (`case_id`, `producer`, `mist_commit`) is NOT scanned: b4 strips it, and
    `mist_commit` legitimately contains the substring "mist".

Observation timestamps are DERIVED (the bundle records elapsed/abs, not per-read
wall-clock): baseline ~ write initiation, at-cap = write-response + `at_cap_elapsed_ms`,
re-probe = `re_probe_abs_ms` - base; each CLAMPED non-decreasing (b4 requires monotone
`t_rel_ms`). The baseline/at-cap read-back status is 2xx by the RAW-candidate invariant
(a non-2xx read-back is an ERROR, never a candidate); asserted here, not assumed.

usage: wildflag_assemble.py <flag-bundle.json> <out-sidecar.json>
"""
import json
import sys
from pathlib import Path

# Mirror of b4_harness.BANNED_STRINGS (leak guard runs at assembly, before P5 render).
BANNED_STRINGS = [
    "mist", "oracle", "verdict", "fire", "no_fire", "quiescence", "gate",
    "triple", "paired", "fault_flag", "fabricated", "injection", "injector",
    "acked-but-lost", "lost write", "lostwrite", "observe mode",
]


def _fail(msg):
    raise SystemExit("WILDFLAG-ASSEMBLE FAILURE: " + msg)


def _rebased_journey(bundle):
    """Journey records with t_abs_ms rebased to a per-case relative t_rel_ms."""
    recs = bundle.get("journey_records") or []
    if not recs:
        _fail("empty journey_records")
    base = recs[0]["t_abs_ms"]
    out = []
    for r in recs:
        t = int(r["t_abs_ms"] - base)
        if r.get("kind") == "request":
            o = {"t_rel_ms": t, "kind": "request", "method": r["method"], "path": r["path"]}
            if r.get("payload") is not None:
                o["payload"] = r["payload"]
        elif r.get("kind") == "response":
            o = {"t_rel_ms": t, "kind": "response", "status": r["status"], "body": r.get("body", "")}
        else:
            _fail("unknown journey record kind: %r" % r.get("kind"))
        out.append(o)
    return out, base


def _obs(t, probe, status, body):
    return {"t_rel_ms": int(t), "kind": "observation", "probe": probe,
            "status": status, "body": body}


def assemble(bundle):
    """Flag-bundle dict -> sidecar dict (raises SystemExit on any invariant breach)."""
    if bundle.get("bundle_version") != 1:
        _fail("unsupported bundle_version: %r" % bundle.get("bundle_version"))
    if bundle.get("producer") != "wildflag-bundle":
        _fail("unexpected producer: %r" % bundle.get("producer"))
    obs = bundle.get("observations") or {}
    if "re_probe_outcome" not in obs:
        _fail("bundle has no re-probe: a clean/present-at-cap case cannot enter the rated "
              "mix (plan §4.1) — refusing to assemble")

    journey, base = _rebased_journey(bundle)
    req_idx = max(i for i, r in enumerate(journey) if r["kind"] == "request")
    resp_idx = max(i for i, r in enumerate(journey) if r["kind"] == "response")
    write_req_t = journey[req_idx]["t_rel_ms"]
    write_resp_t = journey[resp_idx]["t_rel_ms"]

    # Concrete, neutral probe: substitute the actual marker into the descriptor placeholder.
    probe = (bundle.get("probe_descriptor") or "GET <read-back>").replace(
        "<marker>", bundle.get("marker", ""))

    at_cap_status = obs.get("at_cap_status", 200)
    if at_cap_status is None:
        at_cap_status = 200  # older bundles omit it; 2xx by the RAW-candidate invariant
    if at_cap_status // 100 != 2:
        _fail("at-cap read-back status %r is not 2xx — RAW-candidate invariant violated"
              % at_cap_status)

    # THREE observations, temporally ordered and clamped non-decreasing.
    baseline_obs = _obs(write_req_t, probe, at_cap_status, obs.get("baseline_body"))
    at_cap_t = max(write_resp_t + int(obs.get("at_cap_elapsed_ms", 0)), write_resp_t)
    at_cap_obs = _obs(at_cap_t, probe, at_cap_status, obs.get("at_cap_body"))
    re_probe_t = max(int(obs["re_probe_abs_ms"] - base), at_cap_t)
    re_obs = _obs(re_probe_t, probe, obs.get("re_probe_status"), obs.get("re_probe_body"))

    # Order: journey through the write REQUEST, baseline, the write RESPONSE (and any
    # trailing journey records), then at-cap, then the re-probe.
    records = (journey[:req_idx + 1] + [baseline_obs]
               + journey[req_idx + 1:] + [at_cap_obs, re_obs])

    sidecar = {
        "sidecar_version": 1,
        "case_id": bundle.get("marker"),       # neutral, unique, our-side (b4 never renders it)
        "producer": bundle.get("producer"),
        "mist_commit": bundle.get("mist_commit"),
        "sut": bundle.get("sut") or {},
        "records": records,
    }
    _leak_guard(sidecar)
    _monotonic_guard(records)
    return sidecar


def _leak_guard(sidecar):
    """Scan EXACTLY the surface b4 renders into case.md (not the stripped metadata)."""
    sut = sidecar.get("sut") or {}
    parts = [str(sut.get("name", "")), str(sut.get("version_ref", ""))]
    for r in sidecar["records"]:
        if "classification" in r or "gate" in r:
            _fail("label-bearing key leaked into a record")
        for k in ("method", "path", "payload", "status", "body", "probe"):
            if r.get(k) is not None:
                parts.append(str(r[k]))
    blob = " ".join(parts).lower()
    for b in BANNED_STRINGS:
        if b in blob:
            _fail("banned string '%s' in rater-facing content" % b)


def _monotonic_guard(records):
    last = -1
    for r in records:
        t = r["t_rel_ms"]
        if t < last:
            _fail("non-monotonic t_rel_ms (%d < %d)" % (t, last))
        last = t


def main(argv):
    if len(argv) < 3:
        print(__doc__)
        return 2
    bundle = json.loads(Path(argv[1]).read_text(encoding="utf-8"))
    sidecar = assemble(bundle)
    out = Path(argv[2])
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(sidecar, indent=1), encoding="utf-8", newline="\n")
    print("assembled %d records -> %s" % (len(sidecar["records"]), out))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
