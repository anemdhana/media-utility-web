# Media Utility Web

Media Utility Web is a new project started from scratch.

It currently provides:

- Spring Boot backend (Java 21)
- Media file, split, playlist, and ReplayGain utilities
- BDD/Cucumber feature tests for backend flows
- A baseline health endpoint for connectivity checks

## Project Layout

- `src/main/java` - backend application and media utilities
- `src/main/resources` - backend configuration
- `src/test/java` - JUnit and Cucumber step definitions
- `src/test/resources/features` - Gherkin feature files
- `scripts/` - helper scripts for running tagged BDD tests
- `docs/` - quick guides and media support notes
- `config/tools` - optional local tool setup scripts
- `config/vlc` - optional VLC sample configuration

## Prerequisites

- Java 21+
- Maven 3.8+

## Backend: Run

```bash
mvn clean spring-boot:run
```

Backend default URL:

- `http://localhost:8080`

Health endpoint:

- `GET http://localhost:8080/api/health`

## Backend: Test

```bash
mvn test
```

## BDD Feature Tests

Run the full BDD suite:

```powershell
mvn "-Dtest=MediaBddTest" test
```

Run one tagged scenario or feature directly:

```powershell
mvn "-Dcucumber.filter.tags=@split-jynedpzesle-whatsapp" "-Dtest=MediaBddTest" test
```

Use the helper scripts for easier execution on Windows:

```cmd
scripts\run-bdd-by-tag.cmd
scripts\run-bdd-by-tag.cmd -ListOnly
scripts\run-bdd-by-tag.cmd -Tag split-jynedpzesle-whatsapp
```

Available example tags include:
- `@split-jynedpzesle-whatsapp`
- `@split-audio-basic`
- `@split-video-basic`
- `@list-media-files`
- `@playlist-track-management`

## Next Steps

- Verify the labels flow end-to-end on real media files and confirm metadata updates behave correctly.
- Run the complete backend test suite with `mvn test` and ensure there are no errors before the next release or push.

download_location=${user.home}/pssm-music-playlist-downloads
tools_location=${user.home}/pssm-music-playlist-tools

audio_extract={toolsLocation}yt-dlp -f bestaudio -x --audio-format m4a --audio-quality 128K --embed-metadata --embed-thumbnail --convert-thumbnails jpg "https://www.youtube.com/watch?v={videoId}"
convert_to_m4a={toolsLocation}ffmpeg -i "{inputFile}" -ss 0 -c:a aac -b:a 128k -ar 44100 -ac 2 -c:v copy -disposition:v:0 attached_pic -movflags +faststart "{outputFile}"
video_extract={toolsLocation}yt-dlp -f "bestvideo+bestaudio/best" -o "%(id)s.%(ext)s" "https://www.youtube.com/watch?v={videoId}"
video_resize={toolsLocation}ffmpeg -i "{inputFile}" -vf "scale=-2:480:force_original_aspect_ratio=decrease,scale=trunc(iw/2)*2:trunc(ih/2)*2" -c:v libx264 -preset medium -crf 27 -c:a aac -b:a 96k -movflags +faststart "{outputFile}"
video_resize_fragment={toolsLocation}ffmpeg -ss {startTime} -to {endTime} -i "{inputFile}" -vf "scale=-2:480:force_original_aspect_ratio=decrease,scale=trunc(iw/2)*2:trunc(ih/2)*2" -c:v libx264 -preset medium -crf 27 -c:a aac -b:a 96k -movflags +faststart "{outputFile}"audio_extract={toolsLocation}yt-dlp -f bestaudio -x --audio-format m4a --audio-quality 128K --embed-metadata --embed-thumbnail --convert-thumbnails jpg "https://www.youtube.com/watch?v={videoId}"
convert_to_m4a={toolsLocation}ffmpeg -i "{inputFile}" -ss 0 -c:a aac -b:a 128k -ar 44100 -ac 2 -c:v copy -disposition:v:0 attached_pic -movflags +faststart "{outputFile}"
video_extract={toolsLocation}yt-dlp -f "bestvideo+bestaudio/best" -o "%(id)s.%(ext)s" "https://www.youtube.com/watch?v={videoId}"
video_resize={toolsLocation}ffmpeg -i "{inputFile}" -vf "scale=-2:480:force_original_aspect_ratio=decrease,scale=trunc(iw/2)*2:trunc(ih/2)*2" -c:v libx264 -preset medium -crf 27 -c:a aac -b:a 96k -movflags +faststart "{outputFile}"
video_resize_fragment={toolsLocation}ffmpeg -ss {startTime} -to {endTime} -i "{inputFile}" -vf "scale=-2:480:force_original_aspect_ratio=decrease,scale=trunc(iw/2)*2:trunc(ih/2)*2" -c:v libx264 -preset medium -crf 27 -c:a aac -b:a 96k -movflags +faststart "{outputFile}"
