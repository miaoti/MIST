"""
Realism oracle for D7. Given a string value, return True iff the value
matches a known real-world entity.

Two oracle modes:
  offline (default) — match against a curated, version-controlled entity
                      list bundled with this script (`realism_entities.txt`).
                      Deterministic and testable.
  online            — fall back to Wikidata's `wbsearchentities` API for
                      values not in the offline list. Cached locally to a
                      JSON file. Online mode is opt-in only because most
                      runs benefit from deterministic, reproducible scoring.

Lookups are case-insensitive and ignore surrounding whitespace.
"""

from __future__ import annotations

import json
import re
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Iterable

DEFAULT_ENTITIES = Path(__file__).resolve().parent / "realism_entities.txt"
WIKIDATA_API = "https://www.wikidata.org/w/api.php"
WIKIDATA_TIMEOUT = 10  # seconds


def load_offline_entities(path: Path = DEFAULT_ENTITIES) -> set[str]:
    """Read the curated entity file and return a normalised lower-case set."""
    out: set[str] = set()
    if not path.is_file():
        return out
    for line in path.read_text(encoding="utf-8").splitlines():
        s = line.strip()
        if not s or s.startswith("#"):
            continue
        out.add(s.lower())
    return out


def normalise(value: str) -> str:
    """Lower-case, trim, and collapse internal whitespace for matching."""
    if not value:
        return ""
    return re.sub(r"\s+", " ", value.strip()).lower()


# Common transport-domain suffixes / qualifiers that are noise when matching a
# value like "Manhattan Central Station" against a list of city names.
# Strip them off before re-checking the lookup.
TRANSPORT_SUFFIXES = (
    "central station", "international airport", "railway station",
    "train station", "metro station", "bus terminal", "junction",
    "central", "station", "terminal", "airport", "international",
    "railway", "metro", "north", "south", "east", "west",
)

# Tokens shorter than this are skipped during token matching to avoid
# accidental matches against noise tokens like "of", "and", "ny".
TOKEN_MIN_LENGTH = 3


class RealismOracle:
    """Holds an offline entity set and an optional online cache."""

    def __init__(self, entities: Iterable[str] | None = None,
                 cache_path: Path | None = None,
                 online: bool = False) -> None:
        self.entities: set[str] = (
            {e.lower() for e in entities} if entities is not None
            else load_offline_entities()
        )
        self.cache_path = cache_path
        self.cache: dict[str, bool] = {}
        if cache_path and cache_path.is_file():
            try:
                self.cache = json.loads(cache_path.read_text(encoding="utf-8"))
            except (json.JSONDecodeError, OSError):
                self.cache = {}
        self.online = online
        self.online_calls = 0
        self.online_hits = 0

    def is_real(self, value: str) -> tuple[bool, str]:
        """Return (is_real, source). source ∈ {'offline', 'token', 'suffix_stripped',
        'cache', 'online', 'empty', 'unknown'}.

        Lookup chain (each step short-circuits on a hit):
          1. exact match against the curated offline list
          2. token-match — any whitespace/punctuation-separated token of the
             value matches an offline entry (length ≥ 3 to avoid noise)
          3. suffix-stripped match — strip a transport-domain suffix
             (`station`, `terminal`, ...) and re-check exact + token
          4. cache (from a prior online lookup persisted to disk)
          5. online (Wikidata wbsearchentities) when --online
          6. unknown — not real per any oracle path

        Token + suffix matching widens the coverage of the offline oracle from
        "exact city name" to "value contains a known city or strip-able
        transport suffix surrounding a known token." This addresses the
        common LLM output pattern "<city> Central Station" / "<city>
        International Airport" / "<city> Junction" that was previously
        flagged unrealistic despite containing a real entity.
        """
        norm = normalise(value)
        if not norm:
            return False, "empty"

        # 1. Exact match
        if norm in self.entities:
            return True, "offline"

        # 2. Token match
        if self._token_match(norm):
            return True, "token"

        # 3. Suffix-stripped exact + token match
        stripped = self._strip_transport_suffix(norm)
        if stripped and stripped != norm:
            if stripped in self.entities:
                return True, "suffix_stripped"
            if self._token_match(stripped):
                return True, "suffix_stripped"

        # 4. Cache hit from a prior online lookup
        if norm in self.cache:
            return self.cache[norm], "cache"

        # 5. Online fallback (opt-in)
        if self.online:
            ok = self._wikidata_search(norm)
            self.cache[norm] = ok
            self.online_calls += 1
            if ok:
                self.online_hits += 1
            return ok, "online"

        return False, "unknown"

    def _token_match(self, norm_value: str) -> bool:
        """True when any whitespace- or punctuation-separated token (≥ 3
        chars) of the value matches an offline entry, OR when the value
        starts with a known multi-word entity prefix (catches concatenated
        forms like 'chicagounionstation' that contain 'chicago' or
        'shanghaihongqiao' that contain 'shanghai')."""
        for tok in re.split(r"[\s,;/_\-]+", norm_value):
            if len(tok) >= TOKEN_MIN_LENGTH and tok in self.entities:
                return True
            # Concatenated form: token may not split cleanly but contains a
            # known city name as a prefix. Only try this when the candidate
            # is at least 8 chars to avoid overly-aggressive false positives.
            if len(tok) >= 8 and self._prefix_match(tok):
                return True
        return False

    def _prefix_match(self, tok: str) -> bool:
        """Return True iff some offline entity (≥ 4 chars) is a prefix of `tok`.
        Used to catch concatenated forms like `chicagounionstation` →
        `chicago` is the prefix → match."""
        for ent in self.entities:
            if len(ent) >= 4 and tok.startswith(ent):
                return True
        return False

    @staticmethod
    def _strip_transport_suffix(norm_value: str) -> str | None:
        """Strip a single trailing transport-domain qualifier from the
        normalised value. Tries multi-word suffixes first ('central station')
        then single-word ones ('station'). Returns the stripped string or
        None when no suffix matches."""
        for suffix in TRANSPORT_SUFFIXES:
            if norm_value.endswith(" " + suffix):
                return norm_value[: -(len(suffix) + 1)].strip()
        return None

    def _wikidata_search(self, value: str) -> bool:
        """Hit the Wikidata search API. Failure or timeout → False."""
        params = {
            "action": "wbsearchentities",
            "search": value,
            "language": "en",
            "format": "json",
            "limit": "1",
        }
        url = f"{WIKIDATA_API}?{urllib.parse.urlencode(params)}"
        req = urllib.request.Request(url, headers={"User-Agent": "RESTest-D7-Realism/1.0"})
        try:
            with urllib.request.urlopen(req, timeout=WIKIDATA_TIMEOUT) as resp:
                doc = json.loads(resp.read().decode("utf-8", errors="replace"))
        except (urllib.error.URLError, json.JSONDecodeError, TimeoutError, OSError):
            return False
        return bool(doc.get("search"))

    def save_cache(self) -> None:
        if self.cache_path is None:
            return
        try:
            self.cache_path.parent.mkdir(parents=True, exist_ok=True)
            self.cache_path.write_text(json.dumps(self.cache, indent=2), encoding="utf-8")
        except OSError:
            pass
