#!/bin/bash
# F8 (userSelectionFaultMode, CORRUPTED) B-m6 re-capture with a preserved raw log —
# closes the verification round's F8 evidence-form gap (the original B-m6 was inline-
# captured with no legs.log/driver). Flow: register (documentType=1) -> read back
# /users/id/{id} -> compare documentType. Control: 1 (as submitted). Fault: 0 (default).
set -u
GW="http://localhost:8080"
EV=/mnt/c/Users/miaot/Github/MIST/debug/a-main/benchmark/b4/cset/fcorpus-f8
mkdir -p "$EV"
ADMIN_TOK=$(curl -s -X POST -H "Content-Type: application/json" -d '{"username":"admin","password":"222222"}' "$GW/api/v1/users/login" --max-time 20 | python3 -c "import json,sys;print(json.load(sys.stdin)['data']['token'])" 2>/dev/null)
[ -n "$ADMIN_TOK" ] || { echo "FATAL: no admin token"; exit 1; }

run_leg() { # $1=mode $2=leg
  local MODE="$1" LEG="$2" TS U PW RESP FUID DOC
  [ "$MODE" = corrupt ] && curl -s -H "Authorization: Bearer $ADMIN_TOK" "$GW/api/v1/userservice/users/test/selectionfaultmode/corrupt" --max-time 15 >/dev/null
  TS=$(date +%s%N | cut -c1-13); U="f8${LEG}${TS}"; PW="pass123456"
  RESP=$(curl -s -w "\nHTTP=%{http_code}" -X POST -H "Content-Type: application/json" -d "{\"userName\":\"$U\",\"password\":\"$PW\",\"gender\":1,\"documentType\":1,\"documentNum\":\"F8D-$TS\",\"email\":\"$U@corpus.test\"}" "$GW/api/v1/userservice/users/register" --max-time 20)
  ACK=$(echo "$RESP" | tail -1)
  FUID=$(curl -s -X POST -H "Content-Type: application/json" -d "{\"username\":\"$U\",\"password\":\"$PW\"}" "$GW/api/v1/users/login" --max-time 20 | python3 -c "import json,sys;print(json.load(sys.stdin)['data']['userId'])" 2>/dev/null)
  DOC=$(curl -s -H "Authorization: Bearer $ADMIN_TOK" "$GW/api/v1/userservice/users/id/$FUID" --max-time 20 | python3 -c "import json,sys;print(json.load(sys.stdin)['data']['documentType'])" 2>/dev/null)
  curl -s -H "Authorization: Bearer $ADMIN_TOK" "$GW/api/v1/userservice/users/test/selectionfaultmode/none" --max-time 15 >/dev/null
  echo "F8 LEG=$LEG mode=$MODE user=$U uid=$FUID register_$ACK submitted_documentType=1 persisted_documentType=$DOC" | tee -a "$EV/legs.log"
}

: > "$EV/legs.log"
run_leg none CTRL
run_leg corrupt FLT
echo "--- done ---"; cat "$EV/legs.log"
