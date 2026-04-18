#!/usr/bin/env python3
"""yt_playlist_thumbnails.py — Download high-quality YouTube thumbnails for a playlist
and insert them into corresponding Kindle markdown files.

Usage:
    python scripts/yt_playlist_thumbnails.py --media-dir "C:\\Users\\dhana\\media-files\\Jnanodayam-Swasa Maha Vidya"
    python scripts/yt_playlist_thumbnails.py --media-dir "..." --download-only
    python scripts/yt_playlist_thumbnails.py --media-dir "..." --insert-only

Dependencies: None (stdlib only — urllib, json, pathlib)
"""

from __future__ import annotations

import argparse
import json
import logging
import re
import sys
import urllib.error
import urllib.request
from pathlib import Path

logging.basicConfig(level=logging.INFO, format="%(levelname)s: %(message)s")
log = logging.getLogger(__name__)

# YouTube thumbnail URLs in descending quality order
THUMB_URLS = [
    "https://img.youtube.com/vi/{vid}/maxresdefault.jpg",   # 1280×720
    "https://img.youtube.com/vi/{vid}/sddefault.jpg",        # 640×480
    "https://img.youtube.com/vi/{vid}/hqdefault.jpg",        # 480×360
]

# Playlist ID (hardcoded as requested)
PLAYLIST_ID = "PLT6lIcOhPFQpBrEd6H4rX5ZPd3govnYAx"


def discover_episodes(media_dir: Path) -> list[dict]:
    """Scan _punctuated_v7.txt files in media_dir and extract episode number + video ID."""
    pattern = re.compile(
        r"^(\d{2})_.*-([A-Za-z0-9_-]{11})-compact_size_speech_punctuated_v7\.txt$"
    )
    episodes = []
    for f in sorted(media_dir.glob("*_punctuated_v7.txt")):
        m = pattern.match(f.name)
        if m:
            ep_num = m.group(1)
            video_id = m.group(2)
            episodes.append({"ep": ep_num, "video_id": video_id, "source": f.name})
    return episodes


def download_thumbnail(video_id: str, out_path: Path) -> bool:
    """Download the highest-quality available thumbnail. Returns True on success."""
    for url_template in THUMB_URLS:
        url = url_template.format(vid=video_id)
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
            with urllib.request.urlopen(req, timeout=30) as resp:
                data = resp.read()
                # YouTube returns a small placeholder for missing maxresdefault (120 bytes gray image)
                if len(data) < 5000:
                    log.debug("  Skipping %s (placeholder, %d bytes)", url, len(data))
                    continue
                out_path.write_bytes(data)
                log.info("  Downloaded: %s (%d KB)", out_path.name, len(data) // 1024)
                return True
        except urllib.error.HTTPError as e:
            log.debug("  HTTP %d for %s", e.code, url)
            continue
        except Exception as e:
            log.debug("  Error for %s: %s", url, e)
            continue
    return False


def download_all(media_dir: Path, episodes: list[dict]) -> dict[str, Path]:
    """Download thumbnails for all episodes. Returns {ep_num: image_path}."""
    images_dir = media_dir / "images"
    images_dir.mkdir(exist_ok=True)
    results = {}
    for ep in episodes:
        fname = f"{ep['ep']}_thumbnail.jpg"
        out_path = images_dir / fname
        if out_path.exists() and out_path.stat().st_size > 5000:
            log.info("  [%s] Already exists: %s", ep["ep"], fname)
            results[ep["ep"]] = out_path
            continue
        log.info("[%s] Downloading thumbnail for video %s ...", ep["ep"], ep["video_id"])
        if download_thumbnail(ep["video_id"], out_path):
            results[ep["ep"]] = out_path
        else:
            log.warning("[%s] Failed to download thumbnail for %s", ep["ep"], ep["video_id"])
    return results


def find_kindle_files(media_dir: Path) -> list[Path]:
    """Find all Telugu (_kindle.md) and English (_kindle_english.md) files."""
    files = []
    files.extend(sorted(media_dir.glob("*_kindle.md")))
    files.extend(sorted(media_dir.glob("*_kindle_english.md")))
    return files


def extract_ep_num(filepath: Path) -> str | None:
    """Extract 2-digit episode number from kindle filename."""
    m = re.match(r"^(\d{2})_", filepath.name)
    return m.group(1) if m else None


def insert_thumbnail_into_md(md_path: Path, image_rel_path: str, video_id: str) -> bool:
    """Insert thumbnail image + YouTube link after the first H1 heading line."""
    content = md_path.read_text(encoding="utf-8")

    # Skip if already has a thumbnail
    if "![Thumbnail" in content or "![Episode" in content:
        log.info("  [%s] Already has thumbnail — skipping", md_path.name)
        return False

    # Find the first H1 line (# Title...)
    lines = content.split("\n")
    insert_idx = None
    for i, line in enumerate(lines):
        if line.startswith("# "):
            insert_idx = i + 1
            break

    if insert_idx is None:
        log.warning("  [%s] No H1 heading found — skipping", md_path.name)
        return False

    # Build the image + link block
    yt_url = f"https://www.youtube.com/watch?v={video_id}"
    image_block = [
        "",
        f"[![Episode Thumbnail]({image_rel_path})]({yt_url})",
        "",
    ]

    lines[insert_idx:insert_idx] = image_block
    md_path.write_text("\n".join(lines), encoding="utf-8")
    log.info("  [%s] Inserted thumbnail", md_path.name)
    return True


def insert_all(media_dir: Path, episodes: list[dict], image_paths: dict[str, Path]):
    """Insert thumbnail references into all kindle markdown files."""
    kindle_files = find_kindle_files(media_dir)
    ep_video_map = {ep["ep"]: ep["video_id"] for ep in episodes}

    updated = 0
    for md_path in kindle_files:
        ep_num = extract_ep_num(md_path)
        if not ep_num or ep_num not in image_paths or ep_num not in ep_video_map:
            log.debug("  Skipping %s (no matching thumbnail)", md_path.name)
            continue

        image_path = image_paths[ep_num]
        # Use relative path from md file to images/XX_thumbnail.jpg
        image_rel = f"images/{image_path.name}"
        video_id = ep_video_map[ep_num]

        if insert_thumbnail_into_md(md_path, image_rel, video_id):
            updated += 1

    log.info("Updated %d markdown files with thumbnails.", updated)


def main():
    parser = argparse.ArgumentParser(description="Download YouTube playlist thumbnails and insert into kindle md files")
    parser.add_argument("--media-dir", required=True, help="Path to the media files directory")
    parser.add_argument("--download-only", action="store_true", help="Only download thumbnails, don't modify md files")
    parser.add_argument("--insert-only", action="store_true", help="Only insert into md files (thumbnails must exist)")
    args = parser.parse_args()

    media_dir = Path(args.media_dir)
    if not media_dir.is_dir():
        log.error("Directory not found: %s", media_dir)
        sys.exit(1)

    # Discover episodes from source filenames
    episodes = discover_episodes(media_dir)
    if not episodes:
        log.error("No _punctuated_v7.txt source files found in %s", media_dir)
        sys.exit(1)

    log.info("Found %d episodes:", len(episodes))
    for ep in episodes:
        log.info("  [%s] video_id=%s", ep["ep"], ep["video_id"])

    images_dir = media_dir / "images"

    if not args.insert_only:
        log.info("\n--- Downloading thumbnails to %s ---", images_dir)
        image_paths = download_all(media_dir, episodes)
        log.info("Downloaded %d thumbnails.\n", len(image_paths))
    else:
        # Build image_paths from existing files
        image_paths = {}
        for ep in episodes:
            p = images_dir / f"{ep['ep']}_thumbnail.jpg"
            if p.exists():
                image_paths[ep["ep"]] = p

    if not args.download_only:
        log.info("--- Inserting thumbnails into kindle markdown files ---")
        insert_all(media_dir, episodes, image_paths)

    log.info("\nDone!")


if __name__ == "__main__":
    main()
