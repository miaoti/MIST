#!/bin/bash
# Bring the catalogue service back to one replica and wait for it.
set -u
kubectl scale deploy/catalogue -n sock-shop --replicas=1 >/dev/null
kubectl rollout status deploy/catalogue -n sock-shop --timeout=300s | tail -1
kubectl get pods -n sock-shop -l name=catalogue --no-headers | awk '{print $1, $2, $3}'
