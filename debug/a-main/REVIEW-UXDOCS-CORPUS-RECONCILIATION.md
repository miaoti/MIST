# README-UX + corpus plan + checklist — 3-cold-review RECONCILIATION (2026-07-09)

**Reviews:** A README-onboarding (ACCEPT-W-CHANGES, 4 MAJOR/5 minor) · B corpus-dependencies
(ACCEPT-W-CHANGES, 1 BLOCKING/6 MAJOR) · C checklist-audit (ACCEPT-W-CHANGES, 2 BLOCKING/5 MAJOR/7
minor). All findings folded; dispositions:

| finding | fix | commit |
|---|---|---|
| A-1..A-9 (registry resolution rule, dead command, no authoring example, invisible intro/layout, 5 minors) | README + both demo properties | b256f29 |
| B-B1 seed subset hollow (specified/no artifacts; verdict-only logs; untyped raw_logs) | corpus plan REV 2: sidecar format = deliverable 0; seeds = migration + capture runs @pin vs deployed TT; freeze §6 sidecar row | 6f51924 |
| B-M1..M6 (wild-flag capture bundle; single-homing; STRIP-LIST; relative times; +4 gate checks; doc bundles + filing×blindness) | corpus plan §5/§6 rev 2 + checklist step-5 8-check gate | 6f51924 + this commit |
| C-B1 OTel-Demo path unpinned (compose = off-mesh) | checklist 2.3: PINNED = official otel helm demo chart + Kafka/accounting/fraud verify-rider | this commit |
| C-B2 cluster-state contradictions (2.1 already 26GB — editing kills the cluster; Bookinfo 0 pods; sockshop 0; TT holds the box) | 2.1→✔ w/ do-not-touch note; 2.5→REDEPLOY; footer fixed; NEW 2.15 tenancy-swap schedule (seed capture FIRST → TT converge/scale → 2.2/2.3; TT+OTel never co-resident) | this commit |
| C-M1 dangling SHA 1d6ed9b | → 1829a9e everywhere (real W0–W6 commit post-reset) | this commit |
| C-M2 dual-homed items | 2.75/step-5 → verify-pointers; §1.95 single home | this commit |
| C-M3 TraceAnomaly order backwards | 2.5.6 = normal-corpus FIRST; 2.5.7 = train-then-eval, 3a-gated | this commit |
| C-M4 TeaStore mesh-sever unverified under client-side LB | 2.2 rider (TT pod-IP precedent; EnvoyFilter fallback; min-3 floor hangs on it) | this commit |
| C-M5 seed artifacts absent | NEW 1.95.0 inventory-first + 1.95.05 sidecar | this commit |
| C-minors (fork tag, 138–172h, W5 wording, footer) | folded | this commit |

**Standing answer to the user (reviewer-verified):** README now carries the full user journey
(entry → command → registry example → report semantics → troubleshooting); the corpus order is
FACTORY-NOW (§1.95: inventory → sidecar → B4 → seed capture @pin vs the still-deployed TT) ∥
step 2, corpus BODY completes through steps 3a–5, raters start at the step-5 8-check gate.
**Execution begins at §1.95.0 (inventory), with the TT-live capture window protected by 2.15(a).**
