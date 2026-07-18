# RESULT — A3 F-corpus build-out (the SIZE axis) — RESULT OF RECORD (rev 2, COMPLETE)

**Date:** 2026-07-18 (supersedes rev 1's "5 owed" interim state) · Status: **A3 COMPLETE — every
implemented fault carried to a B-m6 verdict; nothing owed.** Under the A-VENUE STRENGTHEN wave
(empirical/benchmark main-track goal); ADDITIVE, never headline.

## Pipeline (proven end-to-end, now exercised over all 7 faults)
isolated clean-room implementer (7 faults on fork `fcorpus-build`, flagship byte-untouched,
conduct diff-reviewed PASS) → JDK-8 in-container image build → kind-load + set-image (5 services
`:fcorpus`) → revive + doubleWrite → admin-bearer toggles → class-aware per-fault B-m6 gate →
case authoring ONLY for gate-passers.

## Final tally: 6 faults IN (live-verified), 1 GATE-REJECTED
| F | class | site | B-m6 divergence (control vs fault, both acked 2xx) | corpus case | MIST |
|---|---|---|---|---|---|
| F1 | **LOST** | cancel-refund (occupied, mechanism-variant) | refund 50→130 lands vs 50→50 LOST | `TT-cancel-refund-asyncrefund-f1-001` | **flag** |
| F8 | corrupted | ts-user (**NEW SITE #1**) | documentType 1 vs 0 | `TT-user-selection-corrupt-f8-001` | n_a |
| F10 | corrupted | order contact fields (occupied) | order doc `...400`==submitted vs `...431` rotated (contact row untouched both legs) | `TT-order-contact-corrupt-f10-001` | n_a |
| F11 | corrupted **intermittent** | cancel status (occupied) | 2 cancels: 4,4 vs 4,**3(CHANGE)** — the corpus's only intermittent case | `TT-cancel-status-recheck-corrupt-f11-001` | n_a |
| F14 | corrupted | ts-basic pricing (**NEW SITE #2**) | **two-surface tell:** search 22.5 & order 22.5 vs search 22.5 & order **50.0** | `TT-basic-price-corrupt-f14-001` | n_a |
| F20 | corrupted | order status (occupied) | set 1 → persists 1 vs persists **2** ((s+1) mod 7) | `TT-order-statusskew-corrupt-f20-001` | n_a |
| F13 | corrupted | cancel refund (occupied) | **NONE — extensionally equivalent to vanilla** (2 attempts) | **DROPPED at the gate** | — |

⇒ Corpus 27 → **33, validator-green**. MIST column: **flag 10 / no_flag 13 / principled-n_a 10**
(census adjudications per case). Integration chain regenerated (census / visibility / map /
scoring / release-staging 46 members). Distinct positive sites 8 → **10** (F8 ts-user, F14
ts-basic).

## The F13 gate rejection (construct-validity evidence, `fcorpus-f13/B-m6-RESULT.md`)
Both guards F13 bypasses are ALREADY DEAD in vanilla: (i) `cancelFromOrder` mutates the shared
order object's status to CANCEL before `calculateRefund` reads it → the NOTPAID guard never
fires; (ii) the deprecated `new Date(year,...)` constructor's 1900 offset puts every departure
in year ~3926 → the expired-ticket guard never fires. Hence `calculateRefund` ≡ 0.8×price ≡ the
fault function on every reachable input: no toggleable divergence exists. **The B-m6 gate
rejecting a vacuous injected fault is the benchmark's construct-validity mechanism working as
designed** — and the two dead guards are genuine vanilla TT defects (80% refunds granted for
never-paid and already-departed cancellations), recorded as ecosystem context for
`TT-cancel-refund-natural-001`.

## The paper value delivered
- **Size axis:** 26 → 33 cases (+27%), positive sites 8 → 10, one intermittent-corrupted
  signature (F11) and one two-surface search-vs-persisted tell (F14) new to the corpus.
- **Upstream grounding:** every F-case cites its FudanSELab survey row; clean-room two-actor
  protocol (implementer never saw upstream fault code).
- **Benchmark broader than the tool:** 5 corrupted cases where MIST is honestly n_a
  out-of-scope — the anti-self-serving datum, now systematic.
- **Gate rigor:** 7 implemented / 6 admitted / 1 rejected-with-root-cause = the corpus admits
  only live-demonstrated divergences.

## End state
TT scaled to 0 at window close. Fork `fcorpus-build` holds all 7 implementations (incl. F13,
kept for the record; MIST-trainticket untouched). Drivers committed
(`runners/fcorpus/{f1,cancelfam,booking}-driver.sh` + `revive-phase-c.sh`). Sealed sets untouched.
