# TrainTicket documentation bundle — pinned reference (rater-facing)

**System:** TrainTicket (FudanSELab), an open-source railway-ticketing microservice benchmark.
**Source of record:** `github.com/FudanSELab/train-ticket` @ commit
`5526e505be15a5232f558e0d3699abb1b90beeb2` (the release-1.0.0 lineage matching the deployed
`codewisdom/*:1.0.0` images). Every file below is an UNMODIFIED extraction from that public commit.

**Contents** (Java sources, `src/main/java` per service, + the project README):
`ts-common` (shared entities) · `ts-user-service` · `ts-auth-service` · `ts-order-service` ·
`ts-cancel-service` · `ts-inside-payment-service` · `ts-contacts-service` ·
`ts-admin-route-service` · `ts-admin-basic-info-service` · `ts-route-service` — 149 Java files.

**How to use (raters):** For each case, derive the *intended behavior* (the norm) ONLY from this
bundle — controllers show the HTTP endpoints, `*ServiceImpl` classes show what each operation is
supposed to do (what it persists, what it returns), `ts-common` entities show the data model. Judge
the case's observed transcript against that norm per the rubric. **Use only this bundle** — no web
search, no other repositories, no chat tools (per your brief).

**Navigation hints:** an endpoint like `POST /api/v1/contactservice/contacts` maps to
`ts-contacts-service/.../controller/ContactsController.java` → the called method in
`.../service/ContactsServiceImpl.java`. Cross-service calls appear as `restTemplate.exchange(...)`
with the target service's URL in the caller's ServiceImpl.

**Assembly attestation (administrator):** extracted 2026-07-10 via `git archive <commit> <paths>`
from the commit above; the extracted tree was mechanically scanned and contains no tooling,
instrumentation, or fault-injection content (scan terms retained in the internal assembly record).
Bundle version: `tt-bundle-1` — cases reference the bundle by this directory; any change is a new
versioned bundle, never an edit in place.
