package io.zahori.framework.driver;

/*-
 * #%L
 * zahori-framework
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2021 - 2023 PANEL SISTEMAS INFORMATICOS,S.L
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

import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.ie.InternetExplorerOptions;
import org.openqa.selenium.opera.OperaOptions;
import org.openqa.selenium.remote.AbstractDriverOptions;
import org.openqa.selenium.safari.SafariOptions;

public enum OptionsFactory {
    CHROME {
        @Override
        public ChromeOptions getOptions() {
            ChromeOptions chromeOptions = new ChromeOptions();
            chromeOptions.addArguments("--disable-infobars");
            chromeOptions.addArguments("--disable-notifications");
            chromeOptions.setHeadless(false);
            chromeOptions.setAcceptInsecureCerts(true);
            chromeOptions.setPageLoadStrategy(PageLoadStrategy.EAGER);
            return chromeOptions;
        }
    },
    FIREFOX {
        @Override
        public FirefoxOptions getOptions() {
            FirefoxOptions firefoxOptions = new FirefoxOptions();
            firefoxOptions.setHeadless(false);
            firefoxOptions.setAcceptInsecureCerts(true);

            return firefoxOptions;
        }
    },
    OPERA {
        @Override
        public OperaOptions getOptions() {
            OperaOptions operaOptions = new OperaOptions();
            operaOptions.setAcceptInsecureCerts(true);
            return operaOptions;
        }
    },
    EDGE {
        @Override
        public EdgeOptions getOptions() {
            EdgeOptions edgeOptions = new EdgeOptions();
            edgeOptions.setHeadless(false);
            edgeOptions.setAcceptInsecureCerts(true);

            return edgeOptions;
        }
    },
    IEXPLORER {
        @Override
        public InternetExplorerOptions getOptions() {
            InternetExplorerOptions internetExplorerOptions = new InternetExplorerOptions();
            internetExplorerOptions.ignoreZoomSettings();
            internetExplorerOptions.setAcceptInsecureCerts(true);
            return internetExplorerOptions;
        }
    },
    SAFARI {
        @Override
        public SafariOptions getOptions() {
            SafariOptions safariOptions = new SafariOptions();
            safariOptions.setAutomaticInspection(false);
            safariOptions.setAcceptInsecureCerts(true);
            return safariOptions;
        }
    };
    public abstract AbstractDriverOptions<?> getOptions();
}
