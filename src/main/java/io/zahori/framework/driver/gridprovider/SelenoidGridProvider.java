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
import io.zahori.framework.driver.browserfactory.Browsers;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.remote.AbstractDriverOptions;

/**
 * Selenoid: a remote grid running locally (same Docker network/host as this process), even
 * though it's accessed via {@code RemoteWebDriver} like any other remote provider. Reproduces
 * the {@code selenoid:options} block that {@code RemoteDriver.getOptions()} used to add
 * unconditionally for every remote execution.
 * <p>
 * Registered as a Spring bean by {@code GridProviderAutoConfiguration} (not {@code @Component}
 * here directly — {@code io.zahori.framework} is typically outside a consuming process's own
 * component-scan base package).
 */
public class SelenoidGridProvider implements GridProvider {

    @Override
    public String id() {
        return "selenoid";
    }

    @Override
    public boolean matches(String remoteUrl) {
        if (StringUtils.isBlank(remoteUrl)) {
            return false;
        }
        return StringUtils.containsIgnoreCase(remoteUrl, "selenoid")
                || StringUtils.containsIgnoreCase(remoteUrl, "localhost")
                || StringUtils.containsIgnoreCase(remoteUrl, "127.0.0.1");
    }

    @Override
    public void enrichCapabilities(AbstractDriverOptions<?> options, Browsers browsers) {
        Map<String, Object> selenoidOptions = new HashMap<>();
        selenoidOptions.put("name", browsers.getCaseExecutionId());
        selenoidOptions.put("testName", browsers.getTestName());
        selenoidOptions.put("enableVNC", true);
        selenoidOptions.put("enableVideo", false);
        selenoidOptions.put("screenResolution", browsers.getScreenResolution());

        options.setCapability("selenoid:options", selenoidOptions);
    }

    @Override
    public HeaderInjectionStrategy headerInjectionStrategy() {
        return HeaderInjectionStrategy.BROWSERMOB_PROXY;
    }
}
