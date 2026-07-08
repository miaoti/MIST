# G3 consolidation plan — close the arc into a gate verdict + paper-evidence pack

**Status:** PLAN (pre-execution). Per the standing workflow: plan → ≥3-cold-review → reconcile →
execute → the executed verdict gets its own ≥3-cold-review before it is cited as the gate verdict.
**Provenance:** this is Option 4 of the strategic fork recorded in
`g3-comparator-tt/REVIEW-BINDABILITY-RUNNER-RECONCILIATION.md` (options 1/2 = executable breadth,
judged LOW-ROI by that review; option 3 = the shipping wild-hunt → executed and CLOSED at commit
4972d3b). Nothing here re-opens reviewed decisions; this plan only assembles them.

## 1. What exists (evidence inventory — all reviewer-accepted)

| # | pillar | verdict doc | review record | status |
|---|---|---|---|---|
| P1 | Gate-1: oracle fires on injected faults + sync FP 0.0 (0/2127, interval [0,0], gate 100%) | `prep/gate1-result.md` | in-doc (3-cold-reviewed) | **G1 = PASS** |
| P2 | Gate-2: blind comparator authored (freeze-before-reveal) + calibration accepted (competence floor met) | `g2-comparator/calibration-result.md` | in-doc | **G2 = COMPLETE** |
| P3 | TT cancel→refund head-to-head (centerpiece): 3 cells N=5 stable — natural = detection tie + MIST diagnosis/localization; constructed (disclosed fork fabricated-ack) = clean MIST win via pre-funded arithmetic balance delta; agreement site = both catch | `g3-comparator-tt/g3-headtohead-results.md` | `REVIEW-HEADTOHEAD-RECONCILIATION.md` (2 rounds × 3) | **ACCEPTED, claim-ready** (2 standing framing rules) |
| P4 | Rider-2 bindability survey over the whole frozen TT set: generous 69/80 = 86.25% bind / 11 structural NC (strict 59/80); residue = the object/aggregate/delta class MIST covers | `g3-comparator-tt/rider2-bindability-survey.md` | in-doc (3×) | **ACCEPTED** = the external-validity answer for the comparator class |
| P5 | SUT-2 benign FP probe: 0/1200 acked benign writes, bar v2 PASS, gate 100% resolved (HAL read-back + cookie auth path) | `prep/g3-sut2-fp-probe-result.md` | `prep/REVIEW-SUT2-FP-RECONCILIATION.md` | **ACCEPTED** |
| P6 | SUT-2 shipping head-to-head: 2×2 (natural/constructed × P2-amended/as-frozen) + benign specificity control + ground-truth corroboration; N=5 deterministic incl. across a host reboot | `g3-comparator-ss/g3-shipping-headtohead-results.md` (RESULT OF RECORD) | `g3-comparator-ss/REVIEW-SHIPPING-HARNESS-RECONCILIATION.md` (3 waves × 3 reviewers) | **ACCEPTED, claim-ready** |

Explicitly NOT in evidence (reviewed decisions, cited not re-litigated): the executable breadth run
(BindabilityRunner REJECTED for the full-empirical claim — the ANALYTICAL survey P4 stands); any SUT-2
carts constructed-sensitivity (branch β: carts honestly 5xxes); wild trace-only bug corpora (README §
"bug story" honesty rule).

## 2. Gate-3 adjudication (the honest mapping — the core of the verdict doc)

Gate-3 as written (README §gates): *"B2 finds ≥1 real acknowledged-but-lost-write /
missing-compensation defect on a real SUT that a status/schema oracle AND a hand-asserted
Tracetest/Cast-style oracle miss, reproduced across ≥2 SUTs."*

Component-by-component:

- **"≥1 real acked-but-lost / missing-compensation defect on a real SUT"** — MET twice, differently:
  TT cancel→refund is natural IN SOURCE (drawbackMoney false → {1,"Success."}); SS shipping's swallow
  ships in the UNMODIFIED upstream image and both faults are purely operational (no source flag at
  all — the stronger instance on this axis).
- **"a status/schema oracle misses"** — MET, demonstrated live: the as-frozen blind contract
  (HTTP_STATUS-only on SS; envelope contract on TT) misses both SS strata; TT constructed is the clean
  miss. DISCLOSED per the accepted framing: TT natural = a detection TIE (the {1,"error"} envelope is
  flagged by the msg gate; MIST's edge there = localization), SS natural under the P2-strengthened
  form = a diagnosis-gap tie (comparator sees the /health outage; MIST localizes the lost write).
  The CLEAN misses are: TT constructed (disclosed fork flag) + SS constructed (operational only).
- **"AND a hand-asserted Tracetest/Cast-style oracle misses"** — RE-SCOPED by a reviewer-accepted
  decision, which the verdict must cite verbatim: prereg reconciliation item 3 ("**Comparator =
  Filibuster-approximating, Cast-pattern OUT**", DONE in G2 v2 with rationale) fixed the fair
  comparator class as the response-assertion contract checker that was then blind-authored,
  calibrated (G2), and run (P3, P6). A live trace-style comparator was therefore never part of the
  executed protocol. The verdict discloses this and adds the ANALYTICAL argument for the SS
  constructed cell: with publisher confirms off, the publish SUCCEEDS at the protocol level and the
  broker's reject-publish drops the message silently — no errored span exists anywhere, and a
  hand-asserted span-presence/error assertion on the publish would PASS; only broker/queue-state
  observation sees the loss. (For TT constructed, the fabricated-ack produces a normal 2xx span tree
  on the unmodified call path — same analytical shape.) This stays an ANALYTICAL disclosure, not an
  empirical cell; the claim wording must scope the comparator to the class actually run.
- **"reproduced across ≥2 SUTs"** — MET: TT + SS, two independently-built systems, two hazard classes
  (sync DB compensation / async MQ enqueue), two durable-sink types (arithmetic balance / queue count).

**Proposed verdict wording (for reviewers to attack):** "G3 = PASS under the G2-v2 re-scoped
comparator class, with the tie/clean-win structure disclosed per the accepted framing rules" — NOT an
unqualified "PASS" of the original two-oracle sentence.

## 3. Deliverables

- **D1 `debug/a-main/g3-result.md`** — the gate verdict (mirrors `gate1-result.md`'s role): the §2
  adjudication + the six-pillar inventory + every standing framing rule collected in one place
  (TT: oracle-class scope adjacent to the clean-win claim; Rider-2 = the external-validity answer;
  SS: the 9 rules in the RESULT OF RECORD §Framing) + threats-to-validity roll-up + the deferred-item
  ledger (executable breadth; trace comparator; β extras).
- **D2 (inside D1)** — the paper-evidence pack: one combined TT+SS table (per SS reviews C-m4/C-m9:
  two phenomena per SUT + controls, never a win-ratio matrix), each headline claim mapped to
  {evidence cell, raw-log path, review record, framing rule}. Three headline claims, no more:
  (i) capability: the differential state oracle detects acked-but-lost writes end-to-end black-box
  (P1, P3, P6); (ii) comparator boundary: the strongest fair response(+liveness) contract checker
  cannot see the clean-loss class, and its bindable fraction over the whole frozen set is 86.25%
  with the residue exactly the class MIST covers (P3 constructed, P6 constructed, P4); (iii)
  specificity/cost: measured FP 0.0 on two SUTs' benign paths + the benign control + the comparator's
  infra-failure rate reporting rule (P1, P5, P6, Rider-2 protocol §2).
- **D3** — manifest/index sync: FILE_INDEX rows for the new docs; memory update; the a-main README
  gets ONE line under the gates section pointing at `g3-result.md` (no plan rewriting).

## 4. Execution order

1. This plan → **3 cold reviewers in parallel** (lenses: (a) gate-adjudication honesty — does §2
   overclaim; (b) evidence-inventory completeness/accuracy against the repo; (c) paper-readiness —
   are D2's three claims the right cut, correctly bounded). Reconcile; fold fixes.
2. Author D1/D2 exactly per the reconciled plan (assembly only — no new experiments, no re-argued
   verdicts; every number traced to a committed doc/log).
3. D1 → its own 3-cold-review (the gate1-result protocol) → reconcile → G3 CLOSED.
4. D3 manifest sync + memory. Then the next arc is a fresh direction decision (paper writing vs the
   remaining secondary β items) — surfaced to the user with the G3 verdict in hand.

## 5. Risks / questions the plan reviewers should answer

- Is citing the G2-v2 comparator re-scope sufficient to adjudicate the "Tracetest/Cast-style" leg, or
  must the verdict carry a stronger disclaimer (e.g., "the original sentence is NOT met as written;
  met under the re-scoped protocol")? Draft answer: carry both sentences verbatim.
- Is the SS analytical no-errored-span argument safe to include as DISCLOSURE (not evidence), or does
  its presence invite "then why didn't you run it" — and if so, is the honest answer (new
  instrumentation arc, zero marginal claim value against the re-scoped protocol, deferred-item
  ledger) adequate?
- Does presenting TT-natural/SS-natural as ties UNDERSELL the diagnosis/localization edge, or is the
  current modest framing (B-MINOR-4) the right ceiling?
- Any pillar mischaracterized or over-summarized in §1's one-line renderings (check against the
  underlying docs)?
