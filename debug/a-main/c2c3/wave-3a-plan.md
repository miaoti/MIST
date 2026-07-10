# Wave 3a-live plan — S1/S2 population on the STANDING tenants (rev 2, post-3-cold-review)

**Status: rev 2 — folds `REVIEW-3A-{A-oracle,B-capture,C-pc}.md` per
`REVIEW-3A-RECONCILIATION.md` (all three verdicts ACCEPT-WITH-FIXES). Execution gated on the
reviewers' unanimous confirmation of THIS revision.**
Scope: the live-tenant subset of checklist step 3a. Executes on the tenancy end-state (OTel-Demo
UP, TeaStore UP, TT at 0 — `tenancy-window-result.md` §3). NO MIST tool code (prep rule). NO TT
revival in this wave. Corpus schema rev-2 FROZEN (changes only via freeze §6 amendments — this
wave adds exactly one convention row, §1.3 below).

## 0. Why now / what it buys (facts grep-verified at review)
- All 7 non-TT cases today are `mist_bindable=false`; 11 TT rows are `mist_bindable=true`
  (4 discriminating positives + the tell-bearing natural + 4 controls + 2 benign traps). **Item 1
  adds the FIRST non-TT `mist_bindable=true` POSITIVE.** Value dependency stated plainly: at wave
  close the earned claim is "the corpus contains a non-TT machine-bindable read-back positive
  (design target preserved, enrolled for the MIST run)"; the claim "MIST catches a non-TT positive"
  is earned only at 2.5/E2 after the 2.75 enablement — this case is authored to be first in line.
- **Item 2 is the corpus's FIRST mesh-sever case of any status** (grep-verified: zero exist), making
  the captured cross-SUT mechanism set {flag, dependency-down, mesh-sever}.
- **Floor honesty (explicit gap arithmetic, C-M2):** distinct defect sites today = **7** (TT 4:
  cancel→refund [natural+fabricatedack = one site], createaccount, adminroute-add,
  adminbasic-contacts-add; SS 1: shipping-enqueue; TeaStore 1: order-confirm; OTel 1:
  checkout→Kafka→accounting). This wave adds **exactly +1** (OTel EmptyCart; item 2 adds 0 — a
  mechanism-multiplex on the existing order-confirm site, which §5 of the freeze refuses to count
  as a site). Post-wave: **8 sites vs the §5 15–18 / 21–28 bands.** Closure is carried by the
  TT F-corpus tranche (floor 6 / target 10, each B-m6-gated; unstarted) + SS carts (~1) + TeaStore
  order-items (~1) + Boutique (exactly 1, S1-minor): ceiling ≈ 17–21, and **the freeze's <20
  disclosed-finding branch is LIVE, not hypothetical.** Sequencing is tenancy economics (these SUTs
  are UP; TT revival is the expensive tranche), not ease.
- Item 3 measures the FP mode of the benchmark's **authored comparator column (arm-3 family)** —
  currently the strongest MEASURED comparator in the corpus (ran-and-caught on sockshop-enqueue,
  the OTel flagship, and both TT breadth positives) — on the survey's canonical pending-vs-missing
  trap. Calibrating a live competitor's failure mode is the S2 stratum's purpose (bookinfo
  precedent), not strawman demolition.

## 1. OTel-Demo `cartFailure` S1 + control (the bindable-read-back positive)
Mechanism `flag` / injection `vendor_flag` (deployed 2.2.0 flagd `cartFailure` — D3c re-freeze
row); the swallow is unmodified source (checkout discards `emptyUserCart`'s return; the flag routes
EmptyCart to a throwing `_badCartStore`). `write_shape=partial-aggregate` (the ORDER lands on both
legs — psql corroboration recorded per leg as the isolation artifact, A-6; the cart-empty effect is
what's lost). `doc_citation` = checkout source + the D3c-frozen flag description (A-M8 grounding).

**1-P0 — TOGGLE-MECHANICS PROBE (pre-capture gate, B-F1; item 3 inherits verbatim):** patch the
`flagd-config` ConfigMap (`cartFailure` defaultVariant off→on) → temporary PF to flagd's
evaluation/OFREP port (no standing PF exists) → poll the flag value until it flips; RECORD the
patch→effect latency. Fallback ladder if the CM patch does not propagate: flagd-ui API → flagd pod
restart (+ post-restart health probe + its ordering consequences recorded). RESTORE = re-apply the
D3c-frozen JSON, verify byte-equality AND a value flip-back poll. The probe script + outputs are
committed (`b4/runners/3a/`).

**Read-back (A-1 BLOCKING fix — effect-directed encoding, no schema change):** the observable is
the **CART-EMPTIED effect** on GET `/api/cart?sessionId=<uuid>` (JSON): `expect_without_fault=
present` (the emptied state present: `items == []`), `expect_with_fault=absent` (the emptied state
absent: the added productId still listed). Locator states the predicate; notes carry the R11-style
NOMINAL disclosure (the enum is effect-directed; the raw item is present-on-fault).
`mist_bindable=true` (api-get JSON).

**MIST cells (the reconciliation's named-convention resolution of A-2/B-F4/C-B1):**
`mist_readback_oracle: not_applicable` with the notes reason string **`bindable-pending-eval`**
(mist_bindable=true; modality api-get JSON; MIST enablement = a 2.75 decision; **enrolled for the
2.5/E2 run — enters the MIST recall denominator at the wave that runs it; NEVER pooled with T9
boundary rows**). FLAG design target preserved per T1 **with the A-3 predicate caveat verbatim**:
the expected durable effect is a REMOVAL (membership-absence on a list read-back); whether the
pinned engine (7d69de9) expresses removal-as-effect is itself a 2.75 enablement finding.
`mist_trace_shape_oracle: not_applicable` (Branch-B traced-but-not-run, natively-traced deploy —
the standard note). **A freeze §6 row pins the `bindable-pending-eval` convention** (this wave's
only freeze change).

**Trace columns (pin BOTH branches now, A-4; canary selects, probe confirms, pre-record):**
canary a healthy checkout trace, bind the cart EmptyCart span (service `cart`, op fragment
`emptycart`, kind server; presence_scope default — sync gRPC, same trace, VERIFIED at canary
against the linked-trace risk, B-F3). Then:
- **Branch α (expected): the EmptyCart span is PRESENT-BUT-ERRORING under the flag** →
  `tracetest_presence=no_flag` (existence-only: a MISS on the genuine leg) + `naive_span_error=flag`
  (the error span: a CATCH) — the inverse of the broker case, and the pair's value.
- **Branch β: the span is ABSENT under the flag** → `tracetest_presence=flag` (catch) +
  `naive_span_error` per the remaining error spans (pin at canary).
Both branches' full 7-cell sets are written in the case-JSON draft BEFORE the record leg; the probe
round selects α or β; **the case is authored iff the MASK holds** (200 ack + non-emptied cart);
**refutation branch (C-M5): if the N≥4 probes refute the masked semantics on deployed 2.2.0, the
case is NOT authored — dated survey correction, disclosed finding (C-m8/R9 precedent).**
Ack columns: status/schema/body = no_flag expected (success-shaped clean — verify no sentinel at
probe). Export query = **service=checkout ONLY** (A-5/B-F3: a service=cart query would pull the
cart-add trace and break the frozen exactly-one rule); selector rows committed into
`trace_score.py` BEFORE the first capture; probes → ≥12 s quiet gap → record → exactly-one
pre-check (the attempt-1 rule, restated per item).

Legs (control-first): control (flag off, verified) → fault (flag on, verified; N≥4 probe sessions
with distinct uuids; fresh session + email-marker per leg). Sidecars + per-leg exports + per-leg
`readback-psql.txt` (order landed both legs) + the cart read-back in the driver observation.
New spec file `oteldemo-emptycart-flow.yaml` (B-F11: own header, sharp-edge warnings copied).
Case JSONs: `oteldemo-emptycart-swallowed-001` + `oteldemo-emptycart-control-001`
(captures dirs `oteldemo-emptycart-{swallowed,control}/`).

## 2. TeaStore mesh-sever S1 + control (the corpus's first mesh-sever case)
Mechanism `mesh-sever` / injection `mesh_abort`: the T15-verified mechanics (plain VS abort **503**
on the persistence `/tools.descartes.teastore.persistence/rest/orders` prefix; sidecars temporarily
on webui+auth — the Phase-C rider already verified the end-to-end mask live). Deploy-shape parity:
BOTH legs captured with sidecars ON; deploy strings in BOTH case JSONs disclose it.

**Trace columns — PRE-REGISTERED EXCLUSION (A-7/B-F10 reconciled):** NO trace column is scored on
this pair. Rationale (pinned now, not decided at capture): (a) a presence target cannot exist even
on the control — with exactly 2 sidecars there is no persistence-side span and the app is
uninstrumented, so the T2 family can never validate; (b) the only error span available on the fault
leg is the VS abort itself — **the injector's own artifact, not SUT-emitted telemetry** — and
scoring naive off the injection tool's signature would credit the column with detecting the
experiment (bookinfo's Envoy spans were the fully-meshed SUT's genuine client failures under
dependency-down; different regime). The probe round records whether the temporary sidecars export
fragments to the istio-system jaeger; the case notes disclose their existence;
`trace_visibility=trace-uninstrumented` (app-level) with the fragment disclosure.
**A-9:** the probe checks whether order-items are written via a separate `/rest/orderitems` call
that the VS prefix does NOT cover (possible orphan rows with orderId −1 on the fault leg) —
measured + disclosed either way; the read-back of record stays the profile order-row.

**Teardown checklist (B-F2 BLOCKING — enumerated, verified step by step):**
1. Injection via the per-DEPLOYMENT pod-template label ONLY (`sidecar.istio.io/inject: "true"` on
   webui + auth) — **NEVER the namespace label** (a leftover ns label + any persistence/db restart
   = sidecar leak + the no-PVC DB wipe).
2. **HARD GUARD: no persistence/db restarts while any sidecar config exists.** Maintenance flag
   verified `false` before AND after the item (mechanism isolation).
3. N≥4 HEALTHY probes at sidecar-injection time (BEFORE the VS): user-ledger identities, distinct
   markers, orders land — the sidecars-on control baseline.
4. VS apply → interception verified (exec-curl from auth container, T15 pattern) → N≥4 fault
   probes (masked acks) → ≥12 s gap → record leg → VS DELETE verified (`kubectl get vs` empty).
5. Labels REMOVED from both deployments; rollouts back to 1/1 verified in both directions.
6. **:8082 PF re-created after EVERY webui pod cycle** (dies with the pod); re-verify page 200.
7. Post-teardown read-back of record (fresh session; the maintenance pair's discipline) + REST
   corroboration + final health (page 200, maintenance false, sidecars gone).
Runner artifacts committed: VS manifest + injection/teardown scripts under `b4/runners/3a/`.

Flow: new spec files `teastore-order-meshsever-flow.yaml` + reuse of the profile-readback pattern
via `teastore-order-meshsever-readback.yaml` (B-F11 headers); **identity ledger (B-F5): user11–17
are CONSUMED (repo records incl. the rider legs); NEXT-FREE = user18; this wave budgets
user18–user21**; per-identity pre-leg baseline read (profile carries no marker rows) recorded in
the runner log. Legs control-first. Case JSONs: `teastore-order-meshsever-masked-001` +
`teastore-order-meshsever-control-001` (captures `teastore-order-meshsever-{masked,control}/`).

## 2b. `teastore-order-depdown-specified-001` (paper-only, no cluster time — C-M4 fix)
A `capture_status=specified` case: mechanism `dependency-down`, label positive by construction,
carrying IN-FILE the UNSOUND-ON-THIS-DEPLOY disclosure (no-PVC db — the wipe destroys the absence
evidence; survey correction row cited) AND the deploy precondition under which capture WOULD be
sound (PVC-backed DB; the mechanism is source-true and deploy-shape-contingent). Never tallied as
a result (R2/R3). Net: TeaStore's min-3 floor claim points at **3 corpus rows** (2 captured + 1
specified-with-disclosed-capturability); checklist 2.2's floor wording is updated in the same
commit; §0's phrasing is "live evidence for 2 of the 3 legs".

## 3. OTel-Demo `kafkaQueueProblems` S2 (pending-vs-missing trap) — PROBE-GATED
The survey's canonical trap: flag on → duplicate sends + fraud-detection consumer delay → orders
DELAYED, not lost. The S2 case documents: clean 200 ack; the row lands LATE (delay measured and
recorded NEXT TO the verdict); an absence assertion sampled at the pinned horizon flags.

**Toggle**: inherits 1-P0 verbatim (probe, fallback ladder, frozen-JSON restore).
**Observation horizon (A-11/B-F6):** the flagship convention pinned in the case — psql read-back at
≈15 s post-ack; the export window closes at record-end + 2 s; both stated in the case JSON.
**Drain-before-record rule (A-11):** the record leg starts ONLY after all probe orders' rows have
LANDED (psql-verified) + a ≥12 s quiet gap — probe backlog can never satisfy the presence selector
inside the record window. **T2 family baseline (B-F9):** the consumer-span family is validated by
reference to the flagship `oteldemo-checkout-control` capture (same selectors, same deploy) + a
fresh pre-flag canary on the day. Selector row `oteldemo-kafkaqueue` committed pre-capture (B-F6).
**Kafka-stability trigger (B-F7):** ANY kafka pod restart during the item ⇒ the leg is INVALID and
the kafka-recovery runbook (restart checkout+accounting+fraud) runs before anything else; post-item
drain check (fraud backlog clears in ~minutes) + RAM check.

**PROBE ROUND (≤1 h, artifacts pinned per B-F8):** records the per-order placed→landed delay
distribution, dedupe evidence (row counts vs duplicate volume), duplicate-send volume, the deployed
2.2.0 semantics vs the D3c row, and ends with an explicit decision line:
- **AUTHOR** (delayed-not-lost confirmed, delay > horizon): pin ALL SEVEN expectation cells at the
  probe-freeze gate (A-12): status/schema/body no_flag; naive no_flag EXPECTED on the checkout-side
  entry trace (dedupe errors, if spans at all, land in accounting's linked traces post-window —
  verify at probe); `tracetest_presence=flag` (the trap firing, an FP on a negative — freeze-§4
  consistent, bookinfo precedent); mist_readback not_applicable (T9 sql-probe boundary, as the
  flagship); **mist_trace_shape not_applicable Branch-B, with the case's MIST targets
  PRE-REGISTERED in notes from the frozen QuiescenceGate mapping (C-M3): the pinned discriminator
  is TIMEOUT_ABSENT ≠ OBSERVED_COMPLETE_ABSENT — a fixed-window absence check would FP here and if
  MIST's own run later FPs, it is reported as MIST's FP (the trap traps everyone equally).**
- **NOT-AUTHORED** (delay < horizon — nothing distinguishes): clean no-case; dated survey note.
- **STOP (C-m8 split): rows actually LOST** ⇒ dated survey correction + a decision point — that is
  an S1-positive candidate (vendor-flag provenance), authored only under its own discipline in a
  later item/wave, never silently subsumed.
**Reporting language (C-M3, pinned now):** the case feeds per-column comparator-FP
characterization ONLY; it enters NO MIST-vs-comparator differential or win tally until MIST's
columns are measured on it.
Case JSON (author branch): `oteldemo-kafkaqueue-pending-benign-001` (S2 negative, by-docs; no
separate control — bookinfo S2 convention; capture dir `oteldemo-kafkaqueue-pending/`).

## 4. Out of scope (disclosed)
Boutique deploy + its 1 S1-minor case (below the write-path floor; survey-quota "optional"; its
exactly-1 site contribution is counted in §0's gap arithmetic). S3 wild-hunt, M-yield/M-prevalence
(step 4/5; rater-gated). Any MIST run or tool change (2.75/E2; user-gated). TT revival (2.5/E2
tranche). OTel paymentFailure/paymentUnreachable (LOUD per survey — not masked).

## 5. Discipline (inherited + this wave's additions, binding)
Inherited verbatim: T2 family-validation + divergence-as-measured; T4 pre-committed selectors
(canary-bound, committed BEFORE each item's first capture); N≥4 consecutive probes; fresh
identities/markers per leg (the §2 ledger); per-leg immediate exports w/ quiet gaps + exactly-one
pre-checks; sidecar leak checks; script-files-only for cluster ops; validator exit-0 after every
case; per-item commits; FILE_INDEX + freeze §6 + README counts + survey corrections in the same
commit as each case pair.
**Wave-start precondition check (B-F14):** TT at 0; OTel 21 pods + TeaStore 7 pods healthy; PFs
re-verified (8085/16687/8082/8083 — re-create any dead ones); RAM (`free`) recorded; maintenance
flag false; flagd ConfigMap byte-equal to the D3c-frozen JSON.
**Wave-close:** end-state declaration (what is up, what was torn down, PF list) appended to
`tenancy-window-result.md`'s NEXT section or the wave's own note; attempt-N retention convention
(failed capture attempts kept as `*-attemptN`, disclosed).
**Leg order:** control-first on every pair.

## 6. Budget + collapse order
**1.5–2 days**; each item's ~0.5 d INCLUDES its ~1 h close-out (validator + FILE_INDEX + freeze/
README counts + commit). Collapse order on any pressure: **1 → 2(+2b) → 3** (bindable-read-back
positive first: unique paper asset; mesh-sever second: first-in-corpus mechanism; the S2 trap
last: deferrable, its taxonomy already partially measured operationally in Phase D). Item 2b is
paper-only and rides item 2's commit (or lands standalone if item 2 is cut).

## 7. Expected corpus after (report as counts, never bare S1)
All items: 18 → **24 committed case files**; reported as **10 pos / 13 neg captured + 1 specified**
(the R1 reporting rule; the specified row is never tallied as a result). Items 1+2(+2b) only:
23 files = 10 pos / 12 neg captured + 1 specified. Item-1-only floor: 20 files = 9/11 + 0.
