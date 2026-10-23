"""Copy one finished round-11b leg into the evidence folder in the layout of verdict_table_20261014:
allure-results.zip, driver.log, mist-<leg>.log, the fault-detection summary, verdicts.csv, scan.csv.

usage: python stage_leg.py <run leg dir> <evidence leg folder name>
"""
import shutil
import sys
import zipfile
from pathlib import Path

src = Path(sys.argv[1])
dst = Path("C:/Users/miaot/Github/MIST/docs/main-contribution/evidence/verdict_table_20261015b") / sys.argv[2]
dst.mkdir(parents=True, exist_ok=True)
res = src / "allure-results"
with zipfile.ZipFile(dst / "allure-results.zip", "w", zipfile.ZIP_DEFLATED) as z:
    for f in sorted(res.rglob("*")):
        if f.is_file():
            z.write(f, Path("allure-results") / f.relative_to(res))
for name in ("driver.log", "verdicts.csv", "scan.csv"):
    shutil.copy(src / name, dst / name)
shutil.copy(src / "mist.log", dst / f"mist-{sys.argv[2]}.log")
for f in src.glob("fault-detection-summary-*.txt"):
    shutil.copy(f, dst / f.name)
# no token or key in a public evidence file
for f in dst.iterdir():
    if f.suffix in (".log", ".txt", ".csv"):
        t = f.read_text(encoding="utf-8", errors="ignore")
        for bad in ("eyJhbGci", "sk-", "Bearer ey"):
            assert bad not in t, (f.name, bad)
print("staged", dst, sorted(p.name for p in dst.iterdir()))
