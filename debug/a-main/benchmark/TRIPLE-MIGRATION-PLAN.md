# Triple-file consolidation — reference audit + migration plan

**Created 2026-07-14.** The per-SUT read-back `target-triples*.yaml` files (the config that tells
MIST *"where to read the durable state back for this write"* — `readback_endpoint` + `isolation_key`
+ `mode`) are scattered across **three roots**, an artifact of the wave-by-wave build. This is the
reference audit + a consolidation proposal, so nothing breaks when they move.

> **STATUS: EXECUTED 2026-07-15** (user signed off on all 3 decision points). 7 triples `git mv`d to
> `evaluation/suts/<sut>/triples/`; 5 runner `-D` paths updated; **plus 7 MIST-tool-code refs the
> original audit MISSED** — 3 `TargetTripleRegistryTest` loads (helpers renamed `locate…G3Dir` →
> `locate…TriplesDir`) + 3 WildHunt default paths — fixed under explicit authorization. Verified:
> `TargetTripleRegistryTest` **27/27 green, BUILD SUCCESS**. The product-demo triple
> (`mist-cli/.../My-Example/`) was left in place per decision 2. The plan below is the as-executed record.

## 1. The triple files today (3 roots)

| SUT | file(s) | current root |
|---|---|---|
| TrainTicket cancel (3 variants) | `target-triples-{natural,constructed,agreement}.yaml` | `evaluation/suts/trainticket/g3/` |
| TrainTicket adminbasic (S3) | `trainticket-adminbasic-triples.yaml` | `debug/a-main/benchmark/b4/s3/` |
| SockShop shipping | `target-triple-shipping.yaml` | `evaluation/suts/sockshop/g3/` |
| TeaStore order | `teastore-order-triple.yaml` | `debug/a-main/benchmark/b4/enable/` |
| OTel checkout | `oteldemo-checkout-triple.yaml` | `debug/a-main/benchmark/b4/enable/` |
| TrainTicket demo (product built-in) | `target-triples.yaml`, `target-triples-demo.yaml` | `mist-cli/src/main/resources/My-Example/trainticket/` |

## 2. Who references them — the break-risk audit

**RUN-CRITICAL** (a move *without* updating these breaks a run): the shell runners pass the path as a
`-D` system property:

| runner | property → path |
|---|---|
| `b4/runners/s3/oteldemo.sh` | `-Ds3.triple=debug/a-main/benchmark/b4/enable/oteldemo-checkout-triple.yaml` |
| `b4/runners/s3/teastore.sh` | `-Ds3.triple=…/teastore-order-triple.yaml` |
| `b4/runners/s3/trainticket.sh` | `-Ds3.triple=…/trainticket-adminbasic-triples.yaml` |
| `b4/e2/e2-run.sh` | `-Dg3.triples.natural=evaluation/suts/trainticket/g3/target-triples-natural.yaml` (+ `.constructed`) |

**NOT run-critical:** the harness reads the path *from* the `-D` value, so the **Java needs no change**
— the property KEYS (`s3.triple`, `g3.triples.natural`, `otel.triple`, …) are stable; only the path
VALUE in the runners moves.

**DOC-ONLY** (a move leaves stale paths but breaks nothing): ~30 `.md` reference the triples for the
record (`RESULT-*`, `REVIEW-*`, `wave-*-plan`, `FILE_INDEX.md`, `README.md`). Historical snapshots.

## 3. Proposed target structure

Consolidate the **benchmark/eval** triples under the existing per-SUT `evaluation/suts/<sut>/` tree
(which already holds `input-fetch-registry.yaml` + `root-api-registry.json`):

```
evaluation/suts/trainticket/triples/   ← cancel-{natural,constructed,agreement} + adminbasic
evaluation/suts/sockshop/triples/      ← shipping
evaluation/suts/teastore/triples/      ← order       (new SUT dir)
evaluation/suts/oteldemo/triples/      ← checkout     (new SUT dir)
```

## 4. Decision points for the user (before any move)

1. **Target structure** — is `evaluation/suts/<sut>/triples/` right, or a different home?
2. **Product-demo triple** (`mist-cli/src/main/resources/My-Example/trainticket/target-triples*.yaml`)
   — **RECOMMEND LEAVE IN PLACE**: it is the product's built-in demo, likely loaded by a fixed
   classpath path at runtime; it is not benchmark scaffolding. Move only if we confirm nothing loads
   it by path.
3. **Doc-reference scope** — **RECOMMEND update only the run-critical runners + `FILE_INDEX.md`**;
   leave the ~30 historical `RESULT/plan` md at their original paths (they are dated snapshots; a
   mass-edit adds risk for no run benefit).

## 5. Execution steps (after sign-off)

1. `git mv` each triple (no deletion) to its `evaluation/suts/<sut>/triples/` home.
2. Update the 4 run-critical `-D` paths (3 s3 runners + `e2-run.sh`).
3. Update `FILE_INDEX.md`.
4. Smoke: path-existence check / dry-run one runner so the `-D` values resolve.

**Risk note:** teastore/oteldemo have no `evaluation/suts/` dir yet (triples only in `b4/enable/`) —
those dirs are created fresh. Historical `RESULT/plan` md keep the old paths (accepted; noted here).
