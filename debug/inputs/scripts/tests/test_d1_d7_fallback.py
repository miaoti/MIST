"""Regression tests for the FALLBACK_* exclusion (D1, D7) and the tightened
D7 NLP-parameter detector. These lock in the user-named cases:

  - bare `name` → not NLP (train labels like "Express Train" should not
    be scored as real-world entities)
  - `contactsName` → NLP (qualified-Name is a person/place)
  - `FALLBACK_*` values → match the FALLBACK regex and are excluded
  - `Express Train` → does NOT match the FALLBACK regex
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from validate_d7 import is_nlp_param, FALLBACK_VALUE_RX as D7_RX
from validate_d1 import FALLBACK_VALUE_RX as D1_RX


def test_d7_nlp_rejects_bare_name():
    """In TrainTicket the bare `name` parameter is a train label, not an entity."""
    assert is_nlp_param("name") is False
    assert is_nlp_param("Name") is False
    assert is_nlp_param("NAME") is False


def test_d7_nlp_accepts_qualified_name():
    """`contactsName`, `firstName`, `cityName` are entity-bearing fields."""
    assert is_nlp_param("contactsName") is True
    assert is_nlp_param("firstName") is True
    assert is_nlp_param("cityName") is True
    assert is_nlp_param("lastName") is True


def test_d7_nlp_skips_id_like_names():
    """ID-like names should never be scored, even with `name` in them."""
    assert is_nlp_param("operationName") is False
    assert is_nlp_param("parameterName") is False
    assert is_nlp_param("userId") is False


def test_d7_nlp_accepts_place_station_country():
    """The classic ARTE-style entity-bearing parameters."""
    assert is_nlp_param("startStation") is True
    assert is_nlp_param("endStation") is True
    assert is_nlp_param("startPlace") is True
    assert is_nlp_param("country") is True
    assert is_nlp_param("terminal") is True
    assert is_nlp_param("junction") is True


def test_fallback_regex_matches_typeaware_padding():
    """`MultiServiceTestCaseGenerator.typeAwareFallbackValue` emits values of
    the shape `FALLBACK_<paramName>_<index>` for string params it cannot
    otherwise resolve. Both D1 and D7 must filter them."""
    samples = [
        "FALLBACK_name_0",
        "FALLBACK_startPlace_5",
        "FALLBACK_contactsName_12",
        "FALLBACK_endStation_99",
    ]
    for v in samples:
        assert D7_RX.match(v) is not None, f"D7 should match {v!r}"
        assert D1_RX.match(v) is not None, f"D1 should match {v!r}"


def test_fallback_regex_does_not_match_real_values():
    """Realistic generator output and LLM output must NOT be filtered."""
    non_fallback = [
        "Express Train",
        "Black Hawk",
        "Shanghai",
        "user_001",
        "fallback_lower",                # lowercase prefix is a different word
        "FALLBACK_no_index",             # no trailing index → not the padding pattern
        "FALLBACK_param_value",          # trailing token isn't a number
        "12345",
    ]
    for v in non_fallback:
        assert D7_RX.match(v) is None, f"D7 should NOT match {v!r}"
        assert D1_RX.match(v) is None, f"D1 should NOT match {v!r}"


def main() -> int:
    fns = [v for k, v in globals().items() if k.startswith("test_") and callable(v)]
    fail = []
    for f in fns:
        try:
            f()
        except AssertionError as e:
            fail.append((f.__name__, str(e) or "AssertionError"))
        except Exception as e:
            fail.append((f.__name__, f"{type(e).__name__}: {e}"))
    print(f"Pass: {len(fns) - len(fail)}/{len(fns)}")
    for n, e in fail:
        print(f"  FAIL {n}: {e}")
    return 1 if fail else 0


if __name__ == "__main__":
    raise SystemExit(main())
