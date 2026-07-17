# F1 (async-refund-sequencing, LOST class) — B-m6 LIVE VERIFICATION: PASS (2026-07-17)

**Fault:** ts-cancel-service `asyncRefundSeqFaultMode=lost` (a MIST-benchmark fault-injection
addition on the fcorpus-build fork branch; DEFAULT off; the flagship ts-inside-payment
drawbackFaultMode/createAccountFaultMode BYTE-UNTOUCHED). Upstream-grounded: FudanSELab
industrial-fault survey F1 (async refund sequencing); f-corpus-spec eligible, OCCUPIED site
(cancel-refund) → a mechanism-variant CASE (case-run depth), NOT a new distinct site.

**Mechanism (implemented):** on cancel, the order-status reset lands but the refund
`drawbackMoney(...)` call is SKIPPED (status forced true) — the refund is a fire-and-forget
that never lands, while the cancel still acks `{status:1,"Success."}`. A genuine
ACKNOWLEDGED-BUT-LOST write.

## The paired live measurement (f1-legs.log; ground truth = direct /account balance reads)
| leg | mode | cancel ack | balance before | balance AFTER cancel |
|---|---|---|---|---|
| CONTROL | none | `{status:1,"Success."}` | 50.00 | **130.00** (refund +80 LANDED) |
| FAULT | lost | `{status:1,"Success."}` | 50.00 | **50.00** (refund LOST — never landed) |

⇒ Both legs ACK success identically; the durable refund lands in control, is LOST in fault.
The value-delta differential (control 50→130 vs fault 50→50) is EXACTLY the masked-2xx /
acknowledged-but-lost class MIST's paired read-back value-delta oracle fires on (same
/account read-back binding as the cancel-refund flagship). **F1 = a MIST-IN-SCOPE new case.**

## B-m6 disposition: PASS (in-class verified live). Toggle requires admin bearer auth
(the /test/ endpoint sits under the authenticated /cancelservice route — a driver fix, not a
fault problem). Reproduced across the control/fault pair. Ready for case authoring +
neutralized rater derivative.
