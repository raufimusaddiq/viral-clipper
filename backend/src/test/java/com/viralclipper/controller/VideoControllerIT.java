package com.viralclipper.controller;

import com.viralclipper.IntegrationTest;
import com.viralclipper.model.Video;
import com.viralclipper.repository.VideoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@IntegrationTest
@AutoConfigureMockMvc
class VideoControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private VideoRepository videoRepository;

    @BeforeEach
    void cleanUp() {
        videoRepository.deleteAll();
    }

    @Test
    void importVideo_withYouTubeUrl_returnsVideoId() throws Exception {
        mockMvc.perform(post("/api/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://www.youtube.com/watch?v=test123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.data.videoId").isNotEmpty())
                .andExpect(jsonPath("$.data.sourceType").value("YOUTUBE"));
    }

    @Test
    void importVideo_withLocalPath_returnsLocalType() throws Exception {
        mockMvc.perform(post("/api/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"localPath\":\"/videos/test.mp4\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sourceType").value("LOCAL"));
    }

    @Test
    void importVideo_withNoInput_returnsError() throws Exception {
        mockMvc.perform(post("/api/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sourceType").value("LOCAL"));
    }

    @Test
    void listVideos_returnsAllVideos() throws Exception {
        Video v1 = new Video("v1", null, "LOCAL", "/test1.mp4");
        Video v2 = new Video("v2", "https://youtube.com/test", "YOUTUBE", "/test2.mp4");
        videoRepository.save(v1);
        videoRepository.save(v2);

        mockMvc.perform(get("/api/videos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.videos", hasSize(2)));
    }

    @Test
    void getVideo_existingId_returnsVideo() throws Exception {
        Video video = new Video("v1", null, "LOCAL", "/test.mp4");
        video.setTitle("Test Video");
        videoRepository.save(video);

        mockMvc.perform(get("/api/videos/v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Test Video"));
    }

    @Test
    void getVideo_nonExistingId_returns404() throws Exception {
        mockMvc.perform(get("/api/videos/nonexistent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    void deleteVideo_existingId_deletesSuccessfully() throws Exception {
        Video video = new Video("v1", null, "LOCAL", "/test.mp4");
        videoRepository.save(video);

        mockMvc.perform(delete("/api/videos/v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deleted").value(true));

        assert !videoRepository.findById("v1").isPresent();
    }
}
