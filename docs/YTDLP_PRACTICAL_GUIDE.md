# yt-dlp Practical Guide: Downloading for Common Use Cases

This guide covers practical yt-dlp commands for downloading YouTube videos tailored for:
- WhatsApp sharing
- YouTube upload
- Music concert archiving (with replay gain)

---

## 1. Download for WhatsApp Sharing

WhatsApp has strict limits: max 16 MB per video, prefers MP4 (H.264/AAC), and short duration.

**Recommended command:**

```bash
yt-dlp -f "bv*[ext=mp4][height<=480]+ba[ext=m4a]/mp4" --merge-output-format mp4 --output "%(title).40s.%(ext)s" --max-filesize 15M --postprocessor-args "-vf scale=480:-2" "VIDEO_URL"
```

- `-f ...` = best video ≤480p + best audio, fallback to mp4
- `--merge-output-format mp4` = ensure MP4 output
- `--output ...` = short filename (max 40 chars)
- `--max-filesize 15M` = keep file under 16 MB
- `--postprocessor-args ...` = scale to 480p if needed

> For longer videos, trim with `--download-sections "*00:00:00-00:02:30"` to get the first 2.5 minutes.

---

## 2. Download for YouTube Upload

YouTube prefers high-quality MP4 (H.264/AAC), but will accept most formats. Avoid re-encoding if possible.

**Recommended command:**

```bash
yt-dlp -f "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best" --merge-output-format mp4 --output "%(title)s_upload.%(ext)s" "VIDEO_URL"
```

- `bestvideo[ext=mp4]+bestaudio[ext=m4a]` = best quality MP4 video/audio
- Fallbacks to best MP4 or best available
- `--merge-output-format mp4` = ensures MP4 output
- `--output ...` = descriptive filename

> For large files, you can compress with FFmpeg after download (see FFmpeg guide).

---

## 3. Download Music Concert with Replay Gain

Replay gain normalizes audio loudness for consistent playback. yt-dlp can pass options to FFmpeg for this.

**Recommended command:**

```bash
yt-dlp -f "bestaudio[ext=m4a]/bestaudio/best" --extract-audio --audio-format mp3 --audio-quality 0 --output "%(title)s_concert.%(ext)s" --postprocessor-args "-af replaygain=track" "VIDEO_URL"
```

- `--extract-audio` = download audio only
- `--audio-format mp3` = output as MP3 (widely compatible)
- `--audio-quality 0` = best quality
- `--postprocessor-args "-af replaygain=track"` = apply replay gain normalization

> For album/playlist, add `--yes-playlist` to process all tracks.

---

## 4. Tips

- Always update yt-dlp: `yt-dlp -U`
- For subtitles: add `--write-subs --sub-lang en --convert-subs srt`
- For metadata: add `--add-metadata`
- For trimming: use `--download-sections "*START-END"`

---

## References
- [yt-dlp Documentation](https://github.com/yt-dlp/yt-dlp#usage-and-options)
- [FFmpeg Audio Filters](https://ffmpeg.org/ffmpeg-filters.html#replaygain)

---

This guide provides practical yt-dlp recipes for common sharing and archiving scenarios. Adjust options as needed for your workflow.
