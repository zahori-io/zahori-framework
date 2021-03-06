package io.zahori.framework.driver.mobilefactory;

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

import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang.StringUtils;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.appium.java_client.AppiumDriver;
import io.zahori.framework.utils.ZahoriStringUtils;

/**
 * The type Mobile driver factory.
 */
public class MobileDriverFactory {

    private static final Logger LOG = LoggerFactory.getLogger(MobileDriverFactory.class);
    private static final int TIME_WAIT_ELEMENT_NOT_PRESENT = 30;

    private MobileDriverFactory() {
    }

    /**
     * Gets driver.
     *
     * @param testCapabilities the test capabilities
     * @return the driver
     */
    public static AppiumDriver<?> getDriver(Map<String, String> testCapabilities) {

        String platform = getPlatform(testCapabilities);
        URL remoteUrl = getRemoteUrl(testCapabilities);

        try {

            // Create platform/device and browser specific capabilities
            Method getCapabilities = Class
                    .forName(MobileDriverFactory.class.getPackage().getName() + "." + platform + "Factory")
                    .getMethod("getCapabilities");
            DesiredCapabilities capabilities = (DesiredCapabilities) getCapabilities.invoke(null);

            // Set capabilities
            setCapabilities(testCapabilities, capabilities);

            // Create the driver
            Method getDriver = Class
                    .forName(MobileDriverFactory.class.getPackage().getName() + "." + platform + "Factory")
                    .getMethod("getDriver", URL.class, DesiredCapabilities.class);

            AppiumDriver<?> appiumDriver = (AppiumDriver<?>) getDriver.invoke(null, remoteUrl, capabilities);

            // IMPLICIT WAIT
            // TODO externalizar
            appiumDriver.manage().timeouts().implicitlyWait(TIME_WAIT_ELEMENT_NOT_PRESENT, TimeUnit.SECONDS);

            // AUTO LAUNCH AND INSTALL APP
            // if ("false".equalsIgnoreCase(testCapabilities.get("autoLaunch")))
            // {
            // // Requiere la capability:
            // // capabilities.setCapability("autoLaunch", "false"); --> sino
            // lanza una excepciçon
            // if
            // (!appiumDriver.isAppInstalled(testCapabilities.get("appPackage")))
            // {
            // LOG.info("-- Instalando la aplicación...");
            // appiumDriver.installApp("D:\\Globalia\\APP_MOVIL\\AirEuropa_1.2.6.apk");
            // }
            // // Launch application (to be used when capability
            // // autoLaunch=false)
            // appiumDriver.launchApp();
            // }

            return appiumDriver;

        } catch (Exception e) {
            LOG.error("Error creating mobile driver: " + e.getMessage());
            return null;
        }

    }

    private static DesiredCapabilities setCapabilities(Map<String, String> capabilities,
            DesiredCapabilities desiredCapabilities) {

        if ((capabilities != null) && (desiredCapabilities != null)) {
            for (Map.Entry<String, String> capability : capabilities.entrySet()) {
                desiredCapabilities.setCapability(capability.getKey(), capability.getValue());
            }
        }

        return desiredCapabilities;
    }

    private static String getPlatform(Map<String, String> testCapabilities) {

        String platformName = testCapabilities.get("platformName");
        if (!StringUtils.isBlank(platformName)) {
            return ZahoriStringUtils.capitalizeWithoutWhitespace(platformName.toLowerCase());
        }

        String platform = testCapabilities.get("platform");
        if (!StringUtils.isBlank(platform)) {
            return ZahoriStringUtils.capitalizeWithoutWhitespace(platform.toLowerCase());
        }

        return "Neither 'platformName' nor 'platform' capabilities have been specified, please set one of them.";
    }

    private static URL getRemoteUrl(Map<String, String> testCapabilities) {

        String remoteUrl = testCapabilities.get("remoteUrl");
        if (StringUtils.isBlank(remoteUrl)) {
            return null;
        }
        try {
            return new URL(ZahoriStringUtils.capitalizeWithoutWhitespace(remoteUrl.toLowerCase()));
        } catch (MalformedURLException e) {
            return null;
        }
    }
}
