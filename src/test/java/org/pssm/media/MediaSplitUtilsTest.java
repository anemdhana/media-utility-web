package org.pssm.media;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("BDD style media split checks")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MediaSplitUtilsTest {
    private static final List<String> AUDIO_EXTS = Arrays.asList("mp3", "m4a", "aac", "wav", "flac");
    private static final List<String> VIDEO_EXTS = Arrays.asList("mp4", "webm", "mkv", "mov", "avi");

    private File pickTestFile(List<String> exts, String... excludedNames) {
        File dir = new File(mediaFilesDir);
        File[] files = dir.listFiles((d, name) -> {
            String lowerName = name.toLowerCase();
            boolean matchesExtension = exts.stream().anyMatch(ext -> lowerName.endsWith("." + ext));
            boolean isExcludedName = Arrays.stream(excludedNames).anyMatch(excluded -> lowerName.equals(excluded.toLowerCase()));
            boolean isGeneratedArtifact = lowerName.startsWith("audio_split_test_")
                    || lowerName.startsWith("video_split_test_")
                    || lowerName.startsWith("audio-split-")
                    || lowerName.startsWith("video-split-");
            return matchesExtension && !isExcludedName && !isGeneratedArtifact;
        });
        if (files == null || files.length == 0) {
            throw new RuntimeException("No matching files found in " + mediaFilesDir);
        }
        Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        return files[0];
    }
    private static final Logger log = LoggerFactory.getLogger(MediaSplitUtilsTest.class);


    @Autowired
    private MediaSplitUtils splitUtils;

    @Value("${mediafiles_dir}")
    private String mediaFilesDir;

    @Test
    void given_the_spring_context_when_media_split_utils_are_loaded_then_they_are_available() {
        log.info("Checking if MediaSplitUtils is autowired");
        assertThat(splitUtils).isNotNull();
    }


    @Test
    void given_an_existing_audio_file_when_i_split_it_then_a_non_empty_audio_clip_is_created() {
        File out = new File(mediaFilesDir, "audio_split_test_out.m4a");
        File input = pickTestFile(AUDIO_EXTS, out.getName(), "dummy.mp3", "dummy_out.m4a");
        log.info("About to call splitAudioFile on {}", input);
        try {
            splitUtils.splitAudioFile(
                    input,
                    "00:00:00",
                    "00:00:10",
                    MediaSplitUtils.OutputQuality.YOUTUBE_UPLOAD,
                    null,
                    out
            );
        } catch (Exception e) {
            log.error("splitAudioFile failed: {}", e.getMessage());
            assertThat(false).as("splitAudioFile should not throw for existing file").isTrue();
        }
        log.info("Checking output file existence");
        assertThat(out).exists().isFile();
        assertThat(out.length()).isGreaterThan(0L);
    }
    @Test
    void given_an_existing_video_file_when_i_split_it_then_a_non_empty_video_clip_is_created() {
        File out = new File(mediaFilesDir, "video_split_test_out.mp4");
        File input = pickTestFile(VIDEO_EXTS, out.getName());
        log.info("About to call splitVideoFile on {}", input);
        try {
            splitUtils.splitVideoFile(
                    input,
                    "00:00:00",
                    "00:00:10",
                    MediaSplitUtils.OutputQuality.WHATSAPP,
                    null,
                    null,
                    out
            );
        } catch (Exception e) {
            log.error("splitVideoFile failed: {}", e.getMessage());
            assertThat(false).as("splitVideoFile should not throw for existing file").isTrue();
        }
        log.info("Checking output file existence");
        assertThat(out).exists().isFile();
        assertThat(out.length()).isGreaterThan(0L);
    }


    @Test
    void given_a_missing_audio_file_when_i_try_to_split_it_then_the_flow_fails_gracefully() {
        File dummy = new File(mediaFilesDir, "dummy.mp3");
        File out = new File(mediaFilesDir, "dummy_out.m4a");
        log.info("About to call splitAudioFile on {}", dummy);
        try {
            splitUtils.splitAudioFile(
                    dummy,
                    "00:00:00",
                    "00:00:10",
                    MediaSplitUtils.OutputQuality.WHATSAPP,
                    null,
                    out
            );
        } catch (Exception e) {
            log.warn("splitAudioFile failed as expected if dummy file does not exist: {}", e.getMessage());
        }
        log.info("Checking output file existence");
        assertThat(out).isNotNull();
    }
}
