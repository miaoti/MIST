#!/usr/bin/env bash
# MYC per-SUT revival (plan rev 2 Phase 0(f) + single-tenant legs). Usage:
#   bash myc-revive.sh teastore|oteldemo|sockshop|bookinfo|down <ns>
# teastore/oteldemo restore from the Phase-1 ttomni snapshots; sockshop = scale-up +
# the mist:mist RabbitMQ user + warm-up POST (standing runbook) + catalogue-db race wait;
# bookinfo = scale-up + reviews-v3 VS check. 'down <ns>' scales a namespace's workloads to 0.
set -u
export PATH="$PATH:/usr/local/bin"
K="kubectl --context kind-mist"
log(){ echo "[$(date +%H:%M:%S)] $*"; }
case "${1:?sut}" in
teastore)
  SNAP=$(ls -t /home/miaot/gate1-logs/ttomni/teastore-replica-snapshot-*.txt | head -1)
  log "teastore from $SNAP"
  awk '$1=="Deployment" && $3 ~ /^[0-9]+$/ {print $2, $3}' "$SNAP" | while read -r n r; do
    $K -n teastore scale deploy "$n" --replicas="$r" >/dev/null 2>&1 && echo "  up: $n=$r"
  done
  for d in $($K -n teastore get deploy --no-headers -o custom-columns=:.metadata.name); do
    timeout 420 $K -n teastore rollout status deploy/"$d" >/dev/null 2>&1 || log "WARN not-ready: $d"
  done
  log "teastore revived: $($K -n teastore get pods --no-headers | grep -c Running)/$($K -n teastore get pods --no-headers | wc -l) running"
  ;;
oteldemo)
  SNAP=$(ls -t /home/miaot/gate1-logs/ttomni/otel-demo-replica-snapshot-*.txt | head -1)
  log "otel-demo from $SNAP"
  awk '($1=="Deployment"||$1=="StatefulSet") && $3 ~ /^[0-9]+$/ {print tolower($1), $2, $3}' "$SNAP" | while read -r k n r; do
    kind=deploy; [ "$k" = "statefulset" ] && kind=sts
    $K -n otel-demo scale $kind "$n" --replicas="$r" >/dev/null 2>&1 && echo "  up: $kind/$n=$r"
  done
  sleep 240   # JVM/kafka settle (small graph; flap rail: no probe-hammering)
  log "otel-demo revived: $($K -n otel-demo get pods --no-headers | grep -c Running) running"
  ;;
sockshop)
  NS=sock-shop
  log "sockshop scale-up (all deployments to 1)"
  for d in $($K -n $NS get deploy --no-headers -o custom-columns=:.metadata.name); do
    $K -n $NS scale deploy "$d" --replicas=1 >/dev/null 2>&1
  done
  for d in catalogue-db catalogue rabbitmq front-end orders shipping queue-master user user-db carts carts-db orders-db payment; do
    timeout 420 $K -n $NS rollout status deploy/"$d" >/dev/null 2>&1 || true
  done
  log "rabbitmq mist:mist user (standing runbook)"
  RP=$($K -n $NS get pods --no-headers | awk '/^rabbitmq/{print $1; exit}')
  $K -n $NS exec "$RP" -c rabbitmq -- rabbitmqctl add_user mist mist 2>/dev/null || echo "  (user exists)"
  $K -n $NS exec "$RP" -c rabbitmq -- rabbitmqctl set_permissions -p / mist '.*' '.*' '.*' 2>/dev/null || true
  log "warm-up POST via front-end"
  $K -n $NS port-forward svc/front-end 8079:80 >/tmp/pf-ss.log 2>&1 & P=$!
  sleep 6
  curl -s -o /dev/null -w "  warmup register: HTTP %{http_code}\n" -X POST http://localhost:8079/register -H 'Content-Type: application/json' -d '{"username":"warmup'$RANDOM'","password":"pw","email":"w@w.w"}'
  kill $P 2>/dev/null
  log "sockshop revived: $($K -n $NS get pods --no-headers | grep -c Running) running"
  ;;
bookinfo)
  NS=bookinfo
  $K -n $NS get deploy --no-headers -o custom-columns=:.metadata.name 2>/dev/null | while read -r d; do
    $K -n $NS scale deploy "$d" --replicas=1 >/dev/null 2>&1
  done
  sleep 60
  log "reviews VS state:"; $K -n $NS get virtualservice reviews -o jsonpath='{.spec.http[0].route[0].destination.subset}' 2>/dev/null; echo
  log "bookinfo revived: $($K -n $NS get pods --no-headers | grep -c Running) running"
  ;;
down)
  NS="${2:?ns}"
  log "scaling $NS workloads to 0"
  $K -n "$NS" get deploy --no-headers -o custom-columns=:.metadata.name 2>/dev/null | while read -r d; do $K -n "$NS" scale deploy "$d" --replicas=0 >/dev/null 2>&1; done
  $K -n "$NS" get sts --no-headers -o custom-columns=:.metadata.name 2>/dev/null | while read -r s; do $K -n "$NS" scale sts "$s" --replicas=0 >/dev/null 2>&1; done
  log "$NS down"
  ;;
esac
