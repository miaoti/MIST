#!/usr/bin/env bash
# =============================================================================
# TrainTicket fault-injection SUT for MIST — deployed the PROVEN way: Kubernetes
# via the upstream `make deploy`. This is exactly how the paper's run22 (10/10
# injected faults) SUT was stood up.
#
# WHY NOT docker-compose. This repo also ships a root docker-compose.yml, but it
# is Mongo-backed while the fault services are MySQL
# (jdbc:mysql://${AUTH_MYSQL_HOST:ts-auth-mysql}...), so compose never converges:
# the services die with UnknownHostException ts-auth-mysql, the nginx gateway
# (ts-ui-dashboard) then `host not found in upstream` -> [emerg], and login never
# returns 200. `make deploy` is the maintained path: `make build` compiles the
# ~40 services FROM the injection-branch source (so the fault code is baked in),
# then `hack/deploy/deploy.sh` k8s-deploys an all-in-one MySQL plus the gateway
# as a NodePort on 32677 — which is exactly MIST's
# base.url=http://localhost:32677 (../trainticket-demo.properties). The 10/10
# detection is marker-driven (MIST reads the {"injected":true,"faultName":...}
# response bodies and matches injectedFaults/injected-faults.json), so it needs
# the SUT reachable but NOT tracing/monitoring.
#
# Requirements (HEAVY — see REPRODUCE.md 6.3): minikube + kubectl + docker; a
# host that can run ~40 JVMs (16+ cores, >=16 GB RAM). The FIRST `make build`
# compiles ~40 Spring services and takes a long time. This does NOT converge on
# a small laptop; the offline path (committed run22 + offline oracle checks)
# evidences TrainTicket's claims without standing the SUT up.
#
# Usage:  ./deploy.sh                       # minikube + make deploy + expose :32677
#         ./deploy.sh teardown              # make reset-deploy + stop the forward
#         TT_SRC=/path/to/clone ./deploy.sh # use an existing clone
#         DEPLOY_ARGS="--with-tracing --with-monitoring" ./deploy.sh  # run22's full args
#
# Source repo (public): https://github.com/AsifShaafi/train-ticket-injection (branch: injection)
# =============================================================================
set -euo pipefail

TT_SRC="${TT_SRC:-$HOME/github/train-ticket-injection}"
TT_REPO="https://github.com/AsifShaafi/train-ticket-injection.git"
NS="${NS:-default}"
# Default to the lean quick-start (all-in-one MySQL, no skywalking/prometheus) —
# enough for the marker-based 10/10. run22 additionally used
# "--with-tracing --with-monitoring"; set DEPLOY_ARGS to match if you want those.
DEPLOY_ARGS="${DEPLOY_ARGS:-}"
PF_PIDFILE="/tmp/tt-ui-portforward.pid"

if [[ "${1:-}" == "teardown" ]]; then
  [[ -f "$PF_PIDFILE" ]] && { kill "$(cat "$PF_PIDFILE")" 2>/dev/null || true; rm -f "$PF_PIDFILE"; }
  [[ -d "$TT_SRC" ]] && ( cd "$TT_SRC" && make reset-deploy Namespace="$NS" ) || true
  echo "torn down (kept minikube + built images; 'minikube delete' frees them)."
  exit 0
fi

command -v minikube >/dev/null || { echo "ERROR: minikube not found — https://minikube.sigs.k8s.io/docs/start/"; exit 1; }
command -v kubectl  >/dev/null || { echo "ERROR: kubectl not found"; exit 1; }
command -v make     >/dev/null || { echo "ERROR: 'make' not found — install build tools (e.g. apt-get install -y make)"; exit 1; }
command -v helm     >/dev/null || { echo "ERROR: 'helm' not found — https://helm.sh/docs/intro/install/ (the all-in-one MySQL is a helm chart)"; exit 1; }

# 0. get the source (the fault code lives here). Shallow + single-branch keeps
#    the download small (the full history is hundreds of MB and a slow/flaky link
#    disconnects mid-pack — observed 2026-06-12: "fetch-pack: unexpected
#    disconnect"). Retry a few times and clear any partial clone.
if [[ ! -d "$TT_SRC/.git" ]]; then
  n=0
  until git clone --single-branch --branch injection "$TT_REPO" "$TT_SRC"; do
    n=$((n+1)); [[ $n -ge 3 ]] && { echo "ERROR: clone failed after $n attempts"; exit 1; }
    echo "clone attempt $n failed; clearing partial clone and retrying in 5s..."
    rm -rf "$TT_SRC"; sleep 5
  done
fi

# 1. k8s substrate: minikube (the proven one). Build images INTO minikube's docker.
#    ~40 JVMs need real memory: give minikube >=20g (so the host wants >=24g).
#    Override with MINIKUBE_CPUS / MINIKUBE_MEM.
# the docker driver refuses to run as root unless forced (common in CI/WSL-as-root).
MK_FORCE=""; [[ "$(id -u)" == "0" ]] && MK_FORCE="--force"
minikube status >/dev/null 2>&1 || \
  minikube start $MK_FORCE --driver="${MINIKUBE_DRIVER:-docker}" \
    --cpus="${MINIKUBE_CPUS:-8}" --memory="${MINIKUBE_MEM:-20g}"
eval "$(minikube docker-env)"

# Docker Desktop's WSL credential helper (credsStore=desktop.exe) can abort
# buildkit even on PUBLIC base-image pulls ("docker-credential-desktop.exe:
# Invalid argument", observed on WSL2-as-root); all images here are public, so
# isolate the build with a throwaway creds-free docker config. No-op on Linux.
export DOCKER_CONFIG="$(mktemp -d)"; printf '{}\n' > "$DOCKER_CONFIG/config.json"

# 2. Build the ~40 service images from source INTO minikube, then k8s-deploy
#    all-in-one MySQL + the services. The multi-stage Dockerfiles compile each
#    service with JDK 8 IN-CONTAINER (no host JDK/Maven needed) and tag them
#    codewisdom/ts-*:1.0.2 to match the deploy manifests, so k8s uses the local
#    (fault-injected) images. First run is LONG. We call build-image +
#    deploy-no-build rather than `make deploy`, because the upstream `clean-image`
#    step that `make deploy` runs first errors on a machine with no prior images.
cd "$TT_SRC"
chmod -R 777 deployment || true
make build-image Repo="${TT_IMG_REPO:-codewisdom}" Tag="${TT_IMG_TAG:-1.0.2}"
make deploy-no-build DeployArgs="$DEPLOY_ARGS" Namespace="$NS"

# 3. expose the gateway on localhost:32677 (MIST's base.url) and wait for login.
( while true; do kubectl port-forward -n "$NS" svc/ts-ui-dashboard 32677:8080; sleep 2; done ) &
echo $! > "$PF_PIDFILE"

echo "waiting for the gateway (login 200 on :32677; TrainTicket takes several minutes to converge)..."
UP=""
for i in $(seq 1 90); do
  code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 \
    -X POST http://localhost:32677/api/v1/users/login \
    -H 'Content-Type: application/json' -d '{"username":"admin","password":"222222"}' 2>/dev/null || true)
  if [[ "$code" == "200" ]]; then echo "  gateway UP (login 200) after ~$((i*10))s"; UP=1; break; fi
  sleep 10
done

if [[ -z "$UP" ]]; then
  echo
  echo "WARNING: gateway not answering login 200 yet. On an adequately-sized host"
  echo "TrainTicket converges in a few minutes; on a small host it can take much"
  echo "longer or thrash (see REPRODUCE.md 6.3). Watch with: kubectl get pods -n $NS"
  exit 1
fi

echo
echo "TrainTicket up. Gateway: http://localhost:32677  (admin/222222)."
echo "MIST is already pointed at it (base.url=http://localhost:32677 in ../trainticket-demo.properties)."
echo "Run a MIST detection run from ../.runtime/ (see ../README.md)."
