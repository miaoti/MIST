# REVIEW — wave-275a-plan.md — Reviewer B (ENGINEERING / ENABLEMENT-DISCIPLINE lens)

**VERDICT: ACCEPT-WITH-FIXES** — the objective is right and the seam can be genuinely small, but two
BLOCKING issues (unresolved execution mode; OTel isolation-key binding-model mismatch) mean the plan
**cannot execute as written** and must be revised + the two BLOCKINGs re-cleared before any code lands.
Everything else is fixable in a rev-2. Not a REJECT: the underlying approach (reuse the existing
override seam, run paired, author triples in YAML) is sound and low-risk once the mode and the OTel key
are pinned.

Reviewed against actual code/assets: `DataIntegrityRuntime.java` (read-back loop L434/L499/L523/L627/
L645/L686; `readbackPath` hard-requires `"GET "`; `extractItems`/`containsKey`/`parsesToCollection`
are JSON-only), `TargetTripleRegistry.java` (strict YAML loader, cross-field guards),
`DataIntegrityObserveCheck.java` (defect tier gating), `PairedFaultExecutor.java`
(`evaluate`/`verdict` — gate-agnostic differential), `CancelRefundHeadToHead` +
`ShippingEnqueueHeadToHead` + `ShippingReadbackHttp` (the real enablement precedent),
`MstAuthHandler.java`, `MistRunner` observe path (L560–640), `mist-cli/pom.xml`, both case JSONs +
the OTel control, the step-2.75 checklist (L216–221), and the test layout
(`ShippingReadbackHttpTest` is the wire-test model).

---

## BLOCKING

### F1 [BLOCKING] — The execution mode (observe vs paired) is unresolved, and the two modes have contradictory requirements. TeaStore *cannot* produce a FLAG in observe mode.
The plan simultaneously asks for (a) "author OpenAPI … one **observe-mode** run whose Allure shows the
data-integrity section" and the DoD "the 1.9 user flow" (§3), and (b) "the same **paired-executor**
mode as TT/SS" (open-q5), and (c) "run MIST observe/paired mode … N≥4 per leg" (§4). These are two
different execution paths with different code, and the plan never commits.

Decisive engineering fact: **observe mode cannot yield a defect for TeaStore.** The observe defect tier
(`DataIntegrityObserveCheck` → `OBSERVED_COMPLETE_ABSENT` → `LOST_MARKER`) is reachable only when
`DataIntegrityRuntime.traceComplete()` is true, which needs `jaeger.base.url` + a real entry trace
(DataIntegrityRuntime L682). TeaStore is **trace-uninstrumented as-deployed** (its own case JSON says
so). So every acked-but-absent TeaStore write stays `TIMEOUT_ABSENT` and the observe check prints
"persistence UNCONFIRMED … NOT counted as a defect" — never the intended FLAG. The paired path
(`PairedFaultExecutor.evaluate`/`verdict`) is **gate-agnostic** (it fires on `control.readbackContainedX
&& !fault.readbackContainedX`, no trace needed — exactly how `CancelRefundHeadToHead` fires with
`traceId=null`). So paired is *required* for TeaStore and *cleaner* for OTel.

The DoD sentence is itself internally inconsistent: "observe-mode run … emits the data-integrity Allure
section **on BOTH legs**" — observe mode is single-leg (`MistRunner.maybeBeginObserve`); "both legs" is
paired vocabulary.

**Fix:** Commit to **paired (g3-style `evaluate`) for both SUTs** as the result-producing path. Then
explicitly re-scope the checklist's "observe-mode Allure section" DoD: either (i) demote it to a
separate lightweight observe smoke on an already-acking read endpoint purely to render the Allure
section, or (ii) rewrite the DoD as "paired run emits the data-integrity verdict cell on both legs."
Do not leave "observe/paired" as an either/or in an execution wave — it changes what code you write.

### F2 [BLOCKING] — OTel's captured read-back keys on a **server-assigned** `order_id` read from the ack; MIST's isolation model forbids exactly that. The "count>0 ⇒ PRESENT" binding is unspecified and likely unsound as stated.
Plan §2.1: "SqlReadbackProbe … membership verdict = `count > 0 ⇒ PRESENT`". The captured locator
(case JSON) is `SELECT count(*) FROM accounting."order" WHERE order_id='<the acked orderId>'` — the
`order_id` comes from the **checkout response**. But `DataIntegrityRuntime` is built on the opposite
invariant: the class doc states *"X is request-derived — never read from the response,"* `freshen`
strips server-assigned `id`, and membership matches a **freshened or supplied request key**, not a
response value. So:
- You cannot key membership on the ack's `order_id`.
- A **scalar count** cannot be post-filtered by the oracle's `containsKey` (which needs a *collection of
  rows* to match a key against). To reuse the reviewed membership logic you must return a **row set**
  (`SELECT <req-derived-key> FROM accounting."order" …` → `[{"key":"…"}]`), which then interacts with
  `readback_bound` truncation on a non-trivial table.
- The alternative is `SUPPLIED` + `VALUE_DELTA` on a per-key count (control 0→1, fault stays 0), which is
  how the g3 cases actually bind async/aggregate writes (userId+balance; queue-name+depth). That needs a
  **request-derivable column** in `accounting."order"` (session uuid / email marker — the stimulus
  carries both) and a **stable pre-write baseline** (`beforeWriteSupplied` requires two agreeing reads;
  DataIntegrityRuntime L522–539), which a busy table won't give.

The plan calls OTel "the right case to prove the seam" because the psql read-back is "clean and
deterministic." The *transport* is clean; the *binding to MIST's isolation model* is the unsolved part,
and it is unsolved specifically for the pilot.

**Fix (gating for the OTel pilot):** Before coding, pin exactly — (1) the isolation strategy
(`SUPPLIED` value-delta-on-count keyed by a request-carried column, or membership on a freshenable
column), (2) the **request-derived** SQL locator (NOT the ack's `order_id`), (3) a live check that the
chosen column actually exists in `accounting."order"`, and (4) a disclosed deviation note (the run's
locator differs from the captured `order_id` locator — parity preserved on the durable *fact*, not the
key). Note the irony for sequencing: **TeaStore's key is cleaner for MIST's model** (its `firstname`
marker is request-supplied), so "derisk on OTel" is misjudged on the binding axis (see F.seq).

---

## MAJOR

### F3 [MAJOR] — The proposed `ReadbackProbe` seam is a bigger, riskier change than needed and contradicts the plan's own minimum-change principle. The existing `installHttpOverride(Http)` already binds both modalities.
Production already has a non-HTTP, off-SUT read-back installed through a public seam:
`DataIntegrityRuntime.installHttpOverride(Http)` + `ShippingReadbackHttp` (hits the RabbitMQ mgmt API on
a different host with basic auth, returns `(status, body)`, maps failure to non-2xx/status-0). The
reviewed oracle consumes only a JSON *collection surface*. Therefore:
- **SQL** (transport differs, extraction can stay): a `SqlReadbackProbe implements
  DataIntegrityRuntime.Http` that runs the psql query and **synthesizes the JSON shape the oracle
  already parses** (`[{"<key>":"…"}]` present / `[]` absent). Reuses `extractItems`/`containsKey`
  verbatim. Zero touch to the decision loop.
- **HTML** (transport is fine, *extraction* differs): the plan mis-frames this as symmetric with SQL. An
  `Http` override that returns raw HTML would parse to empty via the JSON-only `extractItems` → always
  ABSENT. The minimum change is an `HtmlProfileReadbackHttp` that does the authed GET **and scrapes the
  Orders table into the same synthetic JSON collection** (`[{"firstname":"TSMWF1"}]`), again reusing
  `containsKey`. The brittleness is confined to one transport class with its own loopback test.

The plan's "introduce a `ReadbackProbe` seam … provide implementations" (§2.1) instead re-plumbs the
five `s.http.getSut(readbackPath(triple))` call sites in the reviewed loop — the exact thing §2 says not
to touch — and makes the regression guard ("suites stay green") load-bearing where it needn't be.

**Fix:** Drop the new seam. Bind both modalities as `DataIntegrityRuntime.Http` implementations
installed via the existing `installHttpOverride`, each **synthesizing the collection shape** the oracle
already consumes. This preserves the reviewed decision loop byte-for-byte (the real regression guard),
and each probe gets a `ShippingReadbackHttpTest`-style loopback unit test. This is strictly *less* code
than the plan proposes.

### F4 [MAJOR] — §0's grounding is factually wrong: the g3 harnesses **load triples from YAML** via `TargetTripleRegistry.load`; a config-driven loader already exists and is in production. This moots the "bespoke vs config" framing (open-q4).
Plan §0: "the g3 harnesses … **construct their Triples in Java** … there is **no config-driven triple
loader today**." Both false. `CancelRefundHeadToHead.run` does
`TargetTripleRegistry.load(Paths.get(required("g3.triples.natural")))`;
`ShippingEnqueueHeadToHead.run` does `TargetTripleRegistry.load(Paths.get(required("g3.ship.triple")))`;
`MistRunner` loads `dataIntegrityRegistry` the same way. The loader is strict, tested
(`TargetTripleRegistryTest`), and already carries transport-adjacent fields (readback_mode, value_probe,
isolation_strategy, cluster). What is bespoke in g3 is the **stimulus** and the **read-back transport**,
not the triple.

Consequence: the plan's open-q4 dichotomy ("bespoke harness-per-SUT vs a config-driven registry") is a
false choice — you will have both no matter what. For the paper's "general tool" claim, authoring the
two new triples **in YAML** (reuse the loader) is *more* minimal (no Triple-construction code) *and*
reads as "configured, not coded." The §2.2 `readback.transport` discriminator, if kept, belongs there.

**Fix:** Author `oteldemo/target-triples.yaml` and `teastore/target-triples.yaml` and load them; do not
hardcode `Triple`s in Java. Correct §0's claim. Reserve bespoke Java for stimulus + transport impl only.

### F5 [MAJOR] — The **stimulus / scenario driver** — the largest real cost — is unscoped, and "author OpenAPI" does not drive the paired run.
Both target writes are **multi-step stateful flows**: OTel `POST /api/cart(product,session) →
POST /api/checkout`; TeaStore `login → add product 42 → POST cartAction confirm`. The OpenAPI-driven
writer emits **per-operation** tests; a checkout against an empty cart (or a confirm with no session)
will not produce the *acked* write the cases require, so the observe/OpenAPI path would record "not
acknowledged / no durable-write claim," not the intended signal. The existing new-SUT precedent proves
the real shape: g3 drives the flow through a hand-written Java `Stimulus`
(`CancelRefundHeadToHead.Stimulus.createPaidOrder`, `ShippingEnqueueHeadToHead.Stimulus.postShipping`,
implemented as SUT-tuned `TrainTicketStimulus`/live launchers) that **bypasses the OpenAPI writer
entirely.** No OTel/TeaStore enablement asset exists in the repo today (verified) — this is greenfield.

The plan's "minimum change / reuse verbatim" framing (§2) hides that the **stimulus is new, SUT-specific
code** (cart-populate, login+cookie-session, per-leg marker injection, fault sequencing) and is the bulk
of the wave. On the paired path the authored OpenAPI does not execute anything.

**Fix:** Scope and price a `Stimulus` per SUT as first-class work (and record *its* authoring cost, not
just OpenAPI's). State plainly that on the paired path OpenAPI is documentation only (or a separate
observe smoke). This also corrects the authoring-cost line: 2.75's cost metric must include the stimulus.

### F6 [MAJOR] — SQL transport (`kubectl exec … psql`) is the sounder choice, but its engineering hazards are under-specified.
Exec-psql beats JDBC-over-port-forward for this binding: capture-parity, **zero driver added to the
shaded jar** (pom uses maven-shade with a Log4j2 plugin-cache transformer; a new `org.postgresql` dep is
avoidable), and **no psql port-forward** to die on reboot (exec runs inside the pod). Endorse (b). But
the plan must pin, or the run will be fragile/unsafe:
1. **Pinned invocation as a runbook constant:** exact `kubectl exec <pod/selector> -- psql -tAc "…"`,
   db `otel`, schema-qualified `accounting."order"` (the case documents that a bare `\dt` misleads).
2. **Key quoting / injection:** the isolation key is interpolated into a SQL string *and* a kubectl
   argv. `freshValueLike` yields `mist-`+hex (safe), but pin a whitelist/parameterized form so a future
   key with a quote/space cannot break the query or the shell.
3. **Windows/WSL split:** mist.jar runs on Windows; kubectl/cluster live in WSL2 (per project memory).
   Mirror g3's configurable binary knob (`-Dg3.ship.kubectl`) so the probe can invoke `wsl kubectl …`
   or a Windows-reachable kubeconfig. Without it the exec silently fails.
4. **Failure mapping:** a non-zero psql/kubectl exit, missing pod, or timeout must map to
   **non-2xx / status 0** (like `ShippingReadbackHttp`), so the decisive-read gate treats it as
   *unusable*, never as absence (DataIntegrityRuntime L675–681, L690–695).

### F7 [MAJOR] — Operational hazards for an *execution* wave are incomplete: TeaStore DB-wipe edges, the OTel kafka mid-run wedge, and async-landing latency.
- **TeaStore (carry into the runbook):** never `GET /rest/generatedb` (regenerates/wipes all orders —
  the case's own capture-hygiene note records an accidental wipe); never scale the TeaStore `db`
  (no PVC → wipe on any cycle, per the case's "DB-DOWN PRODUCER RULED UNSOUND"); sequence the
  maintenance flag ON-for-confirm / **OFF-before-read-back** (this is a stateful `Fault.inject/clear`
  with convergence — reuse the g3 `ShippingEnqueueHeadToHead` inject-inside-try / clear-in-finally
  discipline; the plan doesn't mention a Fault abstraction for these).
- **OTel kafka wedge:** the case documents that a **kafka pod replacement wedges the producer PAST
  restore** and requires rollout-restarting checkout+accounting+fraud-detection. A per-stratum toggle
  (the g3 `clear→control→inject→fault→clear` shape) would hit this every iteration. **Pin single-toggle
  leg ordering:** run all control iterations (kafka up), then a single scale-to-0, then all fault
  iterations, then restore once at the end with the full recovery runbook. Forbid per-iteration restore
  for OTel and disclose it. The plan's "N≥4 consecutive per leg, control-leg-first" is compatible with
  this only if stated explicitly.
- **Async landing latency (control leg):** the OTel write lands in `accounting."order"` only after the
  async consumer + Postgres insert. MIST polls to `timeoutMs` (default 10s). `ShippingEnqueueHeadToHead`
  had to floor the timeout to 20s for a ~5s-sampled datum or the control leg reads absence too early →
  `NOT_EVALUABLE` ("control write never appeared"). Set and justify an OTel timeout floor and validate
  the control leg lands within it, or the pilot's success cell silently collapses to NOT_EVALUABLE.

### F8 [MAJOR] — Proposed unit-test set is a floor; several load-bearing tests are missing.
Proposed: count>0/==0, HTML marker present/absent, error→ERROR. Add (modeled on
`ShippingReadbackHttpTest` + `ShippingEnqueueHeadToHeadTest`):
1. **Exec-failure → non-2xx/status-0** for the SQL probe (analog of
   `transportFailure_readsAsStatusZero`), proving a failed exec is never scored as absence.
2. **Key quoting/injection** — a key with a dash/space/quote is handled safely (whitelist or param).
3. **HTML fail-closed** — a maintenance/error/truncated HTML page must NOT spuriously match the marker,
   and a *different user's* profile must not match this leg's marker (cross-leg isolation).
4. **End-to-end probe→oracle verdict** — drive `DataIntegrityRuntime.beginRun` with the override
   installed and assert `PairedFaultExecutor.evaluate` returns FIRE (fault) / NO_FIRE (control), not
   just the transport in isolation.
5. **`readback_bound` interaction** if a row-set SQL query is used (a bounded collection at the cap →
   error, not absence).
The regression-guard question ("prove behavior-preservation") largely dissolves under F3: if you reuse
`installHttpOverride` and touch none of the decision loop, "suites stay green" is a *sufficient* guard
because the hot path is unchanged. If the plan insists on the new seam, "suites green" is necessary but
not sufficient and a characterization test on the extracted method is required.

---

## MINOR

### F9 [MINOR] — Tie the `mist_bindable` false→true flip to a MEASURED run, not to the commit.
§2.2 flips `mist_bindable` "at THIS commit." The wave-3a refutation discipline the plan itself invokes
(§4) argues the flip should follow a *green* probe→oracle test **and** a real run producing the expected
FLAG/no_flag — otherwise a T9 row enters the MIST recall denominator on the strength of unproven code.
Gate the freeze §6 amendment on the run result.

### F10 [MINOR] — Don't both put transport on the Triple *and* have the harness own the override.
§2.2 adds a `readback.transport` discriminator to `Triple`. If the **harness** installs the transport
(`installHttpOverride`, per F3), the runtime needs no dispatch and the field is redundant metadata; if
instead the **runtime** dispatches on the field, it must construct SQL/HTML probes — re-coupling the
reviewed loop to modalities (contradicts "reuse verbatim"). Pick one. Recommended: harness owns the
override; the Triple field, if kept, is documentation validated at load, not a runtime switch.

### F11 [MINOR] — Fix the self-contradictory DoD wording ("observe-mode … on BOTH legs") when resolving F1.

---

## What the plan gets right (for balance)
- The read-back seam diagnosis is **code-accurate**: HTTP-only transport + JSON-only extractor is
  genuinely why both modalities are unbindable today (verified at L434/L499/L627 and in `extractItems`).
- Reusing the reviewed `evaluate`/`verdict` differential is the correct instinct; because that verdict
  is gate-agnostic, paired mode sidesteps the trace requirement (which is what makes TeaStore runnable
  at all).
- The anti-circularity argument (§2.3) is sound (label is authored, not MIST-derived).
- Standing discipline is carried correctly (main_track, no Co-Authored-By, FILE_INDEX/memory sync,
  per-item commits, CRLF-stripped scripts, no-redeploy, OTel recovery runbook referenced).
- The derisking instinct (land the seam on one SUT first) is right in principle — see the sequencing
  note for the one correction.

## Sequencing note (F.seq, advisory)
The plan derisks on OTel because the psql read-back is "clean." But the **binding-model** risk is higher
for OTel (server-assigned key, F2) and lower for TeaStore (request-supplied `firstname` marker →
straightforward `SUPPLIED` membership). If the goal is to derisk the *seam* (transport override +
JSON synthesis + oracle reuse), **TeaStore is the more faithful first exercise** and avoids the OTel
isolation-key rabbit hole. At minimum, resolve F2 before committing to OTel-first.

---

### Disposition
Resolve **F1 and F2 in a rev-2 and re-clear them** (they gate all coding). Fold F3–F8 into the design
before implementation. F9–F11 are cleanups. With those, the wave is a sound, genuinely small addition
that reuses the reviewed core rather than re-opening it.
