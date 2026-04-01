package org.pssm.bdd;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.pssm.media.MediaSplitUtils;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

public class MediaYoutubeStepDefinitions extends MediaBddSupport {

    private String videoId;
    private File extractedAudio;

    @Given("the YouTube video id {string}")
    public void theYouTubeVideoId(String videoId) {
        assertThat(videoId).isNotBlank();
        this.videoId = videoId.trim();
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
}
