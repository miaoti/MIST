# REVIEW RECONCILIATION — Wave R1c (coverage-gap micro-widen) — 2026-07-13

**Plan under review:** `wave-r1c-coverage-gap-microwiden-plan.md` rev 1 (author 2–3 "present-but-wrong
value" cells to close a claimed oracle-coverage gap).
**Reviewers (independent, explicit models):** A = coverage-gap correctness (opus) · B = feasibility /
masking (sonnet) · C = strategic / anti-relapse (opus).
**Gate:** standing /goal rule — a new plan executes ONLY on unanimous ACCEPT.

## Verdicts

| Reviewer | Lens | Verdict |
|---|---|---|
| A | coverage-gap correctness (read the MIST oracle SOURCE) | **REJECT** — the flagship cell is OUT of MIST's scope |
| B | feasibility / honest masking (read the actual fork source) | **ACCEPT-WITH-FIXES** (3 MAJOR, 0 blocking) |
| C | strategic / anti-relapse | **REJECT** — the novelty premise is refuted by the corpus's own flagship |

**RESULT: NOT unanimous (2 REJECT + 1 ACCEPT-WITH-FIXES). R1c as scoped DID NOT EXECUTE.** No fork
image built, no TT window opened. This is the **THIRD** positive-side widening attempt to be rejected
(R1b ≥20, R1c micro-widen) — the evidence that the positive side should NOT be widened is now
overwhelming and convergent.

## A and B do NOT actually conflict (the key synthesis)

A REJECT and B ACCEPT-WITH-FIXES look opposed; they answer DIFFERENT questions and are both right:
- **B (feasibility): can you BUILD a masked present-but-wrong fault?** YES — verified against the real
  fork source (`train-ticket-injection@MIST-trainticket`, `PriceServiceImpl.createNewPriceConfig` /
  `updatePriceConfig`): the ack is built from the in-memory submitted object, never re-fetched from the
  DB, so persisting a corrupted value while acking the submitted value is architecturally maskable.
- **A (oracle scope): does MIST DETECT it?** NO — verified against the MIST oracle source
  (`DataIntegrityRuntime.probeVerdict` L938-949, `valueDiffers` L981-998, `TargetTripleRegistry`
  `ReadbackMode` L68-92) and **independently re-confirmed by me reading the same code**: MIST has
  exactly two read-back modes — MEMBERSHIP (`containsKey`, existence) and VALUE_DELTA
  (`valueDiffers(baseline, current)`, movement from the leg's OWN baseline). There is **no
  claim-vs-value comparison anywhere.** A corrupt-to-wrong-value write (V′ ≠ baseline) →
  `valueDiffers(baseline, V′)=TRUE` → PRESENT → **MIST does NOT fire (a MISS).**

**Synthesis:** the present-but-wrong fault is buildable-but-MIST-misses → **INVALID as a MIST-positive
case** (its `mist_readback_oracle` would truthfully be `no_flag`, contradicting the DoD's implied
`flag`). B's "buildable" does not rescue A's "out of scope."

## The convergent core finding

**There is no valuable MIST-catchable positive left to add by widening.** The value-differs oracle
path's two halves are:
- **catchable** (value did NOT move when it should have = absence-of-transition): **ALREADY COVERED** —
  it is the corpus's E2 flagship `TT-cancel-refund-fabricatedack` (`valueDiffers(50,50)=FALSE` → ABSENT
  → FIRE). C verified this and it directly REFUTES the plan's §1 claim ("distinct code path no current
  case uses" — false; it is the paper's discrimination centerpiece).
- **uncatchable** (value moved to a WRONG value = present-but-wrong): a **MIST blind spot** (A) that no
  current comparator catches either (a schema-valid wrong value passes status/schema/marker/trace) → an
  all-miss case, not a discrimination cell, not worth a fragile TT fork window.

Both reviewers also flagged the **`mechanism=code-level` relapse**: the freeze (§2 L65) defines a
runtime feature-toggle as the `flag` class, so the `if(mist.fault.pricecorrupt.enabled)` guard records
`flag` — the SAME "diversity in a vocabulary the schema lacks" trick that sank R1b, in miniature.

## Decision

1. **R1c REJECTED as a standalone wave; positive-side widening is CLOSED.** Accept-and-disclose the
   **8 distinct positive sites** (C independently enumerated + confirmed: TT 4 / TeaStore 2 / OTel 1 /
   SockShop 1; ~13 with F-corpus eligible-unoccupied) under the freeze §5 pre-registered branch ("if
   distinct sites < 20, THAT is a disclosed finding, not padded away"). No 4th widening attempt.
2. **A's finding is a GIFT to the paper, not a capture task.** MIST's scope boundary is now stated
   precisely: **MIST detects acknowledged-but-LOST (absence / non-movement); it does NOT detect
   acknowledged-but-CORRUPTED (present-but-wrong), by design — a black-box existence/movement oracle
   carries no per-field value contract.** This belongs in the paper's Scope/Limitations section as an
   honest boundary (it shows the benchmark is not a MIST advertisement). NO corpus case needed; a
   captured "MIST-misses" exhibit would require building a claim-vs-value comparator (new tool work,
   out of scope).
3. **Pivot fully to R1d benign-power** — the BINDING constraint (all reviewers, twice). Adopt C's
   one-wave structure: benign-power spine leads (12 → toward the `max(30,50−|S3|)` calibration floor),
   ONE plan / ONE review / ONE window, E1 OpenAPI as the parallel no-tenant track. **Drop the positive
   ride-along** (the only candidate was the MIST-miss present-but-wrong cell — deferred to the scope
   statement in item 2, not captured).

## B's feasibility facts preserved (for the record / any future limitation-exhibit)
- TT price ack is submission-derived → maskable (verified). A correct corrupt-write needs
  **clone-and-diverge** (save a separate corrupted `PriceConfig`, return the submitted one) — NOT the
  skip-persist adminroute pattern; naive in-place mutation leaks the corruption into the ack (+ an OSIV
  dirty-check hazard on the UPDATE path). Would be the corpus's first persist-divergence guard.
- **TeaStore and OTel-Demo have NEVER been source-forked** (all their cases are vendor-flag /
  infrastructure mechanisms; upstream-image-pinned; no local clone) → any second-SUT application-fork
  is a from-zero pipeline; TeaStore (Java) is the more feasible than OTel (Go/C#) if ever pursued.
- `ts-price-service` has never been touched in this arc; the 2026-07-10 nacos-double-write / WSL-flap
  restart-batch risk (commit 3bb8209) collides with any deploy/restore loop → small-batch + contingency.
- N≥4 CREATE probes collide on `PriceConfig`'s unique `route_type_idx` → per-run marker salt (S3
  lesson) or UPDATE-shape probes.

## Disposition
The positive-widening chapter is closed after three rejected attempts; the evidence (source-verified
twice) forces it. This is the honest execution of the user's option-1 ("补缺后披露") once the "补缺"
turns out empty for MIST: disclose the site count, and reallocate the window to the binding benign-power
lift. Carried to the user as a decision-with-notice (not a new fork) — the positive side has no genuine
MIST-valuable gap left, and R1d is the unambiguous critical path.
