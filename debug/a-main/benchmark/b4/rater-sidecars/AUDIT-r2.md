# R2 rater-facing leak-audit + de-risking dry-runs — 2026-07-14

Wave E1+R2 (`debug/a-main/c2c3/wave-e1r2-plan.md` rev 2), §2. This log is the durable
record of the corpus-wide narrative-leak neutralization + the mandatory manual read-through
(§2.3) + the de-risking dry runs (§2.4). Reproduce with `neutralize_rater_sidecars.py`
(builds `rater-sidecars/*.json`) then render each through `b4_harness.render()`.

## Scope
25 CAPTURED rated cases (the 26 corpus cases minus `teastore-order-depdown-specified-001`,
`capture_status: specified`, no sidecar). Each gets a canonical, rater-clean derivative
sidecar under `rater-sidecars/<case_id>.json`. The capture-evidence sidecars under
`captures/**` are UNTOUCHED (provenance); these are neutralized derivatives.

## The narrative-leak defect (the BLOCKING fold, verified live at plan-review)
`b4_harness.render()` L97-99 emits the sidecar `probe`/`body` VERBATIM into the rater
`case.md`, while `_scan_banned` only checked a tool/label vocabulary. ≈18 capture sidecars
narrated the injection MECHANISM in their probe prose ("accounting scaled 0, past ~25s
read-back cap", "reads pass under maintenance", "reads are NOT severed by the VS",
"post-VS-teardown", "the fault artifact is the missing child") — none tripped the old
`BANNED_STRINGS`. This is a corpus-wide pre-rater defect; R2 fixes it.

## What was neutralized (per-record transform, `neutralize_rater_sidecars.py`)
- **probe** → the PLAIN canonical read-back locator (from the case's own `readback.locator`
  leading token, or the canonical SQL keyed on the marker). No mechanism prose, no epoch.
- **HTML bodies** (TeaStore profile pages, 10 KB) → a computed **presence descriptor**
  ("orders table: order '<marker>' PRESENT|ABSENT"), presence computed from the REAL
  captured HTML (marker membership), never hand-set. Ack HTML → "<success-shaped page
  rendered>".
- **injection-mechanism narration + epoch** → removed (probe canonicalization drops the
  parentheticals; `epoch_ms` also added to `BANNED_STRINGS`).
- **version_ref** → a bare version label per SUT (was leaking the kind cluster name
  'mist' + deploy internals like "Kieker-only, trace-uninstrumented").
- **split TeaStore transcripts** → MERGED: the write phase (`sidecar.json`: login → add →
  confirm → ack) + the durable read-back phase (`sidecar-postrestore/postteardown.json`),
  the in-window narrating read DROPPED.
- **oteldemo control/lost** → the decisive durable SQL read-back (present / absent) is NOT
  in the capture sidecar (its read-back is the cart-emptied observation); it is sourced
  from the `readback-psql.txt` evidence and rendered as the canonical SQL observation.
- **clean-by-construction**: after transform, every field is re-scanned; any residual
  banned token is elided to `<elided>` + logged. **Elision log: 0** (nothing needed the
  safety elision after the designed transform).

## Manual read-through (§2.3) — findings the automated scan CANNOT catch, and their fixes
The hardened `BANNED_STRINGS` is necessary-NOT-sufficient. Reading all 25 rendered
`case.md` surfaced four real issues; all fixed in the neutralizer (rev 2) EXCEPT the last,
which is disclosed:

1. **LABEL-ENCODING MARKERS (fixed).** The capture markers systematically encoded the
   control/fault label: `TSMWC1`/`M2C1`/`R1C…` (control) vs `TSMWF1`/`M2X1`/`R1X…` (fault);
   TrainTicket `…Breadth Ctl`/`…Breadth Flt`, `BR-CTL-…`/`BR-FLT-…`, `NoOp`, and the
   `-TRACED` capture-variant suffix. A rater who knew the convention could read the label.
   FIX: the teastore/marker tokens are replaced with a neutral display marker
   (`corpus-marker`) — presence is computed against the REAL marker first, then displayed
   neutrally; TT tell tokens are substituted (`Breadth Contact`, `BR-…`, `Corpus`,
   `-TRACED`→∅). Post-fix tell-scan for `ctl|flt|traced|masked|lostwrite|swallowed|
   meshsever|noop|TSMW[CF]|M2[CX]|R1[CX]` over all 25 rendered `case.md` = **0 hits**.
2. **ORDERITEMS OBSERVATIONS MISLABELLED (fixed).** The two persistence read-backs
   (parent orders + child order-items) were assigned canonical probes by POSITION, which
   was backwards for the control and undefined for the empty child read. FIX: dispatch each
   observation by BODY SHAPE (a `productId`/`quantity` body → line-items; an `addressName`
   body → parent order; an EMPTY body → the lost child line-items, count 0, since the
   parent is always non-empty in these cases). Result now faithful: masked = parent
   PRESENT + "line items: 0 item(s)" (the partial-aggregate loss); control = parent
   PRESENT + "line items: 1 item(s)".
3. **SQL PROBE MARKER INCONSISTENCY (fixed).** The eventual/durable SQL probes showed a
   literal `<marker>` while the payload showed the real value. FIX: the real (opaque)
   marker is substituted into the canonical SQL probe so probe and payload agree.
4. **COLLECTION READ-BACK LEGIBILITY (DISCLOSED, not fixed — gated to the Step-5 seal).**
   The TrainTicket admin cases (`adminbasic`, `adminroute`) read back a GLOBAL collection;
   the acting record may fall outside the 600-char truncation window, so a rater may not
   SEE whether the acting contact/route is present. Rendering the acting-record
   presence/value/count is PER-ENDPOINT (membership for contacts/routes/orders; VALUE for
   cancel-refund balances; COUNT for order-items) — that is rater-cut assembly, which the
   plan gates to the IRB/M-yield-gated Step-5 seal (§3). The current truncated bodies are a
   de-risking approximation, disclosed here. (Note: the TeaStore/SQL read-backs already
   render as clean presence/count descriptors, so this residual is scoped to the TT admin +
   global-account collections.)

## Blind-id invariant (confirmed)
`b4_harness.render()` writes `# Case <opaque_id>` as the title. The opaque id is an assembly
INPUT and MUST be a blind token, never the true `case_id` (else the title leaks e.g.
"lostwrite"/"meshsever"). Rendering the 25 with blind `case-NNN` ids = 0 leaks; the sidecar
`case_id` field is NOT rendered (allow-list).

## De-risking dry runs (§2.4 — RE-RUN at the real seal; here they de-risk)
- **Disjointness (partial):** stratum S1=19, S2=6; label positive=10, negative=15; SUTs
  trainticket 11 / teastore 6 / oteldemo 5 / sockshop 2 / bookinfo 1. `|S3|=0`
  (adjudicated-wild empty) and the M-yield stratum is UNMERGED → this is a PARTIAL
  disjointness check, as disclosed.
- **Determinism:** 25/25 render to identical bytes on a re-render. `corpus_content_hash`
  (sorted sidecar sha256 roll-up) recorded in `MANIFEST-r2.json`.
- **Tell-audit (rendered-feature → hidden-label confusion):**
  - `sut` and `num-observations`: NO single-label separation (good — a rater cannot
    shortcut on which SUT or how many probes).
  - `terminal-readback-form`: `ABSENT`/`empty[]` appear only with positives (n=3 total).
    This is the INHERENT domain signal MIST detects (absence under a clean ack = loss),
    rendered so the rater CAN reason to it — NOT a spurious tell, and NOT a perfect
    predictor (6 positives render a non-empty body: fabricated-ack balance-present,
    order-items parent-present, etc.).
  - `readback_shape`: PERFECTLY predicts the label (`none`⟺positive, since positives OMIT
    the field by schema design). This is a case-file **META** field that `b4_harness` never
    renders (the allow-list reads only `sut`+`records`), so it is INVISIBLE to raters —
    confirmed. Flag: `readback_shape` must never be used as a feature in any blind analysis,
    and must stay stripped from any rater-facing artifact (the harness already guarantees
    this).

## Durable R2 outputs (per plan §2.4 / C-F4)
`rater-sidecars/*.json` (25 neutralized) · `neutralize_rater_sidecars.py` (the transform of
record) · this `AUDIT-r2.md` · `MANIFEST-r2.json` (pre-seal manifest + hash) · the hardened
`b4_harness.BANNED_STRINGS` + `test_b4_harness.py` (green, incl. the new mechanism-narration
fail-loud test). The rater SEAL itself stays OPEN (IRB/M-yield/calibration-gated, §3).
