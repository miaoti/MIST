#!/bin/bash
# wave-3a ITEM 1 runner (plan rev 2.1, unanimous b0b5a54): OTel-Demo cartFailure S1 + control.
# Sequence: fresh canary (selector re-verify) -> CONTROL leg -> flag on (1-P0 mechanism) ->
# N>=4 probes -> quiet gap -> RECORD leg -> restore (verified) -> post-restore heal canary.
# All cluster ops scripted (runbook rule); psql corroboration per leg (A-6); export =
# service=checkout ONLY over the leg window; exactly-one pre-check before accepting an export.
set -u
cd /mnt/c/Users/miaot/Github/MIST/debug/a-main/benchmark/b4
J="http://localhost:16687/jaeger/ui/api"
FRONT="http://localhost:8085"
TOGGLE=/tmp/flagd-toggle.sh
PGPOD=$(kubectl -n otel-demo get pods -o name | grep postgresql | head -1)
psqlq() { echo "$1" | kubectl -n otel-demo exec -i "$PGPOD" -- env PGPASSWORD=otel psql -U root -d otel -t -A; }

place() { # $1=session $2=marker -> "code time_total orderId"
  local S=$1 M=$2 R OID
  curl -s -o /dev/null --max-time 15 -X POST -H "Content-Type: application/json" \
    -d "{\"item\":{\"productId\":\"0PUK6V6EV0\",\"quantity\":1},\"userId\":\"$S\"}" \
    "$FRONT/api/cart?currencyCode=USD"
  R=$(curl -s -o /tmp/i1-place.json -w "%{http_code} %{time_total}" --max-time 30 -X POST -H "Content-Type: application/json" \
    -d "{\"userId\":\"$S\",\"userCurrency\":\"USD\",\"email\":\"$M@corpus.test\",\"address\":{\"streetAddress\":\"1 Corpus Way\",\"state\":\"CA\",\"country\":\"United States\",\"city\":\"Mountain View\",\"zipCode\":\"94043\"},\"creditCard\":{\"creditCardCvv\":672,\"creditCardExpirationMonth\":1,\"creditCardExpirationYear\":2030,\"creditCardNumber\":\"4432-8015-6152-0454\"}}" \
    "$FRONT/api/checkout?currencyCode=USD")
  OID=$(python3 -c "import json;print(json.load(open('/tmp/i1-place.json')).get('orderId','NONE'))" 2>/dev/null || echo PARSE_FAIL)
  echo "$R $OID"
}
cart_items() { # $1=session -> item count
  curl -s --max-time 10 "$FRONT/api/cart?sessionId=$1&currencyCode=USD" | \
    python3 -c "import json,sys;print(len(json.load(sys.stdin).get('items',[])))" 2>/dev/null || echo ERR
}
export_leg() { # $1=T0us $2=T1us $3=outfile -> prints entry-trace count; writes merged file
  curl -s --max-time 15 "$J/traces?service=checkout&start=$1&end=$2&limit=20" -o /tmp/i1-exp.json
  python3 - "$3" <<'PY'
import json, sys
a = json.load(open("/tmp/i1-exp.json"))["data"]
n = 0
for t in a:
    procs = {pid: p.get("serviceName","?") for pid,p in t["processes"].items()}
    for s in t["spans"]:
        tags = {x["key"]: x.get("value") for x in s["tags"]}
        if procs[s["processID"]] == "frontend-proxy" and tags.get("span.kind") == "server":
            n += 1
            break
json.dump({"data": a}, open(sys.argv[1], "w"), indent=1)
print(n)
PY
}

echo "===== 0) fresh canary (selector re-verification) ====="
bash $TOGGLE cartFailure status
T0=$(( $(date +%s) * 1000000 ))
S=$(cat /proc/sys/kernel/random/uuid)
OUT=$(place "$S" otl1cnry)
echo "canary: $OUT  cart-after=$(cart_items $S)"
sleep 10
T1=$(( ($(date +%s) + 1) * 1000000 ))
curl -s --max-time 15 "$J/traces?service=checkout&start=$T0&end=$T1&limit=10" -o /tmp/i1-canary.json
python3 - <<'PY'
import json
d = json.load(open("/tmp/i1-canary.json"))["data"]
print("canary traces:", len(d))
for t in d[:1]:
    procs = {pid: p.get("serviceName","?") for pid,p in t["processes"].items()}
    hits = []
    fpx = 0
    for s in t["spans"]:
        svc = procs[s["processID"]]
        tags = {x["key"]: x.get("value") for x in s["tags"]}
        if svc == "frontend-proxy" and tags.get("span.kind") == "server": fpx += 1
        if svc == "cart" and "emptycart" in s["operationName"].lower():
            err = str(tags.get("error","")).lower()=="true" or str(tags.get("otel.status_code","")).upper()=="ERROR"
            hits.append("%s kind=%s err=%s" % (s["operationName"], tags.get("span.kind"), err))
    print("frontend-proxy server spans in entry trace:", fpx)
    print("cart EmptyCart spans:", hits if hits else "NONE")
PY

echo "===== 1) CONTROL LEG (flag off) ====="
bash $TOGGLE cartFailure status
CDIR=captures/oteldemo-emptycart-control; mkdir -p $CDIR
T0=$(( $(date +%s) * 1000000 ))
SESS=$(cat /proc/sys/kernel/random/uuid)
python3 capture_driver.py capture-specs/oteldemo-emptycart-flow.yaml "$CDIR/sidecar.json" --case-id=oteldemo-emptycart-control-001 --var=session="$SESS" --var=marker=otl1c
T1=$(( ($(date +%s) + 2) * 1000000 ))
COID=$(python3 - "$CDIR/sidecar.json" <<'PY'
import json, sys
d = json.load(open(sys.argv[1])); oid = None
for r in d["records"]:
    if r.get("kind") == "response":
        try:
            o = json.loads(r.get("body") or "{}").get("orderId")
            if o: oid = o; break
        except Exception: pass
print(oid or "")
PY
)
echo "control orderId: $COID  post-checkout cart=$(cart_items $SESS)"
sleep 14
CRB=$(psqlq "SELECT count(*) FROM accounting.\"order\" WHERE order_id='$COID';")
{ echo "# oteldemo-emptycart-control-001 psql corroboration ($(date -u +%FT%TZ)) — A-6 isolation artifact"
  echo "# the ORDER lands on BOTH legs (kafka healthy); the pair's variable is the cart-empty effect"
  echo "order_id=$COID"
  echo "order_row_count=$CRB"
} > $CDIR/readback-psql.txt
cat $CDIR/readback-psql.txt
sleep 3
N=$(export_leg $T0 $T1 "$CDIR/trace-control.json")
echo "control export entry-traces: $N"
python3 trace_score.py oteldemo-emptycart-control-001 $CDIR/trace-control.json

echo "===== 2) flag ON + N>=4 probes ====="
bash $TOGGLE cartFailure on || exit 2
for i in 1 2 3 4; do
  S=$(cat /proc/sys/kernel/random/uuid)
  OUT=$(place "$S" otl1prb$i)
  CI=$(cart_items $S)
  echo "probe $i: $OUT cart-after=$CI"
done

echo "===== 3) RECORD LEG (quiet gap first) ====="
sleep 14
FDIR=captures/oteldemo-emptycart-swallowed; mkdir -p $FDIR
T0=$(( $(date +%s) * 1000000 ))
SESS=$(cat /proc/sys/kernel/random/uuid)
python3 capture_driver.py capture-specs/oteldemo-emptycart-flow.yaml "$FDIR/sidecar.json" --case-id=oteldemo-emptycart-swallowed-001 --var=session="$SESS" --var=marker=otl1f
T1=$(( ($(date +%s) + 2) * 1000000 ))
FOID=$(python3 - "$FDIR/sidecar.json" <<'PY'
import json, sys
d = json.load(open(sys.argv[1])); oid = None
for r in d["records"]:
    if r.get("kind") == "response":
        try:
            o = json.loads(r.get("body") or "{}").get("orderId")
            if o: oid = o; break
        except Exception: pass
print(oid or "")
PY
)
echo "record orderId: $FOID  post-checkout cart=$(cart_items $SESS)"
sleep 14
FRB=$(psqlq "SELECT count(*) FROM accounting.\"order\" WHERE order_id='$FOID';")
{ echo "# oteldemo-emptycart-swallowed-001 psql corroboration ($(date -u +%FT%TZ)) — A-6 isolation artifact"
  echo "# the ORDER lands on BOTH legs (kafka healthy); what is LOST under the flag is the cart-empty effect"
  echo "order_id=$FOID"
  echo "order_row_count=$FRB"
} > $FDIR/readback-psql.txt
cat $FDIR/readback-psql.txt
sleep 3
N=$(export_leg $T0 $T1 "$FDIR/trace-fault.json")
echo "record export entry-traces: $N"
python3 trace_score.py oteldemo-emptycart-swallowed-001 $FDIR/trace-fault.json

echo "===== 4) restore + heal canary ====="
bash $TOGGLE cartFailure off || exit 3
S=$(cat /proc/sys/kernel/random/uuid)
OUT=$(place "$S" otl1heal)
echo "heal canary: $OUT cart-after=$(cart_items $S) (expect 0 = emptied again)"
bash $TOGGLE cartFailure status
