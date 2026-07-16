# MEMO — A3(iii-b): the TT admin cross-leg ACK-TEXT tell — keep-vs-exclude (decision = USER at the Step-5 seal)

**Confound of record (E1+R2 corrections row, disclosed-not-rewritten):** on the TT admin
sites the FAULT legs' ack strings are FORK-AUTHORED (absent from the shipped pure-upstream
docs bundle `5526e505`), while control legs carry upstream strings → a rater who
cross-references the docs bundle can derive the LEG DIRECTION from ack text alone. The
evidence was left un-rewritten on principle (rewriting captured bodies destroys evidence
faithfulness; the R2 neutralizer touches narration, never captured protocol bodies).

**Interaction with A3(ii):** the affected admin-family cases are the SAME 9
`tt-collection-truncation-gated` cases (plus any TT case whose rendered ack text is
fork-authored). If the seal takes A3(ii) branch (b) (keep the 9 excluded), the ack-text
question mostly DISSOLVES — the remaining TT rateable renders (cancel-refund trio,
dedupe/noop benigns) must be checked string-by-string against the docs bundle at the seal
(the mechanical check: rendered ack text ∈ upstream bundle?).

- **Branch (a) KEEP affected renders with the confound disclosed** (paper-side disclosure;
  raters uninformed). Risk: a docs-bundle-aware rater decodes direction — a validity
  threat the paper must carry.
- **Branch (b) EXCLUDE ack-text-telling cases from blind rating.** Cost: rating units
  (quantified per case at seal by the mechanical check). Gain: the tell class vanishes.
- **Branch (c) neutralize the ack strings — REJECTED at E1+R2** (evidence rewriting; do
  not resurrect).

**Recommendation:** run the mechanical string-vs-bundle check at the seal over ONLY the
cases actually entering the packet; exclude tellers (branch b) unless that empties a
needed stratum — then branch (a) with the validity threat stated. Sealed sets untouched
by this memo.

## MEASURED ADDENDUM (wave-close pre-check, 2026-07-16)

The mechanical survey over the ENTIRE rateable-ok set (14 renders, the current packet):
**zero non-null response payloads in the neutralized sidecars — NO rendered ack text
exists anywhere in the current packet.** Consequence: the ack-text tell is CONFINED to
the truncation-gated 9 (already excluded by their rateability class), so **for the
current packet this keep-vs-exclude decision is VACUOUS** — nothing rateable can tell.
The decision becomes LIVE only if the seal elects the TT re-capture branch
(MEMO-tt-per-endpoint-rendering branch (a)), whose new renders would carry ack text and
must then pass the string-vs-bundle check before entering the packet.
