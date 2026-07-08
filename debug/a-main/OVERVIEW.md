# MIST — Progress Report (B1/B2 mechanism, G1/G2/G3 gates)

**Black-box detection of acknowledged-but-lost writes in microservices.**

A working report in our own codenames (B1, B2, G1–G3). It says what each codename means, what has been done,
what is in progress, and the honest limits. Concrete examples are called out as `> Example` /
`> Talking point` for the talk. Detailed plans/results are indexed by `EXECUTION.md`.

---

## 0. Codenames and status (read first)

| Code | Name | One line |
|---|---|---|
| **B1** | Fault-injection mode | Run a write once clean, once faulted. **Lab scaffolding** to manufacture labeled ground truth — *not* the contribution. |
| **B2** | Differential read-back oracle | **The contribution.** After a write, issue a black-box GET and check the acknowledged entity is actually there; **fire** if not. |
| **G1** | Gate 1 — mechanism soundness | Does B2 fire on a real lost write *and* not cry wolf on benign writes? |
| **G2** | Gate 2 — fair comparator | Stand up a blind, competent assertion baseline worth beating. |
| **G3** | Gate 3 — head-to-head | A **real** defect B2 catches that the G2 baseline misses because no human wrote the assertion, on **≥2 systems**. |

Key terms: **acknowledged-but-lost write** = the server answers `200 "Success"` but the state change never
persisted. **read-back** = a follow-up GET that re-reads the state. **fire** = the oracle flags a problem.

**Status.**
- **Done + reviewed:** G1 (PASS), G2 (accepted), the two G3 head-to-heads (TrainTicket `cancel→refund` and
  Sock Shop shipping), the breadth survey, the false-positive probes.
- **In progress now:** making the breadth number *executable* on live TrainTicket + consolidating the
  paper-evidence pack.
- **Open bet:** a fully *wild* (zero-injection) defect — the strongest form of the claim.

---

## 1. The problem — one concrete story

Microservices routinely answer `HTTP 200 "Success"` while silently failing to persist the change — an
**acknowledged-but-lost write**. Status/schema oracles pass these *by construction* (they read only the
response). Industry data: ~29% of errors are swallowed (Uber, SIGMETRICS'25).

> **Example (opening story).** In TrainTicket, cancelling a paid order should cancel it **and** refund the
> money. The unmodified source does:
> ```java
> // ts-cancel-service (upstream, unmodified)
> boolean status = drawbackMoney(money, loginId, headers);   // issue the refund
> if (status) { ...notify... }
> else { LOGGER.error("[Draw Back Money Failed] ..."); }      // refund failed → only logs
> return new Response<>(1, "Success.", "test not null");      // ...and returns SUCCESS anyway
> ```
> Order cancelled, money never refunded, client told `"Success."`. No status/schema oracle can see it.

---

## 2. The mechanism — B1 and B2

### B2 — the differential read-back oracle (the contribution)

B2 is a **per-run metamorphic check**: *a `2xx` response that acknowledges entity X must have X observable on
its own read-back.* The write is caught **contradicting itself** — no gold output, no human assertion, no
source access, no production traffic. The read-back is a **black-box follow-up GET** (never DB access) at a
declared `(write, dependency, read-back)` triple.

> **Example (membership mode).** B2 `POST`s a new route, gets `{status:1,"Success"}`, then `GET /adminroute`
> and checks that route is in the list. Clean → present. Faulted (persist skipped) → response still says
> success, route **absent** → **B2 fires**.

> **Talking point (value-delta mode — why "membership" isn't enough).** For the refund, pre-fund the buyer to
> a balance of 50. Correct cancel refunds R: `50 → 130`. Lost refund: `50 → 50`. "Does the buyer exist?"
> passes either way — present before *and* after. **Only the numeric delta separates a real refund from a
> lost one.** That is the observable B2 adds.

Two properties worth stating: (i) B2 waits out benign eventual consistency (poll until stable / trace shows
the write complete, bounded timeout) so it does not cry wolf; (ii) at a deployment site B2 runs **standalone
per-run** — the B1 pairing below is lab scaffolding, not something a deployed MIST needs.

### B1 — fault-injection mode (scaffolding, not the contribution)

B1 runs a write **clean (control)** then **faulted (fault)** to manufacture *labeled* ground truth, so we can
prove B2 fires on a known loss. It is disclosed as an opt-in grey-box mode. Injection is done two ways: a
SUT-side flag (source fork, disclosed) for clean labeled positives, and infrastructure faults (service-mesh /
message-broker policies) for the unmodified-system path. **A deployed MIST finds naturally occurring defects —
it does not inject.**

---

## 3. The evaluation — G1, G2, G3

### G1 — is the mechanism sound? → **PASS**

Question: does B2 fire on a constructed lost write, and stay quiet on benign writes? Result on TrainTicket:
B2 **fires** in the high-confidence stratum, and the false-positive rate over 30 benign iterations is
**0 / 2127** acknowledged writes, observation gate fully resolved. (G1 proves *soundness* only — zero novelty
by itself; that is what G2/G3 carry.)

### G2 — is the baseline fair and competent? → **accepted**

To make "B2 catches what assertion oracles miss" credible, G2 stands up a **competently configured,
blind-authored contract oracle** (Filibuster-style). An independent author wrote success contracts for **all
79 write endpoints across 22 services**, from upstream source only, frozen in git **before** the fault set was
revealed.

> **Talking point (the robot referee).** The baseline is a referee that knows exactly six checks: the HTTP
> status, three response-body checks (`status`, `data`, `msg`), one follow-up GET ("does my write show up?"),
> and "can't check this." A blind author translates each contract clause into these six; it flags a run if
> **any** check fails.

> **Example (why the baseline misses `cancel→refund`).** The refund clause is
> `balance_after == balance_before + R` — that needs *arithmetic on a follow-up read*, and the six checks have
> no subtraction. So the clause becomes `NOT_CHECKABLE`, the response is a flawless `"Success."`, and the
> baseline **passes the faulted run (misses the bug)**. B2, which compares the two balances, **fires**. Adding
> subtraction to the baseline = re-implementing MIST.

The baseline is not hand-crippled: it passes a pre-registered **competence floor** (it must catch the injected
faults, and it does — via genuine state-clause failures, while every clean run passes) and survived three cold
reviews. The paradigm is standard: specification/contract-driven oracles (Barr et al., TSE'15), Filibuster
(SoCC'21), contract testing (Pact, Dredd).

### G3 — head-to-head on real defects, ≥2 systems → **in progress (results below)**

**G3-A: TrainTicket `cancel → refund`** (the centerpiece). Three scenarios, five runs each, deterministic:

| Scenario | B2 | G2 baseline | Reading |
|---|---|---|---|
| Natural dependency fault → `{1,"error"}` | fires | catches (msg gate) | tie — B2 additionally *localizes* the lost write |
| Constructed fabricated ack → `{1,"Success."}` | fires | **misses** | **clean B2 win** — the `50→130` vs `50→50` delta the baseline can't express |
| Body-carrying create (agreement anchor) | fires | catches | both catch — the baseline is demonstrably non-strawman |

**G3-B: Sock Shop shipping** (external validity — a second system, a different hazard: a lost message-queue
enqueue).

> **Example (self-documented bug).** Sock Shop's shipping service wraps its enqueue in a try/catch that logs
> *"Accepting anyway. Don't do this for real!"* and returns `201` regardless. Broker unreachable → shipment
> acknowledged and silently dropped — in the **unmodified** official image.

Four cells (two fault strata × two baseline forms), five runs each, deterministic: **B2 fires on every fault
leg**; the strongest *fair* baseline — extended with a liveness check — **still misses** the constructed cell,
because closing that boundary needs out-of-class broker/queue-state observation (i.e., MIST).

**G3 breadth.** 69 / 80 (86.25%) of the frozen state clauses are bindable/evaluable on live TrainTicket; the
residual 11 are exactly the object/aggregate/delta class B2 covers. **False positives:** 0 / 2127
(TrainTicket), 0 / 1200 (Sock Shop).

---

## 4. What we are careful about — anticipated questions

- **"Did you invent the bug?"** No. The buggy logic is **genuinely upstream** — the cancel service's git
  history is entirely upstream authors, with none of our injection markers. What we add is the *trigger*
  (making the refund fail), in a *different* service, clearly labeled. The one *constructed* piece — a
  fabricated clean ack — is disclosed, and is why the wild (zero-injection) hunt is the higher bar.
- **"Is this really black-box?"** "Black-box" qualifies **B2 (judging)** — HTTP + OTel only, no source, no DB.
  **B1 (injection)** is a separate, disclosed grey-box mode; a deployed MIST does not inject.
- **"Is the G2 baseline fair?"** Its fairness rests on **construct validity** — the six primitives faithfully
  represent the response/contract-assertion oracle *class* (Pact/Dredd), which cannot express cross-request
  arithmetic. We scope the claim to that class, never "no tool could," and disclose it as a threat to validity.
- **"Why not compare to Cast directly?"** Cast needs production-traffic replay + Java AOP + historical
  baselines that OSS lacks; a nominal Cast is a crippled comparator. We approximate its *oracle model*
  (Filibuster-style) and argue the deltas (generation, black-box, label-free read-back, open benchmark) from
  verified facts.

---

## 5. What I am doing now, and next

- **Now:** making the G3 breadth number *executable* on live TrainTicket (a control-only run that turns the
  69/80 survey into a measured result), and folding all evidence into the consolidation / paper-evidence pack.
- **Next:** the wild-hunt for a zero-injection defect (the strongest form of the claim), then the write-up.

*One-sentence takeaway:* **B2 makes silently-lost writes testable using only the black-box HTTP surface and
the OpenTelemetry a system already emits — no production traffic, no AOP, no human assertions — and a
competent, blind, fair G2 baseline misses exactly the defects whose observable is a state delta, which is
precisely what B2 reads back.**
