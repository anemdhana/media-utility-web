package org.pssm.media;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.File;
import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/media")
public class MediaYoutubeController {

    private final MediaFileUtils mediaFileUtils;

    public MediaYoutubeController(MediaFileUtils mediaFileUtils) {
        this.mediaFileUtils = mediaFileUtils;
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

    private MediaSplitUtils.OutputQuality parseOutputQuality(String quality) {
        if (quality == null || quality.isBlank()) {
            return MediaSplitUtils.OutputQuality.YOUTUBE_UPLOAD;
        }

        try {
            return MediaSplitUtils.OutputQuality.valueOf(quality.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unsupported quality. Allowed values: WHATSAPP, YOUTUBE_UPLOAD, MUSIC_CONCERT");
        }
    }
}
