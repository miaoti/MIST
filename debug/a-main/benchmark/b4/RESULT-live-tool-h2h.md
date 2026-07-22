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
the FULL 60-minute budget (statistics.csv: elapsedSeconds 3601, **3,042,883 evaluated actions /
1,592,718 evaluated tests**, covered targets 139; **seed 42**; jar sha256
`7aa06eb6211a4a890805047a964ff0cea388a4c33c43a6413e165f5c28f4772a`; acked-2xx on 2/9 endpoints —
vs the spec-only L1 run's 1/9: the operator-provisioned session opened exactly one additional
2xx surface). Per the pre-registered stop rule the fault leg was **NOT run** (no retry-shopping;
a fault-leg delta-0 is uninterpretable without a control baseline).

**The tool's own fault report is noise-class:** 17 distinct potential faults, by the tool's own
decomposition (run.log): **schema-oracle mismatches (Fault101) ×8, accessible-undeclared-path
probes (Fault210) ×7, HTTP-5xx (Fault100) ×1, leaked-stack-trace (Fault209) ×1** — none
order-flow-shaped; zero durable writes. (The generated fault suite carries 36 status assertions
across those 17 faults — 404×23 / 200×10 / 500×2 / 302×1 — an assertion count, not a fault
count.)

**Diagnosis of record (three-step; steps 1-2 durably artifacted post-review):**
1. **Session alive after the full run** — the prep cookie still returned a logged-in profile
   (HTTP 200) AFTER the 60 minutes (operator-attested at probe time, and **durably entailed** by
   step 2's artifact: TeaStore places an order only for a logged-in session): not cookie expiry,
   not a tool self-logout.
2. **Pipeline proven live** — a manual full-form confirm through the SAME cookie immediately
   landed a durable order (191 → 192, `address1=diagprobe1`): auth, session, cart seeding, and
   persistence all worked. **Durable artifact:** `teastore-auth-control/diagprobe-verification.txt`
   — the diagprobe1 row re-read from the PVC-backed database on a FRESH bring-up after teardown
   (192 rows, diagprobe1 count 1; also records the 191-vs-192 sequencing: leg-summary's after=191
   was measured BEFORE the diagnostic confirm, so the run-attributable delta is 0).
3. **Root cause** — in 3.04 M actions the tool never composed a **VALID** confirm action: it
   mutates `cartAction`'s action-discriminator parameters as data fields (`addToCart="D_3IpK"`
   garbage values mixed with form fields; `confirm=` did appear garbage-valued and
   addToCart-shadowed, e.g. the generated `test_7`), so no semantically-valid confirm-shaped
   submission was ever emitted.

**The finding (strengthens, does not contradict, PWS L1):** on this run, the reachability
barrier SURVIVED auth and state provisioning — the blocking layer is **action-semantics
composition**, one level deeper than L1's spec-only barrier. **Scope (disclosed): a single
60-minute run, single SUT (TeaStore), single seed (42)** — the 3.04 M-action volume defuses
"more tries would have composed it" WITHIN the run, and the tool-class reading rests on the
MECHANISM (action-discriminator params mutated as data fields — a representation property of
spec-driven black-box generation), not on cross-SUT/cross-seed replication, which was not run.
The honest paper sentence this buys: *"even with operator-provisioned authentication and seeded
state — with the same session verified to place orders end-to-end by a durably-artifacted manual
probe — a 60-minute, 3-million-action black-box run (one SUT, one seed) never composes the
multi-field business action; its 17 reported faults are schema/path-probe/5xx noise."* The
EvoMaster cell stays a **vacuous / not-evaluable cell, disclosed** — it is NOT a detection-miss
claim (no acked baseline exists from the tool's own traffic to miss against); MIST's corpus cell
sits alongside as context, never pooled (the separate-table rail).

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
   collected by `runners/livetool/collect_leg.sh`. **Budget integrity:** statistics.csv
   `elapsedSeconds 3601` + the run.log end timestamp `17:33:49` (the run.log carries no start
   timestamp; the 16:33:46 start was the operator-observed process StartTime, consistent with
   end − 3601 s). A first process-exit monitor false-fired (a PowerShell exit-code probe bug)
   and was re-armed with a validated `tasklist` probe.

## Post-review fold (3-cold result review, same day)

`REVIEW-h2hresult-{A,B,C}` = 3× ACCEPT-WITH-FIXES, zero REJECT; all fixes folded in place:
- **A1/B4** the two diagnosis probes were prose-only → the PVC re-read durable artifact
  (`diagprobe-verification.txt`) + the operator-attested/durable-entailment labeling above.
- **A2** "never emitting the confirm-shaped submission" precision → "never composed a VALID
  confirm" (confirm= appeared garbage-valued/addToCart-shadowed).
- **A3** full jar sha256 now in this RESULT. **A4/B1** the fault census re-labeled to the tool's
  own decomposition (101×8 / 210×7 / 100×1 / 209×1 = 17 faults; 36 assertions ≠ 17 faults; the
  earlier "hiddenAccessible 404 probes" label was cross-wired).
- **B2** the session token was STILL cleartext in 4 committed tool artifacts (both generated
  test suites, low-code-index.html, statistics.csv) → redacted in place
  (`REDACTED_EPHEMERAL_SESSION_TOKEN`, 47 occurrences, 0 residual) — the artifacts' evidentiary
  structure (assertions/counts) is unchanged; disclosed here.
- **B3** the start-timestamp source stated (above). **C1/C2** single-run/single-SUT/single-seed
  scoping + mechanism-based tool-class reading + seed 42 disclosed. **C3** "MISS cell" reworded
  to vacuous/not-evaluable (no silent n_e→miss upgrade). **C4** the 2/9-vs-1/9 acked-2xx clause
  added.
