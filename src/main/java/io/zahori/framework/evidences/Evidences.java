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
import io.zahori.framework.driver.browserfactory.BrowserMobProxy;
import io.zahori.framework.files.doc.Word;
import io.zahori.framework.files.log.LogFile;
import io.zahori.framework.files.properties.ZahoriProperties;
import io.zahori.framework.i18n.Messages;
import io.zahori.framework.utils.video.AndroidScreenRecorder;
import io.zahori.framework.utils.video.EnterpriseScreenRecorder;
import io.zahori.framework.utils.video.VideoRecorder;
import io.zahori.model.Status;
import io.zahori.model.Step;
import io.zahori.model.process.CaseExecution;
import io.zahori.model.process.ProcessRegistration;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.*;
import javax.imageio.ImageIO;
import net.lightbody.bmp.core.har.Har;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;

public class Evidences {

    public enum ZahoriLogLevel {
        DEBUG, INFO, WARN, ERROR
    }

    private ZahoriLogLevel logLevel;
    public static final ZahoriLogLevel LOG_DEFAULT_LEVEL = ZahoriLogLevel.INFO;
    private static final String LOG_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";
    private SimpleDateFormat sdf;

    private static final Logger LOG = LogManager.getLogger(Evidences.class);

    private static final String UNDERSCORE = "_";

    private static final String[] BOLD_LIST = new String[]{"[STEP ", "[TEST "};
    private static final String RED = "FF0000";

    private String evidenceFileNamePattern;
    private String path;
    private ZahoriProperties zahoriProperties;

    // Doc
    private List<String> docFileNames = new ArrayList<>();
    private Map<String, Word> docs = new LinkedHashMap<>();

    // Log
    private List<String> logFileNames = new ArrayList<>();
    private Map<String, LogFile> logFiles = new LinkedHashMap<>();

    // Video
    private EnterpriseScreenRecorder video;
    private String videoFileName;

    // Har Log
    private String harLogFileName;

    // Screenshots
    private List<String> screenshots;

    // i18n messages
    private Messages messages;

    private BrowserMobProxy proxy;

    private boolean remoteBrowser;

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

        // Video
        videoFileName = evidenceFileNamePattern + ".avi";
        if (zahoriProperties.isVideoGenerationEnabledWhenPassed() || zahoriProperties.isVideoGenerationEnabledWhenFailed()) {
            try {
                if (StringUtils.equalsIgnoreCase("Android", platform)) {
                    video = new AndroidScreenRecorder(path, Integer.valueOf(zahoriProperties.getExecutionTimeout()));
                } else {
                    video = new VideoRecorder(new File(path));
                }

            } catch (Exception e) {
                throw new RuntimeException("Error creating new video instance: " + e.getMessage());
            }
        }

        // Har Log
        harLogFileName = evidenceFileNamePattern + ".har";

        // Screenshots
        if (zahoriProperties.isScreenshotsGenerationEnabled()) {
            screenshots = new ArrayList<>();
        }

        ZahoriLogLevel configuredLogLevel = zahoriProperties.getLogLevel();
        logLevel = configuredLogLevel == null ? LOG_DEFAULT_LEVEL : configuredLogLevel;
        sdf = new SimpleDateFormat(LOG_DATE_FORMAT);
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
            for (Map.Entry<String, LogFile> log : logFiles.entrySet()) {
                log.getValue().write(sdf.format(new Date()) + StringUtils.SPACE + StringUtils.upperCase(String.valueOf(level)) + StringUtils.SPACE
                        + StringUtils.SPACE + messages.getMessage(log.getKey(), text, textArgs));
            }
        }
    }

    public void insertTextInDocs(String text, String... textArgs) {
        for (Map.Entry<String, Word> doc : docs.entrySet()) {
            doc.getValue().insertarTexto(messages.getMessage(doc.getKey(), text, textArgs));
        }
    }

    public void insertFailedTextInDocs(String text, String... textArgs) {
        for (Map.Entry<String, Word> doc : docs.entrySet()) {
            if (hasBoldText(text)) {
                doc.getValue().insertarTextoColorNegrita(messages.getMessage(doc.getKey(), text, textArgs), RED);
            } else {
                doc.getValue().insertarTextoColor(messages.getMessage(doc.getKey(), text, textArgs), RED);
            }
        }
    }

    public void insertSuccessTextInDocs(String text, String... textArgs) {
        for (Map.Entry<String, Word> doc : docs.entrySet()) {
            if (hasBoldText(text)) {
                doc.getValue().insertarTextoNegrita(messages.getMessage(doc.getKey(), text, textArgs));
            } else {
                doc.getValue().insertarTexto(messages.getMessage(doc.getKey(), text, textArgs));
            }
        }
    }

    public void insertImageInDoc(String image, String text, String... textArgs) {
        if (!StringUtils.isBlank(image)) {
            for (Map.Entry<String, Word> doc : docs.entrySet()) {
                doc.getValue().insertarImagen(new File(image), messages.getMessage(doc.getKey(), text, textArgs));
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
        for (Map.Entry<String, LogFile> log : logFiles.entrySet()) {
            StringBuilder stepText = new StringBuilder();
            stepText.append("\n");
            stepText.append(getStepPrefix(steps)).append("\n");
            for (Step step : steps) {
                stepText.append(messages.getMessage(log.getKey(), step.getDescription(), step.getDescriptionArgs())).append("\n");
            }
            log.getValue().write(stepText.toString());
        }
    }

    private void insertStepsInDoc(List<Step> steps) {
        for (Map.Entry<String, Word> doc : docs.entrySet()) {
            insertTextInDoc(doc, "\n", null);
            String prefixText = getStepPrefix(steps);
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
        String screenshotJpgFilePath = null;
        if (screenshots != null) {
            screenshotJpgFilePath = path + "Step_" + numPaso + "_" + numSubPaso + ".jpg";
            try {
                File screenShotFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
                savePngFileAsJpg(screenShotFile, screenshotJpgFilePath);
            } catch (Exception e) {
                String error = "Error creating screenshot: " + e.getMessage();
                console(ZahoriLogLevel.ERROR, error);
                insertTextInLogFile(ZahoriLogLevel.ERROR, error);
                insertFailedTextInDocs(error);
            }
        }
        return screenshotJpgFilePath;
    }

    private void savePngFileAsJpg(File pngFile, String jpgFilePath) throws IOException {
        // Long start = System.currentTimeMillis();
        BufferedImage pngImage = ImageIO.read(pngFile);

        // jpg needs BufferedImage.TYPE_INT_RGB
        // png needs BufferedImage.TYPE_INT_ARGB
        // create a blank, RGB, same width and height
        BufferedImage jpgImage = new BufferedImage(pngImage.getWidth(), pngImage.getHeight(), BufferedImage.TYPE_INT_RGB);

        // draw a white background and puts the originalImage on it.
        jpgImage.createGraphics().drawImage(pngImage, 0, 0, Color.WHITE, null);

        // save image
        File jpgFile = new File(jpgFilePath);
        ImageIO.write(jpgImage, "jpg", jpgFile);
        // LOG.debug("Convert png to jpg --> time: " + (System.currentTimeMillis() - start));
    }

    public void startVideo() {
        if (video != null) {
            try {
                video.start();
            } catch (Exception e) {
                console(ZahoriLogLevel.ERROR, "Error starting video: " + e.getMessage());
                insertTextInLogFile(ZahoriLogLevel.ERROR, "Error starting video: " + e.getMessage());
            }
        }
    }

    public void stopVideo(boolean isTestPassed) {
        if (video != null) {
            try {
                video.stop();

                if ((isTestPassed && zahoriProperties.isVideoGenerationEnabledWhenPassed())
                        || (!isTestPassed && zahoriProperties.isVideoGenerationEnabledWhenFailed())) {
                    video.saveAs(evidenceFileNamePattern);
                } else {
                    video.deleteVideoTemp();
                }
            } catch (Exception e) {
                console(ZahoriLogLevel.ERROR, "Error stopping video: " + e.getMessage());
                insertTextInLogFile(ZahoriLogLevel.ERROR, "Error stopping video: " + e.getMessage());

            }
        }

    }

    public String getEvidenceFileNamePattern() {
        return evidenceFileNamePattern;
    }

    public String getPath() {
        return path;
    }

    public List<String> getDocFileNames() {
        return docFileNames;
    }

    public List<String> getLogFileNames() {
        return logFileNames;
    }

    public String getHarLogFileName() {
        return harLogFileName;
    }

    public String getVideoFileName() {
        return videoFileName;
    }

    public List<String> getScreenshots() {
        return screenshots;
    }

    public Proxy getBMP4HarLog() throws UnknownHostException {
        proxy = new BrowserMobProxy();

        // Request configuration
        if (zahoriProperties.isHarRequestBinaryContentEnabled()) {
            proxy.enableRequestBinaryContent();
        } else {
            proxy.disableRequestBinaryContent();
        }

        if (zahoriProperties.isHarRequestCookiesEnabled()) {
            proxy.enableRequestCookies();
        } else {
            proxy.disableRequestCookies();
        }

        if (zahoriProperties.isHarRequestHeadersEnabled()) {
            proxy.enableRequestHeaders();
        } else {
            proxy.disableRequestHeaders();
        }

        if (zahoriProperties.isHarRequestContentEnabled()) {
            proxy.enableRequestContent();
        } else {
            proxy.disableRequestContent();
        }

        // Response configuration
        if (zahoriProperties.isHarResponseBinaryContentEnabled()) {
            proxy.enableResponseBinaryContent();
        } else {
            proxy.disableResponseBinaryContent();
        }

        if (zahoriProperties.isHarResponseCookiesEnabled()) {
            proxy.enableResponseCookies();
        } else {
            proxy.disableResponseCookies();
        }

        if (zahoriProperties.isHarResponseHeadersEnabled()) {
            proxy.enableResponseHeaders();
        } else {
            proxy.disableResponseHeaders();
        }

        if (zahoriProperties.isHarResponseContentEnabled()) {
            proxy.enableResponseContent();
        } else {
            proxy.disableResponseContent();
        }

        Map<String, String> headers = zahoriProperties.getHeadersToBeAdded();
        if (!headers.isEmpty()) {
            proxy.addHeaders(headers);
        }

        Map<String, String> blackListReqs = zahoriProperties.getBlackListPatternsToBeAdded();
        if (!blackListReqs.isEmpty()) {
            proxy.addBlackLists(blackListReqs);
        }

        String proxyIP = zahoriProperties.getProxyIP();
        int proxyPort = zahoriProperties.getProxyPort();
        String proxyUser = zahoriProperties.getProxyUser();
        String proxyPassword = zahoriProperties.getProxyEncodedPassword();
        if (!StringUtils.isEmpty(proxyIP) && proxyPort > 0) {
            proxy.configureProxy(proxyIP, proxyPort, proxyUser, proxyPassword);
        }

        Proxy seleniumProxy = proxy.getConfiguredProxy();
        if (remoteBrowser) {
            String bmpIp = zahoriProperties.getBMPIP();
            bmpIp = StringUtils.isEmpty(bmpIp) ? InetAddress.getLocalHost().getHostAddress() : bmpIp;
            String proxyString = bmpIp + ":" + proxy.getProxyPort();
            console("Proxy string set to: " + proxyString);
            seleniumProxy.setHttpProxy(proxyString);
            seleniumProxy.setSslProxy(proxyString);
        }

        return seleniumProxy;
    }

    public void storeHarLog() throws IOException {
        String urlPattern = zahoriProperties.getHarFilterByUrlPattern();
        String requestMethods = zahoriProperties.getHarFilterByRequestMethod();
        Har harLog;
        if (StringUtils.isEmpty(urlPattern) && StringUtils.isEmpty(requestMethods)) {
            harLog = proxy.getUnfilteredHarLog();
        } else {
            if (!StringUtils.isEmpty(requestMethods)) {
                String[] methods = requestMethods.replaceAll(StringUtils.SPACE, StringUtils.EMPTY).split(",");
                List<String> methodsList = new ArrayList<>();
                Collections.addAll(methodsList, methods);
                harLog = StringUtils.isEmpty(urlPattern) ? proxy.getFilteredHarLogByRequestMethod(methodsList)
                        : proxy.getFilteredHarLogByUrlPatternAndRequestMethod(urlPattern, methodsList);
            } else {
                harLog = proxy.getFilteredHarLogByRequestUrl(urlPattern);
            }
        }

        harLog.writeTo(new File(getEvidencesPath() + getEvidenceFileNamePattern() + ".har"));
    }

    public BrowserMobProxy getBrowserMobProxy() {
        return proxy;
    }

    private String getStepPrefix(List<Step> steps) {
        Step step = steps.get(steps.size() - 1);
        return "[STEP " + step.getName() + " - " + step.getStatus() + " at " + sdf.format(new Date()) + "]";
    }

    private void prepareDirectory(File dir) {
        if (dir == null) {
            return;
        }
        /* Prepare Directory */
        if (!dir.exists()) {
            // create all non existing directories
            dir.mkdirs();

            // Permissions: rwx r-x r-x
            dir.setWritable(true, true);
            dir.setReadable(true);
            // dir.setExecutable(true);
        }
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

}
