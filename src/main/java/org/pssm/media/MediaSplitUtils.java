package org.pssm.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Utility for splitting media files by time, with quality and format options.
 */

@Component
public class MediaSplitUtils {
    private static final Logger log = LoggerFactory.getLogger(MediaSplitUtils.class);
    private final MediaCommandRunner mediaCommandRunner;

    public MediaSplitUtils(MediaCommandRunner mediaCommandRunner) {
        this.mediaCommandRunner = mediaCommandRunner;
    }

    /**
     * Output quality presets.
     */
    public enum OutputQuality {
        WHATSAPP, YOUTUBE_UPLOAD, MUSIC_CONCERT
    }


    /**
     * Split an audio file by time, with quality and format options.
     * Only allows audio output formats (mp3, m4a).
     */
    public void splitAudioFile(File inputFile, String startTime, String endTime, OutputQuality quality, String outputFormat, File outputFile) throws IOException, InterruptedException {
        String outExt = getExtension(outputFile.getName());
        boolean isMp3 = outExt.equalsIgnoreCase("mp3");
        boolean isM4a = outExt.equalsIgnoreCase("m4a");
        if (!isMp3 && !isM4a) {
            throw new IllegalArgumentException("Audio output must be .mp3 or .m4a");
        }

        log.info("Splitting audio: {} ({} to {}) as {} quality, format: {}", inputFile, startTime, endTime, quality, outExt);
        if (outputFile.exists()) {
            log.warn("Audio split target {} already exists and will be replaced", outputFile);
        }

        String codecOptions;
        switch (quality) {
            case WHATSAPP:
                codecOptions = isMp3
                        ? "-c:a libmp3lame -b:a 96k -ar 44100"
                        : "-c:a aac -b:a 96k -ar 44100";
                break;
            case YOUTUBE_UPLOAD:
                codecOptions = isMp3
                        ? "-c:a libmp3lame -b:a 192k"
                        : "-c:a aac -b:a 192k";
                break;
            case MUSIC_CONCERT:
                codecOptions = isMp3
                        ? "-af replaygain=track -c:a libmp3lame -b:a 256k"
                        : "-af replaygain=track -c:a aac -b:a 256k";
                break;
            default:
                throw new IllegalArgumentException("Unsupported audio quality: " + quality);
        }

        String formatOptions = isMp3 ? "-f mp3" : "";
        boolean replaceOriginal = inputFile.getCanonicalFile().equals(outputFile.getCanonicalFile());
        File actualOutputFile = outputFile;
        if (replaceOriginal) {
            File parentDir = outputFile.getAbsoluteFile().getParentFile();
            actualOutputFile = File.createTempFile("audio-split-", "." + outExt, parentDir);
            log.warn("Audio split target matches input file; using temporary output {} before replacing original", actualOutputFile);
        }

        int exit = mediaCommandRunner.runAudioSplit(
                inputFile.getAbsolutePath(),
                actualOutputFile.getAbsolutePath(),
                startTime,
                endTime,
                codecOptions,
                formatOptions
        );
        if (exit != 0) {
            if (replaceOriginal && actualOutputFile.exists()) {
                Files.deleteIfExists(actualOutputFile.toPath());
            }
            throw new IOException("ffmpeg failed with exit code " + exit + " while splitting audio: " + inputFile.getAbsolutePath());
        }

        if (replaceOriginal) {
            Files.move(actualOutputFile.toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }

        log.info("Audio split complete: {} -> {}", inputFile, outputFile);
        copySidecar(inputFile, outputFile, ".labels");
        copySidecar(inputFile, outputFile, ".description");
    }

    /**
     * Split a video file by time, with quality, format, and optional crop.
     * Only allows video output formats (mp4).
     */
    public void splitVideoFile(File inputFile, String startTime, String endTime, OutputQuality quality, String outputFormat, String crop, File outputFile) throws IOException, InterruptedException {
        String outExt = getExtension(outputFile.getName());
        boolean isMp4 = outExt.equalsIgnoreCase("mp4");
        if (!isMp4) {
            throw new IllegalArgumentException("Video output must be .mp4");
        }

        log.info("Splitting video: {} ({} to {}) as {} quality, format: {}, crop: {}", inputFile, startTime, endTime, quality, outExt, crop);
        if (outputFile.exists()) {
            log.warn("Video split target {} already exists and will be replaced", outputFile);
        }

        String videoFilterArgs = "";
        String codecOptions;
        switch (quality) {
            case WHATSAPP:
                if (crop != null && !crop.isBlank()) {
                    videoFilterArgs = "-vf \"" + crop + "\"";
                }
                codecOptions = "-c:v libx264 -crf 23 -preset medium -c:a aac -b:a 128k -fs 15M -movflags +faststart";
                break;
            case YOUTUBE_UPLOAD:
                if (crop != null && !crop.isBlank()) {
                    videoFilterArgs = "-vf \"" + crop + "\"";
                }
                codecOptions = "-c:v libx264 -crf 20 -preset medium -c:a aac -b:a 192k -movflags +faststart";
                break;
            case MUSIC_CONCERT:
                if (crop != null && !crop.isBlank()) {
                    videoFilterArgs = "-vf \"" + crop + "\"";
                }
                codecOptions = "-c:v libx264 -crf 18 -preset slow -c:a aac -b:a 256k -movflags +faststart";
                break;
            default:
                throw new IllegalArgumentException("Unsupported video quality: " + quality);
        }

        File actualOutputFile = outputFile;
        boolean replaceOriginal = inputFile.getCanonicalFile().equals(outputFile.getCanonicalFile());
        if (replaceOriginal) {
            File parentDir = outputFile.getAbsoluteFile().getParentFile();
            actualOutputFile = File.createTempFile("video-split-", ".mp4", parentDir);
            log.warn("Output file matches input file; using temporary output {} before replacing original", actualOutputFile);
        }

        int exit = mediaCommandRunner.runVideoSplit(
                inputFile.getAbsolutePath(),
                actualOutputFile.getAbsolutePath(),
                startTime,
                endTime,
                videoFilterArgs,
                codecOptions
        );
        if (exit != 0) {
            if (replaceOriginal && actualOutputFile.exists()) {
                Files.deleteIfExists(actualOutputFile.toPath());
            }
            throw new IOException("ffmpeg failed with exit code " + exit + " while splitting video: " + inputFile.getAbsolutePath());
        }

        if (replaceOriginal) {
            Files.move(actualOutputFile.toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }

        log.info("Video split complete: {} -> {}", inputFile, outputFile);
        copySidecar(inputFile, outputFile, ".labels");
        copySidecar(inputFile, outputFile, ".description");
    }

    private void copySidecar(File src, File dest, String ext) throws IOException {
        File srcSidecar = new File(src.getAbsolutePath() + ext);
        File destSidecar = new File(dest.getAbsolutePath() + ext);
        if (srcSidecar.exists()) {
            if (srcSidecar.getCanonicalFile().equals(destSidecar.getCanonicalFile())) {
                return;
            }
            Files.copy(srcSidecar.toPath(), destSidecar.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String getExtension(String name) {
        int idx = name.lastIndexOf('.');
        return idx >= 0 ? name.substring(idx + 1).toLowerCase() : "";
    }
}
