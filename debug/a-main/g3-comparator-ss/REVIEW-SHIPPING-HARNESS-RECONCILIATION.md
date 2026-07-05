# Shipping head-to-head harness — 3-cold-review reconciliation + blind-author finding

Code under review: the shipping-enqueue head-to-head harness (commit f57dc7d) — `ShippingEnqueueHeadToHead`,
`ShippingReadbackHttp`, `RabbitPolicyInjector`, `IstioAmqpSeverInjector`, the `DataIntegrityRuntime` Http-seam
widening, and `ShippingEnqueueHeadToHeadTest`. Three independent cold reviewers (no shared context), one lens
each: A = oracle-reuse soundness, B = read-back wiring + seam + test-fake, C = injectors + fault + fairness.
In parallel, an independent blind author produced the frozen comparator contract.

## Verdicts
- **A (oracle-reuse): ACCEPT-WITH-CHANGES.** No false-FIRE, no broken oracle reuse; correlator join + claim-
  eligibility genuine; cross-leg +1 handled; omitting the TT pre-funded gate is correct for queue depth.
- **B (wiring): ACCEPT-WITH-CHANGES.** `%2f` preserved end-to-end (raw HttpURLConnection is the correct
  choice); a bad broker read routes to NOT_EVALUABLE, never FIRE; seam widening is visibility-only;
  `extractProbeValue` binds on the real 43-field body; comparator reads are NOT routed through the override.
- **C (injectors): ACCEPT-WITH-CHANGES.** Both strata honestly framed (no rigging); every failure mode is
  safe-direction (NO_FIRE / loud error / hygiene), so nothing is strictly BLOCKING; three MAJORs to fix first.

Net: **the harness is sound and faithful; every failure mode degrades safely (no false positive).** All fixes
folded in this wave (full mist-cli suite 163 green).

## Convergent + MAJOR findings → disposition (all fixed)
| # | Finding | Reviewers | Fix |
|---|---|---|---|
| A-M1 / C-MAJOR-1 | `fault.inject()` outside the try → a convergence-probe throw leaks the DURABLE Istio/broker fault (cluster left partitioned) | A + C (converged) | moved `inject()` INSIDE the try/finally; `clear()` is idempotent |
| C-MAJOR-2 | `IstioAmqpSeverInjector.runProcess` drains stdout BEFORE `waitFor` → a wedged kubectl hangs the run (reverses the reviewed sibling's protection) | C | reordered to waitFor-first, drain-after (mirrors `SutFlagFaultInjector.runProcess`). Kept self-contained rather than widen 3 more fault-package members to public just to dedup (C-M-4 accepted) |
| C-MAJOR-3 | `awaitPolicy` confirms only the policy NAME, not that a publish will be REJECTED — reject-publish bites only at depth≥max-length with no drainer | C | added `requireRejectWillBite()`: asserts `consumers==0` AND depth≥max-length in `inject()`, else fails LOUD (a forgotten queue-master scale-down no longer silently degrades the clean win to NO_FIRE) |
| B-M1 | `ShippingReadbackHttp` had zero direct test coverage (the `%2f` footgun/auth/IOException→0 mapping unguarded) | B | added `ShippingReadbackHttpTest` (4 tests) against a JDK HttpServer: raw-path `%2f`, basic-auth header, 401/500 passthrough, refused→status 0 |

## MINORs → disposition
- **A-m5** evidence-only `depthOf` regex breaks on the real nested body → replaced with a real JSON parse.
- **A-m4 / C read-lag** default 10s timeout ≈2× the ~5s mgmt stats lag → `run()` floors the oracle timeout to 20s unless the launcher set one.
- **B-m4** `installHttpOverride` outside its try → moved inside (cleared in finally on any throw).
- **B-m3 / C-M-2** javadocs: the override is valid only for a supplied triple whose sole `getSut` is the readback path; `readHealth` non-2xx→null assumes health-err is served as HTTP 200 (true for this image).
- **B-m1** test fake coupled to the oracle's 2-baseline-read count → documented as self-guarding (a drift breaks control PRESENT → the FIRE assertion fails, never passes wrongly) + a `calls>=5` sanity assert.
- **A-m7** `printCell` now labels a natural-stratum comparator flag as response/liveness-level, NOT write-localized.
- **C-M-5/M-6** `clear()` surfaces a genuine DELETE failure immediately; `awaitPolicy` timeout reports the last observed HTTP status.
- **A-m6** (verify live POST body has no numeric status≠1) — RESOLVED by the blind author's live check: POST /shipping returns a bare `{id,name}` (no envelope, no status field) → bodyStatus=null → acked. ✓
- **C-M-1** the "bounce shipping" sever fallback is documented but not implemented (the convergence gate correctly refuses a degenerate leg) — accepted; revisit if the live sever needs it.

## Standing framing rules (disclose in the write-up)
1. **[A-m2] The constructed win is an OBSERVABILITY / durable-sink-binding win, NOT an arithmetic-magnitude
   value-delta win.** value-delta is the mechanically correct MODE here (membership on a queue-list is always
   TRUE — the row always exists — so only a count-delta detects the enqueue), but the signal is a unit depth
   increment, not a TT-style arithmetic refund delta. Frame it as durable-sink binding; do NOT re-claim
   value-delta arithmetic power.
2. **[A-m3 / B-m2 / C read-lag] Monotonic depth (queue-master→0) is soundness-relevant, machine-checked only
   at inject() (C-MAJOR-3 fix), and a runbook rule elsewhere** — the analog of the disclosed value-delta
   isolation rider. Timeout floor ≫ the ~5s stats interval is a run precondition.
3. **[C-M-3] The natural stratum's "self-documented swallow log fired" narrative needs LIVE confirmation.**
   With publisher-confirms off, a `basicPublish` can be silently dropped WITHOUT throwing, so the "Accepting
   anyway…" catch may not fire on the sever. MIST still FIREs on the depth signal (unaffected); only the
   self-documented-log narrative is contingent — verify at run time, don't assert it on paper.

## BLIND-AUTHOR FINDING (pivotal — reshapes the comparator, opens a design fork)
The independent blind author (freeze-before-reveal, primary sources + live) found the response-assertion
primitive set can SOUNDLY express **only `HTTP_STATUS 201`** for POST /shipping. Everything else is
NOT_CHECKABLE with a genuine reason: the bare-object id/name echo (no primitive fits — ENVELOPE_* would
FALSE-FAIL a correct success, ENVELOPE_DATA:null is vacuous, no GET returns the resource), the enqueue effect
(swallowed, no HTTP observable), and **service/broker liveness via /health (NOT EXPRESSIBLE** — STATE_GET has
no literal matching and only keys on the submitted {id,name}; `extractItems` does not unwrap `{health:[…]}`).

Consequence: with an HTTP_STATUS-only comparator, POST always returns 201 (the swallow), so the comparator
MISSES BOTH legs in BOTH strata → both cells become clean MIST wins, but the comparator looks weak (the
"just add a health check" rebuttal). The author proposed two minimal primitives to give the comparator its
strongest FAIR form: **P1 RESPONSE_BODY_CONTAINS** (match submitted fields against the write response body →
binds the echo) and **P2** a literal-match STATE_GET expect over a named collection entry + a configurable
collection key (→ binds /health broker+app liveness).

**DESIGN FORK (needs a decision before the live run):**
- **Option 1 — implement P2 (+ maybe P1), then run.** Gives the maximally-fair comparator and RESTORES the
  two-stratum story: natural = /health err → P2 CATCHES the outage (diagnosis gap, MIST localizes); constructed
  = /health GREEN → P2 PASSES both legs → the clean MIST win becomes ROBUST to the "add a health check"
  rebuttal (the killer result, demonstrated not argued). Cost: new ContractEvaluator primitive(s) = tool code
  needing its own ≥3-review.
- **Option 2 — run the HTTP_STATUS-only frozen contract as-is + disclose analytically.** Both strata are clean
  wins; disclose that the response-assertion primitive set structurally cannot express liveness (per the
  independent author), and argue that even the proposed P2 would, by construction, PASS both legs of the
  constructed stratum (green health) — so MIST's niche is unreachable by response+liveness contracts. Cheaper;
  the constructed-stratum robustness is argued, not shown; mildly strawman-attackable.

Recommendation: **Option 1** — it is the anti-strawman-correct choice and makes the constructed-stratum win
demonstrated + robust. Deferred to the user (contribution-framing + scope). Contract + notes:
debug/a-main/g3-comparator-ss/blind-shipping-contract{,.-notes}.md.

## DECISION (user, 2026-07-04): OPTION 1 — P2 IMPLEMENTED
P2 = STATE_GET expect **`contains-literal-fields`**: membership by literal `name=value` constraints over a
collection, with a configurable **`collection_key`** to unwrap `{health:[..]}` (which the default
array/{data}/{_embedded} shapes do not cover). Implemented ENTIRELY in the comparator package —
`AssertionBindings` (allowed key + Check.collectionKey + parse validation: each field must be `name=value`) and
`ContractEvaluator` (presenceSatisfied dispatch + containsLiteralFields + literalItems) — the reviewed oracle
`DataIntegrityRuntime` is untouched. Tests: `ContractEvaluatorLiteralFieldsTest` (6: matcher PASS/FAIL/absent/
bare-array + full STATE_GET flow PASS-healthy / FAIL-broker-err / transport-fail) + `AssertionBindingsTest` (+3:
parse + a load pin of the amended contract), full mist-cli suite **171 green**. The frozen contract's `/health`
clause is amended (DISCLOSED, pre-run, per the TT A2/A3 precedent) from NOT_CHECKABLE to TWO bound liveness
checks (service=shipping-rabbitmq,status=OK + service=shipping,status=OK). Behaviour: natural sever → broker
entry flips to `err` → first check FAILs → comparator CATCHES the outage (diagnosis gap; MIST additionally
localizes the lost enqueue); constructed reject-publish → `/health` green → both checks PASS → comparator
MISSES → the clean MIST win, now robust to "just add a health check". **NEXT: ≥3-cold-review P2 (new comparator
primitive + the amendment) before any claim; then the live run (Istio sever manifest + ShippingStimulus +
both strata + result ≥3-review).**
