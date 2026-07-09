# COLD REVIEW B — corpus dependency analysis + plan soundness (`c3-case-corpus-plan.md` + checklist deltas)

**Reviewer:** independent cold reviewer B (no shared context). **Date:** 2026-07-09.
**Scope per charge:** (1) §2 dependency truth vs plan v2 §3.1–3.3/§5 + rater materials §0/§6; (2) the
§2.2 "seed calibration subset NOW" claim vs what actually exists on disk; (3) the §5 B4 harness spec
vs the §0 blindness invariants; (4) the checklist §1.95 / step-5 entry-gate deltas; (5) cross-doc
consistency incl. whether the rev-2 schema carries the artifacts B4 consumes.

## VERDICT: **ACCEPT-WITH-CHANGES**

The macro answer to the user's question is CORRECT and well-grounded: the corpus BODY (S3 wild,
M-yield audit, SUT-balanced calibration) is an output of steps 2–5 and cannot precede them; the
corpus FACTORY (B4 harness) has no deploy dependency and belongs on a NOW track; raters start at
step 5 by construction. The S3/M-yield/calibration-breadth dependency claims all verify against the
governing plan. **But the §2.2 "seed calibration subset NOW" claim is hollow as written** — every
existing asset it names is either `capture_status: specified` (no artifacts at all) or a
verdict-level tool-vocabulary summary lacking the three things a rater-facing case must show — and
the B4 spec is missing its own input-format contract, several §0 strip invariants, and the checklist
patch left items dual-homed and the entry gate incomplete. All fixable inside the proposed structure;
none of it reverses the build-order decision.

---

## [BLOCKING]

### B1. The §2.2 seed-subset claim is hollow: no existing asset carries the rater-facing raw material, and B4's input format is undefined
`c3-case-corpus-plan.md:36-39` claims the ~10 reviewed assets can be rev-2-migrated + B4-normalized
NOW into ~8–12 calibration cases + the 2 eligibility cases. A rater-facing case must carry "the
request sequence performed, the observed durable state" and "the system's response(s)"
(`c3-rater-materials.md:35-37`, `:53-55`). What actually exists:

- **All 6 v0.1.0 seed cases are `capture_status: "specified"` with every provenance artifact null**
  (`debug/a-main/benchmark/cases/TT-adminroute-lostwrite-001.json:6` + `:49-55`
  `control_trace/fault_trace/readback_response: null`; same at line 6 + the provenance block of all
  five siblings — verified by grep). They are label-by-construction DESIGNS, not observations.
- **The G3 TT cancel/agreement cell artifacts are verdict-level summaries in tool vocabulary**
  (`debug/a-main/g3-comparator-tt/runs/prefunded-run3-v105.log`, `agreement-run3-gated.log`:
  "MIST B2 (differential value-delta): FIRE", gate names, poll counts, and a two-line balance
  summary). No request payloads, no raw response envelopes, no read-back bodies. Same for the SS
  shipping runs (`debug/a-main/g3-comparator-ss/runs/shipping-h2h-reps-n5-bqf1d09h4.txt`: queue
  `baseline=1 -> final=2` inside verdict lines).
- **The richest machine artifact — `debug/a-main/prep/gate1-run3-report.json` `pairs[].fault` —
  still lacks two of the three elements:** it has `baselineBody`/`lastReadbackBody` (observed durable
  state: YES) but only `stepKey='POST /api/v1/adminrouteservice/adminroute'` +
  `isolationKey` (a business-key subset) for the request — **no request payload, no surrounding
  sequence** — and only `ackHttpStatus`+`ackBodyStatus` (an int) — **no full ack body**. This mirrors
  the recorder itself: `DataIntegrityRuntime.RunRecord`
  (`mist-cli/src/main/java/io/mist/cli/fault/DataIntegrityRuntime.java:89-104`) has no ack-body or
  request field; `beforeWrite(stepKey, requestBody)` (`:404`) sees the request transiently and does
  not record it. The W0 observe-session record API (checklist 1.9.2) emits the same RunRecord shape.
- **Pre-pin artifacts violate the freeze anyway:** `c2-freeze.md:96` + invariant `:149` pin ONE
  study-wide `mist_commit` across every case, and the U7 amendment row (`c2-freeze.md:259`) plus
  checklist 1.9.5 explicitly require "promoted G1/G3 seeds re-recorded at the pin". The July-2/3
  run artifacts predate the 2026-07-09 pin. §2.2 is silent on re-recording — it implicitly contradicts
  the freeze it must comply with.
- **The rev-2 schema does not type the artifacts B4 consumes** (charge 5): `c2-freeze.md:129-133`
  gives only `artifacts.raw_logs: [<paths>]` (untyped, @eval) + verdicts (which B4 must strip);
  `stimulus.script` (`:75`) is executable code, not a declarative request list; `oracle_eval.readback`
  (`:82-87`) is the LOCATOR of the observable, not the observation. So §5's "deterministic transform:
  case file + raw run artifacts → rater-facing case" (`c3-case-corpus-plan.md:31-33`, `:67-68`) has
  **no defined input format** — it is currently unimplementable as specified, and each producer
  (pairing report, shipping harness stdout, future wild detectors) emits a different shape.

**Fixes (all inside the NOW track; none moves the build order):**
1. **First 1.95.1 deliverable = pin the rater-artifact sidecar format** (per case: ordered request
   records incl. method/path/payload; response records incl. status + full body; durable-state
   observations = read-back/probe bodies with RELATIVE times; producer + mist_commit stamped). Point
   `artifacts.raw_logs` at it (no frozen-key change needed; add a §6 amendment row documenting the
   format's adoption for hygiene).
2. **Restate §2.2 honestly: seed subset = rev-2 migration + SHORT CAPTURE RUNS at the pinned commit**
   against the already-deployed TT (TT is up per checklist 1.9.3/2.6) + SS re-warmed (post-reboot
   RabbitMQ runbook), with harness-level transcript capture so the MIST pin stays intact. Flip those
   cases `specified → captured` (`c2-freeze.md:49`); only then are they fixtures/calibration cases.
   Note the tenancy wrinkle: big-SUTs-solo means SS capture needs a window when TT is scaled down (or
   an accepted co-residence risk on 26 GB) — schedule it, don't discover it.
3. **Decide capture ownership for detector-(ii) wild runs before step 5:** writer/test-level capture
   is MIST tool code → a disclosed pin amendment; harness/proxy-level capture keeps the pin. Either
   way the decision is a step-5-shaping dependency the corpus plan currently misses (see M1).

---

## [MAJOR]

### M1. S3 pipeline misses a flag-time capture requirement (a MISSED dependency inside step 5)
Traces are rater-INADMISSIBLE (`c3-rater-materials.md:107-109`), yet wild detector (i) is the
trace-shape oracle (`c2c3-execution-plan.md:168-169`) which performs no read-back. A detector-(i)
flag therefore yields NO "observed durable state" — the §0-required third element — unless the
step-5 pipeline captures a durable-state probe + the request/response at flag time.
`c3-case-corpus-plan.md:23-25` and checklist `:131-134` specify detectors and workloads but not the
**wild-flag capture bundle**. Without it, S3 cases cannot be rendered rater-facing no matter how good
B4 is. Fix: add the capture bundle (request sequence, response, durable-state probe, relative times)
to the step-5 detector spec and make it the same sidecar format as B1-fix-1.

### M2. Corpus plan §4 contradicts its own §2.2/§6 — and the governing plan — on seed-migration timing; checklist items are now dual-homed
`c3-case-corpus-plan.md:58` says "✔ correct: seed migration at 2.75", while §2.2/§3/§6 move seed
migration to the NOW track. The governing plan already put "Promote the ~10 reviewed existing assets
into cases" at §2.4 step **2 — BEFORE the deploy waves** (`c2c3-execution-plan.md:109-110`), so the
NOW-track move actually RESTORES the plan's ordering and §4's endorsement of 2.75 is wrong on both
counts (`benchmark/README.md:166-167` "Migration is a step-2 task" is a third, also-stale timing).
The checklist patch then left both items dual-homed with no cross-reference: seed migration at
1.95.2 (`step2-execution-checklist.md:40-42`) AND 2.75 (`:98`); B4 build at 1.95.1 (`:36-39`) AND
step 5 (`:137-139`). Fix: correct §4's verdict line; single-source each item (2.75/step-5 entries
become "moved to 1.95 — VERIFY done, same harness version" pointers); amend README §9's timing
sentence.

### M3. B4 invariant list is incomplete vs §0's full negative list — the case file itself is a leak vector
`c3-case-corpus-plan.md:72-73` tests: no tool strings, no clean-run column, opaque ids, pointers
resolve, deterministic bytes. §0 additionally bans: **traces, hypothesis labels, "expected
observable" annotations** (`c3-rater-materials.md:37`). The B4 INPUT (a rev-2 case) carries
`label.value`, `fault.*` (mechanism/injection!), `oracle_eval.readback.expect_with_fault`,
`oracle_expectation.*`, and a fault-describing `title` (`c2-freeze.md:44-134`) — any of which, if
leaked into `case.md`, destroys blindness or hands the rater the answer. Fix: add an explicit
STRIP-LIST invariant (label, fault, oracle_eval expectations, negative_control, title, trace
attachments) with a leak-fixture test; state that S3 inputs arrive label-less and the transform never
reads label fields; add "artifact richness/format uniformity across strata" to the step-5 tell audit
(different upstream producers must not yield distinguishable case shapes).

### M4. Absolute timestamps are an unaudited stratum tell (and the determinism risk is B4's own clock, not the artifacts)
Raw artifacts carry wall-clock times (`generatedAtEpochMs`, capture dates); calibration is captured
months before S3 by construction, so absolute dates CLASSIFY stratum — a tell not in §0's list
("SUT / endpoint / response-shape mix", `c3-rater-materials.md:34`) nor in §5's invariants.
"Deterministic bytes" is achievable since inputs are fixed once captured — provided B4 never stamps
its own `now()`. Fix: B4 normalizes absolute times to relative offsets (KEEP relative durations —
they are judgment-relevant for documented eventual-consistency windows), forbids own-clock output,
and the step-5 tell audit adds a timestamp check.

### M5. Step-5 corpus-assembly entry gate is missing four cheap, load-bearing checks
`step2-execution-checklist.md:140-143` has same-harness-version, tell-audit, sealed manifest, hash
freeze. Missing: (a) **rubric version pinned into the sealed manifest** — the κ-gate allows up to two
rubric iterations with full relabeling (`c3-rater-materials.md:153-156`); the relabel-all audit trail
needs the corpus↔rubric-version binding; (b) **machine disjointness check**: calibration ∩ S3 ∩
M-yield-audit ∩ eligibility-screen = ∅ by true id (m7 states the rule at `:206-207`; the gate never
CHECKS it, and §2.2 mints both calibration and screen cases from the same seed pool); (c) **every
rated case `capture_status == captured`** (`c2-freeze.md:49` — a `specified` case has nothing to
rate); (d) **IRB determination RECEIVED** — B-M8 makes it a precondition before labeling
(`c3-rater-materials.md:186-193`); 1.9.6 files it, but the gate that admits raters never verifies
receipt. Fix: four checkboxes on the entry gate.

### M6. Docs/spec/source pointers under-specified + a real blindness collision with in-flight upstream filings
§5's "per-case docs/spec/source pointers resolve" (`c3-case-corpus-plan.md:73`) leaves open WHAT they
resolve to. Live URLs drift from the pinned SUT version (breaking the norm derivation), and — sharper
— the plan REQUIRES upstream filing of genuine finds DURING steps 4–5 (`c2c3-execution-plan.md:39`,
`:165-166`): a rater who follows repo links or searches the SUT's issue tracker can find OUR
tool-named report describing the very behavior they are rating. §1's "please do not seek them out"
(`c3-rater-materials.md:70-73`) covers tool output, not this. Fix: per-SUT **version-pinned doc/spec/
source bundles** (vendored snapshot or pinned-commit URLs) as the ONLY rater-provided pointers; a
rater rule "provided bundle only, no web search"; and for any case in the rated set, upstream filings
are either deferred to study close or de-identified (behavior-only, no tool name) until close.

---

## [MINOR]

- **m1. Calibration-size drift:** ~20 (`c2c3-execution-plan.md:138`; `c3-rater-materials.md:24`) vs
  ~30 (`c3-rater-materials.md:150`; `c3-case-corpus-plan.md:15`; checklist `:144`), and the brief's
  "up to 60" cap (`c3-rater-materials.md:45`) breaks at 30 calibration + 40 S3 + audit sample (the
  `:66-68` scaling sentence self-heals pay/hours, not the stated cap). The corpus plan claims to be
  the one authoritative view — reconcile the numbers there (30 is the M1-fix value; update §0/§3.1
  echoes and the cap arithmetic).
- **m2. Missing dependency edge in the §3 diagram:** both wild detectors must be FP-calibrated ON THE
  S2 STRATUM before S3 sampling (`c2c3-execution-plan.md:170-171`) → step-5 S3 also depends on
  step-3a S2 population; `c3-case-corpus-plan.md:44-51` shows 3a feeding only calibration.
- **m3. Missed NOW-track opportunities (false-dependency hunt, inverse direction):** TT/SS S2
  designed-degradation capture needs no step-2 deploy (both SUTs exist); the §0 tell-audit SCRIPT and
  the rubric §3 worked examples ("to be authored on real calibration cases",
  `c3-rater-materials.md:110`) can be built with B4 against the seed subset. None blocks step 2;
  all de-risk step 5.
- **m4. "Deterministic output" vs "random" opaque ids** (`c3-case-corpus-plan.md:70-73`): random ids
  break same-input→same-bytes. Use keyed HMAC(true-id, sealed salt) with the salt in the sealed
  manifest — deterministic, still non-decodable by raters.

---

## Verified correct (checked against sources; no action)

1. **S3 requires step 2 + 2.5 + 2.75 + step-5 workloads — TRUE.** Detector (i) is the trace-shape
   oracle "on step-2.5-instrumented SUTs"; detector (ii) single-leg read-back-absence is MIST's
   observe mode → needs the per-SUT enablement package (registry/auth/triples) = 2.75; workloads
   pinned 12 h / 500-write at step 5 (`c2c3-execution-plan.md:167-175`). Bonus accuracy: detector
   (ii)'s decisive gate also leans on tracing (U3, checklist `:75-81`) — 2.5 is in the dependency
   list either way. No deploy → no flags → no S3: sound.
2. **M-yield audit is correctly step-4-gated** (`c2c3-execution-plan.md:160-166`; checklist
   `:126-128` "1 representative + 10% audit sample → feeds the rater M-yield audit set").
3. **Calibration breadth genuinely wants step 3a — the distributional-tell argument holds.** §0's
   audit demands SUT mix not correlate with stratum (`c3-rater-materials.md:33-34`); TT/SS-only
   calibration + 6-SUT S3 would make "new-SUT case ⇒ not calibration" a rater-usable classifier.
   The ~30-balanced sizing traces to the §6 M1 fix (pooled ≥ 50 free given S1+S2 ≥ 80).
4. **B4 has zero deploy dependency; moving it off step 5 is right** — and B1/M1 make it MORE
   upstream than the plan says (its input format shapes the step-5 detectors' emission).
5. **Raters-start-at-step-5** matches plan §5 sequencing and §3.3's sampling rule min(all flagged,
   40) + the <20-flags ⇒ scarcity-is-the-finding branch (`c2c3-execution-plan.md:179-184`), echoed
   correctly in corpus plan §1.
6. **The step-5 entry-gate items that ARE present** (same-harness-version, final-mix tell audit,
   sealed manifest not shipped to raters, corpus hash freeze) match §0/§5 and close the gap the
   corpus plan correctly identified as missing from the pre-patch checklist.
7. **Eligibility screen disjointness rule (m7)** consistently stated corpus-plan §1 (`:18-19`) ↔
   materials §9 (`:206-207`) — the gate CHECK for it is M5(b), the rule itself is consistent.
8. **§1.95 harness-dev is tenancy-safe** (pure code, no cluster load) — the collision risk is only
   in B1's capture runs, addressed there.

## Disposition summary
| finding | severity | one-line fix |
|---|---|---|
| B1 seed-subset hollow + B4 input format undefined | BLOCKING | pin sidecar artifact format; seed subset = migration + capture runs at the pin; decide detector-(ii) capture ownership |
| M1 wild-flag capture bundle missing | MAJOR | add flag-time request/response/durable-state capture to step-5 spec |
| M2 §4 self-contradiction + dual-homed checklist items | MAJOR | fix §4 line; single-source 1.95 vs 2.75/step-5; amend README §9 |
| M3 strip-list invariants incomplete | MAJOR | add label/fault/expectation/title strip tests + format-uniformity tell |
| M4 absolute-timestamp tell | MAJOR | relative offsets; no own-clock; add to tell audit |
| M5 entry-gate gaps | MAJOR | + rubric-version pin, disjointness check, captured-only check, IRB-received |
| M6 doc-pointer bundles + upstream-filing collision | MAJOR | pinned per-SUT bundles; bundle-only rule; defer/de-identify filings for rated cases |
| m1–m4 | MINOR | as stated |
