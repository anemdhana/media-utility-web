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
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
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

    @MockBean
    private MediaPlaylistUtils mediaPlaylistUtils;

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
    void given_compact_size_quality_parameter_when_extract_audio_endpoint_is_called_then_it_uses_requested_quality() throws Exception {
        String videoId = "fjCYYnfzRvI";
        File downloaded = new File("C:/tmp/sample-" + videoId + "-compact_size.m4a");

        when(mediaFileUtils.extractAudioFromYoutubeVideoId(videoId, MediaSplitUtils.OutputQuality.COMPACT_SIZE)).thenReturn(downloaded);

        mockMvc.perform(post("/api/media/youtube/audio-extract")
                        .param("videoId", videoId)
                        .param("quality", "compact_size"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.videoId").value(videoId))
                .andExpect(jsonPath("$.quality").value("COMPACT_SIZE"))
                .andExpect(jsonPath("$.path").value(downloaded.getAbsolutePath()));

        verify(mediaFileUtils).extractAudioFromYoutubeVideoId(eq(videoId), eq(MediaSplitUtils.OutputQuality.COMPACT_SIZE));
    }

    @Test
    void given_compact_size_speech_quality_parameter_when_extract_audio_endpoint_is_called_then_it_uses_requested_quality() throws Exception {
        String videoId = "B9j3pYC7Z20";
        File downloaded = new File("C:/tmp/sample-" + videoId + "-compact_size_speech.m4a");

        when(mediaFileUtils.extractAudioFromYoutubeVideoId(videoId, MediaSplitUtils.OutputQuality.COMPACT_SIZE_SPEECH)).thenReturn(downloaded);

        mockMvc.perform(post("/api/media/youtube/audio-extract")
                        .param("videoId", videoId)
                        .param("quality", "compact_size_speech"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.videoId").value(videoId))
                .andExpect(jsonPath("$.quality").value("COMPACT_SIZE_SPEECH"))
                .andExpect(jsonPath("$.path").value(downloaded.getAbsolutePath()));

        verify(mediaFileUtils).extractAudioFromYoutubeVideoId(eq(videoId), eq(MediaSplitUtils.OutputQuality.COMPACT_SIZE_SPEECH));
    }

    @Test
    void given_compact_size_music_quality_parameter_when_extract_audio_endpoint_is_called_then_it_uses_requested_quality() throws Exception {
        String videoId = "B9j3pYC7Z20";
        File downloaded = new File("C:/tmp/sample-" + videoId + "-compact_size_music.m4a");

        when(mediaFileUtils.extractAudioFromYoutubeVideoId(videoId, MediaSplitUtils.OutputQuality.COMPACT_SIZE_MUSIC)).thenReturn(downloaded);

        mockMvc.perform(post("/api/media/youtube/audio-extract")
                        .param("videoId", videoId)
                        .param("quality", "compact_size_music"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.videoId").value(videoId))
                .andExpect(jsonPath("$.quality").value("COMPACT_SIZE_MUSIC"))
                .andExpect(jsonPath("$.path").value(downloaded.getAbsolutePath()));

        verify(mediaFileUtils).extractAudioFromYoutubeVideoId(eq(videoId), eq(MediaSplitUtils.OutputQuality.COMPACT_SIZE_MUSIC));
    }

    @Test
    void given_compact_music_instrumental_quality_parameter_when_extract_audio_endpoint_is_called_then_it_uses_requested_quality() throws Exception {
        String videoId = "B9j3pYC7Z20";
        File downloaded = new File("C:/tmp/sample-" + videoId + "-compact_music_instrumental.m4a");

        when(mediaFileUtils.extractAudioFromYoutubeVideoId(videoId, MediaSplitUtils.OutputQuality.COMPACT_MUSIC_INSTRUMENTAL)).thenReturn(downloaded);

        mockMvc.perform(post("/api/media/youtube/audio-extract")
                        .param("videoId", videoId)
                        .param("quality", "compact_music_instrumental"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.videoId").value(videoId))
                .andExpect(jsonPath("$.quality").value("COMPACT_MUSIC_INSTRUMENTAL"))
                .andExpect(jsonPath("$.path").value(downloaded.getAbsolutePath()));

        verify(mediaFileUtils).extractAudioFromYoutubeVideoId(eq(videoId), eq(MediaSplitUtils.OutputQuality.COMPACT_MUSIC_INSTRUMENTAL));
    }

    @Test
    void given_invalid_quality_parameter_when_extract_audio_endpoint_is_called_then_it_returns_bad_request() throws Exception {
        mockMvc.perform(post("/api/media/youtube/audio-extract")
                        .param("videoId", "r98rdmXpA2c")
                        .param("quality", "ultra"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void given_label_when_create_playlist_by_label_endpoint_is_called_then_it_returns_playlist_details() throws Exception {
        String label = "heart-melting-tunes";
        File playlist = new File("C:/tmp/heart-melting-tunes+120.m3u8");

        when(mediaPlaylistUtils.createPlaylistByLabel(label)).thenReturn(playlist);
        when(mediaPlaylistUtils.getPlaylistTracks(playlist)).thenReturn(List.of(new File("C:/tmp/track1.m4a")));

        mockMvc.perform(post("/api/media/playlists/by-label")
                        .param("label", label))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value(label))
                .andExpect(jsonPath("$.playlistPath").value(playlist.getAbsolutePath()))
                .andExpect(jsonPath("$.playlistFileName").value(playlist.getName()))
                .andExpect(jsonPath("$.trackCount").value("1"));

        verify(mediaPlaylistUtils).createPlaylistByLabel(label);
        verify(mediaPlaylistUtils).getPlaylistTracks(playlist);
    }

    @Test
    void given_blank_label_when_create_playlist_by_label_endpoint_is_called_then_it_returns_bad_request() throws Exception {
        mockMvc.perform(post("/api/media/playlists/by-label")
                        .param("label", "   "))
                .andExpect(status().isBadRequest());
    }

    @Test
    void given_playlist_name_and_target_folder_when_copy_playlist_endpoint_is_called_then_it_returns_copy_details() throws Exception {
        String playlistName = "focus.m3u8";
        String targetFolder = "C:/tmp/target";
        File copiedPlaylist = new File("C:/tmp/target/focus.m3u8");

        when(mediaPlaylistUtils.copyPlaylistAndTracksToFolder(playlistName, targetFolder)).thenReturn(copiedPlaylist);
        when(mediaPlaylistUtils.getPlaylistTracks(copiedPlaylist)).thenReturn(List.of(new File("C:/tmp/target/track1.m4a")));

        mockMvc.perform(post("/api/media/playlists/copy")
                        .param("playlistName", playlistName)
                        .param("targetFolder", targetFolder))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playlistName").value(playlistName))
                .andExpect(jsonPath("$.targetFolder").value(new File(targetFolder).getAbsolutePath()))
                .andExpect(jsonPath("$.copiedPlaylistPath").value(copiedPlaylist.getAbsolutePath()))
                .andExpect(jsonPath("$.copiedPlaylistFileName").value(copiedPlaylist.getName()))
                .andExpect(jsonPath("$.copiedTrackCount").value("1"));

        verify(mediaPlaylistUtils).copyPlaylistAndTracksToFolder(playlistName, targetFolder);
        verify(mediaPlaylistUtils).getPlaylistTracks(copiedPlaylist);
    }

    @Test
    void given_blank_playlist_name_when_copy_playlist_endpoint_is_called_then_it_returns_bad_request() throws Exception {
        mockMvc.perform(post("/api/media/playlists/copy")
                        .param("playlistName", "   ")
                        .param("targetFolder", "C:/tmp/target"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void given_blank_target_folder_when_copy_playlist_endpoint_is_called_then_it_returns_bad_request() throws Exception {
        mockMvc.perform(post("/api/media/playlists/copy")
                        .param("playlistName", "focus.m3u8")
                        .param("targetFolder", "   "))
                .andExpect(status().isBadRequest());
    }
}
