"""Copy one finished round-16 Sock Shop leg into the evidence folder (layout of verdict_table_20261015b):
allure-results.zip, generated-tests.zip, driver.log, mist-<leg>.log, the fault-detection summary, verdicts.csv,
scan.csv, row.json.  usage: python stage16.py <run leg dir> <evidence leg folder name> <run id>"""
import shutil
import sys
import zipfile
from pathlib import Path

src = Path(sys.argv[1])
name, run_id = sys.argv[2], sys.argv[3]
dst = Path("C:/Users/miaot/Github/MIST/docs/main-contribution/evidence/sockshop_login_20261016") / name
dst.mkdir(parents=True, exist_ok=True)
res = src / "allure-results"
with zipfile.ZipFile(dst / "allure-results.zip", "w", zipfile.ZIP_DEFLATED) as z:
    for f in sorted(res.rglob("*")):
        if f.is_file():
            z.write(f, Path("allure-results") / f.relative_to(res))
gen = Path("C:/Users/miaot/Github/MIST/evaluation/suts/sockshop/.runtime/mist-cli/src/test/java/sockshop_logged_in") / f"SockShopTest_{run_id}"
assert gen.is_dir(), gen
with zipfile.ZipFile(dst / "generated-tests.zip", "w", zipfile.ZIP_DEFLATED) as z:
    for f in sorted(gen.rglob("*.java")):
        z.write(f, Path(f"SockShopTest_{run_id}") / f.relative_to(gen))
for n in ("driver.log", "verdicts.csv", "scan.csv", "row.json"):
    shutil.copy(src / n, dst / n)
shutil.copy(src / "mist.log", dst / f"mist-{name}.log")
for f in src.glob("fault-detection-summary-*.txt"):
    shutil.copy(f, dst / f.name)
for f in dst.iterdir():
    if f.suffix in (".log", ".txt", ".csv", ".json"):
        t = f.read_text(encoding="utf-8", errors="ignore")
        for bad in ("eyJhbGci", "sk-", "Bearer ey"):
            assert bad not in t, (f.name, bad)
print("staged", name, sorted((p.name, p.stat().st_size // 1024) for p in dst.iterdir()))
