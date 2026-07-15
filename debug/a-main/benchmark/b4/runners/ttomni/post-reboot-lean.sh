#!/usr/bin/env bash
# TT-OMNIBUS post-reboot LEAN recovery (2026-07-15 wedge lesson): after the host/WSL
# reboot, the kind cluster cold-starts ALL 53 pods at once — the exact overload that
# wedged the box. This script races the java boot stampede: as soon as the API answers,
# scale the ~29 non-E5 services to 0 (they die cheap in ContainerCreating/early-boot),
# keep only the cancel-path + login chain + basic deps. Then the MANDATORY nacos
# doubleWrite sequence (fresh nacos boot re-enables 1.X doubleWrite from the PVC).
# Run attached: wsl -e bash post-reboot-lean.sh
set -u
K="kubectl --context kind-mist -n trainticket"
log(){ echo "[$(date +%H:%M:%S)] $*"; }

log "waiting for the K8s API (kind control-plane boot)..."
for i in $(seq 1 60); do
  timeout 8 $K get --raw /readyz >/dev/null 2>&1 && break
  sleep 10
done
timeout 8 $K get --raw /readyz >/dev/null 2>&1 || { log "FATAL: API never came up"; exit 1; }
log "API up. RACE: scaling non-E5 services to 0 NOW (before java RSS peaks)"

KEEP='ts-gateway-service ts-ui-dashboard ts-auth-service ts-user-service ts-verification-code-service ts-order-service ts-cancel-service ts-inside-payment-service ts-payment-service ts-station-service ts-basic-service ts-price-service ts-config-service ts-order-other-service'
DOWN=0
for d in $(timeout 20 $K get deploy --no-headers -o custom-columns=:.metadata.name | grep '^ts-'); do
  echo "$KEEP" | grep -qw "$d" && continue
  timeout 20 $K scale deploy "$d" --replicas=0 >/dev/null 2>&1 && DOWN=$((DOWN+1)) || echo "scale-fail: $d"
done
log "scaled $DOWN non-essential services to 0"

log "waiting for nacos StatefulSet (fresh boot from PVC)..."
timeout 900 $K rollout status sts/nacos 2>/dev/null || log "WARN: nacos rollout slow"

log "MANDATORY doubleWrite sequence (fresh nacos boot = 1.X mode re-enabled)"
PF=0; R=000
for i in $(seq 1 60); do
  if ! kill -0 "$PF" 2>/dev/null; then
    $K port-forward pod/nacos-0 18848:8848 >/tmp/pf-nacos.log 2>&1 &
    PF=$!
    sleep 4
  fi
  R=$(timeout 8 curl -s -o /dev/null -w '%{http_code}' "http://localhost:18848/nacos/v1/console/health/readiness" || true)
  [ "$R" = "200" ] && break
  sleep 8
done
log "nacos readiness=$R"
if [ "$R" = "200" ]; then
  timeout 8 curl -s -X PUT "http://localhost:18848/nacos/v1/ns/operator/switches?entry=doubleWriteEnabled&value=false" >/dev/null || true
  SW=$(timeout 8 curl -s "http://localhost:18848/nacos/v1/ns/operator/switches" | grep -o '"doubleWriteEnabled":[a-z]*')
  log "switches: $SW"
else
  log "WARN: readiness not OK — if 'Distro protocol is not initialized': delete BOTH nacos pods together + re-run"
fi
kill "$PF" 2>/dev/null

log "waiting for the E5 keep-set rollouts..."
for d in $KEEP; do
  timeout 420 $K rollout status deploy/"$d" >/dev/null 2>&1 && echo "  ready: $d" || echo "  NOT-READY: $d"
done
CRASH=$(timeout 20 $K get pods --no-headers | awk '$3=="CrashLoopBackOff"{print $1}')
[ -n "$CRASH" ] && { log "sweeping crash-loopers: $CRASH"; echo "$CRASH" | xargs -r timeout 30 $K delete pod --wait=false; }

log "pods summary:"; timeout 20 $K get pods --no-headers | awk '{c[$3]++} END {for (k in c) printf "  %s=%d\n", k, c[k]}'
log "RAM:"; free -h | head -2
log "lean recovery done — login probe runs from the caller"
