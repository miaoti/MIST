# G2 pre-registration — the Cast-delta paragraph + the fair-comparator protocol

**Status:** DRAFT 2026-07-02 (written during the Gate-1 run-#3 wait; G2 per
[EXECUTION.md](../EXECUTION.md) = "novelty articulation + comparator, before scaling").
Sources: the verified-Cast facts in [README §2](../README.md) (primary-source-checked,
arXiv:2602.00972), the comparator commitments in README §6 + EXECUTION G2, and the
cold-review prior-art list in
[REVIEW-B1B2-COLD-C](../research/REVIEW-B1B2-COLD-C-impl-contribution.md) P2-3.
**This document is itself subject to the ≥3-cold-reviewer 验收 before G3 relies on it.**

---

## 1. The one-paragraph Cast delta (Gate-2 deliverable a)

> We do not claim a new fault-injection technique, nor to be the first to detect
> masked or silent cross-service failures — Cast (ICSE-SEIP'26) already detects
> masked-2xx failures and silent dual-write inconsistencies, with 89
> production-confirmed bugs. Cast achieves this by replaying production traffic
> through Java AOP instrumentation and checking phase-based metric thresholds at
> configured assertion points against historical trace baselines; each of those four
> ingredients is an access requirement that many systems — and every open-source
> system we evaluate — cannot meet. MIST removes all four: it generates its
> cross-service workload (no production traffic), observes only the OpenTelemetry
> the system already emits (no AOP, no added instrumentation, any language), decides
> "acknowledged-but-lost write" with a label-free read-back differential (no metric
> thresholds, no assertion points, no historical baselines, no human-authored
> assertions), and is evaluated end-to-end on open OSS systems with a released
> labeled benchmark (vs Cast's closed evaluation). We quantify what those weaker
> assumptions cost and buy: the oracle's own measured false-positive rate on
> pre-registered benign traps (eventual consistency, retries, designed degradation),
> and a head-to-head against a competently-configured, blind-authored assertion
> oracle on the same injected faults — where the question is not "who detects more"
> but "what does an assertion-based oracle structurally miss when no human wrote
> that assertion."

Hygiene rules bound to this paragraph (from README §8.5/§0):
- Never "first to detect"; the claim is the **combination** (generation + black-box
  OTel + label-free read-back + open benchmark), i.e. accessibility + automation +
  measurement — not detection primacy.
- "No test-specific instrumentation", never "instrumentation-free" (OTel IS
  instrumentation the system already runs).
- Generation-vs-replay coverage is **argued-not-measured** unless we actually measure
  vulnerable-path coverage head-to-head (README §8.5 item 4).
- The read-back oracle applies **only** to write-path SUTs with a clean black-box
  read-back — scope every claim accordingly (README §0 scope honesty).
- Per cold-review C P2-3, position against the full nearest-neighbor set, not only
  Cast: Filibuster (assertions + existing suite), Gremlin (assertion-style checks on
  intercepted RPC), metamorphic REST testing (Segura et al. — our FIRE rule IS a
  per-run metamorphic relation, say so), EvoMaster/RESTest oracles (status/schema/
  regression), AGORA/AGORA+ (mined single-response invariants), MINES (label-free
  invariant inference). The defensible island: **label-free + black-box
  collection-membership read-back + quiescence gating + trace-stratified confidence,
  targeted at acknowledged-but-lost writes, under generation (not replay), evaluated
  openly.**

## 2. The fair-comparator protocol (Gate-2 deliverable b — pre-committed, not "where feasible")

**Comparator identity.** Filibuster-style fault injection + **hand-authored
per-endpoint assertions**, approximating **Cast's oracle pattern** (assertion points +
expected-outcome checks) where the OSS setting permits. We do NOT claim to run Cast
itself (closed system; production-replay + AOP + historical baselines unavailable on
OSS — README §7). We approximate its *oracle*, not its *pipeline*, and say so.

**Blind-authoring rule (what makes it non-strawman).**
1. Assertions are authored from the **OpenAPI contract + service docs/source only**,
   by an author who has NOT seen: the injected-fault list, MIST's target-triple
   registry, MIST's verdicts, or any Gate-1 artifacts. Operationally: the assertion
   author is a fresh-context agent/engineer given only the SUT spec + "write
   assertions you'd defend in code review for these endpoints' success contracts."
2. The assertion set is **frozen by commit hash BEFORE the injected-fault list is
   revealed** or any comparator run executes; the authoring brief + timestamps ship
   with the benchmark (C2) so the blindness is auditable.
3. The comparator gets the **same verified inputs, same endpoints, same fault strata,
   same SUTs, same run budget** as B1+B2 (matched budget), and results are reported
   at **matched recall** (README §6 E2).
4. If a fair comparator cannot be stood up on a SUT, **disclose and drop that SUT
   from the head-to-head — never weaken the comparator** (EXECUTION G2).

**Decisive-result definition (pre-registered).** Evidence that moves a PC off "you
automated an assertion": a defect (injected at G2-calibration; REAL at G3) that
(a) MIST's read-back differential FIREs on, (b) the blind-authored assertion set does
NOT flag, and (c) post-hoc root-cause shows the miss is *because no assertion covered
that state relation* — not because of comparator misconfiguration (each miss gets a
category: no-assertion-existed / assertion-existed-but-wrong-signal /
comparator-infra-failure; only the first counts for us).

**Pre-registered outputs.** Per SUT and per stratum: MIST vs comparator
detection at matched recall; MIST oracle FP (the Gate-1-style interval
`[observed-gated/acked, fires/acked]` per the B1+B2 reconciliation R2); comparator
assertion count + authoring time (the cost axis of the accessibility claim);
miss-category table.

**What G2 does NOT decide.** G2 = the paragraph is PC-defensible + the comparator is
*set up and calibrated on injected faults*. Whether MIST finds REAL defects the
comparator misses is **G3**, and a thin G3 routes to Plan B (benchmark + prevalence
+ capability paper) per README §9 — G2 must not leak Gate-3 claims.

## 3. Execution checklist for G2 (when the cluster frees up after Gate-1)
1. Freeze this prereg (post-验收) → commit hash recorded here.
2. Author + freeze the blind assertion set for TrainTicket's write-path endpoints
   (fresh-context author per §2).
3. Build the comparator runner (Filibuster-style injection reusing B1's
   `FaultInjector` seam; assertion evaluation harness).
4. Calibrate on the Gate-1 injected faults (sensitivity check for BOTH tools).
5. Record the one-paragraph delta (§1) + comparator config in the paper repo.

*Review trail: pending ≥3 independent cold reviewers on this document (per the
standing 验收 rule) — to be dispatched together with the G3 SUT-2 triple
pre-specification so one review wave covers both prereg docs.*
