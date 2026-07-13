# S3 RESULT — 3-cold review reconciliation (the §9 final gate)

Three INDEPENDENT cold reviewers audited `RESULT-s3.md` + the phase docs + the raw artifacts (plan
`s3-wildhunt-plan.md` rev 2.1, freeze §6). **All three returned ACCEPT-WITH-FIXES** — no BLOCKING, no
result-invalidating defect. Each independently reproduced the scientific core from the raw bytes.

## Independently verified by ≥2 reviewers (holds)

- **0 CONFIRMED reconciles exactly** with the three raw `ledger.json` (OTel 499 present + 1 raw-delayed;
  TeaStore 500 present; TT 514 present); whole-tree sweep = `{present, raw-delayed}` only, 0 raw-confirmed,
  0 ERROR, 0 quarantined. N = 500+500+514 = **1514**; K = 1+1+3 = **5**; 3/1514 ≈ **0.20%**. TT per-endpoint
  172/171/171 each ≥100.
- **Detector-correctness TRUE in code** (R2): a non-2xx decisive read-back → `recordReadbackError` →
  `record.error != null` → `classify()` "error", never RAW; a non-2xx re-probe → `ReProbeOutcome.ERROR` →
  "raw-error", never CONFIRMED. TT gateway 429s cannot fabricate a CONFIRMED.
- **Detector NON-vacuous** (R3): all classifications are `present` (oracle bound + W3 gate opened every
  time); the absence branch fired live on `w120` (`TIMEOUT_ABSENT` at cap, re-probe at 328s); the
  raw-confirmed leaf is unit-tested. "0 CONFIRMED" is a genuine measurement.
- **SEALED-MANIFEST 17/17** recomputed-match (all three reviewers). **Blindness:** render 0 BANNED_STRINGS,
  answer key fully stripped. **Pre-registration TIMING:** the freeze STEP-5-AS-AMENDED row is a git-ancestor
  of the P0 engine + all window commits (amended before the data existed).
- **Claim discipline:** only the §0.5 sentences; no cross-population/rate-extrapolation/MIST-superiority
  claim; κ correctly the |S3|=0 degenerate branch.

## Findings + dispositions (all APPLIED)

| # | Sev | Finding (reviewers) | Disposition — APPLIED |
|---|-----|---------------------|------------------------|
| F1 | MAJOR | OTel window ran `10eb19e`, not `5802fa8` (its flag-w120 bundle stamps it); the "pin 5802fa8 identical across all three" claim is contradicted by the sealed evidence (R1+R2+R3, unanimous) | Corrected RESULT-s3/p1/p3/p4 + the benign case: per-window run commits (OTel `10eb19e`, TeaStore `5802fa8`, TT `0fbe00f`); the CLASSIFIER is byte-verified IDENTICAL across all three (diffs = traceId-snapshot reorder + TT pacing/salt, neither in `classify()`); freeze §6 correction row added. Result invariant (0 CONFIRMED). |
| F2/M2 | MAJOR | Benign case fails its own machine schema (4 enum/prose errors) + violates the §2 S2 by-docs+citation invariant (R2, +R3 fold) | Fixed to valid enums (`provenance_class=by-docs`, `source=natural`, `expect_*` tokens); case now validates **0 errors**; freeze §6 amendment authorizes the natural-observation S2 benign (source `natural`, `doc_citation=null` — no completion-bound doc exists per §0.4) as an explicit exception; **machine-schema validation ADDED to the P5 entry-gate**. |
| F3 | MAJOR | RESULT-p4 said the floor-30 was "met from calibration *presents*" — contradicts §4.1 (clean-presents inadmissible, C-B3/A-F10) (R3) | RESULT-p4 carry-forward retracted + corrected to the shortfall branch (clean-presents inadmissible; pool 12 < floor 30, NOT met). |
| M4 | MAJOR | "sync SUTs structurally yield 0" overstated — by-design soft-rejects exist on sync paths (R1) | Qualified everywhere to "0 *acked-absent* shapes"; soft-rejects (TT dedupe `{status:0}`, `tt-s2-contacts-dedupe`) exist, deferred to cross-track corpus + the recommended wave. |
| F2b | MAJOR | Observe-mode measured-recall legs NOT freshly run; §8 marked them "done ✓"; the P0 per-S1-case injector schedule is absent from `s3-p0-pins.md` (R3) | Re-labeled §8/§9 as a **DISCLOSED DEVIATION** (fire-ability rests on the live `w120` absence-branch + unit tests + 2.75-A paired 5/5 + E2 exemplar — NOT a fresh observe-mode CONFIRMED); P0 schedule-pin gap disclosed; low-risk TeaStore observe leg noted as available remedy. |
| m5–m11 | MINOR | rule-of-three i.i.d. caveat; TT calib 19<20; env-guard ground(b) is a discarded literal; lower-bound vs upper-CI conflation; low-stress regime; floor-30 not-yet-met framing; prose-only DB/span cross-checks; RESULT-p3 provenance wording; blind-render ack timing; smoke-dir hygiene; write_index 0-based vs marker 1-based | All folded into RESULT-s3/p1/p3/p4/p5 as explicit caveats/disclosures (each tagged with its finding id). |

## Re-seal + net effect

Edits touched 4 sealed members (the benign case, RESULT-p1/p3/p4) → **SEALED-MANIFEST re-sealed v2**
(fingerprint `5c982d1f…`; v1 `9080dbb8…`). The rendered blind case is **byte-unchanged** (`case_md_sha256
75dce034…` — the metadata fixes are stripped fields, confirmed by re-render). **No headline/number
change: 0 CONFIRMED in N=1514 / K=5, rule-of-three ≤0.20%, scarcity branch — stands.** The fixes are
provenance-disclosure + rating-corpus schema/labeling integrity, exactly the class of defect a
pre-registered-study audit should catch and none that overturns the measurement.

## Residual (disclosed, not fixed — proportionate)

- Observe-mode measured-recall leg not run (disclosed deviation; TeaStore leg available if the venue wants
  a fresh observe-mode CONFIRMED).
- TT calibration 19-vs-≥20 (rate-bar passes; sample-size floor one short; disclosed).
- Benign top-up thin (floor-30 shortfall; pre-registered; power consequence disclosed).
- The dedicated degradation-shaped capture wave (to lift benign power + fix the presence↮label tell) is a
  RECOMMENDATION requiring a new plan + reviewer pass — surfaced for the USER.
