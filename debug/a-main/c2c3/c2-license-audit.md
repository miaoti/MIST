# C2 benchmark license audit (plan §2.4-1, review B M6) — 2026-07-08

Method: GitHub license API (verbatim SPDX) + root-content listing for the no-license row + Zenodo
record API + local repo inspection. **No BLOCKED items.** Two decisive findings:

1. **train-ticket-fault-replicate has NO license** (GitHub `license: null`; no LICENSE/COPYING/NOTICE
   in root; repo active). Their CODE (incl. the fault diffs baked into ts-* trees) is default
   all-rights-reserved — we may not copy/derive. Their FACTS (which fault, which service, observable
   behavior — F1–F22 descriptions, README, ts-fault.txt, the survey paper) are not copyrightable.
   **Disposition: replicate-by-description + cite** — re-implement each chosen fault from its
   description into our own MIST-trainticket fork (derived from the cleanly Apache-2.0
   FudanSELab/train-ticket), copying ZERO lines from their repo; cite repo + paper; the benchmark
   documents that the faults are independent re-implementations. (Same-org Apache-2.0 base does not
   extend an explicit grant to their modifications — re-implement-only is the defensible line.)
2. **miaoti/MIST is LGPL-3.0** (RESTest lineage; LICENSE at repo root, README:518-520) — additions
   inside it are derivative and cannot be relicensed. **The benchmark consumes MIST BY REFERENCE
   (commit hash / released jar), never vendoring its source.**

## Per-source dispositions
| source | SPDX | intended use | disposition | obligations |
|---|---|---|---|---|
| FudanSELab/train-ticket | Apache-2.0 | manifests + fork diff; images by digest | redistribute + reference-by-digest | §4: license copy, notices, change statements in modified files (fork diff carries modification notice) |
| FudanSELab/train-ticket-fault-replicate | **NONE** | replicate ≥6 of F1–F22 | **replicate-by-description + cite** (zero code copying) | cite repo + industry-survey paper; document independent re-implementation |
| microservices-demo (Sock Shop) | Apache-2.0 | manifests + weaveworksdemos images | redistribute + reference-by-digest | attribution + change notices |
| DescartesResearch/TeaStore | Apache-2.0 | manifests + OUR authored OpenAPI (`evaluation/suts/teastore/openapi/teastore-swagger.yaml`, E1 2026-07-14) | redistribute + attribution | authored-by-us CLEAN-ROOM from the v1.4.2 source (webui servlets + persistence JAX-RS) + our corpus specs; TeaStore ships NO upstream OpenAPI → zero copied; note authored-by-us in the spec `info.description` (done) |
| open-telemetry/opentelemetry-demo | Apache-2.0 | manifests + flagd fault configs as S1 cases + OUR authored OpenAPI (`evaluation/suts/oteldemo/openapi/oteldemo-swagger.yaml`, E1 2026-07-14) | redistribute + attribution | mark modified flag configs; the OpenAPI is authored-by-us CLEAN-ROOM from `pb/demo.proto` + the frontend `/api` gateway (OTel Demo is gRPC-native, ships NO upstream OpenAPI → zero copied); the async checkout read-back is documented as an SQL note, NOT a fabricated GET |
| GoogleCloudPlatform/microservices-demo (Boutique) | Apache-2.0 | manifests + image refs | redistribute + reference-by-digest | attribution |
| istio Bookinfo samples | Apache-2.0 (istio/istio LICENSE) | samples + our EnvoyFilter/VS derivatives | redistribute + attribution | change notices on derived manifests |
| Uber Tale-of-Errors Zenodo (10.5281/zenodo.13947828) | CC-BY-4.0 | CITE only | cite-only | normal citation (CC-BY would even permit redistribution w/ attribution) |
| our additions (harness in miaoti/MIST; benchmark manifests/contracts/labels) | MIST = **LGPL-3.0** | release the C2 artifact | **split-license release** (below) | MIST-repo code stays LGPL-3.0; standalone benchmark repo carries its own LICENSE + component map |
| infra images (kind/istio Apache-2.0; RabbitMQ MPL-2.0; MySQL GPL-2.0) | n/a for us | reference by tag/digest; users pull upstream | reference-only | NEVER mirror/re-push images to our registry (MySQL/GPL would trigger source-offer duties) — the default policy already guarantees this |

## Recommended release pairing (with the mandatory carve-out)
- **Benchmark artifact code** (standalone repo: authored manifests, triple YAMLs, glue/run scripts,
  executable contract YAMLs) → **Apache-2.0** (matches every upstream SUT; patent grant;
  AE-friendly).
- **Labels, ground truth, adjudication records, run outputs** → **CC-BY-4.0** (research-data
  standard; matches the Uber-artifact precedent).
- **MIST tool = LGPL-3.0 by reference** (commit hash / jar dependency) — does not impede AE badging;
  all three licenses are open.
- Artifact root ships a LICENSE/README **component map** (which paths are Apache-2.0 vs CC-BY-4.0;
  MIST-by-reference under LGPL-3.0; upstream-manifest provenance with change notices).

## Conduct rules distilled (binding for execution)
1. Zero lines copied from train-ticket-fault-replicate — implement from descriptions only.
2. Never re-push third-party images anywhere we control; reference-by-digest + build-from-source.
3. Fork diffs and modified upstream manifests carry change notices (Apache-2.0 §4).
4. The benchmark repo is a NEW standalone repo (not a subtree of miaoti/MIST) to keep the license
   split clean.

## OpenAPI spec provenance (E1, 2026-07-14) — HETEROGENEOUS, disclosed not laundered

The E1 wave authored the two MISSING specs (TeaStore, OTel-Demo). While cataloguing, the existing
`evaluation/suts/*/openapi/` set was found to be **heterogeneous** (NOT uniformly authored-by-us) — this is
disclosed here rather than laundered. `<none>` in-file means the upstream project is Apache-2.0 but the spec
file carries no `info.license` key.

| SUT | spec file | origin | authored-by-us? | in-file license |
|---|---|---|---|---|
| sockshop | `sockshop-swagger.yaml` | authored-by-us from the front-end route handlers (clean Swagger-2.0 exemplar) | YES | Apache 2.0 |
| teastore | `teastore-swagger.yaml` | **authored-by-us CLEAN-ROOM (E1)** from TeaStore v1.4.2 source (webui servlets + persistence JAX-RS) + our corpus specs; no upstream OpenAPI exists | YES | Apache 2.0 |
| oteldemo | `oteldemo-swagger.yaml` | **authored-by-us CLEAN-ROOM (E1)** from `pb/demo.proto` + the frontend `/api` gateway; OTel Demo is gRPC-native, no upstream OpenAPI exists | YES | Apache 2.0 |
| bookinfo | `bookinfo-swagger.yaml` | **STOCK UPSTREAM Istio sample spec** (`info.description` = "the API of the Istio BookInfo sample application", `termsOfService` istio.io) — upstream-derived, NOT authored-by-us | **NO (upstream)** | Apache 2.0 |
| boutique | `boutique-swagger.yaml` | authored-by-us from `frontend/handlers.go` (single HTTP frontend; other services gRPC) | YES | **`<none>`** (upstream GoogleCloudPlatform Apache-2.0) |
| trainticket | `merged_openapi_spec.yaml` | **machine-GENERATED** OpenAPI 3.0.3 (springdoc-style merge from the running Spring services; generator tags e.g. `basic-error-controller`) — NOT hand-authored | **NO (generated)** | Apache 2.0 |

Obligations: the two E1 specs (`teastore`, `oteldemo`) are clean-room derivatives citing upstream in
`externalDocs`, copying ZERO upstream OpenAPI text (neither project ships one). The `bookinfo` spec is an
upstream Istio artifact redistributed under its Apache-2.0 (attribution kept via its own `info`/`externalDocs`);
it is NOT claimed as authored-by-us. The `boutique` spec is authored-by-us but should gain an explicit
`info.license` block on any future touch (its upstream is Apache-2.0). The `trainticket` spec is a generated
document, not a clean-room authoring — cite it as generated when used as a comparator input.
