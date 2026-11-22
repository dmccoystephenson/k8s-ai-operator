"""Wraps each transcript in docs/screenshots/transcripts/*.txt in a terminal-style HTML page
(docs/screenshots/tools/pages/<name>.html) for darkroom to photograph. The text is copied as-is;
only colours are added (prompt lines, HTTP status, verdicts)."""
import html
import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parent
SRC = ROOT.parent / "transcripts"
OUT = ROOT / "pages"
TITLES = {
    "allowed": "Allowed: natural-language prompt to a get command",
    "blocked": "Blocked: a delete verb and a request with no request_id",
    "audit": "What the operator wrote to the stub DynamoDB and CloudWatch",
    "tests": "./mvnw clean package: test summary (timings and package prefix trimmed)",
}

RULES = [
    (r"^(\$ .*|    -d .*)$", "prompt"),
    (r"^HTTP 2\d\d$", "ok"),
    (r"^HTTP [45]\d\d$", "bad"),
    (r'"allowed": true|\{"BOOL": true\}|BUILD SUCCESS|Failures: 0, Errors: 0', "ok"),
    (r'"allowed": false|\{"BOOL": false\}|"reason": .*|block_reason .*|BUILD FAILURE', "bad"),
    (r"^(DynamoDB|CloudWatch) .*$", "head"),
]



def colour_line(line: str) -> str:
    for pattern, cls in RULES:
        if re.search(pattern, line):
            if cls in ("prompt", "head") or pattern.startswith("^HTTP"):
                return f'<span class="{cls}">{html.escape(line)}</span>'
            parts = re.split(f"({pattern})", line)
            return "".join(f'<span class="{cls}">{html.escape(p)}</span>' if re.fullmatch(pattern, p)
                           else html.escape(p) for p in parts if p)
    return html.escape(line)


PAGE = """<!doctype html><html><head><meta charset="utf-8"><title>{title}</title><style>
body {{ margin: 0; background: #0d1117; }}
.term {{ display: inline-block; margin: 0; background: #0d1117; color: #c9d1d9; min-width: 760px;
  font: 14px/1.45 "DejaVu Sans Mono", Menlo, Consolas, monospace; }}
.bar {{ background: #161b22; color: #8b949e; padding: 8px 14px; font-size: 13px; border-bottom: 1px solid #30363d; }}
.bar i {{ display: inline-block; width: 11px; height: 11px; border-radius: 50%; margin-right: 6px; background: #30363d; }}
pre {{ margin: 0; padding: 14px 18px 18px; font: inherit; white-space: pre; }}
.prompt {{ color: #79c0ff; }} .ok {{ color: #3fb950; }} .bad {{ color: #ff7b72; }} .head {{ color: #d2a8ff; }}
</style></head><body><div class="term"><div class="bar"><i></i><i></i><i></i> {title}</div><pre>{body}</pre></div></body></html>
"""

OUT.mkdir(exist_ok=True)
for src in sorted(SRC.glob("*.txt")):
    body = "\n".join(colour_line(l) for l in src.read_text().rstrip("\n").splitlines())
    title = html.escape(TITLES.get(src.stem, src.stem))
    (OUT / f"{src.stem}.html").write_text(PAGE.format(title=title, body=body))
    print(f"wrote {OUT / (src.stem + '.html')}")
