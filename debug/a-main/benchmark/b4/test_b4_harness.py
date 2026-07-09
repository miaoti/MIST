#!/usr/bin/env python3
"""B4 invariant tests (corpus plan §5 rev 2): strip-list leak fixture, absolute-time
rejection, determinism, monotonic transcript. Run: python test_b4_harness.py"""
import json
import tempfile
import unittest
from pathlib import Path

import b4_harness as b4


def _write(tmp, name, content):
    p = Path(tmp) / name
    p.write_text(content, encoding="utf-8")
    return str(p)


# A case file whose FORBIDDEN fields are all populated with tool vocabulary —
# the leak fixture: none of it may reach a rater-facing byte.
LEAKY_CASE = """
id: tt-s1-cancel-demo
title: "MIST oracle FIRE fabricated-ack acked-but-lost triple"
stratum: S1
sut: {name: trainticket}
fault: {mechanism: flag, injection: "runtime-toggle URL", fault_class: acknowledged_lost_write}
label: {value: genuine, provenance: by-injection}
oracle_eval:
  readback: {modality: api-get, expect_with_fault: absent}
  oracle_expectation: {mist_readback_oracle: flag}
negative_control: {present: true}
"""

GOOD_SIDECAR = {
    "sidecar_version": 1,
    "case_id": "tt-s1-cancel-demo",
    "producer": "seed-capture-driver",
    "mist_commit": "7d69de9",
    "sut": {"name": "trainticket", "version_ref": "img-set-1"},
    "records": [
        {"t_rel_ms": 0, "kind": "request", "method": "POST", "path": "/api/v1/orders", "payload": "{\"from\":\"A\"}"},
        {"t_rel_ms": 120, "kind": "response", "status": 201, "body": "{\"status\":1,\"data\":{\"id\":\"o1\"}}"},
        {"t_rel_ms": 5000, "kind": "observation", "probe": "GET /api/v1/orders", "status": 200, "body": "{\"data\":[]}"},
    ],
}


class B4Tests(unittest.TestCase):
    def _render(self, tmp, sidecar=None):
        c = _write(tmp, "case.yaml", LEAKY_CASE)
        s = _write(tmp, "sidecar.json", json.dumps(sidecar or GOOD_SIDECAR))
        return b4.render(c, s, "case-007", str(Path(tmp) / "out"))

    def test_striplist_leak_fixture_output_is_clean(self):
        with tempfile.TemporaryDirectory() as tmp:
            row = self._render(tmp)
            out = (Path(tmp) / "out" / "case-007" / "case.md").read_text(encoding="utf-8")
            ballot = (Path(tmp) / "out" / "case-007" / "ballot.yaml").read_text(encoding="utf-8")
            for banned in b4.BANNED_STRINGS:
                self.assertNotIn(banned, out.lower())
                self.assertNotIn(banned, ballot.lower())
            # true id only in the manifest row, never in rater-facing bytes
            self.assertNotIn("tt-s1-cancel-demo", out)
            self.assertEqual(row["true_id"], "tt-s1-cancel-demo")
            self.assertEqual(row["opaque_id"], "case-007")

    def test_banned_string_in_transcript_fails_loud(self):
        bad = json.loads(json.dumps(GOOD_SIDECAR))
        bad["records"][0]["payload"] = "the MIST oracle fired"
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaises(SystemExit):
                self._render(tmp, bad)

    def test_absolute_time_key_rejected(self):
        bad = json.loads(json.dumps(GOOD_SIDECAR))
        bad["generatedAtEpochMs"] = 123
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaises(SystemExit):
                self._render(tmp, bad)

    def test_non_monotonic_transcript_rejected(self):
        bad = json.loads(json.dumps(GOOD_SIDECAR))
        bad["records"][2]["t_rel_ms"] = 10
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaises(SystemExit):
                self._render(tmp, bad)

    def test_deterministic_bytes(self):
        with tempfile.TemporaryDirectory() as t1, tempfile.TemporaryDirectory() as t2:
            r1 = self._render(t1)
            r2 = self._render(t2)
            self.assertEqual(r1["case_md_sha256"], r2["case_md_sha256"])


if __name__ == "__main__":
    unittest.main(verbosity=2)
