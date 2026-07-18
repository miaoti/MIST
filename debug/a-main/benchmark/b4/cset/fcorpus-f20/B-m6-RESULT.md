# B-m6 — F20 (orderStatusSkewFaultMode) — PASS
**Date:** 2026-07-18 (A3 wave). Admin-created order, modifyOrder (GET /order/status/{id}/{status}) sets status 1, evidence `legs.log`.
- CTRL: set 1 -> persisted **1 (PAID)**; ack {1,"Modify Order Success"}.
- FLT (corrupt): set 1 -> persisted **2 (COLLECTED)** ((status+1) mod 7 version-skew); ack {1,"Modify Order Success"}.
**In-class: CORRUPTED-present** — the cross-service status write persists a shifted-but-valid code that means something else to readers; no loud failure anywhere. MIST column n_a. Occupied site (order-status artifact), mechanism/floor credit only.
