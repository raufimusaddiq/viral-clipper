package com.viralclipper.controller;

import com.viralclipper.model.DiscoveredVideo;
import com.viralclipper.model.DiscoveryChannel;
import com.viralclipper.model.Job;
import com.viralclipper.model.Video;
import com.viralclipper.service.ChannelCrawlerService;
import com.viralclipper.service.DiscoveryService;
import com.viralclipper.service.JobService;
import com.viralclipper.service.VideoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/discover")
public class DiscoveryController {

    private final DiscoveryService discoveryService;
    private final VideoService videoService;
    private final JobService jobService;
    private final ChannelCrawlerService channelCrawler;

    public DiscoveryController(DiscoveryService discoveryService,
                               VideoService videoService,
                               JobService jobService,
                               ChannelCrawlerService channelCrawler) {
        this.discoveryService = discoveryService;
        this.videoService = videoService;
        this.jobService = jobService;
        this.channelCrawler = channelCrawler;
    }

    @PostMapping("/search")
    public ResponseEntity<Map<String, Object>> search(@RequestBody Map<String, Object> body) {
        String query = (String) body.getOrDefault("query", "");
        int maxResults = intArg(body, "maxResults", 20);
        int minDuration = intArg(body, "minDuration", 0);
        int maxDuration = intArg(body, "maxDuration", 0);

        if (query.isBlank()) return badRequest("query is required");
        return run(() -> discoveryService.search(query, maxResults, minDuration, maxDuration));
    }

    @PostMapping("/trending")
    public ResponseEntity<Map<String, Object>> trending(@RequestBody(required = false) Map<String, Object> body) {
        int maxResults = intArg(body, "maxResults", 20);
        String region = body != null && body.get("region") != null ? (String) body.get("region") : "ID";
        return run(() -> discoveryService.trending(maxResults, region));
    }

    @PostMapping("/channel")
    public ResponseEntity<Map<String, Object>> channel(@RequestBody Map<String, Object> body) {
        String channelUrl = (String) body.getOrDefault("channelUrl", "");
        int maxResults = intArg(body, "maxResults", 20);
        int minDuration = intArg(body, "minDuration", 0);
        int maxDuration = intArg(body, "maxDuration", 0);

        if (channelUrl.isBlank()) return badRequest("channelUrl is required");
        return run(() -> discoveryService.channel(channelUrl, maxResults, minDuration, maxDuration));
    }

    @GetMapping("/candidates")
    public ResponseEntity<Map<String, Object>> candidates(@RequestParam(required = false) String status) {
        List<DiscoveredVideo> rows = discoveryService.listCandidates(status);
        List<Map<String, Object>> videos = new ArrayList<>();
        for (DiscoveredVideo d : rows) videos.add(DiscoveryService.toMap(d));
        Map<String, Object> data = new HashMap<>();
        data.put("count", videos.size());
        data.put("videos", videos);
        return ResponseEntity.ok(Map.of("status", "ok", "data", data));
    }

    @PostMapping("/candidates/{id}/status")
    public ResponseEntity<Map<String, Object>> updateStatus(@PathVariable String id,
                                                            @RequestBody Map<String, Object> body) {
        String status = (String) body.getOrDefault("status", "");
        if (status.isBlank()) return badRequest("status is required");
        return discoveryService.updateStatus(id, status)
                .map(d -> ResponseEntity.ok(Map.of("status", "ok", "data", DiscoveryService.toMap(d))))
                .orElseGet(() -> ResponseEntity.status(404).body(Map.of("status", "error", "error", "not found")));
    }

    @PostMapping("/queue")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> queue(@RequestBody Map<String, Object> body) {
        Object idsObj = body.get("ids");
        if (!(idsObj instanceof List)) return badRequest("ids[] is required");
        List<String> ids = new ArrayList<>();
        for (Object o : (List<Object>) idsObj) if (o != null) ids.add(o.toString());
        if (ids.isEmpty()) return badRequest("ids[] is empty");

        List<DiscoveredVideo> queued = discoveryService.queueByIds(ids);
        List<Map<String, Object>> out = new ArrayList<>();
        for (DiscoveredVideo d : queued) out.add(DiscoveryService.toMap(d));
        return ResponseEntity.ok(Map.of("status", "ok", "data", Map.of("count", out.size(), "videos", out)));
    }

    /** Drain: for each QUEUED candidate, create video + job like the manual import flow. */
    @PostMapping("/queue/drain")
    public ResponseEntity<Map<String, Object>> drain() {
        List<DiscoveredVideo> queued = discoveryService.listCandidates("QUEUED");
        List<Map<String, Object>> started = new ArrayList<>();
        List<Map<String, Object>> failed = new ArrayList<>();

        for (DiscoveredVideo d : queued) {
            try {
                Video video = videoService.importVideo(d.getUrl(), null);
                Job job = jobService.createAndStartJob(video.getId());
                discoveryService.markImported(d.getId(), job.getId(), video.getId());
                Map<String, Object> row = new HashMap<>();
                row.put("discoveredId", d.getId());
                row.put("jobId", job.getId());
                row.put("videoId", video.getId());
                started.add(row);
            } catch (Exception e) {
                Map<String, Object> row = new HashMap<>();
                row.put("discoveredId", d.getId());
                row.put("error", e.getMessage());
                failed.add(row);
            }
        }
        return ResponseEntity.ok(Map.of("status", "ok",
                "data", Map.of("started", started, "failed", failed)));
    }

    // -- channels (Phase 2) --

    @GetMapping("/channels")
    public ResponseEntity<Map<String, Object>> channels(@RequestParam(required = false) String status) {
        List<DiscoveryChannel> rows = (status == null || status.isBlank())
                ? channelCrawler.listActive()
                : channelCrawler.listByStatus(status.toUpperCase());

        List<Map<String, Object>> out = new ArrayList<>();
        for (DiscoveryChannel c : rows) out.add(channelToMap(c));
        return ResponseEntity.ok(Map.of("status", "ok",
                "data", Map.of("count", out.size(), "channels", out)));
    }

    @PostMapping("/channels/reseed")
    public ResponseEntity<Map<String, Object>> reseed() {
        return run(() -> {
            ChannelCrawlerService.SeedResult r = channelCrawler.runCategorySeed();
            return Map.of(
                    "upserted", r.upserted(),
                    "profiled", r.profiled(),
                    "rejected", r.rejected(),
                    "alreadyKnown", r.alreadyKnown()
            );
        });
    }

    @PostMapping("/channels/{id}/refresh")
    public ResponseEntity<Map<String, Object>> refreshChannel(@PathVariable String id) {
        return run(() -> {
            boolean rejected = channelCrawler.refreshProfile(id);
            return Map.of("channelId", id, "rejected", rejected);
        });
    }

    @PostMapping("/channels/{id}/status")
    public ResponseEntity<Map<String, Object>> channelStatus(@PathVariable String id,
                                                             @RequestBody Map<String, Object> body) {
        String status = (String) body.getOrDefault("status", "");
        if (status.isBlank()) return badRequest("status is required");
        return channelCrawler.setStatus(id, status)
                .map(c -> ResponseEntity.ok(Map.of("status", "ok", "data", channelToMap(c))))
                .orElseGet(() -> ResponseEntity.status(404).body(Map.of("status", "error", "error", "not found")));
    }

    private static Map<String, Object> channelToMap(DiscoveryChannel c) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", c.getId());
        m.put("youtubeChannelId", c.getYoutubeChannelId());
        m.put("channelName", c.getChannelName());
        m.put("channelUrl", c.getChannelUrl());
        m.put("primaryCategory", c.getPrimaryCategory());
        m.put("avgDurationSec", c.getAvgDurationSec());
        m.put("medianViewCount", c.getMedianViewCount());
        m.put("uploadsPerWeek", c.getUploadsPerWeek());
        m.put("subscriberCount", c.getSubscriberCount());
        m.put("isLikelyClipperChannel",
                c.getIsLikelyClipperChannel() != null && c.getIsLikelyClipperChannel() == 1);
        m.put("trustScore", c.getTrustScore());
        m.put("trustSamples", c.getTrustSamples());
        m.put("pollCadenceHours", c.getPollCadenceHours());
        m.put("status", c.getStatus());
        m.put("firstSeenAt", c.getFirstSeenAt());
        m.put("lastCrawledAt", c.getLastCrawledAt());
        m.put("lastProfileRefreshAt", c.getLastProfileRefreshAt());
        return m;
    }

    // -- helpers --

    private interface Throws { Map<String, Object> call() throws Exception; }

    private ResponseEntity<Map<String, Object>> run(Throws fn) {
        try {
            return ResponseEntity.ok(Map.of("status", "ok", "data", fn.call()));
        } catch (Exception e) {
            Map<String, Object> err = new HashMap<>();
            err.put("status", "error");
            err.put("error", e.getMessage());
            return ResponseEntity.status(500).body(err);
        }
    }

    private static ResponseEntity<Map<String, Object>> badRequest(String msg) {
        return ResponseEntity.badRequest().body(Map.of("status", "error", "error", msg));
    }

    private static int intArg(Map<String, Object> body, String key, int fallback) {
        if (body == null || !body.containsKey(key) || body.get(key) == null) return fallback;
        Object v = body.get(key);
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(v.toString()); } catch (NumberFormatException e) { return fallback; }
    }
}
