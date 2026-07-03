# Cold review — Blind cancel→refund contract: EXECUTABILITY against the frozen comparator primitive set

Reviewer role: independent cold reviewer (no prior context assumed).
Question: clause by clause, can the FIXED-in-code test oracle actually EXECUTE the frozen contract?

Files read:
- Contract: `C:\Users\miaot\Github\MIST\debug\a-main\g3-comparator-tt\blind-cancel-refund-contract.yaml`
- Oracle primitive impl: `C:\Users\miaot\Github\MIST\mist-cli\src\main\java\io\mist\cli\comparator\ContractEvaluator.java` (`evaluateCheck`, ~120-224; `resolvePath`, `entityMatchesSubmittedFields`, `containsSubmittedFields`)
- Binding schema / closed primitive set: `...\comparator\AssertionBindings.java`
- Execution seam (corroborating): `...\comparator\ComparatorRunner.java`

---

## The closed primitive set (verbatim from code)

`Primitive { HTTP_STATUS, ENVELOPE_STATUS, ENVELOPE_DATA, MSG_CONTAINS, STATE_GET, NOT_CHECKABLE }`
(`AssertionBindings.java:29`).

What each can express, and — critically — what it CANNOT:

- **HTTP_STATUS** — the write's HTTP status code only.
- **ENVELOPE_STATUS** — the integer `status` field of the response envelope.
- **ENVELOPE_DATA** — only whether envelope `data` is null vs non-null (no value inspection).
- **MSG_CONTAINS** — envelope `msg` contains a fixed substring.
- **STATE_GET** — issue ONE read-back GET (`client.get(path)`), path may embed `${field:NAME}` resolved **from the submitted request body** (`resolvePath`, ContractEvaluator.java:231), then either PRESENCE (`contains-submitted-fields` / `entity-matches-submitted-fields`, polled to a cap) or single-shot ABSENCE. **Every compared VALUE is `String.valueOf(submitted.get(field))`** — i.e. a value the client PUT IN THE REQUEST BODY (`entityMatchesSubmittedFields`:269-270, `containsSubmittedFields`:295-296). It is a string-equality **membership** test.
- **NOT_CHECKABLE** — explicitly unbindable.

Structural gaps proven by reading the code (all four bear directly on this contract):
1. **No arithmetic / no delta.** STATE_GET only tests equality or absence. There is no subtraction, no `before+R`, no "exactly one less."
2. **No pre/post snapshot.** `evaluate()` runs AFTER the write; nothing is captured before it. `ComparatorRunner.runEndpoint` does one control write + one fault write, each with a single post-write read. No baseline exists to diff against.
3. **No auth / JWT seam.** `SutClient` is `post(path, body)` + `get(path)` — no header/Authorization parameter anywhere. A read-back that needs `Bearer <JWT>` returns non-2xx → `transportFailure=true` (ContractEvaluator.java:198-207) → reclassified comparator-infra-failure, never a check.
4. **Values come only from the SUBMITTED body.** A server-computed number (balance, refund R, sold-count) or a post-state constant (status==4) is not a submitted field, so no membership/entity check can reference it faithfully.

Corroborating execution-seam facts (runner, not primitive, but they seal (b)):
- The write is **POST-only**: `ComparatorRunner.path()` (197-204) throws on any endpoint not starting `"POST "`; the write is always `client.post(path, body)`. The cancel is `GET .../cancel/{orderId}/{loginId}` — not issuable as specified.
- The write path is used **verbatim**; `{orderId}`/`{loginId}` are never substituted (only STATE_GET paths get `${field:...}`, only `body_template` gets `${uuid|fresh:...}`).
- The submitted "body" is `substitute(body_template)` — **client-generated random tokens** (`${uuid:F}` random UUID / `${fresh:F}` random short string). There is no seed step and no capture of any server-assigned id back into the body.

---

## Per-clause binding table

Legend: **EXEC** = executable with the named primitive(s); **NOT_CHECKABLE** = falls to NOT_CHECKABLE.

| # | Clause (from contract) | Binding | Verdict | Why (one line) |
|---|---|---|---|---|
| R1 | HTTP 200 in ALL outcomes | HTTP_STATUS expect=200 | **EXEC** | the one field the write itself returns. |
| R2 | success ⇒ `status==1` | ENVELOPE_STATUS expect=1 | **EXEC** | integer envelope field, direct. |
| R3 | success ⇒ `msg=="Success."` (NOT status alone) | MSG_CONTAINS expect="Success." | **EXEC** | fixed substring; R2∧R3 = the mandated two-part success gate. |
| R4 | success `data` = "test not null" (G/D) vs null (other) | ENVELOPE_DATA (null-ness only) | **NON-DISCRIMINATING** | other-train SUCCESS is `data:null`, identical to every failure ⇒ data cannot signal success; contract itself says "must not be asserted beyond status+msg." |
| P1a | order.status flips to CANCEL(4): GET `/order/{orderId}` ⇒ `data.status==4` | (would need STATE_GET entity-matches, fields=[status]) | **NOT_CHECKABLE** | needs a REAL `orderId` in the submitted body (none — bodyless GET, only random tokens, no capture) AND `4` is a post-state constant, not a submitted value. |
| P1b | was PAID(1) BEFORE the cancel | — | **NOT_CHECKABLE** | requires a pre-cancel snapshot; none exists. |
| P1c | every OTHER Order field UNCHANGED (13 fields) | — | **NOT_CHECKABLE** | equality against a pre-cancel baseline of 13 fields; no snapshot primitive. |
| P1d | customer refresh: POST `/order/refresh` ⇒ data[] has order.id with status==4 | — | **NOT_CHECKABLE** | same real-orderId gap + status==4 constant; and only one STATE_GET path, un-parametrizable by a captured id. |
| **P2** | **REFUND: `balance_after == balance_before + R`**, read `/inside_payment/account` (JWT), filter by userId | — | **NOT_CHECKABLE** | four independent blockers: numeric DELTA (no arithmetic), pre-cancel SNAPSHOT (none), JWT read-back (no auth seam), and R/balance are server-computed (not submitted). |
| **P3** | **SEAT: sold-count is EXACTLY ONE LESS**, read `/order/{travelDate}/{trainNumber}` | — | **NOT_CHECKABLE** | delta ("one less") + snapshot, PLUS `travelDate`/`trainNumber` unresolvable from an empty body; count is a server aggregate. |
| FC1-resp | not-permitted ⇒ 200 / status0 / msg "Order Status Cancel Not Permitted" / data null | HTTP_STATUS, ENVELOPE_STATUS=0, MSG_CONTAINS, ENVELOPE_DATA=null | **EXEC** | pure envelope. |
| FC1-state | "no state change" (status/balance/sold-count all unchanged) | — | **NOT_CHECKABLE** | three invariances = three snapshot/delta checks. |
| FC2-resp | not-found ⇒ 200 / status0 / msg "Order Not Found." / data null | HTTP_STATUS, ENVELOPE_STATUS=0, MSG_CONTAINS, ENVELOPE_DATA=null | **EXEC** | pure envelope. |
| FC2-state | "no state change" | — | **NOT_CHECKABLE** | snapshot/delta. |
| FC3-resp | downstream flip fails ⇒ status0; other-path `msg` prefix "Fail.Reason:"; G/D `msg` dynamic; data null | ENVELOPE_STATUS=0, ENVELOPE_DATA=null, MSG_CONTAINS="Fail.Reason:" (other path only) | **PARTIAL** | status/data + the fixed prefix bind; the G/D `<order-service msg>` is dynamic ⇒ not matchable. |
| FC3-state | "No refund performed" | — | **NOT_CHECKABLE** | balance snapshot/delta. |
| **FC4** | exception ⇒ 200 / **`status:1, msg:"error"`** (false-success); msg≠"Success." = NON-success | HTTP_STATUS=200, ENVELOPE_STATUS=1, and the R3 gate MSG_CONTAINS "Success." FAILS | **EXEC** | this false-success trap is fully inside the envelope ⇒ the comparator CAN catch it via the msg gate. |
| FC5-resp | G/D post-mutation userinfo lookup fails ⇒ 200 / status0 / msg "Cann't find userinfo by user id." / data null | HTTP_STATUS, ENVELOPE_STATUS=0, MSG_CONTAINS, ENVELOPE_DATA=null | **EXEC** | envelope match binds. |
| FC5-incons | ...EVEN THOUGH order is already CANCEL(4) and the refund row is already written (response says fail, state fully mutated) | — | **NOT_CHECKABLE** | detecting the inconsistency needs the SAME unbindable state reads (order flip + refund delta) — the acked-failure-but-state-changed twin of acked-success-but-state-lost. |

---

## Focused answers to (a)/(b)/(c)

**(a) THE REFUND (postcondition 2) — NOT_CHECKABLE, four ways.**
- *Numeric delta?* No. The primitive set has zero arithmetic; STATE_GET is string-equality membership / absence. `balance_after == balance_before + R` cannot be phrased as "field X equals a submitted value."
- *Pre-cancel baseline?* No. `ContractEvaluator.evaluate` runs only AFTER the write; `ComparatorRunner` captures nothing before it. There is no snapshot slot in `Check`, `Clause`, `BoundEndpoint`, or the runner.
- *JWT-authenticated read-back?* No. `SutClient.get(path)` takes only a path — no header/Authorization argument exists. The account endpoint's required `Bearer <JWT>` makes the read 401/403 → `transportFailure` → infra-failure, not a check.
- *Value source?* Even a membership hack fails: R (=0.80·price, DecimalFormat) and `balance` are server-computed and `loginId` is a PATH param — none are submitted-body fields the primitive can compare against.
  Conclusion: the refund is provable ONLY as a numeric balance delta against a pre-cancel snapshot behind auth — none of which a stateless, membership-only, unauthenticated per-call oracle can express. **NOT_CHECKABLE.**

**(b) THE BODYLESS-GET problem — decisive; the state reads have nothing to resolve against.**
The cancel write carries NO body; identity (`orderId`, `loginId`) is in the PATH. But `${field:NAME}` and every presence/absence value resolve from the submitted BODY (`resolvePath`:231-246; membership:264-296). For this endpoint the "submitted body" is only `substitute(body_template)` = client-side random `${uuid}`/`${fresh}` tokens. Therefore:
- Postconditions 1/3 need `orderId`, `travelDate`, `trainNumber` and P2 needs `loginId` — all attributes of a REAL, pre-existing PAID order. A random UUID does not name any real order, so `/order/${field:orderId}` reads back "not found"/`data:null` and the check FAILS even on the CONTROL leg (⇒ comparator-infra-failure), i.e. it cannot even calibrate.
- To supply real coordinates you would need a seed-order-then-capture-server-id step feeding the submitted body — a mechanism the closed primitive set (and the runner) simply do not have.
- Corroborating: the runner is POST-only and uses the write path verbatim (`path()`:197-204), so `GET .../cancel/{orderId}/{loginId}` is not even issuable as written.
  Conclusion: as-is, the read-back paths are **unbindable**; making them bindable would require new path-param resolution + seed/capture that the primitive set lacks. **NOT_CHECKABLE.**

**(c) order-status `data.status==4` and seat "sold-count exactly ONE LESS" — both NOT_CHECKABLE.**
- *status==4*: closest binding is STATE_GET `entity-matches-submitted-fields` over `/order/${field:orderId}` with a submitted `status`. It dies on two counts: `orderId` has no real value to resolve to (see (b)), and `4` is a post-state constant, not a value the client submitted (smuggling a literal `"status":"4"` into the body distorts the "compare to what was submitted" semantics and still leaves P1b "was PAID before" and P1c "13 fields unchanged" needing an absent snapshot).
- *sold-count one less*: an explicit DELTA against a pre-cancel snapshot, over a server aggregate, at a path keyed by `travelDate`/`trainNumber` that the empty body cannot resolve. No arithmetic, no snapshot, no path value ⇒ **NOT_CHECKABLE.**

---

## Bottom line

**The comparator CAN execute (all envelope-level):** R1 (HTTP 200), the mandated two-part success gate R2∧R3 (`status==1 AND msg=="Success."`), the FC1/FC2/FC5 fixed failure envelopes, the FC3 envelope minus its dynamic G/D `msg`, and — notably — the FC4 **false-success** trap `{status:1, msg:"error"}`, which the msg gate correctly rejects. In short, it can adjudicate the RESPONSE contract and discriminate the fixed envelopes.

**It CANNOT execute (falls to NOT_CHECKABLE):** all three state postconditions — P1 (order flip + 13 unchanged fields), **P2 (the refund)**, P3 (seat/sold-count) — and every state-invariance rider on the failure contracts (FC1/FC2/FC3 "no state change / no refund", and the FC5 "state fully mutated despite a failure response" inconsistency).

**Why the refund and the count deltas are inexpressible in a stateless per-call oracle:** each is a *relation across two points in time* over a *server-computed quantity*, sometimes behind *auth* — `balance_after − balance_before == R`, `sold_after == sold_before − 1`. A per-call assertion oracle whose only stateful primitive is a single post-write GET testing string equality of **submitted-body** values (no arithmetic, no pre-snapshot, no JWT header, no seed/capture of server ids) has no vocabulary for "changed by exactly R" or "one less than before." It can only ask "does the response envelope look right?" and "is this submitted value present/absent in one read-back?" — which is exactly why, on the natural cancel→refund defect (refund acked in the envelope but lost in inside-payment), MIST's B2 balance read-back FIREs while this response-assertion comparator PASSES the envelope and misses the lost write. No clause was weakened here; the contract is faithful and the gap is entirely on the oracle's expressive side.
