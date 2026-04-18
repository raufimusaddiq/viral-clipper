package com.viralclipper.service;

import com.viralclipper.config.AppConfig;
import com.viralclipper.model.Clip;
import com.viralclipper.model.ClipScore;
import com.viralclipper.repository.ClipRepository;
import com.viralclipper.repository.ClipScoreRepository;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;

@Service
public class ClipService {

    private final ClipRepository clipRepository;
    private final ClipScoreRepository clipScoreRepository;
    private final AppConfig appConfig;

    public ClipService(ClipRepository clipRepository, ClipScoreRepository clipScoreRepository, AppConfig appConfig) {
        this.clipRepository = clipRepository;
        this.clipScoreRepository = clipScoreRepository;
        this.appConfig = appConfig;
    }

    public List<Clip> listClips(String videoId) {
        return clipRepository.findByVideoIdOrderByScoreDesc(videoId);
    }

    public Optional<Clip> getClip(String clipId) {
        return clipRepository.findById(clipId);
    }

    public Optional<ClipScore> getClipScore(String clipId) {
        return Optional.ofNullable(clipScoreRepository.findByClipId(clipId));
    }

    public List<Clip> exportClips(List<String> clipIds) throws IOException {
        List<Clip> exported = new ArrayList<>();
        for (String clipId : clipIds) {
            Clip clip = clipRepository.findById(clipId).orElseThrow();
            if (clip.getRenderPath() != null) {
                String exportPath = appConfig.getDataDir() + "/exports/" + clip.getId() + "_final.mp4";
                Path source = Paths.get(clip.getRenderPath());
                if (clip.getExportPath() != null) source = Paths.get(clip.getExportPath());
                Files.copy(source, Paths.get(exportPath), StandardCopyOption.REPLACE_EXISTING);
                clip.setExportStatus("COMPLETED");
                clip.setExportPath(exportPath);
                clipRepository.save(clip);
                exported.add(clip);
            }
        }
        return exported;
    }
}
