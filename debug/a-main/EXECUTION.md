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

## Gate 1 — "the mechanism is real and sound on one SUT" (FIRST SPRINT)

**Definition of done (from plan §8 Gate 1):** on TrainTicket, B1+B2 run end-to-end; the differential oracle
**fires on a constructed lost-write**, and the read-back oracle's **FP rate on a benign-trap stratum is
measured and low**. Fail ⇒ mechanism unsound ⇒ revisit plan §9.

### G0 — Prerequisites (days)
- TrainTicket up via `make deploy` (k8s/minikube — see memory `trainticket-live-deploy`); Jaeger/OTel traces
  captured end-to-end. Confirm a clean trace for a **write** endpoint.
- **Pick the first target triple** `(POST endpoint, persisting dependency D, read-back GET)`. Candidate:
  an order/route create on `ts-*-service` that writes to its DB. Requirement: (a) mutates persisted state,
  (b) has a downstream DB/service write, (c) has a GET that reflects the resulting state.
- Stand up **Toxiproxy** in front of the target service's **DB connection** (black-box DB-write fault: a TCP
  cut/timeout on the DB socket during the write). Note R3 caveat: this is TCP/connection-level, not an
  app-aware DB abort — adequate for a first lost-write provocation; document the limitation.

### G1a — B1: opt-in fault-injection mode (~1 wk) — Task #10
- **Config:** add `mist.fault.injection.enabled` (default **false**) + a target-dependency spec to MstConfig
  (mirror the existing flag plumbing). Opt-in only; zero effect when off.
- **Injector:** new orchestration component (mist-cli) that, per fault-run test, toggles the Toxiproxy
  rule on D, runs the request, then clears it. Abstract the injector behind an interface
  (`FaultInjector{ inject(dep), clear() }`) so Toxiproxy/mesh/Chaos-Mesh back-ends are swappable.
- **Realistic inputs:** drive the request from the existing **two-phase verified-input pool**
  (`MistRunner.java:502-557`) so the only abnormality is the injected fault.
- **Acceptance:** with the mode on, a fault run shows D's span errored/aborted in the captured trace; with
  the mode off, nothing changes vs today.

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
- **Fire rule:** fault-run client response is 2xx/"success" AND D span errored/aborted AND `S_fault` violates
  the success contract (entity absent / stale / partial vs `S_control`).
- **Checker + surface:** new `ShapeInvariant`-style checker; surface findings in the report/Allure with the
  control/fault traces and the state diff.

### G1c — Validate Gate 1 + measure FP (days) — Task #12
- **Sensitivity:** construct a case where the SUT *masks* a failed write as 2xx (or inject such that the write
  is dropped) → confirm the oracle **FIRES** with the state diff as evidence.
- **Specificity / FP:** build a **benign-trap stratum** (eventually-consistent-then-correct, retry-then-
  succeed, optional-dependency) → measure the read-back oracle's **FP/FN rate**; report it **per-SUT** (plan
  §8.5). Target: low/characterized.
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
