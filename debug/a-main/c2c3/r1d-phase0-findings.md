# R1d Phase 0 — supply survey FINDINGS (in progress) — 2026-07-13

**Status:** doc-based + logical analysis DONE; LIVE confirmation on the current tenants (otel-demo 21
Running, teastore 7 Running) still owed before capture is counted. Plan: `wave-r1d-benign-power-plan.md`
rev 2.1 (CLEARED). This pins the emerging §3 Phase-0 outputs.

## THE structural finding (a real finding, like S3 scarcity — not a failure)

**The write-acked-absent PRESENCE-defuser benign (a clean-ack write that renders EMPTY yet is benign)
does not cohere as a MIST-relevant trap** — proven by logic + confirmed by prior evidence:

- **By logic:** a benign that renders ABSENT (empty read-back) with a CLEAN success ack = a masked LOSS
  (that IS the acknowledged-but-lost positive MIST exists to catch). If the server *correctly* declines a
  write, it must SIGNAL the decline (a body-tell) — it cannot ack clean success. So "renders empty + benign"
  necessarily carries a **body-tell** (`status:0`/error field), which is either (a) EXCLUDED by MIST's
  clean-ack precondition (a true negative, not an FP-trap) or (b) decoded by the rater via the body, not
  via presence.
- **Confirmed empirically:** `prep/g3-sut2-fp-probe-report.json` L99 — *"the 2xx-accepted-but-by-design-
  never-persists trap class has NO representative on TrainTicket: the only by-design drop (contacts
  dedupe) soft-rejects with body status:0, which the ack rule already excludes… the residual FP class is
  therefore covered only by the eventually-consistent-then-correct benign runs."* And `c2-depth-survey.md`
  L113: *"TeaStore does NOT gracefully degrade… few masked-benign traps."*

**Consequence:** the ≥8 presence-defuser floor is **structurally near-unreachable by MIST-relevant clean
benigns → a DISCLOSED SHORTFALL** (rev-2.1 §2 pre-registered exactly this). It VALIDATES the reviewers'
insistence on the disclose-tell + known-label-bias-audit framing over a "defuse" claim: the presence
decoder *cannot* be defused, so honesty (disclose + detect) is the only coherent design.

## The achievable decode-safe benign supply (what CAN be captured)

| shape | MIST behavior | rater decode axis | source (to live-verify) | have |
|---|---|---|---|---|
| **eventual-present** (absent-at-cap → heals) | **FPs by construction** (single-shot timeout, the documented limitation) | present⇒benign (disclosed) | OTel accounting eventual-consistency (w120 class); OTel/Bookinfo bounded-backlog | 1 (w120) |
| **dedupe / no-op-modify** (renders PRESENT-unchanged, body `status:0`) | ack-rule EXCLUDES → correct no-fire (true negative) | delta/body-tell⇒benign (disclosed) | TT contacts (have 2) | 2 |
| **designed-degradation / optional-dep** (graceful 200) | depends on read-back surface | present⇒benign | Bookinfo productpage→reviews/details degraded → 200 (packaged, ≤2, C3-EXCLUDED) | 0 |

So the MIST-relevant FP-trap supply is dominated by **eventual-present** (the timeout-limitation traps) +
the 2 body-tell dedupe/no-op. The presence-defuser column is ~empty.

## Pinned Phase-0 decisions (subject to live confirmation)

1. **≥8 presence-defuser floor → DISCLOSED SHORTFALL** (structural, per the finding above). Do NOT
   manufacture clean-ack-empty cases — that would mint masked-loss positives mislabeled benign.
2. **Capture the achievable:** live-verify + capture the **eventual-present** MIST-FP-traps on OTel
   (bounded-backlog beyond w120) + any real TeaStore retry-heal; keep the 2 dedupe/no-op body-traps;
   Bookinfo designed-degradation counts to C2 only (C3-excluded). Realistic decode-safe rateable
   benign yield ≈ **4–8** (mostly eventual-present + body-tell), NOT 35.
3. **Calibration:** run the LARGEST the achieved supply permits (bounded by BOTH the ~4-8 benign AND
   the ~16-rateable-genuine — currently ~9-10 positive case-runs, likely the binding side); DISCLOSE the
   sub-50 calibration + the pooled-κ(n≥50)-basis loss + the binding side (freeze L309/L306).
4. **Framing (already in rev-2.1 §8):** the S2 stratum is a CONSTRUCTED trap stratum; MIST's timeout FP
   on eventual-present traps = a documented read-back-oracle limitation; natural-prevalence cites S3
   only. The presence-defuser structural-scarcity finding JOINS this as an honest disclosure.

## Live-confirmation steps still owed (before counting)
- OTel: verify a bounded-backlog eventual-present capture beyond w120 (accounting scale-0→buffer→drain
  with a magnitude grounded in a documented SLO; render-shape = absent-at-cap→present-at-reprobe).
- TeaStore: cheaply verify whether ANY genuine retry-heal / eventual-present exists (survey says it does
  NOT gracefully degrade → expect ~0; disclose).
- Re-confirm the 2 dedupe/no-op render-shapes (present-row + body `status:0`) under the R1 cadence pin.
- Then: pin the final counts, the calibration size, the ceilings (§5) — and write RESULT-r1d.
