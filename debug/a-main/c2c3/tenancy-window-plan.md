# Tenancy-window wave — plan REV 2 (3-cold-reviewed GO-WITH-CHANGES ×3 → folded; PENDING confirmation pass)

Date 2026-07-10. Owner: main_track. Status: **REV 2 — all three reviews folded per
`../REVIEW-TENANCY-RECONCILIATION.md` (T1–T15 are the binding pins; they override any older phrasing
they touch). Execution starts ONLY on the same three reviewers' unanimous ACCEPT of this revision
(user directive).** Live facts banked by review A: Envoy→Jaeger tracing ALREADY wired mesh-wide
(100% sampling); bookinfo alive at 0/0 WITH the reviews→v3 pin; sock-shop 0/0 with July topology;
TT = 47 deploys + 3 sts, no HPA; agent jar on node; helm 3.21.1 (no repos configured); RAM 1 GiB
free pre-window (Phase A mandatory first). Governing docs: `step2-execution-checklist.md`
§2.15/§2.2/§2.3/§2.5/§2.6 (the reviewed step-2 plan-of-record — this window plan EXECUTES those items
and adds the capture protocol); `c2-freeze.md` rev-2.1 (§4 evaluability); `benchmark/README.md` §8
scale plan; `traced-capture-wave-plan.md` rev-2 (the capture discipline this wave inherits);
`REVIEW-TRACED-WAVE-RECONCILIATION.md` (T-pins); prereg `prep/g3-sut2-triples-prereg.md` (SS-C).

## 0. Why this window (A-conference calculus)
The corpus review (REVIEW-CORPUS-RECONCILIATION) named the binding constraints: single-SUT
concentration, the unrun FP/TP precision pair, and the empty S3 stratum. The rater study is now
staffed (user) and WAITING on corpus. This window converts the granted TT-down tenancy into exactly
those three: (1) **the FP/TP pair's COMPARATOR columns measured + traces banked** — the MIST-side
precision claim (mist_trace_shape, the pair's only separating column per R4) stays PRE-REGISTERED
pending the named 2.5/E2 Branch-B run; until then the pair is comparator-measurement + trace-banking,
never presented as a MIST win (T1); (2) a SECOND and THIRD write-path SUT deployed + their first
captured cases (kills "one SUT, one fork" at the root — TeaStore's positive is NATURAL in-tree, no
fork at all; sockshop's TP is itself a natural second-SUT positive); (3) the corpus grown toward the
calibration floor that gates the raters' work (honest count in §1; the T13 benign rider feeds the
binding S2 side). User directive: complete everything not needing user input; plans reviewed to
acceptance first.

## 1. End-state contract (what is true when this wave closes)
- TT: scaled to 0 (helm infra + PVCs intact; §2.6 + nacos-doubleWrite rule revive it later). NEVER deleted.
- Bookinfo + Sock Shop: FP/TP pair captured-of-record with traces + scored cells → both cases flip
  `specified → captured`; then both SUTs scaled back down (they hold no other pending work).
- TeaStore: deployed (pinned tag+digests), §2.2 riders verified, its natural masked-write S1 + clean
  control captured (read-back modality; trace cells per the 2.5.3 pre-registered Kieker branch);
  left at scale 0 after captures unless Phase D RAM allows co-residence.
- OTel-Demo: deployed via the PINNED official helm chart (§2.3 rider: verify Kafka+accounting+fraud
  render BEFORE deploy), the flagship ASYNC Kafka→accounting acked-but-lost S1 + control captured
  (psql read-back rider), natively-OTel traces exported. End-state: OTel-Demo may stay up (sole big
  tenant) or scale to 0 — decided by the next step's needs, recorded either way.
- Corpus (T10, enumerated): 13 → **18 committed** (+2 TeaStore S1+control, +2 OTel-Demo S1+control,
  +1 sockshop control as a standalone case with the twin's trace_visibility regime value; the two
  flips add no count; bookinfo's control stays a provenance leg — S2 precedent has no control case)
  → reporting line **8 pos / 10 neg**; with the T13 benign rider **20 cases, 8 pos / 12 neg** (the
  rider's +2 are S2 — the raters' binding side: captured S2 goes 3 → 5 of the ≥20 the calibration
  floor needs; the floor itself closes at step 3a, disclosed).
- 2.5.4 coverage rows for SS/bookinfo/OTel-Demo (+ TeaStore = uninstrumented row per 2.5.3).
- **Next tenancy flip (T11, default pre-named): OTel-Demo stays up; the next window = OTel 2.75
  enablement + its 3a S1 quota, then flip to TT revival for the F-corpus/M-yield (TT revives from the
  Phase-A snapshot — which BEATS §2.6(b)'s stale `nacos=1`: nacos returns at 2/2 under the
  joint-restart + doubleWrite-PUT rule). User confirms at Phase E.**
- Every checklist item this discharges gets its row updated with a pointer (no dual-homing);
  partially-discharged rows say PARTIAL with the residue named (T7/T8 riders).

## 2. Phases (strict order; each phase independently valuable; stop rules in §6)

### Phase A — TT down + baseline (minutes)
`kubectl scale` all ts-* deployments to 0 (NOT delete; nacos/mysql sts + rabbitmq per §2.6 recovery
map — scale sts to 0 too, PVCs persist). Scale-down has no JVM-boot storm → no flap risk. Record: free
RAM before/after (expect ~15-20 GB free), a deploy-replica snapshot for revival. Kill the TT ui
port-forward. Jaeger + istio stay up (shared infra).

### Phase B — the FP/TP pair (the flagship half; bookinfo + sockshop co-resident)
B1. **Bookinfo: SCALE UP the existing 0/0 deployments — apply NOTHING that already exists (T5:
    a wholesale samples re-apply includes virtual-service-all-v1.yaml, which would DESTROY the live
    reviews→v3 pin; v1 never calls ratings and both legs would die).** ASSERT (read-only) before
    captures: VS `reviews`→subset v3, the 4 DestinationRules, the gateway; record those manifests
    into the case replay preconditions. Sock Shop revive: scale back up (14 pods, SMALL BATCHES per
    the flap rule), re-create the `mist:mist` RabbitMQ user + warm-up POST (§2.6), smoke `/catalogue`
    + a cookie-session order flow (the July SS-A pattern).
B2. Instrument the SS Java legs via the PROVEN hostPath+JTO runbook (agent 1.33.6 already on the kind
    node): `orders`, `shipping`, `queue-master` (the consume span — the traced-wave T-pin requires it).
    **T6 memory pin: the 3 services run 500Mi limits with JAVA_OPTS `-Xmx128m`, and a command-line
    -Xmx OVERRIDES anything in JAVA_TOOL_OPTIONS — so the SAME patch that adds the agent raises the
    container memory limit to 768Mi (recorded in the sidecar attestation); heap itself untouched
    (agent overhead is native/metaspace). Patch AT 0 REPLICAS, then scale up — one boot round.**
    Front-end Node instrumentation DESCOPED (disclosed; T12 pins the case-note amendment at flip:
    precondition revised to {orders/shipping/queue-master javaagents; entry = orders server span;
    front-end descoped + rationale}, dated, citing this plan). Canary discipline per service (banner,
    no OOME grep, smoke; expect boot-time OTLP retry noise while Envoy sidecars start — retries are
    not export failures, A-m10). Bookinfo needs NO agent (Envoy sidecar spans; the mesh Telemetry
    already exports at 100% sampling — live-verified).
B3. **Selector pre-commit (T4 — the full semantic surface pinned NOW; scorer extension COMMITTED
    before the first real capture; canary may bind ONLY serviceName/operation SPELLINGS and
    tag-encoding mappings, from control/throwaway traces only; span KINDS, dependency identity, the
    exactly-one rule, verdict mapping, scope sets, and the error rule's MEANING are immutable):**
    - Presence span KIND: SERVER for HTTP dependencies (bookinfo ratings), **CONSUMER for AMQP**
      (sockshop queue-master consume) — the frozen scorer's server-only assumption is extended, not
      reinterpreted.
    - Error rule (covers Envoy-emitted spans, pinned as MECHANICS not binding): a scoped-service span
      with `error=true` ∨ `otel.status_code=ERROR` ∨ `http.status_code ≥ 500`.
    - Naive scope = PER-CASE service sets: bookinfo {productpage, reviews, ratings (+their Envoy
      client/server spans)}; sockshop {orders, shipping, queue-master, rabbit client spans}.
    - Entry fragments pinned: bookinfo `GET /productpage`; sockshop `POST /orders`. Per-leg windowing
      restated: probe → FRESH capture window → immediate export (probes stay outside the window so
      exactly-one holds).
    - Entry-span tolerance: Envoy representation (kind/parent under the ingress-gateway) resolved at
      canary as a DISCLOSED binding, never a semantics change.
    Expected (pre-registered in the cases, now to be MEASURED — subject to the T2 divergence rule):
    bookinfo benign → naive FLAG (FP) + presence FLAG (FP; ratings server span absent at scale-0) —
    both structural columns fail the benign trap; sockshop genuine → presence FLAG (TP; consume span
    absent) and naive PINNED-AS-FLAG BUT LIKELY REFUTED (B-B1: rabbit@0 fails the connection before
    basicPublish → plausibly zero error spans → measured `no_flag` = a documented FN, making naive
    fail BOTH directions across the pair — recorded as measured if so). The pair discriminator
    remains SEMANTIC (mist_trace_shape), Branch-B traced-but-not-run this wave; the flips overwrite
    those cells to `not_applicable` (captured⇒as-deployed) while the displaced R4 design targets are
    PRESERVED VERBATIM in the notes as the pre-registered claim (T1).
B4. Captures (fresh ids; probe-first **N≥4 consecutive**; per-leg IMMEDIATE trace export; complete new
    sidecars = capture-of-record; quiescent cluster):
    1. bookinfo control (ratings up): GET /productpage → sidecar + trace → score (all no_flag).
    2. bookinfo FP leg (scale ratings to 0; probe = page renders "Ratings service is currently
       unavailable"): GET /productpage 200 → sidecar + trace → score. Restore ratings.
    3. sockshop control (rabbit up): register→login→cart→POST /orders → ack + queue-master consume span
       PRESENT in trace; readback nominal (none-durable) → score.
    4. sockshop TP leg (scale rabbitmq to 0; probe = orders still acks — the "Accepting anyway" swallow):
       same flow → ack 2xx + consume span ABSENT + shipping error span (naive TP) → score. Restore rabbit.
    Case updates per the §4 rules of the traced-wave plan (measured cells only from these artifacts;
    `mist_readback` stays not_applicable (none-durable); mist_trace_shape cells stay not_applicable with
    the Branch-B note). Sidecars carry the full agent/env attestation.
B5. OPTIONAL bounded rider (skip on any time pressure, ≤45 min): 2.5.5 Tracetest agent smoke vs the live
    bookinfo (`selected_spans.count = 0` absence assertion end-to-end) — E2 arm-2/3 wiring de-risk.
B6. **UNCONDITIONAL teardown (T3 — runs whether or not B5 was skipped):** scale bookinfo + sockshop
    to 0; de-instrument the 3 SS services (restore limits + envs); verify `free` before Phase C/D.

### Phase C — TeaStore (second write-path SUT, NO fork)
C1. Deploy per §2.2 (kind manifests; PIN release tag + image digests at deploy). RAM: TeaStore alone
    (~8 pods) after B's SUTs scaled down.
C2. §2.2 verify-at-deploy riders (survey, checklist wording restored — T8): live-confirm
    **200-ORDERCONFIRMED under maintenance / DB-down / mesh-503** — the mesh-503 leg CONDITIONAL on
    the plain-VS probe intercepting (else deferred-with-disclosure to 3a); recommender cold-start
    semantics VERIFIED (feeds the T13 benign capture); exact digests into the case YAMLs. The
    mesh-sever client-LB rider (C-M4) is TIME-BOXED (T15): plain VS host-match check ONLY, ≤2 h; a
    miss IS the min-3-floor datum; EnvoyFilter authoring belongs to 3a.
C3. Captures: TeaStore natural masked-write S1 (fresh user CREATED AND LOGGED IN BEFORE the DB goes
    down — B-m3; order placed under DB-down → 200 ORDERCONFIRMED ack → read-back: order ABSENT from
    the user's order list after DB restore) + clean control (order lands). Sidecars per B4 discipline.
    Trace `oracle_expectation` CELLS = `not_applicable` all three (captured⇒as-deployed, B-m2);
    `trace_visibility = trace-uninstrumented` per the pre-registered 2.5.3 Kieker branch (NO converter
    spike this window; TeaStore MIST verdicts live in the TIMEOUT_ABSENT stratum discipline — the case
    notes carry the disposition verbatim). Author + validate the 2 case JSONs (rev-2; natural/by-docs
    with source citation). **If C2 REFUTES the masked write: capture NEITHER leg (no dangling twin,
    R9 precedent); the S1 stays `specified`, the survey is corrected — a disclosed FINDING (C-m8).**
C3b. **T13 benign rider (≤0.5 day total across C+D, skip-on-pressure, AFTER the committed captures):**
    TeaStore recommender-cold-start captured as an S2 benign case (by-docs; doc_citation = the C2 rider
    verification).
C4. TeaStore → scale 0 (or keep if Phase D RAM allows; record).

### Phase D — OTel-Demo (the ASYNC flagship; big-solo tenant)
D1. **Pre-deploy rider (§2.3, BEFORE any install):** `helm repo add open-telemetry …` (or fetch the
    pinned chart tarball — which also serves the pinning requirement, A-m8), then render the PINNED
    chart version locally (`helm template`) and CONFIRM **Kafka + accounting + fraud-detection + the
    accounting POSTGRES wiring** (A-M4) are in the k8s rendering; else values-enable or fall back to
    rendered-manifests. Pin chart version + app tag in the record.
D2. Deploy on kind (Jaeger already present; wire the demo's collector → existing Jaeger OR the chart's
    own Jaeger — decide at D1 from the rendering, record). Smoke: frontend up, PlaceOrder round-trip OK,
    accounting consuming (psql probe rider: the accounting-Postgres read-back — §2.3 rider).
D3. Captures: the ASYNC acked-but-lost — PlaceOrder acks while the Kafka→accounting leg is faulted
    (accounting down or Kafka down — choose at D2 per which faults cleanly + restores; §2.3 rider:
    confirm the ack path stays fast under broker-down) → order ack 2xx, accounting Postgres row
    ABSENT (psql read-back per the rider) + clean control (row lands). **T9 convention (pinned):
    `readback.modality = sql-probe`, `mist_bindable = false` (provisional pending the 2.75 enablement
    decision — single-homed there) ⇒ `mist_readback_oracle = not_applicable` with an
    APPLICABILITY-BOUNDARY note (the read-back exists; MIST's oracle cannot bind the modality at the
    pinned commit — distinct from none-durable); freeze §6 gains this convention row at close-out.
    DISCLOSED: as authored this wave the OTel case carries NO runnable MIST column (readback
    unbindable + trace-shape Branch-B) — it is comparator-measurement + boundary-documentation until
    MIST runs.** OTel-Demo is NATIVELY traced → export traces per leg; selector entries pre-committed
    before D3 captures (T4 discipline; binding from D2 canary traces only).
D3b. **T13 benign rider (second half):** OTel-Demo frontend graceful-ad degradation captured as an S2
    benign case (restores the §2.3 rider — T7; by-docs, doc_citation = the rider verification).
D3c. **flagd list re-freeze vs the pinned tag (§2.3 rider restored — T7; ~30 min):** feeds the 3a
    OTel vendor-flag S1 quota.
D4. End-state decision recorded per the §1 default (stay up) + coverage row + case JSONs validated.

### Phase E — close-out (file-local)
Corpus re-validated (all cases exit 0); README §2/§8 + freeze §6 amendment row (counts + measured
cells); FILE_INDEX; checklist rows 2.15(b,c)/2.2/2.3/2.5(bookinfo)/2.5.2(partial)/2.5.4 rows/FP-TP
updated with pointers; memory arc; the wave result note (earned vs deferred per leg). Commits per
phase (each phase lands as its own commit so a stop rule never strands work).

## 3. Capture discipline (inherited pins, restated as binding)
**T2 — family validation + divergence rule (NEW, binding):**
- Per span FAMILY (HTTP server / HTTP client / AMQP producer / AMQP consume / Envoy client / Envoy
  server), a family counts as INSTRUMENTED iff the CONTROL leg produces it. For validated families,
  fault-leg cells are **measured as scored** — the absence of an error span is a real `no_flag`,
  recorded. For families ABSENT on control, the dependent cells stay `not_applicable`/unfilled + an
  instrumentation-gap disclosure (never a silent pass).
- Any measured verdict that DIVERGES from a case's pre-registered `oracle_expectation` is recorded AS
  MEASURED; the expectation is corrected with a dated "design expectation refuted/revised by capture"
  note; the pair/case narrative is re-derived from measured cells only; and **NO selector, scope, or
  error-rule change may be made after the first real capture** to chase an expectation.
- Presence `flag` (absence) cells are claimable ONLY with the same-deploy control-leg baseline
  producing the span (T1 traced-wave pin), AND only under intact context propagation — a consume span
  present as a SEPARATE UNLINKED trace counts as NOT-claimable absence (window correlation supports
  presence only) — applied to sockshop and bookinfo alike (B-m4, A-m6).
Fresh ids per leg (hardcoded-id specs bump ids every run); probe-first with **N≥4 consecutive**
expected-behavior probes after ANY topology change; per-leg IMMEDIATE Jaeger export + non-empty check
(badger on emptyDir); complete new sidecar per leg = capture-of-record; scorer selectors committed
BEFORE real captures (name-binding at canary disclosed); `mist_trace_shape` NEVER hand-simulated
(Branch-B notes); every measured cell cites its artifact; a failed leg's cells stay unfilled +
disclosed (no silent weakening); DB-granularity report accompanies every traced pair where a persist
exists; quiescent cluster during captures; agent config + no-suppression attestation in sidecars.

## 4. RAM / tenancy arithmetic (26 GB box; flap rule: small batches, expect windows)
TT@0 frees the bulk (53 pods). Concurrent sets: {istio, jaeger, bookinfo(~6), sockshop(14), 3 agents}
= Phase B (well under budget); {istio, jaeger, TeaStore(~8)} = Phase C; {istio, jaeger, OTel-Demo
(~15-20 incl. Kafka)} = Phase D solo per §2.15. Between phases: scale down before scaling up; `free`
+ pod-health checks at every boundary; ≥2 GB available before starting any phase else stop/trim.

## 5. Risks (pre-registered handling)
| risk | handling |
|---|---|
| SS AMQP spans missing (javaagent spring-rabbit coverage on this SS version) | canary FIRST on queue-master with a warm-up order; if no consume span → presence cells stay unfilled + disclosed; naive/entry spans still land (orders/shipping HTTP) |
| SS front-end cookie flow breaks (July pattern drift) | reuse the July SS-A cookie session recipe verbatim; if register/login fails → fix per July notes (VS /register /login routes are committed in evaluation/suts/sockshop/deploy) |
| bookinfo Envoy spans not linked (istio sample header propagation) | canary: one throwaway /productpage trace must show productpage→reviews→ratings linkage; if broken → window-correlation rule (absence NOT claimable, presence-only) per the traced-wave M1 fallback |
| TeaStore 200-ORDERCONFIRMED not reproducible on the pinned tag | the §2.2 rider verifies at deploy BEFORE captures; if refuted → the TeaStore S1 stays `specified` + survey corrected (a FINDING, disclosed — do not force it) |
| OTel-Demo chart too heavy for the box | D1 rendering shows the pod set; values-disable non-essential components (load-generator, opensearch/grafana etc. — record every disable); if still infeasible → rendered-manifests minimal set |
| OTel-Demo accounting/Kafka absent from chart version | D1 pre-check catches it; values-enable or fallback per §2.3 |
| WSL flap during SS revival / OTel-Demo boot | small batches, expect 5-15 min windows, never wsl --shutdown, no probe-hammering |
| Jaeger service-name drift (Envoy vs javaagent vs OTel-SDK naming) | name-binding at canary (disclosed) — semantics pinned in this plan, strings bound from throwaway traces before real captures |
| rabbit scale-0 wedges SS consumers (July restart lore) | §2.6: re-create mist:mist user + warm-up POST after every rabbit revival; restore order: rabbit → queue-master restart if consumers wedged |

## 6. Budget + stop rules
Budget: **4-5 working days** (re-pinned per reviews A+C) for A→E. Per-phase stop rules: any phase
exceeding 1.5 days → land what's banked (its commit), write the disclosure, move on; Phase B is the
priority half (if the window delivers ONLY Phase B, **the pair's comparator columns are measured and
the traces banked — the MIST-side claim stays pre-registered** — acceptable minimum, T1 wording);
**on time collapse the order is B then D — C (TeaStore) is the deferrable phase** (T14: the OTel
async case is the class flagship and needs big-solo tenancy; TeaStore fits any later small window);
TeaStore rider refutation (C2) does NOT block Phase D; a second same-class capture-hygiene incident
in one phase → pause that phase, harden the runbook rule first. Nothing in this wave touches MIST tool code
(prep-only rule stands); no case label ever derives from tool output (anti-circularity).

## 7. Out of scope
Boutique (below the write-path floor — thin-E1 later); the TeaStore Kieker→OTel converter spike
(2.5.3's own item); TraceAnomaly corpus (2.5.6) unless a free ≤30-min normal-traffic grab is possible
during Phase B/D canaries (optional rider, disclosed config-drift caveat if skipped); wild-hunt/S3
detector runs (step 5 — needs the wave-runner 2.8); E1/E2 tool runs; any TT revival.
