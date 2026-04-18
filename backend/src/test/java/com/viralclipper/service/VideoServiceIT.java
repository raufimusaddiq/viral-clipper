package com.viralclipper.service;

import com.viralclipper.IntegrationTest;
import com.viralclipper.model.Video;
import com.viralclipper.repository.VideoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@IntegrationTest
class VideoServiceIT {

    @Autowired
    private VideoService videoService;

    @Autowired
    private VideoRepository videoRepository;

    @BeforeEach
    void cleanUp() {
        videoRepository.deleteAll();
    }

    @Test
    void importVideo_youtubeUrl_createsYoutubeVideo() {
        Video video = videoService.importVideo("https://www.youtube.com/watch?v=abc", null);

        assertNotNull(video.getId());
        assertEquals("YOUTUBE", video.getSourceType());
        assertEquals("https://www.youtube.com/watch?v=abc", video.getSourceUrl());
        assertNotNull(video.getCreatedAt());
    }

    @Test
    void importVideo_localPath_createsLocalVideo() {
        Video video = videoService.importVideo(null, "/path/to/video.mp4");

        assertEquals("LOCAL", video.getSourceType());
        assertEquals("/path/to/video.mp4", video.getFilePath());
    }

    @Test
    void importVideo_urlTakesPriority_overLocalPath() {
        Video video = videoService.importVideo("https://youtube.com/test", "/local/file.mp4");

        assertEquals("YOUTUBE", video.getSourceType());
    }

    @Test
    void listVideos_returnsAllOrderedByDate() {
        videoService.importVideo(null, "/first.mp4");
        videoService.importVideo(null, "/second.mp4");

        List<Video> videos = videoService.listVideos();
        assertEquals(2, videos.size());
    }

    @Test
    void getVideo_existingId_returnsVideo() {
        Video saved = videoService.importVideo(null, "/test.mp4");

        Optional<Video> found = videoService.getVideo(saved.getId());
        assertTrue(found.isPresent());
        assertEquals(saved.getId(), found.get().getId());
    }

    @Test
    void getVideo_nonExistentId_returnsEmpty() {
        Optional<Video> found = videoService.getVideo("nonexistent");
        assertFalse(found.isPresent());
    }

    @Test
    void deleteVideo_removesFromRepository() {
        Video saved = videoService.importVideo(null, "/test.mp4");

        videoService.deleteVideo(saved.getId());

        assertFalse(videoRepository.findById(saved.getId()).isPresent());
    }
}
