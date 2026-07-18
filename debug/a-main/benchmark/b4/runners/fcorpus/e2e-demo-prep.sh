#!/bin/bash
# E2E Allure demo prep: instrument ts-admin-route-service (OTel javaagent, leg-1 recipe)
# AND set the adminroute lost-write fault flag ON — one combined JAVA_TOOL_OPTIONS patch,
# ONE rollout. Then verify jaeger query reachability on the kind 30005 mapping.
set -u
NS=trainticket
D=ts-admin-route-service
log(){ echo "[$(date +%H:%M:%S)] $*"; }

C=$(kubectl -n "$NS" get deploy "$D" -o jsonpath='{.spec.template.spec.containers[0].name}')
[ -n "$C" ] || { log "FATAL: no container"; exit 1; }
log "patching $D (container=$C): javaagent + -Dmist.fault.lostwrite.enabled=true (scale-0 -> patch -> scale-1)"
kubectl -n "$NS" scale deploy "$D" --replicas=0 >/dev/null; sleep 3
kubectl -n "$NS" patch deploy "$D" --type=strategic -p "{
  \"spec\":{\"template\":{\"spec\":{
    \"volumes\":[{\"name\":\"otel-agent\",\"hostPath\":{\"path\":\"/otel\",\"type\":\"Directory\"}}],
    \"containers\":[{\"name\":\"$C\",
      \"volumeMounts\":[{\"name\":\"otel-agent\",\"mountPath\":\"/otel\",\"readOnly\":true}],
      \"env\":[
        {\"name\":\"JAVA_TOOL_OPTIONS\",\"value\":\"-javaagent:/otel/opentelemetry-javaagent.jar -Dmist.fault.lostwrite.enabled=true\"},
        {\"name\":\"OTEL_SERVICE_NAME\",\"value\":\"$D\"},
        {\"name\":\"OTEL_EXPORTER_OTLP_ENDPOINT\",\"value\":\"http://jaeger-collector.istio-system:4318\"},
        {\"name\":\"OTEL_EXPORTER_OTLP_PROTOCOL\",\"value\":\"http/protobuf\"},
        {\"name\":\"OTEL_TRACES_SAMPLER\",\"value\":\"always_on\"},
        {\"name\":\"OTEL_METRICS_EXPORTER\",\"value\":\"none\"},
        {\"name\":\"OTEL_LOGS_EXPORTER\",\"value\":\"none\"}
      ]}]}}}}" >/dev/null || { log "FATAL: patch failed"; exit 1; }
kubectl -n "$NS" scale deploy "$D" --replicas=1 >/dev/null
kubectl -n "$NS" rollout status deploy/"$D" --timeout=420s || { log "WARN: rollout slow — watch OOM"; }
sleep 5
kubectl -n "$NS" logs deploy/"$D" --tail=40 2>/dev/null | grep -m1 "Picked up JAVA_TOOL_OPTIONS" && log "JTO banner OK" || log "WARN: no JTO banner yet"
R=$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:30005/jaeger/ui/api/services" || true)
log "jaeger query via kind 30005: HTTP $R"
kubectl -n "$NS" get pods | grep "$D"
log "prep done (fault ON + instrumented; restore later = e2e-demo-restore)"
