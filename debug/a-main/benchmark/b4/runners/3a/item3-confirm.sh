#!/bin/bash
# item-3 CONFIRMATORY pass: rule out a transient behind the "permanent loss under kafkaQueueProblems"
# finding. 2 orders under-flag -> flag off + restore verify -> wait for drain -> confirm the 2 stay
# absent while a fresh post-restore order lands (per-partition-FIFO argument: a newer order draining
# while older acked orders do not = the older ones are not sitting ahead in the topic = lost).
set -u
FRONT="http://localhost:8085"
TOGGLE=/tmp/flagd-toggle.sh
PGPOD=$(kubectl -n otel-demo get pods -o name | grep postgresql | head -1)
Q() { echo "$1" | kubectl -n otel-demo exec -i "$PGPOD" -- env PGPASSWORD=otel psql -U root -d otel -t -A; }
place() { local S=$1 M=$2 R OID
  curl -s -o /dev/null --max-time 15 -X POST -H "Content-Type: application/json" -d "{\"item\":{\"productId\":\"0PUK6V6EV0\",\"quantity\":1},\"userId\":\"$S\"}" "$FRONT/api/cart?currencyCode=USD"
  R=$(curl -s -o /tmp/i3c.json -w "%{http_code} %{time_total}" --max-time 30 -X POST -H "Content-Type: application/json" -d "{\"userId\":\"$S\",\"userCurrency\":\"USD\",\"email\":\"$M@corpus.test\",\"address\":{\"streetAddress\":\"1 Corpus Way\",\"state\":\"CA\",\"country\":\"United States\",\"city\":\"Mountain View\",\"zipCode\":\"94043\"},\"creditCard\":{\"creditCardCvv\":672,\"creditCardExpirationMonth\":1,\"creditCardExpirationYear\":2030,\"creditCardNumber\":\"4432-8015-6152-0454\"}}" "$FRONT/api/checkout?currencyCode=USD")
  OID=$(python3 -c "import json;print(json.load(open('/tmp/i3c.json')).get('orderId','NONE'))" 2>/dev/null || echo PARSE_FAIL)
  echo "$R $OID"
}
K0=$(kubectl -n otel-demo get pods --no-headers | awk '$1 ~ /^kafka/ {print $4}')
echo "kafka restarts start: $K0"
bash $TOGGLE kafkaQueueProblems on || exit 2
O1=$(place $(cat /proc/sys/kernel/random/uuid) i3cf1); echo "cf order1: $O1"
O2=$(place $(cat /proc/sys/kernel/random/uuid) i3cf2); echo "cf order2: $O2"
OID1=$(echo "$O1" | awk '{print $3}'); OID2=$(echo "$O2" | awk '{print $3}')
sleep 30
echo "under-flag @30s: o1=$(Q "SELECT count(*) FROM accounting.\"order\" WHERE order_id='$OID1';") o2=$(Q "SELECT count(*) FROM accounting.\"order\" WHERE order_id='$OID2';")"
bash $TOGGLE kafkaQueueProblems off || exit 3
echo "flag off; waiting 60s for drain..."
sleep 60
PC=$(place $(cat /proc/sys/kernel/random/uuid) i3cfpost); echo "post-restore canary: $PC"
OIDP=$(echo "$PC" | awk '{print $3}')
sleep 25
K1=$(kubectl -n otel-demo get pods --no-headers | awk '$1 ~ /^kafka/ {print $4}')
echo "== CONFIRM RESULT =="
echo "kafka restarts: $K0 -> $K1"
echo "under-flag order1 $OID1 -> rows=$(Q "SELECT count(*) FROM accounting.\"order\" WHERE order_id='$OID1';")"
echo "under-flag order2 $OID2 -> rows=$(Q "SELECT count(*) FROM accounting.\"order\" WHERE order_id='$OID2';")"
echo "post-restore canary $OIDP -> rows=$(Q "SELECT count(*) FROM accounting.\"order\" WHERE order_id='$OIDP';")"
echo "restore equality re-verify:"; bash $TOGGLE kafkaQueueProblems status
