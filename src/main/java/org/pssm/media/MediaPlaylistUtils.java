package org.pssm.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Utility class for M3U8 playlist operations and ReplayGain reporting.
 */
@Component
public class MediaPlaylistUtils {
    private static final Logger log = LoggerFactory.getLogger(MediaPlaylistUtils.class);

    private final MediaFileUtils mediaFileUtils;
    private final MediaCommandRunner mediaCommandRunner;
    private final Path mediaDir;

    public MediaPlaylistUtils(MediaFileUtils mediaFileUtils,
                              MediaCommandRunner mediaCommandRunner,
                              @Value("${mediafiles_dir}") String mediaFilesDir) {
        this.mediaFileUtils = mediaFileUtils;
        this.mediaCommandRunner = mediaCommandRunner;
        this.mediaDir = Paths.get(mediaFilesDir);
    }

    /**
     * Create or replace an M3U8 playlist for the given media files.
     */
    public File createM3u8Playlist(String playlistName, List<File> mediaFiles, boolean applyReplayGain) throws IOException, InterruptedException {
        return createM3u8Playlist(resolvePlaylistFile(playlistName), mediaFiles, applyReplayGain);
    }

    /**
     * Create or replace an M3U8 playlist at the given file location.
     */
    public File createM3u8Playlist(File playlistFile, List<File> mediaFiles, boolean applyReplayGain) throws IOException, InterruptedException {
        writePlaylist(playlistFile, mediaFiles);
        if (applyReplayGain) {
            applyReplayGain(playlistFile);
        }
        return playlistFile;
    }

    /**
     * Read all track entries from a playlist file.
     */
    public List<File> getPlaylistTracks(File playlistFile) throws IOException {
        return readPlaylistEntries(playlistFile);
    }

    /**
     * Add tracks to an existing playlist, keeping order and avoiding duplicates.
     */
    public void addTracks(File playlistFile, List<File> tracksToAdd) throws IOException, InterruptedException {
        List<File> mergedTracks = new ArrayList<>(readPlaylistEntriesIfExists(playlistFile));
        Set<String> existingPaths = mergedTracks.stream()
                .map(this::canonicalPathSafe)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        for (File track : safeFiles(tracksToAdd)) {
            String path = canonicalPathSafe(track);
            if (existingPaths.add(path)) {
                mergedTracks.add(track);
            }
        }

        writePlaylist(playlistFile, mergedTracks);
    }

    /**
     * Remove tracks from an existing playlist.
     */
    public void removeTracks(File playlistFile, List<File> tracksToRemove) throws IOException, InterruptedException {
        Set<String> removalPaths = safeFiles(tracksToRemove).stream()
                .map(this::canonicalPathSafe)
                .collect(Collectors.toSet());

        List<File> remainingTracks = readPlaylistEntries(playlistFile).stream()
                .filter(track -> !removalPaths.contains(canonicalPathSafe(track)))
                .collect(Collectors.toList());

        writePlaylist(playlistFile, remainingTracks);
    }

    /**
     * Remove all tracks from the given playlist file while keeping the M3U header.
     */
    public void clearTracks(File playlistFile) throws IOException {
        Files.createDirectories(playlistFile.toPath().toAbsolutePath().getParent());
        Files.write(playlistFile.toPath(), List.of("#EXTM3U"), StandardCharsets.UTF_8);
    }

    /**
     * Apply ReplayGain to the audio tracks referenced by a playlist.
     */
    public void applyReplayGain(File playlistFile) throws IOException, InterruptedException {
        List<String> audioTrackPaths = readPlaylistEntries(playlistFile).stream()
                .filter(File::exists)
                .filter(this::isAudioFile)
                .map(File::getAbsolutePath)
                .distinct()
                .collect(Collectors.toList());

        if (audioTrackPaths.isEmpty()) {
            log.info("No audio tracks found in playlist {} for ReplayGain processing", playlistFile.getAbsolutePath());
            return;
        }

        int exit = mediaCommandRunner.runBatchReplayGain(audioTrackPaths);
        if (exit != 0) {
            throw new IOException("ReplayGain application failed with exit code " + exit + " for playlist " + playlistFile.getAbsolutePath());
        }
    }

    /**
     * Build a ReplayGain report for all tracks in the playlist.
     */
    public PlaylistReplayGainReport getReplayGainDetails(File playlistFile) throws IOException {
        List<File> tracks = readPlaylistEntries(playlistFile);
        List<PlaylistReplayGainTrack> trackReports = new ArrayList<>();

        int normalizedCount = 0;
        int missingReplayGainCount = 0;

        for (File track : tracks) {
            if (!track.exists()) {
                missingReplayGainCount++;
                trackReports.add(new PlaylistReplayGainTrack(track.getName(), "", "", false, "File not found"));
                continue;
            }

            String replayGainTrack = defaultString(mediaFileUtils.getMetadataTag(track, "replaygain_track_gain"));
            String replayGainAlbum = defaultString(mediaFileUtils.getMetadataTag(track, "replaygain_album_gain"));
            boolean hasReplayGain = !replayGainTrack.isBlank() || !replayGainAlbum.isBlank();

            if (hasReplayGain) {
                normalizedCount++;
            } else {
                missingReplayGainCount++;
            }

            trackReports.add(new PlaylistReplayGainTrack(
                    track.getName(),
                    replayGainTrack,
                    replayGainAlbum,
                    hasReplayGain,
                    hasReplayGain ? "" : "ReplayGain tag not found"
            ));
        }

        return new PlaylistReplayGainReport(
                playlistFile.getName(),
                trackReports,
                new PlaylistReplayGainSummary(trackReports.size(), normalizedCount, missingReplayGainCount)
        );
    }

    /**
     * Get the total duration of all tracks referenced by the playlist, in seconds.
     */
    public double getTotalDuration(File playlistFile) throws IOException, InterruptedException {
        double totalDuration = 0.0;
        for (File track : readPlaylistEntries(playlistFile)) {
            if (track.exists()) {
                totalDuration += mediaFileUtils.getDuration(track);
            }
        }
        return totalDuration;
    }

    private void writePlaylist(File playlistFile, List<File> mediaFiles) throws IOException, InterruptedException {
        Files.createDirectories(playlistFile.toPath().toAbsolutePath().getParent());

        Map<String, File> uniqueTracks = new LinkedHashMap<>();
        for (File mediaFile : safeFiles(mediaFiles)) {
            uniqueTracks.putIfAbsent(canonicalPathSafe(mediaFile), mediaFile);
        }

        List<String> lines = new ArrayList<>();
        lines.add("#EXTM3U");

        for (File mediaFile : uniqueTracks.values()) {
            double duration = 0.0;
            try {
                duration = mediaFileUtils.getDuration(mediaFile);
            } catch (Exception ex) {
                log.warn("Could not read duration for {}: {}", mediaFile.getAbsolutePath(), ex.getMessage());
            }
            lines.add(String.format(Locale.US, "#EXTINF:%.0f,%s", Math.max(0.0, duration), mediaFile.getName()));
            lines.add(mediaFile.toURI().toString());
        }

        Files.write(playlistFile.toPath(), lines, StandardCharsets.UTF_8);
        log.info("Playlist written: {} with {} track(s)", playlistFile.getAbsolutePath(), uniqueTracks.size());
    }

    private List<File> readPlaylistEntries(File playlistFile) throws IOException {
        if (!playlistFile.exists()) {
            throw new IOException("Playlist file not found: " + playlistFile.getAbsolutePath());
        }

        List<File> tracks = new ArrayList<>();
        for (String rawLine : Files.readAllLines(playlistFile.toPath(), StandardCharsets.UTF_8)) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            tracks.add(resolvePlaylistEntry(line, playlistFile.getAbsoluteFile().getParentFile()));
        }
        return tracks;
    }

    private List<File> readPlaylistEntriesIfExists(File playlistFile) throws IOException {
        if (playlistFile.exists()) {
            return readPlaylistEntries(playlistFile);
        }
        return new ArrayList<>();
    }

    private File resolvePlaylistFile(String playlistName) {
        String normalizedName = playlistName.endsWith(".m3u8") ? playlistName : playlistName + ".m3u8";
        Path playlistPath = Paths.get(normalizedName);
        if (!playlistPath.isAbsolute()) {
            playlistPath = mediaDir.resolve(normalizedName);
        }
        return playlistPath.toFile();
    }

    private File resolvePlaylistEntry(String entry, File playlistParentDir) {
        String decodedEntry = URLDecoder.decode(entry, StandardCharsets.UTF_8);
        try {
            if (decodedEntry.startsWith("file:")) {
                return Paths.get(URI.create(decodedEntry)).toFile();
            }
        } catch (Exception ex) {
            log.debug("Could not parse playlist URI {}: {}", decodedEntry, ex.getMessage());
        }

        Path entryPath = Paths.get(decodedEntry);
        if (!entryPath.isAbsolute() && playlistParentDir != null) {
            entryPath = playlistParentDir.toPath().resolve(entryPath).normalize();
        }
        return entryPath.toFile();
    }

    private List<File> safeFiles(List<File> files) {
        if (files == null) {
            return Collections.emptyList();
        }
        return files.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private boolean isAudioFile(File file) {
        String lowerName = file.getName().toLowerCase(Locale.ROOT);
        return lowerName.endsWith(".mp3")
                || lowerName.endsWith(".m4a")
                || lowerName.endsWith(".aac")
                || lowerName.endsWith(".wav")
                || lowerName.endsWith(".flac");
    }

    private String canonicalPathSafe(File file) {
        try {
            return file.getCanonicalPath();
        } catch (IOException ex) {
            return file.getAbsolutePath();
        }
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    public record PlaylistReplayGainTrack(String fileName,
                                          String replayGainTrack,
                                          String replayGainAlbum,
                                          boolean hasReplayGain,
                                          String note) {
    }

    public record PlaylistReplayGainSummary(int trackCount,
                                            int normalizedCount,
                                            int missingReplayGainCount) {
        public boolean fullyNormalized() {
            return trackCount > 0 && missingReplayGainCount == 0 && normalizedCount == trackCount;
        }
    }

    public record PlaylistReplayGainReport(String playlistName,
                                           List<PlaylistReplayGainTrack> tracks,
                                           PlaylistReplayGainSummary summary) {
    }
}
