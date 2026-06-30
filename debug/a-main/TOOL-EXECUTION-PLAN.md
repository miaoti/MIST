# Tool execution plan — B1 (fault-injection mode) + B2 (differential data-integrity oracle)

> **This plan TOUCHES MIST tool code → executing it requires the user's explicit "yes".** It is the
> step-by-step, evidence-anchored sequence to build B1+B2 toward Gate 1, refined by the live Gate-1 smoke
> result **and a multi-round self-review against README §3/§4, EXECUTION G1a/b/c, all three Round-2 reviewers
> (R1 novelty, R2 evaluation, R3 soundness), plus engineering-feasibility & internal-consistency passes**.
> Every step cites a real code seam (from
> `research/01-feasibility-codebase.md`, audited 2026-06-30 with `file:line`; **re-verify the exact line at
> edit time**) and/or the smoke evidence (`prep/gate1-smoke-result.md`) and/or a reviewer concern. All MIST
> changes go on branch `main_track`. **Build additively — with every flag OFF, behavior is byte-for-byte
> unchanged.** v3 (review-hardened), 2026-06-30.

## 0. Facts the smoke + review established (this plan's footing — not assumptions)
1. On a real, fully-deployed TrainTicket, the SUT-side `LOST_WRITE` makes `POST /adminroute` return **HTTP
   200 / `status:1`** while the route is **never persisted** (getAllRoutes count unchanged). Control persists;
   fault loses it. → The differential read-back signal B2 targets is **real**.
2. **All response-level oracles pass the fault run** (status 200, schema ok, body `status:1`). Only the
   control-vs-fault **read-back** distinguishes them. → B2's value is exactly read-back, not the response.
3. **read-back is a black-box GET**, so its signal is **independent of the trace signal floor** (research/01
   §D: status+topology+otel/response_flags) — B2 does **not** degrade on Envoy-only SUTs where the trace
   carries less. **BUT applicability is a separate axis:** B2 applies **only** to write-path SUTs with a clean
   black-box read-back (README §4.6 — TrainTicket/TeaStore/Sock Shop/petclinic), **not** read-only/derived
   demos (Bookinfo, Online Boutique). Do not conflate "signal-floor-independent" with "runs on any Envoy SUT."
4. The opt-in fault flag **must use a JVM `-D` system property** (`JAVA_TOOL_OPTIONS=-Dmist.fault....=true`),
   NOT env relaxed-binding (which silently failed on TT's Spring-Cloud+nacos bootstrap). Load-bearing for B1.
5. SUT = `train-ticket-injection@MIST-trainticket`, deployed the team's proven way
   (`evaluation/suts/trainticket/deploy/deploy.sh` = minikube + make build/deploy). Target triples:
   adminroute (collection read-back) and **adminbasic/contacts (fresh-UUID per-entity read-back — the clean
   FP-measurement target)**. Both are **non-shared-inventory** writes (see §3 B2.2 isolation).
6. **Two defect shapes the oracle must cover** (decided in §3 B2.3 fire rule):
   - **S1 — swallowed downstream error:** D is called → D errors → error swallowed → 2xx. **D span errored.**
   - **S2 — skipped persist:** D is never called (logic bug / wrong branch / forgot to save) → 2xx. **No D
     span.** Our smoke is S2.
   The point that matters: a read-back differential keys on **persisted state, not on a downstream-error
   signal**, so it covers S1 and S2 *alike*. Any **downstream-error-keyed** oracle (the naive span-error
   baseline, MIST's own gated mode in B2.3) has **no signal on S2** — that is the real, **E2-measurable**
   delta (README §6 precision-vs-naive-span-error). **Do NOT overclaim this against Cast:** Cast's *injection*
   sits at the DB call (S1-style) and its *oracle* is metric-threshold/assertion-point — a different,
   stronger-assumption mechanism; whether Cast's assertion points catch a *naturally-occurring* S2 is
   **unverified**, so claim only "different / weaker-assumption mechanism," never "Cast structurally cannot
   reach S2." (Honesty basis = REVIEW2-R1, which flags manufactured deltas; research/03 §4.3 already concedes
   the read-back diff "automates an assertion, not a new analysis.")

## 1. Pre-flight (no-regret; do first, no behavior change)
- **P1. Branch + flags.** Confirm on `main_track`. Reserve two OFF-by-default config keys, mirroring the
  existing `mist.fault.mining.enabled` pattern (`FaultMiner.java:35-40`): `mist.fault.injection.enabled`
  (B1) and `mist.oracle.dataintegrity.enabled` (B2). Wire them into `MstConfig` next to the mining flag.
  *Verify:* with both false, a normal run diffs zero against today.
- **P2. Target-triple registry.** Add a small per-SUT config (reuse the bundle's `real-system-conf.yaml`
  style) listing `{write_endpoint, dependency, readback_endpoint, isolation_key}` — exactly the
  `prep/target-triples.md` triples. The `dependency` must be a **trace-matchable service/operation key** (the
  gated mode in B2.3 needs to locate D's span). No logic yet; data only.

## 2. B1 — opt-in fault-injection mode (research/01 build-list #4; EXECUTION G1a)
**Goal:** for a target write request, run it once clean (control) and once with the dependency faulted
(fault), reusing realistic inputs. *Effort: L. This converts MIST to an opt-in grey-box controller — gate it
behind the flag and frame it as a mode, or the "no SUT instrumentation" identity breaks (research/01 §4.1).*

- **B1.1 FaultInjector interface** `{ inject(target), clear() }` with swappable backends. Smoke evidence
  says the **cleanest ground-truth backend is the SUT-side flag**, so implement `SutFlagFaultInjector`
  FIRST: `inject` = `kubectl set env deploy/<svc> JAVA_TOOL_OPTIONS=-D<key>=true` + `rollout status`;
  `clear` = unset + `rollout status`. (Toxiproxy backend deferred to Gate-3 unmodified-system per EXECUTION
  G0. **When built, word it as connection/TCP-level faults, not per-D-span DB aborts** — Toxiproxy is not
  protocol-aware (R3 concern #5).)
  **Identity note (disclose, or an evaluation reviewer reads "you modified the SUT" as breaking black-box):**
  the SUT-flag injector is **scaffolding to manufacture labeled ground truth, not a tool dependency** — MIST
  itself needs no SUT change. The black-box / no-SUT-change identity is carried by the Toxiproxy
  (Gate-3 unmodified-system) path; Gate-1's SUT flag only buys clean labels. State this wherever Gate-1
  results appear.
- **B1.2 Realistic input reuse.** Drive the request from the existing two-phase verified-input pool
  (`MistRunner.java:502-557`) so the *only* abnormality is the injected fault.
- **B1.3 Control/fault pairing executor.** New orchestration: for target request R, execute control (no
  inject) then fault (inject → run → clear). Separate code path entered only when `mist.fault.injection.enabled`.
- **B1.4 Acceptance.** Fault run shows the masked write (smoke: `status:1` + `data:null` + getAllRoutes
  unchanged); control run persists; flag OFF ⇒ no new code path runs. *Verify by re-running the exact smoke
  scenario through MIST instead of curl.*

## 3. B2 — differential data-integrity oracle + soundness protocol (EXECUTION G1b; the real work)
**Architecture (decided):** B2 is an **active differential oracle** — it actively issues the read-back GET and
diffs *state* across the control/fault pair. It is **not** a `ShapeInvariant.evaluate(trace)`
(`ShapeInvariant.java:11-21`) — that interface evaluates a single trace; B2 needs an active GET + a pairwise
state diff. It hangs off the B1 pairing executor and reuses the writer's request/response plumbing
(`MultiServiceRESTAssuredWriter.java:706-707` staples the entry response; the read-back GET goes right after)
and the oracle-surfacing path (`:714`). (EXECUTION calls it a "ShapeInvariant-*style* checker" meaning it
reuses that surfacing pattern, not that it implements the interface — this doc is the precise version.)

**Dual role (why it matters to the contribution, not just a detector):** B2's discriminating signal is
*persisted state* (`S_control` vs `S_fault`), a **different signal class** from the masking oracle's *trace
topology+status*. So B2 is also the **independent ground truth that de-circularizes the cheap masking oracle**
(README §6; R2 §2a confirms this is the core of the de-circularization). Keeping read-back **independent of
the trace** is a *precondition* for that role — which is exactly why the headline fire mode carries no
trace-derived gate (see B2.3).

- **B2.1 Read-back capture.** After each run in the pair, GET the triple's `readback_endpoint` and capture
  `S_control` / `S_fault` (smoke read-back: getAllRoutes JSON).
- **B2.2 Soundness protocol (§4 — the contribution's spine, not a footnote):**
  - **Isolation:** one fresh unique entity per test (smoke used distinct stations + the service's UUID id).
    Prefer the **adminbasic/contacts** triple (fresh UUID per create). Gate-1 deliberately picks
    **non-shared-inventory** targets so black-box isolation holds; the shared-inventory threat (TrainTicket
    seats / Sock Shop stock, where fresh-ID does NOT isolate finite contention — R3 concern #2) needs
    namespace+reset (not black-box) and is a **G3 threat-to-validity, not a Gate-1 target**.
  - **Quiescence:** **poll the read-back until stable OR the trace shows all causally-related spans completed**
    (trace-driven), bounded timeout. **Report quiescence-gate coverage** per-SUT: for each verdict, was it
    gated by *observed span completion* or by *wall-clock timeout*? Treat **timeout-gated verdicts as a
    separate, lower-confidence stratum** (R3 concern #1 = "the load-bearing soundness gap"; a low FP that is
    90% timeout-gated does not convince R3). This is the single most important soundness add over v1.
  - **Late compensation (the missing_compensation path):** for saga/compensation flows, wait a bounded,
    trace-detected window and **distinguish *pending* (not yet arrived) from *missing* (truly absent)**
    (README §4.3). v1 only handled lost-write; this path is required because missing_compensation is a named
    fault class in our benchmark.
  - **Normalization:** idempotency keys; strip volatile fields (timestamps, server-generated ids) before diff.
- **B2.3 Fire rule — a per-run metamorphic relation, in TWO modes reported separately.**
  Base relation (both modes): a run whose client response is **2xx/"success" and that acknowledges entity X**
  must have **X present/correct on its OWN read-back**; fire when that is violated. *Diff each run against its
  own acknowledgement — NOT one collection against the other.* The control run is a **control for false
  positives** (it proves X would be present under no fault, ruling out invalid-input / test-bug causes), not
  the thing we subtract. (For collection read-backs like getAllRoutes, check "X ∈ read-back," not
  list-equality — the control run legitimately adds its own X.)
  - **pure-differential (HEADLINE):** fire = `fault run 2xx/success acknowledging X  AND  X absent/stale on the
    fault read-back  AND  control run shows X present`. No trace gate → read-back stays **independent of the
    trace** (the de-circularization precondition, R2 §2a) and covers **S2** (no D-error to key on). **Honest
    tradeoff:** FP is then controlled *entirely* by the B2.2 protocol → reported as a **lower-confidence
    stratum** (R3 #1), never pooled with gated.
  - **gated (high-confidence, S1):** the base relation AND `D span observed errored/aborted`. The observed
    D-error is an independent corroborating signal → lowest FP. **It needs a real D failure (Toxiproxy), which
    is deferred to G3 — so this mode is implemented but its validation lands at G3, not Gate 1** (see §4).
  - Smoke instance (S2): fault `status:1` acknowledging route X, but X ∉ getAllRoutes while the control's route
    IS present ⇒ pure-differential fires. Surface in report/Allure with both read-backs + the per-run diff
    (reuse `MultiServiceRESTAssuredWriter.java:714`).
- **B2.4 MEASURE the read-back FP rate (make-or-break, §8.5):**
  - Build a **benign-trap stratum** the oracle must NOT fire on: eventually-consistent-then-correct writes.
    **It MUST include a broker-mediated async write path** (TrainTicket's rabbitmq paths), **not just a
    synchronous `DELAYED_WRITE` sleep** — because R3's real soundness fear is the async regime where OTel
    yields span *links*, producer/consumer spans need not co-occur in one trace, and trace-driven quiescence
    silently degrades to a wall-clock timeout (R3 #1/#4). A synchronous sleep only tests "did poll wait long
    enough"; it does **not** exercise the degradation R3 names. (A SUT-side async injector is the remaining
    SUT-side prep.)
  - Report measured FP/FN **per-SUT** + **quiescence-gate coverage per-SUT**; state explicitly that FP on a
    *constructed* benign-trap is a **lower bound** on wild-async FP (R3 #4).
  - **Gate-1 pass = fires on the constructed lost-write + low/characterized FP (especially in the
    non-timeout-gated stratum);** uncharacterized FP sinks the contribution.

## 3.5 Scope honesty: Gate-1 validates a shallow write; depth is a G3 expansion
Gate-1 targets (adminroute create / adminbasic addContact) are **shallow CRUD** — they validate mechanism
*soundness*, **not transactional depth** (R2 R4; README §8.5.3: count ≠ depth). The depth story (rich sagas,
genuine missing-compensation opportunities) requires **naming a concrete TrainTicket saga site**
(order/booking/payment flow) — that is pre-specified and run at **G3 across ≥2 SUTs**, not faked at Gate 1.
Gate 1 does not claim saga coverage.

**Construct-validity threat (disclose, R3 #3):** the read-back is a **single black-box GET = one projection,
not a cross-service snapshot**. A write can be lost in store A while a GET hitting store B / a read model
still looks correct (or vice versa). The oracle measures **read-back equivalence to the control**, not
**persisted-state correctness** — state this as the honest ceiling of a black-box oracle; do not call it
"data-correctness" unqualified.

## 4. Gate-1 validation (EXECUTION G1c)
- **Sensitivity (Gate-1 = pure-differential only):** drive B1+B2 over the adminroute + adminbasic triples;
  confirm the **pure-differential** mode FIRES on the constructed lost-write (S2) with the per-run diff as
  evidence (smoke showed this manually; here it's automated). **The gated mode is NOT validated at Gate 1** —
  it needs a real D failure (Toxiproxy), deferred to G3; Gate 1 ships the gated code path but exercises it
  only once the Toxiproxy backend exists.
- **Specificity:** run the benign-trap stratum (B2.4, incl. the broker-async path); confirm B2 does NOT fire
  after quiescence; record FP **and quiescence-gate coverage per-SUT**.
- **Gate-1 verdict:** PASS = fires on the constructed case + measured-low, characterized FP (with a
  non-trivial non-timeout-gated fraction) ⇒ proceed to G2/G3. FAIL ⇒ mechanism unsound ⇒ README §9.
  Record results next to `prep/gate1-smoke-result.md`.

## 5. Sequencing & per-step verification gates
```
P1 flags/branch        → verify: flags OFF == today's behavior (diff test)
P2 triple registry     → verify: config loads, no logic touched
B1.1 SutFlagInjector   → verify: inject/clear toggles the -D flag + rollout (re-run smoke via injector)
B1.2 input reuse       → verify: fault run uses a pool-verified valid input
B1.3 pairing executor  → verify: control persists, fault masks (smoke parity, automated)
B2.1 read-back capture → verify: fault read-back lacks its own X; control read-back has its X (per-run, not list-eq)
B2.2 isolation         → verify: fresh non-shared entity; runs never collide
B2.2 quiescence        → verify: poll OR trace-completion; gate-coverage logged per verdict
B2.2 pending-vs-missing→ verify: bounded compensation window distinguishes the two (saga path)
B2.2 normalization     → verify: idempotency key + volatile-field strip before diff
B2.3 fire rule         → verify: pure-differential fires on S2 (per-run X∉read-back); gated path compiles,
                         validated at G3 (needs Toxiproxy S1); two strata reported separately
B2.4 FP measurement    → verify: benign-trap incl. broker-async; FP + quiescence-coverage per-SUT  ← make-or-break
Gate-1 verdict         → PASS/FAIL recorded (non-timeout-gated FP must be characterized-low)
```

## 6. Decisions made deliberately (so they're not re-litigated mid-build)
- **Fire rule = two modes, headline is pure-differential** (this review's call): pure-differential (no
  D-error gate) is the headline because it (a) covers **S2/skip-persist, which any downstream-error-keyed
  oracle (incl. the naive span-error baseline and our own gated mode) cannot see** — the real, E2-measurable
  delta — and (b) keeps read-back independent of the trace, which §6's de-circularization requires (R2 §2a).
  Do NOT phrase (a) as "Cast can't reach it" — unverified (§0 fact 6). The gated mode (observed D-error) is a
  lower-FP S1 stratum, reported **separately**, never pooled (R3 #1). Cost owned: pure-differential puts the
  entire FP burden on the B2.2 protocol → B2.4's measured FP is make-or-break.
- **This two-mode fire SUPERSEDES the single rule in README §4 / research/03 §4** (both bundle the D-error
  conjunct = gated-only, S1). When B2 lands, propagate the split back into README §4 + EXECUTION G1b so there
  is one source of truth (the original formulation silently covered only S1).
- **Flag via `-D`, never env** (smoke fact #4).
- **SUT-flag injector first, Toxiproxy later** — smoke proved the SUT flag is the cleanest labeled positive;
  Toxiproxy/real-outage is for the Gate-3 unmodified-system hunt, worded connection-level (R3 #5).
- **Gate-1 picks non-shared-inventory, shallow targets on purpose** — to make black-box isolation hold and
  isolate the mechanism question; shared-inventory isolation (R3 #2) and saga depth (R2 R4) are explicit G3
  expansions, not Gate-1 claims.
- **B2 is an active read-back oracle, not a trace invariant** — owns the GET, lives in the B1 pairing path,
  its own flag; the shipped trace-shape oracle is untouched.
- **The measured, stratified FP number is the deliverable** (§8.5), not the fire demo (smoke already did it).
- **None of B1/B2 makes the A-accept case alone** — Gate 3 (a real, non-injected lost-write / missing-
  compensation defect that assertion tools miss) does. B1/B2 exist to make that hunt possible and credible.

## 7. What is explicitly OUT of this plan (deferred, not forgotten)
- **B4 — independent-label harness** (README §5, P1): the blind, spec/doc-derived genuine-vs-benign labeling
  that de-circularizes the *masking-precision* study (R2 R1). It is an **eval-phase** build, not Gate-1 — B2
  itself supplies the independent GT for the *injected* strata (README §6). Deferred to the G2/eval stage.
- No statistical invariant mining (research/01 #3, README B5) — BLOCKED-by-data (n=1 corpus); near-twin MINES
  exists; orthogonal to B2.
- No param-level attribution (research/01 §C) — instrumentation-bound, not load-bearing.
- No changes to the shipped trace-shape oracle / Sniper / Root-API mode — untouched.
- **Cast/Filibuster assertion-based comparator** (README §6, G2): the load-bearing novelty defense and the
  hardest to make *fair* on OSS SUTs (a fair Cast wants production replay + Java AOP + historical baselines,
  exactly what OSS lacks — R1 MAJOR 4). Deferred to G2, flagged here as the single most fragile plank: budget
  real effort, since a weak baseline invites "you beat a crippled comparator."
