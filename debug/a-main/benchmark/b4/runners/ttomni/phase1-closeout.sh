#!/usr/bin/env bash
# TT-OMNIBUS Phase 1: tenancy close-out — snapshot + scale OTel-Demo and TeaStore to 0
# (teardown-verified), RAM checkpoint, disk report. Fully reversible (scale-up restores;
# PVC-less TeaStore db regenerates demo data by design — its captures are files, closed).
set -u
STAMP=$(date +%Y%m%d-%H%M)
OUT=/home/miaot/gate1-logs/ttomni
mkdir -p "$OUT"
log(){ echo "[$(date +%H:%M:%S)] $*"; }

for NS in otel-demo teastore; do
  log "=== $NS: snapshot -> $OUT/${NS}-replica-snapshot-$STAMP.txt"
  kubectl -n "$NS" get deploy,sts -o custom-columns='KIND:.kind,NAME:.metadata.name,REPLICAS:.spec.replicas' --no-headers > "$OUT/${NS}-replica-snapshot-$STAMP.txt" 2>/dev/null
  wc -l "$OUT/${NS}-replica-snapshot-$STAMP.txt"
  log "=== $NS: scale all deploy+sts to 0"
  kubectl -n "$NS" get deploy --no-headers -o custom-columns=':.metadata.name' 2>/dev/null \
    | xargs -r -n1 -I{} kubectl -n "$NS" scale deploy {} --replicas=0
  kubectl -n "$NS" get sts --no-headers -o custom-columns=':.metadata.name' 2>/dev/null \
    | xargs -r -n1 -I{} kubectl -n "$NS" scale sts {} --replicas=0
done

log "=== waiting 60s then verifying 0 pods ==="
sleep 60
for NS in otel-demo teastore; do
  N=$(kubectl -n "$NS" get pods --no-headers 2>/dev/null | grep -cv Terminating || true)
  T=$(kubectl -n "$NS" get pods --no-headers 2>/dev/null | grep -c Terminating || true)
  log "$NS: non-terminating=$N terminating=$T"
done

log "=== RAM checkpoint ==="
free -h
log "=== disk ==="
df -h / /home 2>/dev/null | tail -3
docker system df 2>/dev/null | head -6
log "Phase 1 close-out complete (re-check pods=0 before Phase 2)."
