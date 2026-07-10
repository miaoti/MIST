## §4 The per-case ballot (what a rater records)
```yaml
rater_id: <your assigned opaque rater code>     # required — the analysis joins ballots by this
rubric_version: 3                  # copy from the rubric packet header (§3)
case_id: <opaque id — you cannot decode any grouping from it>
label: genuine | benign | underspecified
ack_carries_failure_sentinel: yes | no        # MECHANICAL, not a judgment: does the success
                           # response body carry a failure marker (-1,
                           # {1,"error"}, a negative id)? read it off the body.
grounding:            # REQUIRED for genuine/benign
 citation: <doc-url+version | spec-path+operation | source-file:symbol — INSIDE the provided bundle>
 quote_or_ref: <the clause/signature that grounds the label>
missing_norm: <underspecified ONLY: state exactly what the docs/spec/source do not say>
confidence: high | medium | low # used ONLY in a sensitivity analysis (labels excluded if low); never primary
rationale: <2–4 sentences: what was promised, what was observed, why the label follows>
time_minutes: <int>
```
Submit each ballot via the channel named in your assignment email (separate return; do not share).

---

