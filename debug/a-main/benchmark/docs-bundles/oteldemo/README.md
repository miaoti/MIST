# oteldemo — rater documentation bundle (LIGHT)

**Pinned version:** OpenTelemetry Demo app 2.2.0 / chart 0.40.9 — pinned.

**Sole source of truth for labelling cases on this system:** the files in THIS bundle —
`oteldemo-openapi.yaml` (the pinned API spec) + `BEHAVIOR.md` (per-endpoint durable-contract
notes). Do NOT consult the upstream repo, the live system, web search, or any other version.

Use BEHAVIOR.md to decide, for each observed outcome, whether a durable effect the endpoint
PROMISES is missing (→ genuine), is by-design/tolerated (→ benign), or is not derivable
from this bundle (→ underspecified).
