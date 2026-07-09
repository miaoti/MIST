# COLD REVIEW B — COMPLETENESS + CONSISTENCY of the step-2 checklist + UX design

**Reviewer charge:** find what was FORGOTTEN. Artifacts: `debug/a-main/c2c3/step2-execution-checklist.md`
(the forward manifest) + `debug/a-main/c2c3/mist-ux-design.md` (the UX design it gates on). Diffed against:
plan v2 (`c2c3-execution-plan.md`) §1–§5, `c2-freeze.md` rev 2, `e-sut-applicability-matrix.md` rev 2,
`c2-depth-survey.md`, `benchmark/README.md` §9, `c2-license-audit.md`, `c3-rater-materials.md`,
`REVIEW-STEP1-FREEZE-RECONCILIATION.md`, `r4-comparator-spike.md`; UX claims verified against source
(MistRunner, DataIntegrityRuntime, MultiServiceRESTAssuredWriter, MstAuthHandler, demo properties, the
pinned test suites) and against live cluster state (kind "mist" up, sock-shop Running, **`trainticket`
namespace EMPTY** — checked 2026-07-08).

## VERDICT: **ACCEPT-WITH-CHANGES**
The checklist's step skeleton (1.9→8) faithfully covers plan v2 §5, the deploy riders transcribe the
survey's verify-at-deploy list completely, and the UX design's §1 code citations all verify. But the
checklist **mis-schedules the single longest-lead item (rater outreach/IRB) to step 5**, omits two
mandated reviews, places the study-commit pin too late, and drops ~a dozen binding per-case/reporting
obligations — exactly the "forgotten during the UX detour" class it exists to prevent. The UX design
has four mechanism-level gaps (paired-run interference, session activation, expert-tier reachability,
a precision-inheritance wording that contradicts plan §3.2). All fixable by edits; none is structural.

---

## A. BLOCKING findings

**[BLOCKING] B1 — Rater outreach + IRB are mis-scheduled to step 5; plan v2 schedules them at step 1, in parallel.**
`step2-execution-checklist.md:97-98` files "**USER-GATED (open):** rater channel + IRB determination …
outreach lead 2–6 wk" under **Step 5**. Plan v2 §5 (`c2c3-execution-plan.md:212-213`) puts "rater
outreach (2–6 wk lead, ∥)" in **step 1**; `c3-rater-materials.md:185` says "the §7 recruitment starts
now precisely to avoid it [the author-blind fallback]"; the IRB determination is a **precondition
before any rater is contacted** (`c3-rater-materials.md:172-179`, B-M8) and "can add weeks onto the
longest-lead item". On the 10–13 wk single-box timeline, raters are needed at ~wk 6–8; deferring the
channel decision + IRB filing to step 5 makes the pre-committed-scars fallback (§8: abstract demotion)
the DEFAULT outcome, silently. **Fix:** move to a new checklist item parallel to 1.9 ("surface channel
decision + IRB filing to the USER now; outreach clock starts before step 2"), keep only the
adjudication execution under step 5.

**[BLOCKING] B2 — The mandated ≥3-cold-review of the rater materials before contact is missing entirely.**
`c3-rater-materials.md:9-11`: "To be ≥3-cold-reviewed before any rater is contacted (soundness-critical:
the whole C3 precision claim rests on §0)." No checklist line anywhere schedules this review. It must
precede outreach, i.e. it belongs in the same early slot as B1. **Fix:** add it as the gate on outreach.

## B. MAJOR findings — checklist completeness

**[MAJOR] M1 — C3-results and E2-results ≥3-cold-reviews omitted.** Plan §6
(`c2c3-execution-plan.md:241-243`): "the benchmark artifact **and the C3/E2 results each get their own
≥3-cold-review** at their §2.4/§3 acceptance gates." The checklist has only the benchmark
sampled-reproduction review (`step2-execution-checklist.md:114`). Add review gates at the end of step 5
(C3) and step 6 (E2).

**[MAJOR] M2 — MIST study-commit pin is placed at step 8; it must exist before the first verdict-recording run, and its criteria are stale.**
`step2-execution-checklist.md:112-113` puts "MIST study commit PINNED (criteria: value-delta + supplied
hooks + injectors + fabricated-ack …)" under E6 packaging. The freeze requires ONE commit IDENTICAL
across every case (`c2-freeze.md:95,145`; plan `c2c3-execution-plan.md:86-87`: "oracle drift invalidates
comparability") — verdicts start being recorded at steps 3a/4, so a step-8 pin is retroactive
bookkeeping that cannot prevent drift across steps 3–7. Also the B-m3 criteria list predates the UX
wave: `mist-ux-design.md:32-35` now declares the **product observe mode IS C3's single-leg wild
detector** (plan §3.2 instrument (ii)), so the pinned commit must contain the 1.9 bridge/observe code
too. **Fix:** pin at the end of step 1.9/2.75 (before any step-3 run), criteria += "the 1.9
observe-mode + Allure-bridge wave".

**[MAJOR] M3 — OTel-Demo OpenAPI spec authoring dropped.** Plan §4 E1
(`c2c3-execution-plan.md:187-188`): "author **TeaStore + OTel-Demo** OpenAPI specs (released with the
benchmark, pre-registered as authored — review B m5)". Checklist 2.75 authors only TeaStore's
(`step2-execution-checklist.md:61`); the OTel-Demo bullet (`:62-63`) lists registry/auth/triples but no
spec. E1-THIN on OTel-Demo (3 baseline tools) and the D2 triples proposal both need it.

**[MAJOR] M4 — B4 blind-label harness has no build item.** Plan §3.1 (`c2c3-execution-plan.md:125-126`)
names the B4 harness as C3 machinery; `c3-rater-materials.md:23-27,30-37` requires all rater-facing
cases in "ONE common format (B4 harness output)" with opaque ids, clean-run stripping, and interleaving.
That is real engineering (case normalizer + ballot packaging) with no home in any checklist step.
**Fix:** add to step 4/5 prep (must exist before the calibration round).

**[MAJOR] M5 — Freeze §5's A-M8 S1-genuineness disclosure dropped.** `c2-freeze.md:232-236`: for S1
cases whose durable write is plausibly best-effort (**OTel checkout→Kafka→accounting flagship
especially**), attach contract-grounding evidence OR disclose the case rests on the construction bar.
Not in the step-3a per-case bullet (`step2-execution-checklist.md:75-76`). This is a per-case authoring
obligation that will be forgotten exactly at population time.

**[MAJOR] M6 — License conduct rules absent at their point of use.** `c2-license-audit.md:44-49`
(binding conduct rules): (1) **zero lines copied from train-ticket-fault-replicate — replicate-by-
description only** (binds the step-3a F-corpus builds, `step2-execution-checklist.md:68-71,78-79`, where
only in-class verification is mentioned); (2) never re-push third-party images (binds E6 + any registry
use); (3) Apache-2.0 §4 change notices on fork diffs/modified manifests. Only rule 4 (standalone repo)
made it in (`:110`). A forgotten rule 1 legally taints the F-corpus. **Fix:** add rule 1 to step 3a's
F-corpus line; rules 2–3 to the standing-constraints footer.

**[MAJOR] M7 — M-prevalence reporting obligations diluted.** Plan §3.2
(`c2c3-execution-plan.md:163-173`) pins: the **estimand** (detector-conditioned LOWER bound), **detector
recall on S1 reported as the qualifier**, **two denominators (per-request, per-endpoint)**, named
per-SUT workload sources, and **workload scripts versioned in the benchmark**. Checklist step 5
(`step2-execution-checklist.md:93-96`) keeps only the 12 h/500-write pin + write-fraction. These are the
claims-hygiene items for the paper's only prevalence number.

**[MAJOR] M8 — 1.9.3 demo-run DoD has an unscheduled TrainTicket deploy dependency (verified live).**
`step2-execution-checklist.md:22-23` requires "TrainTicket demo properties with B2 enabled end-to-end →
Allure report shows the data-integrity section". Live check: kind "mist" is up with sock-shop Running,
but **`kubectl get pods -n trainticket` → "No resources found"** — TT is NOT deployed. A TT redeploy is
~20–30 min quick_start + tenancy (big-SUT-solo ⇒ scale sock-shop to 0) — none of which the checklist
schedules before/inside 1.9 (step 2.1's WSL note is "before any TT wave", implying TT waves are later;
WSL currently shows ~25 Gi total so memory is fine). **Fix:** state what the DoD runs against and
schedule the deploy inside 1.9.3 (incl. Jaeger reachability — see u6), or explicitly re-point the first
DoD at the currently-up Sock Shop carts triple and gate the TT demo at the step-2 TT wave.

## C. MAJOR findings — UX design consistency + blast radius

**[MAJOR] U1 — "the product inherits a calibrated precision story" contradicts plan §3.2's pre-registered non-inheritance rule.**
`mist-ux-design.md:36-39` vs `c2c3-execution-plan.md:164-166`: the wild detectors' "FP profiles are
**NOT inherited from the paired-mode zeros** (those are scoped to paired/probe modes)". The G1 FP-0.0
(0/2127) was measured under the paired protocol; observe mode's FP is measured only at the step-5 S2
calibration. The design's own second clause ("C3's S2-FP calibration covers exactly this mode") is the
correct statement — the "inherits a calibrated precision story" clause is the forbidden one. **Fix:**
reword to "inherits the firing DISCIPLINE (rule identity); its measured precision figure comes from the
C3 S2 calibration", and add a rule that neither docs nor paper quote 0.0 for observe mode.

**[MAJOR] U2 — W1's emitted end-of-write check will fire inside PAIRED eval runs unless explicitly gated; the "eval harnesses untouched" constraint has no mechanism.**
Generated test code is SHARED between product and eval runs. `PairedFaultExecutor.java:184,200,431`
brackets the SAME JVM with `beginRun`/`endRun`, and in a fault leg `OBSERVED_COMPLETE_ABSENT` is the
EXPECTED outcome — with `failOnLost=true` (`mist-ux-design.md:45-46`, default true) the new check fails
the test mid-scenario, aborting subsequent steps and changing the run-record stream (orphan-detection /
poll-through / f2FailedFlags dynamics) versus the reviewed G1/G3 behavior. `mist-ux-design.md:87-88`
asserts "no behavior change to the eval harnesses (G1/G3 reproducibility)" but no work item implements
that. **Fix:** the emitted check must no-op (or annotate-only) when a paired/eval session is active (or
the executor sets failOnLost off for its legs), and W1's DoD must name the regression test: paired
fault leg with LOST ⇒ test does NOT fail ⇒ executor tallies unchanged.

**[MAJOR] U3 — Observe-mode session activation is a missing work item, and its placement is the whole ballgame.**
With no active session every hook is a passthrough no-op (`DataIntegrityRuntime.java:29-31`, guards at
`:332,389,514`; pinned by `DataIntegrityRuntimeTest.java:133` `inactive_hooksArePassthroughNoops`) — on
today's product path **no verdict is computed at all** (design §1's "verdicts live on an in-process
record holder" mildly overstates: that holder is only populated inside harness-bracketed sessions).
Someone must begin/end an observe session on normal runs; W1–W5 (`mist-ux-design.md:79-88`) never name
this item. Constraints discovered in source: `beginRun` **throws** if a session is already active
(`DataIntegrityRuntime.java:281-283`) and **refuses to arm** when `mst.test.parallelism>1`
(`:289-296`) — so activation must live in MistRunner's non-paired execution branch (MistRunner already
branches on `FaultInjector.enabled()`, `MistRunner.java:531`), never in generated code (else paired runs
collide and throw), and observe mode must degrade gracefully (warn + skip, not crash) on parallel runs.
Also note `MistRunner.java:328` hard-fails when enabled without a registry — fine while
`mst.oracle.dataintegrity.enabled` stays default-false (`MstConfig.java:424`); the design should state
explicitly that D3's "default" means default-MODE-when-enabled, not enabled-by-default (§6 Q4).

**[MAJOR] U4 — The "expert tier" is not reachable from the product path today; D2/D4 as written oversell it.**
`mist-ux-design.md:64-66` presents value-delta/supplied-isolation as "manual by design… documented as
the expert tier". But the WRITER never emits supplied-mode hooks: `beforeWriteSupplied` callers are only
the fault package + the g3 harnesses (DataIntegrityRuntime, TargetTripleRegistry, AccountCreateAgreement,
CancelRefundHeadToHead, ShippingEnqueueHeadToHead — no writer), and
`DataIntegrityEmissionTest.java:138-143` PINS that a bodyless matching step is left UNHOOKED. So a
hand-authored expert triple for the paper's centerpiece class (TT cancel→refund, bodyless GET) does
NOTHING on a `java -jar mist.jar` run. Either (a) a W-item adds supplied/value-delta emission to the
writer — a materially larger blast radius than stated (emission pins change; and the reviewed
value-delta RUNBOOK requires per-leg-fresh users, which single-leg wild observe mode cannot guarantee —
an unresolved soundness question for wild value-delta verdicts), or (b) D2/D4 must say honestly that the
expert tier currently runs via the harness path, and observe mode excludes expert-tier defect verdicts
(or marks them NOT_EVALUABLE) unless the isolation preconditions hold. Decide in the design, not during
implementation.

## D. MINOR findings

- **[MINOR] m1 — "distinct sites <20 → disclosed finding, stop-and-replan per plan §2.3"
  (`step2-execution-checklist.md:71-72`) conflates two different rules and mis-cites.** Freeze rev 2
  (`c2-freeze.md:224-226`) and matrix (`e-sut-applicability-matrix.md:52-54`) say <20 sites = **disclosed
  finding, continue** (the shortfall branch was pre-registered precisely because the honest count may
  land low-20s); plan §2.3 contains no stop-and-replan (that language is §5 R5 / Gate-4 <3). State which
  rule governs (the freeze) — ambiguity at the decision moment is the risk.
- **[MINOR] m2 — per-case authoring bullet (`:75-76`) omits `oracle_expectation` columns (the R2-adopted
  anti-circularity mechanism, `c2-freeze.md:96-103`) and running the rev-2 JSON validator on every NEW
  case (the checklist requires validator PASS only for the 6 migrated seeds, `:65`).**
- **[MINOR] m3 — statistics obligations compressed away:** κ<0.6 two-iteration relabel rule, PABAK/Gwet
  AC1, κ CI + raw agreement, per-SUT Wilson/Clopper–Pearson rules, Holm/Bonferroni across SUT×tool
  grids, M-yield's retained ≤5% CI-half-width target, CI units = distinct sites (plan §3.1
  `c2c3-execution-plan.md:145-152`). One pointer line in step 5 suffices — the checklist's purpose is recall.
- **[MINOR] m4 — §8.5-5's four soundness-threat disclosures (E2 chapter writing obligation, plan
  `c2c3-execution-plan.md:50,203`) and the two G3 standing framing rules
  (`g3-comparator-tt/REVIEW-HEADTOHEAD-RECONCILIATION.md:32-39`) missing from the paper-honesty footer
  (`step2-execution-checklist.md:121-123`).**
- **[MINOR] m5 — camera-ready watch-list (`c2-freeze.md:37`: re-check OpenRCA 2.0 + FP-aware-TT release
  forms before submission) has no home.**
- **[MINOR] m6 — S3 upstream-filing acceptance (plan §3.3 `c2c3-execution-plan.md:178-179`: filings
  attempted for every genuine WILD find) appears only under step 4 M-yield (`:91`).**
- **[MINOR] m7 — blindness distributional-tells audit (`c3-rater-materials.md:33-34`: SUT/endpoint/
  response-shape mix must not correlate with stratum) absent from step 5's blindness line (`:99`).**
- **[MINOR] m8 — E1 tool-crash/timeout accounting (plan §4 `c2c3-execution-plan.md:190-191`) absent from
  3b; THIN-tier "3 × 30 min" (`:82-83`) leaves tools-vs-seeds ambiguous (plan: 3 SEEDS × 30 min).**
- **[MINOR] m9 — arm-3's benign-trap FP datum (plan §4 `c2c3-execution-plan.md:196-198`: authoring cost
  AND its benign-trap FP) absent from step 6 (`:103-105`); threshold-sweep vs single-(P,R)-point matched-
  recall definition also unstated.**
- **[MINOR] m10 — 2.5.6 trace source unstated:** S1 population is step 3a (after 2.5), so the
  TraceAnomaly empirical row must run on the already-committed G1/G3 trace archives or fresh post-2.5.1
  captures, plus the format conversion the spike names (`r4-comparator-spike.md:99-100`). Say which.
- **[MINOR] m11 — SS never gets the "NEW 1.9 user-flow" DoD:** 2.75 covers only the four new SUTs; TT is
  covered by 1.9.3; Sock Shop (FULL-tier E1 + M-yield + M-prevalence) gets no observe-mode end-to-end
  check anywhere. Cheap to add while SS is up (cookie auth exists: `MstAuthHandler.java:27-30,112`
  `per_jvm_cookie`); the post-reboot RabbitMQ warm-up rule (`:39-40`) applies.
- **[MINOR] u5 — the bundled demo registry D4 points at carries eval-only scaffolding:**
  `mist-cli/src/main/resources/My-Example/trainticket/target-triples.yaml` embeds `fault_flag` blocks +
  a `cluster: context: minikube` block (minikube is stopped per the runbook) and a stale flag name in
  its header comment ("mist.fault.injection.enabled" vs the real `mst.oracle.dataintegrity.enabled`,
  `MstConfig.java:424`). The design's own rule (`mist-ux-design.md:26-27`) says injector scaffolding
  "must NEVER be presented as the product path" — ship a product-clean demo registry or document those
  keys as eval-only/ignored in observe mode.
- **[MINOR] u6 — D1/D4 must state the observability precondition:** `OBSERVED_COMPLETE_ABSENT` requires
  the step's own Jaeger trace present with a stable span set (`DataIntegrityRuntime.java:42-47`). A user
  without tracing can NEVER get a defect verdict — only TIMEOUT_ABSENT warnings. Docs + the run-summary
  should surface "observation gate unavailable" so precision-first does not silently become never-fires.
- **[MINOR] u7 — name the pinned suites in W1's DoD:** `writer/DataIntegrityEmissionTest` (4 tests —
  incl. the flag-off byte-identity pin `:130-135` which must NOT change, and the triples-ON emission
  regexes `:98-106` which WILL legitimately change), `writer/QueryParamEmissionTest`,
  `fault/DataIntegrityRuntimeTest` (incl. the inactive-passthrough pin `:133`),
  `fault/PairedFaultExecutorTest`, `fault/TargetTripleRegistryTest`, the three injector tests, and the
  three g3 suites (currently 117 fault + 16 g3 + 7 writer tests). Distinguishing "expected pin update"
  from "must-not-change pin" up front prevents a lazy regex loosening.
- **[MINOR] m12 — uncommitted assets in git status** (`evaluation/suts/sockshop/input-fetch-registry.yaml`,
  `root-api-registry.json`, `traces/sockshop_addresses.json`, `traces/sockshop_cards.json`,
  `mist-cli/src/test/java/trainticket_gate1_pairing/`) — commit or triage before the UX wave rebases
  anything; "nothing forgotten" includes the working tree.

## E. Forgotten-items list (the deliverable, consolidated)
1. Rater channel + IRB decision surfaced to the USER **now**, parallel to 1.9 (B1).
2. ≥3-cold-review of `c3-rater-materials.md` before any outreach (B2).
3. C3-results ≥3-cold-review (end of step 5) + E2-results ≥3-cold-review (end of step 6) (M1).
4. Study-commit pin moved to end-of-1.9/2.75, criteria += the UX wave (M2).
5. OTel-Demo OpenAPI spec authoring (pre-registered-as-authored) in 2.75 (M3).
6. B4 blind-label harness build (format normalizer + ballots + opaque ids) before step-5 calibration (M4).
7. A-M8 contract-grounding attachment/disclosure for best-effort S1 cases (OTel flagship) in 3a (M5).
8. Zero-copy/replicate-by-description rule on the F-corpus builds; image re-push ban; Apache-2.0 §4
   change notices (M6).
9. M-prevalence: estimand wording, S1-recall qualifier, per-request + per-endpoint denominators, named
   workload sources, workload scripts versioned into the benchmark (M7).
10. 1.9.3 DoD target: schedule the TT deploy (or re-point at SS) — TT is not currently deployed (M8).
11. Observe-mode session-activation work item + paired-run no-op gating + regression tests (U2/U3).
12. Expert-tier reachability decision: writer emission vs harness-only-documented (U4).
13. The m1–m12 pointer items above (statistics, oracle_expectation+validator, framing rules,
    watch-list, wild filings, tells audit, crash accounting, arm-3 FP, trace source, SS DoD, demo
    registry hygiene, Jaeger precondition, named suites, working-tree triage).

## F. Verified-OK (no action; recorded so the reconciliation doesn't re-litigate)
- Checklist step skeleton = plan v2 §5 exactly (1.9 insertion disclosed as user-directed); single-box
  directive stated (`:6-7`); de-scope ladder referenced; tenancy/26GB/CRLF/RabbitMQ runbook carried.
- S1 quotas match survey+matrix+freeze (TeaStore 4–5, OTel 4–5, Boutique 1 disclosed-minor, F-corpus
  ≥6→10 in-class-verified, S2 ≥35 with TT/SS enumeration, both denominators, tell-free tally R8).
- Verify-at-deploy riders fully transcribed (checklist 2.2–2.5 = `c2-depth-survey.md:170-174` list).
- Seed-case migration (README §9) present at 2.75; supersession honored.
- E2: five arms incl. contract-invariant (C-A3), per-visibility recall, N-vs-0 row, NOT_EVALUABLE
  bucket, TraceAnomaly provisional→empirical row (2.5.6) + ≤2-day gate (2.5.7) — all present.
- E1 gates: evaluability smoke, substitution rule, ≥4-tool floor, AutoRestTest LLM pin, ONE pinned TT
  topology, machine-spec/exclusivity citation — present (`:82-87`).
- Wild detectors S2-FP-calibrated BEFORE S3 sampling + benign-dominance branch — present (`:93-96`).
- UX §1 code citations all verify: `MistRunner.java:103,328`; `DataIntegrityRuntime.java:29-31` ("hooks
  never throw" + no-session passthrough); hidden-downstream titled attachment at
  `MultiServiceRESTAssuredWriter.java:781-800`; demo properties `allure.report=true` (line 55) with no
  B2 keys; flag-off byte-identity pinned by `DataIntegrityEmissionTest.java:130-135`.
- D1's firing rule = the G1 discipline verbatim (fails ONLY on OBSERVED_COMPLETE_ABSENT; TIMEOUT_ABSENT
  non-failing) — consistent with the FP-0.0 result's semantics.
- Observe mode = C3's single-leg wild detector (plan §3.2 instrument (ii)) — the identity claim itself
  is consistent and is the RIGHT product identity; only the precision-inheritance wording (U1) errs.
- D5/1.9.4 authoring-cost symmetry matches plan §4 arm-3 + freeze `authoring_cost` schema field.
- W3's DoD correctly uses the in-repo TT+SS specs, so the UX wave is executable before step 2 (the
  TeaStore/OTel proposal-acceptance datum lands in 2.75 as stated).
- The Allure bridge is additive AT THE VERDICT LEVEL (bridge maps existing verdicts; gating untouched)
  — the additivity claim fails only at the test-outcome level in paired runs (U2), which is fixable.
- FILE_INDEX rows for both artifacts exist (`FILE_INDEX.md:861-862`).
