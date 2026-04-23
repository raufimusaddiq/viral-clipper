package com.viralclipper.service;

import com.viralclipper.config.AppConfig;
import com.viralclipper.model.DiscoveredVideo;
import com.viralclipper.model.Video;
import com.viralclipper.pipeline.PythonRunner;
import com.viralclipper.repository.DiscoveredVideoRepository;
import com.viralclipper.repository.VideoRepository;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class DiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(DiscoveryService.class);

    // Enrichment pool: transcript-sampling is yt-dlp + CPU-side text scoring,
    // I/O-bound. Cap at 3 so a fresh search can't saturate the network layer.
    private static final int ENRICHMENT_CONCURRENCY = 3;
    private static final int ENRICHMENT_TOP_N = 10;

    private final PythonRunner pythonRunner;
    private final AppConfig appConfig;
    private final DiscoveredVideoRepository discoveredRepo;
    private final VideoRepository videoRepository;

    private final ExecutorService enrichmentPool;

    public DiscoveryService(PythonRunner pythonRunner, AppConfig appConfig,
                            DiscoveredVideoRepository discoveredRepo,
                            VideoRepository videoRepository) {
        this.pythonRunner = pythonRunner;
        this.appConfig = appConfig;
        this.discoveredRepo = discoveredRepo;
        this.videoRepository = videoRepository;

        AtomicLong tid = new AtomicLong();
        this.enrichmentPool = new ThreadPoolExecutor(
                ENRICHMENT_CONCURRENCY, ENRICHMENT_CONCURRENCY,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(100),
                r -> {
                    Thread t = new Thread(r, "enrich-" + tid.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.DiscardOldestPolicy()
        );
    }

    @PreDestroy
    public void shutdown() {
        enrichmentPool.shutdown();
        try {
            if (!enrichmentPool.awaitTermination(5, TimeUnit.SECONDS)) {
                enrichmentPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            enrichmentPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public Map<String, Object> search(String query, int maxResults, int minDuration, int maxDuration) throws Exception {
        List<String> args = new ArrayList<>();
        args.add("--mode"); args.add("search");
        args.add("--query"); args.add(query);
        args.add("--max-results"); args.add(String.valueOf(maxResults));
        if (minDuration > 0) { args.add("--min-duration"); args.add(String.valueOf(minDuration)); }
        if (maxDuration > 0) { args.add("--max-duration"); args.add(String.valueOf(maxDuration)); }

        JsonNode result = pythonRunner.runScript("discover.py", args);
        List<DiscoveredVideo> persisted = persistResults(result, "search", query);
        scheduleEnrichment(persisted);
        return buildResponse(result.path("mode").asText("search"), query, persisted);
    }

    public Map<String, Object> trending(int maxResults, String region) throws Exception {
        List<String> args = new ArrayList<>();
        args.add("--mode"); args.add("trending");
        args.add("--max-results"); args.add(String.valueOf(maxResults));
        args.add("--region"); args.add(region);

        JsonNode result = pythonRunner.runScript("discover.py", args);
        List<DiscoveredVideo> persisted = persistResults(result, "trending", region);
        scheduleEnrichment(persisted);
        return buildResponse("trending", region, persisted);
    }

    public Map<String, Object> channel(String channelUrl, int maxResults, int minDuration, int maxDuration) throws Exception {
        List<String> args = new ArrayList<>();
        args.add("--mode"); args.add("channel");
        args.add("--channel-url"); args.add(channelUrl);
        args.add("--max-results"); args.add(String.valueOf(maxResults));
        if (minDuration > 0) { args.add("--min-duration"); args.add(String.valueOf(minDuration)); }
        if (maxDuration > 0) { args.add("--max-duration"); args.add(String.valueOf(maxDuration)); }

        JsonNode result = pythonRunner.runScript("discover.py", args);
        List<DiscoveredVideo> persisted = persistResults(result, "channel", channelUrl);
        scheduleEnrichment(persisted);
        return buildResponse("channel", channelUrl, persisted);
    }

    /** List stored candidates. status=null returns NEW+QUEUED ranked by predicted_score. */
    public List<DiscoveredVideo> listCandidates(String status) {
        if (status == null || status.isBlank()) {
            return discoveredRepo.findActiveRanked();
        }
        return discoveredRepo.findByStatusOrderByPredictedScoreDescRelevanceScoreDesc(status.toUpperCase());
    }

    public Optional<DiscoveredVideo> updateStatus(String id, String status) {
        return discoveredRepo.findById(id).map(d -> {
            d.setStatus(status.toUpperCase());
            return discoveredRepo.save(d);
        });
    }

    public List<DiscoveredVideo> queueByIds(List<String> ids) {
        List<DiscoveredVideo> updated = new ArrayList<>();
        for (String id : ids) {
            discoveredRepo.findById(id).ifPresent(d -> {
                if (!"IMPORTED".equals(d.getStatus())) {
                    d.setStatus("QUEUED");
                    updated.add(discoveredRepo.save(d));
                }
            });
        }
        return updated;
    }

    public void markImported(String discoveredId, String jobId, String videoId) {
        discoveredRepo.findById(discoveredId).ifPresent(d -> {
            d.setStatus("IMPORTED");
            d.setJobId(jobId);
            d.setVideoId(videoId);
            discoveredRepo.save(d);
        });
    }

    // -- internal --

    private List<DiscoveredVideo> persistResults(JsonNode result, String mode, String query) {
        List<DiscoveredVideo> out = new ArrayList<>();
        if (!result.has("videos")) return out;

        Set<String> importedYtIds = collectImportedYoutubeIds();

        for (JsonNode v : result.get("videos")) {
            String ytId = v.path("videoId").asText("");
            if (ytId.isBlank()) continue;

            DiscoveredVideo d = discoveredRepo.findByYoutubeId(ytId).orElseGet(() -> {
                DiscoveredVideo n = new DiscoveredVideo();
                n.setId(UUID.randomUUID().toString());
                n.setYoutubeId(ytId);
                n.setDiscoveredAt(Instant.now().toString());
                return n;
            });

            d.setUrl(v.path("url").asText(""));
            d.setTitle(v.path("title").asText(""));
            d.setChannel(v.path("channel").asText(""));
            d.setDurationSec(v.path("duration").asInt(0));
            d.setViewCount(v.has("viewCount") && !v.get("viewCount").isNull() ? v.get("viewCount").asLong() : null);
            d.setUploadDate(v.path("uploadDate").asText(""));
            d.setAgeHours(v.has("age_hours") ? v.get("age_hours").asDouble() : null);
            d.setSourceMode(mode);
            d.setSourceQuery(query);
            d.setRelevanceScore(v.path("relevanceScore").asDouble(0));

            // v2 fields: channel_id FK, heuristic content-type, clip-likeness flag.
            String channelId = v.path("channelId").asText("");
            if (!channelId.isBlank()) d.setChannelId(channelId);
            String ctype = v.path("contentType").asText("");
            if (!ctype.isBlank()) d.setContentType(ctype);
            d.setIsLikelyClipped(v.path("isLikelyClipped").asBoolean(false) ? 1 : 0);

            // Dedup against already-imported library. If the YouTube id matches
            // the source_url of an existing Video row, flag as IMPORTED so the
            // UI can de-emphasize it.
            if (d.getStatus() == null || "NEW".equals(d.getStatus())) {
                if (importedYtIds.contains(ytId)) {
                    d.setStatus("IMPORTED");
                } else {
                    d.setStatus("NEW");
                }
            }

            synchronized (DB_WRITE_LOCK) {
                out.add(discoveredRepo.save(d));
            }
        }
        return out;
    }

    private Set<String> collectImportedYoutubeIds() {
        Set<String> ids = new HashSet<>();
        for (Video v : videoRepository.findAll()) {
            String url = v.getSourceUrl();
            if (url == null) continue;
            String ytId = extractYoutubeId(url);
            if (ytId != null) ids.add(ytId);
        }
        return ids;
    }

    private static String extractYoutubeId(String url) {
        if (url == null || url.isBlank()) return null;
        // Handles watch?v=, youtu.be/, /shorts/
        String[] markers = {"v=", "youtu.be/", "/shorts/", "/embed/"};
        for (String m : markers) {
            int idx = url.indexOf(m);
            if (idx >= 0) {
                int start = idx + m.length();
                int end = start;
                while (end < url.length() && end - start < 11) {
                    char c = url.charAt(end);
                    if (Character.isLetterOrDigit(c) || c == '_' || c == '-') end++;
                    else break;
                }
                if (end - start == 11) return url.substring(start, end);
            }
        }
        return null;
    }

    private void scheduleEnrichment(List<DiscoveredVideo> candidates) {
        candidates.stream()
                .filter(d -> "NEW".equals(d.getStatus()))
                .filter(d -> d.getTranscriptScore() == null)
                .sorted(Comparator.comparingDouble(
                        (DiscoveredVideo d) -> d.getRelevanceScore() == null ? 0 : d.getRelevanceScore()
                ).reversed())
                .limit(ENRICHMENT_TOP_N)
                .forEach(d -> enrichmentPool.submit(() -> enrichOne(d.getId())));
    }

    // Serialize DB writes across enrichment workers. SQLite allows only one
    // concurrent writer; without this lock three parallel yt-dlp finishers
    // race on the same row and most updates lose to SQLITE_BUSY. The yt-dlp
    // fetch itself (the real latency) remains fully parallel — only the save
    // is behind the lock.
    private static final Object DB_WRITE_LOCK = new Object();

    private void enrichOne(String discoveredId) {
        try {
            DiscoveredVideo d = discoveredRepo.findById(discoveredId).orElse(null);
            if (d == null) return;

            List<String> args = new ArrayList<>();
            args.add("--mode"); args.add("enrich");
            args.add("--video-url"); args.add(d.getUrl());
            args.add("--duration"); args.add(String.valueOf(d.getDurationSec() == null ? 0 : d.getDurationSec()));
            args.add("--age-hours"); args.add(String.valueOf(d.getAgeHours() == null ? 9999 : d.getAgeHours()));
            args.add("--view-count"); args.add(String.valueOf(d.getViewCount() == null ? 0 : d.getViewCount()));

            JsonNode result = pythonRunner.runScript("discover.py", args);

            Double tScore = result.has("transcriptScore") ? result.get("transcriptScore").asDouble() : null;
            Double pScore = result.has("predictedScore") ? result.get("predictedScore").asDouble() : null;
            String sample = result.has("transcriptSample") ? result.get("transcriptSample").asText("") : "";
            if (sample.length() > 2000) sample = sample.substring(0, 2000);

            synchronized (DB_WRITE_LOCK) {
                // Re-fetch inside the lock to avoid clobbering any concurrent
                // status change (e.g. user queued the row while we were fetching).
                DiscoveredVideo fresh = discoveredRepo.findById(discoveredId).orElse(null);
                if (fresh == null) return;
                fresh.setTranscriptScore(tScore);
                fresh.setPredictedScore(pScore);
                if (!sample.isEmpty()) fresh.setTranscriptSample(sample);
                fresh.setEnrichedAt(Instant.now().toString());
                discoveredRepo.save(fresh);
                log.info("Enriched {} — transcript={} predicted={}",
                        fresh.getYoutubeId(), tScore, pScore);
            }
        } catch (Exception e) {
            log.warn("Enrichment failed for {}: {}", discoveredId, e.getMessage());
        }
    }

    private Map<String, Object> buildResponse(String mode, String query, List<DiscoveredVideo> rows) {
        Map<String, Object> response = new HashMap<>();
        response.put("mode", mode);
        response.put("query", query);
        response.put("count", rows.size());

        List<Map<String, Object>> videos = new ArrayList<>();
        for (DiscoveredVideo d : rows) videos.add(toMap(d));
        response.put("videos", videos);
        return response;
    }

    public static Map<String, Object> toMap(DiscoveredVideo d) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", d.getId());
        m.put("youtubeId", d.getYoutubeId());
        m.put("videoId", d.getYoutubeId()); // legacy alias for existing frontend
        m.put("url", d.getUrl());
        m.put("title", d.getTitle());
        m.put("channel", d.getChannel());
        m.put("duration", d.getDurationSec());
        m.put("viewCount", d.getViewCount());
        m.put("uploadDate", d.getUploadDate());
        m.put("ageHours", d.getAgeHours());
        m.put("relevanceScore", d.getRelevanceScore());
        m.put("transcriptScore", d.getTranscriptScore());
        m.put("predictedScore", d.getPredictedScore());
        m.put("status", d.getStatus());
        m.put("jobId", d.getJobId());
        m.put("sourceMode", d.getSourceMode());
        m.put("sourceQuery", d.getSourceQuery());
        m.put("channelId", d.getChannelId());
        m.put("contentType", d.getContentType());
        m.put("speechDensityWpm", d.getSpeechDensityWpm());
        m.put("isLikelyClipped", d.getIsLikelyClipped() != null && d.getIsLikelyClipped() == 1);
        return m;
    }
}
