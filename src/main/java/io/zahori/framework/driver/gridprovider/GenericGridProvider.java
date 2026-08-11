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
import org.openqa.selenium.remote.AbstractDriverOptions;

/**
 * Fallback used by {@link GridProviderRegistry} whenever no registered {@link GridProvider}
 * recognizes the remote URL (unknown grid), or when provider autodetection is disabled via the
 * {@code zahori.test.execution.gridProvider.autodetect.enabled} kill-switch. Deliberately
 * reproduces the framework's pre-{@code GridProvider} behavior byte for byte — it always added
 * {@code selenoid:options} for any remote execution and always resolved the proxy IP via the
 * OS-based heuristic — so a process pointed at a grid this framework doesn't recognize behaves
 * exactly as it did before this abstraction existed. Never registered as a Spring bean: it's a
 * plain last-resort default, not something a URL should ever "match".
 */
public class GenericGridProvider implements GridProvider {

    @Override
    public String id() {
        return "generic";
    }

    @Override
    public boolean matches(String remoteUrl) {
        return false;
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
