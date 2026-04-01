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

## Next Steps

- Verify the labels flow end-to-end on real media files and confirm metadata updates behave correctly.
- Run the complete backend test suite with `mvn test` and ensure there are no errors before the next release or push.

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


1. Let us use yt-dlp, ffmpeg,replaygain tools in our project.
tools_location=${user.home}/pssm-music-playlist-tools
mediafiles_dir=${user.home}/media-files

2. Let us have utility class to support the following.
2.1. List down all media files in mediafiles_dir
2.2. Filtering to hapen based on input filter criteria, file name (anywhere match, case insensitive), label. Understand what is mean by label input by looking at pssm-music-playlist project.
2.3. get the total time duration of the media file.
2.4. Size of the media file.
2.5. get the list of labels of the medial file.


Let us go with our first implementation in the backend.
2. service layer: List down all media files.
2.1. Filtering to hapen based on input filter criteria, file name (anywhere match, case insensitive), label. Understand what is mean by label input by looking at pssm-music-playlist project.


1. Let us use yt-dlp, ffmpeg,replaygain tools in our project.
tools_location=${user.home}/pssm-music-playlist-tools
mediafiles_dir=${user.home}/media-files

2. Let us have utility class to support the following.
2.1. List down all media files in mediafiles_dir
2.2. Filtering to hapen based on input filter criteria, file name (anywhere match, case insensitive), label. Understand what is mean by label input by looking at pssm-music-playlist project.
2.3. get the total time duration of the media file.
2.4. Size of the media file.
2.5. get the list of labels of the medial file.

3. One more utility class..
3.1. Split the media file by the given start and end Times, also accept the parameter - output quality of the file(BEST FOR SHARING IN WHATSAPP, YOUTUBE_UPLOAD, MUSIC_CONCERT(use replay gain feature)), use appropriate file format (webm, mp4, m4a, mp3, etc) based on the media file type. Also have the default file format (m4a, mp4), configure in spring properties.
3.2. For video media file, same as 3.1, but also accept parameter to crop the video.
Note: Ensure to copy all metadata information of the track, thumbnail, description, tags/labels, etc..


4. One more utility class
4.1. create the m3u8 playlist file for the given list of media files, apply replaygain for album/playlist.
4.2. get the replaygain information/details for the given playlist file, so that user will understand whether all tracks have gain normalized, etc information.
4.3. Add/remove tracks in the given playlist file.
4.4. Clear all tracks in the given playlist file.
4.5. Apply replaygain for album/playlist.
4.6. get total time duration of the given playlist file.
4.7. More to come soon..


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
video_resize_fragment={toolsLocation}ffmpeg -ss {startTime} -to {endTime} -i "{inputFile}" -vf "scale=-2:480:force_original_aspect_ratio=decrease,scale=trunc(iw/2)*2:trunc(ih/2)*2" -c:v libx264 -preset medium -crf 27 -c:a aac -b:a 96k -movflags +faststart "{outputFile}"audio_extract={toolsLocation}yt-dlp -f bestaudio -x --audio-format m4a --audio-quality 128K --embed-metadata --embed-thumbnail --convert-thumbnails jpg "https://www.youtube.com/watch?v={videoId}"
convert_to_m4a={toolsLocation}ffmpeg -i "{inputFile}" -ss 0 -c:a aac -b:a 128k -ar 44100 -ac 2 -c:v copy -disposition:v:0 attached_pic -movflags +faststart "{outputFile}"
video_extract={toolsLocation}yt-dlp -f "bestvideo+bestaudio/best" -o "%(id)s.%(ext)s" "https://www.youtube.com/watch?v={videoId}"
video_resize={toolsLocation}ffmpeg -i "{inputFile}" -vf "scale=-2:480:force_original_aspect_ratio=decrease,scale=trunc(iw/2)*2:trunc(ih/2)*2" -c:v libx264 -preset medium -crf 27 -c:a aac -b:a 96k -movflags +faststart "{outputFile}"
video_resize_fragment={toolsLocation}ffmpeg -ss {startTime} -to {endTime} -i "{inputFile}" -vf "scale=-2:480:force_original_aspect_ratio=decrease,scale=trunc(iw/2)*2:trunc(ih/2)*2" -c:v libx264 -preset medium -crf 27 -c:a aac -b:a 96k -movflags +faststart "{outputFile}"
