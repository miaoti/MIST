# PLAN — live real-tool head-to-head — rev 2 (3-cold fold: A/B/C all ACCEPT-WITH-CHANGES, zero REJECT)

**Date:** 2026-07-21 · Status: rev 2 — awaiting 3-cold CONFIRM (standing rule; execute only on
all-confirm) · Owner: main_track
**Mandate:** the unanimous expand-review (`REVIEW-expand-{A,B,C}`): the comparison's most visible
soft spot = no competing tool ran live end-to-end. User approved executing this lever (2026-07-21).
**Reviews folded:** `REVIEW-livetool-{A,B,C}` (fold log at the bottom).

## Rev-2 headline changes
1. **Track E (EvoMaster) = THE wave.** All A/B/C fixes folded below; ~1-2 TeaStore tenancy windows.
2. **Track T (Tracetest live) = DEFERRED-WITH-PRINCIPLED-DISPOSITION, not run.** All three
   reviewers independently verified the rev-1 premise was FALSE: the authored specs
   (`b4/e2/tracetest-specs-authored.yaml`) are **TrainTicket-only** (cancel-refund; the only
   working runner targets TT adminroute) — **no bookinfo/sockshop specs exist**, and authoring
   new specs mid-wave is exactly the rigging surface (A3). Combined with C4 (Track T is largely
   REDUNDANT now that E-ANOM ships as the first-class in-headline trace competitor), the honest
   disposition: **Tracetest stays a LABELED surrogate** + authored-never-executed TT specs (the
   authoring-cost cell); no live Tracetest cell is claimed, so no "ran it only where easy"
   surface exists (A4/C3 — we claim NO live T cells rather than cherry-picked ones). The
   named-third-party-live-tool cell is carried by **EvoMaster (this wave) + the already-live
   Schemathesis (PWS L1)**. **USER-ELECTABLE OPTION (preserved, not in this wave):** a dedicated
   TT window running the existing TT specs live (~3-4 windows incl. TT revival; B5's DataStore
   wiring + B6's async-visibility check become binding preconditions if elected).

## Track E — EvoMaster, auth+seeded-cart, on TeaStore maintenance (rev 2)

**Label of record (A1):** "black-box + operator-provisioned auth **+ seeded cart**" — never plain
"black-box". The PWS L1 spec-only cells RETAIN the tool-class barrier datum; this wave executes
the PWS-anticipated auth-demo proactively under explicit user approval (C1), REVERSING the
2026-07-17 no-auth-special-casing rail — disclosed here and in the RESULT (A-flag).

**Claim bounding (C1/C2):** the deliverable sentence is exactly — "on the write made REACHABLE by
operator-provisioned auth + seeded state, the tool's oracles (5xx/schema) do not flag the masked
loss; MIST's read-back does." TeaStore's barrier = auth/session (cleared here); oteldemo/TT
barriers = deeper state (the L1 cells stand). If reachability fails anyway, the PRE-REGISTERED
outcome is `NOT_INTERPRETABLE-well-configured` (recorded, disclosed, no retry-shopping).

**Protocol:**
1. Bring up TeaStore (kind `mist`, PVC-backed db; single tenant).
2. Session prep per leg: curl login `user21` → capture `JSESSIONID`; **seed the cart** (one curl
   addToCart in that session). Disclose: seeding makes the confirm reachable even if the tool
   never chains addToCart; the tool's own addToCart→confirm chains are RECORDED SEPARATELY as
   the autonomous-reachability datum.
3. **Verdict surface pinned to the webui endpoints {loginAction, cartAction}** (A2; B'-confirm
   fix: the E1 spec has no `category` path — browsing is a `/rest/products` query). This is an
   evaluation-layer SCORING pin, not an input restriction: **EvoMaster runs black-box on the FULL
   E1 spec** (the L1 precedent — same spec lineage as every arm); the masked-loss verdict is
   SCORED at the user-facing write (`POST cartAction?confirm=Confirm`), because the webui is what
   masks — the persistence layer's maintenance-mode `201/-1` is the mechanism, not the mask, and
   a MISS scored against a direct `/rest/orders` POST would be a weak-oracle strawman. The
   spec's persistence paths are read-back definition, disclosed as out-of-verdict-surface.
4. CONTROL leg: maintenance OFF → EvoMaster **v6.1.1** black-box, budget 60 min,
   `header0="Cookie: JSESSIONID=…"` (B1 — 6.1.1 has NO `--header`; `header0` verified against
   `--help`). Record: confirm-acks; `/rest/orders` durable delta (B2 — the reachability GATE:
   **control delta ≥1 else NOT_INTERPRETABLE-well-configured, STOP**); EvoMaster-reported faults;
   autonomous chains.
5. FAULT leg: maintenance ON (toggle = **POST `/rest/generatedb/maintenance` JSON; NEVER GET
   `/rest/generatedb`** [DB-wipe]; toggle VERIFIED from outside before the run else STOP) → same
   budget/header/fresh session+seed. Record the same + the masked-loss ground truth (acks with
   `/rest/orders` delta 0) + a **per-endpoint status census under maintenance** (A5 — "reads
   pass" is verified for the reach path login/category/cart, not asserted for the full surface).
6. VERDICT CELL → `b4/pws/evomaster/teastore-auth-cell.json` (the SEPARATE detection table —
   never merged into matched-recall): did ANY tool-reported fault correspond to the masked loss?
   5xx noise on other endpoints = noise, recorded, not detection. MIST's corpus cell sits
   alongside as context, never pooled.
7. Restore maintenance OFF + probe-order verify; TeaStore → 0 replicas.

**Pins (B3):** jar `tools/evomaster/evomaster-v6.1.1.jar`, sha256 `7aa06eb6…772a` (full sha
recorded in the RESULT). Same E1 spec lineage as every other arm; webui sub-surface disclosed.

**Aborts:** cookie expiry → re-login ONCE (second → partial + disclose); tool crashes the SUT →
TOOL-NOT-RUNNABLE-here (the SS L1 precedent), stop; toggle unverifiable → STOP; control gate
fails → NOT_INTERPRETABLE-well-configured, stop.

**Honesty rider (C5):** this wave is a SOFT-SPOT-CLOSER (blunts "no real tool ran live"), not a
ceiling-raiser (the rater study + operating-point reframe are). Reported accordingly.

## Rails (unchanged from rev 1)
Fairness (same spec lineage, no MIST-side hints, corpus-own faults) · honesty (mandatory
reachability datum, noise/detection split, partials recorded, deviations logged) · tenancy (one
SUT, restore + scale-to-0) · reporting (separate detection table; RESULT-of-record
`b4/RESULT-live-tool-h2h.md`; 3-cold result review per DoD).

## Fold log (rev 1 → rev 2)
- A1 seed-cart + relabel → §Track-E label + protocol 2. A2 webui verdict surface → protocol 3.
- A3 specs-don't-exist → Track T re-disposition. A4 no-overclaim → we claim NO live T cells.
- A5 per-endpoint census → protocol 5. A-flag auth-rail reversal disclosed → label block.
- B1 `header0` syntax → protocol 4. B2 delta-gate + abort → protocol 4/aborts. B3 jar sha → pins.
- B4 TT-only specs (hard) → Track T re-disposition. B5 DataStore wiring + B6 async visibility →
  moot this wave; binding preconditions of the preserved TT option.
- C1 claim bounding + PWS reconciliation → claim block. C2 NOT_INTERPRETABLE branch → claim
  block + gate. C3 principled TT reason → Track T re-disposition. C4 redundancy/de-scope →
  Track T re-disposition. C5 honesty rider → rider. C6 never pool live with surrogate → moot
  (no live T cells); the rail is recorded for the preserved option.
