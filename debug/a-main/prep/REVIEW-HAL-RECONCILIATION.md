# HAL `_embedded` extraction — 3-cold-review reconciliation

Change under review: commit `0a16255` (main_track) — `extractItems`/`parsesToCollection`
learn the HAL/HATEOAS `{_embedded:{<rel>:[..]}}` collection shape. Finding + design:
`g3-sut2-hal-readback-finding.md`.

Three independent cold reviewers, no shared context, each read the actual source
(extractItems, addAll, parsesToCollection, containsKey, probeVerdict, extractProbeValue,
both beforeWrite hooks, the afterWrite decisive-read loop, the frozen-comparator call site
`ContractEvaluator.java:288`), ran the suite (34/34 green), and verified the 3 load-bearing
claims.

## Verdicts (all three in — consensus: code correct, no blocking)
- **Reviewer A: ACCEPT-WITH-FIXES** — all 3 claims hold; no correctness defect; test-coverage gaps + one live-dependency NIT (#4, verified moot below).
- **Reviewer B: ACCEPT-WITH-FIXES** — all 3 claims hold; fixes are non-blocking (tests + 1 doc note).
- **Reviewer C: ACCEPT** — all 3 claims hold; no BLOCKING/MAJOR; MINOR/NIT only.

All three independently confirmed via repo-wide `_embedded` grep that no TrainTicket
artifact/fixture/contract/trace emits `_embedded` (freeze-inertness corroborated empirically,
not just structurally).

## Load-bearing claims — all three reviewers that reported CONFIRM
1. **FREEZE-INERTNESS (frozen TrainTicket comparator byte-identical):** HOLDS. The `data
   instanceof JSONArray` branch returns before the `_embedded` check, so any body with a
   `data:[..]` array (every TT collection body, and the only shape the comparator feeds
   extractItems) is byte-identical. The HAL branch is reached only for an object with no
   `data` array AND a top-level `_embedded` JSONObject — a shape TrainTicket (no Spring
   HATEOAS) does not emit. Reviewer C corroborated with a repo-wide grep for `_embedded`
   (matches only the new source/test/doc/index — no TT fixture/contract/trace).
2. **FLATTEN SOUNDNESS (no false membership match):** HOLDS. `containsKey` requires all key
   fields present-and-equal on ONE item; FRESH_STRINGS keys are `"mist-"+random`, globally
   unique, written only to the correct relation, so no wrong-relation row can satisfy the key
   even if a read-back carried multiple relations (in practice one). Reviewer C's structural
   strengthening: the flatten is **add-only** (monotonic) → can only move ABSENT→PRESENT,
   never PRESENT→ABSENT → it **structurally cannot manufacture a false FIRE** (the only
   dangerous direction for the benign probe).
3. **LIVE-SHAPE CORRECTNESS:** HOLDS. For the exact `GET /addresses` HAL body,
   `containsKey({street:SmokeSt, number:111})` returns true (walked by both reviewers; pinned
   by the new test; green).

## Findings dispositions (B + C converged; NONE blocking)

| # | Sev | Finding | Disposition |
|---|-----|---------|-------------|
| F1 | MINOR (B1/C7) | `parsesToCollection` empty-HAL asymmetry: `{"_embedded":{}}` and the omitted-`_embedded` empty representation (`{"_links":{…}}`) → false, unlike empty `{data:[]}` → true. Latent only: DEAD for SS-B (membership never calls parsesToCollection); a *future* value-delta-on-HAL SUPPLIED triple with an empty baseline would record an error instead of a clean empty baseline. | **DOC, not code.** Both reviewers endorsed "make it a conscious decision / one-line runbook note." Fixing it is speculative (no value-delta-HAL triple exists; the omitted-`_embedded` case is genuinely indistinguishable from a non-collection single resource). Record as a known limitation in the finding doc's runbook + a code comment. |
| F2 | MINOR (B2/C8a) | The single most load-bearing freeze property — "a `data` array wins over `_embedded`" (data-first ordering) — is argued in the comment but not pinned by a test. | **FIX (test).** Add `extractItems({data:[{a:1}],_embedded:{x:[{b:2}]}})` → returns the one `data` item. |
| F3 | MINOR (B3/C8) | Multi-relation test asserts only `size()==2`; does not directly pin cross-relation non-collision (core of claim 2). | **FIX (test).** Union body; a key present on the address row matches it, a key on the card row matches it, and a key whose fields are split across the two rows does NOT match (containsKey requires all-fields-on-one-row, no stitching across the union). |
| F4 | NIT (C8b/B5) | Robustness cases unpinned: a relation value that is neither array nor object (`{"_embedded":{"address":"oops"}}` / `null`) must yield 0 items and not crash. | **FIX (test).** Add both; they exercise the `instanceof` guards. |
| F5 | NIT (B4/C6/A5) | `keySet()` iteration order is nondeterministic (org.json HashMap-backed); the union count at the readback bound sums all relations. | **NO CODE CHANGE.** Verdict-irrelevant: containsKey/size/first-match-under-unique-keys are order-independent; the union count only ever degrades toward NOT_EVALUABLE (never a false loss/FIRE). `keySet()` is a proven-available API here (`TargetTripleRegistry.java:218`, `AssertionBindings.java:125`). |
| F6 | NIT (B5/A3) | Tests use a simplified address row rather than the verbatim live body (with `country/city/postcode` + per-item `_links` + top-level `_links` sibling). | **FIX (test).** Row now carries `_links` + full fields + a top-level `_links` sibling to prove they're all ignored by the top-level-field match. |
| A4 | NIT (A only) | `number` (and card `longNum`) is a FRESH_STRINGS isolation-key field → `freshValueLike` freshens it to a non-numeric `mist-<hex>` (`freshValueLike` is UUID-aware only, not numeric-aware); if the user service validated `number` numerically the benign `POST /addresses` would 400 and the probe would stall. | **VERIFIED MOOT (empirical).** Live curl with `number="mist-<hex>"` and `longNum="mist-<hex>"`: both `POST /addresses` and `POST /cards` returned **HTTP 200**, and the freshened address row appeared in the HAL read-back (present via the new `_embedded` path). The Go/Mongo user service is schemaless — no numeric validation. Orthogonal to HAL parsing; recorded so the probe's not-acked risk on this axis is closed. |

## Fix wave — DONE (batched, all three reviewers folded)
- Code: **no correctness change** — all three reviewers agree the change is correct and
  shippable for the SS-B benign probe as-is (add-only flatten, freeze-inert). Added the F1
  known-limitation comment on `parsesToCollection`.
- Tests (F2/A1, F3, F4/A2, F6/A3): +data-first ordering pin (the freeze property), +cross-relation
  non-collision (address/card keys + a split key that must NOT match), +non-array/null relation
  robustness, +verbatim-shaped row with item-level and top-level `_links`. Suite **34/34 green**.
- Doc: F1 known-limitation note added to `g3-sut2-hal-readback-finding.md` runbook; A4 numeric-
  validation NIT verified moot by live curl and recorded there too.
- Verdict: **ACCEPTED** (2× ACCEPT-WITH-FIXES all folded, 1× ACCEPT). The HAL extraction is
  reviewer-cleared for the SS-B benign FP probe.
