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

import java.io.File;
import java.io.IOException;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.WebDriver;
import org.testng.ITestContext;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.appium.java_client.AppiumDriver;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.ios.IOSDriver;
import io.zahori.framework.driver.browserfactory.BrowserMobProxy;
import io.zahori.framework.driver.browserfactory.Browsers;
import io.zahori.framework.evidences.Evidences;
import io.zahori.framework.evidences.Evidences.ZahoriLogLevel;
import io.zahori.framework.exception.ZahoriException;
import io.zahori.framework.files.properties.ProjectProperties;
import io.zahori.framework.files.properties.SystemPropertiesUtils;
import io.zahori.framework.files.properties.ZahoriProperties;
import io.zahori.framework.i18n.Messages;
import io.zahori.framework.robot.UtilsRobot;
import io.zahori.framework.tms.TMS;
import io.zahori.framework.utils.Notification;
import io.zahori.framework.utils.WebdriverUtils;
import io.zahori.model.Status;
import io.zahori.model.Step;

/**
 * The type Test context.
 */
public class TestContext {

    /**
     * The constant DATE_FORMAT.
     */
    public static final String DATE_FORMAT = "yyyyMMdd-HHmmss";
    /**
     * The Test id.
     */
    public String testId = new SimpleDateFormat(DATE_FORMAT).format(new Date());
    /**
     * The Url.
     */
    public String url;
    /**
     * The Test passed.
     */
    public boolean testPassed = true;
    /**
     * The Zahori exception.
     */
    public ZahoriException zahoriException;
    private List<Step> currentStep = new ArrayList<>();
    private List<List<Step>> testSteps = new ArrayList<>();
    private int testDuration = 0;
    private long testStartupTime;

    /**
     * The Test case name.
     */
// TestNG parameters defined in xml file
    public String testCaseName;
    /**
     * The Driver.
     */
    public WebDriver driver;
    /**
     * The Host driver.
     */
    public WebDriver hostDriver;
    /**
     * The Platform.
     */
    public String platform;
    /**
     * The Browser name.
     */
    public String browserName;
    /**
     * The Bits.
     */
    public String bits;
    /**
     * The Version.
     */
    public String version;
    /**
     * The Remote.
     */
    public String remote;
    /**
     * The Remote url.
     */
    public String remoteUrl;
    /**
     * The Appium service.
     */
    public String appiumService;

    // Properties
    private ZahoriProperties zahoriProperties;
    private ProjectProperties projectProperties;

    /**
     * The Timeout find element.
     */
// TEST EXECUTION TIMEOUTS
    public Integer timeoutFindElement;

    // Browser
    private Browser browser;

    // Evidences
    private Evidences evidences;

    // TMS (Test Management Systems)
    private TMS tms;
    /**
     * The Tms test set id.
     */
    public String tmsTestSetId;
    /**
     * The Tms test case id.
     */
    public String tmsTestCaseId;
    /**
     * The Tms test exec id.
     */
    public String tmsTestExecId;
    /**
     * The Tms test plan id.
     */
    public String tmsTestPlanId;

    /**
     * The Case execution id.
     */
    public String caseExecutionId;

    // i18n messages
    // private Map<String, MessageReader> messages = new LinkedHashMap<String,
    // MessageReader>();
    private Messages messages;
    private String executionNotes;
    private String failCause;
    private boolean retriesDisabled;
    private boolean updateTestResultDisabled;
    /**
     * The Testng context.
     */
    public ITestContext testngContext;

    /**
     * Instantiates a new Test context.
     */
    public TestContext() {
    }

    /**
     * Instantiates a new Test context.
     *
     * @param testNGContext the test ng context
     */
    public TestContext(ITestContext testNGContext) {
        this.testngContext = testNGContext;
        // Load TestNG parameters
        loadTestNGParameters(testNGContext);

        constructor();
    }

    /**
     * Constructor.
     */
    public void constructor() {
        // Load configuration.properties into system properties
        // For IE is mandatory to load property: webdriver.ie.driver.path
        SystemPropertiesUtils.loadSystemProperties();

        // Load properties files: zahorí and project specific
        zahoriProperties = new ZahoriProperties();
        projectProperties = new ProjectProperties();

        // Load systemPropertyVariables from pom files
        url = System.getProperty("url.web");
        if (StringUtils.isBlank(url)) {
            url = zahoriProperties.getTestUrl();
        }

        // Initialize messages readers with languages defined in
        // zahori.properties
        messages = new Messages(zahoriProperties.getLanguages());

        // Prepare evidences from configuration.properties
        evidences = new Evidences(zahoriProperties, messages, testCaseName, platform, browserName, testId, getProjectProperty("evidences.template.file.path"),
                StringUtils.equalsIgnoreCase(Browsers.REMOTE_YES, remote));

        // Test execution parameters from configuration.properties
        timeoutFindElement = zahoriProperties.getTimeoutFindElement();

        // Create TMS object to update test results and upload evidences to
        // TestLink, ALM...;
        tms = new TMS(zahoriProperties, evidences, messages, testId);

        evidences.insertTextInDocs("zahori.testInfo.execution.date", testId);
        evidences.insertTextInDocs("zahori.testInfo.execution.platform", platform);
        evidences.insertTextInDocs("zahori.testInfo.execution.browser.name", browserName);
        evidences.insertTextInDocs("zahori.testInfo.execution.browser.version", version);
        evidences.insertTextInDocs("zahori.testInfo.execution.bits", bits);
        evidences.insertTextInDocs("zahori.testInfo.execution.evidences.path", evidences.getPath());

        logInfo("zahori.testInfo.title");
        logInfo("- Case: " + testCaseName);
        logInfo("zahori.testInfo.execution.date", testId);
        logInfo("zahori.testInfo.execution.platform", platform);
        logInfo("zahori.testInfo.execution.browser.name", browserName);
        logInfo("zahori.testInfo.execution.browser.version", version);
        logInfo("zahori.testInfo.execution.bits", bits);
        String txtEvidencesPathProperty = "zahori.testInfo.execution.evidences.path";
        String evidencesPath = getMessage(txtEvidencesPathProperty);
        if (!evidencesPath.isEmpty() && !evidencesPath.equals(txtEvidencesPathProperty)) {
            logInfo(txtEvidencesPathProperty, evidences.getPath());
        }
        if ((url != null) && !url.isEmpty()) {
            logInfo("- Url: " + url);
        }

        // Driver and browser // TODO hacer factoría única para desktop, mobile,
        // host...
        if (StringUtils.equalsIgnoreCase(String.valueOf(Browsers.NULLBROWSER), browserName)) {
            browser = null;
        } else {
            browser = new Browser(this);
        }

        hostDriver = null;
        retriesDisabled = false;
        updateTestResultDisabled = false;

        logInfo("Test context initialized!");
    }

    /**
     * Fail test.
     *
     * @param messageKey  the message key
     * @param messageArgs the message args
     */
    public void failTest(String messageKey, String... messageArgs) {
        failCause = messageKey;
        failTest(new ZahoriException(testCaseName, messageKey, messageArgs));
    }

    /**
     * Fail test.
     *
     * @param e the e
     */
    public void failTest(Exception e) {
        testPassed = false;
        if (e instanceof ZahoriException) {
            this.zahoriException = (ZahoriException) e;
        } else {
            this.zahoriException = new ZahoriException(testCaseName, e.getMessage());
            logStepWithScreenshot(Status.FAILED, "Test exception: " + messages.getMessageInFirstLanguage(zahoriException.getMessageKey()));
        }

        // throw a new exception in order to make the test fail
        throwZahoriException(zahoriException.getMessageKey(), zahoriException.getMessageArgs());
    }

    private void throwZahoriException(String errorMessageKey, String... errorMessageArgs) {
        throw new ZahoriException(testCaseName, errorMessageKey, errorMessageArgs);
    }

    /**
     * Report test result.
     */
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

        // Update test result on Test Link, ALM,...
        if (!updateTestResultDisabled) {
            tms.updateTestResult(testCaseName, testPassed, testSteps, testDuration, tmsTestSetId, tmsTestCaseId, tmsTestExecId, tmsTestPlanId, browserName,
                    platform);
        }
    }

    private void loadTestNGParameters(ITestContext testNGContext) {
        // Test case name
        testCaseName = testNGContext.getName();

        platform = testNGContext.getCurrentXmlTest().getParameter("platform");
        if (StringUtils.isBlank(platform)) {
            platform = testNGContext.getCurrentXmlTest().getParameter("platformName");
        }

        browserName = testNGContext.getCurrentXmlTest().getParameter("browser");
        if (StringUtils.isBlank(browserName)) {
            browserName = testNGContext.getCurrentXmlTest().getParameter("browserName");
        }
        version = testNGContext.getCurrentXmlTest().getParameter("version");
        bits = testNGContext.getCurrentXmlTest().getParameter("bits");
        remote = testNGContext.getCurrentXmlTest().getParameter("remote");
        remoteUrl = testNGContext.getCurrentXmlTest().getParameter("remoteUrl");
        // TMS parameters
        tmsTestSetId = testNGContext.getCurrentXmlTest().getParameter("TMS_TestSetId");
        tmsTestCaseId = testNGContext.getCurrentXmlTest().getParameter("TMS_TestCaseId");
        String testExecSP = System.getProperty("tmsTestExecId");
        tmsTestExecId = StringUtils.isEmpty(testExecSP) ? testNGContext.getCurrentXmlTest().getParameter("TMS_TestExecId") : testExecSP;
        String testPlanSP = System.getProperty("tmsTestPlanId");
        tmsTestPlanId = StringUtils.isEmpty(testPlanSP) ? testNGContext.getCurrentXmlTest().getParameter("TMS_TestPlanId") : testPlanSP;
        appiumService = testNGContext.getCurrentXmlTest().getParameter("appiumService");
        caseExecutionId = testNGContext.getCurrentXmlTest().getParameter("CaseExecutionId");
    }

    /**
     * Log debug.
     *
     * @param text     the text
     * @param textArgs the text args
     */
    public void logDebug(String text, String... textArgs) {
        evidences.console(ZahoriLogLevel.DEBUG, messages.getMessageInFirstLanguage(text, textArgs));
        evidences.insertTextInLogFile(ZahoriLogLevel.DEBUG, text, textArgs);
    }

    /**
     * Log info.
     *
     * @param text     the text
     * @param textArgs the text args
     */
    public void logInfo(String text, String... textArgs) {
        evidences.console(messages.getMessageInFirstLanguage(text, textArgs));
        evidences.insertTextInLogFile(text, textArgs);
    }

    /**
     * Log warn.
     *
     * @param text     the text
     * @param textArgs the text args
     */
    public void logWarn(String text, String... textArgs) {
        evidences.console(ZahoriLogLevel.WARN, messages.getMessageInFirstLanguage(text, textArgs));
        evidences.insertTextInLogFile(ZahoriLogLevel.WARN, text, textArgs);
    }

    /**
     * Log error.
     *
     * @param text     the text
     * @param textArgs the text args
     */
    public void logError(String text, String... textArgs) {
        evidences.console(ZahoriLogLevel.ERROR, messages.getMessageInFirstLanguage(text, textArgs));
        evidences.insertTextInLogFile(ZahoriLogLevel.ERROR, text, textArgs);
    }

    /**
     * Log step passed.
     *
     * @param description     the description
     * @param descriptionArgs the description args
     */
    public void logStepPassed(String description, String... descriptionArgs) {
        logStep(Status.PASSED, description, descriptionArgs);
    }

    /**
     * Log step passed with screenshot.
     *
     * @param description     the description
     * @param descriptionArgs the description args
     */
    public void logStepPassedWithScreenshot(String description, String... descriptionArgs) {
        logStepWithScreenshot(Status.PASSED, description, descriptionArgs);
    }

    /**
     * Log step failed.
     *
     * @param description     the description
     * @param descriptionArgs the description args
     */
    public void logStepFailed(String description, String... descriptionArgs) {
        failCause = description;
        Step step = logStep(Status.FAILED, description, descriptionArgs);
        throwZahoriException(step.getDescription(), step.getDescriptionArgs());
    }

    /**
     * Log step failed with screenshot.
     *
     * @param description     the description
     * @param descriptionArgs the description args
     */
    public void logStepFailedWithScreenshot(String description, String... descriptionArgs) {
        failCause = description;
        Step step = logStepWithScreenshot(Status.FAILED, description, descriptionArgs);
        throwZahoriException(step.getDescription(), step.getDescriptionArgs());
    }

    /**
     * Log partial step.
     *
     * @param description     the description
     * @param descriptionArgs the description args
     */
    public void logPartialStep(String description, String... descriptionArgs) {
        logPartialStepSuccess(description, descriptionArgs);
    }

    /**
     * Log partial step success.
     *
     * @param description     the description
     * @param descriptionArgs the description args
     */
    public void logPartialStepSuccess(String description, String... descriptionArgs) {
        logPartialStep(Status.PASSED, description, descriptionArgs);
    }

    /**
     * Log partial step failed.
     *
     * @param description     the description
     * @param descriptionArgs the description args
     */
    public void logPartialStepFailed(String description, String... descriptionArgs) {
        logPartialStep(Status.FAILED, description, descriptionArgs);
    }

    /**
     * Log partial step with screenshot.
     *
     * @param description     the description
     * @param descriptionArgs the description args
     */
    public void logPartialStepWithScreenshot(String description, String... descriptionArgs) {
        logPartialStepSuccessWithScreenshot(description, descriptionArgs);
    }

    /**
     * Log partial step success with screenshot.
     *
     * @param description     the description
     * @param descriptionArgs the description args
     */
    public void logPartialStepSuccessWithScreenshot(String description, String... descriptionArgs) {
        logPartialStepWithScreenshot(Status.PASSED, description, descriptionArgs);
    }

    /**
     * Log partial step failed with screenshot.
     *
     * @param description     the description
     * @param descriptionArgs the description args
     */
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

        String screenshot = evidences.createScreenshot(testSteps.size() + 1, currentStep.size() + 1, driver);
        if (screenshot != null) {
            step.addAttachment(new File(screenshot));
        }

        currentStep.add(step);
    }

    private Step logStep(String status, String description, String... descriptionArgs) {
        Step step = new Step(null, "" + (testSteps.size() + 1), status, description);
        step.setDescriptionArgs(descriptionArgs);

        currentStep.add(step);

        testSteps.add(currentStep);

        evidences.insertStep(currentStep);

        currentStep = new ArrayList<>();

        if (Status.FAILED.equals(status)) {
            executionNotes = getMessage(description, descriptionArgs);
            tms.setExecutionNotes(executionNotes);
        }

        return step;
    }

    private Step logStepWithScreenshot(String status, String description, String... descriptionArgs) {
        Step step = new Step(null, String.valueOf(testSteps.size() + 1), status, description);
        step.setDescriptionArgs(descriptionArgs);

        String screenshot = evidences.createScreenshot(testSteps.size() + 1, currentStep.size() + 1, driver);
        if (screenshot != null) {
            step.addAttachment(new File(screenshot));
        }

        currentStep.add(step);

        testSteps.add(currentStep);

        evidences.insertStep(currentStep);

        currentStep = new ArrayList<>();

        if (Status.FAILED.equals(status)) {
            executionNotes = getMessage(description, descriptionArgs);
            tms.setExecutionNotes(executionNotes);
        }

        return step;
    }

    /**
     * Start chronometer.
     */
    public void startChronometer() {
        testStartupTime = System.currentTimeMillis();
    }

    /**
     * Stop chronometer.
     */
    public void stopChronometer() {
        long testDurationLong = System.currentTimeMillis() - testStartupTime;
        testDuration = (Long.valueOf(TimeUnit.MILLISECONDS.toSeconds(testDurationLong))).intValue();
    }

    /**
     * Start video.
     */
    public void startVideo() {
        if (!StringUtils.equalsIgnoreCase(String.valueOf(Browsers.NULLBROWSER), browserName)) {
            evidences.startVideo();
        }
    }

    /**
     * Stop video.
     */
    public void stopVideo() {
        if (!StringUtils.equalsIgnoreCase(String.valueOf(Browsers.NULLBROWSER), browserName)) {
            evidences.stopVideo(testPassed);
        }
    }

    /**
     * Move mouse to upper left corner.
     */
    public void moveMouseToUpperLeftCorner() {
        try {
            if ((driver == null) || isMobileDriver() || StringUtils.equalsIgnoreCase(String.valueOf(Browsers.NULLBROWSER), browserName)) {
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

    /**
     * Gets project properties.
     *
     * @return the project properties
     */
    public ProjectProperties getProjectProperties() {
        return this.projectProperties;
    }

    /**
     * Gets project property.
     *
     * @param property the property
     * @return the project property
     */
    public String getProjectProperty(String property) {
        return this.projectProperties.getProperty(property);
    }

    /**
     * Gets project property.
     *
     * @param property     the property
     * @param propertyArgs the property args
     * @return the project property
     */
    public String getProjectProperty(String property, String... propertyArgs) {
        return this.projectProperties.getProperty(property, propertyArgs);
    }

    /**
     * Gets message.
     *
     * @param messageKey  the message key
     * @param messageArgs the message args
     * @return the message
     */
    public String getMessage(String messageKey, String... messageArgs) {
        return messages.getMessageInFirstLanguage(messageKey, messageArgs);
    }

    /**
     * Gets host emulator url.
     *
     * @return the host emulator url
     */
    public String getHostEmulatorURL() {
        return this.zahoriProperties.getHostEmulatorURL();
    }

    /**
     * Gets browser.
     *
     * @return the browser
     */
    public Browser getBrowser() {
        return browser;
    }

    /**
     * Gets host driver.
     *
     * @return the host driver
     */
    public WebDriver getHostDriver() {
        return this.hostDriver;
    }

    /**
     * Sets host driver.
     *
     * @param driver the driver
     */
    public void setHostDriver(WebDriver driver) {
        this.hostDriver = driver;
    }

    /**
     * Show notification failed.
     *
     * @param msDuration      the ms duration
     * @param description     the description
     * @param descriptionArgs the description args
     */
    public void showNotificationFailed(int msDuration, String description, String... descriptionArgs) {
        Notification info = showGenericNotification(msDuration, description, descriptionArgs);
        logStepFailedWithScreenshot(description, descriptionArgs);
        info.closeNotification();
    }

    /**
     * Show notification passed.
     *
     * @param msDuration      the ms duration
     * @param description     the description
     * @param descriptionArgs the description args
     */
    public void showNotificationPassed(int msDuration, String description, String... descriptionArgs) {
        Notification info = showGenericNotification(msDuration, description, descriptionArgs);
        logStepPassedWithScreenshot(description, descriptionArgs);
        info.closeNotification();
    }

    /**
     * Show notification failed.
     *
     * @param msDuration      the ms duration
     * @param pixelsWidth     the pixels width
     * @param pixelsHeight    the pixels height
     * @param description     the description
     * @param descriptionArgs the description args
     */
    public void showNotificationFailed(int msDuration, int pixelsWidth, int pixelsHeight, String description, String... descriptionArgs) {
        Notification info = showGenericNotification(msDuration, pixelsWidth, pixelsHeight, description, descriptionArgs);
        logStepFailedWithScreenshot(description, descriptionArgs);
        info.closeNotification();
    }

    /**
     * Gets first language.
     *
     * @return the first language
     */
    public String getFirstLanguage() {
        return zahoriProperties.getLanguages()[0];
    }

    /**
     * Gets execution timeout.
     *
     * @return the execution timeout
     */
    public int getExecutionTimeout() {
        return zahoriProperties.getExecutionTimeout();
    }

    /**
     * Show notification passed.
     *
     * @param msDuration      the ms duration
     * @param pixelsWidth     the pixels width
     * @param pixelsHeight    the pixels height
     * @param description     the description
     * @param descriptionArgs the description args
     */
    public void showNotificationPassed(int msDuration, int pixelsWidth, int pixelsHeight, String description, String... descriptionArgs) {
        Notification info = showGenericNotification(msDuration, pixelsWidth, pixelsHeight, description, descriptionArgs);
        logStepPassedWithScreenshot(description, descriptionArgs);
        info.closeNotification();
    }

    /**
     * Gets evidences folder.
     *
     * @return the evidences folder
     */
    public String getEvidencesFolder() {
        return evidences.getEvidencesPath();
    }

    private Notification showGenericNotification(int msDuration, int pixelsWidth, int pixelsHeight, String description, String... descriptionArgs) {
        Notification info = new Notification(messages.getMessageInFirstLanguage(description, descriptionArgs), pixelsWidth, pixelsHeight);
        try {
            Thread.sleep(msDuration);
        } catch (InterruptedException e) {
            info.closeNotification();
            return info;
        }

        return info;
    }

    private Notification showGenericNotification(int msDuration, String description, String... descriptionArgs) {
        Notification info = new Notification(messages.getMessageInFirstLanguage(description, descriptionArgs));
        try {
            Thread.sleep(msDuration);
        } catch (InterruptedException e) {
            info.closeNotification();
            return info;
        }

        return info;
    }

    /**
     * Gets test ng parameters.
     *
     * @param testNGContext the test ng context
     * @return the test ng parameters
     */
    public Map<String, String> getTestNGParameters(ITestContext testNGContext) {
        Map<String, String> result = new HashMap<>(testNGContext.getCurrentXmlTest().getAllParameters());
        result.put("remoteUrl", remoteUrl);
        return result;
    }

    /**
     * Is mobile platform boolean.
     *
     * @param platform the platform
     * @return the boolean
     */
    public boolean isMobilePlatform(String platform) {
        return "android".equalsIgnoreCase(platform) || "ios".equalsIgnoreCase(platform);
    }

    /**
     * Is mobile driver boolean.
     *
     * @return the boolean
     */
    public boolean isMobileDriver() {
        return driver instanceof AppiumDriver;
    }

    /**
     * Is android driver boolean.
     *
     * @return the boolean
     */
    public boolean isAndroidDriver() {
        return (driver != null) && AndroidDriver.class.equals(driver.getClass());
    }

    /**
     * Is ios driver boolean.
     *
     * @return the boolean
     */
    public boolean isIOSDriver() {
        return (driver != null) && IOSDriver.class.equals(driver.getClass());
    }

    /**
     * Sets execution notes.
     *
     * @param notes the notes
     */
    public void setExecutionNotes(String notes) {
        executionNotes = StringUtils.isEmpty(executionNotes) ? notes : executionNotes + "\n" + notes;
        tms.setExecutionNotes(notes);
    }

    /**
     * Reset execution notes.
     */
    public void resetExecutionNotes() {
        executionNotes = StringUtils.EMPTY;
    }

    /**
     * Gets execution notes.
     *
     * @return the execution notes
     */
    public String getExecutionNotes() {
        return executionNotes;
    }

    /**
     * Gets fail cause.
     *
     * @return the fail cause
     */
    public String getFailCause() {
        return failCause;
    }

    /**
     * Is har enabled boolean.
     *
     * @return the boolean
     */
    public boolean isHarEnabled() {
        return zahoriProperties.isHarLogFileEnabled();
    }

    /**
     * Gets proxy 4 driver.
     *
     * @return the proxy 4 driver
     */
    public Proxy getProxy4Driver() {
        try {
            return evidences.getBMP4HarLog();
        } catch (UnknownHostException e) {
            return null;
        }
    }

    /**
     * Store har log.
     */
    public void storeHarLog() {
        try {
            evidences.storeHarLog();
        } catch (IOException e) {
            logInfo("zahori.testInfo.execution.harlog.error");
        }
    }

    /**
     * Gets browser mob proxy object.
     *
     * @return the browser mob proxy object
     */
    public BrowserMobProxy getBrowserMobProxyObject() {
        return evidences.getBrowserMobProxy();
    }

    /**
     * Gets max retries.
     *
     * @return the max retries
     */
    public int getMaxRetries() {
        return retriesDisabled ? 0 : zahoriProperties.getDefinedRetries();
    }

    /**
     * Disable test retries.
     */
    public void disableTestRetries() {
        retriesDisabled = true;
        logInfo("zahori.testInfo.execution.retries.disabled");
    }

    /**
     * Gets download path.
     *
     * @return the download path
     */
    public String getDownloadPath() {
        return zahoriProperties.getDownloadPath();
    }

    /**
     * Disable update test result.
     */
    public void disableUpdateTestResult() {
        updateTestResultDisabled = true;
        logInfo("zahori.testInfo.execution.updatetms.disabled");
    }

    /**
     * Gets test duration.
     *
     * @return the test duration
     */
    public int getTestDuration() {
        return testDuration;
    }

    /**
     * Is appium local service boolean.
     *
     * @return the boolean
     */
    public boolean isAppiumLocalService() {
        return "local".equalsIgnoreCase(appiumService);
    }

    /**
     * Sets remote url.
     *
     * @param url the url
     */
    public void setRemoteUrl(String url) {
        this.remoteUrl = url;
    }

    /**
     * Write steps 2 json.
     */
    public void writeSteps2Json() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode json = mapper.createObjectNode();
        json.put("testName", testCaseName);
        json.put("testStatus", testPassed ? "PASSED" : "FAILED");
        json.put("executionDate", testId);
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
            mapper.writeValue(new File(evidences.getPath() + "/testSteps.json"), json);
        } catch (IOException e) {
            logInfo("Error writting steps JSON file: " + e.getMessage());
        }
    }
}
