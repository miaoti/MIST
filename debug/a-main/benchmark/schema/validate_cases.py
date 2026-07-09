#!/usr/bin/env python3
"""Validate benchmark case files against fault-case.schema.json (rev 2).

usage: validate_cases.py [case.json ...]   (default: all of ../cases/*.json)
Exit 0 iff every file validates. v0.1.0 cases fail until migrated to rev 2
(the corpus-assembly wave, checklist §1.95.2b).
"""
import json, sys, glob, os

try:
    import jsonschema
except ImportError:
    sys.exit("need jsonschema: pip install jsonschema")

HERE = os.path.dirname(os.path.abspath(__file__))
SCHEMA = json.load(open(os.path.join(HERE, "fault-case.schema.json"), encoding="utf-8"))
V = jsonschema.Draft202012Validator(SCHEMA)


def main(argv):
    files = argv or sorted(glob.glob(os.path.join(HERE, "..", "cases", "*.json")))
    ok = True
    for f in sorted(files):
        d = json.load(open(f, encoding="utf-8"))
        errs = sorted(V.iter_errors(d), key=lambda e: list(e.path))
        if errs:
            ok = False
            print("FAIL %s:" % os.path.basename(f))
            for e in errs[:6]:
                print("   - %s @ %s" % (e.message[:120], "/".join(map(str, e.path)) or "<root>"))
        else:
            gt = d.get("ground_truth", {})
            print("OK   %s  (v%s, stratum=%s, label=%s, %s)"
                  % (os.path.basename(f), d.get("schema_version"), d.get("stratum"),
                     gt.get("label"), d.get("capture_status")))
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
