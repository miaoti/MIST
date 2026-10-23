"""Do two legs send the same requests? Builds each test's request sequence from the generated sources with the
signature of verdict_table_20261015b/source_distinct.py (method, path, query, headers, cookies, body) and
compares the multisets of the two legs, ignoring test names and run ids.
usage: python compare_requests.py <leg A generated-tests.zip> <leg B generated-tests.zip>"""
import collections
import re
import sys
import zipfile

sys.stdout.reconfigure(encoding="utf-8")
call_re = re.compile(r'req\.when\(\)\.(get|post|put|delete|patch)\("((?:[^"\\]|\\.)*)"\)')
add_re = re.compile(r'req = req\.(queryParam|header|cookie|body|contentType)\(((?:"(?:[^"\\]|\\.)*"\s*,?\s*)+)\)'
                    r'|allStepParameters\.put\("(body)",\s*("(?:[^"\\]|\\.)*")\)')


def signatures(path):
    z = zipfile.ZipFile(path)
    out = collections.Counter()
    for n in sorted(z.namelist()):
        if not n.endswith(".java"):
            continue
        t = z.read(n).decode("utf-8", errors="replace")
        for part in re.split(r"(?=public void test_(?:positive|negative)_flow_)", t)[1:]:
            kind = "negative" if part.startswith("public void test_negative") else "positive"
            reqs, pending = [], []
            events = sorted([(m.start(), "add", m) for m in add_re.finditer(part)] +
                            [(m.start(), "call", m) for m in call_re.finditer(part)], key=lambda e: e[0])
            for _, k, m in events:
                if k == "add":
                    pending.append((m.group(1), m.group(2)) if m.group(1) else (m.group(3), m.group(4)))
                else:
                    reqs.append((m.group(1).upper(), m.group(2), tuple(sorted(pending))))
                    pending = []
            out[(kind, tuple(reqs))] += 1
    return out


a, b = signatures(sys.argv[1]), signatures(sys.argv[2])
print(f"A {sum(a.values())} tests, B {sum(b.values())} tests; identical multisets: {a == b}")
print(f"requests only in A: {sum((a - b).values())}; only in B: {sum((b - a).values())}")
