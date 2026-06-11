#!/usr/bin/env python3
"""
Smart-fetch registry migration — clean the polluted YAML.

Removes:
  - entries whose `service` is the literal sentinel `NO_GOOD_MATCH`
  - endpoints with literal `{paramName}` placeholders that the new fetch path will skip anyway
  - endpoints synthesized as `*/query` (the fabrication that Finding #33 + #2 produced)
  - mapping entries that lowercase-collide (keep highest successRate, latest lastUsed)
  - very-stale entries with successRate=0.0 last used > 90 days ago

Caps:
  - per (endpoint, param) error history to 50 entries
  - individual error reasons to 1024 chars

Backs up the original to `input-fetch-registry.YYYYMMDD.bak.yaml` before overwriting.

Usage: python3 migrate_registry.py [path/to/input-fetch-registry.yaml]
       (defaults to src/main/resources/My-Example/trainticket/input-fetch-registry.yaml)
"""
from __future__ import annotations

import datetime as _dt
import shutil
import sys
import os
from pathlib import Path
from typing import Any, Dict, List

try:
    import yaml
except ImportError:
    print("ERROR: pyyaml not installed. Run: pip install pyyaml", file=sys.stderr)
    sys.exit(1)

DEFAULT_REGISTRY = (
    Path(__file__).resolve().parents[4]
    / "src" / "main" / "resources" / "My-Example" / "trainticket" / "input-fetch-registry.yaml"
)
MAX_ERRORS_PER_PARAM = 50
MAX_REASON_CHARS = 1024
STALE_DAYS = 90


def load_yaml(path: Path) -> Dict[str, Any]:
    with path.open("r", encoding="utf-8") as fh:
        return yaml.safe_load(fh) or {}


def dump_yaml(obj: Any, path: Path) -> None:
    with path.open("w", encoding="utf-8") as fh:
        yaml.safe_dump(obj, fh, sort_keys=False, allow_unicode=True, default_flow_style=False)


def list_to_datetime(parts: List[int]) -> _dt.datetime | None:
    if not isinstance(parts, list) or len(parts) < 6:
        return None
    try:
        return _dt.datetime(
            year=int(parts[0]),
            month=int(parts[1]),
            day=int(parts[2]),
            hour=int(parts[3]),
            minute=int(parts[4]),
            second=int(parts[5]),
            microsecond=int(parts[6]) // 1000 if len(parts) > 6 else 0,
        )
    except Exception:
        return None


def is_path_templated(endpoint: str) -> bool:
    return "{" in (endpoint or "")


def is_query_fabrication(endpoint: str) -> bool:
    """Match the fabricated `/query` rows produced by the old `inferEndpointForService`."""
    if not endpoint:
        return False
    e = endpoint.lower()
    # Fabricated suffix '/query' is the tell. Real legitimate queries use real verbs in
    # OpenAPI paths and do not end in `/query` for the trainticket spec. If the user's
    # SUT really uses /query endpoints they can disable this rule.
    return e.endswith("/query")


def lowercase_service(svc: str) -> str:
    return svc.lower() if isinstance(svc, str) else svc


def migrate(reg: Dict[str, Any]) -> Dict[str, Any]:
    counters = {
        "no_good_match_dropped": 0,
        "templated_dropped": 0,
        "query_fabrication_dropped": 0,
        "stale_zero_dropped": 0,
        "case_collisions_merged": 0,
        "service_lowercased": 0,
        "errors_capped": 0,
        "reason_truncated": 0,
        "params_before": 0,
        "params_after": 0,
        "mappings_before": 0,
        "mappings_after": 0,
    }
    pmap = reg.get("parameterMappings") or {}
    counters["params_before"] = len(pmap)
    cutoff = _dt.datetime.now() - _dt.timedelta(days=STALE_DAYS)

    new_pmap: Dict[str, List[Dict[str, Any]]] = {}
    for param, mappings in pmap.items():
        if not isinstance(mappings, list):
            continue
        counters["mappings_before"] += len(mappings)
        kept_by_canonical: Dict[tuple, Dict[str, Any]] = {}
        for m in mappings:
            if not isinstance(m, dict):
                continue
            svc = m.get("service")
            ep = m.get("endpoint", "")
            # Drop sentinel.
            if isinstance(svc, str) and svc.strip().upper() == "NO_GOOD_MATCH":
                counters["no_good_match_dropped"] += 1
                continue
            # Drop literal-{} templated endpoints that the runtime will skip anyway.
            if is_path_templated(ep):
                counters["templated_dropped"] += 1
                continue
            # Drop fabricated /query.
            if is_query_fabrication(ep):
                counters["query_fabrication_dropped"] += 1
                continue
            # Lowercase service.
            if isinstance(svc, str):
                lower_svc = svc.lower()
                if lower_svc != svc:
                    counters["service_lowercased"] += 1
                    m["service"] = lower_svc
                svc = lower_svc
            # Stale-and-failed pruning.
            sr = m.get("successRate", 0.0)
            last = list_to_datetime(m.get("lastUsed", []))
            if isinstance(sr, (int, float)) and float(sr) == 0.0 and last is not None and last < cutoff:
                counters["stale_zero_dropped"] += 1
                continue
            # Case-collision merge: same (svc, endpoint, method) keep the better one.
            key = (svc, ep, m.get("method", "GET"))
            existing = kept_by_canonical.get(key)
            if existing is None:
                kept_by_canonical[key] = m
            else:
                counters["case_collisions_merged"] += 1
                # Keep higher successRate; if tie, latest lastUsed.
                ex_sr = float(existing.get("successRate", 0.0) or 0.0)
                m_sr = float(m.get("successRate", 0.0) or 0.0)
                if m_sr > ex_sr:
                    kept_by_canonical[key] = m
                elif m_sr == ex_sr:
                    ex_last = list_to_datetime(existing.get("lastUsed", []))
                    m_last = list_to_datetime(m.get("lastUsed", []))
                    if ex_last is None or (m_last is not None and m_last > ex_last):
                        kept_by_canonical[key] = m
        if kept_by_canonical:
            new_pmap[param] = list(kept_by_canonical.values())
            counters["mappings_after"] += len(new_pmap[param])
    counters["params_after"] = len(new_pmap)
    reg["parameterMappings"] = new_pmap

    # parameterErrors size + reason caps.
    perrs = reg.get("parameterErrors") or {}
    for endpoint, by_param in list(perrs.items()):
        if not isinstance(by_param, dict):
            continue
        for pname, errors in list(by_param.items()):
            if not isinstance(errors, list):
                continue
            for e in errors:
                if isinstance(e, dict) and isinstance(e.get("errorReason"), str):
                    if len(e["errorReason"]) > MAX_REASON_CHARS:
                        e["errorReason"] = e["errorReason"][:MAX_REASON_CHARS] + "..."
                        counters["reason_truncated"] += 1
            if len(errors) > MAX_ERRORS_PER_PARAM:
                counters["errors_capped"] += len(errors) - MAX_ERRORS_PER_PARAM
                by_param[pname] = errors[-MAX_ERRORS_PER_PARAM:]
    return counters


def main(argv: List[str]) -> int:
    target = Path(argv[1]) if len(argv) > 1 else DEFAULT_REGISTRY
    if not target.exists():
        print(f"ERROR: registry not found: {target}", file=sys.stderr)
        return 2
    backup = target.with_suffix(f".{_dt.datetime.now().strftime('%Y%m%d%H%M%S')}.bak.yaml")
    print(f"Backup: {target} → {backup}")
    shutil.copy2(target, backup)
    print(f"Loading: {target} ({os.path.getsize(target):,} bytes)")
    reg = load_yaml(target)
    counters = migrate(reg)
    dump_yaml(reg, target)
    print()
    print("Migration counters:")
    for k, v in counters.items():
        print(f"  {k}: {v:,}")
    print()
    print(f"Wrote: {target} ({os.path.getsize(target):,} bytes)")
    print(f"Original preserved at: {backup}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
