
package org.pssm.media;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import org.pssm.media.MediaSplitUtils.OutputQuality;

import java.io.BufferedReader;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.io.InputStreamReader;
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
    private final MediaCommandRunner mediaCommandRunner;

    public MediaFileUtils(
            @Value("${mediafiles_dir}") String mediaDir,
            @Value("${tools_location}") String toolsDir,
            MediaCommandRunner mediaCommandRunner) {
        this.mediaDir = Paths.get(mediaDir);
        this.toolsDir = Paths.get(toolsDir);
        this.mediaCommandRunner = mediaCommandRunner;
    }

    /**
     * Extract best available audio for a YouTube video id and return the downloaded file.
     * If a matching audio file already exists in mediafiles_dir, reuse it.
     */
    public File extractAudioFromYoutubeVideoId(String videoId) throws IOException, InterruptedException {
        return extractAudioFromYoutubeVideoId(videoId, null);
    }

    public File extractThumbnailFromYoutubeVideoId(String videoId) throws IOException, InterruptedException {
        String normalizedVideoId = videoId == null ? "" : videoId.trim();
        if (normalizedVideoId.isEmpty()) {
            throw new IllegalArgumentException("videoId must not be blank");
        }

        File existingThumbnail = findDownloadedThumbnailByVideoId(normalizedVideoId);
        if (existingThumbnail != null) {
            return existingThumbnail;
        }

        Files.createDirectories(mediaDir);
        int exit = mediaCommandRunner.runThumbnailExtract(normalizedVideoId);
        if (exit != 0) {
            throw new IOException("Thumbnail extraction failed with exit code " + exit + " for videoId " + normalizedVideoId);
        }

        File downloadedThumbnail = findDownloadedThumbnailByVideoId(normalizedVideoId);
        if (downloadedThumbnail == null) {
            throw new IOException("Thumbnail extraction completed but no downloaded thumbnail was found for videoId " + normalizedVideoId);
        }

        return downloadedThumbnail;
    }

    public List<File> extractAudioFromYoutubePlaylistId(String playlistId) throws IOException, InterruptedException {
        return extractAudioFromYoutubePlaylistId(playlistId, null);
    }

    /**
     * Extract best available audio for a YouTube video id, optionally convert to a target quality preset,
     * and return the resulting file path.
     */
    public File extractAudioFromYoutubeVideoId(String videoId, OutputQuality quality) throws IOException, InterruptedException {
        String normalizedVideoId = videoId == null ? "" : videoId.trim();
        if (normalizedVideoId.isEmpty()) {
            throw new IllegalArgumentException("videoId must not be blank");
        }

        OutputQuality normalizedQuality = quality == null ? OutputQuality.YOUTUBE_UPLOAD : quality;

        if (normalizedQuality != OutputQuality.YOUTUBE_UPLOAD) {
            File existingQualityFile = findDownloadedAudioByVideoIdAndQuality(normalizedVideoId, normalizedQuality);
            if (existingQualityFile != null) {
                return existingQualityFile;
            }
        }

        File sourceAudio = findDownloadedAudioByVideoId(normalizedVideoId);
        if (sourceAudio == null) {
            Files.createDirectories(mediaDir);
            int exit = mediaCommandRunner.runAudioExtract(normalizedVideoId);
            if (exit != 0) {
                throw new IOException("Audio extraction failed with exit code " + exit + " for videoId " + normalizedVideoId);
            }

            sourceAudio = findDownloadedAudioByVideoId(normalizedVideoId);
            if (sourceAudio == null) {
                throw new IOException("Audio extraction completed but no downloaded file was found for videoId " + normalizedVideoId);
            }
        }

        if (normalizedQuality == OutputQuality.YOUTUBE_UPLOAD) {
            return sourceAudio;
        }

        if (isQualityOutputFile(sourceAudio, normalizedQuality)) {
            return sourceAudio;
        }

        File qualityFile = buildQualityOutputFile(sourceAudio, normalizedQuality);
        if (qualityFile.exists() && qualityFile.length() > 0) {
            deleteIfPresent(sourceAudio);
            return qualityFile;
        }

        int convertExit = mediaCommandRunner.runConvertToM4a(
                sourceAudio.getAbsolutePath(),
                qualityFile.getAbsolutePath(),
                audioCodecOptionsFor(normalizedQuality)
        );
        if (convertExit != 0) {
            throw new IOException("Audio conversion failed with exit code " + convertExit + " for videoId " + normalizedVideoId + " and quality " + normalizedQuality);
        }

        deleteIfPresent(sourceAudio);

        return qualityFile;
    }

    public File extractAndSplitAudioFromYoutubeVideoId(String videoId, String startTime, String endTime,
                                                       OutputQuality quality, String outputFormat) throws IOException, InterruptedException {
        String normalizedVideoId = videoId == null ? "" : videoId.trim();
        if (normalizedVideoId.isEmpty()) {
            throw new IllegalArgumentException("videoId must not be blank");
        }

        String normalizedStart = startTime == null ? "" : startTime.trim();
        String normalizedEnd = endTime == null ? "" : endTime.trim();
        if (normalizedStart.isEmpty() || normalizedEnd.isEmpty()) {
            throw new IllegalArgumentException("startTime and endTime must not be blank");
        }

        OutputQuality normalizedQuality = quality == null ? OutputQuality.YOUTUBE_UPLOAD : quality;
        String outExt = outputFormat == null || outputFormat.isBlank() ? "m4a" : outputFormat.trim().toLowerCase(Locale.ROOT);
        if (!"m4a".equals(outExt) && !"mp3".equals(outExt)) {
            throw new IllegalArgumentException("Audio output must be .mp3 or .m4a");
        }

        Files.createDirectories(mediaDir);
        YoutubeMetadata metadata = fetchYoutubeMetadata(normalizedVideoId);
        String temporaryFileName = "ytclip-" + normalizedVideoId + "-"
                + sanitizeForFileName(normalizedStart) + "-to-" + sanitizeForFileName(normalizedEnd)
                + "." + outExt;
        File temporaryOutputFile = mediaDir.resolve(temporaryFileName).toFile();

        String titledFileName = sanitizeForFileName(metadata.title()) + "." + outExt;
        File finalOutputFile = mediaDir.resolve(titledFileName).toFile();

        String codecOptions = codecOptionsForSplit(normalizedQuality, outExt);
        String formatOptions = "mp3".equals(outExt) ? "-f mp3" : "";

        int exit = mediaCommandRunner.runAudioExtractAndSplit(
                normalizedVideoId,
                temporaryOutputFile.getAbsolutePath(),
                normalizedStart,
                normalizedEnd,
                codecOptions,
                formatOptions
        );
        if (exit != 0) {
            throw new IOException("Audio extract+split failed with exit code " + exit + " for videoId " + normalizedVideoId);
        }

        if (!temporaryOutputFile.exists() || temporaryOutputFile.length() == 0L) {
            throw new IOException("Audio extract+split completed but no non-empty output file was produced for videoId " + normalizedVideoId);
        }

        embedMetadataAndThumbnail(temporaryOutputFile, finalOutputFile, metadata, outExt);
        deleteIfPresent(temporaryOutputFile);

        return finalOutputFile;
    }

    public List<File> extractAudioFromYoutubePlaylistId(String playlistId, OutputQuality quality) throws IOException, InterruptedException {
        String normalizedPlaylistId = playlistId == null ? "" : playlistId.trim();
        if (normalizedPlaylistId.isEmpty()) {
            throw new IllegalArgumentException("playlistId must not be blank");
        }

        OutputQuality normalizedQuality = quality == null ? OutputQuality.YOUTUBE_UPLOAD : quality;
        List<File> sourceFiles = resolveDownloadedPlaylistSourceFiles(normalizedPlaylistId);
        if (sourceFiles.isEmpty()) {
            Files.createDirectories(mediaDir);
            Set<String> existingAudioFiles = snapshotAudioFilePaths();
            int exit = mediaCommandRunner.runPlaylistAudioExtract(normalizedPlaylistId);
            if (exit != 0) {
                throw new IOException("Playlist audio extraction failed with exit code " + exit + " for playlistId " + normalizedPlaylistId);
            }

            sourceFiles = findNewDownloadedAudioFiles(existingAudioFiles);
            if (sourceFiles.isEmpty()) {
                throw new IOException("Playlist audio extraction completed but no downloaded files were found for playlistId " + normalizedPlaylistId);
            }
            writePlaylistManifest(normalizedPlaylistId, sourceFiles);
        }

        if (normalizedQuality == OutputQuality.YOUTUBE_UPLOAD) {
            return sourceFiles;
        }

        List<File> qualityFiles = new ArrayList<>();
        for (File sourceAudio : sourceFiles) {
            if (isQualityOutputFile(sourceAudio, normalizedQuality)) {
                qualityFiles.add(sourceAudio);
                continue;
            }

            File qualityFile = buildQualityOutputFile(sourceAudio, normalizedQuality);
            if (!qualityFile.exists() || qualityFile.length() == 0) {
                int convertExit = mediaCommandRunner.runConvertToM4a(
                        sourceAudio.getAbsolutePath(),
                        qualityFile.getAbsolutePath(),
                        audioCodecOptionsFor(normalizedQuality)
                );
                if (convertExit != 0) {
                    throw new IOException("Audio conversion failed with exit code " + convertExit + " for playlistId " + normalizedPlaylistId + " and quality " + normalizedQuality);
                }
            }
            qualityFiles.add(qualityFile);
            deleteIfPresent(sourceAudio);
        }

        writePlaylistManifest(normalizedPlaylistId, qualityFiles);
        return qualityFiles;
    }

    private void deleteIfPresent(File file) throws IOException {
        if (file != null && file.exists()) {
            Files.delete(file.toPath());
        }
    }

    private String sanitizeForFileName(String value) {
        String normalized = value == null ? "value" : value.trim();
        normalized = normalized.replace(':', '-').replace(' ', '-');
        normalized = normalized.replaceAll("[^A-Za-z0-9._-]", "-");
        normalized = normalized.replaceAll("-+", "-");
        normalized = normalized.replaceAll("^[.-]+|[.-]+$", "");
        return normalized.isBlank() ? "value" : normalized;
    }

    private String codecOptionsForSplit(OutputQuality quality, String outExt) {
        boolean isMp3 = "mp3".equalsIgnoreCase(outExt);
        return switch (quality) {
            case COMPACT_SIZE -> isMp3 ? "-c:a libmp3lame -b:a 80k -ar 44100" : "-c:a aac -b:a 80k -ar 44100";
            case COMPACT_SIZE_SPEECH -> isMp3 ? "-c:a libmp3lame -b:a 48k -ar 32000 -ac 1" : "-c:a aac -b:a 48k -ar 32000 -ac 1";
            case COMPACT_SIZE_MUSIC, COMPACT_MUSIC_INSTRUMENTAL -> isMp3 ? "-c:a libmp3lame -b:a 72k -ar 44100 -ac 2" : "-c:a aac -b:a 72k -ar 44100 -ac 2";
            case WHATSAPP -> isMp3 ? "-c:a libmp3lame -b:a 96k -ar 44100" : "-c:a aac -b:a 96k -ar 44100";
            case MUSIC_CONCERT -> isMp3 ? "-af replaygain=track -c:a libmp3lame -b:a 256k" : "-af replaygain=track -c:a aac -b:a 256k";
            case YOUTUBE_UPLOAD -> isMp3 ? "-c:a libmp3lame -b:a 192k" : "-c:a aac -b:a 192k";
        };
    }

    private YoutubeMetadata fetchYoutubeMetadata(String videoId) throws IOException, InterruptedException {
        String videoUrl = "https://www.youtube.com/watch?v=" + videoId;
        List<String> command = List.of(
                resolveToolExecutable("yt-dlp"),
                "--no-warnings",
                "--skip-download",
                "--print", "%(title)s",
                "--print", "%(uploader)s",
                "--print", "%(channel)s",
                "--print", "%(thumbnail)s",
                "--print", "%(webpage_url)s",
                videoUrl
        );

        List<String> lines = runCommandCaptureLines(command);
        String title = valueOrDefault(lineAt(lines, 0), videoId);
        String uploader = valueOrDefault(lineAt(lines, 1), "");
        String channel = valueOrDefault(lineAt(lines, 2), "");
        String thumbnailUrl = valueOrDefault(lineAt(lines, 3), "");
        String webpageUrl = valueOrDefault(lineAt(lines, 4), videoUrl);
        return new YoutubeMetadata(title, uploader, channel, thumbnailUrl, webpageUrl);
    }

    private void embedMetadataAndThumbnail(File sourceFile, File targetFile, YoutubeMetadata metadata, String outExt)
            throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(resolveToolExecutable("ffmpeg"));
        command.add("-i");
        command.add(sourceFile.getAbsolutePath());

        boolean hasThumbnail = metadata.thumbnailUrl() != null && !metadata.thumbnailUrl().isBlank();
        if (hasThumbnail) {
            command.add("-i");
            command.add(metadata.thumbnailUrl());
        }

        command.add("-map");
        command.add("0:a:0");
        if (hasThumbnail) {
            command.add("-map");
            command.add("1:v:0");
        }

        command.add("-c:a");
        command.add("copy");
        if (hasThumbnail) {
            command.add("-c:v");
            command.add("mjpeg");
            command.add("-disposition:v:0");
            command.add("attached_pic");
        }

        command.add("-map_metadata");
        command.add("0");
        command.add("-metadata");
        command.add("title=" + metadata.title());
        if (!metadata.uploader().isBlank()) {
            command.add("-metadata");
            command.add("artist=" + metadata.uploader());
        }
        if (!metadata.channel().isBlank()) {
            command.add("-metadata");
            command.add("album_artist=" + metadata.channel());
        }
        command.add("-metadata");
        command.add("comment=Source: " + metadata.webpageUrl());

        if ("mp3".equalsIgnoreCase(outExt)) {
            command.add("-id3v2_version");
            command.add("3");
        } else {
            command.add("-movflags");
            command.add("+faststart");
        }

        command.add("-y");
        command.add(targetFile.getAbsolutePath());

        int exitCode = runCommand(command);
        if (exitCode != 0) {
            throw new IOException("Failed to embed metadata/thumbnail for file " + sourceFile.getAbsolutePath());
        }
    }

    private String resolveToolExecutable(String toolBaseName) {
        Path direct = toolsDir.resolve(toolBaseName);
        if (Files.exists(direct)) {
            return direct.toString();
        }

        Path withExe = toolsDir.resolve(toolBaseName + ".exe");
        if (Files.exists(withExe)) {
            return withExe.toString();
        }

        return direct.toString();
    }

    private int runCommand(List<String> command) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        process.getInputStream().transferTo(System.out);
        return process.waitFor();
    }

    private List<String> runCommandCaptureLines(List<String> command) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();

        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("Command failed with exit code " + exitCode + ": " + String.join(" ", command));
        }

        return lines;
    }

    private String lineAt(List<String> lines, int index) {
        if (index < 0 || index >= lines.size()) {
            return "";
        }
        return lines.get(index);
    }

    private String valueOrDefault(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
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
        String comment = getMetadataTag(file, "comment");
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
        if (labelsToAdd == null || labelsToAdd.isEmpty()) {
            return;
        }

        Set<String> existing = new LinkedHashSet<>(getLabelsOfFile(file));
        for (String label : labelsToAdd) {
            if (label != null) {
                String trimmed = label.trim();
                if (!trimmed.isEmpty()) {
                    existing.add(trimmed);
                }
            }
        }
        setFileMetadataComment(file, String.join(",", existing));
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
            if (label == null || label.isBlank()) {
                continue;
            }
            if (!fileLabels.contains(label.toLowerCase())) return false;
        }
        return true;
    }

    // --- Metadata helpers ---

    /**
     * Get a metadata tag from a media file using ffprobe.
     */
    public String getMetadataTag(File file, String tagName) {
        if (tagName == null || tagName.isBlank()) {
            return "";
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    toolsDir.resolve("ffprobe").toString(),
                    "-v", "quiet",
                    "-show_entries", "format_tags=" + tagName,
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
        String normalizedComment = normalizeLabels(comment);
        String currentComment = normalizeLabels(String.join(",", getLabelsOfFile(file)));
        if (Objects.equals(currentComment, normalizedComment)) {
            return;
        }

        String extension = getExtension(file.getName());
        File temp = File.createTempFile("labeltmp", extension.isEmpty() ? ".tmp" : "." + extension, file.getParentFile());
        try {
            Files.deleteIfExists(temp.toPath());
            ProcessBuilder pb = new ProcessBuilder(
                    toolsDir.resolve("ffmpeg").toString(),
                    "-y",
                    "-i", file.getAbsolutePath(),
                    "-map", "0",
                    "-map", "-0:d?",
                    "-map_metadata", "0",
                    "-map_chapters", "0",
                    "-c", "copy",
                    "-metadata", "comment=" + normalizedComment,
                    temp.getAbsolutePath()
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes());
            int exit = process.waitFor();
            if (exit != 0) {
                throw new IOException("Failed to update labels for " + file.getName() + ":\n" + output);
            }
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp.toPath());
        }
    }

    private String normalizeLabels(String labels) {
        if (labels == null || labels.isBlank()) {
            return "";
        }
        return Arrays.stream(labels.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.joining(","));
    }

    private String getExtension(String fileName) {
        int idx = fileName.lastIndexOf('.');
        return idx >= 0 ? fileName.substring(idx + 1) : "";
    }

    private boolean isMediaFile(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".mp3") || name.endsWith(".mp4") || name.endsWith(".wav") || name.endsWith(".m4a") || name.endsWith(".flac") || name.endsWith(".aac");
    }

    private boolean isAudioFile(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".m4a") || name.endsWith(".flac") || name.endsWith(".aac") || name.endsWith(".ogg") || name.endsWith(".opus");
    }

    private Set<String> snapshotAudioFilePaths() throws IOException {
        if (!Files.exists(mediaDir)) {
            return Collections.emptySet();
        }

        try (Stream<Path> paths = Files.walk(mediaDir)) {
            return paths.filter(Files::isRegularFile)
                    .filter(this::isAudioFile)
                    .map(path -> path.toAbsolutePath().normalize().toString())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
    }

    private List<File> findNewDownloadedAudioFiles(Set<String> existingAudioFiles) throws IOException {
        try (Stream<Path> paths = Files.walk(mediaDir)) {
            return paths.filter(Files::isRegularFile)
                    .filter(this::isAudioFile)
                    .map(Path::toFile)
                    .filter(file -> file.length() > 0)
                    .filter(file -> !existingAudioFiles.contains(file.toPath().toAbsolutePath().normalize().toString()))
                    .sorted(Comparator.comparingInt(this::extractLeadingPlaylistIndex)
                            .thenComparing(File::getName, String.CASE_INSENSITIVE_ORDER))
                    .collect(Collectors.toCollection(ArrayList::new));
        }
    }

    private File findDownloadedAudioByVideoId(String videoId) throws IOException {
        if (!Files.exists(mediaDir)) {
            return null;
        }

        try (Stream<Path> paths = Files.walk(mediaDir)) {
            return paths.filter(Files::isRegularFile)
                    .filter(this::isAudioFile)
                    .map(Path::toFile)
                    .filter(file -> file.getName().toLowerCase(Locale.ROOT).contains(videoId.toLowerCase(Locale.ROOT)))
                    .filter(file -> file.length() > 0)
                    .sorted(Comparator.comparingLong(File::lastModified)
                            .thenComparingLong(File::length)
                            .reversed())
                    .findFirst()
                    .orElse(null);
        }
    }

    private File findDownloadedThumbnailByVideoId(String videoId) throws IOException {
        if (!Files.exists(mediaDir)) {
            return null;
        }

        try (Stream<Path> paths = Files.walk(mediaDir)) {
            return paths.filter(Files::isRegularFile)
                    .map(Path::toFile)
                    .filter(this::isThumbnailFile)
                    .filter(file -> file.getName().toLowerCase(Locale.ROOT).contains(videoId.toLowerCase(Locale.ROOT)))
                    .filter(file -> file.length() > 0)
                    .sorted(Comparator.comparingLong(File::lastModified)
                            .thenComparingLong(File::length)
                            .reversed())
                    .findFirst()
                    .orElse(null);
        }
    }

    private File buildQualityOutputFile(File sourceAudio, OutputQuality quality) {
        String sourceName = sourceAudio.getName();
        int dotIndex = sourceName.lastIndexOf('.');
        String stem = dotIndex > 0 ? sourceName.substring(0, dotIndex) : sourceName;
        String qualitySuffix = quality.name().toLowerCase(Locale.ROOT);
        String outputName = stem + "-" + qualitySuffix + ".m4a";
        return mediaDir.resolve(outputName).toFile();
    }

    private File findDownloadedAudioByVideoIdAndQuality(String videoId, OutputQuality quality) throws IOException {
        if (!Files.exists(mediaDir)) {
            return null;
        }

        try (Stream<Path> paths = Files.walk(mediaDir)) {
            return paths.filter(Files::isRegularFile)
                    .filter(this::isAudioFile)
                    .map(Path::toFile)
                    .filter(file -> file.getName().toLowerCase(Locale.ROOT).contains(videoId.toLowerCase(Locale.ROOT)))
                    .filter(file -> isQualityOutputFile(file, quality))
                    .filter(file -> file.length() > 0)
                    .sorted(Comparator.comparingLong(File::lastModified)
                            .thenComparingLong(File::length)
                            .reversed())
                    .findFirst()
                    .orElse(null);
        }
    }

    private boolean isQualityOutputFile(File file, OutputQuality quality) {
        String lowerName = file.getName().toLowerCase(Locale.ROOT);
        String qualitySuffix = "-" + quality.name().toLowerCase(Locale.ROOT) + ".m4a";
        return lowerName.endsWith(qualitySuffix);
    }

    private boolean isThumbnailFile(File file) {
        String lowerName = file.getName().toLowerCase(Locale.ROOT);
        return lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") || lowerName.endsWith(".png") || lowerName.endsWith(".webp");
    }

    private List<File> resolveDownloadedPlaylistSourceFiles(String playlistId) throws IOException {
        Path manifestPath = playlistManifestPath(playlistId);
        if (!Files.exists(manifestPath)) {
            return Collections.emptyList();
        }

        List<String> fileNames = Files.readAllLines(manifestPath).stream()
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .collect(Collectors.toCollection(ArrayList::new));
        if (fileNames.isEmpty()) {
            return Collections.emptyList();
        }

        List<File> resolvedFiles = new ArrayList<>();
        for (String fileName : fileNames) {
            File file = mediaDir.resolve(fileName).toFile();
            if (!file.exists() || file.length() == 0) {
                return Collections.emptyList();
            }
            resolvedFiles.add(file);
        }
        return resolvedFiles;
    }

    private void writePlaylistManifest(String playlistId, List<File> sourceFiles) throws IOException {
        Path manifestPath = playlistManifestPath(playlistId);
        List<String> fileNames = sourceFiles.stream()
                .map(File::getName)
                .collect(Collectors.toCollection(ArrayList::new));
        Files.write(manifestPath, fileNames);
    }

    private Path playlistManifestPath(String playlistId) {
        return mediaDir.resolve(".playlist-" + sanitizeForFileName(playlistId) + ".txt");
    }

    private int extractLeadingPlaylistIndex(File file) {
        String name = file.getName();
        int underscoreIndex = name.indexOf('_');
        if (underscoreIndex <= 0) {
            return Integer.MAX_VALUE;
        }

        try {
            return Integer.parseInt(name.substring(0, underscoreIndex));
        } catch (NumberFormatException ignored) {
            return Integer.MAX_VALUE;
        }
    }

    private String audioCodecOptionsFor(OutputQuality quality) {
        return switch (quality) {
            case COMPACT_SIZE -> "-c:a aac -b:a 80k -ar 44100 -ac 2";
            case COMPACT_SIZE_SPEECH -> "-c:a aac -b:a 48k -ar 32000 -ac 1";
            case COMPACT_SIZE_MUSIC -> "-c:a aac -b:a 72k -ar 44100 -ac 2";
            case COMPACT_MUSIC_INSTRUMENTAL -> "-c:a aac -b:a 72k -ar 44100 -ac 2";
            case WHATSAPP -> "-c:a aac -b:a 96k -ar 44100 -ac 2";
            case MUSIC_CONCERT -> "-c:a aac -b:a 256k -ar 48000 -ac 2";
            case YOUTUBE_UPLOAD -> "-c:a aac -b:a 192k -ar 44100 -ac 2";
        };
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

    private record YoutubeMetadata(String title, String uploader, String channel, String thumbnailUrl, String webpageUrl) {
    }
}
