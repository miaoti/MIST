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
        DONE (2026-07-09, `REVIEW-RATER-RECONCILIATION.md`) → packet hardened to rev 3.**
        **RATERS FOUND (USER, 2026-07-10) + HAND-OVER PACKET ASSEMBLED (`benchmark/rater-packet/`):
        ship/ (brief/consent/rubric/ballot + eligibility incl. 2-question spec-check + rendered
        SCREEN-G1/B1 + the PINNED UPSTREAM docs bundle tt-bundle-1 — pure FudanSELab 5526e505, NOT the
        injection-fork source, leak-scanned) + admin/ (§11 screen, §10 debrief, answer key). Regenerable
        via assemble_packet.py (leak gate caught 4 real leaks at assembly). Screening/consent/eligibility
        can run NOW; the RATING corpus (normalized calibration+S3 mix, sized at assembly per §6) arrives
        from the corpus track (step 2→5).** STILL ON USER BEFORE FIRST CONTACT (F22): IRB/exemption
        status; U1 compensation blanks in ship/02-consent.md. BINDING: mandatory MIST-blind screen (§11)
        + §5 independence + team quiet-period + §10 debrief manipulation check + "internal-but-blind"
        threat disclosure + §8 fallback if genuinely-blind students can't be staffed (do NOT relax
        blindness to fill seats).

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
        ELIG SCREEN cases DONE (2026-07-09): `benchmark/eligibility/tt-elig-{genuine,benign}.json` (rev-2,
        captured, OUTSIDE the measurement corpus, disjoint-by-id) — schema-valid + B4-render verified
        end-to-end (no leak; 5 B4 self-tests green; probe labels neutralized). §9 eligibility instrument =
        renderable. **BREADTH WAVE DONE (2026-07-10, user-approved autonomous order): adminroute +
        adminbasic lost-writes LIVE-CAPTURED on fork-built images (e5af2936/1c9913ea, clean a1767ab3 tree;
        kubectl set image+env; RESTORED to base 1.0.0 after) + NEW same-binary adminbasic control (closes
        R9). Corpus = 12 cases, 6 pos / 6 neg, captured discriminating positives 2→4 (2 fabricated-ack +
        2 skipped-persist across 3 services). Pod-log triple corroboration on adminbasic. RUNBOOK RULE:
        probe-first after any rollout (nacos/ribbon serves terminating pods; first adminbasic attempt
        discarded + re-captured fresh).** **NOOP-MODIFY BENIGN CAPTURED (2026-07-10): R6's missing
        read-back-stress control closed ON TT (idempotent no-op PUT, success-shaped-clean, zero delta;
        base image, no rollout). Corpus = 13 cases, 6 pos / 7 neg (3 S2 traps).** **TRACED-CAPTURE WAVE EXECUTED (2026-07-10b; plan rev-2 3-cold-reviewed → GO; freeze §6 amendment):
        8 legs re-captured as capture-of-record on the OTel-instrumented deploy (agent 1.33.6 pinned+sha,
        hostPath+JTO, per-leg Jaeger v2 exports, frozen pre-committed scorer trace_score.py). N-vs-0
        comparator cells MEASURED (fabricated-ack: naive+presence ran-and-missed, N=2/N=1) + breadth
        presence-catches MEASURED (flag, traced-control baselines) + DB-granularity disclosure MEASURED
        (fault 0 vs control 2/6/3 DB spans). mist_trace_shape = Branch-B traced-but-not-run → 2.5/E2.
        Runbook hardened: N≥4 consecutive probes (ribbon round-robin); fresh-ids for hardcoded-id specs.
        THIS partially discharges 2.5.1 (TT write-path javaagent mechanics de-risked; runbook+agent-pin+
        scorer = the 2.5.1/2.5.2 deliverables); instrumentation torn down (pilot framing, T10).**
        NEXT (no decision): step-2 SUT deploys (TeaStore/OTel-Demo; tenancy window = ASK USER before
        TT-down); FP/TP pair capture (bookinfo redeploy + SS window + queue-master consume spans);
        mist_trace_shape run at 2.5/E2.
☑ 1.95.3 rater-materials 3-cold-review **DONE** (rev 3, `REVIEW-RATER-RECONCILIATION.md`, commit
        3aae75a). IRB/exemption filing + compensation (U1/U2) = USER (arranging). RATERS START only at
        the step-5 corpus gate — by construction, matching the recruitment lead.

## Step 2 — deploy wave (after 1.9; tenancy: big SUTs solo — TT, OTel-Demo; small co-reside)
✔ 2.1 .wslconfig ALREADY 26 GB (live-verified 25Gi in WSL — C-B2). **Do NOT edit .wslconfig again: applying it needs `wsl --shutdown`, which kills the running TT/kind cluster.**
☑ 2.15 **TENANCY SWAP EXECUTED (tenancy-window plan rev-2, 2026-07-10):** (a) TT-live captures done
      (breadth + traced waves); (b) TT scaled to 0 (snapshot `/home/miaot/gate1-logs/tenancy-window/
      tt-replica-snapshot.txt`; helm infra + PVCs persist; §2.6 runbook + nacos doubleWrite rule =
      the revival path); (c) 2.2 (Phase C) + 2.3 (Phase D) executed in the freed window. END-STATE
      (recorded): OTel-Demo UP (plan §1 default), TeaStore UP (RAM allowed; both fit in 25Gi with
      TT at 0). The 2.15(b) lean-traced TT convergence remains a separate later decision.
☑ 2.2 TeaStore deploy: **DISCHARGED (tenancy Phase C, 2026-07-10, commit e9c8773)** — v1.4.2 pinned
      (images :1.4.2, digests in the case JSONs; manifest at gate1-logs/tenancy-window/teastore/).
      Riders adjudicated: **maintenance = VERIFIED-MASKED + CAPTURED** (201/`-1` fabrication measured;
      real toggle = POST `/rest/generatedb/maintenance` JSON body — survey path corrected; bare GET
      `/rest/generatedb` REGENERATES the DB, never probe); **DB-down = UNSOUND-for-capture** (no PVC,
      the wipe destroys the absence evidence — disclosed finding); **mesh-503 = VERIFIED-MASKED live**
      (rider leg; 3a case candidate); **recommender cold-start = REFUTED as user-visible** (isReady
      gating + registry-LB bridge the ~3 s window → no S2 case). **C-M4 RIDER ANSWERED: plain VS
      host-match INTERCEPTS on this deploy** (kind manifest sets HOST_NAME=svc DNS; registry holds
      `teastore-persistence:8080`) — the expected pod-IP miss REFUTED as measured; no EnvoyFilter
      needed. **Floor wording (wave-3a 2b, C-M4 fix): the broker-less min-3 floor points at THREE
      corpus rows — flag CAPTURED (`teastore-order-maintenance-masked-001`) + mesh-sever CAPTURED
      (`teastore-order-meshsever-masked-001`, wave-3a item 2 — the corpus's first mesh-sever case;
      sidecars-parity pair, teardown verified) + dependency-down SPECIFIED-with-disclosed-
      capturability (`teastore-order-depdown-specified-001`: sound only on a PVC-backed db —
      deploy-shape precondition in-file; never tallied per R2/R3). Live evidence for 2 of 3 legs.**
☑ 2.3 OTel-Demo deploy: **DISCHARGED (tenancy Phase D, 2026-07-10, commit 3c8d581)** — chart 0.40.9 /
      app 2.2.0 pinned (values at gate1-logs/tenancy-window/otel/; digests in the case JSONs).
      Pre-deploy rider: Kafka + accounting + fraud-detection + accounting-POSTGRES ALL render with
      DEFAULT values (no values-enable needed); trimmed: load-gen/llm/product-reviews off (capture
      hygiene + not on the checkout path), grafana/prom/opensearch off, collector logs+metrics →
      debug-only (traces→jaeger untouched). Riders adjudicated: **psql read-back VERIFIED** (schema
      `accounting`, NOT public — bare `\dt` misleads; krb5 stderr = harmless Npgsql GSS probe;
      a GSS-disable override was tried + REVERTED, detour disclosed); **ack latency under broker-down
      VERIFIED FAST** (200 at ~0.02 s, produce fully async); **graceful-ad = REFUTED as an S2 case**
      (ads are browser-XHR; `/api/data` 500s honestly under ad-down while the SSR page stays 200;
      ~30 s gRPC reconnect-backoff datum); **flagd list RE-FROZEN vs deployed 2.2.0** (15 flags,
      13 3a-eligible; survey block governs the 3a vendor-flag quota). Recovery runbook (measured):
      a replaced kafka pod wedges both rdkafka clients → rollout-restart checkout+accounting+fraud.
◐ 2.4 Boutique deploy — **DEPLOYED (MYC 2026-07-16: deploy.sh verbatim, istio-injection, loadgenerator→0, 11 Running, smoke 200; M-yield 3-seed leg run).** Rider STILL OPEN: gRPC method-scoped Istio abort on `/hipstershop.CartService/EmptyCart` (HTTP/2 path match) live check.
☑ 2.5 Bookinfo: **DISCHARGED (tenancy Phase B, 2026-07-10c)** — the 0/0 state was SCALED not gone; scaled up + reviews→v3 VS ASSERTED (never re-applied; samples re-apply would have destroyed the pin), FP/TP captures done, scaled back to 0.
☐ 2.6 Post-reboot runbook items stay in force: re-create mist:mist RabbitMQ user + warm-up POST
      before any SS run; repo .sh files are CRLF → run CRLF-stripped copies; minikube stays stopped.
      **NACOS RESTART RULE (2026-07-10 incident, live-verified twice):** ANY nacos pod restart boots the
      member back into 1.X/double-write mode from the July PVC → the cluster REFUSES new gRPC service
      registrations ("Nacos cluster is running with 1.X mode") while already-registered pods keep serving
      → every NEW/restarted service pod crash-loops at registration (stuck rollouts, old+new pods
      coexist). FIX (in order): (1) if "Distro protocol is not initialized" → delete BOTH nacos pods
      together (sts recreates ordered; 2 members must re-form the cluster jointly); (2) wait
      /nacos/v1/console/health/readiness = OK (port-forward 18848); (3) `PUT /nacos/v1/ns/operator/
      switches?entry=doubleWriteEnabled&value=false` + verify switches show False; (4) delete any
      crash-looping service pods to reset backoff (RS recreates; they register immediately).
      **WSL flap cycle (same incident):** every BATCH of TT pod restarts triggers a ~5–15 min WSL
      unresponsive window (0x8007274c) while JVM boots spike memory; it self-recovers — do NOT
      `wsl --shutdown`, do NOT hammer probes (each connection attempt adds load); restart pods in
      SMALL batches and expect the window.
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
◐ 2.5.2 SS: **PARTIAL (tenancy Phase B)** — javaagents on orders/shipping/queue-master executed for the pair captures (768Mi same-patch bump; torn down after per pilot framing); front-end Node auto-instr DESCOPED for the pair (entry = orders server span; T12 case amendment) — full 2.5.2 enablement (if E2 needs front-end spans) remains.
◐ 2.5.3 TeaStore: Kieker→OTel converter spike OR pre-registered exclude branch → cases become
        `trace-uninstrumented` (NEVER conflated with by-construction — B-M5). **U3 DISPOSITION
        (pre-registered): the converter is on MIST's OWN critical path, not just E2's — without
        Jaeger the decisive OBSERVED_COMPLETE_ABSENT can never fire on TeaStore, so the exclude
        branch means TeaStore MIST verdicts live in the TIMEOUT_ABSENT stratum (reported separately,
        G1 R3#1 discipline) or NOT_EVALUABLE-by-instrumentation; any new non-trace absence gate would
        need its OWN S2-FP calibration before use.** **EXCLUDE BRANCH TAKEN for the Phase-C captures
        (2026-07-10): both TeaStore cases carry `trace_visibility=trace-uninstrumented` + trace cells
        `not_applicable` (captured⇒as-deployed); the converter spike itself (for MIST's own path)
        remains open — this row stays ◐ until the converter decision at 2.5/E2.**
◐ 2.5.4 Measured trace-coverage table per SUT (= the §8.5-2 disclosure). **TT ROW MEASURED (traced-capture
        wave 2026-07-10, agent 1.33.6 on 7 write-path services; from the committed trace exports):**
        cancel path = ts-cancel-service (server+client) → ts-order-service (server, JPA/JDBC spans) →
        ts-inside-payment-service (server /drawback + JDBC on persist legs); createaccount = inside-payment
        server + JDBC (in-process, no cross-service hop); adminroute = admin-route server/client →
        ts-route-service server + 6 JDBC (persist); adminbasic = admin-basic server/client →
        ts-contacts-service server + 3 JDBC. Context propagation across @LoadBalanced RestTemplate WORKS
        (single 24/31/15/13-span traces). NOT covered: ui-dashboard (nginx, uninstrumented entry),
        gateway (uninstrumented, header-transparent), user/auth services (not in wave scope), async/broker
        legs (TT has none on these paths). Agent noise: NacosWatch scheduler emits 1-span internal traces
        (excluded by the scorer's entry-server filter, disclosed).
        **SS ROW MEASURED (tenancy Phase B, 2026-07-10c; from the committed pair exports):** order path =
        orders (server, javaagent) → shipping (server+AMQP producer on control) → queue-master (AMQP
        `shipping-task process` CONSUMER span — the pair's discriminator; present 30-span control /
        absent 26-span fault) + queue-master's docker-socket POST error span on EVERY consume
        (env-conditioned k8s behavior, measured). front-end NOT instrumented (descoped, T12).
        **BOOKINFO ROW MEASURED (same wave):** Envoy sidecar spans mesh-wide (Telemetry 100%);
        productpage→reviews→ratings client/server pairs; ratings server span present on the 9-span
        control / ABSENT with the erroring reviews→ratings client span (≥500) on the benign leg.
        **TEASTORE ROW (Phase C): trace-uninstrumented as-deployed** (Kieker-only; the 2.5.3 exclude
        branch — no trace table; cases carry `not_applicable` trace cells).
        **OTEL-DEMO ROW MEASURED (Phase D; from the committed pair exports):** natively traced —
        checkout trace = frontend-proxy (server) → frontend (server ×2 + api-route internal) →
        checkout (server) → cart/product-catalog/currency/shipping/payment/email (server each; cart→
        valkey + product-catalog→postgres client spans) + checkout `publish orders` PRODUCER span
        (present AND CLEAN on BOTH legs — async local-enqueue); Kafka CONSUMERS continue in LINKED
        traces (accounting `receive orders` consumer + `order-consumed` internal + postgres INSERT
        client with `db.system.name`; fraud-detection `receive/process orders` consumers) → the
        scorer's `presence_scope=file` + merged per-leg exports. jaeger v2.17 query = `/jaeger/ui/api/*`;
        JVM SDK batch-export lag ~5-8 s (sleep before per-leg export).
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

## Step 4 — M-yield (1 h × 10 seeds spec-rich tier; 1 h × 3 thin; LLM-off disclosed) — ✔ COMPLETE
✔ **ALL 6 SUTs EXECUTED at pinned budgets (TT @ TT-omnibus + the 5-SUT M-YIELD-COMPLETION window 2026-07-16, `RESULT-myield-completion.md`; 3-cold review FOLDED 2026-07-16 — 6 blocking, all text/disclosure-layer, numbers unchanged): 29+10 seeds, 5145+~2700 tests, 26 clusters + reps + CROSS-SEED 10% audit (`CLUSTERING-myc.json`); NO yield statistic (rater-gated). CROSS-SUT FINDING (post-review CORRECTED mechanism — "budget-capped runs never reach the final round" was FALSE): observe arms ONLY at the enhancer FINAL ROUND, and the ARMED stretch is STARVED under 1 h budgets — SS REACHED+ARMED 10/10 yet 0 DI records (100% of 3440 writes 500'd at the type-naive tier ⇒ nothing durable to observe; kill lands mid-final-round before `maybeEndObserve`; jaeger-off tier cap = defect tier unreachable by construction); TT 4/10 armed w/ triple-coverage miss + 6/10 never reached = a tool-behavior datum, not a defect. Enablement: TeaStore/OTel confs generated from the E1 specs (+1 authored / 4 captured seed traces; DI descoped, five source-verified reasons); 2.4 Boutique → ◐ (deployed via deploy.sh verbatim + loadgen-0; abort-rider open). Disclosed deviations (RESULT disclosures 4/7): SS binding-smoke non-pass ⇒ ran DI-enabled-but-unclaimed; plan-§2 oracle-key disable not implemented (inert by construction).**
◐ (superseded detail) **TT LEG EXECUTED (TT-omnibus 2026-07-15, `RESULT-tt-omnibus.md`):** 10 seeds × 1 h, LLM-off,
  canonical registry armed; 1 natural-complete (2707/2707) + 9 killed-at-budget; **flagged events = 0**
  (DI observe 0 across seeds — hooked steps never covered the 2 registry triples in-budget; injected-fault
  detection 0/10 on the complete seed; killed seeds' Allure outcomes lost to per-seed wipes — disclosed);
  clustering vacuously satisfied at 0 flags; NO yield statistic (rater-gated). Seeds 8-10 degraded =
  environment-side degradation, ATTRIBUTED to the overnight nacos double-restart but not proven
  (live-only observation; seed-21 preceded the first restart — RESULT review B). **REMAINING =
  the named M-YIELD-COMPLETION window:** TeaStore/OTel 2.75 enablement (+ mist_authoring cost) +
  SS/Bookinfo/Boutique thin legs.
☐ Event→case clustering (endpoint × fault-signature × SUT); 1 representative + 10% audit sample →
  feeds the rater M-yield audit set (B-M3). Upstream filings for genuine finds DURING execution.
  (Convention frozen at `ttomni-phase0-protocol.md` (b); vacuous at the TT leg's 0 flags.)

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

## Step 6 — E2 comparator frontier — ✔ AT THE OFFLINE SCOPE (completion-set Phase B 2026-07-16, `b4/e2/RESULT-e2-frontier.md`)
✔ **The completed 6-arm × 4-visibility-class matched-recall table (`benchmark/scoring/
  matched-recall-table.json`, the A6 single mechanical path):** naive span-error (0
  positives + 2 FP) · tracetest_presence SURROGATE (labeled: span-assertion-semantics,
  live tool NOT run; REAL Tracetest specs AUTHORED-never-executed for the authoring-cost
  cell — symmetric with the per-case `mist_authoring` minutes) · db-span-presence (the
  1/1 invisible fabricated-ack CATCH = specification-locality measured) ·
  contract-invariant (LIVE flagship cells 5/5 conforming = the by-construction MISS;
  24 n_e — sidecar response payloads NULL corpus-wide, disclosed) · TraceAnomaly NOT
  CLEARED (no visible license + py3.6 + training-corpus input contract ⇒ the
  pre-registered construction-blindness branch) · + the MIST column (A5 census,
  provenance_class per cell). Per-visibility recall + N-vs-0 (MIST 2/2, shape arms 0,
  db-locality 1/1) + NOT_EVALUABLE buckets all in the artifact; measured-vs-stamped
  0 mismatches; flagship cross-check EXACT incl. cross-trace-generation. LIVE-tool
  cells (2.5.5) declared out-of-scope-disclosed.
☐ **E2-results ≥3-cold-review at this step's acceptance gate (plan §6 — B-MAJOR)** —
  runs at the completion-set wave close (with Phase C), per the confirmed rev-2 DoD.

## Step 7 — E5 ablations (TT ×5 seeds; 3–4 d) — ✔ EXECUTED at the pinned one-SUT-pair scope
✔ **(TT-omnibus 2026-07-15, `RESULT-tt-omnibus.md`):** exact-4 OAT × 5 reps on the cancel-refund
  pair, ALL UNIFORM — C0 paired FIRE · C3 (2× cap) FIRE (permanence: cap size irrelevant) ·
  C1 observe+jaeger fault=OBSERVED_COMPLETE_ABSENT · C2 observe−jaeger fault=TIMEOUT_ABSENT;
  **A2 (trace gate) = the only verdict-tier-moving axis, 5/5.** Config-only axes; re-probe +
  value-delta excluded-by-name (no toggles). Evidence `b4/ttomni/leg3/`.

## Step 8 — E6 packaging + review
☐ E6: standalone benchmark repo (Apache-2.0 + CC-BY-4.0 + component map; MIST by reference LGPL);
  index.generated + MANIFEST.sha256; large artifacts → Zenodo/OSF by hash; scoring-harness home +
  license decided (B-m4). (MIST study-commit pin: DONE back at 1.9.5 — verify all recorded verdicts
  carry it.)
☐ Benchmark ≥3-cold-review in sampled-reproduction form (k=5 re-runs + m=15 audits each).
✔ E3 trigger rate mined — completion-set A1 (f06a2e7, `b4/e3/`): re-scoped to the EXISTING
  logs (M-yield + TT-omnibus; the "E1-era logs" never existed — Step-3b superseded-by-MYC,
  disclosed); descriptive-only rails. (This row was flipped at the WAVE REVIEW — freeze
  row 316 + commit 9ed205a claimed the fold but the checklist edit was missed, the SECOND
  slip of this class after 3fdf477; disclosed per C-B1.)

## Standing constraints (never forget)
- Single box; 26 GB WSL for TT; tenancy schedule; never build while a graph is deployed; docker-exec
  recovery runbook; disk prune per wave; kind cluster "mist" **(footer refreshed 2026-07-16,
  MYC close): ALL tenants at 0 — trainticket (incl. infra sts), otel-demo (lone
  otel-collector-agent DaemonSet pod = known negligible residual), teastore, sock-shop,
  bookinfo (lives in `default` ns — no bookinfo ns exists), boutique (ns PRESERVED, all
  deployments 0); PVCs + helm releases persist; snapshots in /home/miaot/gate1-logs/ttomni/;
  minikube stopped; revival = `b4/runners/ttomni/revive-stage.sh` (TT full-graph) /
  `b4/runners/ttomni/myc-revive.sh` (teastore|oteldemo|sockshop|bookinfo per-SUT; SS needs
  the rabbit `mist:mist` user + warm-up POST) / boutique = the committed `deploy.sh`
  VERBATIM + loadgenerator→0.**
- All MIST-repo changes on `main_track`; no Co-Authored-By; no file deletion; FILE_INDEX + memory sync.
- Frozen docs change only via disclosed amendments (`c2-freeze.md` §6).
- Paper honesty riders: lead with the study; two-denominator S1; tell-free floor; MIST vs arm-3
  authoring-cost symmetry; Gate-4 wording "3 frontier trace comparators + contract-invariant arm".
