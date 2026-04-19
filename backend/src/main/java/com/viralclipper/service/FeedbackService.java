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

    public void saveFeedbackSnapshot(Clip clip, ClipScore clipScore) {
        try {
            Map<String, Object> features = new HashMap<>();
            features.put("hookStrength", clipScore.getHookStrength());
            features.put("keywordTrigger", clipScore.getKeywordTrigger());
            features.put("novelty", clipScore.getNovelty());
            features.put("clarity", clipScore.getClarity());
            features.put("emotionalEnergy", clipScore.getEmotionalEnergy());
            features.put("textSentiment", clipScore.getTextSentiment());
            features.put("pauseStructure", clipScore.getPauseStructure());
            features.put("facePresence", clipScore.getFacePresence());
            features.put("sceneChange", clipScore.getSceneChange());
            features.put("topicFit", clipScore.getTopicFit());
            features.put("historyScore", clipScore.getHistoryScore());

            ClipFeedback fb = new ClipFeedback();
            fb.setId(UUID.randomUUID().toString());
            fb.setClipId(clip.getId());
            fb.setVideoId(clip.getVideoId());
            fb.setFeatures(objectMapper.writeValueAsString(features));
            fb.setPredictedScore(clip.getScore());
            fb.setPredictedTier(clip.getTier());
            fb.setCreatedAt(java.time.Instant.now().toString());
            feedbackRepository.save(fb);
        } catch (Exception e) {
            log.warn("Failed to save feedback snapshot for clip {}: {}", clip.getId(), e.getMessage());
        }
    }

    public ClipFeedback submitTikTokMetrics(String clipId, int views, int likes, int comments, int shares, int saves) {
        ClipFeedback fb = feedbackRepository.findByClipId(clipId)
                .orElseGet(() -> {
                    ClipFeedback newFb = new ClipFeedback();
                    newFb.setId(UUID.randomUUID().toString());
                    newFb.setClipId(clipId);
                    Clip clip = clipRepository.findById(clipId).orElseThrow();
                    newFb.setVideoId(clip.getVideoId());
                    newFb.setFeatures("{}");
                    newFb.setPredictedScore(0.0);
                    newFb.setPredictedTier("UNKNOWN");
                    newFb.setCreatedAt(java.time.Instant.now().toString());
                    return newFb;
                });

        fb.setTiktokViews(views);
        fb.setTiktokLikes(likes);
        fb.setTiktokComments(comments);
        fb.setTiktokShares(shares);
        fb.setTiktokSaves(saves);
        fb.setPostedAt(java.time.Instant.now().toString());
        fb.setLastChecked(java.time.Instant.now().toString());

        try {
            List<String> args = List.of(
                    "--action", "calc-viral-score",
                    "--views", String.valueOf(views),
                    "--likes", String.valueOf(likes),
                    "--comments", String.valueOf(comments),
                    "--shares", String.valueOf(shares),
                    "--saves", String.valueOf(saves)
            );
            JsonNode result = pythonRunner.runScript("feedback.py", args);
            fb.setActualViralScore(result.path("viralScore").asDouble());
        } catch (Exception e) {
            log.warn("Failed to calculate viral score: {}", e.getMessage());
        }

        return feedbackRepository.save(fb);
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
