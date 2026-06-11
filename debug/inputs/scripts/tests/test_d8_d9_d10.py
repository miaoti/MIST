"""Sanity tests for D8 (entropy), D9 (EPC), D10 (NIFP) helpers.

Run via the project's bundled test runner:
    python3 debug/inputs/scripts/tests/test_d8_d9_d10.py
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import math


# ---------- D8 ----------
from validate_d8 import shannon_entropy, simpson_diversity  # noqa: E402


def test_entropy_uniform_two_values():
    """H of a 2-class uniform distribution is exactly 1 bit."""
    assert math.isclose(shannon_entropy([1, 1]), 1.0, abs_tol=1e-9)


def test_entropy_uniform_four_values():
    """H of a 4-class uniform distribution is exactly 2 bits."""
    assert math.isclose(shannon_entropy([5, 5, 5, 5]), 2.0, abs_tol=1e-9)


def test_entropy_zero_when_collapsed():
    """A single-class pool has zero entropy."""
    assert shannon_entropy([10]) == 0.0


def test_simpson_uniform_two():
    """1 - sum(p^2) = 1 - 2 * 0.25 = 0.5 for two equally-likely classes."""
    assert math.isclose(simpson_diversity([1, 1]), 0.5, abs_tol=1e-9)


def test_simpson_zero_when_collapsed():
    assert simpson_diversity([10]) == 0.0


def test_h_norm_uses_pool_size_not_distinct():
    """A pool of 100 samples that collapses to two 50/50 values should NOT
    score H_norm = 1.0 — the framework spec says the ceiling is log_2 n
    (n = pool size). Normalising by distinct-count would let any binary
    pool pass; normalising by total catches the collapse.
    """
    counts = [50, 50]                            # 2 classes, 100 total
    h = shannon_entropy(counts)
    total = sum(counts)
    h_norm = h / math.log2(total)
    assert math.isclose(h_norm, 1.0 / math.log2(100), abs_tol=1e-6)
    # Should be roughly 0.15, definitely not 1.0
    assert h_norm < 0.2


# ---------- D9 ----------
from validate_d9 import classes_for_schema, classify_value  # noqa: E402


def test_d9_enum_classes():
    schema = {"type": "string", "enum": ["A", "B", "C"]}
    classes = classes_for_schema(schema)
    assert len(classes) == 3
    assert classify_value("A", schema).startswith("enum:")
    assert classify_value("ZZ", schema) is None  # not in enum


def test_d9_boolean_classes():
    schema = {"type": "boolean"}
    assert set(classes_for_schema(schema)) == {"true", "false"}
    assert classify_value("true", schema) == "true"
    assert classify_value("FALSE", schema) == "false"
    assert classify_value("yes", schema) is None


def test_d9_integer_unbounded():
    schema = {"type": "integer"}
    assert classify_value("0", schema) == "zero"
    assert classify_value("5", schema) == "positive"
    assert classify_value("-3", schema) == "negative"
    assert classify_value("abc", schema) is None


def test_d9_integer_bounded_in_range():
    schema = {"type": "integer", "minimum": 1, "maximum": 100}
    assert classify_value("0", schema) == "below_min"
    assert classify_value("1", schema) == "at_min"
    assert classify_value("50", schema) == "in_range"
    assert classify_value("100", schema) == "at_max"
    assert classify_value("101", schema) == "above_max"


def test_d9_classes_prune_unreachable_zero_negative():
    """For `minimum:1, maximum:100` the classes `zero` and `negative` are
    unreachable — they must be pruned, otherwise EPC is artificially capped."""
    schema = {"type": "integer", "minimum": 1, "maximum": 100}
    cls = classes_for_schema(schema)
    assert "zero" not in cls
    assert "negative" not in cls
    # Reachable classes must remain
    assert "below_min" in cls and "at_min" in cls
    assert "at_max" in cls and "above_max" in cls and "in_range" in cls


def test_d9_classes_keep_zero_when_in_interior():
    """For `minimum:-5, maximum:5`, zero is reachable and must be kept."""
    schema = {"type": "integer", "minimum": -5, "maximum": 5}
    cls = classes_for_schema(schema)
    assert "zero" in cls


def test_d9_string_format():
    schema = {"type": "string", "format": "uuid"}
    assert classify_value("550e8400-e29b-41d4-a716-446655440000", schema) == "format_conforming"
    assert classify_value("not-a-uuid", schema) == "format_violating"
    assert classify_value("", schema) == "empty"


def test_d9_string_length_classes():
    """No length constraint, no format → fall into short/medium/long buckets."""
    schema = {"type": "string"}
    assert classify_value("", schema) == "empty"
    assert classify_value("a", schema) == "short"
    assert classify_value("a" * 25, schema) == "medium"
    assert classify_value("a" * 200, schema) == "long"


# ---------- D10 ----------
from validate_d10 import (  # noqa: E402
    check_type_mismatch,
    check_boundary_violation,
    check_overflow,
    check_empty_input,
    check_null_input,
    check_special_characters,
    check_semantic_mismatch,
)


def test_d10_type_mismatch_javatype_authoritative():
    """`javaType: Integer` for a string-typed schema is a TYPE_MISMATCH —
    we trust the generator's declared type because the log loses quoting."""
    assert check_type_mismatch("12345", {"type": "string"}, javatype="Integer") is True
    assert check_type_mismatch("12345", {"type": "string"}, javatype="String") is False


def test_d10_type_mismatch_string_for_string_schema():
    """A plain unquoted string against string schema → not a mismatch."""
    assert check_type_mismatch("hello world", {"type": "string"}) is False


def test_d10_type_mismatch_object_literal_for_string_schema():
    """A JSON object literal vs. a string schema → mismatch even without javatype."""
    assert check_type_mismatch('{"a":1}', {"type": "string"}) is True


def test_d10_type_mismatch_alphabetic_for_integer():
    """Without javaType, fall back to value-shape inference."""
    assert check_type_mismatch("hello", {"type": "integer"}) is True


def test_d10_type_mismatch_string_for_integer_via_javatype():
    """`javaType: String` against integer schema is a TYPE_MISMATCH."""
    assert check_type_mismatch("123", {"type": "integer"}, javatype="String") is True


def test_d10_boundary_violation_string_minlength():
    schema = {"type": "string", "minLength": 5}
    assert check_boundary_violation("ab", schema) is True
    assert check_boundary_violation("abcde", schema) is False
    assert check_boundary_violation("abcdef", schema) is False


def test_d10_boundary_violation_numeric_max():
    schema = {"type": "integer", "maximum": 100}
    assert check_boundary_violation("101", schema) is True
    assert check_boundary_violation("100", schema) is False
    assert check_boundary_violation("0", schema) is False


def test_d10_overflow_truncated_string_signal():
    """A truncated value (the log truncates long strings) is overflow."""
    assert check_overflow("AAA", {"type": "string"}, truncated=True) is True


def test_d10_overflow_repeated_char_ramp():
    """A 30+ character repeated single-char string is an overflow probe."""
    assert check_overflow("X" * 50, {"type": "string"}) is True


def test_d10_overflow_normal_string_below_threshold():
    assert check_overflow("hello world", {"type": "string"}) is False


def test_d10_empty_input_required():
    assert check_empty_input("", None, required=True) is True
    assert check_empty_input("   ", None, required=True) is True
    assert check_empty_input("hello", None, required=True) is False
    assert check_empty_input("", None, required=False) is False


def test_d10_null_input_cross_lang():
    assert check_null_input("null", None, required=True) is True
    assert check_null_input("nil", None, required=True) is True
    assert check_null_input("undefined", None, required=True) is True
    assert check_null_input("None", None, required=True) is True
    assert check_null_input("hello", None, required=True) is False


def test_d10_special_characters_owasp():
    assert check_special_characters("'; DROP TABLE users; --", None) is True
    assert check_special_characters("<script>alert(1)</script>", None) is True
    assert check_special_characters("../../../etc/passwd", None) is True
    assert check_special_characters("hello world", None) is False


def test_d10_semantic_mismatch_pure_when_no_other_fault():
    """A value that doesn't trigger any other fault is pure SEMANTIC_MISMATCH."""
    schema = {"type": "string"}
    # Invalid date but a syntactically valid string — pure SEMANTIC_MISMATCH
    assert check_semantic_mismatch("2025-13-99", schema) is True


def test_d10_semantic_mismatch_impure_when_javatype_says_typemismatch():
    """A value with a Java-type that contradicts the schema is impure for
    SEMANTIC_MISMATCH — it's actually a TYPE_MISMATCH and the label is wrong."""
    schema = {"type": "string"}
    assert check_semantic_mismatch("12345", schema, javatype="Integer") is False


def test_d10_semantic_mismatch_impure_when_overflow_truncated():
    """A truncated value is an overflow probe; SEMANTIC label on it is wrong."""
    schema = {"type": "string"}
    assert check_semantic_mismatch("AAA", schema, truncated=True) is False


# ---------- Manual harness ----------
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
