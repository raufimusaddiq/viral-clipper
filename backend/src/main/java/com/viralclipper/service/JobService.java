package com.viralclipper.service;

import com.viralclipper.model.Job;
import com.viralclipper.model.StageStatus;
import com.viralclipper.model.Video;
import com.viralclipper.pipeline.PipelineOrchestrator;
import com.viralclipper.repository.*;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class JobService {

    private static final Logger log = LoggerFactory.getLogger(JobService.class);
    private static final List<String> STAGES = List.of(
            "IMPORT", "AUDIO_EXTRACT", "TRANSCRIBE", "SEGMENT", "SCORE",
            "RENDER", "SUBTITLE", "VARIATION", "ANALYTICS"
    );

    // Bounded pool: the GPU is the shared resource, and Whisper (TRANSCRIBE)
    // plus NVENC render can't meaningfully share it, so we cap concurrency at
    // 2 — one pipeline doing GPU work, one queued up on CPU stages. Extra
    // submissions wait in the queue instead of spawning raw threads.
    private static final int MAX_CONCURRENT_PIPELINES = 2;
    private static final int QUEUE_CAPACITY = 50;

    private final JobRepository jobRepository;
    private final StageStatusRepository stageStatusRepository;
    private final VideoRepository videoRepository;
    private final PipelineOrchestrator pipelineOrchestrator;

    private final ThreadPoolExecutor pipelinePool;
    private final Map<String, Future<?>> activeJobs = new ConcurrentHashMap<>();

    public JobService(JobRepository jobRepository, StageStatusRepository stageStatusRepository,
                      VideoRepository videoRepository, PipelineOrchestrator pipelineOrchestrator) {
        this.jobRepository = jobRepository;
        this.stageStatusRepository = stageStatusRepository;
        this.videoRepository = videoRepository;
        this.pipelineOrchestrator = pipelineOrchestrator;

        AtomicLong tid = new AtomicLong();
        this.pipelinePool = new ThreadPoolExecutor(
                MAX_CONCURRENT_PIPELINES, MAX_CONCURRENT_PIPELINES,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(QUEUE_CAPACITY),
                r -> {
                    Thread t = new Thread(r, "pipeline-" + tid.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

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

        submitPipeline(jobId);
        return job;
    }

    private void submitPipeline(String jobId) {
        Future<?> f = pipelinePool.submit(() -> {
            try {
                pipelineOrchestrator.runPipeline(jobId);
            } finally {
                activeJobs.remove(jobId);
            }
        });
        activeJobs.put(jobId, f);
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

        submitPipeline(jobId);
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

        Future<?> f = activeJobs.remove(jobId);
        if (f != null) {
            f.cancel(true);
        }

        return job;
    }

    /**
     * Startup sweep: any job stuck in RUNNING/QUEUED from a previous crash
     * (&gt; 30 min old) is marked FAILED. Runs once on ApplicationReady.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void recoverOrphanJobsOnStartup() {
        int n = sweepOrphans(30, "startup");
        if (n > 0) {
            log.info("Recovered {} orphan jobs on startup", n);
        }
    }

    /**
     * Live sweep: catches jobs that got stuck mid-session (process hang,
     * PythonRunner timeout propagated as FAILED-but-saved-nothing, etc.).
     * 10-minute cutoff since {@code updated_at} is refreshed by
     * PipelineOrchestrator between every stage.
     */
    @Scheduled(fixedDelayString = "${viralclipper.orphan-sweep-ms:300000}",
               initialDelayString = "${viralclipper.orphan-sweep-initial-ms:300000}")
    public void recoverOrphanJobsPeriodic() {
        int n = sweepOrphans(10, "periodic");
        if (n > 0) {
            log.warn("Periodic sweep recovered {} orphan jobs", n);
        }
    }

    private int sweepOrphans(int staleMinutes, String reason) {
        List<Job> running = jobRepository.findByStatus("RUNNING");
        List<Job> queued = jobRepository.findByStatus("QUEUED");
        if (running.isEmpty() && queued.isEmpty()) {
            return 0;
        }

        Instant cutoff = Instant.now().minus(staleMinutes, ChronoUnit.MINUTES);
        int recovered = 0;

        for (Job job : concat(running, queued)) {
            // Skip jobs whose Future is still alive — they're genuinely running.
            Future<?> f = activeJobs.get(job.getId());
            if (f != null && !f.isDone()) {
                continue;
            }
            if (job.getUpdatedAt() == null) {
                continue;
            }
            try {
                Instant updated = Instant.parse(job.getUpdatedAt());
                if (updated.isBefore(cutoff)) {
                    job.setStatus("FAILED");
                    job.setErrorMessage("Orphan job recovered (" + reason + "); "
                            + "last heartbeat " + job.getUpdatedAt());
                    jobRepository.save(job);
                    recovered++;
                    log.warn("Recovered orphan job {} (video {}), last heartbeat {}",
                            job.getId(), job.getVideoId(), job.getUpdatedAt());
                }
            } catch (Exception e) {
                log.warn("Could not parse updatedAt for job {}: {}", job.getId(), job.getUpdatedAt());
            }
        }
        return recovered;
    }

    private static <T> List<T> concat(List<T> a, List<T> b) {
        List<T> out = new ArrayList<>(a.size() + b.size());
        out.addAll(a);
        out.addAll(b);
        return out;
    }

    @PreDestroy
    void shutdown() {
        pipelinePool.shutdown();
        try {
            if (!pipelinePool.awaitTermination(10, TimeUnit.SECONDS)) {
                pipelinePool.shutdownNow();
            }
        } catch (InterruptedException e) {
            pipelinePool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
