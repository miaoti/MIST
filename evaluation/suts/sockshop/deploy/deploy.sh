#!/usr/bin/env bash
# =============================================================================
# Sock Shop — deploy into the EXISTING kind + Istio + Jaeger cluster (the one
# bookinfo's deploy.sh stands up: cluster "mist", Istio demo profile, Jaeger addon,
# mesh-wide 100% tracing). Sock Shop is the 3rd SUT — it validates that MIST
# generalizes (different services/tags/basePath, no hand-fixing).
#
# Prereq: run evaluation/suts/bookinfo/deploy/deploy.sh first (it creates the
# kind+Istio+Jaeger cluster + the mesh-tracing Telemetry + the istio ingress gateway).
# Then this script adds Sock Shop into the mesh + routes it through the ingress.
#
# Usage: deploy.sh            # deploy
#        deploy.sh teardown   # remove the sock-shop namespace + the sockshop VirtualService
# =============================================================================
set -euo pipefail
export PATH="$HOME/.local/bin:$PATH"
NS=sock-shop
MANIFEST="${SOCKSHOP_MANIFEST:-https://raw.githubusercontent.com/microservices-demo/microservices-demo/master/deploy/kubernetes/complete-demo.yaml}"

if [[ "${1:-}" == "teardown" ]]; then
  kubectl delete virtualservice sockshop -n default --ignore-not-found
  kubectl delete namespace "$NS" --ignore-not-found
  exit 0
fi

# 1. Pre-label the namespace so every pod comes up WITH an Istio sidecar (→ traced).
kubectl create namespace "$NS" --dry-run=client -o yaml | kubectl apply -f -
kubectl label namespace "$NS" istio-injection=enabled --overwrite

# 2. Deploy Sock Shop.
kubectl apply -f "$MANIFEST"

# 3. The databases (MySQL/Mongo/Redis/RabbitMQ) speak non-HTTP TCP that the Istio
#    sidecar mangles, so the app services can't connect to them. Exclude the DBs from
#    the mesh and exclude the app services' DB outbound ports from interception.
for db in catalogue-db carts-db orders-db user-db; do
  kubectl patch deployment "$db" -n "$NS" --type merge \
    -p '{"spec":{"template":{"metadata":{"annotations":{"sidecar.istio.io/inject":"false"}}}}}' || true
done
kubectl patch deployment catalogue -n "$NS" --type merge \
  -p '{"spec":{"template":{"metadata":{"annotations":{"traffic.sidecar.istio.io/excludeOutboundPorts":"3306"}}}}}' || true

# 4. catalogue connects to its DB once at startup (no retry) — restart it after the
#    mesh-free DB is up so it connects cleanly.
kubectl rollout status deployment catalogue-db -n "$NS" --timeout=180s || true
kubectl rollout restart deployment catalogue -n "$NS"
kubectl rollout status deployment catalogue -n "$NS" --timeout=180s || true

# 5. Route Sock Shop's paths through the SHARED Istio ingress gateway (reuse
#    bookinfo-gateway). The ingress Envoy honors the W3C `traceparent` MIST injects, so
#    the marker-first trace lookup works (Sock Shop's front-end does not propagate W3C
#    downstream, so captured traces are ingress→front-end; that's fine for MIST).
GW=$(kubectl get gateway -n default -o jsonpath='{.items[0].metadata.name}')
kubectl apply -f - <<EOF
apiVersion: networking.istio.io/v1
kind: VirtualService
metadata: {name: sockshop, namespace: default}
spec:
  hosts: ["*"]
  gateways: ["$GW"]
  http:
  - match:
    - {uri: {prefix: /catalogue}}
    - {uri: {prefix: /tags}}
    - {uri: {prefix: /cart}}
    - {uri: {prefix: /orders}}
    - {uri: {prefix: /customers}}
    - {uri: {prefix: /cards}}
    - {uri: {prefix: /addresses}}
    route:
    - destination: {host: front-end.sock-shop.svc.cluster.local, port: {number: 80}}
EOF

cat <<'DONE'

Sock Shop deployed into the mesh + routed through the ingress.
Next:
  kubectl port-forward -n istio-system svc/istio-ingressgateway 8080:80   # if not already up (shared with bookinfo)
  kubectl port-forward -n istio-system svc/tracing 16686:80               # Jaeger (shared)
  curl http://localhost:8080/catalogue                                    # smoke test
Then: workload/capture-traces.sh, then run MIST on sockshop-demo.properties.
DONE
