package io.zahori.framework.driver;

import io.zahori.framework.driver.browserfactory.Browsers;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.remote.DesiredCapabilities;

class RemoteDriverTest {
    private final RemoteDriver remoteDriver = new RemoteDriver();

    @Test
    void nativeAppEnvironment_setsAppCapabilityAndRemovesBrowserName() {
        Browsers browsers = new Browsers().withPlatform("ANDROID").withEnvironmentUrl("bs://a7b8b3640114c85db8035330979073ba1ffab6a6");
        DesiredCapabilities capabilities = this.remoteDriver.getAppiumCapabilities(browsers);
        Assertions.assertEquals("bs://a7b8b3640114c85db8035330979073ba1ffab6a6", capabilities.getCapability("appium:app"));
        Assertions.assertNull(capabilities.getCapability("browserName"));
    }

    @Test
    void webEnvironment_removesAppCapabilityAndCopiesDeviceInfoToBstackOptions() {
        Browsers browsers = new Browsers().withPlatform("ANDROID").withEnvironmentUrl("https://stg.aireuropa.com/");
        DesiredCapabilities capabilities = this.remoteDriver.getAppiumCapabilities(browsers);
        Assertions.assertEquals("chrome", capabilities.getCapability("browserName"));
        Assertions.assertNull(capabilities.getCapability("appium:app"));
        Map<String, Object> bstackOptions = (Map<String, Object>)capabilities.getCapability("bstack:options");
        Assertions.assertEquals("Test Device", bstackOptions.get("deviceName"));
        Assertions.assertEquals("15.0", bstackOptions.get("platformVersion"));
    }

    @Test
    void appiumOnBrowserStackDoesNotReceiveSelenoidCapabilities() {
        Browsers browsers = new Browsers()
            .withPlatform("ANDROID")
            .withEnvironmentUrl("https://stg.aireuropa.com/")
            .withRemoteUrl("https://hub.browserstack.com/wd/hub");
        DesiredCapabilities capabilities = this.remoteDriver.getAppiumCapabilities(browsers);
        Assertions.assertNull(capabilities.getCapability("selenoid:options"));
        Assertions.assertNotNull(capabilities.getCapability("bstack:options"));
    }
}
