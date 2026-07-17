#!/bin/bash
# F1 (asyncRefundSeqFaultMode, LOST) B-m6 + capture. Cancel-refund flow; fault masks the refund.
set -u
GW="http://localhost:8080"
EV=/mnt/c/Users/miaot/Github/MIST/debug/a-main/benchmark/b4/cset/fcorpus-f1
mkdir -p "$EV"
ADMIN_TOK=$(curl -s -X POST -H "Content-Type: application/json" -d "{\"username\":\"admin\",\"password\":\"222222\"}" "$GW/api/v1/users/login" --max-time 20 | python3 -c "import json,sys;print(json.load(sys.stdin)[\"data\"][\"token\"])" 2>/dev/null)
run_leg() {
  MODE="$1"; LEG="$2"; TS=$(date +%s)
  U="f1${LEG}${TS}"; PW="pass123456"; DOC="F1D-${LEG}-${TS}"
  curl -s -H "Authorization: Bearer $ADMIN_TOK" "$GW/api/v1/cancelservice/cancel/test/asyncrefundfaultmode/$MODE" --max-time 15 >/dev/null
  curl -s -X POST -H "Content-Type: application/json" -d "{\"userName\":\"$U\",\"password\":\"$PW\",\"gender\":1,\"documentType\":1,\"documentNum\":\"$DOC\",\"email\":\"$U@corpus.test\"}" "$GW/api/v1/userservice/users/register" --max-time 20 >/dev/null
  RESP=$(curl -s -X POST -H "Content-Type: application/json" -d "{\"username\":\"$U\",\"password\":\"$PW\"}" "$GW/api/v1/users/login" --max-time 20)
  TOK=$(echo "$RESP" | python3 -c "import json,sys;print(json.load(sys.stdin)[\"data\"][\"token\"])" 2>/dev/null)
  BUYER=$(echo "$RESP" | python3 -c "import json,sys;print(json.load(sys.stdin)[\"data\"][\"userId\"])" 2>/dev/null)
  curl -s -H "Authorization: Bearer $TOK" "$GW/api/v1/inside_pay_service/inside_payment/$BUYER/50.00" --max-time 20 >/dev/null
  curl -s -X POST -H "Authorization: Bearer $ADMIN_TOK" -H "Content-Type: application/json" -d "{\"accountId\":\"$BUYER\",\"status\":1,\"price\":\"100.0\",\"boughtDate\":\"2026-01-01 10:00:00\",\"travelDate\":\"2027-12-01\",\"travelTime\":\"2027-12-01 10:00:00\",\"from\":\"Shang Hai\",\"to\":\"Su Zhou\",\"trainNumber\":\"G1234\",\"coachNumber\":5,\"seatClass\":2,\"seatNumber\":\"5A\",\"contactsName\":\"F1 Buyer\",\"documentType\":1,\"contactsDocumentNumber\":\"$DOC\"}" "$GW/api/v1/orderservice/order" --max-time 20 >/dev/null
  sleep 1
  OID=$(curl -s -X POST -H "Authorization: Bearer $TOK" -H "Content-Type: application/json" -d "{\"loginId\":\"$BUYER\",\"enableStateQuery\":false,\"enableTravelDateQuery\":false,\"enableBoughtDateQuery\":false}" "$GW/api/v1/orderservice/order/query" --max-time 20 | python3 -c "import json,sys;print(json.load(sys.stdin)[\"data\"][0][\"id\"])" 2>/dev/null)
  BAL0=$(curl -s -H "Authorization: Bearer $ADMIN_TOK" "$GW/api/v1/inside_pay_service/inside_payment/account" --max-time 20 | python3 -c "import json,sys;[print(r[\"balance\"]) for r in json.load(sys.stdin)[\"data\"] if r[\"userId\"]==\"$BUYER\"]" 2>/dev/null)
  CANCEL=$(curl -s -H "Authorization: Bearer $TOK" "$GW/api/v1/cancelservice/cancel/$OID/$BUYER" --max-time 25 | head -c 90)
  sleep 3
  BAL1=$(curl -s -H "Authorization: Bearer $ADMIN_TOK" "$GW/api/v1/inside_pay_service/inside_payment/account" --max-time 20 | python3 -c "import json,sys;[print(r[\"balance\"]) for r in json.load(sys.stdin)[\"data\"] if r[\"userId\"]==\"$BUYER\"]" 2>/dev/null)
  echo "LEG=$LEG mode=$MODE buyer=$BUYER orderId=$OID cancel_ack=${CANCEL} bal_before=$BAL0 bal_after=$BAL1" | tee -a "$EV/f1-legs.log"
}
: > "$EV/f1-legs.log"
run_leg none CTRL
run_leg lost FLT
curl -s -H "Authorization: Bearer $ADMIN_TOK" "$GW/api/v1/cancelservice/cancel/test/asyncrefundfaultmode/none" --max-time 15 >/dev/null
echo "--- restored ---"; cat "$EV/f1-legs.log"
