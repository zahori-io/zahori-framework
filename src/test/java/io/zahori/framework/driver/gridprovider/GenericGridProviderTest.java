package io.zahori.framework.driver.gridprovider;

import io.zahori.framework.driver.browserfactory.Browsers;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.chrome.ChromeOptions;

class GenericGridProviderTest {
    private final GenericGridProvider provider = new GenericGridProvider();

    @Test
    void neverMatches_anyUrl() {
        Assertions.assertThat(this.provider.matches("https://hub-cloud.browserstack.com/wd/hub")).isFalse();
        Assertions.assertThat(this.provider.matches("http://localhost:4444/wd/hub")).isFalse();
        Assertions.assertThat(this.provider.matches("https://some-unknown-grid.example.com/wd/hub")).isFalse();
        Assertions.assertThat(this.provider.matches(null)).isFalse();
    }

    @Test
    void enrichCapabilities_addsSelenoidOptions_forFullBackwardCompatibility() {
        Browsers browsers = new Browsers().withCaseExecution("case-1");
        ChromeOptions options = new ChromeOptions();
        this.provider.enrichCapabilities(options, browsers);
        Assertions.assertThat(options.asMap()).containsKey("selenoid:options");
    }

    @Test
    void headerInjectionStrategy_isBrowserMobProxy_forFullBackwardCompatibility() {
        Assertions.assertThat(this.provider.headerInjectionStrategy()).isEqualTo(HeaderInjectionStrategy.BROWSERMOB_PROXY);
    }

    @Test
    void supportsLocalTunnel_isFalse_byDefault() {
        Assertions.assertThat(this.provider.supportsLocalTunnel()).isFalse();
    }
}
