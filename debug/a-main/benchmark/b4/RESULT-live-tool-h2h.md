# RESULT — live real-tool head-to-head (Track E executed; Track T deferred) — RESULT OF RECORD

**Date:** 2026-07-21 · Plan: `b4/PLAN-live-tool-h2h.md` rev 2 (round-1 3-cold ACCEPT-WITH-CHANGES
folded; confirm round A'/B'/C' ALL CONFIRM) · Status: EXECUTED (Track E), the pre-registered
control-gate branch fired · DoD: 3-cold result review of this file.

> **HONESTY RIDER (pinned per C'-confirm):** this wave is a SOFT-SPOT-CLOSER — it blunts "no
> real competing tool ran live end-to-end". It is NOT a ceiling-raiser; the levers that raise the
> venue ceiling remain the rater study and the operating-point reframe. Nothing here is a
> unique-detection claim; the headline discipline (operating point + 0-FP, per the paper-plan §1
> 2026-07-21 amendment block) binds every sentence derived from this result.

## Track E — EvoMaster v6.1.1, auth-provisioned + seeded cart, on TeaStore

**Label of record:** *black-box + operator-provisioned auth + seeded cart* — never plain
"black-box". (This reverses the 2026-07-17 no-auth-special-casing rail under explicit user
approval 2026-07-21; the PWS L1 spec-only cells stand untouched as the tool-class datum.)

**Outcome: `NOT_INTERPRETABLE-well-configured` — the PRE-REGISTERED control-gate branch.**
The gate (control-leg `/rest/orders` durable delta ≥ 1) measured **delta = 0** (191 → 191) after
the FULL 60-minute budget (3601 s, **3,042,883 evaluated actions / 1,592,718 evaluated tests**,
covered targets 139, acked-2xx on 2/9 endpoints). Per the pre-registered stop rule the fault leg
was **NOT run** (no retry-shopping; a fault-leg delta-0 is uninterpretable without a control
baseline).

**The tool's own fault report is noise-class:** 17 "potential faults" = hiddenAccessible 404
probes (23 assertions), HTML schema-mismatches (10), two 5xx, one 302 — none order-flow-shaped;
zero durable writes.

**Diagnosis of record (three-step, each live-verified):**
1. **Session alive after the full run** — the prep cookie still returns a logged-in profile
   (HTTP 200) AFTER the 60 minutes: not cookie expiry, not a tool self-logout.
2. **Pipeline proven live** — a manual full-form confirm through the SAME cookie immediately
   lands a durable order (191 → 192): auth, session, cart seeding, and persistence all worked.
3. **Root cause** — in 3.04 M actions the tool never COMPOSED the semantically-valid confirm
   action: it mutates `cartAction`'s action-discriminator parameters as data fields
   (`addToCart="D_3IpK"` garbage values mixed with form fields), never emitting the
   confirm-shaped submission the business flow requires.

**The finding (strengthens, does not contradict, PWS L1):** the reachability barrier SURVIVES
auth and state provisioning — it is **action-semantics composition**, a tool-class property one
level deeper than L1's spec-only barrier. The honest paper sentence this buys: *"even with
operator-provisioned authentication and seeded state — with the same session verified to place
orders end-to-end by a manual probe — a 60-minute, 3-million-action black-box run never composes
the multi-field business action; its 17 reported faults are 404/schema/5xx noise."* The
EvoMaster MISS cell stays **vacuous-and-disclosed** (no acked baseline from the tool's own
traffic); MIST's corpus cell sits alongside as context, never pooled (the separate-table rail).

**Cell:** `b4/pws/evomaster/teastore-auth-cell.json` · artifacts in
`b4/pws/evomaster/teastore-auth-control/` (run.log, prep, leg-summary, generated tests,
statistics.csv). Maintenance was `false` before/after (verified); the fault toggle was never
touched; TeaStore restored to 0 replicas.

## Track T — Tracetest live: DEFERRED (principled, per rev 2)

Not run. All three round-1 reviewers independently verified the rev-1 premise false (the
authored specs are TT-only; no bookinfo/sockshop specs exist) and C4 found the track largely
redundant now that E-ANOM ships as the first-class in-headline trace competitor. **No live
Tracetest cell is claimed anywhere; the surrogate labels stay.** The named-third-party-live cell
is carried by EvoMaster (above) + the already-live Schemathesis (PWS L1). USER-ELECTABLE option
preserved: a dedicated TT window on the existing TT specs (B5 DataStore wiring + B6
async-visibility as binding preconditions).

## Ops incidents (disclosed)
1. Docker Desktop + both WSL distros died mid-bring-up (broken `/mnt/wsl/docker-desktop`
   cli-tools mount → kubectl a dangling symlink). Recovered ~3 min (engine relaunch; kind
   auto-restarted; TeaStore state was clean-0 pre-crash). WSL restarts wipe `/tmp` — the runner
   scripts must be re-copied.
2. The background leg wrapper was externally killed mid-run (the documented detached-client
   class); the java child SURVIVED and completed its full budget; post-measurements were
   collected by `runners/livetool/collect_leg.sh` (budget integrity verified from run.log
   timestamps: 16:33:46 → 17:33:49). A first process-exit monitor false-fired (a PowerShell
   exit-code probe bug) and was re-armed with a validated `tasklist` probe.
