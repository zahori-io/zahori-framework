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
import io.zahori.framework.driver.browserfactory.Browsers;
import io.zahori.framework.utils.selenium4.CDPHarCapture;
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
import io.zahori.framework.utils.Chronometer;
import io.zahori.framework.utils.Notification;
import io.zahori.framework.utils.Pause;
import io.zahori.framework.utils.WebdriverUtils;
import io.zahori.model.Status;
import io.zahori.model.Step;
import io.zahori.model.process.CaseExecution;
import io.zahori.model.process.ProcessRegistration;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * TestContext - Thread-Safe & Optimized Version
 *
 * <h2>Mejoras de Concurrencia (Java Champion Level)</h2>
 * <ul>
 *   <li>CopyOnWriteArrayList para steps y attachments (muchas lecturas, pocas escrituras)</li>
 *   <li>AtomicBoolean/AtomicInteger para estado del test</li>
 *   <li>ReadWriteLock para operaciones compuestas sobre currentStep</li>
 *   <li>DateTimeFormatter thread-safe (reemplaza SimpleDateFormat)</li>
 *   <li>ObjectMapper singleton compartido</li>
 *   <li>volatile para visibilidad entre hilos</li>
 * </ul>
 *
 * <h2>Compatibilidad JDK 21</h2>
 * <ul>
 *   <li>Sin synchronized (evita thread pinning con Virtual Threads)</li>
 *   <li>ReentrantReadWriteLock compatible con Virtual Threads</li>
 * </ul>
 */
public class TestContext {

    public static final String JSON_REPORT = "testSteps.json";

    // Constantes públicas para compatibilidad con código externo (Xray, etc.)
    public static final String DATE_FORMAT = "yyyyMMdd-HHmmss";
    public static final String DATE_WEB_FORMAT = "yyyy-MM-dd HH:mm:ss";

    // ========================================================================
    // FORMATTERS THREAD-SAFE (inmutables, reemplazan SimpleDateFormat)
    // ========================================================================
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern(DATE_FORMAT);
    private static final DateTimeFormatter DATE_WEB_FORMATTER =
            DateTimeFormatter.ofPattern(DATE_WEB_FORMAT);

    // ========================================================================
    // OBJECTMAPPER SINGLETON (thread-safe después de configuración)
    // ========================================================================
    private static final ObjectMapper SHARED_MAPPER = new ObjectMapper();

    // ========================================================================
    // LOCK PARA OPERACIONES COMPUESTAS EN STEPS
    // ========================================================================
    private final ReadWriteLock stepLock = new ReentrantReadWriteLock();

    // ========================================================================
    // ESTADO DEL TEST - THREAD-SAFE
    // ========================================================================
    public final String testId = DATE_FORMATTER.format(LocalDateTime.now());
    public volatile String url;

    /** Estado del test con visibilidad garantizada entre hilos */
    private final AtomicBoolean testPassedAtomic = new AtomicBoolean(true);
    /** Excepción capturada de forma thread-safe */
    private final AtomicReference<ZahoriException> zahoriExceptionRef = new AtomicReference<>();

    // Campos públicos para compatibilidad (sincronizados con atomics en getters/setters)
    public boolean testPassed = true;
    public ZahoriException zahoriException;

    // ========================================================================
    // COLECCIONES THREAD-SAFE
    // ========================================================================
    /** CopyOnWriteArrayList: óptimo para muchas lecturas, pocas escrituras */
    private final List<Step> currentStep = new CopyOnWriteArrayList<>();
    /** Lista de pasos del test - thread-safe para acceso concurrente */
    public final List<List<Step>> testSteps = new CopyOnWriteArrayList<>();
    /** Attachments thread-safe */
    private final List<String> attachments = new CopyOnWriteArrayList<>();

    // ========================================================================
    // TIMING - VOLATILE PARA VISIBILIDAD
    // ========================================================================
    private final AtomicInteger testDurationAtomic = new AtomicInteger(0);
    private volatile long testStartupTime;
    private volatile long stepStartupTime;

    // TestNG parameters defined in xml file
    public String testCaseName;
    public volatile WebDriver driver;
    public volatile WebDriver hostDriver;
    public String platform;
    public String browserName;
    public String bits;
    public String version;
    public String resolution;
    public volatile String remote;
    public volatile String remoteUrl;
    public volatile String appiumService;

    public int retries = 0;

    // Properties
    public volatile ZahoriProperties zahoriProperties;
    public volatile ProjectProperties projectProperties;

    // TEST EXECUTION TIMEOUTS
    public volatile Integer timeoutFindElement;

    // Browser
    private volatile Browser browser;

    // Evidences
    public volatile Evidences evidences;

    // TMS (Test Management Systems)
    private volatile TmsService tmsService;
    public String caseExecutionId;

    // i18n messages
    private volatile Messages messages;
    private volatile String executionNotes;
    protected volatile String failCause;

    /** Flags atómicos para control de estado */
    private final AtomicBoolean retriesDisabledAtomic = new AtomicBoolean(false);
    private final AtomicBoolean updateTestResultDisabledAtomic = new AtomicBoolean(false);

    public CaseExecution caseExecution;
    public ProcessRegistration processRegistration;

    private volatile Local browserStackLocal;
    private final AtomicInteger browserStackLocalRetry = new AtomicInteger(0);
    private static final int BROWSERSTACK_LOCAL_MAX_RETRIES = 5;

    private volatile CDPHarCapture cdpHarCapture;

    public TestContext(CaseExecution caseExecution, ProcessRegistration processRegistration) {
        this.caseExecution = caseExecution;
        this.processRegistration = processRegistration;

        testCaseName = caseExecution.getCas().getName();
        caseExecutionId = String.valueOf(caseExecution.getCaseExecutionId());
        platform = getPlatform(); // TODO
        bits = "32"; // TODO
        browserName = caseExecution.getBrowser() == null ? "" : caseExecution.getBrowser().getBrowserName().toUpperCase();
        version = StringUtils.isBlank(caseExecution.getBrowser().getVersion()) ? caseExecution.getBrowser().getDefaultVersion()
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

            // Start CDP HAR capture if enabled and BrowserMobProxy is not used (ZAH-156)
            startCdpHarCaptureIfNeeded();
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
        int currentRetry = browserStackLocalRetry.incrementAndGet();

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

            browserStackLocal = new Local();
            browserStackLocal.start(browserStackLocalArgs);

            isRunning = browserStackLocal.isRunning();
            if (!isRunning) {
                throw new Exception("Connection is not running");
            }
            logInfo("BrowserStack local connection started with id " + localIdentifier + " (" + (System.currentTimeMillis() - start) + " ms)");

        } catch (Exception e) {
            String errorMessage = "BrowserStack local connection failed (id " + localIdentifier + ") [retry: " + currentRetry + "]: " + e.getMessage();
            if (currentRetry >= BROWSERSTACK_LOCAL_MAX_RETRIES) {
                failTest(errorMessage);
            } else {
                logWarn(errorMessage);
                Pause.sleep(Duration.ofSeconds(1));
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
        evidences.insertTextInDocs("zahori.testInfo.execution.browser.version", version);
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
        testPassedAtomic.set(true);
        testPassed = true; // Sincronizar campo público
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
        testPassedAtomic.set(false);
        testPassed = false; // Sincronizar campo público
        if (e instanceof ZahoriException) {
            ZahoriException ze = (ZahoriException) e;
            zahoriExceptionRef.set(ze);
            zahoriException = ze; // Sincronizar campo público
        } else {
            ZahoriException ze = new ZahoriException(testCaseName, e.getMessage());
            zahoriExceptionRef.set(ze);
            zahoriException = ze; // Sincronizar campo público
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
        boolean passed = testPassedAtomic.get();
        if (passed) {
            testResult = "\n[TEST PASSED]: " + testCaseName;
            logInfo(testResult);
            evidences.insertSuccessTextInDocs(testResult);
        } else {
            testResult = "\n[TEST FAILED]: " + testCaseName;
            logInfo(testResult);
            evidences.insertFailedTextInDocs(testResult);

            ZahoriException ze = zahoriExceptionRef.get();
            if (ze != null) {
                logInfo(ze.getMessageKey(), ze.getMessageArgs());
                evidences.insertFailedTextInDocs(ze.getMessageKey(), ze.getMessageArgs());
            }
        }
    }

    // Update test result on Test Link, ALM,...
    public void uploadResultsToTms() {
        if (!updateTestResultDisabledAtomic.get()) {
            tmsService.updateTestResult(caseExecution, testPassedAtomic.get(), testSteps, testDurationAtomic.get(), browserName, platform);
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

    /**
     * Registra un paso parcial con sincronización.
     */
    private void logPartialStep(String status, String description, String... descriptionArgs) {
        stepLock.writeLock().lock();
        try {
            Step step = new Step(null, (testSteps.size() + 1) + "_" + (currentStep.size() + 1), status, description);
            step.setDescriptionArgs(descriptionArgs);
            currentStep.add(step);
        } finally {
            stepLock.writeLock().unlock();
        }
    }

    /**
     * Registra un paso parcial con screenshot y sincronización.
     */
    private void logPartialStepWithScreenshot(String status, String description, String... descriptionArgs) {
        stepLock.writeLock().lock();
        try {
            Step step = new Step(null, (testSteps.size() + 1) + "_" + (currentStep.size() + 1), status, description);
            step.setDescriptionArgs(descriptionArgs);
            createScreenshot(step);
            currentStep.add(step);
        } finally {
            stepLock.writeLock().unlock();
        }
    }

    /**
     * Registra un paso completo con sincronización.
     * Usa ReadWriteLock para evitar race conditions en operaciones compuestas.
     */
    private Step logStep(String status, String description, String... descriptionArgs) {
        stepLock.writeLock().lock();
        try {
            Step step = new Step(null, String.valueOf(testSteps.size() + 1), status, description);
            step.setDescriptionArgs(descriptionArgs);
            setStepDuration(step);

            currentStep.add(step);

            // Crear copia inmutable antes de agregar a testSteps
            List<Step> completedStep = new ArrayList<>(currentStep);
            testSteps.add(completedStep);
            evidences.insertStep(completedStep);

            // Limpiar currentStep para el siguiente paso
            currentStep.clear();

            if (Status.FAILED.equals(status)) {
                setExecutionNotes(getMessage(description, descriptionArgs));
            }

            // Sincronizar con campo público para compatibilidad
            testPassed = testPassedAtomic.get();

            return step;
        } finally {
            stepLock.writeLock().unlock();
        }
    }

    /**
     * Registra un paso completo con screenshot y sincronización.
     */
    private Step logStepWithScreenshot(String status, String description, String... descriptionArgs) {
        stepLock.writeLock().lock();
        try {
            Step step = new Step(null, String.valueOf(testSteps.size() + 1), status, description);
            step.setDescriptionArgs(descriptionArgs);
            setStepDuration(step);

            createScreenshot(step);
            currentStep.add(step);

            // Crear copia inmutable antes de agregar a testSteps
            List<Step> completedStep = new ArrayList<>(currentStep);
            testSteps.add(completedStep);
            evidences.insertStep(completedStep);

            // Limpiar currentStep para el siguiente paso
            currentStep.clear();

            if (Status.FAILED.equals(status)) {
                setExecutionNotes(getMessage(description, descriptionArgs));
            }

            // Sincronizar con campo público para compatibilidad
            testPassed = testPassedAtomic.get();

            return step;
        } finally {
            stepLock.writeLock().unlock();
        }
    }

    /**
     * Calcula la duración del paso de forma thread-safe.
     */
    private void setStepDuration(Step step) {
        long now = System.currentTimeMillis();
        long stepDurationLong = now - stepStartupTime;
        // Eliminado boxing innecesario: (Long.valueOf(...)).intValue()
        int stepDuration = (int) TimeUnit.MILLISECONDS.toSeconds(stepDurationLong);
        step.setDuration(stepDuration);

        // Reset duration for next step
        stepStartupTime = now;
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
        int duration = (int) TimeUnit.MILLISECONDS.toSeconds(testDurationLong);
        testDurationAtomic.set(duration);
    }

    public void startVideo() {
        if (browser != null) {
            evidences.startVideo();
        }
    }

    public void stopVideo() {
        if (browser != null) {
            evidences.stopVideo(testPassedAtomic.get());
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

    /**
     * Creates proxy configuration for network features.
     * @deprecated BrowserMob Proxy removed - use CDP HAR capture instead (ZAH-156)
     * @return null - proxy no longer used
     */
    @Deprecated
    public Proxy createBrowserMobProxy() {
        // BrowserMob Proxy removed - CDP HAR capture handles network features now
        return null;
    }

    /**
     * Stops HAR capture and saves evidence.
     * Now uses CDP-based capture (ZAH-156).
     */
    public void stopBrowserMobProxy() {
        // CDP HAR capture (ZAH-156)
        if (cdpHarCapture != null) {
            try {
                saveCdpHarLog();
            } catch (Exception e) {
                logWarn("Error saving CDP HAR log: {}", e.getMessage());
            }
        }
    }

    /**
     * Saves HAR log using CDP capture (ZAH-156).
     */
    private void saveCdpHarLog() throws IOException {
        if (cdpHarCapture != null && cdpHarCapture.isCapturing()) {
            cdpHarCapture.stopCapture();
            File harFile = new File(evidences.getEvidencesPath() + evidences.getHarLogFileName());
            cdpHarCapture.writeToFile(
                    harFile,
                    zahoriProperties.getHarFilterByUrls(),
                    zahoriProperties.getHarFilterByRequestMethods(),
                    zahoriProperties.getHarFilterByResponseContentTypes()
            );
            logInfo("CDP HAR log saved: {}", harFile.getAbsolutePath());
        }
    }

    /**
     * Starts CDP HAR capture if HAR is enabled.
     * This is the method for Selenium 4+ (ZAH-156).
     */
    private void startCdpHarCaptureIfNeeded() {
        // Only start CDP capture if HAR is enabled
        if (!zahoriProperties.isHarEnabled()) {
            return;
        }

        // Check if driver supports DevTools
        if (driver == null || !(driver instanceof org.openqa.selenium.devtools.HasDevTools)) {
            logWarn("CDP HAR capture not available - driver does not support DevTools");
            return;
        }

        try {
            cdpHarCapture = new CDPHarCapture(driver);

            // Configure extra headers if enabled
            if (zahoriProperties.isAddHeadersEnabled()) {
                cdpHarCapture.setExtraHeaders(zahoriProperties.getHeadersToBeAdded());
            }

            // Configure URL blacklist if enabled
            if (zahoriProperties.isBlackListEnabled()) {
                java.util.List<String> blacklistUrls = new java.util.ArrayList<>(zahoriProperties.getBlackList().values());
                cdpHarCapture.setBlockedUrls(blacklistUrls);
            }

            // Start capture
            cdpHarCapture.startCapture(this.caseExecution.getCas().getName());
            logInfo("CDP HAR capture started for: {}", this.caseExecution.getCas().getName());

        } catch (Exception e) {
            logWarn("Error starting CDP HAR capture: {}", e.getMessage());
        }
    }

    public int getMaxRetries() {
        return retriesDisabledAtomic.get() ? 0 : zahoriProperties.getDefinedRetries();
    }

    public void disableTestRetries() {
        retriesDisabledAtomic.set(true);
        logInfo("zahori.testInfo.execution.retries.disabled");
    }

    public String getDownloadPath() {
        return zahoriProperties.getDownloadPath();
    }

    public void disableUpdateTestResult() {
        updateTestResultDisabledAtomic.set(true);
        logInfo("zahori.testInfo.execution.updatetms.disabled");
    }

    public int getTestDuration() {
        return testDurationAtomic.get();
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

    /**
     * Escribe los pasos a JSON usando ObjectMapper compartido (thread-safe).
     */
    public void writeSteps2Json() {
        ObjectNode json = SHARED_MAPPER.createObjectNode();
        json.put("testName", testCaseName);
        json.put("testStatus", testPassedAtomic.get() ? "PASSED" : "FAILED");
        json.put("executionDate", formatDateForWeb(testId));
        json.put("platform", platform);
        json.put("browserName", browserName);
        json.put("browserVersion", version);
        json.put("bits", bits);
        json.put("durationSeconds", getTestDuration());
        json.put("executionNotes", executionNotes);

        // Crear snapshot de testSteps para serialización thread-safe
        ArrayNode stepsArray = SHARED_MAPPER.valueToTree(new ArrayList<>(testSteps));
        ArrayNode newStepsArray = SHARED_MAPPER.createArrayNode();
        int stepPosition = 1;
        for (JsonNode currentStepList : stepsArray) {
            for (JsonNode step : currentStepList) {
                ObjectNode currentStepNode = (ObjectNode) step;
                String key = step.get("description").asText();
                List<String> argsList = new ArrayList<>();
                for (JsonNode currentArg : step.get("descriptionArgs")) {
                    argsList.add(currentArg.asText());
                }
                String[] args = argsList.toArray(String[]::new);
                currentStepNode.put("messageText", getMessage(key, args));
                currentStepNode.put("name", stepPosition);
                stepPosition++;
                newStepsArray.add(currentStepNode);
            }
        }
        json.set("steps", newStepsArray);
        try {
            SHARED_MAPPER.writeValue(new File(evidences.getPath() + JSON_REPORT), json);
        } catch (IOException e) {
            logInfo("Error writing steps JSON file: " + e.getMessage());
        }
    }

    /**
     * Formatea fecha usando DateTimeFormatter (thread-safe).
     * Reemplaza SimpleDateFormat que NO es thread-safe.
     */
    private String formatDateForWeb(String date) {
        try {
            LocalDateTime parsed = LocalDateTime.parse(date, DATE_FORMATTER);
            return DATE_WEB_FORMATTER.format(parsed);
        } catch (DateTimeParseException e) {
            logError("Error parsing date '" + date + "': " + e.getMessage());
            return "";
        }
    }

    /**
     * Retorna una vista inmutable de los attachments.
     */
    public List<String> getAttachments() {
        return Collections.unmodifiableList(attachments);
    }

    /**
     * Reemplaza todos los attachments (thread-safe).
     */
    public void setAttachments(List<String> newAttachments) {
        attachments.clear();
        if (newAttachments != null) {
            attachments.addAll(newAttachments);
        }
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
            ((IOSDriver) driver).hideKeyboard();
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
        switchToWindowMatching("url", url, () -> this.driver.getCurrentUrl());
    }

    public void switchToWindowWithTitle(String title) {
        switchToWindowMatching("title", title, () -> this.driver.getTitle());
    }

    private void switchToWindowMatching(String type, String searchValue, java.util.function.Supplier<String> valueGetter) {
        logInfo("switching to window with {}: {}", type, searchValue);

        String currentValue = valueGetter.get();
        logInfo("current {}: {}", type, currentValue);
        if (StringUtils.containsIgnoreCase(currentValue, searchValue)) {
            return;
        }

        getPageSource();
        ArrayList<String> windowHandles = new ArrayList<>(this.driver.getWindowHandles());
        logInfo("WindowHandles: {}", windowHandles.toString());

        for (int i = windowHandles.size() - 1; i >= 0; i--) {
            String windowHandle = windowHandles.get(i);
            try {
                logInfo("switching to windowHandle: {}", windowHandle);

                if (isMobileNativeApp()) {
                    switchToWebContext("WEBVIEW_" + windowHandle);
                }

                this.driver.switchTo().window(windowHandle);
                getPageSource();

                if (StringUtils.containsIgnoreCase(valueGetter.get(), searchValue)) {
                    return;
                }
            } catch (Exception e) {
                logError("Error switchToWindowWith{}({}): {}", type, searchValue, e.getMessage());
            }
        }

        throw new RuntimeException("Window containing " + type + " '" + searchValue + "' not found");
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

    public void switchToWebContext() {
        switchToWebContext(null);
    }

    public void switchToWebContext(String contextName) {
        switchToMobileWebContext(contextName);

        String context = getCurrentContext();
        int switchRetries = 0;
        while (StringUtils.contains(context, "NATIVE") && switchRetries <= timeoutFindElement) {
            switchRetries++;
            Pause.sleep(Duration.ofSeconds(1));
            logDebug("Waiting 1 second to find a webview...");

            switchToMobileWebContext(contextName);
            context = getCurrentContext();
        }
        if (StringUtils.contains(context, "NATIVE")) {
            logStepFailedWithScreenshot("Webview not found");
        }
    }

    public String getCurrentContext() {
        if (isAndroidDriver()) {
            return ((AndroidDriver) driver).getContext();
        }
        if (isIOSDriver()) {
            return ((IOSDriver) driver).getContext();
        }
        throw new RuntimeException("Method getCurrentContext() only supported for AndroidDriver and IOSDriver");
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
        logInfo("switchToWebContextAndroid({})", contextName);
        getPageSource();
        // logInfo("getContextHandles...");
        ArrayList<String> contexts = new ArrayList<>(androidDriver.getContextHandles());
        logInfo("getContextHandles: {}", contexts.toString());

        for (int i = contexts.size() - 1; i >= 0; i--) {
            String context = contexts.get(i);

            if (StringUtils.isNotBlank(contextName) && context.equalsIgnoreCase(contextName)) {
                logInfo("switching to context: {}", context);
                androidDriver.context(context);
                return;
            }
            if (StringUtils.isBlank(contextName) && !context.contains("NATIVE")) {
                // For Webviews and browser contexts (CHROMIUM, WEBVIEW_org.mozilla.firefox, ...)
                logInfo("switching to context: {}", context);
                androidDriver.context(context);
                return;
            }
        }
        logInfo("Webview not found: {}", contextName);
    }

    private void switchToWebContextIOS(IOSDriver iOSDriver, String contextName) {
        logInfo("switchToWebContextIOS({})", contextName);
        getPageSource();
        // logInfo("getContextHandles...");
        ArrayList<String> contexts = new ArrayList<>(iOSDriver.getContextHandles());
        logInfo("getContextHandles: {}", contexts.toString());

        for (int i = contexts.size() - 1; i >= 0; i--) {
            String context = contexts.get(i);

            if (StringUtils.isNotBlank(contextName) && context.equalsIgnoreCase(contextName)) {
                logInfo("switching to context: {}", context);
                iOSDriver.context(context);
                return;
            }
            if (StringUtils.isBlank(contextName) && !context.contains("NATIVE")) {
                // For Webviews and Safari contexts
                logInfo("switching to context: {}", context);
                iOSDriver.context(context);
                return;
            }
        }
        logInfo("Webview not found: {}", contextName);
    }

    public List<String> getWebContexts() {
        List<String> webContexts = new ArrayList<>();
        if (isAndroidDriver()) {
            AndroidDriver androidDriver = (AndroidDriver) driver;
            webContexts = getWebContextsAndroid(androidDriver);
        }
        if (isIOSDriver()) {
            IOSDriver iosDriver = (IOSDriver) driver;
            webContexts = getWebContextsIOS(iosDriver);
        }
        logInfo("getWebContexts -> {} ", webContexts.toString());
        return webContexts;
    }

    private List<String> getWebContextsAndroid(AndroidDriver androidDriver) {
        List<String> webContexts = new ArrayList<>();
        ArrayList<String> contexts = new ArrayList<>(androidDriver.getContextHandles());
        for (String context : contexts) {
            if (context.contains("WEBVIEW")) {
                webContexts.add(context);
            }
        }
        return webContexts;
    }

    private List<String> getWebContextsIOS(IOSDriver iOSDriver) {
        List<String> webContexts = new ArrayList<>();
        ArrayList<String> contexts = new ArrayList<>(iOSDriver.getContextHandles());
        for (String context : contexts) {
            if (context.contains("WEBVIEW")) {
                webContexts.add(context);
            }
        }
        return webContexts;
    }

    public String getPageSource() {
        String sourceCode = "";

        Chronometer crono = new Chronometer();
        while (StringUtils.isBlank(sourceCode) && crono.getElapsedSeconds() < timeoutFindElement) {
            try {
                // logInfo("getting page source...");
                sourceCode = driver.getPageSource();
            } catch (Exception e) {
                Pause.sleep(Duration.ofSeconds(1));
            }
        }
        // logInfo("page source -> {} ", sourceCode);
        return sourceCode;
    }

}
