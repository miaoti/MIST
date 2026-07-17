# RESULT — PWS L1: the EvoMaster real-tool arm — RESULT OF RECORD

**Date:** 2026-07-17 · Status: EXECUTED (3 sites live-measured; TT determined-same, a
confirmatory 4th cell rides along in W4). **User disposition (2026-07-17):** KEEP the arm;
document exactly what happened and why; ACCEPT this state — the barrier is plausibly
MIST's advantage; do NOT special-case EvoMaster with auth config; stay situation-dependent.

**Tool:** EvoMaster v6.1.1 (LGPL-3.0; JAR sha256 `7aa06eb6211a4a890805047a964ff0cea388a4c33c43a6413e165f5c28f4772a`, gitignored — a version-pinned RUN, never vendored), BLACK-BOX mode, the committed OpenAPI spec per SUT, fixed seed 42, 60 min budget. Reported as a SEPARATE detection experiment — NEVER merged into the matched-recall table (different inputs; the apples-to-oranges rail).

## What happened, per site (the honest record)

| site | acked-2xx baseline | write endpoint reached | verdict | mechanism |
|---|---|---|---|---|
| **TeaStore** order (cartAction confirm; masked = maintenance ON) | **1/9 (11%)** | YES — 22 tests/run vs `/webui/cartAction` | **NOT_INTERPRETABLE (leaning-miss)** | session-gated webui: `confirm` returns a success-shaped **302** (flagged in `_faults`), never a 200; control-vs-fault output **byte-identical** (40 tests, 22 write-tests, 16 potential faults, 11% each) ⇒ the masked loss is INVISIBLE to the tool, but the acked-**2xx** regime the oracle targets was never entered |
| **SockShop** shipping (order POST; masked = source-inherent swallow) | — | run aborted | **TOOL-NOT-RUNNABLE (SUT-unstable-under-load)** | EvoMaster's request storm (~2.5M evaluated calls/hr) **crashed the single-process Node front-end** — restartCount climbed to 2 across two attempts, each crash killing the port-forward and failing EvoMaster's connectivity probe (`Failed to connect API with TCP`). NO SUT hardening was applied (committed shape stands) |
| **OTel** checkout (/api/checkout; masked = accounting scale-0; kafka flag BARRED) | **0/6 (0%)** | reached-but-erroring | **NOT_INTERPRETABLE** | `/api/checkout` requires a **populated cart for the same userId** (a prior `/api/cart` AddItem call) — a multi-step STATEFUL sequence black-box generation does not construct with random userIds; 0 acked-2xx ⇒ the precondition never held. Fault run skipped-by-determination (disclosed: a 0% baseline determines the cell) |
| **TT** cancel-refund (confirmatory, W4 ride-along) | determined | — | expected NOT_INTERPRETABLE | the cancel endpoint needs a **real booked order** (a full ticket-booking flow) to cancel; black-box random `orderId`/`loginId` cannot produce one |

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

## Evidence

`b4/pws/evomaster/{teastore,sockshop,oteldemo}-cell.json` (+ the TT cell at W4);
per-run logs + `report.json` + generated `*_faults.py`/`*_successes.py` test suites;
`extract_verdict.py` (the reachability extractor); `drawback-collision-analysis.md`.
End-state: all tenants at 0.
