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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openqa.selenium.chrome.ChromeOptions;

class BrowserStackGridProviderTest {

    private final BrowserStackGridProvider provider = new BrowserStackGridProvider();

    @ParameterizedTest
    @ValueSource(strings = {
            "https://hub-cloud.browserstack.com/wd/hub",
            "https://hub.browserstack.com/wd/hub"
    })
    void matches_knownBrowserStackUrls(String remoteUrl) {
        assertThat(provider.matches(remoteUrl)).isTrue();
    }

    @Test
    void doesNotMatch_selenoidUrl() {
        assertThat(provider.matches("http://localhost:4444/wd/hub")).isFalse();
    }

    @Test
    void doesNotMatch_blankOrNullUrl() {
        assertThat(provider.matches(null)).isFalse();
        assertThat(provider.matches("")).isFalse();
    }

    @Test
    void enrichCapabilities_doesNotAddSelenoidOptions() {
        // Regression test for the bug that motivated this class: selenoid:options used to be
        // added unconditionally for every remote grid, including BrowserStack.
        Browsers browsers = new Browsers().withCaseExecution("case-1");
        ChromeOptions options = new ChromeOptions();

        provider.enrichCapabilities(options, browsers);

        assertThat(options.asMap()).doesNotContainKey("selenoid:options");
    }

    @Test
    void headerInjectionStrategy_isNativeCapability() {
        assertThat(provider.headerInjectionStrategy()).isEqualTo(HeaderInjectionStrategy.NATIVE_CAPABILITY);
    }
}
