from __future__ import annotations

"""03_markdown_to_kindle_pdf.py — Convert markdown to Kindle-friendly ebook PDF.

Modes:
  Single file:   python scripts/03_markdown_to_kindle_pdf.py input.md
  Ebook (dir):   python scripts/03_markdown_to_kindle_pdf.py --dir <folder> --lang telugu
                 python scripts/03_markdown_to_kindle_pdf.py --dir <folder> --lang english

The --dir mode collects all *_kindle.md (Telugu) or *_kindle_english.md (English)
files, sorts by episode number, and generates a consolidated ebook with:
  - Cover page with thumbnail
  - Table of contents
  - Chapter pages with page-break separation
  - Page numbers in footer
  - Embedded images (base64)

Dependencies: None beyond stdlib + Chrome for PDF export.
"""

import argparse
import base64
import logging
from html import escape
from pathlib import Path
import re
import shutil
import subprocess
import tempfile

logger = logging.getLogger("ebook")


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(
        description="Convert markdown to a Kindle-friendly PDF using Chrome headless print.",
    )
    p.add_argument("input", nargs="?", default="", help="Single markdown file path")
    p.add_argument("--output", default="", help="Output PDF path")
    p.add_argument(
        "--dir",
        default="",
        help="Directory containing kindle markdown files (ebook mode)",
    )
    p.add_argument(
        "--lang",
        choices=["telugu", "english"],
        default="telugu",
        help="Language variant to compile (ebook mode). Default: telugu",
    )
    return p


# ---------------------------------------------------------------------------
# Chrome PDF
# ---------------------------------------------------------------------------

def find_chrome() -> str | None:
    for c in [
        shutil.which("chrome"),
        shutil.which("chrome.exe"),
        r"C:\Program Files\Google\Chrome\Application\chrome.exe",
        r"C:\Program Files (x86)\Google\Chrome\Application\chrome.exe",
    ]:
        if c and Path(c).exists():
            return str(c)
    return None


def print_to_pdf(html_path: Path, pdf_path: Path) -> None:
    chrome = find_chrome()
    if not chrome:
        raise SystemExit("Chrome not found. Install Chrome to export PDF.")
    logger.info("Using Chrome: %s", chrome)
    cmd = [
        chrome,
        "--headless=new",
        "--disable-gpu",
        "--no-pdf-header-footer",
        f"--print-to-pdf={pdf_path}",
        html_path.resolve().as_uri(),
    ]
    try:
        subprocess.run(
            cmd, check=True, capture_output=True,
            text=True, encoding="utf-8", errors="replace",
        )
    except subprocess.CalledProcessError as exc:
        detail = (exc.stderr or "").strip() or (exc.stdout or "").strip() or "Chrome PDF export failed"
        raise SystemExit(detail) from exc
    if not pdf_path.exists() or pdf_path.stat().st_size == 0:
        raise SystemExit("PDF generation failed: output file missing or empty.")


# ---------------------------------------------------------------------------
# Image embedding
# ---------------------------------------------------------------------------

def embed_image_base64(img_path: Path) -> str:
    """Return a data-URI for the given image file."""
    suffix = img_path.suffix.lower()
    mime = {"jpg": "image/jpeg", "jpeg": "image/jpeg", "png": "image/png",
            "gif": "image/gif", "webp": "image/webp"}.get(suffix.lstrip("."), "image/jpeg")
    data = base64.b64encode(img_path.read_bytes()).decode("ascii")
    return f"data:{mime};base64,{data}"


# ---------------------------------------------------------------------------
# Markdown → HTML  (full parser)
# ---------------------------------------------------------------------------

_RE_BOLD = re.compile(r"\*\*(.+?)\*\*")
_RE_ITALIC_STAR = re.compile(r"(?<!\*)\*([^*]+?)\*(?!\*)")
_RE_ITALIC_UNDER = re.compile(r"(?<![_\w])_([^_]+?)_(?![_\w])")
_RE_CODE = re.compile(r"`([^`]+?)`")
_RE_IMG_LINK = re.compile(r"\[!\[([^\]]*)\]\(([^)]+)\)\]\(([^)]+)\)")  # [![alt](src)](href)
_RE_IMG = re.compile(r"!\[([^\]]*)\]\(([^)]+)\)")
_RE_LINK = re.compile(r"\[([^\]]+)\]\(([^)]+)\)")


def inline_format(text: str, images_dir: Path | None = None) -> str:
    """Convert inline markdown to HTML. Embeds images as base64 if images_dir given."""
    safe = escape(text)

    # Image-links:  [![alt](src)](href)
    def _img_link_repl(m):
        alt, src, href = m.group(1), m.group(2), m.group(3)
        src_html = _resolve_image_src(src, images_dir)
        return f'<a href="{escape(href)}"><img src="{src_html}" alt="{escape(alt)}" class="thumb" /></a>'

    safe = _RE_IMG_LINK.sub(_img_link_repl, safe)

    # Standalone images:  ![alt](src)
    def _img_repl(m):
        alt, src = m.group(1), m.group(2)
        src_html = _resolve_image_src(src, images_dir)
        return f'<img src="{src_html}" alt="{escape(alt)}" class="thumb" />'

    safe = _RE_IMG.sub(_img_repl, safe)

    # Links:  [text](href)
    safe = _RE_LINK.sub(r'<a href="\2">\1</a>', safe)

    # Bold / italic / code
    safe = _RE_BOLD.sub(r"<strong>\1</strong>", safe)
    safe = _RE_ITALIC_STAR.sub(r"<em>\1</em>", safe)
    safe = _RE_ITALIC_UNDER.sub(r"<em>\1</em>", safe)
    safe = _RE_CODE.sub(r"<code>\1</code>", safe)

    return safe


def _resolve_image_src(src: str, images_dir: Path | None) -> str:
    """Resolve image src: embed as base64 if local file exists, else keep URL."""
    if images_dir and not src.startswith(("http://", "https://", "data:")):
        img_path = images_dir / src
        if not img_path.exists():
            img_path = images_dir.parent / src
        if img_path.exists():
            return embed_image_base64(img_path)
    return escape(src)


def markdown_to_html_body(md_text: str, images_dir: Path | None = None) -> str:
    """Parse markdown text into HTML body content."""
    lines = md_text.splitlines()
    html: list[str] = []
    para: list[str] = []
    in_table = False
    in_list = False
    in_blockquote = False
    bq_lines: list[str] = []

    def flush_para():
        nonlocal in_list
        if para:
            text = " ".join(para).strip()
            para.clear()
            if text:
                html.append(f"<p>{inline_format(text, images_dir)}</p>")
        if in_list:
            html.append("</ul>")
            in_list = False

    def flush_blockquote():
        nonlocal in_blockquote
        if bq_lines:
            inner = " ".join(bq_lines).strip()
            bq_lines.clear()
            if inner:
                html.append(f'<blockquote><p>{inline_format(inner, images_dir)}</p></blockquote>')
        in_blockquote = False

    def flush_table():
        nonlocal in_table
        if in_table:
            html.append("</tbody></table>")
            in_table = False

    for raw in lines:
        line = raw.rstrip()
        stripped = line.strip()

        # --- Blank line ---
        if not stripped:
            flush_para()
            flush_blockquote()
            flush_table()
            continue

        # --- Horizontal rule ---
        if re.match(r"^-{3,}\s*$", stripped):
            flush_para()
            flush_blockquote()
            flush_table()
            html.append("<hr />")
            continue

        # --- Headings ---
        heading_matched = False
        for level in (4, 3, 2, 1):
            prefix = "#" * level + " "
            if stripped.startswith(prefix):
                flush_para()
                flush_blockquote()
                flush_table()
                content = stripped[len(prefix):].strip()
                html.append(f"<h{level}>{inline_format(content, images_dir)}</h{level}>")
                heading_matched = True
                break

        if heading_matched:
            continue

        # --- Blockquote ---
        if stripped.startswith("> ") or stripped == ">":
            flush_para()
            flush_table()
            in_blockquote = True
            bq_lines.append(stripped[2:] if stripped.startswith("> ") else "")
            continue
        elif in_blockquote:
            flush_blockquote()

        # --- Table ---
        if stripped.startswith("|") and stripped.endswith("|"):
            flush_para()
            flush_blockquote()
            cells = [c.strip() for c in stripped.split("|")[1:-1]]
            # Skip separator rows like |---|---|
            if all(re.match(r"^[-:]+$", c) for c in cells):
                continue
            if not in_table:
                html.append('<table><thead><tr>')
                for c in cells:
                    html.append(f"<th>{inline_format(c, images_dir)}</th>")
                html.append("</tr></thead><tbody>")
                in_table = True
            else:
                html.append("<tr>")
                for c in cells:
                    html.append(f"<td>{inline_format(c, images_dir)}</td>")
                html.append("</tr>")
            continue

        if in_table and not stripped.startswith("|"):
            flush_table()

        # --- Unordered list ---
        if re.match(r"^[-*]\s+", stripped):
            flush_blockquote()
            flush_table()
            if para:
                text = " ".join(para).strip()
                para.clear()
                if text:
                    html.append(f"<p>{inline_format(text, images_dir)}</p>")
            if not in_list:
                html.append("<ul>")
                in_list = True
            content = re.sub(r"^[-*]\s+", "", stripped)
            html.append(f"<li>{inline_format(content, images_dir)}</li>")
            continue

        # --- Ordered list ---
        if re.match(r"^\d+\.\s+", stripped):
            flush_blockquote()
            flush_table()
            if para:
                text = " ".join(para).strip()
                para.clear()
                if text:
                    html.append(f"<p>{inline_format(text, images_dir)}</p>")
            if not in_list:
                html.append("<ul>")
                in_list = True
            content = re.sub(r"^\d+\.\s+", "", stripped)
            html.append(f"<li>{inline_format(content, images_dir)}</li>")
            continue

        # Close list if non-list line follows
        if in_list:
            html.append("</ul>")
            in_list = False

        # --- Image-only line ---
        if stripped.startswith("[![") or stripped.startswith("!["):
            flush_para()
            flush_table()
            html.append(f'<div class="img-block">{inline_format(stripped, images_dir)}</div>')
            continue

        # --- Regular paragraph text ---
        para.append(stripped)

    flush_para()
    flush_blockquote()
    flush_table()
    return "\n".join(html)


# ---------------------------------------------------------------------------
# HTML templates
# ---------------------------------------------------------------------------

EBOOK_CSS = """
    @page {
      size: 6in 9in;
      margin: 0.5in 0.35in 0.6in 0.35in;
      @bottom-center {
        content: counter(page);
        font-family: Georgia, serif;
        font-size: 9pt;
        color: #756553;
      }
    }

    :root {
      --paper: #f7f1e7;
      --ink: #2f281f;
      --muted: #756553;
      --rule: #dac8b4;
      --accent: #8e5d31;
    }

    html, body { margin: 0; padding: 0; background: var(--paper); }

    body {
      color: var(--ink);
      font-family: Georgia, "Palatino Linotype", "Book Antiqua", "Noto Sans Telugu", "Noto Serif Telugu", serif;
      font-size: 11.5pt;
      line-height: 1.72;
      text-rendering: optimizeLegibility;
    }

    /* --- Cover page --- */
    .cover {
      page-break-after: always;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      min-height: 100vh;
      text-align: center;
      padding: 1in 0.5in;
    }
    .cover h1 {
      font-size: 26pt;
      color: var(--accent);
      margin: 0.3in 0 0.15in;
      line-height: 1.3;
    }
    .cover h2 {
      font-size: 14pt;
      color: var(--muted);
      border: none;
      text-transform: none;
      letter-spacing: 0;
      margin: 0.1in 0;
    }
    .cover .subtitle {
      font-size: 11pt;
      color: var(--muted);
      margin-top: 0.25in;
    }
    .cover img {
      max-width: 80%;
      border-radius: 6px;
      margin: 0.2in 0;
    }

    /* --- TOC --- */
    .toc {
      page-break-after: always;
      padding-top: 0.3in;
    }
    .toc h2 {
      font-size: 16pt;
      color: var(--accent);
      border: none;
      text-transform: none;
      letter-spacing: 0;
      margin-bottom: 0.3in;
    }
    .toc ul {
      list-style: none;
      padding: 0;
      margin: 0;
    }
    .toc li {
      padding: 0.12in 0;
      border-bottom: 1px dotted var(--rule);
      font-size: 11pt;
    }
    .toc li .ep-num {
      color: var(--accent);
      font-weight: bold;
      margin-right: 0.1in;
    }

    /* --- Chapter --- */
    .chapter {
      page-break-before: always;
    }

    h1 {
      margin: 0 0 0.8rem;
      font-size: 20pt;
      line-height: 1.3;
      color: var(--accent);
      page-break-after: avoid;
    }
    h2 {
      margin: 1.4rem 0 0.6rem;
      padding-top: 0.2rem;
      border-top: 1px solid var(--rule);
      color: var(--accent);
      text-transform: uppercase;
      letter-spacing: 0.1em;
      font-size: 10pt;
      page-break-after: avoid;
    }
    h3 {
      margin: 0.8rem 0 0.35rem;
      color: var(--muted);
      font-size: 10.5pt;
      page-break-after: avoid;
    }
    h4 {
      margin: 0.6rem 0 0.25rem;
      color: var(--muted);
      font-size: 10pt;
      font-style: italic;
      page-break-after: avoid;
    }
    p {
      margin: 0 0 0.55rem;
      text-align: justify;
      widows: 3;
      orphans: 3;
    }
    strong { color: #6f4a26; }
    em { color: var(--muted); }
    code {
      background: #ede4d4;
      padding: 1px 4px;
      border-radius: 3px;
      font-size: 0.92em;
    }

    blockquote {
      margin: 0.6rem 0;
      padding: 0.35rem 0.6rem;
      border-left: 3px solid var(--accent);
      background: rgba(142,93,49,0.06);
      font-style: italic;
      color: var(--muted);
    }
    blockquote p { margin: 0; }

    hr {
      border: none;
      border-top: 1px solid var(--rule);
      margin: 0.8rem 0;
    }

    ul {
      margin: 0.3rem 0 0.5rem 1.2rem;
      padding: 0;
    }
    li {
      margin-bottom: 0.18rem;
    }

    table {
      width: 100%;
      border-collapse: collapse;
      margin: 0.5rem 0;
      font-size: 9.5pt;
    }
    th, td {
      border: 1px solid var(--rule);
      padding: 4px 6px;
      text-align: left;
      vertical-align: top;
    }
    th {
      background: rgba(142,93,49,0.08);
      font-weight: bold;
      color: var(--accent);
    }

    .img-block {
      text-align: center;
      margin: 0.4rem 0;
    }
    img.thumb {
      max-width: 100%;
      max-height: 3in;
      border-radius: 4px;
    }
    a { color: var(--accent); text-decoration: none; }
"""


def build_ebook_html(
    chapters: list[dict],
    title: str,
    subtitle: str,
    author: str,
    lang_tag: str,
    cover_image: str | None = None,
) -> str:
    """Build full ebook HTML from chapter dicts [{title, body_html, ep_num}]."""
    safe_title = escape(title)

    cover_img = ""
    if cover_image:
        cover_img = f'<img src="{cover_image}" alt="Cover" />'

    cover = f"""
    <div class="cover">
      {cover_img}
      <h1>{safe_title}</h1>
      <h2>{escape(subtitle)}</h2>
      <div class="subtitle">{escape(author)}</div>
    </div>
    """

    toc_items = []
    for ch in chapters:
        toc_items.append(
            f'<li><span class="ep-num">{escape(ch["ep_num"])}</span> {escape(ch["title"])}</li>'
        )

    toc_heading = "విషయ సూచిక" if lang_tag == "te" else "Table of Contents"
    toc = f"""
    <div class="toc">
      <h2>{toc_heading}</h2>
      <ul>
        {"".join(toc_items)}
      </ul>
    </div>
    """

    chapter_html = []
    for ch in chapters:
        chapter_html.append(f'<div class="chapter">\n{ch["body_html"]}\n</div>')

    body = cover + toc + "\n".join(chapter_html)

    return f"""<!doctype html>
<html lang="{lang_tag}">
<head>
  <meta charset="utf-8" />
  <title>{safe_title}</title>
  <style>{EBOOK_CSS}</style>
</head>
<body>
{body}
</body>
</html>
"""


def build_single_html(body: str, title: str) -> str:
    """Wrap a single-file body in full HTML."""
    return f"""<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <title>{escape(title)}</title>
  <style>{EBOOK_CSS}</style>
</head>
<body>
  <main>{body}</main>
</body>
</html>
"""


# ---------------------------------------------------------------------------
# Ebook assembly
# ---------------------------------------------------------------------------

def extract_chapter_info(md_text: str) -> tuple[str, str]:
    """Extract (H1 title, first H2 or subtitle) from markdown."""
    h1 = ""
    h2 = ""
    for line in md_text.splitlines():
        s = line.strip()
        if s.startswith("# ") and not h1:
            h1 = s[2:].strip()
        elif s.startswith("## ") and not h2:
            h2 = s[3:].strip()
            break
    return h1, h2


def collect_ebook_files(dir_path: Path, lang: str) -> list[Path]:
    """Collect and sort kindle md files for the given language."""
    if lang == "english":
        pattern = "*_kindle_english.md"
    else:
        pattern = "*_kindle.md"
    files = sorted(dir_path.glob(pattern))
    if lang == "telugu":
        files = [f for f in files if not f.name.endswith("_kindle_english.md")]
    return files


def build_ebook(dir_path: Path, lang: str, output_path: Path) -> None:
    """Build a consolidated ebook PDF from all kindle md files in dir_path."""
    files = collect_ebook_files(dir_path, lang)
    if not files:
        raise SystemExit(f"No kindle markdown files found for lang={lang} in {dir_path}")

    logger.info("Found %d chapters for %s ebook:", len(files), lang)
    for f in files:
        logger.info("  %s", f.name)

    images_dir = dir_path

    chapters = []
    first_cover_image = None

    for f in files:
        md = f.read_text(encoding="utf-8-sig")
        ep_match = re.match(r"^(\d{2})_", f.name)
        ep_num = ep_match.group(1) if ep_match else "??"
        h1_title, h2_title = extract_chapter_info(md)
        chapter_title = h2_title or h1_title or f.stem

        if not first_cover_image:
            thumb = dir_path / "images" / f"{ep_num}_thumbnail.jpg"
            if thumb.exists():
                first_cover_image = embed_image_base64(thumb)

        body_html = markdown_to_html_body(md, images_dir)
        chapters.append({
            "ep_num": ep_num,
            "title": chapter_title,
            "body_html": body_html,
        })

    if lang == "telugu":
        title = "జ్ఞానోదయం — శ్వాస మహావిద్య"
        subtitle = "365 రోజుల యజ్ఞం — Day 52 to Day 63"
        author = "డాక్టర్ న్యూటన్ కొండవీటి | Quantum Life University"
        lang_tag = "te"
    else:
        title = "Jnanodayam — The Great Science of Breath (Swasa Maha Vidya)"
        subtitle = "365 Days Yagna — Day 52 to Day 63"
        author = "Dr. Newton Kondaveti | Quantum Life University"
        lang_tag = "en"

    logger.info("Building %s ebook HTML...", lang)
    html = build_ebook_html(
        chapters, title, subtitle, author, lang_tag,
        cover_image=first_cover_image,
    )

    with tempfile.TemporaryDirectory(prefix="ebook_") as tmp:
        html_path = Path(tmp) / "ebook.html"
        html_path.write_text(html, encoding="utf-8")
        logger.info("Generating PDF: %s", output_path)
        print_to_pdf(html_path, output_path)

    size_mb = output_path.stat().st_size / (1024 * 1024)
    logger.info("Ebook written: %s (%.1f MB)", output_path, size_mb)


# ---------------------------------------------------------------------------
# Single-file mode (backward compatible)
# ---------------------------------------------------------------------------

def build_single(input_path: Path, output_path: Path) -> None:
    """Original single-file mode."""
    md = input_path.read_text(encoding="utf-8-sig")
    body = markdown_to_html_body(md, input_path.parent)
    title = input_path.stem
    html = build_single_html(body, title)

    with tempfile.TemporaryDirectory(prefix="md_pdf_") as tmp:
        html_path = Path(tmp) / "booklet.html"
        html_path.write_text(html, encoding="utf-8")
        print_to_pdf(html_path, output_path)

    logger.info("PDF written: %s", output_path)


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main() -> int:
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s | %(levelname)s | %(message)s",
        datefmt="%H:%M:%S",
    )

    args = build_parser().parse_args()

    # --- Ebook mode ---
    if args.dir:
        dir_path = Path(args.dir).expanduser()
        if not dir_path.is_dir():
            raise SystemExit(f"Directory not found: {dir_path}")

        if args.output:
            out = Path(args.output).expanduser()
        else:
            tag = "telugu" if args.lang == "telugu" else "english"
            out = dir_path / f"Jnanodayam_Swasa_Maha_Vidya_Ebook_{tag}.pdf"

        build_ebook(dir_path, args.lang, out)
        return 0

    # --- Single file mode ---
    if not args.input:
        build_parser().print_help()
        return 1

    input_path = Path(args.input).expanduser()
    if not input_path.exists():
        raise SystemExit(f"File not found: {input_path}")

    out = Path(args.output).expanduser() if args.output else input_path.with_suffix(".reading.pdf")
    build_single(input_path, out)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
