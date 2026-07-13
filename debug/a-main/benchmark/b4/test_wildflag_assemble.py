#!/usr/bin/env python3
"""S3 wild-hunt assembler tests (plan rev 2.1 §4 P0 gate): sidecar shape + t_rel rebase,
round-trip through the REAL b4_harness.render (no leak, three observations rendered),
observation-record cadence uniformity across strata (plan §4 line 203), and the
fail-loud guards (missing re-probe, banned version_ref, non-2xx at-cap, label strip).
Run: python test_wildflag_assemble.py"""
import json
import re
import tempfile
import unittest
from pathlib import Path

import b4_harness as b4
import wildflag_assemble as wf


def bundle(marker="corpus-w3-ab12cd34ef56", reprobe_outcome="ABSENT", reprobe_body="[]",
           version_ref="opentelemetry-demo app 2.2.0 / chart 0.40.9", at_cap_status=200):
    """A flag bundle mirroring WildHuntEngine.writeFlagBundle (OTel-shaped, SQL read-back)."""
    payload = ('{"userId":"u","address":{"streetAddress":"%s","state":"CA"},'
               '"creditCard":{"creditCardNumber":"4432-8015-6152-0454"}}' % marker)
    return {
        "bundle_version": 1,
        "producer": "wildflag-bundle",
        "mist_commit": "abc1234",
        "window": "window-oteldemo",
        "sut": {"name": "oteldemo", "version_ref": version_ref},
        "write_index": 3,
        "endpoint": "/api/checkout?currencyCode=USD",
        "marker": marker,
        "classification": "raw-confirmed",  # label-bearing — MUST NOT reach the sidecar
        "probe_descriptor": ("SQL SELECT street_address FROM accounting.shipping "
                             "WHERE street_address='<marker>'"),
        "journey_records": [
            {"t_abs_ms": 1000, "kind": "request", "method": "GET",
             "path": "/api/products/0PUK6V6EV0"},
            {"t_abs_ms": 1050, "kind": "response", "status": 200, "body": "<product json elided>"},
            {"t_abs_ms": 1060, "kind": "request", "method": "POST",
             "path": "/api/cart?currencyCode=USD",
             "payload": '{"item":{"productId":"0PUK6V6EV0","quantity":1},"userId":"u"}'},
            {"t_abs_ms": 1090, "kind": "response", "status": 200,
             "body": '{"userId":"u","items":[{"productId":"0PUK6V6EV0","quantity":1}]}'},
            {"t_abs_ms": 1100, "kind": "request", "method": "POST",
             "path": "/api/checkout?currencyCode=USD", "payload": payload},
            {"t_abs_ms": 1140, "kind": "response", "status": 200,
             "body": '{"orderId":"7c23-11f1","shippingTrackingId":"630c9d4f"}'},
        ],
        "observations": {
            "baseline_body": "[]",
            "at_cap_body": "[]",
            "at_cap_polls": 12,
            "at_cap_elapsed_ms": 900,
            "at_cap_status": at_cap_status,
            "re_probe_outcome": reprobe_outcome,
            "re_probe_status": 200,
            "re_probe_body": reprobe_body,
            "re_probe_abs_ms": 1140 + 300000,  # ~T+5min after the write response
        },
    }


class AssembleTests(unittest.TestCase):

    def test_shape_rebase_and_three_observations(self):
        s = wf.assemble(bundle())
        self.assertEqual(s["sidecar_version"], 1)
        self.assertEqual(s["case_id"], "corpus-w3-ab12cd34ef56")     # neutral, our-side
        self.assertEqual(s["producer"], "wildflag-bundle")
        self.assertEqual(s["mist_commit"], "abc1234")
        kinds = [r["kind"] for r in s["records"]]
        # journey through the write request, baseline, write response, at-cap, re-probe.
        self.assertEqual(kinds, ["request", "response", "request", "response", "request",
                                 "observation", "response", "observation", "observation"])
        self.assertEqual(s["records"][0]["t_rel_ms"], 0)             # rebased to the first record
        obs = [r for r in s["records"] if r["kind"] == "observation"]
        self.assertEqual(len(obs), 3)
        self.assertTrue(all("accounting.shipping" in o["probe"] for o in obs))
        self.assertIn("corpus-w3-ab12cd34ef56", obs[0]["probe"])     # concrete marker substituted
        # re-probe observation lands ~T+5min after the write response, monotone.
        self.assertGreaterEqual(obs[2]["t_rel_ms"], obs[1]["t_rel_ms"])
        self.assertGreaterEqual(obs[2]["t_rel_ms"], 300000)

    def test_no_label_or_absolute_time_leaks_into_sidecar(self):
        blob = json.dumps(wf.assemble(bundle()))
        self.assertNotIn("classification", blob)
        self.assertNotIn("raw-confirmed", blob)
        self.assertNotIn("t_abs_ms", blob)
        # no absolute-time key (mirror b4's ABSOLUTE_TIME_KEYS)
        for key in re.findall(r'"(\w+)"\s*:', blob):
            self.assertIsNone(re.search(r"epoch|timestamp|generatedat|walltime|date", key, re.I),
                              "absolute-time key leaked: " + key)

    def test_roundtrip_through_real_b4_render_no_leak(self):
        with tempfile.TemporaryDirectory() as tmp:
            side = wf.assemble(bundle())
            sp = Path(tmp) / "sidecar.json"
            sp.write_text(json.dumps(side, indent=1), encoding="utf-8")
            cp = Path(tmp) / "case.yaml"
            cp.write_text("id: corpus-w3-ab12cd34ef56\nsut: {name: oteldemo}\n", encoding="utf-8")
            row = b4.render(str(cp), str(sp), "corpus-case-001", str(Path(tmp) / "out"))
            md = (Path(tmp) / "out" / "corpus-case-001" / "case.md").read_text(encoding="utf-8")
            for banned in b4.BANNED_STRINGS:
                self.assertNotIn(banned, md.lower())
            self.assertEqual(md.count("observed state via"), 3)      # all three observations render
            self.assertNotIn("corpus-w3-ab12cd34ef56", "# Case corpus-case-001")  # opaque id shown
            self.assertEqual(row["opaque_id"], "corpus-case-001")
            self.assertEqual(row["producer"], "wildflag-bundle")

    def test_cadence_uniformity_across_strata(self):
        # An S3-CONFIRMED case (absent at re-probe) and a calibration DELAYED benign case
        # (present at re-probe) must yield IDENTICAL record-kind + observation-slot shapes;
        # only the observation BODIES differ (that is the judgment signal, not a format tell).
        confirmed = wf.assemble(bundle(reprobe_outcome="ABSENT", reprobe_body="[]"))
        delayed = wf.assemble(bundle(marker="corpus-w7-9f8e7d6c5b4a",
                                     reprobe_outcome="PRESENT",
                                     reprobe_body='[{"street_address":"corpus-w7-9f8e7d6c5b4a"}]'))
        self.assertEqual([r["kind"] for r in confirmed["records"]],
                         [r["kind"] for r in delayed["records"]])
        self.assertEqual([("probe" in r) for r in confirmed["records"]],
                         [("probe" in r) for r in delayed["records"]])
        # the re-probe body is what differs (absent vs recovered).
        self.assertNotEqual(confirmed["records"][-1]["body"], delayed["records"][-1]["body"])

    def test_missing_reprobe_fails_loud(self):
        b = bundle()
        del b["observations"]["re_probe_outcome"]
        with self.assertRaises(SystemExit):
            wf.assemble(b)

    def test_banned_version_ref_fails_loud(self):
        with self.assertRaises(SystemExit):
            wf.assemble(bundle(version_ref="opentelemetry-demo 2.2.0 @ kind 'mist' ns otel-demo"))

    def test_non_2xx_at_cap_fails_loud(self):
        with self.assertRaises(SystemExit):
            wf.assemble(bundle(at_cap_status=503))

    def test_deterministic_bytes(self):
        a = json.dumps(wf.assemble(bundle()), indent=1)
        b = json.dumps(wf.assemble(bundle()), indent=1)
        self.assertEqual(a, b)


if __name__ == "__main__":
    unittest.main(verbosity=2)
