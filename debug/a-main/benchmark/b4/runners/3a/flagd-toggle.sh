#!/bin/bash
# wave-3a pin 1-P0 (plan rev 2.1, B-F1): flagd toggle with VERIFIED propagation + frozen restore.
#   usage: flagd-toggle.sh <flagName> <on|off|status>
#
# 1-P0 PROBE FINDING (2026-07-11, disclosed): the PRIMARY mechanism from the plan draft (ConfigMap
# patch) CANNOT reach the running flagd — the chart mounts the CM read-only and an initContainer
# copies it into an emptyDir (`config-rw`) that flagd actually watches (`--uri
# file:./etc/flagd/demo.flagd.json`), because flagd-ui needs a WRITABLE file. A CM patch therefore
# only changes the NEXT pod boot's state (probe measured: no propagation in 180 s; the probe's CM
# edits were reverted and verified byte-equal to the frozen reference; the runtime never saw them).
# The MECHANISM OF RECORD is the fallback the plan pre-registered: the flagd-ui API
# (Phoenix FeatureController) — GET /feature/api/read-file -> {"flags": ...};
# POST /feature/api/write-to-file {"data": <document>} writes the watched file; flagd hot-reloads.
# The ConfigMap is NEVER touched by this script (boot-state stays byte-frozen).
# Restore verification: read-file's flags object must equal the wave-start frozen reference's
# flags object (the runtime document differs from the CM only by the stripped "$schema" key —
# measured 2026-07-11) AND the OFREP evaluation must read false.
# Item 3 inherits this script verbatim.
set -u
NS=otel-demo
FLAG=${1:?flag name}
ACTION=${2:?on|off|status}
FROZEN=/home/miaot/gate1-logs/tenancy-window/otel/flagd-frozen.json
UI="http://localhost:8085/feature/api"

pf_start() {
  kubectl -n $NS port-forward svc/flagd 18016:8016 >/tmp/pf-flagd.log 2>&1 &
  PF_PID=$!
  sleep 2
}
pf_stop() { kill ${PF_PID:-0} 2>/dev/null; }

ofrep_value() { # prints the flag's evaluated value ("true"/"false") or ERR
  curl -s --max-time 5 -X POST -H "Content-Type: application/json" -d '{"context":{}}' \
    "http://localhost:18016/ofrep/v1/evaluate/flags/$FLAG" | \
    python3 -c "import json,sys;d=json.load(sys.stdin);print(str(d.get('value')).lower())" 2>/dev/null || echo ERR
}

ui_read() { curl -s --max-time 8 "$UI/read-file"; }

ui_write_variant() { # $1 = target defaultVariant; writes the edited document via the UI API
  local TARGET=$1
  ui_read > /tmp/flagd-ui-live.json
  python3 - "$FLAG" "$TARGET" <<'PY'
import json, sys
flag, target = sys.argv[1], sys.argv[2]
d = json.load(open("/tmp/flagd-ui-live.json"))
flags = d["flags"]
assert flag in flags, "flag %s not in runtime config" % flag
assert target in flags[flag]["variants"], "variant %s not defined for %s" % (target, flag)
flags[flag]["defaultVariant"] = target
json.dump({"data": {"flags": flags}}, open("/tmp/flagd-ui-post.json", "w"))
print("edited %s -> defaultVariant=%s" % (flag, target))
PY
  local CODE
  CODE=$(curl -s -o /tmp/flagd-ui-wr.json -w "%{http_code}" --max-time 10 -X POST \
    -H "Content-Type: application/json" -d @/tmp/flagd-ui-post.json "$UI/write-to-file")
  [ "$CODE" = "200" ] || { echo "write-to-file HTTP $CODE: $(head -c 200 /tmp/flagd-ui-wr.json)"; return 1; }
}

case "$ACTION" in
  status)
    pf_start; echo "$FLAG value: $(ofrep_value)"; pf_stop ;;
  on|off)
    TARGETVAL=$([ "$ACTION" = "on" ] && echo true || echo false)
    TARGETVAR=$([ "$ACTION" = "on" ] && echo on || echo off)
    pf_start
    BEFORE=$(ofrep_value)
    echo "$FLAG before: $BEFORE (target $TARGETVAL)"
    T0=$(date +%s)
    ui_write_variant "$TARGETVAR" || { pf_stop; exit 2; }
    LAT=-1
    for i in $(seq 1 60); do
      V=$(ofrep_value)
      if [ "$V" = "$TARGETVAL" ]; then LAT=$(( $(date +%s) - T0 )); break; fi
      sleep 2
    done
    if [ "$LAT" = "-1" ]; then
      echo "UI-API write did NOT propagate in 120s -> LAST-RESORT fallback = CM patch + flagd pod restart (ordering consequences recorded by the caller)"
      pf_stop; exit 2
    fi
    echo "$FLAG flipped to $TARGETVAL; write->effect latency ${LAT}s"
    if [ "$ACTION" = "off" ]; then
      ui_read > /tmp/flagd-ui-restored.json
      if python3 -c "
import json,sys
a=json.load(open('/tmp/flagd-ui-restored.json'))['flags']
b=json.load(open('$FROZEN'))['flags']
sys.exit(0 if a==b else 1)"; then
        echo "restore verified: runtime flags == frozen reference flags (semantic equality)"
      else
        echo "RESTORE MISMATCH vs frozen reference - INVESTIGATE"; pf_stop; exit 3
      fi
    fi
    pf_stop ;;
  *) echo "usage: $0 <flag> <on|off|status>"; exit 1 ;;
esac
