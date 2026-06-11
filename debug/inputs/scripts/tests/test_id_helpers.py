"""Tests for id_helpers — ID-detection and stem extraction."""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from id_helpers import is_id_like, normalise_id_stem, plural_safe_stem  # noqa: E402


# --- is_id_like ---------------------------------------------------------

def test_is_id_like_camel_case_suffix():
    assert is_id_like("orderId")
    assert is_id_like("accountId")
    assert is_id_like("tripId")
    assert is_id_like("userUUID")


def test_is_id_like_snake_case_suffix():
    assert is_id_like("order_id")
    assert is_id_like("account_id")
    assert is_id_like("trip_uuid")


def test_is_id_like_camel_case_prefix():
    assert is_id_like("idAccount")
    assert is_id_like("uuidOrder")


def test_is_id_like_snake_case_prefix():
    assert is_id_like("id_account")
    assert is_id_like("uuid_order")


def test_is_id_like_rejects_english_words_ending_in_id():
    """Critical regression: pipeline-bug-audit finding #5 — plain English
    words ending in 'id' must not be treated as ID-typed."""
    for w in ("paid", "valid", "void", "humid", "aid", "liquid", "solid", "acid"):
        assert not is_id_like(w), f"{w!r} should not be ID-like"


def test_is_id_like_rejects_empty_or_none():
    assert not is_id_like("")
    assert not is_id_like(None)


def test_is_id_like_rejects_words_starting_with_id():
    """'identify', 'idle' start with 'id' but are not ID parameters."""
    assert not is_id_like("identify")
    assert not is_id_like("identity")
    assert not is_id_like("idle")
    assert not is_id_like("idea")


def test_is_id_like_accepts_bare_id_uuid():
    """REVIEW finding #3 regression — REST primary-key convention uses bare
    'id' / 'uuid' as the body field name. These must be counted as ID-typed.
    """
    assert is_id_like("id")
    assert is_id_like("ID")
    assert is_id_like("Id")
    assert is_id_like("uuid")
    assert is_id_like("UUID")


def test_normalise_id_stem_bare_id_returns_empty_stem():
    """Bare 'id' has no stem of its own — D6 treats empty stem as
    'any earlier producer satisfies it'."""
    assert normalise_id_stem("id") == ""
    assert normalise_id_stem("ID") == ""
    assert normalise_id_stem("uuid") == ""


# --- normalise_id_stem --------------------------------------------------

def test_normalise_id_stem_camel_suffix():
    assert normalise_id_stem("orderId") == "order"
    assert normalise_id_stem("tripId") == "trip"
    assert normalise_id_stem("accountId") == "account"


def test_normalise_id_stem_snake_suffix():
    assert normalise_id_stem("trip_uuid") == "trip"
    assert normalise_id_stem("account_id") == "account"


def test_normalise_id_stem_uppercase_suffix():
    assert normalise_id_stem("clientUUID") == "client"


def test_normalise_id_stem_camel_prefix():
    assert normalise_id_stem("idAccount") == "account"
    assert normalise_id_stem("uuidOrder") == "order"


def test_normalise_id_stem_snake_prefix():
    assert normalise_id_stem("id_account") == "account"


def test_normalise_id_stem_returns_none_for_non_id():
    assert normalise_id_stem("paid") is None
    assert normalise_id_stem("valid") is None
    assert normalise_id_stem("name") is None
    assert normalise_id_stem(None) is None


def test_normalise_id_stem_strips_plural():
    """contactsId → contact (s stripped)."""
    assert normalise_id_stem("contactsId") == "contact"
    assert normalise_id_stem("usersId") == "user"


def test_normalise_id_stem_protects_non_plural_endings():
    """addressId → address (no plural strip — 'address' is not a plural)."""
    assert normalise_id_stem("addressId") == "address"
    assert normalise_id_stem("statusId") == "status"


# --- plural_safe_stem ---------------------------------------------------

def test_plural_safe_stem_strips_regular_plural():
    assert plural_safe_stem("orders") == "order"
    assert plural_safe_stem("users") == "user"


def test_plural_safe_stem_protects_ss_us_is_os_as():
    assert plural_safe_stem("status") == "status"
    assert plural_safe_stem("class") == "class"
    assert plural_safe_stem("analysis") == "analysis"
    assert plural_safe_stem("logos") == "logos"
    assert plural_safe_stem("atlas") == "atlas"


def test_plural_safe_stem_curated_words():
    assert plural_safe_stem("address") == "address"
    assert plural_safe_stem("news") == "news"
    assert plural_safe_stem("bus") == "bus"


def test_plural_safe_stem_short_input():
    """Words ≤ 2 chars are not stripped."""
    assert plural_safe_stem("is") == "is"
    assert plural_safe_stem("us") == "us"
    assert plural_safe_stem("") == ""
