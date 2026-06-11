#!/usr/bin/env bash
# Capture Online Boutique frontend traces (MIST generation input + the
# hidden-downstream oracle evidence). Drives the frontend via its port-forward
# (:8081), pulls deep frontend traces from Jaeger (:16686). Then induces the
# hidden-downstream condition (scale adservice to 0) and captures the
# swallowed-gRPC-error traces, then RESTORES adservice.
# Usage: ./capture-traces.sh
set -uo pipefail
export PATH="$HOME/.local/bin:$PATH"
FE="${FE:-http://localhost:8081}"; JAEGER="${JAEGER_BASE_URL:-http://localhost:16686/jaeger/api}"
TR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/traces"; mkdir -p "$TR"

pull() { # $1=outfile  $2=lookback  — save the most recent frontend.boutique traces (Jaeger API shape)
  curl -s --max-time 20 "$JAEGER/traces?service=frontend.boutique&limit=12&lookback=$2" -o "$TR/$1"
  python3 - "$TR/$1" <<'PY'
import json,sys
d=json.load(open(sys.argv[1])); n=len(d.get("data",[]))
errs=sum(1 for t in d.get("data",[]) for s in t.get("spans",[])
         for tg in s.get("tags",[]) if tg.get("key")=="otel.status_code" and str(tg.get("value")).upper()=="ERROR")
print(f"  saved {n} traces, {errs} ERROR span(s)")
PY
}

drive() { for i in $(seq 1 "$1"); do
  curl -s -o /dev/null --max-time 15 -H 'Cookie: shop_session-id=mist-cap' "$FE/"
  curl -s -o /dev/null --max-time 15 -H 'Cookie: shop_session-id=mist-cap' "$FE/product/OLJCESPC7Z"
done; }

echo "== 1. nominal traffic -> healthy frontend traces (generation seed) =="
drive 8; sleep 8
pull boutique_home.json 2m

echo "== 2. induce hidden-downstream: scale adservice -> 0 =="
kubectl scale deploy adservice -n boutique --replicas=0
kubectl wait --for=delete pod -l app=adservice -n boutique --timeout=60s 2>/dev/null || sleep 12
echo "   driving with adservice down (home page still 200, ads swallowed)..."
for i in $(seq 1 12); do curl -s -o /dev/null -w "   GET / -> %{http_code}\n" --max-time 15 -H 'Cookie: shop_session-id=mist-out' "$FE/"; done
sleep 9
pull boutique_adservice_outage.json 3m

echo "== 3. RESTORE adservice -> 1 =="
kubectl scale deploy adservice -n boutique --replicas=1
kubectl rollout status deploy adservice -n boutique --timeout=120s || true
echo "done. traces -> $TR/  (boutique_home.json = seed; boutique_adservice_outage.json = hidden-downstream evidence)"
echo "verify the oracle:  java -cp mist-cli/target/mist.jar evaluation/suts/bookinfo/OracleCheck.java $TR/boutique_adservice_outage.json 'GET /'"
