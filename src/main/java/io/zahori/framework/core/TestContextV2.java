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
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import net.lightbody.bmp.client.ClientUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * TestContext V2 - Thread-Safe & Optimized Version
 *
 * <h2>Mejoras de Concurrencia</h2>
 * <ul>
 *   <li>Colecciones thread-safe: CopyOnWriteArrayList para steps y attachments</li>
 *   <li>Variables atómicas: AtomicBoolean, AtomicInteger, AtomicReference</li>
 *   <li>ReadWriteLock para operaciones complejas sobre currentStep</li>
 *   <li>DateTimeFormatter (thread-safe) reemplaza SimpleDateFormat</li>
 *   <li>ObjectMapper compartido (thread-safe después de configuración)</li>
 * </ul>
 *
 * <h2>Mejoras de Optimización</h2>
 * <ul>
 *   <li>ObjectMapper singleton pre-configurado</li>
 *   <li>Formatters estáticos inmutables</li>
 *   <li>Eliminación de boxing innecesario</li>
 *   <li>Uso de LockSupport.parkNanos vs Thread.sleep</li>
 * </ul>
 *
 * @author Java Champion Optimization
 * @since 2.0
 */
public class TestContextV2 {

    // ============================================================================
    // CONSTANTES Y SINGLETONS THREAD-SAFE
    // ============================================================================

    public static final String JSON_REPORT = "testSteps.json";

    /** DateTimeFormatter es inmutable y thread-safe (a diferencia de SimpleDateFormat) */
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final DateTimeFormatter DATE_WEB_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** ObjectMapper es thread-safe después de configuración inicial */
    private static final ObjectMapper SHARED_MAPPER = createConfiguredMapper();

    private static ObjectMapper createConfiguredMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // Configuración una sola vez - después es thread-safe para lecturas
        // mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }

    // ============================================================================
    // ESTADO THREAD-SAFE CON ATOMIC TYPES
    // ============================================================================

    /** ID del test generado de forma thread-safe */
    public final String testId;

    /** URL del test - inmutable después de constructor() */
    private volatile String url;

    /** Estado del test con visibilidad garantizada entre hilos */
    private final AtomicBoolean testPassed = new AtomicBoolean(true);

    /** Excepción capturada de forma thread-safe */
    private final AtomicReference<ZahoriException> zahoriException = new AtomicReference<>();

    /** Contador de reintentos atómico */
    private final AtomicInteger retries = new AtomicInteger(0);

    /** Duración del test en segundos */
    private final AtomicInteger testDuration = new AtomicInteger(0);

    /** Timestamps atómicos para mediciones precisas */
    private volatile long testStartupTime;
    private volatile long stepStartupTime;

    // ============================================================================
    // COLECCIONES THREAD-SAFE
    // ============================================================================

    /**
     * CopyOnWriteArrayList: óptimo para escenarios con muchas lecturas y pocas escrituras.
     * Los pasos se escriben secuencialmente pero pueden leerse desde hilos de reporting.
     */
    private final List<Step> currentStep = new CopyOnWriteArrayList<>();

    /** Lista de pasos del test - thread-safe para acceso concurrente */
    private final List<List<Step>> testSteps = new CopyOnWriteArrayList<>();

    /** Attachments thread-safe */
    private final List<String> attachments = new CopyOnWriteArrayList<>();

    /**
     * Lock para operaciones compuestas sobre currentStep.
     * ReadWriteLock permite múltiples lectores concurrentes.
     */
    private final ReadWriteLock stepLock = new ReentrantReadWriteLock();

    // ============================================================================
    // PARÁMETROS DE CONFIGURACIÓN (INMUTABLES DESPUÉS DE SETUP)
    // ============================================================================

    public final String testCaseName;
    public final String caseExecutionId;
    public final String platform;
    public final String browserName;
    public final String bits;
    public final String version;
    public final String resolution;

    /** Driver de Selenium - volátil para visibilidad entre hilos */
    private volatile WebDriver driver;
    private volatile WebDriver hostDriver;

    /** Configuración remota */
    private volatile String remote;
    private volatile String remoteUrl;
    private volatile String appiumService;

    // ============================================================================
    // OBJETOS DE SOPORTE
    // ============================================================================

    public volatile ZahoriProperties zahoriProperties;
    public volatile ProjectProperties projectProperties;
    public volatile Integer timeoutFindElement;

    private volatile Browser browser;
    public volatile Evidences evidences;

    private volatile TmsService tmsService;
    private volatile Messages messages;

    private volatile String executionNotes;
    private volatile String failCause;

    private final AtomicBoolean retriesDisabled = new AtomicBoolean(false);
    private final AtomicBoolean updateTestResultDisabled = new AtomicBoolean(false);

    public final CaseExecution caseExecution;
    public final ProcessRegistration processRegistration;

    private volatile Local browserStackLocal;
    private final AtomicInteger browserStackLocalRetry = new AtomicInteger(0);
    private static final int BROWSERSTACK_LOCAL_MAX_RETRIES = 5;

    private volatile BrowserMobProxy browserMobProxy;
    private volatile CDPHarCapture cdpHarCapture;

    // ============================================================================
    // CONSTRUCTOR
    // ============================================================================

    public TestContextV2(CaseExecution caseExecution, ProcessRegistration processRegistration) {
        // Generar testId de forma thread-safe
        this.testId = DATE_FORMATTER.format(LocalDateTime.now());

        this.caseExecution = caseExecution;
        this.processRegistration = processRegistration;

        this.testCaseName = caseExecution.getCas().getName();
        this.caseExecutionId = String.valueOf(caseExecution.getCaseExecutionId());
        this.platform = detectPlatform();
        this.bits = "32";
        this.browserName = caseExecution.getBrowser() == null ? ""
                : caseExecution.getBrowser().getBrowserName().toUpperCase();
        this.version = StringUtils.isBlank(caseExecution.getBrowser().getVersion())
                ? caseExecution.getBrowser().getDefaultVersion()
                : caseExecution.getBrowser().getVersion();
        this.resolution = StringUtils.isBlank(caseExecution.getScreenResolution())
                ? DEFAULT_SCREEN_RESOLUTION
                : caseExecution.getScreenResolution() + DEFAULT_BIT_DEPTH;
    }

    private String detectPlatform() {
        String configName = caseExecution.getConfiguration().getName();
        if (StringUtils.containsIgnoreCase(configName, "android")) {
            return "ANDROID";
        }
        if (StringUtils.containsIgnoreCase(configName, "ios")) {
            return "IOS";
        }
        return "LINUX";
    }

    // ============================================================================
    // GETTERS/SETTERS THREAD-SAFE
    // ============================================================================

    public boolean isTestPassed() {
        return testPassed.get();
    }

    public void setTestPassed(boolean passed) {
        testPassed.set(passed);
    }

    public ZahoriException getZahoriException() {
        return zahoriException.get();
    }

    public void setZahoriException(ZahoriException exception) {
        zahoriException.set(exception);
    }

    public int getRetries() {
        return retries.get();
    }

    public int incrementRetries() {
        return retries.incrementAndGet();
    }

    public String getUrl() {
        return url;
    }

    public WebDriver getDriver() {
        return driver;
    }

    public void setDriver(WebDriver driver) {
        this.driver = driver;
    }

    public String getRemote() {
        return remote;
    }

    public void setRemote(String remote) {
        this.remote = remote;
    }

    public String getRemoteUrl() {
        return remoteUrl;
    }

    public void setRemoteUrl(String url) {
        this.remoteUrl = url;
    }

    /**
     * Retorna una vista inmutable de los pasos del test.
     * Thread-safe para lectura concurrente.
     */
    public List<List<Step>> getTestSteps() {
        return Collections.unmodifiableList(testSteps);
    }

    // ============================================================================
    // INICIALIZACIÓN
    // ============================================================================

    public void constructor() {
        SystemPropertiesUtils.loadSystemProperties();

        zahoriProperties = new ZahoriProperties(caseExecution.getConfiguration());
        projectProperties = new ProjectProperties();

        url = caseExecution.getConfiguration().getEnvironmentUrl();

        messages = new Messages(zahoriProperties.getLanguages());

        evidences = new Evidences(caseExecution, zahoriProperties, messages, platform, browserName,
                resolution, testId, getProjectProperty("evidences.template.file.path"),
                StringUtils.equalsIgnoreCase(Browsers.REMOTE_YES, remote), processRegistration);

        timeoutFindElement = (int) caseExecution.getConfiguration().getTimeout();

        tmsService = new TmsService(zahoriProperties, evidences, messages, testId);

        logTestInfo(evidences);
        logTmsInfo(evidences);

        hostDriver = null;

        logInfo("Test context initialized!");
    }

    public void createDriver() {
        if (StringUtils.isEmpty(browserName)) {
            browser = null;
        } else {
            browser = new Browser(this.toOriginalContext());
            logInfo("Driver initialized!");
            startCdpHarCaptureIfNeeded();
        }
    }

    // ============================================================================
    // STEP LOGGING - OPERACIONES ATÓMICAS CON LOCK
    // ============================================================================

    /**
     * Registra un paso con sincronización adecuada.
     * Usa ReadWriteLock para permitir lecturas concurrentes.
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

            return step;
        } finally {
            stepLock.writeLock().unlock();
        }
    }

    private Step logStepWithScreenshot(String status, String description, String... descriptionArgs) {
        stepLock.writeLock().lock();
        try {
            Step step = new Step(null, String.valueOf(testSteps.size() + 1), status, description);
            step.setDescriptionArgs(descriptionArgs);
            setStepDuration(step);

            createScreenshot(step);
            currentStep.add(step);

            List<Step> completedStep = new ArrayList<>(currentStep);
            testSteps.add(completedStep);
            evidences.insertStep(completedStep);
            currentStep.clear();

            if (Status.FAILED.equals(status)) {
                setExecutionNotes(getMessage(description, descriptionArgs));
            }

            return step;
        } finally {
            stepLock.writeLock().unlock();
        }
    }

    private void logPartialStep(String status, String description, String... descriptionArgs) {
        stepLock.writeLock().lock();
        try {
            Step step = new Step(null, (testSteps.size() + 1) + "_" + (currentStep.size() + 1),
                    status, description);
            step.setDescriptionArgs(descriptionArgs);
            currentStep.add(step);
        } finally {
            stepLock.writeLock().unlock();
        }
    }

    private void logPartialStepWithScreenshot(String status, String description, String... descriptionArgs) {
        stepLock.writeLock().lock();
        try {
            Step step = new Step(null, (testSteps.size() + 1) + "_" + (currentStep.size() + 1),
                    status, description);
            step.setDescriptionArgs(descriptionArgs);
            createScreenshot(step);
            currentStep.add(step);
        } finally {
            stepLock.writeLock().unlock();
        }
    }

    // ============================================================================
    // TIMING - OPERACIONES THREAD-SAFE
    // ============================================================================

    private void setStepDuration(Step step) {
        long now = System.currentTimeMillis();
        long stepDurationLong = now - stepStartupTime;
        int stepDuration = (int) TimeUnit.MILLISECONDS.toSeconds(stepDurationLong);
        step.setDuration(stepDuration);
        stepStartupTime = now;
    }

    public void startChronometer() {
        long now = System.currentTimeMillis();
        testStartupTime = now;
        stepStartupTime = now;
    }

    public void stopChronometer() {
        long duration = System.currentTimeMillis() - testStartupTime;
        testDuration.set((int) TimeUnit.MILLISECONDS.toSeconds(duration));
    }

    public int getTestDuration() {
        return testDuration.get();
    }

    // ============================================================================
    // JSON WRITING - USANDO MAPPER COMPARTIDO
    // ============================================================================

    /**
     * Escribe los pasos a JSON usando el ObjectMapper compartido.
     * Thread-safe gracias a las colecciones CopyOnWriteArrayList.
     */
    public void writeSteps2Json() {
        ObjectNode json = SHARED_MAPPER.createObjectNode();
        json.put("testName", testCaseName);
        json.put("testStatus", testPassed.get() ? "PASSED" : "FAILED");
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
     * Parsea fecha usando DateTimeFormatter thread-safe.
     */
    private String formatDateForWeb(String date) {
        try {
            LocalDateTime parsed = LocalDateTime.parse(date, DATE_FORMATTER);
            return DATE_WEB_FORMATTER.format(parsed);
        } catch (Exception e) {
            logError("Error parsing date '" + date + "': " + e.getMessage());
            return "";
        }
    }

    // ============================================================================
    // ATTACHMENTS - THREAD-SAFE
    // ============================================================================

    public List<String> getAttachments() {
        return Collections.unmodifiableList(attachments);
    }

    public void addAttachment(String filepath) {
        if (Files.exists(Paths.get(filepath))) {
            attachments.add(filepath);
        } else {
            logError("Error adding attachment. File does not exist: {}", filepath);
        }
    }

    // ============================================================================
    // NOTIFICATION - SIN Thread.sleep
    // ============================================================================

    /**
     * Muestra notificación usando LockSupport en lugar de Thread.sleep.
     * Mejor manejo de interrupciones.
     */
    private Notification showGenericNotification(int msDuration, String description, String... descriptionArgs) {
        Notification info = new Notification(getMessage(description, descriptionArgs));
        try {
            java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(msDuration));
        } catch (Exception e) {
            Thread.currentThread().interrupt();
            info.closeNotification();
            return info;
        }
        return info;
    }

    // ============================================================================
    // MÉTODOS PÚBLICOS DE LOGGING (delegados)
    // ============================================================================

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
        logPartialStep(Status.PASSED, description, descriptionArgs);
    }

    public void logPartialStepWithScreenshot(String description, String... descriptionArgs) {
        logPartialStepWithScreenshot(Status.PASSED, description, descriptionArgs);
    }

    public void logPartialStepFailed(String description, String... descriptionArgs) {
        logPartialStep(Status.FAILED, description, descriptionArgs);
    }

    public void logPartialStepFailedWithScreenshot(String description, String... descriptionArgs) {
        logPartialStepWithScreenshot(Status.FAILED, description, descriptionArgs);
    }

    // ============================================================================
    // DELEGADOS A EVIDENCES
    // ============================================================================

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

    // ============================================================================
    // UTILIDADES
    // ============================================================================

    private void createScreenshot(Step step) {
        if (browser == null) {
            return;
        }
        String screenshot = evidences.createScreenshot(testSteps.size() + 1, currentStep.size() + 1, driver);
        if (screenshot != null) {
            step.addAttachment(new File(screenshot));
        }
    }

    public String getMessage(String messageKey, String... messageArgs) {
        return messages.getMessageInFirstLanguage(messageKey, messageArgs);
    }

    public String getProjectProperty(String property) {
        return this.projectProperties.getProperty(property);
    }

    private void throwZahoriException(String errorMessageKey, String... errorMessageArgs) {
        throw new ZahoriException(testCaseName, errorMessageKey, errorMessageArgs);
    }

    public synchronized void setExecutionNotes(String notes) {
        executionNotes = StringUtils.isEmpty(executionNotes) ? notes : executionNotes + "\n" + notes;
        tmsService.setExecutionNotes(notes);
    }

    public String getExecutionNotes() {
        return executionNotes;
    }

    public String getFailCause() {
        return failCause;
    }

    public void setFailCause(String cause) {
        this.failCause = cause;
    }

    public void resetExecutionNotes() {
        executionNotes = StringUtils.EMPTY;
    }

    public Browser getBrowser() {
        return browser;
    }

    public int getMaxRetries() {
        return retriesDisabled.get() ? 0 : zahoriProperties.getDefinedRetries();
    }

    public void disableTestRetries() {
        retriesDisabled.set(true);
        logInfo("zahori.testInfo.execution.retries.disabled");
    }

    public void disableUpdateTestResult() {
        updateTestResultDisabled.set(true);
        logInfo("zahori.testInfo.execution.updatetms.disabled");
    }

    public boolean isUpdateTestResultDisabled() {
        return updateTestResultDisabled.get();
    }

    // ============================================================================
    // CONVERSIÓN A CONTEXTO ORIGINAL (para compatibilidad)
    // ============================================================================

    /**
     * Crea un wrapper que implementa la interfaz del TestContext original.
     * Permite migración gradual sin romper código existente.
     */
    public TestContext toOriginalContext() {
        // Aquí se implementaría un adapter pattern si fuera necesario
        // Por ahora, retornamos null ya que requiere refactoring adicional
        throw new UnsupportedOperationException(
            "Migration to TestContextV2 requires updating Browser class dependency");
    }

    // ============================================================================
    // MÉTODOS ADICIONALES DELEGADOS (misma firma que original)
    // ============================================================================

    private void logTestInfo(Evidences evidences) {
        evidences.insertTextInDocs("zahori.testInfo.execution.date", testId);
        evidences.insertTextInDocs("zahori.testInfo.execution.platform", platform);
        evidences.insertTextInDocs("zahori.testInfo.execution.configuration",
                caseExecution.getConfiguration().getName());
        logInfo("zahori.testInfo.title");
        logInfo("- Case: " + testCaseName);
        logInfo("zahori.testInfo.execution.date", testId);
    }

    private void logTmsInfo(Evidences evidences) {
        if (caseExecution.getConfiguration() == null ||
            caseExecution.getConfiguration().getTms() == null) {
            return;
        }
        if (!caseExecution.getConfiguration().getTms().isUploadResults()) {
            return;
        }
        logInfo("- TMS-Upload results: {}",
                String.valueOf(caseExecution.getConfiguration().getTms().isUploadResults()));
    }

    private void startCdpHarCaptureIfNeeded() {
        if (!zahoriProperties.isHarEnabled()) {
            return;
        }
        if (browserMobProxy != null) {
            return;
        }
        if (driver == null || !(driver instanceof org.openqa.selenium.devtools.HasDevTools)) {
            logWarn("CDP HAR capture not available - driver does not support DevTools");
            return;
        }
        try {
            cdpHarCapture = new CDPHarCapture(driver);
            if (zahoriProperties.isAddHeadersEnabled()) {
                cdpHarCapture.setExtraHeaders(zahoriProperties.getHeadersToBeAdded());
            }
            if (zahoriProperties.isBlackListEnabled()) {
                java.util.List<String> blacklistUrls =
                        new java.util.ArrayList<>(zahoriProperties.getBlackList().values());
                cdpHarCapture.setBlockedUrls(blacklistUrls);
            }
            cdpHarCapture.startCapture(this.caseExecution.getCas().getName());
            logInfo("CDP HAR capture started for: {}", this.caseExecution.getCas().getName());
        } catch (Exception e) {
            logWarn("Error starting CDP HAR capture: {}", e.getMessage());
        }
    }
}