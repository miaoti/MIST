#!/bin/bash
# S3 wild-hunt — OTel-Demo runner (plan rev 2.1 §6 P1; freeze §6 Step-5-as-amended).
# The Windows JVM hits the tenant via WSL `kubectl port-forward` backgrounded IN THIS SHELL (a
# detached PF dies); SqlDurableReadback shells `wsl kubectl exec ... psql` for the read-back.
#
# usage: bash oteldemo.sh <revive|canary|calibration|window>
#   revive       scale otel-demo 0->1 (load-generator is not a deployment => stays off)
#   canary       traceparent-adoption + read-back-target + flagd-guard preflight
#   calibration  20 benign writes, §3 double-bar FP gate (expect present=20, CONFIRMED=0)
#   window       500 acked writes, the counted M-prevalence window (background this call)
set -u
REPO="C:/Users/miaot/Github/MIST"; cd "$REPO"
NS=otel-demo; KC="wsl kubectl --context kind-mist"
MODE="${1:-canary}"

if [ "$MODE" = "revive" ]; then
  $KC -n $NS scale deploy --all --replicas=1
  for i in $(seq 1 30); do sleep 10
    R=$($KC -n $NS get deploy --no-headers 2>/dev/null | awk '{split($2,a,"/"); if(a[1]==a[2]&&a[2]!="0")n++} END{print n+0}')
    T=$($KC -n $NS get deploy --no-headers 2>/dev/null | wc -l)
    echo "t=${i}0s ready=$R/$T"; [ "$R" -ge "$T" ] && { echo ALL_READY; break; }
  done
  exit 0
fi

# PFs for the JVM (frontend-proxy 8085, jaeger 16687) — same shell as the consumer.
$KC -n $NS port-forward svc/frontend-proxy 8085:8080 >/tmp/pf-fe.log 2>&1 &
PF1=$!
$KC -n $NS port-forward svc/jaeger 16687:16686 >/tmp/pf-jg.log 2>&1 &
PF2=$!
trap "kill $PF1 $PF2 2>/dev/null" EXIT
sleep 8
PGPOD=$($KC -n $NS get pods -o name 2>/dev/null | grep postgresql | head -1 | sed 's|pod/||' | tr -d '\r')
HEAD=$(git rev-parse --short HEAD)
CP="mist-cli/target/classes;mist-core/target/classes;$(cat cp.txt)"
GUARD="flagd-15-off;loadgen-absent;traceparent-adopted-57spans"

case "$MODE" in
  canary)
    # lightweight preflight (see s3-p0-pins.md §4 / RESULT-p1-oteldemo.md for the recorded pass)
    TID=$(cat /proc/sys/kernel/random/uuid | tr -d '-'); SESS=$(cat /proc/sys/kernel/random/uuid); M="corpuscnry$(date +%s)"
    curl -s -o /dev/null -X POST -H "Content-Type: application/json" -d "{\"item\":{\"productId\":\"0PUK6V6EV0\",\"quantity\":1},\"userId\":\"$SESS\"}" "http://localhost:8085/api/cart?currencyCode=USD"
    curl -s -o /tmp/cnry.json -w "checkout=%{http_code}\n" -X POST -H "Content-Type: application/json" -H "traceparent: 00-${TID}-$(echo $TID|cut -c1-16)-01" -d "{\"userId\":\"$SESS\",\"userCurrency\":\"USD\",\"email\":\"$M@x.test\",\"address\":{\"streetAddress\":\"$M\",\"state\":\"CA\",\"country\":\"US\",\"city\":\"MV\",\"zipCode\":\"94043\"},\"creditCard\":{\"creditCardCvv\":672,\"creditCardExpirationMonth\":1,\"creditCardExpirationYear\":2030,\"creditCardNumber\":\"4432-8015-6152-0454\"}}" "http://localhost:8085/api/checkout?currencyCode=USD"
    sleep 12
    echo "shipping-marker: $(echo "SELECT count(*) FROM accounting.shipping WHERE street_address='$M';" | $KC -n $NS exec -i "$PGPOD" -- env PGPASSWORD=otel psql -U root -d otel -t -A 2>/dev/null)"
    curl -s "http://localhost:16687/jaeger/ui/api/traces/$TID" | python3 -c "import json,sys;d=json.load(sys.stdin);print('traceparent-adopted spans=',sum(len(t['spans']) for t in d.get('data',[])) if d.get('data') else 'ABSENT')"
    ;;
  calibration|window)
    N=$([ "$MODE" = window ] && echo 500 || echo 20)
    java -cp "$CP" \
      -Ds3.base=http://localhost:8085 -Ds3.pgpod="$PGPOD" -Ds3.jaeger=http://localhost:16687 \
      -Ds3.mode=$MODE -Ds3.journeys=$N \
      -Ds3.out=debug/a-main/benchmark/b4/s3/${MODE}-oteldemo \
      -Ds3.triple=evaluation/suts/oteldemo/triples/oteldemo-checkout-triple.yaml \
      -Ds3.mist.commit="$HEAD" -Ds3.envguard="$GUARD" \
      io.mist.cli.s3.OtelWildHunt 2>&1 | grep -viE "StatusConsoleListener|Unable to rename"
    ;;
esac
