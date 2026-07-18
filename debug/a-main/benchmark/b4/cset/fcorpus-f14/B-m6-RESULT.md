# B-m6 — F14 (secondClassPriceFaultMode) — PASS (two-surface tell)
**Date:** 2026-07-18 (A3 wave). Real preserve booking (seatType=3 second class), trip D1345, evidence `legs.log`.
- CTRL: batch search (trips/left) economy price **22.5**; booked order persists price **22.5** (match); ack {1,Success.}
- FLT (corrupt): batch search still shows **22.5** (queryForTravels intentionally clean = the search surface stays truthful); booked order persists **50.0** (= distance x firstClassPriceRate, the copy-paste rate bug in the single-travel path preserve uses); ack {1,Success.}
**In-class: CORRUPTED-present** with the strongest rateability signature of the F-corpus: the SAME session's search says 22.5 while the acked order row says 50.0. MIST column n_a. **NEW DISTINCT SITE #2** (ts-basic-service pricing calculation feeding the booking write; outside every previously occupied site).
