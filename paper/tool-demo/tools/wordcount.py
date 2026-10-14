#!/usr/bin/env python3
"""Approximate word count of the tool-demo paper body, per section.

Counts prose words between \\begin{abstract} and \\bibliographystyle, with comments, listings,
TikZ inputs and LaTeX commands stripped; captions and table cells stay. The figure is a rough
budget signal (about +/- 5 %), not a measurement.

    python paper/tool-demo/tools/wordcount.py paper/tool-demo/main_saner.tex
"""
import re
import sys

src = open(sys.argv[1], encoding="utf-8").read()
start = src.index("\\begin{abstract}")
end = src.index("\\bibliographystyle")
body = src[start:end]
body = re.sub(r"(?m)^%.*$", "", body)
body = re.sub(r"\\begin\{lstlisting\}.*?\\end\{lstlisting\}", "", body, flags=re.S)

# split into sections on \section
parts = re.split(r"(\\section\*?\{[^}]*\})", body)
chunks = [("Abstract", parts[0])]
for i in range(1, len(parts), 2):
    name = re.sub(r"\\section\*?\{([^}]*)\}", r"\1", parts[i])
    chunks.append((name, parts[i + 1]))


def count(text):
    t = re.sub(r"\\(cite|ref|label|S|input|resizebox|url|hspace|vspace)\*?\{[^}]*\}", "", text)
    t = re.sub(r"\\[A-Za-z]+\*?(\[[^\]]*\])?", "", t)
    t = re.sub(r"[{}~&]", " ", t)
    return len(re.findall(r"[A-Za-z0-9][A-Za-z0-9'._:/-]*", t))


total = 0
for name, text in chunks:
    n = count(text)
    total += n
    print(f"{n:5d}  {name}")
print(f"{total:5d}  TOTAL (abstract to end of body)")
