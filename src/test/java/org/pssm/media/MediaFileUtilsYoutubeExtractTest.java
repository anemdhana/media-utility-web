package org.pssm.media;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MediaFileUtilsYoutubeExtractTest {

    @TempDir
    Path tempDir;

    private MediaCommandRunner commandRunner;
    private MediaFileUtils utils;
    private Path mediaDir;

    @BeforeEach
    void setUp() throws Exception {
        commandRunner = mock(MediaCommandRunner.class);

        mediaDir = tempDir.resolve("media");
        Path toolsDir = tempDir.resolve("tools");

        Files.createDirectories(mediaDir);
        Files.createDirectories(toolsDir);

        utils = new MediaFileUtils(mediaDir.toString(), toolsDir.toString(), commandRunner);
    }

    @Test
    void given_existing_download_when_extract_audio_then_it_reuses_existing_file_without_downloading_again() throws Exception {
        String videoId = "abc123XYZ";
        Path existingFile = mediaDir.resolve("sample-title-" + videoId + ".m4a");
        Files.writeString(existingFile, "audio");

        File result = utils.extractAudioFromYoutubeVideoId(videoId);

        assertThat(result).isEqualTo(existingFile.toFile());
        assertThat(result.getName()).contains(videoId);
        verify(commandRunner, never()).runAudioExtract(anyString());
    }

    @Test
    void given_no_existing_download_when_extract_audio_then_it_downloads_and_returns_downloaded_file_path() throws Exception {
        String videoId = "To0lu_BrXTk";

        when(commandRunner.runAudioExtract(videoId)).thenAnswer(invocation -> {
            Path downloaded = mediaDir.resolve("deep-meditation-" + videoId + ".m4a");
            Files.writeString(downloaded, "audio");
            return 0;
        });

        File result = utils.extractAudioFromYoutubeVideoId(videoId);

        assertThat(result).isNotNull();
        assertThat(result.exists()).isTrue();
        assertThat(result.getAbsolutePath()).contains(mediaDir.toString());
        assertThat(result.getName()).contains(videoId);
        verify(commandRunner, times(1)).runAudioExtract(videoId);
    }

    @Test
    void given_no_existing_thumbnail_when_extract_thumbnail_then_it_downloads_and_returns_thumbnail_file_path() throws Exception {
        String videoId = "B9j3pYC7Z20";

        when(commandRunner.runThumbnailExtract(videoId)).thenAnswer(invocation -> {
            Path downloaded = mediaDir.resolve("sample-thumb-" + videoId + ".jpg");
            Files.writeString(downloaded, "thumbnail");
            return 0;
        });

        File result = utils.extractThumbnailFromYoutubeVideoId(videoId);

        assertThat(result).isNotNull();
        assertThat(result.exists()).isTrue();
        assertThat(result.getName()).contains(videoId);
        assertThat(result.getName()).endsWith(".jpg");
        verify(commandRunner, times(1)).runThumbnailExtract(videoId);
    }

    @Test
    void given_existing_thumbnail_when_extract_thumbnail_then_it_reuses_existing_file_without_downloading_again() throws Exception {
        String videoId = "abc123XYZ";
        Path existingFile = mediaDir.resolve("sample-thumb-" + videoId + ".jpg");
        Files.writeString(existingFile, "thumbnail");

        File result = utils.extractThumbnailFromYoutubeVideoId(videoId);

        assertThat(result).isEqualTo(existingFile.toFile());
        assertThat(result.getName()).contains(videoId);
        verify(commandRunner, never()).runThumbnailExtract(anyString());
    }

    @Test
    void given_whatsapp_quality_when_extract_audio_then_it_creates_quality_specific_output_once() throws Exception {
        String videoId = "r98rdmXpA2c";

        when(commandRunner.runAudioExtract(videoId)).thenAnswer(invocation -> {
            Path downloaded = mediaDir.resolve("concert-" + videoId + ".m4a");
            Files.writeString(downloaded, "audio");
            return 0;
        });

        when(commandRunner.runConvertToM4a(anyString(), anyString(), contains("96k"))).thenAnswer(invocation -> {
            String outputFile = invocation.getArgument(1, String.class);
            Files.writeString(Path.of(outputFile), "converted-audio");
            return 0;
        });

        File result = utils.extractAudioFromYoutubeVideoId(videoId, MediaSplitUtils.OutputQuality.WHATSAPP);

        assertThat(result.getName()).contains(videoId);
        assertThat(result.getName()).contains("whatsapp");
        assertThat(result.exists()).isTrue();
        verify(commandRunner, times(1)).runAudioExtract(videoId);
        verify(commandRunner, times(1)).runConvertToM4a(anyString(), anyString(), contains("96k"));
    }

    @Test
    void given_existing_quality_download_when_extract_audio_with_same_quality_then_it_reuses_existing_quality_file() throws Exception {
        String videoId = "qhm6KDMaqLg";
        Path existingQualityFile = mediaDir.resolve("sample-title-" + videoId + "-music_concert.m4a");
        Files.writeString(existingQualityFile, "audio");

        File result = utils.extractAudioFromYoutubeVideoId(videoId, MediaSplitUtils.OutputQuality.MUSIC_CONCERT);

        assertThat(result).isEqualTo(existingQualityFile.toFile());
        assertThat(result.getName()).doesNotContain("-music_concert-music_concert");
        verify(commandRunner, never()).runAudioExtract(anyString());
        verify(commandRunner, never()).runConvertToM4a(anyString(), anyString(), anyString());
    }

    @Test
    void given_compact_size_quality_when_extract_audio_then_it_creates_smaller_quality_specific_output() throws Exception {
        String videoId = "fjCYYnfzRvI";

        when(commandRunner.runAudioExtract(videoId)).thenAnswer(invocation -> {
            Path downloaded = mediaDir.resolve("devotional-" + videoId + ".m4a");
            Files.writeString(downloaded, "audio");
            return 0;
        });

        when(commandRunner.runConvertToM4a(anyString(), anyString(), contains("80k"))).thenAnswer(invocation -> {
            String outputFile = invocation.getArgument(1, String.class);
            Files.writeString(Path.of(outputFile), "compact-audio");
            return 0;
        });

        File result = utils.extractAudioFromYoutubeVideoId(videoId, MediaSplitUtils.OutputQuality.COMPACT_SIZE);

        assertThat(result.getName()).contains(videoId);
        assertThat(result.getName()).contains("compact_size");
        assertThat(result.exists()).isTrue();
        verify(commandRunner, times(1)).runAudioExtract(videoId);
        verify(commandRunner, times(1)).runConvertToM4a(anyString(), anyString(), contains("80k"));
    }

    @Test
    void given_compact_size_speech_quality_when_extract_audio_then_it_creates_speech_optimized_output() throws Exception {
        String videoId = "B9j3pYC7Z20";
        Path sourcePath = mediaDir.resolve("speech-lesson-" + videoId + ".m4a");

        when(commandRunner.runAudioExtract(videoId)).thenAnswer(invocation -> {
            Files.writeString(sourcePath, "audio");
            return 0;
        });

        when(commandRunner.runConvertToM4a(anyString(), anyString(), contains("48k"))).thenAnswer(invocation -> {
            String outputFile = invocation.getArgument(1, String.class);
            Files.writeString(Path.of(outputFile), "speech-audio");
            return 0;
        });

        File result = utils.extractAudioFromYoutubeVideoId(videoId, MediaSplitUtils.OutputQuality.COMPACT_SIZE_SPEECH);

        assertThat(result.getName()).contains(videoId);
        assertThat(result.getName()).contains("compact_size_speech");
        assertThat(result.exists()).isTrue();
        assertThat(sourcePath).doesNotExist();
        verify(commandRunner, times(1)).runAudioExtract(videoId);
        verify(commandRunner, times(1)).runConvertToM4a(anyString(), anyString(), contains("48k"));
    }

    @Test
    void given_compact_size_music_quality_when_extract_audio_then_it_creates_music_optimized_output() throws Exception {
        String videoId = "B9j3pYC7Z20";

        when(commandRunner.runAudioExtract(videoId)).thenAnswer(invocation -> {
            Path downloaded = mediaDir.resolve("music-track-" + videoId + ".m4a");
            Files.writeString(downloaded, "audio");
            return 0;
        });

        when(commandRunner.runConvertToM4a(anyString(), anyString(), contains("72k"))).thenAnswer(invocation -> {
            String outputFile = invocation.getArgument(1, String.class);
            Files.writeString(Path.of(outputFile), "music-audio");
            return 0;
        });

        File result = utils.extractAudioFromYoutubeVideoId(videoId, MediaSplitUtils.OutputQuality.COMPACT_SIZE_MUSIC);

        assertThat(result.getName()).contains(videoId);
        assertThat(result.getName()).contains("compact_size_music");
        assertThat(result.exists()).isTrue();
        verify(commandRunner, times(1)).runAudioExtract(videoId);
        verify(commandRunner, times(1)).runConvertToM4a(anyString(), anyString(), contains("72k"));
    }

    @Test
    void given_compact_music_instrumental_quality_when_extract_audio_then_it_creates_instrumental_optimized_output() throws Exception {
        String videoId = "B9j3pYC7Z20";

        when(commandRunner.runAudioExtract(videoId)).thenAnswer(invocation -> {
            Path downloaded = mediaDir.resolve("instrumental-track-" + videoId + ".m4a");
            Files.writeString(downloaded, "audio");
            return 0;
        });

        when(commandRunner.runConvertToM4a(anyString(), anyString(), contains("72k"))).thenAnswer(invocation -> {
            String outputFile = invocation.getArgument(1, String.class);
            Files.writeString(Path.of(outputFile), "instrumental-audio");
            return 0;
        });

        File result = utils.extractAudioFromYoutubeVideoId(videoId, MediaSplitUtils.OutputQuality.COMPACT_MUSIC_INSTRUMENTAL);

        assertThat(result.getName()).contains(videoId);
        assertThat(result.getName()).contains("compact_music_instrumental");
        assertThat(result.exists()).isTrue();
        verify(commandRunner, times(1)).runAudioExtract(videoId);
        verify(commandRunner, times(1)).runConvertToM4a(anyString(), anyString(), contains("72k"));
    }

    @Test
    void given_playlist_id_when_extract_audio_then_it_returns_downloaded_files_in_playlist_order_and_reuses_them() throws Exception {
        String playlistId = "PLT6lIcOhPFQpBrEd6H4rX5ZPd3govnYAx";

        when(commandRunner.runPlaylistAudioExtract(playlistId)).thenAnswer(invocation -> {
            Files.writeString(mediaDir.resolve("2_second-track-videoB.m4a"), "audio-2");
            Files.writeString(mediaDir.resolve("1_first-track-videoA.m4a"), "audio-1");
            Files.writeString(mediaDir.resolve("3_third-track-videoC.m4a"), "audio-3");
            return 0;
        });

        java.util.List<File> firstResult = utils.extractAudioFromYoutubePlaylistId(playlistId);
        java.util.List<File> secondResult = utils.extractAudioFromYoutubePlaylistId(playlistId);

        assertThat(firstResult)
                .extracting(File::getName)
                .containsExactly(
                        "1_first-track-videoA.m4a",
                        "2_second-track-videoB.m4a",
                        "3_third-track-videoC.m4a"
                );
        assertThat(secondResult)
                .extracting(File::getAbsolutePath)
                .containsExactly(
                        firstResult.get(0).getAbsolutePath(),
                        firstResult.get(1).getAbsolutePath(),
                        firstResult.get(2).getAbsolutePath()
                );
        verify(commandRunner, times(1)).runPlaylistAudioExtract(playlistId);
    }

    @Test
    void given_playlist_quality_when_extract_audio_then_it_converts_each_playlist_item_once() throws Exception {
        String playlistId = "PL-playlist-quality";
        Path sourceA = mediaDir.resolve("1_speech-track-videoA.m4a");
        Path sourceB = mediaDir.resolve("2_discourse-track-videoB.m4a");

        when(commandRunner.runPlaylistAudioExtract(playlistId)).thenAnswer(invocation -> {
            Files.writeString(sourceA, "audio-1");
            Files.writeString(sourceB, "audio-2");
            return 0;
        });

        when(commandRunner.runConvertToM4a(anyString(), anyString(), contains("48k"))).thenAnswer(invocation -> {
            String outputFile = invocation.getArgument(1, String.class);
            Files.writeString(Path.of(outputFile), "speech-audio");
            return 0;
        });

        java.util.List<File> result = utils.extractAudioFromYoutubePlaylistId(playlistId, MediaSplitUtils.OutputQuality.COMPACT_SIZE_SPEECH);

        assertThat(result)
                .extracting(File::getName)
                .containsExactly(
                        "1_speech-track-videoA-compact_size_speech.m4a",
                        "2_discourse-track-videoB-compact_size_speech.m4a"
                );
        assertThat(result).allMatch(File::exists);
        assertThat(sourceA).doesNotExist();
        assertThat(sourceB).doesNotExist();
        verify(commandRunner, times(1)).runPlaylistAudioExtract(playlistId);
        verify(commandRunner, times(2)).runConvertToM4a(anyString(), anyString(), contains("48k"));
    }
}
