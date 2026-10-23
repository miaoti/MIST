"""Per leg: negative tests, distinct request paths they sent, how many still requested a captured valid
id, and the status codes they got. usage: python negatives.py <runs dir> <leg> [<leg> ...]"""
import collections
import csv
import re
import sys
from pathlib import Path

VALID = {"0", "0PUK6V6EV0", "1YMWWN1N4O", "2ZYFJ3GM2N", "66VCHSJNUP", "6E92ZMYYFZ", "9SIQT8TOJO",
         "L9ECAV7KIM", "LS4PSXUNUM", "OLJCESPC7Z"}
runs = Path(sys.argv[1])
for leg in sys.argv[2:]:
    V = list(csv.DictReader(open(runs / leg / "verdicts.csv", encoding="utf-8")))
    neg = [r for r in V if r["kind"] == "negative"]
    def seg(e):
        m = re.search(r"/products?/([^/?]*)", e.split("?")[0])
        return m.group(1) if m else None
    still = sum(1 for r in neg if seg(r["entry"]) in VALID)
    st = collections.Counter(r["client_status"] for r in neg)
    print(f"{leg}: {len(neg)} negative tests, {len(set(r['entry'] for r in neg))} distinct request paths, "
          f"{still} on a captured valid id; statuses " + ", ".join(f"{k} x{v}" for k, v in sorted(st.items())))
