package com.viralclipper.repository;

import com.viralclipper.model.Video;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface VideoRepository extends JpaRepository<Video, String> {
    List<Video> findAllByOrderByCreatedAtDesc();
}
