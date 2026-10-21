package io.zahori.framework.driver;

import io.zahori.framework.driver.browserfactory.Browsers;
import io.zahori.framework.driver.gridprovider.BrowserStackGridProvider;
import io.zahori.framework.driver.gridprovider.GenericGridProvider;
import io.zahori.framework.driver.gridprovider.SelenoidGridProvider;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.remote.DesiredCapabilities;

class CapabilitiesBuilderTest {
    private static final String SELENOID = "selenoid:options";
    private static final String BSTACK = "bstack:options";
    private static final String DESCONOCIDO = "otrogrid:options";

    private Browsers browsers() {
        return new Browsers()
            .withName("chrome")
            .withPlatform("windows")
            .withScreenResolution("1920x1080x24")
            .withRemote("YES")
            .withTestName("TC")
            .withCaseExecution("1")
            .withExecution(1L)
            .withEnvironmentUrl("https://example.com")
            .withEnvironmentName("Test");
    }

    @Test
    void browserStackDoesNotReceiveSelenoidCapabilities() {
        DesiredCapabilities capabilities = CapabilitiesBuilder.getCapabilities(this.browsers(), new BrowserStackGridProvider());
        Assertions.assertThat(capabilities.getCapability("selenoid:options")).isNull();
        Assertions.assertThat(capabilities.getCapability("bstack:options")).isNotNull();
    }

    @Test
    void selenoidDoesNotReceiveBrowserStackCapabilities() {
        DesiredCapabilities capabilities = CapabilitiesBuilder.getCapabilities(this.browsers(), new SelenoidGridProvider());
        Assertions.assertThat(capabilities.getCapability("bstack:options")).isNull();
        Assertions.assertThat(capabilities.getCapability("selenoid:options")).isNotNull();
    }

    @Test
    void anUnclaimedNamespaceAlwaysTravels() {
        Assertions.assertThat(CapabilitiesBuilder.getCapabilities(this.browsers(), new BrowserStackGridProvider()).getCapability("otrogrid:options"))
            .isNotNull();
        Assertions.assertThat(CapabilitiesBuilder.getCapabilities(this.browsers(), new SelenoidGridProvider()).getCapability("otrogrid:options")).isNotNull();
    }

    @Test
    void withoutAGridProviderNothingIsFiltered() {
        DesiredCapabilities capabilities = CapabilitiesBuilder.getCapabilities(this.browsers());
        Assertions.assertThat(capabilities.getCapability("selenoid:options")).isNotNull();
        Assertions.assertThat(capabilities.getCapability("bstack:options")).isNotNull();
        Assertions.assertThat(capabilities.getCapability("otrogrid:options")).isNotNull();
    }

    @Test
    void theGenericFallbackFiltersNothing() {
        DesiredCapabilities capabilities = CapabilitiesBuilder.getCapabilities(this.browsers(), new GenericGridProvider());
        Assertions.assertThat(capabilities.getCapability("selenoid:options")).isNotNull();
        Assertions.assertThat(capabilities.getCapability("bstack:options")).isNotNull();
    }

    @Test
    void platformPrefixedCapabilitiesStillResolve() {
        DesiredCapabilities capabilities = CapabilitiesBuilder.getCapabilities(this.browsersOn("android"), new BrowserStackGridProvider());
        Assertions.assertThat(capabilities.getCapability("platformName")).hasToString("ANDROID");
        Assertions.assertThat(capabilities.getCapability("selenoid:options")).isNull();
    }

    @Test
    void eachPlatformGetsOnlyItsOwnValueOfTheSameCapability() {
        Assertions.assertThat(this.headerParamsOn("osx")).isEqualTo("solo-osx");
        Assertions.assertThat(this.headerParamsOn("windows")).isEqualTo("solo-windows");
        Assertions.assertThat(this.headerParamsOn("ios")).isEqualTo("solo-ios");
        Assertions.assertThat(this.headerParamsOn("android")).isNull();
    }

    private Object headerParamsOn(String platform) {
        Object bstack = CapabilitiesBuilder.getCapabilities(this.browsersOn(platform), new BrowserStackGridProvider()).getCapability("bstack:options");
        return bstack == null ? null : ((Map)bstack).get("headerParams");
    }

    private Browsers browsersOn(String platform) {
        return new Browsers()
            .withName("chrome")
            .withPlatform(platform)
            .withScreenResolution("1920x1080x24")
            .withRemote("YES")
            .withTestName("TC")
            .withCaseExecution("1")
            .withExecution(1L)
            .withEnvironmentUrl("https://example.com")
            .withEnvironmentName("Test");
    }
}
