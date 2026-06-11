#!/usr/bin/env bash
# Drive nominal traffic through the local TrainTicket gateway (after deploy/deploy.sh).
# Logs in with TrainTicket's built-in admin account and hits a few endpoints.
set -euo pipefail
GW="${GW:-http://localhost:8080}"
TOK=$(curl -s -X POST "$GW/api/v1/users/login" -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"222222"}' | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
[ -n "$TOK" ] && echo "login OK (token len ${#TOK})" || { echo "login FAILED"; exit 1; }
for i in $(seq 1 "${1:-10}"); do
  curl -s -o /dev/null -w "GET /adminbasic/stations -> %{http_code}\n" \
    -H "Authorization: Bearer $TOK" "$GW/api/v1/adminbasicservice/adminbasic/stations"
done
