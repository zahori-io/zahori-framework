package io.zahori.framework.driver;

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
