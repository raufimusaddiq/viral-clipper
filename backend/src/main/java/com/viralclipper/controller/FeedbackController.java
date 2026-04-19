package com.viralclipper.controller;

import com.viralclipper.model.ClipFeedback;
import com.viralclipper.service.FeedbackService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class FeedbackController {

    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping("/clips/{clipId}/feedback")
    public ResponseEntity<Map<String, Object>> submitFeedback(
            @PathVariable String clipId,
            @RequestBody Map<String, Integer> body) {
        try {
            int views = body.getOrDefault("views", 0);
            int likes = body.getOrDefault("likes", 0);
            int comments = body.getOrDefault("comments", 0);
            int shares = body.getOrDefault("shares", 0);
            int saves = body.getOrDefault("saves", 0);

            ClipFeedback fb = feedbackService.submitTikTokMetrics(clipId, views, likes, comments, shares, saves);
            Map<String, Object> data = new HashMap<>();
            data.put("clipId", fb.getClipId());
            data.put("viralScore", fb.getActualViralScore());
            data.put("views", fb.getTiktokViews());
            data.put("likes", fb.getTiktokLikes());
            return ResponseEntity.ok(Map.of("status", "ok", "data", data));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("status", "error", "error", e.getMessage()));
        }
    }

    @PostMapping("/feedback/train")
    public ResponseEntity<Map<String, Object>> trainWeights() {
        try {
            Map<String, Object> result = feedbackService.triggerTraining();
            return ResponseEntity.ok(Map.of("status", "ok", "data", result));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("status", "error", "error", e.getMessage()));
        }
    }

    @GetMapping("/feedback/weights")
    public ResponseEntity<Map<String, Object>> getWeights() {
        try {
            Map<String, Object> status = feedbackService.getWeightsStatus();
            return ResponseEntity.ok(Map.of("status", "ok", "data", status));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("status", "error", "error", e.getMessage()));
        }
    }
}
