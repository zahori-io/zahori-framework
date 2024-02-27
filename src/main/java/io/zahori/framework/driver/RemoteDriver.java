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
import io.github.bonigarcia.wdm.WebDriverManager;
import io.zahori.framework.driver.browserfactory.Browsers;
import io.zahori.framework.files.properties.ZahoriProperties;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.AbstractDriverOptions;
import org.openqa.selenium.remote.DesiredCapabilities;

public class RemoteDriver extends AbstractDriver {

    @Override
    protected WebDriver createWebDriver(Browsers browsers) {
        // TODO Temporal workaround for Appium driver creation: 1x1x24 for AndroidDriver, 2x2x24 for IOSDriver
        if ("1x1x24".equals(browsers.getScreenResolution()) || "2x2x24".equals(browsers.getScreenResolution())) {
            return getAppiumDriver(browsers);
        } else {
            AbstractDriverOptions<?> options = getOptions(browsers);
            final int timeoutSecondsForDriverCreation = 3600; // 60 minutos
            return WebDriverManager.getInstance(browsers.getName().toUpperCase())
                    .browserVersion(browsers.getVersion())
                    .remoteAddress(browsers.getRemoteUrl())
                    .capabilities(options)
                    .timeout(timeoutSecondsForDriverCreation)
                    .disableTracing()
                    .create();
        }
    }

    @Override
    protected void configureWebDriver(WebDriver webDriver, Browsers browsers) {
        if (webDriver instanceof AndroidDriver || webDriver instanceof IOSDriver) {

        } else {
            // Cualquier otra configuracion especifica de RemoteDriver
            webDriver.manage().window().maximize();
        }
    }

    @Override
    public AbstractDriverOptions<?> getOptions(Browsers browsers) {
        AbstractDriverOptions<?> options = super.getOptions(browsers);
        options.setBrowserVersion(browsers.getVersion());

        Map<String, Object> selenoidOptions = new HashMap<>();
        selenoidOptions.put("name", browsers.getCaseExecutionId());
        selenoidOptions.put("testName", browsers.getTestName());
        selenoidOptions.put("enableVNC", true);
        selenoidOptions.put("enableVideo", false);
        selenoidOptions.put("screenResolution", browsers.getScreenResolution());

        options.setCapability("selenoid:options", selenoidOptions);

        return options;
    }

    private WebDriver getAppiumDriver(Browsers browsers) {
        if ("1x1x24".equals(browsers.getScreenResolution())) {
            DesiredCapabilities capabilities = getAppiumCapabilities("android.");
            String remoteUrl = (String) capabilities.getCapability("remoteUrl");
            return new AndroidDriver(getUrl(remoteUrl), capabilities);
        }
        if ("2x2x24".equals(browsers.getScreenResolution())) {
            DesiredCapabilities capabilities = getAppiumCapabilities("ios.");
            String remoteUrl = (String) capabilities.getCapability("remoteUrl");
            return new IOSDriver(getUrl(remoteUrl), capabilities);
        }
        throw new RuntimeException("No supported AppiumDriver: Set screenResolution to 1x1 for Android or set screenResolution to 2x2 for iOS");
    }

    private boolean isBoolean(String input) {
        return StringUtils.equalsIgnoreCase("true", input) || StringUtils.equalsIgnoreCase("false", input);
    }

    private DesiredCapabilities getAppiumCapabilities(String prefix) {
        Map<String, String> extraCapabilities = new ZahoriProperties().getExtraCapabilities();

        System.out.println("extraCapabilities: " + extraCapabilities.toString());

        DesiredCapabilities capabilities = new DesiredCapabilities();

        extraCapabilities.forEach((extraCap, value) -> {
            if (extraCap.startsWith(prefix)) {
                String capabilityKey = extraCap.replaceFirst(prefix, StringUtils.EMPTY);

                if (isBoolean(value)) {
                    capabilities.setCapability(capabilityKey, Boolean.valueOf(value));
                } else {
                    capabilities.setCapability(capabilityKey, value);
                }
            }
        });

        System.out.println("Appium capabilities: " + capabilities.toString());

        /*
        // platformName: android iOS
        capabilities.setCapability("platformName", "android");
        // Choose test app or web browser:
        // - App:
        // capabilities.setCapability("appium:app", "storage:filename=mda-1.0.16-19.apk");
        // - Web Browser:
        capabilities.setCapability("browserName", "chrome");
        // W3C Protocol is mandatory for Appium 2
        //capabilities.setCapability("appium:platformVersion", "12");
        // capabilities.setCapability("appium:deviceName", "Samsung Galaxy S9"); // atributo name en el api
        // capabilities.setCapability("appium:orientation", "portrait");
        // capabilities.setCapability("appium:app", "storage:filename=<file-name>");
        // Mandatory for Appium 2
        capabilities.setCapability("appium:automationName", "uiautomator2");
         */
 /*
        HashMap<String, Object> sauceOptions = new HashMap<>();
        // appiumVersion is mandatory to use Appium 2
//        sauceOptions.put("appiumVersion", "2.0.0-beta56");
        //sauceOptions.put("username", System.getenv("SAUCE_USERNAME"));
        //sauceOptions.put("accessKey", System.getenv("SAUCE_ACCESS_KEY"));
        sauceOptions.put("build", "Build 1"); // <your build id>
        sauceOptions.put("name", "Test wikipedia on Samsung Galaxy S9"); // <your test name>

         */
        ///// capabilities.setCapability("sauce:options", sauceOptions);
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
