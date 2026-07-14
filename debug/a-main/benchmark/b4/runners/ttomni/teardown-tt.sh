#!/usr/bin/env bash
# TT-OMNIBUS Phase 3: TT close-out — snapshot current replicas, then scale services to 0.
# Infra (mysql/nacos/rabbitmq) fate is a per-run decision: pass KEEP_INFRA=0 to also scale
# infra down (PVCs persist either way; helm releases are never uninstalled).
# Scale-DOWN does not trigger the JVM-boot WSL flap; no batching needed.
set -u
NS=trainticket
OUT="${TT_SNAPSHOT_OUT:-/home/miaot/gate1-logs/ttomni/tt-replica-snapshot-$(date +%Y%m%d-%H%M).txt}"
KEEP_INFRA="${KEEP_INFRA:-1}"
log(){ echo "[$(date +%H:%M:%S)] $*"; }

mkdir -p "$(dirname "$OUT")"
log "snapshotting current replicas -> $OUT"
kubectl -n "$NS" get deploy,sts -o custom-columns='NAME:.metadata.name,REPLICAS:.spec.replicas' --no-headers > "$OUT"
cat "$OUT" | head -60

log "scaling ts-* services to 0"
kubectl -n "$NS" get deploy --no-headers -o custom-columns=':.metadata.name' \
  | grep '^ts-' | xargs -r -n1 -I{} kubectl -n "$NS" scale deploy {} --replicas=0

if [ "$KEEP_INFRA" = "0" ]; then
  log "KEEP_INFRA=0: scaling infra down too (PVCs persist)"
  kubectl -n "$NS" scale sts nacos --replicas=0 2>/dev/null
  kubectl -n "$NS" scale sts nacosdb-mysql --replicas=0 2>/dev/null
  kubectl -n "$NS" scale sts tsdb-mysql --replicas=0 2>/dev/null
  kubectl -n "$NS" scale deploy rabbitmq --replicas=0 2>/dev/null
else
  log "KEEP_INFRA=1: infra left up (nacos/mysql/rabbitmq)"
fi
pkill -f "port-forward svc/ts-ui-dashboard" 2>/dev/null
log "teardown complete."
kubectl -n "$NS" get pods --no-headers 2>/dev/null | wc -l
