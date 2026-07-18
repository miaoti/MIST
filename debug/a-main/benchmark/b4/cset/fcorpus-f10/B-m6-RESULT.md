# B-m6 — F10 (contactsWrongOutputFaultMode) — PASS
**Date:** 2026-07-18 (A3 wave). Real preserve booking flow (contact -> trips/left -> preserve -> order read-back), evidence `legs.log`.
- CTRL: submitted doc `F10DOC425400` -> stored contact `...400` -> **order persists `...400`** (match); preserve ack {1,Success.}
- FLT (corrupt): submitted `F10DOC425430` -> stored contact `...430` (row untouched, perturbation is on the returned copy only) -> **order persists `...431`** (trailing digit rotated) ; preserve ack {1,Success.}
**In-class: CORRUPTED-present** — the booked order carries wrong contact data while booking acks 2xx; the wrong value is CONSUMED from findContactsById's unexpected output by ts-preserve-service. MIST column n_a (out-of-scope-by-design). Occupied site (order-artifact contact fields; C-A4 adjudication: creation-content, distinct field family from cancel-status/refund, but NEVER claimed as a new site).
