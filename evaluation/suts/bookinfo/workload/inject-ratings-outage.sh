#!/usr/bin/env bash
# Toggle the hidden-downstream fault on Bookinfo: a REAL ratings availability
# outage (scale to 0). Bookinfo's reviews-v3 then swallows the failure and still
# returns HTTP 200 (its own graceful-degradation code), so productpage returns
# 200 while the reviews->ratings span 5xx-errors in the trace. NOT a code mutant.
set -euo pipefail
export PATH="$HOME/.local/bin:$PATH"
case "${1:-}" in
  on)  kubectl scale deploy ratings-v1 --replicas=0; echo "ratings OUTAGE = ON (hidden-downstream active)";;
  off) kubectl scale deploy ratings-v1 --replicas=1
       kubectl wait --for=condition=ready pod -l app=ratings --timeout=120s
       echo "ratings restored (healthy control)";;
  *)   echo "usage: $0 on|off"; exit 1;;
esac
