package com.viralclipper.controller;

import com.viralclipper.model.Job;
import com.viralclipper.model.StageStatus;
import com.viralclipper.service.JobService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @PostMapping("/process")
    public ResponseEntity<Map<String, Object>> startProcessing(@RequestBody Map<String, String> body) {
        String videoId = body.get("videoId");
        if (videoId == null || videoId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "error", "videoId required"));
        }
        try {
            Job job = jobService.createAndStartJob(videoId);
            return ResponseEntity.ok(Map.of("status", "ok", "data", Map.of(
                    "jobId", job.getId(),
                    "status", job.getStatus(),
                    "currentStage", job.getCurrentStage() != null ? job.getCurrentStage() : ""
            )));
        } catch (RuntimeException e) {
            return ResponseEntity.status(409).body(Map.of("status", "error", "error", e.getMessage()));
        }
    }

    @GetMapping("/jobs")
    public ResponseEntity<Map<String, Object>> listJobs() {
        List<Job> jobs = jobService.listJobs();
        return ResponseEntity.ok(Map.of("status", "ok", "data", Map.of("jobs", jobs)));
    }

    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<Map<String, Object>> getJob(@PathVariable String jobId) {
        return jobService.getJob(jobId)
                .map(job -> {
                    List<StageStatus> stages = jobService.getStageStatuses(jobId);
                    return ResponseEntity.ok(Map.of("status", "ok", "data", Map.of(
                            "job", job,
                            "stages", stages
                    )));
                })
                .orElse(ResponseEntity.status(404).body(Map.of("status", "error", "error", "Job not found")));
    }

    @PostMapping("/jobs/{jobId}/retry")
    public ResponseEntity<Map<String, Object>> retryJob(@PathVariable String jobId) {
        try {
            Job job = jobService.retryJob(jobId);
            return ResponseEntity.ok(Map.of("status", "ok", "data", Map.of(
                    "jobId", job.getId(),
                    "status", job.getStatus()
            )));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "error", e.getMessage()));
        }
    }

    @PostMapping("/jobs/{jobId}/cancel")
    public ResponseEntity<Map<String, Object>> cancelJob(@PathVariable String jobId) {
        try {
            Job job = jobService.cancelJob(jobId);
            return ResponseEntity.ok(Map.of("status", "ok", "data", Map.of(
                    "jobId", job.getId(),
                    "status", job.getStatus()
            )));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "error", e.getMessage()));
        }
    }
}
