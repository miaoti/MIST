# RESULT-of-record — S3 wild-hunt + M-prevalence window

Governing plan: `debug/a-main/c2c3/s3-wildhunt-plan.md` rev 2.1 (UNANIMOUS-cleared). Pre-registration:
`debug/a-main/c2c3/c2-freeze.md` §6 "Step-5-as-amended" (committed BEFORE any window). This is the
RESULT-of-record; it may use **only** the §0.5 pre-committed claim sentences (reproduced verbatim in §2).
Detector pin **`5802fa8`** (identical across all three windows; TT ran the `0fbe00f` tree — a
workload-harness diff only, never the classifier). Phases P0–P5 are committed
(`6da126c`/`5802fa8` P0; P1/P2 prior; `0fbe00f`+`6a1e9e8` P3; `e595583` P4; `309501e` P5).

## 1. Headline

**0 CONFIRMED wild flags in 1514 acked writes across 5 bound endpoints on three deployed OSS
microservice SUTs (OpenTelemetry-Demo, TeaStore, TrainTicket) under the pinned fault-free workload.**
This is the **pre-registered CENTRAL expectation** (honest prior C-M1), not a null path: every acked-lost
behavior ever captured on these SUTs required an *injected* fault, so the natural base rate was expected
≈ 0 — now measured as a LOWER-BOUND-style upper confidence bound.

## 2. Pre-committed claim sentences (§0.5 — the ONLY forms this study may use), instantiated

> **Zero finds.** "0 CONFIRMED flags in **N = 1514** acked writes over **K = 5** bound endpoints on these
> SUTs under the pinned workload; rule-of-three 95% upper bound ≈ 3/N ≈ **3/1514 ≈ 0.20%** on the per-write
> CONFIRMED-flag rate under these conditions; **no cross-population claim.**"

> **κ.** S3-only κ is PRIMARY but **withheld at |S3| < 10** (raw agreement + Clopper–Pearson only); here
> **|S3| = 0 ⇒ the degenerate branch**: the rated set = calibration (+ the Step-4 M-yield stratum when
> merged), **no S3 precision row**; the study still yields calibration κ + the bias audit.

No *any-find* existential sentence applies (0 finds). No rate extrapolation in any direction.

## 3. The M-prevalence datum (both denominators, per SUT)

| SUT | acked writes (denom a) | bound endpoints (denom b) | CONFIRMED | present | raw-delayed | breaker | skips |
|-----|-----|-----|-----|-----|-----|-----|-----|
| OTel-Demo | 500 | 1 (`POST /api/checkout`) | 0 | 499 | 1 | 0 | 0 |
| TeaStore | 500 | 1 (`cartAction` confirm) | 0 | 500 | 0 | 0 | 0 |
| TrainTicket | 514 | 3 (admin-basic configs/stations/prices) | 0 | 514 | 0 | 0 | 1 |
| **TOTAL** | **1514** | **5** | **0** | **1513** | **1** | **0** | **1** |

- **Write-path fraction** 0.5 (each journey = 1 read step per bound write). **Per-endpoint ≥ 100** met on
  every endpoint (TT 172/171/171; OTel 500; TeaStore 500). **ERROR = 0, quarantined = 0, not-acked
  excluded from the denominator.** Operational skips (1, TT journey-4 connection reset) excluded like a
  breaker-window flag — never a detector event.
- **Coverage** = exactly the bound endpoints named above; no claim beyond them. Evidence: the three
  `s3/window-*/` logs + ledgers; the three `s3/calibration-*/` FP records; env-guards audited (P4 §2).

## 4. What was actually run (detector spec + D1 disclosure)

- **Detector-(ii) only (deviation D1, pre-registered):** detector-(i) (`mist_trace_shape`) is unbuilt, so
  the hunt is single-leg read-back absence in MIST observe mode. RAW = acked ∧ error==null ∧
  absent-at-cap (gate ∈ {TIMEOUT_ABSENT, OBSERVED_COMPLETE_ABSENT}) ∧ W3 quarantine gate open; CONFIRMED =
  RAW ∧ the ≥T+5min re-probe STILL absent (same predicate; a non-2xx/VANISHED/bound-hit re-probe is an
  ERROR, never CONFIRMED). A non-2xx *decisive* read-back is routed to ERROR — so gateway 429s (TT) could
  never fabricate a CONFIRMED.
- **Per-SUT double-bar FP calibration PASS** (both bars: present-rate + CONFIRMED ≤ 1/20): OTel 20/20
  present 0 CONFIRMED; TeaStore 20/20; TT 19/19 (1 operational skip). No calibration produced a CONFIRMED.
- **§0.4 async-completion-bound check (per SUT, never manufactured):** TT + TeaStore SYNCHRONOUS (blocking
  proxy / blocking persistence REST) → genuine-eligible; OTel checkout ASYNC with NO upstream completion
  bound → the frozen rubric's async tie-break rules an OTel acked-absence *underspecified, not genuine* BY
  RULE (so even a hypothetical OTel loss forfeits the genuine leg). None of the three yielded any
  acked-absent-at-cap CONFIRMED regardless.

## 5. A-goal mapping — the honest prior HELD

The natural-discriminator headline needed (a) ≥1 CONFIRMED × (b) raters label it *genuine* × (c) on a
traced SUT × (d) the frozen comparator misses. **Leg (a) = 0**, so the natural-discriminator headline is
**NOT available from S3** — exactly as the honest prior (C-M1/C-1) stated up front. The scientific value
is the **inverse**: the scarcity datum QUANTIFIES why the C2 benchmark's positive cases require *injected*
faults (natural acked-loss ≤ 0.20% per write under these conditions), and it does so as a pre-registered
result, not a post-hoc rationalization. The real traced MIST discrimination run remains PRE-REGISTERED +
owed at 2.5/E2 (unchanged by this study); the TT fabricated-ack exemplar stays SYNTHETIC on forked source.

## 6. Assembly readiness, shortfall, and holds (P5)

- **Benign top-up (degradation-shaped ONLY):** achieved supply = **1** (OTel `w120`, bounded eventual
  consistency; both sync SUTs structurally yield 0). The pre-registered **floor-30 shortfall branch** is
  invoked; benign pool = 12 (< 30 < computed ≈42–43). **Power consequence disclosed** (thin benign side,
  async ambiguity under-represented — the C-2 worst case, anticipated). The one benign case
  (`oteldemo-checkout-eventual-benign-001`) is authored, B4-rendered clean (opaque `S3-BENIGN-01`,
  `case_md_sha256 75dce034…`, 0 leak), and SEALED (`SEALED-MANIFEST.sha256`, fingerprint `9080dbb8…`).
- **Tell-audit (cross-track obligation):** the merged benign set must include re-probe-ABSENT-but-benign
  shapes so re-probe presence does not decode the label (C-B3/A-F10).
- **USER-side holds:** IRB (F22) before first contact; per-rater blindness screens/debriefs; **the M-yield
  hold — rating must not begin until the Step-4 M-yield stratum is merged into the sealed mix.**
- **RECOMMENDATION (needs a new plan + reviewer pass):** a dedicated degradation-shaped capture wave on
  the async SUT would lift benign-side power and fix the presence↮label tell.

## 7. Limitations (stated, not hedged)

Detector-(i) unbuilt (D1) → single-leg estimand. No cross-population/prevalence claim in any direction —
the rule-of-three bound is conditional on these SUTs, endpoints, and workload. |S3|=0 ⇒ no S3-precision κ.
The benign top-up is thin (shortfall branch). OTel's async path forfeits the genuine leg by the frozen
rubric even had a loss occurred. Tenants at 0; P6 review is the standing backstop before "claim-ready".

## 8. Definition-of-done (§9) checklist

P0 freeze row + pins committed pre-window ✓ · per-SUT double-bar calibration PASS ✓ · window logs (both
denominators per endpoint, write-path fraction, ERROR/quarantine/breaker buckets, RAW/present-at-re-probe/
CONFIRMED per gate) ✓ · environment-guard records + audit ✓ · every CONFIRMED has a bundle (0 CONFIRMED →
0 bundles; the 1 raw-delayed has its bundle+sidecar) ✓ · provenance-scoped dedup + rediscovery counts (0)
✓ · scarcity branch invoked (no S3 case files) ✓ · top-up captured + sized (1, floor-30 branch) ✓ ·
cadence-uniform B4 render ✓ · our-side gate checks green + SEALED manifest ✓ · hand-over note (USER-side +
M-yield holds, rater-time) ✓ · measured-recall legs cross-referenced ✓ · RESULT-of-record carrying §0
verbatim ✓ · freeze close-out row (this commit) ✓ · docs/memory sync (this commit) ✓ · tenants at 0 ✓ ·
**THEN the 3-cold review of this RESULT** (§9 final gate).
