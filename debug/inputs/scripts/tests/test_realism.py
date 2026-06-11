"""Tests for realism_oracle — D7 entity matching."""
from __future__ import annotations

import json
import sys
import tempfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from realism_oracle import (  # noqa: E402
    RealismOracle,
    load_offline_entities,
    normalise,
)


def test_normalise_lowercases_and_trims():
    assert normalise("  Shanghai  ") == "shanghai"
    assert normalise("New York") == "new york"
    assert normalise("") == ""


def test_normalise_collapses_internal_whitespace():
    assert normalise("San   Francisco") == "san francisco"
    assert normalise("tokyo\tjapan") == "tokyo japan"


def test_oracle_offline_hit():
    o = RealismOracle(entities={"shanghai", "beijing", "tokyo"})
    ok, src = o.is_real("Shanghai")
    assert ok and src == "offline"


def test_oracle_offline_miss_without_online():
    o = RealismOracle(entities={"shanghai"}, online=False)
    ok, src = o.is_real("Atlantis")
    assert not ok and src == "unknown"


def test_oracle_empty_value():
    o = RealismOracle(entities={"shanghai"})
    ok, src = o.is_real("")
    assert not ok and src == "empty"


def test_oracle_persists_cache(tmp_path):
    """Online cache must round-trip through JSON."""
    cache_path = tmp_path / "cache.json"
    o = RealismOracle(entities=set(), cache_path=cache_path, online=False)
    o.cache["test_value"] = True
    o.save_cache()
    assert cache_path.is_file()
    loaded = json.loads(cache_path.read_text())
    assert loaded == {"test_value": True}


def test_oracle_cache_hit_priority(tmp_path):
    """A cache hit must short-circuit before any online lookup is attempted."""
    cache_path = tmp_path / "cache.json"
    cache_path.write_text(json.dumps({"foo": True}))
    o = RealismOracle(entities=set(), cache_path=cache_path, online=False)
    ok, src = o.is_real("foo")
    assert ok and src == "cache"


def test_load_offline_entities_skips_comments_and_blanks(tmp_path):
    f = tmp_path / "ents.txt"
    f.write_text(
        "# this is a comment\n"
        "shanghai\n"
        "\n"
        "  beijing  \n"
        "# another comment\n"
        "tokyo\n"
    )
    s = load_offline_entities(f)
    assert s == {"shanghai", "beijing", "tokyo"}


def test_load_offline_entities_lowercases():
    """Entries are stored lower-case for case-insensitive lookup."""
    import io
    f = Path(tempfile.mkstemp(suffix=".txt")[1])
    f.write_text("Shanghai\nBEIJING\n")
    try:
        s = load_offline_entities(f)
        assert s == {"shanghai", "beijing"}
    finally:
        f.unlink()


def test_oracle_curated_list_loads_from_default():
    """The bundled realism_entities.txt must load and contain expected anchors."""
    o = RealismOracle()
    assert "shanghai" in o.entities
    assert "beijing" in o.entities
    assert "tokyo" in o.entities
    assert len(o.entities) > 100  # curated list has hundreds of entries


# ---- Token + suffix matching (D7 widening) ----

def test_oracle_token_match_picks_up_compound_names():
    """A value like 'Manhattan Central Station' should match because
    'manhattan' is a known offline entry, even though the full phrase isn't."""
    o = RealismOracle(entities={"manhattan", "shanghai", "tokyo"})
    ok, src = o.is_real("Manhattan Central Station")
    assert ok and src == "token"


def test_oracle_token_match_skips_short_tokens():
    """Tokens shorter than 3 chars should not produce false positives."""
    o = RealismOracle(entities={"ab", "manhattan"})  # 'ab' is too short to match
    ok, _ = o.is_real("ab cd ef")
    assert not ok


def test_oracle_suffix_strip_match():
    """'Tokyo International Airport' → strip 'international airport' →
    'Tokyo' which is in the offline list."""
    o = RealismOracle(entities={"tokyo"})
    ok, src = o.is_real("Tokyo International Airport")
    assert ok and (src == "token" or src == "suffix_stripped")


def test_oracle_does_not_match_pure_garbage():
    """A clearly non-NLP string must NOT pass any oracle path."""
    o = RealismOracle(entities={"shanghai", "beijing", "tokyo"})
    ok, src = o.is_real("xy7zq#")
    assert not ok and src == "unknown"


def test_oracle_strip_then_token_match():
    """When the suffix-stripped form has multiple tokens, token-matching
    on the stripped form should still work."""
    o = RealismOracle(entities={"new york"})  # full-name entry, no individual tokens
    ok, src = o.is_real("New York Central Station")
    # Token match against full normalised value also picks up 'new york' if
    # token-iteration sees it; both paths are acceptable.
    assert ok and src in ("token", "suffix_stripped")
