# RESULT — PWS L4: Tracetest LIVE — INSTALL-BLOCKED (disclosed; surrogate cells stand)

**Date:** 2026-07-17 · Status: **INSTALL-BLOCKED-DISCLOSED** per the plan's stop rule
("Tracetest install fights >½ day ⇒ L4 closes as install-blocked-disclosed; the surrogate
cells stand, labels unchanged").

## What was attempted + where it blocked

- Helm repo add + `helm install tracetest tracetest/tracetest` (postgres bundled):
  the chart **deployed** cleanly.
- BLOCKER: the chart pins `docker.io/bitnami/postgresql:14.7.0-debian-11-r29`, which
  **no longer exists on Docker Hub** (`not found` — Bitnami removed old image tags under
  their 2024 legacy-repo policy). The tracetest server crash-looped on its missing DB
  dependency; postgres stayed `ImagePullBackOff`.
- A `bitnamilegacy/postgresql` image override via `helm upgrade --reuse-values --set`
  did NOT take (the postgres subchart's StatefulSet retained the pinned dead tag — the
  values path varies by chart version and needs a StatefulSet recreate). Resolving it =
  chart-values archaeology + a StatefulSet rebuild, AND the still-pending step
  (javaagent-instrumenting ts-cancel + ts-inside-payment = pod restarts during an active
  WSL-flap window, plus trace-store wiring) — together crossing the ½-day fight threshold
  the stop rule anticipates.

## Disposition (within the reviewed plan)

L4 closes **install-blocked-disclosed**. The E2 comparator table's Tracetest cells remain
the **offline SURROGATE** cells, already labeled verbatim "span-assertion-semantics
(surrogate; the live tool was NOT run)" — UNCHANGED. No cell was relabeled; nothing was
spun into a live result. The real Tracetest test specs stay AUTHORED-never-executed
(`b4/e2/tracetest-specs-authored.yaml`, the authoring-cost artifact).

## Why the paper does not lose here

The real-tool comparison the reviewers wanted is ALREADY delivered by the L1 MULTI-TOOL
arm (EvoMaster + Schemathesis, both real, both run to completion, both miss the masked
class for two complementary fundamental reasons). L4's marginal value was upgrading 5
flagship *presence* surrogate cells to real-tool cells — modest against L1's result and
not worth a cluster-wedge risk in a flap window. Situation-dependent: a future window on a
calmer cluster (or a Tracetest chart version with a live postgres image) can complete it.
