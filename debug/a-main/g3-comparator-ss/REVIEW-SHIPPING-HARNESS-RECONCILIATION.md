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

## P2 REVIEW — fairness/methodology (reviewer B): ACCEPT-WITH-CHANGES (no BLOCKING; framing-only)
Verified: no shipping run-result exists yet (amendment is genuinely PRE-RUN); freeze→amend is git-tracked
(NOT_CHECKABLE at 1adf483 → bound at 41ff9ac); the clause reads ONLY liveness (catches the outage, blind to the
loss — not over-strengthened); /health stays HTTP 200 so the natural FAIL is a real in-body detection (not
reclassified infra-failure); the assertion is MAXIMAL (/health has exactly 2 entries, both asserted OK — not
cherry-picked). Crucially P2 STRENGTHENS the baseline (MIST's win gets harder), the opposite of strawmanning.
The steelman "engineered baseline" attack survives ONLY if framing slips — and its one grain of truth (a
diligent SRE would add queue-depth/publisher-confirms) is SELF-DEFEATING: it concedes that catching this class
requires out-of-class broker/queue-state observation = MIST's contribution. FRAMING RULES (write-up; no code):
- **[B-MAJOR-1] Class-scope the win.** Say "the strongest fair *single-endpoint response+liveness
  contract-checking* class (Pact/Dredd/synthetic-monitoring shape) misses it"; NEVER "no diligent engineer could
  catch this." Bind to the standing oracle-class-scope rule. The constructed stratum is an existence proof that
  this class's boundary is not closable by IN-class strengthening (a health check); crossing it needs an
  out-of-class observable (broker/queue state / cross-service differential) = MIST.
- **[B-MAJOR-2] Report BOTH comparator forms side by side** — (i) as-frozen HTTP_STATUS-only (git 1adf483; misses
  BOTH strata, the swallow returns 201 regardless) and (ii) P2-strengthened (catches the natural outage, still
  misses the constructed loss). **RUN IMPLICATION: evaluate the comparator with BOTH contracts and record both**
  — proves no inconvenient frozen result was suppressed and strengthening did not manufacture the win.
- **[B-MAJOR-3] Describe the PROTOCOL, not "independence".** The anti-gaming guarantee is freeze-before-reveal:
  primary-sources-only, no MIST-internals access, git-frozen before reveal, P2 written into the frozen notes —
  so the spec provably was not reverse-engineered to fit MIST. Do NOT imply an organizationally-separate human.
- **[B-MINOR-1] Defend + disclose the constructed fault:** max-length/reject-publish is a real RabbitMQ overflow
  hazard; disclose it was deliberately chosen to exhibit a GREEN-liveness loss; cite requireRejectWillBite()
  (consumers==0 ∧ depth≥max-length) as evidence the loss is real, not a drained mirage.
- **[B-MINOR-2] Natural stratum stays "diagnosis gap"** (comparator catches the outage, MIST localizes the lost
  enqueue — not a MIST win, not a tie); the printCell A-m7 label already enforces this in code.
- **[B-MINOR-3] State the maximality** (/health = 2 entries, both asserted OK) to pre-empt "convenient subset".

## P2 REVIEW — correctness (A) + outcome (C): both ACCEPT-WITH-CHANGES + LIVE SEVER VERIFICATION
Fix wave folded in; full mist-cli suite 183 green. A + C both flagged the transport-classification gap (converged).

### Code fixes applied
| Finding | Sev | Fix |
|---|---|---|
| A-MAJOR empty-constraints false-PASS (`fields:","` → zero-length split → vacuous PASS) | MAJOR | loader validates the PARSED constraint count ≥1; matcher `if(constraints.isEmpty())return false` (belt+braces) |
| A-MINOR `" =x"` bypasses the `=` check (indexOf on the raw string) | MINOR | trim before the `=` test |
| A-MINOR collection_key silently ignored on non-literal checks | MINOR | reject unless STATE_GET `contains-literal-fields` |
| **C-MAJOR-1** harness `printCell` scored CAUGHT off raw `flagged` — a transport-only fault FAIL = false CAUGHT | MAJOR | mirror `ComparatorRunner.onlyTransportFailures` → `comparator-infra-failure`, not CAUGHT |
| A-NIT / C-MINOR-6 FAIL detail said "submitted state ABSENT" for a literal check | cosmetic | message now reads "literal fields ABSENT" |
| C-MINOR-5 no orchestration test of the liveness CAUGHT path | MINOR | +2 harness tests (CAUGHT with clean control; transport reclassified) |
| **NEW (live)** Istio DENY alone does NOT flip /health (cached AMQP connection persists) | run-blocking | injector force-closes the cached conn (`rabbitmqctl close_all_connections`) after apply — verified live: /health err in ~1s, no bounce; +`IstioAmqpSeverInjectorTest` (6) |

Deferred (safe-direction / operational, disclosed):
- **C-MAJOR-2** (retry breaks early on one OK → false MISS on a flapping sever): the DENY+close holds /health STABLY err (DENY blocks reconnect); direction is a false MISS (never a false MIST win). Runbook: lower the retry cap + confirm no flap.
- **C-MINOR-6 case** (injector `equalsIgnoreCase("OK")` vs oracle exact `status=OK`): inert (live value literally "OK"); self-reveals via the "control also flagged — systemic" signal.
- A-NIT terminal-poll transport classification: /health is HTTP-200-stable, low risk.

### LIVE SEVER VERIFICATION (2 self-cleaning runs, cluster restored, qm→0)
- **Run 1 (DENY only):** /health did NOT flip in 40s (cached connection); the bounce fallback raced (/health empty, POST 000) → mechanism insufficient.
- **Run 2 (DENY + close_all_connections, qm fully drained first):** qm down ~1s; /health flipped to err ~1s with NO bounce; fault POST=201 + queue depth stable (message LOST); control POST landed (depth moved, but the mgmt count lags). → **MECHANISM CONFIRMED** and folded into the injector.
- **LEARNED:** (a) the natural sever = DENY **+ connection-close** (now in `IstioAmqpSeverInjector.inject`); (b) the RabbitMQ mgmt `messages` count **LAGS ~5s** → fine for MIST (poll timeout 20s ≫ lag; the oracle's baseline-stability double-read guards a stale baseline), but the runbook adds a settle between legs.

### RUNBOOK (natural stratum, pre-registered)
- queue-master scaled to 0 AND wait for 0 pods before any baseline (else transient draining reads as a false loss).
- Injector: DENY-apply → `close_all_connections` → await /health err (converge-gated; throws rather than run a degenerate leg).
- Comparator retry cap set LOW for the run so the fault-leg liveness FAIL resolves fast (no ~20s×N budget); the DENY holds /health stably err.
- **Live-confirm at run time (C's A/B/C/D):** every fault-leg /health read is HTTP 200 + stably `shipping-rabbitmq=err`; control recovers green before the next leg; **CONSTRUCTED: /health STAYS GREEN under reject-publish (the health probe checks connection liveness, not a test-publish) — the make-or-break for the clean-win cell**; /health status is literally "OK".

### FRAMING (B + C-MINOR-4): natural = TIE at binary granularity; MIST's edge = LOCALIZATION
The natural stratum is NOT a comparator miss and NOT a MIST *detection* win — both flag (comparator on the coarse /health liveness clause, MIST on the specific queue-depth delta). Present it as "both detect an anomaly; the comparator sees a service-wide outage, MIST localizes the specific lost write," alongside dual-form reporting (as-frozen `blind-shipping-contract-asfrozen.yaml` misses both strata; the P2-amended contract catches the natural outage, still misses the constructed loss).
