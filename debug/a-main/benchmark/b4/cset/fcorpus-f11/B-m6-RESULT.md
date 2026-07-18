# B-m6 — F11 (cancelSeqRecheckFaultMode) — PASS
**Date:** 2026-07-18 (A3 wave). Admin-created PAID orders, 2 cancels per leg, evidence `legs.log`.
- CTRL: both cancels ack {1,Success.}; both orders persist status **4 (CANCEL)**.
- FLT (corrupt): both cancels ack {1,Success.}; order1 persists **4** (recheck ran -> repaired), order2 persists **3 (CHANGE)** (recheck skipped -> wrong status stands).
**In-class: CORRUPTED-present, INTERMITTENT** — the only intermittent corrupted case in the corpus (alternating fallible recheck; deterministic under the static counter: even invocation repairs, odd leaves wrong). Cancel acks success in every invocation. MIST column n_a. Occupied site (cancel-refund constellation), mechanism/floor credit only.
