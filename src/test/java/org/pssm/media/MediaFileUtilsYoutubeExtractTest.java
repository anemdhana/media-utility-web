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
    private Path downloadDir;

    @BeforeEach
    void setUp() throws Exception {
        commandRunner = mock(MediaCommandRunner.class);

        Path mediaDir = tempDir.resolve("media");
        Path toolsDir = tempDir.resolve("tools");
        downloadDir = tempDir.resolve("downloads");

        Files.createDirectories(mediaDir);
        Files.createDirectories(toolsDir);
        Files.createDirectories(downloadDir);

        utils = new MediaFileUtils(mediaDir.toString(), toolsDir.toString(), commandRunner, downloadDir.toString());
    }

    @Test
    void given_existing_download_when_extract_audio_then_it_reuses_existing_file_without_downloading_again() throws Exception {
        String videoId = "abc123XYZ";
        Path existingFile = downloadDir.resolve("sample-title-" + videoId + ".m4a");
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
            Path downloaded = downloadDir.resolve("deep-meditation-" + videoId + ".m4a");
            Files.writeString(downloaded, "audio");
            return 0;
        });

        File result = utils.extractAudioFromYoutubeVideoId(videoId);

        assertThat(result).isNotNull();
        assertThat(result.exists()).isTrue();
        assertThat(result.getAbsolutePath()).contains(downloadDir.toString());
        assertThat(result.getName()).contains(videoId);
        verify(commandRunner, times(1)).runAudioExtract(videoId);
    }

    @Test
    void given_whatsapp_quality_when_extract_audio_then_it_creates_quality_specific_output_once() throws Exception {
        String videoId = "r98rdmXpA2c";

        when(commandRunner.runAudioExtract(videoId)).thenAnswer(invocation -> {
            Path downloaded = downloadDir.resolve("concert-" + videoId + ".m4a");
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
}
