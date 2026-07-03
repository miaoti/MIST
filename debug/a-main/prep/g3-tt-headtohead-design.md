# G3 TT cancel→refund head-to-head — engineering design analysis (pre-implementation)

Scopes the MIST-side engineering for the centerpiece head-to-head (MIST B2 vs the
blind comparator on the natural cancel→refund missing-compensation defect,
[g3-tt-cancel-refund-defect.md](g3-tt-cancel-refund-defect.md)). Written while the
blind contract is being authored; decisions marked RECOMMENDATION are not yet built.

## 1. The B2-triple gap (bodyless GET + external isolation)
The current `TargetTripleRegistry.Triple` isolates a write by freshening
`isolation_key` **fields in the request BODY** (`FRESH_STRINGS`/`STATION_PAIR`, via
`DataIntegrityRuntime.beforeWrite`→`freshen`). The cancel operation is
`GET /api/v1/cancelservice/cancel/{orderId}/{loginId}` — **bodyless**, with the
identity in PATH params. So:
- No body → `freshen` is a no-op → `pending.isolationKey` empty → the runtime records
  "no isolation key established" (NOT_EVALUABLE). The cancel triple is unrunnable as-is.
- The read-back key (the refund's `userId`) is not injected by the write — it is
  established by the **test setup** (which user's order is being cancelled).

**RECOMMENDATION — a bounded B2 extension: "pre-established isolation."** Add an
isolation strategy where the key is NOT freshened into the body but SUPPLIED by the
setup (the fresh `loginId`/`userId` for the run) and matched in the read-back. The
write body is left untouched; `beforeWrite` records the supplied key as the pending
isolation key; membership on the read-back uses it. This is additive (a new
`IsolationStrategy` + a supplied-key channel), flag-gated, and does not touch the
body-freshening path. Test-first + 3-cold-review per the standing pipeline.

## 2. The fault: a ROUTE-SCOPED abort of the drawback hop (not a global sever)
Gate-1/G2 used `SutFlagFaultInjector` (kubectl JAVA_TOOL_OPTIONS flag). The G3
cancel→refund fault must make the drawback call fail on an UNMODIFIED system.

**Researched constraint (corrects an earlier draft).** cancel-service reaches
inside-payment via a hardcoded k8s-DNS URL —
`CancelServiceImpl.drawbackMoney` → `http://ts-inside-payment-service/api/v1/
inside_pay_service/inside_payment/drawback/{userId}/{money}` (a GET). The refund
READ-BACK the oracle uses (`queryAccount`, `GET .../inside_payment/account`) also
lives on ts-inside-payment-service. So the fault **must NOT take inside-payment (or
its DB) down globally** — that would kill the read-back and yield an infra-failure,
not a detected loss. The fault must sever **only the `/drawback` route**, leaving
`/account` reachable.

**RECOMMENDATION — an Istio `VirtualService` fault.abort scoped to the drawback
URI prefix** (`match: uri.prefix: /api/v1/inside_pay_service/inside_payment/drawback`,
`fault.abort.httpStatus: 500`). This severs only the drawback (from any caller,
source-agnostic) → `drawbackMoney` gets a non-1 → returns false → the natural
missing-compensation bug fires, while `queryAccount` stays live to prove the refund
is absent. inject = `kubectl apply` the VirtualService; clear = `kubectl delete` it
(kubectl orchestration like `SutFlagFaultInjector`, so the injector is a thin
`IstioRouteFaultInjector` on the same clear→control→inject→fault→clear path). This
requires **TT running in an Istio mesh** — a deployment decision (§5): either TT into
the existing kind+Istio cluster (resource-check against the 26 GB budget with
Bookinfo/SockShop up — likely stop SockShop to fit) or minikube + the Istio addon.
(A Toxiproxy backend is possible but needs URL repointing around the hardcoded DNS,
so it is the fallback, not the primary.)

## 3. Setup harness (a real refund to lose)
The cancel is only meaningful if there is a PAID order to cancel (so a refund is due).
Per run the harness must: register a fresh user → create + PAY a fresh order → then
cancel. The refund Money`{userId, money, type=D}` is what the fault loses. This is a
per-triple setup step (the B2 pairing runs the SAME generated scenario twice; the
scenario must include the create+pay+cancel sequence, or the setup is a fixture).

## 4. Where MIST's advantage actually is (honest framing)
Authoring the MIST triple (`cancel write ↔ inside-payment/money read-back, key=userId`)
DOES require knowing the refund lands in inside-payment's Money collection — the SAME
domain knowledge the comparator author needs. So MIST's win is **not** "it needs no
knowledge of the read-back." MIST's structural advantages are:
- **Response-blindness by construction.** The cancel RESPONSE is `{1,"Success."}` (it
  lies). An oracle that checks the response PASSES. MIST checks STATE, systematically,
  for every registered triple — it cannot be fooled by a lying ack.
- **Differential precision.** Control (refund present) vs fault (refund absent) with
  isolation + quiescence-gating distinguishes a genuinely-lost write from a legitimate
  no-refund case or async delay — a static "assert a type=D record exists" can
  false-positive on NOTPAID/expired orders (refund legitimately 0) or flake on async.
- **The empirical question:** does the blind comparator author (a) write ANY state
  postcondition (vs response-only — the common case → MIST wins), and (b) if so, get
  the cross-service refund endpoint + membership + timing right (vs MIST's robust
  differential). The blind contract (being authored now) answers (a)/(b).

**Honest disclosure:** if the blind author writes a correct refund state assertion,
the comparator ALSO catches this defect → a TIE on cancel→refund. That is a legitimate
reportable outcome; MIST's paper-level advantage is demonstrated across the breadth of
endpoints (response-only assertions miss ALL acked-but-lost writes MIST catches) plus
the differential precision, not a single hand-picked defect. We report whatever the
frozen blind contract yields.

## 5. Build order (post blind-contract-freeze, and post the frozen-contract review)
1. B2 pre-established-isolation extension (§1) — test-first, review. NOTE: the
   frozen-contract bindability review (REVIEW-BLIND-CANCEL-B) is expected to show the
   comparator ALSO needs path-param resolution (the cancel is bodyless → `${field}`
   has no body to resolve) — so the bodyless-GET adaptation is a FAIR, symmetric
   change both oracles get, not a MIST-only affordance.
2. `IstioRouteFaultInjector` (§2) — kubectl apply/delete of the drawback-abort
   VirtualService; test-first, review.
3. cancel→refund triple + the register→create→pay→cancel setup scenario (§3).
4. Deployment decision + TT up in an Istio mesh (kind+Istio, likely stopping SockShop
   to fit 26 GB; or minikube+Istio addon) + apply/verify the drawback-abort
   VirtualService severs `/drawback` while `/account` stays live.
5. Run: MIST B2 pairing verdict + the comparator on the frozen contract, same
   scenarios; record the head-to-head (per-oracle catch/miss + the Rider-2 reporting:
   infra-failure rate, delay-vs-loss stratification).

*Each of §1/§2 is a MIST code change gated behind the standing 3-cold-review. This is
the real remaining engineering for the centerpiece — substantial but bounded. The
frozen-contract 3-cold-review (REVIEW-BLIND-CANCEL-A/B/C, in flight) gates the
interpretation: whether the refund clause is executable by a stateless assertion
oracle or falls to NOT_CHECKABLE is the head-to-head's crux.*
