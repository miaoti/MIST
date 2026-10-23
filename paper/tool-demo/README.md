# Building the MIST tool-demo paper

> **Active target as of 2026-10-13: SANER 2027 Tool Demo Track** (34th IEEE
> International Conference on Software Analysis, Evolution and Reengineering;
> Richmond, Virginia, USA, 9-12 March 2027). Paper deadline **Fri 23 Oct 2026
> AoE** (optional abstract Mon 19 Oct 2026); notification Tue 8 Dec 2026;
> camera-ready and author registration Fri 8 Jan 2027. The active paper file
> is `main_saner.tex`. The ISSTA 2026 version (`main_issta.tex`, acmart)
> is kept unchanged as an archived record.

## Files

| File | Venue | LaTeX class | Status |
|---|---|---|---|
| `main_saner.tex` | **SANER 2027 Tool Demo Track (active)** | `IEEEtran` (10pt, conference) | ported from `main_issta.tex` on 2026-10-13; body text identical to the ISSTA submission until the SANER revision starts |
| `main_issta.tex` | SPLASH/ISSTA 2026 Posters and Tool Demonstrations | `acmart` (sigconf,screen,review) | submitted June 2026; archived record, do not edit |
| `main.tex` | ICSME 2026 / ICSE 2027 fallback | `IEEEtran` (10pt, conference) | stale: predates the ISSTA submission and carries numbers the ISSTA review removed; do not build |
| `figures/architecture.tex`, `figures/trace_oracle.tex` | shared | TikZ | canonical figure sources for every variant; edit them once |
| `refs.bib` | shared | BibTeX | bibliography for every variant |

## SANER 2027 Tool Demo Track requirements (checked 2026-10-13)

Track page: <https://conf.researchr.org/track/saner-2027/saner-2027-tool-demo-track>

* Format: IEEE conference proceedings style, `\documentclass[10pt,conference]{IEEEtran}`
  (no `compsoc` option), PDF, English.
* Length: **at most 5 pages, references included**.
* Review: **single anonymous** (author names visible to the PC); at least
  three reviews. Do not anonymise the paper, the repository URL, or the
  artifact links.
* Required: a URL where the latest version of the tool can be downloaded or
  accessed (the GitHub repository).
* Optional: a 3-5 minute demo video on YouTube or the tool's website, with its
  URL in the abstract. The screencast recorded for ISSTA
  (<https://youtu.be/T2AePcbFT6E>) fits that window.
* Eligibility: the tool must not have been published before as a tool paper
  (MIST has not); commercial products are not accepted; the demo should
  stress the scientific contribution.
* Evaluation: fit with the SANER research-track topics; motivation and novelty
  against prior industrial or research tools; potential applications and
  usefulness; goals, requirements, architecture and internals. Empirical
  results or user feedback are encouraged; a large-scale evaluation is not
  required.
* Submission: <https://easychair.org/conferences/?conf=saner27tracks>
  ("Make a New Submission", then Other Tracks, then Tool Demo Track).

## Prerequisites

`main_saner.tex` needs `IEEEtran.cls` and `IEEEtran.bst` from
<https://www.ieee.org/conferences/publishing/templates.html> or CTAN
(<https://ctan.org/pkg/ieeetran>). Do **not** drop a stub `IEEEtran.cls` into
this directory: the local copy is searched before system-wide TeX paths, so a
stub masks the real class and the build fails at the first IEEEtran-specific
command. Overleaf, MiKTeX (with auto-install), and full TeX Live all ship
`IEEEtran.cls`; leave them to supply it. The monospace face is Latin Modern
Mono (`\renewcommand{\ttdefault}{lmtt}`), which the same distributions ship.

`main_issta.tex` needs ACM's `acmart` class and `ACM-Reference-Format.bst`
(<https://ctan.org/pkg/acmart>). With acmart v2.20 (MiKTeX, 2026-08) the
`\email{}` lines with underscores raise a "Missing $ inserted" error at
`\maketitle` and the emails render mangled; the PDF is still produced. Overleaf
(acmart v2.14, TeX Live 2025) builds it cleanly.

## Build

SANER variant:

```
pdflatex main_saner.tex && bibtex main_saner && pdflatex main_saner.tex && pdflatex main_saner.tex
pdfinfo main_saner.pdf | grep Pages      # must print 5 or fewer
```

Build of record 2026-10-13 (MiKTeX 25.12): 5 pages, references end at about
40% of the last page's right column, no errors, no overfull boxes.

ISSTA variant (archived):

```
pdflatex main_issta.tex && bibtex main_issta && pdflatex main_issta.tex && pdflatex main_issta.tex
```

## Overleaf

The paper is also maintained in an Overleaf project. Checked 2026-10-13 against
the export `ToolDemoMIST (4).pdf` (acmart v2.14, compiled 2026-10-13): the
Overleaf body text equals `main_issta.tex` word for word. The Overleaf copy of
`figures/trace_oracle.tex` differed in the verdict box: it carried a
`StatusPropagation` row ("passes (no learned per-API baseline for this SUT)")
and a reworded `Response-level` row ("entry is HTTP 200; a status-, schema-,
or body-only oracle records the run as success"). Both were folded into the
local figure on 2026-10-13, so the repository now matches what ISSTA reviewed
(the client-response callout keeps a comma where Overleaf had an em dash).
For SANER, upload `main_saner.tex` to the project and set it as the main
document; `figures/*.tex` and `refs.bib` are shared.

## ISSTA 2026 record

* Track: SPLASH/ISSTA 2026 Posters and Tool Demonstrations (deadline 26 June
  2026 AoE; 4 pages body plus 1 page references; `acmart[sigconf,screen,review]`
  with line numbers; single-blind; YouTube screencast mandatory). Submitted
  June 2026; rejected.
* The ISSTA to IEEEtran port (2026-10-13) changed only the preamble, the
  title and author block, the keyword block, the acmart `\Description` alt-text
  lines, the `figure*` placement option, the bibliography style, and Table I's
  font size (`\scriptsize`, 4pt column padding, to fit the narrower column);
  the body text is byte-identical. In `figures/trace_oracle.tex` the verdict
  box's text width went from 9.6 cm to 10 cm because Times bold is wider than
  acmart's Libertine and the first row touched the box border.
