package com.viralclipper.service;

import com.viralclipper.IntegrationTest;
import com.viralclipper.model.Job;
import com.viralclipper.model.Video;
import com.viralclipper.repository.JobRepository;
import com.viralclipper.repository.VideoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@IntegrationTest
class JobServiceIT {

    @Autowired
    private JobService jobService;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private VideoRepository videoRepository;

    @BeforeEach
    void cleanUp() {
        jobRepository.deleteAll();
        videoRepository.deleteAll();
    }

    @Test
    void createAndStartJob_validVideo_createsJob() {
        Video video = new Video("v1", null, "LOCAL", "/test.mp4");
        videoRepository.save(video);

        Job job = jobService.createAndStartJob("v1");

        assertNotNull(job.getId());
        assertEquals("v1", job.getVideoId());
    }

    @Test
    void createAndStartJob_nonExistentVideo_throwsException() {
        assertThrows(RuntimeException.class, () -> jobService.createAndStartJob("nonexistent"));
    }

    @Test
    void createAndStartJob_duplicateActiveJob_throwsException() {
        Video video = new Video("v1", null, "LOCAL", "/test.mp4");
        videoRepository.save(video);

        jobService.createAndStartJob("v1");

        assertThrows(RuntimeException.class, () -> jobService.createAndStartJob("v1"));
    }

    @Test
    void getJob_existingJob_returnsJob() {
        Video video = new Video("v1", null, "LOCAL", "/test.mp4");
        videoRepository.save(video);

        Job created = jobService.createAndStartJob("v1");

        Job found = jobService.getJob(created.getId()).orElseThrow();
        assertEquals(created.getId(), found.getId());
    }

    @Test
    void getStageStatuses_returnsAllStages() {
        Video video = new Video("v1", null, "LOCAL", "/test.mp4");
        videoRepository.save(video);

        Job created = jobService.createAndStartJob("v1");

        List<?> stages = jobService.getStageStatuses(created.getId());
        assertEquals(9, stages.size());
    }

    @Test
    void listJobs_returnsAll() {
        Video v1 = new Video("v1", null, "LOCAL", "/test1.mp4");
        Video v2 = new Video("v2", null, "LOCAL", "/test2.mp4");
        videoRepository.save(v1);
        videoRepository.save(v2);

        jobService.createAndStartJob("v1");

        List<Job> jobs = jobService.listJobs();
        assertFalse(jobs.isEmpty());
    }

    @Test
    void retryJob_nonFailedJob_throwsException() {
        Video video = new Video("v1", null, "LOCAL", "/test.mp4");
        videoRepository.save(video);

        Job created = jobService.createAndStartJob("v1");

        assertThrows(RuntimeException.class, () -> jobService.retryJob(created.getId()));
    }
}
