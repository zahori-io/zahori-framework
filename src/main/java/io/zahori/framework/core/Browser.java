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
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.ios.IOSDriver;
import io.zahori.framework.driver.browserfactory.Browsers;
import io.zahori.framework.driver.browserfactory.WebDriverBrowserSelenium;
import io.zahori.framework.utils.Chronometer;
import io.zahori.framework.utils.Pause;
import io.zahori.framework.utils.selenium4.BiDiNetworkUtils;
import io.zahori.framework.utils.selenium4.BiDiNetworkUtils.CapturedRequest;
import io.zahori.framework.utils.selenium4.BiDiNetworkUtils.CapturedResponse;
import io.zahori.framework.utils.selenium4.BrowserLogCapture;
import io.zahori.framework.utils.selenium4.BrowserLogCapture.LogLevel;
import io.zahori.framework.utils.selenium4.ChromeDevToolsUtils;
import io.zahori.framework.utils.selenium4.ChromeDevToolsUtils.CPUThrottling;
import io.zahori.framework.utils.selenium4.ChromeDevToolsUtils.NetworkCondition;
import io.zahori.framework.utils.selenium4.NetworkInterceptor;
import io.zahori.framework.utils.selenium4.PrintToPDFUtils;
import io.zahori.framework.utils.selenium4.PrintToPDFUtils.PageFormat;
import io.zahori.framework.utils.selenium4.VirtualAuthenticatorUtils;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.print.PrintOptions;
import org.openqa.selenium.virtualauthenticator.Credential;
import org.openqa.selenium.virtualauthenticator.VirtualAuthenticator;

/**
 * Clase principal para gestionar el navegador en tests Zahori.
 *
 * <h2>Selenium 4 Features integradas:</h2>
 * <ul>
 *   <li><b>Network Monitoring</b>: Interceptar requests/responses via WebDriver BiDi</li>
 *   <li><b>PDF Generation</b>: Imprimir páginas a PDF con opciones configurables</li>
 *   <li><b>Browser Logs</b>: Capturar logs de consola y errores JavaScript</li>
 *   <li><b>Device Emulation</b>: Simular dispositivos móviles (iPhone, Pixel, iPad)</li>
 *   <li><b>Network Emulation</b>: Simular condiciones de red (3G, offline)</li>
 *   <li><b>Geolocation Mock</b>: Simular ubicaciones geográficas</li>
 *   <li><b>WebAuthn</b>: Testing de autenticación biométrica/FIDO2</li>
 * </ul>
 *
 * <h2>Ejemplo de uso - Network Monitoring:</h2>
 * <pre>{@code
 * browser.startNetworkCapture();
 * loginButton.click();
 * Optional<CapturedResponse> response = browser.waitForResponse("/api/login", 10);
 * assertTrue(response.isPresent());
 * assertEquals(200, response.get().statusCode());
 * browser.stopNetworkCapture();
 * }</pre>
 *
 * <h2>Ejemplo de uso - Device Emulation:</h2>
 * <pre>{@code
 * browser.emulateIPhone12();
 * browser.loadPage("https://example.com");
 * // Test responsive design...
 * browser.clearDeviceEmulation();
 * }</pre>
 */
public class Browser {

    private static final Logger LOG = LogManager.getLogger(Browser.class);
    public static final String LOADING_PAGE = "Loading page: ";

    private WebDriver driver;
    public TestContext testContext;
    private WebDriverBrowserSelenium wbs;
    private Map<String, WebDriver> allDrivers;
    private String mainDriverHandle;
    private String activeDriverHandle;

    private static final String TXT_TITLE = "TITLE";
    private static final String TXT_URL = "URL";
    private static final String PLATFORM_WINDOWS = "windows";

    // ========================================================================
    // SELENIUM 4 UTILITIES - Lazy Initialized
    // ========================================================================
    private NetworkInterceptor networkInterceptor;
    private BrowserLogCapture browserLogCapture;
    private VirtualAuthenticator virtualAuthenticator;

    public Browser(TestContext testContext) {
        this.testContext = testContext;
        createDriver();
        if (!(driver instanceof AndroidDriver || driver instanceof IOSDriver)) {
            mainDriverHandle = driver.getWindowHandle();
        }
        activeDriverHandle = mainDriverHandle;
        this.allDrivers = new HashMap<>();
        allDrivers.put(mainDriverHandle, driver);
    }

    public Browser(TestContext testContext, WebDriver driver) {
        this.testContext = testContext;
        this.driver = driver;
    }

    public void close() {
        close(true);
    }

    public void closeWithoutProcessKill() {
        close(false);
    }

    private void close(boolean killProcess) {
        // Cleanup Selenium 4 utilities
        cleanupSelenium4Utilities();

        try {
            if (allDrivers.keySet().isEmpty()) {
                if (driver != null) {
                    driver.quit();
                }
            } else {
                for (String currentKey : allDrivers.keySet()) {
                    if (allDrivers.get(currentKey) != null) {
                        allDrivers.get(currentKey).quit();
                    }
                }
                allDrivers = new HashMap<>();
            }

        } catch (Exception e) {
            testContext.logWarn("A problem has been detected while closing current browser window.");
        }

        driver = null;

        if (StringUtils.equalsIgnoreCase(PLATFORM_WINDOWS, testContext.platform) && killProcess) {
            killProcess("IEDriverServer*");
            killProcess("chromedriver*");
            killProcess("WerFault.exe");
        }
    }

    /**
     * Limpia recursos de utilidades Selenium 4.
     */
    private void cleanupSelenium4Utilities() {
        try {
            if (networkInterceptor != null) {
                networkInterceptor.stopCapturing();
                networkInterceptor = null;
            }
            if (browserLogCapture != null) {
                browserLogCapture.stopCapturing();
                browserLogCapture = null;
            }
            if (virtualAuthenticator != null && driver != null) {
                VirtualAuthenticatorUtils.removeAuthenticator(driver, virtualAuthenticator);
                virtualAuthenticator = null;
            }
        } catch (Exception e) {
            LOG.warn("Error cleaning up Selenium 4 utilities: {}", e.getMessage());
        }
    }

    public void loadPage(String url) {
        createDriver();
        driver.get(url);
        testContext.logInfo(LOADING_PAGE + url);
        setZoomTo100();
    }

    public void reloadPage() {
        if (driver != null) {
            loadPage(driver.getCurrentUrl());
        }
    }

    public String getBrowserUrl() {
        String url = driver.getCurrentUrl();
        testContext.logInfo("Current page: " + url);
        return url;
    }

    /**
     * Carga una página usando un proxy HTTP.
     * El proxy se configura al crear el driver (no después de un timeout).
     *
     * @param url URL a cargar
     * @param urlProxy URL del proxy (host:port)
     * @param user Usuario del proxy
     * @param password Contraseña del proxy
     */
    public void loadPageWithProxy(String url, String urlProxy, String user, String password) {
        // Configurar proxy antes de crear el driver
        String stringProxy = user + ":" + password + "@" + urlProxy;
        Proxy proxy = new Proxy();
        proxy.setHttpProxy(stringProxy);

        // Crear driver con proxy configurado usando la configuración existente
        Browsers browsersConfig = wbs.getNavega();
        wbs = new WebDriverBrowserSelenium(browsersConfig, proxy);
        driver = wbs.getWebDriver();
        testContext.driver = driver;

        driver.get(url);
        testContext.logInfo(LOADING_PAGE + url);
        setZoomTo100();
    }

    public void loadPageWithCertificate(String url, int numCertificate) {
        driver.get(url);
        testContext.logInfo(LOADING_PAGE + url);
        selectCertificate(numCertificate);
        setZoomTo100();
    }

    private void createDriver() {
        if (driver == null) {
            if (StringUtils.equalsIgnoreCase(PLATFORM_WINDOWS, testContext.platform)) {
                killProcess("WerFault.exe");
            }

            Browsers browsers = new Browsers().withName(testContext.browserName).withBits(testContext.bits).withPlatform(testContext.platform)
                    .withVersion(testContext.version).withScreenResolution(testContext.resolution).withRemote(testContext.remote)
                    .withTestName(testContext.testCaseName).withRemoteUrl(testContext.remoteUrl).withCaseExecution(testContext.caseExecutionId)
                    .withExecution(testContext.caseExecution.getExecutionId())
                    .withPageLoadTimeout(testContext.timeoutFindElement.longValue()) // TODO
                    .withImplicitlyWait(testContext.timeoutFindElement.longValue())
                    // TODO: remove withEnvironmentUrl. This is a temporal solution for mobile testing.
                    // This url is used to indicate the id of the app artifact uploaded in the cloud farm (browserstack, ...)
                    .withEnvironmentUrl(testContext.caseExecution.getConfiguration().getEnvironmentUrl())
                    .withEnvironmentName(testContext.caseExecution.getConfiguration().getEnvironmentName());

            browsers = StringUtils.isEmpty(testContext.getDownloadPath()) ? browsers : browsers.withDownloadPath(testContext.getDownloadPath());

            // BrowserMob Proxy removed - CDP HAR capture handles network features (ZAH-156)
            this.wbs = new WebDriverBrowserSelenium(browsers);

            this.driver = wbs.getWebDriver();

            this.testContext.driver = driver;
        }
    }

    public WebDriver createNewWindow() {
        WebDriver newDriver = wbs.getWebDriver();
        activeDriverHandle = newDriver.getWindowHandle();
        allDrivers.put(activeDriverHandle, newDriver);
        testContext.driver = newDriver;
        this.driver = newDriver;
        testContext.logInfo("info.zahori.browser.createnewwindow", activeDriverHandle);
        return newDriver;
    }

    public WebDriver closeWindow() {
        testContext.driver = allDrivers.get(mainDriverHandle);
        this.driver = testContext.driver;
        allDrivers.get(activeDriverHandle).quit();
        allDrivers.remove(activeDriverHandle);
        testContext.logInfo("info.zahori.browser.closewindow.closedbrowser", activeDriverHandle);

        activeDriverHandle = mainDriverHandle;
        testContext.logInfo("info.zahori.browser.closewindow.activebrowser", activeDriverHandle);
        return allDrivers.get(mainDriverHandle);
    }

    public String getActiveWindowHandleID() {
        return activeDriverHandle;
    }

    public void setActiveWindow(String handleID) {
        testContext.driver = allDrivers.get(handleID);
        this.driver = testContext.driver;
    }

    public void activateIECompatibilityMode() {
        try {

            // String browserWindow = driver.getTitle();//Return a string of
            // alphanumeric window handle
            final Robot robot = new Robot();
            robot.keyPress(KeyEvent.VK_F12);
            robot.keyRelease(KeyEvent.VK_F12);

            // Wait for developer tools to be opened
            Pause.sleep(Duration.ofSeconds(4));

            testContext.logInfo("Enabling IE compatibility mode");

            // driver.switchTo().window(browserWindow);
            robot.keyPress(KeyEvent.VK_ALT);
            robot.keyPress(KeyEvent.VK_TAB);
            robot.keyRelease(KeyEvent.VK_TAB);
            robot.keyRelease(KeyEvent.VK_ALT);

            Pause.sleep(Duration.ofSeconds(4));

        } catch (final Exception e) {
            throw new RuntimeException("Error activating IE compatibility mode: " + e.getMessage());
        }
    }

    public String switchBrowserWindowByTitle(String windowTitle, boolean newWindow) {
        return switchBrowserWindowByParm(TXT_TITLE, windowTitle, newWindow);
    }

    public String switchBrowserWindowByURL(String windowURL, boolean newWindow) {
        return switchBrowserWindowByParm(TXT_URL, windowURL, newWindow);
    }

    public String switchBrowserWindowByHandle(String windowHandle) {
        // Store the current window handle.
        String windowHandleBefore = driver.getWindowHandle();

        // Switch to desired window.
        driver.switchTo().window(windowHandle);

        // Returns the before window handle.
        return windowHandleBefore;
    }

    private void selectCertificate(int numCertificado) {
        try {

            final Page certificateErrorPO = new Page(testContext);

            final PageElement avisoIEVayaAEsteSitioWeb = new PageElement(certificateErrorPO, "Link aviso IE 'Vaya a este sitio web'",
                    Locator.name("overridelink"));
            if (avisoIEVayaAEsteSitioWeb.isVisible()) {
                avisoIEVayaAEsteSitioWeb.click();
            }

            Pause.sleep(Duration.ofSeconds(4));

            final Robot robot = new Robot();
            for (int i = 1; i < numCertificado; i++) {
                robot.keyPress(KeyEvent.VK_DOWN);
                robot.keyRelease(KeyEvent.VK_DOWN);
            }

            testContext.logInfo("Select certificate " + numCertificado);

            Pause.sleep(Duration.ofSeconds(1));

            robot.keyPress(KeyEvent.VK_ENTER);
            robot.keyRelease(KeyEvent.VK_ENTER);

            Pause.sleep(Duration.ofSeconds(4));

        } catch (final Exception e) {
            throw new RuntimeException("Error selecting certificate: " + e.getMessage());
        }
    }

    public void setZoom(int zoomPercent) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            js.executeScript("document.body.style.zoom='" + zoomPercent + "%'");
        } catch (final Exception e) {
            testContext.logWarn("Error setting browser zoom to 100%");
        }
    }

    public void setZoomTo100() {
        setZoom(100);
    }

    private void killProcess(String nombreProceso) {
        if (StringUtils.equalsIgnoreCase(PLATFORM_WINDOWS, testContext.platform)) {
            // Validate process name to prevent command injection
            if (!nombreProceso.matches("^[a-zA-Z0-9_.\\-]+$")) {
                LOG.error("Invalid process name: {}", nombreProceso);
                return;
            }
            try {
                ProcessBuilder pb = new ProcessBuilder("taskkill", "/f", "/im", nombreProceso);
                Process proceso = pb.start();
                proceso.waitFor();
                if (proceso.exitValue() == 0) {
                    LOG.debug("Se ha matado correctamente el proceso: {}", nombreProceso);
                }
            } catch (IOException | InterruptedException e) {
                LOG.error("Error intentando matar el proceso {}: {}", nombreProceso, e.getMessage());
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private String switchBrowserWindowByParm(String parm, String option, boolean newWindow) {
        // Store the current window handle
        String windowHandleBefore = driver.getWindowHandle();

        // Search the desired window.
        List<String> windowHandles = new ArrayList<>(driver.getWindowHandles());
        Pause.sleep(Duration.ofMillis(Pause.LONGSLEEPTIME));
        if (newWindow) {
            int actualSize = windowHandles.size();
            Chronometer crono = new Chronometer();
            while ((windowHandles.size() == actualSize) && (crono.getElapsedSeconds() < testContext.timeoutFindElement)) {
                Pause.sleep(Duration.ofMillis(Pause.SHORTSLEEPTIME));
                windowHandles = new ArrayList<>(driver.getWindowHandles());
            }
        }

        boolean found = false;
        int i = 0;
        while (!found && (i < windowHandles.size())) {
            driver.switchTo().window(windowHandles.get(i));
            switch (parm) {
                case TXT_TITLE:
                    found = driver.getTitle().contains(option);
                    break;
                case TXT_URL:
                    found = driver.getCurrentUrl().contains(option);
                    break;
                default:
                    found = false;
                    break;
            }
            i++;
        }

        // Returns the before window handle if it has found the desired window.
        // Null in other case.
        if (found) {
            return windowHandleBefore;
        } else {
            return null;
        }
    }

    // ========================================================================
    // SELENIUM 4: NETWORK MONITORING (WebDriver BiDi)
    // ========================================================================

    /**
     * Obtiene el interceptor de red para monitoreo avanzado.
     * Crea una nueva instancia si no existe.
     *
     * @return NetworkInterceptor configurado
     */
    public NetworkInterceptor getNetworkInterceptor() {
        if (networkInterceptor == null) {
            networkInterceptor = new NetworkInterceptor(driver);
        }
        return networkInterceptor;
    }

    /**
     * Inicia la captura de tráfico de red.
     * Debe llamarse ANTES de las acciones que generan tráfico.
     */
    public void startNetworkCapture() {
        getNetworkInterceptor().startCapturing();
        testContext.logInfo("Network capture started");
    }

    /**
     * Detiene la captura de tráfico de red.
     */
    public void stopNetworkCapture() {
        if (networkInterceptor != null) {
            networkInterceptor.stopCapturing();
            testContext.logInfo("Network capture stopped");
        }
    }

    /**
     * Limpia las peticiones/respuestas capturadas.
     */
    public void clearNetworkCapture() {
        if (networkInterceptor != null) {
            networkInterceptor.clearCaptured();
        }
    }

    /**
     * Espera a que se capture una petición que coincida con el patrón de URL.
     *
     * @param urlPattern patrón de URL (substring match)
     * @param timeoutSeconds timeout en segundos
     * @return Optional con la petición capturada
     */
    public Optional<CapturedRequest> waitForRequest(String urlPattern, int timeoutSeconds) {
        return getNetworkInterceptor().waitForRequest(urlPattern, Duration.ofSeconds(timeoutSeconds));
    }

    /**
     * Espera a que se capture una respuesta que coincida con el patrón de URL.
     *
     * @param urlPattern patrón de URL (substring match)
     * @param timeoutSeconds timeout en segundos
     * @return Optional con la respuesta capturada
     */
    public Optional<CapturedResponse> waitForResponse(String urlPattern, int timeoutSeconds) {
        return getNetworkInterceptor().waitForResponse(urlPattern, Duration.ofSeconds(timeoutSeconds));
    }

    /**
     * Espera una respuesta de servicio (API) exitosa (2xx).
     *
     * @param urlPattern patrón de URL del servicio
     * @param timeoutSeconds timeout en segundos
     * @return Optional con la respuesta
     */
    public Optional<CapturedResponse> waitForServiceResponse(String urlPattern, int timeoutSeconds) {
        return getNetworkInterceptor().waitForServiceResponse(urlPattern, Duration.ofSeconds(timeoutSeconds));
    }

    /**
     * Verifica si hubo peticiones a una URL.
     *
     * @param urlPattern patrón de URL
     * @return true si hubo peticiones
     */
    public boolean hasRequestTo(String urlPattern) {
        return getNetworkInterceptor().hasRequestTo(urlPattern);
    }

    /**
     * Cuenta las peticiones a una URL.
     *
     * @param urlPattern patrón de URL
     * @return número de peticiones
     */
    public long countRequestsTo(String urlPattern) {
        return getNetworkInterceptor().countRequestsTo(urlPattern);
    }

    /**
     * Verifica si hubo errores HTTP (4xx, 5xx).
     *
     * @return true si hubo errores
     */
    public boolean hasHttpErrors() {
        return getNetworkInterceptor().hasHttpErrors();
    }

    /**
     * Configura autenticación HTTP básica para todas las peticiones.
     *
     * @param username usuario
     * @param password contraseña
     */
    public void setHttpBasicAuth(String username, String password) {
        getNetworkInterceptor().withBasicAuth(username, password);
        testContext.logInfo("HTTP Basic Auth configured for user: {}", username);
    }

    // ========================================================================
    // SELENIUM 4: PDF GENERATION (PrintsPage Interface)
    // ========================================================================

    /**
     * Genera un PDF de la página actual con opciones por defecto (A4 vertical).
     *
     * @param filePath ruta donde guardar el PDF
     * @return File con el PDF generado
     */
    public File printToPDF(String filePath) {
        File pdfFile = PrintToPDFUtils.printToPDF(driver, filePath);
        testContext.logInfo("PDF generated: {}", filePath);
        return pdfFile;
    }

    /**
     * Genera un PDF de la página actual con formato específico.
     *
     * @param filePath ruta donde guardar el PDF
     * @param format formato de página (A4, LETTER, LEGAL, A3, TABLOID)
     * @return File con el PDF generado
     */
    public File printToPDF(String filePath, PageFormat format) {
        PrintOptions options = PrintToPDFUtils.createOptions(format, false);
        File pdfFile = PrintToPDFUtils.printToPDF(driver, filePath, options);
        testContext.logInfo("PDF generated ({} format): {}", format.name(), filePath);
        return pdfFile;
    }

    /**
     * Genera un PDF en formato landscape.
     *
     * @param filePath ruta donde guardar el PDF
     * @return File con el PDF generado
     */
    public File printToPDFLandscape(String filePath) {
        PrintOptions options = PrintToPDFUtils.createA4LandscapeOptions();
        File pdfFile = PrintToPDFUtils.printToPDF(driver, filePath, options);
        testContext.logInfo("PDF generated (landscape): {}", filePath);
        return pdfFile;
    }

    /**
     * Genera un PDF sin márgenes (página completa).
     *
     * @param filePath ruta donde guardar el PDF
     * @return File con el PDF generado
     */
    public File printToPDFFullPage(String filePath) {
        PrintOptions options = PrintToPDFUtils.createFullPageOptions();
        File pdfFile = PrintToPDFUtils.printToPDF(driver, filePath, options);
        testContext.logInfo("PDF generated (full page): {}", filePath);
        return pdfFile;
    }

    /**
     * Genera un PDF con rango de páginas específico.
     *
     * @param filePath ruta donde guardar el PDF
     * @param pageRanges rango de páginas (ej: "1-3,5,7-9")
     * @return File con el PDF generado
     */
    public File printToPDF(String filePath, String pageRanges) {
        PrintOptions options = PrintToPDFUtils.createOptionsWithPageRanges(pageRanges);
        File pdfFile = PrintToPDFUtils.printToPDF(driver, filePath, options);
        testContext.logInfo("PDF generated (pages {}): {}", pageRanges, filePath);
        return pdfFile;
    }

    // ========================================================================
    // SELENIUM 4: BROWSER LOGS (WebDriver BiDi Log Inspector)
    // ========================================================================

    /**
     * Obtiene el capturador de logs del navegador.
     * Crea una nueva instancia si no existe.
     *
     * @return BrowserLogCapture configurado
     */
    public BrowserLogCapture getBrowserLogCapture() {
        if (browserLogCapture == null) {
            browserLogCapture = new BrowserLogCapture(driver);
        }
        return browserLogCapture;
    }

    /**
     * Inicia la captura de logs del navegador.
     */
    public void startLogCapture() {
        getBrowserLogCapture().startCapturing();
        testContext.logInfo("Browser log capture started");
    }

    /**
     * Detiene la captura de logs.
     */
    public void stopLogCapture() {
        if (browserLogCapture != null) {
            browserLogCapture.stopCapturing();
            testContext.logInfo("Browser log capture stopped");
        }
    }

    /**
     * Obtiene todos los logs capturados.
     *
     * @return lista de mensajes de log
     */
    public List<String> getBrowserLogs() {
        return getBrowserLogCapture().getLogs();
    }

    /**
     * Obtiene logs de un nivel específico.
     *
     * @param level nivel de log (ALL, DEBUG, INFO, WARNING, ERROR)
     * @return lista de mensajes
     */
    public List<String> getBrowserLogs(LogLevel level) {
        return getBrowserLogCapture().getLogs(level);
    }

    /**
     * Obtiene solo los errores del navegador.
     *
     * @return lista de errores
     */
    public List<String> getBrowserErrors() {
        return getBrowserLogCapture().getErrors();
    }

    /**
     * Verifica si hay errores JavaScript en la consola.
     *
     * @return true si hay errores
     */
    public boolean hasJavaScriptErrors() {
        return getBrowserLogCapture().hasJavaScriptErrors();
    }

    /**
     * Imprime todos los logs capturados al logger.
     */
    public void printBrowserLogs() {
        getBrowserLogCapture().printLogs();
    }

    /**
     * Limpia los logs capturados.
     */
    public void clearBrowserLogs() {
        if (browserLogCapture != null) {
            browserLogCapture.clearLogs();
        }
    }

    // ========================================================================
    // SELENIUM 4: DEVICE EMULATION (Chrome DevTools Protocol)
    // ========================================================================

    /**
     * Emula un iPhone 12.
     * Configura viewport, user agent y touch events.
     */
    public void emulateIPhone12() {
        ChromeDevToolsUtils.emulateIPhone12(driver);
        testContext.logInfo("Device emulation: iPhone 12");
    }

    /**
     * Emula un iPhone 12 Pro Max.
     */
    public void emulateIPhone12ProMax() {
        ChromeDevToolsUtils.emulateDevice(driver, 428, 926, 3, true);
        ChromeDevToolsUtils.setUserAgentChromeMobile(driver);
        ChromeDevToolsUtils.setTouchEmulation(driver, true);
        testContext.logInfo("Device emulation: iPhone 12 Pro Max");
    }

    /**
     * Emula un iPad.
     */
    public void emulateIPad() {
        ChromeDevToolsUtils.emulateIPad(driver);
        testContext.logInfo("Device emulation: iPad");
    }

    /**
     * Emula un Google Pixel 5.
     */
    public void emulatePixel5() {
        ChromeDevToolsUtils.emulatePixel5(driver);
        testContext.logInfo("Device emulation: Pixel 5");
    }

    /**
     * Emula un dispositivo personalizado.
     *
     * @param width ancho en pixels
     * @param height alto en pixels
     * @param deviceScaleFactor factor de escala (1-3)
     * @param mobile true si es dispositivo móvil
     */
    public void emulateDevice(int width, int height, double deviceScaleFactor, boolean mobile) {
        ChromeDevToolsUtils.emulateDevice(driver, width, height, deviceScaleFactor, mobile);
        if (mobile) {
            ChromeDevToolsUtils.setTouchEmulation(driver, true);
        }
        testContext.logInfo("Device emulation: {}x{} (scale: {}, mobile: {})",
                String.valueOf(width), String.valueOf(height),
                String.valueOf(deviceScaleFactor), String.valueOf(mobile));
    }

    /**
     * Desactiva la emulación de dispositivo.
     */
    public void clearDeviceEmulation() {
        ChromeDevToolsUtils.clearDeviceEmulation(driver);
        ChromeDevToolsUtils.setTouchEmulation(driver, false);
        testContext.logInfo("Device emulation cleared");
    }

    // ========================================================================
    // SELENIUM 4: NETWORK EMULATION (Chrome DevTools Protocol)
    // ========================================================================

    /**
     * Emula una condición de red específica.
     *
     * @param condition condición de red (OFFLINE, SLOW_3G, REGULAR_3G, FAST_4G, WIFI)
     */
    public void emulateNetworkCondition(NetworkCondition condition) {
        ChromeDevToolsUtils.emulateNetworkCondition(driver, condition);
        testContext.logInfo("Network emulation: {}", condition.name());
    }

    /**
     * Emula modo offline (sin conexión).
     */
    public void emulateOffline() {
        emulateNetworkCondition(NetworkCondition.OFFLINE);
    }

    /**
     * Emula conexión 3G lenta.
     */
    public void emulateSlow3G() {
        emulateNetworkCondition(NetworkCondition.SLOW_3G);
    }

    /**
     * Emula conexión 4G rápida.
     */
    public void emulateFast4G() {
        emulateNetworkCondition(NetworkCondition.FAST_4G);
    }

    /**
     * Desactiva la emulación de red.
     */
    public void clearNetworkEmulation() {
        ChromeDevToolsUtils.disableNetworkEmulation(driver);
        testContext.logInfo("Network emulation disabled");
    }

    // ========================================================================
    // SELENIUM 4: GEOLOCATION MOCK (Chrome DevTools Protocol)
    // ========================================================================

    /**
     * Simula una ubicación geográfica específica.
     *
     * @param latitude latitud
     * @param longitude longitud
     * @param accuracy precisión en metros
     */
    public void mockGeolocation(double latitude, double longitude, int accuracy) {
        ChromeDevToolsUtils.mockGeolocation(driver, latitude, longitude, accuracy);
        testContext.logInfo("Geolocation mocked: lat={}, lon={}", String.valueOf(latitude), String.valueOf(longitude));
    }

    /**
     * Simula ubicación en Madrid, España.
     */
    public void mockGeolocationMadrid() {
        ChromeDevToolsUtils.mockGeolocationMadrid(driver);
        testContext.logInfo("Geolocation mocked: Madrid");
    }

    /**
     * Simula ubicación en Barcelona, España.
     */
    public void mockGeolocationBarcelona() {
        ChromeDevToolsUtils.mockGeolocationBarcelona(driver);
        testContext.logInfo("Geolocation mocked: Barcelona");
    }

    /**
     * Simula ubicación en Londres, UK.
     */
    public void mockGeolocationLondon() {
        ChromeDevToolsUtils.mockGeolocationLondon(driver);
        testContext.logInfo("Geolocation mocked: London");
    }

    /**
     * Simula ubicación en Nueva York, USA.
     */
    public void mockGeolocationNewYork() {
        ChromeDevToolsUtils.mockGeolocationNewYork(driver);
        testContext.logInfo("Geolocation mocked: New York");
    }

    /**
     * Limpia la simulación de geolocalización.
     */
    public void clearGeolocationMock() {
        ChromeDevToolsUtils.clearGeolocationOverride(driver);
        testContext.logInfo("Geolocation mock cleared");
    }

    // ========================================================================
    // SELENIUM 4: CPU THROTTLING (Chrome DevTools Protocol)
    // ========================================================================

    /**
     * Aplica throttling de CPU.
     *
     * @param throttling nivel de throttling (NO_THROTTLE, SLOW_4X, VERY_SLOW_6X, EXTREME_20X)
     */
    public void setCPUThrottling(CPUThrottling throttling) {
        ChromeDevToolsUtils.setCPUThrottling(driver, throttling);
        testContext.logInfo("CPU throttling: {}", throttling.name());
    }

    /**
     * Desactiva el throttling de CPU.
     */
    public void clearCPUThrottling() {
        ChromeDevToolsUtils.clearCPUThrottling(driver);
        testContext.logInfo("CPU throttling cleared");
    }

    // ========================================================================
    // SELENIUM 4: TIMEZONE & LOCALE (Chrome DevTools Protocol)
    // ========================================================================

    /**
     * Establece la zona horaria del navegador.
     *
     * @param timezoneId ID de zona horaria (ej: "Europe/Madrid", "America/New_York")
     */
    public void setTimezone(String timezoneId) {
        ChromeDevToolsUtils.setTimezoneOverride(driver, timezoneId);
        testContext.logInfo("Timezone set: {}", timezoneId);
    }

    /**
     * Establece zona horaria de Madrid.
     */
    public void setTimezoneMadrid() {
        ChromeDevToolsUtils.setTimezoneMadrid(driver);
        testContext.logInfo("Timezone set: Europe/Madrid");
    }

    /**
     * Establece zona horaria de Londres.
     */
    public void setTimezoneLondon() {
        ChromeDevToolsUtils.setTimezoneLondon(driver);
        testContext.logInfo("Timezone set: Europe/London");
    }

    /**
     * Establece zona horaria de Nueva York.
     */
    public void setTimezoneNewYork() {
        ChromeDevToolsUtils.setTimezoneNewYork(driver);
        testContext.logInfo("Timezone set: America/New_York");
    }

    /**
     * Establece zona horaria de Tokio.
     */
    public void setTimezoneTokyo() {
        ChromeDevToolsUtils.setTimezoneTokyo(driver);
        testContext.logInfo("Timezone set: Asia/Tokyo");
    }

    /**
     * Establece el locale del navegador.
     *
     * @param locale código de locale (ej: "es-ES", "en-US", "ja-JP")
     */
    public void setLocale(String locale) {
        ChromeDevToolsUtils.setLocaleOverride(driver, locale);
        testContext.logInfo("Locale set: {}", locale);
    }

    // ========================================================================
    // SELENIUM 4: CACHE & COOKIES (Chrome DevTools Protocol)
    // ========================================================================

    /**
     * Desactiva la caché del navegador.
     */
    public void disableCache() {
        ChromeDevToolsUtils.setCacheDisabled(driver, true);
        testContext.logInfo("Browser cache disabled");
    }

    /**
     * Activa la caché del navegador.
     */
    public void enableCache() {
        ChromeDevToolsUtils.setCacheDisabled(driver, false);
        testContext.logInfo("Browser cache enabled");
    }

    /**
     * Limpia la caché del navegador.
     */
    public void clearCache() {
        ChromeDevToolsUtils.clearBrowserCache(driver);
        testContext.logInfo("Browser cache cleared");
    }

    /**
     * Limpia las cookies del navegador.
     */
    public void clearCookies() {
        ChromeDevToolsUtils.clearBrowserCookies(driver);
        testContext.logInfo("Browser cookies cleared");
    }

    // ========================================================================
    // SELENIUM 4: PERFORMANCE METRICS (Chrome DevTools Protocol)
    // ========================================================================

    /**
     * Obtiene métricas de rendimiento del navegador.
     *
     * @return Map con métricas (JSHeapUsedSize, LayoutCount, etc.)
     */
    public Map<String, Number> getPerformanceMetrics() {
        return ChromeDevToolsUtils.getPerformanceMetrics(driver);
    }

    // ========================================================================
    // SELENIUM 4: WEBAUTHN / VIRTUAL AUTHENTICATOR
    // ========================================================================

    /**
     * Crea un autenticador virtual tipo plataforma (TouchID, FaceID, Windows Hello).
     * Ideal para testing de autenticación biométrica.
     *
     * @return VirtualAuthenticator configurado
     */
    public VirtualAuthenticator createPlatformAuthenticator() {
        virtualAuthenticator = VirtualAuthenticatorUtils.createPlatformAuthenticator(driver);
        testContext.logInfo("Platform authenticator created (TouchID/FaceID/Windows Hello)");
        return virtualAuthenticator;
    }

    /**
     * Crea un autenticador virtual tipo USB Security Key (YubiKey).
     *
     * @return VirtualAuthenticator configurado
     */
    public VirtualAuthenticator createUSBSecurityKey() {
        virtualAuthenticator = VirtualAuthenticatorUtils.createUSBSecurityKey(driver);
        testContext.logInfo("USB Security Key authenticator created");
        return virtualAuthenticator;
    }

    /**
     * Crea un autenticador virtual tipo NFC.
     *
     * @return VirtualAuthenticator configurado
     */
    public VirtualAuthenticator createNFCSecurityKey() {
        virtualAuthenticator = VirtualAuthenticatorUtils.createNFCSecurityKey(driver);
        testContext.logInfo("NFC Security Key authenticator created");
        return virtualAuthenticator;
    }

    /**
     * Obtiene el autenticador virtual activo.
     *
     * @return VirtualAuthenticator o null si no hay ninguno
     */
    public VirtualAuthenticator getVirtualAuthenticator() {
        return virtualAuthenticator;
    }

    /**
     * Añade una credencial al autenticador virtual.
     *
     * @param credential credencial a añadir
     */
    public void addCredential(Credential credential) {
        if (virtualAuthenticator != null) {
            VirtualAuthenticatorUtils.addCredential(virtualAuthenticator, credential);
            testContext.logInfo("Credential added to virtual authenticator");
        } else {
            throw new IllegalStateException("No virtual authenticator created. Call createPlatformAuthenticator() first.");
        }
    }

    /**
     * Obtiene las credenciales del autenticador virtual.
     *
     * @return lista de credenciales
     */
    public List<Credential> getCredentials() {
        if (virtualAuthenticator != null) {
            return VirtualAuthenticatorUtils.getCredentials(virtualAuthenticator);
        }
        return List.of();
    }

    /**
     * Crea una credencial resident (discoverable) para WebAuthn testing.
     *
     * @param rpId Relying Party ID (dominio, ej: "example.com")
     * @return Credential configurada
     */
    public Credential createResidentCredential(String rpId) {
        return VirtualAuthenticatorUtils.createResidentCredential(rpId, VirtualAuthenticatorUtils.generateUserHandle());
    }

    /**
     * Elimina el autenticador virtual.
     */
    public void removeVirtualAuthenticator() {
        if (virtualAuthenticator != null) {
            VirtualAuthenticatorUtils.removeAuthenticator(driver, virtualAuthenticator);
            virtualAuthenticator = null;
            testContext.logInfo("Virtual authenticator removed");
        }
    }

    /**
     * Configura un escenario completo de WebAuthn testing.
     * Crea autenticador + credencial pre-registrada.
     *
     * @param rpId dominio del sitio (ej: "example.com")
     * @return VirtualAuthenticator configurado
     */
    public VirtualAuthenticator setupWebAuthnTestScenario(String rpId) {
        virtualAuthenticator = VirtualAuthenticatorUtils.setupTestScenario(driver, rpId);
        testContext.logInfo("WebAuthn test scenario configured for: {}", rpId);
        return virtualAuthenticator;
    }

    // ========================================================================
    // SELENIUM 4: RESET ALL EMULATIONS
    // ========================================================================

    /**
     * Resetea todas las emulaciones a valores por defecto.
     * Incluye: device, network, geolocation, CPU, timezone, cache.
     */
    public void resetAllEmulations() {
        ChromeDevToolsUtils.resetAllEmulations(driver);
        testContext.logInfo("All emulations reset to defaults");
    }

    // ========================================================================
    // UTILITY: Get WebDriver
    // ========================================================================

    /**
     * Obtiene el WebDriver actual.
     * Útil para acceso directo a funcionalidades no expuestas.
     *
     * @return WebDriver actual
     */
    public WebDriver getDriver() {
        return driver;
    }
}
