## §3 The rubric packet `rubric_version: 3`
**Three-way label {genuine, benign, underspecified}.**
- **genuine defect** — the system **acknowledged the operation** (returned HTTP 2xx or otherwise
 signaled success) while a **durable write it promises did not land** — or a **downstream write in its
 causal closure did not land** (causal closure = any write the implementation performs as part of
 fulfilling this operation, per the provided source), **where this case presents the relevant durable
 state** — AND the intended "it should have persisted/propagated" **is derivable** from the provided
 docs/spec/source. *(The response body may look like success or may carry an error marker — either
 way, if the durable effect is missing and was contract-required, it is genuine. Record the body's
 marker mechanically in the ballot; it does not change the label.)*
- **benign** — the observed degradation is by-design / lived-with per the provided docs/spec/source.
- **underspecified** — the intended behavior is NOT derivable from the provided docs/spec/source.

**Asynchronous write paths (tie-break — apply before choosing genuine).** If the write path is
asynchronous (a `202 Accepted`, a queue, an "async"/eventually-consistent worker), judge the observed
absence against any **completion bound derivable from the bundle**: absence **past a documented bound →
genuine**; if **no** completion bound is derivable from docs/spec/source (so you cannot establish the
absence is permanent) → **underspecified**, not genuine. Each case gives the **relative** timing of the
observation, so you can compare it to any documented bound.

**Partial writes.** A write where the source states the operation is atomic but only part landed
(parent saved, child lost) → **genuine**. A partial write where neither docs nor source state whether
the operation is atomic → **underspecified**.

**If the case does not present the durable state a clause would need** (e.g. it references a downstream
write whose state is not shown), you cannot evaluate that clause → **underspecified**, never a guessed
genuine.

**Admissibility (observation-vs-norm split — R5):**
- **Admissible AS the OBSERVATION to be judged:** the case's own presented material — the request
 sequence, the response(s), and the observed durable state.
- **Sole source of the NORM (what SHOULD have happened):** the provided pinned docs, OpenAPI/spec, and
 source code — nothing else.
- **Inadmissible:** distributed traces, any tool/oracle output, the live/upstream repository or any
 other version, and any runtime behavior beyond what the case presents.

labeling — the abstract patterns below do not by themselves cover the hard async/partial shapes):**
- *genuine* — POST returns 201 with an order id; GET on that id 404s and no row exists in the service
 whose OpenAPI schema lists it as the system of record. (Contract-grounded "should persist".)
- *genuine, error-marked ack* — cancel returns HTTP 200 with body `{1,"error"}`; the source shows the
 cancel must issue a refund write, and no refund row exists. Acked (2xx), durable write missing,
 contract-required → genuine. (Record `ack_carries_failure_sentinel: yes`.)
- *benign* — a write returns `202 Accepted` and the durable effect appears only after an async worker
 cycle the docs bound to a stated window; observed absence within that documented window is by-design.
- *underspecified, async* — same async shape but the docs state no completion bound; absence at a finite
 time is not derivably permanent → underspecified.
- *underspecified, atomicity* — a partial write where neither docs nor source state whether the
 operation is atomic; the intended post-state cannot be derived.

---

