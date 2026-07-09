# E-item × SUT applicability matrix (plan v2 §4, review B m7) — 2026-07-08

**Purpose.** Pin, per SUT, which E-items and which C2 strata it contributes to — so "on the 6 SUTs"
never implies uniformity. Finalized against the §8.5-3 depth survey (`c2-depth-survey.md`); **rev 2 —
re-derived against the step-1 review (R1 mechanism enum, R3 honest recount, B-M5 trace-visibility split,
C-A2 tell-free axis; aligned with `c2-freeze.md` rev 2 §5).**

## The matrix
| SUT | E1 tier | write path? | S1 (positives) | S2 (benign) | S3 (wild) | trace status (E2) | MIST enablement |
|---|---|---|---|---|---|---|---|
| **TrainTicket** | FULL (5×10×1h) | **YES — proven** (cancel→refund lost-comp; order create/pay) | **YES** — F-corpus subset + reviewed G1/G3 cases | YES — designed degradation | YES — authored scenario workload | dark on target paths → step-2.5 OTel | **enabled** (G1/G3 proven) |
| **Sock Shop** | FULL (5×10×1h) | **YES — proven** (carts `POST /cart`; shipping enqueue) | **YES** — broker-policy shipping cases proven | YES | YES (if workload write-fraction >0) | dark on target paths → step-2.5 OTel | **enabled** (shipping h2h + carts triple) |
| **TeaStore** | FULL (5×10×1h) | **YES — natural, survey-confirmed** (`placeorder` → registry-client status-swallow → clean 200 ORDERCONFIRMED, **tell-free** per A's source check; order-items partial variant) | **YES — 3 mechanisms** {flag(maintenance-toggle), dependency-down(DB-down), mesh-sever} — **broker-less min 3 met (R1)**; mesh-sever spans 2 SITES (order-row + order-items); +code-level spare optional → **2–3 distinct sites, 4–6 case-runs** | YES — 3 paths (regen wipe; maintenance 503; recommender cold-start) | YES — write path + authored workload | **Kieker** → `trace-uninstrumented` (NOT by-construction); converter-or-exclude at step 2.5 | needs enablement: author OpenAPI + auth glue + paired-run DoD |
| **OTel-Demo** | THIN (3×30min) | **YES — natural** (checkout→Kafka→accounting-Postgres async; checkout→EmptyCart) | **YES — 3 real mechanisms** {broker-policy, mesh-sever (incl. method-scoped, same mechanism), flag(cartFailure)}; a code-level spare must be BUILT to reach ≥4 else lean on the cross-SUT floor. **2 distinct sites** (Kafka-loss, EmptyCart), **~4 case-runs**; incl. the **flagship async** (Kafka publish swallowed; durable consumer-side psql read-back = `readback.modality: sql-probe`, `mist_bindable` TBD). Construction-vs-contract seam disclosed (A-M8) | YES — 8 flag paths (kafkaQueueProblems = the pending-vs-missing trap) | YES — loadgen-driven; write-fraction reported | **native OTel** — best-instrumented → prime E2/arm-3 target | needs enablement: registry + auth + compose.full pin + psql probe |
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

- **Ack-content visibility (C-A2 / R8):** the natural masked writes split by whether the 2xx ack carries
  a machine-readable tell. **Tell-free (`success-shaped-clean`) natural exhibits:** TeaStore order-confirm
  (A-verified: the `-1` is cleared from the blob → clean 200) and SS swallowed-enqueue (trace-only, no
  durable read-back). **Tell-bearing** (segregated from the primary discriminating denominator):
  TT-natural `{1,"error"}` (a detection TIE in `g3-result.md`) and TeaStore's internal-CRUD `-1`-body
  tier (survey-capped). The clean oracle-wins concentrate in the tell-free + constructed cases — this is
  now a MEASURED axis, not an asserted one.

## Floor binding (survey → frozen floors in `c2-freeze.md` §5 rev 2 — honest two-denominator recount, R3)
- **Diversity floor (R1, re-worded):** BINDING floor = **≥6 acked-but-lost DISTINCT DEFECT SITES across
  the write-path SUTs** (not mechanism-multiplexed variants). Distinct sites: TeaStore 2 (order-row,
  order-items) + OTel-Demo 2 (Kafka-loss, EmptyCart) + TT ~4–6 (cancel→refund, adminroute, adminbasic/
  contacts, F-corpus in-class subset) + SS ~2–3 (shipping-enqueue, carts) ≈ **10–13 distinct sites** →
  ≥6 met with margin. Per-SUT ≥4 mechanisms "as applicable": TeaStore 3 (broker-less min met); OTel-Demo
  3 real (+build a code-level spare or lean on the cross-SUT floor); TT/SS ≥4. Boutique/Bookinfo excluded.
- **S1 floor on TWO denominators (C-A4 anti-padding):** *distinct defect sites* ≈ **15–18** (add Boutique's
  1 S1-minor) → **~21–28 with F-corpus at target 10** (each F-replication in-class-verified — B-m6);
  *mechanism-variant case-runs* ≈ **37–45**. The ≥45 target is a CASE-RUN count, honestly labeled; the
  distinct-site count is reported alongside as the anti-padding denominator. If distinct sites < 20, that
  is a disclosed finding — **this is a real risk surfaced now, not a step-3 surprise** (F-corpus is the
  main lever but is only a ~4-case swing; the honest S1-distinct-site count may land in the low-to-mid 20s).
- **S2 ≥ 35:** survey gives 16 across the 4 new SUTs + 2 packaged corpora (≤2 cases each); **TT/SS
  designed-degradation paths must be enumerated at step 2** and disclosed if short (not hand-waved).
- **Tell-free floor (R8):** ≥N S1 positives jointly natural + `success-shaped-clean` +
  `trace-invisible-by-construction`. Current natural tell-free exhibits = TeaStore order-confirm + SS
  swallowed-enqueue; if N is small, that IS the honest finding (and strengthens the "prevailing
  methodology filters this out" positioning).
