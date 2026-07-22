#!/bin/bash
# Depdown live-upgrade runner (git-bash) — the single reproduction entry point.
# PREREG: b4/RESULT-depdown-live.md §1 (committed before this runs).
# Does: classpath ensure -> TeaStore bring-up (PVC verified) -> the MIST paired run (java, launch
# line echoed verbatim into the log) -> ground truth (direct /rest/orders, never MIST).
set -u
ROOT=/c/Users/miaot/Github/MIST
cd "$ROOT"
OUT=$ROOT/debug/a-main/benchmark/b4/cset/teastore-depdown
LOG=$OUT/mist-run.log
REPORT=$OUT/teastore-order-depdown-run.report.json
mkdir -p "$OUT"

echo "== depdown live-upgrade runner $(date -u +%FT%TZ) ==" | tee "$LOG"

# classpath (mvn dependency list; reuse mist-cli/cp.txt if present and non-empty)
if [ ! -s mist-cli/cp.txt ]; then
  echo "[runner] generating classpath via mvn dependency:build-classpath" | tee -a "$LOG"
  mvn -q -pl mist-cli -am -DskipTests compile dependency:build-classpath -Dmdep.outputFile=cp.txt >>"$LOG" 2>&1
fi
[ -s mist-cli/cp.txt ] || { echo "ABORT: no classpath" | tee -a "$LOG"; exit 2; }

# bring-up (reuses the committed ts_up script; PFs 8091 webui / 8092 persistence)
cat debug/a-main/benchmark/b4/runners/livetool/ts_up.sh | wsl bash -c "tr -d '\r' > /tmp/livetool_ts_up.sh"
MSYS_NO_PATHCONV=1 wsl bash /tmp/livetool_ts_up.sh 2>&1 | tee -a "$LOG"
echo "[runner] PVC check:" | tee -a "$LOG"
wsl bash -lc "kubectl -n teastore get pvc teastore-db-data --no-headers 2>&1" | tee -a "$LOG"
# webui warm-up
for i in $(seq 1 12); do
  ST=$(wsl bash -c "curl -s -o /dev/null -w '%{http_code}' http://localhost:8091/tools.descartes.teastore.webui/login" | tr -d '\r')
  [ "$ST" = "200" ] && { echo "[runner] webui warm (200)" | tee -a "$LOG"; break; }
  sleep 15
done

CP="mist-cli/target/classes;$(cat mist-cli/cp.txt)"
LAUNCH=(java -cp "$CP"
  -Dts.webui=http://localhost:8091/tools.descartes.teastore.webui
  -Dts.persistence=http://localhost:8092/tools.descartes.teastore.persistence
  -Dts.triple=evaluation/suts/teastore/triples/teastore-order-triple.yaml
  -Dts.probes=4
  -Dts.report="$REPORT"
  io.mist.cli.enable.TeaStoreDepdownHeadToHead)
echo "[runner] LAUNCH LINE (verbatim):" | tee -a "$LOG"
printf '  %q ' "${LAUNCH[@]}" | tee -a "$LOG"; echo | tee -a "$LOG"
"${LAUNCH[@]}" >>"$LOG" 2>&1
RC=$?
echo "[runner] java exit=$RC" | tee -a "$LOG"

# ground truth: parse the report's markers, read /rest/orders DIRECTLY per marker
{
  echo "# ground truth (DIRECT /rest/orders reads, never MIST) $(date -u +%FT%TZ)"
  echo "# method: markers parsed from the report JSON; per marker:"
  echo "#   wsl curl -s http://localhost:8092/tools.descartes.teastore.persistence/rest/orders | grep -oc <marker>"
  python - "$REPORT" <<'PY'
import json,subprocess,sys
rep=json.load(open(sys.argv[1],encoding="utf-8"))
def rd(m):
    r=subprocess.run(["wsl","bash","-c",
        f"curl -s http://localhost:8092/tools.descartes.teastore.persistence/rest/orders | grep -oc {m}"],
        capture_output=True,text=True)
    return (r.stdout or "0").strip()
rows=subprocess.run(["wsl","bash","-c",
    "curl -s http://localhost:8092/tools.descartes.teastore.persistence/rest/orders | grep -o '\"id\"' | wc -l"],
    capture_output=True,text=True).stdout.strip()
print(f"total order rows: {rows}")
for i,p in enumerate(rep.get("pairs",[])):
    for leg in ("control","fault"):
        m=(p.get(leg) or {}).get("isolationKey",{}).get("address1")
        if m: print(f"pair{i} {leg:7} marker {m} -> present-count {rd(m)}")
PY
} > "$OUT/ground-truth-depdown.txt" 2>&1
cat "$OUT/ground-truth-depdown.txt"

# teardown (tenancy): scale to 0, kill PFs
wsl bash -lc "kubectl -n teastore scale deploy --all --replicas=0 >/dev/null 2>&1; pkill -f 'port-forward.*teastore' 2>/dev/null; echo '[runner] teastore scaled to 0, PFs killed'" | tee -a "$LOG"
echo "[runner] DONE rc=$RC" | tee -a "$LOG"
