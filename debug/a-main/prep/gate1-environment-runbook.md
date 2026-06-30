# PREP (main-track) — Gate 1 environment runbook (deploy TT + test the LOST_WRITE variant)

> Main-track prep. **Runs on the WSL2/k8s box** (not from this session). Goal: deploy TrainTicket with
> tracing, enable the SUT-side `LOST_WRITE_FAULT` on `ts-admin-route-service`, and confirm the
> acknowledged-but-lost write by read-back — the manual proof-of-concept of the differential
> data-integrity oracle's *target* before B2 is built in MIST (B2 is BLOCKED until "yes").
> SUT = `train-ticket-injection`, branch **`MIST-trainticket`** (commit `5c471dd8`; built on `injection`).
> Items flagged "CONFIRM" need a check against the live deploy. Date 2026-06-30.

## 0. Source
WSL2 can build directly from the Windows checkout: `/mnt/c/Users/miaot/Github/train-ticket-injection`.
Ensure it's on `MIST-trainticket`: `git -C /mnt/c/Users/miaot/Github/train-ticket-injection branch --show-current`.
(The branch is local-only by choice — not pushed to the collaborator remote. Build from local.)

## 1. Build images
`make build` (alias for clean-image + build-image; `hack/build-image.sh codewisdom 1.0.2`). Repo=`codewisdom`,
Tag=`1.0.2`. (`make deploy` does build + deploy-no-build in one.)

## 2. Deploy with tracing (MIST needs Jaeger/OTel traces)
`make deploy` → `hack/deploy/deploy.sh default "$(DeployArgs)"`. DeployArgs options (from the Makefile):
`--independent-db`, `--with-monitoring`, `--with-tracing` (skywalking), `--all`.
- **CONFIRM:** MIST's pipeline reads **Jaeger/OTel** traces, not SkyWalking. The fork merged
  `set-open-telemetry` (on master/injection), so OTel instrumentation should be present. Confirm the
  Jaeger collector/endpoint MIST expects is reachable after deploy (this matches the existing tool-demo
  deploy the team already runs — see memory `trainticket-live-deploy`). Use whichever tracing flag/setup
  the team's current TT deploy uses for the OTel→Jaeger path.

## 3. Enable the LOST_WRITE variant on ts-admin-route-service
The fault is opt-in via env (Spring relaxed binding `MIST_FAULT_LOSTWRITE_ENABLED` → `mist.fault.lostwrite.enabled`).
Set it **only** on the `ts-admin-route-service` container, then restart that service:
- k8s: add to the `ts-admin-route-service` Deployment container `env:` →
  `- name: MIST_FAULT_LOSTWRITE_ENABLED` / `value: "true"`, then `kubectl rollout restart deploy/ts-admin-route-service`.
- **CONFIRM:** exact Deployment manifest path under `hack/deploy/` (or `deployment/`) for ts-admin-route-service.
- Default (env unset/false) = normal behavior, so leave it off for the **control** run.

## 4. Test = manually demonstrate the acknowledged-but-lost write
With the variant ENABLED:
1. `POST /api/v1/adminrouteservice/adminroute` with a **valid** route (≥2 valid stations, `startStation`
   and `endStation` both present in `stationList`, names 2–50 chars). Expect **HTTP 200**, body
   `{"status":1,"msg":"create and modify success", ...}` — a clean success, **no `isInjected` marker**.
2. `GET /api/v1/adminrouteservice/adminroute` (getAllRoutes). The just-created route is **ABSENT**
   ⇒ acknowledged-but-lost write confirmed (the POST said success; nothing was persisted).
With the variant DISABLED (control): same POST → route **PRESENT** in the GET. The control vs fault read-back
difference is exactly what the differential oracle (B2) will check automatically.

## 5. (Later, Gate 3) the unmodified-system case
For a *real* (non-injected) lost-write hunt, fault the persist at the infrastructure layer instead of via the
SUT flag: Toxiproxy between `ts-route-service` and its MySQL (TCP cut/timeout during the write), driving valid
inputs. The SUT-flag variant (above) is the *ground-truth positive*; the Toxiproxy/real-outage case is the
*unmodified-system* evidence. (B1/B2 in MIST are BLOCKED until "yes"; this section is for later.)

## 6. What this proves (and doesn't)
Proves: TrainTicket can exhibit a masked acknowledged-but-lost write that **status/schema/body oracles pass**
and only a **read-back** catches — validating the oracle's target end-to-end, manually, before building B2.
Does NOT prove: a *real* such bug exists in an unmodified system (that's Gate 3), nor anything about the MIST
tool (untouched).
