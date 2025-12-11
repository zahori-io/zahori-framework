package io.zahori.framework.utils.video;

/*-
 * #%L
 * zahori-framework
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2021 - 2024 PANEL SISTEMAS INFORMATICOS,S.L
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Android Screen Recorder - Java Champion Level Implementation.
 *
 * <p>Records Android device screen using ADB screenrecord command.
 *
 * <h2>Features</h2>
 * <ul>
 *   <li>ProcessBuilder instead of Runtime.exec()</li>
 *   <li>CompletableFuture for async operations</li>
 *   <li>Proper process lifecycle management</li>
 *   <li>No busy-wait loops (uses Process.waitFor with timeout)</li>
 *   <li>FFmpeg for video concatenation</li>
 *   <li>Virtual Threads ready (JDK 21)</li>
 *   <li>Proper resource cleanup with try-with-resources</li>
 *   <li>Thread-safe state management</li>
 * </ul>
 *
 * <h2>Android screenrecord limitations</h2>
 * <ul>
 *   <li>Max 3 minutes per recording (Android limitation)</li>
 *   <li>Solution: Chain multiple recordings, merge with FFmpeg</li>
 * </ul>
 *
 * @see EnterpriseScreenRecorder
 */
public class AndroidScreenRecorder implements EnterpriseScreenRecorder {

    private static final Logger LOG = LogManager.getLogger(AndroidScreenRecorder.class);

    // Android screenrecord limits
    private static final int ANDROID_MAX_DURATION_SECONDS = 180; // 3 minutes
    private static final int DEFAULT_BIT_RATE = 6_000_000; // 6 Mbps
    private static final int PROCESS_TIMEOUT_SECONDS = 10;
    private static final String TEMP_PREFIX = "android_rec_";
    private static final String VIDEO_EXTENSION = ".mp4";

    // State (thread-safe)
    private final AtomicBoolean recording = new AtomicBoolean(false);
    private final AtomicInteger segmentCount = new AtomicInteger(0);
    private final AtomicReference<Process> currentProcess = new AtomicReference<>();
    private final AtomicReference<String> lastError = new AtomicReference<>();

    // Configuration (immutable)
    private final Path evidencesPath;
    private final int maxDurationSeconds;
    private final int maxSegments;

    // Temp files tracking
    private final List<Path> tempSegments = new ArrayList<>();
    private Path tempDir;

    // Executor for async operations
    private final ExecutorService executor;

    // Recording control
    private volatile CompletableFuture<Void> recordingFuture;

    /**
     * Creates Android screen recorder.
     *
     * @param evidencesPath path to save final video
     * @param maxDurationMinutes maximum recording duration in minutes
     */
    public AndroidScreenRecorder(String evidencesPath, Integer maxDurationMinutes) throws IOException {
        this.evidencesPath = Path.of(evidencesPath);

        // Validate path
        if (!Files.isDirectory(this.evidencesPath)) {
            throw new IOException("Evidence path does not exist or is not a directory: " + evidencesPath);
        }

        // Calculate max duration and segments
        int totalSeconds = (maxDurationMinutes != null && maxDurationMinutes > 0)
                ? maxDurationMinutes * 60
                : 30 * 60; // Default 30 minutes

        this.maxDurationSeconds = Math.min(totalSeconds, 60 * 60); // Max 1 hour
        this.maxSegments = (maxDurationSeconds / ANDROID_MAX_DURATION_SECONDS) + 1;

        // Create executor (Virtual Threads if available)
        this.executor = createExecutor();

        LOG.info("AndroidScreenRecorder initialized: path={}, maxDuration={}s, maxSegments={}",
                evidencesPath, maxDurationSeconds, maxSegments);
    }

    // ==================== EnterpriseScreenRecorder Interface ====================

    @Override
    public void start() throws IOException {
        if (recording.getAndSet(true)) {
            LOG.warn("Recording already in progress");
            return;
        }

        // Create temp directory for segments
        tempDir = Files.createTempDirectory(TEMP_PREFIX);
        tempSegments.clear();
        segmentCount.set(0);

        // Clean previous recordings on device
        cleanDeviceRecordings();

        // Start recording chain asynchronously
        recordingFuture = startRecordingChain();

        LOG.info("Android screen recording started");
    }

    @Override
    public void stop() throws IOException {
        if (!recording.getAndSet(false)) {
            return;
        }

        // Stop current recording process
        Process process = currentProcess.getAndSet(null);
        if (process != null && process.isAlive()) {
            // Send SIGINT to screenrecord (graceful stop)
            stopScreenrecordProcess();

            // Wait for process to finish
            try {
                boolean finished = process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    LOG.warn("Had to forcibly terminate screenrecord process");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }

        // Wait for recording future to complete
        if (recordingFuture != null) {
            try {
                recordingFuture.get(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (Exception e) {
                LOG.debug("Recording future completion: {}", e.getMessage());
            }
        }

        LOG.info("Android screen recording stopped. Segments: {}", segmentCount.get());
    }

    @Override
    public void saveAs(String fileName) throws IOException {
        stop();

        // Pull all segments from device
        pullSegmentsFromDevice();

        if (tempSegments.isEmpty()) {
            LOG.warn("No video segments to save");
            return;
        }

        Path outputPath = evidencesPath.resolve(fileName + VIDEO_EXTENSION);

        if (tempSegments.size() == 1) {
            // Single segment - just copy
            Files.copy(tempSegments.get(0), outputPath, StandardCopyOption.REPLACE_EXISTING);
        } else {
            // Multiple segments - concatenate with FFmpeg
            concatenateSegments(outputPath);
        }

        LOG.info("Video saved: {} ({} segments)", outputPath, tempSegments.size());

        // Cleanup
        deleteVideoTemp();
    }

    @Override
    public void saveVideoFail(String name) throws IOException {
        saveAs(name);
    }

    @Override
    public boolean deleteVideoTemp() {
        boolean success = true;

        // Delete temp segments
        for (Path segment : tempSegments) {
            try {
                Files.deleteIfExists(segment);
            } catch (IOException e) {
                LOG.debug("Error deleting temp segment: {}", segment);
                success = false;
            }
        }
        tempSegments.clear();

        // Delete temp directory
        if (tempDir != null) {
            try {
                Files.deleteIfExists(tempDir);
            } catch (IOException e) {
                LOG.debug("Error deleting temp directory: {}", tempDir);
                success = false;
            }
        }

        // Clean device
        try {
            cleanDeviceRecordings();
        } catch (IOException e) {
            LOG.debug("Error cleaning device recordings");
            success = false;
        }

        return success;
    }

    // ==================== Recording Logic ====================

    /**
     * Starts chain of recording segments asynchronously.
     */
    private CompletableFuture<Void> startRecordingChain() {
        return CompletableFuture.runAsync(() -> {
            int segment = 0;

            while (recording.get() && segment < maxSegments) {
                try {
                    recordSegment(segment);
                    segment++;
                    segmentCount.set(segment);
                } catch (Exception e) {
                    LOG.error("Error recording segment {}: {}", segment, e.getMessage());
                    lastError.set(e.getMessage());
                    break;
                }
            }
        }, executor);
    }

    /**
     * Records a single segment (max 3 minutes).
     */
    private void recordSegment(int segmentIndex) throws IOException, InterruptedException {
        String devicePath = "/sdcard/" + TEMP_PREFIX + segmentIndex + VIDEO_EXTENSION;

        ProcessBuilder pb = new ProcessBuilder(
                "adb", "shell",
                "screenrecord",
                "--bit-rate", String.valueOf(DEFAULT_BIT_RATE),
                "--time-limit", String.valueOf(ANDROID_MAX_DURATION_SECONDS),
                devicePath
        );

        pb.redirectErrorStream(true);

        LOG.debug("Starting segment {}: {}", segmentIndex, String.join(" ", pb.command()));

        Process process = pb.start();
        currentProcess.set(process);

        // Wait for process (it will end after time-limit or when stopped)
        boolean finished = process.waitFor(ANDROID_MAX_DURATION_SECONDS + 10, TimeUnit.SECONDS);

        if (!finished && recording.get()) {
            // Process didn't finish but we're still recording - segment complete
            process.destroyForcibly();
        }

        currentProcess.set(null);
    }

    /**
     * Stops screenrecord process gracefully via ADB.
     */
    private void stopScreenrecordProcess() {
        try {
            // Kill screenrecord process on device
            ProcessBuilder pb = new ProcessBuilder(
                    "adb", "shell", "pkill", "-SIGINT", "screenrecord"
            );
            Process p = pb.start();
            p.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            LOG.debug("Error sending SIGINT to screenrecord: {}", e.getMessage());
        }
    }

    /**
     * Pulls recorded segments from device to local temp directory.
     */
    private void pullSegmentsFromDevice() throws IOException {
        for (int i = 0; i <= segmentCount.get(); i++) {
            String devicePath = "/sdcard/" + TEMP_PREFIX + i + VIDEO_EXTENSION;
            Path localPath = tempDir.resolve(TEMP_PREFIX + i + VIDEO_EXTENSION);

            try {
                ProcessBuilder pb = new ProcessBuilder(
                        "adb", "pull", devicePath, localPath.toString()
                );
                pb.redirectErrorStream(true);

                Process process = pb.start();
                String output = readProcessOutput(process);
                boolean success = process.waitFor(30, TimeUnit.SECONDS);

                if (success && process.exitValue() == 0 && Files.exists(localPath)) {
                    tempSegments.add(localPath);
                    LOG.debug("Pulled segment {}: {}", i, localPath);
                } else if (!output.contains("does not exist")) {
                    LOG.debug("Failed to pull segment {}: {}", i, output);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while pulling segment " + i, e);
            }
        }
    }

    /**
     * Concatenates video segments using FFmpeg.
     */
    private void concatenateSegments(Path outputPath) throws IOException {
        if (!isFFmpegAvailable()) {
            LOG.warn("FFmpeg not available, using first segment only");
            if (!tempSegments.isEmpty()) {
                Files.copy(tempSegments.get(0), outputPath, StandardCopyOption.REPLACE_EXISTING);
            }
            return;
        }

        // Create concat file for FFmpeg
        Path concatFile = tempDir.resolve("concat.txt");
        List<String> concatLines = tempSegments.stream()
                .map(p -> "file '" + p.toAbsolutePath() + "'")
                .collect(Collectors.toList());
        Files.write(concatFile, concatLines);

        // Run FFmpeg concat
        ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg", "-y",
                "-f", "concat",
                "-safe", "0",
                "-i", concatFile.toString(),
                "-c", "copy",
                outputPath.toString()
        );
        pb.redirectErrorStream(true);

        try {
            Process process = pb.start();
            boolean finished = process.waitFor(60, TimeUnit.SECONDS);

            if (!finished || process.exitValue() != 0) {
                String output = readProcessOutput(process);
                LOG.warn("FFmpeg concat failed: {}", output);
                // Fallback: copy first segment
                if (!tempSegments.isEmpty()) {
                    Files.copy(tempSegments.get(0), outputPath, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("FFmpeg concat interrupted", e);
        }
    }

    /**
     * Cleans previous recordings from device.
     */
    private void cleanDeviceRecordings() throws IOException {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "adb", "shell", "rm", "-f", "/sdcard/" + TEMP_PREFIX + "*" + VIDEO_EXTENSION
            );
            Process process = pb.start();
            process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ==================== Helper Methods ====================

    /**
     * Checks if FFmpeg is available.
     */
    private boolean isFFmpegAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder("ffmpeg", "-version");
            Process process = pb.start();
            return process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Reads process output.
     */
    private String readProcessOutput(Process process) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * Creates executor - Virtual Threads for JDK 21+.
     */
    private ExecutorService createExecutor() {
        try {
            return (ExecutorService) Executors.class
                    .getMethod("newVirtualThreadPerTaskExecutor")
                    .invoke(null);
        } catch (Exception e) {
            return Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "android-recorder");
                t.setDaemon(true);
                return t;
            });
        }
    }

    /**
     * Returns true if recording is active.
     */
    public boolean isRecording() {
        return recording.get();
    }

    /**
     * Returns segment count.
     */
    public int getSegmentCount() {
        return segmentCount.get();
    }

    /**
     * Returns last error message.
     */
    public String getLastError() {
        return lastError.get();
    }

    /**
     * Shuts down executor.
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
