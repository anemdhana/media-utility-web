package org.pssm.bdd;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.pssm.media.MediaSplitUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

public class MediaSplitStepDefinitions extends MediaBddSupport {

    private static final String DEFAULT_MEDIA_INPUT_PROPERTIES_FILE = "media-input.properties";
    private static final List<String> VIDEO_EXTENSIONS = List.of(".mp4", ".webm", ".mkv", ".mov", ".avi");

    @Autowired
    private MediaSplitUtils splitUtils;

    private File inputFile;
    private File outputFile;
    private boolean audioOutput;
    private Properties mediaInputProperties;

    @Given("an existing audio media file")
    public void anExistingAudioMediaFile() {
        inputFile = pickAudioTestFile("dummy.mp3", "dummy_out.m4a");
    }

    @When("I split the audio clip from {string} to {string} using {string} quality")
    public void iSplitTheAudioClipFromToUsingQuality(String startTime, String endTime, String quality) throws Exception {
        outputFile = createReadableSiblingOutputFile(inputFile, quality.toLowerCase() + "-audio-split", startTime, endTime, "m4a");
        splitUtils.splitAudioFile(
                inputFile,
                startTime,
                endTime,
                MediaSplitUtils.OutputQuality.valueOf(quality),
                null,
                outputFile
        );
    }

    @Then("the created audio clip should exist")
    public void theCreatedAudioClipShouldExist() {
        assertThat(outputFile).exists().isFile();
        assertThat(outputFile.getAbsoluteFile().getParentFile())
                .isEqualTo(inputFile.getAbsoluteFile().getParentFile());
    }

    @Then("the created audio clip should be non-empty")
    public void theCreatedAudioClipShouldBeNonEmpty() {
        assertThat(outputFile.length()).isGreaterThan(0L);
    }

    @Given("an existing video media file")
    public void anExistingVideoMediaFile() {
        inputFile = pickVideoTestFile();
    }

    @Given("the media file for video id {string}")
    public void theMediaFileForVideoId(String videoId) throws Exception {
        inputFile = pickMediaFileByVideoId(videoId);
    }

    @Given("the media split input scenario {string}")
    public void theMediaSplitInputScenario(String scenarioKey) throws Exception {
        assertThat(scenarioKey)
            .as("scenario key must be provided")
            .isNotBlank();
        mediaInputProperties = loadMediaInputProperties(DEFAULT_MEDIA_INPUT_PROPERTIES_FILE, scenarioKey.trim());
    }

    @When("I execute the configured media split feature")
    public void iExecuteTheConfiguredMediaSplitFeature() throws Exception {
        assertThat(mediaInputProperties).isNotNull();

        String feature = propertyValue("feature");
        assertThat(feature)
            .as("feature property must be provided")
            .isNotBlank();

        String mediaType = propertyValue("mediaType");
        assertThat(mediaType)
            .as("mediaType must be provided as 'audio' or 'video'")
            .isNotBlank();

        String startTime = propertyValue("startTime");
        String endTime = propertyValue("endTime");
        assertThat(startTime)
            .as("startTime property must be provided")
            .isNotBlank();
        assertThat(endTime)
            .as("endTime property must be provided")
            .isNotBlank();

        String qualityRaw = propertyValue("quality");
        MediaSplitUtils.OutputQuality quality = qualityRaw == null || qualityRaw.isBlank()
            ? MediaSplitUtils.OutputQuality.YOUTUBE_UPLOAD
            : MediaSplitUtils.OutputQuality.valueOf(qualityRaw.trim().toUpperCase(Locale.ROOT));

        String normalizedFeature = feature.trim().toLowerCase(Locale.ROOT);
        assertThat(normalizedFeature)
            .as("supported split feature is 'media_split'")
            .isEqualTo("media_split");

        String normalizedMediaType = mediaType.trim().toLowerCase(Locale.ROOT);
        if ("video".equals(normalizedMediaType)) {
            inputFile = resolveConfiguredInput(propertyValue("inputFile"), propertyValue("videoId"), false);
            String outputFormat = defaulted(propertyValue("outputFormat"), "mp4");
            outputFile = createReadableSiblingOutputFile(inputFile, quality.name().toLowerCase(Locale.ROOT) + "-video-props", startTime, endTime, outputFormat);
            splitUtils.splitVideoFile(inputFile, startTime, endTime, quality, outputFormat, propertyValue("crop"), outputFile);
            audioOutput = false;
            return;
        }

        if ("audio".equals(normalizedMediaType)) {
            inputFile = resolveConfiguredInput(propertyValue("inputFile"), propertyValue("videoId"), true);
            String outputFormat = defaulted(propertyValue("outputFormat"), "m4a");
            outputFile = createReadableSiblingOutputFile(inputFile, quality.name().toLowerCase(Locale.ROOT) + "-audio-props", startTime, endTime, outputFormat);
            splitUtils.splitAudioFile(inputFile, startTime, endTime, quality, outputFormat, outputFile);
            audioOutput = true;
            return;
        }

        throw new IllegalArgumentException("Unsupported mediaType: " + mediaType + ". Expected 'audio' or 'video'.");
    }

    @When("I split the selected media clip from {string} to {string} for {string} sharing")
    public void iSplitTheSelectedMediaClipFromToForSharing(String startTime, String endTime, String quality) throws Exception {
        audioOutput = isAudioFile(inputFile);
        outputFile = createReadableSiblingOutputFile(
                inputFile,
                quality.toLowerCase() + (audioOutput ? "-audio-share" : "-video-share"),
                startTime,
                endTime,
                audioOutput ? "m4a" : "mp4"
        );

        if (audioOutput) {
            splitUtils.splitAudioFile(
                    inputFile,
                    startTime,
                    endTime,
                    MediaSplitUtils.OutputQuality.valueOf(quality),
                    null,
                    outputFile
            );
        } else {
            splitUtils.splitVideoFile(
                    inputFile,
                    startTime,
                    endTime,
                    MediaSplitUtils.OutputQuality.valueOf(quality),
                    null,
                    null,
                    outputFile
            );
        }
    }

    @When("I split the video clip from {string} to {string} using {string} quality")
    public void iSplitTheVideoClipFromToUsingQuality(String startTime, String endTime, String quality) throws Exception {
        outputFile = createReadableSiblingOutputFile(inputFile, quality.toLowerCase() + "-video-split", startTime, endTime, "mp4");
        splitUtils.splitVideoFile(
                inputFile,
                startTime,
                endTime,
                MediaSplitUtils.OutputQuality.valueOf(quality),
                null,
                null,
                outputFile
        );
    }

    @Then("the created video clip should exist")
    public void theCreatedVideoClipShouldExist() {
        assertThat(outputFile).exists().isFile();
        assertThat(outputFile.getAbsoluteFile().getParentFile())
                .isEqualTo(inputFile.getAbsoluteFile().getParentFile());
    }

    @Then("the created video clip should be non-empty")
    public void theCreatedVideoClipShouldBeNonEmpty() {
        assertThat(outputFile.length()).isGreaterThan(0L);
    }

    @Then("the configured media split output should exist")
    public void theConfiguredMediaSplitOutputShouldExist() {
        assertThat(outputFile).isNotNull();
        assertThat(outputFile).exists().isFile();
    }

    @Then("the configured media split output should be non-empty")
    public void theConfiguredMediaSplitOutputShouldBeNonEmpty() {
        assertThat(outputFile).isNotNull();
        assertThat(outputFile.length()).isGreaterThan(0L);
    }

    /**
     * Resolve the input file for the configured media split scenario.
     *
     * Resolution order:
     * 1. inputFile property (absolute or ${user.home}-relative path)
     * 2. videoId property (match filename in mediafiles_dir)
     * 3. First available file of the appropriate type in mediafiles_dir
     */
    private File resolveConfiguredInput(String inputFilePath, String videoId, boolean audio) throws Exception {
        // 1. Direct file path
        if (inputFilePath != null && !inputFilePath.isBlank()) {
            String resolved = resolvePathPlaceholders(inputFilePath.trim());
            File file = Path.of(resolved).toFile();
            assertThat(file)
                .as("inputFile does not exist: %s", file.getAbsolutePath())
                .exists();
            return file;
        }

        // 2. Lookup by videoId
        if (videoId != null && !videoId.isBlank()) {
            List<File> matches = mediaFileUtils.listAllMediaFiles().stream()
                .filter(File::isFile)
                .filter(file -> file.getName().toLowerCase(Locale.ROOT).contains(videoId.trim().toLowerCase(Locale.ROOT)))
                .filter(file -> audio ? isAudioFile(file) : isVideoFile(file))
                .sorted(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();

            assertThat(matches)
                .as("Expected at least one %s file to contain video id %s", audio ? "audio" : "video", videoId)
                .isNotEmpty();
            return matches.get(0);
        }

        // 3. Pick first available
        return audio ? pickAudioTestFile("dummy.mp3", "dummy_out.m4a") : pickVideoTestFile();
    }

    private String resolvePathPlaceholders(String path) {
        return path.replace("${user.home}", System.getProperty("user.home"));
    }

    private boolean isVideoFile(File file) {
        String lower = file.getName().toLowerCase(Locale.ROOT);
        return VIDEO_EXTENSIONS.stream().anyMatch(lower::endsWith);
    }

    private String defaulted(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
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
            .as("No usable scenario values found for '%s'", scenarioKey)
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

    private boolean hasNonBlankProperty(Properties properties, String key) {
        String value = properties.getProperty(key);
        return value != null && !value.isBlank();
    }
}
