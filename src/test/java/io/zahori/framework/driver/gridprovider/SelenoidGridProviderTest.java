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
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openqa.selenium.chrome.ChromeOptions;

class SelenoidGridProviderTest {

    private final SelenoidGridProvider provider = new SelenoidGridProvider();

    @ParameterizedTest
    @ValueSource(strings = {
            "http://selenoid:4444/wd/hub",
            "http://localhost:4444/wd/hub",
            "http://127.0.0.1:4444/wd/hub",
            "https://my-selenoid-host.internal/wd/hub"
    })
    void matches_knownSelenoidUrls(String remoteUrl) {
        assertThat(provider.matches(remoteUrl)).isTrue();
    }

    @Test
    void doesNotMatch_browserStackUrl() {
        assertThat(provider.matches("https://hub-cloud.browserstack.com/wd/hub")).isFalse();
    }

    @Test
    void doesNotMatch_blankOrNullUrl() {
        assertThat(provider.matches(null)).isFalse();
        assertThat(provider.matches("")).isFalse();
    }

    @Test
    void enrichCapabilities_addsSelenoidOptions() {
        Browsers browsers = new Browsers().withCaseExecution("case-1").withTestName("Test name")
                .withScreenResolution("1920x1080x24");
        ChromeOptions options = new ChromeOptions();

        provider.enrichCapabilities(options, browsers);

        Object selenoidOptions = options.asMap().get("selenoid:options");
        assertThat(selenoidOptions).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> selenoidOptionsMap = (Map<String, Object>) selenoidOptions;
        assertThat(selenoidOptionsMap).containsEntry("name", "case-1").containsEntry("testName", "Test name");
    }

    @Test
    void headerInjectionStrategy_isBrowserMobProxy() {
        assertThat(provider.headerInjectionStrategy()).isEqualTo(HeaderInjectionStrategy.BROWSERMOB_PROXY);
    }
}
