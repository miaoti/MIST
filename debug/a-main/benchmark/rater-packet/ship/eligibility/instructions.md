## Eligibility exercise (about 20 minutes, unpaid — stated up front)

Before the paid work begins, this short exercise confirms the study is a good fit. It has two parts.
It is done once, on your own, using ONLY the materials in this packet (the rubric, the ballot format,
and the `docs-bundles/` reference sources). No web search, no other repositories, no discussing it
with anyone.

**Part 1 — two practice cases.** The folders `SCREEN-1/` and `SCREEN-2/` each contain a `case.md`
(what was done to the system and what was observed) and a `ballot.yaml`. Judge each case exactly as
described in the rubric — derive the intended behavior from the documentation bundle, compare it to
what the case shows, and fill in the `ballot.yaml` (same fields as a study ballot). Expect roughly
5–10 minutes per case.

**Part 2 — two spec-reading questions.** Below. Answer them from the documentation bundle alone, and
record each answer in `spec-answers.yaml`.

Return your two completed practice ballots (`SCREEN-1/ballot.yaml`, `SCREEN-2/ballot.yaml`) and
`spec-answers.yaml` to the study administrator. You will not see these two practice cases again
during the study.

## Spec-reading check (2 questions — answer using ONLY the documentation bundle)

**Q1 (system of record).** A new user registers via `POST /api/v1/userservice/users/register`.
After the flow completes, which service's database is the **system of record for the created User
entity**? Name the service and cite the class + method that persists it.

**Q2 (does it persist?).** Read `ts-auth-service/.../service/impl/TokenServiceImpl.java`, method
`getToken` (the login flow). Does a successful call to this method **create or modify any durable
record**? Answer yes/no and justify in one sentence from the source.

Record each answer in `spec-answers.yaml` (the fields are provided there).
