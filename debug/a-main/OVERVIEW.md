# MIST — Progress Report (B1/B2 mechanism, G1/G2/G3 gates)

**Black-box detection of acknowledged-but-lost writes in microservices.**

A working report in our own codenames (B1, B2, G1–G3). It says what each codename means, what has been done,
what is in progress, and the honest limits. Concrete examples are called out as `> Example` /
`> Talking point` for the talk. Detailed plans/results are indexed by `EXECUTION.md`.

> **Running log — absolute dates.** Every block is tagged by *when the work happened*; only the single **Now**
> marker (current state) is relative, and it too is dated so it moves as the log grows. §0–§5 = the gate work
> **through 2026-07-08** (B1/B2; G1/G2/G3); §6 = **2026-07-08 onward**, ending in the current **Now**. (§5's
> "now / next" is the 2026-07-08 plan, superseded by §6.)

---

## 0. Codenames and status (read first)

| Code | Name | One line |
|---|---|---|
| **B1** | Fault-injection mode | Run a write once clean, once faulted. **Lab scaffolding** to manufacture labeled ground truth — *not* the contribution. |
| **B2** | Differential read-back oracle | **The contribution.** After a write, issue a black-box GET and check the acknowledged entity is actually there; **fire** if not. |
| **G1** | Gate 1 — mechanism soundness | Does B2 fire on a real lost write *and* not cry wolf on benign writes? |
| **G2** | Gate 2 — fair comparator | Stand up a blind, competent assertion baseline worth beating. |
| **G3** | Gate 3 — head-to-head | A **real** defect B2 catches that the G2 baseline misses because no human wrote the assertion, on **≥2 systems**. |
| **C2** | Open labeled benchmark | The corpus of acknowledged-but-lost cases (positives + benign negatives) any oracle is scored on. **The paper's spine (§6).** |
| **C3** | Rater study | Blind human raters label the cases (Cohen's κ) → the ground truth that keeps the evaluation non-circular. |
| **S3** | Wild-hunt | Zero-injection observation for a *natural* discriminator. Result = **scarcity** (§6.3). |

Key terms: **acknowledged-but-lost write** = the server answers `200 "Success"` but the state change never
persisted. **read-back** = a follow-up GET that re-reads the state. **fire** = the oracle flags a problem.

**Status (as of 2026-07-14).**
- **Gate work (through 2026-07-08; §1–§5):** G1 **PASS**, G2 **accepted**, both G3 head-to-heads done — but
  G3 as written is **re-scoped** (the clean B2 wins are on constructed faults, the natural cells tie; §3, §6.1).
- **Benchmark phase (2026-07-08 → 07-14; §6):** corpus **26 cases (11 positive / 15 negative) across 5
  systems**, validator-green; B2 read-back **enabled + fired** on 2 new SUTs (2.75-A); flagship cell
  **run-backed** (E2); wild-hunt = **scarcity** (0 / 1514, S3). **8 distinct positive sites.**
- **Owed:** the **C3 rater study** (materials ready; blocked on IRB + compensation), a full-corpus B2 recall
  table, and — the open bet — a **natural (zero-injection) discriminator**, which S3 showed the wild does not
  readily supply.
- **Not started:** the **paper draft** (the biggest distance to submission).

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

> *(Plan as of 2026-07-08 — **superseded by §6.** The wild-hunt listed as "next" ran 2026-07-13; the
> executable-breadth run was reviewed down as low-ROI and not run. See §6.)*

- **Now:** making the G3 breadth number *executable* on live TrainTicket (a control-only run that turns the
  69/80 survey into a measured result), and folding all evidence into the consolidation / paper-evidence pack.
- **Next:** the wild-hunt for a zero-injection defect (the strongest form of the claim), then the write-up.

*One-sentence takeaway:* **B2 makes silently-lost writes testable using only the black-box HTTP surface and
the OpenTelemetry a system already emits — no production traffic, no AOP, no human assertions — and a
competent, blind, fair G2 baseline misses exactly the defects whose observable is a state delta, which is
precisely what B2 reads back.**

---

## 6. 2026-07-08 → 07-14 — the pivot to an open benchmark + an oracle study

### 6.1 Why the pivot (2026-07-08)

G3's verdict of record (`g3-result.md`): **not met as written** — no wild, zero-injection defect that only B2
catches (the natural cells are ties; the clean B2 wins are constructed; the trace-oracle leg was never run; no
rater κ). Per that verdict the empirical claim stays *"credible, not yet clear"* until an **open labeled
benchmark** and a **human-adjudicated study** exist. **Decision: make the benchmark + oracle-evaluation study
the paper's spine; the wild discrimination win demotes to a bonus.**

### 6.2 What the benchmark is, and what it measures

An open, human-labeled corpus of acknowledged-but-lost behavior cases — positives (genuine masked losses) and
negatives (benign-by-design) — across independently-built systems. Any oracle is scored on it; blind human
raters (Cohen's κ) supply the ground truth, so it is **not B2 grading its own homework**. It measures each
oracle's **recall** on the positives and **false-positive rate** on the negatives. The oracle ladder it
separates:

| Oracle | Judges by | Honest `5xx` failure | Masked `2xx`-but-lost | Needs |
|---|---|---|---|---|
| naive (status/schema) | HTTP code + schema | catches | **misses — false green** | nothing |
| contract (G2 baseline) | response body + one follow-up GET | catches | catches only if a body tell leaks; **misses when clean** | a hand-written contract |
| trace-presence | a downstream span is present | catches (if instrumented) | catches (if instrumented + a pre-set assertion) | instrumentation + assertion |
| **B2 read-back** | the durable effect actually moved | abstains (not its job) | **catches** | a black-box GET only |

> **Talking point.** The benchmark is built around the **"false green" column** — where status/schema passes
> but the write is lost. That column is exactly what makes B2's contribution *measurable*, and what a
> benchmark of this hazard has to be centered on.

### 6.3 Timeline — each step: what, why, result

| Date | Step | Why | Result |
|---|---|---|---|
| 07-08 | Direction pivot | G3 not-met-as-written; a benchmark is the Cast-independent, hard-to-reject spine | benchmark + C3 study becomes the paper's core |
| 07-10 | Tenancy window → **+TeaStore, +OTel-Demo** (4 systems) | external validity — show it isn't TrainTicket-special | corpus → 18; flagship find = **OTel kafka async loss** (acks `200` in ~0.02 s, order message never exists) |
| 07-10 | **Wave 2.75-A** — B2 read-back wired to the 2 new systems | prove B2's read-back path runs end-to-end there | both legs **fire 5/5**, ground-truth-verified. *Self-concordant → not a discrimination win (stays pre-registered).* |
| 07-11 | **E2** — flagship cell run-backed | upgrade TT `cancel→refund` clean win from manual curl to a harness run on a **traced** deploy | B2 **fire 5/5**, baseline **miss 5/5**; claim = *specification-locality* (a capability datum, not the headline) |
| 07-13 | **S3 wild-hunt** — zero-injection observation | settle the open bet: does a natural discriminator exist in the wild? | **0 confirmed losses / 1514 acked writes / 5 endpoints / 3 systems** (≤ 0.20%) = the pre-registered **scarcity** finding |
| 07-13 | **R1 / R1b / R1c / R1d** — corpus completion | fill the corpus to its floors | **8 positive sites** (3 widenings rejected as padding); boundary **B2 = LOST not CORRUPTED**; benign-power shortfall disclosed |

> **Talking point (why "scarcity" is a result, not a failure).** S3 finding 0 wild losses in 1514 writes is
> *why* the benchmark's positives are injected: naturally-occurring masked losses are rare, so injected
> ground truth is the honest way to populate the hazard — and the scarcity number itself is a contribution.

### 6.4 The corpus today (2026-07-14) — 26 cases, JSON-verified

**Composition (real counts from the case files):**

| System | positive | negative | total |
|---|---|---|---|
| TrainTicket | 5 | 6 | 11 |
| TeaStore | 4 | 3 | 7 |
| OTel-Demo | 1 | 4 | 5 |
| Sock Shop | 1 | 1 | 2 |
| Bookinfo | 0 | 1 | 1 |
| **Total** | **11** | **15** | **26** |

**The 11 positives** = **6 natural-mechanism** (unmodified image + an operational trigger; the image's *own*
swallow) **+ 5 TrainTicket fork-flag constructions**. By fault mechanism: flag ×6, dependency-down ×3,
mesh-sever ×2. They occupy **8 distinct sites**:

| System | positive sites | source |
|---|---|---|
| TrainTicket (4) | cancel→refund · adminroute create · adminbasic-contacts create · createaccount | fork flag (constructed) |
| TeaStore (2) | order create (maintenance / mesh-sever) · orderitems child-collection | natural (unmodified image) |
| OTel-Demo (1) | checkout → kafka enqueue | natural |
| Sock Shop (1) | shipping → queue enqueue | natural |

**The 15 negatives** = **9 clean control legs** + **6 designed benign traps**: Bookinfo ratings-down →
graceful degrade; OTel checkout eventual-consistency ×3 (order lands after a delay); TT contacts dedupe-reject;
TT contacts no-op modify.

> **Example (a positive).** TeaStore, `POST` order with the vendor maintenance flag on → **HTTP 201, body
> `-1`**, order never persists, absent from the user's order history → label **positive** (acknowledged-but-
> lost). B2 fires; the status/schema oracle passes it clean.

> **Example (a benign trap).** OTel checkout with kafka slowed → the order is **delayed but does land** →
> label **negative** (eventual consistency). A trigger-happy oracle false-fires here; B2 waits out quiescence
> and stays silent — this is the column that separates a real oracle from a jumpy one.

**Worked example with source (for the talk) — TrainTicket cancel→refund.** This one endpoint shows both
what a masked-2xx looks like *and* the exact natural-vs-constructed line. Two fork toggles take two
different code paths — they do **not** both reach the same return statement:

| mode | drawback does | response | shaped by |
|---|---|---|---|
| `fail` ("natural" cell) | throws HTTP 500 | `{status:1,"error"}` | `CancelController` catch (**unmodified** source) |
| `fabricatedack` ("constructed" cell) | fake `{status:1}`, no persist | `{status:1,"Success."}` | `cancelOrder` line 92 (normal success return; the clean-ack-**lost** *combination* needs the flag) |

The `{1,"error"}` is produced by **unmodified** TrainTicket code — a controller catch-all that reports a
refund failure as machine-readable `status=1` (success), leaking only a human-facing "error" in `msg`:

```java
// CancelController.java:48-50 (unmodified Fudan source, in docs-bundles/)
} catch (Exception e) {
    CancelController.LOGGER.error(e.getMessage());
    return ok(new Response<>(1, "error", null));   // HTTP 200, status=1 SUCCESS, msg literal "error"
}
```

Because `status=1` is the machine success contract (`status==1` ⟺ success across the SUT), this is a
genuine acked-but-lost — **not** honest error handling (which would return `status=0`). But the `msg`
leak lets the frozen comparator's `MSG_CONTAINS` also catch it → **G3 tie**. Only `fabricatedack`'s
fully-clean `{1,"Success."}` is caught by B2 alone (durable balance 50→50 vs the control's 50→130) →
the **clean win**. Both cases honestly carry `source=by_construction`.

> **Provenance for all 26 cases** — source lines, the natural↔constructed **4-tier spectrum**, the one
> *specified-not-captured* outlier, the orthogonal `source` (masking) vs `provenance_class` (trigger)
> axes, and 8 audit findings — is in **`benchmark/PROVENANCE-LEDGER.md`** (2026-07-14).

**Rater study (C3) — materials ready.** A leak-gated hand-over packet is assembled; raters found; blocked
only on user-side IRB + compensation blanks. The rating corpus is delivered from the corpus track.

### 6.5 Now (2026-07-14) — state, honest limits, what's owed

- **Banked:** corpus 26 cases / 5 systems (validator-green); B2 read-back enabled + fired on 2 new SUTs
  (2.75-A); flagship cell run-backed (E2); the S3 scarcity result.
- **Owed:** the **C3 rater study** (materials ready, blocked on IRB + compensation); a **full-corpus B2 recall
  table**; and — the open bet — a **natural (zero-injection) discriminator**, which S3 showed the wild does
  not readily supply.
- **Not started:** the **paper draft** — the single biggest distance to submission.
- **Disclosed limits** (managed by honesty, not by more injection — padding was reviewed and rejected):
  corpus size (26); every positive needs an operational trigger to reach the hazard (6 then surface via the
  image's *own* natural swallow, 5 via a TrainTicket fork flag) — none is a purely wild loss (that is the S3
  result); and the benign-power calibration shortfall.

*One-sentence takeaway (2026-07-08 → 07-14):* **the spine moved from "B2 wins a head-to-head" to "an open, human-
labeled benchmark for acknowledged-but-lost writes + an honest oracle-evaluation on it" — with S3's scarcity
result justifying the injected positives and the natural-discrimination win kept as a bonus the wild does
not readily supply.**
