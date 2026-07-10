# Wave-3a plan — 3-cold-review reconciliation (2026-07-10)

Reviews: `REVIEW-3A-A-oracle.md` (ACCEPT-WITH-FIXES, 1 blocking), `REVIEW-3A-B-capture.md`
(ACCEPT-WITH-FIXES, 2 blocking), `REVIEW-3A-C-pc.md` (ACCEPT-WITH-FIXES, 1 blocking).
All findings dispositioned below; the plan is re-issued as **rev 2** in the same file
(`wave-3a-plan.md`). Execution stays gated on the reviewers' unanimous confirmation of rev 2.

## The one genuine cross-reviewer conflict — the item-1 `mist_readback` cell (A-2 / B-F4 / C-B1)

All three found the rev-1 pin defective; they disagree on the fix. C-B1 wants the design target IN
the cell (`flag`), citing the fabricatedack/adminroute precedent + freeze §2 target semantics + the
T9 denominator-exclusion hazard. A-2 and B-F4 independently want `not_applicable` PLUS a NEW named
convention (a third/fourth n/a species: "bindable-but-not-run"), not pooled with T9.

**RESOLUTION (adopted): A+B's named-convention fix, with C's two substantive concerns absorbed.**
Grounds: (1) the TT verdict-valued cells C cites are all RUN-BACKED — G1 actually fired on
adminroute/adminbasic and the G3 head-to-head ran the cancel/createaccount paths — so they are not
a precedent for a never-run SUT; the tenancy-wave convention (the four newest pairs) records
non-run MIST cells as `not_applicable` with preserved targets, and cross-wave cell-semantics
consistency ("verdict-valued mist cells appear ONLY where MIST ran") is the property a hostile PC
can audit mechanically. (2) C's denominator concern is REAL and is fixed by the new convention row
itself: **`bindable-pending-eval` cells are NOT pooled with T9 boundary rows and ENTER the MIST
recall denominator at the wave where MIST runs them (the case is enrolled in the 2.5/E2 run list in
its own notes)** — that sentence goes verbatim into the freeze §6 row. (3) C's textual-incoherence
finding stands regardless and the rev-2 plan states the pin in one unambiguous sentence.
C-B1 is thus RESOLVED-WITH-MODIFIED-FIX (its diagnosis fully adopted; its cell-value prescription
replaced by the named-convention route it itself offered as the alternative, now in-scope because
the §6 row touches NO existing case).

## Disposition table

| # | finding | disposition in rev 2 |
|---|---|---|
| A-1 [BLOCKING] | readback polarity: schema enums can't express control=emptied/fault=present | **FOLDED**: effect-directed encoding — the read-back observable is the CART-EMPTIED effect; `expect_without_fault=present` (the emptied state present = cart `[]`), `expect_with_fault=absent`; locator states the JSON predicate (`items==[]` vs the added productId still listed); R11-style NOMINAL disclosure in notes. No schema change |
| A-2 [MAJOR] + B-F4 [MAJOR] + C-B1 [BLOCKING] | item-1 mist pin | **FOLDED** per the resolution above: cell `not_applicable`; notes reason string `bindable-pending-eval (mist_bindable=true; modality api-get JSON; MIST enablement = 2.75; enrolled for the 2.5/E2 run)`; NEW freeze §6 convention row (distinct from T9; enters denominators at the run wave); plan sentence de-garbled |
| A-3 [MAJOR] | FLAG target presumes an absence/emptiness predicate MIST may not have at pin 7d69de9 | **FOLDED**: the preserved design target carries the predicate-level caveat verbatim (the expected durable effect is a REMOVAL — membership-absence on a list read-back; whether the pinned engine expresses removal-as-effect is itself a 2.75 enablement finding) |
| A-4 [MAJOR] | pin BOTH asymmetry branches' 7-cell sets now; authoring conditional only on the mask | **FOLDED**: item 1 pins branch α (EmptyCart span present-but-erroring → presence=no_flag MISS + naive=flag CATCH) and branch β (span absent → presence=flag + naive per error spans elsewhere); canary+probe select the branch pre-record; the case is authored iff the MASK holds (C-M5 branch) |
| A-5 [MINOR] + B-F3(export) | export query = service=checkout ONLY | **FOLDED** (a service=cart query would pull the cart-add trace and break exactly-one) |
| A-6 [MINOR] | contract-grounding cites + psql order-landed isolation as an artifact | **FOLDED**: doc_citation = checkout source (discarded return) + the deployed flag description (D3c re-freeze row); `readback-psql.txt` per leg records the ORDER row landing on BOTH legs (isolation evidence) |
| A-7 [MAJOR] + B-F10 [MAJOR] | item-2 trace cells: fragments/naive decision must be pre-registered; probe whether temp sidecars export | **FOLDED as a pre-registered exclusion**: NO trace column is scored on item 2. Rationale pinned: (a) presence target cannot exist even on control (2 sidecars, no persistence-side span, app uninstrumented — T2 family unvalidatable); (b) the only error span available is the VS abort itself = the INJECTOR'S OWN ARTIFACT, not SUT-emitted telemetry — scoring naive off the injection tool's signature would credit the column with detecting the experiment (bookinfo's Envoy spans were the meshed SUT's genuine client failures under dependency-down, a different regime); probe records whether the fragments export to the istio-system jaeger + the case notes disclose their existence + `trace_visibility=trace-uninstrumented` stays (app-level) with the fragment disclosure |
| A-8 [MINOR] + C-M4 [MAJOR] | TeaStore min-3 floor accounting | **FOLDED**: new item 2b authors `teastore-order-depdown-specified-001` (`capture_status=specified`, mechanism `dependency-down`, UNSOUND-on-THIS-deploy disclosure in-file + the PVC-backed precondition under which capture is sound; never tallied per R2/R3) → the min-3 claim points at 3 corpus rows (2 captured + 1 specified-with-disclosed-capturability); checklist 2.2 wording updated in the same commit; §0 "live evidence" fixed to "live evidence for 2 of 3 legs" |
| A-9 [MINOR] | VS prefix `/rest/orders` doesn't cover `/rest/orderitems` | **FOLDED**: probe + disclose — if auth writes orderitems separately, fault legs may leave orphan rows; the probe round checks and the notes record it (read-back stays the profile/order-row) |
| A-10/A-13/A-14, C-i9, B-F15 [INFO] | provenance pins; item-3 label soundness; §7 arithmetic; namespaces | acknowledged; folded where they touch text (deploy strings disclose sidecars-on; capture dirs named) |
| A-11 [MAJOR] + B-F6 [MAJOR] | item-3 horizon undefined + drain-before-record | **FOLDED**: observation horizon = the flagship convention (≈15 s psql wait; export window closes at record-end+2 s; stated in the case); **drain-before-record rule**: the record leg starts only after ALL probe orders' rows have landed (psql) + a ≥12 s quiet gap — probe backlog can never satisfy the presence selector inside the record window; the measured landing delay is recorded NEXT TO the verdict |
| A-12 [MAJOR] + B-F9 [MAJOR] | item-3: pin all 7 cells at probe-freeze + T2 family baseline by reference | **FOLDED**: full 7-cell set pinned at the probe-then-freeze gate; T2 consumer-span family validation cited from the flagship `oteldemo-checkout-control` capture (same selectors, same deploy) + a fresh pre-flag canary if the probe round is >1 day later |
| B-F1 [BLOCKING] | flagd toggle mechanics unverified | **FOLDED as pin 1-P0**: pre-capture toggle probe — patch the ConfigMap → poll the flag evaluation path (temporary PF to flagd's OFREP/evaluation port — no standing PF exists) until the value flips, record the latency; fallback ladder = flagd-ui API → flagd pod restart (with its ordering consequences + a post-restart health probe); restore = re-apply the D3c-frozen JSON + verify byte-equality + value flip-back; item 3 inherits 1-P0 verbatim |
| B-F2 [BLOCKING] | item-2 teardown under-specified | **FOLDED as an enumerated checklist** (injection via per-DEPLOYMENT pod-template label ONLY — never the ns label; N≥4 healthy probes at sidecar-injection time BEFORE the VS; VS delete verified; labels removed; rollouts back to 1/1 verified; :8082 PF re-created after every webui cycle; HARD GUARD: no persistence/db restarts while any sidecar config exists — the no-PVC wipe hazard; maintenance flag verified false before/after) |
| B-F3 [MAJOR] | canary→selector-commit ordering; per-item attempt-1 rule | **FOLDED**: each item's selector rows are committed into `trace_score.py` BEFORE its first capture (canary-bound); probes → ≥12 s quiet gap → record → exactly-one pre-check restated per item |
| B-F5 [MAJOR] | identity freshness unverified | **FOLDED**: consumed-identity ledger written into the plan (user11–17 consumed per repo records incl. the rider legs; NEXT-FREE = user18; budget user18–21) + per-identity pre-leg baseline read (profile shows no marker rows) recorded in the runner log |
| B-F7 [MAJOR] | "no wedge expected" untriggered | **FOLDED**: trigger pin — ANY kafka pod restart during item 3 ⇒ the leg is INVALID + the kafka-recovery runbook runs before anything else; post-item drain check (fraud-detection backlog) + RAM check |
| B-F8 [MAJOR] | probe-round artifacts unpinned | **FOLDED**: the probe round records delay distribution (per-order placed→landed), dedupe evidence (row counts vs duplicates), duplicate volume, semantics-vs-D3c check, the ≤1 h timebox, and an explicit AUTHOR / NOT-AUTHOR / STOP decision line |
| B-F11 [MAJOR] | reused spec headers describe the wrong mechanism | **FOLDED**: per-pair spec files authored (`oteldemo-emptycart-flow.yaml`, `teastore-order-meshsever-flow.yaml` + readback twin), copying the sharp-edge warnings, headers describing THIS pair's leg |
| B-F12 [MAJOR] | runner artifacts not citable | **FOLDED**: committed under `debug/a-main/benchmark/b4/runners/3a/` (toggle probe/restore script, VS manifest, injection/teardown scripts, per-leg psql outputs already land in captures/) |
| B-F13 [MINOR] + C-M1 [MAJOR] | §0 factual sentence grep-refuted | **FOLDED**: replaced with C's corrected wording (all 7 non-TT rows `mist_bindable=false`; 11 TT rows true incl. controls/benigns; item 1 = the first non-TT bindable POSITIVE) + the value-dependency sentence (the "MIST catches it" claim is earned only at the 2.5/E2 run) |
| B-F14 [MINOR] | wave-start/close conventions | **FOLDED**: §5 gains the wave-start precondition check (TT at 0, PFs re-verified, RAM, maintenance=false, flagd=frozen JSON), the end-state declaration, the attempt-N retention convention, and per-item leg orders (control-first everywhere) |
| C-M2 [MAJOR] | floor gap arithmetic | **FOLDED into §0**: sites today = 7 (named); wave delta = exactly +1 (item 2 adds 0 — mechanism-multiplex on the same site, which §5 refuses to count); post-wave 8 vs the 15–18/21–28 bands; closure carried by the TT F-corpus tranche (floor 6/target 10, B-m6-gated, unstarted) + SS carts + TeaStore order-items + Boutique (exactly 1); the <20 disclosed-finding branch is LIVE; sequencing is tenancy-economics, not ease |
| C-M3 [MAJOR] | self-test reporting language + MIST targets | **FOLDED**: pinned now — the S2 case feeds per-column comparator-FP characterization ONLY and enters NO MIST-vs-comparator tally until MIST's columns are measured on it; the case's own MIST targets pre-registered at probe-freeze from the frozen QuiescenceGate mapping (TIMEOUT_ABSENT ≠ OBSERVED_COMPLETE_ABSENT; if MIST later FPs, that is reported as MIST's FP — the trap traps everyone); the strongest-measured-comparator optics line added; "our own presence column" → "the benchmark's authored comparator column (arm-3 family)" |
| C-M5 [MAJOR] | item-1 refutation branch missing | **FOLDED**: mirrored from item 3 — if N≥4 probes refute the 200-ack + non-emptied-cart semantics on deployed 2.2.0, the case is NOT authored; survey corrected with a dated row (C-m8/R9 precedent) |
| C-m6 [MINOR] + B(budget) | budget realism | **FOLDED**: 1.5–2 d; each item's 0.5 d includes its ~1 h close-out (validator+FILE_INDEX+freeze+README+commit); collapse order 1→2→3 unchanged (endorsed by C) |
| C-m7 [MINOR] | undersold: first mesh-sever corpus-wide | **FOLDED into §0** (grep-verified: zero mesh-sever cases exist; item 2 = the corpus's first, making captured mechanisms {flag, dependency-down, mesh-sever} cross-SUT) |
| C-m8 [MINOR] | item-3 refutation branches conflated | **FOLDED**: split — measured LOSS ⇒ STOP + dated survey correction + a decision point (S1-positive candidate under vendor-flag provenance; authored only under its own discipline or deferred); delay-below-window ⇒ clean no-case |

## Net effect on the plan
Rev 2 adds: pins 1-P0 (toggle probe), the effect-directed read-back encoding, both asymmetry
branches' cell sets, the pre-registered item-2 trace-exclusion rationale, item 2b (the specified
dependency-down case), the drain-before-record rule + horizon, the identity ledger, per-pair specs
+ committed runner artifacts, the enumerated item-2 teardown checklist, corrected §0 (facts + gap
arithmetic + first-mesh-sever + value-dependency), the self-test reporting language + MIST-target
pre-registration, refutation branches on all three items (with C-m8's split), §5 wave-start/close
conventions, and the budget restatement. Counts unchanged (18→23 target; 24 if item 2b counts —
it does: **18→24 committed files, but the REPORTED corpus counts remain "10 pos / 13 neg captured
+ 1 specified"** — reported per the R1 rule, never a bare S1 count).

## Confirmation-pass outcome (rev 2 → rev 2.1) — UNANIMOUS
- **C: CONFIRM-ACCEPT** on rev 2 (verified its C-B1 modified-fix grounds against the repo record —
  the TT verdict cells ARE run-backed — and all its M/m findings folded; two cosmetic observations,
  explicitly not conditions: the 19→24 recon slip [fixed above] + the "§1.3" dangling pointer
  [fixed in the plan header]).
- **B: CONFIRM-ACCEPT** on rev 2 (all F1–F15 verified folded; independently re-verified §7 counts
  and that all 18 existing cases are `captured`; same recon slip noted non-blocking).
- **A: RESIDUAL ×4, all textual, none reopening substance**, with the explicit pre-commitment
  "with these four folded (no other changes), my confirmation converts to CONFIRM-ACCEPT without a
  further pass." All four are folded as **rev 2.1** (same file, same commit as this addendum):
  (1) A-2c grandfather sentence for the two pre-convention benign cells (noop-modify, dedupe) in
  the §1 MIST-cells pin — the audit property now holds by construction; (2) the item-3 AUTHOR
  margin (minimum observed delay ≥ 2× the ack→export-close lag; straddling ⇒ NOT-AUTHORED);
  (3) the A-4 catch-all (outside both branches ⇒ STOP, pin the measured 7-cell set pre-record,
  dated divergence note); (4) bookkeeping (18→24 here; the §2b cross-reference made true by adding
  the 2-of-3-legs phrase to §0). Per A's own words the four foldings convert its verdict; B and C's
  confirmations carry over (the rev-2.1 delta is exactly the textual items they flagged as
  non-blocking plus A's residuals, inside substance they endorsed).
**GATE RESULT: UNANIMOUS ACCEPT — wave-3a rev 2.1 is cleared for execution.**
