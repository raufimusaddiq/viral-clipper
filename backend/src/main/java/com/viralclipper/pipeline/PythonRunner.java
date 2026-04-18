package com.viralclipper.pipeline;

import com.viralclipper.config.AppConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

@Component
public class PythonRunner {

    private static final Logger log = LoggerFactory.getLogger(PythonRunner.class);
    private final AppConfig appConfig;
    private final ObjectMapper objectMapper;

    public PythonRunner(AppConfig appConfig, ObjectMapper objectMapper) {
        this.appConfig = appConfig;
        this.objectMapper = objectMapper;
    }

    public JsonNode runScript(String scriptName, List<String> args) throws Exception {
        String scriptPath = resolveScriptPath(scriptName);
        List<String> command = new ArrayList<>();
        command.add(appConfig.getPythonPath());
        command.add(scriptPath);
        command.addAll(args);

        log.info("Running Python script: {}", String.join(" ", command));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(false);
        pb.environment().put("PYTHONIOENCODING", "utf-8");

        Process process = pb.start();

        String stdout;
        String stderr;
        try (BufferedReader outReader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            stdout = outReader.lines().reduce("", (a, b) -> a + b + "\n");
        }
        try (BufferedReader errReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
            stderr = errReader.lines().reduce("", (a, b) -> a + b + "\n");
        }

        int exitCode = process.waitFor();

        if (!stderr.isBlank()) {
            log.info("Python stderr [{}]: {}", scriptName, stderr.trim());
        }

        if (exitCode != 0) {
            throw new RuntimeException("Python script " + scriptName + " failed (exit=" + exitCode + "): " + stderr);
        }

        JsonNode result = objectMapper.readTree(stdout);

        if (!result.path("success").asBoolean(false)) {
            String error = result.path("error").asText("Unknown error");
            throw new RuntimeException("Python script " + scriptName + " error: " + error);
        }

        return result.path("data");
    }

    private String resolveScriptPath(String scriptName) {
        return appConfig.getPipelineDir() + "/" + scriptName;
    }
}
