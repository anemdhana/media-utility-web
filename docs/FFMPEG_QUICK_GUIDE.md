# FFmpeg Quick Guide: Essential Features

This guide summarizes some of the most useful FFmpeg features for everyday video/audio processing, based on the official documentation: <https://ffmpeg.org/ffmpeg.html>

---

## 1. Split Video by Start and End Time

You can extract a segment from a video using the `-ss` (start time) and `-to` (end time) options.

**Example:** Extract from 00:01:00 to 00:02:30

```bash
ffmpeg -i input.mp4 -ss 00:01:00 -to 00:02:30 -c copy output_clip.mp4
```

- `-ss` = start time (seek to this position)
- `-to` = end time (stop at this position)
- `-c copy` = copy streams without re-encoding (fast, lossless)

> For more accurate cuts, place `-ss` before `-i` for fast seeking, or after `-i` for frame-accurate seeking (may be slower).

---

## 2. Select Best Video and Audio Streams

When downloading or processing media with multiple streams, you can select the highest quality video and audio.

**Example:** Select best video and audio streams (when using with tools like youtube-dl or yt-dlp)

```bash
yt-dlp -f "bv*+ba/best" URL
```

- `bv*` = best video
- `ba` = best audio
- `/best` = fallback to best available

**With FFmpeg directly:**

If your file has multiple streams, you can map the best ones:

```bash
ffmpeg -i input.mkv -map 0:v:0 -map 0:a:0 -c copy output.mp4
```

- `-map 0:v:0` = first (usually best) video stream
- `-map 0:a:0` = first (usually best) audio stream

---

## 3. Maintain Quality with Smaller File Size

Use the `-crf` (Constant Rate Factor) option for a good balance between quality and size. Lower values = higher quality, higher values = smaller size.

**Example:**

```bash
ffmpeg -i input.mp4 -c:v libx264 -crf 23 -preset fast -c:a aac -b:a 128k output_small.mp4
```

- `-c:v libx264` = use H.264 video codec (widely compatible)
- `-crf 23` = default quality (lower for better quality, e.g., 18; higher for smaller size, e.g., 28)
- `-preset fast` = encoding speed (use `medium` for better compression, `ultrafast` for speed)
- `-c:a aac` = AAC audio codec
- `-b:a 128k` = audio bitrate

**Tips:**
- For even smaller files, try `-crf 28` or use `libx265` (HEVC) if compatibility allows.
- Always test output quality before deleting originals.

---

## 4. Combine All: Split, Best Streams, Small Size

**Example:** Extract a segment, select best streams, and compress:

```bash
ffmpeg -ss 00:00:30 -to 00:02:00 -i input.mkv -map 0:v:0 -map 0:a:0 -c:v libx264 -crf 24 -preset medium -c:a aac -b:a 128k output_clip_small.mp4
```

---

## 5. Useful References

- [FFmpeg Documentation](https://ffmpeg.org/ffmpeg.html)
- [FFmpeg Filters](https://ffmpeg.org/ffmpeg-filters.html)
- [CRF Guide](https://trac.ffmpeg.org/wiki/Encode/H.264)

---

This guide is a quick reference for common FFmpeg tasks. For more advanced options, see the official docs.