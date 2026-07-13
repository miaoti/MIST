# S3 P2 — TeaStore window RESULT (intermediate; feeds the P6 aggregate)

Plan `s3-wildhunt-plan.md` rev 2.1 §7 P2. MIST pin `mist_commit 5802fa8`.
Artifacts: `s3/window-teastore/{window-log.json, ledger.json, flags/}`.

## Environment + gates (GREEN)

- **Revive:** TeaStore scaled 0→1 (7 deployments, all Ready ~10s). teastore-db is EPHEMERAL
  (no PVC), but the persistence service **auto-seeded** on startup: 100 users (user1..user100 —
  the journey's user22..user61 range exists), 5 categories, products (id 42 present), 185 default
  orders. **No `generatedb` wipe needed** (the auto-seed populated a fresh empty DB). Login verified.
- **§0.4 async-bound:** TeaStore confirm is SYNCHRONOUS (webui blocks on the persistence REST create);
  the FP calibration's 20/20 immediate present-at-cap confirms no async lag ⇒ a sync-acked-but-absent
  order is genuine-eligible (like TT), no async completion bound needed/present. (See s3-p0-pins.md §4.)
- **Read-back:** `GET /rest/orders` (full collection, scope "" returns the whole growing table — B-R1;
  marker-membership is correct, non-colliding with the 185 seed orders). NEVER `GET /rest/generatedb`.
- **§3 FP calibration (20 benign):** 20/20 present-at-cap, 0 CONFIRMED (0/20 ≤ 1/20) → both bars PASS.

## Counted window (500 acked writes)

| metric | value |
|--------|-------|
| acked bound-triple writes (denom a) | **500** |
| bound endpoints (denom b) | 1 (`POST …/cartAction` confirm) |
| **CONFIRMED flags** | **0** |
| present-at-cap | **500** |
| raw-delayed | 0 |
| failed_journeys | 0 |
| breaker events | 0 |

**Result: 0 CONFIRMED wild flags in 500 acked writes on TeaStore order-confirm** — the pre-registered
scarcity outcome on the second SUT. Fully synchronous ⇒ not even a raw-delayed (0 degradation-shaped
cases; the §4 top-up shortfall the plan pre-registered — C-2 worst case — is materializing, handled
at P5 with the disclosed floor-30 branch).

## Recall + carry-forward

- **Measured-recall (freeze pin 12):** TeaStore's known S1-positive is the persistence maintenance-mask
  (permanent while ON). Observe-mode measured recall (mixed on/off schedule) is DEFERRED to the
  consolidated P5 recall pass; 2.75-A already MEASURED it FIRE 5/5 (paired mode). Low-risk injector
  (in-process maintenance toggle, no pod wedge), so a re-revived observe-mode leg is viable at P5.
- TeaStore contributes **0 CONFIRMED** to the scarcity numerator, **500** to denominator (a), and
  **0** top-up cases (sync ⇒ no benign degradation).
- Next: scale TeaStore to 0 → P3 TrainTicket (MANDATORY; the only sync-acked + nonzero-prior surface).
