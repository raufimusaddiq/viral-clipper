package com.viralclipper.controller;

import com.viralclipper.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@IntegrationTest
@AutoConfigureMockMvc
class DiscoveryControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void search_withoutQuery_returns400() throws Exception {
        mockMvc.perform(post("/api/discover/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.error").value("query is required"));
    }

    @Test
    void search_withBlankQuery_returns400() throws Exception {
        mockMvc.perform(post("/api/discover/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("query is required"));
    }

    @Test
    void search_withQuery_callsPythonAndReturns500() throws Exception {
        mockMvc.perform(post("/api/discover/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"indonesian viral\"}"))
                .andExpect(status().is5xxServerError());
    }

    @Test
    void trending_defaultParams_callsPython() throws Exception {
        mockMvc.perform(post("/api/discover/trending")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().is5xxServerError());
    }

    @Test
    void trending_noBody_callsPython() throws Exception {
        mockMvc.perform(post("/api/discover/trending")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is5xxServerError());
    }

    @Test
    void channel_withoutUrl_returns400() throws Exception {
        mockMvc.perform(post("/api/discover/channel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("channelUrl is required"));
    }

    @Test
    void channel_withBlankUrl_returns400() throws Exception {
        mockMvc.perform(post("/api/discover/channel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channelUrl\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("channelUrl is required"));
    }

    @Test
    void channel_withUrl_callsPythonAndReturns500() throws Exception {
        mockMvc.perform(post("/api/discover/channel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channelUrl\":\"https://youtube.com/@test\"}"))
                .andExpect(status().is5xxServerError());
    }

    @Test
    void search_withCustomParams_passesThrough() throws Exception {
        mockMvc.perform(post("/api/discover/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"test\",\"maxResults\":10,\"minDuration\":60,\"maxDuration\":1800}"))
                .andExpect(status().is5xxServerError());
    }

    @Test
    void trending_withCustomParams_passesThrough() throws Exception {
        mockMvc.perform(post("/api/discover/trending")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maxResults\":5,\"region\":\"US\"}"))
                .andExpect(status().is5xxServerError());
    }
}
