# RESULT-of-record — S3 wild-hunt + M-prevalence window

Governing plan: `debug/a-main/c2c3/s3-wildhunt-plan.md` rev 2.1 (UNANIMOUS-cleared). Pre-registration:
`debug/a-main/c2c3/c2-freeze.md` §6 "Step-5-as-amended" (committed BEFORE any window). This is the
RESULT-of-record; it may use **only** the §0.5 pre-committed claim sentences (reproduced verbatim in §2).
**Detector provenance (corrected post-review, F1 unanimous).** The three windows RAN at three different
commits — **OTel `10eb19e`** (its `flag-w120.json` bundle stamps `mist_commit=10eb19e`), **TeaStore
`5802fa8`**, **TT `0fbe00f`** — but the CLASSIFIER (`classify()` + the RAW/CONFIRMED predicate) is
**byte-identical across all three**. The inter-commit diffs are: `10eb19e`→`5802fa8` reorders
`e.traceId=` before the `onAckedAbsent` hook (the §2d RAW-time trace snapshot; `traceId` is never read
by `classify()`); `5802fa8`→`0fbe00f` adds the TT gateway pacing knob + per-run marker salt + runner —
none touches the classifier. With 0 CONFIRMED the trace-export path is never exercised, so the result is
provably invariant to all three commits. (Earlier drafts mislabeled a single pin `5802fa8` "identical
across all three windows"; that was inaccurate for OTel and is corrected here.) Phases P0–P6 committed
(`6da126c`/`5802fa8` P0; OTel P1 `d6e1524` ran `10eb19e`; TeaStore P2 `6a448f7` ran `5802fa8`; TT P3
`0fbe00f`+`6a1e9e8`; P4 `e595583`; P5 `309501e`; P6 pre-review `c32d5ad`).

## 1. Headline

**0 CONFIRMED wild flags in 1514 acked writes across 5 bound endpoints on three deployed OSS
microservice SUTs (OpenTelemetry-Demo, TeaStore, TrainTicket) under the pinned fault-free workload.**
This is the **pre-registered CENTRAL expectation** (honest prior C-M1), not a null path: every acked-lost
behavior ever captured on these SUTs required an *injected* fault, so the natural base rate was expected
≈ 0. Two distinct correctly-computed quantities (kept separate — F5): (i) the **estimand** is a
conservative LOWER BOUND on true prevalence (the single-leg detector under-counts — W3 quarantine
excludes always-lost sites, D1 excludes trace-visible losses); (ii) with 0 observed events, rule-of-three
gives a 95% UPPER confidence bound of ≈ 3/1514 ≈ 0.20% on the per-write CONFIRMED-flag *rate*.
**Regime caveat (F6):** this is measured in the least-failure-prone regime — single-threaded, low-rate,
healthy-cluster, fault-free; acked-loss is likeliest under concurrency/load/partial failure, which this
workload deliberately excludes, and the 0.20% bound does NOT exclude rare natural loss under load.

## 2. Pre-committed claim sentences (§0.5 — the ONLY forms this study may use), instantiated

> **Zero finds.** "0 CONFIRMED flags in **N = 1514** acked writes over **K = 5** bound endpoints on these
> SUTs under the pinned workload; rule-of-three 95% upper bound ≈ 3/N ≈ **3/1514 ≈ 0.20%** on the per-write
> CONFIRMED-flag rate under these conditions; **no cross-population claim.**"

> **κ.** S3-only κ is PRIMARY but **withheld at |S3| < 10** (raw agreement + Clopper–Pearson only); here
> **|S3| = 0 ⇒ the degenerate branch**: the rated set = calibration (+ the Step-4 M-yield stratum when
> merged), **no S3 precision row**; the study still yields calibration κ + the bias audit.

No *any-find* existential sentence applies (0 finds). No rate extrapolation in any direction.
*Independence caveat (F4):* rule-of-three treats the 1514 acked writes as i.i.d. Bernoulli trials; they
are serially/clusterally dependent (500 on a single OTel endpoint, autocorrelated within one observe
session, only 5 endpoints), so 3/1514 is best read as a NOMINAL upper bound, not a strict 95% CI — the
"under these conditions / no cross-population" conditioning is what the claim actually rests on.

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
  present 0 CONFIRMED; TeaStore 20/20; TT 19/19. No calibration produced a CONFIRMED. **Disclosed (F4/#3):
  TT reached 19 acked (not the pre-registered ≥20) — one journey hit an operational SocketException skip;
  the CONFIRMED-rate bar passes as a rate (0/19 = 0% ≤ 5%), but the ≥20 sample-size floor is literally one
  short. Reported here rather than re-reviving TT (scaled to 0) for a single write.**
- **§0.4 async-completion-bound check (per SUT, never manufactured):** TT + TeaStore SYNCHRONOUS (blocking
  proxy / blocking persistence REST) → genuine-eligible; OTel checkout ASYNC with NO upstream completion
  bound → the frozen rubric's async tie-break rules an OTel acked-absence *underspecified, not genuine* BY
  RULE (so even a hypothetical OTel loss forfeits the genuine leg). None of the three yielded any
  acked-absent-at-cap CONFIRMED regardless.

## 5. A-goal mapping — the honest prior HELD

The natural-discriminator headline needed (a) ≥1 CONFIRMED × (b) raters label it *genuine* × (c) on a
traced SUT × (d) the frozen comparator misses. **Leg (a) = 0**, so the natural-discriminator headline is
**NOT available from S3** — exactly as the honest prior (C-M1/C-1) stated up front. The scientific value
is the **inverse**: the scarcity datum is **CONSISTENT WITH** (does not by itself PROVE — F6) the premise
that the C2 benchmark's positive cases require *injected* faults; ≤ 0.20% per write under these conditions
is a pre-registered result, but an equally sufficient explanation for 0 is simply that a benign
low-concurrency regime is where acked-loss is least expected, independent of the injected-fault prior. The
real traced MIST discrimination run remains PRE-REGISTERED + owed at 2.5/E2 (unchanged by this study); the
TT fabricated-ack exemplar stays SYNTHETIC on forked source.

## 6. Assembly readiness, shortfall, and holds (P5)

- **Benign top-up (degradation-shaped ONLY):** achieved supply = **1** (OTel `w120`, bounded eventual
  consistency). The sync SUTs yield 0 of the *acked-absent / eventual-consistency* sub-shape — but
  by-design **soft-reject** benigns DO exist on sync paths (e.g. TT admin-basic duplicate-key `{status:0}`
  "already exists"; the `tt-s2-contacts-dedupe` family); no dedicated ~25–30 degradation-shaped batch was
  captured, so these were deferred to the cross-track corpus + the recommended capture wave (M4-corrected:
  not a structural impossibility). The pre-registered **floor-30 shortfall branch** is invoked; benign
  pool = 12 (11 legacy + 1), and with genuine supply ~7–8 the current rated set is ~20 — **the floor 30 is
  NOT met by current supply; it is contingent on the not-yet-merged M-yield stratum** (m11). **Power
  consequence disclosed** (thin benign side, async ambiguity under-represented — the C-2 worst case,
  anticipated). The one benign case
  (`oteldemo-checkout-eventual-benign-001`) is authored, B4-rendered clean (opaque `S3-BENIGN-01`,
  `case_md_sha256 75dce034…`, 0 leak), and SEALED (`SEALED-MANIFEST.sha256`, fingerprint `5c982d1f… (v2)`).
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
rubric even had a loss occurred. Measured in the least-failure-prone regime (§1 caveat). Tenants at 0;
P6 review is the standing backstop before "claim-ready".

**DISCLOSED DEVIATION (F2, added post-review) — observe-mode measured-recall legs were NOT freshly run.**
Plan §5/§7 pre-registered one observe-mode measured-recall leg per SUT after each counted window; in
practice none was freshly executed. The detector's *fire-ability* (that 0 CONFIRMED means "no losses",
not "a broken detector") rests instead on: (i) the live absence-branch + re-probe machinery DID run in
observe mode on `w120` (`TIMEOUT_ABSENT` at cap, re-probe executed at 328s); (ii) `WildHuntEngineTest`
unit-tests the `raw-confirmed` leaf + the traceId-before-hook; (iii) the 2.75-A MEASURED MIST read-back
FIRE 5/5 (paired mode) + the E2/fabricated-ack exemplar. What is NOT demonstrated is a *fresh
observe-mode end-to-end CONFIRMED on an injected loss*. Relatedly, the per-S1-case injector schedule that
plan §5 [A-R4]/freeze pin 12 says was "pinned at P0" is ABSENT from `s3-p0-pins.md` (disclosed gap; it
was effectively chosen at RESULT time). Not load-bearing for the 0-CONFIRMED number (|S3|=0). Remedy
available (not run here): the low-risk TeaStore maintenance-toggle observe leg would supply the missing
fresh observe-mode CONFIRMED.

## 8. Definition-of-done (§9) checklist

P0 freeze row + pins committed pre-window ✓ · per-SUT double-bar calibration PASS ✓ · window logs (both
denominators per endpoint, write-path fraction, ERROR/quarantine/breaker buckets, RAW/present-at-re-probe/
CONFIRMED per gate) ✓ · environment-guard records + audit ✓ · every CONFIRMED has a bundle (0 CONFIRMED →
0 bundles; the 1 raw-delayed has its bundle+sidecar) ✓ · provenance-scoped dedup + rediscovery counts (0)
✓ · scarcity branch invoked (no S3 case files) ✓ · top-up captured + sized (1; **floor-30 shortfall
branch — floor NOT met by current supply, contingent on the M-yield merge**, §6/m11) ⚠ · cadence-uniform
B4 render ✓ · our-side gate checks green (incl. **machine-schema validation** of the benign case, added
post-review — passes 0 errors) + SEALED manifest ✓ (re-sealed v2 post-review-fixes) · hand-over note
(USER-side + M-yield holds, rater-time) ✓ · **measured-recall legs = DISCLOSED DEVIATION (NOT freshly
run; §7)** ⚠ · RESULT-of-record carrying §0 verbatim ✓ · freeze close-out row ✓ · docs/memory sync ✓ ·
tenants at 0 ✓ · **3-cold review DONE (all ACCEPT-WITH-FIXES; reconciliation
`REVIEW-S3-RESULT-RECONCILIATION.md`, fixes applied)** ✓.
