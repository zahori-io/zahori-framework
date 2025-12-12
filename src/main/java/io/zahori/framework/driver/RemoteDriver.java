package io.zahori.framework.driver;

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
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.ios.IOSDriver;
import io.zahori.framework.driver.browserfactory.Browsers;
import io.zahori.framework.files.properties.ZahoriProperties;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.Point;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.AbstractDriverOptions;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.HttpCommandExecutor;
import org.openqa.selenium.remote.LocalFileDetector;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.remote.http.ClientConfig;

/**
 * Driver remoto para Selenium Grid, Selenoid, BrowserStack, etc.
 *
 * Usa RemoteWebDriver nativo de Selenium (sin WebDriverManager).
 *
 * Configuracion en zahori.properties:
 * - zahori.selenoid.enableVNC=true|false (default: true)
 * - zahori.selenoid.enableVideo=true|false (default: false)
 * - zahori.selenoid.enableLog=true|false (default: false)
 * - zahori.remote.timeout=3600 (segundos para creacion del driver)
 */
public class RemoteDriver extends AbstractDriver {

    private static final Logger LOG = LogManager.getLogger();

    // Propiedades configurables
    private static final String PROP_SELENOID_VNC = "zahori.selenoid.enableVNC";
    private static final String PROP_SELENOID_VIDEO = "zahori.selenoid.enableVideo";
    private static final String PROP_SELENOID_LOG = "zahori.selenoid.enableLog";
    private static final String PROP_REMOTE_TIMEOUT = "zahori.remote.timeout";

    // Valores por defecto
    private static final int DEFAULT_TIMEOUT_SECONDS = 3600; // 60 minutos
    private static final boolean DEFAULT_VNC = true;
    private static final boolean DEFAULT_VIDEO = false;
    private static final boolean DEFAULT_LOG = false;

    @Override
    protected WebDriver createWebDriver(Browsers browsers, Proxy proxy) {
        try {
            // Appium para dispositivos moviles
            if (isAppiumPlatform(browsers)) {
                LOG.info("Creando driver Appium para plataforma: {}", browsers.getPlatform());
                return createAppiumDriver(browsers);
            }

            // Selenium Grid / Selenoid para navegadores web
            LOG.info("Creando driver remoto para {} en {}", browsers.getName(), browsers.getRemoteUrl());
            return createRemoteWebDriver(browsers, proxy);

        } catch (Exception e) {
            throw new RuntimeException(getExceptionMessage(e, browsers), e);
        }
    }

    /**
     * Crea driver remoto usando RemoteWebDriver nativo de Selenium.
     */
    private WebDriver createRemoteWebDriver(Browsers browsers, Proxy proxy) {
        AbstractDriverOptions<?> options = getOptions(browsers, proxy);
        URL remoteUrl = parseUrl(browsers.getRemoteUrl());
        int timeout = getRemoteTimeout();

        // Configurar timeout de conexion
        ClientConfig clientConfig = ClientConfig.defaultConfig()
                .baseUrl(remoteUrl)
                .connectionTimeout(Duration.ofSeconds(timeout))
                .readTimeout(Duration.ofSeconds(timeout));

        HttpCommandExecutor executor = new HttpCommandExecutor(clientConfig);

        return new RemoteWebDriver(executor, options);
    }

    /**
     * Determina si la plataforma es movil (Android/iOS).
     */
    private boolean isAppiumPlatform(Browsers browsers) {
        String platform = browsers.getPlatform();
        return "ANDROID".equalsIgnoreCase(platform) || "IOS".equalsIgnoreCase(platform);
    }

    @Override
    protected void configureWebDriver(WebDriver webDriver, Browsers browsers) {
        if (webDriver instanceof AndroidDriver || webDriver instanceof IOSDriver) {
            // Configuracion especifica para moviles (si es necesaria)
            LOG.debug("Driver movil configurado: {}", webDriver.getClass().getSimpleName());
            return;
        }

        // Configuracion para navegadores web remotos
        ((RemoteWebDriver) webDriver).setFileDetector(new LocalFileDetector());
        webDriver.manage().window().setPosition(new Point(0, 0));
        resizeWindow(webDriver, browsers);
    }

    @Override
    public AbstractDriverOptions<?> getOptions(Browsers browsers, Proxy proxy) {
        AbstractDriverOptions<?> options = super.getOptions(browsers, proxy);
        options.setBrowserVersion(browsers.getVersion());

        // Agregar opciones de Selenoid configurables
        options.setCapability("selenoid:options", buildSelenoidOptions(browsers));

        return options;
    }

    /**
     * Construye las opciones de Selenoid desde zahori.properties.
     * Implements ZAH-158: Selenoid video recording support.
     */
    private Map<String, Object> buildSelenoidOptions(Browsers browsers) {
        ZahoriProperties props = new ZahoriProperties();

        Map<String, Object> selenoidOptions = new HashMap<>();
        selenoidOptions.put("name", browsers.getCaseExecutionId());
        selenoidOptions.put("testName", browsers.getTestName());
        selenoidOptions.put("screenResolution", browsers.getScreenResolution());

        // Opciones configurables
        boolean enableVNC = getBooleanProperty(props, PROP_SELENOID_VNC, DEFAULT_VNC);
        boolean enableVideo = getBooleanProperty(props, PROP_SELENOID_VIDEO, DEFAULT_VIDEO);
        boolean enableLog = getBooleanProperty(props, PROP_SELENOID_LOG, DEFAULT_LOG);

        selenoidOptions.put("enableVNC", enableVNC);
        selenoidOptions.put("enableVideo", enableVideo);
        selenoidOptions.put("enableLog", enableLog);

        // Video name for Selenoid (ZAH-158)
        if (enableVideo) {
            String videoName = buildVideoName(browsers);
            selenoidOptions.put("videoName", videoName);
            LOG.info("Selenoid video recording enabled: {}", videoName);
        }

        LOG.debug("Selenoid options: {}", selenoidOptions);
        return selenoidOptions;
    }

    /**
     * Builds video filename for Selenoid.
     * Format: {caseExecutionId}_{sanitizedTestName}.mp4
     */
    private String buildVideoName(Browsers browsers) {
        String caseId = browsers.getCaseExecutionId();
        String testName = browsers.getTestName();

        // Sanitize test name for filesystem
        String sanitized = testName != null
                ? testName.replaceAll("[^a-zA-Z0-9_-]", "_")
                : "test";

        // Limit length
        if (sanitized.length() > 50) {
            sanitized = sanitized.substring(0, 50);
        }

        return caseId + "_" + sanitized + ".mp4";
    }

    /**
     * Obtiene el timeout para creacion de driver remoto.
     */
    private int getRemoteTimeout() {
        String timeout = new ZahoriProperties().getProperty(PROP_REMOTE_TIMEOUT);
        if (StringUtils.isNotBlank(timeout) && StringUtils.isNumeric(timeout)) {
            return Integer.parseInt(timeout);
        }
        return DEFAULT_TIMEOUT_SECONDS;
    }

    /**
     * Obtiene una propiedad booleana con valor por defecto.
     */
    private boolean getBooleanProperty(ZahoriProperties props, String key, boolean defaultValue) {
        String value = props.getProperty(key);
        if (StringUtils.isBlank(value)) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }

    // ==================== Appium ====================

    /**
     * Crea driver Appium para Android o iOS.
     */
    private WebDriver createAppiumDriver(Browsers browsers) {
        DesiredCapabilities capabilities = buildAppiumCapabilities(browsers);
        URL remoteUrl = parseUrl(browsers.getRemoteUrl());

        if ("ANDROID".equalsIgnoreCase(browsers.getPlatform())) {
            return new AndroidDriver(remoteUrl, capabilities);
        }
        if ("IOS".equalsIgnoreCase(browsers.getPlatform())) {
            return new IOSDriver(remoteUrl, capabilities);
        }

        throw new IllegalArgumentException(
            "Plataforma Appium no soportada: " + browsers.getPlatform() +
            ". Valores validos: Android, iOS");
    }

    /**
     * Construye capabilities para Appium.
     */
    private DesiredCapabilities buildAppiumCapabilities(Browsers browsers) {
        DesiredCapabilities capabilities = CapabilitiesBuilder.getCapabilities(browsers);

        // BrowserStack options
        Map<String, Object> browserStackOptions = getBrowserStackOptions(capabilities);

        // Configurar segun tipo de app (nativa vs web)
        if (isNativeApp(browsers, capabilities)) {
            configureNativeApp(browsers, capabilities);
        } else {
            configureWebApp(capabilities, browserStackOptions);
        }

        LOG.info("Appium capabilities: {}", capabilities);
        return capabilities;
    }

    /**
     * Obtiene las opciones de BrowserStack existentes o crea nuevas.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> getBrowserStackOptions(DesiredCapabilities capabilities) {
        Object rawOptions = capabilities.getCapability("bstack:options");
        Map<String, Object> options = new HashMap<>();

        if (rawOptions instanceof Map<?, ?>) {
            options = (Map<String, Object>) rawOptions;
            // Timeout por defecto para BrowserStack
            options.putIfAbsent("idleTimeout", "240");
        }

        return options;
    }

    /**
     * Determina si es una app nativa (vs web).
     */
    private boolean isNativeApp(Browsers browsers, DesiredCapabilities capabilities) {
        return StringUtils.startsWithIgnoreCase(browsers.getEnvironmentUrl(), "bs://")
                || capabilities.getCapability("appium:app") != null;
    }

    /**
     * Configura capabilities para app nativa.
     */
    private void configureNativeApp(Browsers browsers, DesiredCapabilities capabilities) {
        // URL bs:// indica artifact en BrowserStack
        if (StringUtils.startsWithIgnoreCase(browsers.getEnvironmentUrl(), "bs://")) {
            capabilities.setCapability("appium:app", browsers.getEnvironmentUrl());
        }
        // Eliminar browserName para apps nativas
        if (capabilities.getCapability("browserName") != null) {
            capabilities.setCapability("browserName", (String) null);
        }
    }

    /**
     * Configura capabilities para app web movil.
     */
    private void configureWebApp(DesiredCapabilities capabilities, Map<String, Object> browserStackOptions) {
        // Eliminar app capability para web
        if (capabilities.getCapability("appium:app") != null) {
            capabilities.setCapability("appium:app", (String) null);
        }
        // BrowserStack requiere deviceName y platformVersion en bstack:options
        browserStackOptions.put("deviceName", capabilities.getCapability("appium:deviceName"));
        browserStackOptions.put("platformVersion", capabilities.getCapability("appium:platformVersion"));
    }

    /**
     * Parsea URL validando formato.
     */
    private URL parseUrl(String url) {
        try {
            return new URL(url);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("URL remota invalida: " + url, e);
        }
    }

    // ==================== Manejo de errores ====================

    /**
     * Genera mensaje de error descriptivo analizando las causas.
     */
    private String getExceptionMessage(Exception e, Browsers browsers) {
        StringBuilder message = new StringBuilder(cleanMessage(e.getMessage()));

        Throwable cause = e.getCause();
        while (cause != null) {
            String causeMessage = cleanMessage(cause.getMessage());

            // Errores de conectividad
            if (StringUtils.containsIgnoreCase(causeMessage, "UnknownHostException")) {
                return "Host desconocido: " + StringUtils.substringAfter(causeMessage, "UnknownHostException:");
            }
            if (StringUtils.containsIgnoreCase(causeMessage, "Timeout")) {
                return causeMessage + " (servidor remoto: " + browsers.getRemoteUrl() + ")";
            }
            if (isConnectionRefused(causeMessage)) {
                return "Conexion rechazada desde " + browsers.getRemoteUrl() +
                       ". Verificar que el servidor remoto esta activo y accesible.";
            }

            // Errores de BrowserStack
            String browserStackError = parseBrowserStackError(causeMessage, browsers);
            if (browserStackError != null) {
                return browserStackError;
            }

            message.append(" -> ").append(causeMessage);
            LOG.debug("Causa: {}", causeMessage);
            cause = cause.getCause();
        }

        return message.toString();
    }

    private boolean isConnectionRefused(String message) {
        return StringUtils.containsIgnoreCase(message, "Conexion rehusada")
                || StringUtils.containsIgnoreCase(message, "Connection refused");
    }

    /**
     * Parsea errores especificos de BrowserStack.
     */
    private String parseBrowserStackError(String message, Browsers browsers) {
        if (!StringUtils.containsIgnoreCase(browsers.getRemoteUrl(), "browserstack")) {
            return null;
        }

        if (StringUtils.containsIgnoreCase(message, "Authorization required")) {
            return "Autorizacion requerida: falta userName y accessKey en capabilities";
        }
        if (StringUtils.containsIgnoreCase(message, "Platform and Browser not valid")) {
            return "Capabilities invalidas: verificar platformName, deviceName o browserName";
        }
        if (StringUtils.containsIgnoreCase(message, "Could not find device")) {
            return message;
        }
        if (StringUtils.containsIgnoreCase(message, "[browserstack.local]")) {
            return message;
        }
        if (StringUtils.containsIgnoreCase(message, "BROWSERSTACK_INVALID_APP_URL")) {
            return "App no encontrada en BrowserStack. Verificar que la app esta subida y la URL (bs://...) es correcta";
        }
        if (StringUtils.containsIgnoreCase(message, "[BROWSERSTACK")) {
            return message;
        }

        return null;
    }

    /**
     * Limpia mensaje de error removiendo informacion verbose.
     */
    private String cleanMessage(String message) {
        if (message == null) {
            return "";
        }
        return StringUtils.substringBefore(
                StringUtils.substringBefore(message, "Host info"),
                "Build info");
    }
}
