# Tool execution plan — B1 (fault-injection mode) + B2 (differential data-integrity oracle)

> **This plan TOUCHES MIST tool code → executing it requires the user's explicit "yes".** It is the
> step-by-step, evidence-anchored sequence to build B1+B2 toward Gate 1, refined by the live Gate-1 smoke
> result **and a multi-round self-review against README §3/§4, EXECUTION G1a/b/c, all three Round-2 reviewers
> (R1 novelty, R2 evaluation, R3 soundness), plus engineering-feasibility & internal-consistency passes, then
> THREE independent cold-reviewer subagents (no shared context): the first (on v3) confirmed no MAJOR; a second
> pair (on v4) EACH found one real MAJOR the first missed — the writer seam is a code-GENERATION site, not
> runtime plumbing; and the make-or-break async-FP Gate-1 PASS criterion was untrustworthy — plus drift two
> reviewers flagged together. All folded into v5 (seam line-numbers are real; their semantics are now fixed)**.
> Every step cites a real code seam (from
> `research/01-feasibility-codebase.md`, audited 2026-06-30 with `file:line`; **re-verify the exact line at
> edit time**) and/or the smoke evidence (`prep/gate1-smoke-result.md`) and/or a reviewer concern. All MIST
> changes go on branch `main_track`. **Build additively — with every flag OFF, behavior is byte-for-byte
> unchanged.** v8 (six review rounds; round-5 on v6 AND round-6 on v7 EACH returned **3/3 cold-reviewer OVERALL
> SATISFIED** across feasibility+prep / novelty+contribution / design-logic, with **zero BLOCKING findings** on
> v7. Round-6's non-blocking polish is cleaned here: the last per-entity A-error residue (a seed-case note),
> the §8 benchmark-count arithmetic, the ≤5% sync-FP bar propagated cross-doc, the state-channel made precise
> (sibling static holder), same-JVM wording, and stale version stamps. The plan is executable and
> reviewer-cleared; **execution of B1/B2 remains BLOCKED until the user says "yes."**), 2026-06-30.

## 0. Facts the smoke + review established (this plan's footing — not assumptions)
1. On a real, fully-deployed TrainTicket, the SUT-side `LOST_WRITE` makes `POST /adminroute` return **HTTP
   200 / `status:1`** while the route is **never persisted** (getAllRoutes count unchanged). Control persists;
   fault loses it. → The differential read-back signal B2 targets is **real**.
2. **All response-level oracles pass the fault run** (status 200, schema ok, body `status:1`). Only the
   control-vs-fault **read-back** distinguishes them. → B2's value is exactly read-back, not the response.
   (**Naming precision, cold-review F-MINOR-2:** the FIRE is a *per-run metamorphic* relation — 2xx-acks-X ⟹
   X ∈ its OWN read-back — plus a control *liveness* gate; "differential" names the control-vs-fault pairing
   that supplies that liveness gate + labels, **NOT** a same-input A/B state-diff. Keep the name, read it this
   way — see B2.3.)
3. **read-back is a black-box GET**, so its signal is **independent of the trace signal floor** (research/01
   §D: status+topology+otel/response_flags) — **the pure-differential mode's signal** does **not** degrade on
   Envoy-only SUTs where the trace carries less (the **gated** mode, keying on an observed D-error, DOES ride
   the trace floor — so this independence is the pure mode's, not "B2's"). **BUT applicability is a separate axis:** B2 applies **only** to write-path SUTs with a clean
   black-box read-back (README §4 item 6 — TrainTicket/TeaStore/Sock Shop/petclinic), **not** read-only/derived
   demos (Bookinfo, Online Boutique). Do not conflate "signal-floor-independent" with "runs on any Envoy SUT."
4. The opt-in fault flag **must use a JVM `-D` system property** (`JAVA_TOOL_OPTIONS=-Dmist.fault....=true`),
   NOT env relaxed-binding (which silently failed on TT's Spring-Cloud+nacos bootstrap). Load-bearing for B1.
5. SUT = `train-ticket-injection@MIST-trainticket`, deployed the team's proven way
   (`evaluation/suts/trainticket/deploy/deploy.sh` = minikube + make build/deploy). Target triples:
   adminroute (getAllRoutes collection read-back) and **adminbasic/contacts (ALSO collection read-back =
   `getAllContacts`)**. **Correction (cold-review A, spec-verified):** adminbasic has **NO per-entity
   `GET /contacts/{id}`** — the spec exposes only `delete` on `/contacts/{contactsId}` plus the collection GET;
   the earlier "fresh-UUID per-entity read-back / clean FP target" claim was a **factual error**. Both targets
   are non-shared-inventory appends; FP is measured by **business-key membership in the collection**, not a
   per-entity GET. (A real per-entity GET exists only on `ts-contacts-service`, which has no LOST_WRITE
   injector — see §3 B2.3.)
6. **Two defect shapes the oracle must cover** (decided in §3 B2.3 fire rule):
   - **S1 — swallowed downstream error:** D is called → D errors → error swallowed → 2xx. **D span errored.**
   - **S2 — skipped persist:** D is never called (logic bug / wrong branch / forgot to save) → 2xx. **No D
     span.** Our smoke is S2.
   The point that matters: a read-back differential keys on **persisted state, not on a downstream-error
   signal**, so it covers S1 and S2 *alike*. Any **downstream-error-keyed** oracle (the naive span-error
   baseline, MIST's own gated mode in B2.3) has **no signal on S2** — that is the real, **E2-measurable**
   delta, **over those weak baselines** (the load-bearing Cast/Filibuster comparison is the deferred G2 item, §7). **Do NOT overclaim this against Cast:** Cast's *injection*
   sits at the DB call (S1-style) and its *oracle* is metric-threshold/assertion-point — a different,
   stronger-assumption mechanism; whether Cast's assertion points catch a *naturally-occurring* S2 is
   **unverified**, so claim only "different / weaker-assumption mechanism," never "Cast structurally cannot
   reach S2." (Honesty basis = REVIEW2-R1, which flags manufactured deltas; research/03 §4.3 already concedes
   the read-back diff "automates an assertion, not a new analysis.")
   **S2 is only producible invasively (disclose):** S2 = "D never called" is an *application-logic* fault; a
   TCP-level proxy (Toxiproxy) can only perturb calls that ARE made → it yields **S1, never S2**. So the
   pure-differential mode's S2-distinguishing power is demonstrable only on a **source-injected** SUT (the
   Gate-1 `LOST_WRITE` flag) or a **real wild S2** (the uncertain G3 bet) — **never on the black-box Toxiproxy
   path**, which exercises only the gated/S1 mode. Do not imply the black-box path evidences S2.

## 1. Pre-flight (no-regret; do first, no behavior change)
- **P1. Branch + flags.** Confirm on `main_track`. Reserve two OFF-by-default keys, each mirroring the
  *correct* shipped pattern (verified, cold-review): **B1** = `mist.fault.injection.enabled`, a **system
  property** in the `mist.fault.*` namespace mirroring `FaultMiner.ENABLED_PROPERTY` (`FaultMiner.java:54`,
  read via `System.getProperty` — it is **NOT** in `MstConfig`). **B2** = `mst.oracle.dataintegrity.enabled`,
  an opt-in `parseBool` field in **`MstConfig.Oracle`** (`MstConfig.java:403-417`, namespace `mst.oracle.*` —
  note **`mst`**, not `mist`), mirroring the default-false `hidden_downstream_failure.enabled` (`:415-416` —
  which actually sits at `mst.oracle.shape.invariants.*`; place the new key as a **sibling at a different
  depth**, `mst.oracle.dataintegrity.enabled`, reusing the parseBool/default-false/system-property *pattern*,
  not the exact path — cold-review D-m1).
  *Verify:* with both off, a normal run diffs zero against today.
  **✅ DONE 2026-07-01** — landed on `main_track`: `MstConfig.Oracle.dataIntegrityOracleEnabled` (parseBool,
  default false), both keys whitelisted in `MstConfigValidator.KNOWN_KEYS`, pinned by
  `MstConfigDataIntegrityTest` (default-off / opt-in / strict-validator survival). 338 tests green
  (335 baseline + 3 new); neither flag has a consumer yet ⇒ flags-off behavior byte-identical by construction.
- **P2. Target-triple registry.** Add a small per-SUT config (reuse the bundle's `real-system-conf.yaml`
  style) listing `{write_endpoint, dependency, readback_endpoint, isolation_key}` — exactly the
  `prep/target-triples.md` triples. The `dependency` must be a **trace-matchable service/operation key** (the
  gated mode in B2.3 needs to locate D's span). No logic yet; data only.
  **✅ DONE 2026-07-01** — `mist-cli/src/main/resources/My-Example/trainticket/target-triples.yaml` (both
  Gate-1 triples; business keys spec-verified: startStation+endStation / accountId+documentNumber; `dependency`
  = persisting service ts-route-service / ts-contacts-service, deliberately distinct from the injector
  deployment) + strict loader `io.mist.cli.fault.TargetTripleRegistry` + 6 tests (`TargetTripleRegistryTest`).
  All green; nothing consumes the loader yet.
- **P3. Async benign-trap prerequisite (make-or-break — schedule before B2.4, do NOT discover mid-Gate-1).**
  The read-back FP probe (B2.4) needs a **broker-mediated** async write path, not a synchronous sleep.
  **UNVERIFIED whether TrainTicket exposes a clean black-box async write+read-back without new SUT work** —
  resolve FIRST: name the concrete rabbitmq-backed write endpoint(s) (e.g. order/cancel or notification flows)
  and decide *existing path* vs a *new SUT-side async injector*. This gates B2.4 (cold-review MODERATE #3).
  **✅ RESOLVED 2026-07-01** — verdict **NEW-INJECTOR-NEEDED** (`prep/p3-async-path-resolution.md`, read-only
  source research on `MIST-trainticket@bbf3d6ae`): NO existing 2xx-ack-before-persist path with a black-box
  read-back exists — the only live broker edge (food→delivery) persists the observable entity synchronously
  before the 2xx, and the async-persisted `Delivery` row has no HTTP reader anywhere; the email edge's real
  producers are commented out. RabbitMQ itself IS helm-installed by `make deploy`. **Decision: Gate-1 takes
  this plan's pre-decided branch (§3 B2.4 / §4): sync-FP benign traps + the explicit async disclaimer; the
  broker-async trap = the doc's pre-specified Option A injector (~40-line flag-gated async-write trap in
  ts-food-service, `GET /foodservice/orders/{orderId}` read-back), built when G3's async-soundness claim
  needs it.** Gate-1 is NOT hard-blocked (per B2.4).

## 2. B1 — opt-in fault-injection mode (research/01 build-list #4; EXECUTION G1a)
**Goal:** for a target write request, run it once clean (control) and once with the dependency faulted
(fault), reusing realistic inputs. *Effort: L. This converts MIST to an opt-in grey-box controller — gate it
behind the flag and frame it as a mode, or the "no SUT instrumentation" identity breaks (research/01 §4.1).*

- **B1.1 FaultInjector interface** `{ inject(target), clear() }` with swappable backends. Smoke evidence
  says the **cleanest ground-truth backend is the SUT-side flag**, so implement `SutFlagFaultInjector`
  FIRST: `inject` = `kubectl set env deploy/<svc> JAVA_TOOL_OPTIONS=-D<key>=true` + `rollout status`;
  `clear` = unset + `rollout status`. **(Throughput, cold-review D-m2: each toggle = set-env + rollout = tens
  of seconds on minikube; benign-trap FP runs use flag-OFF ⇒ NO per-run toggle, and positives are few — not a
  bottleneck, but BATCH positives to amortize rollouts.)** (Toxiproxy backend deferred to Gate-3
  unmodified-system per EXECUTION G0. **When built, word it as connection/TCP-level faults, not per-D-span DB aborts** — Toxiproxy is not
  protocol-aware (R3 concern #5).)
  **Identity note (disclose, or an evaluation reviewer reads "you modified the SUT" as breaking black-box):**
  the SUT-flag injector is **scaffolding to manufacture labeled ground truth, not a tool dependency** — MIST
  itself needs no SUT change. The black-box / no-SUT-change identity is carried by the Toxiproxy
  (Gate-3 unmodified-system) path; Gate-1's SUT flag only buys clean labels. State this wherever Gate-1
  results appear.
- **B1.2 Realistic input reuse.** Drive the request from the existing two-phase verified-input pool
  (`MistRunner.java:502-557`, verified-valid *values* harvested in Phase A) so the only **behavioral**
  abnormality is the injected fault — the isolation key is **freshly varied by design** (B2.2), so control and
  fault are deliberately not byte-identical inputs.
- **B1.3 Control/fault pairing executor.** New orchestration: for target request R, execute control (no
  inject) then fault (inject → run → clear). Separate code path entered only when `mist.fault.injection.enabled`.
- **B1.4 Acceptance.** Fault run shows the masked write (smoke: `status:1` + `data:null` + getAllRoutes
  unchanged); control run persists; flag OFF ⇒ no new code path runs. *Verify by re-running the exact smoke
  scenario through MIST instead of curl.*

## 3. B2 — differential data-integrity oracle + soundness protocol (EXECUTION G1b; the real work)
**Architecture (decided — corrected after cold review found the seam mis-located).** MIST is
**generate-the-test-then-run-it** (`MistRunner.java:506 executeGeneratedTestsWithJUnit`); the writer *emits a
standalone RestAssured/JUnit test as a String* — `MultiServiceRESTAssuredWriter.java:706-707` and `:714` are
`pw.println(...)` that **emit** the response-staple and the per-run `oracle.evaluate(model,…)` *into the
generated test*, which then runs **in-process under `JUnitCore` (same JVM — the generated-test execution
layer, NOT a separate process; this is exactly what lets the orchestration layer read the emitted state,
below)**. So B2 spans **two layers that v4 wrongly conflated:**
- **Codegen layer (inside the generated test, the `:706-714` region):** emit the **read-back GET** + the
  per-run check `X ∈ its own read-back` as additional generated steps, beside the existing emitted oracle.
  "Reuse `:714`" means the verdict-**emission pattern**, not the single-trace `oracle.evaluate` (which stays
  as-is; B2 is **not** a `ShapeInvariant.evaluate(trace)`, `ShapeInvariant.java:11-21` — that takes one trace,
  no active GET).
- **Orchestration layer (mist-cli, B1.3):** `inject → run generated test → clear` (`kubectl set env` +
  rollout), executed **twice** (control flag-off, fault flag-on); collect each run's emitted read-back state +
  acknowledgement **through the existing in-process channel** — the generated test runs in-process under
  `JUnitCore` (`MistRunner.java:1127`, same JVM), so read-back state crosses back via a **sibling shared static
  holder** (the SAME pattern the writer already uses to surface a verdict — `MultiServiceRESTAssuredWriter.java:715`
  `LAST_VERDICT.set`, a `private static ThreadLocal` at `:252`; B2 adds its OWN holder, read by the
  orchestration thread after `JUnitCore.run()` returns — `MistRunner.java:1158` already treats generated-test
  statics as shared in-process state), **not a new IPC** (cold-review D-M2/G confirmed the enabler); do the
  **pairwise** fire decision (control as FP-guard) + stratified reporting HERE.
A pairwise control-vs-fault verdict **cannot** be surfaced through the per-run `:714` as-is — it lives in the
orchestration layer (cold-review MAJOR: writer = codegen, not runtime plumbing).

**Dual role (why it matters to the contribution, not just a detector):** B2's discriminating signal is
*persisted state* (`S_control` vs `S_fault`), a **different signal class** from the masking oracle's *trace
topology+status*. So B2 is also the **independent ground truth that de-circularizes the cheap masking oracle**
(README §6; R2 §2a confirms this is the core of the de-circularization). Keeping read-back **independent of
the trace** is a *precondition* for that role — which is exactly why the headline fire mode carries no
trace-derived gate (see B2.3).

- **B2.1 Read-back capture.** After each run in the pair, GET the triple's `readback_endpoint` and capture
  `S_control` / `S_fault` (smoke read-back: getAllRoutes JSON).
- **B2.2 Soundness protocol (§4 — the contribution's spine, not a footnote):**
  - **Isolation:** one fresh unique entity per test, keyed by a **request-supplied business key** (adminroute:
    station-pair; adminbasic/contacts: accountId+documentNumber) — NOT a per-entity GET (adminbasic has none;
    §0 fact 5). Read-back = membership of that business key in the collection. Gate-1 deliberately picks
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
    (README §4 item 3). **Shared failure mode (disclose — cold-review C-M3):** black-box, this distinction
    relies on the SAME async span-completion visibility quiescence needs — which R3 §5.1 shows is unobservable
    across brokers (span *links*, often absent). So in the async regime pending-vs-missing **IS** the same
    wall-clock race as quiescence, not an independent guarantee; compensation verdicts reached by timeout are
    reported in the same **lower-confidence, timeout-gated stratum**. **FN direction (cold-review F-MOD-2):**
    black-box within the window, a *truly-missing* compensation is indistinguishable from *not-yet-arrived* → the
    oracle can **silently MISS the very missing_compensation class it advertises** (a recall/FN hole, not only
    an FP/confidence label); this async *sensitivity* gap is bounded globally to G3 (§3.5), stated here so the
    DoD is not misread as covering compensation recall. **Code-built at Gate-1 but VALIDATED AT G3** against the
    named saga site — Gate-1's shallow CRUD targets have no compensation flow to exercise it (its Gate-1 verify
    row is reduced to a **compile-only check** in §5, not a fire/FP test — cold-review MODERATE #2).
  - **Normalization:** idempotency keys; strip volatile fields (timestamps, server-generated ids) before diff.
- **B2.3 Fire rule — a bounded, FP-measured differential relation (per-run), in TWO modes reported separately.**
  (Not called an "invariant": R3's standing point is "a race, not an invariant" — the per-run framing kills
  *cross-run* contamination but not the *temporal* race, which only B2.2 + the measured FP bound.)
  Base relation (both modes): a run whose client response is **2xx/"success" and that acknowledges entity X**
  must have **X present/correct on its OWN read-back**; fire when that is violated. *Diff each run against its
  own acknowledgement — NOT one collection against the other.* (For collection read-backs like getAllRoutes,
  check "X ∈ read-back," not list-equality — the control run legitimately adds its own X.)
  **What the control run actually rules out (rescoped — cold-review C-M1):** it rules out **systemic /
  environmental** FP (if the SUT/DB/auth/harness were down, the control's X would be absent too). It does NOT
  prove the *fault* input "would persist under no fault" — control & fault deliberately use different isolation
  keys (B1.2). **Input validity is carried by the two-phase pool** (an input that reached 2xx = verified-
  ACCEPTED, `MistRunner.java:495-499`, not verified-*persisted*) **+ isolation uniqueness.** The residual FP
  class "a fresh valid write that returns 2xx but legitimately does not persist" is bounded ONLY by the
  measured benign-trap FP (B2.4) — so the benign-trap stratum must include such cases.
  **X is request-derived** (a business key sent IN the request — adminroute: station-pair; adminbasic:
  accountId+documentNumber), **never read from the fault response** (S2 returns `data:null`, so the fault run
  never learns a server-assigned id). **Neither Gate-1 target has a per-entity GET** (§0 fact 5), so read-back =
  **business-key membership in the collection** for both; the earlier "fresh-UUID per-entity" cleanliness claim
  is dropped (cold-review A). A real per-entity-GET target exists only on `ts-contacts-service`
  (`/contactservice/contacts/{id}`), which has **no LOST_WRITE injector** — using it as a Gate-1 positive is
  extra SUT work, deferred.
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
  - Build a **benign-trap stratum** the oracle must NOT fire on: eventually-consistent-then-correct writes
    **AND the `2xx-accepted-but-by-design-never-persists` sub-class (accept-then-drop — e.g. a write silently
    dropped by a documented filter/quota/dedup; NOT "idempotent no-op," which under B2.2's fresh business keys
    leaves X *present* = a true negative, cold-review I) — the exact residual FP class B2.3's control-rescoping
    delegates here, so the trap MUST operationalize it, not just delayed-persistence (cold-review F-MOD-1).**
    **It MUST include a broker-mediated async write path** (TrainTicket's rabbitmq paths), **not just a
    synchronous `DELAYED_WRITE` sleep** — because R3's real soundness fear is the async regime where OTel
    yields span *links*, producer/consumer spans need not co-occur in one trace, and trace-driven quiescence
    silently degrades to a wall-clock timeout (R3 #1/#4). A synchronous sleep only tests "did poll wait long
    enough"; it does **not** exercise the degradation R3 names. (Prerequisite = **P3**: resolve the concrete
    rabbitmq path vs a new SUT-side async injector there, before this step.) **The broker-async trap is required
    only for the async *soundness claim*; if no clean async path exists (P3 negative), Gate-1 still PASSES on
    sync FP with the §4 async disclaimer — P3-UNVERIFIED does NOT hard-block Gate-1** (async-FP deferred to G3).
  - Report measured FP/FN **per-SUT** + **quiescence-gate coverage per-SUT**; state explicitly that FP on a
    *constructed* benign-trap is a **lower bound** on wild-async FP (R3 #4).
  - **Gate-1 pass = fires on the constructed lost-write + low/characterized FP (especially in the
    non-timeout-gated stratum);** uncharacterized FP sinks the contribution.

## 3.5 Scope honesty: Gate-1 validates a shallow write; depth is a G3 expansion
Gate-1 targets (adminroute create / adminbasic addContact) are **shallow CRUD** — they validate mechanism
*soundness*, **not transactional depth** (R2 R4; README §8.5 item 3: count ≠ depth). The depth story (rich sagas,
genuine missing-compensation opportunities) requires **naming a concrete TrainTicket saga site**
(order/booking/payment flow) — that is pre-specified and run at **G3 across ≥2 SUTs**, not faked at Gate 1.
Gate 1 does not claim saga coverage.

**Construct-validity threat (disclose, R3 #3):** the read-back is a **single black-box GET = one projection,
not a cross-service snapshot**. A write can be lost in store A while a GET hitting store B / a read model
still looks correct (or vice versa). The oracle measures **read-back equivalence to the control**, not
**persisted-state correctness** — state this as the honest ceiling of a black-box oracle; do not call it
"data-correctness" unqualified.

**Gate-1 evidences SOUNDNESS ONLY — essentially ZERO novelty evidence (disclose bluntly, cold-review B).**
Gate-1 hand-targets a known endpoint, reuses one verified input, and fires on a **self-injected** fault beaten
only against **weak baselines** (naive span-error + MIST's own gated mode). As a *novelty* signal that is ~zero
(beating a strawman on a planted fault). Gate-1 validates the read-back oracle's **soundness**, **NOT** (a) the
generation-driven path-discovery delta vs Cast (README §2 axis 1; "argued not measured," R2 R5), nor (b) any
research-novelty claim. **ALL novelty evidence is back-loaded to G2 (a fair Cast/Filibuster comparator) + G3
(real bugs the comparator misses).** Also: Gate-1 tests async *specificity* (B2.4) but not async *sensitivity*
(a lost-write on an async path) — the latter is a G3 item.

## 4. Gate-1 validation (EXECUTION G1c)
- **Sensitivity (Gate-1 = pure-differential only):** drive B1+B2 over the adminroute + adminbasic triples;
  confirm the **pure-differential** mode FIRES on the constructed lost-write (S2) with the per-run diff as
  evidence. **Only adminroute is live-smoke-demonstrated** (`prep/gate1-smoke-result.md`); **adminbasic is
  build-verified only — its read-back smoke is a G0 step** (`prep/sut-fault-injection-capability.md` §9), so do
  NOT read "smoke showed this" as covering both targets (cold-review D-M1). Here both are automated. **The
  gated mode is NOT validated at Gate 1** — it needs a real D failure (Toxiproxy), deferred to G3; Gate 1 ships
  the gated code path but exercises it only once the Toxiproxy backend exists.
- **Specificity:** run the benign-trap stratum (B2.4, incl. the broker-async path); confirm B2 does NOT fire
  after quiescence; record FP **+ quiescence-gate coverage per-SUT AND per-stratum (sync vs async)**.
- **Gate-1 verdict (async-FP criterion tightened — cold-review MAJOR):** the broker-async trap's verdicts are
  expected to be **largely timeout-gated** (OTel emits span *links*, not parent-child edges, so trace-driven
  quiescence degrades to wall-clock on async paths — R3 §5.1), and timeout-gated FP is a **function of the
  wall-clock timeout** (lengthen it → FP drops trivially). So: **pre-register the quiescence/compensation
  timeout independent of the trap; report async FP as a CURVE over that timeout, not a point; report the
  non-timeout-gated fraction per stratum INCLUDING async.** PASS = fires-on-the-constructed-case AND
  characterized-low **sync** FP (**pre-registered NUMERIC bar: ≤5% non-timeout-gated sync FP — cold-review
  F-MINOR-1/I; "characterized" is falsifiable, "low" must be this pinned number, fixed at pre-registration**)
  AND **either** a
  non-trivial *observed-gated* async fraction **or** an explicit
  disclaimer that Gate-1's async-FP is a low-confidence lower bound making **no** async-soundness claim
  (trustworthy async-FP deferred to G3 with instrumented broker context). A green FP that is ~100%
  timeout-gated on the async path does **NOT** pass the async axis (R3 §4: "90% timeout-gated would not
  convince me"). FAIL ⇒ mechanism unsound ⇒ README §9. Record next to `prep/gate1-smoke-result.md`.
  **Blind spot of the anti-gaming lever (cold-review F-MOD-3):** the *observed-gated fraction* presumes a
  **complete** expected-dependency set (the P2 registry). On multi-hop async where P2 is incomplete (an
  undeclared broker consumer), "all expected spans completed" is **vacuously true** → the observed-gated
  fraction **over-counts in exactly the async regime it must certify**. So the G3 async-sensitivity claim
  REQUIRES per-async-path **P2-completeness validation** (declare every consumer before trusting the fraction);
  Gate-1 (sync-only) is unaffected. **So any Gate-1 async observed-gated fraction is DESCRIPTIVE-ONLY /
  non-load-bearing (cold-review I):** the load-bearing Gate-1 pass is SYNC soundness (fires + ≤5%
  non-timeout-gated sync FP); the async fraction reported at Gate-1 supports **no** async-soundness claim until
  the G3 P2-completeness validation exists.
  **Gate-1 DoD = SYNC-mechanism soundness on one SUT (cold-review C-M2):** a PASS via the async-disclaimer path
  establishes the **sync** regime only; the oracle's advertised **async/CQRS** regime gets ZERO validation
  until G3. Do NOT read a disclaimer-path PASS (or EXECUTION's "sound on one SUT") as *general* soundness.

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
B2.2 pending-vs-missing→ (NOT Gate-1 — no saga target) code compiles; VALIDATED AT G3 vs named saga site
B2.2 normalization     → verify: idempotency key + volatile-field strip before diff
B2.3 fire rule         → verify: pure-differential fires on S2 (per-run X∉read-back); gated path compiles,
                         validated at G3 (needs Toxiproxy S1); two strata reported separately
B2.4 FP measurement    → verify: P3 resolved; FP-vs-timeout curve + gate-coverage per-SUT AND per-stratum  ← make-or-break
Gate-1 verdict         → PASS = fires + ≤5% non-timeout-gated SYNC FP + (observed-gated async fraction OR
                         low-conf async disclaimer; async fraction is descriptive-only); ~100% timeout-gated
                         async does NOT pass the async axis
```

## 6. Decisions made deliberately (so they're not re-litigated mid-build)
- **Fire rule = two modes, headline is pure-differential** (this review's call): pure-differential (no
  D-error gate) is the headline because it (a) covers **S2/skip-persist, which any downstream-error-keyed
  oracle (incl. the naive span-error baseline and our own gated mode) cannot see** — the real, E2-measurable
  delta — and (b) keeps read-back independent of the trace, which §6's de-circularization requires (R2 §2a).
  Do NOT phrase (a) as "Cast can't reach it" — unverified (§0 fact 6). The gated mode (observed D-error) is a
  lower-FP S1 stratum, reported **separately**, never pooled (R3 #1). Cost owned: pure-differential puts the
  entire FP burden on the B2.2 protocol → B2.4's measured FP is make-or-break.
- **This two-mode fire SUPERSEDES the single rule across README §4 / EXECUTION G1a / research/03 §4 /
  `prep/target-triples.md`** (all bundled the D-error conjunct = gated-only, S1). **Propagated (v5):** README §4
  + EXECUTION G1b carry the two-mode rule; **EXECUTION G0/G1a + README §5 B1 patched to SUT-flag-injector-first
  for Gate-1 (S2, no D span), Toxiproxy → G3** — the injector-backend drift TWO reviewers flagged as
  un-propagated; `prep/target-triples.md` line 18 + research/03 §4 left as historical snapshots with a pointer.
  Lesson: spec-propagation must precede code and cover **both** the fire rule AND the injector backend.
- **Flag via `-D`, never env** (smoke fact #4).
- **SUT-flag injector first, Toxiproxy later** — smoke proved the SUT flag is the cleanest labeled positive;
  Toxiproxy/real-outage is for the Gate-3 unmodified-system hunt, worded connection-level (R3 #5).
  **Toxiproxy can only ever validate the gated/S1 mode** (it perturbs calls that are made; S2 = no call), so
  pure-differential's S2 evidence is source-injected at Gate-1 / wild at G3, never on the proxy path; and the
  "no-SUT-change identity" is precisely "no SUT *source* change + an in-path proxy," not literally unmodified.
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
- **Cast/Filibuster assertion-based comparator** (README §6, G2): deferred to G2 but **budgeted as a
  first-class deliverable, NOT "where feasible"** (cold-review B) — it is the ONLY comparator that tests oracle
  *novelty* (naive-span-error / Tracetest-generic / TraceAnomaly are strawman-adjacent or RCA tools, not
  oracle-novelty baselines — research/02 §1d). A fair Cast wants production-replay + Java-AOP + historical
  baselines, exactly what OSS lacks (R1 MAJOR 4); a weak one invites "you beat a crippled comparator." Budget
  the real effort or the headline novelty comparison is contestable.
