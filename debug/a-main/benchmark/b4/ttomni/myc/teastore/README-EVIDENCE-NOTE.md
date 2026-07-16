# EVIDENCE NOTE — TeaStore seed dirs carry a STALE smoke-3 duplicate (post-review marker, 2026-07-16)

**Found by the RESULT 3-cold review (reviewer C, blocking; reviewer A independently).**

Every one of the 10 `s2026071*/allure-results/` directories in THIS folder contains, at its
TOP level, a stale **byte-identical** result JSON (`abc66bd4-…-result.json`, MD5
`12c05513dfa23aa5ff7ad788144f6e00`, `fullName=myc_teastore_smoke3.…`) that belongs to the
PRE-RUN smoke attempt 3 — **not** to the seed named by the directory.

**The GENUINE per-seed results live one level DEEPER, at
`s<seed>/allure-results/allure-results/*-result.json`** (distinct `fullName =
myc_teastore_s<seed>…`, distinct historyId, seed-matching timestamps). TeaStore is the ONLY
SUT with this nesting.

**Cause (traced from `driver-attempt1.log` + `driver.log`):** driver attempt 1 (18:24:24-47)
crashed near-instantly on every seed (PS 5.1 parse trap; blank `EXIT rc=`) and its
unconditional copy-out banked the stale `target/allure-results` (the smoke-3 leftover) into
each fresh seed dir; the fixed attempt 2 (18:26:10+) then `Copy-Item -Recurse`'d the REAL
results into the ALREADY-EXISTING destination, which PowerShell nests.

**Impact on reported numbers: none.** `CLUSTERING-myc.json` and the RESULT's per-leg table
reference ONLY the genuine nested files (verified independently by two reviewers; the
recount separated smoke-vs-seed by `fullName`).

Nothing was deleted (standing no-deletion rule) — consumers must use the NESTED files.
Record: `RESULT-myield-completion.md` disclosure 8(a).
