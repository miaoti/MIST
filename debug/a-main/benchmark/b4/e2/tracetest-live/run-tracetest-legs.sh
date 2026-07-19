#!/bin/bash
# Tracetest LIVE head-to-head, adminroute site (2026-07-18). REST-API-driven; both legs
# (control = fault OFF, fault = lostwrite ON) run the SAME three real Tracetest tests:
#   T1 presence-of-downstream-persist (client span to ts-route-service) — the presence arm
#   T2 entry-acked-2xx (the masked ack; passes on BOTH legs = no discrimination)
#   T3 no-5xx-span (naive error-span; passes on BOTH legs)
# Leg sequencing (fault flip) is done by the caller via the e2e-demo flip/reset scripts.
# Usage: run-tracetest-legs.sh <legname>
set -u
LEG="${1:?legname}"
API=http://localhost:11633/api
GW_IN=http://host.docker.internal:8080
OUT=/mnt/c/Users/miaot/Github/MIST/debug/a-main/benchmark/b4/e2/tracetest-live
TOK=$(curl -s -X POST -H "Content-Type: application/json" -d '{"username":"admin","password":"222222"}' http://localhost:8080/api/v1/users/login --max-time 20 | python3 -c "import json,sys;print(json.load(sys.stdin)['data']['token'])")
[ -n "$TOK" ] || { echo "FATAL: no admin token"; exit 1; }

BODY='{\"id\":\"\",\"startStation\":\"shanghai\",\"endStation\":\"taiyuan\",\"stationList\":\"shanghai,taiyuan\",\"distanceList\":\"0,1350\"}'

mktest() { # $1=id $2=name $3=selector $4=assertions-json-array
  cat <<EOF
{"type":"Test","spec":{"id":"$1","name":"$2",
 "trigger":{"type":"http","httpRequest":{"method":"POST",
   "url":"$GW_IN/api/v1/adminrouteservice/adminroute",
   "headers":[{"key":"Content-Type","value":"application/json"},
              {"key":"Authorization","value":"Bearer $TOK"}],
   "body":"$BODY"}},
 "specs":[{"selector":"$3","name":"$2","assertions":$4}]}}
EOF
}

put_test() { # $1=id $2=json
  curl -s -X PUT "$API/tests/$1" -H "Content-Type: application/json" -d "$2" >/dev/null \
    || curl -s -X POST "$API/tests" -H "Content-Type: application/json" -d "$2" >/dev/null
}

put_test adminroute-presence "$(mktest adminroute-presence 'adminroute: downstream persist client span present' 'span[kind=\"client\"]' '["attr:tracetest.selected_spans.count >= 1"]')"
put_test adminroute-acked2xx "$(mktest adminroute-acked2xx 'adminroute: entry acked 2xx (masked)' 'span[kind=\"server\"]' '["attr:http.status_code = 200"]')"
put_test adminroute-noerror "$(mktest adminroute-noerror 'adminroute: no 5xx span (naive)' 'span[tracetest.span.type=\"http\"]' '["attr:http.status_code < 500"]')"

for T in adminroute-presence adminroute-acked2xx adminroute-noerror; do
  RUN=$(curl -s -X POST "$API/tests/$T/run" -H "Content-Type: application/json" -d '{}')
  RID=$(echo "$RUN" | python3 -c "import json,sys;d=json.load(sys.stdin);print(d.get('id') or d.get('run',{}).get('id',''))" 2>/dev/null)
  echo "leg=$LEG test=$T run=$RID launched"
  for i in $(seq 1 30); do
    ST=$(curl -s "$API/tests/$T/run/$RID" | python3 -c "import json,sys;d=json.load(sys.stdin);print(d.get('state',''))" 2>/dev/null)
    case "$ST" in FINISHED|FAILED|TRIGGER_FAILED|TRACE_FAILED|ASSERTION_FAILED|ANALYZING_ERROR) break;; esac
    sleep 4
  done
  curl -s "$API/tests/$T/run/$RID" > "$OUT/run-$LEG-$T.json"
  python3 - "$OUT/run-$LEG-$T.json" "$LEG" "$T" <<'PYEOF'
import json,sys
d=json.load(open(sys.argv[1]))
res=d.get('result',{}) or {}
allp=res.get('allPassed')
state=d.get('state')
rows=[]
for sr in (res.get('results') or []):
    for a in (sr.get('results') or []):
        ok=all(s.get('result',{}).get('passed',False) for s in (a.get('allPassed') and [] or a.get('spanResults') or [])) if a.get('spanResults') else a.get('allPassed')
        rows.append((a.get('assertion'), a.get('allPassed'), len(a.get('spanResults') or [])))
print(f"leg={sys.argv[2]} test={sys.argv[3]} state={state} allPassed={allp} " + " | ".join(f"{r[0]} passed={r[1]} spans={r[2]}" for r in rows))
PYEOF
done
