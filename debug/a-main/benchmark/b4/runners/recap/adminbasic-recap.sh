#!/bin/bash
# TT re-capture (rev2 PRE-REG) — Class A: adminbasic-contacts-lostwrite (JVM-flag fault).
# PREREQ: the deployed image is stale :1.0.0 -> set-image to :mistfault (rebuilt from fork) first.
# Fault ON = JAVA_TOOL_OPTIONS + -Dmist.fault.lostwrite.enabled=true (rollout). Read-back = MySQL
# ts.contacts TOTAL count-delta (the case's count-delta locator; server-gen id + fault returns
# data:null, same as adminroute). ABORT rule + fresh label-free contact + 90s settle after rollout.
set -u
GW="http://localhost:8080"; NS=trainticket; D=ts-admin-basic-info-service
AGENT=""  # adminbasic has NO /otel volume mount; read-back is MySQL, agent not needed
EV=/mnt/c/Users/miaot/Github/MIST/debug/a-main/benchmark/b4/cset/recap-adminbasic
mkdir -p "$EV"; LOG="$EV/attempts.log"; rm -f "$EV/DONE"
ADMIN=$(curl -s -X POST -H "Content-Type: application/json" -d '{"username":"admin","password":"222222"}' "$GW/api/v1/users/login" --max-time 20 | python3 -c "import json,sys;print(json.load(sys.stdin)['data']['token'])" 2>/dev/null)
[ -n "$ADMIN" ] || { echo "FATAL: no admin token"; exit 1; }
contacts_count(){ kubectl -n "$NS" exec tsdb-mysql-0 -c mysql -- sh -c "mysql -uroot -N -e \"select count(*) from ts.contacts;\" 2>/dev/null" 2>/dev/null | tr -d '[:space:]'; }
set_fault(){ # $1=on|off  (also pins the :mistfault image)
  kubectl -n "$NS" set image deploy/"$D" "$D=codewisdom/ts-admin-basic-info-service:mistfault" >/dev/null 2>&1
  if [ "$1" = on ]; then kubectl -n "$NS" set env deploy/"$D" JAVA_TOOL_OPTIONS="-Dmist.fault.lostwrite.enabled=true" >/dev/null
  else kubectl -n "$NS" set env deploy/"$D" JAVA_TOOL_OPTIONS="" >/dev/null; fi
  kubectl -n "$NS" rollout status deploy/"$D" --timeout=300s >/dev/null 2>&1; sleep 90
}
leg(){ # $1=faultstate $2=legname
  local FS="$1" LEG="$2" TS B4 AF DELTA ACK CID AID
  TS=$(date +%s%N | cut -c1-16)
  CID=$(cat /proc/sys/kernel/random/uuid); AID=$(cat /proc/sys/kernel/random/uuid)
  B4=$(contacts_count)
  ACK=$(curl -s -w "|HTTP=%{http_code}" -X POST -H "Authorization: Bearer $ADMIN" -H "Content-Type: application/json" -d "{\"id\":\"$CID\",\"accountId\":\"$AID\",\"name\":\"rb${TS}\",\"documentType\":1,\"documentNumber\":\"DN${TS}\",\"phoneNumber\":\"1${TS}\"}" "$GW/api/v1/adminbasicservice/adminbasic/contacts" --max-time 25)
  sleep 3; AF=$(contacts_count); DELTA=$(( ${AF:-0} - ${B4:-0} ))
  echo "leg=$LEG fault=$FS ack=${ACK} contacts_count ${B4}->${AF} delta=${DELTA}" | tee -a "$LOG"
  eval "RES_${LEG}=${DELTA}"
}
: > "$LOG"; echo "=== adminbasic-contacts-lostwrite re-capture (N=1; :mistfault set-image) ===" | tee -a "$LOG"; PASS=0
for attempt in 1; do
  echo "--- attempt $attempt ---" | tee -a "$LOG"
  set_fault off; leg off CTRL
  set_fault on;  leg on  FLT
  set_fault off
  if [ "${RES_CTRL}" -ge 1 ] 2>/dev/null && [ "${RES_FLT}" -eq 0 ] 2>/dev/null; then echo "  GATE PASS: control PERSISTED (+${RES_CTRL}) & fault LOST (+${RES_FLT}) = of-record" | tee -a "$LOG"; PASS=1
  elif [ "${RES_FLT}" -ge 1 ] 2>/dev/null && [ "${RES_CTRL}" -ge 1 ] 2>/dev/null; then echo "  CONTRADICTION (fault persisted despite flag) = DISCLOSED ANOMALY" | tee -a "$LOG"; PASS=2
  else echo "  gate not met (ctrl=${RES_CTRL} flt=${RES_FLT})" | tee -a "$LOG"; fi
done
echo "=== result: PASS=$PASS ===" | tee -a "$LOG"; touch "$EV/DONE"
