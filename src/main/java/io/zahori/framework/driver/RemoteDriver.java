package io.zahori.framework.driver;

/*-
 * #%L
 * zahori-framework
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2021 - 2022 PANEL SISTEMAS INFORMATICOS,S.L
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
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.ios.IOSDriver;
import io.zahori.framework.core.ExecutionTarget;
import io.zahori.framework.driver.browserfactory.Browsers;
import io.zahori.framework.driver.gridprovider.GridProvider;
import io.zahori.framework.driver.gridprovider.GridProviderRegistry;
import io.zahori.framework.files.properties.ZahoriProperties;
import io.zahori.framework.utils.CapabilitiesMasker;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.LongSupplier;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.Point;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.AbstractDriverOptions;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.HttpCommandExecutor;
import org.openqa.selenium.remote.LocalFileDetector;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.remote.http.ClientConfig;
import org.openqa.selenium.remote.http.HttpClient;

public class RemoteDriver extends AbstractDriver {

    static final String DRIVER_CREATION_TIMEOUT_PROPERTY = "zahori.test.execution.driverCreationTimeoutSeconds";
    static final String SESSION_REQUEST_TIMEOUT_PROPERTY = "zahori.test.execution.sessionRequestTimeoutSeconds";

    // La cola del grid (Selenoid -limit, paralelos de BrowserStack) puede durar minutos
    static final int DEFAULT_DRIVER_CREATION_TIMEOUT_SECONDS = 3600;

    // Espera en cola de una peticion: no depende del plazo de creacion, que un proceso puede fijar en minutos
    static final int DEFAULT_SESSION_REQUEST_TIMEOUT_SECONDS = 3600;

    static final Duration QUICK_FAILURE = Duration.ofSeconds(5);
    static final Duration FIRST_BACKOFF = Duration.ofSeconds(5);
    static final Duration MAX_BACKOFF = Duration.ofSeconds(60);

    /** Crea la sesion con los clientes HTTP que se le dan. */
    @FunctionalInterface
    interface SessionFactory {
        WebDriver create(URL grid, Capabilities capabilities, HttpClient.Factory clients);
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    // Selenium directo: WebDriverManager no deja dar plazo propio al cliente en remoto
    static final SessionFactory WEB = (grid, capabilities, clients) -> new RemoteWebDriver(
            new HttpCommandExecutor(Map.of(), ClientConfig.defaultConfig().baseUrl(grid), clients), capabilities);
    static final SessionFactory ANDROID = (grid, capabilities, clients) -> new AndroidDriver(grid, clients, capabilities);
    static final SessionFactory IOS = (grid, capabilities, clients) -> new IOSDriver(grid, clients, capabilities);

    private final LongSupplier clock;
    private final Sleeper sleeper;
    private final DoubleSupplier jitter;
    private final HttpClient.Factory httpClients;

    static final Sleeper THREAD_SLEEP = duration -> Thread.sleep(duration.toMillis());
    static final DoubleSupplier JITTER = () -> ThreadLocalRandom.current().nextDouble(0.8, 1.2);

    public RemoteDriver() {
        this(System::currentTimeMillis, THREAD_SLEEP, JITTER, HttpCommandExecutor.getDefaultClientFactory());
    }

    RemoteDriver(LongSupplier clock, Sleeper sleeper, DoubleSupplier jitter, HttpClient.Factory httpClients) {
        this.clock = clock;
        this.sleeper = sleeper;
        this.jitter = jitter;
        this.httpClients = httpClients;
    }

    @Override
    protected WebDriver createWebDriver(Browsers browsers, Proxy proxy) {
        ZahoriProperties properties = new ZahoriProperties(ExecutionTarget.of(browsers.getEnvironmentName(), browsers.getPlatform()));
        Duration total = Duration.ofSeconds(positiveSeconds(properties.getProperty(DRIVER_CREATION_TIMEOUT_PROPERTY),
                DEFAULT_DRIVER_CREATION_TIMEOUT_SECONDS));
        Duration sessionRequest = sessionRequestTimeout(properties.getProperty(SESSION_REQUEST_TIMEOUT_PROPERTY), total);
        String remoteUrl = browsers.getRemoteUrl();
        URL grid = getUrl(remoteUrl);
        // TODO Temporal workaround for Appium driver creation
        if ("ANDROID".equalsIgnoreCase(browsers.getPlatform()) || "IOS".equalsIgnoreCase(browsers.getPlatform())) {
            SessionFactory appium = "ANDROID".equalsIgnoreCase(browsers.getPlatform()) ? ANDROID : IOS;
            return createSession(appium, grid, getAppiumCapabilities(browsers), total, sessionRequest,
                    e -> getExceptionMessage(e, remoteUrl));
        }
        AbstractDriverOptions<?> options = getOptions(browsers, proxy);
        // Sin las capabilities enviadas no hay forma de saber cual rechaza el grid
        return createSession(WEB, grid, options, total, sessionRequest,
                e -> getExceptionMessage(e, remoteUrl) + " Capabilities sent: " + CapabilitiesMasker.mask(options.asMap()));
    }

    /**
     * El plazo total decide si se lanza otro intento; cada peticion espera su tope entero aunque lo
     * rebase, porque abandonarla deja en el grid una sesion huerfana. La cola llena se reintenta;
     * un rechazo que no cambia por reintentar se propaga en cuanto se reconoce.
     */
    WebDriver createSession(SessionFactory factory, URL grid, Capabilities capabilities, Duration total,
            Duration sessionRequest, Function<Exception, String> describe) {
        long deadline = clock.getAsLong() + total.toMillis();
        int quickFailures = 0;
        RuntimeException failure;
        do {
            long attemptStart = clock.getAsLong();
            SessionRequestHttpClientFactory clients = new SessionRequestHttpClientFactory(httpClients, sessionRequest);
            try {
                return factory.create(grid, capabilities, clients);
            } catch (RuntimeException e) {
                clients.closeAll();
                failure = new RuntimeException(describe.apply(e), e);
                if (isDeterministicRejection(failure.getMessage())) {
                    throw failure;
                }
                long now = clock.getAsLong();
                // Un rechazo inmediato (cola llena) no se repite sin pausa contra el grid
                if (now - attemptStart >= QUICK_FAILURE.toMillis()) {
                    quickFailures = 0;
                } else if (now < deadline) {
                    pause(backoff(++quickFailures, deadline - now, jitter.getAsDouble()), failure);
                }
            }
        } while (clock.getAsLong() < deadline);
        throw failure;
    }

    private void pause(Duration duration, RuntimeException failure) {
        try {
            sleeper.sleep(duration);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw failure;
        }
    }

    /** 5 s, 10 s, 20 s... hasta 60 s, con +-20 % para que los hilos no vuelvan a la vez; nunca mas que lo que queda. */
    static Duration backoff(int failures, long remainingMillis, double jitterFactor) {
        long exponential = FIRST_BACKOFF.toMillis() << Math.min(failures - 1, 10);
        long capped = Math.min(exponential, MAX_BACKOFF.toMillis());
        return Duration.ofMillis(Math.min((long) (capped * jitterFactor), remainingMillis));
    }

    /**
     * Distingue el rechazo del que no se sale reintentando -capability invalida, credenciales,
     * host inalcanzable- del transitorio -cola llena, corte de red-, que si se reintenta.
     */
    static boolean isDeterministicRejection(String message) {
        String text = StringUtils.defaultString(message);
        // Cola o paralelos llenos de BrowserStack: se libera al acabar otras sesiones
        if (StringUtils.containsAny(text, "QUEUE_SIZE_EXCEEDED", "QUEUE_TIME_LIMIT_EXCEEDED", "ALL_PARALLELS_IN_USE",
                "All parallel tests are currently in use")) {
            return false;
        }
        return StringUtils.containsAny(text,
                "rejected the session", "Authorization required", "Invalid platformName",
                "Could not find device", "[BROWSERSTACK", "[browserstack.local]",
                "Unknown host", "Connection refused", "not valid");
    }

    static String getExceptionMessage(Exception e, String remoteUrl) {
        String exceptionMessage = getCleanMessage(e.getMessage());

        Throwable cause = e.getCause();
        if (cause != null) {
            exceptionMessage = exceptionMessage + " Causes: ";
        }

        while (cause != null) {
            String causeMessage = getCleanMessage(cause.getMessage());
            if (StringUtils.containsIgnoreCase(causeMessage, "UnknownHostException")) {
                return "Unknown host:" + StringUtils.substringAfter(causeMessage, "UnknownHostException:");
            }
            // Por tipo: el cliente HTTP lanza estas excepciones sin texto cuando el grid no esta
            if (cause instanceof java.net.UnknownHostException) {
                return "Unknown host: " + StringUtils.defaultIfBlank(StringUtils.trim(causeMessage), hostOf(remoteUrl));
            }
            if (isTimeout(cause)) {
                return timeoutMessage(cause, remoteUrl);
            }
            if (cause instanceof java.net.ConnectException
                    || StringUtils.containsIgnoreCase(causeMessage, "Conexión rehusada")
                    || StringUtils.containsIgnoreCase(causeMessage, "Connection refused")) {
                return "Connection refused from remote server " + remoteUrl + ". Check that the remote server is up and there is connectivity with it.";
            }
            // BrowserStack errors:
            if (StringUtils.containsIgnoreCase(remoteUrl, "browserstack")) {
                if (StringUtils.containsIgnoreCase(causeMessage, "Authorization required")) {
                    return "Authorization required: userName and accessKey capabilities are not present";
                }
                if (StringUtils.containsIgnoreCase(causeMessage, "Platform and Browser not valid")) {
                    return "Invalid platformName, deviceName or browserName capabilities.";
                }
                if (StringUtils.containsIgnoreCase(causeMessage, "Could not find device")) {
                    return causeMessage;
                }
                if (StringUtils.containsIgnoreCase(causeMessage, "[browserstack.local]")) {
                    return causeMessage;
                }
                if (StringUtils.containsIgnoreCase(causeMessage, "BROWSERSTACK_INVALID_APP_URL")) {
                    return "Application not present in BrowserStack. Review that the application is uploaded in BrowserStack and the correct url (bs://...) is set in Zahori.";
                }
                if (StringUtils.containsIgnoreCase(causeMessage, "[BROWSERSTACK")) {
                    return causeMessage;
                }
                // El grid contesta 200 con el motivo dentro: sin esto se diluye entre las causas.
                if (StringUtils.containsIgnoreCase(causeMessage, "browserstack.")) {
                    return "BrowserStack rejected the session: " + causeMessage
                            + " (remote server: " + remoteUrl + ")";
                }
            }

            exceptionMessage = exceptionMessage + ". " + causeMessage;
            cause = cause.getCause();
        }
        return exceptionMessage;
    }

    private static String hostOf(String remoteUrl) {
        try {
            return StringUtils.defaultIfBlank(java.net.URI.create(remoteUrl).getHost(), remoteUrl);
        } catch (IllegalArgumentException e) {
            return remoteUrl;
        }
    }

    // Por tipo, no por texto: un rechazo que nombra browserstack.idleTimeout no es una espera agotada
    static boolean isTimeout(Throwable cause) {
        return cause instanceof java.util.concurrent.TimeoutException || cause instanceof org.openqa.selenium.TimeoutException
                || cause instanceof java.net.SocketTimeoutException || cause instanceof java.net.http.HttpTimeoutException;
    }

    // Con la causa raiz: sin ella un rechazo del grid parece una espera
    static String timeoutMessage(Throwable timeout, String remoteUrl) {
        String message = getCleanMessage(timeout.getMessage()) + " (remote server: " + remoteUrl + ")";
        Throwable root = ExceptionUtils.getRootCause(timeout);
        return root == timeout ? message : message + ". Cause: " + getCleanMessage(root.toString());
    }

    /**
     * Sin ajuste, la peticion de sesion espera la hora entera aunque el plazo de creacion sea menor:
     * en la cola de Selenoid o de BrowserStack el cliente no la abandona, y el plazo corto solo decide
     * si se lanza otro intento tras un fallo.
     */
    static Duration sessionRequestTimeout(String configuredValue, Duration total) {
        int byDefault = (int) Math.max(total.getSeconds(), DEFAULT_SESSION_REQUEST_TIMEOUT_SECONDS);
        return Duration.ofSeconds(positiveSeconds(configuredValue, byDefault));
    }

    static int positiveSeconds(String configuredValue, int defaultSeconds) {
        if (StringUtils.isBlank(configuredValue)) {
            return defaultSeconds;
        }
        try {
            int seconds = Integer.parseInt(configuredValue.trim());
            return seconds > 0 ? seconds : defaultSeconds;
        } catch (NumberFormatException e) {
            return defaultSeconds;
        }
    }

    private static String getCleanMessage(String message) {
        return StringUtils.substringBefore(StringUtils.substringBefore(message, "Host info"), "Build info");
    }

    private void resizeWindow(WebDriver driver, Browsers browsers) {
        driver.manage().window().setPosition(new Point(0, 0));

        String resolution = browsers.getScreenResolution();
        Integer width = Integer.valueOf(resolution.split("x")[0]);
        Integer height = Integer.valueOf(resolution.split("x")[1]);
        driver.manage().window().setSize(new Dimension(width, height));
    }

    @Override
    protected void configureWebDriver(WebDriver webDriver, Browsers browsers) {
        if (webDriver instanceof AndroidDriver || webDriver instanceof IOSDriver) {

        } else {
            // Cualquier otra configuración especifica de RemoteDriver

            // Enable file uploads in Selenoid: https://www.selenium.dev/documentation/webdriver/drivers/remote_webdriver/#uploads
            ((RemoteWebDriver) webDriver).setFileDetector(new LocalFileDetector());

            // Browser window size
            // https://aerokube.com/selenoid/latest/#_custom_screen_resolution_screenresolution
            resizeWindow(webDriver, browsers);
            webDriver.manage().window().maximize();
        }
    }

    @Override
    public AbstractDriverOptions<?> getOptions(Browsers browsers, Proxy proxy) {
        AbstractDriverOptions<?> options = super.getOptions(browsers, proxy);
        options.setBrowserVersion(browsers.getVersion());

        resolveGridProvider(browsers).enrichCapabilities(options, browsers);

        return options;
    }

    /**
     * Resolves which {@link GridProvider} this execution's {@code remoteUrl} belongs to
     * (Selenoid, BrowserStack, a 3rd-party one contributed by the consuming process, or the
     * generic fallback), so capability enrichment (bug: {@code selenoid:options} used to be
     * added unconditionally for every remote grid) is delegated to it instead of hardcoded here.
     */
    @Override
    protected GridProvider gridProvider(Browsers browsers) {
        return resolveGridProvider(browsers);
    }

    private GridProvider resolveGridProvider(Browsers browsers) {
        boolean autodetectEnabled = new ZahoriProperties(
                browsers == null ? ExecutionTarget.of(null, null) : ExecutionTarget.of(browsers.getEnvironmentName(), browsers.getPlatform()))
                .isGridProviderAutodetectEnabled();
        String remoteUrl = browsers == null ? null : browsers.getRemoteUrl();
        return GridProviderRegistry.resolve().resolveProvider(remoteUrl, autodetectEnabled);
    }

    // Package-private (not private): exercised directly by RemoteDriverTest without booting a
    // real Appium/BrowserStack session, since it never touches the network.
    DesiredCapabilities getAppiumCapabilities(Browsers browsers) {
        // Set capabilities defined in zahori.properties starting with keys:
        //      zahori.test.capabilities.add.android.
        //      zahori.test.capabilities.add.ios.
        // Con el proveedor, igual que la rama web: sin el, un bloque selenoid:options declarado en
        // zahori.properties viajaba también a BrowserStack en Android e iOS.
        DesiredCapabilities capabilities = CapabilitiesBuilder.getCapabilities(browsers, gridProvider(browsers));

        // TODO: remove below code. This is a temporal solution for mobile testing until mobile functionalities are fully implememted in server and framework
        Object rawBrowserStackOptions = capabilities.getCapability("bstack:options");
        Map<String, Object> browserStackOptions = new HashMap<>();
        if (rawBrowserStackOptions instanceof Map<?, ?>) {
            browserStackOptions = (Map<String, Object>) rawBrowserStackOptions;

            // Temporal fix to increase browserstack idle timeout
            // This capability should be adde in zahori.properties
            // but as it conains a "." it is interpreted as a map
            browserStackOptions.put("idleTimeout", "240");
        }

        // NATIVE APP
        if (StringUtils.startsWithIgnoreCase(browsers.getEnvironmentUrl(), "bs://")
                || capabilities.getCapability("appium:app") != null) {

            // bs:// url is used to indicate the id of the app artifact uploaded in the cloud farm (browserstack, ...)
            if (StringUtils.startsWithIgnoreCase(browsers.getEnvironmentUrl(), "bs://")) {
                // overwrite 'app' capability value defined in zahori.properties with environment url from selected configuration
                capabilities.setCapability("appium:app", browsers.getEnvironmentUrl());
            }

            // Remove specific capabilities for Web apps if present
            if (capabilities.getCapability("browserName") != null) {
                capabilities.setCapability("browserName", (String) null);
            }

        } else { // WEB APP
            // For Web apps in BrowserStack, the capabilities deviceName and platformVersion must be set at bstack:options level
            browserStackOptions.put("deviceName", capabilities.getCapability("appium:deviceName"));
            browserStackOptions.put("platformVersion", capabilities.getCapability("appium:platformVersion"));
        }

        System.out.println("- Appium capabilities: " + CapabilitiesMasker.mask(capabilities.asMap()));

        return capabilities;
    }

    private URL getUrl(String url) {
        try {
            return new URL(url);
        } catch (MalformedURLException ex) {
            throw new RuntimeException("Error parsing URL '" + url + "': " + ex.getMessage());
        }
    }

}
