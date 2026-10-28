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
import io.zahori.framework.driver.gridprovider.GridProvider;
import io.zahori.framework.driver.gridprovider.GridProviderRegistry;
import io.zahori.framework.driver.gridprovider.RequiredCapabilitiesValidator;
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
import io.zahori.framework.utils.Waits;
import io.zahori.framework.utils.WebdriverUtils;
import io.zahori.model.Status;
import io.zahori.model.Step;
import io.zahori.model.process.CaseExecution;
import io.zahori.model.process.ProcessRegistration;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.lightbody.bmp.client.ClientUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

public class TestContext {

    private static final Logger LOG = LogManager.getLogger(TestContext.class);

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
    // Ajuste manual y su driver; null = segun el tipo de driver
    private Boolean nativeContext;
    private WebDriver nativeContextDriver;
    private Duration appliedImplicitWait;
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

    // Single source of truth for "where" this execution runs (environment/platform/grid provider)
    public ExecutionTarget executionTarget;
    private GridProvider gridProvider;

    // Properties
    public ZahoriProperties zahoriProperties;
    public ProjectProperties projectProperties;

    // TEST EXECUTION TIMEOUTS
    public Integer timeoutFindElement;

    /** Property opcional que ajusta el plazo de asentamiento de una casilla. */
    static final String CHECKBOX_SETTLE_PROPERTY = "zahori.test.execution.timeout.checkboxSettle";

    /** Plazo por defecto: lo que tarda un componente en reflejar el clic sin que haya nada raro. */
    static final int DEFAULT_CHECKBOX_SETTLE_SECONDS = 5;

    private int checkboxSettleSeconds = DEFAULT_CHECKBOX_SETTLE_SECONDS;
    private transient BooleanSupplier pageIsSettled;

    /** Property opcional que guarda el DOM de todos los pasos, no solo de los que fallan. */
    static final String DOM_SNAPSHOT_ALWAYS_PROPERTY = "zahori.test.evidences.domSnapshot.always";

    private boolean domSnapshotAlways;

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

    private static final String TUNNEL_PROPERTY_PREFIX = "zahori.test.execution.browserStackLocal.";
    private static final String CAPABILITY_PROPERTY_PREFIX = "zahori.test.capabilities.add.";
    private static final String BSTACK_OPTIONS = ".bstack:options.";
    static final String BROWSERSTACK_LOCAL_BINARY_PATH_PROPERTY ="zahori.test.execution.browserStackLocalBinaryPath";

    // Tuneles del proceso, no del caso; holder para crearlos solo si un caso los pide
    private static final class ProcessTunnels {
        static final BrowserStackLocalTunnelRegistry INSTANCE = BrowserStackLocalTunnelRegistry.forThisProcess();
    }

    // Tunel con cuyo identificador se empareja la sesion de este caso
    private BrowserStackLocalTunnel tunnel;
    // El mismo tunel visto por este caso: sabe relevarlo si BrowserStack no lo ve
    private LocalTunnelLease tunnelLease;

    static final String MISSING_TUNNEL = "BrowserStack local connection is required by zahori.properties but this case has no tunnel:"
            + " the session would ask for a tunnel that is not running";

    private BrowserMobProxy browserMobProxy;

    public TestContext(CaseExecution caseExecution, ProcessRegistration processRegistration) {
        this.caseExecution = caseExecution;
        this.processRegistration = processRegistration;

        testCaseName = caseExecution.getCas().getName();
        caseExecutionId = String.valueOf(caseExecution.getCaseExecutionId());
        platform = getPlatform(); // TODO
        executionTarget = ExecutionTarget.of(caseExecution.getConfiguration() == null ? null : caseExecution.getConfiguration().getEnvironmentName(), platform);
        bits = "32"; // TODO
        browserName = caseExecution.getBrowser() == null ? "" : caseExecution.getBrowser().getBrowserName().toUpperCase();
        browserVersion = StringUtils.isBlank(caseExecution.getBrowser().getVersion()) ? caseExecution.getBrowser().getDefaultVersion()
                : caseExecution.getBrowser().getVersion();
        resolution = StringUtils.isBlank(caseExecution.getScreenResolution()) ? DEFAULT_SCREEN_RESOLUTION
                : caseExecution.getScreenResolution() + DEFAULT_BIT_DEPTH;
    }

    /** Prefix of the optional per-platform remote grid URL override in zahori.properties. */
    static final String REMOTE_URL_PROPERTY_PREFIX = "zahori.test.execution.remoteUrl.";

    /**
     * Resolves which remote grid URL this execution must use.
     *
     * <p>A process gets a single grid URL injected at Spring boot ({@code zahori.selenoid.url}),
     * which forces every platform of that process onto the same grid. This override lets a run
     * route per platform — Android and iOS to BrowserStack while Windows and OSX keep hitting the
     * local Selenoid — by declaring {@code zahori.test.execution.remoteUrl.<platform>} in
     * zahori.properties.
     *
     * <p>Backwards compatible by construction: with no property declared, the injected URL is
     * returned unchanged, so processes that know nothing about this keep working without touching
     * their application.properties. A blank value is treated as "not declared" rather than as
     * "no grid", which would otherwise silently break the run.
     *
     * <p>The platform is lowercased because {@code getPlatform()} is inconsistent about it — it
     * returns "ANDROID" and "IOS" but "windows" and "osx" — and the property key must not depend
     * on that. {@code startRemoteTunnel()} lowercases it for the same reason.
     *
     * @param injectedRemoteUrl  the URL injected at boot, used when there is no override
     * @param platform           this execution's platform, in any casing
     * @param zahoriProperties   properties of the case, already resolved for its environment
     * @return the URL to use, never blank unless the injected one already was
     */
    static String resolveRemoteUrl(String injectedRemoteUrl, String platform, ZahoriProperties zahoriProperties) {
        String platformRemoteUrl = zahoriProperties.getProperty(REMOTE_URL_PROPERTY_PREFIX + StringUtils.lowerCase(platform));
        return StringUtils.isNotBlank(platformRemoteUrl) ? platformRemoteUrl : injectedRemoteUrl;
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
        zahoriProperties = new ZahoriProperties(executionTarget);
        zahoriProperties.setConfiguration(caseExecution.getConfiguration());
        projectProperties = new ProjectProperties();

        String resolvedRemoteUrl = resolveRemoteUrl(remoteUrl, platform, zahoriProperties);
        if (!StringUtils.equals(resolvedRemoteUrl, remoteUrl)) {
            // Logged, and not silently applied: "why did this run go to BrowserStack?" has to be
            // answerable from the log. It goes through the class logger and not through logInfo()
            // because evidences do not exist yet at this point of constructor().
            LOG.info("Remote grid URL overridden for platform '{}': {}", platform, resolvedRemoteUrl);
            remoteUrl = resolvedRemoteUrl;
        }

        // Resolve, once, which grid provider this execution's remoteUrl belongs to (Selenoid,
        // BrowserStack, a 3rd-party one, or the generic fallback) — completes the ExecutionTarget
        // identity and drives the header strategy and the local tunnel.
        gridProvider = GridProviderRegistry.resolve().resolveProvider(remoteUrl, zahoriProperties.isGridProviderAutodetectEnabled());
        executionTarget = executionTarget.withGridProviderId(gridProvider.id());

        // Fail fast (before creating the driver) if this provider/platform combination is
        // missing configuration it genuinely needs, instead of surfacing a cryptic error later.
        RequiredCapabilitiesValidator.validate(gridProvider, executionTarget.environment(), executionTarget.platform(), zahoriProperties);

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
        checkboxSettleSeconds = readCheckboxSettleSeconds();
        domSnapshotAlways = readDomSnapshotAlways();

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
            browser = createBrowser(this::newBrowser, localTunnelRequired(zahoriProperties, gridProvider, processPlatform(), platform));
            logInfo("Driver initialized!");
        }
    }

    Browser newBrowser() {
        return new Browser(this);
    }

    /** La configuracion pide BrowserStack Local en este grid. */
    static boolean localTunnelRequired(ZahoriProperties properties, GridProvider gridProvider, String processPlatform) {
        return localTunnelRequired(properties, gridProvider, processPlatform, null);
    }

    static boolean localTunnelRequired(ZahoriProperties properties, GridProvider gridProvider, String processPlatform,
            String casePlatform) {
        return properties != null
                && shouldStartLocalTunnel(gridProvider, tunnelSetting(properties, "local", processPlatform, casePlatform));
    }

    // Sin tunel la sesion pediria a BrowserStack el identificador de la plantilla, que no levanta nadie
    Browser createBrowser(Supplier<Browser> browserFactory, boolean tunnelRequired) {
        if (tunnelLease == null) {
            if (tunnelRequired) {
                logStepFailed(MISSING_TUNNEL);
            }
            return browserFactory.get();
        }
        try {
            return tunnelLease.create(browserFactory);
        } catch (BrowserStackLocalTunnel.TunnelUnavailableException e) {
            forgetTunnel();
            logStepFailed(e.getMessage());
            return null;
        }
    }

    private void forgetTunnel() {
        tunnel = null;
        tunnelLease = null;
    }

    /** Tunel solo si el grid lo soporta y la ejecucion lo pide con bstack:options.local. */
    static boolean shouldStartLocalTunnel(GridProvider gridProvider, String localConnectionCapability) {
        return gridProvider != null
                && gridProvider.supportsLocalTunnel()
                && Boolean.parseBoolean(localConnectionCapability);
    }

    public void startRemoteTunnel() {
        String processPlatform = processPlatform();
        String browserStackLocalConnection = tunnelSetting(zahoriProperties, "local", processPlatform, platform);
        if (!shouldStartLocalTunnel(gridProvider, browserStackLocalConnection)) {
            LOG.info(tunnelNotStartedReason(gridProvider, browserStackLocalConnection));
            return;
        }

        String accessKey = tunnelSetting(zahoriProperties, "accessKey", processPlatform, platform);
        startRemoteTunnel(ProcessTunnels.INSTANCE, accessKey, zahoriProperties.getProperty(BROWSERSTACK_LOCAL_BINARY_PATH_PROPERTY));
    }

    // En el log del proceso: sin el, un caso sin tunel no dice si fue el grid o el ajuste
    static String tunnelNotStartedReason(GridProvider gridProvider, String localConnectionSetting) {
        String grid = gridProvider == null ? "none resolved"
                : gridProvider.id() + (gridProvider.supportsLocalTunnel() ? "" : " (no local tunnel)");
        return "BrowserStack local connection not started: grid provider " + grid + ", local setting '"
                + localConnectionSetting + "'";
    }

    /** Plataforma de la JVM (windows, osx o linux por defecto): el tunel es del proceso, no del caso. */
    static String processPlatform() {
        return processPlatform(System.getProperty("os.name"));
    }

    static String processPlatform(String osName) {
        if (StringUtils.containsIgnoreCase(osName, "windows")) {
            return "windows";
        }
        if (StringUtils.containsIgnoreCase(osName, "mac") || StringUtils.containsIgnoreCase(osName, "darwin")) {
            return "osx";
        }
        return "linux";
    }

    /** Ajuste del tunel sin plataforma del caso: el propio del proceso o, si falta, el bloque de la plataforma del proceso. */
    static String tunnelSetting(ZahoriProperties properties, String name, String processPlatform) {
        return tunnelSetting(properties, name, processPlatform, null);
    }

    /**
     * Ajuste del tunel: el propio del proceso, si falta el bloque de la plataforma del proceso y, como ultimo recurso, el de
     * la plataforma del caso. Ese ultimo paso es lo que hacian las versiones anteriores: sin el, un servidor Linux con
     * zahori.properties sin ajuste del proceso ni bloque "linux" nunca arrancaba el tunel.
     */
    static String tunnelSetting(ZahoriProperties properties, String name, String processPlatform, String casePlatform) {
        String own = properties.getProperty(TUNNEL_PROPERTY_PREFIX + name);
        if (StringUtils.isNotBlank(own)) {
            return own;
        }
        String ofProcessPlatform = properties.getProperty(CAPABILITY_PROPERTY_PREFIX + processPlatform + BSTACK_OPTIONS + name);
        if (StringUtils.isNotBlank(ofProcessPlatform) || StringUtils.isBlank(casePlatform)) {
            return ofProcessPlatform;
        }
        return properties.getProperty(CAPABILITY_PROPERTY_PREFIX + casePlatform.toLowerCase() + BSTACK_OPTIONS + name);
    }

    // El localIdentifier lo pone el tunel: es del proceso, no de este caso ni de su ejecucion
    void startRemoteTunnel(BrowserStackLocalTunnelRegistry tunnels, String accessKey, String binaryPath) {
        forgetTunnel();
        if (StringUtils.isBlank(accessKey)) {
            logStepFailed("BrowserStack local connection is enabled in zahori.properties but the access key is not defined");
            return;
        }
        Map<String, String> args = BrowserStackLocalTunnelRegistry.argsFor(accessKey, binaryPath);
        BrowserStackLocalTunnel requested = tunnels.tunnelFor(args);
        try {
            tunnelLease = LocalTunnelLease.acquire(requested, args, tunnelLog());
            tunnel = requested;
        } catch (BrowserStackLocalTunnel.TunnelUnavailableException e) {
            logStepFailed(e.getMessage());
        }
    }

    BrowserStackLocalTunnel.TunnelLog tunnelLog() {
        return new BrowserStackLocalTunnel.TunnelLog() {
            @Override
            public void info(String message) {
                logInfo(message);
            }

            @Override
            public void warn(String message) {
                logWarn(message);
            }

            @Override
            public void error(String message) {
                logError(message);
            }
        };
    }

    /** @deprecated no hace nada: el tunel es del proceso, lo usan otras ejecuciones y se para al apagar la JVM. */
    @Deprecated(since = "0.2.25.21_appium", forRemoval = true)
    public void stopRemoteTunnel() {
        LOG.warn("stopRemoteTunnel() ignored: the BrowserStack Local tunnel is shared by the process");
    }

    public boolean isTunnelRunning() {
        return tunnel != null && tunnel.isRunning();
    }

    /** @return identificador del tunel de este caso, o vacio si no usa tunel */
    public String getTunnelLocalIdentifier() {
        return tunnel == null ? "" : tunnel.identifier();
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
        evidences.insertTextInDocs("zahori.testInfo.execution.gridProvider", gridProvider == null ? "" : gridProvider.id());
        evidences.insertTextInDocs("zahori.testInfo.execution.overlays",
                zahoriProperties.getLoadedOverlayFiles().isEmpty() ? "(none)" : String.join(", ", zahoriProperties.getLoadedOverlayFiles()));

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
        logInfo("- Grid provider resolved: " + (gridProvider == null ? "n/a" : gridProvider.id())
                + " (autodetect=" + zahoriProperties.isGridProviderAutodetectEnabled() + ")");
        logInfo("- Property overlays applied: "
                + (zahoriProperties.getLoadedOverlayFiles().isEmpty() ? "(none)" : String.join(", ", zahoriProperties.getLoadedOverlayFiles())));
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

    public void logBrowserStackStatus() {
        try {
            if (!StringUtils.containsIgnoreCase(remoteUrl, "browserstack")) {
                return;
            }

            if (driver == null) {
                logWarn("BrowserStack status not set: driver is null");
                return;
            }

            ObjectMapper mapper = new ObjectMapper();
            JavascriptExecutor jse = (JavascriptExecutor) driver;

            // Setting name of the test
            ObjectNode setNamePayload = mapper.createObjectNode();
            setNamePayload.put("action", "setSessionName");

            ObjectNode setNameArgs = mapper.createObjectNode();
            setNameArgs.put("name", String.valueOf(caseExecution.getCas().getName()));
            setNamePayload.set("arguments", setNameArgs);

            jse.executeScript("browserstack_executor: " + mapper.writeValueAsString(setNamePayload));

            // Setting the status of test as 'passed' or 'failed'
            ObjectNode setStatusPayload = mapper.createObjectNode();
            setStatusPayload.put("action", "setSessionStatus");

            String status = testPassed ? "passed" : "failed";
            String reason = StringUtils.defaultString(executionNotes);
            ObjectNode setStatusArgs = mapper.createObjectNode();
            setStatusArgs.put("status", status);
            setStatusArgs.put("reason", reason);
            setStatusPayload.set("arguments", setStatusArgs);

            jse.executeScript("browserstack_executor: " + mapper.writeValueAsString(setStatusPayload));

        } catch (Exception e) {
            logWarn("Error setting session status in BrowserStack: {}", e.getMessage());
        }
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

        // Por defecto solo en los pasos fallidos: guardarlo en todos son decenas de MB por
        // ejecucion. Con la property puesta se guardan todos, que es como se sacan localizadores
        // del DOM real en vez de a ojo.
        if (Status.FAILED.equals(step.getStatus()) || domSnapshotAlways) {
            String domSnapshot = evidences.createDomSnapshot(testSteps.size() + 1, currentStep.size() + 1,
                    getPageSource(), isMobileNativeApp());
            if (domSnapshot != null) {
                step.addAttachment(new File(domSnapshot));
            }
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

        String ip = browserMobProxyHost(remote);
        if (ip == null) {
            logInfo("Remote browser on grid provider '{}': BrowserMobProxy only runs with a local browser, skipping it.",
                    gridProvider == null ? "unknown" : gridProvider.id());
            return null;
        }

        // BrowserMob proxy
        browserMobProxy = new BrowserMobProxy(zahoriProperties, this);
        browserMobProxy.start();
        browserMobProxy.startHarCapture(this.caseExecution.getCas().getName());

        // Selenium proxy
        Proxy seleniumProxy = ClientUtil.createSeleniumProxy(browserMobProxy.getProxy());

        int port = browserMobProxy.getPort();
        String hostAndPort = ip + ":" + port;
        seleniumProxy.setHttpProxy(hostAndPort);
        seleniumProxy.setSslProxy(hostAndPort);

        logInfo("Defined selenium proxy at {}", hostAndPort);
        logInfo("### Proxy selenium {}-{}-{}, hostAndPort {}", caseExecutionId, browserName, resolution, hostAndPort);

        return seleniumProxy;
    }

    public void stopBrowserMobProxy() {
        if (browserMobProxy != null) {
            try {
                browserMobProxy.stop();
                saveHarLog();
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

    /**
     * @return host con el que el navegador llega al BrowserMobProxy de este proceso, o null si el
     *         navegador es remoto: desde Selenoid o BrowserStack el flujo no avanzaba por el proxy
     */
    static String browserMobProxyHost(String remote) {
        return StringUtils.equalsIgnoreCase(Browsers.REMOTE_YES, remote) ? null : "localhost";
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

            List<String> windowHandles = new ArrayList<>(getWindowHandles());
            for (int i = windowHandles.size() - 1; i >= 0; i--) {
                String windowHandle = windowHandles.get(i);
                try {
                    logInfo("switching to windowHandle: {}", windowHandle);
                    this.driver.switchTo().window(windowHandle);

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

    public String getCurrentWindowHandle() {
        String currentWindowHandle = "";
        try {
            currentWindowHandle = this.driver.getWindowHandle();
            logInfo("getCurrentWindowHandle: {}", currentWindowHandle);
            return currentWindowHandle;
        } catch (Exception e) {
            logError("getCurrentWindowHandle: {}", e.getMessage());
        }
        return currentWindowHandle;
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

    public void switchToWindowHandle(String windowHandle) {
        logInfo("switchToWindowHandle: {}", windowHandle);
        driver.switchTo().window(windowHandle);
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
                    logInfo("switching to windowHandle: {}", windowHandle);
                    this.driver.switchTo().window(windowHandle);

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
        nativeContext = isAndroidDriver() || isIOSDriver();
        nativeContextDriver = driver;
        forgetImplicitWait();
    }

    /**
     * Fija la espera implicita del driver, y sólo si no esta ya en ese valor.
     *
     * <p>El framework la quiere en cero -sincroniza con esperas explicitas, que sondean- pero no
     * basta con ponerla al crear el driver: al conmutar a un webview, los comandos van a otra
     * sesión que no conserva los timeouts, y una busqueda "sin espera" pasa a costar el plazo
     * entero. Medido el 22/09/2026 en Android: tres busquedas de 60 s seguidas en el contexto web.
     *
     * <p>Se recuerda el último valor aplicado para no gastar un comando por comprobación: con la
     * implicita siempre en cero, esto manda una sola petición después de cada cambio de contexto.
     *
     * @param timeout espera implicita que debe quedar activa
     */
    public void applyImplicitWait(Duration timeout) {
        if (timeout.equals(appliedImplicitWait)) {
            return;
        }
        driver.manage().timeouts().implicitlyWait(timeout);
        appliedImplicitWait = timeout;
    }

    /**
     * Plazo para que una casilla refleje el clic; agotarlo es que el clic no la ha cambiado.
     *
     * @return el plazo en segundos
     */
    public int getCheckboxSettleSeconds() {
        return checkboxSettleSeconds;
    }

    /**
     * Ajusta ese plazo. Un componente lento puede necesitar más; una prueba que ejercita el camino
     * de fallo, mucho menos: esperarlo de verdad multiplica lo que tarda la bateria.
     *
     * @param seconds plazo en segundos, ignorado si es negativo
     */
    public void setCheckboxSettleSeconds(int seconds) {
        if (seconds >= 0) {
            checkboxSettleSeconds = seconds;
        }
    }

    /**
     * Registra cuando la pagina esta quieta. El framework la consulta despues de cada interaccion,
     * porque en una SPA el indicador de carga sale al pulsar y actuar entonces va sobre la pantalla
     * anterior. Sin registrar nada no se consulta nada: el comportamiento no cambia.
     *
     * @param pageIsSettled devuelve true cuando se puede seguir
     */
    public void setPageSettledCheck(BooleanSupplier pageIsSettled) {
        this.pageIsSettled = pageIsSettled;
    }

    /** Espera a que la pagina quede quieta. Sin check registrado vuelve sin hacer nada. */
    public void waitPageSettled() {
        if (pageIsSettled == null) {
            return;
        }
        Waits.until(pageIsSettled, timeoutFindElement);
    }

    private boolean readDomSnapshotAlways() {
        return zahoriProperties != null
                && Boolean.parseBoolean(StringUtils.trimToEmpty(
                        zahoriProperties.getProperty(DOM_SNAPSHOT_ALWAYS_PROPERTY)));
    }

    /** Guarda el DOM de todos los pasos. Vale para recoger localizadores contra la pagina real. */
    public void setDomSnapshotAlways(boolean always) {
        this.domSnapshotAlways = always;
    }

    public boolean isDomSnapshotAlways() {
        return domSnapshotAlways;
    }

    private int readCheckboxSettleSeconds() {
        String configured = zahoriProperties == null ? "" : zahoriProperties.getProperty(CHECKBOX_SETTLE_PROPERTY);
        if (StringUtils.isBlank(configured)) {
            return DEFAULT_CHECKBOX_SETTLE_SECONDS;
        }
        try {
            int seconds = Integer.parseInt(configured.trim());
            return seconds >= 0 ? seconds : DEFAULT_CHECKBOX_SETTLE_SECONDS;
        } catch (NumberFormatException notANumber) {
            logWarn("Property {} is not a number ({}): using the default of {} s",
                    CHECKBOX_SETTLE_PROPERTY, configured, String.valueOf(DEFAULT_CHECKBOX_SETTLE_SECONDS));
            return DEFAULT_CHECKBOX_SETTLE_SECONDS;
        }
    }

    /** Tras conmutar de contexto, lo que el driver tenga fijado deja de ser lo que creemos. */
    private void forgetImplicitWait() {
        appliedImplicitWait = null;
    }

    /**
     * Si el driver esta en el contexto nativo de un movil, donde no hay DOM ni motor de javascript.
     *
     * <p>Lo recuerda quien lo cambia en vez de preguntarlo al driver: {@code getCurrentContext()}
     * es una ida y vuelta de decenas de milisegundos, y esto se consulta antes de cada
     * interacción. Para un driver de escritorio es siempre falso.
     *
     * @return {@code true} si la ultima conmutacion de este driver lo dejo en contexto nativo o,
     *         sin conmutacion, si es una app movil nativa
     */
    public boolean isNativeContext() {
        // Una app nativa arranca en NATIVE_APP sin pasar por switchToNativeContext
        if (nativeContext != null && nativeContextDriver == driver) {
            return nativeContext;
        }
        return isMobileNativeApp();
    }

    public void switchToWebContext() {
        switchToWebContext(null);
    }

    public void switchToWebContext(String contextName) {
        if (StringUtils.startsWithIgnoreCase(contextName, "NATIVE")) {
            switchToNativeContext();
            return;
        }

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
        nativeContext = false;
        nativeContextDriver = driver;
        forgetImplicitWait();
        //getPageSource();
    }

    private void switchToWebContextAndroid(AndroidDriver androidDriver, String contextName) {
        if (StringUtils.isNotBlank(contextName)) {
            try {
                logInfo("switching to context: {}", contextName);
                androidDriver.context(contextName);
                // getCurrentUrl();
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
                // getCurrentUrl();
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
            if (!context.contains("NATIVE")) {
                webContexts.add(context);
            }
        }
        return webContexts;
    }

    private List<String> getWebContextsIOS(IOSDriver iOSDriver) {
        List<String> webContexts = new ArrayList<>();
        Set<String> contexts = iOSDriver.getContextHandles();
        for (String context : contexts) {
            if (!context.contains("NATIVE")) {
                webContexts.add(context);
            }
        }
        return webContexts;
    }

    public String getPageSource() {
        String sourceCode = "";

        try {
            sourceCode = driver.getPageSource();
        } catch (Exception e) {
            logError("getPageSource error: {} ", e.getMessage());
        }
        // logInfo("page source -> {} ", sourceCode);
        return sourceCode;
    }

    public String getCurrentUrl() {
        if (StringUtils.startsWithIgnoreCase(getCurrentContext(), "NATIVE")) {
            return "";
        }

        String currentUrl = "";

        if (isMobileDriver()) {
            Object currentUrlObject = null;
            try {
                currentUrlObject = ((JavascriptExecutor) driver).executeScript("return window.location.href;");
                currentUrl = (String) currentUrlObject;
                logInfo("getCurrentUrl (javascript): {}", currentUrl);
                return currentUrl;
            } catch (Exception ex) {
                try {
                    // Parse error
                    if (currentUrlObject instanceof LinkedHashMap currentUrlMap) {
                        logError("getCurrentUrl (javascript): {}", currentUrlMap.toString());
                    }
                } catch (Exception exc) {
                    logError("getCurrentUrl (javascript) error: {}", exc.getMessage());
                } finally {
                    return "";
                }
            }
        }

        try {
            currentUrl = new WebDriverWait(driver, Duration.ofSeconds(5))
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
