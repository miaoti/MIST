#!/usr/bin/env python3
"""E1 DoD gate (committed per post-review A-2/C-3 so the claimed mechanical
gate is a reproducible artifact): (i) strict-Swagger-2.0-validate the two E1
authored specs offline; (ii) coverage gate over EVERY corpus-bound endpoint —
both entry_endpoint.path AND readback.locator — for TeaStore + OTel-Demo.

Normalization rules (why a naive grep is NOT enough): the spec path set is
basePath-joined (oteldemo basePath=/api); query strings are stripped; {param}
segments normalize to a placeholder. readback.locator is prose — the FIRST
"METHOD /path" token is the read-back of record (later path tokens are
corroborating-probe shorthand, often context-path-less); a locator containing
SELECT is an SQL probe, satisfied by the honest externalDocs SQL note (NOT a
fabricated GET) per RESULT-e1r2."""
import glob, json, os, re, sys

import yaml
from openapi_spec_validator import validate

_HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(_HERE, "..", "..", ".."))
SPECS = {
    "teastore": os.path.join(ROOT, "evaluation", "suts", "teastore", "openapi", "teastore-swagger.yaml"),
    "oteldemo": os.path.join(ROOT, "evaluation", "suts", "oteldemo", "openapi", "oteldemo-swagger.yaml"),
}
CASES = os.path.join(_HERE, "cases")


def norm_path(p):
    p = p.split("?")[0].strip().rstrip("/")
    return re.sub(r"\{[^}]+\}", "<P>", p)


def spec_fullpaths(spec):
    base = (spec.get("basePath") or "").rstrip("/")
    return {norm_path(base + path) for path in spec.get("paths", {})}


def extract_locator_path(loc):
    if re.search(r"\bSELECT\b", loc, re.I):
        return None  # SQL probe: covered by the externalDocs SQL note
    m = re.search(r"\b(?:GET|POST|PUT|DELETE)\s+(/[^\s,()]+)", loc)
    if m:
        return m.group(1)
    m = re.search(r"(/[^\s,()]+)", loc)
    return m.group(1) if m else None


def main():
    ok = True
    covered = {}
    for name, path in SPECS.items():
        doc = yaml.safe_load(open(path, encoding="utf-8"))
        try:
            validate(doc)
            print("[VALIDATE] %s: PASS (swagger %s)" % (name, doc.get("swagger")))
        except Exception as e:
            ok = False
            print("[VALIDATE] %s: FAIL -> %s: %s" % (name, type(e).__name__, str(e)[:200]))
        covered[name] = spec_fullpaths(doc)

    missing = []
    for f in sorted(glob.glob(os.path.join(CASES, "*.json"))):
        d = json.load(open(f, encoding="utf-8"))
        sut = (d.get("sut") or {}).get("name")
        if sut not in covered:
            continue
        t = d.get("target") or {}
        checks = []
        ep = (t.get("entry_endpoint") or {}).get("path")
        if ep:
            checks.append(("entry", norm_path(ep)))
        loc = (t.get("readback") or {}).get("locator")
        if loc:
            p = extract_locator_path(loc)
            if p is None:
                print("  OK(sql-note) %s [readback]" % os.path.basename(f))
            else:
                checks.append(("readback", norm_path(p)))
        for kind, np in checks:
            hit = np in covered[sut]
            print("  %s %s [%s] %s" % ("OK" if hit else "MISSING", os.path.basename(f), kind, np))
            if not hit:
                missing.append((os.path.basename(f), kind, np))

    if missing:
        ok = False
        print("MISSING coverage:", missing)
    else:
        print("coverage: every bound entry + non-SQL readback endpoint is covered")
    print("OVERALL:", "PASS" if ok else "FAIL")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
