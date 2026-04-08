package org.pssm.bdd;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.junit.jupiter.api.Assumptions;
import org.pssm.media.MediaPlaylistUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

public class MediaPlaylistStepDefinitions extends MediaBddSupport {

    private static final Pattern DB_VALUE_PATTERN = Pattern.compile("([-+]?\\d+(?:\\.\\d+)?)\\s*dB", Pattern.CASE_INSENSITIVE);
    private static final double MIN_NORMAL_GAIN_DB = -15.0;
    private static final double MAX_NORMAL_GAIN_DB = 10.0;

    @Autowired
    private MediaPlaylistUtils playlistUtils;

    private File playlistFile;
    private File labelPlaylistFile;
    private Path targetFolder;
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

    @Given("a copied audio file labeled {string}")
    public void aCopiedAudioFileLabeled(String label) throws Exception {
        firstTrack = pickAudioTestFile();
        mediaFileUtils.addLabels(firstTrack, List.of(label));
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

    @When("I create a playlist from label {string}")
    public void iCreateAPlaylistFromLabel(String label) throws Exception {
        labelPlaylistFile = playlistUtils.createPlaylistByLabel(label);
    }

    @When("I copy the playlist and tracks to a temporary target folder")
    public void iCopyThePlaylistAndTracksToATemporaryTargetFolder() throws Exception {
        targetFolder = Files.createTempDirectory("bdd-playlist-copy-target-");
        playlistFile = playlistUtils.copyPlaylistAndTracksToFolder(playlistFile.getAbsolutePath(), targetFolder.toString());
    }

    @Then("the playlist should contain {int} tracks")
    public void thePlaylistShouldContainTracks(int expectedCount) throws Exception {
        assertThat(playlistUtils.getPlaylistTracks(playlistFile)).hasSize(expectedCount);
    }

    @Then("the playlist duration should be at least {int} seconds")
    public void thePlaylistDurationShouldBeAtLeastSeconds(int minimumSeconds) throws Exception {
        assertThat(playlistUtils.getTotalDuration(playlistFile)).isGreaterThanOrEqualTo((double) minimumSeconds);
    }

    @Then("the created label playlist filename should include the label and total duration")
    public void theCreatedLabelPlaylistFilenameShouldIncludeTheLabelAndTotalDuration() {
        assertThat(labelPlaylistFile).isNotNull();
        assertThat(labelPlaylistFile.getName()).matches("^bdd-heart-melting\\+\\d+\\.m3u8$");
    }

    @Then("the created label playlist should contain at least {int} tracks")
    public void theCreatedLabelPlaylistShouldContainAtLeastTracks(int minimumTrackCount) throws Exception {
        assertThat(playlistUtils.getPlaylistTracks(labelPlaylistFile).size()).isGreaterThanOrEqualTo(minimumTrackCount);
    }

    @Then("the copied playlist and all tracks should exist in the target folder")
    public void theCopiedPlaylistAndAllTracksShouldExistInTheTargetFolder() throws Exception {
        assertThat(targetFolder).isNotNull();
        assertThat(playlistFile).isNotNull();
        assertThat(playlistFile.exists()).isTrue();
        assertThat(playlistFile.getParentFile().getAbsolutePath()).isEqualTo(targetFolder.toFile().getAbsolutePath());

        List<File> tracks = playlistUtils.getPlaylistTracks(playlistFile);
        assertThat(tracks).isNotEmpty();
        assertThat(tracks).allSatisfy(track -> {
            assertThat(track.exists()).isTrue();
            assertThat(track.getParentFile().getAbsolutePath()).isEqualTo(targetFolder.toFile().getAbsolutePath());
        });
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

    @Then("the album volume gain should stay in a normal range across tracks without being too loud or too low")
    public void theAlbumVolumeGainShouldStayInANormalRangeAcrossTracksWithoutBeingTooLoudOrTooLow() {
        assertThat(report).isNotNull();
        assertThat(report.tracks()).isNotEmpty();

        assertThat(report.tracks()).allSatisfy(track -> {
            assertThat(track.hasReplayGain()).isTrue();
            String gainText = track.replayGainAlbum().isBlank() ? track.replayGainTrack() : track.replayGainAlbum();
            double gainDb = parseGainInDb(gainText);
            assertThat(gainDb)
                    .as("ReplayGain dB for %s should be between %s and %s", track.fileName(), MIN_NORMAL_GAIN_DB, MAX_NORMAL_GAIN_DB)
                    .isBetween(MIN_NORMAL_GAIN_DB, MAX_NORMAL_GAIN_DB);
        });
    }

    private double parseGainInDb(String gainText) {
        Matcher matcher = DB_VALUE_PATTERN.matcher(gainText == null ? "" : gainText.trim());
        assertThat(matcher.find()).as("Expected ReplayGain value in dB format but found: %s", gainText).isTrue();
        return Double.parseDouble(matcher.group(1));
    }
}
