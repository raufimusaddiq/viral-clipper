package com.viralclipper.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    @Value("${app.python-path}")
    private String pythonPath;

    @Value("${app.pipeline-dir}")
    private String pipelineDir;

    @Value("${app.ffmpeg-path}")
    private String ffmpegPath;

    @Value("${app.ytdlp-path}")
    private String ytdlpPath;

    @Value("${app.data-dir}")
    private String dataDir;

    @Value("${app.whisper-model}")
    private String whisperModel;

    @Value("${app.whisper-device}")
    private String whisperDevice;

    @Value("${app.whisper-language}")
    private String whisperLanguage;

    @Value("${app.niche-keywords}")
    private String nicheKeywords;

    public String getPythonPath() { return pythonPath; }
    public String getPipelineDir() { return pipelineDir; }
    public String getFfmpegPath() { return ffmpegPath; }
    public String getYtdlpPath() { return ytdlpPath; }
    public String getDataDir() { return dataDir; }
    public String getWhisperModel() { return whisperModel; }
    public String getWhisperDevice() { return whisperDevice; }
    public String getWhisperLanguage() { return whisperLanguage; }
    public String getNicheKeywords() { return nicheKeywords; }
}
