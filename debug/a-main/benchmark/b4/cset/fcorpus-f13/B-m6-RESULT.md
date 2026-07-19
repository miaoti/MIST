# B-m6 — F13 (paymentInFlightFaultMode) — GATE-REJECTED (fault EXTENSIONALLY EQUIVALENT to vanilla)
**Date:** 2026-07-18 (A3 wave). TWO attempts, evidence `legs.log`:
1. NOTPAID order (status 0): CTRL bal 50->130 **and** FLT 50->130 — identical (+80 both).
2. PAID order, PAST travel date (2026-01-05): CTRL 50->130 **and** FLT 50->130 — identical again.

## Root cause (source-verified): BOTH guards the fault bypasses are ALREADY DEAD in vanilla
- **Aliasing quirk:** `cancelFromOrder(order)` mutates the SHARED order object's status to CANCEL
  *before* `calculateRefund(order)` runs -> the NOTPAID guard (`status==0 -> refund 0.00`) can
  never fire on this path (CancelServiceImpl: the mutation is the vanilla line, not injected).
- **1900-offset bug:** `new Date(year, ...)` uses the deprecated constructor whose year param is
  YEAR-1900; with `cal.get(Calendar.YEAR)=2026` the computed departure lands in year **3926** ->
  `nowDate.after(startTime)` is always false -> the expired-ticket branch (`refund 0`) never fires.
=> `calculateRefund` returns 0.8*price on EVERY reachable cancellable order == exactly
`fullRefundIgnoringPaidState`. The injected fault has NO observable divergence.

## Disposition
**F13 DROPPED at the B-m6 gate** (never enters the corpus). This is the gate doing its job:
every injected fault must demonstrate a live control-vs-fault divergence before authoring; a
fault that bypasses guards vanilla never enforces is vacuous. Side finding (disclosed, benchmark-
construction value): vanilla TrainTicket already refunds 80% for never-paid and already-departed
cancellations via these two dead guards — i.e. the F13 fault CLASS exists natively in the SUT;
it is unreachable as a *toggleable differential*. Recorded as ecosystem context for the natural
cancel-refund defect case (TT-cancel-refund-natural-001).

**LOG-FORM NOTE (verification round):** `legs.log` holds only the SECOND attempt's pair - the
driver re-run truncates the log by design (`: >` per ONLY-selector), so attempt 1's raw lines were
overwritten. Both attempts' outcomes are documented above; the preserved pair is attempt 2
(PAID + past travel date), identical divergence-free pattern (50->130 both legs).
