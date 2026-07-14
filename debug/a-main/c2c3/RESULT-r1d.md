# RESULT — Wave R1d (benign-power lift, S2) — 2026-07-13 (3-cold review closed 2026-07-14)

**Status:** EXECUTED + **post-capture 3-cold review DONE (2026-07-14, all 3 ACCEPT-WITH-FIXES; the 1
BLOCKING item source-verified + folded) → R1d CLOSES.** Reconciliation:
`REVIEW-R1D-RESULT-RECONCILIATION.md` (reviews `REVIEW-R1D-RESULT-{A,B,C}.md`). RESULT-of-record for the
benign-side (S2) completion leg of Wave R1. Plan: `wave-r1d-benign-power-plan.md` rev 2.1 (CLEARED).
Phase-0 finding: `r1d-phase0-findings.md`.

**One-line result:** the S2 benign floor is **NOT met and is disclosed short** — R1d captured the
**achievable** decode-safe benign supply (**2 induced eventual-present benigns** on live OTel, clean +
ground-truth-verified, lifting the decode-safe FP-trap pool **4 → 6**, 5 C3-rateable) against a frozen
demand of **≥35 S2 traps AND ~40-43 benign for a 50-case calibration**; the gap is disclosed with its
power/κ consequence (§3, §8). The benign side is the binding constraint (all R1b/R1c/R1d reviewers). The
shortfall is itself a finding: with the S3 0/1514 wild scarcity + the Phase-0 presence-defuser
structural-scarcity, it QUANTIFIES why a natural masked-benign corpus cannot be manufactured to volume.

---

## §1 What executed — 2 induced eventual-present benigns (live OTel, ground-truth-verified)

Runner `b4/runners/r1/r1d-otel-eventual.sh` (kafka pod NEVER touched; accounting scaled 0→1 only).
Full log: `b4/captures/oteldemo-checkout-eventual-induced/r1d-run-1784003126.log`. Mechanism: scale the
accounting CONSUMER to 0 → the checkout→kafka `orders` message BUFFERS durably → order acked 200 while
`accounting.shipping` stays ABSENT past MIST's ~25 s read-back cap → scale accounting back to 1 → the
consumer drains → the row LANDS (PRESENT at re-probe). **The write is NOT lost — benign bounded eventual
consistency.**

| case | marker | ack (HTTP 200, orderId) | at-cap probe | re-probe (post-drain) |
|---|---|---|---|---|
| `oteldemo-checkout-eventual-benign-002` | `r1dev1-1784003126` | `17158d51-…` @ epoch 1784003148767 | `[]` @ +30402 ms (ABSENT) | `[r1dev1-…]` @ +58214 ms (**PRESENT**) |
| `oteldemo-checkout-eventual-benign-003` | `r1dev2-1784003126` | `3cfd32b3-…` @ epoch 1784003212363 | `[]` @ +29337 ms (ABSENT) | `[r1dev2-…]` @ +58842 ms (**PRESENT**) |

Controls in the same run (clean): health canary landed @12 s; final canary landed; accounting restored
1/1; kafka 1/1 untouched. Ground truth is INDEPENDENT of MIST's oracle — direct psql reads of
`accounting.shipping`, not a MIST verdict.

**Provenance (§7):** these are `source=by_construction`, `provenance_class=by-injection`, magnitude ~35 s
accounting-down window (~9× smaller than the natural w120's 328 s backlog; both sub-minute-to-minutes
bounded backlog on this exact path — disclosed) — counted SEPARATELY from the natural w120
(`oteldemo-checkout-eventual-benign-001`, `source=natural`). Sidecars:
`b4/captures/oteldemo-checkout-eventual-induced/sidecar-00{2,3}.json` (w120 format; rater-facing opaque-id
+ marker re-key is the corpus-wide ASSEMBLY step). All 26 cases validate green.

## §2 The mechanical benign census (SUT × readback_shape × provenance)

DoD §9.3: a **structured** shape field, not free-text. Added optional `readback_shape` to
`fault-case.schema.json` and populated all 15 negatives (positives omit it — shape is `fault_class`-derived).
Census computed mechanically from the field:

| readback_shape | n | breakdown |
|---|---|---|
| **present-landed** (clean twin/control) | 9 | TT 4 · TeaStore 3 · OTel 1 · SockShop 1 |
| **eventual-present** (absent-at-cap → heals) | 3 | OTel: 1 natural (w120) + 2 induced (this wave) |
| **reject-no-delta** (dedupe / no-op) | 2 | TT: dedupe + no-op-modify |
| **degraded-present** (graceful degradation) | 1 | Bookinfo (C3-EXCLUDED, packaged) |
| **reject-absent-empty** (presence-defuser) | **0** | structurally near-unreachable — see §4 |
| TOTAL negatives | **15** | |

**Decode-safe FP-TRAPS** (non-control benigns) = eventual-present (3) + reject-no-delta (2) +
degraded-present (1) = **6**, of which Bookinfo is **C3-excluded** (packaged FP corpus — plan §6/§11 +
freeze §5 "packaged FP corpora"; NOT the freeze §6 R1 X7 clause, which names the TT/SS probe corpora) ⇒
**C3-rateable decode-safe traps = 5**. Pre-R1d = 4 (plan §1) → post-R1d = 6. **Δ = +2** (the 2 induced
eventual). The 9 `present-landed` cases are clean CONTROLS (S1 clean twins), which per freeze line 142 R1
X1 **never count toward the ≥35 S2-trap floor or the C3 benign supply**.

## §3 Demand vs supply reconciliation (freeze §6 note, OWED per DoD §9.4)

- **Demand — TWO frozen obligations, BOTH unmet (A-F1 correction; my first draft under-stated this):**
  (i) the **S2 floor = ≥35 distinct benign traps** (freeze §5); AND
  (ii) the **calibration target = 50** at |S3|=0 (`max(30, 50−|S3|)=50`, pooled=calibration), which at the
  benign-skew ≥2:1 needs **benign ≈ 50 − genuine ≈ 40-43** (rateable genuine ≈ 7-10, §8).
  The S3-era grounding's "~42-43" was PARTLY a double-count — its *33+10* derivation wrongly treated the
  FP-calibration pool and the S2 pool as disjoint (they are not: calibration draws FROM S2, mechanical
  FP-rate runs don't consume cases), so THAT 43 is corrected down toward the ~34 calibration draw. BUT
  obligation (ii), `50 − genuine ≈ 40-43` benign, is INDEPENDENT of that double-count and is NOT subsumed
  by ≥35 (my draft's "correct single demand is ≥35" was wrong to drop it). Honest demand = **≥35 (S2 floor)
  AND ~40-43 (calibration-50 benign share)**; the dated freeze §6 row records both.
- **Supply number 4 vs S3's "12":** the S3 RESULT's "benign pool 12" folded in NON-rateable items (clean
  controls + packaged corpora). The rateable **decode-safe trap** supply pre-R1d was **4**; post-R1d **6**
  (5 C3-rateable). Total negatives (incl. controls) = 15.
- **Verdict: DISCLOSED SHORTFALL against BOTH obligations.** 5-6 rateable traps (or 15 total negatives) ≪
  35 ≪ 40-43. TWO disclosure bases (C-F7): the **≥8 presence-defuser floor is STRUCTURAL** (near-unreachable
  by logic, §4), while the **≥35 / ~40-43 volume floor is an EXHAUSTION-bar** shortfall — the candidate
  SITES are not all exhausted, but the achievable decode-safe SHAPES are structurally scarce (the S3
  finding). Both were pre-registered (freeze §5 + the R1 pre-reg row X2 "≥35 structurally unreachable on
  this SUT set", X3 "≥8 write-acked-absent"). R1d captures the achievable and quantifies the gap; it does
  not close it.

## §4 The structural finding — the presence-defuser is near-unreachable (`readback_shape: reject-absent-empty` = 0)

**A clean-ack write that renders EMPTY yet is benign does not cohere as a MIST-relevant trap** (Phase-0,
`r1d-phase0-findings.md`):
- **By logic:** a benign that renders ABSENT with a CLEAN success ack IS a masked LOSS (the acked-but-lost
  positive MIST exists to catch). A server that *correctly* declines a write must SIGNAL it (a body-tell) —
  so "renders empty + benign" necessarily carries a body-tell, which is either EXCLUDED by MIST's clean-ack
  precondition (a true negative, not an FP-trap) or decoded by the rater via the body (not via presence).
- **Confirmed empirically:** `prep/g3-sut2-fp-probe-report.json` L99 (the only TT by-design drop soft-rejects
  with body `status:0`, ack-rule-excluded) + `c2-depth-survey.md` L113 (TeaStore does not gracefully
  degrade → few masked-benign traps).

**Consequence:** the freeze R1 X3 **≥8 write-acked-absent presence-defuser floor is a DISCLOSED SHORTFALL
(0 achieved)** — a structural result, not an execution failure. It VALIDATES the reviewers' insistence on
disclose-tell + known-label bias-audit over a "defuse" claim: the presence decoder *cannot* be defused
because its qualifying shape does not exist among honest clean benigns. This joins the S3 0/1514 scarcity
as the SECOND structural-scarcity datum of the corpus.

## §5 Precision correction (verified from case files) — the 2 `reject-no-delta` traps decode DIFFERENTLY

Phase-0's "dedupe/no-op … body `status:0`" was slightly imprecise. Verified against the case files:
- **`TT-contacts-dedupe-benign-001`:** `ack_content_visibility=status-field-tells`, body carries
  `status:0` ("Contacts already exists"); read-back = count-delta-zero. Decode axis = **body-tell** (the
  ack rule excludes it → MIST correctly no-fires).
- **`TT-contacts-noop-modify-benign-001`:** `ack_content_visibility=success-shaped-clean` (200
  `{status:1,"Modify success"}` — **NO body tell**); read-back = present-unchanged. Decode axis =
  **present-row + the same-with-same norm** (submitted values equal current → nothing to change), NOT a
  body-tell.

Both are decode-SAFE (neither relies on ABSENCE), but the disambiguator differs (body-tell vs norm+present).
Both share `readback_shape: reject-no-delta` (they trap the DELTA/ack-body decoders, not the presence
decoder) — recorded accurately so the census is not over-claimed as uniformly body-tell.

## §6 Anti-concentration ceilings (plan §5) — induced captures mono-mechanism; trap-pool OTel-concentrated (DISCLOSED)

- **≤40% per single SUT — two denominators (A-F3 / C-F4):** across all 15 negatives TT 6/15 = 40%
  (boundary), OTel 4/15 = 27%, TeaStore 3/15 = 20%, SockShop 1/15, Bookinfo 1/15 — passes at the boundary.
  But across the **6 decode-safe TRAPS** (the plan-§5 ≤40%-per-shape cap's real target) **eventual-present =
  OTel = 3/6 = 50%** — over the 40% line. This trap-level OTel/eventual concentration is the SAME phenomenon
  as the mono-mechanism miss below and is disclosed as ONE shortfall, not a clean pass. **≥3 SUTs across the
  full pool ✓** (5 SUTs); across the traps = 3 (OTel/TT/Bookinfo) ✓.
- **≥2 mechanisms among the INDUCED captures — NOT MET (DISCLOSED); three honestly-separated reasons (B):**
  this wave's 2 induced captures are both `dependency-down`/`dependency_scale_zero` on OTel. Why no 2nd
  induced mechanism:
  (a) the presence-defuser shape is **STRUCTURALLY unreachable** (§4) — a real finding, not a miss;
  (b) TeaStore eventual-present was **SURVEYED absent** (does not gracefully degrade, `c2-depth-survey` L113);
  (c) TrainTicket was **NOT ATTEMPTED this wave** — plan §6 Batch 2 (TT revival) was deferred; this is a
  disclosed SCOPE choice, NOT an "unreachable" finding. Precise note: reviving TT would add its NATURAL
  soft-reject traps (`fault.mechanism=none` — the 2 existing `reject-no-delta` already ARE that family), NOT
  a second INDUCED mechanism, so the mono-INDUCED-mechanism disclosure stands regardless of TT.
  Per the plan §9 stop-rule ("§5 ceiling breach ⇒ STOP + disclose") I did **NOT** manufacture a second
  mechanism to hit the ceiling (= padding-in-a-benign-hat). The **whole benign POOL is mechanism-diverse**
  (dependency-down + mesh-sever + natural + none across the 15); only this wave's 2 induced captures are mono.
- **Mechanism-diversity firewall (C-F3):** the `fault.mechanism` diversity floor (≥4 per write-path SUT) is
  computed over `label==positive` sites ONLY, so a NEGATIVE's `mechanism` stamp (these dependency-down
  benigns) can NEVER inflate it. Verified latent-but-does-not-bite: OTel already carries `dependency-down`
  among its POSITIVES, so the 3 dependency-down negatives add no new per-SUT positive mechanism.
- **Magnitude grounding:** the ~35 s accounting-down window is grounded as ~9× smaller than the natural
  w120's 328 s kafka→accounting backlog (an observed bound on this exact path), disclosed per case. The S2
  FP-rate must be reported as a **sensitivity band over magnitude** at scoring (plan §5), not a point knob.

## §7 MIST's observe oracle on eventual-present (plan §8) — CORRECT abstention, a precision strength

**CORRECTED after the review (B-F1, source-verified — my draft here was INVERTED).** My first draft claimed
MIST's observe oracle "FIRES a false-positive by construction"; that is wrong. Per source:
- `DataIntegrityObserveCheck.java` L58-73: in OBSERVE mode an at-cap absence with no trace-confirmation is
  gated `TIMEOUT_ABSENT` = "persistence UNCONFIRMED", reported WARN-only and **NOT counted as a defect**
  (shipped default WARN, L16-20/L104-107).
- `DataIntegrityRuntime.java` L736-773: the defect tier `OBSERVED_COMPLETE_ABSENT` is reached ONLY when
  `traceComplete(traceId)` (a Jaeger trace shows the step's own trace complete); the async
  checkout→kafka→accounting downstream-persist path does not provide that, so the gate stays
  `TIMEOUT_ABSENT`.
- `reProbe` is S3-hunt-only (grep: called only from `WildHuntEngine.java`) — the one half of my draft that
  checked out.

So **MIST's OBSERVE oracle correctly does NOT flag these benigns** — `mist_readback_oracle = no_flag` is
ACHIEVED by MIST (precision-first design), not a target it fails. Consistency verified against the corpus:
the flagship async loss `oteldemo-checkout-lost-001` is `oracle_mode=paired` → `mist_readback_oracle=flag`
(paired fires on the absence-delta); the 3 eventual-present benigns are `oracle_mode=observe` → `no_flag`
(observe correctly abstains) — the cells were right; only my prose explanation was inverted.

The oracle these benigns actually TRAP is a **NAIVE at-cap read-back comparator** (fires on any at-cap
absence → FP). **PAIRED**-mode MIST would ALSO FP here — it fires on the absence-delta with **no re-probe**,
which is precisely why the S3 CONFIRMED detector adds the T+5 min re-probe gate. Honest split: MIST's
**OBSERVE** oracle is a **precision STRENGTH** on eventual-present (correct abstention where the naive
baseline FPs); MIST's **PAIRED** oracle without re-probe would FP (the re-probe is the fix). These R1d cases
exercise observe mode. (This is also why the corpus's positives are injected: nature does not hand you
masked-absent-then-healed writes at volume — the S3-scarce phenomenon.)

## §8 Calibration decision (plan §4) — largest achievable ≪ 50, binding side = benign

At |S3|=0 the formula MANDATES 50 (pooled = calibration; benign-skewed ≥2:1 → ~34 benign + ~16 genuine).
Achieved supply: decode-safe rateable benign traps ≈ 5 (15 total negatives incl. controls); rateable
genuine ≈ 9-10 (S1 positives). **The largest achievable calibration is ≪ 50, and the BENIGN side is the
binding constraint** (fewer rateable traps than genuine — the reviewers' consistent finding). Reported as a
**DISCLOSED shortfall** (not a formula floor) with: the pooled-κ(n≥50)-basis loss, the power consequence,
and the binding side named (benign). Do NOT pre-commit 30-vs-50 — the achieved supply sets it.

## §9 Decode-safety framing (plan §2/§8) — disclose + bias-audit, NOT "defuse"

The structural decode directions (**present⇒benign**; **body-reject⇒benign**) are **DISCLOSED**; the
**known-label bias-audit is the pre-registered detector** (a rater who rides a tell shows a structured
confusion matrix vs the known calibration labels → feeds the S3-precision sensitivity band, F17). We do NOT
claim to "defuse" the decode — the eventual-present family renders PRESENT (heals), a disclosed present⇒benign
tell that reinforces, not breaks, the presence heuristic. Honest disclosure + a detector is the only coherent
design, exactly because the presence-defuser shape that WOULD break it is structurally near-unreachable (§4).

## §10 Honest framing (plan §8) — pinned sentences

1. **S2 is a CONSTRUCTED FP-trap stratum**, parallel to the constructed S1 positives. Natural-prevalence
   claims cite **S3 only** (0/1514, ≤0.20%). The induced majority is DISCLOSED and expected.
2. **The eventual-present benigns are induced** (`by_construction`); only w120 is a natural observation.
3. The two structural-scarcity data (S3 0/1514 wild + the R1d presence-defuser 0) together justify why the
   corpus's positives are injected and why the S2 volume floor is disclosed-short, not met.
4. **MIST's observe oracle correctly ABSTAINS on eventual-present** (§7) — these benigns are FP traps for
   the NAIVE at-cap comparator and PAIRED-mode-without-re-probe, NOT for MIST's observe oracle (a precision
   STRENGTH, correcting the draft's inverted "limitation" wording).
5. **S2-invariant exemption (disclosed):** the induced -002/-003 (`by_construction`/`by-injection`,
   `doc_citation=null`) are exempt from the §2 S2-invariant `by-docs`+citation sub-clause, EXTENDING the
   -001 natural-observation exemption (freeze row 308). The shared *exemption basis* is the async path's
   lack of a citable completion bound (§0.4); the *provenance genuinely SHIFTS* (natural → induced), so the
   induced cases carry the stricter disclosures (separate count §1, induced-magnitude sensitivity band §6).

## §11 DoD check (plan §9) + residuals/owed

| DoD item | status |
|---|---|
| §9.1 Phase-0 numbers pinned + candidates verified + calibration decision + ceilings | ✅ `r1d-phase0-findings.md` + §2/§6/§8 |
| §9.2 ≥8 presence-defuser + ≥2 eventual **OR shortfall disclosed** | ✅ OR-branch: 3 eventual (≥2 ✓); presence-defuser 0 → **shortfall disclosed** (§4) with calibration/κ consequence (§8) |
| §9.3 schema-valid + honest provenance + **structured shape field** | ✅ 26/26 validate; `readback_shape` added + 15 negatives populated |
| §9.4 §8 disclosure + dated freeze §6 note (35-vs-42-43 + 4-vs-12) | ✅ §3 + §10; freeze §6 R1d row landed |
| §9.5 RESULT-r1d + **CONFIRMATION PASS + post-capture 3-cold review** | ✅ **DONE** — 3-cold review complete (A/B/C all ACCEPT-WITH-FIXES; the 1 BLOCKING item source-verified + folded; `REVIEW-R1D-RESULT-RECONCILIATION.md`) → **R1d CLOSES** |

**Residuals / owed:**
- **Rater-facing render is an R2 ASSEMBLY gate, NOT done this wave (C-F8):** the -002/-003 sidecars use raw
  markers (`r1dev*` — a wave-label leak that WOULD trip BANNED_STRINGS); the opaque-id + marker re-key + the
  0-BANNED_STRINGS `b4_harness.render` round-trip happen at R2 assembly (corpus-wide), not here. No clean
  rater-facing render is claimed for these two cases yet.
- The S2 FP-rate **magnitude sensitivity band** is reported at SCORING (R2 assembly), not here.
- The **real traced MIST discrimination run** stays PRE-REGISTERED + owed at 2.5/E2 (unchanged by R1d).
- Bookinfo/Boutique packaged benigns stay C3-excluded (plan §6/§11 + freeze §5 "packaged FP corpora").

**Folded corpus-hygiene fix (C-F1):** `teastore-orderitems-meshsever-control-001.fault_class` corrected
`acknowledged_lost_write` → `none` (it is a clean control where order + items both land; its sibling
`teastore-order-meshsever-control` was already `none`). Does not affect the census (which reads
`readback_shape`).

**End-state:** OTel-Demo UP & healthy (accounting 1/1, kafka 1/1, restored post-run); TeaStore UP; TT at 0.
Corpus = 26 cases (11 positive / 15 negative), all validator-green.
