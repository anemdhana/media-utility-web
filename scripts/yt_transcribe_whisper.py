from __future__ import annotations

"""yt_transcribe_whisper.py — Download YouTube audio and transcribe with fast-whisper.

Generates timestamped transcript with speaker labels and timestamps.

Usage:
    python yt_transcribe_whisper.py <video_id> [--start HH:MM:SS] [--end HH:MM:SS] [OPTIONS]
    python yt_transcribe_whisper.py --props media-input.properties --scenario extract_split
    python yt_transcribe_whisper.py --audio /path/to/audio.mp3 [OPTIONS]

Examples:
    python yt_transcribe_whisper.py dQw4w9WgXcQ
    python yt_transcribe_whisper.py dQw4w9WgXcQ --start 00:02:00 --end 00:10:00
    python yt_transcribe_whisper.py dQw4w9WgXcQ --lang hi
    python yt_transcribe_whisper.py dQw4w9WgXcQ --lang en
    python yt_transcribe_whisper.py --audio local_audio.mp3 --lang en
    python yt_transcribe_whisper.py --props src/test/resources/media-input.properties --scenario extract_split

Output format (same as timestamped transcripts in this project):
    [HH:MM:SS.mmm -> HH:MM:SS.mmm] Speaker: text

Dependencies:
    pip install yt-dlp faster-whisper
"""

import argparse
import logging
import os
import re
import shutil
import subprocess
import sys
import tempfile
import time
from pathlib import Path


# ---------------------------------------------------------------------------
# Time helpers
# ---------------------------------------------------------------------------

def parse_ts(ts: str) -> float:
    """Parse HH:MM:SS[.mmm] or HH:MM:SS,mmm or MM:SS → seconds as float."""
    ts = ts.strip().replace(",", ".")
    parts = ts.split(":")
    try:
        if len(parts) == 3:
            return int(parts[0]) * 3600 + int(parts[1]) * 60 + float(parts[2])
        if len(parts) == 2:
            return int(parts[0]) * 60 + float(parts[1])
        return float(parts[0])
    except (ValueError, IndexError):
        return 0.0


def format_ts(seconds: float) -> str:
    """Format seconds → HH:MM:SS.mmm"""
    if seconds < 0:
        seconds = 0.0
    total_ms = int(round(seconds * 1000))
    hrs = total_ms // 3_600_000
    rem = total_ms % 3_600_000
    mins = rem // 60_000
    rem %= 60_000
    secs = rem // 1000
    ms = rem % 1000
    return f"{hrs:02d}:{mins:02d}:{secs:02d}.{ms:03d}"


# ---------------------------------------------------------------------------
# Speaker detection
# ---------------------------------------------------------------------------

# Patterns tried in order:
#   1.  >> Speaker Name: text       (common YouTube multi-speaker captions)
#   2.  [SPEAKER NAME]: text        (bracket format, optional colon)
#   3.  [SPEAKER NAME] text         (bracket with no colon)
#   4.  Speaker Name: text          (plain "Capitalized Name:" prefix)
_SPEAKER_PATTERNS = [
    re.compile(r"^>>\s*(?P<speaker>[^:<>\[\]]+?)\s*:\s*(?P<text>.+)$"),
    re.compile(r"^\[(?P<speaker>[A-Z][^\[\]]{1,40}?)\]\s*:\s*(?P<text>.+)$"),
    re.compile(r"^\[(?P<speaker>[A-Z][^\[\]]{1,40}?)\]\s+(?P<text>.+)$"),
    re.compile(r"^(?P<speaker>[A-Z][A-Za-z .'-]{1,30}):\s+(?P<text>\S.+)$"),
]


def detect_speaker(text: str, current_speaker: str) -> tuple[str, str]:
    """Return (speaker_label, cleaned_text).  Falls back to current_speaker."""
    for pat in _SPEAKER_PATTERNS:
        m = pat.match(text.strip())
        if m:
            return m.group("speaker").strip(), m.group("text").strip()
    return current_speaker, text.strip()


# ---------------------------------------------------------------------------
# Properties loader
# ---------------------------------------------------------------------------

def load_properties(props_path: Path) -> dict[str, str]:
    """Parse a key=value properties file; ignore blank lines and # comments."""
    props: dict[str, str] = {}
    for line in props_path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        if "=" in line:
            key, _, value = line.partition("=")
            props[key.strip()] = value.strip()
    return props


_SPRING_PLACEHOLDER = re.compile(r"\$\{([^}]+)\}")


def resolve_spring_placeholders(value: str, props: dict[str, str]) -> str:
    """Resolve Spring-style ${key} placeholders.

    Supported built-ins: user.home → Path.home().
    Other keys are looked up in *props* first, then os.environ.
    """
    def _replace(m: re.Match) -> str:
        key = m.group(1)
        if key == "user.home":
            return str(Path.home())
        return props.get(key, os.environ.get(key, m.group(0)))

    return _SPRING_PLACEHOLDER.sub(_replace, value)


def read_mediafiles_dir(app_props_path: Path) -> Path | None:
    """Load application.properties and return the resolved mediafiles_dir, or None."""
    if not app_props_path.exists():
        return None
    props = load_properties(app_props_path)
    raw = props.get("mediafiles_dir", "")
    if not raw:
        return None
    resolved = resolve_spring_placeholders(raw, props)
    return Path(resolved).expanduser()


def resolve_scenario_props(props: dict[str, str], scenario: str) -> dict[str, str]:
    """Merge properties following the project resolution order:

    Resolution precedence (highest wins):
        4. scenario.<scenario>.<key>
        3. common.<key>
        2. default.<key>
        1. <key>  (unscoped legacy)
    """
    # Map of logical key → properties key variants tried in ascending precedence
    logical_keys = ["videoId", "startTime", "endTime", "label", "speaker", "lang", "audioFile"]
    result: dict[str, str] = {}
    for key in logical_keys:
        value = ""
        for prefix in ("", "default.", "common.", f"scenario.{scenario}."):
            candidate = props.get(f"{prefix}{key}", "")
            if candidate:
                value = candidate
        if value:
            result[key] = value
    return result


# ---------------------------------------------------------------------------
# yt-dlp download
# ---------------------------------------------------------------------------

_UNSAFE_FILENAME_CHARS = re.compile(r'[\\/:*?"<>|\x00-\x1f]')
_MULTI_DASH = re.compile(r"-{2,}")


def slugify(title: str, max_len: int = 120) -> str:
    """Convert a video title to a safe filename stem."""
    slug = _UNSAFE_FILENAME_CHARS.sub("-", title)
    slug = _MULTI_DASH.sub("-", slug).strip("- ")
    return slug[:max_len]


def fetch_video_title(video_id: str, logger: logging.Logger) -> str:
    """Return the YouTube video title via yt-dlp --print title, or '' on failure."""
    url = f"https://www.youtube.com/watch?v={video_id}"
    try:
        result = subprocess.run(
            ["yt-dlp", "--print", "title", "--no-playlist", url],
            capture_output=True,
            text=True,
            timeout=30,
        )
        if result.returncode == 0:
            title = result.stdout.strip().splitlines()[0]
            logger.info("      Title     : %s", title)
            return title
        logger.debug("yt-dlp title fetch failed: %s", result.stderr.strip())
    except Exception as exc:  # noqa: BLE001
        logger.debug("Title fetch error: %s", exc)
    return ""


def download_audio(
    video_id: str,
    tmp_dir: Path,
    logger: logging.Logger,
) -> Path | None:
    """Download audio via yt-dlp; return path to the audio file, or None."""
    url = f"https://www.youtube.com/watch?v={video_id}"
    out_template = str(tmp_dir / "%(id)s.%(ext)s")

    cmd = [
        "yt-dlp",
        "--extract-audio",
        "--audio-format", "mp3",
        "--audio-quality", "192",
        "-o", out_template,
        url,
    ]
    logger.info("Downloading audio: %s", " ".join(cmd))
    result = subprocess.run(cmd, capture_output=True, text=True)

    if result.returncode != 0:
        logger.debug("yt-dlp stderr: %s", result.stderr.strip())
        return None

    # Find the downloaded audio file
    for ext_glob in ("*.mp3", "*.m4a", "*.wav", "*.opus", "*.vorbis"):
        found = list(tmp_dir.glob(ext_glob))
        if found:
            logger.info("Downloaded: %s", found[0].name)
            return found[0]

    return None


def normalize_languages(lang_input: str) -> str:
    """Normalize language input to comma-separated format, stripping whitespace.
    
    Examples:
        'en' → 'en'
        'hi' → 'hi'
        'en,hi' → 'en,hi'
        'en, hi' → 'en,hi'
    """
    if not lang_input:
        return "en"
    # Split by comma, strip whitespace from each, join back
    langs = [lang.strip() for lang in lang_input.split(",")]
    # Filter out empty strings
    langs = [lang for lang in langs if lang]
    return ",".join(langs) if langs else "en"


def transcribe_audio(
    audio_file: Path,
    lang: str,
    model_size: str,
    logger: logging.Logger,
) -> list[dict] | None:
    """Transcribe audio using faster-whisper; return list of segments or None.
    
    Each segment is a dict with keys: start, end, text.
    Whisper will auto-detect language if lang is 'auto'.
    For single language, pass the language code (e.g., 'en', 'hi').
    For multiple languages (e.g., 'en,hi'), auto-detect is used.
    """
    try:
        from faster_whisper import WhisperModel
    except ImportError:
        logger.error("faster-whisper not installed. Install with: pip install faster-whisper")
        return None

    # For mixed-language input, rely on Whisper auto-detection.
    primary_lang = lang
    if "," in lang:
        logger.info(
            "Mixed language input '%s' detected; using auto-detect for better code-mixed transcription.",
            lang,
        )
        primary_lang = "auto"
    if primary_lang == "auto":
        primary_lang = None  # None means auto-detect

    logger.info("Loading Whisper model '%s'...", model_size)
    try:
        t0 = time.time()
        # Options: tiny, base, small, medium, large
        model = WhisperModel(model_size, device="auto", compute_type="auto")
        logger.info("Model loaded in %.1f seconds.", time.time() - t0)
    except Exception as exc:
        logger.error("Failed to load Whisper model: %s", exc)
        return None

    logger.info("Transcribing audio (language: %s)...", primary_lang or "auto-detect")
    try:
        t0 = time.time()
        segments, info = model.transcribe(str(audio_file), language=primary_lang)
        audio_duration = info.duration
        logger.info(
            "Audio duration: %s (%.0f seconds). Detected language: %s (prob=%.2f)",
            format_ts(audio_duration), audio_duration,
            info.language, info.language_probability,
        )
        # Convert generator to list of dicts, logging progress periodically
        result = []
        last_pct_logged = -1
        for segment in segments:
            result.append({
                "start": segment.start,
                "end": segment.end,
                "text": segment.text.strip(),
            })
            # Log progress every ~10%
            if audio_duration > 0:
                pct = int(segment.end / audio_duration * 100)
                pct_bucket = pct // 10 * 10
                if pct_bucket > last_pct_logged and pct_bucket <= 100:
                    elapsed = time.time() - t0
                    logger.info(
                        "  Progress: %3d%% (%s / %s) | %d segments | elapsed %.0fs",
                        min(pct, 100),
                        format_ts(segment.end),
                        format_ts(audio_duration),
                        len(result),
                        elapsed,
                    )
                    last_pct_logged = pct_bucket
        elapsed_total = time.time() - t0
        logger.info(
            "Transcribed %d segments in %.1f seconds (%.1fx realtime).",
            len(result), elapsed_total,
            audio_duration / elapsed_total if elapsed_total > 0 else 0,
        )
        return result
    except Exception as exc:
        logger.error("Transcription failed: %s", exc)
        return None


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description=(
            "Download YouTube audio and transcribe with faster-whisper, "
            "or transcribe local audio files. "
            "Outputs timestamped transcript with speaker labels.\n\n"
            "Output format:  [HH:MM:SS.mmm -> HH:MM:SS.mmm] Speaker: text"
        ),
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument(
        "video_id",
        nargs="?",
        default="",
        help="YouTube video ID (e.g. dQw4w9WgXcQ) or full URL. Optional when --props or --audio is supplied.",
    )
    parser.add_argument(
        "--audio",
        default="",
        metavar="FILE",
        help="Path to local audio file (mp3, wav, m4a, etc.). Takes precedence over video_id.",
    )
    parser.add_argument(
        "--props",
        default="",
        metavar="FILE",
        help="Path to a media-input.properties file (e.g. src/test/resources/media-input.properties).",
    )
    parser.add_argument(
        "--scenario",
        default="extract_split",
        metavar="KEY",
        help="Scenario key to read from the properties file (default: extract_split).",
    )
    parser.add_argument(
        "--start",
        default="",
        metavar="HH:MM:SS",
        help="Clip start time (default: beginning of audio)",
    )
    parser.add_argument(
        "--end",
        default="",
        metavar="HH:MM:SS",
        help="Clip end time (default: end of audio)",
    )
    parser.add_argument(
        "--lang",
        default="en",
        metavar="LANG",
        help=(
            "Language code for transcription: en, hi, te, fr, etc. "
            "Use 'auto' for auto-detection, or comma-separated codes like 'en,hi' "
            "for mixed-language speech. "
            "(default: en)"
        ),
    )
    parser.add_argument(
        "--speaker",
        default=None,
        metavar="NAME",
        help="Default speaker label for all transcribed text (default: Speaker)",
    )
    parser.add_argument(
        "--app-props",
        default="",
        metavar="FILE",
        help=(
            "Path to application.properties for reading mediafiles_dir. "
            "Defaults to src/main/resources/application.properties relative to the script."
        ),
    )
    parser.add_argument(
        "--output",
        default="",
        metavar="FILE",
        help="Output .txt path. Overrides mediafiles_dir when specified.",
    )
    parser.add_argument(
        "--keep-tmp",
        action="store_true",
        help="Keep the temporary download folder after extraction",
    )
    parser.add_argument(
        "--model",
        default="base",
        metavar="SIZE",
        help=(
            "Whisper model size: tiny, base, small, medium, large. "
            "Larger = more accurate but slower. (default: base)"
        ),
    )
    return parser


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main() -> int:
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s | %(levelname)s | %(message)s",
        datefmt="%H:%M:%S",
    )
    logger = logging.getLogger("yt_transcribe")

    args = build_parser().parse_args()

    # ── Load and merge properties file ──────────────────────────────────────
    scenario_props: dict[str, str] = {}
    if args.props:
        props_path = Path(args.props).expanduser().resolve()
        if not props_path.exists():
            raise SystemExit(f"Properties file not found: {props_path}")
        raw_props = load_properties(props_path)
        scenario_props = resolve_scenario_props(raw_props, args.scenario)
        logger.info("Loaded scenario '%s' from: %s", args.scenario, props_path)
        logger.info("  Resolved: %s", scenario_props)

    def prop(key: str, cli_val: str, fallback: str = "") -> str:
        """CLI value wins; then scenario property; then fallback."""
        if cli_val:
            return cli_val
        return scenario_props.get(key, fallback)

    # Determine source: --audio (local file) or video_id (YouTube)
    audio_path: Path | None = None
    video_id = ""

    if args.audio:
        # Use local audio file
        audio_path = Path(args.audio).expanduser().resolve()
        if not audio_path.exists():
            logger.error("Audio file not found: %s", audio_path)
            return 1
        logger.info("[1/3] Audio file: %s", audio_path)
    else:
        # Try to get video_id from CLI or properties
        raw_video_id = prop("videoId", args.video_id)
        if not raw_video_id:
            logger.error(
                "No audio source supplied. "
                "Pass --audio FILE or provide a video ID (CLI arg or via --props)."
            )
            return 1

        video_id = raw_video_id.strip()
        # Extract video ID from URL if needed
        url_match = re.search(r"(?:v=|youtu\.be/)([A-Za-z0-9_-]{11})", video_id)
        if url_match:
            video_id = url_match.group(1)

        logger.info("[1/3] Video ID  : %s", video_id)

    start_raw = prop("startTime", args.start)
    end_raw   = prop("endTime",   args.end)
    start_s: float = parse_ts(start_raw) if start_raw else 0.0
    end_s: float   = parse_ts(end_raw)   if end_raw   else float("inf")

    if start_raw and end_raw and end_s <= start_s:
        logger.error("--end time must be after --start time.")
        return 1

    # Speaker: CLI > props > default
    resolved_speaker = prop("speaker", args.speaker or "", "Speaker")

    # Language: CLI default is "en"
    resolved_lang = args.lang if args.lang != "en" else prop("lang", args.lang, "en")
    resolved_lang = normalize_languages(resolved_lang)

    # ── Resolve output directory from application.properties ─────────────────
    app_props_path = (
        Path(args.app_props).expanduser().resolve()
        if args.app_props
        else Path(__file__).resolve().parent.parent
        / "src" / "main" / "resources" / "application.properties"
    )
    mediafiles_dir = read_mediafiles_dir(app_props_path)
    if mediafiles_dir:
        logger.info("      Output dir: %s  (from mediafiles_dir)", mediafiles_dir)
        mediafiles_dir.mkdir(parents=True, exist_ok=True)
    else:
        logger.info("      Output dir: . (mediafiles_dir not resolved; using cwd)")

    # Output: CLI > YouTube title > video_id/filename fallback
    base_dir = mediafiles_dir or Path(".")
    if args.output:
        output_path = Path(args.output).expanduser()
    else:
        if audio_path:
            # Use audio filename as stem
            stem = audio_path.stem
        else:
            # Fetch video title for filename
            title = fetch_video_title(video_id, logger)
            stem = slugify(title) if title else video_id
        output_path = base_dir / f"{stem}.transcript.txt"

    end_label = "end" if end_s == float("inf") else format_ts(end_s)
    logger.info("      Language  : %s", resolved_lang)
    logger.info("      Speaker   : %s", resolved_speaker)
    logger.info("      Time range: %s → %s", format_ts(start_s), end_label)

    tmp_dir: Path | None = None
    try:
        # ── Step 1: Get audio file ───────────────────────────────────────
        if not audio_path:
            # Download audio from YouTube
            logger.info("[2/3] Downloading audio...")
            tmp_dir = Path(tempfile.mkdtemp(prefix=f"yt_trans_{video_id}_"))
            audio_path = download_audio(video_id, tmp_dir, logger)

            if audio_path is None:
                logger.error("Failed to download audio for video: %s", video_id)
                return 1
        else:
            logger.info("[2/3] Using local audio file: %s", audio_path)

        # ── Step 2: Transcribe ───────────────────────────────────────────
        logger.info("[3/3] Transcribing...")
        segments = transcribe_audio(audio_path, resolved_lang, args.model, logger)

        if segments is None:
            logger.error("Transcription failed.")
            return 1

        # ── Step 3: Filter by time range ────────────────────────────────
        logger.info("      Filtering to time range %s → %s", format_ts(start_s), end_label)
        filtered = [
            seg for seg in segments
            if seg["end"] > start_s and seg["start"] < end_s
        ]
        logger.info("      %d segments in range", len(filtered))

        if not filtered:
            logger.warning("No transcribed segments found in the specified time range.")

        # ── Step 4: Write output ─────────────────────────────────────────
        logger.info("Writing: %s", output_path)

        video_url = f"https://www.youtube.com/watch?v={video_id}" if video_id else str(audio_path)
        header_lines = [
            f"Title    : {Path(output_path.stem).name}",
            f"Source   : {video_url}",
            f"Language : {resolved_lang}",
            f"Model    : {args.model}",
            f"Range    : {format_ts(start_s)} → {end_label}",
            f"Segments : {len(filtered)}",
            "",
        ]

        transcript_lines: list[str] = []
        current_speaker = resolved_speaker
        for seg in filtered:
            seg_start = seg["start"]
            seg_end = seg["end"]
            text = seg["text"]
            speaker, clean_text = detect_speaker(text, current_speaker)
            current_speaker = speaker
            if clean_text:
                transcript_lines.append(
                    f"[{format_ts(seg_start)} -> {format_ts(seg_end)}] {speaker}: {clean_text}"
                )

        if args.scenario and args.props:
            header_lines.insert(0, f"Scenario : {args.scenario}")

        all_lines = header_lines + transcript_lines
        output_path.write_text("\n".join(all_lines).strip() + "\n", encoding="utf-8-sig")

        logger.info("Done. %d transcript lines written to: %s", len(transcript_lines), output_path)
        return 0

    finally:
        if tmp_dir:
            if args.keep_tmp:
                logger.info("Temp files kept at: %s", tmp_dir)
            else:
                shutil.rmtree(tmp_dir, ignore_errors=True)


if __name__ == "__main__":
    sys.exit(main())
