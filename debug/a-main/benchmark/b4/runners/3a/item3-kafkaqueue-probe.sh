#!/bin/bash
# wave-3a ITEM 3 PROBE ROUND (plan rev 2.1 §3, B-F8 artifacts; <=1 h timebox):
# pre-flag canary (T2/B-F9 baseline) -> flag ON (1-P0 mechanism) -> 4 probe orders with per-order
# placed->landed delay measurement -> dedupe/duplicate-volume evidence (accounting logs) ->
# kafka-stability check (B-F7) -> flag OFF (restore verify) -> DECISION LINE vs the margin rule:
#   AUTHOR iff MIN observed delay >= 2x the ack->export-close lag (pinned: lag ~= 12 s -> margin 24 s)
#   NOT-AUTHORED if delay < margin (nothing distinguishes at the pinned horizon)
#   STOP if any probe order is genuinely LOST (rows never land) -> dated survey correction + S1-candidate decision point
set -u
cd /mnt/c/Users/miaot/Github/MIST/debug/a-main/benchmark/b4
FRONT="http://localhost:8085"
TOGGLE=/tmp/flagd-toggle.sh
PGPOD=$(kubectl -n otel-demo get pods -o name | grep postgresql | head -1)
psqlq() { echo "$1" | kubectl -n otel-demo exec -i "$PGPOD" -- env PGPASSWORD=otel psql -U root -d otel -t -A; }
place() { # $1=session $2=marker -> "code time_total orderId"
  local S=$1 M=$2 R OID
  curl -s -o /dev/null --max-time 15 -X POST -H "Content-Type: application/json" \
    -d "{\"item\":{\"productId\":\"0PUK6V6EV0\",\"quantity\":1},\"userId\":\"$S\"}" \
    "$FRONT/api/cart?currencyCode=USD"
  R=$(curl -s -o /tmp/i3-place.json -w "%{http_code} %{time_total}" --max-time 30 -X POST -H "Content-Type: application/json" \
    -d "{\"userId\":\"$S\",\"userCurrency\":\"USD\",\"email\":\"$M@corpus.test\",\"address\":{\"streetAddress\":\"1 Corpus Way\",\"state\":\"CA\",\"country\":\"United States\",\"city\":\"Mountain View\",\"zipCode\":\"94043\"},\"creditCard\":{\"creditCardCvv\":672,\"creditCardExpirationMonth\":1,\"creditCardExpirationYear\":2030,\"creditCardNumber\":\"4432-8015-6152-0454\"}}" \
    "$FRONT/api/checkout?currencyCode=USD")
  OID=$(python3 -c "import json;print(json.load(open('/tmp/i3-place.json')).get('orderId','NONE'))" 2>/dev/null || echo PARSE_FAIL)
  echo "$R $OID"
}
wait_landed() { # $1=orderId -> prints seconds-to-land or TIMEOUT (300 s cap); also rowcount at end
  local OID=$1 T0 C
  T0=$(date +%s)
  for i in $(seq 1 60); do
    C=$(psqlq "SELECT count(*) FROM accounting.\"order\" WHERE order_id='$OID';")
    if [ "$C" != "0" ] && [ -n "$C" ]; then echo "$(( $(date +%s) - T0 )) rows=$C"; return 0; fi
    sleep 5
  done
  echo "TIMEOUT rows=0"
}

echo "== margin pin: ack->export-close lag ~= 12 s (driver obs 3s+5s + T1 +2s + overhead) -> AUTHOR threshold = 24 s minimum delay =="
KRESTARTS0=$(kubectl -n otel-demo get pods --no-headers | awk '$1 ~ /^kafka/ {print $4}')
echo "kafka restarts at start: ${KRESTARTS0:-?}"

echo "== 0) pre-flag canary (T2/B-F9 baseline; flag verified off) =="
bash $TOGGLE kafkaQueueProblems status
S=$(cat /proc/sys/kernel/random/uuid)
OUT=$(place "$S" i3base)
OID=$(echo "$OUT" | awk '{print $3}')
echo "baseline order: $OUT"
echo "baseline landing: $(wait_landed $OID)"

echo "== 1) flag ON =="
bash $TOGGLE kafkaQueueProblems on || exit 2

echo "== 2) 4 probe orders, per-order delay =="
DELAYS=""
LOST=0
for i in 1 2 3 4; do
  S=$(cat /proc/sys/kernel/random/uuid)
  OUT=$(place "$S" i3prb$i)
  OID=$(echo "$OUT" | awk '{print $3}')
  L=$(wait_landed $OID)
  echo "probe $i: ack=$OUT landing=$L"
  echo "$L" | grep -q TIMEOUT && LOST=$((LOST+1))
  DELAYS="$DELAYS $(echo "$L" | awk '{print $1}')"
done
echo "delay distribution (s):$DELAYS  lost=$LOST/4"

echo "== 3) dedupe / duplicate-volume evidence (accounting + checkout logs) =="
kubectl -n otel-demo logs deploy/accounting --tail=200 2>&1 | grep -c -i "duplicate\|unique\|violat" | sed "s/^/accounting dup-skip log lines: /"
kubectl -n otel-demo logs deploy/checkout --tail=200 2>&1 | grep -c -i "queue\|flood\|kafkaQueueProblems" | sed "s/^/checkout flood log lines: /"
for i in 1 2 3 4; do :; done
echo "row multiplicity check (any order_id with >1 row): $(psqlq 'SELECT count(*) FROM (SELECT order_id FROM accounting."order" GROUP BY order_id HAVING count(*)>1) d;')"

echo "== 4) kafka stability (B-F7) =="
KRESTARTS1=$(kubectl -n otel-demo get pods --no-headers | awk '$1 ~ /^kafka/ {print $4}')
echo "kafka restarts now: ${KRESTARTS1:-?} (leg INVALID if changed)"
echo "fraud-detection lag note: $(kubectl -n otel-demo logs deploy/fraud-detection --tail=5 2>&1 | grep -c -i "order")"

echo "== 5) flag OFF (restore verify) =="
bash $TOGGLE kafkaQueueProblems off || exit 3

echo "== 6) DECISION INPUTS =="
echo "margin=24s; delays:$DELAYS; lost=$LOST/4; kafka-restart-delta: ${KRESTARTS0:-?} -> ${KRESTARTS1:-?}"
echo "DECISION RULE: lost>0 => STOP; min(delay)>=24 => AUTHOR; else NOT-AUTHORED"
