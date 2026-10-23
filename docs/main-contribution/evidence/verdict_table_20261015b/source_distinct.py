"""Distinct requests among the generated tests of one run, read from the generated JUnit sources:
per test method, every request it builds (path parameters already in the path, query parameters,
headers, cookies, body) and the HTTP call it makes. Tests that build the same requests count once.

usage: python source_distinct.py <generated-tests.zip or test class directory> [<verdicts.csv>]
  <verdicts.csv>: optional; its first column names the executed tests, and only those are counted.
"""
import collections
import re
import sys
import zipfile
from pathlib import Path

sys.stdout.reconfigure(encoding="utf-8")
src = Path(sys.argv[1])
names = None
if len(sys.argv) > 2:
    names = {line.split(",")[0].strip() for line in open(sys.argv[2], encoding="utf-8") if line.strip()}
if src.suffix == ".zip":
    z = zipfile.ZipFile(src)
    sources = [z.read(n).decode("utf-8", errors="replace") for n in sorted(z.namelist()) if n.endswith(".java")]
else:
    sources = [f.read_text(encoding="utf-8", errors="replace") for f in sorted(src.glob("*.java"))]

call_re = re.compile(r'req\.when\(\)\.(get|post|put|delete|patch)\("((?:[^"\\]|\\.)*)"\)')
add_re = re.compile(r'req = req\.(queryParam|header|cookie|body|contentType)\(((?:"(?:[^"\\]|\\.)*"\s*,?\s*)+)\)'
                    r'|allStepParameters\.put\("(body)",\s*("(?:[^"\\]|\\.)*")\)')
sig = {}
for t in sources:
    parts = re.split(r"(?=public void test_(?:positive|negative)_flow_)", t)
    for part in parts[1:]:
        name = re.match(r"public void (test_\w+)\(", part).group(1)
        if names is not None and name not in names:
            continue
        # requests in order: the parameters added since the previous call belong to the next call
        reqs, pending = [], []
        pos = 0
        events = sorted([(m.start(), "add", m) for m in add_re.finditer(part)] +
                        [(m.start(), "call", m) for m in call_re.finditer(part)], key=lambda e: e[0])
        for _, kind, m in events:
            if kind == "add":
                # a body set through a variable is read from its recorded literal
                pending.append((m.group(1), m.group(2)) if m.group(1) else (m.group(3), m.group(4)))
            else:
                reqs.append((m.group(1).upper(), m.group(2), tuple(sorted(pending))))
                pending = []
        sig[name] = tuple(reqs)

counts = collections.Counter(sig.values())
kinds = collections.Counter("negative" if "_negative_" in n else "positive" for n in sig)
print(f"tests {len(sig)} ({dict(kinds)}) -> distinct request sequences {len(counts)}; "
      f"largest repeat {max(counts.values()) if counts else 0}; tests without a request: "
      f"{sum(1 for v in sig.values() if not v)}")
for k, c in counts.most_common(5):
    if c > 1:
        print(f"  x{c}: {str(k)[:240]}")
