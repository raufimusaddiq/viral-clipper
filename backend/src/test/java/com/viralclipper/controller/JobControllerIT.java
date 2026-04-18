package com.viralclipper.controller;

import com.viralclipper.IntegrationTest;
import com.viralclipper.model.Video;
import com.viralclipper.repository.VideoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@IntegrationTest
@AutoConfigureMockMvc
class JobControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private VideoRepository videoRepository;

    @BeforeEach
    void cleanUp() {
        videoRepository.deleteAll();
    }

    @Test
    void startProcessing_noVideoId_returns400() throws Exception {
        mockMvc.perform(post("/api/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("videoId required"));
    }

    @Test
    void startProcessing_nonExistentVideo_returns409() throws Exception {
        mockMvc.perform(post("/api/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"videoId\":\"nonexistent\"}"))
                .andExpect(status().is(409));
    }

    @Test
    void startProcessing_existingVideo_returnsJobId() throws Exception {
        Video video = new Video("v1", null, "LOCAL", "/test.mp4");
        videoRepository.save(video);

        mockMvc.perform(post("/api/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"videoId\":\"v1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobId").isNotEmpty())
                .andExpect(jsonPath("$.data.status").value("QUEUED"));
    }

    @Test
    void getJob_existingJob_returnsJobDetails() throws Exception {
        Video video = new Video("v1", null, "LOCAL", "/test.mp4");
        videoRepository.save(video);

        String response = mockMvc.perform(post("/api/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"videoId\":\"v1\"}"))
                .andReturn().getResponse().getContentAsString();

        String jobId = new ObjectMapper()
                .readTree(response).path("data").path("jobId").asText();
        mockMvc.perform(get("/api/jobs/" + jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.job.id").value(jobId));
    }

    @Test
    void getJob_nonExistentJob_returns404() throws Exception {
        mockMvc.perform(get("/api/jobs/nonexistent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    void listJobs_returnsAllJobs() throws Exception {
        mockMvc.perform(get("/api/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobs").isArray());
    }

    @Test
    void retryJob_nonFailedJob_returns400() throws Exception {
        Video video = new Video("v1", null, "LOCAL", "/test.mp4");
        videoRepository.save(video);

        String response = mockMvc.perform(post("/api/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"videoId\":\"v1\"}"))
                .andReturn().getResponse().getContentAsString();

        String jobId = new ObjectMapper()
                .readTree(response).path("data").path("jobId").asText();
        mockMvc.perform(post("/api/jobs/" + jobId + "/retry"))
                .andExpect(status().isBadRequest());
    }
}
