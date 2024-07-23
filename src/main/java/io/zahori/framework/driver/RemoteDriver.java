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
        try {
            // TODO Temporal workaround for Appium driver creation
            if ("ANDROID".equalsIgnoreCase(browsers.getPlatform())
                    || "IOS".equalsIgnoreCase(browsers.getPlatform())) {
                return getAppiumDriver(browsers);
            } else {

                AbstractDriverOptions<?> options = getOptions(browsers);
                final int timeoutSecondsForDriverCreation = 3600; // 3600 = 60 minutos
                return WebDriverManager.getInstance(browsers.getName().toUpperCase())
                        .browserVersion(browsers.getVersion())
                        .remoteAddress(browsers.getRemoteUrl())
                        .capabilities(options)
                        .timeout(timeoutSecondsForDriverCreation)
                        .disableTracing()
                        .create();
            }
        } catch (Exception e) {
            throw new RuntimeException(getExceptionMessage(e, browsers));
        }
    }

    private String getExceptionMessage(Exception e, Browsers browsers) {
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
            if (StringUtils.containsIgnoreCase(causeMessage, "Timeout")) {
                return causeMessage + "(remote server: " + browsers.getRemoteUrl() + ")";
            }
            if (StringUtils.containsIgnoreCase(causeMessage, "Conexión rehusada")
                    || StringUtils.containsIgnoreCase(causeMessage, "Connection refused")) {
                return "Connection refused from remote server " + browsers.getRemoteUrl() + ". Check that the remote server is up and there is connectivity with it.";
            }
            // BrowserStack errors:
            if (StringUtils.containsIgnoreCase(browsers.getRemoteUrl(), "browserstack")) {
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
            }

            exceptionMessage = exceptionMessage + ". " + causeMessage;
            System.out.println(">>> Cause: " + exceptionMessage);
            cause = cause.getCause();
        }
        return exceptionMessage;
    }

    private String getCleanMessage(String message) {
        return StringUtils.substringBefore(StringUtils.substringBefore(message, "Host info"), "Build info");
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
        DesiredCapabilities capabilities = getAppiumCapabilities(browsers);

        if ("ANDROID".equalsIgnoreCase(browsers.getPlatform())) {
            return new AndroidDriver(getUrl(browsers.getRemoteUrl()), capabilities);
        }
        if ("IOS".equalsIgnoreCase(browsers.getPlatform())) {
            return new IOSDriver(getUrl(browsers.getRemoteUrl()), capabilities);
        }
        throw new RuntimeException("No supported AppiumDriver: run an execution with a Configuration containing 'Android' or 'iOS' in the name");
    }

    private boolean isBoolean(String input) {
        return StringUtils.equalsIgnoreCase("true", input) || StringUtils.equalsIgnoreCase("false", input);
    }

    private DesiredCapabilities getAppiumCapabilities(Browsers browsers) {
        String prefix = browsers.getPlatform().toLowerCase() + ".";
        Map<String, String> extraCapabilities = new ZahoriProperties().getExtraCapabilities();

        DesiredCapabilities capabilities = new DesiredCapabilities();
        HashMap<String, Object> cloudOptions = new HashMap<>();
        String cloudOptionsKey = "";

        for (Map.Entry<String, String> entry : extraCapabilities.entrySet()) {
            String capabilityKey = entry.getKey();

            if (capabilityKey.startsWith(prefix)) {
                capabilityKey = capabilityKey.replaceFirst(prefix, StringUtils.EMPTY);
                String capabilityValue = getCapabilityValue(browsers, entry.getValue());

                if (StringUtils.startsWith(capabilityKey, "cloud.")) {
                    String cloudOptionAndCapabilityNameString = capabilityKey.replaceFirst("cloud.", StringUtils.EMPTY);
                    String[] cloudOptionAndCapabilityName = StringUtils.split(cloudOptionAndCapabilityNameString, ".");
                    if (cloudOptionAndCapabilityName.length > 1) {
                        cloudOptionsKey = cloudOptionAndCapabilityName[0];
                        cloudOptions.put(cloudOptionAndCapabilityName[1], capabilityValue);
                    }
                } else {
                    if (isBoolean(capabilityValue)) {
                        capabilities.setCapability(capabilityKey, Boolean.valueOf(capabilityValue));
                    } else {
                        capabilities.setCapability(capabilityKey, capabilityValue);
                    }
                }
            }
        }

        // TODO: remove. This is a temporal solution for mobile testing.
        // This url is used to indicate the id of the app artifact uploaded in the cloud farm (browserstack, ...)
        if (StringUtils.startsWithIgnoreCase(browsers.getEnvironmentUrl(), "bs://")
                && capabilities.getCapability("app") == null) {
            // overwrite 'app' capability value defined in zahori.properties with environment url from selected configuration
            capabilities.setCapability("app", browsers.getEnvironmentUrl());
        }

        if (StringUtils.isNotBlank(cloudOptionsKey) && !cloudOptions.isEmpty()) {
            setAppSettingsForBrowserStack(cloudOptions, browsers);
            capabilities.setCapability(cloudOptionsKey, cloudOptions);
        }

        System.out.println(
                "Appium capabilities: " + capabilities.toString());

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
		 capabilities.setCapability("appium:deviceName", "Samsung Galaxy S9");  atributo name en el api
		// capabilities.setCapability("appium:orientation", "portrait");
		// capabilities.setCapability("appium:app", "storage:filename=<file-name>");
		// Mandatory for Appium 2
		capabilities.setCapability("appium:automationName", "uiautomator2");
         */
 /*
		HashMap<String, Object> sauceOptions = new HashMap<>();
		// appiumVersion is mandatory to use Appium 2
                // sauceOptions.put("appiumVersion", "2.0.0-beta56");
		// sauceOptions.put("username", System.getenv("SAUCE_USERNAME"));
		// sauceOptions.put("accessKey", System.getenv("SAUCE_ACCESS_KEY"));
		sauceOptions.put("build", "Build 1"); // <your build id>
		sauceOptions.put("name", "Test wikipedia on Samsung Galaxy S9"); // <your test name>

                // capabilities.setCapability("sauce:options", sauceOptions);
         */
        return capabilities;
    }

    private String getCapabilityValue(Browsers browsers, String capabilityValue) {
        if (StringUtils.contains(capabilityValue, "{platform}")) {
            capabilityValue = StringUtils.replace(capabilityValue, "{platform}", browsers.getPlatform().toLowerCase());
        }
        if (StringUtils.contains(capabilityValue, "{executionId}")) {
            capabilityValue = StringUtils.replace(capabilityValue, "{executionId}", browsers.getExecutionId().toString());
        }
        if (StringUtils.contains(capabilityValue, "{caseExecutionId}")) {
            capabilityValue = StringUtils.replace(capabilityValue, "{caseExecutionId}", browsers.getCaseExecutionId());
        }
        if (StringUtils.contains(capabilityValue, "{caseName}")) {
            capabilityValue = StringUtils.replace(capabilityValue, "{caseName}", browsers.getTestName());
        }

        return capabilityValue;
    }

    private URL getUrl(String url) {
        try {
            return new URL(url);
        } catch (MalformedURLException ex) {
            throw new RuntimeException("Error parsing URL '" + url + "': " + ex.getMessage());
        }
    }

    /*
        TODO
        https://www.browserstack.com/docs/app-automate/appium/advanced-features/ios-app-settings
        https://www.browserstack.com/docs/app-automate/appium/advanced-features/ios-app-settings#App-specific_permission_settings
        https://www.browserstack.com/docs/app-automate/appium/advanced-features/ios-app-settings#App_settings_added_via_iOS_Settings_bundle
     */
    private void setAppSettingsForBrowserStack(HashMap<String, Object> cloudOptions, Browsers browsers) {
        if (!"IOS".equalsIgnoreCase(browsers.getPlatform())) {
            return;
        }

        String environment = browsers.getEnvironmentName();
        HashMap<String, Object> iosAppSettings = new HashMap<>();

        if (StringUtils.containsIgnoreCase(environment, "STG")) {
            iosAppSettings.put("Enviroment", "STG");
        }
        if (StringUtils.containsIgnoreCase(environment, "PRO")) {
            iosAppSettings.put("Enviroment", "PRO");
        }

        if (StringUtils.containsIgnoreCase(environment, "QA")) {
            iosAppSettings.put("Enviroment", "QA");
        }

        if (StringUtils.containsIgnoreCase(environment, "PRE")) {
            iosAppSettings.put("Enviroment", "PRE");
        }

        if (!iosAppSettings.isEmpty()) {
            cloudOptions.put("updateAppSettings", iosAppSettings);
        }
    }

}
