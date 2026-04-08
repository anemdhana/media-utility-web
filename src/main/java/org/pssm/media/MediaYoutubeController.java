package org.pssm.media;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/media")
public class MediaYoutubeController {
    private static final String ALLOWED_QUALITIES = Arrays.stream(MediaSplitUtils.OutputQuality.values())
            .map(Enum::name)
            .collect(Collectors.joining(", "));

    private final MediaFileUtils mediaFileUtils;
    private final MediaPlaylistUtils mediaPlaylistUtils;

    public MediaYoutubeController(MediaFileUtils mediaFileUtils, MediaPlaylistUtils mediaPlaylistUtils) {
        this.mediaFileUtils = mediaFileUtils;
        this.mediaPlaylistUtils = mediaPlaylistUtils;
    }

    @PostMapping("/youtube/audio-extract")
    public Map<String, String> extractAudioFromYoutube(@RequestParam("videoId") String videoId,
                                                        @RequestParam(value = "quality", required = false) String quality) {
        String normalizedVideoId = videoId == null ? "" : videoId.trim();
        if (normalizedVideoId.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "videoId is required");
        }

        MediaSplitUtils.OutputQuality outputQuality = parseOutputQuality(quality);

        try {
            File downloaded = mediaFileUtils.extractAudioFromYoutubeVideoId(normalizedVideoId, outputQuality);
            return Map.of(
                    "videoId", normalizedVideoId,
                    "quality", outputQuality.name(),
                    "path", downloaded.getAbsolutePath(),
                    "fileName", downloaded.getName()
            );
        } catch (IOException | InterruptedException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to extract audio for videoId " + normalizedVideoId, ex);
        }
    }

    @PostMapping("/playlists/by-label")
    public Map<String, String> createPlaylistByLabel(@RequestParam("label") String label) {
        String normalizedLabel = label == null ? "" : label.trim();
        if (normalizedLabel.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "label is required");
        }

        try {
            File playlistFile = mediaPlaylistUtils.createPlaylistByLabel(normalizedLabel);
            List<File> tracks = mediaPlaylistUtils.getPlaylistTracks(playlistFile);
            return Map.of(
                    "label", normalizedLabel,
                    "playlistPath", playlistFile.getAbsolutePath(),
                    "playlistFileName", playlistFile.getName(),
                    "trackCount", String.valueOf(tracks.size())
            );
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (IOException | InterruptedException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to create playlist for label " + normalizedLabel, ex);
        }
    }

    @PostMapping("/playlists/copy")
    public Map<String, String> copyPlaylistToTargetFolder(@RequestParam("playlistName") String playlistName,
                                                           @RequestParam("targetFolder") String targetFolder) {
        String normalizedPlaylistName = playlistName == null ? "" : playlistName.trim();
        String normalizedTargetFolder = targetFolder == null ? "" : targetFolder.trim();

        if (normalizedPlaylistName.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "playlistName is required");
        }
        if (normalizedTargetFolder.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "targetFolder is required");
        }

        try {
            File copiedPlaylist = mediaPlaylistUtils.copyPlaylistAndTracksToFolder(normalizedPlaylistName, normalizedTargetFolder);
            List<File> copiedTracks = mediaPlaylistUtils.getPlaylistTracks(copiedPlaylist);
            return Map.of(
                    "playlistName", normalizedPlaylistName,
                    "targetFolder", new File(normalizedTargetFolder).getAbsolutePath(),
                    "copiedPlaylistPath", copiedPlaylist.getAbsolutePath(),
                    "copiedPlaylistFileName", copiedPlaylist.getName(),
                    "copiedTrackCount", String.valueOf(copiedTracks.size())
            );
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (IOException | InterruptedException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to copy playlist " + normalizedPlaylistName + " to target folder", ex);
        }
    }

    private MediaSplitUtils.OutputQuality parseOutputQuality(String quality) {
        if (quality == null || quality.isBlank()) {
            return MediaSplitUtils.OutputQuality.YOUTUBE_UPLOAD;
        }

        try {
            return MediaSplitUtils.OutputQuality.valueOf(quality.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unsupported quality. Allowed values: " + ALLOWED_QUALITIES);
        }
    }
}
