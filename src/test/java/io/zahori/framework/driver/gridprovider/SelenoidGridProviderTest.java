package io.zahori.framework.driver.gridprovider;

import io.zahori.framework.driver.browserfactory.Browsers;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.MapAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openqa.selenium.chrome.ChromeOptions;

class SelenoidGridProviderTest {
    private final SelenoidGridProvider provider = new SelenoidGridProvider();

    @ParameterizedTest
    @ValueSource(
        strings = {"http://selenoid:4444/wd/hub", "http://localhost:4444/wd/hub", "http://127.0.0.1:4444/wd/hub", "https://my-selenoid-host.internal/wd/hub"}
    )
    void matches_knownSelenoidUrls(String remoteUrl) {
        Assertions.assertThat(this.provider.matches(remoteUrl)).isTrue();
    }

    @Test
    void doesNotMatch_browserStackUrl() {
        Assertions.assertThat(this.provider.matches("https://hub-cloud.browserstack.com/wd/hub")).isFalse();
    }

    @Test
    void doesNotMatch_blankOrNullUrl() {
        Assertions.assertThat(this.provider.matches(null)).isFalse();
        Assertions.assertThat(this.provider.matches("")).isFalse();
    }

    @Test
    void enrichCapabilities_addsSelenoidOptions() {
        Browsers browsers = new Browsers().withCaseExecution("case-1").withTestName("Test name").withScreenResolution("1920x1080x24");
        ChromeOptions options = new ChromeOptions();
        this.provider.enrichCapabilities(options, browsers);
        Object selenoidOptions = options.asMap().get("selenoid:options");
        Assertions.assertThat(selenoidOptions).isInstanceOf(Map.class);
        Map<String, Object> selenoidOptionsMap = (Map<String, Object>)selenoidOptions;
        ((MapAssert)Assertions.assertThat(selenoidOptionsMap).containsEntry("name", "case-1")).containsEntry("testName", "Test name");
    }

    @Test
    void headerInjectionStrategy_isBrowserMobProxy() {
        Assertions.assertThat(this.provider.headerInjectionStrategy()).isEqualTo(HeaderInjectionStrategy.BROWSERMOB_PROXY);
    }

    @Test
    void supportsLocalTunnel_isFalse_becauseTheGridIsAlreadyLocal() {
        Assertions.assertThat(this.provider.supportsLocalTunnel()).isFalse();
    }
}
