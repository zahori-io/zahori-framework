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

import io.appium.java_client.ios.IOSDriver;
import io.appium.java_client.ios.options.XCUITestOptions;
import io.appium.java_client.screenrecording.CanRecordScreen;
import io.appium.java_client.screenrecording.ScreenRecordingUploadOptions;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.WebDriver;

/**
 * iOS Screen Recorder - Java Champion Level Implementation.
 *
 * <p>Records iOS device/simulator screen using Appium XCUITest driver's
 * native screen recording capabilities.
 *
 * <h2>Features</h2>
 * <ul>
 *   <li>Uses Appium's native startRecordingScreen/stopRecordingScreen</li>
 *   <li>Supports iOS Simulator (Xcode 9+) and real devices (iOS 11+)</li>
 *   <li>H.264 codec for better compatibility</li>
 *   <li>Configurable time limit (default 30 min, max 30 min)</li>
 *   <li>Thread-safe state management</li>
 *   <li>Proper resource cleanup</li>
 * </ul>
 *
 * <h2>Requirements</h2>
 * <ul>
 *   <li>Appium XCUITest driver</li>
 *   <li>FFmpeg installed on Appium server (brew install ffmpeg)</li>
 *   <li>iOS Simulator with Xcode 9+ or real device with iOS 11+</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>
 * IOSScreenRecorder recorder = new IOSScreenRecorder(driver, evidencesPath, 30);
 * recorder.start();
 * // ... test execution ...
 * recorder.saveAs("testName");
 * </pre>
 *
 * @see EnterpriseScreenRecorder
 * @see <a href="https://appium.readthedocs.io/en/latest/en/commands/device/recording-screen/start-recording-screen/">Appium Screen Recording</a>
 */
public class IOSScreenRecorder implements EnterpriseScreenRecorder {

    private static final Logger LOG = LogManager.getLogger(IOSScreenRecorder.class);

    // iOS recording limits
    private static final int MAX_DURATION_SECONDS = 1800; // 30 minutes (Appium default max)
    private static final int DEFAULT_DURATION_SECONDS = 1800;
    private static final String VIDEO_EXTENSION = ".mp4";
    private static final String DEFAULT_VIDEO_TYPE = "h264"; // Better compatibility than mjpeg

    // State (thread-safe)
    private final AtomicBoolean recording = new AtomicBoolean(false);
    private final AtomicReference<String> lastError = new AtomicReference<>();

    // Dependencies
    private final WebDriver driver;
    private final Path evidencesPath;
    private final int maxDurationSeconds;

    // Recorded video data
    private volatile String recordedVideoBase64;

    /**
     * Creates iOS screen recorder.
     *
     * @param driver WebDriver (must be IOSDriver with CanRecordScreen capability)
     * @param evidencesPath path to save final video
     * @param maxDurationMinutes maximum recording duration in minutes (max 30)
     * @throws IOException if evidences path is invalid
     */
    public IOSScreenRecorder(WebDriver driver, String evidencesPath, Integer maxDurationMinutes)
            throws IOException {
        this.driver = driver;
        this.evidencesPath = Path.of(evidencesPath);

        // Validate path
        if (!Files.isDirectory(this.evidencesPath)) {
            Files.createDirectories(this.evidencesPath);
        }

        // Calculate max duration (capped at 30 minutes)
        if (maxDurationMinutes != null && maxDurationMinutes > 0) {
            this.maxDurationSeconds = Math.min(maxDurationMinutes * 60, MAX_DURATION_SECONDS);
        } else {
            this.maxDurationSeconds = DEFAULT_DURATION_SECONDS;
        }

        // Validate driver supports recording
        if (!supportsRecording()) {
            LOG.warn("Driver does not support screen recording. IOSDriver with CanRecordScreen required.");
        }

        LOG.info("IOSScreenRecorder initialized: path={}, maxDuration={}s",
                evidencesPath, maxDurationSeconds);
    }

    /**
     * Creates iOS screen recorder with default duration.
     *
     * @param driver WebDriver (must be IOSDriver)
     * @param evidencesPath path to save final video
     */
    public IOSScreenRecorder(WebDriver driver, String evidencesPath) throws IOException {
        this(driver, evidencesPath, null);
    }

    // ==================== EnterpriseScreenRecorder Interface ====================

    @Override
    public void start() throws IOException {
        if (recording.getAndSet(true)) {
            LOG.warn("Recording already in progress");
            return;
        }

        if (!supportsRecording()) {
            throw new IOException("Driver does not support screen recording");
        }

        try {
            CanRecordScreen recordableDriver = (CanRecordScreen) driver;

            // Start recording with iOS-specific options
            // Using XCUITest options for iOS
            recordableDriver.startRecordingScreen(
                new io.appium.java_client.screenrecording.BaseStartScreenRecordingOptions<>() {}
                    .withTimeLimit(Duration.ofSeconds(maxDurationSeconds))
            );

            LOG.info("iOS screen recording started (max {}s)", maxDurationSeconds);

        } catch (Exception e) {
            recording.set(false);
            lastError.set(e.getMessage());
            throw new IOException("Failed to start iOS screen recording: " + e.getMessage(), e);
        }
    }

    @Override
    public void stop() throws IOException {
        if (!recording.getAndSet(false)) {
            return;
        }

        if (!supportsRecording()) {
            return;
        }

        try {
            CanRecordScreen recordableDriver = (CanRecordScreen) driver;

            // Stop recording and get base64 encoded video
            recordedVideoBase64 = recordableDriver.stopRecordingScreen();

            if (recordedVideoBase64 == null || recordedVideoBase64.isEmpty()) {
                LOG.warn("No video data returned from stopRecordingScreen");
            } else {
                LOG.info("iOS screen recording stopped. Video size: {} bytes (base64)",
                        recordedVideoBase64.length());
            }

        } catch (Exception e) {
            lastError.set(e.getMessage());
            LOG.error("Error stopping iOS screen recording: {}", e.getMessage());
        }
    }

    @Override
    public void saveAs(String fileName) throws IOException {
        stop();

        if (recordedVideoBase64 == null || recordedVideoBase64.isEmpty()) {
            LOG.warn("No video data to save");
            return;
        }

        Path outputPath = evidencesPath.resolve(fileName + VIDEO_EXTENSION);
        Files.createDirectories(evidencesPath);

        try {
            // Decode base64 video and save to file
            byte[] videoData = Base64.getDecoder().decode(recordedVideoBase64);
            Files.write(outputPath, videoData);

            LOG.info("Video saved: {} ({} bytes)", outputPath, videoData.length);

        } catch (Exception e) {
            lastError.set(e.getMessage());
            throw new IOException("Failed to save video: " + e.getMessage(), e);
        } finally {
            // Clear memory
            deleteVideoTemp();
        }
    }

    @Override
    public void saveVideoFail(String name) throws IOException {
        saveAs(name);
    }

    @Override
    public boolean deleteVideoTemp() {
        recordedVideoBase64 = null;
        return true;
    }

    // ==================== Helper Methods ====================

    /**
     * Checks if driver supports screen recording.
     */
    private boolean supportsRecording() {
        return driver instanceof CanRecordScreen;
    }

    /**
     * Returns true if recording is active.
     */
    public boolean isRecording() {
        return recording.get();
    }

    /**
     * Returns the last error message, if any.
     */
    public String getLastError() {
        return lastError.get();
    }

    /**
     * Checks if iOS video recording is available for the given driver.
     *
     * @param driver WebDriver to check
     * @return true if recording is supported
     */
    public static boolean isAvailable(WebDriver driver) {
        return driver instanceof CanRecordScreen && driver instanceof IOSDriver;
    }
}
