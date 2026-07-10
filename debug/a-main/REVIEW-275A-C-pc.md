# REVIEW-275A-C — Reviewer C (hostile PC / methodology lens)

**Target:** `debug/a-main/c2c3/wave-275a-plan.md` (rev 1 DRAFT)
**Reviewer stance:** independent cold reviewer, adversarial-but-fair, no shared context. I verified the
plan against the two case JSONs, `c2-freeze.md` §2/§6, `c2-depth-survey.md`, and the oracle source
(`DataIntegrityRuntime.java`, `TargetTripleRegistry.java`). I did not trust the plan's self-assessment.

## VERDICT: ACCEPT-WITH-FIXES — conditional. The enablement WORK is legitimate; the CLAIM as written is not. Three BLOCKING items must land before any flipped `mist_readback` cell is presented as a MIST detection result. If the plan ships §1's "same discriminating signature ... that the trace-only comparator arms miss" over these two cases without B1–B3, I reject.

---

## What is genuinely sound (stated up front, so the blocking findings are read as fixes, not a hit piece)
- **Pre-registration exists and the team has a refutation track record.** Expected cells are written
  before the run (§4); the wave-3a items 1 and 3 were REFUTED-not-authored and the kafkaQueueProblems
  STOP fired. This is not a group that only reports wins.
- **Minimum-change engineering.** The `ReadbackProbe` seam behind the reviewed polling/gate/verdict
  logic, with the TT/SS suites as a green regression guard, is the right shape.
- **Self-flagged tool-framing risk.** Open Q1 already asks whether `kubectl exec psql` shell-out
  compromises the "tool" framing. It does (see M3), and honest self-surfacing is credited.

These do not offset the following.

---

## BLOCKING

### B1 [BLOCKING] — Discrimination overclaim: neither new case reproduces "trace-only comparator misses, read-back catches."
§1 asserts both cases are "the SAME discriminating signature as the reviewed TT/SS results (typed
read-back FLAGs a success-shaped-clean masked/lost write **that the trace-only comparator arms miss**)."
That claim is false for one case and vacuous for the other, and the case files prove it:

- **OTel:** `oteldemo-checkout-lost-001.oracle_expectation.tracetest_presence_oracle = flag`
  (provenance: "tracetest_presence = FLAG MEASURED ran-and-caught" — the accounting consumer span is
  absent in the fault file). A trace-only comparator **already catches this fault.** MIST's read-back
  would be CONCORDANT with presence, not discriminating over it. On the paper's actual thesis axis
  (read-back beats trace-only), the OTel read-back cell contributes **nothing** — both flag.
- **TeaStore:** `naive_span_error_oracle` and `tracetest_presence_oracle` are both `not_applicable`
  **because the SUT is trace-uninstrumented as-deployed** (Kieker-only, no OTel wiring). "Read-back
  beats trace-only" here is vacuous: no trace comparator RAN. "The oracle that reads state beats the
  oracle that reads traces, on a SUT with no traces" is a tautology, not a discrimination result.

So the reviewed TT/SS contrast (trace PRESENT and CLEAN, yet the write is lost, and read-back is the
unique catcher) is **not** reproduced by either new SUT. **Fix (required):** delete the "same
discriminating signature / arms miss" framing for these two cells. Re-scope honestly:
  - OTel = a **modality-binding + concordance** datum (SQL transport binds; read-back agrees with
    presence). Report the read-back cell **beside** `tracetest_presence=flag` so the concordance is
    visible; never present it as read-back-wins-where-trace-loses.
  - TeaStore = read-back-is-the-only-runnable-oracle **under the explicit caveat "no trace
    instrumentation deployed."** If the paper wants a genuine "trace present-and-clean, read-back
    catches" datum on TeaStore, it must first instrument TeaStore (the deferred 2.5 Kieker/OTel
    branch) — otherwise the cell cannot carry the discrimination claim.

### B2 [BLOCKING] — Circularity firewall: the read-back re-runs the exact probe that DEFINED the ground truth, and the flip injects guaranteed-TP cells into the recall denominator.
The plan's §2.3 rebuts only **label** circularity ("the label comes from authored design, not MIST's
output") — and it is correct on that narrow point. But it dismisses the circularity that actually bites:
**measurement circularity.** The OTel ground-truth VALUE ("the accounting row is absent") was
established by running `SELECT count(*) FROM accounting."order" WHERE order_id=...` and observing 0 —
that psql output is the recorded ground-truth artifact (`provenance.readback_response = readback-psql.txt`).
MIST's "detection" is re-running the **same query at the same locator** and observing the **same 0**.
The FLAG is guaranteed by construction, because the capture already confirmed count==0 there. This is
not "does MIST catch the fault"; it is "does re-running the SELECT return the same number." TeaStore is
the same shape (profile-page absence is both the ground truth and MIST's probe).

Compounding it: §2.2 flips `mist_bindable false→true` and moves these cells **into the MIST recall
denominator.** Because each flipped cell is a circular guaranteed-TP, recall rises mechanically
(k/n → (k+2)/(n+2)). A PC reads that as "the tool's recall went up because they added two cases the
tool could not fail by construction." **Fix (required), any one of, but disclosed:**
  1. **State an independence standard in the paper** and hold every read-back cell to it: a read-back
     detection is non-trivial only when EITHER (a) it discriminates against a comparator that had a
     fair chance (trace present) — see B1 — OR (b) the ground truth was established **independently of
     the read-back probe** (e.g. source-level reasoning + consumer-span absence + a healed canary),
     with MIST's probe as a downstream confirmation. Declare, per cell, which holds.
  2. **Firewall these cells from headline recall.** Report them in a separate "self-concordant /
     circular-by-construction" bucket and report MIST recall **with and without** them. The headline
     number must not move on cells the oracle cannot fail.
  3. At minimum, both of the above as disclosure.
The current text ("that is NOT circular ... Disclose this explicitly in the run record") is not enough —
a buried run-record note does not neutralize a denominator that grew by guaranteed passes.

### B3 [BLOCKING, OTel-specific] — The OTel binding violates the oracle's own soundness invariant: the isolation key is read from the response.
`DataIntegrityRuntime` L40 (class contract): *"X is request-derived — never read from the response."*
This is a load-bearing soundness property — it is what stops a coincidental pre-existing row from being
credited as "your write landed." The OTel case's read-back locator is
`... WHERE order_id='<the acked orderId>'` — the key is the **server-assigned order_id read from the
200 ack.** Checkout assigns the order id; the client does not supply it. So:
  - `beforeWrite`/`freshen` cannot freshen a client field into the accounting key (the key is server-minted).
  - `beforeWriteSupplied` does not fit either — its `keyValue` comes from the scenario **setup** (e.g. a
    pre-registered user), not from the write's own response.
  - Binding OTel therefore requires a **new key-from-ack path** — precisely the thing the invariant
    forbids — and the plan's claim to "reuse the reviewed ... verdict logic verbatim" (§2) silently
    breaks on the key-derivation half of the protocol.

**Fix (required):** resolve before the OTel cell counts. Either (a) find a **request-derived**
correlatable key that the accounting row carries (session uuid / cart key / an idempotency key on
`POST /api/checkout`) and key the SQL probe on THAT, restoring the invariant; or (b) explicitly relax
the invariant for server-assigned-id creates, DISCLOSE that OTel isolation now rests on
**server-id-uniqueness** rather than MIST's freshening, and add the guard that the id was minted in THIS
run's ack (not reused). Silence is not an option — as written the OTel read-back is not just circular
(B2) but isolation-unsound.

---

## MAJOR

### M1 [MAJOR] — The denominator move is presented as pre-registered; it is not, for T9 cells. Write it as a dated new amendment.
§2.2 says moving the T9 rows into recall is "exactly the mechanism the T9 + bindable-pending-eval
conventions pre-registered." Read the freeze: the **T9 convention** (§6 row) says boundary cells are
"**excluded from MIST recall denominators and reported as their own applicability row**"; the
**bindable-pending-eval convention** governs cells that were **already** `mist_bindable=true`. These two
cases were `mist_bindable=false` (T9). No convention pre-registered what happens to a **T9** cell's
recall membership once new transport code makes it bindable — the plan is writing that rule now.
That is defensible, but it is a **new** decision, not a pre-registered one. **Fix:** add a dated §6
amendment stating the rule explicitly ("a T9 cell whose modality is later bound becomes a measured cell
at the wave that binds+runs it, reported with-and-without per B2"), with reasoning — do not launder a
post-hoc denominator change as pre-registration.

### M2 [MAJOR] — Verdict-stratum disclosure: both new absences are MIST's LOWER-confidence stratum, and must not be pooled with high-confidence reviewed results.
MIST's own gate ladder: `OBSERVED_COMPLETE_ABSENT` (high confidence) requires a Jaeger trace
(`jaeger.base.url` + traceId) present with a stable span set; otherwise absence stays `TIMEOUT_ABSENT`
(the "lower-confidence stratum reported separately"). For these cases:
  - **TeaStore is trace-uninstrumented** → `traceComplete` structurally returns false → every absence
    can only ever be `TIMEOUT_ABSENT`. MIST **cannot** reach high-confidence absence here, by design.
  - **OTel** has Jaeger, but the completeness check runs on the **entry (checkout) trace**, while the
    lost write is in a **linked accounting consumer trace**. A complete entry trace says nothing about
    whether the async downstream settled — so an OTel `OBSERVED_COMPLETE_ABSENT` here would be
    **falsely** high-confidence. The honest stratum is `TIMEOUT_ABSENT`-quality.
**Fix:** record and report the QuiescenceGate stratum for each new cell; disclose that both are
timeout-gated absence; and if the reviewed TT/SS cells were `OBSERVED_COMPLETE_ABSENT`, do NOT pool the
new lower-stratum cells with them without a stratum column. (This connects to the UX-amendment's own
disclosure: the high-confidence tier "requires a trace backend.")

### M3 [MAJOR] — Construct validity: OTel's read-back is white-box internal-DB access, and the surface-selection principle is inconsistent across the two SUTs.
- The OTel case states plainly: "the SUT has **NO order-query HTTP API** ... the accounting Postgres
  row is the SUT's ONLY durable record." So MIST's read-back reaches into the accounting service's
  **private** Postgres schema via `kubectl exec psql`. That is (a) a **white-box** capability, not the
  client-facing API read-back the TT/SS story rests on — a stronger threat model (operator DB creds +
  network path to a private store); and (b) `kubectl exec ... psql` is a **k8s admin shell-out**, i.e.
  benchmark harness plumbing, not a deployable tool feature. JDBC-over-port-forward (Open Q1 option a)
  is more principled but still white-box.
- **Inconsistency:** for TeaStore the plan binds the **user-facing HTML** `/profile` page and builds a
  "brittle-by-disclosure" scraper — even though the case notes an internal **JSON** orders API
  (`GET /rest/orders/user/{id}`) that the EXISTING JSON oracle would consume with **zero new code**. So
  the two SUTs use opposite principles (OTel: internal store because no user surface exists; TeaStore:
  user surface, declining an available internal JSON that needs no new modality). Absent a stated rule,
  the HTML choice looks driven by "new modality" novelty rather than realistic usage.
**Fix:** (1) disclose the OTel read-back as white-box internal-DB access with the elevated threat model,
and prefer JDBC over exec-shell-out so the binding is a tool capability, not a harness call; (2) state
ONE surface-selection principle ("MIST binds the most user-facing durable read-back that exists; where
none exists it binds the internal durable store and discloses the white-box assumption") and show both
bindings following it — which, notably, would let TeaStore reuse the JSON oracle unchanged and reserve
the HTML scraper only if the user-facing-surface principle demands it.

### M4 [MAJOR] — Generality: bespoke code-per-SUT supports "extensible oracle," not "general tool." Pre-commit the claim string.
§3 explicitly chooses "code-per-SUT ... NOT a config-driven registry," and §7 lists a harness class per
SUT plus new `Sql`/`HtmlField` transports and per-SUT OpenAPI/auth. Two more hand-wired SUTs (now 4
total) is fine for an eval, but it cannot support "point MIST at a new SUT and it works." **Fix:**
pre-register the exact claim the paper is allowed to make — "the oracle DECISION core
(membership/value-delta + quiescence gate) is reused verbatim; per SUT we author {OpenAPI, auth, triple,
transport-probe} at cost X minutes" — and report the recorded authoring cost for BOTH new SUTs beside
the TT/SS costs. Explicitly disavow any "general/automatic/out-of-the-box" phrasing.

### M5 [MAJOR] — The refutation branch only admits engineering failure; it must also pre-register the scientific anti-findings as REPORTED outcomes.
§4's refutation branch fires only if "the psql read-back can't be reached, or the HTML locator is too
brittle" — i.e. the **wire broke.** A FLAG is treated as automatic success. That is a plumbing test,
not a falsifiable scientific hypothesis. The scientific anti-findings from B1/B2 are foreseeable NOW and
must be pre-committed as things the run record will surface, not discovered-and-buried later:
  1. OTel presence=flag ⇒ read-back is non-discriminating on the trace axis for that case;
  2. the read-back reuses the ground-truth probe (measurement circularity);
  3. TeaStore trace columns are n/a for lack of instrumentation, so "beats trace-only" is vacuous there.
**Fix:** add these three to §4 as pre-registered REPORTED outcomes (with the recall-with/without split of
B2), so the paper cannot present a guaranteed FLAG as a clean win.

---

## MINOR

- **m1 [MINOR]** — "Reuse the verdict logic verbatim" (§2) is overstated for HTML: `probeVerdict`/
  `containsKey`/`extractItems` are JSON-only; the HTML probe re-implements present/absent extraction
  outside the reviewed path. Only the polling/gate is reused. Say so.
- **m2 [MINOR]** — Pin **paired** mode (Open Q5), not observe-mode single-leg. The reviewed centerpiece
  is the paired differential oracle; an observe-mode single-leg cell would be a weaker, non-comparable
  result. Consistency argues for paired.
- **m3 [MINOR]** — SQL semantics: a bare `count(*)` is VALUE_DELTA-shaped (count moved), not MEMBERSHIP
  (key present). Keep the keyed form (`WHERE order_id=<X>` → row present/absent) so membership meaning
  is preserved, and TEST that a psql connection/exec failure maps to an **ERROR** record, never to
  count==0 ⇒ ABSENT (the plan mentions this in §5 — keep it; it is the one place a DB-unreachable could
  masquerade as "write lost").
- **m4 [MINOR]** — Cite freeze A-M8 in the OTel run record. A-M8 already flags OTel checkout→accounting
  as "plausibly best-effort," requiring contract-grounding or a construction-bar disclosure. The case
  attaches the docs grounding; reference it explicitly so the genuineness bar is visible where the
  read-back result is reported.

---

## CONFIRMATION PASS (rev 2 + REVIEW-275A-RECONCILIATION.md) — Reviewer C disposition

**CONFIRM-ACCEPT.** All three BLOCKING and all five MAJOR findings are substantively resolved. Four
MINOR textual residuals remain; each is a disclosure-strengthening I pre-commit to accept once folded —
they do NOT gate execution.

Finding-by-finding:
- **B1 (discrimination overclaim) — RESOLVED.** Rev 2 §0/§1/§4 drop the "trace-only arms miss" framing;
  OTel is reported as presence-CONCORDANCE (beside `presence=flag`, never "beat the trace arm"),
  TeaStore as SOLE-oracle under the explicit "no tracing deployed" caveat; the MIST-only discrimination
  win stays the TT fabricated-ack case, NOT claimed for these two SUTs.
- **B2 (measurement circularity) — RESOLVED.** Independence standard (request-derived unique marker,
  LIVE re-run not artifact replay; label is by-construction from the injection, not from the read) +
  self-concordant reporting bucket + recall stated with/without. The denominator no longer grows by
  guaranteed passes without disclosure.
- **B3 (key-from-ack, isolation-unsound) — RESOLVED.** Live schema confirms `accounting.shipping`
  carries a CLIENT-SUPPLIED `street_address`; the stimulus plants a unique marker there and the SQL
  read-back keys on it — request-derived, never read from the ack, unique per run. Verified sound: the
  survey confirms accounting persists Order/OrderItem/Shipping in ONE `dbContext.SaveChanges()`, so the
  shipping-keyed absence is equivalence-preserving vs the capture's order_id key.
- **M1 (denominator move) — RESOLVED.** The false→true flip is a dated §6 amendment atomic with the
  measured run, explicitly disclaimed as NOT pre-registration.
- **M2 (verdict stratum) — RESOLVED in substance.** Paired `evaluate()` fires on the
  control-present/fault-absent differential and is gate-agnostic, so the TIMEOUT_ABSENT vs
  OBSERVED_COMPLETE_ABSENT stratum no longer gates the verdict — and the new cells now share the SAME
  paired basis as the reviewed TT/SS cells rather than being a weaker single-leg stratum. (Residual R1.)
- **M3 (construct validity / surface principle) — RESOLVED.** One consistent principle: MIST probes the
  durable system-of-record (psql for OTel, `/rest/orders` JSON for TeaStore), disclosed as an INTERNAL
  durable-store probe (stronger threat model) for BOTH SUTs. Bonus: the reconciliation independently
  found the HTML `firstname` also renders in the profile greeting → text match reads PRESENT on both
  legs → HTML binding would be UNSOUND, corroborating the push off HTML.
- **M4 (generality) — RESOLVED in substance.** Rev 2 corrects a rev-1 factual error my review inherited:
  triples load from YAML and the override seam exists, so the reusable surface (verbatim decision core +
  YAML triples) is LARGER than "code-per-SUT"; only stimulus + transport are code, with per-SUT
  authoring cost recorded. (Residual R4 = pin the claim-string to prevent paper-time drift.)
- **M5 (scientific anti-findings) — RESOLVED.** The three standing caveats (OTel concordance, TeaStore
  vacuity, circularity) are baked into the reporting framing, and the failure-mode anti-findings
  (can't-isolate / non-independent / HTML-ambiguous → cell stays not_applicable with dated disclosure)
  are pre-registered as valid outcomes.
- **Minors m1–m4 — all folded** (HTML dropped for JSON so `containsKey` reuse is verbatim; paired mode
  pinned; keyed membership + transport-failure→ERROR-not-ABSENT; A-M8 construction-bar cite).

Residuals (MINOR, textual, pre-committed accept — fold into the plan/result record, no re-review needed):
- **R1** — Commit to RECORDING the fault-leg QuiescenceGate stratum (TIMEOUT_ABSENT) in the result and
  noting the paired verdict is gate-agnostic, so a reader sees the absence is differential-based, not a
  high-confidence single-leg absence.
- **R2** — State the co-persistence justification explicitly: accounting writes Order+Shipping+OrderItem
  in ONE transaction (`SaveChanges()`), which is WHY keying `shipping.street_address` is
  equivalence-preserving vs the capture's `order.order_id`. Without it the table-deviation looks
  unjustified to a hostile reader.
- **R3** — Generalize the §4(i) independence wording: it is currently phrased OTel-specifically
  ("different column than the capture's server order_id"). State the general standard (live re-run +
  by-construction label + request-derived unique marker) so it explicitly covers TeaStore, where MIST's
  probe surface (`/rest/orders`) OVERLAPS the capture's corroboration surface (independence there rests
  on live-re-run + by-construction label, not on a different surface).
- **R4** — Pre-commit the allowed generality claim-string to the result record ("extensible oracle core
  reused verbatim + per-SUT authored {stimulus, triple, transport} at recorded cost; NOT a
  general/automatic tool") to prevent drift into an overclaim at paper time.

No new BLOCKING or MAJOR issues introduced by rev 2. The re-sequence (TeaStore primary / OTel follow),
the kafka single-toggle control-first ordering (avoids the producer-wedge), and the async-landing floor
are all sound.

---

## Bottom line for the PC (rev 1, retained for the record)
The wave is worth executing as **enablement** (bind SQL/HTML, prove the seam, record authoring cost) and
as a **concordance** datum. It is NOT, as written, an extension of the paper's discrimination thesis, and
its two headline cells are circular guaranteed-TPs at MIST's lower-confidence stratum, one of them
isolation-unsound. Land B1 (drop the "trace-only misses" framing), B2 (independence standard +
recall-with/without firewall), and B3 (fix or disclose the response-derived key), fold M1–M5 into the
plan and freeze, and this becomes an honest, defensible enablement wave. Ship it without them and a
hostile PC has a clean rejection: "recall rose because they wrote a probe that re-reads the ground truth
on cases where either a comparator already fires or none can."
