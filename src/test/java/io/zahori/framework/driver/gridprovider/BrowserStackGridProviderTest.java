package io.zahori.framework.driver.gridprovider;

import io.zahori.framework.driver.browserfactory.Browsers;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openqa.selenium.chrome.ChromeOptions;

class BrowserStackGridProviderTest {
    private final BrowserStackGridProvider provider = new BrowserStackGridProvider();

    @ParameterizedTest
    @ValueSource(
        strings = {"https://hub-cloud.browserstack.com/wd/hub", "https://hub.browserstack.com/wd/hub"}
    )
    void matches_knownBrowserStackUrls(String remoteUrl) {
        Assertions.assertThat(this.provider.matches(remoteUrl)).isTrue();
    }

    @Test
    void doesNotMatch_selenoidUrl() {
        Assertions.assertThat(this.provider.matches("http://localhost:4444/wd/hub")).isFalse();
    }

    @Test
    void doesNotMatch_blankOrNullUrl() {
        Assertions.assertThat(this.provider.matches(null)).isFalse();
        Assertions.assertThat(this.provider.matches("")).isFalse();
    }

    @Test
    void enrichCapabilities_doesNotAddSelenoidOptions() {
        Browsers browsers = new Browsers().withCaseExecution("case-1");
        ChromeOptions options = new ChromeOptions();
        this.provider.enrichCapabilities(options, browsers);
        Assertions.assertThat(options.asMap()).doesNotContainKey("selenoid:options");
    }

    @Test
    void headerInjectionStrategy_isNativeCapability() {
        Assertions.assertThat(this.provider.headerInjectionStrategy()).isEqualTo(HeaderInjectionStrategy.NATIVE_CAPABILITY);
    }

    @Test
    void supportsLocalTunnel_becauseTheBrowserRunsInTheCloud() {
        Assertions.assertThat(this.provider.supportsLocalTunnel()).isTrue();
    }
}
