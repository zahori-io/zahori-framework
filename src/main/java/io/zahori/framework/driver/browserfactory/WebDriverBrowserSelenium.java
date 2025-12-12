package io.zahori.framework.driver.browserfactory;

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
import io.appium.java_client.android.AndroidDriver;
import io.zahori.framework.driver.DriverFactory;
import java.time.Duration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.WebDriver;

public class WebDriverBrowserSelenium {

    private static final Logger LOG = LogManager.getLogger(WebDriverBrowserSelenium.class);

    private Browsers browsers;

    private Proxy proxy;

    public WebDriverBrowserSelenium(Browsers browsers) {
        super();
        this.browsers = browsers;
        proxy = null;
    }

    public WebDriverBrowserSelenium(Browsers browsers, final Proxy proxy) {
        super();
        this.browsers = browsers;
        this.proxy = proxy;
    }

    public WebDriver getWebDriver() {
        WebDriver driver = null;
        try {
            driver = new DriverFactory().create(browsers, proxy);
            setProperties(driver, browsers);
        } catch (final IllegalArgumentException | SecurityException e) {
            LOG.error("Error creating WebDriver: {}", e.getMessage(), e);
        }
        return driver;
    }

    /**
     * Configura timeouts del driver.
     *
     * <p><strong>NOTA IMPORTANTE sobre Implicit Waits:</strong></p>
     * <p>Selenium 4 Best Practices recomienda NO usar implicit waits porque:</p>
     * <ul>
     *   <li>Hacen los tests mas lentos (siempre esperan el tiempo completo)</li>
     *   <li>Pueden causar flaky tests cuando se mezclan con explicit waits</li>
     *   <li>No permiten condiciones personalizadas</li>
     * </ul>
     *
     * <p>Se recomienda usar WebDriverWait con ExpectedConditions:</p>
     * <pre>
     * WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
     * wait.until(ExpectedConditions.elementToBeClickable(locator));
     * </pre>
     *
     * @param driver WebDriver a configurar
     * @param browsers configuracion de navegador
     */
    public void setProperties(final WebDriver driver, Browsers browsers) {
        if (driver == null) {
            return;
        }

        if (!(driver instanceof AndroidDriver)) { // pageLoadTimeout is not implemented yet for AndroidDriver
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(browsers.getPageLoadTimeout()));
        }

        // NOTA: implicitlyWait esta desaconsejado en Selenium 4.
        // Se mantiene por retrocompatibilidad, pero se recomienda usar explicit waits.
        // Ver: https://www.selenium.dev/documentation/webdriver/waits/
        long implicitWait = browsers.getImplicitlyWait();
        if (implicitWait > 0) {
            LOG.warn("Implicit wait configurado a {} segundos. Considere usar explicit waits (WebDriverWait) en su lugar.", implicitWait);
        }
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(implicitWait));
    }

    /**
     * Configura solo el pageLoadTimeout (recomendado Selenium 4).
     * No configura implicit waits, permitiendo usar solo explicit waits.
     *
     * @param driver WebDriver a configurar
     * @param pageLoadTimeoutSeconds timeout para carga de pagina
     */
    public void setPropertiesWithoutImplicitWait(final WebDriver driver, long pageLoadTimeoutSeconds) {
        if (driver == null) {
            return;
        }

        if (!(driver instanceof AndroidDriver)) {
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(pageLoadTimeoutSeconds));
        }
        // No configurar implicit wait - usar solo explicit waits
        driver.manage().timeouts().implicitlyWait(Duration.ZERO);
        LOG.info("Driver configurado sin implicit waits (best practice Selenium 4)");
    }

    public Browsers getNavega() {
        return browsers;
    }

    public void setNavega(final Browsers navega) {
        this.browsers = navega;
    }
}
