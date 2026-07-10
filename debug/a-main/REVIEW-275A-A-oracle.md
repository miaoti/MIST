# REVIEW-275A-A — Reviewer A (ORACLE / TOOL-SOUNDNESS lens)

**VERDICT: ACCEPT-WITH-FIXES** — the core idea (bind the read-back behind a transport seam, run PAIRED, FLAG the clean-but-lost write) is sound and matches the reviewed architecture, but two BLOCKING soundness gaps hit the two named cases (OTel isolation key; TeaStore HTML marker), and several MAJOR items rest on factual misreadings of the current code. The cleanest resolution is **OTel-only this wave, and only after the request-derived SQL key is nailed**; defer TeaStore until F3/F4/F13 are resolved.

Scope of verification: I read `DataIntegrityRuntime`, `TargetTripleRegistry`, `DataIntegrityObserveCheck`, `PairedFaultExecutor`, `MstAuthHandler`, `CancelRefundHeadToHead`, `ShippingReadbackHttp`, the two OTel + two TeaStore case JSONs, the OTel capture read-back artifacts + capture spec, and freeze §6 (T9 + bindable-pending-eval rows). Findings below cite line-level code, not the plan's prose.

---

## F1 [MAJOR] — Do NOT introduce a new `ReadbackProbe` interface. The transport seam already exists (`Http` + `installHttpOverride`); reuse it.

The plan (§2.1) proposes extracting a new `ReadbackProbe` interface and refactoring the hardcoded `s.http.getSut(readbackPath)` call sites behind it, then guarding the refactor with "TT/SS suites stay green." This is **unnecessary and strictly riskier than the existing mechanism**:

- `DataIntegrityRuntime.Http` (L175–181) is already the transport seam: `getSut(path) → HttpResponse(status, body)`.
- `installHttpOverride(Http)` (L273–275) already exists **precisely to route the read-back at a non-SUT transport**, and `ShippingReadbackHttp` already does exactly this for the Sock Shop broker-management read-back (a different host + basic auth), with the reviewed poll/gate/verdict loop **completely untouched**.

So the SQL/HTML bindings should be `SqlReadbackProbe implements DataIntegrityRuntime.Http` and `HtmlProfileReadbackProbe implements DataIntegrityRuntime.Http`, each returning a **synthesized JSON collection** (`{"data":[{<key fields>}]}` or `[]`) so `extractItems`/`containsKey`/`probeVerdict` run byte-for-byte unchanged, installed via `installHttpOverride`. `ShippingReadbackHttp` is the proven template — the plan cites `CancelRefundHeadToHead` as the enablement template but misses that `ShippingReadbackHttp` is the actual *transport-seam* precedent already in the tree.

Why this matters for soundness: the plan's own biggest self-identified risk ("does extracting the read-back behind an interface change the verdict semantics the TT/SS results depend on?") **is dissolved** by not doing the extraction. Note also the "suites stay green" guard is insufficient as stated: `getSut` is called at five semantically distinct sites — baseline read (L434), supplied baseline + VALUE_DELTA stability re-read (L499/L523), unacked immediate read (L627), the decisive poll loop (L645), and the post-settle re-read (L686), plus the *non-read-back* `freshStationPair` SUT lookup (L820). Green JSON-suites prove none of the new SQL/HTML behavior and do not exercise the multi-site usage under a new intermediary layer. Reusing `Http` keeps all five sites on the existing, already-tested path.

**Fix:** delete the `ReadbackProbe` design; implement `Sql`/`HtmlProfile` probes as `DataIntegrityRuntime.Http` impls installed via `installHttpOverride`, returning synthesized JSON collections. Constrain each bound triple to `SUPPLIED` isolation so the read-back GET is the triple's *only* `getSut` (the exact scope rule `ShippingReadbackHttp`'s class doc already pins).

---

## F2 [BLOCKING] — OTel's isolation key is the SERVER-ASSIGNED `orderId`, which the oracle is architecturally forbidden from reading. "count>0 ⇒ PRESENT / count==0 ⇒ ABSENT" is underspecified: count of *what*?

This is the central pilot case and the plan does not acknowledge the conflict.

- The capture's read-back is `SELECT count(*) FROM accounting."order" WHERE order_id='<the acked orderId>'`. I verified in both `readback-psql.txt` files and `oteldemo-checkout-flow.yaml` that the key is literally **"orderId from the checkout ack"** — read from the response.
- The oracle's isolation protocol forbids exactly this. Class doc L36–40: *"X is request-derived — never read from the response."* `freshen` (L776) even does `body.remove("id")` so a server-assigned id cannot become the key. `beforeWriteSupplied` establishes the key **before** the write (baseline is captured pre-write); the orderId does not exist until **after** the write acks. There is no sound path by which MIST's oracle can key on the acked orderId.
- Therefore "count>0/count==0" is ambiguous and, in every concrete reading, unsound as written:
  - **Bare `count(*)`** (no WHERE): control-count and fault-count are both >0 (the table is non-empty) ⇒ both PRESENT ⇒ `verdict()` returns NO_FIRE (L481) ⇒ the lost write is **missed**.
  - **`count(*) WHERE order_id=<acked id>`**: requires reading the response — forbidden — and would be a per-run global read anyway.
  - **A global count-delta** (baseline count vs post count) is (a) not membership but VALUE_DELTA, and (b) not isolation-safe: any concurrent order (the load-generator is disabled for this reason, but MIST's own N≥4 consecutive probes and the paired legs are concurrent writers) moves the count independently ⇒ false movement.

The only sound binding is a **request-derived isolation key** that `accounting."order"` actually stores and can be queried on (e.g. the per-leg email marker or the session uuid the stimulus generates *before* checkout), used as `WHERE <that column>=<supplied value>` via a `SUPPLIED` + `MEMBERSHIP` triple. The plan never identifies such a column, and the documented schema evidence only shows `order_id` + `orderitem`. If `accounting."order"` has **no** request-derived queryable column, the OTel read-back is **not soundly bindable as scoped** and the case must stay at the T9 boundary — a dated refutation, not a `mist_bindable` flip (see F9).

**Fix (blocking, before any OTel run):** determine whether `accounting."order"` (or a joinable `orderitem`/shipping row) carries a request-derived, per-leg-unique column. If yes: bind a `SUPPLIED`+`MEMBERSHIP` triple keyed on it, `SqlReadbackProbe` emits `{"data":[{"<col>":"<val>"}]}` for matching rows, and the harness supplies the fresh key via `beforeWriteSupplied` (as `CancelRefundHeadToHead` supplies `userId`). If no: record a dated disclosure, keep `mist_readback_oracle=not_applicable`, do **not** flip `mist_bindable`.

---

## F3 [BLOCKING] — TeaStore HTML marker = the user's `firstname`, which appears in the profile user-info/greeting, not only the Orders table ⇒ a text/CSS match reads PRESENT on both legs ⇒ the masked write is MISSED (false negative).

The case locator is *"GET /profile — the acting user's Orders table (HTML page; the marker firstname identifies the leg's row)."* But `firstname` is the **account's own first name**, rendered in the profile's user-info/edit section regardless of whether any order exists. A naive page-scoped text/CSS match on the firstname string will therefore match the profile header/user-info on **both** legs:

- Fault leg: order row absent, but firstname still present in user-info ⇒ probe reads PRESENT ⇒ `fault.readbackContainedX==true` ⇒ `verdict()` NO_FIRE (L481) ⇒ **lost write missed** (false negative).

The differential structure protects against a *fully* broken locator (a locator that matches nothing makes the **control** leg ABSENT ⇒ `!control.readbackContainedX` ⇒ NOT_EVALUABLE at L471, not a false FLAG — good). But it does **not** protect against this *partially* wrong locator, which reads PRESENT for the wrong reason and silently converts a positive into a miss. This is a genuine false-verdict failure mode the plan's "brittle-by-disclosure locator" hand-wave does not cover.

**Fix:** the HTML extractor must be DOM-scoped to the Orders-table subtree only (parse the specific `<table>`/rows for that section), and/or key on an **order-unique** token (order id, order total+timestamp) rather than the user's own firstname. Until the extractor is scoped and the marker is order-unique, do not bind TeaStore HTML. Given F3+F4+F13, TeaStore should be **deferred to its own wave** (the plan's open Q2 leans this way already).

---

## F4 [MAJOR] — TeaStore per-leg-fresh-user vs `MstAuthHandler.PER_JVM_COOKIE` single-cached-session collision; the read-back must use the harness-owned per-leg session.

`MstAuthHandler` caches exactly **one** session per JVM: `PER_JVM_COOKIE` stores `cachedCookies` for the first login and attaches it to every request (L171–178, L274–280). But the TeaStore capture uses a **fresh user per leg** (control user13, fault user12) and reads the profile in a **fresh session** post-restore. If the read-back goes through `MstAuthHandler`'s cached cookie, it reads the *wrong* user's profile ⇒ marker absent on both ⇒ control NOT_EVALUABLE ⇒ no result. The plan's §3 "reuse the http client + auth" and "auth glue (the cookie-session the capture used)" underestimates this: `CancelRefundHeadToHead` solved the analogous problem by having the harness/stimulus own the per-leg credential (`Order.token`) and NOT relying on the `MstAuthHandler` cache. TeaStore needs the same: the `HtmlProfileReadbackProbe` must carry the *acting leg's* session cookie (re-login as that user), owned by the harness, not the JVM cache.

**Fix:** the TeaStore read-back probe holds the per-leg session (login-as-user in the probe, or inject the leg's cookie jar), independent of `MstAuthHandler`. Another reason to defer TeaStore.

---

## F5 [MAJOR] — SQL/HTML transport failure MUST map to non-2xx, or a broken probe's `count==0`/no-match reads as ABSENT ⇒ false FLAG. This is the error-vs-absence latching guard the new paths need.

The entire decisive-read gate keys on `readback.status/100==2` to separate "usable read" from "error" (L628, L648, L675, L690). The reviewed value-delta path has an explicit analog — the "probe row vanished from a 2xx read ⇒ error, not movement" rule (L657–664, L697–703) — precisely so an unreliable surface is never scored as a data change. The SQL/HTML probes need the same discipline at the transport layer:

- `SqlReadbackProbe`: a failed `kubectl exec`/psql (pod gone, connection refused, auth error, non-numeric output) MUST return a **non-2xx** `HttpResponse` (as `ShippingReadbackHttp` maps IO error → status 0, L76–77). Otherwise a failed exec yields empty/zero output → `containsKey` false → **ABSENT → FLAG** on a leg that actually persisted ⇒ a fabricated defect. On the decisive read this becomes an ERROR record (L675–680) — correct — *only if* the status is non-2xx.
- `HtmlProfileReadbackProbe`: an auth redirect (302 to login), a 5xx, or an unparseable page MUST surface as non-2xx, not as an empty Orders table.

**Fix:** specify and unit-test the failure→non-2xx mapping for both probes (query/exec error, connection failure, auth/redirect, malformed output). This is a named DoD item, not an afterthought.

---

## F6 [MAJOR] — Must run PAIRED, not observe single-leg. Observe-mode on the fault leg structurally cannot report LOST (U8 quarantine + no trace gate). Answers open Q5.

Two independent mechanisms block a single-leg observe fault run from producing the intended verdict:

1. **U8 quarantine:** `DataIntegrityObserveCheck` only reports LOST when `observeTripleHasObservedPresent(tripleName)` is true — i.e. the triple showed at least one `OBSERVED_PRESENT` **in the same session** (L74–94, gate defined at `DataIntegrityRuntime` L341–354). On a fault-only leg (kafka down / maintenance on), no write ever lands, so the triple never has an `OBSERVED_PRESENT` ⇒ the absence is **QUARANTINED**, not reported as a defect. This is exactly the "observe-mode quarantine makes fault-leg runs ill-defined" note already recorded in freeze §6 (traced-wave amendment, 2026-07-10).
2. **Trace gate:** `OBSERVED_COMPLETE_ABSENT` (the tier that can escalate to LOST) requires `traceComplete(...)` (L682) — a Jaeger lookup keyed on the step's traceId with `jaeger.base.url` set. Without it the absence stays `TIMEOUT_ABSENT` ⇒ "persistence UNCONFIRMED, not a defect" (`DataIntegrityObserveCheck` L63–73). TeaStore is trace-uninstrumented, so `TIMEOUT_ABSENT` is the ceiling there regardless.

**PAIRED mode via `PairedFaultExecutor.evaluate(...)` avoids both:** the FIRE rule (L481–489) needs only *fault acked ∧ fault absent ∧ control present*; it does not consult the quarantine or the trace gate. This is precisely how `CancelRefundHeadToHead` gets a clean cell on a timeout-gated read-back. 

**Fix:** run PAIRED (bespoke harness + `installHttpOverride` + `PairedFaultExecutor.evaluate`), matching the g3 template. State this in the plan and drop the observe-single-leg option for these cases.

---

## F7 [MAJOR] — The plan's engineering premise rests on two factual errors about the current code.

- **§0 (L27–29): "the g3 harnesses construct their `Triple`s in Java … there is no config-driven triple loader today."** False. `CancelRefundHeadToHead` (L311–313), `ShippingEnqueueHeadToHead` (L369), and `AccountCreateAgreement` (L81) all load triples via `TargetTripleRegistry.load(Paths.get(...))` from YAML, through a strict, guard-rich validator (`TargetTripleRegistry.parse`, L205–321). A config-driven triple loader **already exists and is already used**. What the harnesses hand-write is the *stimulus/wiring*, not the `Triple`. Consequently the new `readback.transport` discriminator (§2.2) should be a new **validated YAML field on the existing registry**, and open-question Q4 ("bespoke harness vs a *first step toward* a config-driven registry") is a false dichotomy — the registry is not speculative, it is the current substrate. Use it.
- **§2.3: "MIST's SQL read-back uses the SAME psql locator the capture used."** Given F2, MIST *cannot* reuse the `order_id` locator (server-assigned) and must key on a request-derived column. So this sentence is both inaccurate and self-undermining; it should be rewritten (see F12).

**Fix:** correct §0 to reflect the existing loader; add `readback.transport` (+ `sql_locator` / `html_field_locator` + connection descriptor) as validated registry keys mirroring the existing cross-field guards (e.g. the VALUE_DELTA/SUPPLIED guards at L263–305); rewrite §2.3 per F12.

---

## F8 [MAJOR] — The OTel live re-run needs a scale-based injector and control-FIRST ordering; the producer-wedge datum makes leg ordering load-bearing. More harness code than "just bind the transport."

A MEASURED paired result requires re-injecting the fault live (kafka scale 0) around the paired legs, not replaying artifacts (F12). But:

- The registry's fault model is `fault_flag {deployment, property}` driving `SutFlagFaultInjector` (property flags). "Scale kafka to 0" is not a property flag on the write deployment ⇒ needs a bespoke `KafkaScaleInjector` (analogous to the g3 `HttpToggleFaultInjector`/`IstioAmqpSeverInjector`/`RabbitPolicyInjector`), driving `kubectl scale`.
- The case documents a **producer wedge**: a replaced kafka pod (emptyDir, new cluster id) leaves the old checkout producer silently losing acked orders *past* restore. So the run must be **control-first** (kafka up → measure control → scale to 0 → measure fault), and kafka must **not** be restored mid-window; a fault-then-control ordering risks the wedge contaminating the control leg. The plan says "control-leg-first" (good) but does not connect it to the wedge or to the injector gap.

**Fix:** specify the `KafkaScaleInjector`; require control-first with full pod-gone verification before the fault probes; document that recovery (rollout-restart checkout+accounting+fraud) happens only *after* the measured window.

---

## F9 [MAJOR] — The `mist_bindable` flip is legitimate under the convention ONLY when it lands atomically with a real measured run (or a dated refutation). A standalone bool flip is the definitional trick the audit property forbids. Answers open Q3.

Freeze §6 pins two governing rows:
- **T9 row (L301):** boundary cells are *"excluded from MIST recall denominators"* and their FLAG targets are *"PRESERVED in case notes … never tallied as results."*
- **bindable-pending-eval row (L300):** the audit property is explicit — *"verdict-valued mist cells appear ONLY where MIST ran"*; a bindable-but-unrun cell records `not_applicable` with reason `bindable-pending-eval` and *"ENTER[s] the MIST recall denominator AT THE WAVE THAT RUNS THEM."*

So the plan's §2.2 phrasing ("`mist_bindable` flips false→true at THIS commit; the T9 rows … mov[e] into the recall denominator") is correct **only if** the verdict-valued `flag`/`no_flag` cell is produced in the same wave. Flipping the boolean at the seam commit without the run gives you `bindable-pending-eval` (still `not_applicable`) — not a denominator entry. Flipping *and* tallying without a run would violate the audit property. The bar the convention sets is: **a recorded MIST run producing the cell**, not the existence of a binding.

**Fix:** commit the `mist_bindable` flip **atomically with** the measured cell (or, if the run is deferred/split, leave the cell `not_applicable`/`bindable-pending-eval`). If a run REFUTES (F2 no queryable column, F5 probe unreachable, F3 locator unsound), the cell stays `not_applicable` with a dated reason — the wave-3a refutation discipline the plan's §4 already invokes. Amend both the T9 row and the bindable-pending-eval row in §6 to record the two cases' move.

---

## F10 [MINOR] — N≥4 repeats need DISTINCT correlators per repeat or `requireClaimEligible` throws.

`CancelRefundHeadToHead.requireClaimEligible` (L204–214) hard-requires `joinMode=="correlator"` AND `correlatorUnique`. `correlatorUnique` is false if any correlator repeats within a leg (`PairedFaultExecutor.allUnique`, L382–390). One cancel per leg made a per-triple constant unique; **four probes per leg reusing one correlator makes it non-unique ⇒ the claim-eligibility gate throws.**

**Fix:** stamp a distinct correlator per repeat (e.g. `triple.name + "#" + i`) in the harness so the N≥4 legs join uniquely.

---

## F11 [MINOR] — Verify the OTel checkout ack body carries no top-level `status` field that would trip `bodyStatus()` into a false `!acked`.

`acked = httpStatus/100==2 && (bodyStatus==null || bodyStatus==1)` (L614); `bodyStatus` reads a top-level JSON `status` int (L1063–1073). OTel's checkout confirmation JSON (orderId, shippingTrackingId, items, totals…) most likely has no top-level `status`, so `bodyStatus==null` ⇒ `acked=true`. But if it does carry a `status` field ≠ 1, every fault leg is judged `!acked` ⇒ the `!acked` branch (L623–635, NOT_APPLICABLE gate) ⇒ NO_FIRE, and the case silently never fires.

**Fix:** confirm the ack body shape against a captured `sidecar.json`; if a `status` field exists, add it to the case notes and confirm it does not collide with the ack rule.

---

## F12 [MINOR] — The anti-circularity argument is valid, but sharpen the disclosure: MIST must LIVE re-run (fresh writes/keys), never replay the frozen capture artifacts — and MIST's key differs from the capture's `order_id`, which *strengthens* independence.

The label-independence claim (§2.3: labels are authored, not from MIST's output) is correct — the corpus label is authored and never derived from MIST. The residual concern is *observation* independence, and it is satisfied **only** if MIST re-executes the scenario live (re-injects kafka scale-0, issues fresh writes with fresh keys, derives FLAG from its own poll/gate/verdict) rather than re-reading `readback-psql.txt`. The plan's §4 implies a live re-run but the word "captured pair" is ambiguous. Also, per F2, MIST keys on a request-derived column, *not* the capture's `order_id` — so MIST's query is a **different, isolation-sound query**, which makes the independence stronger than "mirrors the capture's own psql query."

**Fix:** rewrite §2.3 to (a) state explicitly "live re-run, fresh keys — never replay of stored artifacts," and (b) drop the "same locator" claim in favor of "MIST binds a request-derived key distinct from the capture's server-assigned order_id."

---

## F13 [MINOR] — If TeaStore instead binds the internal `/rest/orders/user/{id}` JSON (the sound surface), that is a LOCATOR/MODALITY change from the case's authored black-box HTML surface — requires an explicit freeze amendment + disclosure, not a silent swap.

The case explicitly distinguishes the **black-box** read-back (the HTML `/profile` page) from the **runner-level corroboration** (`GET persistence /rest/orders/user/{id}` JSON, "not the black-box surface"). The T9 boundary in §6 (L301) is specifically about the *HTML* modality at the pinned commit. The sound, easily-bindable surface is the internal JSON (its key `{id}` is the request-supplied user id — isolation-clean, and F3/F4 evaporate). But binding MIST to the internal JSON means MIST is **not** testing the user-facing surface the case documents — a weaker/internal record. That may be a perfectly defensible choice, but it is a *modality change* and must be disclosed as such: the HTML T9 boundary stays for the black-box surface, and a new note records that MIST binds the internal JSON. Do not silently retarget the locator and present it as "the TeaStore read-back cell."

**Fix:** decide explicitly (JSON-internal vs scoped-HTML). If JSON-internal, add a dated §6 amendment: MIST binds `/rest/orders/user/{id}` JSON; the HTML profile remains a documented T9 boundary for the black-box surface.

---

## Summary of what must change before execution

- **Blocking (OTel, the pilot):** F2 — identify a request-derived queryable column in `accounting."order"` and key a `SUPPLIED`+`MEMBERSHIP` triple on it; otherwise the OTel read-back stays at the T9 boundary (refutation, not a flip).
- **Blocking (TeaStore):** F3 — DOM-scope the Orders-table extractor and use an order-unique marker (firstname is unsound); combined with F4/F13, defer TeaStore to its own wave.
- **Architecture corrections that also de-risk the plan:** F1 (reuse `Http`+`installHttpOverride`, no new interface), F7 (registry already config-driven; fix §0/§2.3), F6 (PAIRED only), F8 (scale injector + control-first).
- **Guards to specify + unit-test:** F5 (transport-failure→non-2xx = error-not-absence), F10 (unique correlators), F11 (ack-body `status` check).
- **Provenance discipline:** F9 (flip atomic with the measured run/refutation), F12 (live re-run disclosure), F13 (locator/modality change is a freeze amendment).

The plan is executable and the discriminating-signature claim is real, but as written it would (a) attempt an OTel binding that the oracle's own isolation invariant forbids, and (b) ship a TeaStore HTML locator that misses the very write it is meant to catch. Fix F2/F3 (or scope to OTel-only after F2), adopt the existing seam (F1/F7), run PAIRED (F6), and it becomes a sound, minimal, review-clean enablement wave.
