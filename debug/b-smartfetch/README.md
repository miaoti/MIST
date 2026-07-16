# debug/b-smartfetch — SmartFetch standalone paper track (B-venue)

This folder is the working record for the **second paper track**: turning MIST's **Smart Fetch**
(smart input fetching) subsystem into a standalone conference paper (target tier: CCF-B / CORE A-B),
run in parallel with the A-venue main track in `debug/a-main/`.

SmartFetch is the protagonist: LLM-guided discovery of live read endpoints, live-value harvesting
from the running microservice system, persistent cross-run registry learning, and LLM fallback —
as implemented in `mist-core/src/main/java/io/mist/core/smart/` and documented in
`docs/Smart-Fetch-Process.md`.

## Contents

| Path | What it is |
|---|---|
| `PAPER-PLAN.md` | The paper plan of record (story, claims, RQs, experiment design, venue, timeline). Reviewer-gated: 3/3 cold-reviewer ACCEPT required before execution. |
| `research/codebase-inventory.md` | Evidence report: what SmartFetch actually is in code (classes, prompts, registries, config, existing measurements). |
| `research/related-work.md` | Online novelty scan: closest prior art, what SmartFetch can/cannot claim, expected baselines. |
| `research/venue-scan.md` | Venue candidates, deadlines, and the evaluation bar at comparable venues. |
| `REVIEW-*.md` | Cold-review + reconciliation files. **Local-only, gitignored** (same rule as `debug/a-main/`). |

## Ground rules (mirrors the a-main track)

- All repo changes on branch `main_track`; artifacts in English.
- Plan executes only after 3 independent cold reviewers ACCEPT (reviews are local-only).
- `FILE_INDEX.md` at repo root is updated in the same change as any file added/moved here.
