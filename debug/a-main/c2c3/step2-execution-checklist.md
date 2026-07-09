# Step-2+ execution checklist (the forward manifest) — single-box, UX-gated

**Why this exists (user directive 2026-07-08):** before any step-2 work, (a) the full forward
checklist must be written down so nothing is forgotten while the UX wave runs, and (b) the MIST
user-experience of the main contribution must be DESIGNED AND RESOLVED first (see
`mist-ux-design.md`). **Cloud second node: NOT assumed — single-box is the default** (user decision;
the 10–13 wk single-box timeline of plan v2 §5 governs; the de-scope ladder §5.4 stands).
Sources consolidated: plan v2 §5, `c2-freeze.md` rev 2, `e-sut-applicability-matrix.md` rev 2,
`c2-depth-survey.md` verify-at-deploy riders, `benchmark/README.md` §9 migration map,
`REVIEW-STEP1-FREEZE-RECONCILIATION.md`. Status legend: ☐ open · ☐⋯ partially prepared · ✔ done.

## Step 1.9 — THE UX WAVE (gates step 2; user directive) — REV 2 per REVIEW-UX-RECONCILIATION U1–U8
✔ 1.9.1 UX design doc → ≥3-cold-reviewED → reconciled (4e136b2); rev-2 deltas govern.
✔ 1.9.2 IMPLEMENTED W0–W4+W6 (commit 1829a9e + summary-logging fix in 7d69de9; suites GREEN mist-cli 199, reactor BUILD SUCCESS = U5 byte-equal proof; W5 authoring-cost capture = a step-2.75 recording protocol, not code — lives at 1.9.4):
        **W0 observe-session lifecycle FIRST** (arming; parallelism→1 for hooked classes; registry
        property key; record API; positive-steps-only freshening — U1) → W1 verdict check (INERT in
        paired sessions; fail at end-of-method; defect = acked ∧ error==null ∧ OBSERVED_COMPLETE_ABSENT)
        → W2 Allure surfacing (categories.json → allure-results; TIMEOUT_ABSENT via tag-label +
        terminal summary) → W3 proposal (collection-shaped ONLY + first-run control probe — U8)
        → W4 docs+demo (demo registry WITHOUT fault_flag rows) → W6 supplied-tier writer emission
        (scoped; else expert tier disclosed harness-only — U6). failOnLost DEFAULT WARN until the
        S2-FP calibration at product caps passes (U4). Jaeger prerequisite documented (U3).
✔ 1.9.3 Demo-run DoD **PASSED (2026-07-09)** — see `ux-demo-dod-result.md`: TT redeployed (runbook §2.6); 2,483 tests executed exit 0; observe armed via the enhancer final-round path; rendered Allure test `test_positive_flow_S262_v219` carries "✅ durable write confirmed [adminroute-create] — 1 poll, 69 ms"; categories.json written.
✔ 1.9.4 Authoring-cost protocol pinned (D5/U7): minutes-per-bound-endpoint both sides; acceptance rate descriptive only; recording starts at step 2.75.
✔ 1.9.5 **MIST STUDY-COMMIT PIN = the ux-demo-dod-result.md commit** (criteria met; QuiescenceGate mapping frozen there; fork pin = a1767ab3) (criteria: value-delta + supplied hooks +
        injectors + fabricated-ack + W0–W6 in one buildable commit; QuiescenceGate→verdict mapping
        FROZEN at this pin; all case verdicts from 3a on recorded at it; promoted G1/G3 seeds
        re-recorded at it — moved from step 8, B-MAJOR).
☐⋯ 1.9.6 **∥ (U2 — longest lead):** CHANNEL DECIDED 2026-07-09 = in-group MIST-blind SE grad students
        (`c3-rater-materials.md` §7 rev 3) → external-outreach lead removed. **Rater-materials 3-cold-review
        DONE (2026-07-09, `REVIEW-RATER-RECONCILIATION.md`) → packet hardened to rev 3.** STILL OPEN
        BEFORE FIRST CONTACT (F22 — recruitment/screening are themselves IRB-covered): IRB/exemption
        filing; compensation decision (stipend vs credit + rate/hours, U1); the §11 blindness-screen
        instrument + §9 eligibility screen administered. BINDING: mandatory MIST-blind screen (§11) + §5
        independence + team quiet-period + §10 debrief manipulation check + "internal-but-blind" threat
        disclosure + §8 fallback if genuinely-blind students can't be staffed (do NOT relax blindness to
        fill seats).

## Step 1.95 — THE CORPUS FACTORY (parallel track, starts NOW; c3-case-corpus-plan.md)
☐ 1.95.0 **Raw-artifact INVENTORY (C-M5/B-B1): list what each seed asset actually has** (all 6 v0.1.0 cases are `capture_status: specified` = NO artifacts; TT h2h transcripts not committed) → sizes the capture-run list BEFORE building fixtures.
☐ 1.95.05 **Rater-artifact SIDECAR FORMAT (deliverable 0 — B-B1):** ordered request records (method/path/payload) + response records (status+full body) + durable-state observations, RELATIVE times, producer+mist_commit stamp; every producer emits THIS.
☑ 1.95.1 **B4 blind-label harness — DONE** (`b4_harness.py` + 5 invariant tests green; STRIP-LIST +
        no-own-clock + opaque-id + deterministic-bytes; consumes case+sidecar).
☑ 1.95.2a **SEED CAPTURES — DONE: 9 seeds, live-verified + B4-validated (commits d6d799c / 779a487).**
        `capture_driver.py` extended (multi-auth session|admin|none + variable-flow capture/set_session/
        redact + `{var}` subst) + specs, against the deployed fork (ts-inside-payment-service:1.0.5).
        Genuine(3): cancel-natural `{1,"error"}` bal50-lost, cancel-fabricatedack `{1,"Success."}`
        bal50-lost, createaccount-agreement 200-but-/account-ABSENT. Benign(4): adminroute-control,
        cancel-clean (+80→130), createaccount-clean (60), contacts-dedupe (`{0,"already exists"}`,
        1 durable contact — FIXED, was 400 wrong-endpoint). Eligibility(2): elig-genuine, elig-benign
        (disjoint-by-id). **SS pair still needs a TT-down window (2.15 tenancy).**
☑ 1.95.2b **CORPUS-ASSEMBLY WAVE — DONE + 3-COLD-REVIEWED + FOLDED (commits d062e52 → rev-2.1 fix wave, 2026-07-09).**
        (1) ✓ migrated the 6 legacy v0.1.0 cases → rev-2 (`fault{}`, typed `readback{}`, `oracle_eval{}`,
        `mist_dataintegrity_oracle`→`mist_readback_oracle`, ADDED `tracetest_presence_oracle`);
        (2) ✓ authored 5 NEW rev-2 measurement cases (cancel trio + createaccount pair); adminroute-control
        + contacts-dedupe flipped specified→captured with base-image digests + live sidecars linked;
        (3) ✓ schema was already rev-2 (`c2-freeze` §2 / 2026-07-08) — no change needed;
        (4) ✓ ALL 11 `benchmark/cases/*.json` validate rev-2 via `schema/validate_cases.py` (added);
        (5) ✓ ≥3-cold-review DONE + FOLDED (`REVIEW-CORPUS-RECONCILIATION.md`): A oracle-soundness /
        B migration-faithfulness (nothing dishonest) / C benchmark-design (ACCEPT-as-SEED). Through-line =
        reposition as SEED/pilot + scale plan, enforce honesty in-file. Fix wave EXECUTED (freeze rev-2.1
        §2/§4/§6 = S1↔schema reconciliation + capture_status-keyed evaluability + SEED disclosure; 10 case
        edits; README rewrite; validator re-green). Deploy pins = codewisdom 1.0.0 + fork inside-payment
        digest 81186b71 (branch MIST-trainticket). **REPORT: the corpus is 6 pos / 5 neg, a defensible
        PILOT; the FP/TP pair + breadth + S3 are the pre-registered scale plan (README §8, need live deploys).**
        REMAINDER: elig pair (tt-elig-genuine/benign) → the SCREENING instrument (§11), authored OUTSIDE the
        measurement corpus (schema pattern now review-cleared).
☑ 1.95.3 rater-materials 3-cold-review **DONE** (rev 3, `REVIEW-RATER-RECONCILIATION.md`, commit
        3aae75a). IRB/exemption filing + compensation (U1/U2) = USER (arranging). RATERS START only at
        the step-5 corpus gate — by construction, matching the recruitment lead.

## Step 2 — deploy wave (after 1.9; tenancy: big SUTs solo — TT, OTel-Demo; small co-reside)
✔ 2.1 .wslconfig ALREADY 26 GB (live-verified 25Gi in WSL — C-B2). **Do NOT edit .wslconfig again: applying it needs `wsl --shutdown`, which kills the running TT/kind cluster.**
☐ 2.15 **TENANCY SWAP SCHEDULE (C-B2 — TT currently holds the box: 53 pods, ~560Mi free):**
      (a) FIRST finish the §1.95 TT-live seed capture runs (TT must be UP);
      (b) then converge TT to the pinned lean-traced G1 topology (the E1/M-yield pin) or scale TT to
      0 (helm infra stays, PVCs persist — the §2.6 runbook recovers it);
      (c) only then 2.2/2.3. TT and OTel-Demo are BOTH big-solo tenants — never co-resident.
☐ 2.2 TeaStore deploy (kind; pin release tag + image digests at deploy → freeze label validity).
      Verify-at-deploy riders (survey): live-confirm 200-ORDERCONFIRMED under maintenance / DB-down
      / mesh-503; recommender cold-start semantics; capture the exact digests into case YAMLs.
      **+RIDER (C-M4): live-verify the Istio mesh-sever actually intercepts TeaStore's CLIENT-SIDE
      load-balanced calls (registry hands out pod IPs — the TT pod-IP/EnvoyFilter precedent); the
      broker-less min-3 mechanism floor hangs on this. If plain VS host-match misses, fall back to
      the inbound-EnvoyFilter pattern.**
☐ 2.3 OTel-Demo deploy — **PINNED PATH (C-B1): the official `opentelemetry-helm-charts` demo chart on the kind cluster** (compose is OFF-mesh → would forfeit the mesh-sever case-runs + 2.5.5 Tracetest wiring). VERIFY-RIDER: the pinned chart version's k8s rendering includes Kafka + accounting + fraud-detection (they were compose.full-only upstream once) — check BEFORE deploy; else values-enable or fallback to rendered-manifests.
      Riders: accounting-Postgres wiring + psql read-back probe; PlaceOrder ack latency under
      broker-down (confirm the ack path stays fast); frontend graceful-ad rendering; re-freeze the
      flagd list against the pinned tag (docs/json skew).
☐ 2.4 Boutique deploy (light). Rider: gRPC method-scoped Istio abort on `/hipstershop.CartService/EmptyCart`
      (HTTP/2 path match) live check.
☐ 2.5 Bookinfo: REDEPLOY needed (live-verified 0 pods — C-B2; the July assets are in-repo but the cluster state is gone); small: istio samples apply + smoke.
☐ 2.6 Post-reboot runbook items stay in force: re-create mist:mist RabbitMQ user + warm-up POST
      before any SS run; repo .sh files are CRLF → run CRLF-stripped copies; minikube stays stopped.
      **TT redeploy lessons (2026-07-09, live-verified):** (a) `quickstart-k8s/yamls/deploy.yaml` is
      GENERATED + gitignored — regeneration resets images to `:1.0.2` which is ABSENT on Docker Hub →
      `sed s/:1.0.2/:1.0.0/` (the cached, previously-run tag) then re-apply; (b) an "empty" trainticket
      ns may be SCALED-TO-0 infra, not deleted — helm releases persist → `helm install` fails
      "cannot re-use a name"; recovery = `kubectl scale sts nacosdb-mysql tsdb-mysql --replicas=2`,
      `sts nacos --replicas=1`, `deploy rabbitmq --replicas=1` (PVCs preserve July mysql data);
      (c) WSL can go unresponsive for ~1 min during 40+-pod startup (R6) — wait, never `wsl --shutdown`.
☐ 2.7 Wipe scripts / state-reset per SUT (DB-wipe preferred, rollout-restart fallback).
☐ 2.8 Wave-runner (unattended: timeout, logs, reset, dispatch; 2–4 d budget) — REQUIRED for E1/M-yield
      on a single box (R6 host-wedge protection: exclusive runs, off-peak builds, disk prune per wave).

## Step 2.5 — instrumentation wave (gates ALL E2 trace arms)
☐ 2.5.1 TT: OTel javaagents on the target write paths (G3 two-part mitigation precedent).
☐ 2.5.2 SS: OTel Node auto-instr (front-end) + javaagents (orders/shipping).
☐ 2.5.3 TeaStore: Kieker→OTel converter spike OR pre-registered exclude branch → cases become
        `trace-uninstrumented` (NEVER conflated with by-construction — B-M5). **U3 DISPOSITION
        (pre-registered): the converter is on MIST's OWN critical path, not just E2's — without
        Jaeger the decisive OBSERVED_COMPLETE_ABSENT can never fire on TeaStore, so the exclude
        branch means TeaStore MIST verdicts live in the TIMEOUT_ABSENT stratum (reported separately,
        G1 R3#1 discipline) or NOT_EVALUABLE-by-instrumentation; any new non-trace absence gate would
        need its OWN S2-FP calibration before use.**
☐ 2.5.4 Measured trace-coverage table per SUT (= the §8.5-2 disclosure).
☐ 2.5.5 Tracetest Agent + OTLP collector on the kind cluster; smoke arm 2+3 vs Bookinfo
        (`selected_spans.count = 0` absence assertion end-to-end).
☐ 2.5.6 TraceAnomaly normal-corpus capture per SUT FIRST (training data — C-M3 ordering fix).
☐ 2.5.7 TraceAnomaly Docker: train on 2.5.6's corpus, then evaluate on captured S1 masked-write
        traces (**exist only after 3a** — this item is 3a-gated) → turns the PROVISIONAL
        construction-blindness verdict into an empirical row (C-A3).
☐ 2.5.8 Contract-invariant arm spike (Pact/Dredd/AGORA+-style; the 5th, non-trace-family arm — C-A3):
        operability on ≥1 SUT; config recorded per case.

## Step 2.75 — per-SUT MIST enablement package (1–3 d/SUT; DoD = the NEW 1.9 user flow)
☐ TeaStore: author OpenAPI (pre-registered as authored-by-us) + auth glue + triples (propose→confirm)
  + one end-to-end observe-mode run whose Allure shows the data-integrity section. Record authoring cost.
☐ OTel-Demo: **author its OpenAPI too (plan §4 authors BOTH specs — B-MAJOR)** + registry + auth smoke
  + triples (Kafka case = `readback.modality: sql-probe` → `mist_bindable` decided HERE) + same DoD.
  Record authoring cost.
☐ Boutique (light) + Bookinfo (oracle-smoke only). Record authoring cost.
☐ Seed-case migration: **moved to §1.95.2 (single-homed — B-M2/C-M2); here only VERIFY it is done at the same harness/pin version.**

## Step 3a — S1/S2 population (∥ nights; quotas = survey + freeze rev 2 §5)
☐ S1: TeaStore 4–5 (flag/dependency-down/mesh ×2 sites) · OTel-Demo 4–5 (broker/mesh/flag; build the
  code-level spare or lean on cross-SUT floor) · Boutique 1 (disclosed S1-minor) · TT (F-corpus ≥6
  target 10, EACH in-class-verified masked-2xx before counting — B-m6; + G1/G3 promoted cases) · SS
  (shipping + carts promoted cases). **Report BOTH denominators (distinct-site vs case-run); if
  distinct sites < 20 → disclosed finding, stop-and-replan per plan §2.3.**
☐ S2: survey's 16 (4 new SUTs) + 2 packaged corpora (≤2 cases each) + TT/SS designed-degradation
  paths ENUMERATED (not hand-waved — B-B2); target ≥35 or disclosed shortfall.
☐ Every case: negative control (S1), health-precondition checklist, replay script, typed readback,
  `ack_content_visibility` + `trace_visibility` + `write_shape` + `oracle_mode` + `mist_authoring`
  authored, license fields, digests, oracle_expectation + validator PASS (B-minor).
☐ License conduct AT POINT OF USE (B-MAJOR): F-corpus = replicate-by-description ZERO code copied +
  cite; never re-push images; §4 change notices on modified manifests/fork diffs.
☐ A-M8 disclosure task: for best-effort-plausible S1 writes (OTel accounting) attach contract-grounding
  evidence or disclose the construction-bar basis in the case file.
☐ Tell-free floor tally (R8): count natural × success-shaped-clean × trace-invisible-by-construction.
☐ Fork image builds where needed (TT fabricated-ack inside-payment 1.0.5 etc.) — off-peak, never
  while a graph is deployed.

## Step 3b — E1 two-tier baseline grid (~160 h driven; wave-runner mandatory)
☐ FULL tier: TT / TeaStore / SS — 5 tools × 10 seeds × 1 h (driven total 138–172 h, not "~160" flat — C-minor). THIN: Bookinfo / Boutique / OTel-Demo —
  3 × 30 min (saturation disclosed). Evaluability smoke gate per cell ("tool reaches ≥1 authed
  endpoint" else non-evaluable, not zero). Substitution: Morest/AutoRestTest fail → RestTestGen;
  floor ≥4 runnable tools. AutoRestTest LLM key + model pinned. ONE pinned TT topology (lean-traced
  G1). Machine spec + exclusivity per "No Time to Rest Yet".

## Step 4 — M-yield (1 h × 10 seeds spec-rich tier; 1 h × 3 thin; LLM-off disclosed)
☐ Event→case clustering (endpoint × fault-signature × SUT); 1 representative + 10% audit sample →
  feeds the rater M-yield audit set (B-M3). Upstream filings for genuine finds DURING execution.

## Step 5 — M-prevalence + S3 (rater-gated ∥)
☐ Wild detectors: (i) trace-shape masking oracle on instrumented SUTs; (ii) single-leg read-back
  absence — BOTH S2-FP-calibrated BEFORE S3 sampling. Workloads pinned (12 h/SUT or 500-write stop);
  write-path fraction reported. S3 sample = min(all, 40) stratified; <20 wild flags ⇒ scarcity IS the
  finding (benign-dominance branch pre-registered).
☐ Rater channel + IRB: **already started at 1.9.6 (U2)** — this step only CONSUMES the recruited
  raters; author-blind fallback (with scars) triggers here only if 1.9.6 failed.
☐ B4 harness: **built at §1.95.1 (single-homed — B-M2/C-M2); here only VERIFY same harness version across all strata (part of the entry gate).**
☐ **CORPUS-ASSEMBLY ENTRY GATE (8 checks — corpus plan §6 rev 2; before ANY rater sees a case):**
  same-B4-harness-version across strata · tell-audit (incl. timestamps + cross-strata shape
  uniformity) · manifest SEALED incl. RUBRIC VERSION · corpus hash frozen · machine disjointness
  (calibration ∩ S3 ∩ M-yield-audit ∩ eligibility = ∅ by true id) · every rated case
  `capture_status == captured` · IRB determination RECEIVED (before FIRST CONTACT, not just labeling — F22) ·
  wild-flag capture bundle present for every S3 case (B-M1) · **(9th check, rater review F2/F3/F11)
  blindness-screen (§11) + §10 debrief records on file for every rater; the §3 worked examples AUTHORED
  on real calibration cases + rater packet rev ≥3 final**.
☐ Adjudication: blindness invariants; **conservative-tie-break primary (adjudicated secondary; F6)**;
  S3-only κ primary; calibration = max(30, 50−|S3|), benign-skewed ≥2:1, sized so pooled ≥50 free.
☐ M-prevalence reporting obligations VERBATIM (B-MAJOR): detector-conditioned LOWER-BOUND estimand
  stated; detector recall on S1 as the qualifier; two denominators (per-request, per-endpoint);
  workload scripts versioned in the benchmark; write-path fraction reported.
☐ **C3-results ≥3-cold-review at this step's acceptance gate (plan §6 — B-MAJOR).**

## Step 6 — E2 comparator frontier (5 arms; +1 wk if TraceAnomaly cleared)
☐ Arms: naive span-error · Tracetest span-error · Tracetest span-PRESENCE (per-endpoint
  authoring-cost recorded — the automation-gap datum, symmetric with OUR triples cost) ·
  TraceAnomaly (as cleared: competitor or construction-blindness demo) · contract-invariant.
  Matched recall on the trace-visible subset; recall per visibility class;
  trace-invisible-by-construction = its own N-vs-0 row; NOT_EVALUABLE its own bucket.
☐ **E2-results ≥3-cold-review at this step's acceptance gate (plan §6 — B-MAJOR).**

## Step 7 — E5 ablations (TT ×5 seeds; 3–4 d). Step 8 — E6 packaging + review
☐ E6: standalone benchmark repo (Apache-2.0 + CC-BY-4.0 + component map; MIST by reference LGPL);
  index.generated + MANIFEST.sha256; large artifacts → Zenodo/OSF by hash; scoring-harness home +
  license decided (B-m4). (MIST study-commit pin: DONE back at 1.9.5 — verify all recorded verdicts
  carry it.)
☐ Benchmark ≥3-cold-review in sampled-reproduction form (k=5 re-runs + m=15 audits each).
☐ E3 trigger rate mined from E1/M-yield logs (free).

## Standing constraints (never forget)
- Single box; 26 GB WSL for TT; tenancy schedule; never build while a graph is deployed; docker-exec
  recovery runbook; disk prune per wave; kind cluster "mist": TT up (53 pods), sockshop + bookinfo scaled to 0/absent (C-B2 live-verified), minikube stopped.
- All MIST-repo changes on `main_track`; no Co-Authored-By; no file deletion; FILE_INDEX + memory sync.
- Frozen docs change only via disclosed amendments (`c2-freeze.md` §6).
- Paper honesty riders: lead with the study; two-denominator S1; tell-free floor; MIST vs arm-3
  authoring-cost symmetry; Gate-4 wording "3 frontier trace comparators + contract-invariant arm".
