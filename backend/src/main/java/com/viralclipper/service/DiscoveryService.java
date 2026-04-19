package com.viralclipper.service;

import com.viralclipper.config.AppConfig;
import com.viralclipper.pipeline.PythonRunner;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class DiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(DiscoveryService.class);
    private final PythonRunner pythonRunner;
    private final AppConfig appConfig;

    public DiscoveryService(PythonRunner pythonRunner, AppConfig appConfig) {
        this.pythonRunner = pythonRunner;
        this.appConfig = appConfig;
    }

    public Map<String, Object> search(String query, int maxResults, int minDuration, int maxDuration) throws Exception {
        List<String> args = new ArrayList<>();
        args.add("--mode");
        args.add("search");
        args.add("--query");
        args.add(query);
        args.add("--max-results");
        args.add(String.valueOf(maxResults));
        if (minDuration > 0) {
            args.add("--min-duration");
            args.add(String.valueOf(minDuration));
        }
        if (maxDuration > 0) {
            args.add("--max-duration");
            args.add(String.valueOf(maxDuration));
        }

        JsonNode result = pythonRunner.runScript("discover.py", args);
        return buildResponse(result);
    }

    public Map<String, Object> trending(int maxResults, String region) throws Exception {
        List<String> args = new ArrayList<>();
        args.add("--mode");
        args.add("trending");
        args.add("--max-results");
        args.add(String.valueOf(maxResults));
        args.add("--region");
        args.add(region);

        JsonNode result = pythonRunner.runScript("discover.py", args);
        return buildResponse(result);
    }

    public Map<String, Object> channel(String channelUrl, int maxResults, int minDuration, int maxDuration) throws Exception {
        List<String> args = new ArrayList<>();
        args.add("--mode");
        args.add("channel");
        args.add("--channel-url");
        args.add(channelUrl);
        args.add("--max-results");
        args.add(String.valueOf(maxResults));
        if (minDuration > 0) {
            args.add("--min-duration");
            args.add(String.valueOf(minDuration));
        }
        if (maxDuration > 0) {
            args.add("--max-duration");
            args.add(String.valueOf(maxDuration));
        }

        JsonNode result = pythonRunner.runScript("discover.py", args);
        return buildResponse(result);
    }

    private Map<String, Object> buildResponse(JsonNode result) {
        Map<String, Object> response = new HashMap<>();
        response.put("mode", result.path("mode").asText(""));
        response.put("query", result.path("query").asText(""));
        response.put("count", result.path("count").asInt(0));

        List<Map<String, Object>> videos = new ArrayList<>();
        if (result.has("videos")) {
            for (JsonNode v : result.get("videos")) {
                Map<String, Object> video = new HashMap<>();
                video.put("videoId", v.path("videoId").asText(""));
                video.put("title", v.path("title").asText(""));
                video.put("url", v.path("url").asText(""));
                video.put("duration", v.path("duration").asInt(0));
                video.put("channel", v.path("channel").asText(""));
                video.put("viewCount", v.path("viewCount").asLong(0));
                video.put("uploadDate", v.path("uploadDate").asText(""));
                video.put("relevanceScore", v.path("relevanceScore").asDouble(0));
                videos.add(video);
            }
        }
        response.put("videos", videos);
        return response;
    }
}
