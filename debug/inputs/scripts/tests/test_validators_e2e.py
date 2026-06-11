"""End-to-end smoke tests on tiny synthetic fixtures.

Each test creates a temporary inputs.csv / provenance.csv / jaeger.csv,
runs the validator's main(), and asserts the produced summary JSON contains
the expected metric value. This catches join logic, file I/O, and CSV
round-trip bugs.
"""
from __future__ import annotations

import csv
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import validate_d4  # noqa: E402
import validate_d5  # noqa: E402
import validate_d6  # noqa: E402


# --- Helpers -------------------------------------------------------------

def write_inputs(path: Path, rows: list[dict]) -> None:
    cols = ["run_id", "scenario", "test_method", "test_kind", "step_idx",
            "http_method", "path", "parameter", "location", "value"]
    with path.open("w", encoding="utf-8", newline="") as fh:
        w = csv.DictWriter(fh, fieldnames=cols, quoting=csv.QUOTE_MINIMAL)
        w.writeheader()
        for r in rows:
            w.writerow({k: r.get(k, "") for k in cols})


def write_provenance(path: Path, rows: list[tuple[str, str, str]]) -> None:
    with path.open("w", encoding="utf-8", newline="") as fh:
        w = csv.writer(fh, quoting=csv.QUOTE_MINIMAL)
        w.writerow(["parameter", "value", "provenance"])
        for r in rows:
            w.writerow(r)


def write_jaeger(path: Path, values: list[str]) -> None:
    with path.open("w", encoding="utf-8", newline="") as fh:
        w = csv.writer(fh, quoting=csv.QUOTE_MINIMAL)
        w.writerow(["trace_id", "span_id", "service", "operation", "source_field", "value"])
        for v in values:
            w.writerow(["t1", "s1", "svc", "op", "tag.x", v])


def base_row(**kw):
    """Defaults that mark a row as a positive variant of a 1-step test."""
    out = {
        "run_id": "R1",
        "scenario": "Flow_Scenario_1",
        "test_method": "test_positive_flow_S1_v1",
        "test_kind": "positive",
        "step_idx": "1",
        "http_method": "GET",
        "path": "/api/v1/orderservice/orders",
        "parameter": "orderId",
        "location": "path",
        "value": "X",
    }
    out.update(kw)
    return out


# --- D4 SFHR -------------------------------------------------------------

def test_d4_sfhr_full_smart_fetch(tmp_path):
    inputs = tmp_path / "inputs.csv"
    prov = tmp_path / "prov.csv"
    write_inputs(inputs, [
        base_row(parameter="orderId", value="X"),
        base_row(parameter="accountId", value="Y"),
    ])
    write_provenance(prov, [
        ("orderId", "X", "SMART_FETCH"),
        ("accountId", "Y", "SMART_FETCH"),
    ])
    out_dir = tmp_path / "out"
    rc = validate_d4.main([
        "--inputs", str(inputs),
        "--provenance", str(prov),
        "--out-dir", str(out_dir),
    ])
    assert rc == 0
    summary = json.loads((out_dir / "d4_summary.json").read_text())
    assert summary["sfhr_conservative"] == 1.0
    assert summary["sfhr_upper"] == 1.0
    assert summary["id_typed_inputs"] == 2


def test_d4_sfhr_partial(tmp_path):
    inputs = tmp_path / "inputs.csv"
    prov = tmp_path / "prov.csv"
    write_inputs(inputs, [
        base_row(parameter="orderId", value="X"),
        base_row(parameter="orderId", value="Y"),
        base_row(parameter="orderId", value="Z"),
        base_row(parameter="orderId", value="W"),
    ])
    write_provenance(prov, [
        ("orderId", "X", "SMART_FETCH"),
        ("orderId", "Y", "LLM"),
        ("orderId", "Z", "SHARED_POOL_DRAW"),
        # W is unknown
    ])
    out_dir = tmp_path / "out"
    validate_d4.main(["--inputs", str(inputs), "--provenance", str(prov),
                      "--out-dir", str(out_dir)])
    s = json.loads((out_dir / "d4_summary.json").read_text())
    # SFHR conservative: only X is smart-fetch → 1/4
    assert s["sfhr_conservative"] == 0.25
    # SFHR upper: X (smart) + Z (pool) → 2/4
    assert s["sfhr_upper"] == 0.5
    assert s["by_classification"]["smart_fetch"] == 1
    assert s["by_classification"]["shared_pool_draw"] == 1
    assert s["by_classification"]["llm"] == 1
    assert s["by_classification"]["unknown"] == 1


def test_d4_skips_non_id_parameters(tmp_path):
    """Non-ID-like parameters (e.g. boughtDateEnd) must not affect SFHR."""
    inputs = tmp_path / "inputs.csv"
    prov = tmp_path / "prov.csv"
    write_inputs(inputs, [
        base_row(parameter="boughtDateEnd", value="2024-01-01"),
        base_row(parameter="orderId", value="X"),
    ])
    write_provenance(prov, [("orderId", "X", "SMART_FETCH")])
    out_dir = tmp_path / "out"
    validate_d4.main(["--inputs", str(inputs), "--provenance", str(prov),
                      "--out-dir", str(out_dir)])
    s = json.loads((out_dir / "d4_summary.json").read_text())
    assert s["id_typed_inputs"] == 1  # only orderId
    assert s["sfhr_conservative"] == 1.0


def test_d4_skips_negatives_by_default(tmp_path):
    inputs = tmp_path / "inputs.csv"
    prov = tmp_path / "prov.csv"
    write_inputs(inputs, [
        base_row(parameter="orderId", value="X", test_kind="negative"),
        base_row(parameter="orderId", value="Y"),
    ])
    write_provenance(prov, [("orderId", "Y", "SMART_FETCH")])
    out_dir = tmp_path / "out"
    validate_d4.main(["--inputs", str(inputs), "--provenance", str(prov),
                      "--out-dir", str(out_dir)])
    s = json.loads((out_dir / "d4_summary.json").read_text())
    assert s["id_typed_inputs"] == 1


# --- D5 IDR --------------------------------------------------------------

def test_d5_idr_full_resolution(tmp_path):
    inputs = tmp_path / "inputs.csv"
    jaeger = tmp_path / "jaeger.csv"
    write_inputs(inputs, [
        base_row(parameter="orderId", value="abc-123"),
        base_row(parameter="accountId", value="def-456"),
    ])
    write_jaeger(jaeger, ["abc-123", "def-456", "extra-789"])
    out_dir = tmp_path / "out"
    rc = validate_d5.main(["--inputs", str(inputs), "--jaeger", str(jaeger),
                           "--out-dir", str(out_dir)])
    assert rc == 0
    s = json.loads((out_dir / "d5_summary.json").read_text())
    assert s["idr"] == 1.0
    assert s["resolvable"] == 2


def test_d5_idr_partial(tmp_path):
    inputs = tmp_path / "inputs.csv"
    jaeger = tmp_path / "jaeger.csv"
    write_inputs(inputs, [
        base_row(parameter="orderId", value="seen"),
        base_row(parameter="orderId", value="unseen"),
    ])
    write_jaeger(jaeger, ["seen", "extra"])
    out_dir = tmp_path / "out"
    validate_d5.main(["--inputs", str(inputs), "--jaeger", str(jaeger),
                      "--out-dir", str(out_dir)])
    s = json.loads((out_dir / "d5_summary.json").read_text())
    assert s["idr"] == 0.5
    assert s["resolvable"] == 1
    assert s["unresolved"] == 1


def test_d5_idr_empty_jaeger(tmp_path):
    inputs = tmp_path / "inputs.csv"
    jaeger = tmp_path / "jaeger.csv"
    write_inputs(inputs, [base_row(parameter="orderId", value="X")])
    write_jaeger(jaeger, [])
    out_dir = tmp_path / "out"
    validate_d5.main(["--inputs", str(inputs), "--jaeger", str(jaeger),
                      "--out-dir", str(out_dir)])
    s = json.loads((out_dir / "d5_summary.json").read_text())
    assert s["idr"] == 0.0
    assert s["observed_values_in_traces"] == 0


# --- D6 CRR --------------------------------------------------------------

def test_d6_crr_chain_resolved(tmp_path):
    """Two-step sequence where step 2 consumes orderId; step 1 produces it.
    Step 1 uses non-ID parameters (username, password) so the only ID input
    is at step 2 and resolves against step 1's producer set."""
    inputs = tmp_path / "inputs.csv"
    write_inputs(inputs, [
        # Step 1: POST /orders — produces 'order'; non-ID body params only.
        base_row(test_method="test_positive_flow_S1_v1", step_idx="1",
                 http_method="POST", path="/api/v1/orderservice/orders",
                 parameter="username", value="bob", location="body"),
        # Step 2: GET /orders/{orderId} — consumes orderId.
        base_row(test_method="test_positive_flow_S1_v1", step_idx="2",
                 http_method="GET", path="/api/v1/orderservice/orders/{orderId}",
                 parameter="orderId", value="X", location="path"),
    ])
    out_dir = tmp_path / "out"
    rc = validate_d6.main(["--inputs", str(inputs), "--out-dir", str(out_dir)])
    assert rc == 0
    s = json.loads((out_dir / "d6_summary.json").read_text())
    assert s["sequences_considered"] == 1
    assert s["sequences_fully_resolved"] == 1, s
    assert s["crr"] == 1.0
    assert s["id_inputs_total"] == 1
    assert s["id_inputs_resolved"] == 1


def test_d6_crr_chain_unresolved(tmp_path):
    """Step 1 produces 'user' but step 2 consumes orderId — violation."""
    inputs = tmp_path / "inputs.csv"
    write_inputs(inputs, [
        base_row(test_method="test_positive_flow_S1_v1", step_idx="1",
                 http_method="GET", path="/api/v1/userservice/users",
                 parameter="username", value="bob", location="body"),
        base_row(test_method="test_positive_flow_S1_v1", step_idx="2",
                 http_method="GET", path="/api/v1/orderservice/orders/{orderId}",
                 parameter="orderId", value="X", location="path"),
    ])
    out_dir = tmp_path / "out"
    validate_d6.main(["--inputs", str(inputs), "--out-dir", str(out_dir)])
    s = json.loads((out_dir / "d6_summary.json").read_text())
    assert s["sequences_considered"] == 1
    assert s["sequences_fully_resolved"] == 0
    assert s["crr"] == 0.0
    assert s["violations"] == 1


def test_d6_id_param_in_first_step_is_unresolvable(tmp_path):
    """A consumer in step 1 has no earlier step to draw from — counts as
    a violation. Documented behaviour: any ID-like consumer at step 1 is
    fundamentally unresolvable, which is the right read of the metric."""
    inputs = tmp_path / "inputs.csv"
    write_inputs(inputs, [
        base_row(test_method="t1", step_idx="1",
                 http_method="POST", path="/api/v1/orderservice/orders",
                 parameter="loginId", value="bob", location="body"),
        base_row(test_method="t1", step_idx="2",
                 http_method="GET", path="/api/v1/orderservice/orders/{orderId}",
                 parameter="orderId", value="X", location="path"),
    ])
    out_dir = tmp_path / "out"
    validate_d6.main(["--inputs", str(inputs), "--out-dir", str(out_dir)])
    s = json.loads((out_dir / "d6_summary.json").read_text())
    # 1 ID input at step 1 (loginId, no producer) and 1 at step 2 (orderId, resolved)
    assert s["id_inputs_total"] == 2
    assert s["id_inputs_resolved"] == 1
    assert s["sequences_fully_resolved"] == 0  # loginId at step 1 is unresolved
    assert s["violations"] == 1


def test_d6_skips_single_step_sequences(tmp_path):
    """Single-step sequences are excluded — they trivially resolve."""
    inputs = tmp_path / "inputs.csv"
    write_inputs(inputs, [
        base_row(test_method="t1", step_idx="1",
                 http_method="GET", path="/api/v1/orderservice/orders/{orderId}",
                 parameter="orderId", value="X", location="path"),
    ])
    out_dir = tmp_path / "out"
    validate_d6.main(["--inputs", str(inputs), "--out-dir", str(out_dir)])
    s = json.loads((out_dir / "d6_summary.json").read_text())
    assert s["sequences_considered"] == 0
    assert s["sequences_skipped_single_step"] == 1
    assert s["crr"] is None


def test_d3_shape_sanity_fake_uuid(tmp_path):
    """The fake-UUID detection caught an LLM diverse_gen failure mode
    where the LLM emitted UUID-shaped strings with non-hex characters
    (e.g. 'a3b2c1d4-ijkl-1234-5678-abcdef9015'). The shape-only sanity
    check must flag these even when the stated constraint is just
    `type: string`."""
    from validate_d3 import check_shape_sanity
    # Real UUID — passes
    assert check_shape_sanity("98779e1f-8cce-4435-9ff4-81411a9d9bd5") is None
    # Fake UUID with non-hex chars — flagged
    assert check_shape_sanity("a3b2c1d4-ijkl-1234-5678-abcdef9015") is not None
    # Plain string — passes
    assert check_shape_sanity("Shanghai") is None
    # Email-shaped without dot — flagged
    assert check_shape_sanity("user@noDomain") is not None
    # Real email — passes
    assert check_shape_sanity("user@example.com") is None


def test_d6_bare_id_resolves_when_any_earlier_producer_exists(tmp_path):
    """REVIEW finding #3 regression — bare 'id' parameter (REST primary-key
    convention) must be counted in D6. With empty stem, it resolves whenever
    ANY earlier step contributed a producer."""
    inputs = tmp_path / "inputs.csv"
    write_inputs(inputs, [
        # Step 1: any operation that contributes a producer.
        base_row(test_method="t1", step_idx="1",
                 http_method="GET", path="/api/v1/orderservice/orders",
                 parameter="someField", value="x", location="body"),
        # Step 2: bare 'id' field — resolves against step 1's 'order' producer.
        base_row(test_method="t1", step_idx="2",
                 http_method="GET", path="/api/v1/orderservice/orders/X",
                 parameter="id", value="X", location="body"),
    ])
    out_dir = tmp_path / "out"
    validate_d6.main(["--inputs", str(inputs), "--out-dir", str(out_dir)])
    s = json.loads((out_dir / "d6_summary.json").read_text())
    assert s["id_inputs_total"] == 1
    assert s["id_inputs_resolved"] == 1
    assert s["sequences_fully_resolved"] == 1


def test_d6_id_input_resolution_rate(tmp_path):
    """Verify the inner ID-input-resolution rate counter."""
    inputs = tmp_path / "inputs.csv"
    write_inputs(inputs, [
        # Step 1 produces 'order' but not 'user'
        base_row(test_method="t1", step_idx="1",
                 http_method="POST", path="/api/v1/orderservice/orders",
                 parameter="someField", value="x", location="body"),
        # Step 2: one resolved, one unresolved
        base_row(test_method="t1", step_idx="2",
                 http_method="POST", path="/api/v1/anything",
                 parameter="orderId", value="o1", location="body"),
        base_row(test_method="t1", step_idx="2",
                 http_method="POST", path="/api/v1/anything",
                 parameter="userId", value="u1", location="body"),
    ])
    out_dir = tmp_path / "out"
    validate_d6.main(["--inputs", str(inputs), "--out-dir", str(out_dir)])
    s = json.loads((out_dir / "d6_summary.json").read_text())
    assert s["id_inputs_total"] == 2
    assert s["id_inputs_resolved"] == 1  # orderId resolves, userId does not
    assert s["id_input_resolution_rate"] == 0.5
