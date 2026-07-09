# REVIEW C — line-by-line correctness + executability audit of `step2-execution-checklist.md`

**Reviewer:** independent cold reviewer C (no shared context), 2026-07-09.
**Target:** `debug/a-main/c2c3/step2-execution-checklist.md` as it stands (HEAD `a59b7eb`).
**Method:** every ✔/☐ cross-checked against the governing docs (plan v2, `c2-freeze.md` rev 2,
`e-sut-applicability-matrix.md` rev 2, `c2-depth-survey.md`, `c3-case-corpus-plan.md`,
`c3-rater-materials.md`, `ux-demo-dod-result.md`, `REVIEW-UX-RECONCILIATION.md`,
`REVIEW-STEP1-FREEZE-RECONCILIATION.md`, `benchmark/README.md` §9, `r4-comparator-spike.md`), against
`git log`/`git branch --contains`, against the CODE (`DataIntegrityObserveCheck.java`), and against the
LIVE box (`wsl free -h`, `kubectl get ns/pods` on kind "mist").

## VERDICT: **ACCEPT-WITH-CHANGES**

The checklist is substantively faithful: the coverage audit (finding list at the end) found **no
dropped commitment** from plan v2 §5 / freeze rev 2 §5–§6 / matrix rev 2 / the two reconciliations —
every named obligation (C3/E2 own ≥3-reviews, B4 harness, OTel OpenAPI authoring, license conduct at
point of use, M-prevalence obligations verbatim, A-M8, seed migration, authoring-cost capture,
study-pin usage from 3a) is carried. But the step-2 wave — the NEXT work — is **not executable in the
order and state the checklist asserts**: two items assert live-cluster facts that are false today, the
OTel-Demo deploy substrate is unpinned in a way that silently forecloses half its S1 cases, and two
sub-steps consume artifacts produced later. Fix the two BLOCKING items before any step-2 command runs.

---

## [BLOCKING]

### B1 — 2.3 OTel-Demo: "compose.full profile or k8s equivalent" is an unpinned fork in the road that decides case feasibility (`step2-execution-checklist.md:52`)
- The survey pins components to the **compose** packaging ("Kafka + accounting + fraud-detection exist
  ONLY in the `compose.full.yaml` overlay" — `c2-depth-survey.md:94-95`) and the matrix's enablement
  column says "compose.full pin" (`e-sut-applicability-matrix.md:14`). **No document pins a k8s path**
  (grep for helm/k8s OTel manifests across `debug/a-main/c2c3/` + `evaluation/`: nothing).
- On this box the infra is a **kind cluster + Istio**. Docker-compose would run on the WSL docker
  daemon OUTSIDE the mesh → the OTel-Demo **mesh-sever mechanisms cannot be injected under compose**.
  That kills 2 of the SUT's ~4 S1 case-runs (kafka-loss × mesh-sever, EmptyCart × method-scoped sever —
  `c2-depth-survey.md:106-108`), drops OTel-Demo's mechanism count from 3 real to 2, and breaks the
  `kubectl apply` injection idiom + the 2.5.5 Tracetest/OTLP wiring (which the checklist puts "on the
  kind cluster"). The "or" reads as an operator's free choice; it is not.
- **Fix:** rewrite 2.3 to PIN the k8s path: deploy via the opentelemetry-demo Helm chart (or the repo's
  rendered kubernetes manifests) at an exact chart+app version, **with a verify-at-deploy rider that
  the rendering actually includes kafka + accounting + fraud-detection** (the compose.full-only finding
  is about compose packaging; the k8s componentization must be confirmed against the pinned version,
  values-enabled if needed), in-mesh (sidecar-injected ns) so mesh-sever cases are injectable. Compose
  is at most a disclosed fallback for the broker/flag cases only, with the mesh-sever loss disclosed as
  a quota change. Record the decision in the case YAMLs' deploy pins.

### B2 — Step-2 sequencing + live-state assertions are stale; as written the wave is not executable in item order (`step2-execution-checklist.md:47,48,49,52,58,168`)
Live facts (2026-07-09, verified this review): `.wslconfig` = **26GB already** and running WSL shows
**25Gi total / 560Mi available / 24Gi used**; `trainticket` ns has **53 Running pods** (~4.6h old, the
DoD redeploy); **`sock-shop` ns has ZERO pods**; **no bookinfo namespace and `default` ns is empty**.
Consequences, item by item:
- **2.1 is DONE** (config 26GB + live 25Gi — the running WSL already picked it up; the TT DoD ran under
  it). Flip to ✔ with the `free -h` evidence. Keep only a standing rule: any FUTURE `.wslconfig` edit
  needs `wsl --shutdown` → kills the kind cluster → full §2.6 TT recovery + the SS post-reboot items
  (rabbit user + warm-up) — so wslconfig changes may only happen at a planned swap boundary.
- **2.5 is FALSE today**: "Bookinfo: already live-proven in-repo — nothing to deploy beyond the
  existing cluster state" — there are no bookinfo pods on the cluster. The in-repo assets
  (`evaluation/suts/bookinfo/…`) exist, but 2.5.5's Tracetest smoke and Bookinfo's E1-thin/S2 runs need
  an actual redeploy (cheap, but it is a deploy task; also verify Jaeger in `istio-system` still lives).
- **Line 168 is stale**: "kind cluster \"mist\" (sockshop currently up)" — sock-shop is scaled to
  zero; TT (the biggest tenant, 53 pods) is what's up and isn't mentioned. Standing-constraints text
  should not assert mutable live state; say "check tenancy before every wave" instead.
- **2.2/2.3 cannot start with TT up**: 560Mi available means deploying TeaStore, let alone OTel-Demo,
  alongside TT is an R6 wedge waiting to happen. The header's "big SUTs solo — TT, OTel-Demo" states
  the rule but the checklist never gives the SWAP SCHEDULE, and TeaStore is classified by neither the
  plan (§5: "small co-reside: Bookinfo+SS") nor the checklist.
- **Fix — add an explicit tenancy/swap schedule to step 2** (answering "TT live for §1.95" honestly):
  1. **TT-up window (now):** finish all TT-dependent work first — §1.95 doc/factory work needs no
     cluster, but the promoted-seed RE-CAPTURE/re-record at the study pin (REVIEW-UX-RECONCILIATION
     C-disposition: re-record during 2.75/3a) and any missing raw artifacts for the v0.1.0 seeds (see
     M6) need TT live; also 2.5.1 javaagents + converge TT to the **pinned lean-traced G1 topology**
     (3b pins it — `step2-execution-checklist.md:123` — but no step-2/2.5 item currently converges the
     running full-quick_start untraced deploy to it; add that task).
  2. Scale TT to 0 (§2.6(b) helm-preserving idiom) → **OTel-Demo solo window** (2.3 + its 2.75/2.5
     items). 3. TeaStore window (measure footprint at deploy; likely co-residable with Bookinfo/SS,
     NOT with TT at current headroom — classify it then). 4. Bookinfo redeploy + SS re-scale (+ rabbit
     user + warm-up) for their waves. 5. TT back up for 3a/3b TT legs.

---

## [MAJOR]

### M1 — 1.9.2 cites an UNREACHABLE commit: `1d6ed9b` (`step2-execution-checklist.md:14`)
`git branch --all --contains 1d6ed9b` → empty: it is the pre-rebase/pre-amend twin of **`1829a9e`**
(same message "UX wave W0-W6…", same files), which IS on `main_track`. A dangling object can be GC'd,
and this pointer sits under the study-commit-pin lineage. **Fix:** cite `1829a9e` (+ the summary-logging
fix in `7d69de9`). While there: 1.9.5 names the pin only as "the ux-demo-dod-result.md commit" — write
the sha **`7d69de9`** explicitly (the doc's "commit of this document" self-reference goes stale on any
later edit of that file).

### M2 — B4-harness and seed-migration items are DUPLICATED without cross-annotation (1.95.1 vs step 5; 1.95.2 vs 2.75) (`step2-execution-checklist.md:36-42,98,137-139`)
The corpus plan moved B4 + seed migration to the NOW-track (`c3-case-corpus-plan.md` §4 "✘ mis-timed…
moves to the parallel track"; §6). The checklist added §1.95 but left the OLD copies intact and
unmarked: step 5 still says "**B4 blind-label harness BUILD item** … built + smoke-tested BEFORE
calibration" (137-139) and 2.75 still says "Seed-case migration (benchmark/README §9): port the 6
v0.1.0 seed cases to rev 2" (98). Two live copies of one task = double-execution or divergent drift.
**Fix:** reword the step-5 copy to "B4 built at 1.95.1 — at this step only pin the harness VERSION and
re-run the invariant tests on the final mix (the entry gate below)"; reword the 2.75 copy to "done at
1.95.2 — verify validator PASS and re-record verdicts at the study pin in this step's TT/SS windows".

### M3 — 2.5.6/2.5.7 are ordered backwards AND consume step-3a output (`step2-execution-checklist.md:85-87`)
Two defects: (a) "TraceAnomaly Docker on **captured S1 masked-write traces**" — no such traces exist
today (TT ran quick_start UNTRACED for the DoD; TT/SS are trace-dark on target paths per the matrix;
the committed bookinfo/boutique traces are S2 benign) — S1 masked-write traces on an instrumented SUT
first exist AFTER 2.5.1/2.5.2 instrumentation + a 3a fault run. (b) TraceAnomaly is unsupervised — its
Bayesian model **trains on a normal corpus and scores traces against it** (`r4-comparator-spike.md:56-60`)
— so 2.5.6 cannot run before at least one SUT's normal-corpus capture, yet the checklist makes 2.5.7
(normal-corpus capture) CONDITIONAL on 2.5.6 clearing. Circular as written. **Fix:** split 2.5.7 into
"minimal 1-SUT normal-corpus capture (prereq of 2.5.6)" + "full per-SUT capture (only if 2.5.6 clears
in ≤2 days)"; re-annotate 2.5.6 "earliest: after 2.5 instrumentation + the first 3a S1 captures;
latest: step 6" (this matches the spike's own "step-2.5/step-6 run" wording, `r4-comparator-spike.md:31-34`).

### M4 — TeaStore mesh-sever interception is unverified under client-side load balancing, and the mechanism FLOOR hangs on it (`step2-execution-checklist.md:49-51`)
TeaStore does registry-based **client-side load balancing** (`c2-depth-survey.md:46-47`) — the same
pattern that broke plain VirtualService host-matching on TT (calls go to pod-IP authority; the fix was
an EnvoyFilter on the target's INBOUND listener — the committed TT mesh note/precedent). If mesh-sever
does not intercept on TeaStore, its mechanisms drop {flag, dependency-down} = 2 < the broker-less
minimum 3 (`c2-freeze.md` §5 R1), and the order-items partial-write site (mesh-only) is lost. The 2.2
rider list carries the survey's items but not this one. **Fix:** add rider to 2.2: "verify Envoy abort
actually intercepts the registry-client call path (TT pod-IP precedent → expect EnvoyFilter-on-inbound,
not VS host-match); if not interceptable, the mechanism floor accounting changes — disclose before
counting TeaStore S1 quotas."

### M5 — §1.95 assumes raw artifacts exist for every seed; for the 6 v0.1.0 cases they do NOT, and TT h2h raw transcripts are results-doc-only (`step2-execution-checklist.md:36-42`)
All 6 `benchmark/cases/*.json` carry `capture_status: "specified"` — by definition "traces/read-back
not yet recorded" (`benchmark/README.md` §4). B4's input is "rev-2 case + raw artifacts (ack response,
read-back polls / probe outputs, request sequence)" (`c3-case-corpus-plan.md` §5). SS h2h run
transcripts ARE tracked (`debug/a-main/g3-comparator-ss/runs/*.txt`), but `g3-comparator-tt/` has no
raw runs directory (bindings + results docs only). So "promote … through the harness end-to-end"
(1.95.2) silently depends on captures that need LIVE SUTs — contradicting the §1.95 "zero deploy deps"
premise for part of the subset. **Fix:** make 1.95.2's first action an **artifact inventory per
promoted seed**; seeds with captured artifacts (SS runs, G1/G2 records, whatever the TT results docs
embed verbatim) proceed now; `specified`-only seeds enter as B4 fixture inputs (schema/validator work)
but become RATER-FACING calibration cases only after re-capture in the TT-up window (B2 fix, step
2.75/3a). Size the "~8–12" calibration subset from the captured-artifact pool, not the full list.

---

## [MINOR]

- **m1 — wrong fork-image tag "1.0.5"** (`step2-execution-checklist.md:116`): the committed repoint
  script uses fork **`:1.0.2`** ("ts-cancel + ts-inside-payment → fork :1.0.2, IfNotPresent" —
  `debug/a-main/prep/g3-tt-deploy-progress.md:150`). "1.0.5" appears nowhere else in the repo. This is
  a real trap because §2.6(a) documents sed-ing upstream `1.0.2→1.0.0` (Hub-absent tag) — the fork
  deliberately squats the local-only `:1.0.2`. Fix the constant to ":1.0.2 (kind-loaded, IfNotPresent —
  see g3-tt-deploy-progress.md)".
- **m2 — mis-cited stop-and-replan** (`step2-execution-checklist.md:105`): "if distinct sites < 20 →
  disclosed finding, stop-and-replan per plan §2.3" conflates two regimes and mis-cites. Freeze rev 2
  §5: <20 distinct sites = **disclosed finding** (not padded away); stop-and-replan is the R5 response
  to breaching FROZEN floors (≥6 distinct-site DI cases; S2 shortfall undisclosed), per plan §5, not
  §2.3. Reword: "<20 distinct sites → disclosed finding (freeze rev 2 §5); frozen-floor breach →
  stop-and-replan (plan §5 R5)".
- **m3 — path + timing staleness on the README §9 citations** (`step2-execution-checklist.md:9,40,98`):
  the file is `debug/a-main/benchmark/README.md` (repo-root `benchmark/` does not exist), and README
  §9's own text still says "Migration is a step-2 task … the seed cases FAIL rev 2 until then"
  (`benchmark/README.md:166-167`) — superseded by the corpus plan's NOW-track. Coordinator: annotate
  README §9 with the 1.95 supersession at next touch.
- **m4 — 2.75 DoD undefined for Bookinfo** (`step2-execution-checklist.md:91,97`): header says "DoD =
  the NEW 1.9 user flow", but Bookinfo has 0 write paths (matrix) → no triples → the Allure
  data-integrity section cannot appear → the DoD is unmeetable as literally stated. The "(oracle-smoke
  only)" parenthetical hints at plan §5's "thin SUTs may be oracle/prevalence-only, disclosed" — make
  it explicit: per-SUT DoD = full 1.9 flow for write-path SUTs; for Bookinfo, a completed run + the S2
  trap exercised, disclosed as oracle-smoke.
- **m5 — 1.9.5's parenthetical folds FUTURE obligations into a ✔ item** (`step2-execution-checklist.md:25-28`):
  "all case verdicts from 3a on recorded at it; promoted G1/G3 seeds re-recorded at it" are unstarted
  future tasks living inside a done-mark. Add an explicit ☐ under 3a: "re-record promoted G1/G3/SS seed
  verdicts at pin 7d69de9 (TT/SS live windows)" + "write the freeze §6 pin-amendment row at population
  time" (the DoD doc promises that row; §6 does not carry it yet — deliberate deferral, but give it a
  checklist home so it isn't lost).
- **m6 — W5 silently absent from 1.9.2's wave list** (`step2-execution-checklist.md:14-22`): the design
  defines W5 = authoring-cost capture template, "filled … during step 2.75"
  (`mist-ux-design.md:133`). The implemented list (W0–W4, W6) skips it without comment. 1.9.4's
  protocol-pinned ✔ covers the substance (freeze `mist_authoring` fields are the template); add one
  clause "(W5 = the freeze's mist_authoring/authoring_cost fields, filled at 2.75)" so the gap reads
  as deliberate.
- **m7 — 3b "~160h driven" label** (`step2-execution-checklist.md:119`): literal arithmetic is 3×5×10×1
  = 150h (FULL) + 3×5×3×0.5 = 22.5h (THIN) = **172.5h** at 5 tools, 138h at the 4-tool floor; "~160h"
  is the plan's round number sitting inside that band — fine, but annotate "(138–172h depending on
  runnable-tool count)" so the wave-runner budget isn't sized to the optimistic figure.

---

## Verified correct (checked, no action)

- **1.9.1 ✔** — `4e136b2` is the 3-review+reconciliation commit (git log verified).
- **1.9.2 substance** — `1829a9e` contains W0–W6 code (MistRunner, DataIntegrityObserveCheck,
  DataIntegrityRuntime, TriplesProposer, MultiServiceRESTAssuredWriter + the fault_flag-free
  `target-triples-demo.yaml`); **failOnLost default WARN confirmed in code**
  (`DataIntegrityObserveCheck.java:27-28,104-107`, property `mst.oracle.dataintegrity.failonlost`,
  default false = warn); Jaeger prerequisite disclosed (`mist-ux-design.md:13-16`). Only the commit
  POINTER is wrong (M1).
- **1.9.3 ✔** — `ux-demo-dod-result.md` exists, PASSED 2026-07-09; the checklist's evidence claims
  (2,483 tests exit 0; `test_positive_flow_S262_v219`; categories.json; enhancer final-round arming)
  match the record verbatim; `7d69de9` contains the doc + the MistRunner summary-mirror fix + the DoD
  properties profile.
- **1.9.4 ✔ / 1.9.6 ☐⋯ / 1.95.3 ☐** — consistent with `c3-rater-materials.md` §7 (channel DECIDED
  2026-07-09, in-group MIST-blind, all four binding conditions mirrored), §8 fallback, §9 eligibility
  screen, and the §7-end IRB precondition; U7 authoring-cost protocol in freeze §6 rev-2 amendment row.
- **1.9.5 pin criteria** — match `ux-demo-dod-result.md` §1.9.5 and freeze §6 (pin at END of 1.9 wave,
  QuiescenceGate mapping frozen); fork pin `a1767ab3` is external (not verifiable here, noted only).
- **Step-2 riders complete vs the survey's verify-at-deploy list** (`c2-depth-survey.md:170-174`):
  TeaStore (tag/digest pin, 200-ORDERCONFIRMED × 3 producers, recommender cold-start), OTel (psql
  probe, ack latency under broker-down, graceful ads, flag-list re-freeze vs docs/json skew), Boutique
  (gRPC method-scoped abort live check), Bookinfo (nothing) — all carried at 2.2–2.5.
- **Coverage sweep — every charged commitment lands:** C3-results ≥3-review (`:148`), E2-results
  ≥3-review (`:156`), B4 harness (1.95.1 + step-5 gate), OTel-Demo OpenAPI authored-by-us (`:94`),
  license conduct at point of use (`:111-112`), M-prevalence obligations VERBATIM — detector-conditioned
  lower-bound estimand, S1-recall qualifier, two denominators, versioned workloads, write-path fraction
  (`:145-147` + `:133`), A-M8 disclosure (`:113-114`), seed migration (1.95.2), authoring-cost capture
  (1.9.4, 2.75 ×3, step-6 arm-3 symmetry), study-commit pin from 3a on (1.9.5 + step-8 verify), B-m6
  in-class verification (`:104`), S2 enumeration not-hand-waved (`:106-107`), tell-free floor tally
  (`:115`), two-denominator S1 reporting (`:104-105`), TeaStore U3/Kieker disposition pre-registered
  with the TIMEOUT_ABSENT-stratum / NOT_EVALUABLE-by-instrumentation split (`:75-81`), TraceAnomaly
  provisional→empirical intent (`:85-86`, ordering aside — M3), 5-arm E2 incl. contract-invariant +
  per-visibility-class recall + N-vs-0 row (`:150-155`), Gate-4 wording (`:172`), κ mechanics: S3-only
  primary + calibration ~30 so pooled ≥50 free (`:144`), corpus-assembly entry gate = corpus plan §4/§6
  verbatim (`:140-143`), <20-wild-flags scarcity branch (`:133-134`), de-scope ladder + single-box
  default (`:6-7`), R6 protections + wave-runner (`:69-70`).
- **Arithmetic (charge 5):** 3b = 138–172h driven ≈ plan's "~160h" ✔ (m7 label nit only); step-5
  prevalence = 12h × 6 = 72h ≈ plan's "~72h ≈ 1wk" ✔; M-yield = 30h + 9h ≈ 1–1.5wk ✔. No impossible
  scheduling found given the wave-runner + nights-∥ assumptions; the binding constraint is the
  deploy-swap serialization, which is exactly what B2's missing swap schedule must encode.

## Bottom line
Fix B1 (pin the OTel-Demo k8s path + component verification) and B2 (tenancy swap schedule + correct
the three stale live-state assertions + flip 2.1 to ✔) before the first step-2 command; fold M1–M5 in
the same editing pass (all are one-to-three-line checklist edits except M5's artifact inventory, which
is an hour of work that de-risks the whole §1.95 track). Nothing found that undermines the governing
pre-registration itself.
