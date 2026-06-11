#!/usr/bin/env bash
# Capture Sock Shop catalogue traces (generation input for MIST). Drives the catalogue
# endpoints through the Istio ingress (:8080) with W3C traceparent markers (valid 32-hex),
# then pulls each trace by id from Jaeger (:16686) into traces/.
# Usage: ./capture-traces.sh
set -euo pipefail
GW="${GW:-http://localhost:8080}"; JAEGER="${JAEGER_BASE_URL:-http://localhost:16686/jaeger/api}"
TR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/traces"; mkdir -p "$TR"

CID=$(curl -s --max-time 15 "$GW/catalogue" | python3 -c 'import sys,json; print(json.load(sys.stdin)[0]["id"])')
echo "sample catalogue id = $CID"

drive(){ curl -s -o /dev/null -w "  HTTP %{http_code}  %s\n" --max-time 15 \
           -H "traceparent: 00-$1-00000000000000a1-01" "$GW$2"; }
echo "driving marked catalogue requests through the ingress..."
drive abcd0000abcd0000abcd0000abcd0001 "/catalogue"
drive abcd0000abcd0000abcd0000abcd0002 "/catalogue/size"
drive abcd0000abcd0000abcd0000abcd0003 "/tags"
drive abcd0000abcd0000abcd0000abcd0004 "/catalogue/$CID"
sleep 5

pull(){ curl -s --max-time 15 "$JAEGER/traces/$1" -o "$TR/$2"
        n=$(python3 -c "import json;d=json.load(open('$TR/$2'));print(len(d['data'][0]['spans']) if d.get('data') else 0)" 2>/dev/null)
        echo "  $2: ${n:-0} spans"; }
echo "pulling traces -> $TR/"
pull abcd0000abcd0000abcd0000abcd0001 sockshop_catalogue.json
pull abcd0000abcd0000abcd0000abcd0002 sockshop_catalogue_size.json
pull abcd0000abcd0000abcd0000abcd0003 sockshop_tags.json
pull abcd0000abcd0000abcd0000abcd0004 sockshop_catalogue_id.json
echo "done. (traces are ingress->front-end; Sock Shop's front-end does not propagate W3C downstream.)"
