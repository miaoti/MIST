#!/bin/bash
# E2 outage: scale the catalogue service (not its database) to zero and wait until its pod is gone.
set -u
kubectl scale deploy/catalogue -n sock-shop --replicas=0
for i in $(seq 1 60); do
  n=$(kubectl get pods -n sock-shop -l name=catalogue --no-headers 2>/dev/null | wc -l)
  [ "$n" = "0" ] && break
  sleep 3
done
kubectl get deploy -n sock-shop catalogue catalogue-db
kubectl get pods -n sock-shop --no-headers | awk '{print $1, $2, $3}'
