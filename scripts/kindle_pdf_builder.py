from __future__ import annotations

from html import escape
from pathlib import Path
import re
import shutil
import subprocess


SOURCE = Path(
    r"c:\Users\dhana\Downloads\Dr. Lakshmi Newton Talks on Sacred Relationships and Seven Reflections #PyramidValley #PMCValley-B9j3pYC7Z20-compact_size_speech.m4a (1).txt"
)
HTML_OUT = SOURCE.with_suffix(".kindle.html")
PDF_OUT = SOURCE.with_suffix(".kindle.pdf")


def is_heading(line: str) -> bool:
    if not line:
        return False
    if len(line) > 60:
        return False
    if line.endswith(":"):
        return False
    if any(char in line for char in ".,!?;"):
        return False
    return True


def classify_heading(line: str) -> str:
    if line == "Booklet Reading Edition":
        return "subtitle"
    if line in {"Main Talk", "Q&A", "Closing Exchange Before Q&A"}:
        return "section"
    if re.match(r"^The\s+.+\s+Mirror$", line):
        return "chapter"
    return "section"


def paragraphize(text: str) -> list[tuple[str, str]]:
    blocks: list[tuple[str, str]] = []
    current: list[str] = []

    for raw_line in text.splitlines():
        line = raw_line.strip()

        if not line:
            if current:
                blocks.append(("p", " ".join(current)))
                current = []
            continue

        if is_heading(line):
            if current:
                blocks.append(("p", " ".join(current)))
                current = []
            blocks.append((classify_heading(line), line))
            continue

        current.append(line)

    if current:
        blocks.append(("p", " ".join(current)))

    return blocks


def render_html(blocks: list[tuple[str, str]]) -> str:
    parts: list[str] = []

    for kind, content in blocks:
        safe = escape(content)
        safe = safe.replace("“", "&ldquo;").replace("”", "&rdquo;")

        if kind == "section":
            parts.append(f'<h2 class="section-title">{safe}</h2>')
        elif kind == "chapter":
            parts.append(f'<h3 class="chapter-title">{safe}</h3>')
        elif kind == "subtitle":
            parts.append(f'<p class="subtitle">{safe}</p>')
        else:
            if safe.startswith("Question:"):
                safe = safe.replace("Question:", '<span class="label">Question:</span>', 1)
                parts.append(f'<p class="qa">{safe}</p>')
            elif safe.startswith("Answer:"):
                safe = safe.replace("Answer:", '<span class="label">Answer:</span>', 1)
                parts.append(f'<p class="qa">{safe}</p>')
            else:
                parts.append(f"<p>{safe}</p>")

    title = escape(SOURCE.stem)
    return f"""<!doctype html>
<html lang=\"en\">
<head>
  <meta charset=\"utf-8\">
  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">
  <title>{title}</title>
  <style>
    @page {{
      size: 6in 9in;
      margin: 0.36in 0.18in 0.36in 0.18in;
    }}

    :root {{
      --paper: #f8f2e8;
      --ink: #2d251d;
      --muted: #7a6957;
      --rule: #d8c6b1;
      --accent: #8c5a2b;
    }}

    html,
    body {{
      margin: 0;
      background: var(--paper);
    }}

    body {{
      color: var(--ink);
      font-family: Georgia, "Palatino Linotype", "Book Antiqua", serif;
      font-size: 12.4pt;
      line-height: 1.72;
      text-rendering: optimizeLegibility;
      -webkit-font-smoothing: antialiased;
      hanging-punctuation: first last;
    }}

    main {{
      padding: 0;
      box-sizing: border-box;
    }}

    .title-block {{
      page-break-after: avoid;
      text-align: center;
      padding-top: 0.16in;
      padding-bottom: 0.9in;
    }}

    h1 {{
      font-size: 22pt;
      line-height: 1.2;
      margin: 0;
      font-weight: 700;
      letter-spacing: 0.01em;
    }}

    .subtitle {{
      margin: 0.7rem 0 0;
      color: var(--muted);
      font-size: 11pt;
      letter-spacing: 0.12em;
      text-transform: uppercase;
    }}

    .ornament {{
      margin: 1rem auto 0;
      width: 72px;
      height: 1px;
      background: linear-gradient(90deg, transparent, var(--accent), transparent);
    }}

    .section-title {{
      page-break-after: avoid;
      page-break-inside: avoid;
      margin: 2.1rem 0 1rem;
      padding-top: 0.2rem;
      font-size: 10.6pt;
      font-weight: 700;
      letter-spacing: 0.16em;
      text-transform: uppercase;
      color: var(--accent);
      border-top: 1px solid var(--rule);
    }}

    .chapter-title {{
      page-break-after: avoid;
      page-break-inside: avoid;
      margin: 2rem 0 0.8rem;
      font-size: 17pt;
      line-height: 1.25;
      font-weight: 700;
      color: var(--ink);
    }}

    p {{
      margin: 0 0 0.82rem;
      text-align: justify;
      text-indent: 1.3em;
      widows: 3;
      orphans: 3;
    }}

    .title-block + .section-title,
    .section-title + p,
    .chapter-title + p {{
      text-indent: 0;
    }}

    .qa {{
      text-indent: 0;
      margin-bottom: 0.9rem;
    }}

    .label {{
      font-weight: 700;
      color: var(--accent);
    }}

    .footer-note {{
      margin-top: 2rem;
      padding-top: 0.8rem;
      border-top: 1px solid var(--rule);
      color: var(--muted);
      font-size: 10.5pt;
      text-indent: 0;
      text-align: center;
    }}
  </style>
</head>
<body>
  <main>
    <section class=\"title-block\">
      <h1>Dr. Lakshmi Newton Talks on Sacred Relationships and Seven Reflections</h1>
      <p class=\"subtitle\">Kindle Reading Edition</p>
      <div class=\"ornament\"></div>
    </section>
    {''.join(parts[2:])}
    <p class=\"footer-note\">Formatted for relaxed long-form reading and PDF export.</p>
  </main>
</body>
</html>
"""


def find_chrome() -> str | None:
  candidates = [
    shutil.which("chrome"),
    shutil.which("chrome.exe"),
    r"C:\Program Files\Google\Chrome\Application\chrome.exe",
    r"C:\Program Files (x86)\Google\Chrome\Application\chrome.exe",
  ]

  for candidate in candidates:
    if candidate and Path(candidate).exists():
      return str(candidate)

  return None


def print_to_pdf(html_path: Path, pdf_path: Path) -> bool:
  chrome_path = find_chrome()
  if not chrome_path:
    print("Chrome not found. Skipped PDF generation.")
    return False

  command = [
    chrome_path,
    "--headless=new",
    "--disable-gpu",
    "--no-pdf-header-footer",
    f"--print-to-pdf={pdf_path}",
    html_path.resolve().as_uri(),
  ]

  try:
    subprocess.run(command, check=True, capture_output=True, text=True)
  except subprocess.CalledProcessError as error:
    stderr = error.stderr.strip()
    stdout = error.stdout.strip()
    details = stderr or stdout or "Unknown Chrome PDF export error."
    print(f"Failed to generate PDF: {details}")
    return False

  if not pdf_path.exists() or pdf_path.stat().st_size == 0:
    print("Chrome ran but no PDF was generated.")
    return False

  print(f"Wrote {pdf_path}")
  return True


def main() -> None:
  text = SOURCE.read_text(encoding="utf-8")
  blocks = paragraphize(text)
  html = render_html(blocks)
  HTML_OUT.write_text(html, encoding="utf-8")
  print(f"Wrote {HTML_OUT}")
  print_to_pdf(HTML_OUT, PDF_OUT)


if __name__ == "__main__":
    main()
