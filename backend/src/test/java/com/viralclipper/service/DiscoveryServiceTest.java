package com.viralclipper.service;

import com.viralclipper.config.AppConfig;
import com.viralclipper.pipeline.PythonRunner;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DiscoveryServiceTest {

    @Mock
    private PythonRunner pythonRunner;

    @Mock
    private AppConfig appConfig;

    private DiscoveryService discoveryService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        discoveryService = new DiscoveryService(pythonRunner, appConfig);
    }

    private com.fasterxml.jackson.databind.JsonNode mockResult(String mode, int count) {
        String json = "{\"mode\":\"" + mode + "\",\"query\":\"test\",\"count\":" + count
                + ",\"videos\":[{\"videoId\":\"abc\",\"title\":\"Test\",\"url\":\"https://youtube.com/watch?v=abc\","
                + "\"duration\":300,\"channel\":\"Ch\",\"viewCount\":1000,\"uploadDate\":\"20260401\","
                + "\"relevanceScore\":0.75}]}";
        return objectMapper.readTree(json);
    }

    @Test
    void search_buildsCorrectArgs() throws Exception {
        when(pythonRunner.runScript(eq("discover.py"), any())).thenReturn(mockResult("search", 1));

        Map<String, Object> result = discoveryService.search("indonesian viral", 20, 0, 0);

        assertEquals("search", result.get("mode"));
        assertEquals(1, result.get("count"));
        verify(pythonRunner).runScript(eq("discover.py"), argThat(args -> {
            return args.contains("--mode") && args.contains("search")
                    && args.contains("--query") && args.contains("indonesian viral")
                    && args.contains("--max-results") && args.contains("20");
        }));
    }

    @Test
    void search_withDurationFilters() throws Exception {
        when(pythonRunner.runScript(eq("discover.py"), any())).thenReturn(mockResult("search", 0));

        discoveryService.search("test", 10, 120, 1800);

        verify(pythonRunner).runScript(eq("discover.py"), argThat(args -> {
            return args.contains("--min-duration") && args.contains("120")
                    && args.contains("--max-duration") && args.contains("1800");
        }));
    }

    @Test
    void trending_buildsCorrectArgs() throws Exception {
        when(pythonRunner.runScript(eq("discover.py"), any())).thenReturn(mockResult("trending", 5));

        Map<String, Object> result = discoveryService.trending(15, "US");

        assertEquals("trending", result.get("mode"));
        verify(pythonRunner).runScript(eq("discover.py"), argThat(args -> {
            return args.contains("--mode") && args.contains("trending")
                    && args.contains("--region") && args.contains("US")
                    && args.contains("--max-results") && args.contains("15");
        }));
    }

    @Test
    void channel_buildsCorrectArgs() throws Exception {
        when(pythonRunner.runScript(eq("discover.py"), any())).thenReturn(mockResult("channel", 3));

        Map<String, Object> result = discoveryService.channel("https://youtube.com/@test", 10, 60, 600);

        assertEquals("channel", result.get("mode"));
        verify(pythonRunner).runScript(eq("discover.py"), argThat(args -> {
            return args.contains("--mode") && args.contains("channel")
                    && args.contains("--channel-url") && args.contains("https://youtube.com/@test")
                    && args.contains("--min-duration") && args.contains("60")
                    && args.contains("--max-duration") && args.contains("600");
        }));
    }

    @Test
    void search_parsesVideoFields() throws Exception {
        when(pythonRunner.runScript(eq("discover.py"), any())).thenReturn(mockResult("search", 1));

        Map<String, Object> result = discoveryService.search("test", 5, 0, 0);
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> videos = (java.util.List<Map<String, Object>>) result.get("videos");

        assertEquals(1, videos.size());
        assertEquals("abc", videos.get(0).get("videoId"));
        assertEquals("Test", videos.get(0).get("title"));
        assertEquals(300, videos.get(0).get("duration"));
        assertEquals(0.75, videos.get(0).get("relevanceScore"));
    }
}
