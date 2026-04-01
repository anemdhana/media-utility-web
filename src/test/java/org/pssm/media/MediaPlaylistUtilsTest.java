package org.pssm.media;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@SpringBootTest
@DisplayName("BDD style playlist and ReplayGain checks")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MediaPlaylistUtilsTest {

    @Autowired
    private MediaPlaylistUtils playlistUtils;

    @Autowired
    private MediaFileUtils mediaFileUtils;

    @Value("${tools_location}")
    private String toolsLocation;

    private File copySmallMediaFile(boolean audioOnly) throws Exception {
        List<File> candidates = mediaFileUtils.listAllMediaFiles().stream()
                .filter(File::isFile)
                .filter(file -> {
                    String lower = file.getName().toLowerCase(Locale.ROOT);
                    if (audioOnly) {
                        return lower.endsWith(".mp3") || lower.endsWith(".m4a") || lower.endsWith(".aac") || lower.endsWith(".wav") || lower.endsWith(".flac");
                    }
                    return lower.endsWith(".mp3") || lower.endsWith(".m4a") || lower.endsWith(".aac") || lower.endsWith(".wav")
                            || lower.endsWith(".flac") || lower.endsWith(".mp4") || lower.endsWith(".webm")
                            || lower.endsWith(".mkv") || lower.endsWith(".mov") || lower.endsWith(".avi");
                })
                .sorted(Comparator.comparingLong(File::length))
                .collect(Collectors.toList());

        assertThat(candidates).isNotEmpty();
        File source = candidates.get(0);

        String name = source.getName();
        int dotIndex = name.lastIndexOf('.');
        String suffix = dotIndex >= 0 ? name.substring(dotIndex) : ".tmp";
        Path copyPath = Files.createTempFile("playlist-track-", suffix);
        Files.copy(source.toPath(), copyPath, StandardCopyOption.REPLACE_EXISTING);
        copyPath.toFile().deleteOnExit();
        return copyPath.toFile();
    }

    @Test
    void given_the_spring_context_when_playlist_utils_are_loaded_then_they_are_available() {
        assertThat(playlistUtils).isNotNull();
    }

    @Test
    void given_a_playlist_when_i_add_remove_and_clear_tracks_then_the_playlist_state_updates_correctly() throws Exception {
        File track1 = copySmallMediaFile(false);
        File track2 = copySmallMediaFile(false);
        File playlistFile = File.createTempFile("media-playlist-", ".m3u8");
        playlistFile.deleteOnExit();

        playlistUtils.createM3u8Playlist(playlistFile, List.of(track1), false);
        assertThat(playlistFile).exists();
        assertThat(playlistUtils.getPlaylistTracks(playlistFile)).hasSize(1);
        assertThat(playlistUtils.getTotalDuration(playlistFile)).isGreaterThanOrEqualTo(0.0);

        playlistUtils.addTracks(playlistFile, List.of(track2));
        assertThat(playlistUtils.getPlaylistTracks(playlistFile)).hasSize(2);

        playlistUtils.removeTracks(playlistFile, List.of(track2));
        assertThat(playlistUtils.getPlaylistTracks(playlistFile)).hasSize(1);

        playlistUtils.clearTracks(playlistFile);
        assertThat(playlistUtils.getPlaylistTracks(playlistFile)).isEmpty();
    }

    @Test
    void given_a_playlist_when_i_request_replaygain_details_then_a_human_readable_report_is_returned() throws Exception {
        File track = copySmallMediaFile(true);
        File playlistFile = File.createTempFile("replaygain-report-", ".m3u8");
        playlistFile.deleteOnExit();

        playlistUtils.createM3u8Playlist(playlistFile, List.of(track), false);
        MediaPlaylistUtils.PlaylistReplayGainReport report = playlistUtils.getReplayGainDetails(playlistFile);

        assertThat(report).isNotNull();
        assertThat(report.playlistName()).isEqualTo(playlistFile.getName());
        assertThat(report.summary().trackCount()).isEqualTo(1);
        assertThat(report.summary().normalizedCount() + report.summary().missingReplayGainCount())
                .isEqualTo(report.summary().trackCount());
        assertThat(report.tracks()).singleElement().satisfies(trackReport -> {
            assertThat(trackReport.fileName()).isEqualTo(track.getName());
            assertThat(trackReport.hasReplayGain())
                    .isEqualTo(!trackReport.replayGainTrack().isBlank() || !trackReport.replayGainAlbum().isBlank());
            if (trackReport.hasReplayGain()) {
                assertThat(trackReport.note()).isBlank();
            } else {
                assertThat(trackReport.note()).contains("ReplayGain");
            }
        });
    }

    @Test
    void given_the_replaygain_tool_when_i_apply_it_to_a_playlist_then_the_tracks_are_reported_as_normalized() throws Exception {
        File rsgainExe = new File(toolsLocation, "rsgain.exe");
        File rsgainCmd = new File(toolsLocation, "rsgain");
        assumeTrue(rsgainExe.exists() || rsgainCmd.exists(), "rsgain tool is required for ReplayGain integration test");

        File track = copySmallMediaFile(true);
        File playlistFile = File.createTempFile("replaygain-apply-", ".m3u8");
        playlistFile.deleteOnExit();

        playlistUtils.createM3u8Playlist(playlistFile, List.of(track), false);
        playlistUtils.applyReplayGain(playlistFile);

        MediaPlaylistUtils.PlaylistReplayGainReport report = playlistUtils.getReplayGainDetails(playlistFile);
        assertThat(report.summary().trackCount()).isEqualTo(1);
        assertThat(report.summary().normalizedCount()).isEqualTo(1);
        assertThat(report.summary().missingReplayGainCount()).isZero();
        assertThat(report.summary().fullyNormalized()).isTrue();
        assertThat(report.tracks()).singleElement().satisfies(trackReport -> {
            assertThat(trackReport.fileName()).isEqualTo(track.getName());
            assertThat(trackReport.hasReplayGain()).isTrue();
            assertThat(trackReport.replayGainTrack()).isNotBlank();
            assertThat(trackReport.replayGainAlbum()).isNotBlank();
            assertThat(trackReport.note()).isBlank();
        });
    }
}
