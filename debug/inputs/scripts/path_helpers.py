"""
Path-segment analysis used by D6 (Chain Resolution Rate).

Given a request `(method, path)`, return the set of "producer stems" that
operation can plausibly produce. A consumer step's ID input is "resolved"
when an earlier step in the same sequence has its stem in this set.

Heuristic — matches the noun-to-stem logic that
`SemanticDependencyRegistry` uses on the Java side:

  POST /api/v1/orderservice/orders         → produces stem 'order'
  GET  /api/v1/contactservice/contacts/{id} → produces stem 'contact'
  POST /api/v1/inside_pay_service/inside_payment → produces 'payment'

Path segments matching `PATH_NOISE` (api, v1, etc.) are ignored. Segments
ending in 'service' are stripped (orderservice → order). Plurals are stem-
stripped via the same plural-safe rules used in SemanticDependencyRegistry.
"""

from __future__ import annotations

import re

from id_helpers import plural_safe_stem

PATH_NOISE = {
    "api", "rest", "service", "services", "internal", "external",
    "public", "private", "v", "version",
}

# Path segments are split into tokens by these characters; an
# "inside_pay_service" segment yields tokens "inside", "pay", "service".
TOKEN_SPLIT_RX = re.compile(r"[_\-]")

# CamelCase splitter — `orderOtherService` → ['order', 'Other', 'Service'].
# We split on the boundary between a lowercase letter and an uppercase one.
# Run BEFORE lower-casing so the boundaries are visible.
CAMEL_SPLIT_RX = re.compile(r"(?<=[a-z])(?=[A-Z])")


def producer_stems(method: str, path: str) -> set[str]:
    """Return the set of resource stems that this operation may produce.

    The result is lower-case, singular. Includes:
      - Direct path-noun stems (`/orders` → 'order', `/contacts` → 'contact').
      - Service-name stems with the 'service' suffix stripped
        (`/orderservice/...` → 'order').
      - Multi-token segment stems (`/inside_pay_service/...` → 'pay', 'inside').

    Does NOT distinguish strong (POST) from weak (GET) producers — any
    operation whose URL touches a resource is treated as a candidate
    producer of that resource's ID. A future refinement could make POST
    contribute to the producer set and GET only echo (so consumer-without-
    POST chains are penalised), but the current heuristic is conservative
    and matches how the Java SemanticDependencyRegistry treats path nouns.
    """
    if not path:
        return set()
    out: set[str] = set()
    for raw_seg in path.split("/"):
        if not raw_seg or raw_seg.startswith("{"):
            continue  # skip path placeholders like {orderId}
        # Strip query string from the LAST segment if present.
        if "?" in raw_seg:
            raw_seg = raw_seg.split("?", 1)[0]
        # First split underscores/hyphens (`inside_pay_service` → 3 tokens).
        # Then for each piece, also split on camelCase boundaries
        # (`orderOtherService` → 'order', 'Other', 'Service'). Lower-case AFTER
        # camel-case splitting so we don't lose the boundary information.
        coarse_tokens = TOKEN_SPLIT_RX.split(raw_seg)
        for coarse in coarse_tokens:
            if not coarse:
                continue
            for token in CAMEL_SPLIT_RX.split(coarse):
                tok = token.lower()
                if not tok or tok in PATH_NOISE:
                    continue
                # Strip 'service' suffix when the token is longer than just 'service'
                if tok.endswith("service") and len(tok) > len("service"):
                    tok = tok[:-len("service")]
                # Skip purely-numeric tokens (e.g. version numbers)
                if tok.isdigit():
                    continue
                # Skip tokens that are too short to be a meaningful resource noun
                if len(tok) < 3:
                    continue
                stem = plural_safe_stem(tok)
                if stem and len(stem) >= 3:
                    out.add(stem)
    return out
