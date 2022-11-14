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

import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.appium.java_client.service.local.AppiumDriverLocalService;
import io.appium.java_client.service.local.AppiumServerHasNotBeenStartedLocallyException;
import io.appium.java_client.service.local.AppiumServiceBuilder;
import io.zahori.framework.driver.browserfactory.Browsers;
import io.zahori.framework.driver.browserfactory.WebDriverBrowserSelenium;
import io.zahori.framework.driver.mobilefactory.MobileDriverFactory;
import io.zahori.framework.utils.Chronometer;
import io.zahori.framework.utils.Pause;

public class Browser {

    private static final Logger LOG = LoggerFactory.getLogger(TestContext.class);
    public static final String LOADING_PAGE = "Loading page: ";

    private WebDriver driver;
    public TestContext testContext;
    private WebDriverBrowserSelenium wbs;
    private AppiumDriverLocalService service;
    private Map<String, WebDriver> allDrivers;
    private String mainDriverHandle;
    private String activeDriverHandle;

    private static final String TXT_TITLE = "TITLE";
    private static final String TXT_URL = "URL";
    private static final String PLATFORM_WINDOWS = "windows";

    public Browser(TestContext testContext) {
        this.testContext = testContext;
        createLocalServices();
        createDriver();
        mainDriverHandle = driver != null ? driver.getWindowHandle() : StringUtils.EMPTY;
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
        if (testContext.isHarEnabled()) {
            testContext.storeHarLog();
        }

        if (this.testContext.isAppiumLocalService() && service != null) {
            service.stop();
        }

        try {
            if (allDrivers.keySet().isEmpty()) {
                if (driver != null)
                    driver.quit();
            } else {
                for (String currentKey : allDrivers.keySet()) {
                    if (allDrivers.get(currentKey) != null)
                        allDrivers.get(currentKey).quit();
                }
                allDrivers = new HashMap<>();
            }

        } catch (Exception e) {
            testContext.logWarn("A problem has been detected while closing current browser window.");
        }

        driver = null;

        if (StringUtils.equalsIgnoreCase(PLATFORM_WINDOWS, testContext.platform) && killProcess) {
            matarProceso("IEDriverServer*");
            matarProceso("chromedriver*");
            matarProceso("WerFault.exe");
        }
    }

    public void loadPage(String url) {
        createDriver();
        driver.get(url);
        testContext.logInfo(LOADING_PAGE + url);
        setBrowserZoomTo100();
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

    public void loadPageWithProxy(String url, String urlProxy, String user, String password) {
        createDriver();
        driver.manage().timeouts().pageLoadTimeout(100L, TimeUnit.MILLISECONDS);
        try {
            driver.get(url);
        } catch (TimeoutException e) {
            String stringProxy = user + ":" + password + "@" + urlProxy;
            Proxy proxy = new Proxy();
            proxy.setHttpProxy(stringProxy);
            DesiredCapabilities caps = (DesiredCapabilities) ((RemoteWebDriver) driver).getCapabilities();
            caps.setCapability(CapabilityType.PROXY, proxy);
            try {
                driver = wbs.getDriver(caps, null);
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | InstantiationException e1) {
                LOG.error("Error al cargar el driver: " + e1.getMessage());
            }
        } finally {
            driver.manage().timeouts().pageLoadTimeout(10L, TimeUnit.SECONDS);
            driver.get(url);
        }

        testContext.logInfo(LOADING_PAGE + url);
        setBrowserZoomTo100();
    }

    public void loadPageWithCertificate(String url, int numCertificate) {
        driver.get(url);
        testContext.logInfo(LOADING_PAGE + url);
        selectCertificate(numCertificate);
        setBrowserZoomTo100();
    }

    private void createLocalServices() {
        if (this.testContext.isAppiumLocalService()) {
            service = AppiumDriverLocalService.buildService(new AppiumServiceBuilder().usingAnyFreePort());

            service.start();

            if (service == null || !service.isRunning()) {
                throw new AppiumServerHasNotBeenStartedLocallyException("An appium server node is not started!");
            }
            this.testContext.setRemoteUrl(service.getUrl().toString());
        }
    }

    private void createDriver() {
        if (driver == null) {
            if (StringUtils.equalsIgnoreCase(PLATFORM_WINDOWS, testContext.platform)) {
                matarProceso("WerFault.exe");
            }
            if (testContext.isMobilePlatform(testContext.platform)) {
                driver = MobileDriverFactory.getDriver(testContext.getTestNGParameters(testContext.testngContext));
            } else {

                Browsers browsers = Browsers.valueOf(testContext.browserName).withBits(testContext.bits).withPlatform(testContext.platform)
                        .withVersion(testContext.version).withScreenResolution(testContext.resolution).withRemote(testContext.remote)
                        .withTestName(testContext.testCaseName).withRemoteUrl(testContext.remoteUrl).withCaseExecution(testContext.caseExecutionId);

                browsers = StringUtils.isEmpty(testContext.getDownloadPath()) ? browsers : browsers.withDownloadPath(testContext.getDownloadPath());

                Proxy proxy = testContext.isHarEnabled() ? testContext.getProxy4Driver() : null;
                this.wbs = proxy == null ? new WebDriverBrowserSelenium(browsers) : new WebDriverBrowserSelenium(browsers, proxy);
                //				this.wbs = testContext.isHarEnabled() ? new WebDriverBrowserSelenium(browsers, testContext.getProxy4Driver())
                //						: new WebDriverBrowserSelenium(browsers);

                String emulatedDevice = testContext.zahoriProperties.getEmulatedDeviceName();
                this.driver = StringUtils.isBlank(emulatedDevice) ? wbs.getWebDriver() : wbs.getWebDriver(emulatedDevice);
            }
            this.testContext.driver = driver;
        }
    }

    public WebDriver createNewWindow() {
    	return createNewWindow(null);
    }
    
    public WebDriver createNewWindow(String mobileDevice) {
        WebDriver newDriver = StringUtils.isEmpty(mobileDevice) ? wbs.getWebDriver() : wbs.getWebDriver(mobileDevice);
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
            Pause.pause(4);

            testContext.logInfo("Enabling IE compatibility mode");

            // driver.switchTo().window(browserWindow);

            robot.keyPress(KeyEvent.VK_ALT);
            robot.keyPress(KeyEvent.VK_TAB);
            robot.keyRelease(KeyEvent.VK_TAB);
            robot.keyRelease(KeyEvent.VK_ALT);

            Pause.pause(4);

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

    public void setLoadingPageTimeoutInSeconds(int timeoutInSeconds) {
        driver.manage().timeouts().pageLoadTimeout(timeoutInSeconds, TimeUnit.SECONDS);
    }

    private void selectCertificate(int numCertificado) {
        try {

            final Page certificateErrorPO = new Page(testContext);

            final PageElement avisoIEVayaAEsteSitioWeb = new PageElement(certificateErrorPO, "Link aviso IE 'Vaya a este sitio web'",
                    Locator.name("overridelink"));
            if (avisoIEVayaAEsteSitioWeb.isVisible()) {
                avisoIEVayaAEsteSitioWeb.click();
            }

            Pause.pause(4);

            final Robot robot = new Robot();
            for (int i = 1; i < numCertificado; i++) {
                robot.keyPress(KeyEvent.VK_DOWN);
                robot.keyRelease(KeyEvent.VK_DOWN);
            }

            testContext.logInfo("Select certificate " + numCertificado);

            Pause.pause(1);

            robot.keyPress(KeyEvent.VK_ENTER);
            robot.keyRelease(KeyEvent.VK_ENTER);

            Pause.pause(4);

        } catch (final Exception e) {
            throw new RuntimeException("Error selecting certificate: " + e.getMessage());
        }
    }

    private void setBrowserZoomTo100() {
        try {
            if (StringUtils.equalsIgnoreCase("WINDOWS", testContext.platform)) {
                final Robot robot = new Robot();
                robot.keyPress(KeyEvent.VK_CONTROL);
                robot.keyPress(KeyEvent.VK_0);
                robot.keyRelease(KeyEvent.VK_CONTROL);
                robot.keyRelease(KeyEvent.VK_0);
            }
        } catch (final Exception e) {
            testContext.logWarn("Error setting browser zoom to 100%");
        }
    }

    private void matarProceso(String nombreProceso) {
        if (StringUtils.equalsIgnoreCase(PLATFORM_WINDOWS, testContext.platform)) {
            // Se mata el proceso IEDriverServer.exe
            String cmd = "taskkill /f /im  " + nombreProceso;
            Process proceso;
            try {
                proceso = Runtime.getRuntime().exec(cmd);
                proceso.waitFor();
                if (proceso.exitValue() == 0) {
                    LOG.debug("Se ha matado correctamente el proceso: " + nombreProceso);
                }
            } catch (IOException | InterruptedException e) {
                LOG.error("Error intentando matar el proceso " + nombreProceso + ": " + e.getMessage());
            }
        }

    }

    private String switchBrowserWindowByParm(String parm, String option, boolean newWindow) {
        // Store the current window handle
        String windowHandleBefore = driver.getWindowHandle();

        // Search the desired window.
        List<String> windowHandles = new ArrayList<>(driver.getWindowHandles());
        Pause.pause();
        if (newWindow) {
            int actualSize = windowHandles.size();
            Chronometer crono = new Chronometer();
            while ((windowHandles.size() == actualSize) && (crono.getElapsedSeconds() < testContext.timeoutFindElement.intValue())) {
                Pause.shortPause();
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
}
