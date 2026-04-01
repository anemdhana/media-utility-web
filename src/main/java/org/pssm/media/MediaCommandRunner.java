package org.pssm.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Component
public class MediaCommandRunner {
    private static final Logger log = LoggerFactory.getLogger(MediaCommandRunner.class);

    @Value("${tools_location}")
    private String toolsLocation;
    @Value("${download_location:${mediafiles_dir}}")
    private String downloadLocation;
    @Value("${audio_extract}")
    private String audioExtractCmd;
    @Value("${convert_to_m4a}")
    private String convertToM4aCmd;
    @Value("${audio_split}")
    private String audioSplitCmd;
    @Value("${video_extract}")
    private String videoExtractCmd;
    @Value("${video_resize}")
    private String videoResizeCmd;
    @Value("${video_resize_fragment}")
    private String videoResizeFragmentCmd;
    @Value("${video_split}")
    private String videoSplitCmd;
    @Value("${calculate_replaygain}")
    private String calculateReplayGainCmd;

    public int runAudioExtract(String videoId) throws IOException, InterruptedException {
        Map<String, String> params = new HashMap<>();
        params.put("toolsLocation", toolsLocation.endsWith("/") ? toolsLocation : toolsLocation + "/");
        params.put("downloadLocation", downloadLocation);
        params.put("videoId", videoId);
        String cmd = substitute(audioExtractCmd, params);
        return runShell(cmd);
    }

    public int runConvertToM4a(String inputFile, String outputFile) throws IOException, InterruptedException {
        return runConvertToM4a(inputFile, outputFile, "-c:a aac -b:a 128k -ar 44100 -ac 2");
    }

    public int runConvertToM4a(String inputFile, String outputFile, String audioCodecOptions) throws IOException, InterruptedException {
        Map<String, String> params = new HashMap<>();
        params.put("toolsLocation", toolsLocation.endsWith("/") ? toolsLocation : toolsLocation + "/");
        params.put("inputFile", inputFile);
        params.put("outputFile", outputFile);
        params.put("audioCodecOptions", audioCodecOptions == null || audioCodecOptions.isBlank()
                ? "-c:a aac -b:a 128k -ar 44100 -ac 2"
                : audioCodecOptions);
        String cmd = substitute(convertToM4aCmd, params);
        return runShell(cmd);
    }

    public int runAudioSplit(String inputFile, String outputFile, String startTime, String endTime,
                             String codecOptions, String formatOptions) throws IOException, InterruptedException {
        Map<String, String> params = new HashMap<>();
        params.put("toolsLocation", toolsLocation.endsWith("/") ? toolsLocation : toolsLocation + "/");
        params.put("inputFile", inputFile);
        params.put("outputFile", outputFile);
        params.put("startArgs", startTime != null && !startTime.isBlank() ? "-ss " + startTime : "");
        params.put("endArgs", endTime != null && !endTime.isBlank() ? "-to " + endTime : "");
        params.put("codecOptions", codecOptions != null ? codecOptions : "");
        params.put("formatOptions", formatOptions != null ? formatOptions : "");
        String cmd = substitute(audioSplitCmd, params).trim();
        return runShell(cmd);
    }

    public int runVideoExtract(String videoId) throws IOException, InterruptedException {
        Map<String, String> params = new HashMap<>();
        params.put("toolsLocation", toolsLocation.endsWith("/") ? toolsLocation : toolsLocation + "/");
        params.put("videoId", videoId);
        String cmd = substitute(videoExtractCmd, params);
        return runShell(cmd);
    }

    public int runVideoResize(String inputFile, String outputFile) throws IOException, InterruptedException {
        Map<String, String> params = new HashMap<>();
        params.put("toolsLocation", toolsLocation.endsWith("/") ? toolsLocation : toolsLocation + "/");
        params.put("inputFile", inputFile);
        params.put("outputFile", outputFile);
        String cmd = substitute(videoResizeCmd, params);
        return runShell(cmd);
    }

    public int runVideoResizeFragment(String inputFile, String outputFile, String startTime, String endTime) throws IOException, InterruptedException {
        Map<String, String> params = new HashMap<>();
        params.put("toolsLocation", toolsLocation.endsWith("/") ? toolsLocation : toolsLocation + "/");
        params.put("inputFile", inputFile);
        params.put("outputFile", outputFile);
        params.put("startTime", startTime);
        params.put("endTime", endTime);
        String cmd = substitute(videoResizeFragmentCmd, params);
        return runShell(cmd);
    }

    public int runVideoSplit(String inputFile, String outputFile, String startTime, String endTime,
                             String videoFilterArgs, String codecOptions) throws IOException, InterruptedException {
        Map<String, String> params = new HashMap<>();
        params.put("toolsLocation", toolsLocation.endsWith("/") ? toolsLocation : toolsLocation + "/");
        params.put("inputFile", inputFile);
        params.put("outputFile", outputFile);
        params.put("startArgs", startTime != null && !startTime.isBlank() ? "-ss " + startTime : "");
        params.put("endArgs", endTime != null && !endTime.isBlank() ? "-to " + endTime : "");
        params.put("videoFilterArgs", videoFilterArgs != null ? videoFilterArgs : "");
        params.put("codecOptions", codecOptions != null ? codecOptions : "");
        String cmd = substitute(videoSplitCmd, params).trim();
        return runShell(cmd);
    }

    public int runReplayGain(String filePath) throws IOException, InterruptedException {
        Map<String, String> params = new HashMap<>();
        params.put("toolsLocation", toolsLocation.endsWith("/") ? toolsLocation : toolsLocation + "/");
        params.put("filePath", filePath);
        String cmd = substitute(calculateReplayGainCmd, params).trim();
        return runShell(cmd);
    }

    public int runBatchReplayGain(java.util.List<String> filePaths) throws IOException, InterruptedException {
        if (filePaths == null || filePaths.isEmpty()) {
            return 0;
        }
        StringBuilder cmd = new StringBuilder();
        cmd.append(toolsLocation.endsWith("/") ? toolsLocation : toolsLocation + "/").append("rsgain custom -a -s i");
        for (String filePath : filePaths) {
            cmd.append(" \"").append(filePath).append("\"");
        }
        return runShell(cmd.toString());
    }

    private String substitute(String template, Map<String, String> params) {
        String result = template;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    private int runShell(String cmd) throws IOException, InterruptedException {
        log.info("Running command: {}", cmd);
        ProcessBuilder pb = new ProcessBuilder();
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            pb.command("cmd.exe", "/c", cmd);
        } else {
            pb.command("bash", "-c", cmd);
        }
        pb.redirectErrorStream(true);
        Process process = pb.start();
        process.getInputStream().transferTo(System.out);
        int exit = process.waitFor();
        log.info("Command exited with code {}", exit);
        return exit;
    }
}
