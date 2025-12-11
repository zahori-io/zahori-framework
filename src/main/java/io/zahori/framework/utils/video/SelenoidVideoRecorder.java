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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Selenoid Video Recorder - Downloads video from Selenoid/Moon after session ends.
 *
 * <p>Implements ZAH-158: Video recording via Aerokube-Selenoid.
 *
 * <h2>Features (Java Champion Level)</h2>
 * <ul>
 *   <li>Async download with CompletableFuture (non-blocking)</li>
 *   <li>HttpClient (Java 11+) - no external dependencies</li>
 *   <li>Retry with exponential backoff</li>
 *   <li>Virtual Thread ready (JDK 21)</li>
 *   <li>Proper resource cleanup</li>
 *   <li>Thread-safe state management</li>
 * </ul>
 *
 * <h2>Selenoid Video API</h2>
 * <pre>
 * GET    /video/{sessionId}.mp4  - Download video
 * DELETE /video/{sessionId}.mp4  - Delete video from server
 * </pre>
 *
 * <h2>Usage</h2>
 * <pre>
 * SelenoidVideoRecorder recorder = new SelenoidVideoRecorder(
 *     "http://selenoid:4444",
 *     sessionId,
 *     evidencesPath
 * );
 *
 * // Video is recorded by Selenoid automatically when enableVideo=true
 * // After driver.quit():
 * recorder.downloadAndSave("testName");
 * </pre>
 *
 * @see EnterpriseScreenRecorder
 */
public class SelenoidVideoRecorder implements EnterpriseScreenRecorder {

    private static final Logger LOG = LogManager.getLogger(SelenoidVideoRecorder.class);

    // Configuration
    private static final int CONNECT_TIMEOUT_SECONDS = 10;
    private static final int READ_TIMEOUT_SECONDS = 300; // 5 min for large videos
    private static final int MAX_RETRIES = 3;
    private static final int INITIAL_RETRY_DELAY_MS = 1000;
    private static final int VIDEO_READY_WAIT_MS = 2000; // Wait for Selenoid to finish encoding

    // State (thread-safe)
    private final AtomicBoolean recording = new AtomicBoolean(false);
    private final AtomicBoolean videoDownloaded = new AtomicBoolean(false);
    private final AtomicReference<Path> downloadedVideoPath = new AtomicReference<>();
    private final AtomicReference<String> lastError = new AtomicReference<>();

    // Immutable configuration
    private final String selenoidBaseUrl;
    private final String sessionId;
    private final Path evidencesPath;
    private final HttpClient httpClient;

    // Virtual Thread executor for JDK 21+ (fallback to cached pool for JDK 17)
    private final ExecutorService executor;

    /**
     * Creates a new Selenoid video recorder.
     *
     * @param selenoidUrl base URL of Selenoid (e.g., "http://selenoid:4444")
     * @param sessionId   Selenium session ID
     * @param evidencesPath path to save downloaded video
     */
    public SelenoidVideoRecorder(String selenoidUrl, String sessionId, String evidencesPath) {
        this.selenoidBaseUrl = normalizeUrl(selenoidUrl);
        this.sessionId = sessionId;
        this.evidencesPath = Path.of(evidencesPath);

        // Create HttpClient with timeouts
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        // Use Virtual Threads if available (JDK 21+), otherwise cached thread pool
        this.executor = createExecutor();

        LOG.info("SelenoidVideoRecorder initialized: url={}, sessionId={}", selenoidBaseUrl, sessionId);
    }

    /**
     * Creates executor - Virtual Threads for JDK 21+, cached pool for JDK 17.
     */
    private ExecutorService createExecutor() {
        try {
            // Try to use Virtual Threads (JDK 21+)
            return (ExecutorService) Executors.class
                    .getMethod("newVirtualThreadPerTaskExecutor")
                    .invoke(null);
        } catch (Exception e) {
            // Fallback for JDK 17
            LOG.debug("Virtual Threads not available, using cached thread pool");
            return Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "selenoid-video-downloader");
                t.setDaemon(true);
                return t;
            });
        }
    }

    // ==================== EnterpriseScreenRecorder Interface ====================

    /**
     * Marks recording as started.
     * Note: Actual recording is done by Selenoid when enableVideo=true capability is set.
     */
    @Override
    public void start() throws IOException {
        recording.set(true);
        LOG.info("Selenoid video recording started for session: {}", sessionId);
    }

    /**
     * Marks recording as stopped.
     * Note: Selenoid stops recording automatically when session ends.
     */
    @Override
    public void stop() throws IOException {
        recording.set(false);
        LOG.info("Selenoid video recording stopped for session: {}", sessionId);
    }

    /**
     * Downloads video from Selenoid and saves with given filename.
     *
     * @param fileName name for the video file (without extension)
     */
    @Override
    public void saveAs(String fileName) throws IOException {
        downloadAndSave(fileName, true);
    }

    /**
     * Downloads video for failed test.
     */
    @Override
    public void saveVideoFail(String name) throws IOException {
        saveAs(name);
    }

    /**
     * Deletes temporary video from Selenoid server.
     */
    @Override
    public boolean deleteVideoTemp() {
        return deleteRemoteVideo();
    }

    // ==================== Core Download Logic ====================

    /**
     * Downloads video from Selenoid and saves to evidences path.
     *
     * @param fileName base name for the video file
     * @param deleteAfterDownload whether to delete video from Selenoid after download
     * @return true if download successful
     */
    public boolean downloadAndSave(String fileName, boolean deleteAfterDownload) {
        if (sessionId == null || sessionId.isBlank()) {
            LOG.warn("Cannot download video: sessionId is null or empty");
            return false;
        }

        // Wait for Selenoid to finish encoding the video
        waitForVideoReady();

        String videoUrl = buildVideoUrl();
        Path targetPath = evidencesPath.resolve(fileName + ".mp4");

        LOG.info("Downloading Selenoid video: {} -> {}", videoUrl, targetPath);

        try {
            // Ensure evidences directory exists
            Files.createDirectories(evidencesPath);

            // Download with retry
            boolean success = downloadWithRetry(videoUrl, targetPath);

            if (success) {
                videoDownloaded.set(true);
                downloadedVideoPath.set(targetPath);
                LOG.info("Video downloaded successfully: {} ({} bytes)",
                        targetPath, Files.size(targetPath));

                // Optionally delete from Selenoid server
                if (deleteAfterDownload) {
                    deleteRemoteVideo();
                }
            }

            return success;

        } catch (Exception e) {
            lastError.set(e.getMessage());
            LOG.error("Error downloading video from Selenoid: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Downloads video asynchronously (non-blocking).
     *
     * @param fileName base name for the video file
     * @return CompletableFuture that completes with the downloaded file path
     */
    public CompletableFuture<Path> downloadAsync(String fileName) {
        return CompletableFuture.supplyAsync(() -> {
            boolean success = downloadAndSave(fileName, true);
            if (success) {
                return downloadedVideoPath.get();
            }
            throw new RuntimeException("Video download failed: " + lastError.get());
        }, executor);
    }

    /**
     * Downloads with exponential backoff retry.
     */
    private boolean downloadWithRetry(String url, Path targetPath) throws IOException, InterruptedException {
        int retryDelay = INITIAL_RETRY_DELAY_MS;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(READ_TIMEOUT_SECONDS))
                        .GET()
                        .build();

                HttpResponse<InputStream> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofInputStream());

                int statusCode = response.statusCode();

                if (statusCode == 200) {
                    // Success - save to file
                    try (InputStream is = response.body()) {
                        Files.copy(is, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                    return true;

                } else if (statusCode == 404) {
                    // Video not found - might not be ready yet
                    LOG.warn("Video not found (attempt {}/{}): {}", attempt, MAX_RETRIES, url);

                } else {
                    LOG.warn("Unexpected response {} downloading video (attempt {}/{})",
                            statusCode, attempt, MAX_RETRIES);
                }

            } catch (Exception e) {
                LOG.warn("Download attempt {}/{} failed: {}", attempt, MAX_RETRIES, e.getMessage());
            }

            // Exponential backoff
            if (attempt < MAX_RETRIES) {
                LOG.debug("Retrying in {}ms...", retryDelay);
                Thread.sleep(retryDelay);
                retryDelay *= 2;
            }
        }

        return false;
    }

    /**
     * Deletes video from Selenoid server.
     */
    public boolean deleteRemoteVideo() {
        if (sessionId == null || sessionId.isBlank()) {
            return false;
        }

        String videoUrl = buildVideoUrl();
        LOG.debug("Deleting remote video: {}", videoUrl);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(videoUrl))
                    .timeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
                    .DELETE()
                    .build();

            HttpResponse<Void> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.discarding());

            boolean success = response.statusCode() == 200 || response.statusCode() == 204;
            if (success) {
                LOG.info("Remote video deleted: {}", videoUrl);
            } else {
                LOG.warn("Failed to delete remote video: {} (status {})",
                        videoUrl, response.statusCode());
            }
            return success;

        } catch (Exception e) {
            LOG.warn("Error deleting remote video: {}", e.getMessage());
            return false;
        }
    }

    // ==================== Helper Methods ====================

    /**
     * Waits for Selenoid to finish encoding the video.
     */
    private void waitForVideoReady() {
        try {
            LOG.debug("Waiting {}ms for video encoding...", VIDEO_READY_WAIT_MS);
            Thread.sleep(VIDEO_READY_WAIT_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Builds the video download URL.
     */
    private String buildVideoUrl() {
        return selenoidBaseUrl + "/video/" + sessionId + ".mp4";
    }

    /**
     * Normalizes URL removing trailing slashes.
     */
    private String normalizeUrl(String url) {
        if (url == null) {
            return "";
        }
        return url.replaceAll("/+$", "");
    }

    // ==================== Getters ====================

    /**
     * Returns true if video was successfully downloaded.
     */
    public boolean isVideoDownloaded() {
        return videoDownloaded.get();
    }

    /**
     * Returns path to downloaded video, or null if not downloaded.
     */
    public Path getDownloadedVideoPath() {
        return downloadedVideoPath.get();
    }

    /**
     * Returns the last error message, if any.
     */
    public String getLastError() {
        return lastError.get();
    }

    /**
     * Returns the session ID.
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * Shuts down the executor service.
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
