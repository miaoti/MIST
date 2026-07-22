# Excluded — out of the masked-2xx class (retired from the benchmark-of-record 2026-07-21, user-directed)

Retired from the benchmark-of-record (**27 → 26**; 11 positive / 15 negative). NOT deleted —
provenance and all live-run evidence preserved; simply out of the headline in-scope corpus.

This directory is DISTINCT from `../excluded-fcorpus/`. That one holds cases outside MIST's scope
because they are *corrupted-not-lost* (present-but-wrong) or a same-site-covered duplicate. This one
holds a case outside the scope because it is a **loud failure, not a masked (acknowledged-2xx) loss**.

## `teastore-order-depdown-specified-001` — the dependency-down producer

**Why retired (live-oracle-refuted, not merely specified-uncaptured):** the REAL MIST paired oracle
was run end-to-end on this case 2026-07-21 (`../../b4/RESULT-depdown-live.md` §2) and returned
**NO_FIRE 0/4** — `"fault run not acknowledged (http 302, body status null) — base relation vacuous"`.

Under `teastore-db`-scale-0 the whole persistence read path is down, so the order-confirm journey
**500s** (the confirm's own response is a 302 and the followed confirm page is HTTP 500 — the user
lands on an error page). This is a **loud-500 loss, NOT a masked-2xx loss**. Contrast the maintenance
producer (`../teastore-order-maintenance-masked-001`, a live FIRE 5/5): there persistence stays UP,
the create returns a silent `201 / body -1`, and the followed confirm page renders **HTTP 200** — a
genuine masked-2xx acknowledgement. MIST's acknowledged-but-lost class is **2xx-gated**
(`DataIntegrityRuntime` L700 `acked = httpStatus/100==2 && (bodyStatus==null||bodyStatus==1)`), so on
depdown the oracle **abstains by design**.

The 2026-07-20 curl "capture" had read the confirm's **unfollowed** 302 as "masked, no error page";
following the redirect (as the live oracle does) shows the 500. That reading was falsified by the live
run. Independent ground truth still confirms the write was **lost** (fault marker absent from
`/rest/orders`) — so this is a genuine lost-write, just outside the masked-2xx class.

**Value as a boundary case (why it is kept, not deleted):** it is a clean demonstration that MIST
**correctly abstains on loud failures** (it does not over-fire when the client is not told success) —
a principled-abstention example that *strengthens* the 0-FP / precision story, alongside the
lost-vs-corrupted boundary in `../excluded-fcorpus/`. If a genuine *masked* dependency failure is ever
wanted in-corpus, it needs a different producer — one that keeps the read path up so the confirm
journey stays 2xx (e.g. a write-only outage) — which is a new capture, not this one.

**Evidence (all preserved):** `../../b4/RESULT-depdown-live.md` (PREREG + RESULT, 3-cold reviewed),
the three attempt logs + reports + ground truth in `../../b4/cset/teastore-depdown/`, the original
2026-07-20 capture (`depdown-legs.log`, `depdown_capture.sh`, `ts_journey.sh`) preserved UNTOUCHED,
and the case JSON's own `provenance.notes` (the dated refutation leads; the original capture note
follows, its "masked" reading marked falsified).
