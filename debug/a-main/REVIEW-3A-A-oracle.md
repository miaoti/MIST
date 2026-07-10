# COLD REVIEW A — wave-3a-plan.md (oracle-soundness / case-semantics lens)

**Target:** `debug/a-main/c2c3/wave-3a-plan.md` (DRAFT rev 1). **Reviewer:** cold reviewer A, no
shared context with the author. **Method:** repo files only (plan; `c2c3/c2-freeze.md` rev-2.1 incl.
§6 rows through 2026-07-10 + the T9 convention row; `c2c3/c2-depth-survey.md` incl. the dated
2026-07-10 correction blocks; `c2c3/tenancy-window-result.md`; `benchmark/schema/fault-case.schema.json`
+ `schema/validate_cases.py`; `benchmark/b4/trace_score.py`; precedent cases
`oteldemo-checkout-lost/-control-001`, `teastore-order-maintenance-masked-001`,
`bookinfo-ratings-benign-001`, `sockshop-shipping-swallowed-enqueue-001`,
`TT-contacts-noop-modify-benign-001`, `TT-adminroute-lostwrite-001`, plus a corpus-wide grep of every
`mist_bindable` / `mist_readback_oracle` / `capture_status` cell). No cluster/network commands run.

## VERDICT: ACCEPT-WITH-FIXES

The plan is substantively sound: no label unsoundness found, no fabricated cell, the item-2
no-scoring decision is honest (not convenience), and the item-3 self-test is freeze-consistent with
a real precedent. But it carries one blocking encoding gap (the schema literally cannot express
item 1's inverted read-back pair), and several MAJOR pins that must land **pre-capture** or the
wave's headline case (the first non-TT bindable read-back) ships with ambiguous or refutable
semantics. All fixes are concrete and cheap; none require a schema-key change or a scorer change.

---

## Findings

### Item 1 — OTel-Demo `cartFailure` S1 + control

**1. [BLOCKING] The readback expectation pair cannot be encoded as the plan implies — pin the
inverted-polarity encoding before capture day.**
The plan's read-back is residual-state: control = cart **emptied** (`items []`), fault = item
**still present**. The frozen enums run the other way and have no inverse values:
`expect_without_fault ∈ {present, landed, count-delta-positive, span-present}` (no `absent`),
`expect_with_fault ∈ {absent, not-landed, count-delta-zero, span-absent}` (no `present`)
(`fault-case.schema.json` readback block; freeze §2). Writing `present`/`absent` naively would make
the frozen field **literally false** (the item is NOT present without fault), and any other pair
fails `validate_cases.py`. Fix (choose one, pin it in the plan rev): **(a) recommended —
effect-directed parameterization, no schema change:** define the typed observable as *the
cart-emptied effect for the session*, with the locator spelling it out verbatim, e.g. `locator:
"GET /api/cart?sessionId=<uuid> — the observable is the CART-EMPTIED EFFECT of the acked checkout:
'present' = the emptying write is present (items == []); 'absent' = the emptying write is absent
(the added item remains — residual state)"`, plus an R11-style note in `provenance.notes` (exact
precedent: bookinfo's "expect pair NOMINAL — the enum has no no-write value" disclosure); or
**(b)** a §6 amendment extending the two enums (heavier: schema+freeze lockstep bump). Do not leave
this to capture-day improvisation. The same pin applies to the control case JSON.

**2. [MAJOR] The `mist_bindable=true` + `mist_readback_oracle=not_applicable` pin is right in
substance but needs (a) a de-garbled plan sentence, (b) a NAMED third convention with its own
freeze §6 reporting row, (c) an explicit non-following of the noop-modify precedent.**
Adjudication the plan asked for: **no schema conflict** — the only `mist_readback`-forcing
constraint is `modality==none-durable ⇒ not_applicable` (allOf #2); nothing ties `mist_bindable`
to the cell, so `(mist_bindable=true, cell=not_applicable, captured)` validates. It is also
machine-distinguishable from T9 (every T9 instance has `mist_bindable=false` — verified corpus-wide:
TT cases are `true` with *verdict-valued* cells from waves where MIST actually ran; all non-TT cells
are `false`+`not_applicable`). But it IS a **third species** and the plan must say so: (i)
`none-durable` = no durable read-back exists (schema-forced, never in any MIST denominator); (ii)
**T9 boundary** = read-back exists, MODALITY unbindable at the pin (own applicability row,
*excluded* from MIST recall denominators — freeze §6 2026-07-10 T9 row); (iii) **bindable-but-not-run**
= modality bindable as-deployed, MIST simply has not run (the read-back analog of the trace-shape
Branch-B convention the corpus already uses). The T9 row's trigger ("MIST cannot bind the MODALITY")
does NOT cover item 1, so absorbing this cell into the T9 applicability row would silently shrink
MIST's future recall obligation — exactly what a hostile PC would probe. Fixes: (a) rewrite the
plan's self-contradictory parenthetical ("the CELL stays `not_applicable`? NO — pin: … `not_applicable`
recorded") into a clean pin statement; (b) add a freeze §6 convention row in item 1's commit:
*bindable-but-not-run cells are pending-eval, ENTER the MIST recall denominator at the MIST-run wave
(2.5/E2 or a 2.75-gated run), are never merged into the T9 applicability row, and the cell is
updated from `not_applicable` to the measured verdict by a dated amendment when MIST runs; the FLAG
design target lives in notes per T1*; (c) note the corpus tension explicitly:
`TT-contacts-noop-modify-benign-001` records a **design-target `no_flag` IN the cell** of a captured
case — the wave must not replicate that pattern for item 1 (a target-valued cell on a captured case
is presentable-as-result, the exact hazard R7/C-F1 and the traced-wave "ran-and-missed" amendment
exist to prevent); the convention row should state which practice governs going forward.

**3. [MAJOR] `mist_bindable=true` is modality-honest but the preserved FLAG design target presumes a
should-be-ABSENT predicate MIST may not have — verify or caveat.**
The schema field is defined as "can MIST's oracle bind this MODALITY" — JSON `api-get` is MIST's
native modality, so `true` is schema-honest. But every existing bindable target is
membership/agreement/value-delta with normal polarity (submitted row should be PRESENT; TT
precedents). Item 1's target assertion is inverted: *the previously-present item should be ABSENT
after the acked checkout*. If the pinned commit's (7d69de9) predicate vocabulary cannot express an
emptiness / should-be-absent postcondition on a read-back, the true boundary is PREDICATE-level and
the headline "first non-TT machine-bindable read-back" oversells. Fix: verify the predicate
vocabulary against the pinned commit before the paper claim (a read, not a tool change — the no-tool
rule is untouched), and in all cases write the preserved design target with the polarity explicit:
"mist_readback FLAG target requires a should-be-absent/emptiness postcondition (inverted-polarity
binding); if absent from the pinned vocabulary this becomes a 2.75 enablement decision and the case
note must say so."

**4. [MAJOR] Do not leave the presence-semantics branch open — pin BOTH branches' full expectation
sets now; authoring must not be conditional on the asymmetry materializing.**
Adjudication of lens Q3: probe-then-freeze for **selector names** is T4-sanctioned (Phase-D
precedent: names bound from canary traces, committed pre-capture). But deciding **expected
verdicts** only after seeing a fault-leg canary is weaker than the corpus's own best practice — the
traced wave PINNED `naive=flag` on sockshop pre-capture and disclosed the refutation as a measured
T2 divergence. The honest middle, which the plan should adopt verbatim: enumerate both branches in
the plan NOW — **default (source-derived):** `_badCartStore` throws inside the gRPC handler → the
cart `EmptyCart` SERVER span is PRESENT-but-erroring → `tracetest_presence=no_flag` (MISS, since
presence is existence-only — verified against the frozen scorer semantics: "the error/status axis
belongs exclusively to naive_span_error") + `naive_span_error=flag` (CATCH, cart in the pinned
scope); **alternate:** span ABSENT under the flag → `presence=flag` catch, `naive` per canary. The
probe selects the branch; the selected branch's full 7-cell set + scorer selector rows are committed
BEFORE the capture-of-record; behavior outside both branches → stop + disclose. And pin: the CASE is
authored in EITHER branch (the bindable read-back positive is the case's value; the
inverse-of-the-broker asymmetry is a bonus datum, not an authoring condition). Only a refuted MASK
(no clean 200 ack, or the cart actually empties) kills the case.

**5. [MINOR] Pin the export query for item 1 — a `service=cart` window query breaks the frozen
exactly-one rule.**
The stimulus contains TWO frontend-proxy POST entries (add-to-cart `POST /api/cart`, then
`POST /api/checkout`). The flagship's export was safe because its window queries were
`service=checkout` (+`service=accounting`) — the add-to-cart trace touches neither and is excluded.
An implementer of item 1 might naturally query `service=cart` (the presence target's service): that
returns the add-to-cart trace too, which contains a frontend-proxy POST server span → TWO
entry-matching traces → `trace_score.py` hard-fails the exactly-one rule. Fix: pin export =
`service=checkout` windowed query only (the entry trace already contains the cart `EmptyCart` server
span via sync gRPC propagation — confirm on the healthy canary; `presence_scope` default, no merge
needed since accounting is unasserted; the flagship's merged form is harmless if reused, `service=cart`
is not). Also: new scorer SELECTOR rows (`oteldemo-emptycart-swallowed`, `oteldemo-emptycart-control`,
and item 3's ids) must be committed pre-capture per the Phase-D extension pattern; prefix-collision
check against the existing `startswith` table passes (verified).

**6. [MINOR] Ground-truth grounding (A-M8) + partial-aggregate evidence for item 1.**
Label = genuine positive is sound in structure (the swallow `_ = cs.emptyUserCart(...)` is natural
in-tree source; the vendor flag only produces the downstream failure the swallow masks — same
construction as the flagship's broker-down). Two hardening fixes: (a) freeze §5 A-M8 requires
contract-grounding for plausibly-best-effort writes — attach the citation set pre-capture (the
source call itself + the deployed 2.2.0 `cartFailure` flag description from the re-frozen ConfigMap
+ demo docs if any state checkout empties the cart) or explicitly disclose the case rests on the
construction bar; pre-empt the foreseeable rater objection "cart is ephemeral session state, not
data integrity" in the rationale (server-side store-of-record; residual cart → double-purchase
exposure). (b) `write_shape=partial-aggregate` is correctly claimed (sockshop precedent:
parent/order lands, child effect lost) — but the "order lands on BOTH legs" isolation evidence must
be a recorded artifact (psql output in the capture dir / sidecar), not only "in the runner log" as
the plan currently says.

### Item 2 — TeaStore mesh-sever S1 + control

**7. [MAJOR] Not scoring the 2-sidecar Envoy fragments is SOUND — but pin `trace_visibility` and
disclose the erroring client-span fragment honestly, keyed to a probe-time telemetry check.**
Adjudication of lens Q4: this is not fabricated-convenience, for two independent reasons the plan
should state more sharply. (i) The frozen presence semantics require "a SERVER-kind span of the
case's dependency service" — the dependency (persistence) carries NO sidecar, so the presence target
**cannot exist even on the control leg**, which is precisely the T2/B-B4 family-validation failure
mode; scoring it would fabricate an unbindable assertion target. (ii) The bookinfo precedent is not
merely "fully meshed": bookinfo's apps forward B3 headers (that is why it is Istio's tracing
sample), so its Envoy spans form ONE connected, entry-selectable trace. TeaStore's Kieker-only app
propagates nothing, so webui-entry and auth→persistence client spans land as DISCONNECTED roots — a
`naive` verdict computed over the selected entry trace would be an instrumentation artifact, and a
file-merge (Phase-D pattern) was justified there by span-LINKED async traces, not by broken
propagation. Required fixes: (a) at the probe round, check whether teastore-ns sidecar spans are
exported at all (is a mesh-wide Telemetry CR from Phase B still active, and does the collector path
exist for this ns?) and record the answer; (b) pin the case field accordingly — if no spans export,
`trace_visibility=trace-uninstrumented` (maintenance-twin precedent) + note; if fragments DO export,
the auth-sidecar CLIENT span carrying the 503 abort EXISTS on the fault leg — then the honest
encoding is a note disclosing that fragment (visible-in-principle, unscoreable under the frozen
entry/exactly-one/presence semantics for the reasons above) and a deliberate `trace_visibility`
choice justified in the notes (the enum has no partial-mesh value — that is a disclosure, not a
silent pick); (c) optionally archive any raw fragment export unscored (cheap, hostile-PC-proof
evidence for the not-scoring decision). Without (a)/(b) the E2 per-trace-visibility aggregation view
(frozen §4) risks a mislabeled axis.

**8. [MINOR] "The min-3 floor's live evidence" overstates — state the floor reading.**
After item 2, TeaStore has 2 mechanisms CAPTURED (flag, mesh-sever) and dependency-down
**specified-only and UNSOUND-for-capture on this deploy** (no-PVC wipe destroys the absence
evidence — survey 2026-07-10 correction). If the §5 broker-less min-3 floor is read as "captured",
TeaStore cannot close it on this deploy shape, ever. The plan already disclaims floor closure
(good); fix the wording to say which reading applies and that the third mechanism remains a
disclosed specified-unsound row (R3 honest-recount ethos), not "live evidence" toward min-3.

**9. [MINOR] Verify-and-disclose the orphan order-items side effect on the fault leg.**
The VS aborts the `/rest/orders` prefix, which does NOT match `/rest/orderitems`. Per the survey,
`placeOrder` writes the order row first, then order-items in a loop, and never checks the `-1L`; with
persistence UP (unlike maintenance), the items loop may land orphan `orderitem` rows referencing
orderId `-1` on the fault leg. This does not damage the case's absence evidence (the profile Orders
read-back keys on the order row/marker), but it mutates shared DB state and touches the "DB intact
throughout" style of claim. Fix: check at the probe round; disclose either way in the case notes.

**10. [INFO] Provenance/rationale fields for item 2 — follow the flagship/sockshop pattern.**
Pin `ground_truth.source=natural` + `fault.provenance_class=by-docs` (the swallow is natural
in-tree source — NonBalancedCRUDOperations throws only on 404/408, placeOrder never checks `-1`;
the 503 outage is injected — the flagship's exact structure), rationale NORM = the profile
order-history page as the documented read-back surface (mirrors the accepted maintenance twin), and
the deploy strings disclosing sidecars-ON for BOTH legs (the plan's parity pin is correct and
answers the only-variable-is-the-VS requirement; distinct case ids keep the maintenance pair's
sidecarless shape uncontaminated — good).

### Item 3 — OTel-Demo `kafkaQueueProblems` S2 (probe-gated)

**11. [MAJOR] Pin the observation horizon AND add a drain-before-record rule — otherwise the
expected FP is mechanically refutable by the scorer's correlation-free presence selector.**
Adjudication of lens Q5 (second half): the S2 case is sound ONLY relative to a pinned horizon. The
plan says "sampled at the standard window" but never defines it. Two required pins: (a) **horizon**
— the standard = the flagship's per-leg immediate export with quiet gap and windowed queries; the
probe-measured landing delay must exceed that horizon by a stated margin (pick one and write it,
e.g. delay ≥ 2× the ack→export lag) or the `tracetest_presence=flag` expectation is
non-deterministic, violating "baseline columns deterministic by construction" (freeze §2); the probe
gate's "delay < the export window ⇒ not authored" branch already exists — it just has no defined
window to compare against. (b) **drain-before-record** — the scorer's presence check matches ANY
(`accounting`, consumer, "receive orders") span in the file with NO order-id correlation. Under this
flag the queue is backed up and DRAINING: late consumer spans from the N≥4 PROBE orders landing
inside the record window would satisfy the selector → `presence=no_flag` → the case's point refuted
by cross-order contamination (the flagship never faced this: broker down = zero consumer spans).
Fix: after the probes, wait until the probe backlog is fully drained (all probe rows landed via
psql + an export pre-check showing zero accounting consumer spans in a fresh window) BEFORE the
capture-of-record; load-generator is disabled in this deploy (pinned values), so no background
orders compete. Also pin the two observation TIMES and their roles: comparator columns observe at
the standard window (absence = the trap); the ground-truth sql-probe of record observes after the
measured delay (landed = the benign truth) — both recorded.

**12. [MAJOR] Pin all seven `oracle_expectation` cells at the probe-freeze, not just presence; cite
the T2 family evidence.**
The schema requires all seven; the plan pins one. Expected set to pre-register (probe-confirmed):
status/schema/body `no_flag` (clean 200 ack); `naive_span_error` — nominally `no_flag` (checkout-side
producer spans are present+clean per the flagship; accounting's dedupe unique-violation error spans,
if any, land POST-window so they cannot flip the verdict at the standard horizon), but the probe
canary must confirm the 100-duplicate flood does not produce client-side error spans inside the
entry trace; `tracetest_presence=flag` (the documented FP); `mist_readback=not_applicable` (T9
sql-probe boundary, flagship-identical, with the no_flag design target in notes); `mist_trace_shape=
not_applicable` (Branch-B). Any probe divergence → record as divergence-as-measured or take the
not-authored branch. Also state where the T2 family validation comes from: the Phase-D flagship
CONTROL capture validates the accounting consumer-span family on the same deploy + same selectors —
that is why "no separate control (S2 convention, bookinfo precedent)" is acceptable here; say it
explicitly so the FP attribution is not challengeable as instrumentation failure.

**13. [INFO] Item 3's label and self-test framing are SOUND as gated — confirmed against the freeze.**
`tracetest_presence_oracle: flag` on a `label=negative` case is exactly the bookinfo precedent
(REVIEW-CORPUS R4 blessed "HONEST tracetest_presence=flag"; measured FP this window), and freeze §4
scores it as the column's FP ("any benign fault leg should NOT fire (else FP)") — the expectation
cell records what the column WILL do, the label says what it SHOULD do; recording our own comparator
column's FP mode is anti-circularity-healthy (the label derives from the flag's own vendor
description + the probe-verified landing, never from any oracle verdict). The rubric's async
tie-break aligns: absence within the documented delayed-not-lost behavior, row lands → benign, not
genuine. The refutation branches are correctly designed and genuinely live: the survey places the
1s/msg sleep in fraud-detection (a separate consumer group), so accounting may consume promptly →
delay≈0 refutation is plausible — the gate's not-authored + disclosed-survey-finding branch
(C-m8/R9 precedent) is the right handling; note that the "rows actually lost" branch would be a
survey CORRECTION (and a candidate S1), which the disclosed-finding path also covers.

### Cross-cutting

**14. [INFO] Latent schema-description tension — do not propagate; §7 counts verified.**
The schema's description sentence ("an oracle is CORRECT … which for baseline columns equals
(verdict==flag)==(label==positive)") is already contradicted by expected-FP rows (bookinfo, and
item 3 will be the second); the freeze §4 text is the authoritative correctness definition — avoid
quoting the schema sentence in new case notes, and a future amendment should reword it. Plan §7
arithmetic checks out (18→23 = +2 S1 positives +2 controls +1 S2 negative = 10 pos / 13 neg;
items 1–2 only: 22 = 10/12), and the counts-not-bare-S1 reporting rule is respected. Item 1's §0
claim "FIRST non-TT bindable-read-back positive" is corpus-verified true (all non-TT
`mist_bindable` are currently `false`).

---

*Process note: this review file is new; its FILE_INDEX.md row was deliberately NOT added by the
reviewer (task constraint: modify no other file) — add it at reconciliation.*
