package io.zahori.framework.core;

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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.ios.IOSDriver;
import static io.zahori.framework.core.BaseProcess.DEFAULT_BIT_DEPTH;
import static io.zahori.framework.core.BaseProcess.DEFAULT_SCREEN_RESOLUTION;
import io.zahori.framework.driver.browserfactory.BrowserMobProxy;
import io.zahori.framework.driver.browserfactory.Browsers;
import io.zahori.framework.evidences.Evidences;
import io.zahori.framework.evidences.Evidences.ZahoriLogLevel;
import io.zahori.framework.exception.ZahoriException;
import io.zahori.framework.exception.ZahoriPassedException;
import io.zahori.framework.files.properties.ProjectProperties;
import io.zahori.framework.files.properties.SystemPropertiesUtils;
import io.zahori.framework.files.properties.ZahoriProperties;
import io.zahori.framework.i18n.Messages;
import io.zahori.framework.robot.UtilsRobot;
import io.zahori.framework.tms.TmsService;
import io.zahori.framework.utils.Notification;
import io.zahori.framework.utils.Pause;
import io.zahori.framework.utils.WebdriverUtils;
import io.zahori.model.Status;
import io.zahori.model.Step;
import io.zahori.model.process.CaseExecution;
import io.zahori.model.process.ProcessRegistration;
import java.io.File;
import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.WebDriver;

public class TestContext {

    public static final String JSON_REPORT = "testSteps.json";
    public static final String DATE_FORMAT = "yyyyMMdd-HHmmss";
    public static final String DATE_WEB_FORMAT = "yyyy-MM-dd HH:mm:ss";
    public String testId = new SimpleDateFormat(DATE_FORMAT).format(new Date());
    public String url;
    public boolean testPassed = true;
    public ZahoriException zahoriException;
    private List<Step> currentStep = new ArrayList<>();
    public List<List<Step>> testSteps = new ArrayList<>();
    private int testDuration = 0;
    private long testStartupTime;
    private long stepStartupTime;

    // TestNG parameters defined in xml file
    public String testCaseName;
    public WebDriver driver;
    public WebDriver hostDriver;
    public String platform;
    public String browserName;
    public String bits;
    public String version;
    public String resolution;
    public String remote;
    public String remoteUrl;
    public String appiumService;
    public int retries = 0;

    // Properties
    public ZahoriProperties zahoriProperties;
    public ProjectProperties projectProperties;

    // TEST EXECUTION TIMEOUTS
    public Integer timeoutFindElement;

    // Browser
    private Browser browser;

    // Evidences
    public Evidences evidences;

    // Attachments
    private List<String> attachments = new ArrayList<>();

    // TMS (Test Management Systems)
    private TmsService tmsService;
    public String caseExecutionId;

    // i18n messages
    // private Map<String, MessageReader> messages = new LinkedHashMap<String,
    // MessageReader>();
    private Messages messages;
    private String executionNotes;
    protected String failCause;
    private boolean retriesDisabled;
    private boolean updateTestResultDisabled;

    public CaseExecution caseExecution;
    public ProcessRegistration processRegistration;

    public TestContext(CaseExecution caseExecution, ProcessRegistration processRegistration) {
        this.caseExecution = caseExecution;
        this.processRegistration = processRegistration;

        testCaseName = caseExecution.getCas().getName();
        caseExecutionId = String.valueOf(caseExecution.getCaseExecutionId());
        platform = "LINUX"; // TODO
        bits = "32"; // TODO
        browserName = caseExecution.getBrowser().getBrowserName().toUpperCase();
        version = StringUtils.isBlank(caseExecution.getBrowser().getVersion()) ? caseExecution.getBrowser().getDefaultVersion()
                : caseExecution.getBrowser().getVersion();
        resolution = StringUtils.isBlank(caseExecution.getScreenResolution()) ? DEFAULT_SCREEN_RESOLUTION
                : caseExecution.getScreenResolution() + DEFAULT_BIT_DEPTH;
    }

    public void constructor() {

        // Load configuration.properties into system properties
        // For IE is mandatory to load property: webdriver.ie.driver.path
        SystemPropertiesUtils.loadSystemProperties();

        // Load properties files: zahorí and project specific
        zahoriProperties = new ZahoriProperties(caseExecution.getConfiguration());
        projectProperties = new ProjectProperties();

        // Read url from configuration
        url = caseExecution.getConfiguration().getEnvironmentUrl();

        // Initialize messages readers with languages defined in
        // zahori.properties
        messages = new Messages(zahoriProperties.getLanguages());

        // Prepare evidences from configuration.properties // TODO refactor
        evidences = new Evidences(caseExecution, zahoriProperties, messages, platform, browserName, resolution, testId,
                getProjectProperty("evidences.template.file.path"), StringUtils.equalsIgnoreCase(Browsers.REMOTE_YES, remote), processRegistration);

        // Timeout
        timeoutFindElement = (int) caseExecution.getConfiguration().getTimeout();

        // Create TMS object to update test results and upload evidences to
        // TestLink, ALM...;
        tmsService = new TmsService(zahoriProperties, evidences, messages, testId);

        // Log context info
        logTestInfo(evidences);
        logTmsInfo(evidences);

        // Driver and browser // TODO hacer factoría única para desktop, mobile,
        // host...
        if (StringUtils.equalsIgnoreCase(String.valueOf(Browsers.NULLBROWSER), browserName)) {
            browser = null;
        } else {
            browser = new Browser(this);
        }

        hostDriver = null;

        logInfo("Test context initialized!");
    }

    private void logTestInfo(Evidences evidences) {
        evidences.insertTextInDocs("zahori.testInfo.execution.date", testId);
        evidences.insertTextInDocs("zahori.testInfo.execution.platform", platform);
        evidences.insertTextInDocs("zahori.testInfo.execution.browser.name", browserName);
        evidences.insertTextInDocs("zahori.testInfo.execution.browser.version", version);
        evidences.insertTextInDocs("zahori.testInfo.execution.browser.resolution", resolution);
        evidences.insertTextInDocs("zahori.testInfo.execution.evidences.path", evidences.getPath());

        logInfo("zahori.testInfo.title");
        logInfo("- Case: " + testCaseName);
        logInfo("zahori.testInfo.execution.date", testId);
        logInfo("zahori.testInfo.execution.platform", platform);
        logInfo("zahori.testInfo.execution.browser.name", browserName);
        logInfo("zahori.testInfo.execution.browser.version", version);
        logInfo("zahori.testInfo.execution.browser.resolution", resolution);
        String txtEvidencesPathProperty = "zahori.testInfo.execution.evidences.path";
        String evidencesPath = getMessage(txtEvidencesPathProperty);
        if (!evidencesPath.isEmpty() && !evidencesPath.equals(txtEvidencesPathProperty)) {
            logInfo(txtEvidencesPathProperty, evidences.getPath());
        }
        if (StringUtils.isNotBlank(url)) {
            logInfo("- Url: " + url);
        }
    }

    private void logTmsInfo(Evidences evidences) {
        if (caseExecution.getConfiguration() == null || caseExecution.getConfiguration().getTms() == null) {
            return;
        }

        if (!caseExecution.getConfiguration().getTms().isUploadResults()) {
            return;
        }

        evidences.insertTextInDocs("- TMS-Name: {}", zahoriProperties.getTMS());
        evidences.insertTextInDocs("- TMS-Upload results: {}", String.valueOf(caseExecution.getConfiguration().getTms().isUploadResults()));
        evidences.insertTextInDocs("- TMS-Test Case Id: {}", caseExecution.getConfiguration().getTms().getTestCaseId());
        evidences.insertTextInDocs("- TMS-Test Execution Id: {}", caseExecution.getConfiguration().getTms().getTestExecutionId());
        evidences.insertTextInDocs("- TMS-Test Execution Summary: {}", caseExecution.getConfiguration().getTms().getTestExecutionSummary());
        evidences.insertTextInDocs("- TMS-Test Plan Id: {}", caseExecution.getConfiguration().getTms().getTestPlanId());

        logInfo("- TMS-Upload results: {}", String.valueOf(caseExecution.getConfiguration().getTms().isUploadResults()));
        logInfo("- TMS-Name: {}", zahoriProperties.getTMS());
        logInfo("- TMS-Test Case Id: {}", caseExecution.getConfiguration().getTms().getTestCaseId());
        logInfo("- TMS-Test Execution Id: {}", caseExecution.getConfiguration().getTms().getTestExecutionId());
        logInfo("- TMS-Test Execution Summary: {}", caseExecution.getConfiguration().getTms().getTestExecutionSummary());
        logInfo("- TMS-Test Plan Id: {}", caseExecution.getConfiguration().getTms().getTestPlanId());
    }

    public void passTest(String messageKey, String... messageArgs) {
        passTest(false, messageKey, messageArgs);
    }

    public void passTestWithScreenshot(String messageKey, String... messageArgs) {
        passTest(true, messageKey, messageArgs);
    }

    protected void passTest(boolean withScreenshot, String messageKey, String... messageArgs) {
        testPassed = true;
        String message = getMessage(messageKey, messageArgs);
        failCause = message;
        if (withScreenshot) {
            logStepPassedWithScreenshot(message);
        } else {
            logStepPassed(message);
        }
        throw new ZahoriPassedException(testCaseName, message);
    }

    public void failTest(String messageKey, String... messageArgs) {
        logStepFailed(messageKey, messageArgs);
    }

    public void failTestWithScreenshot(String messageKey, String... messageArgs) {
        logStepFailedWithScreenshot(messageKey, messageArgs);
    }

    protected void failTest(Exception e) {
        this.testPassed = false;
        if (e instanceof ZahoriException) {
            this.zahoriException = (ZahoriException) e;
        } else {
            this.zahoriException = new ZahoriException(testCaseName, e.getMessage());
            logStepWithScreenshot(Status.FAILED, getMessage(zahoriException.getMessageKey()) + getErrorLine(e));
            logError(ExceptionUtils.getStackTrace(e));
        }

        // throw a new exception in order to make the test fail
        throwZahoriException(zahoriException.getMessageKey(), zahoriException.getMessageArgs());
    }

    private String getErrorLine(Exception e) {
        String[] lines = StringUtils.split(ExceptionUtils.getStackTrace(e), "\n");
        if (lines == null || lines.length == 0) {
            return "";
        }

        List<String> linesList = Arrays.asList(lines);
        for (int i = 0; i < linesList.size(); i++) {
            if (i == 0) {
                continue;
            }
            if (!StringUtils.startsWith(linesList.get(i), "	at io.zahori.framework")) {
                return " " + linesList.get(i);
            }
        }
        return "";
    }

    private void throwZahoriException(String errorMessageKey, String... errorMessageArgs) {
        throw new ZahoriException(testCaseName, errorMessageKey, errorMessageArgs);
    }

    public void reportTestResult() {
        String testResult;
        if (testPassed) {
            testResult = "\n[TEST PASSED]: " + testCaseName;
            logInfo(testResult);
            evidences.insertSuccessTextInDocs(testResult);
        } else {
            testResult = "\n[TEST FAILED]: " + testCaseName;
            logInfo(testResult);
            evidences.insertFailedTextInDocs(testResult);

            logInfo(zahoriException.getMessageKey(), zahoriException.getMessageArgs());
            evidences.insertFailedTextInDocs(zahoriException.getMessageKey(), zahoriException.getMessageArgs());
        }
    }

    // Update test result on Test Link, ALM,...
    public void uploadResultsToTms() {
        if (!updateTestResultDisabled) {
            tmsService.updateTestResult(caseExecution, testPassed, testSteps, testDuration, browserName, platform);
        }
    }

    public void logDebug(String text, String... textArgs) {
        evidences.console(ZahoriLogLevel.DEBUG, getMessage(text, textArgs));
        evidences.insertTextInLogFile(ZahoriLogLevel.DEBUG, text, textArgs);
    }

    public void logInfo(String text, String... textArgs) {
        evidences.console(getMessage(text, textArgs));
        evidences.insertTextInLogFile(text, textArgs);
    }

    public void logWarn(String text, String... textArgs) {
        evidences.console(ZahoriLogLevel.WARN, getMessage(text, textArgs));
        evidences.insertTextInLogFile(ZahoriLogLevel.WARN, text, textArgs);
    }

    public void logError(String text, String... textArgs) {
        evidences.console(ZahoriLogLevel.ERROR, getMessage(text, textArgs));
        evidences.insertTextInLogFile(ZahoriLogLevel.ERROR, text, textArgs);
    }

    public void logStepPassed(String description, String... descriptionArgs) {
        logStep(Status.PASSED, description, descriptionArgs);
    }

    public void logStepPassedWithScreenshot(String description, String... descriptionArgs) {
        logStepWithScreenshot(Status.PASSED, description, descriptionArgs);
    }

    public void logStepFailed(String description, String... descriptionArgs) {
        failCause = description;
        Step step = logStep(Status.FAILED, description, descriptionArgs);
        throwZahoriException(step.getDescription(), step.getDescriptionArgs());
    }

    public void logStepFailedWithScreenshot(String description, String... descriptionArgs) {
        failCause = description;
        Step step = logStepWithScreenshot(Status.FAILED, description, descriptionArgs);
        throwZahoriException(step.getDescription(), step.getDescriptionArgs());
    }

    public void logPartialStep(String description, String... descriptionArgs) {
        logPartialStepSuccess(description, descriptionArgs);
    }

    public void logPartialStepSuccess(String description, String... descriptionArgs) {
        logPartialStep(Status.PASSED, description, descriptionArgs);
    }

    public void logPartialStepFailed(String description, String... descriptionArgs) {
        logPartialStep(Status.FAILED, description, descriptionArgs);
    }

    public void logPartialStepWithScreenshot(String description, String... descriptionArgs) {
        logPartialStepSuccessWithScreenshot(description, descriptionArgs);
    }

    public void logPartialStepSuccessWithScreenshot(String description, String... descriptionArgs) {
        logPartialStepWithScreenshot(Status.PASSED, description, descriptionArgs);
    }

    public void logPartialStepFailedWithScreenshot(String description, String... descriptionArgs) {
        logPartialStepWithScreenshot(Status.FAILED, description, descriptionArgs);
    }

    private void logPartialStep(String status, String description, String... descriptionArgs) {
        Step step = new Step(null, (testSteps.size() + 1) + "_" + (currentStep.size() + 1), status, description);
        step.setDescriptionArgs(descriptionArgs);
        currentStep.add(step);
    }

    private void logPartialStepWithScreenshot(String status, String description, String... descriptionArgs) {
        Step step = new Step(null, (testSteps.size() + 1) + "_" + (currentStep.size() + 1), status, description);
        step.setDescriptionArgs(descriptionArgs);

        createScreenshot(step);
        currentStep.add(step);
    }

    private Step logStep(String status, String description, String... descriptionArgs) {
        Step step = new Step(null, "" + (testSteps.size() + 1), status, description);
        step.setDescriptionArgs(descriptionArgs);
        setStepDuration(step);

        currentStep.add(step);
        testSteps.add(currentStep);
        evidences.insertStep(currentStep);
        currentStep = new ArrayList<>();

        if (Status.FAILED.equals(status)) {
            setExecutionNotes(getMessage(description, descriptionArgs));
        }

        return step;
    }

    private Step logStepWithScreenshot(String status, String description, String... descriptionArgs) {
        Step step = new Step(null, String.valueOf(testSteps.size() + 1), status, description);
        step.setDescriptionArgs(descriptionArgs);
        setStepDuration(step);

        createScreenshot(step);
        currentStep.add(step);
        testSteps.add(currentStep);
        evidences.insertStep(currentStep);
        currentStep = new ArrayList<>();

        if (Status.FAILED.equals(status)) {
            setExecutionNotes(getMessage(description, descriptionArgs));
        }

        return step;
    }

    private void setStepDuration(Step step) {
        long stepDurationLong = System.currentTimeMillis() - stepStartupTime;
        int stepDuration = (Long.valueOf(TimeUnit.MILLISECONDS.toSeconds(stepDurationLong))).intValue();
        step.setDuration(stepDuration);

        // Reset duration for next step
        stepStartupTime = System.currentTimeMillis();
    }

    private void createScreenshot(Step step) {
        if (StringUtils.equalsIgnoreCase(String.valueOf(Browsers.NULLBROWSER), browserName)) {
            return;
        }

        String screenshot = evidences.createScreenshot(testSteps.size() + 1, currentStep.size() + 1, driver);
        if (screenshot != null) {
            step.addAttachment(new File(screenshot));
        }
    }

    public void startChronometer() {
        testStartupTime = System.currentTimeMillis();
        stepStartupTime = System.currentTimeMillis();
    }

    public void stopChronometer() {
        long testDurationLong = System.currentTimeMillis() - testStartupTime;
        testDuration = (Long.valueOf(TimeUnit.MILLISECONDS.toSeconds(testDurationLong))).intValue();
    }

    public void startVideo() {
        if (!StringUtils.equalsIgnoreCase(String.valueOf(Browsers.NULLBROWSER), browserName)) {
            evidences.startVideo();
        }
    }

    public void stopVideo() {
        if (!StringUtils.equalsIgnoreCase(String.valueOf(Browsers.NULLBROWSER), browserName)) {
            evidences.stopVideo(testPassed);
        }
    }

    public void moveMouseToUpperLeftCorner() {
        try {
            if ((driver == null) || StringUtils.equalsIgnoreCase(String.valueOf(Browsers.NULLBROWSER), browserName)) {
                // move mouse does not apply
                return;
            }

            UtilsRobot robot = new UtilsRobot();
            int verticalCorrector = WebdriverUtils.getBrowserBarHeight(driver);

            robot.moveMousePointer(0, verticalCorrector);
        } catch (Exception e) {
            this.logWarn("Error when moving mouse pointer to upper left corner");
        }

    }

    public ProjectProperties getProjectProperties() {
        return this.projectProperties;
    }

    public String getProjectProperty(String property) {
        return this.projectProperties.getProperty(property);
    }

    public String getProjectProperty(String property, String... propertyArgs) {
        return this.projectProperties.getProperty(property, propertyArgs);
    }

    public String getMessage(String messageKey, String... messageArgs) {
        return messages.getMessageInFirstLanguage(messageKey, messageArgs);
    }

    public String getHostEmulatorURL() {
        return this.zahoriProperties.getHostEmulatorURL();
    }

    public Browser getBrowser() {
        return browser;
    }

    public WebDriver getHostDriver() {
        return this.hostDriver;
    }

    public void setHostDriver(WebDriver driver) {
        this.hostDriver = driver;
    }

    public void showNotificationFailed(int msDuration, String description, String... descriptionArgs) {
        Notification info = showGenericNotification(msDuration, description, descriptionArgs);
        logStepFailedWithScreenshot(description, descriptionArgs);
        info.closeNotification();
    }

    public void showNotificationPassed(int msDuration, String description, String... descriptionArgs) {
        Notification info = showGenericNotification(msDuration, description, descriptionArgs);
        logStepPassedWithScreenshot(description, descriptionArgs);
        info.closeNotification();
    }

    public void showNotificationFailed(int msDuration, int pixelsWidth, int pixelsHeight, String description, String... descriptionArgs) {
        Notification info = showGenericNotification(msDuration, pixelsWidth, pixelsHeight, description, descriptionArgs);
        logStepFailedWithScreenshot(description, descriptionArgs);
        info.closeNotification();
    }

    public String getFirstLanguage() {
        return zahoriProperties.getLanguages()[0];
    }

    public int getExecutionTimeout() {
        return zahoriProperties.getExecutionTimeout();
    }

    public void showNotificationPassed(int msDuration, int pixelsWidth, int pixelsHeight, String description, String... descriptionArgs) {
        Notification info = showGenericNotification(msDuration, pixelsWidth, pixelsHeight, description, descriptionArgs);
        logStepPassedWithScreenshot(description, descriptionArgs);
        info.closeNotification();
    }

    public String getEvidencesFolder() {
        return evidences.getEvidencesPath();
    }

    private Notification showGenericNotification(int msDuration, int pixelsWidth, int pixelsHeight, String description, String... descriptionArgs) {
        Notification info = new Notification(getMessage(description, descriptionArgs), pixelsWidth, pixelsHeight);
        try {
            Thread.sleep(msDuration);
        } catch (InterruptedException e) {
            info.closeNotification();
            return info;
        }

        return info;
    }

    private Notification showGenericNotification(int msDuration, String description, String... descriptionArgs) {
        Notification info = new Notification(getMessage(description, descriptionArgs));
        try {
            Thread.sleep(msDuration);
        } catch (InterruptedException e) {
            info.closeNotification();
            return info;
        }

        return info;
    }

    public void setExecutionNotes(String notes) {
        executionNotes = StringUtils.isEmpty(executionNotes) ? notes : executionNotes + "\n" + notes;
        tmsService.setExecutionNotes(notes);
    }

    public void resetExecutionNotes() {
        executionNotes = StringUtils.EMPTY;
    }

    public String getExecutionNotes() {
        return executionNotes;
    }

    public String getFailCause() {
        return failCause;
    }

    public boolean isHarEnabled() {
        return zahoriProperties.isHarLogFileEnabled();
    }

    public Proxy getProxy4Driver() {
        try {
            return evidences.getBMP4HarLog();
        } catch (UnknownHostException e) {
            return null;
        }
    }

    public void storeHarLog() {
        try {
            evidences.storeHarLog();
        } catch (IOException e) {
            logInfo("zahori.testInfo.execution.harlog.error");
        }
    }

    public BrowserMobProxy getBrowserMobProxyObject() {
        return evidences.getBrowserMobProxy();
    }

    public int getMaxRetries() {
        return retriesDisabled ? 0 : zahoriProperties.getDefinedRetries();
    }

    public void disableTestRetries() {
        retriesDisabled = true;
        logInfo("zahori.testInfo.execution.retries.disabled");
    }

    public String getDownloadPath() {
        return zahoriProperties.getDownloadPath();
    }

    public void disableUpdateTestResult() {
        updateTestResultDisabled = true;
        logInfo("zahori.testInfo.execution.updatetms.disabled");
    }

    public int getTestDuration() {
        return testDuration;
    }

    public String getTmsName() {
        return caseExecution.getConfiguration().getTms().getName();
    }

    public boolean isAppiumLocalService() {
        return "local".equalsIgnoreCase(appiumService);
    }

    public void setRemoteUrl(String url) {
        this.remoteUrl = url;
    }

    public void writeSteps2Json() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode json = mapper.createObjectNode();
        json.put("testName", testCaseName);
        json.put("testStatus", testPassed ? "PASSED" : "FAILED");
        json.put("executionDate", getDate(testId));
        json.put("platform", platform);
        json.put("browserName", browserName);
        json.put("browserVersion", version);
        json.put("bits", bits);
        json.put("durationSeconds", getTestDuration());
        json.put("executionNotes", executionNotes);
        ArrayNode stepsArray = mapper.convertValue(testSteps, ArrayNode.class);
        ArrayNode newStepsArray = mapper.createArrayNode();
        int stepPosition = 1;
        for (JsonNode currentStepList : stepsArray) {
            // ArrayNode subStepsArray = mapper.createArrayNode();
            for (JsonNode step : currentStepList) {
                ObjectNode currentStep = (ObjectNode) step;
                String key = step.get("description").asText();
                List<String> argsList = new ArrayList<>();
                for (JsonNode currentArg : step.get("descriptionArgs")) {
                    argsList.add(currentArg.asText());
                }
                String[] args = argsList.stream().toArray(String[]::new);
                currentStep.put("messageText", getMessage(key, args));
                currentStep.put("name", stepPosition);
                stepPosition++;
                newStepsArray.add(currentStep);
            }
            // newStepsArray.add(subStepsArray);
        }
        json.set("steps", newStepsArray);
        try {
            mapper.writeValue(new File(evidences.getPath() + JSON_REPORT), json);
        } catch (IOException e) {
            logInfo("Error writting steps JSON file: " + e.getMessage());
        }
    }

    private String getDate(String date) {
        Date formattedDate;
        try {
            formattedDate = new SimpleDateFormat(DATE_FORMAT).parse(date);
            return new SimpleDateFormat(DATE_WEB_FORMAT).format(formattedDate);
        } catch (ParseException e) {
            logError("Error parsing jenkins date '" + date + "': " + e.getMessage());
            return "";
        }
    }

    public List<String> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<String> attachments) {
        this.attachments = attachments;
    }

    public void addAttachment(String filepath) {
        if (Files.exists(Paths.get(filepath))) {
            attachments.add(filepath);
        } else {
            logError("Error adding attachment. File does not exist: {}", filepath);
        }
    }

    public boolean isMobileDriver() {
        return isAndroidDriver() || isIOSDriver();
    }

    public boolean isAndroidDriver() {
        return driver instanceof AndroidDriver;
    }

    public boolean isIOSDriver() {
        return driver instanceof IOSDriver;
    }

    public void switchToWindowWithUrl(String url) {
        if (isIOSDriver()) {
            logWarn("switchToWindowWithUrl not implemented for iOSDriver: driver.getWindowHandles() not supported");
            return;
        }

        int secondsWaiting = 1;
        while (secondsWaiting <= this.timeoutFindElement) {
            for (String winHandle : this.driver.getWindowHandles()) {
                this.driver.switchTo().window(winHandle);

                if (StringUtils.contains(this.driver.getCurrentUrl().trim().toLowerCase(), url.trim().toLowerCase())) {
                    return;
                }
            }
            Pause.pause(1);
            secondsWaiting++;
        }

        throw new RuntimeException("Window containing url '" + url + "' not found");
    }

    public void switchToWindowWithTitle(String title) {
        if (isIOSDriver()) {
            logWarn("switchToWindowWithTitle not implemented for iOSDriver: driver.getWindowHandles() not supported");
            return;
        }

        int secondsWaiting = 1;
        while (secondsWaiting <= this.timeoutFindElement) {
            for (String winHandle : ((IOSDriver) this.driver).getWindowHandles()) {
                this.driver.switchTo().window(winHandle);

                if (StringUtils.contains(this.driver.getTitle().trim().toLowerCase(), title.trim().toLowerCase())) {
                    return;
                }
            }
            Pause.pause(1);
            secondsWaiting++;
        }

        throw new RuntimeException("Window containing title '" + title + "' not found");
    }

    public void switchToNativeContext() {
        if (isAndroidDriver()) {
            AndroidDriver androidDriver = (AndroidDriver) driver;
            androidDriver.context("NATIVE_APP");
        }
        if (isIOSDriver()) {
            IOSDriver iosDriver = (IOSDriver) driver;
            iosDriver.context("NATIVE_APP");
        }
    }

    public void switchToWebContext(String name) {
        if (isAndroidDriver()) {
            AndroidDriver androidDriver = (AndroidDriver) driver;
            androidDriver.context(name);
        }
        if (isIOSDriver()) {
            IOSDriver iosDriver = (IOSDriver) driver;
            iosDriver.context(name);
        }
    }

    public void switchToWebContext() {
        if (isAndroidDriver()) {
            AndroidDriver androidDriver = (AndroidDriver) driver;
            switchToWebContextAndroid(androidDriver);
        }
        if (isIOSDriver()) {
            IOSDriver iosDriver = (IOSDriver) driver;
            switchToWebContextIOS(iosDriver);
        }
    }

    private void switchToWebContextAndroid(AndroidDriver androidDriver) {
        ArrayList<String> contexts = new ArrayList<>(androidDriver.getContextHandles());
        System.out.println("getContextHandles: ");
        for (String context : contexts) {
            System.out.println("- context: " + context);
            if (context.contains("WEBVIEW")) {
                androidDriver.context(context);
                return;
            }
        }
    }

    private void switchToWebContextIOS(IOSDriver iOSDriver) {
        ArrayList<String> contexts = new ArrayList<>(iOSDriver.getContextHandles());
        System.out.println("getContextHandles: ");
        for (String context : contexts) {
            System.out.println("- context: " + context);
            if (context.contains("WEBVIEW")) {
                iOSDriver.context(context);
                return;
            }
        }
    }
}
