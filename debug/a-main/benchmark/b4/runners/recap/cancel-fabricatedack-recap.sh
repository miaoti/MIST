#!/bin/bash
# TT re-capture (rev2 PRE-REG) — Class B: cancel-refund-fabricatedack (the flagship).
# Fault = drawbackFaultMode=fabricatedack (the cancel's drawback acks WITHOUT persisting the refund).
# Read-back = MySQL ts.inside_money.money (the buyer's durable balance) WHERE user_id=<fresh buyer>.
# Within-leg value: control refund LANDS (balance up), fault refund LOST (balance flat). ABORT rule +
# toggle reset + fresh label-free keys + N<=3 cap + contradiction-is-a-finding.
set -u
GW="http://localhost:8080"
EV=/mnt/c/Users/miaot/Github/MIST/debug/a-main/benchmark/b4/cset/recap-cancel-fabricatedack
mkdir -p "$EV"; LOG="$EV/attempts.log"
ADMIN=$(curl -s -X POST -H "Content-Type: application/json" -d '{"username":"admin","password":"222222"}' "$GW/api/v1/users/login" --max-time 20 | python3 -c "import json,sys;print(json.load(sys.stdin)['data']['token'])" 2>/dev/null)
[ -n "$ADMIN" ] || { echo "FATAL: no admin token"; exit 1; }
toggle(){ curl -s -H "Authorization: Bearer $ADMIN" "$GW/api/v1/inside_pay_service/inside_payment/test/faultmode/$1" --max-time 15 >/dev/null; }
# read-back = COUNT of type-D DRAWBACK/refund rows for the buyer (the case's committed count-delta
# locator: without-fault=count-delta-positive, with-fault=count-delta-zero). Probe v1 read limit-1
# money and hit the type-A balance row (50), missing the type-D refund row - CORRECTED here (see
# attempts-probe-v1-WRONG-ROW.log; the ground truth [control refund lands] was never in doubt, only
# the probe row). This is a probe fix, not outcome-shopping - the type-D row IS the refund.
refund_rows(){ kubectl -n trainticket exec tsdb-mysql-0 -c mysql -- sh -c "mysql -uroot -N -e \"select count(*) from ts.inside_money where user_id='$1' and type='D';\" 2>/dev/null" 2>/dev/null | tr -d '[:space:]'; }

leg(){ # $1=mode $2=legname
  local MODE="$1" LEG="$2" TS U PW RESP TOK BUYER OID B0 B1 CANCEL
  TS=$(date +%s%N | cut -c1-16); U="rc${TS}${RANDOM}"; PW="pass123456"
  if [ "$MODE" = fabricatedack ]; then toggle fabricatedack; else toggle none; fi
  curl -s -X POST -H "Content-Type: application/json" -d "{\"userName\":\"$U\",\"password\":\"$PW\",\"gender\":1,\"documentType\":1,\"documentNum\":\"RC-$TS\",\"email\":\"$U@corpus.test\"}" "$GW/api/v1/userservice/users/register" --max-time 20 >/dev/null
  RESP=$(curl -s -X POST -H "Content-Type: application/json" -d "{\"username\":\"$U\",\"password\":\"$PW\"}" "$GW/api/v1/users/login" --max-time 20)
  TOK=$(echo "$RESP" | python3 -c "import json,sys;print(json.load(sys.stdin)['data']['token'])" 2>/dev/null)
  BUYER=$(echo "$RESP" | python3 -c "import json,sys;print(json.load(sys.stdin)['data']['userId'])" 2>/dev/null)
  curl -s -H "Authorization: Bearer $TOK" "$GW/api/v1/inside_pay_service/inside_payment/$BUYER/50.00" --max-time 20 >/dev/null
  curl -s -X POST -H "Authorization: Bearer $ADMIN" -H "Content-Type: application/json" -d "{\"accountId\":\"$BUYER\",\"status\":1,\"price\":\"100.0\",\"boughtDate\":\"2026-01-01 10:00:00\",\"travelDate\":\"2027-12-01\",\"travelTime\":\"2027-12-01 10:00:00\",\"from\":\"Shang Hai\",\"to\":\"Su Zhou\",\"trainNumber\":\"G1234\",\"coachNumber\":5,\"seatClass\":2,\"seatNumber\":\"5A\",\"contactsName\":\"RC Buyer\",\"documentType\":1,\"contactsDocumentNumber\":\"RC-$TS\"}" "$GW/api/v1/orderservice/order" --max-time 20 >/dev/null
  sleep 1
  OID=$(curl -s -X POST -H "Authorization: Bearer $TOK" -H "Content-Type: application/json" -d "{\"loginId\":\"$BUYER\",\"enableStateQuery\":false,\"enableTravelDateQuery\":false,\"enableBoughtDateQuery\":false}" "$GW/api/v1/orderservice/order/query" --max-time 20 | python3 -c "import json,sys;print(json.load(sys.stdin)['data'][0]['id'])" 2>/dev/null)
  B0=$(refund_rows "$BUYER")
  CANCEL=$(curl -s -H "Authorization: Bearer $TOK" "$GW/api/v1/cancelservice/cancel/$OID/$BUYER" --max-time 25 | head -c 60)
  sleep 3
  B1=$(refund_rows "$BUYER")
  toggle none
  echo "leg=$LEG mode=$MODE buyer=$BUYER order=$OID cancel_ack=${CANCEL} refund_rows_before=$B0 refund_rows_after=$B1" | tee -a "$LOG"
  eval "B0_${LEG}=${B0:-ERR}; B1_${LEG}=${B1:-ERR}"
}

: > "$LOG"; echo "=== cancel-fabricatedack re-capture (N<=3) ===" | tee -a "$LOG"; PASS=0
for attempt in 1 2 3; do
  echo "--- attempt $attempt ---" | tee -a "$LOG"
  leg none CTRL; leg fabricatedack FLT
  # gate: control refund LANDS (B1>B0), fault refund LOST (B1==B0)
  cu=$([ "${B1_CTRL:-0}" -ge 1 ] 2>/dev/null && echo 1 || echo 0)
  fu=$([ "${B1_FLT:-0}" -ge 1 ] 2>/dev/null && echo 1 || echo 0)
  if [ "$cu" = 1 ] && [ "$fu" = 0 ]; then echo "  GATE PASS: control refund landed & fault refund lost = of-record" | tee -a "$LOG"; PASS=1; break
  elif [ "$cu" = 1 ] && [ "$fu" = 1 ]; then echo "  CONTRADICTION (fault refund landed despite toggle) = DISCLOSED ANOMALY -> STOP" | tee -a "$LOG"; PASS=2; break
  else echo "  gate not met (ctrl-landed=$cu flt-landed=$fu) -> retry" | tee -a "$LOG"; fi
done
echo "=== result: PASS=$PASS ===" | tee -a "$LOG"
