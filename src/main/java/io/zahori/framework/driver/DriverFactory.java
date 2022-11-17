package io.zahori.framework.driver;

import io.zahori.framework.driver.browserfactory.Browsers;
import org.openqa.selenium.WebDriver;

    public class DriverFactory implements AbstractFactory<WebDriver>{
        private TypExecution execType;

        public WebDriver create(Browsers browsers) {
        Driver mdriver = null;
        if ("YES".equalsIgnoreCase(browsers.getRemote())) {
            mdriver = new RemoteDriver();
        } else {
            mdriver = new LocalDriver();
        }
        return mdriver.getDriver(browsers);
    }
}
