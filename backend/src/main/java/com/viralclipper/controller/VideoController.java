package com.viralclipper.controller;

import com.viralclipper.model.Video;
import com.viralclipper.service.VideoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class VideoController {

    private final VideoService videoService;

    public VideoController(VideoService videoService) {
        this.videoService = videoService;
    }

    @PostMapping("/import")
    public ResponseEntity<Map<String, Object>> importVideo(@RequestBody Map<String, String> body) {
        String url = body.get("url");
        String localPath = body.get("localPath");
        Video video = videoService.importVideo(url, localPath);
        return ResponseEntity.ok(Map.of("status", "ok", "data", Map.of(
                "videoId", video.getId(),
                "sourceType", video.getSourceType()
        )));
    }

    @GetMapping("/videos")
    public ResponseEntity<Map<String, Object>> listVideos() {
        List<Video> videos = videoService.listVideos();
        return ResponseEntity.ok(Map.of("status", "ok", "data", Map.of("videos", videos)));
    }

    @GetMapping("/videos/{videoId}")
    public ResponseEntity<Map<String, Object>> getVideo(@PathVariable String videoId) {
        return videoService.getVideo(videoId)
                .map(v -> ResponseEntity.ok(Map.of("status", "ok", "data", v)))
                .orElse(ResponseEntity.status(404).body(Map.of("status", "error", "error", "Video not found")));
    }

    @DeleteMapping("/videos/{videoId}")
    public ResponseEntity<Map<String, Object>> deleteVideo(@PathVariable String videoId) {
        videoService.deleteVideo(videoId);
        return ResponseEntity.ok(Map.of("status", "ok", "data", Map.of("deleted", true, "videoId", videoId)));
    }
}
