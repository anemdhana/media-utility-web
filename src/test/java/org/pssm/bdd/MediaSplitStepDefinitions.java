package org.pssm.bdd;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.pssm.media.MediaSplitUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

public class MediaSplitStepDefinitions extends MediaBddSupport {

    @Autowired
    private MediaSplitUtils splitUtils;

    private File inputFile;
    private File outputFile;
    private boolean audioOutput;

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
}
