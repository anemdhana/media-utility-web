from __future__ import annotations

import argparse
import importlib
import logging
from pathlib import Path
import re


TIMESTAMP_PATTERN = re.compile(r"^\[(?P<start>[^\]]+) -> (?P<end>[^\]]+)\]\s+(?P<speaker>.*?):\s+(?P<text>.+)$")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Transcribe an audio file into timestamped text with speaker labels."
    )
    parser.add_argument("input", help="Path to input audio file (.m4a, .mp3, .wav, etc.)")
    parser.add_argument(
        "--output",
        default="",
        help="Output transcript path (.txt). Default: <input>.timestamped.txt",
    )
    parser.add_argument("--model", default="small", help="Whisper model size: tiny, base, small, medium, large-v3")
    parser.add_argument("--language", default="te", help="Language code (example: te, en). Use auto for detection.")
    parser.add_argument(
        "--speaker",
        default="Speaker 1",
        help="Speaker label when diarization is not enabled.",
    )
    parser.add_argument(
        "--diarize",
        action="store_true",
        help="Enable speaker diarization via pyannote (optional).",
    )
    parser.add_argument(
        "--hf-token",
        default="",
        help="Hugging Face token for pyannote diarization model access.",
    )
    return parser


def format_ts(seconds: float) -> str:
    if seconds < 0:
        seconds = 0.0
    total_ms = int(round(seconds * 1000))
    hrs = total_ms // 3_600_000
    rem = total_ms % 3_600_000
    mins = rem // 60_000
    rem = rem % 60_000
    secs = rem // 1000
    ms = rem % 1000
    return f"{hrs:02d}:{mins:02d}:{secs:02d}.{ms:03d}"


def normalize_text(text: str) -> str:
    text = re.sub(r"\s+", " ", (text or "")).strip()
    return text


def maybe_load_diarization_pipeline(hf_token: str):
    if not hf_token:
        return None
    try:
        pyannote_audio = importlib.import_module("pyannote.audio")
    except ImportError:
        return None
    Pipeline = getattr(pyannote_audio, "Pipeline")

    try:
        return Pipeline.from_pretrained("pyannote/speaker-diarization-3.1", use_auth_token=hf_token)
    except Exception:
        return None


def diarization_ranges(pipeline, audio_path: Path):
    if pipeline is None:
        return []
    diarization = pipeline(str(audio_path))
    ranges = []
    for turn, _, speaker in diarization.itertracks(yield_label=True):
        ranges.append((float(turn.start), float(turn.end), str(speaker)))
    return ranges


def speaker_for_segment(start: float, end: float, diarization_data, fallback_speaker: str) -> str:
    if not diarization_data:
        return fallback_speaker

    mid = (start + end) / 2.0
    best = None
    best_dist = None
    for s, e, label in diarization_data:
        if s <= mid <= e:
            return label
        dist = min(abs(mid - s), abs(mid - e))
        if best_dist is None or dist < best_dist:
            best_dist = dist
            best = label
    return best or fallback_speaker


def write_transcript(
    output_path: Path,
    title: str,
    default_speaker: str,
    model_name: str,
    language: str,
    rows,
) -> None:
    lines = [
        f"Title: {title}",
        f"Speaker: {default_speaker}",
        f"Model: {model_name}",
        f"Language: {language}",
        "",
    ]

    for start_s, end_s, speaker, text in rows:
        normalized = normalize_text(text)
        if not normalized:
            continue
        lines.append(f"[{format_ts(start_s)} -> {format_ts(end_s)}] {speaker}: {normalized}")

    output_path.write_text("\n".join(lines).strip() + "\n", encoding="utf-8-sig")


def main() -> int:
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s | %(levelname)s | %(message)s",
        datefmt="%H:%M:%S",
    )
    logger = logging.getLogger("step1")

    args = build_parser().parse_args()
    input_path = Path(args.input).expanduser()
    if not input_path.exists() or not input_path.is_file():
        raise SystemExit(f"Input file not found: {input_path}")

    logger.info("[1/6] Input validated: %s", input_path)

    output_path = Path(args.output).expanduser() if args.output else input_path.with_suffix(".timestamped.txt")
    language = "" if args.language.lower() == "auto" else args.language
    logger.info("[2/6] Output path resolved: %s", output_path)

    try:
        faster_whisper = importlib.import_module("faster_whisper")
    except ImportError as exc:
        raise SystemExit("Missing dependency: faster-whisper. Install with: pip install faster-whisper") from exc

    logger.info("[3/6] Loading whisper model: %s", args.model)

    WhisperModel = getattr(faster_whisper, "WhisperModel")
    model = WhisperModel(args.model, device="cpu", compute_type="int8")

    diarization_data = []
    if args.diarize:
        logger.info("[4/6] Diarization enabled; attempting pyannote load")
        pipeline = maybe_load_diarization_pipeline(args.hf_token)
        diarization_data = diarization_ranges(pipeline, input_path)
        logger.info("Diarization ranges collected: %s", len(diarization_data))
    else:
        logger.info("[4/6] Diarization disabled")

    logger.info("[5/6] Transcribing audio")
    segments, info = model.transcribe(
        str(input_path),
        language=language or None,
        task="transcribe",
        vad_filter=True,
    )

    rows = []
    segment_count = 0
    for segment in segments:
        start = float(segment.start)
        end = float(segment.end)
        speaker = speaker_for_segment(start, end, diarization_data, args.speaker)
        rows.append((start, end, speaker, segment.text))
        segment_count += 1
        if segment_count % 25 == 0:
            logger.info("Transcription progress: %s segments processed", segment_count)

    detected_language = getattr(info, "language", "auto") or "auto"
    title = input_path.stem
    write_transcript(output_path, title, args.speaker, args.model, detected_language, rows)

    logger.info("[6/6] Transcript written: %s", output_path)
    logger.info("Segments total: %s | Detected language: %s", segment_count, detected_language)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
