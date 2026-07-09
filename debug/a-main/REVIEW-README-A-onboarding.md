# Cold review A — README as the ONLY thing a new user reads (data-integrity oracle onboarding)

Reviewer: independent cold reviewer A (no shared context with the authors of the README patch).
Object under review: `README.md` at working-tree HEAD, judged as the sole onboarding document for the
data-integrity oracle (observe mode). Every user-facing claim was checked against the code
(`MistRunner`, `DataIntegrityObserveCheck`, `DataIntegrityRuntime`, `TriplesProposer`,
`TargetTripleRegistry`, `MistPathResolver`, `MistMain`, `MultiServiceRESTAssuredWriter`,
`trainticket-demo.properties`, `target-triples-demo.yaml`), and the TriplesProposer CLI was
**executed live** against the bundled TrainTicket spec.

## VERDICT: ACCEPT-WITH-CHANGES

The dedicated section is accurate on verdict semantics, defaults, gating, quarantine, parallelism
and the Jaeger prerequisite — every behavioral claim I checked matches the code, and the section's
boldest disclosure (proposer yields 0 on the bundled TT spec) is empirically TRUE (I ran it). All
three cross-links to the section resolve (GitHub slug byte-verified, em dash → `--`). What blocks a
clean ACCEPT: the Inputs table documents a registry key value + resolution rule that would break at
startup if copied (doc-vs-code mismatch), the one copy-pasteable command in the section fails as
typed, the hand-authoring path (the path the README itself says TT-like users must take) never shows
the registry schema, and the intro/architecture area — the top of the funnel — never mentions the
main contribution at all. No finding rises to BLOCKING: the bundled demo as shipped works, and
nothing documented misleads about *safety* (precision-first gating is described exactly as built).

---

## Findings

### [MAJOR] A-1 — Inputs table misstates the registry key's bundled value AND its resolution rule; copying the shown value breaks startup

- `README.md:55` column header: "Bundled demo value **(relative to the .properties file)**";
  `README.md:61` registry row shows the value `trainticket/target-triples-demo.yaml`.
- The actual bundled value is the bare `target-triples-demo.yaml`
  (`mist-cli/src/main/resources/My-Example/trainticket-demo.properties:480`).
- The actual resolution rule for a relative `mst.oracle.dataintegrity.registry` is **beside the SUT
  conf first, then CWD fallback** — NOT relative to the .properties file
  (`mist-cli/src/main/java/io/mist/cli/MistRunner.java:331-344`). The key is absent from
  `MistPathResolver`'s `INPUT_PATH_KEYS`/`MST_INPUT_PATH_KEYS`
  (`mist-cli/src/main/java/io/mist/cli/MistPathResolver.java:27-62`), so the "Configuration layout"
  paragraph (`README.md:280-287`: "Every INPUT-path key ... the various registry paths) are resolved
  relative to the .properties file's own directory by io.mist.cli.MistPathResolver") is wrong for
  this key.
- Consequence, traced concretely: a user who writes the table's value
  `mst.oracle.dataintegrity.registry=trainticket/target-triples-demo.yaml` into the demo file gets
  beside-conf = `My-Example/trainticket/trainticket/target-triples-demo.yaml` (missing) → CWD
  fallback `<repo-root>/trainticket/target-triples-demo.yaml` (missing — no `trainticket/` dir at
  repo root, verified) → `IllegalStateException: ... no registry at ...`
  (`MistRunner.java:349-353`). The mismatch also teaches the wrong mental model for pointing at a
  registry anywhere non-default.
- Fix (three edits):
  1. Table cell → `target-triples-demo.yaml` with an inline note "(resolves beside the SUT conf —
     the one exception to the props-relative column; demo ships it ON)".
  2. Oracle section item 2 (`README.md:426`): after "default `target-triples.yaml` beside your SUT
     conf" add "; a relative override also resolves beside the conf (CWD as fallback)".
  3. Configuration-layout paragraph (`README.md:280-284`): name the actual resolver-handled registry
     keys or append "(exception: `mst.oracle.dataintegrity.registry` resolves beside the SUT conf —
     see the oracle section)".

### [MAJOR] A-2 — The section's only copy-pasteable command fails as typed

- `README.md:428`: `java -cp mist.jar io.mist.cli.fault.TriplesProposer <spec.yaml>`. There is no
  `mist.jar` at repo root — the launcher is `mist-cli/target/mist.jar`, which the README itself uses
  in every other command (`README.md:39,112,154,175,191`). From the repo root (where Quick Start A
  step 4 explicitly puts the user) this exact line dies with a class-not-found/jar-not-found error —
  at precisely the "what do I type?" moment of the authoring path.
- I verified the correct form works end-to-end:
  `java -cp mist-cli/target/mist.jar io.mist.cli.fault.TriplesProposer "mist-cli/src/main/resources/My-Example/trainticket/merged_openapi_spec 1.yaml" <out>`
  → `proposed 0 triple(s)` (see verified list, item V10).
- Fix: change to `java -cp mist-cli/target/mist.jar io.mist.cli.fault.TriplesProposer <spec.yaml> [out.yaml]`
  (the optional second arg exists — `TriplesProposer.java:138-146` — and is worth showing since the
  default drops `proposed-triples.yaml` into the CWD). Same stale `mist.jar` form is echoed in the
  shipped properties comment (`trainticket-demo.properties:477`) — fix in the same pass.

### [MAJOR] A-3 — The hand-authoring path (the path the README says TT-like users must take) never shows what a registry looks like

- The section honestly discloses that on declaration-poor specs the proposer yields 0 and "the demo
  registry is hand-verified instead (exactly the honest fallback the tool expects of you)"
  (`README.md:432-435`) — but then never tells the user **how** to hand-author:
  - No field list. The six core fields (`name`, `write_endpoint`, `dependency`,
    `readback_endpoint`, `isolation_key`, `isolation_strategy`) appear nowhere in the README.
  - No pointer to the demo registry **as a template**. `README.md:445` names
    `trainticket/target-triples-demo.yaml` only as demo wiring, without its real path prefix
    (`mist-cli/src/main/resources/My-Example/trainticket/`), and never says "copy this".
  - No semantic guidance for the load-bearing concepts: that `isolation_key` fields are freshened
    per test and then looked up in the read-back collection (a user who picks a server-generated or
    non-echoed field authors a permanently-quarantined triple), that `dependency` is the
    trace-matchable persisting service name, that `readback_endpoint` must be a **collection** GET
    (`GET `-prefixed enforced at load, `TargetTripleRegistry.java:252-255`).
  - README is the ONLY user-facing doc: repo-wide, every other `target-triples`/proposer mention
    lives under `debug/` (internal) — verified by grep. The proposer output's own header says "see
    the data-integrity docs" (`TriplesProposer.java:119`), which for a new user dead-ends at this
    README section. And on a 0-proposal spec the proposal file contains **no example entry** to
    learn from (verified: header + empty list only).
- What softens it (why not BLOCKING): the parser is strict and loud — unknown keys, missing fields,
  a non-GET read-back, and every cross-field violation produce errors that name the allowed keys
  (`TargetTripleRegistry.java:173-187,238-319`), and the demo registry file itself carries an
  excellent explanatory header. A determined user recovers. But "the README as the only thing a new
  user reads" leaves them staring at a blank YAML.
- Fix (pick at least the first): (a) inline one minimal annotated triple (~8 lines — the
  `adminroute-create` entry is ideal) + a one-line-per-field glossary incl. the allowed
  `isolation_strategy` values `fresh-strings | station-pair | supplied`
  (`TargetTripleRegistry.java:56-62`); (b) an explicit "use
  `mist-cli/src/main/resources/My-Example/trainticket/target-triples-demo.yaml` as your template"
  sentence; (c) one sentence on how membership checking works so isolation-key choice is informed.

### [MAJOR] A-4 — The main contribution is invisible from the intro/architecture area

- The intro blockquote (`README.md:3-12`) names exactly three contributions (Root API Mode, Sniper
  Strategy, Trace Shape Oracle); the architecture diagram (`README.md:22-37`) lists mist-core's
  "five architectural stages" and mist-cli's contents — the data-integrity oracle appears in
  neither. A user reading only the top of the README never learns MIST checks whether acknowledged
  writes persist; first mention is an *(optional)* input-table row at `README.md:61`.
- The other four mandated discovery points all deliver (Inputs row :61, Quick Start A callout
  :129-135, "What this does" item 6 :254-257, dedicated section :410-445) — the funnel is broken
  only at its mouth.
- Fix without disturbing the paper's "three named contributions" framing: append one sentence to
  the intro blockquote, e.g. "At runtime, a fourth check — the *data-integrity oracle* (observe
  mode) — verifies each acknowledged write actually persisted, surfacing 💧 acked-but-lost writes
  in the report." Optionally add `fault/  Data-integrity oracle (observe runtime, TriplesProposer,
  target-triple registry)` to the mist-cli tree — the Repository-layout section
  (`README.md:532-548`) omits the `fault/` package entirely, so even a code-spelunking user gets no
  map pin.

### [MINOR] A-5 — `jaeger.base.url` expected URL form undocumented; a wrong base degrades silently

- `README.md:436-439` says "jaeger.base.url must point at your Jaeger API" but not the form. The
  code appends `/traces/<id>` and expects an API base already ending in `/api`
  (`DataIntegrityRuntime.java:1086-1089`; demo value `http://localhost:30005/jaeger/ui/api`,
  `trainticket-demo.properties:352`). A user who supplies the familiar UI base
  (`http://localhost:16686`) gets HTML back, `spanCount` returns -1, `traceComplete` stays false —
  every absence silently stays ⏳ with only a debug-level log (`DataIntegrityRuntime.java:1096-1099`).
  Precision-safe, but the user thinks the defect tier is armed when it is not.
- Fix: one clause + example: "the Jaeger HTTP API base, e.g. `http://localhost:16686/api` (the demo
  uses `.../jaeger/ui/api`)". The ⏳ attachment does tell them at finding-time
  (`DataIntegrityObserveCheck.java:70-72,140-143`) — good — but only after a run.

### [MINOR] A-6 — Timing knobs exist but are undocumented

- `mst.oracle.dataintegrity.poll.ms` (500), `.timeout.ms` (10000), `.trace.settle.ms` are real,
  user-settable properties (`DataIntegrityRuntime.java:58-70`). A slow-persisting SUT at the 10s
  default will report spurious ⏳ with no documented remedy. One table row or sentence in the
  section fixes it.

### [MINOR] A-7 — Quarantine wording: run-scope vs check-time

- `README.md:442-443` "A triple whose read-back never shows *any* write landing in the run is
  quarantined" — the implemented check is *at verdict time*: "has not shown any write landing **so
  far** this run" (`DataIntegrityObserveCheck.java:74-93`,
  `DataIntegrityRuntime.java:343-352`). A LOST candidate on the run's first hooked write of a triple
  is quarantined even if a later write of the same triple lands (conservative, precision-first — the
  right direction, but a user seeing ⚠️-then-✅ on the same triple may be confused by the README's
  whole-run phrasing). Suggest "has not (yet) shown any write landing this run".

### [MINOR] A-8 — Feature is scoped to the MST generator path; README doesn't say so

- Triples reach the writer only when it is the `MultiServiceRESTAssuredWriter`
  (`MistRunner.java:355-358`); with a classic generator (`generator=RT/CBT/...`) the registry loads
  and then nothing is hooked — silent no-op. All bundled demos use MST so the default path is fine,
  but one clause ("requires the MST generator — the default in every bundled demo") would prevent a
  confusing silent-quiet run for classic-generator users.

### [MINOR] A-9 — Report-surface completeness nits

- The verdict table (`README.md:417-422`) lists 3 verdicts; the report can also show
  ⚠️ quarantined (documented in prose below — fine) and ℹ️ not-acked / internal-error steps
  (`DataIntegrityObserveCheck.java:48-57` — documented nowhere). The end-of-run **terminal summary**
  (hooked/✅/💧/⏳ counts + a no-Jaeger warning note, `MistRunner.java:643-667`) is also
  undocumented, yet it is the discovery surface for users who never open Allure. One sentence each.
- Quick Start C (`README.md:160-192`) never mentions the oracle among the keys an own-SUT user
  should consider; a one-line "optionally drop a `target-triples.yaml` beside your conf to arm the
  data-integrity oracle (see below)" closes the loop for the audience that most needs it.

---

## Charge-question answers

1. **Discoverability walk-through:** 4 of the 5 mandated surfaces deliver the feature with working
   links; the intro/architecture area does not (A-4). All three anchors
   `#data-integrity-oracle-acked-but-lost-writes--observe-mode` (README:61,135,257) resolve against
   the heading at README:410 — byte-verified: the heading uses a true em dash (U+2014), GitHub's
   slugger drops it and hyphenates both flanking spaces → the double-hyphen `writes--observe` in the
   links is exactly right. "What do I type?" dead ends: the proposer command (A-2), the hand-authored
   registry contents (A-3), and the Jaeger URL form (A-5). Every other how-to question I posed as a
   first-time reader (how to enable, where the registry lives by default, how to see verdicts, how to
   make losses fail the build, what the demo already ships) is answered in-section.
2. **Correctness:** every checked claim matches the code except the two registry-resolution
   misstatements (A-1). Verified-correct list below.
3. **Proposer expectation:** honest — and now *empirically verified* honest (live run: 0 proposals
   on the bundled TT spec; the spec's GET responses are declared as opaque `HttpEntity` — 230
   occurrences). But honest-then-stranded: the README names the fallback (hand-author) without
   equipping it (A-3). Minimum fix = point at the demo registry as the template; better = inline one
   annotated triple.
4. **Registry authoring from zero:** not yet. A user gets the concept, the enable key, the default
   location and the guard rails, but not the schema, the field semantics, or the template path. The
   loud strict parser + the demo file's header would eventually get them there; the README should
   not make them earn it (A-3, plus A-5/A-6 for the two silent-degradation knobs).

---

## Verified-correct list (claim → code)

- **V1** `mst.oracle.dataintegrity.enabled` exists, default false; demo ships `true`
  (`MstConfig.java:423-424`; `trainticket-demo.properties:479`).
- **V2** `mst.oracle.dataintegrity.registry` exists; default = `target-triples.yaml` **beside the
  SUT conf** exactly as the section states; missing registry fails loudly with a self-explanatory
  message naming both the default convention and the override key (`MistRunner.java:329-353`).
- **V3** `mst.oracle.dataintegrity.failonlost` default false = warn; a validated observation-gated
  loss throws `AssertionError` only when the flag is true, and the evidence attachment is written
  before the throw (`DataIntegrityObserveCheck.java:28,95-107`).
- **V4** Verdict semantics as tabled: ✅ green step (`:59-62`); 💧 titled attachment with ack vs
  read-back, poll timeline, isolation key (`evidenceText`, `:116-145`) + warning step (`:107`);
  ⏳ warning step + attachment, "NOT counted as a defect" (`:63-72`).
- **V5** Observation-gating exactly as described: `OBSERVED_COMPLETE_ABSENT` only when the step's
  own trace is complete (exact traceparent id, stable span count across a settle re-look) and after
  a post-settle re-read of the read-back; otherwise `TIMEOUT_ABSENT`
  (`DataIntegrityRuntime.java:682-711`).
- **V6** Jaeger prerequisite claim precisely true: no/blank `jaeger.base.url` (or no trace id) ⇒
  `traceComplete` = false ⇒ 💧 and `failonlost` can never fire; the ✅ tier needs no trace backend
  (`DataIntegrityRuntime.java:1080-1083`, `:655-668`).
- **V7** Quarantine guard exists and downgrades 💧 → ⚠️ warning + attachment when the triple's
  read-back has shown no write landing (`DataIntegrityObserveCheck.java:74-93`,
  `DataIntegrityRuntime.java:343-352`).
- **V8** "Parallelism forced to 1 for the hooked stretch" — set on arming, restored on end
  (`MistRunner.java:606-631`); observe mode is mutually exclusive with injection/comparator ("no
  faults are injected" — `observeEligible`, `MistRunner.java:600-604`).
- **V9** "Negative/faulty test variants are never checked" — the writer emits
  `DataIntegrityObserveCheck.afterStep` under `if (!isNegativeTest)`
  (`MultiServiceRESTAssuredWriter.java:2294-2302`).
- **V10** TriplesProposer CLI: default output `proposed-triples.yaml` (`TriplesProposer.java:146`);
  heuristic = body-carrying POST + same-path **collection-shaped** GET only, per-entity GETs never
  proposed, expert tier (bodyless/value-delta/supplied) excluded by design (`:73-106`, javadoc
  `:20-34`); output header mandates review + TODO(dependency) fill (`:111-121,130`). **Executed
  live** against `merged_openapi_spec 1.yaml` → `proposed 0 triple(s)` — the README's 0-proposals
  disclosure is fact, and the spec's opaque-`HttpEntity` characterization is corroborated (230
  occurrences).
- **V11** Demo wiring complete as claimed: `trainticket-demo.properties:479-480` +
  `trainticket/target-triples-demo.yaml` (2 triples, no eval scaffolding) + `jaeger.base.url` set
  (`:352`); Quick Start A callout's step text matches the emitted step verbatim
  ("✅ durable write confirmed [...] — read-back shows it (N poll(s), M ms)").
- **V12** Properties-file keys (incl. `.registry`, `.failonlost`, `jaeger.base.url`) land in System
  properties at launch (`MistMain.java:89-125`), so "in your properties file" instructions are
  correct — corroborated by the live DoD run record (`debug/a-main/c2c3/ux-demo-dod-result.md`).
- **V13** All three section anchors resolve (slug byte-verified, see charge answer 1); the README's
  other relative link targets exist (`REPRODUCE.md`, `LICENSE`, `.idea/runConfigurations/`,
  `debug/Conference-refinement/PATH_B_REBUILD_PLAN.md`, `mist-llm/.../LLMConfig.java`).
- **V14** Registry parser is strict + loud (unknown/missing keys, non-GET read-back, duplicate
  names, all cross-field rules → errors naming allowed keys), so mis-authored registries fail fast
  rather than silently (`TargetTripleRegistry.java:173-187,238-319`) — the README's "guard rails"
  framing is if anything understated.
