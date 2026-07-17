# L3 — TT per-endpoint read-back re-capture: the A3(ii) evidence-block MECHANISM-RESOLVED (2026-07-17)

**Context:** the A3(ii) finding (completion-set wave) was that the 9 `tt-collection-truncation-gated`
cases' committed sidecars carry NULL read-back payloads at ALL layers, so per-endpoint
membership/value/count rendering was EVIDENCE-BLOCKED. L3 (PWS) resolves the mechanism on
the revived TT cancel-refund subgraph: the read-back bodies ARE live-capturable, and the
acting record IS extractable by business key.

## Captured (real, non-null — the direct null-payload fix)

Live GETs on the revived TT (admin token), persisted here:
- `account-readback.json` (11,531 bytes) — the cancel-refund + createaccount read-back
  (`GET /inside_payment/account`; rows `{userId, balance}`).
- `contacts-readback.json` (2,633 bytes) — the adminbasic-contacts read-back
  (`GET /adminbasic/contacts`; rows `{id, accountId, name, documentType, documentNumber, phoneNumber}`).
- `routes-readback.json` (36,280 bytes) — the adminroute read-back
  (`GET /adminroute`; rows `{id, stations[], ...}`).

## End-to-end acting-record demonstration (the full per-endpoint rendering)

A marker-salted contact write + read-back + membership extraction, proving the
per-endpoint acting-record rendering works where the sidecars were null:
- WRITE: `POST /adminbasic/contacts {documentNumber: "L3PWS-1784301173", ...}` → ack 200.
- READ-BACK: `GET /adminbasic/contacts` → 14 rows (`contacts-readback-after.json`).
- EXTRACT (per-endpoint membership by business key `documentNumber`): the acting record
  is PRESENT — full row `{id, accountId, name:"L3 PWS Demo", documentNumber:"L3PWS-1784301173", ...}`.

⇒ membership (present/absent), value (the row fields), and count (collection size) are ALL
renderable per-endpoint from a marker-salted acting record — the A3(ii) block is a
sidecar-persistence gap, NOT a structural impossibility.

## What this un-blocks vs what remains user-side

- **Un-blocked (mechanism proven):** the seal's per-endpoint rendering branch for the 9
  truncation-gated cases is now demonstrably feasible (real bodies + acting-record extract),
  which was the open question in the A3(ii) keep-vs-recapture memo.
- **Seal-time completion (USER-side):** the FULL 9-case re-capture (each case's fault AND
  control legs, marker-salted, with the fault mechanism toggled — fabricatedack for cancel,
  the lostwrite VS/injector for admin) then neutralized into rater renders is the
  USER-witnessed seal step. This note + the captured bodies are the STAGING input showing
  the +9-rateable-units branch is executable (the calibration rehearsal's S1 scenario).

## Hygiene

The demo left one benign marked test contact (`documentNumber=L3PWS-1784301173`,
name "L3 PWS Demo") on the revived TT — harmless, clearly marked; the TT graph is scaled
to 0 at window close (no durable pollution beyond this marked row, which a DB reset clears).
Sealed sets untouched (this is staging).
