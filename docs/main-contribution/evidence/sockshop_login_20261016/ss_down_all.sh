#!/bin/bash
# Restore the state before round 16: every Sock Shop deployment back to zero replicas.
set -u
kubectl scale deploy --all -n sock-shop --replicas=0 >/dev/null
kubectl get deploy -n sock-shop --no-headers | awk '{print $1, $2}'
