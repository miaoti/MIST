#!/bin/bash
# Completion-set wave Phase C — the kafka S1 SECOND ATTEMPT (rev-2 pinned; the first attempt
# STOPPED: 7/8 orders permanently lost, wedge persisted past flag-off - tenancy item 3).
# X4 conventions: control-leg-FIRST N>=10 off -> N>=20 on; per-trial T+5min RE-PROBE
# (delayed-but-landed != LOST); flag flips via the flagd-ui API mechanism of record
# (flagd-toggle.sh verbatim); poisoning-window drain + health canary + recovery-restart
# (checkout+accounting+fraud) + NO THIRD ATTEMPT; ground truth = direct psql (never MIST).
#   usage: kafka-s1.sh <control|faulton|reprobe|restore|canary|recover>
set -u
NS=otel-demo
B4=/mnt/c/Users/miaot/Github/MIST/debug/a-main/benchmark/b4
EV=$B4/cset/kafka-s1
mkdir -p "$EV"
FRONT="http://localhost:8085"
TOGGLE=$B4/runners/3a/flagd-toggle.sh
PGPOD=$(kubectl -n $NS get pods -o name 2>/dev/null | grep postgresql | head -1)
psqlq() { echo "$1" | kubectl -n $NS exec -i "$PGPOD" -- env PGPASSWORD=otel psql -U root -d otel -t -A; }
STAMP=$(date +%H:%M:%S)

place() { # $1=session $2=marker -> "http_code time_total orderId"
  local S=$1 M=$2 R OID
  curl -s -o /dev/null --max-time 15 -X POST -H "Content-Type: application/json" \
    -d "{\"item\":{\"productId\":\"0PUK6V6EV0\",\"quantity\":1},\"userId\":\"$S\"}" \
    "$FRONT/api/cart?currencyCode=USD"
  R=$(curl -s -o /tmp/ks1-place.json -w "%{http_code} %{time_total}" --max-time 30 -X POST -H "Content-Type: application/json" \
    -d "{\"userId\":\"$S\",\"userCurrency\":\"USD\",\"email\":\"$M@corpus.test\",\"address\":{\"streetAddress\":\"1 Corpus Way\",\"state\":\"CA\",\"country\":\"United States\",\"city\":\"Mountain View\",\"zipCode\":\"94043\"},\"creditCard\":{\"creditCardCvv\":672,\"creditCardExpirationMonth\":1,\"creditCardExpirationYear\":2030,\"creditCardNumber\":\"4432-8015-6152-0454\"}}" \
    "$FRONT/api/checkout?currencyCode=USD")
  OID=$(python3 -c "import json;print(json.load(open('/tmp/ks1-place.json')).get('orderId','NONE'))" 2>/dev/null || echo PARSE_FAIL)
  echo "$R $OID"
}
rowcount() { psqlq "SELECT count(*) FROM accounting.\"order\" WHERE order_id='$1';"; }

case "${1:?subcommand}" in
control)
  # N=10 flag-OFF trials, paced 5 s; initial landed-sweep (up to 120 s each) recorded.
  echo "[$STAMP] CONTROL leg: 10 trials flag-OFF" | tee -a $EV/control.log
  bash $TOGGLE kafkaQueueProblems status 2>&1 | tail -2 | tee -a $EV/control.log
  : > $EV/control-orders.txt
  for i in $(seq 1 10); do
    M="ks1c${i}-$(date +%s)"
    R=$(place "ks1-ctl-$i" "$M")
    echo "trial=$i marker=$M resp=$R" | tee -a $EV/control.log
    echo "$R" | awk -v m=$M '{print m, $3}' >> $EV/control-orders.txt
    sleep 5
  done
  echo "-- landed sweep --" | tee -a $EV/control.log
  while read -r M OID; do
    for t in $(seq 1 24); do C=$(rowcount "$OID"); [ "$C" != "0" ] && [ -n "$C" ] && break; sleep 5; done
    echo "ctl $M $OID rows=$C" | tee -a $EV/control.log
  done < $EV/control-orders.txt
  ;;
faulton)
  echo "[$STAMP] FAULT leg: flag ON + 20 trials" | tee -a $EV/fault.log
  bash $TOGGLE kafkaQueueProblems on 2>&1 | tail -3 | tee -a $EV/fault.log
  : > $EV/fault-orders.txt
  for i in $(seq 1 20); do
    M="ks1f${i}-$(date +%s)"
    R=$(place "ks1-flt-$i" "$M")
    echo "trial=$i marker=$M resp=$R" | tee -a $EV/fault.log
    echo "$R" | awk -v m=$M '{print m, $3}' >> $EV/fault-orders.txt
    sleep 5
  done
  echo "-- initial landed sweep (60 s/order cap) --" | tee -a $EV/fault.log
  while read -r M OID; do
    for t in $(seq 1 12); do C=$(rowcount "$OID"); [ "$C" != "0" ] && [ -n "$C" ] && break; sleep 5; done
    echo "flt $M $OID rows=$C" | tee -a $EV/fault.log
  done < $EV/fault-orders.txt
  echo "[$(date +%H:%M:%S)] initial sweep done; run 'reprobe' at T+5min per X4" | tee -a $EV/fault.log
  ;;
reprobe)
  # The BINDING T+5min re-probe: delayed-but-landed != LOST.
  echo "[$STAMP] T+5min RE-PROBE of fault trials" | tee -a $EV/fault.log
  LOST=0; N=0
  while read -r M OID; do
    N=$((N+1)); C=$(rowcount "$OID")
    S="LANDED"; [ "$C" = "0" ] && { S="LOST"; LOST=$((LOST+1)); }
    echo "reprobe $M $OID rows=$C -> $S" | tee -a $EV/fault.log
  done < $EV/fault-orders.txt
  echo "SUMMARY: lost=$LOST / N=$N" | tee -a $EV/fault.log
  ;;
restore)
  echo "[$STAMP] flag OFF + restore verify" | tee -a $EV/restore.log
  bash $TOGGLE kafkaQueueProblems off 2>&1 | tail -4 | tee -a $EV/restore.log
  ;;
canary)
  # poisoning-window canary: one clean order must land end-to-end (120 s cap).
  M="ks1canary-$(date +%s)"
  R=$(place "ks1-canary" "$M")
  OID=$(echo "$R" | awk '{print $3}')
  echo "[$STAMP] canary resp=$R" | tee -a $EV/restore.log
  for t in $(seq 1 24); do C=$(rowcount "$OID"); [ "$C" != "0" ] && [ -n "$C" ] && break; sleep 5; done
  echo "canary $OID rows=$C $([ "$C" != "0" ] && echo PASS || echo FAIL)" | tee -a $EV/restore.log
  ;;
recover)
  # the pinned recovery-restart: checkout + accounting + fraud (the flag wedges rdkafka).
  echo "[$STAMP] recovery-restart checkout+accounting+fraud" | tee -a $EV/restore.log
  kubectl -n $NS rollout restart deploy checkout accounting fraud-detection 2>&1 | tee -a $EV/restore.log
  kubectl -n $NS rollout status deploy/checkout --timeout=180s 2>&1 | tail -1 | tee -a $EV/restore.log
  kubectl -n $NS rollout status deploy/accounting --timeout=180s 2>&1 | tail -1 | tee -a $EV/restore.log
  kubectl -n $NS rollout status deploy/fraud-detection --timeout=180s 2>&1 | tail -1 | tee -a $EV/restore.log
  ;;
*) echo "unknown subcommand"; exit 2;;
esac
