# PREP (main-track) — Gate-1 MIST-side session runbook (pairing + FP probe)

> Companion to `gate1-environment-runbook.md` (SUT side). This is the MIST-side script for the live
> Gate-1 session: run the B1/B2 pairing against the deployed TrainTicket and record the verdicts.
> Everything runs **inside WSL2** (kubectl context `minikube` lives there; MIST repo reachable at
> `/mnt/c/Users/miaot/Github/MIST`). Date 2026-07-01.

## 0. Preconditions
1. WSL2 up; `minikube status` → currently **Stopped**; start it: `minikube start` and wait for the
   ~46 `ts-*` pods: `kubectl get pods -n default | grep -cv Running` → eventually only header.
   (Images were built from `train-ticket-injection@MIST-trainticket` = contain both LOST_WRITE injectors.)
2. **Stale-flag hygiene is automatic:** the smoke left `JAVA_TOOL_OPTIONS` set on
   `ts-admin-route-service`; the pairing executor's first act is clear-all (kubectl set env
   `JAVA_TOOL_OPTIONS-` + rollout) on BOTH triple deployments, which flushes it. No manual unset needed —
   but verify after the run: `kubectl set env deployment/ts-admin-route-service --list | grep JAVA_TOOL` → empty.
   The stale `MIST_FAULT_LOSTWRITE_ENABLED` env var (relaxed binding, provably inert on this SUT) can be
   removed manually once: `kubectl set env deployment/ts-admin-route-service MIST_FAULT_LOSTWRITE_ENABLED- -n default`.
3. Gateway port-forward (as the smoke): `kubectl port-forward svc/ts-ui-dashboard 32677:8080 -n default &`
   → base URI `http://localhost:32677`. Jaeger reachable at the config's `jaeger.base.url`
   (`http://localhost:30005/jaeger/ui/api` — run22 topology; adjust port-forward if needed:
   `kubectl port-forward svc/<jaeger-query-svc> 30005:... ` per the deployed tracing setup).
4. Java 21 + Maven in WSL2 (verified 2026-07-01). LLM: config uses deepseek via `DEEPSEEK_API_KEY` or the
   seeded LLM cache (`mist.llm.cache.read=auto`); export the key if cache misses are expected.
5. **Traceparent precheck (load-bearing for the observation-gated absence stratum):** the absence upgrade
   queries Jaeger by the exact client-fabricated traceparent id. Verify once on THIS deploy (run22 evidence
   says it works, re-verify):
   ```bash
   TID=$(openssl rand -hex 16); curl -s -H "traceparent: 00-$TID-$(openssl rand -hex 8)-01" \
     http://localhost:32677/api/v1/adminrouteservice/adminroute -H "Authorization: Bearer <token>" >/dev/null
   sleep 5; curl -s "http://localhost:30005/jaeger/ui/api/traces/$TID" | head -c 200   # expect a trace, not 404
   ```
   If the deploy does NOT honor traceparent, every absent verdict lands timeout-gated — record that and read
   the §3 strata accordingly (the FIRE itself still stands; only its confidence stratum changes).

## 1. Build + config
```bash
cd /mnt/c/Users/miaot/Github/MIST
mvn clean install -DskipTests          # produces mist-cli/target/mist.jar
```
Run config: `mist-cli/src/main/resources/My-Example/trainticket-gate1-pairing.properties`
(demo config + `faulty.ratio=0.0`, two-phase ON, enhancer OFF, `mst.oracle.dataintegrity.enabled=true`,
`mist.fault.injection.enabled=true`, pre-registered quiescence 500 ms / 10 s). Registry
(`target-triples.yaml`, beside the conf) declares both triples + `cluster: {context: minikube}`.
`base.uri`/conf paths resolve exactly as the demo does (same directory).

## 2. The pairing run
```bash
java -jar mist-cli/target/mist.jar mist-cli/src/main/resources/My-Example/trainticket-gate1-pairing.properties
```
Expected flow: Phase A (positives, pool harvest) → Phase B generation (positives only) → writer logs
`data-integrity oracle ON: 2 target triple(s)` → pairing: clear-all → control run (filtered to the
pairing methods) → inject-all (2 rollouts) → fault run → clear-all → verdict summary + JSON report at
`logs/data-integrity-reports/pairing_trainticket_gate1_pairing_<id>.json`.

**Known gap to resolve live (task #11d):** the bundled trace corpus has NO scenario for
`POST /adminbasicservice/adminbasic/contacts` (adminroute has one). If the generated suite contains no
contacts method, the pairing covers adminroute only. Fix during the session: log in to the admin UI /
curl one `POST /api/v1/adminbasicservice/adminbasic/contacts` through the gateway (this is also the
pending adminbasic G0 read-back smoke), export the fresh Jaeger trace into the run's trace file, and
re-run so the scenario materializes. Record whichever path was taken in gate1-result.md.

## 3. PASS/FAIL reading (plan §4)
- **Sensitivity:** the report's `pureDifferential` = `FIRE` for the injected (fault) pair on each live
  triple, with the fault record's quiescence gate noted (`OBSERVED_COMPLETE_ABSENT` when Jaeger confirms
  the write's trace completed; `TIMEOUT_ABSENT` otherwise — report both, strata never pooled).
- **Specificity (B2.4, sync):** benign runs must not fire; ≤5% non-timeout-gated sync FP
  (pre-registered). Async: DESCRIPTIVE-ONLY at Gate-1 + explicit disclaimer (P3 verdict: no clean
  broker-async path; Option A injector deferred to G3).
- Any `NOT_EVALUABLE` pair = environment/protocol failure → diagnose before drawing conclusions.
- **Evidence hygiene:** the pairing legs also feed the legacy channels (fault-detection report counts each
  test twice — control+fault; parameter observations drained to the on-disk registry include fault-run
  harvests, which are validation-passing values either way). Gate-1 pairing evidence is read ONLY from
  `logs/data-integrity-reports/pairing_*.json`; the legacy fault-detection report of a pairing run is noise
  for this purpose and is not cited.
- Record everything in `debug/a-main/prep/gate1-result.md`; FAIL ⇒ README §9 fallback.

## 4. Post-session cleanup
```bash
kubectl set env deployment/ts-admin-route-service --list -n default | grep -i java_tool   # expect empty
kubectl set env deployment/ts-admin-basic-info-service --list -n default | grep -i java_tool
minikube stop    # leave the cluster stopped, not deleted
```
