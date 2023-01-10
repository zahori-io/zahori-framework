package io.zahori.framework.driver;

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
