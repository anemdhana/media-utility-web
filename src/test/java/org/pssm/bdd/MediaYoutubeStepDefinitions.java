package org.pssm.bdd;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.pssm.media.MediaSplitUtils;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class MediaYoutubeStepDefinitions extends MediaBddSupport {

    private String videoId;
    private File extractedAudio;
    private final Map<String, File> extractedAudioByVideoId = new LinkedHashMap<>();
    private List<String> configuredLabels = Collections.emptyList();
    private Properties mediaInputProperties;

    @Given("the YouTube video id {string}")
    public void theYouTubeVideoId(String videoId) {
        assertThat(videoId).isNotBlank();
        this.videoId = videoId.trim();
    }

    @Given("the media input properties file {string}")
    public void theMediaInputPropertiesFile(String propertiesFile) throws Exception {
        Properties properties = new Properties();
        try (InputStream inputStream = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream(propertiesFile)) {
            if (inputStream == null) {
                throw new IllegalArgumentException("Unable to find properties file: " + propertiesFile);
            }
            properties.load(inputStream);
        }

        mediaInputProperties = properties;
    }

    @When("I extract the audio for the YouTube video")
    public void iExtractTheAudioForTheYouTubeVideo() throws Exception {
        extractedAudio = mediaFileUtils.extractAudioFromYoutubeVideoId(videoId);
    }

    @When("I extract the audio for the YouTube video with {string} quality")
    public void iExtractTheAudioForTheYouTubeVideoWithQuality(String quality) throws Exception {
        extractedAudio = mediaFileUtils.extractAudioFromYoutubeVideoId(videoId,
                MediaSplitUtils.OutputQuality.valueOf(quality.trim().toUpperCase()));
    }

    @When("I execute the configured media feature")
    public void iExecuteTheConfiguredMediaFeature() throws Exception {
        assertThat(mediaInputProperties).isNotNull();

        String feature = propertyValue("feature");
        assertThat(feature)
            .as("feature property must be provided")
            .isNotBlank();
        assertThat(feature.trim().toLowerCase(Locale.ROOT))
            .as("Only youtube feature is currently supported")
            .isEqualTo("youtube");

        String videoIdsCsv = propertyValue("videoId");
        List<String> configuredVideoIds = splitCsv(videoIdsCsv);
        assertThat(configuredVideoIds)
            .as("videoId property must contain at least one value")
            .isNotEmpty();

        String configuredQuality = propertyValue("quality");
        MediaSplitUtils.OutputQuality quality = configuredQuality == null || configuredQuality.isBlank()
            ? MediaSplitUtils.OutputQuality.YOUTUBE_UPLOAD
            : MediaSplitUtils.OutputQuality.valueOf(configuredQuality.trim().toUpperCase(Locale.ROOT));

        configuredLabels = splitCsv(propertyValue("label"));
        extractedAudioByVideoId.clear();

        for (String configuredVideoId : configuredVideoIds) {
            File firstResult = mediaFileUtils.extractAudioFromYoutubeVideoId(configuredVideoId, quality);
            if (!configuredLabels.isEmpty()) {
                mediaFileUtils.addLabels(firstResult, configuredLabels);
            }

            File secondResult = mediaFileUtils.extractAudioFromYoutubeVideoId(configuredVideoId, quality);
            assertThat(secondResult.getAbsolutePath())
                .as("Expected existing download to be reused for videoId %s", configuredVideoId)
                .isEqualTo(firstResult.getAbsolutePath());

            extractedAudioByVideoId.put(configuredVideoId, secondResult);
        }
    }

    @Then("the extracted audio file should exist")
    public void theExtractedAudioFileShouldExist() {
        assertThat(extractedAudio).isNotNull();
        assertThat(extractedAudio).exists().isFile();
    }

    @Then("the extracted audio filename should contain the video id")
    public void theExtractedAudioFilenameShouldContainTheVideoId() {
        assertThat(extractedAudio.getName())
                .as("Expected filename to contain videoId '%s'", videoId)
                .containsIgnoringCase(videoId);
    }

    @Then("the extracted audio file should be non-empty")
    public void theExtractedAudioFileShouldBeNonEmpty() {
        assertThat(extractedAudio.length())
                .as("Expected extracted audio file to be non-empty")
                .isGreaterThan(0L);
    }

    @Then("all configured YouTube downloads should exist")
    public void allConfiguredYouTubeDownloadsShouldExist() {
        assertThat(extractedAudioByVideoId).isNotEmpty();
        extractedAudioByVideoId.forEach((configuredVideoId, extractedFile) -> {
            assertThat(extractedFile)
                    .as("Expected extracted file for videoId %s", configuredVideoId)
                    .isNotNull();
            assertThat(extractedFile.exists())
                    .as("Expected extracted file to exist for videoId %s", configuredVideoId)
                    .isTrue();
            assertThat(extractedFile.length())
                    .as("Expected extracted file to be non-empty for videoId %s", configuredVideoId)
                    .isGreaterThan(0L);
            assertThat(extractedFile.getName().toLowerCase(Locale.ROOT))
                    .contains(configuredVideoId.toLowerCase(Locale.ROOT));
        });
    }

    @Then("the configured label should be applied to all downloaded files when provided")
    public void theConfiguredLabelShouldBeAppliedToAllDownloadedFilesWhenProvided() {
        if (configuredLabels.isEmpty()) {
            return;
        }

        extractedAudioByVideoId.forEach((configuredVideoId, extractedFile) -> {
            assertThat(mediaFileUtils.containsLabels(extractedFile, configuredLabels))
                    .as("Expected configured labels %s on file for videoId %s", configuredLabels, configuredVideoId)
                    .isTrue();
        });
    }

    private String propertyValue(String key) {
        if (mediaInputProperties == null) {
            return null;
        }
        return mediaInputProperties.getProperty(key);
    }

    private List<String> splitCsv(String value) {
        if (value == null || value.isBlank()) {
            return Collections.emptyList();
        }

        return java.util.Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));
    }
}
