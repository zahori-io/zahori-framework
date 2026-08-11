package io.zahori.framework.driver.gridprovider;

/*-
 * #%L
 * zahori-framework
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2021 - 2026 PANEL SISTEMAS INFORMATICOS,S.L
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
import static org.assertj.core.api.Assertions.assertThat;

import io.zahori.framework.driver.browserfactory.Browsers;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.remote.AbstractDriverOptions;

class GridProviderRegistryTest {

    private final GridProviderRegistry registry = new GridProviderRegistry(
            List.of(new SelenoidGridProvider(), new BrowserStackGridProvider()));

    @Test
    void resolvesSelenoidProvider_forSelenoidUrl() {
        GridProvider resolved = registry.resolveProvider("http://localhost:4444/wd/hub", true);

        assertThat(resolved.id()).isEqualTo("selenoid");
    }

    @Test
    void resolvesBrowserStackProvider_forBrowserStackUrl() {
        GridProvider resolved = registry.resolveProvider("https://hub-cloud.browserstack.com/wd/hub", true);

        assertThat(resolved.id()).isEqualTo("browserstack");
    }

    @Test
    void proxyStrategy_differsBetweenSelenoidAndBrowserStack() {
        // Regression test for bug 3.1: getProxyIP() used to assume every remote grid was
        // Selenoid-in-Docker, breaking header injection against BrowserStack.
        GridProvider selenoid = registry.resolveProvider("http://localhost:4444/wd/hub", true);
        GridProvider browserStack = registry.resolveProvider("https://hub-cloud.browserstack.com/wd/hub", true);

        assertThat(selenoid.headerInjectionStrategy()).isNotEqualTo(browserStack.headerInjectionStrategy());
        assertThat(selenoid.headerInjectionStrategy()).isEqualTo(HeaderInjectionStrategy.BROWSERMOB_PROXY);
        assertThat(browserStack.headerInjectionStrategy()).isEqualTo(HeaderInjectionStrategy.NATIVE_CAPABILITY);
    }

    @Test
    void resolvesGenericFallback_forUnknownUrl() {
        GridProvider resolved = registry.resolveProvider("https://some-unknown-grid.example.com/wd/hub", true);

        assertThat(resolved.id()).isEqualTo("generic");
    }

    @Test
    void resolvesGenericFallback_forBlankUrl() {
        assertThat(registry.resolveProvider(null, true).id()).isEqualTo("generic");
        assertThat(registry.resolveProvider("", true).id()).isEqualTo("generic");
    }

    @Test
    void killSwitch_disabled_alwaysResolvesGenericFallback_regardlessOfUrl() {
        // With gridProvider.autodetect.enabled=false, behavior must be indistinguishable from
        // the code that existed before GridProvider was introduced.
        assertThat(registry.resolveProvider("https://hub-cloud.browserstack.com/wd/hub", false).id()).isEqualTo("generic");
        assertThat(registry.resolveProvider("http://localhost:4444/wd/hub", false).id()).isEqualTo("generic");
    }

    @Test
    void thirdPartyProvider_notShippedByTheFramework_isPickedUpByTheRegistryWithoutAnyFrameworkChange() {
        // Simulates a consuming process contributing its own GridProvider (e.g. for SauceLabs) —
        // the registry itself needs no change to resolve it correctly.
        GridProvider fakeThirdPartyProvider = new GridProvider() {
            @Override
            public String id() {
                return "fake-third-party";
            }

            @Override
            public boolean matches(String remoteUrl) {
                return remoteUrl != null && remoteUrl.contains("fake-third-party-grid");
            }

            @Override
            public void enrichCapabilities(AbstractDriverOptions<?> options, Browsers browsers) {
                // no-op
            }

            @Override
            public HeaderInjectionStrategy headerInjectionStrategy() {
                return HeaderInjectionStrategy.NONE;
            }
        };
        GridProviderRegistry registryWithThirdParty = new GridProviderRegistry(
                List.of(new SelenoidGridProvider(), new BrowserStackGridProvider(), fakeThirdPartyProvider));

        GridProvider resolved = registryWithThirdParty.resolveProvider("https://fake-third-party-grid.example.com/wd/hub", true);

        assertThat(resolved.id()).isEqualTo("fake-third-party");
    }

    @Test
    void aProviderThatThrowsWhileMatching_isSkipped_notPropagated() {
        GridProvider brokenProvider = new GridProvider() {
            @Override
            public String id() {
                return "broken";
            }

            @Override
            public boolean matches(String remoteUrl) {
                throw new RuntimeException("boom");
            }

            @Override
            public void enrichCapabilities(AbstractDriverOptions<?> options, Browsers browsers) {
                // no-op
            }

            @Override
            public HeaderInjectionStrategy headerInjectionStrategy() {
                return HeaderInjectionStrategy.NONE;
            }
        };
        GridProviderRegistry registryWithBrokenProvider = new GridProviderRegistry(List.of(brokenProvider, new SelenoidGridProvider()));

        GridProvider resolved = registryWithBrokenProvider.resolveProvider("http://localhost:4444/wd/hub", true);

        assertThat(resolved.id()).isEqualTo("selenoid");
    }
}
