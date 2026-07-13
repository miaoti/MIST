# Wave R1c — coverage-gap micro-widen (present-but-wrong / transition) — rev 1 (FOR 3-COLD REVIEW)

**Date:** 2026-07-13 · Owner: main_track · Status: **DRAFT — requires ≥3 independent cold reviewers
UNANIMOUS-ACCEPT before any fork engineering (standing /goal rule).**
**Provenance:** user decision 2026-07-13 (AskUserQuestion, option 1 "精准补缺后披露") AFTER the R1b
≥20 all-TT widening was 3-cold-REJECTED (`REVIEW-R1B-RECONCILIATION.md`: 2 REJECT + 1
accept-whose-fixes-gut-it). This plan **SUPERSEDES R1b**. It abandons the ≥20 count target and instead
authors ONLY the cells a coverage-gap enumeration shows are genuinely UNCOVERED, accepts whatever
distinct-site count results, and discloses the shortfall under freeze §5. The freed TT window goes to
the benign-power lift (next wave R1d) + E1 OpenAPI (parallel track) — both OUT OF SCOPE here (§6).

---

## §1 Coverage-gap enumeration (coverage-FIRST, per R1b review B3)

The target set is DERIVED from what the corpus does not yet cover — not retrofitted to a number.
Grid of the 11 captured POSITIVE cases over the frozen `fault.mechanism × readback.modality ×
write_shape` axes (grepped from the 24 live case files, 2026-07-13):

| mechanism | modality | write_shape | covered by |
|---|---|---|---|
| dependency-down | none-durable | partial-aggregate | sockshop-shipping-swallowed-enqueue |
| dependency-down | sql-probe | whole | oteldemo-checkout-lost |
| dependency-down | api-get | whole | teastore-order-depdown |
| flag | api-get | whole | adminroute, adminbasic-contacts, cancel-refund ×2, createaccount-agreement, teastore-maintenance |
| mesh-sever | api-get | whole | teastore-order-meshsever |
| mesh-sever | api-get | partial-aggregate | teastore-orderitems |

**Enum coverage today:** mechanism = {flag, mesh-sever, dependency-down} = **3 of 6** (broker-policy,
code-level, none-durable-as-positive UNUSED); modality = {api-get, sql-probe, none-durable} = 3 of 5
(broker-count, trace-span-presence UNUSED); write_shape = {whole, partial-aggregate} = **2 of 3**
(transition UNUSED).

**The load-bearing gap (oracle-challenge axis, not just enum):** ALL 11 positives are **absence-shaped**
— the lost artifact is a missing row / missing child-collection / un-applied transfer delta. The corpus
has **zero `present-but-wrong` positives**: a read-back that returns a value that is PRESENT but
CONTRADICTS the ack's own echoed claim. This exercises a distinct MIST code path (`valueDiffers` /
`extractProbeValue`, per R1b review B) vs the existence check (`containsKey`) every current case uses.
This is the ONE gap that is both genuinely novel to the oracle AND cheaply reachable.

## §2 Target cells — 2–3, each closing a distinct UNCOVERED cell (NOT a count)

| # | cell (the gap it closes) | site (least-artificial home) | mechanism / modality / write_shape | feasibility |
|---|---|---|---|---|
| 1 | **present-but-wrong value** (flagship; first non-absence positive) | TT ts-price create/update (`basicPriceRate`/`firstClassPriceRate`) — ack echoes the SUBMITTED value, persists a corrupted one | code-level (flag-gated corrupt write) / value-delta / **transition** if update, else whole | **A-VERIFIED feasible** (R1b review A: "SOLID + genuinely distinct… value-delta read-back catches it") |
| 2 | **present-but-wrong on a SECOND SUT** (external validity, answers R1b review C-B2) | a non-TT durable value: TeaStore product/price OR OTel checkout total in accounting.shipping | code-level / value-delta or sql-probe / whole | CANDIDATE — verify the ack-echo/persist seam at authoring; DROP + disclose if the ack is DB-round-trip-derived (no clean masking seam) |
| 3 (optional) | **transition write_shape via lost-UPDATE** (present-but-stale) if not already gotten by #1-as-update | an acked UPDATE that silently keeps the old value (distinct from create-absence) | code-level / api-get / **transition** | OPTIONAL — author only if #1 landed as a create (whole); skip if #1 already covers transition |

**Yield is 2–3 cells, coverage-bounded.** If cell 2's second-SUT seam proves infeasible, the widen is
2 cells on TT+—; if cell 3 is redundant with cell 1, we stop at 2. **We do NOT add cells to reach a
number.** Expected NEW enum-diversity gains (real, in the schema's own vocabulary): mechanism
`code-level` 0→1, write_shape `transition` 0→1, plus the first `present-but-wrong` oracle challenge —
i.e. this micro-widen closes exactly the enum axes R1b review B showed R1b left untouched.

## §3 Engineering (honest, drop-on-infeasible)

- Mechanism = OUR OWN opt-in guard `if (mist.fault.pricecorrupt.enabled) { persist(corruptedValue); }`
  keyed by a JVM system property (the proven adminroute/adminbasic fork pattern), Apache-2.0 §4 change
  notice on every modified file, off-by-default (the SUT ships clean; the flag is lab scaffolding —
  §2.7-A "B1 is not a contribution" framing carried).
- **The ack MUST echo the SUBMITTED value, not the corrupted one** (else the corruption shows in the
  ack = not masked; R1b review A's authoring tell). In-class verification gate (masked-2xx proven live:
  clean success-envelope ack carrying value V ∧ durable read-back = V' ≠ V) BEFORE the case is counted.
- Per cell: edit controller → build fork image (off-peak, graphs at 0) → deploy → probe-first (N≥4 vs
  ribbon round-robin) → in-class-verify → control-first fault+control capture pair → restore base image
  → teardown-verify (C-F7) → next. Batch the ≤3 images in one off-peak build, iterate in one TT window.
- **Second-SUT cell (cell 2)** runs on TeaStore/OTel in its own tenant, same discipline.

## §4 Disclosure machinery (the honesty gates R1b reviews demanded — FIRST-CLASS)

- **M5 — pre-committed claim sentence (pin NOW, S3 §0.5 discipline):** *"The benchmark comprises N
  distinct positive defect sites across K SUTs, of which M are constructed on previously-unused
  endpoints solely to broaden oracle-challenge coverage (present-but-wrong / transition); reported as
  corpus scale and challenge-coverage, NOT as prevalence. Natural masked-write sites remain scarce
  (S3: 0 wild in 1514 acked writes) — the justification for constructed positives."* No RESULT-time
  sentence may exceed this.
- **M6 — one reconciled distinct-site count table (supersedes the 3 disagreeing numbers):**

  | basis | distinct positive sites | note |
  |---|---|---|
  | captured as of 2026-07-13 (post-A2) | **8** | TT 4 (adminroute, adminbasic-contacts, cancel-refund, createaccount-agreement) / TeaStore 2 (order-row, orderitems) / OTel 1 / SockShop 1 |
  | R1 pre-reg row "7" | 7 | pre-A2; +1 orderitems child-collection site (this wave's A2 capture) = 8 |
  | B0 ceiling | 13 | 8 + F-corpus eligible-unoccupied ~2 + this micro-widen ~3 |
  | after R1c (projected) | **~10–13** | **DISCLOSED under freeze §5 as < 20 = a finding, NOT padded to 20** |

- **B1 — schema-honest diversity reporting:** report the new cells' ACTUAL `fault.mechanism` value
  (argued `code-level`; if the reviewers rule it `flag`, report `flag` and rest the novelty claim on the
  read-back-modality/write_shape/oracle-challenge gains, NOT on an invented prose column).
- **A6 — trace-blindness disclosed:** the new services are NOT in the traced-capture wave's 7
  instrumented services → `trace_visibility=trace-uninstrumented`, read-back-oracle-only, do NOT feed
  the E2 trace-comparator arms. This bounds R1c's contribution to S1 site-count + oracle-challenge
  coverage.
- **Two-denominator + tell-free-natural (R8):** unchanged; these new cells are `by-injection`,
  excluded from the natural-exhibit tally; natural masked sites stay scarce = the disclosed finding.

## §5 Acceptance (DoD)
1. 2–3 new cells captured-or-attrition-disclosed, each in-class-verified masked-2xx (present-but-wrong),
   each with a negative control, replay script, typed read-back, digests, license notice,
   `provenance_class=by-injection`, `ground_truth.source=by_construction`.
2. At least ONE `present-but-wrong` positive captured (the flagship gap) on TT price; cell 2 on a
   SECOND SUT captured-or-infeasibility-disclosed (external validity).
3. §4 disclosure machinery delivered: M5 sentence pinned, M6 table published, B1 honest mechanism,
   A6 trace-blindness stated. RESULT discloses the final distinct-site count (~10–13 < 20) as a
   freeze §5 finding.
4. Corpus-wide validator green; every new case schema-valid.
5. README/freeze §6/FILE_INDEX/memory synced; RESULT-r1c + ≥3-cold-review PASSED.

## §6 Out of scope (the reallocation — sequenced next, each its own plan+review)
- **R1d benign-power lift (the BINDING constraint — reviewers' primary reallocation target):** raise
  rateable degradation-shaped benigns from ~12 toward the frozen floor. Needs its own grounded plan
  (exact floor from the frozen max()-formula, per-SUT degradation inventory, decoder-safety). **Grounding
  starts in parallel while THIS plan is in review.**
- **E1 OpenAPI authoring** for TeaStore + OTel-Demo (Gate-4 baseline-grid critical path) — a parallel
  AUTHORING track (no tenant window; can be a subagent job).
- Any NATURAL-discrimination claim; new SUTs; the fork-publication decision (USER, still owed for E6).

## §7 How R1c differs from the REJECTED R1b (traceability)
| R1b blocking finding | R1c resolution |
|---|---|
| B-B1 diversity in a vocabulary the schema lacks (all `flag`) | §1/§4-B1: target set DERIVED from schema-axis gaps; expected real enum gains (code-level, transition) reported in the schema's own fields |
| B-B2 stop-rule self-contradictory | removed — no count target, so no homogeneity stop needed; yield is coverage-bounded |
| B-B3 target-first, coverage-second | §1 is an explicit coverage enumeration; §2 derives from it |
| C-B1 adds ~0 new oracle challenge | §1: `present-but-wrong` is a genuinely new oracle code path (the ONLY one reachable) |
| C-B2 drives to ~80% TT | §2 cell 2 REQUIRES a second SUT; net TT fraction does not rise (2–3 cells, ≥1 non-TT) |
| C-B3 spends the binding week wrong | §6: the freed week goes to R1d benign-power (the binding constraint) + E1 |
| A-1/A-2 food & contacts infeasible | dropped entirely — not in the R1c target set |
| A-3/A-4 ≥4-mechanism unreachable / stop trips | no mechanism-count DoD; DoD is coverage-gap closure |
| M5/M6 no pinned sentence / unreconciled counts | §4 delivers both |
