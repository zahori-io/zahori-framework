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
import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.remote.AbstractDriverOptions;

/**
 * BrowserStack: a cloud grid, unreachable from a local BrowserMobProxy. Unlike Selenoid, it must
 * NOT receive the {@code selenoid:options} capability (bug fixed here — that capability used to
 * be added unconditionally for every remote execution), and it injects headers itself via the
 * native {@code bstack:options.headerParams} capability (already declared by the user via the
 * existing {@code zahori.test.capabilities.add.*} prefix mechanism — this class adds nothing
 * extra, it only tells the rest of the framework not to attempt local proxy header injection).
 * <p>
 * Registered as a Spring bean by {@code GridProviderAutoConfiguration} (not {@code @Component}
 * here directly — {@code io.zahori.framework} is typically outside a consuming process's own
 * component-scan base package).
 */
public class BrowserStackGridProvider implements GridProvider {

    @Override
    public String id() {
        return "browserstack";
    }

    @Override
    public boolean matches(String remoteUrl) {
        return StringUtils.containsIgnoreCase(remoteUrl, "browserstack");
    }

    @Override
    public void enrichCapabilities(AbstractDriverOptions<?> options, Browsers browsers) {
        // Nothing to add here: bstack:options.* capabilities already flow through the existing
        // zahori.test.capabilities.add.* prefix mechanism in CapabilitiesBuilder.
    }

    @Override
    public HeaderInjectionStrategy headerInjectionStrategy() {
        return HeaderInjectionStrategy.NATIVE_CAPABILITY;
    }
}
