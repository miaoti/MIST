# UX design + step-2 checklist — 3-cold-review RECONCILIATION + fix disposition

**Reviews:** A design/code (`REVIEW-UX-A-design.md`, ACCEPT-WITH-CHANGES) · B completeness
(`REVIEW-UX-B-completeness.md`, ACCEPT-WITH-CHANGES) · C adversarial (`REVIEW-UX-C-adversarial.md`,
borderline). All §1 code citations verified accurate by A and B; the design direction confirmed; the
findings below are folded as rev 2 of both docs + a disclosed `c2-freeze.md` §6 amendment.

## Convergent findings (≥2 reviewers) → fixes
| # | finding | reviewers | fix |
|---|---|---|---|
| U1 | **Observe-mode session lifecycle does not exist** — `beginRun`/`endRun` are harness-only; in a normal run every hook is a no-op (no baseline/poll/records); plus `beginRun` refuses parallelism>1 while normal runs default auto→8; W1 would map nothing; demo DoD unpassable | A-B1, B-MAJOR | **new W0** (session arming in MistRunner when enabled+observe; parallelism forced 1 for hooked classes or per-class sessions; record-access API; enhancer/exploration-round policy) |
| U2 | **Rater outreach + IRB misplaced at step 5** — plan §5 puts outreach at step 1 (2–6 wk lead ∥); the rater-materials ≥3-review-before-contact is absent from the checklist; as written the scarred author-blind fallback becomes the default outcome | B-B1, B-B2 | checklist: outreach + IRB + rater-materials review moved to §1.9-parallel (USER-GATED, start NOW) |
| U3 | **Decisive verdict is Jaeger-gated and undisclosed** — `traceComplete()` returns false without `jaeger.base.url` → absence stays TIMEOUT_ABSENT → failOnLost can NEVER fire on un-traced SUTs; user-input table lacks the trace backend; TeaStore (Kieker) hits this structurally (MIST's own FNs, not just E2's) | C-strongest, A-MAJOR, B-MINOR | disclose prerequisite in §1 table + claim wording ("once bound AND trace-instrumented"); TeaStore disposition PRE-REGISTERED in the checklist (converter = critical path for MIST's decisive verdict, else TIMEOUT_ABSENT-stratum reporting / NOT_EVALUABLE-by-instrumentation; any non-trace gate must be separately S2-FP-calibrated) |
| U4 | **"Product inherits a calibrated precision story" contradicts plan §3.2** ("FP profiles are NOT inherited from paired-mode zeros"); failOnLost=true would ship before S2 calibration exists | C-A3, B-MAJOR | sentence DELETED; policy: ship `failOnLost` **default warn**; flips to fail-by-default only after the S2-FP calibration at product-default caps passes (pre-registered) |
| U5 | **W1's check would fire inside paired fault legs** (same JVM sessions via PairedFaultExecutor) → breaks G1/G3 record flow / eval reproducibility | A-MAJOR, B-MAJOR | W1 check emitted but INERT unless the session is observe-mode (session-kind flag); paired suites re-run to prove byte-equal behavior |
| U6 | **Expert tier is harness-only in the product path** — the writer never emits `beforeWriteSupplied`; bodyless matched steps are skipped (pinned by DataIntegrityEmissionTest:138-143) | A-MAJOR, B-MAJOR, C-A2 | disclosed in the design (expert tier = eval-only TODAY); W6 (writer emission for supplied triples) added as a scoped item; paper prices the tier split (per-case config-tier field) |
| U7 | **Symmetry not mechanized in the schema** — comparator `authoring_cost` exists; MIST's cost/tier has no field; units incommensurable; W3's acceptance-rate DoD circular (authors accepting author-written specs) | C-A1, A-m | freeze §6 amendment: `config_provenance.mist_authoring {tier: proposed-accepted|hand-written|expert, minutes}` + per-case `oracle_mode: observe|paired`; common unit = minutes per bound endpoint, both sides; acceptance-rate reported as descriptive, never as an evaluated claim |
| U8 | **D2 heuristic proposes read-backs the runtime cannot evaluate** — `extractItems` is collection-only; per-entity `GET /res/{id}` → perpetual false LOST | A-MAJOR | W3 scope: propose ONLY collection-shaped read-backs; per-entity shapes are out-of-scope until a dedicated (tested) extractor exists; proposal validator runs one control write per accepted triple before trusting it (verify-at-first-run) |

## Single findings folded
A: no registry property key exists (path = hardcoded `<conf dir>/target-triples.yaml` convention) → W0
adds `mst.oracle.dataintegrity.registry` (default = the convention); verdict enum names corrected
(`OBSERVED_PRESENT`; record-level `NOT_APPLICABLE`/error vs pair-level NOT_EVALUABLE); Allure: MistRunner
writes allure-results (no HTML gen) → categories.json lands in allure-results; TIMEOUT_ABSENT surfacing
via tag-label + terminal/FaultDetectionTracker summary (categories can't capture passing tests);
failOnLost stays a boolean (no 3-level knob); fail at end-of-method with the ACKED-BUT-LOST marker;
afterWrite is synchronous → no @AfterAll drain; observe-mode freshening must not clobber negative-variant
fault params (W0 policy: freshen only positive/happy-path steps).
B: C3-results + E2-results each get their OWN ≥3-cold-review (checklist steps 5/6 acceptance); MIST
study-commit pin moved to END of the 1.9 wave (criteria include W0–W6; verdicts recorded from 3a on);
OTel-Demo OpenAPI authoring added (2.75); B4 blind-label harness build item added (step 5 prep);
A-M8 genuineness disclosure task added (3a); license conduct rules pinned into 3a; M-prevalence reporting
obligations restored verbatim (estimand, S1-recall qualifier, two denominators, workload scripts
versioned); **TT namespace verified EMPTY → TT redeploy scheduled before the 1.9.3 demo DoD**; pinned
suites named for the re-run (writer emission + registry + runtime suites); demo registry must not carry
fault_flag rows (product demo ≠ eval registry); "<20 sites" wording aligned with freeze rev 2.
C: G1/G3 promoted seed cases carry paired verdicts from pre-freeze commits vs the mist_commit-IDENTICAL
invariant → disposition: promoted cases' verdicts are RE-RECORDED at the pinned commit during 2.75/3a
(historical results remain in the result docs, cases reference them as provenance notes); QuiescenceGate→
{FIRE,NO_FIRE,NOT_EVALUABLE} mapping frozen at the 1.9 pin (no post-hoc tuning surface); claim scoped to
"state-level verdicts once bound and trace-instrumented"; tier-stratified recall reported.

## Execution order
1. This reconciliation committed. 2. `mist-ux-design.md` rev 2 (U1/U3–U8 + A's corrections; W0+W6 added).
3. `step2-execution-checklist.md` rev 2 (U2 + B's forgotten items + TT redeploy + TeaStore disposition +
pin timing). 4. `c2-freeze.md` §6 amendment + JSON schema lockstep (`oracle_mode`, `mist_authoring`,
tier field). 5. FILE_INDEX + memory. 6. THEN implement W0–W6 → suites green → demo DoD → step 2.
