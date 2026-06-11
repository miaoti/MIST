#!/usr/bin/env bash
# =============================================================================
# Reproducible local stand-up of the FAULT-INJECTION TrainTicket SUT for MIST.
#
# TrainTicket is the dedicated FAULT-INJECTION SUT: 7 of its ~40 services carry
# code-level injected faults (ts-auth-service, ts-admin-basic-info-service,
# ts-admin-order-service, ts-admin-route-service, ts-admin-travel-service,
# ts-travel-service, ts-travel-plan-service). On a faulty request they answer
# HTTP 200 with a body that self-reports {"data":{"injected":true,
# "faultName":"..."}}, which MIST matches against injectedFaults/injected-faults.json
# (10 faults). The other ~33 services use the upstream public codewisdom images.
#
# Because the injected faults live in source (NOT in the public codewisdom
# images), the 7 fault services are BUILT from source here; the rest are PULLED.
# This makes the 10/10 detection reproducible on any machine with docker — no
# dependency on the authors' lab deployment.
#
# Requirements: Linux, docker (server running; user in the docker group or sudo),
#   ~16 GB RAM, ENOUGH CPU for ~40 JVMs (8 cores is marginal; do not run other
#   heavy workloads concurrently — TrainTicket-on-compose needs CPU to converge),
#   internet (first run pulls ~33 images + builds 7).
#
# Usage:  ./deploy.sh                 # build the 7 fault images + compose up
#         ./deploy.sh teardown        # docker compose down -v
#         TT_SRC=/path/to/train-ticket-injection ./deploy.sh   # use an existing clone
#
# Source repo (public): https://github.com/AsifShaafi/train-ticket-injection (branch: injection)
# =============================================================================
set -euo pipefail

DC="docker"; command -v docker >/dev/null || { echo "docker not found"; exit 1; }
# use sudo if the daemon needs it
$DC info >/dev/null 2>&1 || DC="sudo docker"

TT_SRC="${TT_SRC:-$HOME/github/train-ticket-injection}"
TT_REPO="https://github.com/AsifShaafi/train-ticket-injection.git"
TAG="${TAG:-0.2.0}"
NAMESPACE="${NAMESPACE:-codewisdom}"
IMG_REPO="${IMG_REPO:-codewisdom}"
FAULT_SERVICES=(ts-auth-service ts-admin-basic-info-service ts-admin-order-service \
  ts-admin-route-service ts-admin-travel-service ts-travel-service ts-travel-plan-service)

if [[ "${1:-}" == "teardown" ]]; then
  ( cd "$TT_SRC" && NAMESPACE=$NAMESPACE TAG=$TAG IMG_REPO=$IMG_REPO IMG_TAG=$TAG $DC compose down -v )
  echo "torn down."; exit 0
fi

# 0. get the source (fault code lives here)
[[ -d "$TT_SRC/.git" ]] || git clone --branch injection "$TT_REPO" "$TT_SRC"

# 1. ensure docker compose v2 is available
$DC compose version >/dev/null 2>&1 || {
  echo "installing docker compose v2 plugin..."
  sudo mkdir -p /usr/local/lib/docker/cli-plugins
  sudo curl -fsSL https://github.com/docker/compose/releases/download/v2.29.7/docker-compose-linux-x86_64 \
    -o /usr/local/lib/docker/cli-plugins/docker-compose
  sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
}

# 2. build the 7 fault images from source (tagged codewisdom/<svc>:TAG so compose uses them)
cd "$TT_SRC"
for svc in "${FAULT_SERVICES[@]}"; do
  if $DC image inspect "$IMG_REPO/$svc:$TAG" >/dev/null 2>&1; then
    echo "== $svc:$TAG already built, skip =="; continue
  fi
  echo "== building $svc (fault-injected) =="
  $DC build --build-arg SERVICE_NAME="$svc" -t "$IMG_REPO/$svc:$TAG" -f "$svc/Dockerfile" .
done

# 3. bring up all ~40 services (7 local fault images + ~33 pulled upstream + DBs)
NAMESPACE=$NAMESPACE TAG=$TAG IMG_REPO=$IMG_REPO IMG_TAG=$TAG $DC compose up -d

# 4. wait for the gateway (ts-ui-dashboard nginx). It crash-restarts until every
#    upstream service it proxies is resolvable, then serves on :8080.
echo "waiting for the SUT gateway on http://localhost:8080 (TrainTicket takes several minutes to converge)..."
UP=""
for i in $(seq 1 60); do
  code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 \
    -X POST http://localhost:8080/api/v1/users/login \
    -H 'Content-Type: application/json' -d '{"username":"admin","password":"222222"}' 2>/dev/null || true)
  if [[ "$code" == "200" ]]; then echo "  gateway UP (login 200) after ${i}0s"; UP=1; break; fi
  # nudge nginx to re-resolve upstreams as services finish booting
  [[ $((i % 6)) -eq 0 ]] && $DC restart train-ticket-injection-ts-ui-dashboard-1 >/dev/null 2>&1 || true
  sleep 10
done

if [[ -z "$UP" ]]; then
  echo
  echo "WARNING: the gateway never answered login 200 within 10 min. The containers are"
  echo "up but the SUT has not converged yet — on a small host this can take longer"
  echo "(or never finish under load; see REPRODUCE.md 6.3). Re-check manually with:"
  echo "  curl -s -o /dev/null -w '%{http_code}\\n' -X POST http://localhost:8080/api/v1/users/login \\"
  echo "       -H 'Content-Type: application/json' -d '{\"username\":\"admin\",\"password\":\"222222\"}'"
  exit 1
fi

echo
echo "TrainTicket up. Gateway: http://localhost:8080  (admin/222222, TT's built-in account)"
echo "Point MIST at it:  base.url=http://localhost:8080  (already set in ../trainticket-demo.properties)"
echo "Then from the repo root run a MIST detection run (see ../README.md)."
