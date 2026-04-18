package com.viralclipper.repository;

import com.viralclipper.model.Job;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface JobRepository extends JpaRepository<Job, String> {
    List<Job> findByVideoId(String videoId);
    List<Job> findByStatus(String status);
    Optional<Job> findTopByVideoIdOrderByCreatedAtDesc(String videoId);
    List<Job> findAllByOrderByCreatedAtDesc();
}
