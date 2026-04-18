"""
Batch-process all videos from a camera directory through the
whatsapp_compact_video BDD scenario, then delete originals on success.

Usage:
    python scripts/batch_whatsapp_compact.py [--dry-run] [--camera-dir DIR]
"""

import argparse
import os
import re
import subprocess
import sys
from pathlib import Path

VIDEO_EXTENSIONS = {".mp4", ".mkv", ".avi", ".mov", ".3gp", ".wmv", ".flv", ".webm"}

FFMPEG = r"C:\Users\dhana\Downloads\ffmpeg.exe"
DEFAULT_CAMERA_DIR = r"C:\Users\dhana\Realme_8i_backup\DCIM\Camera"
PROJECT_DIR = Path(__file__).resolve().parent.parent          # media-utility-web
PROPS_FILE = PROJECT_DIR / "src" / "test" / "resources" / "media-input.properties"

MVN_CMD = [
    "mvn", "-f", str(PROJECT_DIR / "pom.xml"),
    "-Dtest=MediaBddTest",
    "-Dcucumber.filter.tags=@split-media-input-whatsapp-compact-video and not @ignore",
    "test",
]


def get_video_duration(video_path: str) -> str:
    """Return duration as HH:MM:SS using ffmpeg -i (works when ffprobe is unavailable)."""
    cmd = [FFMPEG, "-i", video_path]
    # ffmpeg -i without output exits with code 1 but prints duration in stderr header
    result = subprocess.run(cmd, capture_output=True, text=True)
    match = re.search(r"Duration:\s*(\d{2}):(\d{2}):(\d{2})\.\d+", result.stderr)
    if not match:
        raise RuntimeError(f"Could not parse duration from ffmpeg output for {video_path}")
    h, m, s = int(match.group(1)), int(match.group(2)), int(match.group(3))
    return f"{h:02d}:{m:02d}:{s:02d}"


def update_properties(input_file: str, end_time: str) -> None:
    """Rewrite the two dynamic lines in media-input.properties."""
    text = PROPS_FILE.read_text(encoding="utf-8")

    # Use forward slashes for Java properties compatibility
    input_file_fwd = input_file.replace("\\", "/")

    text = re.sub(
        r"(scenario\.whatsapp_compact_video\.inputFile=).*",
        rf"\g<1>{input_file_fwd}",
        text,
    )
    text = re.sub(
        r"(scenario\.whatsapp_compact_video\.endTime=).*",
        rf"\g<1>{end_time}",
        text,
    )
    PROPS_FILE.write_text(text, encoding="utf-8")


def collect_videos(camera_dir: str) -> list[Path]:
    """Return sorted list of video files in the camera directory."""
    d = Path(camera_dir)
    if not d.is_dir():
        print(f"ERROR: directory not found: {camera_dir}", file=sys.stderr)
        sys.exit(1)
    videos = sorted(
        p for p in d.iterdir()
        if p.is_file() and p.suffix.lower() in VIDEO_EXTENSIONS
    )
    return videos


def main() -> None:
    parser = argparse.ArgumentParser(description="Batch WhatsApp compact video processing")
    parser.add_argument("--camera-dir", default=DEFAULT_CAMERA_DIR, help="Source video directory")
    parser.add_argument("--dry-run", action="store_true", help="Show plan without executing")
    args = parser.parse_args()

    videos = collect_videos(args.camera_dir)
    if not videos:
        print("No video files found.")
        return

    print(f"Found {len(videos)} video(s) in {args.camera_dir}\n")

    succeeded: list[Path] = []
    failed: list[Path] = []

    for i, video in enumerate(videos, 1):
        print(f"[{i}/{len(videos)}] Processing: {video.name}")

        # 1. Get duration via ffprobe
        try:
            end_time = get_video_duration(str(video))
        except Exception as e:
            print(f"  SKIP – ffprobe failed: {e}")
            failed.append(video)
            continue

        print(f"  Duration: {end_time}")

        if args.dry_run:
            print(f"  DRY-RUN: would run scenario with inputFile={video}, endTime={end_time}")
            continue

        # 2. Update properties file
        update_properties(str(video), end_time)

        # 3. Run Maven BDD scenario
        print(f"  Running whatsapp_compact_video scenario …")
        result = subprocess.run(MVN_CMD, cwd=str(PROJECT_DIR))

        if result.returncode != 0:
            print(f"  FAILED – Maven returned {result.returncode}")
            failed.append(video)
            continue

        print(f"  SUCCESS – deleting original: {video.name}")

        # 4. Delete original video
        try:
            video.unlink()
            succeeded.append(video)
        except OSError as e:
            print(f"  WARNING – delete failed: {e}")
            failed.append(video)

    # Summary
    print(f"\n{'='*60}")
    print(f"Processed: {len(succeeded)} succeeded, {len(failed)} failed out of {len(videos)} total")
    if failed:
        print("Failed files:")
        for f in failed:
            print(f"  - {f.name}")


if __name__ == "__main__":
    main()
