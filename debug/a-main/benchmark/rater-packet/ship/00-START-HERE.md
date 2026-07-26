# START HERE — rater packet

Thank you for taking part. This is a paid, short-term labelling study: for each case you are shown a
short sequence of API requests made against one open-source microservice system, the system's
response(s), and the durable state observed afterward. Your job is to decide, using ONLY the
reference material provided in this packet, whether the outcome is a **genuine defect**, a
**by-design / benign** behavior, or **underspecified** (not decidable from the material given). You
are not debugging or fixing anything — only labelling what you are shown, against the documentation
provided as the reference.

Work through this packet in this order:

1. **[`01-brief.md`](01-brief.md)** — what the task is, who it's for, timing, and pay.
2. **[`02-consent.md`](02-consent.md)** — the consent form (your administrator confirms the
   compensation details before you sign).
3. **[`03-rubric.md`](03-rubric.md)** — the labelling rubric (genuine / benign / underspecified) plus
   worked examples. This is the core of the task; read it carefully, more than once.
4. **[`04-ballot.md`](04-ballot.md)** — the exact fields you record for every case (label, grounding
   citation, confidence, rationale, time spent).
5. **[`docs-bundles/`](docs-bundles/)** — the ONLY reference sources you may use. One folder per
   system (`bookinfo/`, `oteldemo/`, `sockshop/`, `teastore/`, `trainticket/`); each case tells you
   which single system's bundle applies. Skim each system's `README.md` / `BEHAVIOR.md` now, so you
   know where to look once you're assigned cases.
6. **[`eligibility/instructions.md`](eligibility/instructions.md)** — a short, unpaid exercise that
   confirms fit before the paid work begins: two practice cases
   (`eligibility/SCREEN-1/`, `eligibility/SCREEN-2/`) labelled the same way, plus two spec-reading
   questions answered in `eligibility/spec-answers.yaml`. Return both practice ballots and the
   spec-answers sheet to your administrator — you will not see these two cases again.

After you pass the eligibility exercise and are assigned, you will receive the rating cases as
additional `case.md` + `ballot.yaml` folders in this same format (same as the practice cases) —
roughly 18 cases, about 15–45 minutes each; your administrator states the exact set, pace, and total
in your assignment email.

Use ONLY the materials in this packet for every judgment: no web search, no other repositories, no
outside tools, and no discussing cases with anyone (including other raters) until the study closes.
