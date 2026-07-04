# G3 cancel→refund head-to-head — live results

MIST's B2 differential value-delta oracle vs the FROZEN blind response-assertion comparator
(`blind-cancel-refund-contract.yaml` @ 38e7aa6 → executable `assertion-bindings-cancel-refund.yaml`),
both observing the SAME cancel→refund stimulus under two fault strata. Runner:
`io.mist.cli.g3.CancelRefundHeadToHead` + `TrainTicketStimulus`. Design:
`prep/g3-headtohead-run-architecture.md`. Faithfulness + ground truth:
`g3-natural-faithfulness-source-check.md`, `g3-value-delta-ground-truth.md`.

Status: **PRELIMINARY** — a first ≥3-cold-review (A/B/C) returned ACCEPT-WITH-FIXES and its fixes
are folded in below; a re-review gates these numbers before they feed any claim.

## The three cells (N=5 stable each, `runs/*.log`)

| cell | write / fault → response | MIST B2 | Comparator (frozen contract) |
|---|---|---|---|
| **natural** | bodyless cancel; drawBack throws → `{1,"error"}` | **FIRE** | **CAUGHT** (msg gate) |
| **constructed** | bodyless cancel; fabricated-ack → `{1,"Success."}` | **FIRE** | **MISSED** (clean win) |
| **agreement** | body-carrying createAccount; fabricated-ack → `{1,"Create Account Success"}` | **FIRE** | **CAUGHT** (STATE_GET binds) |

The three cells together are the argument: in every cell where the comparator's primitives BIND it
CATCHES (natural via the response msg gate; agreement via a STATE_GET on the body-carrying create),
and it MISSES only the constructed clean win — a bodyless write whose sole observable is a numeric
delta. So MIST's win is attributable to the value-delta capability, not to a rigged baseline. (This
is a three-cell existence demonstration on this scenario; frequency claims belong to the breadth /
Rider-2 bindability round.)

```
=== stratum: natural ===
  MIST B2 (differential value-delta): FIRE   (acked-but-lost: acked http 200/status 1, X absent, control's X persisted)
  Comparator (frozen response contract): control flagged=false, fault flagged=true  -> CAUGHT
  value-delta probe (buyer /account balance):  control baseline=50.00 -> final=130.00 ;  fault baseline=50.00 -> final=50.00
=== stratum: constructed ===
  MIST B2 (differential value-delta): FIRE   (same shape)
  Comparator (frozen response contract): control flagged=false, fault flagged=false  -> MISSED
  value-delta probe (buyer /account balance):  control baseline=50.00 -> final=130.00 ;  fault baseline=50.00 -> final=50.00
```

Every run: natural = FIRE + CAUGHT, constructed = FIRE + MISSED, control never flagged, ~24 s each
(no restarts) → deterministic, not a routing coin-flip. Each buyer is **PRE-FUNDED** to a non-zero
`/account` balance (50.00) before the cancel, so the value-delta is a real arithmetic delta
(control 50→130, fault 50→50), not an appear-vs-absent membership signal.

Log bookkeeping: the probe-line evidence set is `prefunded-run2.log` + `prefunded-reps.txt` reps 2–5
(five probe-carrying runs); `prefunded-run1.log` is an additional earlier pre-funded run whose
harness predated the probe printing (same verdicts). `prefunded-run3-v105.log` +
`agreement-run2-v105.log` re-verify all three cells on the final `:1.0.5` image with the
claim-eligibility line (`joinMode=correlator, correlatorUnique=true`) printed per cell.

## Why the constructed miss is un-contestable (fairness of the clean win)

The obvious skeptic attack is "a contract tool could just `STATE_GET /account` and check the buyer
is present, so the miss is unfair." It fails on the merits, and all three grounds are on the page:

1. **Presence ≠ refund; membership is INSUFFICIENT.** With the pre-funded buyer, the buyer is
   PRESENT in `/account` both BEFORE and AFTER the cancel (baseline 50.00). A membership `STATE_GET`
   (does the buyer appear) therefore PASSES on the control AND the fault leg → it cannot catch the
   lost refund. Only the numeric delta distinguishes them: control 50→130 (+R), fault 50→50. (An
   earlier fresh-zero-buyer config made MIST's own value-delta degenerate to appearance, which the
   comparator *could* match — review A/B; the pre-fund closes that, and the run's probe line proves
   the delta is arithmetic.)
2. **The blocker is a missing primitive, not auth or field-naming.** The comparator's client DOES
   carry a JWT (`RestAssuredSutClient.applyAuth`) and `/account` is readable — auth is not the
   blocker (the frozen bindings' earlier "no auth-token" reason was factually wrong and has been
   corrected; verdict unchanged). The decisive ground is that the closed primitive set has **no
   snapshot / delta / arithmetic primitive**, so it cannot express `balance_after == balance_before
   + R`. `/money` returns `data:null` even on success and `/account` is a summed aggregate, so no
   single response field carries the refund either.
3. **A baseline that could catch it would be MIST.** A comparator extended with snapshot + delta +
   arithmetic is re-implementing MIST's differential value oracle — i.e. conceding the contribution.

Scope note: "the comparator" here is the class of **response / contract-assertion oracles**
(schema/Pact/response-shape checks + follow-up existence/`STATE_GET` membership). The claim is that
this class cannot catch a numeric-delta lost write, not that no conceivable oracle can.

## MIST is complementary to the baseline, not a strict superset

MIST fires only on **acked** writes (`acked = 2xx && (bodyStatus==null || ==1)`). A loud `status:0`
failure would make MIST NO_FIRE ("base relation vacuous") while the comparator's `ENVELOPE_STATUS==1`
gate still catches it. So MIST does not dominate the baseline everywhere; it targets **silent/acked**
data loss, complementary to response assertions on loud failures. The natural cell is a **tie**
precisely because the fault kept `status:1` and corrupted only `msg` — the comparator's msg gate
catches that, and MIST independently catches the state loss.

## Natural cell — what makes it "natural" (faithfulness)

The `{1,"error"}` is produced by UNMODIFIED cancel-service code: `cancelFromOrder` flips the order to
CANCEL *before* `drawbackMoney` is called, and when drawback throws (HTTP 500) the exception
propagates out of `cancelOrder` to `CancelController`'s genuine `catch` → HTTP-200 `{1,"error"}`
(`CancelController.java:45-51`). Only drawBack's throw is injected; the response shaping is the
fork's own. So "natural" means "the fork's own compensation-failure path, triggered by a dependency
fault," not "occurs with no injection." The clean `{1,"Success."}`+lost path is **dead code** on the
unmodified fork (drawBack's `{0}` return is unreachable — `findByUserId` returns an empty-not-null
`List`), so a clean-ack lost refund genuinely requires the DISCLOSED constructed fabricated-ack. Full
source derivation: `g3-natural-faithfulness-source-check.md`. MIST's edge on this tie is diagnostic,
not detection: it names the specific acked-but-lost write (the cancel) and the missing observable
(the refund balance-delta), where the comparator only reports a wrong `msg` — but MIST is black-box
on cancel + `/account` and does NOT attribute the fault to the inside-payment hop (effect
localization, not fault localization).

## Fault mechanism — runtime in-memory toggle (and why two earlier mechanisms failed)

`HttpToggleFaultInjector`: a fork endpoint
`GET …/inside_payment/test/faultmode/{none|fail|fabricatedack}` flips an in-memory `volatile` mode on
inside-payment. **No pod restart**, so ts-cancel-service's pooled connection + Ribbon routing to the
single stable pod stay valid → the per-leg toggle is reliable + instant. `fail` = throw → 500 →
`{1,"error"}`; `fabricatedack` = exact success envelope without the persist → clean `{1,"Success."}`.
The route is gateway-guarded, so the toggle carries a reader JWT.

Two restart/mesh mechanisms were tried and **rejected** — a real methodological finding, because both
failure modes are about the SUT caller's client-side caching, not MIST:

1. **EnvoyFilter mesh abort.** A stably-applied filter aborts `/drawback`, but the per-leg toggle
   races ts-cancel-service's **pooled** connection: the convergence probe (fresh connection) sees the
   new Envoy config before the reused write-path connection. Proxy-log ground truth: a control-leg
   drawback returned 418 (abort live) while the probe 0.36 s earlier returned 403 (abort gone) →
   the leg observes the wrong filter state → false NO_FIRE / NOT_EVALUABLE.
2. **JAVA_TOOL_OPTIONS `-D` flag rollout** (`SutFlagFaultInjector`). The rollout RESTARTS
   inside-payment; the caller's stale pool / Ribbon cache then races — short settle → wrong-flag
   round-robin (old pod up); long settle → dead-IP read-timeout hang (old pod gone). Also gated on
   the nacos ipDeleteTimeout (~30 s) + Ribbon refresh (~30 s).

## SUT / deployment

- kind cluster `mist`, ns `trainticket`, upstream `codewisdom/*:1.0.0` graph; `ts-inside-payment-service`
  fork-built (branch MIST-trainticket), **replicas=1**, sidecar-free. Reached via
  `kubectl port-forward svc/ts-gateway-service 18888`.
- **Image tags (run provenance):** the two cancel cells' original N=5 ran on `:1.0.4` (the drawback
  runtime toggle, fork `ea4d60af`); the agreement cell ran on `:1.0.5` (= 1.0.4 + the createAccount
  fabricated-ack, fork `d4679bd5` — the drawback code path is identical between the two tags). All
  THREE cells were then re-verified on the uniform final `:1.0.5` with the claim-eligibility guard
  live (`runs/prefunded-run3-v105.log`, `runs/agreement-run2-v105.log`) — same verdicts.

## Soundness scope / disclosures (from review A/B/C — none is a false-FIRE path)

- **Isolation is a runbook rule, not machine-enforced.** VALUE_DELTA hardcodes `baselineHasX=false`,
  so the executor's isolation tripwire cannot fire; isolation relies on the harness registering a
  FRESH pre-funded buyer per leg. The pre-write baseline **stability double-read** catches a
  still-settling baseline (→ error, not FIRE).
- **The pre-fund itself IS machine-gated** (round-2 review B): the stimulus asserts the addMoney
  envelope is a real `{status:1}` (a soft-failed 200-`{0}` would silently regress the cell to the
  contestable membership shape), and the runner aborts the cell unless both legs' parsed baselines
  are the SAME POSITIVE number (`requirePreFundedBaselines`) — the clean-win label can no longer be
  printed for a degenerate configuration.
- **Traceless timeout-gate.** The sidecar-free cancel is traceless → the fault-leg absence is
  `TIMEOUT_ABSENT`, not `OBSERVED_COMPLETE_ABSENT`. This is a confidence-label limitation, not a
  false-FIRE: the control leg observes the refund on ~the first poll, the injected loss is permanent,
  and the source has no async refund path, so the 10 s cap cannot hide a slow-but-real refund.
- **replicas=1.** The toggle sets a volatile on one pod; >1 could miss on a fault leg → NO_FIRE
  (fault X present), never a false FIRE. N=5 100% FIRE confirms a single stable instance.
- **Same nonzero R both legs.** The stimulus creates identical PAID far-future orders; a zero-R
  control degrades to NOT_EVALUABLE, never FIRE.

## Cell: AGREEMENT anchor (body-carrying create, both catch) — DONE

Review B asked for a body-carrying write, beside the two core cells, where the comparator's
`STATE_GET` clause BINDS and catches (both oracles catch) — so the comparator is demonstrably able
to catch a lost write when its primitives suffice, not defined to lose on cancel→refund. Built as
`AccountCreateAgreement` on the SAME service (ts-inside-payment-service): the write is
`POST /inside_payment/account {userId, money}` — a body-carrying create — with a runtime fabricated-ack
fault (createAccountFaultMode) that acks `{1,"Create Account Success"}` without persisting. Read-back
is MEMBERSHIP (the submitted userId appears in `/account` iff the create persisted).

```
=== stratum: agreement (body-carrying create) ===
  MIST B2 (membership): FIRE   (fault acked http 200/status 1, X absent, control's X persisted)
  Comparator (STATE_GET binds): control flagged=false, fault flagged=true  -> CAUGHT
  => AGREEMENT (both catch — comparator is no strawman)
```

Because the createAccount body carries `userId` (the `/account` key), the frozen comparator's
`STATE_GET(contains-submitted-fields, userId)` binds and CATCHES the lost create; MIST catches it via
membership. N=5 stable (`runs/agreement-*.log`). This is the direct anti-strawman evidence: the
comparator's STATE machinery works in the head-to-head — it misses the cancel→refund clean win
specifically because that write is bodyless and the observable is a numeric delta (no bindable field,
no arithmetic primitive), not because the comparator was defined to lose. Contract:
`assertion-bindings-account-create.yaml`; triple: `target-triples-agreement.yaml`.

