# SUT-2 (Sock Shop) benign FP probe — RESULT OF RECORD

**Date:** 2026-07-04 · **Phase:** G3 β external-validity (prereg C-pin 4, bar v2)
**Status:** result captured; pending ≥3-cold-review of the RESULT.
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
/ 10000 ms) — the zero is not a timeout artifact; every benign write appeared well within the cap
(observed `polls=1`, ~12–19 ms).

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
- **Async disclaimer:** Gate-1/this probe measure the SYNC stratum only; no broker-mediated async
  write path is claimed. (report `fpProbe.asyncDisclaimer`.)
- **Accept-then-drop trap:** disclosed as having no representative on this stratum (report field).
- **`gatedMode: NOT_EVALUATED`** is the async/D-span gated mode (deferred to G3), orthogonal to the
  sync FP probe reported here.

## What it supports / does NOT claim

- **Supports:** external validity of the oracle's FALSE-POSITIVE behavior — on a second,
  independently-built system with a different collection encoding (HAL) and a different auth model
  (cookies), the benign FP rate is 0.0 with a fully-resolved gate. This is the SUT-2 half of the
  generalization story (the TT half is Gate-1's 0/2127).
- **Does NOT claim:** any SUT-2 detection/constructed-positive result (branch β has none by design);
  any async-FP result; any breadth-bindability claim (that is Rider-2, separate). External-validity
  of the DETECTION shape remains the TT head-to-head + the Rider-2 bindability fraction.

## Next
≥3 independent cold reviewers on THIS result (report↔records consistency; bar-v2 compliance;
gate-resolution soundness; HAL-dependency disclosure; no overclaim). Then update memory + FILE_INDEX.
