# MIST A-main — EXECUTION (decision: bet on Gate 3, 2026-06-30)

> Companion to `README.md` (plan v4). The team chose **Option 1: bet on execution** — build B1+B2 and run
> Gate 3, the only path to a clear A-accept and the cheapest way to falsify the idea before over-investing.
> This file turns §5/§8 of the plan into an ordered, concrete engineering sequence with code seams (from
> `research/01-feasibility-codebase.md`) and per-gate acceptance criteria. **Focus = Gate 1** (the first
> ~2–3 weeks); G2/G3 are outlined at the end. Build additively — default behavior must not change.

## North star
Reach **Gate 3**: find ≥1 (ideally ≥2, on ≥2 SUTs) **real** acknowledged-but-lost-write / missing-
compensation defect that a competently-configured assertion oracle misses *because no human authored that
assertion*. Everything before Gate 3 exists to make that hunt possible and credible.

---

## Prep finding (2026-06-30) that reshapes B1 — see prep/sut-fault-injection-capability.md
Our SUT fork (`train-ticket-injection@injection`) already has an in-service fault injector
(`FaultInjectionResponse` + inline `*ServiceImpl` hooks; canonical 10-fault state). It does input-validation
rejections today; a small SUT-side extension (`LOST_WRITE_FAULT`: skip persist, return 2xx) on a new
`MIST-trainticket` branch gives the differential oracle its **ground-truth positives with ZERO MIST tool
code**. So B1's *ground-truth* provocation = the SUT injector (clean, labeled); Toxiproxy / real outage is
reserved for the *unmodified-system* Gate-3 hunt. B2 (the oracle, in MIST) remains BLOCKED until "yes".

---

## Gate 1 — "the mechanism is real and sound on one SUT" (FIRST SPRINT)

**Definition of done (from plan §8 Gate 1):** on TrainTicket, B1+B2 run end-to-end; the differential oracle
**fires on a constructed lost-write**, and the read-back oracle's **FP rate on a benign-trap stratum is
measured and low**. **Scope (cold-review C-M2): this DoD = SYNC-mechanism soundness on ONE SUT** — the
async/CQRS regime is NOT validated here (async FP is largely timeout-gated ⇒ deferred to G3; TOOL-PLAN §4).
"Sound on one SUT" throughout this file means the sync regime only. Fail ⇒ mechanism unsound ⇒ revisit plan §9.

### G0 — Prerequisites (days)
- TrainTicket up via `make deploy` (k8s/minikube — see memory `trainticket-live-deploy`); Jaeger/OTel traces
  captured end-to-end. Confirm a clean trace for a **write** endpoint.
- **Pick the first target triple** `(POST endpoint, persisting dependency D, read-back GET)`. Candidate:
  an order/route create on `ts-*-service` that writes to its DB. Requirement: (a) mutates persisted state,
  (b) has a downstream DB/service write, (c) has a GET that reflects the resulting state.
- **Gate-1 fault backend = the SUT-flag injector** (`LOST_WRITE`, the S2/skip-persist path proven in the
  smoke: D never called → 2xx + `data:null` + state unchanged), toggled per run via
  `kubectl set env … JAVA_TOOL_OPTIONS=-Dmist.fault.lostwrite.enabled=true` + rollout. **Toxiproxy** (TCP-level
  DB-socket cut = the S1/errored-D path) is **DEFERRED to G3's unmodified-system hunt, NOT Gate-1** — two cold
  reviewers flagged the old "stand up Toxiproxy at Gate-1" wording as drift against the SUT-flag-first decision
  (TOOL-PLAN §6; B1.1). R3 caveat for when it lands: TCP/connection-level, not an app-aware DB abort.

### G1a — B1: opt-in fault-injection mode (~1 wk) — Task #10
- **Config:** add the two OFF-by-default flags **per TOOL-PLAN P1's corrected pattern** (B1 =
  `mist.fault.injection.enabled` **system property**; B2 = `mst.oracle.dataintegrity.enabled` in
  `MstConfig.Oracle` — **not** both wired into MstConfig as earlier worded). Opt-in only; zero effect when off.
- **Injector:** new mist-cli orchestration that, per fault run, toggles the fault on D, runs the request, then
  clears it, behind `FaultInjector{ inject(dep), clear() }`. **Gate-1 backend = `SutFlagFaultInjector`**
  (`kubectl set env … -Dmist.fault.lostwrite.enabled=true` + rollout = the S2 path; smoke-proven);
  Toxiproxy/mesh back-ends are swappable but **deferred to G3** (S1/errored-D).
- **Realistic inputs:** drive the request from the existing **two-phase verified-input pool**
  (`MistRunner.java:502-557`) so the only abnormality is the injected fault.
- **Acceptance (Gate-1 = SUT-flag / S2):** with the mode on, the fault run returns **2xx acknowledging X but X
  is absent on its read-back** while the control run shows X present — note **there is NO errored D span** (S2:
  D is never called). The old "D's span errored/aborted" acceptance applies only to the **G3 Toxiproxy/S1**
  path. Mode off ⇒ nothing changes vs today.

### G1b — B2: differential data-integrity oracle + soundness protocol (~1 wk) — Task #11
- **Pairing executor:** new path that, for target request R, executes **control** (no fault → read-back
  `S_control`, trace `T_control`) then **fault** (inject on D → read-back `S_fault`, trace `T_fault`).
- **Soundness protocol (plan §4 — this is the real work, not a footnote):**
  1. **Isolation:** fresh unique entity per test (unique IDs / new account); no shared mutable key across the
     two runs. For shared-inventory state (seats/stock) use a dedicated test namespace + reset between runs
     (R3 caveat — black-box isolation fails on shared inventory otherwise).
  2. **Quiescence:** poll the read-back GET until the value stabilizes OR `T_fault` shows all causally-related
     spans complete, bounded timeout. **Disclose:** if cross-broker span-links are absent/broken, quiescence
     degrades to a wall-clock timeout (R3) — log which path was used per test.
  3. **Late compensation:** bounded window for saga/compensation completion (trace-detected); distinguish
     *pending* from *missing*.
  4. **Normalization:** idempotency keys; normalize volatile fields (timestamps/IDs) before the diff.
- **Fire rule — TWO modes (authoritative spec: `TOOL-EXECUTION-PLAN.md` §3 B2.3; refined post-REVIEW2):** a
  per-run relation — a 2xx/"success" run acknowledging entity X must have X on its OWN read-back. **pure-
  differential (headline):** fire = fault run 2xx acknowledging X AND X absent/stale on the fault read-back AND
  control shows X present (no D-error gate → also catches S2/skip-persist; read-back stays trace-independent).
  **gated (high-confidence / S1, validation deferred to G3):** the same AND `D span observed errored/aborted`.
  **Do NOT build only the single gated rule — it cannot fire on the S2 smoke case** (D never called, no D span).
- **Checker + surface:** new `ShapeInvariant`-style checker; surface findings in the report/Allure with the
  control/fault traces and the state diff.

### G1c — Validate Gate 1 + measure FP (days) — Task #12
- **Sensitivity:** construct a case where the SUT *masks* a failed write as 2xx (or inject such that the write
  is dropped) → confirm the oracle **FIRES** with the state diff as evidence.
- **Specificity / FP:** build a **benign-trap stratum** (eventually-consistent-then-correct, retry-then-
  succeed, optional-dependency), **which MUST include a broker-mediated async write path** (TOOL-PLAN B2.4 /
  prerequisite P3 — a synchronous sleep does not exercise the async span-link degradation R3 fears) → measure
  the read-back oracle's **FP/FN rate**; report it **per-SUT AND per-stratum (sync vs async)** with
  quiescence-gate coverage (plan §8.5). Target: low/characterized **sync** FP; async FP as a curve over the
  pre-registered timeout (TOOL-PLAN §4), not a point.
- **Gate 1 verdict:** PASS = fires on the constructed case + measured low FP ⇒ proceed to G2/G3.
  FAIL ⇒ mechanism unsound ⇒ plan §9 (re-venue or pivot).

---

## G2 — Novelty articulation + comparator (before scaling) — Task #13 (part 1)
- Write the one-paragraph Cast delta a skeptical PC accepts (generation vs replay / black-box vs Java-AOP /
  read-back vs metric-threshold / open vs closed) — verified facts in plan §2.
- Stand up a **competently-configured assertion-based comparator** on the **same** injected faults:
  Filibuster-style FI + hand-authored assertions, and/or a Cast-pattern oracle (metric thresholds +
  assertion points). It must be visibly non-strawman.

## G3 — The empirical bug hunt (make-or-break) — Task #13 (part 2)
- Run B1+B2 across **≥2 write-path SUTs** (TrainTicket + TeaStore/Sock Shop; pre-specify the concrete
  saga/dual-write/compensation site per SUT — plan §8.5 item 3).
- **Target:** ≥1–2 real lost-write/missing-compensation defects the comparator misses because no human wrote
  that assertion. Report ≥2 incidental dev-confirmed bugs (Morest bar). Release the labeled corpus (C2).
- Outcome routing: bugs land ⇒ full eval (plan §6) → A submission. Bugs thin ⇒ Plan B (benchmark +
  prevalence + capability) at a better-fit venue.

---

## Pre-registration commitments to honor during execution (plan §8.5)
Underspecified-case resolution rule + report underspecified fraction; read-back FP **per-SUT** + async write-
path coverage disclosure; data-integrity **depth** (named sites, not just count); frame replay-coverage delta
as argued-not-measured unless measured; disclose the soundness threats-to-validity. Pre-register the rubric
and thresholds **before** the G3 run.

*Status 2026-06-30: execution kicked off. Immediate next action = G1a (Task #10) — scaffold the opt-in
fault-injection mode. Build additively; keep `mist.fault.injection.enabled=false` the default.*
