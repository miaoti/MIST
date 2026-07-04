# Value-delta ground truth — the clean win in concrete numbers (PRE-FUNDED configuration of record)

Hand-captured against the live TrainTicket (host → ts-gateway 18888, inside-payment `:1.0.5`),
matching the harness stimulus of record: fresh buyer per leg, **PRE-FUNDED to a 50.00 balance**
(addMoney, type-A Money row), then a PAID 100.0 order, then cancel. Shows the constructed clean win
at the byte level: the cancel responses are IDENTICAL and the buyer is PRESENT in `/account` in
BOTH legs — only the persisted balance VALUE differs. A response-only comparator cannot see it, a
membership STATE_GET (is the buyer present?) passes on both legs and cannot catch it; ONLY the
arithmetic +refund delta discriminates — which is exactly MIST's differential value-delta.

```
[control|none]          buyer=d2b46ea8-919c-46e6-aa84-a77a42a5e17a
[control|none]          addMoney     = {"status":1,"msg":"Add Money Success","data":null}
[control|none]          baseline row = "userId":"d2b4...e17a","balance":"50.00"    <-- PRESENT
[control|none]          cancel ack   = {"status":1,"msg":"Success.","data":"test not null"}
[control|none]          final row    = "userId":"d2b4...e17a","balance":"130.00"   <-- +80.00 refund LANDED

[fault|fabricatedack]   buyer=a30e7df5-00dd-4c2b-908b-0bfa9899cb0d
[fault|fabricatedack]   addMoney     = {"status":1,"msg":"Add Money Success","data":null}
[fault|fabricatedack]   baseline row = "userId":"a30e...cb0d","balance":"50.00"    <-- PRESENT
[fault|fabricatedack]   cancel ack   = {"status":1,"msg":"Success.","data":"test not null"}
[fault|fabricatedack]   final row    = "userId":"a30e...cb0d","balance":"50.00"    <-- refund LOST (no movement)
```

- The two cancel acks are **byte-identical** `{"status":1,"msg":"Success.","data":"test not null"}`
  (cancelOrder's G|H branch, `CancelServiceImpl.java:92`), HTTP 200 both → the frozen
  response-assertion comparator (HTTP_STATUS 200 + ENVELOPE_STATUS 1 + MSG_CONTAINS "Success.")
  passes both → **MISSES**.
- The buyer's `/account` row is PRESENT at baseline **in both legs** (50.00) → a membership
  `STATE_GET` (contains the buyer) also passes both legs → **cannot catch the lost refund**.
- The only divergent observable is the balance VALUE: control `50.00 → 130.00` (the 80.00 = 80 % of
  the 100.0 fare refund landed as a type-D Money row; `queryAccount` sums Money − Payments), fault
  `50.00 → 50.00`. MIST's value_probe (match_field=userId, value_field=balance, X = value differs
  from the leg's own pre-write baseline) → **FIRES**.

## Isolation note (for the record)

`GET /inside_payment/account` (queryAccount) returns a GLOBAL list of all accounts. Per-leg
isolation relies on each leg using a FRESH registered buyer (unique userId) and MIST filtering the
list to that userId. With the pre-fund, each leg's baseline is "buyer present at 50.00" and
X-present = "the buyer's balance differs from ITS OWN baseline". The harness machine-gates this
configuration: the stimulus asserts the addMoney envelope is a real `{status:1}` success, and the
runner aborts the cell unless both legs' parsed baselines are the SAME POSITIVE number
(`CancelRefundHeadToHead#requirePreFundedBaselines`) — a silently soft-failed pre-fund can no
longer regress the cell to the appear-vs-absent shape.

---

## APPENDIX — SUPERSEDED capture (pre-fix, fresh ZERO-balance configuration; kept for history)

The first hand capture (2026-07-03, before the round-1 review fix wave) used an UN-funded fresh
buyer. Round-1 reviewers A+B correctly flagged that configuration as **membership-degenerate**: the
buyer was ABSENT from `/account` at baseline, so the "delta" was really an appear-vs-absent
presence signal — which a comparator membership STATE_GET could in principle also express, making
the clean win contestable. It was superseded by the pre-funded configuration above (harness commit
`dca01b0`); the numbers below are retained only as the historical record of that earlier shape:

```
[control  mode=none]          cancel ack = {"status":1,"msg":"Success.","data":"test not null"}
[control  mode=none]          buyer's own /account row = "userId":"d434...9fc","balance":"80.00"   <-- appeared
[fault    mode=fabricatedack] cancel ack = {"status":1,"msg":"Success.","data":"test not null"}
[fault    mode=fabricatedack] buyer's own /account row = <ABSENT>                                  <-- never appeared
```
