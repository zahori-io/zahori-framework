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

/**
 * How a {@link GridProvider} expects request headers (and, more generally, the local
 * BrowserMobProxy used for headers/blacklist/HAR capture) to reach the browser under test.
 */
public enum HeaderInjectionStrategy {

    /**
     * The browser under test can reach a BrowserMobProxy running on this machine (Selenoid: same
     * Docker network/host as this process). {@code TestContext.getProxyIP()} resolves the actual
     * reachable address (localhost / local IP / host.docker.internal depending on OS and mobile
     * web app flag) — this strategy value only says "that resolution is meaningful here".
     */
    BROWSERMOB_PROXY,

    /**
     * The grid provider injects headers itself via a native capability (e.g. BrowserStack's
     * {@code bstack:options.headerParams}). A local BrowserMobProxy is never reachable from a
     * cloud grid, so no proxy should be created for header purposes.
     */
    NATIVE_CAPABILITY,

    /**
     * No header injection support (e.g. a physical local driver, or a grid provider that simply
     * doesn't offer it). No proxy should be created for header purposes.
     */
    NONE
}
