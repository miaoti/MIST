# Excluded — F-corpus (retired from the benchmark-of-record 2026-07-21, user-directed)

Retired from the benchmark-of-record (**33 → 27**; 12 positive / 15 negative). NOT deleted —
provenance preserved; simply out of the headline in-scope corpus.

**5 CORRUPTED-WRITE cases** — out of MIST's read-back scope BY DESIGN (MIST detects acknowledged-but-
LOST writes, not acknowledged-but-CORRUPTED / present-but-wrong):
- `TT-user-selection-corrupt-f8-001` (ts-user documentType 1→0)
- `TT-order-contact-corrupt-f10-001`
- `TT-cancel-status-recheck-corrupt-f11-001`
- `TT-basic-price-corrupt-f14-001` (ts-basic price)
- `TT-order-statusskew-corrupt-f20-001`

These stay available as a DISCLOSED out-of-scope BOUNDARY appendix (demonstrating MIST's lost-vs-
corrupted boundary) if the paper wants it — captured + labeled, just not in the in-scope count.

**1 IN-SCOPE lost-write variant**, retired because its site is already covered:
- `TT-cancel-refund-asyncrefund-f1-001` — an async-refund variant on the cancel-refund site, which is
  already covered by `TT-cancel-refund-fabricatedack-001` in the benchmark-of-record (same-site
  variant, not a distinct site).

**OWED FOLLOW-UP:** the E2 scoring/census/freeze counts still reflect the full 33 — they need
re-scoping to the 27 benchmark-of-record (bundle with the depdown `mist_readback` re-score).
