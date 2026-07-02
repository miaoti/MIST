# Gate-1 infra incident + recovery log (2026-07-01)

Durable record of a WSL2-memory incident during the first Gate-1 pairing run and the
user-approved recovery. Feeds the disclosures section of `gate1-result.md`.

## What happened (run #1 — poisoned, discarded)
- First pairing run reached **Phase B generation** (progress bar ~8/18) after ~1h44m.
- The WSL2 box (25 GiB) ran out of memory: the full **traced** TrainTicket topology
  (~44 `ts-*` services + OTel stack: jaeger/otelcol/prometheus/grafana + nacos×3 + mysql×3)
  steady-states at **~19–20 GiB**, and cumulative growth over the run drove **swap to 95%**
  (7.6/8.0 GiB). Under that thrash the in-node container runtime (**cri-dockerd**) wedged
  (`crictl` → `DeadlineExceeded`), which took down the **kube-apiserver** (`apiserver: Stopped`,
  `kubectl` → TLS handshake timeout).
- Consequence: every MIST API call failed with `Connection refused`
  (**1046** cycles by the time it was stopped). Phase B was generating against a dead cluster,
  and the pairing stage (control/fault + read-back) cannot execute with the cluster down →
  the run's Gate-1 verdict was structurally NOT_EVALUABLE. **Run #1 is discarded, not counted.**
- Not self-recovering: swap held at 95% for ~10 min of read-only observation.

## User decision
Presented three options (kill+recover+relaunch / defer+honest-verdict / bump-WSL-mem+redeploy).
User chose **"Kill run + recover + relaunch"**, with the stated fallback: *if it OOMs again,
write the honest infra-limited verdict rather than looping.*

## Recovery steps (all verified)
1. **Aborted** the poisoned run (SIGTERM; `java gone`) — freed its ~1.8 GiB.
2. `minikube start` (existing profile, non-destructive) → **exit 0**, apiserver `/readyz` → `ok`.
   The restart **drained the swap thrash**: Mem 6.7 GiB used / swap 168 MiB.
3. Post-restart the SUT was degraded: **43 pods in `Error`** (node-crash-orphaned, restart=0, not
   self-healing) incl. nacos + the whole test path. **nacos + mysql came back Running first.**
4. **Swept** the 43 non-Running/non-Completed pods (`kubectl delete pod`) → Deployments/StatefulSets
   recreated them fresh. Boot storm stayed healthy (17 GiB free during boot, no re-thrash).
5. Topology restabilized to **59/60 Running** in ~3 min; all key test-path pods Ready 1/1.
6. Pre-launch verify: admin login end-to-end (valid JWT), Jaeger services list served, forwards up,
   **JAVA_TOOL_OPTIONS on both target deployments = just the OTel javaagent, NO fault flag**
   (clean baseline — no leftover injection from run #1 or the earlier G0 manual test).
7. **Relaunched** the clean pairing run (same `trainticket-gate1-pairing.properties`).

## Relaunch state (run #2 — the counted run)
- **0 connection-refused**; ablation profile printed; Phase A/B smart-fetch active.
- Memory **stable, not thrashing**: ~20.4 GiB used / ~5.6 GiB available; swap creeping slowly
  (~67 MiB/min cold-page eviction, ~4.3/8.0 GiB) with available RAM steady and `api=ok`. Materially
  healthier than the pre-crash wedge.
- **Headroom action:** scaled `prometheus-server` + `grafana` to 0 (non-essential for B2's Jaeger
  quiescence gate; caps future metrics-scrape growth). Verified afterward: otelcol Running,
  jaeger still serving, login=200 → **trace path intact**.
- **Target services healthy + fast:** adminroute read-back 200 in 0.305 s, adminbasic contacts
  200 in 0.326 s, station-service 200 in 0.234 s; all three pods Running 1/1.
- **Caveat carried into the verdict:** peripheral services (executeservice, admintravel,
  waitorder, cancel) log 5 s HTTP timeouts / 404s under memory pressure. MIST itself WARNs
  "detection counts may drop silently" — this reduces *smart-fetch breadth* during generation but
  does **not** touch the two Gate-1 target triples (probed healthy). The adminroute leg is the
  load-bearing sync triple; the **contacts leg remains body-less/unhooked** this run (same as before —
  manual-G0 evidence + follow-up).

## Environment note for the verdict
Single-box WSL2 minikube. Host is **31.7 GiB**, but `.wslconfig` caps WSL at `memory=26GB` (WSL sees ~25 GiB);
the traced topology + a MIST run peak at ~31 GiB committed, near/over the cap. This is a **memory-budget**
problem on a 32 GiB box, not a need-a-bigger-machine problem. Durable fix (see `gate1-result.md` follow-up):
lean SUT deploy (only the ~18 pairing-path services) + `-Xmx4g` on MIST + optionally raise `.wslconfig` to
28 GB (`wsl --shutdown`). run #2 proceeded on the freshly-recovered clean topology with slow-swap headroom.
