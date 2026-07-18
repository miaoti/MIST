#!/bin/bash
# Mid-session fault flip for the E2E Allure demo: turn the adminroute lost-write fault ON
# (agent kept). Script-file form — quoting survives no shell hops.
set -u
kubectl -n trainticket set env deploy/ts-admin-route-service \
  JAVA_TOOL_OPTIONS="-javaagent:/otel/opentelemetry-javaagent.jar -Dmist.fault.lostwrite.enabled=true"
kubectl -n trainticket rollout status deploy/ts-admin-route-service --timeout=180s
sleep 90
kubectl -n trainticket get deploy ts-admin-route-service \
  -o jsonpath='{.spec.template.spec.containers[0].env[?(@.name=="JAVA_TOOL_OPTIONS")].value}'
echo
echo "FLIP-ON-DONE"
