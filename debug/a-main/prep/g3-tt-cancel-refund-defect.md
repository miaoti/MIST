# G3 TrainTicket depth site — cancel→refund missing-compensation (source-verified)

> **CORRECTION (2026-07-03, survey pass):** the claim below that the response-assertion
> comparator "PASSES/misses" under the fault is WRONG for injectable infra faults: the
> exchange throws → CancelController's catch returns `{1,"error"}` → the frozen blind
> contract's msg gate FAILs it (comparator flags; detection tie, MIST wins diagnosis).
> The clean `{1,"Success."}` miss requires the constructed fabricated-ack stratum.
> See [g3-tt-defect-survey.md](g3-tt-defect-survey.md) — authoritative.

Prereg §0.5 live-verification against the TT fork
(`../../../../train-ticket-injection`, our `ts-*` sources). **Result: the
missing-compensation defect is NATURAL in the upstream source** — not injected — and
has the exact acknowledged-but-lost-write shape MIST's differential oracle targets and
a response-assertion oracle misses. This is the chosen G3 head-to-head defect.

## The natural bug (in `CancelServiceImpl.cancelOrder`)
`ts-cancel-service/.../CancelServiceImpl.java` lines 57-92:
1. `cancelFromOrder(order)` sets status=CANCEL and PUTs `ts-order-service` — the order
   status transition. **This write succeeds independently.**
2. On cancel success it computes the refund and calls
   `boolean status = drawbackMoney(money, loginId, headers)`.
3. **The bug:** if `drawbackMoney` returns **false** (the refund failed), the code only
   logs an error (lines 89-90) and **STILL returns `Response(1, "Success.", ...)`**
   (line 92). The order is cancelled but the money is never refunded, and the client
   receives `{status:1, "Success."}`. (The controller even returns `{1,"error"}` on an
   exception — line 50 — so this endpoint essentially never signals failure.)

So the API **acknowledges a successful cancel+refund while the refund write is lost** —
a missing compensation in a saga, invisible to any oracle that trusts the response.

## Endpoints
- **Write (the acked operation):** `GET /api/v1/cancelservice/cancel/{orderId}/{loginId}`
  → `{1,"Success."}` (a state-mutating GET; **bodyless** — isolation is by a fresh
  order/user per run, not body-freshening).
- **The lost compensation write:**
  `GET /api/v1/inside_pay_service/inside_payment/drawback/{userId}/{money}` →
  `InsidePaymentServiceImpl.drawBack` saves a `Money{userId, money, type=D}` to
  `addMoneyRepository` and returns `{1,"Draw Back Money Success"}`.
- **Read-back for the refund (B2 target):**
  `GET /api/v1/inside_pay_service/inside_payment/money` (`queryAddMoney` →
  `addMoneyRepository.findAll()`) — the list of Money records. **Business key =
  `(userId, money, type=D)`.** The list is GLOBAL/growing (findAll, all users) → the
  §0 `readback_bound` completeness check applies (eng item i).
- (Separately, the order-status read-back via the order service shows CANCEL — that
  write is NOT lost; the B2 triple targets the **refund** record, which is.)

## Fault to trigger it (realistic)
`drawbackMoney` returns false when the inside-payment `drawback` call yields status≠1.
Two paths: (a) **Toxiproxy sever `ts-inside-payment-service` ↔ its DB (or the
cancel→inside-payment hop)** during the cancel → the drawback throws/returns non-1 →
missing compensation (the pre-registered S1 shape); (b) natural: `drawBack` returns 0
when the user has no prior `addMoney` record (`findByUserId == null`, line 246). The
head-to-head uses (a) — a realistic payment-service hiccup — so the defect is exposed
by a fault on an **unmodified** system.

## The head-to-head (why it is the PC-moving result)
- **MIST B2 (differential read-back):** cancel is acked `{1,"Success."}`; the refund
  Money`{userId, money, type=D}` is **ABSENT** from `queryAddMoney` at the cap → FIRE
  (acknowledged-but-lost write). Automatic — no knowledge of the refund semantics
  required.
- **Response-assertion comparator:** the frozen contract for cancel is `{1,"Success."}`
  → the response **PASSES** under the fault. To catch the bug the blind author must
  have written a STATE postcondition asserting the refund record appears — the
  empirical question the head-to-head answers.

## Freeze-protocol constraint (important)
`ts-cancel-service` and `ts-inside-payment-service` are in the frozen blind set's
**`not_covered`** list (`g2-comparator/blind-assertions-trainticket.yaml` — 22
covered services, these two excluded). **The comparator has no frozen contract for
cancel→refund.** Therefore the depth head-to-head requires a **blind-authored contract
extension** for the cancel→refund flow, frozen by commit **before** the fault is
revealed (freeze-before-reveal): a blind author writes what `cancel` and the refund
SHOULD do from the API/source, independent of this specific bug. Whether that author
includes the refund-record STATE postcondition (→ comparator catches) or only the
response + order-status checks (→ comparator misses) is the honest, pre-registered
question. MIST catches it either way.

## Next steps (this depth site)
1. **Blind-author + freeze** the cancel→refund contract extension (A1 protocol: fresh
   author, TT source + API only, blind to the injected fault; freeze by commit).
2. **B2 triple config** for cancel→refund: write = the cancel GET (bodyless →
   fresh-order-per-run isolation, NOT body-freshening); read-back = `queryAddMoney`
   membership on `(userId, money, type=D)`; `readback_bound` set for the global list.
   (Design note: the bodyless-GET write needs a setup that creates+pays a fresh order
   per run so there is a real refund to lose — a per-triple harness step.)
3. **TT deploy** (minikube) + **Toxiproxy** on the inside-payment DB hop + run MIST B2
   and the comparator on the same cancel scenarios; record the head-to-head.

*Depends on: TT fork sources (verified here). Feeds: the G3 centerpiece head-to-head.
The full 79-endpoint breadth binding round (Rider 2) is a separate, parallel surface;
this depth site is the headline.*
