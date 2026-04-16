from __future__ import annotations

import argparse
from dataclasses import dataclass
import logging
from pathlib import Path
import re


ENTRY_PATTERN = re.compile(r"^\[(?P<start>[^\]]+) -> (?P<end>[^\]]+)\]\s+(?P<speaker>.*?):\s+(?P<text>.+)$")
QNA_PATTERN = re.compile(r"\b(question|q\&a|ప్రశ్న|doubt|డౌట్|అడగండి)\b", re.IGNORECASE)
TERMINAL_PUNCTUATION = (".", "!", "?", "।", '"', "'")


@dataclass
class Entry:
    start: str
    end: str
    speaker: str
    text: str


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Clean a timestamped transcript and generate structured markdown for reading."
    )
    parser.add_argument("input", help="Path to timestamped transcript (.txt)")
    parser.add_argument(
        "--output",
        default="",
        help="Output markdown path. Default: <input>.md",
    )
    return parser


def normalize_spaces(text: str) -> str:
    text = re.sub(r"\s+", " ", text).strip()
    text = re.sub(r"\s+([,.;:?!])", r"\1", text)
    return text


def timestamp_to_seconds(value: str) -> float:
    parts = value.split(":")
    if len(parts) != 3:
        return 0.0

    hours = int(parts[0])
    minutes = int(parts[1])
    seconds = float(parts[2])
    return (hours * 3600) + (minutes * 60) + seconds


def overlap_size(existing: str, incoming: str) -> int:
    existing_words = existing.split()
    incoming_words = incoming.split()
    limit = min(len(existing_words), len(incoming_words), 18)

    for size in range(limit, 0, -1):
        if existing_words[-size:] != incoming_words[:size]:
            continue
        if size >= 2:
            return size
        if len(incoming_words[0]) >= 6:
            return size

    return 0


def combine_overlapping_text(existing: str, incoming: str) -> str:
    existing = normalize_spaces(existing)
    incoming = normalize_spaces(incoming)

    if not existing:
        return incoming
    if not incoming:
        return existing
    if incoming == existing or incoming in existing:
        return existing
    if existing in incoming:
        return incoming

    shared_words = overlap_size(existing, incoming)
    if shared_words:
        incoming_tail = incoming.split()[shared_words:]
        if not incoming_tail:
            return existing
        return f"{existing} {' '.join(incoming_tail)}".strip()

    return f"{existing} {incoming}".strip()


def should_break_block(current: Entry, item: Entry) -> bool:
    if item.speaker != current.speaker:
        return True

    current_start = timestamp_to_seconds(current.start)
    current_end = timestamp_to_seconds(current.end)
    item_start = timestamp_to_seconds(item.start)

    gap = max(0.0, item_start - current_end)
    word_count = len(current.text.split())
    sentence_count = len(split_sentences(current.text))
    has_terminal_pause = current.text.endswith(TERMINAL_PUNCTUATION)

    if gap >= 8.0:
        return True
    if word_count >= 240:
        return True
    if word_count >= 120 and has_terminal_pause:
        return True
    if sentence_count >= 5 and has_terminal_pause:
        return True
    if (timestamp_to_seconds(item.end) - current_start) >= 150 and word_count >= 90 and has_terminal_pause:
        return True

    return False


def add_soft_commas(text: str) -> str:
    markers = ["అంటే", "మరి", "కాబట్టి", "అందుకే", "కానీ", "కాని", "so", "then", "however"]
    pattern = r"(?<![,])\s+(%s)\s+" % "|".join(re.escape(m) for m in markers)
    return re.sub(pattern, lambda m: f", {m.group(1)} ", text, flags=re.IGNORECASE)


def ensure_terminal_punctuation(text: str) -> str:
    if not text:
        return text
    if text.endswith(TERMINAL_PUNCTUATION):
        return text
    return text + "."


def split_sentences(text: str) -> list[str]:
    parts = re.split(r"(?<=[.!?।])\s+", text)
    return [p.strip() for p in parts if p.strip()]


def paragraphize(text: str, max_sentences: int = 4, max_words: int = 90) -> list[str]:
    sentences = split_sentences(text)
    if not sentences:
        return []

    paragraphs: list[str] = []
    bucket: list[str] = []
    bucket_words = 0

    for sentence in sentences:
        words = len(sentence.split())
        if bucket and (len(bucket) >= max_sentences or (bucket_words + words) > max_words):
            paragraphs.append(" ".join(bucket).strip())
            bucket = [sentence]
            bucket_words = words
        else:
            bucket.append(sentence)
            bucket_words += words

    if bucket:
        paragraphs.append(" ".join(bucket).strip())

    return paragraphs


def parse_transcript(path: Path):
    metadata: dict[str, str] = {}
    entries: list[Entry] = []

    for raw in path.read_text(encoding="utf-8-sig").splitlines():
        line = raw.strip()
        if not line:
            continue

        match = ENTRY_PATTERN.match(line)
        if match:
            entries.append(
                Entry(
                    start=match.group("start").strip(),
                    end=match.group("end").strip(),
                    speaker=match.group("speaker").strip(),
                    text=match.group("text").strip(),
                )
            )
            continue

        if ":" in line:
            key, value = line.split(":", maxsplit=1)
            metadata[key.strip()] = value.strip()

    return metadata, entries


def merge_entries(entries: list[Entry]) -> list[Entry]:
    if not entries:
        return []

    merged: list[Entry] = []
    current = entries[0]

    for item in entries[1:]:
        if not should_break_block(current, item):
            current = Entry(
                start=current.start,
                end=item.end,
                speaker=current.speaker,
                text=combine_overlapping_text(current.text, item.text),
            )
        else:
            merged.append(current)
            current = item
    merged.append(current)
    return merged


def cleaned_text(text: str) -> str:
    text = normalize_spaces(text)
    text = add_soft_commas(text)
    text = normalize_spaces(text)
    text = ensure_terminal_punctuation(text)
    return text


def to_markdown(metadata: dict[str, str], entries: list[Entry]) -> str:
    title = metadata.get("Title") or "Transcript"
    speaker = metadata.get("Speaker") or "Speaker 1"

    main_blocks: list[str] = []
    qna_blocks: list[str] = []

    for item in merge_entries(entries):
        text = cleaned_text(item.text)
        paragraphs = paragraphize(text)
        block_text = "\n\n".join(paragraphs) if paragraphs else text
        stamped = f"**[{item.start} -> {item.end}] {item.speaker}**\n\n{block_text}"
        if QNA_PATTERN.search(item.text):
            qna_blocks.append(stamped)
        else:
            main_blocks.append(stamped)

    lines: list[str] = [f"# {title}", "", f"_Speaker: {speaker}_", ""]

    lines.extend(["## Main Talk", ""])
    if main_blocks:
        for block in main_blocks:
            lines.append(block)
            lines.append("")
    else:
        lines.append("No main-talk blocks detected.")
        lines.append("")

    if qna_blocks:
        lines.extend(["## Q&A", ""])
        for block in qna_blocks:
            lines.append(block)
            lines.append("")

    return "\n".join(lines).rstrip() + "\n"


def main() -> int:
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s | %(levelname)s | %(message)s",
        datefmt="%H:%M:%S",
    )
    logger = logging.getLogger("step2")

    args = build_parser().parse_args()
    input_path = Path(args.input).expanduser()
    if not input_path.exists() or not input_path.is_file():
        raise SystemExit(f"Input file not found: {input_path}")
    logger.info("[1/4] Input validated: %s", input_path)

    output_path = Path(args.output).expanduser() if args.output else input_path.with_suffix(".md")
    logger.info("[2/4] Output path resolved: %s", output_path)

    metadata, entries = parse_transcript(input_path)
    logger.info("[3/4] Parsed transcript entries: %s", len(entries))
    markdown = to_markdown(metadata, entries)
    output_path.write_text(markdown, encoding="utf-8-sig")

    logger.info("[4/4] Markdown written: %s", output_path)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
