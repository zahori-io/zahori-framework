package io.zahori.framework.driver.gridprovider;

import io.zahori.framework.driver.browserfactory.Browsers;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.remote.AbstractDriverOptions;

class GridProviderRegistryTest {
    private final GridProviderRegistry registry = new GridProviderRegistry(List.of(new SelenoidGridProvider(), new BrowserStackGridProvider()));

    @Test
    void resolvesSelenoidProvider_forSelenoidUrl() {
        GridProvider resolved = this.registry.resolveProvider("http://localhost:4444/wd/hub", true);
        Assertions.assertThat(resolved.id()).isEqualTo("selenoid");
    }

    @Test
    void resolvesBrowserStackProvider_forBrowserStackUrl() {
        GridProvider resolved = this.registry.resolveProvider("https://hub-cloud.browserstack.com/wd/hub", true);
        Assertions.assertThat(resolved.id()).isEqualTo("browserstack");
    }

    @Test
    void proxyStrategy_differsBetweenSelenoidAndBrowserStack() {
        GridProvider selenoid = this.registry.resolveProvider("http://localhost:4444/wd/hub", true);
        GridProvider browserStack = this.registry.resolveProvider("https://hub-cloud.browserstack.com/wd/hub", true);
        Assertions.assertThat(selenoid.headerInjectionStrategy()).isNotEqualTo(browserStack.headerInjectionStrategy());
        Assertions.assertThat(selenoid.headerInjectionStrategy()).isEqualTo(HeaderInjectionStrategy.BROWSERMOB_PROXY);
        Assertions.assertThat(browserStack.headerInjectionStrategy()).isEqualTo(HeaderInjectionStrategy.NATIVE_CAPABILITY);
    }

    @Test
    void resolvesGenericFallback_forUnknownUrl() {
        GridProvider resolved = this.registry.resolveProvider("https://some-unknown-grid.example.com/wd/hub", true);
        Assertions.assertThat(resolved.id()).isEqualTo("generic");
    }

    @Test
    void resolvesGenericFallback_forBlankUrl() {
        Assertions.assertThat(this.registry.resolveProvider(null, true).id()).isEqualTo("generic");
        Assertions.assertThat(this.registry.resolveProvider("", true).id()).isEqualTo("generic");
    }

    @Test
    void killSwitch_disabled_alwaysResolvesGenericFallback_regardlessOfUrl() {
        Assertions.assertThat(this.registry.resolveProvider("https://hub-cloud.browserstack.com/wd/hub", false).id()).isEqualTo("generic");
        Assertions.assertThat(this.registry.resolveProvider("http://localhost:4444/wd/hub", false).id()).isEqualTo("generic");
    }

    @Test
    void thirdPartyProvider_notShippedByTheFramework_isPickedUpByTheRegistryWithoutAnyFrameworkChange() {
        GridProvider fakeThirdPartyProvider = new GridProvider() {
            public String id() {
                return "fake-third-party";
            }

            public boolean matches(String remoteUrl) {
                return remoteUrl != null && remoteUrl.contains("fake-third-party-grid");
            }

            public void enrichCapabilities(AbstractDriverOptions<?> options, Browsers browsers) {
            }

            public HeaderInjectionStrategy headerInjectionStrategy() {
                return HeaderInjectionStrategy.NONE;
            }
        };
        GridProviderRegistry registryWithThirdParty = new GridProviderRegistry(
            List.of(new SelenoidGridProvider(), new BrowserStackGridProvider(), fakeThirdPartyProvider)
        );
        GridProvider resolved = registryWithThirdParty.resolveProvider("https://fake-third-party-grid.example.com/wd/hub", true);
        Assertions.assertThat(resolved.id()).isEqualTo("fake-third-party");
    }

    @Test
    void aProviderThatThrowsWhileMatching_isSkipped_notPropagated() {
        GridProvider brokenProvider = new GridProvider() {
            public String id() {
                return "broken";
            }

            public boolean matches(String remoteUrl) {
                throw new RuntimeException("boom");
            }

            public void enrichCapabilities(AbstractDriverOptions<?> options, Browsers browsers) {
            }

            public HeaderInjectionStrategy headerInjectionStrategy() {
                return HeaderInjectionStrategy.NONE;
            }
        };
        GridProviderRegistry registryWithBrokenProvider = new GridProviderRegistry(List.of(brokenProvider, new SelenoidGridProvider()));
        GridProvider resolved = registryWithBrokenProvider.resolveProvider("http://localhost:4444/wd/hub", true);
        Assertions.assertThat(resolved.id()).isEqualTo("selenoid");
    }

    @Test
    void vendorPrefixes_listsTheNamespaceOfEveryKnownProvider() {
        Assertions.assertThat(this.registry.vendorPrefixes()).containsExactlyInAnyOrder(new String[]{"selenoid:options", "bstack:options"});
    }

    @Test
    void eachProviderOwnsItsOwnNamespace() {
        Assertions.assertThat(new SelenoidGridProvider().vendorCapabilityPrefix()).isEqualTo("selenoid:options");
        Assertions.assertThat(new BrowserStackGridProvider().vendorCapabilityPrefix()).isEqualTo("bstack:options");
    }

    @Test
    void theFallbackClaimsNoNamespaceSoItFiltersNothing() {
        Assertions.assertThat(new GenericGridProvider().vendorCapabilityPrefix()).isEmpty();
    }
}
