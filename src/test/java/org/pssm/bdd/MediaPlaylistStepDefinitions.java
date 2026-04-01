package org.pssm.bdd;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.junit.jupiter.api.Assumptions;
import org.pssm.media.MediaPlaylistUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class MediaPlaylistStepDefinitions extends MediaBddSupport {

    @Autowired
    private MediaPlaylistUtils playlistUtils;

    private File playlistFile;
    private File firstTrack;
    private File secondTrack;
    private MediaPlaylistUtils.PlaylistReplayGainReport report;

    @Given("a playlist with one copied media track")
    public void aPlaylistWithOneCopiedMediaTrack() throws Exception {
        firstTrack = copySmallMediaFile(false);
        playlistFile = createTempFile("bdd-media-playlist-", ".m3u8");
        playlistUtils.createM3u8Playlist(playlistFile, List.of(firstTrack), false);
    }

    @Given("a playlist with one copied audio track")
    public void aPlaylistWithOneCopiedAudioTrack() throws Exception {
        firstTrack = copySmallMediaFile(true);
        playlistFile = createTempFile("bdd-audio-playlist-", ".m3u8");
        playlistUtils.createM3u8Playlist(playlistFile, List.of(firstTrack), false);
    }

    @When("I add another copied media track to the playlist")
    public void iAddAnotherCopiedMediaTrackToThePlaylist() throws Exception {
        secondTrack = copySmallMediaFile(false);
        playlistUtils.addTracks(playlistFile, List.of(secondTrack));
    }

    @When("I remove the extra track from the playlist")
    public void iRemoveTheExtraTrackFromThePlaylist() throws Exception {
        assertThat(secondTrack).isNotNull();
        playlistUtils.removeTracks(playlistFile, List.of(secondTrack));
    }

    @When("I clear the playlist")
    public void iClearThePlaylist() throws Exception {
        playlistUtils.clearTracks(playlistFile);
    }

    @Then("the playlist should contain {int} tracks")
    public void thePlaylistShouldContainTracks(int expectedCount) throws Exception {
        assertThat(playlistUtils.getPlaylistTracks(playlistFile)).hasSize(expectedCount);
    }

    @Then("the playlist duration should be at least {int} seconds")
    public void thePlaylistDurationShouldBeAtLeastSeconds(int minimumSeconds) throws Exception {
        assertThat(playlistUtils.getTotalDuration(playlistFile)).isGreaterThanOrEqualTo((double) minimumSeconds);
    }

    @Given("the ReplayGain command line tool is available")
    public void theReplayGainCommandLineToolIsAvailable() {
        File rsgainExe = new File(getToolsLocation(), "rsgain.exe");
        File rsgainCmd = new File(getToolsLocation(), "rsgain");
        Assumptions.assumeTrue(rsgainExe.exists() || rsgainCmd.exists(), "rsgain tool is required for ReplayGain integration test");
    }

    @When("I inspect the ReplayGain report for the playlist")
    public void iInspectTheReplayGainReportForThePlaylist() throws Exception {
        report = playlistUtils.getReplayGainDetails(playlistFile);
    }

    @When("I apply ReplayGain to the playlist")
    public void iApplyReplayGainToThePlaylist() throws Exception {
        playlistUtils.applyReplayGain(playlistFile);
    }

    @Then("the ReplayGain report should reference the current playlist")
    public void theReplayGainReportShouldReferenceTheCurrentPlaylist() {
        assertThat(report).isNotNull();
        assertThat(report.playlistName()).isEqualTo(playlistFile.getName());
    }

    @Then("the ReplayGain summary should report {int} tracks")
    public void theReplayGainSummaryShouldReportTracks(int expectedCount) {
        assertThat(report.summary().trackCount()).isEqualTo(expectedCount);
        assertThat(report.summary().normalizedCount() + report.summary().missingReplayGainCount())
                .isEqualTo(expectedCount);
    }

    @Then("the ReplayGain report should mark the playlist as fully normalized")
    public void theReplayGainReportShouldMarkThePlaylistAsFullyNormalized() {
        assertThat(report.summary().fullyNormalized()).isTrue();
        assertThat(report.summary().missingReplayGainCount()).isZero();
        assertThat(report.summary().normalizedCount()).isEqualTo(report.summary().trackCount());
    }
}
