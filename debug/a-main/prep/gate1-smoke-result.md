# PREP (main-track) — Gate-1 smoke result: acknowledged-but-lost write CONFIRMED on live TrainTicket

> Main-track prep. **NO MIST tool code.** Manual end-to-end proof, on a real TrainTicket deploy, that the
> SUT-side `LOST_WRITE_FAULT` produces an *acknowledged-but-lost write* that status/schema/body oracles pass
> and only a read-back differential catches — i.e. the differential data-integrity oracle's target is real.
> Reality pre-check before building B2 (B2 itself is BLOCKED until "yes"). Date 2026-06-30.

## Environment (the team's proven path)
- Deployed via the `evaluation/suts/trainticket/deploy/deploy.sh` recipe = **minikube + `make build` + `make deploy`**
  (the run22 path). TT runs on **minikube**; the team's **kind** cluster (Istio Bookinfo) is separate and untouched.
- All ~46 `codewisdom/ts-*:1.0.2` images built **from `MIST-trainticket` source** into minikube; pod imageID ==
  locally-built imageID (verified). Adminroute jar extracted from the running pod contains the LOST_WRITE code
  (`LOST_WRITE_FAULT`, `create and modify success`, `mist.fault.lostwrite.enabled`, `lostWriteFaultEnabled` all present).
- Gateway `ts-ui-dashboard` port-forwarded to `localhost:32677`; admin login `admin/222222`.

## Result — control vs fault on `POST /api/v1/adminrouteservice/adminroute`
| | Control (fault OFF) | Fault (fault ON) |
|---|---|---|
| input | valid route shanghai→nanjing | valid route suzhou→wuxi |
| POST response | `status:1 "Save and Modify success"`, `data={id:<uuid>}` | `status:1 "create and modify success"`, **`data:null`** |
| getAllRoutes count | 10 → **11** (persisted) | 12 → **12** (unchanged) |
| read-back | route **present** | route **ABSENT** |
| pod log | normal persist to ts-route-service | `WARN [INJECTED FAULT][LOST_WRITE_FAULT] acknowledging success WITHOUT persisting route id: mist-fault-002` |

Both runs return HTTP 200 / `status:1` → **status-code, schema, and body-marker oracles all PASS on the fault run**.
Only the control-vs-fault **read-back differential** distinguishes them. Exactly the signal B2 is designed to fire on.

**Evidence-hygiene note (cold-review C).** The counts step 11 (end of control) → 12 (start of fault) because a
manual create was run between the two scripted steps — the smoke was interactive, not a single automated pair.
This does **not** affect the per-run verdict (the fault run's OWN baseline→after, 12→12 with its route absent,
is the read-back signal), but the automated B2 pairing executor (TOOL-PLAN B1.3) MUST run control and fault
back-to-back with no interleaved mutation, precisely to avoid this.

## Key engineering finding (load-bearing for B1)
The opt-in flag **must not rely on env→property relaxed binding** on this SUT. With container env
`MIST_FAULT_LOSTWRITE_ENABLED=true` present in the pod, Spring did **not** bind it to
`@Value("${mist.fault.lostwrite.enabled:false}")` (first fault run took the normal persist path). TrainTicket uses
Spring Cloud + nacos bootstrap, whose PropertySource ordering differs from plain Spring Boot. **Deterministic fix:**
`JAVA_TOOL_OPTIONS=-Dmist.fault.lostwrite.enabled=true` (a JVM system property matches the `@Value` placeholder
directly). **B1 implication:** the fault flag must use a JVM `-D` system property and/or explicit `application.yml`
key, never env-relaxed-binding alone. Same caveat applies to adminbasic LOST_WRITE.

## Proves / does not prove
- **Proves:** on a real, fully-deployed TrainTicket, a masked acknowledged-but-lost write exists that all
  response-level oracles pass and only a black-box read-back catches. Gate-1 reality pre-check: PASS.
- **Does NOT prove:** (a) a *real* non-injected such bug (= Gate 3); (b) the B2 oracle itself (not built, BLOCKED
  until "yes"); (c) read-back FP rate under async/eventual consistency (the soundness measurement B2 must do).

## Live-cluster state / pending cleanup (read before the next run)
The minikube cluster is **Stopped** (not deleted), so the fault is **not active right now**. But the smoke set the
flag via `kubectl set env`, which persists in the deployment spec — so on the next `minikube start`,
`ts-admin-route-service` comes back up with `LOST_WRITE` **still ON**. Before any non-fault run, unset it:
```
kubectl config use-context minikube
kubectl set env deployment/ts-admin-route-service JAVA_TOOL_OPTIONS- MIST_FAULT_LOSTWRITE_ENABLED- -n default
kubectl rollout status deployment/ts-admin-route-service -n default
```
(Not spinning the cluster up just to unset this: B1's `SutFlagInjector` re-manages this exact flag, so the cleanup
is naturally subsumed by the first B1 run. Cleanup script: `tmp/cleanup_flag.sh`.)
