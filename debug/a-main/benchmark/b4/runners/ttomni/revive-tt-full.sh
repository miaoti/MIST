#!/usr/bin/env bash
# TT-OMNIBUS Phase 2(i): FULL-GRAPH TrainTicket revival (wave-tt-omnibus-plan.md rev 2.1).
# Snapshot-driven: restores per-workload replicas from tt-replica-snapshot.txt — NOT the
# S3 8-service subgraph (that excludes the cancel services). Runs in WSL:
#   wsl -e bash /path/to/revive-tt-full.sh
# RAILS (checklist §2.6, 3bb8209): services come up in SMALL BATCHES — every batch of TT
# pod (re)starts can trigger a ~5-15 min SELF-RECOVERING WSL unresponsive window
# (0x8007274c). NEVER `wsl --shutdown`. Do NOT hammer probes. Expect the window.
set -u
NS=trainticket
SNAP="${TT_SNAPSHOT:-/home/miaot/gate1-logs/tenancy-window/tt-replica-snapshot.txt}"
BATCH="${TT_BATCH:-6}"          # small-batch size (services per batch)
BATCH_PAUSE="${TT_BATCH_PAUSE:-90}"   # seconds between batches (JVM boot spike absorption)
log(){ echo "[$(date +%H:%M:%S)] $*"; }

[ -f "$SNAP" ] || { echo "FATAL: snapshot not found: $SNAP"; exit 1; }

log "=== Phase A: infra (helm releases + PVCs persist; scale, never reinstall) ==="
kubectl -n "$NS" scale sts nacosdb-mysql --replicas=2 2>/dev/null
kubectl -n "$NS" scale sts tsdb-mysql   --replicas=2 2>/dev/null
kubectl -n "$NS" scale deploy rabbitmq  --replicas=1 2>/dev/null
# nacos: restore to the snapshot's member count (both members re-form jointly).
# snapshot format (verified): '<Kind> <name> <replicas>' e.g. 'StatefulSet nacos 2'
NACOS_R=$(awk '$1=="StatefulSet" && $2=="nacos" {print $3}' "$SNAP" | head -1)
kubectl -n "$NS" scale sts nacos --replicas="${NACOS_R:-2}" 2>/dev/null
log "waiting for infra pods (mysql x2 sts, rabbitmq, nacos)..."
kubectl -n "$NS" rollout status sts/nacosdb-mysql --timeout=600s
kubectl -n "$NS" rollout status sts/tsdb-mysql   --timeout=600s
kubectl -n "$NS" rollout status sts/nacos        --timeout=900s

trap 'log "EXIT trap: script leaving with rc=$? (a missing FATAL above this = external kill)"' EXIT
log "=== Phase B: nacos readiness + the MANDATORY doubleWrite rule (2026-07-10 incident) ==="
# readiness via a SELF-HEALING PF on 18848: kubectl port-forward EXITS on its first
# refused connection (nacos early-boot), so re-create it whenever it has died.
PF_NACOS=0
R=000
for i in $(seq 1 60); do
  if ! kill -0 "$PF_NACOS" 2>/dev/null; then
    kubectl -n "$NS" port-forward svc/nacos 18848:8848 >/tmp/pf-nacos.log 2>&1 &
    PF_NACOS=$!
    sleep 4
  fi
  R=$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:18848/nacos/v1/console/health/readiness" || true)
  [ "$R" = "200" ] && break
  sleep 10
done
[ "$R" = "200" ] || { log "FATAL: nacos readiness never OK (last=$R). If 'Distro protocol is not initialized': delete BOTH nacos pods together and re-run."; kill $PF_NACOS 2>/dev/null; exit 1; }
log "nacos ready; PUT doubleWriteEnabled=false (required after EVERY nacos restart)"
curl -s -X PUT "http://localhost:18848/nacos/v1/ns/operator/switches?entry=doubleWriteEnabled&value=false" || true
SW=$(curl -s "http://localhost:18848/nacos/v1/ns/operator/switches" | grep -o '"doubleWriteEnabled":[a-z]*')
log "switches: $SW"
echo "$SW" | grep -q 'false' || { log "FATAL: doubleWriteEnabled not false"; kill $PF_NACOS 2>/dev/null; exit 1; }
kill $PF_NACOS 2>/dev/null

log "=== Phase C: services from the snapshot, SMALL BATCHES of $BATCH (WSL flap rail) ==="
# snapshot format (verified): 'Deployment <name> <replicas>' rows for ts-* services
mapfile -t ROWS < <(awk '$1=="Deployment" && $2 ~ /^ts-/ && $3 ~ /^[0-9]+$/ {print $2" "$3}' "$SNAP")
[ "${#ROWS[@]}" -gt 0 ] || { log "FATAL: no ts-* rows parsed from snapshot"; exit 1; }
i=0
for row in "${ROWS[@]}"; do
  name=${row%% *}; name=${name#deployment.apps/}; name=${name#deploy/}
  reps=${row##* }
  kubectl -n "$NS" scale deploy "$name" --replicas="$reps" 2>/dev/null
  i=$((i+1))
  if [ $((i % BATCH)) -eq 0 ]; then
    log "batch of $BATCH scaled ($i/${#ROWS[@]}); pausing ${BATCH_PAUSE}s (expect possible WSL slow-window; do not intervene)"
    sleep "$BATCH_PAUSE"
  fi
done
log "all ${#ROWS[@]} services scaled; waiting for the CANCEL-PATH services specifically"
for d in ts-gateway-service ts-ui-dashboard ts-auth-service ts-user-service ts-order-service ts-cancel-service ts-inside-payment-service; do
  kubectl -n "$NS" rollout status deploy/"$d" --timeout=900s || log "WARN: $d not ready in 900s (check for crash-loop -> delete pod to reset backoff per the nacos rule)"
done

log "=== Phase D: crash-loop sweep (nacos-rule step 4) ==="
CRASH=$(kubectl -n "$NS" get pods --no-headers | awk '$3=="CrashLoopBackOff"{print $1}')
if [ -n "$CRASH" ]; then
  log "deleting crash-looping pods to reset backoff: $CRASH"
  echo "$CRASH" | xargs -r kubectl -n "$NS" delete pod
fi

log "=== Phase E: standing PF for the window (ts-ui-dashboard 8080) ==="
pkill -f "port-forward svc/ts-ui-dashboard" 2>/dev/null
nohup kubectl -n "$NS" port-forward svc/ts-ui-dashboard 8080:8080 >/tmp/pf-tt-ui.log 2>&1 &
sleep 3
H=$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:8080/" || true)
log "ui-dashboard PF probe: HTTP $H (302/200 expected)"
kubectl -n "$NS" get pods --no-headers | awk '{c[$3]++} END {for (k in c) printf "  %s=%d\n", k, c[k]}'
log "revival complete. Probe-first health gates (login journey) run from the leg runner, NOT here."
