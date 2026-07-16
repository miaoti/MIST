# MEMO — A3(iii-a): `sockshop-shipping-swallowed-enqueue-001` keep-vs-exclude (decision = USER at the Step-5 seal)

**Class of record (MANIFEST-r2):** `trace-required-not-blind-rateable` — the case has NO
rendered black-box discriminator: its read-back is non-discriminating BY DESIGN
(`order.shipment` written at order creation regardless of the enqueue; queue-master
persists nothing — the A5 census's structural adjudication), and the only genuine
discriminator is the missing consumer span in the trace.

- **Branch (a) EXCLUDE from blind rating (the E1+R2 default).** Cost: −1 rateable genuine
  (supply is already short: 3 ok-positives). Gain: cross-case SHAPE UNIFORMITY — no case
  ships white-box trace evidence, so "has trace evidence" cannot become a label decoder
  (tell-audit surface). The case remains a full BENCHMARK positive (ground truth from
  source inspection; the read-back oracle's applicability-boundary exemplar — its paper
  value is in §Scope, not in the rater study).
- **Branch (b) INCLUDE with disclosed white-box trace evidence.** Gain: +1 genuine rating
  unit. Cost: the ONLY case with trace evidence in the packet = a single-case evidence-class
  tell (decodable direction), plus a rubric amendment (an evidence-class disclosure) and a
  tell-audit re-run. The R1c/R1d discipline (no decodable single-case features) argues
  against.

**Recommendation: (a) EXCLUDE** — uniformity beats one rating unit; the calibration
shortfall (see the A3(iv) rehearsal) is not curable by 1 unit anyway. Sealed sets
untouched by this memo.
