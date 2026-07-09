# MIST UX design — how the main contribution reaches a normal user (pre-step-2 gate)

**Why (user directive 2026-07-08):** everything we benchmark is part of the MIST tool; a normal user
runs MIST and reads an **Allure report**. Before step 2 we must (a) pin what the user provides vs what
is automatic, (b) design how the main contribution (acked-but-lost detection) SURFACES, and (c) fix
the gap. **Design doc → ≥3-cold-review → implement → demo-run DoD → then step 2**
(`step2-execution-checklist.md` §1.9).

## §1 Current user journey — code-cited facts
1. **Invocation:** `java -jar mist.jar <config.properties>` → `MistMain` (loads properties, builds
   MstConfig + Inputs) → `MistRunner` (generation → optional execution → Allure;
   `MistRunResult` carries the report dir + run id). Bundled demos:
   `mist-cli/src/main/resources/My-Example/trainticket-demo.properties` (`allure.report=true`).
2. **What exists per oracle today:**
   | oracle | wired how | user-visible where |
   |---|---|---|
   | status-code / schema (RESTest lineage) | generated assertions | test failures → Allure ✅ |
   | **trace-shape / hidden-downstream** | generated per-test block | **good UX already**: "Trace Shape Oracle Verdict" JSON attachment; `Allure.step("❌ shape violation…")`; a titled "🕳️ HIDDEN DOWNSTREAM FAILURE" attachment with a Jaeger deep-link, plain-language WHAT-THE-CLIENT-SAW vs WHAT-ACTUALLY-HAPPENED, and a filterable label (`MultiServiceRESTAssuredWriter.java:770-800`) |
   | **B2 data-integrity (THE main contribution)** | `mst.oracle.dataintegrity.enabled=true` + a per-SUT `target-triples.yaml` (`MistRunner.java:103,328`); writer emits beforeWrite/afterWrite hooks ONLY for triple-matched steps (flag-off = byte-identical codegen) | **INVISIBLE**: `DataIntegrityRuntime` "Hooks never throw" (`:31`); verdicts live on an in-process record holder + the JSON pairing report consumed by the EVALUATION harnesses (`PairedFaultExecutor`). **No test failure, no Allure step, no attachment, no category. The bundled demo properties don't even carry the B2 keys.** |
3. **What the user must provide vs automatic (today):**
   | input | who authors it | notes |
   |---|---|---|
   | OpenAPI spec + base URL + auth (properties) | user | standard for the tool family |
   | `target-triples.yaml` (write endpoint, dependency, read-back GET, isolation key/strategy, readback_mode, value_probe, optional fault_flag — `TargetTripleRegistry`) | **user, entirely by hand** | we hand-authored TT + SS; nothing proposes them |
   | input-fetch registry / root-api registry | **learned by the tool** (persisted YAML/JSON across runs; pre-seeding optional) | not a UX burden |
   | `fault_flag`, paired control/fault legs, injectors | **evaluation-only** (G1/G3 harnesses) | must NEVER be presented as the product path |

**The gap, in one sentence:** the paper's headline oracle is configuration-gated behind a hand-written
YAML nobody is told to write, and even when enabled its verdicts never reach the report the user reads.

## §2 The product identity (design stance)
- **Default product mode = OBSERVE (wild, single-leg):** MIST generates + executes its workflows as
  usual; for each triple-matched write it captures the ack, polls the read-back, and renders a verdict
  — **no fault injection in the product path** (identical to C3's single-leg wild detector; the paired
  executor stays an eval-harness behind its existing flag).
- **Precision-first firing rule (G1 discipline, pre-registered):** only **OBSERVED_COMPLETE_ABSENT**
  (acked + observation-gated absent) is a defect verdict. TIMEOUT_ABSENT is *unconfirmed*, never a
  failure. This is the same rule the FP-0.0 result was measured under — the product inherits a
  calibrated precision story, and C3's S2-FP calibration covers exactly this mode.

## §3 Design decisions
**D1 — verdict → Allure bridge + failure semantics (closes the visibility gap).**
- At a defined end-of-write check point in the generated test (NOT inside the hooks — "hooks never
  throw" stays for internal errors): map the record verdict:
  - `OBSERVED_COMPLETE_ABSENT` → **the test FAILS** (configurable `mst.oracle.dataintegrity.failOnLost`,
    default true) with a titled attachment **"💧 ACKED-BUT-LOST WRITE"** mirroring the proven
    hidden-downstream pattern: WHAT THE CLIENT SAW (2xx ack + correlator) / WHAT THE READ-BACK SHOWS
    (poll timeline, decisive 2xx read, absent key/value) / Jaeger deep-link / isolation key + freshness.
  - `PRESENT` → green `Allure.step("✅ durable write confirmed (…ms)")`.
  - `TIMEOUT_ABSENT` → non-failing warning step + attachment ("⏳ persistence unconfirmed —
    timeout-gated; not counted as a defect").
  - `NOT_EVALUABLE` / internal error records → info step (reason string).
- Ship an Allure **categories.json** into the report dir: "Data integrity — acked-but-lost write" /
  "Persistence unconfirmed (timeout-gated)" / "Hidden downstream failure (trace)" / standard failures.
- **Run-level summary attachment** (overview): N triple-matched writes · confirmed · LOST · unconfirmed
  · not-evaluable (+ pointer to the JSON report it already writes).
**D2 — triples UX: propose → confirm (kills the cold-start wall, keeps the strict registry).**
- New generation-time step (and/or `--propose-triples`): scan the OpenAPI spec with CRUD heuristics —
  for each 2xx write (POST/PUT), find the sibling read (`GET /res`, `GET /res/{id}`), pick an
  id-like isolation key from the request/response schema → emit `proposed-triples.yaml` with a
  confidence tier + explicit TODO markers; the user reviews/renames and passes it as the registry.
  The STRICT loader (`TargetTripleRegistry`) is unchanged — proposals are input to the human, never
  silently trusted.
- **Expert modes stay manual BY DESIGN** (value-delta probes, supplied-isolation for bodyless writes
  — the TT cancel class): not heuristically proposable; documented as the expert tier. This is the
  same expressiveness the paper prices as depth — disclosed, not hidden.
**D3 — mode separation made explicit:** `mst.oracle.dataintegrity.mode=observe` (default; product)
vs the eval-only paired path (existing flags; harness-only). Docs state the product never injects.
**D4 — out-of-the-box demo + docs:** the bundled TrainTicket demo properties gain the B2 keys wired
to the bundled `trainticket/target-triples.yaml`, so the FIRST run a new user does already shows the
data-integrity section. A "Data-integrity oracle" README section: what to provide (spec, auth,
triples-or-proposal), what's automatic (registries, polling, gating), how to read the report (the
three verdict tiers), and the timeout semantics.
**D5 — authoring-cost symmetry (paper honesty, feeds E2):** from step 2 on, record per SUT: # triples,
authoring minutes (proposed-accepted vs hand-written vs expert), proposal acceptance rate. Reported
NEXT TO arm-3's per-endpoint Tracetest authoring cost. If our cost is nontrivial, we say so — the
differentiator is what the config BUYS (state-level verdicts), not that it is free.

## §4 Work items (implementation wave, after this doc's review)
| # | item | tests/DoD |
|---|---|---|
| W1 | end-of-write verdict check + failure semantics in the writer's generated code (failOnLost, default true; hooks untouched) | emission unit tests (verdict→fail/step matrix); flag-off byte-identical preserved |
| W2 | "💧 ACKED-BUT-LOST WRITE" attachment + steps + categories.json + run-summary | golden-file emission tests; manual Allure inspection on the demo |
| W3 | triples proposal generator (spec-scan heuristics, confidence, TODOs) | unit tests on TT + SS specs: proposes the known-good triples for body-carrying CRUD; proposes NOTHING for the cancel class (expert tier) |
| W4 | `mode=observe` default + docs + demo properties w/ B2 keys | demo-run DoD: normal `java -jar mist.jar trainticket-demo.properties` → Allure shows the section (checklist §1.9.3) |
| W5 | authoring-cost capture template (per-SUT enablement record) | filled for TeaStore + OTel-Demo during step 2.75 |
Constraint: all on `main_track`; suites stay green; codegen with triples-off stays byte-identical
(the existing pin); no behavior change to the eval harnesses (G1/G3 reproducibility).

## §5 What this does NOT change
The frozen C2 schema/floors (`c2-freeze.md` rev 2) — untouched; the benchmark consumes verdicts via
run records/JSON exactly as before (the Allure bridge is additive). The eval harnesses and their
reviewed results — untouched. The paired executor stays behind its flag. plan v2 budgets absorb the
wave as step 1.9 (~2–4 d implementation) — the elapsed cost is disclosed if it slips the timeline.

## §6 Questions for the 3-cold-review
1. Is the D1 failure semantics right (fail on OBSERVED_COMPLETE_ABSENT only; TIMEOUT_ABSENT warns)?
   Or should strictness be a 3-level knob (off/warn/fail) for CI users?
2. Does D2's proposal heuristic risk luring users into WRONG triples (a read-back that isn't the
   system of record → false LOST verdicts)? What guardrails (confidence floor, verify-at-first-run
   probe: control write must land before the triple is trusted)?
3. Is the observe-mode identity consistent with every claim doc (no over-claim that the product
   "injects faults")? 4. Is the demo-properties change safe for existing users? 5. Anything in W1–W5
   that threatens the frozen eval reproducibility?
