#!/bin/bash
# F13/F11/F20 B-m6 (cancel-family; admin-created orders, no preserve needed).
# Single-fault discipline: each fault toggled corrupt ONLY for its own fault leg.
set -u
GW="http://localhost:8080"
EVROOT=/mnt/c/Users/miaot/Github/MIST/debug/a-main/benchmark/b4/cset
ADMIN_TOK=$(curl -s -X POST -H "Content-Type: application/json" -d '{"username":"admin","password":"222222"}' "$GW/api/v1/users/login" --max-time 20 | python3 -c "import json,sys;print(json.load(sys.stdin)['data']['token'])" 2>/dev/null)
[ -n "$ADMIN_TOK" ] || { echo "FATAL: no admin token"; exit 1; }

toggle() { curl -s -H "Authorization: Bearer $ADMIN_TOK" "$GW$1" --max-time 15 >/dev/null; }

mkuser() { # $1=tag -> sets U_TOK U_ID
  local TS U PW
  TS=$(date +%s%N | cut -c1-13); U="fc${1}${TS}"; PW="pass123456"
  curl -s -X POST -H "Content-Type: application/json" -d "{\"userName\":\"$U\",\"password\":\"$PW\",\"gender\":1,\"documentType\":1,\"documentNum\":\"FCD-$U\",\"email\":\"$U@corpus.test\"}" "$GW/api/v1/userservice/users/register" --max-time 20 >/dev/null
  local RESP; RESP=$(curl -s -X POST -H "Content-Type: application/json" -d "{\"username\":\"$U\",\"password\":\"$PW\"}" "$GW/api/v1/users/login" --max-time 20)
  U_TOK=$(echo "$RESP" | python3 -c "import json,sys;print(json.load(sys.stdin)['data']['token'])" 2>/dev/null)
  U_ID=$(echo "$RESP" | python3 -c "import json,sys;print(json.load(sys.stdin)['data']['userId'])" 2>/dev/null)
}

mkorder() { # $1=accountId $2=status [$3=travelDate default 2027-12-01] -> ORDER_ID
  local TS DOC TD; TS=$(date +%s%N | cut -c1-13); DOC="FCO-$TS"; TD="${3:-2027-12-01}"
  ORDER_ID=$(curl -s -X POST -H "Authorization: Bearer $ADMIN_TOK" -H "Content-Type: application/json" -d "{\"accountId\":\"$1\",\"status\":$2,\"price\":\"100.0\",\"boughtDate\":\"2026-01-01 10:00:00\",\"travelDate\":\"$TD\",\"travelTime\":\"$TD 10:00:00\",\"from\":\"Shang Hai\",\"to\":\"Su Zhou\",\"trainNumber\":\"G1234\",\"coachNumber\":5,\"seatClass\":2,\"seatNumber\":\"5A\",\"contactsName\":\"FC Buyer\",\"documentType\":1,\"contactsDocumentNumber\":\"$DOC\"}" "$GW/api/v1/orderservice/order" --max-time 20 | python3 -c "import json,sys;print(json.load(sys.stdin)['data']['id'])" 2>/dev/null)
}

balance_of() { # $1=userId
  curl -s -H "Authorization: Bearer $ADMIN_TOK" "$GW/api/v1/inside_pay_service/inside_payment/account" --max-time 20 | python3 -c "import json,sys;[print(r['balance']) for r in json.load(sys.stdin)['data'] if r['userId']=='$1']" 2>/dev/null
}

order_status() { # $1=orderId $2=userId
  curl -s -X POST -H "Authorization: Bearer $ADMIN_TOK" -H "Content-Type: application/json" -d "{\"loginId\":\"$2\",\"enableStateQuery\":false,\"enableTravelDateQuery\":false,\"enableBoughtDateQuery\":false}" "$GW/api/v1/orderservice/order/query" --max-time 20 | python3 -c "import json,sys;[print(r['status']) for r in json.load(sys.stdin)['data'] if r['id']=='$1']" 2>/dev/null
}

# ---------------- F13: refund-ignoring-settlement-state ----------------------------------------
# PAID order with a PAST travel date: control calculateRefund -> "0" (expired, no refund due);
# fault fullRefundIgnoringPaidState -> 80.00 drawn back anyway. (The NOTPAID divergence leg is
# DEFEATED by a pre-existing vanilla aliasing quirk: cancelFromOrder mutates the shared order
# object's status to CANCEL before calculateRefund reads it, so the NOTPAID guard never fires
# in this path — disclosed in the case file.)
f13_leg() { # $1=mode $2=leg
  [ "$1" = corrupt ] && toggle "/api/v1/cancelservice/cancel/test/paymentinflightfaultmode/corrupt"
  mkuser "13$2"
  curl -s -H "Authorization: Bearer $U_TOK" "$GW/api/v1/inside_pay_service/inside_payment/$U_ID/50.00" --max-time 20 >/dev/null
  mkorder "$U_ID" 1 "2026-01-05"
  sleep 1
  local B0 ACK B1 ST
  B0=$(balance_of "$U_ID")
  ACK=$(curl -s -H "Authorization: Bearer $U_TOK" "$GW/api/v1/cancelservice/cancel/$ORDER_ID/$U_ID" --max-time 25 | head -c 80)
  sleep 3
  B1=$(balance_of "$U_ID"); ST=$(order_status "$ORDER_ID" "$U_ID")
  toggle "/api/v1/cancelservice/cancel/test/paymentinflightfaultmode/none"
  echo "F13 LEG=$2 mode=$1 order=$ORDER_ID ack=$ACK bal=${B0}->${B1} status=$ST" | tee -a "$EVROOT/fcorpus-f13/legs.log"
}

# ---------------- F11: seq+fallible-recheck (PAID orders; fault: 2 cancels -> one CANCEL(4) one CHANGE(3)) ----
f11_leg() { # $1=mode $2=leg
  [ "$1" = corrupt ] && toggle "/api/v1/cancelservice/cancel/test/seqrecheckfaultmode/corrupt"
  mkuser "11$2"
  local O1 O2 A1 A2 S1 S2
  mkorder "$U_ID" 1; O1=$ORDER_ID; sleep 1
  mkorder "$U_ID" 1; O2=$ORDER_ID; sleep 1
  A1=$(curl -s -H "Authorization: Bearer $U_TOK" "$GW/api/v1/cancelservice/cancel/$O1/$U_ID" --max-time 25 | head -c 60)
  sleep 2
  A2=$(curl -s -H "Authorization: Bearer $U_TOK" "$GW/api/v1/cancelservice/cancel/$O2/$U_ID" --max-time 25 | head -c 60)
  sleep 3
  S1=$(order_status "$O1" "$U_ID"); S2=$(order_status "$O2" "$U_ID")
  toggle "/api/v1/cancelservice/cancel/test/seqrecheckfaultmode/none"
  echo "F11 LEG=$2 mode=$1 o1=$O1 ack1=$A1 status1=$S1 | o2=$O2 ack2=$A2 status2=$S2" | tee -a "$EVROOT/fcorpus-f11/legs.log"
}

# ---------------- F20: status skew on modifyOrder (set 1; control persists 1, fault persists 2) ----
f20_leg() { # $1=mode $2=leg
  [ "$1" = corrupt ] && toggle "/api/v1/orderservice/order/test/statusskewfaultmode/corrupt"
  mkuser "20$2"
  mkorder "$U_ID" 0; sleep 1
  local ACK ST
  ACK=$(curl -s -H "Authorization: Bearer $ADMIN_TOK" "$GW/api/v1/orderservice/order/status/$ORDER_ID/1" --max-time 20 | head -c 60)
  sleep 2
  ST=$(order_status "$ORDER_ID" "$U_ID")
  toggle "/api/v1/orderservice/order/test/statusskewfaultmode/none"
  echo "F20 LEG=$2 mode=$1 order=$ORDER_ID ack=$ACK persisted_status=$ST (set 1)" | tee -a "$EVROOT/fcorpus-f20/legs.log"
}

ONLY="${1:-all}"
mkdir -p "$EVROOT/fcorpus-f13" "$EVROOT/fcorpus-f11" "$EVROOT/fcorpus-f20"
if [ "$ONLY" = all ] || [ "$ONLY" = f13 ]; then : > "$EVROOT/fcorpus-f13/legs.log"; echo "=== F13 ==="; f13_leg none CTRL; f13_leg corrupt FLT; fi
if [ "$ONLY" = all ] || [ "$ONLY" = f11 ]; then : > "$EVROOT/fcorpus-f11/legs.log"; echo "=== F11 ==="; f11_leg none CTRL; f11_leg corrupt FLT; fi
if [ "$ONLY" = all ] || [ "$ONLY" = f20 ]; then : > "$EVROOT/fcorpus-f20/legs.log"; echo "=== F20 ==="; f20_leg none CTRL; f20_leg corrupt FLT; fi
echo "=== legs done ($ONLY) ==="
