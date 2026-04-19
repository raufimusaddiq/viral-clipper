package com.viralclipper.service;

import com.viralclipper.config.AppConfig;
import com.viralclipper.model.*;
import com.viralclipper.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.*;

@Service
public class VideoService {

    private static final Logger log = LoggerFactory.getLogger(VideoService.class);

    private final VideoRepository videoRepository;
    private final JobRepository jobRepository;
    private final StageStatusRepository stageStatusRepository;
    private final ClipRepository clipRepository;
    private final AppConfig appConfig;

    public VideoService(VideoRepository videoRepository, JobRepository jobRepository,
                        StageStatusRepository stageStatusRepository, ClipRepository clipRepository,
                        AppConfig appConfig) {
        this.videoRepository = videoRepository;
        this.jobRepository = jobRepository;
        this.stageStatusRepository = stageStatusRepository;
        this.clipRepository = clipRepository;
        this.appConfig = appConfig;
    }

    public Video importVideo(String url, String localPath) {
        String id = UUID.randomUUID().toString();
        String sourceType = (url != null && !url.isBlank()) ? "YOUTUBE" : "LOCAL";
        String filePath = "LOCAL".equals(sourceType) ? localPath : appConfig.getDataDir() + "/raw/" + id + ".mp4";

        Video video = new Video(id, url, sourceType, filePath);
        return videoRepository.save(video);
    }

    public List<Video> listVideos() {
        return videoRepository.findAllByOrderByCreatedAtDesc();
    }

    public Optional<Video> getVideo(String videoId) {
        return videoRepository.findById(videoId);
    }

    public void deleteVideo(String videoId) {
        List<Clip> clips = clipRepository.findByVideoIdOrderByScoreDesc(videoId);
        for (Clip clip : clips) {
            deleteFileIfExists(clip.getRenderPath());
            deleteFileIfExists(clip.getExportPath());
            clipRepository.delete(clip);
        }

        List<Job> jobs = jobRepository.findByVideoId(videoId);
        for (Job job : jobs) {
            stageStatusRepository.deleteAll(stageStatusRepository.findByJobId(job.getId()));
            jobRepository.delete(job);
        }

        String dataDir = appConfig.getDataDir();
        String videoIdShort = videoId;
        for (String sub : List.of("raw", "audio", "transcripts", "segments", "analytics")) {
            deleteFileIfExists(dataDir + "/" + sub + "/" + videoIdShort + (sub.equals("raw") ? ".mp4" : sub.equals("audio") ? ".wav" : ".json"));
        }

        videoRepository.deleteById(videoId);
        log.info("Deleted video {} with {} clips, {} jobs, and associated files", videoId, clips.size(), jobs.size());
    }

    private void deleteFileIfExists(String path) {
        if (path == null) return;
        try {
            File f = new File(path);
            if (f.exists()) {
                f.delete();
                log.debug("Deleted file: {}", path);
            }
        } catch (Exception e) {
            log.warn("Failed to delete file {}: {}", path, e.getMessage());
        }
    }
}
