# Tool execution plan — B1 (fault-injection mode) + B2 (differential data-integrity oracle)

> **This plan TOUCHES MIST tool code → executing it requires the user's explicit "yes".** It is the
> step-by-step, evidence-anchored sequence to build B1+B2 toward Gate 1, refined by the live Gate-1 smoke
> result. Every step cites a real code seam (from `research/01-feasibility-codebase.md`, audited 2026-06-30
> with `file:line`; **re-verify the exact line at edit time**) and/or the smoke evidence
> (`prep/gate1-smoke-result.md`). All MIST changes go on branch `main_track`. **Build additively — with every
> flag OFF, behavior is byte-for-byte unchanged.** Date 2026-06-30.

## 0. Facts the smoke test established (this plan's footing — not assumptions)
1. On a real, fully-deployed TrainTicket, the SUT-side `LOST_WRITE` makes `POST /adminroute` return **HTTP
   200 / `status:1`** while the route is **never persisted** (getAllRoutes count unchanged). Control (flag
   off) persists; fault (flag on) loses it. → The differential read-back signal B2 targets is **real**.
2. **All response-level oracles pass the fault run** (status 200, schema ok, body `status:1`). Only the
   control-vs-fault **read-back** distinguishes them. → B2's value is exactly read-back, not the response.
3. **read-back is a black-box GET** (`GET /adminroute` = getAllRoutes), independent of the trace. → B2 is
   **not bounded by the trace signal floor** (research/01 §D) — it works on Envoy SUTs too, unlike the
   trace-shape oracle. This is B2's structural advantage and must be stated that way.
4. The opt-in fault flag **must use a JVM `-D` system property** (`JAVA_TOOL_OPTIONS=-Dmist.fault....=true`),
   NOT env relaxed-binding (which silently failed on TT's Spring-Cloud+nacos bootstrap). Load-bearing for B1.
5. SUT = `train-ticket-injection@MIST-trainticket`, deployed the team's proven way
   (`evaluation/suts/trainticket/deploy/deploy.sh` = minikube + make build/deploy). Target triples:
   adminroute (collection read-back) and **adminbasic/contacts (fresh-UUID per-entity read-back — the clean
   FP-measurement target)**.

## 1. Pre-flight (no-regret; do first, no behavior change)
- **P1. Branch + flags.** Confirm on `main_track`. Reserve two OFF-by-default config keys, mirroring the
  existing `mist.fault.mining.enabled` pattern (`FaultMiner.java:35-40`): `mist.fault.injection.enabled`
  (B1) and `mist.oracle.dataintegrity.enabled` (B2). Wire them into `MstConfig` next to the mining flag.
  *Verify:* with both false, a normal run diffs zero against today.
- **P2. Target-triple registry.** Add a small per-SUT config (reuse the bundle's `real-system-conf.yaml`
  style) listing `{write_endpoint, dependency, readback_endpoint, isolation_key}` — exactly the
  `prep/target-triples.md` triples. No logic yet; data only.

## 2. B1 — opt-in fault-injection mode (research/01 build-list #4; EXECUTION G1a)
**Goal:** for a target write request, run it once clean (control) and once with the dependency faulted
(fault), reusing realistic inputs. *Effort: L. This converts MIST to an opt-in grey-box controller — gate it
behind the flag and frame it as a mode, or the "no SUT instrumentation" identity breaks (research/01 §4.1).*

- **B1.1 FaultInjector interface** `{ inject(target), clear() }` with swappable backends. Smoke evidence
  says the **cleanest ground-truth backend is the SUT-side flag**, so implement `SutFlagFaultInjector`
  FIRST: `inject` = `kubectl set env deploy/<svc> JAVA_TOOL_OPTIONS=-D<key>=true` + `rollout status`;
  `clear` = unset + `rollout status`. (Toxiproxy DB-socket backend is deferred to Gate-3 unmodified-system
  per EXECUTION G0 — not needed for the labeled positives.)
- **B1.2 Realistic input reuse.** Drive the request from the existing two-phase verified-input pool
  (`MistRunner.java:502-557`) so the *only* abnormality is the injected fault.
- **B1.3 Control/fault pairing executor.** New orchestration: for target request R, execute control (no
  inject) then fault (inject → run → clear). Keep it a separate code path entered only when
  `mist.fault.injection.enabled`.
- **B1.4 Acceptance.** Fault run shows the masked write (smoke: `status:1` + `data:null` + getAllRoutes
  unchanged); control run persists; flag OFF ⇒ no new code path runs. *Verify by re-running the exact smoke
  scenario through MIST instead of curl.*

## 3. B2 — differential data-integrity oracle + soundness protocol (EXECUTION G1b; the real work)
**Architecture note (decided, not on a whim):** B2 is **not** a passive `ShapeInvariant` over a trace — it
must actively issue the **read-back GET** and diff state across the control/fault pair. So it is a new
*active* oracle hanging off the B1 pairing executor, surfaced alongside the trace oracle — not a new
`ShapeInvariant.evaluate(trace)` (`ShapeInvariant.java:11-21`). It reuses the writer's request/response
plumbing (`MultiServiceRESTAssuredWriter.java:706-707` staples the entry response; the read-back GET goes
right after) and the oracle-surfacing path (`:714`).

- **B2.1 Read-back capture.** After each run in the pair, GET the triple's `readback_endpoint` and capture
  `S_control` / `S_fault` (the smoke read-back: getAllRoutes JSON).
- **B2.2 Soundness protocol (plan §4 — this is the contribution's spine, not a footnote):**
  - **Isolation:** one fresh unique entity per test (smoke used distinct stations + the service's UUID id).
    Prefer the **adminbasic/contacts** triple (fresh UUID per create) so runs never collide — the clean
    target for the FP number.
  - **Quiescence:** poll `readback_endpoint` until stable (K consecutive equal reads) OR a bounded timeout
    before diffing; if still changing → label `inconclusive`, exclude, and report the excluded fraction.
    (Smoke was synchronous; async write paths REQUIRE this — see B2.4.)
  - **Normalization:** strip volatile fields (timestamps, server-generated ids) before the diff.
- **B2.3 Fire rule.** Fire iff fault-run response is 2xx/success **AND** `S_fault` violates the success
  contract vs `S_control` (entity absent / stale / partial). Smoke instance: fault `status:1` but
  getAllRoutes count unchanged ⇒ fire. Surface the finding in the report/Allure with both read-backs + the
  state diff (reuse the oracle-surface path at `MultiServiceRESTAssuredWriter.java:714`).
- **B2.4 MEASURE the read-back FP rate (make-or-break, plan §8.5).** Build a **benign-trap stratum** the
  oracle must NOT fire on: eventually-consistent-then-correct writes. This needs a SUT-side `DELAYED_WRITE`
  injector (persist on a delay) on `MIST-trainticket` — the one remaining SUT-side prep, deferred from the
  benchmark. Report the oracle's FP/FN **per-SUT** under async load. A low, characterized FP here is the
  Gate-1 pass condition; an uncharacterized one sinks the contribution.

## 4. Gate-1 validation (EXECUTION G1c)
- **Sensitivity:** drive B1+B2 over the adminroute + adminbasic triples; confirm B2 FIRES on the constructed
  lost-write with the state diff as evidence (smoke already showed this manually; here it's automated).
- **Specificity:** run the benign-trap stratum (B2.4); confirm B2 does NOT fire after quiescence; record the
  FP rate per SUT.
- **Gate-1 verdict:** PASS = fires on the constructed case + measured-low FP ⇒ proceed to G2/G3. FAIL ⇒
  mechanism unsound ⇒ plan §9 (re-venue / pivot). Record results next to `prep/gate1-smoke-result.md`.

## 5. Sequencing & per-step verification gates
```
P1 flags/branch        → verify: flags OFF == today's behavior (diff test)
P2 triple registry     → verify: config loads, no logic touched
B1.1 SutFlagInjector   → verify: inject/clear toggles the flag + rollout (re-run smoke via injector)
B1.2 input reuse       → verify: fault run uses a pool-verified valid input
B1.3 pairing executor  → verify: control persists, fault masks (smoke parity, automated)
B2.1 read-back capture → verify: S_control != S_fault on the lost-write case
B2.2 soundness         → verify: isolation (fresh entity), quiescence (stable read), normalization
B2.3 fire rule         → verify: fires on lost-write, surfaced with diff
B2.4 FP measurement    → verify: benign-trap stratum, FP rate reported per SUT  ← make-or-break
Gate-1 verdict         → PASS/FAIL recorded
```

## 6. Decisions made deliberately (so they're not re-litigated mid-build)
- **Flag via `-D`, never env** (smoke fact #4). Same for any second-service injector.
- **SUT-flag injector first, Toxiproxy later** — smoke proved the SUT flag is the cleanest labeled positive;
  Toxiproxy/real-outage is for the Gate-3 unmodified-system hunt only.
- **B2 is an active read-back oracle, not a trace invariant** — it owns SUT control (the GET), so it lives in
  the B1 pairing path, gated by its own flag; the trace-shape oracle is untouched.
- **The FP number is the deliverable** (plan §8.5), not the fire demo (smoke already did the fire demo).
- **None of B1/B2 makes the A-accept case by itself** — Gate 3 (a real, non-injected lost-write/missing-
  compensation bug that assertion tools miss) does. B1/B2 exist to make that hunt possible and credible.

## 7. What is explicitly OUT of this plan
- No statistical invariant mining (research/01 #3) — BLOCKED-by-data (n=1 corpus), orthogonal to B2.
- No param-level attribution (research/01 §C) — instrumentation-bound, not load-bearing.
- No changes to the shipped trace-shape oracle / Sniper / Root-API mode — untouched.
