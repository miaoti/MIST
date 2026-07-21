#!/bin/bash
# depdown capture — runs entirely in WSL bash. Args: CTRL_MARKER F1 F2 F3
set -u
NS=teastore; POD=capcurl
W=http://teastore-webui:8080/tools.descartes.teastore.webui
P=http://teastore-persistence:8080/tools.descartes.teastore.persistence
EXE() { kubectl -n "$NS" exec "$POD" -- "$@"; }

setup_cart() { local M=$1 U=$2 CJ=/tmp/cj-$1
  EXE curl -s -c "$CJ" -b "$CJ" -o /dev/null -X POST "$W/loginAction" -d "username=$U&password=password"
  EXE curl -s -c "$CJ" -b "$CJ" -o /dev/null "$W/category?category=2&page=1"
  EXE curl -s -c "$CJ" -b "$CJ" -o /dev/null -X POST "$W/cartAction" -d 'addToCart=&productid=42'; }
confirm() { local M=$1 CJ=/tmp/cj-$1
  EXE curl -s -c "$CJ" -b "$CJ" -o /dev/null -w '%{http_code}' -X POST "$W/cartAction" \
    -d "firstname=Order&lastname=Journey&address1=$M&address2=City1&cardtype=volvo&cardnumber=314159265359&expirydate=12/2030&confirm=Confirm"; }
db_down() { kubectl -n "$NS" scale deploy teastore-db --replicas=0 >/dev/null 2>&1
  for i in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15; do
    n=$(kubectl -n "$NS" get pods 2>/dev/null | grep -c teastore-db); [ "$n" = "0" ] && return 0; sleep 3; done; return 1; }
db_up() {
  # FORCE-kill persistence WHILE db is still down -> drops any buffered/in-flight write (no graceful flush)
  pp=$(kubectl -n "$NS" get pods -o name 2>/dev/null | grep persistence | head -1)
  [ -n "$pp" ] && kubectl -n "$NS" delete "$pp" --grace-period=0 --force >/dev/null 2>&1
  # now bring db back (clean); new persistence starts against a db with no pending write
  kubectl -n "$NS" scale deploy teastore-db --replicas=1 >/dev/null 2>&1
  kubectl -n "$NS" rollout status deploy/teastore-db --timeout=100s >/dev/null 2>&1
  kubectl -n "$NS" rollout status deploy/teastore-persistence --timeout=120s >/dev/null 2>&1; sleep 8; }

CTRL=$1; M1=$2; M2=$3; M3=$4; U=(user21 user22 user23); MK=("$M1" "$M2" "$M3")
for i in 0 1 2; do setup_cart "${MK[$i]}" "${U[$i]}"; echo "setup ${MK[$i]} (${U[$i]})"; done
if db_down; then echo "DB-DOWN confirmed (0 db pods)"; else echo "DB-DOWN FAILED (pods remain)"; fi
for i in 0 1 2; do echo "confirm ${MK[$i]} -> HTTP $(confirm "${MK[$i]}")"; done
db_up; echo "DB restored + persistence reconnected"
EXE curl -s -o /tmp/rb.json "$P/rest/orders"
echo "total order rows: $(EXE grep -o id /tmp/rb.json | wc -l | tr -d '[:space:]')"
for m in "$CTRL" "$M1" "$M2" "$M3"; do echo "  $m present-count: $(EXE grep -oc "$m" /tmp/rb.json | tr -d '[:space:]')"; done
