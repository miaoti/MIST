#!/bin/bash
# Track E leg prep (WSL). $1 = control|fault.
# Sets/verifies the maintenance state (POST /rest/generatedb/maintenance JSON; NEVER GET
# /rest/generatedb - DB WIPE), then login user21 + seed the cart, and emits:
#   COOKIE=<cookie header value>   BASELINE=<orders row count>   MAINT=<state>
set -u
export PATH="/usr/local/bin:/usr/bin:$PATH"
LEG=$1
W=http://localhost:8091/tools.descartes.teastore.webui
P=http://localhost:8092/tools.descartes.teastore.persistence
MURL=$P/rest/generatedb/maintenance

want=false; [ "$LEG" = "fault" ] && want=true
cur=$(curl -s "$MURL")
if [ "$cur" != "$want" ]; then
  curl -s -X POST -H 'Content-Type: application/json' -d "$want" "$MURL" >/dev/null
  sleep 2
  cur=$(curl -s "$MURL")
fi
if [ "$cur" != "$want" ]; then echo "ABORT: maintenance state '$cur' != wanted '$want' (unverified toggle)"; exit 2; fi

J=$(mktemp)
curl -s -c "$J" -b "$J" -o /dev/null "$W/login"
curl -s -c "$J" -b "$J" -o /dev/null -X POST "$W/loginAction" -d 'username=user21&password=password'
curl -s -c "$J" -b "$J" -o /dev/null -X POST "$W/cartAction" -d 'addToCart=&productid=42'
COOKIE=$(awk '!/^#/ && NF>=7 {printf "%s%s=%s", sep, $6, $7; sep="; "}' "$J")
if [ -z "$COOKIE" ]; then echo "ABORT: no session cookie captured"; exit 3; fi
BASELINE=$(curl -s "$P/rest/orders" | grep -o '"id"' | wc -l | tr -d '[:space:]')
echo "MAINT=$cur"
echo "COOKIE=$COOKIE"
echo "BASELINE=$BASELINE"
