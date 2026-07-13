# REVIEW RECONCILIATION — Wave R1b (S1 distinct-site ≥20 widening) — 2026-07-13

**Plan under review:** `wave-r1b-site-widening-plan.md` rev 1 (constructed ≥20 all-TT widening).
**Reviewers (independent, explicit models):** A = feasibility/engineering (opus) · B = padding/honesty
crux (sonnet) · C = value/strategic (opus).
**Gate:** standing /goal rule — a new plan executes ONLY on unanimous ACCEPT (fixes foldable).

## Verdicts

| Reviewer | Lens | Verdict |
|---|---|---|
| A | feasibility / engineering | **ACCEPT-WITH-FIXES** — but 2 BLOCKING sub-items are source-verified infeasibilities that gut the diversity headline |
| B | padding / honesty crux | **REJECT** |
| C | value / strategic | **REJECT** |

**RESULT: NOT unanimous (2 REJECT + 1 accept-whose-fixes-gut-the-plan). R1b as scoped DID NOT
EXECUTE.** No fork image was built; no TT window was opened.

## The convergent finding (three independent angles, one conclusion)

All three reviewers, from three different lenses, land on the same place: **the ≥20 constructed
widening is the wrong use of the TT window, and accept-and-disclose ~13 is both the higher-integrity
AND the higher-value move.**

- **B (schema angle):** the plan's "5 mechanism classes / ≥4 mechanisms" is measured in a PROSE
  vocabulary (skipped-persist / fabricated-ack / value-corrupt / partial-aggregate) that does not
  exist in the frozen schema. In the real `fault.mechanism` enum {flag, mesh-sever, broker-policy,
  dependency-down, code-level, none}, the R1b guard pattern (`if (mist.fault.<mech>.enabled)`)
  records as **`flag`** — so 9 of 10 new sites add MORE `flag` and **zero new enum values**. The
  widening pads the exact axis the corpus already under-fills, "while calling it diversity in a
  vocabulary the schema doesn't recognize." The §4(b) homogeneity stop (">2 skipped-persist ⇒ STOP")
  is **already tripped by the plan's own §2** (5 skipped-persist by design) — a gate nobody can
  evaluate isn't a gate.
- **C (strategic angle):** every mechanism in the R1b target set already has ≥1 representative in the
  24-case corpus; the 10 sites add **~0 new oracle-evaluation CHALLENGE types**. Widening drives the
  positive set to **~80% TrainTicket-on-our-own-fork** (16 of ~20), damaging the external-validity
  story the paper must defend. The binding statistical constraint on the headline C3 study is the
  **benign side** (rateable benigns ≈12 < floor 30, need ≈42–43), while the S1 discrimination floors
  are **already MET**; ≥20 is a scale/anti-padding floor the writing rule keeps OFF the headline.
- **A (feasibility angle):** even on success the plan yields only ~17–18 distinct sites (not ≥20),
  and 2 of the 5 marquee mechanisms are **source-verified infeasible**: site 6 (food) —
  `FoodOrder.java` is a FLAT row with no items collection, so "partial-aggregate" has nothing to
  lose; site 10 (contacts USER path) — `ContactsServiceImpl.create()` saves directly (no downstream
  dep to sever) AND writes the **same table + same key as the OCCUPIED adminbasic-contacts site**
  (not a distinct site under C-A4). Honest yield = 8 new sites, 2–3 mechanism classes.

## Two crux facts VERIFIED before reversing the user's decision

1. **Mechanism-enum tally (grep of all 24 live case files, `fault.mechanism`):** flag ×6 · mesh-sever
   ×2 · dependency-down ×4 · none (controls/benigns) rest · **broker-policy 0 · code-level 0.** The
   positive/fault cases use exactly **3 of 6 enum values**. B's "adds zero new enum values" is
   factually airtight — the R1b guard is the `flag` pattern.
2. **Freeze §5 pre-registration (verified, `c2-freeze.md` L253–256):** *"If distinct sites < 20, THAT
   is a disclosed finding (not padded away)."* The corpus PRE-COMMITTED disclose-the-shortfall as the
   honest response to exactly this scenario. B's point stands: R1b would be the first time the
   corpus's response to a floor-collision is "inject through it" instead of "disclose / revise the
   number" — a behavioral inversion of its own pre-commitment, corrosive to a paper whose selling
   point is measurement honesty.

## The reviewers' converged reduced form (the ACCEPT-able path)

Not "walk away from widening entirely," and not "≥20 all-TT." The synthesis all three point to:

1. **Drop ≥20 as a target.** Re-derive any new sites from a **coverage-gap enumeration** — which
   `mechanism × read-back-modality × write-shape` cells are NOT yet covered ANYWHERE in the existing
   corpus (all SUTs). That yields perhaps **2–5 genuinely-novel cells**, not ten.
2. **Author only those novel cells**, sited on whichever SUT makes each **least artificial**, split
   across **≥2 SUTs** (not all-TT). A confirms the genuinely-distinct + feasible ones:
   **value-corrupt** (persist-a-wrong-value, echo the submitted value → value-delta read-back; TT
   price is SOLID) and a real **delayed-vs-lost pending-present** (count-delta). These are
   substantively different oracle code paths (`containsKey` vs `valueDiffers`/`extractProbeValue`) —
   genuine oracle-evaluation coverage, not padding.
3. **Accept whatever count results (~15) and DISCLOSE** the shortfall under the freeze §5
   pre-registered branch. Pre-commit the exact RESULT sentence now (S3-style §0.5 discipline).
4. **Reallocate the freed TT week to the actually-binding work:** the benign-power capture wave
   (12 → ≥30 degradation-shaped rateable benigns — the C3 study's binding constraint) and E1
   OpenAPI authoring for TeaStore + OTel-Demo (critical path to the Gate-4 baseline grid).

## Fold-in fixes (if any micro-widen proceeds)

- B1: report diversity against the **schema** fields (predominantly `flag`), not the invented prose
  column; drop the "5 mechanism classes" framing.
- B2: rewrite the homogeneity stop against a stated baseline so it can actually fire.
- B3/M-C: select sites by **new-oracle-challenge coverage**, not service count.
- A-1/A-2: site 6 (food partial-aggregate) and site 10 (contacts) are DEAD as specified — drop or
  re-spec (food → async-lost only if the broker path is stood up; contacts → drop).
- A-6: the 9 new services are NOT in the traced-capture wave's 7 instrumented services →
  `trace-uninstrumented`, read-back-oracle-only, do NOT feed E2 trace-comparator arms. Disclose;
  this bounds any widening's contribution to S1 site-count alone.
- M5/M6: pre-commit the claim sentence; produce ONE dated reconciled site-count table (the docs
  currently disagree: R1b §2 "~8", R1 pre-reg row "7", B0 ceiling "13").

## Disposition

Per the /goal rule, R1b as scoped is **rejected for execution**. Because the reviewers' converged
recommendation **reverses the user's explicit 2026-07-13 "widen to ≥20" decision** — and does so on
the argument that the user's own meta-goal ("do all experiments as perfectly as possible") is BETTER
served by the reduced form (for a benchmark-and-honesty paper, "perfect" = maximally defensible, and
injecting-to-a-quota is the opposite) — this is carried back to the user with the recommendation:
**coverage-gap micro-widen (2–3 genuinely-novel cells across ≥2 SUTs) + disclose ~15 + reallocate the
week to benign-power + E1 OpenAPI.** The user's decision was made on a premise ("widening = 最完美")
that these reviews show to be false; the correction is owed to them before proceeding.
