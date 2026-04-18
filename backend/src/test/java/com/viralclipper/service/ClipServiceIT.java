package com.viralclipper.service;

import com.viralclipper.IntegrationTest;
import com.viralclipper.model.Clip;
import com.viralclipper.model.ClipScore;
import com.viralclipper.model.Video;
import com.viralclipper.repository.ClipRepository;
import com.viralclipper.repository.ClipScoreRepository;
import com.viralclipper.repository.VideoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@IntegrationTest
class ClipServiceIT {

    @Autowired
    private ClipService clipService;

    @Autowired
    private ClipRepository clipRepository;

    @Autowired
    private ClipScoreRepository clipScoreRepository;

    @Autowired
    private VideoRepository videoRepository;

    private String videoId;

    @BeforeEach
    void setUp() {
        clipRepository.deleteAll();
        clipScoreRepository.deleteAll();
        videoRepository.deleteAll();

        Video video = new Video("vid1", null, "LOCAL", "/test.mp4");
        videoRepository.save(video);
        videoId = video.getId();
    }

    @Test
    void listClips_emptyVideo_returnsEmptyList() {
        List<Clip> clips = clipService.listClips(videoId);
        assertTrue(clips.isEmpty());
    }

    @Test
    void listClips_returnsClipsOrderedByScore() {
        Clip clip1 = createClip("c1", 0.5);
        Clip clip2 = createClip("c2", 0.9);
        clipRepository.save(clip1);
        clipRepository.save(clip2);

        List<Clip> clips = clipService.listClips(videoId);
        assertEquals(2, clips.size());
        assertEquals(0.9, clips.get(0).getScore());
        assertEquals(0.5, clips.get(1).getScore());
    }

    @Test
    void getClip_existingClip_returnsClip() {
        Clip clip = createClip("c1", 0.8);
        clipRepository.save(clip);

        Optional<Clip> found = clipService.getClip("c1");
        assertTrue(found.isPresent());
        assertEquals(0.8, found.get().getScore());
    }

    @Test
    void getClipScore_existingScore_returnsScore() {
        Clip clip = createClip("c1", 0.8);
        clipRepository.save(clip);

        ClipScore score = new ClipScore();
        score.setId("s1");
        score.setClipId("c1");
        score.setHookStrength(0.9);
        clipScoreRepository.save(score);

        Optional<ClipScore> found = clipService.getClipScore("c1");
        assertTrue(found.isPresent());
        assertEquals(0.9, found.get().getHookStrength());
    }

    @Test
    void getClipScore_noScore_returnsEmpty() {
        Optional<ClipScore> found = clipService.getClipScore("nonexistent");
        assertFalse(found.isPresent());
    }

    private Clip createClip(String id, double score) {
        Clip clip = new Clip();
        clip.setId(id);
        clip.setVideoId(videoId);
        clip.setScore(score);
        clip.setTier(score >= 0.8 ? "PRIMARY" : score >= 0.65 ? "BACKUP" : "SKIP");
        clip.setStartTime(0.0);
        clip.setEndTime(30.0);
        clip.setDurationSec(30.0);
        clip.setCreatedAt(java.time.Instant.now().toString());
        return clip;
    }
}
