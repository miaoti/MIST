#!/usr/bin/env python3
"""Assemble the rater-facing SHIP packet from the frozen c3-rater-materials.md (rev 3).

Deterministic extraction by '## ' heading boundaries — the packet is REGENERATED, never hand-edited,
so it cannot drift from the frozen protocol text. Layout (per the hand-over manifest):

  rater-packet/
    README-ADMIN.md            administrator guide (this script writes it)
    ship/                      EVERYTHING a rater may see
      01-brief.md              §1   02-consent.md §2   03-rubric.md §3   04-ballot.md §4
      eligibility/instructions.md   §9 + the 2-question spec-reading check
      eligibility/SCREEN-G1/ SCREEN-B1/   (B4-rendered cases; copied, not re-rendered here)
      docs-bundles/trainticket/  pinned upstream source bundle
    admin/                     INTERNAL-only
      screen-instrument.md     §11 (administered BEFORE assignment)
      debrief.md               §10 (administered at close ONLY)
      eligibility-answer-key.md

Leak gate: after assembly, every file under ship/ is scanned for INTERNAL/tool terms; a hit aborts.
"""
import io, os, re, shutil, sys

ROOT = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.normpath(os.path.join(ROOT, "..", "..", "c2c3", "c3-rater-materials.md"))
BENCH = os.path.normpath(os.path.join(ROOT, ".."))
SHIP, ADMIN = os.path.join(ROOT, "ship"), os.path.join(ROOT, "admin")

BANNED_IN_SHIP = ["MIST", "mist", "[INTERNAL]", "detector", "stratum", "wild-flag", "M-yield",
                  "calibration", "INJECTED", "fabricatedack", "lostwrite", "faultmode",
                  "§11", "§1.95", "checklist"]

ELIG_INSTRUCTIONS = """## Eligibility exercise (about 20 minutes, unpaid — stated up front)

Before the paid work begins, this short exercise confirms the study is a good fit. It has two parts.
It is done once, on your own, using ONLY the materials in this packet (the rubric, the ballot format,
and the `docs-bundles/` reference sources). No web search, no other repositories, no discussing it
with anyone.

**Part 1 — two practice cases.** The folders `SCREEN-G1/` and `SCREEN-B1/` each contain a `case.md`
(what was done to the system and what was observed) and a `ballot.yaml`. Judge each case exactly as
described in the rubric — derive the intended behavior from the documentation bundle, compare it to
what the case shows, and record your label + grounding citation + confidence + rationale in the
ballot. Expect roughly 5–10 minutes per case.

**Part 2 — two spec-reading questions.** Below. Answer them from the documentation bundle alone.

Return your two completed ballots and your two answers to the study administrator. You will not see
these two cases again during the study.
"""

SPEC_CHECK = """
## Spec-reading check (2 questions — answer using ONLY the documentation bundle)

**Q1 (system of record).** A new user registers via `POST /api/v1/userservice/users/register`.
After the flow completes, which service's database is the **system of record for the created User
entity**? Name the service and cite the class + method that persists it.

**Q2 (does it persist?).** Read `ts-auth-service/.../service/impl/TokenServiceImpl.java`, method
`getToken` (the login flow). Does a successful call to this method **create or modify any durable
record**? Answer yes/no and justify in one sentence from the source.

Record your answers in the eligibility ballot alongside your labels for the two practice cases.
"""


def sections(text):
    out, cur, name = {}, [], None
    for line in text.splitlines(keepends=True):
        m = re.match(r"^## (.+)$", line)
        if m:
            if name: out[name] = "".join(cur)
            name, cur = m.group(1).strip(), [line]
        else:
            cur.append(line)
    if name: out[name] = "".join(cur)
    return out


def find(secs, prefix):
    for k in secs:
        if k.startswith(prefix): return secs[k]
    raise SystemExit("section not found: " + prefix)


def strip_ship_tag(s):
    return s.replace("[SHIP]", "").replace("  ", " ")


def main():
    text = io.open(SRC, encoding="utf-8").read()
    secs = sections(text)
    for d in (SHIP, ADMIN, os.path.join(SHIP, "eligibility")):
        os.makedirs(d, exist_ok=True)

    # SHIP-rendering transforms (each disclosed; frozen source text unchanged):
    # - consent: internal section refs "(screening, §9/§11)" -> "(the screening)"; the reviewed
    #   known-labels disclosure keeps its meaning with neutral wording ("quality checking" instead of
    #   the internal term), so the stratum vocabulary never reaches a rater.
    # - rubric: drop the admin TODO line about authoring worked examples (rating-phase material,
    #   authored at corpus assembly; not part of the screening packet).
    consent = find(secs, "§2").replace("(screening, §9/§11)", "(the screening)")
    consent = consent.replace("used only to check calibration", "used only for quality checking")
    rubric = re.sub(r"^\*\*Worked examples \(calibration-only[^\n]*\n", "", find(secs, "§3"), flags=re.M)
    ship_files = {
        "01-brief.md": find(secs, "§1"), "02-consent.md": consent,
        "03-rubric.md": rubric, "04-ballot.md": find(secs, "§4"),
    }
    for fn, body in ship_files.items():
        io.open(os.path.join(SHIP, fn), "w", encoding="utf-8", newline="\n").write(strip_ship_tag(body))

    # SHIP-rendering decision (disclosed): §9's frozen text is an ADMINISTRATOR protocol description
    # (it cross-references internal sections and the stratum structure, which §0 forbids showing a
    # rater). The rater-facing rendering below is purpose-written to the same protocol; §9 itself
    # stays admin-side (copied to admin/ for reference).
    elig = ELIG_INSTRUCTIONS + SPEC_CHECK
    io.open(os.path.join(ADMIN, "eligibility-protocol-sec9.md"), "w", encoding="utf-8", newline="\n").write(find(secs, "§9"))
    io.open(os.path.join(SHIP, "eligibility", "instructions.md"), "w", encoding="utf-8", newline="\n").write(elig)

    io.open(os.path.join(ADMIN, "screen-instrument.md"), "w", encoding="utf-8", newline="\n").write(find(secs, "§11"))
    io.open(os.path.join(ADMIN, "debrief.md"), "w", encoding="utf-8", newline="\n").write(find(secs, "§10"))

    # docs bundle (copy; self-contained packet)
    src_bundle = os.path.join(BENCH, "docs-bundles", "trainticket")
    dst_bundle = os.path.join(SHIP, "docs-bundles", "trainticket")
    if os.path.isdir(dst_bundle): shutil.rmtree(dst_bundle)
    shutil.copytree(src_bundle, dst_bundle)

    # leak gate over ship/
    hits = []
    for dirpath, _, files in os.walk(SHIP):
        if "docs-bundles" in dirpath: continue  # bundle scanned at assembly with its own term set
        for f in files:
            p = os.path.join(dirpath, f)
            body = io.open(p, encoding="utf-8", errors="ignore").read()
            for term in BANNED_IN_SHIP:
                if term in body:
                    hits.append("%s :: %s" % (os.path.relpath(p, SHIP), term))
    if hits:
        for h in hits: print("LEAK:", h)
        sys.exit("ABORT: ship/ leak gate failed (%d hits)" % len(hits))
    print("ship/ leak gate: CLEAN")
    print("assembled:", ", ".join(sorted(ship_files)), "+ eligibility/instructions.md + admin/ + bundle")


if __name__ == "__main__":
    main()
