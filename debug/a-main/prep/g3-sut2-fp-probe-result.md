# SUT-2 (Sock Shop) benign FP probe — RESULT OF RECORD

**Date:** 2026-07-04 · **Phase:** G3 β external-validity (prereg C-pin 4, bar v2)
**Status:** RESULT REVIEWER-ACCEPTED (3× ACCEPT-WITH-FIXES, all folded — `REVIEW-SUT2-FP-RECONCILIATION.md`).
**Evidence (committed):** `g3-sut2-fp-probe-report.json` (machine-readable), `g3-sut2-fp-probe-records.log`
(the 1200 raw benign records). Run dir (gitignored): `evaluation/suts/sockshop/.runtime/`, runId `1783149661165`.

## Headline

MIST's B2 data-integrity oracle, run **benignly** N=30 on a second, independently-built SUT
(Sock Shop), produced **0 false positives over 1200 acknowledged benign writes**, with the
observation gate **100 % resolved** (every record `OBSERVED_PRESENT`). **Bar v2 verdict: PASS.**

| metric | aggregate | ss-b-address-create | ss-b-card-create |
|---|---|---|---|
| acked benign runs | 1200 | 600 | 600 |
| fp fires | **0** | 0 | 0 |
| fp rate | **0.0** | 0.0 | 0.0 |
| gate histogram | {OBSERVED_PRESENT: 1200} | {OBSERVED_PRESENT: 600} | {OBSERVED_PRESENT: 600} |
| timeout-gated fires | 0 | 0 | 0 |
| invalid runs | 0 | 0 | 0 |

**syncFpBar (bar v2):** `verdict=PASS`, `value=0`, `gateResolvedFraction=1.0`,
`timeoutGatedFraction=0`. Pre-registered bar: non-timeout-gated sync FP ≤ 0.05 over ≥ 20 acked
benign runs, evaluable only while the gate held (gateResolvedFraction ≥ 0.5 AND
timeoutGatedFraction ≤ 0.3). **The reported FP interval `[observed-gated/acked, fires/acked]` = [0, 0].**

**fpVsTimeoutCurve:** FP = 0 at every pre-registered cutoff (500 / 1000 / 2000 / 3000 / 5000 / 7500
/ 10000 ms) — the zero is not a timeout artifact; every benign write appeared on the **first poll**,
elapsed **9–38 ms** (all ≪ the 500 ms smallest cutoff).

## Setup (reproducible)

- **SUT-2:** Sock Shop on kind `mist`, ns `sock-shop` (14/14 up); ingress port-forward localhost:8080,
  Jaeger localhost:16686. Config: `evaluation/suts/sockshop/sockshop-g3-benign.properties` +
  `target-triples.yaml` (beside the conf). Run: `cd .runtime && DEEPSEEK_API_KEY=$(cat REPO/.api_keys/DEEPSEEK_API_KEY)
  java -jar REPO/mist-cli/target/mist.jar REPO/…/sockshop-g3-benign.properties`.
- **Triples (branch β, benign-trap-only, NO fault_flag):** SS-B `POST /addresses` (isolation_key
  street+number) and `POST /cards` (longNum), MEMBERSHIP + FRESH_STRINGS, readback_bound 500,
  read-back `GET /addresses|/cards`.
- **Probe:** `faulty.ratio=0.0` (benign only), `mst.oracle.dataintegrity.fpprobe.runs=30`. Generation
  produced 40 pairing methods (20 variants × 2 write scenarios); the suite ran 30 times ⇒
  30 × 40 = 1200 acked benign records (600 per triple). Quiescence carried from Gate-1 unchanged
  (poll 500 ms / timeout 10 s / trace-settle 3 s). Enhancer + status-exploration OFF (probe purity).
- **Auth:** cookie session (MstAuthHandler `per_jvm_cookie`, register-as-login with a `${unique}`
  username). Confirmed wiring live: every write `acked=true` (2xx), no 401/403.

## Load-bearing dependency: the HAL read-back fix (reviewer-accepted)

This result is only meaningful because MIST parses Sock Shop's Spring HATEOAS read-backs. Sock Shop's
`GET /addresses|/cards` return HAL `{_embedded:{address|card:[..]}}`; before the fix `extractItems`
returned EMPTY for that shape ⇒ every benign write would have read ABSENT (a ~100 % FP storm that is
a parsing artifact). The fix (`extractItems`/`parsesToCollection` learn the HAL `_embedded`
convention) is **3-cold-reviewed and ACCEPTED** (commits `0a16255` + `a8d7d32`,
`REVIEW-HAL-RECONCILIATION.md`; finding `g3-sut2-hal-readback-finding.md`). Every benign record here
is `X-present=true gate=OBSERVED_PRESENT` — i.e. `containsKey` found the freshened `mist-<hex>` key in
the `_embedded` collection, exactly the path the fix enables. The flatten is add-only (monotonic), so
it cannot manufacture a false PRESENT that would MASK a real loss; combined with fresh-unique keys, a
`present` verdict here is a true observation, not an artifact.

**Jar note:** the run used the jar built from `0a16255`; the later fix-wave `a8d7d32` was
test/doc/comment-only (no runtime behavior change — verified), so the result is valid against the
reviewer-accepted code. mist-cli suite 147 green at both commits.

## Cross-check (independent recount)

The committed raw-record file `g3-sut2-fp-probe-records.log` was independently recounted (excluding
comment lines): 1200 `acked=true`, **0** `X-present=false`, 1200 `X-present=true`, 1200
`gate=OBSERVED_PRESENT`, 0 acked records not observed-present — matching the report's aggregate
exactly. (A transient count of "1" during capture was traced to the literal string in this file's own
comment header, not a record.)

## Disclosures (carried from the report)

- **`pairs: []` is correct for branch β:** the SS triples carry no `fault_flag`, so nothing is
  injected and there is no control-vs-fault differential pair — the FP probe IS the whole SUT-2
  deliverable (per `g3-sut2-triples-prereg.md` C-pin 2: carts/orders honestly 5xx on Mongo failure,
  so Sock Shop has no constructed positives; SUT-2 = FP/breadth + wild-hunt only).
- **`gatedMode: NOT_EVALUATED`** is the async/D-span gated mode (deferred to G3), orthogonal to the
  sync FP probe reported here.

### Disclosure-field provenance (review C — READ THIS with the committed JSON)
Two string fields in `g3-sut2-fp-probe-report.json` are **reused Gate-1 constants** emitted verbatim
by the shared `PairedFaultExecutor` fpProbe emitter for EVERY SUT — they were authored for
TrainTicket and are **not** SUT-2-specific analysis. Do not read them as claims about Sock Shop:
- **`asyncDisclaimer`** literally says "No broker-mediated async write path exists on this SUT." That
  is the TrainTicket analysis and is **FALSE as a statement about Sock Shop**, which DOES run a broker
  (RabbitMQ: orders → shipping → queue-master). It is true only of the **measured** SS-B write path
  (`POST /addresses|/cards` are synchronous single-doc Mongo writes, no broker on that path). The
  correct SUT-2 reading: this probe measures the SYNC SS-B stratum; **no async-FP claim is made**
  either way; Sock Shop's async surface (SS-C orders/shipping) is out of scope here (branch β,
  wild-hunt candidate only). The code constant is left unchanged (it is frozen Gate-1 wording used by
  the TT reports); this note is the SUT-2 correction of record.
- **`acceptThenDropTrap`** similarly analyzes TrainTicket's contacts-dedupe and says "no representative
  on TrainTicket." For Sock Shop the correct reading is simply: no by-design accept-then-drop trap was
  exercised on the SS-B path (benign-only, faulty.ratio=0). Reused constant, not SS analysis.

## Threats to validity / scope (from the 3-cold-review — all three converged)

- **The quiescence/gate machinery was NOT exercised on this SUT.** All 1200 writes were present on the
  FIRST poll (9–38 ms), so the observation gate never had to discriminate — every record is
  `OBSERVED_PRESENT` because nothing was ever transiently absent, not because the gate resolved a hard
  case. Sock Shop's SS-B is a synchronously-consistent single-doc Mongo write, so the delay-induced
  false-absent vector (a benign write momentarily absent then appearing — the exact case bar-v2's
  timeout gate + trace-settle exist to handle, where a naive oracle WOULD false-fire) is **not part of
  this SUT-2 evidence**; that rests on TT Gate-1 (0/2127 with a real gate-resolution histogram). So
  SUT-2 demonstrates external validity of the **HAL parsing + exact-match membership + cookie auth** on
  a second collection encoding — **not** of the quiescence machinery.
- **Pseudo-replication — 1200 is correlated, not 1200 i.i.d. designs.** 1200 = 30 pre-registered
  iterations × 40 generated write-shapes (2 endpoints × 20 variants). Freshening makes every one a
  genuinely distinct membership trial (1200 unique keys verified), so "0 over 1200 acked writes" and
  the observed interval `[0,0]` are literal and honest, and the ≥20-run bar is met (1200 aggregate,
  600/triple). **But** the effective count of independent DESIGNS is ~40 (really 2 write endpoints);
  any paper-stage confidence interval must use that correlated structure, NOT treat n=1200 as 1200
  independent scenarios. The `[0,0]` here is a **descriptive observed interval, not a CI**.
- **`readback_bound: 500` was not exercised.** The bound guard sits only on the ABSENT path
  (reached after a timeout with `present=false`); with zero absences it never triggered. "Bound
  honored on SS-B" rests on the unit tests, not this run. Immaterial to FP=0 (an exact fresh-key match
  on a growing collection cannot yield a false PRESENT).
- **Provenance.** The machine report `g3-sut2-fp-probe-report.json` is committed; the 1200 raw records
  (`g3-sut2-fp-probe-records.log`) are a grep-extraction from the **gitignored** `.runtime/logs/mist.log`
  (the full run dir is not committed — standard for this result class). Internal consistency is
  complete (report ↔ extracted records ↔ code-computed bar all reconcile; 1200 unique keys over
  monotonic timestamps spanning ~43 min), so the extraction faithfully represents the run.

## What it supports / does NOT claim

- **Supports:** external validity of the oracle's FALSE-POSITIVE behavior on plain synchronous entity
  CRUD — on a second, independently-built system with a different collection encoding (HAL/HATEOAS
  `_embedded`) and a different auth model (cookie sessions), the benign FP rate is 0.0 with a
  fully-resolved gate. Concretely this validates the **HAL read-back parsing + exact-match membership +
  cookie-auth read-back** path. This is the SUT-2 half of the generalization story; the TT half
  (Gate-1's 0/2127) is what validates the quiescence/gate machinery under real absence.
- **Does NOT claim:** any SUT-2 detection/constructed-positive result (branch β has none by design —
  carts/orders honestly 5xx on Mongo failure); any async-FP result (Sock Shop HAS a broker, but its
  async surface is out of scope here); any quiescence-machinery robustness (not stressed on this
  sync-consistent SUT); any breadth-bindability claim (that is Rider-2, separate). External validity
  of the DETECTION shape remains the TT head-to-head + the Rider-2 bindability fraction.

## Next
FP arc CLOSED (3-cold-reviewed, all fixes folded). Remaining SUT-2 β work: generalization + wild-hunt
(SS-C shipping enqueue-swallow, trace-gated) — secondary. Primary next deliverable: the executable
breadth-bindings YAML for TrainTicket (Rider-2), which needs the env swapped back (Sock Shop → 0, TT → up).

## ADDENDUM (2026-07-04, result-review C-M1): shipping scope SUPERSEDED
The "Does NOT claim any SUT-2 detection/constructed-positive result (branch β has none by design)" line
above described the CARTS/ORDERS write path (honest 5xx on Mongo failure) and predates the SHIPPING
promotion: the wild-hunt shipping enqueue-swallow was subsequently promoted to a full depth head-to-head
(constructed reject-publish stratum + a blind SUT-2 comparator contract + a clean-win detection result) —
see `debug/a-main/g3-comparator-ss/g3-shipping-headtohead-results.md`. Branch β's "no constructed
sensitivity" finding still holds where it was measured (carts); it no longer bounds SUT-2 as a whole.
This FP probe's own claims (benign FP 0.0 on sync CRUD) are unaffected — and note the probe does NOT
stand in for the shipping oracle's specificity control (different endpoint/read-back/mode); that control
is the head-to-head's own live `benign` stratum (MIST NO_FIRE on the queue-depth oracle). Shipping is
banked as DEPTH (second independent SUT), not double-counted as breadth.
