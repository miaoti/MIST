# DISCLOSURE — OTel-Demo + TeaStore tenants scaled to 0 for E2 RAM (2026-07-11)

**User-authorized** (2026-07-11, in response to the RAM-wall decision): scale the OTel-Demo and
TeaStore tenants to 0 to free WSL memory for the E2 TrainTicket revival.

## What was done

`kubectl -n otel-demo scale deploy --all --replicas=0` and `kubectl -n teastore scale deploy --all
--replicas=0` — both standing tenants scaled to 0 replicas.

## Why

The WSL2 VM backing the kind cluster has ~26 GB total. Reviving a **full** TrainTicket (45 services +
mysql×4 + nacos×3 + rabbitmq ≈ 53 pods, each a JVM) for the E2 run, **on top of** the OTel-Demo
(~21 pods) and TeaStore (~10 pods) tenants left running by wave 2.75-A, over-committed memory: WSL hit
**~88 MB available of 26 GB**, thrashed, and kubectl became unreliable (the WSL flap window would not
clear because the boot never finished). The three tenants physically do not fit at once. E2 needs
TrainTicket; it does not need OTel-Demo or TeaStore, so those were freed.

## Impact on the 2.75-A recorded end-state (the honest part — do not gloss over it)

Wave 2.75-A's RESULT-of-record (`wave-275a-result.md`, `tenancy-window-result.md`) recorded the
end-state as **"OTel-Demo UP + healthy, TeaStore UP."** **That end-state no longer holds** — as of
2026-07-11 both tenants are **DOWN (scaled to 0)**. That "leave the tenants running" step is therefore
**superseded / not maintained**; treat it as **incomplete** going forward, exactly as flagged.

**What is NOT affected:** the 2.75-A *measurements themselves* stand — they were fully captured,
committed, and independently archived before this teardown:
- MIST verdicts + reports: `b4/enable/{teastore,oteldemo}-checkout?-run.report.json` (FIRE 5/5 each).
- Independent ground truth: `b4/enable/ground-truth-{teastore,oteldemo}.txt`.
- Case cells + freeze §6 rows: committed (`9eff481`, `ba87306`, `93741dd`).

So the 2.75-A *results* are auditable regardless of tenant state; only the *"tenants still running"*
closing condition is reversed.

## Reversibility

Fully reversible: `kubectl -n otel-demo scale deploy --all --replicas=1` (and the TeaStore deploys to
their prior counts) brings them back — subject to the same RAM budget (they cannot run concurrently
with a full TrainTicket). If both OTel-Demo and a full TrainTicket are needed live at once, the WSL RAM
allocation must be raised first.

## Cross-references updated

`wave-275a-result.md` end-state section (dated pointer to this note); memory `c2c3-benchmark-arc.md`;
FILE_INDEX. This note is the disclosure of record for the teardown.
