"""Tests for path_helpers — producer-stem extraction for D6 CRR."""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from path_helpers import producer_stems  # noqa: E402


def test_producer_stems_simple_resource():
    assert "order" in producer_stems("POST", "/api/v1/orderservice/orders")
    assert "user" in producer_stems("POST", "/api/v1/userservice/users")


def test_producer_stems_strips_service_suffix():
    """'orderservice' → 'order'."""
    s = producer_stems("GET", "/api/v1/orderservice/orders")
    assert "order" in s


def test_producer_stems_skips_path_noise():
    """'api' and 'v1' must not be treated as resources."""
    s = producer_stems("GET", "/api/v1/orderservice/orders")
    assert "api" not in s
    assert "v1" not in s
    assert "v" not in s


def test_producer_stems_handles_path_template():
    """{orderId} placeholders must not contribute a stem."""
    s = producer_stems("GET", "/api/v1/orderservice/orders/{orderId}")
    assert "{orderId}" not in s
    assert "orderid" not in s
    assert "order" in s


def test_producer_stems_underscore_split():
    """'inside_pay_service' contributes 'pay' and 'inside'."""
    s = producer_stems("POST", "/api/v1/inside_pay_service/inside_payment")
    assert "pay" in s or "payment" in s


def test_producer_stems_filters_short_tokens():
    """Tokens shorter than 3 chars are not resources."""
    s = producer_stems("GET", "/a/b/c")
    assert "a" not in s
    assert "b" not in s
    assert "c" not in s


def test_producer_stems_protects_non_plurals():
    """An 'address' segment must not be stem-stripped to 'addres'."""
    s = producer_stems("POST", "/api/v1/userservice/address")
    assert "address" in s
    assert "addres" not in s


def test_producer_stems_empty_path():
    assert producer_stems("GET", "") == set()
    assert producer_stems("GET", "/") == set()


def test_producer_stems_skips_pure_numbers():
    """Path segments that are pure digits are noise (version numbers etc.)."""
    s = producer_stems("GET", "/api/v1/123/orders")
    assert "123" not in s
    assert "order" in s


def test_producer_stems_camel_case_splitting():
    """REVIEW finding #6 regression — TrainTicket has paths like
    /api/v1/orderOtherService/orderOther/refresh; CamelCase boundaries
    must contribute the inner stems so 'order' is reachable for D6."""
    s = producer_stems("POST", "/api/v1/orderOtherService/orderOther/refresh")
    assert "order" in s
    assert "other" in s
    s2 = producer_stems("POST", "/api/v1/adminOrderService/adminOrder")
    assert "order" in s2
    assert "admin" in s2
