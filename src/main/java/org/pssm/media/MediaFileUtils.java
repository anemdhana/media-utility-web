
package org.pssm.media;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Utility class for media file operations.
 * Tools used: yt-dlp, ffmpeg, replaygain
 * Tools location: configurable via Spring property
 * Media files directory: configurable via Spring property
 */
@Component
public class MediaFileUtils {
    private final Path mediaDir;
    private final Path toolsDir;

    public MediaFileUtils(
            @Value("${mediafiles_dir}") String mediaDir,
            @Value("${tools_location}") String toolsDir) {
        this.mediaDir = Paths.get(mediaDir);
        this.toolsDir = Paths.get(toolsDir);
    }

    /**
     * List all media files in the media directory (recursively).
     */
    public List<File> listAllMediaFiles() throws IOException {
        try (Stream<Path> paths = Files.walk(mediaDir)) {
            return paths.filter(Files::isRegularFile)
                    .filter(this::isMediaFile)
                    .map(Path::toFile)
                    .collect(Collectors.toList());
        }
    }

    /**
     * Filter media files by filename (case-insensitive, substring match) and/or label.
     * If filterName is null/empty, matches all. If filterLabel is null/empty, matches all.
     */
    public List<File> filterMediaFiles(String filterName, String filterLabel) throws IOException {
        return listAllMediaFiles().stream()
                .filter(f -> filterName == null || filterName.isEmpty() || f.getName().toLowerCase().contains(filterName.toLowerCase()))
                .filter(f -> filterLabel == null || filterLabel.isEmpty() || getLabelsOfFile(f).stream().anyMatch(l -> l.equalsIgnoreCase(filterLabel)))
                .collect(Collectors.toList());
    }

    /**
     * Get total duration (in seconds) of a media file using ffmpeg.
     */
    public double getDuration(File file) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(
                toolsDir.resolve("ffmpeg").toString(),
                "-i", file.getAbsolutePath(),
                "-hide_banner"
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();
        try (Scanner scanner = new Scanner(process.getInputStream())) {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if (line.contains("Duration:")) {
                    String dur = line.split("Duration:")[1].split(",")[0].trim();
                    return parseDurationToSeconds(dur);
                }
            }
        }
        process.waitFor();
        return 0;
    }

    private double parseDurationToSeconds(String duration) {
        // Format: HH:MM:SS.xx
        String[] parts = duration.split(":");
        double hours = Double.parseDouble(parts[0]);
        double minutes = Double.parseDouble(parts[1]);
        double seconds = Double.parseDouble(parts[2]);
        return hours * 3600 + minutes * 60 + seconds;
    }

    /**
     * Get file size in bytes.
     */
    public long getFileSize(File file) {
        return file.length();
    }


    /**
     * Get list of labels for a media file from its 'comment' metadata tag.
     */
    public List<String> getLabelsOfFile(File file) {
        String comment = getFileMetadataComment(file);
        if (comment == null || comment.isEmpty()) return Collections.emptyList();
        return Arrays.stream(comment.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * Add one or more labels to the file's 'comment' tag (comma-separated).
     */
    public void addLabels(File file, List<String> labelsToAdd) throws IOException, InterruptedException {
        Set<String> existing = new HashSet<>(getLabelsOfFile(file));
        for (String label : labelsToAdd) {
            existing.add(label.trim());
        }
        setFileMetadataComment(file, String.join(",", existing));
    }

    /**
     * Remove one or more labels from the file's 'comment' tag.
     */
    public void removeLabels(File file, List<String> labelsToRemove) throws IOException, InterruptedException {
        Set<String> existing = new HashSet<>(getLabelsOfFile(file));
        for (String label : labelsToRemove) {
            existing.removeIf(l -> l.equalsIgnoreCase(label.trim()));
        }
        setFileMetadataComment(file, String.join(",", existing));
    }

    /**
     * Clear all labels from the file's 'comment' tag.
     */
    public void clearAllLabels(File file) throws IOException, InterruptedException {
        setFileMetadataComment(file, "");
    }

    /**
     * Get all unique labels in the media directory (from metadata).
     */
    public Set<String> getAllLabels() throws IOException {
        Set<String> labels = new HashSet<>();
        for (File f : listAllMediaFiles()) {
            labels.addAll(getLabelsOfFile(f));
        }
        return labels;
    }

    /**
     * Check if the file contains all the given labels (case-insensitive).
     */
    public boolean containsLabels(File file, List<String> requiredLabels) {
        Set<String> fileLabels = getLabelsOfFile(file).stream().map(String::toLowerCase).collect(Collectors.toSet());
        for (String label : requiredLabels) {
            if (!fileLabels.contains(label.toLowerCase())) return false;
        }
        return true;
    }

    // --- Metadata helpers ---

    /**
     * Get the 'comment' tag from file metadata using ffprobe.
     */
    private String getFileMetadataComment(File file) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    toolsDir.resolve("ffprobe").toString(),
                    "-v", "quiet",
                    "-show_entries", "format_tags=comment",
                    "-of", "default=noprint_wrappers=1:nokey=1",
                    file.getAbsolutePath()
            );
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes()).trim();
            process.waitFor();
            return output;
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Set the 'comment' tag in file metadata using ffmpeg (in-place, atomic replace).
     */
    private void setFileMetadataComment(File file, String comment) throws IOException, InterruptedException {
        // Write to temp file, then replace original
        File temp = File.createTempFile("labeltmp", ".tmp", file.getParentFile());
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    toolsDir.resolve("ffmpeg").toString(),
                    "-i", file.getAbsolutePath(),
                    "-map_metadata", "0",
                    "-c", "copy",
                    "-metadata", "comment=" + comment,
                    temp.getAbsolutePath()
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();
            process.waitFor();
            // Replace original file
            Files.move(temp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } finally {
            temp.delete();
        }
    }



    private boolean isMediaFile(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".mp3") || name.endsWith(".mp4") || name.endsWith(".wav") || name.endsWith(".m4a") || name.endsWith(".flac") || name.endsWith(".aac");
    }

    /**
     * Filter media files by filename (case-insensitive, substring match) and/or labels (all must match).
     * If filterName is null/empty, matches all. If filterLabels is null/empty, matches all.
     */
    public List<File> filterMediaFiles(String filterName, List<String> filterLabels) throws IOException {
        return listAllMediaFiles().stream()
                .filter(f -> filterName == null || filterName.isEmpty() || f.getName().toLowerCase().contains(filterName.toLowerCase()))
                .filter(f -> filterLabels == null || filterLabels.isEmpty() || containsLabels(f, filterLabels))
                .collect(Collectors.toList());
    }
}
