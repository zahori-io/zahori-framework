package io.zahori.framework.evidences;

/*-
 * #%L
 * zahori-framework
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2021 PANEL SISTEMAS INFORMATICOS,S.L
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
import io.zahori.framework.files.doc.Word;
import io.zahori.framework.files.log.LogFile;
import io.zahori.framework.files.properties.ZahoriProperties;
import io.zahori.framework.i18n.Messages;
import io.zahori.framework.utils.video.AndroidScreenRecorder;
import io.zahori.framework.utils.video.CDPScreenRecorder;
import io.zahori.framework.utils.video.EnterpriseScreenRecorder;
import io.zahori.framework.utils.video.IOSScreenRecorder;
import io.zahori.framework.utils.video.SelenoidVideoRecorder;
import io.zahori.framework.utils.video.VideoRecorder;
import io.zahori.model.Status;
import io.zahori.model.Step;
import io.zahori.model.process.CaseExecution;
import io.zahori.model.process.ProcessRegistration;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;

public class Evidences {

    public enum ZahoriLogLevel {
        DEBUG, INFO, WARN, ERROR
    }

    private ZahoriLogLevel logLevel;
    public static final ZahoriLogLevel LOG_DEFAULT_LEVEL = ZahoriLogLevel.INFO;

    // Thread-safe: DateTimeFormatter is immutable and thread-safe (unlike SimpleDateFormat)
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final Logger LOG = LogManager.getLogger(Evidences.class);

    private static final String UNDERSCORE = "_";

    private static final String[] BOLD_LIST = new String[]{"[STEP ", "[TEST "};
    private static final String RED = "FF0000";

    // Cached WebP support check (immutable after class loading)
    // scanForPlugins() ensures TwelveMonkeys SPI registration is triggered
    private static final boolean WEBP_SUPPORTED;
    static {
        ImageIO.scanForPlugins();
        WEBP_SUPPORTED = ImageIO.getImageWritersByFormatName("webp").hasNext();
    }

    private final String evidenceFileNamePattern;
    private final String path;
    private final ZahoriProperties zahoriProperties;

    // Thread-safe collections for concurrent access
    // CopyOnWriteArrayList: optimal for read-heavy, write-rare scenarios (evidence file names)
    private final List<String> docFileNames = new CopyOnWriteArrayList<>();
    private final List<String> logFileNames = new CopyOnWriteArrayList<>();

    // Synchronized maps for document/log file access
    // Using Collections.synchronizedMap for compatibility; iteration still needs external sync
    private final Map<String, Word> docs = Collections.synchronizedMap(new LinkedHashMap<>());
    private final Map<String, LogFile> logFiles = Collections.synchronizedMap(new LinkedHashMap<>());

    // Video recording with explicit locking for start/stop coordination
    private final ReadWriteLock videoLock = new ReentrantReadWriteLock();
    private volatile EnterpriseScreenRecorder video;
    private final String videoFileName;

    // Har Log (immutable after construction)
    private final String harLogFileName;

    // Screenshots: thread-safe list, may be null if disabled
    private final List<String> screenshots;

    // i18n messages (should be thread-safe or immutable)
    private final Messages messages;

    private final boolean remoteBrowser;

    public Evidences(CaseExecution caseExecution, ZahoriProperties zahoriProperties, Messages messages, String platform, String browser, String resolution,
            String testId, String templatePath, boolean remoteBrowser, ProcessRegistration processRegistration) {
        this.remoteBrowser = remoteBrowser;
        this.zahoriProperties = zahoriProperties;
        this.messages = messages;
        evidenceFileNamePattern = caseExecution.getCas().getName() + UNDERSCORE + (!StringUtils.isBlank(platform) ? platform + UNDERSCORE : "")
                + (!StringUtils.isBlank(browser) ? browser + UNDERSCORE : "") + testId;

        String processPath = "";
        if (processRegistration != null) {
            processPath = processRegistration.getClientId() + File.separator + processRegistration.getTeamId() + File.separator + processRegistration.getName()
                    + File.separator;
        }
        path = zahoriProperties.getResultsDir() + processPath + caseExecution.getCas().getName() + File.separator
                + (!StringUtils.isBlank(platform) ? platform + File.separator : "") + (!StringUtils.isBlank(browser) ? browser + File.separator : "")
                + (!StringUtils.isBlank(resolution) ? resolution + File.separator : "") + testId + File.separator;
        prepareDirectory(new File(path));

        caseExecution.setEvidencesPath(path);

        // Get languages defined in zahori.properties
        String[] langs = messages.getLanguages();

        // Log files (based on languages)
        if (zahoriProperties.isLogFileGenerationEnabled()) {
            for (String lang : langs) {
                String logFile = evidenceFileNamePattern + "_" + lang + ".log";
                logFileNames.add(logFile);
                logFiles.put(lang, new LogFile(path + logFile));
            }
        }

        // Doc files (based on languages)
        if (zahoriProperties.isDocGenerationEnabled()) {
            for (String lang : langs) {
                String docFile = evidenceFileNamePattern + "_" + lang + ".docx";
                docFileNames.add(docFile);
                if ((templatePath == null) || templatePath.isEmpty()) {
                    docs.put(lang, new Word(path, docFile, "Test: " + caseExecution.getCas().getName()));
                } else {
                    docs.put(lang, new Word(path, docFile, "Test: " + caseExecution.getCas().getName(), templatePath));
                }
            }
        }

        // Video - Strategy selection based on platform and remote mode
        // For remote=YES (Selenoid): video is recorded by Selenoid server (ZAH-158)
        // For remote=NO (local): use CDP for Chrome/Edge, Monte for others
        videoFileName = evidenceFileNamePattern + (remoteBrowser ? ".mp4" : ".avi");
        if (zahoriProperties.isVideoGenerationEnabledWhenPassed() || zahoriProperties.isVideoGenerationEnabledWhenFailed()) {
            try {
                video = createVideoRecorder(platform, browser, remoteBrowser, path, zahoriProperties);
            } catch (Exception e) {
                throw new RuntimeException("Error creating new video instance: " + e.getMessage());
            }
        }

        // Har Log
        harLogFileName = evidenceFileNamePattern + ".har";

        // Screenshots: use thread-safe list if enabled
        if (zahoriProperties.isScreenshotsGenerationEnabled()) {
            screenshots = new CopyOnWriteArrayList<>();
        } else {
            screenshots = null;
        }

        ZahoriLogLevel configuredLogLevel = zahoriProperties.getLogLevel();
        logLevel = configuredLogLevel == null ? LOG_DEFAULT_LEVEL : configuredLogLevel;
    }

    public void insertStep(List<Step> steps) {
        consoleSteps(steps);
        insertStepsInLogFile(steps);
        insertStepsInDoc(steps);
    }

    public void console(String text) {
        if (StringUtils.isBlank(text)) {
            LOG.info("");
            return;
        }
        // Print \n using LOG.info("")
        text = StringUtils.replace(text, "\n", " \n");
        String[] lines = StringUtils.split(text, "\n");
        for (String line : lines) {
            LOG.info(line);
        }
    }

    public void console(ZahoriLogLevel level, String text) {
        if (level.compareTo(logLevel) >= 0) {
            try {
                Method method = LOG.getClass().getMethod(StringUtils.lowerCase(String.valueOf(level)), String.class);
                if (StringUtils.isBlank(text)) {
                    method.invoke("");
                }
                // Print \n using LOG.info("")
                text = StringUtils.replace(text, "\n", " \n");
                String[] lines = StringUtils.split(text, "\n");
                for (String line : lines) {
                    method.invoke(line);
                }
            } catch (SecurityException | NoSuchMethodException | IllegalArgumentException | IllegalAccessException | InvocationTargetException e) {
                console(text);
            }
        }
    }

    public void insertTextInLogFile(String text, String... textArgs) {
        insertTextInLogFile(logLevel, text, textArgs);
    }

    public void insertTextInLogFile(ZahoriLogLevel level, String text, String... textArgs) {
        if (level.compareTo(logLevel) >= 0) {
            String timestamp = LocalDateTime.now().format(DATE_FORMATTER);
            // Synchronized iteration over logFiles map
            synchronized (logFiles) {
                for (Map.Entry<String, LogFile> log : logFiles.entrySet()) {
                    log.getValue().write(timestamp + StringUtils.SPACE + StringUtils.upperCase(String.valueOf(level)) + StringUtils.SPACE
                            + StringUtils.SPACE + messages.getMessage(log.getKey(), text, textArgs));
                }
            }
        }
    }

    public void insertTextInDocs(String text, String... textArgs) {
        synchronized (docs) {
            for (Map.Entry<String, Word> doc : docs.entrySet()) {
                doc.getValue().insertarTexto(messages.getMessage(doc.getKey(), text, textArgs));
            }
        }
    }

    public void insertFailedTextInDocs(String text, String... textArgs) {
        synchronized (docs) {
            for (Map.Entry<String, Word> doc : docs.entrySet()) {
                if (hasBoldText(text)) {
                    doc.getValue().insertarTextoColorNegrita(messages.getMessage(doc.getKey(), text, textArgs), RED);
                } else {
                    doc.getValue().insertarTextoColor(messages.getMessage(doc.getKey(), text, textArgs), RED);
                }
            }
        }
    }

    public void insertSuccessTextInDocs(String text, String... textArgs) {
        synchronized (docs) {
            for (Map.Entry<String, Word> doc : docs.entrySet()) {
                if (hasBoldText(text)) {
                    doc.getValue().insertarTextoNegrita(messages.getMessage(doc.getKey(), text, textArgs));
                } else {
                    doc.getValue().insertarTexto(messages.getMessage(doc.getKey(), text, textArgs));
                }
            }
        }
    }

    public void insertImageInDoc(String image, String text, String... textArgs) {
        if (!StringUtils.isBlank(image)) {
            synchronized (docs) {
                for (Map.Entry<String, Word> doc : docs.entrySet()) {
                    doc.getValue().insertarImagen(new File(image), messages.getMessage(doc.getKey(), text, textArgs));
                }
            }
        } else {
            insertTextInDocs(text);
        }
    }

    public String getEvidencesPath() {
        return path;
    }

    private void consoleSteps(List<Step> steps) {
        LOG.info("");
        LOG.info(getStepPrefix(steps));
        for (Step step : steps) {
            console(messages.getMessageInFirstLanguage(step.getDescription(), step.getDescriptionArgs()));
        }
    }

    private void insertStepsInLogFile(List<Step> steps) {
        String stepPrefix = getStepPrefix(steps);
        synchronized (logFiles) {
            for (Map.Entry<String, LogFile> log : logFiles.entrySet()) {
                StringBuilder stepText = new StringBuilder();
                stepText.append("\n");
                stepText.append(stepPrefix).append("\n");
                for (Step step : steps) {
                    stepText.append(messages.getMessage(log.getKey(), step.getDescription(), step.getDescriptionArgs())).append("\n");
                }
                log.getValue().write(stepText.toString());
            }
        }
    }

    private void insertStepsInDoc(List<Step> steps) {
        String prefixText = getStepPrefix(steps);
        synchronized (docs) {
            for (Map.Entry<String, Word> doc : docs.entrySet()) {
                insertTextInDoc(doc, "\n", null);
                insertTextInDoc(doc, prefixText, steps.get(steps.size() - 1).getStatus());
                for (Step step : steps) {
                    String status = step.getStatus();
                    File image = (step.getAttachments() == null) || step.getAttachments().isEmpty() ? null : step.getAttachments().get(0);
                    if (image != null) {
                        switch (status) {
                            case Status.FAILED:
                                doc.getValue().insertarImagenColor(image, messages.getMessage(doc.getKey(), step.getDescription(), step.getDescriptionArgs()), RED);
                                break;
                            default:
                                doc.getValue().insertarImagen(image, messages.getMessage(doc.getKey(), step.getDescription(), step.getDescriptionArgs()));
                        }

                    } else {
                        insertTextInDoc(doc, messages.getMessage(doc.getKey(), step.getDescription(), step.getDescriptionArgs()), status);
                    }
                    insertTextInDoc(doc, "\n", null);
                }
            }
        }
    }

    private void insertTextInDoc(Map.Entry<String, Word> doc, String text, String status) {
        if (status == null) {
            if (hasBoldText(text)) {
                doc.getValue().insertarTextoNegrita(text);
            } else {
                doc.getValue().insertarTexto(text);
            }

        } else {
            switch (status) {
                case Status.FAILED:
                    if (hasBoldText(text)) {
                        doc.getValue().insertarTextoColorNegrita(text, RED);
                    } else {
                        doc.getValue().insertarTextoColor(text, RED);
                    }
                    break;
                default:
                    if (hasBoldText(text)) {
                        doc.getValue().insertarTextoNegrita(text);
                    } else {
                        doc.getValue().insertarTexto(text);
                    }

            }
        }

    }

    public String createScreenshot(int numPaso, int numSubPaso, WebDriver driver) {
        String screenshotFilePath = null;
        if (screenshots != null) {
            String format = getEffectiveScreenshotFormat();
            screenshotFilePath = path + "Step_" + numPaso + "_" + numSubPaso + "." + format;
            try {
                File screenShotFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
                saveScreenshot(screenShotFile, screenshotFilePath, format);
            } catch (Exception e) {
                String error = "Error creating screenshot: " + e.getMessage();
                console(ZahoriLogLevel.ERROR, error);
                insertTextInLogFile(ZahoriLogLevel.ERROR, error);
                insertFailedTextInDocs(error);
            }
        }
        return screenshotFilePath;
    }

    /**
     * Gets the effective screenshot format, falling back to jpg if webp is not available.
     */
    private String getEffectiveScreenshotFormat() {
        String format = zahoriProperties.getScreenshotFormat();
        if ("webp".equals(format) && !isWebPSupported()) {
            console(ZahoriLogLevel.WARN, "WebP format not available (TwelveMonkeys ImageIO not in classpath), falling back to JPG");
            return "jpg";
        }
        return format;
    }

    /**
     * Checks if WebP format is supported (TwelveMonkeys ImageIO plugin available).
     * Uses cached value for performance (checked once at class loading).
     */
    private boolean isWebPSupported() {
        return WEBP_SUPPORTED;
    }

    /**
     * Saves screenshot in the configured format with optimization.
     */
    private void saveScreenshot(File sourceFile, String outputPath, String format) throws IOException {
        BufferedImage sourceImage = ImageIO.read(sourceFile);

        // Apply scaling if configured
        float scaleFactor = zahoriProperties.getScreenshotScale();
        BufferedImage scaledImage = scaleImage(sourceImage, scaleFactor);

        switch (format) {
            case "png":
                savePng(scaledImage, outputPath);
                break;
            case "webp":
                saveWebP(scaledImage, outputPath);
                break;
            case "jpg":
            default:
                saveJpg(scaledImage, outputPath);
                break;
        }
    }

    /**
     * Saves image as optimized JPG with configurable quality.
     */
    private void saveJpg(BufferedImage image, String filePath) throws IOException {
        // Convert to RGB (JPG doesn't support alpha channel)
        BufferedImage rgbImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = rgbImage.createGraphics();
        g2d.drawImage(image, 0, 0, Color.WHITE, null);
        g2d.dispose();

        float quality = zahoriProperties.getScreenshotQuality();
        saveJpgWithQuality(rgbImage, filePath, quality);
    }

    /**
     * Saves image as PNG (lossless, larger file size).
     */
    private void savePng(BufferedImage image, String filePath) throws IOException {
        ImageIO.write(image, "png", new File(filePath));
    }

    /**
     * Saves image as WebP with configurable quality (requires TwelveMonkeys ImageIO).
     * WebP provides ~25-35% better compression than JPG at same quality.
     */
    private void saveWebP(BufferedImage image, String filePath) throws IOException {
        // Convert to RGB for better WebP compression
        BufferedImage rgbImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = rgbImage.createGraphics();
        g2d.drawImage(image, 0, 0, Color.WHITE, null);
        g2d.dispose();

        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("webp");
        if (!writers.hasNext()) {
            throw new IOException("WebP format not supported - TwelveMonkeys ImageIO not available");
        }

        ImageWriter writer = writers.next();
        ImageWriteParam param = writer.getDefaultWriteParam();

        // Configure WebP compression if supported
        if (param.canWriteCompressed()) {
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(zahoriProperties.getScreenshotQuality());
        }

        File outputFile = new File(filePath);
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(outputFile)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(rgbImage, null, null), param);
        } finally {
            writer.dispose();
        }
    }

    /**
     * Scales an image by the given factor.
     * Uses bilinear interpolation for smooth results.
     *
     * @param original the original image
     * @param scaleFactor scale factor (1.0 = original size, 0.5 = half size)
     * @return scaled image, or original if scaleFactor is 1.0
     */
    private BufferedImage scaleImage(BufferedImage original, float scaleFactor) {
        if (scaleFactor >= 1.0f) {
            return original;
        }

        int newWidth = Math.max(1, (int) (original.getWidth() * scaleFactor));
        int newHeight = Math.max(1, (int) (original.getHeight() * scaleFactor));

        BufferedImage scaled = new BufferedImage(newWidth, newHeight, original.getType());
        Graphics2D g2d = scaled.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.drawImage(original, 0, 0, newWidth, newHeight, null);
        g2d.dispose();

        return scaled;
    }

    /**
     * Saves a BufferedImage as JPEG with configurable compression quality.
     *
     * @param image the image to save
     * @param filePath output file path
     * @param quality compression quality (0.0-1.0, where 1.0 is highest quality)
     */
    private void saveJpgWithQuality(BufferedImage image, String filePath, float quality) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality);

        File outputFile = new File(filePath);
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(outputFile)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }

    public void startVideo() {
        videoLock.writeLock().lock();
        try {
            if (video != null) {
                video.start();
            }
        } catch (Exception e) {
            console(ZahoriLogLevel.ERROR, "Error starting video: " + e.getMessage());
            insertTextInLogFile(ZahoriLogLevel.ERROR, "Error starting video: " + e.getMessage());
        } finally {
            videoLock.writeLock().unlock();
        }
    }

    public void stopVideo(boolean isTestPassed) {
        videoLock.writeLock().lock();
        try {
            if (video != null) {
                video.stop();

                if ((isTestPassed && zahoriProperties.isVideoGenerationEnabledWhenPassed())
                        || (!isTestPassed && zahoriProperties.isVideoGenerationEnabledWhenFailed())) {
                    video.saveAs(evidenceFileNamePattern);
                } else {
                    video.deleteVideoTemp();
                }
            }
        } catch (Exception e) {
            console(ZahoriLogLevel.ERROR, "Error stopping video: " + e.getMessage());
            insertTextInLogFile(ZahoriLogLevel.ERROR, "Error stopping video: " + e.getMessage());
        } finally {
            videoLock.writeLock().unlock();
        }
    }

    public String getEvidenceFileNamePattern() {
        return evidenceFileNamePattern;
    }

    public String getPath() {
        return path;
    }

    /**
     * Returns an unmodifiable view of the document file names.
     * Thread-safe: CopyOnWriteArrayList provides snapshot iteration.
     */
    public List<String> getDocFileNames() {
        return Collections.unmodifiableList(docFileNames);
    }

    /**
     * Returns an unmodifiable view of the log file names.
     * Thread-safe: CopyOnWriteArrayList provides snapshot iteration.
     */
    public List<String> getLogFileNames() {
        return Collections.unmodifiableList(logFileNames);
    }

    public String getHarLogFileName() {
        return harLogFileName;
    }

    public String getVideoFileName() {
        return videoFileName;
    }

    /**
     * Returns an unmodifiable view of the screenshots list, or null if disabled.
     * Thread-safe: CopyOnWriteArrayList provides snapshot iteration.
     */
    public List<String> getScreenshots() {
        return screenshots != null ? Collections.unmodifiableList(screenshots) : null;
    }

    private String getStepPrefix(List<Step> steps) {
        Step step = steps.get(steps.size() - 1);
        return "[STEP " + step.getName() + " - " + step.getStatus() + " at " + LocalDateTime.now().format(DATE_FORMATTER) + "]";
    }

    /**
     * Prepares the evidence directory, creating it if necessary.
     * Uses atomic check-and-create to avoid race conditions.
     */
    private void prepareDirectory(File dir) {
        if (dir == null) {
            return;
        }
        // mkdirs() is atomic and returns false if directory already exists
        // This avoids the check-then-act race condition
        if (dir.mkdirs()) {
            // Directory was created, set permissions
            dir.setWritable(true, true);
            dir.setReadable(true);
        }
        // If mkdirs() returned false, directory already exists (no action needed)
    }

    private boolean hasBoldText(String text) {
        boolean found = false;
        int i = 0;
        while (!found && (i < BOLD_LIST.length)) {
            found = StringUtils.startsWith(text, BOLD_LIST[i]);
            i++;
        }

        return found;
    }

    // ==================== Video Recorder Factory ====================

    /**
     * Creates the appropriate video recorder based on execution context.
     *
     * <h2>Strategy (ZAH-158)</h2>
     * <ul>
     *   <li>Android: AndroidScreenRecorder (ADB screenrecord)</li>
     *   <li>Remote/Selenoid: SelenoidVideoRecorder (server-side recording)</li>
     *   <li>Local Chrome/Edge: CDPScreenRecorder (CDP Page.captureScreenshot)</li>
     *   <li>Local Firefox/Safari: VideoRecorder (Monte Media fallback)</li>
     * </ul>
     *
     * @param platform execution platform (Android, iOS, null for desktop)
     * @param browser browser name (chrome, firefox, edge, safari)
     * @param remoteBrowser true if running on remote grid (Selenoid)
     * @param evidencesPath path to save video
     * @param props zahori properties
     * @return appropriate EnterpriseScreenRecorder implementation
     */
    private EnterpriseScreenRecorder createVideoRecorder(
            String platform,
            String browser,
            boolean remoteBrowser,
            String evidencesPath,
            ZahoriProperties props) throws Exception {

        // 1. Android: Use ADB screenrecord
        if (StringUtils.equalsIgnoreCase("Android", platform)) {
            LOG.info("Video recorder: AndroidScreenRecorder (ADB)");
            return new AndroidScreenRecorder(evidencesPath, props.getExecutionTimeout());
        }

        // 2. iOS: Use Appium XCUITest screen recording
        // Note: IOSScreenRecorder needs IOSDriver, will be set later when driver is available
        if (StringUtils.equalsIgnoreCase("iOS", platform)) {
            LOG.info("Video recorder: IOSScreenRecorder (Appium XCUITest)");
            // Will be created when driver is available
            return null;
        }

        // 4. Remote (Selenoid/Grid): Video recorded server-side
        // Note: For Selenoid, video is created automatically when enableVideo=true
        // SelenoidVideoRecorder handles download after session ends
        if (remoteBrowser) {
            LOG.info("Video recorder: SelenoidVideoRecorder (server-side, ZAH-158)");
            // SelenoidVideoRecorder will be initialized with sessionId later
            // For now, return null - video download happens in stopVideo()
            return null;
        }

        // 5. Local Chrome/Edge: Use CDP (headless compatible)
        if (isCDPCompatibleBrowser(browser)) {
            LOG.info("Video recorder: CDPScreenRecorder (CDP Page.captureScreenshot)");
            // Note: CDPScreenRecorder needs WebDriver, will be set later
            return null; // Will be created when driver is available
        }

        // 6. Fallback: Monte Media (Firefox, Safari, others)
        LOG.info("Video recorder: VideoRecorder (Monte Media fallback)");
        return new VideoRecorder(new File(evidencesPath));
    }

    /**
     * Checks if browser supports CDP (Chrome DevTools Protocol).
     */
    private boolean isCDPCompatibleBrowser(String browser) {
        if (browser == null) {
            return false;
        }
        String lowerBrowser = browser.toLowerCase();
        return lowerBrowser.contains("chrome")
                || lowerBrowser.contains("chromium")
                || lowerBrowser.contains("edge");
    }

}
