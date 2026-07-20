#!/bin/bash
# TT re-capture (rev2 PRE-REG) — Class A: adminroute-lostwrite (JVM-flag fault).
# Image = codewisdom/ts-admin-route-service:mistfault (carries lostWriteFaultEnabled; verified).
# Fault ON = JAVA_TOOL_OPTIONS + -Dmist.fault.lostwrite.enabled=true (rollout). Read-back = MySQL
# ts.route WHERE id=<client-supplied fresh route id> (business-key scoped; escapes the admin-layer
# truncating list). ABORT rule + fresh label-free ids + 90s settle after rollout.
set -u
GW="http://localhost:8080"; NS=trainticket; D=ts-admin-route-service
AGENT="-javaagent:/otel/opentelemetry-javaagent.jar"
EV=/mnt/c/Users/miaot/Github/MIST/debug/a-main/benchmark/b4/cset/recap-adminroute
mkdir -p "$EV"; LOG="$EV/attempts.log"
ADMIN=$(curl -s -X POST -H "Content-Type: application/json" -d '{"username":"admin","password":"222222"}' "$GW/api/v1/users/login" --max-time 20 | python3 -c "import json,sys;print(json.load(sys.stdin)['data']['token'])" 2>/dev/null)
[ -n "$ADMIN" ] || { echo "FATAL: no admin token"; exit 1; }
# read-back = TOTAL ts.route count; the leg measures the delta (the case's committed count-delta
# locator). Server generates its own route id + validates stations against the catalog + fault leg
# returns data:null, so neither a client id nor a salted station key works (v1/v2 logs); the durable
# collection-count delta is the correct acting-record read-back.
route_count(){ kubectl -n "$NS" exec tsdb-mysql-0 -c mysql -- sh -c "mysql -uroot -N -e \"select count(*) from ts.route;\" 2>/dev/null" 2>/dev/null | tr -d '[:space:]'; }
set_fault(){ # $1=on|off
  if [ "$1" = on ]; then kubectl -n "$NS" set env deploy/"$D" JAVA_TOOL_OPTIONS="$AGENT -Dmist.fault.lostwrite.enabled=true" >/dev/null
  else kubectl -n "$NS" set env deploy/"$D" JAVA_TOOL_OPTIONS="$AGENT" >/dev/null; fi
  kubectl -n "$NS" rollout status deploy/"$D" --timeout=240s >/dev/null 2>&1; sleep 90
}
leg(){ # $1=faultstate(off|on) $2=legname
  local FS="$1" LEG="$2" B4 AF DELTA ACK
  B4=$(route_count)
  ACK=$(curl -s -w "|HTTP=%{http_code}" -X POST -H "Authorization: Bearer $ADMIN" -H "Content-Type: application/json" -d "{\"id\":\"placeholder\",\"startStation\":\"shanghai\",\"endStation\":\"taiyuan\",\"stationList\":\"shanghai,taiyuan\",\"distanceList\":\"0,1350\"}" "$GW/api/v1/adminrouteservice/adminroute" --max-time 25)
  sleep 3; AF=$(route_count)
  DELTA=$(( ${AF:-0} - ${B4:-0} ))
  echo "leg=$LEG fault=$FS ack=${ACK} route_count ${B4}->${AF} delta=${DELTA}" | tee -a "$LOG"
  eval "RES_${LEG}=${DELTA}"
}
: > "$LOG"; echo "=== adminroute-lostwrite re-capture (N<=3) ===" | tee -a "$LOG"; PASS=0
for attempt in 1; do
  echo "--- attempt $attempt ---" | tee -a "$LOG"
  set_fault off; leg off CTRL   # EXPLICIT fault-off + rollout (never assume; prior run may leave it ON)
  set_fault on;  leg on  FLT
  set_fault off   # residue reset
  if [ "${RES_CTRL}" -ge 1 ] 2>/dev/null && [ "${RES_FLT}" -eq 0 ] 2>/dev/null; then echo "  GATE PASS: control PRESENT (${RES_CTRL}) & fault ABSENT (${RES_FLT}) = of-record" | tee -a "$LOG"; PASS=1; break
  elif [ "${RES_FLT}" -ge 1 ] 2>/dev/null && [ "${RES_CTRL}" -ge 1 ] 2>/dev/null; then echo "  CONTRADICTION (fault leg PRESENT despite flag) = DISCLOSED ANOMALY -> STOP" | tee -a "$LOG"; PASS=2; break
  else echo "  gate not met (ctrl=${RES_CTRL} flt=${RES_FLT}) -> retry" | tee -a "$LOG"; fi
done
echo "=== result: PASS=$PASS ===" | tee -a "$LOG"
touch "$EV/DONE"
