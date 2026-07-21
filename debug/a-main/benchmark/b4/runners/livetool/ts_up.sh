#!/bin/bash
# Track E bring-up (WSL): TeaStore on kind 'mist' (PVC-backed db) + port-forwards + maintenance read.
set -u
export PATH="/usr/local/bin:/usr/bin:$PATH"   # non-login shells lack kubectl's dir
NS=teastore
kubectl -n $NS scale deploy teastore-registry teastore-db --replicas=1 >/dev/null 2>&1
kubectl -n $NS rollout status deploy/teastore-registry --timeout=120s >/dev/null 2>&1
kubectl -n $NS rollout status deploy/teastore-db --timeout=120s >/dev/null 2>&1
kubectl -n $NS scale deploy teastore-persistence --replicas=1 >/dev/null 2>&1
kubectl -n $NS rollout status deploy/teastore-persistence --timeout=120s >/dev/null 2>&1
kubectl -n $NS scale deploy teastore-auth teastore-image teastore-recommender teastore-webui --replicas=1 >/dev/null 2>&1
for d in teastore-auth teastore-image teastore-recommender teastore-webui; do
  kubectl -n $NS rollout status deploy/$d --timeout=150s >/dev/null 2>&1
done
echo "pods:"; kubectl -n $NS get pods --no-headers | awk '{print "  "$1" "$2" "$3}'
# port-forwards, detached from this client (nohup+disown), logs in /tmp
pkill -f "port-forward.*teastore" 2>/dev/null; sleep 1
nohup kubectl -n $NS port-forward svc/teastore-webui 8091:8080 >/tmp/pf-webui.log 2>&1 & disown
nohup kubectl -n $NS port-forward svc/teastore-persistence 8092:8080 >/tmp/pf-persist.log 2>&1 & disown
sleep 4
echo -n "webui via PF: ";   curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8091/tools.descartes.teastore.webui/login
echo -n "persistence via PF: "; curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8092/tools.descartes.teastore.persistence/rest/orders
echo -n "maintenance state: "; curl -s http://localhost:8092/tools.descartes.teastore.persistence/rest/generatedb/maintenance; echo
