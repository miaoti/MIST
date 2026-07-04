# SUT-2 (Sock Shop) — live-discovered blocker: HAL/HATEOAS read-back shape

**Date:** 2026-07-04 · **Phase:** G3 β SUT-2 benign FP probe (prereg C-pin 4)
**Status:** finding recorded; fix designed; implementation + ≥3-cold-review before the probe is trusted.

## What happened

The N=30 benign FP probe (`sockshop-g3-benign.properties` + `target-triples.yaml`, SS-B
addresses/cards) failed fast at `MistRunner.java:632`:

```
IllegalStateException: Data-integrity pairing requested but no generated test matched a
registered triple — the write endpoint is missing from the scenario corpus (no trace covers it).
```

Root cause of *that* error: the trace corpus (`evaluation/suts/sockshop/traces/`) only holds
catalogue GETs; nothing covers `POST /addresses` or `POST /cards`, so generation produced no
test on the write endpoints. That is fixable by capturing write traces (below).

But live-verifying the write path surfaced a **deeper, prior blocker** that the prereg missed.

## The real blocker: SS-B read-backs are HAL/HATEOAS, and `extractItems` can't parse them

Live `GET /addresses` (cookie session, after a `POST /addresses`):

```json
{"_embedded":{"address":[
  {"street":"SmokeSt","number":"111","country":"UK","city":"London","postcode":"AB1 2CD","id":"..."},
  ...seeded rows... ]}}
```

`GET /cards` is the same shape under `_embedded.card[]`. This is **Spring HATEOAS** — Sock
Shop's user service (`user`, Go front-end proxying to the Spring user svc) emits HAL collection
resources across its whole surface (addresses, cards, customers).

`DataIntegrityRuntime.extractItems` (:872-897) understands exactly two encodings:
1. a bare top-level array `[...]`;
2. the TrainTicket envelope `{...,"data":[...]}` (extracts `data` iff it is a JSONArray).

For a HAL body, `obj.opt("data")` is `null` → **`extractItems` returns EMPTY**. Therefore
`containsKey` (:844-865) iterates an empty list and reports **every benign write ABSENT** —
a ~100 % false-positive storm that is a *parsing artifact*, not a real miss. This invalidates
the "SS-B hooks with zero new code" premise recorded in the β runbook: Sock Shop's read-backs
are HAL, not bare arrays or `{data:[]}`.

Prereg cross-check: `g3-sut2-triples-prereg.md` §0 / Triple SS-B verified SS-B read-backs are
"global, seeded, monotonically-growing lists" (the accumulation shape that motivated
`readback_bound`) but **never pinned the JSON envelope**. The `_embedded` wrapper is the gap.
(Same class of live-discovered gap as the Mongo `OP_QUERY` image pin and the `?custId=` override —
recorded, not hidden.)

## Decision: teach `extractItems` the HAL `_embedded` convention (general, additive, review-gated)

Rationale (why this is the right fix, not a Sock-Shop hack):
- **HAL is a standard** (IETF `draft-kelly-json-hal`), the default Spring HATEOAS collection
  encoding — pervasive far beyond Sock Shop. Every write+collection-readback pair in Sock Shop
  (addresses, cards, customers, the paginated orders endpoint) is HAL, so this is the *general*
  unlock for the whole SUT, not a one-off.
- It **strengthens the external-validity story**: MIST's read-back extraction then handles two
  independently-designed collection shapes — the TrainTicket `{status,msg,data:[]}` envelope and
  the HAL `_embedded` convention.
- It is **provably inert on the frozen TrainTicket comparator**. `extractItems` is shared with
  `ContractEvaluator.java:288`, but the HAL branch is reached only when `data` is *not* a
  JSONArray **and** `_embedded` is a JSONObject — a shape **no** TrainTicket endpoint emits (TT
  bodies are bare arrays or `{status,msg,data}`). So every TrainTicket comparator verdict is
  byte-identical. A unit test pins this.

Design (minimal, in `extractItems`; sibling `parsesToCollection` updated for consistency):
1. bare `[...]` → array (unchanged);
2. `{...,"data":[...]}` → `data` array (unchanged, TrainTicket);
3. **new** `{...,"_embedded":{<rel>:[...], <rel2>:{...}, ...}}` → flatten: for each relation
   under `_embedded`, add every element of an array value, or a single object value as one item.

**Soundness of flatten:** membership matches on (field-name + value) of the **fresh, unique**
isolation key (random street+number / longNum). A fresh unique value cannot collide across
relations, so matching against the union of embedded arrays is exactly as sound as matching the
one correct sub-array. A read-back GET returns exactly one relation in practice, so the flatten
is singular here anyway; the union rule just keeps it deterministic and general.

`readback_bound` (the R1 accumulation guard) now counts HAL items correctly (the size check at
:618 uses `extractItems`), so the bounded-collection prerequisite is honoured on SS-B.

## Guard-rails (unchanged, still enforced)

- SS-B stays MEMBERSHIP + FRESH_STRINGS; `parsesToCollection` is only on the SUPPLIED/VALUE_DELTA
  baseline path (:413/:426), **not** on SS-B's membership path — but it is updated to recognise
  HAL for consistency and any future value-delta-on-HAL triple.
- SS-A (cart) stays deliberately absent — the BFF *renames* the key (`id`→`itemId`); HAL parsing
  does not fix a key rename. That still needs a reviewed membership alias.
- Bar v2 unchanged: NOT_EVALUABLE on a degraded gate; report the interval + gate histogram.

## Runbook / known limitations (from the 3-cold-review, commit-folded)

- **Empty-HAL asymmetry in `parsesToCollection` (review F1).** An empty HAL collection —
  `{"_embedded":{}}` or Spring's omitted-`_embedded` `{"_links":{…}}` — reads as *not a
  collection* here, asymmetric with empty `{"data":[]}` which reads as a collection. This is
  **dead for SS-B** (membership uses `beforeWrite`, which never calls `parsesToCollection`), so
  it does not affect the benign probe. It becomes live only for a *future* value-delta-on-HAL
  **SUPPLIED** triple: an empty baseline would be recorded as an error instead of a clean empty
  baseline. Left as a documented limitation (fixing it is speculative — the omitted-`_embedded`
  case is genuinely indistinguishable from a non-collection single resource). Re-decide before
  adding a value-delta-on-HAL supplied triple.
- **Freshened isolation keys are non-numeric (review A4) — VERIFIED ACCEPTED.** `number`
  (addresses) and `longNum` (cards) are FRESH_STRINGS isolation-key fields, so `freshValueLike`
  (UUID-aware only) rewrites them to `mist-<hex>` — a non-numeric string. Live-verified the
  Sock Shop user service (Go + Mongo, schemaless) accepts them: `POST /addresses` with
  `number="mist-<hex>"` and `POST /cards` with `longNum="mist-<hex>"` both return **HTTP 200**,
  and the freshened row appears in the HAL read-back. So the probe will not stall with
  not-acked records on this axis.

## Verification plan (goal-driven)

1. Implement HAL branch in `extractItems` + `parsesToCollection`. → verify: unit tests below pass.
2. Unit tests: HAL body → items extracted (membership PRESENT for a submitted key); HAL absent
   key → ABSENT; multi-relation `_embedded` → union; single embedded object → one item; **a
   representative TrainTicket `{status,msg,data:[]}` body → identical extraction (freeze-inertness)**;
   empty/absent `_embedded` → empty. → verify: `mvn -pl mist-cli test` green.
3. ≥3 independent cold reviewers (freeze-inertness on TT + flatten soundness + membership
   correctness on the live HAL shape). → verify: reconciled ACCEPT.
4. Capture `POST /addresses` + `POST /cards` write traces through a cookie session (ingress +
   traceparent markers, like `capture-traces.sh` does for catalogue); pull from Jaeger into
   `traces/`. → verify: generation produces a test on each write endpoint; pairing methods
   non-empty (no more :632).
5. Re-run the N=30 benign probe. → verify: FP interval + gate histogram; then ≥3-cold-review the
   result.
