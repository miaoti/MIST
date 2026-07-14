# Wave R1d — benign-power lift (S2) — rev 2.1 (CONFIRMATION PASS PASSED — residuals folded)

**Date:** 2026-07-13 · Owner: main_track · Status: **CLEARED for capture (Phase 0 first). CONFIRMATION
PASS PASSED** — all 3 reviewers resumed (C CONFIRM-ACCEPT; A + B ACCEPT-WITH-RESIDUAL, all textual/
foldable, none requiring another review round; B: "on those two folds landing, this is a confirm").
rev 2.1 folds the residuals: R1d-B R-1 (decoder-precision — the ≥8 presence-defuser shape is the
fresh-key-reject-that-renders-EMPTY, we have 0 not 2; dedupe/no-op render PRESENT = delta/body traps,
§2); R1d-B/C R-2 (the ≤40%-per-shape ceiling does NOT bind the write-acked-absent defuser family, §5);
R1d-A (calibration bounded by BOTH benign AND rateable-genuine supply, §4). **Authoring to-do: the
structured shape-taxonomy field must be ADDED to `fault-case.schema.json` (it does not exist yet).**
History: rev 1 had real errors (a confabulated SockShop story, a mischaracterized TeaStore toggle, a
mis-assigned decode-safety floor) fixed in rev 2 per `REVIEW-R1D-RECONCILIATION.md`.
**Provenance:** the binding-constraint wave (all R1b+R1c+R1d-A/C reviewers: the C3 study's gate is the
benign side). Positive-side widening is CLOSED (`REVIEW-R1C-RECONCILIATION.md`).

---

## §1 Demand, supply, and the RE-SCOPE

- **Demand (frozen): S2 ≥ 35 distinct benign traps** (`c2-freeze.md` §5). This **subsumes** the ~34
  calibration benign draw (calibration = max(30,50−|S3|)=50 at |S3|=0, benign-skewed ≥2:1; it draws
  FROM the S2 pool; mechanical FP-rate runs don't consume cases). The grounding's "~42-43" **double-
  counted** FP + calibration as disjoint pools → a dated freeze §6 note will reconcile the number
  (OWED). **Supply today = 4** benign traps (dated-correct the S3 RESULT's "12", which folded in
  non-rateable items).
- **RE-SCOPE (R1d-C):** ≥35 is ALREADY pre-registered as "structurally unreachable → disclose the
  shortfall" (freeze §5 + the R1 row). So this wave targets the **decode-safety MINIMUM**, not volume-
  to-35. Inducing benigns is genuine construction ONLY up to what decode-safety structurally requires
  (nature cannot supply masked-absent-then-healed writes — that is the S3-scarce phenomenon); inducing
  volume beyond that is padding-in-a-benign-hat. **Revised size ≈ 8–10 captures**, then DISCLOSE the
  ≥35 shortfall + the calibration/pooled-κ consequence.

## §2 Decode-safety — SUBSTRATE-ALIGNED honest framing (rev-1's central error, B-BLOCKING-1)

Verified against `b4_harness.render()` (renders EVERY observation verbatim, no cap/truncation) + the
real `w120-sidecar.json` (ends in a PRESENT observation). Consequences, corrected:

- **A rater sees the full transcript.** An eventual-present benign renders PRESENT (heals) → the rater
  sees the write LANDED → it is a **present⇒benign** case, a DISCLOSED structural tell — it does NOT
  defuse "absent⇒genuine". (rev 1 wrongly claimed the opposite.)
- **The ≥8 floor targets the PRESENCE decoder, and its qualifying shape is the FRESH-KEY soft-reject
  that renders EMPTY** — a fresh-key 2xx write whose body signals rejection, nothing persisted, nothing
  prior → read-back EMPTY, ABSENT like a genuine loss → dilutes "absent⇒genuine" toward P≈0.50.
  **Precision correction (R1d-B R-1): dedupe / no-op-modify do NOT render empty** — dedupe reads the
  first row PRESENT (trap = count-delta-zero), no-op reads the row PRESENT-unchanged (trap = zero-delta);
  they correctly trap the DELTA and ACK-BODY decoders, NOT the presence decoder → they do NOT count
  toward the presence-defuser ≥8. **Recount: fresh-key-reject presence-defusers = 0 today → need ~8;
  the 2 dedupe/no-op are kept as delta/body traps (separate count).** **eventual-present ≥2** (w120 + ~1)
  render PRESENT = the disclosed present⇒benign tell. **Phase 0 verifies render-shape PER candidate
  (empty vs present-row vs body-tell), not just "2xx + benign"**; fresh-key-rejects that render empty may
  be scarce → disclosed-short if the ≥8 is unreachable (the honest floor survives via disclose+bias-audit
  regardless).
- **We do NOT claim to "defuse" the decode.** Per the substrate (R1 rev2 §3): the structural decode
  directions (present⇒benign; body-reject⇒benign) are **DISCLOSED**, and the **known-label bias-audit
  is the pre-registered detector** (a rater who uses a tell shows a structured confusion matrix vs the
  known calibration labels → feeds the S3-precision sensitivity band, F17). Honest disclosure + a
  detector, not a defusing claim.
- **Optional render-path hardening (Phase-0 decision):** for eventual-present benigns, a bounded change
  to strip the post-cap PRESENT observation from the *rater-facing* sidecar (heal recorded only in the
  admin answer-key) would let them render ABSENT and actually defuse — evaluate cost vs just inheriting
  the disclosed-tell framing.

## §3 Phase 0 — LIVE per-SUT verification survey (no pre-asserted candidates; the confabulation lesson)

rev 1 asserted specific candidates that turned out unsound. rev 2 asserts FAMILIES only; Phase 0
DISCOVERS + verifies live before counting:
- **write-acked-absent family:** by-design soft-rejects (2xx + body-reject + no persist) + dedupe/no-op
  on the sync SUTs (TT/TeaStore; S3 noted "by-design soft-rejects exist & were deferred"). Verify each
  is (a) 2xx-acked, (b) durably ABSENT, (c) benign by body-tell/semantics, (d) NOT a masked loss.
- **eventual-present family:** OTel accounting eventual-consistency (w120 class); TeaStore
  persistence-retry-heal IF real — the survey warns "TeaStore does NOT gracefully degrade… few masked
  benign traps", so verify cheaply before counting.
- **Phase 0 outputs (pinned before capture):** the reconciled demand/supply numbers (§1); the verified
  candidate list per SUT; the calibration decision (§4); per-SUT/shape ceilings (§5).

## §4 Calibration decision (A+C MAJOR — NO "shrink to 30")

At |S3|=0, `max(30,50−0)=50` MANDATES 50 and pooled=calibration, so pooled-≥50 REQUIRES calibration=50;
"30" is not a legal floor here and M-yield joins measurement-κ, not pooled-κ. **Decision: run the
LARGEST calibration the achieved supply permits — bounded by BOTH the decode-safe benign supply AND the
rateable-GENUINE supply (~16 from S1 positives; currently ~9-10 positive case-runs, so the genuine side
may bind calibration below 50 INDEPENDENT of benign supply, R1d-A residual); any calibration < 50 at
|S3|=0 is a DISCLOSED shortfall (not a formula floor), reported with the pooled-κ(n≥50)-basis loss +
power consequence AND the binding side (benign vs genuine)** (the already-frozen honest version, freeze
L309/L306). Do NOT pre-commit 30-vs-50; the achieved supply sets it.

## §5 Anti-concentration ceilings + magnitude-grounding (R1d-C MAJOR)

- **Hard ceilings that can actually fire** (checked at Phase 0): **≤ 40% per single SUT**, **≥ 3 SUTs
  represented**, **≥ 2 fault mechanisms** among the induced ones (avoid a `dependency-down` monoculture
  = the benign twin of the all-`flag` relapse). **The ≤40%-per-shape ceiling does NOT bind the
  write-acked-absent decode-safety family** (R1d-B/C R-2: that family is REQUIRED to be dominant, so a
  per-shape cap would contradict the ≥8 floor) — its anti-concentration is enforced WITHIN it (≥3 SUTs,
  ≥2 mechanisms, ≤40%/SUT); the ≤40%-per-shape cap binds only the NON-defuser shapes. **Phase-0
  feasibility check:** rateable decode-safe benigns come mainly from TT/TeaStore/OTel (Bookinfo/Boutique
  packaged-excluded), so ≥3-SUTs × ≤40%/SUT is feasible but TIGHT — verify before counting. If a ceiling
  would be breached, STOP and disclose rather than concentrate.
- **Induced-degradation magnitudes GROUNDED in documented SLOs / plausible p99s**, disclosed per case;
  the S2 FP-rate is reported as a **sensitivity band over magnitude** (else MIST's FP number is a knob
  artifact — "you chose the latencies that set your FP rate").
- Note the enum: toxiproxy-latency is not a `fault.mechanism` value — record induced degradation as its
  true mechanism (`dependency-down` for scale-0, etc.) + the magnitude in `fault.config`; specify at
  authoring.

## §6 Sequencing (RAM-aware) + E1 parallel
- **Batch 1 (now, no revival):** TeaStore (soft-rejects, retry-heal if real) + OTel (eventual-present) —
  both UP. Subject to the §5 ceilings (do NOT let batch-1 concentrate the stratum on 2 SUTs).
- **Batch 2:** revive TT (snapshot + nacos doubleWrite) for its soft-reject write-acked-absent family +
  the 2 legacy dedupe/noop re-captures under the R1 cadence pin; scale OTel/TeaStore to 0 first if RAM
  demands (the E2 3-tenant lesson).
- **Bookinfo/Boutique packaged** (≤2 each) are the freeze §5 "packaged FP corpora" — **EXCLUDED from C3
  rateable supply** (carve them out of the calibration arithmetic; they count only to the C2 corpus).
- **Marker-salt is TT-ONLY** (`s3-p0-pins.md` §1); verify key-uniqueness for any newly-entering SUT
  before assuming salt is needed/safe. Cadence-extension pin (SS/Bookinfo/Boutique) already dated in the
  R1 row — cross-reference before those captures.
- **E1 OpenAPI (parallel, no tenant window):** author OpenAPI specs for TeaStore + OTel-Demo for the
  **Gate-4 baseline-grid comparator arms** (name the specific arm(s) that consume them in the E1 DoD) —
  a subagent authoring job, independent of the capture batches.

## §7 Provenance honesty
Each benign labeled by true provenance: `natural-observation` (e.g. the existing w120, `source=natural`)
vs `by-injection` (induced transient degradation). Count them separately (a new induced OTel eventual
capture is `by-injection`, NOT folded with the natural w120). The R8 tell-free-natural floor is a
POSITIVE (S1) floor — it does NOT appear in this benign wave.

## §8 Framing + disclosure (R1d-C MAJOR)
- **S2 is a CONSTRUCTED FP-trap stratum**, parallel to the constructed S1 positives; natural-prevalence
  claims cite **S3 only** (0/1514); the induced majority is DISCLOSED and expected. Pin this sentence.
- **[CORRECTED at RESULT time — 2026-07-14, post-capture review B-F1; see `RESULT-r1d.md` §7 + freeze row
  310 item 7. This pre-registration premise was INVERTED and is RETRACTED:** in OBSERVE mode
  `TIMEOUT_ABSENT` is WARN-only, NOT a defect (`DataIntegrityObserveCheck` L58-73), so MIST's observe oracle
  does NOT fire on eventual-present — it correctly ABSTAINS (precision STRENGTH). The FP trap is a NAIVE
  at-cap comparator; PAIRED-mode-without-re-probe would FP. Only the `reProbe`-is-S3-only half below was
  right.]** ~~MIST's single-shot-timeout read-back FIRES on eventual-present traps BY CONSTRUCTION~~ (confirmed vs
  source: `reProbe` is an S3-hunt-only accessor; the product observe path is single-shot poll-to-cap →
  TIMEOUT_ABSENT). ~~Frame this as a documented read-back-oracle LIMITATION~~ (a timeout oracle cannot
  distinguish eventual-consistency-beyond-cap from loss), NOT buried at scoring. MIST's S2 precision
  splits honestly: ~~fires on eventual-present (a known limit)~~ [correctly ABSTAINS on eventual-present,
  observe-mode]; correctly no-fires on dedupe/no-op/clean soft-rejects where the read-back is unambiguous.
- **Record the shape family in a STRUCTURED case field** (not free-text) so the shape-polarity census is
  mechanically computable. One reconciled benign-count table by SUT × shape × provenance.

## §9 DoD + stop rules
1. Phase 0 done: numbers pinned + candidate list verified + calibration decision + ceilings set.
2. ≥8 write-acked-absent (defuser family) + ≥2 eventual-present captured, decode-safe, within §5
   ceilings; OR the shortfall disclosed with the calibration/pooled-κ consequence.
3. Each case schema-valid + rendered (0 BANNED_STRINGS) + honest provenance + structured shape field;
   corpus-wide validator green.
4. §8 disclosure delivered; a dated freeze §6 note reconciling the demand number (35 vs the frozen
   42-43) + the supply number (4 vs the S3 RESULT's 12).
5. RESULT-r1d + **CONFIRMATION PASS + post-capture 3-cold review** PASSED; README/freeze/FILE_INDEX/
   memory synced.
- **Stop rules:** a candidate that is a masked LOSS on inspection ⇒ it is a POSITIVE, not padded into S2
  (honest reclassification; needs a heal-bound decision for slow-heal candidates — extend the benign
  re-probe bound beyond 300s or reclassify); a shape that only decodes via presence with no ambiguity ⇒
  disclose it as a tell (don't claim defusing); §5 ceiling breach ⇒ STOP + disclose; RAM over-commit ⇒
  scale-to-0 per §6; TT/nacos fragility ⇒ runbook.

## §10 Out of scope
Positive-side widening (CLOSED); the MIST value-corruption scope boundary (paper Scope/Limitations,
`REVIEW-R1C-RECONCILIATION.md`); R2 assembly/seal + M1 (E1-grid execution, E2 done, E5/E6); the
fork-publication + IRB decisions (USER).
