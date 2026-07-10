#!/bin/bash
# wave-3a ITEM 2 runner (plan rev 2.1 §2, B-F2 enumerated checklist, control-first):
# baselines -> inject sidecars (per-DEPLOYMENT labels only) -> healthy probes -> CONTROL capture ->
# VS apply + interception verify -> fault probes (+A-9 orderitems orphan check) -> quiet gap ->
# RECORD capture -> VS delete verified -> post-teardown read-backs (both legs) -> labels removed ->
# rollouts verified -> PF re-created -> final health. Identity ledger: user18=control, user19=fault,
# user20=probes (markers distinct). HARD GUARD: no persistence/db restarts anywhere in this script.
set -u
cd /mnt/c/Users/miaot/Github/MIST/debug/a-main/benchmark/b4
WB="http://localhost:8082/tools.descartes.teastore.webui"
PB="http://localhost:8083/tools.descartes.teastore.persistence"
VS=/mnt/c/Users/miaot/Github/MIST/debug/a-main/benchmark/b4/runners/3a/teastore-meshsever-vs.yaml
NS="-n teastore"

pf8082() { # re-create the webui PF (dies with the pod)
  PID=$(ss -ltnp 2>/dev/null | grep ":8082 " | grep -o "pid=[0-9]*" | head -1 | cut -d= -f2)
  [ -n "${PID:-}" ] && kill $PID 2>/dev/null; sleep 1
  nohup kubectl $NS port-forward svc/teastore-webui 8082:8080 >/tmp/pf-teastore-webui.log 2>&1 &
  sleep 3
  echo "PF :8082 re-created; page=$(curl -s -o /dev/null -w "%{http_code}" --max-time 12 "$WB/")"
}
probe_order() { # $1=user $2=marker -> CONFIRMED/NOPE
  local JJ=/tmp/i2-$$.jar PG=/tmp/i2-$$.html; rm -f $JJ
  curl -s -c $JJ -b $JJ -L -o /dev/null --max-time 20 -X POST "$WB/loginAction?username=$1&password=password"
  curl -s -c $JJ -b $JJ -L -o /dev/null --max-time 20 -X POST "$WB/cartAction?addToCart=&productid=42"
  curl -s -c $JJ -b $JJ -L -o $PG --max-time 30 -X POST "$WB/cartAction?firstname=$2&lastname=P&address1=S&address2=C&cardtype=visa&cardnumber=314159265359&expirydate=12%2F2029&confirm=Confirm"
  grep -q -i "confirmed" $PG && echo CONFIRMED || echo NOPE
}
uid_of() { curl -s --max-time 8 "$PB/rest/users/name/$1" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2; }
orders_of() { curl -s --max-time 8 "$PB/rest/orders/user/$(uid_of $1)"; }

echo "===== pre: maintenance flag + baselines (B-F5) ====="
echo "maintenance: $(curl -s --max-time 6 $PB/rest/generatedb/maintenance)"
for U in user18 user19 user20; do
  echo "$U baseline orders: $(orders_of $U | grep -o '"addressName":"[^"]*"' | wc -l) rows: $(orders_of $U | grep -o '"addressName":"[^"]*"' | tr "\n" " ")"
done
echo "orderitems orphan baseline (order_id<=0): $(curl -s --max-time 10 "$PB/rest/orderitems" | grep -o '"orderId":-\?[0-9]*' | grep -c -E ':(-[0-9]+|0)$' || echo 0)"

echo "===== B-F2.1 inject sidecars (per-deployment labels ONLY) ====="
kubectl $NS patch deploy teastore-webui -p '{"spec":{"template":{"metadata":{"labels":{"sidecar.istio.io/inject":"true"}}}}}'
kubectl $NS patch deploy teastore-auth  -p '{"spec":{"template":{"metadata":{"labels":{"sidecar.istio.io/inject":"true"}}}}}'
kubectl $NS rollout status deploy/teastore-webui --timeout=180s
kubectl $NS rollout status deploy/teastore-auth --timeout=180s
kubectl $NS get pods | grep -E "webui|auth" | grep -v Terminating
pf8082

echo "===== B-F2.3 N>=4 HEALTHY probes (sidecars on, NO VS) — user20 ====="
for i in 1 2 3 4; do echo "healthy probe $i (M2H$i): $(probe_order user20 M2H$i)"; done
sleep 3
echo "user20 orders now: $(orders_of user20 | grep -o '"addressName":"M2H[0-9] P"' | tr "\n" " ")"

echo "===== CONTROL LEG capture (user18, sidecars on, NO VS) ====="
CDIR=captures/teastore-order-meshsever-control; mkdir -p $CDIR
python3 capture_driver.py capture-specs/teastore-order-meshsever-flow.yaml "$CDIR/sidecar.json" --case-id=teastore-order-meshsever-control-001 --var=user=user18 --var=pw=password --var=marker=M2C1
echo "control flow marker hits in sidecar: $(grep -c M2C1 $CDIR/sidecar.json)"

echo "===== B-F2.4 VS apply + interception verify ====="
kubectl apply -f "$VS"
sleep 4
kubectl $NS exec deploy/teastore-auth -c teastore-auth -- curl -s -o /dev/null -w "auth->persistence /rest/orders w/ VS: %{http_code}\n" --max-time 6 "http://teastore-persistence:8080/tools.descartes.teastore.persistence/rest/orders/user/1" 2>/dev/null
kubectl $NS exec deploy/teastore-auth -c teastore-auth -- curl -s -o /dev/null -w "auth->persistence /rest/categories (unmatched): %{http_code}\n" --max-time 6 "http://teastore-persistence:8080/tools.descartes.teastore.persistence/rest/categories" 2>/dev/null

echo "===== fault probes N>=4 (user20, markers M2F1-4) ====="
for i in 1 2 3 4; do echo "fault probe $i (M2F$i): $(probe_order user20 M2F$i)"; done
echo "A-9 orderitems orphan check after fault probes (order_id<=0): $(curl -s --max-time 10 "$PB/rest/orderitems" | grep -o '"orderId":-\?[0-9]*' | grep -c -E ':(-[0-9]+|0)$' || echo 0)"

echo "===== RECORD LEG (user19, quiet gap first) ====="
sleep 12
FDIR=captures/teastore-order-meshsever-masked; mkdir -p $FDIR
python3 capture_driver.py capture-specs/teastore-order-meshsever-flow.yaml "$FDIR/sidecar.json" --case-id=teastore-order-meshsever-masked-001 --var=user=user19 --var=pw=password --var=marker=M2X1
echo "record flow marker hits in sidecar: $(grep -c M2X1 $FDIR/sidecar.json)"

echo "===== B-F2.4b VS DELETE verified ====="
kubectl delete -f "$VS"
sleep 3
echo "VS remaining: $(kubectl $NS get vs 2>/dev/null | wc -l) (expect 0 lines beyond header -> total 0)"

echo "===== A-7 fragment-export probe (do the temp sidecars export to istio-system jaeger?) ====="
nohup kubectl -n istio-system port-forward svc/tracing 26686:80 >/tmp/pf-istio-jaeger.log 2>&1 &
IJPF=$!
sleep 3
for SVC in "teastore-webui.teastore" "teastore-auth.teastore" "teastore-webui" "teastore-auth"; do
  N=$(curl -s --max-time 8 "http://localhost:26686/jaeger/api/traces?service=$SVC&limit=5&lookback=1h" | python3 -c "import json,sys;print(len(json.load(sys.stdin).get('data',[])))" 2>/dev/null || echo ERR)
  echo "istio-jaeger traces for $SVC: $N"
done
kill $IJPF 2>/dev/null

echo "===== post-teardown read-backs of record (fresh sessions; VS gone, sidecars still on = parity) ====="
python3 capture_driver.py capture-specs/teastore-order-meshsever-readback.yaml "$CDIR/sidecar-postteardown.json" --case-id=teastore-order-meshsever-control-001 --var=user=user18 --var=pw=password
python3 capture_driver.py capture-specs/teastore-order-meshsever-readback.yaml "$FDIR/sidecar-postteardown.json" --case-id=teastore-order-meshsever-masked-001 --var=user=user19 --var=pw=password
echo "control M2C1 in post-teardown readback: $(grep -c M2C1 $CDIR/sidecar-postteardown.json)"
echo "fault   M2X1 in post-teardown readback: $(grep -c M2X1 $FDIR/sidecar-postteardown.json)"
echo "REST corroboration:"
echo "  user18 (control) M2C1 rows: $(orders_of user18 | grep -o '"addressName":"M2C1 Corpus"' | wc -l)"
echo "  user19 (fault)   M2X1 rows: $(orders_of user19 | grep -o '"addressName":"M2X1 Corpus"' | wc -l)"
echo "  user20 probes: M2H landed=$(orders_of user20 | grep -o '"addressName":"M2H[0-9] P"' | wc -l)/4  M2F landed=$(orders_of user20 | grep -o '"addressName":"M2F[0-9] P"' | wc -l)/4"

echo "===== B-F2.5/2.6 labels removed + rollouts + PF + final health ====="
kubectl $NS patch deploy teastore-webui --type=json -p '[{"op":"remove","path":"/spec/template/metadata/labels/sidecar.istio.io~1inject"}]'
kubectl $NS patch deploy teastore-auth  --type=json -p '[{"op":"remove","path":"/spec/template/metadata/labels/sidecar.istio.io~1inject"}]'
kubectl $NS rollout status deploy/teastore-webui --timeout=180s
kubectl $NS rollout status deploy/teastore-auth --timeout=180s
kubectl $NS get pods | grep -E "webui|auth" | grep -v Terminating
pf8082
echo "final: page=$(curl -s -o /dev/null -w "%{http_code}" --max-time 12 "$WB/product?id=42") maintenance=$(curl -s --max-time 6 $PB/rest/generatedb/maintenance) vs=$(kubectl $NS get vs 2>/dev/null | wc -l)"
