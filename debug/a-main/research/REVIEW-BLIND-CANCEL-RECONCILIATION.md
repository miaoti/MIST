# Reconciliation — blind cancel→refund contract review (3 cold reviewers)

Reconciles REVIEW-BLIND-CANCEL-{A-faithfulness, B-bindability, C-strength}.md over the
frozen contract `debug/a-main/g3-comparator-tt/blind-cancel-refund-contract.yaml`
(frozen 38e7aa6). All three reviewers ran independently, no shared context.

## Consensus on the contract itself
- **A (faithfulness): PASS.** Every load-bearing claim VERIFIED against the TT source
  with file+line evidence; no WRONG claims. Response envelope (HTTP-200-always, the
  `{1,"error"}` false-success catch, the post-mutation `{0,"Cann't find userinfo"}`
  inconsistency), postcondition 1 (order→CANCEL(4)), postcondition 2 (R=0.80·price,
  type-D drawback, queryAccount sums it, **/money returns data:null**, /account needs a
  JWT), postcondition 3 (sold-count skips status≥CHANGE(3)), and all 5 failure
  contracts — all faithful. **INDEPENDENCE: PASS** (no tool/fault/"missing-compensation"
  language; a symmetric competent-engineer spec, not reverse-engineered to hit/dodge a
  defect). Two immaterial nits (seat map omits HIGHSOFTBED; "refresh entry" phrasing).
- **C (strength): STRONG** — a fair, non-strawman baseline. Asserts the response AND all
  three observable state effects; the refund is a HARD `balance_after==balance_before+R`
  delta; correctly requires a non-expired order so R>0 (avoids the expired-order delta-0
  trap); no material over-reach; self-consistent. Key note: *the contract is strong
  enough that a faithful executor WOULD catch the defect via postcondition 2 — so any
  comparator "miss" is an EXECUTABILITY/derivation gap, not the spec under-asserting.*
- **Verdict: the frozen contract is VALIDATED** — factually sound, independently
  authored, and a strong baseline. No changes to the contract (it stays frozen as-is).

## The head-to-head result (from B, bindability)
The comparator's closed primitive set (HTTP_STATUS, ENVELOPE_STATUS, ENVELOPE_DATA,
MSG_CONTAINS, STATE_GET, NOT_CHECKABLE) provably has **no arithmetic/delta, no
pre/post snapshot, no JWT/auth on the read-back**, and STATE_GET matches only
string-equality of values taken from the **submitted request body**.
- **EXECUTABLE:** the whole response envelope — HTTP 200, the mandated success gate
  `status==1 AND msg=="Success."`, the fixed failure envelopes, and notably the FC4
  `{1,"error"}` false-success trap (the msg gate correctly rejects it).
- **NOT_CHECKABLE:** ALL three state postconditions + the state-invariance riders. The
  refund needs a numeric delta vs a JWT-gated pre-cancel snapshot over a server-computed
  R (four independent blockers). The bodyless cancel GET has no submitted body, so
  `${field:…}` / presence values have nothing to resolve (the runner is POST-only) —
  the state reads can't even calibrate on the control leg. `data.status==4` is a
  post-state constant (not submitted); the seat check is another delta.
- **Bottom line:** on the acked-but-lost refund, the comparator adjudicates the (lying)
  `{1,"Success."}` envelope and PASSES → **misses the lost write**. The gap is entirely
  on the oracle's expressive side, exactly as the thesis predicts.

## THE LINCHPIN (source-verified here + corroborated by A/B) — and its consequence
`InsidePaymentServiceImpl.queryAddMoney` (GET /money) returns
`new Response<>(1,"Query Money Success", null)` — **`data` is hardcoded null even on
success.** So the discrete type-D drawback record is **observable via no working
endpoint**. The refund shows up ONLY in the `queryAccount` (GET /account) aggregate:
`balance = Σ(Money rows type A+D) − Σ(Payments)`. For a fresh buyer (external pay saves
a Payment P; refund saves Money type-D of R): **balance = R−P (control) vs −P (fault),
a delta of exactly R** — never a clean absolute value or a discrete record.

**Consequence for MIST's B2 (design change, not a contract issue):**
- My original read-back — membership on GET /money for the drawback record — is
  **INVALIDATED** (/money exposes nothing). Recorded so it is not used.
- The refund is only observable via /account as a **value delta**. MIST's B2 as shipped
  does **membership-differential** (a freshened key present in control, absent in
  fault). To catch this it needs a **value-differential** read-back: the buyer's
  /account balance is lower under fault than under control by R (acked ∧ fault<control
  → FIRE). This is a principled generalization of MIST's differential (membership is the
  special case), and it is precisely the cross-execution comparison a stateless
  assertion oracle structurally cannot do — so it does not narrow the head-to-head gap;
  it widens it. (An absolute entity-match on the computed balance is technically
  possible but FRAGILE — BigDecimal string-format + a no-stray-rows assumption — so
  value-differential is the robust choice.)
- The balance trajectory (fresh user, external pay, no stray Money/Payment rows so the
  delta is exactly R) must be **live-verified** on the deployed TT before the run.

## Dispositions
| # | Finding | Source | Disposition |
|---|---------|--------|-------------|
| 1 | Contract faithful + independent + strong | A,C | ACCEPT — contract stays frozen |
| 2 | Comparator misses all state postconditions (NOT_CHECKABLE) | B | ACCEPT — this IS the head-to-head result (report it) |
| 3 | /money broken → refund only via /account delta | me,A | DESIGN: MIST read-back → /account value-differential; original /money-membership design retired |
| 4 | Schema divergence: this file uses `response_contract`/`failure_contracts`(list) vs G2 set's `success_response`/`failure_contract`(str) | C | DISCLOSE — intended schema evolution; ensure the comparator loader/binding round treats the two frozen files by their own schema (do not silently mis-consume) |
| 5 | MIST needs value-differential + JWT read-back + pre-established isolation for this one defect | me | STRATEGIC FLAG (below) — surface before building |

## Strategic flag (why this is a decision point, not just engineering)
Catching cancel→refund now requires stacking three MIST affordances — pre-established
isolation (bodyless GET), a value-differential read-back (broken /money), and a
JWT-authenticated read-back (/account). Each is individually defensible and principled,
but a skeptical PC reviewer could read the stack as "MIST was extended until it caught
the chosen bug." The mitigation is framing + breadth, not weakening: the **breadth
binding round (Rider 2, ~79 endpoints)** already yields many CLEAN-membership wins where
MIST's *shipped* B2 catches acked-but-lost writes the response-assertion comparator
misses — so the paper's quantitative claim rests on volume, and cancel→refund is the
qualitative DEPTH centerpiece (a natural saga compensation loss). Whether to (a) proceed
with the value-differential extension for cancel→refund as the single depth site, or (b)
ALSO stand up a clean-membership depth defect (MIST's shipped B2, no extension) to make
the head-to-head maximally un-contestable, is a research-framing call worth the user's
input — it changes both the engineering scope and the reviewer-defense.

## Bottom line
Contract VALIDATED + frozen. Comparator CONFIRMED to miss the lost refund (expressivity
gap, per a strong blind contract — the ideal head-to-head shape). MIST catches it via an
/account value-differential (a principled B2 generalization, live-verification pending).
The thesis holds; the open decision is scope/framing (strategic flag), surfaced next.
