package io.zahori.framework.driver.browserfactory;

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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.Platform;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.ie.InternetExplorerDriver;
import org.openqa.selenium.ie.InternetExplorerOptions;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.testng.log4testng.Logger;

import io.zahori.framework.files.properties.ZahoriProperties;

public class WebDriverBrowserSelenium {

    private static final int TIME_WAIT_ELEMENT_NOT_PRESENT_TWENTY_SG = 20;

    private static final Logger LOG = Logger.getLogger(WebDriverBrowserSelenium.class);

    private static final String PREFIX_NAME_METHOD = "getWebDriverRemote";

    private Browsers browsers;

    private Proxy proxy;

    public WebDriverBrowserSelenium(Browsers browsers) {
        super();
        this.browsers = browsers;
        proxy = null;
    }

    public WebDriverBrowserSelenium(Browsers browsers, final Proxy proxy) {
        super();
        this.browsers = browsers;
        this.proxy = proxy;
    }

    public WebDriver getWebDriver() {
        WebDriver driver = null;
        String testName = browsers.getTestName();

        try {
            DesiredCapabilities caps = new CapsBrowserSelenium(browsers).getCapsByNavigator();
            if (proxy != null) {
                caps.setCapability(CapabilityType.PROXY, proxy);
            }

            // ElasTest Capabilities
            caps.setCapability("live", true);
            caps.setCapability("testName", testName);
            
            // Zahori Capabilities
            if (!StringUtils.isEmpty(browsers.getCaseExecutionId()))
                caps.setCapability("name", browsers.getCaseExecutionId());

            driver = getDriver(caps);

            if (driver != null) {
                setProperties(driver);
            }

        } catch (final IllegalArgumentException | SecurityException | ClassNotFoundException | NoSuchMethodException | InvocationTargetException
                | IllegalAccessException | InstantiationException e) {
            LOG.error(e.getMessage() + e.getCause());
        }
        return driver;
    }

    public void setProperties(final WebDriver driver) {
        driver.manage().timeouts().implicitlyWait(TIME_WAIT_ELEMENT_NOT_PRESENT_TWENTY_SG, TimeUnit.SECONDS);
        driver.manage().window().maximize();
    }

    public WebDriver getDriver(final DesiredCapabilities caps)
            throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        WebDriver driver;
        final Method meth = getClass().getMethod(PREFIX_NAME_METHOD + browsers.getRemote(), Browsers.class, DesiredCapabilities.class);
        driver = (WebDriver) meth.invoke(this, this.browsers, caps);
        return driver;
    }

    public WebDriver getWebDriverRemoteYES(final Browsers navega, final DesiredCapabilities caps) {

        WebDriver driver = null;
        try {
            Map<String, String> extraPreferences = new ZahoriProperties().getBrowserPreferencesToBeAdded(navega.getName());
            if (StringUtils.equalsIgnoreCase("chrome", navega.getName())) {
                ChromeOptions options = new ChromeOptions();
                for (String pref : extraPreferences.keySet()) {
                    String argument = StringUtils.isEmpty(extraPreferences.get(pref)) ? pref : pref + "=" + extraPreferences.get(pref);
                    options.addArguments(argument);
                }

                caps.setCapability(ChromeOptions.CAPABILITY, options);
            }

            String remoteUrl = StringUtils.isEmpty(System.getenv("ET_EUS_API")) ? navega.getRemoteUrl() : System.getenv("ET_EUS_API");
            LOG.debug("Valor que llega del Plugin de Jenkins de Elastest [ET_EUS_API]:  " + remoteUrl);
            caps.setPlatform(Platform.ANY);
            driver = new RemoteWebDriver(new URL(remoteUrl), caps);
        } catch (final MalformedURLException e) {
            LOG.error(e.getMessage() + e.getCause());
        }
        return driver;
    }

    public WebDriver getWebDriverRemoteNO(final Browsers navega, final DesiredCapabilities caps)
            throws InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException, ClassNotFoundException {
        Platform currentPlatform = caps.getPlatform();
        if (isWindowsPlatform(currentPlatform)) {
            caps.setPlatform(Platform.WINDOWS);
        }
        ZahoriProperties zahoriProperties = new ZahoriProperties();

        Map<String, String> extraPreferences = zahoriProperties.getBrowserPreferencesToBeAdded(navega.getName());

        if (StringUtils.containsIgnoreCase(navega.getName(), "firefox")) {
            FirefoxOptions options = new FirefoxOptions(caps);
            for (String extraPref : extraPreferences.keySet()) {
                if (isBoolean(extraPreferences.get(extraPref))) {
                    options.addPreference(extraPref, Boolean.valueOf(extraPreferences.get(extraPref)));
                } else if (isDigit(extraPreferences.get(extraPref))) {
                    options.addPreference(extraPref, Integer.parseInt(extraPreferences.get(extraPref)));
                } else {
                    options.addPreference(extraPref, extraPreferences.get(extraPref));
                }
            }

            options.setAcceptInsecureCerts(true);
            try {
                return (WebDriver) Class.forName(navega.getPackageWebdrivername()).getConstructor(FirefoxOptions.class).newInstance(options);
            } catch (Exception e) {
                LOG.info("No se puede instanciar el driver: " + e.getMessage());
                return null;
            }
        } else if (StringUtils.containsIgnoreCase(navega.getName(), "explorer")) {
            caps.setAcceptInsecureCerts(false);
            InternetExplorerOptions options = new InternetExplorerOptions(caps);
            options.ignoreZoomSettings();

            try {
                return new InternetExplorerDriver(options);
            } catch (Exception e) {
                LOG.info("No se puede instanciar el driver: " + e.getMessage());
                return null;
            }
        } else if (StringUtils.containsIgnoreCase(navega.getName(), "chrome")) {

            ChromeOptions options = new ChromeOptions();

            Map<String, Object> prefs = new HashMap<>();
            prefs.put("download.default_directory", navega.getDownloadPath());
            options.setExperimentalOption("prefs", prefs);

            for (String currentCap : caps.asMap().keySet()) {
                options.setCapability(currentCap, caps.asMap().get(currentCap));
            }
            options.addArguments("--ignore-certificate-errors");
            options.addArguments("start-maximized");

            for (String key : extraPreferences.keySet()) {
                String argument = StringUtils.isEmpty(extraPreferences.get(key)) ? key : key + "=" + extraPreferences.get(key);
                options.addArguments(argument);
            }

            try {
                return (WebDriver) Class.forName(navega.getPackageWebdrivername()).getConstructor(ChromeOptions.class).newInstance(options);
            } catch (Exception e) {
                LOG.info("No se puede instanciar el driver: " + e.getMessage());
                return null;
            }
        } else {
            try {
                return (WebDriver) Class.forName(navega.getPackageWebdrivername()).getConstructor(Capabilities.class).newInstance(caps);
            } catch (Exception e) {
                LOG.info("No se puede instanciar el driver: " + e.getMessage());
                return null;
            }
        }
    }

    public Browsers getNavega() {
        return browsers;
    }

    public void setNavega(final Browsers navega) {
        this.browsers = navega;
    }

    private boolean isWindowsPlatform(Platform platform) {
        return Platform.VISTA == platform || Platform.WIN8 == platform || Platform.WIN8_1 == platform || Platform.WIN10 == platform
                || Platform.WINDOWS == platform || Platform.XP == platform;
    }

    private boolean isDigit(String input) {
        return StringUtils.equalsIgnoreCase("0", input) || StringUtils.equalsIgnoreCase("1", input) || StringUtils.equalsIgnoreCase("2", input)
                || StringUtils.equalsIgnoreCase("3", input) || StringUtils.equalsIgnoreCase("4", input) || StringUtils.equalsIgnoreCase("5", input)
                || StringUtils.equalsIgnoreCase("6", input) || StringUtils.equalsIgnoreCase("7", input) || StringUtils.equalsIgnoreCase("8", input)
                || StringUtils.equalsIgnoreCase("9", input);
    }

    private boolean isBoolean(String input) {
        return StringUtils.equalsIgnoreCase("true", input) || StringUtils.equalsIgnoreCase("false", input);
    }

}
