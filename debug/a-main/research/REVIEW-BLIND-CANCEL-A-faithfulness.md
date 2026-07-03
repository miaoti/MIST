# Cold Review — Faithfulness of `blind-cancel-refund-contract.yaml` to TrainTicket source

Reviewer: independent cold reviewer (no prior context).
Contract under review: `C:/Users/miaot/Github/MIST/debug/a-main/g3-comparator-tt/blind-cancel-refund-contract.yaml`
Source of truth: `C:/Users/miaot/Github/train-ticket-injection` (Spring Boot `ts-*` services).

Verdict legend: VERIFIED = matches source exactly / faithfully; WRONG = contradicts source; UNCERTAIN = plausible but not fully pinned or slightly imprecise.

---

## 1. Response contract

| # | Claim | Verdict | Evidence |
|---|-------|---------|----------|
| 1.1 | `cancelTicket` always returns HTTP 200 (`ResponseEntity.ok`), even on failure/exception | VERIFIED | `CancelController.java:47` `return ok(...)` (success) and `:50` `return ok(new Response<>(1,"error",null))` (catch). No non-200 path. |
| 1.2 | Endpoint is `GET /api/v1/cancelservice/cancel/{orderId}/{loginId}` | VERIFIED | `CancelController.java:19` class `@RequestMapping("/api/v1/cancelservice")` + `:40` `@GetMapping("/cancel/{orderId}/{loginId}")`. |
| 1.3 | G/D-train success = `{1,"Success.","test not null"}` | VERIFIED | `CancelServiceImpl.java:92` `return new Response<>(1,"Success.","test not null")`. |
| 1.4 | other-train success = `{1,"Success.",null}` | VERIFIED | `CancelServiceImpl.java:127` `return new Response<>(1,"Success.",null)`. |
| 1.5 | Catch-all returns `{1,"error",null}` on any exception (false-success) | VERIFIED | `CancelController.java:48-51`. Envelope status=1 with msg="error". |
| 1.6 | G/D path can return `{0,"Cann't find userinfo by user id.",null}` AFTER order already cancelled+refunded | VERIFIED | `CancelServiceImpl.java:70-73`: `getAccount(...)` runs *after* `cancelFromOrder` (`:57`) and `drawbackMoney` (`:64`); on status 0 returns that envelope. State already mutated. |
| 1.7 | Assert SUCCESS as (status==1 AND msg=="Success."), not status==1 alone | VERIFIED (sound) | Justified by 1.5 (status=1 "error") and 1.6 (status=0 after mutation). A competent, source-derived caveat. |
| 1.8 | `data` carries no business meaning ("test not null"/null) | VERIFIED | Literal string at `:92`; null at `:127`. |

## 2. Postcondition 1 — order status flips to CANCEL(4)

| # | Claim | Verdict | Evidence |
|---|-------|---------|----------|
| 2.1 | Correct cancel flips order to CANCEL(4) | VERIFIED | `CancelServiceImpl.java:240` `order.setStatus(OrderStatus.CANCEL.getCode())` then PUT `/api/v1/orderservice/order` (`:246`). |
| 2.2 | Read-back `GET /api/v1/orderservice/order/{orderId}` returns `{1,"Success.",Order}` with `data.status==4` | VERIFIED | `OrderController.java:98` `@GetMapping("/order/{orderId}")`; `OrderServiceImpl.java:366` returns `{1,"Success.",order}`. |
| 2.3 | Enum values CANCEL==4, PAID==1, CHANGE==3, NOTPAID==0 | VERIFIED | `OrderStatus.java:11-35` NOTPAID(0), PAID(1), COLLECTED(2), CHANGE(3), CANCEL(4), REFUNDS(5), USED(6). |
| 2.4 | All other Order fields unchanged; fields exist (accountId, price, trainNumber, coachNumber, seatClass, seatNumber, from, to, travelDate, travelTime, contactsName, contactsDocumentNumber, documentType) | VERIFIED | `Order.java:21-59` — every listed field present; only `setStatus` is mutated on the cancel path. |
| 2.5 | `GET /api/v1/orderservice/order/{orderId}` is permitAll/public | VERIFIED | `order/config/SecurityConfig.java:80` `antMatchers("/api/v1/orderservice/order/**").permitAll()`; GET is not caught by the POST/PUT/DELETE role matchers (`:75-77`). |
| 2.6 | non-G/D read-back = `GET /api/v1/orderOtherService/orderOther/{orderId}`, same shape | VERIFIED | `OrderOtherController.java:101` `@GetMapping("/orderOther/{orderId}")`; permitAll at `other/config/SecurityConfig.java:74`. |
| 2.7 | Customer equivalent `POST /order/refresh` with body {loginId,enableStateQuery,enableTravelDateQuery,enableBoughtDateQuery} returns list with the cancelled order at status 4 | VERIFIED (minor wording) | `OrderController.java:65` `@PostMapping("/order/refresh")`; `OrderInfo.java:17,29-33` has those fields; `OrderServiceImpl.java:205` returns `ArrayList<Order>`. Entries are **flat `Order` objects** (fields `.id`/`.status` directly), not nested under an `order` key — contract's "entry whose order.id" phrasing is slightly loose but the read-back is satisfiable. |

## 3. Postcondition 2 — 80% refund credited to inside-payment balance

| # | Claim | Verdict | Evidence |
|---|-------|---------|----------|
| 3.1 | R = `DecimalFormat("0.00").format(0.80 * Double.parse(price))` when PAID & not expired | VERIFIED | `CancelServiceImpl.java:228-233`: `totalPrice*0.8`, `new DecimalFormat("0.00").format(price)`. |
| 3.2 | R = "0" if expired (now after travel datetime) | VERIFIED (shorthand) | `:216-226`: builds `startTime` from travelDate(date)+travelTime(time); `if (nowDate.after(startTime)) return "0"`. Contract's "now > travelTime" is shorthand for the composed travel datetime — essentially correct. |
| 3.3 | R = "0.00" if NOTPAID(0) | VERIFIED | `:201-202` `if (status==NOTPAID) return "0.00"`. |
| 3.4 | DecimalFormat("0.00") uses half-even rounding | VERIFIED | `:230` no explicit RoundingMode set → Java default `RoundingMode.HALF_EVEN`. |
| 3.5 | Refund drawn back to `loginId` (path param), via `drawbackMoney(R, loginId)` | VERIFIED | `:64` `drawbackMoney(money, loginId, headers)`; URL `.../drawback/{userId}/{money}` at `:285`. |
| 3.6 | `drawBack` saves a Money row `type=D` keyed by userId | VERIFIED | `InsidePaymentServiceImpl.java:247-251` builds `Money{userId,money,type=MoneyType.D}` and `addMoneyRepository.save`. `MoneyType.java:17` `D("Draw Back Money",2)`. |
| 3.7 | `queryAccount` (GET `/inside_payment/account`) sums EVERY Money row (A and D) minus Payment rows → type-D row raises balance by R | VERIFIED | `InsidePaymentServiceImpl.java:175-207`: sums `addMoney.getMoney()` for **all** rows regardless of type (`:178-186`), subtracts payments (`:194-202`), returns `Balance{userId,balance}`. Cancel adds exactly one +R row and touches no payments → delta = +R. |
| 3.8 | GET `/inside_payment/money` (queryAddMoney) returns `data:null` | VERIFIED | `InsidePaymentServiceImpl.java:320-327`: on success `return new Response<>(1,"Query Money Success",null)` — list fetched then discarded; data always null. |
| 3.9 | `/account` requires Authorization Bearer JWT role USER/ADMIN | VERIFIED | `inside_payment/config/SecurityConfig.java:71` `antMatchers("/api/v1/inside_pay_service/**").hasAnyRole("ADMIN","USER")`. |
| 3.10 | Original Payment NOT deleted; GET `/inside_payment/payment` still lists it | VERIFIED | `drawBack` (`:245-257`) never touches `paymentRepository`; `queryPayment` `:234-237` returns `findAll()`. |

## 4. Postcondition 3 — seat released (sold-count drops)

| # | Claim | Verdict | Evidence |
|---|-------|---------|----------|
| 4.1 | Sold count excludes orders with status >= CHANGE(3) (so CANCEL(4) stops counting) | VERIFIED | `OrderServiceImpl.java:274` `if (order.getStatus() >= OrderStatus.CHANGE.getCode()) continue;`. |
| 4.2 | Read-back `GET /api/v1/orderservice/order/{travelDate}/{trainNumber}` | VERIFIED | `OrderController.java:72-78` `@GetMapping("/order/{travelDate}/{trainNumber}")` → `queryAlreadySoldOrders`. permitAll (see 2.5). |
| 4.3 | seatClass→field mapping (NONE→noSeat, BUSINESS→businessSeat, FIRSTCLASS→firstClassSeat, SECONDCLASS→secondClassSeat, HARDSEAT→hardSeat, SOFTSEAT→softSeat, HARDBED→hardBed, SOFTBED→softBed) | VERIFIED (incomplete) | `OrderServiceImpl.java:277-294` matches all 8 exactly; `SeatClass.java:11-39` codes align. Contract omits the 9th mapping HIGHSOFTBED(8)→highSoftBed (`:293-294`, `SoldTicket.java:33`) — incompleteness, not an error. |
| 4.4 | ts-cancel makes NO seat-service call; seat release is implicit via sold-count only | VERIFIED | No RestTemplate call to ts-seat-service anywhere in `CancelServiceImpl.java`. Only order, inside-payment, user calls exist. |

## 5. failure_contracts

| # | Claim | Verdict | Evidence |
|---|-------|---------|----------|
| 5.1 | status not in {0,1,3} → `{0,"Order Status Cancel Not Permitted",null}`; no state change | VERIFIED | Permitted set at `CancelServiceImpl.java:52-53` & `:109-110`; reject at `:100` / `:134` with field `orderStatusCancelNotPermitted` (`:39`). Returns before any cancel/drawback. |
| 5.2 | Order not found in either service → `{0,"Order Not Found.",null}` | VERIFIED | `CancelServiceImpl.java:138` `return new Response<>(0,"Order Not Found.",null)`. |
| 5.3 | Downstream status-flip !=1 → G/D `{0,<msg>,null}`; other `{0,"Fail.Reason:"+<msg>,null}`; no refund (drawback only after successful flip) | VERIFIED | G/D `:95`; other `:130`. drawback (`:64`/`:121`) is inside the `if (changeOrderResult.getStatus()==1)` block. |
| 5.4 | Any thrown exception → `{1,"error",null}` (false-success; treat msg!="Success." as non-success) | VERIFIED | `CancelController.java:48-51`. |
| 5.5 | G/D path ONLY: post-mutation buyer-account lookup fails → `{0,"Cann't find userinfo by user id.",null}` though order already CANCEL(4) & refund row written | VERIFIED | `CancelServiceImpl.java:70-73`; `getAccount` uses `order.getAccountId()` (`:70`). The other-train branch (`:122-127`) has no getAccount and returns Success — so "G/D path only" is exact. |

## 6. notes / observability caveats

| # | Claim | Verdict | Evidence |
|---|-------|---------|----------|
| 6.1 | No cancellation email/notification: `sendEmail(...)` commented out (TODO async); NotifyInfo built but never sent | VERIFIED | `CancelServiceImpl.java:74-84` builds NotifyInfo; `:86-87` `// TODO...` and `// sendEmail(notifyInfo, headers);` commented. |
| 6.2 | Refund credited to loginId, not necessarily order.accountId; equal for self-service | VERIFIED | drawback keyed by `loginId` (`:64`); getAccount uses `order.accountId` (`:70`) — genuinely distinct ids, correctly caveated. |
| 6.3 | Refund provable only as a balance DELTA vs pre-cancel snapshot (no per-user refund endpoint; queryAddMoney null; /account returns all users + needs JWT) | VERIFIED | Follows from 3.7/3.8/3.9; queryAccount returns list for all userIds (`:206`), caller must filter userId==loginId. |
| 6.4 | Auth: cancel endpoint reached through gateway which injects caller JWT | VERIFIED | `cancel/config/SecurityConfig.java:71` `/api/v1/cancelservice/**` hasAnyRole ADMIN/USER — endpoint does require a JWT. |

---

## INDEPENDENCE verdict: PASS (genuine, source-derived, not steered)

- **No test-tool / fault / "missing compensation" language anywhere.** The contract references only TrainTicket services, endpoints, enums and Java behavior. No Toxiproxy, no injection, no oracle/comparator framing, no "acked-but-lost" wording.
- **Symmetric, complete spec.** It asserts the three natural state effects of "cancel a ticket" — (1) order→CANCEL, (2) 80% refund to balance, (3) seat frees for resale — each weighted equally, plus the response-envelope semantics. This is exactly what a competent engineer would write from the API + source, not a bug-shaped subset.
- **Not reverse-engineered toward a defect.** The refund postcondition (2) — which is where a silent drawback failure would surface — is present because refunding money is the obvious core of a cancel, not because it was chosen to trap a specific fault. Nothing narrows to the inside-payment↔DB hop or any single failure mode.
- **Not steered away either.** It does not omit or soften the refund assertion; it fully specifies balance_after == balance_before + R.
- **Honest, source-grounded caveats.** The "assert by STATE not envelope" guidance and the observability limits (queryAddMoney returns null, JWT-gated /account, no per-user refund record) are all directly derived from reading the controllers/services — the hallmark of an engineer who actually read the code, not of tool-awareness.

## BOTTOM LINE

The contract is **factually sound as frozen.** Every load-bearing claim — HTTP-200-always, the two success envelopes, the false-success catch, the post-mutation "Cann't find userinfo" inconsistency, CANCEL(4)/status-enum values, the 80%/expired/NOTPAID refund formula, type-D drawback + queryAccount summation, queryAddMoney returning null, JWT gating, the `>= CHANGE(3)` sold-count exclusion, the full seat-class field map, and all five failure_contracts — is VERIFIED against the source with file+line evidence. **No WRONG claims found.** Only two immaterial nits: (a) the seat table omits the 9th class HIGHSOFTBED→highSoftBed (incomplete, not incorrect), and (b) the refresh read-back entries are flat `Order` objects so the "entry whose order.id" phrasing is slightly loose. Neither affects any assertion's correctness. Independence: PASS — this reads as a competent engineer's black-box contract derived from the API and source, not reverse-engineered to hit or dodge any particular defect.
