"""
ID detection and stem extraction.

Mirrors the boundary-aware regex used by RESTest's SemanticDependencyRegistry
(after the bug-audit fix in pipeline-bug-audit.md finding #5). Plain English
words ending in 'id' or 'uuid' (paid, valid, void, humid, aid) are NOT
treated as ID-typed.

Used by D4 (SFHR), D5 (IDR), D6 (CRR).
"""

from __future__ import annotations

import re

# Camel-case (Id/UUID) or snake-case (_id/_uuid) boundary required.
# Pure-lowercase trailing 'id'/'uuid' is rejected.
ID_SUFFIX = re.compile(r"^.+?(Id|ID|UUID|Uuid|_id|_ID|_uuid|_UUID)$")
ID_SUFFIX_STRIP = re.compile(r"(Id|ID|UUID|Uuid|_id|_ID|_uuid|_UUID)$")

# Bare ID parameter names — the literal field name `id`, `uuid`, etc. — that
# RESTful APIs commonly use for the "primary key of this resource" body field.
# These don't match ID_SUFFIX (which requires a stem prefix) but are clearly
# ID-typed and must be counted in D4/D5/D6 denominators.
SHORT_ID_NAMES = {"id", "ID", "Id", "iD", "uuid", "UUID", "Uuid", "uUid"}

# Prefix form: id_account, idAccount, uuid_order, uuidOrder.
# Requires explicit separator so words like 'identity', 'idle' are not matched.
ID_PREFIX = re.compile(r"^(?:id|Id|ID|uuid|Uuid|UUID)(?:_(\w+)|([A-Z]\w*))$")

# Common English non-plurals that must not have their trailing 's' stripped.
NON_PLURAL_S_WORDS = {
    "address", "news", "bus", "gas", "boss", "loss", "pass", "class",
    "miss", "kiss", "less", "press", "process", "stress", "access",
    "atlas", "canvas", "chaos", "lens", "series", "species", "logos",
    "campus", "focus", "menus", "virus", "thus", "plus", "minus", "bonus",
}


def is_id_like(param_name: str) -> bool:
    """True when the parameter name follows an ID convention.

    Camel-case suffix (`orderId`, `userUUID`) or snake-case suffix
    (`account_id`, `trip_uuid`) qualifies. Camel-case prefix (`idAccount`)
    or snake-case prefix (`uuid_order`) qualifies. Bare `id`/`uuid` field
    names also qualify (REST primary-key convention). Pure-lowercase `id`
    suffix (`paid`, `valid`, `humid`) is **rejected**.
    """
    if not param_name:
        return False
    if param_name in SHORT_ID_NAMES:
        return True
    if ID_SUFFIX.match(param_name):
        return True
    if ID_PREFIX.match(param_name):
        return True
    return False


def plural_safe_stem(stem: str) -> str:
    """Strip a trailing 's' only when it forms a regular English plural.

    Protects words ending in `ss`/`us`/`is`/`os`/`as` and a curated set of
    common non-plurals (`news`, `address`, `bus`, ...). Mirrors the behaviour
    of `SemanticDependencyRegistry.pluralSafeStem` after the bug-audit fix.
    """
    if not stem or len(stem) <= 2 or not stem.endswith("s"):
        return stem
    last2 = stem[-2:]
    if last2 in ("ss", "us", "is", "os", "as"):
        return stem
    if stem in NON_PLURAL_S_WORDS:
        return stem
    return stem[:-1]


def normalise_id_stem(param_name: str) -> str | None:
    """Reduce an ID-typed parameter name to its entity stem (lower-case singular).

    Examples:
        orderId      -> order
        tripId       -> trip
        accountId    -> account
        contactsId   -> contact
        trip_uuid    -> trip
        userUUID     -> user
        idAccount    -> account
        uuid_order   -> order
        id           -> ''       (no stem; matches any path noun via D6's
                                  "no stem constraint" semantics)

    Returns None when the name is not ID-like (e.g. 'paid', 'valid').
    """
    if param_name is None:
        return None

    if param_name in SHORT_ID_NAMES:
        # Bare 'id' / 'uuid' has no stem — it's whatever the surrounding
        # path's resource is. D6 treats an empty stem as "any earlier
        # producer satisfies it" because the resource is implicit in path.
        return ""

    pre = ID_PREFIX.match(param_name)
    if pre:
        raw = pre.group(1) if pre.group(1) is not None else pre.group(2)
        return plural_safe_stem(raw.lower())

    if not ID_SUFFIX.match(param_name):
        return None
    stripped = ID_SUFFIX_STRIP.sub("", param_name).rstrip("_")
    if not stripped:
        return None
    return plural_safe_stem(stripped.lower())
