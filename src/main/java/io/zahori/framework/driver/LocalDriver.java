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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.Credentials;
import org.openqa.selenium.HasAuthentication;
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

import java.net.URI;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Driver local usando Selenium Manager nativo (Selenium 4.6+).
 *
 * Selenium Manager descarga automaticamente el driver del navegador
 * sin necesidad de configuracion adicional ni dependencias externas.
 *
 * Soporta autenticación de proxy HTTP 407 mediante HasAuthentication
 * directa en drivers modernos (Chrome, Firefox, Edge).
 *
 * Configuracion en zahori.properties:
 * - zahori.test.execution.proxy.ip=host (opcional)
 * - zahori.test.execution.proxy.port=8080 (opcional)
 * - zahori.test.execution.proxy.user=user (opcional, para proxy autenticado)
 * - zahori.test.execution.proxy.password=ENCRYPTED (opcional, cifrado con ZahoriCipher)
 *
 * Navegadores soportados: Chrome, Firefox, Edge, Safari, IE
 *
 * @since 2.0.0 - Añadido soporte para proxy autenticado via HasAuthentication
 */
public class LocalDriver extends AbstractDriver {

    private static final Logger LOG = LogManager.getLogger();

    @Override
    protected WebDriver createWebDriver(Browsers browsers, Proxy proxy) {
        AbstractDriverOptions<?> options = getOptions(browsers, proxy);
        String browserName = browsers.getName().toUpperCase();

        LOG.info("Creando driver local para {} usando Selenium Manager", browserName);

        WebDriver driver = createDriverInstance(browserName, options);

        // Registrar autenticación de proxy (si está habilitada)
        registerProxyAuthentication(driver, browserName);

        return driver;
    }

    /**
     * Crea la instancia del driver según el navegador solicitado.
     *
     * @param browserName nombre del navegador en mayúsculas
     * @param options opciones configuradas del navegador
     * @return instancia del WebDriver
     */
    private WebDriver createDriverInstance(String browserName, AbstractDriverOptions<?> options) {
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

    /**
     * Registra las credenciales de autenticación del proxy en el driver.
     *
     * Los drivers locales modernos (ChromeDriver, EdgeDriver, FirefoxDriver)
     * implementan HasAuthentication directamente, sin necesidad de Augmenter.
     *
     * Selenium 4.x requiere BiDi habilitado para HasAuthentication.
     * Safari e IE no soportan esta característica.
     *
     * @param driver instancia del WebDriver
     * @param browserName nombre del navegador para logging
     */
    private void registerProxyAuthentication(WebDriver driver, String browserName) {
        // Verificar si el proxy está habilitado para navegador y requiere autenticación
        if (!ProxyAuthConfig.isProxyBrowserEnabled() || !ProxyAuthConfig.isAuthenticationRequired()) {
            if (ProxyAuthConfig.isProxyBrowserEnabled()) {
                LOG.debug("Proxy configurado sin autenticación para {}", browserName);
            }
            return;
        }

        // Obtener credenciales y predicado
        Supplier<Credentials> credentials = ProxyAuthConfig.getCredentials();
        Predicate<URI> uriPredicate = ProxyAuthConfig.getUriPredicate();

        if (credentials == null || uriPredicate == null) {
            LOG.warn("Credenciales de proxy incompletas, omitiendo registro de autenticación");
            return;
        }

        // Verificar soporte de HasAuthentication
        if (driver instanceof HasAuthentication hasAuth) {
            try {
                hasAuth.register(uriPredicate, credentials);
                LOG.info("Autenticación de proxy registrada para {} -> {}",
                         browserName, ProxyAuthConfig.getHost());
            } catch (Exception e) {
                // Algunos drivers pueden no tener BiDi completamente habilitado
                LOG.warn("No se pudo registrar autenticación de proxy en {}: {}. " +
                         "Verificar que BiDi está habilitado.",
                         browserName, e.getMessage());
            }
        } else {
            // Safari e IE no soportan HasAuthentication
            LOG.warn("Driver {} no soporta HasAuthentication. " +
                     "La autenticación de proxy 407 no funcionará automáticamente. " +
                     "Considerar usar Chrome, Firefox o Edge.",
                     browserName);
        }
    }

    @Override
    protected void configureWebDriver(WebDriver webDriver, Browsers browsers) {
        resizeWindow(webDriver, browsers);
    }
}
