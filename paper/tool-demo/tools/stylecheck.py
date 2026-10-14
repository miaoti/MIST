#!/usr/bin/env python3
"""House-style scan of the tool-demo paper source. Reports; the judgement is a person's.

Flags: em dashes, ", verb+ing" participial clauses, "iff", negation-first "not A but B"
frames, contractions, semicolons (count and sites), "Figure"/"Section" spelled against the
IEEE convention used here ("Fig." in text), double spaces, a \\cite inside the abstract, and
every sentence that names two or more tools with fewer citation commands than names.

    python paper/tool-demo/tools/stylecheck.py paper/tool-demo/main_saner.tex
"""
import re
import sys

src = open(sys.argv[1], encoding="utf-8").read()
body = src[src.index("\\begin{abstract}"):src.index("\\bibliographystyle")]
body = re.sub(r"(?m)^%.*$", "", body)
body = re.sub(r"\\begin\{lstlisting\}.*?\\end\{lstlisting\}", "", body, flags=re.S)

TOOLS = ["EvoMaster", "RESTler", "RESTest", "RestTestGen", "Schemathesis", "Morest", "ARAT-RL",
         "AutoRestTest", "RESTGPT", "LlamaRestTest", "LogiAgent", "RESTifAI", "SATORI",
         "MACROHIVE", "Tracetest", "TraceRCA", "Nezha", "Jaeger", "OpenTelemetry",
         "TrainTicket", "Bookinfo", "Sock Shop", "Online Boutique"]


def report(title, hits):
    print(f"== {title}: {len(hits)}")
    for h in hits[:40]:
        print("   ", h)


# sentences (rough): split on period/question/exclamation followed by space + capital or newline
prose = re.sub(r"\s+", " ", body)
sentences = re.split(r"(?<=[.!?])\s+(?=[A-Z\\])", prose)

report("em dashes (--- or U+2014)", [m.group(0) for m in re.finditer(r".{0,30}(---|\u2014).{0,30}", prose)])
report(", verb+ing (participial clause; judge each)",
       [m.group(0) for m in re.finditer(r".{0,40},\s+\w+ing\b.{0,40}", prose)])
report("iff", [m.group(0) for m in re.finditer(r".{0,30}\biff\b.{0,30}", prose)])
report("negation-first frames (not ... but)",
       [m.group(0) for m in re.finditer(r".{0,30}\bnot\b[^.;]{0,60}\bbut\b.{0,30}", prose)])
report("contractions", [m.group(0) for m in re.finditer(r".{0,30}\b\w+n't\b.{0,30}|.{0,30}\b(it|that|there|what|who)'s\b.{0,30}", prose)])
report("semicolons", [m.group(0) for m in re.finditer(r".{0,50};.{0,50}", prose)])
report("'Figure'/'Section' spelled (IEEE text uses Fig.; Section is fine)",
       [m.group(0) for m in re.finditer(r".{0,20}\bFigure~?\\ref.{0,20}", prose)])
report("double spaces in source", [m.group(0) for m in re.finditer(r".{0,20}\S  +\S.{0,20}", body)])
abstract = re.search(r"\\begin\{abstract\}(.*?)\\end\{abstract\}", src, re.S).group(1)
report("\\cite inside the abstract", re.findall(r"\\cite\{[^}]*\}", abstract))
report("'black-box' / 'grey-box' uses", [m.group(0) for m in re.finditer(r".{0,40}\b(black|grey|gray|white)-box\b.{0,40}", prose)])

under = []
for s in sentences:
    names = [t for t in TOOLS if re.search(r"\b" + re.escape(t) + r"\b", s)]
    cites = len(re.findall(r"\\cite\{", s))
    if len(names) >= 2 and cites < len(names):
        under.append(f"[{len(names)} names, {cites} cites] " + s[:140])
report("sentences naming >=2 tools/systems with fewer cites than names (first mentions must cite; later mentions may not need to)", under)

# first mention of each tool: does that sentence carry a \cite?
print("== first body mention of each tool/system, and whether that sentence cites")
body_after_abstract = body[body.index("\\end{abstract}"):]
prose2 = re.sub(r"\s+", " ", body_after_abstract)
sents2 = re.split(r"(?<=[.!?])\s+(?=[A-Z\\])", prose2)
for t in TOOLS:
    for s in sents2:
        if re.search(r"\b" + re.escape(t) + r"\b", s):
            ok = "cite" if "\\cite{" in s else "NO CITE"
            print(f"   {t:16s} {ok:8s} {s[:90]}")
            break
