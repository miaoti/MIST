## §1 Rater-facing recruitment brief
**What this is.** A paid, short-term labeling study on open-source microservice benchmarks. You will
review a **capped set of recorded system behaviors** and judge, for each, whether the behavior is a
genuine correctness defect, an intentional/by-design behavior, or not decidable from the available
documentation.

**Who we're looking for.** Software engineers with microservice literacy: comfortable reading
OpenAPI/REST specs, synchronous REST + asynchronous messaging + eventual consistency, and reading
application source (Java and Go). A short screening task (§9) confirms fit.

**The task, per case.** You are given (a) the system and its exact version, (b) the sequence of API
requests performed, (c) the system's response(s), and (d) the resulting observed durable state. Using
ONLY the **provided, version-pinned** documentation, OpenAPI/spec, and source bundle for that system,
you assign one label (full definitions in §3):
- **genuine defect** — the system acknowledged success but a durable effect it promises did not occur,
 and "it should have persisted/propagated" is derivable from the provided bundle.
- **by-design / benign** — the provided bundle establishes the behavior is acceptable.
- **underspecified** — the intended behavior for what you observed is NOT derivable from the bundle.

**Sole source of truth.** For each case use ONLY the provided version-pinned docs/spec/source bundle for
that system. **Do not consult the upstream or live repository, web search, or any other version** — the
live code may differ from the pinned version each case is bound to, which would make labels
irreproducible. The pinned bundle is the sole source of the norm.

**Time + pay.** ~15–45 minutes per case. The set is capped at **≈ 90 cases**; at the stated pace that is
**~22–68 hours**. You are paid **`[USER DECISION U1 — RATE]`** on a **per-hour** basis for the estimated
hours **regardless of the labels you produce** — there is no "right answer" we are steering toward, and
compensation does not depend on which labels you record.

**What you may consult, and only that:** the provided bundle. We are measuring your independent judgment
from the bundle alone; do not consult tools, traces, other people, or the web.

---

