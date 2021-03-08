package io.zahori.framework.driver.mobilefactory;

/*-
 * #%L
 * zahori-framework
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2021 PANEL SISTEMAS INFORMATICOS,S.L
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

import java.net.URL;

import org.openqa.selenium.Platform;
import org.openqa.selenium.remote.DesiredCapabilities;

import com.google.common.collect.ImmutableMap;

import io.appium.java_client.android.AndroidDriver;

public class AndroidFactory {

    private AndroidFactory() {
    }

    public static DesiredCapabilities getCapabilities() {
        return new DesiredCapabilities();
    }

    public static AndroidDriver<?> getDriver(URL url, DesiredCapabilities capabilities) {
    	DesiredCapabilities caps = capabilities;
    	caps.setPlatform(Platform.ANY);
    	caps.setCapability("appium:chromeOptions", ImmutableMap.of("w3c", false));
    	//caps.setCapability("chromedriverExecutableDir", "/usr/local/bin/");
        return new AndroidDriver(url, caps);
    }
}
