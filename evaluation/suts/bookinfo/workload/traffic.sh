#!/usr/bin/env bash
# Drive nominal traffic through the Istio ingress gateway (port-forward :8080).
# Usage: ./traffic.sh [N]   (default 50 requests to GET /productpage)
set -euo pipefail
N="${1:-50}"; BASE="${GW:-http://localhost:8080}"
codes=""
for i in $(seq 1 "$N"); do
  c=$(curl -s -o /dev/null -w "%{http_code}" --retry 6 --retry-connrefused --retry-delay 1 --max-time 12 "$BASE/productpage?u=test" 2>/dev/null)
  codes="$codes $c"
done
echo "HTTP codes:$codes"
