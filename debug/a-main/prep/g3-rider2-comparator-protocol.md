# G3 Rider 2 — comparator binding-round + reporting protocol (pre-registration)

**Status:** PRE-REGISTERED 2026-07-02 (docs rider; no code). Consolidates the carried
G3 riders from
[REVIEW-COMPARATOR-RECONCILIATION](../research/REVIEW-COMPARATOR-RECONCILIATION.md)
(C3/C13, review-B finding 7) and
[g2-comparator/calibration-result.md](../g2-comparator/calibration-result.md) into an
executable protocol, so the Gate-3 head-to-head over REAL defects is fixed BEFORE any
G3 defect is revealed. Any change after this file is a disclosed amendment.

## 1. The full-frozen-set binding round (C3, review-B finding 7 — the landmine)

At G2 only the two calibration endpoints were bound. G2's own bindings narrowed the
state clauses exactly onto MIST's registry keys (review B) — a pattern a hostile PC
would attack. G3 removes that surface:

> **Binding round rule.** Before the G3 comparison run, bind the **ENTIRE frozen
> blind set** for each SUT's tested surface — every mutating endpoint AND its
> **failure contracts** — from the frozen `blind-assertions-*.yaml`, using ONLY the
> upstream-source provisioning already frozen (A1). No endpoint is silently dropped;
> an endpoint the runner cannot bind is recorded `NOT_CHECKABLE` with the reason, not
> omitted.

**Why the failure contracts are load-bearing (B-7):** a frozen contract like
adminroute's `{0,'start or end station not include in stationList', null}` or
contacts' `{0,'Already Exists', ...}` says a *rejection is the correct behavior* for
invalid/duplicate input. If those clauses are left unbound, a legitimate rejection at
G3 (e.g., a defect that makes the SUT reject a valid write, or a test that happens to
submit a colliding key) would be scored by the comparator as a **flag** — a false
positive that would *inflate the comparator's apparent recall* against MIST. Binding
the failure contracts lets the comparator recognize a contract-correct rejection and
NOT flag it. This is pro-rigor and, if anything, pro-MIST-adversarial (it makes the
comparator harder to beat), so it is the honest operating point.

- **Scope per SUT:** TrainTicket = the frozen 79-endpoint set (`15954a8`) on the
  tested surface. Sock Shop / petclinic (SUT-2/3) = a NEW blind-authored frozen set
  per the same A1 protocol (upstream-source-only, frozen-by-commit BEFORE the run,
  transcript-attested), covering their mutating endpoints + failure contracts. The
  SUT-2/3 blind sets do not yet exist — authoring them is a G3 prerequisite gated by
  the SUT-2/3 deploy (prereg §1/§2).
- **Executable bindings:** extend the closed primitive set already shipped
  (HTTP_STATUS / ENVELOPE_STATUS / ENVELOPE_DATA / MSG_CONTAINS / STATE_GET /
  NOT_CHECKABLE + entity-matches-submitted-fields). Failure-contract clauses bind to
  the same primitives against the *reject* outcome (e.g., ENVELOPE_STATUS expect 0 +
  MSG_CONTAINS the frozen reason + STATE_GET absence-expected).

## 2. Per-SUT FP / cost protocol (C13 + prereg §0 cross-reference)

MIST and the comparator are measured at a **matched operating point** per SUT:

- **MIST side (prereg §0, unchanged):** benign probe N=30 per SUT; pre-registered
  **≤5% observed-gated sync-FP bar per SUT, never pooled across SUTs**; quiescence
  knobs carried from Gate-1 (poll 500 ms / timeout 10 000 ms / settle 3 000 ms); a
  SUT whose bar is NOT_EVALUABLE does not count toward the ≥2-SUT requirement.
- **Comparator side (NEW rule, C13 — the asymmetry the study must print):** the
  comparator **cannot show a control false alarm by construction** — its control-run
  all-PASS gate reclassifies any control failure to `comparator-infra-failure`. That
  is not a free win: it means comparator control-FPs are *hidden in the
  infra-failure channel*. Therefore **report the comparator's per-SUT
  infra-failure RATE** (control-gate aborts + transport failures + binding
  mismatches, as a fraction of attempted endpoints) alongside MIST's FP rate. A
  comparator that "never false-alarms" only because it frequently cannot evaluate is
  not strictly better than MIST — the two costs are reported side by side, neither
  pooled nor hidden.

## 3. Delay-vs-loss stratification (satisfied by A3 — documented, no new code)

Gate-3's Toxiproxy faults include **latency (S-delay)** as well as **loss (S1)**. A
detector that flags a *tolerated delay* as a data-integrity loss is producing a false
positive. Both oracles already distinguish delay from loss at the matched budget:

- **MIST:** the read-back poll waits to the pre-registered 10 s / 500 ms cap with the
  quiescence gate — a write delayed but landing within the cap gates
  `OBSERVED_PRESENT` (NO_FIRE), only a write absent AT the cap gates absent.
- **Comparator:** the **A3 bounded presence-retry** (`f42ea2a`;
  `mst.comparator.state.retry.cap.ms=10000` / `poll.ms=500`) is exactly the additive
  second state-read at MIST's budget the rider asked for — a delayed-but-present
  write converges to PASS within the same window; only a true loss stays ABSENT to
  the cap. Absence-expected clauses stay single-shot.

> **Stratification reporting rule.** G3 results are reported **stratified by fault
> type** (loss S1 vs delay S-delay vs the wild/natural stratum), per SUT, for BOTH
> oracles. A delay-fault run that either oracle flags as a loss is a measured false
> positive in the delay stratum and is reported as such — never pooled into the loss
> stratum's detection count. This is the operating point at which the delay/loss
> discrimination claim is made; it is matched (same 10 s/500 ms budget) so neither
> oracle gets a timing advantage.

## 4. What this rider does and does not pre-commit
- **Does:** the binding-round scope (full frozen set + failure contracts), the
  infra-failure-rate reporting rule, the delay-vs-loss stratified reporting, and the
  matched-budget statement — all fixed before any G3 defect is seen.
- **Does not:** author the SUT-2/3 blind sets or bind them (gated by deploy); assert
  a comparison OUTCOME (the head-to-head numbers are produced at G3, not pre-stated —
  only the protocol is). The G2 injected-fault wins remain calibration-only
  (oracle-co-designed fault class); the PC-moving comparison is the G3 real-defect
  head-to-head under this protocol.

*Depends on: G2 CLOSED (calibration accepted, `4c4290a`); Rider 1 correlator join
(`e640748`) for the MIST-side per-pair tallies. Feeds: the G3 run once SUT-2/3 are
deployed and their blind sets authored+frozen.*
