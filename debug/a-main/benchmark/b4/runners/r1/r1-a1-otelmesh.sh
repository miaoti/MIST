#!/bin/bash
# Wave R1 §4-A1 runner (mesh items) — OTel-Demo, TWO items sharing one TEMPORARY checkout sidecar:
#   ITEM A: kafka×mesh-sever S1 pair (2nd mechanism on the checkout site; client-side TCP blackhole
#           — the kafka pod is NEVER touched, no wedge class).
#   ITEM B: emptycart×method-scoped-sever VERIFY-FIRST (MASKED → capture pair; LOUD → refutation).
# Discipline: control-first, N>=4 probes per condition, psql read-back at the pinned cadence
# (25 s at-cap, T+300 s re-probe), teardown-verification between items (C-F7), heal canaries.
# Marker convention C-R2: street_address carries the marker (email mirrors it).
set -u
cd /mnt/c/Users/miaot/Github/MIST/debug/a-main/benchmark/b4
NS="-n otel-demo"
BH=/mnt/c/Users/miaot/Github/MIST/debug/a-main/benchmark/b4/runners/r1/r1-otel-blackhole.yaml
EC=/mnt/c/Users/miaot/Github/MIST/debug/a-main/benchmark/b4/runners/r1/r1-emptycart-vs.yaml
TS=$(date +%s)

pf_up() {
  PID=$(ss -ltnp 2>/dev/null | grep ":8085 " | grep -o "pid=[0-9]*" | head -1 | cut -d= -f2)
  [ -n "${PID:-}" ] && kill $PID 2>/dev/null; sleep 1
  nohup kubectl $NS port-forward svc/frontend-proxy 8085:8080 >/tmp/pf-r1-fe.log 2>&1 &
  sleep 3
  echo "PF :8085 -> $(curl -s -o /dev/null -w %{http_code} --max-time 12 http://localhost:8085/)"
}
PGPOD=""
pg_resolve() { PGPOD=$(kubectl $NS get pods -o name | grep postgresql | head -1 | sed 's|pod/||' | tr -d '\r'); echo "pg pod: $PGPOD"; }
pg_count() { # $1=street marker -> row count in accounting.shipping
  echo "SELECT count(*) FROM accounting.shipping WHERE street_address='$1';" | \
    kubectl $NS exec -i "$PGPOD" -- env PGPASSWORD=otel psql -U root -d otel -t -A 2>/dev/null | tr -d '\r'
}
place_order() { # $1=marker ; prints "code=<http> order=<id-or-none>"
  local SESS=$(cat /proc/sys/kernel/random/uuid)
  curl -s -o /dev/null --max-time 15 -X POST -H "Content-Type: application/json" \
    -d "{\"item\":{\"productId\":\"0PUK6V6EV0\",\"quantity\":1},\"userId\":\"$SESS\"}" \
    "http://localhost:8085/api/cart?currencyCode=USD"
  local CODE=$(curl -s -o /tmp/r1-ord.json -w "%{http_code}" --max-time 30 -X POST -H "Content-Type: application/json" \
    -d "{\"userId\":\"$SESS\",\"userCurrency\":\"USD\",\"email\":\"$1@corpus.test\",\"address\":{\"streetAddress\":\"$1\",\"state\":\"CA\",\"country\":\"United States\",\"city\":\"Mountain View\",\"zipCode\":\"94043\"},\"creditCard\":{\"creditCardCvv\":672,\"creditCardExpirationMonth\":1,\"creditCardExpirationYear\":2030,\"creditCardNumber\":\"4432-8015-6152-0454\"}}" \
    "http://localhost:8085/api/checkout?currencyCode=USD")
  local OID=$(python3 -c "import json;print(json.load(open('/tmp/r1-ord.json')).get('orderId','none'))" 2>/dev/null || echo parse-err)
  echo "code=$CODE order=$OID"
}
place_order_sess() { # $1=marker $2=session (for cart-emptied checks) ; same output
  curl -s -o /dev/null --max-time 15 -X POST -H "Content-Type: application/json" \
    -d "{\"item\":{\"productId\":\"0PUK6V6EV0\",\"quantity\":1},\"userId\":\"$2\"}" \
    "http://localhost:8085/api/cart?currencyCode=USD"
  local CODE=$(curl -s -o /tmp/r1-ord.json -w "%{http_code}" --max-time 30 -X POST -H "Content-Type: application/json" \
    -d "{\"userId\":\"$2\",\"userCurrency\":\"USD\",\"email\":\"$1@corpus.test\",\"address\":{\"streetAddress\":\"$1\",\"state\":\"CA\",\"country\":\"United States\",\"city\":\"Mountain View\",\"zipCode\":\"94043\"},\"creditCard\":{\"creditCardCvv\":672,\"creditCardExpirationMonth\":1,\"creditCardExpirationYear\":2030,\"creditCardNumber\":\"4432-8015-6152-0454\"}}" \
    "http://localhost:8085/api/checkout?currencyCode=USD")
  local OID=$(python3 -c "import json;print(json.load(open('/tmp/r1-ord.json')).get('orderId','none'))" 2>/dev/null || echo parse-err)
  echo "code=$CODE order=$OID"
}
cart_items() { # $1=session -> item count
  curl -s --max-time 10 "http://localhost:8085/api/cart?sessionId=$1&currencyCode=USD" | \
    python3 -c "import sys,json;d=json.load(sys.stdin);print(len(d.get('items',[])))" 2>/dev/null || echo parse-err
}

echo "===== R1-A1 pre-flight ====="
pf_up; pg_resolve
echo "health canary (r1hc$TS): $(place_order r1hc$TS)"; sleep 12
echo "health canary psql: $(pg_count r1hc$TS) (expect 1)"

echo "===== temp sidecar on checkout (per-deployment label) ====="
kubectl $NS patch deploy checkout -p '{"spec":{"template":{"metadata":{"labels":{"sidecar.istio.io/inject":"true"}}}}}'
kubectl $NS rollout status deploy/checkout --timeout=180s
kubectl $NS get pods --no-headers | grep checkout | grep -v Terminating

echo "===== ITEM A: healthy probes N>=4 (sidecar on, NO blackhole) ====="
for i in 1 2 3 4; do
  M="r1ah$i-$TS"; echo "healthy $i: $(place_order $M)"; sleep 8
  echo "healthy $i psql: $(pg_count $M) (expect 1)"
done

echo "===== ITEM A: CONTROL capture (marker r1kc$TS; sidecar on, NO blackhole) ====="
CDIR=captures/oteldemo-checkout-meshsever-control; mkdir -p $CDIR
python3 capture_driver.py capture-specs/oteldemo-checkout-meshsever-flow.yaml "$CDIR/sidecar.json" --case-id=oteldemo-checkout-meshsever-control-001 --var=session=$(cat /proc/sys/kernel/random/uuid) --var=marker=r1kc$TS
sleep 25
echo "CONTROL at-cap psql: $(pg_count r1kc$TS) (expect 1)"

echo "===== ITEM A: apply blackhole (SE+VS) + interception verify ====="
kubectl apply -f "$BH"; sleep 5
kubectl $NS get virtualservice,serviceentry
for i in 1 2 3 4; do
  M="r1af$i-$TS"; echo "fault probe $i: $(place_order $M)"; sleep 8
  echo "fault probe $i psql at ~8s: $(pg_count $M) (expect 0)"
done
echo "checkout kafka errors (last 5):"
kubectl $NS logs deploy/checkout --since=3m 2>/dev/null | grep -i -E "kafka|failed|error" | tail -5

echo "===== ITEM A: FAULT capture (marker r1kx$TS; blackhole on) ====="
FDIR=captures/oteldemo-checkout-meshsever-lost; mkdir -p $FDIR
python3 capture_driver.py capture-specs/oteldemo-checkout-meshsever-flow.yaml "$FDIR/sidecar.json" --case-id=oteldemo-checkout-meshsever-lost-001 --var=session=$(cat /proc/sys/kernel/random/uuid) --var=marker=r1kx$TS
sleep 25
echo "FAULT at-cap psql: $(pg_count r1kx$TS) (expect 0)"

echo "===== ITEM A: T+300s re-probe (pinned cadence) ====="
sleep 300
echo "re-probe CONTROL (r1kc$TS): $(pg_count r1kc$TS) (expect 1)"
echo "re-probe FAULT   (r1kx$TS): $(pg_count r1kx$TS) (expect 0 — permanent)"

echo "===== ITEM A: teardown blackhole + heal verify (C-F7 gate) ====="
kubectl delete -f "$BH"; sleep 5
kubectl $NS get virtualservice,serviceentry 2>&1 | head -4
M="r1heal$TS"; echo "heal canary: $(place_order $M)"; sleep 15
echo "heal canary psql: $(pg_count $M) (expect 1; if 0 the producer wedged -> rollout-restart checkout, disclosed)"
H=$(pg_count $M)
if [ "$H" != "1" ]; then
  echo "producer wedged post-blackhole — restarting checkout (disclosed)"
  kubectl $NS rollout restart deploy/checkout; kubectl $NS rollout status deploy/checkout --timeout=180s
  sleep 5; M="r1heal2-$TS"; echo "heal canary 2: $(place_order $M)"; sleep 15
  echo "heal canary 2 psql: $(pg_count $M) (expect 1)"
fi

echo "===== ITEM B: emptycart method-sever VERIFY (sidecar still on) ====="
kubectl apply -f "$EC"; sleep 5
for i in 1 2 3; do
  S=$(cat /proc/sys/kernel/random/uuid); M="r1ev$i-$TS"
  R=$(place_order_sess $M $S); sleep 10
  echo "verify $i: $R psql=$(pg_count $M) cart_items_after=$(cart_items $S) (MASKED iff code=200 & psql=1 & cart_items>0)"
done
echo "ITEM B verdict recorded above — capture pair only if MASKED (orchestrator decides; VS stays ON for a fault capture if masked)"

echo "===== ITEM B: teardown emptycart VS ====="
kubectl delete -f "$EC"; sleep 3
kubectl $NS get virtualservice 2>&1 | head -3

echo "===== final: sidecar label off + rollout + zero-artifact assert ====="
kubectl $NS patch deploy checkout --type=json -p '[{"op":"remove","path":"/spec/template/metadata/labels/sidecar.istio.io~1inject"}]'
kubectl $NS rollout status deploy/checkout --timeout=180s
echo "residual artifacts (expect 0/0): vs=$(kubectl $NS get virtualservice --no-headers 2>/dev/null | wc -l) se=$(kubectl $NS get serviceentry --no-headers 2>/dev/null | wc -l)"
pf_up
M="r1z$TS"; echo "final health: $(place_order $M)"; sleep 15
echo "final health psql: $(pg_count $M) (expect 1)"
echo "===== R1-A1 mesh items DONE ====="
