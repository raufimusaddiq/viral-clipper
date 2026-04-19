package com.viralclipper.service;

import com.viralclipper.model.Job;
import com.viralclipper.model.StageStatus;
import com.viralclipper.model.Video;
import com.viralclipper.pipeline.PipelineOrchestrator;
import com.viralclipper.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class JobService {

    private static final Logger log = LoggerFactory.getLogger(JobService.class);
    private static final List<String> STAGES = List.of(
            "IMPORT", "AUDIO_EXTRACT", "TRANSCRIBE", "SEGMENT", "SCORE",
            "RENDER", "SUBTITLE", "VARIATION", "ANALYTICS"
    );

    private final JobRepository jobRepository;
    private final StageStatusRepository stageStatusRepository;
    private final VideoRepository videoRepository;
    private final PipelineOrchestrator pipelineOrchestrator;

    public JobService(JobRepository jobRepository, StageStatusRepository stageStatusRepository,
                      VideoRepository videoRepository, PipelineOrchestrator pipelineOrchestrator) {
        this.jobRepository = jobRepository;
        this.stageStatusRepository = stageStatusRepository;
        this.videoRepository = videoRepository;
        this.pipelineOrchestrator = pipelineOrchestrator;
    }

    private final Map<String, Thread> activeThreads = new java.util.concurrent.ConcurrentHashMap<>();

    public Job createAndStartJob(String videoId) {
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new RuntimeException("Video not found: " + videoId));

        Job existing = jobRepository.findTopByVideoIdOrderByCreatedAtDesc(videoId)
                .filter(j -> "RUNNING".equals(j.getStatus()) || "QUEUED".equals(j.getStatus()))
                .orElse(null);
        if (existing != null) {
            throw new RuntimeException("Video already has an active job: " + existing.getId());
        }

        String jobId = UUID.randomUUID().toString();
        Job job = new Job(jobId, videoId);
        job = jobRepository.save(job);

        for (String stage : STAGES) {
            StageStatus ss = new StageStatus(UUID.randomUUID().toString(), jobId, stage);
            stageStatusRepository.save(ss);
        }

        Thread pipelineThread = new Thread(() -> {
            try {
                pipelineOrchestrator.runPipeline(jobId);
            } finally {
                activeThreads.remove(jobId);
            }
        }, "pipeline-" + jobId);
        pipelineThread.setDaemon(true);
        activeThreads.put(jobId, pipelineThread);
        pipelineThread.start();

        return job;
    }

    public Optional<Job> getJob(String jobId) {
        return jobRepository.findById(jobId);
    }

    public List<Job> listJobs() {
        return jobRepository.findAllByOrderByCreatedAtDesc();
    }

    public List<StageStatus> getStageStatuses(String jobId) {
        return stageStatusRepository.findByJobId(jobId);
    }

    public Job retryJob(String jobId) {
        Job job = jobRepository.findById(jobId).orElseThrow();
        if (!"FAILED".equals(job.getStatus())) {
            throw new RuntimeException("Can only retry failed jobs");
        }
        job.setStatus("QUEUED");
        job.setErrorMessage(null);
        jobRepository.save(job);

        Thread pipelineThread = new Thread(() -> {
            try {
                pipelineOrchestrator.runPipeline(jobId);
            } finally {
                activeThreads.remove(jobId);
            }
        }, "pipeline-retry-" + jobId);
        pipelineThread.setDaemon(true);
        activeThreads.put(jobId, pipelineThread);
        pipelineThread.start();

        return job;
    }

    public Job cancelJob(String jobId) {
        Job job = jobRepository.findById(jobId).orElseThrow(() -> new RuntimeException("Job not found: " + jobId));
        if (!"RUNNING".equals(job.getStatus()) && !"QUEUED".equals(job.getStatus())) {
            throw new RuntimeException("Can only cancel running or queued jobs, current: " + job.getStatus());
        }
        job.setStatus("CANCELLED");
        job.setErrorMessage("Cancelled by user");
        jobRepository.save(job);

        Thread t = activeThreads.remove(jobId);
        if (t != null) {
            t.interrupt();
        }

        return job;
    }
}
