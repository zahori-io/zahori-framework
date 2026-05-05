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
import com.browserstack.local.Local;
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
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import net.lightbody.bmp.client.ClientUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

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
    public String browserVersion;
    public String bits;
    public String resolution;
    public String remote;
    public String remoteUrl;
    public String appiumService;
    public int retries = 0;
    private Map<String, Object> data = new HashMap<>();

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

    private Local browserStackLocal;
    private int browserStackLocalRetry = 0;
    private final int browserStackLocalMaxRetries = 5;

    private BrowserMobProxy browserMobProxy;

    public TestContext(CaseExecution caseExecution, ProcessRegistration processRegistration) {
        this.caseExecution = caseExecution;
        this.processRegistration = processRegistration;

        testCaseName = caseExecution.getCas().getName();
        caseExecutionId = String.valueOf(caseExecution.getCaseExecutionId());
        platform = getPlatform(); // TODO
        bits = "32"; // TODO
        browserName = caseExecution.getBrowser() == null ? "" : caseExecution.getBrowser().getBrowserName().toUpperCase();
        browserVersion = StringUtils.isBlank(caseExecution.getBrowser().getVersion()) ? caseExecution.getBrowser().getDefaultVersion()
                : caseExecution.getBrowser().getVersion();
        resolution = StringUtils.isBlank(caseExecution.getScreenResolution()) ? DEFAULT_SCREEN_RESOLUTION
                : caseExecution.getScreenResolution() + DEFAULT_BIT_DEPTH;
    }

    // TODO Temporal workaround for Appium driver creation:
    private String getPlatform() {
        if (StringUtils.containsIgnoreCase(caseExecution.getConfiguration().getName(), "android")) {
            return "ANDROID";
        }
        if (StringUtils.containsIgnoreCase(caseExecution.getConfiguration().getName(), "ios")) {
            return "IOS";
        }
        if (StringUtils.containsIgnoreCase(caseExecution.getConfiguration().getName(), "windows")) {
            return "windows";
        }
        if (StringUtils.containsIgnoreCase(caseExecution.getConfiguration().getName(), "OS X")
                || StringUtils.containsIgnoreCase(caseExecution.getConfiguration().getName(), "OSX")
                || StringUtils.containsIgnoreCase(caseExecution.getConfiguration().getName(), "MAC")) {
            return "osx";
        }
        return "LINUX";
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

        hostDriver = null;

        logInfo("Test context initialized!");
    }

    public void createDriver() {
        // Driver and browser // TODO hacer factoría única para desktop, mobile,
        // host...
        if (StringUtils.isEmpty(browserName)) {
            browser = null;
        } else {
            browser = new Browser(this);
            logInfo("Driver initialized!");
        }
    }

    /* Starts a local connection with BrowserStack if the following capabilities are set in zahori.properties:
        zahori.test.capabilities.add.android.bstack\:options.local=true
        zahori.test.capabilities.add.android.bstack\:options.localIdentifier={executionId}-{caseExecutionId}
        or
        zahori.test.capabilities.add.ios.bstack\:options.local=true
        zahori.test.capabilities.add.ios.bstack\:options.localIdentifier={executionId}-{caseExecutionId}
     */
    public void startRemoteTunnel() {
        browserStackLocalRetry++;

        String browserStackLocalConnection = zahoriProperties.getProperty("zahori.test.capabilities.add." + platform.toLowerCase() + ".bstack:options.local");
        if (StringUtils.isBlank(browserStackLocalConnection) || !Boolean.parseBoolean(browserStackLocalConnection)) {
            return;
        }

        String accessKey = zahoriProperties.getProperty("zahori.test.capabilities.add." + platform.toLowerCase() + ".bstack:options.accessKey");
        if (StringUtils.isBlank(accessKey)) {
            logStepFailed("BrowserStack local connection is enable in zahori.properties but the access key is not defined");
        }

        String localIdentifier = caseExecution.getExecutionId() + "-" + caseExecution.getCaseExecutionId();

        boolean isRunning;
        long start = System.currentTimeMillis();
        try {
            HashMap<String, String> browserStackLocalArgs = new HashMap<>();

            browserStackLocalArgs.put("key", accessKey);
            // Route all traffic via this local machine:
            browserStackLocalArgs.put("forcelocal", "true");
            // Disable local testing for Live and Screenshots, and enable only Automate:
            browserStackLocalArgs.put("onlyAutomate", "true");
            // For doing simultaneous multiple local testing connections, set this uniquely for different processes:
            browserStackLocalArgs.put("localIdentifier", localIdentifier);

            // Enable verbose logging:
            //// bsLocalArgs.put("v", "true");
            // Log file:
            //// browserStackLocalArgs.put("logFile", "./browserstack-agent.log");
            // Binary Path (downloads):
            //// browserStackLocalArgs.put("binarypath", "./BrowserStackLocal");
            browserStackLocal = new Local();
            browserStackLocal.start(browserStackLocalArgs);

            isRunning = browserStackLocal.isRunning();
            if (!isRunning) {
                throw new Exception("Connection is not running");
            }
            logInfo("BrowserStack local connection started with id " + localIdentifier + " (" + String.valueOf(System.currentTimeMillis() - start) + " ms)");

        } catch (Exception e) {
            String errorMessage = "BrowserStack local connection failed (id " + localIdentifier + ") [retry: " + browserStackLocalRetry + "]: " + e.getMessage();
            if (browserStackLocalRetry >= browserStackLocalMaxRetries) {
                failTest(errorMessage);
            } else {
                logWarn(errorMessage);
                Pause.pause(1);
                stopRemoteTunnel();
                startRemoteTunnel();
            }
        }
    }

    public void stopRemoteTunnel() {
        try {
            if (browserStackLocal != null) {
                browserStackLocal.stop();
                logInfo("BrowserStack local connection stopped");
            }
        } catch (Exception e) {
            logError("BrowserStack local connection failed to stop: {}", e.getMessage());
        }
    }

    private void logTestInfo(Evidences evidences) {
        evidences.insertTextInDocs("zahori.testInfo.execution.date", testId);
        evidences.insertTextInDocs("zahori.testInfo.execution.platform", platform);
        evidences.insertTextInDocs("zahori.testInfo.execution.configuration", caseExecution.getConfiguration().getName());
        evidences.insertTextInDocs("zahori.testInfo.execution.environment", caseExecution.getConfiguration().getEnvironmentName());
        evidences.insertTextInDocs("zahori.testInfo.execution.url", caseExecution.getConfiguration().getEnvironmentUrl());
        evidences.insertTextInDocs("zahori.testInfo.execution.browser.name", browserName);
        evidences.insertTextInDocs("zahori.testInfo.execution.browser.version", browserVersion);
        evidences.insertTextInDocs("zahori.testInfo.execution.browser.resolution", resolution);
        evidences.insertTextInDocs("zahori.testInfo.execution.evidences.path", evidences.getPath());

        logInfo("zahori.testInfo.title");
        logInfo("- Case: " + testCaseName);
        logInfo("zahori.testInfo.execution.date", testId);
        logInfo("zahori.testInfo.execution.platform", platform);
        logInfo("zahori.testInfo.execution.configuration", caseExecution.getConfiguration().getName());
        logInfo("zahori.testInfo.execution.environment", caseExecution.getConfiguration().getEnvironmentName());
        logInfo("zahori.testInfo.execution.url", caseExecution.getConfiguration().getEnvironmentUrl());
        logInfo("zahori.testInfo.execution.browser.name", browserName);
        logInfo("zahori.testInfo.execution.browser.version", browserVersion);
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
        if (browser == null) {
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
        if (browser != null) {
            evidences.startVideo();
        }
    }

    public void stopVideo() {
        if (browser != null) {
            evidences.stopVideo(testPassed);
        }
    }

    public void moveMouseToUpperLeftCorner() {
        try {
            if ((driver == null) || browser == null) {
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

    public BrowserMobProxy getBrowserMobProxy() {
        return browserMobProxy;
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
        return zahoriProperties.isHarEnabled();
    }

    public Proxy createBrowserMobProxy() {
        if (!zahoriProperties.isAddHeadersEnabled()
                && !zahoriProperties.isBlackListEnabled()
                && !zahoriProperties.isHarEnabled()) {
            return null;
        }

        if (isMobileDriver()) {
            return null;
        }

        // BrowserMob proxy
        browserMobProxy = new BrowserMobProxy(zahoriProperties);
        browserMobProxy.start();
        browserMobProxy.startHarCapture(this.caseExecution.getCas().getName());

        // Selenium proxy
        Proxy seleniumProxy = ClientUtil.createSeleniumProxy(browserMobProxy.getProxy());

        String ip = getProxyIP();
        int port = browserMobProxy.getPort();
        String hostAndPort = ip + ":" + port;
        seleniumProxy.setHttpProxy(hostAndPort);
        seleniumProxy.setSslProxy(hostAndPort);

        logInfo("Defined selenium proxy at {}", hostAndPort);

        return seleniumProxy;
    }

    public void stopBrowserMobProxy() {
        if (browserMobProxy != null) {
            try {
                saveHarLog();
                browserMobProxy.stop();
            } catch (Exception e) {
                logWarn("Error stopping selenium proxy: {}", e.getMessage());
            }
        }
    }

    private void saveHarLog() throws IOException {
        if (browserMobProxy != null) {
            browserMobProxy.stopHarCapture(new File(evidences.getEvidencesPath() + evidences.getHarLogFileName()));
        }
    }

    private boolean isLinuxOS() {
        String osName = System.getProperty("os.name").toLowerCase();
        return osName.contains("nux") || osName.contains("nix");
    }

    private String getProxyIP() {
        boolean remoteBrowser = StringUtils.equalsIgnoreCase(Browsers.REMOTE_YES, remote);
        if (!remoteBrowser) {
            return "localhost";
        }

        if (isMobileWebApp()) {
            return "localhost";
        }

        if (isLinuxOS()) {
            return getLocalIP();
        }

        // Para que en Mac y Windows los contenedores de Selenoid tengan conexión con el proxy que no está en el deben usar "host.docker.internal"
        return "host.docker.internal";
    }

    private String getLocalIP() {
        String ip = "";
        try {
            // A. Alternativa rápida (menos precisa), puede devolver 127.0.0.1 en muchos entornos, especialmente en contenedores o configuraciones sin DNS apropiado.
            //ip = Inet4Address.getLocalHost().getHostAddress();

            // B. Alternativa más precisa:
            // Iterar sobre todas las interfaces de red
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();

                // Ignorar interfaces no activas o loopback
                if (!iface.isUp() || iface.isLoopback() || iface.isVirtual()) {
                    continue;
                }

                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();

                    // Solo IPv4 (si se necesita IPv6, quitar este filtro)
                    if (addr.getHostAddress().contains(".")) {
                        ip = addr.getHostAddress();
                        // System.out.println("IP local: " + ip);
                    }
                }
            }
        } catch (Exception e) {
            logInfo("Error getting local IP: {}", e.getMessage());
        } finally {
            return ip;
        }
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
        json.put("browserVersion", browserVersion);
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

    public void hideKeyboard() {
        if (isAndroidDriver()) {
            ((AndroidDriver) driver).hideKeyboard();
        }
        if (isIOSDriver()) {
            try {
                ((IOSDriver) driver).hideKeyboard();
            } catch (Exception e) {
                PageElement okButtonOnKeyboard = new PageElement(new Page(this), "OK/Done button on keyboard",
                        Locator.xpath("(//*[@name='Done' or @name='OK'])[1]"));
                if (okButtonOnKeyboard.isVisibleWithoutWait()) {
                    okButtonOnKeyboard.clickNonVisible();
                }
            }
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

    public boolean isWebApp() {
        Capabilities capabilities = ((RemoteWebDriver) driver).getCapabilities();
        String browserNameCapability = (String) capabilities.getCapability("browserName");
        String appiumBrowserNameCapability = (String) capabilities.getCapability("appium:browserName");
        // Note: For native Apps, the "app" and "appium:app" capabilities are removed and replaced with appPackage, appActivity or bundleId... when driver is instantiated,
        // so "app" capability can't be used to distinguise between web and native app, that's why it is used browserName.

        return StringUtils.isNotBlank(browserNameCapability) || StringUtils.isNotBlank(appiumBrowserNameCapability);
    }

    public boolean isMobileWebApp() {
        return isMobileDriver() && isWebApp();
    }

    public boolean isMobileNativeApp() {
        return isMobileDriver() && !isMobileWebApp();
    }

    public void switchToWindowWithUrl(String url) {
        logInfo("switching to window with url: {}", url);
        // getPageSource();

        // logInfo("getting current url...");
        String currentUrl = getCurrentUrl();

        if (StringUtils.containsIgnoreCase(currentUrl, url)) {
            return;
        }

        int secondsWaiting = 1;
        while (secondsWaiting <= timeoutFindElement) {

            getPageSource();

            List<String> windowHandles = new ArrayList<>(getWindowHandles());
            for (int i = windowHandles.size() - 1; i >= 0; i--) {
                String windowHandle = windowHandles.get(i);
                try {
                    if (isMobileNativeApp()) {
                        switchToWebContext("WEBVIEW_" + windowHandle);
                    }

                    logInfo("switching to windowHandle: {}", windowHandle);
                    this.driver.switchTo().window(windowHandle);
                    getPageSource();

                    if (StringUtils.containsIgnoreCase(getCurrentUrl().trim(), url.trim())) {
                        return;
                    }
                } catch (Exception e) {
                    logError("Error on switchToWindowWithUrl({}): {}", url, e.getMessage());
                }
            }
            Pause.pause(1);
            secondsWaiting++;
        }

        throw new RuntimeException("Window containing url '" + url + "' not found");
    }

    public Set<String> getWindowHandles() {
        Set<String> windowHandles = new HashSet<>();
        try {
            getPageSource();
            windowHandles = this.driver.getWindowHandles();
            logInfo("getWindowHandles: {}", windowHandles.toString());
        } catch (Exception e) {
            logError("getWindowHandles: {}", e.getMessage());
        }
        return windowHandles;
    }

    // TODO
    public void switchToWindowWithTitle(String title) {
        logInfo("switching to window with title: {}", title);
        // getPageSource();

        // logInfo("getting current title...");
        String currentTitle = this.driver.getTitle();
        logInfo("currentTitle: {}", currentTitle);
        if (StringUtils.containsIgnoreCase(currentTitle, title)) {
            return;
        }

        int secondsWaiting = 1;
        while (secondsWaiting <= timeoutFindElement) {

            List<String> windowHandles = new ArrayList<>(getWindowHandles());
            for (int i = windowHandles.size() - 1; i >= 0; i--) {
                String windowHandle = windowHandles.get(i);
                try {
                    if (isMobileNativeApp()) {
                        switchToWebContext("WEBVIEW_" + windowHandle);
                    }

                    logInfo("switching to windowHandle: {}", windowHandle);
                    this.driver.switchTo().window(windowHandle);
                    getPageSource();

                    if (StringUtils.containsIgnoreCase(this.driver.getTitle().trim(), title.trim())) {
                        logInfo("currentTitle: {}", currentTitle);
                        return;
                    }
                } catch (Exception e) {
                    logError("Error switchToWindowWithUrl({}): {}", title, e.getMessage());
                }
            }
            Pause.pause(1);
            secondsWaiting++;
        }

        throw new RuntimeException("Window containing title '" + title + "' not found");
    }

    public void switchToWebviewWithUrl(String url) {
        if (!isMobileNativeApp()) {
            logWarn("method switchToWebviewWithUrl is only supported for native apps, use instead method switchToWindowWithUrl");
            return;
        }

        logInfo("switching to webview with url: {}", url);

        int secondsWaiting = 1;
        while (secondsWaiting <= timeoutFindElement) {

            List<String> webContexts = new ArrayList<>(getWebContexts());
            for (int i = webContexts.size() - 1; i >= 0; i--) {
                String webContext = webContexts.get(i);
                switchToWebContext(webContext);

                List<String> windowHandles = new ArrayList<>(this.driver.getWindowHandles());
                for (int j = windowHandles.size() - 1; j >= 0; j--) {
                    String windowHandle = windowHandles.get(j);

                    try {

                        logInfo("switching to windowHandle: {}", windowHandle);
                        this.driver.switchTo().window(windowHandle);
                        getPageSource();

                        if (StringUtils.containsIgnoreCase(getCurrentUrl().trim(), url.trim())) {
                            return;
                        }
                    } catch (Exception e) {
                        logError("Error on switchToWebViewWithUrl({}): {}", url, e.getMessage());
                    }
                }
            }
            logInfo("switching to windowHandle, waiting for 1 second...");
            Pause.pause(1);
            secondsWaiting++;
        }

        throw new RuntimeException("webview containing url '" + url + "' not found");
    }

    public String getCurrentContext() {
        try {
            if (isAndroidDriver()) {
                return ((AndroidDriver) driver).getContext();
            }
            if (isIOSDriver()) {
                return ((IOSDriver) driver).getContext();
            }
        } catch (Exception e) {
            logError("getCurrentContext error: {}", e.getMessage());
            return "";
        }
        throw new RuntimeException("Method getCurrentContext() only supported for AndroidDriver and IOSDriver");
    }

    public void switchToNativeContext() {
        logInfo("switchToNativeContext");
        if (isAndroidDriver()) {
            AndroidDriver androidDriver = (AndroidDriver) driver;
            androidDriver.context("NATIVE_APP");
        }
        if (isIOSDriver()) {
            IOSDriver iosDriver = (IOSDriver) driver;
            iosDriver.context("NATIVE_APP");
        }
    }

    public void switchToWebContext() {
        switchToWebContext(null);
    }

    public void switchToWebContext(String contextName) {
        switchToMobileWebContext(contextName);

        String context = getCurrentContext();
        int switchRetries = 0;
        while (StringUtils.contains(context, "NATIVE") && switchRetries <= timeoutFindElement) {
            switchRetries++;
            Pause.pause(1);
            System.out.println("Waiting 1 second to find a webview...");

            switchToMobileWebContext(contextName);
            context = getCurrentContext();
        }

        if (StringUtils.contains(context, "NATIVE")) {
            throw new RuntimeException("Webview " + contextName + " not found");
        }
    }

    private void switchToMobileWebContext(String contextName) {
        if (isAndroidDriver()) {
            AndroidDriver androidDriver = (AndroidDriver) driver;
            switchToWebContextAndroid(androidDriver, contextName);
        }
        if (isIOSDriver()) {
            IOSDriver iosDriver = (IOSDriver) driver;
            switchToWebContextIOS(iosDriver, contextName);
        }
        getPageSource();
    }

    private void switchToWebContextAndroid(AndroidDriver androidDriver, String contextName) {
        if (StringUtils.isNotBlank(contextName)) {
            try {
                logInfo("switching to context: {}", contextName);
                androidDriver.context(contextName);
                getCurrentUrl();
            } catch (Exception e) {
                logError("Error switching to context {}: {}", contextName, e.getMessage());
            }
            return;
        }

        List<String> webContexts = getWebContexts();
        for (String context : webContexts) {

            // switch to first available context (no error and not empty source)
            try {
                // For Webviews and browser contexts (CHROMIUM, WEBVIEW_org.mozilla.firefox, ...)
                logInfo("switching to context: {}", context);
                androidDriver.context(context);

                if (StringUtils.isNotBlank(getCurrentUrl()) && StringUtils.isNotBlank(getPageSource())) {
                    return;
                }
            } catch (Exception e) {
                logError("Error switching to context {}: {}", contextName, e.getMessage());
            }
        }

        // webview not found or invalid, switch back to native context
        switchToNativeContext();
    }

    private void switchToWebContextIOS(IOSDriver iOSDriver, String contextName) {
        if (StringUtils.isNotBlank(contextName)) {
            try {
                logInfo("switching to context: {}", contextName);
                iOSDriver.context(contextName);
                getCurrentUrl();
            } catch (Exception e) {
                logError("Error switching to context {}: {}", contextName, e.getMessage());
            }
            return;
        }

        List<String> webContexts = getWebContexts();
        for (String context : webContexts) {

            // switch to first available context (no error and not empty source)
            try {
                // For Webviews and browser contexts (CHROMIUM, WEBVIEW_org.mozilla.firefox, ...)
                logInfo("switching to context: {}", context);
                iOSDriver.context(context);

                if (StringUtils.isNotBlank(getCurrentUrl()) && StringUtils.isNotBlank(getPageSource())) {
                    return;
                }
            } catch (Exception e) {
                logError("Error switching to context {}: {}", contextName, e.getMessage());
            }
        }

        // webview not found or invalid, switch back to native context
        switchToNativeContext();
    }

    public List<String> getWebContexts() {
        List<String> webContexts = new ArrayList<>();
        try {
            getPageSource();

            if (isAndroidDriver()) {
                AndroidDriver androidDriver = (AndroidDriver) driver;
                webContexts = getWebContextsAndroid(androidDriver);
            }
            if (isIOSDriver()) {
                IOSDriver iosDriver = (IOSDriver) driver;
                webContexts = getWebContextsIOS(iosDriver);
            }
        } catch (Exception e) {
            logError("getWebContexts error: {} ", e.getMessage());
        }

        logInfo("getWebContexts: {} ", webContexts.toString());
        return webContexts;
    }

    private List<String> getWebContextsAndroid(AndroidDriver androidDriver) {
        List<String> webContexts = new ArrayList<>();
        Set<String> contexts = androidDriver.getContextHandles();
        for (String context : contexts) {
            if (context.contains("WEBVIEW")) {
                webContexts.add(context);
            }
        }
        return webContexts;
    }

    private List<String> getWebContextsIOS(IOSDriver iOSDriver) {
        List<String> webContexts = new ArrayList<>();
        Set<String> contexts = iOSDriver.getContextHandles();
        for (String context : contexts) {
            if (context.contains("WEBVIEW")) {
                webContexts.add(context);
            }
        }
        return webContexts;
    }

    public String getPageSource() {
        String sourceCode = "";

        int maxRetries = 5;
        int retry = 1;
        while (StringUtils.isBlank(sourceCode) && retry <= maxRetries) {
            try {
                retry++;
                // logInfo("getting page source...");
                sourceCode = driver.getPageSource();
            } catch (Exception e) {
                logError("getPageSource error: {} ", e.getMessage());
            }
            Pause.pause(1);
        }
        // logInfo("page source -> {} ", sourceCode);
        return sourceCode;
    }

    public String getCurrentUrl() {
        String currentUrl = "";
        if (isMobileDriver()) {
            try {
                currentUrl = (String) ((JavascriptExecutor) driver).executeScript("return window.location.href;");
                logInfo("getCurrentUrl (javascript): {}", currentUrl);
                return currentUrl;
            } catch (Exception e) {
                logError("getCurrentUrl (javascript) error: {}", e.getMessage());
                try {
                    LinkedHashMap currentUrlMap = (LinkedHashMap) ((JavascriptExecutor) driver).executeScript("return window.location.href;");
                    logInfo("getCurrentUrl (javascript): {}", currentUrlMap.toString());
                } catch (Exception ex) {
                    logError("getCurrentUrl (javascript) error: {}", ex.getMessage());
                }
            }
        }

        try {
            currentUrl = new WebDriverWait(driver, Duration.ofSeconds(timeoutFindElement))
                    .until(d -> {
                        String currenturl = d.getCurrentUrl();
                        return (currenturl != null && !currenturl.isEmpty()) ? currenturl : "";
                    });

            logInfo("getCurrentUrl: {}", currentUrl);
            return currentUrl;
        } catch (Exception e) {
            logError("getCurrentUrl error: {}", e.getMessage());
        }

        logInfo("getCurrentUrl: {}", currentUrl);
        return currentUrl;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }

    public Object getData(String key) {
        return data.get(key);
    }

    public void setData(String key, Object data) {
        this.data.put(key, data);
    }

}
