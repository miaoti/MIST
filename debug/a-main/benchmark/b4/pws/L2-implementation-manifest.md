# PWS L2 — F-corpus implementation manifest (isolated clean-room implementer)

**Date:** 2026-07-17 · **Role:** isolated two-actor clean-room IMPLEMENTER (wave PWS leg L2)
**Fork:** `C:\Users\miaot\Github\train-ticket-injection` · **Branch:** `fcorpus-build` (off
`MIST-trainticket` @ `a1767ab3`, which is left untouched for the orchestrator's diff-review)
**Scope executed:** implement + COMPILE-VERIFY only. No docker build, no deploy, no capture, no
B-m6 live verification, no case authoring — all OUT of scope (orchestrator's cross-session cluster
step).

## Clean-room conduct (attested)
- Inputs consumed: ONLY `debug/a-main/c2c3/f-corpus-spec.md` (description-only survey) +
  `debug/a-main/benchmark/b4/pws/drawback-collision-analysis.md` (orchestrator conduct guidance) +
  the clean Apache-2.0 base source in the fork.
- NEVER fetched/opened `FudanSELab/train-ticket-fault-replicate`, any re-host, any `ts-error-*` /
  `istio-error-*` branch, or any `faults*/` directory. No web fetches of upstream fault code. Every
  fault below is my own re-implementation of the DESCRIBED behavior class; zero upstream fault code.
- Each modified file carries an Apache-2.0 §4 change-notice comment (what changed + that it is a
  MIST-benchmark fault-injection addition).

## Toggle + collision discipline (attested)
- Each fault = its OWN `public static volatile String <name>FaultMode` field (DEFAULT `"none"`) +
  its OWN runtime toggle endpoint. No `-D`/`@Value` channel (matches the fork's verified pattern).
- `ts-inside-payment-service` is **byte-untouched** (`git diff MIST-trainticket --name-only` shows
  NO inside-payment file): `drawbackFaultMode` / `createAccountFaultMode` and the
  `drawBack`/`createAccount` paths are unmodified, so the flagship fabricated-ack case stays
  reproducible. No pom / build-config files were changed.
- All defaults are `"none"` → every existing capture/case is unaffected until a fault is explicitly
  toggled.

## Class discipline
Per f-corpus-spec: **6 of 7 = CORRUPTED-present** (present-but-wrong writes), **F1 = the LOST-class**
one. Downstream, corrupted-class cases enter the corpus but MIST's column is n_a
("out-of-scope-by-design" — MIST detects acknowledged-but-LOST, not acknowledged-but-CORRUPTED); MIST
bindings are NOT implemented here (SUT fault only).

## Compile-verification recipe (why non-default)
Environment JDK note (does NOT alter any source or build file): the only compiling JDKs on this
machine are JDK 21 (default) and a JDK 25 EA; TrainTicket targets JDK 8. To compile-verify under
JDK 21 I used two **command-line-only** overrides (no file edits, reverted-by-nature):
`-Dlombok.version=1.18.30` (the pinned 1.18.20 crashes on JDK 21: `JCTree qualid NoSuchFieldError`)
and `-Dmaven.compiler.release=8` (JDK 21 rejects ts-user-service's pom-hardcoded source/target 7;
`release` overrides it). All injected Java is plain source-7/8-compatible (no Java-8+ syntax), so the
verification is faithful; the orchestrator's Docker build uses the intended toolchain unchanged.

**Consolidated result:** `mvn -q -pl ts-cancel-service,ts-basic-service,ts-user-service,
ts-contacts-service,ts-order-service -am compile -DskipTests -Dlombok.version=1.18.30
-Dmaven.compiler.release=8` → **EXIT 0** (all 5 modules + ts-common, no errors). Baseline pre-change
compile of the same modules was green first.

---

## Per-fault records

### F1 — order-cancel asynchronous sequencing — **LOST** — COMPILE PASS
- **Spec row:** f-corpus-spec §3 F1 (ELIGIBLE; OCCUPIED flagship cancel-refund; floor/mechanism).
- **Target service:** ts-cancel-service.
- **Files touched:** `ts-cancel-service/.../cancel/service/CancelServiceImpl.java` (both cancel
  branches G|H and Z|K/Other), `.../cancel/controller/CancelController.java`.
- **Field:** `asyncRefundSeqFaultMode` ∈ {`none`,`lost`}.
- **Toggle:** `GET /api/v1/cancelservice/cancel/test/asyncrefundfaultmode/{mode}`.
- **Expected class:** LOST (acked-but-lost).
- **Input artifact (implemented sentence):** "the two downstream effects run as asynchronous
  messages with no sequence control; the order-status reset can complete before the refund, and the
  user-facing cancellation reads as settled while the refund side lags or never lands." →
  implemented: after the order-status reset lands, the refund message is fire-and-forget and never
  lands (drawback skipped, `status=true`), while cancelOrder still returns `{1,"Success."}` → refund
  Money row LOST.
- **Occupancy:** OCCUPIED (flagship cancel-refund site); mechanism/floor credit only.

### F8 — redis-held key/token misread in booking flow — **CORRUPTED** — COMPILE PASS
- **Spec row:** f-corpus-spec §3 F8 (ELIGIBLE borderline-disclosed; UNOCCUPIED; NEW-SITE; C-A4
  caveat).
- **Target service:** ts-user-service.
- **Files touched:** `ts-user-service/.../user/service/impl/UserServiceImpl.java` (saveUser +
  updateUser), `.../user/controller/UserController.java`.
- **Field:** `userSelectionFaultMode` ∈ {`none`,`corrupt`}.
- **Toggle:** `GET /api/v1/userservice/users/test/selectionfaultmode/{mode}`.
- **Expected class:** CORRUPTED-present.
- **Input artifact (implemented sentence):** "a key/token is read wrongly / not passed from one
  microservice to its dependency, so a request proceeds with a missing/wrong token and a default
  selection silently changes — the flow continues with no error as if a different (default) state
  had been requested." → implemented: the token conveying the user's document-type SELECTION is
  misread, so saveUser/updateUser silently persist the DEFAULT selection (`documentType = 0`)
  instead of the submitted value, while still acking 2xx ("REGISTER/SAVE USER SUCCESS").
- **Occupancy / C-A4 adjudication (DISCLOSED):** UNOCCUPIED → NEW-SITE candidate. Vanilla
  TrainTicket has **no VIP/redis/entitlement primitive** (verified by source scan), so the described
  entitlement/token is modelled abstractly on the user's persisted selection attribute. The
  corrupted artifact lands in the **ts-user row** (`document_type`), **NOT the ts-order row**, so
  the C-A4 order-artifact collision does NOT fire — this is a clean distinct site (user store),
  read-back-able via `GET /users/id/{userId}`. **Borderline risk:** the persisted-wrong leg is a
  user-attribute write, not an order write; the orchestrator's live B-m6 gate must confirm it counts
  in-class. If the gate rejects, swap pool = F12 only (per spec §5), disclosed.

### F10 — wrong API / unexpected output in a special business case — **CORRUPTED** — COMPILE PASS
- **Spec row:** f-corpus-spec §3 F10 (ELIGIBLE; OCCUPIED adminbasic-contacts; floor credit).
- **Target service:** ts-contacts-service.
- **Files touched:** `ts-contacts-service/.../contacts/service/ContactsServiceImpl.java`
  (findContactsById + a `perturbDocumentNumber` helper), `.../contacts/controller/ContactsController.java`.
- **Field:** `contactsWrongOutputFaultMode` ∈ {`none`,`corrupt`}.
- **Toggle:** `GET /api/v1/contactservice/contacts/test/faultmode/{mode}`.
- **Expected class:** CORRUPTED-present.
- **Input artifact (implemented sentence):** "in one special case the wrong API is invoked / the API
  returns an unexpected output, so the business data produced by that step is silently wrong and the
  flow completes with wrong data (no error)." → implemented: findContactsById returns an unexpected
  output — a silently perturbed document number (trailing-digit rotation) — on a **detached copy**
  (the stored contacts row is left untouched). Consumed by ts-preserve-service during booking →
  `order.setContactsDocumentNumber(...)` → the persisted order carries wrong contact data while the
  booking acks 2xx.
- **Occupancy (DISCLOSED):** OCCUPIED (contacts site); mechanism/floor credit only, NEVER a new
  site. The durable-wrong artifact lands in the **ts-order row** (contact fields — creation content,
  distinct from cancel-status/refund), recorded for authoring-time C-A4 adjudication.

### F11 — cancellation writes in unexpected sequence w/ fallible recheck — **CORRUPTED** — COMPILE PASS
- **Spec row:** f-corpus-spec §3 F11 (ELIGIBLE; OCCUPIED cancel-refund; floor credit).
- **Target service:** ts-cancel-service.
- **Files touched:** `ts-cancel-service/.../cancel/service/CancelServiceImpl.java` (cancelFromOrder +
  cancelFromOtherOrder, via a `cancelStatusWithPossibleRecheck` helper), CancelController.
- **Field:** `cancelSeqRecheckFaultMode` ∈ {`none`,`corrupt`}.
- **Toggle:** `GET /api/v1/cancelservice/cancel/test/seqrecheckfaultmode/{mode}`.
- **Expected class:** CORRUPTED-present, **intermittent**.
- **Input artifact (implemented sentence):** "the database values touched by cancellation are set in
  an unexpected sequence because sequence control is absent; a recheck step repairs the state only
  sometimes (it does not always execute), so the persisted outcome is intermittently wrong while the
  operation completes without a surfaced error." → implemented: a wrong status (`CHANGE`) is written
  first; a recheck runs on only ~half the invocations (static counter, even→repair to `CANCEL`,
  odd→leave wrong); when skipped, the order persists `CHANGE` instead of `CANCEL` while cancelOrder
  still acks success.
- **Occupancy:** OCCUPIED (cancel-refund constellation); mechanism/floor credit only.

### F13 — cancellation confirmed while payment still in flight — **CORRUPTED** — COMPILE PASS
- **Spec row:** f-corpus-spec §3 F13 (ELIGIBLE; OCCUPIED cancel-refund; floor credit).
- **Target service:** ts-cancel-service.
- **Files touched:** `ts-cancel-service/.../cancel/service/CancelServiceImpl.java` (both cancel
  branches, via a `fullRefundIgnoringPaidState` helper), CancelController.
- **Field:** `paymentInFlightFaultMode` ∈ {`none`,`corrupt`}.
- **Toggle:** `GET /api/v1/cancelservice/cancel/test/paymentinflightfaultmode/{mode}`.
- **Expected class:** CORRUPTED-present.
- **Input artifact (implemented sentence):** "two requests where the latter needs the former's
  result are processed in the wrong order — a cancellation is confirmed while the payment for the
  same order has not completed; both are individually accepted, leaving the durable money/order
  state inconsistent with no surfaced error." → implemented: under fault the refund is computed as
  the full 80% figure **ignoring the not-paid guard**, so money is drawn back for a not-yet-settled
  (NOTPAID) order — a refund with no captured payment = durable money/order inconsistency; both the
  cancel and the drawback ack success.
- **Occupancy:** OCCUPIED (cancel-refund constellation); mechanism/floor credit only.
- **Note:** manifests specifically when the cancelled order is NOTPAID (payment-in-flight); for an
  already-PAID order the corrupt path equals the normal 80% refund (no divergence) — disclosed.

### F14 — seat-price calculation logic error — **CORRUPTED** — COMPILE PASS
- **Spec row:** f-corpus-spec §3 F14 (ELIGIBLE borderline-disclosed; UNOCCUPIED; NEW-SITE).
- **Target service:** ts-basic-service (pricing calculation; feeds booking write path).
- **Files touched:** `ts-basic-service/.../fdse/microservice/service/BasicServiceImpl.java`
  (queryForTravel), `.../fdse/microservice/controller/BasicController.java`.
- **Field:** `secondClassPriceFaultMode` ∈ {`none`,`corrupt`}.
- **Toggle:** `GET /api/v1/basicservice/basic/test/faultmode/{mode}`.
- **Expected class:** CORRUPTED-present.
- **Input artifact (implemented sentence):** "the computed price for second-class seats is wrong due
  to a calculation-logic mistake; no error is raised — callers receive and use the wrong figure, and
  the same calculation feeds the booking write path where a 2xx-acked order persists a corrupted
  price value." → implemented: in queryForTravel the SECOND-class (economy) price is miscomputed as
  `distance * firstClassPriceRate` instead of `* basicPriceRate` (a copy-paste rate bug); first-class
  price untouched. Flows via ts-preserve-service `order.setPrice("economyClass")` →
  `createOrder` → the persisted order carries the wrong price while booking acks success.
- **Occupancy (DISCLOSED):** UNOCCUPIED → NEW-SITE candidate (ts-basic-service pricing calc is
  outside the occupied set; distinct from the adminbasic CONTACT artifact). **Borderline risk:** as a
  pure query it is read-only; the in-class leg is the persisted order price — the orchestrator's live
  B-m6 gate must demonstrate the persisted-wrong-price order. Injection is in queryForTravel (the
  single-travel endpoint ts-preserve-service uses for the booking write); the batch/search path
  queryForTravels is intentionally left clean (search display stays truthful; only the booked order
  persists wrong) — disclosed.

### F20 — library-version skew on order status — **CORRUPTED** — COMPILE PASS
- **Spec row:** f-corpus-spec §3 F20 (ELIGIBLE; OCCUPIED order artifact; floor credit).
- **Target service:** ts-order-service.
- **Files touched:** `ts-order-service/.../order/service/OrderServiceImpl.java` (modifyOrder, via an
  `applyStatusSkew` helper), `.../order/controller/OrderController.java`.
- **Field:** `orderStatusSkewFaultMode` ∈ {`none`,`corrupt`}.
- **Toggle:** `GET /api/v1/orderservice/order/test/statusskewfaultmode/{mode}`.
- **Expected class:** CORRUPTED-present.
- **Input artifact (implemented sentence):** "two microservices built against different versions of
  a shared library interpret the same order-status value differently — the same status has a
  different value across versions — so an operation completes and acks while the status it wrote
  means something else to another service." → implemented: modifyOrder (the cross-service
  `/order/status/{id}/{status}` write, driven by the pay flow via ts-inside-payment-service) persists
  a shifted status code (`(status+1) mod 7`, an off-by-one ordinal skew) while still acking success;
  readers using the correct version misinterpret it. All shifted codes are valid statuses → no loud
  failure (masked).
- **Occupancy:** OCCUPIED (order-status artifact = flagship cancel-refund constellation);
  mechanism/floor credit only, NEVER a new site.
- **Note:** injected at modifyOrder only (the canonical cross-service status endpoint / pay flow);
  saveChanges and updateOrder status writes left clean to bound blast radius — disclosed.

---

## Summary
| F | class | service | field | toggle path (after base) | compile |
|---|---|---|---|---|---|
| F1 | **LOST** | ts-cancel-service | asyncRefundSeqFaultMode | /cancel/test/asyncrefundfaultmode/{mode} | PASS |
| F8 | corrupted | ts-user-service | userSelectionFaultMode | /test/selectionfaultmode/{mode} | PASS |
| F10 | corrupted | ts-contacts-service | contactsWrongOutputFaultMode | /contacts/test/faultmode/{mode} | PASS |
| F11 | corrupted (intermittent) | ts-cancel-service | cancelSeqRecheckFaultMode | /cancel/test/seqrecheckfaultmode/{mode} | PASS |
| F13 | corrupted | ts-cancel-service | paymentInFlightFaultMode | /cancel/test/paymentinflightfaultmode/{mode} | PASS |
| F14 | corrupted | ts-basic-service | secondClassPriceFaultMode | /basic/test/faultmode/{mode} | PASS |
| F20 | corrupted | ts-order-service | orderStatusSkewFaultMode | /order/test/statusskewfaultmode/{mode} | PASS |

**All 7 survey-ELIGIBLE faults implemented + compile-verified (7/7 PASS). None dropped; F12 swap not
needed.** Class split = 1 lost (F1) + 6 corrupted, matching the PWS plan. New-site candidates = F8
(ts-user), F14 (ts-basic); occupied mechanism-variants = F1, F10, F11, F13, F20.

## Commits (branch `fcorpus-build`, off `MIST-trainticket`)
- `dcb2ec00` cancel-service F1/F11/F13
- `b44cab41` basic-service F14
- `b53cd897` user-service F8
- `437040c3` contacts-service F10
- `0e98ac36` order-service F20
- `4df14191` fix: F11 restored a pre-existing commented-out `order.setStatus(...)` dead line in
  cancelOrder that the F11 status-write replace had partly un-commented (compiled but unintended);
  the F11 injections remain correctly only inside cancelFromOrder + cancelFromOtherOrder. Net branch
  diff = 10 files, toggles-off behaviour byte-equivalent to base.

## Handoffs to the orchestrator (OUT of this actor's scope)
1. Pre-build diff-review for clean-room conduct (`git diff MIST-trainticket..fcorpus-build`;
   inside-payment must be empty — verified here).
2. Docker image build + deploy + **B-m6 live in-class verification** per fault (esp. the two
   borderlines F8 + F14 — confirm the persisted-wrong leg acks 2xx and reads back wrong).
3. Authoring-time **C-A4 artifact adjudication**: F10 (order contact fields) and F8 (user row, NOT
   order — no order collision) recorded above; never claim an occupied fault as a new site.
4. Case authoring (`benchmark/cases/*.json`) + corpus-class stamping (corrupted → MIST column n_a).
5. `FILE_INDEX.md` update for this manifest was intentionally NOT done — per L2 scope ("do NOT touch
   the MIST repo except to WRITE that one manifest file"); the orchestrator should index it.
