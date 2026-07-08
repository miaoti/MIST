# MIST — Progress Report and Presentation Notes

**Black-box detection of acknowledged-but-lost writes in microservices.**

This is a working report of where the project stands: what the idea is, what we have built and validated, what
we are doing now, and the honest limits. Concrete examples are called out as `> Example` / `> Talking point`
blocks so they can be expanded live. Detailed plans and results live in the docs indexed by `EXECUTION.md`.

---

## Status at a glance

- **Done and reviewed.** The mechanism (Gate 1 — PASS); the fair baseline comparator (Gate 2 — accepted); two
  head-to-head studies — TrainTicket `cancel → refund` (the centerpiece) and Sock Shop shipping (a second
  system); a breadth survey; and false-positive probes on both systems.
- **In progress now.** Turning the breadth survey into an *executable* run on live TrainTicket, and
  consolidating all results into the paper-evidence pack.
- **The open bet.** A fully *wild* (zero-injection) defect would give the strongest form of the claim; the
  current head-to-heads use a realistic injected trigger on a genuine defect, which is disclosed.

---

## 1. The problem — one concrete story

Microservices routinely answer a request with `HTTP 200 "Success"` while silently failing to persist the
underlying change. We call this an **acknowledged-but-lost write**. Conventional test oracles — status code,
schema, response shape — pass these *by construction*, because they only read the response, never the
resulting state. Industry data says the phenomenon is common: ~29% of errors are swallowed (Uber,
SIGMETRICS'25).

> **Example (the opening story).** In TrainTicket, cancelling a paid order should (a) cancel it and (b) refund
> the money. The unmodified source does this:
> ```java
> // ts-cancel-service (upstream, unmodified)
> boolean status = drawbackMoney(money, loginId, headers);   // issue the refund
> if (status) { ...notify... }
> else { LOGGER.error("[Draw Back Money Failed] ..."); }      // refund failed → only logs
> return new Response<>(1, "Success.", "test not null");      // ...and returns SUCCESS anyway
> ```
> The order is cancelled, the money is never refunded, and the client is told `"Success."`. No status/schema
> oracle can see this — the response is flawless.

## 2. What we built — the read-back oracle

The core idea is a **per-run metamorphic check**: *a `2xx` response that acknowledges entity X must have X
observable on its own read-back.* The write is caught **contradicting itself** — no gold output, no
human-authored assertion, no source access, no production traffic. The read-back is a **black-box follow-up
GET** (never database access) at a declared `(write, dependency, read-back)` triple.

> **Example (membership).** MIST `POST`s a new route, gets `{status:1,"Success"}`, then issues
> `GET /adminroute` and checks whether that exact route is in the returned list. Clean run → present.
> Faulted run (persist skipped) → the response still says success, but the route is **absent** → MIST fires.

Some effects are not "present vs. absent" but "a number that should have moved." For those we use a
**value-delta** read-back.

> **Talking point (why "membership" is not enough).** For the refund, pre-fund the buyer to a balance of 50.
> A correct cancel refunds R: `50 → 130`. A lost refund: `50 → 50`. Asking "does the buyer exist on the
> account?" passes either way — the buyer is present before *and* after. **Only the numeric delta separates a
> real refund from a lost one.** This is the observable MIST adds.

Two engineering points worth stating: (i) the oracle waits out benign eventual consistency (poll until the
value stabilizes or the trace shows the write complete, bounded timeout) so it does not cry wolf; (ii) at a
deployment site the oracle runs **standalone per-run** — the control/fault pairing used in the lab is
*scaffolding to manufacture labeled ground truth*, not something a deployed MIST needs.

## 3. Why the comparison is fair — and why the baseline misses

Beating a strawman proves nothing, so we compare against a **competently configured, blind-authored contract
oracle** (Filibuster-style: fault injection + hand-authored per-endpoint assertions). An independent author
wrote success contracts for **all 79 write endpoints across 22 services**, from the upstream source only, and
froze them in git **before** the fault set was revealed.

> **Talking point (the robot referee).** Think of the baseline as a referee that knows exactly six checks:
> the HTTP status, three response-body checks (`status` field, `data` null-ness, `msg` text), one follow-up
> GET ("does my write show up?"), and "can't check this." A blind author translates each contract clause into
> these six. It flags a run if **any** check fails.

This makes the head-to-head decisive and, crucially, *explains* the outcome mechanically.

> **Example (why it misses cancel→refund).** The refund clause is "`balance_after == balance_before + R`."
> That needs *arithmetic on a follow-up read* — and the six checks include no subtraction. So the clause
> becomes `NOT_CHECKABLE`, and the response is a flawless `"Success."`, so the baseline **passes the faulted
> run (misses the bug)**. MIST, which compares the two balances, **fires**. Extending the baseline with a
> subtraction primitive would be re-implementing MIST — which concedes the contribution.

The baseline is not hand-crippled: it must pass a pre-registered **competence floor** (catch the injected
faults) and survived three independent cold reviews. The paradigm is standard — specification/contract-driven
test oracles (Barr et al., *The Oracle Problem in Software Testing*, TSE'15), Filibuster (SoCC'21), and
contract testing (Pact, Dredd).

## 4. What we have done — results

**Gate 1 — the mechanism is sound (TrainTicket).** On a constructed lost-write, MIST **fires** in the
high-confidence stratum, and on 30 benign iterations the false-positive rate is **0 / 2127** acknowledged
writes, with the observation gate fully resolved.

**Gate 2 — the baseline is competent and fair (TrainTicket).** Both calibration faults are flagged via
**genuine state-clause failures** (not transport errors), while **every clean run passes every clause** —
evidence the baseline discriminates rather than rubber-stamps.

**Gate 3 (centerpiece) — TrainTicket `cancel → refund`.** Three scenarios, each run five times, fully
deterministic:

| Scenario | MIST | Baseline | Reading |
|---|---|---|---|
| Natural dependency fault (`{1,"error"}`) | fires | catches (msg gate) | tie — MIST additionally *localizes* the lost write |
| Constructed fabricated clean ack (`{1,"Success."}`) | fires | **misses** | **clean MIST win** — the `50→130` vs `50→50` delta the baseline cannot express |
| Body-carrying create (agreement anchor) | fires | catches | both catch — the baseline is demonstrably non-strawman |

**Gate 3 (external validity) — Sock Shop shipping.** A second system, a different hazard (a lost
message-queue enqueue).

> **Example (self-documented bug).** Sock Shop's shipping service wraps its enqueue in a try/catch that logs
> *"Accepting anyway. Don't do this for real!"* and returns `201` no matter what. When the broker is
> unreachable, the shipment is acknowledged and silently dropped — present in the **unmodified** official
> image.

Four cells (two fault strata × two comparator forms), five runs each, deterministic: **MIST fires on every
fault leg**; the strongest *fair* baseline — extended with a liveness check — **still misses** the constructed
cell, because closing that boundary needs out-of-class broker/queue-state observation (i.e., MIST).

**Breadth.** 69 / 80 (86.25%) of the frozen state clauses are bindable/evaluable on live TrainTicket; the
residual 11 are exactly the object/aggregate/delta class MIST covers. **False positives:** 0 / 2127
(TrainTicket) and 0 / 1200 (Sock Shop).

## 5. What we are careful about — anticipated questions

- **"Did you just invent the bug?"** No. The buggy logic is **genuinely upstream** — the cancel service's
  commit history is entirely upstream authors, and the file carries none of our injection markers (verified
  from git). What we add is the *trigger* (making the refund step fail), in a *different* service, clearly
  labeled. So the defect is real; we inject only a realistic fault to trigger it deterministically. The one
  *constructed* piece — a fabricated clean acknowledgement — is disclosed and is the reason the wild
  (zero-injection) hunt is the higher bar.
- **"Is this really black-box?"** "Black-box" qualifies the **oracle** (judging uses only HTTP + OTel, no
  source, no DB). Fault *injection* is a separate, disclosed grey-box mode — a deployed MIST finds naturally
  occurring defects, it does not inject.
- **"Is the baseline fair?"** Its fairness rests on **construct validity** — the six primitives faithfully
  represent the response/contract-assertion oracle *class* (Pact/Dredd), which cannot express cross-request
  arithmetic. We scope the claim to that class ("the strongest fair single-endpoint response+liveness
  checker misses it"), never "no tool could," and disclose this as a threat to validity.
- **"You didn't compare to Cast directly?"** Cast requires production-traffic replay + Java AOP + historical
  baselines that open-source systems lack; a nominal Cast would be a crippled comparator. We approximate its
  *oracle model* (Filibuster-style) and argue the deltas (generation, black-box, label-free read-back, open
  benchmark) from verified facts.

## 6. What I am doing now, and next

- **Now:** making the breadth number *executable* on live TrainTicket (a control-only run that turns the 69/80
  survey into a measured result), and folding all evidence into the consolidation / paper-evidence pack.
- **Next:** the wild-hunt for a zero-injection defect (the strongest form of the claim), then the write-up.

---

*One-sentence takeaway:* **MIST makes silently-lost writes testable using only the black-box HTTP surface and
the OpenTelemetry a system already emits — no production traffic, no AOP, no human-authored assertions — and a
competently-configured, blind, fair baseline misses exactly the defects whose observable is a state delta,
which is precisely what MIST reads back.**
