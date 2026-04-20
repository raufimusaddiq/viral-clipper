package com.viralclipper.pipeline;

import com.viralclipper.config.AppConfig;
import com.viralclipper.model.*;
import com.viralclipper.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Component
public class PipelineOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(PipelineOrchestrator.class);
    private static final List<String> STAGES = List.of(
            "IMPORT", "AUDIO_EXTRACT", "TRANSCRIBE", "SEGMENT", "SCORE",
            "RENDER", "SUBTITLE", "VARIATION", "ANALYTICS"
    );

    private final AppConfig appConfig;
    private final JobRepository jobRepository;
    private final StageStatusRepository stageStatusRepository;
    private final VideoRepository videoRepository;
    private final ClipRepository clipRepository;
    private final ClipScoreRepository clipScoreRepository;
    private final PythonRunner pythonRunner;
    private final ObjectMapper objectMapper;
    private final com.viralclipper.service.FeedbackService feedbackService;

    public PipelineOrchestrator(AppConfig appConfig, JobRepository jobRepository,
                                StageStatusRepository stageStatusRepository,
                                VideoRepository videoRepository,
                                ClipRepository clipRepository,
                                ClipScoreRepository clipScoreRepository,
                                PythonRunner pythonRunner, ObjectMapper objectMapper,
                                com.viralclipper.service.FeedbackService feedbackService) {
        this.appConfig = appConfig;
        this.jobRepository = jobRepository;
        this.stageStatusRepository = stageStatusRepository;
        this.videoRepository = videoRepository;
        this.clipRepository = clipRepository;
        this.clipScoreRepository = clipScoreRepository;
        this.pythonRunner = pythonRunner;
        this.objectMapper = objectMapper;
        this.feedbackService = feedbackService;
    }

    public void runPipeline(String jobId) {
        Job job = jobRepository.findById(jobId).orElseThrow();
        Video video = videoRepository.findById(job.getVideoId()).orElseThrow();

        try {
            job.setStatus("RUNNING");
            jobRepository.save(job);

            ensureDataDirs();

            runStage(job, "IMPORT", () -> stageImport(job, video));
            runStage(job, "AUDIO_EXTRACT", () -> stageAudioExtract(job, video));
            runStage(job, "TRANSCRIBE", () -> stageTranscribe(job, video));
            runStage(job, "SEGMENT", () -> stageSegment(job, video));
            runStage(job, "SCORE", () -> stageScore(job, video));
            runStage(job, "RENDER", () -> stageRender(job, video));
            runStage(job, "SUBTITLE", () -> stageSubtitle(job, video));
            runStage(job, "VARIATION", () -> stageVariation(job, video));
            runStage(job, "ANALYTICS", () -> stageAnalytics(job, video));

            job.setStatus("COMPLETED");
            jobRepository.save(job);
            log.info("Pipeline completed for job {}", jobId);

        } catch (Exception e) {
            log.error("Pipeline failed for job {}: {}", jobId, e.getMessage(), e);
            job.setStatus("FAILED");
            job.setErrorMessage(e.getMessage());
            jobRepository.save(job);
        }
    }

    private void runStage(Job job, String stageName, StageRunnable runnable) {
        Job fresh = jobRepository.findById(job.getId()).orElse(job);
        if ("CANCELLED".equals(fresh.getStatus())) {
            throw new RuntimeException("Job cancelled by user");
        }

        StageStatus stage = stageStatusRepository
                .findByJobId(job.getId()).stream()
                .filter(s -> s.getStage().equals(stageName))
                .findFirst()
                .orElseGet(() -> {
                    StageStatus s = new StageStatus(UUID.randomUUID().toString(), job.getId(), stageName);
                    return stageStatusRepository.save(s);
                });

        stage.setStatus("IN_PROGRESS");
        stage.setStartedAt(java.time.Instant.now().toString());
        stageStatusRepository.save(stage);

        job.setCurrentStage(stageName);
        jobRepository.save(job);

        try {
            String outputPath = runnable.run();
            stage.setStatus("COMPLETED");
            stage.setCompletedAt(java.time.Instant.now().toString());
            stage.setOutputPath(outputPath);
        } catch (Exception e) {
            stage.setStatus("FAILED");
            stage.setErrorMessage(e.getMessage());
            throw new RuntimeException("Stage " + stageName + " failed: " + e.getMessage(), e);
        } finally {
            stageStatusRepository.save(stage);
        }
    }

    private String stageImport(Job job, Video video) throws Exception {
        if ("YOUTUBE".equals(video.getSourceType())) {
            String outputPath = appConfig.getDataDir() + "/raw/" + video.getId() + ".mp4";

            ProcessBuilder titlePb = new ProcessBuilder(appConfig.getYtdlpPath(), "--print", "title", "--no-warnings", video.getSourceUrl());
            titlePb.redirectErrorStream(true);
            Process titleProc = titlePb.start();
            StringBuilder titleBuilder = new StringBuilder();
            try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(titleProc.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (titleBuilder.length() > 0) titleBuilder.append(" ");
                    titleBuilder.append(line);
                }
            }
            titleProc.waitFor();
            String title = titleBuilder.toString().trim();
            if (!title.isEmpty()) {
                video.setTitle(title);
            }

            ProcessBuilder pb = new ProcessBuilder(
                    appConfig.getYtdlpPath(),
                    "-f", "bestvideo[vcodec^!=av01][ext=mp4]+bestaudio[ext=m4a]/bestvideo[vcodec^!=av01]+bestaudio/best[ext=mp4]",
                    "--merge-output-format", "mp4",
                    "--no-warnings",
                    "-o", outputPath,
                    video.getSourceUrl()
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("[yt-dlp] {}", line);
                }
            }
            int exit = p.waitFor();
            File[] partFiles = new File(appConfig.getDataDir() + "/raw").listFiles((dir, name) -> name.endsWith(".part"));
            if (partFiles != null) {
                for (File f : partFiles) {
                    log.info("Cleaning up stale partial download: {}", f.getName());
                    f.delete();
                }
            }
            if (exit != 0) throw new RuntimeException("yt-dlp download failed (exit=" + exit + ")");
            if (!new File(outputPath).exists()) throw new RuntimeException("Downloaded file not found: " + outputPath);
            video.setFilePath(outputPath);
            videoRepository.save(video);
            return outputPath;
        } else {
            return video.getFilePath();
        }
    }

    private String stageAudioExtract(Job job, Video video) throws Exception {
        String audioPath = appConfig.getDataDir() + "/audio/" + video.getId() + ".wav";
        ProcessBuilder pb = new ProcessBuilder(
                appConfig.getFfmpegPath(),
                "-i", video.getFilePath(),
                "-vn",
                "-acodec", "pcm_s16le",
                "-ar", "16000",
                "-ac", "1",
                "-y",
                audioPath
        );
        pb.redirectErrorStream(true);
        Process p = pb.start();
        try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
            while (reader.readLine() != null) {}
        }
        int exit = p.waitFor();
        if (exit != 0) throw new RuntimeException("ffmpeg audio extraction failed (exit=" + exit + ")");
        return audioPath;
    }

    private String stageTranscribe(Job job, Video video) throws Exception {
        String audioPath = appConfig.getDataDir() + "/audio/" + video.getId() + ".wav";
        String outputPath = appConfig.getDataDir() + "/transcripts/" + video.getId() + ".json";

        List<String> args = List.of(
                "--audio", audioPath,
                "--output", outputPath,
                "--language", appConfig.getWhisperLanguage(),
                "--model", appConfig.getWhisperModel(),
                "--device", appConfig.getWhisperDevice()
        );

        JsonNode result = pythonRunner.runScript("transcribe.py", args);

        Files.writeString(Path.of(outputPath), objectMapper.writeValueAsString(result));
        return outputPath;
    }

    private String stageSegment(Job job, Video video) throws Exception {
        String transcriptPath = appConfig.getDataDir() + "/transcripts/" + video.getId() + ".json";
        String outputPath = appConfig.getDataDir() + "/segments/" + video.getId() + ".json";

        List<String> args = List.of(
                "--transcript", transcriptPath,
                "--output", outputPath
        );

        JsonNode result = pythonRunner.runScript("segment.py", args);

        Files.writeString(Path.of(outputPath), objectMapper.writeValueAsString(result));
        return outputPath;
    }

    private String stageScore(Job job, Video video) throws Exception {
        String segmentsPath = appConfig.getDataDir() + "/segments/" + video.getId() + ".json";
        String audioPath = appConfig.getDataDir() + "/audio/" + video.getId() + ".wav";
        String transcriptPath = appConfig.getDataDir() + "/transcripts/" + video.getId() + ".json";

        List<String> args = new ArrayList<>(List.of(
                "--segments", segmentsPath,
                "--video", video.getFilePath()
        ));
        if (new File(audioPath).exists()) {
            args.addAll(List.of("--audio", audioPath));
        }
        if (new File(transcriptPath).exists()) {
            args.addAll(List.of("--transcript", transcriptPath));
        }

        JsonNode result = pythonRunner.runScript("score.py", args);

        String outputPath = appConfig.getDataDir() + "/segments/" + video.getId() + ".json";
        Files.writeString(Path.of(outputPath), objectMapper.writeValueAsString(result));

        saveClipsFromScoredSegments(video.getId(), result);
        return outputPath;
    }

    private void saveClipsFromScoredSegments(String videoId, JsonNode scoredData) {
        clipRepository.findByVideoIdOrderByScoreDesc(videoId).forEach(c -> clipRepository.delete(c));

        if (!scoredData.has("scoredSegments")) return;

        int rank = 1;
        for (JsonNode seg : scoredData.get("scoredSegments")) {
            Clip clip = new Clip();
            clip.setId(UUID.randomUUID().toString());
            clip.setVideoId(videoId);
            clip.setRankPos(rank++);
            clip.setScore(seg.path("finalScore").asDouble());
            clip.setTier(seg.path("tier").asText());
            clip.setTitle(seg.path("title").asText(null));
            clip.setDescription(seg.path("description").asText(null));
            clip.setStartTime(seg.path("startTime").asDouble());
            clip.setEndTime(seg.path("endTime").asDouble());
            clip.setDurationSec(seg.path("duration").asDouble());
            clip.setTextContent(seg.path("text").asText());
            clip.setCreatedAt(java.time.Instant.now().toString());
            clipRepository.save(clip);

            JsonNode scores = seg.path("scores");
            ClipScore cs = new ClipScore();
            cs.setId(UUID.randomUUID().toString());
            cs.setClipId(clip.getId());
            cs.setHookStrength(scores.path("hookStrength").asDouble());
            cs.setKeywordTrigger(scores.path("keywordTrigger").asDouble());
            cs.setNovelty(scores.path("novelty").asDouble());
            cs.setClarity(scores.path("clarity").asDouble());
            cs.setEmotionalEnergy(scores.path("emotionalEnergy").asDouble());
            cs.setTextSentiment(scores.path("textSentiment").asDouble());
            cs.setPauseStructure(scores.path("pauseStructure").asDouble());
            cs.setFacePresence(scores.path("facePresence").asDouble());
            cs.setSceneChange(scores.path("sceneChange").asDouble());
            cs.setTopicFit(scores.path("topicFit").asDouble());
            cs.setHistoryScore(scores.path("historyScore").asDouble());
            cs.setBoostTotal(scores.path("boostTotal").asDouble());
            cs.setPenaltyTotal(scores.path("penaltyTotal").asDouble());
            clipScoreRepository.save(cs);

            feedbackService.saveFeedbackSnapshot(clip, cs);
        }
    }

    private String stageRender(Job job, Video video) throws Exception {
        String segmentsPath = appConfig.getDataDir() + "/segments/" + video.getId() + ".json";
        String renderDir = appConfig.getDataDir() + "/renders/";
        new java.io.File(renderDir).mkdirs();

        String segJson = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(segmentsPath)));
        JsonNode segData = objectMapper.readTree(segJson);
        JsonNode scored = segData.path("scoredSegments");
        List<String> tiers = List.of("PRIMARY", "BACKUP");
        List<Integer> toRender = new java.util.ArrayList<>();
        for (int i = 0; i < scored.size(); i++) {
            String tier = scored.get(i).path("tier").asText("");
            if (tiers.contains(tier)) toRender.add(i);
        }

        StageStatus renderStage = stageStatusRepository.findByJobId(job.getId()).stream()
                .filter(s -> "RENDER".equals(s.getStage())).findFirst().orElse(null);

        int maxParallelRenders = 2;
        ExecutorService renderPool = Executors.newFixedThreadPool(maxParallelRenders);
        List<Future<RenderResult>> futures = new ArrayList<>();

        for (int idx : toRender) {
            Job fresh = jobRepository.findById(job.getId()).orElse(job);
            if ("CANCELLED".equals(fresh.getStatus())) {
                renderPool.shutdownNow();
                throw new RuntimeException("Job cancelled by user");
            }

            final int clipIdx = idx;
            final String segPath = segmentsPath;
            final String vidPath = video.getFilePath();
            final String rDir = renderDir;
            futures.add(renderPool.submit(() -> {
                List<String> args = List.of(
                        "--segments", segPath,
                        "--video", vidPath,
                        "--output-dir", rDir,
                        "--clip-index", String.valueOf(clipIdx)
                );
                try {
                    JsonNode result = pythonRunner.runScript("render.py", args);
                    return new RenderResult(clipIdx, true, result, null);
                } catch (Exception e) {
                    log.warn("Render failed for clip index {}: {}", clipIdx, e.getMessage());
                    return new RenderResult(clipIdx, false, null, e.getMessage());
                }
            }));
        }

        renderPool.shutdown();

        int rendered = 0, failed = 0;
        for (Future<RenderResult> f : futures) {
            try {
                RenderResult rr = f.get();
                if (rr.success) {
                    updateClipRenderStatuses(video.getId(), rr.result);
                    rendered++;
                } else {
                    failed++;
                }
                if (renderStage != null) {
                    renderStage.setOutputPath("Rendering " + (rendered + failed) + "/" + toRender.size() + " clips");
                    stageStatusRepository.save(renderStage);
                }
            } catch (Exception e) {
                failed++;
                log.warn("Render future failed: {}", e.getMessage());
            }
        }

        if (renderStage != null) {
            renderStage.setOutputPath(rendered + " rendered, " + failed + " failed");
            stageStatusRepository.save(renderStage);
        }

        return renderDir;
    }

    private void updateClipRenderStatuses(String videoId, JsonNode renderResult) {
        if (!renderResult.has("clips")) return;
        List<Clip> clips = clipRepository.findByVideoIdOrderByScoreDesc(videoId);
        for (JsonNode rc : renderResult.get("clips")) {
            int rank = rc.path("rank").asInt();
            String status = rc.path("status").asText();
            String path = rc.path("path").asText("");
            if (rank > 0 && rank <= clips.size()) {
                Clip clip = clips.get(rank - 1);
                clip.setRenderStatus(status);
                if (!path.isEmpty()) clip.setRenderPath(path);
                clipRepository.save(clip);
            }
        }
    }

    private String stageSubtitle(Job job, Video video) throws Exception {
        String transcriptPath = appConfig.getDataDir() + "/transcripts/" + video.getId() + ".json";
        String segmentsPath = appConfig.getDataDir() + "/segments/" + video.getId() + ".json";
        String renderDir = appConfig.getDataDir() + "/renders/";
        String exportDir = appConfig.getDataDir() + "/exports/";

        List<String> args = List.of(
                "--transcript", transcriptPath,
                "--segments", segmentsPath,
                "--render-dir", renderDir,
                "--output-dir", exportDir
        );

        JsonNode result = pythonRunner.runScript("subtitle.py", args);

        updateClipExportStatuses(video.getId(), result);
        return exportDir;
    }

    private void updateClipExportStatuses(String videoId, JsonNode exportResult) {
        if (!exportResult.has("clips")) return;
        List<Clip> clips = clipRepository.findByVideoIdOrderByScoreDesc(videoId);
        for (JsonNode ec : exportResult.get("clips")) {
            int rank = ec.path("rank").asInt();
            String status = ec.path("status").asText();
            String path = ec.path("path").asText("");
            if (rank > 0 && rank <= clips.size()) {
                Clip clip = clips.get(rank - 1);
                clip.setExportStatus(status);
                if (!path.isEmpty()) clip.setExportPath(path);
                clipRepository.save(clip);
            }
        }
    }

    private String stageVariation(Job job, Video video) throws Exception {
        String segmentsPath = appConfig.getDataDir() + "/segments/" + video.getId() + ".json";
        String variationDir = appConfig.getDataDir() + "/variations/";

        List<String> args = List.of(
                "--segments", segmentsPath,
                "--video", video.getFilePath(),
                "--output-dir", variationDir
        );

        pythonRunner.runScript("variation.py", args);
        return variationDir;
    }

    private String stageAnalytics(Job job, Video video) throws Exception {
        String segmentsPath = appConfig.getDataDir() + "/segments/" + video.getId() + ".json";
        String outputPath = appConfig.getDataDir() + "/analytics/" + video.getId() + ".json";

        List<String> args = List.of(
                "--segments", segmentsPath,
                "--output", outputPath
        );

        JsonNode result = pythonRunner.runScript("analytics.py", args);

        Files.writeString(Path.of(outputPath), objectMapper.writeValueAsString(result));
        return outputPath;
    }

    private void ensureDataDirs() {
        String base = appConfig.getDataDir();
        for (String dir : List.of("input", "raw", "audio", "transcripts", "segments", "clips", "renders", "exports", "variations", "analytics", "logs")) {
            Path path = Paths.get(base, dir);
            if (!Files.exists(path)) {
                try { Files.createDirectories(path); } catch (IOException e) { log.warn("Cannot create dir {}", path); }
            }
        }
    }

    private static class RenderResult {
        final int clipIndex;
        final boolean success;
        final JsonNode result;
        final String error;

        RenderResult(int clipIndex, boolean success, JsonNode result, String error) {
            this.clipIndex = clipIndex;
            this.success = success;
            this.result = result;
            this.error = error;
        }
    }

    @FunctionalInterface
    private interface StageRunnable {
        String run() throws Exception;
    }
}
