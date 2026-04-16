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

    private static final String DEFAULT_MEDIA_INPUT_PROPERTIES_FILE = "media-input.properties";

    @FunctionalInterface
    private interface FeatureAction {
        void execute() throws Exception;
    }

    private String videoId;
    private File extractedAudio;
    private File extractedThumbnail;
    private final Map<String, File> extractedAudioResults = new LinkedHashMap<>();
    private List<String> configuredLabels = Collections.emptyList();
    private Properties mediaInputProperties;

    @Given("the YouTube video id {string}")
    public void theYouTubeVideoId(String videoId) {
        assertThat(videoId).isNotBlank();
        this.videoId = videoId.trim();
    }

    @Given("the media input properties file {string}")
    public void theMediaInputPropertiesFile(String propertiesFile) throws Exception {
        mediaInputProperties = loadMediaInputProperties(propertiesFile, null);
    }

    @Given("the media input scenario {string}")
    public void theMediaInputScenario(String scenarioKey) throws Exception {
        assertThat(scenarioKey)
            .as("scenario key must be provided")
            .isNotBlank();
        mediaInputProperties = loadMediaInputProperties(DEFAULT_MEDIA_INPUT_PROPERTIES_FILE, scenarioKey.trim());
    }

    @Given("the media input properties file {string} and scenario {string}")
    public void theMediaInputPropertiesFileAndScenario(String propertiesFile, String scenarioKey) throws Exception {
        assertThat(scenarioKey)
            .as("scenario key must be provided")
            .isNotBlank();
        mediaInputProperties = loadMediaInputProperties(propertiesFile, scenarioKey.trim());
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

    @When("I extract and split the audio for the YouTube video from {string} to {string} with {string} quality")
    public void iExtractAndSplitTheAudioForTheYouTubeVideo(String startTime, String endTime, String quality) throws Exception {
        extractedAudio = mediaFileUtils.extractAndSplitAudioFromYoutubeVideoId(
                videoId,
                startTime,
                endTime,
                MediaSplitUtils.OutputQuality.valueOf(quality.trim().toUpperCase(Locale.ROOT)),
                "m4a"
        );
    }

    @When("I extract the high quality thumbnail for the YouTube video")
    public void iExtractTheHighQualityThumbnailForTheYouTubeVideo() throws Exception {
        extractedThumbnail = mediaFileUtils.extractThumbnailFromYoutubeVideoId(videoId);
    }

    @When("I execute the configured media feature")
    public void iExecuteTheConfiguredMediaFeature() throws Exception {
        assertThat(mediaInputProperties).isNotNull();

        String feature = propertyValue("feature");
        assertThat(feature)
            .as("feature property must be provided")
            .isNotBlank();
        String normalizedFeature = feature.trim().toLowerCase(Locale.ROOT);
        extractedAudioResults.clear();
        configuredLabels = Collections.emptyList();
        Map<String, FeatureAction> featureActions = new LinkedHashMap<>();
        featureActions.put("youtube", this::executeYoutubeAudioFromProperties);
        featureActions.put("youtube_extract_split", this::executeYoutubeExtractSplitFromProperties);
        featureActions.put("youtube_thumbnail", this::executeYoutubeThumbnailFromProperties);

        FeatureAction featureAction = featureActions.get(normalizedFeature);
        assertThat(featureAction)
            .as("Supported features are %s", String.join(", ", featureActions.keySet()))
            .isNotNull();
        featureAction.execute();
    }

    private void executeYoutubeThumbnailFromProperties() throws Exception {
        String configuredVideoId = propertyValue("videoId");
        assertThat(configuredVideoId)
            .as("videoId property must contain a value for youtube_thumbnail")
            .isNotBlank();

        File firstResult = mediaFileUtils.extractThumbnailFromYoutubeVideoId(configuredVideoId.trim());
        File secondResult = mediaFileUtils.extractThumbnailFromYoutubeVideoId(configuredVideoId.trim());
        assertThat(secondResult.getAbsolutePath())
            .as("Expected existing thumbnail to be reused for videoId %s", configuredVideoId)
            .isEqualTo(firstResult.getAbsolutePath());

        extractedThumbnail = secondResult;
        extractedAudioResults.put(configuredVideoId.trim(), secondResult);
    }

    private void executeYoutubeExtractSplitFromProperties() throws Exception {
        String videoIdsCsv = propertyValue("videoId");
        assertThat(videoIdsCsv)
            .as("videoId property must contain a value for youtube_extract_split")
            .isNotBlank();

        String configuredStartTime = propertyValue("startTime");
        String configuredEndTime = propertyValue("endTime");
        assertThat(configuredStartTime)
            .as("startTime property must contain a value for youtube_extract_split")
            .isNotBlank();
        assertThat(configuredEndTime)
            .as("endTime property must contain a value for youtube_extract_split")
            .isNotBlank();

        String configuredQuality = propertyValue("quality");
        MediaSplitUtils.OutputQuality quality = configuredQuality == null || configuredQuality.isBlank()
            ? MediaSplitUtils.OutputQuality.YOUTUBE_UPLOAD
            : MediaSplitUtils.OutputQuality.valueOf(configuredQuality.trim().toUpperCase(Locale.ROOT));
        String outputFormat = propertyValue("outputFormat");
        String normalizedOutputFormat = outputFormat == null || outputFormat.isBlank() ? "m4a" : outputFormat.trim();

        extractedAudio = mediaFileUtils.extractAndSplitAudioFromYoutubeVideoId(
                videoIdsCsv.trim(),
                configuredStartTime.trim(),
                configuredEndTime.trim(),
                quality,
                normalizedOutputFormat
        );
        extractedAudioResults.put(videoIdsCsv.trim(), extractedAudio);
    }

    private void executeYoutubeAudioFromProperties() throws Exception {
        String videoIdsCsv = propertyValue("videoId");
        List<String> configuredVideoIds = splitCsv(videoIdsCsv);
        String configuredPlaylistId = propertyValue("playlistId");
        boolean hasPlaylistId = configuredPlaylistId != null && !configuredPlaylistId.isBlank();
        assertThat(hasPlaylistId || !configuredVideoIds.isEmpty())
            .as("Either playlistId or videoId property must contain at least one value")
            .isTrue();

        String configuredQuality = propertyValue("quality");
        MediaSplitUtils.OutputQuality quality = configuredQuality == null || configuredQuality.isBlank()
            ? MediaSplitUtils.OutputQuality.YOUTUBE_UPLOAD
            : MediaSplitUtils.OutputQuality.valueOf(configuredQuality.trim().toUpperCase(Locale.ROOT));

        configuredLabels = splitCsv(propertyValue("label"));

        if (hasPlaylistId) {
            List<File> firstResults = mediaFileUtils.extractAudioFromYoutubePlaylistId(configuredPlaylistId.trim(), quality);
            if (!configuredLabels.isEmpty()) {
                for (File firstResult : firstResults) {
                    mediaFileUtils.addLabels(firstResult, configuredLabels);
                }
            }

            List<File> secondResults = mediaFileUtils.extractAudioFromYoutubePlaylistId(configuredPlaylistId.trim(), quality);
            assertThat(secondResults)
                .as("Expected existing download to be reused for playlistId %s", configuredPlaylistId)
                .hasSameSizeAs(firstResults);

            for (int index = 0; index < firstResults.size(); index++) {
                File firstResult = firstResults.get(index);
                File secondResult = secondResults.get(index);
                assertThat(secondResult.getAbsolutePath())
                    .as("Expected existing download to be reused for playlistId %s at position %s", configuredPlaylistId, index + 1)
                    .isEqualTo(firstResult.getAbsolutePath());
                extractedAudioResults.put(String.valueOf(index + 1), secondResult);
            }
            return;
        }

        for (String configuredVideoId : configuredVideoIds) {
            File firstResult = mediaFileUtils.extractAudioFromYoutubeVideoId(configuredVideoId, quality);
            if (!configuredLabels.isEmpty()) {
                mediaFileUtils.addLabels(firstResult, configuredLabels);
            }

            File secondResult = mediaFileUtils.extractAudioFromYoutubeVideoId(configuredVideoId, quality);
            assertThat(secondResult.getAbsolutePath())
                .as("Expected existing download to be reused for videoId %s", configuredVideoId)
                .isEqualTo(firstResult.getAbsolutePath());

            extractedAudioResults.put(configuredVideoId, secondResult);
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

    @Then("the extracted thumbnail file should exist")
    public void theExtractedThumbnailFileShouldExist() {
        assertThat(extractedThumbnail).isNotNull();
        assertThat(extractedThumbnail).exists().isFile();
    }

    @Then("the extracted thumbnail filename should contain the video id")
    public void theExtractedThumbnailFilenameShouldContainTheVideoId() {
        assertThat(extractedThumbnail.getName())
                .as("Expected filename to contain videoId '%s'", videoId)
                .containsIgnoringCase(videoId);
    }

    @Then("the extracted thumbnail file should be non-empty")
    public void theExtractedThumbnailFileShouldBeNonEmpty() {
        assertThat(extractedThumbnail.length())
                .as("Expected extracted thumbnail file to be non-empty")
                .isGreaterThan(0L);
    }

    @Then("all configured YouTube downloads should exist")
    public void allConfiguredYouTubeDownloadsShouldExist() {
        assertThat(extractedAudioResults).isNotEmpty();
        extractedAudioResults.forEach((configuredKey, extractedFile) -> {
            assertThat(extractedFile)
                .as("Expected extracted file for entry %s", configuredKey)
                    .isNotNull();
            assertThat(extractedFile.exists())
                .as("Expected extracted file to exist for entry %s", configuredKey)
                    .isTrue();
            assertThat(extractedFile.length())
                .as("Expected extracted file to be non-empty for entry %s", configuredKey)
                .isGreaterThan(0L);
        });
    }

    @Then("the configured label should be applied to all downloaded files when provided")
    public void theConfiguredLabelShouldBeAppliedToAllDownloadedFilesWhenProvided() {
        if (configuredLabels.isEmpty()) {
            return;
        }

        extractedAudioResults.forEach((configuredKey, extractedFile) -> {
            assertThat(mediaFileUtils.containsLabels(extractedFile, configuredLabels))
                    .as("Expected configured labels %s on file for entry %s", configuredLabels, configuredKey)
                    .isTrue();
        });
    }

    private String propertyValue(String key) {
        if (mediaInputProperties == null) {
            return null;
        }
        return mediaInputProperties.getProperty(key);
    }

    private Properties loadMediaInputProperties(String propertiesFile, String scenarioKey) throws Exception {
        Properties rawProperties = new Properties();
        try (InputStream inputStream = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream(propertiesFile)) {
            if (inputStream == null) {
                throw new IllegalArgumentException("Unable to find properties file: " + propertiesFile);
            }
            rawProperties.load(inputStream);
        }

        if (scenarioKey == null || scenarioKey.isBlank()) {
            return rawProperties;
        }
        return resolveScenarioProperties(rawProperties, scenarioKey);
    }

    private Properties resolveScenarioProperties(Properties rawProperties, String scenarioKey) {
        Properties resolved = new Properties();
        copyLegacyUnscopedProperties(rawProperties, resolved);
        applyPrefixedProperties(rawProperties, "default.", resolved);
        applyPrefixedProperties(rawProperties, "common.", resolved);
        applyPrefixedProperties(rawProperties, "scenario." + scenarioKey + ".", resolved);

        assertThat(hasNonBlankProperty(resolved, "feature"))
            .as("No usable scenario values found for '%s'. Available scenario keys: %s", scenarioKey, availableScenarioKeys(rawProperties))
            .isTrue();
        return resolved;
    }

    private void copyLegacyUnscopedProperties(Properties source, Properties target) {
        source.stringPropertyNames().stream()
            .filter(name -> !name.contains("."))
            .forEach(name -> target.setProperty(name, source.getProperty(name)));
    }

    private void applyPrefixedProperties(Properties source, String prefix, Properties target) {
        source.stringPropertyNames().stream()
            .filter(name -> name.startsWith(prefix))
            .forEach(name -> target.setProperty(name.substring(prefix.length()), source.getProperty(name)));
    }

    private String availableScenarioKeys(Properties source) {
        List<String> keys = source.stringPropertyNames().stream()
            .filter(name -> name.startsWith("scenario."))
            .map(name -> name.substring("scenario.".length()))
            .filter(name -> name.contains("."))
            .map(name -> name.substring(0, name.indexOf('.')))
            .distinct()
            .sorted()
            .collect(Collectors.toList());
        return keys.isEmpty() ? "<none>" : String.join(", ", keys);
    }

    private boolean hasNonBlankProperty(Properties source, String key) {
        String value = source.getProperty(key);
        return value != null && !value.isBlank();
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
