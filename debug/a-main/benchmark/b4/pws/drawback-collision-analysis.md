# drawBack collision analysis (PWS L2 pre-build conduct gate; orchestrator-side)

**Date:** 2026-07-16 · For the C-B1 / B-B1 fold: verify F-corpus faults that touch
`ts-inside-payment-service` do NOT collide with the fabricatedack injector before any build.

## Source finding (fork `MIST-trainticket` @ a1767ab3, read at recon)

`InsidePaymentServiceImpl` uses **per-fault `public static volatile String <name>FaultMode`
fields, DEFAULT "none"**, each toggled at RUNTIME by its own controller endpoint
(`setFaultMode` pattern) — NO `-D`/`@Value` channel. Existing fields:
- `drawbackFaultMode` ∈ {none, fail, fabricatedack} — the cancel-refund flagship.
- `createAccountFaultMode` ∈ {none, fabricatedack} — the createaccount agreement anchor.

## The invariant for L2 (pinned)

Any F-corpus fault implemented in this service (or any shared service) MUST:
1. add its OWN `static volatile` fault-mode field, DEFAULT "none" (never overload
   `drawbackFaultMode` / `createAccountFaultMode`);
2. add its OWN toggle endpoint (or a distinct mode value on a NEW field);
3. leave the flagship fields' code paths byte-untouched (the post-build fabricatedack
   REGRESSION — paired FIRE must still reproduce — enforces this).

## Occupied-site F-faults touching this constellation (from f-corpus-spec)

F1 / F13 land on the cancel-refund constellation (mechanism variants, floor-credit only,
NEVER new sites). Under the invariant above they get distinct fields/toggles — orthogonal
to `drawbackFaultMode`, so the flagship case's reproducibility is structurally protected.

**Implementer note:** the ISOLATED L2 implementer receives `f-corpus-spec.md` + the clean
base source only; THIS analysis is orchestrator-side conduct guidance — the implementer
independently chooses mechanics but the pre-build diff review (orchestrator) rejects any
diff that mutates the two flagship fields or their `drawBack`/`createAccount` paths.
