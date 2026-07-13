# S3 P3 — TrainTicket window RESULT (intermediate; feeds the P6 aggregate)

Plan `s3-wildhunt-plan.md` rev 2.1 §7 P3 (**MANDATORY**: the only sync-acked + nonzero-prior surface,
§0.4/§6). Artifacts: `s3/window-trainticket/{window-log.json, ledger.json}` (no `flags/` — 0 candidates).

**MIST pin (per-window run commits; corrected post-review F1).** The CLASSIFIER (`classify()` + the
RAW/CONFIRMED predicate) is byte-identical across the three windows' run commits — OTel ran `10eb19e`
(its flag-w120 bundle stamps it), TeaStore ran `5802fa8`, and this TT window's JVM ran the working tree
captured by **`0fbe00f`** ("S3 TT window enablement"). The `0fbe00f` diff over `6a448f7` is
workload-harness only — the gateway journey-pacing knob (`WildHuntEngine.setJourneyDelayMs`, default 0)
and the TT per-run marker salt + versioned runner — touching **neither** `classify()` nor the
RAW/CONFIRMED predicate. Provenance bookkeeping: the runner snapshotted the launch-time HEAD `6a448f7`
into `-Ds3.mist.commit` (I committed `0fbe00f` moments after launch), but with 0 flag bundles emitted
that value is never written to any artifact — so the run's true tree is `0fbe00f`'s and nothing records a
conflicting stamp. **The window result is invariant to the `0fbe00f` diff** (salt = opaque marker values;
pacing = inter-write timing).

## Environment + gates (GREEN)

- **Revive:** 8-service measurement subgraph (ts-ui-dashboard, ts-gateway-service, ts-auth-service,
  ts-user-service, ts-admin-basic-info-service, ts-station-service, ts-config-service, ts-price-service),
  all 1/1 Running over the runbook-bootstrapped infra (nacos quorum + `doubleWriteEnabled=false` + mysql
  2×2 + rabbitmq, §2.6). Admin login (admin/222222) + all 3 triples live-preflight-verified.
- **§0.4 async-bound:** TT admin-basic create is **SYNCHRONOUS** — `AdminBasicInfoServiceImpl.add*`
  proxies via a *blocking* `RestTemplate.exchange` and returns only after the downstream station/config/
  price service responds (no queue/@Async). Confirmed live: 514/514 writes present-at-cap on poll 1 (no
  async lag), 0 raw-delayed. ⇒ a sync-acked create absent from its GET-all at cap would be genuine-eligible
  (like TeaStore), no async-completion bound present or manufactured (s3-p0-pins.md §4).
- **Read-back:** the built-in RestAssured transport (admin bearer via `MstAuthHandler`) GETs each triple's
  full collection; the reviewed MEMBERSHIP oracle checks `{<key>: <marker>}` over the `{status,msg,data:[]}`
  envelope. Marker is request-supplied in the create body (request-derived), never read from the response.
- **Two operational fixes (disclosed; neither is a detector change):**
  1. **Gateway pacing** — TT's Spring Cloud Gateway + Sentinel rate-limits bursts (rapid read-backs → 429).
     Journeys paced at 800 ms (`s3.journey.delay.ms`). Workload pacing only; the per-case observation
     cadence (poll 500 ms / re-probe T+5 min) is UNCHANGED, so cross-strata cadence uniformity holds
     (plan §4.2). The detector already routes a non-2xx decisive read-back to `error` (never a RAW
     candidate), so 429s could never fabricate a CONFIRMED — pacing only restores clean yield.
  2. **Per-run marker salt** — TT admin-basic writes are UNIQUE-KEYED, so a fixed seed collides with a
     prior run's rows (HTTP 200 `{status:0}` → not-acked). The runner XOR-salts the pinned base seed
     `20260713` with a per-run nonce; effective seed `317704970330729` recorded our-side in
     `environment_guard` (never rater-facing). Ban-free grammar `corpus-w<seq>-<12hex>` unchanged.
- **§3 FP calibration (20 benign, salted):** 19/19 present-at-cap, 0 CONFIRMED (0/19 ≤ 1/20) → **both bars
  PASS**. (20th journey = 1 operational skip: a `SocketException: Connection reset` at journey 4, the
  early-connection warmup blip; excluded from the denominator, never a detector event.)

## Counted window (≥500 acked writes across ALL bound TT triples)

| metric | value |
|--------|-------|
| acked bound-triple writes (denom a) | **514** |
| bound endpoints (denom b) | 3 (`configs` / `stations` / `prices`) |
| per-endpoint acked (each ≥ 100 bar) | configs **172** · stations **171** · prices **171** |
| **CONFIRMED flags** | **0** |
| present-at-cap | **514** |
| raw-delayed (present-at-re-probe) | 0 |
| re-probes scheduled | 0 (no absent-at-cap candidate ever arose) |
| failed_journeys (operational skips) | 1 (journey-4 connection reset; excluded) |
| breaker events | 0 |
| read / write steps · write-path fraction | 515 / 515 · **0.5** |

**Result: 0 CONFIRMED wild flags in 514 acked writes across all 3 bound TT admin-basic endpoints** — the
pre-registered scarcity outcome on the MANDATORY sync-acked, nonzero-prior surface. Fully synchronous ⇒ not
even a raw-delayed (0 degradation-shaped cases; the §4 top-up shortfall the plan pre-registered as
NEAR-CERTAIN — C-2 — is fully materialized: OTel 1 raw-delayed, TeaStore 0, TT 0).

## Recall + carry-forward

- **Measured-recall (freeze pin 12):** TT is the fault-free natural surface here; its S1-positive recall
  exemplar is the **fabricated-ack** capture (`captures/tt-s1-cancel-fabricatedack`, 200 `{1,"Success."}`,
  balance 50 = lost, no marker) — a SYNTHETIC exemplar on forked source (memory arc). No new TT recall run
  is owed by S3; the real traced MIST discrimination run remains PRE-REGISTERED + owed at 2.5/E2 (unchanged
  by this window). Cross-referenced, not re-run.
- **Aggregate across all 3 SUT windows (feeds P4/P6):**

  | SUT | acked (denom a) | endpoints (denom b) | CONFIRMED | present | raw-delayed |
  |-----|-----|-----|-----|-----|-----|
  | OTel-Demo | 500 | 1 | 0 | 499 | 1 |
  | TeaStore | 500 | 1 | 0 | 500 | 0 |
  | TrainTicket | 514 | 3 | 0 | 514 | 0 |
  | **TOTAL** | **1514** | **5** | **0** | **1513** | **1** |

  Pre-committed zero-finds sentence (§0.5) instantiates at **N = 1514, K = 5**: rule-of-three 95% upper
  bound ≈ 3/1514 ≈ **0.20%** on the per-write CONFIRMED-flag rate under the pinned workload; no
  cross-population claim. |S3| = 0 ⇒ the degenerate-κ branch (rated set = calibration + M-yield; no S3
  precision row). This is the pre-registered CENTRAL expectation, not a null path.
- Next: scale TT to 0 → P4 (provenance-scoped dedup — trivial at 0 CONFIRMED → env-guard audit →
  scarcity branch invoked) → P5 assembly readiness → P6 RESULT + 3-cold review.
