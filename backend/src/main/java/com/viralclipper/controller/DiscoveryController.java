package com.viralclipper.controller;

import com.viralclipper.service.DiscoveryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/discover")
public class DiscoveryController {

    private final DiscoveryService discoveryService;

    public DiscoveryController(DiscoveryService discoveryService) {
        this.discoveryService = discoveryService;
    }

    @PostMapping("/search")
    public ResponseEntity<Map<String, Object>> search(@RequestBody Map<String, Object> body) {
        String query = (String) body.getOrDefault("query", "");
        int maxResults = body.containsKey("maxResults") ? ((Number) body.get("maxResults")).intValue() : 20;
        int minDuration = body.containsKey("minDuration") ? ((Number) body.get("minDuration")).intValue() : 0;
        int maxDuration = body.containsKey("maxDuration") ? ((Number) body.get("maxDuration")).intValue() : 0;

        if (query.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "error", "query is required"));
        }

        try {
            Map<String, Object> result = discoveryService.search(query, maxResults, minDuration, maxDuration);
            return ResponseEntity.ok(Map.of("status", "ok", "data", result));
        } catch (Exception e) {
            Map<String, Object> err = new HashMap<>();
            err.put("status", "error");
            err.put("error", e.getMessage());
            return ResponseEntity.status(500).body(err);
        }
    }

    @PostMapping("/trending")
    public ResponseEntity<Map<String, Object>> trending(@RequestBody(required = false) Map<String, Object> body) {
        int maxResults = (body != null && body.containsKey("maxResults")) ? ((Number) body.get("maxResults")).intValue() : 20;
        String region = (body != null && body.containsKey("region")) ? (String) body.get("region") : "ID";

        try {
            Map<String, Object> result = discoveryService.trending(maxResults, region);
            return ResponseEntity.ok(Map.of("status", "ok", "data", result));
        } catch (Exception e) {
            Map<String, Object> err = new HashMap<>();
            err.put("status", "error");
            err.put("error", e.getMessage());
            return ResponseEntity.status(500).body(err);
        }
    }

    @PostMapping("/channel")
    public ResponseEntity<Map<String, Object>> channel(@RequestBody Map<String, Object> body) {
        String channelUrl = (String) body.getOrDefault("channelUrl", "");
        int maxResults = body.containsKey("maxResults") ? ((Number) body.get("maxResults")).intValue() : 20;
        int minDuration = body.containsKey("minDuration") ? ((Number) body.get("minDuration")).intValue() : 0;
        int maxDuration = body.containsKey("maxDuration") ? ((Number) body.get("maxDuration")).intValue() : 0;

        if (channelUrl.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "error", "channelUrl is required"));
        }

        try {
            Map<String, Object> result = discoveryService.channel(channelUrl, maxResults, minDuration, maxDuration);
            return ResponseEntity.ok(Map.of("status", "ok", "data", result));
        } catch (Exception e) {
            Map<String, Object> err = new HashMap<>();
            err.put("status", "error");
            err.put("error", e.getMessage());
            return ResponseEntity.status(500).body(err);
        }
    }
}
