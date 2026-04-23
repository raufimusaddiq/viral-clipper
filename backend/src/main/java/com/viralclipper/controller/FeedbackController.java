package com.viralclipper.controller;

import com.viralclipper.model.ClipFeedback;
import com.viralclipper.service.FeedbackService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
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
            @RequestBody Map<String, Object> body) {
        try {
            // postedAt is REQUIRED — the viral score is normalized by time on
            // platform, so raw metrics without a timestamp are meaningless.
            Object postedAtObj = body.get("postedAt");
            if (postedAtObj == null || postedAtObj.toString().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "status", "error",
                        "error", "postedAt is required (ISO-8601 timestamp of TikTok upload)"
                ));
            }

            int views = intValue(body.get("views"));
            int likes = intValue(body.get("likes"));
            int comments = intValue(body.get("comments"));
            int shares = intValue(body.get("shares"));
            int saves = intValue(body.get("saves"));

            ClipFeedback fb = feedbackService.submitTikTokMetrics(
                    clipId, views, likes, comments, shares, saves, postedAtObj.toString());

            Map<String, Object> data = new HashMap<>();
            data.put("clipId", fb.getClipId());
            data.put("viralScore", fb.getActualViralScore());
            data.put("views", fb.getTiktokViews());
            data.put("likes", fb.getTiktokLikes());
            data.put("postedAt", fb.getPostedAt());
            return ResponseEntity.ok(Map.of("status", "ok", "data", data));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("status", "error", "error", e.getMessage()));
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

    private static int intValue(Object v) {
        if (v == null) return 0;
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(v.toString()); } catch (NumberFormatException e) { return 0; }
    }
}
