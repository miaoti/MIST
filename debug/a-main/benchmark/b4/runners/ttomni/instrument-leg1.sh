#!/usr/bin/env bash
# TT-OMNIBUS leg-1 instrumentation (verbatim from traced-capture-wave-plan.md §3, live-verified
# recipe): OTel javaagent 1.33.6 via kind-node hostPath /otel + JTO env; single strategic patch
# per deployment (scale-0 -> patch -> scale-1, ONE AT A TIME); do NOT fight the heap (-Xmx200m
# stands; agent overhead is native/metaspace — WATCH restarts).
#   bash instrument-leg1.sh canary            # ts-contacts-service canary, then leaves it ON
#                                             # (de-instrument later via restore)
#   bash instrument-leg1.sh apply SVC [SVC..] # instrument deployments (e.g. cancel path)
#   bash instrument-leg1.sh restore SVC [..]  # remove env+mount+volume, back to base
#   bash instrument-leg1.sh status            # who carries JTO now
set -u
NS=trainticket
OTLP=http://jaeger-collector.istio-system:4318
log(){ echo "[$(date +%H:%M:%S)] $*"; }

patch_one(){
  local D="$1"
  local C
  C=$(kubectl -n "$NS" get deploy "$D" -o jsonpath='{.spec.template.spec.containers[0].name}')
  [ -n "$C" ] || { log "FATAL: no container name for $D"; return 1; }
  log "instrumenting $D (container=$C): scale-0 -> patch -> scale-1"
  kubectl -n "$NS" scale deploy "$D" --replicas=0 >/dev/null
  sleep 3
  kubectl -n "$NS" patch deploy "$D" --type=strategic -p "{
    \"spec\":{\"template\":{\"spec\":{
      \"volumes\":[{\"name\":\"otel-agent\",\"hostPath\":{\"path\":\"/otel\",\"type\":\"Directory\"}}],
      \"containers\":[{\"name\":\"$C\",
        \"volumeMounts\":[{\"name\":\"otel-agent\",\"mountPath\":\"/otel\",\"readOnly\":true}],
        \"env\":[
          {\"name\":\"JAVA_TOOL_OPTIONS\",\"value\":\"-javaagent:/otel/opentelemetry-javaagent.jar\"},
          {\"name\":\"OTEL_SERVICE_NAME\",\"value\":\"$D\"},
          {\"name\":\"OTEL_EXPORTER_OTLP_ENDPOINT\",\"value\":\"$OTLP\"},
          {\"name\":\"OTEL_EXPORTER_OTLP_PROTOCOL\",\"value\":\"http/protobuf\"},
          {\"name\":\"OTEL_TRACES_SAMPLER\",\"value\":\"always_on\"},
          {\"name\":\"OTEL_METRICS_EXPORTER\",\"value\":\"none\"},
          {\"name\":\"OTEL_LOGS_EXPORTER\",\"value\":\"none\"}
        ]}]}}}}" >/dev/null || { log "FATAL: patch failed for $D"; return 1; }
  kubectl -n "$NS" scale deploy "$D" --replicas=1 >/dev/null
  kubectl -n "$NS" rollout status deploy/"$D" --timeout=420s || { log "WARN: $D rollout slow (watch OOM/restarts)"; return 1; }
  # JTO banner gate (recipe: verify 'Picked up' in pod logs)
  local POD
  POD=$(kubectl -n "$NS" get pods -l app="$D" --no-headers -o custom-columns=':.metadata.name' 2>/dev/null | head -1)
  [ -z "$POD" ] && POD=$(kubectl -n "$NS" get pods --no-headers | awk -v d="$D" '$1 ~ d {print $1}' | head -1)
  sleep 5
  if kubectl -n "$NS" logs "$POD" 2>/dev/null | grep -q "Picked up JAVA_TOOL_OPTIONS"; then
    log "$D: JTO banner OK ($POD)"
  else
    log "WARN: $D no JTO banner yet ($POD) — check logs"
  fi
  local RESTARTS
  RESTARTS=$(kubectl -n "$NS" get pod "$POD" --no-headers | awk '{print $4}')
  log "$D pod=$POD restarts=$RESTARTS"
}

restore_one(){
  local D="$1"
  local C
  C=$(kubectl -n "$NS" get deploy "$D" -o jsonpath='{.spec.template.spec.containers[0].name}')
  log "restoring $D (remove env + mount + volume)"
  kubectl -n "$NS" set env deploy/"$D" JAVA_TOOL_OPTIONS- OTEL_SERVICE_NAME- OTEL_EXPORTER_OTLP_ENDPOINT- OTEL_EXPORTER_OTLP_PROTOCOL- OTEL_TRACES_SAMPLER- OTEL_METRICS_EXPORTER- OTEL_LOGS_EXPORTER- >/dev/null
  kubectl -n "$NS" patch deploy "$D" --type=json -p '[
    {"op":"remove","path":"/spec/template/spec/containers/0/volumeMounts"},
    {"op":"remove","path":"/spec/template/spec/volumes"}]' >/dev/null 2>&1 || true
  kubectl -n "$NS" rollout status deploy/"$D" --timeout=420s || log "WARN: $D restore rollout slow"
}

case "${1:-}" in
canary)
  patch_one ts-contacts-service
  log "canary probe: login -> touch contacts via gateway, then check jaeger for the service"
  kubectl -n "$NS" port-forward svc/ts-ui-dashboard 8080:8080 >/tmp/pf-ui.log 2>&1 & P1=$!
  JPOD=$(kubectl -n istio-system get pods -l app=jaeger -o name | head -1)
  kubectl -n istio-system port-forward "$JPOD" 16686:16686 >/tmp/pf-jq.log 2>&1 & P2=$!
  sleep 5
  TOK=$(curl -s -X POST http://localhost:8080/api/v1/users/login -H 'Content-Type: application/json' \
    -d '{"username":"fdse_microservice","password":"111111"}' | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
  UID2=4d2a46c7-71cb-4cf1-b5bb-b68406d9da6f
  R=$(curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $TOK" \
    "http://localhost:8080/api/v1/contactservice/contacts/account/$UID2")
  log "contacts key-path via gateway: HTTP $R"
  sleep 6   # export flush
  T1=$(( $(date +%s%6N) )); T0=$(( T1 - 120000000 ))
  # jaeger query API rides the query svc; try collector svc first then jaeger svc
  J=$(curl -s "http://localhost:16686/jaeger/api/traces?service=ts-contacts-service&start=$T0&end=$T1&limit=5" | head -c 200)
  log "jaeger sample: ${J:0:120}"
  kill $P1 $P2 2>/dev/null
  ;;
apply)
  shift
  for D in "$@"; do patch_one "$D"; done
  ;;
restore)
  shift
  for D in "$@"; do restore_one "$D"; done
  ;;
status)
  kubectl -n "$NS" get deploy -o json | python3 -c "
import json,sys
d=json.load(sys.stdin)
for it in d['items']:
    envs=[e.get('name') for c in it['spec']['template']['spec'].get('containers',[]) for e in (c.get('env') or [])]
    if 'JAVA_TOOL_OPTIONS' in envs: print(it['metadata']['name'])"
  ;;
*)
  echo "usage: instrument-leg1.sh canary | apply SVC.. | restore SVC.. | status"; exit 2;;
esac
