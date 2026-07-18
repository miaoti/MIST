#!/bin/bash
# Phase B tail (doubleWrite PUT) + Phase C-E of revive-tt-full.sh, resumed after the
# PF-flap FATAL (nacos itself was up; the readiness PF never connected).
set -u
NS=trainticket
SNAP=/home/miaot/gate1-logs/tenancy-window/tt-replica-snapshot.txt
log(){ echo "[$(date +%H:%M:%S)] $*"; }

pkill -f "port-forward svc/nacos" 2>/dev/null
kubectl -n "$NS" port-forward svc/nacos 18848:8848 >/tmp/pf-nacos.log 2>&1 &
PF=$!
sleep 5
R=$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:18848/nacos/v1/console/health/readiness" || true)
log "nacos readiness: $R"
[ "$R" = "200" ] || { log "FATAL: nacos still not ready via PF"; kill $PF 2>/dev/null; exit 1; }
curl -s -X PUT "http://localhost:18848/nacos/v1/ns/operator/switches?entry=doubleWriteEnabled&value=false" || true
SW=$(curl -s "http://localhost:18848/nacos/v1/ns/operator/switches" | grep -o '"doubleWriteEnabled":[a-z]*')
log "switches: $SW"
kill $PF 2>/dev/null
echo "$SW" | grep -q 'false' || { log "FATAL: doubleWriteEnabled not false"; exit 1; }

log "=== Phase C: services from snapshot, batches of 6 ==="
mapfile -t ROWS < <(awk '$1=="Deployment" && $2 ~ /^ts-/ && $3 ~ /^[0-9]+$/ {print $2" "$3}' "$SNAP")
i=0
for row in "${ROWS[@]}"; do
  name=${row%% *}; reps=${row##* }
  kubectl -n "$NS" scale deploy "$name" --replicas="$reps" 2>/dev/null
  i=$((i+1))
  [ $((i % 6)) -eq 0 ] && { log "batch scaled ($i/${#ROWS[@]}); pausing 90s"; sleep 90; }
done
log "all ${#ROWS[@]} scaled; waiting for cancel-path + booking-path services"
for d in ts-gateway-service ts-ui-dashboard ts-auth-service ts-user-service ts-order-service ts-cancel-service ts-inside-payment-service ts-preserve-service ts-travel-service ts-basic-service ts-contacts-service ts-station-service ts-seat-service ts-config-service ts-security-service ts-food-service ts-verification-code-service; do
  kubectl -n "$NS" rollout status deploy/"$d" --timeout=900s || log "WARN: $d not ready in 900s"
done
CRASH=$(kubectl -n "$NS" get pods --no-headers | awk '$3=="CrashLoopBackOff"{print $1}')
[ -n "$CRASH" ] && { log "deleting crash-looping pods: $CRASH"; echo "$CRASH" | xargs -r kubectl -n "$NS" delete pod; }
pkill -f "port-forward svc/ts-ui-dashboard" 2>/dev/null
nohup kubectl -n "$NS" port-forward svc/ts-ui-dashboard 8080:8080 >/tmp/pf-tt-ui.log 2>&1 &
sleep 3
H=$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:8080/" || true)
log "ui PF probe: HTTP $H"
kubectl -n "$NS" get pods --no-headers | awk '{c[$3]++} END {for (k in c) printf "  %s=%d\n", k, c[k]}'
log "phase C-E complete"
