# L2 — F-corpus build: LAUNCH-READINESS (2026-07-17)

**Status: READY-TO-LAUNCH, NOT-YET-STARTED.** L2 is the multi-day clean-room build of the
7 survey-eligible upstream-grounded faults — the leg that answers the reviewer attacks on
CORPUS SIZE and INJECTED-vs-natural POSITIVES. It needs sustained TT windows + a build
pipeline (mvn + docker + set-image per touched service), so it does not fit the tail of a
window shared with EvoMaster-TT / L3 / L4. This note captures everything so a dedicated
window (this session's continuation or the next) launches it cleanly.

## The 7 faults + the CLASS SPLIT (the review's load-bearing fold)

| F | target service(s) | occupancy | class (rev-2 split) |
|---|---|---|---|
| F1  | cancel / inside-payment / order | OCCUPIED (flagship) | async refund sequencing — **LOST** candidate (the 1 lost-class) |
| F8  | auth / VIP token path | **UNOCCUPIED (new-site)** | token/tier **CORRUPTED-present** |
| F10 | ts-contacts-service | OCCUPIED (contacts) | **CORRUPTED-present** |
| F11 | cancel flow sequencing | OCCUPIED (cancel) | **CORRUPTED-present** |
| F13 | cancel vs payment interleave | OCCUPIED (cancel) | **CORRUPTED-present** |
| F14 | price calculation | **UNOCCUPIED (new-site)** | price **CORRUPTED-present** |
| F20 | order-status version skew | OCCUPIED (order) | status **CORRUPTED-present** |

**6/7 are CORRUPTED-present (incl. both new-site F8/F14); ≤1 is LOST (F1).** Per the
review fold: corrupted cases go IN-CORPUS via the additive `fault_class` schema amendment,
but their MIST column = principled n_a "out-of-scope-by-design" (MIST is lost-only; its
correct abstention is NEVER scored a miss). New SITES ≤ 2; the rest = case-run depth.

## Clean-room protocol (X5, VERBATIM — the isolated implementer's ONLY inputs)

- Implementer = an ISOLATED subagent (explicit non-fable model) whose ONLY inputs are
  `debug/a-main/c2c3/f-corpus-spec.md` + the clean Apache-2.0 `FudanSELab/train-ticket`
  base source in the fork. It NEVER fetches `train-ticket-fault-replicate` or any re-host
  or any `ts-error-*`/`istio-error-*` branch content. Per-fault input artifact recorded.
- Fork: `C:\Users\miaot\Github\train-ticket-injection` @ branch `MIST-trainticket`.
- Toggle discipline (the drawBack collision analysis, `b4/pws/drawback-collision-analysis.md`):
  ONE image, each fault behind its OWN `static volatile String <name>FaultMode` field
  DEFAULT "none" + its own toggle endpoint; NEVER overload `drawbackFaultMode` /
  `createAccountFaultMode`; modified files carry Apache-2.0 §4 change notices.
- Post-build fabricatedack REGRESSION (paired FIRE reproduces) gates every F-corpus capture.

## Per-fault pipeline (each)

implement (isolated actor) → [one batched mvn+docker build + set-image] → **class-aware
B-m6 LIVE VERIFY** (acked-2xx + durable state WRONG-or-ABSENT by direct read, matching the
fault's described class; 2 attempts then swap/disclose) → capture fault+control legs
(control-first, marker salt, 800ms pacing) with **machine-read read-back bodies persisted**
(the A3(ii) lesson; the L3 read-back capture pattern works — `L3-readback-bodies/`) → case
files (schema-valid; occupied = mechanism-variant, never new-site; F8 C-A4 artifact
adjudication AT authoring, recorded in-case) → neutralize via the hardened harness →
validator + tell-audit dry-run → integration-chain regen (censuses/map/release-staging).

## Launch prerequisites (all present)

- TT fork checked out @ MIST-trainticket ✓ · f-corpus-spec.md B0-survey-executed ✓ ·
  the cancel-refund subgraph revival works (this session) ✓ · the read-back capture
  pattern proven (L3) ✓ · the collision analysis done ✓ · EvoMaster/Schemathesis specs
  reusable for the new sites' comparator cells ✓.
- Needs: a sustained TT full-graph window (the 18-service cancel subgraph + admin, plus
  auth/price for F8/F14) + the mvn/docker build pipeline in the fork.

## Honest scope note

L2 is the single largest remaining PWS piece (multi-day). It is LAUNCH-READY; it was not
started in the L1/L3/L4/EvoMaster-TT window because it needs its own sustained build+capture
windows. The corpus stands at 27 validator-green cases WITHOUT L2; L2 is ADDITIVE
depth/grounding, pre-disclosed as such (never load-bearing for the headline).
