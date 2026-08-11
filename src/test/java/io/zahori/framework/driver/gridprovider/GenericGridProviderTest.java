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
import org.junit.jupiter.api.Test;
import org.openqa.selenium.chrome.ChromeOptions;

/**
 * The fallback used whenever no URL matches: must reproduce the framework's pre-GridProvider
 * behavior exactly (always add selenoid:options, use BrowserMobProxy-based header injection).
 */
class GenericGridProviderTest {

    private final GenericGridProvider provider = new GenericGridProvider();

    @Test
    void neverMatches_anyUrl() {
        assertThat(provider.matches("https://hub-cloud.browserstack.com/wd/hub")).isFalse();
        assertThat(provider.matches("http://localhost:4444/wd/hub")).isFalse();
        assertThat(provider.matches("https://some-unknown-grid.example.com/wd/hub")).isFalse();
        assertThat(provider.matches(null)).isFalse();
    }

    @Test
    void enrichCapabilities_addsSelenoidOptions_forFullBackwardCompatibility() {
        Browsers browsers = new Browsers().withCaseExecution("case-1");
        ChromeOptions options = new ChromeOptions();

        provider.enrichCapabilities(options, browsers);

        assertThat(options.asMap()).containsKey("selenoid:options");
    }

    @Test
    void headerInjectionStrategy_isBrowserMobProxy_forFullBackwardCompatibility() {
        assertThat(provider.headerInjectionStrategy()).isEqualTo(HeaderInjectionStrategy.BROWSERMOB_PROXY);
    }
}
