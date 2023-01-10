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
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.AbstractDriverOptions;
import org.openqa.selenium.remote.LocalFileDetector;
import org.openqa.selenium.remote.RemoteWebDriver;

import java.util.HashMap;
import java.util.Map;

public class RemoteDriver implements Driver{
    public WebDriver getDriver(Browsers browsers){
        AbstractDriverOptions<?> options = getOptions(browsers);
        WebDriver webDriver = WebDriverManager.getInstance(browsers.getName().toUpperCase()).browserVersion(browsers.getVersion()).remoteAddress(browsers.getRemoteUrl()).capabilities(options).create();
        
        ((RemoteWebDriver)webDriver).setFileDetector(new LocalFileDetector());
        webDriver.manage().window().maximize();

        return webDriver;
    }

    public AbstractDriverOptions<?> getOptions(Browsers browsers) {
        AbstractDriverOptions<?> options = OptionsFactory.valueOf(browsers.name()).getOptions();
        options.setAcceptInsecureCerts(true);
        options.setBrowserVersion(browsers.getVersion());

        options.setCapability("name", browsers.getCaseExecutionId());
        options.setCapability("testName", browsers.getTestName());

        options.setCapability("enableVNC", true);
        options.setCapability("enableVideo", false);

        options.setCapability("screenResolution", browsers.getScreenResolution());
        return options;
    }
}
