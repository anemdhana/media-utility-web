# Media Support Summary

This document summarizes the media file types currently handled by the backend utilities and the supported operations.

---

## Supported Media File Types

### Audio files
The current utilities recognize these audio formats:

- `.mp3`
- `.m4a`
- `.aac`
- `.wav`
- `.flac`

### Video files
The current utilities recognize these video formats:

- `.mp4`
- `.webm`
- `.mkv`
- `.mov`
- `.avi`

> Internally, `listAllMediaFiles()` scans the configured `mediafiles_dir` recursively and includes the above extensions.

---

## Supported Operations on Audio and Video Files

### Common media file operations
These work for both audio and video files where applicable:

- **List all media files** from the configured media directory
- **Filter media files** by:
  - filename substring match (case-insensitive)
  - label/comment metadata
- **Get duration** of a media file
- **Get file size**
- **Read labels** from metadata comment tag
- **Add labels** to metadata comment tag
- **Check whether required labels exist** on a file
- **Get all unique labels** across the media library

---

## Audio-specific operations

### Split audio file
Supported through `MediaSplitUtils#splitAudioFile(...)`.

#### Input
- source audio file
- `startTime`
- `endTime`
- output quality preset:
  - `WHATSAPP`
  - `YOUTUBE_UPLOAD`
  - `MUSIC_CONCERT`
- output target file

#### Output formats currently supported
- `.mp3`
- `.m4a`

#### Behavior
- replaces the target if it already exists
- safely handles same input/output path using a temporary file first
- preserves metadata and copies sidecar files like:
  - `.labels`
  - `.description`

---

## Video-specific operations

### Split video file
Supported through `MediaSplitUtils#splitVideoFile(...)`.

#### Input
- source video file
- `startTime`
- `endTime`
- output quality preset:
  - `WHATSAPP`
  - `YOUTUBE_UPLOAD`
  - `MUSIC_CONCERT`
- optional crop filter
- output target file

#### Output format currently supported
- `.mp4`

#### Behavior
- replaces the target if it already exists
- safely handles same input/output path using a temporary file first
- can keep the original video style more closely unless an explicit crop is provided
- preserves metadata and copies sidecar files like:
  - `.labels`
  - `.description`

---

## Playlist operations

### M3U8 playlist support
Supported through `MediaPlaylistUtils`.

#### Supported playlist actions
- **Create** an `.m3u8` playlist from a given list of media files
- **Read/list tracks** from an existing playlist
- **Add tracks** to a playlist
- **Remove tracks** from a playlist
- **Clear all tracks** while keeping the playlist header
- **Apply ReplayGain** to audio tracks referenced by the playlist
- **Get ReplayGain report/details** for the tracks in a playlist
- **Get total playlist duration** in seconds

#### ReplayGain report details include
- track file name
- `replaygain_track_gain`
- `replaygain_album_gain`
- whether the track appears normalized
- missing-tag / file-not-found notes

---

## Command/configuration support

The media and playlist flows are configuration-driven via Spring properties in `application.properties`, including:

- `audio_split`
- `video_split`
- `audio_extract`
- `video_extract`
- `video_resize`
- `video_resize_fragment`
- `convert_to_m4a`
- `calculate_replaygain`

The execution is routed through `MediaCommandRunner`.

---

## Current scope note

At present, the labels flow is simplified to **adding and reading labels**, along with checking whether a file contains required labels.
