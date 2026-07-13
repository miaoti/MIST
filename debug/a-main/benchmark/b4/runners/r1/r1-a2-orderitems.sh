#!/bin/bash
# Wave R1 §4-A2 runner — TeaStore ORDER-ITEMS mesh-sever S1 pair (plan rev 2; freeze §6 R1 row).
# Discipline = wave-3a item-2 verbatim: baselines -> temp sidecars (per-DEPLOYMENT labels only) ->
# healthy probes N>=4 -> CONTROL capture -> VS apply + interception verify -> fault probes N>=4
# (mask = ORDERCONFIRMED page + parent order PRESENT + child items ABSENT) -> quiet gap -> FAULT
# capture -> VS delete verified -> post-teardown readbacks (both legs) -> labels removed ->
# rollouts verified -> final health + zero-fault-artifact assert (C-F7 teardown gate).
# Identity ledger (fresh db generation at the R1 revival): user21=control user22=fault user23=probes.
# HARD GUARDS: no persistence/db restarts anywhere; never GET /rest/generatedb; maintenance flag
# verified false before/after.
set -u
cd /mnt/c/Users/miaot/Github/MIST/debug/a-main/benchmark/b4
WB="http://localhost:8082/tools.descartes.teastore.webui"
PB="http://localhost:8083/tools.descartes.teastore.persistence"
VS=/mnt/c/Users/miaot/Github/MIST/debug/a-main/benchmark/b4/runners/r1/r1-orderitems-vs.yaml
NS="-n teastore"
TS=$(date +%s)

pf_up() { # (re-)create both PFs; they die with pod restarts
  for spec in "8082 teastore-webui" "8083 teastore-persistence"; do
    P=${spec%% *}; SVC=${spec##* }
    PID=$(ss -ltnp 2>/dev/null | grep ":$P " | grep -o "pid=[0-9]*" | head -1 | cut -d= -f2)
    [ -n "${PID:-}" ] && kill $PID 2>/dev/null
  done
  sleep 1
  nohup kubectl $NS port-forward svc/teastore-webui 8082:8080 >/tmp/pf-r1-wui.log 2>&1 &
  nohup kubectl $NS port-forward svc/teastore-persistence 8083:8080 >/tmp/pf-r1-per.log 2>&1 &
  sleep 3
  echo "PFs: webui=$(curl -s -o /dev/null -w %{http_code} --max-time 12 $WB/) persistence=$(curl -s -o /dev/null -w %{http_code} --max-time 12 $PB/rest/categories)"
}
probe_order() { # $1=user $2=marker -> prints CONFIRMED/NOPE
  local JJ=/tmp/r1-$$-$RANDOM.jar PG=/tmp/r1-$$-pg.html; rm -f $JJ
  curl -s -c $JJ -b $JJ -L -o /dev/null --max-time 20 -X POST "$WB/loginAction?username=$1&password=password"
  curl -s -c $JJ -b $JJ -L -o /dev/null --max-time 20 -X POST "$WB/cartAction?addToCart=&productid=42"
  curl -s -c $JJ -b $JJ -L -o $PG --max-time 30 -X POST "$WB/cartAction?firstname=$2&lastname=P&address1=S&address2=C&cardtype=visa&cardnumber=314159265359&expirydate=12%2F2029&confirm=Confirm"
  grep -q -i "confirmed" $PG && echo CONFIRMED || echo NOPE
}
uid_of() { curl -s --max-time 8 "$PB/rest/users/name/$1" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2; }
order_id_by_marker() { # $1=uid $2=marker -> order id whose addressName == "marker <lastname>"
  curl -s --max-time 10 "$PB/rest/orders/user/$1" | python3 -c "
import sys,json
mk=sys.argv[1]
try: rows=json.load(sys.stdin)
except Exception: rows=[]
for r in rows:
    if str(r.get('addressName','')).startswith(mk): print(r.get('id')); break
" "$2"; }
items_of_order() { # $1=orderid -> count of orderitems with that orderId (scoped endpoint, probed below)
  curl -s --max-time 10 "$PB/rest/orderitems/order/$1" | python3 -c "
import sys,json
try: rows=json.load(sys.stdin)
except Exception: rows=[]
print(len(rows) if isinstance(rows,list) else 'PARSE-ERR')"; }

echo "===== R1-A2 pre-flight ====="
pf_up
echo "maintenance: $(curl -s --max-time 6 $PB/rest/generatedb/maintenance)"
echo "orderitems list size: $(curl -s --max-time 10 "$PB/rest/orderitems" | python3 -c 'import sys,json;print(len(json.load(sys.stdin)))' 2>/dev/null || echo PARSE-ERR)"
# probe-first: the scoped child endpoint must exist, else ABORT (spec uses it)
SC=$(curl -s -o /tmp/r1-scoped.json -w "%{http_code}" --max-time 10 "$PB/rest/orderitems/order/999999")
echo "scoped endpoint /rest/orderitems/order/{id} -> HTTP $SC (expect 200+[] or 404)"
if [ "$SC" != "200" ]; then echo "ABORT: scoped orderitems endpoint unavailable (HTTP $SC) — adjust readback spec to the full-list endpoint before re-running"; exit 3; fi
for U in user21 user22 user23; do
  UID_=$(uid_of $U); echo "$U uid=$UID_ baseline orders: $(curl -s --max-time 8 "$PB/rest/orders/user/$UID_" | python3 -c 'import sys,json;print(len(json.load(sys.stdin)))' 2>/dev/null)"
done

echo "===== temp sidecars on webui+auth (per-deployment labels ONLY) ====="
kubectl $NS patch deploy teastore-webui -p '{"spec":{"template":{"metadata":{"labels":{"sidecar.istio.io/inject":"true"}}}}}'
kubectl $NS patch deploy teastore-auth  -p '{"spec":{"template":{"metadata":{"labels":{"sidecar.istio.io/inject":"true"}}}}}'
kubectl $NS rollout status deploy/teastore-webui --timeout=180s
kubectl $NS rollout status deploy/teastore-auth --timeout=180s
kubectl $NS get pods --no-headers | grep -E "webui|auth" | grep -v Terminating
pf_up

echo "===== healthy probes N>=4 (sidecars on, NO VS) — user23 ====="
for i in 1 2 3 4; do echo "healthy probe $i (R1H$i): $(probe_order user23 R1H$i)"; done
sleep 3
U23=$(uid_of user23)
for i in 1 2 3 4; do
  OID=$(order_id_by_marker $U23 R1H$i)
  echo "healthy R1H$i order=$OID items=$( [ -n "$OID" ] && items_of_order $OID || echo NO-ORDER )"
done

echo "===== CONTROL capture (user21, marker R1C$TS; sidecars on, NO VS) ====="
CDIR=captures/teastore-orderitems-meshsever-control; mkdir -p $CDIR
python3 capture_driver.py capture-specs/teastore-orderitems-meshsever-flow.yaml "$CDIR/sidecar.json" --case-id=teastore-orderitems-meshsever-control-001 --var=user=user21 --var=pw=password --var=marker=R1C$TS
sleep 3
U21=$(uid_of user21); C_OID=$(order_id_by_marker $U21 R1C$TS)
echo "control order=$C_OID items=$( [ -n "$C_OID" ] && items_of_order $C_OID || echo NO-ORDER )"
if [ -z "$C_OID" ]; then echo "ABORT: control order did not land"; exit 4; fi

echo "===== VS apply + interception verify ====="
kubectl apply -f "$VS"
sleep 4
kubectl $NS get virtualservice
echo "fault probes N>=4 (user23, R1F1..4): expect CONFIRMED page + parent order PRESENT + child items 0"
for i in 1 2 3 4; do echo "fault probe $i (R1F$i): $(probe_order user23 R1F$i)"; done
sleep 3
for i in 1 2 3 4; do
  OID=$(order_id_by_marker $U23 R1F$i)
  echo "fault R1F$i order=$OID items=$( [ -n "$OID" ] && items_of_order $OID || echo NO-ORDER )"
done

echo "===== quiet gap (8 s) then FAULT capture (user22, marker R1X$TS; VS on) ====="
sleep 8
FDIR=captures/teastore-orderitems-meshsever-masked; mkdir -p $FDIR
python3 capture_driver.py capture-specs/teastore-orderitems-meshsever-flow.yaml "$FDIR/sidecar.json" --case-id=teastore-orderitems-meshsever-masked-001 --var=user=user22 --var=pw=password --var=marker=R1X$TS
sleep 3
U22=$(uid_of user22); F_OID=$(order_id_by_marker $U22 R1X$TS)
echo "fault order=$F_OID items=$( [ -n "$F_OID" ] && items_of_order $F_OID || echo NO-ORDER )"

echo "===== VS delete + verify gone ====="
kubectl delete -f "$VS"
sleep 3
kubectl $NS get virtualservice 2>&1 | head -3
echo "post-teardown heal probe (user23, R1P1): $(probe_order user23 R1P1)"
sleep 3
P_OID=$(order_id_by_marker $U23 R1P1)
echo "heal R1P1 order=$P_OID items=$( [ -n "$P_OID" ] && items_of_order $P_OID || echo NO-ORDER )"

echo "===== post-teardown readbacks of record (persistence surface, both legs) ====="
python3 capture_driver.py capture-specs/teastore-orderitems-meshsever-readback.yaml "$CDIR/sidecar-postteardown.json" --case-id=teastore-orderitems-meshsever-control-001 --var=uid=$U21 --var=orderid=$C_OID --var=marker=R1C$TS
python3 capture_driver.py capture-specs/teastore-orderitems-meshsever-readback.yaml "$FDIR/sidecar-postteardown.json" --case-id=teastore-orderitems-meshsever-masked-001 --var=uid=$U22 --var=orderid=${F_OID:-0} --var=marker=R1X$TS
echo "control items final: $( [ -n "$C_OID" ] && items_of_order $C_OID )"
echo "fault   items final: $( [ -n "$F_OID" ] && items_of_order $F_OID || echo NO-ORDER )"

echo "===== teardown: labels off + rollouts + final health (C-F7 gate) ====="
kubectl $NS patch deploy teastore-webui --type=json -p '[{"op":"remove","path":"/spec/template/metadata/labels/sidecar.istio.io~1inject"}]'
kubectl $NS patch deploy teastore-auth  --type=json -p '[{"op":"remove","path":"/spec/template/metadata/labels/sidecar.istio.io~1inject"}]'
kubectl $NS rollout status deploy/teastore-webui --timeout=180s
kubectl $NS rollout status deploy/teastore-auth --timeout=180s
pf_up
echo "residual fault artifacts (expect NONE): vs=$(kubectl $NS get virtualservice --no-headers 2>/dev/null | wc -l) envoyfilter=$(kubectl $NS get envoyfilter --no-headers 2>/dev/null | wc -l)"
echo "maintenance: $(curl -s --max-time 6 $PB/rest/generatedb/maintenance)"
echo "final health probe (user23, R1Z1): $(probe_order user23 R1Z1)"
echo "===== R1-A2 orderitems DONE ====="
