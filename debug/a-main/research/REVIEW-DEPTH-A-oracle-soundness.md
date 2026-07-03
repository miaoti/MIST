# REVIEW-DEPTH-A — Oracle soundness of the supplied-isolation + value-delta extension (cold review)

- **Reviewer:** independent cold reviewer A (no prior context; oracle-soundness mandate).
- **Scope:** commits `3a94b88` (supplied isolation + value-delta read-back) and `7453142`
  (IstioRouteFaultInjector), current files
  `mist-cli/src/main/java/io/mist/cli/fault/DataIntegrityRuntime.java`,
  `TargetTripleRegistry.java`, `PairedFaultExecutor.java` (unchanged verdict machinery),
  `DataIntegrityRuntimeTest.java` (value-delta block).
- **Grounding:** TT fork sources read directly
  (`train-ticket-injection/ts-inside-payment-service/.../InsidePaymentServiceImpl.java`,
  `ts-cancel-service/.../CancelServiceImpl.java`); shipped registry
  `mist-cli/src/main/resources/My-Example/trainticket/target-triples.yaml` (no cancel triple yet;
  test triple has `readback_bound` unset).
- **Verified SUT semantics load-bearing for this review:**
  - `queryAccount(headers)` (GET `/inside_payment/account`) builds the per-user balance as
    **Σ(all Money rows for the user, types A and D both ADD) − Σ(all Payment rows for the user)**
    over the user's ENTIRE history (`addMoneyRepository.findAll()` grouped by userId;
    `paymentRepository.findByUserId`). A user appears in the list **only if they have ≥1 Money
    row** (the map is built from Money rows; a payments-only user is absent).
  - Refund direction is **UP** (drawback saves `Money{type=D}`, which the sum ADDS); a payment
    is **DOWN**. Both `pay()` and `drawBack` persist synchronously inside their request.
  - `calculateRefund` (CancelServiceImpl:200-234) returns `"0.00"` for NOTPAID orders and `"0"`
    for expired travel times; otherwise `0.8 × price`. `drawbackMoney(money, loginId, …)` sends
    the refund to the **`{loginId}` path variable**, not `order.accountId`.
  - The fault leg's cancel response is `{1,"error"}` (controller catch-all), which the runtime's
    ack predicate (`2xx && (bodyStatus==null || bodyStatus==1)`) counts as acked — pinned by
    `suppliedValueDelta_faultLeg_ackedErrorEnvelope_balanceNeverMoves_absent`.

The verdict rule is unchanged and confirmed from `PairedFaultExecutor.verdict` (lines 441-484):
FIRE ⇔ control error-free ∧ control.acked ∧ X(control) ∧ fault.acked ∧ ¬X(fault); errors and
guard failures go to NOT_EVALUABLE; ¬acked fault or X(fault) go to NO_FIRE. Under VALUE_DELTA,
X = `valueDiffers(probe(baselineBody), probe(pollBody))` per leg (`presentX`, runtime 720-727).

---

## Findings

### F1 — Direction-agnostic "movement" + single unverified baseline read: wrong-reason X in both legs. CONFIRMED (mechanism), PLAUSIBLE (frequency on the intended harness)

`valueDiffers` treats ANY change — up, down, appear, vanish — as X-present, and
`beforeWriteSupplied` takes **one** baseline read (runtime 399) with no stability check. Nothing
in the runtime or registry verifies that the scenario's earlier effects (createAccount's Money-A
row, pay()'s Payment row) have quiesced before the baseline, and for VALUE_DELTA the executor's
only automated isolation tripwire is **structurally disabled**: `baselineHasX` is hardcoded
false for VALUE_DELTA (runtime 411), so `verdict`'s "isolation violated" NOT_EVALUABLE branch
(executor 456-459) can never trigger on this triple.

Concrete failing scenarios:

1. **False negative (fault leg).** The pay step's Payment row becomes visible only after the
   cancel's baseline read (DB read lag, a replica-serving deployment, or a harness that fires
   cancel without awaiting the pay ack). Baseline captures the pre-payment balance; during the
   poll window the payment lands, the balance moves DOWN by price, `valueDiffers` = true, the
   loop **latches** `OBSERVED_PRESENT` and breaks (runtime 524-528). Verdict: NO_FIRE with the
   reason "fault run's write persisted (did the injected flag take effect?)" — the genuinely
   lost refund is silently absorbed into a plausible-looking NO_FIRE. The record's
   baselineBody/lastReadbackBody would let a human see the balance moved the WRONG direction,
   but no automated surface distinguishes it.
2. **Misattributed FIRE (claim-level false positive).** If the refund is systemically broken
   (never lands even with no fault injected) AND the control leg's baseline also catches the
   payment mid-flight, control X is satisfied by the payment's movement — the systemic guard the
   control leg exists to provide is defeated, and the pair FIREs, attributing a non-fault
   systemic loss to the injected fault. This needs a double coincidence, but it is exactly the
   class the control leg is pre-registered to exclude, and under VALUE_DELTA the guard is weaker
   than under MEMBERSHIP (where X was key-specific by construction).

Mitigating reality: in the fork, `pay()` saves the Payment row synchronously before returning
(InsidePaymentServiceImpl 120/130) and the deploy is single-node, so with a sequential,
ack-awaiting harness the mid-flight window is small — but it is nonzero, unenforced, and
repeated across N=30 benign probes plus the head-to-head runs. Isolation
(fresh-registered user per leg, no post-cancel steps touching the user's Money/Payment rows,
never the stock `fdse_microservice`/admin users whose balances churn) is **sufficient if
honored** — the probe only reads the buyer's own row, so the global list's other users are
invisible — but it is **enforced nowhere**: `beforeWriteSupplied` checks only field name ∈
isolation_key and non-empty value (runtime 390-397).

Fixes (cheap, ordered):
- (a) Pre-write stability check in `beforeWriteSupplied` for VALUE_DELTA: read the baseline
  twice ≥1 poll apart and require equal probe values; unequal → error record (never scored).
- (b) Record `probeBaseline`/`probeFinal` (the extracted values, not just the full bodies) on
  the RunRecord and emit them in the report, so direction is auditable even after the 8000-char
  body truncation (executor 741-746) — the /account body grows with every accumulated user and
  the buyer's row can fall past the truncation.
- (c) Optional but strong: a per-triple `expected_direction: increase` registry knob; control
  evidence then requires movement in the refund direction, converting wrong-reason control X
  into an error. (The refund direction is known and fixed for this surface: UP.)
- (d) Runbook rules (must be written down as binding, per the standing registry-path rider):
  harness awaits every setup ack; fresh user per leg; no concurrent activity on the supplied
  user.

### F2 — Probe-null overload: a truncated/garbage 2xx read-back or a vanishing row reads as "movement". CONFIRMED (code-level), low-to-moderate reachability on /account

`valueDiffers(nonNull, null)` = true (runtime 762-765), and `extractProbeValue` returns null for
"row absent", "value field absent", **and** "body unparseable" alike — `extractItems` catches
the parse exception and returns an empty list (runtime 826-829). So on any 2xx poll whose body
is truncated, non-JSON (proxy error page with 200), or windowed such that the buyer's row is
missing, the probe flips non-null → null and `presentX` returns TRUE:

- **Fault leg:** X-present latches `OBSERVED_PRESENT` on that single bad read, the loop breaks,
  verdict NO_FIRE — a false negative that even stops further polling from correcting it.
- **Control leg:** fake OBSERVED_PRESENT control evidence (the FIRE it enables still requires a
  genuine fault-leg absence, so this direction is less damaging, but the gate label lies).
- **Baseline variant:** the same hole applies to the baseline read, which is only
  status-checked, never parse-checked (runtime 399-405). A garbage-2xx baseline → baseline probe
  null → the buyer's untouched standing balance reads as "movement" on the first poll →
  fault-leg NO_FIRE. (Under MEMBERSHIP a garbage baseline was harmless because X never depended
  on baseline content; VALUE_DELTA makes the baseline load-bearing — a new exposure class.)

The pre-existing `readbackBound` absence-guard **cannot help and is actively wrong-way-round in
VALUE_DELTA**: it runs only on the absence exit (runtime 566-573), while truncation manifests in
VALUE_DELTA as PRESENCE; and since it counts ALL items of the global /account list (one row per
user ever), configuring `readback_bound > 0` on this triple would instead convert every genuine
fault-leg absence on a populated deployment into "bound reached → error" and break the
head-to-head. The registry cross-validation imposes no constraint either way.

Reachability for the intended surface: `queryAccount` is `findAll()` with no pagination — no
app-level windowing, and rows can never legitimately vanish (Money rows are never deleted;
inside-payment has no delete API), so the realistic triggers are proxy/gateway artifacts and
partial bodies. Low probability per read, but every poll of every leg of every run rolls the
dice, and one bad read latches.

Fixes:
- (a) In VALUE_DELTA, treat baseline-probe-nonNull → current-probe-null as a **read-back error
  record** (row vanished = surface anomaly on this SUT), never as movement. Keep null → value
  as movement (the appearing fresh buyer is a real, needed signal shape: a payments-only user
  appears in /account only when the refund creates their first Money row).
- (b) Parse-check the VALUE_DELTA baseline: 2xx + envelope-parseable (extractItems non-throwing
  and `data` an array), else error record.
- (c) Registry: reject (or loudly warn on) `readback_bound > 0` together with
  `readback_mode: value-delta`.

### F3 — Zero-delta refunds are invisible by definition; one realistic false-FIRE vector and a benign-probe FP tax. CONFIRMED (source-verified policy), harness-preventable

`calculateRefund` returns `"0.00"` (NOTPAID) or `"0"` (travel time already past), and `drawBack`
then still saves a 0-valued Money row — under the old MEMBERSHIP plan the row would be visible;
under VALUE_DELTA a zero delta is **undetectable in principle**:

- **Sterile pairs (surfaced):** both legs zero-refund → control X absent → NOT_EVALUABLE
  ("control write never appeared") — visible, not silent. Acceptable.
- **False FIRE (the one realistic vector I found):** the executor runs control first, then
  injects, then fault. If the harness books a near-now travel time, the expiry boundary can be
  crossed **between the legs**: control refund = 0.8×price (movement, X-present); fault leg's
  refund computes to "0" — the drawback "lands" as policy dictates, the balance rightly never
  moves, `{1,"Success."}` acked → FIRE, attributing a policy-conformant zero refund to the
  injected fault. No code guard can see this; the harness must book far-future travel dates and
  assert nonzero price (and PAID status) before cancelling.
- **Benign-probe FP tax:** if the depth triple participates in `benignProbe`, every zero-refund
  cancel is acked + no-movement-at-cap = a **measured false positive against MIST**
  (executor fpStats 577-593 counts it as a fire). The probe harness must exclude zero-refund
  orders, or the pre-registered FP bar eats policy noise.
- **Key identity:** the refund lands on `loginId`, so the supplied `userId` key MUST be the
  cancel URL's loginId (usually equal to accountId in TT, but the harness contract should say
  loginId explicitly).

### F4 — extractProbeValue / valueDiffers coercion: mostly sound for the intended surface; residual off-TT pitfalls. PLAUSIBLE, low

Checked against org.json behavior:
- Numeric-vs-string representation drift across polls ("100" Integer vs "100.0" Double) is
  handled: `String.valueOf` then BigDecimal compareTo → equal. Scientific notation parses in
  BigDecimal. Server-side the balance is recomputed deterministically via BigDecimal string
  arithmetic, so two reads of the same state produce identical strings on TT.
- JSON `null` balance → `String.valueOf(JSONObject.NULL)` = `"null"` → BigDecimal throws →
  string fallback: `"null"` vs `"null"` equal (no false movement); `"null"` → `"100"` differs
  (movement) — defensible.
- **Whitespace:** `new BigDecimal(" 100 ")` throws (no trim) → string fallback → `" 100 "` vs
  `"100"` = movement. Two reads of the same state can only differ this way if the SUT's
  serialization is unstable — not the case for /account; a `.trim()` before parsing is a
  one-line hardening.
- Numeric userId matching (`String.valueOf(42)` = `"42"`): consistent per SUT; a `42.0`
  serialization would read as row-absent → probe null → flows into F2's overload rather than a
  silent wrong match. TT userIds are strings; fine.
- `extractProbeValue` matches the FIRST row on `matchField` ONLY, ignoring any other
  isolation-key fields — safe for SUPPLIED (exactly one key field, enforced at parse), ambiguous
  for multi-key triples — see F5.

### F5 — Registry permits FRESH_STRINGS / STATION_PAIR + VALUE_DELTA: mixed, untested semantics. CONFIRMED gap (no current triple exercises it)

Cross-validation (registry 262-279) ties value-delta ⇔ value_probe and match_field ∈
isolation_key, but does **not** constrain the isolation strategy or key arity. Consequences of
the permitted-but-unintended combination:
- Via `beforeWrite`, `baselineHasX` is computed **unconditionally with MEMBERSHIP semantics**
  (`containsKey`, runtime 349) while X uses delta semantics — mixed per-record meaning
  (conservative in effect: a collision → NOT_EVALUABLE — but unpinned and undocumented).
  Contrast `beforeWriteSupplied`, which correctly gates the membership check on mode (411).
- Multi-field isolation keys with a single-field probe → wrong-row matching risk (F4 last
  bullet).
- No test covers the combination; it "sort of works" as appear-detection, which is exactly the
  kind of silent semantic drift the loud-registry philosophy exists to prevent.

Fix: require `isolation_strategy: supplied` (or at minimum `isolation_key.size() == 1`) when
`readback_mode: value-delta`. Note SUPPLIED + MEMBERSHIP is coherent and correctly handled —
keep it legal.

### F6 — Ack predicate and {1,"error"}: correct for TT, disclosed; bounded leak elsewhere. OK / PLAUSIBLE-low

`{1,"error"}` acked is faithful to the fork's envelope semantics (controller catch-all is unique
to ts-cancel-service, per the survey) and pinned by test. Two disclosure duties follow: the
headline FIRE's "acknowledged" ack leaks its anomaly in `msg` (the survey's detection-tie
framing already owns this — keep it in the claims); and on non-TT SUTs any 2xx body carrying an
unrelated integer `status` field flips `acked` (e.g. a status enum ≠ 1 → acked=false → control
NOT_EVALUABLE — sterilizing, not false-firing). Pre-existing behavior, not introduced by these
commits; no action beyond the disclosure.

### F7 — OBSERVED_PRESENT latch: movement is NOT monotone under VALUE_DELTA. PLAUSIBLE, bounded by F1 fixes

Under MEMBERSHIP, X (row exists) is stable once true, so latching on first sight is sound. Under
VALUE_DELTA the predicate can oscillate (value moves and returns: late payment −P then refund
+0.8P; or exact round-trips in other deployments), making the verdict sampling-dependent: the
latch converts any transient movement into permanent X (fault leg → NO_FIRE, F1's vector), while
exact cancellation between polls reads as no movement (control → NOT_EVALUABLE, surfaced). No
separate fix needed beyond F1(a)-(c); flagging so the non-monotonicity is a recorded, accepted
property.

### F8 — IstioRouteFaultInjector (7453142): convergence design is right; one residual window and one probe-choice hazard. PLAUSIBLE, low

Probed convergence (inject waits for the abort status to appear, clear for it to disappear, I/O
failure satisfies neither) is the correct contract and well-tested. Residuals:
- The probe observes **one Envoy path** (typically via the ingress). Enforcement for the
  mesh-internal cancel → inside-payment call happens at the **cancel-service sidecar**, and
  istiod propagation is per-proxy; probe-converged-at-gateway does not strictly imply
  converged-at-the-relevant-sidecar. The exposure window is typically sub-second and its failure
  direction is conservative (fault leg degrades toward a second control leg → NO_FIRE /
  NOT_EVALUABLE, never a false FIRE), but it deserves one disclosure line in the run protocol,
  or a probe routed through the same client path when feasible.
- For `clear`, ANY non-abort, non-(-1) status counts as converged — including an app/gateway
  5xx. The chosen abortStatus must be distinct from statuses the probe path can produce
  organically (the documented 404/405-on-incomplete-path choice is good; avoid 503 as the abort
  status since gateways emit 503 for unhealthy upstreams).

---

## Answers to the mandated attack items

1. **Global aggregate / control-leg movement for other reasons:** the probe reads only the
   buyer's row, so other users are invisible; same-user late-landing payments are the real
   vector (F1) — direction-agnostic valueDiffers makes them X-present on either leg (fault →
   false negative; control → guard-defeating misattribution). Per-leg-fresh-user isolation is
   sufficient if honored and **enforced nowhere** (F1); the VALUE_DELTA hardcoded
   `baselineHasX=false` removes even the membership-era tripwire.
2. **Baseline races the write:** confirmed exposure, bounded on this SUT by synchronous
   pay/drawback and a sequential harness; the machinery would **silently absorb** it as NO_FIRE
   ("write persisted") on the fault leg — only the recorded bodies would reveal it to a human.
   Fix = pre-write double-read stability check + probe-value/direction fields (F1).
3. **Coercion:** BigDecimal-first compare handles representation drift, scientific notation,
   and int/double mixing; "null"-string and both-null are safe; no case found where two reads of
   the SAME TT state compare different (whitespace would need an unstable serializer — add
   `.trim()` anyway). Residuals are off-TT and degrade to probe-null → F2's overload (F4).
4. **Bound check in VALUE_DELTA:** the guard fires only on the absence exit while truncation
   manifests as PRESENCE (vanished row → valueDiffers(baseline,null)=true → fault-leg
   OBSERVED_PRESENT → NO_FIRE false negative) — so it is useless for the risk it was built for
   and harmful if configured (global list hits the bound → every absence becomes an error).
   Not reachable through /account's own behavior (findAll, no windowing; rows never vanish);
   reachable through proxy artifacts and garbage-2xx bodies, including the **baseline** (F2).
   The code should guard it (vanish = error; parse-checked baseline); the registry should
   reject bound+value-delta (F2 fixes).
5. **VALUE_DELTA on FRESH_STRINGS:** permitted, mixed-semantics, untested — should be rejected
   at parse (F5).
6. **Other:** ack-predicate leak bounded and pre-existing (F6); OBSERVED_PRESENT latch is
   non-monotone under value-delta (F7); ThreadLocal pending + parallelism refusal carried over
   correctly (supplied hook inherits the single-thread contract; drainOrphan keeps the join
   aligned; wiring-mismatch guards are loud and reach NOT_EVALUABLE through pending.error);
   report body truncation at 8000 chars can hide the probed row on a grown /account — fixed by
   F1(b); injector residuals in F8; zero-refund policy interactions incl. one realistic
   false-FIRE vector and a benign-probe FP tax in F3.

## What is sound (verified, not just absent-of-findings)

- The verdict rule's failure directions are predominantly conservative: read-back errors,
  non-2xx decisive reads, baseline HTTP failures, wiring mismatches, and unusable keys all land
  in error → NOT_EVALUABLE, never absence; questionable movement lands in NO_FIRE (a false
  negative for MIST, not a false positive against the comparator) — the right polarity for a
  head-to-head where MIST's FIREs carry the claim.
- The {1,"error"} fault-leg shape, the fresh-buyer-appearing shape, numeric equivalence, wiring
  guards, baseline-failure, and probe/differ semantics are all pinned by tests that match the
  real SUT envelopes (verified against fork source).
- beforeWriteSupplied reuses the freshening hook's baseline/pending/record machinery verbatim;
  polling, gates, join, and executor are untouched, so the Gate-1/G2-validated machinery is not
  destabilized.
- The injector's convergence-probed contract closes the "fault leg not yet faulted" silent
  false-negative identified in its own commit message, with correct I/O-failure neutrality.

## Verdict: ACCEPT-WITH-FIXES

Blocking-before-G3-data (claims-load-bearing):
1. **F2(a)+(b):** VALUE_DELTA vanish (nonNull→null) = error record, not movement; parse-check
   the VALUE_DELTA baseline. (One latched bad read currently converts a genuine loss into
   NO_FIRE.)
2. **F1(a)+(b):** pre-write baseline stability double-read; record probeBaseline/probeFinal
   values (and thereby direction) on the RunRecord and in the report.
3. **F5 + F2(c):** registry rejects value-delta without supplied isolation (or key arity 1) and
   rejects readback_bound>0 with value-delta.

Required as written protocol (no code):
4. **F1(d)/F3:** harness runbook — fresh registered user per leg (= the cancel loginId), await
   every setup ack, PAID + far-future + nonzero-price order, zero-refund orders excluded from
   the benign probe; disclose the {1,"error"} ack-leak framing (already in the survey) and the
   injector's per-proxy propagation window (F8).

Nice-to-have: F4 trim() hardening; F1(c) expected_direction knob; traceparent ids wired through
the hand-authored harness so fault-leg absences can upgrade past TIMEOUT_ABSENT.

None of the findings invalidates the design: X-as-own-baseline-delta with the unchanged paired
verdict is the right construction for an aggregate-only observable, and every identified hole
has a bounded, local fix.
