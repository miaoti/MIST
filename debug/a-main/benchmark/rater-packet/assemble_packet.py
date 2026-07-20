#!/usr/bin/env python3
"""Assemble the rater-facing SHIP packet from the frozen c3-rater-materials.md (rev 3).

Deterministic extraction by '## ' heading boundaries — the packet is REGENERATED, never hand-edited,
so it cannot drift from the frozen protocol text. Layout (per the hand-over manifest):

  rater-packet/
    README-ADMIN.md            administrator guide (hand-maintained; NOT written by this script)
    ship/                      EVERYTHING a rater may see
      01-brief.md              §1   02-consent.md §2   03-rubric.md §3   04-ballot.md §4
      eligibility/instructions.md   §9 + the 2-question spec-reading check
      eligibility/spec-answers.yaml   fillable sheet for the 2 spec-reading answers
      eligibility/SCREEN-1/ SCREEN-2/   (B4-rendered cases; copied, not re-rendered here)
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
                  "§11", "§1.95", "checklist",
                  # readiness-review C5 hardening: tokens verified absent, banned to self-defend re-gen
                  "oracle", "read-back", "readback", "corpus", "meshsever", "SCREEN-G", "SCREEN-B",
                  "single-key", "street_address", "moneytype"]

START_HERE = """# START HERE — rater packet

Thank you for taking part. Work through this packet in order:

1. **`01-brief.md`** — what the task is and what you are being asked to do.
2. **`02-consent.md`** — the consent form (your administrator confirms the compensation details).
3. **`03-rubric.md`** — the labelling rubric (genuine / benign / underspecified). This is the core of
   the task; read it carefully. **`04-ballot.md`** shows the exact fields you record for each case.
4. **`docs-bundles/`** — the ONLY reference sources you may use, one folder per system. Each case uses
   exactly one system's bundle.
5. **`eligibility/`** — do the short eligibility exercise (`instructions.md`): two practice cases
   (`SCREEN-1/`, `SCREEN-2/`) and two spec-reading questions (`spec-answers.yaml`). Return the two
   practice ballots + `spec-answers.yaml` to your administrator.

After you pass the eligibility exercise and are assigned, you will receive the rating cases — same
`case.md` + `ballot.yaml` format as the practice cases. Use ONLY the materials in this packet: no web
search, no other repositories, no discussing cases with anyone.
"""

ELIG_INSTRUCTIONS = """## Eligibility exercise (about 20 minutes, unpaid — stated up front)

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
"""

SPEC_CHECK = """
## Spec-reading check (2 questions — answer using ONLY the documentation bundle)

**Q1 (system of record).** A new user registers via `POST /api/v1/userservice/users/register`.
After the flow completes, which service's database is the **system of record for the created User
entity**? Name the service and cite the class + method that persists it.

**Q2 (does it persist?).** Read `ts-auth-service/.../service/impl/TokenServiceImpl.java`, method
`getToken` (the login flow). Does a successful call to this method **create or modify any durable
record**? Answer yes/no and justify in one sentence from the source.

Record each answer in `spec-answers.yaml` (the fields are provided there).
"""

SPEC_ANSWERS_SHEET = """# Spec-reading answers (eligibility Part 2) — fill in and return with your two practice ballots.
q1_system_of_record:            # name the service that is the system of record for the User entity
q1_citation:                    # class + method that persists it (inside the provided bundle)
q2_persists_durable_record:     # yes | no — does TokenServiceImpl.getToken create/modify a durable record?
q2_justification:               # one sentence from the source
time_minutes:                   # integer
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
    # - rubric: the worked-examples heading spans two source lines; its admin TODO clause (authoring
    #   plan, internal vocabulary) is replaced by a plain caveat heading — the caveat text and the
    #   abstract example patterns are kept verbatim.
    consent = find(secs, "§2").replace("(screening, §9/§11)", "(the screening)")
    consent = consent.replace("used only to check calibration", "used only for quality checking")
    rubric, n = re.subn(
        r"^\*\*Worked examples \(calibration-only[^\n]*\n[^\n]*\):\*\*\n",
        "**Worked examples (the abstract patterns below do not by themselves cover the hard"
        " async/partial shapes):**\n",
        find(secs, "§3"), flags=re.M)
    if n != 1:
        raise SystemExit("rubric worked-examples heading transform did not apply (matched %d)" % n)
    ship_files = {
        "01-brief.md": find(secs, "§1"), "02-consent.md": consent,
        "03-rubric.md": rubric, "04-ballot.md": find(secs, "§4"),
    }
    for fn, body in ship_files.items():
        io.open(os.path.join(SHIP, fn), "w", encoding="utf-8", newline="\n").write(strip_ship_tag(body))
    io.open(os.path.join(SHIP, "00-START-HERE.md"), "w", encoding="utf-8", newline="\n").write(START_HERE)

    # SHIP-rendering decision (disclosed): §9's frozen text is an ADMINISTRATOR protocol description
    # (it cross-references internal sections and the stratum structure, which §0 forbids showing a
    # rater). The rater-facing rendering below is purpose-written to the same protocol; §9 itself
    # stays admin-side (copied to admin/ for reference).
    elig = ELIG_INSTRUCTIONS + SPEC_CHECK
    io.open(os.path.join(ADMIN, "eligibility-protocol-sec9.md"), "w", encoding="utf-8", newline="\n").write(find(secs, "§9"))
    io.open(os.path.join(SHIP, "eligibility", "instructions.md"), "w", encoding="utf-8", newline="\n").write(elig)
    io.open(os.path.join(SHIP, "eligibility", "spec-answers.yaml"), "w", encoding="utf-8", newline="\n").write(SPEC_ANSWERS_SHEET)

    io.open(os.path.join(ADMIN, "screen-instrument.md"), "w", encoding="utf-8", newline="\n").write(find(secs, "§11"))
    io.open(os.path.join(ADMIN, "debrief.md"), "w", encoding="utf-8", newline="\n").write(find(secs, "§10"))

    # docs bundles (copy ALL per-SUT bundles; self-contained packet). R1 fix: was trainticket-only.
    src_root = os.path.join(BENCH, "docs-bundles")
    for _sut in sorted(os.listdir(src_root)):
        _sp = os.path.join(src_root, _sut)
        if not os.path.isdir(_sp): continue
        _dp = os.path.join(SHIP, "docs-bundles", _sut)
        if os.path.isdir(_dp): shutil.rmtree(_dp)
        shutil.copytree(_sp, _dp)

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
