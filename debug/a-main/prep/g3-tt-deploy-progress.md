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

## Order from here
1. ⏳ deploy infra + 40 services (running). Verify: all ts-* pods Running; the gateway
   reachable; register→login→create-order→pay works end-to-end (the harness stimulus).
2. Add a sidecar to ts-inside-payment-service (namespace/pod annotation + restart) +
   author & apply the EnvoyFilter abort (inbound, 418, /drawback prefix); live-verify it
   severs /drawback while /account stays 200.
3. Build + kind-load the fork ts-inside-payment image for the constructed stratum.
4. Author the two target-triples configs + the head-to-head harness (per
   g3-headtohead-run-architecture.md), then run natural + constructed + agreement.

*Feeds: the G3 head-to-head runs. The depth oracle code + injector are done+reviewed
(REVIEW-DEPTH-RECONCILIATION.md); this doc tracks the live SUT bring-up.*
