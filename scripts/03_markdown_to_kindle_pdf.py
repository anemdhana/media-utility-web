from __future__ import annotations

import argparse
from html import escape
import logging
from pathlib import Path
import re
import shutil
import subprocess
import tempfile


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Convert markdown to a Kindle-friendly PDF using Chrome headless print."
    )
    parser.add_argument("input", help="Path to markdown file")
    parser.add_argument(
        "--output",
        default="",
        help="Output PDF path. Default: <input>.reading.pdf",
    )
    return parser


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


def markdown_to_html_body(md_text: str) -> str:
    lines = md_text.splitlines()
    html_parts: list[str] = []
    paragraph_buffer: list[str] = []

    def flush_paragraph():
        if not paragraph_buffer:
            return
        text = " ".join(paragraph_buffer).strip()
        paragraph_buffer.clear()
        if text:
            html_parts.append(f"<p>{inline_format(text)}</p>")

    for raw in lines:
        line = raw.rstrip()
        stripped = line.strip()

        if not stripped:
            flush_paragraph()
            continue

        if stripped.startswith("### "):
            flush_paragraph()
            html_parts.append(f"<h3>{inline_format(stripped[4:].strip())}</h3>")
            continue
        if stripped.startswith("## "):
            flush_paragraph()
            html_parts.append(f"<h2>{inline_format(stripped[3:].strip())}</h2>")
            continue
        if stripped.startswith("# "):
            flush_paragraph()
            html_parts.append(f"<h1>{inline_format(stripped[2:].strip())}</h1>")
            continue

        paragraph_buffer.append(stripped)

    flush_paragraph()
    return "\n".join(html_parts)


def inline_format(text: str) -> str:
    safe = escape(text)
    safe = re.sub(r"\*\*(.+?)\*\*", r"<strong>\1</strong>", safe)
    safe = re.sub(r"_(.+?)_", r"<em>\1</em>", safe)
    return safe


def wrap_html(body: str, title: str) -> str:
    safe_title = escape(title)
    return f"""<!doctype html>
<html lang=\"en\">
<head>
  <meta charset=\"utf-8\" />
  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\" />
  <title>{safe_title}</title>
  <style>
    @page {{
      size: 6in 9in;
      margin: 0.42in 0.26in 0.42in 0.26in;
    }}

    :root {{
      --paper: #f7f1e7;
      --ink: #2f281f;
      --muted: #756553;
      --rule: #dac8b4;
      --accent: #8e5d31;
    }}

    html, body {{
      margin: 0;
      background: var(--paper);
    }}

    body {{
      color: var(--ink);
      font-family: Georgia, "Palatino Linotype", "Book Antiqua", serif;
      font-size: 12.2pt;
      line-height: 1.8;
      text-rendering: optimizeLegibility;
    }}

    main {{
      padding: 0;
    }}

    h1 {{
      margin: 0 0 1.2rem;
      font-size: 22pt;
      line-height: 1.22;
      color: var(--accent);
      page-break-after: avoid;
    }}

    h2 {{
      margin: 1.8rem 0 0.8rem;
      padding-top: 0.25rem;
      border-top: 1px solid var(--rule);
      color: var(--accent);
      text-transform: uppercase;
      letter-spacing: 0.12em;
      font-size: 10.5pt;
      page-break-after: avoid;
    }}

    h3 {{
      margin: 1rem 0 0.45rem;
      color: var(--muted);
      font-size: 11.2pt;
      page-break-after: avoid;
    }}

    p {{
      margin: 0 0 0.75rem;
      text-align: justify;
      widows: 3;
      orphans: 3;
    }}

    strong {{
      color: #6f4a26;
    }}

    em {{
      color: var(--muted);
    }}
  </style>
</head>
<body>
  <main>
{body}
  </main>
</body>
</html>
"""


def print_to_pdf(html_path: Path, pdf_path: Path) -> None:
    chrome_path = find_chrome()
    if not chrome_path:
        raise SystemExit("Chrome not found. Install Chrome to export PDF.")

  logger = logging.getLogger("step3")
  logger.info("Using Chrome binary: %s", chrome_path)

    cmd = [
        chrome_path,
        "--headless=new",
        "--disable-gpu",
        "--no-pdf-header-footer",
        f"--print-to-pdf={pdf_path}",
        html_path.resolve().as_uri(),
    ]

    try:
        subprocess.run(cmd, check=True, capture_output=True, text=True, encoding="utf-8", errors="replace")
    except subprocess.CalledProcessError as exc:
        details = (exc.stderr or "").strip() or (exc.stdout or "").strip() or "Chrome PDF export failed"
        raise SystemExit(details) from exc

    if not pdf_path.exists() or pdf_path.stat().st_size == 0:
        raise SystemExit("PDF generation failed: output file missing or empty.")


def main() -> int:
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s | %(levelname)s | %(message)s",
        datefmt="%H:%M:%S",
    )
    logger = logging.getLogger("step3")

    args = build_parser().parse_args()
    input_path = Path(args.input).expanduser()
    if not input_path.exists() or not input_path.is_file():
        raise SystemExit(f"Input markdown file not found: {input_path}")
    logger.info("[1/5] Input validated: %s", input_path)

    output_path = Path(args.output).expanduser() if args.output else input_path.with_suffix(".reading.pdf")
    logger.info("[2/5] Output path resolved: %s", output_path)

    logger.info("[3/5] Rendering markdown to HTML")
    md = input_path.read_text(encoding="utf-8-sig")
    body = markdown_to_html_body(md)
    title = input_path.stem
    html = wrap_html(body, title)

    logger.info("[4/5] Generating PDF with Chrome headless")
    with tempfile.TemporaryDirectory(prefix="md_pdf_") as temp_dir:
        html_path = Path(temp_dir) / "booklet.html"
        html_path.write_text(html, encoding="utf-8")
        print_to_pdf(html_path, output_path)

    logger.info("[5/5] PDF written: %s", output_path)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
