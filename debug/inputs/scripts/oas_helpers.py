"""
Lightweight OpenAPI 3.0 schema-lookup and JSON-Schema-style validation.

Two responsibilities:
  1. Load an OAS document and resolve $ref entries (depth-limited).
  2. For a given (method, path, parameter, location), return a JSON Schema
     object that an external validator can use.

Used by validate_d1.py. Keeps the script-side dependency surface to PyYAML +
jsonschema (no full openapi-core / prance install).
"""

from __future__ import annotations

import re
from copy import deepcopy
from typing import Any, Iterable

import yaml


MAX_RESOLVE_DEPTH = 16


def load_oas(path: str | "PathLike") -> dict:
    with open(path, "r", encoding="utf-8") as fh:
        spec = yaml.safe_load(fh)
    if not isinstance(spec, dict):
        raise ValueError("OAS file did not parse to an object")
    return spec


def _resolve_ref(ref: str, root: dict, _service: str | None = None) -> Any:
    if not ref.startswith("#/"):
        raise ValueError(f"Only local $refs are supported, got {ref!r}")
    parts = ref[2:].split("/")
    cur: Any = root
    try:
        for piece in parts:
            cur = cur[piece]
    except (KeyError, TypeError):
        # TrainTicket's spec leaves dangling refs like `api_X` while the actual
        # component key is `<service>_X`. Fall back to suffix-matching within
        # the parent component bucket.
        if len(parts) >= 3 and parts[0] == "components":
            bucket = (root.get("components") or {}).get(parts[1]) or {}
            if isinstance(bucket, dict):
                wanted = parts[2]
                suffix = wanted[len("api_"):] if wanted.startswith("api_") else wanted
                candidates = [k for k in bucket if k.endswith("_" + suffix) or k == suffix]
                if not candidates:
                    raise
                if _service:
                    normalised = _service.lower().replace("-", "")
                    candidates.sort(key=lambda k: (not k.lower().replace("-", "").startswith(normalised), k))
                cur = bucket[candidates[0]]
            else:
                raise
        else:
            raise
    return cur


def deref_schema(schema: Any, root: dict, depth: int = 0, service: str | None = None) -> Any:
    """Recursively resolve $ref nodes inside a schema. Returns a new structure
    with all $refs replaced by the referenced schemas (also dereferenced).
    Cycles are broken by depth limit. The optional `service` hint biases the
    suffix-recovery fallback when a $ref like `api_X` does not resolve directly
    (TrainTicket spec quirk — see `_resolve_ref`).
    """
    if depth > MAX_RESOLVE_DEPTH:
        return schema
    if isinstance(schema, dict):
        if "$ref" in schema:
            try:
                resolved = _resolve_ref(schema["$ref"], root, _service=service)
            except (KeyError, ValueError, TypeError):
                return {}
            return deref_schema(deepcopy(resolved), root, depth + 1, service)
        out = {}
        for k, v in schema.items():
            out[k] = deref_schema(v, root, depth + 1, service)
        return out
    if isinstance(schema, list):
        return [deref_schema(v, root, depth + 1, service) for v in schema]
    return schema


def oas_to_jsonschema(schema: Any) -> Any:
    """Translate OpenAPI 3.0 schema quirks to plain JSON Schema (Draft-7).
    The main difference is `nullable: true`. We also strip vendor extensions
    (x-*) and OpenAPI-only keywords that confuse Draft-7 validators.
    """
    if not isinstance(schema, dict):
        return schema
    out = {}
    nullable = bool(schema.get("nullable"))
    for k, v in schema.items():
        if k == "nullable":
            continue
        if k.startswith("x-"):
            continue
        if k in ("discriminator", "xml", "externalDocs", "example", "examples", "deprecated", "readOnly", "writeOnly"):
            continue
        if k in ("properties", "patternProperties"):
            out[k] = {pk: oas_to_jsonschema(pv) for pk, pv in (v or {}).items()}
        elif k == "items":
            out[k] = oas_to_jsonschema(v)
        elif k in ("oneOf", "anyOf", "allOf"):
            out[k] = [oas_to_jsonschema(s) for s in (v or [])]
        elif k == "additionalProperties":
            if isinstance(v, dict):
                out[k] = oas_to_jsonschema(v)
            else:
                out[k] = v
        else:
            out[k] = v
    if nullable:
        t = out.get("type")
        if isinstance(t, str):
            out["type"] = [t, "null"]
        elif isinstance(t, list) and "null" not in t:
            out["type"] = t + ["null"]
    return out


def normalise_path(path: str) -> str:
    """Strip query string and trailing slash. Leaves path templating intact."""
    p = path.split("?", 1)[0]
    if len(p) > 1 and p.endswith("/"):
        p = p[:-1]
    return p


def match_oas_path(actual_path: str, oas_paths: Iterable[str]) -> str | None:
    """Find the OAS path template that matches `actual_path`.
    Templates use {name} placeholders; the actual path may contain literal
    UUIDs / digits / station names where the template has placeholders.
    """
    actual = normalise_path(actual_path)
    actual_segments = [s for s in actual.split("/") if s]
    best: tuple[int, str] | None = None
    for tmpl in oas_paths:
        tmpl_norm = normalise_path(tmpl)
        tmpl_segments = [s for s in tmpl_norm.split("/") if s]
        if len(tmpl_segments) != len(actual_segments):
            continue
        ok = True
        literal_matches = 0
        for ts, asg in zip(tmpl_segments, actual_segments):
            if ts.startswith("{") and ts.endswith("}"):
                continue  # placeholder — matches anything
            if ts == asg:
                literal_matches += 1
            else:
                ok = False
                break
        if not ok:
            continue
        if best is None or literal_matches > best[0]:
            best = (literal_matches, tmpl)
    return best[1] if best else None


def get_operation(spec: dict, method: str, actual_path: str) -> tuple[str, dict] | None:
    """Return (matched_path_template, operation_object) or None."""
    paths = spec.get("paths") or {}
    method = method.lower()
    tmpl = match_oas_path(actual_path, paths.keys())
    if tmpl is None:
        return None
    path_item = paths.get(tmpl) or {}
    op = path_item.get(method)
    if not op:
        return None
    # Path-level parameters apply to all operations on the path.
    merged = deepcopy(op)
    path_level_params = path_item.get("parameters") or []
    op_level_params = op.get("parameters") or []
    if path_level_params or op_level_params:
        merged["parameters"] = list(path_level_params) + list(op_level_params)
    return tmpl, merged


def parameter_schema(spec: dict, op: dict, name: str, location: str) -> dict | None:
    """Return the dereferenced JSON Schema for a query/path/header parameter."""
    location_norm = (location or "").lower()
    service = op.get("x-service-name")
    for p in op.get("parameters") or []:
        p_resolved = deref_schema(p, spec, service=service)
        if not isinstance(p_resolved, dict):
            continue
        if p_resolved.get("name") != name:
            continue
        if (p_resolved.get("in") or "").lower() != location_norm:
            continue
        sch = p_resolved.get("schema")
        if sch is None:
            return None
        return oas_to_jsonschema(deref_schema(sch, spec, service=service))
    return None


def _find_request_body_by_suffix(spec: dict, ref_name: str, service_name: str | None) -> dict | None:
    """Several TrainTicket operations carry a stale `$ref: #/components/requestBodies/api_X`
    while the actual key is `<service>_X`. Recover gracefully: prefer matches that include the
    operation's `x-service-name`, fall back to any unique `*_X` match.
    """
    rbs = (spec.get("components") or {}).get("requestBodies") or {}
    if not rbs:
        return None
    suffix = ref_name
    if suffix.startswith("api_"):
        suffix = suffix[len("api_"):]
    candidates = [k for k in rbs.keys() if k.endswith("_" + suffix) or k == suffix]
    if not candidates:
        return None
    if service_name:
        normalised = service_name.lower().replace("-", "")
        scored = []
        for k in candidates:
            klow = k.lower().replace("-", "")
            scored.append((klow.startswith(normalised), k))
        scored.sort(key=lambda x: (not x[0], x[1]))
        return rbs[scored[0][1]]
    return rbs[candidates[0]]


def request_body_schema(spec: dict, op: dict, content_type: str = "application/json") -> dict | None:
    rb = op.get("requestBody")
    if rb is None:
        return None
    service = op.get("x-service-name")
    # The requestBody itself may be a $ref to #/components/requestBodies/...
    rb_resolved = deref_schema(rb, spec, service=service)
    if isinstance(rb_resolved, dict) and "content" not in rb_resolved:
        # Either the ref is broken (TrainTicket spec uses stale `api_X` refs) or deref bottomed
        # out at depth limit. Try the suffix-recovery fallback before giving up.
        original_ref = (rb or {}).get("$ref") or ""
        ref_name = original_ref.rsplit("/", 1)[-1] if original_ref else ""
        if ref_name:
            recovered = _find_request_body_by_suffix(spec, ref_name, op.get("x-service-name"))
            if recovered is not None:
                rb_resolved = deref_schema(recovered, spec)
    content = (rb_resolved or {}).get("content") or {}
    # Try the requested content type first, then any application/* fallback.
    candidates = [content_type] + [k for k in content.keys() if k != content_type]
    for ct in candidates:
        media = content.get(ct)
        if media and "schema" in media:
            return oas_to_jsonschema(deref_schema(media["schema"], spec, service=service))
    return None
