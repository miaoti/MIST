#!/bin/bash
# Wave R1d — induced eventual-present (bounded-eventual-consistency) benign capture, OTel-Demo.
# Shape S-B/eventual-present: order acked HTTP 200, accounting CONSUMER scaled to 0 so the kafka
# message BUFFERS (durable) and accounting.shipping stays ABSENT past MIST's 25s cap (a MIST
# TIMEOUT_ABSENT false-positive BY CONSTRUCTION — the documented single-shot-timeout limitation);
# then accounting is scaled back to 1, the consumer drains, and the row lands (PRESENT at re-probe).
# Kafka pod is NEVER touched (no rdkafka wedge). Provenance = by-injection (induced), magnitude =
# the accounting-down window (~35s, same ORDER as the natural w120's 328s backlog — disclosed).
# EVIDENCE-ONLY: logs marker/ack/probe results+times; the sidecar + case JSON are authored after.
set -u
NS="-n otel-demo"
TS=$(date +%s)

pf_up() {
  PID=$(ss -ltnp 2>/dev/null | grep ":8085 " | grep -o "pid=[0-9]*" | head -1 | cut -d= -f2)
  [ -n "${PID:-}" ] && kill $PID 2>/dev/null; sleep 1
  nohup kubectl $NS port-forward svc/frontend-proxy 8085:8080 >/tmp/pf-r1d-fe.log 2>&1 &
  sleep 3
  echo "PF :8085 -> $(curl -s -o /dev/null -w %{http_code} --max-time 12 http://localhost:8085/)"
}
PGPOD=""
pg_resolve() { PGPOD=$(kubectl $NS get pods -o name | grep postgresql | head -1 | sed 's|pod/||' | tr -d '\r'); echo "pg pod: $PGPOD"; }
pg_probe() { # $1=street marker -> the matching row(s), one per line (empty = absent)
  echo "SELECT street_address FROM accounting.shipping WHERE street_address='$1';" | \
    kubectl $NS exec -i "$PGPOD" -- env PGPASSWORD=otel psql -U root -d otel -t -A 2>/dev/null | tr -d '\r'
}
place_order() { # $1=marker ; prints "code=<http> order=<id-or-none>"
  local SESS=$(cat /proc/sys/kernel/random/uuid)
  curl -s -o /dev/null --max-time 15 -X POST -H "Content-Type: application/json" \
    -d "{\"item\":{\"productId\":\"0PUK6V6EV0\",\"quantity\":1},\"userId\":\"$SESS\"}" \
    "http://localhost:8085/api/cart?currencyCode=USD"
  local CODE=$(curl -s -o /tmp/r1d-ord.json -w "%{http_code}" --max-time 30 -X POST -H "Content-Type: application/json" \
    -d "{\"userId\":\"$SESS\",\"userCurrency\":\"USD\",\"email\":\"$1@corpus.test\",\"address\":{\"streetAddress\":\"$1\",\"state\":\"CA\",\"country\":\"United States\",\"city\":\"Mountain View\",\"zipCode\":\"94043\"},\"creditCard\":{\"creditCardCvv\":672,\"creditCardExpirationMonth\":1,\"creditCardExpirationYear\":2030,\"creditCardNumber\":\"4432-8015-6152-0454\"}}" \
    "http://localhost:8085/api/checkout?currencyCode=USD")
  local OID=$(python3 -c "import json;print(json.load(open('/tmp/r1d-ord.json')).get('orderId','none'))" 2>/dev/null || echo parse-err)
  echo "code=$CODE order=$OID"
}
acct_scale() { kubectl $NS scale deploy/accounting --replicas=$1 >/dev/null 2>&1; }

echo "===== R1D-OTEL-EVENTUAL pre-flight ($TS) ====="
pf_up; pg_resolve
echo "accounting now: $(kubectl $NS get deploy accounting --no-headers | awk '{print $2}')"

echo "===== HEALTH CANARY (accounting UP, lands immediately) ====="
M="r1dhc$TS"; echo "canary order: $(place_order $M)"; sleep 12
echo "canary probe @12s: [$(pg_probe $M)] (expect the marker = landed)"

for i in 1 2; do
  M="r1dev${i}-$TS"
  echo "===== INDUCED EVENTUAL-PRESENT #$i (marker $M) ====="
  echo "scale accounting -> 0"; acct_scale 0
  kubectl $NS rollout status deploy/accounting --timeout=60s 2>&1 | tail -1
  sleep 3
  echo "order (accounting down): $(place_order $M)"
  ACKT=$(date +%s%3N)
  echo "  ack at epoch_ms=$ACKT"
  echo "  probe @~5s: [$(pg_probe $M)] (expect empty)"
  echo "  waiting to pass MIST 25s cap..."; sleep 28
  P_CAP="$(pg_probe $M)"; CAPT=$(date +%s%3N)
  echo "  PROBE-AT-CAP (~31s, epoch_ms=$CAPT): [$P_CAP] (expect EMPTY = absent past cap)"
  echo "scale accounting -> 1 (drain)"; acct_scale 1
  kubectl $NS rollout status deploy/accounting --timeout=120s 2>&1 | tail -1
  echo "  draining..."; sleep 25
  P_RE="$(pg_probe $M)"; RET=$(date +%s%3N)
  echo "  PROBE-AT-REPROBE (post-drain, epoch_ms=$RET): [$P_RE] (expect PRESENT = landed = benign)"
  echo "  RESULT #$i: marker=$M ack=see-above cap=[$P_CAP] reprobe=[$P_RE]"
done

echo "===== restore + final health ====="
acct_scale 1; kubectl $NS rollout status deploy/accounting --timeout=120s 2>&1 | tail -1
M="r1dz$TS"; echo "final canary: $(place_order $M)"; sleep 15
echo "final canary probe: [$(pg_probe $M)] (expect landed)"
echo "accounting final: $(kubectl $NS get deploy accounting --no-headers | awk '{print $2}')"
echo "===== R1D-OTEL-EVENTUAL DONE ====="
