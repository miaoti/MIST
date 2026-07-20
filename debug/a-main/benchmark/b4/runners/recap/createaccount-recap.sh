#!/bin/bash
# TT re-capture (rev2 PRE-REG) — Class B: createaccount-agreement.
# Fault = createAccountFaultMode=fabricatedack (ack success WITHOUT persisting the account).
# Read-back = MySQL ts.inside_money WHERE user_id = <fresh label-free key> (business-key scoped,
# escapes the truncating global /account list). ABORT rule: record ONLY if control PRESENT and
# fault ABSENT; else stale/mis-toggle -> abort. Toggle reset after fault; fresh key per leg.
set -u
GW="http://localhost:8080"
EV=/mnt/c/Users/miaot/Github/MIST/debug/a-main/benchmark/b4/cset/recap-createaccount
mkdir -p "$EV"; LOG="$EV/attempts.log"
ADMIN=$(curl -s -X POST -H "Content-Type: application/json" -d '{"username":"admin","password":"222222"}' "$GW/api/v1/users/login" --max-time 20 | python3 -c "import json,sys;print(json.load(sys.stdin)['data']['token'])" 2>/dev/null)
[ -n "$ADMIN" ] || { echo "FATAL: no admin token"; exit 1; }

toggle(){ curl -s -H "Authorization: Bearer $ADMIN" "$GW/api/v1/inside_pay_service/inside_payment/test/createfaultmode/$1" --max-time 15 >/dev/null; }
readback(){ # $1=user_id -> count of inside_money rows (business-key membership)
  wsl_noop=1
  kubectl -n trainticket exec tsdb-mysql-0 -c mysql -- sh -c "mysql -uroot -N -e \"select count(*) from ts.inside_money where user_id='$1';\" 2>/dev/null" 2>/dev/null | tr -d '[:space:]'
}

leg(){ # $1=mode(none|fabricatedack) $2=legname
  local MODE="$1" LEG="$2" TS ACCT MONEY ACK CNT
  # fresh LABEL-FREE key (request-derived; NO leg/fault marker)
  TS=$(date +%s%N | cut -c1-16); ACCT="ra${TS}${RANDOM}"; MONEY="100"
  if [ "$MODE" = fabricatedack ]; then toggle fabricatedack; else toggle none; fi   # assert-none before control too
  ACK=$(curl -s -w "|HTTP=%{http_code}" -X POST -H "Authorization: Bearer $ADMIN" -H "Content-Type: application/json" -d "{\"userId\":\"$ACCT\",\"money\":\"$MONEY\"}" "$GW/api/v1/inside_pay_service/inside_payment/account" --max-time 25)
  sleep 3
  CNT=$(readback "$ACCT")
  toggle none   # residue reset after EVERY leg
  echo "leg=$LEG mode=$MODE user_id=$ACCT ack=${ACK} inside_money_rows=${CNT:-ERR}" | tee -a "$LOG"
  # export for the gate
  eval "RES_${LEG}=${CNT:-ERR}"
}

: > "$LOG"
echo "=== createaccount-agreement re-capture (N<=3 cap; first passing = of record) ===" | tee -a "$LOG"
PASS=0
for attempt in 1 2 3; do
  echo "--- attempt $attempt ---" | tee -a "$LOG"
  leg none CTRL
  leg fabricatedack FLT
  # ABORT/gate rule: control PRESENT (>=1) and fault ABSENT (==0)
  if [ "${RES_CTRL}" = "ERR" ] || [ "${RES_FLT}" = "ERR" ]; then echo "  gate: readback ERROR -> retry" | tee -a "$LOG"; continue; fi
  if [ "${RES_CTRL}" -ge 1 ] 2>/dev/null && [ "${RES_FLT}" -eq 0 ] 2>/dev/null; then
    echo "  GATE PASS: control PRESENT (${RES_CTRL}) & fault ABSENT (${RES_FLT}) = of-record" | tee -a "$LOG"; PASS=1; break
  elif [ "${RES_FLT}" -ge 1 ] 2>/dev/null && [ "${RES_CTRL}" -ge 1 ] 2>/dev/null; then
    echo "  CONTRADICTION (fault leg PRESENT despite toggle) = DISCLOSED ANOMALY, not a re-roll -> STOP" | tee -a "$LOG"; PASS=2; break
  else
    echo "  gate not met (ctrl=${RES_CTRL} flt=${RES_FLT}) -> retry" | tee -a "$LOG"
  fi
done
echo "=== result: PASS=$PASS (1=captured,2=anomaly-disclosed,0=exhausted) ===" | tee -a "$LOG"
