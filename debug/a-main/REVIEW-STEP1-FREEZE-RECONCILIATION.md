# Step-1 freeze wave — 3-cold-review RECONCILIATION + re-freeze disposition

**Reviews:** A fidelity (`REVIEW-STEP1-FREEZE-A-fidelity.md`, **ACCEPT-WITH-CHANGES**), B completeness
(`REVIEW-STEP1-FREEZE-B-completeness.md`, **REJECT → re-freeze**), C adversarial
(`REVIEW-STEP1-FREEZE-C-adversarial.md`, **BORDERLINE-lean-REJECT**). Consensus: the narrative core
(claim, sweep, license, applicability) is sound and **both load-bearing source claims VERIFY against
upstream** (A WebFetched TeaStore + OTel-Demo + Bookinfo); but the *machinery* freeze needs one
focused **re-freeze** before step-2 population commits to it. This doc records verdicts, the
cross-review synthesis, and the disposition of every finding (where each is fixed).

## Headline synthesis — the sharpest issue (C-Attack-2 × A-source-check)
C's strongest reject: the "masked/silent" class carries machine-readable tells in its natural form
(TeaStore `-1`, TT `{1,"error"}`), so a trivial body oracle catches them and the clean-silent cases are
disproportionately constructed → the class is thinner than claimed; the schema records `trace_visibility`
but **no ack-content-visibility axis**. **A's source verification partially REFUTES the example:** TeaStore's
`-1` is NEVER echoed to the client — `placeOrder` clears the SessionBlob and returns a clean 200
(`REVIEW-A` VERIFIED §1) → TeaStore natural is genuinely **tell-free**. But the *schema gap* C names is
real (we cannot PROVE per-case tell-freeness without the axis), and TT `{1,"error"}` IS tell-bearing
(g3-result grades it a detection TIE). **Disposition:** adopt C's fix (add `ack_content_visibility`,
segregate tell-bearing cases from the primary positive denominator, add a tell-free floor, resolve the
"success-shaped body" rubric) AND fold A's correction (TeaStore natural = a positive tell-free exhibit).
Net effect: the attack converts into a *measured detectability spectrum* — "first to quantify how
detectable this class actually is" — which strengthens the "prevailing methodology filters this out"
positioning.

## Convergences (≥2 reviewers, or confirmed-real) → all BLOCKING for the re-freeze
| # | finding | reviewers | fix location |
|---|---|---|---|
| R1 | **Mechanism enum can't express the survey's mechanisms** (TeaStore needs DB-down/toggle/mesh×2; enum has no `dependency-down`; broker-less TeaStore caps at 3 vs the ≥4 floor). Freeze also silently dropped the plan's "as applicable" qualifier. | A-B1, B-B3 | c2-freeze §2 enum + §5 floor; amendment |
| R2 | **Pre-existing `debug/a-main/benchmark/` scaffold** (JSON schema + rubric v0.1.0 + 6 seed cases) is incompatible + methodologically opposed (labels-from-runtime) and unreferenced; freeze regressed a real validator to prose. | B-B1 (confirmed by inspection) | supersede + migration map + adopt `oracle_expectation`/anti-circularity/`fault_class`/`capture_status` + ship the machine JSON schema |
| R3 | **S1≥45 (and S2≥35) floor doesn't close**; contradicted by the freeze's own survey (README's "3×7 endpoints" refuted: TeaStore=1 write, Boutique=0); TT/SS uncounted. Honest tally ~37–41. | A-M7, B-B2 | honest S1/S2 recount folding TT/SS from committed prior work; disclose shortfall + stop-and-replan branch |
| R4 | **Observable model too narrow** — SS flagship has NO durable read-back (trace-only, `mist_dataintegrity=not_applicable`); OTel uses out-of-band psql probe. Frozen schema forces a durable `observable_pin`. | B-B4, A-M3 | typed `readback: {modality, locator, expect_*}` incl. `trace-span-presence`/`sql-probe`/`none-durable` + MIST-bindability flag |
| R5 | **Rater rubric ≠ frozen rubric** on admissibility of the observed state (the "not a fork" guarantee is violated on the crux). | A-M4, B-B1(rubric) | observation-vs-verdict split, verbatim in BOTH files |
| R6 | **Blindness leak** — S1 calibration always has a clean-run twin, S3 wild usually doesn't → clean-run presence is a stratum tell. | A-M5, B-M2 | normalize the clean-run field across all rater-facing cases; add §0 invariant |
| R7 | **κ pooled n≥50 unreachable in the S3-scarce branch + calibration-inflated.** | A-M6, B-M1 | S3-only κ PRIMARY (Clopper–Pearson n<10) + pooled secondary w/ inflation caveat; size calibration so pooled≥50 is free given S1+S2≥80 |
| R8 | **Ack-content-visibility axis missing** (C-Attack-2, see synthesis). | C, A(§1 correction) | `ack_content_visibility` field + segregate tell-bearing + tell-free floor + resolve "success-shaped body" |

## Single findings folded (MAJOR/MINOR)
| id | finding | fix |
|---|---|---|
| A-M1 / B-M6 | S3 scoring undefined (no injection/twin); S3 reproducibility collides with automated-replay acceptance | add an S3 branch to §4 (score against the recorded transcript, observed-flag path); S3 reproduction = captured-artifact + best-effort replay, non-determinism documented |
| A-M2 | "including-underspecified" precision never defined | define: included precision = genuine-fires / (genuine+benign+underspecified fires) |
| A-M8 | OTel flagship on the construction-vs-contract seam | disclose S1-by-injection genuineness = injection-induced divergence from the control (a bar distinct from the rubric's contract bar); attach OTel order→accounting contract-grounding where available |
| B-M4 | no arm-3 authoring-cost schema home | add `comparator_configs[].authoring_cost:{minutes,endpoints_covered,notes}` |
| B-M5 | `trace_visibility` conflates by-construction vs un-instrumented | split → `trace-invisible-by-construction` \| `trace-uninstrumented`; pre-register the TeaStore-Kieker-exclude branch |
| B-M7 | partial/aggregate writes uncovered by "did not land" | add `write_shape: whole \| partial-aggregate \| transition` + a worked partial example |
| B-M8 | no IRB/ethics path on the longest-lead item | add IRB/exemption determination as a §7 precondition (surface to user); state the expected-exemption rationale |
| B-M3 | M-yield audit cases absent from the rater package | fold the M-yield audit sample into §0's normalized mix + the ballot; size + blindness stated |
| C-A1 | "first" leans on Cast's non-openness; Filibuster no fresh row; claim string opens "first" despite the writing rule | add a Filibuster differentiation row; note the paper LEADS with the study (claim string is for-the-record); strengthen Cast framing (openness + benign-split + oracle-eval labels Cast lacks) |
| C-A3 | comparator suite all trace-span-family; no invariant/contract (Pact/Dredd/AGORA+) or differential arm | add a contract/invariant comparator arm to E2 (plan-level); mark the TraceAnomaly re-scope PROVISIONAL-until-run |
| m (several) | 7th-field numbering; `negative_control` "true negative" bakes outcome; NonBalanced→LoadBalanced cite; claim-string drift; rater hours vs case-count; MIST-commit pin criteria; scoring-harness license; Gate-4=3 wording; F-subset in-class check; eligibility/calibration disjoint | folded as small edits across the docs |

## Re-freeze execution order (this reconciliation → the fixes)
1. **c2-freeze.md** — R1, R2(schema side), R4, R8, A-M1, A-M2, A-M8, B-M4, B-M5, B-M7, m1/m2; amendments §6.
2. **debug/a-main/benchmark/** — update `schema/fault-case.schema.json` to the harmonized machine schema (adopt `oracle_expectation`+anti-circularity+`fault_class`+`capture_status`; add the typed readback, ack-content-visibility, write_shape, authoring_cost); supersession banner + seed-case migration map in README; log in freeze §6 (R2).
3. **c3-rater-materials.md** — R5, R6, R7, B-M3, B-M8, m2(hours), m7(eligibility disjoint) (R5).
4. **e-sut-applicability-matrix.md + c2-depth-survey.md** — R1 mechanism re-derivation, R3 honest S1/S2 recount, B-M5 trace-visibility split, C-A2 tell-free exhibit, m3(cite).
5. **c2-claim-sweep.md** — C-A1 Filibuster row + Cast framing + lead-with-study note.
6. Reconciliation committed first (this file) as the durable roadmap; then the fixes; FILE_INDEX + amendments per change.

**Standing rules honored:** frozen-doc changes are disclosed amendments (c2-freeze §6); no file deletion;
branch `main_track`; no Co-Authored-By; artifacts English.
