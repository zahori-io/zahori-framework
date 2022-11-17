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

import io.zahori.framework.files.properties.ZahoriProperties;
import io.zahori.framework.utils.ZahoriStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.Platform;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxProfile;
import org.openqa.selenium.firefox.GeckoDriverService;
import org.openqa.selenium.ie.InternetExplorerDriver;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.logging.LoggingPreferences;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.safari.SafariOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Paths;
import java.util.Map;
import java.util.logging.Level;

public class CapsBrowserSelenium extends DesiredCapabilities {
/**
    private static final String SUFFIX_NAME_METHOD = "Caps";

    private static final String PREFIX_NAME_METHOD = "get";

    private static final long serialVersionUID = -6421556099900469645L;

    private static final String WEBDRIVER_IE_DRIVER_NAME = "webdriver.ie.driver.name";

    private static final String WEBDRIVER_IE_DRIVER = "webdriver.ie.driver";

    private static final String WEBDRIVER_IE_DRIVER_PATH = "webdriver.ie.driver.path";

    private static final String WEBDRIVER_MARIONETTE_DRIVER_PATH_WINDOWS = "webdriver.marionette.driver.path.windows";

    private static final String WEBDRIVER_MARIONETTE_DRIVER_PATH_UNIX = "webdriver.marionette.driver.path.unix";

    private static final String WEBDRIVER_MARIONETTE_BINARY_PATH_WINDOWS = "webdriver.marionette.firefox.binary.path.windows";

    private static final String WEBDRIVER_MARIONETTE_BINARY_PATH_UNIX = "webdriver.marionette.firefox.binary.path.unix";

    private static final String MAX_FIREFOX_LEGACY_VERSION = "47.0.1";
    private static final Logger LOG = LoggerFactory.getLogger(CapsBrowserSelenium.class);

    private Browsers browsers;

    public CapsBrowserSelenium() {
        super();
    }

    public CapsBrowserSelenium(Browsers browsers) {
        super(browsers.getName(), browsers.getVersion(), Platform.valueOf(browsers.getPlatform()));
        this.browsers = browsers;
    }

    public DesiredCapabilities getCapsByNavigator() throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {

        final Method meth = getClass().getMethod(PREFIX_NAME_METHOD + ZahoriStringUtils.capitalizeWithoutWhitespace(browsers.getName()) + SUFFIX_NAME_METHOD,
                Browsers.class);

        DesiredCapabilities caps = null;
        try {
            caps = (DesiredCapabilities) meth.invoke(this, browsers);
            Map<String, String> extraCapabilities = new ZahoriProperties().getExtraCapabilities();
            for (String extraCap : extraCapabilities.keySet()) {
                if (isBoolean(extraCapabilities.get(extraCap))) {
                    caps.setCapability(extraCap, Boolean.valueOf(extraCapabilities.get(extraCap)));
                } else {
                    caps.setCapability(extraCap, extraCapabilities.get(extraCap));
                }
            }
            caps.setVersion(browsers.getVersion());
            caps.setCapability("screenResolution", browsers.getScreenResolution());
            caps.setPlatform(Platform.getCurrent());
            caps.setBrowserName(browsers.getName());
            caps.setCapability(CapabilityType.ACCEPT_SSL_CERTS, true);
            caps.setCapability(CapabilityType.ACCEPT_INSECURE_CERTS, true);

        } catch (final IllegalArgumentException | SecurityException e) {
            LOG.error(e.getMessage());
        }
        return caps;
    }

    public DesiredCapabilities getInternetExplorerCaps(Browsers navega) {
        final String pathDriverExplorer = System.getProperty(WEBDRIVER_IE_DRIVER_PATH) + navega.getBits() + System.getProperty(WEBDRIVER_IE_DRIVER_NAME);
        System.setProperty(WEBDRIVER_IE_DRIVER, pathDriverExplorer);

        final DesiredCapabilities caps = DesiredCapabilities.internetExplorer();

        caps.setCapability(InternetExplorerDriver.INTRODUCE_FLAKINESS_BY_IGNORING_SECURITY_DOMAINS, true);

        // caps.setCapability("ie.browserCommandLineSwitches", "-private");
        caps.setCapability("ie.ensureCleanSession", true);
        caps.setCapability("ie.enablePersistentHover", false);
        caps.setCapability("takesScreenshot", false);

        caps.setCapability("ignoreZoomSetting", true);
        caps.setCapability("platformName", "WINDOWS");

        if (new ZahoriProperties().enableSeleniumLogPerformance()) {
            LoggingPreferences loggingprefs = new LoggingPreferences();
            loggingprefs.enable(LogType.PERFORMANCE, Level.ALL);
            caps.setCapability(CapabilityType.LOGGING_PREFS, loggingprefs);
        }

        return caps;
    }

    public DesiredCapabilities getFirefoxCaps(Browsers navega) {

        final DesiredCapabilities caps = DesiredCapabilities.firefox();
        FirefoxProfile profile = new FirefoxProfile();

        if (!StringUtils.isEmpty(navega.getDownloadPath())) {
            profile.setPreference("browser.download.folderList", 2);
            profile.setPreference("browser.download.manager.showWhenStarting", false);
            profile.setPreference("browser.download.manager.showAlertOnComplete", false);
            profile.setPreference("browser.download.useDownloadDir", true);
            profile.setPreference("browser.download.dir", Paths.get(navega.getDownloadPath()).toString());
            profile.setPreference("browser.helperApps.neverAsk.saveToDisk",
                    "application/zip,text/csv,application/java-archive, application/x-msexcel,application/excel,application/vnd.openxmlformats-officedocument.wordprocessingml.document,application/x-excel,application/vnd.ms-excel,image/png,image/jpeg,text/html,text/plain,application/msword,application/xml,application/vnd.microsoft.portable-executable");
            profile.setPreference("pdfjs.disabled", true);
        }

        caps.setCapability(FirefoxDriver.PROFILE, profile);
        caps.setCapability(CapabilityType.ACCEPT_SSL_CERTS, true);
        caps.setJavascriptEnabled(true);

        if (isLegacyFirefoxVersion()) {
            caps.setCapability(FirefoxDriver.MARIONETTE, false);
        }

        if (Platform.WINDOWS.equals(Platform.valueOf(navega.getPlatform()))) {
            System.setProperty(GeckoDriverService.GECKO_DRIVER_EXE_PROPERTY,
                    System.getProperty(WEBDRIVER_MARIONETTE_DRIVER_PATH_WINDOWS) + navega.getBits() + "/geckodriver.exe");
            caps.setCapability(FirefoxDriver.BINARY, System.getProperty(WEBDRIVER_MARIONETTE_BINARY_PATH_WINDOWS));
        } else if (Platform.UNIX.equals(Platform.valueOf(navega.getPlatform()))) {
            System.setProperty(GeckoDriverService.GECKO_DRIVER_EXE_PROPERTY,
                    System.getProperty(WEBDRIVER_MARIONETTE_DRIVER_PATH_UNIX) + navega.getBits() + "/geckodriver");

            caps.setCapability(FirefoxDriver.BINARY, System.getProperty(WEBDRIVER_MARIONETTE_BINARY_PATH_UNIX));
        }

        if (new ZahoriProperties().enableSeleniumLogPerformance()) {
            LoggingPreferences loggingprefs = new LoggingPreferences();
            loggingprefs.enable(LogType.PERFORMANCE, Level.ALL);
            caps.setCapability(CapabilityType.LOGGING_PREFS, loggingprefs);
        }

        return caps;
    }

    public DesiredCapabilities getChromeCaps(Browsers navega) {
        final DesiredCapabilities caps = DesiredCapabilities.chrome();
        if (new ZahoriProperties().enableSeleniumLogPerformance()) {
            LoggingPreferences loggingprefs = new LoggingPreferences();
            loggingprefs.enable(LogType.PERFORMANCE, Level.ALL);
            caps.setCapability("goog:loggingPrefs", loggingprefs);
        }
        return caps;
    }

    public DesiredCapabilities getSafariCaps(Browsers navega) {
        final DesiredCapabilities caps = DesiredCapabilities.safari();

        if (navega.getPlatform().equalsIgnoreCase(Platform.WINDOWS.name())) {
            final SafariOptions options = new SafariOptions();
            caps.setCapability(SafariOptions.CAPABILITY, options);
        }
        caps.setJavascriptEnabled(true);

        if (new ZahoriProperties().enableSeleniumLogPerformance()) {
            LoggingPreferences loggingprefs = new LoggingPreferences();
            loggingprefs.enable(LogType.PERFORMANCE, Level.ALL);
            caps.setCapability(CapabilityType.LOGGING_PREFS, loggingprefs);
        }

        return caps;
    }

    public DesiredCapabilities getMicrosoftEdgeCaps(Browsers navega) {
        final DesiredCapabilities caps = DesiredCapabilities.edge();
        caps.setJavascriptEnabled(true);

        if (new ZahoriProperties().enableSeleniumLogPerformance()) {
            LoggingPreferences loggingprefs = new LoggingPreferences();
            loggingprefs.enable(LogType.PERFORMANCE, Level.ALL);
            caps.setCapability(CapabilityType.LOGGING_PREFS, loggingprefs);
        }

        return caps;
    }

    public DesiredCapabilities getOperaCaps(Browsers navega) {
        final DesiredCapabilities caps = DesiredCapabilities.opera();
        caps.setJavascriptEnabled(true);

        if (new ZahoriProperties().enableSeleniumLogPerformance()) {
            LoggingPreferences loggingprefs = new LoggingPreferences();
            loggingprefs.enable(LogType.PERFORMANCE, Level.ALL);
            caps.setCapability(CapabilityType.LOGGING_PREFS, loggingprefs);
        }

        return caps;
    }

    public Browsers getNavega() {
        return browsers;
    }

    public void setNavega(Browsers navega) {
        this.browsers = navega;
    }

    private boolean isLegacyFirefoxVersion() {
        Version paramVersion = new Version(browsers.getVersion());
        Version maxLegacyVersion = new Version(MAX_FIREFOX_LEGACY_VERSION);

        return paramVersion.compareTo(maxLegacyVersion) <= 0;
    }

    private boolean isBoolean(String input) {
        return StringUtils.equalsIgnoreCase("true", input) || StringUtils.equalsIgnoreCase("false", input);
    }
**/
}
