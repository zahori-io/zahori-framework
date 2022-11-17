package io.zahori.framework.driver;

import io.zahori.framework.driver.browserfactory.Browsers;
import org.openqa.selenium.WebDriver;

public interface Driver {
    WebDriver getDriver(Browsers browsers);
}
