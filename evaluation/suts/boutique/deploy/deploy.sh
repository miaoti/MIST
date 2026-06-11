#!/usr/bin/env bash
# =============================================================================
# Online Boutique — deploy into the EXISTING kind + Istio + Jaeger cluster (the
# one bookinfo's deploy.sh stands up: cluster "mist", Istio demo profile, Jaeger
# addon, mesh-wide 100% tracing). Online Boutique is the 4th SUT — it is the 2nd
# hidden-downstream demonstration, over gRPC (the others' downstream is HTTP).
#
# Prereq: run evaluation/suts/bookinfo/deploy/deploy.sh first (it creates the
# kind+Istio+Jaeger cluster + the mesh-tracing Telemetry). Then this script adds
# Online Boutique with Istio sidecars (so frontend->gRPC calls are traced).
#
# Usage: deploy.sh            # deploy
#        deploy.sh teardown   # remove the boutique namespace
# =============================================================================
set -euo pipefail
export PATH="$HOME/.local/bin:$PATH"
NS=boutique
MANIFEST="${BOUTIQUE_MANIFEST:-https://raw.githubusercontent.com/GoogleCloudPlatform/microservices-demo/main/release/kubernetes-manifests.yaml}"

if [[ "${1:-}" == "teardown" ]]; then
  kubectl delete namespace "$NS" --ignore-not-found
  exit 0
fi

# 1. Pre-label the namespace so every pod comes up WITH an Istio sidecar (-> traced).
#    Unlike Sock Shop's Mongo/MySQL, Online Boutique's redis-cart tolerates the
#    sidecar, so no per-DB mesh exclusion is needed.
kubectl create namespace "$NS" --dry-run=client -o yaml | kubectl apply -f -
kubectl label namespace "$NS" istio-injection=enabled --overwrite

# 2. Deploy Online Boutique (11 gRPC services + the HTTP frontend + redis-cart + loadgenerator).
kubectl apply -n "$NS" -f "$MANIFEST"

# 3. Wait for everything to come up.
kubectl wait --for=condition=available deployment --all -n "$NS" --timeout=300s || true

cat <<'DONE'

Online Boutique deployed into the mesh.
The frontend is the only HTTP service; reach it with a dedicated port-forward
(its own local port, so it doesn't collide with bookinfo/sockshop on :8080):

  kubectl port-forward -n boutique svc/frontend 8081:80          # frontend (base.url)
  kubectl port-forward -n istio-system svc/tracing 16686:80      # Jaeger (shared)
  curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8081/   # smoke test -> 200

Then: workload/capture-traces.sh, then run MIST on boutique-demo.properties.
DONE
