package org.pssm.bdd;

import org.pssm.media.MediaFileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

abstract class MediaBddSupport {
    private static final List<String> AUDIO_EXTENSIONS = List.of("mp3", "m4a", "aac", "wav", "flac");
    private static final List<String> VIDEO_EXTENSIONS = List.of("mp4", "webm", "mkv", "mov", "avi");

    @Autowired
    protected MediaFileUtils mediaFileUtils;

    @Value("${mediafiles_dir}")
    private String mediaFilesDir;

    @Value("${tools_location}")
    private String toolsLocation;

    protected File createLabelTestFileCopy() throws Exception {
        List<File> files = mediaFileUtils.listAllMediaFiles();
        assertThat(files).isNotEmpty();
        File source = files.stream()
                .filter(f -> f.getName().endsWith(".m4a") || f.getName().endsWith(".mp4"))
                .findFirst()
                .orElseThrow();

        String fileName = source.getName();
        int dotIndex = fileName.lastIndexOf('.');
        String suffix = dotIndex >= 0 ? fileName.substring(dotIndex) : ".tmp";
        Path tempFile = Files.createTempFile("bdd-label-test-", suffix);
        Files.copy(source.toPath(), tempFile, StandardCopyOption.REPLACE_EXISTING);
        tempFile.toFile().deleteOnExit();
        return tempFile.toFile();
    }

    protected File copySmallMediaFile(boolean audioOnly) throws Exception {
        List<File> candidates = mediaFileUtils.listAllMediaFiles().stream()
                .filter(File::isFile)
                .filter(file -> {
                    String lower = file.getName().toLowerCase(Locale.ROOT);
                    if (audioOnly) {
                        return AUDIO_EXTENSIONS.stream().anyMatch(ext -> lower.endsWith("." + ext));
                    }
                    return AUDIO_EXTENSIONS.stream().anyMatch(ext -> lower.endsWith("." + ext))
                            || VIDEO_EXTENSIONS.stream().anyMatch(ext -> lower.endsWith("." + ext));
                })
                .sorted(Comparator.comparingLong(File::length))
                .collect(Collectors.toList());

        assertThat(candidates).isNotEmpty();
        File source = candidates.get(0);

        String name = source.getName();
        int dotIndex = name.lastIndexOf('.');
        String suffix = dotIndex >= 0 ? name.substring(dotIndex) : ".tmp";
        Path copyPath = Files.createTempFile("bdd-playlist-track-", suffix);
        Files.copy(source.toPath(), copyPath, StandardCopyOption.REPLACE_EXISTING);
        copyPath.toFile().deleteOnExit();
        return copyPath.toFile();
    }

    protected File pickAudioTestFile(String... excludedNames) {
        return pickTestFile(AUDIO_EXTENSIONS, excludedNames);
    }

    protected File pickVideoTestFile(String... excludedNames) {
        return pickTestFile(VIDEO_EXTENSIONS, excludedNames);
    }

    protected File pickMediaFileByVideoId(String videoId) throws IOException {
        List<File> matches = mediaFileUtils.listAllMediaFiles().stream()
                .filter(File::isFile)
                .filter(file -> file.getName().toLowerCase(Locale.ROOT).contains(videoId.toLowerCase(Locale.ROOT)))
                .sorted(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());

        assertThat(matches)
                .as("Expected at least one media file to contain video id %s", videoId)
                .isNotEmpty();

        return matches.stream()
                .filter(this::isAudioFile)
                .findFirst()
                .orElse(matches.get(0));
    }

    protected boolean isAudioFile(File file) {
        String lower = file.getName().toLowerCase(Locale.ROOT);
        return AUDIO_EXTENSIONS.stream().anyMatch(ext -> lower.endsWith("." + ext));
    }

    protected File createTempFile(String prefix, String suffix) throws IOException {
        File file = File.createTempFile(prefix, suffix);
        file.deleteOnExit();
        return file;
    }

    protected File createSiblingOutputFile(File inputFile, String prefix, String suffix) throws IOException {
        File parentDir = inputFile.getAbsoluteFile().getParentFile();
        return File.createTempFile(prefix, suffix, parentDir);
    }

    protected File createReadableSiblingOutputFile(File inputFile, String label, String startTime, String endTime, String extension) throws IOException {
        File parentDir = inputFile.getAbsoluteFile().getParentFile();
        String baseName = inputFile.getName();
        int dotIndex = baseName.lastIndexOf('.');
        if (dotIndex >= 0) {
            baseName = baseName.substring(0, dotIndex);
        }

        String safeBaseName = sanitizeForFileName(baseName);
        String safeLabel = sanitizeForFileName(label);
        String safeRange = sanitizeForFileName(startTime) + "-to-" + sanitizeForFileName(endTime);
        String fileName = safeBaseName + "-" + safeLabel + "-" + safeRange + "." + extension;
        return new File(parentDir, fileName);
    }

    protected String sanitizeForFileName(String value) {
        String normalized = value == null ? "value" : value.trim();
        normalized = normalized.replace(':', '-').replace(' ', '-');
        normalized = normalized.replaceAll("[^A-Za-z0-9._-]", "-");
        normalized = normalized.replaceAll("-+", "-");
        normalized = normalized.replaceAll("^[.-]+|[.-]+$", "");
        return normalized.isBlank() ? "value" : normalized;
    }

    protected String getToolsLocation() {
        return toolsLocation;
    }

    private File pickTestFile(List<String> extensions, String... excludedNames) {
        File dir = new File(mediaFilesDir);
        File[] files = dir.listFiles((currentDir, name) -> {
            String lowerName = name.toLowerCase(Locale.ROOT);
            boolean matchesExtension = extensions.stream().anyMatch(ext -> lowerName.endsWith("." + ext));
            boolean isExcludedName = Arrays.stream(excludedNames).anyMatch(excluded -> lowerName.equals(excluded.toLowerCase(Locale.ROOT)));
            boolean isGeneratedArtifact = lowerName.startsWith("audio_split_test_")
                    || lowerName.startsWith("video_split_test_")
                    || lowerName.startsWith("audio-split-")
                    || lowerName.startsWith("video-split-")
                    || lowerName.startsWith("bdd-");
            return matchesExtension && !isExcludedName && !isGeneratedArtifact;
        });

        if (files == null || files.length == 0) {
            throw new IllegalStateException("No matching files found in " + mediaFilesDir);
        }

        Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        return files[0];
    }
}
