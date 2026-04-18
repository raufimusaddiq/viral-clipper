package com.viralclipper.controller;

import com.viralclipper.model.Clip;
import com.viralclipper.model.ClipScore;
import com.viralclipper.service.ClipService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ClipController {

    private final ClipService clipService;

    public ClipController(ClipService clipService) {
        this.clipService = clipService;
    }

    @GetMapping("/videos/{videoId}/clips")
    public ResponseEntity<Map<String, Object>> listClips(@PathVariable String videoId) {
        List<Clip> clips = clipService.listClips(videoId);
        return ResponseEntity.ok(Map.of("status", "ok", "data", Map.of("clips", clips)));
    }

    @GetMapping("/clips/{clipId}")
    public ResponseEntity<Map<String, Object>> getClip(@PathVariable String clipId) {
        return clipService.getClip(clipId)
                .map(clip -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("clip", clip);
                    clipService.getClipScore(clipId).ifPresent(score -> {
                        data.put("scoreBreakdown", score);
                    });
                    return ResponseEntity.ok(Map.of("status", "ok", "data", data));
                })
                .orElse(ResponseEntity.status(404).body(Map.of("status", "error", "error", "Clip not found")));
    }

    @PostMapping("/clips/export")
    public ResponseEntity<Map<String, Object>> exportClips(@RequestBody Map<String, List<String>> body) {
        List<String> clipIds = body.get("clipIds");
        if (clipIds == null || clipIds.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "error", "clipIds required"));
        }
        try {
            List<Clip> exported = clipService.exportClips(clipIds);
            return ResponseEntity.ok(Map.of("status", "ok", "data", Map.of("exported", exported)));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("status", "error", "error", e.getMessage()));
        }
    }

    @GetMapping("/clips/{clipId}/preview")
    public ResponseEntity<Resource> previewClip(@PathVariable String clipId) {
        return clipService.getClip(clipId)
                .filter(c -> c.getRenderPath() != null)
                .map(c -> serveFile(c.getRenderPath(), VIDEO_MP4, false))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/clips/{clipId}/export")
    public ResponseEntity<Resource> downloadClip(@PathVariable String clipId) {
        return clipService.getClip(clipId)
                .filter(c -> c.getExportPath() != null)
                .map(c -> serveFile(c.getExportPath(), MediaType.APPLICATION_OCTET_STREAM, true))
                .orElse(ResponseEntity.notFound().build());
    }

    private ResponseEntity<Resource> serveFile(String filePath, MediaType mediaType, boolean asAttachment) {
        File file = Paths.get(filePath).toFile();
        if (!file.exists()) return ResponseEntity.notFound().build();

        FileSystemResource resource = new FileSystemResource(file);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);
        if (asAttachment) {
            headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getName() + "\"");
        }
        return ResponseEntity.ok().headers(headers).body(resource);
    }

    private static final MediaType VIDEO_MP4 = MediaType.parseMediaType("video/mp4");
}
