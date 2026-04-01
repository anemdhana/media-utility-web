package org.pssm.media;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.File;
import java.io.IOException;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("BDD style youtube audio extract endpoint checks")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MediaYoutubeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MediaFileUtils mediaFileUtils;

    @Test
    void given_valid_video_id_when_extract_audio_endpoint_is_called_then_it_returns_downloaded_file_path() throws Exception {
        String videoId = "To0lu_BrXTk";
        File downloaded = new File("C:/tmp/deep-meditation-" + videoId + ".m4a");

        when(mediaFileUtils.extractAudioFromYoutubeVideoId(videoId, MediaSplitUtils.OutputQuality.YOUTUBE_UPLOAD)).thenReturn(downloaded);

        mockMvc.perform(post("/api/media/youtube/audio-extract")
                        .param("videoId", videoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.videoId").value(videoId))
                .andExpect(jsonPath("$.quality").value("YOUTUBE_UPLOAD"))
                .andExpect(jsonPath("$.path").value(downloaded.getAbsolutePath()))
                .andExpect(jsonPath("$.fileName").value(downloaded.getName()));

        verify(mediaFileUtils).extractAudioFromYoutubeVideoId(videoId, MediaSplitUtils.OutputQuality.YOUTUBE_UPLOAD);
    }

    @Test
    void given_blank_video_id_when_extract_audio_endpoint_is_called_then_it_returns_bad_request() throws Exception {
        mockMvc.perform(post("/api/media/youtube/audio-extract")
                        .param("videoId", "   "))
                .andExpect(status().isBadRequest());
    }

    @Test
    void given_extraction_error_when_extract_audio_endpoint_is_called_then_it_returns_server_error() throws Exception {
        String videoId = "JyNedPZesLE";
        when(mediaFileUtils.extractAudioFromYoutubeVideoId(videoId, MediaSplitUtils.OutputQuality.YOUTUBE_UPLOAD))
                .thenThrow(new IOException("yt-dlp failed"));

        mockMvc.perform(post("/api/media/youtube/audio-extract")
                        .param("videoId", videoId))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void given_quality_parameter_when_extract_audio_endpoint_is_called_then_it_uses_requested_quality() throws Exception {
        String videoId = "r98rdmXpA2c";
        File downloaded = new File("C:/tmp/sample-" + videoId + "-whatsapp.m4a");

        when(mediaFileUtils.extractAudioFromYoutubeVideoId(videoId, MediaSplitUtils.OutputQuality.WHATSAPP)).thenReturn(downloaded);

        mockMvc.perform(post("/api/media/youtube/audio-extract")
                        .param("videoId", videoId)
                        .param("quality", "whatsapp"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.videoId").value(videoId))
                .andExpect(jsonPath("$.quality").value("WHATSAPP"))
                .andExpect(jsonPath("$.path").value(downloaded.getAbsolutePath()));

        verify(mediaFileUtils).extractAudioFromYoutubeVideoId(eq(videoId), eq(MediaSplitUtils.OutputQuality.WHATSAPP));
    }

    @Test
    void given_invalid_quality_parameter_when_extract_audio_endpoint_is_called_then_it_returns_bad_request() throws Exception {
        mockMvc.perform(post("/api/media/youtube/audio-extract")
                        .param("videoId", "r98rdmXpA2c")
                        .param("quality", "ultra"))
                .andExpect(status().isBadRequest());
    }
}
