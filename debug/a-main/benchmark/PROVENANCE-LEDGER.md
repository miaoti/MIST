# Corpus provenance ledger — case-by-case "is it a real bug / why not" audit

**Created 2026-07-14.** An audit trail that answers, for every one of the 26 cases, the two
questions a reviewer will ask:

- **positives:** *is this a genuine acknowledged-but-lost write, or a fabricated bug?* — and
  precisely **which layer** is natural vs constructed.
- **negatives:** *why is this benign and not a defect?* — and **which naive oracle** it is a
  false-positive trap for.

This is the construct-validity evidence base for the paper. It is derived by re-reading each
case's `fault` + `ground_truth`, and — where source is available in `docs-bundles/` — checking
the claim against the actual source lines. All quantitative claims here are grounded in files,
not asserted.

> **Scope of source verification** (corrected 2026-07-14 after cold review C).
> `docs-bundles/` ships the **unmodified BASE** TrainTicket source (**149** `.java` files) — **not**
> the fork: a grep of the whole bundle for the injection tokens (`fabricatedack`,
> `drawbackFaultMode`, `createAccountFaultMode`, `MIST_FAULT_LOSTWRITE`) returns **0 hits**. So the
> bundle verifies the **norm/baseline derivation** of the 5 TrainTicket positives (what the code
> does with the fault *removed*), but **not** their injected fault mechanism — that lives in the
> fork repo (`train-ticket-injection`), out of bundle, exactly like the 6 TeaStore/OTel/Sock Shop
> positives whose swallow source is at external upstream paths (`src/checkout/main.go`,
> `ShippingController`, `NonBalancedCRUDOperations`, `DatabaseGenerationEndpoint`). Net: **no
> positive is end-to-end verifiable from the bundle alone** — natural masks → upstream repos,
> constructed masks → fork repo; all pinned + public. This split is a disclosed limitation (F4).

---

## 1. Two orthogonal provenance axes (read this first — they are not the same thing)

A recurring source of confusion (including in my own earlier explanations) is conflating **who
produces the deceptive success** with **who pulls the trigger**. The corpus tracks them in two
separate fields:

| Field | Question it answers | Values |
|---|---|---|
| `ground_truth.source` | Who produces the **masking** (the swallow that turns a failure into a clean 2xx)? | `natural` = the SUT's *own unmodified code* swallows; `by_construction` = our fork flag / injected code produces the swallow |
| `fault.provenance_class` | Who supplies the **trigger** (the condition that makes the write fail in the first place)? | `by-docs` = a documented/vendor mechanism or real dependency outage; `by-injection` = we injected the trigger (mesh abort, scale-to-zero, fork flag) |

These are independent. The clearest case is **TeaStore mesh-sever** (`source=natural`,
`provenance_class=by-injection`): the *masking* is the SUT's own `NonBalancedCRUDOperations`
swallow-to-`-1L` (natural), while the *trigger* is an injected Istio 503 (by-injection). A row
that looks self-contradictory at a glance is actually precise once you hold the two axes apart.

---

## 2. The natural↔constructed spectrum (positives are NOT binary)

The single most important correction from this audit: "natural vs constructed" is a **4-tier
spectrum**, not a yes/no. Tiers 1–2 need our code to produce the swallow; tiers 3–4 use the
SUT's own swallow and only inject (or don't even inject) the trigger.

| Tier | What is constructed | Trigger | Cases | Honesty note |
|---|---|---|---|---|
| **T1 — constructed swallow (new code path)** | Our fork adds `if(flag){ skip persist; ack success }` — masking is **injected code**, no pre-existing success-without-persist shape to reuse | fork env flag | TT-adminbasic-contacts, TT-adminroute, **TT-createaccount** (‡) | `source=by_construction`; masking has no natural analogue on this endpoint |
| **T2 — constructed (lights up dead code)** | Fork flag makes an **existing-but-unreachable** success-without-persist path fire (the success *shape* pre-exists; the fork only neutralizes the persist) | fork flag | TT-cancel fabricatedack | `source=by_construction`; the clean-ack-**with-lost-refund combination** is unreachable on the unmodified fork (the success return itself is normal) |
| **T3 — native swallow + injected trigger** | Nothing — the swallow is the SUT's own code; we only inject the **failure** it swallows | mesh abort | TT-cancel `fail`*, TeaStore mesh-sever ×2 | `source=natural` (masking) with `provenance_class=by-injection` (trigger) |
| **T4 — native swallow + real/vendor/self-outage trigger** | Nothing — swallow is native; trigger is a real dependency outage (incl. self-performed scale-to-zero of a real backing store) or a **vendor-shipped** switch | dep outage / scale-0 / vendor flag | OTel kafka, Sock Shop rabbitmq, TeaStore maintenance, **TeaStore dep-down** (†) | strongest "natural"; Sock Shop's source *comment* self-flags the swallow as an anti-pattern |

> \* **TT-cancel `fail`** is a hybrid: its `source` field is `by_construction` (because it still
> needs the fork toggle to make drawback fail), but the **masking** that shapes `{1,"error"}` is
> genuinely native (`CancelController` catch — see §4). Honest borderline between T2 and T3; its
> `source=by_construction` refuses to over-claim "natural."
> ‡ **TT-createaccount moved T2→T1** (cold review B / D1): base `createAccount`
> (`InsidePaymentServiceImpl` L142-155) has its *only* `{1,"Create Account Success"}` return tightly
> coupled **after** `save(...)` inside `if(list.isEmpty())` — there is **no** pre-existing
> success-without-persist shape to "light up," unlike cancel's L92 unconditional return. So its
> fabricated-ack is an injected/relocated branch (T1 semantics), not dead-code reuse (confidence:
> medium — the fork branch is out of bundle, inferred from base control flow).
> † **TeaStore dep-down moved T3→T4** (cold review B / D2): its trigger is
> `dependency_scale_zero` of the real backing DB with `provenance_class=by-docs` — the **same
> mechanism and class** as OTel kafka-scale-0 and Sock Shop rabbitmq-down (both T4). Calling it an
> "injected trigger" (T3) while those are "real outages" (T4) was inconsistent; the `by-docs` field
> governs. (Masking origin still native, unchanged.)

---

## 3. Full 26-case ledger

### 3a. Positives (11)

> **On "src in bundle?"** — ✅ means the **norm/baseline** is verifiable in `docs-bundles/`; the
> **injected fault mechanism** of the 4 constructed positives is **not** in the bundle (it's in the
> fork repo — see Scope note). Response shorthands drop the envelope `data` field (the clean cancel
> ack is literally `{1,"Success.","test not null"}`); immaterial to read-back, noted for precision.

| Case | `source` | trigger (`provenance_class`) | captured? | src in bundle?¹ | response (status/msg) | msg leak? | masking origin |
|---|---|---|---|---|---|---|---|
| TT-adminbasic-contacts-lostwrite | by_construction | fork env flag (by-injection) | yes | ✅ AdminBasicInfoServiceImpl | `{1,"create contacts success",null}` | clean | **injected** (skip persist POST) |
| TT-adminroute-lostwrite | by_construction | fork env flag (by-injection) | yes | ✅ AdminRouteServiceImpl | `{1,"create and modify success",null}` | clean | **injected** (skip persist POST) |
| TT-cancel-refund-fabricatedack | by_construction | fork flag (by-injection) | yes | ✅ CancelServiceImpl | `{1,"Success."}` | clean | lit dead code → line 92 |
| TT-cancel-refund-natural | by_construction | fork flag `fail` (by-injection) | yes | ✅ CancelController:48-50 | `{1,"error"}` | **LEAKS "error"** | **native** controller catch |
| TT-createaccount-agreement | by_construction | fork flag (by-injection) | yes | ✅ InsidePaymentServiceImpl | `{1,"Create Account Success"}` | clean (but body carries `userId` key) | lit dead code |
| oteldemo-checkout-lost | natural | kafka scale-0 (by-docs) | yes (absent in-window + post-restore + post-canary) | ❌ refs `src/checkout/main.go` | full 200 order confirmation | clean | **native** (sendToPostProcessor logs+acks) |
| sockshop-shipping-swallowed-enqueue | natural | rabbitmq down (by-docs) | yes | ❌ refs `ShippingController` | 2xx + shipment object | clean | **native** (catch; comment "Don't do this for real!") |
| teastore-order-depdown-**specified** | natural | teastore-db scale-0 (by-docs) | **NO — specified only** | ❌ refs OrderRepository/NonBalancedCRUDOperations | 201 / `-1L` → confirmed page | clean | **native** (`-1L` chain) |
| teastore-order-maintenance-masked | natural | **vendor** maintenance flag (by-docs) | yes | ❌ refs DatabaseGenerationEndpoint | 302→200 confirmed page | clean | **native** (vendor flag + registryclient) |
| teastore-order-meshsever-masked | natural | Istio 503 on /rest/orders (by-injection) | yes | ❌ refs NonBalancedCRUDOperations | 302→200 confirmed page | clean | **native** `-1L`; trigger injected |
| teastore-orderitems-meshsever-masked | natural | Istio 503 on /rest/orderitems (by-injection) | yes (orders 1388-1392, item count 0) | ❌ refs NonBalancedCRUDOperations | confirmed page | clean | **native** partial-write `-1L`; trigger injected |

### 3b. Negatives (15)

**Clean twins (9)** — 1:1 paired specificity checks (same stimulus, fault OFF); the write LANDS
so every oracle must NOT fire (a fire = false positive). All `source=by_construction` except
where noted; none is contestable.

| Case | twin of | landing evidence |
|---|---|---|
| TT-adminbasic-contacts-control | adminbasic positive | read-back lists submitted accountId+documentNumber |
| TT-adminroute-control | adminroute positive | read-back GET lists submitted route id |
| TT-cancel-refund-clean | cancel positives | balance 50→130 (+80 = calculateRefund on advance cancel) |
| TT-createaccount-clean | createaccount positive | user appears on /account at 60.00 |
| oteldemo-checkout-control | oteldemo positive | accounting PG row present + consumer/INSERT spans in trace |
| sockshop-shipping-control | sockshop positive | queue-master consumer span present |
| teastore-order-control | maintenance positive | marker row in profile Orders + persistence REST |
| teastore-order-meshsever-control | meshsever positive | marker M2C1 present (+ 4/4 sidecars-on healthy probes) |
| teastore-orderitems-meshsever-control | orderitems positive | order 1386 present with 1 item (+ 4/4 healthy probes) |

**Benign traps (6)** — surface phenomenon *resembles* acked-but-lost, but the behaviour is
correct-by-design; each is a false-positive trap for a specific naive oracle.

| Case | `source` | why benign (norm) | FP trap for |
|---|---|---|---|
| TT-contacts-dedupe-benign | natural | 2xx but envelope **`status:0`** ("Contacts already exists"); by-design persists nothing new | naive "2xx + no read-back delta" oracle; correct rule excludes via `status:0` |
| TT-contacts-noop-modify-benign | natural | PUT acks `{1,"Modify success"}` but state **legitimately** unchanged (submitted == current, same-with-same overwrite) | any delta-expecting oracle (2xx + zero durable delta) |
| bookinfo-ratings-benign | natural | reviews catches failed ratings; productpage 200 "Ratings unavailable" — developer-intended (istio/istio PR #15489) | naive "error span under a 2xx" oracle |
| oteldemo-checkout-eventual-benign-001 | natural | row absent @~27s, **present @~328s** (landed after cap) | naive at-cap-only read-back; S3 CONFIRMED (T+5min re-probe) correctly abstains |
| oteldemo-checkout-eventual-benign-002 | by_construction | induced (scale accounting 0→1, drained backlog); absent @~30.4s, present @~58.2s | naive at-cap comparator; PAIRED-MIST-without-re-probe would also FP — why the re-probe gate exists |
| oteldemo-checkout-eventual-benign-003 | by_construction | induced replicate; absent @~29.3s, present @~58.8s | same as -002 |

---

## 4. Flagship worked example (for the paper/talk): TT cancel→refund

This one endpoint carries the natural-vs-constructed distinction cleanly, and its full causal
chain is verifiable to source lines **in this repo**
(`docs-bundles/trainticket/ts-cancel-service/...`, unmodified Fudan TrainTicket). Three source
sites matter:

**(a) `CancelServiceImpl.cancelOrder` — the "return success regardless" tail (lines 64–92):**
```java
64:  boolean status = drawbackMoney(money, loginId, headers);   // refund
65:  if (status) { ... send notification ... }
89:  } else {
90:      LOGGER.error("Draw Back Money Failed");   // failure only LOGGED
91:  }
92:  return new Response<>(1, "Success.", "test not null");   // OUTSIDE if/else — unconditional status=1
```

**(b) `CancelServiceImpl.drawbackMoney` — collapses the refund result to a boolean (lines 278–291):**
```java
289:  Response result = re.getBody();
291:  return result.getStatus() == 1;   // only reads status; does NOT propagate msg
```

**(c) `CancelController` — the native catch-all that shapes `{1,"error"}` (lines 48–50):**
```java
48:  } catch (Exception e) {
49:      CancelController.LOGGER.error(e.getMessage());
50:      return ok(new Response<>(1, "error", null));   // ok()=HTTP 200, status=1 SUCCESS, msg literal "error"
```

**The two fault modes take two different paths** (this is the crux — they do *not* both reach
line 92):

| mode | drawback does | reaches line 92? | response shaped by | response |
|---|---|---|---|---|
| **`fail`** ("natural" cell) | **throws HTTP 500** | **NO** — exception escapes `drawbackMoney`, escapes `cancelOrder` before line 92 | `CancelController` catch (c) | `{1,"error"}` |
| **`fabricatedack`** ("constructed" cell) | returns fake `{status:1}` **without persisting** (no throw) | **YES** — `drawbackMoney` returns true → `if(status)` → line 92 | `cancelOrder` line 92 (a) | `{1,"Success."}` |

**Why each is what it is:**
- The `{1,"error"}` is produced by **unmodified** code (`CancelController` catch): the system
  reports a refund failure as machine-readable **`status=1` (success)**, leaking a human-only
  "error" in `msg`. Because `status` is the machine contract (`status==1` ⟺ success, verifiable:
  `drawbackMoney` line 291, `cancelOrder` lines 49/59, inside-payment 252/255), this is a genuine
  acked-but-lost — **not** honest error handling (which would return `status=0`). But the `msg`
  leak makes it an *imperfect* mask → the frozen comparator's `MSG_CONTAINS` primitive also
  catches it → **G3 tie**, not a clean win.
- The `{1,"Success."}` (fabricatedack) return at line 92 is the **normal** success path — what is
  unreachable on the unmodified fork is the **clean-ack-with-lost-refund *combination***: drawBack's
  `status=0` return is dead (`findByUserId` returns an empty-not-null List, so its `!=null` guard is
  always true → it returns `{1}` only *after* persisting), so a clean ack with an unpersisted refund
  genuinely requires the disclosed constructed fabricated-ack. It is the *only* one of the two with a
  fully clean ack → only the read-back (balance 50→50 vs the control's 50→130) catches it → **B2
  clean win**.

Full independent source re-derivation: `../../g3-comparator-tt/g3-natural-faithfulness-source-check.md`
(verified 2026-07-14 to attribute the catch correctly to `CancelController`).

---

## 5. The positive/benign decision boundary (OTel checkout — same endpoint, both labels)

The corpus's sharpest adjudication pair lives on **one endpoint under one mechanism**
(OTel checkout → kafka), separated only by **permanence**:

- **positive** (`oteldemo-checkout-lost`): kafka is **down**, so the message *never exists*
  anywhere durable → the accounting row is absent in-window, after broker restore, and after a
  later canary proves the pipeline healed. **Permanent** loss.
- **benign** (`oteldemo-checkout-eventual-*`): the consumer/backlog is merely **slow**, so the
  message buffers and **lands late** (absent at ~27–30s, present at ~58–328s). **Delayed**, not
  lost.

This pair is the corpus's operational definition of the label boundary, and it is exactly why the
detector needs a **T+5min re-probe** (S3 CONFIRMED) rather than an at-cap-only read: an at-cap
comparator false-fires on the benigns. It also grounds the disclosed MIST semantics —
OBSERVE-mode `TIMEOUT_ABSENT` is WARN-only (`DataIntegrityObserveCheck` L58-73), the
`OBSERVED_COMPLETE_ABSENT` defect tier needs a Jaeger trace-complete
(`DataIntegrityRuntime` L736-773).

---

## 6. Findings / clarifications discovered in this audit (2026-07-14)

| # | Finding | Status / recommendation |
|---|---|---|
| **F1** | `TT-cancel-refund-natural-001` rationale says *"cancelOrder catches and STILL acks"* — but the catch is in **`CancelController` (L48-50)**, not `cancelOrder` (which has no try/catch around `drawbackMoney`; the exception escapes it). | **Rationale mis-attribution.** The corpus is frozen (`c2-freeze`), so this is logged here rather than silently edited. `g3-natural-faithfulness-source-check.md` is already correct. **Owed: user decides whether to re-issue the case rationale.** |
| **F2** | The filename `...-natural-001` reads as if the case were natural, but its `source` is `by_construction`. | Already handled honestly: `source` field refuses "natural," and G3 doc defines the internal "natural" as "the fork's own compensation-failure path, triggered by a dependency fault — not zero-injection." No change needed; noted so the paper never quotes the filename as provenance. |
| **F3** | `teastore-order-depdown-specified-001` is **SPECIFIED, not CAPTURED** — the only positive never actually observed; its label is inferred from the two captured twins on the same site (db has no PVC, so a scale-cycle wipes the absence evidence). | Evidence-strength outlier. Must be reported as specified (weaker than captured) in any recall table; do **not** pool it with captured positives without disclosure. |
| **F4** | Source-verifiability split (corrected, cold review C): the bundle holds the **unmodified base** (149 `.java`), so it verifies the **norm** of the 5 TT positives but **not** their injected mechanism (grep for injection tokens = 0 hits — the fork branches are out of bundle), same as the 6 TeaStore/OTel/SockShop positives whose swallow source is external. **No positive is end-to-end bundle-verifiable.** | Disclosed limitation; all upstreams pinned + public. Scope note corrected 2026-07-14. |
| **F5** | Only **one** positive leaks a *within-leg* response tell (`TT-cancel fail` → `msg="error"`) → sole G3 "tie". Precision note (cold review B): adminbasic/adminroute carry disclosed **cross-leg** ack-text differentials ("create contacts success" vs base "Create Success"; "create and modify success" vs base "Save and Modify success") — paired-differential tells, not within-leg, so F5 stands, but the fork acks are **not** byte-identical to the clean twin. | Not a defect; state precisely so no one assumes byte-identical acks. |
| **F6** | Provenance is a 4-tier spectrum (§2), not binary natural/constructed. | Adopt the tier vocabulary in the paper to pre-empt the "fabricated bug" objection precisely. |
| **F7** | positive/benign boundary is defined by **permanence** on the same OTel endpoint (§5). | Use this pair as the paper's operational label definition; it doubles as the motivation for the re-probe gate. |
| **F8** | `source` (masking origin) and `provenance_class` (trigger origin) are orthogonal (§1); mesh-sever rows are `natural`+`by-injection` and only look contradictory if the axes are conflated. Caveat (cold review B): the §1 one-liner "`source`=who produces the masking" is **not literal** for `TT-cancel fail`, whose masking is native yet `source=by_construction` — there `source` tracks *instance construction* (needs a toggle), and the T3 footnote does the real work. The **constructed+by-docs** quadrant is empty. | Define both axes explicitly; state the toggle-instance nuance. |
| **F9** (owed) | **No labeling-process / inter-rater disclosure** — the corpus is "human-labeled" but `adjudication` is `null` on all 26; no rater count / κ. A reviewer's #1 demand for a benchmark. | **Owed to C3 rater study** (materials ready, blocked on IRB). Not fabricatable here; disclose as pending. |
| **F10** (owed) | **Measured vs pre-registered oracle cells not summarized** — many `oracle_expectation` cells are DESIGN TARGETs (e.g. the trace-shape oracle was never run on any case); computing precision/recall off the table silently mixes measured + aspirational. | Add a corpus-level measured/aspirational count before any recall table. |
| **F11** | **Headline "11 positives" folds in 1 never-captured case** (F3). | Re-state as **"10 captured + 1 specified"** in the paper headline. |
| **F12** | **Site diversity < case count** — 11 positive *cases* collapse to **~8 distinct sites** (TeaStore maintenance/mesh/depdown are the *same* order-confirm `-1L` site under 3 triggers). | State site-count in the headline; a reviewer discounts by site, not case. |
| **F13** | **Permanence threshold has no external anchor** — the 5-min re-probe cap on the OTel async path has no SLA/vendor citation and no sensitivity analysis; the boundary is enforced by the harness re-probe *gate*, not intrinsic oracle discrimination (PAIRED-MIST-without-re-probe would also FP). | Disclose the cap as a free parameter; add a sensitivity sweep if claiming the boundary. Don't frame "MIST correctly abstains" as an intrinsic oracle property. |
| **F14** | **Tiny n** — one scenario per case, no CI / cross-seed; §7 makes precision/recall statements with no sample-size disclosure. | Add n and a variance caveat before any quantitative claim. |

---

## 7. What this means for the paper (construct validity stance)

- The positives are **not fabricated bugs**; they are **controlled proxies for a real fault
  class** (acknowledged-but-lost writes), standard mutation-/fault-injection methodology
  (Just et al. FSE 2014; Defects4J; chaos/fault injection).
- The **natural tier (T4)** carries the strongest evidence: the swallow is the SUT's own
  unmodified code — in Sock Shop the source *comment itself* flags it as an anti-pattern
  ("Don't do this for real!"), and in TeaStore the trigger is a **vendor-shipped** maintenance
  switch whose own javadoc documents refusal (503) as the intended degraded mode.
- The **constructed tier (T1–T2)** exists because the natural, clean-ack, response-zero-leak
  instance is scarce (S3: 0 confirmed / 1514 acked writes) — so we **measure** that scarcity and
  disclose it, rather than claim natural ubiquity. The constructed cases are the ground-truth-
  certain recall calibration points; the wild-scarcity result is reported as a first-class finding.
- Every **benign** is a false-positive trap for a *named* naive oracle, so the corpus scores
  **precision**, not just recall — the discriminating value of the read-back oracle is exactly its
  behaviour on the "clean ack, permanently lost" column that the naive/contract/trace oracles miss,
  while abstaining on the eventual-consistency benigns that make trigger-happy oracles false-fire.

---

## 8. Cold-review reconciliation (2026-07-14)

Three independent cold reviewers (A source-facts · B provenance-judgment · C construct-validity
challenge; all opus, read-only) re-verified this ledger against source. Outcome:

- **Confirmed unanimously:** F1 (catch is `CancelController`, not `cancelOrder` — A + B both verified
  from source; §4 worked example is line-accurate), F3 (`teastore-depdown` is genuinely
  SPECIFIED-not-captured), the source claims underpinning §4, the machine `status==1`⟺success
  contract (`Response.java` javadoc "1 true, 0 false"), and — reached **independently** by B before
  reading the ledger — the **4 injected / 7 native** masking split (exact match). No positive
  over-claims naturalness in its `source` field. C's overall verdict: **accept-with-disclosures**
  (the construct-validity reasoning is strong and self-critical; the issues are disclosure/re-scope,
  not dishonesty).
- **Corrections applied above:** the Scope-note falsifiable error (94→149 files; "fork source"→
  unmodified base; injected code out-of-bundle; F4 extended); the "line 92 is dead code" phrasing
  (→ the clean-ack-with-lost-refund *combination* is unreachable; §2 T2 + §4); TeaStore dep-down
  T3→T4 (B/D2 — its `by-docs` trigger matches OTel/SockShop); TeaStore createaccount T2→T1 (B/D1 —
  no pre-existing shape to light up; medium confidence); F5 precision (cross-leg ack-text tells);
  §1 toggle-instance nuance (F8); the response-shorthand `data` field (§3a note).
- **New owed items (C, folded into F9–F14):** labeling-process/κ disclosure (→ C3), a measured-vs-
  aspirational oracle-cell summary, a "10 captured + 1 specified" headline, a ~8-site headline, a
  permanence-threshold sensitivity note, and a tiny-n caveat.
- **One residual for the corroborating doc (cold review A):** `g3-natural-faithfulness-source-check.md`
  cites `drawBack` at "(280-290)" / the `{0}` return at "line 289" — the substance is right but those
  line numbers are from a different revision (bundle: `drawBack` L244-257, `{0}` L255). This ledger's
  own line refs all match the bundle; the drift is confined to that older doc. Owed: a line-number
  refresh of that doc (non-urgent).

*This ledger is the single source of truth for per-case provenance; keep it in sync with
`cases/*.json` and `c2c3/c2-freeze.md` on any corpus change.*
