# Natural-stratum faithfulness — independent source check

Authors' independent re-derivation (not the survey) of WHY the two strata are what they are,
straight from the unmodified fork source. Corroborates `prep/g3-tt-defect-survey.md`. All line
refs are the fork (`train-ticket-injection`, branch MIST-trainticket).

## The unmodified cancel path (nothing here is injected)

`ts-cancel-service/.../CancelController.java:45-51`
```java
try {
    return ok(cancelService.cancelOrder(orderId, loginId, headers));
} catch (Exception e) {
    CancelController.LOGGER.error(e.getMessage());
    return ok(new Response<>(1, "error", null));   // HTTP 200, {status:1, msg:"error"}
}
```
`ts-cancel-service/.../CancelServiceImpl.java` cancelOrder (G|H branch, lines 46-101):
```java
boolean status = drawbackMoney(money, loginId, headers);   // 64
if (status) { ...notify... }                                // 65-88
else { LOGGER.error("[Draw Back Money Failed]..."); }       // 89-91  <-- logs, does nothing
return new Response<>(1, "Success.", "test not null");      // 92     <-- ALWAYS {1,"Success."}
```
`drawbackMoney` (278-291): `return re.getBody().getStatus() == 1;` — false iff drawback replies
HTTP-200 `{status:0}`; **throws** iff drawback replies non-2xx (restTemplate throws, no catch here).

## Why the natural failure is `{1,"error"}` (a TIE), not the clean `{1,"Success."}`

The scary bug IS line 92: cancelOrder returns `{1,"Success."}` even when `drawbackMoney` returned
false (line 89-91 only logs). That clean-miss fires **only if `drawbackMoney` returns false**, i.e.
only if drawback replies HTTP-200 `{status:0}`. But the real drawBack cannot reply `{0}`:

`ts-inside-payment-service/.../InsidePaymentServiceImpl.java` drawBack (280-290):
```java
if (addMoneyRepository.findByUserId(userId) != null) { ...save...; return {1,"...Success",null}; } // 280-286
else { LOGGER.error(...); return new Response<>(0, "Draw Back Money Failed", null); }               // 287-289
```
`findByUserId` returns `List<Money>` (proven at line 305: `List<Money> addMonies = addMoneyRepository.findByUserId(userId);`).
A derived collection query returns an **empty list, never null**, so line 280 is ALWAYS true →
line 289's `{0}` return is **dead code**. Hence drawback naturally returns only `{1}` (success,
persists) or **throws** (e.g. DB down during `save`). A throw → 500 → `drawbackMoney` throws →
cancelOrder throws → CancelController catch → **HTTP-200 `{1,"error"}`**.

Therefore, on the unmodified binary:
- **Natural reachable acked-but-lost = `{1,"error"}`** (status 1, so an ack; refund lost). Both
  oracles flag it → **TIE** (MIST via acked-but-lost state; comparator via msg≠"Success.").
- **Clean `{1,"Success."}` acked-but-lost is NOT naturally reachable** → it needs the DISCLOSED
  constructed fabricated-ack (drawBack replies the exact success envelope without persisting →
  `drawbackMoney` true → line 92 `{1,"Success."}`). Comparator MISSES → **clean win**.

This is exactly the two-stratum split; the injector's "fail" mode (throw at top of drawBack)
reproduces the natural throw path faithfully — it does not bypass any response-shaping logic,
because cancelOrder/CancelController do the shaping and are untouched.

## Noted (not acted on): a second, cancel-side clean-miss mechanism

Line 92's "ignore `drawbackMoney==false`" bug is the REAL cancel-service defect, distinct from
the constructed inside-payment fabricated-ack. It would produce the SAME clean `{1,"Success."}`
miss if drawback returned `{0}` — which is only dead-on-this-binary because of the List-never-null
quirk (the developer clearly INTENDED drawback to be able to reply `{0}`, line 289). A "cleanfail"
injector mode (drawback replies HTTP-200 `{0}`) would exercise the real line-92 bug directly and
yield a clean win attributable to cancel-service rather than inside-payment. Left as a possible
strengthening if reviewers prefer a cancel-side natural-ish clean win; the current constructed
inside-payment fabricated-ack is faithful + disclosed as-is.
