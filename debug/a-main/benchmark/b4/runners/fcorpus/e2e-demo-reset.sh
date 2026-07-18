#!/bin/bash
# Reset the demo SUT state: fault OFF (agent kept), settled.
set -u
kubectl -n trainticket set env deploy/ts-admin-route-service \
  JAVA_TOOL_OPTIONS="-javaagent:/otel/opentelemetry-javaagent.jar"
kubectl -n trainticket rollout status deploy/ts-admin-route-service --timeout=180s
sleep 90
echo "RESET-OFF-DONE"
