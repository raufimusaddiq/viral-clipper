package com.viralclipper.pipeline;

import com.viralclipper.config.AppConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class PythonRunner {

    private static final Logger log = LoggerFactory.getLogger(PythonRunner.class);

    // Per-script wall-clock timeouts. A hung Python process no longer blocks a
    // pipeline thread indefinitely — it's killed and the stage is marked FAILED.
    private static final Map<String, Integer> TIMEOUT_MINUTES = Map.of(
            "transcribe.py", 20,
            "score.py", 10,
            "render.py", 30,
            "subtitle.py", 10,
            "variation.py", 10,
            "analytics.py", 5,
            "segment.py", 2,
            "discover.py", 5,
            "channel_crawler.py", 10
    );
    private static final int DEFAULT_TIMEOUT_MINUTES = 15;
    private static final int STDERR_RING_BYTES = 64 * 1024;

    private final AppConfig appConfig;
    private final ObjectMapper objectMapper;

    public PythonRunner(AppConfig appConfig, ObjectMapper objectMapper) {
        this.appConfig = appConfig;
        this.objectMapper = objectMapper;
    }

    public JsonNode runScript(String scriptName, List<String> args) throws Exception {
        return runScript(scriptName, args, TIMEOUT_MINUTES.getOrDefault(scriptName, DEFAULT_TIMEOUT_MINUTES));
    }

    public JsonNode runScript(String scriptName, List<String> args, int timeoutMinutes) throws Exception {
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

        // Drain stdout and stderr on separate threads to avoid the classic pipe-
        // buffer deadlock (child blocks writing stderr while we're still reading
        // stdout, or vice-versa). stdout is collected in full (we parse it as
        // JSON). stderr is clipped to the last 64 KB so a rogue tqdm stream
        // cannot OOM the backend.
        ExecutorService drainers = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "pyrunner-drain-" + scriptName);
            t.setDaemon(true);
            return t;
        });
        try {
            CompletableFuture<String> stdoutF = CompletableFuture.supplyAsync(
                    () -> drainFull(process.getInputStream()), drainers);
            CompletableFuture<String> stderrF = CompletableFuture.supplyAsync(
                    () -> drainTail(process.getErrorStream(), STDERR_RING_BYTES), drainers);

            boolean finished = process.waitFor(timeoutMinutes, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                // give the drainers a brief moment to flush what they already have
                stdoutF.completeOnTimeout("", 2, TimeUnit.SECONDS);
                stderrF.completeOnTimeout("", 2, TimeUnit.SECONDS);
                String tail = safeGet(stderrF);
                throw new TimeoutException(
                        "Python script " + scriptName + " exceeded " + timeoutMinutes
                                + " min wall-clock budget; killed. stderr tail: " + tail);
            }

            String stdout = stdoutF.get(30, TimeUnit.SECONDS);
            String stderr = stderrF.get(30, TimeUnit.SECONDS);
            int exitCode = process.exitValue();

            if (!stderr.isBlank()) {
                log.info("Python stderr [{}]: {}", scriptName, stderr.trim());
            }

            if (exitCode != 0) {
                throw new RuntimeException(
                        "Python script " + scriptName + " failed (exit=" + exitCode + "): " + stderr);
            }

            JsonNode result = objectMapper.readTree(stdout);

            if (!result.path("success").asBoolean(false)) {
                String error = result.path("error").asText("Unknown error");
                throw new RuntimeException("Python script " + scriptName + " error: " + error);
            }

            return result.path("data");
        } finally {
            drainers.shutdownNow();
        }
    }

    private static String drainFull(InputStream in) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } catch (Exception e) {
            // swallow — caller detects via exitCode / missing JSON
        }
        return sb.toString();
    }

    /** Keep only the last {@code maxBytes} bytes of the stream. */
    private static String drainTail(InputStream in, int maxBytes) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line).append('\n');
                if (sb.length() > maxBytes * 2) {
                    // Trim: drop everything before the last half of maxBytes so we
                    // always retain at least maxBytes of recent output, amortizing
                    // the substring cost.
                    sb.delete(0, sb.length() - maxBytes);
                }
            }
        } catch (Exception e) {
            // swallow
        }
        if (sb.length() > maxBytes) {
            return "…[truncated]…\n" + sb.substring(sb.length() - maxBytes);
        }
        return sb.toString();
    }

    private static String safeGet(CompletableFuture<String> f) {
        try {
            return f.getNow("");
        } catch (Exception e) {
            return "";
        }
    }

    private String resolveScriptPath(String scriptName) {
        return appConfig.getPipelineDir() + "/" + scriptName;
    }
}
