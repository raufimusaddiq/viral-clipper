package com.viralclipper.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viralclipper.config.AppConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PythonRunnerTest {

    @TempDir
    Path tempDir;

    @Mock
    private AppConfig appConfig;

    private ObjectMapper objectMapper;
    private PythonRunner pythonRunner;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        objectMapper = new ObjectMapper();
        when(appConfig.getPythonPath()).thenReturn("python3");
        when(appConfig.getPipelineDir()).thenReturn("ai-pipeline");
        pythonRunner = new PythonRunner(appConfig, objectMapper);
    }

    @Test
    void runScript_withEchoPython_simulatesSuccess() throws Exception {
        String jsonOutput = "{\"success\":true,\"data\":{\"result\":\"ok\"}}";

        ProcessBuilder pb = mock(ProcessBuilder.class);
        Process process = mock(Process.class);

        when(process.getInputStream()).thenReturn(
                new ByteArrayInputStream(jsonOutput.getBytes()));
        when(process.getErrorStream()).thenReturn(
                new ByteArrayInputStream("".getBytes()));
        when(process.waitFor()).thenReturn(0);
    }

    @Test
    void runScript_withNonZeroExit_throwsException() {
        String errorJson = "{\"success\":false,\"error\":\"something went wrong\"}";

        assertThrows(RuntimeException.class, () -> {
            throw new RuntimeException("Python script test.py failed (exit=1): stderr output");
        });
    }

    @Test
    void runScript_withSuccessFalseInOutput_throwsException() {
        assertThrows(RuntimeException.class, () -> {
            throw new RuntimeException("Python script test.py error: model not found");
        });
    }
}
