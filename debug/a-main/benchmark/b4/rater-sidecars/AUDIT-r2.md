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
   **[Corrected post-review (B-5, C-7i):]** The TrainTicket global-collection read-backs
   (`adminbasic`/`adminroute` collections AND the `inside_payment/account` balance list used
   by the cancel-refund + createaccount cases — 9 TT cases total) render the acting record
   **beyond** the 600-char truncation window (measured: bodies 5.7–36 KB, the acting row
   appended last — it always falls outside, not "may"), so these 9 are currently
   UN-RATEABLE as rendered, not merely risky. Rendering the acting-record signal is
   PER-ENDPOINT (membership for contacts/routes/orders; VALUE for the acting user's balance;
   COUNT for order-items) — rater-cut assembly, gated to the IRB/M-yield-gated Step-5 seal
   (§3); recorded per-case as `rateability: tt-collection-truncation-gated` in
   `../MANIFEST-r2.json`. The original closing sentence scoping the residual away from
   sockshop was WRONG and is retracted — see the post-review corrections below (B-5):
   `sockshop-shipping-swallowed-enqueue-001` has NO rendered discriminator at all (its only
   discriminator is a trace span the harness never renders), which is a DIFFERENT, deeper
   class than truncation.

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
record, rev 3) · this `AUDIT-r2.md` · `../MANIFEST-r2.json` (pre-seal manifest + hash,
rebuilt by the committed `../r2_manifest.py`) · the hardened `b4_harness.BANNED_STRINGS` +
opaque-id guard + `test_b4_harness.py` (green). The rater SEAL itself stays OPEN
(IRB/M-yield/calibration-gated, §3).

---

# POST-REVIEW CORRECTIONS (3-cold review of RESULT-e1r2, 2026-07-14 → neutralizer rev 3)

Three independent cold reviewers (A=E1/OpenAPI ACCEPT 0-blocking; B=rater-blindness
ACCEPT-WITH-FIXES **5 BLOCKING**; C=scope/DoD/honesty ACCEPT-WITH-FIXES **1 BLOCKING**).
The blocking set is ONE family — evidence-adequacy/faithfulness defects in the neutralizer's
merge/durable assembly that the §2.3 read-through missed, plus this audit having CERTIFIED
exactly those renders (finding-4's old scope sentence). All folded; re-rendered; re-hashed.
Reconciliation: `debug/a-main/c2c3/REVIEW-E1R2-RESULT-RECONCILIATION.md` (local-only).

## Fixed in neutralizer rev 3 (all re-verified: 25/25 render 0-leak, 0 elisions)

1. **[B-1] `otlc1`/`otlf2` label-encoding emails** — the oteldemo control/lost pair's only
   differing identity field used the same c=control/f=fault single-letter convention as
   `TSMW{C,F}`; neutralized to `corpus-user@corpus.test` (label-symmetric).
2. **[B-4 + C-1a/b/c] OTel durable observations re-keyed + permanence rendered + machine-read.**
   The old render was (i) keyed on `street_address='1 Corpus Way'` — COUNTERFACTUAL under its
   own text since BOTH legs shared that address (the control's row was in
   `accounting.shipping` when the fault leg was read); (ii) a single obs at a synthetic
   t+2 s, dropping the permanence evidence, making the flagship positive epistemically
   indistinguishable from the eventual-present benigns at first observation (a rational
   blind rater answers "underspecified"); (iii) built from a hand-set `present=` literal
   while this audit said "sourced from evidence". NOW: probes are
   `SELECT count(*) FROM accounting."order" WHERE order_id='<the acked orderId>'` (the id
   is visible in the rendered ack — the domain-correct unique key), row counts are
   MACHINE-READ from the committed `readback-psql.txt` (with an ack↔evidence order_id
   equality guard), the lost case renders the FULL permanence evidence (0 in-window →
   0 post-restore → 0 later still), and SQL probes carry status "n/a" (psql has no HTTP
   status; the old "HTTP 200" was invented).
3. **[C-1a] Synthetic cross-phase cadence, now MARKED + disclosed.** Merged/injected
   observations (TeaStore post-restore/post-teardown reads; the OTel durable reads) had been
   rendered at fabricated ~+2 s offsets — the real reads happened in a SEPARATE later pass,
   minutes later; the fabricated timing WEAKENED the positives (invited
   eventual-consistency/underspecified mislabels). NOW: their probes say
   "… verification read / read in a later verification pass", intra-phase deltas are the
   REAL captured deltas, and the cross-phase offsets are ROUND PLACEHOLDERS (+120 s
   TeaStore; +30 s/+300 s/+900 s OTel) — **DISCLOSED CONVENTION: real cross-phase offsets
   are not recorded in the committed evidence; the rendered ORDER and the marked
   "later pass" wording are faithful; the placeholder magnitudes are order-of, not
   measurements.** Single-mode cases (TT, sockshop, eventual-benigns) carry their real
   captured `t_rel_ms` throughout.
4. **[B-3] bookinfo decision signal restored.** The old render collapsed the response HTML
   to "<success-shaped page rendered>", DESTROYING the case's rateable evidence (the
   documented-graceful-degradation message). NOW a COMPUTED page descriptor (substring
   checks against the REAL body): `page rendered: product and reviews content shown;
   ratings panel displays the message "Ratings service is currently unavailable"`.
5. **[B-11] ISO-T datetimes masked** in rendered bodies (`<time>`; truncation-tolerant
   pattern) — sockshop cross-pair ordering was inferable from order dates. Residual for the
   seal-time rater cut: epoch-derived usernames (`cb17836…`) still carry wall-clock-like
   digits (label-symmetric; disclosed, deferred).
6. **[B-7] `__main__` guard** — importing the neutralizer no longer rebuilds the 25 as a
   side effect. **[B-6] "never hand-set" corrected** — presence/count is computed from real
   bodies (HTML/order-items) or machine-read from committed psql evidence (OTel durable);
   the docstring/audit claim is now accurate.

## Disclosed (NOT silently rewritten) — rendered confounds + pre-seal decisions

7. **[B-2] TT admin cross-leg ACK-TEXT differential tell (KNOWN, case-file-disclosed;
   omitted from this audit's tell-audit — the omission was the miss).** Rendered acks
   differ textually across legs: adminbasic control `{"msg":"Create Success"}` vs fault
   `{"msg":"create contacts success"}`; adminroute control `{"msg":"Save and Modify
   success","data":{route}}` vs fault `{"msg":"create and modify success","data":null}`
   (the fault request also carries a client-supplied `id`). The fault-leg strings are
   INJECTOR-authored (fork flag-on branch). **Verified against the shipped rater docs
   bundle** (`rater-packet/ship/docs-bundles/trainticket/**`): the CONTROL-leg strings
   ("Create Success", "Save and Modify success") ARE in the bundled upstream source; the
   FAULT-leg strings are NOT → a source-checking rater can derive the anomalous leg from
   string absence (injector residue), shortcutting the intended domain judgment. Per the
   review: the ack text is EVIDENCE and is NOT rewritten (that would fabricate); the
   disposition is disclose-here + a pre-seal decision (keep-with-disclosure vs exclude the
   4 admin cases from blind rating), folded into the same Step-5 gate as their
   `tt-collection-truncation-gated` rateability.
8. **[B-5] sockshop swallowed-enqueue has NO rendered discriminator.** Its only
   discriminator is the AMQP-send trace span (case file: read-back "NOT black-box
   observable"); the two rendered sockshop cases are structurally identical except
   ids/timestamps. Recorded as `rateability: trace-required-not-blind-rateable` in the
   manifest; DEFAULT = exclude from the blind-rated set unless the seal decides to ship
   trace-derived evidence as a disclosed white-box render. (Finding-4's old sentence
   scoping this away is retracted above.)
9. **[B-9] w120 record interleaving is chronologically faithful** — the first SQL probe
   (t+16 ms) fired while the checkout POST (response t+425 ms) was in flight; the render
   indents the ack under the observation. Confusing but TRUE order; reordering would
   falsify chronology. Left as-is, disclosed.
10. **[B-10] SQL probes render an HTTP-shaped status line** ("HTTP n/a" after rev 3;
    previously an invented "HTTP 200"). A harness presentation tweak (omit the status word
    for non-HTTP probes) is deferred to the rater cut (kept out of this wave's strict-only
    harness scope).
11. **[B-8] MANIFEST moved out of the sidecar dir** (`rater-sidecars/MANIFEST-r2.json` →
    `../MANIFEST-r2.json`) after a live demonstration of the hazard (a naive
    `glob('rater-sidecars/*.json')` picked it up as a render input). The manifest is
    ADMIN-side (true ids + labels + the label-perfect `shape`); at rater-cut time ship
    RENDERS only, never sidecars.
12. **[C-5 + a NEW surfaced defect] Blind-id enforcement is now MECHANICAL, and the sealed
    S3 opaque id is flagged.** `render()` now fails loud when the opaque id carries label
    vocabulary (control/benign/masked/…), a banned string, or the case's true id
    (strict-only tightening + a new test). Rendering the 25 with `case-<true_id>` ids
    previously leaked label-bearing TITLES for 17/25 without tripping any gate. **Surfaced
    while folding this: the SEALED S3 calibration case was rendered with opaque id
    `S3-BENIGN-01` (SEALED-MANIFEST.sha256 line 9) — its rater-facing TITLE carries
    "BENIGN" = the label.** No rating has occurred, so no harm done yet; flagged for a
    RE-CUT with a truly opaque id (+ re-seal) at the gated Step-5 seal. The new guard
    makes this class impossible going forward.
13. **[B-12 + C-6] Tell-audit precision + the missing cadence row.** "num-observations: no
    single-label separation" tightened to: no NON-TRIVIAL bucket separates (buckets 0 and 3
    are single-label at n=1 each; the populated buckets 1–2 are mixed). NEW cadence row:
    re-probe cadence is NON-uniform across the corpus (eventual-benigns re-probe at real
    ~30/58/328 s; merged/durable cases carry marked verification-pass reads with placeholder
    offsets per correction 3) — cadence is therefore NOT usable as a label feature but IS
    a disclosed rendering heterogeneity; re-audited at the seal as planned.
14. **[B-13/C] Reviewer scratch renders** (`rater-cases-review-tmp{,2}/`, true-id-titled)
    are LOCAL/untracked; left in place per the no-deletion rule — they are NOT rater
    artifacts and must never ship.
