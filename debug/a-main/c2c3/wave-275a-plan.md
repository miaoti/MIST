# Wave 2.75-A — MIST enablement: read-back modality bindings + new-SUT observe-mode runs (DRAFT rev 1)

**Status:** DRAFT for 3-cold-review. **Gate:** the user opened the MIST-tool-code gate on 2026-07-10
(scoped to "MIST 启用+跑测" = enablement + runs; see `main-track-workflow-rules` amendment). Per the
standing goal, this plan executes ONLY after unanimous 3-cold-reviewer ACCEPT. This is the FIRST wave
that writes MIST tool code since the prep phase began.

## 0. Where we are (grounded facts, verified this session)
- **The centerpiece MIST result is already done:** the TT cancel→refund head-to-head (3 cells N=5,
  reviewer-accepted, `g3-headtohead-result.md`) + SS shipping head-to-head. MIST's value-delta and
  membership read-back oracles are BUILT and reviewed (`DataIntegrityRuntime`, `PairedFaultExecutor`,
  `ContractEvaluator`, `MstAuthHandler`).
- **The new-SUT captured positives are NOT yet run by MIST.** `oteldemo-checkout-lost-001`
  (async broker loss; `readback.modality: sql-probe`, `mist_bindable: false`) and
  `teastore-order-maintenance-masked-001` (masked write; `readback.modality: api-get` over an HTML
  profile page, `mist_bindable: false`) both carry a T9 applicability-boundary today: the read-back
  EXISTS and is decisive, but the oracle cannot BIND the modality at the pinned commit.
- **Why unbindable (root cause, code-verified):** the read-back oracle reads durable state ONLY over
  HTTP — `DataIntegrityRuntime` calls `s.http.getSut(readbackPath(triple))` (the decisive-read loop at
  ~L627–L700; baselines at L434/L499) and parses the body as JSON (`probeVerdict`/`extractProbeValue`).
  `TargetTripleRegistry.Triple` has `readbackEndpoint` (an HTTP path) + `readbackMode`
  {MEMBERSHIP, VALUE_DELTA}. There is NO SQL transport and NO HTML body extractor. So:
  - OTel `sql-probe` (psql count on `accounting."order"`; the SUT has NO order-query HTTP API) →
    unbindable because the transport is HTTP-only.
  - TeaStore `api-get`-over-HTML (the `/profile` Orders table) → the transport is HTTP (fine) but the
    body is HTML, and the extractor is JSON-only.
- **Enablement is currently code-per-SUT:** the g3 harnesses (`CancelRefundHeadToHead`,
  `ShippingEnqueueHeadToHead`) construct their `Triple`s in Java and call the shared oracle; there is
  no config-driven triple loader today.

## 1. Objective (what this wave converts from pre-registered → measured)
Turn the two new-SUT T9-boundary cells into **measured MIST results** by binding their read-back
modalities, then running MIST observe-mode on the captured pairs:
- **OTel-Demo (PRIMARY, derisking pilot):** bind `sql-probe`; run MIST on
  `oteldemo-checkout-lost-001` (fault) + `-control-001` → expect **FLAG on fault / no_flag on control**
  (the accounting row is absent under broker-down, present on control). This is the flagship async
  acked-but-lost, and the psql read-back is clean and deterministic — the right case to prove the seam.
- **TeaStore (FAST-FOLLOW, same wave iff the seam makes it cheap):** bind HTML `api-get`; run MIST on
  `teastore-order-maintenance-masked-001` (fault) + `-control-001` → expect **FLAG / no_flag** (the
  order marker absent from the `/profile` HTML under maintenance, present on control).

Both are the SAME discriminating signature as the reviewed TT/SS results (typed read-back FLAGs a
success-shaped-clean masked/lost write that the trace-only comparator arms miss), now extended across
the async + HTML-durable modalities. **Non-goal this wave:** the full E2 5-arm frontier (step 6), TT
2.5 trace instrumentation, and the comparator arms — those are later waves. This wave is MIST-side
read-back only.

## 2. Engineering — the read-back transport seam (the tool code)
**Design principle (karpathy §2/§3):** minimum change, reuse the reviewed polling/gate/verdict logic
verbatim; touch only the transport + extraction seam. Do NOT refactor the oracle's decision logic.

**2.1 Introduce a `ReadbackProbe` seam.** Today the oracle hardcodes `s.http.getSut(readbackPath)`.
Extract a tiny interface — "given the triple + the leg's isolation key, return a probe observation
(a status + a value/collection surface + a raw record for disclosure)". Provide implementations:
- `HttpJsonReadbackProbe` — the EXISTING behavior, refactored behind the seam (no semantic change;
  proven by the TT/SS suites staying green — this is the regression guard).
- `SqlReadbackProbe` — runs the case's SQL locator and returns a count/row surface. Two candidate
  transports (decide in review): (a) JDBC over a port-forward to postgres (principled; needs the
  driver dep + PG port exposed), or (b) `kubectl exec … psql` shell-out (matches what the capture
  runner used; no new dep). **Lean (b)** for parity with capture + zero driver surface, unless a
  reviewer prefers JDBC. The membership verdict = `count > 0 ⇒ PRESENT`, `count == 0 ⇒ ABSENT`.
- `HtmlFieldReadbackProbe` — HTTP GET (reuse the http client + auth) then extract the marker via a
  pinned, brittle-by-disclosure locator (CSS/text match on the `/profile` Orders table). Membership
  verdict = marker present/absent.

**2.2 Registry + schema wiring.** Add a `readback.transport` discriminator to the `Triple`
(http-json | sql | html) so the modality is explicit and validated (mirror the existing VALUE_DELTA
guards: e.g. SQL transport requires a `sqlLocator` + a DB connection descriptor; HTML requires a
field locator). The case-JSON `readback.modality` already carries `sql-probe`/`api-get` — the triple
builder maps it. **`mist_bindable` flips false→true for these two modalities at THIS commit**; record
the flip in `c2-freeze.md` §6 (the T9 rows become bindable-and-run, moving out of the boundary bucket
into the MIST recall denominator — exactly the mechanism the T9 + bindable-pending-eval conventions
pre-registered).

**2.3 Anti-circularity (pre-empt the reviewer concern).** MIST's SQL read-back uses the SAME psql
locator the capture used — that is NOT circular: the case LABEL (positive/negative) comes from the
authored design, never from MIST's output; MIST is being TESTED on whether its oracle, given the
binding, independently produces FLAG/no_flag. The shared read-back path is expected (it is the SUT's
only durable record). Disclose this explicitly in the run record.

## 3. Per-SUT enablement packages (2.75 checklist)
Follow the existing code-per-SUT pattern (a small harness class per SUT reusing the reviewed oracle),
NOT a speculative config-driven registry (that generality is not requested — karpathy §2).
- **OTel-Demo:** author OpenAPI (pre-registered as authored-by-us) for the entry write
  (`POST /api/checkout`) + the (nonexistent) read-back → the read-back is SQL, so the OpenAPI covers
  the write path only + documents the SQL read-back as the durable oracle; registry entry; auth =
  none (session = uuid, no JWT); a target triple for the checkout-lost pair; one observe-mode run
  whose Allure shows the data-integrity section. Record authoring cost (minutes-per-bound-endpoint).
- **TeaStore:** author OpenAPI for `POST …/placeorder` + the `/profile` read-back; registry; auth
  glue (the cookie-session the capture used); triple; observe run + Allure. Record authoring cost.
- **DoD per SUT = the 1.9 user flow** (observe-mode run reaches ≥1 authed endpoint and emits the
  data-integrity Allure section on BOTH legs).

## 4. Runs — what gets measured (the "跑测")
For each SUT, run MIST observe/paired mode on the captured pair, N≥4 consecutive per leg (the standing
runbook), control-leg-first:
- **Expected cells (pre-registered NOW, before any run):** `mist_readback = FLAG` on the fault leg,
  `no_flag` on the control leg; `mist_bindable = true`. The ack columns stay success-shaped-clean
  (no sentinel). If a run REFUTES the expectation (e.g. the psql read-back can't be reached, or the
  HTML locator is too brittle) → dated disclosure + the cell stays `not_applicable` with the reason,
  NO silent re-scope (the wave-3a refutation discipline).
- **Deterministic vs stochastic:** OTel broker-DOWN loss is PERMANENT and deterministic (unlike the
  kafkaQueueProblems flag) → clean FLAG expected. Use the captured `oteldemo-checkout-lost-001`
  mechanism (kafka scale-0), NOT the stochastic flag.
- Record each run's raw psql/HTML observation + the RunRecord (for the disclosure trail).

## 5. Discipline / standing rules (carried)
- All code on `main_track`; karpathy skill; no Co-Authored-By; no file deletion; FILE_INDEX + memory
  sync per change; per-item commits.
- **Regression guard:** the existing mist-cli suites (incl. the TT/SS oracle tests) MUST stay green
  after the seam refactor — the `HttpJsonReadbackProbe` extraction is behavior-preserving. Add unit
  tests for the SQL + HTML probes (count>0/⩵0, HTML marker present/absent, error→ERROR-record).
- Cases changed only via the freeze §6 amendment (mist_bindable flip + the run cells).
- Cluster ops via CRLF-stripped script files; OTel + TeaStore are UP (from the wave-3a close-out) —
  no redeploy; the OTel recovery runbook (rollout-restart on kafka pod replacement) applies if a run
  cycles kafka.

## 6. Open questions for reviewers (pressure-test these)
1. **SQL transport:** kubectl-exec-psql (capture-parity, no dep) vs JDBC-over-PF (principled, +driver).
   Which is the sounder MIST binding, and does exec-shell-out compromise the "tool" framing?
2. **Scope cut:** OTel-only this wave (derisk the seam on the clean case) vs OTel+TeaStore together
   (the checklist authors both OpenAPI specs as a B-MAJOR). Is the HTML extractor brittle enough to
   warrant its own wave?
3. **`mist_bindable` flip provenance:** is flipping false→true at this commit + running enough to move
   the T9 rows into the recall denominator, or must the binding meet a higher bar (e.g. the OpenAPI
   contract must express the read-back) before the cells count as measured MIST results?
4. **Enablement shape:** bespoke harness-per-SUT (matches g3, fast) vs a first step toward a
   config-driven triple registry (more general, more code). Is the bespoke path acceptable for the
   paper, or does it undercut a "general tool" claim?
5. **Observe vs paired mode:** run the new SUTs in the same paired-executor mode as TT/SS, or a
   simpler observe-mode single-leg + control? Which produces the cleaner, defensible result cell?

## 7. File manifest (what this wave will touch)
- MIST tool code: a new `ReadbackProbe` seam + `Sql`/`HtmlField`/`HttpJson` probes under
  `io/mist/cli/fault/`; `TargetTripleRegistry.Triple` gains the transport discriminator + guards;
  two enablement harness classes (OTel, TeaStore) under `io/mist/cli/…`; unit tests.
- SUT enablement assets: authored OpenAPI specs (OTel, TeaStore); registry + triple configs; auth glue.
- Corpus: `oteldemo-checkout-lost-001` / `-control-001` + `teastore-order-maintenance-masked-001` /
  `-control-001` gain measured `mist_readback`/`mist_bindable` cells (freeze §6 amendment).
- Docs: this plan → RECONCILIATION after review; a RESULT record per SUT run; FILE_INDEX + memory.

## 8. DoD (wave complete when)
- Suites green (regression + new probe tests). `mist_bindable=true` for the two modalities.
- OTel (and, if in-scope, TeaStore) observe/paired runs recorded, N≥4/leg, with FLAG/no_flag measured
  (or a dated refutation if the binding fails). Freeze §6 + README + FILE_INDEX + memory synced.
- Authoring cost recorded per SUT. RESULT-of-record note written. Per-item commits on `main_track`.
