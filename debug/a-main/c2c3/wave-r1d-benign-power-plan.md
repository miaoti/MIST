# Wave R1d — benign-power lift (S2 → floor) — rev 1 (FOR 3-COLD REVIEW)

**Date:** 2026-07-13 · Owner: main_track · Status: **DRAFT — requires ≥3 independent cold reviewers
UNANIMOUS-ACCEPT before capture (standing /goal rule).**
**Provenance:** the R1b (≥20) and R1c (micro-widen) reviews CONVERGED (twice) that the BINDING
constraint on the C3 rater study is the BENIGN side, not the positive-site count. Positive-side widening
is CLOSED (`REVIEW-R1C-RECONCILIATION.md`). This wave addresses the actual gate. It adopts R1c-review-C's
"one wave" structure: benign-power is the spine; E1 OpenAPI is a parallel no-tenant track (§5); the
positive ride-along is dropped.

---

## §1 The demand (pinned from the FROZEN materials — this is the whole point of the wave)

- **S2 stratum floor = ≥ 35 distinct benign traps** (`c2-freeze.md` §5 L255-257). The survey projected
  ~16 (4 new SUTs + 2 packaged corpora ≤2 each) + "TT/SS designed-degradation paths — to be enumerated
  at step 2, **disclosed if short**." → the shortfall response is ALREADY pre-registered (freeze §5),
  same discipline as the positive side.
- **Calibration draws ~33 benigns** from that same S2 pool (calibration = max(30, 50−|S3|) = **50** with
  |S3|=0, benign-skewed **≥2:1** → ~33 benign + ~17 genuine; `c3-rater-materials.md` §6). Mechanical
  oracle-FP runs do NOT consume cases, so **one pool of ≥35 distinct benign traps serves BOTH** the
  FP-rate story AND the calibration draw (calibration-not-reused-as-measurement holds because the
  measurement κ round is S3+M-yield, not S2).
- **Supply today = 4 captured benign traps** (bookinfo-ratings-benign, oteldemo-checkout-eventual-benign,
  TT-contacts-noop-modify-benign, TT-contacts-dedupe-benign). **Gap ≈ 31.**
- **NOT padding (the key disanalogy to the rejected positive widening):** inducing a *transient
  degradation* (latency / transient-503 / eventual-consistency / retry-heal) produces a GENUINE benign —
  the write really does land eventually, or the response is honestly degraded. This is real corpus
  construction, not a fabricated fault. (Diversity across shapes/SUTs still matters — §2/§3 — so it is
  not "35 clones of one shape.")

## §2 Decoder-safety = the CENTRAL design constraint (do NOT let the stratum decode)

The C3 study is blind; a benign pool that a rater can DECODE ("if it heals it's benign / if it stays
absent it's genuine") destroys the FP measurement. Frozen constraints (C-B3 / A-F10 / R1 rev2 §3 / the
S3-review presence↮label tell):
1. **Degradation-shaped ONLY — never clean-present.** A benign where the write simply succeeded with
   everything present is a decodable tell (clean ⇒ benign). Forbidden in the S2 stratum.
2. **Absence-at-decision-point must NOT be a genuine-tell.** The S1 positives are acked-absent-permanent;
   if EVERY benign is acked-present, "absent ⇒ genuine" decodes. So the benign pool MUST include
   **acked-absent-at-observation-cap** shapes (eventual-consistency benigns that are absent at the
   rater's fixed observation point and only heal later) — R1 rev2 §3 floor ≥8 of these — so absence at
   the decision point is genuinely ambiguous (P(genuine|absent) pushed toward 0.5).
3. **Balanced shape mix**, disclosed, with the residual decode direction stated and a **per-rater
   bias-audit used as a detector** (a rater who decodes will show a structured confusion matrix vs the
   known calibration labels → feeds the S3-precision sensitivity band, F17).
4. **Genuinely tempting** — each trap must plausibly read as a masked loss at first glance (acked +
   something-off) so it actually tests FP discipline; an obviously-benign case teaches nothing.

## §3 Degradation-shape taxonomy + Phase 0 supply survey (BEFORE any counted capture)

**Shapes (decoder-safe benign classes):**
- **S-A eventual-consistency / acked-absent-then-present** (absent at cap, present at re-probe) — the
  decode-defusing shape (§2.2); floor ≥8.
- **S-B eventual-present / delayed-present** (present but late) — floor ≥2.
- **S-C transient-503-then-healed / retry-succeeded** (honest degraded response, write lands on retry).
- **S-D maintenance-window honest-degraded** (TeaStore maintenance toggle = an honest 503/"-1", NOT a
  masked success — benign because the user is TOLD it failed).
- **S-E idempotent dedupe / no-op-modify** (re-submit or unchanged-modify acked, no new row expected) —
  the 2 captured TT traps.
- **S-F draining-backlog-then-drained** (a queue that is behind at cap but drains) — SockShop's
  shipping/carts (its S3 FP-storm shape is exactly an S2 benign here — see §4).

**Phase 0 (mandatory, no capture yet):** enumerate achievable {shape × endpoint} per SUT and PROJECT the
ceiling. Two PRE-REGISTERED branches (freeze §5 "disclosed if short" already blesses branch b):
- **ceiling ≥ 35** → capture to the floor with a decoder-safe shape mix.
- **ceiling < 35** → capture all achievable, DISCLOSE the shortfall, AND shrink calibration toward its
  max(30, …) floor of **30** (re-derive pooled-≥50 feasibility with the smaller benign draw; the
  reliability ladder §6 is unchanged) — NO clean-present padding (decoder hazard §2.1).

## §4 Per-SUT capture plan (RAM-aware order in §5)

| SUT | tenant status | candidate degradation-shaped benigns | shapes |
|---|---|---|---|
| OTel-Demo | UP | accounting scale-0→buffer→drain (eventual-consistency, have 1 `w120`); transient-503 on a sync dep then heal; kafka bounded-lag-then-drain | S-A, S-B, S-C |
| TeaStore | UP | maintenance-toggle honest-degraded (503/"-1"); persistence retry-heal; recommender cold-start warm-up window (the C3b datum — user-visible?-re-test as benign) | S-D, S-C, S-B |
| TrainTicket | 0 (revive) | admin-basic soft-rejects (S3 found "by-design soft-rejects"); dedupe/no-op (have 2); order/travel transient-latency-then-present | S-E, S-A, S-C |
| SockShop | 0 (revive) | shipping-enqueue draining-backlog-then-drained (its S3-FP-storm shape = a GENUINE S2 benign here); carts eventual | S-F, S-A |
| Bookinfo | 0 (revive) | ratings dependency-flap-then-recover (have 1 benign) | S-B (packaged, ≤2) |
| Boutique | 0 (revive) | one packaged degradation benign | S-C (packaged, ≤2) |

**SockShop reconsidered (defensible):** S3 EXCLUDED SockShop for a draining-queue "FP storm" (read-back
sees absent-then-present). That exact shape is a *legitimate S2 benign* (S-F) — SS's S3 liability is its
S2 asset. Include it as a benign source (NOT re-opened for S3).

**Discipline per capture (unchanged from R1/S3):** probe-first N≥4 vs ribbon round-robin; the s3-p0-pins
cadence (OTel 25s/2s, TeaStore 10s/0.5s, TT 10s/0.5s, re-probe 300s — freeze:309); a negative control
where a control is meaningful; C-F7 teardown-verify between captures; per-run marker salt (S3 lesson);
NEVER GET /rest/generatedb (TeaStore wipe); TT nacos doubleWrite after any nacos restart.

## §5 Sequencing (RAM-aware — the E2 3-tenant lesson) + E1 parallel track

- **Batch 1 (now, no revival):** OTel + TeaStore benigns (both UP) → S-A/B/C/D. Fastest path to a large
  chunk of the 31.
- **Batch 2:** revive TT (snapshot + nacos doubleWrite) → S-E/A/C; scale OTel/TeaStore to 0 first if RAM
  demands (E2 lesson: 3 full tenants over-commit 25Gi).
- **Batch 3:** revive SockShop (S-F) + Bookinfo/Boutique packaged (≤2 each).
- **E1 OpenAPI (parallel, no tenant window):** author OpenAPI specs for TeaStore + OTel-Demo for the
  Gate-4 baseline grid — a subagent AUTHORING job runnable anytime, independent of the capture batches.
  Scoped lightly here; its own DoD is "specs that the baseline tools can consume," verified at the grid.

## §6 Provenance honesty
Each benign labeled by true provenance: `natural-observation` (observed degradation, e.g. the existing
`oteldemo-checkout-eventual-benign`) vs `by-injection` (induced transient degradation — latency/503
toxiproxy/scale-0-then-restore). Do NOT claim induced benigns are natural. `ground_truth.source` set
accordingly; the tell-free-natural floor (R8) counts only the natural ones and stays honestly small.

## §7 Disclosure machinery (carry the R1c M5/M6 discipline)
- **Pinned claim sentence (S3 §0.5 style):** *"The S2 benign-trap stratum comprises N distinct
  degradation-shaped benign cases across K SUTs (M natural-observation / N−M induced transient
  degradation), spanning S-A…S-F shapes; the calibration set draws ~33 benign from it. Where N < 35 the
  shortfall is disclosed (freeze §5) and the calibration set is sized to its max(30,…) floor."*
- **One reconciled benign-count table** (supply 4 → captured N → shortfall vs 35), by SUT and shape.
- **Decoder-safety audit reported:** the shape-balance, the residual decode direction, and the per-rater
  bias-audit-as-detector (F17).

## §8 DoD + stop rules
1. Phase 0 supply survey done + ceiling projected + branch (a/b) selected + disclosed.
2. Benign traps captured toward 35 with a decoder-safe shape mix (≥8 S-A acked-absent, ≥2 S-B) OR the
   shortfall disclosed + calibration shrunk to floor-30; each case schema-valid, rendered, controlled
   where meaningful, honest provenance.
3. §7 disclosure delivered; corpus-wide validator green.
4. RESULT-r1d + ≥3-cold review PASSED; README/freeze §6/FILE_INDEX/memory synced.
- **Stop rules:** decoder-safety failure (a shape that decodes the stratum) ⇒ drop + disclose; RAM
  over-commit ⇒ scale-to-0 per §5; TT/nacos fragility ⇒ runbook; if a "benign" turns out to be a masked
  LOSS on inspection (not a heal) ⇒ it is a POSITIVE, not padded into S2 (honest reclassification).

## §9 Out of scope
Positive-side widening (CLOSED); the MIST value-corruption scope boundary (→ paper Scope/Limitations,
`REVIEW-R1C-RECONCILIATION.md`); R2 assembly/seal + M1 (E1-grid execution, E2 done, E5/E6); the
fork-publication + IRB decisions (USER).
