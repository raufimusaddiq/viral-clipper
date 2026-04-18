package com.viralclipper.controller;

import com.viralclipper.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@IntegrationTest
@AutoConfigureMockMvc
class ClipControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listClips_nonExistentVideo_returnsEmptyClips() throws Exception {
        mockMvc.perform(get("/api/videos/nonexistent/clips"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.clips").isEmpty());
    }

    @Test
    void getClip_nonExistentClip_returns404() throws Exception {
        mockMvc.perform(get("/api/clips/nonexistent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    void exportClips_noClipIds_returns400() throws Exception {
        mockMvc.perform(post("/api/clips/export")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void exportClips_emptyClipIds_returns400() throws Exception {
        mockMvc.perform(post("/api/clips/export")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clipIds\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void previewClip_nonExistent_returns404() throws Exception {
        mockMvc.perform(get("/api/clips/nonexistent/preview"))
                .andExpect(status().isNotFound());
    }

    @Test
    void downloadClip_nonExistent_returns404() throws Exception {
        mockMvc.perform(get("/api/clips/nonexistent/export"))
                .andExpect(status().isNotFound());
    }
}
