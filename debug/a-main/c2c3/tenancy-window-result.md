# Tenancy-window wave — RESULT OF RECORD (2026-07-10)

Executes `tenancy-window-plan.md` rev 2 (UNANIMOUS 3-reviewer accept; recon
`REVIEW-TENANCY-RECONCILIATION.md` T1–T15). Commits: Phase A+B `ccf4917` (+doc sync
`7e3a84e`/`521ae1e`/`3db1b3a`), Phase C `e9c8773`, Phase D pre-capture freeze `8bb0424` +
captures `3c8d581`, Phase E (this note + freeze/README/checklist rows). All corpus changes
validate: **18 cases, 8 positives / 10 negatives, exit 0**.

## 1. Earned vs deferred, per leg

| leg | EARNED (measured, capture-of-record) | DEFERRED / NOT EARNED (disclosed) |
|---|---|---|
| bookinfo benign (B) | naive=FLAG + presence=FLAG on the designed degradation — **both structural columns measurably FP**; 9-span clean control validates both span families (T2) | `mist_trace_shape` Branch-B → the MIST-side pair-separation (precision) claim **stays pre-registered** until 2.5/E2 |
| sockshop genuine + control (B) | presence=FLAG TP (consumer span absent; validated baseline); **T2 divergence disclosed: pre-registered naive=flag REFUTED** (no producer span before basicPublish → measured no_flag FN); control: naive=FLAG on CLEAN operation (docker-socket consume error, diagnosed) → naive fails all three ways, all measured | same Branch-B scoping; front-end Node instrumentation descoped (T12 amendment) |
| TeaStore masked write + control (C) | The SUT's own maintenance flag → persistence CREATE fabricates **201/body `-1`** (same body 500s healthy — measured matrix); confirmed page + marker ABSENT in-window AND post-restore, DB intact; N≥4 probes all masked; **all three ack columns ran-and-missed**; control lands everywhere | trace cells `not_applicable` (Kieker; 2.5.3 exclude branch taken for the captures; converter spike still open); `mist_readback` = T9 boundary (HTML modality) w/ preserved FLAG design target |
| TeaStore riders (C) | **C-M4/T15: plain-VS INTERCEPTS** (HOST_NAME=svc DNS; expected miss REFUTED); **mesh-503 rider VERIFIED-MASKED live** (503 swallowed into the confirmed page, marker lost — exactly the survey's `-1L` chain); DB-down ruled UNSOUND-for-capture (no-PVC wipe destroys absence evidence); survey toggle-path corrected; input-driven bogus-uid claim UNADJUDICATED (probe-body confound) | mesh-sever S1 case NOT authored this wave (plan-pinned 2 TeaStore cases) — a one-driver-run 3a candidate |
| TeaStore C3b (C) | **Recommender cold-start REFUTED as user-visible** (pod-delete timeline: page never degraded at ~1.4 s sampling; TrainingSynchronizer isReady gating + registry-LB bridge the ~3 s window) — the rider verification is the deliverable | no TeaStore S2 case (premise refuted, not skipped); S2 quota moved to D3b — which also refuted (below); the S2 stratum stays bookinfo + 2 TT benign traps |
| OTel-Demo async flagship + control (D) | Broker-down PlaceOrder acks **200 at ~0.02 s** (§2.3 latency rider VERIFIED — produce fully async); accounting-Postgres row **PERMANENTLY absent** (in-window, post-restore, post-verified-heal; probes 4/4 + 4/4 across both attempts); **naive no_flag ran-and-missed with the producer span PRESENT+CLEAN on both legs** (sharper than sockshop — the trace looks successful at the producer); **presence FLAG ran-and-caught** via the linked consumer trace (`presence_scope=file`, pre-committed `8bb0424`); control validates the family + the postgres INSERT client span (T6) | `mist_readback` = T9 boundary (sql-probe; the psql probe IS the ground truth, MIST cannot bind it at the pin) w/ preserved FLAG design target; `mist_trace_shape` Branch-B; **the case carries NO runnable MIST column this wave — comparator measurement + boundary documentation until MIST runs (disclosed)** |
| OTel-Demo riders (D) | **Recovery-window datum (measured):** a replaced kafka pod (emptyDir, new cluster id) wedges BOTH rdkafka clients — the old producer keeps silently losing 200-acked orders PAST the restore (4 heal canaries lost) until a checkout restart; the old consumer stops while later messages buffer DURABLY and drain after an accounting restart (**pending-vs-missing observed live**); **D3c flagd re-freeze** (15 deployed flags; 13 3a-eligible; percentage-graded paymentFailure; intlShippingSlowdown gone) | **D3b graceful-ad REFUTED as an S2 case** (ads are browser-XHR; `/api/data` 500s honestly under ad-down while the SSR page stays 200 — no success-shaped server-side trap; ~30 s gRPC reconnect-backoff datum recorded) |
| B5 Tracetest smoke | — | SKIPPED (optional rider, disclosed in the Phase-B row) |

## 2. Capture-hygiene incidents (disclosed, no rule changes)
- **OTel attempt-1 export window** caught the 4 probe traces → 5 entry traces broke the frozen
  exactly-one rule → full re-capture with a 12 s quiet gap + an export pre-check; attempt-1 files
  kept as `*-attempt1` in the capture dir. No selector/scope/rule change.
- **TeaStore endpoint discovery** once issued a bare `GET /rest/generatedb` (regenerates the DB);
  all capture-leg users/markers postdate the regeneration (disclosed in the case notes; the sharp
  edge is now written into the spec headers + survey).
- A **diagnostic detour** ("accounting cannot persist — krb5") was WRONG: the krb5 stderr is a
  harmless Npgsql 10 GSS probe and the tables live in schema `accounting` (a bare `\dt` misled);
  the temporary GSS-disable env override was REVERTED (rows land with the as-shipped env). Recorded
  in the values-file comment + case notes.

## 3. End state (recorded per plan §1 default)
- **TT**: scaled to 0 (snapshot `/home/miaot/gate1-logs/tenancy-window/tt-replica-snapshot.txt`;
  helm infra + PVCs persist; revival = §2.6 runbook + the nacos doubleWrite rule).
- **OTel-Demo**: UP (the §1 default — next-window big-solo tenant for 2.75/3a).
- **TeaStore**: UP (C4: RAM allowed — both fit with TT at 0; ~16 GB free at close).
- bookinfo + sockshop: scaled to 0 (Phase-B teardown; agents stripped).
- Port-forwards (die on reboot): OTel frontend-proxy :8085, OTel jaeger :16687 (`/jaeger/ui/api/*`),
  TeaStore webui :8082, TeaStore persistence :8083.

## 4. What this buys the paper (evidence now in hand)
1. **Four write-path SUTs with captured cases** (TT forked-flag + 3 UN-FORKED natural producers:
   sockshop source-comment swallow, TeaStore vendor-flag fabrication, OTel-Demo async broker loss) —
   kills "one SUT, one fork" at the root.
2. **The comparator indictment is now measured, not asserted**: naive FPs on designed degradation
   (bookinfo), FNs on genuine swallows (sockshop enqueue, OTel produce — both with NO error span),
   FPs on clean operation (sockshop docker-socket); presence catches the two genuine async/enqueue
   losses but FPs the benign trap (bookinfo) — the semantic gap the MIST columns are pre-registered
   to close at 2.5/E2.
3. **The T9 applicability-boundary convention** (freeze §6): read-backs that EXIST but are
   modality-unbindable (sql-probe, HTML api-get) are their own reported row — the honest scoping
   that survives a hostile PC.
4. **Live-measured operational taxonomy** for the async class: permanent-vs-pending loss,
   the producer-wedge loss-window extension, and the restart runbook — by-measurement material for
   the paper's phenomenon section.

## 5. Next (per plan §7 / standing arc)
The next tenancy flip default: OTel-Demo stays up → **2.75 (MIST enablement decisions: read-back
modality bindings) + 3a (live S1 quota: TeaStore mesh-sever candidate, OTel vendor-flag set,
S3 wild-hunt)** → TT revival from snapshot when the TT-side legs (2.5/E2 `mist_trace_shape` runs)
are scheduled. Rater track proceeds in parallel (user-side screening; rating corpus from the
corpus track at the step-5 gate).
