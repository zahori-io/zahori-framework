package io.zahori.framework.driver;

/*-
 * #%L
 * zahori-framework
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2021 - 2024 PANEL SISTEMAS INFORMATICOS,S.L
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
import org.openqa.selenium.WebDriver;

/**
 * Factory class to create WebDriver instances.
 */
public class DriverFactory implements AbstractFactory<WebDriver> {

    /**
     * Creates a WebDriver instance based on the specified browser settings.
     *
     * @param browsers The browser settings used to configure the WebDriver.
     * @return A WebDriver instance.
     */
    @Override
    public WebDriver create(Browsers browsers) {
        return isRemoteExecution(browsers) ? createRemoteDriver(browsers) : createLocalDriver(browsers);
    }

    /**
     * Determines if the execution should be remote based on the browser settings.
     *
     * @param browsers The browser settings.
     * @return true if execution is remote, false otherwise.
     */
    private boolean isRemoteExecution(Browsers browsers) {
        return ExecutionType.REMOTE.getName().equals(browsers.getRemote());
    }

    /**
     * Creates a remote WebDriver based on the browser settings.
     *
     * @param browsers The browser settings.
     * @return A remote WebDriver instance.
     */
    private WebDriver createRemoteDriver(Browsers browsers) {
        return new RemoteDriver().getDriver(browsers);
    }

    /**
     * Creates a local WebDriver based on the browser settings.
     *
     * @param browsers The browser settings.
     * @return A local WebDriver instance.
     */
    private WebDriver createLocalDriver(Browsers browsers) {
        return new LocalDriver().getDriver(browsers);
    }
}
