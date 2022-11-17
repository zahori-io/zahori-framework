package io.zahori.framework.driver;

import io.github.bonigarcia.wdm.WebDriverManager;
import io.zahori.framework.driver.browserfactory.Browsers;
import org.openqa.selenium.WebDriver;

public class RemoteDriver implements Driver{
    public WebDriver getDriver(Browsers browsers){
        return WebDriverManager.getInstance(browsers.getName()).browserVersion(browsers.getVersion()).remoteAddress(browsers.getRemoteUrl()).create();
    }
}
