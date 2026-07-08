# E-item × SUT applicability matrix (plan v2 §4, review B m7) — 2026-07-08

**Purpose.** Pin, per SUT, which E-items and which C2 strata it contributes to — so "on the 6 SUTs"
never implies uniformity. **FINALIZED** against the §8.5-3 depth survey (`c2-depth-survey.md`), which
set the normative S1 quotas and the write-path classification for the four newer SUTs. Enters the
step-1 review wave with the rest of the freeze output.

## The matrix
| SUT | E1 tier | write path? | S1 (positives) | S2 (benign) | S3 (wild) | trace status (E2) | MIST enablement |
|---|---|---|---|---|---|---|---|
| **TrainTicket** | FULL (5×10×1h) | **YES — proven** (cancel→refund lost-comp; order create/pay) | **YES** — F-corpus subset + reviewed G1/G3 cases | YES — designed degradation | YES — authored scenario workload | dark on target paths → step-2.5 OTel | **enabled** (G1/G3 proven) |
| **Sock Shop** | FULL (5×10×1h) | **YES — proven** (carts `POST /cart`; shipping enqueue) | **YES** — broker-policy shipping cases proven | YES | YES (if workload write-fraction >0) | dark on target paths → step-2.5 OTel | **enabled** (shipping h2h + carts triple) |
| **TeaStore** | FULL (5×10×1h) | **YES — natural, survey-confirmed** (`placeorder` → registry-client status-swallow → 200 ORDERCONFIRMED; order-items partial variant) | **YES — quota 4–5** (maintenance-toggle / DB-down / mesh-sever ×2 legs; ≥4 mechanisms met w/o code-level) | YES — 3 paths (regen wipe; maintenance 503; recommender cold-start) | YES — write path + authored workload | **Kieker**, not OTel → converter-or-exclude decided at step 2.5 | needs enablement: author OpenAPI + auth glue + paired-run DoD |
| **OTel-Demo** | THIN (3×30min) | **YES — natural** (checkout→Kafka→accounting-Postgres async; checkout→EmptyCart) | **YES — quota 4–5** incl. the benchmark's **flagship async acked-but-lost** (Kafka publish swallowed; durable consumer-side psql read-back) | YES — 8 flag paths (kafkaQueueProblems = the pending-vs-missing trap) | YES — loadgen-driven; write-fraction reported | **native OTel** — best-instrumented → prime E2/arm-3 target | needs enablement: registry + auth + compose.full pin + psql probe |
| **Boutique** | THIN (3×30min) | **NO — below floor** (orders not persisted; 1 natural site = checkout→EmptyCart only) | **S1-minor only: quota 1, disclosed** (not a write-path SUT) | YES — 2 paths (ads, recommendations; already committed MIST assets) | NO (no write-path stratum) | OTel option available | needs enablement (light) |
| **Bookinfo** | THIN (3×30min) | **NO — read-only** (ratings POST = 501 on v2; ~4 GET paths, saturation disclosed) | **NO** | YES — 3 benign traps (reviews→ratings; productpage→reviews; productpage→details) | **NO** | **Istio+Jaeger already traced** → lowest-risk E2 smoke target | minimal (oracle-smoke only) |

## The non-uniformities this matrix exists to record
- **E1 tiers (frozen by plan §4, budget decision):** FULL = TT / TeaStore / SS; THIN = Bookinfo /
  Boutique / OTel-Demo.
- **Write-path (data-integrity) SUTs = TT + SS + TeaStore + OTel-Demo** (four). **Boutique and
  Bookinfo are NOT write-path SUTs** — the survey found Boutique persists no orders (1 natural site,
  below the ≥4-mechanism floor) and Bookinfo is read-only (0 sites). They contribute S2 benign traps +
  thin E1 saturation datapoints ONLY (Boutique + a single disclosed S1-minor case). This is the single
  most important non-uniformity — "on the 6 SUTs" must never imply six write-path SUTs.
- **Two of the four new/existing SUTs carry NATURAL masked writes:** TeaStore (survey's key finding —
  the second SUT after TT with a wholly natural in-tree swallow, no fork) and OTel-Demo (the async
  Kafka flagship). This upgrades the pre-registration line that called TeaStore "shallower CRUD."
- **Trace-instrumentation heterogeneity (E2-critical):** OTel-Demo native-OTel (best) · Bookinfo
  Istio+Jaeger (already traced, smoke target) · TT/SS OTel-dark on target paths (step-2.5 wave) ·
  **TeaStore Kieker (non-OTel)** → converter-or-exclude-from-trace-arms, decided at step 2.5. This is
  why E2 recall MUST be reported per trace-visibility class, not pooled.

## Floor binding (survey → frozen floors in `c2-freeze.md` §5)
- **≥6 acked-but-lost across write-path SUTs:** met with margin — the new SUTs alone yield 8–10 natural
  data-integrity cases (TeaStore 4 + OTel-Demo 4), before TT/SS's proven ≥4.
- **≥4 mechanisms per write-path SUT:** met on TT, SS, TeaStore, OTel-Demo; explicitly NOT met on
  Boutique/Bookinfo → correctly excluded from the write-path class rather than quietly under-filled.
- **S1 ≥ 45 headline floor:** TeaStore (4–5) + OTel-Demo (4–5) + Boutique (1) contribute ~9–11 new S1
  cases; TT (F-corpus ≥6 + G1/G3 reviewed cases) + SS (shipping/carts proven) carry the balance. The
  F-corpus floor (≥6, target ≥10) is the main remaining lever, per plan §2.3.
