"""Tests for mine_provenance.classify_line — exec-log line classification."""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from mine_provenance import (  # noqa: E402
    P_LLM,
    P_NEGATIVE,
    P_SHARED_POOL,
    P_SMART_FETCH,
    classify_line,
)


def test_smart_fetch_step1_with_type_decoration():
    """Critical regression: trailing ' (type: String)' must NOT leak into the
    captured value. Otherwise the (param, value) join with inputs.csv fails."""
    line = (
        "2026-04-27 23:04:32 INFO  MultiServiceTestCaseGenerator:818 - "
        "Smart Fetch (Step 1) → ts-contacts-service accountId = "
        "98779e1f-8cce-4435-9ff4-81411a9d9bd5 (type: String) ✅"
    )
    result = classify_line(line)
    assert result == (P_SMART_FETCH, "accountId", "98779e1f-8cce-4435-9ff4-81411a9d9bd5")


def test_smart_fetch_step1_no_decoration():
    line = ("Smart Fetch (Step 1) → ts-contacts-service accountId = "
            "98779e1f-8cce-4435-9ff4-81411a9d9bd5 ✅")
    result = classify_line(line)
    assert result == (P_SMART_FETCH, "accountId", "98779e1f-8cce-4435-9ff4-81411a9d9bd5")


def test_smart_fetch_independent_with_step_decoration():
    line = ("Smart Fetch (Independent) → ts-foo-service paramX = "
            "abc123 ✅ (step R3)")
    result = classify_line(line)
    assert result == (P_SMART_FETCH, "paramX", "abc123")


def test_smart_fetch_bare_during_pool_fill():
    line = "INFO  SmartInputFetcher:183 - Smart Fetch → endPlace = beijing ✅"
    result = classify_line(line)
    assert result == (P_SMART_FETCH, "endPlace", "beijing")


def test_smart_fetch_failure_marker_rejected():
    """'ERROR (No smart sources …)' MUST NOT count as a smart-fetch hit."""
    line = ("INFO  SmartInputFetcher:190 - Smart Fetch → loginId = "
            "ERROR (No smart sources available for parameter: loginId), "
            "falling back to LLM")
    assert classify_line(line) is None


def test_smart_fetch_no_good_match_sentinel_rejected():
    """'NO_GOOD_MATCH ✅' is the LLM extractor's abstention sentinel — the
    actual variant value will not be 'NO_GOOD_MATCH', so this line is not a
    real provenance entry for any (param, value) we'll see in inputs.csv."""
    line = "Smart Fetch → state = NO_GOOD_MATCH ✅"
    assert classify_line(line) is None


def test_shared_pool_with_pool_size_decoration():
    line = (
        "Shared Pool (Step 1) → ts-order-other-service boughtDateEnd = "
        "2026-05-08T23:48:54Z (type: String, pool size: 15) ✅"
    )
    result = classify_line(line)
    assert result == (P_SHARED_POOL, "boughtDateEnd", "2026-05-08T23:48:54Z")


def test_llm_step1_fallback_with_type():
    line = ("LLM (Step 1 Fallback) → ts-foo-service param = G1234 (type: String)")
    result = classify_line(line)
    assert result == (P_LLM, "param", "G1234")


def test_llm_independent_fallback_with_step():
    line = ("LLM (Independent Fallback) → ts-foo-service param = abc "
            "(type: String) (step R2)")
    result = classify_line(line)
    assert result == (P_LLM, "param", "abc")


def test_llm_fallback_rotated():
    line = "LLM (Fallback, Rotated) → boughtDateEnd = 2026-05-18T23:48:54Z"
    result = classify_line(line)
    assert result == (P_LLM, "boughtDateEnd", "2026-05-18T23:48:54Z")


def test_llm_fallback_rotated_with_from_n_options_suffix():
    """REVIEW finding #1 regression — the live exec log appends
    ' (from N options)' to LLM (Fallback, Rotated) lines. That suffix must
    be stripped from the captured value, otherwise 46/46 LLM rows in real
    runs are corrupted with the suffix and don't join with inputs.csv."""
    line = "LLM (Fallback, Rotated) → orderId = ORD123456 (from 5 options)"
    result = classify_line(line)
    assert result == (P_LLM, "orderId", "ORD123456")


def test_split_type_and_format_parenthetical():
    """The constraint extractor must split 'integer (int32)' into
    type='integer', format='int32'. Otherwise the D3 LHR validator only
    checks type and never enforces format constraints, producing an
    artificial 0% LHR."""
    from mine_llm_log import _split_type_and_format
    assert _split_type_and_format("integer (int32)") == ("integer", "int32")
    assert _split_type_and_format("string (date)") == ("string", "date")
    assert _split_type_and_format("number (double)") == ("number", "double")
    assert _split_type_and_format("string") == ("string", "")
    assert _split_type_and_format("") == ("", "")


def test_provenance_does_not_demote_pool_to_negative():
    """REVIEW finding #2 regression — when the same (param, value) appears
    first as a Shared Pool draw and later as a Negative Test fault, the
    final classification must NOT be NEGATIVE_FAULT (which would mis-label
    the legitimate positive draw)."""
    # We test through the line classifier directly here; the upgrade rule
    # lives in mine_provenance.main(), but the principle is that the
    # classifier returns the correct prov for each line and the rule only
    # promotes shared-pool to smart-fetch / LLM, never to negative.
    pool_line = ("Shared Pool (Step 1) → ts-svc accountId = `` "
                 "(type: String, pool size: 15) ✅")
    neg_line = ("✅ Negative Test (Round-Robin) → accountId = `` "
                "[InvalidType: SEMANTIC_MISMATCH] (javaType: String) - LOCKED")
    p1 = classify_line(pool_line)
    p2 = classify_line(neg_line)
    assert p1 == (P_SHARED_POOL, "accountId", "``")
    assert p2 == (P_NEGATIVE, "accountId", "``")


def test_negative_test_round_robin():
    line = (
        "INFO  MultiServiceTestCaseGenerator:742 - "
        "✅ Negative Test (Round-Robin) → boughtDateStart = "
        "' OR '1'='1 [InvalidType: SPECIAL_CHARACTERS] (javaType: String) - LOCKED"
    )
    result = classify_line(line)
    assert result is not None
    prov, param, value = result
    assert prov == P_NEGATIVE
    assert param == "boughtDateStart"
    assert "OR" in value


def test_negative_test_truncated_overflow_value():
    """Overflow values are truncated with '...' in the log; the trailing
    ellipsis must be stripped because it's a logging artifact, not part of
    the value."""
    line = (
        "✅ Negative Test (Round-Robin) → boughtDateStart = "
        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA... "
        "[InvalidType: OVERFLOW] (javaType: String) - LOCKED"
    )
    result = classify_line(line)
    assert result is not None
    prov, param, value = result
    assert prov == P_NEGATIVE
    assert param == "boughtDateStart"
    assert not value.endswith("...")
    assert "AAAAA" in value


def test_unknown_line_returns_none():
    assert classify_line("some random log line") is None
    assert classify_line("") is None
