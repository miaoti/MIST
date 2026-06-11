#!/usr/bin/env python3
"""
Stdlib-only test runner for the D1–D10 metric scripts.

Discovers every `test_*` function in `tests/test_*.py`, runs it with a fresh
tmp_path (when the function takes one), and reports pass/fail. No external
dependencies — works in any Python 3.9+ environment.

Usage:
    python3 debug/inputs/scripts/run_tests.py
    python3 debug/inputs/scripts/run_tests.py --verbose
    python3 debug/inputs/scripts/run_tests.py --pattern test_id_helpers
"""

from __future__ import annotations

import argparse
import importlib.util
import inspect
import sys
import tempfile
import traceback
from pathlib import Path


def discover_tests(test_dir: Path, pattern: str | None) -> list[tuple[str, Path]]:
    files = sorted(test_dir.glob("test_*.py"))
    if pattern:
        files = [f for f in files if pattern in f.name]
    return [(f.stem, f) for f in files]


def load_module(name: str, path: Path):
    spec = importlib.util.spec_from_file_location(name, path)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"Could not load {path}")
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def collect_test_functions(module) -> list[tuple[str, callable]]:
    out = []
    for name, obj in inspect.getmembers(module):
        if name.startswith("test_") and callable(obj):
            out.append((name, obj))
    return out


def run(verbose: bool, pattern: str | None) -> int:
    test_dir = Path(__file__).resolve().parent / "tests"
    if not test_dir.is_dir():
        print(f"error: test directory not found: {test_dir}", file=sys.stderr)
        return 2

    # Make sure the scripts folder is on sys.path so `import id_helpers` etc. work.
    sys.path.insert(0, str(Path(__file__).resolve().parent))

    files = discover_tests(test_dir, pattern)
    if not files:
        print("no test files matched")
        return 0

    total = passed = failed = 0
    failures: list[tuple[str, str]] = []

    for stem, path in files:
        mod = load_module(stem, path)
        funcs = collect_test_functions(mod)
        for name, fn in funcs:
            total += 1
            label = f"{stem}::{name}"
            try:
                # Inject a tmp_path argument when the function asks for it
                # (mirrors pytest's tmp_path fixture).
                sig = inspect.signature(fn)
                kwargs = {}
                if "tmp_path" in sig.parameters:
                    tmp = Path(tempfile.mkdtemp(prefix="metric_test_"))
                    kwargs["tmp_path"] = tmp
                fn(**kwargs)
                passed += 1
                if verbose:
                    print(f"  ✅ {label}")
            except AssertionError as e:
                failed += 1
                tb = traceback.format_exc()
                failures.append((label, tb))
                print(f"  ❌ {label}: {e}")
            except Exception as e:
                failed += 1
                tb = traceback.format_exc()
                failures.append((label, tb))
                print(f"  ❌ {label}: ERROR {e!r}")

    print()
    print(f"{passed}/{total} passed, {failed} failed")
    if failures and verbose:
        print()
        print("--- Failures ---")
        for label, tb in failures:
            print(f"\n[{label}]")
            print(tb)
    return 0 if failed == 0 else 1


def main(argv: list[str]) -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--verbose", "-v", action="store_true")
    p.add_argument("--pattern", help="Only run test files whose name contains this substring")
    args = p.parse_args(argv)
    return run(args.verbose, args.pattern)


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
