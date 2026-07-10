# [INTERNAL] Eligibility screen — answer key (administrator only; never shown to a candidate)

Pass rule (§9): BOTH practice cases labeled correctly AND BOTH spec-reading questions correct.
Grade the ballots against this key; record pass/fail per candidate with the §11 screen outcome.

## Practice case SCREEN-G1 — correct label: **genuine**
A paid order is cancelled; the cancel returns HTTP 200 `{status:1,msg:"Success."}` (clean success
acknowledgement). The norm (bundle: `ts-cancel-service/.../CancelServiceImpl.java` — cancelOrder
calls drawback; `ts-inside-payment-service/.../InsidePaymentServiceImpl.java` — drawBack persists a
refund Money record) requires the buyer's balance to rise by the refund. The observed durable state
shows the acting user's balance UNCHANGED at 50.00 → an acknowledged write whose durable effect is
absent = genuine defect. Acceptable groundings: cancelOrder→drawback flow, drawBack's persist, or
the account-balance read-back contract. A `benign` or `underspecified` label = fail (the norm is
derivable from the bundle; the transcript presents the durable state).

## Practice case SCREEN-B1 — correct label: **benign**
The FIRST contact create succeeds (201, `{status:1,"Create contacts success"}`). The SECOND,
identical create returns 201 with `{status:0,msg:"Contacts already exists"}` and the read-back shows
exactly ONE contact. The norm (bundle: `ts-contacts-service/.../ContactsServiceImpl.java` — the
create path checks accountId+documentType+documentNumber and rejects duplicates with status 0)
makes this designed duplicate-rejection: the write was REFUSED (body says status:0), not silently
lost. Correct label: benign/by-design. A `genuine` label = fail (misreads a signalled rejection as a
lost write).

## Spec-reading Q1 — correct answer: **ts-user-service**
`UserServiceImpl.saveUser` (ts-user-service) persists the User entity via `userRepository.save(user)`
and then *notifies* ts-auth-service to create login credentials (a separate credential record).
System of record for the User entity = ts-user-service. Cite: `UserServiceImpl.saveUser`.
(Answering "ts-auth-service" = fail; the auth copy is a credential projection, not the entity of
record.)

## Spec-reading Q2 — correct answer: **No**
`TokenServiceImpl.getToken` (ts-auth-service) verifies the optional verification code (remote GET),
authenticates the username/password, READS the user (`userRepository.findByUsername`), and issues a
JWT. No repository save/update/delete occurs — nothing durable is created or modified. Any "yes"
answer that cites token issuance = fail (a JWT is returned to the caller, not persisted).
