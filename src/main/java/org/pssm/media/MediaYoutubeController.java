package org.pssm.media;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    public Object extractAudioFromYoutube(@RequestParam("videoId") String videoId,
                                          @RequestParam(value = "quality", required = false) String quality) {
        List<String> videoIds = parseVideoIds(videoId);
        if (videoIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "videoId is required");
        }

        MediaSplitUtils.OutputQuality outputQuality = parseOutputQuality(quality);

        if (videoIds.size() == 1) {
            String singleId = videoIds.get(0);
            try {
                File downloaded = mediaFileUtils.extractAudioFromYoutubeVideoId(singleId, outputQuality);
                return Map.of(
                        "videoId", singleId,
                        "quality", outputQuality.name(),
                        "path", downloaded.getAbsolutePath(),
                        "fileName", downloaded.getName()
                );
            } catch (IOException | InterruptedException ex) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Failed to extract audio for videoId " + singleId, ex);
            }
        }

        List<Map<String, String>> results = extractAudioBatchParallel(videoIds, outputQuality);
        return Map.of(
                "quality", outputQuality.name(),
                "results", results
        );
    }

    /**
     * Runs one yt-dlp/ffmpeg pipeline per video id concurrently (virtual threads) while preserving
     * response order to match the request list.
     */
    private List<Map<String, String>> extractAudioBatchParallel(List<String> videoIds,
                                                                MediaSplitUtils.OutputQuality outputQuality) {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<Map<String, String>>> futures = videoIds.stream()
                    .map(id -> CompletableFuture.supplyAsync(() -> extractSingleBatchEntry(id, outputQuality), executor))
                    .toList();
            List<Map<String, String>> results = new ArrayList<>(videoIds.size());
            for (CompletableFuture<Map<String, String>> future : futures) {
                try {
                    results.add(future.join());
                } catch (CompletionException ex) {
                    throw unwrapBatchExtractFailure(ex);
                }
            }
            return results;
        }
    }

    private Map<String, String> extractSingleBatchEntry(String id, MediaSplitUtils.OutputQuality outputQuality) {
        try {
            File downloaded = mediaFileUtils.extractAudioFromYoutubeVideoId(id, outputQuality);
            return Map.of(
                    "videoId", id,
                    "path", downloaded.getAbsolutePath(),
                    "fileName", downloaded.getName()
            );
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to extract audio for videoId " + id, ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new UncheckedIOException(
                    new IOException("Interrupted while extracting audio for videoId " + id, ex));
        }
    }

    private static ResponseStatusException unwrapBatchExtractFailure(CompletionException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof UncheckedIOException) {
            IOException io = ((UncheckedIOException) cause).getCause();
            return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    cause.getMessage(), io != null ? io : cause);
        }
        if (cause instanceof IOException) {
            IOException io = (IOException) cause;
            return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, io.getMessage(), io);
        }
        return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                "Failed to extract audio", cause != null ? cause : ex);
    }

    private static List<String> parseVideoIds(String videoId) {
        if (videoId == null || videoId.isBlank()) {
            return List.of();
        }
        return Arrays.stream(videoId.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
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
