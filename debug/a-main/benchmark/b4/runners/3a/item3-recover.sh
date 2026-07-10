#!/bin/bash
# item-3 pipeline recovery + DECISIVE pending-vs-missing separation.
# The kafkaQueueProblems window left the accounting consumer wedged (fresh flag-off canary did not
# drain). Runbook (Phase D): rollout-restart the kafka clients. Then measure which acked orders drain
# (buffered => pending-not-missing) vs stay absent (lost at production => the swallowed publish).
set -u
Q() { local PGPOD; PGPOD=$(kubectl -n otel-demo get pods -o name | grep postgresql | head -1)
  echo "$1" | kubectl -n otel-demo exec -i "$PGPOD" -- env PGPASSWORD=otel psql -U root -d otel -t -A; }
echo "== restart kafka clients (accounting + checkout + fraud-detection) =="
kubectl -n otel-demo rollout restart deploy/accounting deploy/checkout deploy/fraud-detection
kubectl -n otel-demo rollout status deploy/accounting --timeout=120s
kubectl -n otel-demo rollout status deploy/checkout --timeout=120s
echo "== wait 90s for consumer reconnect + backlog drain =="
sleep 90
echo "== DECISIVE recheck =="
echo "-- confirmatory (order2 + its canary + the flag-off fresh canary): drain => pending, absent => lost --"
for O in 320f390b-7c94-11f1-9555-feeb0cdff4fe 69589ef0-7c94-11f1-9555-feeb0cdff4fe a1a4ce9a-7c94-11f1-9555-feeb0cdff4fe; do
  echo "$O -> rows=$(Q "SELECT count(*) FROM accounting.\"order\" WHERE order_id='$O';")"
done
echo "-- probe round 4 (expect STILL 0 = lost at production; 791c41c3 already landed) --"
for O in 68d30bc7-7c90-11f1-9555-feeb0cdff4fe 243d0229-7c91-11f1-9555-feeb0cdff4fe dfb71a2d-7c91-11f1-9555-feeb0cdff4fe 9b2415a8-7c92-11f1-9555-feeb0cdff4fe; do
  echo "$O -> rows=$(Q "SELECT count(*) FROM accounting.\"order\" WHERE order_id='$O';")"
done
echo "== post-recovery health canary (flag off) =="
NEW=$(cat /proc/sys/kernel/random/uuid)
curl -s -o /dev/null --max-time 15 -X POST -H "Content-Type: application/json" -d "{\"item\":{\"productId\":\"0PUK6V6EV0\",\"quantity\":1},\"userId\":\"$NEW\"}" "http://localhost:8085/api/cart?currencyCode=USD"
R=$(curl -s -o /tmp/i3r.json -w "%{http_code} %{time_total}" --max-time 30 -X POST -H "Content-Type: application/json" -d "{\"userId\":\"$NEW\",\"userCurrency\":\"USD\",\"email\":\"i3recover@corpus.test\",\"address\":{\"streetAddress\":\"1 Corpus Way\",\"state\":\"CA\",\"country\":\"United States\",\"city\":\"Mountain View\",\"zipCode\":\"94043\"},\"creditCard\":{\"creditCardCvv\":672,\"creditCardExpirationMonth\":1,\"creditCardExpirationYear\":2030,\"creditCardNumber\":\"4432-8015-6152-0454\"}}" "http://localhost:8085/api/checkout?currencyCode=USD")
NOID=$(python3 -c "import json;print(json.load(open('/tmp/i3r.json')).get('orderId','NONE'))" 2>/dev/null || echo PARSE)
echo "health canary ack: $R $NOID"
for i in $(seq 1 18); do
  C=$(Q "SELECT count(*) FROM accounting.\"order\" WHERE order_id='$NOID';")
  [ "$C" = "1" ] && { echo "health canary landed rows=1 at ~$((i*5))s"; break; }
  sleep 5
done
[ "${C:-0}" != "1" ] && echo "health canary STILL rows=0 at 90s -- pipeline NOT recovered, investigate"
echo "total accounting.order = $(Q 'SELECT count(*) FROM accounting."order";')"
