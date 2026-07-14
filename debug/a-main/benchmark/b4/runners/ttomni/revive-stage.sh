#!/usr/bin/env bash
# TT-OMNIBUS staged revival (field revision of revive-tt-full.sh after runs 1-3):
# detached-inside-WSL processes died silently after their wsl.exe client exited, so each
# stage now runs as a FOREGROUND-ATTACHED call <=10 min. Stages are idempotent.
#   bash revive-stage.sh b        # nacos readiness + doubleWrite=false (infra must be up)
#   bash revive-stage.sh c 1 4    # scale service batches 1..4 (of 6 each; 90s pauses)
#   bash revive-stage.sh c 5 8    # scale service batches 5..8
#   bash revive-stage.sh d        # rollout waits (cancel path) + crash-loop sweep + summary
# RAILS: small batches (3bb8209 flap windows self-recover; never wsl --shutdown).
set -u
NS=trainticket
SNAP="${TT_SNAPSHOT:-/home/miaot/gate1-logs/tenancy-window/tt-replica-snapshot.txt}"
BATCH=6
BATCH_PAUSE=90
log(){ echo "[$(date +%H:%M:%S)] $*"; }
STAGE="${1:-}"

case "$STAGE" in
b)
  log "stage b: nacos readiness via self-healing PF + doubleWrite=false"
  PF=0; R=000
  for i in $(seq 1 40); do
    if ! kill -0 "$PF" 2>/dev/null; then
      # POD-level PF (field fix: svc/nacos endpoint resolution fed a dead target
      # while both pods answered 200 directly)
      kubectl -n "$NS" port-forward pod/nacos-0 18848:8848 >/tmp/pf-nacos.log 2>&1 &
      PF=$!
      sleep 4
    fi
    R=$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:18848/nacos/v1/console/health/readiness" || true)
    [ "$R" = "200" ] && break
    sleep 8
  done
  if [ "$R" != "200" ]; then
    log "FATAL: readiness never OK (last=$R). If 'Distro protocol is not initialized': delete BOTH nacos pods together, re-run stage b."
    kill "$PF" 2>/dev/null; exit 1
  fi
  curl -s -X PUT "http://localhost:18848/nacos/v1/ns/operator/switches?entry=doubleWriteEnabled&value=false" >/dev/null || true
  SW=$(curl -s "http://localhost:18848/nacos/v1/ns/operator/switches" | grep -o '"doubleWriteEnabled":[a-z]*')
  kill "$PF" 2>/dev/null
  log "switches: $SW"
  echo "$SW" | grep -q false || { log "FATAL: doubleWriteEnabled not false"; exit 1; }
  log "stage b OK"
  ;;
c)
  FROM="${2:?batch-from}"; TO="${3:?batch-to}"
  mapfile -t ROWS < <(awk '$1=="Deployment" && $2 ~ /^ts-/ && $3 ~ /^[0-9]+$/ {print $2" "$3}' "$SNAP")
  TOTAL=${#ROWS[@]}
  log "stage c: batches $FROM..$TO of $(( (TOTAL + BATCH - 1) / BATCH )) (total $TOTAL services)"
  for b in $(seq "$FROM" "$TO"); do
    s=$(( (b-1)*BATCH )); e=$(( b*BATCH )); [ "$e" -gt "$TOTAL" ] && e=$TOTAL
    [ "$s" -ge "$TOTAL" ] && { log "batch $b: past end"; break; }
    for idx in $(seq "$s" $((e-1)) ); do
      row=${ROWS[$idx]}; name=${row%% *}; reps=${row##* }
      kubectl -n "$NS" scale deploy "$name" --replicas="$reps" >/dev/null 2>&1 && echo -n "." || echo -n "x"
    done
    echo
    log "batch $b scaled ($s..$((e-1))); pausing ${BATCH_PAUSE}s (expect possible WSL slow-window; do not intervene)"
    sleep "$BATCH_PAUSE"
  done
  log "stage c $FROM..$TO done; pods now: $(kubectl -n "$NS" get pods --no-headers 2>/dev/null | wc -l)"
  ;;
d)
  log "stage d: cancel-path rollout waits"
  for dep in ts-gateway-service ts-ui-dashboard ts-auth-service ts-user-service ts-order-service ts-cancel-service ts-inside-payment-service; do
    kubectl -n "$NS" rollout status deploy/"$dep" --timeout=420s || log "WARN: $dep not ready (crash-loop? delete pod to reset backoff)"
  done
  CRASH=$(kubectl -n "$NS" get pods --no-headers | awk '$3=="CrashLoopBackOff"{print $1}')
  [ -n "$CRASH" ] && { log "deleting crash-loopers: $CRASH"; echo "$CRASH" | xargs -r kubectl -n "$NS" delete pod; }
  kubectl -n "$NS" get pods --no-headers | awk '{c[$3]++} END {for (k in c) printf "  %s=%d\n", k, c[k]}'
  log "stage d done (PF policy: each leg runner owns its own PF)"
  ;;
*)
  echo "usage: revive-stage.sh b | c FROM TO | d"; exit 2
  ;;
esac
