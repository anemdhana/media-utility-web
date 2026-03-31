# Media Utility Web

Media Utility Web is a new project started from scratch.

It currently provides:

- Spring Boot backend (Java 21)
- React frontend powered by Vite
- A baseline health endpoint for connectivity checks

## Project Layout

- `src/main/java` - backend application and APIs
- `src/main/resources` - backend configuration
- `src/test/java` - backend tests
- `frontend/` - React + Vite frontend
- `config/tools` - optional local tool setup scripts
- `config/vlc` - optional VLC sample configuration

## Prerequisites

- Java 21+
- Maven 3.8+
- Node.js 18+
- npm 9+

## Backend: Run

```bash
mvn clean spring-boot:run
```

Backend default URL:

- `http://localhost:8080`

Health endpoint:

- `GET http://localhost:8080/api/health`

## Frontend: Run

```bash
cd frontend
npm install
npm run dev
```

Frontend default URL:

- `http://localhost:5173`

The frontend calls backend APIs through the Vite `/api` proxy in local development.

## Backend: Test

```bash
mvn test
```

## Frontend: Build

```bash
cd frontend
npm run build
```

## Prompts Ready
We are going to start fresh implementation of
1. Extracting audio from the given youtube videoId
1.1. Take the startTime, endTime, label parameters, etc matching as it is same as in this file, but do not bind any API calls for now



We need controller to extract audio from the given youtube videoId, startTime, endTime, and label parameters.
For this, setting up tools is required to make use of yt-dlp and ffmpeg in the project.
Log the important 

Add these properties to be used later.

download_location=${user.home}/pssm-music-playlist-downloads
tools_location=${user.home}/pssm-music-playlist-tools

audio_extract={toolsLocation}yt-dlp -f bestaudio -x --audio-format m4a --audio-quality 128K --embed-metadata --embed-thumbnail --convert-thumbnails jpg "https://www.youtube.com/watch?v={videoId}"
convert_to_m4a={toolsLocation}ffmpeg -i "{inputFile}" -ss 0 -c:a aac -b:a 128k -ar 44100 -ac 2 -c:v copy -disposition:v:0 attached_pic -movflags +faststart "{outputFile}"
video_extract={toolsLocation}yt-dlp -f "bestvideo+bestaudio/best" -o "%(id)s.%(ext)s" "https://www.youtube.com/watch?v={videoId}"
video_resize={toolsLocation}ffmpeg -i "{inputFile}" -vf "scale=-2:480:force_original_aspect_ratio=decrease,scale=trunc(iw/2)*2:trunc(ih/2)*2" -c:v libx264 -preset medium -crf 27 -c:a aac -b:a 96k -movflags +faststart "{outputFile}"
video_resize_fragment={toolsLocation}ffmpeg -ss {startTime} -to {endTime} -i "{inputFile}" -vf "scale=-2:480:force_original_aspect_ratio=decrease,scale=trunc(iw/2)*2:trunc(ih/2)*2" -c:v libx264 -preset medium -crf 27 -c:a aac -b:a 96k -movflags +faststart "{outputFile}"
