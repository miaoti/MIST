# F-corpus spec — F1–F22 description-only eligibility survey (Wave R1, Phase B0-1)

**Date:** 2026-07-13 · Owner: main_track · Status: **B0 survey EXECUTED (document+web only)**
**Authority:** `wave-r1-corpus-completion-plan.md` §4-B0-1 · `c2-freeze.md` rev 2.1 §5 (C-A4
distinct-site rule) · `c2-license-audit.md` (replicate-by-description disposition, conduct rule 1).
**Role of this file (C-F3 two-actor clean-room):** this spec is the ONLY upstream-derived input the
Phase-B implementer may consume (plus the clean Apache-2.0 `FudanSELab/train-ticket` base source).
The implementer must NEVER fetch `FudanSELab/train-ticket-fault-replicate`, nor any re-hosted copy
of its fault branches (including the `ts-error-*` / `istio-error-*` branches that exist on
`AsifShaafi/train-ticket-injection`), nor any `faults/`, `faults-dingding/`, `faults-lwh/` content.

---

## §0 Clean-room conduct — inputs consumed and refused

The upstream fault repo is UNLICENSED (GitHub `license: null`, verified 2026-07-13 —
https://api.github.com/repos/FudanSELab/train-ticket-fault-replicate): its CODE is
all-rights-reserved; its FACTS/descriptions are not copyrightable. Accordingly:

**Consumed (prose only, all accessed 2026-07-13):**
- **[README]** — the fault table / per-fault narrative ("Industrial fault description" +
  "TrainTicket replicated fault description") in the README of
  https://github.com/FudanSELab/train-ticket-fault-replicate . Read twice via the WebFetch
  summarization layer (two independent fetches; the load-bearing rows cross-agree between fetches).
- **[PAGE]** — the study's companion page https://fudanselab.github.io/research/MSFaultEmpiricalStudy/ ,
  which carries the survey paper's industrial fault table (per-fault symptom + root cause, F1–F22).
- `ts-fault.txt` (repo root) was fetched and **classified only**: it is an experiment-metrics table
  skeleton (fault × method × recall columns), NOT fault-description prose — **no content from it is
  used** below.
- The TSE paper PDF (mirror) failed to parse as text (binary); **no paper-body content was ingested**;
  the paper's fault-table prose is represented here solely by [PAGE].

**Refused (never fetched/opened):** any source code, diff, patch, or fault-implementation branch of
the upstream repo or its re-hosts — branches `ts-error-*`, `istio-error-*`, directories `faults/`,
`faults-dingding/`, `faults-lwh/`. Branch NAMES were observed via the GitHub branches API (metadata
only); no branch content was read.

**Authorship:** every description below is this survey's own paraphrase; quoted fragments are short
cited phrases from the README/page prose. **Caveat:** the WebFetch layer summarizes; where the
summarizer's service attribution could be loose, the per-fault row says so. Final in-class status is
decided only by the live B-m6 verification gate in Phase B — description-level uncertainty is priced
in there.

## §1 Pinned eligibility rule (masked-2xx)

**ELIGIBLE** iff, by description alone, the fault plausibly produces an ACKNOWLEDGED-SUCCESS
response (HTTP 2xx or success envelope) while a durable write is **lost or corrupted** downstream
(acked-but-lost / masked data-integrity class). **INELIGIBLE:** loud failures (5xx, timeouts,
crashes, visibly returned errors), pure-performance faults, UI-only/read-only-display faults.
**UNDETERMINED-BY-DESCRIPTION** = too thin to tell → counts as INELIGIBLE for planning.

## §2 Occupied-site cross-check set (given, freeze C-A4/C-F2)

The corpus already occupies these TT defect sites; a fault targeting any of these services is
**OCCUPIED** — it earns mechanism/floor-6 credit only, NEVER a new distinct site:

| occupied site | services |
|---|---|
| cancel-refund (flagship) | ts-cancel-service, ts-inside-payment-service, ts-order-service |
| createaccount | ts-inside-payment-service |
| adminroute | ts-admin-route-service, ts-route-service |
| adminbasic-contacts | ts-admin-basic-info-service, ts-contacts-service |

C-A4 note: site identity is the durable write target that gets LOST; where a candidate's lost
artifact might differ from the occupied site's artifact on the same store, the row flags it for
authoring-time adjudication (never silently claimed as a new site).

## §3 Per-fault survey

Format per fault: **target** (TT replication, best-effort ts-* mapping — implementer verifies names
against the clean base tree) · **paraphrase** (own words) · **input** · **eligibility** · **occupancy**.

### F1 — order-cancel asynchronous sequencing
- **Target:** ts-cancel-service / ts-inside-payment-service / ts-order-service (cancel flow).
- **Paraphrase:** the cancellation flow performs its two downstream effects as asynchronous messages
  with no sequence control; the event that resets the order's status can complete before the event
  that returns the customer's money (README phrase: reset-order-status "will complete before"
  drawback-money). The user-facing cancellation therefore reads as settled while the refund side
  lags or never lands. [PAGE] root cause: asynchronous delivery lacking sequence control.
- **Input:** [README] F1 row + [PAGE] F1 row.
- **Eligibility:** **ELIGIBLE** — acknowledged cancel with the durable refund write trailing/lost is
  exactly the acked-but-lost class.
- **Occupancy:** **OCCUPIED** — this is the corpus flagship cancel-refund site itself (plan C-F2
  pre-call). Floor-6/mechanism credit only.

### F2 — report data returned out of order
- **Target:** reservation/report read path (UI-facing composition of several queries).
- **Paraphrase:** multiple data requests composing one report return in an unexpected order, so the
  displayed report shows wrong information; a read/presentation-composition fault with no durable
  write in play.
- **Input:** [README] F2 row + [PAGE] F2 row.
- **Eligibility:** **INELIGIBLE** (read/display only).
- **Occupancy:** unoccupied (moot).

### F3 — JVM vs Docker memory configuration mismatch
- **Target:** multiple services (container/JVM configuration).
- **Paraphrase:** JVM memory settings exceed the container's limit, so processes are periodically
  killed and the system periodically returns server 500s.
- **Input:** [README] F3 row + [PAGE] F3 row.
- **Eligibility:** **INELIGIBLE** (loud: 500s/crashes).
- **Occupancy:** cross-cutting (moot).

### F4 — fine-grained SSL overhead
- **Target:** platform-wide (SSL at nearly every instance).
- **Paraphrase:** SSL processing at each hop makes response times very long on complex calls.
- **Input:** [README] F4 row + [PAGE] F4 row.
- **Eligibility:** **INELIGIBLE** (pure performance).
- **Occupancy:** cross-cutting (moot).

### F5 — thread-pool/connection exhaustion across request types
- **Target:** basic-info query service (ts-basic-service; NOTE: distinct from the occupied
  ts-admin-basic-info-service — summarizer wrote "Basic-Info-Service", mapping is best-effort).
- **Paraphrase:** a high-volume request type saturates the service's connection/thread pool so a
  different request type waits and finally receives a timeout exception.
- **Input:** [README] F5 row + [PAGE] F5 row.
- **Eligibility:** **INELIGIBLE** (loud timeout).
- **Occupancy:** unoccupied (moot).

### F6 — SQL error in a dependency causing endless retries  *(named floor candidate)*
- **Target:** voucher service path (ts-voucher-service neighborhood).
- **Paraphrase:** a SQL error inside a dependent microservice makes the calling service retry
  endlessly/recursively; the caller slows down and the request finally returns a timeout error.
- **Input:** [README] F6 row + [PAGE] F6 row.
- **Eligibility:** **INELIGIBLE** — the described end state is a visible timeout error (loud). If a
  live replication ever showed an early success-ack before the retry storm that would be behavior
  the description does not claim; by description this is loud.
- **Occupancy:** unoccupied (moot).
- **Floor-candidate check: F6 does NOT survive.**

### F7 — third-party payment dependency overload
- **Target:** third-party payment path (Node.js side; ts-payment adjacency — ts-payment itself is
  NOT in the occupied set).
- **Paraphrase:** overload of requests to an external payment dependency produces denial of service;
  the system's payment function visibly fails (timeout).
- **Input:** [README] F7 row + [PAGE] F7 row.
- **Eligibility:** **INELIGIBLE** (loud, visible payment failure).
- **Occupancy:** unoccupied (moot).

### F8 — redis-held key/token missing or wrongly read in the booking flow  *(named floor candidate)*
- **Target:** auth/VIP token path feeding ticket booking — ts-auth-service / ts-user-service /
  verification-code neighborhood (README frames it as the user-authentication/VIP process).
- **Paraphrase:** a key/token saved in redis is read wrongly, or is not passed from one microservice
  to its dependency, so a booking-flow request proceeds carrying a missing/wrong token. The
  industrial symptom is a default selection silently changing: the flow CONTINUES with no error while
  behaving as if a different (non-VIP/default) state had been requested — e.g., a VIP entitlement
  silently not applied during booking.
- **Input:** [README] F8 row + [PAGE] F8 row.
- **Eligibility:** **ELIGIBLE (borderline, disclosed)** — plausibly the booking acks 2xx while the
  persisted outcome derived from the token (order attributes/discount/selection) is silently wrong =
  masked corrupted-write. Borderline because the described symptom is a wrong selection/state rather
  than an explicit lost row; the B-m6 live gate must demonstrate the persisted-wrong leg.
- **Occupancy:** **UNOCCUPIED** (fault target services are outside the occupied set). **C-A4
  caveat:** if the implementation lands the corrupted artifact in the ts-order row, artifact-level
  distinctness vs the cancel-refund site (creation-content vs cancel-status/refund artifacts) must be
  adjudicated at authoring before any new-site claim.
- **Floor-candidate check: F8 SURVIVES (unoccupied).**

### F9 — RTL rendering error
- **Target:** login UI (CSS bi-directional style).
- **Paraphrase:** right-to-left display error for UI words due to a CSS style defect.
- **Input:** [README] F9 row + [PAGE] F9 row.
- **Eligibility:** **INELIGIBLE** (UI-only).
- **Occupancy:** UI (moot).

### F10 — wrong API used in a special business case  *(named floor candidate)*
- **Target:** ts-contacts-service (contacts used in a specific ticket-reservation scenario).
- **Paraphrase:** in one special case of business processing, the wrong API is invoked / the API
  returns an unexpected output, so the business data produced by that step is silently wrong; no
  error is described — the flow completes with wrong data. (Industrial analogue on [PAGE]: a
  bill-of-material count is wrong after a special-case update.)
- **Input:** [README] F10 row + [PAGE] F10 row.
- **Eligibility:** **ELIGIBLE** (masked wrong-write/wrong-output in a completing flow).
- **Occupancy:** **OCCUPIED** — ts-contacts-service is the adminbasic-contacts site (plan C-F2
  pre-called exactly this). Floor-6/mechanism credit only.
- **Floor-candidate check: F10 survives eligibility but is OCCUPIED.**

### F11 — cancellation writes applied in unexpected sequence with a fallible recheck
- **Target:** order-cancellation flow (ts-cancel-service / ts-order-service neighborhood).
- **Paraphrase:** the database values touched by cancellation are set in an unexpected sequence
  because sequence control is absent; a recheck step repairs the state only sometimes (it does not
  always execute), so the persisted outcome is intermittently wrong while the operation itself
  completes without a surfaced error.
- **Input:** [README] F11 row + [PAGE] F11 row.
- **Eligibility:** **ELIGIBLE** (acked operation, intermittently corrupted durable state).
- **Occupancy:** **OCCUPIED** (cancel-refund constellation). Floor-6/mechanism credit only.

### F12 — status-dependent fault in order processing
- **Target:** ts-order-service (trigger conditions: locked stations, thread pool at capacity).
- **Paraphrase:** the fault manifests only when the service is in a particular internal status; the
  industrial symptom on [PAGE] is a wrong status shown in a result table because a caller ignores an
  unexpected output within its call chain. Whether any durable write is lost is not stated; the
  described wrongness reads as query/processing output.
- **Input:** [README] F12 row + [PAGE] F12 row.
- **Eligibility:** **UNDETERMINED-BY-DESCRIPTION** (counts INELIGIBLE for planning). Swap-alternate
  ONLY if live probing in Phase B reveals a masked write effect — not planned, not counted.
- **Occupancy:** **OCCUPIED** (ts-order-service) regardless.

### F13 — cancellation confirmed while payment still in flight
- **Target:** booking + cancellation interleave (ts-cancel-service / ts-inside-payment-service /
  preserve path).
- **Paraphrase:** two requests where the latter needs the former's result are processed in the wrong
  order — confirmation of a cancellation has already begun while the payment process for the same
  order has not completed (README phrasing). Both operations are individually accepted, leaving the
  durable money/order state inconsistent with no surfaced error.
- **Input:** [README] F13 row + [PAGE] F13 row.
- **Eligibility:** **ELIGIBLE** (acked operations, durable financial-state inconsistency).
- **Occupancy:** **OCCUPIED** (cancel-refund constellation). Floor-6/mechanism credit only.

### F14 — seat-price calculation logic error
- **Target:** price-calculation path used by booking (ts-price-service / travel pricing;
  best-effort mapping).
- **Paraphrase:** the computed price for second-class seats is wrong due to a calculation-logic
  mistake ([PAGE] industrial analogue: a locked product wrongly included in an index calculation);
  no error is raised — callers receive and use the wrong figure.
- **Input:** [README] F14 row + [PAGE] F14 row.
- **Eligibility:** **ELIGIBLE (borderline, disclosed)** — as a pure query this is read-only, but the
  same calculation feeds the booking write path, where a 2xx-acked order persists a corrupted price
  value. The B-m6 gate must demonstrate the persisted-wrong-value leg (order row carrying the
  mis-calculated price), else the candidate fails in-class.
- **Occupancy:** **UNOCCUPIED** — pricing/calculation services are not in the occupied set (the
  adminbasic site's artifact is the CONTACT record, not price data). **NEW-SITE candidate.**

### F15 — gateway request-size limit blocks JSON POSTs
- **Target:** nginx gateway configuration (food/consign requests).
- **Paraphrase:** the gateway limits POST JSON bodies to roughly 200 bytes, so larger food/consign
  requests are blocked — a visible request failure at the edge.
- **Input:** [README] F15 row (the [PAGE] F15 industrial row is a different config-class incident —
  a data-sync job quitting on an actor-framework misconfiguration; the TT replication adapted it to
  a gateway size limit; both are loud).
- **Eligibility:** **INELIGIBLE** (loud reject).
- **Occupancy:** gateway (moot).

### F16 — upload size limit on route file upload
- **Target:** route-upload function (ts-admin-route-service neighborhood).
- **Paraphrase:** a max-content-length limit (README/[PAGE] phrase: "max-content-length"
  configuration "not allowing" a big file) makes the file-uploading process fail visibly.
- **Input:** [README] F16 row + [PAGE] F16 row.
- **Eligibility:** **INELIGIBLE** (loud upload failure).
- **Occupancy:** would be OCCUPIED (adminroute) anyway.

### F17 — deeply nested SQL slows queries past the timeout
- **Target:** voucher query path (ts-voucher-service neighborhood).
- **Paraphrase:** a constructed SQL statement with many nested select/from clauses takes ~10 s,
  exceeding the ~5 s network timeout; the grid/query is slow and times out.
- **Input:** [README] F17 row + [PAGE] F17 row.
- **Eligibility:** **INELIGIBLE** (performance → loud timeout).
- **Occupancy:** unoccupied (moot).

### F18 — null JSON field breaks the consuming frontend
- **Target:** ts-food-service response consumed by the UI.
- **Paraphrase:** a key in the returned JSON is null and the frontend dereferences it without a null
  check, breaking the food/chart page. The backend answers 2xx, but the effect is read-path/UI —
  nothing durable is written or lost.
- **Input:** [README] F18 row + [PAGE] F18 row.
- **Eligibility:** **INELIGIBLE** (UI/read-path).
- **Occupancy:** unoccupied (moot).

### F19 — locale-formatting error on displayed price
- **Target:** consign-price display (ts-consign-price-service neighborhood).
- **Paraphrase:** the product price is not formatted correctly for the French locale — a display
  formatting fault; the description gives no persisted-value effect.
- **Input:** [README] F19 row + [PAGE] F19 row.
- **Eligibility:** **INELIGIBLE** (UI/display).
- **Occupancy:** unoccupied (moot).

### F20 — library-version skew makes services disagree on order status  *(named floor candidate)*
- **Target:** order-status handling across multiple services (shared library at different versions).
- **Paraphrase:** two microservices built against different versions of a shared library interpret
  the same order-status value differently (README phrase: same order status has a "different value"
  across versions), producing silent cross-service inconsistency: an operation completes and acks
  while the status it wrote means something else to another service. ([PAGE] F20's industrial row is
  a different loud incident — a missing DB driver jar; the TT replication adapted it to
  version-skewed status semantics, which is the masked variant surveyed here.)
- **Input:** [README] F20 row (primary); [PAGE] F20 row (industrial analogue, differs).
- **Eligibility:** **ELIGIBLE** (masked corrupted-state: acked write whose durable meaning is wrong
  for downstream readers).
- **Occupancy:** **OCCUPIED (planning call)** — the status-bearing artifact is the ts-order row and
  order-status transitions belong to the flagship cancel-refund site. A variant landing the skew on
  a different status-bearing artifact would need fresh C-A4 adjudication at authoring; for planning
  it earns floor-6/mechanism credit only.
- **Floor-candidate check: F20 survives eligibility but is OCCUPIED(-leaning).**

### F21 — missing accessibility label
- **Target:** login/verification-code UI markup.
- **Paraphrase:** an aria-labelledby association is missing so a screen reader skips elements.
- **Input:** [README] F21 row + [PAGE] F21 row.
- **Eligibility:** **INELIGIBLE** (UI-only).
- **Occupancy:** UI (moot).

### F22 — SQL selects a column inconsistent with its FROM clause
- **Target:** voucher print/query path (ts-voucher-service neighborhood).
- **Paraphrase:** the constructed SQL statement names a wrong column in its select part relative to
  its from part; the request visibly returns a SQL column-missing error.
- **Input:** [README] F22 row + [PAGE] F22 row.
- **Eligibility:** **INELIGIBLE** (loud SQL error returned).
- **Occupancy:** unoccupied (moot).

## §4 Summary table

| F | target (TT replication) | eligibility | occupancy | note |
|---|---|---|---|---|
| F1 | cancel / inside-payment / order | **ELIGIBLE** | OCCUPIED (flagship) | async refund sequencing |
| F2 | report read path | ineligible | — | read/display |
| F3 | many (JVM/docker cfg) | ineligible | — | loud 500/kill |
| F4 | platform SSL | ineligible | — | performance |
| F5 | ts-basic-service | ineligible | — | loud timeout |
| F6 | voucher path | ineligible | — | loud (floor candidate REJECTED) |
| F7 | third-party payment | ineligible | — | loud |
| F8 | auth/VIP token path | **ELIGIBLE** (borderline) | **UNOCCUPIED** | NEW-SITE candidate; C-A4 caveat |
| F9 | login UI | ineligible | — | UI |
| F10 | ts-contacts-service | **ELIGIBLE** | OCCUPIED (adminbasic-contacts) | floor credit only |
| F11 | cancel flow sequencing | **ELIGIBLE** | OCCUPIED (cancel-refund) | floor credit only |
| F12 | ts-order-service | UNDETERMINED | OCCUPIED | swap-alternate only |
| F13 | cancel vs payment interleave | **ELIGIBLE** | OCCUPIED (cancel-refund) | floor credit only |
| F14 | price calculation | **ELIGIBLE** (borderline) | **UNOCCUPIED** | NEW-SITE candidate |
| F15 | gateway size limit | ineligible | — | loud reject |
| F16 | route upload limit | ineligible | (adminroute) | loud |
| F17 | voucher nested SQL | ineligible | — | performance/timeout |
| F18 | food JSON null → UI | ineligible | — | UI/read |
| F19 | consign price format | ineligible | — | UI/display |
| F20 | order-status version skew | **ELIGIBLE** | OCCUPIED (order artifact) | floor credit only |
| F21 | a11y label | ineligible | — | UI |
| F22 | voucher SQL column | ineligible | — | loud SQL error |

**Counts:** ELIGIBLE **7** (F1, F8, F10, F11, F13, F14, F20) · eligible-AND-unoccupied (NEW-SITE
yield) **2** (F8, F14) · UNDETERMINED 1 (F12, planning-ineligible) · ineligible 14.

## §5 Recommended candidate set (≥6, weighted to unoccupied)

Build order (unoccupied first): **F8, F14** (new-site candidates) → **F1, F10, F11, F13, F20**
(occupied; floor-6/mechanism credit only). That is 7 candidates against floor 6 / target 10 — the
achievable target is **7** (10 is out of reach by description; the cap never binds). Margin is ONE
candidate: if two candidates fail the live in-class gate (both borderlines F8/F14 carry real risk),
the wave lands at 5 < 6 and Phase B's stop rule / freeze shortfall disclosure engages. Swap pool
beyond these: F12 only, and only if live probing upgrades it (not counted for planning).

**Named floor candidates:** F6 **REJECTED** (loud-by-description) · F8 **SURVIVES, unoccupied** ·
F10 survives eligibility, **OCCUPIED** · F20 survives eligibility, **OCCUPIED**.

## §6 S1 distinct-site projection (§1.1 arithmetic) — ≥20 NOT PROJECTED

current 7 + TeaStore 1 + OTel (0–1) + Boutique (0–1) + SS (0–1) + F-corpus eligible-unoccupied **2**
(far below the 10-replication cap) = **10–13 distinct sites**.

**≥20 is NOT projected.** Hard ceiling on the current SUT set + these descriptions = **13** (every
optional verify leg landing + both borderlines passing; F12 cannot add a site — its service is
occupied). Per plan §1.1/§4-B0-1 the **stop-and-replan decision is hereby SURFACED before Phase B
spends its budget**. Options (plan-named):
1. **Extend the F-corpus target on unoccupied services** — STRUCTURALLY FUTILE for sites: the
   description-level eligible-unoccupied ceiling is 2; extension adds case-runs, not sites.
2. **Accept the disclosed <20 finding** — report both denominators (distinct-site AND case-run; the
   F-corpus adds ~7 fault cases + ~7 controls to the case-run denominator) with the freeze §5
   disclosed-shortfall framing.
3. **Widen elsewhere** (new SUT or non-F-corpus TT write surfaces, e.g., food/consign/voucher order
   flows) — out of R1's reviewed scope; requires a new plan + full review.

**Survey recommendation:** option 2 (option 1 cannot mathematically reach 20; option 3 is
out-of-plan). Decision routes per plan §7-1 (surfaced-with-options satisfies the DoD; the choice is
the user's / goal-mode orchestrator's, recorded before Phase B starts).

## §7 Implementer obligations (restated for the isolated Phase-B actor)

1. Inputs = THIS FILE + the clean `FudanSELab/train-ticket` base source (Apache-2.0) only. Never
   fetch `train-ticket-fault-replicate` or any `ts-error-*` branch content anywhere it is hosted.
2. Re-implement the DESCRIBED behavior class per fault (masked-2xx acked-but-lost/corrupted);
   implementation mechanics are the implementer's own choices; zero upstream fault code; modified
   base files carry Apache-2.0 §4 change notices.
3. Each candidate passes the live in-class verification gate (B-m6: masked-2xx acked-but-lost
   demonstrated live) before it counts; failures swap from this pool (F12 last resort) and are
   disclosed.
4. Occupied candidates are authored as mechanism variants on their existing sites — never claimed as
   new distinct sites; F8's C-A4 artifact adjudication happens at authoring and is recorded in-case.
5. Per-case provenance cites [README]/[PAGE] rows via this spec (input-artifact chain preserved).
