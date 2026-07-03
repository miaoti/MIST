# G3 TT depth-site survey — where can an acked-but-lost write actually occur? (source-verified)

User-requested research pass ("investigate before deciding") for the "do both"
direction (cancel→refund depth + a clean-membership second site). Result: the survey
**corrects a load-bearing expectation** in the two earlier docs
([g3-tt-cancel-refund-defect.md](g3-tt-cancel-refund-defect.md),
[g3-tt-headtohead-design.md](g3-tt-headtohead-design.md)) and yields a sharper,
more defensible head-to-head design. Everything below is verified in the fork sources.

## 1. Repo-wide facts (the failure-conversion map)
- **`Response<>(1,"error")` catch-all is UNIQUE to ts-cancel-service** (repo-wide grep:
  1 hit, CancelController.java:50). No other TT service converts exceptions into
  envelopes; ts-inside-payment-service and ts-assurance-service have **zero try/catch**.
- Every inter-service call uses `restTemplate.exchange`, which **throws on any non-2xx**.
  With no catches anywhere (except cancel), a downstream infra failure becomes an
  uncaught exception → HTTP 500 from the upstream → **response-visible**.
- The "swallow" branches that DO exist — preserve's three
  (`Success.But Buy Assurance/Food Fail.`, `Consign Fail.`, all status=1,
  PreserveServiceImpl.java:184-238), inside-payment `pay()` **ignoring** the
  `setOrderStatus` result (InsidePaymentServiceImpl.java:121,128), rebook's
  `return result.getStatus()==1` helpers — all trigger only on a downstream
  **HTTP-200-with-status-0 envelope**, which an infra fault never produces (see above).
  cancel's own `{0,"Draw Back Money Failed"}` branch is effectively **dead code**
  (`findByUserId != null` — Spring Data returns an empty list, never null).

**⇒ On the UNMODIFIED fork, no network-level fault anywhere can produce a
clean-success acked-but-lost write.** Every injectable infra failure is
response-visible: either a 5xx, or (uniquely, cancel) `{1,"error"}` — an *acked*
envelope (status=1) that still **leaks the anomaly in `msg`**.

## 2. Correction to the earlier docs (disclosed)
The defect doc and design doc §4 said the response-assertion comparator would
**PASS** under the natural cancel fault. **That is WRONG for the frozen blind
contract:** under any injectable infra fault, `drawbackMoney`'s exchange throws →
CancelController's catch returns `{1,"error"}` → the frozen contract's success gate
(`status==1 AND msg=="Success."` — FC4, EXECUTABLE per REVIEW-BLIND-CANCEL-B)
**FAILs the response → the comparator flags it.** The blind author's msg gate was
precisely the strong-baseline behavior our fairness review certified.
- MIST also fires: `acked = HTTP 2xx && (bodyStatus==null || bodyStatus==1)`
  (DataIntegrityRuntime.java:403) → `{1,"error"}` IS acked; refund absent → FIRE.
- So the natural stratum is a **detection tie with a diagnosis gap**: the comparator
  says "envelope anomalous" (no idea anything was lost); MIST names the lost refund
  (order cancelled, money never returned) and its verdict does not depend on the
  response leaking at all.

## 3. Where the comparator is STRUCTURALLY blind (and how to reach it)
The comparator misses only when the response is clean AND its state clauses are
unbindable (REVIEW-BLIND-CANCEL-B: no delta, no snapshot, no JWT, body-resolved
fields only). The clean-response acked-but-lost is reachable ONLY via the
**constructed stratum** — a SUT-fork flag, exactly the Gate-1/G2 methodology
(disclosed scaffolding): a `fabricated-ack drawBack` flag (return
`{1,"Draw Back Money Success"}` without saving) →
cancel returns a **perfect `{1,"Success."}`** → comparator response gate PASSES,
refund state clause NOT_CHECKABLE → **MISS**; MIST: acked + refund absent → **FIRE**.
This emulates the documented real-world class (ack-then-lose: fire-and-forget
handoffs, async replication loss) and is disclosed as constructed.

## 4. The "second clean-membership site" (the do-both leg) — reframed
Body-carrying CRUD endpoints (contacts, food, consign …) + fabricated-ack flags are
NOT MIST-wins: their frozen state clauses (e.g. food's
"GET /orders/{orderId} returns the order") ARE bindable (body present → `${field}`
resolves) → **the comparator catches those too**. That is a FEATURE for fairness:
report one such site as an **agreement case** proving the comparator is not a
strawman (it catches whenever its primitives suffice). The discriminating axis is
**state-clause BINDABILITY** — bodyless writes, delta/aggregate observables,
auth-gated read-backs — not "has a state clause". cancel→refund sits squarely in the
unbindable class (bodyless GET + balance delta + JWT: all three).

## 5. Resulting design menu (decision point)
- **Option 1 (recommended) — two-stratum depth + agreement anchor:**
  (a) natural stratum (Istio route-abort on the drawback hop, unmodified fork):
  both detect; MIST localizes the lost refund — reported as the diagnosis gap;
  (b) constructed stratum (fabricated-ack drawBack flag, disclosed): comparator
  misses, MIST fires — the clean win; (c) one body-carrying agreement site
  (comparator catches too — fairness anchor); (d) Rider-2 binding round over the
  ~80 frozen endpoints quantifies the unbindable-fraction = the breadth number.
- **Option 2 — natural-only purist:** no constructed stratum; claim = detection tie
  + diagnosis gap + the binding-round fraction. Zero constructed-optics, weaker
  headline.
- **Option 3 — breadth-only:** drop the depth site. Weakest; not recommended.

MIST engineering per Option 1 (unchanged from the design doc, all 3-cold-review
gated): pre-established isolation (bodyless GET), /account value-differential
read-back (+ JWT), IstioRouteFaultInjector; plus the fork-side fabricated-ack flag
(constructed stratum) and the setup harness (register→create→pay→cancel).

*Feeds: the user decision on the head-to-head design; then the build. Corrects:
g3-tt-cancel-refund-defect.md §"The head-to-head", g3-tt-headtohead-design.md §4
(pointer notes added in both).*
