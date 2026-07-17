# RESULT — PWS L1: the MULTI-TOOL real-tool arm (EvoMaster + Schemathesis; RESTler disclosed) — RESULT OF RECORD

**Date:** 2026-07-17 · Status: EXECUTED — ALL 4 EvoMaster sites live-measured (TT's 4th cell confirmed the barrier: reached /cancel, stalled before the booked-order baseline) + Schemathesis 2 sites. **User disposition (2026-07-17):** KEEP the arm;
document exactly what happened and why; ACCEPT this state — the barrier is plausibly
MIST's advantage; do NOT special-case EvoMaster with auth config; stay situation-dependent.

**Tool:** EvoMaster v6.1.1 (LGPL-3.0; JAR sha256 `7aa06eb6211a4a890805047a964ff0cea388a4c33c43a6413e165f5c28f4772a`, gitignored — a version-pinned RUN, never vendored), BLACK-BOX mode, the committed OpenAPI spec per SUT, fixed seed 42, 60 min budget. Reported as a SEPARATE detection experiment — NEVER merged into the matched-recall table (different inputs; the apples-to-oranges rail).

## What happened, per site (the honest record)

| site | acked-2xx baseline | write endpoint reached | verdict | mechanism |
|---|---|---|---|---|
| **TeaStore** order (cartAction confirm; masked = maintenance ON) | **1/9 (11%)** | YES — 22 tests/run vs `/webui/cartAction` | **NOT_INTERPRETABLE (leaning-miss)** | session-gated webui: `confirm` returns a success-shaped **302** (flagged in `_faults`), never a 200; control-vs-fault output **byte-identical** (40 tests, 22 write-tests, 16 potential faults, 11% each) ⇒ the masked loss is INVISIBLE to the tool, but the acked-**2xx** regime the oracle targets was never entered |
| **SockShop** shipping (order POST; masked = source-inherent swallow) | — | run aborted | **TOOL-NOT-RUNNABLE (SUT-unstable-under-load)** | EvoMaster's request storm (~2.5M evaluated calls/hr) **crashed the single-process Node front-end** — restartCount climbed to 2 across two attempts, each crash killing the port-forward and failing EvoMaster's connectivity probe (`Failed to connect API with TCP`). NO SUT hardening was applied (committed shape stands) |
| **OTel** checkout (/api/checkout; masked = accounting scale-0; kafka flag BARRED) | **0/6 (0%)** | reached-but-erroring | **NOT_INTERPRETABLE** | `/api/checkout` requires a **populated cart for the same userId** (a prior `/api/cart` AddItem call) — a multi-step STATEFUL sequence black-box generation does not construct with random userIds; 0 acked-2xx ⇒ the precondition never held. Fault run skipped-by-determination (disclosed: a 0% baseline determines the cell) |
| **TT** cancel-refund (4th site, MEASURED 2026-07-17) | stalled-before-baseline | YES — reached `/cancelservice/cancel` + `/orderservice/order` (735 covered targets) | **NOT_INTERPRETABLE (confirmed)** | reached the endpoint but STALLED on the heavy slow TT graph (5.9% budget in ~50min) and cannot establish the acked-2xx baseline: cancelling needs a REAL booked order (full search→book→pay→confirm flow) black-box random ids cannot assemble. Killed as a determined-confirmatory result |

## Why — the one mechanism, stated precisely

**Spec-only black-box generation cannot establish the acked-2xx WRITE baseline that a
masked-2xx fault hides, because the vulnerable write sites live at the end of valid
multi-step STATEFUL sequences** (register → login → add-to-cart → confirm; book → cancel;
add-item → checkout). A tool that samples requests from the OpenAPI schema with random
values reaches the write *endpoint* (TeaStore: 22 tests hit it) but not the write *success
state* — the ack it would need to see is gated behind session/cart/order state it never
assembles. Where it does reach the endpoint, the masked loss changes nothing in its output
(TeaStore control≡fault), because the loss is durable, not HTTP-visible.

## The framing (user-endorsed): this is plausibly MIST's advantage, honestly stated

MIST is **generation-driven but STIMULUS-based** — it actively drives the vulnerable write
path (the 2.75-A/head-to-head harnesses log in, build the cart, confirm, then read the
durable state back), so it *reaches* the acked-2xx write state a spec-only black-box
fuzzer cannot, and then its read-back oracle catches the masked loss the fuzzer is blind
to twice over (unreached AND HTTP-invisible). The paper reports this as a measured
**reachability barrier** for spec-only black-box tools on stateful write paths — the exact
accessibility gap MIST's approach closes — NOT as a merged recall cell.

## Disclosures (do not spin)

1. **Not configured with auth / stateful sequencing** (user-directed: no special-casing).
   EvoMaster's own startup warning recommends auth setup; we did not add it. This is a
   DISCLOSED SCOPE CHOICE, and it is defensible because the deeper barrier is STATE
   CHAINING, which auth alone does not resolve (checkout needs a populated cart, not just
   a token). White-box mode (per-SUT driver instrumentation) is the heavier alternative we
   also did not take — it is exactly the per-service instrumentation cost MIST avoids.
2. **A hostile-PC reading remains** ("you under-configured the tool"): answered by (1) —
   the barrier is state-chaining, not auth — but the paper must state the config choice
   openly and, if a reviewer presses, the situation-dependent option (add auth to ONE SUT
   to show the state-chain wall persists) stays available (user: "看情况而定").
3. **SockShop is a SUT-fragility datum, not a MIST claim** — the Node front-end's
   instability under load is about that SUT, reported as tool-not-runnable-there.
4. **Determinism:** fixed seed 42; the TeaStore control≡fault byte-identity is a
   determinism-backed invisibility datum, not a sampling fluke.

## Schemathesis (the 2nd real tool; user-directed 2026-07-17 — "加入其他真的tool")

**Tool:** Schemathesis v4.23.0 (MIT, property-based black-box OpenAPI tester, incl. a
STATEFUL phase via OpenAPI links). Same protocol; separate cells (`schemathesis/*-cell.json`).

| site | run | verdict | why |
|---|---|---|---|
| **TeaStore** | 206 cases; 6 server-error ops; 25 unique failures, ALL LOUD (501/500/status-conformance) | **BY-CONSTRUCTION MISS (oracle blindness)** | exercised the write endpoints but its check set is conformance-only (status/schema/content-type/500s) — NO read-back oracle ⇒ cannot see an acked-but-lost write by construction |
| **OTel** | 338 cases; 85 passed / 6 failed; STATEFUL phase REACHED POST /checkout (even flagged a schema-violating-request-accepted gap there); 13 unique failures ALL response-side conformance | **BY-CONSTRUCTION MISS — even with STATEFUL testing** | a stateful-capable black-box tool reached the write endpoint statefully and STILL missed the masked class, because the oracle for acked-but-lost writes does not exist in the tool |

## RESTler — CONSIDERED, NOT RUN (disclosed, situation-dependent)

RESTler (MS; black-box with producer-consumer STATEFUL sequencing) was the high-value 3rd
candidate. NOT RUN: no .NET SDK on the box + RESTler needs clone+build+grammar-compile
(a multi-hour Windows setup, exceeding the plan's ½-day tool-setup stop rule), AND its
unique angle — a stateful tool reaching the write baseline — is ALREADY covered by
Schemathesis's stateful phase (which reached POST /checkout and still missed by oracle
blindness). Disclosed as a situation-dependent addition if a reviewer specifically demands
a producer-consumer-inference tool.

## FRAMING OF RECORD (A2, per the publishability review — 2026-07-17)

The 3-cold publishability review + the lift-plan review converge on the framing:
- **HEADLINE real-tool claim = SCHEMATHESIS's structural ORACLE-BLINDNESS** (run unmodified,
  its stateful phase REACHES the write endpoints, yet its conformance-only oracle set
  CANNOT see the durable loss — a by-construction miss, to be made rigorous with a
  control-vs-fault differential in the A1 leg). This is the strongest, cleanest, non-strawman
  real-tool evidence in the materials.
- **DEMOTED to a hedged secondary note = EVOMASTER's REACHABILITY BARRIER.** Its 4
  NOT_INTERPRETABLE/tool-not-runnable cells are NOT a co-equal "measured barrier": the
  disclosed no-auth config hands a hostile reviewer "you set the baseline up to fail". Kept
  as a secondary observation (spec-only black-box struggles to reach stateful write baselines),
  NOT the headline.
- The paper leads with this + the pre-registered NULLS as thesis (S3 0/1514; presence-defuser
  0/≥8), not as back-half caveats — recorded for the drafting stage (gated).

## The multi-tool synthesis (the anti-strawman result, honestly)

Two real, widely-cited black-box REST tools, neither detects the masked-2xx class, for
TWO COMPLEMENTARY and FUNDAMENTAL reasons:
1. **EvoMaster — a REACHABILITY barrier:** spec-only black-box generation cannot establish
   the acked-2xx WRITE baseline behind multi-step stateful sequences (0-11% acked-2xx;
   TeaStore reached-but-invisible, OTel/TT unreachable, SockShop crashed the SUT).
2. **Schemathesis — a STRUCTURAL ORACLE blindness:** even reaching the write endpoint
   (statefully), a conformance-only oracle set (status/schema/content-type/500s) cannot
   see an acknowledged-but-lost durable write.

Together these are not an EvoMaster quirk — they are properties of the TOOL CLASS
(spec-only black-box + conformance oracle). MIST closes BOTH: it is stimulus-driven (it
REACHES the acked-2xx write state the fuzzers cannot) AND it carries a read-back
differential oracle (it SEES the masked loss the conformance oracles cannot). This is the
paper's accessibility+oracle delta, measured against real tools — reported as a SEPARATE
real-tool applicability section, NEVER merged into the matched-recall table.

## Evidence

`b4/pws/evomaster/{teastore,sockshop,oteldemo}-cell.json` (+ the TT cell at W4);
per-run logs + `report.json` + generated `*_faults.py`/`*_successes.py` test suites;
`extract_verdict.py` (the reachability extractor); `drawback-collision-analysis.md`.
End-state: all tenants at 0.
