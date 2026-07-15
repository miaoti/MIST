#!/bin/bash
# S3 wild-hunt — TeaStore runner (plan rev 2.1 §6 P2). NEVER `GET /rest/generatedb` (DB wipe).
# usage: bash teastore.sh <revive|calibration|window>
#   revive       scale teastore 0->1 (persistence AUTO-SEEDS a fresh empty db: 100 users, products, cats)
#   calibration  20 benign writes, §3 FP gate (expect present=20, CONFIRMED=0)
#   window       500 acked writes (background this call — but TeaStore is sync+local, ~1-2 min)
set -u
REPO="C:/Users/miaot/Github/MIST"; cd "$REPO"
NS=teastore; KC="wsl kubectl --context kind-mist"
MODE="${1:-calibration}"

if [ "$MODE" = "revive" ]; then
  $KC -n $NS scale deploy --all --replicas=1
  for i in $(seq 1 24); do sleep 10
    R=$($KC -n $NS get deploy --no-headers 2>/dev/null | awk '{split($2,a,"/"); if(a[1]==a[2]&&a[2]!="0")n++} END{print n+0}')
    T=$($KC -n $NS get deploy --no-headers 2>/dev/null | wc -l)
    echo "t=${i}0s ready=$R/$T"; [ "$R" -ge "$T" ] && { echo ALL_READY; break; }
  done
  exit 0
fi

$KC -n $NS port-forward svc/teastore-webui 8082:8080 >/tmp/pf-wui.log 2>&1 &
PF1=$!
$KC -n $NS port-forward svc/teastore-persistence 8083:8080 >/tmp/pf-per.log 2>&1 &
PF2=$!
trap "kill $PF1 $PF2 2>/dev/null" EXIT
sleep 8
HEAD=$(git rev-parse --short HEAD)
CP="mist-cli/target/classes;mist-core/target/classes;$(cat cp.txt)"
N=$([ "$MODE" = window ] && echo 500 || echo 20)
java -cp "$CP" \
  -Ds3.webui=http://localhost:8082/tools.descartes.teastore.webui \
  -Ds3.persistence=http://localhost:8083/tools.descartes.teastore.persistence \
  -Ds3.mode=$MODE -Ds3.journeys=$N -Ds3.userbase=22 -Ds3.userspan=40 \
  -Ds3.out=debug/a-main/benchmark/b4/s3/${MODE}-teastore \
  -Ds3.triple=evaluation/suts/teastore/triples/teastore-order-triple.yaml \
  -Ds3.mist.commit="$HEAD" -Ds3.envguard="autoseed-100users;maintenance-false;sync-SUT" \
  io.mist.cli.s3.TeaStoreWildHunt 2>&1 | grep -viE "StatusConsoleListener|Unable to rename"
