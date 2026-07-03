# G3 TrainTicket deploy — progress + the fork-image constraint

Live bring-up for the head-to-head. Running record (append as it advances).

## Target + resource plan
- Cluster: the existing kind **"mist"** (kube-context `kind-mist`), Istio already installed.
- Namespace: **`trainticket`**, deployed **sidecar-free** (NO istio-injection label) to
  avoid the well-known Istio-sidecar-on-infra startup race (mysql/nacos/rabbitmq deadlock
  when the sidecar isn't ready at app start). The mesh footprint is added LATER and
  minimally: only **ts-inside-payment-service** needs a sidecar, for the inbound
  EnvoyFilter abort (the caller needs none for an inbound filter).
- Resources: Sock Shop scaled to **0 replicas** (reversible; β/secondary work is paused)
  to free ~5 GB; bookinfo left up (small). 19 GB free at launch.
- Method: `hack/deploy/deploy.sh trainticket` (quick_start = all-in-one mysql, no
  tracing/monitoring) — launched detached, log `/home/miaot/gate1-logs/tt-deploy.log`.
  Prereqs verified: helm v3.21.1, charts (mysql/nacos/rabbitmq) present, images
  `codewisdom/ts-*:1.0.2` on Docker Hub, no pre-existing helm releases. Scripts CRLF→LF
  in the Windows working tree (git stores LF; autocrlf shows clean).

## CRITICAL constraint — the constructed stratum needs a FORK-built image
The deploy uses **upstream** `codewisdom/ts-*:1.0.2` images. That is fine for:
- **the natural stratum** (unmodified SUT + EnvoyFilter abort on /drawback), and
- **the agreement anchor** (a body-carrying create + fabricated-ack — but see below).

It is NOT enough for the **constructed stratum**: the fabricated-ack drawback flag
(fork commit f57102e6, `mist.fault.drawback.fabricatedack.enabled` in
InsidePaymentServiceImpl) lives in the fork SOURCE, not in the upstream image. So the
constructed run needs a **fork-built `ts-inside-payment-service` image** kind-loaded
into the cluster and that one Deployment repointed to it (the other ~39 services stay on
upstream). Build path: `hack/build-image.sh` builds `codewisdom/<svc>:1.0.2` from source
(mvn + docker) — run it for ts-inside-payment-service ONLY, then `kind load docker-image`
into "mist", then `kubectl set image`/rollout. (This is the same channel Gate-1 used for
its adminroute/adminbasic fork faults, but scoped to one service here.)

## UPDATE — image-tag blocker + the fidelity split (resolved)
The fork manifests pin `codewisdom/ts-*:1.0.2`, which **is not published** (that tag is
meant to be built locally). Docker Hub has `1.0.0` + `latest`. The fork (1.0.2) is newer
than public 1.0.0 and carries our source, so:
- **Fidelity split:** the two services the head-to-head actually measures —
  **ts-cancel-service** (the natural bug: cancelOrder returns {1,"Success."} despite a
  failed drawback) and **ts-inside-payment-service** (drawBack / queryAccount / the
  fabricated-ack flag) — are **built from fork source** so deployed behavior = the source
  I analyzed + the frozen contract's basis. The ~38 supporting services (order, user,
  auth, preserve, travel, …) only need to run the create→pay→cancel graph, so they use
  public **1.0.0** — their version does not affect the measured oracle (MIST reads
  /account on the fork inside-payment; the comparator's cancel contract is the fork
  cancel-service).
- **Live-verify** the cancel→refund behaviors against the deployed fork images before
  any run (the fidelity gate).

Two background ops now running (both logged in /home/miaot/gate1-logs):
1. `tt-deploy.log` → graph on 1.0.0 (repointed live via kubectl set image; ~17/47
   Deployments ready and climbing as pulls complete).
2. `fork-build.log` → multi-stage `docker build` of ts-cancel + ts-inside-payment
   (maven-in-docker, WSL-native context ~/ttbuild) → `kind load … --name mist`. On
   completion: repoint those 2 Deployments to `:1.0.2` with imagePullPolicy IfNotPresent.

## RESOURCE BLOCKER + strategic fix (minimal subgraph)
Running the FULL 40-service graph pull concurrently with the maven fork-build
over-committed the ~22 GB WSL budget: vmmemWSL ~17.3 GB, host free ~0.6-0.8 GB → WSL2's
relay starved → `wsl.exe` calls time out (Wsl/Service/0x8007274c). The VM is alive
(distros still listed running), just thrashing. Per the standing rule I do NOT
`wsl --shutdown` (nor `--terminate`, which would kill the kind cluster + all deploy
progress). Recovery = let the transient maven load finish / OOM-reclaim so the relay
responds; a per-probe-`timeout` background loop detects it.

**Strategic fix (not just recovery):** this machine is marginal for a full 40-service
TrainTicket + Istio + builds. The head-to-head only exercises
register→create→pay→cancel→refund + the /account read-back — a ~20-service SUBGRAPH.
So run TrainTicket as a **minimal cancel→refund subgraph**: scale the ~17 services not
on that path to 0 permanently (admin-*, news, voucher, notification, avatar, wait-order,
rebook, execute, delivery, food-delivery, ui-dashboard, ticketoffice). If still tight,
also drop food*/consign*/assurance/plan by using a BASIC order stimulus (no food, no
consign, no insurance) so those services are never called. This halves the footprint and
stops the thrash — the correct long-term configuration, staged in
$CLAUDE_JOB_DIR/tmp/scale-down-nonessential.sh. Sequence the 2 fork builds AFTER the
core subgraph is up (not concurrent) to avoid the peak.

## RECOVERY (2026-07-03, the docker-exec bypass) — graph UP, fork images IN, nacos re-formed
The Ubuntu WSL relay stayed starved long after pressure eased, but the **Docker Desktop
engine answered from Windows** — and the kind node container survived (Up 15h). Everything
proceeded via `docker exec mist-control-plane kubectl --kubeconfig /etc/kubernetes/admin.conf …`
(full cluster control with no Ubuntu relay, no restart, nothing killed):
- **All 56 trainticket pods reached Running** during the lockout (the 1.0.0 repoint
  completed). Then the 17-service scale-down applied (16 scaled, 1 name-variant absent)
  → the ~33-pod core subgraph (all 1/1, gateway + full cancel→refund path present).
- **Fork images: BUILT + IMPORTED.** The earlier WSL-side build never started (no maven
  base image — it died at the thrash). Rebuilt **from Windows** against a trimmed context
  (pom.xml + ts-common + the 2 services, 0.15 MB): BuildKit reused the 2-week-old layer
  cache (the P0-B screencast-era 0.2.0 builds of the same repo), so both images finished
  in ~2 min; fabricated-ack source verified present in the context before building. Then
  `docker save | docker cp | ctr -n k8s.io images import` into the kind node (no kind CLI
  needed): `codewisdom/ts-cancel-service:1.0.2` + `codewisdom/ts-inside-payment-service:1.0.2`
  now in the node's containerd (crictl-verified).
- **Stimulus source-verified** (exact facts for the harness):
  - register `POST /api/v1/userservice/users/register`; login `POST /api/v1/users/login`.
  - `OrderServiceImpl.create` saves the order AS POSTED (only the id is regenerated) →
    a PAID order is directly creatable: `POST /api/v1/orderservice/order` with status=1,
    price>0, travelDate/travelTime far-future ("yyyy-MM-dd" / "yyyy-MM-dd HH:mm:ss").
  - `calculateRefund`: status must be PAID and now<startTime else refund 0/0.00.
  - `queryAccount` builds rows ONLY from Money records → a fresh buyer has NO /account
    row until the drawback lands: control = null→refund (appearing = X present, the
    value-delta fresh-buyer-appearing case), fault = null→null (X absent) → FIRE shape
    confirmed. Balance fields = userId + balance (match the shipped triples exactly).
- **First live probe found the real casualty:** register → gateway 500; user-service saw
  nothing; the gateway's nacos client UNHEALTHY; a fresh gateway pod ALSO failed
  (STARTING) — **nacos-0's server process had failed to boot during the thrash** ("Nacos
  failed to start") while its container lingered Running. Fix: `rollout restart
  statefulset nacos` (mysql is healthy now), then re-test register; services' wedged
  nacos clients should reconnect once the cluster is back (restart individual pods only
  if their clients stay wedged).

## Order from here (concrete; scripts staged in $CLAUDE_JOB_DIR/tmp)
On WSL recovery (blocked on the maven build finishing + memory freeing):
1. **Relieve + verify** — `scale-down-nonessential.sh` (17 off-path svcs → 0), then
   confirm the ~20-service core subgraph is 1/1 (nacos/mysql/rabbitmq + user, auth,
   verification-code, order(+other), preserve(+other), basic, travel(+2), route, train,
   price, seat, config, station, contacts, security, payment, cancel, inside-payment,
   gateway). Chase stragglers (missing 1.0.0 tag → :latest for that svc).
2. **Fork images** — check `fork-build.log` / `docker images`. If done →
   `repoint-fork-images.sh` (ts-cancel + ts-inside-payment → fork :1.0.2, IfNotPresent).
   If the build died under memory pressure, rerun `build-fork-images.sh` NOW (core is up,
   no longer concurrent with 40 pulls).
3. **Mesh + fault** — `enable-inside-payment-mesh.sh` (sidecar opt-in on inside-payment +
   apply the EnvoyFilter) then live-verify /drawback=418 while /account reachable.
4. **Fidelity gate** — register→login→PAID order→cancel: confirm on the FORK images that
   cancel of a paid order = {1,"Success."}, drawback moves /account balance, /money=null.
   Record exact curls here (the harness stimulus).
5. **Harness** — author per g3-headtohead-run-architecture.md (verified-signature
   blueprint appended there 5d886dd); compile; run natural + constructed + agreement;
   ≥3-cold-review before the numbers feed claims.

*Feeds: the G3 head-to-head runs. The depth oracle code + injector are done+reviewed
(REVIEW-DEPTH-RECONCILIATION.md); this doc tracks the live SUT bring-up.*
