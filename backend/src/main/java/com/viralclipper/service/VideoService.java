package com.viralclipper.service;

import com.viralclipper.config.AppConfig;
import com.viralclipper.model.Video;
import com.viralclipper.repository.VideoRepository;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class VideoService {

    private final VideoRepository videoRepository;
    private final AppConfig appConfig;

    public VideoService(VideoRepository videoRepository, AppConfig appConfig) {
        this.videoRepository = videoRepository;
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
        videoRepository.deleteById(videoId);
    }
}
