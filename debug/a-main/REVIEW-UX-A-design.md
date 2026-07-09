# Cold review A — UX design (design soundness + code-fact verification)

**Artifacts:** `debug/a-main/c2c3/mist-ux-design.md` (all) + `debug/a-main/c2c3/step2-execution-checklist.md` §1.9.
**Reviewer charge:** verify the §1 code-cited facts against the code; judge D1–D5 as a product review;
Allure ergonomics. Independent cold read; all claims below re-derived from source.

## VERDICT: ACCEPT-WITH-CHANGES

The §1 current-journey facts are essentially accurate (every citation checks out; corrections below are
refinements, not reversals) and the design direction — observe-by-default, precision-first firing,
propose-then-confirm triples — is the right product shape. But the work items as scoped do not
implement the design's own premise: **nothing arms the data-integrity runtime in a normal run, so W1's
verdict check would find zero records and the 1.9.3 demo DoD cannot pass** (B1). Fix B1 plus the
majors (most are one-decision-each) and this is implementable as written.

---

## 1. Code-fact verification

### 1.1 Verified correct
- **Flag + registry.** `mst.oracle.dataintegrity.enabled`, default `false`
  (`mist-core/src/main/java/io/mist/core/config/MstConfig.java:402-424`; whitelisted in
  `MstConfigValidator.java:145-153`). Registry loaded only under the flag; fail-fast when missing
  (`mist-cli/src/main/java/io/mist/cli/MistRunner.java:324-331`). Doc citations `:103` and `:328` are
  exact (field at 103-105, throw at 327-329).
- **"Hooks never throw"** — `mist-cli/src/main/java/io/mist/cli/fault/DataIntegrityRuntime.java:31-32`
  javadoc, and the code honors it: every hook wraps its body in try/catch and records the failure on
  the run record (`:370-376`, `:466-472`, `:652-658`).
- **Where records go / no path to the test outcome.** Records land on the in-process session holder
  (`Session.records`, `DataIntegrityRuntime.java:219`), retrievable ONLY via `endRun()` (`:307-314`),
  which is called exclusively by `PairedFaultExecutor` (`PairedFaultExecutor.java:184-204`, benign
  probe `:431-435`) and the G3 harnesses (`g3/CancelRefundHeadToHead.java:113,139`,
  `g3/ShippingEnqueueHeadToHead.java:149,174`, `g3/AccountCreateAgreement.java:123,141`). The JSON
  pairing report is `logs/data-integrity-reports/pairing_<exp>_<id>.json` (`MistRunner.java:677-679`).
  **Zero Allure usage anywhere in `io.mist.cli.fault`** (grep: no `Allure` in the package). The
  invisibility claim is fully confirmed.
- **Trace-shape / hidden-downstream Allure block** — verified at
  `mist-cli/src/main/java/io/mist/cli/writer/MultiServiceRESTAssuredWriter.java:772-810` (doc cites
  770-800; actual span 772-810): "Trace Shape Oracle Verdict" JSON attachment (`:772`), `Allure.step("❌
  shape violation…")` (`:776`), titled 🕳️ attachment with Jaeger deep-link + WHAT-THE-CLIENT-SAW /
  WHAT-ACTUALLY-HAPPENED (`:793-804`), labels `mist.anomaly` + `tag=mist_hidden_downstream`
  (`:805-806`), parameter (`:807`). The 4-part pattern (attachment/label/tag/parameter) is the template
  D1 should copy verbatim.
- **Hook emission + flag-off cleanliness.** Hooks are emitted only for triple-matched steps
  (`MultiServiceRESTAssuredWriter.java:1969-1997` beforeWrite beside the body literal, `:2207-2214`
  afterWrite after response capture); triples are set only under the flag (`MistRunner.java:332-335`,
  writer javadoc `:106-115`). Flag-off output carries no trace of the feature — pinned by
  `mist-cli/src/test/java/io/mist/cli/writer/DataIntegrityEmissionTest.java:130-135`. (Nuance: the pin
  is a "contains no `DataIntegrityRuntime`" containment assertion, not a golden byte-compare; byte
  identity holds by construction since all emission is conditional.)
- **Demo properties carry no B2 keys** — verified by full read of
  `mist-cli/src/main/resources/My-Example/trainticket-demo.properties` (no `mst.oracle.dataintegrity.*`
  anywhere). Note for D4: a bundled registry ALREADY exists at
  `mist-cli/src/main/resources/My-Example/trainticket/target-triples.yaml` (adminroute + contacts, the
  Gate-1 pair), and `conf.path` is resolved against the properties-file directory
  (`MistPathResolver.java:27-48,90-102`), so adding the single flag line to the demo properties finds
  it. D4 is feasible as designed.
- **D3 gating verified.** Pairing requires `mist.fault.injection.enabled=true`
  (`FaultInjector.java:20-25`) AND a loaded registry (`MistRunner.java:531-532`); comparator mode
  requires `-Dmst.comparator.enabled` (`MistRunner.java:352`) plus both flags (`:565-577`). All default
  off → the paired/eval path is really flag-gated out of normal runs.
- **W1 sequencing premise holds — no @AfterAll drain needed.** `afterWrite` is SYNCHRONOUS: the poll
  loop (500ms poll / 10s timeout / 3s trace-settle defaults, `DataIntegrityRuntime.java:58-71`) runs in
  the calling test thread (`:555-651`) and the record is complete when the emitted call at
  writer `:2212` returns — which is BEFORE the test's own status-code/LLM assertions (`:2234+`). A
  verdict check emitted right after `:2212-2214` sees the final record. (Placement consequence in M8.)
- **MistRunResult carries the Allure dir + run id** (`MistRunResult.java:15-32`) — §1.1 accurate.

### 1.2 Corrections to cited facts
- **The registry has NO property key.** The path is a hardcoded convention:
  `Paths.get(inputs.confPath).toAbsolutePath().getParent().resolve("target-triples.yaml")`
  (`MistRunner.java:325-326`). The doc never names how the file is located, and D2 says the user
  "passes it as the registry" — there is no pass mechanism to invoke. See m1.
- **Verdict enum names are wrong at the record level.** Actual `QuiescenceGate` =
  `OBSERVED_PRESENT / OBSERVED_COMPLETE_ABSENT / TIMEOUT_ABSENT / NOT_APPLICABLE`
  (`DataIntegrityRuntime.java:77-86`). There is no `PRESENT`; `NOT_EVALUABLE` exists only as the
  pair-level `PairVerdict` (`PairedFaultExecutor.java:98`) which is unreachable in single-leg observe
  mode. See M1.
- **`MistRunner` does not produce the Allure report.** It populates `target/allure-results/` (via the
  AllureJunit4 listener + `setupAllureForIntelliJ`, `MistRunner.java:2957-2992`) and points the user at
  `allure serve target/allure-results` (`:453-455`; comment `:380-384`). "Ship categories.json into the
  report dir" must read "into the allure-results dir". See M9.

---

## 2. Findings

### [BLOCKING] B1 — No observe-mode session lifecycle: as scoped, W1–W4 surface nothing
With no armed session every hook is a passthrough no-op (`DataIntegrityRuntime.java:29-30` javadoc;
`:332-335` beforeWrite early-return; `:514-517` afterWrite early-return). `beginRun`/`endRun` are
invoked ONLY by `PairedFaultExecutor` and the G3 harnesses (grep over `mist-cli/src/main/java`: no
other callers). In exactly the D3/W4 product configuration — flag on, injection off —
`pairingRequested` is false (`MistRunner.java:531-532`) and execution goes through
`executeGeneratedTestsWithJUnit` (`:552`) or the enhancer path (`:533-537`): **no baseline read, no
key freshening, no polling, no records.** §2's "for each triple-matched write it captures the ack,
polls the read-back, and renders a verdict" describes behavior that does not exist outside pairing,
and no work item creates it. D1's "map the record verdict" maps nothing; demo DoD 1.9.3 fails.

**Fix — add work item W0 "observe-session lifecycle", deciding four things:**
1. *Arming:* MistRunner brackets each execution round with `beginRun(triples, "observe")`/`endRun()`
   when flag-on ∧ injection-off (simplest; keeps `endRun()`'s records available for the run summary),
   or a writer-emitted `@BeforeClass`/`@AfterClass` pair. Note `beginRun` throws if a run is already
   active (`DataIntegrityRuntime.java:281-283`) — per-round bracketing must be exception-safe.
2. *Parallelism:* `beginRun` refuses `mst.test.parallelism > 1` (`:289-300`), but normal runs default
   to auto → min(cores, 8), or 4 with LLM validation (`MistRunner.java:3248-3263`). Either force 1
   while armed (as the pairing path does, `:697-698`) and disclose the throughput cost, or do the
   (larger) concurrency-hardening of the runtime. Decide explicitly; the design is silent.
3. *Which rounds arm:* the demo runs the ENHANCER path (demo properties: `test.enhancer.enabled=true`)
   with multiple rounds, plus status-code exploration re-runs, plus optional two-phase. Without a rule
   (e.g. final-round-only, or dedupe by correlator), one write is recorded N times and the D1 summary
   double-counts. Also exclude data-integrity failures from `FailedTestCollector`→ enhancer intake, or
   the LLM will burn calls "fixing" inputs for a genuine SUT defect (mirror of `skip5xx`).
4. *Record access for the check point:* `endRun()` is the only reader today (`:307-314`). Give the
   generated check a surface — `afterWrite` returning its `RunRecord`, or a `LAST_RECORD` ThreadLocal
   mirroring the writer's existing `LAST_VERDICT` channel (`DataIntegrityRuntime.java:26-27` javadoc
   names the precedent).

### [MAJOR] M1 — D1's verdict names don't match the code; the mapping must be spec'd on the RunRecord surface
`PRESENT` → `OBSERVED_PRESENT`; `NOT_EVALUABLE` (pair-level) → record-level `NOT_APPLICABLE` and/or
`error != null`. The defect predicate must be exactly:
`acked && error == null && gate == OBSERVED_COMPLETE_ABSENT`; confirmed = `OBSERVED_PRESENT`;
unconfirmed = `TIMEOUT_ABSENT`; info = `NOT_APPLICABLE` or any `error != null` (error records already
absorb every unsound case: non-2xx decisive read `DataIntegrityRuntime.java:592-598`, VANISHED probe
row `:576-582`, readback-bound `:632-639`, baseline instability `:440-457`). W1's "verdict→fail/step
matrix" tests would otherwise be written against names that don't compile.

### [MAJOR] M2 — The defect tier is Jaeger-gated and the design never says so
`traceComplete` returns false when `jaeger.base.url` is unset or the step has no trace id
(`DataIntegrityRuntime.java:998-1001`) → an absence can NEVER upgrade past `TIMEOUT_ABSENT` → with D1
semantics, `failOnLost` can never fire on an untraced SUT. The §1.3 "what the user must provide" table
omits the tracing backend entirely; a normal user on a Jaeger-less SUT gets a product whose headline
oracle silently degrades to warnings. Fix: (a) list Jaeger/OTel reachability as a REQUIREMENT of the
defect tier in §1.3 + D4 docs; (b) the D1 run summary must state which tier was reachable (e.g.
"jaeger.base.url unset — defect tier disabled, unconfirmed-only mode"); (c) the demo is fine
(`jaeger.base.url` set in the demo properties).

### [MAJOR] M3 — Async-write false-defect vector inherited into the product default
The polling machinery handles async persistence ≤ timeout (10s default) fine. Beyond it: a queue
consumer that persists later can leave the producer-side trace complete-and-stable across the two
settle looks (`DataIntegrityRuntime.java:998-1013`, settle 3s) → absence upgrades to
`OBSERVED_COMPLETE_ABSENT` → false FAIL on a correct-but-slow async write. The code discloses a
related residual itself (`:60-67`), and G1's FP-0.0 was measured on the sync stratum with an async
disclaimer — §2's "the product inherits a calibrated precision story" over-claims for arbitrary user
SUTs. Fix: per-triple `timeout_ms` override (the registry has NO per-triple timeout today —
`TargetTripleRegistry.java:135-153`; only global `-D` knobs), and/or an `async: true` triple field
demoting lost→warn; carry the async caveat in D4 docs verbatim from the Gate-1 disclosure.

### [MAJOR] M4 — D2's heuristic proposes read-back shapes the runtime cannot evaluate
The doc proposes "find the sibling read (`GET /res`, `GET /res/{id}`)". Per-entity `GET /res/{id}` is
unsupported: `readback_endpoint` is a literal path (no templating; the server-assigned id isn't known
at registry time), and membership parses collections only (`extractItems`,
`DataIntegrityRuntime.java:891-930` — a single-object body yields an empty item list → `containsKey`
false → perpetual ABSENT → **false LOST** on every write once the trace is complete). The bundled
registry's own header says "collection read-back … no per-entity GET"
(`My-Example/trainticket/target-triples.yaml:12-14`). Also, the isolation key must be a CLIENT-supplied
round-trip field: `freshen` strips `id` (`DataIntegrityRuntime.java:696`), and a server-overwritten
"id-like" key would never reappear in the read-back → false LOST. Fix W3: restrict proposals to
collection-shaped sibling GETs; propose only keys present in BOTH the request schema and the read-back
item schema; set `readback_bound` when the read declares pagination params.

### [MAJOR] M5 — Observe-mode interaction with negative variants is unaddressed
Hooks are emitted for every triple-matched step regardless of variant polarity
(`MultiServiceRESTAssuredWriter.java:1969-1997` has no faulty check; the demo runs `faulty.ratio=0.8`).
Once armed (B1): (a) `beforeWrite` freshens isolation-key fields — if the Sniper's designed-invalid
parameter IS an isolation-key field, freshening silently overwrites it and the negative test no longer
tests its fault; (b) an acked-but-validation-rejected invalid write reads as ACKED-BUT-LOST, conflating
a soft-error/validation bug with a durability defect. Fix: in observe mode emit hooks only on positive
variants (`scenario.getFaulty()` is available at emission — writer `:2028`), disclosed in D3.

### [MAJOR] M6 — §5's "no behavior change to the eval harnesses" is contradicted by the natural W1 implementation
The verdict check is emitted into the SAME generated code the pairing legs execute. A `failOnLost`
throw in a fault leg aborts the remaining steps of that method → changes the fault leg's record stream
(the correlator join tolerates skips, but tallies change), and a control-leg throw would be new
behavior too. Fix: the failure semantics must be inert unless the session is an observe session (e.g.
gate on the run label or a session-mode bit) — pairing/benign-probe sessions keep today's
record-only behavior. State this in W1's DoD.

### [MAJOR] M7 — Checklist ordering: 1.9.3's demo DoD depends on step-2 work
The DoD requires an end-to-end TrainTicket run (`base.url=localhost:32677` + Jaeger `:30005` live), but
TT deploy prerequisites (26 GB WSL restore, deploy wave) are scheduled AFTER 1.9 in step 2
(`step2-execution-checklist.md` 2.1). Either pull the 26-GB restore + a TT quick_start into 1.9.3's
prerequisites, or re-scope the DoD to a currently-live SUT and re-run the TT demo at step 2.75.

### [MAJOR] M8 — D1 failure placement: decide mid-step vs end-of-test explicitly
The natural check point (right after writer `:2212`) runs BEFORE the test's functional assertions
(`:2234+`); throwing there pre-empts them and aborts later steps (and later triple-matched writes) in
multi-step scenarios. Precedent cuts both ways: the shape oracle already throws mid-test on ERROR
verdicts (writer `:1951-1953`). Recommendation: record + emit the Allure step/attachment inline at the
write, but do the `failOnLost` throw at END of the test method, aggregating all lost writes, with a
stable marker string (e.g. `ACKED-BUT-LOST`) in the failure message — categories.json needs that
marker to categorize (M9), and end-of-method maximizes per-run evidence for the benchmark. A separate
synthetic test node per write is NOT worth it under JUnit4+AllureJunit4 (requires hand-writing Allure
result JSONs — new machinery for marginal gain).

### [MAJOR] M9 — Allure mechanics: three concrete gotchas
1. **categories.json location/timing:** the tool never generates the HTML report
   (`MistRunner.java:380-384`, `:453-455`); Allure reads `categories.json` from the RESULTS dir at
   serve/generate time. Write it into `target/allure-results/` AFTER the run-start cleanup —
   `clearAllureResults` deletes files there (`MistRunner.java:2930-2940`); end-of-execution is the safe
   point. "Report dir" wording must change.
2. **Categories can't capture passing tests:** category matching runs on failed/broken status +
   message/trace regex. The "Persistence unconfirmed (timeout-gated)" tier is non-failing by design →
   it can never appear as a category. Use the existing filterable tag-label pattern instead
   (`tag=mist_hidden_downstream` precedent, writer `:806` — e.g. `mist_ackedlost`, `mist_unconfirmed`).
   Keep categories.json only for the FAILING lost-write tier (matched on the M8 marker).
3. **"Run-level summary attachment" is not an Allure concept** (attachments are per-test). Use
   `environment.properties` in allure-results for the counts, AND extend the tool's established
   run-summary surfaces: record data-integrity verdicts into `FaultDetectionTracker` (the trace-shape
   oracle already does, writer `:819-824`) so they land in the fault-detection report and the terminal
   findings summary (`MistRunner.java:425-436`). That keeps ONE summary channel instead of inventing a
   second.

### [MAJOR] M10 — "Expert tier stays manual" understates: it is harness-only in the product path
The writer never emits `beforeWriteSupplied`: a bodyless triple-matched step is left un-hooked
(`MultiServiceRESTAssuredWriter.java:1976-1984`; pinned by `DataIntegrityEmissionTest.java:138-143`),
and a SUPPLIED triple whose write has a body hits the freshening hook and records a loud ERROR
(`DataIntegrityRuntime.java:344-349`). So a user who hand-authors a supplied/value-delta triple (the TT
cancel class) gets error records or nothing from the product — the expert tier is exercisable only via
the G3 focused harnesses today. D2/D4 must say "harness-only today; product wiring is future work", or
W-scope the `beforeWriteSupplied` emission. Presenting it as "documented as the expert tier" without
this reads as supported-if-you-try-hard, which the code refutes.

### [MINOR]
- **m1** — D2 "passes it as the registry": no such mechanism; the flow is "place/rename to
  `target-triples.yaml` beside the file `conf.path` points at" (`MistRunner.java:325-326`). Name the
  convention in §1.2/D2, and have the proposal generator emit `proposed-triples.yaml` beside the conf
  with the rename instruction in its header.
- **m2** — New keys (`failOnLost`, `mode`) must be added to `MstConfigValidator.KNOWN_KEYS`
  (`MstConfigValidator.java:145-153`) or every run warns under the owned `mst.` namespace; and the
  existing key style is lowercase-dotted (`poll.ms`, `trace.settle.ms`, `fpprobe.runs`) — prefer
  `mst.oracle.dataintegrity.fail.on.lost` over camelCase.
- **m3** — Bundled registry header is stale: "nothing reads this file unless
  `mist.fault.injection.enabled=true`" (`My-Example/trainticket/target-triples.yaml:2-3`) — it is read
  whenever `mst.oracle.dataintegrity.enabled=true` (`MistRunner.java:324-331`), and after D4 on every
  demo run. Also `cluster.context: minikube` (`:34`) predates the kind cluster — harmless for observe
  mode (cluster block feeds only the injector) but fix it with D4.
- **m4** — The "byte-identical" pin is a no-trace containment assertion, not a golden byte-compare
  (`DataIntegrityEmissionTest.java:130-135`). Fine, but W1's DoD should keep asserting flag-off
  emits none of the NEW check code either.
- **m5** — Observe-mode latency budget undocumented: a genuinely lost/slow write costs up to
  timeout + settle + re-read (~13s+) per hooked write, and each hooked write adds a baseline GET +
  ≥1 read-back GET. Fine at demo scale; belongs in the D4 docs section.

---

## 3. Answers to §6 reviewer questions
1. **D1 semantics:** fail-on-`OBSERVED_COMPLETE_ABSENT`-only is right — it is the calibrated G1 rule,
   and the code's error-record discipline (non-2xx decisive reads, VANISHED, bounds) already keeps
   flaky-SUT noise out of both the fail and warn tiers (`DataIntegrityRuntime.java:566-598`): 503-prone
   read-backs become error/info records, not warnings, so TIMEOUT_ABSENT spam on a healthy sync SUT
   should be rare (absent-at-10s on an eventually-landing write is the only source). A 3-level knob
   adds little: `off` duplicates the master flag, `warn` = `failOnLost=false`. Keep the boolean; spend
   the effort on M2 (Jaeger disclosure), M3 (async demotion / per-triple timeout) and M8 (end-of-method
   aggregation). Fail-the-whole-test is precedent-consistent (shape oracle throws too); synthetic
   per-write nodes not worth it (M8).
2. **D2 false-lost risk is real** and the two §6 guardrails are necessary but not sufficient. Add:
   collection-only read-backs + round-trip key fields + readback_bound (M4 — these prevent structurally
   guaranteed false LOSTs the control probe would only catch at run time); make the first-run control
   probe MANDATORY for proposed triples with QUARANTINE semantics — a triple whose probe write never
   reaches `OBSERVED_PRESENT` is auto-disabled and reported as an info verdict ("read-back never
   observed a landed write — triple quarantined"), never warn/fail; surface probe outcomes in the D1
   run summary.
3. **Observe identity:** consistent and code-true (injection paths verified flag-gated,
   `MistRunner.java:531-532,352,565-577`) PROVIDED M2 and M10 are disclosed — "no injection" is
   honest, but "the product detects acked-but-lost" needs the Jaeger-tier and expert-tier-harness-only
   qualifications.
4. **Demo-properties change:** safe for existing users — the flag defaults off for everyone else, the
   bundled registry already exists, and the enhancer-conflict check only trips with injection on
   (`MistRunner.java:341-345`). The demo itself will get slower (armed session → parallelism 1 + poll
   latency) — disclose in D4.
5. **Eval reproducibility:** yes, one real threat — M6 (verdict check must be inert in paired
   sessions). New config keys are additive-default-off and don't perturb frozen eval configs; the
   flag-on codegen change is expected (only flag-off byte-identity is pinned).

## 4. Checklist §1.9 consistency
1.9.2's D1–D4 lines match the design doc (including inheriting M1's naming and B1's missing session
lifecycle — add W0 to 1.9.2 explicitly). 1.9.3 has the M7 ordering dependency. 1.9.4/W5/D5 consistent
with the E2 symmetry story and touch no code. §5's freeze-safety claims hold given M6.
