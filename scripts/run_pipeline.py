from __future__ import annotations

import argparse
import logging
from pathlib import Path
import subprocess
import sys
import time


# ---------------------------------------------------------------------------
# Properties-file loader
# ---------------------------------------------------------------------------

def load_properties(props_path: Path) -> dict[str, str]:
    """Parse a simple key=value properties file; ignore blank lines and # comments."""
    props: dict[str, str] = {}
    for line in props_path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        if "=" in line:
            key, _, value = line.partition("=")
            props[key.strip()] = value.strip()
    return props


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description=(
            "Run pipeline: audio -> timestamped txt -> cleaned md -> Kindle PDF.\n"
            "Pass --props to drive everything from a properties file."
        )
    )
    parser.add_argument(
        "--props",
        default="",
        help="Path to a pipeline.properties file. All other flags are optional when using this.",
    )
    parser.add_argument("input", nargs="?", default="", help="Path to input audio / txt / md file")

    parser.add_argument("--steps", default="1,2,3", help="Comma-separated steps to run: 1, 2, 3")
    parser.add_argument("--model", default="small", help="Whisper model size for step 1")
    parser.add_argument("--language", default="te", help="Language for step 1")
    parser.add_argument("--speaker", default="Speaker 1", help="Fallback speaker label for step 1")
    parser.add_argument("--diarize", action="store_true", help="Enable diarization for step 1")
    parser.add_argument("--hf-token", default="", help="HuggingFace token for diarization")
    parser.add_argument("--output-dir", default="", help="Output folder. Default: same folder as input")
    parser.add_argument("--basename", default="", help="Base name for outputs. Default: input file stem")

    return parser


def configure_logging() -> logging.Logger:
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s | %(levelname)s | %(message)s",
        datefmt="%H:%M:%S",
    )
    return logging.getLogger("pipeline")


def run_step(command: list[str], step_name: str, index: int, total: int, logger: logging.Logger) -> None:
    logger.info("[%s/%s] START %s", index, total, step_name)
    logger.info("Command: %s", " ".join(command))
    started = time.perf_counter()
    result = subprocess.run(command)
    elapsed = time.perf_counter() - started
    if result.returncode != 0:
        logger.error("[%s/%s] FAILED %s (exit=%s, %.1fs)", index, total, step_name, result.returncode, elapsed)
        raise SystemExit(f"{step_name} failed with exit code {result.returncode}")
    logger.info("[%s/%s] DONE %s (%.1fs)", index, total, step_name, elapsed)


def main() -> int:
    logger = configure_logging()
    args = build_parser().parse_args()

    # ── Merge properties file (file values fill in unset CLI defaults) ───────
    props: dict[str, str] = {}
    if args.props:
        props_path = Path(args.props).expanduser().resolve()
        if not props_path.exists():
            raise SystemExit(f"Properties file not found: {props_path}")
        props = load_properties(props_path)
    elif (Path(__file__).resolve().parent / "pipeline.properties").exists():
        # Auto-load sibling pipeline.properties when present and --props not given
        props = load_properties(Path(__file__).resolve().parent / "pipeline.properties")

    def prop(key: str, cli_val: str, default: str = "") -> str:
        """Return cli_val if non-empty, else props value, else default."""
        if cli_val:
            return cli_val
        return props.get(key, default)

    steps_raw = prop("steps", args.steps, "1,2,3")
    steps = [s.strip() for s in steps_raw.split(",") if s.strip()]
    invalid_steps = [s for s in steps if s not in {"1", "2", "3"}]
    if invalid_steps:
        raise SystemExit(f"Invalid steps in configuration: {', '.join(invalid_steps)}. Allowed: 1,2,3")

    input_raw = prop("input", args.input, "")
    if not input_raw:
        raise SystemExit("No input file specified. Set 'input' in pipeline.properties or pass it as a positional argument.")

    input_path = Path(input_raw).expanduser().resolve()
    if not input_path.exists() or not input_path.is_file():
        raise SystemExit(f"Input file not found: {input_path}")

    logger.info("Input: %s", input_path)
    logger.info("Selected steps: %s", ", ".join(steps) if steps else "none")

    output_dir_raw = prop("output_dir", args.output_dir, "")
    output_dir = Path(output_dir_raw).expanduser().resolve() if output_dir_raw else input_path.parent
    output_dir.mkdir(parents=True, exist_ok=True)

    base = prop("basename", args.basename, "").strip() or input_path.stem

    model    = prop("model",    args.model,    "small")
    language = prop("language", args.language, "te")
    speaker  = prop("speaker",  args.speaker,  "Speaker 1")
    diarize  = args.diarize or props.get("diarize", "false").lower() == "true"
    hf_token = prop("hf_token", args.hf_token, "")

    txt_path = output_dir / f"{base}.timestamped.txt"
    md_path  = output_dir / f"{base}.timestamped.md"
    pdf_path = output_dir / f"{base}.timestamped.reading.pdf"

    python_exec = sys.executable
    script_dir = Path(__file__).resolve().parent

    step1 = [
        python_exec,
        str(script_dir / "01_transcribe_timestamped.py"),
        str(input_path),
        "--output", str(txt_path),
        "--model",    model,
        "--language", language,
        "--speaker",  speaker,
    ]
    if diarize:
        step1.append("--diarize")
        if hf_token:
            step1.extend(["--hf-token", hf_token])

    step2 = [
        python_exec,
        str(script_dir / "02_transcript_to_markdown.py"),
        str(txt_path),
        "--output", str(md_path),
    ]

    step3 = [
        python_exec,
        str(script_dir / "03_markdown_to_kindle_pdf.py"),
        str(md_path),
        "--output", str(pdf_path),
    ]

    logger.info("Outputs:")
    logger.info("  transcript: %s", txt_path)
    logger.info("  markdown  : %s", md_path)
    logger.info("  pdf       : %s", pdf_path)

    # ── Run only the selected steps ─────────────────────────────────────────
    selected_count = len(steps)
    progress_index = 0

    if "1" in steps:
        progress_index += 1
        run_step(step1, "step-1-transcribe", progress_index, selected_count, logger)
    else:
        logger.info("SKIPPED step-1-transcribe")

    if "2" in steps:
        progress_index += 1
        run_step(step2, "step-2-clean-markdown", progress_index, selected_count, logger)
    else:
        logger.info("SKIPPED step-2-clean-markdown")

    if "3" in steps:
        progress_index += 1
        run_step(step3, "step-3-markdown-to-pdf", progress_index, selected_count, logger)
    else:
        logger.info("SKIPPED step-3-markdown-to-pdf")

    logger.info("Pipeline complete")
    if "1" in steps:
        logger.info("  Timestamped transcript : %s", txt_path)
    if "2" in steps:
        logger.info("  Markdown               : %s", md_path)
    if "3" in steps:
        logger.info("  Kindle PDF             : %s", pdf_path)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
