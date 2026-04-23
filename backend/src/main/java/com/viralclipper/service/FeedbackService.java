package com.viralclipper.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viralclipper.config.AppConfig;
import com.viralclipper.model.Clip;
import com.viralclipper.model.ClipFeedback;
import com.viralclipper.model.ClipScore;
import com.viralclipper.pipeline.PythonRunner;
import com.viralclipper.repository.ClipFeedbackRepository;
import com.viralclipper.repository.ClipRepository;
import com.viralclipper.repository.ClipScoreRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Service
public class FeedbackService {

    private static final Logger log = LoggerFactory.getLogger(FeedbackService.class);

    private final ClipFeedbackRepository feedbackRepository;
    private final ClipRepository clipRepository;
    private final ClipScoreRepository clipScoreRepository;
    private final PythonRunner pythonRunner;
    private final AppConfig appConfig;
    private final ObjectMapper objectMapper;

    public FeedbackService(ClipFeedbackRepository feedbackRepository,
                           ClipRepository clipRepository,
                           ClipScoreRepository clipScoreRepository,
                           PythonRunner pythonRunner,
                           AppConfig appConfig,
                           ObjectMapper objectMapper) {
        this.feedbackRepository = feedbackRepository;
        this.clipRepository = clipRepository;
        this.clipScoreRepository = clipScoreRepository;
        this.pythonRunner = pythonRunner;
        this.appConfig = appConfig;
        this.objectMapper = objectMapper;
    }

    /** Create or update a clip_feedback row with real TikTok metrics.
     *
     * The caller must supply {@code postedAtIso} — a past ISO-8601 timestamp
     * when the clip was uploaded to TikTok. The viral score is normalized by
     * time-on-platform (views/day velocity, not raw totals), because a clip
     * with 10k views in 2 hours is very different from 10k views in 2 months.
     *
     * We snapshot the current scoring feature vector at submit time so the
     * supervised trainer can learn (feature_vector → actual_viral_score)
     * pairs later without needing to recompute features on stale clips.
     */
    public ClipFeedback submitTikTokMetrics(
            String clipId, int views, int likes, int comments, int shares, int saves,
            String postedAtIso) {

        java.time.Instant postedAt;
        try {
            postedAt = java.time.Instant.parse(postedAtIso);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "postedAt must be ISO-8601 (e.g. 2026-04-22T10:00:00Z); got: " + postedAtIso);
        }
        java.time.Instant now = java.time.Instant.now();
        if (postedAt.isAfter(now)) {
            throw new IllegalArgumentException("postedAt is in the future: " + postedAtIso);
        }
        double hoursSincePost = Math.max(
                (now.toEpochMilli() - postedAt.toEpochMilli()) / 3_600_000.0, 0.1);

        ClipFeedback fb = feedbackRepository.findByClipId(clipId)
                .orElseGet(() -> {
                    ClipFeedback newFb = new ClipFeedback();
                    newFb.setId(UUID.randomUUID().toString());
                    newFb.setClipId(clipId);
                    Clip clip = clipRepository.findById(clipId)
                            .orElseThrow(() -> new IllegalArgumentException("Clip not found: " + clipId));
                    newFb.setVideoId(clip.getVideoId());
                    newFb.setPredictedScore(clip.getScore() != null ? clip.getScore() : 0.0);
                    newFb.setPredictedTier(clip.getTier() != null ? clip.getTier() : "UNKNOWN");
                    newFb.setCreatedAt(now.toString());
                    // Snapshot the feature vector at submit time so we capture
                    // what the scorer saw for this clip. If the ClipScore has
                    // been updated since, we record the current values.
                    newFb.setFeatures(serializeFeatures(clipId));
                    return newFb;
                });

        fb.setTiktokViews(views);
        fb.setTiktokLikes(likes);
        fb.setTiktokComments(comments);
        fb.setTiktokShares(shares);
        fb.setTiktokSaves(saves);
        fb.setPostedAt(postedAtIso);
        fb.setLastChecked(now.toString());

        try {
            List<String> args = List.of(
                    "--action", "calc-viral-score",
                    "--views", String.valueOf(views),
                    "--likes", String.valueOf(likes),
                    "--comments", String.valueOf(comments),
                    "--shares", String.valueOf(shares),
                    "--saves", String.valueOf(saves),
                    "--hours-since-post", String.valueOf(hoursSincePost)
            );
            JsonNode result = pythonRunner.runScript("feedback.py", args);
            fb.setActualViralScore(result.path("viralScore").asDouble());
        } catch (Exception e) {
            log.warn("Failed to calculate viral score: {}", e.getMessage());
        }

        return feedbackRepository.save(fb);
    }

    private String serializeFeatures(String clipId) {
        try {
            ClipScore cs = clipScoreRepository.findByClipId(clipId);
            if (cs == null) return "{}";
            Map<String, Object> f = new HashMap<>();
            f.put("hookStrength", cs.getHookStrength());
            f.put("keywordTrigger", cs.getKeywordTrigger());
            f.put("novelty", cs.getNovelty());
            f.put("clarity", cs.getClarity());
            f.put("emotionalEnergy", cs.getEmotionalEnergy());
            f.put("textSentiment", cs.getTextSentiment());
            f.put("pauseStructure", cs.getPauseStructure());
            f.put("facePresence", cs.getFacePresence());
            f.put("sceneChange", cs.getSceneChange());
            f.put("topicFit", cs.getTopicFit());
            f.put("historyScore", cs.getHistoryScore());
            return objectMapper.writeValueAsString(f);
        } catch (Exception e) {
            return "{}";
        }
    }

    public Map<String, Object> triggerTraining() throws Exception {
        List<ClipFeedback> records = feedbackRepository.findAllByActualViralScoreNotNull();

        Path feedbackDir = Path.of(appConfig.getDataDir(), "feedback");
        Files.createDirectories(feedbackDir);
        Path feedbackFile = feedbackDir.resolve("feedback_export.json");

        List<Map<String, Object>> exportList = new ArrayList<>();
        for (ClipFeedback fb : records) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("clipId", fb.getClipId());
            try {
                entry.put("features", objectMapper.readValue(fb.getFeatures(), Map.class));
            } catch (Exception e) {
                entry.put("features", new HashMap<>());
            }
            entry.put("predicted_score", fb.getPredictedScore());
            entry.put("predicted_tier", fb.getPredictedTier());
            entry.put("actual_viral_score", fb.getActualViralScore());
            entry.put("text", "");
            exportList.add(entry);
        }
        Files.writeString(feedbackFile, objectMapper.writeValueAsString(exportList));

        List<String> args = List.of(
                "--action", "train",
                "--feedback", feedbackFile.toString(),
                "--min-samples", "5"
        );
        JsonNode result = pythonRunner.runScript("learn_weights.py", args);

        Map<String, Object> response = new HashMap<>();
        response.put("status", result.path("status").asText());
        response.put("version", result.path("version").asInt());
        response.put("trained_on", result.path("trained_on").asInt());

        if (result.has("weight_changes")) {
            Map<String, Object> changes = new HashMap<>();
            Iterator<Map.Entry<String, JsonNode>> it = result.path("weight_changes").fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> entry = it.next();
                Map<String, Object> change = new HashMap<>();
                change.put("from", entry.getValue().path("from").asDouble());
                change.put("to", entry.getValue().path("to").asDouble());
                changes.put(entry.getKey(), change);
            }
            response.put("weight_changes", changes);
        }

        if (result.has("new_weights")) {
            Map<String, Object> weights = new HashMap<>();
            Iterator<Map.Entry<String, JsonNode>> it = result.path("new_weights").fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> entry = it.next();
                weights.put(entry.getKey(), entry.getValue().asDouble());
            }
            response.put("new_weights", weights);
        }

        if (result.has("message")) {
            response.put("message", result.path("message").asText());
        }

        return response;
    }

    public Map<String, Object> getWeightsStatus() throws Exception {
        List<String> args = List.of("--action", "status");
        JsonNode result = pythonRunner.runScript("learn_weights.py", args);

        Map<String, Object> status = new HashMap<>();
        status.put("version", result.path("version").asInt());
        status.put("trained_on", result.path("trained_on").asInt());
        status.put("last_updated", result.path("last_updated").asText(""));
        status.put("total_feedback", feedbackRepository.count());
        status.put("with_actual_scores", feedbackRepository.countByActualViralScoreNotNull());

        Map<String, Object> weights = new HashMap<>();
        Iterator<Map.Entry<String, JsonNode>> it = result.path("weights").fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> entry = it.next();
            weights.put(entry.getKey(), entry.getValue().asDouble());
        }
        status.put("weights", weights);

        return status;
    }
}
