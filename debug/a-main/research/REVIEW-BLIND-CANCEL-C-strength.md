# Cold review — strength/fairness of `blind-cancel-refund-contract.yaml`

Reviewer: independent cold reviewer C (no prior context). Scope: is this frozen
contract a STRONG, FAIR baseline (not a strawman) for an oracle head-to-head, and
does it conform to the frozen G2 blind-assertion schema/style?

Method: every load-bearing claim was checked against the TrainTicket source at
`C:/Users/miaot/Github/train-ticket-injection` (CancelController, CancelServiceImpl,
InsidePaymentServiceImpl, OrderServiceImpl.queryAlreadySoldOrders, OrderController,
OrderStatus, SoldTicket, order + inside-payment SecurityConfig).

## BOTTOM LINE: STRONG (accept as a fair, non-strawman baseline)

This is a maximally strong oracle for "cancel an order," not a weak one. It asserts
the response envelope AND all three meaningfully-observable state effects, and it
verifies the exact fact the target defect destroys (the refund) as a hard, black-box
balance delta. The only findings are minor and mostly about schema key-naming.

---

## 1. STRENGTH / completeness — everything a diligent oracle would assert IS asserted

Verified the contract asserts the full observable footprint of the endpoint:

- **Response, gated correctly.** HTTP 200 in all outcomes (controller wraps every
  path in `ok(...)`); success gated on `status==1 AND msg=="Success."`, NOT status
  alone. This is essential and correct: the catch-all returns `{1,"error",null}`
  (CancelController:50) and the G/D path can return `{0,"Cann't find userinfo..."}`
  AFTER a real cancel+refund (CancelServiceImpl:70-73). Both quirks verified.
- **(1) Order status -> CANCEL(4)**, with every other Order field asserted UNCHANGED.
  Verified real: `cancelFromOrder` sets status=CANCEL and PUTs the whole order back,
  so field-preservation is a genuine contract, not over-reach.
- **(2) Refund = 80% of price credited to the buyer's inside-payment balance**, proved
  as `balance_after == balance_before + R` against a pre-cancel snapshot. Mechanism
  verified: `drawBack` saves a type-D Money row keyed by loginId; `queryAccount`
  (GET /inside_payment/account) sums EVERY Money row (types A and D) minus Payments,
  so the type-D row raises balance by exactly R. This is the crux — it is precisely
  the fact the cancel->refund missing-compensation defect loses, and the contract
  makes it a HARD assertion. A strong engineer would write exactly this.
- **(3) Seat released** via the sold-ticket count (queryAlreadySoldOrders skips
  status>=CHANGE(3), verified at OrderServiceImpl:274), delta -1 for the seatClass.

Correctly REQUIRES a non-expired PAID order (postcondition intro). This matters: for
an expired order R="0", making the balance delta 0 and INDISTINGUISHABLE from a lost
refund — the contract avoids that trap by scoping to non-expired. Good design.

## 2. OVER-REACH — none material; correct exclusions

- `data` payload ("test not null"/null) correctly declared meaningless and excluded.
- Email/notification correctly NOT asserted (sendEmail is commented out; NotifyInfo
  built but never sent — verified).
- Original Payment row correctly NOT asserted removed (refund is a compensating
  type-D row; queryPayment still lists it — verified).
- All msg strings ("Success.", "Order Status Cancel Not Permitted", "Order Not
  Found.", "Cann't find userinfo by user id.", "Fail.Reason:"+msg) match source.
- No cascade to assurance/consign/voucher is asserted — correct, cancel makes no such
  calls (only order, inside-payment, user services). Asserting them would be unfair.

## 3. SCHEMA / STYLE conformance — minor key-name divergence from the frozen G2 set

The frozen G2 file (`g2-comparator/blind-assertions-trainticket.yaml`) uses per-entry
keys `success_response` and `failure_contract` (singular string). This contract uses
`response_contract` and `failure_contracts` (a list), plus an extra `sources_consulted`
block. NOTE: the review brief's own stated key list ("response_contract ...
failure_contracts") matches THIS file, not the literal G2 file — so this looks like an
intended schema evolution, and the list form is genuinely better for 5 distinct failure
modes. Non-blocking, but the two frozen files are now key-inconsistent; recommend either
aligning names or recording the schema-version bump so a consumer of both isn't tripped.
`service/endpoint/state_postcondition/notes` conform exactly. Rigor level meets or
exceeds the G2 entries.

## 4. SELF-CONSISTENCY — notes clarify, never walk back

The `notes` bound observability (refund provable only as a delta, not absolute; seat
release is implicit via sold-count; refund lands on loginId) but do NOT retract any
asserted postcondition. Clean separation of "what to assert" vs "observability
caveats." No contradiction found.

## 5. FAIRNESS BOTH WAYS — the one thing to watch

This contract is strong enough to be fair; the risk is the OPPOSITE of a strawman.
Because postcondition (2) asserts the balance delta, an oracle that FAITHFULLY executes
this contract WOULD catch the lost-refund defect. Therefore, in the head-to-head, any
comparator "miss" must come from the comparator's inability to MECHANICALLY execute a
JWT-authenticated pre/post balance-delta snapshot (USER/ADMIN role required on
/inside_pay_service/**, verified) — NOT from the contract under-specifying. The paper
must frame the miss as an executability/derivation gap, and must not claim "the baseline
oracle as specified misses it" (the spec catches it). State this explicitly, or the
comparison could be attacked as unfair to the comparator.

## Minor / optional
- CHANGE(3)-status cancels are in the cancellable set and also refund 80%, but
  postcondition (2) enumerates only PAID/NOTPAID amounts. Scoping to the PAID happy
  path is fine; a one-line CHANGE note would close the gap.
- Could add an explicit "cancel does NOT cascade to assurance/consign/voucher/food"
  note to make the completeness argument airtight.

## Claims spot-checked TRUE against source
Controller 200-always + catch {1,"error"}; success shapes {1,"Success.","test not
null"} (G/D) / {1,"Success.",null} (other); refund calc 0.8*price / "0" expired /
"0.00" notpaid; drawBack type-D keyed by loginId; queryAccount sums A+D minus Payments;
/money returns data:null; Payment not deleted; status flip via full-order PUT; cancellable
set {0,1,3} and OrderStatus codes; sold-count skips status>=3; order/** permitAll,
inside_pay_service/** USER|ADMIN; failure_contract #5 (G/D getAccount-fail returns
status 0 while order already CANCEL + refund already written) is a real inconsistency,
correctly documented.
