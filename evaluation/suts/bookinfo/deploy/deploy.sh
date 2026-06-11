#!/usr/bin/env bash
# Reproducible deploy of Istio Bookinfo on kind, with Jaeger tracing wired, for
# the MIST distributed-trace oracle (hidden-downstream evidence + trace corpus).
#
# Bookinfo is the dedicated *hidden-downstream phenomenon* SUT: its reviews
# service swallows a failed ratings call and still returns HTTP 200 (graceful
# degradation), so a downstream span 5xx-errors behind a clean 2xx — exactly the
# shape MIST's HiddenDownstreamFailureInvariant targets. See ../README.md.
#
# Requirements: Linux, docker access (docker group), ~8 GB free RAM, internet.
# Usage:  ./deploy.sh           # stand everything up + start port-forwards
#         ./deploy.sh teardown  # delete the kind cluster
set -euo pipefail

CLUSTER=mist
BIN="$HOME/.local/bin"; mkdir -p "$BIN"; export PATH="$BIN:$PATH"
ISTIO_VER="${ISTIO_VER:-1.30.0}"
ISTIO_DIR="$HOME/istio-${ISTIO_VER}"

if [[ "${1:-}" == "teardown" ]]; then
  kind delete cluster --name "$CLUSTER"; exit 0
fi

# 1. tools (install to ~/.local/bin if absent)
command -v kubectl >/dev/null || { curl -fsSL -o "$BIN/kubectl" "https://dl.k8s.io/release/$(curl -fsSL https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"; chmod +x "$BIN/kubectl"; }
command -v kind    >/dev/null || { curl -fsSL -o "$BIN/kind" "https://kind.sigs.k8s.io/dl/v0.24.0/kind-linux-amd64"; chmod +x "$BIN/kind"; }
[[ -d "$ISTIO_DIR" ]] || (cd "$HOME" && curl -fsSL https://istio.io/downloadIstio | ISTIO_VERSION="$ISTIO_VER" sh -)
command -v istioctl >/dev/null || cp "$ISTIO_DIR/bin/istioctl" "$BIN/"

# 2. cluster
kind get clusters 2>/dev/null | grep -qx "$CLUSTER" || kind create cluster --name "$CLUSTER" --wait 150s
# idempotent re-runs: a pre-existing cluster may have no kubeconfig entry for the
# current account (verified failure mode: istioctl falls back to localhost:8080)
kind export kubeconfig --name "$CLUSTER"

# 3. Istio (demo profile = tracing on) + Jaeger backend
istioctl install --set profile=demo -y
kubectl apply -f "$ISTIO_DIR/samples/addons/jaeger.yaml"

# 3b. Harden the Jaeger addon against slow restarts. The addon's all-in-one pod
# keeps its badger store on an emptyDir that SURVIVES container restarts within
# the pod, and ships only a ~30s liveness window (/status:13133, k8s-default
# 3 failures x 10s, no startupProbe). On a long-lived cluster the store grows
# until a container restart takes longer than that window, and the pod then
# crash-loops forever (observed live after 9 days of 100%-sampled traffic:
# 1745 badger SSTs, 153 restarts; diagnosis + fix evidence in
# debug/a-rank-fixes/VALIDATION-2026-06-10.md). The startupProbe below gives a
# (re)starting container up to 600s before the liveness probe takes over.
# Strategic merge keyed by container name; re-running is a no-op (idempotent).
kubectl patch deployment jaeger -n istio-system --type=strategic -p '{
  "spec":{"template":{"spec":{"containers":[{"name":"jaeger","startupProbe":{
    "httpGet":{"path":"/status","port":13133},
    "periodSeconds":10,"failureThreshold":60,"timeoutSeconds":5}}]}}}}'
kubectl rollout status deployment/jaeger -n istio-system --timeout=300s

# 4. enable trace export (Istio >=1.21 needs a Telemetry resource), 100% sampling
kubectl apply -f - <<'EOF'
apiVersion: telemetry.istio.io/v1
kind: Telemetry
metadata: {name: mesh-tracing, namespace: istio-system}
spec:
  tracing:
  - providers: [{name: jaeger}]
    randomSamplingPercentage: 100.0
EOF

# 5. deploy Bookinfo (sidecar-injected) + gateway + destination rules
kubectl label namespace default istio-injection=enabled --overwrite
kubectl apply -f "$ISTIO_DIR/samples/bookinfo/platform/kube/bookinfo.yaml"
kubectl apply -f "$ISTIO_DIR/samples/bookinfo/networking/bookinfo-gateway.yaml"
kubectl apply -f "$ISTIO_DIR/samples/bookinfo/networking/destination-rule-all.yaml"
# pin reviews->v3 (v3 calls ratings) for deterministic hidden-downstream traces
kubectl apply -f - <<'EOF'
apiVersion: networking.istio.io/v1
kind: VirtualService
metadata: {name: reviews, namespace: default}
spec:
  hosts: [reviews]
  http: [{route: [{destination: {host: reviews, subset: v3}}]}]
EOF

# 6. wait
kubectl wait --for=condition=ready pod -l app=productpage --timeout=240s
kubectl wait --for=condition=ready pod -l app=ratings     --timeout=180s
kubectl wait --for=condition=ready pod -l app=reviews      --timeout=180s

# 7. port-forwards for the host (productpage app + ingress gateway + Jaeger query)
echo "Deploy complete. Start these port-forwards (each in its own shell):"
echo "  kubectl port-forward -n istio-system svc/istio-ingressgateway 8080:80   # SUT entry  -> http://localhost:8080/productpage"
echo "  kubectl port-forward -n istio-system svc/tracing 16686:80               # Jaeger     -> http://localhost:16686/jaeger/api"
echo "Inject the hidden-downstream fault with: ../workload/inject-ratings-outage.sh on|off"
