package io.zahori.framework.driver;

/*-
 * #%L
 * zahori-framework
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2021 - 2024 PANEL SISTEMAS INFORMATICOS,S.L
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
import io.zahori.framework.driver.browserfactory.Browsers;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.ie.InternetExplorerDriver;
import org.openqa.selenium.ie.InternetExplorerOptions;
import org.openqa.selenium.remote.AbstractDriverOptions;
import org.openqa.selenium.safari.SafariDriver;
import org.openqa.selenium.safari.SafariOptions;

/**
 * Driver local usando Selenium Manager nativo (Selenium 4.6+).
 *
 * Selenium Manager descarga automaticamente el driver del navegador
 * sin necesidad de configuracion adicional ni dependencias externas.
 *
 * Navegadores soportados: Chrome, Firefox, Edge, Safari, IE
 */
public class LocalDriver extends AbstractDriver {

    private static final Logger LOG = LogManager.getLogger();

    @Override
    protected WebDriver createWebDriver(Browsers browsers, Proxy proxy) {
        AbstractDriverOptions<?> options = getOptions(browsers, proxy);
        String browserName = browsers.getName().toUpperCase();

        LOG.info("Creando driver local para {} usando Selenium Manager", browserName);

        return switch (browserName) {
            case "CHROME" -> new ChromeDriver((ChromeOptions) options);
            case "FIREFOX" -> new FirefoxDriver((FirefoxOptions) options);
            case "EDGE" -> new EdgeDriver((EdgeOptions) options);
            case "SAFARI" -> new SafariDriver((SafariOptions) options);
            case "IE", "IEXPLORER", "INTERNETEXPLORER" -> new InternetExplorerDriver((InternetExplorerOptions) options);
            default -> throw new IllegalArgumentException(
                "Navegador no soportado: " + browserName +
                ". Valores validos: Chrome, Firefox, Edge, Safari, IE/IExplorer");
        };
    }

    @Override
    protected void configureWebDriver(WebDriver webDriver, Browsers browsers) {
        resizeWindow(webDriver, browsers);
    }

    /**
     * Configura el tamano de la ventana del navegador.
     */
    private void resizeWindow(WebDriver driver, Browsers browsers) {
        String resolution = browsers.getScreenResolution();
        if (StringUtils.isNotBlank(resolution) && resolution.contains("x")) {
            try {
                int width = Integer.parseInt(resolution.split("x")[0]);
                int height = Integer.parseInt(resolution.split("x")[1]);
                driver.manage().window().setSize(new Dimension(width, height));
                LOG.debug("Ventana configurada: {}x{}", width, height);
            } catch (NumberFormatException e) {
                LOG.warn("Resolucion invalida '{}', maximizando ventana", resolution);
                driver.manage().window().maximize();
            }
        } else {
            driver.manage().window().maximize();
        }
    }
}
