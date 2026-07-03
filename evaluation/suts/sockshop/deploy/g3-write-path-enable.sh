#!/usr/bin/env bash
# =============================================================================
# G3 WRITE-PATH ENABLEMENT for Sock Shop (layered on top of deploy.sh).
#
# The base bundle (README + deploy.sh) validates GENERALIZATION on the
# read-only catalogue path. The MIST main-track G3 study exercises the
# WRITE paths (cart / order / user), which need two fixes the base deploy
# does not apply — both discovered by live-verification 2026-07-02 and
# recorded in debug/a-main/prep/g3-sut2-deploy-verify.md:
#
#   1. Mongo image pin. complete-demo.yaml leaves carts-db/orders-db as
#      `image: mongo` (untagged -> latest -> 8.x), which REMOVED the legacy
#      OP_QUERY opcode the 2017-era carts:0.4.8 / orders drivers still send,
#      so every Mongo-backed write 500s. Pin to mongo:3.4 (the version Sock
#      Shop shipped with) to restore the original working wire protocol.
#      (user-db is the self-contained weaveworksdemos/user-db:0.3.0 image and
#      is left as-is.)
#   2. Auth + write ingress routes. The base VirtualService routes only the
#      read/generalization prefixes; the write study needs /register /login
#      (cookie session — the ?custId= dev override is NOT honored on POST on
#      this build, so a fresh registered user per run is the isolation key)
#      plus /card /address.
#
# Idempotent: safe to re-run. Requires the base deploy.sh to have run first.
# Usage: g3-write-path-enable.sh
# =============================================================================
set -uo pipefail
export PATH="$HOME/.local/bin:$PATH"
NS=sock-shop

echo "== 1. pin the floating Mongo DBs to 3.4 (OP_QUERY-compatible)"
for db in carts-db orders-db; do
  cur=$(kubectl get deploy "$db" -n "$NS" -o jsonpath='{.spec.template.spec.containers[0].image}')
  cn=$(kubectl get deploy "$db" -n "$NS" -o jsonpath='{.spec.template.spec.containers[0].name}')
  if [ "$cur" != "mongo:3.4" ]; then
    echo "   $db: $cur -> mongo:3.4"
    kubectl set image deployment/"$db" "$cn=mongo:3.4" -n "$NS"
  else
    echo "   $db already mongo:3.4"
  fi
done
for db in carts-db orders-db; do
  kubectl rollout status deployment/"$db" -n "$NS" --timeout=180s
done
# The app services connect to Mongo at startup; restart them after the pin.
kubectl rollout restart deployment/carts deployment/orders -n "$NS"
kubectl rollout status deployment/carts  -n "$NS" --timeout=180s
kubectl rollout status deployment/orders -n "$NS" --timeout=180s

echo "== 2. extend the ingress VirtualService with the auth + write routes"
GW=$(kubectl get gateway -n default -o jsonpath='{.items[0].metadata.name}')
kubectl apply -f - <<EOF
apiVersion: networking.istio.io/v1
kind: VirtualService
metadata: {name: sockshop, namespace: default}
spec:
  hosts: ["*"]
  gateways: ["$GW"]
  http:
  - match:
    - {uri: {prefix: /catalogue}}
    - {uri: {prefix: /tags}}
    - {uri: {prefix: /cart}}
    - {uri: {prefix: /orders}}
    - {uri: {prefix: /customers}}
    - {uri: {prefix: /cards}}
    - {uri: {prefix: /addresses}}
    - {uri: {prefix: /register}}
    - {uri: {prefix: /login}}
    - {uri: {prefix: /card}}
    - {uri: {prefix: /address}}
    route:
    - destination: {host: front-end.sock-shop.svc.cluster.local, port: {number: 80}}
EOF

echo "== G3 write-path enablement applied. Smoke: register -> cookie -> POST /cart -> GET /cart."
