# COLD REVIEW B — wave-3a-plan.md (DRAFT rev 1) — capture-discipline / reproducibility lens

**Reviewer:** independent cold reviewer B (no shared context with the author or other reviewers;
judgment formed from repo files only — no cluster/network commands run).
**Target:** `debug/a-main/c2c3/wave-3a-plan.md` (DRAFT rev 1).
**Grounding read:** `tenancy-window-result.md` (incl. §2 hygiene incidents, §3 end state),
`c2-freeze.md` §2/§4/§6 (2026-07-10 rows incl. T9 + R2/R3), `tenancy-window-plan.md` §3 discipline
(T2/T4, N≥4-after-topology-change, quiet gaps), `c2-depth-survey.md` (D3c re-freeze block, item-1/3
source chains, Phase-C corrections), `b4/capture_driver.py` + the three frozen specs,
`b4/trace_score.py` (frozen selector table, Phase-D `presence_scope=file` comment, exactly-one rule),
`b4/captures/oteldemo-checkout-lost/readback-psql.txt` (attempt-1/2 disclosure pattern),
`step2-execution-checklist.md` 2.2/2.3/2.5.4, existing case JSONs + sidecars (user/marker record).

## VERDICT: ACCEPT-WITH-FIXES

The wave is well-chosen (the bindable-read-back positive is the unique paper asset; the collapse
order is right; the item-3 refutation branch is correctly wired; §7 arithmetic checks out). But two
capture-mechanics assumptions are asserted as fact without any verifying record, several inherited
disciplines are invoked generically where the incidents that created them demand per-item pins, and
the reproducibility artifacts (specs, scripts, manifests, user ledger) are under-specified in ways
that would make the captured cases non-replayable as recorded. All fixes are cheap; none change the
wave's shape.

---

## Findings

### F1 [BLOCKING] — Item 1/3 flagd toggle mechanism is UNVERIFIED in the repo record; pin a pre-capture toggle-mechanics probe + access path + fallback ladder + restore reference
The plan states as fact: "flagd ConfigMap patch (`cartFailure` defaultVariant off→on) + flagd
hot-reload." The entire repo record on flagd config is: (a) the D3c re-freeze **read** the live
`flagd-config` ConfigMap (`c2-depth-survey.md:156`) — a read-only operation; (b) the main-branch
survey line "flagd-ui allows runtime toggling" (`c2-depth-survey.md:136`) — a docs claim about a
**different** mechanism, and whether flagd-ui is even deployed in the trimmed chart (checklist 2.3
values trims) is not recorded. Nothing anywhere verifies that a ConfigMap patch propagates to
flagd's mounted file and that flagd hot-reloads it. Two failure modes are silent-ish: kubelet
ConfigMap propagation is delayed (sync-period dependent, can be ~1 min), and a **subPath** mount
never receives updates at all — in which case only a flagd pod restart works, which is a topology
change with ordering consequences. The plan's per-leg "verify via the flag evaluation API" would
catch a dead patch (good), but the wave would then stall with no pinned fallback, and the evaluation
API itself has no recorded access path (standing PFs are frontend-proxy :8085, jaeger :16687,
TeaStore :8082/:8083 — no flagd).
**Fix (pin BEFORE the N≥4 probes, as item-1 step 0):**
1. Scripted toggle-mechanics probe: patch `cartFailure` off→on → poll the evaluation surface until
   the value flips (record propagation latency) → patch back → verify off. This is the verification
   step the review prompt's discipline requires before trusting the mechanism for a leg toggle.
2. Pin the evaluation access path (in-cluster curl via kubectl exec to flagd's evaluation/OFREP
   endpoint, or a new scripted PF) — as a committed script file (script-files-only).
3. Pin the fallback ladder: (i) ConfigMap patch + bounded wait; (ii) flagd-ui API **if deployed**
   (record whether it is); (iii) flagd pod restart — declared LAST-resort with its consequences
   pinned: restart completes + readiness + N≥4 healthy probes before any leg; never mid-leg (a flagd
   restart briefly breaks evaluation for every flag-consuming service).
4. Restore reference: after each leg and at item end, compare the live `demo.flagd.json` against the
   D3c-frozen list (the deployed-config re-freeze row is the authority) AND re-verify via the
   evaluation API; disclose the kubectl-patch-vs-helm-values drift caveat (a later helm upgrade
   would revert; record-only).
5. State explicitly that item 3 inherits this pinned mechanism + verification (the plan currently
   restates the toggle only for item 1).

### F2 [BLOCKING] — Item 2 teardown/restore is a one-liner; enumerate the pinned steps or risk stranding the SUT off its pristine shape
"sidecars + VS torn down after" hides every step that has previously bitten. Missing pins:
1. **Injection mechanism unpinned.** Namespace `istio-injection` label vs per-deployment template
   annotation is not stated. A namespace label (or one left behind) means ANY later pod restart in
   `teastore` — including persistence/db — silently gains a sidecar; and a persistence/db cycle also
   WIPES the no-PVC database (recorded Phase-C finding), destroying the user ledger and any
   absence evidence. **Pin: per-deployment annotation patch on webui+auth only; never the ns label.**
2. **Teardown checklist (ordered):** delete the VS (+ any DestinationRule) and verify
   `kubectl get vs,dr -n teastore` empty → remove the annotations → rollout webui+auth → verify BOTH
   deployments back to **1/1 containers** (and, in the other direction, verify 2/2 during the legs —
   the §5 "sidecar leak check" made concrete) → healthy heal probe (a fresh-marker order lands).
3. **PF re-creation rule.** kubectl PFs bind a pod; every webui pod cycle (at least twice: sidecar-on,
   sidecar-off) kills the :8082 PF that the frozen specs' `base_url: http://localhost:8082` requires
   (`tenancy-window-result.md` §3 records these PFs as fragile). Pin: re-create + verify :8082 (and
   assert :8083 persistence alive) after EVERY rollout, before any leg or read-back.
4. **Hard guard:** persistence/db must NOT cycle during item 2; if either does, data regenerates —
   the item restarts with fresh identities + a disclosure (do not continue on a regenerated DB).
5. **N≥4 applies at injection time too.** The standing rule is N≥4 consecutive expected-behavior
   probes after ANY topology change (`tenancy-window-plan.md` §3). The plan has N≥4 only *under the
   VS*. Add the post-sidecar-injection healthy probe round BEFORE applying the VS — it is also the
   deploy-shape-parity evidence that sidecars alone don't perturb the flow, and it pins leg order
   (healthy probes / control side first → VS → fault leg).

### F3 [MAJOR] — Item 1: make the canary outputs part of the T4 pre-commit and pin the export mechanics (the attempt-1 lesson applies verbatim)
The probe-then-freeze language is right but under-bound:
1. The `presence_scope` decision (same-trace vs `file`) + the exact EmptyCart span binding must be
   recorded from the canary and **committed into `trace_score.py` SELECTORS (new `oteldemo-emptycart`
   prefix rows) BEFORE the first real capture** — the Phase-D comment in the scorer is the precedent
   for why "sync gRPC ⇒ same trace" cannot be assumed on this SUT; treat "presence_scope default" as
   a hypothesis the canary confirms, not a decision already made.
2. Pin the export query = windowed **service=checkout** (the flagship convention). The flow's own
   cart-add POST creates a second cart-touching trace: a service=cart or service=frontend-proxy
   window query returns ≥2 candidate traces and collides with the exactly-one rule; service=checkout
   + the entry-server filter is the safe shape.
3. Probe pollution: the N≥4 flag-ON probe sessions create their own frontend-proxy POST entry traces
   in the window — exactly the attempt-1 incident (5 entry traces → unsatisfiable exactly-one → full
   re-capture). Restate per-item: probes → quiet gap (≥12 s precedent) → capture-of-record → export
   pre-check (exactly one entry trace in the window) → export; any re-capture keeps `*-attempt1`
   files (the `readback-psql.txt` disclosure pattern).
4. The claimed asymmetry (presence=no_flag PRESENT-but-erroring MISS + naive=flag CATCH) is a
   pre-registered EXPECTATION — record it as such and score as measured under the T2 divergence rule
   (no post-capture selector/scope change to chase it). The canary must also confirm the naive scope
   {frontend-proxy, frontend, checkout, cart} is error-quiet on control (the flagship excluded
   fraud-detection for its routine EventStream errors — same hygiene check here).

### F4 [MAJOR] — Item 1 MIST-cell pin: right value, but it creates a FOURTH `not_applicable` reason that must not pool with T9 boundary rows
The author asked reviewers to check this pin. `not_applicable` + FLAG design target in notes is the
right value, but the reason — **bindable modality, MIST simply not run this wave** — is distinct
from the three existing `not_applicable` reasons: (i) T9 modality-unbindable (sql-probe / HTML),
(ii) trace-uninstrumented-as-deployed, (iii) in-process/no-presence-target. The freeze's T9 row
says boundary cells get their own reported applicability row; if this case's cell lands in that same
bucket, the wave's own headline ("first non-TT `mist_bindable=true` read-back") is muddled by its
bookkeeping. **Fix:** pin the reason string in the case notes AND in the freeze §6 amendment row
("MIST-not-run-at-capture; bindable at the pin; run scheduled 2.5/E2 via 2.75 enablement"), and add
the case explicitly to the 2.5/E2 run list so the design target has a discharge path.

### F5 [MAJOR] — Item 2 user hygiene: user18/user19 freshness is asserted, not verified; no next-free-user ledger exists
Repo record of consumption: user12 (masked fault leg, TSMWF1), user13 (control, TSMWC1), user15
(probe rounds PRB1–4 + CPRB1) — from the case JSONs/sidecars. But the mesh-503 rider leg and the
endpoint-discovery detour consumed UNRECORDED identities (user11/14/16/17 status is simply unknown
in the repo), so "user18/user19 fresh" is a plausible guess, not a record. **Fix:**
1. **Verify, don't assume:** per identity, a pre-leg baseline read (login + GET profile; assert zero
   marker-shaped rows; record the generated-order count) before the leg uses it. This converts
   freshness from ledger-dependent to measured.
2. **Write the ledger:** the wave result addendum + case notes record consumed users to date
   (user12/13/15 + "≤user17 quarantined as possibly rider-consumed"), the identities this wave takes,
   and the next-free pointer for future waves.
3. **Count the slots:** item 2 needs ~4 identity slots (post-injection healthy probes, VS probes,
   fault leg, control leg), not 2. Pin whether probes ride a dedicated probe user with distinct
   markers (the maintenance precedent: user15 + PRB1–4) or the leg users; keep the fault-leg user's
   history to exactly its one marker if possible, and verify probe markers by exact token
   post-teardown as the precedent did.

### F6 [MAJOR] — Item 3: "the standard window" is undefined, but the S2 FP measurement is entirely window-relative
Whether the presence column FLAGS on this negative depends on WHEN the export + psql read happen
relative to the delayed landing — an unpinned window makes the FP manufactured-by-choice.
**Fix (pre-commit before the capture-of-record):** export timing/window = the flagship pair's
convention (JVM batch-lag sleep before export; windowed service=checkout + service=accounting merged
export per `presence_scope=file`; psql probe at the flagship's post-ack delay), and record the
measured landing delay NEXT TO the verdict so the case documents "flagged at t=X, landed at t=Y".
Also: add the `oteldemo-kafkaqueue` selector row (same entry/presence/scope/`presence_scope=file`
shape as `oteldemo-checkout-lost`) to `trace_score.py` BEFORE the capture — the scorer is a frozen
file whose pre-capture extension pattern is established in-file (T4/T8); without the row, the
"our presence column FLAGS" cell cannot be *measured*, only asserted.

### F7 [MAJOR] — Item 3: "no wedge expected" is only safe while the kafka POD is not replaced — and overload is the flag's designed effect
The recorded wedge cause is pod replacement (emptyDir, new cluster id). The flag does not restart
kafka — but it floods it (100 duplicate goroutine sends/checkout + consumer-side sleeps); a kafka
OOM/crash-restart under the flood recreates the wedge scenario live. **Fix:** (a) pin the trigger
condition — watch the kafka pod restart count during item 3; ANY restart ⇒ the kafka-recovery
runbook applies (rollout-restart checkout+accounting+fraud) AND the leg is invalid + re-run with
disclosure; (b) pin the post-item drain check before the SUT is declared healthy for anything else:
fraud-detection's backlog drains at ~1 s/msg × (100 dups × N sessions) ⇒ minutes — verify consumer
catch-up + a heal canary landing at normal delay; (c) `free` (or equivalent) after the item, not
only before — the flood is a during-item RAM/disk event on a box carrying OTel(21 pods)+TeaStore(7).

### F8 [MAJOR] — Item 3 probe round: pin the recorded artifacts, not just the verification questions
The plan lists what to verify ((a) lands with what delay, (b) dedupe-only duplicates, (c) semantics
match) but not what the probe round must RECORD. **Fix — committed probe-round outputs:** per-probe
order→row landing delay (psql poll timestamps → the delay distribution), dedupe evidence
(rows-per-order_id == 1 + accounting unique-violation/skip log lines), observed duplicate volume,
the flag-semantics check against the D3c-frozen config, timebox accounting, and an explicit
refutation-branch decision line. These feed the case notes if authored and the dated survey
correction if refuted. (The refutation wiring itself — case NOT authored, C-m8/R9 precedent,
disclosed survey finding — is correct as written; no change.)

### F9 [MAJOR] — Item 3: the presence-FP cell needs a same-deploy family baseline citation (the "bookinfo precedent" cuts both ways)
No control leg is fine for the CASE (S2 convention), but bookinfo's benign leg leaned on a 9-span
clean control to validate the span families (T2) before its FP verdicts counted. The kafkaqueue
presence=flag cell needs the accounting consumer-span family validated on THIS deploy. **Fix:** pin
the citation — the flagship `oteldemo-checkout-control` trace (same deploy, same selectors) is the
family baseline, valid provided no deploy change in between (item 1's ConfigMap patch is restored
and verified per F1, so this holds); or take one throwaway healthy canary trace pre-flag (item 1's
canary can double). State it in the case notes.

### F10 [MAJOR] — Item 2: the blanket trace-cells-`not_applicable` call needs a pre-registered rationale that survives the bookinfo comparison
The author asked for adjudication. The **presence** half of the call is clearly right: the
effect-side persistence server span cannot exist even on control (no sidecar on persistence, app
uninstrumented) ⇒ per T2 the presence cell must be `not_applicable` — scoring a 2-hop fragment would
fabricate an unsatisfiable assertion target. **But the naive half is not automatic:** with sidecars
on webui+auth under mesh-wide Telemetry, the fault leg likely emits an Envoy client 503 error span —
a mechanically scoreable `naive_span_error` cell — and bookinfo's benign case DID score Envoy-only
spans (naive=FLAG FP). Measuring naive on a benign case but declining it on a genuine case (where it
would be a CATCH) is exactly the shape a hostile reviewer calls motivated. **Fix — decide BEFORE
capture, in the frozen note, one of:** (a) measure-and-report the naive cell with the disclosure
that the error span is emitted by the injection vehicle itself (an Envoy abort self-report; its own
reported row, not pooled with app-instrumentation verdicts); or (b) pre-register the exclusion
rationale explicitly (sidecars exist solely as the injection vehicle; the SUT's as-deployed
`trace_visibility` is trace-uninstrumented; a 2-sidecar shape is not the case's deploy shape) and
disclose in the notes that Envoy fragments exist and were excluded by this pre-registered rule.
Either is defensible; deciding after seeing verdicts is not.

### F11 [MAJOR] — Spec reuse breaks replay documentation: the case JSONs record the spec YAML as `replay_script`, and the frozen headers describe the WRONG leg for every new pair
Existing cases record `sut.replay_script` = the capture-spec path (verified in
`oteldemo-checkout-lost-001.json` and `teastore-order-maintenance-masked-001.json`), so spec headers
ARE the replay semantics. As reused: `oteldemo-checkout-flow.yaml` says "the leg is the KAFKA
deployment's scale" (wrong for cartFailure — and its cart observation is documented as "entry
surface healthy" while item 1 promotes that same observation to the READ-BACK OF RECORD);
`teastore-order-flow.yaml` says "the leg is the persistence service's MAINTENANCE flag" and
`teastore-profile-readback.yaml` says "run AFTER the maintenance flag is toggled back off" (both
wrong for mesh-sever). **Fix:** author per-pair spec files (e.g. `oteldemo-emptycart-flow.yaml`,
`teastore-order-meshsever-flow.yaml` + its readback twin) with correct leg-toggle headers, copying
the sharp-edge warnings verbatim (the `GET /rest/generatedb` hazard, cookie_session notes); steps
stay identical so the frozen driver is untouched. Alternatively dated header amendments — but new
files are cleaner and collision-free.

### F12 [MAJOR] — Reproducibility artifacts must be NAMED, COMMITTED repo paths per item (script-files-only made concrete)
The plan invokes script-files-only generically but names no artifacts. The Phase-C mesh rider's
live artifacts exist only under WSL gate1-logs — not citable by a repo case. **Fix — name in the
plan and commit with each item:** item 1/3 flagd toggle+verify script; the psql corroboration
script + per-leg output files (the `readback-psql.txt` pattern — item 1 asserts the row LANDS on
both legs, which needs its own recorded artifact); item 2's VS abort manifest + sidecar
injection/teardown script — all referenced from the case JSONs' fault/injection + replay fields
(precedent: `evaluation/suts/sockshop/deploy/g3-write-path-enable.sh`).

### F13 [MINOR] — §0 wording contradiction
"The 18-case pilot … has ONE case with a MIST-bindable read-back outside TT (none — …)" — the
sentence says one, the parenthetical says none. Fix to "has NO case…" (the parenthetical is the
correct fact per the freeze record).

### F14 [MINOR] — §5 additions
Add: wave-start precondition check (TT still at 0 per the tenancy end state; both tenants healthy;
standing PFs alive; RAM check) · wave-close end-state declaration (both SUTs stay UP per the §1
default — record it, as the tenancy result did) · the attempt-N retention convention restated
(any re-captured leg keeps `*-attempt1` artifacts + a disclosure) · per-item leg-order pins
(control-first or re-verify toggle/VS state between legs).

### F15 [INFO] — §7 arithmetic + namespaces verified consistent
18→23 = +2 S1 positives +2 controls +1 S2 negative → 10 pos / 13 neg ✓; items 1–2 only: 22 = 10/12 ✓.
Case ids and capture dirs collide with nothing existing (checked `benchmark/cases/` and
`b4/captures/`); scorer prefix matching for `oteldemo-emptycart` / `oteldemo-kafkaqueue` is
collision-free vs `oteldemo-checkout-*`; item 2 correctly adds NO selector row. Suggest naming the
new capture dirs in the plan (e.g. `captures/oteldemo-emptycart-{lost,control}`,
`captures/teastore-order-meshsever-{masked,control}`, `captures/oteldemo-kafkaqueue-pending`) to
keep the convention.

---

## Disposition summary
- BLOCKING: F1 (flagd toggle mechanics pin), F2 (item-2 teardown enumeration).
- MAJOR: F3–F12.
- MINOR: F13, F14. INFO: F15.

With F1–F12 folded into rev 2, this reviewer's verdict converts to ACCEPT.
