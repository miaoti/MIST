# PREP (main-track) — our SUT fault-injection capability + how it reshapes B1/B2

> Main-track prep — **NO MIST tool code.** Read-only inspection of OUR TrainTicket fork
> (`C:\Users\miaot\Github\train-ticket-injection`, branch `origin/injection`; per the user, only this branch
> is ours — the `ts-error-F*` / `istio-error-*` branches are upstream reference). **Finding:** the fork
> already has an in-service fault injector. It serves the *masking/soft-error* oracle's ground truth today,
> and can be EXTENDED (SUT-side, on a new `MIST-trainticket` branch, **no tool code**) to serve the
> differential *data-integrity* oracle. Date 2026-06-30.

## 1. What exists on `injection` (21 commits ahead of master)
An application-level fault injector baked into the services (adminroute, adminorder, admintravel, adminbasic,
auth, travel, travelplan): a `FaultInjectionResponse` entity `{isInjected, faultName, message, details}` +
inline hooks in each `*ServiceImpl`, documented per-service in `FAULT_INJECTION_SUMMARY.md` + a root
`INJECTED_FAULTS.md`. Recent injection-branch commits even prototyped MIST trace-oracle mutants on adminroute
("INSUFFICIENT_STATIONS silent-acceptance", "HIDDEN_DOWNSTREAM swallow", then "restore … canonical 10-fault state").

Verbatim pattern (`AdminRouteServiceImpl.createAndModifyRoute`):
```java
if (stations == null || stations.isEmpty()) {
    FaultInjectionResponse fault = new FaultInjectionResponse(true, "INSUFFICIENT_STATIONS_FAULT", "...");
    return new Response<>(0, "Route creation rejected: station list cannot be null or empty", fault);
}
// ... else: checkStationsExists -> downstream persist (ts-route-service) -> return re.getBody();
```
Marker MIST's `FaultDetectionTracker` matches: body `{status:0, msg, data:{isInjected:true, faultName, message, details}}`.

## 2. The canonical faults today = INPUT-VALIDATION REJECTIONS (loud soft errors)
INSUFFICIENT_STATIONS, INVALID_STATION_NAME_LENGTH (adminroute); INVALID_CONTACTS_NAME, INVALID_SEAT_NUMBER
(adminorder); INVALID_PRICE_RATE (adminbasic); … All: bad input → `status:0` soft-error reject. These are the
tool-demo's 10-fault corpus and serve the **soft-error / masking** oracle's positives.

## 3. The GAP for the differential data-integrity oracle (plan §4)
The data-integrity oracle needs the OPPOSITE of a rejection: the service returns **2xx success** yet the write
is **silently lost** (acknowledged-but-not-persisted) or a downstream error is **swallowed** behind a 2xx. The
current injector only does loud-ish rejections (`status:0`), not success-but-lost-write.

## 4. The fix is SUT-side (no MIST tool code): extend the injector on `MIST-trainticket`
Add new fault types to the SAME mechanism, gated by a config flag, e.g. in `createAndModifyRoute`:
- **LOST_WRITE_FAULT** — skip the real persist but return success:
  ```java
  if (faultCfg.lostWrite() && triggers(request)) {
      // do NOT call the downstream persist
      return new Response<>(1, "create and modify success", request); // 2xx success, nothing saved
  }
  ```
  → POST returns 2xx; `GET getAllRoutes` does NOT show the route ⇒ acknowledged-but-lost write (oracle must fire).
- **SWALLOW_DOWNSTREAM_FAULT** — call downstream; if it errors, swallow it and return 2xx anyway (the
  HiddenDownstream case; already prototyped on the injection branch).
Config via the existing per-service `application.yml` fault keys.

## 5. Implications — reshapes EXECUTION B1/B2 + ground truth
- **Stratum-1 positives are SUT-injected** (RCAEval/Nezha-style; known root cause), for BOTH oracles, built
  entirely SUT-side — **no MIST tool code, no Toxiproxy needed for the ground-truth corpus.**
- **B1 (provoke a downstream/persistence fault):** for ground truth use the SUT injector (cleanest,
  controllable, known label). Toxiproxy / real outage remains for the *unmodified-system* black-box case (Gate 3).
- **B2 (the differential oracle) still must be built in MIST → BLOCKED until "yes".** But its INPUTS (the
  lost-write positives) can be prepared now, SUT-side.
- **Target triple A (adminroute) is doubly good:** known endpoint + existing traces + the injector hook is
  already in `createAndModifyRoute` (add `LOST_WRITE` next to `INSUFFICIENT_STATIONS`).

## 6. Honest caveat for the paper
SUT-injected lost-writes are **ground-truth positives** (legitimate, standard practice — RCAEval/Nezha inject
faults). They are NOT the headline "real bug" claim. The headline still requires Gate 3: a *real*
lost-write/missing-compensation defect in an unmodified system that assertion tools miss. The SUT injector
gives us recall/precision/FP measurement; Gate 3 gives us the bug story.

## 7. Next prep step (SUT-side, allowed — no tool code)
Branch `MIST-trainticket` from `injection`; add `LOST_WRITE_FAULT` (+ optionally `SWALLOW_DOWNSTREAM_FAULT`)
to adminroute (+ 1–2 more write-path services) behind a config flag; document in `INJECTED_FAULTS.md`; build
to confirm it compiles. Write the code via the karpathy-guidelines skill. This builds the differential-oracle
ground truth with zero MIST tool changes.

## 8. Implemented (2026-06-30) — DONE for adminroute
Branch `MIST-trainticket` created from `origin/injection`; commit `5c471dd8` adds `LOST_WRITE_FAULT` to
`AdminRouteServiceImpl.createAndModifyRoute`: when env `MIST_FAULT_LOSTWRITE_ENABLED=true`, it returns
`status:1` "create and modify success" but skips the persist to `ts-route-service` (masked, no marker).
Default off = no behavior change. Documented in `ts-admin-route-service/FAULT_INJECTION_SUMMARY.md`.
**Build/deploy + read-back test → WSL2 runbook: `gate1-environment-runbook.md`.** Branch kept LOCAL (remote
is the collaborator's `AsifShaafi/train-ticket-injection`; not pushed — WSL2 builds from the local checkout).
Compiler not run here (trivial, review-verified); real verification is the runbook's read-back test on WSL2.

## 9. Implemented (2026-06-30) — DONE for adminbasic/contacts (2nd service)
Commit `bbf3d6ae` (MIST-trainticket) adds the same `LOST_WRITE_FAULT` to
`AdminBasicInfoServiceImpl.addContact` (`POST /api/v1/adminbasicservice/adminbasic/contacts`): when env
`MIST_FAULT_LOSTWRITE_ENABLED=true`, it returns `status:1` "create contacts success" but skips the persist
to `ts-contacts-service`. Same per-container env key (set it on `ts-admin-basic-info-service` to isolate).
This is **target triple B** — a per-entity create with a fresh UUID per request, the cleanest isolation for
**measuring the read-back oracle's FP rate** (plan §8.5). Two lost-write positives now exist on two distinct
write-path services (adminroute, adminbasic), so the oracle's ground truth is not endpoint-specific.
Benchmark seed case: `../benchmark/cases/TT-adminbasic-contacts-lostwrite-001.json`. Build/deploy verified
on WSL2 via `make build` (same path as adminroute). Compiler/Response-ctor review-verified here
(`Response<T>` is Lombok `@AllArgsConstructor(status,msg,data)`; `Contacts` is `@Data` → `getId()`).
Remaining optional SUT extension: `SWALLOW_DOWNSTREAM_FAULT` (input-driven masked downstream error — would
add a stratum-1 positive for the swallowed-downstream class and directly attack finding F4).
