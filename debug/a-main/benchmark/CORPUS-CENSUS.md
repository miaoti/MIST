# Corpus census — the 33 case files: benchmark membership + rater-study membership, per case

> **[2026-07-21 CORRECTION — read before the tables.]** depdown (#28,
> `teastore-order-depdown-specified-001`) was **RETIRED** to `cases/excluded-out-of-mask/` after the
> REAL MIST oracle returned **NO_FIRE 0/4** on it 2026-07-21 (db-scale-0 → the confirm journey 500s
> = a LOUD-500 loss, NOT masked-2xx; the 2026-07-20 curl "capture" read the *unfollowed* 302 and was
> refuted; 3-cold-reviewed, user-directed). **Benchmark-of-record = 26 (11 pos / 15 neg)**; the
> "33 files / all-27-captured / 12 captured positives" figures below are 2026-07-20-era and
> superseded. The **18-case rater-study set is UNAFFECTED** (depdown was benchmark-only, never in it).
> MIST read-back stays **9/9 evaluable + 0 FP**. See `b4/RESULT-depdown-live.md`.

> **ADMIN-ONLY. NEVER ship next to rater renders.** This file carries case labels AND the
> `CASE-Qxx ↔ real case_id` crosswalk (the blind-id map). It is an internal audit, kept under
> `benchmark/`, not under `rater-packet/ship/`.

**Created 2026-07-20.** `benchmark/cases/` holds **33** JSON files. That is *not* one flat set — it is a
funnel with two membership questions per case:

- **(A) Is it in the 27-case core benchmark?** (the validator-green corpus every oracle is scored on)
- **(B) Is it in the 18-case blind rater study?** (the neutral renders a human rater actually judges)

This file answers both, for every one of the 33, and gives the reason for every "no". It is the companion
to `PROVENANCE-LEDGER.md` (which answers "why is each a *real* bug"); this one answers "is each *in* the
benchmark / *in* the rater set, and why / why not".

---

## 1. The funnel at a glance

```
33 case files  (benchmark/cases/*.json)
   │
   ├─ drop 6  F-corpus (-f<N>- files)            ─────────►  NOT in the benchmark
   │
   └─ 27  core benchmark  (validate_cases.py → 27/27 green)
        │
        ├─ drop 2  (both post-date the 07-14 sidecar build)  ─►  in benchmark, NOT rated
        │
        └─ 25  neutralized rater sidecars (b4/rater-sidecars/, 0-leak)
             │
             ├─ drop 7  (present/absent signal not blind-legible) ─► in benchmark, NOT rated
             │
             └─ 18  shipped to raters  (ship/cases/CASE-Q01…Q18)
```

**Partition (adds to 33):** **18** in both · **9** in the benchmark only · **6** in neither.

---

## 2. The concepts every "why not" reason uses

- **LOST vs CORRUPTED (this is the MIST scope line).** MIST's read-back fires on **absence / no-movement**
  ("you acked success but nothing is there / the accumulator never moved off its own baseline") — which
  needs **no answer key**: "nothing changed" is self-evident. MIST **cannot** fire on **present-but-wrong**
  ("the record is there, but a field holds a wrong value") — telling wrong from right needs a **per-field
  answer key** (`stored == submitted?`), i.e. a **claim-vs-value** comparison, which is **outside** MIST's
  black-box membership+delta design. So `acknowledged_corrupted_write` cases carry
  `mist_readback_oracle: not_applicable`.
- **captured vs specified.** `captured` = the leg was actually run and the present/absent evidence recorded
  (`captures/**`). `specified` = designed, label known by construction, but **never captured** (its oracle
  cells are DESIGN TARGETs, never tallied as a result). **As of 2026-07-20 no core case is `specified` —
  all 27 are `captured`** (the last one, #28, was captured live on a PVC-backed db; see §4b).
- **Blind-legibility rule (the rater cut).** A case ships to raters **iff its present/absent signal is
  directly legible in the neutral render** — no tool, no trace span, no async re-probe calibration.
- **Rateability tags** (recorded per case in `b4/MANIFEST-r2.json`): `ok` · `tt-collection-truncation-gated`
  (the acting record falls past the 600-char render truncation) · `async-no-bound-calibration-ineligible` ·
  `trace-required-not-blind-rateable`.

---

## 3. The full 33-case census

**Legend** — *kind*: `LOST+` acked-but-lost positive (MIST in scope) · `CORR+` corrupted/present-but-wrong
positive (MIST **out** of scope) · `CTRL−` clean control negative · `TRAP−` designed benign trap negative.
*cap*: `capt`/`spec`. *in-27*: in the core benchmark. *rater*: shipped opaque id or `—`.

| # | case_id | SUT | kind | cap | in-27 | rater | status / why-not |
|---|---|---|---|---|---|---|---|
| 1 | TT-adminbasic-contacts-control-001 | TT | CTRL− | capt | ✓ | — | rater-drop: truncation-gated **control** (redundant clean twin of Q13) |
| 2 | TT-adminbasic-contacts-lostwrite-001 | TT | LOST+ | capt | ✓ | **Q13** | in both (truncation-gated → re-cut to a canonical durable-state check) |
| 3 | TT-adminroute-control-001 | TT | CTRL− | capt | ✓ | — | rater-drop: truncation-gated **control** (twin of Q08) |
| 4 | TT-adminroute-lostwrite-001 | TT | LOST+ | capt | ✓ | **Q08** | in both (re-cut) |
| 5 | TT-basic-price-corrupt-f14-001 | TT | CORR+ | capt | ✗ | — | **F-corpus** — CORRUPTED (search shows 22.5, order persists 50.0); MIST out of scope |
| 6 | TT-cancel-refund-asyncrefund-f1-001 | TT | LOST+ | capt | ✗ | — | **F-corpus** — a *distinct async* refund-loss mechanism; MIST-eligible, **candidate to fold in** |
| 7 | TT-cancel-refund-clean-001 | TT | CTRL− | capt | ✓ | — | rater-drop: truncation-gated **control** (one of 3 cancel variants; kept Q17) |
| 8 | TT-cancel-refund-fabricatedack-001 | TT | LOST+ | capt | ✓ | **Q17** | in both (re-cut); the clean `{1,"Success."}` flagship representative |
| 9 | TT-cancel-refund-natural-001 | TT | LOST+ | capt | ✓ | — | rater-drop: kept fabricatedack as the representative; natural carries the disclosed ack-text `"error"` tell |
| 10 | TT-cancel-status-recheck-corrupt-f11-001 | TT | CORR+ | capt | ✗ | — | **F-corpus** — CORRUPTED, intermittent (status persists 4 then 3); MIST out of scope |
| 11 | TT-contacts-dedupe-benign-001 | TT | TRAP− | capt | ✓ | **Q15** | in both (designed duplicate-rejection, `status:0`) |
| 12 | TT-contacts-noop-modify-benign-001 | TT | TRAP− | capt | ✓ | **Q02** | in both (designed idempotent no-op) |
| 13 | TT-createaccount-agreement-001 | TT | LOST+ | capt | ✓ | **Q14** | in both (re-cut) |
| 14 | TT-createaccount-clean-001 | TT | CTRL− | capt | ✓ | — | rater-drop: truncation-gated **control** (twin of Q14) |
| 15 | TT-order-contact-corrupt-f10-001 | TT | CORR+ | capt | ✗ | — | **F-corpus** — CORRUPTED via a poisoned downstream read (order stores contact …431 for …430); MIST out of scope |
| 16 | TT-order-statusskew-corrupt-f20-001 | TT | CORR+ | capt | ✗ | — | **F-corpus** — CORRUPTED (set 1/PAID, persists 2/COLLECTED — a valid-but-wrong code); MIST out of scope |
| 17 | TT-user-selection-corrupt-f8-001 | TT | CORR+ | capt | ✗ | — | **F-corpus** — CORRUPTED (register persists documentType 0/NONE for submitted 1/ID_CARD); MIST out of scope |
| 18 | bookinfo-ratings-benign-001 | bookinfo | TRAP− | capt | ✓ | **Q18** | in both (graceful degradation: productpage 200 with "Ratings unavailable") |
| 19 | oteldemo-checkout-control-001 | OTel | CTRL− | capt | ✓ | **Q04** | in both |
| 20 | oteldemo-checkout-eventual-benign-001 | OTel | TRAP− | capt | ✓ | **Q01** | in both (bounded eventual consistency) |
| 21 | oteldemo-checkout-eventual-benign-002 | OTel | TRAP− | capt | ✓ | **Q09** | in both (induced bounded eventual consistency) |
| 22 | oteldemo-checkout-eventual-benign-003 | OTel | TRAP− | capt | ✓ | **Q07** | in both (induced bounded eventual consistency) |
| 23 | oteldemo-checkout-kafkaqueue-lost-001 | OTel | LOST+ | capt | ✓ | — | rater-drop: the **27th** case, added 07-16 **after** the 07-14 sidecar build; folds in at re-seal |
| 24 | oteldemo-checkout-lost-001 | OTel | LOST+ | capt | ✓ | — | rater-drop: `async-no-bound-calibration-ineligible` — the loss is only confirmable via a bounded re-probe a blind rater can't run |
| 25 | sockshop-shipping-control-001 | SockShop | CTRL− | capt | ✓ | **Q10** | in both |
| 26 | sockshop-shipping-swallowed-enqueue-001 | SockShop | LOST+ | capt | ✓ | — | rater-drop: `trace-required-not-blind-rateable` — the only discriminator is a trace span the render never shows |
| 27 | teastore-order-control-001 | TeaStore | CTRL− | capt | ✓ | **Q16** | in both |
| 28 | teastore-order-depdown-specified-001 | TeaStore | LOST+ | **capt** | ✓ | — | **CAPTURED 2026-07-20** (PVC-backed `teastore-db`; control present / fault absent **N=6/6** under the buffer-drop protocol). Now post-dates the 07-14 sidecar build → folds in at re-seal (like #23). *Case id still contains "specified"; `mist_readback_oracle` stays `not_applicable` pending a MIST run.* |
| 29 | teastore-order-maintenance-masked-001 | TeaStore | LOST+ | capt | ✓ | **Q11** | in both (vendor maintenance flag → 201 / body `-1`) |
| 30 | teastore-order-meshsever-control-001 | TeaStore | CTRL− | capt | ✓ | **Q12** | in both |
| 31 | teastore-order-meshsever-masked-001 | TeaStore | LOST+ | capt | ✓ | **Q03** | in both (mesh-injected 503 on the persistence orders write) |
| 32 | teastore-orderitems-meshsever-control-001 | TeaStore | CTRL− | capt | ✓ | **Q05** | in both |
| 33 | teastore-orderitems-meshsever-masked-001 | TeaStore | LOST+ | capt | ✓ | **Q06** | in both (PARTIAL write: parent order lands, child order-items lost) |

**Tallies.** In-27 = 27 (all but the 6 F-corpus). In-rater = 18. Not-rated-but-in-27 = 9
(#1, #3, #7, #9, #14, #23, #24, #26, #28). In-neither = 6 (the F-corpus).

---

## 4. Why each excluded case is excluded — case by case

### 4a. The 6 F-corpus — *not in the 27 benchmark* (#5, #6, #10, #15, #16, #17)

The `-f<N>-` files are a captured **fault-family extension**, kept as files but held out of the headline
27. Five are **CORRUPTED (present-but-wrong)**; one is a distinct async **LOST**.

**Why the 5 corrupted ones are out of the benchmark's MIST-scored corpus.** MIST detects **LOST** (absent /
no-movement), not **CORRUPTED** (present-but-wrong). The record IS there, so MIST's membership read returns
"present" and — correctly, by design — does **not** fire. To flag the wrong *value* you must compare the
stored value to the value the client submitted (**claim-vs-value**), which a black-box tool cannot do
without a per-field answer key (and could not tell corruption from a legitimate server transform anyway).
So each carries `mist_readback_oracle: not_applicable`; they are the honest **scope-boundary** set — "the
benchmark is broader than the tool."

Worked examples (the value that is wrong, and why membership can't see it):

- **#17 `f8` user register — documentType 0 vs 1.** From the real TrainTicket source
  (`DocumentType.java`): `NONE(0,"Null")`, `ID_CARD(1,"ID Card")`. The user submits `1` (ID card); under
  fault register acks 2xx, the **user record is created**, but `documentType` is stored as `0` (the default
  NONE). MIST keys membership on the **user** — the user appeared → success. Both legs read back "user
  present" (control `documentType=1`, fault `documentType=0`); membership gives the **same** verdict for
  both, so it is blind to the 1-vs-0 difference. `0` is not *missing*; it is a valid enum value that is
  *wrong* — only knowable against the submitted `1`.
- **#15 `f10` booking — order stores contact …431 for submitted …430.** One write (the **booking**,
  `POST /preserve`). The contact is a **pre-existing** record only *read* during booking; the fault
  corrupts that downstream read's **returned value** (a detached copy, `430→431`) — the stored contact row
  stays `430`. The order persists the wrong `431`. Nothing is absent (the order lands, keyed on its
  identity); the corruption is a wrong field inside a present record. `mist_bindable: false`.
- **#16 `f20` order status — set 1 (PAID), persists 2 (COLLECTED).** Every skewed status code is a *valid*
  status, so no oracle sees a loud failure; the order is present with a legal-but-wrong status.
- **#5 `f14` pricing** (search shows 22.5, order persists 50.0) and **#10 `f11` status-recheck**
  (intermittent 4→3) are the same class.

**The contrast that fixes the line (same balance field, two bugs):** in the cancel→refund flagship the
read-back is `count-delta-positive` (no-fault) vs `count-delta-**zero**` (fault) — MIST fires on **zero
movement** (`50→50`), needing no answer key. But a refund that lands the *wrong amount* (`50→80` when it
should be `50→130`) MOVES, so MIST does **not** fire — that is CORRUPTED, and catching it would again need
the answer key. The line is not "is it a numeric comparison"; it is **"does judging it require an external
answer key?"** — LOST does not, CORRUPTED does.

**Why #6 `f1` (async refund loss) is different.** It is a genuine `acknowledged_lost_write` that MIST
**does** flag (`mist_readback_oracle: flag`) — a distinct async-refund mechanism on the cancel-refund site.
It sits in the F-corpus only because it was built in that extension wave; it is a legitimate **candidate to
fold into the core corpus** (see §6).

### 4b. In the 27 benchmark, *excluded from the 18 rater set* (9 cases)

These are real corpus cases; they are held out of the **blind** rater study because the present/absent
signal a rater would need is not legible in a neutral render.

- **#28 `teastore-order-depdown-specified`** — **CAPTURED 2026-07-20** (this resolves the former
  `specified` status). It was live-**attempted** 2026-07-10 but was unsound then: the as-deployed
  `teastore-db` had **no PVC**, so a DB scale-cycle wiped+regenerated the store (product 42 → 404),
  destroying the absence evidence. The fix was a **deploy change, not a SUT change** — patch `teastore-db`
  with a local-path PVC (2Gi at `/var/lib/mysql`). The capture is then sound: control present /
  **fault absent N=6/6** on `GET /rest/orders` (`b4/cset/teastore-depdown/depdown-legs.log`), the same
  durable surface as the maintenance/mesh producers. Honest nuance disclosed in the log: the loss is
  deterministic only under a **buffer-drop protocol** (force-delete the persistence pod while the DB is
  down, before restoring it) — a naive scale-cycle shows ~50% persistence because the JPA pool buffers the
  failed write and flushes it on reconnect (that flush path is a separate downstream-eventual behavior, out
  of the masked-loss claim). It is still **not** in the rater set only because it now post-dates the 07-14
  sidecar build (folds in at re-seal, like #23); `mist_readback_oracle` stays `not_applicable` until a MIST
  head-to-head run scores it.
- **#23 `oteldemo-checkout-kafkaqueue-lost`** — the **27th** case, captured 2026-07-16, **after** the
  07-14 neutralized-sidecar build. Purely a timing gap; it folds in at the Step-5 re-seal.
- **#24 `oteldemo-checkout-lost`** — `async-no-bound-calibration-ineligible`: the async loss is only
  confirmable by a **bounded re-probe** (did the order still not land after T+cap?). A blind rater reading a
  static render can't run that calibration, so the case is not blind-rateable.
- **#26 `sockshop-shipping-swallowed-enqueue`** — `trace-required-not-blind-rateable`: its **only**
  discriminating signal is a **trace span** (the swallowed enqueue) that the harness never renders. With no
  rendered present/absent signal, a blind rater has nothing to judge.
- **#1 / #3 / #7 / #14 (four clean TT controls) and #9 (cancel-refund-natural)** —
  `tt-collection-truncation-gated`: the TrainTicket global-collection read-backs are 5.7–36 KB with the
  acting row appended last, so it always falls **past** the 600-char render truncation. For the four **clean
  controls** the re-cut adds no value (they are redundant twins of shipped positives, and shipping both legs
  of a tight A/B pair invites pattern-matching), so they are dropped. **#9 natural** is dropped because
  cancel-refund has three variants and one representative positive is shipped (**#8 fabricatedack**, the
  fully-clean `{1,"Success."}`); natural additionally carries the disclosed ack-text `"error"` tell. The
  four **positive** truncation-gated cases (#2, #4, #8, #13) were instead **re-cut** to a canonical
  "durable-state check → no matching record" render and **do** ship (Q13/Q08/Q17/Q14).

---

## 5. The 18 in the rater set (7 positives + 11 negatives)

**Positives (7)** — all TeaStore or TrainTicket, i.e. exactly the sites whose loss is directly visible in a
rendered durable read-back: Q03 order-meshsever, Q06 orderitems-meshsever, Q11 order-maintenance (TeaStore);
Q08 adminroute, Q13 adminbasic-contacts, Q14 createaccount, Q17 cancel-refund-fabricatedack (TrainTicket).
*(OTel's two positives and SockShop's one are out — async-calibration / trace-only.)*

**Negatives (11)** — 5 clean controls (Q04, Q05, Q10, Q12, Q16) + 6 designed benign traps (Q01/Q07/Q09
eventual-consistency, Q02 no-op-modify, Q15 dedupe-reject, Q18 ratings graceful-degrade).

A rater sees only a neutral event trace ("what was done / what was observed") + a ballot
(`genuine | benign | underspecified` + a grounding citation). The opaque ids (CASE-Qxx) are re-keyed so even
the filename cannot leak the label.

---

## 6. Pending decisions (user-side; noted, not resolved here)

1. **#28 depdown — RESOLVED 2026-07-20 via option (a):** captured live on a **PVC-backed** `teastore-db`
   (`depdown-legs.log`, fault absent N=6/6). The corpus is now **12 captured positives, 0 specified** — the
   old "11 captured + 1 specified" headline is **superseded by "12 captured"**. Remaining follow-ups: score
   its `mist_readback_oracle` with a MIST head-to-head run (it uses the same `GET /rest/orders` surface as
   the maintenance/mesh producers, which ARE mist-bindable), and fold it into the rater set at re-seal.
   *(Synced 2026-07-20: `OVERVIEW.md` §6.4 and `PROVENANCE-LEDGER.md` F3/F11 + ledger row updated to reflect the capture.)*
2. **#6 f1 async refund loss** — fold into the core corpus (a legitimate MIST-in-scope LOST positive) or keep
   it in the F-corpus extension.
3. **Step-5 re-seal** — fold in #23 (kafka 27th), bless the four re-cut TT positives (Q08/Q13/Q14/Q17), and
   the `CASE-Q47` swap for the label-leaking S3 opaque id.

---

*Sources: `benchmark/cases/*.json` (all 33); `schema/validate_cases.py` + `schema/fault-case.schema.json`
(the 27/27 structural gate); `b4/MANIFEST-r2.json` (25-sidecar rateability); `rater-packet/admin/
opaque-id-map.json` (the 18 crosswalk); `b4/rater-sidecars/AUDIT-r2.md` (neutralization + truncation);
`docs-bundles/trainticket/.../DocumentType.java` (the 0/1 enum). Companion: `PROVENANCE-LEDGER.md`.*
