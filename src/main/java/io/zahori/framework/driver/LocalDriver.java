package io.zahori.framework.driver;

/*-
 * #%L
 * zahori-framework
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2021 - 2022 PANEL SISTEMAS INFORMATICOS,S.L
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

import io.github.bonigarcia.wdm.WebDriverManager;
import io.zahori.framework.driver.browserfactory.Browsers;
import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.AbstractDriverOptions;

import java.time.Duration;

public class LocalDriver extends AbstractDriver {

    private void resizeWindow(WebDriver driver, Browsers browsers) {
        String resolution = browsers.getScreenResolution();
        driver.manage().window().setSize(new Dimension(Integer.valueOf(resolution.split("x")[0]).intValue(), Integer.valueOf(resolution.split("x")[1]).intValue()));
    }

    @Override
    protected WebDriver createWebDriver(Browsers browsers) {
        return WebDriverManager.getInstance(browsers.getName().toUpperCase())
                .capabilities(getOptions(browsers))
                .create();
    }

    // Si hay configuraciones específicas para LocalDriver, puedes sobrescribir `configureWebDriver` aquí

    @Override
    protected void configureWebDriver(WebDriver webDriver, Browsers browsers) {
        resizeWindow(webDriver, browsers);
    }
}
