# Value-delta ground truth — the clean win in concrete numbers

Hand-captured against the live TrainTicket (host → ts-gateway 18888), fresh buyer per leg, a
PAID 100.0 order then cancel. Shows the constructed clean-win at the byte level: the cancel
responses are IDENTICAL, only the persisted refund state differs — exactly what a response-only
comparator cannot see and MIST's value-delta can.

```
[control  mode=none]          buyer=d4340918-f670-4d99-bff2-0e590b9d99fc
[control  mode=none]          cancel ack = {"status":1,"msg":"Success.","data":"test not null"}
[control  mode=none]          buyer's own /account row = "userId":"d434...9fc","balance":"80.00"   <-- refund PRESENT

[fault    mode=fabricatedack] buyer=c20106b8-e072-43e5-9e4b-e44c007b5f26
[fault    mode=fabricatedack] cancel ack = {"status":1,"msg":"Success.","data":"test not null"}
[fault    mode=fabricatedack] buyer's own /account row = <ABSENT — no refund row>               <-- refund LOST
```

- The two cancel acks are **byte-identical** `{"status":1,"msg":"Success.","data":"test not null"}`
  (the cancelOrder G|H branch, `CancelServiceImpl.java:92`). HTTP 200 both. So the frozen
  response-assertion comparator (HTTP_STATUS 200 + ENVELOPE_STATUS 1 + MSG_CONTAINS "Success.")
  passes both → cannot distinguish → **MISSES** the fault.
- The persisted state diverges: the control buyer's refund lands as a type-D Money row
  (`balance:"80.00"` = the 100.0 fare minus fee); the fault buyer's never lands. MIST's value_probe
  (match_field=userId=buyer, value_field=balance on GET /inside_payment/account) reads the buyer's
  own row present (control) vs absent (fault) → **FIRES**.

## Isolation note (for the record)

`GET /inside_payment/account` (queryAccount) returns a GLOBAL list of all accounts, not the
caller's alone. Per-leg isolation therefore relies on each leg using a FRESH registered buyer
(unique userId) and MIST filtering the list to that userId — not on endpoint-level scoping. With
a fresh buyer the baseline is "userId absent" and X-present = "userId now appears with a balance",
so a control refund makes the row appear and a lost refund leaves it absent. This is the
"fresh-buyer-appearing counts" value-delta path; it is sound as long as the per-leg-fresh-buyer
runbook rule holds (it does here — the harness registers a new user per leg).
