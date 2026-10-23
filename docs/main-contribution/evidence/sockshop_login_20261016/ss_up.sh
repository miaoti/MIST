#!/bin/bash
# Scale every Sock Shop deployment to one replica and wait for each rollout.
set -u
kubectl scale deploy --all -n sock-shop --replicas=1 >/dev/null
for d in $(kubectl get deploy -n sock-shop -o name); do
  kubectl rollout status -n sock-shop "$d" --timeout=420s | tail -1
done
kubectl get pods -n sock-shop --no-headers | awk '{print $1, $2, $3}'
free -g | head -2
