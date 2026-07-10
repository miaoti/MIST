# REVIEW — wave-3a-plan.md (DRAFT rev 1) — Reviewer C (hostile program-committee / scoping lens)

**Reviewer:** C — cold, no shared context with author or other reviewers; formed from repo files only
(no cluster/network commands run).
**Target:** `debug/a-main/c2c3/wave-3a-plan.md` (DRAFT rev 1).
**Adversarial frame:** if this wave executes as written and its outputs land in an A-conference
paper's benchmark section, where does a hostile reviewer draw blood?
**Evidence base:** `c2-freeze.md` (§1/§2/§4/§5/§6 incl. rev-2.1 + T9 rows), `benchmark/README.md`,
`tenancy-window-result.md`, `c2-depth-survey.md` (incl. 2026-07-10 correction blocks),
`step2-execution-checklist.md`, case files `TT-cancel-refund-fabricatedack-001.json`,
`TT-adminroute-lostwrite-001.json`, `oteldemo-checkout-lost-001.json`,
`bookinfo-ratings-benign-001.json`, plus corpus-wide greps of `mist_bindable` and
`fault.mechanism` over `benchmark/cases/*.json` (18 files).

## VERDICT: ACCEPT-WITH-FIXES

The wave is correctly scoped (live-tenant subset of 3a, no MIST runs, no TT revival), its collapse
order is right, its §7 count arithmetic checks out (18→23, 10 pos / 13 neg; items 1–2 only: 22,
10/12), and items 2–3 carry earned premises (mesh-503 rider VERIFIED-MASKED live in Phase C; the
kafkaQueueProblems trap probe-gated with an explicit not-authored branch). But the plan's single
most load-bearing pin — the item-1 `mist_readback` cell convention, which the author explicitly
asked reviewers to check — is self-contradictory as written and, in the reading its final clause
lands on, contradicts the corpus's own precedent AND the T9 denominator rule in a way that would
invert the case's purpose. That is blocking. Four majors follow on floor arithmetic, a grep-refuted
factual sentence, self-test reporting language, and mechanism-floor accounting.

---

### C-B1 [BLOCKING] — The item-1 `mist_readback` cell pin is incoherent as written, and the value it lands on would EXCLUDE the wave's flagship case from MIST's future recall denominator

Plan §1 (lines 27–31): *"still recorded as a TARGET — MIST does not run this wave; the CELL stays
`not_applicable`? NO — pin: `mist_bindable=true` and the cell stays a design-target under the same
rev-2.1 R2/R3 discipline used everywhere: … MIST didn't run ⇒ `not_applicable` recorded, FLAG
design target preserved in notes per T1."*

This paragraph asserts both branches: it answers "`not_applicable`? NO" and then pins
"`not_applicable` recorded" three clauses later. A frozen pre-registration cannot be
readable-both-ways.

Worse, the branch it finally lands on (`not_applicable` in the cell, FLAG target in notes) is wrong
on three independent grounds:

1. **It contradicts the corpus's own precedent for bindable read-backs.** The two captured,
   bindable, MIST-not-run-that-wave analogues record the design target IN the cell:
   `TT-cancel-refund-fabricatedack-001.json:83` → `"mist_readback_oracle": "flag"` and
   `TT-adminroute-lostwrite-001.json:86` → `"mist_readback_oracle": "flag"`. The freeze §2 defines
   the `mist_*` `oracle_expectation` columns as *"TARGETS, measured at eval, NEVER ground truth"* —
   target-in-cell is the schema's semantics; the measured verdict has its own home
   (`artifacts.mist_verdict`, `@eval`, null until MIST runs). The plan's claim that
   `not_applicable`-when-not-run is "the same discipline used everywhere" is refuted by the two
   rows it is imitating.
2. **`not_applicable` is already a term of art with three pinned meanings, none of which is
   "bindable but not yet run":** (a) no input as-deployed (rev-2.1 R2: trace oracle on a
   trace-uninstrumented deploy), (b) modality unbindable at the pin (T9 boundary: sql-probe, HTML),
   (c) Branch-B traced-but-not-run for `mist_trace_shape` specifically, with its named note. Item
   1's case is none of these: the modality is `api-get` JSON, `mist_bindable=true` — the missing
   piece is the OTel-Demo enablement package (OpenAPI/registry/triples), a 2.75 decision. Adding a
   fourth, unnamed species under the same enum value is exactly the "cells mean different things in
   different cases" inconsistency a hostile PC audits for.
3. **The T9 reporting rule makes it self-defeating.** Freeze §6 T9 row: boundary `not_applicable`
   cells *"are excluded from MIST recall denominators and reported as their own applicability
   row."* Record `not_applicable` here and the corpus's first non-TT bindable positive — the datum
   item 1 exists to create — is excluded by your own rule from the very denominator it is supposed
   to eventually join. The paper table would show the flagship case as MIST-inapplicable while the
   plan's §0 sells it as the first MIST-bindable non-TT positive. A reviewer diffs those two
   statements and stops trusting the cells.

**Fix (concrete):** rewrite the pin to one unambiguous sentence following the
fabricatedack/adminroute convention: `mist_readback_oracle: flag` (design target in the cell, per
freeze §2 target semantics), `artifacts.mist_verdict: null`, notes carry "MIST not run this wave —
OTel-Demo enablement is a 2.75 decision; this target is never tallied as a result until measured
(rev-2.1 R2/R3)". `mist_trace_shape_oracle: not_applicable` Branch-B with the standard
traced-but-not-run note (deploy is natively traced), matching the traced-wave/tenancy precedent. If
the authors instead want a new "bindable-but-unenabled ⇒ not_applicable" convention, that is a
freeze §6 amendment which must ALSO re-touch the two TT bindable rows for consistency — out of this
wave's scope and not recommended.

### C-M1 [MAJOR] — §0's supporting sentence is factually wrong twice (grep-refuted); the headline claim survives, the sentence does not

Plan §0 (lines 9–10): *"The 18-case pilot (8 pos / 10 neg) has ONE case with a MIST-bindable
read-back outside TT (none — TT's 4 discriminating positives are the only `mist_bindable=true`
rows)."*

Grep over `benchmark/cases/*.json` (18 files): **all 7 non-TT cases are `mist_bindable: false`**
(oteldemo ×2, teastore ×2, sockshop ×2, bookinfo ×1) and **ELEVEN TT rows are
`mist_bindable: true`** — the 4 discriminating positives PLUS the tell-bearing cancel-natural
positive PLUS 4 clean controls PLUS 2 S2 benign traps (dedupe, noop-modify). So: "ONE case …
outside TT" contradicts its own parenthetical "(none)", and "the only `mist_bindable=true` rows"
is false by 7 rows. The intended claim — item 1 adds the FIRST non-TT bindable-read-back positive —
is TRUE and verified; the sentence supporting it is the kind of checkable-and-wrong statement that,
quoted into a paper or artifact appendix, costs the whole table its credibility.

**Fix:** replace with: "All 7 non-TT cases are `mist_bindable=false`; the only bindable-read-back
*discriminating positives* are TT's 4 (11 TT rows total are bindable, incl. controls/benign). Item
1 adds the first non-TT `mist_bindable=true` positive." Additionally state the value dependency in
one sentence: the claim available at wave close is "the corpus CONTAINS a non-TT machine-bindable
read-back positive (design target preserved)"; the claim "MIST catches a non-TT positive" is earned
only when MIST runs (2.75 enablement + 2.5/E2), and the plan should say the case is authored to be
first in line for that run.

### C-M2 [MAJOR] — Floor honesty: the concession is correctly placed but under-specified; state the remaining-gap arithmetic instead of "closes later"

Plan §0 concedes "~1 distinct SITE" and disclaims floor closure — right instinct, correctly placed
at the top. But "the §5 distinct-site floor closes later via the TT-revival tranche +
Boutique/remaining survey sites" is a vague promissory note, and vagueness is precisely what makes
"you cherry-picked easy tenants" stick. The numbers, from the corpus + freeze §5 + survey:

- **Current distinct defect sites ≈ 7**: TT 4 (cancel→refund [natural+fabricatedack = ONE site per
  the C-A4 anti-padding rule], createaccount, adminroute-add, adminbasic/contacts-add) + SS 1
  (shipping-enqueue) + TeaStore 1 (order-confirm) + OTel 1 (checkout→Kafka→accounting).
- **This wave adds exactly +1** (OTel EmptyCart). Item 2 adds **0 sites** (mesh-sever on the SAME
  order-confirm site = mechanism-multiplex, which §5 explicitly refuses to count). Item 3 is a
  negative. Post-wave: **8 sites vs the 15–18 / 21–28 §5 bands**.
- **Closure is carried almost entirely by the TT F-corpus tranche** (floor 6 / target 10, each
  requiring B-m6 in-class verification — unstarted) + SS carts (~1) + TeaStore order-items (~1) +
  Boutique (exactly 1, S1-minor). Ceiling ≈ 17–21; if the F-corpus lands at floor 6, the total
  lands **under 20 ⇒ the freeze's disclosed-finding branch fires**. That branch is genuinely live,
  not hypothetical.

**Fix:** replace the "closes later" clause with a 3–4 line gap statement in §0: current site count
(named), wave delta (+1, and why item 2 counts 0), the post-wave 8-vs-15/28 arithmetic, and which
future tranche carries how many sites incl. the live <20 disclosed-finding branch. Change "~1" to
"exactly 1". Then add the one sentence that actually defuses the cherry-picking charge: the wave is
ordered by tenancy economics (these SUTs are UP, TT is at 0; TT-revival is the expensive tranche),
not by ease — with the gap number said out loud, sequencing-by-cost is a defensible engineering
choice rather than a concealment.

### C-M3 [MAJOR] — Item-3 self-test: strength, but only with reporting language pinned NOW and the case's own MIST targets pre-registered

The disclosed self-test (the benchmark's presence column expected to FP on the pending-vs-missing
S2 case) is publishable-honest — comparator-column FP characterization is what the S2 stratum is
FOR, and the tenancy wave already measured presence-FP on bookinfo. But as drafted it hands a
hostile reviewer the "authors grade their own homework: they DESIGNED a case their comparator
fails while their own tool conveniently never ran on it" attack. Four pins, all cheap:

1. **Reporting language, pinned in the plan (not decided at write-up):** the case counts toward
   per-column comparator FP-mode characterization ONLY; it enters **no MIST-vs-comparator
   differential or win tally** until MIST's own columns are MEASURED on it (2.5/E2 or post-2.75).
   A measured comparator FP set against an unmeasured MIST target is not a comparison — this is
   the same discipline the tenancy amendment applied to the pair-separation claim; instantiate it
   for this case by name.
2. **The plan is silent on the case's own `mist_readback`/`mist_trace_shape` targets — the deeper
   self-test.** A fixed-window read-back absence check would ALSO FP on pending-not-missing;
   MIST's pre-registered discriminator is the TIMEOUT_ABSENT ≠ OBSERVED_COMPLETE_ABSENT split
   (1.9 UX freeze: defect requires OBSERVED_COMPLETE_ABSENT; TIMEOUT_ABSENT reported separately).
   Note the sharp edge: on a natively-traced SUT the entry trace may look complete while the row
   is still pending — whether the frozen QuiescenceGate→verdict mapping yields `no_flag` or an FP
   here is exactly what must be pre-registered from the mapping + the probe-measured delay,
   BEFORE capture, in the probe-then-freeze round. If MIST later also FPs, that is reported as
   MIST's FP — the trap traps everyone equally, which is what makes it calibration rather than
   sandbagging. Say so in the plan.
3. **Optics line:** state that `tracetest_presence` is currently the strongest measured comparator
   in the corpus (ran-and-caught on sockshop-enqueue, OTel checkout, and both breadth positives —
   README/tenancy result). Documenting the FP mode of your strongest baseline is calibration of a
   live competitor, not humiliation of a strawman. One sentence kills the homework charge.
4. **Column-family wording:** "OUR OWN presence column" blurs families. It is the benchmark's
   *authored comparator* column (arm-3 family, per-endpoint authoring cost recorded), not a MIST
   column. Keep the self-test framing; fix the possessive so the paper cannot be read as "MIST
   FPs" when the comparator does.

### C-M4 [MAJOR] — Mechanism-diversity accounting: after item 2 the corpus contains TWO TeaStore mechanisms, and the plan's set-notation papers over it

Grep over `benchmark/cases/*.json`: current corpus `fault.mechanism` values = flag ×6,
dependency-down ×3 (bookinfo, sockshop, oteldemo), none ×9. **TeaStore's corpus mechanisms = {flag}
today; after item 2, {flag, mesh-sever} = 2.** There is NO TeaStore dependency-down case in any
`capture_status` — "dependency-down SPECIFIED-UNSOUND-disclosed" (plan line 67) is the status of a
*survey row* (`c2-depth-survey.md` 2026-07-10 correction: no-PVC wipe destroys the absence
evidence; "order-row × DB-down: specified-only"), not of any corpus case. Meanwhile checklist 2.2
already asserts "the broker-less min-3 floor stands on {flag, dependency-down, mesh-sever}".

Per the freeze R1 wording ("≥4 distinct `fault.mechanism` VALUES … as applicable; broker-less min
3"), the natural reading is corpus composition — cases carrying those values. A floor whose third
leg has zero cases and is admitted uncapturable on this deploy is floor-met-on-paper; a hostile
reviewer will count case rows, get 2, and quote your own anti-padding language back at you.

**Fix (choose one, in-wave):**
- *(preferred, ~30 min, no cluster time)* Author `teastore-order-depdown-specified-001` as
  `capture_status: specified`, label-by-construction, mechanism `dependency-down`, with the
  UNSOUND-on-THIS-deploy disclosure in-file plus the deploy precondition under which capture WOULD
  be sound (PVC-backed DB — the mechanism is source-true and deploy-shape-contingent, not
  intrinsically unsound). Then the min-3 claim points at three actual corpus rows (2 captured + 1
  specified-with-disclosed-capturability), and rev-2.1 R2/R3 already guarantees the specified row
  is never tallied as a result.
- *(fallback)* Re-word plan + checklist to "TeaStore floor = 2 captured mechanisms + 1
  survey-verified-but-uncapturable-on-this-deploy (disclosed)" and record in the freeze §6 that the
  min-3 floor is read over survey-verified mechanisms with disclosed capturability — i.e., accept
  and disclose the weaker reading rather than implying the stronger one.

Either way, fix §0's "the min-3 floor's live evidence": item 2 provides live evidence for 2 of the
3 legs.

### C-M5 [MAJOR] — Item 1 lacks the refutation branch item 3 has, on a flag set with a DOCUMENTED version-skew incident

Item 1's premise chain (checkout discards `emptyUserCart`'s return; `cartFailure` routes EmptyCart
to a throwing `_badCartStore`) is survey-verified against MAIN-branch source; the deployed app is
2.2.0, and the D3c re-freeze of THIS EXACT flag set already produced version-skew corrections
(intlShippingSlowdown gone; paymentFailure re-graded percentage). This week's record also shows
premises refuted live twice (D3b graceful-ad; C3b recommender cold-start). The plan requires N≥4
probes "expect 200 acks + non-emptied carts" but never says what happens if the probes REFUTE the
masked semantics on 2.2.0. Item 3 carries an explicit probe-gate + not-authored branch; item 1
does not.

**Fix:** one sentence mirroring item 3: "if the probe round refutes the 200-ack + non-emptied-cart
semantics on the deployed 2.2.0, the case is NOT authored — survey corrected with a dated row,
disclosed finding (C-m8/R9 precedent)."

### C-m6 [MINOR] — Budget: credible-optimistic; state where doc time lives, keep the collapse order

1.5 d is defensible given the matured runbook (frozen scorer, Phase-D selector precedent, mesh
mechanics de-risked by the rider, kafka-recovery runbook in force, and the nacos/ribbon failure
class N/A with TT at 0). But the just-closed wave's record shows (a) a full re-capture forced by
one hygiene rule (OTel attempt-1 export window) and (b) per-item close-out overhead the plan itself
mandates (§5: FILE_INDEX + freeze §6 + README counts + survey corrections + validator + per-item
commit) historically ≈ 1–2 h/item. Either budget "1.5–2 d" or state that each 0.5 d includes its
close-out hour. The collapse order 1→2→3 is correct — the bindable positive is the unique paper
asset, mesh-sever second, the S2 trap genuinely deferrable (its taxonomy is already partially
measured operationally in Phase D).

### C-m7 [MINOR] — Item 2 undersells itself: first mesh-sever case in the ENTIRE corpus

Grep-verified: zero `mesh-sever` cases exist corpus-wide today. Item 2 is not merely "TeaStore's
second captured mechanism" — it is the corpus's FIRST mesh-sever case of any status, i.e. a
cross-SUT mechanism-diversity datum (captured mechanisms would become {flag, dependency-down,
mesh-sever}). Say so in §0; it is the honest counterweight to C-M4's deflation and it strengthens
the wave's value-per-day argument.

### C-m8 [MINOR] — Item 3's refutation branch conflates two opposite outcomes

"Rows actually lost" and "delay < export window" both currently land in "case NOT authored." The
second is a clean no-case. The first is a BANK, not a bust: a vendor flag that measurably LOSES
acked writes refutes the by-docs benign label AND hands you an S1-positive candidate
(vendor-flag provenance) plus a survey correction. Pre-register the split: loss ⇒ STOP, dated
survey correction + decision point (S1 candidate authored under its own discipline or deferred),
never silently subsumed under "not authored."

### C-i9 [INFO] — Scope discipline, precedent conformance, and arithmetic: checked, clean

- **No smuggling:** comparator trace-cell scoring at capture time is the established
  traced-wave/tenancy practice (frozen pre-committed scorer), not step-6 E2 work; MIST runs and
  tool code are correctly out (2.75/E2, user-gated); S3/M-yield out (step 4/5); TT revival out
  (2.5/E2 tranche). The out-of-scope list matches the checklist ladder.
- **Boutique deferral is honest:** the survey's own quota row is "1 (S1-minor, disclosed)" and
  "optional"; the plan's "below the write-path floor; separate small window later" matches, and
  the wave stays inside its declared "live-tenant subset of 3a" framing (3a is not declared
  closed). C-M2's gap arithmetic should still name Boutique's exact contribution (1 site).
- **§7 arithmetic verified:** 18→23 = +2 S1 positives +2 controls +1 S2 ⇒ 10 pos / 13 neg;
  items 1–2 only ⇒ 22, 10/12. Correct, and the "report as counts, never bare S1" rule is honored.
- **Item-2 reviewer question (2-hop Envoy fragments):** NOT scoring them is the right call.
  With exactly 2 sidecars there is no persistence-side span even on control, so a presence target
  would be fabricated-unsatisfiable; the bookinfo contrast (fully meshed, Telemetry 100%) is the
  correct asymmetry argument. Keep the disclosure in the case notes and record the sidecars-on
  deploy string in BOTH case JSONs (the plan's parity pin already implies this).
- **Item-1 selector discipline:** canary-on-healthy-trace then freeze-before-fault-legs matches
  the "names bound from control/canary traces only" precedent; the present-but-erroring inversion
  hypothesis is properly hedged as probe-adjudicated. For item 3, T2 family-validation can be
  satisfied by reference to the existing `oteldemo-checkout-control` capture (same selectors/path)
  — state that in the plan rather than leaving T2 coverage implicit for a control-less S2 capture.

---

## Summary for reconciliation

- **C-B1 [BLOCKING]** — item-1 mist_readback pin: self-contradictory text; final clause contradicts
  fabricatedack/adminroute precedent and T9's denominator rule (would exclude the flagship case
  from MIST recall). Fix: target-in-cell `flag` + null `mist_verdict` + not-run note; trace cell
  Branch-B.
- **C-M1 [MAJOR]** — §0 bindable-rows sentence grep-refuted twice (0 non-TT bindable, 11 TT rows
  not 4); reword + state the runs-later value dependency.
- **C-M2 [MAJOR]** — replace "floor closes later" with explicit gap arithmetic (7→8 sites vs
  15–28; F-corpus carries closure; <20 disclosed-finding branch is live) + tenancy-economics
  sentence.
- **C-M3 [MAJOR]** — item-3 self-test: pin now that the case feeds comparator-FP characterization
  only and no MIST-vs-comparator tally until MIST measured; pre-register the case's own MIST
  targets (TIMEOUT_ABSENT vs OBSERVED_COMPLETE_ABSENT) at probe-then-freeze; add
  strongest-comparator optics line; fix "our own" column-family wording.
- **C-M4 [MAJOR]** — min-3 mechanism floor: TeaStore corpus would hold 2 mechanisms; author the
  specified dependency-down case (preferred) or downgrade the wording + freeze-amend the floor
  reading; fix "min-3 floor's live evidence".
- **C-M5 [MAJOR]** — add item-1 probe-refutation branch (2.2.0 version-skew precedent on this very
  flag set).
- **C-m6 [MINOR]** — budget 1.5–2 d or state close-out time inside each 0.5 d; collapse order
  endorsed.
- **C-m7 [MINOR]** — item 2 is the corpus's FIRST mesh-sever case anywhere; say so.
- **C-m8 [MINOR]** — split item-3's refutation branch: rows-lost ⇒ S1-candidate + survey
  correction, not silent non-authoring.
- **C-i9 [INFO]** — scope/ladder conformance, Boutique deferral, §7 arithmetic, 2-hop-Envoy call,
  and selector discipline all check out; minor T2-by-reference note for item 3.
